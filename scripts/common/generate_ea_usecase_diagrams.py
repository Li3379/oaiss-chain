#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OAISS CHAIN - 标准 UML 2.5 角色用例图
100% 还原用户参考图模板视觉风格 (1253x444 等比横版企业级用例图)

视觉规格规范:
- 画布: 纯白底色 (#FFFFFF), 严格保持参考图宽高比 ≈ 2.82 : 1
- 系统边界: 单一浅灰细线矩形框 (#919191, linewidth=1.2), 纯白内部填充
- 系统标题: 水平居中置于系统矩形框内顶部正下方, 深灰加粗 (#111827), 无外围多余大标题/副标题/分割线
- 参与者 (Actor): 标准 Stick Man 图元, 居中置于系统边界左侧, 角色名称居中于双腿正下方 (纯中文, 无多余构造型)
- 用例椭圆: 极扁平椭圆 (实测宽高比 ≈ 4.8 : 1), 统一浅灰底 (#F0F0F0), 细灰边 (#919191), 深色文字 (#1F2937)
- 连线与关系 (依据 UML 2.5 规范):
  · 实线关联 (Association): 参与者 -> 每行基础用例 (实线, 无箭头, 精确触达椭圆边缘)
  · 虚线开放箭头 (include): 基础用例 -> 步骤用例 (虚线, 开放式 V 形箭头, 箭线上方标 include)
  · 绝无多余外围图例框或花哨多色填充, 纯粹工业标准

产出高分辨率图 (300 DPI):
- docs/assets/architecture/02-enterprise-usecase-diagram.png        企业用户用例图
- docs/assets/architecture/02-reviewer-usecase-diagram.png          数据审核员用例图
- docs/assets/architecture/02-thirdparty-admin-usecase-diagram.png  第三方监管机构与系统管理员用例图
- docs/assets/architecture/02-four-roles-collaboration.png          系统多角色核心用例全景图
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

# 标准统一尺寸与配色
W = 16.0
H = 5.67  # 比例 2.822 : 1，对标 1253x444

UC_W = 2.36
UC_H = 0.49
FONT_UC = 8.6
FONT_TAG = 6.6

UC_FILL = '#F0F0F0'    # 模板取样浅灰
UC_BORDER = '#919191'  # 模板取样细灰边
UC_TEXT = '#1F2937'    # 深灰文字
LINE_COLOR = '#4B5563' # 连线颜色


def edge_point(from_pt, cx, cy):
    """计算从外部点指向椭圆中心时，射线与椭圆边缘的精确交点"""
    a, b = UC_W / 2.0, UC_H / 2.0
    dx, dy = cx - from_pt[0], cy - from_pt[1]
    dist = math.hypot(dx, dy)
    if dist < 1e-6:
        return (cx, cy)
    t = 1.0 / math.sqrt((dx / (a * dist)) ** 2 + (dy / (b * dist)) ** 2)
    return (from_pt[0] + dx * t, from_pt[1] + dy * t)


def draw_actor(ax, cx, cy, name, scale=1.0):
    """绘制标准 UML Stick Man 图元与角色名称"""
    # 头 (圆)
    ax.add_patch(patches.Circle((cx, cy + 0.46 * scale), 0.13 * scale,
                                edgecolor='#1F2937', facecolor='#FFFFFF', linewidth=1.3, zorder=4))
    # 躯干 (纵向垂直线)
    ax.plot([cx, cx], [cy + 0.33 * scale, cy + 0.05 * scale], color='#1F2937', linewidth=1.3, zorder=4)
    # 双臂 (横向水平线)
    ax.plot([cx - 0.22 * scale, cx + 0.22 * scale], [cy + 0.22 * scale, cy + 0.22 * scale],
            color='#1F2937', linewidth=1.3, zorder=4)
    # 双腿 (倒 V 形斜线)
    ax.plot([cx, cx - 0.17 * scale], [cy + 0.05 * scale, cy - 0.20 * scale], color='#1F2937', linewidth=1.3, zorder=4)
    ax.plot([cx, cx + 0.17 * scale], [cy + 0.05 * scale, cy - 0.20 * scale], color='#1F2937', linewidth=1.3, zorder=4)
    # 角色名在下方居中
    ax.text(cx, cy - 0.32 * scale, name, fontsize=9.2, weight='bold', color='#111827',
            ha='center', va='top', zorder=4)


def draw_uc(ax, cx, cy, text):
    """绘制企业级用例椭圆"""
    ax.add_patch(patches.Ellipse((cx, cy), UC_W, UC_H,
                                 edgecolor=UC_BORDER, facecolor=UC_FILL,
                                 linewidth=1.1, zorder=3))
    ax.text(cx, cy, text, fontsize=FONT_UC, color=UC_TEXT,
            ha='center', va='center', zorder=4)


def draw_assoc(ax, p_from, p_to):
    """绘制 UML 2.5 关联关系 (无箭头实线)"""
    ax.plot([p_from[0], p_to[0]], [p_from[1], p_to[1]], color=LINE_COLOR, linewidth=1.1, zorder=2)


def draw_include(ax, from_cx, to_cx, cy, label='include'):
    """绘制横向 include 关系 (虚线 + 开放式箭头)"""
    p1 = (from_cx + UC_W / 2.0, cy)
    p2 = (to_cx - UC_W / 2.0, cy)
    ax.annotate('', xy=p2, xytext=p1,
                arrowprops=dict(arrowstyle="->,head_width=0.14,head_length=0.20",
                                color=LINE_COLOR, lw=1.0, linestyle='--',
                                shrinkA=0, shrinkB=0),
                zorder=2)
    mx = (p1[0] + p2[0]) / 2.0
    ax.text(mx, cy + 0.065, label, fontsize=FONT_TAG, color=LINE_COLOR,
            ha='center', va='bottom', zorder=4)


def render_single_role_diagram(filename, system_title, actor_name, usecase_matrix):
    """
    渲染单角色 4 行 × 4 列横版企业级用例图
    严格契合模板：系统标题居中置于框内顶部、Actor 左置垂直居中、统一灰调
    """
    fig, ax = plt.subplots(figsize=(W, H), dpi=300)
    fig.patch.set_facecolor('#FFFFFF')
    ax.set_facecolor('#FFFFFF')
    ax.set_xlim(0, W)
    ax.set_ylim(0, H)
    ax.axis('off')

    # 系统边界矩形
    bnd_x = 2.15
    bnd_y = 0.28
    bnd_w = W - bnd_x - 0.72  # 13.13
    bnd_h = H - bnd_y - 0.28  # 5.11

    ax.add_patch(patches.Rectangle((bnd_x, bnd_y), bnd_w, bnd_h,
                                   edgecolor='#919191', facecolor='#FFFFFF',
                                   linewidth=1.2, zorder=1))

    # 系统标题居中位于边界内顶部
    ax.text(bnd_x + bnd_w / 2.0, bnd_y + bnd_h - 0.32, system_title,
            fontsize=11.5, weight='bold', color='#111827', ha='center', va='center', zorder=2)

    # 网格坐标
    col_xs = [
        bnd_x + 1.25,
        bnd_x + 4.80,
        bnd_x + 8.35,
        bnd_x + 11.90
    ]
    row_ys = [
        bnd_y + bnd_h - 1.05,
        bnd_y + bnd_h - 2.05,
        bnd_y + bnd_h - 3.05,
        bnd_y + bnd_h - 4.05
    ]

    centers = []
    for r, row in enumerate(usecase_matrix):
        row_centers = []
        cy = row_ys[r]
        for c, text in enumerate(row):
            cx = col_xs[c]
            draw_uc(ax, cx, cy, text)
            row_centers.append((cx, cy))
        centers.append(row_centers)

    # Actor 垂直居中于业务区左侧
    actor_cx = 1.05
    actor_cy = (row_ys[0] + row_ys[-1]) / 2.0
    draw_actor(ax, actor_cx, actor_cy, actor_name)

    # 实线关联: Actor -> 基础用例 (每行第 1 列)
    actor_pt = (actor_cx + 0.35, actor_cy)
    for r in range(len(usecase_matrix)):
        first_cx, first_cy = centers[r][0]
        target_pt = edge_point(actor_pt, first_cx, first_cy)
        draw_assoc(ax, actor_pt, target_pt)

    # 虚线 include 关系
    for r in range(len(usecase_matrix)):
        for c in range(len(usecase_matrix[r]) - 1):
            draw_include(ax, centers[r][c][0], centers[r][c+1][0], row_ys[r])

    out_p = os.path.join(OUTPUT_DIR, filename)
    plt.savefig(out_p, dpi=300, bbox_inches='tight', facecolor='#FFFFFF')
    plt.close(fig)
    print(f"已生成: {filename}")


def render_dual_role_diagram(filename, system_title, actor1_name, actor2_name, matrix1, matrix2):
    """
    渲染双角色横版用例图 (第三方监管机构与系统管理员，各占 2 行)
    """
    fig, ax = plt.subplots(figsize=(W, H), dpi=300)
    fig.patch.set_facecolor('#FFFFFF')
    ax.set_facecolor('#FFFFFF')
    ax.set_xlim(0, W)
    ax.set_ylim(0, H)
    ax.axis('off')

    bnd_x = 2.15
    bnd_y = 0.28
    bnd_w = W - bnd_x - 0.72
    bnd_h = H - bnd_y - 0.28

    ax.add_patch(patches.Rectangle((bnd_x, bnd_y), bnd_w, bnd_h,
                                   edgecolor='#919191', facecolor='#FFFFFF',
                                   linewidth=1.2, zorder=1))

    ax.text(bnd_x + bnd_w / 2.0, bnd_y + bnd_h - 0.32, system_title,
            fontsize=11.5, weight='bold', color='#111827', ha='center', va='center', zorder=2)

    col_xs = [
        bnd_x + 1.25,
        bnd_x + 4.80,
        bnd_x + 8.35,
        bnd_x + 11.90
    ]
    row_ys = [
        bnd_y + bnd_h - 1.05,
        bnd_y + bnd_h - 2.05,
        bnd_y + bnd_h - 3.05,
        bnd_y + bnd_h - 4.05
    ]

    # 上半区: Actor 1
    centers1 = []
    for r, row in enumerate(matrix1):
        row_centers = []
        cy = row_ys[r]
        for c, text in enumerate(row):
            cx = col_xs[c]
            draw_uc(ax, cx, cy, text)
            row_centers.append((cx, cy))
        centers1.append(row_centers)

    actor1_cx = 1.05
    actor1_cy = (row_ys[0] + row_ys[1]) / 2.0
    draw_actor(ax, actor1_cx, actor1_cy, actor1_name)

    actor1_pt = (actor1_cx + 0.35, actor1_cy)
    for r in range(len(matrix1)):
        p_to = edge_point(actor1_pt, centers1[r][0][0], centers1[r][0][1])
        draw_assoc(ax, actor1_pt, p_to)
        for c in range(len(matrix1[r]) - 1):
            draw_include(ax, centers1[r][c][0], centers1[r][c+1][0], row_ys[r])

    # 下半区: Actor 2
    centers2 = []
    for r, row in enumerate(matrix2):
        row_centers = []
        cy = row_ys[r + 2]
        for c, text in enumerate(row):
            cx = col_xs[c]
            draw_uc(ax, cx, cy, text)
            row_centers.append((cx, cy))
        centers2.append(row_centers)

    actor2_cx = 1.05
    actor2_cy = (row_ys[2] + row_ys[3]) / 2.0
    draw_actor(ax, actor2_cx, actor2_cy, actor2_name)

    actor2_pt = (actor2_cx + 0.35, actor2_cy)
    for r in range(len(matrix2)):
        p_to = edge_point(actor2_pt, centers2[r][0][0], centers2[r][0][1])
        draw_assoc(ax, actor2_pt, p_to)
        for c in range(len(matrix2[r]) - 1):
            draw_include(ax, centers2[r][c][0], centers2[r][c+1][0], row_ys[r + 2])

    out_p = os.path.join(OUTPUT_DIR, filename)
    plt.savefig(out_p, dpi=300, bbox_inches='tight', facecolor='#FFFFFF')
    plt.close(fig)
    print(f"已生成: {filename}")


def render_panoramic_diagram(filename):
    """
    渲染系统多角色核心用例全景图 (2×2 象限统一风格)
    """
    PAN_W = 16.0
    PAN_H = 11.34  # 相当于上下两张横版图组合 (长宽比 1.41:1)

    fig, ax = plt.subplots(figsize=(PAN_W, PAN_H), dpi=300)
    fig.patch.set_facecolor('#FFFFFF')
    ax.set_facecolor('#FFFFFF')
    ax.set_xlim(0, PAN_W)
    ax.set_ylim(0, PAN_H)
    ax.axis('off')

    quad_w = 7.70
    quad_h = 5.25

    quads = [
        # (ox, oy, title, actor, matrix)
        (0.20, 5.75, "OAISS-CHAIN 双碳链动系统 —— 控排企业核心用例", "控排企业", [
            ["碳核算填报", "生产台账录入", "公式实时推导", "核算报告送审"],
            ["报告数字签名", "私钥信封解密", "原始化验凭证", "链上哈希核验"],
            ["双向拍卖挂单", "配额资产冻结", "连续撮合出清", "DVP 原子交割"],
            ["碳币账户管理", "发起碳币转账", "信用评分查询", "奖惩明细查阅"]
        ]),
        (8.10, 5.75, "OAISS-CHAIN 双碳链动系统 —— 数据审核员核心用例", "数据审核员", [
            ["登录审核台", "验证码校验", "双令牌签发", "待办看板加载"],
            ["认领待审报告", "权限编辑锁定", "AI预审查阅", "参数凭证核验"],
            ["企业RSA验签", "调取登记公钥", "防篡改告警", "签署通过决定"],
            ["结构化驳回", "驳回意见出具", "异步整改通知", "审核历史统计"]
        ]),
        (0.20, 0.25, "OAISS-CHAIN 双碳链动系统 —— 第三方监管核心用例", "第三方监管", [
            ["监管大盘访问", "合规偏离雷达", "时序价格预测", "异常排放归因"],
            ["出具存证决定", "Fabric链码上链", "区块高度穿透", "TxHash指纹固化"],
            ["穿透审计日志", "追溯流转记录", "下载存证证书", "存证报告归档"],
            ["监管预警推送", "超排企业示警", "风险协同排查", "合规报告出具"]
        ]),
        (8.10, 0.25, "OAISS-CHAIN 双碳链动系统 —— 系统管理员核心用例", "系统管理员", [
            ["企业准入审查", "证书签发纳管", "违规企业注销", "核查资质审核"],
            ["全域账号治理", "启禁用状态切换", "角色权限划分", "机构绑定归属"],
            ["系统参数配置", "费率动态调整", "撮合周期调优", "监控阈值设定"],
            ["服务健康态势", "Fabric节点探测", "机器学习引擎", "容器资源巡检"]
        ])
    ]

    for (ox, oy, q_title, q_actor, matrix) in quads:
        # 子系统边界框
        bnd_x = ox + 1.25
        bnd_y = oy + 0.15
        bnd_w = quad_w - 1.35
        bnd_h = quad_h - 0.25

        ax.add_patch(patches.Rectangle((bnd_x, bnd_y), bnd_w, bnd_h,
                                       edgecolor='#919191', facecolor='#FFFFFF',
                                       linewidth=1.1, zorder=1))

        # 框内居中标题
        ax.text(bnd_x + bnd_w / 2.0, bnd_y + bnd_h - 0.25, q_title,
                fontsize=7.8, weight='bold', color='#111827', ha='center', va='center', zorder=2)

        # 象限内网格
        q_uc_w = 1.15
        q_uc_h = 0.36
        q_col_xs = [bnd_x + 0.72 + c * 1.56 for c in range(4)]
        q_row_ys = [bnd_y + bnd_h - 0.85 - r * 1.05 for r in range(4)]

        q_centers = []
        for r, row in enumerate(matrix):
            r_c = []
            cy = q_row_ys[r]
            for c, text in enumerate(row):
                cx = q_col_xs[c]
                ax.add_patch(patches.Ellipse((cx, cy), q_uc_w, q_uc_h,
                                             edgecolor=UC_BORDER, facecolor=UC_FILL,
                                             linewidth=0.9, zorder=3))
                ax.text(cx, cy, text, fontsize=5.8, color=UC_TEXT,
                        ha='center', va='center', zorder=4)
                r_c.append((cx, cy))
            q_centers.append(r_c)

        # Actor
        act_cx = ox + 0.58
        act_cy = (q_row_ys[0] + q_row_ys[-1]) / 2.0
        draw_actor(ax, act_cx, act_cy, q_actor, scale=0.78)

        # 实线关联
        act_pt = (act_cx + 0.25, act_cy)
        for r in range(4):
            fcx, fcy = q_centers[r][0]
            # 计算精确边缘点 (使用较小椭圆尺寸)
            a, b = q_uc_w / 2.0, q_uc_h / 2.0
            dx, dy = fcx - act_pt[0], fcy - act_pt[1]
            dist = math.hypot(dx, dy)
            t = 1.0 / math.sqrt((dx / (a * dist)) ** 2 + (dy / (b * dist)) ** 2)
            p_to = (act_pt[0] + dx * t, act_pt[1] + dy * t)
            draw_assoc(ax, act_pt, p_to)

            # 行内 include
            for c in range(3):
                p1 = (q_centers[r][c][0] + q_uc_w / 2.0, cy)
                p2 = (q_centers[r][c+1][0] - q_uc_w / 2.0, cy)
                ax.annotate('', xy=p2, xytext=p1,
                            arrowprops=dict(arrowstyle="->,head_width=0.10,head_length=0.15",
                                            color=LINE_COLOR, lw=0.85, linestyle='--',
                                            shrinkA=0, shrinkB=0),
                            zorder=2)
                mx = (p1[0] + p2[0]) / 2.0
                ax.text(mx, cy + 0.045, 'include', fontsize=4.8, color=LINE_COLOR,
                        ha='center', va='bottom', zorder=4)

    out_p = os.path.join(OUTPUT_DIR, filename)
    plt.savefig(out_p, dpi=300, bbox_inches='tight', facecolor='#FFFFFF')
    plt.close(fig)
    print(f"已生成: {filename}")


def main():
    print("开始生成符合企业用例图与 UML 2.5 规范的全新高清图表...")

    # 1. 控排企业用例图
    enterprise_matrix = [
        ["碳核算模板填报", "录入生产活动台账", "国标公式实时推导", "提交核算报告送审"],
        ["报告数字签名", "私钥信封解密", "上传原始化验凭证", "查询链上存证哈希"],
        ["双向拍卖挂单", "冻结碳币配额资产", "连续撮合出清", "DVP 原子清算交割"],
        ["碳币账户管理", "发起碳币转账", "碳信用履约评分查询", "奖惩事件明细查阅"]
    ]
    render_single_role_diagram(
        filename='02-enterprise-usecase-diagram.png',
        system_title="OAISS-CHAIN 双碳链动系统 —— 控排企业核心业务系统",
        actor_name="控排企业",
        usecase_matrix=enterprise_matrix
    )

    # 2. 数据审核员用例图
    reviewer_matrix = [
        ["登录审核工作台", "图形验证码校验", "JWT 双令牌签发", "加载待办统计看板"],
        ["认领待审报告", "锁定报告编辑权限", "AI 预审结论查阅", "查验工艺参数凭证"],
        ["企业签名 RSA 验签", "调取企业登记公钥", "篡改检测与告警", "签署审核通过决定"],
        ["结构化驳回出具", "驳回类别与意见填写", "异步发送整改通知", "审核历史与通过率"]
    ]
    render_single_role_diagram(
        filename='02-reviewer-usecase-diagram.png',
        system_title="OAISS-CHAIN 双碳链动系统 —— 数据审核员公信核验系统",
        actor_name="数据审核员",
        usecase_matrix=reviewer_matrix
    )

    # 3. 第三方监管机构与系统管理员用例图 (方案 A: 监管只读穿透，无上链审批)
    thirdparty_matrix = [
        ["访问双碳监管大盘", "企业合规偏离雷达图", "时序价格预测通道", "异常排放特征归因"],
        ["存证穿透比对与公信核验", "Fabric 账本只读查询", "背书节点签名查验", "存证指纹公信核验"]
    ]
    admin_matrix = [
        ["企业准入审查", "签发碳管理证书", "吊销违规企业资质", "审核员资质认证"],
        ["全平台账号治理", "动态调整交易费率", "穿透审计敏感日志", "服务健康态势巡检"]
    ]
    render_dual_role_diagram(
        filename='02-thirdparty-admin-usecase-diagram.png',
        system_title="OAISS-CHAIN 双碳链动系统 —— 监管态势与系统治理系统",
        actor1_name="第三方监管机构",
        actor2_name="系统管理员",
        matrix1=thirdparty_matrix,
        matrix2=admin_matrix
    )

    # 4. 系统多角色核心用例全景图
    render_panoramic_diagram('02-four-roles-collaboration.png')

    print("全部用例图已成功生成完毕！")


if __name__ == '__main__':
    main()
