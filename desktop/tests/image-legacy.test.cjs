const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs/promises'),path=require('node:path');
const {_electron}=require('playwright-core'),root=path.resolve(__dirname,'..');
async function launch(dir){
 const packaged=process.env.MINIMADOCS_IMAGE_TEST_APP;
 const app=await _electron.launch({executablePath:packaged||require('electron'),args:packaged?['--user-data-dir='+dir]:[root],env:{...process.env,MINIMADOCS_TEST_DIR:dir},timeout:60000});
 const home=await app.firstWindow();await home.waitForFunction(()=>document.querySelector('#connection')&&!document.querySelector('#connection').textContent.includes('Opening workspace'));
 return {app,home};
}
async function ready(editor){await editor.waitForFunction(()=>document.querySelector('#save')&&!document.querySelector('#save').disabled,null,{timeout:180000});return editor.frames().find(f=>f.url().includes('/photocraft/index.html'));}
async function saved(editor){await editor.waitForFunction(()=>document.querySelector('#save-status').textContent==='Saved on this Mac',null,{timeout:90000});}
const inspect=frame=>frame.evaluate(async()=>(await photoCraft.request('inspect')).value);
async function stop(app){if(app){await app.evaluate(({BrowserWindow})=>BrowserWindow.getAllWindows().forEach(w=>w.destroy())).catch(()=>{});await app.close();}}
async function importLegacy(app,home,file){
 await app.evaluate(({dialog},file)=>{dialog.showOpenDialog=async()=>({canceled:false,filePaths:[file]});},file);
 const opening=app.waitForEvent('window');await home.evaluate(()=>docs.call('import'));const editor=await opening;return {editor,frame:await ready(editor)};
}
test('Android miniPaint project opens in PhotoCraft with its layers, saves as .pcraft and keeps the original as its own document',{timeout:300000},async()=>{
 const dir=await fs.mkdtemp(path.join(root,'build/image-legacy-'));let app;
 try{
  let home;({app,home}=await launch(dir));
  const fixture=path.join(__dirname,'fixtures/android-image-studio.minimadocs-image.json'),original=await fs.readFile(fixture);
  // Store the project exactly as a pre-0.1.4 editor saved it, then reopen it from Files.
  const first=await importLegacy(app,home,fixture);
  await first.editor.evaluate(b64=>{window.minimaDocsSave=()=>MinimaDocs.saved(b64);},original.toString('base64'));
  await first.editor.locator('#save').click();await saved(first.editor);
  const stored=(await home.evaluate(()=>docs.call('list'))).documents[0];
  await first.editor.close();let opening=app.waitForEvent('window');await home.evaluate(id=>docs.call('open',{id}),stored.id);
  const editor=await opening,frame=await ready(editor);
  assert.ok(frame,'Legacy projects must open in PhotoCraft, not miniPaint');
  assert.equal((await editor.evaluate(()=>JSON.parse(MinimaDocs.bootstrap()))).legacy,true);
  assert.equal(editor.frames().some(f=>f.url().includes('/image/index.html')),false,'The hidden converter frame must be removed');
  const doc=await inspect(frame);assert.equal(doc.width,320);assert.equal(doc.height,240);
  // miniPaint order is top first: masked multiply layer, brush stroke, hidden original.
  assert.deepEqual(doc.layers.map(l=>[l.name,l.visible]),[['Data URL · adjusted',true],['Brush #3',true],['Data URL',false]]);
  assert.equal(doc.layers[0].blend,'Multiply');assert.ok(Math.abs(doc.layers[0].opacity-0.85)<0.01);
  assert.ok(doc.layers[1].bounds&&doc.layers[1].bounds[2]>0,'Vector brush must be rendered into pixels');
  assert.match(await editor.locator('#save-status').textContent(),/Converted from the older image editor/);
  await editor.locator('#title').fill('Converted phone image');await editor.locator('#save').click();await saved(editor);
  const id=stored.id,list=(await home.evaluate(()=>docs.call('list'))).documents;
  const kept=list.find(d=>d.title==='Converted phone image (original miniPaint image)');assert.ok(kept&&kept.id!==id,'The original project must be kept as its own document');
  let open=app.waitForEvent('window');await home.evaluate(id=>docs.call('open',{id}),kept.id);const keptEditor=await open;await ready(keptEditor);
  const keptConfig=await keptEditor.evaluate(()=>JSON.parse(MinimaDocs.bootstrap()));assert.equal(keptConfig.legacy,true);assert.deepEqual(Buffer.from(keptConfig.base64,'base64'),original,'Original bytes must be exact');
  await app.evaluate(({dialog})=>{dialog.showMessageBox=async()=>({response:2});});await keptEditor.close();
  // A second save must not file another copy.
  await editor.locator('#title').fill('Converted phone image');await editor.locator('#save').click();await saved(editor);
  const titles=(await home.evaluate(()=>docs.call('list'))).documents.map(d=>d.title);assert.deepEqual(titles.filter(t=>t.endsWith('(original miniPaint image)')),['Converted phone image (original miniPaint image)'],'Only Files documents get a kept original, once');
  await editor.close();const reopening=app.waitForEvent('window');await home.evaluate(id=>docs.call('open',{id}),id);const reopened=await reopening;const again=await ready(reopened);
  const config=await reopened.evaluate(()=>JSON.parse(MinimaDocs.bootstrap()));assert.match(config.name,/\.pcraft$/);assert.equal(config.legacy,false);
  assert.deepEqual((await inspect(again)).layers.map(l=>[l.name,l.visible,l.blend]),doc.layers.map(l=>[l.name,l.visible,l.blend]));
  console.log('Verified legacy project → PhotoCraft layers, visibility, blend, opacity, vector render and .pcraft reopen.');
 }catch(e){if(app)for(const w of app.windows())console.log('Window:',await w.locator('body').innerText().catch(()=>''));throw e;}finally{await stop(app);}
});
test('Large legacy image converts with exact pixels',{timeout:300000},async()=>{
 const dir=await fs.mkdtemp(path.join(root,'build/image-legacy-large-'));let app;
 try{
  let home;({app,home}=await launch(dir));
  // Deterministic noise: a multi-MiB project, beyond the old stack-overflow threshold.
  const png=await home.evaluate(()=>{const c=document.createElement('canvas');c.width=1280;c.height=1024;const ctx=c.getContext('2d'),p=ctx.createImageData(c.width,c.height);let n=123456789;for(let i=0;i<p.data.length;i+=4){n^=n<<13;n^=n>>>17;n^=n<<5;p.data[i]=n&255;p.data[i+1]=(n>>>8)&255;p.data[i+2]=(n>>>16)&255;p.data[i+3]=255;}ctx.putImageData(p,0,0);return c.toDataURL('image/png').split(',')[1];});
  const file=path.join(dir,'synthetic-large.minimadocs-image.json');await fs.writeFile(file,require('./legacy-image.cjs')(png,1280,1024));
  const {editor,frame}=await importLegacy(app,home,file);
  const flattened=path.join(dir,'large.png');
  await app.evaluate(({dialog},file)=>{dialog.showMessageBox=async()=>({response:1});dialog.showSaveDialog=async()=>({canceled:false,filePath:file});},flattened);
  await editor.locator('#export').click();await editor.waitForFunction(()=>/^(Exported a copy|Export notes:)/.test(document.querySelector('#save-status').textContent),null,{timeout:120000});
  const pixels=async b64=>home.evaluate(async b64=>{const i=new Image();i.src='data:image/png;base64,'+b64;await i.decode();const c=document.createElement('canvas');c.width=i.width;c.height=i.height;const x=c.getContext('2d');x.drawImage(i,0,0);return Array.from(x.getImageData(0,0,c.width,c.height).data.filter((_,k)=>k%997===0));},b64);
  assert.deepEqual(await pixels((await fs.readFile(flattened)).toString('base64')),await pixels(png),'Converted pixels must match the original layer');
  assert.ok(frame);
  console.log('Verified large legacy conversion with exact sampled pixels.');
 }catch(e){if(app)for(const w of app.windows())console.log('Window:',await w.locator('body').innerText().catch(()=>''));throw e;}finally{await stop(app);}
});
