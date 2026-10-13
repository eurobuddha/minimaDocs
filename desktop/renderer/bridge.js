'use strict';
// Desktop dispatch only; the Android workbench stays unchanged.
const bridge=document.createElement('script');
bridge.src=JSON.parse(MinimaDocs.bootstrap()).photocraft?'/desktop/photocraft.js':'/workbench/bridge.js';
bridge.onerror=()=>MinimaDocs.error('The editor bridge could not load.');
document.head.appendChild(bridge);
