#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OAISS CHAIN - 针对 Markdown 中所有裸泛型文本的安全转义修补器
"""

import os
import re
import glob

GENERIC_TAGS = [
    'Page<T>', 'Result<T>', 'Result<Void>', 'Result<Long>', 'Result<String>',
    '<T>', '<Void>', '<Long>', '<String>', '<BigDecimal>', '<MyEntity>',
    '<FormInstance>', '<PredictionPoint>', '<Recommendation>',
    '<dependency>', '</dependency>', '<groupId>', '</groupId>',
    '<artifactId>', '</artifactId>', '<version>', '</version>',
    '<previous>', '<owner>', '<JWT>', '<token>', '<secret>',
    '<port>', '<host>'
]

def escape_file(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    new_content = content
    for tag in GENERIC_TAGS:
        # 匹配非反引号包围的 tag
        pattern = r'(?<!`)' + re.escape(tag) + r'(?!`)'
        new_content = re.sub(pattern, f'`{tag}`', new_content)

    if new_content != content:
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(new_content)
        print(f"成功安全转义泛型: {file_path}")
        return True
    return False

def main():
    md_files = glob.glob('docs/**/*.md', recursive=True) + glob.glob('docs/*.md')
    md_files = list(set(md_files))
    
    count = 0
    for mf in md_files:
        if escape_file(mf):
            count += 1
            
    print(f"泛型治理全部完成，共计更新 {count} 个文件")

if __name__ == '__main__':
    main()
