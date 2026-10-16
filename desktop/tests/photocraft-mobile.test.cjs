const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs/promises'),path=require('node:path');
const {_electron}=require('playwright-core'),root=path.resolve(__dirname,'..');
test('touch workspace uses real PhotoCraft, saves editable layers, and recovers from rejected saves',{timeout:300000},async()=>{
 const dir=await fs.mkdtemp(path.join(root,'build/mobile-photo-'));let app;
 try{
  app=await _electron.launch({executablePath:require('electron'),args:[root],env:{...process.env,MINIMADOCS_TEST_DIR:dir},timeout:60000});
  const home=await app.firstWindow();await home.waitForFunction(()=>document.querySelector('#connection')&&!document.querySelector('#connection').textContent.includes('Opening workspace'));
  const png=await home.evaluate(()=>{const c=document.createElement('canvas');c.width=320;c.height=240;const x=c.getContext('2d'),g=x.createLinearGradient(0,0,320,240);g.addColorStop(0,'#195671');g.addColorStop(1,'#f4b46f');x.fillStyle=g;x.fillRect(0,0,320,240);return c.toDataURL().split(',')[1];});
  const opening=app.waitForEvent('window');await app.evaluate(({BrowserWindow})=>{globalThis.mobileTestWindow=new BrowserWindow({width:430,height:850,show:true,webPreferences:{nodeIntegration:false,contextIsolation:true,backgroundThrottling:false}});globalThis.mobileTestWindow.loadURL("about:blank");});
  const page=await opening;const errors=[];page.on('pageerror',e=>errors.push(e.message));page.on('crash',()=>console.log('Mobile test renderer crashed'));page.on('console',m=>{if(m.type()==='error')console.log('Mobile renderer:',m.text());});
  let config={kind:'image',photocraft:true,name:'mobile.png',base64:png};
  await page.addInitScript(config=>{window.mobileHost={config,saved:[],errors:[],ready:false};window.MinimaDocs={bootstrap:()=>JSON.stringify(mobileHost.config),used(){},changed(){},saveDeferred(){mobileHost.deferred=true;},ready(){mobileHost.ready=true;},error(e){mobileHost.errors.push(e);},saved(data){mobileHost.saved.push(data);minimaDocsSaved(!mobileHost.fail,mobileHost.fail?'Synthetic native save failure':null);},exported(data,type){mobileHost.exported={data,type};}};},config);
  const script=await fs.readFile(path.join(root,'../android/app/src/androidTest/assets/photocraft-mobile-check.js'),'utf8');
  await page.goto('https://editors.minimadocs.local/workbench/photocraft-mobile.html');await page.waitForFunction(()=>mobileHost.ready||mobileHost.errors.length,null,{timeout:180000});assert.deepEqual(await page.evaluate(()=>mobileHost.errors),[]);console.log('Mobile engine ready');await page.evaluate(script);
  await page.evaluate(()=>MobilePhotoCheck.prepare());const point=await page.evaluate(()=>MobilePhotoCheck.point());await page.mouse.move(point.x,point.y);await page.mouse.down();await page.mouse.move(point.x+40,point.y+10,{steps:12});await page.mouse.up();
  const expected=await page.evaluate(()=>MobilePhotoCheck.afterMask());await page.evaluate(()=>MobilePhotoCheck.layout());await page.screenshot({path:path.join(root,'build/photocraft-mobile-portrait.png')});
  await page.evaluate(()=>minimaDocsSave());const saved=await page.evaluate(()=>mobileHost.saved.at(-1));assert.equal(Buffer.from(saved,'base64').subarray(0,4).toString('hex'),'504b0304');await fs.writeFile(path.join(dir,'mobile.pcraft'),Buffer.from(saved,'base64'));
  // Reopen through the same phone UI; layer parameters and masks must be identical.
  await page.addInitScript(saved=>{mobileHost.config={kind:'image',photocraft:true,name:'mobile.pcraft',base64:saved};},saved);
  await page.reload();await page.waitForFunction(()=>mobileHost.ready||mobileHost.errors.length,null,{timeout:180000});assert.deepEqual(await page.evaluate(()=>mobileHost.errors),[]);console.log('Mobile engine ready');await page.evaluate(script);await page.evaluate(expected=>MobilePhotoCheck.reopened(expected),expected);
  await page.evaluate(()=>{MobilePhotoCheck.click('Exposure');});await page.waitForSelector('#range-exposure');await page.evaluate(()=>minimaDocsSave(true));assert.equal(await page.evaluate(()=>mobileHost.deferred),true);await page.evaluate(()=>MobilePhotoCheck.cancel());
  await page.evaluate(()=>{mobileHost.fail=true;});await page.evaluate(()=>minimaDocsSave());assert.match((await page.evaluate(()=>mobileHost.errors)).at(-1),/Synthetic native save failure/);
  await page.evaluate(()=>{mobileHost.fail=false;});await page.evaluate(()=>minimaDocsSave());assert.equal(await page.locator('#tabs button').count(),3);
  await page.evaluate(()=>minimaDocsExport('PNG'));const exported=await page.evaluate(()=>mobileHost.exported);assert.equal(exported.type,'PNG');assert.equal(Buffer.from(exported.data,'base64').subarray(0,4).toString('hex'),'89504e47');
  await app.evaluate(({BrowserWindow})=>{globalThis.mobileTestWindow.setContentSize(850,430);});await page.waitForTimeout(400);await page.evaluate(()=>MobilePhotoCheck.layout());await page.screenshot({path:path.join(root,'build/photocraft-mobile-landscape.png')});
  // Import the phone project into the existing desktop host without conversion.
  await app.evaluate(({dialog},file)=>{dialog.showOpenDialog=async()=>({canceled:false,filePaths:[file]});},path.join(dir,'mobile.pcraft'));
  const desktopOpening=app.waitForEvent('window');await home.evaluate(()=>docs.call('import'));const desktop=await desktopOpening;await desktop.waitForFunction(()=>document.querySelector('#save')&&!document.querySelector('#save').disabled,null,{timeout:180000});
  const engine=desktop.frames().find(f=>f.url().includes('/photocraft/index.html'));const desktopState=await engine.evaluate(async()=>(await photoCraft.request('inspect')).value);
  const portable=layers=>layers.map(({adjustmentKind,params,children,...layer})=>children?{...layer,children:portable(children)}:layer);
  assert.deepEqual(desktopState.layers,portable(expected));assert.equal(desktopState.width,240);
  assert.deepEqual(errors,[]);console.log('Verified mobile Looks, adjustment cancel, one-step undo, painted mask, crop, PCraft reopen, PNG export, autosave defer and native failure recovery.');
 }catch(e){if(app)for(const w of app.windows()){if(w.url().includes('photocraft-mobile')){console.log('Mobile UI:',await w.locator('body').innerText().catch(()=>''));await w.screenshot({path:path.join(root,'build/photocraft-mobile-failure.png')}).catch(()=>{});}}throw e;}finally{if(app){await app.evaluate(({BrowserWindow})=>BrowserWindow.getAllWindows().forEach(w=>w.destroy())).catch(()=>{});await app.close();}}
});
