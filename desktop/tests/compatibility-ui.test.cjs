const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs/promises'),path=require('node:path'),cp=require('node:child_process');
const {_electron:electron}=require('playwright-core'),root=path.resolve(__dirname,'..');
const xml=(bytes,name)=>cp.execFileSync('python3',['-c','import sys,zipfile,io;print(zipfile.ZipFile(io.BytesIO(sys.stdin.buffer.read())).read(sys.argv[1]).decode())',name],{input:bytes,encoding:'utf8'});
test('Mac imports Office fixtures, preserves structure, and exports PDF and layered images',{timeout:300000},async()=>{
 const dir=await fs.mkdtemp(path.join(root,'build/compatibility-'));let app;
 try{
  app=await electron.launch({executablePath:require('electron'),args:[root],env:{...process.env,MINIMADOCS_TEST_DIR:dir}});const home=await app.firstWindow();
  await home.waitForFunction(()=>document.getElementById('connection')?.textContent==='Offline session');
  for(const kind of ['docx','xlsx','image']){
   console.log('Checking',kind);const opening=app.waitForEvent('window');
   if(kind==='image')await home.evaluate(()=>docs.call('new',{kind:'image'}));
   else{
    const fixture=path.resolve(root,'../android/build/fixtures/compatibility.'+kind);
    await app.evaluate(({dialog},file)=>{dialog.showOpenDialog=async()=>({canceled:false,filePaths:[file]});},fixture);
    await home.evaluate(()=>docs.call('import'));
   }
   const editor=await opening;await editor.waitForFunction(()=>document.querySelector('#save')&&!document.querySelector('#save').disabled,null,{timeout:180000});
   await editor.locator('#title').fill('Compatibility '+kind);
   await editor.locator('#save').click();await editor.waitForFunction(()=>document.getElementById('save-status').textContent==='Saved on this Mac',null,{timeout:180000});
   await editor.screenshot({path:path.join(root,'build/mac-'+kind+'.png')});
   const file=path.join(dir,'saved.'+(kind==='image'?'minimadocs-image.json':kind));
   await app.evaluate(({dialog},file)=>{dialog.showMessageBox=async()=>({response:0});dialog.showSaveDialog=async()=>({canceled:false,filePath:file});},file);
   await editor.locator('#export').click();await editor.waitForFunction(()=>document.getElementById('save-status').textContent.startsWith('Exported a copy'),null,{timeout:180000});
   const bytes=await fs.readFile(file);
   if(kind==='docx'){assert.match(xml(bytes,'word/document.xml'),/Body text: alpha, beta, gamma/);assert.match(xml(bytes,'word/document.xml'),/w:tbl/);assert.match(xml(bytes,'word/header1.xml'),/minimaDocs header/);assert.match(xml(bytes,'word/footer1.xml'),/minimaDocs footer/);}
   if(kind==='xlsx'){const sheet=xml(bytes,'xl/worksheets/sheet1.xml');assert.match(sheet,/SUM\(B2:B3\)/);assert.match(sheet,/<v>20<\/v>/);assert.match(sheet,/pane/);assert.match(sheet,/autoFilter/);}
   if(kind==='image')assert.ok(Array.isArray(JSON.parse(bytes).layers));
   const rendered=path.join(dir,kind+(kind==='image'?'.png':'.pdf'));
   await app.evaluate(({dialog},file)=>{dialog.showMessageBox=async()=>({response:1});dialog.showSaveDialog=async()=>({canceled:false,filePath:file});},rendered);
   await editor.locator('#export').click();await editor.waitForFunction(()=>document.getElementById('save-status').textContent.startsWith('Exported a copy'),null,{timeout:180000});
   const out=await fs.readFile(rendered);assert.ok(out.length>8);if(kind==='image')assert.equal(out.subarray(0,8).toString('hex'),'89504e470d0a1a0a');else assert.equal(out.subarray(0,5).toString(),'%PDF-');
   await editor.close();console.log('Verified',kind,'save and export');
  }
 }finally{if(app){await app.evaluate(({BrowserWindow})=>BrowserWindow.getAllWindows().forEach(w=>w.destroy()));await app.close();}}
});
