#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OAISS CHAIN - 按照参考 PDF (软件杯OAISS CHAIN 双碳链动系统开发手册) 严格复刻 DOCX 版式与视觉色调
1. 页面设置: A4 (210mm x 297mm), 左右边距 20mm, 上边距 30mm, 下边距 22mm
2. 页眉设置: 通栏居左/右，底边附 0.5pt 黑色水平细实线
3. 字体与色调: 正文 SimSun / Times New Roman 12pt (小四), 纯黑 #000000, 1.5倍行距, 首行缩进 2 字符 (24pt)
4. 各级标题:
   - 一级标题 (第X章/第X部分): SimHei 15pt (小三), 加粗居中, 前置分页, 段前 12pt, 段后 18pt
   - 二级标题 (X. 节): SimHei 14pt (四号), 加粗靠左, 段前 14pt, 段后 8pt
   - 三级标题 (X.X 小节): SimSun Bold 12pt (小四), 加粗靠左, 段前 8pt, 段后 4pt
5. 表格全量色调重塑:
   - 全网格 0.5pt 纯黑单实线边框 (w:sz="4", w:color="000000")
   - 首行表头填充浅灰底色 #D9D9D9 (Word 15% 灰), 表头文本居中加粗
   - 表格标题居中置于表格上方, 10.5pt
6. 目录 (TOC): 居中“目  录” (小二号 18pt), 严格点线引导符 (········) 与右对齐
"""

import os
import re
import docx
from docx.shared import Pt, Mm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import parse_xml
from docx.oxml.ns import qn

DOCS_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '../../docs'))

TARGET_DOCX = [
    os.path.join(DOCS_DIR, '项目概览/系统设计与架构规格书.docx'),
    os.path.join(DOCS_DIR, '开发指南/全栈开发与技术规格指南.docx'),
    os.path.join(DOCS_DIR, '接口参考/系统API接口参考规范.docx'),
    os.path.join(DOCS_DIR, '质量保证/质量保证与验收规范.docx'),
    os.path.join(DOCS_DIR, '运维部署/系统部署与运维手册.docx'),
    os.path.join(DOCS_DIR, 'OAISS_CHAIN_技术架构与使用说明书.docx'),
    os.path.join(DOCS_DIR, 'raw/01~05原始需求与标准规格书.docx'),
    os.path.join(DOCS_DIR, '软件学院2026届学生毕业设计成果_OAISS_CHAIN双碳链动系统(企软方向).docx'),
    os.path.join(DOCS_DIR, '软件学院2026届学生毕业设计任务书_OAISS_CHAIN双碳链动系统.docx'),
]

def format_docx(file_path):
    print(f"正在对标 PDF 排版重塑: {file_path}")
    doc = docx.Document(file_path)
    
    # 1. 页面设置 (A4, 20mm 边距)
    for sec in doc.sections:
        sec.page_width = Mm(210)
        sec.page_height = Mm(297)
        sec.left_margin = Mm(20.0)
        sec.right_margin = Mm(20.0)
        sec.top_margin = Mm(30.0)
        sec.bottom_margin = Mm(22.0)
        sec.header_distance = Mm(15.0)
        sec.footer_distance = Mm(15.0)
        
        # 页眉设置
        header = sec.header
        if header.paragraphs:
            p_head = header.paragraphs[0]
            if not p_head.text.strip():
                p_head.text = "OAISS CHAIN 双碳链动系统 · 软件工程开发手册"
            p_head.alignment = WD_ALIGN_PARAGRAPH.LEFT
            for r in p_head.runs:
                r.font.name = "Times New Roman"
                r._element.rPr.get_or_add_rFonts().set(qn("w:eastAsia"), "SimSun")
                r.font.size = Pt(10.5)
                r.font.color.rgb = RGBColor(0, 0, 0)
                
            # 0.5pt 黑色下划线 (OpenXML sz=4 -> 0.5pt)
            pPr = p_head._element.get_or_add_pPr()
            # 移除已有的 pBdr 避免重复
            for c in list(pPr):
                if c.tag.endswith('pBdr'):
                    pPr.remove(c)
            pBdr = parse_xml(r'<w:pBdr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">'
                             r'  <w:bottom w:val="single" w:sz="4" w:space="4" w:color="000000"/>'
                             r'</w:pBdr>')
            pPr.append(pBdr)

    # 2. 表格全量色调与边框重塑
    tbl_borders_xml = (
        r'<w:tblBorders xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">'
        r'  <w:top w:val="single" w:sz="4" w:space="0" w:color="000000"/>'
        r'  <w:left w:val="single" w:sz="4" w:space="0" w:color="000000"/>'
        r'  <w:bottom w:val="single" w:sz="4" w:space="0" w:color="000000"/>'
        r'  <w:right w:val="single" w:sz="4" w:space="0" w:color="000000"/>'
        r'  <w:insideH w:val="single" w:sz="4" w:space="0" w:color="000000"/>'
        r'  <w:insideV w:val="single" w:sz="4" w:space="0" w:color="000000"/>'
        r'</w:tblBorders>'
    )
    shd_header_xml = (
        r'<w:shd xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" '
        r'w:val="clear" w:color="auto" w:fill="D9D9D9"/>'
    )
    
    for tbl in doc.tables:
        tblPr = tbl._tbl.tblPr
        # 移除旧边框
        for c in list(tblPr):
            if c.tag.endswith('tblBorders'):
                tblPr.remove(c)
        tblPr.append(parse_xml(tbl_borders_xml))
        
        # 表头单元格浅灰 #D9D9D9
        if tbl.rows:
            header_row = tbl.rows[0]
            for cell in header_row.cells:
                tcPr = cell._tc.get_or_add_tcPr()
                for c in list(tcPr):
                    if c.tag.endswith('shd'):
                        tcPr.remove(c)
                tcPr.append(parse_xml(shd_header_xml))
                
                # 表头文字加粗居中
                for p in cell.paragraphs:
                    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
                    p.paragraph_format.first_line_indent = Pt(0)
                    p.paragraph_format.line_spacing = 1.2
                    p.paragraph_format.space_before = Pt(2)
                    p.paragraph_format.space_after = Pt(2)
                    for r in p.runs:
                        r.font.name = "Times New Roman"
                        r._element.rPr.get_or_add_rFonts().set(qn("w:eastAsia"), "SimSun")
                        r.font.size = Pt(10.5)
                        r.font.bold = True
                        r.font.color.rgb = RGBColor(0, 0, 0)
                        
            # 数据行文字样式
            for r_idx in range(1, len(tbl.rows)):
                row = tbl.rows[r_idx]
                for cell in row.cells:
                    for p in cell.paragraphs:
                        p.paragraph_format.first_line_indent = Pt(0)
                        p.paragraph_format.line_spacing = 1.2
                        p.paragraph_format.space_before = Pt(2)
                        p.paragraph_format.space_after = Pt(2)
                        for r in p.runs:
                            r.font.name = "Times New Roman"
                            r._element.rPr.get_or_add_rFonts().set(qn("w:eastAsia"), "SimSun")
                            r.font.size = Pt(10.5)
                            r.font.color.rgb = RGBColor(0, 0, 0)

    # 3. 段落与标题样式重塑
    for p in doc.paragraphs:
        text = p.text.strip()
        if not text:
            p.paragraph_format.space_before = Pt(0)
            p.paragraph_format.space_after = Pt(0)
            continue
            
        # 目录标题
        if text in ['目  录', '目录', 'Table of Contents']:
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p.paragraph_format.first_line_indent = Pt(0)
            p.paragraph_format.space_before = Pt(18)
            p.paragraph_format.space_after = Pt(18)
            for r in p.runs:
                r.font.name = "Times New Roman"
                r._element.rPr.get_or_add_rFonts().set(qn("w:eastAsia"), "SimSun")
                r.font.size = Pt(18)  # 小二号
                r.font.bold = True
                r.font.color.rgb = RGBColor(0, 0, 0)
            continue
            
        # 目录项 (点线引导行)
        if re.search(r'\[.+\]\(#.+\)', text) or ('·' in text and re.search(r'\d+$', text)):
            p.alignment = WD_ALIGN_PARAGRAPH.LEFT
            p.paragraph_format.line_spacing = 1.0
            p.paragraph_format.space_before = Pt(0)
            p.paragraph_format.space_after = Pt(0)
            # 缩进设置
            if re.match(r'^\s*第[一二三四五六七八九十0-9]+[部分章节篇]', text):
                p.paragraph_format.first_line_indent = Pt(0)
            elif re.match(r'^\s*\d+\.\d+\s', text):
                p.paragraph_format.first_line_indent = Pt(21)  # 2 字符缩进
            elif re.match(r'^\s*\d+\.\d+\.\d+\s', text):
                p.paragraph_format.first_line_indent = Pt(42)  # 4 字符缩进
            for r in p.runs:
                r.font.name = "Times New Roman"
                r._element.rPr.get_or_add_rFonts().set(qn("w:eastAsia"), "SimSun")
                r.font.size = Pt(10.5)
                r.font.color.rgb = RGBColor(0, 0, 0)
            continue
            
        # 表格标题或图表标题
        if re.match(r'^(表|图)\s*\d+', text) or text.startswith('**表 ') or text.startswith('**图 '):
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p.paragraph_format.first_line_indent = Pt(0)
            p.paragraph_format.space_before = Pt(6)
            p.paragraph_format.space_after = Pt(4)
            for r in p.runs:
                r.font.name = "Times New Roman"
                r._element.rPr.get_or_add_rFonts().set(qn("w:eastAsia"), "SimSun")
                r.font.size = Pt(10.5)  # 五号
                r.font.bold = True
                r.font.color.rgb = RGBColor(0, 0, 0)
            continue
            
        # 一级标题 (第X章 / 第X部分)
        if re.match(r'^第[一二三四五六七八九十0-9]+[部分章节篇]', text):
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p.paragraph_format.first_line_indent = Pt(0)
            p.paragraph_format.space_before = Pt(12)
            p.paragraph_format.space_after = Pt(18)
            p.paragraph_format.page_break_before = True
            for r in p.runs:
                r.font.name = "Times New Roman"
                r._element.rPr.get_or_add_rFonts().set(qn("w:eastAsia"), "SimHei")
                r.font.size = Pt(15)  # 小三号
                r.font.bold = True
                r.font.color.rgb = RGBColor(0, 0, 0)
            continue

        # 二级标题 (1. 2. 3.)
        m2 = re.match(r'^(\d+)\.\s*([^\n。；]{2,50})$', text)
        if m2 and not text.endswith(('。', '；', '：', ':')):
            p.alignment = WD_ALIGN_PARAGRAPH.LEFT
            p.paragraph_format.first_line_indent = Pt(0)
            p.paragraph_format.space_before = Pt(14)
            p.paragraph_format.space_after = Pt(8)
            for r in p.runs:
                r.font.name = "Arial"
                r._element.rPr.get_or_add_rFonts().set(qn("w:eastAsia"), "SimHei")
                r.font.size = Pt(14)  # 四号
                r.font.bold = True
                r.font.color.rgb = RGBColor(0, 0, 0)
            continue

        # 三级标题 (1.1, 2.3)
        m3 = re.match(r'^(\d+\.\d+)\s*([^\n。；]{2,60})$', text)
        if m3 and not text.endswith(('。', '；', '：', ':')):
            p.alignment = WD_ALIGN_PARAGRAPH.LEFT
            p.paragraph_format.first_line_indent = Pt(0)
            p.paragraph_format.space_before = Pt(8)
            p.paragraph_format.space_after = Pt(4)
            for r in p.runs:
                r.font.name = "Times New Roman"
                r._element.rPr.get_or_add_rFonts().set(qn("w:eastAsia"), "SimSun")
                r.font.size = Pt(12)  # 小四号
                r.font.bold = True
                r.font.color.rgb = RGBColor(0, 0, 0)
            continue

        # 四级标题 (1.1.1, 2.3.4)
        m4 = re.match(r'^(\d+\.\d+\.\d+)\s*([^\n。；]{2,70})$', text)
        if m4 and not text.endswith(('。', '；', '：', ':')):
            p.alignment = WD_ALIGN_PARAGRAPH.LEFT
            p.paragraph_format.first_line_indent = Pt(0)
            p.paragraph_format.space_before = Pt(6)
            p.paragraph_format.space_after = Pt(2)
            for r in p.runs:
                r.font.name = "Times New Roman"
                r._element.rPr.get_or_add_rFonts().set(qn("w:eastAsia"), "SimSun")
                r.font.size = Pt(12)  # 小四号
                r.font.bold = True
                r.font.color.rgb = RGBColor(0, 0, 0)
            continue

        # 常规正文段落
        p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
        # 列表或代码行不首行缩进
        if text.startswith('•') or text.startswith('-') or text.startswith('```') or text.startswith('>'):
            p.paragraph_format.first_line_indent = Pt(0)
        else:
            p.paragraph_format.first_line_indent = Pt(24)  # 2字符首行缩进 (24pt)
            
        p.paragraph_format.line_spacing = 1.5              # 1.5 倍行距
        p.paragraph_format.space_before = Pt(0)
        p.paragraph_format.space_after = Pt(0)
        
        for r in p.runs:
            r.font.name = "Times New Roman"
            r._element.rPr.get_or_add_rFonts().set(qn("w:eastAsia"), "SimSun")
            r.font.size = Pt(12)  # 小四号
            r.font.color.rgb = RGBColor(0, 0, 0)

    # 保存覆盖
    temp_save = file_path + ".tmp"
    doc.save(temp_save)
    os.replace(temp_save, file_path)
    print(f"成功重排并覆盖: {file_path}")

def main():
    for f in TARGET_DOCX:
        if os.path.exists(f):
            format_docx(f)
        else:
            print(f"[跳过] 找不到文件: {f}")

if __name__ == '__main__':
    main()
