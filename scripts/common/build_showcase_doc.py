#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OAISS CHAIN - 毕业设计成果展示文档生成（依据「毕业设计成果展示_模板」1:1 样式还原）

流程：复制官方模板 → 清空模板示例内容（保留样式定义与页面设置）→ 填充项目真实模块内容与实机截图
版式：沿用模板样式（毕设正文 / 毕设三级标题 / 毕设图表名称）+ 仿宋字体 + 16cm 通栏截图 + 居中图题
"""

import os
import shutil
import re
import docx
from docx.shared import Pt, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import parse_xml
from docx.oxml.ns import qn, nsdecls

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '../..'))
TPL = os.path.join(ROOT, '.doc-templates/软件学院2026届-毕业设计成果展示_模板.docx')
OUT = os.path.join(ROOT, 'docs/软件学院2026届学生毕业设计成果展示_OAISS_CHAIN双碳链动系统.docx')
SHOT_DIR = os.path.join(ROOT, 'reports')

CN_FONT = '仿宋'

SECTIONS = [
    {
        'title': '1 企业数据可视化驾驶舱',
        'desc': '企业用户登录后进入数据可视化驾驶舱，一屏总览本月碳排放、履约进度与信用状况；'
                '页面顶部展示 AI 执行摘要晨报，自动给出当前碳价位置、履约窗口倒计时与量化建议，'
                '并支持按资产编号、报告类型、核算周期等条件组合查询核算报告，如下图所示。',
        'img': 'role-dashboard.png',
        'cap': '图1 企业数据可视化驾驶舱页面',
    },
    {
        'title': '2 碳核算填报与报告提交',
        'desc': '在碳核算填报页选择火力发电或电网行业模板，录入核算周期与活动数据，'
                '系统按 GB/T 32150 国标公式自动计算排放总量并生成防伪签名，'
                '确认无误后一键提交进入审核流程，如下图所示。',
        'img': 'role-carbon-upload.png',
        'cap': '图2 碳核算填报与报告提交页面',
    },
    {
        'title': '3 国标碳核算公式计算器',
        'desc': '公式计算器支持火力发电 25 项与电网 9 项参数的逐项录入与实时推导，'
                '右侧面板同步展示公式推导过程与中间计算结果，底部汇总排放总量，'
                '可保存为草稿或直接生成正式报告，如下图所示。',
        'img': 'role-carbon-formula.png',
        'cap': '图3 国标碳核算公式计算器页面',
    },
    {
        'title': '4 碳配额双向拍卖交易市场',
        'desc': '企业进入碳交易（双向拍卖）市场，按最优价格分批挂出买单或卖单，'
                '系统自动冻结对应碳币资金或碳配额并进入撮合队列；'
                '页面提供全部挂单、我的挂单与撮合结果三个视图，实时跟踪挂单状态与成交记录，如下图所示。',
        'img': 'role-trading-market.png',
        'cap': '图4 碳配额双向拍卖交易市场页面',
    },
    {
        'title': '5 P2P 点对点交易与订单管理',
        'desc': '除连续竞价外，企业还可通过 P2P 交易页与供应链上下游企业点对点议价成交，'
                '并在订单管理中查看全部交易订单的状态、金额与对手方信息，如下图所示。',
        'img': 'role-trading-p2p.png',
        'cap': '图5 P2P 点对点交易页面',
    },
    {
        'title': '6 碳币账户与信誉评分',
        'desc': '碳币账户页展示企业碳币余额、收支流水与充值划转记录；'
                '信誉评分页展示企业碳信用综合得分、等级评定与信用事件明细，'
                '信用评分与交易、履约行为实时联动，如下图所示。',
        'img': 'role-carbon-coin.png',
        'cap': '图6 碳币账户页面',
    },
    {
        'title': '7 区块链浏览器与链上存证核验',
        'desc': '区块链浏览器实时展示 Fabric 联盟链连接状态（运行模式 FABRIC、通道 mychannel、节点数）；'
                '支持按交易哈希直接查询链上交易详情，并以「最新区块」与「交易记录」双视图呈现'
                '区块高度、区块哈希、交易数量与时间戳等账本数据，实现存证记录的穿透核验，如下图所示。',
        'img': 'role-blockchain.png',
        'cap': '图7 区块链浏览器与链上存证核验页面',
    },
    {
        'title': '8 碳中和项目与排放数据管理',
        'desc': '碳中和项目页支持核证自愿减排量（CCER）项目的申报、核证与注销全生命周期管理；'
                '排放数据页按核算周期汇总展示企业历史排放数据与同比趋势，如下图所示。',
        'img': 'role-carbon-neutral.png',
        'cap': '图8 碳中和项目管理页面',
    },
    {
        'title': '9 AI 智能预测：碳价预测与企业推理',
        'desc': 'AI 智能预测包含两大分析视图：碳市场量化推演驾驶舱基于 Prophet 时序模型输出'
                '未来价格预测曲线与 95% 置信通道，并给出 XGBoost 特征归因与建仓策略建议；'
                '企业碳合规体检基于 Isolation Forest 输出六维偏离雷达图与三阶段降碳路径，如下图所示。',
        'img': 'role-market-prediction.png',
        'cap': '图9 AI 碳价预测与驱动因子归因页面',
    },
    {
        'title': '10 审核端：待审报告与审核工作台',
        'desc': '审核员登录后进入审核材料工作台，页面顶部展示个人执业资格证（证书编号与所属组织）'
                '与审核统计（总审核数、通过数、驳回数、通过率）；'
                '待审报告列表按报告编号、企业名称、报告标题、总排放量、状态、验签结果与创建时间展示，'
                '支持逐条进入审核并给出通过或结构化驳回意见，如下图所示。',
        'img': 'role-reviewer-audit.png',
        'cap': '图10 审核端待审报告列表页面',
    },
    {
        'title': '11 监管端：第三方监管面板',
        'desc': '第三方监管机构登录后进入监管中心，页面展示监管机构信息（机构名称、权限级别、地址与联系人），'
                '并按总报告数、待审核、已通过、已拒绝与审核通过率汇总全平台数据；'
                '碳排放报告列表穿透展示各企业报告的核算周期、排放总量与链上存证状态（已上链），'
                '支持按企业、关键词与状态组合筛选，如下图所示。',
        'img': 'role-thirdparty-monitor.png',
        'cap': '图11 第三方监管面板页面',
    },
    {
        'title': '12 管理端：平台统计与用户管理',
        'desc': '管理员登录后可在数据管理页查看全平台规模统计（总用户数、企业数量、审核员数量、第三方数量）'
                '与用户类型分布图；在用户管理中按用户类型与状态筛选账号，'
                '并对账号执行启用与禁用操作，实现全平台账号的集中治理，如下图所示。',
        'img': 'role-admin-statistics.png',
        'cap': '图12 管理端平台规模统计页面',
    },
]

def set_font(run, size=None, bold=False, center=False):
    run.font.name = CN_FONT
    rPr = run._r.get_or_add_rPr()
    rFonts = rPr.find(qn('w:rFonts'))
    if rFonts is None:
        rFonts = parse_xml(f'<w:rFonts {nsdecls("w")}/>')
        rPr.append(rFonts)
    for attr in ('w:ascii', 'w:hAnsi', 'w:eastAsia', 'w:cs'):
        rFonts.set(qn(attr), CN_FONT)
    if size:
        run.font.size = Pt(size)
    run.font.bold = bold
    run.font.color.rgb = RGBColor(0, 0, 0)

def build():
    # 1. 复制模板（先复制文档）
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    shutil.copyfile(TPL, OUT)
    print(f'已复制模板 -> {OUT}')

    doc = docx.Document(OUT)

    # 2. 清空模板示例内容（保留最终 sectPr 与样式定义）
    body = doc.element.body
    children = list(body)
    removed = 0
    for el in children:
        if el.tag.endswith('}sectPr'):
            continue
        body.remove(el)
        removed += 1
    print(f'已清空模板示例内容: {removed} 个元素（样式定义与页面设置保留）')

    # 3. 填充项目内容
    # 3.1 标题
    p_title = doc.add_paragraph(style=doc.styles['毕设正文'])
    p_title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_title.paragraph_format.first_line_indent = Pt(0)
    r = p_title.add_run('《双碳链动系统（OAISS CHAIN）》——成果展示')
    set_font(r, 16, True)

    # 3.2 各模块章节
    for sec in SECTIONS:
        # 三级标题
        p_h = doc.add_paragraph(style=doc.styles['毕设三级标题'])
        r_h = p_h.add_run(sec['title'])
        set_font(r_h)

        # 操作说明
        p_d = doc.add_paragraph(style=doc.styles['毕设正文'])
        r_d = p_d.add_run(sec['desc'])
        set_font(r_d)

        # 截图（16cm 通栏）
        img_path = os.path.join(SHOT_DIR, sec['img'])
        if os.path.exists(img_path):
            p_img = doc.add_paragraph()
            p_img.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p_img.paragraph_format.first_line_indent = Pt(0)
            p_img.paragraph_format.line_spacing = 1.0  # 单倍行距（auto）：避免固定行距裁切内嵌图片
            p_img.paragraph_format.space_before = Pt(6)
            p_img.paragraph_format.space_after = Pt(2)
            r_img = p_img.add_run()
            r_img.add_picture(img_path, width=Cm(16.0))
        else:
            print(f'  [警告] 截图缺失: {img_path}')

        # 图题
        p_cap = doc.add_paragraph(style=doc.styles['毕设图表名称'])
        p_cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p_cap.paragraph_format.first_line_indent = Pt(0)
        r_cap = p_cap.add_run(sec['cap'])
        set_font(r_cap)

    # 4. 保存
    tmp = OUT + '.tmp'
    doc.save(tmp)
    os.replace(tmp, OUT)
    print(f'成果展示文档生成完成: {OUT}')

if __name__ == '__main__':
    build()
