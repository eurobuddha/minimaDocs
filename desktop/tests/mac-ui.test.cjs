const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs/promises'),path=require('node:path');
const {_electron:electron}=require('playwright-core');
const root=path.resolve(__dirname,'..');
test('Mac workspace opens offline editors, saves and reopens an editable document',{timeout:240000},async()=>{
 const dir=await fs.mkdtemp(path.join(root,'build/ui-test-'));let app;
 try{
  app=await electron.launch({executablePath:require('electron'),args:[root],env:{...process.env,MINIMADOCS_TEST_DIR:dir},timeout:60000});
  const home=await app.firstWindow();await home.waitForSelector('#connection');await home.waitForFunction(()=>document.getElementById('connection').textContent==='Offline session');
  await home.screenshot({path:path.join(root,'build/mac-workspace.png')});
  const created=app.waitForEvent('window');await home.locator('[data-new="docx"]').click();
  const doc=await created;
  console.log('Word window opened');await doc.waitForFunction(()=>document.querySelector('#save')&&!document.querySelector('#save').disabled,null,{timeout:180000});
  console.log('Word editor ready');await doc.locator('#title').fill('Mac document test');
  await doc.evaluate(()=>{const host=document.getElementById('editor').contentWindow;for(let i=0;i<host.frames.length;i++){const api=host.frames[i].Asc?.editor;if(api){api.pluginMethod_PasteHtml('<h1>minimaDocs on Mac</h1><p>Editable document round trip.</p>');return;}}throw Error('No Word API');});
  await doc.screenshot({path:path.join(root,'build/mac-word.png')});
  await doc.locator('#save').click();
  await doc.waitForFunction(()=>document.getElementById('save-status').textContent==='Saved on this Mac',null,{timeout:180000});
  const files=await home.evaluate(()=>docs.call('list'));assert.equal(files.documents.length,1);assert.equal(files.documents[0].title,'Mac document test');
  await doc.locator('#share').click();await home.getByRole('button',{name:'Access & updates',exact:true}).click();
  await home.getByText('You · Owner',{exact:true}).waitFor();await home.locator('#close-dialog').click();
  await doc.close();const reopening=app.waitForEvent('window');await home.evaluate(id=>Promise.all([docs.call('open',{id}),docs.call('open',{id})]),files.documents[0].id);const reopened=await reopening;
  assert.equal(app.windows().length,2,'Rapid duplicate opens must focus one editor');
  await reopened.waitForFunction(()=>document.querySelector('#save')&&!document.querySelector('#save').disabled,null,{timeout:180000});
  assert.equal(await reopened.locator('#title').inputValue(),'Mac document test');
  const encoded=await reopened.evaluate(()=>JSON.parse(MinimaDocs.bootstrap()).base64);
  const xml=require('node:child_process').execFileSync('python3',['-c','import sys,zipfile,io;print(zipfile.ZipFile(io.BytesIO(sys.stdin.buffer.read())).read("word/document.xml").decode())'],{input:Buffer.from(encoded,'base64'),encoding:'utf8'});
  assert.match(xml,/Editable document round trip/);
  await reopened.locator('#title').fill('Saved while quitting');
  const exited=app.waitForEvent('close');
  await app.evaluate(({app,dialog})=>{dialog.showMessageBox=async()=>({response:0});app.quit();});
  await exited;app=null;
  app=await electron.launch({executablePath:require('electron'),args:[root],env:{...process.env,MINIMADOCS_TEST_DIR:dir},timeout:60000});
  const restarted=await app.firstWindow();await restarted.waitForFunction(()=>document.getElementById('connection').textContent==='Offline session');
  const recovered=await restarted.evaluate(()=>docs.call('list'));assert.equal(recovered.documents[0].title,'Saved while quitting');
 }finally{if(app){await app.evaluate(({BrowserWindow})=>BrowserWindow.getAllWindows().forEach(w=>w.destroy()));await app.close();}}
});
