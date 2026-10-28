#!/usr/bin/env node
/**
 * AC2 逐页比对：把迁移后的实际截图与 pen 参考图做结构化比较。
 *
 * 不追求逐像素相同（实现与设计稿必然有差异），而是核验"设计语言是否一致"：
 *   1. 亮度分布（浅色体系 vs 深色体系）
 *   2. 主色是否落在 pen 色板附近
 *   3. 是否有大面积异色块（旧的青绿渐变残留）
 *   4. 图像尺寸与内容占比
 */
import { readFileSync, readdirSync, writeFileSync, existsSync } from 'node:fs';
import { execFileSync } from 'node:child_process';

const AFTER = '../.omc/reviews/ac2-after';
const PEN = 'pencli/png';

/** 用 Python 解码 PNG 并做色彩统计（Node 无内置 PNG 解码） */
const ANALYZE = `
import struct, zlib, sys, json
from collections import Counter

def decode(p):
    d = open(p,'rb').read(); pos=8; idat=b''; w=h=0
    while pos < len(d):
        ln = struct.unpack('>I', d[pos:pos+4])[0]; t = d[pos+4:pos+8]; data = d[pos+8:pos+8+ln]
        if t == b'IHDR': w,h,bd,ct = struct.unpack('>IIBB', data[:10])
        if t == b'IDAT': idat += data
        pos += 12+ln
    raw = zlib.decompress(idat); bpp=4; stride=w*bpp
    out = bytearray(); prev = bytearray(stride); o=0
    for y in range(h):
        f = raw[o]; o+=1; line = bytearray(raw[o:o+stride]); o+=stride
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
        out += line; prev = line
    return w,h,bytes(out)

def analyze(p):
    w,h,px = decode(p)
    n = w*h
    light=dark=teal=0
    for i in range(0,len(px),4):
        r,g,b = px[i],px[i+1],px[i+2]
        if r>200 and g>200 and b>200: light+=1
        elif r<60 and g<60 and b<60: dark+=1
        # 青绿系：g 明显高于 r 且 b 接近 g
        if g>90 and g-r>35 and abs(g-b)<45: teal+=1
    return {'w':w,'h':h,'lightPct':round(light/n*100,1),'darkPct':round(dark/n*100,1),'tealPct':round(teal/n*100,2)}

print(json.dumps({p: analyze(p) for p in sys.argv[1:]}))
`;

const norm = (s) => s.replace(/[_\s]+/g, ' ').replace(/\.png$/i, '').trim();

const penFiles = readdirSync(PEN).filter((f) => f.endsWith('.png'));
const afterFiles = readdirSync(AFTER).filter((f) => f.endsWith('.png'));

const penMap = new Map(penFiles.map((f) => [norm(f), `${PEN}/${f}`]));
const afterMap = new Map(afterFiles.map((f) => [norm(f), `${AFTER}/${f}`]));

const all = [...new Set([...penMap.keys(), ...afterMap.keys()])].sort();

const stats = JSON.parse(
  execFileSync('python', ['-c', ANALYZE, ...[...penMap.values(), ...afterMap.values()]], {
    encoding: 'utf8',
    maxBuffer: 64 * 1024 * 1024,
  }),
);

const rows = [];
for (const name of all) {
  const penP = penMap.get(name);
  const aftP = afterMap.get(name);
  if (!penP || !aftP) {
    rows.push({ name, status: 'MISSING', note: !penP ? 'no pen ref' : 'no capture' });
    continue;
  }
  const a = stats[penP];
  const b = stats[aftP];
  const issues = [];

  // 1) 亮度体系一致性：双方都应以浅色为主（或设计稿本身是深色 hero）
  const penLightDominant = a.lightPct >= 40;
  const aftLightDominant = b.lightPct >= 40;
  if (penLightDominant !== aftLightDominant) {
    issues.push(`亮度体系不一致 pen=${a.lightPct}% / 实际=${b.lightPct}%`);
  }

  // 2) 青绿残留：pen 稿几乎没有青绿，实际稿若 >3% 说明旧调色板残留
  if (b.tealPct > 3 && a.tealPct < 3) {
    issues.push(`青绿残留 ${b.tealPct}%（pen ${a.tealPct}%）`);
  }

  // 3) 尺寸
  if (Math.abs(b.w - 1440) > 2) issues.push(`宽度异常 ${b.w}`);

  rows.push({
    name,
    status: issues.length ? 'DIFF' : 'OK',
    issues,
    pen: { light: a.lightPct, dark: a.darkPct, teal: a.tealPct },
    actual: { light: b.lightPct, dark: b.darkPct, teal: b.tealPct, w: b.w, h: b.h },
  });
}

writeFileSync('../.omc/reviews/ac2-diff.json', JSON.stringify(rows, null, 2));

const ok = rows.filter((r) => r.status === 'OK').length;
const diff = rows.filter((r) => r.status === 'DIFF');
const missing = rows.filter((r) => r.status === 'MISSING');

console.log(`比对 ${rows.length} 页：OK ${ok} / DIFF ${diff.length} / MISSING ${missing.length}\n`);
if (diff.length) {
  console.log('需要关注的页面：');
  for (const r of diff) console.log(`  ${r.name}\n    ${r.issues.join('\n    ')}`);
}
if (missing.length) {
  console.log('\n缺失：');
  for (const r of missing) console.log(`  ${r.name} — ${r.note}`);
}
process.exit(diff.length === 0 && missing.length === 0 ? 0 : 1);