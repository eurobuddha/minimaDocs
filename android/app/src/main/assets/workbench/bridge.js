'use strict';
// Uses ranuts/document's documented embed API and miniPaint's actual project serializer.
(() => {
  const config=JSON.parse(MinimaDocs.bootstrap());
  const frame=document.getElementById('editor');
  const observed=new WeakSet();
  function followInput(win) {
    try {
      if(!observed.has(win.document)) {
        observed.add(win.document);
        for(const type of ['input','keydown','pointerdown'])win.document.addEventListener(type,e=>{if(e.isTrusted)MinimaDocs.used();},true);
      }
      for(let i=0;i<win.frames.length;i++)followInput(win.frames[i]);
    } catch(_) { /* Only bundled frames are observed. */ }
  }
  setInterval(()=>followInput(frame.contentWindow),1000);
  const origin=location.origin;
  let pending=null,opened=false;
  let readyWait=null;
  const error=e=>MinimaDocs.error(e instanceof Error?e.message:String(e));
  const bytes=s=>Uint8Array.from(atob(s),c=>c.charCodeAt(0));
  const base64=blob=>new Promise((resolve,reject)=>{
    if(blob.size>16*1024*1024)return reject(new Error('The edited file exceeds the 16 MiB sharing limit.'));
    const read=new FileReader();read.onerror=()=>reject(new Error('Could not read the edited file.'));
    read.onload=()=>resolve(String(read.result).split(',')[1]);read.readAsDataURL(blob);
  });
  const send=(type,payload={},id='open')=>frame.contentWindow.postMessage({type,payload,id},origin);
  const returned=async blob=>{try{MinimaDocs.saved(await base64(blob));}catch(e){error(e);}finally{pending=null;}};
  // Same readiness flags used by the engine's save-stream.ts. Construction alone
  // does not mean the document and full editing API have finished loading.
  function officeReady() {
    if(readyWait)return;
    const started=Date.now();
    readyWait=setInterval(()=>{
      const editor=frame.contentWindow;
      for(let i=0;i<editor.frames.length;i++) {
        const doc=editor.frames[i].document;
        if(!doc.getElementById('minimadocs-theme')&&doc.head) {
          const style=doc.createElement('link');style.id='minimadocs-theme';style.rel='stylesheet';style.href='/workbench/office.css';doc.head.appendChild(style);
        }
        const api=editor.frames[i].Asc?.editor;
        if(api?.isLoadFullApi&&api.isDocumentLoadComplete) {
          clearInterval(readyWait);readyWait=null;MinimaDocs.ready();return;
        }
      }
      if(Date.now()-started>180000){clearInterval(readyWait);readyWait=null;error('The editor did not finish opening. Close it and try again.');}
    },250);
  }

  window.minimaDocsSave=async () => {
    if(pending)return;
    pending='save-'+Date.now();
    try {
      if(config.kind==='image') {
        if(!opened)throw new Error('The image editor is still opening.');
        await returned(new Blob([frame.contentWindow.FileSave.export_as_json()],{type:'application/json'}));
      } else send('document:save',{targetExt:config.kind.toUpperCase()},pending);
    } catch(e){pending=null;error(e);}
  };

  window.addEventListener('message',async event=>{
    if(event.origin!==origin||event.source!==frame.contentWindow)return;
    const msg=event.data;
    if(!msg||typeof msg!=='object')return;
    if(msg.type==='document:ready'&&!opened) {
      opened=true;
      if(config.base64) {
        send('document:open-buffer',{fileName:config.name,buffer:bytes(config.base64).buffer});
        delete config.base64;
      } else {
        try {await frame.contentWindow.onCreateNew('.'+config.kind);officeReady();}catch(e){error(e);}
      }
    } else if(msg.type==='document:opened') officeReady();
    else if(msg.type==='document:saved'&&msg.id===pending) await returned(msg.payload.file);
    else if(msg.type==='document:error'){pending=null;error(msg.payload?.message||'The editor could not complete that operation.');}
  });

  frame.addEventListener('load',async()=>{
    if(config.kind!=='image')return;
    try {
      const editor=frame.contentWindow;
      if(!editor.FileSave||!editor.FileOpen)throw new Error('The bundled image editor did not load.');
      const style=editor.document.createElement('link');style.rel='stylesheet';style.href='/workbench/image.css';editor.document.head.appendChild(style);
      if(config.base64) {
        if(config.name.toLowerCase().endsWith('.minimadocs-image.json')) {
          const original=editor.State.do_action;let loaded=false;
          editor.State.do_action=async function(action,...args) {
            const result=await original.call(this,action,...args);
            if(action.action_id==='open_json_file')loaded=result.status==='completed';
            return result;
          };
          try{await editor.FileOpen.load_json(new TextDecoder().decode(bytes(config.base64)));}
          finally{editor.State.do_action=original;}
          if(!loaded)throw new Error('The image project could not be opened.');
        }
        else {
          const blob=new Blob([bytes(config.base64)]);const url=URL.createObjectURL(blob);
          try {
            // Reuse miniPaint's import action and wait for its layer insertion.
            await new Promise((resolve,reject)=>{
              const original=editor.State.do_action;
              const timer=setTimeout(()=>{editor.State.do_action=original;reject(new Error('The image could not be opened.'));},30000);
              editor.State.do_action=async function(action,...args) {
                const result=await original.call(this,action,...args);
                if(action.action_id==='open_file_data_url') {
                  clearTimeout(timer);editor.State.do_action=original;
                  if(result.status==='completed')resolve();else reject(new Error('The image could not be opened.'));
                }
                return result;
              };
              editor.FileOpen.file_open_data_url_handler(url);
            });
          } finally {URL.revokeObjectURL(url);}
        }
        delete config.base64;
      }
      opened=true;MinimaDocs.ready();
    } catch(e){error(e);}
  });
  frame.src=config.kind==='image'?'/image/index.html':'/editor.html?embed=1&embedOrigin='+encodeURIComponent(origin);
})();
