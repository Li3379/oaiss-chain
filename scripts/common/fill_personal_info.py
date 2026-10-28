#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OAISS CHAIN - 毕业设计文档个人信息填写

依据桌面《李帅_245102030510_国家奖学金申请材料》与《校企指导老师对应表》核对确认的个人信息：
- 姓名：李帅  学号：245102030510  班级：软件技术2404  专业：软件技术  二级学院：软件学院
- 指导教师：陈湘龙（校内）、蒋钊林（企业）

填写范围：
1. 任务书：姓名 / 学号 / 班级名称 / 选题类型（产品设计类加粗标记）
2. 成果说明书：封面 二级学院 / 专业名称 / 班级名称 / 姓名 / 学号 / 指导教师；承诺书日期
"""

import os
import docx
from docx.shared import Pt
from docx.oxml import parse_xml
from docx.oxml.ns import qn, nsdecls

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '../..'))
ASG = os.path.join(ROOT, 'docs/软件学院2026届学生毕业设计任务书_OAISS_CHAIN双碳链动系统.docx')
OUT = os.path.join(ROOT, 'docs/软件学院2026届学生毕业设计成果_OAISS_CHAIN双碳链动系统(企软方向).docx')

NAME = '李帅'
STU_ID = '245102030510'
CLASS_NAME = '软件技术2404'
MAJOR = '软件技术'
COLLEGE = '软件学院'
ADVISORS = '陈湘龙、蒋钊林'
COMMIT_DATE = '2026年4月10日'

def set_cell_text(cell, text, size_pt=None, bold=False, font='宋体'):
    """重写单元格文本（保留单元格样式，统一字体）"""
    # 清空所有段落（保留第一段）
    for p in cell.paragraphs[1:]:
        p._p.getparent().remove(p._p)
    p = cell.paragraphs[0]
    for r in list(p.runs):
        r._r.getparent().remove(r._r)
    p.paragraph_format.first_line_indent = Pt(0)
    r = p.add_run(text)
    r.font.name = 'Times New Roman'
    rPr = r._r.get_or_add_rPr()
    rFonts = rPr.find(qn('w:rFonts'))
    if rFonts is None:
        rFonts = parse_xml(f'<w:rFonts {nsdecls("w")}/>')
        rPr.insert(0, rFonts)
    rFonts.set(qn('w:ascii'), 'Times New Roman')
    rFonts.set(qn('w:hAnsi'), 'Times New Roman')
    rFonts.set(qn('w:eastAsia'), font)
    if size_pt is not None:
        r.font.size = Pt(size_pt)
    r.font.bold = bold

def find_cell_by_label(table, label):
    """在表中查找包含指定标签的单元格，返回其右侧值单元格"""
    for row in table.rows:
        for c_idx, cell in enumerate(row.cells):
            if cell.text.strip().replace('\u3000', '') == label or cell.text.strip() == label:
                # 返回该行最后一个单元格（值列）
                return row.cells[-1]
    return None

def fill_assignment():
    doc = docx.Document(ASG)
    print('【任务书】填写个人信息')
    t0 = doc.tables[0]

    # 表0 结构: R0: 二级学院|软件学院|姓名|张三|校内指导教师|陈湘龙
    #          R1: 班级名称|软件技术xxxx|学号|XXXXXX|企业指导教师|蒋钊林
    r0 = t0.rows[0]
    set_cell_text(r0.cells[3], NAME, 12, True)          # 姓名值
    r1 = t0.rows[1]
    set_cell_text(r1.cells[1], CLASS_NAME, 12, True)    # 班级名称值
    set_cell_text(r1.cells[3], STU_ID, 12, True)        # 学号值
    print(f'  ✓ 姓名={NAME}  学号={STU_ID}  班级={CLASS_NAME}')

    # 选题类型行（R3）：标记"产品设计类"
    type_cell = t0.rows[3].cells[1]
    text = type_cell.text
    set_cell_text(type_cell, '', 12)  # 先清空
    p = type_cell.paragraphs[0]
    p.paragraph_format.first_line_indent = Pt(0)
    r1 = p.add_run('产品设计类')
    r1.font.name = 'Times New Roman'
    r1._r.get_or_add_rPr().append(parse_xml(f'<w:rFonts {nsdecls("w")} w:eastAsia="宋体"/>'))
    r1.font.size = Pt(12)
    r1.font.bold = True  # 加粗标记选中项
    r2 = p.add_run('     工艺设计类     方案设计类     作品设计类')
    r2.font.name = 'Times New Roman'
    r2._r.get_or_add_rPr().append(parse_xml(f'<w:rFonts {nsdecls("w")} w:eastAsia="宋体"/>'))
    r2.font.size = Pt(12)
    r3 = p.add_run()
    r3.add_break()
    r4 = p.add_run('作品展示类     其他')
    r4.font.name = 'Times New Roman'
    r4._r.get_or_add_rPr().append(parse_xml(f'<w:rFonts {nsdecls("w")} w:eastAsia="宋体"/>'))
    r4.font.size = Pt(12)
    print('  ✓ 选题类型：已加粗标记「产品设计类」')

    tmp = ASG + '.tmp'
    doc.save(tmp)
    os.replace(tmp, ASG)
    print(f'  ✓ 已保存: {ASG}')

def fill_outcome():
    doc = docx.Document(OUT)
    print('【成果说明书】填写封面与承诺书')
    t0 = doc.tables[0]

    # 封面：R3 二级学院 / R4 专业名称 / R5 班级名称 / R6 姓名 / R7 学号 / R8 指导教师
    mapping = {
        3: COLLEGE,
        4: MAJOR,
        5: CLASS_NAME,
        6: NAME,
        7: STU_ID,
        8: ADVISORS,
    }
    for r_idx, val in mapping.items():
        label = t0.rows[r_idx].cells[0].text.strip()
        set_cell_text(t0.rows[r_idx].cells[1], val, None, True)  # 宋体继承字号 + 加粗（与既有"选题名称"值一致）
        print(f'  ✓ {label} = {val}')

    # 承诺书日期（学生签名日期 + 指导教师审核日期）
    t1 = doc.tables[1]
    date_cells_filled = 0
    for r_idx in (2, 6):
        row = t1.rows[r_idx]
        # 日期在行末单元格
        cell = row.cells[-1]
        if '2026年4月' in cell.text:
            set_cell_text(cell, COMMIT_DATE, None, False)
            date_cells_filled += 1
    print(f'  ✓ 承诺书日期已填写: {COMMIT_DATE}（{date_cells_filled} 处）')

    tmp = OUT + '.tmp'
    doc.save(tmp)
    os.replace(tmp, OUT)
    print(f'  ✓ 已保存: {OUT}')

if __name__ == '__main__':
    fill_assignment()
    print()
    fill_outcome()
    print()
    print('个人信息填写完成')
