import { execute } from './pen.mjs';
const r = await execute(`Get((n,c)=>{ if(n.type==='text'&&String(n.content||'').includes('[object')) Print(n.id+' :: '+String(n.content)); }); Print('scan complete');`);
console.log((r.text.split('## Print output')[1]||'').trim());
