'use strict';
(()=>{
 const config=JSON.parse(MinimaDocs.bootstrap()),frame=document.getElementById('editor');
 let editor,opened=false,busy=false;
 const error=e=>MinimaDocs.error(e?.message||String(e));
 const decode=s=>Uint8Array.from(atob(s),c=>c.charCodeAt(0));
 // FileReader avoids spreading multi-MiB buffers onto the JavaScript stack.
 const encode=bytes=>new Promise((resolve,reject)=>{
  if(!bytes.length||bytes.length>16*1024*1024)return reject(Error('The edited file must be between 1 byte and 16 MiB.'));
  const reader=new FileReader();reader.onerror=()=>reject(Error('The edited file could not be read.'));
  reader.onload=()=>resolve(String(reader.result).split(',')[1]);reader.readAsDataURL(new Blob([bytes]));
 });
 async function save(type){
  if(busy)throw Error('PhotoCraft is finishing a save or export. Try again in a moment.');busy=true;
  try{
   if(!opened)throw Error('PhotoCraft is still opening.');
   const result=await editor.request('snapshot',{format:(type||'pcraft').toLowerCase()});
   const base64=await encode(result.bytes);
   if(type){await MinimaDocs.exported(base64,type);if(result.value?.warnings?.length)await desktopEditor.action('exportNotes',result.value.warnings.join(' · '));}
   else{const saved=await MinimaDocs.saved(base64);if(saved?.ok)await editor.request('ack');}
  }catch(e){error(e);}finally{busy=false;}
 }
 window.minimaDocsSave=()=>save();window.minimaDocsExport=type=>save(type);
 window.minimaDocsWritable=()=>{config.readonly=false;frame.contentWindow.document.body.inert=false;};
 frame.addEventListener('load',async()=>{
  try{
   editor=frame.contentWindow.photoCraft;if(!editor)throw Error('The bundled PhotoCraft bridge did not load.');
   editor.onEvent=(kind,value,bytes)=>{
    if(kind==='changed'&&opened&&!config.readonly)MinimaDocs.changed();
    else if(kind==='error')error(value.message);
    else if(kind==='action')desktopEditor.action(value.action).catch(error);
    else if(kind==='download'){
     encode(bytes).then(data=>desktopEditor.exportPhotoCraft(data,value.name)).then(result=>value.token?editor.request('exportAck',{token:value.token,saved:result.saved}):null).catch(e=>{
      if(value.token)editor.request('exportAck',{token:value.token,saved:false,error:e.message}).catch(()=>{});error(e);
     });
    }
   };
   await Promise.race([editor.ready,new Promise((_,reject)=>setTimeout(()=>reject(Error('PhotoCraft could not start. Close this window and try again.')),90000))]);
   let name=config.name||'Untitled.pcraft',data=config.base64?decode(config.base64):new Uint8Array(),notes=[];
   if(config.legacy&&config.base64){
    // Older miniPaint projects open in PhotoCraft; the first save adds a .pcraft version.
    await new Promise((resolve,reject)=>{const s=document.createElement('script');s.src='/desktop/legacy-image.js';s.onload=resolve;s.onerror=()=>reject(Error('The image converter could not load.'));document.head.append(s);});
    ({bytes:data,notes}=await legacyImageToOra(new TextDecoder().decode(data)));name=name.replace(/\.minimadocs-image\.json$/i,'')+'.ora';
   }
   await editor.request('open',{name},data);
   delete config.base64;frame.contentWindow.document.body.inert=!!config.readonly;
   opened=true;MinimaDocs.ready();
   if(config.legacy&&!config.readonly)desktopEditor.action('converted',notes.join(' ')).catch(error);
  }catch(e){error(e);}
 });
 frame.title='PhotoCraft image editor';frame.src='/photocraft/index.html';
})();
