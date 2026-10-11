/* Runs inside the packaged Android image frame; native instrumentation supplies
 * real touchscreen strokes between phases. Uses the desktop regression's same
 * pixel/history/project assertions, never mocks the rendering engine. */
(() => {
 const a=ImageEditor,$=s=>document.querySelector(s);
 const check=(ok,message)=>{if(!ok)throw Error(message);};
 const click=text=>{const b=[...document.querySelectorAll('button')].find(b=>b.textContent===text);check(b,'Missing '+text);b.click();};
 const wait=async fn=>{for(let i=0;i<200;i++){if(fn())return;await new Promise(r=>setTimeout(r,50));}throw Error('Image action timed out');};
 const pixels=()=>{const c=document.createElement('canvas');c.width=a.Config.WIDTH;c.height=a.Config.HEIGHT;a.Layers.convert_layers_to_canvas(c.getContext('2d'),null,false);return [...c.getContext('2d').getImageData(160,120,1,1).data];};
 const visible=el=>{const r=el.getBoundingClientRect();return r.width>0&&r.height>0&&r.left>=0&&r.right<=innerWidth+1&&r.top>=0&&r.bottom<=innerHeight+1;};
 window.ImageStudioCheck={
  async prepare(){
   await ImageStudio.ready;
   check(FILTR_FX.length===16&&FiltrEngine.BUILTIN_PRESETS.length===14,'Desktop effects missing');
   for(const name of ['selection','clone','blur','sharpen','desaturate','brush','text','shape'])check(a.GUI.GUI_tools.tools_modules[name],'Missing tool '+name);
   check([...$('.studio-catbar').children].every(visible),'Phone tabs overflow');
   for(const name of ['Tools','Studio','Layers','Colour','History']){click(name);check(!$('.studio-sheet').hidden,'Sheet did not open '+name);check($('#canvas_minipaint').getBoundingClientRect().height>30,'Sheet covered canvas');click('Done');}
   const l=a.Config.layer;window.originalSource=l.link.src;
   a.GUI.GUI_tools.tools_modules.selection.object.selection={x:160,y:0,width:160,height:240};
   await ImageStudio.addMask(true);check(pixels()[3]===255,'Selection mask failed');await ImageStudio.invertMask();check(pixels()[3]===0,'Invert mask failed');await a.State.undo_action();check(pixels()[3]===255,'Mask undo failed');
   await ImageStudio.addMask();$('#mask-soft').checked=false;$('#mask-size').value=70;
   click('Layers');click('Paint hide');check($('.studio-sheet').hidden,'Mask painting did not close controls');
   await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));
  },
  point(){const r=$('#canvas_minipaint').getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2};},
  async afterMask(){
   await ImageStudio.flush();check(pixels()[3]===0,'Native finger did not paint mask');check(a.Config.layer.link.src===originalSource,'Mask damaged original');
   await a.State.undo_action();check(pixels()[3]===255,'Touch mask undo failed');await a.State.redo_action();check(pixels()[3]===0,'Touch mask redo failed');
   click('Paint hide');await ImageStudio.addMask();
   // Existing pixel retouch action must preserve exact source bytes on undo.
   const c=a.Layers.convert_layer_to_canvas(null,true),ctx=c.getContext('2d');ctx.fillStyle='#ff0000';ctx.fillRect(20,20,40,40);
   await a.Layers.update_layer_image(c);await ImageStudio.flush();check(a.Config.layer.link.src!==originalSource,'Retouch failed');await a.State.undo_action();await ImageStudio.flush();check(a.Config.layer.link.src===originalSource,'Retouch undo lost original');await a.State.redo_action();
   await a.GUI.GUI_tools.activate_tool('brush');a.Config.COLOR='#00ff00';
   window.beforeBrush=a.Config.layers.length;
  },
  async afterBrush(){
   await ImageStudio.flush();check(a.State.action_history.some(x=>/brush/i.test(x.action_description)),'Native brush stroke failed');
   await a.State.undo_action();await a.State.redo_action();
   // Keep a raster layer selected for effects, as on desktop.
   await a.State.do_action(new a.Actions.Select_layer_action(a.Config.layers.find(l=>l.type==='image').id));
   await ImageStudio.openEffects('adjust');
   check(visible($('.studio-effects footer'))&&visible($('#fx-apply')),'Phone effect actions clipped');
   check(getComputedStyle($('.fx-body aside')).display!=='none','Phone presets hidden');
   check($('#fx-presets').children.length===14,'Presets missing');
   $('#fx-presets').lastElementChild.scrollIntoView({block:'nearest',inline:'end'});check(visible($('#fx-presets').lastElementChild),'Last preset unreachable');
   $('#fx-presets').lastElementChild.click();click('Adjust');const brightness=$('[aria-label="brightness"]');brightness.value='15';brightness.dispatchEvent(new Event('input'));
  },
  async apply(){
   const before=a.Config.layers.length;click('Apply as new layer');await wait(()=>!$('.studio-effects'));await ImageStudio.flush();check(a.Config.layers.length===before+1,'Adjustment layer missing');
   check(a.Config.layer.params.minimaFiltr,'Colour settings lost');check(a.Config.layers.some(l=>!l.visible),'Original not preserved');
   // Exercise all desktop GPU effects/presets on Android's real WebGL driver.
   const c=document.createElement('canvas'),r=new FiltrEngine.Renderer(c);r.source.setImage(a.Layers.convert_layer_to_canvas(null,true));
   try{for(const effect of FILTR_FX){const s=FiltrEngine.freshSettings();s.output.maxPreviewDim=128;s.active=effect.id;r.render(s,0);check(!r.gl.getError(),'GPU effect '+effect.id);}for(const p of FiltrEngine.BUILTIN_PRESETS){const s=FiltrEngine.applyPreset(p.id,FiltrEngine.freshSettings());s.output.maxPreviewDim=128;r.render(s,0);check(!r.gl.getError(),'GPU preset '+p.id);}}finally{r.dispose();}
   await ImageStudio.addMask();click('Layers');
   const blend=$('#studio-blend');blend.value='multiply';blend.dispatchEvent(new Event('change'));await ImageStudio.flush();check(a.Config.layer.composition==='multiply','Blend missing');
   const opacity=$('#studio-opacity');opacity.value=85;opacity.dispatchEvent(new Event('change'));await ImageStudio.flush();check(a.Config.layer.opacity===85,'Opacity missing');
   click('Done');
   return JSON.parse(FileSave.export_as_json());
  },
  async reopened(){await ImageStudio.flush();const p=JSON.parse(FileSave.export_as_json());check(p.layers.some(l=>l.params?.minimaMask),'Reopened mask missing');check(p.layers.some(l=>l.params?.minimaFiltr),'Reopened colour settings missing');return p;}
 };
})();
