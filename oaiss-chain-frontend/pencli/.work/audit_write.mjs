import { execute } from './pen.mjs';
import { writeFileSync } from 'node:fs';
const probe = `
const report = {icons_sizes:{}, overflow:{}, hard:{}, weights:{}, fonts:{}, text_cnt:0, icon_cnt:0};
Get((n,c)=>{
  if(n.type==='icon'){ report.icon_cnt++; const s=n.width||0; report.icons_sizes[s]=(report.icons_sizes[s]||0)+1; }
  if(c.problems){ const t=c.problems; report.overflow[t]=(report.overflow[t]||0)+1; }
  if(n.fill && typeof n.fill==='string' && /^#[0-9a-fA-F]{3,8}$/.test(n.fill) && /^#[0-3]/.test(n.fill)){ report.hard[n.fill]=(report.hard[n.fill]||0)+1; }
  if(n.type==='text'){ report.text_cnt++; if(n.fontWeight)report.weights[String(n.fontWeight)]=(report.weights[String(n.fontWeight)]||0)+1; if(n.fontFamily)report.fonts[String(n.fontFamily)]=(report.fonts[String(n.fontFamily)]||0)+1; }
});
Print(JSON.stringify(report));
`;
const r = await execute(probe);
const json = (r.text.split('## Print output')[1]||'').trim();
writeFileSync('pencli/.work/audit_summary.json', json);
console.log('saved', json.length, 'bytes');
console.log(r.ok ? 'ok' : r.text.slice(0,300));
