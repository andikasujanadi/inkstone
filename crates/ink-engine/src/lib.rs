use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::sync::Arc;
mod gpu;

const MAX_PIXELS: usize = 16_777_216;
const PIXEL_BUDGET: usize = 67_108_864;
const HISTORY_BUDGET: usize = 96 * 1024 * 1024;

#[derive(Clone)]
pub struct Layer {
    pub id: u32,
    pub name: String,
    pub visible: bool,
    pub opacity: f32,
    pub blend: String,
    pub alpha_lock: bool,
    pub locked: bool,
    pub clipping: bool,
    pub pixels: Arc<Vec<u32>>,
}

#[derive(Clone)]
struct State {
    layers: Vec<Layer>,
    active: usize,
    background: u32,
    background_visible: bool,
}

enum Change {
    Pixels { id: u32, rect: [usize; 4], before: Vec<u32>, after: Vec<u32> },
    Structure { before: State, after: State },
}

impl Change {
    fn bytes(&self) -> usize {
        match self {
            Self::Pixels { before, after, .. } => (before.len() + after.len()) * 4,
            Self::Structure { before, after } => (before.layers.len() + after.layers.len()) * 256
                + before.layers.iter().map(|l| l.pixels.len() * 4).sum::<usize>()
                + after.layers.iter().map(|l| l.pixels.len() * 4).sum::<usize>(),
        }
    }
}

#[derive(Clone, Copy, Debug)]
pub struct Sample { pub x: f32, pub y: f32, pub pressure: f32, pub tilt: f32 }

#[derive(Clone, Copy)]
pub struct Brush {
    pub kind: u8,
    pub color: u32,
    pub size: f32,
    pub opacity: f32,
    pub flow: f32,
    pub stabilization: f32,
    pub pressure_size: f32,
    pub pressure_opacity: f32,
}

impl Default for Brush {
    fn default() -> Self {
        Self { kind: 0, color: 0xff202020, size: 16.0, opacity: 1.0, flow: 1.0,
            stabilization: 0.12, pressure_size: 0.8, pressure_opacity: 0.0 }
    }
}

struct Stroke { rect: [usize; 4], before: Vec<u32>, layer: usize, last: Sample, filtered: Sample, brush: Brush, seed: u32, smudge: Option<Vec<u32>>, pending: Vec<gpu::Dab>, gpu_paint: bool }

pub struct Engine {
    gpu: Option<gpu::Gpu>,
    renderer_requested: String,
    renderer_error: Option<String>,
    pub width: usize,
    pub height: usize,
    dpi: u32,
    state: State,
    next_id: u32,
    undo: Vec<Change>,
    redo: Vec<Change>,
    stroke: Option<Stroke>,
    flood_preview: Option<(usize, Arc<Vec<u32>>)>,
    selection: Option<Vec<bool>>,
    pub dirty: bool,
    damage: Option<[usize; 4]>,
}

fn finite(v: f32, fallback: f32) -> f32 { if v.is_finite() { v } else { fallback } }
fn channel(c: u32, shift: u32) -> f32 { ((c >> shift) & 255) as f32 / 255.0 }
fn pack(r: f32, g: f32, b: f32, a: f32) -> u32 {
    let c = |v: f32| (v.clamp(0.0, 1.0) * 255.0 + 0.5) as u32;
    (c(a) << 24) | (c(r) << 16) | (c(g) << 8) | c(b)
}

pub fn composite(dst: u32, src: u32, opacity: f32, mode: &str) -> u32 {
    let sa = channel(src, 24) * opacity;
    if sa <= 0.0 { return dst; }
    if mode == "Normal" && sa >= 1.0 { return src; }
    let da = channel(dst, 24);
    let a = sa + da * (1.0 - sa);
    let mut out = [0.0; 3];
    let darker_source = mode=="Darker Colour" && [16,8,0].iter().map(|shift|(src>>shift)&255).sum::<u32>() < [16,8,0].iter().map(|shift|(dst>>shift)&255).sum::<u32>();
    for (i, shift) in [16, 8, 0].iter().enumerate() {
        let s = channel(src, *shift); let d = channel(dst, *shift);
        let b = match mode {
            "Multiply" => s * d,
            "Colour Burn" => if s<=0.0{0.0}else{1.0-((1.0-d)/s).min(1.0)},
            "Linear Burn" => (d+s-1.0).max(0.0),
            "Colour Dodge" => if s>=1.0{1.0}else{(d/(1.0-s)).min(1.0)},
            "Subtract" => (d-s).max(0.0),
            "Exclusion" => d+s-2.0*d*s,
            "Hard Light" => if s<0.5{2.0*s*d}else{1.0-2.0*(1.0-s)*(1.0-d)},
            "Screen" => 1.0 - (1.0 - s) * (1.0 - d),
            "Overlay" => if d < 0.5 { 2.0 * s * d } else { 1.0 - 2.0 * (1.0 - s) * (1.0 - d) },
            "Add" => (s + d).min(1.0),
            "Darker Colour" => if darker_source{s}else{d},
            "Darken" => s.min(d), "Lighten" => s.max(d), "Difference" => (s - d).abs(),
            _ => s,
        };
        out[i] = (sa * (1.0 - da) * s + sa * da * b + (1.0 - sa) * da * d) / a.max(0.0001);
    }
    pack(out[0], out[1], out[2], a)
}

impl Engine {
    pub fn new(width: usize, height: usize) -> Result<Self, String> {
        let pixels = width.checked_mul(height).ok_or("Canvas dimensions overflow")?;
        if width < 16 || height < 16 || width > 8192 || height > 8192 || pixels > MAX_PIXELS {
            return Err("Canvas must be at least 16 × 16 and at most 16 megapixels".into());
        }
        Ok(Self { gpu: None, renderer_requested: "cpu".into(), renderer_error: None, width, height, dpi:300, state: State { layers: vec![Layer {
            id: 1, name: "Layer 1".into(), visible: true, opacity: 1.0, blend: "Normal".into(),
            alpha_lock: false, locked:false, clipping: false, pixels: Arc::new(vec![0; pixels]),
        }], active: 0, background: 0xfffaf9f6, background_visible: true }, next_id: 2,
            undo: vec![], redo: vec![], stroke: None, flood_preview: None, selection: None, dirty: true, damage: None })
    }

    fn record(&mut self, change: Change) {
        self.redo.clear();
        self.undo.push(change);
        let mut bytes: usize = self.undo.iter().map(Change::bytes).sum();
        while bytes > HISTORY_BUDGET && self.undo.len() > 1 {
            bytes -= self.undo.remove(0).bytes();
        }
        if self.undo.len() > 250 { self.undo.remove(0); }
        self.dirty = true;
    }

    fn structure<F: FnOnce(&mut Self)>(&mut self, f: F) {
        self.end();
        let before = self.state.clone(); f(self);
        self.record(Change::Structure { before, after: self.state.clone() });
    }

    fn pixel_change(&mut self, layer: usize, before: Vec<u32>) {
        self.pixel_change_rect(layer, before, [0,0,self.width,self.height]);
    }

    fn pixel_change_rect(&mut self, layer: usize, before: Vec<u32>, search: [usize;4]) {
        let after = &self.state.layers[layer].pixels;
        let mut rect = [self.width, self.height, 0, 0];
        for y in search[1]..search[3] { for x in search[0]..search[2] {
            let i = y * self.width + x;
            if before[i] != after[i] {
                let (x, y) = (i % self.width, i / self.width);
                rect[0] = rect[0].min(x); rect[1] = rect[1].min(y);
                rect[2] = rect[2].max(x + 1); rect[3] = rect[3].max(y + 1);
            }
        }
        }
        if rect[2] == 0 { return; }
        let mut old = vec![]; let mut new = vec![];
        for y in rect[1]..rect[3] {
            old.extend_from_slice(&before[y * self.width + rect[0]..y * self.width + rect[2]]);
            new.extend_from_slice(&after[y * self.width + rect[0]..y * self.width + rect[2]]);
        }
        self.record(Change::Pixels { id: self.state.layers[layer].id, rect, before: old, after: new });
    }

    fn apply_change(&mut self, c: &Change, forward: bool) {
        match c {
            Change::Structure { before, after } => self.state = if forward { after.clone() } else { before.clone() },
            Change::Pixels { id, rect, before, after } => {
                if let Some(l) = self.state.layers.iter_mut().find(|l| l.id == *id) {
                    let src = if forward { after } else { before };
                    let pixels = Arc::make_mut(&mut l.pixels);
                    let w = rect[2] - rect[0];
                    for (row, y) in (rect[1]..rect[3]).enumerate() {
                        pixels[y * self.width + rect[0]..y * self.width + rect[2]]
                            .copy_from_slice(&src[row * w..(row + 1) * w]);
                    }
                }
            }
        }
        self.dirty = true;
    }

    pub fn undo(&mut self) -> bool {
        self.end();
        if let Some(c) = self.undo.pop() { self.apply_change(&c, false); self.redo.push(c); true } else { false }
    }
    pub fn redo(&mut self) -> bool {
        self.end();
        if let Some(c) = self.redo.pop() { self.apply_change(&c, true); self.undo.push(c); true } else { false }
    }

    pub fn begin(&mut self, p: Sample, mut brush: Brush) {
        self.end();self.cancel_flood();
        if self.state.layers[self.state.active].locked { return; }
        if !p.x.is_finite() || !p.y.is_finite() { return; }
        brush.size = finite(brush.size, 16.0).clamp(0.5, 600.0);
        brush.opacity = finite(brush.opacity, 1.0).clamp(0.0, 1.0);
        brush.flow = finite(brush.flow, 1.0).clamp(0.01, 1.0);
        brush.stabilization = finite(brush.stabilization, 0.0).clamp(0.0, 0.95);
        brush.pressure_size = finite(brush.pressure_size, 0.8).clamp(0.0, 1.0);
        brush.pressure_opacity = finite(brush.pressure_opacity, 0.0).clamp(0.0, 1.0);
        if brush.kind == 6 || brush.kind>=128 { brush.color = 0; }
        let mut stroke = Stroke { rect: [self.width,self.height,0,0], before: self.state.layers[self.state.active].pixels.as_ref().clone(),
            layer: self.state.active, last: p, filtered: p, brush, seed: 0x736561, smudge: None, pending: Vec::new(), gpu_paint: brush.size>=32.0 };
        if stroke.gpu_paint { if let Some(gpu)=&mut self.gpu { gpu.selection(self.selection.as_deref()); } }
        self.stamp(p, &mut stroke);
        self.stroke = Some(stroke);
    }

    pub fn sample(&mut self, mut p: Sample) {
        if !p.x.is_finite() || !p.y.is_finite() { return; }
        if let Some(mut stroke) = self.stroke.take() {
            let smooth = 1.0 - stroke.brush.stabilization * 0.85;
            p.x = stroke.filtered.x + (p.x - stroke.filtered.x) * smooth;
            p.y = stroke.filtered.y + (p.y - stroke.filtered.y) * smooth;
            stroke.filtered = p;
            let a = stroke.last;
            let distance = (p.x - a.x).hypot(p.y - a.y);
            let spacing = (stroke.brush.size * 0.055).max(0.65);
            let steps = (distance / spacing).ceil().clamp(1.0, 8192.0) as usize;
            for i in 1..=steps {
                let t = i as f32 / steps as f32;
                self.stamp(Sample { x: a.x + (p.x - a.x) * t, y: a.y + (p.y - a.y) * t,
                    pressure: a.pressure + (p.pressure - a.pressure) * t, tilt: p.tilt }, &mut stroke);
            }
            stroke.last = p;
            self.stroke = Some(stroke);
        }
    }

    fn stamp(&mut self, p: Sample, stroke: &mut Stroke) {
        if stroke.pending.len() >= gpu::MAX_DABS { self.flush_stroke(stroke); }
        let brush = stroke.brush;
        let pressure = finite(p.pressure, 1.0).clamp(0.0, 1.0).powf(0.7);
        let mut radius = brush.size * 0.5 * (1.0 - brush.pressure_size + brush.pressure_size * pressure).max(0.08);
        if brush.kind == 1 || brush.kind == 3 { radius *= 1.0 + finite(p.tilt, 0.0).clamp(0.0, 1.57) * 0.7; }
        let x0 = ((p.x - radius - 1.0).floor().max(0.0) as usize).min(self.width);
        let y0 = ((p.y - radius - 1.0).floor().max(0.0) as usize).min(self.height);
        let x1 = ((p.x + radius + 1.0).ceil().max(0.0) as usize).min(self.width);
        let y1 = ((p.y + radius + 1.0).ceil().max(0.0) as usize).min(self.height);
        if self.gpu.is_some() && stroke.gpu_paint {
            let smudging=brush.kind==6 || brush.kind>=128;
            let initial=smudging && stroke.smudge.is_none();
            if initial { stroke.smudge=Some(Vec::new()); }
            stroke.pending.push(gpu::Dab { sample:p, params:gpu::Params {
                meta:[self.width as u32,self.height as u32,brush.kind as u32,self.state.layers[stroke.layer].alpha_lock as u32],
                rect:[x0 as u32,y0 as u32,x1 as u32,y1 as u32],
                dab:[p.x,p.y,radius,brush.opacity*brush.flow*(1.0-brush.pressure_opacity+brush.pressure_opacity*pressure)],
                flags:[brush.color,stroke.seed,self.selection.is_some() as u32,initial as u32],region:[0;4],
            }});
            if !initial { stroke.seed=stroke.seed.wrapping_add(1); }
            return;
        }
        let selected = &self.selection;
        let layer = &mut self.state.layers[stroke.layer];
        let lock = layer.alpha_lock;
        let color = brush.color;
        let smudging=brush.kind==6 || brush.kind>=128;
        let shape=if brush.kind>=128 {brush.kind&127} else if smudging {2} else {brush.kind};
        if smudging && stroke.smudge.is_none() {
            let mut tip=vec![0;32*32];
            for gy in 0..32 {for gx in 0..32 {
                let sx=(p.x+((gx as f32+0.5)/16.0-1.0)*radius).floor() as i32;
                let sy=(p.y+((gy as f32+0.5)/16.0-1.0)*radius).floor() as i32;
                if sx>=0 && sy>=0 && sx<self.width as i32 && sy<self.height as i32 {
                    tip[gy*32+gx]=layer.pixels[sy as usize*self.width+sx as usize];
                }
            }}
            stroke.smudge=Some(tip);return;
        }
        // Pick up the destination before writing this dab, preserving direction and color variation.
        let pickup=if smudging {let mut tip=vec![0;1024];for gy in 0..32 {for gx in 0..32 {
            let sx=(p.x+((gx as f32+0.5)/16.0-1.0)*radius).floor() as i32;
            let sy=(p.y+((gy as f32+0.5)/16.0-1.0)*radius).floor() as i32;
            if sx>=0 && sy>=0 && sx<self.width as i32 && sy<self.height as i32 {tip[gy*32+gx]=layer.pixels[sy as usize*self.width+sx as usize];}
        }}Some(tip)}else{None};
        let pixels = Arc::make_mut(&mut layer.pixels);
        let strength = brush.opacity * brush.flow * (1.0 - brush.pressure_opacity + brush.pressure_opacity * pressure);
        for y in y0..y1 { for x in x0..x1 {
            let idx = y * self.width + x;
            if selected.as_ref().is_some_and(|s| !s[idx]) { continue; }
            let dx = x as f32 + 0.5 - p.x; let dy = y as f32 + 0.5 - p.y;
            let d = dx.hypot(dy) / radius.max(0.1);
            if d > 1.0 + 0.7 / radius { continue; }
            let edge = ((1.0 - d) * radius + 0.5).clamp(0.0, 1.0);
            let texture = match shape {
                1 => { let n = noise(x as u32, y as u32, 91); 0.16 + n * 0.58 },
                2 => ((-4.0*d*d).exp()-(-4.0f32).exp()).max(0.0)/(1.0-(-4.0f32).exp())*0.12,
                8 | 9 => {let hard=if shape==8{0.35}else{0.7};let t=((1.0-d)/(1.0-hard)).clamp(0.0,1.0);t*t*(3.0-2.0*t)*0.18},
                10 => if noise(x as u32,y as u32,stroke.seed)>0.965 {0.65*(1.0-d).max(0.0)}else{0.0},
                11 => {let strands=(dx*1.7).sin().abs();if dy.abs()<radius*0.55 {0.12+strands*0.4}else{0.0}},
                12 => if dy.abs()<radius*0.48 {0.2}else{0.0},
                13 => noise(x as u32,y as u32,19).powf(2.0)*0.38,
                14 => (0.1+noise(x as u32,y as u32,73)*0.3)*(1.0-d*d).max(0.0),
                3 => noise(x as u32, y as u32, stroke.seed / 15).powf(1.8) * 0.7,
                4 => (1.0 - d.clamp(0.0, 1.0) * 0.7) * 0.10,
                6 => 0.11,
                7 => if dx.abs() < radius * 0.32 { 0.85 } else { 0.0 },
                _ => 1.0,
            };
            let alpha = (strength * edge * texture).clamp(0.0, 1.0);
            let dst = pixels[idx];
            if smudging {
                let carried=sample_tip(stroke.smudge.as_ref().unwrap(),(dx/radius+1.0)*16.0-0.5,(dy/radius+1.0)*16.0-0.5);
                let mask=match shape {
                    2 | 4 => ((1.0-d*d).max(0.0)).powf(2.0),
                    8 | 9 => {let hard=if shape==8{0.35}else{0.7};let t=((1.0-d)/(1.0-hard)).clamp(0.0,1.0);t*t*(3.0-2.0*t)},
                    7 | 10 | 11 | 12 | 13 | 14 => texture.min(1.0)*edge,
                    _=>edge,
                };
                let mixed=mix_rgba(dst,carried,(strength*mask*0.32).clamp(0.0,1.0));
                pixels[idx]=if lock {(mixed&0xffffff)|(dst&0xff000000)}else{mixed};
            } else if brush.kind == 5 {
                if lock { continue; }
                let a = channel(dst, 24) * (1.0 - alpha);
                pixels[idx] = (dst & 0x00ffffff) | (((a * 255.0).round() as u32) << 24);
            } else if lock {
                if dst >> 24 != 0 {
                    let c = composite(dst | 0xff000000, color | 0xff000000, alpha, "Normal");
                    pixels[idx] = (c & 0x00ffffff) | (dst & 0xff000000);
                }
            } else { pixels[idx] = composite(dst, color, alpha, "Normal"); }
        }}
        if let (Some(tip),Some(pickup))=(&mut stroke.smudge,pickup){
            for (carried,local) in tip.iter_mut().zip(pickup){*carried=mix_rgba(*carried,local,0.08);}
        }
        stroke.seed = stroke.seed.wrapping_add(1);
        if x0 < x1 && y0 < y1 {
            stroke.rect[0]=stroke.rect[0].min(x0);stroke.rect[1]=stroke.rect[1].min(y0);
            stroke.rect[2]=stroke.rect[2].max(x1);stroke.rect[3]=stroke.rect[3].max(y1);
            let r = self.damage.get_or_insert([x0, y0, x1, y1]);
            r[0] = r[0].min(x0); r[1] = r[1].min(y0);
            r[2] = r[2].max(x1); r[3] = r[3].max(y1);
        }
    }

    pub fn set_renderer(&mut self, mode:&str)->Result<(),String> {
        if !matches!(mode,"auto"|"cpu") {return Err("Renderer must be auto or cpu".into());}
        self.end();self.renderer_requested=mode.into();self.renderer_error=None;
        if mode=="cpu" {self.gpu=None;}
        else if self.gpu.is_none() {
            match gpu::Gpu::new() {Ok(gpu)=>self.gpu=Some(gpu),Err(e)=>self.renderer_error=Some(e)}
        }
        self.dirty=true;Ok(())
    }
    pub fn flush_gpu(&mut self) {
        if let Some(mut stroke)=self.stroke.take() {self.flush_stroke(&mut stroke);self.stroke=Some(stroke);}
    }
    fn flush_stroke(&mut self,stroke:&mut Stroke) {
        if stroke.pending.is_empty(){return;}
        let pending=std::mem::take(&mut stroke.pending);
        let mut rect=[self.width,self.height,0,0];
        for dab in &pending {let r=dab.params.rect; if r[0]<r[2] && r[1]<r[3] {rect[0]=rect[0].min(r[0] as usize);rect[1]=rect[1].min(r[1] as usize);rect[2]=rect[2].max(r[2] as usize);rect[3]=rect[3].max(r[3] as usize);}}
        // An off-canvas smudge still picks up transparency and updates its carried tip.
        let dispatch_rect=if rect[0]>=rect[2] || rect[1]>=rect[3] {[0,0,1,1]}else{rect};
        let smudging=stroke.brush.kind==6 || stroke.brush.kind>=128;
        let result=self.gpu.as_mut().unwrap().paint(&mut self.state.layers[stroke.layer],&pending,dispatch_rect,smudging);
        match result {
            Ok(tip)=>{
                if smudging {stroke.smudge=tip;}
                if rect[0]<rect[2] && rect[1]<rect[3] {
                    for i in 0..2 {stroke.rect[i]=stroke.rect[i].min(rect[i]);stroke.rect[i+2]=stroke.rect[i+2].max(rect[i+2]);}
                    let damage=self.damage.get_or_insert(rect);
                    for i in 0..2 {damage[i]=damage[i].min(rect[i]);damage[i+2]=damage[i+2].max(rect[i+2]);}
                }
            },
            Err(e)=>{
                // The CPU mirror changes only after a successful GPU readback, so replay this batch exactly once.
                self.gpu=None;self.renderer_error=Some(e);stroke.seed=pending[0].params.flags[1];
                if pending[0].params.flags[3]!=0 {stroke.smudge=None;}
                for dab in pending {self.stamp(dab.sample,stroke);}
            }
        }
    }
    pub fn end(&mut self) {
        self.flush_gpu();
        if let Some(s) = self.stroke.take() {
            // Recording undo changes no pixels; keep the pending stroke damage intact.
            let was_dirty = self.dirty;
            self.pixel_change_rect(s.layer, s.before, s.rect);
            self.dirty = was_dirty;
        }
    }
    pub fn cancel(&mut self) {
        if let Some(s) = self.stroke.take() { self.state.layers[s.layer].pixels = Arc::new(s.before); self.dirty = true; }
    }

    pub fn color_at(&self, x: usize, y: usize, background: bool) -> u32 {
        let idx = y.min(self.height - 1) * self.width + x.min(self.width - 1);
        let mut c = if background && self.state.background_visible { self.state.background } else { 0 };
        let mut base_alpha = 0.0;
        for l in &self.state.layers {
            if !l.clipping { base_alpha = if l.visible { channel(l.pixels[idx], 24) } else { 0.0 }; }
            if !l.visible { continue; }
            let opacity = l.opacity * if l.clipping { base_alpha } else { 1.0 };
            c = composite(c, l.pixels[idx], opacity, &l.blend);
        }
        c
    }

    pub fn render(&mut self, bytes: &mut [u8], background: bool) -> bool {
        self.flush_gpu();
        if bytes.len() < self.width * self.height * 4 { return false; }
        for y in 0..self.height { for x in 0..self.width {
            let c = self.color_at(x, y, background);
            let i = (y * self.width + x) * 4;
            let a = (c >> 24) & 255;
            // Android's RGBA_8888 bitmap buffer stores premultiplied channels.
            bytes[i] = (((c >> 16) & 255) * a / 255) as u8;
            bytes[i + 1] = (((c >> 8) & 255) * a / 255) as u8;
            bytes[i + 2] = ((c & 255) * a / 255) as u8;
            bytes[i + 3] = a as u8;
        }}
        true
    }

    pub fn render_patch(&mut self, force: bool) -> Option<Vec<i32>> {
        self.flush_gpu();
        let rect = if force || self.dirty { [0, 0, self.width, self.height] }
            else { self.damage? };
        let [x0, y0, x1, y1] = rect;
        let mut patch = Vec::with_capacity(4 + (x1 - x0) * (y1 - y0));
        patch.extend([x0 as i32, y0 as i32, (x1 - x0) as i32, (y1 - y0) as i32]);
        // Tiny patches cost less to composite locally than to submit and read back from the GPU.
        let gpu_pixels=if force || (x1-x0)*(y1-y0)*self.state.layers.len()>=32768 {
            self.gpu.as_mut().map(|gpu|gpu.composite(&self.state.layers,self.width,rect,if self.state.background_visible {self.state.background}else{0}))
        }else{None};
        match gpu_pixels {
            Some(Ok(pixels))=>patch.extend(pixels.into_iter().map(|c|c as i32)),
            result=>{
                if let Some(Err(e))=result {self.gpu=None;self.renderer_error=Some(e);}
                for y in y0..y1 {for x in x0..x1 {patch.push(self.color_at(x,y,true) as i32);}}
            }
        }
        self.dirty = false; self.damage = None;
        Some(patch)
    }

    pub fn info(&self) -> Value {
        json!({"renderer":if self.gpu.is_some(){"gpu"}else{"cpu"},"rendererRequested":self.renderer_requested,
            "rendererDevice":self.gpu.as_ref().map(|g|g.description.as_str()),"rendererError":self.renderer_error,
            "gpuComposites":self.gpu.as_ref().map_or(0,|g|g.composites),
            "gpuBatches":self.gpu.as_ref().map_or(0,|g|g.batches),"gpuDabs":self.gpu.as_ref().map_or(0,|g|g.dabs),
            "width":self.width,"height":self.height,"active":self.state.active,
            "dpi":self.dpi,"undo":self.undo.len(),"redo":self.redo.len(),"background":self.state.background,
            "backgroundVisible":self.state.background_visible,"selection":self.selection.is_some(),
            "layers":self.state.layers.iter().map(|l| json!({"id":l.id,"name":l.name,
                "visible":l.visible,"opacity":l.opacity,"blend":l.blend,
                "locked":l.locked,"alphaLock":l.alpha_lock,"clipping":l.clipping})).collect::<Vec<_>>()})
    }

    fn can_add(&self) -> bool { (self.state.layers.len() + 1) * self.width * self.height <= PIXEL_BUDGET && self.state.layers.len() < 64 }

    pub fn command(&mut self, v: &Value) -> Result<Value, String> {
        self.end();
        let op = v["op"].as_str().ok_or("Missing operation")?;
        if self.flood_preview.is_some() && !matches!(op,"floodPreview"|"floodCommit"|"floodCancel"|"info"|"pick") {
            self.cancel_flood();
        }
        let index = v["index"].as_u64().unwrap_or(self.state.active as u64) as usize;
        let n = |key: &str, default: f32| finite(v[key].as_f64().unwrap_or(default as f64) as f32, default);
        if self.state.layers[self.state.active].locked && matches!(op,"clear"|"fill"|"flood"|"floodPreview"|"adjust"|"transform"|"delete"|"merge") {
            return Err("This layer is locked. Swipe left and choose Unlock to edit it.".into());
        }
        if op=="merge" && self.state.active>0 && self.state.layers[self.state.active-1].locked {return Err("The layer below is locked".into());}
        match op {
            "setup" => {
                if !self.undo.is_empty() || self.state.layers.len()!=1 {return Err("Canvas setup is only available on a new canvas".into());}
                self.dpi=v["dpi"].as_u64().unwrap_or(300).clamp(1,2400) as u32;
                if let Some(c)=v["color"].as_u64(){self.state.background=c as u32|0xff000000;}
                if let Some(b)=v["visible"].as_bool(){self.state.background_visible=b;}
                self.dirty=true;
            },
            "renderer" => {self.set_renderer(v["mode"].as_str().unwrap_or("auto"))?;},
            "info" => {},
            "undo" => { self.undo(); }, "redo" => { self.redo(); },
            "active" => { if index < self.state.layers.len() { self.state.active = index; } },
            "add" | "duplicate" => {
                if !self.can_add() { return Err("Layer limit reached for this canvas size".into()); }
                self.structure(|e| {
                    let id = e.next_id; e.next_id += 1;
                    let l = if op == "duplicate" {
                        let mut l = e.state.layers[e.state.active].clone(); l.id = id; l.name += " copy"; l
                    } else { Layer { id, name: format!("Layer {}", id), visible:true, opacity:1.0,
                        blend:"Normal".into(), alpha_lock:false, locked:false, clipping:false,
                        pixels:Arc::new(vec![0; e.width * e.height]) } };
                    e.state.active += 1; e.state.layers.insert(e.state.active, l);
                });
            },
            "delete" => {
                if self.state.layers.len() <= 1 { return Err("Keep at least one layer; use Clear instead".into()); }
                self.structure(|e| { e.state.layers.remove(e.state.active); e.state.active = e.state.active.saturating_sub(1); });
            },
            "layer" => {
                if index >= self.state.layers.len() { return Err("Layer no longer exists".into()); }
                self.structure(|e| {
                    let l = &mut e.state.layers[index];
                    if let Some(s) = v["name"].as_str() { l.name = s.chars().take(100).collect(); }
                    if let Some(b) = v["visible"].as_bool() { l.visible = b; }
                    if let Some(b) = v["locked"].as_bool(){l.locked=b;}
                    if let Some(b) = v["alphaLock"].as_bool() { l.alpha_lock = b; }
                    if let Some(b) = v["clipping"].as_bool() { l.clipping = b && index > 0; }
                    if v["opacity"].is_number() { l.opacity = n("opacity", 1.0).clamp(0.0, 1.0); }
                    if let Some(s) = v["blend"].as_str() { l.blend = s.to_string(); }
                });
            },
            "reorder" => {
                let to = v["to"].as_u64().unwrap_or(index as u64) as usize;
                if index < self.state.layers.len() && to < self.state.layers.len() {
                    self.structure(|e| { let l = e.state.layers.remove(index); e.state.layers.insert(to, l); e.state.active = to;
                        e.state.layers[0].clipping = false; });
                }
            },
            "background" => self.structure(|e| {
                if let Some(c) = v["color"].as_u64() { e.state.background = c as u32 | 0xff000000; }
                if let Some(b) = v["visible"].as_bool() { e.state.background_visible = b; }
            }),
            "flipCanvas" => self.structure(|e| {
                let horizontal = v["horizontal"].as_bool().unwrap_or(true);
                for layer in &mut e.state.layers {
                    let pixels = Arc::make_mut(&mut layer.pixels);
                    if horizontal {
                        for row in pixels.chunks_exact_mut(e.width) { row.reverse(); }
                    } else {
                        for y in 0..e.height / 2 { for x in 0..e.width {
                            pixels.swap(y * e.width + x, (e.height - y - 1) * e.width + x);
                        }}
                    }
                }
                e.selection = None;
            }),
            "clear" | "fill" => {
                let before = self.state.layers[self.state.active].pixels.as_ref().clone();
                let l = &mut self.state.layers[self.state.active];
                let lock = l.alpha_lock; let pixels = Arc::make_mut(&mut l.pixels);
                let color = v["color"].as_u64().unwrap_or(0xff202020) as u32;
                for (i, p) in pixels.iter_mut().enumerate() {
                    if self.selection.as_ref().is_some_and(|s| !s[i]) { continue; }
                    if op == "clear" { *p = 0; }
                    else if !lock { *p = color; }
                    else { *p = (color & 0x00ffffff) | (*p & 0xff000000); }
                }
                self.pixel_change(self.state.active, before);
            },
            "merge" => {
                let a = self.state.active;
                if a == 0 { return Err("There is no layer below this one".into()); }
                if self.state.layers[a].clipping || self.state.layers[a - 1].clipping
                    || self.state.layers[a].blend != "Normal" || self.state.layers[a - 1].blend != "Normal"
                    || self.state.layers[a - 1].opacity != 1.0 {
                    return Err("Merge currently supports normal layers with an opaque lower layer setting".into());
                }
                self.structure(|e| {
                    let top = e.state.layers.remove(a);
                    let bottom = &mut e.state.layers[a - 1];
                    if !bottom.visible { Arc::make_mut(&mut bottom.pixels).fill(0); }
                    if top.visible { for (d, s) in Arc::make_mut(&mut bottom.pixels).iter_mut().zip(top.pixels.iter()) {
                        *d = composite(*d, *s, top.opacity, "Normal");
                    }}
                    bottom.visible = bottom.visible || top.visible; e.state.active = a - 1;
                });
            },
            "pick" => {
                let x = n("x", 0.0).max(0.0) as usize; let y = n("y", 0.0).max(0.0) as usize;
                return Ok(json!({"color":self.color_at(x, y, true)}));
            },
            "flood" => self.flood(n("x", 0.0) as i32, n("y", 0.0) as i32,
                v["color"].as_u64().unwrap_or(0xff202020) as u32, n("threshold", 0.12),true)?,
            "floodPreview" => {
                if self.flood_preview.is_none(){self.flood_preview=Some((self.state.active,self.state.layers[self.state.active].pixels.clone()));}
                let (layer,before)=self.flood_preview.as_ref().unwrap();
                self.state.layers[*layer].pixels=before.clone();
                self.flood(n("x",0.0) as i32,n("y",0.0) as i32,v["color"].as_u64().unwrap_or(0xff202020) as u32,n("threshold",0.12),false)?;
            },
            "floodCommit" => {if let Some((layer,before))=self.flood_preview.take(){self.pixel_change(layer,before.as_ref().clone());}},
            "floodCancel" => self.cancel_flood(),
            "deselect" => { self.selection = None; self.dirty = true; },
            "select" => {
                let points: Vec<(f32,f32)> = v["points"].as_array().ok_or("Missing selection points")?.iter()
                    .filter_map(|p| Some((p[0].as_f64()? as f32,p[1].as_f64()? as f32))).collect();
                if points.len() < 2 { return Err("Draw a selection on the canvas".into()); }
                let shape = v["shape"].as_str().unwrap_or("Rectangle");
                let (x0,y0) = points[0]; let (x1,y1) = *points.last().unwrap();
                let mut mask = vec![false; self.width*self.height];
                for y in 0..self.height { for x in 0..self.width {
                    let (px,py) = (x as f32+0.5,y as f32+0.5);
                    mask[y*self.width+x] = match shape {
                        "Freehand" => inside_polygon(px,py,&points),
                        "Ellipse" => {
                            let rx=(x1-x0).abs()*0.5; let ry=(y1-y0).abs()*0.5;
                            rx>0.0 && ry>0.0 && ((px-(x0+x1)*0.5)/rx).powi(2)+((py-(y0+y1)*0.5)/ry).powi(2)<=1.0
                        },
                        _ => px>=x0.min(x1) && px<=x0.max(x1) && py>=y0.min(y1) && py<=y0.max(y1),
                    };
                }}
                self.selection=Some(mask); self.dirty=true;
            },
            "invertSelection" => { if let Some(s) = self.selection.as_mut() { for p in s { *p = !*p; } } },
            "adjust" => {
                let before=self.state.layers[self.state.active].pixels.as_ref().clone();
                let kind=v["kind"].as_str().unwrap_or("Brightness"); let amount=n("amount",0.0);
                let pixels=Arc::make_mut(&mut self.state.layers[self.state.active].pixels);
                if kind == "Blur" {
                    let radius=(amount.abs()*12.0).ceil().clamp(1.0,16.0) as usize;
                    blur(pixels,self.width,self.height,radius);
                    if let Some(s)=&self.selection { for i in 0..pixels.len() { if !s[i] { pixels[i]=before[i]; } } }
                } else { for (i,p) in pixels.iter_mut().enumerate() {
                    if self.selection.as_ref().is_some_and(|s| !s[i]) { continue; }
                    let (r,g,b,a)=(channel(*p,16),channel(*p,8),channel(*p,0),channel(*p,24));
                    let gray=r*0.2126+g*0.7152+b*0.0722;
                    *p=match kind {
                        "Invert" => pack(1.0-r,1.0-g,1.0-b,a),
                        "Grayscale" => pack(gray,gray,gray,a),
                        "Saturation" => { let s=1.0+amount; pack(gray+(r-gray)*s,gray+(g-gray)*s,gray+(b-gray)*s,a) },
                        _ => pack(r+amount,g+amount,b+amount,a),
                    };
                }}
                self.pixel_change(self.state.active,before);
            },
            "transform" => self.transform(n("dx",0.0),n("dy",0.0),n("sx",1.0),n("sy",1.0),n("angle",0.0)),
            _ => return Err(format!("Unknown operation: {}",op)),
        }
        Ok(self.info())
    }

    fn transform(&mut self, dx:f32, dy:f32, sx:f32, sy:f32, angle:f32) {
        if sx.abs()<0.01 || sy.abs()<0.01 { return; }
        let before=self.state.layers[self.state.active].pixels.as_ref().clone();
        let mut out=before.clone();
        let sel=&self.selection;
        for i in 0..out.len() { if sel.as_ref().is_none_or(|s|s[i]) { out[i]=0; } }
        let (cx,cy)=(self.width as f32*0.5,self.height as f32*0.5);
        let (sn,cs)=angle.sin_cos();
        for y in 0..self.height { for x in 0..self.width {
            let (px,py)=(x as f32-cx-dx,y as f32-cy-dy);
            let (ox,oy)=((cs*px+sn*py)/sx+cx,(-sn*px+cs*py)/sy+cy);
            if ox>=0.0 && oy>=0.0 && ox<self.width as f32 && oy<self.height as f32 {
                let src=oy as usize*self.width+ox as usize;
                if sel.as_ref().is_none_or(|s|s[src]) { let i=y*self.width+x; out[i]=composite(out[i],before[src],1.0,"Normal"); }
            }
        }}
        self.state.layers[self.state.active].pixels=Arc::new(out);
        self.selection=None;
        self.pixel_change(self.state.active,before);
    }

    fn cancel_flood(&mut self){if let Some((layer,before))=self.flood_preview.take(){self.state.layers[layer].pixels=before;self.dirty=true;}}
    fn flood(&mut self,x:i32,y:i32,color:u32,threshold:f32,record:bool)->Result<(),String> {
        if x<0 || y<0 || x>=self.width as i32 || y>=self.height as i32 { return Ok(()); }
        let start=y as usize*self.width+x as usize;
        let before=self.state.layers[self.state.active].pixels.clone();
        let target=before[start];
        let distance=|a:u32,b:u32| -> f32 {
            let aa=channel(a,24);let ba=channel(b,24);
            [16,8,0].iter().map(|s|(channel(a,*s)*aa-channel(b,*s)*ba).abs()).fold((aa-ba).abs(),f32::max)
        };
        let l=&mut self.state.layers[self.state.active]; let lock=l.alpha_lock;
        let pixels=Arc::make_mut(&mut l.pixels); let mut seen=vec![false;pixels.len()]; let mut stack=vec![start];
        let matches=|i:usize| !self.selection.as_ref().is_some_and(|s|!s[i]) && distance(before[i],target)<=threshold.clamp(0.0,1.0);
        while let Some(i)=stack.pop() {
            if seen[i] || !matches(i){continue;}
            let row=i/self.width;let mut x=i%self.width;
            while x>0 && !seen[row*self.width+x-1] && matches(row*self.width+x-1){x-=1;}
            let mut above=false;let mut below=false;
            while x<self.width {
                let idx=row*self.width+x;if seen[idx]||!matches(idx){break;}
                seen[idx]=true;pixels[idx]=if lock {(color&0xffffff)|(before[idx]&0xff000000)}else{color};
                if row>0 {let n=idx-self.width;let ok=!seen[n]&&matches(n);if ok&&!above{stack.push(n);}above=ok;}
                if row+1<self.height {let n=idx+self.width;let ok=!seen[n]&&matches(n);if ok&&!below{stack.push(n);}below=ok;}
                x+=1;
            }
        }
        if record {self.pixel_change(self.state.active,before.as_ref().clone());}else{self.dirty=true;}
        Ok(())
    }

    pub fn import(&mut self,width:usize,height:usize,pixels:&[u32])->Result<(),String> {
        if width==0 || height==0 || width.checked_mul(height)!=Some(pixels.len()) || pixels.len()>MAX_PIXELS {return Err("Invalid image".into());}
        if !self.can_add() {return Err("Layer limit reached".into());}
        self.command(&json!({"op":"add"}))?;
        let before=self.state.layers[self.state.active].pixels.as_ref().clone();
        let data=Arc::make_mut(&mut self.state.layers[self.state.active].pixels);
        let scale=(self.width as f32/width as f32).min(self.height as f32/height as f32).min(1.0);
        let (dw,dh)=((width as f32*scale) as usize,(height as f32*scale) as usize);
        let (dx,dy)=((self.width-dw)/2,(self.height-dh)/2);
        for y in 0..dh {for x in 0..dw {data[(y+dy)*self.width+x+dx]=pixels[((y as f32/scale) as usize).min(height-1)*width+((x as f32/scale) as usize).min(width-1)];}}
        self.state.layers[self.state.active].name="Inserted image".into();
        self.pixel_change(self.state.active,before); Ok(())
    }

    pub fn save(&mut self)->Result<Vec<u8>,String> {
        self.flush_gpu();
        let doc=SavedDocument {version:1,width:self.width,height:self.height,active:self.state.active,
            dpi:self.dpi,background:self.state.background,background_visible:self.state.background_visible,
            layers:self.state.layers.iter().map(|l| SavedLayer {id:l.id,name:l.name.clone(),visible:l.visible,
                opacity:l.opacity,blend:l.blend.clone(),alpha_lock:l.alpha_lock,locked:l.locked,clipping:l.clipping,runs:encode_runs(if let Some((index,before))=&self.flood_preview {if self.state.layers[*index].id==l.id {before}else{&l.pixels}}else{&l.pixels})}).collect()};
        serde_json::to_vec(&doc).map_err(|e|e.to_string())
    }
    pub fn load(bytes:&[u8])->Result<Self,String> {
        if bytes.len()>128*1024*1024 {return Err("Document is too large".into());}
        let doc:SavedDocument=serde_json::from_slice(bytes).map_err(|_|"This is not a supported Inkstone document")?;
        if doc.version!=1 {return Err("Unsupported document version".into());}
        let mut e=Self::new(doc.width,doc.height)?;
        if doc.layers.is_empty() || doc.layers.len()>64 || doc.layers.len()*doc.width*doc.height>PIXEL_BUDGET || doc.active>=doc.layers.len() {return Err("Invalid layer count".into());}
        let mut layers=vec![]; let mut ids=std::collections::HashSet::new();
        for l in doc.layers {
            if !ids.insert(l.id) || l.id==u32::MAX || !l.opacity.is_finite() {return Err("Invalid layer metadata".into());}
            let pixels=decode_runs(&l.runs,doc.width*doc.height)?;
            layers.push(Layer{id:l.id,name:l.name.chars().take(100).collect(),visible:l.visible,opacity:l.opacity.clamp(0.0,1.0),
                blend:l.blend,alpha_lock:l.alpha_lock,locked:l.locked,clipping:l.clipping,pixels:Arc::new(pixels)});
        }
        e.dpi=doc.dpi.clamp(1,2400);
        e.next_id=layers.iter().map(|l|l.id).max().unwrap()+1;
        e.state=State{layers,active:doc.active,background:doc.background,background_visible:doc.background_visible};
        Ok(e)
    }
}

fn sample_tip(tip:&[u32],x:f32,y:f32)->u32 {
    let x=x.clamp(0.0,31.0);let y=y.clamp(0.0,31.0);let ix=x as usize;let iy=y as usize;
    let a=mix_rgba(tip[iy*32+ix],tip[iy*32+(ix+1).min(31)],x-ix as f32);
    let b=mix_rgba(tip[(iy+1).min(31)*32+ix],tip[(iy+1).min(31)*32+(ix+1).min(31)],x-ix as f32);
    mix_rgba(a,b,y-iy as f32)
}

fn mix_rgba(a:u32,b:u32,t:f32)->u32 {
    let aa=channel(a,24)*(1.0-t);let ba=channel(b,24)*t;let alpha=aa+ba;
    if alpha<0.00001{return 0;}
    pack((channel(a,16)*aa+channel(b,16)*ba)/alpha,(channel(a,8)*aa+channel(b,8)*ba)/alpha,
        (channel(a,0)*aa+channel(b,0)*ba)/alpha,alpha)
}

fn noise(x:u32,y:u32,seed:u32)->f32 {
    let mut n=x.wrapping_mul(374761393).wrapping_add(y.wrapping_mul(668265263)).wrapping_add(seed);
    n=(n^(n>>13)).wrapping_mul(1274126177); ((n^(n>>16))&65535) as f32/65535.0
}
fn inside_polygon(x:f32,y:f32,points:&[(f32,f32)])->bool {
    let mut inside=false; let mut j=points.len()-1;
    for i in 0..points.len() {let (xi,yi)=points[i];let(xj,yj)=points[j];
        if (yi>y)!=(yj>y) && x<(xj-xi)*(y-yi)/(yj-yi)+xi {inside=!inside;} j=i;}
    inside
}
fn blur(pixels:&mut [u32],w:usize,h:usize,r:usize) {
    let mut tmp=vec![0;pixels.len()];
    for pass in 0..2 {let (major,minor)=if pass==0 {(h,w)} else {(w,h)};
        for a in 0..major {let mut sums=[0.0;4];let index=|b:usize|if pass==0 {a*w+b} else {b*w+a};
            for b in 0..=r.min(minor-1) {let c=pixels[index(b)];let alpha=channel(c,24);for k in 0..3 {sums[k]+=channel(c,[16,8,0][k])*alpha;}sums[3]+=alpha;}
            for b in 0..minor {
                let count=(b+r+1).min(minor)-b.saturating_sub(r);let alpha=sums[3]/count as f32;
                tmp[index(b)]=pack(sums[0]/sums[3].max(0.0001),sums[1]/sums[3].max(0.0001),sums[2]/sums[3].max(0.0001),alpha);
                if b>=r {let c=pixels[index(b-r)];let alpha=channel(c,24);for k in 0..3 {sums[k]-=channel(c,[16,8,0][k])*alpha;}sums[3]-=alpha;}
                if b+r+1<minor {let c=pixels[index(b+r+1)];let alpha=channel(c,24);for k in 0..3 {sums[k]+=channel(c,[16,8,0][k])*alpha;}sums[3]+=alpha;}
            }
        } pixels.copy_from_slice(&tmp);
    }
}
fn default_dpi()->u32{300}
#[derive(Serialize,Deserialize)]
struct SavedDocument {#[serde(default="default_dpi")]dpi:u32,version:u32,width:usize,height:usize,active:usize,background:u32,background_visible:bool,layers:Vec<SavedLayer>}
#[derive(Serialize,Deserialize)]
struct SavedLayer {#[serde(default)]locked:bool,id:u32,name:String,visible:bool,opacity:f32,blend:String,alpha_lock:bool,clipping:bool,runs:Vec<(u32,u32)>}
fn encode_runs(pixels:&[u32])->Vec<(u32,u32)> {
    let mut runs=vec![];for &p in pixels {if let Some((n,c))=runs.last_mut() {if *c==p {*n+=1;continue;}}runs.push((1,p));}runs
}
fn decode_runs(runs:&[(u32,u32)],count:usize)->Result<Vec<u32>,String> {
    let mut out=Vec::with_capacity(count);
    for &(n,c) in runs {if n==0 || n as usize>count-out.len() {return Err("Invalid pixel data".into());}out.resize(out.len()+n as usize,c);}
    if out.len()!=count {return Err("Incomplete pixel data".into());}Ok(out)
}

#[cfg(any(target_os="android", feature="host-jni"))]
mod android;

#[cfg(test)]
mod tests;

#[cfg(test)]
mod gpu_tests;
