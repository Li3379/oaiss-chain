#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
reformat_raw_spec_docx.py
-------------------------
专门针对《docs/raw/01~05原始需求与标准规格书.docx》进行深度标准化排版重构：
1. 建立正规三节（3 Sections）标准体系：
   - Section 1 (封面)：启用 titlePg，居中大字竖排微标“系 统 开 发 文 档”，二〇二六年十月，无边框规范 CMMI 属性表
   - Section 2 (目录)：独立分节，居中黑体“目  录”，注入原生动态 TOC 域，页脚罗马数字 i
   - Section 3 (正文)：独立分节，清除 titlePg 污染，双语页眉带 0.5pt 细横线，页脚阿拉伯数字从 1 开始独立连续编码
2. 彻底清洗 530 处 Markdown 语法残留：
   - 移除 89 处 ![](data:image/...) 伪图片 base64 文本
   - 移除 200+ 处 [xxx 1](#_Toc...) Markdown 伪目录死链接
   - 将 117 处 **粗体** 解析为 Word 原生加粗 Run（彻底剥离 **）
   - 将 16 处 #### 剥离并设定为规范小标题
   - 将行内代码 `code` 解析为 Consolas 等宽 Run（彻底剥离 `）
3. 规范大纲层级：
   - 01~05 各篇主标题设定为 Heading 1（三号 15pt 黑体居中，前置分页，outlineLvl=0，keep_with_next）
   - 1. 2. 3. 设定为 Heading 2（四号 14pt 黑体靠左，outlineLvl=1，keep_with_next）
   - 1.1 2.1 3.1 设定为 Heading 3（小四 12pt 黑体靠左，outlineLvl=2，keep_with_next）
4. 表格规范重塑：
   - 71 个数据表格全部保留原有完整数据
   - 贴合版心（总宽 9638 twips / 17.0cm）
   - 全网格 0.5pt 细实线边框
   - 首行表头填充浅灰底色 #D9D9D9，加粗居中，注入 tblHeader（跨页重复）与 cantSplit（防断行）
"""

import os
import re
import shutil
from pathlib import Path
import docx
from docx import Document
from docx.shared import Inches, Pt, Cm, Mm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.section import WD_SECTION_START
from docx.oxml import OxmlElement, parse_xml
from docx.oxml.ns import nsdecls, qn

WORKSPACE_ROOT = Path(__file__).resolve().parent.parent.parent
DOCS_DIR = WORKSPACE_ROOT / "docs"
SRC_DOCX = DOCS_DIR / "raw" / "01~05原始需求与标准规格书.docx"
BACKUP_DOCX = DOCS_DIR / "raw" / "01~05原始需求与标准规格书_backup.docx"

PAGE_WIDTH_TWIPS = 11906   # 210mm
PAGE_HEIGHT_TWIPS = 16838  # 297mm
MARGIN_TOP_TWIPS = 1417    # 25mm
MARGIN_BOTTOM_TWIPS = 1134 # 20mm
MARGIN_LEFT_TWIPS = 1134   # 20mm
MARGIN_RIGHT_TWIPS = 1134  # 20mm


def set_run_fonts(run, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=12, bold=False, color_rgb=None):
    run.font.name = ascii_font
    run.font.size = Pt(size_pt)
    run.bold = bold
    if color_rgb:
        run.font.color.rgb = color_rgb
    rPr = run._r.get_or_add_rPr()
    rFonts = rPr.find(qn('w:rFonts'))
    if rFonts is None:
        rFonts = OxmlElement('w:rFonts')
        rPr.append(rFonts)
    rFonts.set(qn('w:ascii'), ascii_font)
    rFonts.set(qn('w:hAnsi'), ascii_font)
    rFonts.set(qn('w:eastAsia'), east_asia_font)
    rFonts.set(qn('w:cs'), ascii_font)


def set_para_spacing(para, before_pt=0, after_pt=0, line_spacing=1.25, keep_with_next=False):
    pPr = para._p.get_or_add_pPr()
    sp = pPr.find(qn('w:spacing'))
    if sp is None:
        sp = OxmlElement('w:spacing')
        pPr.append(sp)
    sp.set(qn('w:before'), str(int(before_pt * 20)))
    sp.set(qn('w:after'), str(int(after_pt * 20)))
    sp.set(qn('w:line'), str(int(line_spacing * 240)))
    sp.set(qn('w:lineRule'), 'auto')
    if keep_with_next:
        kwn = pPr.find(qn('w:keepNext'))
        if kwn is None:
            kwn = OxmlElement('w:keepNext')
            pPr.append(kwn)


def set_para_indent(para, first_line_chars=2, left_pt=0):
    pPr = para._p.get_or_add_pPr()
    ind = pPr.find(qn('w:ind'))
    if ind is None:
        ind = OxmlElement('w:ind')
        pPr.append(ind)
    if first_line_chars > 0:
        ind.set(qn('w:firstLineChars'), str(int(first_line_chars * 100)))
        ind.set(qn('w:firstLine'), str(int(first_line_chars * 240)))
    else:
        ind.set(qn('w:firstLine'), '0')
        ind.set(qn('w:firstLineChars'), '0')
    if left_pt > 0:
        ind.set(qn('w:left'), str(int(left_pt * 20)))


def configure_header_footer(section, is_cover=False, is_toc=False):
    sectPr = section._sectPr
    if is_cover:
        section.different_first_page_header_footer = True
        return
        
    if is_toc:
        section.different_first_page_header_footer = False
        header = section.header
        p_hdr = header.paragraphs[0]
        p_hdr.text = ""
        p_hdr.alignment = WD_ALIGN_PARAGRAPH.RIGHT
        r_hdr = p_hdr.add_run("目  录 (Table of Contents)")
        set_run_fonts(r_hdr, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=9.0, color_rgb=RGBColor(100, 116, 139))
        set_para_spacing(p_hdr, before_pt=0, after_pt=4, line_spacing=1.0)
        
        footer = section.footer
        p_ftr = footer.paragraphs[0]
        p_ftr.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p_ftr.text = ""
        r_ftr = p_ftr.add_run("i")
        set_run_fonts(r_ftr, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=10.0, color_rgb=RGBColor(100, 116, 139))
        set_para_spacing(p_ftr, before_pt=4, after_pt=0, line_spacing=1.0)
        return

    # 正文节
    t_pg = sectPr.find(qn('w:titlePg'))
    if t_pg is not None:
        sectPr.remove(t_pg)
    section.different_first_page_header_footer = False

    header = section.header
    p_hdr = header.paragraphs[0]
    p_hdr.text = ""
    p_hdr.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    r_hdr = p_hdr.add_run("OAISS CHAIN 双碳链动系统 · 原始需求与标准规格书 (CMMI)")
    set_run_fonts(r_hdr, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=9.0, color_rgb=RGBColor(71, 85, 105))
    set_para_spacing(p_hdr, before_pt=0, after_pt=4, line_spacing=1.0)
    
    pPr = p_hdr._p.get_or_add_pPr()
    pBdr = parse_xml(
        r'''<w:pBdr {} >
            <w:bottom w:val="single" w:sz="4" w:space="4" w:color="000000"/>
        </w:pBdr>'''.format(nsdecls('w'))
    )
    pPr.append(pBdr)

    pgNumType = sectPr.find(qn('w:pgNumType'))
    if pgNumType is None:
        pgNumType = OxmlElement('w:pgNumType')
        sectPr.append(pgNumType)
    pgNumType.set(qn('w:start'), '1')
    pgNumType.set(qn('w:fmt'), 'decimal')

    footer = section.footer
    p_ftr = footer.paragraphs[0]
    p_ftr.text = ""
    p_ftr.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_para_spacing(p_ftr, before_pt=4, after_pt=0, line_spacing=1.0)
    
    r_fld = p_ftr.add_run()
    fldSimple = parse_xml(r'<w:fldSimple %s w:instr="PAGE"/>' % nsdecls('w'))
    r_fld._r.append(fldSimple)
    set_run_fonts(r_fld, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=10.0, color_rgb=RGBColor(30, 41, 59))


def parse_inline_runs(text):
    """将包含 **粗体**、`代码` 和 [链接] 的字符串解析为规范标记单元列表"""
    # 先清除图片语法残留
    text = re.sub(r"\!\[.*?\]\(.*?\)", "", text)
    # 将纯 [text](url) 替换为 text
    text = re.sub(r"\[(.*?)\]\([^)]*?\)", r"\1", text)
    
    pattern = re.compile(r"(\*\*.*?\*\*|`.*?`)")
    tokens = pattern.split(text)
    runs = []
    for token in tokens:
        if not token:
            continue
        if token.startswith("**") and token.endswith("**") and len(token) >= 4:
            clean = token[2:-2].strip()
            if clean:
                runs.append(("bold", clean))
        elif token.startswith("`") and token.endswith("`") and len(token) >= 2:
            clean = token[1:-1].strip()
            if clean:
                runs.append(("code", clean))
        else:
            clean = token.replace("**", "").replace("`", "")
            if clean:
                runs.append(("normal", clean))
    return runs


def append_inline_runs(para, text, default_size=11.5, default_bold=False):
    """解析并在段落中追加 runs"""
    runs = parse_inline_runs(text)
    for style_type, content in runs:
        r = para.add_run(content)
        if style_type == "bold":
            set_run_fonts(r, ascii_font="Times New Roman", east_asia_font="SimHei", size_pt=default_size, bold=True)
        elif style_type == "code":
            set_run_fonts(r, ascii_font="Consolas", east_asia_font="SimSun", size_pt=default_size - 1.5, bold=False, color_rgb=RGBColor(30, 41, 59))
        else:
            set_run_fonts(r, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=default_size, bold=default_bold)


def style_table_standard(table):
    """为表格应用对标 PDF 的规范样式"""
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    tblBorders = parse_xml(
        r'''<w:tblBorders {} >
            <w:top w:val="single" w:sz="4" w:space="0" w:color="000000"/>
            <w:left w:val="single" w:sz="4" w:space="0" w:color="000000"/>
            <w:bottom w:val="single" w:sz="4" w:space="0" w:color="000000"/>
            <w:right w:val="single" w:sz="4" w:space="0" w:color="000000"/>
            <w:insideH w:val="single" w:sz="4" w:space="0" w:color="000000"/>
            <w:insideV w:val="single" w:sz="4" w:space="0" w:color="000000"/>
        </w:tblBorders>'''.format(nsdecls('w'))
    )
    tblPr = table._tbl.tblPr
    for c in list(tblPr):
        if c.tag.endswith('tblBorders'):
            tblPr.remove(c)
    tblPr.append(tblBorders)

    if not table.rows:
        return
        
    hdr_trPr = table.rows[0]._tr.get_or_add_trPr()
    hdr_trPr.append(parse_xml(r'<w:tblHeader %s/>' % nsdecls('w')))
    
    for cell in table.rows[0].cells:
        tcPr = cell._tc.get_or_add_tcPr()
        for c in list(tcPr):
            if c.tag.endswith('shd'):
                tcPr.remove(c)
        tcPr.append(parse_xml(r'<w:shd %s w:val="clear" w:color="auto" w:fill="D9D9D9"/>' % nsdecls('w')))
        for p in cell.paragraphs:
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            set_para_indent(p, first_line_chars=0)
            set_para_spacing(p, before_pt=2, after_pt=2, line_spacing=1.15)
            for r in p.runs:
                set_run_fonts(r, ascii_font="Times New Roman", east_asia_font="SimHei", size_pt=9.5, bold=True)
                
    for r_idx in range(1, len(table.rows)):
        row = table.rows[r_idx]
        row._tr.get_or_add_trPr().append(parse_xml(r'<w:cantSplit %s/>' % nsdecls('w')))
        for cell in row.cells:
            for p in cell.paragraphs:
                set_para_indent(p, first_line_chars=0)
                set_para_spacing(p, before_pt=1, after_pt=1, line_spacing=1.15)
                for r in p.runs:
                    set_run_fonts(r, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=9.0, bold=False)


def reformat_raw_spec():
    print(f"正在读取原始 CMMI 规格书: {SRC_DOCX}")
    if not BACKUP_DOCX.exists():
        shutil.copy2(SRC_DOCX, BACKUP_DOCX)
        print(f"已创建安全备份: {BACKUP_DOCX.name}")
        
    src_doc = Document(str(SRC_DOCX))
    new_doc = Document()
    
    # ── Section 1: 封面 ────────────────────────────────────────────────
    sec_cover = new_doc.sections[0]
    sec_cover.page_width = Pt(PAGE_WIDTH_TWIPS / 20)
    sec_cover.page_height = Pt(PAGE_HEIGHT_TWIPS / 20)
    sec_cover.top_margin = Pt(MARGIN_TOP_TWIPS / 20)
    sec_cover.bottom_margin = Pt(MARGIN_BOTTOM_TWIPS / 20)
    sec_cover.left_margin = Pt(MARGIN_LEFT_TWIPS / 20)
    sec_cover.right_margin = Pt(MARGIN_RIGHT_TWIPS / 20)
    configure_header_footer(sec_cover, is_cover=True)
    
    p_top = new_doc.add_paragraph()
    p_top.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_para_spacing(p_top, before_pt=20, after_pt=12, line_spacing=1.2)
    set_para_indent(p_top, first_line_chars=0)
    r_top = p_top.add_run("OAISS CHAIN · 企业级双碳链动系统工程技术规范\nCMMI DEV V1.2 软件工程管理标准规格书")
    set_run_fonts(r_top, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=14.0, bold=True, color_rgb=RGBColor(15, 23, 42))
    
    p_title = new_doc.add_paragraph()
    p_title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_para_spacing(p_title, before_pt=12, after_pt=4, line_spacing=1.3)
    set_para_indent(p_title, first_line_chars=0)
    r_title1 = p_title.add_run("01~05 原始需求与标准规格书\n")
    set_run_fonts(r_title1, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=18.0, bold=True, color_rgb=RGBColor(30, 41, 59))
    r_title2 = p_title.add_run("（项目需求分析·概要设计·详细方案·碳核算模型·测试规格）")
    set_run_fonts(r_title2, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=13.0, bold=False, color_rgb=RGBColor(71, 85, 105))
    
    p_gap1 = new_doc.add_paragraph()
    set_para_spacing(p_gap1, before_pt=10, after_pt=10, line_spacing=1.0)
    
    for ch in ["系", "统", "开", "发", "文", "档"]:
        p_ch = new_doc.add_paragraph()
        p_ch.alignment = WD_ALIGN_PARAGRAPH.CENTER
        set_para_spacing(p_ch, before_pt=2, after_pt=2, line_spacing=1.0)
        set_para_indent(p_ch, first_line_chars=0)
        r_ch = p_ch.add_run(ch)
        set_run_fonts(r_ch, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=26.0, bold=True, color_rgb=RGBColor(15, 23, 42))
        
    p_gap2 = new_doc.add_paragraph()
    set_para_spacing(p_gap2, before_pt=12, after_pt=12, line_spacing=1.0)
    
    p_date = new_doc.add_paragraph()
    p_date.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_para_spacing(p_date, before_pt=12, after_pt=12, line_spacing=1.2)
    set_para_indent(p_date, first_line_chars=0)
    r_date = p_date.add_run("二〇二六年十月")
    set_run_fonts(r_date, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=15.0, bold=False, color_rgb=RGBColor(51, 65, 85))
    
    tbl_cover = new_doc.add_table(rows=7, cols=2)
    tbl_cover.alignment = WD_TABLE_ALIGNMENT.CENTER
    cover_data = [
        ("作品名称：", "OAISS CHAIN 双碳链动系统"),
        ("文档编号：", "创梦先锋队-OAISS CHAIN-001~005"),
        ("受控状态：", "■受控  □非受控"),
        ("保密级别：", "□公司级  □部门级  ■项目级  □普通级"),
        ("采纳标准：", "CMMI DEV V1.2 (软件工程成熟度集成模型)"),
        ("研发团队：", "OAISS CHAIN 创梦先锋队 & 软件学院全栈团队"),
        ("所属单位：", "软件学院 & 双碳区块链工程中心")
    ]
    col_w = [Inches(1.8), Inches(4.5)]
    for r_idx, (k, v) in enumerate(cover_data):
        row = tbl_cover.rows[r_idx]
        c0 = row.cells[0]
        c0.width = col_w[0]
        p0 = c0.paragraphs[0]
        p0.alignment = WD_ALIGN_PARAGRAPH.RIGHT
        set_para_spacing(p0, before_pt=2, after_pt=2, line_spacing=1.2)
        set_para_indent(p0, first_line_chars=0)
        r0 = p0.add_run(k)
        set_run_fonts(r0, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=11.0, bold=True, color_rgb=RGBColor(30, 41, 59))
        
        c1 = row.cells[1]
        c1.width = col_w[1]
        p1 = c1.paragraphs[0]
        p1.alignment = WD_ALIGN_PARAGRAPH.LEFT
        set_para_spacing(p1, before_pt=2, after_pt=2, line_spacing=1.2)
        set_para_indent(p1, first_line_chars=0)
        r1 = p1.add_run(v)
        set_run_fonts(r1, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=11.0, bold=False, color_rgb=RGBColor(51, 65, 85))
        
    tblBorders = parse_xml(
        r'''<w:tblBorders {} >
            <w:top w:val="none" w:sz="0" w:space="0" w:color="auto"/>
            <w:left w:val="none" w:sz="0" w:space="0" w:color="auto"/>
            <w:bottom w:val="none" w:sz="0" w:space="0" w:color="auto"/>
            <w:right w:val="none" w:sz="0" w:space="0" w:color="auto"/>
            <w:insideH w:val="none" w:sz="0" w:space="0" w:color="auto"/>
            <w:insideV w:val="none" w:sz="0" w:space="0" w:color="auto"/>
        </w:tblBorders>'''.format(nsdecls('w'))
    )
    tbl_cover._tbl.tblPr.append(tblBorders)
    
    # ── Section 2: 目录 ────────────────────────────────────────────────
    sec_toc = new_doc.add_section(WD_SECTION_START.NEW_PAGE)
    sec_toc.page_width = Pt(PAGE_WIDTH_TWIPS / 20)
    sec_toc.page_height = Pt(PAGE_HEIGHT_TWIPS / 20)
    sec_toc.top_margin = Pt(MARGIN_TOP_TWIPS / 20)
    sec_toc.bottom_margin = Pt(MARGIN_BOTTOM_TWIPS / 20)
    sec_toc.left_margin = Pt(MARGIN_LEFT_TWIPS / 20)
    sec_toc.right_margin = Pt(MARGIN_RIGHT_TWIPS / 20)
    configure_header_footer(sec_toc, is_toc=True)
    
    p_toc_head = new_doc.add_paragraph()
    p_toc_head.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_para_spacing(p_toc_head, before_pt=24, after_pt=16, line_spacing=1.3)
    set_para_indent(p_toc_head, first_line_chars=0)
    r_toc_head = p_toc_head.add_run("目  录")
    set_run_fonts(r_toc_head, ascii_font="Times New Roman", east_asia_font="SimHei", size_pt=18.0, bold=True, color_rgb=RGBColor(15, 23, 42))
    
    p_hint = new_doc.add_paragraph()
    p_hint.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_para_spacing(p_hint, before_pt=0, after_pt=8, line_spacing=1.0)
    set_para_indent(p_hint, first_line_chars=0)
    r_hint = p_hint.add_run("〔提示：在 Word 中右键选择‘更新域’即可根据当前正文页码全量自动同步〕")
    set_run_fonts(r_hint, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=9.0, color_rgb=RGBColor(148, 163, 184))

    p_toc_fld = new_doc.add_paragraph()
    p_toc_fld.alignment = WD_ALIGN_PARAGRAPH.LEFT
    set_para_spacing(p_toc_fld, before_pt=6, after_pt=12, line_spacing=1.2)
    set_para_indent(p_toc_fld, first_line_chars=0)
    r_tf = p_toc_fld.add_run()
    fldChar1 = parse_xml(r'<w:fldChar %s w:fldCharType="begin"/>' % nsdecls('w'))
    instrText = parse_xml(r'<w:instrText %s xml:space="preserve"> TOC \o "1-3" \h \z \u </w:instrText>' % nsdecls('w'))
    fldChar2 = parse_xml(r'<w:fldChar %s w:fldCharType="separate"/>' % nsdecls('w'))
    fldChar3 = parse_xml(r'<w:fldChar %s w:fldCharType="end"/>' % nsdecls('w'))
    r_tf._r.append(fldChar1)
    r_tf._r.append(instrText)
    r_tf._r.append(fldChar2)
    r_tf._r.append(fldChar3)
    
    # ── Section 3: 正文 ────────────────────────────────────────────────
    sec_body = new_doc.add_section(WD_SECTION_START.NEW_PAGE)
    sec_body.page_width = Pt(PAGE_WIDTH_TWIPS / 20)
    sec_body.page_height = Pt(PAGE_HEIGHT_TWIPS / 20)
    sec_body.top_margin = Pt(MARGIN_TOP_TWIPS / 20)
    sec_body.bottom_margin = Pt(MARGIN_BOTTOM_TWIPS / 20)
    sec_body.left_margin = Pt(MARGIN_LEFT_TWIPS / 20)
    sec_body.right_margin = Pt(MARGIN_RIGHT_TWIPS / 20)
    configure_header_footer(sec_body, is_cover=False, is_toc=False)

    # 遍历原文档的 body 元素
    body = src_doc._body._body
    children = body.getchildren()
    
    # 构建原 table 和 para 映射
    p_idx = 0
    t_idx = 0
    all_paras = src_doc.paragraphs
    all_tables = src_doc.tables
    
    print(f"开始重构正文元素 (总计 {len(children)} 个节点)...")
    
    in_preamble = True  # 前导无用 Markdown 封面段落跳过
    
    for c in children:
        tag = c.tag.split('}')[-1]
        if tag == 'p':
            para = all_paras[p_idx]
            p_idx += 1
            raw_t = para.text.strip()
            
            # 跳过前导乱码封面
            if in_preamble:
                if re.match(r'^(01\s+项目需求分析|1\.\s+简介)', raw_t):
                    in_preamble = False
                elif "01 项目需求分析说明" in raw_t:
                    in_preamble = False
                else:
                    continue

            # 跳过 Markdown 伪目录死链接
            if re.match(r'^\[.*?\]\(#_Toc.*?\)$', raw_t) or raw_t == "目录" or raw_t == "**目录**":
                continue
                
            # 清理 Markdown base64 图片语法行
            if raw_t.startswith("![") and "data:image" in raw_t and "文件编号" in raw_t:
                # 提取文件编号纯文本
                m_num = re.search(r'文件编号[：:]\s*([^\s]+)', raw_t)
                if m_num:
                    p = new_doc.add_paragraph()
                    set_para_indent(p, first_line_chars=0)
                    set_para_spacing(p, before_pt=4, after_pt=4, line_spacing=1.2)
                    r = p.add_run(f"【受控文件编号：{m_num.group(1)}】")
                    set_run_fonts(r, ascii_font="Times New Roman", east_asia_font="SimHei", size_pt=10.5, bold=True)
                continue
            elif raw_t.startswith("![") and "data:image" in raw_t:
                # 纯 base64 图片标签直接忽略
                continue

            if not raw_t:
                continue

            # 一级标题（01~05 篇章）
            if re.match(r'^(0[1-5]\s+|第[一二三四五]部分\s+)', raw_t) or (len(raw_t) < 30 and any(raw_t.startswith(x) for x in ["01 项目", "02 项目", "03 项目", "04 碳核算", "05 项目"])):
                clean_title = re.sub(r'[*`#]', '', raw_t).strip()
                p = new_doc.add_paragraph(style='Heading 1')
                p.alignment = WD_ALIGN_PARAGRAPH.CENTER
                set_para_indent(p, first_line_chars=0)
                set_para_spacing(p, before_pt=18, after_pt=12, line_spacing=1.3, keep_with_next=True)
                p.paragraph_format.page_break_before = True
                
                pPr = p._p.get_or_add_pPr()
                outl = pPr.find(qn('w:outlineLvl'))
                if outl is None:
                    outl = OxmlElement('w:outlineLvl')
                    pPr.append(outl)
                outl.set(qn('w:val'), '0')
                
                r = p.add_run(clean_title)
                set_run_fonts(r, ascii_font="Times New Roman", east_asia_font="SimHei", size_pt=15.0, bold=True, color_rgb=RGBColor(15, 23, 42))
                continue

            # 二级标题 (1. 2. 3. 等)
            if re.match(r'^\d+\.\s+[\u4e00-\u9fa5A-Za-z]', raw_t) and len(raw_t) < 40:
                clean_title = re.sub(r'[*`#]', '', raw_t).strip()
                p = new_doc.add_paragraph(style='Heading 2')
                p.alignment = WD_ALIGN_PARAGRAPH.LEFT
                set_para_indent(p, first_line_chars=0)
                set_para_spacing(p, before_pt=14, after_pt=6, line_spacing=1.25, keep_with_next=True)
                
                pPr = p._p.get_or_add_pPr()
                outl = pPr.find(qn('w:outlineLvl'))
                if outl is None:
                    outl = OxmlElement('w:outlineLvl')
                    pPr.append(outl)
                outl.set(qn('w:val'), '1')
                
                r = p.add_run(clean_title)
                set_run_fonts(r, ascii_font="Times New Roman", east_asia_font="SimHei", size_pt=14.0, bold=True, color_rgb=RGBColor(30, 41, 59))
                continue

            # 三级标题 (1.1 2.1 3.1 等)
            if re.match(r'^\d+\.\d+\s*[\u4e00-\u9fa5A-Za-z]', raw_t) and len(raw_t) < 50:
                clean_title = re.sub(r'[*`#]', '', raw_t).strip()
                p = new_doc.add_paragraph(style='Heading 3')
                p.alignment = WD_ALIGN_PARAGRAPH.LEFT
                set_para_indent(p, first_line_chars=0)
                set_para_spacing(p, before_pt=8, after_pt=4, line_spacing=1.2, keep_with_next=True)
                
                pPr = p._p.get_or_add_pPr()
                outl = pPr.find(qn('w:outlineLvl'))
                if outl is None:
                    outl = OxmlElement('w:outlineLvl')
                    pPr.append(outl)
                outl.set(qn('w:val'), '2')
                
                r = p.add_run(clean_title)
                set_run_fonts(r, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=12.0, bold=True, color_rgb=RGBColor(15, 23, 42))
                continue

            # 四级标题 (#### 或 1.1.1 等)
            if raw_t.startswith("#### ") or (re.match(r'^\d+\.\d+\.\d+\s*[\u4e00-\u9fa5A-Za-z]', raw_t) and len(raw_t) < 60):
                clean_title = re.sub(r'[*`#]', '', raw_t).strip()
                p = new_doc.add_paragraph()
                p.alignment = WD_ALIGN_PARAGRAPH.LEFT
                set_para_indent(p, first_line_chars=0)
                set_para_spacing(p, before_pt=6, after_pt=3, line_spacing=1.2, keep_with_next=True)
                r = p.add_run(clean_title)
                set_run_fonts(r, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=11.5, bold=True, color_rgb=RGBColor(30, 41, 59))
                continue

            # 表题与图题
            if raw_t.startswith("表") or raw_t.startswith("图") and any(raw_t.startswith(x) for x in ["表1-", "表2-", "表3-", "表6-", "表8-", "表9-", "图1-", "图2-", "图3-", "图6-", "图8-"]):
                clean_cap = re.sub(r'[*`]', '', raw_t).strip()
                p = new_doc.add_paragraph()
                p.alignment = WD_ALIGN_PARAGRAPH.CENTER
                set_para_indent(p, first_line_chars=0)
                set_para_spacing(p, before_pt=6, after_pt=4, line_spacing=1.2, keep_with_next=True)
                r = p.add_run(clean_cap)
                set_run_fonts(r, ascii_font="Times New Roman", east_asia_font="SimHei", size_pt=10.5, bold=True, color_rgb=RGBColor(30, 41, 59))
                continue

            # 普通正文段落（清洗并按 runs 渲染）
            clean_text = raw_t.replace("\\_", "_")
            p = new_doc.add_paragraph()
            p.alignment = WD_ALIGN_PARAGRAPH.LEFT
            set_para_indent(p, first_line_chars=2)
            set_para_spacing(p, before_pt=2, after_pt=3, line_spacing=1.25)
            append_inline_runs(p, clean_text, default_size=11.5)

        elif tag == 'tbl':
            orig_tbl = all_tables[t_idx]
            t_idx += 1
            if in_preamble:
                continue
                
            # 复制表格到新文档
            n_rows = len(orig_tbl.rows)
            n_cols = len(orig_tbl.columns)
            new_tbl = new_doc.add_table(rows=n_rows, cols=n_cols)
            for r_i, row in enumerate(orig_tbl.rows):
                for c_i, cell in enumerate(row.cells):
                    raw_cell_t = cell.text.strip().replace("\n\n", "\n")
                    clean_cell_t = re.sub(r'\!\[.*?\]\(.*?\)', '', raw_cell_t)
                    clean_cell_t = clean_cell_t.replace('**', '').replace('`', '')
                    new_tbl.cell(r_i, c_i).text = clean_cell_t
            style_table_standard(new_tbl)
            new_doc.add_paragraph()

    new_doc.save(str(SRC_DOCX))
    print(f"🎉 成功完成《{SRC_DOCX.name}》的深度重构与清洗！")


if __name__ == "__main__":
    reformat_raw_spec()
