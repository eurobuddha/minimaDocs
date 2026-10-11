const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs/promises'),path=require('node:path');
const {_electron}=require('playwright-core'),root=path.resolve(__dirname,'..');
test('Android project opens on desktop and controls survive phone/foldable resizing',{timeout:120000},async()=>{
 const dir=await fs.mkdtemp(path.join(root,'build/image-phone-'));let app;
 try{
  const packaged=process.env.MINIMADOCS_IMAGE_TEST_APP;
  app=await _electron.launch({executablePath:packaged||require('electron'),args:packaged?['--user-data-dir='+dir]:[root],env:{...process.env,MINIMADOCS_TEST_DIR:dir}});
  const home=await app.firstWindow();await home.waitForFunction(()=>document.querySelector('#connection')&&!document.querySelector('#connection').textContent.includes('Opening workspace'));
  const file=path.join(__dirname,'fixtures/android-image-studio.minimadocs-image.json'),expected=JSON.parse(await fs.readFile(file,'utf8'));
  await app.evaluate(({dialog},file)=>{dialog.showOpenDialog=async()=>({canceled:false,filePaths:[file]});},file);
  const opening=app.waitForEvent('window');await home.evaluate(()=>docs.call('import'));const editor=await opening;editor.on('dialog',()=>{});await editor.waitForFunction(()=>!document.querySelector('#save').disabled);
  const frame=editor.frames().find(f=>f.url().includes('/image/index.html'));
  const actual=await frame.evaluate(async()=>{await ImageStudio.flush();return JSON.parse(FileSave.export_as_json());});
  assert.deepEqual(actual.layers,expected.layers);assert.deepEqual(actual.data,expected.data);
  for(const width of packaged?[]:[360,768,1024,420,1320]){
   await editor.locator('#editor').evaluate((el,width)=>{el.style.width=width+'px';el.style.height='600px';},width);
   await frame.waitForFunction(width=>innerWidth===width,width);
   if(width<=900){
    await frame.getByRole('button',{name:'Layers',exact:true}).click();
    assert.equal(await frame.locator('#studio-slot-layers .block.layers').count(),1);
    assert.equal(await frame.locator('#studio-slot-tools #tools_container').count(),1);
    const layout=await frame.evaluate(()=>{const c=document.querySelector('#canvas_minipaint').getBoundingClientRect(),s=document.querySelector('.studio-sheet').getBoundingClientRect(),bar=document.querySelector('.studio-catbar').getBoundingClientRect();return {canvas:c.height,sheet:s.height,bar:bar.bottom,height:innerHeight};});
    assert.ok(layout.canvas>30&&layout.sheet>50&&layout.bar<=layout.height+1);
    await frame.getByRole('button',{name:'Close image controls',exact:true}).click();
   }else{
    await frame.locator('.sidebar_right>.block.layers').waitFor({state:'attached'});
    assert.equal(await frame.locator('.wrapper>#tools_container').count(),1);
    assert.equal(await frame.locator('.submenu>#action_attributes').count(),1);
   }
  }
  await editor.locator('#save').click();await editor.waitForFunction(()=>document.querySelector('#save-status').textContent==='Saved on this Mac');
  console.log(packaged?'Android project bytes/layers retained by the published Mac app.':'Android project bytes/layers retained on Mac; 360, 420, 768, 1024 and 1320px layouts verified.');
 }finally{if(app){await app.evaluate(({BrowserWindow})=>BrowserWindow.getAllWindows().forEach(w=>w.destroy())).catch(()=>{});await app.close();}}
});
