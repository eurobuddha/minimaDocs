'use strict';
// Client-side UI contract test. This fixture is not a Parlons host implementation
// and does not replace the cross-application release checks in the specification.
const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs/promises'),path=require('node:path'),http=require('node:http');
const {_electron:electron}=require('playwright-core');
const {fingerprint}=require('../main/parlons.cjs');
const root=path.resolve(__dirname,'..'),key='0x'+'ab'.repeat(32),account=fingerprint(key),code='ef'.repeat(32),token='cd'.repeat(32);
async function host(provider){
 let approved=false,rows=[{key,name:provider==='core'?'Core Alice':'Desktop Alice',address:'Mx12345678@host:9001'}];
 const server=http.createServer(async(req,res)=>{
  const chunks=[];for await(const chunk of req)chunks.push(chunk);const body=JSON.parse(Buffer.concat(chunks));
  const op=req.url.split('/v1/')[1],envelope={ok:true,provider,account};
  if(op==='connect'&&body.code===code){approved=true;Object.assign(envelope,{name:provider==='core'?'Core account':'Desktop account',publicKey:key,token});}
  else if(!approved||req.headers.authorization!=='Bearer '+token){res.writeHead(403,{'Content-Type':'application/json'});return res.end('{"ok":false}');}
  else if(op==='contacts')envelope.contacts=rows;
  else if(op==='invitations')envelope.invitations=[];
  else if(op==='disconnect')approved=false;
  else if(op==='contact/remove'){rows=rows.filter(c=>c.key!==body.key);envelope.removed=true;}
  else if(op==='invite')envelope.state='queued';
  else {res.writeHead(400);return res.end();}
  res.writeHead(200,{'Content-Type':'application/json'});res.end(JSON.stringify(envelope));
 });
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 return {link:`minimadocs://parlons/v1?port=${server.address().port}&provider=${provider}&account=${account}&code=${code}`,
  close:()=>new Promise(resolve=>{server.closeAllConnections();server.close(resolve);})};
}
test('People connects and separates both host types, searches contacts and revokes access',{timeout:120000},async()=>{
 const directory=await fs.mkdtemp(path.join(root,'build/parlons-ui-')),desktop=await host('desktop'),core=await host('core');let app;
 try{
  app=await electron.launch({executablePath:require('electron'),args:[root],env:{...process.env,MINIMADOCS_TEST_DIR:directory},timeout:60000});
  const home=await app.firstWindow();await home.waitForFunction(()=>document.getElementById('connection').textContent==='Offline session');
  await home.locator('[data-page="people"]').click();await home.getByRole('button',{name:'Connect Parlons',exact:true}).waitFor();
  for(const fixture of [desktop,core]){
   await home.getByRole('button',{name:'Connect Parlons',exact:true}).click();
   await home.getByLabel('Parlons connection link').fill(fixture.link);
   await home.getByRole('button',{name:'Review connection',exact:true}).click();
   await home.getByRole('button',{name:'Connect account',exact:true}).click();
   await home.locator('#dialog').waitFor({state:'hidden'});
  }
  await home.getByText('Core Alice',{exact:true}).waitFor();await home.getByText('Desktop Alice',{exact:true}).waitFor();
  assert.equal(await home.locator('.parlons-account').count(),2);
  await home.getByLabel('Search Core account in minimaCore Desktop').fill('absent');
  await home.getByText('No matching contacts.',{exact:true}).waitFor();
  await home.getByText('Desktop Alice',{exact:true}).waitFor();
  await home.getByLabel('Search Core account in minimaCore Desktop').fill('Alice');
  await home.screenshot({path:path.join(root,'build/mac-parlons-contacts.png')});
  const metadata=await home.evaluate(()=>docs.call('parlonsList'));assert.equal(metadata.connections.length,2);assert.equal(JSON.stringify(metadata).includes(token),false);
  assert.equal(await home.getByRole('button',{name:'My QR code',exact:true}).count(),1);
  const card=home.locator('.parlons-account').filter({hasText:'Core account'});
  await card.getByRole('button',{name:'Manage connection',exact:true}).click();
  await home.getByRole('button',{name:'Disconnect account',exact:true}).click();await home.locator('#dialog').waitFor({state:'hidden'});
  await home.waitForFunction(()=>document.querySelectorAll('.parlons-account').length===1);
  await home.getByText('Desktop Alice',{exact:true}).waitFor();
 }finally{
  if(app){await app.evaluate(({BrowserWindow})=>BrowserWindow.getAllWindows().forEach(w=>w.destroy()));await app.close();}
  await desktop.close();await core.close();await fs.rm(directory,{recursive:true,force:true});
 }
});
