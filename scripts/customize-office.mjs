// Extend the pinned engine with Manrope using its tested catalog/selection helpers.
import {readFileSync,writeFileSync} from 'node:fs';
import {resolve,dirname} from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const engine=resolve(process.argv[2]||'android/build/office-engine');
const {decode,encode,buildRecord}=await import(pathToFileURL(resolve(engine,'bin/lib/selection-bin.mjs')));
const {xorPrefix}=await import(pathToFileURL(resolve(engine,'bin/lib/sfnt.mjs')));
const catalog=resolve(engine,'public/sdkjs/common/AllFonts.js');
let source=readFileSync(catalog,'utf8');
const array=(name)=>{const marker=`window["${name}"] = [`;const start=source.indexOf(marker)+marker.length;return JSON.parse('['+source.slice(start,source.indexOf('];',start))+']');};
const files=array('__fonts_files'),infos=array('__fonts_infos');
if(!infos.some(row=>row[0]==='Manrope')){
  const marker='window["g_fonts_selection_bin"] = "';const start=source.indexOf(marker)+marker.length;
  const selection=decode(source.slice(start,source.indexOf('"',start)));const position=files.length;
  for(const weight of ['regular','bold']){
    const name='manrope-'+weight;const face=readFileSync(resolve(root,'android/app/src/main/assets/workbench/fonts/manrope_'+weight+'.ttf'));
    writeFileSync(resolve(engine,'public/fonts/'+name),xorPrefix(face));files.push(name);
    selection.records.push(buildRecord(face,{path:'/fonts/'+name+'.ttf'}));
  }
  infos.push(['Manrope',position,0,-1,-1,position+1,0,-1,-1]);
  for(const [name,rows] of [['__fonts_files',files],['__fonts_infos',infos]]){
    const marker=`window["${name}"] = [`;const start=source.indexOf(marker)+marker.length;const end=source.indexOf('];',start);
    source=source.slice(0,start)+'\n'+rows.map(row=>JSON.stringify(row)).join(',\n')+'\n'+source.slice(end);
  }
  source=source.replace(/(window\["g_fonts_selection_bin"\] = ")[^"]+/,(_,prefix)=>prefix+encode(selection));writeFileSync(catalog,source);
}
const pdf=resolve(engine,'packages/converter/src/pdf-fonts.ts');let converter=readFileSync(pdf,'utf8');
if(!converter.includes("file: 'manrope-regular'")){
  const anchor="  { file: '062', aliases:";if(!converter.includes(anchor))throw Error('Pinned PDF manifest changed');
  converter=converter.replace(anchor,"  { file: 'manrope-regular', aliases: ['Manrope.ttf', 'Manrope-Regular.ttf'] },\n  { file: 'manrope-bold', aliases: ['Manrope-Bold.ttf'] },\n"+anchor);writeFileSync(pdf,converter);
}
console.log('Manrope catalog, font matcher and PDF manifest registered. Run upstream font-thumbnails.mjs before building.');
