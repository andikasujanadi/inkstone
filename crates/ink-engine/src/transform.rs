use super::*;

impl Engine {
    pub(super) fn transform_bounds(&self, moving: bool, layer: usize) -> [usize; 4] {
        if Arc::ptr_eq(&self.state.layers[layer].pixels,&self.empty_pixels){return [0;4];}
        let mut r = [self.width, self.height, 0, 0];
        for y in 0..self.height {
            for x in 0..self.width {
                let i = y * self.width + x;
                let coverage = self.selection_coverage(i);
                let belongs = if layer == self.state.active { if moving{coverage>0}else{coverage<255} } else { !moving };
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
                let coverage=if layer!=self.state.active{255}else if plane==count{self.selection_coverage(i) as u32}else{255-self.selection_coverage(i) as u32};
                let color=self.state.layers[layer].masked_pixel(i);out.push(if coverage==0{0}else{((color&0xffffff)|((((color>>24)*coverage+127)/255)<<24)) as i32});
            }}
        }
        out
    }

    pub fn transform_affine(&mut self,m:[f32;6])->Result<(),String>{let [a,b,c,d,x,y]=m;self.transform_projective([a,c,x,b,d,y,0.,0.,1.],"Bilinear")}
    pub fn transform_projective(&mut self,m:[f32;9],filter:&str)->Result<(),String>{
        if m.iter().any(|v|!v.is_finite() || v.abs()>1_000_000.){return Err("Invalid transform matrix".into());}
        if m==[1.,0.,0.,0.,1.,0.,0.,0.,1.]{return Ok(());}
        let [a,b,c,d,e,f,g,h,i]=m;let det=a*(e*i-f*h)-b*(d*i-f*g)+c*(d*h-e*g);
        if det.abs()<1e-8{return Err("Transform is too small or singular".into());}
        let inv=[(e*i-f*h)/det,(c*h-b*i)/det,(b*f-c*e)/det,(f*g-d*i)/det,(a*i-c*g)/det,(c*d-a*f)/det,(d*h-e*g)/det,(b*g-a*h)/det,(a*e-b*d)/det];
        let r=self.transform_bounds(true,self.state.active);if r[2]==0{return Ok(());}
        let corners=[(r[0] as f32,r[1] as f32),(r[2] as f32,r[1] as f32),(r[2] as f32,r[3] as f32),(r[0] as f32,r[3] as f32)];
        let mut target=[f32::INFINITY,f32::INFINITY,f32::NEG_INFINITY,f32::NEG_INFINITY];let mut sign=0.;
        for(x,y)in corners{let z=g*x+h*y+i;if z.abs()<1e-5 || (sign!=0. && z.signum()!=sign){return Err("Distort corners cross the projection horizon".into());}sign=z.signum();let (x,y)=((a*x+b*y+c)/z,(d*x+e*y+f)/z);target[0]=target[0].min(x);target[1]=target[1].min(y);target[2]=target[2].max(x);target[3]=target[3].max(y);}
        self.resample_transform(target,filter,|x,y|{let z=inv[6]*x+inv[7]*y+inv[8];if z.abs()<1e-6{None}else{Some(((inv[0]*x+inv[1]*y+inv[2])/z,(inv[3]*x+inv[4]*y+inv[5])/z))}})
    }
    pub fn transform_mesh(&mut self,columns:usize,rows:usize,points:&[(f32,f32)],filter:&str)->Result<(),String>{
        if !(1..=6).contains(&columns) || !(1..=6).contains(&rows) || points.len()!=(columns+1)*(rows+1) || points.iter().any(|(x,y)|!x.is_finite() || !y.is_finite() || x.abs()>1_000_000. || y.abs()>1_000_000.){return Err("Invalid warp grid".into());}
        let r=self.transform_bounds(true,self.state.active);if r[2]==0{return Ok(());}
        let mut cells=vec![];let mut orientation=0.;
        for row in 0..rows{for col in 0..columns{let at=row*(columns+1)+col;let q=[points[at],points[at+1],points[at+columns+2],points[at+columns+1]];let mut sign=0.;for n in 0..4{let a=q[n];let b=q[(n+1)%4];let c=q[(n+2)%4];let cross=(b.0-a.0)*(c.1-b.1)-(b.1-a.1)*(c.0-b.0);if cross.abs()<1e-5 || (sign!=0. && sign!=cross.signum()){return Err("Warp cells must not fold or cross".into());}sign=cross.signum();}if orientation!=0. && sign!=orientation{return Err("Warp cells must share an orientation".into());}orientation=sign;cells.push((col,row,q));}}
        let mut target=[f32::INFINITY,f32::INFINITY,f32::NEG_INFINITY,f32::NEG_INFINITY];for&(x,y)in points{target[0]=target[0].min(x);target[1]=target[1].min(y);target[2]=target[2].max(x);target[3]=target[3].max(y);}
        self.resample_transform(target,filter,|x,y|{
            for &(col,row,q)in &cells{
                if x<q.iter().map(|p|p.0).fold(f32::INFINITY,f32::min)-0.001 || x>q.iter().map(|p|p.0).fold(f32::NEG_INFINITY,f32::max)+0.001 || y<q.iter().map(|p|p.1).fold(f32::INFINITY,f32::min)-0.001 || y>q.iter().map(|p|p.1).fold(f32::NEG_INFINITY,f32::max)+0.001{continue;}
                let mut u=0.5;let mut v=0.5;
                for _ in 0..10{let fx=(1.-u)*(1.-v)*q[0].0+u*(1.-v)*q[1].0+u*v*q[2].0+(1.-u)*v*q[3].0-x;let fy=(1.-u)*(1.-v)*q[0].1+u*(1.-v)*q[1].1+u*v*q[2].1+(1.-u)*v*q[3].1-y;
                    let ux=(1.-v)*(q[1].0-q[0].0)+v*(q[2].0-q[3].0);let uy=(1.-v)*(q[1].1-q[0].1)+v*(q[2].1-q[3].1);let vx=(1.-u)*(q[3].0-q[0].0)+u*(q[2].0-q[1].0);let vy=(1.-u)*(q[3].1-q[0].1)+u*(q[2].1-q[1].1);let det=ux*vy-uy*vx;if det.abs()<1e-8{break;}u-=(fx*vy-fy*vx)/det;v-=(fy*ux-fx*uy)/det;if fx.abs()+fy.abs()<0.0001{break;}
                }
                if u>=-0.0001 && u<=1.0001 && v>=-0.0001 && v<=1.0001{return Some((r[0] as f32+(col as f32+u.clamp(0.,1.))/columns as f32*(r[2]-r[0]) as f32,r[1] as f32+(row as f32+v.clamp(0.,1.))/rows as f32*(r[3]-r[1]) as f32));}
            }None
        })
    }
    fn resample_transform(&mut self,target:[f32;4],filter:&str,map:impl Fn(f32,f32)->Option<(f32,f32)>)->Result<(),String>{
        let original_state=self.state.clone();let mask_before=self.state.layers[self.state.active].mask.clone();
        if mask_before.is_some() && self.selection.is_some(){return Err("Apply the layer mask before transforming a selection".into());}
        let before=self.state.layers[self.state.active].pixels.clone();let mut out=before.as_ref().clone();let mut mask_out=mask_before.as_ref().map(|_|vec![255u8;self.width*self.height]);
        for(i,p)in out.iter_mut().enumerate(){let remaining=255-self.selection_coverage(i) as u32;*p=if remaining==0{0}else{(*p&0xffffff)|(((((*p>>24)*remaining)+127)/255)<<24)};}
        let x0=(target[0].floor()-1.).clamp(0.,self.width as f32) as usize;let y0=(target[1].floor()-1.).clamp(0.,self.height as f32) as usize;let x1=(target[2].ceil()+1.).clamp(0.,self.width as f32) as usize;let y1=(target[3].ceil()+1.).clamp(0.,self.height as f32) as usize;
        for y in y0..y1{for x in x0..x1{let Some((sx,sy))=map(x as f32+0.5,y as f32+0.5)else{continue};if !sx.is_finite() || !sy.is_finite(){continue;}
            let ox=sx-0.5;let oy=sy-0.5;let ix=ox.floor() as i32;let iy=oy.floor() as i32;let fx=ox-ox.floor();let fy=oy-oy.floor();let mut rgba=[0.;4];let mut mask_value=0.;
            let samples=if filter=="Nearest"{[(ox.round() as i32,oy.round() as i32,1.),(0,0,0.),(0,0,0.),(0,0,0.)]}else{[(ix,iy,(1.-fx)*(1.-fy)),(ix+1,iy,fx*(1.-fy)),(ix,iy+1,(1.-fx)*fy),(ix+1,iy+1,fx*fy)]};
            for(xx,yy,weight)in samples{if weight==0. || xx<0 || yy<0 || xx>=self.width as i32 || yy>=self.height as i32{continue;}let i=yy as usize*self.width+xx as usize;let color=before[i];let alpha=channel(color,24)*weight*self.selection_coverage(i) as f32/255.;for(k,shift)in [16,8,0].into_iter().enumerate(){rgba[k]+=channel(color,shift)*alpha;}rgba[3]+=alpha;if let Some(mask)=&mask_before{mask_value+=mask[i] as f32*alpha;}}
            if rgba[3]>1e-6{let color=pack(rgba[0]/rgba[3],rgba[1]/rgba[3],rgba[2]/rgba[3],rgba[3]);let i=y*self.width+x;out[i]=composite(out[i],color,1.,"Normal");if let Some(mask)=mask_out.as_mut(){mask[i]=(mask_value/rgba[3]+0.5).clamp(0.,255.) as u8;}}
        }}
        self.state.layers[self.state.active].pixels=Arc::new(out);self.set_selection(None);
        if let Some(mask)=mask_out{self.state.layers[self.state.active].mask=Some(Arc::new(mask));self.record(Change::Structure{before:original_state,after:self.state.clone()});}else{self.pixel_change(self.state.active,before.as_ref().clone());}Ok(())
    }
}
