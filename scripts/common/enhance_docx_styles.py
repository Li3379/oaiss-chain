#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OAISS CHAIN - 优化 DOCX 文档样式与大纲级别
将文本标题段落映射至 Word 原生大纲样式 (Heading 1, 2, 3, 4)，使导航窗格与目录完美展示。
"""

import os
import re
import docx

DOCS_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '../../docs'))

TARGET_DOCX = [
    os.path.join(DOCS_DIR, '项目概览/系统设计与架构规格书.docx'),
    os.path.join(DOCS_DIR, '开发指南/全栈开发与技术规格指南.docx'),
    os.path.join(DOCS_DIR, '接口参考/系统API接口参考规范.docx'),
    os.path.join(DOCS_DIR, '质量保证/质量保证与验收规范.docx'),
    os.path.join(DOCS_DIR, '运维部署/系统部署与运维手册.docx'),
    os.path.join(DOCS_DIR, 'OAISS_CHAIN_技术架构与使用说明书.docx'),
]

def enhance_docx(file_path):
    print(f"正在优化大纲样式: {file_path}")
    doc = docx.Document(file_path)
    
    modified_count = 0
    
    for p in doc.paragraphs:
        text = p.text.strip()
        if not text or len(text) > 80:
            continue
            
        # 跳过目录超链接
        if re.search(r'\[.+\]\(#.+\)', text) or text in ['目录', 'Table of Contents']:
            continue
            
        # 1. 一级大纲 (第X部分 / 第X章)
        if re.match(r'^第[一二三四五六七八九十0-9]+[部分章节篇]', text):
            p.style = 'Heading 1'
            modified_count += 1
            continue
            
        # 2. 二级大纲 (1. 2. 3.)
        m2 = re.match(r'^(\d+)\.\s*([^\n。；]{2,50})$', text)
        if m2 and not text.endswith(('。', '；', '：', ':')):
            p.style = 'Heading 2'
            modified_count += 1
            continue
            
        # 3. 三级大纲 (1.1, 2.3)
        m3 = re.match(r'^(\d+\.\d+)\s*([^\n。；]{2,60})$', text)
        if m3 and not text.endswith(('。', '；', '：', ':')):
            p.style = 'Heading 3'
            modified_count += 1
            continue
            
        # 4. 四级大纲 (1.1.1, 2.3.4)
        m4 = re.match(r'^(\d+\.\d+\.\d+)\s*([^\n。；]{2,70})$', text)
        if m4 and not text.endswith(('。', '；', '：', ':')):
            p.style = 'Heading 4'
            modified_count += 1
            continue

    temp_path = file_path + '.tmp'
    doc.save(temp_path)
    os.replace(temp_path, file_path)
    print(f"完成优化: {file_path} (成功绑定 {modified_count} 处大纲标题样式)")

def main():
    for f in TARGET_DOCX:
        if os.path.exists(f):
            enhance_docx(f)
        else:
            print(f"[跳过] 找不到文件: {f}")

if __name__ == '__main__':
    main()
