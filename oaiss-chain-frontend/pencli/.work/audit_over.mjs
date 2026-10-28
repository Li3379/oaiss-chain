import { execute } from './pen.mjs';
const probe = `
const out=[];
Get((n,c)=>{
  if(!c.problems) return;
  const p=c.parentCtx;
  if(!p) return;
  const overW = c.bounds.x + c.bounds.width - p.bounds.width;
  const overH = c.bounds.y + c.bounds.height - p.bounds.height;
  out.push({name:n.name, kind:c.problems, overW:Math.round(overW), overH:Math.round(overH), parent:p.node.name});
});
Print(JSON.stringify(out));
`;
const r = await execute(probe);
console.log((r.text.split('## Print output')[1]||'').trim());
