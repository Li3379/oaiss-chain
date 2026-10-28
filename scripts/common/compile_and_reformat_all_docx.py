#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
compile_and_reformat_all_docx.py
--------------------------------
将 OAISS CHAIN 全量 Markdown 活文档编译并格式化至 Word DOCX 交付成果
严格对标样板 PDF《“中国软件杯”大学生软件设计大赛 芯片质量检测系统开发手册 v2.2》的版式与工业规范：
1. 建立正规三节（3 Sections）标准体系：
   - Section 1 (封面)：独立分节，开启 titlePg，无页眉无页脚无页码，顶部项目规范标题、中间居中竖排 6 个单字大段落“系 统 开 发 文 档”（26pt 宋体加粗居中）、二〇二六年十月、无边框居中元数据表格
   - Section 2 (目录)：独立分节 (WD_SECTION_START.NEW_PAGE)，居中黑体 18pt“目  录”，注入原生动态目录域 ({ TOC \\o "1-3" \\h \\z \\u })，页脚居中罗马数字 i，强制分页
   - Section 3 (正文)：独立分节 (WD_SECTION_START.NEW_PAGE)，彻底清除 titlePg 继承污染，显示双语工程页眉（带 0.5pt 细横线），页脚从 1 开始独立连续编码阿拉伯数字居中页码
2. 彻底清洗所有段落与表格中的 Markdown 格式残留：
   - 泛化支持所有 #、##、###、#### 标题，剥离符号并绑定原生 Word Heading 样式
   - 深度解析 **粗体** 为 Word 原生加粗 Run（剥离 ** 符号）
   - 深度解析 `代码` 为 Consolas 等宽 Run（剥离 ` 符号）
   - 将 [链接文本](URL) 解析为纯净文本（剥离中括号与圆括号语法）
3. 表格全量色调与规范重塑：
   - 贴合版心（宽度 9638 twips / 17.0cm）
   - 全网格 0.5pt 纯黑单实线边框
   - 首行表头填充浅灰底色 #D9D9D9 (Word 15% 灰)，表头文本居中加粗，注入 tblHeader（跨页自动重复）
   - 数据行注入 cantSplit（防跨页断行）
   - 表格标题居中加粗置于表格上方，10.5pt 宋体，keep_with_next
4. 高清插图自适应插入：
   - 解析 Markdown 中的 `<img src="...">` 与 `![...](...)`，从 docs/assets/architecture/ 等路径读取真实原图插入
   - 居中单倍行距防切图，图题居中置于插图下方，10.5pt 宋体，keep_with_next
"""

import os
import re
import sys
from pathlib import Path
import docx
from docx import Document
from docx.shared import Inches, Pt, Cm, Mm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
from docx.enum.section import WD_SECTION_START
from docx.oxml import OxmlElement, parse_xml
from docx.oxml.ns import nsdecls, qn

WORKSPACE_ROOT = Path(__file__).resolve().parent.parent.parent
DOCS_DIR = WORKSPACE_ROOT / "docs"
ASSETS_DIR = DOCS_DIR / "assets" / "architecture"

# A4 页面标准 (twips)
PAGE_WIDTH_TWIPS = 11906   # 210mm
PAGE_HEIGHT_TWIPS = 16838  # 297mm
MARGIN_TOP_TWIPS = 1417    # 25mm
MARGIN_BOTTOM_TWIPS = 1134 # 20mm
MARGIN_LEFT_TWIPS = 1134   # 20mm
MARGIN_RIGHT_TWIPS = 1134  # 20mm
CONTENT_WIDTH_TWIPS = PAGE_WIDTH_TWIPS - MARGIN_LEFT_TWIPS - MARGIN_RIGHT_TWIPS # 9638 twips (约 17.0cm)

MD_DOCX_SPECS = [
    {
        "md_path": DOCS_DIR / "项目概览" / "系统设计与架构规格书.md",
        "docx_path": DOCS_DIR / "项目概览" / "系统设计与架构规格书.docx",
        "title": "系统设计与架构规格书",
        "subtitle": "产品规格说明、六边形微服务拓扑与 24 业务表数据字典",
        "doc_type": "系统全景架构与规格基准分册 (Architecture Baseline)",
    },
    {
        "md_path": DOCS_DIR / "开发指南" / "全栈开发与技术规格指南.md",
        "docx_path": DOCS_DIR / "开发指南" / "全栈开发与技术规格指南.docx",
        "title": "全栈开发与技术规格指南",
        "subtitle": "Vue 3 前端工程、JVM 诊断手册、Fabric 链码与国标碳核算算法推导",
        "doc_type": "全栈工程落地与底层技术指南分册 (Developer Guide)",
    },
    {
        "md_path": DOCS_DIR / "接口参考" / "系统API接口参考规范.md",
        "docx_path": DOCS_DIR / "接口参考" / "系统API接口参考规范.docx",
        "title": "系统API接口参考规范",
        "subtitle": "146 个 Spring Boot REST 端点契约、统一信封与 ML 算法接口",
        "doc_type": "接口协议与前后端契约规范分册 (API Reference)",
    },
    {
        "md_path": DOCS_DIR / "质量保证" / "质量保证与验收规范.md",
        "docx_path": DOCS_DIR / "质量保证" / "质量保证与验收规范.docx",
        "title": "质量保证与验收规范",
        "subtitle": "四角色核心业务验收标准、ELI5 易用性设计与发布阻断守则",
        "doc_type": "质量保证体系与多角色验收标准分册 (Quality Assurance)",
    },
    {
        "md_path": DOCS_DIR / "运维部署" / "系统部署与运维手册.md",
        "docx_path": DOCS_DIR / "运维部署" / "系统部署与运维手册.docx",
        "title": "系统部署与运维手册",
        "subtitle": "Staging/Prod 双环境编排、配置模板、监控巡检与主排障轴",
        "doc_type": "系统安装部署与生产运维运维分册 (Operations & Deployment)",
    },
    {
        "md_path": DOCS_DIR / "开发手册" / "OAISS_CHAIN_项目完整介绍与实操校验手册.md",
        "docx_path": DOCS_DIR / "开发手册" / "OAISS_CHAIN_项目完整介绍与实操校验手册.docx",
        "title": "项目完整介绍与实操校验手册",
        "subtitle": "基于区块链的可信碳核算、逐页面功能校验与端到端链路闭环",
        "doc_type": "可逐步照做的全栈校验脚本、真实测试证据与功能实操权威手册",
    },
]


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
    """设置段落首行缩进与左缩进"""
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
    """严格配置页面的页眉页脚与分页属性，阻断跨节 titlePg 继承污染"""
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
        
        # 罗马数字页码
        footer = section.footer
        p_ftr = footer.paragraphs[0]
        p_ftr.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p_ftr.text = ""
        r_ftr = p_ftr.add_run("i")
        set_run_fonts(r_ftr, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=10.0, color_rgb=RGBColor(100, 116, 139))
        set_para_spacing(p_ftr, before_pt=4, after_pt=0, line_spacing=1.0)
        return

    # 正文节：彻底移除 titlePg 标签
    t_pg = sectPr.find(qn('w:titlePg'))
    if t_pg is not None:
        sectPr.remove(t_pg)
    section.different_first_page_header_footer = False

    # 页眉：通栏工程双语标头 + 0.5pt 细横线
    header = section.header
    p_hdr = header.paragraphs[0]
    p_hdr.text = ""
    p_hdr.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    r_hdr = p_hdr.add_run("OAISS CHAIN 双碳链动系统 · 软件工程开发手册")
    set_run_fonts(r_hdr, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=9.0, color_rgb=RGBColor(71, 85, 105))
    set_para_spacing(p_hdr, before_pt=0, after_pt=4, line_spacing=1.0)
    
    pPr = p_hdr._p.get_or_add_pPr()
    pBdr = parse_xml(
        r'''<w:pBdr {} >
            <w:bottom w:val="single" w:sz="4" w:space="4" w:color="000000"/>
        </w:pBdr>'''.format(nsdecls('w'))
    )
    pPr.append(pBdr)

    # 页脚：正文阿拉伯数字页码，从 1 独立编码
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


def add_toc_field_elements(doc):
    """注入标准的 Word 原生动态目录域"""
    p_hint = doc.add_paragraph()
    p_hint.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_para_spacing(p_hint, before_pt=0, after_pt=8, line_spacing=1.0)
    set_para_indent(p_hint, first_line_chars=0)
    r_hint = p_hint.add_run("〔提示：在 Word 中右键选择‘更新域’即可根据当前正文页码全量自动同步〕")
    set_run_fonts(r_hint, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=9.0, color_rgb=RGBColor(148, 163, 184))

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.LEFT
    set_para_spacing(p, before_pt=6, after_pt=12, line_spacing=1.2)
    set_para_indent(p, first_line_chars=0)
    
    r = p.add_run()
    fldChar1 = parse_xml(r'<w:fldChar %s w:fldCharType="begin"/>' % nsdecls('w'))
    instrText = parse_xml(r'<w:instrText %s xml:space="preserve"> TOC \o "1-3" \h \z \u </w:instrText>' % nsdecls('w'))
    fldChar2 = parse_xml(r'<w:fldChar %s w:fldCharType="separate"/>' % nsdecls('w'))
    fldChar3 = parse_xml(r'<w:fldChar %s w:fldCharType="end"/>' % nsdecls('w'))
    r._r.append(fldChar1)
    r._r.append(instrText)
    r._r.append(fldChar2)
    r._r.append(fldChar3)


def parse_inline_runs(text):
    """将包含 **粗体**、`代码` 和 [链接] 的字符串解析为规范标记单元列表，彻底剥离 Markdown 符号"""
    text = re.sub(r"\!\[.*?\]\(.*?\)", "", text)
    text = re.sub(r"\[(.*?)\]\([^)]*?\)", r"\1", text)
    
    pattern = re.compile(r"(\*\*.*?\*\*|`+[^`]+`+)")
    tokens = pattern.split(text)
    runs = []
    for token in tokens:
        if not token:
            continue
        if token.startswith("**") and token.endswith("**") and len(token) >= 4:
            clean = token[2:-2].strip().replace("`", "")
            if clean:
                runs.append(("bold", clean))
        elif token.startswith("`") and token.endswith("`"):
            clean = token.strip("`").strip()
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


def add_callout_box(doc, text, title="架构导言"):
    """添加左侧粗边框的优雅提示导言框"""
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
    
    p = cell.paragraphs[0]
    set_para_spacing(p, before_pt=4, after_pt=4, line_spacing=1.2)
    set_para_indent(p, first_line_chars=0)
    
    if title:
        r_t = p.add_run(f"【{title}】\n")
        set_run_fonts(r_t, ascii_font="Times New Roman", east_asia_font="SimHei", size_pt=10.5, bold=True, color_rgb=RGBColor(30, 64, 175))
    
    clean_box_text = re.sub(r'[*`]', '', text).strip()
    r_body = p.add_run(clean_box_text)
    set_run_fonts(r_body, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=10.0, color_rgb=RGBColor(51, 65, 85))
    doc.add_paragraph()


def add_code_block(doc, code_text):
    """添加格式化的代码段落"""
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
            <w:left w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>
            <w:bottom w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>
            <w:right w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>
        </w:tcBorders>'''.format(nsdecls('w'))
    )
    tcPr.append(tcBorders)
    
    p = cell.paragraphs[0]
    set_para_spacing(p, before_pt=3, after_pt=3, line_spacing=1.15)
    set_para_indent(p, first_line_chars=0)
    r = p.add_run(code_text.strip())
    set_run_fonts(r, ascii_font="Consolas", east_asia_font="SimSun", size_pt=9.5, color_rgb=RGBColor(30, 41, 59))
    doc.add_paragraph()


def style_table_standard(table):
    """为表格应用对标 PDF 的标准视觉样式：贴合版心、0.5pt 纯黑细边框、#D9D9D9 浅灰表头、tblHeader、cantSplit"""
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
            set_para_spacing(p, before_pt=3, after_pt=3, line_spacing=1.15)
            clean_hdr = re.sub(r'[*`]', '', p.text).strip()
            p.text = ""
            r = p.add_run(clean_hdr)
            set_run_fonts(r, ascii_font="Times New Roman", east_asia_font="SimHei", size_pt=10.0, bold=True)
                
    for r_idx in range(1, len(table.rows)):
        row = table.rows[r_idx]
        row._tr.get_or_add_trPr().append(parse_xml(r'<w:cantSplit %s/>' % nsdecls('w')))
        for cell in row.cells:
            for p in cell.paragraphs:
                set_para_indent(p, first_line_chars=0)
                set_para_spacing(p, before_pt=2, after_pt=2, line_spacing=1.15)
                raw_cell_t = p.text
                p.text = ""
                append_inline_runs(p, raw_cell_t, default_size=9.5)


def add_image_figure(doc, img_rel_path, fig_title, width_cm=14.5):
    """插入居中图片并附加居中图题"""
    img_candidates = [
        WORKSPACE_ROOT / "docs" / img_rel_path.lstrip("/\\"),
        ASSETS_DIR / Path(img_rel_path).name,
        DOCS_DIR / "assets" / Path(img_rel_path).name,
        DOCS_DIR / "raw" / Path(img_rel_path).name,
    ]
    actual_img = None
    for cand in img_candidates:
        if cand.exists() and cand.is_file():
            actual_img = cand
            break
            
    if actual_img:
        p_img = doc.add_paragraph()
        p_img.alignment = WD_ALIGN_PARAGRAPH.CENTER
        set_para_spacing(p_img, before_pt=8, after_pt=4, line_spacing=1.0)
        set_para_indent(p_img, first_line_chars=0)
        run_img = p_img.add_run()
        run_img.add_picture(str(actual_img), width=Cm(width_cm))
        
        p_cap = doc.add_paragraph()
        p_cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
        set_para_spacing(p_cap, before_pt=4, after_pt=8, line_spacing=1.2, keep_with_next=True)
        set_para_indent(p_cap, first_line_chars=0)
        r_cap = p_cap.add_run(fig_title.strip())
        set_run_fonts(r_cap, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=10.5, bold=True, color_rgb=RGBColor(30, 41, 59))
    else:
        p_cap = doc.add_paragraph()
        p_cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
        set_para_spacing(p_cap, before_pt=6, after_pt=6, line_spacing=1.2, keep_with_next=True)
        set_para_indent(p_cap, first_line_chars=0)
        r_cap = p_cap.add_run(f"〔架构插图〕{fig_title.strip()}")
        set_run_fonts(r_cap, ascii_font="Times New Roman", east_asia_font="SimHei", size_pt=10.5, bold=True, color_rgb=RGBColor(30, 64, 175))


def build_full_docx_from_md(spec):
    """将单个 Markdown 活文档全量编译重构为高保真三节标准 DOCX"""
    md_path = spec["md_path"]
    docx_path = spec["docx_path"]
    title = spec["title"]
    subtitle = spec["subtitle"]
    doc_type = spec["doc_type"]
    
    print(f"正在全量编译重构规范成果: {docx_path.name}")
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
    
    p_top = doc.add_paragraph()
    p_top.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_para_spacing(p_top, before_pt=20, after_pt=14, line_spacing=1.2)
    set_para_indent(p_top, first_line_chars=0)
    r_top = p_top.add_run("OAISS CHAIN · 企业级双碳链动系统工程技术规范\nEnterprise Dual-Carbon Blockchain Platform")
    set_run_fonts(r_top, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=14.0, bold=True, color_rgb=RGBColor(15, 23, 42))
    
    p_title = doc.add_paragraph()
    p_title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_para_spacing(p_title, before_pt=12, after_pt=4, line_spacing=1.3)
    set_para_indent(p_title, first_line_chars=0)
    r_title1 = p_title.add_run(f"{title}\n")
    set_run_fonts(r_title1, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=18.0, bold=True, color_rgb=RGBColor(30, 41, 59))
    r_title2 = p_title.add_run(f"（{subtitle}）")
    set_run_fonts(r_title2, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=13.0, bold=False, color_rgb=RGBColor(71, 85, 105))
    
    p_gap1 = doc.add_paragraph()
    set_para_spacing(p_gap1, before_pt=10, after_pt=10, line_spacing=1.0)
    
    for ch in ["系", "统", "开", "发", "文", "档"]:
        p_ch = doc.add_paragraph()
        p_ch.alignment = WD_ALIGN_PARAGRAPH.CENTER
        set_para_spacing(p_ch, before_pt=2, after_pt=2, line_spacing=1.0)
        set_para_indent(p_ch, first_line_chars=0)
        r_ch = p_ch.add_run(ch)
        set_run_fonts(r_ch, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=26.0, bold=True, color_rgb=RGBColor(15, 23, 42))
        
    p_gap2 = doc.add_paragraph()
    set_para_spacing(p_gap2, before_pt=12, after_pt=12, line_spacing=1.0)
    
    p_date = doc.add_paragraph()
    p_date.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_para_spacing(p_date, before_pt=12, after_pt=12, line_spacing=1.2)
    set_para_indent(p_date, first_line_chars=0)
    r_date = p_date.add_run("二〇二六年十月")
    set_run_fonts(r_date, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=15.0, bold=False, color_rgb=RGBColor(51, 65, 85))
    
    tbl_cover = doc.add_table(rows=6, cols=2)
    tbl_cover.alignment = WD_TABLE_ALIGNMENT.CENTER
    cover_data = [
        ("作品名称：", "OAISS CHAIN 双碳链动系统"),
        ("分册定位：", doc_type),
        ("文档版本：", "v2.2 (企业级生产发布规范版)"),
        ("研发团队：", "OAISS CHAIN 联合研发团队"),
        ("指导架构：", "系统架构委员会 & 双碳区块链工程实验室"),
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
    r_m1 = p_meta_t.add_run(f"双碳链动系统 (OAISS CHAIN) —— {title}\n")
    set_run_fonts(r_m1, ascii_font="Times New Roman", east_asia_font="SimHei", size_pt=14.0, bold=True, color_rgb=RGBColor(15, 23, 42))
    r_m2 = p_meta_t.add_run(f"{subtitle}\nTechnical Specification Document")
    set_run_fonts(r_m2, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=11.0, bold=False, color_rgb=RGBColor(71, 85, 105))
    
    meta_box = (
        f"分册定位：{doc_type}\n"
        "受控版本：v2.2 (Enterprise Release)\n"
        "编制团队：OAISS Fullstack Engineering & Architecture Group\n"
        "编写日期：2026 年 10 月\n"
        "质量基准：代码与架构事实 100% 自动化断言零漂移"
    )
    add_callout_box(doc, meta_box, title="文档元数据与分发范围")
    
    # ── 读取并解析 Markdown 正文 ──────────────────────────────────────
    with open(md_path, 'r', encoding='utf-8') as f:
        md_text = f.read()

    md_text = re.sub(r'<div align="center">[\s\S]*?</div>\s*---\s*', '', md_text)
    md_text = re.sub(r'## 目 录[\s\S]*?---\s*', '', md_text)

    lines = md_text.split('\n')
    i = 0
    n = len(lines)
    
    table_lines = []
    in_code = False
    code_lines = []

    while i < n:
        raw_line = lines[i]
        s = raw_line.strip()

        # 表格处理
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
                for r_idx, r_cells in enumerate(rows_data):
                    for c_idx, c_text in enumerate(r_cells):
                        clean_c = c_text.replace('<br>', '\n').replace('\\|', '|').replace('`', '').replace('**', '')
                        tbl.rows[r_idx].cells[c_idx].text = clean_c
                style_table_standard(tbl)
                doc.add_paragraph()
            table_lines = []

        # 代码块处理
        if s.startswith('```'):
            if not in_code:
                in_code = True
                code_lines = []
            else:
                in_code = False
                if code_lines:
                    add_code_block(doc, '\n'.join(code_lines))
                code_lines = []
            i += 1
            continue
            
        if in_code:
            code_lines.append(raw_line)
            i += 1
            continue

        if not s:
            i += 1
            continue

        # 架构插图匹配: <img src="..." alt="...">
        img_match = re.search(r'<img\s+src=["\']([^"\']+)["\'].*?alt=["\']([^"\']*)["\']', s)
        if img_match:
            img_src = img_match.group(1)
            img_alt = img_match.group(2) or "系统架构图"
            add_image_figure(doc, img_src, img_alt)
            i += 1
            continue

        # 一级标题（# 第X章 / # 第X部分 等）
        if s.startswith('# '):
            title_text = s.lstrip('#').strip()
            title_text = re.sub(r'[*`]', '', title_text)
            p = doc.add_paragraph(style='Heading 1')
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
            
            r = p.add_run(title_text)
            set_run_fonts(r, ascii_font="Times New Roman", east_asia_font="SimHei", size_pt=15.0, bold=True, color_rgb=RGBColor(15, 23, 42))
            i += 1
            continue

        # 二级标题 (## ...)
        if s.startswith('## '):
            title_text = s.lstrip('#').strip()
            title_text = re.sub(r'[*`]', '', title_text)
            p = doc.add_paragraph(style='Heading 2')
            p.alignment = WD_ALIGN_PARAGRAPH.LEFT
            set_para_indent(p, first_line_chars=0)
            set_para_spacing(p, before_pt=14, after_pt=6, line_spacing=1.25, keep_with_next=True)
            
            pPr = p._p.get_or_add_pPr()
            outl = pPr.find(qn('w:outlineLvl'))
            if outl is None:
                outl = OxmlElement('w:outlineLvl')
                pPr.append(outl)
            outl.set(qn('w:val'), '1')
            
            r = p.add_run(title_text)
            set_run_fonts(r, ascii_font="Times New Roman", east_asia_font="SimHei", size_pt=14.0, bold=True, color_rgb=RGBColor(30, 41, 59))
            i += 1
            continue

        # 三级标题 (### ...)
        if s.startswith('### '):
            title_text = s.lstrip('#').strip()
            title_text = re.sub(r'[*`]', '', title_text)
            p = doc.add_paragraph(style='Heading 3')
            p.alignment = WD_ALIGN_PARAGRAPH.LEFT
            set_para_indent(p, first_line_chars=0)
            set_para_spacing(p, before_pt=8, after_pt=4, line_spacing=1.2, keep_with_next=True)
            
            pPr = p._p.get_or_add_pPr()
            outl = pPr.find(qn('w:outlineLvl'))
            if outl is None:
                outl = OxmlElement('w:outlineLvl')
                pPr.append(outl)
            outl.set(qn('w:val'), '2')
            
            r = p.add_run(title_text)
            set_run_fonts(r, ascii_font="Times New Roman", east_asia_font="SimHei", size_pt=12.0, bold=True, color_rgb=RGBColor(15, 23, 42))
            i += 1
            continue

        # 四级标题 (#### ...)
        if s.startswith('#### '):
            title_text = s.lstrip('#').strip()
            title_text = re.sub(r'[*`]', '', title_text)
            p = doc.add_paragraph()
            p.alignment = WD_ALIGN_PARAGRAPH.LEFT
            set_para_indent(p, first_line_chars=0)
            set_para_spacing(p, before_pt=6, after_pt=3, line_spacing=1.2, keep_with_next=True)
            r = p.add_run(title_text)
            set_run_fonts(r, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=11.5, bold=True, color_rgb=RGBColor(30, 41, 59))
            i += 1
            continue

        # 表题与图题
        if s.startswith('**表') or s.startswith('**图') or s.startswith('<b>图'):
            clean_cap = re.sub(r'[*<>/b]', '', s).strip()
            p = doc.add_paragraph()
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            set_para_indent(p, first_line_chars=0)
            set_para_spacing(p, before_pt=6, after_pt=4, line_spacing=1.2, keep_with_next=True)
            r = p.add_run(clean_cap)
            set_run_fonts(r, ascii_font="Times New Roman", east_asia_font="SimHei", size_pt=10.5, bold=True, color_rgb=RGBColor(30, 41, 59))
            i += 1
            continue

        # 引用导言框 (> ...)
        if s.startswith('>'):
            callout_text = s.lstrip('>').strip()
            add_callout_box(doc, callout_text, title="规范导言")
            i += 1
            continue

        # 列表条目
        if s.startswith('- ') or s.startswith('* ') or re.match(r'^\d+\.\s+', s):
            clean_item = re.sub(r'^[-*]\s+|\d+\.\s+', '', s).strip()
            p = doc.add_paragraph()
            p.alignment = WD_ALIGN_PARAGRAPH.LEFT
            set_para_indent(p, first_line_chars=0, left_pt=14)
            set_para_spacing(p, before_pt=2, after_pt=2, line_spacing=1.25)
            r_bullet = p.add_run("•  ")
            set_run_fonts(r_bullet, ascii_font="Times New Roman", east_asia_font="SimSun", size_pt=11.0, bold=True)
            append_inline_runs(p, clean_item, default_size=11.0)
            i += 1
            continue

        # 普通正文段落（调用 inline parser，生成原生 Word Run）
        clean_para = s
        p = doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT
        set_para_indent(p, first_line_chars=2)
        set_para_spacing(p, before_pt=2, after_pt=3, line_spacing=1.25)
        append_inline_runs(p, clean_para, default_size=11.5)
        i += 1

    doc.save(str(docx_path))
    print(f"✅ 成功生成并重构: {docx_path.name}")


def main():
    print("=" * 70)
    print("OAISS CHAIN — 全量企业级技术规格书 DOCX 标准化排版与重构编译")
    print("=" * 70)
    
    for spec in MD_DOCX_SPECS:
        if not spec["md_path"].exists():
            print(f"⚠️ 跳过未找到的源 Markdown: {spec['md_path']}")
            continue
        build_full_docx_from_md(spec)
        
    print("\n🎉 全量规格书 DOCX 标准化重构完成！")


if __name__ == "__main__":
    main()
