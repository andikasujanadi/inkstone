use inkstone::{Brush, Engine, Sample};
use serde_json::json;
fn main() {
    let mut e = Engine::new(48, 32).unwrap();
    e.command(&json!({"op":"background","visible":false}))
        .unwrap();
    e.command(&json!({"op":"layer","name":"Base · 底层"}))
        .unwrap();
    e.begin(
        Sample {
            x: 18.0,
            y: 16.0,
            pressure: 1.0,
            tilt: 0.0,
        },
        Brush {
            color: 0xffd35248,
            size: 18.0,
            pressure_size: 0.0,
            ..Brush::default()
        },
    );
    e.end();
    e.command(&json!({"op":"add"})).unwrap();
    e.command(&json!({"op":"layer","name":"Blue overlay","opacity":0.47,"blend":"Multiply","clipping":true})).unwrap();
    e.begin(
        Sample {
            x: 24.0,
            y: 16.0,
            pressure: 1.0,
            tilt: 0.0,
        },
        Brush {
            color: 0x800c80ed,
            size: 24.0,
            pressure_size: 0.0,
            ..Brush::default()
        },
    );
    e.end();
    e.command(&json!({"op":"layer","locked":true,"alphaLock":true}))
        .unwrap();
    e.command(&json!({"op":"add"})).unwrap();
    e.command(&json!({"op":"layer","name":"Hidden","visible":false}))
        .unwrap();
    e.command(&json!({"op":"fill","color":0xff44bb33u32}))
        .unwrap();
    e.command(&json!({"op":"add"})).unwrap();
    e.command(&json!({"op":"layer","name":"Empty"})).unwrap();
    let base = std::env::args()
        .nth(1)
        .unwrap_or_else(|| "artifacts/psd-fixture".into());
    std::fs::write(format!("{base}.json"), e.save().unwrap()).unwrap();
    std::fs::write(
        format!("{base}-merged.json"),
        serde_json::to_vec(&e.render_patch(true).unwrap()[4..]).unwrap(),
    )
    .unwrap();
    e.write_psd(&mut std::io::BufWriter::new(
        std::fs::File::create(format!("{base}.psd")).unwrap(),
    ))
    .unwrap();
}
