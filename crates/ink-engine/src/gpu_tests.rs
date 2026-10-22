use super::*;
#[test]
fn validate_compute_shaders_without_adapter() {
    for source in [include_str!("paint.wgsl"), include_str!("composite.wgsl")] {
        let module = naga::front::wgsl::parse_str(source).unwrap();
        naga::valid::Validator::new(
            naga::valid::ValidationFlags::all(),
            naga::valid::Capabilities::empty(),
        )
        .validate(&module)
        .unwrap();
    }
}
#[test]
fn cpu_renderer_preserves_artwork_and_history() {
    let mut e = Engine::new(64, 64).unwrap();
    e.begin(
        Sample {
            x: 20.0,
            y: 20.0,
            pressure: 1.0,
            tilt: 0.0,
        },
        Brush::default(),
    );
    e.end();
    let before = e.save().unwrap();
    let undo = e.undo.len();
    e.set_renderer("cpu").unwrap();
    assert_eq!(e.info()["renderer"], "cpu");
    assert_eq!(e.save().unwrap(), before);
    assert_eq!(e.undo.len(), undo);
    assert!(e.set_renderer("unsupported").is_err());
    assert!(e.undo());
    assert!(e.redo());
    assert_eq!(e.save().unwrap(), before);
}
fn gpu_engine(w: usize, h: usize) -> Engine {
    let mut e = Engine::new(w, h).unwrap();
    e.set_renderer("auto").unwrap();
    assert!(e.gpu.is_some(), "A physical GPU is required: {}", e.info());
    e
}
#[test]
#[ignore = "Requires a physical Metal or Vulkan adapter"]
fn physical_gpu_brush_smudge_history() {
    let mut gpu = gpu_engine(192, 128);
    let mut cpu = Engine::new(192, 128).unwrap();
    println!("GPU: {}", gpu.info());
    for kind in [
        0, 1, 2, 3, 4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 128, 130, 136, 139,
    ] {
        let brush = Brush {
            kind,
            color: 0xffd65a20,
            size: 42.0,
            stabilization: 0.0,
            pressure_size: 0.5,
            ..Brush::default()
        };
        for engine in [&mut gpu, &mut cpu] {
            engine.begin(
                Sample {
                    x: 32.0,
                    y: 45.0,
                    pressure: 0.4,
                    tilt: 0.3,
                },
                brush,
            );
            for x in (35..150).step_by(7) {
                engine.sample(Sample {
                    x: x as f32,
                    y: 45.0 + (x as f32 * 0.04).sin() * 13.0,
                    pressure: 0.8,
                    tilt: 0.3,
                });
                engine.flush_gpu();
            }
            engine.end();
        }
        let a = &gpu.state.layers[0].pixels;
        let b = &cpu.state.layers[0].pixels;
        let max_error = a
            .iter()
            .zip(b.iter())
            .flat_map(|(a, b)| {
                [0, 8, 16, 24].map(|s| ((a >> s & 255) as i32 - (b >> s & 255) as i32).abs())
            })
            .max()
            .unwrap();
        assert!(max_error <= 4, "kind {kind}, max channel error {max_error}");
        gpu.undo();
        gpu.redo();
    }
    assert!(gpu.info()["gpuBatches"].as_u64().unwrap() > 100);
    let batches = gpu.info()["gpuBatches"].as_u64().unwrap();
    gpu.begin(
        Sample {
            x: 50.0,
            y: 60.0,
            pressure: 1.0,
            tilt: 0.0,
        },
        Brush {
            size: 12.0,
            ..Brush::default()
        },
    );
    gpu.sample(Sample {
        x: 80.0,
        y: 60.0,
        pressure: 1.0,
        tilt: 0.0,
    });
    gpu.end();
    assert_eq!(
        gpu.info()["gpuBatches"].as_u64().unwrap(),
        batches,
        "Thin pens must avoid GPU submission overhead"
    );
    let saved = gpu.save().unwrap();
    let restored = Engine::load(&saved).unwrap();
    assert_eq!(gpu.state.layers[0].pixels, restored.state.layers[0].pixels);
    let before = gpu.state.layers[0].pixels.clone();
    gpu.begin(
        Sample {
            x: 80.0,
            y: 80.0,
            pressure: 1.0,
            tilt: 0.0,
        },
        Brush {
            size: 42.0,
            ..Brush::default()
        },
    );
    let undo_count = gpu.undo.len();
    gpu.sample(Sample {
        x: 100.0,
        y: 84.0,
        pressure: 0.8,
        tilt: 0.0,
    });
    let mid_stroke = gpu.save().unwrap();
    assert!(gpu.stroke.is_some(), "Saving must not end a GPU stroke");
    assert_eq!(undo_count, gpu.undo.len());
    assert_eq!(
        Engine::load(&mid_stroke).unwrap().state.layers[0].pixels,
        gpu.state.layers[0].pixels
    );
    gpu.sample(Sample {
        x: 115.0,
        y: 84.0,
        pressure: 0.8,
        tilt: 0.0,
    });
    gpu.flush_gpu();
    gpu.cancel();
    assert_eq!(before, gpu.state.layers[0].pixels);
}
#[test]
#[ignore = "Requires a physical Metal or Vulkan adapter"]
fn physical_gpu_composite_selection_and_recovery() {
    let mut e = gpu_engine(96, 64);
    let modes = [
        "Normal",
        "Multiply",
        "Colour Burn",
        "Linear Burn",
        "Colour Dodge",
        "Subtract",
        "Exclusion",
        "Hard Light",
        "Screen",
        "Overlay",
        "Add",
        "Darker Colour",
        "Darken",
        "Lighten",
        "Difference",
    ];
    for i in 0..3 {
        if i > 0 {
            e.command(&json!({"op":"add"})).unwrap();
        }
        let pixels = Arc::make_mut(&mut e.state.layers[i].pixels);
        for (n, pixel) in pixels.iter_mut().enumerate() {
            *pixel = ((n as u32 * 173 + 89) % 256) << 24
                | ((n as u32 * 379 + i as u32 * 123456) & 0xffffff);
        }
    }
    e.state.layers[1].opacity = 0.47;
    e.state.layers[2].clipping = true;
    for mode in modes {
        for visible in [true, false] {
            for background in [true, false] {
                e.state.layers[1].blend = mode.into();
                e.state.layers[1].visible = visible;
                e.state.background_visible = background;
                let patch = e.render_patch(true).unwrap();
                assert!(e.gpu.is_some(), "{}", e.info());
                for y in 0..64 {
                    for x in 0..96 {
                        let a = patch[4 + y * 96 + x] as u32;
                        let b = e.color_at(x, y, true);
                        for shift in [0, 8, 16, 24] {
                            assert!(
                                ((a >> shift & 255) as i32 - (b >> shift & 255) as i32).abs() <= 1,
                                "{mode} at {x},{y}: {a:x} vs {b:x}"
                            );
                        }
                    }
                }
            }
        }
    }
    e.selection = Some((0..96 * 64).map(|i| i % 96 < 48).collect());
    e.state.active = 0;
    e.state.layers[0].alpha_lock = true;
    let before = e.state.layers[0].pixels.clone();
    e.begin(
        Sample {
            x: 47.0,
            y: 30.0,
            pressure: 1.0,
            tilt: 0.0,
        },
        Brush {
            size: 60.0,
            ..Brush::default()
        },
    );
    e.end();
    let after = e.state.layers[0].pixels.clone();
    assert_ne!(before, after);
    for i in 0..before.len() {
        if i % 96 >= 48 {
            assert_eq!(before[i], after[i]);
        }
        assert_eq!(before[i] >> 24, after[i] >> 24);
    }
    e.undo();
    assert_eq!(e.state.layers[0].pixels, before);
    e.redo();
    assert_eq!(e.state.layers[0].pixels, after);
    // Destroying a real device must replay the uncommitted batch on the CPU mirror.
    e.gpu.as_ref().unwrap().destroy_for_test();
    let mut cpu = Engine::load(&e.save().unwrap()).unwrap();
    cpu.selection = e.selection.clone();
    for engine in [&mut e, &mut cpu] {
        engine.begin(
            Sample {
                x: 20.0,
                y: 20.0,
                pressure: 0.6,
                tilt: 0.0,
            },
            Brush {
                size: 40.0,
                kind: 3,
                ..Brush::default()
            },
        );
        engine.sample(Sample {
            x: 30.0,
            y: 28.0,
            pressure: 0.9,
            tilt: 0.0,
        });
        engine.end();
    }
    assert!(e.gpu.is_none());
    assert!(e.renderer_error.is_some());
    assert_eq!(e.state.layers[0].pixels, cpu.state.layers[0].pixels);
}
#[test]
#[ignore = "Requires a physical Metal or Vulkan adapter"]
fn physical_gpu_large_brush_timing() {
    let mut gpu = gpu_engine(1600, 1200);
    let mut cpu = Engine::new(1600, 1200).unwrap();
    for (label, kind, size) in [
        ("pen", 0, 12.0),
        ("airbrush", 2, 400.0),
        ("smudge", 130, 400.0),
    ] {
        for (backend, engine) in [("cpu", &mut cpu), ("auto", &mut gpu)] {
            engine
                .command(&json!({"op":"fill","color":0xff3498abu32}))
                .unwrap();
            engine.render_patch(true);
            let start = std::time::Instant::now();
            engine.begin(
                Sample {
                    x: 150.0,
                    y: 300.0,
                    pressure: 1.0,
                    tilt: 0.0,
                },
                Brush {
                    kind,
                    size,
                    stabilization: 0.0,
                    ..Brush::default()
                },
            );
            for batch in 0..20 {
                for n in 0..4 {
                    let x = 150.0 + (batch * 4 + n) as f32 * 14.0;
                    engine.sample(Sample {
                        x,
                        y: 500.0 + (x * 0.005).sin() * 200.0,
                        pressure: 0.8,
                        tilt: 0.0,
                    });
                }
                engine.flush_gpu();
                engine.render_patch(false);
            }
            engine.end();
            println!("{label} {backend}: {} ms", start.elapsed().as_millis());
        }
    }
}
