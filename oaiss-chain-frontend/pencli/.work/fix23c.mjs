import { execute } from './pen.mjs';
const src = `
Delete('MlFux');Delete('XjsLN');
Get((n,c)=>{ if(n.name==='GaugePanel') Print('PANEL '+n.id); });
`;
const r = await execute(src);
console.log(r.ok, (r.text.split('## Print output')[1]||'').trim());
