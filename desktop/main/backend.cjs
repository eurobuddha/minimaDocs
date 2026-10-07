'use strict';
const {spawn}=require('node:child_process');
const path=require('node:path');
const MAX=24*1024*1024;
class Backend {
  constructor(java,classpath,directory){
    this.pending=new Map();this.seq=0;this.buffer=Buffer.alloc(0);this.dead=false;
    this.child=spawn(java,['-Xmx512m','-Djava.awt.headless=true','-cp',path.join(classpath,'*'),'org.mininotes.android.MacBackend',directory],{stdio:['pipe','pipe','pipe']});
    // Transport logs may contain addresses. Keep them out of Electron's console.
    this.child.stderr.resume();
    this.child.stdout.on('data',chunk=>{
      this.buffer=Buffer.concat([this.buffer,chunk]);
      while(this.buffer.length>=4){
        const size=this.buffer.readUInt32BE(0);if(size<1||size>MAX){this.fail(new Error('The document backend returned an invalid response.'));this.child.kill();return;}
        if(this.buffer.length<size+4)break;
        let message;try{message=JSON.parse(this.buffer.subarray(4,size+4).toString());}catch(_){this.fail(new Error('The document backend returned an unreadable response.'));this.child.kill();return;}
        this.buffer=this.buffer.subarray(size+4);const job=this.pending.get(message.id);if(!job)continue;
        clearTimeout(job.timer);this.pending.delete(message.id);if(message.error)job.reject(new Error(message.error));else job.resolve(message.result);
      }
    });
    this.child.on('error',()=>this.fail(new Error('Could not start the bundled document backend.')));
    this.child.on('exit',()=>this.fail(new Error('The document backend stopped. Your saved files are safe; restart minimaDocs.')));
  }
  fail(error){this.dead=true;for(const p of this.pending.values()){clearTimeout(p.timer);p.reject(error);}this.pending.clear();}
  call(method,params={}){
    if(this.dead)return Promise.reject(new Error('The document backend is unavailable. Restart minimaDocs.'));
    const id=++this.seq,body=Buffer.from(JSON.stringify({id,method,params}));
    if(body.length>MAX)return Promise.reject(new Error('The file exceeds the 16 MiB limit.'));
    const header=Buffer.alloc(4);header.writeUInt32BE(body.length);
    return new Promise((resolve,reject)=>{
      const timer=setTimeout(()=>{this.pending.delete(id);reject(new Error('The operation timed out. Check the saved document before retrying.'));},180000);
      this.pending.set(id,{resolve,reject,timer});this.child.stdin.write(Buffer.concat([header,body]),e=>{if(e){clearTimeout(timer);this.pending.delete(id);reject(e);}});
    });
  }
  async close(){
    if(this.dead)return;
    this.child.stdin.end();
    await new Promise(resolve=>{const timer=setTimeout(()=>{this.child.kill();resolve();},5000);this.child.once('exit',()=>{clearTimeout(timer);resolve();});});
  }
}
module.exports={Backend,MAX};
