#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Enterprise Architect 12 (EA12) / UML 2.5 规范:
系统多角色核心用例全景图 (Multi-Role Core Use Case Panorama)

解决核心问题:
1. 实例比例过小 -> 提炼平台核心业务用例，采用标准大椭圆 (长宽比 4.6:1, 字体 8.2pt), 保证 100% 清晰阅读
2. 缺少关系连线 -> 补齐四方参与者实线关联、行内与跨模块 include / extend 虚线依赖、跨角色业务流转闭环
3. 纯正工业风 -> 纯白底色 (#FFFFFF)、细灰边框 (#94A3B8)、标准 Stick Man 图元与系统边界框
"""

import os
import math
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.patches as patches

OUTPUT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '../../docs/assets/architecture'))
os.makedirs(OUTPUT_DIR, exist_ok=True)

plt.rcParams['font.sans-serif'] = ['Microsoft YaHei', 'SimHei', 'DejaVu Sans']
plt.rcParams['axes.unicode_minus'] = False

# 画布尺寸 (16:10 工业出版级, 300 DPI -> 4800 x 3150)
W, H = 16.2, 10.5

UC_W = 2.22
UC_H = 0.48
FONT_UC = 8.0
FONT_TAG = 6.4

UC_FILL = '#F8FAFC'
UC_BORDER = '#64748B'
UC_TEXT = '#0F172A'
LINE_COLOR = '#334155'


def edge_point(from_pt, cx, cy, w=UC_W, h=UC_H):
    """计算从外部点指向椭圆中心时，与椭圆外轮廓的精确交点"""
    a, b = w / 2.0, h / 2.0
    dx, dy = cx - from_pt[0], cy - from_pt[1]
    dist = math.hypot(dx, dy)
    if dist < 1e-6:
        return (cx, cy)
    t = 1.0 / math.sqrt((dx / (a * dist)) ** 2 + (dy / (b * dist)) ** 2)
    return (from_pt[0] + dx * t, from_pt[1] + dy * t)


def draw_actor(ax, cx, cy, name, role_code="", scale=1.0):
    """绘制标准 UML Stick Man 参与者"""
    # 头部圆圈
    ax.add_patch(patches.Circle((cx, cy + 0.48 * scale), 0.14 * scale,
                                edgecolor='#1E293B', facecolor='#FFFFFF', linewidth=1.3, zorder=5))
    # 躯干垂直线
    ax.plot([cx, cx], [cy + 0.34 * scale, cy + 0.05 * scale], color='#1E293B', linewidth=1.3, zorder=5)
    # 手臂水平线
    ax.plot([cx - 0.23 * scale, cx + 0.23 * scale], [cy + 0.23 * scale, cy + 0.23 * scale],
            color='#1E293B', linewidth=1.3, zorder=5)
    # 腿部倒 V 形
    ax.plot([cx, cx - 0.18 * scale], [cy + 0.05 * scale, cy - 0.22 * scale], color='#1E293B', linewidth=1.3, zorder=5)
    ax.plot([cx, cx + 0.18 * scale], [cy + 0.05 * scale, cy - 0.22 * scale], color='#1E293B', linewidth=1.3, zorder=5)
    # 角色名称
    ax.text(cx, cy - 0.35 * scale, name, fontsize=8.8, weight='bold', color='#0F172A',
            ha='center', va='top', zorder=5)
    if role_code:
        ax.text(cx, cy - 0.54 * scale, f"({role_code})", fontsize=7.0, color='#64748B',
                ha='center', va='top', zorder=5)


def draw_uc(ax, cx, cy, text, w=UC_W, h=UC_H, bg=UC_FILL, border=UC_BORDER):
    """绘制企业级用例椭圆"""
    ax.add_patch(patches.Ellipse((cx, cy), w, h,
                                 edgecolor=border, facecolor=bg, linewidth=1.05, zorder=4))
    ax.text(cx, cy, text, fontsize=FONT_UC, color=UC_TEXT,
            ha='center', va='center', zorder=5)


def draw_assoc(ax, p_from, p_to):
    """UML 关联关系 (无箭头实线)"""
    ax.plot([p_from[0], p_to[0]], [p_from[1], p_to[1]], color=LINE_COLOR, linewidth=1.1, zorder=2)


def draw_dependency(ax, p_from, p_to, label="«include»", rad=0.0):
    """UML 包含/扩展依赖 (虚线 + 开放单向箭头)"""
    ax.annotate('', xy=p_to, xytext=p_from,
                arrowprops=dict(arrowstyle="->,head_width=0.13,head_length=0.19",
                                color='#475569', lw=1.0, linestyle='--',
                                shrinkA=0, shrinkB=0,
                                connectionstyle=f"arc3,rad={rad}"),
                zorder=3)
    mx = (p_from[0] + p_to[0]) / 2.0
    my = (p_from[1] + p_to[1]) / 2.0
    ax.text(mx, my + 0.065, label, fontsize=FONT_TAG, color='#334155',
            ha='center', va='bottom',
            bbox=dict(boxstyle='square,pad=0.10', facecolor='#FFFFFF', edgecolor='#CBD5E1', linewidth=0.5),
            zorder=6)


def render_ea12_panoramic_diagram(filename='02-four-roles-collaboration.png'):
    fig, ax = plt.subplots(figsize=(W, H), dpi=300)
    fig.patch.set_facecolor('#FFFFFF')
    ax.set_facecolor('#FFFFFF')
    ax.set_xlim(0, W)
    ax.set_ylim(0, H)
    ax.axis('off')

    # 1. EA12 图框 (Diagram Frame)
    FRAME_X, FRAME_Y, FRAME_W, FRAME_H = 0.40, 0.35, W - 0.80, H - 0.70
    ax.add_patch(patches.Rectangle((FRAME_X, FRAME_Y), FRAME_W, FRAME_H,
                                   edgecolor='#94A3B8', facecolor='#FFFFFF', linewidth=1.2, zorder=1))

    # 左上角页签
    TAB_W, TAB_H = 6.2, 0.46
    ax.add_patch(patches.Rectangle((FRAME_X, FRAME_Y + FRAME_H - TAB_H), TAB_W, TAB_H,
                                   edgecolor='#94A3B8', facecolor='#F1F5F9', linewidth=1.0, zorder=2))
    ax.text(FRAME_X + 0.15, FRAME_Y + FRAME_H - TAB_H/2,
            "usecase [Diagram] OAISS-CHAIN [系统多角色核心用例全景图]",
            fontsize=8.5, weight='bold', color='#1E293B', va='center', zorder=3)
    ax.text(FRAME_X + FRAME_W - 0.2, FRAME_Y + FRAME_H - TAB_H/2,
            "Enterprise Architect 12 / UML 2.5 Specification · 300 DPI",
            fontsize=7.8, color='#64748B', ha='right', va='center', zorder=3)

    # 2. 系统边界矩形 (System Boundary)
    BND_X, BND_Y, BND_W, BND_H = 2.40, 0.65, W - 4.80, H - 1.35
    ax.add_patch(patches.Rectangle((BND_X, BND_Y), BND_W, BND_H,
                                   edgecolor='#64748B', facecolor='#FFFFFF', linewidth=1.2, zorder=1))
    ax.text(BND_X + BND_W / 2.0, BND_Y + BND_H - 0.26,
            "OAISS-CHAIN 双碳链动系统 —— 核心业务系统边界",
            fontsize=11.2, weight='bold', color='#0F172A', ha='center', va='center', zorder=2)

    # 3. 四大功能域分组框 (Subsystem Boundaries inside Boundary)
    subsystems = [
        # (x, y, w, h, name)
        (BND_X + 0.25, BND_Y + 4.60, 5.25, 4.05, "碳资产核算与存证业务域"),
        (BND_X + 5.90, BND_Y + 4.60, 5.25, 4.05, "宏观态势与智能监管大盘域"),
        (BND_X + 0.25, BND_Y + 0.30, 5.25, 4.05, "碳配额交易与清算交割域"),
        (BND_X + 5.90, BND_Y + 0.30, 5.25, 4.05, "平台准入与安全审计治理域"),
    ]
    for sx, sy, sw, sh, sname in subsystems:
        ax.add_patch(patches.Rectangle((sx, sy), sw, sh,
                                       edgecolor='#CBD5E1', facecolor='#F8FAFC', linewidth=0.9, zorder=2))
        ax.add_patch(patches.Rectangle((sx, sy + sh - 0.30), min(sw, len(sname) * 0.12 + 0.5), 0.30,
                                       edgecolor='#CBD5E1', facecolor='#EFF6FF', linewidth=0.7, zorder=3))
        ax.text(sx + 0.12, sy + sh - 0.15, sname,
                fontsize=7.4, weight='bold', color='#1E40AF', va='center', zorder=4)

    # 4. 参与者 (4 大主体分布在左右两侧)
    # 左上: 控排企业
    ACT_ENT_PT = (1.10, 7.50)
    draw_actor(ax, ACT_ENT_PT[0], ACT_ENT_PT[1], "控排企业", "ENTERPRISE", scale=1.0)
    # 左下: 数据审核员
    ACT_REV_PT = (1.10, 2.70)
    draw_actor(ax, ACT_REV_PT[0], ACT_REV_PT[1], "数据审核员", "REVIEWER", scale=1.0)
    # 右上: 第三方监管机构
    ACT_REG_PT = (15.10, 7.50)
    draw_actor(ax, ACT_REG_PT[0], ACT_REG_PT[1], "第三方监管机构", "THIRD_PARTY", scale=1.0)
    # 右下: 系统管理员
    ACT_ADM_PT = (15.10, 2.70)
    draw_actor(ax, ACT_ADM_PT[0], ACT_ADM_PT[1], "系统管理员", "ADMIN", scale=1.0)

    # -------------------------------------------------------------
    # 5. 用例图元定义与排布 (共 20 个核心用例, 彻底解决比例过小问题)
    # -------------------------------------------------------------
    UC_NODES = {}

    # === 域 1: 碳资产核算与存证业务域 ===
    UC_NODES['uc_fill'] = (3.90, 8.40)      # 碳核算模板填报
    UC_NODES['uc_submit'] = (6.40, 8.40)    # 提交核算报告送审
    UC_NODES['uc_sign'] = (6.40, 7.20)      # 报告生成数字签名
    UC_NODES['uc_audit'] = (3.90, 6.10)     # 审核碳核算报告
    UC_NODES['uc_verify'] = (6.40, 6.10)    # 企业公钥RSA验签
    UC_NODES['uc_reject'] = (3.90, 5.05)    # 结构化驳回出具

    draw_uc(ax, UC_NODES['uc_fill'][0], UC_NODES['uc_fill'][1], "碳核算模板填报")
    draw_uc(ax, UC_NODES['uc_submit'][0], UC_NODES['uc_submit'][1], "提交核算报告送审")
    draw_uc(ax, UC_NODES['uc_sign'][0], UC_NODES['uc_sign'][1], "报告生成数字签名")
    draw_uc(ax, UC_NODES['uc_audit'][0], UC_NODES['uc_audit'][1], "审核碳核算报告")
    draw_uc(ax, UC_NODES['uc_verify'][0], UC_NODES['uc_verify'][1], "企业公钥RSA验签")
    draw_uc(ax, UC_NODES['uc_reject'][0], UC_NODES['uc_reject'][1], "结构化驳回出具")

    # === 域 2: 宏观态势与智能监管大盘域 ===
    UC_NODES['uc_board'] = (12.30, 8.40)   # 访问双碳监管大盘
    UC_NODES['uc_prophet'] = (9.50, 8.40)  # AI时序碳价预测
    UC_NODES['uc_anomaly'] = (9.50, 7.20)  # 异常排放偏离归因
    UC_NODES['uc_certify'] = (12.30, 6.10) # 存证穿透比对与公信核验
    UC_NODES['uc_fabric'] = (9.50, 6.10)   # Fabric链码上链存证

    draw_uc(ax, UC_NODES['uc_board'][0], UC_NODES['uc_board'][1], "访问双碳监管大盘")
    draw_uc(ax, UC_NODES['uc_prophet'][0], UC_NODES['uc_prophet'][1], "AI时序碳价预测")
    draw_uc(ax, UC_NODES['uc_anomaly'][0], UC_NODES['uc_anomaly'][1], "异常排放偏离归因")
    draw_uc(ax, UC_NODES['uc_certify'][0], UC_NODES['uc_certify'][1], "存证穿透比对与公信核验")
    draw_uc(ax, UC_NODES['uc_fabric'][0], UC_NODES['uc_fabric'][1], "Fabric链码上链存证")

    # === 域 3: 碳配额交易与清算交割域 ===
    UC_NODES['uc_auction'] = (3.90, 4.05)   # 双向拍卖限价挂单
    UC_NODES['uc_matching'] = (6.40, 4.05)  # 连续撮合出清运算
    UC_NODES['uc_dvp'] = (6.40, 2.95)       # DVP原子清算交割
    UC_NODES['uc_account'] = (3.90, 1.80)   # 碳币资金账户管理
    UC_NODES['uc_credit'] = (6.40, 1.80)    # 碳信用履约评分评价

    draw_uc(ax, UC_NODES['uc_auction'][0], UC_NODES['uc_auction'][1], "双向拍卖限价挂单")
    draw_uc(ax, UC_NODES['uc_matching'][0], UC_NODES['uc_matching'][1], "连续撮合出清运算")
    draw_uc(ax, UC_NODES['uc_dvp'][0], UC_NODES['uc_dvp'][1], "DVP原子清算交割")
    draw_uc(ax, UC_NODES['uc_account'][0], UC_NODES['uc_account'][1], "碳币资金账户管理")
    draw_uc(ax, UC_NODES['uc_credit'][0], UC_NODES['uc_credit'][1], "碳信用履约评分评价")

    # === 域 4: 平台准入与安全审计治理域 ===
    UC_NODES['uc_admission'] = (12.30, 4.05) # 企业准入资质审查
    UC_NODES['uc_cert'] = (9.50, 4.05)       # 签发X.509管理证书
    UC_NODES['uc_users'] = (12.30, 2.85)     # 全域账号与权限治理
    UC_NODES['uc_log'] = (9.50, 1.65)        # 敏感审计日志穿透
    UC_NODES['uc_ops'] = (12.30, 1.65)       # 容器健康态势巡检

    draw_uc(ax, UC_NODES['uc_admission'][0], UC_NODES['uc_admission'][1], "企业准入资质审查")
    draw_uc(ax, UC_NODES['uc_cert'][0], UC_NODES['uc_cert'][1], "签发X.509管理证书")
    draw_uc(ax, UC_NODES['uc_users'][0], UC_NODES['uc_users'][1], "全域账号与权限治理")
    draw_uc(ax, UC_NODES['uc_log'][0], UC_NODES['uc_log'][1], "敏感审计日志穿透")
    draw_uc(ax, UC_NODES['uc_ops'][0], UC_NODES['uc_ops'][1], "容器健康态势巡检")

    # -------------------------------------------------------------
    # 6. 参与者与用例的实线关联 (Associations: 无箭头实线)
    # -------------------------------------------------------------
    # 控排企业 (ENTERPRISE)
    ent_act_pos = (ACT_ENT_PT[0] + 0.35, ACT_ENT_PT[1])
    for target_key in ['uc_fill', 'uc_auction', 'uc_account']:
        tx, ty = UC_NODES[target_key]
        p_to = edge_point(ent_act_pos, tx, ty)
        draw_assoc(ax, ent_act_pos, p_to)

    # 数据审核员 (REVIEWER)
    rev_act_pos = (ACT_REV_PT[0] + 0.35, ACT_REV_PT[1])
    for target_key in ['uc_audit', 'uc_reject']:
        tx, ty = UC_NODES[target_key]
        p_to = edge_point(rev_act_pos, tx, ty)
        draw_assoc(ax, rev_act_pos, p_to)

    # 第三方监管机构 (THIRD_PARTY)
    reg_act_pos = (ACT_REG_PT[0] - 0.35, ACT_REG_PT[1])
    for target_key in ['uc_board', 'uc_certify']:
        tx, ty = UC_NODES[target_key]
        p_to = edge_point(reg_act_pos, tx, ty)
        draw_assoc(ax, reg_act_pos, p_to)

    # 系统管理员 (ADMIN)
    adm_act_pos = (ACT_ADM_PT[0] - 0.35, ACT_ADM_PT[1])
    for target_key in ['uc_admission', 'uc_users', 'uc_ops']:
        tx, ty = UC_NODES[target_key]
        p_to = edge_point(adm_act_pos, tx, ty)
        draw_assoc(ax, adm_act_pos, p_to)

    # -------------------------------------------------------------
    # 7. 用例之间的依赖关系 (Includes & Extends: 虚线开放单向箭头)
    # -------------------------------------------------------------
    # 域 1 内部: 填报 -> 送审 -> 签名
    p1 = (UC_NODES['uc_fill'][0] + UC_W/2, UC_NODES['uc_fill'][1])
    p2 = (UC_NODES['uc_submit'][0] - UC_W/2, UC_NODES['uc_submit'][1])
    draw_dependency(ax, p1, p2, "«include»")

    p1 = (UC_NODES['uc_submit'][0], UC_NODES['uc_submit'][1] - UC_H/2)
    p2 = (UC_NODES['uc_sign'][0], UC_NODES['uc_sign'][1] + UC_H/2)
    draw_dependency(ax, p1, p2, "«include»")

    # 审核报告 -> 验签
    p1 = (UC_NODES['uc_audit'][0] + UC_W/2, UC_NODES['uc_audit'][1])
    p2 = (UC_NODES['uc_verify'][0] - UC_W/2, UC_NODES['uc_verify'][1])
    draw_dependency(ax, p1, p2, "«include»")

    # 驳回 -> 审核报告 (extend 条件扩展)
    p1 = (UC_NODES['uc_reject'][0], UC_NODES['uc_reject'][1] + UC_H/2)
    p2 = (UC_NODES['uc_audit'][0], UC_NODES['uc_audit'][1] - UC_H/2)
    draw_dependency(ax, p1, p2, "«extend»")

    # 域 2 内部: 监管大盘 -> AI预测 / 偏离归因
    p1 = (UC_NODES['uc_board'][0] - UC_W/2, UC_NODES['uc_board'][1])
    p2 = (UC_NODES['uc_prophet'][0] + UC_W/2, UC_NODES['uc_prophet'][1])
    draw_dependency(ax, p1, p2, "«include»")

    p1 = (UC_NODES['uc_board'][0] - UC_W/2 + 0.3, UC_NODES['uc_board'][1] - UC_H/2)
    p2 = (UC_NODES['uc_anomaly'][0] + UC_W/2, UC_NODES['uc_anomaly'][1] + 0.1)
    draw_dependency(ax, p1, p2, "«include»")

    # 存证穿透比对与公信核验 -> Fabric 上链 (只读核验)
    p1 = (UC_NODES['uc_certify'][0] - UC_W/2, UC_NODES['uc_certify'][1])
    p2 = (UC_NODES['uc_fabric'][0] + UC_W/2, UC_NODES['uc_fabric'][1])
    draw_dependency(ax, p1, p2, "«只读核验»")

    # 域 3 内部: 拍卖挂单 -> 撮合出清 -> DVP清算
    p1 = (UC_NODES['uc_auction'][0] + UC_W/2, UC_NODES['uc_auction'][1])
    p2 = (UC_NODES['uc_matching'][0] - UC_W/2, UC_NODES['uc_matching'][1])
    draw_dependency(ax, p1, p2, "«include»")

    p1 = (UC_NODES['uc_matching'][0], UC_NODES['uc_matching'][1] - UC_H/2)
    p2 = (UC_NODES['uc_dvp'][0], UC_NODES['uc_dvp'][1] + UC_H/2)
    draw_dependency(ax, p1, p2, "«include»")

    # 账户管理 -> 信用评分
    p1 = (UC_NODES['uc_account'][0] + UC_W/2, UC_NODES['uc_account'][1])
    p2 = (UC_NODES['uc_credit'][0] - UC_W/2, UC_NODES['uc_credit'][1])
    draw_dependency(ax, p1, p2, "«include»")

    # 域 4 内部: 准入审查 -> 签发证书
    p1 = (UC_NODES['uc_admission'][0] - UC_W/2, UC_NODES['uc_admission'][1])
    p2 = (UC_NODES['uc_cert'][0] + UC_W/2, UC_NODES['uc_cert'][1])
    draw_dependency(ax, p1, p2, "«include»")

    # 账号治理 -> 审计穿透
    p1 = (UC_NODES['uc_users'][0] - UC_W/2 + 0.3, UC_NODES['uc_users'][1] - UC_H/2)
    p2 = (UC_NODES['uc_log'][0] + UC_W/2, UC_NODES['uc_log'][1] + 0.1)
    draw_dependency(ax, p1, p2, "«include»")

    # -------------------------------------------------------------
    # 8. 跨角色协同关键闭环连线 (Cross-Role Collaboration Flows)
    # -------------------------------------------------------------
    # 核心流转 1: 报告审核验签通过 -> 移交第三方监管出具存证 (跨域协作)
    p_from = (UC_NODES['uc_verify'][0] + UC_W/2, UC_NODES['uc_verify'][1])
    p_to = (UC_NODES['uc_fabric'][0] - UC_W/2, UC_NODES['uc_fabric'][1])
    ax.annotate('', xy=p_to, xytext=p_from,
                arrowprops=dict(arrowstyle="->,head_width=0.13,head_length=0.19",
                                color='#0284C7', lw=1.2, linestyle='-.',
                                shrinkA=0, shrinkB=0),
                zorder=3)
    ax.text((p_from[0] + p_to[0])/2, (p_from[1] + p_to[1])/2 + 0.08, "«通过流转»",
            fontsize=6.2, weight='bold', color='#0284C7', ha='center', va='bottom',
            bbox=dict(boxstyle='square,pad=0.10', facecolor='#FFFFFF', edgecolor='#0284C7', linewidth=0.6),
            zorder=6)

    # 核心流转 2: 管理员签发 X.509 证书 -> 提供企业数字签名凭据
    p_from = (UC_NODES['uc_cert'][0], UC_NODES['uc_cert'][1] + UC_H/2)
    p_to = (UC_NODES['uc_sign'][0] + 0.5, UC_NODES['uc_sign'][1] - UC_H/2)
    ax.annotate('', xy=p_to, xytext=p_from,
                arrowprops=dict(arrowstyle="->,head_width=0.13,head_length=0.19",
                                color='#16A34A', lw=1.2, linestyle='-.',
                                shrinkA=0, shrinkB=0,
                                connectionstyle="arc3,rad=-0.16"),
                zorder=3)
    ax.text((p_from[0] + p_to[0])/2 - 0.5, (p_from[1] + p_to[1])/2 - 0.15, "«准入授信»",
            fontsize=6.2, weight='bold', color='#16A34A', ha='center', va='top',
            bbox=dict(boxstyle='square,pad=0.10', facecolor='#FFFFFF', edgecolor='#16A34A', linewidth=0.6),
            zorder=6)

    out_p = os.path.join(OUTPUT_DIR, filename)
    fig.savefig(out_p, dpi=300, bbox_inches='tight', facecolor='#FFFFFF')
    plt.close(fig)
    print(f"标准 UML 全景用例图已成功生成: {out_p}")


if __name__ == '__main__':
    render_ea12_panoramic_diagram()
