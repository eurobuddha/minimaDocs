'use strict';
const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process');
const root=path.resolve(__dirname,'..'),repo=path.resolve(root,'..');
const run=(cmd,args)=>cp.execFileSync(cmd,args,{stdio:'inherit',cwd:root});
const jdk=process.env.JPACKAGE_JDK||cp.execFileSync('/usr/libexec/java_home',[],{encoding:'utf8'}).trim();
if(!fs.existsSync(path.join(repo,'android/app/src/main/assets/editors/editor.html')))throw Error('Prepare the existing offline editor assets before building the desktop app.');
const version=require('../package.json').version;
if(!fs.existsSync(path.join(root,'backend/build/package-input/minimaDocs-backend-'+version+'.jar')))throw Error('Run the backend tests and packageInput Gradle task first.');
const runtime=path.join(root,'build/runtime');
if(!fs.existsSync(runtime)){
 const modules=['java.base','java.desktop','java.sql','java.logging','java.naming','jdk.unsupported','jdk.crypto.ec'].filter(m=>fs.existsSync(path.join(jdk,'jmods',m+'.jmod')));
 run(path.join(jdk,'bin/jlink'),['--add-modules',modules.join(','),'--strip-debug','--no-header-files','--no-man-pages','--compress=zip-6','--output',runtime]);
}
// Exact existing Android launcher paths; no stock desktop icon.
const xml=fs.readFileSync(path.join(repo,'android/app/src/main/res/drawable/ic_launcher_foreground.xml'),'utf8');
const args=['-size','2048x2048','xc:#F2F1EC'];
for(const p of xml.matchAll(/<path\s+([^>]+)\/>/g)){
 const a=Object.fromEntries([...p[1].matchAll(/android:(\w+)="([^"]*)"/g)].map(m=>[m[1],m[2]]));
 args.push('-fill',a.fillColor||'none','-stroke',a.strokeColor||'none','-strokewidth',a.strokeWidth||'0','-draw',`scale ${2048/108},${2048/108} path '${a.pathData}'`);
}
args.push('-resize','1024x1024','-depth','8','PNG32:'+path.join(root,'build/icon.png'));run('magick',args);
console.log('Mac runtime, offline engines and minimaDocs icon prepared.');
