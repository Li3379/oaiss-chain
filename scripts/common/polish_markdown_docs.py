#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OAISS CHAIN - 深度美化 Markdown 文档排版
1. 修复 ## 1. 1 样式为 ### 1.1 标准多级标题
2. 自动检测裸 JSON、SQL、Java、Bash 文本块并包裹代码语法高亮
3. 将伪单表格 Callout 转换为标准引用提示框 (> **注意 (Note)**: ...)
4. 依据表格上下文重命名表格标题（如：角色权限矩阵表、接口契约表、数据字典表）
5. 规整段落与列表格式
"""

import os
import re

DOCS_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '../../docs'))

TARGET_FILES = [
    os.path.join(DOCS_DIR, '项目概览/系统设计与架构规格书.md'),
    os.path.join(DOCS_DIR, '开发指南/全栈开发与技术规格指南.md'),
    os.path.join(DOCS_DIR, '接口参考/系统API接口参考规范.md'),
    os.path.join(DOCS_DIR, '质量保证/质量保证与验收规范.md'),
    os.path.join(DOCS_DIR, '运维部署/系统部署与运维手册.md'),
    os.path.join(DOCS_DIR, 'OAISS_CHAIN_技术架构与使用说明书.md'),
]

def polish_content(text):
    lines = text.split('\n')
    new_lines = []
    
    i = 0
    n = len(lines)
    
    table_counter = 1
    
    while i < n:
        line = lines[i]
        
        # 1. 标题层级编号修复: ## 1. 1 -> ### 1.1
        m_h2 = re.match(r'^##\s+(\d+)\.\s+(\d+)\s*(.*)', line)
        if m_h2:
            sec = m_h2.group(1)
            sub = m_h2.group(2)
            title = m_h2.group(3).strip()
            new_lines.append(f"### {sec}.{sub} {title}")
            i += 1
            continue

        # 标题修复: ### 1. 1. 1 -> #### 1.1.1
        m_h3 = re.match(r'^###\s+(\d+)\.\s+(\d+)\.\s+(\d+)\s*(.*)', line)
        if m_h3:
            sec = m_h3.group(1)
            sub1 = m_h3.group(2)
            sub2 = m_h3.group(3)
            title = m_h3.group(4).strip()
            new_lines.append(f"#### {sec}.{sub1}.{sub2} {title}")
            i += 1
            continue
            
        # 2. 识别单单元格伪表格 -> 转换为 Callout 提示框
        if line.startswith('| 【') and line.endswith(' |'):
            content = line[1:-1].strip()
            tag_match = re.match(r'^【(.*?)】\s*(.*)', content)
            if tag_match:
                tag = tag_match.group(1)
                body = tag_match.group(2)
                tag_label = "注意 (Note)"
                if "警告" in tag:
                    tag_label = "警告 (Warning)"
                elif "说明" in tag or "提示" in tag or "设计" in tag or "建议" in tag:
                    tag_label = "说明 (Notice)"
                new_lines.append(f"> **{tag_label}**：{body}")
                # 跳过随后的分割线 | :--- |
                if i + 1 < n and re.match(r'^\|\s*:?---+:?\s*\|$', lines[i+1].strip()):
                    i += 2
                    continue
                i += 1
                continue
                
        # 3. 增强表格标题根据表头推断
        if line.startswith('**表 ') and '数据规范对照表**' in line:
            # 探测下一行或下两行表头
            header_peek = ""
            for j in range(i+1, min(i+4, n)):
                if lines[j].startswith('|'):
                    header_peek = lines[j]
                    break
            
            table_name = "数据规范对照表"
            if '字段' in header_peek or '类型' in header_peek or 'Nullable' in header_peek:
                table_name = "数据库实体字段与约束字典表"
            elif '端点' in header_peek or 'Method' in header_peek or '路径' in header_peek or 'API' in header_peek:
                table_name = "REST API 接口规范与参数契约表"
            elif '角色' in header_peek or '权限' in header_peek:
                table_name = "四角色访问控制与权限矩阵表"
            elif '环境' in header_peek or '硬件' in header_peek or '版本' in header_peek or '依赖' in header_peek:
                table_name = "技术栈依赖与运行环境配置表"
            elif '状态' in header_peek or '枚举' in header_peek or 'Code' in header_peek:
                table_name = "状态机流转与错误码对照表"
            elif '指标' in header_peek or '阈值' in header_peek or '测试' in header_peek:
                table_name = "质量检验与自动化测试矩阵表"
                
            prefix_match = re.match(r'^\*\*表\s+([\d\.]+)\s+', line)
            if prefix_match:
                pref = prefix_match.group(1)
                new_lines.append(f"**表 {pref} {table_name}**")
            else:
                new_lines.append(f"**表 {table_counter} {table_name}**")
                table_counter += 1
            i += 1
            continue

        # 4. 识别独立 JSON 文本块 (以 { 开始，且若干行后以 } 结束)
        if line.strip() == '{' and not (new_lines and new_lines[-1].startswith('```')):
            # 探测是否是 JSON 块
            json_block = [line]
            k = i + 1
            is_json = False
            while k < min(i + 40, n):
                json_block.append(lines[k])
                if lines[k].strip() == '}':
                    is_json = True
                    break
                k += 1
                
            if is_json:
                new_lines.append("```json")
                for jl in json_block:
                    new_lines.append(jl)
                new_lines.append("```")
                i = k + 1
                continue

        # 5. 识别 SQL 语句块 (以 CREATE TABLE 开头)
        if line.strip().startswith('CREATE TABLE ') and not (new_lines and new_lines[-1].startswith('```')):
            sql_block = [line]
            k = i + 1
            is_sql = False
            while k < min(i + 50, n):
                sql_block.append(lines[k])
                if lines[k].strip().endswith(');') or lines[k].strip() == ');':
                    is_sql = True
                    break
                k += 1
                
            if is_sql:
                new_lines.append("```sql")
                for sl in sql_block:
                    new_lines.append(sl)
                new_lines.append("```")
                i = k + 1
                continue
                
        new_lines.append(line)
        i += 1
        
    res = "\n".join(new_lines)
    # 消除连续超过两个的换行
    res = re.sub(r'\n{3,}', '\n\n', res)
    return res

def main():
    for f in TARGET_FILES:
        if os.path.exists(f):
            print(f"深度美化: {f}")
            with open(f, 'r', encoding='utf-8') as fp:
                content = fp.read()
            polished = polish_content(content)
            with open(f, 'w', encoding='utf-8') as fp:
                fp.write(polished)
            print(f"完成美化: {f} ({len(polished)} 字符)")
        else:
            print(f"[跳过] 找不到文件: {f}")

if __name__ == '__main__':
    main()
