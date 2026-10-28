#!/usr/bin/env node
/**
 * 阶段 6 迁移：把旧令牌名引用替换为语义令牌。
 *
 * 覆盖三种形式：
 *   1. var(--old)
 *   2. var(--old, fallback)      —— fallback 一并丢弃
 *   3. cssToken('--old', '#xxx') —— 运行时读变量，改为读语义变量
 *
 * 只改 <style> 块与 cssToken 调用点；不碰 i18n、路由、任何逻辑。
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { globSync } from 'node:fs';

const MAP = {
  'bg-page': 'color-ground',
  'bg-surface': 'color-surface',
  'bg-header': 'color-ground',
  'text-primary': 'color-text',
  'text-secondary': 'color-text-muted',
  'border-color': 'color-border',
  'teal': 'color-accent',
  'green': 'color-status-success',
  'success-soft': 'color-status-success-soft',
  'shadow-soft': 'color-shadow-soft',
  'text-on-dark': 'color-on-inverse',
  'text-on-dark-muted': 'color-on-inverse-muted',
  'teal-on-dark': 'color-accent-on-inverse',
  'text-brand-strong': 'color-brand-strong',
  'text-brand-muted': 'color-brand-muted',
  'chrome-selected-text': 'color-on-selected',
  'chrome-selected-gradient': 'color-raised',
  'chrome-gradient-side': 'color-ground',
  'chrome-gradient-top': 'color-surface',
  'chrome-gradient-accent': 'color-accent',
  'border-teal-soft': 'color-border-soft',
  'bg-subtle': 'color-raised',
  'bg-elevated': 'color-surface-elevated',
  'mint-400': 'color-mint-400',
  'mint-300': 'color-mint-300',
  'mint-200': 'color-mint-200',
  'mint-100': 'color-mint-100',
  'mint-050': 'color-mint-050',
  'chart-series-1': 'color-chart-1',
  'chart-series-2': 'color-chart-2',
  'chart-series-3': 'color-chart-3',
  'chart-series-info': 'color-chart-info',
  'brand-gradient': 'color-accent',
  'surface-gradient': 'color-surface',
  'stat-gradient-info': 'color-surface',
  'stat-gradient-warning': 'color-surface',
  'stat-gradient-success': 'color-surface',
  'stat-gradient-danger': 'color-surface',
  'stat-gradient-neutral': 'color-surface',
  'login-backdrop': 'color-brand-strong',
  'login-accent': 'color-accent',
};

const names = Object.keys(MAP).sort((a, b) => b.length - a.length);
const escaped = names.map((n) => n.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));

// var(--old) | var(--old, anything-up-to-closing-paren)
const varRe = new RegExp(`var\\(--(${escaped.join('|')})(?:\\s*,[^()]*(?:\\([^()]*\\))?[^()]*)?\\)`, 'g');
// cssToken('--old', '#fff') | cssToken("--old")
const cssTokenRe = new RegExp(`cssToken\\(\\s*(['"])(--(?:${escaped.join('|')}))\\1(?:\\s*,[^)]*)?\\)`, 'g');

const files = globSync('src/**/*.{vue,ts,css}', { cwd: process.cwd() });
let changedFiles = 0;
let totalReplacements = 0;
const report = [];

for (const f of files) {
  const before = readFileSync(f, 'utf8');
  let after = before.replace(varRe, (_m, old) => `var(--${MAP[old]})`);
  after = after.replace(cssTokenRe, (_m, q, oldName) => {
    const bare = oldName.replace(/^--/, '');
    return `cssToken('--${MAP[bare]}')`;
  });
  if (after !== before) {
    const n = (before.match(varRe) || []).length + (before.match(cssTokenRe) || []).length;
    writeFileSync(f, after);
    changedFiles++;
    totalReplacements += n;
    report.push(`${String(n).padStart(4)}  ${f}`);
  }
}

console.log(`迁移完成：${changedFiles} 个文件，${totalReplacements} 处引用`);
report.forEach((r) => console.log(r));

// 断言：不应再有旧名作为 var() 引用
let residual = 0;
for (const f of files) {
  const s = readFileSync(f, 'utf8');
  const m = s.match(varRe);
  if (m) residual += m.length;
}
console.log(`\n残留旧名 var() 引用：${residual}`);
process.exit(residual === 0 ? 0 : 1);