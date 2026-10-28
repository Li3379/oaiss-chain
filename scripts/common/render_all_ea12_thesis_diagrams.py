#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Enterprise Architect 12 (EA12) / UML 2.5 全套毕业设计工程图生成器 (300 DPI)
全面将原有的深色/暗蓝非规范图元重构为企业级工业规范：
1. 01-system-hexagonal-architecture.png: 系统总体分层与六边形微服务拓扑架构图 (图2.6)
2. 04-database-entity-er-diagram.png: 数据库 24 张核心业务实体 ER 逻辑关系模型图 (图3.1)
3. 08-frontend-vue3-architecture.png: 全系统工程架构与包组织关系图 (图3.11)
4. 06-cryptography-envelope-encryption.png: 密码学数字签名与链上存证流转顺序图 (图3.12)
5. 05-double-auction-matching-engine.png: 双向拍卖撮合引擎与 DVP 结算流转顺序图 (图3.13)
6. 10-ai-ml-microservice-architecture.png: AI 预测算法微服务调用与模型流水线顺序图 (图3.14)
7. 12-fullstack-testing-pyramid.png: 全栈分层测试金字塔与质量度量架构图 (图3.15)
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

def create_ea12_canvas(title_text, subtitle_text, w=16.0, h=9.8):
    fig, ax = plt.subplots(figsize=(w, h), dpi=300)
    fig.patch.set_facecolor('#FFFFFF')
    ax.set_facecolor('#FFFFFF')
    ax.set_xlim(0, w)
    ax.set_ylim(0, h)
    ax.axis('off')

    # 外框
    FRAME_X, FRAME_Y, FRAME_W, FRAME_H = 0.45, 0.4, w - 0.9, h - 0.8
    ax.add_patch(patches.Rectangle((FRAME_X, FRAME_Y), FRAME_W, FRAME_H,
                                   edgecolor='#94A3B8', facecolor='#FFFFFF', linewidth=1.2, zorder=1))

    # 左上角页签
    TAB_W, TAB_H = min(w * 0.46, len(title_text) * 0.115 + 0.8), 0.46
    ax.add_patch(patches.Rectangle((FRAME_X, FRAME_Y + FRAME_H - TAB_H), TAB_W, TAB_H,
                                   edgecolor='#94A3B8', facecolor='#F1F5F9', linewidth=1.0, zorder=2))
    ax.text(FRAME_X + 0.15, FRAME_Y + FRAME_H - TAB_H/2, title_text,
            fontsize=8.5, weight='bold', color='#1E293B', va='center', zorder=3)

    # 右上角标注
    ax.text(FRAME_X + FRAME_W - 0.2, FRAME_Y + FRAME_H - TAB_H/2, subtitle_text,
            fontsize=7.8, color='#64748B', ha='right', va='center', zorder=3)
    return fig, ax


# =========================================================================
# 1. 图 3.11: 全系统工程架构与包组织关系图 (08-frontend-vue3-architecture.png)
# =========================================================================
def render_fig_package_architecture():
    fig, ax = create_ea12_canvas("package [Model] OAISS-CHAIN [全系统工程架构与包组织关系图]",
                                "EA12 / UML 2.5 Package Diagram · 300 DPI")

    def draw_pkg(x, y, w, h, name, stereotype="package", items=[], bg='#FAFAFA', border='#64748B'):
        tab_w = min(w * 0.6, max(2.2, len(name) * 0.11 + 0.7))
        tab_h = 0.32
        ax.add_patch(patches.Rectangle((x, y + h), tab_w, tab_h,
                                       edgecolor=border, facecolor=bg, linewidth=1.1, zorder=2))
        ax.text(x + 0.12, y + h + tab_h/2, f"«{stereotype}» {name}",
                fontsize=7.5, weight='bold', color='#334155', va='center', zorder=3)
        ax.add_patch(patches.Rectangle((x, y), w, h,
                                       edgecolor=border, facecolor=bg, linewidth=1.1, zorder=2))
        if items:
            for i, it in enumerate(items):
                ax.text(x + 0.18, y + h - 0.28 - i * 0.22, it,
                        fontsize=7.0, color='#334155', va='center', zorder=4)

    def draw_dep(p1, p2, label=""):
        ax.annotate('', xy=p2, xytext=p1,
                    arrowprops=dict(arrowstyle="->,head_width=0.14,head_length=0.20",
                                    color='#475569', lw=1.1, linestyle='--',
                                    shrinkA=0, shrinkB=0),
                    zorder=3)
        if label:
            mx, my = (p1[0] + p2[0])/2, (p1[1] + p2[1])/2
            ax.text(mx, my + 0.08, label, fontsize=6.6, color='#1E293B', ha='center', va='bottom',
                    bbox=dict(boxstyle='square,pad=0.12', facecolor='#FFFFFF', edgecolor='#CBD5E1', linewidth=0.6),
                    zorder=5)

    # 1. 前端工程包: oaiss-chain-frontend (X: 0.8 ~ 5.4, Y: 1.0 ~ 8.6)
    draw_pkg(0.8, 1.0, 4.6, 7.6, "oaiss-chain-frontend (Vue 3.5 SPA)", stereotype="root package", bg='#F8FAFC')
    draw_pkg(1.1, 6.6, 4.0, 1.6, "src.views (四角色工作台视图)", stereotype="package",
             items=["• views.enterprise: 碳核算填报 / 资产管理 / 拍卖市场",
                    "• views.auditor: 待审报告认领 / RSA 验签 / 结构化驳回",
                    "• views.third_party: 宏观态势大屏 / TxHash 穿透 / 存证管理",
                    "• views.admin: 准入审查 / 证书签发 / 账号审计 / 节点治理"], bg='#FFFFFF')
    draw_pkg(1.1, 4.7, 4.0, 1.5, "src.api (22 个 REST 客户端模块)", stereotype="package",
             items=["• api.carbon / api.trade / api.credit: 业务端点封装",
                    "• api.auth / api.user: 双令牌鉴权与验证码拦截",
                    "• api.blockchain / api.ml: 链上穿透与 AI 算法接口",
                    "• utils.request: Axios 统一请求/响应拦截与状态守卫"], bg='#FFFFFF')
    draw_pkg(1.1, 2.9, 4.0, 1.4, "src.stores (Pinia 3 状态中心)", stereotype="package",
             items=["• stores.user: 用户凭证、角色列表与路由权限映射",
                    "• stores.carbon: 碳排放草稿数据与动态公式中间推导",
                    "• stores.app: 国际化双语词条、侧边栏导航与主题模式"], bg='#FFFFFF')
    draw_pkg(1.1, 1.3, 4.0, 1.2, "src.router (路由守卫与导航网格)", stereotype="package",
             items=["• router.guards: beforeEach JWT 有效性与角权限拦截",
                    "• router.routes: 28 个核心路由扁平化映射"], bg='#FFFFFF')

    # 2. 后端工程包: oaiss-chain-backend (X: 5.8 ~ 11.2, Y: 1.0 ~ 8.6)
    draw_pkg(5.8, 1.0, 5.4, 7.6, "oaiss-chain-backend (Spring Boot 3.2.12)", stereotype="root package", bg='#F8FAFC')
    draw_pkg(6.1, 7.0, 4.8, 1.2, "com.oaiss.controller (入站控制器)", stereotype="package",
             items=["• 22 个 REST 控制器 (CarbonReportController, AuctionController, etc.)",
                    "• JSON 契约数据验证 (@Valid) 与全局异常处理器 (GlobalExceptionHandler)"], bg='#FFFFFF')
    draw_pkg(6.1, 5.4, 4.8, 1.2, "com.oaiss.security (安全防护与 AOP)", stereotype="package",
             items=["• JwtAuthenticationFilter / TokenProvider (双 Token 刷新机制)",
                    "• @AuditLogAspect (敏感操作自动审计入库) / RateLimitAspect (IP 防刷)"], bg='#FFFFFF')
    draw_pkg(6.1, 3.5, 4.8, 1.5, "com.oaiss.service (领域服务核心内核)", stereotype="package",
             items=["• 35 个领域服务类 (AccountingEngine, AuctionMatchingEngine)",
                    "• 密码学服务 (CryptoManager: RSA-2048 验签 / AES-GCM 信封加解密)",
                    "• 信用评价引擎 (CreditScoreService) / 证书服务 (CertificateService)",
                    "• 事务外发箱服务 (OutboxMessageService: 最终一致性事件发布)"], bg='#FFFFFF')
    draw_pkg(6.1, 2.0, 4.8, 1.1, "com.oaiss.repository (JPA 数据持久化)", stereotype="package",
             items=["• 24 个 Spring Data JPA Repository 接口 (带软删除与索引过滤)",
                    "• 复杂多表连接与动态条件规约查询 (Specification)"], bg='#FFFFFF')
    draw_pkg(6.1, 1.2, 4.8, 0.55, "com.oaiss.model (实体与传输对象: Entity / DTO / VO / Enums)", stereotype="package",
             items=["• BaseEntity 审计基类 / 24 张核心业务实体映射类"], bg='#FFFFFF')

    # 3. 算法微服务与区块链包 (X: 11.6 ~ 15.6)
    draw_pkg(11.6, 5.0, 4.0, 3.6, "oaiss-chain-ml-service (FastAPI)", stereotype="root package", bg='#F8FAFC')
    draw_pkg(11.8, 6.7, 3.6, 1.5, "app.engine (算法核心模型)", stereotype="package",
             items=["• Prophet 价格预测时序引擎",
                    "• XGBoost 特征重要度归因分析",
                    "• Isolation Forest 异常模式检测",
                    "• Qwen 2.5 7B 大模型异步推理"], bg='#FFFFFF')
    draw_pkg(11.8, 5.2, 3.6, 1.1, "app.routers (REST 算法端点)", stereotype="package",
             items=["• /api/v1/predict/price (碳价预测通道)",
                    "• /api/v1/anomaly/detect (合规偏离雷达)"], bg='#FFFFFF')

    draw_pkg(11.6, 1.0, 4.0, 3.6, "oaiss-chain-chaincode (Fabric)", stereotype="root package", bg='#F8FAFC')
    draw_pkg(11.8, 2.7, 3.6, 1.5, "chaincode.go (Go 智能合约)", stereotype="package",
             items=["• CarbonVerificationContract (核算存证)",
                    "• TradeClearingContract (撮合原子固化)",
                    "• ChaincodeStub 账本存取与事件发射"], bg='#FFFFFF')
    draw_pkg(11.8, 1.2, 3.6, 1.1, "fabric.gateway (联盟网络契约)", stereotype="package",
             items=["• 4 组织 Peer 节点与 Orderer 共识通信",
                    "• mychannel 业务通道背书策略"], bg='#FFFFFF')

    # 依赖连线
    draw_dep((5.4, 5.4), (5.8, 7.5), "«HTTPS/REST 调用»")
    draw_dep((8.5, 7.0), (8.5, 6.6), "«依赖鉴权»")
    draw_dep((8.5, 5.4), (8.5, 5.0), "«调用业务服务»")
    draw_dep((8.5, 3.5), (8.5, 3.1), "«持久化访问»")
    draw_dep((10.9, 4.5), (11.6, 5.8), "«WebClient 算法调用»")
    draw_dep((10.9, 3.8), (11.6, 2.5), "«gRPC 链码背书上链»")

    out_p = os.path.join(OUTPUT_DIR, '08-frontend-vue3-architecture.png')
    fig.savefig(out_p, dpi=300, bbox_inches='tight', facecolor='#FFFFFF')
    plt.close(fig)
    print(f"EA12 包组织架构图生成完成: {out_p}")


# =========================================================================
# 2. 图 3.12: 密码学数字签名与链上存证顺序图 (06-cryptography-envelope-encryption.png)
# =========================================================================
def render_fig_crypto_sequence():
    fig, ax = create_ea12_canvas("interaction [Sequence] OAISS-CHAIN [密码学数字签名与链上存证流转顺序图]",
                                "EA12 / UML 2.5 Sequence Diagram · 300 DPI")

    lifelines = [
        (1.2, "控排企业前端\n(Enterprise Web)"),
        (3.2, "核心接入网关\n(ReportController)"),
        (5.5, "碳核算引擎\n(AccountingService)"),
        (8.0, "密码学中心\n(CryptoManager)"),
        (10.5, "审核员工作台\n(Auditor Web)"),
        (12.7, "事务外发箱\n(OutboxService)"),
        (14.8, "Fabric 联盟链\n(Fabric Gateway)")
    ]

    top_y, bot_y = 8.4, 0.9

    for x, name in lifelines:
        # 生命线头部
        ax.add_patch(patches.Rectangle((x - 0.75, top_y), 1.5, 0.65,
                                       edgecolor='#475569', facecolor='#F8FAFC', linewidth=1.1, zorder=3))
        ax.text(x, top_y + 0.32, name, fontsize=7.2, weight='bold', color='#0F172A',
                ha='center', va='center', zorder=4)
        # 垂直虚线生命线
        ax.plot([x, x], [top_y, bot_y], color='#94A3B8', linestyle='--', linewidth=0.9, zorder=1)

    # 激活块
    def draw_activation(x, y1, y2):
        ax.add_patch(patches.Rectangle((x - 0.10, y2), 0.20, y1 - y2,
                                       edgecolor='#475569', facecolor='#FFFFFF', linewidth=0.9, zorder=2))

    # 消息箭头
    def draw_msg(x1, x2, y, text, is_async=False, is_return=False):
        style = '--' if (is_async or is_return) else '-'
        color = '#334155'
        ax.annotate('', xy=(x2, y), xytext=(x1, y),
                    arrowprops=dict(arrowstyle="->,head_width=0.12,head_length=0.18",
                                    color=color, lw=1.0, linestyle=style,
                                    shrinkA=0, shrinkB=0),
                    zorder=3)
        mx = (x1 + x2) / 2
        ax.text(mx, y + 0.06, text, fontsize=6.6, color='#1E293B', ha='center', va='bottom', zorder=4)

    # 绘制流程
    # 1. 试算
    draw_activation(1.2, 8.1, 7.3)
    draw_activation(3.2, 8.0, 7.4)
    draw_activation(5.5, 7.9, 7.5)
    draw_msg(1.2, 3.2, 8.0, "1: POST /carbon/calculate (录入活动台账)")
    draw_msg(3.2, 5.5, 7.9, "1.1: 执行国标公式实时推导与碳氧化率反推")
    draw_msg(5.5, 3.2, 7.6, "1.2: 返回试算排放总量与分项数据", is_return=True)
    draw_msg(3.2, 1.2, 7.4, "1.3: 响应核算报告预览数据", is_return=True)

    # 2. 本地私钥签名与送审
    draw_activation(1.2, 7.1, 6.2)
    draw_activation(8.0, 7.0, 6.6)
    draw_msg(1.2, 8.0, 7.0, "2: AES-GCM 解密私钥并执行 RSA-2048 签名")
    draw_msg(8.0, 1.2, 6.7, "2.1: 返回数字签名密文 Signature", is_return=True)
    draw_activation(3.2, 6.5, 5.6)
    draw_activation(5.5, 6.4, 5.7)
    draw_msg(1.2, 3.2, 6.5, "3: POST /carbon/reports (提交报告与签名)")
    draw_msg(3.2, 5.5, 6.4, "3.1: 校验报告唯一性并持久化 (状态: SUBMITTED)")
    draw_msg(5.5, 1.2, 5.8, "3.2: 报告提审成功 (返回 Report ID)", is_return=True)

    # 3. 审核员验签与出具决定
    draw_activation(10.5, 5.5, 4.3)
    draw_activation(3.2, 5.4, 4.4)
    draw_activation(8.0, 5.2, 4.6)
    draw_msg(10.5, 3.2, 5.4, "4: GET /carbon/reports/{id}/verify (调取待审报告)")
    draw_msg(3.2, 8.0, 5.2, "4.1: 调取登记公钥执行 RSA_Verify(PK, Hash, Sig)")
    draw_msg(8.0, 10.5, 4.7, "4.2: 验签结果 PASS: 报告未被篡改", is_return=True)
    draw_msg(10.5, 3.2, 4.4, "5: POST /carbon/certify (签署审核通过决定)")

    # 4. Outbox 事务外发箱与 Fabric 链上存证
    draw_activation(3.2, 4.2, 3.0)
    draw_activation(12.7, 4.0, 2.0)
    draw_activation(14.8, 3.7, 1.4)
    draw_msg(3.2, 12.7, 4.0, "6: 本地事务内写入 outbox_message (事件: REPORT_CERTIFIED)")
    draw_msg(12.7, 14.8, 3.7, "7: Outbox 调度器异步调用 Fabric Gateway gRPC (上链存证)")
    draw_msg(14.8, 14.8, 3.2, "7.1: 智能合约校验双重背书并广播共识出块")
    draw_msg(14.8, 12.7, 2.5, "7.2: 生成交易哈希 TxHash 并回执", is_return=True)
    draw_msg(12.7, 3.2, 2.1, "8: 回填报告 tx_hash 与 block_number (状态: ON_CHAIN)", is_return=True)
    draw_msg(3.2, 1.2, 1.5, "9: 异步 WebSocket 通知企业用户: 报告已成功上链存证", is_async=True)

    out_p = os.path.join(OUTPUT_DIR, '06-cryptography-envelope-encryption.png')
    fig.savefig(out_p, dpi=300, bbox_inches='tight', facecolor='#FFFFFF')
    plt.close(fig)
    print(f"EA12 密码学顺序图生成完成: {out_p}")


# =========================================================================
# 3. 图 3.13: 双向拍卖撮合引擎与 DVP 结算流转顺序图 (05-double-auction-matching-engine.png)
# =========================================================================
def render_fig_auction_sequence():
    fig, ax = create_ea12_canvas("interaction [Sequence] OAISS-CHAIN [连续双向拍卖撮合引擎与 DVP 结算顺序图]",
                                "EA12 / UML 2.5 Sequence Diagram · 300 DPI")

    lifelines = [
        (1.2, "买方企业\n(Buyer Enterprise)"),
        (3.5, "卖方企业\n(Seller Enterprise)"),
        (6.0, "交易控制器\n(AuctionController)"),
        (8.5, "撮合核心引擎\n(MatchingEngine)"),
        (11.0, "资产清算服务\n(ClearingService)"),
        (13.2, "资金与配额账户\n(AccountService)"),
        (15.0, "Fabric 联盟链\n(BlockchainService)")
    ]

    top_y, bot_y = 8.4, 0.9

    for x, name in lifelines:
        ax.add_patch(patches.Rectangle((x - 0.75, top_y), 1.5, 0.65,
                                       edgecolor='#475569', facecolor='#F8FAFC', linewidth=1.1, zorder=3))
        ax.text(x, top_y + 0.32, name, fontsize=7.2, weight='bold', color='#0F172A',
                ha='center', va='center', zorder=4)
        ax.plot([x, x], [top_y, bot_y], color='#94A3B8', linestyle='--', linewidth=0.9, zorder=1)

    def draw_activation(x, y1, y2):
        ax.add_patch(patches.Rectangle((x - 0.10, y2), 0.20, y1 - y2,
                                       edgecolor='#475569', facecolor='#FFFFFF', linewidth=0.9, zorder=2))

    def draw_msg(x1, x2, y, text, is_async=False, is_return=False):
        style = '--' if (is_async or is_return) else '-'
        ax.annotate('', xy=(x2, y), xytext=(x1, y),
                    arrowprops=dict(arrowstyle="->,head_width=0.12,head_length=0.18",
                                    color='#334155', lw=1.0, linestyle=style,
                                    shrinkA=0, shrinkB=0),
                    zorder=3)
        mx = (x1 + x2) / 2
        ax.text(mx, y + 0.06, text, fontsize=6.6, color='#1E293B', ha='center', va='bottom', zorder=4)

    # 流程
    draw_activation(1.2, 8.0, 7.2)
    draw_activation(6.0, 7.9, 7.1)
    draw_activation(13.2, 7.7, 7.3)
    draw_msg(1.2, 6.0, 7.9, "1: POST /trade/orders (买方提交限价单 Bid: P_b, Q_b)")
    draw_msg(6.0, 13.2, 7.7, "1.1: 冻结买方 carbon_coin 资金 (P_b * Q_b)")
    draw_msg(13.2, 6.0, 7.4, "1.2: 资金冻结成功", is_return=True)
    draw_msg(6.0, 8.5, 7.2, "1.3: 买单入队 BidQueue (按价格降序/时间升序)")

    draw_activation(3.5, 7.0, 6.2)
    draw_activation(6.0, 6.9, 6.1)
    draw_activation(13.2, 6.7, 6.3)
    draw_msg(3.5, 6.0, 6.9, "2: POST /trade/orders (卖方提交限价单 Ask: P_s, Q_s)")
    draw_msg(6.0, 13.2, 6.7, "2.1: 锁定卖方碳配额资产 (Q_s)")
    draw_msg(13.2, 6.0, 6.4, "2.2: 配额锁定成功", is_return=True)
    draw_msg(6.0, 8.5, 6.2, "2.3: 卖单入队 AskQueue (按价格升序/时间升序)")

    draw_activation(8.5, 6.0, 4.4)
    draw_activation(11.0, 5.3, 2.2)
    draw_msg(8.5, 8.5, 5.8, "3: 触发微批次撮合算法: 判定 Max(Bid) >= Min(Ask)")
    draw_msg(8.5, 8.5, 5.4, "3.1: 计算清算均价 P_m = (P_b+P_s)/2, 成交量 Q_m = Min(Q_b,Q_s)")
    draw_msg(8.5, 11.0, 5.0, "4: 生成撮合记录 MatchingResult 并移交 DVP 结算")

    draw_activation(13.2, 4.8, 3.2)
    draw_msg(11.0, 13.2, 4.7, "5: 执行 DVP 原子过户 (划转 Q_m 碳配额至买方账户)")
    draw_msg(11.0, 13.2, 4.2, "5.1: 结算划扣买方资金 P_m * Q_m 至卖方账户")
    draw_msg(11.0, 13.2, 3.7, "5.2: 差价返还买方: (P_b - P_m) * Q_m")
    draw_msg(13.2, 11.0, 3.3, "5.3: 资金与资产划转成功", is_return=True)

    draw_activation(15.0, 2.9, 1.5)
    draw_msg(11.0, 15.0, 2.8, "6: 异步向 Fabric 链码提交撮合存证记录 (Invoke TradeClearing)")
    draw_msg(15.0, 11.0, 2.0, "6.1: 账本写入完成并返回交易哈希 TxHash", is_return=True)
    draw_msg(11.0, 1.2, 1.6, "7: 通知买卖双方企业: 交易撮合与 DVP 原子结算成功", is_async=True)

    out_p = os.path.join(OUTPUT_DIR, '05-double-auction-matching-engine.png')
    fig.savefig(out_p, dpi=300, bbox_inches='tight', facecolor='#FFFFFF')
    plt.close(fig)
    print(f"EA12 双向拍卖顺序图生成完成: {out_p}")


# =========================================================================
# 4. 图 3.14: AI 预测算法微服务调用与模型流水线顺序图 (10-ai-ml-microservice-architecture.png)
# =========================================================================
def render_fig_ai_sequence():
    fig, ax = create_ea12_canvas("interaction [Sequence] OAISS-CHAIN [AI 碳价预测与算法微服务调用顺序图]",
                                "EA12 / UML 2.5 Sequence Diagram · 300 DPI")

    lifelines = [
        (1.2, "第三方监管前端\n(Regulator Web)"),
        (3.4, "Spring 后端网关\n(PredictionCtrl)"),
        (5.8, "异步 WebClient\n(MLClientAdapter)"),
        (8.2, "FastAPI 微服务\n(PredictionEngine)"),
        (10.6, "Prophet 模型\n(TimeSeriesForecaster)"),
        (12.8, "XGBoost 模型\n(FeatureAttribution)"),
        (15.0, "Qwen 2.5 大模型\n(CopilotInference)")
    ]

    top_y, bot_y = 8.4, 0.9

    for x, name in lifelines:
        ax.add_patch(patches.Rectangle((x - 0.75, top_y), 1.5, 0.65,
                                       edgecolor='#475569', facecolor='#F8FAFC', linewidth=1.1, zorder=3))
        ax.text(x, top_y + 0.32, name, fontsize=7.2, weight='bold', color='#0F172A',
                ha='center', va='center', zorder=4)
        ax.plot([x, x], [top_y, bot_y], color='#94A3B8', linestyle='--', linewidth=0.9, zorder=1)

    def draw_activation(x, y1, y2):
        ax.add_patch(patches.Rectangle((x - 0.10, y2), 0.20, y1 - y2,
                                       edgecolor='#475569', facecolor='#FFFFFF', linewidth=0.9, zorder=2))

    def draw_msg(x1, x2, y, text, is_async=False, is_return=False):
        style = '--' if (is_async or is_return) else '-'
        ax.annotate('', xy=(x2, y), xytext=(x1, y),
                    arrowprops=dict(arrowstyle="->,head_width=0.12,head_length=0.18",
                                    color='#334155', lw=1.0, linestyle=style,
                                    shrinkA=0, shrinkB=0),
                    zorder=3)
        mx = (x1 + x2) / 2
        ax.text(mx, y + 0.06, text, fontsize=6.6, color='#1E293B', ha='center', va='bottom', zorder=4)

    # 流程
    draw_activation(1.2, 8.0, 7.3)
    draw_activation(3.4, 7.9, 7.4)
    draw_activation(5.8, 7.8, 2.2)
    draw_msg(1.2, 3.4, 7.9, "1: GET /api/v1/ml/dashboard (请求宏观碳价与预测分析)")
    draw_msg(3.4, 5.8, 7.7, "2: 提取历史行情、履约事件与宏观煤炭特征数据")

    draw_activation(8.2, 7.4, 2.5)
    draw_msg(5.8, 8.2, 7.4, "3: POST /api/v1/predict/price (异步发送特征向量)")

    draw_activation(10.6, 7.1, 5.9)
    draw_msg(8.2, 10.6, 7.1, "4: 启动 Prophet 时序分解: 趋势基线/周期项/节假日特征")
    draw_msg(10.6, 10.6, 6.6, "4.1: 蒙特卡洛模拟推导未来 30/90/180 天 95% 置信区间")
    draw_msg(10.6, 8.2, 6.1, "4.2: 返回碳价点预测与上/下界带状序列", is_return=True)

    draw_activation(12.8, 5.7, 4.4)
    draw_msg(8.2, 12.8, 5.7, "5: 启动 XGBoost 树模型: 计算各维度 SHAP 特征重要度")
    draw_msg(12.8, 8.2, 4.6, "5.1: 输出能源价格/履约周期等价格驱动因子权重排序", is_return=True)

    draw_activation(15.0, 4.2, 2.9)
    draw_msg(8.2, 15.0, 4.2, "6: 组装 Prompt (预测结果 + 归因向量) 请求大模型诊断")
    draw_msg(15.0, 8.2, 3.1, "6.1: 流式返回结构化合规诊断建议文本", is_return=True)

    draw_msg(8.2, 5.8, 2.5, "7: 聚合返回预测曲线、归因权重与诊断结论", is_return=True)
    draw_msg(5.8, 3.4, 2.0, "8: 组装 REST Response (R<MLDashboardVO>)", is_return=True)
    draw_msg(3.4, 1.2, 1.5, "9: 渲染 ECharts 金融看板与偏离雷达图", is_return=True)

    out_p = os.path.join(OUTPUT_DIR, '10-ai-ml-microservice-architecture.png')
    fig.savefig(out_p, dpi=300, bbox_inches='tight', facecolor='#FFFFFF')
    plt.close(fig)
    print(f"EA12 AI 算法顺序图生成完成: {out_p}")


# =========================================================================
# 5. 图 3.15: 全栈分层测试金字塔与质量度量架构图 (12-fullstack-testing-pyramid.png)
# =========================================================================
def render_fig_testing_pyramid():
    fig, ax = create_ea12_canvas("package [Model] OAISS-CHAIN [全栈分层测试金字塔与质量度量架构图]",
                                "EA12 / ISO-25010 Quality Model · 300 DPI")

    # 金字塔梯形层绘制
    # 底部基准: y=1.2, 顶部 y=8.0, 宽度自下而上收拢
    tiers = [
        # (y_bot, y_top, w_bot, w_top, title, metrics, bg, border)
        (1.2, 2.9, 14.4, 12.4,
         "Level 1: 持续集成与代码质量工程门禁 (Quality Gates & Architecture Discipline)",
         ["• doc-verify.mjs: 22 控制器 / 143 端点 / 24 实体表 100% 零漂移强校验 (违规直接阻断构建)",
          "• i18n-verify.mjs: 1430 键中文/英文全对称零遗漏门禁 (消除任何死链与孤儿键)",
          "• closure-audit.mjs: 5 份标准资产与 8 级质量闸门全量自动核验",
          "• Flyway 数据库迁移基线门禁: V1 ~ V13 脚本校验和严格一致，禁止非法逆向修改"],
         '#F8FAFC', '#64748B'),

        (3.1, 4.8, 12.0, 9.8,
         "Level 2: 单元与领域计算测试 (Unit & Domain Logic Tests - 3,261 例)",
         ["• 后端 JUnit 5 + Mockito (2,881 例全绿): 覆盖碳核算推导、双向拍卖撮合、RSA 验签逻辑",
          "• 前端 Vitest + Vue Test Utils (344 例全绿): 组件交互、Pinia 状态、表单滚动定位",
          "• 算法微服务 Pytest (36 例全绿): Prophet 拟合、XGBoost 推理、Isolation Forest 检测",
          "• 智能合约链码单元测试 (Fabric ccenv): Go 语言模拟账本 PutState / GetState 100% 覆盖"],
         '#EFF6FF', '#0284C7'),

        (5.0, 6.7, 9.4, 7.0,
         "Level 3: 服务集成与安全隔离测试 (Integration & Security Tests)",
         ["• Spring Boot @SpringBootTest / @AutoConfigureMockMvc: 143 个接口全覆盖",
          "• Spring Security 跨租户越权防护核验 (企业不可读写其他主体报告与资产)",
          "• Redisson 分布式锁高并发幂等测试: 并发挂单与转账 0 资金超扣、0 双花",
          "• Fabric Gateway gRPC 集成测试: 真实背书出块与持久化状态机流转"],
         '#F0FDF4', '#16A34A'),

        (6.9, 8.6, 6.6, 4.2,
         "Level 4: 端到端全角色验收测试 (E2E UAT)",
         ["• Playwright 自动化黑盒 E2E (28 条全链路)",
          "• 覆盖 4 大核心角色全生命周期交互闭环",
          "• 真实上链共识出块 tx 穿透核验 100% 通过",
          "• 控制台 0 Error、网络 0 异常失败"],
         '#FFFBEB', '#D97706')
    ]

    for yb, yt, wb, wt, title, items, bg, border in tiers:
        cx = 8.0
        # 梯形顶点坐标
        pts = [
            [cx - wb/2, yb],
            [cx + wb/2, yb],
            [cx + wt/2, yt],
            [cx - wt/2, yt]
        ]
        poly = patches.Polygon(pts, closed=True, edgecolor=border, facecolor=bg, linewidth=1.1, zorder=2)
        ax.add_patch(poly)

        # 标题
        ax.text(cx, yt - 0.26, title, fontsize=8.2, weight='bold', color='#0F172A',
                ha='center', va='center', zorder=3)
        # 项目列表
        for i, it in enumerate(items):
            ax.text(cx, yt - 0.55 - i * 0.25, it, fontsize=7.0, color='#334155',
                    ha='center', va='center', zorder=3)

    out_p = os.path.join(OUTPUT_DIR, '12-fullstack-testing-pyramid.png')
    fig.savefig(out_p, dpi=300, bbox_inches='tight', facecolor='#FFFFFF')
    plt.close(fig)
    print(f"EA12 测试金字塔图生成完成: {out_p}")


def main():
    print("开始依据 Enterprise Architect 12 (EA12) 工业规范重构全部核心工程图...")
    render_fig_package_architecture()
    render_fig_crypto_sequence()
    render_fig_auction_sequence()
    render_fig_ai_sequence()
    render_fig_testing_pyramid()
    print("全部 EA12 工业工程图已全部渲染完成！")

if __name__ == '__main__':
    main()
