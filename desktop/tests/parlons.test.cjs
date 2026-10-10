'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs/promises'),path=require('node:path'),os=require('node:os'),http=require('node:http'),crypto=require('node:crypto');
const {ParlonsClient,parseLink,request,contacts,fingerprint}=require('../main/parlons.cjs');
const PUBLIC='0x'+'ab'.repeat(32),ACCOUNT=fingerprint(PUBLIC),TOKEN='cd'.repeat(32),CODE='ef'.repeat(32);
const link=(provider='desktop',port=9588)=>`minimadocs://parlons/v1?port=${port}&provider=${provider}&account=${ACCOUNT}&code=${CODE}`;
const base={provider:'desktop',account:ACCOUNT,token:TOKEN,port:9588};
const envelope=(c,extra={})=>({ok:true,provider:c.provider,account:c.account,...extra});
async function fixture(t,override){
 const directory=await fs.mkdtemp(path.join(os.tmpdir(),'minimadocs-parlons-')),key=crypto.randomBytes(32),calls=[];
 const transport=async(c,op,p)=>{calls.push({c,op,p});if(override){const result=await override(c,op,p);if(result!==undefined)return result;}
  if(op==='connect')return envelope(c,{name:'My account',token:TOKEN,publicKey:PUBLIC});
  if(op==='contacts')return envelope(c,{contacts:[{key:PUBLIC,name:'Alice',address:'Mx123@host:9001'}]});
  if(op==='invitations')return envelope(c,{invitations:[{id:'12'.repeat(32),from:PUBLIC,line:'synthetic-pairing-line'}]});
  return envelope(c,{state:'queued'});
 };
 const client=new ParlonsClient({directory,key,transport});await client.load();
 t.after(async()=>{await client.close();await fs.rm(directory,{recursive:true,force:true});});
 return {client,directory,key,calls,transport};
}
test('connection links accept only the exact local protocol and unique fields',()=>{
 assert.deepEqual(parseLink(link()),{port:9588,provider:'desktop',account:ACCOUNT,code:CODE});
});
test('invalid connection targets cannot become network requests',()=>{
 for(const value of [link().replace('minimadocs:','http:'),link()+'&host=evil.test',link()+'&port=123',link().replace('parlons/v1','parlons:9000/v1'),link().replace('parlons/v1','user@parlons/v1'),link()+'#extra',link('unknown'),link('desktop',0),link('desktop',65536),link().replace(CODE,'z'.repeat(64)),link().replace('port=9588','port=9e3')])assert.throws(()=>parseLink(value));
 assert.equal(parseLink(link('core')).provider,'core');
});
test('contact validation matches Android key constraints, deduplication and normalization',()=>{
 const other='0x'+'cd'.repeat(32),list=contacts([{key:PUBLIC,name:' Zoe\n',address:''},{key:other,name:'Alice',address:'Mx1@host:9'},{key:PUBLIC.toUpperCase().replace('0X','0x'),name:'duplicate',address:''}]);
 assert.equal(list.length,2);assert.equal(list[0].name,'Alice');assert.equal(list[1].name,'Zoe');
 for(const bad of [null,{},[{key:'0x../path',name:'Eve',address:''}],[{key:PUBLIC+'a',name:'Eve',address:''}],[{key:PUBLIC,name:'a'.repeat(1025),address:''}]])assert.throws(()=>contacts(bad));
});
test('two hosts using the same identity remain separate; credentials never appear in metadata',async t=>{
 const {client}=await fixture(t);const desktop=await client.connect(link()),core=await client.connect(link('core'));
 assert.notEqual(desktop.id,core.id);assert.equal(client.list().length,2);
 for(const info of client.list()){assert.equal(info.token,undefined);assert.equal(info.port,undefined);assert.equal(info.code,undefined);}
 const snapshot=await client.snapshot(core.id);assert.equal(snapshot.host,'minimaCore Desktop');assert.equal(snapshot.contacts[0].name,'Alice');assert.equal(snapshot.token,undefined);
});
test('encrypted credentials survive restart and reject a different workspace key',async t=>{
 const {client,directory,key,transport}=await fixture(t);const c=await client.connect(link());
 const stored=await fs.readFile(path.join(directory,'parlons-connections.protected'));
 assert.equal(stored.includes(Buffer.from(TOKEN)),false);assert.equal(stored.includes(Buffer.from('My account')),false);
 assert.equal((await fs.stat(client.file)).mode&0o777,0o600);
 const reopened=new ParlonsClient({directory,key,transport});await reopened.load();assert.deepEqual(reopened.list(),[c]);await reopened.snapshot(c.id);await reopened.close();
 const wrong=new ParlonsClient({directory,key:crypto.randomBytes(32),transport});await assert.rejects(wrong.load(),/could not be opened/);await wrong.close();
});
test('tampered credential file fails closed without changing the file',async t=>{
 const {client,directory,key,transport}=await fixture(t);await client.connect(link());
 const bytes=await fs.readFile(client.file);bytes[bytes.length-1]^=1;await fs.writeFile(client.file,bytes);
 const reopened=new ParlonsClient({directory,key,transport});await assert.rejects(reopened.load(),/could not be opened/);assert.deepEqual(await fs.readFile(client.file),bytes);await reopened.close();
});
test('connection redemption verifies the account public-key fingerprint',async t=>{
 const {client}=await fixture(t,(c,op)=>op==='connect'?envelope(c,{token:TOKEN,name:'Impostor',publicKey:'0x'+'11'.repeat(32)}):undefined);
 await assert.rejects(client.connect(link()),/invalid/);assert.equal(client.list().length,0);
});
test('concurrent additions persist both accounts',async t=>{
 const {client,directory,key,transport}=await fixture(t);await Promise.all([client.connect(link()),client.connect(link('core'))]);
 const reopened=new ParlonsClient({directory,key,transport});await reopened.load();assert.equal(reopened.list().length,2);await reopened.close();
});
test('failed remote revocation keeps the credential until explicit local forget',async t=>{
 const {client,calls}=await fixture(t,(_c,op)=>{if(op==='disconnect')throw Error('offline');});const c=await client.connect(link());
 await assert.rejects(client.disconnect(c.id),/offline/);assert.equal(client.list().length,1);
 await client.disconnect(c.id,true);assert.equal(client.list().length,0);assert.equal(calls.filter(x=>x.op==='disconnect').length,1);
});
test('a snapshot arriving after disconnect cannot expose stale contacts',async t=>{
 let finish,start;const began=new Promise(resolve=>start=resolve),pending=new Promise(resolve=>finish=resolve);
 const {client}=await fixture(t,async(c,op)=>{if(op==='contacts'){start();await pending;return envelope(c,{contacts:[]});}});
 const c=await client.connect(link()),snapshot=client.snapshot(c.id);const rejected=assert.rejects(snapshot,/connection changed/);
 await began;await client.disconnect(c.id);finish();await rejected;
});
test('malformed or oversized inboxes fail before reaching the renderer',async t=>{
 const {client}=await fixture(t,(c,op)=>op==='invitations'?envelope(c,{invitations:Array(65).fill({})}):undefined);
 const c=await client.connect(link());await assert.rejects(client.snapshot(c.id),/unreadable inbox/);
});
test('invites send once on a selected account with no application or RPC override',async t=>{
 const {client,calls}=await fixture(t);const c=await client.connect(link('core'));assert.deepEqual(await client.send(c.id,PUBLIC,'synthetic-line'),{state:'queued'});
 const sent=calls.filter(x=>x.op==='invite');assert.equal(sent.length,1);assert.equal(sent[0].c.provider,'core');
 assert.deepEqual(Object.keys(sent[0].p).sort(),['line','requestId','to']);assert.match(sent[0].p.requestId,/^[a-f0-9-]{36}$/);
 await assert.rejects(client.send(c.id,'not-a-key','line'));await assert.rejects(client.send(c.id,PUBLIC,'x'.repeat(16385)));
});
async function serverFixture(t,handler){
 const server=http.createServer(handler);await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 t.after(()=>new Promise(resolve=>{server.closeAllConnections();server.close(resolve);}));return {...base,port:server.address().port};
}
test('HTTP uses the fixed loopback route, JSON body and Bearer header',async t=>{
 const c=await serverFixture(t,async(req,res)=>{const chunks=[];for await(const part of req)chunks.push(part);assert.equal(req.url,'/minimadocs/v1/contacts');assert.equal(req.method,'POST');assert.equal(req.headers.authorization,'Bearer '+TOKEN);assert.equal(req.headers.origin,undefined);assert.equal(Buffer.concat(chunks).toString(),'{}');res.writeHead(200,{'Content-Type':'application/json'});res.end(JSON.stringify(envelope(base,{contacts:[]})));});
 assert.deepEqual((await request(c,'contacts')).contacts,[]);
});
test('redeeming a code does not send a bearer token',async t=>{
 const c=await serverFixture(t,(_req,res)=>{assert.equal(_req.headers.authorization,undefined);res.writeHead(200,{'Content-Type':'application/json'});res.end(JSON.stringify(envelope(base,{token:TOKEN})));});
 assert.equal((await request(c,'connect',{code:CODE})).token,TOKEN);
});
test('HTTP rejects wrong identity, redirects, revoked access and non-JSON replies',async t=>{
 let mode='identity';const c=await serverFixture(t,(_req,res)=>{
  if(mode==='redirect'){res.writeHead(302,{Location:'http://example.invalid/'});return res.end();}
  if(mode==='revoked'){res.writeHead(403);return res.end('secret response must not escape');}
  if(mode==='html'){res.writeHead(200,{'Content-Type':'text/html'});return res.end('<h1>secret</h1>');}
  res.writeHead(200,{'Content-Type':'application/json'});res.end(JSON.stringify(envelope(base,{account:'00'.repeat(32)})));
 });
 await assert.rejects(request(c,'contacts'),/different Parlons account/);mode='redirect';await assert.rejects(request(c,'contacts'),/HTTP 302/);mode='revoked';await assert.rejects(request(c,'contacts'),/refused access/);mode='html';await assert.rejects(request(c,'contacts'),/unreadable response/);
});
test('HTTP enforces response bounds and an absolute request deadline',async t=>{
 let mode='large';const c=await serverFixture(t,(_req,res)=>{res.writeHead(200,{'Content-Type':'application/json'});if(mode==='large')res.end('x'.repeat(262145));else res.write('{');});
 await assert.rejects(request(c,'contacts'),/too large/);mode='stall';await assert.rejects(request(c,'contacts',{},50),/did not answer/);
});
test('unknown operations are refused before network access',async()=>{await assert.rejects(request(base,'seed.reveal'),/Unknown/);});
