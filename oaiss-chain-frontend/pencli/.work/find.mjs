import { execute } from './pen.mjs';
const src = `
Get((n,c)=>{
  if(n.type!=='text'||n.content!=='[object Object]')return;
  let chain=[]; let x=c;
  while(x){ chain.unshift(x.node.name); x=x.parentCtx; }
  Print(n.id+' :: '+chain.join(' > '));
});
`;
const r = await execute(src);
console.log(r.ok);
console.log((r.text.split('## Print output')[1]||'').trim());
