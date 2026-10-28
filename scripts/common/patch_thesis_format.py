#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OAISS CHAIN - 毕业设计成果说明书：模板精确排版对齐补丁

将重建后的全部段落对齐官方模板的精确直接格式（原模板段落级 pPr 参数）：
- Heading 1       : spacing(0/0, 360 auto) + ind(firstLine 480) + jc both
- 毕设二级标题     : spacing(280/160) + ind(firstLine 0) + jc left
- 毕设三级标题     : spacing(280/160) + ind(firstLine 0) + jc left
- 毕设图表名称     : spacing(120/80) + ind(firstLine 0) + jc center
- 毕设正文         : spacing(0/0, 360 auto) + ind(firstLine 480) + jc both
- toc 1/2/3        : spacing(280/160) + ind(firstLine 0) + jc left
- 参考文献条目     : Normal + 悬挂缩进(left 480, hanging 200) + 360 auto + both

另补全“附  录”（Heading 1）与核心数据表建表脚本（真实 Flyway DDL）。
"""

import os
import re
import docx
from docx.shared import Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import parse_xml
from docx.oxml.ns import qn, nsdecls

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '../..'))
DOCX_PATH = os.path.join(ROOT, 'docs/软件学院2026届学生毕业设计成果_OAISS_CHAIN双碳链动系统(企软方向).docx')

JC_MAP = {
    'both': WD_ALIGN_PARAGRAPH.JUSTIFY,
    'left': WD_ALIGN_PARAGRAPH.LEFT,
    'center': WD_ALIGN_PARAGRAPH.CENTER,
}

# 模板段落级直接格式参数（单位：twips，1pt = 20 twips）
PROFILES = {
    'Heading 1':     dict(before=0,   after=0,   line=360, rule='auto', first_line=480, jc='both'),
    '毕设二级标题':   dict(before=280, after=160, line=None, rule=None, first_line=0,   jc='left'),
    '毕设三级标题':   dict(before=280, after=160, line=None, rule=None, first_line=0,   jc='left'),
    '毕设图表名称':   dict(before=120, after=80,  line=None, rule=None, first_line=0,   jc='center'),
    '毕设正文':       dict(before=0,   after=0,   line=360, rule='auto', first_line=480, jc='both'),
    'toc 1':         dict(before=280, after=160, line=None, rule=None, first_line=0,   jc='left'),
    'toc 2':         dict(before=280, after=160, line=None, rule=None, first_line=0,   jc='left'),
    'toc 3':         dict(before=280, after=160, line=None, rule=None, first_line=0,   jc='left'),
}

def apply_direct_format(p, prof, replace_ind=True):
    """按模板参数写入段落级直接格式（spacing/ind/jc）"""
    pPr = p._p.get_or_add_pPr()

    # 清理旧的 spacing / ind / jc
    for tag in ('w:spacing', 'w:ind', 'w:jc'):
        for el in pPr.findall(qn(tag)):
            pPr.remove(el)

    # spacing
    s_attrs = f'w:before="{prof["before"]}" w:after="{prof["after"]}"'
    if prof.get('line'):
        s_attrs += f' w:line="{prof["line"]}" w:lineRule="{prof["rule"]}"'
    pPr.append(parse_xml(f'<w:spacing {nsdecls("w")} {s_attrs}/>'))

    # ind
    if replace_ind:
        if prof.get('first_line'):
            pPr.append(parse_xml(f'<w:ind {nsdecls("w")} w:firstLine="{prof["first_line"]}"/>'))
        else:
            pPr.append(parse_xml(f'<w:ind {nsdecls("w")} w:firstLine="0"/>'))

    # jc
    pPr.append(parse_xml(f'<w:jc {nsdecls("w")} w:val="{prof["jc"]}"/>'))

def set_run_font(run, size=12, bold=False, cn_font='SimSun', mono=False):
    run.font.name = 'Consolas' if mono else 'Times New Roman'
    rPr = run._r.get_or_add_rPr()
    rFonts = rPr.find(qn('w:rFonts'))
    if rFonts is None:
        rFonts = parse_xml(f'<w:rFonts {nsdecls("w")}/>')
        rPr.append(rFonts)
    rFonts.set(qn('w:eastAsia'), cn_font)
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.color.rgb = RGBColor(0, 0, 0)

def patch_paragraphs(doc):
    patched = {}
    for p in doc.paragraphs:
        st = p.style.name
        if st in PROFILES:
            apply_direct_format(p, PROFILES[st])
            patched[st] = patched.get(st, 0) + 1
    return patched

def patch_references(doc):
    """将参考文献条目改为 Normal + 悬挂缩进格式（对齐原模板）"""
    count = 0
    in_refs = False
    for p in doc.paragraphs:
        if p.style.name == 'Heading 1' and '参考文献' in p.text:
            in_refs = True
            continue
        if in_refs:
            if p.style.name == 'Heading 1':
                break
            if not p.text.strip():
                continue
            # 参照原模板：Normal 样式 + 悬挂缩进
            p.style = doc.styles['Normal']
            pPr = p._p.get_or_add_pPr()
            for tag in ('w:spacing', 'w:ind', 'w:jc'):
                for el in pPr.findall(qn(tag)):
                    pPr.remove(el)
            pPr.append(parse_xml(f'<w:spacing {nsdecls("w")} w:line="360" w:lineRule="auto" w:before="0" w:after="0"/>'))
            pPr.append(parse_xml(f'<w:ind {nsdecls("w")} w:left="480" w:hanging="480" w:firstLine="480"/>'))
            pPr.append(parse_xml(f'<w:jc {nsdecls("w")} w:val="both"/>'))
            for r in p.runs:
                set_run_font(r, 10.5, False)
            count += 1
    return count

DDL_SQL = """CREATE TABLE `carbon_report` (
    `id`                 BIGINT        NOT NULL AUTO_INCREMENT,
    `created_at`         DATETIME      NOT NULL,
    `updated_at`         DATETIME      NOT NULL,
    `is_deleted`         TINYINT(1)    NOT NULL DEFAULT 0,
    `report_no`          VARCHAR(50)   NOT NULL,
    `enterprise_id`      BIGINT        NOT NULL,
    `submitter_id`       BIGINT        NOT NULL,
    `accounting_period`  VARCHAR(20)   NOT NULL,
    `title`              VARCHAR(200)  NOT NULL,
    `report_type`        INT           NOT NULL,
    `emission_data`      TEXT          NOT NULL,
    `total_emission`     DECIMAL(15,4) NULL,
    `scope1_emission`    DECIMAL(15,4) NULL,
    `scope2_emission`    DECIMAL(15,4) NULL,
    `scope3_emission`    DECIMAL(15,4) NULL,
    `calculation_method` VARCHAR(100)  NULL,
    `status`             INT           NOT NULL DEFAULT 0,
    `reviewer_id`        BIGINT        NULL,
    `review_comment`     TEXT          NULL,
    `reviewed_at`        DATETIME      NULL,
    `signature_data`     TEXT          NULL,
    `blockchain_tx_hash` VARCHAR(255)  NULL,
    `on_chain_at`        DATETIME      NULL,
    `attachments`        TEXT          NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_carbon_report_report_no` (`report_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;"""

def add_appendix(doc):
    """补全附录章节：真实 Flyway 核心数据表建表脚本"""
    # 检查是否已存在附录
    for p in doc.paragraphs:
        if p.style.name == 'Heading 1' and '附' in p.text and '录' in p.text:
            print("  附录已存在，跳过")
            return

    doc.add_page_break()
    h1 = doc.add_paragraph(style='Heading 1')
    r = h1.add_run('附  录')
    set_run_font(r, 16, True, 'SimHei')
    apply_direct_format(h1, PROFILES['Heading 1'])

    h2 = doc.add_paragraph(style='毕设二级标题')
    r2 = h2.add_run('附录A  核心数据表建表脚本（carbon_report）')
    set_run_font(r2, 14, True, 'SimHei')
    apply_direct_format(h2, PROFILES['毕设二级标题'])

    body = doc.add_paragraph(style='毕设正文')
    rb = body.add_run('本附录给出系统核心业务表 carbon_report（碳核算报告表）的 MySQL 8.0 建表脚本，'
                      '该脚本已通过 Flyway V1 基线迁移在系统中实际执行验证，完整包含主键、唯一约束、'
                      '审计字段与区块链存证字段的物理定义。')
    set_run_font(rb, 12, False)
    apply_direct_format(body, PROFILES['毕设正文'])

    # 代码块（等宽字体，无首行缩进）
    for line in DDL_SQL.split('\n'):
        p = doc.add_paragraph()
        pPr = p._p.get_or_add_pPr()
        pPr.append(parse_xml(f'<w:spacing {nsdecls("w")} w:before="0" w:after="0" w:line="240" w:lineRule="auto"/>'))
        pPr.append(parse_xml(f'<w:ind {nsdecls("w")} w:firstLine="0" w:left="240"/>'))
        pPr.append(parse_xml(f'<w:jc {nsdecls("w")} w:val="left"/>'))
        r = p.add_run(line if line.strip() else ' ')
        set_run_font(r, 9, False, mono=True)
    print("  附录章节补全完成（含真实 Flyway DDL）")

def main():
    print("=== 毕业设计成果说明书：模板精确排版对齐 ===")
    source_p = DOCX_PATH + '.tmp' if os.path.exists(DOCX_PATH + '.tmp') else DOCX_PATH
    doc = docx.Document(source_p)

    patched = patch_paragraphs(doc)
    print("  段落直接格式对齐统计：")
    for k, v in patched.items():
        print(f"    {k}: {v} 段")

    ref_count = patch_references(doc)
    print(f"  参考文献条目格式对齐: {ref_count} 条")

    add_appendix(doc)

    tmp = DOCX_PATH + '.tmp'
    doc.save(tmp)
    try:
        os.replace(tmp, DOCX_PATH)
        print(f"排版对齐完成: {DOCX_PATH}")
    except PermissionError:
        print(f"! 目标文件正被 WPS 打开，排版对齐成果已暂存: {tmp}")

if __name__ == '__main__':
    main()
