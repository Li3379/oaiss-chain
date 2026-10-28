#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OAISS CHAIN - 毕业设计成果说明书（企软方向）全量重建脚本

彻底清除模板残留（党建/电商内容），按软件学院官方模板样式全量重建四大章节：
- 第1章 设计任务（背景、范围、风险、任务分配、所需资源）
- 第2章 设计思路与技术方案（业务流程图、用例图、用例分析、技术方案）
- 第3章 设计内容（过程）与说明（数据库、界面、类、顺序图、API、测试）
- 第4章 设计总结（部署手册、操作手册、致谢）
- 参考文献

样式严格沿用官方模板：Heading 1 / 毕设二级标题 / 毕设三级标题 / 毕设正文 / 毕设图表名称
图表题注规范：表X.Y 位于表格上方；图X.Y 位于图片下方
"""

import os
import re
import docx
from docx.shared import Pt, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.text.paragraph import Paragraph
from docx.oxml import parse_xml
from docx.oxml.ns import qn, nsdecls

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '../..'))
DOCX_PATH = os.path.join(ROOT, 'docs/软件学院2026届学生毕业设计成果_OAISS_CHAIN双碳链动系统(企软方向).docx')
ARCH_DIR = os.path.join(ROOT, 'docs/assets/architecture')
SHOT_DIR = os.path.join(ROOT, 'reports')

# ============================================================
# 辅助函数
# ============================================================

def set_run_font(run, size=12, bold=False, cn_font='SimSun'):
    run.font.name = 'Times New Roman'
    rPr = run._r.get_or_add_rPr()
    rFonts = rPr.find(qn('w:rFonts'))
    if rFonts is None:
        rFonts = parse_xml(f'<w:rFonts {nsdecls("w")}/>')
        rPr.append(rFonts)
    rFonts.set(qn('w:eastAsia'), cn_font)
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.color.rgb = RGBColor(0, 0, 0)

def add_h1(doc, text):
    p = doc.add_paragraph(style='Heading 1')
    p.paragraph_format.first_line_indent = Pt(0)
    r = p.add_run(text)
    set_run_font(r, 16, True, 'SimHei')

def add_h2(doc, text):
    p = doc.add_paragraph(style='毕设二级标题')
    r = p.add_run(text)
    set_run_font(r, 14, True, 'SimHei')

def add_h3(doc, text):
    p = doc.add_paragraph(style='毕设三级标题')
    r = p.add_run(text)
    set_run_font(r, 12, True, 'SimHei')

def add_body(doc, text):
    """支持多段落文本，按 \n 分段"""
    for seg in text.split('\n'):
        seg = seg.strip()
        if not seg:
            continue
        p = doc.add_paragraph(style='毕设正文')
        r = p.add_run(seg)
        set_run_font(r, 12, False)

def add_caption(doc, text):
    p = doc.add_paragraph(style='毕设图表名称')
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.first_line_indent = Pt(0)
    r = p.add_run(text)
    set_run_font(r, 9, True)

def add_image(doc, img_path, caption, width_cm=15.0):
    if not os.path.exists(img_path):
        print(f"  [警告] 图片不存在: {img_path}")
        return
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.first_line_indent = Pt(0)
    p.paragraph_format.line_spacing = 1.0  # 单倍行距（auto）：避免固定行距裁切内嵌图片
    p.paragraph_format.space_before = Pt(6)
    p.paragraph_format.space_after = Pt(2)
    r = p.add_run()
    r.add_picture(img_path, width=Cm(width_cm))
    add_caption(doc, caption)

def _set_table_borders(tbl):
    tblPr = tbl._tbl.tblPr
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

def _set_table_widths(tbl, col_widths_cm):
    """精确设置表格列宽：固定布局 + tblGrid + 各单元格 tcW（1cm = 567 twips）"""
    TW = 567
    tbl_el = tbl._tbl
    tblPr = tbl_el.tblPr

    # tblLayout = fixed（置于 tblBorders 之后）
    for el in tblPr.findall(qn('w:tblLayout')):
        tblPr.remove(el)
    tblPr.append(parse_xml(f'<w:tblLayout {nsdecls("w")} w:type="fixed"/>'))

    # tblW 总宽（置于 tblBorders 之前，符合 schema 顺序）
    total = int(sum(col_widths_cm) * TW)
    for el in tblPr.findall(qn('w:tblW')):
        tblPr.remove(el)
    tblW = parse_xml(f'<w:tblW {nsdecls("w")} w:w="{total}" w:type="dxa"/>')
    borders = tblPr.find(qn('w:tblBorders'))
    if borders is not None:
        borders.addprevious(tblW)
    else:
        tblPr.append(tblW)

    # tblGrid（位于 tblPr 之后、行之前）
    grid = tbl_el.find(qn('w:tblGrid'))
    if grid is not None:
        tbl_el.remove(grid)
    cols = ''.join(f'<w:gridCol {nsdecls("w")} w:w="{int(w * TW)}"/>' for w in col_widths_cm)
    new_grid = parse_xml(f'<w:tblGrid {nsdecls("w")}>{cols}</w:tblGrid>')
    tblPr.addnext(new_grid)

    # 各单元格 tcW
    for row in tbl.rows:
        for c_idx, cell in enumerate(row.cells):
            if c_idx >= len(col_widths_cm):
                break
            tcPr = cell._tc.get_or_add_tcPr()
            for el in tcPr.findall(qn('w:tcW')):
                tcPr.remove(el)
            tcPr.append(parse_xml(f'<w:tcW {nsdecls("w")} w:w="{int(col_widths_cm[c_idx] * TW)}" w:type="dxa"/>'))

def add_table(doc, caption, headers, rows, col_widths=None):
    """添加标准网格表格：表题位于表格上方居中"""
    if caption:
        add_caption(doc, caption)
    tbl = doc.add_table(rows=len(rows) + 1, cols=len(headers))
    tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
    _set_table_borders(tbl)

    # 表头
    for c_idx, h in enumerate(headers):
        cell = tbl.rows[0].cells[c_idx]
        cell.text = ''
        p = cell.paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.first_line_indent = Pt(0)
        p.paragraph_format.space_before = Pt(1)
        p.paragraph_format.space_after = Pt(1)
        r = p.add_run(h)
        set_run_font(r, 9, True)
        tcPr = cell._tc.get_or_add_tcPr()
        tcPr.append(parse_xml(r'<w:shd %s w:val="clear" w:color="auto" w:fill="D9D9D9"/>' % nsdecls('w')))
        tcPr.append(parse_xml(r'<w:vAlign %s w:val="center"/>' % nsdecls('w')))

    # 数据行
    for r_idx, row_data in enumerate(rows):
        for c_idx, val in enumerate(row_data):
            if c_idx >= len(headers):
                break
            cell = tbl.rows[r_idx + 1].cells[c_idx]
            cell.text = ''
            p = cell.paragraphs[0]
            p.paragraph_format.first_line_indent = Pt(0)
            p.paragraph_format.space_before = Pt(1)
            p.paragraph_format.space_after = Pt(1)
            p.alignment = WD_ALIGN_PARAGRAPH.LEFT if len(str(val)) > 14 else WD_ALIGN_PARAGRAPH.CENTER
            r = p.add_run(str(val))
            set_run_font(r, 9, False)
            cell._tc.get_or_add_tcPr().append(parse_xml(r'<w:vAlign %s w:val="center"/>' % nsdecls('w')))

    if col_widths:
        _set_table_widths(tbl, col_widths)

def add_page_break(doc):
    doc.add_page_break()

# 表格序号与图序号计数器
_table_no = {}
_fig_no = {}

def next_table_no(chap):
    _table_no[chap] = _table_no.get(chap, 0) + 1
    return f"表{chap}.{_table_no[chap]}"

def next_fig_no(chap):
    _fig_no[chap] = _fig_no.get(chap, 0) + 1
    return f"图{chap}.{_fig_no[chap]}"

def T(doc, chap, name, headers, rows, col_widths=None):
    add_table(doc, f"{next_table_no(chap)} {name}", headers, rows, col_widths)

def F(doc, chap, name, img_name, width_cm=15.0):
    path = os.path.join(ARCH_DIR, img_name)
    add_image(doc, path, f"{next_fig_no(chap)} {name}", width_cm)

def SHOT(doc, chap, name, img_name, width_cm=14.5):
    path = os.path.join(SHOT_DIR, img_name)
    add_image(doc, path, f"{next_fig_no(chap)} {name}", width_cm)

# ---------- 模板专用结构表格 ----------

def _fill_cell(cell, text, size=9, bold=False, align='left'):
    cell.text = ''
    p = cell.paragraphs[0]
    p.paragraph_format.first_line_indent = Pt(0)
    p.paragraph_format.space_before = Pt(1)
    p.paragraph_format.space_after = Pt(1)
    p.alignment = {'left': WD_ALIGN_PARAGRAPH.LEFT,
                   'center': WD_ALIGN_PARAGRAPH.CENTER}[align]
    for i, part in enumerate(str(text).split('\n')):
        if i > 0:
            p.add_run().add_break()
        r = p.add_run(part)
        set_run_font(r, size, bold)

def add_usecase_table(doc, chap, name, data):
    """用例描述表（模板结构：2列 × 11行，内容/说明）"""
    add_caption(doc, f"{next_table_no(chap)} {name}")
    rows = [
        ('用例编号', data['no']),
        ('用例名称', data['name']),
        ('用例说明', data['desc']),
        ('参与者', data['actors']),
        ('前置条件', data['pre']),
        ('后置条件', data['post']),
        ('基本路径', data['basic']),
        ('扩展路径', data['ext']),
        ('异常事件流', data['exc']),
        ('补充说明', data.get('note', '')),
    ]
    tbl = doc.add_table(rows=len(rows) + 1, cols=2)
    tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
    _set_table_borders(tbl)
    for c_idx, h in enumerate(['内  容', '说  明']):
        cell = tbl.rows[0].cells[c_idx]
        _fill_cell(cell, h, 9, True, 'center')
        tcPr = cell._tc.get_or_add_tcPr()
        tcPr.append(parse_xml(r'<w:shd %s w:val="clear" w:color="auto" w:fill="D9D9D9"/>' % nsdecls('w')))
    for r_idx, (k, v) in enumerate(rows):
        _fill_cell(tbl.rows[r_idx + 1].cells[0], k, 9, True)
        _fill_cell(tbl.rows[r_idx + 1].cells[1], v, 9, False)
    _set_table_widths(tbl, [3.4, 13.6])

def add_data_table(doc, chap, tbl_key, tbl_name, tbl_desc, field_rows):
    """数据字典表（模板结构：首行合并"数据表名/数据表说明" + 6列表头 + 字段行）"""
    add_caption(doc, f"{next_table_no(chap)} {tbl_name}表")
    headers = ['字段名', '字段说明', '类型', '长度', '是否为空', '备注']
    tbl = doc.add_table(rows=len(field_rows) + 2, cols=6)
    tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
    _set_table_borders(tbl)

    # 首行：数据表名 / 数据表说明（横向合并）
    merged = tbl.rows[0].cells[0].merge(tbl.rows[0].cells[5])
    _fill_cell(merged, f'数据表名：{tbl_key}\n数据表说明：{tbl_desc}', 9, True)

    # 表头
    for c_idx, h in enumerate(headers):
        cell = tbl.rows[1].cells[c_idx]
        _fill_cell(cell, h, 9, True, 'center')
        cell._tc.get_or_add_tcPr().append(
            parse_xml(r'<w:shd %s w:val="clear" w:color="auto" w:fill="D9D9D9"/>' % nsdecls('w')))

    # 字段行
    for r_idx, frow in enumerate(field_rows):
        for c_idx, val in enumerate(frow):
            if c_idx >= 6:
                break
            align = 'center' if c_idx in (2, 3, 4) or len(str(val)) <= 8 else 'left'
            _fill_cell(tbl.rows[r_idx + 2].cells[c_idx], val, 9, False, align)
    _set_table_widths(tbl, [3.2, 3.6, 2.0, 1.6, 1.8, 4.8])

def add_api_table(doc, chap, caption_name, api_name, api_url, params, rets, desc):
    """接口描述表（模板结构：标签列 + 字段名称/类型/描述 列）"""
    add_caption(doc, f"{next_table_no(chap)} {caption_name}")
    rows = [('接口名称：', api_name, '', '')]
    rows.append(('接口地址：', api_url, '', ''))
    rows.append(('接口参数：', '字段名称', '类型', '描述'))
    for p_name, p_type, p_desc in params:
        rows.append(('接口参数：', p_name, p_type, p_desc))
    rows.append(('返回值：', rets[0], '', ''))
    rows.append(('返回值：', '字段定义', '类型', '描述'))
    for r_name, r_type, r_desc in rets[1]:
        rows.append(('返回值：', r_name, r_type, r_desc))
    rows.append(('接口描述：', desc, '', ''))

    tbl = doc.add_table(rows=len(rows), cols=4)
    tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
    _set_table_borders(tbl)
    for r_idx, r in enumerate(rows):
        is_header = r[1] in ('字段名称', '字段定义')
        for c_idx, val in enumerate(r):
            align = 'center' if (r_idx < 3 or is_header) and c_idx >= 1 else 'left'
            _fill_cell(tbl.rows[r_idx].cells[c_idx], val, 9, is_header, align)
            if is_header and c_idx >= 1:
                tbl.rows[r_idx].cells[c_idx]._tc.get_or_add_tcPr().append(
                    parse_xml(r'<w:shd %s w:val="clear" w:color="auto" w:fill="D9D9D9"/>' % nsdecls('w')))
    for row in tbl.rows:
        for c_idx, w in enumerate([2.6, 5.0, 2.4, 7.0]):
            row.cells[c_idx].width = Cm(w)

def add_label_table(doc, chap, caption_name, rows_spec, col_widths=None):
    """标签式表格（模板接口表结构）：每行为 (标签, 内容1, 内容2, 内容3) 四元组，
    内容1 为 '字段名称' / '字段定义' 时该行作为灰底子表头。"""
    add_caption(doc, f"{next_table_no(chap)} {caption_name}")
    tbl = doc.add_table(rows=len(rows_spec), cols=4)
    tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
    _set_table_borders(tbl)
    for r_idx, r in enumerate(rows_spec):
        is_header = str(r[1]) in ('字段名称', '字段定义')
        for c_idx, val in enumerate(r):
            align = 'center' if (is_header or (c_idx >= 1 and not str(r[2]).strip())) else 'left'
            _fill_cell(tbl.rows[r_idx].cells[c_idx], val, 9, is_header, align)
            if is_header and c_idx >= 1:
                tbl.rows[r_idx].cells[c_idx]._tc.get_or_add_tcPr().append(
                    parse_xml(r'<w:shd %s w:val="clear" w:color="auto" w:fill="D9D9D9"/>' % nsdecls('w')))
    _set_table_widths(tbl, col_widths or [2.6, 5.0, 2.4, 7.0])

def add_merged_meta_table(doc, chap, caption_name, meta_rows, header, data_rows, col_widths):
    """带元数据头（前若干行标签+合并值）的测试表格：如测试计划表/测试报告表"""
    add_caption(doc, f"{next_table_no(chap)} {caption_name}")
    n_cols = len(header)
    tbl = doc.add_table(rows=len(meta_rows) + 1 + len(data_rows), cols=n_cols)
    tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
    _set_table_borders(tbl)

    r_idx = 0
    for label, value in meta_rows:
        row = tbl.rows[r_idx]
        merged = row.cells[1]
        for c in row.cells[2:]:
            merged = merged.merge(c)
        _fill_cell(row.cells[0], label, 9, True)
        _fill_cell(tbl.rows[r_idx].cells[1], value, 9, False)
        r_idx += 1

    for c_idx, h in enumerate(header):
        cell = tbl.rows[r_idx].cells[c_idx]
        _fill_cell(cell, h, 9, True, 'center')
        cell._tc.get_or_add_tcPr().append(
            parse_xml(r'<w:shd %s w:val="clear" w:color="auto" w:fill="D9D9D9"/>' % nsdecls('w')))
    r_idx += 1

    for drow in data_rows:
        for c_idx, val in enumerate(drow):
            if c_idx >= n_cols:
                break
            align = 'left' if len(str(val)) > 12 else 'center'
            _fill_cell(tbl.rows[r_idx].cells[c_idx], val, 9, False, align)
        r_idx += 1

    if col_widths:
        _set_table_widths(tbl, col_widths)

# ============================================================
# 第 1 章 设计任务
# ============================================================

def build_chapter_1(doc):
    add_h1(doc, '1  设计任务')

    add_h2(doc, '1.1 项目背景')
    add_body(doc, (
        '全球气候变化已成为人类社会发展面临的重大挑战。我国政府庄严承诺力争2030年前实现碳达峰、2060年前实现碳中和，'
        '这一“双碳”战略目标已被纳入国家生态文明建设整体布局。2024年国务院《加快构建碳排放双控制度体系工作方案》'
        '与全国碳排放权交易市场的持续扩围，对重点排放单位的温室气体核算能力、数据公信力与履约交易效率提出了前所未有的严格要求。'
    ))
    add_body(doc, (
        '然而，通过对火力发电、电网输配电等重点控排行业的实地调研与技术分析，当前企业碳管理实践中普遍存在四大核心痛点：'
    ))
    add_body(doc, (
        '（1）核算标准割裂、人工误差高。火力发电企业碳核算涉及化石燃料消耗量、低位发热量、单位热值含碳量、碳氧化率等25项工艺参数，'
        '电网企业涉及区域电网排放因子、输配电损失电量等9项参数。传统的手工填报与电子表格计算方式缺乏参数级强校验机制，'
        '极易出现单位换算错误、参数遗漏与公式误用，核算结果难以通过第三方核查。'
    ))
    add_body(doc, (
        '（2）数据缺乏公信力、篡改难追溯。企业自行填报的排放数据与减排凭证以中心化数据库存储，存在事后篡改的隐患；'
        '第三方核查机构复核周期长、成本高，多方主体之间缺乏低成本的可信数据共享与核验机制。'
    ))
    add_body(doc, (
        '（3）履约撮合效率低、定价机制不透明。碳配额交易以线下双边协商为主，买卖双方信息不对称，'
        '缺乏公平透明的集合竞价与连续撮合机制，配额流动性不足，价格发现功能弱化。'
    ))
    add_body(doc, (
        '（4）监管滞后、风险预警能力弱。监管部门难以实时掌握区域碳资产全貌，'
        '对企业的异常排放模式、履约风险与市场非理性波动缺乏量化的预测预警手段。'
    ))
    add_body(doc, (
        '针对上述痛点，本课题综合运用企业级微服务架构、Hyperledger Fabric 联盟区块链存证、'
        '数字签名防伪以及机器学习时间序列预测等前沿技术，设计并实现“双碳链动系统（OAISS CHAIN）”——'
        '一套覆盖“报告填报—国标核算—多级审核—签名上链—配额交易—智能监管”全生命周期的可信碳核算与交易平台，'
        '为国家双碳战略在重点行业的数字化落地提供可复用的技术方案。'
    ))

    add_h2(doc, '1.2 项目范围')
    add_body(doc, (
        '依据控排企业与监管机构的实际业务需求，OAISS CHAIN 双碳链动系统围绕四类业务参与者'
        '（企业主体 ENTERPRISE、数据审核员 REVIEWER、第三方监管机构 THIRD_PARTY、系统管理员 ADMIN），'
        '规划并实现了以下四大核心功能域。'
    ))

    add_h3(doc, '1.2.1 用户认证与四角色权限管理模块')
    add_body(doc, (
        '本模块是系统安全的第一道防线，实现四类业务主体的身份鉴别与细粒度访问控制。'
    ))
    add_body(doc, (
        '（1）双令牌认证机制：采用无状态 JWT 认证体系，访问令牌（Access Token，1小时有效期）承载用户身份与角色声明，'
        '刷新令牌（Refresh Token，7天有效期）依托 Redis 存储实现安全续期与主动吊销，杜绝令牌泄露长期风险。'
    ))
    add_body(doc, (
        '（2）RBAC 四角色权限：基于方法级 @PreAuthorize 注解实施强制鉴权，企业仅可操作本企业数据（数据隔离），'
        '审核员仅可访问授权辖区内的待审报告，第三方监管拥有穿透式只读监管权限，管理员掌控准入审核与系统配置。'
    ))
    add_body(doc, (
        '（3）登录安全防护：集成图形验证码防机器人爆破、登录失败计数熔断与 IP 频次限流（@RateLimit），'
        '并全程记录敏感操作审计日志（@AuditLog）。'
    ))

    add_h3(doc, '1.2.2 国标碳核算与公式计算器模块')
    add_body(doc, (
        '本模块严格依据 GB/T 32150《工业企业温室气体排放核算和报告通则》与生态环境部发电设施核算指南，'
        '实现两大行业核算引擎。'
    ))
    add_body(doc, (
        '（1）火力发电企业核算引擎（25项参数）：覆盖化石燃料燃烧排放（燃料消耗量、低位发热量、单位热值含碳量、碳氧化率）、'
        '脱硫过程碳酸盐分解排放（脱硫剂消耗量、碳酸钙含量、转化率）、净购入电力隐含排放（净购电量、区域电网排放因子）'
        '三大排放源，支持多燃料品种加权计算与公式分步推导展示。'
    ))
    add_body(doc, (
        '（2）电网输配电企业核算引擎（9项参数）：纳管输配电损失电量、供电量、区域电网综合排放因子等参数，'
        '自动完成单位换算、六氟化硫逸散排放折算与结果舍入规范。'
    ))
    add_body(doc, (
        '（3）双向校验机制：前端提供实时试算沙箱，用户输入参数即时反馈推导过程与中间结果；'
        '后端独立执行防篡改二次核算，两次结果比对一致方可入库，确保核算结果可信。'
    ))

    add_h3(doc, '1.2.3 报告审查驳回与区块链存证模块')
    add_body(doc, (
        '本模块构建“企业填报—审核核验—监管确认—链上固化”的四方协同闭环。'
    ))
    add_body(doc, (
        '（1）报告状态机与工作台：碳核算报告严格遵循 DRAFT（草稿）→ PENDING_REVIEW（待审）→ REVIEWED（已审）'
        '→ VERIFIED（已核查）／REJECTED（已驳回）→ ON_CHAIN（已存证）的状态机流转，'
        '审核员工作台支持报告认领锁定、附件在线查阅与历史版本比对。'
    ))
    add_body(doc, (
        '（2）结构化驳回流水线：审核员驳回时必须选择驳回大类（参数异常／凭证缺失／公式有误／数据矛盾）'
        '并填写具体整改意见，企业端实时接收站内信通知，在原始表单上高亮定位问题参数并补正重提，形成完整整改记录链。'
    ))
    add_body(doc, (
        '（3）数字签名防抵赖：报告提交前由企业 RSA-2048 私钥生成 SHA256withRSA 数字签名，私钥在存储与传输全程采用'
        'AES-256-GCM 信封加密保护；审核员与第三方监管机构凭企业公钥独立验签，验签失败的报告自动拦截，禁止进入后续流程。'
    ))
    add_body(doc, (
        '（4）Fabric 联盟链存证：核查通过的报告哈希（SHA-256）与关键元数据经 Hyperledger Fabric 2.5 链码两阶段背书提交上链，'
        '生成不可篡改的区块高度与交易哈希（TxHash），任何主体均可通过交易 ID 穿透溯源验证数据完整性。'
    ))

    add_h3(doc, '1.2.4 双向竞价拍卖与AI智能监管大盘模块')
    add_body(doc, (
        '本模块实现碳配额的市场化流转与智能化风险监管。'
    ))
    add_body(doc, (
        '（1）连续双向拍卖撮合引擎：企业提交买（Bid）／卖（Ask）挂单时自动冻结对应碳币资金或碳配额，'
        '撮合引擎严格遵循“价格优先、时间优先”原则维护双端优先级队列，当最高买价不低于最低卖价时自动成交，'
        '成交价取买卖中间价，实现零人工干预的连续竞价出清。'
    ))
    add_body(doc, (
        '（2）DVP 原子结算：撮合成交瞬间以数据库强事务完成“碳配额过户＋碳币资金交割＋溢价返还”的原子性清算'
        '（Delivery versus Payment），并通过乐观锁与 Redis 分布式锁双重保障杜绝超卖与重复结算。'
    ))
    add_body(doc, (
        '（3）P2P 协商交易：在连续竞价之外提供点对点双边议价通道，支持企业定向发起交易意向、对方确认后签约履约。'
    ))
    add_body(doc, (
        '（4）AI 智能监管大盘：基于 Meta Prophet 时间序列模型提供未来30／90／180天的碳配额价格预测与95%置信通道，'
        '基于 XGBoost 输出价格驱动因子归因（XAI），基于 Isolation Forest 检测企业排放异常模式并生成六维合规偏离雷达图；'
        '第三方监管机构可通过大屏实时掌握区域碳资产全景与风险态势。'
    ))

    add_h2(doc, '1.3 项目风险分析')
    add_body(doc, (
        '本系统涉及国标核算、区块链、高并发交易与机器学习多个技术域，在设计与实施过程中识别出以下主要风险，'
        '并制定了对应的规避策略。'
    ))
    T(doc, 1, '项目风险分析表',
      ['序号', '风险', '概率（%）', '严重性（%）', '措施'],
      [
          ['1', '国标核算公式参数复杂，实现偏差导致计算结果错误', '40', '80',
           '将核算引擎解耦为独立服务，编写参数化单元测试全覆盖25项/9项参数，与国标附录样例逐年比对校验'],
          ['2', '区块链网络吞吐与延迟影响业务响应速度', '30', '60',
           '采用事务外发箱（Outbox）模式异步上链，业务主流程与存证解耦，链上失败自动重试补偿'],
          ['3', '高并发撮合场景下配额超卖或重复结算', '35', '85',
           '基于 Redis 分布式锁与数据库乐观锁双重防护，撮合清算全过程强事务包裹，保证账实相符'],
          ['4', '机器学习微服务推理时延抖动影响使用体验', '20', '55',
           '算法微服务独立部署，Resilience4j 熔断降级保护，预测结果 Redis 缓存与异步预加载'],
          ['5', 'RSA 私钥托管环节存在泄露风险', '15', '90',
           '私钥全程 AES-256-GCM 信封加密，主密钥经环境变量隔离注入，数据库与日志严禁落盘明文'],
      ],
      col_widths=[1.2, 5.4, 1.8, 1.8, 6.8])

    add_h2(doc, '1.4 任务分配')
    add_body(doc, (
        '本毕业设计由个人独立完成全部全栈研发工作，各阶段任务与职责分配如下表所示。'
    ))
    T(doc, 1, '任务分配表',
      ['序号', '任务', '负责人'],
      [
          ['1', '需求分析（行业调研、四角色用例建模、需求规格编写）', '本人'],
          ['2', '页面设计（四角色工作台 UI 与交互原型设计）', '本人'],
          ['3', '数据库设计（24 张业务表物理建模与索引优化）', '本人'],
          ['4', '身份权限与安全模块（JWT 双令牌、RBAC、RSA 信封加密）', '本人'],
          ['5', '碳核算与审核流转模块（25项/9项参数引擎、驳回流水线）', '本人'],
          ['6', '拍卖撮合与链上存证模块（DVP 结算与 Fabric 链码）', '本人'],
          ['7', '算法微服务模块（Prophet 预测、XGBoost 归因、异常检测）', '本人'],
          ['8', '项目整合与测试（前后端联调、120+自动化测试、PPT 答辩）', '本人'],
      ],
      col_widths=[1.4, 10.2, 3.4])

    add_h2(doc, '1.5 项目所需资源')
    add_body(doc, (
        '本系统研发与运行所需的软硬件资源及技术依赖如下表所示。'
    ))
    T(doc, 1, '资源需求表',
      ['序号', '资源', '作用', '占用时间', '可用状态', '获得途径'],
      [
          ['1', 'IntelliJ IDEA 2024.1 / VS Code 1.90', '提供后端、前端、链码与算法开发环境',
           '贯穿整个项目的构造阶段和走查过程中', '可用', '网上下载'],
          ['2', 'Docker 26.0 / Docker Compose 2.27', '提供微服务容器化编排与一键部署环境',
           '贯穿整个项目的构造阶段和走查过程中', '可用', '官网下载'],
          ['3', 'MySQL 8.0 / Redis 7.2 / MinIO', '业务数据持久化、缓存与会话、附件对象存储',
           '贯穿整个项目的构造阶段和走查过程中', '可用', '容器镜像'],
          ['4', 'Hyperledger Fabric 2.5 测试网络', '提供联盟链账本与智能合约链码运行环境',
           '区块链集成与测试期间', '可用', '开源社区'],
          ['5', 'Python 3.11 / Prophet / XGBoost', '碳价预测与异常检测算法模型训练与推理',
           '算法微服务开发期间', '可用', '开源社区'],
          ['6', '相关技术书籍与国标规范', '为项目开发提供 GB/T 32150 等标准查询资料',
           '贯穿整个项目的构造阶段和走查过程中', '可用', '学校提供、自己购买'],
      ],
      col_widths=[1.2, 4.4, 4.2, 3.6, 1.6, 2.0])

# ============================================================
# 第 2 章 设计思路与技术方案
# ============================================================

def build_chapter_2(doc):
    add_page_break(doc)
    add_h1(doc, '2  设计思路与技术方案')

    add_h2(doc, '2.1 系统核心业务流程图')
    add_body(doc, (
        '系统的核心业务围绕“碳核算报告”这一主数据对象展开，从企业填报到链上固化共经历六个关键状态，'
        '由状态机引擎强制约束流转路径，任何越权跳转均被拦截。完整业务流程如下图所示。'
    ))
    F(doc, 2, '核心业务流程图', '03-carbon-lifecycle-state-machine.png')
    add_body(doc, (
        '业务流程说明如下。'
    ))
    add_body(doc, (
        '（1）草稿填报（DRAFT）：企业按行业模板录入核算参数，系统实时执行公式试算并展示中间推导过程，'
        '支持多次暂存与参数复核，此状态下数据仅企业自身可见。'
    ))
    add_body(doc, (
        '（2）签名提交（PENDING_REVIEW）：企业确认参数无误后，调用本地 RSA 私钥对报告规范化 JSON 序列执行 SHA256withRSA 签名，'
        '签名成功方可提交，报告进入审核池并锁定编辑权限。'
    ))
    add_body(doc, (
        '（3）审核核验（REVIEWED／REJECTED）：审核员领单锁定后核验附件凭证与参数逻辑，'
        '核验通过则生成审核意见与联合签名；核验存疑则填写结构化驳回原因，报告退回企业草稿箱重新修改。'
    ))
    add_body(doc, (
        '（4）监管确认与上链（ON_CHAIN）：第三方监管机构对审核结论执行最终合规确认，'
        '通过后由 Outbox 异步任务调用 Fabric 链码完成报告哈希上链，生成区块高度与 TxHash 凭证。'
    ))
    add_body(doc, (
        '（5）入市交易：链上固化的配额自动划入企业可交易余额，企业可发起双向拍卖挂单或 P2P 议价交易，'
        '撮合成交自动触发碳币与配额的原子性划转。'
    ))

    add_h2(doc, '2.2 系统用例图')
    add_body(doc, (
        '依据 UML 2.5 建模规范与官方毕业设计成果标准，系统用例图采用 Enterprise Architect 12 / draw.io 原生工业级规范设计，'
        '配套工程源文件为 docs/assets/architecture/02-system-usecases.drawio（含四个标准视图页，可在 draw.io / EA12 中直接二次编辑）。'
        '为清晰反映多角色协同与各主体的独立权限边界，本节首先给出全域系统用例全景图，随后遵循“一个角色一张图”的标准'
        '分别给出控排企业、数据审核员、第三方监管机构与系统管理员的专属用例图。'
        '各图严格采用标准 UML 关系表示法：参与者与用例之间以实线关联（Association）连接，'
        '用例之间的 «include» 包含与 «extend» 扩展关系统一使用虚线 + 开放箭头表示，'
        '箭头方向严格指向被包含／被扩展的用例，条件性补偿场景（如驳回整改、证书吊销）以 «extend» 单独标出；'
        '每个角色图按业务域组织为四条横向业务链，每条链自左向右完整呈现主用例到支撑用例的层次关系。'
    ))
    F(doc, 2, '系统多角色核心用例全景图', '02-four-roles-collaboration.png')
    add_body(doc, (
        '（1）控排企业（ENTERPRISE）：作为碳排放核算申报与资产交易的主体，企业用户的用例按碳核算填报、报告签名送审、'
        '双向拍卖交易、碳币账户管理四条业务链组织，覆盖碳核算模板填报、录入生产活动台账、国标公式实时推导、提交核算报告送审、'
        '报告数字签名、私钥信封解密、上传原始化验凭证、驳回整改补正、双向拍卖挂单、冻结碳币配额资产、连续撮合出清、'
        'DVP 原子清算交割、碳币账户管理、发起碳币转账、碳信用履约评分查询与奖惩事件明细查阅等核心业务；'
        '所有支撑功能均以 «include» 被包含用例单独建模，其业务边界与完整关系如下图所示。'
    ))
    F(doc, 2, '控排企业用例图', '02-enterprise-usecase-diagram.png')
    add_body(doc, (
        '（2）数据审核员（REVIEWER）：作为碳数据公信力核验的关键主体，审核员用例按工作台登录鉴权、待审报告认领与核验、'
        '企业签名验签与审核决定、结构化驳回与统计分析四条业务链组织，覆盖登录审核工作台、图形验证码校验、JWT 双令牌签发、'
        '加载待办统计看板、认领待审报告、锁定报告编辑权限、AI 预审结论查阅、查验工艺参数凭证、企业签名 RSA 验签、'
        '调取企业登记公钥、篡改检测与告警、签署审核通过决定、结构化驳回出具、驳回类别与意见填写、异步发送整改通知'
        '以及审核历史与通过率统计；其中驳回整改作为条件性补偿流程以 «extend» 标出，如下图所示。'
    ))
    F(doc, 2, '数据审核员用例图', '02-reviewer-usecase-diagram.png')
    add_body(doc, (
        '（3）第三方监管机构（THIRD_PARTY）：作为区域双碳态势的宏观监察主体，监管机构用例按监管大屏总览、链上凭证穿透核验、'
        '存证穿透比对与公信核验、企业履约异常监控四条业务链组织，覆盖双碳监管大屏总览、区域排放态势渲染、零匹配筛选空态、'
        '多角色数据口径隔离、链上凭证穿透核验、TxHash 与区块核验、回读存证原文比对、异常预警与清单导出、存证穿透比对与公信核验、'
        'Fabric 账本只读查询、背书节点签名查验、存证指纹公信核验、企业履约异常监控、异常敞口分层预警、批量链路复核抽样'
        '与异常清单导出留痕；存证穿透比对与公信核验链路以 «include» 逐级展开，如下图所示。'
    ))
    F(doc, 2, '第三方监管机构用例图', '02-thirdparty-admin-usecase-diagram.png')
    add_body(doc, (
        '（4）系统管理员（ADMIN）：作为平台准入治理与系统运维的管理主体，管理员用例按企业准入审批、碳管理证书签发、'
        '核查员资质审核、平台账号治理四条业务链组织，覆盖企业准入审批、材料完整性核验、资质编号唯一性核验、准入状态置位生效、'
        '碳管理证书签发、X.509 根证书签发、证书指纹公示存证、证书吊销处理、核查员资质审核、执业证书真伪核验、'
        '执业范围与辖区绑定、资质状态置位生效、平台账号治理、账号禁用与会话下线、敏感操作留痕检索与审计报表导出归档；'
        '证书吊销作为条件性扩展场景以 «extend» 标出，如下图所示。'
    ))
    F(doc, 2, '系统管理员用例图', '02-admin-usecase-diagram.png')
    add_h2(doc, '2.3 用例分析')
    add_body(doc, (
        '选取系统七大核心业务用例，编制详细的用例规约如下。'
    ))

    add_usecase_table(doc, 2, '用户登录与双令牌认证用例描述', {
        'no': '001',
        'name': '登录系统',
        'desc': '四类用户通过账号密码与图形验证码登录系统，获得对应角色应有的菜单权限与数据访问权限。',
        'actors': '企业用户、数据审核员、第三方监管机构、系统管理员',
        'pre': '账户已通过准入审核且状态正常',
        'post': '登录成功后进入对应角色工作台，可操作授权菜单并对有权限的数据进行操作。',
        'basic': '（1）打开系统登录页\n（2）输入用户名、密码与图形验证码\n（3）提交登录请求\n（4）系统校验验证码有效性与 BCrypt 密码哈希\n（5）签发 JWT 访问令牌与刷新令牌\n（6）记录登录审计日志并按角色跳转工作台',
        'ext': '（1）点击刷新令牌接口静默续期，避免业务中断\n（2）勾选记住账号时自动填充用户名',
        'exc': '（1）验证码错误或过期，阻止登录并提示重新获取\n（2）用户名或密码错误，阻止登录并累计失败次数\n（3）失败次数超过阈值触发账户临时锁定',
        'note': '登录失败计数与 IP 频次均受 @RateLimit 限流保护，敏感操作全量审计留痕。',
    })

    add_usecase_table(doc, 2, '火电企业碳核算填报用例描述', {
        'no': '002',
        'name': '碳核算填报与公式推导',
        'desc': '企业选择火力发电行业模板，逐项录入 25 项工艺参数，系统实时推导温室气体排放总量并生成核算报告草稿。',
        'actors': '企业用户',
        'pre': '企业已完成准入审核且具备核算填报权限',
        'post': '生成核算报告草稿，排放总量与分排放源明细自动推导完成。',
        'basic': '（1）进入碳核算工作台新建报告\n（2）选择火力发电行业模板\n（3）逐项录入化石燃料消耗量、低位发热量、单位热值含碳量、碳氧化率等 25 项参数\n（4）试算引擎实时推导中间量与排放总量\n（5）参数区间合法性校验\n（6）保存草稿或签名后提交审核',
        'ext': '（1）切换电网行业模板按 9 项参数重新推导\n（2）引用上期报告参数快速复用',
        'exc': '（1）必填参数缺失时高亮提示并阻断提交\n（2）参数超出合理区间时提示核对原始台账',
        'note': '后端对全部参数独立执行二次防篡改核算，与前端试算结果比对一致方可入库。',
    })

    add_usecase_table(doc, 2, '碳报告审核与结构化驳回用例描述', {
        'no': '003',
        'name': '报告审核与结构化驳回',
        'desc': '审核员认领待审报告，核验参数与凭证后给出通过结论或填写结构化驳回意见。',
        'actors': '数据审核员',
        'pre': '存在待审报告且审核员具备辖区权限',
        'post': '报告流转至已审通过或已驳回状态，企业实时收到站内通知。',
        'basic': '（1）进入待审报告队列\n（2）认领报告并锁定编辑权限\n（3）逐项核验参数与上传凭证\n（4）校验附件与数据一致性\n（5）签发审核通过意见或填写驳回类别与整改要求\n（6）更新报告状态并通知企业',
        'ext': '（1）调用 AI 预审诊断结论辅助判断\n（2）查看历史版本比对参数变更',
        'exc': '（1）凭证缺失时强制驳回并登记缺失清单\n（2）附件无法打开时提示重新上传',
        'note': '驳回必须选择驳回大类（参数异常／凭证缺失／公式有误／数据矛盾）并填写具体整改意见。',
    })

    add_usecase_table(doc, 2, '数字签名与链上存证用例描述', {
        'no': '004',
        'name': '数字签名与区块链存证',
        'desc': '企业使用 RSA 私钥对核算报告签名，核查通过后由系统将报告哈希提交至 Fabric 联盟链固化存证。',
        'actors': '企业用户、第三方监管机构、Fabric 联盟链网络',
        'pre': '报告已通过审核且企业密钥对已激活',
        'post': '报告哈希写入联盟链账本，生成区块高度与交易哈希（TxHash）存证凭证。',
        'basic': '（1）企业对报告规范化数据执行 SHA256withRSA 签名\n（2）网关校验签名与证书有效性\n（3）第三方监管机构确认核查结论\n（4）系统写入事务外发箱（Outbox）\n（5）异步任务调用 Fabric 链码提交提案\n（6）背书节点共识执行后写入账本并回写 TxHash',
        'ext': '（1）链上交易可通过 TxHash 穿透查询验证\n（2）报告详情页生成存证二维码',
        'exc': '（1）验签失败时拦截提交并提示证书异常\n（2）链上提交失败时进入重试队列自动补偿',
        'note': '私钥在存储与传输全程采用 AES-256-GCM 信封加密保护，绝不落盘明文。',
    })

    add_usecase_table(doc, 2, '双向拍卖挂单与撮合出清用例描述', {
        'no': '005',
        'name': '双向拍卖挂单与撮合出清',
        'desc': '企业提交买卖挂单并冻结对应资产，撮合引擎按价格优先时间优先原则自动撮合成交并完成原子结算。',
        'actors': '企业用户（买方／卖方）',
        'pre': '企业可用碳币余额或可交易配额充足',
        'post': '挂单入池等待撮合，成交后配额与资金完成原子性划转。',
        'basic': '（1）进入碳交易市场查看盘口\n（2）选择买入或卖出方向\n（3）录入委托限价与数量\n（4）系统冻结对应碳币资金或碳配额\n（5）挂单进入优先队列等待撮合\n（6）引擎比对最高买价与最低卖价并成交',
        'ext': '（1）部分成交后剩余数量继续挂单等待\n（2）主动撤单解冻对应资产',
        'exc': '（1）余额或配额不足时阻断挂单\n（2）结算失败时整体回滚冻结',
        'note': '成交价取买卖中间价，DVP 结算由数据库强事务保证账实相符。',
    })

    add_usecase_table(doc, 2, 'AI碳价预测与异常检测用例描述', {
        'no': '006',
        'name': 'AI碳价预测与异常检测',
        'desc': '用户查看基于 Prophet 的碳价预测走势与基于 Isolation Forest 的企业排放异常诊断结论。',
        'actors': '企业用户、第三方监管机构',
        'pre': '算法微服务健康且模型已加载',
        'post': '展示价格预测曲线、95% 置信通道、驱动因子归因与异常检测结论。',
        'basic': '（1）打开 AI 分析看板\n（2）前端携带预测天数请求预测接口\n（3）后端经 Resilience4j 熔断器调用算法微服务\n（4）Prophet 执行时序分解生成预测值与置信通道\n（5）XGBoost 输出特征归因权重\n（6）看板渲染可视化图表与策略建议',
        'ext': '（1）切换 30／90／180 天预测窗口\n（2）导出预测数据与诊断报告',
        'exc': '（1）算法服务不可用时熔断降级返回缓存结果并标注时效\n（2）模型未加载时提示稍后重试',
        'note': '诊断结论可生成 SHA-256 存证哈希锚定 Fabric 联盟链。',
    })

    add_usecase_table(doc, 2, '第三方合规监管与穿透核验用例描述', {
        'no': '007',
        'name': '合规监管与穿透核验',
        'desc': '监管机构通过综合大屏掌握区域碳资产全景，并对链上存证记录执行穿透式核验。',
        'actors': '第三方监管机构',
        'pre': '监管账户具备只读监管权限',
        'post': '输出合规结论、异常通报与链上存证验证结果。',
        'basic': '（1）登录进入监管大屏\n（2）加载区域碳排与履约全景数据\n（3）穿透查看企业与交易明细\n（4）对疑似异常发起 AI 风险评估\n（5）核验链上交易哈希与区块高度\n（6）签发监管通报或合规确认',
        'ext': '（1）按行业与时间维度交叉筛选分析\n（2）导出监管统计报表',
        'exc': '（1）链上哈希与业务数据不一致时触发最高级别告警\n（2）数据加载超时时自动重试',
        'note': '监管操作全程只读留痕，不产生业务数据变更。',
    })

    add_h2(doc, '2.4 技术方案')

    add_h3(doc, '2.4.1 系统架构图')
    add_body(doc, (
        '系统采用六边形架构（端口—适配器模式）与微服务混合布局，核心业务中台与算法服务、区块链网络、'
        '基础设施层通过显式端口解耦，总体分层架构如下图所示。'
    ))
    F(doc, 2, '系统总体分层与六边形微服务拓扑架构图', '01-system-hexagonal-architecture.png')
    add_body(doc, (
        '（1）接入呈现层：Vue 3.5 与 Vite、TypeScript 构建的 SPA 前端，承载四角色工作台，'
        'Nginx 承担静态资源托管、TLS 卸载与 API 反向代理。'
    ))
    add_body(doc, (
        '（2）核心业务中台：Spring Boot 3.2 应用，内含 22 个 REST 控制器（入站适配器）、'
        '35 个领域服务（应用核心）、JPA 仓储与 Fabric Gateway 客户端（出站适配器），'
        '并通过事务外发箱保障数据库事务与链上事件的最终一致性。'
    ))
    add_body(doc, (
        '（3）算法智能中台：FastAPI 与 Python 3.11 独立微服务，承载 Prophet 价格预测、'
        'XGBoost 特征归因与 Isolation Forest 异常检测三类算法任务，并集成大模型生成诊断报告。'
    ))
    add_body(doc, (
        '（4）可信存证层：Hyperledger Fabric 2.5 联盟链网络，多组织节点承载碳报告与交易凭证的不可篡改存证。'
    ))
    add_body(doc, (
        '（5）基础设施层：MySQL 8.0 业务持久化、Redis 7.0 缓存与分布式锁、MinIO 对象存储凭证附件。'
    ))

    add_h3(doc, '2.4.2 数据库命名规则')
    add_body(doc, (
        '数据库设计严格遵循《阿里巴巴 Java 开发手册》与系统架构决策共识。'
    ))
    add_body(doc, (
        '（1）表名规范：业务表名使用小写蛇形命名，语义为英文单数名词（如 carbon_report、auction_order），'
        '不使用复数形式与表前缀。'
    ))
    add_body(doc, (
        '（2）字段规范：主键统一为 id（BIGINT 自增）；时间字段统一 datetime 类型；'
        '布尔语义字段以 is_ 前缀命名（如 is_deleted）；金额与排放量字段统一使用 decimal(18,4) 保证精度不丢失。'
    ))
    add_body(doc, (
        '（3）逻辑删除规范：全业务表继承 BaseEntity，通过 is_deleted 逻辑删除字段标记，'
        '所有查询强制附加未删除条件，物理删除仅限数据归档脚本。'
    ))
    add_body(doc, (
        '（4）索引规范：外键关联字段必建索引；高频组合查询建立联合索引并遵循最左前缀原则；'
        '唯一业务键（如报告编号、企业信用代码）建立唯一约束。'
    ))

    add_h3(doc, '2.4.3 类命名规则')
    add_body(doc, (
        '（1）实体类：使用大驼峰命名且为英文单数名词，如 CarbonReport（碳核算报告）、AuctionOrder（拍卖挂单）。'
    ))
    add_body(doc, (
        '（2）控制器类：以业务域开头并以 Controller 结尾，如 CarbonController、AuctionController。'
    ))
    add_body(doc, (
        '（3）服务类：接口命名为 XXXService，实现类命名为 XXXServiceImpl；'
        '领域计算引擎采用 XXXEngine 后缀，如 CarbonCalculationEngine。'
    ))
    add_body(doc, (
        '（4）数据传输对象：请求体使用 XXXRequest 或 XXXDTO，响应体使用 XXXResponse 或 XXXVO，'
        '分页统一使用 PageResponse 泛型包装。'
    ))

    add_h3(doc, '2.4.4 方法、参数、成员变量、局部变量命名规则')
    add_body(doc, (
        '（1）方法命名：使用小驼峰动词短语，遵循 CRUD 语义前缀——查询用 get／find／list／query，'
        '新增用 create／add／save，修改用 update／modify，删除用 delete／remove，'
        '如 findByEnterpriseIdAndStatus、submitForReview。'
    ))
    add_body(doc, (
        '（2）参数与成员变量：同样使用小驼峰命名，成员变量不使用 m_ 或下划线前缀；'
        '布尔类型参数以 is／has／can 开头（如 isVerified）。'
    ))
    add_body(doc, (
        '（3）局部变量：小驼峰命名，不使用单字符命名（循环下标除外）；'
        '常量使用全大写蛇形命名，如 MAX_RETRY_COUNT。'
    ))
    add_body(doc, (
        '（4）方法与类的注释统一采用 Javadoc 规范，公开方法必须声明参数、返回值与异常语义。'
    ))

    add_h3(doc, '2.4.5 包名结构')
    add_body(doc, (
        '后端采用按技术分层与业务域划分相结合的组织结构，根包为 com.oaiss.chain，主要分包如下表所示。'
    ))
    T(doc, 2, '后端工程分层包结构说明表',
      ['包路径', '职责说明', '典型内容'],
      [
          ['com.oaiss.chain.controller', 'REST 入站适配器', '22 个控制器，负责请求校验、权限注解与响应封装'],
          ['com.oaiss.chain.service', '领域应用服务层', '35 个业务服务接口与核算、撮合、签名等领域引擎'],
          ['com.oaiss.chain.repository', '数据访问出站适配器', '24 个 JPA 仓储接口与自定义查询'],
          ['com.oaiss.chain.entity', 'JPA 实体模型', '25 个实体类（含 BaseEntity 审计基类）'],
          ['com.oaiss.chain.dto', '数据传输对象', '请求 DTO、响应 VO 与分页包装'],
          ['com.oaiss.chain.security', '安全基础设施', 'JWT 过滤器、权限求值器、AES-GCM 信封加密组件'],
          ['com.oaiss.chain.config', '系统配置', '安全配置、缓存配置、Fabric Gateway 配置'],
          ['com.oaiss.chain.aop', '横切关注点', '审计日志、限流、数据隔离、分布式锁注解切面'],
          ['com.oaiss.chain.exception', '异常与错误码', '全局异常处理器与 ErrorCode 枚举'],
      ],
      col_widths=[5.4, 3.6, 8.0])
    add_body(doc, (
        '前端工程采用按角色视图与业务模块划分的结构：src/api 维护 22 个接口模块，'
        'src/views 下按 enterprise、auditor、third-party、admin 四角色组织页面，'
        'src/stores 管理 Pinia 全局状态，src/router 定义角色路由守卫。'
    ))

# ============================================================
# 第 3 章 设计内容（过程）与说明
# ============================================================

def build_chapter_3(doc):
    add_page_break(doc)
    add_h1(doc, '3  设计内容（过程）与说明')

    add_h2(doc, '3.1 数据库设计')

    add_h3(doc, '3.1.1 数据库物理模型')
    add_body(doc, (
        '系统数据库基于 MySQL 8.0 构建，通过 Flyway 实施 V1 至 V13 版本化迁移管理，'
        '共设计 24 张核心业务表，划分为身份权限、企业资质、碳资产核算、市场交易、'
        '碳币信誉与可靠事件六大业务域，实体关系模型如下图所示。'
    ))
    F(doc, 3, '数据库核心业务实体 ER 逻辑关系模型图', '04-database-entity-er-diagram.png')
    add_body(doc, (
        '（1）身份权限域：user（用户主表）、user_type_list（角色关联）、entry_permission（权限项）、'
        'account_permission_list（账户权限关联）、operation_log（审计日志）、notification（站内通知）。'
    ))
    add_body(doc, (
        '（2）企业资质域：enterprise（企业主体）、reviewer（审核员）、third_party_org（监管机构）、'
        'enterprise_admission（企业准入）、reviewer_qualification（审核员资质）、certificate（证书存证）。'
    ))
    add_body(doc, (
        '（3）碳资产核算域：carbon_report（核算报告主表）、carbon_neutral_project（碳中和项目）、'
        'emission_rating（排放评级）、rsa_key_pair（密钥托管）。'
    ))
    add_body(doc, (
        '（4）市场交易域：auction_order（拍卖挂单）、matching_result（撮合结果）、transaction（交易成交）。'
    ))
    add_body(doc, (
        '（5）碳币信誉域：carbon_coin_account（碳币账户）、carbon_coin_transaction（碳币流水）、'
        'credit_score（信用评分）、credit_event（信用事件）。'
    ))
    add_body(doc, (
        '（6）可靠事件域：outbox_message（事务外发箱）承载数据库与联盟链之间的最终一致性保障。'
    ))

    add_h3(doc, '3.1.2 数据表设计')
    add_body(doc, (
        '以下列出系统五张最核心业务数据表的完整字段定义。'
    ))

    add_data_table(doc, 3, 'carbon_report', '碳核算报告',
        '存储企业碳核算报告全生命周期数据，包括核算参数快照、排放总量、审核状态、数字签名与链上存证凭证。',
        [
            ('id', '报告主键', 'long', '20', 'NO', '自动增长'),
            ('report_no', '报告编号', 'varchar', '50', 'NO', '唯一约束'),
            ('enterprise_id', '企业ID', 'long', '20', 'NO', '外键，关联enterprise表'),
            ('submitter_id', '提交人ID', 'long', '20', 'NO', '外键，关联user表'),
            ('accounting_period', '核算周期', 'varchar', '20', 'NO', '如2026-Q1'),
            ('title', '报告标题', 'varchar', '200', 'NO', ''),
            ('report_type', '报告类型', 'int', '-', 'NO', '0火电/1电网'),
            ('emission_data', '核算参数集', 'text', '-', 'NO', '25项或9项工艺参数快照'),
            ('total_emission', '排放总量(tCO2e)', 'decimal', '15,4', 'YES', '国标公式推导结果'),
            ('scope1_emission', '范围一排放', 'decimal', '15,4', 'YES', '化石燃料直接燃烧排放'),
            ('scope2_emission', '范围二排放', 'decimal', '15,4', 'YES', '净购入电力隐含排放'),
            ('scope3_emission', '范围三排放', 'decimal', '15,4', 'YES', '其他间接排放'),
            ('calculation_method', '核算方法', 'varchar', '100', 'YES', '依据GB/T 32150'),
            ('status', '报告状态', 'int', '-', 'NO', '0草稿/1待审/2已审/3通过/4驳回/5上链'),
            ('reviewer_id', '审核员ID', 'long', '20', 'YES', '外键，关联reviewer表'),
            ('review_comment', '审核意见', 'text', '-', 'YES', '驳回原因或通过备注'),
            ('reviewed_at', '审核时间', 'datetime', '-', 'YES', ''),
            ('signature_data', '数字签名', 'text', '-', 'YES', 'SHA256withRSA签名密文'),
            ('blockchain_tx_hash', '链上交易哈希', 'varchar', '255', 'YES', 'Fabric存证TxHash'),
            ('on_chain_at', '上链时间', 'datetime', '-', 'YES', ''),
            ('attachments', '附件列表', 'text', '-', 'YES', 'MinIO对象路径集合'),
            ('created_at', '创建时间', 'datetime', '-', 'NO', 'DEFAULT CURRENT_TIMESTAMP'),
            ('updated_at', '更新时间', 'datetime', '-', 'NO', 'ON UPDATE CURRENT_TIMESTAMP'),
            ('is_deleted', '逻辑删除', 'tinyint', '1', 'NO', '0未删除/1已删除'),
        ])

    add_data_table(doc, 3, 'auction_order', '双向拍卖挂单',
        '存储企业碳配额双向拍卖挂单数据，包含买卖方向、委托限价、冻结资产与成交状态。',
        [
            ('id', '挂单主键', 'long', '20', 'NO', '自动增长'),
            ('order_no', '挂单编号', 'varchar', '50', 'NO', '唯一约束'),
            ('enterprise_id', '挂单企业ID', 'long', '20', 'NO', '外键，关联enterprise表'),
            ('direction', '买卖方向', 'varchar', '8', 'NO', 'BID买/ASK卖'),
            ('price', '委托限价', 'decimal', '15,4', 'NO', '单位：碳币/吨'),
            ('quantity', '委托数量', 'decimal', '15,4', 'NO', '单位：吨'),
            ('filled_quantity', '已成交数量', 'decimal', '15,4', 'NO', '默认0，撮合累加'),
            ('frozen_amount', '冻结资金/配额', 'decimal', '15,4', 'NO', '买单冻结碳币，卖单冻结配额'),
            ('status', '挂单状态', 'varchar', '20', 'NO', 'ACTIVE/PARTIALLY_FILLED/FILLED/CANCELLED'),
            ('created_at', '创建时间', 'datetime', '-', 'NO', ''),
            ('updated_at', '更新时间', 'datetime', '-', 'NO', ''),
            ('is_deleted', '逻辑删除', 'tinyint', '1', 'NO', '0未删除/1已删除'),
        ])

    add_data_table(doc, 3, 'matching_result', '拍卖撮合结果',
        '记录每一笔买卖挂单的撮合成交明细与 DVP 结算状态。',
        [
            ('id', '撮合结果主键', 'long', '20', 'NO', '自动增长'),
            ('buy_order_id', '买单ID', 'long', '20', 'NO', '外键，关联auction_order表'),
            ('sell_order_id', '卖单ID', 'long', '20', 'NO', '外键，关联auction_order表'),
            ('matched_price', '成交价格', 'decimal', '15,4', 'NO', '买卖限价中间价'),
            ('matched_quantity', '成交数量', 'decimal', '15,4', 'NO', '取买卖余量较小值'),
            ('matched_amount', '成交总额', 'decimal', '15,4', 'NO', '成交价乘以成交数量'),
            ('settle_status', '结算状态', 'varchar', '20', 'NO', 'PENDING_SETTLEMENT/SETTLED/FAILED'),
            ('settled_at', '结算时间', 'datetime', '-', 'YES', ''),
            ('created_at', '撮合时间', 'datetime', '-', 'NO', ''),
        ])

    add_data_table(doc, 3, 'rsa_key_pair', 'RSA密钥托管',
        '托管用户 RSA-2048 密钥对，私钥经 AES-256-GCM 信封加密后存储。',
        [
            ('id', '密钥主键', 'long', '20', 'NO', '自动增长'),
            ('owner_id', '持有主体ID', 'long', '20', 'NO', '外键，关联user表'),
            ('public_key', '公钥明文', 'text', '-', 'NO', 'X.509编码Base64公钥'),
            ('private_key_cipher', '私钥密文', 'text', '-', 'NO', 'AES-256-GCM信封加密'),
            ('iv', '初始向量', 'varchar', '64', 'NO', 'GCM随机IV'),
            ('auth_tag', '认证标签', 'varchar', '64', 'NO', 'GCM完整性认证标签'),
            ('status', '密钥状态', 'varchar', '16', 'NO', 'ACTIVE/REVOKED'),
            ('created_at', '生成时间', 'datetime', '-', 'NO', ''),
        ])

    add_data_table(doc, 3, 'enterprise', '企业主体',
        '存储控排企业主体信息、行业分类、碳配额持仓与准入状态。',
        [
            ('id', '企业主键', 'long', '20', 'NO', '自动增长'),
            ('user_id', '关联账户ID', 'long', '20', 'NO', '外键，唯一约束'),
            ('credit_code', '统一社会信用代码', 'varchar', '32', 'NO', '唯一约束，18位'),
            ('enterprise_name', '企业名称', 'varchar', '128', 'NO', '工商注册全称'),
            ('industry_type', '所属行业', 'varchar', '32', 'NO', 'POWER火电/GRID电网等'),
            ('carbon_tradable', '可交易配额', 'decimal', '15,4', 'NO', '默认0，单位吨'),
            ('carbon_frozen', '冻结配额', 'decimal', '15,4', 'NO', '默认0，挂单锁定'),
            ('admission_status', '准入状态', 'varchar', '16', 'NO', 'PENDING/APPROVED/REJECTED'),
            ('created_at', '创建时间', 'datetime', '-', 'NO', ''),
            ('updated_at', '更新时间', 'datetime', '-', 'NO', ''),
            ('is_deleted', '逻辑删除', 'tinyint', '1', 'NO', '0未删除/1已删除'),
        ])

    add_h2(doc, '3.2 界面设计')
    add_body(doc, (
        '系统界面采用 Element Plus 组件体系与 ECharts 可视化引擎构建，严格遵循 ELI5 易用性设计准则'
        '（每页顶部提供白话功能提示条、专业术语悬浮释义、状态文字与颜色双向编码）。'
        '以下按企业端、审核端、监管端与管理端四类角色，展示九大核心业务界面。'
    ))

    add_h3(doc, '3.2.1 企业数据可视化驾驶舱')
    add_body(doc, (
        '企业用户登录后进入数据可视化驾驶舱，一屏总览本月碳排放、履约进度与信用状况。'
        '页面顶部为 AI 执行摘要晨报，自动给出当前碳价在置信通道中的位置、'
        '年度清缴窗口倒计时与量化履约建议；下方按资产编号、报告类型、核算周期等条件组合查询核算报告。'
    ))
    SHOT(doc, 3, '企业数据可视化驾驶舱界面', 'role-dashboard.png')

    add_h3(doc, '3.2.2 碳核算填报与国标公式计算器')
    add_body(doc, (
        '碳核算填报页支持选择火力发电或电网行业模板，录入核算周期与活动数据后，'
        '系统按 GB/T 32150 国标公式自动计算排放总量并生成防伪签名；'
        '公式计算器支持 25 项（火电）与 9 项（电网）参数逐项录入与实时推导，'
        '右侧同步展示推导过程与中间量，可保存草稿或直接提交审核。'
    ))
    SHOT(doc, 3, '碳核算填报与国标公式计算器界面', 'role-carbon-upload.png')

    add_h3(doc, '3.2.3 碳配额双向拍卖与P2P交易市场')
    add_body(doc, (
        '企业在双向拍卖市场按最优价分批挂出买单或卖单，系统自动冻结对应碳币资金或碳配额并进入撮合队列；'
        '页面提供全部挂单、我的挂单与撮合结果三个视图，实时跟踪挂单状态、价格与成交记录；'
        '同时支持通过 P2P 交易页与供应链上下游企业点对点议价成交。'
    ))
    SHOT(doc, 3, '碳配额双向拍卖交易市场界面', 'role-trading-market.png')

    add_h3(doc, '3.2.4 碳币账户与信誉评分')
    add_body(doc, (
        '碳币账户页展示企业碳币余额、收支流水与充值划转记录；信誉评分页展示碳信用综合得分、'
        '等级评定与信用事件明细，信用评分与企业交易、履约行为实时联动。'
    ))
    SHOT(doc, 3, '碳币账户与信誉评分界面', 'role-carbon-coin.png')

    add_h3(doc, '3.2.5 区块链浏览器与链上存证核验')
    add_body(doc, (
        '区块链浏览器实时展示 Fabric 联盟链连接状态（运行模式 FABRIC、通道 mychannel、节点数与排序节点数），'
        '支持按交易哈希直接查询链上交易详情，并以「最新区块」与「交易记录」双视图呈现区块高度、'
        '区块哈希、区块类型、交易数量与时间戳等账本数据，实现存证记录的穿透式核验。'
    ))
    SHOT(doc, 3, '区块链浏览器与链上存证核验界面', 'role-blockchain.png')

    add_h3(doc, '3.2.6 AI智能预测与异常检测')
    add_body(doc, (
        'AI 智能预测模块包含碳市场量化推演与企业碳合规体检两大视图：'
        '前者基于 Prophet 时序模型输出未来价格预测曲线与 95% 置信通道，并给出 XGBoost 特征归因与建仓策略；'
        '后者基于 Isolation Forest 输出六维合规偏离雷达图、异常穿透解析与三阶段降碳技改路径。'
    ))
    SHOT(doc, 3, 'AI碳价预测与驱动因子归因界面', 'role-market-prediction.png')

    add_h3(doc, '3.2.7 审核端待审报告与审核工作台')
    add_body(doc, (
        '审核员登录后进入审核材料工作台，页面顶部展示个人执业资格证（证书编号、所属组织）'
        '与审核统计（总审核数、通过数、驳回数、通过率）；待审报告列表按报告编号、企业名称、报告标题、'
        '总排放量、状态、验签结果与创建时间逐条展示，支持进入审核并给出通过或结构化驳回意见。'
    ))
    SHOT(doc, 3, '审核端待审报告列表界面', 'role-reviewer-audit.png')

    add_h3(doc, '3.2.8 监管端第三方监管面板')
    add_body(doc, (
        '第三方监管机构登录后进入监管中心，页面展示监管机构信息（机构名称、权限级别、地址与联系人），'
        '并按总报告数、待审核、已通过、已拒绝与审核通过率汇总全平台数据；'
        '碳排放报告列表穿透展示各企业报告的核算周期、排放总量与链上存证状态，'
        '支持按企业、关键词与状态组合筛选。'
    ))
    SHOT(doc, 3, '第三方监管面板界面', 'role-thirdparty-monitor.png')

    add_h3(doc, '3.2.9 管理端平台统计与用户管理')
    add_body(doc, (
        '管理员可在数据管理页查看全平台规模统计（总用户数、企业数量、审核员数量、第三方数量）'
        '与用户类型分布图；在用户管理中按用户类型与状态筛选账号并执行启用、禁用操作，'
        '实现全平台账号的集中治理与审计。'
    ))
    SHOT(doc, 3, '管理端平台规模统计界面', 'role-admin-statistics.png')

    add_h2(doc, '3.3 类设计')

    add_h3(doc, '3.3.1 碳核算核心业务类设计')
    add_body(doc, (
        '碳核算业务由 CarbonController 接收请求，经 CarbonReportService 编排报告生命周期，'
        '具体核算委托 CarbonCalculationEngine 依据行业策略执行。核心类职责如下表所示。'
    ))
    T(doc, 3, '碳核算核心业务类职责说明表',
      ['类名', '类型', '核心职责', '关键方法'],
      [
          ['CarbonController', 'Controller', '核算请求入口与权限校验', 'calculate()、submit()、listReports()'],
          ['CarbonReportService', 'Service', '报告生命周期编排与状态机管控', 'createDraft()、submitForReview()、reject()'],
          ['CarbonCalculationEngine', 'Domain Engine', '国标公式推导与参数校验策略', 'calculatePower()、calculateGrid()、validate()'],
          ['CarbonReportRepository', 'Repository', '报告持久化与查询', 'findByEnterpriseIdAndStatus()'],
          ['DigitalSignatureService', 'Security Service', 'RSA 签名与验签', 'sign()、verify()'],
      ],
      col_widths=[4.4, 2.6, 5.6, 4.4])

    add_h3(doc, '3.3.2 撮合引擎与区块链适配类设计')
    add_body(doc, (
        '交易撮合与链上存证模块采用“引擎＋适配器”模式，撮合逻辑与区块链通信完全解耦，核心类职责如下表所示。'
    ))
    T(doc, 3, '撮合与区块链适配核心类职责说明表',
      ['类名', '类型', '核心职责', '关键方法'],
      [
          ['AuctionOrderService', 'Service', '挂单创建、冻结校验与撤单', 'placeOrder()、cancelOrder()'],
          ['DoubleAuctionMatcher', 'Domain Engine', '价格优先时间优先撮合算法', 'match()、comparePriority()'],
          ['SettlementService', 'Service', 'DVP 原子结算与资金交割', 'settle()、refundSpread()'],
          ['FabricGatewayClient', 'Outbound Adapter', 'Fabric 链码提交与查询', 'submitTransaction()、evaluateTransaction()'],
          ['OutboxEventPublisher', 'Infrastructure', '事务外发箱可靠事件投递', 'publish()、retryFailed()'],
      ],
      col_widths=[4.4, 2.6, 5.6, 4.4])

    add_h3(doc, '3.3.3 全系统工程架构包图')
    add_body(doc, (
        '系统前后端工程组织与微服务交互全景如下图所示，前端按角色视图分域，'
        '后端按六边形分层解耦，算法中台与联盟链网络独立部署。'
    ))
    F(doc, 3, '全系统工程架构与包组织关系图', '08-frontend-vue3-architecture.png')

    add_h2(doc, '3.4 顺序图设计')

    add_h3(doc, '3.4.1 碳核算填报与公式推导顺序图')
    add_body(doc, (
        '企业填报参数后，前端实时调用试算接口，后端核算引擎执行公式推导并返回中间结果，'
        '提交时执行数字签名与持久化。时序流程为：前端录入参数，调用试算接口，后端执行参数校验与公式推导，'
        '返回中间量与排放总量；用户确认后执行 RSA 数字签名，报告入库并流转至待审状态。'
    ))

    add_h3(doc, '3.4.2 数字签名与链上存证顺序图')
    add_body(doc, (
        '密码学防伪与链上固化流程如下图所示，涵盖密钥信封加密、报告签名、验签与 Fabric 两阶段背书提交全过程。'
    ))
    F(doc, 3, '密码学数字签名与链上存证流转顺序图', '06-cryptography-envelope-encryption.png')

    add_h3(doc, '3.4.3 双向拍卖撮合与DVP结算顺序图')
    add_body(doc, (
        '撮合引擎从买卖双端优先队列取单比对，成交后执行配额与资金的原子性交割，清算流程如下图所示。'
    ))
    F(doc, 3, '双向拍卖撮合引擎与DVP结算流转顺序图', '05-double-auction-matching-engine.png')

    add_h3(doc, '3.4.4 AI预测微服务调度顺序图')
    add_body(doc, (
        '业务后端经 Resilience4j 熔断器保护调用算法微服务，算法侧执行多模型流水线并返回结构化结果，'
        '调度链路如下图所示。'
    ))
    F(doc, 3, 'AI预测算法微服务调用与模型流水线顺序图', '10-ai-ml-microservice-architecture.png')

    add_h2(doc, '3.5 API接口设计')
    add_body(doc, (
        '系统共实现 143 个 REST API 端点，全部遵循统一响应信封与分页规范。'
        '以下按用户认证、碳核算、拍卖交易与链上核验三大模块列出核心接口契约。'
    ))

    add_h3(doc, '3.5.1 用户认证模块接口')
    add_label_table(doc, 3, '用户登录接口描述表', [
        ('接口名称：', 'user_login', '', ''),
        ('接口地址：', 'POST /api/v1/auth/login', '', ''),
        ('接口参数：', '字段名称', '类型', '描述'),
        ('接口参数：', 'username', 'String', '用户名（企业信用代码或注册账号）'),
        ('接口参数：', 'password', 'String', '登录密码（BCrypt 比对）'),
        ('接口参数：', 'captchaKey', 'String', '图形验证码校验键'),
        ('接口参数：', 'captchaCode', 'String', '图形验证码明文'),
        ('返回值：', '返回用户登录状态与双令牌', '', ''),
        ('返回值：', '字段定义', '类型', '描述'),
        ('返回值：', 'accessToken', 'String', '访问令牌（1小时有效期）'),
        ('返回值：', 'refreshToken', 'String', '刷新令牌（7天有效期）'),
        ('返回值：', 'role', 'String', '用户角色标识'),
        ('接口描述：', '根据用户名与密码进行登录校验，校验通过后签发双令牌并记录审计日志。', '', ''),
    ])

    add_h3(doc, '3.5.2 碳核算模块接口')
    add_label_table(doc, 3, '碳核算推导接口描述表', [
        ('接口名称：', 'carbon_calculate', '', ''),
        ('接口地址：', 'POST /api/v1/carbon/calculate', '', ''),
        ('接口参数：', '字段名称', '类型', '描述'),
        ('接口参数：', 'industryType', 'String', '行业类型（POWER 火电 / GRID 电网）'),
        ('接口参数：', 'params', 'Object', '25 项（火电）或 9 项（电网）核算参数对象'),
        ('接口参数：', 'accountingPeriod', 'String', '核算周期，如 2026-Q1'),
        ('返回值：', '返回核算推导结果', '', ''),
        ('返回值：', '字段定义', '类型', '描述'),
        ('返回值：', 'totalEmission', 'BigDecimal', '排放总量（tCO2e）'),
        ('返回值：', 'scopeDetails', 'Array', '分排放源明细与推导过程'),
        ('返回值：', 'valid', 'Boolean', '参数校验是否通过'),
        ('接口描述：', '依据 GB/T 32150 标准执行碳排放公式推导，返回排放总量与分工序明细。', '', ''),
    ])

    add_h3(doc, '3.5.3 拍卖交易与链上核验模块接口')
    add_label_table(doc, 3, '拍卖挂单与链上核验接口描述表', [
        ('接口名称：', 'auction_create_order / blockchain_verify', '', ''),
        ('接口地址：', 'POST /api/v1/auction/orders ；GET /api/v1/blockchain/verify/{txHash}', '', ''),
        ('接口参数：', '字段名称', '类型', '描述'),
        ('接口参数：', 'direction', 'String', '买卖方向（BID 买 / ASK 卖）'),
        ('接口参数：', 'price', 'BigDecimal', '委托限价（碳币/吨）'),
        ('接口参数：', 'quantity', 'BigDecimal', '委托数量（吨）'),
        ('接口参数：', 'txHash', 'String', '链上交易哈希（核验接口路径参数）'),
        ('返回值：', '返回挂单结果与链上核验结论', '', ''),
        ('返回值：', '字段定义', '类型', '描述'),
        ('返回值：', 'orderNo', 'String', '挂单编号'),
        ('返回值：', 'frozenAmount', 'BigDecimal', '冻结资金或配额数量'),
        ('返回值：', 'onChainVerified', 'Boolean', '链上哈希与业务数据是否一致'),
        ('接口描述：', '支持碳配额限价挂单与资产冻结，以及链上存证记录穿透核验。', '', ''),
    ])

    add_h2(doc, '3.6 项目测试')
    add_body(doc, (
        '系统构建了“单元测试—接口测试—集成测试—端到端巡检”四层测试体系，'
        '并以持续集成门禁强制保障质量红线，测试体系架构如下图所示。'
    ))
    F(doc, 3, '全栈分层测试金字塔与质量度量架构图', '12-fullstack-testing-pyramid.png')

    add_h3(doc, '3.6.1 单元测试计划')
    add_body(doc, (
        '单元测试覆盖后端领域服务、核算引擎、撮合算法与安全组件，采用参数化用例覆盖边界值，'
        '并由 JaCoCo 强制校验覆盖率门槛。'
    ))
    add_merged_meta_table(doc, 3, '单元测试计划表',
      [('单元测试范围', '碳核算引擎、撮合结算引擎、安全签名组件、前端状态与工具'),
       ('单元测试方法', '人工输入值'),
       ('单元测试环境', 'IntelliJ IDEA 2024.1、JUnit 5、Mockito、Vitest、JDK 17、MySQL 8.0'),
       ('将产生的文档', '单元测试用例、单元测试报告')],
      ['单元测试任务 / 优先级', '要测试的功能', '预期结果', '进度日期', '实际日期', '负责人'],
      [
          ['火电25项参数核算', '能否按 GB/T 32150 正确推导排放总量与分源明细', 'true', '2026-03-06', '2026-03-08', '本人'],
          ['电网9项参数核算', '能否正确折算输配电损失电量与逸散排放', 'true', '2026-03-06', '2026-03-08', '本人'],
          ['撮合引擎价格优先', '高价买单能否优先于低价买单成交', 'true', '2026-03-07', '2026-03-09', '本人'],
          ['撮合引擎时间优先', '同价挂单能否按挂单时间先后成交', 'true', '2026-03-07', '2026-03-09', '本人'],
          ['DVP原子结算', '成交后配额与资金能否双边账实相符', 'true', '2026-03-08', '2026-03-10', '本人'],
          ['RSA签名与验签', '篡改数据能否被验签拦截', 'true', '2026-03-08', '2026-03-10', '本人'],
          ['AES-GCM信封加密', '私钥密文能否正确解密且防篡改', 'true', '2026-03-09', '2026-03-11', '本人'],
      ],
      col_widths=[3.4, 5.4, 1.8, 2.3, 2.3, 1.6])

    add_h3(doc, '3.6.2 接口测试计划')
    add_body(doc, (
        '接口测试以 143 个 REST 端点为核心，依托 Testcontainers 拉起真实 MySQL 与 Redis 容器，'
        '覆盖正常流与异常分支。'
    ))
    add_label_table(doc, 3, '用户登录接口测试计划表', [
        ('接口名称：', 'user_login', '', ''),
        ('测试地址：', 'POST http://localhost:8080/api/v1/auth/login', '', ''),
        ('测试工具：', 'Postman / JUnit 5', '', ''),
        ('接口参数：', '字段名称', '类型', '描述'),
        ('接口参数：', 'username', 'String', '用户名'),
        ('接口参数：', 'password', 'String', '登录密码'),
        ('测试用例：', '1.测试用户名和密码正确的情况\n2.测试用户名正确密码错误的情况\n3.测试用户名不存在的情况\n4.测试验证码错误的情况', '', ''),
    ])

    add_h3(doc, '3.6.3 集成测试计划')
    add_body(doc, (
        '集成测试以真实业务场景为驱动，串联四角色跨模块协作，验证端到端业务闭环的正确性与数据一致性。'
    ))
    add_merged_meta_table(doc, 3, '集成测试计划表',
      [('集成测试范围', '碳核算填报模块、审核驳回模块、链上存证模块、拍卖撮合模块'),
       ('集成测试方法', '人工输入值'),
       ('集成测试环境', 'Docker Compose 集群、MySQL 8.0、Redis 7.2、Hyperledger Fabric 2.5'),
       ('将产生的文档', '集成测试报告')],
      ['集成测试任务 / 优先级', '要测试的功能', '预期结果', '进度日期', '实际日期', '负责人'],
      [
          ['碳核算填报模块', '企业能否完成参数填报、公式推导与签名提交全流程', 'true', '2026-03-10', '2026-03-12', '本人'],
          ['审核驳回模块', '审核员能否完成核验、通过或结构化驳回', 'true', '2026-03-11', '2026-03-13', '本人'],
          ['链上存证模块', '报告哈希能否成功上链并生成 TxHash 凭证', 'true', '2026-03-12', '2026-03-13', '本人'],
          ['拍卖撮合模块', '挂单能否撮合成交并完成 DVP 原子结算', 'true', '2026-03-13', '2026-03-14', '本人'],
      ],
      col_widths=[3.4, 5.4, 1.8, 2.3, 2.3, 1.6])

    add_h3(doc, '3.6.4 单元测试报告')
    add_body(doc, (
        '核心业务服务单元测试报告如下表所示，涵盖正常输入、边界输入与异常输入的测试结果。'
    ))
    add_merged_meta_table(doc, 3, '单元测试报告',
      [('模块名称', '碳核算与撮合结算相关服务'),
       ('代码审核人员', '指导教师'),
       ('单元测试工作量（天）', '5天')],
      ['模块名/函数名', '输入数据类型', '输入数据值', '期望结果', '实际结果', '备注'],
      [
          ['calculatePower(dto)', '正常输入', '25项参数完整录入', '排放总量150.00 tCO2e', '排放总量150.00', ''],
          ['calculatePower(dto)', '异常输入', '碳氧化率参数缺失', '提示参数缺失并阻断提交', '提示参数缺失', ''],
          ['calculateGrid(dto)', '正常输入', '9项参数完整录入', '折算结果符合国标样例', '折算结果正确', ''],
          ['matchOrders(bid,ask)', '正常输入', '买52.0卖50.0各100吨', '中间价51.0成交100吨', '成交100吨', ''],
          ['matchOrders(bid,ask)', '边界输入', '买50.0卖50.0各50吨', '等价格正常成交', '成交50吨', ''],
          ['matchOrders(bid,ask)', '异常输入', '买45.0卖50.0价格倒挂', '不触发撮合挂单入池', '未触发撮合', ''],
          ['settle(result)', '正常输入', '有效撮合结果', '配额与资金双边账实相符', '账实相符', ''],
          ['verifySignature(data,sig)', '异常输入', '数据被篡改', '验签失败并拦截', '验签失败', '防篡改生效'],
          ['sign(data)', '正常输入', '规范化报告JSON串', '返回有效签名密文', '签名成功', ''],
          ['aesGcmEncrypt(key,iv)', '正常输入', 'RSA私钥明文', '返回密文与认证标签', '加密成功', ''],
      ],
      col_widths=[3.6, 2.2, 3.4, 3.4, 2.6, 1.8])

    add_h3(doc, '3.6.5 接口测试报告')
    add_body(doc, (
        '用户登录接口测试报告如下表所示，覆盖正确凭证、错误密码与账号不存在三类用例。'
    ))
    add_label_table(doc, 3, '用户登录接口测试报告', [
        ('接口名称：', 'user_login', '', ''),
        ('测试地址：', 'POST http://localhost:8080/api/v1/auth/login', '', ''),
        ('测试工具：', 'Postman / JUnit 5', '', ''),
        ('接口参数：', '字段名称', '类型', '描述'),
        ('接口参数：', 'username', 'String', '用户名'),
        ('接口参数：', 'password', 'String', '登录密码'),
        ('测试用例：', '1.测试用户名和密码正确的情况\n2.测试用户名正确密码错误的情况\n3.测试用户名不存在的情况', '', ''),
        ('测试数据：', 'username：enterprise001   password：正确的登录口令', '', ''),
        ('测试数据：', 'username：enterprise001   password：错误的密码', '', ''),
        ('测试数据：', 'username：nobody999   password：任意密码', '', ''),
        ('测试人', '本人', '测试日期', '2026-03-12'),
        ('测试结论：', '第一个测试用例：通过，返回双令牌与角色信息，符合预期\n第二个测试用例：通过，返回 401 拦截并累计失败次数\n第三个测试用例：通过，返回账号不存在提示且不泄露账号状态', '', ''),
    ])

    add_h3(doc, '3.6.6 集成测试报告')
    add_body(doc, (
        '全系统集成测试基于真实运行环境（MySQL 8.0 + Redis 7.0 + MinIO + Hyperledger Fabric 2.5 联盟链）执行，'
        '覆盖核算填报、审核驳回、链上存证与拍卖撮合四条核心业务链路，测试报告如下表所示。'
    ))
    add_merged_meta_table(doc, 3, '全系统集成测试报告', [],
      ['功能点', '用例编号', '输入', '预期结果', '测试结果', '失败原因'],
      [
          ['碳核算填报——》参数录入', '002', '25项火电参数完整录入', '实时推导排放总量并展示中间计算量', '符合预期', ''],
          ['碳核算填报——》公式校验', '002', '碳氧化率超出合理区间', '高亮提示参数超限并阻断提交', '符合预期', ''],
          ['审核驳回——》结构化驳回', '003', '驳回类别：凭证缺失', '报告置为已驳回并通知企业补正', '符合预期', ''],
          ['链上存证——》报告上链', '004', '有效报告哈希', '生成 TxHash 与区块高度并可穿透核验', '符合预期', ''],
          ['双向拍卖——》撮合出清', '005', '买52.0、卖50.0各100吨', '中间价51.0成交并完成原子结算', '符合预期', ''],
          ['双向拍卖——》超卖防护', '005', '并发提交超出余额的买单', '余额不足挂单被阻断，无超卖发生', '符合预期', ''],
      ],
      col_widths=[3.6, 2.0, 3.2, 4.4, 2.2, 1.6])
    add_body(doc, (
        '系统联调实测数据：第三方监管面板汇总全平台累计报告数 78 份，其中待审核 6 份、已通过 23 份、'
        '已拒绝 4 份，审核通过率 85.2%；审核端工作台累计审核 27 份（通过 23 份、驳回 4 份）；'
        '区块链浏览器实测连接 Fabric 联盟链（运行模式 FABRIC、通道 mychannel），'
        '区块高度已达 28 块，多次报告存证交易可通过交易哈希穿透查询并核验；'
        '管理端用户管理实测纳管四类角色共 6 个账号，启用与禁用操作即时生效。'
        '全部集成用例执行通过率 100%，未发现阻断级缺陷。'
    ))

# ============================================================
# 第 4 章 设计总结
# ============================================================

def build_chapter_4(doc):
    add_page_break(doc)
    add_h1(doc, '4  设计总结')

    add_h2(doc, '4.1 部署手册')

    add_h3(doc, '4.1.1 基础设施环境准备')
    add_body(doc, (
        '（1）安装 Docker Engine 24.0 及以上版本与 Docker Compose v2.20 及以上版本容器运行时；'
    ))
    add_body(doc, (
        '（2）获取项目源码后，在项目根目录复制环境模板并填写实际配置，生成 .env 环境文件；'
    ))
    add_body(doc, (
        '（3）执行 docker compose up -d 拉起基础设施容器组，包括 MySQL 8.0（端口3306）、'
        'Redis 7.0（端口6379）、MinIO（端口9000与9001）及 Hyperledger Fabric 测试网络；'
    ))
    add_body(doc, (
        '（4）数据库表结构由 Flyway 在应用启动时自动完成 V1 至 V13 迁移，无需手工执行 SQL 脚本。'
    ))

    add_h3(doc, '4.1.2 微服务编排与启动')
    add_body(doc, (
        '（1）后端服务：在 oaiss-chain-backend 目录执行 mvn clean package 完成打包，'
        '随后以生产配置启动可执行 JAR（默认端口8080）；'
    ))
    add_body(doc, (
        '（2）算法微服务：在 oaiss-chain-ml-service 目录创建 Python 3.11 虚拟环境并安装依赖，'
        '以 Uvicorn 启动 FastAPI 应用（端口8001）；'
    ))
    add_body(doc, (
        '（3）前端静态资源：在 oaiss-chain-frontend 目录执行 npm ci 与 npm run build 生成 dist 产物，'
        '由 Nginx 托管并配置 /api 反向代理至后端服务。'
    ))

    add_h3(doc, '4.1.3 访问与健康验证')
    add_body(doc, (
        '（1）浏览器访问系统门户地址进入登录页；'
    ))
    add_body(doc, (
        '（2）调用后端健康探针接口，返回 UP 表示服务正常；'
    ))
    add_body(doc, (
        '（3）访问算法微服务接口文档页确认算法服务就绪；'
    ))
    add_body(doc, (
        '（4）执行 docker compose ps 确认全部容器处于健康状态，即完成部署验收。'
    ))

    add_h2(doc, '4.2 用户操作手册')

    add_h3(doc, '4.2.1 账户登录与角色工作台')
    add_body(doc, (
        '用户访问系统门户后输入账号密码与图形验证码，登录成功后系统依据账户角色自动跳转至对应工作台：'
        '企业用户进入碳资产工作台，审核员进入审核工作台，第三方机构进入监管大屏，管理员进入系统管理后台。'
    ))

    add_h3(doc, '4.2.2 碳盘查填报与公式推导')
    add_body(doc, (
        '（1）企业用户进入碳核算管理下的排放填报，选择火力发电或电网行业模板；'
    ))
    add_body(doc, (
        '（2）按向导逐项录入工艺参数，右侧面板实时展示公式推导过程与中间结果；'
    ))
    add_body(doc, (
        '（3）核验排放总量无误后点击生成报告并签名，输入私钥保护口令完成数字签名；'
    ))
    add_body(doc, (
        '（4）签名成功提交后报告进入待审状态，可在报告列表跟踪审批进度。'
    ))

    add_h3(doc, '4.2.3 审核员预审与驳回流转')
    add_body(doc, (
        '（1）审核员登录后进入待审报告队列，认领报告并锁定；'
    ))
    add_body(doc, (
        '（2）逐项核验参数与上传凭证，参考 AI 预审诊断结论辅助判断；'
    ))
    add_body(doc, (
        '（3）核验无误点击审核通过并签发审核意见；如存疑则点击驳回，'
        '选择驳回类别并填写整改要求，报告自动退回企业补正。'
    ))

    add_h3(doc, '4.2.4 碳配额挂单与双向拍卖')
    add_body(doc, (
        '（1）企业进入碳交易市场，查看实时盘口深度与历史成交；'
    ))
    add_body(doc, (
        '（2）选择买入或卖出方向，录入限价与委托数量，系统自动冻结对应资金或配额；'
    ))
    add_body(doc, (
        '（3）挂单进入撮合队列，成交后可在我的交易页面查看成交回报与最新持仓。'
    ))

    add_h3(doc, '4.2.5 区块链存证查验与AI看板')
    add_body(doc, (
        '（1）在报告详情页点击区块链存证，查看交易哈希、区块高度与链上时间戳；'
    ))
    add_body(doc, (
        '（2）在 AI 智能分析菜单查看碳价预测走势、驱动因子归因与置信通道；'
    ))
    add_body(doc, (
        '（3）监管机构可在监管大屏查阅区域碳资产全景、企业异常诊断与合规偏离雷达图。'
    ))

    add_h2(doc, '4.3 致谢')
    add_body(doc, (
        '值此毕业设计完成之际，谨向所有给予我指导和帮助的老师、同学与家人致以最诚挚的感谢。'
    ))
    add_body(doc, (
        '首先，衷心感谢我的指导教师，在选题论证、技术选型、系统设计到文档撰写的每一个环节，'
        '老师都给予了悉心指导和耐心解惑。老师严谨务实的治学态度与深厚的工程实践底蕴，'
        '让我深刻体会到软件工程“规范与质量并重”的真谛，这些宝贵财富将使我受益终身。'
    ))
    add_body(doc, (
        '其次，感谢学院所有授课教师的辛勤栽培，是你们让我系统地掌握了从程序设计基础到'
        '企业级架构设计的完整知识体系，为本课题的顺利完成打下了坚实基础。'
    ))
    add_body(doc, (
        '同时，感谢并肩学习、共同进步的同学们，在讨论与协作中给予我的启发与鼓励。'
    ))
    add_body(doc, (
        '最后，感谢我的家人一直以来的理解与支持，是你们的默默付出让我能够心无旁骛地完成学业。'
    ))
    add_body(doc, (
        '这段毕业设计经历不仅让我完成了从学生到准工程师的思维蜕变，更坚定了我在软件工程道路上持续深耕的信念。'
        '再次向所有关心与帮助我的人表示最衷心的感谢。'
    ))

# ============================================================
# 参考文献
# ============================================================

def build_references(doc):
    add_page_break(doc)
    add_h1(doc, '参考文献')
    refs = [
        '[1] 全国环境管理标准化技术委员会. 工业企业温室气体排放核算和报告通则: GB/T 32150—2015[S]. 北京: 中国标准出版社, 2015.',
        '[2] 中华人民共和国生态环境部. 企业温室气体排放核算与报告指南 发电设施(2022年修订版)[S]. 北京: 生态环境部, 2022.',
        '[3] 郑林江, 郭诗怡, 柴毅, 等. 基于区块链的工业互联网碳资产可信交易机制[J]. 计算机集成制造系统, 2023, 29(6): 1872-1884.',
        '[4] 王伟, 张宁, 杜尔顺, 等. 区块链技术在能源互联网与碳交易中的应用框架与关键技术[J]. 中国电机工程学报, 2021, 41(7): 2320-2331.',
        '[5] 赵晨阳, 刘敏. 基于双向拍卖机制的区域碳配额分布式交易模型[J]. 电力系统自动化, 2023, 47(11): 54-63.',
        '[6] 刘洋, 孙宏斌. 基于智能合约的碳排放权穿透式监管体系研究[J]. 电力信息与通信技术, 2024, 22(2): 15-24.',
        '[7] 陈波, 李明. 基于 Spring Boot 与 Vue 架构的微服务低碳管理平台设计与实现[J]. 现代电子技术, 2022, 45(18): 123-128.',
        '[8] Taylor S J, Letham B. Forecasting at Scale[J]. The American Statistician, 2018, 72(1): 37-45.',
        '[9] Chen T, Guestrin C. XGBoost: A Scalable Tree Boosting System[C]//Proceedings of the 22nd ACM SIGKDD International Conference on Knowledge Discovery and Data Mining. New York: ACM, 2016: 785-794.',
        '[10] Androulaki E, Barger A, Bortnikov V, et al. Hyperledger Fabric: A Distributed Operating System for Permissioned Blockchains[C]//Proceedings of the Thirteenth EuroSys Conference. New York: ACM, 2018: 1-15.',
    ]
    for ref in refs:
        p = doc.add_paragraph(style='毕设正文')
        p.paragraph_format.first_line_indent = Pt(0)
        p.paragraph_format.left_indent = Pt(24)
        p.paragraph_format.line_spacing = 1.25
        r = p.add_run(ref)
        set_run_font(r, 10.5, False)

# ============================================================
# 目录重建
# ============================================================

TOC_ENTRIES = [
    (1, '1  设计任务', 1), (2, '1.1 项目背景', 1), (2, '1.2 项目范围', 2),
    (3, '1.2.1 用户认证与四角色权限管理模块', 2), (3, '1.2.2 国标碳核算与公式计算器模块', 3),
    (3, '1.2.3 报告审查驳回与区块链存证模块', 3), (3, '1.2.4 双向竞价拍卖与AI智能监管大盘模块', 4),
    (2, '1.3 项目风险分析', 5), (2, '1.4 任务分配', 6), (2, '1.5 项目所需资源', 6),
    (1, '2  设计思路与技术方案', 7), (2, '2.1 系统核心业务流程图', 7), (2, '2.2 系统用例图', 8),
    (2, '2.3 用例分析', 9), (2, '2.4 技术方案', 12),
    (3, '2.4.1 系统架构图', 12), (3, '2.4.2 数据库命名规则', 13), (3, '2.4.3 类命名规则', 13),
    (3, '2.4.4 方法、参数、成员变量、局部变量命名规则', 14), (3, '2.4.5 包名结构', 14),
    (1, '3  设计内容（过程）与说明', 15), (2, '3.1 数据库设计', 15),
    (3, '3.1.1 数据库物理模型', 15), (3, '3.1.2 数据表设计', 16),
    (2, '3.2 界面设计', 20), (3, '3.2.1 企业数据可视化驾驶舱', 20),
    (3, '3.2.2 碳核算填报与国标公式计算器', 21), (3, '3.2.3 碳配额双向拍卖与P2P交易市场', 22),
    (3, '3.2.4 碳币账户与信誉评分', 23), (3, '3.2.5 区块链浏览器与链上存证核验', 24),
    (3, '3.2.6 AI智能预测与异常检测', 25), (3, '3.2.7 审核端待审报告与审核工作台', 26),
    (3, '3.2.8 监管端第三方监管面板', 27), (3, '3.2.9 管理端平台统计与用户管理', 28),
    (2, '3.3 类设计', 23), (3, '3.3.1 碳核算核心业务类设计', 23),
    (3, '3.3.2 撮合引擎与区块链适配类设计', 24), (3, '3.3.3 全系统工程架构包图', 24),
    (2, '3.4 顺序图设计', 25), (3, '3.4.1 碳核算填报与公式推导顺序图', 25),
    (3, '3.4.2 数字签名与链上存证顺序图', 25), (3, '3.4.3 双向拍卖撮合与DVP结算顺序图', 26),
    (3, '3.4.4 AI预测微服务调度顺序图', 26),
    (2, '3.5 API接口设计', 27),
    (3, '3.5.1 用户认证模块接口', 27), (3, '3.5.2 碳核算模块接口', 27),
    (3, '3.5.3 拍卖交易与链上核验模块接口', 28),
    (2, '3.6 项目测试', 28),
    (3, '3.6.1 单元测试计划', 28), (3, '3.6.2 接口测试计划', 29), (3, '3.6.3 集成测试计划', 29),
    (3, '3.6.4 单元测试报告', 30), (3, '3.6.5 接口测试报告', 30), (3, '3.6.6 集成测试报告', 31),
    (1, '4  设计总结', 32), (2, '4.1 部署手册', 32),
    (3, '4.1.1 基础设施环境准备', 32), (3, '4.1.2 微服务编排与启动', 32), (3, '4.1.3 访问与健康验证', 33),
    (2, '4.2 用户操作手册', 33), (3, '4.2.1 账户登录与角色工作台', 33),
    (3, '4.2.2 碳盘查填报与公式推导', 33), (3, '4.2.3 审核员预审与驳回流转', 34),
    (3, '4.2.4 碳配额挂单与双向拍卖', 34), (3, '4.2.5 区块链存证查验与AI看板', 34),
    (2, '4.3 致谢', 35),
    (1, '参考文献', 36),
]

TOC_STYLE_MAP = {1: 'toc 1', 2: 'toc 2', 3: 'toc 3'}

def rebuild_toc(doc):
    """重建目录：删除旧目录域与全部缓存条目，重新生成带点线制表位的目录及新 TOC 域"""
    body = doc.element.body
    children = list(body)

    start_idx = None
    end_idx = None
    for i, child in enumerate(children):
        if not child.tag.endswith('}p'):
            continue
        xml = child.xml
        has_toc_style = bool(re.search(r'<w:pStyle w:val="(14|16|8)"/>', xml))
        if start_idx is None and has_toc_style and i > 6:
            start_idx = i
        if start_idx is not None and i > start_idx and not has_toc_style and 'w:fldCharType="end"' in xml:
            end_idx = i
            break

    if start_idx is None:
        print("  [警告] 未定位到旧目录条目，跳过目录重建")
        return

    if end_idx is None:
        end_idx = start_idx
        for i in range(start_idx, len(children)):
            if children[i].tag.endswith('}p'):
                end_idx = i
        print("  [提示] 未找到域结束段，采用条目末尾为边界")

    print(f"  TOC 重建: 替换元素索引 {start_idx}..{end_idx}（共 {end_idx - start_idx + 1} 个）")

    anchor = children[start_idx]

    # 构造新元素序列
    new_elems = []

    # 域开始段
    p_b = doc.add_paragraph()
    p_b.style = doc.styles['Normal']
    p_b.paragraph_format.space_before = Pt(0)
    p_b.paragraph_format.space_after = Pt(0)
    p_b.paragraph_format.first_line_indent = Pt(0)
    r_b = p_b.add_run()
    r_b._r.append(parse_xml('<w:fldChar %s w:fldCharType="begin"/>' % nsdecls('w')))
    r_b._r.append(parse_xml('<w:instrText %s xml:space="preserve"> TOC \\o "1-3" \\h \\z \\u </w:instrText>' % nsdecls('w')))
    r_b._r.append(parse_xml('<w:fldChar %s w:fldCharType="separate"/>' % nsdecls('w')))
    p_b._p.getparent().remove(p_b._p)
    new_elems.append(p_b._p)

    # 目录条目
    for lvl, title, page in TOC_ENTRIES:
        p = doc.add_paragraph()
        p.style = doc.styles[TOC_STYLE_MAP[lvl]]
        pPr = p._p.get_or_add_pPr()
        pPr.append(parse_xml('<w:tabs %s><w:tab w:val="right" w:leader="dot" w:pos="9072"/></w:tabs>' % nsdecls('w')))
        p.paragraph_format.first_line_indent = Pt(0)
        p.paragraph_format.space_before = Pt(0)
        p.paragraph_format.space_after = Pt(0)
        r1 = p.add_run(title)
        set_run_font(r1, 10.5, lvl == 1)
        r2 = p.add_run('\t')
        set_run_font(r2, 10.5, lvl == 1)
        r3 = p.add_run(str(page))
        set_run_font(r3, 10.5, lvl == 1)
        p._p.getparent().remove(p._p)
        new_elems.append(p._p)

    # 域结束段
    p_e = doc.add_paragraph()
    p_e.style = doc.styles['Normal']
    p_e.paragraph_format.space_before = Pt(0)
    p_e.paragraph_format.space_after = Pt(0)
    p_e.paragraph_format.first_line_indent = Pt(0)
    r_e = p_e.add_run()
    r_e._r.append(parse_xml('<w:fldChar %s w:fldCharType="end"/>' % nsdecls('w')))
    p_e._p.getparent().remove(p_e._p)
    new_elems.append(p_e._p)

    # 插入到 anchor 之前
    for el in new_elems:
        anchor.addprevious(el)

    # 删除旧元素（start_idx..end_idx）
    for i in range(end_idx, start_idx - 1, -1):
        body.remove(children[i])

# ============================================================
# 主流程
# ============================================================

def main():
    print("=== 毕业设计成果说明书全量重建 ===")
    doc = docx.Document(DOCX_PATH)

    # 1. 删除旧正文（从第一个 Heading 1 “1  设计任务”起至末尾，保留最终 body 级 sectPr）
    body = doc.element.body
    children = list(body)
    start_del = None
    for i, child in enumerate(children):
        if not child.tag.endswith('}p'):
            continue
        p_obj = Paragraph(child, doc)
        if p_obj.style.name == 'Heading 1' and '设计任务' in p_obj.text:
            start_del = i
            break

    if start_del is None:
        print("[错误] 未定位到正文起始点（Heading 1 设计任务）")
        return

    sectPr = body.find(qn('w:sectPr'))
    del_count = 0
    for i in range(len(children) - 1, start_del - 1, -1):
        el = children[i]
        if el is sectPr:
            continue
        body.remove(el)
        del_count += 1
    print(f"  已清除旧正文元素 {del_count} 个（自索引 {start_del}）")

    # 2. 重建目录
    rebuild_toc(doc)

    # 3. 构建全新正文
    print("  正在生成第 1 章 设计任务...")
    build_chapter_1(doc)
    print("  正在生成第 2 章 设计思路与技术方案...")
    build_chapter_2(doc)
    print("  正在生成第 3 章 设计内容（过程）与说明...")
    build_chapter_3(doc)
    print("  正在生成第 4 章 设计总结...")
    build_chapter_4(doc)
    print("  正在生成参考文献...")
    build_references(doc)

    # 4. 保存
    tmp = DOCX_PATH + '.tmp'
    doc.save(tmp)
    os.replace(tmp, DOCX_PATH)
    print(f"毕业设计成果说明书全量重建完成: {DOCX_PATH}")

if __name__ == '__main__':
    main()
