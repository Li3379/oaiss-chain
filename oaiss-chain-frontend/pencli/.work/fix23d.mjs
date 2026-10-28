import { execute } from './pen.mjs';
const src = `
Get((n,c)=>n.name==='GaugePanel'&&Print(JSON.stringify(Get(n.id,{depth:3}).children[1])));
`;
const r = await execute(src);
console.log(r.text.slice(0,1400));
