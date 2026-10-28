/**
 * 8 维质量雷达图生成器
 * 原生数学计算，生成完全自包含的纯 SVG 矢量图形（零外部 CDN 依赖）
 */
export function generateRadarSvg(dimensions) {
  const cx = 250;
  const cy = 250;
  const r = 160;
  const count = dimensions.length;
  const angleStep = (Math.PI * 2) / count;

  // 计算同心多边形网络 (20%, 40%, 60%, 80%, 100%)
  const gridLevels = [0.2, 0.4, 0.6, 0.8, 1.0];
  let gridSvg = '';
  for (const level of gridLevels) {
    const points = [];
    for (let i = 0; i < count; i++) {
      const angle = i * angleStep - Math.PI / 2;
      const x = cx + Math.cos(angle) * r * level;
      const y = cy + Math.sin(angle) * r * level;
      points.push(`${x.toFixed(1)},${y.toFixed(1)}`);
    }
    gridSvg += `<polygon points="${points.join(' ')}" fill="none" stroke="#e2e8f0" stroke-width="1.2" />\n`;
  }

  // 辐射轴线与标签
  let axesSvg = '';
  let labelsSvg = '';
  for (let i = 0; i < count; i++) {
    const angle = i * angleStep - Math.PI / 2;
    const x = cx + Math.cos(angle) * r;
    const y = cy + Math.sin(angle) * r;
    axesSvg += `<line x1="${cx}" y1="${cy}" x2="${x.toFixed(1)}" y2="${y.toFixed(1)}" stroke="#cbd5e1" stroke-width="1.2" stroke-dasharray="3,3" />\n`;

    // 标签坐标 (稍微向外延伸 25px)
    const labelX = cx + Math.cos(angle) * (r + 28);
    const labelY = cy + Math.sin(angle) * (r + 28);
    const textAnchor = Math.abs(Math.cos(angle)) < 0.2 ? 'middle' : Math.cos(angle) > 0 ? 'start' : 'end';
    const scoreText = `${Math.round(dimensions[i].score * 100)}%`;
    labelsSvg += `
      <text x="${labelX.toFixed(1)}" y="${labelY.toFixed(1)}" text-anchor="${textAnchor}" font-family="system-ui, -apple-system, sans-serif" font-size="12" font-weight="600" fill="#334155">
        ${dimensions[i].name}
        <tspan fill="#0ea5e9" font-size="11">(${scoreText})</tspan>
      </text>
    `;
  }

  // 数据填充多边形
  const dataPoints = [];
  for (let i = 0; i < count; i++) {
    const angle = i * angleStep - Math.PI / 2;
    const val = Math.max(0.05, Math.min(1.0, dimensions[i].score));
    const x = cx + Math.cos(angle) * r * val;
    const y = cy + Math.sin(angle) * r * val;
    dataPoints.push(`${x.toFixed(1)},${y.toFixed(1)}`);
  }

  const polygonSvg = `
    <polygon points="${dataPoints.join(' ')}" fill="rgba(14, 165, 233, 0.25)" stroke="#0ea5e9" stroke-width="2.5" />
    ${dataPoints.map(p => {
      const [x, y] = p.split(',');
      return `<circle cx="${x}" cy="${y}" r="4" fill="#0284c7" stroke="#ffffff" stroke-width="1.5" />`;
    }).join('\n')}
  `;

  return `
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 540 500" width="100%" height="400">
      <rect width="100%" height="100%" fill="#ffffff" rx="12" />
      <g transform="translate(20, 0)">
        ${gridSvg}
        ${axesSvg}
        ${polygonSvg}
        ${labelsSvg}
      </g>
    </svg>
  `;
}
