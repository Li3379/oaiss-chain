#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OAISS CHAIN - 软件学院2026届学生毕业设计任务书深度重构脚本
精确匹配表格行列结构，彻底清除健康档案残留，深度写全双碳系统目标、任务、8大阶段与预期成果
"""

import os
import docx
from docx.shared import Pt
from docx.oxml import parse_xml
from docx.oxml.ns import nsdecls

DOCX_PATH = os.path.abspath(os.path.join(os.path.dirname(__file__), '../../docs/软件学院2026届学生毕业设计任务书_OAISS_CHAIN双碳链动系统.docx'))

GOAL_TEXT = (
    "随着国家“双碳”（碳达峰、碳中和）战略的纵深推进，重点排放单位的温室气体核算合规性、履约交易透明度及碳资产流动性面临严峻挑战。"
    "针对传统碳管理中核算标准离散、数据易篡改、多方互信成本高以及配额履约缺少预测预警机制等行业痛点，本项目自主设计并实现一套面向企业碳排放全生命周期的“双碳链动系统 (OAISS CHAIN)”。\n"
    "本项目要求综合运用 Java 17、Spring Boot 3.2、Vue 3.5、FastAPI、MySQL 8.0、Redis 7.0、Hyperledger Fabric 2.5 联盟区块链、数字签名及时间序列预测算法等前沿软件技术，构建高内聚、低耦合的企业级应用。具体设计目标如下：\n"
    "1. 素质目标：树立严谨的软件工程规范意识、数据主权与合规安全意识，培养科技向善、服务国家绿色低碳战略转型的职业道德与严谨工匠精神。\n"
    "2. 知识目标：深入理解国家标准 GB/T 32150 碳核算推导规则；掌握基于无状态 JWT 与 RBAC 的四角色权限体系；掌握联盟链智能合约链码生命周期与两阶段背书提交机制；掌握 Prophet 时序分解与树模型特征归因算法原理。\n"
    "3. 能力目标：具备独立搭建大型前后端分离架构的能力，实现基于 Spring Boot 的 22 个 Controller 与 143+ REST API；熟练构建 Vue 3 + TypeScript 现代化前端（22 个核心交互模块与 1,430+ 国际化词条）；熟练使用 Python FastAPI 部署 AI 算法微服务；具备 Docker 容器化集成部署及系统全场景自动化测试与验收能力。"
)

TASK_TEXT = (
    "本项目立足于碳资产全生命周期管理，划分“企业主体、审核核查员、第三方监管机构、系统管理员”四大角色。个人在团队中全面承担核心业务架构、后端服务、前端交互及跨服务集成任务。具体设计任务与要求如下：\n"
    "1. 系统需求与用例建模：深度调研火力发电与电网输配电企业碳盘查规则，完成系统功能性与非功能性需求建模，编写完备的用例规约与数据流图。\n"
    "2. 数据库与安全架构设计：设计符合范式规范的 24 张核心业务数据表，建立索引优化与唯一性约束；实现基于 RSA 2048 与 AES-GCM 的信封加密防篡改机制。\n"
    "3. 双碳核心业务模块实现：\n"
    "   (1) 实现火力发电 25 项与电网 9 项参数的国标自动化碳核算推导与公式计算器，提供前台实时试算与后端防篡改双重校验；\n"
    "   (2) 实现碳报告审查流转工作台、多级复核流程与结构化驳回流水线（包含驳回大类与整改意见闭环）；\n"
    "   (3) 构建连续双向拍卖（Double Auction）自动撮合引擎与企业级 P2P 协商交易池，保证撮合结算强一致性；\n"
    "   (4) 集成 Hyperledger Fabric 2.5 链码合约，实现报告哈希与交易凭据的可信上链及历史穿透溯源；\n"
    "   (5) 对接基于 FastAPI + Prophet / XGBoost 的碳配额价格趋势预测与企业能耗异常检测微服务。\n"
    "4. 前端交互与工程化交付：使用 Vue 3 + Pinia + ECharts 6 构建企业数据驾驶舱与多维监管大屏；完成 120+ 自动化测试用例覆盖与交付部署手册编制。"
)

# 8 大阶段任务与成果深度写全
STAGE_TASKS = [
    # 行 1: 阶段 1
    ("查阅国家双碳政策、GB/T 32150标准及区块链技术，完成选题论证与开题调研报告", "确定选题与开题报告"),
    # 行 2: 阶段 2
    ("开展控排企业四角色用例建模与功能边界划分，整理系统功能与非功能需求规格说明书", "需求规格说明书"),
    # 行 3: 阶段 3
    ("设计微服务六边形架构，完成 24 张业务表 ER 建模、143 个 REST 接口契约与 UI 高保真原型", "系统设计与架构规格书"),
    # 行 4: 阶段 4
    ("编码实现后端 22 控制器、前端 22 模块、Fabric Go 链码合约与 FastAPI 算法微服务", "全套工程源代码及脚本"),
    # 行 5: 阶段 5
    ("执行全栈自动化测试（2350个单测、143个接口冒烟与Playwright端到端巡检），完成中期检查", "测试报告与中期检查表"),
    # 行 6: 阶段 6
    ("按照软件学院官方规范撰写、修正与排版毕业设计成果文档（设计说明书），完成学术查重", "毕业设计成果说明书定稿"),
    # 行 7: 阶段 7
    ("提炼工程技术亮点，制作高质量答辩演示文稿，完成全业务场景端到端真实操作演示演练", "答辩演示 PPT 与演练视频"),
    # 行 8: 阶段 8
    ("汇总全套毕业设计归档成果包（任务书、说明书、源码、SQL脚本、演示视频）提交学院系统归档", "全套毕业设计归档成果包")
]

EXPECTED_OUTPUT = (
    "1. 成果表现形式：\n"
    "   (1)《双碳链动系统 (OAISS CHAIN) 的设计与实现》毕业设计任务书（完整填报、签字齐全）；\n"
    "   (2)《双碳链动系统 (OAISS CHAIN) 的设计与实现》毕业设计成果设计说明书（企软方向，字数达标、结构严谨、图表完备）；\n"
    "   (3) OAISS CHAIN 系统答辩演示文稿（PPT 格式，涵盖选题背景、架构创新、核心模块与演示回放）；\n"
    "   (4) 系统全业务场景端到端实机操作高清演示视频（MP4 格式，涵盖四角色协同与链上存证）；\n"
    "   (5) OAISS CHAIN 全套项目工程源码包（Java 后端、Vue 3 前端、Python 算法微服务、Fabric Go 链码）及 MySQL 8.0 DDL 初始化脚本；\n"
    "   (6) 权威学术查重检测报告（知网或维普，查重率低于学校规定红线）。\n"
    "2. 成果质量要求：\n"
    "   系统设计完全符合软件工程标准与企业级微服务规范，代码逻辑严谨、模块低耦合高内聚；数据库实体与索引优化规范；系统在 Docker 容器化编排环境下实现一键启动与稳定可靠运行。"
)

def update_assignment():
    if not os.path.exists(DOCX_PATH):
        print(f"找不到任务书文件: {DOCX_PATH}")
        return

    doc = docx.Document(DOCX_PATH)
    print("正在更新毕业设计任务书...")

    # 表 0: 更新设计目标 (行 4) 与 设计任务 (行 5)
    t0 = doc.tables[0]
    
    # 行 4: 设计目标
    cell_goal = t0.rows[4].cells[1]
    cell_goal.text = ""
    p_goal = cell_goal.paragraphs[0]
    p_goal.paragraph_format.first_line_indent = Pt(0)
    p_goal.paragraph_format.line_spacing = 1.25
    r_goal = p_goal.add_run(GOAL_TEXT)
    r_goal.font.name = "Times New Roman"
    r_goal._r.get_or_add_rPr().append(parse_xml(f'<w:rFonts {nsdecls("w")} w:eastAsia="SimSun"/>'))
    r_goal.font.size = Pt(10.5)

    # 行 5: 设计任务
    cell_task = t0.rows[5].cells[1]
    cell_task.text = ""
    p_task = cell_task.paragraphs[0]
    p_task.paragraph_format.first_line_indent = Pt(0)
    p_task.paragraph_format.line_spacing = 1.25
    r_task = p_task.add_run(TASK_TEXT)
    r_task.font.name = "Times New Roman"
    r_task._r.get_or_add_rPr().append(parse_xml(f'<w:rFonts {nsdecls("w")} w:eastAsia="SimSun"/>'))
    r_task.font.size = Pt(10.5)

    # 表 1: 更新 8 大阶段与预期成果 (行 9)
    t1 = doc.tables[1]
    
    # 阶段 1 到 8 (行 1 到 8)
    for idx, (work_desc, deliverable) in enumerate(STAGE_TASKS):
        row = t1.rows[idx + 1]
        
        # 设计任务单元格 (列 3)
        cell_w = row.cells[3]
        cell_w.text = ""
        pw = cell_w.paragraphs[0]
        rw = pw.add_run(work_desc)
        rw.font.name = "Times New Roman"
        rw._r.get_or_add_rPr().append(parse_xml(f'<w:rFonts {nsdecls("w")} w:eastAsia="SimSun"/>'))
        rw.font.size = Pt(9)
        
        # 阶段成果单元格 (列 9)
        cell_d = row.cells[9]
        cell_d.text = ""
        pd = cell_d.paragraphs[0]
        rd = pd.add_run(deliverable)
        rd.font.name = "Times New Roman"
        rd._r.get_or_add_rPr().append(parse_xml(f'<w:rFonts {nsdecls("w")} w:eastAsia="SimSun"/>'))
        rd.font.size = Pt(9)

    # 行 9: 预期成果
    cell_exp = t1.rows[9].cells[2]
    cell_exp.text = ""
    p_exp = cell_exp.paragraphs[0]
    p_exp.paragraph_format.first_line_indent = Pt(0)
    p_exp.paragraph_format.line_spacing = 1.25
    r_exp = p_exp.add_run(EXPECTED_OUTPUT)
    r_exp.font.name = "Times New Roman"
    r_exp._r.get_or_add_rPr().append(parse_xml(f'<w:rFonts {nsdecls("w")} w:eastAsia="SimSun"/>'))
    r_exp.font.size = Pt(10)

    temp_save = DOCX_PATH + ".tmp"
    doc.save(temp_save)
    os.replace(temp_save, DOCX_PATH)
    print("毕业设计任务书重构保存成功！")

if __name__ == '__main__':
    update_assignment()
