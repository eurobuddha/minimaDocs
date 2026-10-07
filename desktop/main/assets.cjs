'use strict';
const fs=require('node:fs/promises'),path=require('node:path');
const {createReadStream}=require('node:fs'),{Readable}=require('node:stream');
const mime={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.json':'application/json','.svg':'image/svg+xml','.png':'image/png','.jpg':'image/jpeg','.webp':'image/webp','.ttf':'font/ttf','.woff':'font/woff','.woff2':'font/woff2','.wasm':'application/wasm','.br':'application/wasm','.map':'application/json'};
const WORKSPACE='https://workspace.minimadocs.local',EDITOR='https://editors.minimadocs.local';
function target(url,roots){
  const u=new URL(url);if(u.protocol!=='https:'||u.port||![WORKSPACE,EDITOR].includes(u.origin))return null;
  let pathname;try{pathname=decodeURIComponent(u.pathname);}catch(_){return null;}
  if(pathname.includes('\\')||pathname.includes('\0')||pathname.split('/').some(p=>p==='..'||p==='.')||/\/(sw|document_editor_service_worker)\.js$/.test(pathname))return null;
  let root=roots.editors,relative=pathname.slice(1);
  if(u.origin===WORKSPACE){root=pathname.startsWith('/fonts/')?roots.workbench:roots.renderer;relative=relative||'index.html';}
  else if(pathname.startsWith('/desktop/')){root=roots.renderer;relative=pathname.slice(9);}
  else if(pathname.startsWith('/workbench/')){root=roots.workbench;relative=pathname.slice(11);}
  else if(/^\/fonts\/manrope-(regular|bold)$/.test(pathname)){root=roots.workbench;relative='fonts/'+path.basename(pathname);}
  else if(pathname==='/editor')relative='editor.html';
  const file=path.resolve(root,relative);if(!file.startsWith(path.resolve(root)+path.sep))return null;
  return {file,pathname,origin:u.origin};
}
function handler(roots){return async request=>{
  const t=target(request.url,roots);if(!t||request.method!=='GET')return new Response('Unavailable',{status:403});
  try {
    if(!(await fs.stat(t.file)).isFile())return new Response('Not found',{status:404});
    const headers={'Content-Type':mime[path.extname(t.file)]||'application/octet-stream','Cache-Control':'no-store','Content-Security-Policy':"default-src 'self' data: blob:; script-src 'self' 'unsafe-inline' 'unsafe-eval' blob:; style-src 'self' 'unsafe-inline'; connect-src 'self' blob:; frame-src 'self' blob:; worker-src 'self' blob:; object-src 'none'; base-uri 'self'; form-action 'none'"};
    if(t.file.endsWith('.html')){
      let html=await fs.readFile(t.file,'utf8');
      if(t.origin===EDITOR)html=html.replace(/<head>/i,'<head><script src="/workbench/memory-storage.js"></script><link rel="stylesheet" href="/workbench/typography.css">');
      if(t.pathname==='/workbench/index.html')html=html.replace('</head>','<link rel="stylesheet" href="/desktop/editor.css"></head>').replace('<body>','<body><header id="desktop-bar"><button id="files">Files</button><input id="title" maxlength="100" aria-label="Document title"><span id="save-status" role="status">Opening editor…</span><button id="save" disabled>Save</button><button id="copy" disabled>Save a copy</button><button id="export" disabled>Export</button><button id="share" disabled class="primary">Share</button></header>').replace('</body>','<script src="/desktop/editor-ui.js"></script></body>');
      return new Response(html,{headers});
    }
    return new Response(Readable.toWeb(createReadStream(t.file)),{headers});
  }catch(_){return new Response('Not found',{status:404});}
};}
module.exports={target,handler,WORKSPACE,EDITOR};
