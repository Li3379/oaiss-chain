#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OAISS CHAIN - 毕业设计成果说明书重建（从纯净官方模板 1:1 复刻）

流程：
1. 复制纯净官方模板（保留校徽页眉、封面/承诺书版式、分节页码体系）
2. 填充封面字段与承诺书日期、勾选选题类型
3. 保留前 3 节（封面/承诺书/目录），替换正文内容为项目内容
4. 重建目录（保留分节符）、设置正文页码（阿拉伯数字起始 1）
"""

import os
import sys
import re
import shutil
import docx
from docx.shared import Pt
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import parse_xml
from docx.oxml.ns import qn, nsdecls
from docx.text.paragraph import Paragraph

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '../..'))
sys.path.insert(0, os.path.join(ROOT, 'scripts/common'))

TPL = 'C:/Users/LiShuai/Desktop/软件技术专业2026届毕业设计文档模版/软件学院2026届学生毕业设计成果（学号姓名）_模板(企软方向).docx'
OUT = os.path.join(ROOT, 'docs/软件学院2026届学生毕业设计成果_OAISS_CHAIN双碳链动系统(企软方向).docx')

# 复用既有内容构建器
from rebuild_thesis_outcome_doc import (
    build_chapter_1, build_chapter_2, build_chapter_3, build_chapter_4,
    build_references, TOC_ENTRIES, set_run_font,
)

TOPIC = '双碳链动系统 (OAISS CHAIN) 的设计与实现——碳核算、区块链存证与智能交易系统'
COLLEGE = '软件学院'
MAJOR = '软件技术'
CLASS_NAME = '软件技术2404'
NAME = '李帅'
STU_ID = '245102030510'
ADVISORS = '陈湘龙、蒋钊林'
COMMIT_DATE = '2026年4月10日'

def set_cell_text(cell, text, size_pt=None, bold=False):
    for p in cell.paragraphs[1:]:
        p._p.getparent().remove(p._p)
    p = cell.paragraphs[0]
    for r in list(p.runs):
        r._r.getparent().remove(r._r)
    p.paragraph_format.first_line_indent = Pt(0)
    r = p.add_run(text)
    r.font.name = 'Times New Roman'
    rPr = r._r.get_or_add_rPr()
    rPr.insert(0, parse_xml(f'<w:rFonts {nsdecls("w")} w:ascii="Times New Roman" w:hAnsi="Times New Roman" w:eastAsia="宋体" w:cs="Times New Roman"/>'))
    if size_pt is not None:
        r.font.size = Pt(size_pt)
    r.font.bold = bold

def check_checkbox(cell, index=0):
    """将单元格内第 index 个 Wingdings 00A8(☐) 改为 00FE(☑)"""
    syms = cell._tc.findall('.//' + qn('w:sym'))
    cnt = 0
    for sym in syms:
        if sym.get(qn('w:char')) == '00A8':
            if cnt == index:
                sym.set(qn('w:char'), '00FE')
                return True
            cnt += 1
    return False

def fill_cover(doc):
    t0 = doc.tables[0]
    # R1 选题名称
    set_cell_text(t0.rows[1].cells[1], TOPIC, None, True)
    # R2 选题类型：勾选「产品设计类」（第一个 ☐ → ☑）
    ok = check_checkbox(t0.rows[2].cells[1], 0)
    print(f'  ✓ 选题类型勾选产品设计类: {ok}')
    # R3~R8 字段
    set_cell_text(t0.rows[3].cells[1], COLLEGE, None, True)
    set_cell_text(t0.rows[4].cells[1], MAJOR, None, True)
    set_cell_text(t0.rows[5].cells[1], CLASS_NAME, None, True)
    set_cell_text(t0.rows[6].cells[1], NAME, None, True)
    set_cell_text(t0.rows[7].cells[1], STU_ID, None, True)
    set_cell_text(t0.rows[8].cells[1], ADVISORS, None, True)
    print(f'  ✓ 封面字段: {COLLEGE}/{MAJOR}/{CLASS_NAME}/{NAME}/{STU_ID}/{ADVISORS}')

    # 承诺书日期（T1 行2、行6 的末列）
    t1 = doc.tables[1]
    filled = 0
    for r_idx in (2, 6):
        cell = t1.rows[r_idx].cells[-1]
        if '2026年4月' in cell.text:
            set_cell_text(cell, COMMIT_DATE, None, False)
            filled += 1
    print(f'  ✓ 承诺书日期: {COMMIT_DATE}（{filled} 处）')

def delete_body_after_toc(doc):
    """删除目录之后的全部正文（保留正文末节 sectPr），返回删除计数"""
    body = doc.element.body
    children = list(body)

    start = None
    for i, el in enumerate(children):
        if not el.tag.endswith('}p'):
            continue
        p = Paragraph(el, doc)
        if p.style.name == 'Heading 1' and '设计任务' in p.text:
            start = i
            break
    if start is None:
        raise SystemExit('未定位到正文起始（Heading 1 设计任务）')

    sectPr = body.find(qn('w:sectPr'))
    removed = 0
    for i in range(len(children) - 1, start - 1, -1):
        el = children[i]
        if el is sectPr:
            continue
        # 若段落持有分节符（最后一节之前的），跳过（不应存在于本区间）
        body.remove(el)
        removed += 1
    print(f'  ✓ 已清除模板正文示例元素: {removed} 个（自索引 {start}）')
    return start

def rebuild_toc_preserving_break(doc):
    """重建目录条目：保留末条目上的分节符（sectPr）"""
    body = doc.element.body
    children = list(body)

    toc_idx = [i for i, el in enumerate(children)
               if el.tag.endswith('}p') and Paragraph(el, doc).style.name in ('toc 1', 'toc 2', 'toc 3')]
    if not toc_idx:
        print('  ! 未定位到旧目录条目，跳过')
        return

    start_idx = toc_idx[0]
    # 找到域结束段
    end_idx = None
    for i in range(start_idx, len(children)):
        el = children[i]
        if not el.tag.endswith('}p'):
            continue
        p = Paragraph(el, doc)
        if p.style.name not in ('toc 1', 'toc 2', 'toc 3') and 'w:fldCharType="end"' in el.xml:
            end_idx = i
            break
    if end_idx is None:
        end_idx = toc_idx[-1]

    # 捕获区间内可能存在的分节符
    kept_sectPr = None
    for i in range(start_idx, end_idx + 1):
        s = children[i].find('.//' + qn('w:sectPr'))
        if s is not None:
            kept_sectPr = s
            break

    anchor = children[start_idx]

    # 构造新元素
    new_elems = []
    p_b = doc.add_paragraph()
    p_b.style = doc.styles['Normal']
    pb = p_b.paragraph_format
    pb.space_before = Pt(0); pb.space_after = Pt(0); pb.first_line_indent = Pt(0); pb.line_spacing = None
    r_b = p_b.add_run()
    r_b._r.append(parse_xml('<w:fldChar %s w:fldCharType="begin"/>' % nsdecls('w')))
    r_b._r.append(parse_xml('<w:instrText %s xml:space="preserve"> TOC \\o "1-3" \\h \\z \\u </w:instrText>' % nsdecls('w')))
    r_b._r.append(parse_xml('<w:fldChar %s w:fldCharType="separate"/>' % nsdecls('w')))
    p_b._p.getparent().remove(p_b._p)
    new_elems.append(p_b._p)

    for lvl, title, page in TOC_ENTRIES:
        p = doc.add_paragraph()
        p.style = doc.styles[{1: 'toc 1', 2: 'toc 2', 3: 'toc 3'}[lvl]]
        pPr = p._p.get_or_add_pPr()
        pPr.append(parse_xml('<w:tabs %s><w:tab w:val="right" w:leader="dot" w:pos="9072"/></w:tabs>' % nsdecls('w')))
        pf = p.paragraph_format
        pf.first_line_indent = None; pf.left_indent = None
        pf.space_before = None; pf.space_after = None; pf.line_spacing = None
        r1 = p.add_run(title); set_run_font(r1, 10.5, lvl == 1)
        r2 = p.add_run('\t'); set_run_font(r2, 10.5, lvl == 1)
        r3 = p.add_run(str(page)); set_run_font(r3, 10.5, lvl == 1)
        p._p.getparent().remove(p._p)
        new_elems.append(p._p)

    # 域结束段（并把分节符挂到最后一个新元素上）
    p_e = doc.add_paragraph()
    p_e.style = doc.styles['Normal']
    pe = p_e.paragraph_format
    pe.space_before = Pt(0); pe.space_after = Pt(0); pe.first_line_indent = Pt(0); pe.line_spacing = None
    r_e = p_e.add_run()
    r_e._r.append(parse_xml('<w:fldChar %s w:fldCharType="end"/>' % nsdecls('w')))
    p_e._p.getparent().remove(p_e._p)
    new_elems.append(p_e._p)

    # 插入新元素（在 anchor 之前）
    for el in new_elems:
        anchor.addprevious(el)

    # 将捕获的分节符挂到最后一个旧条目位置（保留节结构）
    if kept_sectPr is not None:
        last_new_p = new_elems[-1]
        pPr = last_new_p.get_or_add_pPr()
        pPr.append(kept_sectPr)
        print('  ✓ 已保留目录节的分节符')

    # 删除旧元素
    for i in range(end_idx, start_idx - 1, -1):
        body.remove(children[i])
    print(f'  ✓ 目录已重建（{len(TOC_ENTRIES)} 条），替换元素 {end_idx - start_idx + 1} 个')

def setup_body_footer(doc):
    """正文末节：页脚阿拉伯页码，起始 1"""
    sec = doc.sections[-1]
    # 页码格式：十进制，起始 1
    sectPr = sec._sectPr
    for el in sectPr.findall(qn('w:pgNumType')):
        sectPr.remove(el)
    sectPr.append(parse_xml(f'<w:pgNumType {nsdecls("w")} w:start="1" w:fmt="decimal"/>'))

    # 解除页脚继承并写入 PAGE 域
    footer = sec.footer
    footer.is_linked_to_previous = False
    for p in footer.paragraphs[1:]:
        p._p.getparent().remove(p._p)
    p = footer.paragraphs[0]
    for r in list(p.runs):
        r._r.getparent().remove(r._r)
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.first_line_indent = Pt(0)
    r = p.add_run()
    r._r.append(parse_xml('<w:fldChar %s w:fldCharType="begin"/>' % nsdecls('w')))
    r._r.append(parse_xml('<w:instrText %s xml:space="preserve"> PAGE </w:instrText>' % nsdecls('w')))
    r._r.append(parse_xml('<w:fldChar %s w:fldCharType="end"/>' % nsdecls('w')))
    set_run_font(r, 10.5)
    print('  ✓ 正文页脚已设置阿拉伯页码（起始 1）')

def main():
    try:
        shutil.copyfile(TPL, OUT)
        print(f'已复制官方模板 -> {OUT}')
        target_doc = OUT
    except PermissionError:
        tmp_target = OUT + '.tmp'
        shutil.copyfile(TPL, tmp_target)
        print(f'! 目标文件正被 WPS 打开，改在暂存文件操作: {tmp_target}')
        target_doc = tmp_target

    doc = docx.Document(target_doc)
    print('【封面与承诺书】')
    fill_cover(doc)

    print('【正文替换】')
    delete_body_after_toc(doc)
    rebuild_toc_preserving_break(doc)

    print('【内容生成】')
    build_chapter_1(doc)
    build_chapter_2(doc)
    build_chapter_3(doc)
    build_chapter_4(doc)
    build_references(doc)

    print('【页码设置】')
    setup_body_footer(doc)

    tmp = OUT + '.tmp'
    doc.save(tmp)
    try:
        os.replace(tmp, OUT)
        print(f'成果说明书重建完成: {OUT}')
    except PermissionError:
        print(f'! 目标文件正被 WPS 打开，成果说明书已暂存: {tmp}')
        print(f'  关闭 WPS 窗口后运行 finalize_thesis_1to1.bat 即可一键落盘！')

if __name__ == '__main__':
    main()
