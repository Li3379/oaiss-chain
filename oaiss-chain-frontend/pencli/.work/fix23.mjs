import { execute } from './pen.mjs';
// Find the GaugePanel body and replace its two arc gauges with meter rows.
const r1 = await execute(`Get((n,c)=>n.name==='GaugePanel'&&Print(n.id));`);
const id = (r1.text.split('## Print output')[1]||'').trim().split('\n')[0];
console.log('GaugePanel', id);
const r2 = await execute(`x=Get('${id}',{depth:4});Print(JSON.stringify(x).slice(0,1200));`);
console.log(r2.text.slice(0,1200));
