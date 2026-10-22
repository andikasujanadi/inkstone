struct Params {canvas:vec4<u32>,rect:vec4<u32>,data:vec4<f32>,flags:vec4<u32>}
@group(0) @binding(0) var<storage,read> layer:array<u32>;
@group(0) @binding(1) var<storage,read_write> output:array<vec2<u32>>;
@group(0) @binding(2) var<uniform> p:Params;
fn unpack(c:u32)->vec4<f32>{return vec4<f32>(f32((c>>16u)&255u),f32((c>>8u)&255u),f32(c&255u),f32(c>>24u))/255.0;}
fn pack(c:vec4<f32>)->u32{let v=vec4<u32>(clamp(c,vec4(0.0),vec4(1.0))*255.0+0.5);return(v.w<<24u)|(v.x<<16u)|(v.y<<8u)|v.z;}
fn blend(dst:u32,src:u32,opacity:f32,mode:u32)->u32{
 let d=unpack(dst);let s=unpack(src);let sa=s.w*opacity;if(sa<=0.0){return dst;}if(mode==0u && sa>=1.0){return src;}
 let a=sa+d.w*(1.0-sa);var b=s.xyz;
 switch(mode){
 case 1u:{b=s.xyz*d.xyz;}
 case 2u:{b=select(1.0-min((1.0-d.xyz)/max(s.xyz,vec3(0.0000001)),vec3(1.0)),vec3(0.0),s.xyz<=vec3(0.0));}
 case 3u:{b=max(d.xyz+s.xyz-1.0,vec3(0.0));}
 case 4u:{b=select(min(d.xyz/max(1.0-s.xyz,vec3(0.0000001)),vec3(1.0)),vec3(1.0),s.xyz>=vec3(1.0));}
 case 5u:{b=max(d.xyz-s.xyz,vec3(0.0));}
 case 6u:{b=d.xyz+s.xyz-2.0*d.xyz*s.xyz;}
 case 7u:{b=select(1.0-2.0*(1.0-s.xyz)*(1.0-d.xyz),2.0*s.xyz*d.xyz,s.xyz<vec3(0.5));}
 case 8u:{b=1.0-(1.0-s.xyz)*(1.0-d.xyz);}
 case 9u:{b=select(1.0-2.0*(1.0-s.xyz)*(1.0-d.xyz),2.0*s.xyz*d.xyz,d.xyz<vec3(0.5));}
 case 10u:{b=min(s.xyz+d.xyz,vec3(1.0));}
 case 11u:{b=select(d.xyz,s.xyz,((src>>16u)&255u)+((src>>8u)&255u)+(src&255u)<((dst>>16u)&255u)+((dst>>8u)&255u)+(dst&255u));}
 case 12u:{b=min(s.xyz,d.xyz);}
 case 13u:{b=max(s.xyz,d.xyz);}
 case 14u:{b=abs(s.xyz-d.xyz);}
 default:{}
 }
 return pack(vec4((sa*(1.0-d.w)*s.xyz+sa*d.w*b+(1.0-sa)*d.w*d.xyz)/max(a,0.0001),a));
}
@compute @workgroup_size(8,8) fn composite(@builtin(global_invocation_id) id:vec3<u32>){
 if(any(id.xy>=p.rect.zw)){return;}let index=id.y*p.rect.z+id.x;
 var c=output[index].x;var base=bitcast<f32>(output[index].y);
 if(p.canvas.w!=0u){c=p.canvas.z;base=0.0;}
 let xy=id.xy+p.rect.xy;let src=layer[xy.y*p.canvas.x+xy.x];
 if(p.flags.z==0u){base=select(0.0,f32(src>>24u)/255.0,p.flags.y!=0u);}
 if(p.flags.y!=0u){c=blend(c,src,p.data.x*select(1.0,base,p.flags.z!=0u),p.flags.x);}
 output[index]=vec2(c,bitcast<u32>(base));
}
