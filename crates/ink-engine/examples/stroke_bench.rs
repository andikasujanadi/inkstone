use inkstone::{Engine,Brush,Sample};
use std::time::Instant;
fn main(){
    let full=std::env::args().any(|a|a=="--full");
    let mut e=Engine::new(1600,1200).unwrap();e.render_patch(false);
    let mut bytes=vec![0u8;1600*1200*4];let mut times=vec![];let mut updates=0;let mut pixels=0usize;
    let total=Instant::now();
    for stroke in 0..40 {
        let start=Instant::now();let y=30.0+(stroke%20)as f32*50.0;
        e.begin(Sample{x:30.0,y,pressure:1.0,tilt:0.0},Brush{size:12.0,stabilization:0.0,..Brush::default()});
        for i in 1..=64 {
            e.sample(Sample{x:30.0+i as f32*20.0,y:y+(i as f32*0.15).sin()*10.0,pressure:0.8,tilt:0.0});
            if i%8==0 {if full{e.render(&mut bytes,true);pixels+=1600*1200;}else if let Some(p)=e.render_patch(false){pixels+=(p.len()-4) as usize;}updates+=1;}
        }
        e.end();times.push(start.elapsed().as_secs_f64()*1000.0);
    }
    println!("mode={} strokes=40 updates={} total_ms={:.2} first10_avg_ms={:.2} last10_avg_ms={:.2} pixels_per_update={}",if full{"full"}else{"dirty"},updates,total.elapsed().as_secs_f64()*1000.0,times[..10].iter().sum::<f64>()/10.0,times[30..].iter().sum::<f64>()/10.0,pixels/updates);
}
