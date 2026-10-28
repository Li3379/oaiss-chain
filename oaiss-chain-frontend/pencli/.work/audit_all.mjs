import { execute } from './pen.mjs';
const probe = `
const report = {icons:[], overflow:[], hard:{}, weights:{}, fonts:{}};
Get((n,c)=>{
  // 1) icons: collect sizes + stroke (lucide)
  if(n.type==='icon') report.icons.push({n:n.name,size:n.width||0,lib:n.library||''});
  // 2) layout problems (clipped)
  if(c.problems) report.overflow.push(n.name+'@'+c.bounds.x+','+c.bounds.y+' '+c.problems);
  // 3) hardcoded dark hex fills
  if(n.fill && typeof n.fill==='string' && /^#[0-9a-fA-F]{3,8}$/.test(n.fill) && /^#[0-3]/.test(n.fill)) {
    (report.hard[n.fill]=report.hard[n.fill]||[]).push(n.name);
  }
  // 4) font weights used
  if(n.type==='text' && n.fontWeight) (report.weights[String(n.fontWeight)]=(report.weights[String(n.fontWeight)]||0)+1);
  // 5) font families
  if(n.type==='text' && n.fontFamily) (report.fonts[String(n.fontFamily)]=(report.fonts[String(n.fontFamily)]||0)+1);
});
Print(JSON.stringify(report));
`;
const r = await execute(probe);
console.log(r.ok);
console.log((r.text.split('## Print output')[1]||'').trim().slice(0,3000));
