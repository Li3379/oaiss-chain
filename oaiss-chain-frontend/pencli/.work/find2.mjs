import { execute } from './pen.mjs';
const r = await execute(`Get((n,c)=>{
  if(n.type!=='text'||String(n.content||'').indexOf('[object')<0)return;
  let chain=[]; let x=c;
  while(x){ chain.unshift(x.node.name); x=x.parentCtx; }
  Print(n.id+' :: '+chain.slice(0,6).join(' > '));
});`);
console.log((r.text.split('## Print output')[1]||'').trim());
