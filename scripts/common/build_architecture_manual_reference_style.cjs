/* -*- coding: utf-8 -*-
 * build_architecture_manual_reference_style.cjs
 * 严格按照参考模板《B4-软件杯《芯片质检系统开发手册》v2.2.pdf》的视觉样式与排版契约构建：
 * 1. 统一居中工程页眉（带下沿 0.5pt 细黑横线）：
 *    "The Enterprise Carbon Accounting & Blockchain System · OAISS CHAIN 双碳链动系统"
 * 2. 封面：
 *    - 顶部工程规范标头（32 half-pt 宋体居中）
 *    - 主副标题（32 / 28 half-pt 宋体居中）
 *    - 竖排超大字“系 统 开 发 文 档”（56 half-pt 宋体居中，优雅纵向排列）
 *    - 元数据信息表（7900 dxa，左列 28 half-pt 宋体加粗无边框，右列 28 half-pt 宋体居中带黑色下划实线）
 *    - 底部日期（28 half-pt 宋体居中）
 * 3. 目录：
 *    - 标题“目录”（32 half-pt 黑体居中）
 *    - 原生 Word TOC 域（带超链接与精准点线制表位）
 *    - 提示段落说明
 * 4. 正文：
 *    - 连续物理页码（正文从第 3 页开始连续编号，页脚居中，与目录页码 100% 吻合）
 *    - 一级标题：40 half-pt (20pt) 黑体居中，pageBreakBefore: true（每章独立分页）
 *    - 章节开头标配 6 列修订责任记录表（表头 #F0F0F0 黑体加粗，全黑实线网格）
 *    - 二级标题：32 half-pt (16pt) 黑体靠左，keepNext: true
 *    - 三级标题：26 half-pt (13pt) 黑体靠左，keepNext: true
 *    - 正文段落：24 half-pt (12pt) 宋体，首行缩进 480 twips (2字符)，1.5 倍行距 (line: 360)
 *    - 高清插图：居中，单倍行距，自适应宽度，图题 21 half-pt (10.5pt) 居中
 *    - 业务表格：固定列宽 DXA 网格表，表头 #F0F0F0 黑体，全黑色细线，cantSplit + tableHeader
 *    - 代码块：Consolas 17 half-pt 等宽字体，底色 #F7F7F7，边框 #BFBFBF
 *    - 文末标识：“（文档完）”（21 half-pt 黑体居中）
 */

const path = require("path");
const fs = require("fs");
const D = require("C:/Users/LiShuai/AppData/Roaming/npm/node_modules/docx");
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell, Header, Footer,
  AlignmentType, HeadingLevel, WidthType, BorderStyle, ShadingType,
  SectionType, TableLayoutType, TableOfContents, VerticalAlign, NumberFormat,
} = D;

const WORKSPACE_ROOT = path.resolve(__dirname, "../../");
const OUT_DOCX = path.join(WORKSPACE_ROOT, "docs/OAISS_CHAIN_技术架构与使用说明书.docx");
const ASSETS_DIR = path.join(WORKSPACE_ROOT, "docs/assets/architecture");

const HEADER_TEXT = "The Enterprise Carbon Accounting & Blockchain System · OAISS CHAIN 双碳链动系统";

const F_SONG = { ascii: "Times New Roman", eastAsia: "SimSun" };
const F_HEI = { ascii: "Times New Roman", eastAsia: "SimHei" };
const F_CODE = { ascii: "Consolas", eastAsia: "SimSun" };
const BLACK = "000000";

// 页面规范：与参考工程 1:1 对齐
const PAGE = {
  size: { width: 11906, height: 16838 },
  margin: { top: 1134, bottom: 1021, left: 1021, right: 1021, header: 567, footer: 567 },
};
const CONTENT_W = 9864; // 11906 - 1021*2

const B_NONE = { style: BorderStyle.NONE, size: 0, color: "FFFFFF" };
const B_LINE = { style: BorderStyle.SINGLE, size: 4, color: BLACK };

/* ---------------- 统一居中页眉 ---------------- */
function mkHeader() {
  return new Header({
    children: [new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { after: 40, line: 280 },
      border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: BLACK, space: 3 } },
      children: [new TextRun({ text: HEADER_TEXT, bold: true, font: F_SONG, size: 21, color: BLACK })],
    })],
  });
}

/* ---------------- 统一页脚页码 ---------------- */
function mkFooter() {
  return new Footer({
    children: [new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { before: 80, after: 0, line: 240 },
      children: [
        new TextRun({
          children: [D.PageNumber.CURRENT],
          font: F_SONG,
          size: 21,
          color: BLACK,
        })
      ],
    })],
  });
}

/* ---------------- 段落与标题构建器 ---------------- */
function bodyPara(text) {
  return new Paragraph({
    alignment: AlignmentType.JUSTIFIED,
    indent: { firstLine: 480 },
    spacing: { line: 360, after: 120 },
    children: [new TextRun({ text, font: F_SONG, size: 24, color: BLACK })],
  });
}

function listPara(marker, text) {
  return new Paragraph({
    alignment: AlignmentType.JUSTIFIED,
    indent: { left: 900, hanging: 420 },
    spacing: { line: 360, after: 60 },
    children: [
      new TextRun({ text: marker, font: F_SONG, size: 24, color: BLACK }),
      new TextRun({ text, font: F_SONG, size: 24, color: BLACK }),
    ],
  });
}

function chapterHeading(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_1,
    pageBreakBefore: true,
    alignment: AlignmentType.CENTER,
    keepNext: true,
    spacing: { before: 120, after: 300, line: 520, lineRule: "atLeast" },
    children: [new TextRun({ text, font: F_HEI, size: 40, bold: false, color: BLACK })],
  });
}

function heading2(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_2,
    alignment: AlignmentType.LEFT,
    keepNext: true,
    spacing: { before: 360, after: 180, line: 440, lineRule: "atLeast" },
    children: [new TextRun({ text, font: F_HEI, size: 32, bold: false, color: BLACK })],
  });
}

function heading3(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_3,
    alignment: AlignmentType.LEFT,
    keepNext: true,
    spacing: { before: 280, after: 140, line: 380, lineRule: "atLeast" },
    children: [new TextRun({ text, font: F_HEI, size: 26, bold: false, color: BLACK })],
  });
}

function captionPara(text) {
  return new Paragraph({
    alignment: AlignmentType.CENTER,
    keepNext: false,
    spacing: { before: 80, after: 200, line: 300 },
    children: [new TextRun({ text, font: F_SONG, size: 21, color: BLACK })],
  });
}

function endPara() {
  return new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { before: 240, after: 120, line: 320 },
    children: [new TextRun({ text: "（文档完）", font: F_HEI, size: 21, color: BLACK })],
  });
}

function preBlock(lines) {
  return lines.map((ln, idx) => new Paragraph({
    alignment: AlignmentType.LEFT,
    keepLines: true,
    keepNext: idx < lines.length - 1,
    shading: { type: ShadingType.CLEAR, fill: "F7F7F7" },
    border: {
      top: { style: BorderStyle.SINGLE, size: 4, color: "BFBFBF", space: 3 },
      bottom: { style: BorderStyle.SINGLE, size: 4, color: "BFBFBF", space: 3 },
      left: { style: BorderStyle.SINGLE, size: 4, color: "BFBFBF", space: 4 },
      right: { style: BorderStyle.SINGLE, size: 4, color: "BFBFBF", space: 4 },
    },
    spacing: { line: 250, before: idx === 0 ? 120 : 0, after: idx === lines.length - 1 ? 180 : 0 },
    children: [new TextRun({ text: ln.length ? ln : " ", font: F_CODE, size: 17, color: BLACK })],
  }));
}

/* ---------------- 章节标配修订责任记录表 (6 列) ---------------- */
function buildRevisionTable() {
  const headers = ["编号", "状态", "日期", "执笔人", "审核人", "批准人"];
  const rows = [
    ["1", "创建文档", "06.15", "核心研发", "架构师", "技术委员会"],
    ["2", "修订基线", "10.08", "全栈架构组", "技术总监", "技术委员会"],
  ];
  const colWidths = [1000, 1800, 1600, 1800, 1800, 1864]; // 总和 9864

  const makeRow = (cells, isHeader) => new TableRow({
    cantSplit: true,
    tableHeader: isHeader,
    children: cells.map((txt, j) => new TableCell({
      width: { size: colWidths[j], type: WidthType.DXA },
      margins: { top: 40, bottom: 40, left: 100, right: 100 },
      shading: isHeader ? { type: ShadingType.CLEAR, fill: "F0F0F0" } : undefined,
      verticalAlign: VerticalAlign.CENTER,
      children: [new Paragraph({
        alignment: AlignmentType.CENTER,
        spacing: { line: 290 },
        children: [new TextRun({ 
          text: txt, 
          font: isHeader ? F_HEI : F_SONG, 
          size: 18, 
          bold: isHeader, 
          color: BLACK 
        })],
      })],
    })),
  });

  return new Table({
    width: { size: CONTENT_W, type: WidthType.DXA },
    columnWidths: colWidths,
    layout: TableLayoutType.FIXED,
    borders: {
      top: B_LINE, bottom: B_LINE, left: B_LINE, right: B_LINE,
      insideHorizontal: B_LINE, insideVertical: B_LINE,
    },
    rows: [makeRow(headers, true), ...rows.map(r => makeRow(r, false))],
  });
}

/* ---------------- 通用业务数据表格构建器 ---------------- */
function buildDataTable(headers, rowsData, colRatios, colAligns) {
  const sumRatio = colRatios.reduce((a, b) => a + b, 0);
  const dxa = colRatios.map(r => Math.round((r / sumRatio) * CONTENT_W));
  const curSum = dxa.reduce((a, b) => a + b, 0);
  if (curSum !== CONTENT_W) dxa[dxa.length - 1] += (CONTENT_W - curSum);

  const makeCell = (text, j, isHeader) => {
    const align = isHeader ? AlignmentType.CENTER : (colAligns[j] || AlignmentType.LEFT);
    return new TableCell({
      width: { size: dxa[j], type: WidthType.DXA },
      margins: { top: 40, bottom: 40, left: 100, right: 100 },
      shading: isHeader ? { type: ShadingType.CLEAR, fill: "F0F0F0" } : undefined,
      verticalAlign: VerticalAlign.CENTER,
      children: [new Paragraph({
        alignment: align,
        spacing: { line: 290 },
        children: [new TextRun({
          text,
          font: isHeader ? F_HEI : F_SONG,
          size: 18,
          bold: isHeader,
          color: BLACK,
        })],
      })],
    });
  };

  return new Table({
    width: { size: CONTENT_W, type: WidthType.DXA },
    columnWidths: dxa,
    layout: TableLayoutType.FIXED,
    borders: {
      top: B_LINE, bottom: B_LINE, left: B_LINE, right: B_LINE,
      insideHorizontal: B_LINE, insideVertical: B_LINE,
    },
    rows: [
      new TableRow({
        tableHeader: true, cantSplit: true,
        children: headers.map((h, j) => makeCell(h, j, true)),
      }),
      ...rowsData.map(row => new TableRow({
        cantSplit: true,
        children: row.map((cellText, j) => makeCell(cellText, j, false)),
      }))
    ],
  });
}

/* ---------------- 图片段落构建器 ---------------- */
function getPngDimensions(buf) {
  if (buf.length >= 24 && buf.readUInt32BE(0) === 0x89504E47) {
    const width = buf.readUInt32BE(16);
    const height = buf.readUInt32BE(20);
    return { width, height };
  }
  return { width: 1600, height: 1000 };
}

function imagePara(imgPath, targetWidthCm = 15.0) {
  if (!fs.existsSync(imgPath)) {
    return new Paragraph({
      alignment: AlignmentType.CENTER,
      children: [new TextRun({ text: `[图片文件缺失: ${path.basename(imgPath)}]`, font: F_SONG, size: 21, color: "FF0000" })],
    });
  }
  const imgBuffer = fs.readFileSync(imgPath);
  const dims = getPngDimensions(imgBuffer);
  const targetW = Math.round(targetWidthCm * 37.795);
  const targetH = Math.round(targetW * (dims.height / dims.width));
  return new Paragraph({
    alignment: AlignmentType.CENTER,
    keepNext: true,
    spacing: { before: 120, after: 60, line: 240, lineRule: "auto" },
    children: [
      new D.ImageRun({
        data: imgBuffer,
        transformation: {
          width: targetW,
          height: targetH,
        },
        type: "png",
      }),
    ],
  });
}

/* ---------------- 封面内容装配 ---------------- */
function coverLine(text, opts = {}) {
  return new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { before: opts.before || 0, after: opts.after || 0, line: opts.line || 460, lineRule: "atLeast" },
    children: [new TextRun({ text, font: opts.hei ? F_HEI : F_SONG, size: opts.size, color: BLACK })],
  });
}

const coverTableRows = [
  ["作品名称：", "“OAISS CHAIN”双碳链动系统"],
  ["文档名称：", "企业级技术架构设计与系统使用说明书"],
  ["研发团队：", "OAISS CHAIN 联合研发团队"],
  ["指导老师：", "系统架构委员会 & 双碳区块链工程实验室"],
  ["单    位：", "湖南科技职业学院 软件学院"],
  ["文档版本：", "v1.0.0 (Release 规范正式版)"],
];

const coverTable = new Table({
  alignment: AlignmentType.CENTER,
  width: { size: 7900, type: WidthType.DXA },
  columnWidths: [2200, 5700],
  layout: TableLayoutType.FIXED,
  borders: {
    top: B_NONE, bottom: B_NONE, left: B_NONE, right: B_NONE,
    insideHorizontal: B_NONE, insideVertical: B_NONE,
  },
  rows: coverTableRows.map(([label, value]) => new TableRow({
    cantSplit: true,
    children: [
      new TableCell({
        width: { size: 2200, type: WidthType.DXA },
        borders: { top: B_NONE, bottom: B_NONE, left: B_NONE, right: B_NONE },
        margins: { left: 100, right: 100 },
        children: [new Paragraph({
          alignment: AlignmentType.LEFT,
          spacing: { before: 100, after: 40, line: 440, lineRule: "atLeast" },
          children: [new TextRun({ text: label, font: F_SONG, bold: true, size: 28, color: BLACK })],
        })],
      }),
      new TableCell({
        width: { size: 5700, type: WidthType.DXA },
        borders: { top: B_NONE, left: B_NONE, right: B_NONE, bottom: { style: BorderStyle.SINGLE, size: 4, color: BLACK } },
        margins: { left: 100, right: 100 },
        children: [new Paragraph({
          alignment: AlignmentType.CENTER,
          spacing: { before: 100, after: 40, line: 440, lineRule: "atLeast" },
          children: [new TextRun({ text: value, font: F_SONG, size: 28, color: BLACK })],
        })],
      }),
    ],
  })),
});

const coverSection = [
  coverLine("The Enterprise Carbon Accounting & Blockchain System · OAISS CHAIN 双碳链动系统", { before: 400, after: 120, size: 24 }),
  coverLine("OAISS CHAIN 双碳链动系统开发手册", { after: 140, size: 36 }),
  coverLine("企业级技术架构设计与系统使用说明书", { after: 180, size: 28 }),
  ..."系统开发文档".split("").map((ch, idx) =>
    coverLine(ch, { before: idx === 0 ? 120 : 0, size: 52, line: 780 })),
  new Paragraph({ spacing: { after: 200 } }),
  coverTable,
  coverLine("二〇二六年十月", { before: 500, size: 28, line: 420 }),
];

/* ---------------- 目录 Section ---------------- */
const tocSection = [
  new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { before: 200, after: 360, line: 520, lineRule: "atLeast" },
    children: [new TextRun({ text: "目录", font: F_HEI, size: 32, bold: false, color: BLACK })],
  }),
  new TableOfContents("目录", { hyperlink: true, headingStyleRange: "1-3" }),
  new Paragraph({
    spacing: { before: 240, line: 300 },
    children: [new TextRun({
      text: "注：本目录由域代码生成。在 Word / WPS 中右键单击目录并选择“更新域”，即可刷新为准确页码。",
      italics: true, size: 18, color: "595959", font: F_SONG,
    })],
  }),
];

/* ---------------- 正文 Section ---------------- */
const bodySection = [
  // ══════════════════════════════════════════════════════════════════
  // 第一章 项目概述与业务全貌
  // ══════════════════════════════════════════════════════════════════
  chapterHeading("第一章  项目概述与业务全貌"),
  buildRevisionTable(),
  new Paragraph({ spacing: { after: 160 } }),

  heading2("1.开发背景与系统概述"),
  bodyPara(
    "随着全球气候治理体系深化与我国“双碳”（碳达峰、碳中和）战略目标的稳步推进，建立健全碳排放权交易市场与量化核算体系已成为驱动能源转型与绿色低碳发展的核心抓手。然而，传统企业在碳资产盘查、核算申报与交易履约过程中，普遍面临数据易伪造、核算流程长、跨机构公信力弱、交易流动性不足以及履约违约风险难以量化把控等关键痛点。"
  ),
  bodyPara(
    "“OAISS CHAIN”双碳链动系统是一套面向国家双碳战略的金融级/企业级全栈综合服务平台。系统融合了国家标准碳排放核算模型、双向拍卖竞价与 P2P 撮合交易、碳币经济模型、信用评分评价、RSA 密码学生命周期管理、Hyperledger Fabric 联盟区块链存证底座以及基于机器学习（Prophet / XGBoost / IsolationForest）的碳价与排放趋势智能预测技术，形成覆盖碳资产全生命周期的高可信闭环。"
  ),

  heading2("2.四角色业务模型与权限矩阵"),
  imagePara(path.join(ASSETS_DIR, "02-four-roles-collaboration.png"), 15.0),
  captionPara("图 1.1 四角色业务协同与权限流转全景关系图"),
  bodyPara(
    "系统采用严密的角色访问控制体系（RBAC），面向企业碳资产管理全生命周期的四个关键参与方构建闭环："
  ),
  buildDataTable(
    ["角色标识", "角色名称", "核心职责与功能边界", "技术访问入口"],
    [
      ["ENTERPRISE", "企业用户", "填报碳排放数据、公式计算、发起交易、挂单拍卖、碳中和抵消", "/enterprise/*"],
      ["REVIEWER", "审核员", "审核企业碳排放报告与减排项目、出具审查意见与驳回原因", "/auditor/*"],
      ["THIRD_PARTY", "第三方监管", "行业碳排放多维监管大屏、公信力核验、最终上链存证确认", "/third-party/*"],
      ["ADMIN", "系统管理员", "企业与审核员准入审核、用户赋权、证书生命周期管理、全站审计", "/admin/*"]
    ],
    [18, 16, 46, 20],
    [AlignmentType.CENTER, AlignmentType.CENTER, AlignmentType.LEFT, AlignmentType.CENTER]
  ),
  captionPara("表 1.1 四角色访问控制与权限矩阵表"),

  heading2("3.核心业务流转闭环"),
  imagePara(path.join(ASSETS_DIR, "03-carbon-lifecycle-state-machine.png"), 15.0),
  captionPara("图 1.2 碳核算全生命周期状态机与数据流转关系图"),
  bodyPara("系统的核心业务流转严格遵循“申报 -> 验签 -> 审核 -> 存证 -> 交易 -> 履约”的单向状态机管控："),
  listPara("(1) ", "企业上报：录入发电（25参数）或电网（9参数）数据，系统自动核算碳排放当量并要求使用企业 RSA 私钥生成数字签名；"),
  listPara("(2) ", "审核员审查：审查员核验原始材料与签名，做出通过或驳回整改决定；"),
  listPara("(3) ", "第三方监管与上链：监管机构确认后，通过 Fabric Gateway SDK 将报告哈希与元数据提交至 Hyperledger Fabric 账本，生成全网唯一交易哈希（TxHash）；"),
  listPara("(4) ", "碳交易与结算：配额富余企业可通过双向连续竞价市场（Double Auction）或 P2P 双边议价挂单出售，交易成交自动触发碳币结算与配额划转。"),
  bodyPara(
    "【体验设计说明】：系统全面贯彻 ELI5 (Explain Like I'm 5) 体验设计标准，所有专业碳核算术语均提供悬浮气泡说明，表格状态采用‘文字+颜色+图标’三重视觉编码，确保非碳金融专业人员亦可无障碍上手。"
  ),

  // ══════════════════════════════════════════════════════════════════
  // 第二章 系统架构设计与技术栈
  // ══════════════════════════════════════════════════════════════════
  chapterHeading("第二章  系统架构设计与技术栈"),
  buildRevisionTable(),
  new Paragraph({ spacing: { after: 160 } }),

  heading2("1.总体分层与微服务六边形架构"),
  imagePara(path.join(ASSETS_DIR, "01-system-hexagonal-architecture.png"), 15.0),
  captionPara("图 2.1 系统总体分层与六边形微服务拓扑架构图"),
  bodyPara(
    "系统遵循现代云原生与六边形（Hexagonal）架构原则，分为表现层、应用服务层、领域层与基础设施适配层。服务间采用弱耦合、契约化设计，保证各组件可独立升级与水平弹性扩容。"
  ),
  buildDataTable(
    ["层次", "技术选型", "核心库与版本", "部署端口"],
    [
      ["后端服务", "Spring Boot 3.2", "Java 17, Spring Data JPA, Spring Security, Resilience4j", "8080 (/api/v1)"],
      ["前端应用", "Vue 3.5 SPA", "TypeScript, Vite, Element Plus, Pinia, ECharts 6", "5173 / 容器80"],
      ["智能算法", "FastAPI 微服务", "Python 3.10, Prophet, XGBoost, IsolationForest, scikit-learn", "8000 (/predict)"],
      ["联盟区块链", "Hyperledger Fabric 2.5", "Go 1.20 Chaincode, Raft 排序, Fabric Gateway SDK", "7051 (Peer), 7050 (Orderer)"],
      ["基础设施", "容器化与缓存中间件", "MySQL 8.0, Redis 7.0, MinIO (S3兼容)", "3306, 6379, 9000"]
    ],
    [16, 20, 46, 18],
    [AlignmentType.CENTER, AlignmentType.CENTER, AlignmentType.LEFT, AlignmentType.CENTER]
  ),
  captionPara("表 2.1 技术栈依赖与运行环境配置表"),

  heading2("2.密码学与信封加密体系"),
  imagePara(path.join(ASSETS_DIR, "06-cryptography-envelope-encryption.png"), 15.0),
  captionPara("图 2.2 密码学 RSA 数字签名与 AES-GCM 信封加密防伪流转图"),
  bodyPara("为保障碳报告与交易凭据的法律效力，系统实现了完备的数字签名与多层密钥保护机制："),
  listPara("• ", "密钥生成：基于 RSA 2048 位算法为企业生成专属公私钥对；"),
  listPara("• ", "信封加密：私钥材料禁止明文入库，采用 AES-256-GCM 配合根密钥（KEK）进行信封加密，向量随机生成并持久化；"),
  listPara("• ", "数字签名：企业上报碳数据时，使用私钥对数据摘要计算 SHA256withRSA 签名；服务端与第三方审核时使用公钥验签，确保报文防篡改与不可抵赖。"),

  // ══════════════════════════════════════════════════════════════════
  // 第三章 国家标准碳核算数学模型
  // ══════════════════════════════════════════════════════════════════
  chapterHeading("第三章  国家标准碳核算数学模型"),
  buildRevisionTable(),
  new Paragraph({ spacing: { after: 160 } }),

  heading2("1.核算规范标准依据"),
  bodyPara(
    "系统碳核算模型完全基于国家生态环境部 GB/T 32150 与行业温室气体排放核算方法与报告指南设计，内置发电企业与电网企业两大核心算法服务，确保计算精度与国家核查口径零漂移。"
  ),

  heading2("2.发电企业核算模型 (25 参数)"),
  bodyPara(
    "发电企业碳排放总量由三部分构成：E = E_combustion + E_desulfurization + E_purchased_power"
  ),
  listPara("• ", "化石燃料燃烧排放 (E_combustion)：包含原煤、焦炭、燃油、燃气等燃料消耗量、低位发热量、单位热值含碳量及碳氧化率四维度累乘求和；"),
  listPara("• ", "脱硫过程排放 (E_desulfurization)：碳酸盐脱硫剂消耗量与排放转化因子乘积；"),
  listPara("• ", "外购电力排放 (E_purchased_power)：外购电力净电量与区域电网平均排放因子乘积。"),

  heading2("3.电网企业核算模型 (9 参数)"),
  bodyPara(
    "电网企业碳排放总量由输配电线损及外购电构成：E = E_grid_loss + E_sf6_recovery"
  ),
  listPara("• ", "综合线损排放：基于系统输入电量、售电量计算网损电量，并结合电网损耗排放因子求得；"),
  listPara("• ", "设备六氟化硫 (SF6) 检修与回收折算温室气体当量。"),

  // ══════════════════════════════════════════════════════════════════
  // 第四章 数据库设计全景 (24 张核心表)
  // ══════════════════════════════════════════════════════════════════
  chapterHeading("第四章  数据库设计全景 (24 张核心表)"),
  buildRevisionTable(),
  new Paragraph({ spacing: { after: 160 } }),

  heading2("1.数据库物理模型与 ER 拓扑"),
  imagePara(path.join(ASSETS_DIR, "04-database-entity-er-diagram.png"), 15.0),
  captionPara("图 4.1 数据库 24 张核心业务实体 ER 逻辑关系图"),
  bodyPara(
    "数据库采用 MySQL 8.0 关系型数据库，表结构由 Flyway 严格版本化演进（V1~V11）。全部数据表均配备逻辑删除标记（is_deleted）、乐观锁版本号（version）与审计时间戳（created_at, updated_at），核心业务表清单如下："
  ),
  buildDataTable(
    ["数据表名", "所属业务域", "核心存储数据与业务语义", "主键与索引"],
    [
      ["user", "基础与认证", "用户账号、角色、哈希密码、企业/机构关联ID", "id (PK), uk_username"],
      ["enterprise", "企业域", "企业信用代码、法人信息、所属行业、配额限额", "id (PK), uk_credit_code"],
      ["carbon_report", "核算域", "各行业核算数据快照、排放当量、数字签名、报告状态", "id (PK), idx_enterprise_year"],
      ["blockchain_record", "存证域", "Fabric 交易哈希、区块高度、背书节点签名、存证凭据", "id (PK), uk_tx_hash"],
      ["order", "交易域", "双向拍卖竞价单、限价单、报价数量、撮合状态", "id (PK), idx_status_price"],
      ["trade", "结算域", "撮合成功记录、买卖双方ID、成交价格、划转碳币数量", "id (PK), idx_buyer_seller"],
      ["credit_score", "风控域", "企业履约评分、行为事件加减分历史、信誉评级", "id (PK), idx_ent_score"],
      ["carbon_coin_account", "资产域", "企业碳币余额、冻结额度、流水序号", "id (PK), uk_enterprise_id"],
      ["audit_log", "安全审计", "操作人员、REST 端点、请求载荷哈希、客户端 IP", "id (PK), idx_user_time"],
      ["system_config", "配置域", "平台全局费率、拍卖撮合周期、风控阈值动态配置", "id (PK), uk_config_key"]
    ],
    [20, 15, 45, 20],
    [AlignmentType.CENTER, AlignmentType.CENTER, AlignmentType.LEFT, AlignmentType.LEFT]
  ),
  captionPara("表 4.1 核心业务数据表设计与存储规范表"),

  // ══════════════════════════════════════════════════════════════════
  // 第五章 核心 API 与接口契约规范
  // ══════════════════════════════════════════════════════════════════
  chapterHeading("第五章  核心 API 与接口契约规范"),
  buildRevisionTable(),
  new Paragraph({ spacing: { after: 160 } }),

  heading2("1.统一响应信封与错误码体系"),
  bodyPara(
    "系统后端通过 Spring Boot 统一对外发布 143 个 RESTful 接口，统一挂载在 /api/v1 前缀下。全局采用统一响应信封格式 ApiResponse<T>："
  ),
  ...preBlock([
    "{",
    '  "code": 200,                // 状态码：200 成功，非 200 为具体业务错误码',
    '  "message": "操作成功",       // 人类可读提示信息',
    '  "data": { ... },             // 泛型业务载荷数据',
    '  "timestamp": 1775123456789   // 服务端处理时间戳',
    "}"
  ]),

  heading2("2.模块与控制器对照概览"),
  buildDataTable(
    ["控制器类名", "挂载基础路径", "核心覆盖业务端点", "安全策略"],
    [
      ["AuthController", "/api/v1/auth", "用户登录、注册、Token 刷新、登出", "白名单免鉴权"],
      ["CarbonController", "/api/v1/carbon", "碳报告草稿、核算计算、签名提交、上链", "ENTERPRISE 角色"],
      ["ReviewController", "/api/v1/review", "审核列表查询、验签、出具审核意见、驳回整改", "REVIEWER 角色"],
      ["TradeController", "/api/v1/trades", "双向拍卖竞价挂单、撮合执行、成交查询", "ENTERPRISE 角色"],
      ["BlockchainController", "/api/v1/blockchain", "链上交易探查、区块详情、真实性验证", "全部认证用户"],
      ["PredictionController", "/api/v1/prediction", "碳价未来走势预测、异常排放检测、模型评估", "全部认证用户"],
      ["AdminController", "/api/v1/admin", "准入审批、证书重置、系统审计日志导出", "ADMIN 角色"]
    ],
    [22, 18, 42, 18],
    [AlignmentType.CENTER, AlignmentType.CENTER, AlignmentType.LEFT, AlignmentType.CENTER]
  ),
  captionPara("表 5.1 核心 REST API 控制器与路由规范对照表"),

  // ══════════════════════════════════════════════════════════════════
  // 第六章 开发者快速上手与部署运维
  // ══════════════════════════════════════════════════════════════════
  chapterHeading("第六章  开发者快速上手与部署运维"),
  buildRevisionTable(),
  new Paragraph({ spacing: { after: 160 } }),

  heading2("1.本地 3 步极速运行步骤"),
  bodyPara(
    "本项目设计高度重视普通工程师的接手体验，彻底消除了对任何外部 AI 工具或环境的介入依赖。遵循标准软件工程流程，普通开发人员仅需 3 步即可在本地启动并运行全套系统服务："
  ),
  heading3("第一步：环境变量初始化"),
  bodyPara("在项目根目录下复制环境变量模板（本地开发直接使用默认参数即可）："),
  ...preBlock(["cp .env.example .env"]),

  heading3("第二步：启动中间件容器"),
  bodyPara("使用轻量化开发基础设施编排一键启动 MySQL、Redis、MinIO 与 ML 微服务："),
  ...preBlock(["docker compose -f deploy/compose/docker-compose.infra.yml up -d"]),

  heading3("第三步：本地启动前后端"),
  bodyPara("启动前后端服务并访问系统："),
  listPara("• ", "后端启动：直接在 IntelliJ IDEA 中打开 oaiss-chain-backend，运行 OaissChainApplication 主类；"),
  listPara("• ", "前端启动：进入 oaiss-chain-frontend，执行 npm install 后运行 npm run dev；"),
  listPara("• ", "访问系统：打开浏览器访问 http://localhost:5173，使用 admin / admin123 即可登录。"),
  bodyPara(
    "【开发说明】：本地开发默认启用 Mock 区块链引擎，所有存证与交易哈希在内存中模拟生成，秒级响应。开发者完全不需要在本地安装运行笨重的 Hyperledger Fabric 节点网络即可畅通开发全部业务逻辑。"
  ),

  heading2("2.质量门禁与自查命令"),
  bodyPara("在提交代码前，工程师可在终端执行以下轻量门禁进行一键自查："),
  ...preBlock([
    "node scripts/doc-verify.mjs  # 检查代码与文档架构事实零漂移",
    "node scripts/i18n-verify.mjs # 检查前端国际化语言包完整性",
    "cd oaiss-chain-backend && mvn test # 运行后端单元测试套件"
  ]),

  endPara(),
];

/* ---------------- 组装 Document ---------------- */
const doc = new Document({
  creator: "OAISS CHAIN 架构组",
  title: "OAISS CHAIN 双碳链动系统开发手册 - 技术架构与使用说明书",
  styles: {
    default: {
      document: { run: { font: F_SONG, size: 24, color: BLACK } },
      heading1: { 
        run: { font: F_HEI, size: 40, bold: false, color: BLACK }, 
        paragraph: { alignment: AlignmentType.CENTER, spacing: { before: 120, after: 300 } } 
      },
      heading2: { 
        run: { font: F_HEI, size: 32, bold: false, color: BLACK }, 
        paragraph: { spacing: { before: 360, after: 180 } } 
      },
      heading3: { 
        run: { font: F_HEI, size: 26, bold: false, color: BLACK }, 
        paragraph: { spacing: { before: 280, after: 140 } } 
      },
    },
  },
  sections: [
    { 
      properties: { page: PAGE }, 
      headers: { default: mkHeader() }, 
      children: coverSection 
    },
    { 
      properties: { type: SectionType.NEXT_PAGE, page: PAGE }, 
      headers: { default: mkHeader() }, 
      children: tocSection 
    },
    { 
      properties: { 
        type: SectionType.NEXT_PAGE, 
        page: {
          ...PAGE,
          pageNumbers: { start: 3, formatType: NumberFormat.DECIMAL },
        }
      }, 
      headers: { default: mkHeader() }, 
      footers: { default: mkFooter() },
      children: bodySection 
    },
  ],
});

Packer.toBuffer(doc).then((buf) => {
  fs.writeFileSync(OUT_DOCX, buf);
  console.log("Successfully generated:", OUT_DOCX, buf.length, "bytes; body count:", bodySection.length);
});
