#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OAISS CHAIN - 按照参考 PDF (软件杯OAISS CHAIN 双碳链动系统开发手册) 严格复刻 Markdown 版式、色调与目录
1. 注入对标 PDF 的标准高保真 CSS 样式表 (A4 页面规格、SimSun/SimHei 字体栈、1.5倍行距、2字符首行缩进、全封闭黑边框、#D9D9D9表头灰底)
2. 自动提取各级标题生成标准点线引导符目录 (<div class="toc-box">...</div>)
3. 规范化表格与图表标题居中呈现
"""

import os
import re

DOCS_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '../../docs'))

TARGET_MD_FILES = [
    os.path.join(DOCS_DIR, '文档索引.md'),
    os.path.join(DOCS_DIR, '产品规格/AI智能预测模块深度重塑与LLM融合升级说明.md'),
    os.path.join(DOCS_DIR, '证据归档/README.md'),
    os.path.join(DOCS_DIR, '项目概览/系统设计与架构规格书.md'),
    os.path.join(DOCS_DIR, '开发指南/全栈开发与技术规格指南.md'),
    os.path.join(DOCS_DIR, '接口参考/系统API接口参考规范.md'),
    os.path.join(DOCS_DIR, '质量保证/质量保证与验收规范.md'),
    os.path.join(DOCS_DIR, '运维部署/系统部署与运维手册.md'),
    os.path.join(DOCS_DIR, 'OAISS_CHAIN_技术架构与使用说明书.md'),
]

CSS_HEADER = """<style>
/* ==========================================================================
   OAISS CHAIN 技术规范手册打印级排版样式表 (严格对标参考 PDF 视觉规范)
   - 色调体系: 单色工科级调色板 (纯黑 #000000, 表头灰 #D9D9D9, 页面白 #FFFFFF)
   - 字体体系: 中文 SimSun (宋体) / SimHei (黑体), 西文 Times New Roman / Arial
   - 版面参数: 1.5 倍行距, 正文首行缩进 2 字符 (24pt), 表格 0.5pt 全网格边框
   ========================================================================== */

body, .markdown-body {
  font-family: "SimSun", "Times New Roman", serif;
  font-size: 12pt;
  line-height: 1.5;
  color: #000000;
  background-color: #ffffff;
  padding: 20px 24px;
}

/* 一级标题 (章) - 仿黑体小三号 15pt 居中加粗 */
h1 {
  font-family: "SimHei", "Times New Roman", sans-serif;
  font-size: 15pt;
  font-weight: bold;
  text-align: center;
  margin-top: 24pt;
  margin-bottom: 14pt;
  color: #000000;
  border-bottom: none !important;
  page-break-before: always;
}

/* 二级标题 (节) - 仿黑体四号 14pt 顶格加粗 */
h2 {
  font-family: "Arial", "SimHei", sans-serif;
  font-size: 14pt;
  font-weight: bold;
  text-align: left;
  margin-top: 18pt;
  margin-bottom: 8pt;
  color: #000000;
  border-bottom: none !important;
}

/* 三级标题 (小节) - 仿宋体粗体小四号 12pt 顶格加粗 */
h3 {
  font-family: "SimSun", "Times New Roman", serif;
  font-size: 12pt;
  font-weight: bold;
  text-align: left;
  margin-top: 12pt;
  margin-bottom: 6pt;
  color: #000000;
}

/* 四级标题 (条目) - 仿宋体粗体小四号 12pt */
h4 {
  font-family: "SimSun", "Times New Roman", serif;
  font-size: 12pt;
  font-weight: bold;
  text-align: left;
  margin-top: 8pt;
  margin-bottom: 4pt;
  color: #000000;
}

/* 正文段落: 严格 2 字符首行缩进 + 1.5 倍行距 + 两端对齐 */
p {
  text-align: justify;
  text-indent: 2em;
  margin-top: 0;
  margin-bottom: 6pt;
  line-height: 1.5;
  color: #000000;
}

/* 居中区域、表格内部、代码块与提示框免缩进 */
div[align="center"] p, table p, blockquote p, pre p, .no-indent {
  text-indent: 0 !important;
}

/* 表格全局规范: 0.5pt 黑色全网格线 + #D9D9D9 灰底表头 */
table {
  width: 100%;
  border-collapse: collapse !important;
  margin: 10pt 0 16pt 0;
  font-size: 10.5pt;
  color: #000000;
  border: 0.5pt solid #000000 !important;
}

table th, table td {
  border: 0.5pt solid #000000 !important;
  padding: 4.5pt 6pt;
  color: #000000;
  line-height: 1.35;
}

table th {
  background-color: #D9D9D9 !important;
  font-weight: bold;
  text-align: center;
}

/* 提示引用框 */
blockquote {
  border-left: 3pt solid #000000 !important;
  background-color: #f7f7f7 !important;
  padding: 8pt 14pt;
  margin: 10pt 0;
  color: #000000;
}

blockquote p {
  text-indent: 0 !important;
}

/* 目录 (TOC) 点线对齐引导排版容器 */
.toc-box {
  margin: 20pt 0 28pt 0;
  padding: 16pt 20pt;
  border: 0.5pt solid #000000;
  background-color: #fafafa;
}

.toc-title {
  font-family: "SimSun", serif;
  font-size: 18pt;
  font-weight: bold;
  text-align: center;
  margin-bottom: 14pt;
  color: #000000;
}

.toc-item {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin: 3.5pt 0;
  font-size: 10.5pt;
  line-height: 1.4;
  color: #000000;
}

.toc-item.level-1 {
  font-weight: bold;
  margin-top: 7pt;
}

.toc-item.level-2 {
  padding-left: 1.8em;
}

.toc-item.level-3 {
  padding-left: 3.6em;
}

.toc-item-title {
  flex-shrink: 0;
}

.toc-item-dots {
  flex-grow: 1;
  border-bottom: 1px dotted #000000;
  margin: 0 4pt;
}

.toc-item-page {
  flex-shrink: 0;
  font-family: "Times New Roman", serif;
}
</style>
"""

def extract_headings(text):
    headings = []
    lines = text.split('\n')
    page_counter = 1
    
    for line in lines:
        s = line.strip()
        # 跳过封面与目录本身的标题
        if s.startswith('# ') and not any(k in s for k in ['开 发', '索 引', '规 格 书', '指 南', '手 册', '说 明 书', '规范']):
            clean = s[2:].strip()
            headings.append((1, clean, page_counter))
            page_counter += 1
        elif s.startswith('## '):
            clean = s[3:].strip()
            headings.append((2, clean, page_counter))
            if len(headings) % 3 == 0:
                page_counter += 1
        elif s.startswith('### ') and not s.startswith('### （') and not s.startswith('### 双碳'):
            clean = s[4:].strip()
            headings.append((3, clean, page_counter))
            
    return headings

def build_toc_html(headings):
    if not headings:
        return ""
        
    html = ['\n<div class="toc-box">\n<div class="toc-title">目  录</div>\n']
    
    # 限制前 35 个关键目录项，保持紧凑美观
    for lvl, title, page in headings[:35]:
        cls = f"level-{lvl}"
        html.append(f'  <div class="toc-item {cls}">'
                    f'<span class="toc-item-title">{title}</span>'
                    f'<span class="toc-item-dots"></span>'
                    f'<span class="toc-item-page">{page}</span>'
                    f'</div>')
        
    html.append('</div>\n\n---\n')
    return "\n".join(html)

def reformat_markdown_file(file_path):
    print(f"正在重排 Markdown 样式与点线目录: {file_path}")
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # 移除现存的旧 style
    content = re.sub(r'<style>[\s\S]*?</style>\n*', '', content)
    # 移除旧的 toc-box
    content = re.sub(r'<div class="toc-box">[\s\S]*?</div>\n*---\n*', '', content)

    headings = extract_headings(content)
    toc_html = build_toc_html(headings)
    
    # 找到封面第一屏横线分割符位置 (通常在第一个 "---" 后)
    cover_match = re.search(r'(</div>\s*\n\s*---\s*\n)', content)
    if cover_match:
        split_pos = cover_match.end()
        head_part = content[:split_pos]
        tail_part = content[split_pos:]
        new_content = CSS_HEADER + "\n" + head_part + "\n" + toc_html + tail_part
    else:
        new_content = CSS_HEADER + "\n" + toc_html + content

    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(new_content)
    print(f"成功重排: {file_path} (提取 {len(headings)} 级目录项并注入印刷级 CSS)")

def main():
    for f in TARGET_MD_FILES:
        if os.path.exists(f):
            reformat_markdown_file(f)
        else:
            print(f"[跳过] 找不到文件: {f}")

if __name__ == '__main__':
    main()
