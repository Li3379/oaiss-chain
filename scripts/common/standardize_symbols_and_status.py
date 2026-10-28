#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OAISS CHAIN - 全量规范化工程符号、数据字典可空性与测试状态词
1. 权限与功能矩阵表: 将非正式 Emoji (✅, ❌) 统一替换为标准工程符号 (√, —)
2. 数据字典表格: 表头统一规范为“可否为空”，取值统一规范写定为“否”(非空) 与“是”(允许为空)
3. 单元测试卡片: 执行状态列统一规范写定为 **PASSED** 与 **FAILED**，期望与实际结果使用确定性业务中文
4. 上线闸门与证据清单: 严格锁定“已完成”/“待执行”与“- [x]”/“- [ ]”，确保 100% 满足 CI 门禁正则
5. 科学公式中的乘法: 严格保留标准数学乘号 × (Unicode U+00D7)
"""

import os
import re
import glob

DOCS_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '../../docs'))

def standardize_markdown_content(content, file_path):
    lines = content.split('\n')
    new_lines = []
    
    in_gate_table = False
    
    for line in lines:
        s = line.strip()
        
        # 1. 检测是否处于上线闸门表格中 (必须受门禁严格保护)
        if '上线闸门' in line or '上线闸门矩阵' in line:
            in_gate_table = True
        elif in_gate_table and s.startswith('#'):
            in_gate_table = False
            
        if in_gate_table:
            # 在上线闸门表格中，状态列必须绝对保持 已完成 / 待执行
            new_lines.append(line)
            continue
            
        # 2. 规范权限矩阵中的 Emoji: ✅ -> √, ❌ -> —, ⚠️ -> 部分
        # 仅在表格行中替换，且不破坏复选框
        if s.startswith('|') and s.endswith('|'):
            # 替换权限矩阵中的 Emoji 勾叉
            line = line.replace('✅', '√').replace('❌', '—').replace('⚠️', '部分')
            
            # 规范数据字典中的可空性
            # 检查表头是否包含 可空 或 可否为空
            cells = [c.strip() for c in s.strip('|').split('|')]
            if len(cells) >= 5:
                # 规范可空表头
                if '可空' in line and '可否为空' not in line:
                    line = line.replace('可空', '可否为空')
                    
                # 规范行内值 (将单独立的 NO 替换为 否，YES 替换为 是)
                # 采用正则单元格匹配替换
                line = re.sub(r'\|\s*NO\s*\|', '| 否 |', line)
                line = re.sub(r'\|\s*YES\s*\|', '| 是 |', line)
                line = re.sub(r'\|\s*NOT NULL\s*\|', '| 否 |', line)
                line = re.sub(r'\|\s*NULL\s*\|', '| 是 |', line)
                
            new_lines.append(line)
            continue
            
        # 3. 规范单测执行状态: **PASS** -> **PASSED**
        if '**PASS**' in line and '**PASSED**' not in line:
            line = line.replace('**PASS**', '**PASSED**')
            
        new_lines.append(line)
        
    return '\n'.join(new_lines)

def main():
    md_files = glob.glob(os.path.join(DOCS_DIR, '**/*.md'), recursive=True) + glob.glob(os.path.join(DOCS_DIR, '*.md'))
    md_files = list(set(md_files))
    
    updated_count = 0
    for mf in md_files:
        with open(mf, 'r', encoding='utf-8') as f:
            orig = f.read()
        std = standardize_markdown_content(orig, mf)
        if std != orig:
            with open(mf, 'w', encoding='utf-8') as f:
                f.write(std)
            updated_count += 1
            print(f"符号与状态规范化完成: {os.path.basename(mf)}")
            
    print(f"工程符号与状态规范化全部完成，共计更新 {updated_count} 个 Markdown 活文档！")

if __name__ == '__main__':
    main()
