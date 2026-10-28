import { spawn, execFileSync } from 'node:child_process';
const EXE='D:/Program File/Pencil/resources/app.asar.unpacked/out/mcp-server-windows-x64.exe';
const child=spawn(EXE,['--app','desktop'],{stdio:['pipe','pipe','pipe']});
let buf=''; const pending=new Map();
child.stdout.on('data',d=>{buf+=d.toString();let i;while((i=buf.indexOf('\n'))>=0){const l=buf.slice(0,i).trim();buf=buf.slice(i+1);if(!l)continue;try{const m=JSON.parse(l);if(m.id&&pending.has(m.id)){pending.get(m.id)(m);pending.delete(m.id);}}catch{}}});
const req=(method,params,id)=>new Promise(r=>{pending.set(id,r);child.stdin.write(JSON.stringify({jsonrpc:'2.0',id,method,params})+'\n')});
const BRIGHT=`import struct,zlib,sys
p=sys.argv[1]
d=open(p,'rb').read(); pos=8; idat=b''
while pos<len(d):
    ln=struct.unpack('>I',d[pos:pos+4])[0]; typ=d[pos+4:pos+8]; data=d[pos+8:pos+8+ln]
    if typ==b'IHDR': w,h,bd,ct=struct.unpack('>IIBB',data[:10])
    if typ==b'IDAT': idat+=data
    pos+=12+ln
raw=zlib.decompress(idat); bpp=4; stride=w*bpp
out=bytearray(); prev=bytearray(stride); o=0
for y in range(h):
    f=raw[o]; o+=1; line=bytearray(raw[o:o+stride]); o+=stride
    if f==1:
        for i in range(bpp,stride): line[i]=(line[i]+line[i-bpp])&255
    elif f==2:
        for i in range(stride): line[i]=(line[i]+prev[i])&255
    elif f==3:
        for i in range(stride): line[i]=(line[i]+((line[i-bpp] if i>=bpp else 0)+prev[i])//2)&255
    elif f==4:
        for i in range(stride):
            a=line[i-bpp] if i>=bpp else 0; b=prev[i]; c=prev[i-bpp] if i>=bpp else 0
            pa=abs(b-c); pb=abs(a-c); pc=abs(a+b-2*c)
            pr=a if (pa<=pb and pa<=pc) else (b if pb<=pc else c)
            line[i]=(line[i]+pr)&255
    out+=line; prev=line
print(sum(1 for i in range(0,len(out),4) if out[i]>120 and out[i+1]>120))`;
let id=1;
await req('initialize',{protocolVersion:'2024-11-05',capabilities:{},clientInfo:{name:'x',version:'1'}},id++);
child.stdin.write(JSON.stringify({jsonrpc:'2.0',method:'notifications/initialized'})+'\n');
const call=async(n,i)=>{const r=await req('tools/call',{name:n,arguments:i},id++);return (r.result?.content||[]).filter(x=>x.type==='text').map(x=>x.text).join('\n');};
for(let k=0;k<8;k++){
  const out=await call('export_nodes',{filePath:'pencli/UI_Desgin.pen',nodeIds:['b98bBV'],outputDir:'pencli/.work/dx',format:'png',scale:1});
  const m=[...out.matchAll(/([A-Za-z]:[^\n]+?\.png)/g)].pop();
  const b=m?execFileSync('python',['-c',BRIGHT,m[1].trim()],{encoding:'utf8'}).trim():'nopath';
  console.log('attempt',k+1,'bright',b);
  await new Promise(r=>setTimeout(r,4000));
}
child.kill();process.exit(0);
