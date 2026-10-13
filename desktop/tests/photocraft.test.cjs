const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs/promises'),path=require('node:path');
const {_electron}=require('playwright-core'),root=path.resolve(__dirname,'..');
async function launch(dir){
 const packaged=process.env.MINIMADOCS_IMAGE_TEST_APP;
 const app=await _electron.launch({executablePath:packaged||require('electron'),args:packaged?['--user-data-dir='+dir]:[root],env:{...process.env,MINIMADOCS_TEST_DIR:dir},timeout:60000});
 const home=await app.firstWindow();await home.waitForFunction(()=>document.querySelector('#connection')&&!document.querySelector('#connection').textContent.includes('Opening workspace'));
 app.on('window',p=>{p.on('console',m=>{if(m.type()==='error')console.log('Renderer:',m.text());});p.on('pageerror',e=>console.log('Page error:',e.message));});
 return {app,home};
}
async function ready(editor){await editor.waitForFunction(()=>document.querySelector('#save')&&!document.querySelector('#save').disabled,null,{timeout:180000});return editor.frames().find(f=>f.url().includes('/photocraft/index.html'));}
async function command(frame,command,params={}){return frame.evaluate(async({command,params})=>(await photoCraft.request('command',{command,params})).value,{command,params});}
async function inspect(frame){return frame.evaluate(async()=>(await photoCraft.request('inspect')).value);}
async function saved(editor){await editor.waitForFunction(()=>document.querySelector('#save-status').textContent==='Saved on this Mac',null,{timeout:90000});}
async function stop(app){if(app){await app.evaluate(({BrowserWindow})=>BrowserWindow.getAllWindows().forEach(w=>w.destroy())).catch(()=>{});await app.close();}}
test('PhotoCraft layers, masks and adjustments survive encrypted save, native exports, reopen and save failure',{timeout:300000},async()=>{
 const dir=await fs.mkdtemp(path.join(root,'build/photocraft-test-'));let app;
 try{
  const started=await launch(dir);app=started.app;const home=started.home;
  const png=await home.evaluate(()=>{const c=document.createElement('canvas');c.width=320;c.height=240;const x=c.getContext('2d'),g=x.createLinearGradient(0,0,320,240);g.addColorStop(0,'#195671');g.addColorStop(1,'#f4b46f');x.fillStyle=g;x.fillRect(0,0,320,240);return c.toDataURL().split(',')[1];});
  const file=path.join(dir,'synthetic-gradient.png');await fs.writeFile(file,Buffer.from(png,'base64'));
  await app.evaluate(({dialog},file)=>{dialog.showOpenDialog=async()=>({canceled:false,filePaths:[file]});},file);
  let opening=app.waitForEvent('window');await home.evaluate(()=>docs.call('import'));let editor=await opening,frame=await ready(editor);assert.ok(frame);
  const initial=await inspect(frame);assert.equal(initial.width,320);assert.equal(initial.height,240);
  await command(frame,'layer.new.layer',{name:'Retouch'});
  await command(frame,'edit.fill',{color:'#de5f43'});
  await command(frame,'layer.layerMask.revealAll');
  await command(frame,'layer.newAdjustmentLayer.brightnessContrast',{brightness:12,contrast:8});
  const before=await inspect(frame);assert.ok(before.layers.some(l=>l.name==='Retouch'&&l.hasMask));assert.ok(before.layers.some(l=>l.kind==='Adjustment'));
  await editor.locator('#title').fill('PhotoCraft round trip');await editor.locator('#save').click();await saved(editor);
  const id=(await home.evaluate(()=>docs.call('list'))).documents[0].id;
  await editor.screenshot({path:path.join(root,'build/photocraft-workspace.png')});
  for(const [choice,extension,magic]of [[0,'pcraft','504b0304'],[1,'png','89504e47'],[2,'psd','38425053']]){
   const output=path.join(dir,'layered.'+extension);
   await app.evaluate(({dialog},{choice,output})=>{dialog.showMessageBox=async()=>({response:choice});dialog.showSaveDialog=async()=>({canceled:false,filePath:output});},{choice,output});
   await editor.evaluate(()=>desktopEditor.action('export'));await editor.waitForFunction(()=>/^(Exported a copy|Export notes:)/.test(document.querySelector('#save-status').textContent),null,{timeout:90000});
   const bytes=await fs.readFile(output);assert.equal(bytes.subarray(0,4).toString('hex'),magic);
  }
  await editor.close();opening=app.waitForEvent('window');await home.evaluate(id=>docs.call('open',{id}),id);editor=await opening;frame=await ready(editor);
  assert.match((await editor.evaluate(()=>JSON.parse(MinimaDocs.bootstrap()))).name,/\.pcraft$/);
  const after=await inspect(frame);assert.deepEqual(after.layers,before.layers);assert.equal(after.width,before.width);
  // Failed serialization preserves the saved snapshot and leaves a usable native close/quit path.
  await editor.locator('#title').fill('Failed save must not replace this');
  await frame.evaluate(()=>{const request=photoCraft.request;window.restore=()=>{photoCraft.request=request;};photoCraft.request=(method,...args)=>method==='snapshot'?Promise.reject(Error('Synthetic PhotoCraft save failure')):request(method,...args);});
  await editor.locator('#save').click();await editor.getByText(/Could not save: Synthetic PhotoCraft save failure/).waitFor();
  await app.evaluate(({dialog})=>{dialog.showMessageBox=async()=>({response:1});});await app.evaluate(({app})=>app.quit());assert.ok(!editor.isClosed());
  await frame.evaluate(()=>restore());await editor.locator('#title').fill('Recovered PhotoCraft save');await editor.locator('#save').click();await saved(editor);
  await editor.locator('#title').fill('PhotoCraft save on quit');await app.evaluate(({dialog})=>{dialog.showMessageBox=async()=>({response:0});});
  const exit=app.waitForEvent('close');await app.evaluate(({app})=>app.quit());await exit;app=null;
  const again=await launch(dir);app=again.app;assert.equal((await again.home.evaluate(()=>docs.call('list'))).documents[0].title,'PhotoCraft save on quit');
  console.log('Verified PhotoCraft native layers/masks/adjustments, PCraft/PNG/PSD exports, encrypted reopen, save failure recovery and save on quit.');
 }catch(e){if(app)for(const w of app.windows())console.log('Window:',await w.locator('body').innerText().catch(()=>''));throw e;}finally{await stop(app);}
});

test('PhotoCraft cancelled exports and extra tabs cannot silently lose work',{timeout:180000},async()=>{
 const dir=await fs.mkdtemp(path.join(root,'build/photocraft-cancel-'));let app;
 try{
  const started=await launch(dir);app=started.app;const home=started.home;
  const opening=app.waitForEvent('window');await home.evaluate(()=>docs.call('new',{kind:'image'}));const editor=await opening,frame=await ready(editor);
  await editor.locator('#save').click();await saved(editor);
  await command(frame,'history.newDocument',{name:'Independent image'});
  await command(frame,'layer.new.layer',{name:'Keep this layer'});
  await assert.rejects(frame.evaluate(()=>photoCraft.request('snapshot')),/additional image tabs/);
  await frame.evaluate(()=>{const request=photoCraft.request;window.exportAcknowledged=0;photoCraft.request=async(method,...args)=>{const r=await request(method,...args);if(method==='exportAck')window.exportAcknowledged++;return r;};});
  await app.evaluate(({dialog})=>{dialog.showSaveDialog=async()=>({canceled:true});});
  await command(frame,'file.save',{path:'independent.pcraft'});await frame.waitForFunction(()=>exportAcknowledged===1);
  let state=await command(frame,'session.inspect');assert.equal(state.documents[1].dirty,true,'Cancel must retain unsaved changes');
  const output=path.join(dir,'independent.pcraft');
  await app.evaluate(({dialog},output)=>{dialog.showSaveDialog=async()=>({canceled:false,filePath:output});},output);
  await command(frame,'file.save',{path:'independent.pcraft'});await frame.waitForFunction(()=>exportAcknowledged===2);
  state=await command(frame,'session.inspect');assert.equal(state.documents[1].dirty,false);assert.equal((await fs.readFile(output)).subarray(0,4).toString('hex'),'504b0304');
  await command(frame,'file.close');assert.equal((await command(frame,'session.inspect')).documents.length,1);
  await editor.locator('#save').click();await saved(editor);
  // File > New creates another native document window, preserving this document's identity.
  const next=app.waitForEvent('window');await command(frame,'file.new');const fresh=await next;await ready(fresh);
  assert.notEqual((await fresh.evaluate(()=>JSON.parse(MinimaDocs.bootstrap()))).id,(await editor.evaluate(()=>JSON.parse(MinimaDocs.bootstrap()))).id);
  await app.evaluate(({dialog})=>{dialog.showMessageBox=async()=>({response:2});});await fresh.close();
  console.log('Verified cancelled export, saved export acknowledgement, extra-tab guard and host-owned File > New.');
 }finally{await stop(app);}
});
