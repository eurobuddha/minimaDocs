// Build the pinned miniPaint engine with the workbench API entry; do not modify its source.
const path=require('node:path'),cp=require('node:child_process');
const root=path.resolve(__dirname,'..'),source=path.resolve(root,'../image-engine');
if(cp.execFileSync('git',['rev-parse','HEAD'],{cwd:source,encoding:'utf8'}).trim()!=='a79733eb803fc97084ef0ee4faa96b031e69e1c0')throw Error('Unexpected image engine revision');
const webpack=require(path.join(source,'node_modules/webpack'));
const config=require(path.join(source,'webpack.config.js'));
config.module.rules.push({test:/[\\/]actions[\\/]store[\\/]image-store\.js$/,enforce:'pre',use:[path.join(__dirname,'image-source-loader.cjs')]});
config.context=source;config.mode='production';config.entry=[path.join(source,'src/js/main.js'),path.join(__dirname,'image-entry.js')];
config.output={...config.output,path:path.join(root,'android/app/src/main/assets/editors/image/dist')};
webpack(config,(error,stats)=>{if(error||stats.hasErrors()){console.error(error||stats.toString({all:false,errors:true}));process.exitCode=1;}else console.log('Image engine and workbench API built.');});
