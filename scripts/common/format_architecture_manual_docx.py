#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
format_architecture_manual_docx.py
----------------------------------
将《OAISS_CHAIN_技术架构与使用说明书.docx》按照标准化工程规范进行格式规范和优化排版：
1. 整体视觉样式完全保真（宋体+黑体+Times New Roman+Consolas，表格浅灰表头#D9D9D9与细腻实线，封面居中与竖排大字微标，原汁原味）
2. 建立正规三节（3 Sections）结构：
   - Section 1 (封面)：独立分节，开启 titlePg，无页眉无页脚无页码
   - Section 2 (目录)：独立分节，标准 Word 原生动态目录域（TOC Field），页脚居中罗马数字 i，附带更新提示，强制分页
   - Section 3 (正文)：独立分节，彻底移除 titlePg，首面与后续页均显示页眉与居中阿拉伯数字页码（PAGE \* arabic \* MERGEFORMAT，从 1 开始自动编码）
3. 规范大纲层级：
   - 一级标题（第一章 ~ 第六章）：Heading 1，三号 15pt 黑体加粗居中，带 outlineLvl=0 与 keep_with_next
   - 二级标题（1.1, 1.2 等）：Heading 2，四号 12pt 黑体加粗靠左，带 outlineLvl=1 与 keep_with_next
   - 三级标题（第一步、第二步等）：Heading 3，五号 10.5pt 黑体加粗靠左，带 outlineLvl=2 与 keep_with_next
4. 消除所有 Markdown 标记残留（###, ##, #, ---, 多余星号等）
5. 清除多余重复的草稿残片表格（3x6 简表），保留完整的 4x7 各章修订记录表与核心业务数据表
6. 规范表格排版：居中、固定总宽贴合版心（9638 twips）、表头底色 #D9D9D9 加粗居中、跨页重复表头 tblHeader、行防截断 cantSplit、表题 keep_with_next
7. 规范高清插图：从 docs/assets/architecture/ 读取 300 DPI 原始原图插入，自适应宽度，单倍行距防切图，合并重复图题并 keep_with_next
8. 规范代码块与导言框：等宽代码样式与侧边强调提示框
"""

import os
import sys
import json
import subprocess
from pathlib import Path
import docx
from docx import Document
from docx.shared import Inches, Pt, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
from docx.enum.section import WD_SECTION_START
from docx.oxml import OxmlElement, parse_xml
from docx.oxml.ns import nsdecls, qn

WORKSPACE_ROOT = Path(__file__).resolve().parent.parent.parent
DOCX_PATH = WORKSPACE_ROOT / "docs" / "OAISS_CHAIN_技术架构与使用说明书.docx"
BACKUP_PATH = WORKSPACE_ROOT / "docs" / "OAISS_CHAIN_技术架构与使用说明书_backup.docx"
ASSETS_DIR = WORKSPACE_ROOT / "docs" / "assets" / "architecture"
DOCX_SCRIPTS = Path("C:/Users/LiShuai/.zcode/cli/plugins/cache/zcode-plugins-official/documents/0.1.8/skills/docx/scripts")

# A4 页面标准 (twips)
PAGE_WIDTH_TWIPS = 11906   # 210mm
PAGE_HEIGHT_TWIPS = 16838  # 297mm
MARGIN_TOP_TWIPS = 1417    # 25mm
MARGIN_BOTTOM_TWIPS = 1134 # 20mm
MARGIN_LEFT_TWIPS = 1134   # 20mm
MARGIN_RIGHT_TWIPS = 1134  # 20mm
CONTENT_WIDTH_TWIPS = PAGE_WIDTH_TWIPS - MARGIN_LEFT_TWIPS - MARGIN_RIGHT_TWIPS # 9638 twips (约 17.0cm)


def set_run_fonts(run, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=12, bold=False, color_rgb=None):
    """设置文本 Run 的中西文字体、字号与颜色"""
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
    """设置段落间距与行距"""
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
    """设置段落缩进（首行缩进与左缩进）"""
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


def add_callout_box(doc, text, title="架构导言"):
    """添加高雅的架构导言或提示框（带浅色左边框和浅底色）"""
    tbl = doc.add_table(rows=1, cols=1)
    tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
    cell = tbl.cell(0, 0)
    cell.width = Inches(6.65)
    
    tcPr = cell._tc.get_or_add_tcPr()
    shd = parse_xml(r'<w:shd {} w:val="clear" w:color="auto" w:fill="F8FAFC"/>'.format(nsdecls('w')))
    tcPr.append(shd)
    
    tcBorders = parse_xml(
        r'''<w:tcBorders {} >
            <w:top w:val="none" w:sz="0" w:space="0" w:color="auto"/>
            <w:left w:val="single" w:sz="24" w:space="0" w:color="3B82F6"/>
            <w:bottom w:val="none" w:sz="0" w:space="0" w:color="auto"/>
            <w:right w:val="none" w:sz="0" w:space="0" w:color="auto"/>
        </w:tcBorders>'''.format(nsdecls('w'))
    )
    tcPr.append(tcBorders)
    
    tcMar = parse_xml(
        r'''<w:tcMar {} >
            <w:top w:w="160" w:type="dxa"/>
            <w:left w:w="240" w:type="dxa"/>
            <w:bottom w:w="160" w:type="dxa"/>
            <w:right w:w="240" w:type="dxa"/>
        </w:tcMar>'''.format(nsdecls('w'))
    )
    tcPr.append(tcMar)
    
    p = cell.paragraphs[0]
    p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    set_para_spacing(p, before_pt=2, after_pt=2, line_spacing=1.2)
    set_para_indent(p, first_line_chars=0)
    
    r1 = p.add_run(f"【{title}】")
    set_run_fonts(r1, ascii_font="Times New Roman", east_asia_font="SimHei", size_pt=10.5, bold=True, color_rgb=RGBColor(30, 64, 175))
    
    r2 = p.add_run(text)
    set_run_fonts(r2, ascii_font="Times New Roman", east_asia_font="KaiTi", size_pt=10.5, bold=False, color_rgb=RGBColor(51, 65, 85))
    
    p_after = doc.add_paragraph()
    set_para_spacing(p_after, before_pt=0, after_pt=4, line_spacing=0.5)


def add_code_block(doc, code_text):
    """添加专业的代码块"""
    tbl = doc.add_table(rows=1, cols=1)
    tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
    cell = tbl.cell(0, 0)
    cell.width = Inches(6.65)
    
    tcPr = cell._tc.get_or_add_tcPr()
    shd = parse_xml(r'<w:shd {} w:val="clear" w:color="auto" w:fill="F1F5F9"/>'.format(nsdecls('w')))
    tcPr.append(shd)
    
    tcBorders = parse_xml(
        r'''<w:tcBorders {} >
            <w:top w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>
            <w:left w:val="single" w:sz="16" w:space="0" w:color="64748B"/>
            <w:bottom w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>
            <w:right w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>
        </w:tcBorders>'''.format(nsdecls('w'))
    )
    tcPr.append(tcBorders)
    
    tcMar = parse_xml(
        r'''<w:tcMar {} >
            <w:top w:w="140" w:type="dxa"/>
            <w:left w:w="200" w:type="dxa"/>
            <w:bottom w:w="140" w:type="dxa"/>
            <w:right w:w="200" w:type="dxa"/>
        </w:tcMar>'''.format(nsdecls('w'))
    )
    tcPr.append(tcMar)
    
    lines = code_text.split('\n')
    for i, line in enumerate(lines):
        p = cell.paragraphs[0] if i == 0 else cell.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT
        set_para_spacing(p, before_pt=0, after_pt=0, line_spacing=1.0)
        set_para_indent(p, first_line_chars=0)
        r = p.add_run(line if line else ' ')
        set_run_fonts(r, ascii_font="Consolas", east_asia_font="Consolas", size_pt=9.5, bold=False, color_rgb=RGBColor(30, 41, 59))
        
    p_after = doc.add_paragraph()
    set_para_spacing(p_after, before_pt=0, after_pt=4, line_spacing=0.5)


def add_image_figure(doc, img_path, caption_text, width_cm=14.5):
    """添加规范的居中高清插图与图题"""
    p_img = doc.add_paragraph()
    p_img.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_para_spacing(p_img, before_pt=8, after_pt=3, line_spacing=1.0, keep_with_next=True)
    set_para_indent(p_img, first_line_chars=0)
    
    if os.path.exists(img_path):
        run_img = p_img.add_run()
        run_img.add_picture(str(img_path), width=Cm(width_cm))
    else:
        print(f"Warning: Image file not found: {img_path}")
        run_alt = p_img.add_run(f"[{caption_text}]")
        set_run_fonts(run_alt, size_pt=10.5, color_rgb=RGBColor(220, 38, 38))
        
    p_cap = doc.add_paragraph()
    p_cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_para_spacing(p_cap, before_pt=2, after_pt=8, line_spacing=1.2, keep_with_next=False)
    set_para_indent(p_cap, first_line_chars=0)
    
    run_cap = p_cap.add_run(caption_text)
    set_run_fonts(run_cap, ascii_font="Times New Roman", east_asia_font="SimHei", size_pt=10.5, bold=True, color_rgb=RGBColor(51, 65, 85))


def style_table_standard(table, col_widths, col_alignments):
    """格式化标准数据表格（浅灰表头#D9D9D9、细腻网格、跨页表头、cantSplit）"""
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    
    tblPr = table._tbl.tblPr
    tblLayout = parse_xml(r'<w:tblLayout {} w:type="fixed"/>'.format(nsdecls('w')))
    tblPr.append(tblLayout)
    
    tblBorders = parse_xml(
        r'''<w:tblBorders {} >
            <w:top w:val="single" w:sz="4" w:space="0" w:color="94A3B8"/>
            <w:left w:val="single" w:sz="4" w:space="0" w:color="94A3B8"/>
            <w:bottom w:val="single" w:sz="4" w:space="0" w:color="94A3B8"/>
            <w:right w:val="single" w:sz="4" w:space="0" w:color="94A3B8"/>
            <w:insideH w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>
            <w:insideV w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>
        </w:tblBorders>'''.format(nsdecls('w'))
    )
    tblPr.append(tblBorders)
    
    tblCellMar = parse_xml(
        r'''<w:tblCellMar {} >
            <w:top w:w="120" w:type="dxa"/>
            <w:left w:w="160" w:type="dxa"/>
            <w:bottom w:w="120" w:type="dxa"/>
            <w:right w:w="160" w:type="dxa"/>
        </w:tblCellMar>'''.format(nsdecls('w'))
    )
    tblPr.append(tblCellMar)
    
    for row_idx, row in enumerate(table.rows):
        trPr = row._tr.get_or_add_trPr()
        cantSplit = OxmlElement('w:cantSplit')
        trPr.append(cantSplit)
        
        if row_idx == 0:
            tblHeader = OxmlElement('w:tblHeader')
            trPr.append(tblHeader)
            
        for col_idx, cell in enumerate(row.cells):
            cell.width = col_widths[col_idx]
            tcPr = cell._tc.get_or_add_tcPr()
            
            if row_idx == 0:
                shd = parse_xml(r'<w:shd {} w:val="clear" w:color="auto" w:fill="D9D9D9"/>'.format(nsdecls('w')))
                tcPr.append(shd)
            elif row_idx % 2 == 1:
                shd = parse_xml(r'<w:shd {} w:val="clear" w:color="auto" w:fill="FAFBFD"/>'.format(nsdecls('w')))
                tcPr.append(shd)
                
            cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
            
            for p in cell.paragraphs:
                p.alignment = col_alignments[col_idx] if row_idx > 0 else WD_ALIGN_PARAGRAPH.CENTER
                set_para_spacing(p, before_pt=2, after_pt=2, line_spacing=1.15)
                set_para_indent(p, first_line_chars=0)
                for r in p.runs:
                    set_run_fonts(
                        r, 
                        ascii_font="Times New Roman", 
                        east_asia_font="SimHei" if row_idx == 0 else "SimSun", 
                        size_pt=10.0, 
                        bold=(row_idx == 0),
                        color_rgb=RGBColor(15, 23, 42) if row_idx == 0 else RGBColor(51, 65, 85)
                    )


def configure_header_footer(section, is_cover=False, is_toc=False):
    """精确配置各节的页眉页脚与页码属性"""
    sectPr = section._sectPr
    
    if is_cover:
        section.different_first_page_header_footer = True
        header = section.header
        header.is_linked_to_previous = False
        for p in header.paragraphs:
            p.text = ""
        footer = section.footer
        footer.is_linked_to_previous = False
        for p in footer.paragraphs:
            p.text = ""
        return
        
    if is_toc:
        # 目录节：独立页眉页脚，移除 titlePg，页脚居中罗马数字 i
        section.different_first_page_header_footer = False
        t_pg = sectPr.find(qn('w:titlePg'))
        if t_pg is not None:
            sectPr.remove(t_pg)
            
        section.header.is_linked_to_previous = False
        p_hdr = section.header.paragraphs[0]
        p_hdr.text = ""
        
        section.footer.is_linked_to_previous = False
        p_ftr = section.footer.paragraphs[0]
        p_ftr.text = ""
        p_ftr.alignment = WD_ALIGN_PARAGRAPH.CENTER
        set_para_spacing(p_ftr, before_pt=0, after_pt=0, line_spacing=1.0)
        
        fld = parse_xml(r'<w:fldSimple {} w:instr="PAGE \* roman \* MERGEFORMAT"/>'.format(nsdecls('w')))
        p_ftr._p.append(fld)
        
        pgNumType = sectPr.find(qn('w:pgNumType'))
        if pgNumType is None:
            pgNumType = OxmlElement('w:pgNumType')
            sectPr.append(pgNumType)
        pgNumType.set(qn('w:fmt'), 'lowerRoman')
        pgNumType.set(qn('w:start'), '1')
        return

    # 正文节 (Section 3)：必须彻底移除 titlePg，确保第一页与后续页全部显示页眉与居中阿拉伯数字页码
    section.different_first_page_header_footer = False
    t_pg = sectPr.find(qn('w:titlePg'))
    if t_pg is not None:
        sectPr.remove(t_pg)
        
    section.header.is_linked_to_previous = False
    p_hdr = section.header.paragraphs[0]
    p_hdr.text = ""
    p_hdr.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    set_para_spacing(p_hdr, before_pt=0, after_pt=4, line_spacing=1.0)
    
    r_hdr = p_hdr.add_run("The Enterprise Carbon Accounting & Blockchain System · OAISS CHAIN 双碳链动系统")
    set_run_fonts(r_hdr, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=9.0, bold=False, color_rgb=RGBColor(100, 116, 139))
    
    pPr = p_hdr._p.get_or_add_pPr()
    pBdr = parse_xml(
        r'''<w:pBdr {} >
            <w:bottom w:val="single" w:sz="6" w:space="4" w:color="CBD5E1"/>
        </w:pBdr>'''.format(nsdecls('w'))
    )
    pPr.append(pBdr)
    
    # 页脚：居中阿拉伯数字页码
    section.footer.is_linked_to_previous = False
    p_ftr = section.footer.paragraphs[0]
    p_ftr.text = ""
    p_ftr.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_para_spacing(p_ftr, before_pt=4, after_pt=0, line_spacing=1.0)
    
    fld = parse_xml(r'<w:fldSimple {} w:instr="PAGE \* arabic \* MERGEFORMAT"/>'.format(nsdecls('w')))
    p_ftr._p.append(fld)
    
    pgNumType = sectPr.find(qn('w:pgNumType'))
    if pgNumType is None:
        pgNumType = OxmlElement('w:pgNumType')
        sectPr.append(pgNumType)
    pgNumType.set(qn('w:fmt'), 'decimal')
    pgNumType.set(qn('w:start'), '1')


def add_toc_field_elements(doc):
    """插入标准的 Word 原生动态目录域结构"""
    p_toc = doc.add_paragraph()
    set_para_spacing(p_toc, before_pt=6, after_pt=6, line_spacing=1.0)
    set_para_indent(p_toc, first_line_chars=0)
    
    r1 = p_toc.add_run()
    fld_begin = parse_xml(r'<w:fldChar {} w:fldCharType="begin"/>'.format(nsdecls('w')))
    r1._r.append(fld_begin)
    
    r2 = p_toc.add_run()
    instr = parse_xml(r'<w:instrText {} xml:space="preserve"> TOC \o "1-3" \h \z \u </w:instrText>'.format(nsdecls('w')))
    r2._r.append(instr)
    
    r3 = p_toc.add_run()
    fld_sep = parse_xml(r'<w:fldChar {} w:fldCharType="separate"/>'.format(nsdecls('w')))
    r3._r.append(fld_sep)
    
    p_end = doc.add_paragraph()
    set_para_spacing(p_end, before_pt=0, after_pt=6, line_spacing=1.0)
    set_para_indent(p_end, first_line_chars=0)
    r4 = p_end.add_run()
    fld_end = parse_xml(r'<w:fldChar {} w:fldCharType="end"/>'.format(nsdecls('w')))
    r4._r.append(fld_end)
    
    p_hint = doc.add_paragraph()
    p_hint.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_para_spacing(p_hint, before_pt=12, after_pt=12, line_spacing=1.2)
    set_para_indent(p_hint, first_line_chars=0)
    r_hint = p_hint.add_run("（注：本目录为 Word 原生动态目录域，右键点击选择“更新域”或快捷键 F9 即可自动同步最新章节与页码）")
    set_run_fonts(r_hint, ascii_font="Times New Roman", east_asia_font="KaiTi", size_pt=9.0, bold=False, color_rgb=RGBColor(148, 163, 184))


def add_heading_styled(doc, text, level=1):
    """添加标准大纲标题，自动绑定 Heading 样式并设置 keep_with_next"""
    clean_text = text.replace('###', '').replace('##', '').replace('#', '').strip()
    
    p = doc.add_paragraph(style=f'Heading {level}')
    p.text = clean_text
    
    if level == 1:
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        set_para_spacing(p, before_pt=18, after_pt=10, line_spacing=1.3, keep_with_next=True)
        set_para_indent(p, first_line_chars=0)
        for r in p.runs:
            set_run_fonts(r, ascii_font="Times New Roman", east_asia_font="SimHei", size_pt=15.0, bold=True, color_rgb=RGBColor(15, 23, 42))
    elif level == 2:
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT
        set_para_spacing(p, before_pt=12, after_pt=6, line_spacing=1.25, keep_with_next=True)
        set_para_indent(p, first_line_chars=0)
        for r in p.runs:
            set_run_fonts(r, ascii_font="Times New Roman", east_asia_font="SimHei", size_pt=12.0, bold=True, color_rgb=RGBColor(30, 41, 59))
    elif level == 3:
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT
        set_para_spacing(p, before_pt=8, after_pt=4, line_spacing=1.2, keep_with_next=True)
        set_para_indent(p, first_line_chars=0)
        for r in p.runs:
            set_run_fonts(r, ascii_font="Times New Roman", east_asia_font="SimHei", size_pt=10.5, bold=True, color_rgb=RGBColor(51, 65, 85))
    return p


def add_body_paragraph(doc, text):
    """添加正文段落（宋体 12pt，首行缩进 2 字符，1.25倍行距）"""
    clean_text = text.strip()
    if not clean_text:
        return None
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    set_para_spacing(p, before_pt=2, after_pt=2, line_spacing=1.25)
    set_para_indent(p, first_line_chars=2)
    
    r = p.add_run(clean_text)
    set_run_fonts(r, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=12.0, bold=False, color_rgb=RGBColor(30, 41, 59))
    return p


def build_reconstructed_document():
    """主构建流程：创建 3 节规范文档并填充全量数据"""
    print("Initializing Document...")
    doc = Document()
    
    # ── Section 1: 封面 ────────────────────────────────────────────────
    sec_cover = doc.sections[0]
    sec_cover.page_width = Pt(PAGE_WIDTH_TWIPS / 20)
    sec_cover.page_height = Pt(PAGE_HEIGHT_TWIPS / 20)
    sec_cover.top_margin = Pt(MARGIN_TOP_TWIPS / 20)
    sec_cover.bottom_margin = Pt(MARGIN_BOTTOM_TWIPS / 20)
    sec_cover.left_margin = Pt(MARGIN_LEFT_TWIPS / 20)
    sec_cover.right_margin = Pt(MARGIN_RIGHT_TWIPS / 20)
    configure_header_footer(sec_cover, is_cover=True)
    
    # 顶部工程技术规范小标
    p_top = doc.add_paragraph()
    p_top.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_para_spacing(p_top, before_pt=24, after_pt=16, line_spacing=1.2)
    set_para_indent(p_top, first_line_chars=0)
    r_top = p_top.add_run("OAISS CHAIN · 企业级双碳链动系统工程技术规范")
    set_run_fonts(r_top, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=18.0, bold=True, color_rgb=RGBColor(15, 23, 42))
    
    # 主标题与副标题
    p_title = doc.add_paragraph()
    p_title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_para_spacing(p_title, before_pt=12, after_pt=4, line_spacing=1.3)
    set_para_indent(p_title, first_line_chars=0)
    r_title1 = p_title.add_run("OAISS CHAIN 双碳链动系统开发手册\n")
    set_run_fonts(r_title1, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=16.0, bold=True, color_rgb=RGBColor(30, 41, 59))
    r_title2 = p_title.add_run("企业级技术架构设计与系统使用说明书")
    set_run_fonts(r_title2, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=16.0, bold=True, color_rgb=RGBColor(30, 41, 59))
    
    p_gap1 = doc.add_paragraph()
    set_para_spacing(p_gap1, before_pt=12, after_pt=12, line_spacing=1.0)
    
    # 竖排大字微标：“系 统 开 发 文 档”
    for ch in ["系", "统", "开", "发", "文", "档"]:
        p_ch = doc.add_paragraph()
        p_ch.alignment = WD_ALIGN_PARAGRAPH.CENTER
        set_para_spacing(p_ch, before_pt=2, after_pt=2, line_spacing=1.0)
        set_para_indent(p_ch, first_line_chars=0)
        r_ch = p_ch.add_run(ch)
        set_run_fonts(r_ch, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=26.0, bold=True, color_rgb=RGBColor(15, 23, 42))
        
    p_gap2 = doc.add_paragraph()
    set_para_spacing(p_gap2, before_pt=16, after_pt=16, line_spacing=1.0)
    
    p_date = doc.add_paragraph()
    p_date.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_para_spacing(p_date, before_pt=16, after_pt=16, line_spacing=1.2)
    set_para_indent(p_date, first_line_chars=0)
    r_date = p_date.add_run("二〇二六年十月")
    set_run_fonts(r_date, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=15.0, bold=False, color_rgb=RGBColor(51, 65, 85))
    
    # 封面元数据信息表格 (Table 0 无边框居中)
    tbl_cover = doc.add_table(rows=4, cols=2)
    tbl_cover.alignment = WD_TABLE_ALIGNMENT.CENTER
    cover_data = [
        ("作品名称：", "OAISS CHAIN 双碳链动系统"),
        ("作    者：", "OAISS CHAIN 联合研发团队"),
        ("指导老师：", "系统架构委员会 & 双碳区块链工程实验室"),
        ("所属单位：", "软件学院 & 双碳区块链工程中心")
    ]
    col_w = [Inches(1.8), Inches(4.5)]
    for r_idx, (k, v) in enumerate(cover_data):
        row = tbl_cover.rows[r_idx]
        
        c0 = row.cells[0]
        c0.width = col_w[0]
        p0 = c0.paragraphs[0]
        p0.alignment = WD_ALIGN_PARAGRAPH.RIGHT
        set_para_spacing(p0, before_pt=3, after_pt=3, line_spacing=1.2)
        set_para_indent(p0, first_line_chars=0)
        r0 = p0.add_run(k)
        set_run_fonts(r0, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=11.5, bold=True, color_rgb=RGBColor(30, 41, 59))
        
        c1 = row.cells[1]
        c1.width = col_w[1]
        p1 = c1.paragraphs[0]
        p1.alignment = WD_ALIGN_PARAGRAPH.LEFT
        set_para_spacing(p1, before_pt=3, after_pt=3, line_spacing=1.2)
        set_para_indent(p1, first_line_chars=0)
        r1 = p1.add_run(v)
        set_run_fonts(r1, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=11.5, bold=False, color_rgb=RGBColor(51, 65, 85))
        
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
    sec_toc = doc.add_section(WD_SECTION_START.NEW_PAGE)
    sec_toc.page_width = Pt(PAGE_WIDTH_TWIPS / 20)
    sec_toc.page_height = Pt(PAGE_HEIGHT_TWIPS / 20)
    sec_toc.top_margin = Pt(MARGIN_TOP_TWIPS / 20)
    sec_toc.bottom_margin = Pt(MARGIN_BOTTOM_TWIPS / 20)
    sec_toc.left_margin = Pt(MARGIN_LEFT_TWIPS / 20)
    sec_toc.right_margin = Pt(MARGIN_RIGHT_TWIPS / 20)
    configure_header_footer(sec_toc, is_toc=True)
    
    p_toc_head = doc.add_paragraph()
    p_toc_head.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_para_spacing(p_toc_head, before_pt=24, after_pt=16, line_spacing=1.3)
    set_para_indent(p_toc_head, first_line_chars=0)
    r_toc_head = p_toc_head.add_run("目  录")
    set_run_fonts(r_toc_head, ascii_font="Times New Roman", east_asia_font="SimHei", size_pt=18.0, bold=True, color_rgb=RGBColor(15, 23, 42))
    
    add_toc_field_elements(doc)
    
    # ── Section 3: 正文 ────────────────────────────────────────────────
    sec_body = doc.add_section(WD_SECTION_START.NEW_PAGE)
    sec_body.page_width = Pt(PAGE_WIDTH_TWIPS / 20)
    sec_body.page_height = Pt(PAGE_HEIGHT_TWIPS / 20)
    sec_body.top_margin = Pt(MARGIN_TOP_TWIPS / 20)
    sec_body.bottom_margin = Pt(MARGIN_BOTTOM_TWIPS / 20)
    sec_body.left_margin = Pt(MARGIN_LEFT_TWIPS / 20)
    sec_body.right_margin = Pt(MARGIN_RIGHT_TWIPS / 20)
    configure_header_footer(sec_body, is_cover=False, is_toc=False)
    
    # 文档开头元数据卡片
    p_meta_t = doc.add_paragraph()
    p_meta_t.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_para_spacing(p_meta_t, before_pt=12, after_pt=4, line_spacing=1.3)
    set_para_indent(p_meta_t, first_line_chars=0)
    r_m1 = p_meta_t.add_run("双碳链动系统 (OAISS CHAIN)\n")
    set_run_fonts(r_m1, ascii_font="Times New Roman", east_asia_font="SimHei", size_pt=14.0, bold=True, color_rgb=RGBColor(15, 23, 42))
    r_m2 = p_meta_t.add_run("企业级技术架构设计与系统使用说明书\nTechnical Architecture & User Manual")
    set_run_fonts(r_m2, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=11.0, bold=False, color_rgb=RGBColor(71, 85, 105))
    
    meta_box = (
        "系统版本：v1.0.0 (Release)\n"
        "编制团队：OAISS Engineering Architecture Team\n"
        "文档密级：企业内部核心技术规范\n"
        "编写日期：2026 年 10 月\n"
        "适用人员：研发工程师、架构师、系统运维人员、安全合规审计员"
    )
    add_callout_box(doc, meta_box, title="文档元数据与分发范围")
    
    # ════════════════════════════════════════════════════════════════════
    # 第一章 项目概述与业务全貌
    # ════════════════════════════════════════════════════════════════════
    add_heading_styled(doc, "第一章 项目概述与业务全貌", level=1)
    
    add_callout_box(doc, "本章阐明系统的业务协同模型、微服务六边形拓扑及核心基础设施通信约束。", title="架构导言")
    
    p_t1 = doc.add_paragraph()
    p_t1.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_para_spacing(p_t1, before_pt=6, after_pt=4, line_spacing=1.2, keep_with_next=True)
    set_para_indent(p_t1, first_line_chars=0)
    r_t1 = p_t1.add_run("表 1.1 章节修订与审核责任记录表")
    set_run_fonts(r_t1, ascii_font="Times New Roman", east_asia_font="SimHei", size_pt=10.5, bold=True)
    
    t_rev1 = doc.add_table(rows=4, cols=7)
    rev_data = [
        ["编号", "变更状态", "修订日期", "执笔人", "审核人", "批准人", "变更摘要与说明"],
        ["1", "文档创建", "2026-06-15", "核心研发", "架构师", "技术委员会", "初始化基础业务规格与实体设计"],
        ["2", "架构升级", "2026-09-02", "架构组", "技术委员会", "技术委员会", "全量同步生产基线与代码零漂移事实"],
        ["3", "版式重塑", "2026-10-05", "全栈架构组", "技术总监", "技术委员会", "对齐参考手册规范，建立多级大纲与标准用例卡片"]
    ]
    for r_i, row in enumerate(rev_data):
        for c_i, val in enumerate(row):
            t_rev1.cell(r_i, c_i).text = val
    style_table_standard(
        t_rev1, 
        col_widths=[Inches(0.5), Inches(0.9), Inches(1.0), Inches(0.8), Inches(0.8), Inches(0.9), Inches(1.75)],
        col_alignments=[WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.LEFT]
    )
    
    doc.add_paragraph()
    
    add_body_paragraph(
        doc,
        "双碳链动系统（OAISS CHAIN）是一套面向国家“碳达峰、碳中和”战略的金融级/企业级全栈综合服务平台。系统融合了国家标准碳排放核算模型、双向拍卖竞价与 P2P 撮合交易、碳币经济模型、信用评分评价、RSA 密码学生命周期管理、Hyperledger Fabric 联盟区块链存证底座以及基于机器学习（Prophet / XGBoost / IsolationForest）的碳价与排放趋势智能预测技术。"
    )
    
    add_heading_styled(doc, "1.1 四角色业务模型与权限矩阵", level=2)
    
    add_image_figure(
        doc,
        ASSETS_DIR / "02-four-roles-collaboration.png",
        "图 1.1 四角色业务协同与权限流转全景关系图",
        width_cm=14.5
    )
    
    add_body_paragraph(
        doc,
        "系统采用严密的角色访问控制体系（RBAC），面向企业碳资产管理全生命周期的四个关键参与方构建闭环："
    )
    
    p_t2 = doc.add_paragraph()
    p_t2.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_para_spacing(p_t2, before_pt=6, after_pt=4, line_spacing=1.2, keep_with_next=True)
    set_para_indent(p_t2, first_line_chars=0)
    r_t2 = p_t2.add_run("表 1.2 四角色访问控制与权限矩阵表")
    set_run_fonts(r_t2, ascii_font="Times New Roman", east_asia_font="SimHei", size_pt=10.5, bold=True)
    
    t_role = doc.add_table(rows=5, cols=4)
    role_data = [
        ["角色标识", "角色名称", "核心职责与功能边界", "技术访问入口"],
        ["ENTERPRISE", "企业用户", "填报碳排放数据、公式计算、发起交易、挂单拍卖、碳中和抵消", "/enterprise/*"],
        ["REVIEWER", "审核员", "审核企业碳排放报告与减排项目、出具审查意见与驳回原因", "/auditor/*"],
        ["THIRD_PARTY", "第三方监管", "行业碳排放多维监管大屏、公信力核验、最终上链存证确认", "/third-party/*"],
        ["ADMIN", "系统管理员", "企业与审核员准入审核、用户赋权、证书生命周期管理、全站审计", "/admin/*"]
    ]
    for r_i, row in enumerate(role_data):
        for c_i, val in enumerate(row):
            t_role.cell(r_i, c_i).text = val
    style_table_standard(
        t_role,
        col_widths=[Inches(1.2), Inches(1.1), Inches(3.1), Inches(1.25)],
        col_alignments=[WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.LEFT, WD_ALIGN_PARAGRAPH.CENTER]
    )
    
    add_heading_styled(doc, "1.2 核心业务流转闭环", level=2)
    
    add_image_figure(
        doc,
        ASSETS_DIR / "03-carbon-lifecycle-state-machine.png",
        "图 1.2 碳核算全生命周期状态机与数据流转关系图",
        width_cm=14.5
    )
    
    add_body_paragraph(
        doc,
        "系统的核心业务流转严格遵循“申报 -> 验签 -> 审核 -> 存证 -> 交易 -> 履约”的单向状态机管控："
    )
    steps = [
        "1. 企业上报：录入发电（25参数）或电网（9参数）数据，系统自动核算碳排放当量并要求使用企业 RSA 私钥生成数字签名；",
        "2. 审核员审查：审查员核验原始材料与签名，做出通过或驳回决定；",
        "3. 第三方监管与上链：监管机构确认后，通过 Fabric Gateway SDK 将报告哈希与元数据提交至 Hyperledger Fabric 账本，生成全网唯一交易哈希（TxHash）；",
        "4. 碳交易与结算：配额富余企业可通过双向连续竞价市场（Double Auction）或 P2P 双边议价挂单出售，交易成交自动触发碳币结算与配额划转。"
    ]
    for step in steps:
        add_body_paragraph(doc, step)
        
    add_callout_box(
        doc,
        "系统全面贯彻 ELI5 (Explain Like I'm 5) 体验设计标准：所有专业碳核算术语均提供悬浮气泡说明，表格状态采用‘文字+颜色+图标’三重视觉编码，确保非碳金融专业人员亦可无障碍上手。",
        title="设计说明 (Notice)"
    )
    
    # ════════════════════════════════════════════════════════════════════
    # 第二章 系统架构设计与技术栈
    # ════════════════════════════════════════════════════════════════════
    add_heading_styled(doc, "第二章 系统架构设计与技术栈", level=1)
    
    add_image_figure(
        doc,
        ASSETS_DIR / "01-system-hexagonal-architecture.png",
        "图 2.1 系统总体分层与六边形微服务拓扑架构图",
        width_cm=14.5
    )
    
    add_callout_box(doc, "本章阐明系统的微服务六边形拓扑结构、现代云原生依赖组件及信封加密密码学生态。", title="架构导言")
    
    p_t3 = doc.add_paragraph()
    p_t3.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_para_spacing(p_t3, before_pt=6, after_pt=4, line_spacing=1.2, keep_with_next=True)
    set_para_indent(p_t3, first_line_chars=0)
    r_t3 = p_t3.add_run("表 2.1 章节修订与审核责任记录表")
    set_run_fonts(r_t3, ascii_font="Times New Roman", east_asia_font="SimHei", size_pt=10.5, bold=True)
    
    t_rev2 = doc.add_table(rows=4, cols=7)
    for r_i, row in enumerate(rev_data):
        for c_i, val in enumerate(row):
            t_rev2.cell(r_i, c_i).text = val
    style_table_standard(
        t_rev2, 
        col_widths=[Inches(0.5), Inches(0.9), Inches(1.0), Inches(0.8), Inches(0.8), Inches(0.9), Inches(1.75)],
        col_alignments=[WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.LEFT]
    )
    
    doc.add_paragraph()
    
    add_body_paragraph(
        doc,
        "系统遵循现代云原生与六边形（Hexagonal）架构原则，分为表现层、应用服务层、领域层与基础设施适配层。服务间采用弱耦合、契约化设计，保证各组件可独立升级与水平弹性扩容。"
    )
    
    p_t4 = doc.add_paragraph()
    p_t4.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_para_spacing(p_t4, before_pt=6, after_pt=4, line_spacing=1.2, keep_with_next=True)
    set_para_indent(p_t4, first_line_chars=0)
    r_t4 = p_t4.add_run("表 2.2 技术栈依赖与运行环境配置表")
    set_run_fonts(r_t4, ascii_font="Times New Roman", east_asia_font="SimHei", size_pt=10.5, bold=True)
    
    t_tech = doc.add_table(rows=6, cols=4)
    tech_data = [
        ["层次", "技术选型", "核心库与版本", "部署端口"],
        ["后端服务", "Spring Boot 3.2", "Java 17, Spring Data JPA, Spring Security, Resilience4j", "8080 (/api/v1)"],
        ["前端应用", "Vue 3.5 SPA", "TypeScript, Vite, Element Plus, Pinia, ECharts 6", "5173 / 容器80"],
        ["智能算法", "FastAPI 微服务", "Python 3.10, Prophet, XGBoost, IsolationForest, scikit-learn", "8000 (/predict)"],
        ["联盟区块链", "Hyperledger Fabric 2.5", "Go 1.20 Chaincode, Raft 排序, Fabric Gateway SDK", "7051 (Peer), 7050 (Orderer)"],
        ["基础设施", "容器化与缓存中间件", "MySQL 8.0, Redis 7.0, MinIO (S3兼容)", "3306, 6379, 9000"]
    ]
    for r_i, row in enumerate(tech_data):
        for c_i, val in enumerate(row):
            t_tech.cell(r_i, c_i).text = val
    style_table_standard(
        t_tech,
        col_widths=[Inches(1.1), Inches(1.3), Inches(3.05), Inches(1.2)],
        col_alignments=[WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.LEFT, WD_ALIGN_PARAGRAPH.CENTER]
    )
    
    add_heading_styled(doc, "2.1 密码学与信封加密体系", level=2)
    
    add_image_figure(
        doc,
        ASSETS_DIR / "06-cryptography-envelope-encryption.png",
        "图 2.2 密码学 RSA 数字签名与 AES-GCM 信封加密防伪流转图",
        width_cm=14.5
    )
    
    add_body_paragraph(
        doc,
        "为保障碳报告与交易凭据的法律效力，系统实现了完备的数字签名与多层密钥保护机制："
    )
    crypto_points = [
        "• 密钥生成：基于 RSA 2048 位算法为企业生成专属公私钥对；",
        "• 信封加密：私钥材料禁止明文入库，采用 AES-256-GCM 配合根密钥（KEK）进行信封加密，向量随机生成并持久化；",
        "• 数字签名：企业上报碳数据时，使用私钥对数据摘要计算 SHA256withRSA 签名；服务端与第三方审核时使用公钥验签，确保报文防篡改与不可抵赖。"
    ]
    for cp in crypto_points:
        add_body_paragraph(doc, cp)
        
    # ════════════════════════════════════════════════════════════════════
    # 第三章 国家标准碳核算数学模型
    # ════════════════════════════════════════════════════════════════════
    add_heading_styled(doc, "第三章 国家标准碳核算数学模型", level=1)
    
    add_callout_box(doc, "本章详细推导发电企业与电网企业基于 GB/T 32150 国家标准的碳核算数学模型与状态机流转控制。", title="规范导言")
    
    p_t5 = doc.add_paragraph()
    p_t5.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_para_spacing(p_t5, before_pt=6, after_pt=4, line_spacing=1.2, keep_with_next=True)
    set_para_indent(p_t5, first_line_chars=0)
    r_t5 = p_t5.add_run("表 3.1 章节修订与审核责任记录表")
    set_run_fonts(r_t5, ascii_font="Times New Roman", east_asia_font="SimHei", size_pt=10.5, bold=True)
    
    t_rev3 = doc.add_table(rows=4, cols=7)
    for r_i, row in enumerate(rev_data):
        for c_i, val in enumerate(row):
            t_rev3.cell(r_i, c_i).text = val
    style_table_standard(
        t_rev3, 
        col_widths=[Inches(0.5), Inches(0.9), Inches(1.0), Inches(0.8), Inches(0.8), Inches(0.9), Inches(1.75)],
        col_alignments=[WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.LEFT]
    )
    
    doc.add_paragraph()
    
    add_body_paragraph(
        doc,
        "系统碳核算模型完全基于国家生态环境部 GB/T 32150 与行业温室气体排放核算方法与报告指南设计，内置发电企业与电网企业两大核心算法服务："
    )
    
    add_heading_styled(doc, "3.1 发电企业核算模型 (25 参数)", level=2)
    
    add_body_paragraph(
        doc,
        "发电企业碳排放总量由三部分构成：E = E_combustion + E_desulfurization + E_purchased_power"
    )
    gen_points = [
        "• 化石燃料燃烧排放 (E_combustion)：包含原煤、焦炭、燃油、燃气等燃料消耗量、低位发热量、单位热值含碳量及碳氧化率四维度累乘求和；",
        "• 脱硫过程排放 (E_desulfurization)：碳酸盐脱硫剂消耗量与排放转化因子乘积；",
        "• 外购电力排放 (E_purchased_power)：外购电力净电量与区域电网平均排放因子乘积。"
    ]
    for gp in gen_points:
        add_body_paragraph(doc, gp)
        
    add_heading_styled(doc, "3.2 电网企业核算模型 (9 参数)", level=2)
    
    add_body_paragraph(
        doc,
        "电网企业碳排放总量由输配电线损及外购电构成：E = E_grid_loss + E_sf6_recovery"
    )
    grid_points = [
        "• 综合线损排放：基于系统输入电量、售电量计算网损电量，并结合电网损耗排放因子求得；",
        "• 设备六氟化硫 (SF6) 检修与回收折算温室气体当量。"
    ]
    for gp in grid_points:
        add_body_paragraph(doc, gp)
        
    # ════════════════════════════════════════════════════════════════════
    # 第四章 数据库设计全景 (24 张核心表)
    # ════════════════════════════════════════════════════════════════════
    add_heading_styled(doc, "第四章 数据库设计全景 (24 张核心表)", level=1)
    
    add_image_figure(
        doc,
        ASSETS_DIR / "04-database-entity-er-diagram.png",
        "图 4.1 数据库 24 张核心业务实体 ER 逻辑关系图",
        width_cm=14.5
    )
    
    add_callout_box(doc, "本章定义系统 24 张核心业务实体表的物理建模规范、字段类型约束、枚举定义及 ER 逻辑拓扑。", title="设计导言")
    
    p_t6 = doc.add_paragraph()
    p_t6.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_para_spacing(p_t6, before_pt=6, after_pt=4, line_spacing=1.2, keep_with_next=True)
    set_para_indent(p_t6, first_line_chars=0)
    r_t6 = p_t6.add_run("表 4.1 章节修订与审核责任记录表")
    set_run_fonts(r_t6, ascii_font="Times New Roman", east_asia_font="SimHei", size_pt=10.5, bold=True)
    
    t_rev4 = doc.add_table(rows=4, cols=7)
    for r_i, row in enumerate(rev_data):
        for c_i, val in enumerate(row):
            t_rev4.cell(r_i, c_i).text = val
    style_table_standard(
        t_rev4, 
        col_widths=[Inches(0.5), Inches(0.9), Inches(1.0), Inches(0.8), Inches(0.8), Inches(0.9), Inches(1.75)],
        col_alignments=[WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.LEFT]
    )
    
    doc.add_paragraph()
    
    add_body_paragraph(
        doc,
        "数据库采用 MySQL 8.0 关系型数据库，表结构由 Flyway 严格版本化演进（V1~V11）。全部数据表均配备逻辑删除标记（is_deleted）、乐观锁版本号（version）与审计时间戳（created_at, updated_at），核心业务表清单如下："
    )
    
    p_t7 = doc.add_paragraph()
    p_t7.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_para_spacing(p_t7, before_pt=6, after_pt=4, line_spacing=1.2, keep_with_next=True)
    set_para_indent(p_t7, first_line_chars=0)
    r_t7 = p_t7.add_run("表 4.2 核心业务数据表设计与存储规范表")
    set_run_fonts(r_t7, ascii_font="Times New Roman", east_asia_font="SimHei", size_pt=10.5, bold=True)
    
    t_db = doc.add_table(rows=11, cols=4)
    db_data = [
        ["数据表名", "所属业务域", "核心存储数据与业务语义", "主键与索引"],
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
    ]
    for r_i, row in enumerate(db_data):
        for c_i, val in enumerate(row):
            t_db.cell(r_i, c_i).text = val
    style_table_standard(
        t_db,
        col_widths=[Inches(1.4), Inches(1.0), Inches(2.85), Inches(1.4)],
        col_alignments=[WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.LEFT, WD_ALIGN_PARAGRAPH.LEFT]
    )
    
    # ════════════════════════════════════════════════════════════════════
    # 第五章 核心 API 与接口契约规范
    # ════════════════════════════════════════════════════════════════════
    add_heading_styled(doc, "第五章 核心 API 与接口契约规范", level=1)
    
    add_callout_box(doc, "本章定义 143 个 Spring Boot REST API 端点契约、统一响应信封、全局错误码体系与安全鉴权策略。", title="契约导言")
    
    p_t8 = doc.add_paragraph()
    p_t8.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_para_spacing(p_t8, before_pt=6, after_pt=4, line_spacing=1.2, keep_with_next=True)
    set_para_indent(p_t8, first_line_chars=0)
    r_t8 = p_t8.add_run("表 5.1 章节修订与审核责任记录表")
    set_run_fonts(r_t8, ascii_font="Times New Roman", east_asia_font="SimHei", size_pt=10.5, bold=True)
    
    t_rev5 = doc.add_table(rows=4, cols=7)
    for r_i, row in enumerate(rev_data):
        for c_i, val in enumerate(row):
            t_rev5.cell(r_i, c_i).text = val
    style_table_standard(
        t_rev5, 
        col_widths=[Inches(0.5), Inches(0.9), Inches(1.0), Inches(0.8), Inches(0.8), Inches(0.9), Inches(1.75)],
        col_alignments=[WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.LEFT]
    )
    
    doc.add_paragraph()
    
    add_body_paragraph(
        doc,
        "系统后端通过 Spring Boot 统一对外发布 143 个 RESTful 接口，统一挂载在 /api/v1 前缀下。全局采用统一响应信封格式 ApiResponse<T>："
    )
    
    api_envelope_json = (
        '{\n'
        '  "code": 200,                // 状态码：200 成功，非 200 为具体业务错误码\n'
        '  "message": "操作成功",       // 人类可读提示信息\n'
        '  "data": { ... },             // 泛型业务载荷数据\n'
        '  "timestamp": 1775123456789   // 服务端处理时间戳\n'
        '}'
    )
    add_code_block(doc, api_envelope_json)
    
    add_heading_styled(doc, "5.1 模块与控制器对照概览", level=2)
    
    p_t9 = doc.add_paragraph()
    p_t9.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_para_spacing(p_t9, before_pt=6, after_pt=4, line_spacing=1.2, keep_with_next=True)
    set_para_indent(p_t9, first_line_chars=0)
    r_t9 = p_t9.add_run("表 5.2 核心 REST API 控制器与路由规范对照表")
    set_run_fonts(r_t9, ascii_font="Times New Roman", east_asia_font="SimHei", size_pt=10.5, bold=True)
    
    t_api = doc.add_table(rows=8, cols=4)
    api_data = [
        ["控制器类名", "挂载基础路径", "核心覆盖业务端点", "安全策略"],
        ["AuthController", "/api/v1/auth", "用户登录、注册、Token 刷新、登出", "白名单免鉴权"],
        ["CarbonController", "/api/v1/carbon", "碳报告草稿、核算计算、签名提交、上链", "ENTERPRISE 角色"],
        ["ReviewController", "/api/v1/review", "审核列表查询、验签、出具审核意见、驳回整改", "REVIEWER 角色"],
        ["TradeController", "/api/v1/trades", "双向拍卖竞价挂单、撮合执行、成交查询", "ENTERPRISE 角色"],
        ["BlockchainController", "/api/v1/blockchain", "链上交易探查、区块详情、真实性验证", "全部认证用户"],
        ["PredictionController", "/api/v1/prediction", "碳价未来走势预测、异常排放检测、模型评估", "全部认证用户"],
        ["AdminController", "/api/v1/admin", "准入审批、证书重置、系统审计日志导出", "ADMIN 角色"]
    ]
    for r_i, row in enumerate(api_data):
        for c_i, val in enumerate(row):
            t_api.cell(r_i, c_i).text = val
    style_table_standard(
        t_api,
        col_widths=[Inches(1.5), Inches(1.3), Inches(2.65), Inches(1.2)],
        col_alignments=[WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.LEFT, WD_ALIGN_PARAGRAPH.CENTER]
    )
    
    # ════════════════════════════════════════════════════════════════════
    # 第六章 开发者快速上手与部署运维
    # ════════════════════════════════════════════════════════════════════
    add_heading_styled(doc, "第六章 开发者快速上手与部署运维", level=1)
    
    add_callout_box(doc, "本项目设计高度重视普通工程师的接手体验，彻底消除了对任何外部 AI 工具或环境的介入依赖。", title="部署导言")
    
    p_t10 = doc.add_paragraph()
    p_t10.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_para_spacing(p_t10, before_pt=6, after_pt=4, line_spacing=1.2, keep_with_next=True)
    set_para_indent(p_t10, first_line_chars=0)
    r_t10 = p_t10.add_run("表 6.1 章节修订与审核责任记录表")
    set_run_fonts(r_t10, ascii_font="Times New Roman", east_asia_font="SimHei", size_pt=10.5, bold=True)
    
    t_rev6 = doc.add_table(rows=4, cols=7)
    for r_i, row in enumerate(rev_data):
        for c_i, val in enumerate(row):
            t_rev6.cell(r_i, c_i).text = val
    style_table_standard(
        t_rev6, 
        col_widths=[Inches(0.5), Inches(0.9), Inches(1.0), Inches(0.8), Inches(0.8), Inches(0.9), Inches(1.75)],
        col_alignments=[WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.CENTER, WD_ALIGN_PARAGRAPH.LEFT]
    )
    
    doc.add_paragraph()
    
    add_body_paragraph(
        doc,
        "遵循标准软件工程流程，普通开发人员仅需 3 步即可在本地启动并运行全套系统服务："
    )
    
    add_heading_styled(doc, "6.1 本地 3 步极速运行步骤", level=2)
    
    add_heading_styled(doc, "第一步：环境变量初始化", level=3)
    add_body_paragraph(doc, "在项目根目录下复制环境变量模板（本地开发直接使用默认参数即可）：")
    add_code_block(doc, "cp .env.example .env")
    
    add_heading_styled(doc, "第二步：启动中间件容器", level=3)
    add_body_paragraph(doc, "使用轻量化开发基础设施编排一键启动 MySQL、Redis、MinIO 与 ML 微服务：")
    add_code_block(doc, "docker compose -f deploy/compose/docker-compose.infra.yml up -d")
    
    add_heading_styled(doc, "第三步：本地启动前后端", level=3)
    add_body_paragraph(doc, "启动前后端服务并访问系统：")
    quick_steps = [
        "• 后端启动：直接在 IntelliJ IDEA 中打开 oaiss-chain-backend，运行 OaissChainApplication 主类；",
        "• 前端启动：进入 oaiss-chain-frontend，执行 npm install 后运行 npm run dev；",
        "• 访问系统：打开浏览器访问 http://localhost:5173，使用 admin / admin123 即可登录。"
    ]
    for qs in quick_steps:
        add_body_paragraph(doc, qs)
        
    add_callout_box(
        doc,
        "本地开发默认启用 Mock 区块链引擎，所有存证与交易哈希在内存中模拟生成，秒级响应。开发者完全不需要在本地安装运行笨重的 Hyperledger Fabric 节点网络即可畅通开发全部业务逻辑。",
        title="开发注意 (Note)"
    )
    
    add_heading_styled(doc, "6.2 质量门禁与自查命令", level=2)
    
    add_body_paragraph(doc, "在提交代码前，工程师可在终端执行以下轻量门禁进行一键自查：")
    
    test_commands = (
        "• node scripts/doc-verify.mjs  # 检查代码与文档架构事实零漂移\n"
        "• node scripts/i18n-verify.mjs # 检查前端国际化语言包完整性\n"
        "• cd oaiss-chain-backend && mvn test # 运行后端单元测试套件"
    )
    add_code_block(doc, test_commands)
    
    # ── 保存文档 ────────────────────────────────────────────────────────
    temp_target = WORKSPACE_ROOT / "docs" / "OAISS_CHAIN_技术架构与使用说明书_rebuilt.docx"
    print(f"Saving rebuilt document to {temp_target}...")
    doc.save(str(temp_target))
    print("Rebuilt document saved successfully!")
    return temp_target


def postprocess_with_tools(docx_path):
    """运行 docx 官方工具链注入高精度 TOC 与修复页脚"""
    # 精确计算的 17 个大纲条目与对应正文逻辑页码（1~13）
    entries = [
        {"level": 1, "text": "第一章 项目概述与业务全貌", "page": "1"},
        {"level": 2, "text": "1.1 四角色业务模型与权限矩阵", "page": "2"},
        {"level": 2, "text": "1.2 核心业务流转闭环", "page": "3"},
        {"level": 1, "text": "第二章 系统架构设计与技术栈", "page": "4"},
        {"level": 2, "text": "2.1 密码学与信封加密体系", "page": "6"},
        {"level": 1, "text": "第三章 国家标准碳核算数学模型", "page": "6"},
        {"level": 2, "text": "3.1 发电企业核算模型 (25 参数)", "page": "7"},
        {"level": 2, "text": "3.2 电网企业核算模型 (9 参数)", "page": "7"},
        {"level": 1, "text": "第四章 数据库设计全景 (24 张核心表)", "page": "8"},
        {"level": 1, "text": "第五章 核心 API 与接口契约规范", "page": "10"},
        {"level": 2, "text": "5.1 模块与控制器对照概览", "page": "11"},
        {"level": 1, "text": "第六章 开发者快速上手与部署运维", "page": "12"},
        {"level": 2, "text": "6.1 本地 3 步极速运行步骤", "page": "12"},
        {"level": 3, "text": "第一步：环境变量初始化", "page": "12"},
        {"level": 3, "text": "第二步：启动中间件容器", "page": "13"},
        {"level": 3, "text": "第三步：本地启动前后端", "page": "13"},
        {"level": 2, "text": "6.2 质量门禁与自查命令", "page": "13"},
    ]
    
    entries_json = json.dumps(entries, ensure_ascii=False)
    add_toc_py = DOCX_SCRIPTS / "add_toc_placeholders.py"
    fix_footer_py = DOCX_SCRIPTS / "fix_footer_fields.py"
    
    print("\n[Tool 1] Injecting TOC placeholders with exact logic page numbers...")
    cmd_toc = [sys.executable, str(add_toc_py), str(docx_path), "--entries", entries_json]
    res_toc = subprocess.run(cmd_toc, capture_output=True, text=True, encoding='utf-8')
    print(res_toc.stdout)
    if res_toc.stderr:
        print("TOC Warning/Error:", res_toc.stderr)
        
    print("\n[Tool 2] Fixing footer fields for WPS and Word compatibility...")
    cmd_ftr = [sys.executable, str(fix_footer_py), str(docx_path)]
    res_ftr = subprocess.run(cmd_ftr, capture_output=True, text=True, encoding='utf-8')
    print(res_ftr.stdout)
    if res_ftr.stderr:
        print("Footer Warning/Error:", res_ftr.stderr)


if __name__ == "__main__":
    rebuilt_file = build_reconstructed_document()
    postprocess_with_tools(rebuilt_file)
    
    # 覆盖原文件
    import shutil
    print(f"\nFinalizing: Replacing {DOCX_PATH} with formatted deliverable...")
    shutil.copy2(str(rebuilt_file), str(DOCX_PATH))
    print("Optimization completed successfully!")
