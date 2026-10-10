'use strict';
// Real host adapters and encrypted loopback invitation transport; synthetic documents only.
const {test}=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs/promises'),path=require('node:path'),crypto=require('node:crypto');
const {spawn}=require('node:child_process'),readline=require('node:readline');
const {ParlonsClient}=require('../main/parlons.cjs'),{Backend}=require('../main/backend.cjs');
const root=path.resolve(__dirname,'..'),maxima=path.resolve(root,'../../../maxima');
async function until(action,description,timeout=120000){const end=Date.now()+timeout;let error;while(Date.now()<end){try{const value=await action();if(value)return value;}catch(e){error=e;}await new Promise(r=>setTimeout(r,250));}throw Error(description+(error?': '+error.message:''));}
async function hosts(directory){
 const cp=(await fs.readFile(path.join(maxima,'desktop/build/docs-fixture-classpath.txt'),'utf8')).trim();
 const child=spawn('/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java',['-cp',cp,'com.eurobuddha.maxima.desktop.DocsHostsFixture',directory]);
 let log='';child.stderr.on('data',b=>{log=(log+b).slice(-5000);});
 const ready=new Promise((resolve,reject)=>{const lines=readline.createInterface({input:child.stdout});lines.on('line',line=>{if(line.startsWith('DOCS_FIXTURE '))resolve(JSON.parse(line.slice(13)));});child.once('exit',code=>reject(Error('Hosts exited '+code+' '+log)));child.once('error',reject);});
 const timer=setTimeout(()=>child.kill(),30000);let links;try{links=await ready;}finally{clearTimeout(timer);}
 return {links,child,async close(){if(child.exitCode!==null)return;const done=new Promise(r=>child.once('exit',r));child.stdin.end('quit\n');const kill=setTimeout(()=>child.kill('SIGKILL'),10000);await done;clearTimeout(kill);}};
}
test('both real hosts deliver invitations and preserve separated credentials through restart',{timeout:90000},async()=>{
 const directory=await fs.mkdtemp(path.join(root,'build/real-parlons-'));let host,client;const key=crypto.randomBytes(32);
 try{
  host=await hosts(path.join(directory,'hosts'));client=new ParlonsClient({directory,key});await client.load();
  const desktop=await client.connect(host.links.desktop),core=await client.connect(host.links.core);
  const a=await client.snapshot(desktop.id),b=await client.snapshot(core.id);
  assert.equal(a.contacts[0].name,b.contacts[0].name);assert.notEqual(a.contacts[0].key,b.contacts[0].key);
  const line='MN2.Test|Mx12345678@host:9001|AQ==|Ag==';
  assert.equal((await client.send(desktop.id,a.contacts[0].key,line)).state,'queued');
  const inbox=await until(async()=>{const s=await client.snapshot(core.id);return s.invitations[0];},'Desktop invitation not received',15000);assert.equal(inbox.line,line);
  await client.send(core.id,b.contacts[0].key,line+'|Reply|r');
  await until(async()=>(await client.snapshot(desktop.id)).invitations[0],'Core invitation not received',15000);
  await client.close();await host.close();host=await hosts(path.join(directory,'hosts'));
  client=new ParlonsClient({directory,key});await client.load();assert.equal(client.list().length,2);
  assert.equal((await client.snapshot(core.id)).invitations[0].id,inbox.id);
  await client.dismiss(core.id,inbox.id);await client.send(desktop.id,a.contacts[0].key,line);
  await new Promise(r=>setTimeout(r,600));assert.equal((await client.snapshot(core.id)).invitations.length,0);
  await client.remove(desktop.id,a.contacts[0].key);assert.equal((await client.snapshot(desktop.id)).contacts.length,0);
  // Parlons removal also notifies the peer; the other account identity must stay distinct.
  assert.equal((await client.snapshot(core.id)).id,core.id);
  host.child.stdin.write('revoke core\n');await until(async()=>{try{await client.snapshot(core.id);return false;}catch(e){return /refused access/.test(e.message);}},'Host revocation failed',5000);
  await client.disconnect(desktop.id);assert.equal(client.list().length,1);
 }finally{await client?.close();await host?.close();}
});
test('Parlons invitations establish exact DOCX and XLSX delivery with existing permission checks',{timeout:360000},async()=>{
 const directory=await fs.mkdtemp(path.join(root,'build/parlons-documents-'));let host,client;const peers=[];
 try{
  host=await hosts(path.join(directory,'hosts'));client=new ParlonsClient({directory,key:crypto.randomBytes(32)});await client.load();
  const aHost=await client.connect(host.links.desktop),bHost=await client.connect(host.links.core);
  const contact=(await client.snapshot(aHost.id)).contacts[0];
  for(const name of ['Parlons document sender','Parlons document recipient']){const dir=path.join(directory,String(peers.length));await fs.mkdir(dir);const peer=new Backend(path.join(root,'build/runtime/bin/java'),path.join(root,'backend/build/package-input'),dir);peers.push(peer);await peer.call('init',{key:crypto.randomBytes(32).toString('base64')});await peer.call('name',{name});}
  const [a,b]=peers;
  await Promise.all(peers.map(peer=>until(async()=>(await peer.call('list')).connection==='Maxima connected','Document transport unavailable')));
  for(const kind of ['docx','xlsx']){
   const bytes=await fs.readFile(path.resolve(root,'../android/build/fixtures/compatibility.'+kind));
   const opened=await a.call('open',{kind,title:'Parlons synthetic '+kind});
   const saved=await a.call('save',{session:opened.session,title:'Parlons synthetic '+kind,base64:bytes.toString('base64')});
   const offer=await a.call('invitation',{id:saved.id,level:kind==='docx'?'WRITE':'READ'});
   assert.equal((await a.call('qrRead',{base64:offer.qr.split(',')[1]})).text,offer.link);
   await client.send(aHost.id,contact.key,offer.line);
   const invitation=await until(async()=>(await client.snapshot(bHost.id)).invitations.find(x=>x.line===offer.line),'Document invitation missing',15000);
   const review=await b.call('preview',{text:invitation.line});assert.equal(review.level,kind==='docx'?'WRITE':'READ');
   await b.call('accept',{text:invitation.line});await client.dismiss(bHost.id,invitation.id);
   const received=await until(()=>b.call('open',{id:saved.id}),'Document was not delivered');
   assert.equal(received.base64,bytes.toString('base64'));assert.equal(received.readonly,kind==='xlsx');
   if(kind==='xlsx')await assert.rejects(b.call('save',{session:received.session,title:'Refused',base64:received.base64}));
   console.log('Verified Parlons invitation, QR compatibility and exact '+kind.toUpperCase()+' delivery');
  }
 }finally{await Promise.all(peers.map(p=>p.close()));await client?.close();await host?.close();}
});

test('real account panel approves minimaDocs and the workspace displays both real accounts',{timeout:120000},async()=>{
 const directory=await fs.mkdtemp(path.join(root,'build/parlons-host-ui-'));let host,app;
 try{
  host=await hosts(path.join(directory,'hosts'));
  app=await require('playwright-core')._electron.launch({executablePath:require('electron'),args:[root],env:{...process.env,MINIMADOCS_TEST_DIR:path.join(directory,'workspace')},timeout:60000});
  const home=await app.firstWindow();await home.waitForFunction(()=>document.getElementById('connection').textContent==='Offline session');
  await app.evaluate(({BrowserWindow},url)=>{const w=new BrowserWindow({width:1100,height:850,webPreferences:{partition:"parlons-panel-fixture"}});w.loadURL(url);},host.links.panel);
  const panel=await until(async()=>{for(const w of app.windows())if(w.url().startsWith('http://127.0.0.1:'))return w;},'Account panel did not open',20000);
  await panel.waitForFunction(()=>document.querySelector('[data-route="settings"]'));
  await panel.locator('[data-route="settings"]').click();
  panel.on('dialog',d=>d.accept());await panel.getByRole('button',{name:'Connect minimaDocs',exact:true}).click();
  await panel.locator('#docsLink').waitFor({state:'visible'});
  const link=await panel.locator('#docsLink').inputValue();assert.match(link,/provider=core/);
  await panel.screenshot({path:path.join(root,'build/parlons-core-connected-apps.png')});
  await home.locator('[data-page="people"]').click();
  for(const value of [host.links.desktop,link]){
   await home.getByRole('button',{name:'Connect Parlons',exact:true}).click();await home.getByLabel('Parlons connection link').fill(value);
   await home.getByRole('button',{name:'Review connection',exact:true}).click();await home.getByRole('button',{name:'Connect account',exact:true}).click();await home.locator('#dialog').waitFor({state:'hidden'});
  }
  await home.waitForFunction(()=>document.querySelectorAll('.parlons-account').length===2);
  await home.getByText('Same contact name',{exact:true}).first().waitFor();
  assert.equal(await home.getByText('Same contact name',{exact:true}).count(),2);
  await home.screenshot({path:path.join(root,'build/parlons-real-accounts.png')});
  await panel.getByRole('button',{name:'Revoke minimaDocs',exact:true}).click();
  await panel.getByText('Access revoked.',{exact:true}).waitFor();
  await home.evaluate(()=>go('people'));await home.getByText(/Parlons refused access/).waitFor();
  assert.equal(await home.getByRole('button',{name:'My QR code',exact:true}).count(),1);
 }finally{if(app){await app.evaluate(({BrowserWindow})=>BrowserWindow.getAllWindows().forEach(w=>w.destroy()));await app.close();}await host?.close();}
});
