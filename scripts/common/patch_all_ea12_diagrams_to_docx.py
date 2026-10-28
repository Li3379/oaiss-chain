#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
毕业设计成果文档 11 张核心工程图全量工业规范化更新脚本
涵盖全部 UML 2.5 / EA12 规范图表:
- image3.png  (rId17): 图2.2 系统多角色核心用例全景图 (02-four-roles-collaboration.png)
- image4.png  (rId18): 图2.3 企业用户用例图 (02-enterprise-usecase-diagram.png)
- image5.png  (rId19): 图2.4 数据审核员用例图 (02-reviewer-usecase-diagram.png)
- image6.png  (rId20): 图2.5 第三方监管机构与系统管理员用例图 (02-thirdparty-admin-usecase-diagram.png)
- image7.png  (rId21): 图2.6 系统总体分层与六边形微服务拓扑架构图 (01-system-hexagonal-architecture.png)
- image8.png  (rId22): 图3.1 数据库核心业务实体 ER 逻辑关系模型图 (04-database-entity-er-diagram.png)
- image18.png (rId32): 图3.11 全系统工程架构与包组织关系图 (08-frontend-vue3-architecture.png)
- image19.png (rId33): 图3.12 密码学数字签名与链上存证流转顺序图 (06-cryptography-envelope-encryption.png)
- image20.png (rId34): 图3.13 双向拍卖撮合引擎与 DVP 结算流转顺序图 (05-double-auction-matching-engine.png)
- image21.png (rId35): 图3.14 AI 预测算法微服务调用与模型流水线顺序图 (10-ai-ml-microservice-architecture.png)
- image22.png (rId36): 图3.15 全栈分层测试金字塔与质量度量架构图 (12-fullstack-testing-pyramid.png)
"""
import os
import zipfile
import re
import shutil
from PIL import Image

DOCX_ORIG = r"docs/软件学院2026届学生毕业设计成果_OAISS_CHAIN双碳链动系统(企软方向).docx"
DOCX_TMP = r"docs/软件学院2026届学生毕业设计成果_OAISS_CHAIN双碳链动系统(企软方向).docx.tmp"

DOCX_SRC = DOCX_ORIG

IMG_MAP = {
    "word/media/image3.png": ("docs/assets/architecture/02-four-roles-collaboration.png", "rId17"),
    "word/media/image4.png": ("docs/assets/architecture/02-enterprise-usecase-diagram.png", "rId18"),
    "word/media/image5.png": ("docs/assets/architecture/02-reviewer-usecase-diagram.png", "rId19"),
    "word/media/image6.png": ("docs/assets/architecture/02-thirdparty-admin-usecase-diagram.png", "rId20"),
    "word/media/image7.png": ("docs/assets/architecture/01-system-hexagonal-architecture.png", "rId21"),
    "word/media/image8.png": ("docs/assets/architecture/04-database-entity-er-diagram.png", "rId22"),
    "word/media/image18.png": ("docs/assets/architecture/08-frontend-vue3-architecture.png", "rId32"),
    "word/media/image19.png": ("docs/assets/architecture/06-cryptography-envelope-encryption.png", "rId33"),
    "word/media/image20.png": ("docs/assets/architecture/05-double-auction-matching-engine.png", "rId34"),
    "word/media/image21.png": ("docs/assets/architecture/10-ai-ml-microservice-architecture.png", "rId35"),
    "word/media/image22.png": ("docs/assets/architecture/12-fullstack-testing-pyramid.png", "rId36"),
}

TARGET_CX = 5400000  # 15.0 cm (标准版心宽度)

ASPECTS = {}
RID_TO_CY = {}

print("=== 计算 11 张工程图尺寸与显示高度 ===")
for media_path, (local_img, rid) in IMG_MAP.items():
    im = Image.open(local_img)
    w, h = im.size
    aspect = w / h
    cy = int(TARGET_CX / aspect)
    ASPECTS[media_path] = (TARGET_CX, cy, aspect)
    RID_TO_CY[rid] = cy
    print(f"[{rid:5s}] {os.path.basename(local_img):42s}: {w}x{h}, aspect={aspect:.2f}, cy={cy:7d} ({cy/360000:.2f} cm)")

DOCX_GEN_TMP = DOCX_TMP + ".new"
with zipfile.ZipFile(DOCX_SRC, 'r') as zin:
    file_list = zin.namelist()
    doc_xml = zin.read('word/document.xml').decode('utf-8')

    def replace_drawing(match):
        block = match.group(0)
        for rid, cy in RID_TO_CY.items():
            if f'r:embed="{rid}"' in block:
                block = re.sub(r'(<wp:extent\s+cx="\d+"\s+cy=")\d+(")', rf'\g<1>{cy}\g<2>', block)
                block = re.sub(r'(<a:ext\s+cx="\d+"\s+cy=")\d+(")', rf'\g<1>{cy}\g<2>', block)
                print(f"Updated extent for {rid}: cy={cy}")
                break
        return block

    doc_xml_new = re.sub(r'<w:drawing>.*?</w:drawing>', replace_drawing, doc_xml, flags=re.DOTALL)

    with zipfile.ZipFile(DOCX_GEN_TMP, 'w', compression=zipfile.ZIP_DEFLATED) as zout:
        for item in file_list:
            if item in IMG_MAP:
                local_path = IMG_MAP[item][0]
                with open(local_path, 'rb') as f_img:
                    zout.writestr(item, f_img.read())
                print(f"Replaced: {item} -> {os.path.basename(local_path)}")
            elif item == 'word/document.xml':
                zout.writestr(item, doc_xml_new.encode('utf-8'))
                print("Replaced: word/document.xml with updated drawing extents")
            else:
                zout.writestr(item, zin.read(item))

if os.path.exists(DOCX_TMP):
    os.remove(DOCX_TMP)
shutil.move(DOCX_GEN_TMP, DOCX_TMP)
print(f"\n成功构建包含全部 11 张 EA12 规范工程图的 Word 成果文档: {DOCX_TMP}")

try:
    shutil.copyfile(DOCX_TMP, DOCX_ORIG)
    os.remove(DOCX_TMP)
    print(f"直接覆盖正式文档 {DOCX_ORIG} 成功！")
except Exception as e:
    print(f"正式文档当前仍被占用: {e}")
    print(f"已在 {DOCX_TMP} 完整保留最新成果，使用 finalize_docx.bat 即可一键覆盖落盘。")
