import { execute } from './pen.mjs';
const probe = `
const out=[];
Get((n,c)=>{
  if(c.depth!==0) return;
  const rootH = c.bounds.height;
  let maxB=0, maxN='', maxX=0, maxW=0;
  Get(n.id,(d,cc)=>{ if(cc.depth>0){ const b=cc.bounds; if(b.y+b.height>maxB){maxB=b.y+b.height; maxN=d.name;} if(b.x+b.width>maxW){maxW=b.x+b.width; maxX=b.x;} } });
  out.push({page:n.name, rootH, maxBottom:Math.round(maxB), overH:Math.round(maxB-rootH), deepest:maxN, maxRight:Math.round(maxW)});
});
Print(JSON.stringify(out));
`;
const r = await execute(probe);
console.log((r.text.split('## Print output')[1]||'').trim());
