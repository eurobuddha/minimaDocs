'use strict';
// Uses ranuts/document's documented embed API and miniPaint's actual project serializer.
(() => {
  const config=JSON.parse(MinimaDocs.bootstrap());
  const freshDocument=!config.base64;
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
  let pending=null,opened=false,saveTimeout=null,sequence=0,converting=false;
  let readyWait=null;
  const error=e=>MinimaDocs.error(e instanceof Error?e.message:String(e));
  const bytes=s=>Uint8Array.from(atob(s),c=>c.charCodeAt(0));
  const base64=blob=>new Promise((resolve,reject)=>{
    if(blob.size>16*1024*1024)return reject(new Error('The edited file exceeds the 16 MiB sharing limit.'));
    const read=new FileReader();read.onerror=()=>reject(new Error('Could not read the edited file.'));
    read.onload=()=>resolve(String(read.result).split(',')[1]);read.readAsDataURL(blob);
  });
  const send=(type,payload={},id='open')=>frame.contentWindow.postMessage({type,payload,id},origin);
  const returned=async (blob,request)=>{
    if(pending!==request||converting)return;converting=true;
    try{
      const encoded=await base64(blob);if(pending!==request)return;
      if(request.exportType)MinimaDocs.exported(encoded,request.exportType);else MinimaDocs.saved(encoded);
    }catch(e){if(pending===request)error(e);}
    finally{if(pending===request){pending=null;converting=false;clearTimeout(saveTimeout);}}
  };
  window.minimaDocsWritable=()=>{
    config.readonly=false;
    if(config.kind==='image')frame.contentWindow.document.body.inert=false;
    else send('document:set-readonly',{readonly:false},'writable');
  };
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
          clearInterval(readyWait);readyWait=null;
          if(config.kind==='docx') {
            if(freshDocument) {
              api.put_TextPrFontName?.('Manrope');api.put_TextPrFontSize?.(12);
              if(config.seedText) {
                const escaped=config.seedText.replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
                api.pluginMethod_PasteHtml('<p style="font-family:Manrope;font-size:12pt">'+escaped.replace(/\r\n|\r|\n/g,'<br>')+'</p>');delete config.seedText;
              }
            }
            window.installWordUi?.(editor.frames[i],config);
          }
          MinimaDocs.ready();return;
        }
      }
      if(Date.now()-started>180000){clearInterval(readyWait);readyWait=null;error('The editor did not finish opening. Close it and try again.');}
    },250);
  }

  window.minimaDocsSave=()=>save();
  window.minimaDocsExport=type=>save(type);
  async function save(exportType='') {
    if(pending)return;
    if(exportType&&!['PDF','PNG'].includes(exportType))return error('Unsupported export format.');
    const request=pending={id:'save-'+(++sequence),exportType};converting=false;
    saveTimeout=setTimeout(()=>{if(pending!==request)return;pending=null;converting=false;error('Saving timed out. Your previous saved version is safe. Try Save again.');},180000);
    try {
      if(config.kind==='image') {
        if(!opened)throw new Error('The image editor is still opening.');
        const editor=frame.contentWindow;
        const project=editor.FileSave.export_as_json();
        if(exportType==='PNG') {
          const info=JSON.parse(project).info;
          if(!(info.width>0&&info.height>0&&info.width*info.height<=40000000))throw new Error('PNG export supports up to 40 million pixels.');
          const canvas=editor.document.createElement('canvas');canvas.width=info.width;canvas.height=info.height;
          const ctx=canvas.getContext('2d');editor.FileSave.disable_canvas_smooth(ctx);
          editor.FileSave.Base_layers.convert_layers_to_canvas(ctx,null,false);
          const blob=await new Promise(resolve=>canvas.toBlob(resolve,'image/png'));
          if(!blob)throw new Error('Could not export this image.');await returned(blob,request);
        }else await returned(new Blob([project],{type:'application/json'}),request);
      } else send('document:save',{targetExt:exportType||config.kind.toUpperCase()},request.id);
    } catch(e){if(pending===request){pending=null;converting=false;clearTimeout(saveTimeout);error(e);}}
  }

  window.addEventListener('message',async event=>{
    if(event.origin!==origin||event.source!==frame.contentWindow)return;
    const msg=event.data;
    if(!msg||typeof msg!=='object')return;
    if(msg.type==='document:ready'&&!opened) {
      opened=true;
      if(config.base64) {
        send('document:open-buffer',{fileName:config.name,buffer:bytes(config.base64).buffer,readonly:!!config.readonly});
        delete config.base64;
      } else {
        try {await frame.contentWindow.onCreateNew('.'+config.kind);officeReady();}catch(e){error(e);}
      }
    } else if(msg.type==='document:opened') officeReady();
    else if(msg.type==='document:dirty-changed'&&msg.payload?.dirty) MinimaDocs.changed();
    else if(msg.type==='document:saved'&&pending&&msg.id===pending.id) {
      if(msg.payload.dirty)MinimaDocs.changed();
      await returned(msg.payload.file,pending);
    }
    else if(msg.type==='document:error'&&(!pending||!msg.id||msg.id===pending.id)){pending=null;converting=false;clearTimeout(saveTimeout);error(msg.payload?.message||'The editor could not complete that operation.');}
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
      // Observe the same completed actions miniPaint uses for its undo history.
      for(const name of ['do_action','undo_action','redo_action']) {
        const original=editor.State?.[name];if(!original)continue;
        editor.State[name]=async function(...args){
          const result=await original.apply(this,args);
          if(!config.readonly&&(name!=='do_action'||result?.status==='completed'))MinimaDocs.changed();
          return result;
        };
      }
      if(config.readonly)editor.document.body.inert=true;
      opened=true;MinimaDocs.ready();
    } catch(e){error(e);}
  });
  frame.src=config.kind==='image'?'/image/index.html':'/editor.html?embed=1&embedOrigin='+encodeURIComponent(origin);
})();
