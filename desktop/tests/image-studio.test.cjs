const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs/promises'),path=require('node:path');
const {_electron}=require('playwright-core'),root=path.resolve(__dirname,'..');
test('image studio retains masks, alpha, layers and effects through undo, save and reopen',{timeout:240000},async()=>{
 const dir=await fs.mkdtemp(path.join(root,'build/image-studio-'));let app;
 try{
  const packaged=process.env.MINIMADOCS_IMAGE_TEST_APP;
  app=await _electron.launch({executablePath:packaged||require('electron'),args:packaged?['--user-data-dir='+dir]:[root],env:{...process.env,MINIMADOCS_TEST_DIR:dir}});
  const home=await app.firstWindow();await home.waitForFunction(()=>document.querySelector('#connection')&&!document.querySelector('#connection').textContent.includes('Opening workspace'));
  const png=await home.evaluate(()=>{const c=document.createElement('canvas');c.width=640;c.height=480;const x=c.getContext('2d'),g=x.createLinearGradient(0,0,640,480);g.addColorStop(0,'#174b68');g.addColorStop(.5,'#edb06b');g.addColorStop(1,'#964d51');x.fillStyle=g;x.fillRect(0,0,640,480);x.fillStyle='#ffe8b9';x.beginPath();x.arc(420,155,62,0,Math.PI*2);x.fill();x.fillStyle='#183a47';x.beginPath();x.moveTo(0,410);x.lineTo(220,190);x.lineTo(470,480);x.lineTo(0,480);x.fill();x.clearRect(0,0,12,12);return c.toDataURL().split(',')[1];});
  const file=path.join(dir,'studio-landscape.png');await fs.writeFile(file,Buffer.from(png,'base64'));
  await app.evaluate(({dialog},file)=>{dialog.showOpenDialog=async()=>({canceled:false,filePaths:[file]});},file);
  const opening=app.waitForEvent('window');await home.evaluate(()=>docs.call('import'));let editor=await opening;editor.on('dialog',()=>{});await editor.waitForFunction(()=>!document.querySelector('#save').disabled);
  let frame=editor.frames().find(f=>f.url().includes('/image/index.html'));assert.ok(frame);
  assert.equal(await frame.locator('.studio-tabs [role=tab]').count(),3);
  const state=await frame.evaluate(async()=>{
   const a=ImageEditor,l=a.Config.layer,source=l.link.src;
   a.GUI.GUI_tools.tools_modules.selection.object.selection={x:320,y:0,width:320,height:480};
   await ImageStudio.addMask(true);
   const pixels=()=>{const c=document.createElement('canvas');c.width=a.Config.WIDTH;c.height=a.Config.HEIGHT;a.Layers.convert_layers_to_canvas(c.getContext('2d'),null,false);return [...c.getContext('2d').getImageData(100,200,1,1).data];};
   const masked=pixels();await a.State.undo_action();const undone=pixels();await a.State.redo_action();const redone=pixels();
   return {masked,undone,redone,sourceUnchanged:l.link.src===source,mask:l.params.minimaMask.data};
  });
  assert.equal(state.masked[3],0);assert.equal(state.undone[3],255);assert.equal(state.redone[3],0);assert.ok(state.sourceUnchanged);
  await frame.getByRole('button',{name:'Remove mask',exact:true}).click();
  await frame.getByRole('button',{name:'Brightness',exact:true}).click();await frame.locator('.studio-effects').waitFor();
  await frame.locator('[aria-label="brightness"]').fill('15');await frame.locator('[aria-label="brightness"]').dispatchEvent('input');
  await editor.screenshot({path:path.join(root,'build/image-studio-effects.png')});
  await frame.getByRole('button',{name:'Apply as new layer',exact:true}).click();await frame.locator('.studio-effects').waitFor({state:'detached'});
  const applied=await frame.evaluate(()=>({layers:AppConfig.layers.length,originalHidden:!AppConfig.layers[0].visible,width:AppConfig.layer.width_original,height:AppConfig.layer.height_original,alpha:(()=>{const c=Layers.convert_layer_to_canvas(null,true);return c.getContext('2d').getImageData(0,0,1,1).data[3];})()}));
  assert.equal(applied.layers,2);assert.ok(applied.originalHidden);assert.equal(applied.width,640);assert.equal(applied.height,480);assert.equal(applied.alpha,0);
  await frame.evaluate(async()=>{await State.undo_action();if(AppConfig.layers.length!==1||!AppConfig.layer.visible)throw Error('Undo lost source');await State.redo_action();});
  // Exercise every GPU effect and built-in preset against the actual vendored engine.
  const effects=await frame.evaluate(()=>{
   const c=document.createElement('canvas'),source=Layers.convert_layer_to_canvas(null,true),r=new FiltrEngine.Renderer(c);r.source.setImage(source);
   try{for(const effect of FILTR_FX){const s=FiltrEngine.freshSettings();s.output.maxPreviewDim=256;s.active=effect.id;r.render(s,0);if(r.gl.getError())throw Error('GPU error: '+effect.id);}
    for(const p of FiltrEngine.BUILTIN_PRESETS){const s=FiltrEngine.applyPreset(p.id,FiltrEngine.freshSettings());s.output.maxPreviewDim=256;r.render(s,0);if(r.gl.getError())throw Error('GPU error: '+p.id);}
    return {effects:FILTR_FX.length,presets:FiltrEngine.BUILTIN_PRESETS.length};
   }finally{r.dispose();}
  });assert.equal(effects.effects,16);assert.equal(effects.presets,14);
  await frame.evaluate(async()=>{
   const original=AppConfig.layer.link.src,c=Layers.convert_layer_to_canvas(null,true),ctx=c.getContext('2d');ctx.fillStyle='#ff0000';ctx.fillRect(50,50,20,20);
   const result=await Layers.update_layer_image(c);if(result.status!=='completed')throw Error('Retouch action failed');await AppConfig.layer.link.decode();
   if(AppConfig.layer.link.src===original)throw Error('Retouch did not alter pixels');await State.undo_action();await AppConfig.layer.link.decode();
   if(AppConfig.layer.link.src!==original)throw Error('Retouch undo did not restore original pixels');
   await State.redo_action();await AppConfig.layer.link.decode();if(AppConfig.layer.link.src===original)throw Error('Retouch redo lost edit');
  });
  await frame.getByRole('button',{name:'Add mask',exact:true}).click();await frame.waitForFunction(()=>AppConfig.layer.params.minimaMask);
  // Use real canvas mouse input to paint a mask; no pixel mutation in the test.
  await frame.getByRole('button',{name:'Paint hide',exact:true}).click();
  const point=await frame.evaluate(()=>{const p=Layers.get_screen_coords?Layers.get_screen_coords(320,240):null;const g=ImageEditor.GUI;return {x:g.canvas_offset.x+AppConfig.WIDTH*AppConfig.ZOOM/2,y:g.canvas_offset.y+AppConfig.HEIGHT*AppConfig.ZOOM/2};});
  const rect=await editor.locator('#editor').boundingBox();await editor.mouse.move(rect.x+point.x,rect.y+point.y);await editor.mouse.down();await editor.mouse.move(rect.x+point.x+20,rect.y+point.y+20,{steps:5});await editor.locator('#save').click({force:true});await editor.mouse.up();
  await frame.waitForFunction(()=>State.action_history.some(a=>a.action_description==='Paint layer mask'));
  await editor.locator('#title').fill('Image studio regression');await editor.locator('#save').click();await editor.waitForFunction(()=>document.querySelector('#save-status').textContent==='Saved on this Mac');
  const before=await frame.evaluate(()=>JSON.parse(FileSave.export_as_json()));assert.ok(before.layers.at(-1).params.minimaMask);
  await editor.screenshot({path:path.join(root,'build/image-studio-workspace.png')});
  const id=(await home.evaluate(()=>docs.call('list'))).documents[0].id;
  await editor.close();const reopening=app.waitForEvent('window');await home.evaluate(id=>docs.call('open',{id}),id);editor=await reopening;editor.on('dialog',()=>{});await editor.waitForFunction(()=>!document.querySelector('#save').disabled);
  frame=editor.frames().find(f=>f.url().includes('/image/index.html'));const after=await frame.evaluate(()=>JSON.parse(FileSave.export_as_json()));
  assert.deepEqual(after.layers.map(l=>l.params),before.layers.map(l=>l.params));assert.deepEqual(after.data,before.data);
  console.log('Verified 15 effects, 14 presets, alpha, mask painting, undo/redo, layered save and reopen.');
 }catch(e){if(app)for(const w of app.windows())console.log('Window status:',await w.locator('body').innerText().catch(()=>''));throw e;}
 finally{if(app){await app.evaluate(({BrowserWindow})=>BrowserWindow.getAllWindows().forEach(w=>w.destroy())).catch(()=>{});await app.close();}}
});
