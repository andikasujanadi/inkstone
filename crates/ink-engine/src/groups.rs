use super::*;

#[derive(Clone,Serialize,Deserialize)]
pub(super) struct LayerGroup {pub(super) id:u32,pub(super) name:String,pub(super) visible:bool,pub(super) locked:bool,collapsed:bool,#[serde(default="group_opacity")] pub(super) opacity:f32,#[serde(default="group_blend")] pub(super) blend:String}
fn group_opacity()->f32{1.0}
fn group_blend()->String{"Pass Through".into()}
impl Layer {
    pub fn is_visible(&self)->bool{self.visible && self.group_visible}
    pub fn is_locked(&self)->bool{self.locked || self.group_locked}
}
impl Engine {
    pub(super) fn refresh_groups(&mut self){
        for l in &mut self.state.layers{
            let group=self.state.groups.iter().find(|g|Some(g.id)==l.group_id);
            l.group_visible=group.map_or(true,|g|g.visible);l.group_locked=group.is_some_and(|g|g.locked);
            if group.is_none(){l.group_id=None;}
        }
    }
    pub(super) fn move_layer(&mut self,v:&Value)->Result<(),String>{
        let id=v["id"].as_u64().ok_or("Missing layer")? as u32;
        let source=self.state.layers.iter().position(|l|l.id==id).ok_or("Layer not found")?;
        let group=v["groupId"].as_u64().map(|n|n as u32);
        if group.is_some() && !self.state.groups.iter().any(|g|Some(g.id)==group){return Err("Group not found".into());}
        let target=v["targetId"].as_u64().map(|n|n as u32);if target==Some(id) && self.state.layers[source].group_id==group{return Ok(());}
        let above=v["above"].as_bool().unwrap_or(true);
        self.structure(|e|{
            let active=e.state.layers[e.state.active].id;
            if let Some(group)=e.state.groups.iter_mut().find(|g|Some(g.id)==group){group.collapsed=false;}
            let mut layer=e.state.layers.remove(source);layer.group_id=group;
            let position=if target==Some(id){source.min(e.state.layers.len())}else if let Some(index)=target.and_then(|id|e.state.layers.iter().position(|l|l.id==id)){
                let other=e.state.layers[index].group_id;
                if other!=group && other.is_some(){if above{e.state.layers.iter().rposition(|l|l.group_id==other).unwrap()+1}else{e.state.layers.iter().position(|l|l.group_id==other).unwrap()}}
                else{index+usize::from(above)}
            }else if group.is_some(){e.state.layers.iter().rposition(|l|l.group_id==group).map_or(e.state.layers.len(),|n|n+1)}else{0};
            e.state.layers.insert(position,layer);e.state.active=e.state.layers.iter().position(|l|l.id==active).unwrap();e.state.layers[0].clipping=false;e.refresh_groups();
        });Ok(())
    }
    pub(super) fn group_command(&mut self,v:&Value)->Result<Value,String>{
        let action=v["action"].as_str().unwrap_or("create");
        if !matches!(action,"create"|"update"|"member"|"removeMember"|"ungroup"|"translate"){return Err("Unknown group action".into());}
        if action=="create" && self.state.groups.len()>=512{return Err("Group limit reached".into());}
        let id=v["id"].as_u64().map(|n|n as u32);
        let group=self.state.groups.iter().position(|g|Some(g.id)==id);
        if action!="create" && group.is_none(){return Err("Group not found".into());}
        if action=="translate" && self.state.groups[group.unwrap()].locked{return Err("This group is locked".into());}
        if let Some(mode)=v["blend"].as_str(){if !matches!(mode,"Pass Through"|"Normal"|"Multiply"|"Screen"|"Overlay"|"Darken"|"Lighten"|"Color Burn"|"Linear Burn"|"Color Dodge"|"Add"|"Soft Light"|"Hard Light"|"Difference"|"Exclusion"|"Subtract"){return Err("Unknown group blend mode".into());}}
        let index=v["index"].as_u64().unwrap_or(self.state.active as u64) as usize;
        if index>=self.state.layers.len(){return Err("Layer not found".into());}
        if action=="translate"{
            let moving=self.state.layers.iter().filter(|l|l.group_id==id).count();
            if Self::layer_bytes(&self.state.layers)+moving*self.width*self.height*4>PIXEL_BUDGET*4{return Err("Not enough canvas memory to move this group".into());}
        }
        self.structure(|e|{
            match action{
                "create"=>{let next=e.state.groups.iter().map(|g|g.id).max().unwrap_or(0).saturating_add(1);e.state.groups.push(LayerGroup{id:next,name:v["name"].as_str().unwrap_or("New group").chars().take(100).collect(),visible:true,locked:false,collapsed:false,opacity:1.0,blend:group_blend()});e.state.layers[index].group_id=Some(next);},
                "member"=>{
                    let active_id=e.state.layers[e.state.active].id;
                    let mut layer=e.state.layers.remove(index);layer.group_id=id;
                    let at=e.state.layers.iter().rposition(|l|l.group_id==id).map_or(e.state.layers.len(),|n|n+1);e.state.layers.insert(at,layer);
                    e.state.active=e.state.layers.iter().position(|l|l.id==active_id).unwrap();
                },
                "removeMember"=>{e.state.layers[index].group_id=None;},
                "ungroup"=>{for l in &mut e.state.layers{if l.group_id==id{l.group_id=None;}}e.state.groups.remove(group.unwrap());},
                "translate"=>{
                    let dx=v["x"].as_i64().unwrap_or(0).clamp(-(e.width as i64),e.width as i64) as isize;
                    let dy=v["y"].as_i64().unwrap_or(0).clamp(-(e.height as i64),e.height as i64) as isize;
                    for l in &mut e.state.layers{if l.group_id!=id || l.locked{continue;}
                        let mut out=vec![0;e.width*e.height];let mut mask=l.mask.as_ref().map(|_|vec![255;e.width*e.height]);
                        for y in 0..e.height{for x in 0..e.width{let xx=x as isize+dx;let yy=y as isize+dy;if xx>=0 && yy>=0 && xx<e.width as isize && yy<e.height as isize{let from=y*e.width+x;let to=yy as usize*e.width+xx as usize;out[to]=l.pixels[from];if let (Some(source),Some(target))=(&l.mask,mask.as_mut()){target[to]=source[from];}}}}
                        l.pixels=Arc::new(out);l.mask=mask.map(Arc::new);
                    }
                },
                _=>{let g=&mut e.state.groups[group.unwrap()];if let Some(name)=v["name"].as_str(){g.name=name.chars().take(100).collect();}if let Some(b)=v["visible"].as_bool(){g.visible=b;}if let Some(b)=v["locked"].as_bool(){g.locked=b;}if let Some(b)=v["collapsed"].as_bool(){g.collapsed=b;}if let Some(n)=v["opacity"].as_f64(){g.opacity=(n as f32).clamp(0.0,1.0);}if let Some(mode)=v["blend"].as_str(){g.blend=mode.into();}}
            }
            if matches!(action,"create"|"member"|"removeMember"){
                let active=e.state.layers[e.state.active].id;
                let mut pending=std::mem::take(&mut e.state.layers);
                while !pending.is_empty(){let layer=pending.remove(0);let group=layer.group_id;e.state.layers.push(layer);if group.is_some(){let mut i=0;while i<pending.len(){if pending[i].group_id==group{e.state.layers.push(pending.remove(i));}else{i+=1;}}}}
                e.state.active=e.state.layers.iter().position(|l|l.id==active).unwrap();
            }
            e.refresh_groups();
        });Ok(self.info())
    }
}
