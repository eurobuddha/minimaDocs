const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs/promises'),path=require('node:path'),os=require('node:os');
const {target,handler,WORKSPACE,EDITOR}=require('../main/assets.cjs');
const roots={renderer:'/bundle/ui',editors:'/bundle/editors',workbench:'/bundle/workbench'};
test('bundled routes stay under their asset roots',()=>{
 assert.equal(target(EDITOR+'/workbench/bridge.js',roots).file,'/bundle/workbench/bridge.js');
 assert.equal(target(WORKSPACE+'/fonts/manrope_regular.ttf',roots).file,'/bundle/workbench/fonts/manrope_regular.ttf');
 assert.equal(target(EDITOR+'/fonts/manrope-bold',roots).file,'/bundle/workbench/fonts/manrope-bold');
 for(const url of ['https://example.com/a','file:///etc/passwd',EDITOR+':999/a',EDITOR+'/%2e%2e%2fsecret',EDITOR+'/%5csecret',EDITOR+'/%00x',EDITOR+'/sw.js',EDITOR+'/sdkjs/common/serviceworker/document_editor_service_worker.js'])assert.equal(target(url,roots),null,url);
});
test('asset serving refuses writes and injects only bundled editor support',async()=>{
 const dir=await fs.mkdtemp(path.join(os.tmpdir(),'minimadocs-assets-'));try{
  await fs.writeFile(path.join(dir,'index.html'),'<html><head></head><body><iframe id="editor"></iframe></body></html>');
  const serve=handler({renderer:dir,workbench:dir,editors:dir});
  assert.equal((await serve(new Request(EDITOR+'/workbench/index.html',{method:'POST'}))).status,403);
  const response=await serve(new Request(EDITOR+'/workbench/index.html'));assert.equal(response.status,200);assert.match(response.headers.get('content-security-policy'),/connect-src 'self' blob:/);assert.equal(response.headers.get('cache-control'),'no-store');const html=await response.text();assert.match(html,/memory-storage.js/);assert.match(html,/desktop-bar/);
  assert.equal((await serve(new Request(EDITOR+'/missing'))).status,404);
 }finally{await fs.rm(dir,{recursive:true,force:true});}
});
