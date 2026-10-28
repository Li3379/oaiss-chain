#!/usr/bin/env node
// Export every page to PNG. The exporter has a bounded canvas window, so each
// page is temporarily moved to the origin, exported, then restored.
import { writeFileSync, mkdirSync, readdirSync, existsSync } from 'node:fs';
import { execute } from './pen.mjs';

const OUT = process.argv[2] || 'pencli/png';
mkdirSync(OUT, { recursive: true });

const listRes = await execute(`Print(JSON.stringify(Get((n,c)=>c.depth===0?{id:n.id,name:n.name,x:n.x,y:n.y}:undefined)));`);
const json = listRes.text.slice(listRes.text.indexOf('['));
const pages = JSON.parse(json);
console.log(`${pages.length} pages`);

const report = [];
for (const p of pages) {
  const safe = String(p.name).replace(/[^\w\u4e00-\u9fa5]+/g, '_');
  // Move to origin, export at 1x, then restore.
  const res = await execute(`Update('${p.id}',{x:0,y:0});Export(['${p.id}'],'png','./${OUT}',{scale:1});Update('${p.id}',{x:${p.x},y:${p.y}});Print('done ${p.id}');`);
  const ok = res.ok && /Exported/.test(res.text);
  console.log(`${ok ? 'OK  ' : 'FAIL'} ${p.name}`);
  if (!ok) console.log('   ', res.text.replace(/\s+/g, ' ').slice(0, 300));
  report.push({ id: p.id, name: p.name, ok });
}
writeFileSync(`${OUT}/report.json`, JSON.stringify(report, null, 2));
console.log(`${report.filter((r) => r.ok).length}/${report.length} exported`);
