import { execute } from './pen.mjs';
import { readdirSync, renameSync, writeFileSync } from 'node:fs';
const r = await execute(`Print(JSON.stringify(Get((n,c)=>c.depth===0?{id:n.id,name:n.name}:undefined)));`);
const pages = JSON.parse(r.text.slice(r.text.indexOf('[')));
const dir = 'pencli/png';
const map = {};
for (const p of pages) map[p.id] = p.name;
const files = readdirSync(dir).filter((f) => f.endsWith('.png'));
let moved = 0;
for (const f of files) {
  const id = f.replace('.png', '');
  if (!map[id]) continue;
  const dest = `${dir}/${map[id]}.png`;
  renameSync(`${dir}/${f}`, dest);
  moved++;
}
writeFileSync(`${dir}/index.json`, JSON.stringify(Object.values(map).sort(), null, 2));
console.log('renamed', moved, 'files');
