#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OAISS CHAIN - 按照企业级原始 Word 模板全量重塑 DOCX 交付文档
1. 页面设置: A4 (210mm x 297mm), 左右边距 20mm (56.7pt), 上 25mm (70.9pt), 下 20mm (56.7pt)
2. 页眉: 右对齐工程标头, 底部带贯通版心的 0.5pt 细实线黑边框 (pBdr/w:bottom)
3. 封面:
   - 居中双行大标题 (二号 18pt 宋体加粗): “OAISS CHAIN · 企业级双碳链动系统工程技术规范”
   - 居中竖排六字“系\\n统\\n开\\n发\\n文\\n档” (一号 26pt 宋体加粗, 垂直字距 46.8pt)
   - 元数据两列无边框表格 (总宽 12.45cm, 左列 3.8cm 小三号 15pt 右对齐, 右列 8.6cm 小四号 12pt 居中带 0.5pt 底横线)
   - 底部中文大写日期 (小三号 15pt 宋体居中)
4. 目录:
   - 居中“目  录” (小二号 18pt 宋体加粗)
   - Word 原生点线引导制表位 (Tab Stop: pos="9698", leader="dot", alignment="right")
   - 阶梯式缩进: 一级 0pt, 二级 2字符 (420twips), 三级 4字符 (840twips)
5. 正文与高清图文混排:
   - 自动在核心章节插入 7 张 300 DPI 架构大图并配居中图题
   - 每一大章前置分页, 小三号 15pt 黑体居中加粗
   - 紧随 6 列标准修订历史表 (编号/状态/日期/执笔人/审核人/批准人, 表头 #D9D9D9 灰底, 0.5pt 黑网格)
   - 正文段落: 小四号 12pt 宋体, 首行缩进 2 字符 (24pt / 480twips), 1.5 倍行距, 两端对齐
   - 表格: 全封闭 0.5pt 细黑网格, 表头灰底加粗居中, 表题居中位于表格上方 (10.5pt)
"""

import os
import re
import docx
from docx.shared import Pt, Cm, Mm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml import parse_xml
from docx.oxml.ns import nsdecls, qn

DOCS_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '../../docs'))
ARCH_DIR = os.path.join(DOCS_DIR, 'assets/architecture')

TARGET_DOCX_LIST = [
    (
        os.path.join(DOCS_DIR, 'OAISS_CHAIN_技术架构与使用说明书.md'),
        os.path.join(DOCS_DIR, 'OAISS_CHAIN_技术架构与使用说明书.docx'),
        "OAISS CHAIN 双碳链动系统开发手册",
        "企业级技术架构设计与系统使用说明书"
    ),
    (
        os.path.join(DOCS_DIR, '项目概览/系统设计与架构规格书.md'),
        os.path.join(DOCS_DIR, '项目概览/系统设计与架构规格书.docx'),
        "OAISS CHAIN 双碳链动系统开发手册",
        "系统设计与架构规格说明书（24表数据字典与六边形架构）"
    ),
    (
        os.path.join(DOCS_DIR, '开发指南/全栈开发与技术规格指南.md'),
        os.path.join(DOCS_DIR, '开发指南/全栈开发与技术规格指南.docx'),
        "OAISS CHAIN 双碳链动系统开发手册",
        "全栈开发与技术规格指南（Vue3前端、JVM诊断与Fabric链码）"
    ),
    (
        os.path.join(DOCS_DIR, '接口参考/系统API接口参考规范.md'),
        os.path.join(DOCS_DIR, '接口参考/系统API接口参考规范.docx'),
        "OAISS CHAIN 双碳链动系统开发手册",
        "系统 API 接口参考规范（143 REST 端点与 ML 微服务契约）"
    ),
    (
        os.path.join(DOCS_DIR, '质量保证/质量保证与验收规范.md'),
        os.path.join(DOCS_DIR, '质量保证/质量保证与验收规范.docx'),
        "OAISS CHAIN 双碳链动系统开发手册",
        "质量保证与验收规范（四角色验收标准与发布阻断门禁）"
    ),
    (
        os.path.join(DOCS_DIR, '运维部署/系统部署与运维手册.md'),
        os.path.join(DOCS_DIR, '运维部署/系统部署与运维手册.docx'),
        "OAISS CHAIN 双碳链动系统开发手册",
        "系统部署与运维手册（硬件配置、网络防火墙与多环境编排）"
    ),
]

OTHER_DOCX_LIST = [
    os.path.join(DOCS_DIR, 'raw/01~05原始需求与标准规格书.docx'),
]

IMAGE_INSERTS = {
    '四角色业务模型': ('02-four-roles-collaboration.png', '图 1.1.1 系统多角色核心用例全景图'),
    '技术架构': ('01-system-hexagonal-architecture.png', '图 2.1.1 系统总体分层与六边形微服务拓扑架构图'),
    '密码学体系': ('06-cryptography-envelope-encryption.png', '图 2.2.1 密码学 RSA 数字签名与 AES-GCM 信封加密防伪流转图'),
    '核心业务流转闭环': ('03-carbon-lifecycle-state-machine.png', '图 1.2.1 核心业务流程图'),
    '数据库设计全景': ('04-database-entity-er-diagram.png', '图 4.1.1 数据库 24 张核心业务实体 ER 逻辑关系图'),
    '碳交易与双向拍卖': ('05-double-auction-matching-engine.png', '图 5.2.1 连续双向拍卖撮合引擎与 DVP 结算流转关系图'),
    '发布阻断守则': ('07-release-gates-pipeline.png', '图 4.1.1 企业级持续交付与五级阻断门禁流水线流转图'),
    '六边形架构': ('01-system-hexagonal-architecture.png', '图 2.1.1 系统总体分层与六边形微服务拓扑架构图'),
    '双向拍卖撮合': ('05-double-auction-matching-engine.png', '图 5.2.1 连续双向拍卖撮合引擎与 DVP 结算流转关系图'),
}

def setup_page_layout(section):
    section.page_width = Cm(21.0)
    section.page_height = Cm(29.7)
    section.left_margin = Cm(2.0)
    section.right_margin = Cm(2.0)
    section.top_margin = Cm(2.5)
    section.bottom_margin = Cm(2.0)
    
    header = section.header
    p_head = header.paragraphs[0]
    p_head.text = ""
    p_head.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    r_head = p_head.add_run('The Enterprise Carbon Accounting & Blockchain System · OAISS CHAIN 双碳链动系统')
    r_head.font.name = 'Times New Roman'
    r_head.font.size = Pt(10.5)
    r_head.bold = True
    r_head._r.get_or_add_rPr().append(parse_xml(f'<w:rFonts {nsdecls("w")} w:eastAsia="SimSun"/>'))
    
    pPr_head = p_head._p.get_or_add_pPr()
    for c in list(pPr_head):
        if c.tag.endswith('pBdr'):
            pPr_head.remove(c)
    pPr_head.append(parse_xml(
        f'<w:pBdr {nsdecls("w")}><w:bottom w:val="single" w:sz="4" w:space="1" w:color="auto"/></w:pBdr>'
    ))

def apply_table_grid_style(table):
    tblPr = table._tbl.tblPr
    for c in list(tblPr):
        if c.tag.endswith('tblBorders'):
            tblPr.remove(c)
    tblPr.append(parse_xml(f'''
        <w:tblBorders {nsdecls("w")}>
            <w:top w:val="single" w:sz="4" w:space="0" w:color="auto"/>
            <w:left w:val="single" w:sz="4" w:space="0" w:color="auto"/>
            <w:bottom w:val="single" w:sz="4" w:space="0" w:color="auto"/>
            <w:right w:val="single" w:sz="4" w:space="0" w:color="auto"/>
            <w:insideH w:val="single" w:sz="4" w:space="0" w:color="auto"/>
            <w:insideV w:val="single" w:sz="4" w:space="0" w:color="auto"/>
        </w:tblBorders>
    '''))
    
    if not table.rows:
        return
        
    header_tr = table.rows[0]._tr.get_or_add_trPr()
    header_tr.append(parse_xml(r'<w:tblHeader %s/>' % nsdecls('w')))
    
    for cell in table.rows[0].cells:
        tcPr = cell._tc.get_or_add_tcPr()
        for c in list(tcPr):
            if c.tag.endswith('shd'):
                tcPr.remove(c)
        tcPr.append(parse_xml(r'<w:shd %s w:val="clear" w:color="auto" w:fill="D9D9D9"/>' % nsdecls('w')))
        for p in cell.paragraphs:
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p.paragraph_format.first_line_indent = Pt(0)
            p.paragraph_format.line_spacing = 1.2
            for r in p.runs:
                r.font.name = 'Times New Roman'
                r._r.get_or_add_rPr().append(parse_xml(f'<w:rFonts {nsdecls("w")} w:eastAsia="SimSun"/>'))
                r.font.size = Pt(10.5)
                r.font.bold = True
                
    for r_idx in range(1, len(table.rows)):
        row = table.rows[r_idx]
        row._tr.get_or_add_trPr().append(parse_xml(r'<w:cantSplit %s/>' % nsdecls('w')))
        for cell in row.cells:
            for p in cell.paragraphs:
                p.paragraph_format.first_line_indent = Pt(0)
                p.paragraph_format.line_spacing = 1.2
                for r in p.runs:
                    r.font.name = 'Times New Roman'
                    r._r.get_or_add_rPr().append(parse_xml(f'<w:rFonts {nsdecls("w")} w:eastAsia="SimSun"/>'))
                    r.font.size = Pt(10.5)

def create_official_cover(doc, main_title, sub_title, doc_date="二〇二六年十月"):
    p_t1 = doc.add_paragraph()
    p_t1.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_t1.paragraph_format.space_before = Pt(14)
    p_t1.paragraph_format.space_after = Pt(4)
    r_t1 = p_t1.add_run('OAISS CHAIN · 企业级双碳链动系统工程技术规范')
    r_t1.font.name = 'SimSun'
    r_t1.font.size = Pt(18)
    r_t1.bold = True
    r_t1._r.get_or_add_rPr().append(parse_xml(f'<w:rFonts {nsdecls("w")} w:eastAsia="SimSun"/>'))

    p_t2 = doc.add_paragraph()
    p_t2.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_t2.paragraph_format.space_before = Pt(0)
    p_t2.paragraph_format.space_after = Pt(20)
    r_t2 = p_t2.add_run(f'{main_title}\n{sub_title}')
    r_t2.font.name = 'SimSun'
    r_t2.font.size = Pt(16)
    r_t2.bold = True
    r_t2._r.get_or_add_rPr().append(parse_xml(f'<w:rFonts {nsdecls("w")} w:eastAsia="SimSun"/>'))

    # 竖排六字“系 统 开 发 文 档”
    for ch in ['系', '统', '开', '发', '文', '档']:
        p_ch = doc.add_paragraph()
        p_ch.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p_ch.paragraph_format.space_before = Pt(3)
        p_ch.paragraph_format.space_after = Pt(3)
        r_ch = p_ch.add_run(ch)
        r_ch.font.name = 'SimSun'
        r_ch.font.size = Pt(26)
        r_ch.bold = True
        r_ch._r.get_or_add_rPr().append(parse_xml(f'<w:rFonts {nsdecls("w")} w:eastAsia="SimSun"/>'))

    p_gap = doc.add_paragraph()
    p_gap.paragraph_format.space_before = Pt(16)

    # 元数据两列无边框表格 (等宽下划线)
    meta_table = doc.add_table(rows=4, cols=2)
    meta_table.alignment = WD_TABLE_ALIGNMENT.CENTER
    meta_table._tbl.tblPr.append(parse_xml(f'''
        <w:tblBorders {nsdecls("w")}>
            <w:top w:val="none"/><w:left w:val="none"/><w:bottom w:val="none"/><w:right w:val="none"/>
            <w:insideH w:val="none"/><w:insideV w:val="none"/>
        </w:tblBorders>
    '''))

    meta_items = [
        ('作品名称：', 'OAISS CHAIN 双碳链动系统'),
        ('作    者：', 'OAISS CHAIN 联合研发团队'),
        ('指导老师：', '系统架构委员会 & 双碳区块链工程实验室'),
        ('单    位：', '软件学院 & 双碳区块链工程中心')
    ]

    for idx, (label, val) in enumerate(meta_items):
        row = meta_table.rows[idx]
        cell_l, cell_r = row.cells[0], row.cells[1]
        cell_l.width = Cm(3.8)
        cell_r.width = Cm(8.6)

        p_l = cell_l.paragraphs[0]
        p_l.alignment = WD_ALIGN_PARAGRAPH.RIGHT
        p_l.paragraph_format.space_before = Pt(4)
        p_l.paragraph_format.space_after = Pt(4)
        rl = p_l.add_run(label)
        rl.font.name = 'SimSun'
        rl.font.size = Pt(15)
        rl._r.get_or_add_rPr().append(parse_xml(f'<w:rFonts {nsdecls("w")} w:eastAsia="SimSun"/>'))

        p_r = cell_r.paragraphs[0]
        p_r.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p_r.paragraph_format.space_before = Pt(4)
        p_r.paragraph_format.space_after = Pt(4)
        rr = p_r.add_run(val)
        rr.font.name = 'SimSun'
        rr.font.size = Pt(12)
        rr._r.get_or_add_rPr().append(parse_xml(f'<w:rFonts {nsdecls("w")} w:eastAsia="SimSun"/>'))

        cell_r._tc.get_or_add_tcPr().append(parse_xml(
            f'<w:tcBorders {nsdecls("w")}><w:bottom w:val="single" w:sz="4" w:space="0" w:color="auto"/></w:tcBorders>'
        ))

    p_date = doc.add_paragraph()
    p_date.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_date.paragraph_format.space_before = Pt(32)
    r_date = p_date.add_run(doc_date)
    r_date.font.name = 'SimSun'
    r_date.font.size = Pt(15)
    r_date._r.get_or_add_rPr().append(parse_xml(f'<w:rFonts {nsdecls("w")} w:eastAsia="SimSun"/>'))

def create_official_toc(doc, headings_tree):
    doc.add_page_break()
    p_toc_title = doc.add_paragraph()
    p_toc_title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_toc_title.paragraph_format.space_before = Pt(12)
    p_toc_title.paragraph_format.space_after = Pt(18)
    r_title = p_toc_title.add_run('目  录')
    r_title.font.name = 'SimSun'
    r_title.font.size = Pt(18)
    r_title.bold = True
    r_title._r.get_or_add_rPr().append(parse_xml(f'<w:rFonts {nsdecls("w")} w:eastAsia="SimSun"/>'))

    page_counter = 4
    for lvl, title in headings_tree[:35]:
        p = doc.add_paragraph()
        pPr = p._p.get_or_add_pPr()
        pPr.append(parse_xml(
            f'<w:tabs {nsdecls("w")}><w:tab w:val="right" w:leader="dot" w:pos="9698"/></w:tabs>'
        ))
        indent_val = (lvl - 1) * 420
        pPr.append(parse_xml(f'<w:ind {nsdecls("w")} w:left="{indent_val}"/>'))
        
        p.paragraph_format.space_before = Pt(2)
        p.paragraph_format.space_after = Pt(2)
        p.paragraph_format.line_spacing = 1.15

        rt = p.add_run(title)
        rt.font.name = 'SimSun'
        rt.font.size = Pt(10.5)
        if lvl == 1:
            rt.bold = True
        rt._r.get_or_add_rPr().append(parse_xml(f'<w:rFonts {nsdecls("w")} w:eastAsia="SimSun"/>'))

        p.add_run('\t')

        rp = p.add_run(str(page_counter))
        rp.font.name = 'Times New Roman'
        rp.font.size = Pt(10.5)
        page_counter += 1

def add_chapter_landing(doc, chapter_title, chap_count=1):
    doc.add_page_break()
    p_chap = doc.add_paragraph()
    p_chap.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_chap.paragraph_format.space_before = Pt(36)
    p_chap.paragraph_format.space_after = Pt(20)
    r_chap = p_chap.add_run(chapter_title)
    r_chap.font.name = 'SimHei'
    r_chap.font.size = Pt(15)
    r_chap.bold = True
    r_chap._r.get_or_add_rPr().append(parse_xml(f'<w:rFonts {nsdecls("w")} w:eastAsia="SimHei"/>'))

    rev_tbl = doc.add_table(rows=3, cols=6)
    rev_tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
    headers = ['编号', '状态', '日期', '执笔人', '审核人', '批准人']
    for idx, h in enumerate(headers):
        rev_tbl.rows[0].cells[idx].text = h

    data = [
        ('1', '创建文档', '06.15', '核心研发', '架构师', '技术委员会'),
        ('2', '修订基线', '10.08', '全栈架构组', '技术总监', '技术委员会')
    ]
    for r_idx, row in enumerate(data):
        for c_idx, val in enumerate(row):
            rev_tbl.rows[r_idx + 1].cells[c_idx].text = val

    apply_table_grid_style(rev_tbl)
    
    p_gap = doc.add_paragraph()
    p_gap.paragraph_format.space_before = Pt(14)

def check_and_insert_image(doc, text, inserted_images):
    """根据段落文本判断是否需要插入架构大图"""
    for kw, (img_name, caption) in IMAGE_INSERTS.items():
        if kw in text and img_name not in inserted_images:
            img_path = os.path.join(ARCH_DIR, img_name)
            if os.path.exists(img_path):
                p_img = doc.add_paragraph()
                p_img.alignment = WD_ALIGN_PARAGRAPH.CENTER
                p_img.paragraph_format.first_line_indent = Pt(0)
                p_img.paragraph_format.space_before = Pt(12)
                p_img.paragraph_format.space_after = Pt(4)
                r_img = p_img.add_run()
                r_img.add_picture(img_path, width=Cm(15.5))
                
                p_cap = doc.add_paragraph()
                p_cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
                p_cap.paragraph_format.first_line_indent = Pt(0)
                p_cap.paragraph_format.space_before = Pt(2)
                p_cap.paragraph_format.space_after = Pt(10)
                r_cap = p_cap.add_run(caption)
                r_cap.font.name = 'Times New Roman'
                r_cap.font.size = Pt(10.5)
                r_cap.bold = True
                r_cap._r.get_or_add_rPr().append(parse_xml(f'<w:rFonts {nsdecls("w")} w:eastAsia="SimSun"/>'))
                inserted_images.add(img_name)
                return True
    return False

def parse_markdown_to_word_doc(md_path, docx_path, main_title, sub_title):
    print(f"正在全量编译生成原始 Word 模板: {docx_path}")
    doc = docx.Document()
    setup_page_layout(doc.sections[0])

    with open(md_path, 'r', encoding='utf-8') as f:
        md_text = f.read()

    headings_tree = []
    lines = md_text.split('\n')
    for line in lines:
        s = line.strip()
        if re.match(r'^#\s+第[一二三四五六七八九十0-9]+[章节部分篇]', s):
            headings_tree.append((1, s.lstrip('#').strip()))
        elif re.match(r'^##\s+\d+\.\s+', s):
            headings_tree.append((2, s.lstrip('#').strip()))
        elif re.match(r'^###\s+\d+\.\d+\s+', s):
            headings_tree.append((3, s.lstrip('#').strip()))

    # 1. 封面
    create_official_cover(doc, main_title, sub_title)

    # 2. 目录页
    create_official_toc(doc, headings_tree)

    # 3. 正文解析与排版
    # 仅剔除文首包含“作品”或“工程要素”的元数据封面卡片，绝不误删正文架构大图
    clean_text = re.sub(r'<div align="center">\s*[\s\S]*?(?:作品|工程要素)[\s\S]*?</div>\s*---\s*', '', md_text, count=1)
    clean_text = re.sub(r'## 目 录[\s\S]*?---\s*', '', clean_text)
    clean_lines = clean_text.split('\n')

    i = 0
    n = len(clean_lines)
    chap_count = 1
    table_lines = []
    inserted_images = set()

    while i < n:
        line = clean_lines[i]
        s = line.strip()

        # 表格收集
        if s.startswith('|') and s.endswith('|'):
            table_lines.append(s)
            i += 1
            continue
        elif table_lines:
            rows_data = []
            for tl in table_lines:
                if re.match(r'^\|\s*:?---+:?\s*\|', tl):
                    continue
                cells = [c.strip() for c in tl.strip('|').split('|')]
                rows_data.append(cells)
            if rows_data:
                num_cols = max(len(r) for r in rows_data)
                tbl = doc.add_table(rows=len(rows_data), cols=num_cols)
                tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
                for r_idx, r_cells in enumerate(rows_data):
                    for c_idx, c_text in enumerate(r_cells):
                        clean_c = c_text.replace('<br>', '\n').replace('\\|', '|').replace('`', '')
                        tbl.rows[r_idx].cells[c_idx].text = clean_c
                apply_table_grid_style(tbl)
            table_lines = []

        if not s:
            i += 1
            continue

        # 代码块
        if s.startswith('```'):
            code_lines = []
            i += 1
            while i < n and not clean_lines[i].strip().startswith('```'):
                code_lines.append(clean_lines[i])
                i += 1
            p_code = doc.add_paragraph()
            p_code.paragraph_format.first_line_indent = Pt(0)
            p_code.paragraph_format.line_spacing = 1.15
            p_code.paragraph_format.space_before = Pt(4)
            p_code.paragraph_format.space_after = Pt(4)
            r_c = p_code.add_run('\n'.join(code_lines))
            r_c.font.name = 'Consolas'
            r_c.font.size = Pt(9.5)
            r_c.font.color.rgb = RGBColor(40, 40, 40)
            i += 1
            continue

        # 原生图片标签解析 (<img src="...">)
        if '<img src=' in s or (s.startswith('![') and '.png' in s):
            m_src = re.search(r'src=["\']([^"\']+)["\']', s)
            if not m_src:
                m_src = re.search(r'\(([^)]+\.png)\)', s)
            if m_src:
                img_rel = m_src.group(1)
                img_name = os.path.basename(img_rel)
                img_full_path = os.path.join(ARCH_DIR, img_name)
                if os.path.exists(img_full_path) and img_name not in inserted_images:
                    p_img = doc.add_paragraph()
                    p_img.alignment = WD_ALIGN_PARAGRAPH.CENTER
                    p_img.paragraph_format.first_line_indent = Pt(0)
                    p_img.paragraph_format.space_before = Pt(12)
                    p_img.paragraph_format.space_after = Pt(4)
                    r_img = p_img.add_run()
                    r_img.add_picture(img_full_path, width=Cm(15.5))
                    inserted_images.add(img_name)
            i += 1
            continue

        # 忽略单纯的排版 div 标签与换行
        if s.startswith('<div') or s.startswith('</div') or s == '<br>':
            i += 1
            continue

        # 一级大章
        if re.match(r'^#\s+第[一二三四五六七八九十0-9]+[章节部分篇]', s):
            chapter_title = s.lstrip('#').strip()
            add_chapter_landing(doc, chapter_title, chap_count)
            chap_count += 1
            i += 1
            continue

        # 二级节 (1. 2. 3.)
        if re.match(r'^##\s+\d+\.\s+', s):
            p = doc.add_paragraph()
            p.alignment = WD_ALIGN_PARAGRAPH.LEFT
            p.paragraph_format.first_line_indent = Pt(0)
            p.paragraph_format.space_before = Pt(14)
            p.paragraph_format.space_after = Pt(8)
            sec_text = s.lstrip('#').strip()
            r = p.add_run(sec_text)
            r.font.name = 'Arial'
            r.font.size = Pt(14)
            r.bold = True
            r._r.get_or_add_rPr().append(parse_xml(f'<w:rFonts {nsdecls("w")} w:eastAsia="SimHei"/>'))
            
            # 检测并插入对应关系大图
            check_and_insert_image(doc, sec_text, inserted_images)
            i += 1
            continue

        # 三级小节 (1.1, 2.3)
        if re.match(r'^###\s+\d+\.\d+\s+', s):
            p = doc.add_paragraph()
            p.alignment = WD_ALIGN_PARAGRAPH.LEFT
            p.paragraph_format.first_line_indent = Pt(0)
            p.paragraph_format.space_before = Pt(8)
            p.paragraph_format.space_after = Pt(4)
            subsec_text = s.lstrip('#').strip()
            r = p.add_run(subsec_text)
            r.font.name = 'Times New Roman'
            r.font.size = Pt(12)
            r.bold = True
            r._r.get_or_add_rPr().append(parse_xml(f'<w:rFonts {nsdecls("w")} w:eastAsia="SimSun"/>'))
            
            # 检测并插入对应关系大图
            check_and_insert_image(doc, subsec_text, inserted_images)
            i += 1
            continue

        # 四级条目 (1.1.1, 2.3.4)
        if re.match(r'^####\s+\d+\.\d+\.\d+\s+', s):
            p = doc.add_paragraph()
            p.alignment = WD_ALIGN_PARAGRAPH.LEFT
            p.paragraph_format.first_line_indent = Pt(0)
            p.paragraph_format.space_before = Pt(6)
            p.paragraph_format.space_after = Pt(2)
            r = p.add_run(s.lstrip('#').strip())
            r.font.name = 'Times New Roman'
            r.font.size = Pt(12)
            r.bold = True
            r._r.get_or_add_rPr().append(parse_xml(f'<w:rFonts {nsdecls("w")} w:eastAsia="SimSun"/>'))
            i += 1
            continue

        # 表题与图题 (置于上方/下方居中, 10.5pt)
        if s.startswith('**表 ') or s.startswith('**图 ') or s.startswith('<b>图 '):
            p = doc.add_paragraph()
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p.paragraph_format.first_line_indent = Pt(0)
            p.paragraph_format.space_before = Pt(6)
            p.paragraph_format.space_after = Pt(4)
            clean_cap = re.sub(r'[*<>/b]', '', s).strip()
            r = p.add_run(clean_cap)
            r.font.name = 'Times New Roman'
            r.font.size = Pt(10.5)
            r.bold = True
            r._r.get_or_add_rPr().append(parse_xml(f'<w:rFonts {nsdecls("w")} w:eastAsia="SimSun"/>'))
            i += 1
            continue

        # 提示引用块
        if s.startswith('>'):
            p = doc.add_paragraph()
            p.paragraph_format.first_line_indent = Pt(0)
            p.paragraph_format.line_spacing = 1.35
            p.paragraph_format.space_before = Pt(4)
            p.paragraph_format.space_after = Pt(4)
            r = p.add_run(s.lstrip('> ').strip())
            r.font.name = 'Times New Roman'
            r.font.size = Pt(11)
            r.font.italic = True
            r.font.color.rgb = RGBColor(50, 50, 50)
            r._r.get_or_add_rPr().append(parse_xml(f'<w:rFonts {nsdecls("w")} w:eastAsia="SimSun"/>'))
            i += 1
            continue

        # 正文普通段落
        p = doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
        if s.startswith('•') or s.startswith('-') or s.startswith('*'):
            p.paragraph_format.first_line_indent = Pt(0)
        else:
            p.paragraph_format.first_line_indent = Pt(24)
        p.paragraph_format.line_spacing = 1.5
        p.paragraph_format.space_before = Pt(0)
        p.paragraph_format.space_after = Pt(0)

        clean_text_run = s.replace('`', '')
        r = p.add_run(clean_text_run)
        r.font.name = 'Times New Roman'
        r.font.size = Pt(12)
        r._r.get_or_add_rPr().append(parse_xml(f'<w:rFonts {nsdecls("w")} w:eastAsia="SimSun"/>'))
        i += 1

    temp_save = docx_path + '.tmp'
    doc.save(temp_save)
    os.replace(temp_save, docx_path)
    print(f"成功生成原始 Word 模板交付物: {docx_path} (嵌入架构大图: {len(inserted_images)} 张)")

def format_existing_docx_official(file_path):
    print(f"正在以原始 Word 模板标准格式化现有交付物: {file_path}")
    doc = docx.Document(file_path)
    for sec in doc.sections:
        setup_page_layout(sec)
    for tbl in doc.tables:
        apply_table_grid_style(tbl)
    temp_save = file_path + '.tmp'
    doc.save(temp_save)
    os.replace(temp_save, file_path)
    print(f"完成格式化: {file_path}")

def main():
    for md_p, docx_p, m_title, s_title in TARGET_DOCX_LIST:
        if os.path.exists(md_p):
            parse_markdown_to_word_doc(md_p, docx_p, m_title, s_title)
        else:
            print(f"[跳过] 找不到源文件: {md_p}")

    for f in OTHER_DOCX_LIST:
        if os.path.exists(f):
            format_existing_docx_official(f)
        else:
            print(f"[跳过] 找不到文件: {f}")

if __name__ == '__main__':
    main()
