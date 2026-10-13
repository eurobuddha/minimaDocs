'use strict';
const {contextBridge,ipcRenderer}=require('electron');
const config=ipcRenderer.sendSync('editor:bootstrap');
contextBridge.exposeInMainWorld('MinimaDocs',{
  bootstrap:()=>JSON.stringify(config),
  ready:()=>ipcRenderer.send('editor:event','ready'),
  used:()=>{},
  changed:()=>ipcRenderer.send('editor:event','changed'),
  error:message=>ipcRenderer.send('editor:event','error',String(message).slice(0,1000)),
  saved:base64=>ipcRenderer.invoke('editor:saved',base64).then(result=>({ok:true,result})).catch(e=>({error:e.message})),
  exported:(base64,type)=>ipcRenderer.invoke('editor:exported',base64,type).catch(()=>{})
});
contextBridge.exposeInMainWorld('desktopEditor',{
  exportPhotoCraft:(base64,name)=>ipcRenderer.invoke('editor:photocraft-export',base64,name),
  action:(name,value)=>ipcRenderer.invoke('editor:action',name,value),
  onStatus:fn=>ipcRenderer.on('editor:status',(_e,state)=>fn(state))
});
