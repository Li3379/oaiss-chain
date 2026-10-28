#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Enterprise Architect 12 (EA12) 数据建模工程规范:
数据库 24 张核心业务实体 ER 逻辑关系模型图 (Flyway V1~V13 Schema)

规范要点:
- 纯白底色与标准 EA12 图框
- 规范三段式数据表实体 («table» 构造型、PK 分栏、FK/属性分栏)
- 六大业务域边界清晰划分 (Subject Areas)
- 信息工程法 (Information Engineering / IE Crow's Foot) 乌鸦脚基数连线
- 300 DPI 印刷级高清渲染输出
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

W, H = 16.5, 10.5  # 300 DPI -> 4950 x 3150

def render_ea12_database_er_diagram(filename='04-database-entity-er-diagram.png'):
    fig, ax = plt.subplots(figsize=(W, H), dpi=300)
    fig.patch.set_facecolor('#FFFFFF')
    ax.set_facecolor('#FFFFFF')
    ax.set_xlim(0, W)
    ax.set_ylim(0, H)
    ax.axis('off')

    # 1. 外部图框 (EA12 Diagram Frame)
    FRAME_X, FRAME_Y, FRAME_W, FRAME_H = 0.45, 0.4, W - 0.9, H - 0.8
    ax.add_patch(patches.Rectangle((FRAME_X, FRAME_Y), FRAME_W, FRAME_H,
                                   edgecolor='#94A3B8', facecolor='#FFFFFF', linewidth=1.2, zorder=1))

    # EA12 标题卡签 (左上角)
    TAB_W, TAB_H = 6.2, 0.46
    ax.add_patch(patches.Rectangle((FRAME_X, FRAME_Y + FRAME_H - TAB_H), TAB_W, TAB_H,
                                   edgecolor='#94A3B8', facecolor='#F1F5F9', linewidth=1.0, zorder=2))
    ax.text(FRAME_X + 0.15, FRAME_Y + FRAME_H - TAB_H/2,
            "package [DataModel] OAISS-CHAIN [数据库 24 张核心实体 ER 逻辑关系模型图]",
            fontsize=8.5, weight='bold', color='#1E293B', va='center', zorder=3)

    ax.text(FRAME_X + FRAME_W - 0.2, FRAME_Y + FRAME_H - TAB_H/2,
            "EA12 Data Modeling Profile · IE Crow's Foot Notation · 300 DPI",
            fontsize=7.8, color='#64748B', ha='right', va='center', zorder=3)

    # -------------------------------------------------------------
    # 绘图工具函数
    # -------------------------------------------------------------
    def draw_domain_box(x, y, w, h, title, bg='#FAFAFA', border='#94A3B8'):
        """业务数据域背景边框 (Subject Area)"""
        ax.add_patch(patches.Rectangle((x, y), w, h,
                                       edgecolor=border, facecolor=bg, linewidth=1.0, zorder=2))
        tab_w = max(2.2, len(title) * 0.115 + 0.6)
        tab_h = 0.30
        ax.add_patch(patches.Rectangle((x, y + h - tab_h), tab_w, tab_h,
                                       edgecolor=border, facecolor='#F1F5F9', linewidth=0.8, zorder=3))
        ax.text(x + 0.12, y + h - tab_h/2, title,
                fontsize=7.5, weight='bold', color='#334155', va='center', zorder=4)

    def draw_table_entity(x, y, w, h, tbl_name, comment, pks, fks, cols, border='#475569'):
        """EA12 标准三段式数据库表实体图元"""
        # 主矩形 (纯白底色)
        ax.add_patch(patches.Rectangle((x, y), w, h,
                                       edgecolor=border, facecolor='#FFFFFF', linewidth=1.05, zorder=4))
        
        # 1. 顶格 Header
        header_h = 0.38
        ax.add_patch(patches.Rectangle((x, y + h - header_h), w, header_h,
                                       edgecolor=border, facecolor='#F8FAFC', linewidth=0.9, zorder=5))
        ax.text(x + w/2, y + h - 0.12, "«table»",
                fontsize=6.2, color='#64748B', ha='center', va='center', zorder=6)
        ax.text(x + w/2, y + h - 0.26, f"{tbl_name} ({comment})",
                fontsize=7.6, weight='bold', color='#0F172A', ha='center', va='center', zorder=6)

        curr_y = y + h - header_h
        
        # 2. PK 分栏
        pk_h = len(pks) * 0.19 + 0.05
        ax.plot([x, x + w], [curr_y, curr_y], color='#CBD5E1', linewidth=0.7, zorder=6)
        for i, pk in enumerate(pks):
            ax.text(x + 0.10, curr_y - 0.11 - i * 0.19, f"+ {pk}",
                    fontsize=6.8, weight='bold', color='#B45309', va='center', zorder=6)
        curr_y -= pk_h

        # 3. FK 与普通字段分栏
        ax.plot([x, x + w], [curr_y, curr_y], color='#CBD5E1', linewidth=0.7, zorder=6)
        for i, fk in enumerate(fks):
            ax.text(x + 0.10, curr_y - 0.10 - i * 0.18, f"+ {fk}",
                    fontsize=6.6, color='#0369A1', va='center', zorder=6)
        curr_y -= len(fks) * 0.18

        for i, c in enumerate(cols):
            ax.text(x + 0.10, curr_y - 0.10 - i * 0.18, f"+ {c}",
                    fontsize=6.5, color='#475569', va='center', zorder=6)

    def draw_crows_foot_rel(p1, p2, label="", card1="1", card2="N", orthogonal=False, mid_coord=None):
        """标准 IE 乌鸦脚连线 (1:1 或 1:N)"""
        color = '#334155'
        if orthogonal and mid_coord:
            # 折线: p1 -> (mid_coord, p1_y) -> (mid_coord, p2_y) -> p2
            pts_x, pts_y = [p1[0], mid_coord[0], mid_coord[0], p2[0]], [p1[1], p1[1], p2[1], p2[1]]
            ax.plot(pts_x, pts_y, color=color, linewidth=1.1, zorder=3)
            lx, ly = mid_coord[0], (p1[1] + p2[1]) / 2
        else:
            ax.plot([p1[0], p2[0]], [p1[1], p2[1]], color=color, linewidth=1.1, zorder=3)
            lx, ly = (p1[0] + p2[0]) / 2, (p1[1] + p2[1]) / 2

        # 端点基数标记
        ax.text(p1[0] + 0.12, p1[1] + 0.08, card1, fontsize=6.6, weight='bold', color='#1E293B', zorder=6)
        ax.text(p2[0] - 0.14, p2[1] + 0.08, card2, fontsize=6.6, weight='bold', color='#1E293B', zorder=6)
        
        # 关系说明
        if label:
            ax.text(lx, ly + 0.06, label, fontsize=6.2, color='#1E293B', ha='center', va='bottom',
                    bbox=dict(boxstyle='square,pad=0.12', facecolor='#FFFFFF', edgecolor='#CBD5E1', linewidth=0.6),
                    zorder=6)

    # =============================================================
    # 布局组织 (六大业务域网格化排布)
    # =============================================================

    # -------------------------------------------------------------
    # 域 1: 身份权限与操作审计域 (Top-Left: X: 0.7 ~ 5.4, Y: 5.3 ~ 9.4)
    # -------------------------------------------------------------
    draw_domain_box(0.7, 5.3, 4.8, 4.15, "【身份权限与审计域】", bg='#FAFAFA')
    # user
    draw_table_entity(0.9, 7.3, 2.1, 1.85, "user", "用户主表",
                      pks=["id: bigint [PK]"],
                      fks=[],
                      cols=["username: varchar(64)", "password: varchar(128)", "real_name: varchar(64)", "status: int", "email: varchar(128)", "phone: varchar(32)"])
    # user_type_list
    draw_table_entity(3.2, 7.8, 2.1, 1.35, "user_type_list", "角色关联",
                      pks=["id: bigint [PK]"],
                      fks=["user_id: bigint [FK]"],
                      cols=["user_type_id: int", "assigned_at: datetime"])
    # entry_permission
    draw_table_entity(3.2, 5.5, 2.1, 1.45, "entry_permission", "权限项字典",
                      pks=["id: bigint [PK]"],
                      fks=[],
                      cols=["permission_name: varchar(64)", "permission_code: varchar(64)", "path: varchar(128)"])
    # account_permission_list
    draw_table_entity(0.9, 5.5, 2.1, 1.45, "account_permission_list", "用户授权",
                      pks=["id: bigint [PK]"],
                      fks=["user_id: bigint [FK]", "permission_id: bigint [FK]"],
                      cols=["granted_at: datetime"])

    # -------------------------------------------------------------
    # 域 2: 企业资质与准入证书域 (Top-Middle: X: 5.8 ~ 10.8, Y: 5.3 ~ 9.4)
    # -------------------------------------------------------------
    draw_domain_box(5.8, 5.3, 4.8, 4.15, "【企业主体与资质证书域】", bg='#FAFAFA')
    # enterprise
    draw_table_entity(6.0, 7.3, 2.2, 1.85, "enterprise", "企业主体",
                      pks=["id: bigint [PK]"],
                      fks=["user_id: bigint [FK]"],
                      cols=["enterprise_name: varchar(128)", "credit_code: varchar(32)", "industry_type: varchar(32)", "legal_person: varchar(64)"])
    # certificate
    draw_table_entity(8.3, 7.8, 2.1, 1.35, "certificate", "数字证书",
                      pks=["id: bigint [PK]"],
                      fks=["enterprise_id: bigint [FK]"],
                      cols=["cert_sn: varchar(64)", "public_key_fp: varchar(128)", "valid_until: datetime"])
    # enterprise_admission
    draw_table_entity(8.3, 5.5, 2.1, 1.45, "enterprise_admission", "准入审查",
                      pks=["id: bigint [PK]"],
                      fks=["enterprise_id: bigint [FK]"],
                      cols=["audit_status: int", "reason: text", "audited_at: datetime"])
    # reviewer & third_party_org (合并展示)
    draw_table_entity(6.0, 5.5, 2.2, 1.45, "reviewer / 3rd_org", "核查与监管",
                      pks=["id: bigint [PK]"],
                      fks=["user_id: bigint [FK]"],
                      cols=["org_name: varchar(128)", "qualification_lvl: varchar(32)", "status: int"])

    # -------------------------------------------------------------
    # 域 3: 碳资产核算与数字签名域 (Top-Right: X: 11.2 ~ 15.9, Y: 5.3 ~ 9.4)
    # -------------------------------------------------------------
    draw_domain_box(11.2, 5.3, 4.8, 4.15, "【碳资产核算与数字签名域】", bg='#FAFAFA')
    # carbon_report
    draw_table_entity(11.4, 7.1, 2.3, 2.05, "carbon_report", "核算报告主表",
                      pks=["id: bigint [PK]"],
                      fks=["enterprise_id: bigint [FK]"],
                      cols=["report_title: varchar(128)", "period: varchar(32)", "total_emissions: decimal", "status: varchar(32)", "rsa_signature: text", "tx_hash: varchar(64)"])
    # rsa_key_pair
    draw_table_entity(13.8, 7.8, 2.0, 1.35, "rsa_key_pair", "密钥托管",
                      pks=["id: bigint [PK]"],
                      fks=["enterprise_id: bigint [FK]"],
                      cols=["public_key: text", "enc_private_key: text", "key_status: int"])
    # carbon_neutral_project
    draw_table_entity(11.4, 5.5, 2.3, 1.45, "carbon_neutral_project", "减排项目",
                      pks=["id: bigint [PK]"],
                      fks=["enterprise_id: bigint [FK]"],
                      cols=["project_name: varchar(128)", "reduction_amt: decimal", "status: varchar(32)"])
    # emission_rating
    draw_table_entity(13.8, 5.5, 2.0, 1.45, "emission_rating", "排放评级",
                      pks=["id: bigint [PK]"],
                      fks=["enterprise_id: bigint [FK]"],
                      cols=["carbon_intensity: decimal", "rating_grade: varchar(16)", "year: int"])

    # -------------------------------------------------------------
    # 域 4: 市场交易与清算交割域 (Bottom-Left: X: 0.7 ~ 5.4, Y: 0.7 ~ 4.9)
    # -------------------------------------------------------------
    draw_domain_box(0.7, 0.7, 4.8, 4.25, "【配额市场拍卖与撮合结算域】", bg='#FAFAFA')
    # auction_order
    draw_table_entity(0.9, 2.9, 2.2, 1.8, "auction_order", "拍卖挂单表",
                      pks=["id: bigint [PK]"],
                      fks=["enterprise_id: bigint [FK]"],
                      cols=["order_type: varchar(16)", "quota_amount: decimal", "price: decimal(16,4)", "status: varchar(32)", "frozen_amount: decimal"])
    # matching_result
    draw_table_entity(3.2, 3.1, 2.1, 1.6, "matching_result", "撮合出清表",
                      pks=["id: bigint [PK]"],
                      fks=["buy_order_id: bigint [FK]", "sell_order_id: bigint [FK]"],
                      cols=["match_price: decimal(16,4)", "match_amount: decimal", "batch_id: varchar(64)"])
    # transaction
    draw_table_entity(0.9, 0.9, 4.4, 1.7, "transaction", "原子成交结算表 (DVP)",
                      pks=["id: bigint [PK]"],
                      fks=["buy_ent_id: bigint [FK]", "sell_ent_id: bigint [FK]", "matching_id: bigint [FK]"],
                      cols=["total_price: decimal(16,4)", "tx_hash: varchar(64)", "settlement_status: varchar(32)", "cleared_at: datetime"])

    # -------------------------------------------------------------
    # 域 5: 碳币经济与信誉评价域 (Bottom-Middle: X: 5.8 ~ 10.8, Y: 0.7 ~ 4.9)
    # -------------------------------------------------------------
    draw_domain_box(5.8, 0.7, 4.8, 4.25, "【碳币账户与信誉评价域】", bg='#FAFAFA')
    # carbon_coin_account
    draw_table_entity(6.0, 2.9, 2.2, 1.8, "carbon_coin_account", "碳币资金账户",
                      pks=["id: bigint [PK]"],
                      fks=["enterprise_id: bigint [FK]"],
                      cols=["balance: decimal(16,4)", "frozen_balance: decimal", "total_income: decimal", "total_expense: decimal"])
    # carbon_coin_transaction
    draw_table_entity(8.3, 3.1, 2.1, 1.6, "carbon_coin_transaction", "碳币流水",
                      pks=["id: bigint [PK]"],
                      fks=["account_id: bigint [FK]"],
                      cols=["tx_type: varchar(32)", "amount: decimal(16,4)", "balance_after: decimal", "tx_ref: varchar(64)"])
    # credit_score
    draw_table_entity(6.0, 0.9, 2.2, 1.7, "credit_score", "信誉综合总表",
                      pks=["id: bigint [PK]"],
                      fks=["enterprise_id: bigint [FK]"],
                      cols=["current_score: int", "credit_level: varchar(16)", "rating_date: datetime"])
    # credit_event
    draw_table_entity(8.3, 0.9, 2.1, 1.7, "credit_event", "奖惩行为轨迹",
                      pks=["id: bigint [PK]"],
                      fks=["enterprise_id: bigint [FK]"],
                      cols=["event_type: varchar(32)", "delta_points: int", "reason: varchar(255)"])

    # -------------------------------------------------------------
    # 域 6: 可靠事件外箱与审计运维域 (Bottom-Right: X: 11.2 ~ 15.9, Y: 0.7 ~ 4.9)
    # -------------------------------------------------------------
    draw_domain_box(11.2, 0.7, 4.8, 4.25, "【可靠事件外箱与审计运维域】", bg='#FAFAFA')
    # outbox_message
    draw_table_entity(11.4, 2.9, 2.3, 1.8, "outbox_message", "事务消息外发箱",
                      pks=["id: bigint [PK]"],
                      fks=[],
                      cols=["aggregate_type: varchar(64)", "aggregate_id: varchar(64)", "payload: text", "status: varchar(32)", "retry_count: int"])
    # operation_log & notification
    draw_table_entity(13.8, 2.9, 2.0, 1.8, "operation_log", "敏感操作日志",
                      pks=["id: bigint [PK]"],
                      fks=["user_id: bigint [FK]"],
                      cols=["action: varchar(64)", "details: text", "ip_address: varchar(45)", "created_at: datetime"])
    # notification & baseline
    draw_table_entity(11.4, 0.9, 2.3, 1.7, "notification", "站内通知消息",
                      pks=["id: bigint [PK]"],
                      fks=["user_id: bigint [FK]"],
                      cols=["title: varchar(128)", "content: text", "is_read: boolean", "created_at: datetime"])
    draw_table_entity(13.8, 0.9, 2.0, 1.7, "flyway_schema", "数据库迁移基线",
                      pks=["rank: int [PK]"],
                      fks=[],
                      cols=["version: varchar(50)", "description: varchar(200)", "success: boolean"])

    # =============================================================
    # 跨域外键基数连线 (Crow's Foot Relationships)
    # =============================================================
    # 1. user (1) -> user_type_list (N)
    draw_crows_foot_rel((3.0, 8.4), (3.2, 8.4), "", card1="1", card2="N")
    # 2. user (1) -> enterprise (1:1)
    draw_crows_foot_rel((3.0, 7.8), (6.0, 7.8), "1:1 组织主体绑定", card1="1", card2="1")
    # 3. enterprise (1) -> certificate (N)
    draw_crows_foot_rel((8.2, 8.4), (8.3, 8.4), "", card1="1", card2="N")
    # 4. enterprise (1) -> carbon_report (N)
    draw_crows_foot_rel((8.2, 7.6), (11.4, 7.6), "1:N 报告申报", card1="1", card2="N")
    # 5. enterprise (1) -> auction_order (N)
    draw_crows_foot_rel((6.5, 7.3), (2.0, 4.7), "配额交易挂单 (1:N)", card1="1", card2="N")
    # 6. enterprise (1) -> carbon_coin_account (1:1)
    draw_crows_foot_rel((7.1, 7.3), (7.1, 4.7), "资金账户 (1:1)", card1="1", card2="1")
    # 7. auction_order (1) -> matching_result (N)
    draw_crows_foot_rel((3.1, 3.8), (3.2, 3.8), "", card1="1", card2="N")
    # 8. matching_result (1) -> transaction (1)
    draw_crows_foot_rel((4.2, 3.1), (3.5, 2.6), "DVP原子交割", card1="1", card2="1")
    # 9. carbon_coin_account (1) -> carbon_coin_transaction (N)
    draw_crows_foot_rel((8.2, 3.8), (8.3, 3.8), "", card1="1", card2="N")
    # 10. carbon_report (1) -> outbox_message (N)
    draw_crows_foot_rel((12.5, 7.1), (12.5, 4.7), "可靠上链事件发布", card1="1", card2="N")

    out_p = os.path.join(OUTPUT_DIR, filename)
    plt.savefig(out_p, dpi=300, bbox_inches='tight', facecolor='#FFFFFF')
    plt.close(fig)
    print(f"EA12 数据库 ER 图生成完成: {out_p}")

if __name__ == '__main__':
    render_ea12_database_er_diagram()
