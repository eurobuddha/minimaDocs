'use strict';
// Loaded before WASM. The only boundary is same-origin memory; it grants no disk access.
(()=>{
 let sequence=0,resolveReady;const pending=new Map();
 const ready=new Promise(resolve=>{resolveReady=resolve;});
 const client=window.photoCraft={ready,onEvent:()=>{},request(method,params={},bytes=new Uint8Array()){
  const id=++sequence;
  return new Promise((resolve,reject)=>{
   const timer=setTimeout(()=>{pending.delete(id);reject(Error('PhotoCraft did not respond. Your previous saved version is safe.'));},180000);
   pending.set(id,{resolve,reject,timer});
   window.dispatchEvent(new CustomEvent('minimadocs:request',{detail:{json:JSON.stringify({id,method,...params}),bytes:new Uint8Array(bytes)}}));
  });
 }};
 window.addEventListener('minimadocs:response',event=>{
  const {kind,json,bytes}=event.detail;let value;try{value=JSON.parse(json);}catch{return;}
  if(kind==='ready'){resolveReady();return;}
  if(kind==='result'){
   const request=pending.get(value.id);if(!request)return;pending.delete(value.id);clearTimeout(request.timer);
   if(value.error)request.reject(Error(value.error));else request.resolve({value:value.value,bytes});
  }else client.onEvent(kind,value,bytes);
 });
})();
