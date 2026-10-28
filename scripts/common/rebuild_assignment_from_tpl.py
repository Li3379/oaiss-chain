#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OAISS CHAIN - 毕业设计任务书重建（从纯净官方模板 1:1 复刻）

流程：复制官方模板 → 仅填充内容字段（保留校徽页眉 / 22pt 标题 / Wingdings 复选符号 / 表格边框）
单元格段落格式对齐模板：段落样式「首行缩进:  2 字符」+ 行距 360 exact + 首行缩进 480 twips + 宋体 12pt
"""

import os
import shutil
import docx
from docx.shared import Pt
from docx.oxml import parse_xml
from docx.oxml.ns import qn, nsdecls

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '../..'))
TPL = 'C:/Users/LiShuai/Desktop/软件技术专业2026届学生毕业设计文档模版/软件学院2026届学生毕业设计任务书（学号姓名）_模板.docx'.replace('软件技术专业2026届学生', '软件技术专业2026届')
TPL = 'C:/Users/LiShuai/Desktop/软件技术专业2026届毕业设计文档模版/软件学院2026届学生毕业设计任务书（学号姓名）_模板.docx'
OUT = os.path.join(ROOT, 'docs/软件学院2026届学生毕业设计任务书_OAISS_CHAIN双碳链动系统.docx')

TOPIC = '双碳链动系统 (OAISS CHAIN) 的设计与实现——碳核算、区块链存证与智能交易系统'
NAME = '李帅'
STU_ID = '245102030510'
CLASS_NAME = '软件技术2404'

GOAL_PARAS = [
    '随着国家“双碳”（碳达峰、碳中和）战略的纵深推进，重点排放单位的温室气体核算合规性、履约交易透明度及碳资产流动性面临严峻挑战。'
    '针对传统碳管理中核算标准离散、数据易篡改、多方互信成本高以及配额履约缺少预测预警机制等行业痛点，'
    '本项目自主设计并实现一套面向企业碳排放全生命周期的“双碳链动系统（OAISS CHAIN）”。'
    '本项目要求综合运用 Java 17、Spring Boot 3.2、Vue 3.5、FastAPI、MySQL 8.0、Redis 7.0、'
    'Hyperledger Fabric 2.5 联盟区块链、数字签名及时间序列预测算法等前沿软件技术，构建高内聚、低耦合的企业级应用。具体设计目标如下：',
    '（1）素质目标：树立严谨的软件工程规范意识、数据主权与合规安全意识，培养科技向善、服务国家绿色低碳战略转型的职业道德与严谨工匠精神。',
    '（2）知识目标：深入理解国家标准 GB/T 32150 碳核算推导规则；掌握基于无状态 JWT 与 RBAC 的四角色权限体系；'
    '掌握联盟链智能合约链码生命周期与两阶段背书提交机制；掌握 Prophet 时序分解与树模型特征归因算法原理。',
    '（3）能力目标：具备独立搭建大型前后端分离架构的能力，实现基于 Spring Boot 的 22 个 Controller 与 143 个 REST API；'
    '熟练构建 Vue 3 与 TypeScript 现代化前端（22 个核心交互模块与 1,430 项国际化词条）；'
    '熟练使用 Python FastAPI 部署 AI 算法微服务；具备 Docker 容器化集成部署及系统全场景自动化测试与验收能力。',
]

TASK_PARAS = [
    '本项目立足于碳资产全生命周期管理，划分“企业主体、审核核查员、第三方监管机构、系统管理员”四大角色。'
    '个人在团队中全面承担核心业务架构、后端服务、前端交互及跨服务集成任务。具体设计任务与要求如下：',
    '（1）系统需求与用例建模：深度调研火力发电与电网输配电企业碳盘查规则，完成系统功能性与非功能性需求建模，编写完备的用例规约与数据流图。',
    '（2）数据库与安全架构设计：设计符合范式规范的 24 张核心业务数据表，建立索引优化与唯一性约束；'
    '实现基于 RSA 2048 与 AES-GCM 的信封加密防篡改机制。',
    '（3）双碳核心业务模块实现：实现火力发电 25 项与电网 9 项参数的国标自动化碳核算推导与公式计算器；'
    '实现碳报告审查流转工作台与结构化驳回流水线；构建连续双向拍卖（Double Auction）自动撮合引擎与 P2P 协商交易池；'
    '集成 Hyperledger Fabric 2.5 链码合约实现报告哈希与交易凭据的可信上链；'
    '对接基于 FastAPI 与 Prophet、XGBoost 的碳配额价格趋势预测与异常检测微服务。',
    '（4）前端交互与工程化交付：使用 Vue 3、Pinia 与 ECharts 6 构建覆盖企业端、审核端、监管端与管理端的'
    '四类角色工作台（含数据可视化驾驶舱、区块链浏览器、AI 智能预测看板与多维监管面板），'
    '全站遵循 ELI5 易用性设计准则并实现中英双语国际化；完成 120 项以上自动化测试用例覆盖与交付部署手册编制。',
]

STAGE_TASKS = [
    ('查阅国家双碳政策、GB/T 32150 标准及区块链技术，完成选题论证与开题调研报告', '确定选题与开题报告'),
    ('开展控排企业四角色用例建模与功能边界划分，整理系统功能与非功能需求规格说明书', '需求规格说明书'),
    ('设计微服务六边形架构，完成 24 张业务表 ER 建模、143 个 REST 接口契约与 UI 高保真原型', '系统设计与架构规格书'),
    ('编码实现后端 22 控制器、前端 22 模块、Fabric Go 链码合约与 FastAPI 算法微服务', '全套工程源代码及脚本'),
    ('执行全栈自动化测试（2350 个单测、143 个接口冒烟与 Playwright 端到端巡检），完成中期检查', '测试报告与中期检查表'),
    ('按照软件学院官方规范撰写、修正与排版毕业设计成果文档（设计说明书），完成学术查重', '毕业设计成果说明书定稿'),
    ('提炼工程技术亮点，制作高质量答辩演示文稿，完成全业务场景端到端真实操作演示演练', '答辩演示 PPT 与演练视频'),
    ('汇总全套毕业设计归档成果包（任务书、说明书、源码、SQL 脚本、演示视频）提交学院系统归档', '全套毕业设计归档成果包'),
]

EXPECTED_PARAS = [
    '成果表现形式',
    '（1）《双碳链动系统 (OAISS CHAIN) 的设计与实现》毕业设计任务书（完整填报、签字齐全）；',
    '（2）《双碳链动系统 (OAISS CHAIN) 的设计与实现》毕业设计成果设计说明书（企软方向，字数达标、结构严谨、图表完备）；',
    '（3）OAISS CHAIN 系统答辩演示文稿（PPT 格式，涵盖选题背景、架构创新、核心模块与演示回放）；',
    '（4）系统全业务场景端到端实机操作高清演示视频（MP4 格式，涵盖四角色协同与链上存证）；',
    '（5）OAISS CHAIN 全套项目工程源码包（Java 后端、Vue 3 前端、Python 算法微服务、Fabric Go 链码）及 MySQL 8.0 建库脚本；',
    '（6）权威学术查重检测报告（知网或维普，查重率低于学校规定红线）。',
    '成果质量要求',
    '系统设计完全符合软件工程标准与企业级微服务规范，代码逻辑严谨、模块低耦合高内聚；数据库实体与索引优化规范；'
    '系统在 Docker 容器化编排环境下实现一键启动与稳定可靠运行。',
]

def make_run(p, text, size=12):
    r = p.add_run(text)
    r.font.name = 'Times New Roman'
    rPr = r._r.get_or_add_rPr()
    rFonts = parse_xml(f'<w:rFonts {nsdecls("w")} w:ascii="Times New Roman" w:hAnsi="Times New Roman" w:eastAsia="宋体" w:cs="Times New Roman"/>')
    rPr.insert(0, rFonts)
    r.font.size = Pt(size)
    return r

def fill_cell_simple(cell, text):
    """填充单行值单元格（保持模板 12pt 宋体，无高亮）"""
    for p in cell.paragraphs[1:]:
        p._p.getparent().remove(p._p)
    p = cell.paragraphs[0]
    for r in list(p.runs):
        r._r.getparent().remove(r._r)
    make_run(p, text, 12)

def fill_cell_paras(doc, cell, paras, style_name='首行缩进:  2 字符'):
    """填充多段落单元格：使用模板段落样式 + 行距 360 exact + 首行缩进 480"""
    for p in cell.paragraphs[1:]:
        p._p.getparent().remove(p._p)
    first_p = cell.paragraphs[0]
    for r in list(first_p.runs):
        r._r.getparent().remove(r._r)

    target_ps = []
    # 复用第一段，其余新建
    target_ps.append(first_p)
    for _ in range(len(paras) - 1):
        target_ps.append(cell.add_paragraph())

    for p, text in zip(target_ps, paras):
        try:
            p.style = doc.styles[style_name]
        except KeyError:
            pass
        pPr = p._p.get_or_add_pPr()
        for tag in ('w:spacing', 'w:ind'):
            for el in pPr.findall(qn(tag)):
                pPr.remove(el)
        pPr.append(parse_xml(f'<w:spacing {nsdecls("w")} w:line="360" w:lineRule="exact"/>'))
        pPr.append(parse_xml(f'<w:ind {nsdecls("w")} w:firstLine="480"/>'))
        for r in list(p.runs):
            r._r.getparent().remove(r._r)
        make_run(p, text, 12)

def build():
    shutil.copyfile(TPL, OUT)
    print(f'已复制官方模板 -> {OUT}')

    doc = docx.Document(OUT)
    t0 = doc.tables[0]
    t1 = doc.tables[1]

    # 表0 基础信息
    fill_cell_simple(t0.rows[0].cells[3], NAME)         # 姓名
    fill_cell_simple(t0.rows[1].cells[1], CLASS_NAME)   # 班级名称
    fill_cell_simple(t0.rows[1].cells[3], STU_ID)       # 学号
    fill_cell_simple(t0.rows[2].cells[1], TOPIC)        # 选题名称
    # 选题类型（R3）：模板已勾选 ☑产品设计类（Wingdings 00FE），保持不变

    # 表0 设计目标 / 设计任务
    fill_cell_paras(doc, t0.rows[4].cells[1], GOAL_PARAS)
    fill_cell_paras(doc, t0.rows[5].cells[1], TASK_PARAS)

    # 表1 进程表：8 阶段（列3=设计任务，列9=阶段成果）
    for idx, (work, deliverable) in enumerate(STAGE_TASKS):
        row = t1.rows[idx + 1]
        fill_cell_simple(row.cells[3], work)
        fill_cell_simple(row.cells[9], deliverable)

    # 表1 预期成果（行9，列2）
    fill_cell_paras(doc, t1.rows[9].cells[2], EXPECTED_PARAS)

    tmp = OUT + '.tmp'
    doc.save(tmp)
    os.replace(tmp, OUT)
    print(f'任务书重建完成: {OUT}')

if __name__ == '__main__':
    build()
