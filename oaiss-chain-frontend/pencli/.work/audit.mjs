import { execute } from './pen.mjs';
const r = await execute(`
Get((n,c)=>{ if(c.depth!==0)return; root=c.bounds.height; maxc=0;
 Get(n.id,(d,cc)=>{ if(cc.depth>0 && cc.bounds.y+cc.bounds.height>maxc) maxc=cc.bounds.y+cc.bounds.height; });
 if(maxc>root+2) Print('OVERFLOW '+n.id+' '+n.name+' root='+Math.round(root)+' need='+Math.round(maxc));
});
Print('audit complete');
`);
console.log((r.text.split('## Print output')[1]||'').trim());
