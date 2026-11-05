'use strict';
// Converts a legacy miniPaint/Filtr .minimadocs-image.json project into a layered OpenRaster
// archive that PhotoCraft opens. Every layer is rendered by miniPaint's own renderer in a
// hidden frame, so text, brushes, shapes and filters look exactly as they did. Enabled
// Image Studio masks are applied to the layer pixels; opacity, visibility and blend mode
// stay editable layer properties. The original JSON version is never modified.
(()=>{
 const BLEND={'source-over':'svg:src-over',multiply:'svg:multiply',screen:'svg:screen',overlay:'svg:overlay',darken:'svg:darken',lighten:'svg:lighten','color-dodge':'svg:color-dodge','color-burn':'svg:color-burn','hard-light':'svg:hard-light','soft-light':'svg:soft-light',difference:'svg:difference',exclusion:'svg:exclusion',hue:'svg:hue',saturation:'svg:saturation',color:'svg:color',luminosity:'svg:luminosity',lighter:'svg:plus'};
 const CRC=new Uint32Array(256).map((_,n)=>{let c=n;for(let k=0;k<8;k++)c=c&1?0xEDB88320^(c>>>1):c>>>1;return c>>>0;});
 const crc32=b=>{let c=~0;for(let i=0;i<b.length;i++)c=CRC[(c^b[i])&255]^(c>>>8);return ~c>>>0;};
 // ponytail: stored (uncompressed) ZIP; layer PNGs are already deflated. No ZIP64, so >4 GiB fails.
 function zip(files){
  const parts=[],central=[];let offset=0;
  for(const [name,data] of files){
   const n=new TextEncoder().encode(name),h=new DataView(new ArrayBuffer(30)),c=new DataView(new ArrayBuffer(46)),crc=crc32(data);
   h.setUint32(0,0x04034b50,true);h.setUint16(4,20,true);h.setUint32(14,crc,true);h.setUint32(18,data.length,true);h.setUint32(22,data.length,true);h.setUint16(26,n.length,true);
   c.setUint32(0,0x02014b50,true);c.setUint16(4,20,true);c.setUint16(6,20,true);c.setUint32(16,crc,true);c.setUint32(20,data.length,true);c.setUint32(24,data.length,true);c.setUint16(28,n.length,true);c.setUint32(42,offset,true);
   parts.push(new Uint8Array(h.buffer),n,data);central.push(new Uint8Array(c.buffer),n);offset+=30+n.length+data.length;
  }
  const size=central.reduce((s,p)=>s+p.length,0),end=new DataView(new ArrayBuffer(22));
  end.setUint32(0,0x06054b50,true);end.setUint16(8,files.length,true);end.setUint16(10,files.length,true);end.setUint32(12,size,true);end.setUint32(16,offset,true);
  const out=new Uint8Array(offset+size+22);let p=0;for(const part of [...parts,...central,new Uint8Array(end.buffer)]){out.set(part,p);p+=part.length;}
  return out;
 }
 const xml=s=>String(s).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
 const png=async c=>new Uint8Array(await (await new Promise((res,rej)=>c.toBlob(b=>b?res(b):rej(Error('A layer could not be converted.')),'image/png'))).arrayBuffer());
 async function image(url){const i=new Image();i.src=url;await i.decode();return i;}
 window.legacyImageToOra=async function(text){
  const frame=document.createElement('iframe');frame.hidden=true;frame.title='Legacy image converter';
  const loaded=new Promise((res,rej)=>{frame.onload=res;frame.onerror=()=>rej(Error('The legacy image engine could not load.'));});
  frame.src='/image/index.html';document.body.append(frame);
  try{
   await loaded;const editor=frame.contentWindow,api=editor.ImageEditor;
   if(!editor.FileOpen||!api)throw Error('The legacy image engine did not load.');
   const original=api.State.do_action;let opened=false;
   api.State.do_action=async function(action,...args){const r=await original.call(this,action,...args);if(action.action_id==='open_json_file')opened=r.status==='completed';return r;};
   try{await editor.FileOpen.load_json(text);}finally{api.State.do_action=original;}
   if(!opened)throw Error('The image project could not be opened.');
   const {Config:config,Layers}=api,W=config.WIDTH,H=config.HEIGHT,canvas=()=>{const c=document.createElement('canvas');c.width=W;c.height=H;return c;};
   await editor.document.fonts?.ready;
   for(const l of config.layers)if(l.link instanceof editor.HTMLImageElement&&!l.link.complete)await l.link.decode();
   const notes=[],stack=[],files=[],rendered=[],merged=canvas(),mctx=merged.getContext('2d'),sorted=Layers.get_sorted_layers();
   for(const [i,l] of sorted.entries()){
    const c=canvas(),spec=l.params?.minimaMask;let layer={...l,visible:true};
    if(l.type==='image'&&spec?.data){
     if(spec.enabled===false)notes.push('“'+l.name+'”: its disabled mask was not carried over.');
     else{
      const src=l.link_canvas||l.link,out=document.createElement('canvas');out.width=src.naturalWidth||src.width;out.height=src.naturalHeight||src.height;
      const x=out.getContext('2d');x.drawImage(src,0,0);x.globalCompositeOperation='destination-in';x.drawImage(await image(spec.data),0,0,out.width,out.height);
      layer={...layer,link_canvas:out};
     }
    }
    Layers.render_object(c.getContext('2d'),layer);rendered.push(c);
    const op=BLEND[l.composition||'source-over'];if(!op)notes.push('“'+l.name+'”: blend “'+l.composition+'” opens as Normal.');
    files.push(['data/layer'+i+'.png',await png(c)]);
    stack.push('<layer name="'+xml(l.name||'Layer '+(i+1))+'" src="data/layer'+i+'.png" x="0" y="0" opacity="'+Math.max(0,Math.min(1,(l.opacity??100)/100))+'" visibility="'+(l.visible===false?'hidden':'visible')+'" composite-op="'+(op||'svg:src-over')+'"/>');
   }
   for(const [i,l] of [...sorted.entries()].reverse())if(l.visible!==false){mctx.globalAlpha=(l.opacity??100)/100;mctx.globalCompositeOperation=l.composition||'source-over';mctx.drawImage(rendered[i],0,0);}
   const scale=Math.min(1,256/Math.max(W,H)),thumb=document.createElement('canvas');thumb.width=Math.max(1,Math.round(W*scale));thumb.height=Math.max(1,Math.round(H*scale));thumb.getContext('2d').drawImage(merged,0,0,thumb.width,thumb.height);
   const doc='<?xml version="1.0" encoding="UTF-8"?>\n<image version="0.0.5" w="'+W+'" h="'+H+'"><stack>'+stack.join('')+'</stack></image>';
   return {bytes:zip([['mimetype',new TextEncoder().encode('image/openraster')],['stack.xml',new TextEncoder().encode(doc)],...files,['mergedimage.png',await png(merged)],['Thumbnails/thumbnail.png',await png(thumb)]]),notes};
  }finally{frame.remove();}
 };
})();
