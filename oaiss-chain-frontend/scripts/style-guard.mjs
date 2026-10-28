#!/usr/bin/env node
/**
 * 样式门禁（AC3）：保证主题迁移后不会回退到硬编码色、渐变、阴影。
 *
 * 规则：
 *   1. 不得使用旧令牌名（41 个黑名单）
 *   2. 不得出现裸 hex（豁免：令牌定义块 :root / html.dark、白名单文件）
 *   3. 不得出现 gradient（豁免：白名单文件里的 ECharts 脚本渐变）
 *   4. 不得出现 box-shadow（豁免：none、焦点环、inset 强调条、EP 浮层）
 */
import { readFileSync, globSync } from 'node:fs';

const OLD_TOKENS = [
  'bg-page', 'bg-surface', 'bg-header', 'text-primary', 'text-secondary', 'border-color',
  'teal', 'green', 'success-soft', 'shadow-soft', 'text-on-dark', 'text-on-dark-muted',
  'teal-on-dark', 'text-brand-strong', 'text-brand-muted', 'chrome-selected-text',
  'chrome-selected-gradient', 'chrome-gradient-side', 'chrome-gradient-top',
  'chrome-gradient-accent', 'border-teal-soft', 'bg-subtle', 'bg-elevated',
  'mint-400', 'mint-300', 'mint-200', 'mint-100', 'mint-050',
  'chart-series-1', 'chart-series-2', 'chart-series-3', 'chart-series-info',
  'brand-gradient', 'surface-gradient',
  'stat-gradient-info', 'stat-gradient-warning', 'stat-gradient-success',
  'stat-gradient-danger', 'stat-gradient-neutral',
  'login-backdrop', 'login-accent',
];

// Login 左侧是深底品牌面板（配 hero 照片），文字色不随浅色主题；
// CompanyDashboard 的图表渐变写在 <script> 里，CSS 变量无法替代。
const ALLOW_HEX = ['views/Login.vue'];


const files = globSync('src/**/*.{vue,css}', { cwd: process.cwd() });
const violations = [];
const norm = (f) => f.replace(/\\/g, '/');

function styleBlock(src) {
  const out = [];
  const re = /<style[^>]*>([\s\S]*?)<\/style>/g;
  let m;
  while ((m = re.exec(src))) out.push(m[1]);
  return out.join('\n');
}

/** 剥离令牌定义块：:root {...} 与 html.dark / html.dark:root 里的色值本就该是具体值 */
function stripTokenBlocks(css) {
  return css
    .replace(/:root\s*\{[\s\S]*?\n\}/g, '')
    .replace(/html\.dark(?::root)?\s*\{[\s\S]*?\n\}/g, '');
}

for (const f of files) {
  const raw = readFileSync(f, 'utf8');
  const isCss = f.endsWith('.css');
  const css = isCss ? raw : styleBlock(raw);
  if (!css) continue;

  const clean = stripTokenBlocks(css);
  const nf = norm(f);

  for (const name of OLD_TOKENS) {
    const re = new RegExp(`var\\(\\s*--${name}(?![\\w-])`);
    if (re.test(raw)) violations.push({ f, rule: 'old-token', detail: `var(--${name})` });
  }

  if (!ALLOW_HEX.some((a) => nf.includes(a))) {
    const hexRe = /#[0-9a-fA-F]{3,8}\b/g;
    let m;
    while ((m = hexRe.exec(clean))) violations.push({ f, rule: 'bare-hex', detail: m[0] });
  }

  // 渐变：只检查 style 块；<script> 内的 ECharts 渐变无法用 CSS 变量表达，按设计豁免
  {
    const gradRe = /(linear|radial|conic)-gradient/g;
    let m;
    while ((m = gradRe.exec(clean))) violations.push({ f, rule: 'gradient', detail: m[0] });
  }

  const shadowRe = /box-shadow\s*:\s*([^;]+);/g;
  let m;
  while ((m = shadowRe.exec(clean))) {
    const v = m[1].trim();
    if (/^none$/i.test(v)) continue;
    if (/^0\s+0\s+0\s+\d+px/.test(v)) continue;
    if (/^inset\s+\d+px\s+0\s+0\s+0/.test(v)) continue;
    if (/^inset\s+0\s+0\s+0\s+1px/.test(v)) continue;
    if (/el-(menu|dialog|popper|dropdown|tooltip|card)/i.test(v)) continue;
    violations.push({ f, rule: 'box-shadow', detail: v.slice(0, 48) });
  }
}

if (violations.length === 0) {
  console.log(`✅ 样式门禁通过：扫描 ${files.length} 个文件，0 violation`);
  process.exit(0);
}

console.log(`❌ 样式门禁失败：${violations.length} violation\n`);
const byRule = {};
for (const v of violations) (byRule[v.rule] = byRule[v.rule] || []).push(v);
for (const [rule, list] of Object.entries(byRule)) {
  console.log(`【${rule}】${list.length} 处`);
  for (const v of list.slice(0, 30)) console.log(`  ${v.f}  ${v.detail}`);
  if (list.length > 30) console.log(`  … 另有 ${list.length - 30} 处`);
  console.log();
}
process.exit(1);