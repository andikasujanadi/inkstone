use super::*;

fn dot(e:&mut Engine,x:f32,y:f32,size:f32,color:u32) {
    e.begin(Sample{x,y,pressure:1.0,tilt:0.0},Brush{size,color,stabilization:0.0,..Brush::default()});e.end();
}
#[test]
fn stroke_undo_redo_restores_exact_pixels() {
    let mut e=Engine::new(64,64).unwrap();let original=e.save().unwrap();
    dot(&mut e,32.0,32.0,12.0,0xff112233);let drawn=e.save().unwrap();assert_ne!(original,drawn);
    assert!(e.undo());assert_eq!(e.save().unwrap(),original);assert!(e.redo());assert_eq!(e.save().unwrap(),drawn);
}
#[test]
fn pressure_changes_brush_footprint() {
    let mut low=Engine::new(64,64).unwrap();let mut high=Engine::new(64,64).unwrap();
    for (e,p) in [(&mut low,0.1),(&mut high,1.0)] {
        e.begin(Sample{x:32.0,y:32.0,pressure:p,tilt:0.0},Brush{size:32.0,pressure_size:1.0,..Brush::default()});e.end();
    }
    let count=|e:&Engine|e.state.layers[0].pixels.iter().filter(|p|**p>>24>0).count();
    assert!(count(&high)>count(&low)*4);
}
#[test]
fn multitouch_cancellation_discards_only_pending_stroke() {
    let mut e=Engine::new(32,32).unwrap();dot(&mut e,8.0,8.0,5.0,0xff000000);let saved=e.save().unwrap();
    e.begin(Sample{x:24.0,y:24.0,pressure:1.0,tilt:0.0},Brush::default());e.cancel();
    assert_eq!(e.save().unwrap(),saved);assert_eq!(e.undo.len(),1);
}
#[test]
fn eraser_preserves_other_layers_and_undo() {
    let mut e=Engine::new(32,32).unwrap();dot(&mut e,16.0,16.0,12.0,0xffff0000);
    e.command(&json!({"op":"add"})).unwrap();dot(&mut e,16.0,16.0,12.0,0xff0000ff);
    let saved=e.save().unwrap();e.begin(Sample{x:16.0,y:16.0,pressure:1.0,tilt:0.0},Brush{kind:5,size:20.0,..Brush::default()});e.end();
    assert_eq!(e.color_at(16,16,false),0xffff0000);e.undo();assert_eq!(e.save().unwrap(),saved);
}
#[test]
fn selection_limits_paint_fill_and_adjustments() {
    let mut e=Engine::new(32,32).unwrap();e.command(&json!({"op":"select","shape":"Rectangle","points":[[8,8],[16,16]]})).unwrap();
    e.command(&json!({"op":"fill","color":0xffff0000u32})).unwrap();
    assert_eq!(e.state.layers[0].pixels[10*32+10],0xffff0000);assert_eq!(e.state.layers[0].pixels[20*32+20],0);
    e.command(&json!({"op":"adjust","kind":"Invert"})).unwrap();assert_eq!(e.state.layers[0].pixels[10*32+10],0xff00ffff);
}
#[test]
fn flood_respects_enclosed_region() {
    let mut e=Engine::new(32,32).unwrap();
    for x in 5..27 {dot(&mut e,x as f32,5.0,3.0,0xff000000);dot(&mut e,x as f32,26.0,3.0,0xff000000);}
    for y in 5..27 {dot(&mut e,5.0,y as f32,3.0,0xff000000);dot(&mut e,26.0,y as f32,3.0,0xff000000);}
    e.command(&json!({"op":"flood","x":16,"y":16,"color":0xff00ff00u32})).unwrap();
    assert_eq!(e.color_at(16,16,false),0xff00ff00);assert_eq!(e.color_at(0,0,false),0);
}
#[test]
fn alpha_lock_preserves_coverage() {
    let mut e=Engine::new(32,32).unwrap();dot(&mut e,16.0,16.0,10.0,0xff000000);
    let alpha:Vec<_>=e.state.layers[0].pixels.iter().map(|p|p>>24).collect();
    e.command(&json!({"op":"layer","alphaLock":true})).unwrap();e.command(&json!({"op":"fill","color":0xffff0000u32})).unwrap();
    assert_eq!(alpha,e.state.layers[0].pixels.iter().map(|p|p>>24).collect::<Vec<_>>());
}
#[test]
fn document_roundtrip_and_malformed_input() {
    let mut e=Engine::new(32,32).unwrap();dot(&mut e,15.0,15.0,8.0,0x8899aabb);
    e.command(&json!({"op":"duplicate"})).unwrap();e.command(&json!({"op":"layer","opacity":0.5,"blend":"Multiply"})).unwrap();
    let bytes=e.save().unwrap();let restored=Engine::load(&bytes).unwrap();assert_eq!(restored.save().unwrap(),bytes);
    assert!(Engine::load(b"{}").is_err());assert!(Engine::new(8192,8192).is_err());
    assert!(decode_runs(&[(u32::MAX,0)],1024).is_err());assert!(decode_runs(&[(1,0)],1024).is_err());
}
#[test]
fn transform_is_undoable_and_selection_moves_only_selected_pixels() {
    let mut e=Engine::new(32,32).unwrap();dot(&mut e,8.0,8.0,4.0,0xff000000);dot(&mut e,24.0,24.0,4.0,0xffff0000);
    e.command(&json!({"op":"select","points":[[4,4],[12,12]]})).unwrap();let saved=e.save().unwrap();
    e.command(&json!({"op":"transform","dx":8,"dy":0})).unwrap();
    assert_eq!(e.color_at(8,8,false),0);assert_eq!(e.color_at(16,8,false),0xff000000);assert_eq!(e.color_at(24,24,false),0xffff0000);
    e.undo();assert_eq!(e.save().unwrap(),saved);
}
#[test]
fn layer_structure_is_undoable_and_new_strokes_clear_redo() {
    let mut e=Engine::new(32,32).unwrap();e.command(&json!({"op":"add"})).unwrap();assert_eq!(e.state.layers.len(),2);
    e.undo();assert_eq!(e.state.layers.len(),1);e.redo();assert_eq!(e.state.layers.len(),2);
    e.command(&json!({"op":"reorder","index":1,"to":0})).unwrap();assert_eq!(e.state.active,0);
    e.undo();dot(&mut e,16.0,16.0,4.0,0xff000000);assert!(!e.redo());
}
#[test]
fn transparent_export_is_premultiplied_and_background_can_be_hidden() {
    let mut e=Engine::new(16,16).unwrap();e.command(&json!({"op":"fill","color":0x80ff0000u32})).unwrap();
    let mut bytes=vec![0;16*16*4];e.render(&mut bytes,false);assert_eq!(&bytes[0..4],&[128,0,0,128]);
    e.render(&mut bytes,true);assert_eq!(bytes[3],255);assert!(!e.render(&mut [0;3],true));
}
#[test]
fn supported_blend_modes_have_expected_outputs() {
    assert_eq!(composite(0xffffffff,0xff445566,1.0,"Multiply"),0xff445566);
    assert_eq!(composite(0xff000000,0xff445566,1.0,"Screen"),0xff445566);
    assert_eq!(composite(0,0x80445566,1.0,"Normal"),0x80445566);
}
#[test]
fn nan_input_does_not_corrupt_drawing() {
    let mut e=Engine::new(32,32).unwrap();let before=e.save().unwrap();
    e.begin(Sample{x:f32::NAN,y:0.0,pressure:1.0,tilt:0.0},Brush::default());e.end();assert_eq!(e.save().unwrap(),before);
}

#[test]
fn smudge_does_not_introduce_new_paint_on_empty_pixels() {
    let mut e=Engine::new(32,32).unwrap();let before=e.save().unwrap();
    e.begin(Sample{x:8.0,y:8.0,pressure:1.0,tilt:0.0},Brush{kind:6,color:0xffff0000,..Brush::default()});
    e.sample(Sample{x:24.0,y:24.0,pressure:1.0,tilt:0.0});e.end();
    assert_eq!(e.save().unwrap(),before);
}

#[test]
fn canvas_flip_moves_every_layer_and_is_undoable() {
    let mut e=Engine::new(32,32).unwrap();dot(&mut e,5.5,5.5,3.0,0xffff0000);
    e.command(&json!({"op":"add"})).unwrap();dot(&mut e,10.5,10.5,3.0,0xff0000ff);
    let before=e.save().unwrap();e.command(&json!({"op":"flipCanvas","horizontal":true})).unwrap();
    assert_eq!(e.color_at(26,5,false),0xffff0000);assert_eq!(e.color_at(21,10,false),0xff0000ff);
    e.undo();assert_eq!(e.save().unwrap(),before);
}

#[test]
fn merge_does_not_reveal_hidden_lower_layer() {
    let mut e=Engine::new(32,32).unwrap();dot(&mut e,5.0,5.0,4.0,0xffff0000);
    e.command(&json!({"op":"layer","visible":false})).unwrap();e.command(&json!({"op":"add"})).unwrap();
    dot(&mut e,24.0,24.0,4.0,0xff0000ff);e.command(&json!({"op":"merge"})).unwrap();
    assert_eq!(e.color_at(5,5,false),0);assert_eq!(e.color_at(24,24,false),0xff0000ff);
}

#[test]
fn saving_during_a_long_stroke_does_not_end_or_split_it() {
    let mut e=Engine::new(512,64).unwrap();
    let brush=Brush{size:6.0,pressure_size:0.0,stabilization:0.0,..Brush::default()};
    e.begin(Sample{x:10.0,y:32.0,pressure:1.0,tilt:0.0},brush);
    for x in (20..500).step_by(10) {
        if x%50==0 {
            let saved=e.save().unwrap();assert!(Engine::load(&saved).is_ok());
            assert_eq!(e.info()["undo"],0,"Save must leave the active stroke open");
        }
        e.sample(Sample{x:x as f32,y:32.0,pressure:1.0,tilt:0.0});
    }
    e.end();assert_eq!(e.info()["undo"],1);
    for x in 10..490 {assert!(e.color_at(x,32,false)>>24>0,"Gap at {x}");}
    e.undo();assert!((0..512).all(|x|e.color_at(x,32,false)==0));
}

#[test]
fn incremental_frames_match_full_compositing_and_invalidate_on_edits() {
    let mut e=Engine::new(128,96).unwrap();
    let mut frame=vec![0u32;128*96];
    fn apply(e:&mut Engine,frame:&mut[u32]) {
        if let Some(p)=e.render_patch(false){let(x,y,w,h)=(p[0]as usize,p[1]as usize,p[2]as usize,p[3]as usize);
            for row in 0..h {for col in 0..w {frame[(y+row)*e.width+x+col]=p[4+row*w+col]as u32;}}
        }
        for y in 0..e.height {for x in 0..e.width {assert_eq!(frame[y*e.width+x],e.color_at(x,y,true),"Frame mismatch at {x},{y}");}}
    }
    apply(&mut e,&mut frame);assert!(e.render_patch(false).is_none());
    for kind in [0,2,5] {
        e.begin(Sample{x:10.0,y:20.0,pressure:1.0,tilt:0.0},Brush{kind,size:12.0,color:0x99dc5020,..Brush::default()});
        for x in (15..110).step_by(5){e.sample(Sample{x:x as f32,y:40.0,pressure:0.7,tilt:0.0});apply(&mut e,&mut frame);}
        e.end();apply(&mut e,&mut frame);
    }
    for command in [json!({"op":"undo"}),json!({"op":"redo"}),json!({"op":"background","color":0xffabcdefu32}),json!({"op":"add"}),json!({"op":"layer","opacity":0.4})] {
        e.command(&command).unwrap();apply(&mut e,&mut frame);
    }
    e.begin(Sample{x:40.0,y:30.0,pressure:1.0,tilt:0.0},Brush::default());apply(&mut e,&mut frame);
    e.cancel();apply(&mut e,&mut frame);
}

#[test]
fn repeated_marks_do_not_expand_frame_damage_to_old_strokes() {
    let mut e=Engine::new(1600,1200).unwrap();e.render_patch(false).unwrap();
    for i in 0..80 {
        let x=20.0+(i%20)as f32*70.0;let y=20.0+(i/20)as f32*70.0;
        dot(&mut e,x,y,10.0,0xff102030);
        let patch=e.render_patch(false).unwrap();
        assert!(patch[2]<=14 && patch[3]<=14,"Damage accumulated across strokes");
        assert!(patch.len()<201);
    }
}

#[test]
fn layer_lock_blocks_paint_and_destructive_edits_and_roundtrips() {
    let mut e=Engine::new(32,32).unwrap();dot(&mut e,12.0,12.0,4.0,0xffff0000);
    e.command(&json!({"op":"layer","locked":true})).unwrap();let before=e.save().unwrap();
    dot(&mut e,24.0,24.0,6.0,0xff0000ff);assert_eq!(e.save().unwrap(),before);
    assert!(e.command(&json!({"op":"clear"})).is_err());
    assert!(e.command(&json!({"op":"fill","color":0xff000000u32})).is_err());
    assert!(e.command(&json!({"op":"transform","dx":2})).is_err());
    let restored=Engine::load(&before).unwrap();assert_eq!(restored.info()["layers"][0]["locked"],true);
    e.command(&json!({"op":"layer","locked":false})).unwrap();dot(&mut e,24.0,24.0,6.0,0xff0000ff);
    assert_eq!(e.color_at(24,24,false),0xff0000ff);
}

#[test]
fn canvas_dpi_and_old_documents_are_compatible() {
    let mut e=Engine::new(64,96).unwrap();
    e.command(&json!({"op":"setup","dpi":150,"color":0xff112233u32,"visible":false})).unwrap();
    assert_eq!(e.info()["undo"],0);let bytes=e.save().unwrap();let restored=Engine::load(&bytes).unwrap();
    assert_eq!(restored.info()["dpi"],150);assert_eq!(restored.info()["backgroundVisible"],false);
    let mut old:Value=serde_json::from_slice(&bytes).unwrap();old.as_object_mut().unwrap().remove("dpi");
    for layer in old["layers"].as_array_mut().unwrap(){layer.as_object_mut().unwrap().remove("locked");}
    let old=Engine::load(&serde_json::to_vec(&old).unwrap()).unwrap();
    assert_eq!(old.info()["dpi"],300);assert_eq!(old.info()["layers"][0]["locked"],false);
}

#[test]
fn colordrop_preview_is_reversible_and_commits_one_undo() {
    let mut e=Engine::new(32,32).unwrap();
    let pixels=Arc::make_mut(&mut e.state.layers[0].pixels);
    for y in 0..32 {for x in 0..32 {pixels[y*32+x]=if x<16 {0xffeeeeee}else{0xffcccccc};}}
    let original=e.save().unwrap();
    e.command(&json!({"op":"floodPreview","x":8,"y":8,"color":0xffff0000u32,"threshold":0.02})).unwrap();
    assert_eq!(e.state.layers[0].pixels[8*32+8],0xffff0000);
    assert_eq!(e.state.layers[0].pixels[8*32+24],0xffcccccc);
    assert_eq!(e.undo.len(),0);assert_eq!(e.save().unwrap(),original);
    e.command(&json!({"op":"floodPreview","x":8,"y":8,"color":0xffff0000u32,"threshold":0.3})).unwrap();
    assert_eq!(e.state.layers[0].pixels[8*32+24],0xffff0000);
    e.command(&json!({"op":"floodPreview","x":8,"y":8,"color":0xffff0000u32,"threshold":0.02})).unwrap();
    assert_eq!(e.state.layers[0].pixels[8*32+24],0xffcccccc);
    e.command(&json!({"op":"floodCancel"})).unwrap();assert_eq!(e.save().unwrap(),original);
    e.command(&json!({"op":"floodPreview","x":8,"y":8,"color":0xff00ff00u32,"threshold":0.02})).unwrap();
    e.command(&json!({"op":"floodCommit"})).unwrap();assert_eq!(e.undo.len(),1);
    assert!(e.undo());assert_eq!(e.save().unwrap(),original);assert!(e.redo());
    assert_eq!(e.state.layers[0].pixels[8*32+8],0xff00ff00);
}

#[test]
fn airbrush_has_smooth_falloff_and_distinct_hardness() {
    let mut soft=Engine::new(96,96).unwrap();let mut medium=Engine::new(96,96).unwrap();let mut hard=Engine::new(96,96).unwrap();
    for (e,kind) in [(&mut soft,2),(&mut medium,8),(&mut hard,9)] {
        e.begin(Sample{x:48.0,y:48.0,pressure:1.0,tilt:0.0},Brush{kind,size:64.0,pressure_size:0.0,..Brush::default()});e.end();
    }
    let alpha=|e:&Engine,x:usize|e.state.layers[0].pixels[48*96+x]>>24;
    assert!(alpha(&soft,48)>alpha(&soft,60));assert!(alpha(&soft,60)>alpha(&soft,72));
    assert!(alpha(&medium,65)>alpha(&soft,65));assert!(alpha(&hard,72)>alpha(&medium,72));
    assert_eq!(alpha(&soft,81),0);
}

#[test]
fn smudge_transports_a_multicolor_tip_without_using_active_color() {
    let mut e=Engine::new(96,64).unwrap();
    let pixels=Arc::make_mut(&mut e.state.layers[0].pixels);
    for y in 0..64 {for x in 0..96 {pixels[y*96+x]=if x<48 {if y<32{0xffff0000}else{0xff00ff00}}else{0xff0000ff};}}
    let original=e.save().unwrap();
    e.begin(Sample{x:40.0,y:32.0,pressure:1.0,tilt:0.0},Brush{kind:128+2,size:28.0,color:0xffffffff,pressure_size:0.0,stabilization:0.0,..Brush::default()});
    assert_eq!(e.save().unwrap(),original,"initial pickup must not paint");
    e.sample(Sample{x:60.0,y:32.0,pressure:1.0,tilt:0.0});e.end();
    let upper=e.state.layers[0].pixels[27*96+53];let lower=e.state.layers[0].pixels[37*96+53];
    assert!(channel(upper,16)>channel(upper,8));assert!(channel(lower,8)>channel(lower,16));
    assert!(channel(upper,0)>0.0 && channel(upper,16)>0.0);
    assert_ne!(upper,0xffffffff);assert!(e.undo());assert_eq!(e.save().unwrap(),original);
}

#[test]
fn colordrop_treats_hidden_rgb_as_transparent_and_respects_locks(){
    let mut e=Engine::new(32,32).unwrap();
    Arc::make_mut(&mut e.state.layers[0].pixels)[16*32+16]=0x00ff0000;
    e.command(&json!({"op":"floodPreview","x":16,"y":16,"threshold":0.0,"color":0xff3050a0u32})).unwrap();
    assert!(e.state.layers[0].pixels.iter().all(|p|*p==0xff3050a0));
    e.command(&json!({"op":"floodCancel"})).unwrap();
    e.command(&json!({"op":"layer","locked":true})).unwrap();
    let saved=e.save().unwrap();
    assert!(e.command(&json!({"op":"floodPreview","x":16,"y":16,"color":0xff3050a0u32})).is_err());
    assert_eq!(saved,e.save().unwrap());
}

#[test]
fn colordrop_stays_inside_a_closed_outline(){
    let mut e=Engine::new(40,40).unwrap();let data=Arc::make_mut(&mut e.state.layers[0].pixels);
    for i in 8..32 {data[8*40+i]=0xff000000;data[31*40+i]=0xff000000;data[i*40+8]=0xff000000;data[i*40+31]=0xff000000;}
    e.command(&json!({"op":"floodPreview","x":20,"y":20,"threshold":0.12,"color":0xffeb5b40u32})).unwrap();
    assert_eq!(e.state.layers[0].pixels[20*40+20],0xffeb5b40);
    assert_eq!(e.state.layers[0].pixels[4*40+4],0);assert_eq!(e.state.layers[0].pixels[8*40+20],0xff000000);
    e.command(&json!({"op":"floodCommit"})).unwrap();assert_eq!(e.undo.len(),1);
}
