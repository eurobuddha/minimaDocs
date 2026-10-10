const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs/promises'),path=require('node:path');
const {_electron}=require('playwright-core'),root=path.resolve(__dirname,'..');
async function launch(directory){
 const packaged=process.env.MINIMADOCS_IMAGE_TEST_APP;
 const app=await _electron.launch({executablePath:packaged||require('electron'),args:packaged?['--user-data-dir='+directory]:[root],env:{...process.env,MINIMADOCS_TEST_DIR:directory},timeout:60000});
 // Electron resolves approved beforeunload itself; do not let Playwright also dismiss it.
 const observe=page=>page.on('dialog',dialog=>{if(dialog.type()!=='beforeunload')throw new Error('Unexpected browser dialog: '+dialog.type());});
 app.on('window',observe);for(const page of app.windows())observe(page);
 const home=await app.firstWindow();await home.waitForFunction(()=>document.querySelector('#connection')&&!document.querySelector('#connection').textContent.includes('Opening workspace'));
 assert.equal(await app.evaluate(({app})=>app.getPath('userData')),directory);
 return {app,home};
}
async function stop(app){if(app){await app.evaluate(({BrowserWindow})=>BrowserWindow.getAllWindows().forEach(w=>w.destroy()));await app.close();}}
async function ready(editor){await editor.waitForFunction(()=>document.querySelector('#save')&&!document.querySelector('#save').disabled,null,{timeout:180000});}
async function saved(editor){await editor.waitForFunction(()=>document.querySelector('#save-status').textContent==='Saved on this Mac',null,{timeout:60000});}
test('large imported image saves, exports, reopens and saves on quit',{timeout:240000},async()=>{
 const dir=await fs.mkdtemp(path.join(root,'build/large-image-'));let app;
 try{
  let home;({app,home}=await launch(dir));
  // A deterministic, detailed image exercises multi-MiB data rather than a blank canvas.
  const png=await home.evaluate(()=>{const c=document.createElement('canvas');c.width=1280;c.height=1024;const ctx=c.getContext('2d'),pixels=ctx.createImageData(c.width,c.height);let n=123456789;for(let i=0;i<pixels.data.length;i+=4){n^=n<<13;n^=n>>>17;n^=n<<5;pixels.data[i]=n&255;pixels.data[i+1]=(n>>>8)&255;pixels.data[i+2]=(n>>>16)&255;pixels.data[i+3]=255;}ctx.putImageData(pixels,0,0);return c.toDataURL('image/png').split(',')[1];});
  const imported=path.join(dir,'synthetic-large.png');await fs.writeFile(imported,Buffer.from(png,'base64'));
  await app.evaluate(({dialog},file)=>{dialog.showOpenDialog=async()=>({canceled:false,filePaths:[file]});},imported);
  const opening=app.waitForEvent('window');await home.evaluate(()=>docs.call('import'));const editor=await opening;await ready(editor);
  await editor.locator('#title').fill('Large image regression');await editor.locator('#save').click();await saved(editor);
  const doc=(await home.evaluate(()=>docs.call('list'))).documents[0];assert.ok(doc.id);
  const output=path.join(dir,'large.minimadocs-image.json');
  await app.evaluate(({dialog},file)=>{dialog.showMessageBox=async()=>({response:0});dialog.showSaveDialog=async()=>({canceled:false,filePath:file});},output);
  await editor.locator('#export').click();await editor.waitForFunction(()=>document.querySelector('#save-status').textContent.startsWith('Exported a copy'));
  const project=await fs.readFile(output);assert.ok(project.length>4*1024*1024,'Fixture must exceed the old stack-overflow threshold');
  const parsed=JSON.parse(project);assert.equal(parsed.info.width,1280);assert.equal(parsed.info.height,1024);assert.ok(parsed.layers.length);assert.equal(parsed.data[0].data.split(',')[1],png,'Saved image pixels must remain exact');
  const flattened=path.join(dir,'large.png');
  await app.evaluate(({dialog},file)=>{dialog.showMessageBox=async()=>({response:1});dialog.showSaveDialog=async()=>({canceled:false,filePath:file});},flattened);
  await editor.locator('#export').click();await editor.waitForFunction(()=>document.querySelector('#save-status').textContent.startsWith('Exported a copy'));
  assert.deepEqual(await fs.readFile(flattened),Buffer.from(png,'base64'));
  await editor.close();const reopening=app.waitForEvent('window');await home.evaluate(id=>docs.call('open',{id}),doc.id);const reopened=await reopening;await ready(reopened);
  assert.deepEqual(Buffer.from(await reopened.evaluate(()=>JSON.parse(MinimaDocs.bootstrap()).base64),'base64'),project);
  await reopened.locator('#title').fill('Large image saved on quit');
  await app.evaluate(({dialog})=>{dialog.showMessageBox=async()=>({response:0});});
  const exit=app.waitForEvent('close');await app.evaluate(({app})=>app.quit());await exit;app=null;
  ({app,home}=await launch(dir));const after=(await home.evaluate(()=>docs.call('list'))).documents.find(d=>d.id===doc.id);assert.equal(after.title,'Large image saved on quit');
  console.log('Verified imported image, exact project and PNG bytes, reopen and save-on-quit:',project.length,'bytes');
 }catch(error){if(app)for(const p of app.windows())console.log('Failure state',await p.locator('body').innerText().catch(()=>''));throw error;}finally{await stop(app);}
});
test('save failure pauses autosave and allows retry, discard, and quit',{timeout:180000},async()=>{
 const dir=await fs.mkdtemp(path.join(root,'build/image-save-failure-'));let app;
 try{
  const started=await launch(dir);app=started.app;const home=started.home;
  const png=await home.evaluate(()=>{const c=document.createElement('canvas');c.width=c.height=32;const ctx=c.getContext('2d');ctx.fillStyle='#e63312';ctx.fillRect(0,0,32,32);return c.toDataURL('image/png').split(',')[1];});
  const file=path.join(dir,'synthetic-filled.png');await fs.writeFile(file,Buffer.from(png,'base64'));
  await app.evaluate(({dialog},file)=>{dialog.showOpenDialog=async()=>({canceled:false,filePaths:[file]});},file);
  const opening=app.waitForEvent('window');await home.evaluate(()=>docs.call('import'));const editor=await opening;await ready(editor);
  await editor.locator('#save').click();await saved(editor);const baseline=(await home.evaluate(()=>docs.call('list'))).documents[0];
  await editor.locator('#title').fill('Unsaved failed edit');
  await editor.evaluate(()=>{const engine=document.getElementById('editor').contentWindow,save=engine.FileSave.export_as_json;window.restoreSave=()=>{engine.FileSave.export_as_json=save;};window.failedAttempts=0;engine.FileSave.export_as_json=()=>{window.failedAttempts++;throw new RangeError('Synthetic save failure');};});
  await editor.locator('#save').click();await editor.getByText(/Could not save: .*Synthetic save failure/).waitFor();
  // More than one autosave interval: failure must not keep restarting the operation.
  await new Promise(r=>setTimeout(r,32000));assert.equal(await editor.evaluate(()=>window.failedAttempts),1);
  assert.equal(await editor.locator('#save').isEnabled(),true);
  await app.evaluate(({dialog})=>{global.saveFailureDialog=null;dialog.showMessageBox=async(_win,options)=>{global.saveFailureDialog=options;return {response:1};};});
  await app.evaluate(({app})=>app.quit());
  await editor.waitForFunction(()=>true);const prompt=await app.evaluate(()=>global.saveFailureDialog);
  assert.match(prompt.detail,/last save failed/);assert.equal(prompt.defaultId,1);assert.ok(!editor.isClosed());
  await editor.evaluate(()=>window.restoreSave());await editor.locator('#save').click();await saved(editor);
  await editor.locator('#title').fill('Discard this edit');
  await editor.evaluate(()=>{window.minimaDocsSave=()=>MinimaDocs.saved('invalid file data');});
  await editor.locator('#save').click();await editor.getByText(/Could not save: Invalid file data/).waitFor();
  await app.evaluate(({dialog})=>{dialog.showMessageBox=async()=>({response:2});});
  const exit=app.waitForEvent('close');await app.evaluate(({app})=>app.quit());await exit;app=null;
  const restarted=await launch(dir);app=restarted.app;
  const recovered=(await restarted.home.evaluate(()=>docs.call('list'))).documents.find(d=>d.id===baseline.id);
  assert.equal(recovered.title,'Unsaved failed edit','Discard must retain the last successful save');
  console.log('Verified failed serialization and rejected data recover without trapping close/quit or overwriting saved work');
 }catch(error){if(app)for(const p of app.windows())console.log('Failure state',await p.locator('body').innerText().catch(()=>''));throw error;}finally{await stop(app);}
});
