// Opt-in release smoke test: launches the real app with real Keychain protection.
// It reads the workspace and opens unsaved blank drafts; it does not save or share files.
const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs/promises'),path=require('node:path');
const {_electron:electron}=require('playwright-core'),root=path.resolve(__dirname,'..');
test('signed Mac bundle opens its Keychain workspace and all bundled editors',{timeout:240000},async()=>{
 let instance;
 try{
  instance=await electron.launch({executablePath:path.join(root,'dist/mac-arm64/minimaDocs.app/Contents/MacOS/minimaDocs'),timeout:60000});
  const home=await instance.firstWindow();await home.waitForSelector('#connection');
  await home.waitForFunction(()=>!document.getElementById('connection').textContent.includes('Opening workspace'));
  const metadata=await instance.evaluate(({app,safeStorage})=>({packaged:app.isPackaged,version:app.getVersion(),directory:app.getPath('userData'),encryption:safeStorage.isEncryptionAvailable()}));
  assert.equal(metadata.packaged,true);assert.equal(metadata.version,require('../package.json').version);assert.equal(metadata.encryption,true);
  const protectedKey=await fs.readFile(path.join(metadata.directory,'storage-key.protected'));assert.ok(protectedKey.length>32);
  await assert.rejects(fs.access(path.join(metadata.directory,'synthetic-test-key')));
  const before=await home.evaluate(()=>docs.call('list'));assert.notEqual(before.connection,'Offline session');
  await home.screenshot({path:path.join(root,'build/mac-packaged-workspace.png')});
  for(const kind of ['docx','xlsx','image']){
   const opened=instance.waitForEvent('window');await home.evaluate(kind=>docs.call('new',{kind}),kind);const editor=await opened;
   await editor.waitForFunction(()=>document.querySelector('#save')&&!document.querySelector('#save').disabled,null,{timeout:180000});
   await editor.screenshot({path:path.join(root,'build/mac-packaged-'+kind+'.png')});
   await instance.evaluate(({BrowserWindow})=>{const win=BrowserWindow.getAllWindows().find(w=>w.webContents.getURL().includes('/workbench/'));win.destroy();});
   console.log('Packaged editor ready:',kind);
  }
  const after=await home.evaluate(()=>docs.call('list'));assert.deepEqual(after.documents,before.documents);
  console.log('Verified real Keychain storage, packaged version',metadata.version);
 }finally{if(instance){await instance.evaluate(({BrowserWindow})=>BrowserWindow.getAllWindows().forEach(w=>w.destroy()));await instance.close();}}
});
