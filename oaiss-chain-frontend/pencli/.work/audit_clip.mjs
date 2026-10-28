import { execute } from './pen.mjs';
import { writeFileSync } from 'node:fs';
const probe = `
const clips=[];
Get((n,c)=>{ if(c.problems){ let chain=[]; let x=c; while(x&&chain.length<6){chain.unshift(x.node.name);x=x.parentCtx;} clips.push(n.type+'|'+n.name+'|'+c.problems+'|'+chain.join('/')); } });
Print(JSON.stringify(clips));
`;
const r = await execute(probe);
const json=(r.text.split('## Print output')[1]||'').trim();
writeFileSync('pencli/.work/audit_clip.json',json);
console.log('saved', json.length);
