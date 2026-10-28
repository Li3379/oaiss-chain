// Export Pencil nodes to PNG, keeping one MCP session alive for the whole batch.
// Usage: node export.mjs <outDir> <nodeName...>
import { spawn } from 'node:child_process';
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync } from 'node:fs';

const PEN = 'pencli/UI_Desgin.pen';
const EXE = 'D:/Program File/Pencil/resources/app.asar.unpacked/out/mcp-server-windows-x64.exe';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function openSession() {
  const child = spawn(EXE, ['--app', 'desktop'], { stdio: ['pipe', 'pipe', 'pipe'] });
  let buf = '';
  const pending = new Map();
  child.stdout.on('data', (d) => {
    buf += d.toString();
    let i;
    while ((i = buf.indexOf('\n')) >= 0) {
      const line = buf.slice(0, i).trim();
      buf = buf.slice(i + 1);
      if (!line) continue;
      try { const m = JSON.parse(line); if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); } } catch {}
    }
  });
  const request = (method, params, id) => new Promise((res) => {
    pending.set(id, res);
    child.stdin.write(JSON.stringify({ jsonrpc: '2.0', id, method, params }) + '\n');
  });
  return { child, request };
}

const BRIGHT_PY = `
import struct,zlib,sys
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
print(sum(1 for i in range(0,len(out),4) if out[i]>120 and out[i+1]>120))
`;

function bright(png) {
  try { return parseInt(execFileSync('python', ['-c', BRIGHT_PY, png], { encoding: 'utf8' }).trim(), 10); }
  catch { return -1; }
}

async function main() {
  const outDir = process.argv[2];
  const names = process.argv.slice(3);
  mkdirSync(outDir, { recursive: true });
  const { child, request } = openSession();
  let id = 1;
  await request('initialize', { protocolVersion: '2024-11-05', capabilities: {}, clientInfo: { name: 'zcode', version: '1' } }, id++);
  child.stdin.write(JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized' }) + '\n');

  const call = async (name, input) => {
    const r = await request('tools/call', { name, arguments: input }, id++);
    return r.result?.content?.filter((x) => x.type === 'text').map((x) => x.text).join('\n') || JSON.stringify(r.error || {});
  };

  const listing = await call('batch_get', { filePath: PEN, patterns: names.map((n) => ({ name: n })), readDepth: 0 });
  const arr = JSON.parse(listing.slice(listing.indexOf('[')));
  const idByName = {};
  for (const n of arr) if (names.includes(n.name)) idByName[n.name] = n.id;

  const results = [];
  for (const n of names) {
    const nodeId = idByName[n] || n;
    let done = null;
    for (let attempt = 0; attempt < 5 && !done; attempt++) {
      await sleep(1200);
      const res = await call('export_nodes', { filePath: PEN, nodeIds: [nodeId], outputDir: outDir, format: 'png', scale: 1 });
      const mm = [...res.matchAll(/([A-Za-z]:[^\n]+?\.png)/g)].pop();
      if (!mm) { continue; }
      const png = mm[1].trim();
      if (!existsSync(png)) continue;
      const b = bright(png);
      if (b >= 40) done = { path: png, bright: b, attempt: attempt + 1 };
    }
    console.log(`${n} -> ${done ? `${done.path} (bright=${done.bright}, attempt ${done.attempt})` : 'FAILED'}`);
    results.push({ name: n, ...(done || { path: null }) });
  }
  child.kill();
  process.exit(results.every((r) => r.path) ? 0 : 1);
}

main();
