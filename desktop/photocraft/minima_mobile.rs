//! Touch host adapter. Document operations remain engine commands; previews use its COW history.
use photocraft_doc::{Document, Layer, LayerContent, LayerId};
use photocraft_engine::{DocState, Session};
use photocraft_ui_egui::{
    PhotocraftApp,
    control::{self, ControlRequest, Outcome},
};
use serde_json::{Value, json};
use std::sync::Arc;

#[derive(Default)]
pub struct Mobile {
    original: Option<Arc<Document>>,
    preview: Option<DocState>,
    sequence: u64,
}
impl Mobile {
    pub fn pending(&self) -> bool {
        self.preview.is_some()
    }
    fn begin(&mut self, session: &Session) -> Result<(), String> {
        if self.pending() {
            return Err("Apply or cancel the current tool first".into());
        }
        self.preview = Some(session.active().ok_or("No image is open")?.clone());
        self.sequence = self.sequence.saturating_add(1);
        Ok(())
    }
    fn finish(&mut self, session: &mut Session, apply: bool) -> Result<(), String> {
        let Some(mut before) = self.preview.take() else { return Ok(()) };
        let current = session.active_mut().ok_or("No image is open")?;
        if before.doc.id != current.doc.id {
            self.preview = Some(before);
            return Err("The active image changed".into());
        }
        if !apply {
            let clean = !before.is_dirty();
            before.revision = current.revision.saturating_add(1);
            if clean {
                before.saved_revision = before.revision;
            }
            before.last_damage = None;
            *current = before;
        }
        current.coalesce = None;
        Ok(())
    }
    pub fn run(&mut self, app: &mut PhotocraftApp, ctx: &egui::Context, method: &str, r: &Value) -> Result<(Value, Vec<u8>), String> {
        let value = match method {
            "mobile.configure" => {
                self.original = Some(app.session.active().ok_or("No image is open")?.doc.clone());
                app.ui.view.screen_mode = "fullScreen".into();
                ui(app, ctx, json!({"tool":"hand", "fit":true}))?
            }
            "mobile.begin" => {
                app.prepare_host_snapshot()?;
                self.begin(&app.session)?;
                Value::Null
            }
            "mobile.finish" => {
                self.finish(&mut app.session, r.get("apply").and_then(Value::as_bool).unwrap_or(false))?;
                app.sync_views();
                Value::Null
            }
            "mobile.ui" => {
                let params = r.get("params").cloned().unwrap_or(json!({}));
                if !params.as_object().is_some_and(|p| p.keys().all(|k| ["zoom", "center", "fit"].contains(&k.as_str()))) {
                    return Err("Unsupported mobile view setting".into());
                }
                ui(app, ctx, params)?
            }
            "mobile.command" => {
                let id = r.get("command").and_then(Value::as_str).ok_or("Missing command")?;
                if !allowed(id) {
                    return Err("This command is not available in the touch editor".into());
                }
                if self.pending() && ["edit.undo", "edit.redo"].contains(&id) {
                    return Err("Apply or cancel before undo".into());
                }
                let mut params = r.get("params").cloned().unwrap_or(json!({}));
                let object = params.as_object_mut().ok_or("Command parameters must be an object")?;
                if self.pending() {
                    object.insert("coalesce".into(), json!(format!("mobile-tool-{}", self.sequence)));
                }
                app.run(id, params)?
            }
            "mobile.state" => {
                let state = app.session.active().ok_or("No image is open")?;
                let mut value = photocraft_engine::inspect::document(state);
                enrich(&mut value["layers"], &state.doc);
                let rect = app.last_canvas_rect;
                value["canvas"] = json!([rect.min.x, rect.min.y, rect.width(), rect.height()]);
                value["view"] = app.session.active_index().and_then(|i| app.ui.views.get(i)).map_or(Value::Null, |v| json!({"zoom":v.zoom,"center":v.center}));
                value["ppp"] = json!(ctx.pixels_per_point());
                value["preview"] = json!(self.pending());
                value
            }
            "mobile.thumbnail" => {
                let original = r.get("original").and_then(Value::as_bool).unwrap_or(false);
                let doc = if original { self.original.as_ref().ok_or("No original image")? } else { &app.session.active().ok_or("No image")?.doc };
                let mut copy = doc.as_ref().clone();
                if let Some(lut) = r.get("look").and_then(Value::as_str) {
                    if !["warm", "cool", "tealOrange", "bleachBypass", "fadedFilm", "dayForNight", "monoContrast", "crossProcess"].contains(&lut) {
                        return Err("Unknown Look".into());
                    }
                    let adjustment = photocraft_engine::commands::adjustment_from_params("colorLookup", &json!({"lut":lut}));
                    copy.insert_above(copy.top_layer(), Layer::new(lut, LayerContent::Adjustment(adjustment)));
                }
                let thumb = photocraft_compose::thumbnail(&copy, if original { 1024 } else { 160 });
                let image = photocraft_codecs::Image::from_u8(thumb.width, thumb.height, photocraft_codecs::ChannelLayout::Rgba, thumb.pixels)
                    .map_err(|e| e.to_string())?;
                let bytes = photocraft_codecs::encode(&image, photocraft_codecs::Format::Png, &Default::default()).map_err(|e| e.to_string())?;
                return Ok((Value::Null, bytes));
            }
            _ => return Err("Unknown mobile operation".into()),
        };
        ctx.request_repaint();
        Ok((value, Vec::new()))
    }
}
fn ui(app: &mut PhotocraftApp, ctx: &egui::Context, params: Value) -> Result<Value, String> {
    let (request, _) = ControlRequest::new("ui.set", params);
    match control::handle(app, ctx, &request) {
        Outcome::Done(value) if value["ok"] == true => Ok(value["result"].clone()),
        Outcome::Done(value) => Err(value["error"].to_string()),
        _ => Err("View update did not complete".into()),
    }
}
fn enrich(layers: &mut Value, doc: &Document) {
    if let Some(layers) = layers.as_array_mut() {
        for layer in layers {
            if let Some(Layer { content: LayerContent::Adjustment(a), .. }) = layer["id"].as_u64().and_then(|id| doc.layer(LayerId(id))) {
                layer["adjustmentKind"] = json!(photocraft_engine::commands::adjustment_kind(a));
                layer["params"] = photocraft_engine::adjust_params::to_params(a);
            }
            enrich(&mut layer["children"], doc);
        }
    }
}
fn allowed(id: &str) -> bool {
    ["brightnessContrast", "exposure", "vibrance", "colorBalance", "blackWhite", "colorLookup", "curves"]
        .iter()
        .any(|kind| id == format!("layer.newAdjustmentLayer.{kind}"))
        || [
            "layer.setAdjustment",
            "layer.setProps",
            "layer.select",
            "layer.layerMask.revealAll",
            "layer.layerMask.hideAll",
            "paint.stroke",
            "image.crop",
            "image.imageRotation.90cw",
            "image.imageRotation.90ccw",
            "image.imageRotation.flipCanvasHorizontal",
            "image.imageRotation.flipCanvasVertical",
            "edit.undo",
            "edit.redo",
        ]
        .contains(&id)
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn touch_host_hides_chrome_and_uses_validated_commands() {
        let mut app = PhotocraftApp::new(Session::default(), photocraft_ui_egui::Services::default());
        app.run("file.new", json!({"width":32,"height":24})).unwrap();
        let ctx = egui::Context::default();
        let mut mobile = Mobile::default();
        mobile.run(&mut app, &ctx, "mobile.configure", &json!({})).unwrap();
        assert_eq!(app.ui.view.screen_mode, "fullScreen");
        mobile.run(&mut app, &ctx, "mobile.begin", &json!({})).unwrap();
        mobile.run(&mut app, &ctx, "mobile.command", &json!({"command":"layer.newAdjustmentLayer.exposure","params":{"exposure":1}})).unwrap();
        assert!(mobile.run(&mut app, &ctx, "mobile.command", &json!({"command":"file.new"})).is_err());
        mobile.run(&mut app, &ctx, "mobile.finish", &json!({"apply":true})).unwrap();
        let (_, png) = mobile.run(&mut app, &ctx, "mobile.thumbnail", &json!({"look":"warm"})).unwrap();
        assert!(png.starts_with(&[137, 80, 78, 71]));
    }
    #[test]
    fn cancel_restores_pixels_layers_and_history_without_stale_revision() {
        let mut s = Session::default();
        s.execute("file.new", json!({"width":32,"height":24})).unwrap();
        let before = s.active().unwrap().clone();
        let mut mobile = Mobile::default();
        mobile.begin(&s).unwrap();
        assert!(mobile.begin(&s).is_err());
        s.execute("layer.newAdjustmentLayer.exposure", json!({"exposure":2})).unwrap();
        let revision = s.active().unwrap().revision;
        mobile.finish(&mut s, false).unwrap();
        let after = s.active().unwrap();
        assert_eq!(before.doc.layers.len(), after.doc.layers.len());
        assert_eq!(before.history.entries(), after.history.entries());
        assert!(after.revision > revision);
        assert_eq!(before.is_dirty(), after.is_dirty());
    }
    #[test]
    fn applied_adjustment_roundtrips_as_editable_layer() {
        let mut s = Session::default();
        s.execute("file.new", json!({"width":32,"height":24})).unwrap();
        let mut mobile = Mobile::default();
        mobile.begin(&s).unwrap();
        s.execute("layer.newAdjustmentLayer.vibrance", json!({"vibrance":42})).unwrap();
        mobile.finish(&mut s, true).unwrap();
        let out = photocraft_io::export(&s.active().unwrap().doc, "mobile.pcraft", &Default::default()).unwrap();
        let imported = photocraft_io::import("mobile.pcraft", &out.bytes).unwrap();
        assert_eq!(imported.document.layers.len(), s.active().unwrap().doc.layers.len());
        let mut value = photocraft_engine::inspect::document(s.active().unwrap());
        enrich(&mut value["layers"], &s.active().unwrap().doc);
        assert_eq!(value["layers"][0]["params"]["vibrance"].as_f64(), Some(42.0));
        assert!(!mobile.pending());
    }
}
