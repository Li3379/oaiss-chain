#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OAISS CHAIN - 高清企业级工程架构与关系大图渲染生成器 (300 DPI)
生成 7 张专业架构关系图，彻底摒弃纯文字罗列，实现图文并茂：
1. 01-system-hexagonal-architecture.png: 系统总体六边形微服务分层拓扑架构图
2. 02-four-roles-collaboration.png: 四角色业务协同与权限流转全景关系图
3. 03-carbon-lifecycle-state-machine.png: 碳核算全生命周期状态机与数据流图
4. 04-database-entity-er-diagram.png: 数据库 24 张核心业务实体 ER 逻辑关系图
5. 05-double-auction-matching-engine.png: 连续双向拍卖撮合引擎与 DVP 结算流转图
6. 06-cryptography-envelope-encryption.png: 密码学 RSA 数字签名与 AES-GCM 信封加密图
7. 07-release-gates-pipeline.png: 五级持续交付发布阻断门禁流水线流转图
"""

import os
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.patches as patches

OUTPUT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '../../docs/assets/architecture'))
os.makedirs(OUTPUT_DIR, exist_ok=True)

# 统一中文字体与全局样式
plt.rcParams['font.sans-serif'] = ['Microsoft YaHei', 'SimHei', 'DejaVu Sans']
plt.rcParams['axes.unicode_minus'] = False

def create_canvas(title, subtitle, w=16, h=9):
    fig, ax = plt.subplots(figsize=(w, h), dpi=300)
    fig.patch.set_facecolor('#0B1120') # 深邃科技暗蓝底
    ax.set_facecolor('#0B1120')
    ax.set_xlim(0, w)
    ax.set_ylim(0, h)
    ax.axis('off')
    
    # 顶部装饰与标头
    ax.text(0.8, h - 0.55, title, fontsize=18, color='#F8FAFC', weight='bold')
    ax.text(0.8, h - 0.95, subtitle, fontsize=10.5, color='#38BDF8')
    ax.plot([0.8, w - 0.8], [h - 1.15, h - 1.15], color='#334155', linewidth=1.2)
    return fig, ax

def draw_node(ax, x, y, w, h, title, items=[], border_color='#38BDF8', bg_color='#1E293B', title_color='#F8FAFC'):
    box = patches.FancyBboxPatch((x, y), w, h, boxstyle='round,pad=0.02,rounding_size=0.12',
                                edgecolor=border_color, facecolor=bg_color, linewidth=1.4)
    ax.add_patch(box)
    ax.text(x + w/2, y + h - 0.32, title, color=title_color, fontsize=11, weight='bold', ha='center', va='center')
    for idx, item in enumerate(items):
        ax.text(x + 0.22, y + h - 0.65 - idx * 0.28, item, color='#94A3B8', fontsize=8.8, ha='left', va='center')

def draw_arrow(ax, x1, y1, x2, y2, color='#38BDF8', label=''):
    ax.annotate('', xy=(x2, y2), xytext=(x1, y1),
                arrowprops=dict(arrowstyle="->", color=color, lw=1.6, mutation_scale=14))
    if label:
        mx, my = (x1 + x2)/2, (y1 + y2)/2
        ax.text(mx, my + 0.12, label, color='#F1F5F9', fontsize=8, ha='center', va='bottom',
                bbox=dict(boxstyle='round,pad=0.15', facecolor='#0B1120', edgecolor='#334155', alpha=0.85))

# ==========================================
# 图 1: 系统总体分层与六边形微服务架构图
# ==========================================
def render_fig_01():
    fig, ax = create_canvas("OAISS CHAIN · 系统总体分层与六边形微服务拓扑架构图",
                            "Hexagonal Architecture & Microservices Decoupling Overview")
    
    # 1. 表现层
    draw_node(ax, 0.8, 6.2, 3.2, 1.4, "1. 表现接入层 (Web SPA)",
              ["• Vue 3.5 + Vite + TypeScript", "• Element Plus + ECharts 6.0", "• 四角色工作台独立路由"],
              border_color='#38BDF8', bg_color='#1E293B')
    
    # 2. 网关层
    draw_node(ax, 4.5, 6.2, 2.6, 1.4, "2. 边缘网关 (Gateway)",
              ["• Nginx 1.25 反向代理", "• SSL/TLS 证书终端卸载", "• URL 分流路由控制"],
              border_color='#60A5FA', bg_color='#1E293B')
    
    # 3. 核心中台 (Spring Boot 六边形核心)
    draw_node(ax, 7.6, 4.4, 4.4, 3.2, "3. 核心业务中台 (Spring Boot 3.2.12)",
              ["• 22 REST Controllers (143 Endpoints)",
               "• Spring Security + JWT 双 Token 鉴权",
               "• 35 领域服务 (核算/撮合/账户/信誉)",
               "• AOP 审计日志 (@AuditLog) & 防刷流控",
               "• Outbox 事务外箱与可靠事件分发",
               "• Resilience4j 熔断器 & WebClient"],
              border_color='#10B981', bg_color='#064E3B', title_color='#34D399')
    
    # 4. 算法中台 (FastAPI)
    draw_node(ax, 12.5, 5.8, 2.7, 1.8, "4. 算法智能中台 (FastAPI)",
              ["• Python 3.12 异步引擎", "• Prophet 碳价时序分解", "• XGBoost 多维归因", "• Qwen 2.5 7B Copilot"],
              border_color='#F59E0B', bg_color='#451A03', title_color='#FBBF24')
    
    # 5. 存储层
    draw_node(ax, 0.8, 2.0, 3.8, 1.8, "5. 基础存储集群 (Storage)",
              ["• MySQL 8.0 (24 张核心业务表)",
               "• Redis 7.0 (分布式锁/行情缓存)",
               "• MinIO S3 (凭据与报告附件对象池)"],
              border_color='#A855F7', bg_color='#3B0764', title_color='#C084FC')

    # 6. 区块链存证层
    draw_node(ax, 5.2, 2.0, 4.8, 1.8, "6. 可信存证与联盟链 (Hyperledger Fabric 2.5)",
              ["• 组织 Peer 节点群 (Enterprise / Reviewer / Admin / Regulator)",
               "• Go 语言智能合约链码 (碳核算指纹/交易存证)",
               "• Raft 共识排序广播 (Orderer 节点集群)"],
              border_color='#EC4899', bg_color='#500724', title_color='#F472B6')

    # 7. 质量门禁与观测
    draw_node(ax, 10.6, 2.0, 4.6, 1.8, "7. 质量治理与观测中台 (DevOps)",
              ["• 120 项全自动化 E2E 测试套件",
               "• 零漂移架构事实门禁 (doc-verify)",
               "• 1430 键对称国际化门禁 (i18n-verify)"],
              border_color='#64748B', bg_color='#1E293B', title_color='#94A3B8')

    # 连接线
    draw_arrow(ax, 4.0, 6.9, 4.5, 6.9, '#38BDF8', 'HTTPS 请求')
    draw_arrow(ax, 7.1, 6.9, 7.6, 6.9, '#60A5FA', '/api/v1/* 转发')
    draw_arrow(ax, 12.0, 6.7, 12.5, 6.7, '#F59E0B', 'WebClient 调用')
    draw_arrow(ax, 8.5, 4.4, 3.0, 3.8, '#A855F7', 'JPA / Redisson')
    draw_arrow(ax, 9.8, 4.4, 7.6, 3.8, '#EC4899', 'Fabric Gateway gRPC')

    fig.savefig(os.path.join(OUTPUT_DIR, '01-system-hexagonal-architecture.png'), bbox_inches='tight')
    plt.close(fig)
    print("生成完成: 01-system-hexagonal-architecture.png")

# ==========================================
# 图 2: 四角色业务协同与权限流转全景关系图
# ==========================================
def render_fig_02():
    fig, ax = create_canvas("OAISS CHAIN · 四角色业务协同与权限流转全景关系图",
                            "Four-Role Collaborative Workflow & RBAC Permission Matrix")

    draw_node(ax, 1.0, 5.0, 3.2, 2.4, "企业用户 (ENTERPRISE)",
              ["• 角色标识: ENTERPRISE",
               "• 填报火力/电网碳排放台账",
               "• 本地 RSA 私钥签名报告",
               "• 提交核算审批与资质申请",
               "• 发起双向拍卖挂单与 P2P 撮合"],
              border_color='#38BDF8', bg_color='#0C4A6E', title_color='#7DD3FC')

    draw_node(ax, 6.4, 5.0, 3.2, 2.4, "数据审核员 (REVIEWER)",
              ["• 角色标识: REVIEWER",
               "• 认领辖区待审企业报告",
               "• 查验原始化验凭单与附件",
               "• 调取企业公钥执行 RSA 验签",
               "• 出具审查结论 (通过/驳回)",
               "• 生成联合签名推送到核查池"],
              border_color='#10B981', bg_color='#064E3B', title_color='#6EE7B7')

    draw_node(ax, 11.8, 5.0, 3.2, 2.4, "第三方监管机构 (THIRD_PARTY)",
              ["• 角色标识: THIRD_PARTY",
               "• 宏观区域双碳综合监管大屏",
               "• 终审合规核查出具公信结论",
               "• 触发 Fabric 链码上链固化存证",
               "• 穿透查询链上 TxHash 与区块"],
              border_color='#F59E0B', bg_color='#451A03', title_color='#FCD34D')

    draw_node(ax, 6.4, 1.2, 3.2, 2.4, "系统管理员 (ADMIN)",
              ["• 角色标识: ADMIN",
               "• 机构与企业入驻准入审核",
               "• 平台根证书管理与密钥轮换",
               "• 调度双向竞价撮合清算引擎",
               "• 全站操作审计日志查阅 (@AuditLog)"],
              border_color='#EC4899', bg_color='#500724', title_color='#F472B6')

    # 流转关系连线
    draw_arrow(ax, 4.2, 6.5, 6.4, 6.5, '#38BDF8', '1. 提交签名报告')
    draw_arrow(ax, 6.4, 5.8, 4.2, 5.8, '#EF4444', '2. 驳回整改通知')
    draw_arrow(ax, 9.6, 6.2, 11.8, 6.2, '#10B981', '3. 审核通过推入核查池')
    draw_arrow(ax, 13.4, 5.0, 8.5, 3.6, '#F59E0B', '4. 存证与监管反馈')
    draw_arrow(ax, 6.4, 2.4, 2.6, 5.0, '#EC4899', '5. 准入资格签发')
    draw_arrow(ax, 8.0, 3.6, 8.0, 5.0, '#EC4899', '6. 审核员资质赋权')

    fig.savefig(os.path.join(OUTPUT_DIR, '02-four-roles-collaboration.png'), bbox_inches='tight')
    plt.close(fig)
    print("生成完成: 02-four-roles-collaboration.png")

# ==========================================
# 图 3: 碳核算全生命周期状态机与数据流图
# ==========================================
def render_fig_03():
    fig, ax = create_canvas("OAISS CHAIN · 碳核算全生命周期状态机与数据流转关系图",
                            "Carbon Accounting Lifecycle State Machine & Audit Flow")

    draw_node(ax, 0.8, 4.5, 2.2, 1.4, "0: DRAFT",
              ["• 状态: 草稿", "• 允许增删改台账", "• 计算国标公式结果"],
              border_color='#64748B', bg_color='#1E293B')

    draw_node(ax, 3.8, 4.5, 2.4, 1.4, "1: SUBMITTED",
              ["• 状态: 已提交待审", "• 锁定台账数据", "• 携带企业RSA签名"],
              border_color='#38BDF8', bg_color='#0C4A6E')

    draw_node(ax, 7.0, 4.5, 2.4, 1.4, "2: UNDER_REVIEW",
              ["• 状态: 审核中", "• 审核员领单锁定", "• 附件凭据比对"],
              border_color='#60A5FA', bg_color='#1E293B')

    draw_node(ax, 7.0, 1.8, 2.4, 1.4, "4: REJECTED",
              ["• 状态: 已驳回", "• 录入结构化理由", "• 发送站内信提醒"],
              border_color='#EF4444', bg_color='#450A0A', title_color='#F87171')

    draw_node(ax, 10.2, 4.5, 2.4, 1.4, "3: APPROVED",
              ["• 状态: 审核通过", "• 审核员联合公章", "• 进入存证外箱队列"],
              border_color='#10B981', bg_color='#064E3B')

    draw_node(ax, 13.4, 4.5, 2.0, 1.4, "5: ON_CHAIN",
              ["• 状态: 链上存证终态", "• 生成 Fabric TxHash", "• 生成存证二维码"],
              border_color='#F59E0B', bg_color='#451A03', title_color='#FCD34D')

    # 流转箭头
    draw_arrow(ax, 3.0, 5.2, 3.8, 5.2, '#38BDF8', '企业RSA签名提交')
    draw_arrow(ax, 6.2, 5.2, 7.0, 5.2, '#60A5FA', '审核员领单认领')
    draw_arrow(ax, 8.2, 4.5, 8.2, 3.2, '#EF4444', '审核不符驳回')
    draw_arrow(ax, 7.0, 2.5, 1.9, 4.5, '#EF4444', '退回草稿重新修改')
    draw_arrow(ax, 9.4, 5.2, 10.2, 5.2, '#10B981', '核验无误通过')
    draw_arrow(ax, 12.6, 5.2, 13.4, 5.2, '#F59E0B', 'Fabric 链码上链')

    fig.savefig(os.path.join(OUTPUT_DIR, '03-carbon-lifecycle-state-machine.png'), bbox_inches='tight')
    plt.close(fig)
    print("生成完成: 03-carbon-lifecycle-state-machine.png")

# ==========================================
# 图 4: 数据库 24 张核心实体 ER 关系图
# ==========================================
def render_fig_04():
    fig, ax = create_canvas("OAISS CHAIN · 数据库 24 张核心业务实体 ER 逻辑关系图",
                            "Relational Entity-Relationship Diagram (Flyway V1~V13 Schema)")

    # 模块 1: 用户权限
    draw_node(ax, 0.8, 5.0, 3.2, 2.6, "【身份权限域】",
              ["• user (用户主表 - PK: id)",
               "• user_type_list (用户类型表)",
               "• entry_permission (菜单权限表)",
               "• account_permission_list (账户权限)",
               "• operation_log (操作审计日志)",
               "• notification (消息通知表)"],
              border_color='#38BDF8', bg_color='#1E293B')

    # 模块 2: 企业资质
    draw_node(ax, 4.5, 5.0, 3.4, 2.6, "【企业与资质域】",
              ["• enterprise (企业主体表 - PK: id)",
               "• reviewer (审核员主体表)",
               "• third_party_org (第三方监管表)",
               "• enterprise_admission (企业准入表)",
               "• reviewer_qualification (资质审查表)",
               "• certificate (企业证书存证表)"],
              border_color='#10B981', bg_color='#1E293B')

    # 模块 3: 碳核算与中和
    draw_node(ax, 8.4, 5.0, 3.4, 2.6, "【碳核算与资产域】",
              ["• carbon_report (碳核算报告表)",
               "• carbon_neutral_project (减排项目表)",
               "• emission_rating (碳排评级排名表)",
               "• rsa_key_pair (密码学密钥对表)"],
              border_color='#F59E0B', bg_color='#1E293B')

    # 模块 4: 撮合交易与资金
    draw_node(ax, 12.2, 5.0, 3.2, 2.6, "【市场交易与撮合域】",
              ["• auction_order (双向拍卖挂单表)",
               "• matching_result (撮合结果中间表)",
               "• transaction (交易成交结算表)"],
              border_color='#EC4899', bg_color='#1E293B')

    # 模块 5: 碳币经济与信誉
    draw_node(ax, 2.6, 1.4, 4.8, 2.4, "【碳币账户与信誉评价域】",
              ["• carbon_coin_account (碳币资金总账账户表)",
               "• carbon_coin_transaction (碳币收支流水明细表)",
               "• credit_score (企业信誉评分综合总表)",
               "• credit_event (信用奖惩行为事件轨迹表)"],
              border_color='#A855F7', bg_color='#1E293B')

    # 模块 6: 消息外箱与可靠事件
    draw_node(ax, 8.8, 1.4, 4.8, 2.4, "【可靠事件与消息外箱域】",
              ["• outbox_message (事务消息外箱持久化表)",
               "• authenticator (已安全退场的历史表)",
               "• flyway_schema_history (V1~V13 迁移基线表)"],
              border_color='#64748B', bg_color='#1E293B')

    # 关联连线
    draw_arrow(ax, 4.0, 6.3, 4.5, 6.3, '#38BDF8', '1:1 组织绑定')
    draw_arrow(ax, 7.9, 6.3, 8.4, 6.3, '#10B981', '1:N 报告申报')
    draw_arrow(ax, 11.8, 6.3, 12.2, 6.3, '#F59E0B', '配额交易挂单')
    draw_arrow(ax, 6.2, 5.0, 5.0, 3.8, '#A855F7', '资金与信誉绑定')
    draw_arrow(ax, 10.1, 5.0, 11.2, 3.8, '#EC4899', '异步链上存证事件')

    fig.savefig(os.path.join(OUTPUT_DIR, '04-database-entity-er-diagram.png'), bbox_inches='tight')
    plt.close(fig)
    print("生成完成: 04-database-entity-er-diagram.png")

# ==========================================
# 图 5: 连续双向拍卖撮合引擎流转图
# ==========================================
def render_fig_05():
    fig, ax = create_canvas("OAISS CHAIN · 连续双向拍卖撮合引擎与 DVP 结算流转关系图",
                            "Continuous Double Auction (CDA) Matching & Delivery-vs-Payment")

    draw_node(ax, 0.8, 5.2, 3.2, 1.8, "买方企业申报 (Bid)",
              ["• 提交买单限价与采购量", "• 冻结 carbon_coin 资金", "• 校验 RSA 交易签名"],
              border_color='#38BDF8', bg_color='#0C4A6E')

    draw_node(ax, 0.8, 2.0, 3.2, 1.8, "卖方企业申报 (Ask)",
              ["• 提交卖单限价与出售量", "• 锁定 carbon_tradable 配额", "• 校验碳配额有效余额"],
              border_color='#F59E0B', bg_color='#451A03')

    draw_node(ax, 5.0, 5.2, 3.4, 1.8, "买单优先队列 (Bid Queue)",
              ["• 排序规则: Price DESC", "• 次级规则: Time ASC", "• 优先保障高买价最优撮合"],
              border_color='#38BDF8', bg_color='#1E293B')

    draw_node(ax, 5.0, 2.0, 3.4, 1.8, "卖单优先队列 (Ask Queue)",
              ["• 排序规则: Price ASC", "• 次级规则: Time ASC", "• 优先保障低卖价最优撮合"],
              border_color='#F59E0B', bg_color='#1E293B')

    draw_node(ax, 9.4, 3.6, 3.0, 2.0, "撮合核心引擎 (Engine)",
              ["• 判定条件: Max(Bid) >= Min(Ask)",
               "• 成交价计算: P_settle = (Bid+Ask)/2",
               "• 成交量计算: Q_match = Min(Q_b, Q_s)",
               "• 生成 MatchingResult 单据"],
              border_color='#10B981', bg_color='#064E3B', title_color='#34D399')

    draw_node(ax, 13.0, 3.6, 2.4, 2.0, "原子性 DVP 结算",
              ["• 配额自动过户划转",
               "• 资金多退少补扣减",
               "• 溢价返还买方可用",
               "• 触发 Fabric 链上存证"],
              border_color='#EC4899', bg_color='#500724', title_color='#F472B6')

    # 连线
    draw_arrow(ax, 4.0, 6.1, 5.0, 6.1, '#38BDF8', '验资入队')
    draw_arrow(ax, 4.0, 2.9, 5.0, 2.9, '#F59E0B', '锁券入队')
    draw_arrow(ax, 8.4, 6.1, 9.4, 4.8, '#38BDF8', '队首买单出列')
    draw_arrow(ax, 8.4, 2.9, 9.4, 4.4, '#F59E0B', '队首卖单出列')
    draw_arrow(ax, 12.4, 4.6, 13.0, 4.6, '#10B981', '原子交割结算')

    fig.savefig(os.path.join(OUTPUT_DIR, '05-double-auction-matching-engine.png'), bbox_inches='tight')
    plt.close(fig)
    print("生成完成: 05-double-auction-matching-engine.png")

# ==========================================
# 图 6: 密码学 RSA 数字签名与 AES-GCM 信封加密图
# ==========================================
def render_fig_06():
    fig, ax = create_canvas("OAISS CHAIN · 密码学 RSA 数字签名与 AES-GCM 信封加密防伪流转图",
                            "Cryptography Envelope Encryption & Non-repudiation Signature Flow")

    draw_node(ax, 0.8, 3.8, 3.2, 2.6, "1. 密钥生成与主密钥保护",
              ["• 环境变量部署主密钥 RSA_KEK",
               "• 生成企业 RSA-2048 密钥对",
               "• 本地私钥经 AES-256-GCM 加密落盘",
               "• 数据库严禁存储私钥明文"],
              border_color='#38BDF8', bg_color='#1E293B')

    draw_node(ax, 4.6, 5.2, 3.4, 2.2, "2. 碳核算报告签名 (防篡改)",
              ["• JSON 报告序列化规范字符串",
               "• 计算报告摘要 Hash = SHA-256(Data)",
               "• 企业私钥解密后进行 RSA 签名",
               "• 产出数字签名密文 Signature"],
              border_color='#10B981', bg_color='#064E3B')

    draw_node(ax, 4.6, 1.6, 3.4, 2.2, "3. 附件信封加密 (保密性)",
              ["• 生成一次性会话对称密钥 K_session",
               "• 敏感工艺凭据通过 AES-GCM 加密",
               "• 使用审核员公钥封装 K_session",
               "• 密文与密文密钥一同存储 MinIO"],
              border_color='#F59E0B', bg_color='#451A03')

    draw_node(ax, 8.8, 3.8, 3.2, 2.6, "4. 网关与审核员验签解密",
              ["• 调取企业已公开登记的公钥",
               "• 执行 RSA_Verify(PK, Hash, Sig)",
               "• 验签失败直接 403 阻断提交",
               "• 审核员私钥解封会话密钥查阅原件"],
              border_color='#A855F7', bg_color='#1E293B')

    draw_node(ax, 12.6, 3.8, 2.6, 2.6, "5. 联盟链存证固化",
              ["• 将数据哈希与签名入账",
               "• 写入 Fabric 智能合约账本",
               "• 广播全网背书节点共识",
               "• 生成防伪可查 TxHash"],
              border_color='#EC4899', bg_color='#500724')

    # 连线
    draw_arrow(ax, 4.0, 5.5, 4.6, 6.3, '#38BDF8', '导出公私钥')
    draw_arrow(ax, 4.0, 4.2, 4.6, 2.7, '#38BDF8', '保护会话凭据')
    draw_arrow(ax, 8.0, 6.3, 8.8, 5.5, '#10B981', '提交验签载荷')
    draw_arrow(ax, 8.0, 2.7, 8.8, 4.5, '#F59E0B', '提交信封载荷')
    draw_arrow(ax, 12.0, 5.1, 12.6, 5.1, '#EC4899', '合规上链存证')

    fig.savefig(os.path.join(OUTPUT_DIR, '06-cryptography-envelope-encryption.png'), bbox_inches='tight')
    plt.close(fig)
    print("生成完成: 06-cryptography-envelope-encryption.png")

# ==========================================
# 图 7: 五级持续交付发布阻断门禁流水线流转图
# ==========================================
def render_fig_07():
    fig, ax = create_canvas("OAISS CHAIN · 企业级持续交付与五级阻断门禁流水线流转图",
                            "Five-Stage Continuous Delivery Release Gates & Quality Verification")

    draw_node(ax, 0.6, 3.6, 2.0, 1.8, "代码提交触发",
              ["• PR 变更合并", "• 触发 CI 流程", "• 执行自动化门禁"],
              border_color='#64748B', bg_color='#1E293B')

    draw_node(ax, 3.0, 3.6, 2.4, 1.8, "Gate 1: 测试覆盖率",
              ["• mvn clean test", "• JaCoCo 行覆盖 >=95%", "• Vitest / pytest 全通"],
              border_color='#38BDF8', bg_color='#0C4A6E')

    draw_node(ax, 5.8, 3.6, 2.4, 1.8, "Gate 2: 零漂移断言",
              ["• doc-verify.mjs", "• 22 控制器/143 端点", "• 24 张核心业务表对齐"],
              border_color='#60A5FA', bg_color='#1E293B')

    draw_node(ax, 8.6, 3.6, 2.4, 1.8, "Gate 3: 国际化对称",
              ["• i18n-verify.mjs", "• 1430 双语镜像匹配", "• 0 遗漏 0 缺失键位"],
              border_color='#10B981', bg_color='#064E3B')

    draw_node(ax, 11.4, 3.6, 2.4, 1.8, "Gate 4: 交付物闭环",
              ["• closure-audit.mjs", "• 9 份 DOCX 规范资产", "• 证据归档真实检验"],
              border_color='#F59E0B', bg_color='#451A03')

    draw_node(ax, 14.2, 3.6, 1.5, 1.8, "Gate 5: 容器安全",
              ["• 0 高危 CVE", "• 非 root 运行", "• 准许发布上线"],
              border_color='#EC4899', bg_color='#500724')

    # 流转箭头
    draw_arrow(ax, 2.6, 4.5, 3.0, 4.5, '#64748B', '准入')
    draw_arrow(ax, 5.4, 4.5, 5.8, 4.5, '#38BDF8', 'PASS')
    draw_arrow(ax, 8.2, 4.5, 8.6, 4.5, '#60A5FA', 'PASS')
    draw_arrow(ax, 11.0, 4.5, 11.4, 4.5, '#10B981', 'PASS')
    draw_arrow(ax, 13.8, 4.5, 14.2, 4.5, '#F59E0B', 'PASS')

    fig.savefig(os.path.join(OUTPUT_DIR, '07-release-gates-pipeline.png'), bbox_inches='tight')
    plt.close(fig)
    print("生成完成: 07-release-gates-pipeline.png")

def main():
    render_fig_01()
    render_fig_02()
    render_fig_03()
    render_fig_04()
    render_fig_05()
    render_fig_06()
    render_fig_07()
    print("全部 7 张企业级高清架构大图生成完毕！")

if __name__ == '__main__':
    main()
