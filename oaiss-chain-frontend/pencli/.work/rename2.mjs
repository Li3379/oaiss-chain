import { execute } from './pen.mjs';
import { readdirSync, renameSync, rmSync } from 'node:fs';
const r = await execute(`Print(JSON.stringify(Get((n,c)=>c.depth===0?{id:n.id,name:n.name}:undefined)));`);
const pages = JSON.parse(r.text.slice(r.text.indexOf('[')));
const map = Object.fromEntries(pages.map((p) => [p.id, p.name]));
const src = 'pencli/png_new/png_new';
const dst = 'pencli/png';
let n = 0;
for (const f of readdirSync(src)) {
  if (!f.endsWith('.png')) continue;
  const name = map[f.replace('.png', '')];
  if (!name) continue;
  renameSync(`${src}/${f}`, `${dst}/${name}.png`);
  n++;
}
console.log('replaced', n);
