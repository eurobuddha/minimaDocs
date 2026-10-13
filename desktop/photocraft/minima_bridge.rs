//! In-memory minimaDocs boundary. No document storage or filesystem authority in the editor.
use photocraft_ui_egui::PhotocraftApp;
use serde_json::{Value, json};
use std::{
    cell::{Cell, RefCell},
    collections::{HashMap, VecDeque},
    rc::Rc,
};
use wasm_bindgen::{JsCast, JsValue, closure::Closure};
const MAX: usize = 16 * 1024 * 1024;
struct PendingExport {
    doc: u64,
    revision: u64,
    path: String,
    warnings: Vec<String>,
    copy: bool,
}
thread_local! {
    static PRIMARY: Cell<Option<u64>> = const { Cell::new(None) };
    static EXPORT_SEQUENCE: Cell<u64> = const { Cell::new(0) };
    static EXPORTS: RefCell<HashMap<u64, PendingExport>> = RefCell::new(HashMap::new());
}
pub fn write(doc: u64, revision: u64, path: &str, bytes: &[u8], warnings: &[String], copy: bool) -> Result<(), String> {
    if bytes.len() > MAX {
        return Err("Export exceeds the 16 MiB minimaDocs limit".into());
    }
    if EXPORTS.with(|p| p.borrow().len()) >= 8 {
        return Err("Finish the open export dialogs first".into());
    }
    let token = EXPORT_SEQUENCE.with(|n| {
        let next = n.get().saturating_add(1);
        n.set(next);
        next
    });
    EXPORTS.with(|p| p.borrow_mut().insert(token, PendingExport { doc, revision, path: path.into(), warnings: warnings.into(), copy }));
    if let Err(e) = emit("download", json!({"name":path,"token":token}), bytes) {
        EXPORTS.with(|p| p.borrow_mut().remove(&token));
        return Err(e);
    }
    Ok(())
}
type Queue = Rc<RefCell<VecDeque<(String, Vec<u8>)>>>;
pub fn emit(kind: &str, value: Value, bytes: &[u8]) -> Result<(), String> {
    let window = web_sys::window().ok_or("no window")?;
    let detail = js_sys::Object::new();
    let put = |key: &str, value: &JsValue| js_sys::Reflect::set(&detail, &key.into(), value).map_err(|e| format!("{e:?}"));
    put("kind", &kind.into())?;
    put("json", &value.to_string().into())?;
    put("bytes", &js_sys::Uint8Array::from(bytes))?;
    let init = web_sys::CustomEventInit::new();
    init.set_detail(&detail);
    let event = web_sys::CustomEvent::new_with_event_init_dict("minimadocs:response", &init).map_err(|e| format!("{e:?}"))?;
    window.dispatch_event(&event).map_err(|e| format!("{e:?}"))?;
    Ok(())
}
pub fn command(app: &mut PhotocraftApp, id: &str, params: &Value) -> Option<Result<Value, String>> {
    let child = app.session.active().is_some_and(|d| app.session.smart_links.iter().any(|l| l.child == d.doc.id));
    let target = params.get("document").and_then(Value::as_u64).and_then(|n| usize::try_from(n).ok()).or(app.session.active_index());
    let root = target.and_then(|i| app.session.documents().get(i)).is_some_and(|d| PRIMARY.with(|p| p.get()) == Some(d.doc.id.0));
    let action = match id {
        "file.new" => "new",
        "file.open" | "file.openAs" => "open",
        "file.save" if !child && root => "save",
        "file.saveAs" | "file.saveACopy" if !child && root => "export",
        "file.close" if root => "close",
        "file.closeAll" | "file.exit" => "close",
        "file.closeOthers" if !root => "close",
        "image.duplicate" => "copy",
        _ => return None,
    };
    Some(emit("action", json!({"action":action}), &[]).map(|()| Value::Null))
}
pub struct Bridge {
    queue: Queue,
    signature: Vec<(u64, u64)>,
    exported: Vec<(u64, u64)>,
    opened: bool,
    pending_edit: bool,
    ctx: egui::Context,
}
impl Bridge {
    pub fn new(ctx: egui::Context) -> Result<Self, String> {
        let queue: Queue = Rc::default();
        let input = queue.clone();
        let wake = ctx.clone();
        let cb = Closure::<dyn FnMut(web_sys::CustomEvent)>::new(move |event: web_sys::CustomEvent| {
            let detail = event.detail();
            let get = |key: &str| js_sys::Reflect::get(&detail, &key.into()).unwrap_or(JsValue::UNDEFINED);
            let Some(text) = get("json").as_string() else { return };
            let array = get("bytes").dyn_into::<js_sys::Uint8Array>().ok();
            if text.len() > 65536 || array.as_ref().is_some_and(|a| a.length() as usize > MAX) || input.borrow().len() >= 32 {
                let _ = emit("error", json!({"message":"PhotoCraft request is too large or the editor is busy."}), &[]);
                return;
            }
            input.borrow_mut().push_back((text, array.map_or_else(Vec::new, |a| a.to_vec())));
            wake.request_repaint();
        });
        web_sys::window()
            .ok_or("no window")?
            .add_event_listener_with_callback("minimadocs:request", cb.as_ref().unchecked_ref())
            .map_err(|e| format!("{e:?}"))?;
        cb.forget();
        emit("ready", Value::Null, &[])?;
        Ok(Self { queue, signature: Vec::new(), exported: Vec::new(), opened: false, pending_edit: false, ctx })
    }
    fn revisions(app: &PhotocraftApp) -> Vec<(u64, u64)> {
        app.session.documents().iter().map(|s| (s.doc.id.0, s.revision)).collect()
    }
    pub fn poll(&mut self, app: &mut PhotocraftApp) {
        let requests = std::mem::take(&mut *self.queue.borrow_mut());
        for (text, bytes) in requests {
            let request = serde_json::from_str::<Value>(&text);
            let id = request.as_ref().ok().and_then(|v| v.get("id")).cloned().unwrap_or(Value::Null);
            let result = request.map_err(|e| e.to_string()).and_then(|r| self.run(app, &r, bytes));
            match result {
                Ok((value, bytes)) => {
                    let _ = emit("result", json!({"id":id,"value":value}), &bytes);
                }
                Err(message) => {
                    let _ = emit("result", json!({"id":id,"error":message}), &[]);
                }
            }
        }
        let signature = Self::revisions(app);
        let pending_edit = app.host_pending_edits();
        if self.opened && (signature != self.signature || pending_edit != self.pending_edit) {
            self.pending_edit = pending_edit;
            self.signature = signature;
            let _ = emit("changed", Value::Null, &[]);
        }
    }
    fn run(&mut self, app: &mut PhotocraftApp, r: &Value, bytes: Vec<u8>) -> Result<(Value, Vec<u8>), String> {
        match r.get("method").and_then(Value::as_str).unwrap_or("") {
            "open" => {
                if self.opened {
                    return Err("This document is already open.".into());
                }
                if bytes.is_empty() {
                    app.session.execute("file.new", json!({"width":1280,"height":800,"name":"Untitled image"})).map_err(|e| e.to_string())?;
                    app.sync_views();
                } else {
                    app.open_file(r.get("name").and_then(Value::as_str).ok_or("Missing file name")?, &bytes)?;
                }
                if app.session.documents().len() != 1 {
                    return Err("This file did not open as an image document.".into());
                }
                PRIMARY.with(|p| p.set(app.session.active().map(|d| d.doc.id.0)));
                self.opened = true;
                self.signature = Self::revisions(app);
                Ok((json!({"warnings":app.ui.status}), Vec::new()))
            }
            "snapshot" => {
                app.prepare_host_snapshot()?;
                let format = r.get("format").and_then(Value::as_str).unwrap_or("pcraft");
                if !["pcraft", "png", "psd", "ora"].contains(&format) {
                    return Err("Unsupported export format".into());
                }
                let root = PRIMARY.with(|p| p.get()).ok_or("No image document is open")?;
                if app.session.documents().iter().any(|d| d.doc.id.0 != root && !app.session.smart_links.iter().any(|l| l.child == d.doc.id)) {
                    return Err("There are additional image tabs. Export and close those tabs before saving this minimaDocs document.".into());
                }
                let active = app.session.active_index();
                let links = app.session.smart_links.clone();
                for link in links.iter().rev() {
                    if let Some(index) = app.session.documents().iter().position(|d| d.doc.id == link.child) {
                        app.session.set_active(index);
                        app.run("layer.smartObjects.saveContents", json!({}))?;
                    }
                }
                if let Some(index) = active {
                    app.session.set_active(index);
                }
                let doc = &app.session.documents().iter().find(|d| d.doc.id.0 == root).ok_or("The original image tab is no longer open")?.doc;
                let out = photocraft_io::export(doc, &format!("image.{format}"), &Default::default()).map_err(|e| e.to_string())?;
                if out.bytes.len() > MAX {
                    return Err("The edited file exceeds the 16 MiB minimaDocs sharing limit. Reduce the image dimensions or export a smaller copy.".into());
                }
                self.signature = Self::revisions(app);
                self.pending_edit = app.host_pending_edits();
                if format == "pcraft" {
                    self.exported = self.signature.clone();
                }
                Ok((json!({"warnings":out.warnings}), out.bytes))
            }
            "ack" => {
                for (id, revision) in &self.exported {
                    if let Some(index) = app.session.documents().iter().position(|d| d.doc.id.0 == *id) {
                        let active = app.session.active_index();
                        app.session.set_active(index);
                        if let Some(d) = app.session.active_mut() {
                            d.saved_revision = *revision;
                        }
                        if let Some(i) = active {
                            app.session.set_active(i);
                        }
                    }
                }
                Ok((Value::Null, Vec::new()))
            }
            "exportAck" => {
                let token = r.get("token").and_then(Value::as_u64).ok_or("Missing export token")?;
                let p = EXPORTS.with(|pending| pending.borrow_mut().remove(&token)).ok_or("No matching export")?;
                if r.get("saved").and_then(Value::as_bool) == Some(true) {
                    app.complete_host_save(p.doc, p.revision, &p.path, &p.warnings, p.copy, &self.ctx);
                } else {
                    app.ui.status = r.get("error").and_then(Value::as_str).unwrap_or("Export cancelled; changes are still unsaved.").into();
                    app.ui.status_error = r.get("error").is_some();
                }
                Ok((Value::Null, Vec::new()))
            }
            "command" => {
                let id = r.get("command").and_then(Value::as_str).ok_or("Missing command")?;
                let value = photocraft_ui_egui::menus::invoke(app, &self.ctx, id, r.get("params").cloned().unwrap_or(json!({})))?;
                Ok((value, Vec::new()))
            }
            "inspect" => Ok((app.run("document.inspect", json!({}))?, Vec::new())),
            _ => Err("Unknown PhotoCraft host operation".into()),
        }
    }
}
