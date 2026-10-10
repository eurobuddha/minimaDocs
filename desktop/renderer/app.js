'use strict';
const $=id=>document.getElementById(id),dialog=$('dialog'),body=$('dialog-body');
let page='files',kind='all',documents=[],noticeTimer,refreshing=false,dialogEpoch=0;
function element(tag,text,className){const e=document.createElement(tag);if(text)e.textContent=text;if(className)e.className=className;return e;}
function button(text,action,className='quiet'){const b=element('button',text,className);b.addEventListener('click',async()=>{b.disabled=true;try{await action();}catch(e){showError(e);}finally{b.disabled=false;}});return b;}
function notice(text){$('notice').textContent=text;$('notice').hidden=false;clearTimeout(noticeTimer);noticeTimer=setTimeout(()=>$('notice').hidden=true,6500);}
function showError(e){if(dialog.open){let state=body.querySelector('.dialog-state');if(!state){state=element('p','', 'dialog-state');body.append(state);}state.textContent=e.message;}else notice(e.message);}
function sheet(title,label='MINIMADOCS'){dialogEpoch++;body.replaceChildren(element('h2',title));$('dialog-label').textContent=label;if(!dialog.open)dialog.showModal();return dialogEpoch;}
function close(){dialogEpoch++;dialog.close();}
$('close-dialog').onclick=close;dialog.addEventListener('cancel',()=>{dialogEpoch++;});
const call=(method,params)=>docs.call(method,params);
async function refresh(){if(refreshing)return;refreshing=true;try{const data=await call('list');documents=data.documents;$('file-count').textContent=documents.length;$('connection').textContent=data.connection;$('connection').classList.toggle('online',data.connection==='Maxima connected');if(page!=='people')renderFiles();}catch(e){notice(e.message);}finally{refreshing=false;}}
function renderFiles(){
 const query=$('search').value.trim().toLowerCase(),rows=documents.filter(d=>(page!=='shared'||d.shared)&&(kind==='all'||d.kind===kind)&&d.title.toLowerCase().includes(query));
 $('files').replaceChildren();
 if(!rows.length){const empty=element('div','', 'empty');empty.append(element('h2',query?'No matching files':page==='shared'?'A place for shared work.':'Your first idea belongs here.'),element('p',query?'Try another title or change the file type.':page==='shared'?'Open a Maxima invitation or share one of your files. Shared documents will appear here.':'Create a document, spreadsheet or image above, or import a file from your Mac. Your files stay available offline.'));$('files').append(empty);return;}
 for(const d of rows){const row=element('article','', 'file-row'),mark=element('div',d.kind==='docx'?'D':d.kind==='xlsx'?'S':'I','file-kind '+d.kind),title=button(d.title,()=>openDocument(d),'file-title');
  title.append(element('small',d.versions>1?'Versions need review':d.readonly?'Can view':d.pending?'Updates waiting to send':d.shared?'Shared document':'Saved on this Mac'));
  row.append(mark,title,element('span',d.kind==='docx'?'Document':d.kind==='xlsx'?'Spreadsheet':'Image','file-meta'),element('span',new Date(d.updated).toLocaleDateString(undefined,{day:'numeric',month:'short',year:'numeric'}),'file-meta'),button('Share',()=>sharing(d.id),'row-share'));$('files').append(row);
 }
}
async function openDocument(d){
 const data=await call('versions',{id:d.id});
 if(data.versions.length<2)return call('open',{id:d.id});
 sheet('Choose a version to review','SHARED UPDATES');
 body.append(element('p',data.title),element('p','These edits arrived independently. Each version is kept. Close its editor to review another, or use Save a copy to continue editing separately.'));
 data.versions.forEach((v,i)=>{const b=button('Version '+(i+1)+(v.available?'':' · still arriving'),async()=>{await call('open',{id:d.id,file:v.file});close();},'choice');b.disabled=!v.available;body.append(b);});
}
async function go(next){if(next!=='people')stopParlonsPolling();page=next;document.querySelectorAll('[data-page]').forEach(b=>{if(b.dataset.page===next)b.setAttribute('aria-current','page');else b.removeAttribute('aria-current');});$('breadcrumb').textContent='Workspace / '+next[0].toUpperCase()+next.slice(1);$('heading').textContent=next==='people'?'Better, together.':next==='shared'?'Shared work.':'Your workspace.';$('subtitle').textContent=next==='people'?'Your trusted connections on Maxima.':next==='shared'?'The files you work on with other people.':'Write, calculate, create. Everything starts here.';$('create').hidden=next!=='files';$('library').hidden=next==='people';$('people').hidden=next!=='people';if(next==='people')await people();else await refresh();}
document.querySelectorAll('[data-page]').forEach(b=>b.onclick=()=>go(b.dataset.page).catch(showError));
document.querySelectorAll('[data-kind]').forEach(b=>b.onclick=()=>{kind=b.dataset.kind;document.querySelectorAll('[data-kind]').forEach(x=>x.setAttribute('aria-pressed',String(x===b)));renderFiles();});
document.querySelectorAll('[data-new]').forEach(b=>b.onclick=()=>call('new',{kind:b.dataset.new}).catch(showError));
$('new-document').onclick=()=>call('new',{kind:'docx'}).catch(showError);$('import').onclick=()=>call('import').catch(showError);$('search').oninput=renderFiles;
document.addEventListener('keydown',e=>{if((e.metaKey||e.ctrlKey)&&e.key.toLowerCase()==='k'){e.preventDefault();$('search').focus();}});
async function people(){const data=await call('people'),root=$('people');if(page!=='people')return;root.replaceChildren();await parlonsPeople(root);if(page!=='people')return;root.append(element('h2','Direct minimaDocs connections','direct-heading'));const actions=element('div','', 'people-actions');actions.append(button('My QR code',()=>invitation()),button('Add a person',()=>receive()));root.append(actions);
 for(const person of data.people){const row=element('div','', 'person'),detail=element('div');detail.append(element('strong',person.name),element('small','Verification digits · '+person.code));row.append(element('div',person.name.slice(0,1).toUpperCase(),'person-initial'),detail,button('Copy address',async()=>{await call('clipboard',{text:person.address});notice('Maxima address copied');}));root.append(row);}
 if(!data.people.length)root.append(element('p','Show someone your QR code or open their minimaDocs invitation. They will appear here after pairing.','parlons-caption'));
}
async function sharing(id){const d=documents.find(d=>d.id===id);const epoch=sheet('Share with anyone','MAXIMA SHARING');body.append(element('p',d?.title||'Your document'),element('p','They need minimaDocs. Share a link in any messaging app or show them a QR code.'));
 body.append(button('Parlons contacts',()=>chooseParlons(id),'choice'),button('Link or QR code',()=>invitation(id),'choice'),button('Enter recipient',()=>receive('',id),'choice'),button('Open recipient QR image',async()=>{const r=await call('readQR');if(r.text)await receive(r.text,id);},'choice'),button('Access & updates',()=>access(id),'choice'));
 const data=await call('people');if(!dialog.open||epoch!==dialogEpoch)return;
 if(data.people.length)body.append(element('h3','Previously paired'));
 for(const person of data.people)body.append(button(person.name,()=>grant(id,person),'choice'));
 body.append(button('Send updates now',async()=>{const r=await call('sync',{id});notice(r.message);},'text-button'));
}
async function access(id){
 const epoch=sheet('People with access','ACCESS & UPDATES'),data=await call('access',{id});if(epoch!==dialogEpoch)return;
 const roles={OWNER:'Owner',ADMIN:'Administrator',WRITE:'Can edit',READ:'Can view',GONE:'No access',INHERITED:'Access through a folder'};
 body.append(element('p','You · '+roles[data.mine]));
 for(const person of data.people){const label=person.name+' · '+roles[person.level];body.append(person.manage?button(label+' · Manage',()=>manageAccess(id,person),'choice'):element('p',label));}
 body.append(element('h3','Updates'),element('p',data.pending?'Updates are waiting to send. Your saved copy is available here.':'No queued document notices. File delivery may still be in progress.'),button('Send updates now',async()=>{const r=await call('sync',{id});notice(r.message);}),button('Refresh status',()=>access(id)));
}
function manageAccess(id,person){
 sheet(person.name,'DOCUMENT PERMISSIONS');const level=levelSelect();level.value=person.level==='READ'?'READ':'WRITE';
 body.append(button('Update access',async()=>{const r=await call('manage',{id,address:person.address,level:level.value});notice(r.message);await access(id);},'primary'),button('Remove access',()=>{
  sheet('Remove access for '+person.name+'?','DOCUMENT PERMISSIONS');body.append(element('p','They will stop receiving updates. Copies already received stay on their device.'),button('Remove access',async()=>{const r=await call('manage',{id,address:person.address,level:'GONE'});notice(r.message);await access(id);await refresh();},'primary'),button('Cancel',()=>access(id)));
 },'text-button'));
}
function levelSelect(){const label=element('label','Access','role-label'),select=element('select');for(const [value,text] of [['READ','Can view'],['WRITE','Can edit']]){const option=element('option',text);option.value=value;select.append(option);}select.value='WRITE';label.append(select);body.append(label);return select;}
async function invitation(id){const epoch=sheet(id?'Create an invitation':'Your Maxima QR code','SHARE DIRECTLY');body.append(element('p',id?'Choose what this person can do. Anyone with the invitation can join at this access level for 15 minutes.':'Let the other person scan this in minimaDocs, or send them the invitation link. The pairing code is valid for 15 minutes.'));const level=id?levelSelect():null;
 body.append(button(id?'Create invitation':'Show my QR code',async()=>{const r=await call('invitation',{id:id||'',level:level?.value||'READ'});if(epoch!==dialogEpoch)return;const pic=element('img','', 'qr');pic.src=r.qr;pic.alt='minimaDocs invitation QR code';body.append(pic,button('Copy invitation',async()=>{await call('clipboard',{text:r.link});notice('Invitation copied');},'primary'),element('p','On their device: Shared → Open invitation → scan or paste. Keep minimaDocs open while they connect.'));},'primary'));
}
async function grant(id,person){sheet('Give '+person.name+' access','DOCUMENT PERMISSIONS');const level=levelSelect();body.append(element('p','Can view allows reading and downloading. Can edit allows saved changes to be sent back.'));
 body.append(button('Share document',async()=>{const r=await call('share',{id,address:person.address,level:level.value});close();notice(r.message);await refresh();},'primary'));
}
async function receive(text='',documentId=''){sheet(documentId?'Enter recipient':'Open invitation','CONNECT THROUGH MAXIMA');body.append(element('p',documentId?'Paste their minimaDocs invitation or an already paired Maxima address.':'Paste a minimaDocs invitation to review who sent it and what access it offers.'));
 const input=element('textarea');input.value=text;input.placeholder='Paste invitation or recipient code';input.setAttribute('aria-label','Invitation or recipient');body.append(input);
 const actions=element('div','', 'dialog-actions');actions.append(button('Open QR image',async()=>{const r=await call('readQR');if(r.text)input.value=r.text;}),button('Continue',async()=>{
  const value=input.value.trim();if(documentId&&value.startsWith('Mx')){const data=await call('people'),person=data.people.find(p=>p.address===value);if(!person)throw new Error('Exchange an invitation or QR code first so both devices have encryption keys.');return grant(documentId,person);}
  const preview=await call('preview',{text:value});sheet(documentId?'Pair with '+preview.name+'?':'From '+preview.name,'REVIEW INVITATION');body.append(element('p',documentId?'Pair this recipient, then choose their access to your document.':preview.offer||'An invitation to pair devices.'),element('p',documentId?'':preview.level==='WRITE'?'You can edit this document.':'You can view this document.'),element('p','Verification digits: '+preview.code+'. Compare these in People on both devices after pairing.','warning'));
  body.append(button(documentId?'Pair and choose access':'Accept invitation',async()=>{const result=await call('accept',{text:value,recipient:!!documentId});if(documentId)await grant(documentId,{name:preview.name,address:result.address});else{close();notice(result.message);await go('shared');}},'primary'));
 },'primary'));body.append(actions);input.focus();}
$('open-invitation').onclick=()=>receive();
$('settings').onclick=async()=>{try{const data=await call('people');sheet('Make yourself at home.','SETTINGS');body.append(element('p','Your name is shown to people you pair with.'));const input=element('input');input.value=data.name;input.maxLength=80;input.setAttribute('aria-label','Your name');body.append(input,button('Save name',async()=>{await call('name',{name:input.value});close();notice('Your name is saved');},'primary'),element('h3','Privacy & storage'),element('p','Documents, attachments and device keys are encrypted on this Mac. The macOS Keychain protects the key that opens them. Export copies of important files before moving to another Mac.'),element('h3','Sharing'),element('p','minimaDocs runs its own lightweight Maxima messaging node. Keep the app open to receive updates. Saved document versions are shared; simultaneous edits are kept for review.'),element('p','Editing powered by ONLYOFFICE (AGPL-3.0), ranuts/document and miniPaint (MIT).'));}catch(e){showError(e);}};
docs.onRefresh(refresh);docs.onShare(async id=>{await refresh();sharing(id).catch(showError);});docs.onInvitation(text=>receive(text));
call('version').then(v=>$('version').textContent=v.version).catch(()=>{});refresh();setInterval(refresh,15000);
