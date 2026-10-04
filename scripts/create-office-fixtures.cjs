// Reuse the pinned office engine's OOXML test builders. Python supplies ZIP
// packaging in this development helper, without installing a second JS toolchain.
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),cp=require('node:child_process');
const {stripTypeScriptTypes}=require('node:module');
const root=path.resolve(__dirname,'..'),engine=path.resolve(process.argv[2]||path.join(root,'../office-engine'));
if(cp.execFileSync('git',['rev-parse','HEAD'],{cwd:engine,encoding:'utf8'}).trim()!=='9c743826d0152239dc7ad51677535d59679c7ff1')throw Error('Use the pinned office engine');
const context={Buffer,createZip:entries=>entries};
const source=fs.readFileSync(path.join(engine,'test/e2e/lib/ooxml.ts'),'utf8').replace(/^import .*?;$/m,'').replace(/^export /gm,'');
vm.runInNewContext(stripTypeScriptTypes(source),context);
const documents={
  'compatibility.docx':context.buildDocx('',
    '<w:p><w:r><w:rPr><w:b/><w:sz w:val="32"/></w:rPr><w:t>minimaDocs compatibility</w:t></w:r></w:p>'+
    '<w:p><w:r><w:t>Body text: alpha, beta, gamma.</w:t></w:r></w:p>'+
    '<w:tbl><w:tblPr><w:tblW w:w="5000" w:type="pct"/></w:tblPr><w:tblGrid><w:gridCol w:w="3000"/><w:gridCol w:w="3000"/></w:tblGrid>'+
    '<w:tr><w:tc><w:p><w:r><w:t>Item</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>Amount</w:t></w:r></w:p></w:tc></w:tr>'+
    '<w:tr><w:tc><w:p><w:r><w:t>Apples</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>12</w:t></w:r></w:p></w:tc></w:tr></w:tbl>',
    {headerText:'minimaDocs header',footerText:'minimaDocs footer'}),
  'compatibility.xlsx':context.buildXlsx({rows:[['Item','Amount'],['Apples',12],['Pears',8],['Total',20]],freeze:{rows:1,cols:0},autoFilterRef:'A1:B3'})
};
const entries=documents['compatibility.xlsx'];
const sheet=entries.find(e=>e.name==='xl/worksheets/sheet1.xml');
sheet.data=sheet.data.replace('<c r="B4"><v>20</v></c>','<c r="B4"><f>SUM(B2:B3)</f><v>20</v></c>');
const dest=path.join(root,'android/build/fixtures');fs.mkdirSync(dest,{recursive:true});
cp.execFileSync('python3',['-c',
  'import json,sys,zipfile,pathlib\ndata=json.load(sys.stdin)\nfor name,entries in data.items():\n with zipfile.ZipFile(pathlib.Path(sys.argv[1])/name,"w",zipfile.ZIP_STORED) as z:\n  for e in entries:z.writestr(e["name"],e["data"])',dest],{input:JSON.stringify(documents)});
console.log(dest);
