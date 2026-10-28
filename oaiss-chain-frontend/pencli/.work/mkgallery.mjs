import { readdirSync, writeFileSync } from 'node:fs';

const dir = 'pencli/png';
const files = readdirSync(dir).filter((f) => f.endsWith('.png')).sort();
const groups = [
  { title: '公共页面', match: /^0[123] / },
  { title: '企业端（16 页）', match: /^0[4-9] |^1[0-8] / },
  { title: '审核端 / 监管端', match: /^19 |^2[0-3] / },
  { title: '管理端', match: /^2[4-8] / },
];

const card = (f) => {
  const name = f.replace('.png', '');
  return `<figure class="card">
  <a href="png/${encodeURIComponent(f)}" target="_blank"><img loading="lazy" src="png/${encodeURIComponent(f)}" alt="${name}"></a>
  <figcaption>${name}</figcaption>
</figure>`;
};

const sections = groups.map((g) => {
  const items = files.filter((f) => g.match.test(f));
  return `<section><h2>${g.title}<span class="count">${items.length}</span></h2><div class="grid">${items.map(card).join('')}</div></section>`;
}).join('\n');

const html = `<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8">
<title>双碳链动系统 · 全站原型（28 页）</title>
<style>
  :root{--bg:#070d0b;--surface:#0c1512;--line:#20302a;--text:#e9f2ed;--muted:#93a79e;--faint:#728779;--accent:#c6f24e;--mono:"JetBrains Mono",ui-monospace,monospace}
  *{box-sizing:border-box}
  body{margin:0;background:var(--bg);color:var(--text);font:14px/1.6 "Noto Sans SC","PingFang SC",system-ui,sans-serif}
  header{padding:40px 40px 28px;border-bottom:1px solid var(--line)}
  h1{margin:0 0 10px;font-size:26px;font-weight:600;letter-spacing:.2px}
  .lede{margin:0;color:var(--muted);max-width:70ch}
  .meta{margin-top:18px;display:flex;gap:28px;flex-wrap:wrap;font-family:var(--mono);font-size:11.5px;color:var(--faint)}
  .meta b{color:var(--accent);font-weight:500}
  main{padding:32px 40px 80px}
  section{margin-bottom:44px}
  h2{display:flex;align-items:center;gap:10px;font-size:15px;font-weight:500;color:var(--muted);margin:0 0 18px}
  h2::after{content:"";flex:1;height:1px;background:var(--line)}
  .count{font-family:var(--mono);font-size:11px;color:var(--faint);border:1px solid var(--line);padding:1px 7px;border-radius:2px}
  .grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(420px,1fr));gap:20px}
  .card{margin:0;background:var(--surface);border:1px solid var(--line);border-radius:3px;overflow:hidden;transition:border-color .15s}
  .card:hover{border-color:#3c5c4e}
  .card img{display:block;width:100%;height:auto;background:#070d0b}
  figcaption{padding:10px 13px;font-size:12.5px;color:var(--muted);border-top:1px solid var(--line);font-variant-numeric:tabular-nums}
</style></head>
<body>
<header>
  <h1>双碳链动系统 · 全站原型</h1>
  <p class="lede">「精密仪器」深色仪表盘视觉世界：墨绿近黑底色、柠黄绿强调、1px 发丝边框、等宽数字。顶栏主导 + 左侧细导航壳层，四角色共用同一套组件语言。全部文案、枚举与数值取自真实前端源码与种子数据。</p>
  <div class="meta"><span>页面 <b>28</b></span><span>画板 <b>1440 × 900</b></span><span>源文件 <b>UI_Desgin.pen</b></span><span>生成 <b>2026-10-27</b></span></div>
</header>
<main>
${sections}
</main>
</body></html>`;

writeFileSync('pencli/index.html', html);
console.log('gallery written:', files.length, 'pages');
