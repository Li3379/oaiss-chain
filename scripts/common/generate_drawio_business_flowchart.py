#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OAISS CHAIN - 绘制符合 draw.io 标准的 300 DPI 经典工科风格多角色泳道业务流程图
替换原有的简单深色图，完美对齐用户提供的 draw.io 模板图格式：
1. 背景纯白，经典 draw.io 商务工科灰白配色
2. 5 大角色垂直泳道 + 3 大业务阶段横向分区（二维矩阵泳道）
3. 标准流程图元：圆角胶囊（开始/结束）、圆角矩形（处理活动）、菱形（条件判断决策）
4. 正交折线箭头（Orthogonal Connectors）与条件分支标注（通过/驳回）
5. 完整呈现真实业务：台账填报 -> 国标推导 -> RSA签名 -> 审核驳回闭环 -> Fabric上链 -> 双向拍卖DVP结算
"""

import os
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.patches as patches

OUTPUT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '../../docs/assets/architecture'))
os.makedirs(OUTPUT_DIR, exist_ok=True)
PNG_PATH = os.path.join(OUTPUT_DIR, '03-carbon-lifecycle-state-machine.png')

plt.rcParams['font.sans-serif'] = ['Microsoft YaHei', 'SimHei', 'DejaVu Sans']
plt.rcParams['axes.unicode_minus'] = False

def draw_drawio_swimlane_chart():
    # 画幅尺寸 16 x 11.5 英寸, 300 DPI (超高清)
    fig, ax = plt.subplots(figsize=(16, 11.5), dpi=300)
    fig.patch.set_facecolor('#FFFFFF')
    ax.set_facecolor('#FFFFFF')
    ax.set_xlim(0, 16)
    ax.set_ylim(0, 11.5)
    ax.axis('off')

    # -------------------------------------------------------------
    # 1. 泳道定义与网格框架绘制
    # -------------------------------------------------------------
    # X 轴定义 (各泳道 X 边界)
    # x=0.6: 外框左
    # x=2.1: 阶段列 (宽 1.5)
    # x=5.1: 企业用户 (宽 3.0)
    # x=8.1: 系统中台/算法微服务 (宽 3.0)
    # x=10.8: 数据审核员 (宽 2.7)
    # x=13.3: 第三方监管机构 (宽 2.5)
    # x=15.6: Fabric区块链/交易撮合底座 (宽 2.3)
    
    x_outer_l = 0.5
    x_outer_r = 15.6
    y_outer_b = 0.5
    y_outer_t = 11.0
    
    col_splits = [0.5, 2.0, 5.2, 8.4, 11.1, 13.5, 15.6]
    row_splits = [0.5, 3.8, 7.4, 10.3, 11.0] # 阶段3, 阶段2, 阶段1, 泳道表头, 顶部
    
    # 画最外层边框 (1.5pt 黑灰色)
    outer_box = patches.Rectangle((x_outer_l, y_outer_b), x_outer_r - x_outer_l, y_outer_t - y_outer_b,
                                  linewidth=1.5, edgecolor='#2B2B2B', facecolor='none')
    ax.add_patch(outer_box)
    
    # 绘制泳道表头背景 (浅灰填充 #EFEFEF)
    hdr_box = patches.Rectangle((x_outer_l, row_splits[3]), x_outer_r - x_outer_l, row_splits[4] - row_splits[3],
                                linewidth=1.2, edgecolor='#2B2B2B', facecolor='#F2F4F7')
    ax.add_patch(hdr_box)
    
    # 绘制阶段侧边栏背景 (极浅灰底 #F8F9FA)
    phase_box = patches.Rectangle((x_outer_l, y_outer_b), col_splits[1] - x_outer_l, row_splits[3] - y_outer_b,
                                  linewidth=1.2, edgecolor='#2B2B2B', facecolor='#F8F9FA')
    ax.add_patch(phase_box)

    # 绘制纵向分割线
    for x in col_splits[1:-1]:
        ax.plot([x, x], [y_outer_b, y_outer_t], color='#333333', linewidth=1.1)

    # 绘制横向阶段分割线
    for y in row_splits[1:]:
        ax.plot([x_outer_l, x_outer_r], [y, y], color='#333333', linewidth=1.1)

    # -------------------------------------------------------------
    # 2. 泳道标题与阶段标题
    # -------------------------------------------------------------
    # 顶部各泳道标题
    ax.text(1.25, 10.65, '业务阶段', fontsize=11, color='#1A1A1A', weight='bold', ha='center', va='center')
    ax.text(3.6, 10.65, '控排企业主体\n(ENTERPRISE)', fontsize=10.5, color='#1A1A1A', weight='bold', ha='center', va='center')
    ax.text(6.8, 10.65, '系统数据中台 / 计算引擎\n(Spring Boot & ML Service)', fontsize=10.5, color='#1A1A1A', weight='bold', ha='center', va='center')
    ax.text(9.75, 10.65, '数据审核员\n(REVIEWER)', fontsize=10.5, color='#1A1A1A', weight='bold', ha='center', va='center')
    ax.text(12.3, 10.65, '第三方监管机构\n(THIRD_PARTY)', fontsize=10.5, color='#1A1A1A', weight='bold', ha='center', va='center')
    ax.text(14.55, 10.65, '可信存证与市场底座\n(Fabric & Engine)', fontsize=10.5, color='#1A1A1A', weight='bold', ha='center', va='center')

    # 左侧各阶段横向分组文字 (垂直居中)
    ax.text(1.25, 8.85, '阶段一\n\n数据采集\n与国标核算', fontsize=11, color='#2B2B2B', weight='bold', ha='center', va='center')
    ax.text(1.25, 5.60, '阶段二\n\n数字签名\n与多级审查', fontsize=11, color='#2B2B2B', weight='bold', ha='center', va='center')
    ax.text(1.25, 2.15, '阶段三\n\n联盟链存证\n与市场履约', fontsize=11, color='#2B2B2B', weight='bold', ha='center', va='center')

    # -------------------------------------------------------------
    # 3. 节点绘制辅助函数 (draw.io 规范图形)
    # -------------------------------------------------------------
    def draw_terminal(cx, cy, w, h, text, bg='#E8EEF5', border='#2B4C7E'):
        """开始/结束胶囊状节点"""
        rect = patches.FancyBboxPatch((cx - w/2, cy - h/2), w, h,
                                      boxstyle='round,pad=0.04,rounding_size=0.25',
                                      edgecolor=border, facecolor=bg, linewidth=1.3)
        ax.add_patch(rect)
        ax.text(cx, cy, text, fontsize=9.5, color='#111827', weight='bold', ha='center', va='center')

    def draw_process(cx, cy, w, h, text, bg='#FFFFFF', border='#374151', text_color='#111827'):
        """标准活动节点 (圆角矩形)"""
        rect = patches.FancyBboxPatch((cx - w/2, cy - h/2), w, h,
                                      boxstyle='round,pad=0.03,rounding_size=0.10',
                                      edgecolor=border, facecolor=bg, linewidth=1.2)
        ax.add_patch(rect)
        ax.text(cx, cy, text, fontsize=9.2, color=text_color, ha='center', va='center', linespacing=1.25)

    def draw_decision(cx, cy, w, h, text, bg='#FFFBEB', border='#D97706'):
        """判断菱形节点"""
        diamond = patches.Polygon([[cx, cy + h/2], [cx + w/2, cy], [cx, cy - h/2], [cx - w/2, cy]], closed=True,
                                  edgecolor=border, facecolor=bg, linewidth=1.3)
        ax.add_patch(diamond)
        ax.text(cx, cy, text, fontsize=8.8, color='#92400E', weight='bold', ha='center', va='center', linespacing=1.2)

    def draw_arrow_path(points, label='', label_pos='top', label_color='#1F2937', color='#1F2937'):
        """正交折线连接器"""
        for i in range(len(points) - 1):
            p1 = points[i]
            p2 = points[i+1]
            if i == len(points) - 2:
                # 最后一节带箭头
                ax.annotate('', xy=p2, xytext=p1,
                            arrowprops=dict(arrowstyle="->,head_width=0.35,head_length=0.45",
                                            color=color, lw=1.2, shrinkA=0, shrinkB=0))
            else:
                ax.plot([p1[0], p2[0]], [p1[1], p2[1]], color=color, linewidth=1.2)
        
        if label and len(points) >= 2:
            # 在第一段或拐角处写标签
            lx = (points[0][0] + points[1][0]) / 2
            ly = (points[0][1] + points[1][1]) / 2
            dy = 0.12 if label_pos == 'top' else -0.12
            dx = 0.15 if label_pos == 'right' else (-0.15 if label_pos == 'left' else 0)
            ax.text(lx + dx, ly + dy, label, fontsize=8.2, color=label_color, weight='bold',
                    ha='center', va='center',
                    bbox=dict(boxstyle='square,pad=0.15', facecolor='#FFFFFF', edgecolor='none', alpha=0.9))

    # -------------------------------------------------------------
    # 4. 节点具体布局与绘制
    # -------------------------------------------------------------

    # === 阶段一：数据采集与国标核算 ===
    # 1. 开始
    draw_terminal(3.6, 9.85, 1.4, 0.45, "流程开始")
    
    # 2. 采集能源台账
    draw_process(3.6, 8.95, 2.5, 0.70, "采集化石燃料消耗\n与购电用能原始凭证")
    
    # 3. 填报核算参数
    draw_process(3.6, 7.85, 2.5, 0.70, "选择行业模板录入参数\n(火电25项 / 电网9项)")
    
    # 4. 实时核算与试算推导
    draw_process(6.8, 7.85, 2.5, 0.70, "调用 GB/T 32150 引擎\n自动推导各工序排放总量")
    
    # 5. 校验决策
    draw_decision(6.8, 8.95, 2.2, 0.85, "参数区间与\n完整性校验？")

    # 阶段一连线
    draw_arrow_path([(3.6, 9.62), (3.6, 9.30)])
    draw_arrow_path([(3.6, 8.60), (3.6, 8.20)])
    draw_arrow_path([(4.85, 7.85), (5.55, 7.85)], label='提交试算', label_pos='top')
    draw_arrow_path([(6.8, 8.20), (6.8, 8.52)])
    # 校验不通过：折回重新录入
    draw_arrow_path([(5.7, 8.95), (4.85, 8.95)], label='不通过: 提示异常', label_pos='top', color='#DC2626', label_color='#B91C1C')
    # 校验通过：向下流转到草稿保存
    draw_process(6.8, 6.75, 2.4, 0.65, "生成核算报告草稿\n状态: DRAFT (已锁定)")
    draw_arrow_path([(6.8, 8.52), (6.8, 7.07)], label='通过', label_pos='right', color='#059669', label_color='#047857')

    # === 阶段二：数字签名与多级审查 ===
    # 6. 企业私钥签名
    draw_process(3.6, 6.75, 2.5, 0.65, "调用本地 RSA-2048 私钥\n计算报告签名 (SHA256withRSA)")
    draw_arrow_path([(5.6, 6.75), (4.85, 6.75)], label='加载草稿', label_pos='top')

    # 7. 提交待审
    draw_process(3.6, 5.65, 2.5, 0.65, "签名数据连同佐证附件上传\n状态: PENDING_REVIEW")
    draw_arrow_path([(3.6, 6.42), (3.6, 5.97)])

    # 8. 审核员认领核验
    draw_process(9.75, 5.65, 2.3, 0.70, "认领锁定待审报告\n在线核验附件与工艺参数")
    draw_arrow_path([(4.85, 5.65), (8.60, 5.65)], label='分配流转', label_pos='top')

    # 9. 审核决策菱形
    draw_decision(9.75, 4.35, 2.2, 0.85, "数据真实性与\n计算合规审核？")
    draw_arrow_path([(9.75, 5.30), (9.75, 4.77)])

    # 10. 驳回流转
    draw_process(7.0, 4.35, 2.1, 0.65, "填写结构化驳回原因\n状态: REJECTED", bg='#FEF2F2', border='#EF4444', text_color='#991B1B')
    draw_arrow_path([(8.65, 4.35), (8.05, 4.35)], label='审核驳回', label_pos='top', color='#DC2626', label_color='#B91C1C')
    # 驳回退回到企业重新修改
    draw_arrow_path([(5.95, 4.35), (3.6, 4.35), (3.6, 5.32)], label='异步站内信通知整改', label_pos='top', color='#DC2626', label_color='#B91C1C')

    # 11. 审核通过流转 (方案 A: 审核通过系统直接自动上链)
    # === 阶段三：联盟链存证与市场履约 ===
    # 12. 写入 Outbox 外箱与触发上链
    draw_process(6.8, 3.10, 2.5, 0.65, "写入事务外发箱 (Outbox)\n触发 Fabric 异步存证事件")
    draw_arrow_path([(9.75, 3.92), (9.75, 3.10), (8.05, 3.10)], label='审核通过 (系统自动上链)', label_pos='top', color='#059669', label_color='#047857')

    # 13. Fabric 联盟链上链存证
    draw_process(14.55, 3.10, 1.9, 0.75, "Fabric 智能合约执行\n多组织背书与账本固化\n(生成 TxHash 凭证)")
    draw_arrow_path([(8.05, 3.10), (13.60, 3.10)], label='Gateway gRPC 提议', label_pos='top')

    # 14. 存证完成终态
    draw_process(6.8, 2.05, 2.5, 0.65, "回写区块链 TxHash 凭证\n报告终态: ON_CHAIN")
    draw_arrow_path([(14.55, 2.72), (14.55, 2.05), (8.05, 2.05)], label='存证回执', label_pos='top')

    # 15. 配额划转入市 & 双向拍卖撮合
    draw_process(3.6, 2.05, 2.5, 0.75, "配额入市交易\n发起双向拍卖挂单 (CDA)\n或 P2P 协商点对点交易")
    draw_arrow_path([(5.55, 2.05), (4.85, 2.05)], label='配额过户', label_pos='top')

    # 16. 撮合引擎撮合成交与 DVP 结算
    draw_process(3.6, 0.95, 2.5, 0.65, "连续撮合引擎出清\nDVP 资金与配额原子结算")
    draw_arrow_path([(3.6, 1.67), (3.6, 1.27)])

    # 17. 监管大屏与穿透查询
    draw_process(12.3, 0.95, 2.2, 0.65, "监管大屏实时同步\n穿透查询链上 TxHash", bg='#F0F9FF', border='#0284C7', text_color='#0369A1')
    draw_arrow_path([(4.85, 0.95), (11.20, 0.95)], label='广播交易事件', label_pos='top')

    # 18. 结束节点
    draw_terminal(14.55, 0.95, 1.7, 0.45, "流程闭环 / 归档", bg='#E2E8F0', border='#475569')
    draw_arrow_path([(13.40, 0.95), (13.70, 0.95)])

    # -------------------------------------------------------------
    # 5. 保存输出
    # -------------------------------------------------------------
    fig.savefig(PNG_PATH, bbox_inches='tight', dpi=300, facecolor=fig.get_facecolor(), edgecolor='none')
    plt.close(fig)
    print(f"成功生成 draw.io 风格业务流程图: {PNG_PATH}")

if __name__ == '__main__':
    draw_drawio_swimlane_chart()
