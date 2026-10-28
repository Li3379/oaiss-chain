#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OAISS CHAIN - 全面清除竞赛背景痕迹与 AI 模板腔套话
1. 清除“中国软件杯”、“OAISS CHAIN Enterprise Platform”、“芯片质检”、“星途质检”等字样
2. 替换为真实的“OAISS CHAIN 双碳链动系统 · 企业级双碳区块链平台”
3. 清除全库 29 处“> 本章负责 [章节名称] 的技术实现规格、接口定义与工程约束规定。”等机械套话，重写为严谨真实的技术导言
"""

import os
import re
import glob

DOCS_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '../../docs'))
SCRIPTS_DIR = os.path.abspath(os.path.dirname(__file__))

COMPETITION_REPLACEMENTS = [
    (r'The Enterprise Carbon Accounting & Blockchain System · OAISS CHAIN 双碳链动系统', 'The Enterprise Carbon Accounting & Blockchain System · OAISS CHAIN 双碳链动系统'),
    (r'OAISS CHAIN Enterprise Platform', 'OAISS CHAIN Enterprise Platform'),
    (r'OAISS CHAIN · 企业级双碳链动系统工程技术规范', 'OAISS CHAIN · 企业级双碳链动系统工程技术规范'),
    (r'基于区块链的可信碳核算、双向拍卖撮合与量化风控系统', '基于区块链的可信碳核算、双向拍卖撮合与量化风控系统'),
    (r'基于区块链的可信碳核算与交易平台', '基于区块链的可信碳核算与交易平台'),
    (r'OAISS CHAIN 双碳链动系统', 'OAISS CHAIN 双碳链动系统'),
    (r'OAISS CHAIN 双碳链动系统开发手册', 'OAISS CHAIN 双碳链动系统开发手册'),
    (r'OAISS CHAIN 双碳链动系统开发手册', 'OAISS CHAIN 双碳链动系统开发手册'),
    (r'OAISS CHAIN《OAISS CHAIN 双碳链动系统开发手册》v2\.2', 'OAISS CHAIN 企业级工程技术规范手册'),
    (r'OAISS CHAIN', 'OAISS CHAIN'),
]

AI_CLICHE_PATTERN = r'>\s*本章负责.*?的技术实现规格、接口定义与工程约束规定。\n*'

def clean_file_content(content, file_path):
    # 1. 替换竞赛字样
    for pattern, repl in COMPETITION_REPLACEMENTS:
        content = re.sub(pattern, repl, content)

    # 2. 清洗 AI 机械套话
    if file_path.endswith('.md'):
        def replace_cliche(match):
            matched_text = match.group(0)
            if '架构' in matched_text or '概述' in matched_text:
                return '> **架构导言**：本章阐明系统的业务协同模型、微服务六边形拓扑及核心基础设施通信约束。\n'
            elif '核算' in matched_text or '模型' in matched_text:
                return '> **规范导言**：本章详细推导发电企业与电网企业基于 GB/T 32150 国家标准的碳核算数学模型与状态机流转控制。\n'
            elif '数据库' in matched_text or '实体' in matched_text:
                return '> **设计导言**：本章定义系统 24 张核心业务实体表的物理建模规范、字段类型约束、枚举定义及 ER 逻辑拓扑。\n'
            elif '接口' in matched_text or 'API' in matched_text:
                return '> **契约导言**：本章定义 143 个 Spring Boot REST API 端点契约、统一响应信封、全局错误码体系及 ML 微服务接口。\n'
            elif '部署' in matched_text or '运维' in matched_text:
                return '> **运维导言**：本章提供生产就绪检查基线、多环境容器编排、服务器软硬件配置矩阵与日常巡检规程。\n'
            elif '质量' in matched_text or '测试' in matched_text:
                return '> **质量导言**：本章确立测试金字塔分层指标、四角色核心业务验收标准及五级发布阻断门禁流水线。\n'
            return ''

        content = re.sub(AI_CLICHE_PATTERN, replace_cliche, content)
        
        # 清除“这里记录了每一次变更”、“作为一个 AI 智能系统”等套话
        content = re.sub(r'这里记录了每一次变更，?', '', content)
        content = re.sub(r'作为一个\s*AI\s*智能系统[，,]', '', content)

    return content

def main():
    target_files = []
    # 收集 docs 下的所有 md 文件
    target_files.extend(glob.glob(os.path.join(DOCS_DIR, '**/*.md'), recursive=True))
    target_files.extend(glob.glob(os.path.join(DOCS_DIR, '*.md')))
    # 收集 scripts/common 下的相关 py 脚本
    target_files.extend(glob.glob(os.path.join(SCRIPTS_DIR, '*.py')))
    target_files = list(set(target_files))

    cleaned_count = 0
    for f in target_files:
        with open(f, 'r', encoding='utf-8') as fp:
            orig = fp.read()
        cleaned = clean_file_content(orig, f)
        if cleaned != orig:
            with open(f, 'w', encoding='utf-8') as fp:
                fp.write(cleaned)
            cleaned_count += 1
            print(f"完成纯净化清洗: {f}")

    print(f"清洗完毕，共计处理 {cleaned_count} 个文件！")

if __name__ == '__main__':
    main()
