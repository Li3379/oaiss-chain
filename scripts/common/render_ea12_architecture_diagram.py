#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Enterprise Architect 12 (EA12) 工程图规范: 系统总体分层与六边形微服务拓扑架构图
- 标准纯白工程底色 (#FFFFFF)
- UML 2.5 Package 文件夹页签图元 (左上角 Tab 页签)
- UML 2.5 Component 组件图元 (右上角齿槽标记与 «stereotype»)
- 六边形核心 (入站端口适配器 / 领域服务核心 / 出站端口适配器)
- 接口装配连线 (Interface assembly connectors: «REST/JSON», «gRPC», «JDBC», «WebClient»)
- 高分辨率出版级输出 (300 DPI)
"""

import os
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.patches as patches

OUTPUT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '../../docs/assets/architecture'))
os.makedirs(OUTPUT_DIR, exist_ok=True)

plt.rcParams['font.sans-serif'] = ['Microsoft YaHei', 'SimHei', 'DejaVu Sans']
plt.rcParams['axes.unicode_minus'] = False

W, H = 16.0, 9.8  # 300 DPI -> 4800 x 2940

def render_ea12_architecture_diagram(filename='01-system-hexagonal-architecture.png'):
    fig, ax = plt.subplots(figsize=(W, H), dpi=300)
    fig.patch.set_facecolor('#FFFFFF')
    ax.set_facecolor('#FFFFFF')
    ax.set_xlim(0, W)
    ax.set_ylim(0, H)
    ax.axis('off')

    # 1. 外部图框 (EA12 Diagram Frame)
    FRAME_X, FRAME_Y, FRAME_W, FRAME_H = 0.5, 0.4, W - 1.0, H - 0.8
    ax.add_patch(patches.Rectangle((FRAME_X, FRAME_Y), FRAME_W, FRAME_H,
                                   edgecolor='#94A3B8', facecolor='#FFFFFF', linewidth=1.2, zorder=1))

    # EA12 标题卡签 (左上角)
    TAB_W, TAB_H = 5.8, 0.46
    ax.add_patch(patches.Rectangle((FRAME_X, FRAME_Y + FRAME_H - TAB_H), TAB_W, TAB_H,
                                   edgecolor='#94A3B8', facecolor='#F1F5F9', linewidth=1.0, zorder=2))
    ax.text(FRAME_X + 0.15, FRAME_Y + FRAME_H - TAB_H/2,
            "package [Model] OAISS-CHAIN [系统总体分层与六边形微服务拓扑架构图]",
            fontsize=8.5, weight='bold', color='#1E293B', va='center', zorder=3)

    # 右上角标准规范标识
    ax.text(FRAME_X + FRAME_W - 0.2, FRAME_Y + FRAME_H - TAB_H/2,
            "Enterprise Architect 12 / UML 2.5 Specification · 300 DPI",
            fontsize=7.8, color='#64748B', ha='right', va='center', zorder=3)

    # -------------------------------------------------------------
    # 绘图工具函数
    # -------------------------------------------------------------
    def draw_package(x, y, w, h, name, stereotype="layer", bg='#FAFAFA', border='#64748B'):
        """UML 2.5 Package 文件夹图元 (带左上角页签)"""
        tab_w = min(w * 0.48, max(2.2, len(name) * 0.115 + 0.9))
        tab_h = 0.34
        # 页签
        ax.add_patch(patches.Rectangle((x, y + h), tab_w, tab_h,
                                       edgecolor=border, facecolor=bg, linewidth=1.1, zorder=2))
        ax.text(x + 0.12, y + h + tab_h/2, f"«{stereotype}» {name}",
                fontsize=7.8, weight='bold', color='#334155', va='center', zorder=3)
        # 包体矩形
        ax.add_patch(patches.Rectangle((x, y), w, h,
                                       edgecolor=border, facecolor=bg, linewidth=1.1, zorder=2))

    def draw_component(x, y, w, h, name, stereotype="component", items=[], bg='#FFFFFF', border='#475569'):
        """UML 2.5 Component 组件图元 (带右上角组件齿槽图标)"""
        ax.add_patch(patches.Rectangle((x, y), w, h,
                                       edgecolor=border, facecolor=bg, linewidth=1.05, zorder=3))
        # 右上角组件图标
        ico_x = x + w - 0.36
        ico_y = y + h - 0.30
        ax.add_patch(patches.Rectangle((ico_x, ico_y), 0.22, 0.18,
                                       edgecolor=border, facecolor='#F8FAFC', linewidth=0.75, zorder=4))
        ax.add_patch(patches.Rectangle((ico_x - 0.05, ico_y + 0.10), 0.08, 0.05,
                                       edgecolor=border, facecolor='#F8FAFC', linewidth=0.75, zorder=5))
        ax.add_patch(patches.Rectangle((ico_x - 0.05, ico_y + 0.03), 0.08, 0.05,
                                       edgecolor=border, facecolor='#F8FAFC', linewidth=0.75, zorder=5))
        
        # 构造型与组件名
        ax.text(x + (w - 0.4)/2, y + h - 0.18, f"«{stereotype}»",
                fontsize=6.8, color='#64748B', ha='center', va='center', zorder=4)
        ax.text(x + (w - 0.4)/2, y + h - 0.38, name,
                fontsize=8.5, weight='bold', color='#0F172A', ha='center', va='center', zorder=4)
        
        # 分隔线与功能列表
        if items:
            ax.plot([x + 0.08, x + w - 0.08], [y + h - 0.52, y + h - 0.52],
                    color='#E2E8F0', linewidth=0.8, zorder=4)
            for i, it in enumerate(items):
                ax.text(x + 0.15, y + h - 0.70 - i * 0.21, it,
                        fontsize=7.0, color='#334155', va='center', zorder=4)

    def draw_interface_conn(p1, p2, label="", style='--'):
        """UML 依赖/接口装配连线"""
        ax.plot([p1[0], p2[0]], [p1[1], p2[1]], color='#475569', linewidth=1.1, linestyle=style, zorder=2)
        if label:
            mx, my = (p1[0] + p2[0]) / 2, (p1[1] + p2[1]) / 2
            ax.text(mx, my + 0.08, label, fontsize=6.6, color='#1E293B', ha='center', va='bottom',
                    bbox=dict(boxstyle='square,pad=0.15', facecolor='#FFFFFF', edgecolor='#CBD5E1', linewidth=0.6),
                    zorder=5)

    # =============================================================
    # 布局设计 (严格对应毕业设计文档 2.4.1 节五个子系统与六边形中台)
    # =============================================================

    # 1. 客户端呈现与接入层 (Layer 1 - Top Left, Y: 6.85 ~ 8.7)
    draw_package(0.8, 6.85, 4.4, 1.85, "表现接入层", stereotype="layer", bg='#F8FAFC')
    draw_component(1.0, 7.0, 1.9, 1.5, "Vue 3.5 SPA 前端", stereotype="presentation",
                   items=["• 四角色独立工作台路由", "• Pinia 3 响应式状态中心", "• ECharts 6.0 金融大盘", "• i18n 1430词条国际化"],
                   bg='#FFFFFF')
    draw_component(3.1, 7.0, 1.9, 1.5, "Nginx 1.25 网关", stereotype="edge gateway",
                   items=["• 静态资产高效分发托管", "• SSL / TLS 证书卸载终结", "• URL 动态反向代理分流", "• 跨域与网络防护过滤"],
                   bg='#FFFFFF')

    # 2. 算法智能中台 (Subsystem - Top Right, Y: 6.85 ~ 8.7)
    draw_package(10.8, 6.85, 4.4, 1.85, "算法智能中台", stereotype="subsystem", bg='#F8FAFC')
    draw_component(11.0, 7.0, 4.0, 1.5, "FastAPI 算法微服务 (Python 3.11)", stereotype="ai engine",
                   items=["• Prophet 时间序列模型: 碳价未来 30/90/180 天预测与 95% 置信通道",
                          "• XGBoost 特征重要度归因引擎: 宏观能源与履约因子 XAI 驱动解析",
                          "• Isolation Forest 无监督异常排放模式检测与合规偏离雷达图",
                          "• Qwen 2.5 7B 大模型异步推理引擎: 智能合规诊断建议自动生成"],
                   bg='#FFFFFF')

    # 3. 核心业务中台六边形架构 (Subsystem - Middle, Y: 2.75 ~ 6.45)
    draw_package(0.8, 2.75, 14.4, 3.70, "核心业务中台 (Spring Boot 3.2.12)", stereotype="hexagonal subsystem", bg='#FAFAFA')

    # 3.1 左侧: 入站适配器 (Inbound Ports & Adapters)
    draw_package(1.1, 2.95, 3.6, 3.2, "入站端口适配器", stereotype="inbound adapters", bg='#FFFFFF', border='#94A3B8')
    draw_component(1.25, 4.65, 3.3, 1.35, "RESTful API 控制器群", stereotype="inbound adapter",
                   items=["• 22 个 REST 控制器 (143 个端点)", "• JSON 数据契约校验 (@Valid)", "• 统一响应与异常拦截包装 (R<T>)"],
                   bg='#F8FAFC')
    draw_component(1.25, 3.10, 3.3, 1.35, "安全鉴权与 AOP 切面", stereotype="inbound adapter",
                   items=["• Spring Security + JWT 双令牌鉴权", "• @AuditLog 敏感业务操作自动留痕", "• RateLimit IP/用户维度双轨防刷"],
                   bg='#F8FAFC')

    # 3.2 中间: 业务领域核心 (Domain Core / Application Services)
    draw_package(5.0, 2.95, 6.0, 3.2, "业务领域内核与应用服务", stereotype="domain core", bg='#FFFFFF', border='#0284C7')
    core_items_1 = [
        "• 碳核算与公式推导引擎: 发电25参数、电网SF6六氟化硫排放核算与反推",
        "• 连续双向拍卖撮合引擎: 价格优先/时间优先、微批次出清、原子交割",
        "• 密码学签名验签中心: RSA-2048 签名/验签、AES-256-GCM 私钥信封解密",
        "• 碳币资产总账管理: 账户隔离、资金流水记账、双花防御、余额互转"
    ]
    core_items_2 = [
        "• 碳信用履约评价体系: 基础信用分维护、违规扣分流转、奖惩轨迹存证",
        "• 企业准入与证书签发: 资质审查流转、X.509 数字证书生命周期管线",
        "• 监控与大屏态势引擎: 区域碳排放宏观聚类、历史审核统计、预警推送"
    ]
    draw_component(5.15, 4.65, 5.7, 1.35, "核心领域计算与交易引擎", stereotype="domain service",
                   items=core_items_1, bg='#F0F9FF', border='#0284C7')
    draw_component(5.15, 3.10, 5.7, 1.35, "信用评价与合规监管服务", stereotype="domain service",
                   items=core_items_2, bg='#F0F9FF', border='#0284C7')

    # 3.3 右侧: 出站适配器 (Outbound Ports & Adapters)
    draw_package(11.3, 2.95, 3.6, 3.2, "出站端口适配器", stereotype="outbound adapters", bg='#FFFFFF', border='#94A3B8')
    draw_component(11.45, 4.65, 3.3, 1.35, "区块链与事件发布适配器", stereotype="outbound adapter",
                   items=["• Fabric Gateway 客户端 (gRPC 接口)", "• Outbox 事务外发箱持久发布器", "• 异步存证指纹回填事件监听器"],
                   bg='#F8FAFC')
    draw_component(11.45, 3.10, 3.3, 1.35, "数据存储与 RPC 适配器", stereotype="outbound adapter",
                   items=["• Spring Data JPA 数据持久化仓储", "• Redisson 缓存与分布式互斥锁", "• WebClient 异步非阻塞 HTTP 客户端"],
                   bg='#F8FAFC')

    # 4. 可信区块链存证层与基础设施层 (Layer 4 & 5 - Bottom, Y: 0.6 ~ 2.45)
    draw_package(0.8, 0.6, 6.8, 1.85, "可信区块链存证层", stereotype="subsystem", bg='#F8FAFC')
    draw_component(1.0, 0.75, 6.4, 1.5, "Hyperledger Fabric 2.5 联盟链网络", stereotype="blockchain network",
                   items=["• 组织 Peer 节点群: EnterprisePeer / ReviewerPeer / AdminPeer / RegulatorPeer",
                          "• Raft 共识排序集群: Orderer 节点池 (高效共识出块、强防分叉机制)",
                          "• Go 语言智能合约链码: 碳核算指纹不可篡改存证、交易结算凭据存证、TxHash 穿透验证"],
                   bg='#FFFFFF')

    draw_package(8.4, 0.6, 6.8, 1.85, "基础设施与存储集群", stereotype="layer", bg='#F8FAFC')
    draw_component(8.6, 0.75, 6.4, 1.5, "数据存储集群 (Persistence Cluster)", stereotype="storage cluster",
                   items=["• MySQL 8.0: 关系型主库 (24 张核心业务实体表，Flyway V1~V13 版本化迁移)",
                          "• Redis 7.0: 内存缓存集群 (行情缓存、防刷令牌桶计数、分布式互斥锁)",
                          "• MinIO S3: 兼容对象存储 (原始化验单凭据扫描件、PDF 报告附件、审计留痕凭单)"],
                   bg='#FFFFFF')

    # =============================================================
    # 连线装配 (UML Assembly / Interface Connectors)
    # =============================================================
    # 表现层 -> 网关 -> REST
    draw_interface_conn((2.9, 7.75), (3.1, 7.75), "HTTP", style='-')
    draw_interface_conn((4.05, 7.0), (4.05, 6.55), "", style='-')
    draw_interface_conn((4.05, 6.55), (2.9, 6.0), "«HTTPS/REST 转发»", style='-')

    # 核心出站 -> 算法中台
    draw_interface_conn((13.1, 4.45), (13.1, 7.0), "«HTTP/JSON WebClient»", style='--')

    # 核心出站 -> 区块链存证
    draw_interface_conn((12.5, 4.65), (12.5, 2.55), "", style='-')
    draw_interface_conn((12.5, 2.55), (4.2, 2.55), "", style='-')
    draw_interface_conn((4.2, 2.55), (4.2, 2.25), "«gRPC Fabric Gateway»", style='-')

    # 核心出站 -> 数据存储
    draw_interface_conn((13.5, 3.10), (13.5, 2.45), "«JDBC / Redis / S3 API»", style='-')

    out_p = os.path.join(OUTPUT_DIR, filename)
    plt.savefig(out_p, dpi=300, bbox_inches='tight', facecolor='#FFFFFF')
    plt.close(fig)
    print(f"EA12 系统架构图生成完成: {out_p}")

if __name__ == '__main__':
    render_ea12_architecture_diagram()
