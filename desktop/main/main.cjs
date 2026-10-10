'use strict';
// Window and packaging conventions adapted from PandaGet Desktop. All document operations
// stay in the shared Java backend; each editor window owns one backend view.
const {app,BrowserWindow,ipcMain,dialog,Menu,session,safeStorage,clipboard}=require('electron');
const fs=require('node:fs/promises'),path=require('node:path'),crypto=require('node:crypto');
const {Backend}=require('./backend.cjs'),{handler,WORKSPACE,EDITOR}=require('./assets.cjs');
const {ParlonsClient,parseLink,PROVIDERS}=require('./parlons.cjs');
const ROOT=path.resolve(__dirname,'..'),MAX=16*1024*1024;
const test=!app.isPackaged&&process.env.MINIMADOCS_TEST_DIR;
if(test)app.setPath('userData',path.resolve(test));
app.setName('minimaDocs');
const editors=new Map(),opening=new Map();let workspace,backend,quitting=false,quitRequested=false,ready=false,pendingInvitation='';const pendingFiles=[];
let parlons,parlonsError='';
const notify=()=>{if(workspace&&!workspace.isDestroyed())workspace.webContents.send('workspace:refresh');};
const fail=(win,e)=>dialog.showMessageBox(win,{type:'error',title:'minimaDocs',message:e.message||'The operation could not finish.'});
const validFrame=(event,url)=>event.senderFrame===event.sender.mainFrame&&event.senderFrame.url===url;
function workspaceOnly(e){if(!workspace||e.sender!==workspace.webContents||!validFrame(e,WORKSPACE+'/'))throw new Error('Untrusted workspace request');}
function editorOnly(e){const state=editors.get(e.sender.id);if(!state||!validFrame(e,EDITOR+'/workbench/index.html'))throw new Error('Untrusted editor request');return state;}
function bytes(encoded){
  // A repeated-group regexp over a multi-MiB string can overflow V8's stack.
  // Buffer handles the decoding; a roundtrip rejects its permissive input forms.
  if(typeof encoded!=='string'||encoded.length>Math.ceil(MAX/3)*4||encoded.length%4||/[^A-Za-z0-9+/=]/.test(encoded))throw new Error('Invalid file data');
  const b=Buffer.from(encoded,'base64');
  if(!b.length||b.length>MAX)throw new Error('Files must be between 1 byte and 16 MiB.');
  if(b.toString('base64')!==encoded)throw new Error('Invalid file data');
  return b;
}
async function atomicWrite(file,data){const temp=file+'.'+crypto.randomUUID()+'.tmp';let handle;try{handle=await fs.open(temp,'wx',0o600);await handle.writeFile(data);await handle.sync();await handle.close();handle=null;await fs.rename(temp,file);}finally{await handle?.close();await fs.rm(temp,{force:true});}}
function secureWindow(win){
  win.webContents.setWindowOpenHandler(()=>({action:'deny'}));
  win.webContents.on('will-navigate',(event,url)=>{if(![WORKSPACE+'/',EDITOR+'/workbench/index.html'].includes(url))event.preventDefault();});
  win.webContents.on('will-attach-webview',event=>event.preventDefault());
}
function makeWorkspace(){
  if(workspace&&!workspace.isDestroyed()){workspace.show();workspace.focus();return workspace;}
  workspace=new BrowserWindow({width:1240,height:850,minWidth:850,minHeight:620,title:'minimaDocs',backgroundColor:'#F2F1EC',titleBarStyle:'hiddenInset',trafficLightPosition:{x:20,y:24},webPreferences:{preload:path.join(__dirname,'preload.cjs'),contextIsolation:true,nodeIntegration:false,sandbox:true,spellcheck:false}});
  secureWindow(workspace);workspace.loadURL(WORKSPACE+'/');
  workspace.webContents.once('did-finish-load',()=>{if(pendingInvitation){workspace.webContents.send('workspace:invitation',pendingInvitation);pendingInvitation='';}});
  return workspace;
}
function update(state,message){if(!state.win.isDestroyed())state.win.webContents.send('editor:status',{message,ready:state.ready,dirty:state.dirty,saving:state.saving,readonly:state.config.readonly,title:state.title,conflict:state.config.conflict});}
function saveFailed(state,error){
  state.saving=false;state.exporting=false;state.closeAfterSave=false;state.shareAfterSave=false;state.exportAfterSave=false;
  state.saveError=String(error?.message||error||'The operation could not finish.');quitRequested=false;
  update(state,'Could not save: '+state.saveError+' Use Save to retry, or close and choose Discard edits.');
}
async function openEditor(params){
  if(params.id&&opening.has(params.id))return opening.get(params.id);
  const pending=createEditor(params);if(params.id)opening.set(params.id,pending);
  try{return await pending;}finally{if(params.id)opening.delete(params.id);}
}
async function createEditor(params){
  if(params.id){const already=[...editors.values()].find(s=>s.config.id===params.id);if(already){already.win.show();already.win.focus();return {id:params.id};}}
  const config=await backend.call('open',params);
  const win=new BrowserWindow({width:1320,height:920,minWidth:820,minHeight:600,title:config.title+' — minimaDocs',backgroundColor:'#F2F1EC',titleBarStyle:'hiddenInset',trafficLightPosition:{x:18,y:24},webPreferences:{preload:path.join(__dirname,'editor-preload.cjs'),contextIsolation:true,nodeIntegration:false,sandbox:true,spellcheck:false}});
  const state={win,config,title:config.title,dirty:!params.id,ready:false,saving:false,saveError:'',edits:0,savedEdits:0,lastBytes:config.base64||null,lastEdit:Date.now()};editors.set(win.webContents.id,state);secureWindow(win);
  // Embedded browser editors have their own exit guards. The native save/discard
  // decision must be authoritative, but only after a close has been approved.
  win.webContents.on('will-prevent-unload',event=>{if(state.closeAllowed)event.preventDefault();});
  win.on('close',event=>{
    if(state.forceClose||!state.dirty&&!state.saving){state.closeAllowed=true;return;}
    event.preventDefault();if(state.prompting)return;
    if(state.saving){state.closeAfterSave=true;return;}
    state.prompting=true;
    dialog.showMessageBox(win,{type:'question',message:'Save changes to “'+state.title+'”?',detail:(state.saveError?'The last save failed: '+state.saveError+'\n\n':'')+'Your last saved version is kept if you discard these edits.',buttons:['Save','Keep editing','Discard edits'],defaultId:state.saveError?1:0,cancelId:1}).then(({response})=>{
      state.prompting=false;if(response===0){state.closeAfterSave=true;requestSave(state);}else if(response===2){state.forceClose=true;win.close();}else quitRequested=false;
    }).catch(()=>{state.prompting=false;quitRequested=false;});
  });
  const contentsId=win.webContents.id;
  win.on('closed',()=>{editors.delete(contentsId);backend.call('release',{session:config.session}).catch(()=>{});notify();finishQuit();});
  await win.loadURL(EDITOR+'/workbench/index.html');return {id:config.id};
}
function requestSave(state){
  if(!state.ready||state.saving||state.config.readonly){update(state,state.config.readonly?'Read-only · use Save a copy to edit.':'Wait for the editor to finish.');state.closeAfterSave=false;quitRequested=false;return;}
  state.saveError='';state.saving=true;state.savedEdits=state.edits;update(state,'Saving…');
  state.win.webContents.executeJavaScript('window.minimaDocsSave()').catch(e=>saveFailed(state,e));
}
function kindOf(name){const n=name.toLowerCase();return /\.(docx|odt|rtf|doc)$/.test(n)?'docx':/\.(xlsx|ods|csv|xls)$/.test(n)?'xlsx':/\.(minimadocs-image\.json|png|jpg|jpeg|webp)$/.test(n)?'image':'';}
async function importFile(file){
  const kind=kindOf(file);if(!kind)throw new Error('Choose a Word document, spreadsheet, image or minimaDocs image project.');
  const stat=await fs.stat(file);if(!stat.isFile()||stat.size>MAX||!stat.size)throw new Error('Choose a file between 1 byte and 16 MiB.');
  const name=path.basename(file),raw=await fs.readFile(file);if(raw.length>MAX)throw new Error('The file exceeds 16 MiB.');
  return openEditor({kind,title:name.replace(/(\.minimadocs-image\.json|\.[^.]+)$/i,''),name,base64:raw.toString('base64')});
}
async function exportFile(state,data,type){
  const ext=type|| (state.config.kind==='image'?'minimadocs-image.json':state.config.kind);
  const name=(state.title||'Untitled').replace(/[\x00-\x1f/\\:]/g,'_')+'.'+ext.toLowerCase();
  const picked=await dialog.showSaveDialog(state.win,{title:'Export a copy',defaultPath:name});if(picked.canceled)return;
  await atomicWrite(picked.filePath,data);update(state,'Exported a copy · your document stays here.');
}
function shareEditor(state){const win=makeWorkspace();win.show();win.focus();const send=()=>win.webContents.send('workspace:share',state.config.id);if(win.webContents.isLoading())win.webContents.once('did-finish-load',send);else send();}
ipcMain.on('editor:bootstrap',e=>{const state=editors.get(e.sender.id);e.returnValue=state&&e.senderFrame===e.sender.mainFrame?state.config:{};});
ipcMain.on('editor:event',(e,event,message)=>{
  let s;try{s=editorOnly(e);}catch(_){return;}
  if(event==='ready'){s.ready=true;update(s,s.config.conflict?'Concurrent versions kept · save a separate copy after review.':s.config.readonly?'Can view':s.dirty?'New document · save to keep it':'Saved on this Mac');}
  if(event==='changed'&&!s.config.readonly){s.dirty=true;s.edits++;s.lastEdit=Date.now();update(s,'Unsaved changes');}
  if(event==='error')saveFailed(s,message);
});
ipcMain.handle('editor:saved',async(e,base64)=>{
  const s=editorOnly(e);if(!s.saving)throw new Error('No save is pending');
  try {
    bytes(base64);const saved=await backend.call('save',{session:s.config.session,base64,title:s.title});s.config.id=saved.id;s.config.conflict=saved.conflict;s.lastBytes=base64;s.dirty=s.edits!==s.savedEdits;s.saving=false;
    update(s,saved.conflict?'Concurrent versions kept for review':s.dirty?'Saved · newer edits waiting':'Saved on this Mac');notify();
    if(s.exportAfterSave){s.exportAfterSave=false;await exportFile(s,bytes(base64));}
    if(s.shareAfterSave){s.shareAfterSave=false;shareEditor(s);}
    if(s.closeAfterSave){if(!s.dirty){s.forceClose=true;s.win.close();}else quitRequested=false;}s.closeAfterSave=false;
    return saved;
  }catch(err){saveFailed(s,err);throw err;}
});
ipcMain.handle('editor:exported',async(e,base64,type)=>{const s=editorOnly(e);try{if(!s.exporting||!['PDF','PNG'].includes(type))throw new Error('No export is pending');s.exporting=false;await exportFile(s,bytes(base64),type);}catch(err){s.exporting=false;update(s,err.message);throw err;}});
ipcMain.handle('editor:action',async(e,action,value)=>{
  const s=editorOnly(e);
  if(action==='title'){if(s.config.readonly||typeof value!=='string'||value.length>100)return;s.title=value;s.dirty=true;s.edits++;s.lastEdit=Date.now();s.win.setTitle((value||'Untitled')+' — minimaDocs');update(s,'Unsaved changes');}
  else if(action==='save')requestSave(s);
  else if(action==='files')makeWorkspace();
  else if(action==='share'){if(s.dirty||!s.lastBytes){s.shareAfterSave=true;requestSave(s);}else shareEditor(s);}
  else if(action==='copy'){if(!s.ready||s.saving||s.exporting||s.copying)return;s.copying=true;try{const result=await backend.call('copy',{session:s.config.session});s.config.id=result.id;s.config.readonly=false;s.config.conflict=false;s.dirty=true;await s.win.webContents.executeJavaScript('window.minimaDocsWritable()');requestSave(s);}finally{s.copying=false;}}
  else if(action==='export'){
    if(!s.ready||s.saving||s.exporting)return;
    const converted=s.config.kind==='image'?'PNG':'PDF';
    const {response}=await dialog.showMessageBox(s.win,{message:'Export “'+s.title+'”',buttons:[s.config.kind==='image'?'Editable image project':s.config.kind.toUpperCase(),converted,'Cancel'],cancelId:2});
    if(response===0){if(s.dirty||!s.lastBytes){s.exportAfterSave=true;requestSave(s);}else await exportFile(s,bytes(s.lastBytes));}
    if(response===1){s.exporting=true;update(s,'Preparing '+converted+'…');try{await s.win.webContents.executeJavaScript('window.minimaDocsExport('+JSON.stringify(converted)+')');}catch(err){saveFailed(s,err);}}
  }
});
ipcMain.handle('workspace:call',async(e,method,params={})=>{
  workspaceOnly(e);
  if(!ready)throw new Error('Your workspace is opening…');
  if(method.startsWith('parlons')){
    if(parlonsError)throw new Error(parlonsError);
    if(method==='parlonsList')return {connections:parlons.list()};
    if(method==='parlonsLink'){const p=parseLink(params.link);return {host:PROVIDERS[p.provider],account:p.account};}
    if(method==='parlonsConnect')return parlons.connect(params.link);
    if(method==='parlonsSnapshot')return parlons.snapshot(params.connection);
    if(method==='parlonsAdd')return parlons.add(params.connection,params.address);
    if(method==='parlonsRemove')return parlons.remove(params.connection,params.key);
    if(method==='parlonsDismiss')return parlons.dismiss(params.connection,params.id);
    if(method==='parlonsDisconnect')return parlons.disconnect(params.connection);
    if(method==='parlonsForget')return parlons.disconnect(params.connection,true);
    if(method==='parlonsSend'){
      // Validate the selected host and contact before minting a document offer.
      const book=await parlons.snapshot(params.connection);
      if(!book.contacts.some(c=>c.key===params.to))throw new Error('This person is no longer a contact in that Parlons account.');
      if(!['READ','WRITE'].includes(params.level))throw new Error('Choose Can view or Can edit.');
      const offer=await backend.call('invitation',{id:params.id||'',level:params.level});
      return parlons.send(params.connection,params.to,offer.line);
    }
    throw new Error('Unknown Parlons action');
  }
  if(['list','versions','people','invitation','preview','accept','share','access','manage','sync','name'].includes(method))return backend.call(method,params);
  if(method==='new'){if(!['docx','xlsx','image'].includes(params.kind))throw new Error('Unknown document type');return openEditor({kind:params.kind,title:params.kind==='docx'?'Untitled document':params.kind==='xlsx'?'Untitled spreadsheet':'Untitled image'});}
  if(method==='open')return openEditor({id:params.id,...(params.file?{file:params.file}:{})});
  if(method==='import'){const p=await dialog.showOpenDialog(workspace,{properties:['openFile'],filters:[{name:'Documents, spreadsheets and images',extensions:['docx','doc','odt','rtf','xlsx','xls','ods','csv','png','jpg','jpeg','webp','json']}]});return p.canceled?{}:importFile(p.filePaths[0]);}
  if(method==='readQR'){const p=await dialog.showOpenDialog(workspace,{title:'Open a recipient QR image',properties:['openFile'],filters:[{name:'QR image',extensions:['png','jpg','jpeg']}]});if(p.canceled)return {};const stat=await fs.stat(p.filePaths[0]);if(stat.size>MAX)throw new Error('Choose an image up to 16 MiB.');return backend.call('qrRead',{base64:(await fs.readFile(p.filePaths[0])).toString('base64')});}
  if(method==='clipboard'){if(typeof params.text!=='string'||params.text.length>16384)throw new Error('Invalid invitation');clipboard.writeText(params.text);return {};}
  if(method==='version')return {version:app.getVersion()};
  throw new Error('Unknown workspace action');
});
function finishQuit(){if(!quitRequested||quitting||editors.size)return;quitting=true;Promise.allSettled([backend?.close(),parlons?.close()]).finally(()=>app.quit());}
function menu(){Menu.setApplicationMenu(Menu.buildFromTemplate([
  {label:'minimaDocs',submenu:[{role:'about'},{type:'separator'},{role:'services'},{type:'separator'},{role:'hide'},{role:'hideOthers'},{role:'unhide'},{type:'separator'},{role:'quit'}]},
  {label:'File',submenu:[{label:'New document',accelerator:'CmdOrCtrl+N',click:()=>openEditor({kind:'docx',title:'Untitled document'}).catch(e=>fail(workspace,e))},{label:'Open…',accelerator:'CmdOrCtrl+O',click:async()=>{const p=await dialog.showOpenDialog({properties:['openFile']});if(!p.canceled)importFile(p.filePaths[0]).catch(e=>fail(workspace,e));}},{label:'Save',accelerator:'CmdOrCtrl+S',click:()=>{const s=editors.get(BrowserWindow.getFocusedWindow()?.webContents.id);if(s)requestSave(s);}},{role:'close'}]},
  {role:'editMenu'},{label:'View',submenu:[{label:'Files',accelerator:'CmdOrCtrl+Shift+H',click:makeWorkspace},{role:'togglefullscreen'}]},{role:'windowMenu'}
]));}
if(!test&&!app.requestSingleInstanceLock())app.quit();
else {
 app.on('second-instance',()=>makeWorkspace());
 app.on('open-file',(event,file)=>{event.preventDefault();if(ready)importFile(file).catch(e=>fail(workspace,e));else pendingFiles.push(file);});
 app.on('open-url',(event,url)=>{event.preventDefault();if(!url.startsWith('minimadocs://pair/')||url.length>16384)return;pendingInvitation=url;if(ready){const w=makeWorkspace();if(!w.webContents.isLoading()){w.webContents.send('workspace:invitation',pendingInvitation);pendingInvitation='';}}});
 app.whenReady().then(async()=>{
  const root=app.isPackaged?process.resourcesPath:path.resolve(ROOT,'../android/app/src/main/assets');
  const roots={renderer:path.join(ROOT,'renderer'),editors:path.join(root,'editors'),workbench:path.join(root,'workbench')};
  session.defaultSession.protocol.handle('https',handler(roots));
  session.defaultSession.setPermissionRequestHandler((_wc,_permission,callback)=>callback(false));
  session.defaultSession.setPermissionCheckHandler(()=>false);
  session.defaultSession.webRequest.onBeforeRequest((details,callback)=>{const url=details.url;callback({cancel:!url.startsWith(WORKSPACE+'/')&&!url.startsWith(EDITOR+'/')&&!url.startsWith('blob:')&&!url.startsWith('data:')});});
  await session.defaultSession.clearCache();
  const directory=app.getPath('userData');await fs.mkdir(directory,{recursive:true,mode:0o700});
  const kept=path.join(directory,'storage-key.protected');let master;
  if(test){const testKey=path.join(directory,'synthetic-test-key');try{master=await fs.readFile(testKey);}catch(e){if(e.code!=='ENOENT')throw e;master=crypto.randomBytes(32);await atomicWrite(testKey,master);}}
  else {if(!safeStorage.isEncryptionAvailable())throw new Error('Unlock your macOS Keychain, then reopen minimaDocs.');try{master=Buffer.from(safeStorage.decryptString(await fs.readFile(kept)),'base64');}catch(e){if(e.code!=='ENOENT')throw new Error('The macOS Keychain could not open this workspace. Your files have not been changed.');master=crypto.randomBytes(32);await atomicWrite(kept,safeStorage.encryptString(master.toString('base64')));}}
  const java=app.isPackaged?path.join(process.resourcesPath,'runtime/bin/java'):path.join(ROOT,'build/runtime/bin/java');
  backend=new Backend(java,app.isPackaged?path.join(process.resourcesPath,'backend'):path.join(ROOT,'backend/build/package-input'),directory);
  await backend.call('init',{key:master.toString('base64'),offline:!!test});
  parlons=new ParlonsClient({directory,key:master});
  try{await parlons.load();}catch(_){parlonsError='The saved Parlons connections could not be opened. Your documents are available; the connections file has been preserved.';}
  master.fill(0);ready=true;menu();makeWorkspace();
  for(const file of pendingFiles.splice(0))await importFile(file);
  if(app.isPackaged)app.setAsDefaultProtocolClient('minimadocs');
  setInterval(()=>{for(const s of editors.values())if(s.ready&&s.dirty&&!s.saving&&!s.exporting&&!s.prompting&&!s.saveError&&!s.config.readonly&&Date.now()-s.lastEdit>2000)requestSave(s);},30000).unref();
 }).catch(e=>{dialog.showErrorBox('minimaDocs could not open',e.message);app.quit();});
 app.on('activate',()=>{if(ready)makeWorkspace();});
 // Closing the last window leaves the Mac app available in its Dock/menu bar.
 app.on('window-all-closed',()=>{});
 app.on('before-quit',event=>{if(quitting)return;event.preventDefault();if(quitRequested)return;quitRequested=true;for(const s of [...editors.values()])s.win.close();finishQuit();});
}
