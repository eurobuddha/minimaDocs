const test=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../android/app/src/main/assets/workbench/bridge.js'),'utf8');
function editorHarness(kind='docx',config={}) {
  const handlers={},frameHandlers={},timers=new Map(),sent=[],saved=[],errors=[],exported=[];
  let ready=0,next=0,changed=0;
  const doc={getElementById:()=>true};
  const api={isLoadFullApi:false,isDocumentLoadComplete:false};
  const child={frames:[{Asc:{editor:api},document:doc}],onCreateNew:async()=>{},postMessage:m=>sent.push(m)};
  const frame={contentWindow:child,addEventListener:(n,f)=>frameHandlers[n]=f};
  const origin='https://editors.minimadocs.local';
  const context={window:{addEventListener:(n,f)=>handlers[n]=f},document:{getElementById:()=>frame},
    location:{origin},MinimaDocs:{bootstrap:()=>JSON.stringify({kind,...config}),ready:()=>ready++,error:e=>errors.push(e),saved:s=>saved.push(s),exported:(s,type)=>exported.push({s,type}),used:()=>{},changed:()=>changed++},
    setInterval:(f,ms)=>{timers.set(++next,{f,ms});return next;},clearInterval:id=>timers.delete(id),
    setTimeout:(f,ms)=>{timers.set(++next,{f,ms});return next;},clearTimeout:id=>timers.delete(id),
    Blob,Uint8Array,TextDecoder,URL,atob,
    FileReader:class {readAsDataURL(blob){blob.arrayBuffer().then(b=>{this.result='data:;base64,'+Buffer.from(b).toString('base64');this.onload();});}}
  };
  vm.runInNewContext(source,context);
  return {context,api,sent,saved,errors,exported,child,frameHandlers,get ready(){return ready;},get changed(){return changed;},
    timeout:()=>{for(const t of [...timers.values()])if(t.ms===180000)t.f();},
    tick:()=>{for(const t of [...timers.values()])if(t.ms===250)t.f();},
    emit:(data,extra={})=>handlers.message({data,origin,source:child,...extra})};
}
test('office stays unready until both document and full API are loaded',async()=>{
  const h=editorHarness();await h.emit({type:'document:ready'});h.tick();assert.equal(h.ready,0);
  h.api.isLoadFullApi=true;h.tick();assert.equal(h.ready,0);
  h.api.isDocumentLoadComplete=true;h.tick();h.tick();assert.equal(h.ready,1);
});
test('messages from other origins or frames cannot open or save a document',async()=>{
  const h=editorHarness();await h.emit({type:'document:ready'},{origin:'https://example.com'});
  await h.emit({type:'document:ready'},{source:{}});h.api.isLoadFullApi=h.api.isDocumentLoadComplete=true;h.tick();
  assert.equal(h.ready,0);assert.equal(h.sent.length,0);
});
test('save accepts only its matching reply, once, and retains exact bytes',async()=>{
  const h=editorHarness();await h.context.window.minimaDocsSave();await h.context.window.minimaDocsSave();
  assert.equal(h.sent.length,1);const id=h.sent[0].id;
  await h.emit({type:'document:saved',id:'old-save',payload:{file:new Blob(['wrong'])}});assert.deepEqual(h.saved,[]);
  const reply={type:'document:saved',id,payload:{file:new Blob(['edited bytes'])}};
  await h.emit(reply);await h.emit(reply);assert.deepEqual(h.saved,[Buffer.from('edited bytes').toString('base64')]);
});
test('oversized edited files are rejected without crossing the native bridge',async()=>{
  const h=editorHarness();await h.context.window.minimaDocsSave();
  await h.emit({type:'document:saved',id:h.sent[0].id,payload:{file:new Blob([new Uint8Array(16*1024*1024+1)])}});
  assert.equal(h.saved.length,0);assert.match(h.errors[0],/16 MiB/);
});
test('browser storage keeps preferences in memory and disables IndexedDB',()=>{
  const context={window:{},navigator:Object.create({serviceWorker:{}})};
  vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../android/app/src/main/assets/workbench/memory-storage.js'),'utf8'),context);
  const storage=context.window.localStorage;storage.setItem('draft','sample');assert.equal(storage.getItem('draft'),'sample');
  assert.equal(context.window.sessionStorage.getItem('draft'),null);assert.equal(context.window.indexedDB,undefined);
  assert.equal('serviceWorker' in context.navigator,false);
  storage.clear();assert.equal(storage.length,0);
});
test('a rejected image project cannot be presented as successfully opened',async()=>{
  const h=editorHarness('image',{name:'broken.minimadocs-image.json',base64:Buffer.from('{}').toString('base64')});
  h.child.document={createElement:()=>({}),head:{appendChild:()=>{}}};
  h.child.FileSave={};h.child.State={do_action:async()=>({status:'aborted'})};
  h.child.FileOpen={load_json:()=>h.child.State.do_action({action_id:'open_json_file'})};
  await h.frameHandlers.load();assert.equal(h.ready,0);assert.match(h.errors[0],/could not be opened/);
});

test('dirty changes and readonly import reach the native contract',async()=>{
  const h=editorHarness('docx',{readonly:true,name:'report.docx',base64:Buffer.from('docx').toString('base64')});
  await h.emit({type:'document:ready'});assert.equal(h.sent[0].payload.readonly,true);
  await h.emit({type:'document:dirty-changed',payload:{dirty:true}});assert.equal(h.changed,1);
});
test('timeout permits retry and stale replies cannot complete the new save',async()=>{
  const h=editorHarness();await h.context.window.minimaDocsSave();const old=h.sent[0].id;h.timeout();
  await h.context.window.minimaDocsSave();const now=h.sent[1].id;assert.notEqual(old,now);
  await h.emit({type:'document:error',id:old,payload:{message:'old failure'}});
  await h.emit({type:'document:saved',id:old,payload:{file:new Blob(['old'])}});
  await h.emit({type:'document:saved',id:now,payload:{file:new Blob(['new'])}});
  assert.deepEqual(h.saved,[Buffer.from('new').toString('base64')]);assert.equal(h.errors.length,1);
});
test('simultaneous duplicate replies cross the native save bridge only once',async()=>{
  const h=editorHarness();await h.context.window.minimaDocsSave();
  const reply={type:'document:saved',id:h.sent[0].id,payload:{file:new Blob(['once'])}};
  await Promise.all([h.emit(reply),h.emit(reply)]);assert.equal(h.saved.length,1);
});
test('PDF conversion exports bytes without replacing the editable document',async()=>{
  const h=editorHarness('xlsx');await h.context.window.minimaDocsExport('PDF');
  assert.equal(h.sent[0].payload.targetExt,'PDF');
  await h.emit({type:'document:saved',id:h.sent[0].id,payload:{file:new Blob(['%PDF-1.7'])}});
  assert.equal(h.saved.length,0);assert.equal(h.exported[0].type,'PDF');
});
