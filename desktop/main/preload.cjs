'use strict';
const {contextBridge,ipcRenderer}=require('electron');
// Only the workspace's main frame gets this bridge. Editors have their own scoped contract.
contextBridge.exposeInMainWorld('docs',{
  call:(method,params)=>ipcRenderer.invoke('workspace:call',method,params),
  onRefresh:fn=>{const listener=()=>fn();ipcRenderer.on('workspace:refresh',listener);return()=>ipcRenderer.removeListener('workspace:refresh',listener);},
  onShare:fn=>ipcRenderer.on('workspace:share',(_e,id)=>fn(id)),
  onInvitation:fn=>ipcRenderer.on('workspace:invitation',(_e,text)=>fn(text))
});
