// Opt-in release smoke test: launches the real app with real Keychain protection.
// It reads the workspace and opens unsaved blank drafts; it does not save or share files.
const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs/promises'),path=require('node:path');
const {_electron:electron}=require('playwright-core'),root=path.resolve(__dirname,'..');
const {execFileSync}=require('node:child_process');
test('packaged JDBC keeps dependency contents and signs both Mac libraries',async()=>{
 const backend=path.join(root,'dist/mac-arm64/minimaDocs.app/Contents/Resources/backend');
 const jarName=(await fs.readdir(backend)).find(n=>/^sqlite-jdbc-.*\.jar$/.test(n));
 assert.ok(jarName);const jar=path.join(backend,jarName),source=path.join(root,'backend/build/package-input',jarName);
 const compare='import sys,zipfile\nwith zipfile.ZipFile(sys.argv[1]) as a,zipfile.ZipFile(sys.argv[2]) as b:\n assert set(a.namelist())==set(b.namelist())\n for n in a.namelist():\n  if not n.endswith(".dylib"):assert a.read(n)==b.read(n),n';
 execFileSync('python3',['-c',compare,source,jar]);
 const directory=await fs.mkdtemp(path.join(root,'build/verify-native-'));
 try{
  for(const arch of ['aarch64','x86_64']){
   const file=path.join(directory,arch+'.dylib');
   await fs.writeFile(file,execFileSync('/usr/bin/unzip',['-p',jar,'org/sqlite/native/Mac/'+arch+'/libsqlitejdbc.dylib'],{maxBuffer:16*1024*1024}));
   execFileSync('/usr/bin/codesign',['--verify','--strict',file]);
   // codesign emits its metadata on stderr, including on success.
   const result=require('node:child_process').spawnSync('/usr/bin/codesign',['-dv','--verbose=2',file],{encoding:'utf8'});
   assert.equal(result.status,0);assert.match(result.stderr,/Authority=Developer ID Application/);assert.match(result.stderr,/Timestamp=/);
  }
 }finally{await fs.rm(directory,{recursive:true,force:true});}
});
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
