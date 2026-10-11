/* Phone sheet adapted from Atelier minidapp/filtr-sheet.js.
 * Move the real controls, preserving listeners and engine state; restore their
 * exact homes when a foldable/window becomes wide. No second editor or schema. */
'use strict';
(() => {
 const $=s=>document.querySelector(s),wrapper=$('.wrapper'),gui=ImageEditor.GUI;
 const categories={Tools:['#tools_container','#action_attributes'],Studio:['.studio-panel'],Layers:['.block.layers','#details_base'],Colour:['.colors'],History:['.studio-history','.preview']};
 const sheet=document.createElement('section');sheet.className='studio-sheet';sheet.hidden=true;
 sheet.innerHTML='<header><strong id="studio-sheet-title"></strong><button type="button" aria-label="Close image controls">Done</button></header><div class="studio-sheet-body"></div>';
 const bar=document.createElement('nav');bar.className='studio-catbar';bar.setAttribute('aria-label','Image workspace');
 wrapper.append(sheet,bar);
 let on=false,active=null;const homes=new Map(),slots=new Map(),buttons=new Map();
 function refit(){requestAnimationFrame(()=>{gui.prepare_canvas();gui.GUI_preview.zoom_auto();gui.check_canvas_offset();});}
 function close(){sheet.hidden=true;active=null;for(const b of buttons.values())b.setAttribute('aria-expanded','false');refit();}
 function open(name){
  active=name;for(const [key,slot]of slots)slot.hidden=key!==name;
  $('#studio-sheet-title').textContent=name;sheet.hidden=false;
  for(const [key,b]of buttons)b.setAttribute('aria-expanded',String(key===name));refit();
 }
 // Filtr's record-home/move/restore lifecycle, with a placeholder for each
 // original position so sibling controls can be restored in any order.
 function move(selector,into){const el=$(selector);if(!el)return;const home=document.createComment('Image Studio control home');el.before(home);homes.set(el,home);into.append(el);}
 for(const name of Object.keys(categories)){
  const slot=document.createElement('div');slot.className='studio-slot';slot.id='studio-slot-'+name.toLowerCase();slot.hidden=true;$('.studio-sheet-body').append(slot);slots.set(name,slot);
  const b=document.createElement('button');b.type='button';b.textContent=name;b.setAttribute('aria-controls',slot.id);b.setAttribute('aria-expanded','false');b.onclick=()=>active===name?close():open(name);bar.append(b);buttons.set(name,b);
 }
 sheet.querySelector('header button').onclick=close;
 function sync(){
  const narrow=window.innerWidth<=900;
  if(narrow&&!on){for(const [name,selectors]of Object.entries(categories))for(const selector of selectors)move(selector,slots.get(name));on=true;}
  else if(!narrow&&on){close();for(const [el,home]of homes){home.replaceWith(el);}homes.clear();on=false;}
  refit();
 }
 // Select a tool and return to the image. Its settings remain in Tools.
 $('#tools_container').addEventListener('click',()=>{if(on)close();});
 $('.studio-tabpanel#studio-retouch').addEventListener('click',()=>{if(on)close();});
 $('.mask-actions').addEventListener('click',e=>{if(on&&e.target.dataset.maskMode)close();});
 window.addEventListener('resize',sync);sync();
})();
