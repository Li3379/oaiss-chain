import { execute } from './pen.mjs';
const r = await execute(`Get((n,c)=>{
  if(n.name==='GaugePanel') Print('PANEL ' + n.id);
  if(n.name==='Gauge') Print('GAUGE ' + n.id);
  if(n.name==='Legend') Print('LEGEND ' + n.id);
});`);
console.log((r.text.split('## Print output')[1]||'').trim());
