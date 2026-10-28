#!/usr/bin/env node
// Build pages from a build_*.mjs registry, one execute call per page, with screenshots.
// node build.mjs build_public.mjs [--only 01] [--shot]
import { pathToFileURL } from 'node:url';
import { writeFileSync, mkdirSync } from 'node:fs';
import { HELPERS_SRC } from './lib.js';
const GRID = (i) => ({ x: (i % 2) * 1480, y: Math.floor(i / 2) * 980 });
import { execute } from './pen.mjs';

const file = process.argv[2];
const onlyIdx = process.argv.indexOf('--only');
const only = onlyIdx > 0 ? process.argv.slice(onlyIdx + 1) : null;
const wantShot = process.argv.includes('--shot');

const mod = await import(pathToFileURL(file).href);
const pages = (mod.PAGES || []).filter((p) => !only || only.some((o) => p.name.startsWith(o)));

mkdirSync('pencli/preview', { recursive: true });
const results = [];

for (const p of pages) {
  const idx = (mod.INDEX_OFFSET || 0) + mod.PAGES.indexOf(p);
  const { x, y } = GRID(idx);
  const src = `PX=${x};PY=${y};\n${HELPERS_SRC}\n${p.body}\nPrint('BUILT', '${p.name}');`;
  const res = await execute(src);
  results.push({ name: p.name, ok: res.ok });
  console.log(`${res.ok ? 'OK  ' : 'FAIL'} ${p.name}`);
  if (!res.ok) {
    console.log('   ', res.text.replace(/\s+/g, ' ').slice(0, 400));
    continue;
  }
  if (wantShot && res.images?.length) {
    const safe = p.name.replace(/[^\w\u4e00-\u9fa5]+/g, '_');
    writeFileSync(`pencli/preview/${safe}.png`, Buffer.from(res.images[0].data, 'base64'));
    console.log('    shot ->', `pencli/preview/${safe}.png`);
  }
}

const bad = results.filter((r) => !r.ok);
console.log(`\n${results.length - bad.length}/${results.length} built`);
process.exit(bad.length ? 1 : 0);
