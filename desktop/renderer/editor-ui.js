'use strict';
(() => {
 const config=JSON.parse(MinimaDocs.bootstrap()),title=document.getElementById('title'),status=document.getElementById('save-status');
 title.value=config.title;title.disabled=!!config.readonly;
 title.addEventListener('input',()=>desktopEditor.action('title',title.value));
 for(const name of ['files','save','copy','export','share'])document.getElementById(name).addEventListener('click',()=>desktopEditor.action(name).catch(e=>{status.textContent=e.message;}));
 desktopEditor.onStatus(state=>{status.textContent=state.message;title.disabled=state.readonly;document.getElementById('save').disabled=!state.ready||state.readonly||state.saving;document.getElementById('export').disabled=!state.ready||state.saving;document.getElementById('copy').disabled=!state.ready||state.saving;document.getElementById('share').disabled=!state.ready||state.saving;});
})();
