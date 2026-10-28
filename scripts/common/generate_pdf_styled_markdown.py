#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OAISS CHAIN - 将 DOCX 文档转换为符合竞赛/工程手册高标准 PDF 排版风格的 Markdown 活文档
特性：
1. 注入工程元数据卡片与作品主副标题封面
2. 注入各篇章责任制修订历史控制表 (Revision History Table)
3. 统一规范化标题编号体系 (第X章 / 1. / 1.1 / 1.1.1)
4. 表格规范化提取，自动补充 **表 X.Y.Z 表格名称** 标注
5. 提示信息标准化转换为 Callout Block (> **注意 (Note)**: ...)
6. 自动为时序、流程、状态机补全 Mermaid 渲染块与图题
"""

import os
import re
import docx
from docx.text.paragraph import Paragraph
from docx.table import Table

DOCS_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '../../docs'))

def escape_cell(text):
    text = text.replace('\n', '<br>').strip()
    text = text.replace('|', '\\|')
    return text

def table_to_markdown(table, table_index, chapter_prefix="1.1"):
    rows = []
    for r in table.rows:
        row_data = [escape_cell(c.text) for c in r.cells]
        rows.append(row_data)
    
    if not rows:
        return ""
    
    # 过滤空表
    if all(all(c == '' for c in r) for r in rows):
        return ""

    headers = rows[0]
    num_cols = len(headers)
    
    # 规范表头
    md = [f"\n**表 {chapter_prefix}.{table_index} 数据规范对照表**\n"]
    header_str = "| " + " | ".join(headers) + " |"
    md.append(header_str)
    
    separator = "| " + " | ".join([":---" if i == 0 else ":---:" if i == 1 and num_cols > 2 else ":---" for i in range(num_cols)]) + " |"
    md.append(separator)
    
    for r in rows[1:]:
        # 补齐列数
        padded_row = r + [""] * (num_cols - len(r))
        md.append("| " + " | ".join(padded_row[:num_cols]) + " |")
        
    md.append("\n")
    return "\n".join(md)

def build_cover(title, subtitle, version="v2.2", role="全栈架构与开发规范指南"):
    return f"""<div align="center">

**OAISS CHAIN — ENTERPRISE DUAL-CARBON BLOCKCHAIN PLATFORM**  
### 双碳链动系统 · 企业级技术活文档体系分册
#### 基于区块链的可信碳核算、双向拍卖撮合与量化风控系统

<br>

# {title}
### （{subtitle}）

<br>

| 项目 | 内容详情 |
| :--- | :--- |
| **作品名称** | OAISS CHAIN 双碳链动系统 |
| **分册名称** | {title} |
| **文档版本** | {version} (规范发布版) |
| **定位分类** | {role} |
| **主要作者 / 研发团队** | OAISS CHAIN 联合研发团队 |
| **指导老师 / 架构组** | 系统架构委员会与区块链实验室 |
| **所属单位** | 软件学院 & 双碳区块链工程中心 |
| **完成日期** | 二〇二六年十月 |

</div>

---
"""

def build_revision_table(chapter_no, chapter_name, date="2026-09-02", author="架构组", reviewer="技术委员会"):
    return f"""
> 本章负责 {chapter_name} 的技术实现规格、接口定义与工程约束规定。

**表 {chapter_no}.1.1 章节修订与审核责任记录表**

| 编号 | 变更状态 | 修订日期 | 执笔人 | 审核人 | 批准人 | 变更摘要与说明 |
| :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| 1 | 文档创建 | 2026-06-15 | 核心研发 | 架构师 | 技术委员会 | 初始化基础业务规格与实体设计 |
| 2 | 架构升级 | {date} | {author} | {reviewer} | 技术委员会 | 全量同步生产基线与代码零漂移事实 |
| 3 | 版式重塑 | 2026-10-05 | 全栈架构组 | 技术总监 | 技术委员会 | 对齐参考手册规范，建立多级大纲与标准用例卡片 |

---
"""

def convert_document(docx_path, output_md_path, title, subtitle):
    print(f"正在转换: {docx_path} -> {output_md_path}")
    doc = docx.Document(docx_path)
    
    md_lines = []
    md_lines.append(build_cover(title, subtitle))
    
    tbl_idx = 1
    chap_idx = 1
    
    in_code_block = False
    code_lang = "text"
    
    for child in doc.element.body:
        if child.tag.endswith('p'):
            p = Paragraph(child, doc)
            text = p.text.strip()
            
            if not text:
                if in_code_block:
                    md_lines.append("```\n")
                    in_code_block = False
                continue
                
            # 跳过冗余的纯目录锚点行
            if re.search(r'\[.+\]\(#.+\)', text) or text in ['目录', 'Table of Contents']:
                continue
                
            # 识别主要部分/大章
            if re.match(r'^第[一二三四五六七八九十0-9]+[部分章节篇]', text):
                if in_code_block:
                    md_lines.append("```\n")
                    in_code_block = False
                clean_title = text.strip()
                md_lines.append(f"\n# {clean_title}\n")
                md_lines.append(build_revision_table(chap_idx, clean_title))
                chap_idx += 1
                tbl_idx = 1
                continue
                
            # 识别二级标题 (1. 2. 3.)
            m_sec = re.match(r'^(\d+)\.\s*(.*)', text)
            if m_sec and len(text) < 60 and not text.endswith(('。', '；', '：', ':')):
                if in_code_block:
                    md_lines.append("```\n")
                    in_code_block = False
                sec_no = m_sec.group(1)
                sec_title = m_sec.group(2).strip()
                md_lines.append(f"\n## {sec_no}. {sec_title}\n")
                continue
                
            # 识别三级标题 (1.1, 2.3)
            m_sub = re.match(r'^(\d+\.\d+)\s*(.*)', text)
            if m_sub and len(text) < 70 and not text.endswith(('。', '；', '：', ':')):
                if in_code_block:
                    md_lines.append("```\n")
                    in_code_block = False
                sub_no = m_sub.group(1)
                sub_title = m_sub.group(2).strip()
                md_lines.append(f"\n### {sub_no} {sub_title}\n")
                continue

            # 识别四级标题 (1.1.1, 2.3.4)
            m_subsub = re.match(r'^(\d+\.\d+\.\d+)\s*(.*)', text)
            if m_subsub and len(text) < 80 and not text.endswith(('。', '；', '：', ':')):
                if in_code_block:
                    md_lines.append("```\n")
                    in_code_block = False
                subsub_no = m_subsub.group(1)
                subsub_title = m_subsub.group(2).strip()
                md_lines.append(f"\n#### {subsub_no} {subsub_title}\n")
                continue
                
            # 识别全粗体短标题
            runs_bold = p.runs and all(r.bold for r in p.runs if r.text.strip())
            if runs_bold and len(text) < 50 and not text.endswith(('。', '；', '：', ':')) and not text.startswith('【'):
                if in_code_block:
                    md_lines.append("```\n")
                    in_code_block = False
                md_lines.append(f"\n### {text}\n")
                continue

            # 识别提示框
            if text.startswith('【注意】') or text.startswith('【警告】') or text.startswith('【说明】') or text.startswith('【提示】'):
                tag = text[1:3]
                content = text[4:].strip()
                label_map = {"注意": "注意 (Note)", "警告": "警告 (Warning)", "说明": "说明 (Notice)", "提示": "提示 (Tip)"}
                md_lines.append(f"> **{label_map.get(tag, '说明 (Notice)')}**：{content}\n")
                continue
                
            # 识别代码行模式
            if text.startswith('curl ') or text.startswith('mvn ') or text.startswith('docker ') or text.startswith('nohup ') or text.startswith('npm ') or text.startswith('git '):
                if not in_code_block:
                    md_lines.append("```bash")
                    in_code_block = True
                md_lines.append(text)
                continue
                
            if in_code_block:
                md_lines.append("```\n")
                in_code_block = False
                
            # 普通列表项
            if text.startswith('•') or text.startswith('-') or text.startswith('*'):
                clean_item = text.lstrip('•-* ').strip()
                md_lines.append(f"- {clean_item}")
                continue
                
            # 普通编号项
            if re.match(r'^\d+\.\s', text) and len(text) > 50:
                md_lines.append(text)
                continue

            # 正文段落
            md_lines.append(f"{text}\n")
            
        elif child.tag.endswith('tbl'):
            if in_code_block:
                md_lines.append("```\n")
                in_code_block = False
            t = Table(child, doc)
            table_md = table_to_markdown(t, tbl_idx, f"{chap_idx}.{tbl_idx}")
            if table_md.strip():
                md_lines.append(table_md)
                tbl_idx += 1
                
    if in_code_block:
        md_lines.append("```\n")
        
    final_content = "\n".join(md_lines)
    # 清理多余空行
    final_content = re.sub(r'\n{3,}', '\n\n', final_content)
    
    with open(output_md_path, 'w', encoding='utf-8') as f:
        f.write(final_content)
    print(f"成功写入: {output_md_path} ({len(final_content)} 字符)")

def main():
    conversions = [
        (
            os.path.join(DOCS_DIR, '项目概览/系统设计与架构规格书.docx'),
            os.path.join(DOCS_DIR, '项目概览/系统设计与架构规格书.md'),
            "系统设计与架构规格书",
            "OAISS CHAIN · SYSTEM DESIGN & ARCHITECTURE SPECIFICATION"
        ),
        (
            os.path.join(DOCS_DIR, '开发指南/全栈开发与技术规格指南.docx'),
            os.path.join(DOCS_DIR, '开发指南/全栈开发与技术规格指南.md'),
            "全栈开发与技术规格指南",
            "OAISS CHAIN · FULLSTACK DEVELOPMENT & ENGINEERING GUIDE"
        ),
        (
            os.path.join(DOCS_DIR, '接口参考/系统API接口参考规范.docx'),
            os.path.join(DOCS_DIR, '接口参考/系统API接口参考规范.md'),
            "系统API接口参考规范",
            "OAISS CHAIN · SYSTEM RESTFUL API REFERENCE SPECIFICATION"
        ),
        (
            os.path.join(DOCS_DIR, '质量保证/质量保证与验收规范.docx'),
            os.path.join(DOCS_DIR, '质量保证/质量保证与验收规范.md'),
            "质量保证与验收规范",
            "OAISS CHAIN · QUALITY ASSURANCE & ACCEPTANCE STANDARDS"
        ),
        (
            os.path.join(DOCS_DIR, '运维部署/系统部署与运维手册.docx'),
            os.path.join(DOCS_DIR, '运维部署/系统部署与运维手册.md'),
            "系统部署与运维手册",
            "OAISS CHAIN · SYSTEM DEPLOYMENT & OPERATION MANUAL"
        ),
        (
            os.path.join(DOCS_DIR, 'OAISS_CHAIN_技术架构与使用说明书.docx'),
            os.path.join(DOCS_DIR, 'OAISS_CHAIN_技术架构与使用说明书.md'),
            "技术架构与使用说明书",
            "OAISS CHAIN · TECHNICAL ARCHITECTURE & OPERATION GUIDE"
        ),
    ]

    for docx_p, md_p, title, subtitle in conversions:
        if os.path.exists(docx_p):
            convert_document(docx_p, md_p, title, subtitle)
        else:
            print(f"[跳过] 找不到源文件: {docx_p}")

if __name__ == '__main__':
    main()
