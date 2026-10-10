/* minimaDocs Image Studio. Reuses miniPaint actions/tools and Filtr's vendored
 * renderer/control schema. Masks live in layer.params; original pixels remain
 * in layer.link, so the existing serializer, undo and sharing keep working. */
'use strict';
(() => {
 const app=window.ImageEditor;
 if(!app)throw new Error('Image workspace API is missing.');
 const {Config:config,State,Actions,Layers,GUI}=app;
 const $=s=>document.querySelector(s), clone=o=>JSON.parse(JSON.stringify(o));
 const masks=new Map(),pendingActions=new Set();let maskMode='',stroke=null,busy=false,fx=null;
 const canvas=(w,h)=>{const c=document.createElement('canvas');c.width=w;c.height=h;return c;};
 const element=(tag,cls,text)=>{const e=document.createElement(tag);if(cls)e.className=cls;if(text!==undefined)e.textContent=text;return e;};
 const button=(label,fn,host,cls='')=>{const b=element('button',cls,label);b.type='button';b.onclick=()=>run(fn);host.append(b);return b;};
 function note(message){$('#studio-message').textContent=message;}
 async function run(fn){if(busy)return;try{await fn();}catch(e){note(e.message||String(e));}}
 async function action(title,items){const result=await State.do_action(new Actions.Bundle_action('studio_edit',title,items));if(result?.status!=='completed')throw result?.reason||Error('The edit could not finish.');}
 async function updateLayer(layer,changes,title){await action(title,[new Actions.Update_layer_action(layer.id,changes)]);}
 function imageLayer(){const l=config.layer;if(l?.type!=='image')throw Error('Select an image layer first. Use Layer → Rasterize for text or shapes.');return l;}
 async function loadMask(layer){
  const url=layer.params?.minimaMask?.data;if(!url)return null;
  if(!url.startsWith('data:image/png;base64,')||url.length>24*1024*1024)throw Error('Invalid layer mask.');
  let entry=masks.get(layer.id);
  if(entry?.url===url){await entry.ready;return entry;}
  entry={url,canvas:null};masks.set(layer.id,entry);
  entry.ready=(async()=>{const image=new Image();image.src=url;await image.decode();entry.canvas=canvas(image.width,image.height);entry.canvas.getContext('2d').drawImage(image,0,0);config.need_render=true;})();
  await entry.ready;return entry;
 }
 const renderObject=Layers.render_object;
 Layers.render_object=function(ctx,layer,preview){
  const spec=layer.params?.minimaMask;
  if(layer.type==='image'&&spec?.enabled!==false&&spec?.data){
   const entry=masks.get(layer.id),mask=stroke?.id===layer.id?stroke.canvas:entry?.url===spec.data?entry.canvas:null;
   if(mask){
    const source=layer.link_canvas||layer.link;
    if(source instanceof HTMLImageElement&&!source.complete)return;
    // A private cache is omitted by the existing project serializer.
    const key=[source,source.src,spec.data,stroke?.revision];let cached=layer._studioComposite;
    if(layer.link_canvas||!cached||key.some((v,i)=>v!==cached.key[i])){
     const out=canvas(source.naturalWidth||source.width,source.naturalHeight||source.height),c=out.getContext('2d');
     c.drawImage(source,0,0);c.globalCompositeOperation='destination-in';c.drawImage(mask,0,0,out.width,out.height);
     cached=layer._studioComposite={key,canvas:out};
    }
    return renderObject.call(this,ctx,{...layer,link_canvas:cached.canvas},preview);
   }
  }
  return renderObject.call(this,ctx,layer,preview);
 };
 async function syncMasks(){await Promise.all(config.layers.map(loadMask));for(const id of masks.keys())if(!config.layers.some(l=>l.id===id))masks.delete(id);}
 async function setMask(layer,c,title='Edit layer mask'){
  const params={...layer.params};params.minimaMask=c?{data:c.toDataURL('image/png'),enabled:true}:undefined;
  await updateLayer(layer,{params},title);
 }
 async function addMask(fromSelection=false){
  const l=imageLayer(),c=canvas(l.width_original,l.height_original),ctx=c.getContext('2d');ctx.fillStyle='#fff';
  if(fromSelection){
   const s=GUI.GUI_tools.tools_modules.selection.object.selection;
   if(!s.width||!s.height)throw Error('Draw a rectangular selection first.');
   // Map the existing document-space selection through the layer transform.
   ctx.scale(c.width/l.width,c.height/l.height);ctx.translate(l.width/2,l.height/2);ctx.rotate(-l.rotate*Math.PI/180);ctx.translate(-l.x-l.width/2,-l.y-l.height/2);ctx.fillRect(s.x,s.y,s.width,s.height);
  }else ctx.fillRect(0,0,c.width,c.height);
  await setMask(l,c,fromSelection?'Mask from selection':'Add layer mask');note('Mask added. Paint Hide or Reveal; original pixels are retained.');
 }
 async function invertMask(){const l=imageLayer(),m=await loadMask(l);if(!m)throw Error('Add a mask first.');const c=canvas(m.canvas.width,m.canvas.height),ctx=c.getContext('2d');ctx.fillStyle='#fff';ctx.fillRect(0,0,c.width,c.height);ctx.globalCompositeOperation='destination-out';ctx.drawImage(m.canvas,0,0);await setMask(l,c,'Invert layer mask');}
 function maskPoint(e,l){const p=app.Tools.get_mouse_coordinates_from_event(e),a=-l.rotate*Math.PI/180,dx=p.x-l.x-l.width/2,dy=p.y-l.y-l.height/2;return {x:(dx*Math.cos(a)-dy*Math.sin(a)+l.width/2)*l.width_original/l.width,y:(dx*Math.sin(a)+dy*Math.cos(a)+l.height/2)*l.height_original/l.height};}
 function paintMask(e){
  const l=Layers.get_layer(stroke.id),p=maskPoint(e,l),last=stroke.last||p,ctx=stroke.canvas.getContext('2d'),radius=Number($('#mask-size').value)/2*l.width_original/l.width;
  // Filtr's filtrEraseAt stroke, adapted to a separate reversible alpha mask.
  ctx.save();ctx.globalCompositeOperation=maskMode==='hide'?'destination-out':'source-over';ctx.lineCap='round';ctx.lineJoin='round';
  if($('#mask-soft').checked)ctx.filter='blur('+(radius*.35)+'px)';ctx.strokeStyle='#fff';ctx.lineWidth=radius*2;ctx.beginPath();ctx.moveTo(last.x,last.y);ctx.lineTo(p.x+.01,p.y+.01);ctx.stroke();ctx.restore();
  stroke.last=p;stroke.revision++;config.need_render=true;
 }
 document.addEventListener('mousedown',e=>{
  if(!maskMode||e.button!==0||e.target.id!=='canvas_minipaint')return;
  e.preventDefault();e.stopImmediatePropagation();
  run(async()=>{const l=imageLayer(),m=await loadMask(l);if(!m)throw Error('Add a mask first.');if(l.params.minimaMask.enabled===false)throw Error('Enable the mask before painting.');
   stroke={id:l.id,canvas:canvas(m.canvas.width,m.canvas.height),revision:0};stroke.canvas.getContext('2d').drawImage(m.canvas,0,0);paintMask(e);
  });
 },true);
 document.addEventListener('mousemove',e=>{if(stroke){e.preventDefault();e.stopImmediatePropagation();paintMask(e);}},true);
 async function finishStroke(){if(!stroke)return;const current=stroke;stroke=null;await setMask(Layers.get_layer(current.id),current.canvas,'Paint layer mask');}
 document.addEventListener('mouseup',e=>{if(stroke){e.preventDefault();e.stopImmediatePropagation();run(finishStroke);}},true);
 window.addEventListener('blur',()=>run(finishStroke));
 function maskTool(mode){maskMode=maskMode===mode?'':mode;$('#canvas_minipaint').style.cursor=maskMode?'crosshair':'';for(const b of document.querySelectorAll('[data-mask-mode]'))b.setAttribute('aria-pressed',String(b.dataset.maskMode===maskMode));note(maskMode?'Paint on the image to '+maskMode+' pixels.':'Image tools active.');}
 $('#tools_container').addEventListener('click',()=>{maskMode='';refresh();});
 const right=$('.sidebar_right');
 const studio=element('section','studio-panel');studio.innerHTML='<div class="studio-heading"><strong>IMAGE STUDIO</strong><span>RGB / 8</span></div><div class="studio-tabs" role="tablist" aria-label="Image tools"></div><div id="studio-panels"></div><p id="studio-message" role="status">Select a layer to edit. Your originals stay in the project.</p>';
 right.prepend(studio);
 const panels={};
 for(const name of ['Adjust','Effects','Retouch']){
  const panel=element('div','studio-tabpanel');panel.id='studio-'+name.toLowerCase();panel.setAttribute('role','tabpanel');$('#studio-panels').append(panel);panels[name]=panel;
  const b=button(name,()=>{for(const [key,p]of Object.entries(panels))p.hidden=key!==name;for(const tab of $('.studio-tabs').children)tab.setAttribute('aria-selected',String(tab===b));},$('.studio-tabs'));b.setAttribute('role','tab');b.setAttribute('aria-controls',panel.id);b.setAttribute('aria-selected',String(name==='Adjust'));panel.hidden=name!=='Adjust';
 }
 const adjustNames=['Brightness','Contrast','Saturation','Hue','Gamma','Sharpness','Blur'];
 for(const label of adjustNames)button(label,()=>openEffects('adjust',label.toLowerCase()),panels.Adjust);
 button('Colour balance',()=>GUI.modules['image/color_corrections'].color_corrections(),panels.Adjust);
 for(const effect of FILTR_FX.filter(f=>f.id!=='none'))button(effect.label,()=>openEffects('effects',effect.id),panels.Effects);
 for(const [label,id,hint] of [['Clone stamp','clone','Right-click or long-press to set the clone source, then paint.'],['Blur brush','blur','Paint over an image layer to soften detail.'],['Sharpen brush','sharpen','Paint over an image layer to bring out detail.'],['Desaturate','desaturate','Paint to remove colour locally.'],['Magic erase','magic_erase','Click a colour region to erase it.'],['Brush','brush','Draw on the canvas; choose size and colour above.'],['Text','text','Drag a text box on the canvas.'],['Shapes','shape','Choose a shape, then drag on the canvas.']])button(label,async()=>{maskMode='';await GUI.GUI_tools.activate_tool(id);note(hint);},panels.Retouch);
 const layerBlock=$('.block.layers');right.append(layerBlock);
 const layerControls=element('div','studio-layer-controls');layerControls.innerHTML='<label>Blend <select id="studio-blend" aria-label="Layer blend mode"></select></label><label>Opacity <input id="studio-opacity" aria-label="Layer opacity" type="range" min="0" max="100"><output id="studio-opacity-value"></output></label>';
 layerBlock.insertBefore(layerControls,$('#layers_base'));
 for(const mode of ['source-over','multiply','screen','overlay','soft-light','hard-light','darken','lighten','color-dodge','color-burn','difference','exclusion','hue','saturation','color','luminosity']){const o=element('option','',mode==='source-over'?'Normal':mode.replaceAll('-',' '));o.value=mode;$('#studio-blend').append(o);}
 $('#studio-blend').onchange=e=>run(()=>updateLayer(config.layer,{composition:e.target.value},'Change blend mode'));
 $('#studio-opacity').oninput=e=>{$('#studio-opacity-value').textContent=e.target.value+'%';};
 $('#studio-opacity').onchange=e=>run(()=>updateLayer(config.layer,{opacity:Number(e.target.value)},'Change opacity'));
 $('#layer_duplicate').textContent='Duplicate';$('#layer_raster').textContent='Rasterize';
 const maskPanel=element('details','studio-masks');maskPanel.open=true;maskPanel.innerHTML='<summary>Layer mask <span id="mask-state">None</span></summary><div class="mask-actions"></div><div class="mask-brush"><label>Size <input id="mask-size" aria-label="Mask brush size" type="range" min="2" max="250" value="40"></label><label><input id="mask-soft" type="checkbox" checked> Soft edge</label></div>';
 layerBlock.append(maskPanel);const maskActions=$('.mask-actions');
 button('Add mask',()=>addMask(),maskActions);button('From selection',()=>addMask(true),maskActions);
 for(const mode of ['hide','reveal']){const b=button(mode==='hide'?'Paint hide':'Paint reveal',()=>maskTool(mode),maskActions);b.dataset.maskMode=mode;b.setAttribute('aria-pressed','false');}
 button('Invert',invertMask,maskActions);button('Enable / disable',()=>{const l=imageLayer();if(!l.params.minimaMask)throw Error('Add a mask first.');return updateLayer(l,{params:{...l.params,minimaMask:{...l.params.minimaMask,enabled:l.params.minimaMask.enabled===false}}},'Toggle layer mask');},maskActions);
 button('Remove mask',()=>setMask(imageLayer(),null,'Remove layer mask'),maskActions);
 const history=element('details','studio-history');history.innerHTML='<summary>History</summary><div id="studio-history-list"></div>';right.append(history);
 const top=element('div','studio-commands');$('.submenu').append(top);
 button('Undo',()=>State.undo_action(),top);button('Redo',()=>State.redo_action(),top);button('Fit',()=>GUI.GUI_preview.zoom_auto(),top);button('100%',()=>GUI.GUI_preview.zoom(100),top);
 const logo=$('.logo');logo.textContent='Image Studio';logo.removeAttribute('href');
 for(const tool of $('#tools_container').children){tool.setAttribute('role','button');tool.tabIndex=0;tool.setAttribute('aria-label',tool.title);tool.addEventListener('keydown',e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();tool.click();}});}
 function refresh(){
  $('#canvas_minipaint').style.cursor=maskMode?'crosshair':'';
  const l=config.layer;if(!l)return;
  $('#studio-blend').value=l.composition;$('#studio-opacity').value=l.opacity;$('#studio-opacity-value').textContent=l.opacity+'%';
  $('#mask-state').textContent=l.params?.minimaMask?(l.params.minimaMask.enabled===false?'Disabled':'Active'):'None';
  for(const b of document.querySelectorAll('[data-mask-mode]'))b.setAttribute('aria-pressed',String(b.dataset.maskMode===maskMode));
  const list=$('#studio-history-list');list.replaceChildren();
  for(const [i,item]of State.action_history.entries()){const row=element('div',i<State.action_history_index?'':'history-undone',item.action_description);list.prepend(row);}
  GUI.GUI_layers.render_layers();
 }
 for(const method of ['do_action','undo_action','redo_action']){
  const original=State[method];State[method]=function(...args){
   const task=(async()=>{const result=await original.apply(this,args);await syncMasks();refresh();return result;})();
   pendingActions.add(task);task.finally(()=>pendingActions.delete(task)).catch(()=>{});return task;
  };
 }
 // Filtr controls use the same declarative schema as Atelier, with local preview
 // and a full-resolution result inserted through miniPaint's existing action.
 function control(host,spec,object){
  const row=element('label','fx-control'),label=spec.lb||spec.k.replace(/([A-Z])/g,' $1');row.append(element('span','',label));let input;
  if(spec.o){input=element('select');spec.o.forEach((value,i)=>{const option=element('option','',spec.lo?.[i]||String(value));option.value=String(value);input.append(option);});input.value=String(object[spec.k]);}
  else {input=element('input');input.type=spec.t==='b'?'checkbox':spec.t==='col'?'color':'range';if(input.type==='range'){input.min=spec.mi;input.max=spec.ma;input.step=spec.st||1;}if(input.type==='checkbox')input.checked=object[spec.k];else input.value=object[spec.k];}
  input.setAttribute('aria-label',label);const output=element('output','',input.type==='range'?String(object[spec.k]):'');
  input.oninput=()=>{object[spec.k]=input.type==='checkbox'?input.checked:spec.o?spec.o[input.selectedIndex]:input.type==='color'?input.value:Number(input.value);output.textContent=input.type==='range'?String(object[spec.k]):'';renderEffects();};row.append(input,output);host.append(row);return input;
 }
 function effectControls(){
  const host=$('#fx-controls');host.replaceChildren();
  if(fx.tab==='adjust')for(const c of FILTR_ADJUST)control(host,c,fx.settings.adjust);
  else if(fx.tab==='effects'){const s=element('select');s.id='fx-effect';s.setAttribute('aria-label','Effect');for(const f of FILTR_FX){const o=element('option','',f.label);o.value=f.id;s.append(o);}s.value=fx.settings.active;s.onchange=()=>{fx.settings.active=s.value;effectControls();renderEffects();};host.append(s);for(const c of FILTR_FX.find(f=>f.id===fx.settings.active).c)control(host,c,fx.settings[fx.settings.active]);}
  else for(const post of FILTR_POST){control(host,{k:'enabled',t:'b',lb:post.id},fx.settings.post[post.id]);for(const c of post.c)control(host,c,fx.settings.post[post.id]);}
 }
 function renderEffects(full=false){
  if(!fx)return;
  try{
   fx.settings.output.maxPreviewDim=full?Math.max(fx.source.width,fx.source.height):Math.min(1280,Math.max(fx.source.width,fx.source.height));
   // Fresh renderer per source avoids Filtr's CPU-dither cache retaining another layer.
   fx.renderer.render(fx.settings,0);
   $('#fx-size').textContent=(full?'Output':'Preview')+' '+fx.preview.width+' × '+fx.preview.height+' · output '+fx.source.width+' × '+fx.source.height;
   $('#fx-error').textContent='';$('#fx-apply').disabled=false;
  }catch(e){$('#fx-error').textContent=e.message;$('#fx-apply').disabled=true;throw e;}
 }
 function closeEffects(){if(!fx)return;fx.renderer.dispose();fx.dialog.close();fx.dialog.remove();fx=null;}
 async function openEffects(tab='adjust',setting){
  const l=imageLayer();if(fx)return;
  const dialog=element('dialog','studio-effects');dialog.innerHTML='<header><div><span class="studio-eyebrow">IMAGE STUDIO</span><h2>Adjustments & effects</h2></div><button id="fx-close" aria-label="Close adjustments">×</button></header><div class="fx-body"><aside><h3>Presets</h3><div id="fx-presets"></div></aside><div class="fx-image"><canvas id="fx-preview"></canvas><p id="fx-size"></p><label><input id="fx-original" type="checkbox"> Show original</label><p id="fx-error" role="alert"></p></div><section><div id="fx-tabs"></div><div id="fx-controls"></div></section></div><footer><span>Applies to a new layer. The source stays in your project.</span><button id="fx-reset">Reset</button><button id="fx-cancel">Cancel</button><button id="fx-apply">Apply as new layer</button></footer>';
  dialog.addEventListener('keydown',e=>e.stopPropagation());
  document.body.append(dialog);const preview=$('#fx-preview'),source=Layers.convert_layer_to_canvas(l.id,true,false);let renderer;
  try{renderer=new FiltrEngine.Renderer(preview);}catch(e){dialog.remove();throw e;}
  fx={dialog,preview,renderer,source,layer:l,settings:FiltrEngine.freshSettings(),tab};if(source.width>renderer.gl.getParameter(renderer.gl.MAX_TEXTURE_SIZE)||source.height>renderer.gl.getParameter(renderer.gl.MAX_TEXTURE_SIZE)){closeEffects();throw Error('This image exceeds the GPU size limit for effects. Resize a copy first.');}renderer.source.setImage(source,l.name);
  if(tab==='effects'&&setting)fx.settings.active=setting;
  for(const preset of FiltrEngine.BUILTIN_PRESETS)button(preset.name,()=>{fx.settings=FiltrEngine.applyPreset(preset.id,fx.settings);effectControls();renderEffects();},$('#fx-presets'));
  for(const [label,key]of [['Adjust','adjust'],['Effects','effects'],['Finish','post']])button(label,()=>{fx.tab=key;effectControls();},$('#fx-tabs'));
  $('#fx-original').onchange=e=>{fx.settings.output.showOriginal=e.target.checked;renderEffects();};
  $('#fx-close').onclick=closeEffects;$('#fx-cancel').onclick=closeEffects;dialog.addEventListener('cancel',e=>{e.preventDefault();closeEffects();});
  $('#fx-reset').onclick=()=>{fx.settings=FiltrEngine.freshSettings();$('#fx-original').checked=false;effectControls();renderEffects();};
  $('#fx-apply').onclick=()=>run(async()=>{
   busy=true;$('#fx-apply').disabled=true;
   try{
    fx.settings.output.showOriginal=false;renderEffects(true);
    const out=canvas(source.width,source.height),ctx=out.getContext('2d');ctx.drawImage(preview,0,0);
    // Filtr composites to an opaque screen; restore source alpha for image layers.
    ctx.globalCompositeOperation='destination-in';ctx.drawImage(source,0,0);
    const data=out.toDataURL('image/png');
    const params={...clone(l.params),minimaFiltr:{settings:clone(fx.settings)}};
    await action('Apply image adjustments',[
     new Actions.Insert_layer_action({type:'image',name:l.name+' · adjusted',data,x:l.x,y:l.y,width:l.width,height:l.height,width_original:out.width,height_original:out.height,rotate:l.rotate,opacity:l.opacity,composition:l.composition,filters:clone(l.filters),params},false),
     new Actions.Update_layer_action(l.id,{visible:false})
    ]);
    closeEffects();note('Adjustment layer created. The original is hidden below it; Undo restores it.');
   }finally{busy=false;if(fx)$('#fx-apply').disabled=false;}
  });
  dialog.showModal();effectControls();renderEffects();if(setting&&tab==='adjust')dialog.querySelector('[aria-label="'+setting+'"]')?.focus();
 }
 async function flush(){
  await finishStroke();
  while(pendingActions.size)await Promise.all([...pendingActions]);
  await syncMasks();
  await Promise.all(config.layers.filter(l=>l.type==='image').map(l=>l.link.decode()));
 }
 window.ImageStudio={flush,ready:syncMasks().then(()=>{config.TRANSPARENCY=true;refresh();GUI.check_canvas_offset();GUI.GUI_preview.zoom_auto();}),openEffects,addMask,invertMask};
})();
