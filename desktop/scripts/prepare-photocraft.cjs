'use strict';
// Reuse the pinned upstream shell, apply the reviewed patch, and package its offline WASM.
const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process'),crypto=require('node:crypto');
const desktop=path.resolve(__dirname,'..'),adapter=path.join(desktop,'photocraft'),pin=require('../photocraft/pin.json');
const source=path.resolve(process.env.PHOTOCRAFT_SOURCE||path.join(desktop,'../../photocraft-engine'));
const digest=data=>crypto.createHash('sha256').update(data).digest('hex');
function inputHash(){return digest(['pin.json','upstream.patch','minima_bridge.rs','minima_mobile.rs'].map(f=>fs.readFileSync(path.join(adapter,f))).reduce((a,b)=>Buffer.concat([a,b]),Buffer.alloc(0)));}
const out=path.join(desktop,'build/photocraft');
function verify(){
 const manifest=JSON.parse(fs.readFileSync(path.join(out,'minimadocs-engine.json'),'utf8'));
 if(manifest.revision!==pin.revision||manifest.adapter!==inputHash())throw Error('PhotoCraft assets are stale: run npm run prepare:photocraft.');
 for(const [name,hash]of Object.entries(manifest.files))if(digest(fs.readFileSync(path.join(out,name)))!==hash)throw Error('PhotoCraft asset changed: '+name);
 if(!manifest.files['index.html']||!Object.keys(manifest.files).some(f=>f.endsWith('.wasm')))throw Error('PhotoCraft engine is incomplete.');
 return manifest;
}
if(require.main===module){
 const run=(args,options={})=>cp.execFileSync('git',args,{cwd:source,encoding:'utf8',...options});
 if(!fs.existsSync(path.join(source,'.git')))throw Error('Clone '+pin.repository+' at '+pin.revision+' into '+source+' first.');
 if(run(['rev-parse','HEAD']).trim()!==pin.revision)throw Error('Unexpected PhotoCraft revision; preserve the checkout and use the pinned source.');
 const patch=fs.readFileSync(path.join(adapter,'upstream.patch'),'utf8'),diff=run(['diff','--binary','HEAD']);
 if(diff&&diff!==patch)throw Error('PhotoCraft has other source changes. They have been preserved.');
 if(!diff){run(['apply','--check','-'],{input:patch});run(['apply','-'],{input:patch});}
 for(const name of ['minima_bridge.rs','minima_mobile.rs']){
  const bridge=path.join(source,'apps/photocraft-web/src',name),expected=fs.readFileSync(path.join(adapter,name));
  if(fs.existsSync(bridge)&&!fs.readFileSync(bridge).equals(expected))throw Error('PhotoCraft host adapter has other edits. They have been preserved: '+name);
  fs.writeFileSync(bridge,expected);
 }
 if(!process.argv.includes('--stage')){
  const env={...process.env,RUSTUP_TOOLCHAIN:pin.rust};delete env.NO_COLOR;
  const cargoBin=path.join(require('node:os').homedir(),'.cargo/bin');env.PATH=cargoBin+path.delimiter+env.PATH;
  const local=path.join(desktop,'build/photocraft-tools/trunk'),trunk=process.env.PHOTOCRAFT_TRUNK||(fs.existsSync(local)?local:'trunk');
  if(!cp.execFileSync(trunk,['--version'],{env,encoding:'utf8'}).includes(pin.trunk))throw Error('Use Trunk '+pin.trunk);
  cp.execFileSync(trunk,['build','--release','--config',path.join(source,'apps/photocraft-web/Trunk.toml')],{env,stdio:'inherit'});
 }
 const built=path.join(source,'dist/web');
 // Only replace this generated engine directory, never a source checkout or other build outputs.
 fs.rmSync(out,{recursive:true,force:true});fs.cpSync(built,out,{recursive:true});
 for(const name of ['LICENSE-MIT','LICENSE-APACHE','NOTICE','ATTRIBUTION.md','README.md'])fs.copyFileSync(path.join(source,name),path.join(out,name));
 for(const directory of ['assets/fonts','assets/icons','assets/dict','assets/app-icon']){
  for(const name of fs.readdirSync(path.join(source,directory)))if(/^(LICENSE|OFL)/.test(name)){
   const relative=path.join(directory,name);fs.mkdirSync(path.dirname(path.join(out,relative)),{recursive:true});fs.copyFileSync(path.join(source,relative),path.join(out,relative));
  }
 }
 const files={};const walk=dir=>{for(const entry of fs.readdirSync(dir,{withFileTypes:true})){const file=path.join(dir,entry.name);if(entry.isDirectory())walk(file);else files[path.relative(out,file)]=digest(fs.readFileSync(file));}};walk(out);
 fs.writeFileSync(path.join(out,'minimadocs-engine.json'),JSON.stringify({version:pin.version,revision:pin.revision,adapter:inputHash(),files},null,2)+'\n');
 verify();console.log('Prepared pinned PhotoCraft '+pin.version+' for minimaDocs desktop.');
}
module.exports={verify,inputHash};
