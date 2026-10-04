// Editor preferences are session-only. Document bytes belong to the encrypted notebook.
(() => {
  function memory() {
    const data = new Map();
    return {get length(){return data.size;},key:n=>Array.from(data.keys())[n] ?? null,
      getItem:key=>data.get(String(key)) ?? null,setItem:(key,value)=>data.set(String(key),String(value)),
      removeItem:key=>data.delete(String(key)),clear:()=>data.clear()};
  }
  Object.defineProperty(window,'localStorage',{value:memory()});
  Object.defineProperty(window,'sessionStorage',{value:memory()});
  Object.defineProperty(window,'indexedDB',{value:undefined});
  // Both bundled office entry points test `serviceWorker in navigator`.
  // APK assets do not need a service worker or its persistent cache.
  if(typeof navigator!=='undefined') {
    for(let object=navigator;object;object=Object.getPrototypeOf(object)) {
      if(Object.getOwnPropertyDescriptor(object,'serviceWorker')?.configurable)delete object.serviceWorker;
    }
  }
})();
