struct Params {
    canvas: vec4<u32>,
    rect: vec4<u32>,
    dab: vec4<f32>,
    flags: vec4<u32>,
    region: vec4<u32>,
}
@group(0) @binding(0) var<storage, read_write> pixels: array<u32>;
@group(0) @binding(1) var<storage, read> selection: array<u32>;
@group(0) @binding(2) var<uniform> p: Params;
@group(0) @binding(3) var<storage, read_write> tip: array<u32>;
@group(0) @binding(4) var<storage, read_write> output_pixels: array<u32>;
fn unpack(c:u32)->vec4<f32>{return vec4<f32>(f32((c>>16u)&255u),f32((c>>8u)&255u),f32(c&255u),f32(c>>24u))/255.0;}
fn pack(c:vec4<f32>)->u32{let v=vec4<u32>(clamp(c,vec4(0.0),vec4(1.0))*255.0+0.5);return(v.w<<24u)|(v.x<<16u)|(v.y<<8u)|v.z;}
fn mix_rgba(a:u32,b:u32,t:f32)->u32{
 let av=unpack(a);let bv=unpack(b);let aa=av.w*(1.0-t);let ba=bv.w*t;let alpha=aa+ba;
 if(alpha<0.00001){return 0u;}return pack(vec4((av.xyz*aa+bv.xyz*ba)/alpha,alpha));
}
fn over(dst:u32,src:u32,opacity:f32)->u32{
 let d=unpack(dst);let s=unpack(src);let sa=s.w*opacity;if(sa<=0.0){return dst;}if(sa>=1.0){return src;}
 let a=sa+d.w*(1.0-sa);return pack(vec4((s.xyz*sa+d.xyz*d.w*(1.0-sa))/max(a,0.0001),a));
}
fn noise(x:u32,y:u32,seed:u32)->f32{
 var n=x*374761393u+y*668265263u+seed;n=(n^(n>>13u))*1274126177u;return f32((n^(n>>16u))&65535u)/65535.0;
}
fn sample_tip(pos:vec2<f32>)->u32{
 let v=clamp(pos,vec2(0.0),vec2(31.0));let q=vec2<u32>(v);let q1=min(q+vec2(1u),vec2(31u));let f=fract(v);
 return mix_rgba(mix_rgba(tip[q.y*32u+q.x],tip[q.y*32u+q1.x],f.x),mix_rgba(tip[q1.y*32u+q.x],tip[q1.y*32u+q1.x],f.x),f.y);
}
@compute @workgroup_size(8,8) fn pickup(@builtin(global_invocation_id) id:vec3<u32>){
 if(any(id.xy>=vec2(32u))){return;}let s=vec2<i32>(floor(p.dab.xy+(vec2<f32>(id.xy)+0.5-16.0)/16.0*p.dab.z));
 var c=0u;if(all(s>=vec2(0)) && all(s<vec2<i32>(p.canvas.xy))){c=pixels[u32(s.y)*p.canvas.x+u32(s.x)];}
 let i=id.y*32u+id.x;tip[1024u+i]=c;if(p.flags.w!=0u){tip[i]=c;}
}
@compute @workgroup_size(8,8) fn advance(@builtin(global_invocation_id) id:vec3<u32>){
 if(any(id.xy>=vec2(32u))){return;}let i=id.y*32u+id.x;tip[i]=mix_rgba(tip[i],tip[1024u+i],0.08);
}
@compute @workgroup_size(8,8) fn paint(@builtin(global_invocation_id) id:vec3<u32>){
 let xy=id.xy+p.rect.xy;if(any(xy>=p.rect.zw)){return;}let i=xy.y*p.canvas.x+xy.x;
 if(p.flags.z!=0u && selection[i]==0u){return;}
 let delta=vec2<f32>(xy)+0.5-p.dab.xy;let radius=p.dab.z;let d=length(delta)/max(radius,0.1);
 if(d>1.0+0.7/radius){return;}let edge=clamp((1.0-d)*radius+0.5,0.0,1.0);
 let smudging=p.canvas.z==6u || p.canvas.z>=128u;var shape=p.canvas.z;if(smudging){shape=select(2u,p.canvas.z&127u,p.canvas.z>=128u);}
 if(p.canvas.z>=64u && p.canvas.z<=78u){shape=p.canvas.z-64u;}
 var texture=1.0;
 switch(shape){
 case 1u:{texture=0.16+noise(xy.x,xy.y,91u)*0.58;}
 case 2u:{texture=max(exp(-4.0*d*d)-exp(-4.0),0.0)/(1.0-exp(-4.0))*0.12;}
 case 8u,9u:{let hard=select(0.35,0.7,shape==9u);let t=clamp((1.0-d)/(1.0-hard),0.0,1.0);texture=t*t*(3.0-2.0*t)*0.18;}
 case 10u:{texture=select(0.0,0.65*max(1.0-d,0.0),noise(xy.x,xy.y,p.flags.y)>0.965);}
 case 11u:{texture=select(0.0,0.12+abs(sin(delta.x*1.7))*0.4,abs(delta.y)<radius*0.55);}
 case 12u:{texture=select(0.0,0.2,abs(delta.y)<radius*0.48);}
 case 13u:{texture=pow(noise(xy.x,xy.y,19u),2.0)*0.38;}
 case 14u:{texture=(0.1+noise(xy.x,xy.y,73u)*0.3)*max(1.0-d*d,0.0);}
 case 3u:{texture=pow(noise(xy.x,xy.y,p.flags.y/15u),1.8)*0.7;}
 case 4u:{texture=pow(max(1.0-d*d,0.0),2.0)*0.10;}
 case 7u:{texture=select(0.0,0.85,abs(delta.x)<radius*0.32);}
 default:{}
 }
 if(shape>=16u && shape<=31u){let hard=f32(shape-16u)/15.0*0.95;let t=clamp((1.0-d)/(1.0-hard),0.0,1.0);texture=t*t*(3.0-2.0*t);}
 var hardness=1.0;
 if(p.canvas.z>=64u && p.canvas.z<=78u && (p.flags.x&255u)<255u){let hard=f32(p.flags.x&255u)/255.0*0.95;let t=clamp((1.0-d)/(1.0-hard),0.0,1.0);hardness=t*t*(3.0-2.0*t);}
 let alpha=clamp(p.dab.w*edge*texture*hardness,0.0,1.0);let dst=pixels[i];let lock=p.canvas.w!=0u;
 if(smudging){
  let carried=sample_tip((delta/radius+1.0)*16.0-0.5);var mask=edge;
  switch(shape){
  case 2u,4u:{mask=pow(max(1.0-d*d,0.0),2.0);}
  case 8u,9u:{let hard=select(0.35,0.7,shape==9u);let t=clamp((1.0-d)/(1.0-hard),0.0,1.0);mask=t*t*(3.0-2.0*t);}
  case 7u,10u,11u,12u,13u,14u:{mask=min(texture,1.0)*edge;}
  default:{}
  }
  let c=mix_rgba(dst,carried,clamp(p.dab.w*mask*0.32,0.0,1.0));pixels[i]=select(c,(c&0xffffffu)|(dst&0xff000000u),lock);
 }else if(p.canvas.z==5u || (p.canvas.z>=16u && p.canvas.z<=31u) || (p.canvas.z>=64u && p.canvas.z<=78u)){if(!lock){pixels[i]=(dst&0xffffffu)|(u32(f32(dst>>24u)*(1.0-alpha)+0.5)<<24u);}}
 else if(lock){if(dst>>24u!=0u){pixels[i]=(over(dst|0xff000000u,p.flags.x|0xff000000u,alpha)&0xffffffu)|(dst&0xff000000u);}}
 else{pixels[i]=over(dst,p.flags.x,alpha);}
}
@compute @workgroup_size(8,8) fn gather(@builtin(global_invocation_id) id:vec3<u32>){
 if(any(id.xy>=p.region.zw)){return;}let xy=p.region.xy+id.xy;output_pixels[id.y*p.region.z+id.x]=pixels[xy.y*p.canvas.x+xy.x];
}
