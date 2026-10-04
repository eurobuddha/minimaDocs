// Assemble offline assets from pinned, inspected sources. No downloads at app runtime.
const fs=require('node:fs'),path=require('node:path'),zlib=require('node:zlib'),cp=require('node:child_process');
const root=path.resolve(__dirname,'..');
const office=path.resolve(process.argv[2]||path.join(root,'android/build/office-engine'));
const image=path.resolve(process.argv[3]||path.join(root,'../image-engine'));
const imageCommit='a79733eb803fc97084ef0ee4faa96b031e69e1c0';
if(cp.execFileSync('git',['rev-parse','HEAD'],{cwd:image,encoding:'utf8'}).trim()!==imageCommit)throw Error('Image source revision differs from the reviewed revision');
const dest=path.join(root,'android/app/src/main/assets/editors');
if(!fs.existsSync(path.join(office,'dist/editor.html')))throw Error('Build the pinned office engine first; see .github/workflows/office-engine.yml');
fs.rmSync(dest,{recursive:true,force:true});
fs.mkdirSync(dest,{recursive:true});
fs.cpSync(path.join(office,'dist'),dest,{recursive:true});
fs.copyFileSync(path.join(office,'LICENSE'),path.join(dest,'OFFICE-LICENSE'));
fs.copyFileSync(path.join(office,'NOTICE'),path.join(dest,'OFFICE-NOTICE'));
const paint=path.join(dest,'image');fs.mkdirSync(paint,{recursive:true});
for(const file of ['index.html','dist','src','images','MIT-LICENSE.txt'])fs.cpSync(path.join(image,file),path.join(paint,file),{recursive:true});
// WebView interception supplies decoded bodies; unpack the converter once at packaging time.
const wasm=path.join(dest,'sdkjs/common/wasm/x2t/x2t.wasm.br');
fs.writeFileSync(wasm,zlib.brotliDecompressSync(fs.readFileSync(wasm)));
fs.writeFileSync(path.join(dest,'minimadocs-engines.json'),JSON.stringify({office:'9c743826d0152239dc7ad51677535d59679c7ff1',image:imageCommit},null,2));
console.log('Offline office and image assets prepared.');
