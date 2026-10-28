#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OAISS CHAIN - 毕业设计文档 1:1 官方模板样式还原

依据「软件技术专业2026届毕业设计文档模版」中两份官方模板的实测格式基准，
对任务书与成果说明书实施 1:1 样式还原（页面边距 / 段落直接格式 / 表格单元格样式 / 参考文献缩进）。

官方模板基准：
- 任务书：边距 25/25/25/25mm；表格单元格 宋体 12pt
- 成果说明书：S0 上35 其余 25/25/25/25mm；表格单元格使用「表格正文」样式（10.5pt）；
  段落直接格式：H1(spacing 12/12pt) / 毕设二级·三级(ind firstLine 24pt) / 图表名称(ind firstLine 18.05pt)
  / 毕设正文(spacing 6/6pt, ind firstLine 24pt) / 目录条目(仅点线制表位) / 参考文献(Normal + 悬挂缩进 24pt)
"""

import os
import docx
from docx.shared import Pt, Mm
from docx.oxml import parse_xml
from docx.oxml.ns import qn, nsdecls

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '../..'))
ASG = os.path.join(ROOT, 'docs/软件学院2026届学生毕业设计任务书_OAISS_CHAIN双碳链动系统.docx')
OUT = os.path.join(ROOT, 'docs/软件学院2026届学生毕业设计成果_OAISS_CHAIN双碳链动系统(企软方向).docx')

# ---------- 通用工具 ----------

def reset_paragraph_format(p, before=None, after=None, first_line=None, clear_jc=True, clear_line=True):
    pf = p.paragraph_format
    pf.space_before = Pt(before) if before is not None else None
    pf.space_after = Pt(after) if after is not None else None
    pf.first_line_indent = Pt(first_line) if first_line is not None else None
    pf.left_indent = None
    if clear_line:
        pf.line_spacing = None
    if clear_jc:
        pf.alignment = None

def set_cell_style(doc, cell, style_name):
    """将单元格内所有段落切换到指定样式，并统一字体（Times New Roman + 宋体）"""
    for p in cell.paragraphs:
        try:
            p.style = doc.styles[style_name]
        except KeyError:
            pass

def normalize_run_font(run, east_asia='宋体', size_pt=None):
    rPr = run._r.get_or_add_rPr()
    rFonts = rPr.find(qn('w:rFonts'))
    if rFonts is None:
        rFonts = parse_xml(f'<w:rFonts {nsdecls("w")}/>')
        rPr.insert(0, rFonts)
    rFonts.set(qn('w:ascii'), 'Times New Roman')
    rFonts.set(qn('w:hAnsi'), 'Times New Roman')
    rFonts.set(qn('w:eastAsia'), east_asia)
    rFonts.set(qn('w:cs'), 'Times New Roman')
    run.font.size = Pt(size_pt) if size_pt is not None else None

# ---------- 任务书还原 ----------

def restore_assignment():
    doc = docx.Document(ASG)
    print('【任务书】1:1 样式还原')

    # 1. 页面边距 25/25/25/25
    for sec in doc.sections:
        sec.top_margin = Mm(25)
        sec.bottom_margin = Mm(25)
        sec.left_margin = Mm(25)
        sec.right_margin = Mm(25)
    print('  ✓ 页面边距还原为 25/25/25/25 mm')

    # 2. 表格单元格：宋体 12pt（对齐模板 sz=24）
    n_cells = 0
    for tb in doc.tables:
        for row in tb.rows:
            for cell in row.cells:
                for p in cell.paragraphs:
                    for r in p.runs:
                        if r.text.strip():
                            normalize_run_font(r, '宋体', 12)
                            n_cells += 1
    print(f'  ✓ 表格单元格字体还原为 宋体 12pt（{n_cells} 处）')

    tmp = ASG + '.tmp'
    doc.save(tmp)
    try:
        os.replace(tmp, ASG)
        print(f'  ✓ 已保存: {ASG}')
    except PermissionError:
        print(f'  ! 文件被占用（WPS/Word 打开中），还原结果暂存: {tmp}')
        print(f'    请关闭该文档后运行: python scripts/common/restore_template_1to1.py --finalize')

# ---------- 成果说明书还原 ----------

def restore_outcome():
    doc = docx.Document(OUT)
    print('【成果说明书】1:1 样式还原')

    # 1. 页面边距：S0 上35mm，其余全部 25mm
    for i, sec in enumerate(doc.sections):
        sec.top_margin = Mm(35) if i == 0 else Mm(25)
        sec.bottom_margin = Mm(25)
        sec.left_margin = Mm(25)
        sec.right_margin = Mm(25)
    print(f'  ✓ 页面边距还原（S0 上35mm / 其余 25mm；共 {len(doc.sections)} 节）')

    # 2. 段落直接格式（按样式分类还原）
    stat = {}
    in_refs = False
    for p in doc.paragraphs:
        st = p.style.name
        if st == 'Heading 1' and '参考文献' in p.text:
            in_refs = True
        elif st == 'Heading 1' and in_refs:
            in_refs = False

        if in_refs and p.text.strip().startswith('['):
            # 参考文献：Normal + 悬挂缩进（left 480, hanging 480）
            p.style = doc.styles['Normal']
            pf = p.paragraph_format
            pf.space_before = None
            pf.space_after = None
            pf.line_spacing = None
            pf.alignment = None
            pf.left_indent = Pt(24)
            pf.first_line_indent = Pt(-24)  # 悬挂
            for r in p.runs:
                normalize_run_font(r, '宋体', 10.5)
            stat['参考文献'] = stat.get('参考文献', 0) + 1
            continue

        if st == 'Heading 1':
            reset_paragraph_format(p, before=12, after=12, first_line=None)
            stat['Heading 1'] = stat.get('Heading 1', 0) + 1
        elif st == '毕设二级标题':
            reset_paragraph_format(p, before=None, after=None, first_line=24)
            stat['毕设二级标题'] = stat.get('毕设二级标题', 0) + 1
        elif st == '毕设三级标题':
            reset_paragraph_format(p, before=None, after=None, first_line=24)
            stat['毕设三级标题'] = stat.get('毕设三级标题', 0) + 1
        elif st == '毕设图表名称':
            reset_paragraph_format(p, before=None, after=None, first_line=18.05)
            stat['毕设图表名称'] = stat.get('毕设图表名称', 0) + 1
        elif st == '毕设正文':
            reset_paragraph_format(p, before=6, after=6, first_line=24)
            stat['毕设正文'] = stat.get('毕设正文', 0) + 1
        elif st in ('toc 1', 'toc 2', 'toc 3'):
            reset_paragraph_format(p, before=None, after=None, first_line=None)
            stat[st] = stat.get(st, 0) + 1

    for k, v in stat.items():
        print(f'    · {k}: {v} 段已还原')

    # 3. 表格单元格：使用「表格正文」样式（10.5pt 继承）
    #    注意：跳过前两张表（封面 / 承诺书）—— 属模板原件，保持其原有大字标题与版式
    n_tbl_cells = 0
    for tb_idx, tb in enumerate(doc.tables):
        if tb_idx < 2:
            continue
        for row in tb.rows:
            for cell in row.cells:
                set_cell_style(doc, cell, '表格正文')
                for p in cell.paragraphs:
                    for r in p.runs:
                        if r.text.strip():
                            normalize_run_font(r, '宋体', None)  # 移除显式字号，继承样式 10.5pt
                            n_tbl_cells += 1
    print(f'  ✓ 正文表格单元格还原为「表格正文」样式（{n_tbl_cells} 处，封面/承诺书保持模板原样）')

    tmp = OUT + '.tmp'
    doc.save(tmp)
    try:
        os.replace(tmp, OUT)
        print(f'  ✓ 已保存: {OUT}')
    except PermissionError:
        print(f'  ! 文件被占用（WPS/Word 打开中），还原结果暂存: {tmp}')

def finalize_all():
    for path, label in [(ASG, '任务书'), (OUT, '成果说明书')]:
        tmp = path + '.tmp'
        if not os.path.exists(tmp):
            print(f'  · {label}: 无暂存文件，跳过')
            continue
        try:
            os.replace(tmp, path)
            print(f'  ✓ {label}已最终落盘: {path}')
        except PermissionError:
            print(f'  ! {label}仍被占用（请关闭 WPS/Word）: {tmp}')

if __name__ == '__main__':
    import sys
    if '--finalize' in sys.argv:
        finalize_all()
    else:
        restore_assignment()
        print()
        restore_outcome()
        print()
        print('1:1 样式还原完成')
