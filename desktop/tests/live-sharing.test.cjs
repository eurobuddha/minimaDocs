// Opt-in live test. Two disposable profiles use the production pipe bridge and
// backend; only the repository's synthetic Office fixture leaves this machine.
const {test}=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs/promises'),path=require('node:path'),crypto=require('node:crypto');
const {Backend}=require('../main/backend.cjs'),root=path.resolve(__dirname,'..');
async function until(action,description,timeout=120000){
 const end=Date.now()+timeout;let last;
 while(Date.now()<end){try{const result=await action();if(result)return result;}catch(e){last=e;}
  await new Promise(resolve=>setTimeout(resolve,1000));
 }
 throw new Error(description+(last?': '+last.message:''));
}
test('two Mac backends exchange a DOCX and editable updates over live Maxima',{timeout:300000},async()=>{
 const profiles=await fs.mkdtemp(path.join(root,'build/live-sharing-')),peers=[];
 try{
  for(const name of ['Mac sharing test A','Mac sharing test B']){
   const directory=path.join(profiles,String(peers.length));await fs.mkdir(directory);
   const peer=new Backend(path.join(root,'build/runtime/bin/java'),path.join(root,'backend/build/package-input'),directory);peers.push(peer);
   await peer.call('init',{key:crypto.randomBytes(32).toString('base64')});await peer.call('name',{name});
  }
  const [a,b]=peers;
  await Promise.all(peers.map(peer=>until(async()=>{const s=await peer.call('list');return s.connection==='Maxima connected';},'Relay connection did not finish')));
  const bytes=await fs.readFile(path.resolve(root,'../android/build/fixtures/compatibility.docx'));
  const opened=await a.call('open',{kind:'docx',title:'Live synthetic document'});
  const saved=await a.call('save',{session:opened.session,title:'Live synthetic document',base64:bytes.toString('base64')});
  const invitation=await a.call('invitation',{id:saved.id,level:'WRITE'});
  assert.ok(invitation.link.startsWith('minimadocs://pair/'),'Invitations must use the minimaDocs app link');
  const decoded=await b.call('qrRead',{base64:invitation.qr.split(',')[1]});assert.equal(decoded.text,invitation.link);
  const review=await b.call('preview',{text:decoded.text});assert.equal(review.name,'Mac sharing test A');assert.equal(review.level,'WRITE');
  await b.call('accept',{text:invitation.link});
  const received=await until(()=>b.call('open',{id:saved.id}),'Recipient did not receive the document');
  assert.equal(received.readonly,false);assert.equal(received.base64,bytes.toString('base64'));
  console.log('Verified QR acceptance and exact DOCX delivery to the second Mac backend');
  await b.call('save',{session:received.session,title:'Returned synthetic document',base64:received.base64});
  await until(async()=>{const data=await a.call('list');return data.documents.some(d=>d.id===saved.id&&d.title==='Returned synthetic document');},'Edited document did not return');
  const access=await a.call('access',{id:saved.id});assert.equal(access.people.length,1);assert.equal(access.people[0].level,'WRITE');
  await a.call('manage',{id:saved.id,address:access.people[0].address,level:'READ'});
  await until(async()=>{const data=await b.call('list');return data.documents.some(d=>d.id===saved.id&&d.readonly);},'Read-only permission did not arrive');
  await assert.rejects(b.call('save',{session:received.session,title:'Must not overwrite',base64:received.base64}));
  console.log('Verified updates return and remote Can view access prevents further writes');
 }finally{await Promise.all(peers.map(peer=>peer.close()));}
});
