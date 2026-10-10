const {test}=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
// Exercise the real main-process validator, using the shared bridge tests' VM pattern.
const source=fs.readFileSync(path.join(__dirname,'../main/main.cjs'),'utf8');
const MAX=16*1024*1024;
const bytes=vm.runInNewContext('const MAX='+MAX+';'+source.slice(source.indexOf('function bytes(encoded)'),source.indexOf('async function atomicWrite'))+';bytes',{Buffer});
test('large valid files retain exact bytes through the 16 MiB boundary',()=>{
 for(const size of [1,2,3,4*1024*1024,8*1024*1024,MAX]){
  const original=Buffer.alloc(size,173);assert.deepEqual(bytes(original.toString('base64')),original);
 }
});
test('invalid or oversized file encodings cannot reach the backend',()=>{
 for(const encoded of [null,42,{},'', 'Zg','Zg===','Z=g=','Zg==AAAA','Zg==\n','____','Zh==','Zm9=',Buffer.alloc(MAX+1).toString('base64')])
  assert.throws(()=>bytes(encoded));
});
