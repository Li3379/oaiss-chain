import { execute } from './pen.mjs';
// Map existing sizes to a clean scale by landing zone.
// Standard: nav/rail 18, header/search/bell 15->16, field/btn 14, caret 12->14, tick 10->12
const remap = {10:12, 12:14, 13:14, 14:14, 15:16, 16:16, 17:16, 18:18, 19:18};
// But keep brand Glyph at 16 (majority already 16).
// We'll only change outliers to reduce churn: 10,13,17,19 and 12->14,15->16.
const patch = `
const ids=[];
Get((n,c)=>{ if(n.type==='icon'){ const s=n.width||0; const target=(s===10||s===12||s===13)?14:(s===15)?16:(s===17)?16:(s===19)?18:null; if(target){ ids.push(n.id); Update(n.id,{width:target,height:target}); } } });
Print('retuned '+ids.length+' icons');
`;
const r = await execute(patch);
console.log(r.ok, (r.text.split('## Print output')[1]||'').trim());
