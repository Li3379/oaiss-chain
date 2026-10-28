#!/usr/bin/env node
// Canonical page registry: index -> [name, x, y]. Two columns, generous vertical step.
export const GRID = {
  colX: [0, 1480],
  rowY: (i) => Math.floor(i / 2) * 980,
  pos(i) { return { x: this.colX[i % 2], y: this.rowY(i) }; },
};

// Page order (index -> title). Index matches the numbers used in each page file.
export const ORDER = [
  '01 碳核算填报',
  '02 官方网站首页',
  '03 登录',
  '04 排放数据',
  '05 碳中和项目',
  '06 项目详情',
  '07 碳核算公式',
  '08 数据可视化',
  '09 双向拍卖',
  '10 P2P交易',
  '11 订单管理',
  '12 碳币账户',
  '13 信誉评分',
  '14 区块链浏览器',
  '15 AI市场预测',
  '16 AI企业推理',
  '17 企业信息',
  '18 个人中心',
  '19 待审报告',
  '20 项目核证',
  '21 审核历史',
  '22 上链补救',
  '23 监管面板',
  '24 用户管理',
  '25 碳核算管理',
  '26 系统配置',
  '27 统计数据',
  '28 证书管理',
];
