'use strict';
// Uses the existing Atelier components and Pairing review flow. No credentials,
// arbitrary requests or host owner-session cookies cross into this renderer.
let parlonsPageEpoch=0,parlonsTimers=[];
function stopParlonsPolling(){parlonsPageEpoch++;parlonsTimers.forEach(clearInterval);parlonsTimers=[];}
async function parlonsPeople(root){
 stopParlonsPolling();
 const epoch=++parlonsPageEpoch,section=element('section','','parlons-section');root.append(section);
 const heading=element('div','','parlons-heading');heading.append(element('div'),button('Connect Parlons',connectParlons));
 heading.firstChild.append(element('span','YOUR ADDRESS BOOKS','eyebrow'),element('h2','People you already know.'));
 section.append(heading,element('p','Use contacts from Parlons Desktop or the Parlons included in minimaCore Desktop. Each account stays separate.','parlons-caption'));
 let data;try{data=await call('parlonsList');}catch(e){section.append(element('p',e.message,'dialog-state'));return;}
 if(epoch!==parlonsPageEpoch||page!=='people')return;
 if(!data.connections.length){section.append(element('p','Connect an account to find your contacts and receive minimaDocs invitations here.','parlons-caption'));return;}
 for(const c of data.connections){
  const card=element('div','','parlons-account'),top=element('div','','parlons-heading'),names=element('div');
  names.append(element('strong',c.name),element('small',c.host));top.append(names,button('Manage connection',()=>manageParlons(c)));card.append(top);
  const content=element('div');card.append(content);section.append(card);
  let loading=false,lastSnapshot='';
  const load=async(background=false)=>{
   if(loading)return;loading=true;
   const previousSearch=content.querySelector('input'),query=previousSearch?.value||'',focused=document.activeElement===previousSearch;
   if(!background)content.replaceChildren(element('p','Refreshing contacts…','parlons-caption'));
   try{const book=await call('parlonsSnapshot',{connection:c.id});if(epoch!==parlonsPageEpoch||page!=='people')return;
    const serialized=JSON.stringify(book);if(background&&serialized===lastSnapshot)return;lastSnapshot=serialized;
    content.replaceChildren();
    if(book.invitations.length){content.append(element('h3','Invitations for you'));for(const invite of book.invitations){
     const from=book.contacts.find(p=>p.key===invite.from)?.name||'Parlons contact';
     content.append(button('Review invitation from '+from,()=>reviewParlons(c,invite,from),'choice'));
    }}
    const controls=element('div','','parlons-controls'),search=element('input');search.type='search';search.value=query;search.placeholder='Find a contact';search.setAttribute('aria-label','Search '+c.name+' in '+c.host);
    controls.append(search,button('Refresh',()=>load()),button('Add contact',()=>addParlonsContact(c)));content.append(controls);
    const rows=element('div');content.append(rows);
    const render=()=>{rows.replaceChildren();const q=search.value.trim().toLowerCase();const matches=book.contacts.filter(p=>p.name.toLowerCase().includes(q)||p.key.includes(q));
     for(const person of matches){const row=element('div','','person'),name=element('div');name.append(element('strong',person.name),element('small',c.host+' · '+c.name));
      row.append(element('div',person.name.slice(0,1).toUpperCase(),'person-initial'),name,button('Connect in minimaDocs',()=>sendParlons(c,person)),button('Manage',()=>manageParlonsContact(c,person),'text-button'));rows.append(row);}
     if(!matches.length)rows.append(element('p',q?'No matching contacts.':'No contacts in this Parlons account yet.','parlons-caption'));
    };search.oninput=render;render();if(focused&&!dialog.open)search.focus();
   }catch(e){if(epoch!==parlonsPageEpoch||page!=='people')return;lastSnapshot='';content.replaceChildren(element('p',e.message,'dialog-state'),button('Retry',()=>load()));}
   finally{loading=false;}
  };await load();
  if(epoch===parlonsPageEpoch&&page==='people')parlonsTimers.push(setInterval(()=>{if(!dialog.open&&document.visibilityState==='visible')load(true);},15000));
 }
}
function connectParlons(){
 sheet('Connect your Parlons account','CONTACTS & INVITATIONS');
 body.append(element('p','In Parlons Desktop, or in minimaCore → Parlons, open Connected apps and choose Connect minimaDocs. Paste the connection link below.'));
 const input=element('textarea');input.placeholder='minimadocs://parlons/v1?…';input.setAttribute('aria-label','Parlons connection link');input.spellcheck=false;body.append(input);
 body.append(button('Review connection',async()=>{
  const link=input.value.trim(),info=await call('parlonsLink',{link});sheet('Connect '+info.host+'?','REVIEW CONNECTION');
  body.append(element('p','minimaDocs will read and manage this account’s contacts, and send and receive minimaDocs invitations. Documents continue to use their own encrypted sharing connection.'),element('p','Account fingerprint · '+info.account.slice(0,16),'parlons-caption'),element('p','You can revoke this connection in either app.'),
   button('Connect account',async()=>{await call('parlonsConnect',{link});close();notice('Parlons account connected');await go('people');},'primary'));
 },'primary'));input.focus();
}
async function chooseParlons(id){
 const epoch=sheet('Choose a Parlons account','SHARE THROUGH PARLONS'),data=await call('parlonsList');if(epoch!==dialogEpoch)return;
 body.append(element('p','Choose the account whose contacts you want to use.'));
 for(const c of data.connections){const b=button(c.name,()=>chooseParlonsContact(c,id),'choice');b.append(element('small',c.host));body.append(b);}
 body.append(button('Connect another Parlons account',connectParlons,'text-button'));
}
async function chooseParlonsContact(c,id){
 const epoch=sheet('Share with a contact',c.host),book=await call('parlonsSnapshot',{connection:c.id});if(epoch!==dialogEpoch)return;
 body.append(element('p',c.name));const search=element('input');search.type='search';search.placeholder='Find a contact';search.setAttribute('aria-label','Find a Parlons contact');body.append(search);const rows=element('div');body.append(rows);
 const render=()=>{rows.replaceChildren();const q=search.value.trim().toLowerCase(),matches=book.contacts.filter(p=>p.name.toLowerCase().includes(q)||p.key.includes(q));for(const p of matches)rows.append(button(p.name,()=>sendParlons(c,p,id),'choice'));if(!matches.length)rows.append(element('p','No matching contacts in this account.'));};search.oninput=render;render();search.focus();
}
function sendParlons(c,person,id=''){
 sheet('Invite '+person.name,'PARLONS · '+c.name);body.append(element('p',id?(documents.find(d=>d.id===id)?.title||'Your document'):'Connect your minimaDocs devices.'),element('p',c.host+' · '+c.name));
 const level=id?levelSelect():null;
 body.append(element('p','They need minimaDocs connected to their Parlons account. They choose whether to accept. The invitation expires after 15 minutes.'),button('Send invitation',async()=>{
  const r=await call('parlonsSend',{connection:c.id,to:person.key,id,level:level?.value||'READ'});close();notice(r.state==='queued'?'Invitation queued for '+person.name:'Invitation sent through Parlons. Recipient confirmation is still pending.');
 },'primary'));
}
async function reviewParlons(c,invite,from){
 const epoch=sheet('Invitation from '+from,c.host),preview=await call('preview',{text:invite.line});if(epoch!==dialogEpoch)return;
 body.append(element('p',preview.offer||'Connect minimaDocs devices.'),element('p','minimaDocs identity · '+preview.name),element('p',preview.level==='WRITE'?'You can edit.':'You can view.'),element('p','Verification digits · '+preview.code,'warning'));
 body.append(button('Accept invitation',async()=>{
  await call('accept',{text:invite.line});
  try{await call('parlonsDismiss',{connection:c.id,id:invite.id});notice('Invitation accepted. Waiting for the other device.');}
  catch(_){notice('Invitation accepted. Parlons could not clear its inbox entry; you can dismiss it later.');}
  close();await go('shared');
 },'primary'),button('Dismiss invitation',async()=>{await call('parlonsDismiss',{connection:c.id,id:invite.id});close();await go('people');},'text-button'));
}
function addParlonsContact(c){
 sheet('Add a contact to Parlons',c.host);body.append(element('p','This contact will be added to '+c.name+' in Parlons.'));
 const input=element('input');input.placeholder='Parlons contact address';input.setAttribute('aria-label','Parlons contact address');body.append(input,button('Add contact',async()=>{await call('parlonsAdd',{connection:c.id,address:input.value.trim()});close();notice('Introduction sent. The contact appears when they answer.');await go('people');},'primary'));
}
function manageParlonsContact(c,p){
 sheet(p.name,c.host+' · '+c.name);body.append(button('Copy Parlons address',async()=>{await call('clipboard',{text:p.address});notice('Parlons address copied');},'choice'),button('Remove from Parlons',()=>{
  sheet('Remove '+p.name+' from Parlons?','CONTACTS');body.append(element('p','This removes the contact from '+c.name+' for its linked apps. Their existing minimaDocs document access is managed separately in Share → Access & updates.'),button('Remove contact',async()=>{await call('parlonsRemove',{connection:c.id,key:p.key});close();await go('people');},'primary'));
 },'text-button'));
}
function manageParlons(c){
 sheet(c.name,c.host);body.append(element('p','Disconnecting revokes minimaDocs’ access to this Parlons account. Existing shared documents stay in minimaDocs.'),button('Disconnect account',async()=>{await call('parlonsDisconnect',{connection:c.id});close();notice('Parlons connection revoked');await go('people');},'primary'),button('Forget an unavailable connection',()=>{
  sheet('Forget this connection on this Mac?','LOCAL CONNECTION');body.append(element('p','This removes the saved credential from minimaDocs. It cannot revoke access in an unavailable Parlons host. Revoke minimaDocs in that host’s Connected apps when you reopen it.'),button('Forget on this Mac',async()=>{await call('parlonsForget',{connection:c.id});close();await go('people');},'primary'));
 },'text-button'));
}
