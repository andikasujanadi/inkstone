use super::*;

impl Engine {
    pub(super) fn transform_bounds(&self, moving: bool, layer: usize) -> [usize; 4] {
        let mut r = [self.width, self.height, 0, 0];
        for y in 0..self.height {
            for x in 0..self.width {
                let i = y * self.width + x;
                let selected = self.selection.as_ref().is_none_or(|s| s[i]);
                let belongs = if layer == self.state.active { selected == moving } else { !moving };
                if belongs && self.state.layers[layer].pixels[i] >> 24 != 0 {
                    r[0] = r[0].min(x); r[1] = r[1].min(y);
                    r[2] = r[2].max(x + 1); r[3] = r[3].max(y + 1);
                }
            }
        }
        if r[2] == 0 { [0; 4] } else { r }
    }

    // Cropped layer planes plus the moving selection; at most six million preview pixels.
    pub fn transform_snapshot(&mut self) -> Vec<i32> {
        self.end(); self.cancel_flood();
        let count = self.state.layers.len();
        let mut rects: Vec<_> = (0..count).map(|i| self.transform_bounds(false, i)).collect();
        let moving = self.transform_bounds(true, self.state.active);
        rects.push(moving);
        let area: usize = rects.iter().map(|r| (r[2] - r[0]) * (r[3] - r[1])).sum();
        let scale = (1536.0 / self.width.max(self.height) as f32).min(1.0)
            .min((6_000_000.0 / area.max(1) as f32).sqrt());
        let mut out = vec![count as i32, self.state.active as i32];
        out.extend(moving.map(|n| n as i32));
        for (plane, r) in rects.into_iter().enumerate() {
            let w = ((r[2] - r[0]) as f32 * scale).floor() as usize;
            let h = ((r[3] - r[1]) as f32 * scale).floor() as usize;
            let (w, h) = if r[2] > r[0] { (w.max(1), h.max(1)) } else { (0, 0) };
            out.extend(r.map(|n| n as i32)); out.extend([w as i32, h as i32]);
            let layer = plane.min(count - 1);
            let layer = if plane == count { self.state.active } else { layer };
            for y in 0..h { for x in 0..w {
                let sx = r[0] + x * (r[2] - r[0]) / w;
                let sy = r[1] + y * (r[3] - r[1]) / h;
                let i = sy * self.width + sx;
                let selected = self.selection.as_ref().is_none_or(|s| s[i]);
                let belongs = layer != self.state.active || selected == (plane == count);
                out.push(if belongs { self.state.layers[layer].masked_pixel(i) as i32 } else { 0 });
            }}
        }
        out
    }

    pub fn transform_affine(&mut self, m: [f32; 6]) -> Result<(), String> {
        let [a, b, c, d, tx, ty] = m;
        let det = a * d - b * c;
        if m.iter().any(|n| !n.is_finite() || n.abs() > 1_000_000.0) || det.abs() < 1e-6 {
            return Err("Invalid object transform".into());
        }
        if m == [1.0, 0.0, 0.0, 1.0, 0.0, 0.0] { return Ok(()); }
        let bounds = self.transform_bounds(true, self.state.active);
        if bounds[2] == 0 { return Ok(()); }
        let original_state=self.state.clone();
        let mask_before=self.state.layers[self.state.active].mask.clone();
        if mask_before.is_some() && self.selection.is_some(){return Err("Apply the layer mask before transforming a selection".into());}
        let mut mask_out=mask_before.as_ref().map(|_|vec![255u8;self.width*self.height]);
        let before = self.state.layers[self.state.active].pixels.clone();
        let selected = |i| self.selection.as_ref().is_none_or(|s| s[i]);
        let mut out = before.as_ref().clone();
        for (i, p) in out.iter_mut().enumerate() { if selected(i) { *p = 0; } }
        let mut target = [f32::INFINITY, f32::INFINITY, f32::NEG_INFINITY, f32::NEG_INFINITY];
        for (x, y) in [(bounds[0], bounds[1]), (bounds[2], bounds[1]), (bounds[2], bounds[3]), (bounds[0], bounds[3])] {
            let (x, y) = (a*x as f32+c*y as f32+tx, b*x as f32+d*y as f32+ty);
            target[0] = target[0].min(x); target[1] = target[1].min(y);
            target[2] = target[2].max(x); target[3] = target[3].max(y);
        }
        let x0 = (target[0].floor()-1.0).clamp(0.0, self.width as f32) as usize;
        let y0 = (target[1].floor()-1.0).clamp(0.0, self.height as f32) as usize;
        let x1 = (target[2].ceil()+1.0).clamp(0.0, self.width as f32) as usize;
        let y1 = (target[3].ceil()+1.0).clamp(0.0, self.height as f32) as usize;
        for y in y0..y1 { for x in x0..x1 {
            let (px, py) = (x as f32+0.5-tx, y as f32+0.5-ty);
            let (ox, oy) = ((d*px-c*py)/det-0.5, (-b*px+a*py)/det-0.5);
            let (ix, iy) = (ox.floor() as i32, oy.floor() as i32);
            let (fx, fy) = (ox-ox.floor(), oy-oy.floor());
            let mut rgba = [0.0; 4];let mut mask_value=0.0;
            for (xx, yy, weight) in [(ix,iy,(1.0-fx)*(1.0-fy)),(ix+1,iy,fx*(1.0-fy)),(ix,iy+1,(1.0-fx)*fy),(ix+1,iy+1,fx*fy)] {
                if xx < 0 || yy < 0 || xx >= self.width as i32 || yy >= self.height as i32 { continue; }
                let i = yy as usize*self.width+xx as usize;
                if !selected(i) { continue; }
                let color = before[i]; let alpha = channel(color,24)*weight;
                for (k, shift) in [16,8,0].into_iter().enumerate() { rgba[k] += channel(color,shift)*alpha; }
                rgba[3] += alpha;
                if let Some(mask)=&mask_before{mask_value+=mask[i] as f32*alpha;}
            }
            if rgba[3] > 1e-6 {
                let color = pack(rgba[0]/rgba[3],rgba[1]/rgba[3],rgba[2]/rgba[3],rgba[3]);
                let i = y*self.width+x; out[i] = composite(out[i],color,1.0,"Normal");
                if let Some(mask)=mask_out.as_mut(){mask[i]=(mask_value/rgba[3]+0.5).clamp(0.0,255.0) as u8;}
            }
        }}
        self.state.layers[self.state.active].pixels = Arc::new(out);
        self.selection = None;
        if let Some(mask)=mask_out{self.state.layers[self.state.active].mask=Some(Arc::new(mask));self.record(Change::Structure{before:original_state,after:self.state.clone()});}
        else{self.pixel_change(self.state.active, before.as_ref().clone());}
        Ok(())
    }
}
