#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OAISS CHAIN - 将 12 张高清架构大图全面注入各分册 Markdown 活文档
彻底替换纯文字、ASCII 字符画与错配图，实现图文并茂
"""

import os
import re

DOCS_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '../../docs'))
ARCH_DIR = os.path.join(DOCS_DIR, 'assets/architecture')

DOC_IMAGE_MAPPINGS = [
    # 1. 技术架构与使用说明书
    (
        os.path.join(DOCS_DIR, 'OAISS_CHAIN_技术架构与使用说明书.md'),
        '### 1.1 四角色业务模型与权限矩阵',
        '02-four-roles-collaboration.png',
        '图 1.1.1 四角色业务协同与权限流转全景关系图'
    ),
    (
        os.path.join(DOCS_DIR, 'OAISS_CHAIN_技术架构与使用说明书.md'),
        '# 第二章 系统架构设计与技术栈',
        '01-system-hexagonal-architecture.png',
        '图 2.1.1 系统总体分层与六边形微服务拓扑架构图'
    ),
    (
        os.path.join(DOCS_DIR, 'OAISS_CHAIN_技术架构与使用说明书.md'),
        '### 2.1 密码学与信封加密体系',
        '06-cryptography-envelope-encryption.png',
        '图 2.2.1 密码学 RSA 数字签名与 AES-GCM 信封加密防伪流转图'
    ),
    (
        os.path.join(DOCS_DIR, 'OAISS_CHAIN_技术架构与使用说明书.md'),
        '### 1.2 核心业务流转闭环',
        '03-carbon-lifecycle-state-machine.png',
        '图 1.2.1 碳核算全生命周期状态机与数据流转关系图'
    ),
    (
        os.path.join(DOCS_DIR, 'OAISS_CHAIN_技术架构与使用说明书.md'),
        '# 第四章 数据库设计全景',
        '04-database-entity-er-diagram.png',
        '图 4.1.1 数据库 24 张核心业务实体 ER 逻辑关系图'
    ),
    (
        os.path.join(DOCS_DIR, 'OAISS_CHAIN_技术架构与使用说明书.md'),
        '### 1.2 碳交易与双向拍卖操作规程',
        '05-double-auction-matching-engine.png',
        '图 5.2.1 连续双向拍卖撮合引擎与 DVP 结算流转关系图'
    ),

    # 2. 质量保证与验收规范
    (
        os.path.join(DOCS_DIR, '质量保证/质量保证与验收规范.md'),
        '### 1.3 测试矩阵分层架构 (Testing Pyramid)',
        '12-fullstack-testing-pyramid.png',
        '图 1.3.1 全栈分层测试金字塔与质量度量架构图'
    ),
    (
        os.path.join(DOCS_DIR, '质量保证/质量保证与验收规范.md'),
        '# 第四章 自动化门禁与发布阻断守则 (Release Gates)',
        '07-release-gates-pipeline.png',
        '图 4.1.1 企业级持续交付与五级阻断门禁流水线流转图'
    ),

    # 3. 系统设计与架构规格书
    (
        os.path.join(DOCS_DIR, '项目概览/系统设计与架构规格书.md'),
        '## 2. 技术架构',
        '01-system-hexagonal-architecture.png',
        '图 2.1.1 系统总体分层与六边形微服务拓扑架构图'
    ),
    (
        os.path.join(DOCS_DIR, '项目概览/系统设计与架构规格书.md'),
        '## 3. 用户角色与权限',
        '02-four-roles-collaboration.png',
        '图 3.1.1 四角色业务协同与权限流转全景关系图'
    ),
    (
        os.path.join(DOCS_DIR, '项目概览/系统设计与架构规格书.md'),
        '### 3.2 Fabric节点映射与权限矩阵',
        '09-fabric-consortium-network-topology.png',
        '图 3.2.1 Hyperledger Fabric 联盟链网络拓扑与组织映射图'
    ),
    (
        os.path.join(DOCS_DIR, '项目概览/系统设计与架构规格书.md'),
        '### 4.5 双向拍卖模块',
        '05-double-auction-matching-engine.png',
        '图 4.5.1 连续双向拍卖撮合引擎与 DVP 结算流转关系图'
    ),
    (
        os.path.join(DOCS_DIR, '项目概览/系统设计与架构规格书.md'),
        '### 4.8 数字签名模块',
        '06-cryptography-envelope-encryption.png',
        '图 4.8.1 密码学 RSA 数字签名与 AES-GCM 信封加密防伪流转图'
    ),
    (
        os.path.join(DOCS_DIR, '项目概览/系统设计与架构规格书.md'),
        '## 6. 数据库设计',
        '04-database-entity-er-diagram.png',
        '图 6.1.1 数据库 24 张核心业务实体 ER 逻辑关系图'
    ),
    (
        os.path.join(DOCS_DIR, '项目概览/系统设计与架构规格书.md'),
        '## 7. 前端页面说明',
        '08-frontend-vue3-architecture.png',
        '图 7.1.1 前端工程化多层架构与安全管控设计图'
    ),
    (
        os.path.join(DOCS_DIR, '项目概览/系统设计与架构规格书.md'),
        '# 第二部分 系统总体架构与技术选型',
        '11-container-cluster-deployment-topology.png',
        '图 10.1.1 容器化集群部署与网络隔离拓扑架构图'
    ),

    # 4. 全栈开发与技术规格指南
    (
        os.path.join(DOCS_DIR, '开发指南/全栈开发与技术规格指南.md'),
        '# 第一部分 前端工程开发指南 (Vue 3 + TypeScript)',
        '08-frontend-vue3-architecture.png',
        '图 1.1.1 前端工程化多层架构与安全管控设计图'
    ),
    (
        os.path.join(DOCS_DIR, '开发指南/全栈开发与技术规格指南.md'),
        '## 1. 测试规范与分层金字塔',
        '12-fullstack-testing-pyramid.png',
        '图 2.1.1 全栈分层测试金字塔与质量度量架构图'
    ),
    (
        os.path.join(DOCS_DIR, '开发指南/全栈开发与技术规格指南.md'),
        '# 第三部分 Hyperledger Fabric 联盟链集成规范与智能合约',
        '09-fabric-consortium-network-topology.png',
        '图 3.1.1 Hyperledger Fabric 联盟链网络拓扑与链码调用架构图'
    ),
    (
        os.path.join(DOCS_DIR, '开发指南/全栈开发与技术规格指南.md'),
        '# 第五部分 机器学习智能预测算法规格',
        '10-ai-ml-microservice-architecture.png',
        '图 5.1.1 AI/ML 预测算法微服务中台与大模型推理架构图'
    ),

    # 5. 产品规格 / AI 智能预测说明
    (
        os.path.join(DOCS_DIR, '产品规格/AI智能预测模块深度重塑与LLM融合升级说明.md'),
        '# 第二章 软硬件运行环境与算法算力配置规范',
        '10-ai-ml-microservice-architecture.png',
        '图 2.1.1 AI/ML 预测算法微服务中台与大模型推理架构图'
    ),

    # 6. 运维部署手册
    (
        os.path.join(DOCS_DIR, '运维部署/系统部署与运维手册.md'),
        '# 第二部分 系统安装与多环境部署手册',
        '11-container-cluster-deployment-topology.png',
        '图 2.1.1 容器化集群部署与网络隔离拓扑架构图'
    ),

    # 7. 系统 API 接口参考规范
    (
        os.path.join(DOCS_DIR, '接口参考/系统API接口参考规范.md'),
        '# 第一部分 后端 REST API 接口完整清单 (143 端点)',
        '01-system-hexagonal-architecture.png',
        '图 1.0.1 系统 API 接口网关与六边形微服务交互拓扑图'
    ),
    (
        os.path.join(DOCS_DIR, '接口参考/系统API接口参考规范.md'),
        '# 第二部分 Python ML 预测微服务算法接口',
        '10-ai-ml-microservice-architecture.png',
        '图 2.0.1 AI 算法微服务中台调用拓扑图'
    ),
]

def inject_images_to_markdown():
    # 先处理修正《全栈开发指南》中错配的图片
    dev_path = os.path.join(DOCS_DIR, '开发指南/全栈开发与技术规格指南.md')
    if os.path.exists(dev_path):
        with open(dev_path, 'r', encoding='utf-8') as f:
            c = f.read()
        # 修正第 552 行附近的错配图片
        c = c.replace('03-carbon-lifecycle-state-machine.png', '09-fabric-consortium-network-topology.png')
        c = c.replace('图 3.1.1 碳核算全生命周期状态机与数据流转关系图', '图 3.1.1 Hyperledger Fabric 联盟链网络拓扑与链码调用架构图')
        with open(dev_path, 'w', encoding='utf-8') as f:
            f.write(c)

    for file_path, anchor, img_name, caption in DOC_IMAGE_MAPPINGS:
        if not os.path.exists(file_path):
            continue
            
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
            
        rel_img_path = f"../assets/architecture/{img_name}"
        if file_path.endswith('OAISS_CHAIN_技术架构与使用说明书.md'):
            rel_img_path = f"./assets/architecture/{img_name}"

        img_markdown = f"\n<div align=\"center\">\n  <img src=\"{rel_img_path}\" width=\"90%\" alt=\"{caption}\">\n  <br>\n  <b>{caption}</b>\n</div>\n"
        
        # 避免在同一文件同一锚点重复插入同一图片
        if f"alt=\"{caption}\"" in content:
            continue
            
        if anchor in content:
            content = content.replace(anchor, f"{anchor}\n{img_markdown}", 1)
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(content)
            print(f"成功注入架构大图: {os.path.basename(file_path)} <- {caption}")

if __name__ == '__main__':
    inject_images_to_markdown()
    print("全量 12 张架构大图注入完毕！")
