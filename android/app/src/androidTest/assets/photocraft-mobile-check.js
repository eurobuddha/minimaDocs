(()=>{
 const check=(v,m)=>{if(!v)throw Error(m);},delay=ms=>new Promise(r=>setTimeout(r,ms));
 async function wait(fn,message){for(let i=0;i<600;i++){if(await fn())return;await delay(50);}throw Error(message);}
 const pc=()=>document.querySelector('#editor').contentWindow.photoCraft;
 const state=async()=>(await pc().request('mobile.state')).value;
 const pixels=async()=>Array.from((await pc().request('mobile.thumbnail')).bytes).reduce((hash,b)=>(Math.imul(hash,31)+b)|0,0);let beforeMask;
 const click=text=>{const b=[...document.querySelectorAll('button')].find(b=>b.textContent.trim()===text||b.querySelector('span')?.textContent.trim()===text);check(b&&!b.disabled,'Missing enabled button: '+text);b.click();};
 async function apply(){click('Apply');await wait(()=>!document.querySelector('.sheet-heading'),'Apply did not finish');}
 async function cancel(){click('Cancel');await wait(()=>!document.querySelector('.sheet-heading'),'Cancel did not finish');}
 async function input(id,value){const el=document.getElementById('range-'+id);check(el,'Missing slider '+id);el.value=value;el.dispatchEvent(new Event('input',{bubbles:true}));await delay(150);}
 window.MobilePhotoCheck={
  state,wait,click,input,apply,cancel,
  async prepare(){
   await wait(()=>document.querySelector('.tool-grid'),'No tool grid');
   const initial=await state();check(initial.width===320&&initial.height===240,'Wrong import size');
   click('Tune image');await wait(()=>document.querySelector('#range-brightness'),'No brightness slider');await input('brightness',24);await apply();
   const tuned=await state();check(tuned.layers[0].params.brightness===24,'Brightness was not applied as a layer');
   click('Colour');await wait(()=>document.querySelector('#range-vibrance'),'No colour sliders');await input('vibrance',55);await cancel();
   check((await state()).layers.length===tuned.layers.length,'Cancel left an adjustment behind');
   document.querySelector('[data-tab=looks]').click();click('Warm');await wait(()=>document.querySelector('#range-strength'),'No Look strength');await input('strength',65);await apply();
   let current=await state();check(current.layers[0].adjustmentKind==='colorLookup'&&Math.abs(current.layers[0].opacity-.65)<.001,'Look is not editable');
   document.querySelector('#undo').click();await wait(async()=>(await state()).layers.length===tuned.layers.length,'Look must undo in one step');document.querySelector('#redo').click();await wait(async()=>(await state()).layers.length===current.layers.length,'Redo lost Look');
   document.querySelector('[data-tab=edits]').click();document.querySelector('[aria-label="Brush mask for '+current.layers[0].name+'"]').click();await wait(()=>document.querySelector('#range-size'),'No mask brush');beforeMask=await pixels();
   return true;
  },
  point(){const r=document.querySelector('#touch').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2};},
  async afterMask(){await apply();check(await pixels()!==beforeMask,'The touch mask stroke did not change rendered pixels');const s=await state();check(s.layers[0].hasMask,'Painted mask missing');document.querySelector('[data-tab=tools]').click();click('Crop & rotate');await wait(()=>document.querySelector('.crop-ratios'),'No crop tool');click('1:1');await apply();const cropped=await state();check(cropped.width===240&&cropped.height===240,'Crop failed');return cropped.layers;},
  async reopened(expected){const s=await state();check(s.width===240&&s.height===240,'Crop size lost');check(JSON.stringify(s.layers)===JSON.stringify(expected),'Editable layers changed after reopen');return true;},
  layout(){const c=document.querySelector('#stage').getBoundingClientRect(),tabs=document.querySelector('#tabs').getBoundingClientRect();check(c.height>=100,'Photo canvas clipped');check(tabs.bottom<=innerHeight+1,'Bottom controls clipped');return true;}
 };
})()
