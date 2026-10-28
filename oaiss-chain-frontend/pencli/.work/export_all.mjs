#!/usr/bin/env node
// Export every page to PNG. The exporter has a bounded canvas window, so each
// page is temporarily moved to the origin, captured, then restored.
import { spawn, execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, writeFileSync, readFileSync } from 'node:fs';
import { ORDER } from './registry.mjs';

const PEN = 'pencli/UI_Desgin.pen';
const EXE = 'C:/Users/LiShuai/AppData/Local/Programs/Pen/resources/app.asar.unpacked/out/mcp-server-windows-x64.exe';
const OUT = process.argv[2] || 'pencli/png';
const ONLY = process.argv.slice(3);

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const BRIGHT = `
import struct,zlib,sys
d=open(sys.argv[1],'rb').read(); pos=8; idat=b''
while pos<len(d):
    ln=struct.unpack('>I',d[pos:pos+4])[0]; t=d[pos+4:pos+8]; data=d[pos+8:pos+8+ln]
    if t==b'IHDR': w,h,bd,ct=struct.unpack('>IIBB',data[:10])
    if t==b'IDAT': idat+=data
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
  try { return parseInt(execFileSync('python', ['-c', BRIGHT, png], { encoding: 'utf8' }).trim(), 10); }
  catch { return -1; }
}

function session() {
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
  return { child, req: (method, params, id) => new Promise((r) => { pending.set(id, r); child.stdin.write(JSON.stringify({ jsonrpc: '2.0', id, method, params }) + '\n'); }) };
}

async function main() {
  mkdirSync(OUT, { recursive: true });
  const { child, req } = session();
  let id = 1;
  await req('initialize', { protocolVersion: '2024-11-05', capabilities: {}, clientInfo: { name: 'zcode', version: '1' } }, id++);
  child.stdin.write(JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized' }) + '\n');

  const call = async (name, input) => {
    const r = await req('tools/call', { name, arguments: input }, id++);
    const c = r.result?.content || [];
    return c.filter((x) => x.type === 'text').map((x) => x.text).join('\n') || JSON.stringify(r.error || {});
  };

  const names = ONLY.length ? ONLY : ORDER;
  const listing = await call('batch_get', { filePath: PEN, patterns: names.map((n) => ({ name: n })), readDepth: 0 });
  const arr = JSON.parse(listing.slice(listing.indexOf('[')));
  const byName = {};
  for (const n of arr) if (names.includes(n.name)) byName[n.name] = n;

  const report = [];
  for (const name of names) {
    const node = byName[name];
    if (!node) { console.log(`${name} -> MISSING`); report.push({ name, ok: false, why: 'missing' }); continue; }
    const orig = { x: node.x ?? 0, y: node.y ?? 0 };
    let done = null;
    for (let attempt = 0; attempt < 4 && !done; attempt++) {
      await call('batch_design', { filePath: PEN, input: `Update("${node.id}",{x:0,y:0})` });
      const res = await call('export_nodes', { filePath: PEN, nodeIds: [node.id], outputDir: OUT, format: 'png', scale: 1 });
      const m = [...res.matchAll(/([A-Za-z]:[^\n]+?\.png)/g)].pop();
      if (m && existsSync(m[1].trim())) {
        const b = bright(m[1].trim());
        if (b >= 40) done = { path: m[1].trim(), bright: b };
      }
    }
    await call('batch_design', { filePath: PEN, input: `Update("${node.id}",{x:${orig.x},y:${orig.y}})` });
    const safe = name.replace(/[^\w\u4e00-\u9fa5]+/g, '_');
    if (done) {
      const dest = `${OUT}/${safe}.png`;
      writeFileSync(dest, readFileSync(done.path));
      console.log(`${name} -> ${dest} (bright=${done.bright})`);
      report.push({ name, ok: true, path: dest, bright: done.bright });
    } else {
      console.log(`${name} -> FAILED`);
      report.push({ name, ok: false });
    }
  }
  writeFileSync(`${OUT}/report.json`, JSON.stringify(report, null, 2));
  child.kill();
  process.exit(report.every((r) => r.ok) ? 0 : 1);
}

main();
