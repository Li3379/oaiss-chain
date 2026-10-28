import { execute } from './pen.mjs';
const r = await execute(`Get((n,c)=>{
  if(n.type!=='text'||String(n.content||'').indexOf('[object')<0)return;
  Print(n.id+' | page='+c.parentCtx.parentCtx.parentCtx.parentCtx.parentCtx.parentCtx.node.name+' | tdIndex='+c.parentCtx.parentCtx.index+' | rowIndex='+c.parentCtx.parentCtx.parentCtx.index);
});`);
console.log((r.text.split('## Print output')[1]||'').trim());
