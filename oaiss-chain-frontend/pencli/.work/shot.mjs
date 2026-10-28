import { spawn } from 'node:child_process';
import { writeFileSync } from 'node:fs';
const EXE='C:/Users/LiShuai/AppData/Local/Programs/Pen/resources/app.asar.unpacked/out/mcp-server-windows-x64.exe';
const nodeId=process.argv[2];
const child=spawn(EXE,['--app','desktop'],{stdio:['pipe','pipe','pipe']});
let buf=''; const pending=new Map();
child.stdout.on('data',d=>{buf+=d.toString();let i;while((i=buf.indexOf('\n'))>=0){const l=buf.slice(0,i).trim();buf=buf.slice(i+1);if(!l)continue;try{const m=JSON.parse(l);if(m.id&&pending.has(m.id)){pending.get(m.id)(m);pending.delete(m.id);}}catch{}}});
const req=(method,params,id)=>new Promise(r=>{pending.set(id,r);child.stdin.write(JSON.stringify({jsonrpc:'2.0',id,method,params})+'\n')});
let id=1;
await req('initialize',{protocolVersion:'2024-11-05',capabilities:{},clientInfo:{name:'x',version:'1'}},id++);
child.stdin.write(JSON.stringify({jsonrpc:'2.0',method:'notifications/initialized'})+'\n');
const r=await req('tools/call',{name:'get_screenshot',arguments:{filePath:'pencli/UI_Desgin.pen',nodeId}},id++);
const c=r.result?.content||[];
console.log('blocks:',c.map(b=>b.type).join(','));
c.forEach((b,i)=>{
  if(b.type==='image'){ const ext=b.mimeType?.split('/')[1]||'png'; const p=`pencli/.work/shot_${nodeId}_${i}.${ext}`; writeFileSync(p,Buffer.from(b.data,'base64')); console.log('WROTE',p); }
  else console.log(b.text?.slice(0,300));
});
child.kill();process.exit(0);
