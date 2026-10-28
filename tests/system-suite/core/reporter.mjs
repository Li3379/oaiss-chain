import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { generateRadarSvg } from './radar.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '../../..');
const REPORT_DIR = path.join(ROOT, 'tests/system-suite/reports');

/**
 * 测试结果收集器与多格式报告生成器
 */
export class TestReporter {
  constructor() {
    this.results = [];
    this.startTime = Date.now();
  }

  record(result) {
    this.results.push(result);
    // 控制台逐条格式化输出
    const statusTag = result.pass ? '\x1b[32m[PASS]\x1b[0m' : '\x1b[31m[FAIL]\x1b[0m';
    const dimTag = `\x1b[36m[${result.dimension}]\x1b[0m`;
    const idTag = `\x1b[1m[${result.id}]\x1b[0m`;
    const timeTag = `\x1b[90m(${result.durationMs}ms)\x1b[0m`;
    console.log(`${statusTag} ${idTag} ${dimTag} ${result.title} -> 状态:${result.actualStatus} ${timeTag}`);
    if (!result.pass && result.error) {
      console.log(`       \x1b[31m↳ 失败原因: ${result.error}\x1b[0m`);
    }
  }

  generateReports() {
    const total = this.results.length;
    const passed = this.results.filter(r => r.pass).length;
    const failed = total - passed;
    const passRate = total > 0 ? (passed / total) * 100 : 0;
    const totalDuration = Date.now() - this.startTime;

    // 聚合 8 大维度
    const dimensionMap = new Map();
    for (const r of this.results) {
      if (!dimensionMap.has(r.dimension)) {
        dimensionMap.set(r.dimension, { name: r.dimension, total: 0, passed: 0, failed: 0, durations: [] });
      }
      const entry = dimensionMap.get(r.dimension);
      entry.total++;
      if (r.pass) entry.passed++;
      else entry.failed++;
      entry.durations.push(r.durationMs);
    }

    const dimensionStats = Array.from(dimensionMap.values()).map(d => ({
      name: d.name,
      total: d.total,
      passed: d.passed,
      failed: d.failed,
      score: d.total > 0 ? d.passed / d.total : 0,
      avgTime: Math.round(d.durations.reduce((a, b) => a + b, 0) / d.total)
    }));

    if (!fs.existsSync(REPORT_DIR)) {
      fs.mkdirSync(REPORT_DIR, { recursive: true });
    }

    // 1. 生成 Markdown 报告
    const mdPath = path.join(REPORT_DIR, 'system-test-report.md');
    const mdContent = this.buildMarkdown(total, passed, failed, passRate, totalDuration, dimensionStats);
    fs.writeFileSync(mdPath, mdContent, 'utf8');

    // 2. 生成 HTML 交互式报告（含内嵌纯 SVG 雷达图）
    const htmlPath = path.join(REPORT_DIR, 'system-test-report.html');
    const htmlContent = this.buildHtml(total, passed, failed, passRate, totalDuration, dimensionStats);
    fs.writeFileSync(htmlPath, htmlContent, 'utf8');

    return { total, passed, failed, passRate, totalDuration, mdPath, htmlPath };
  }

  buildMarkdown(total, passed, failed, passRate, totalDuration, dimensionStats) {
    return `# OAISS CHAIN 百量级多方法系统测试全景执行报告

> **生成时间**: ${new Date().toISOString()}  
> **用例总量**: ${total} 条 ｜ **通过**: ${passed} ｜ **失败**: ${failed} ｜ **通过率**: ${passRate.toFixed(1)}%  
> **总执行耗时**: ${(totalDuration / 1000).toFixed(2)} 秒  

---

## 一、 八大测试方法维度质量分布

| 维度编号 | 测试方法维度 | 用例数 | 通过数 | 失败数 | 通过率 | 平均耗时 |
| :---: | :--- | :---: | :---: | :---: | :---: | :---: |
${dimensionStats.map((d, i) => `| D${i + 1} | **${d.name}** | ${d.total} | ${d.passed} | ${d.failed} | **${(d.score * 100).toFixed(1)}%** | ${d.avgTime}ms |`).join('\n')}

---

## 二、 测试用例执行明细清单 (共 ${total} 条六元组实测记录)

| 编号 | 测试维度 | 用例标题 | 角色/端点 | 预期响应 | 实际响应 | 耗时 | 判定 |
| :--- | :--- | :--- | :--- | :--- | :--- | :---: | :---: |
${this.results.map(r => `| \`${r.id}\` | ${r.dimension} | ${r.title} | \`${r.role}\` ${r.endpoint} | HTTP ${r.expectedStatus}${r.expectedCode ? ` (code:${r.expectedCode})` : ''} | HTTP ${r.actualStatus}${r.actualCode ? ` (code:${r.actualCode})` : ''} | ${r.durationMs}ms | ${r.pass ? '✅ PASS' : '❌ FAIL'} |`).join('\n')}

---

## 三、 失败用例根因排查记录
${failed === 0 ? '> 🎉 全部用例 100% 成功通过，系统在功能、边界、判定表、状态迁移、安全越权、容错、国密与发件箱自愈各维度均无回归！' : this.results.filter(r => !r.pass).map(r => `
### ❌ [${r.id}] ${r.title} (${r.dimension})
- **请求接口**: \`${r.method} ${r.endpoint}\`
- **执行角色**: \`${r.role}\`
- **测试输入**: \`${JSON.stringify(r.input)}\`
- **预期结果**: HTTP ${r.expectedStatus}, Code ${r.expectedCode || '任意'}
- **实际结果**: HTTP ${r.actualStatus}, Code ${r.actualCode || '无'}
- **错误详情**: \`${r.error}\`
`).join('\n')}
`;
  }

  buildHtml(total, passed, failed, passRate, totalDuration, dimensionStats) {
    const radarSvg = generateRadarSvg(dimensionStats);
    return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <title>OAISS CHAIN 百量级多方法系统测试全景仪表板</title>
  <style>
    :root {
      --primary: #0ea5e9;
      --bg: #f8fafc;
      --card: #ffffff;
      --text: #0f172a;
      --border: #e2e8f0;
      --success: #10b981;
      --danger: #ef4444;
    }
    body { font-family: system-ui, -apple-system, sans-serif; background: var(--bg); color: var(--text); margin: 0; padding: 24px; }
    .container { max-width: 1200px; margin: 0 auto; }
    .header { background: var(--card); border: 1px solid var(--border); border-radius: 12px; padding: 24px; margin-bottom: 24px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
    .metrics-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; margin-top: 16px; }
    .metric-card { background: #f1f5f9; border-radius: 8px; padding: 16px; text-align: center; }
    .metric-val { font-size: 28px; font-weight: 700; color: #0284c7; }
    .metric-lbl { font-size: 13px; color: #64748b; margin-top: 4px; }
    .radar-section { background: var(--card); border: 1px solid var(--border); border-radius: 12px; padding: 24px; margin-bottom: 24px; text-align: center; }
    table { width: 100%; border-collapse: collapse; background: var(--card); border: 1px solid var(--border); border-radius: 12px; overflow: hidden; margin-bottom: 24px; }
    th, td { padding: 12px 16px; text-align: left; border-bottom: 1px solid var(--border); font-size: 14px; }
    th { background: #f8fafc; font-weight: 600; color: #475569; }
    .pass-tag { background: #ecfdf5; color: #059669; padding: 4px 8px; border-radius: 4px; font-weight: 600; font-size: 12px; }
    .fail-tag { background: #fef2f2; color: #dc2626; padding: 4px 8px; border-radius: 4px; font-weight: 600; font-size: 12px; }
    .filter-bar { margin-bottom: 16px; display: flex; gap: 8px; }
    .btn { background: #e2e8f0; border: none; padding: 8px 16px; border-radius: 6px; cursor: pointer; font-weight: 500; }
    .btn.active { background: #0ea5e9; color: white; }
  </style>
</head>
<body>
  <div class="container">
    <div class="header">
      <h1 style="margin:0 0 8px 0; font-size:24px;">📊 OAISS CHAIN 全系统多方法系统测试全景仪表板</h1>
      <p style="margin:0; color:#64748b;">测试方法涵盖：功能等价类、边界值、判定表、状态迁移、安全越权IDOR、异常容错、国密抗篡改、发件箱自愈</p>
      <div class="metrics-grid">
        <div class="metric-card"><div class="metric-val">${total}</div><div class="metric-lbl">测试用例总量</div></div>
        <div class="metric-card"><div class="metric-val" style="color:var(--success);">${passed}</div><div class="metric-lbl">通过用例数</div></div>
        <div class="metric-card"><div class="metric-val" style="color:${failed > 0 ? 'var(--danger)' : '#64748b'};">${failed}</div><div class="metric-lbl">失败用例数</div></div>
        <div class="metric-card"><div class="metric-val">${passRate.toFixed(1)}%</div><div class="metric-lbl">总体通过率</div></div>
        <div class="metric-card"><div class="metric-val">${(totalDuration / 1000).toFixed(1)}s</div><div class="metric-lbl">总执行耗时</div></div>
      </div>
    </div>

    <div class="radar-section">
      <h2 style="margin:0 0 16px 0; font-size:18px;">🎯 八大测试方法维度系统质量雷达图 (纯SVG矢量渲染)</h2>
      ${radarSvg}
    </div>

    <table>
      <thead>
        <tr><th>维度编号</th><th>测试方法</th><th>用例总数</th><th>通过数</th><th>失败数</th><th>达成率</th><th>平均响应时间</th></tr>
      </thead>
      <tbody>
        ${dimensionStats.map((d, i) => `
          <tr>
            <td><strong>D${i + 1}</strong></td>
            <td><strong>${d.name}</strong></td>
            <td>${d.total}</td>
            <td style="color:var(--success); font-weight:600;">${d.passed}</td>
            <td style="color:${d.failed > 0 ? 'var(--danger)' : '#64748b'};">${d.failed}</td>
            <td><strong>${(d.score * 100).toFixed(1)}%</strong></td>
            <td>${d.avgTime} ms</td>
          </tr>
        `).join('')}
      </tbody>
    </table>

    <h2 style="font-size:18px; margin: 24px 0 12px 0;">📋 用例执行明细流水 (${total} 条)</h2>
    <table>
      <thead>
        <tr><th>用例ID</th><th>测试维度</th><th>用例名称</th><th>角色/端点</th><th>预期状态</th><th>实际状态</th><th>耗时</th><th>结论</th></tr>
      </thead>
      <tbody>
        ${this.results.map(r => `
          <tr>
            <td><code>${r.id}</code></td>
            <td>${r.dimension}</td>
            <td>${r.title}</td>
            <td><code>${r.role}</code> ${r.endpoint}</td>
            <td>HTTP ${r.expectedStatus}${r.expectedCode ? ` (${r.expectedCode})` : ''}</td>
            <td>HTTP ${r.actualStatus}${r.actualCode ? ` (${r.actualCode})` : ''}</td>
            <td>${r.durationMs}ms</td>
            <td>${r.pass ? '<span class="pass-tag">PASS</span>' : '<span class="fail-tag">FAIL</span>'}</td>
          </tr>
        `).join('')}
      </tbody>
    </table>
  </div>
</body>
</html>`;
  }
}
