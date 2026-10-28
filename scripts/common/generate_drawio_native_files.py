#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OAISS CHAIN - 标准 UML 2.5 原生 02-system-usecases.drawio 工程文件

版式规格 (与 PNG 渲染脚本 generate_ea_usecase_diagrams.py 同源, 对标用户参考图):
- 横向长画布: 系统边界内 4 行 × 4 列; 每行 = 一条完整业务链
- 参与者垂直居中于业务区左侧, 实线关联至每行第一个用例
- 行内 «include» 虚线开放箭头自左向右逐级串联 (箭头指向被包含用例)
- «extend» 虚线开放箭头自扩展用例指向被扩展用例 (条件分支)

关系仅使用标准三类 (UML 2.5):
1. 关联 (Association) : 参与者—用例, 实线, 无箭头无标签
2. 包含 («include»)   : 基础用例 → 被包含用例, 虚线 + 开放箭头
3. 扩展 («extend»)    : 扩展用例 → 被扩展用例, 虚线 + 开放箭头

五个 Tab 页:
- page1_panoramic    : 多角色核心用例全景图 (2×2 象限, 每象限 4×4 流水线)
- page2_enterprise   : 控排企业用例图
- page3_reviewer     : 数据审核员用例图
- page4_thirdparty   : 第三方监管机构用例图
- page5_admin        : 系统管理员用例图
"""

import os
import xml.etree.ElementTree as ET

ARCH_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '../../docs/assets/architecture'))
os.makedirs(ARCH_DIR, exist_ok=True)
OUT_DRAWIO = os.path.join(ARCH_DIR, '02-system-usecases.drawio')

# 几何常量 (对标参考图实测比例)
UC_W, UC_H = 150, 34          # 用例椭圆 (参考图实测宽高比 4.3~4.8:1)
ACT_W, ACT_H = 40, 76         # 参与者
COL_X = [400, 610, 820, 1030]
ROW_STEP = 74
TOP_Y = 170

TITLE_STYLE = ("text;html=1;strokeColor=none;fillColor=none;align=left;verticalAlign=middle;"
               "rounded=0;fontStyle=1;fontSize=15;fontColor=#111827;")
BND_STYLE = ("rounded=0;whiteSpace=wrap;html=1;fillColor=#FAFAFA;strokeColor=#C9CDD4;strokeWidth=1.1;"
             "verticalAlign=top;align=left;spacingLeft=12;spacingTop=6;fontStyle=1;fontSize=12;fontColor=#374151;")
ACTOR_STYLE = ("shape=umlActor;verticalLabelPosition=bottom;verticalAlign=top;html=1;outlineConnect=0;"
               "fillColor=#FFFFFF;strokeColor=#1F2937;strokeWidth=1.3;fontStyle=1;fontSize=11;align=center;")
ASSOC_STYLE = "endArrow=none;html=1;rounded=0;strokeColor=#374151;strokeWidth=1.1;"
DEP_STYLE = ("endArrow=open;dashed=1;html=1;rounded=0;strokeColor=#4B5563;strokeWidth=1.0;"
             "endFill=0;fontSize=9;fontColor=#4B5563;exitX=1;exitY=0.5;entryX=0;entryY=0.5;")
DEP_RV_STYLE = ("endArrow=open;dashed=1;html=1;rounded=0;strokeColor=#4B5563;strokeWidth=1.0;"
                "endFill=0;fontSize=9;fontColor=#4B5563;exitX=0;exitY=0.5;entryX=1;entryY=0.5;")
LEGEND_STYLE = ("rounded=0;whiteSpace=wrap;html=1;fillColor=#FFFFFF;strokeColor=#E5E7EB;strokeWidth=0.9;"
                "align=left;verticalAlign=top;spacingLeft=10;spacingTop=6;fontSize=9;fontColor=#4B5563;")

UC_PRIMARY = "ellipse;whiteSpace=wrap;html=1;fillColor=#F0F9FF;strokeColor=#0284C7;strokeWidth=1.0;fontSize=10;align=center;"
UC_ACCENT = "ellipse;whiteSpace=wrap;html=1;fillColor=#F0FDF4;strokeColor=#16A34A;strokeWidth=1.0;fontSize=10;align=center;"
UC_WARN = "ellipse;whiteSpace=wrap;html=1;fillColor=#FFFBEB;strokeColor=#D97706;strokeWidth=1.0;fontSize=10;align=center;"
UC_PLAIN = "ellipse;whiteSpace=wrap;html=1;fillColor=#FFFFFF;strokeColor=#9CA3AF;strokeWidth=1.0;fontSize=10;align=center;"
UC_DEEP = "ellipse;whiteSpace=wrap;html=1;fillColor=#FFFFFF;strokeColor=#6B7280;strokeWidth=1.0;fontSize=10;align=center;"

LEGEND_TEXT = ("图例 (UML 2.5 关系规范)<br>"
               "───── 关联 Association：参与者—用例（实线）<br>"
               "⇢⇢⇢ «include» 包含：箭头指向被包含用例<br>"
               "⇢⇢⇢ «extend» 扩展：箭头指向被扩展用例")


def add_cell(parent, cid, val='', style='', vertex=None, edge=None, source=None, target=None):
    c = ET.SubElement(parent, 'mxCell', id=str(cid), value=str(val), style=str(style))
    c.set('parent', '1')
    if vertex:
        c.set('vertex', '1')
    if edge:
        c.set('edge', '1')
    if source:
        c.set('source', str(source))
    if target:
        c.set('target', str(target))
    return c


def set_geo(cell, x, y, w, h):
    g = ET.SubElement(cell, 'mxGeometry', x=str(x), y=str(y), width=str(w), height=str(h))
    g.set('as', 'geometry')


def emit_page(root_el, page_id, page_name, title, boundary_label, boundary_geo,
              actors, use_cases, associations, dependencies, legend_geo, title_w=760):
    pg = ET.SubElement(root_el, 'diagram', id=page_id, name=page_name)
    m = ET.SubElement(pg, 'mxGraphModel', dx='1700', dy='1200', grid='1', gridSize='10',
                      pageWidth='1654', pageHeight='1169', pageScale='1')
    r = ET.SubElement(m, 'root')
    ET.SubElement(r, 'mxCell', id='0')
    ET.SubElement(r, 'mxCell', id='1', parent='0')

    t = add_cell(r, f"{page_id}_title", title, TITLE_STYLE, vertex=True)
    set_geo(t, 40, 20, title_w, 35)

    b = add_cell(r, f"{page_id}_bnd", boundary_label, BND_STYLE, vertex=True)
    set_geo(b, *boundary_geo)

    for a in actors:
        c = add_cell(r, a['id'], a['label'], ACTOR_STYLE, vertex=True)
        set_geo(c, a['x'], a['y'], ACT_W, ACT_H)

    for u in use_cases:
        c = add_cell(r, u['id'], u['text'], u['style'], vertex=True)
        set_geo(c, u['x'], u['y'], UC_W, UC_H)

    for a_id, u_id in associations:
        add_cell(r, f"{page_id}_a_{a_id}_{u_id}", '', ASSOC_STYLE, edge=True, source=a_id, target=u_id)

    for f_id, t_id, lbl in dependencies:
        stl = DEP_RV_STYLE if lbl == '«extend»' else DEP_STYLE
        add_cell(r, f"{page_id}_d_{f_id}_{t_id}", lbl, stl, edge=True, source=f_id, target=t_id)

    lg = add_cell(r, f"{page_id}_legend", LEGEND_TEXT, LEGEND_STYLE, vertex=True)
    set_geo(lg, *legend_geo)


def build_rows(page_prefix, rows, styles_rule=None):
    """rows: [[(text, style_key), ...], ...] -> 返回 use_cases 列表 + 行坐标表"""
    use_cases = []
    centers = {}
    for r, row in enumerate(rows):
        cy = TOP_Y + r * ROW_STEP
        row_centers = []
        for c, item in enumerate(row):
            text, style = item
            uid = f"{page_prefix}_u{r}_{c}"
            use_cases.append(dict(id=uid, text=text, style=style, x=COL_X[c], y=cy))
            row_centers.append(uid)
        centers[r] = row_centers
    return use_cases, centers


def chain_deps(page_prefix, centers, n_rows):
    deps = []
    for r in range(n_rows):
        ids = centers[r]
        for c in range(len(ids) - 1):
            deps.append((ids[c], ids[c + 1], '«include»'))
    return deps


def build_full_uml25_usecases_drawio():
    root_el = ET.Element('mxfile', host='app.diagrams.net', version='22.0.0', type='device')

    R4 = [UC_PRIMARY, UC_PLAIN, UC_WARN, UC_ACCENT]
    R4B = [UC_PRIMARY, UC_PLAIN, UC_PLAIN, UC_DEEP]

    # =====================================================================
    # Page 1: 控排企业用例图
    # =====================================================================
    p2_rows = [
        [("碳核算模板填报", UC_PRIMARY), ("录入生产活动台账", UC_PLAIN), ("国标公式实时推导", UC_WARN), ("提交核算报告送审", UC_ACCENT)],
        [("报告数字签名", UC_PRIMARY), ("私钥信封解密", UC_PLAIN), ("上传原始化验凭证", UC_PLAIN), ("驳回整改补正", UC_WARN)],
        [("双向拍卖挂单", UC_PRIMARY), ("冻结碳币配额资产", UC_WARN), ("连续撮合出清", UC_PLAIN), ("DVP 原子清算交割", UC_ACCENT)],
        [("碳币账户管理", UC_PRIMARY), ("发起碳币转账", UC_PLAIN), ("碳信用履约评分查询", UC_PLAIN), ("奖惩事件明细查阅", UC_DEEP)],
    ]
    ucs, centers = build_rows('p2', p2_rows)
    emit_page(root_el, 'page2_enterprise', '控排企业用例图',
              "控排企业用例图 (ENTERPRISE USE CASE DIAGRAM)",
              "OAISS CHAIN 控排企业核心业务系统边界 (Enterprise Boundary)",
              (250, 75, 940, 380),
              [dict(id='p2_act_e', label="«Business Actor»&#xa;控排企业&#xa;(ENTERPRISE)", x=90, y=TOP_Y + 3 * ROW_STEP - 18)],
              ucs,
              [('p2_act_e', centers[r][0]) for r in range(4)],
              chain_deps('p2', centers, 4),
              legend_geo=(1100, 120, 400, 90))

    # =====================================================================
    # Page 2: 数据审核员用例图
    # =====================================================================
    p3_rows = [
        [("登录审核工作台", UC_PRIMARY), ("图形验证码校验", UC_PLAIN), ("JWT 双令牌签发", UC_PLAIN), ("加载待办统计看板", UC_DEEP)],
        [("认领待审报告", UC_PRIMARY), ("锁定报告编辑权限", UC_WARN), ("AI 预审结论查阅", UC_PLAIN), ("查验工艺参数凭证", UC_PLAIN)],
        [("企业签名 RSA 验签", UC_PRIMARY), ("调取企业登记公钥", UC_PLAIN), ("篡改检测与告警", UC_WARN), ("签署审核通过决定", UC_ACCENT)],
        [("结构化驳回出具", UC_PRIMARY), ("驳回类别与意见填写", UC_PLAIN), ("异步发送整改通知", UC_PLAIN), ("审核历史与通过率", UC_DEEP)],
    ]
    ucs, centers = build_rows('p3', p3_rows)
    deps = chain_deps('p3', centers, 4)
    deps.append((centers[3][0], centers[2][3], '«extend»'))  # 结构化驳回 → 签署审核通过决定
    emit_page(root_el, 'page3_reviewer', '数据审核员用例图',
              "数据审核员用例图 (REVIEWER USE CASE DIAGRAM)",
              "OAISS CHAIN 碳报告审核与公信核验系统边界 (Audit Boundary)",
              (250, 75, 940, 380),
              [dict(id='p3_act_r', label="«Business Actor»&#xa;数据审核员&#xa;(REVIEWER)", x=90, y=TOP_Y + 3 * ROW_STEP - 18)],
              ucs,
              [('p3_act_r', centers[r][0]) for r in range(4)],
              deps,
              legend_geo=(1100, 120, 400, 90))

    # =====================================================================
    # Page 3: 第三方监管机构用例图
    # =====================================================================
    p4_rows = [
        [("双碳监管大屏总览", UC_PRIMARY), ("区域排放态势渲染", UC_PLAIN), ("零匹配筛选空态", UC_PLAIN), ("多角色数据口径隔离", UC_DEEP)],
        [("链上凭证穿透核验", UC_PRIMARY), ("TxHash 与区块核验", UC_PLAIN), ("回读存证原文比对", UC_PLAIN), ("异常预警与清单导出", UC_WARN)],
        [("存证穿透比对与公信核验", UC_PRIMARY), ("Fabric 账本只读查询", UC_PLAIN), ("背书节点签名查验", UC_PLAIN), ("存证指纹公信核验", UC_PLAIN)],
        [("企业履约异常监控", UC_PRIMARY), ("异常敞口分层预警", UC_WARN), ("批量链路复核抽样", UC_PLAIN), ("异常清单导出留痕", UC_DEEP)],
    ]
    ucs, centers = build_rows('p4', p4_rows)
    emit_page(root_el, 'page4_thirdparty', '第三方监管机构用例图',
              "第三方监管机构用例图 (THIRD_PARTY REGULATOR USE CASE DIAGRAM)",
              "OAISS CHAIN 宏观监管与联盟链存证系统边界 (Supervision Boundary)",
              (250, 75, 940, 380),
              [dict(id='p4_act_tp', label="«Regulatory Actor»&#xa;第三方监管机构&#xa;(THIRD_PARTY)", x=90, y=TOP_Y + 3 * ROW_STEP - 18)],
              ucs,
              [('p4_act_tp', centers[r][0]) for r in range(4)],
              chain_deps('p4', centers, 4),
              legend_geo=(1100, 120, 400, 90))

    # =====================================================================
    # Page 4: 系统管理员用例图
    # =====================================================================
    p5_rows = [
        [("企业准入审批", UC_PRIMARY), ("材料完整性核验", UC_PLAIN), ("资质编号唯一性核验", UC_PLAIN), ("准入状态置位生效", UC_DEEP)],
        [("碳管理证书签发", UC_PRIMARY), ("X.509 根证书签发", UC_ACCENT), ("证书指纹公示存证", UC_PLAIN), ("证书吊销处理", UC_WARN)],
        [("核查员资质审核", UC_PRIMARY), ("执业证书真伪核验", UC_PLAIN), ("执业范围与辖区绑定", UC_PLAIN), ("资质状态置位生效", UC_DEEP)],
        [("平台账号治理", UC_PRIMARY), ("账号禁用与会话下线", UC_WARN), ("敏感操作留痕检索", UC_PLAIN), ("审计报表导出归档", UC_DEEP)],
    ]
    ucs, centers = build_rows('p5', p5_rows)
    emit_page(root_el, 'page5_admin', '系统管理员用例图',
              "系统管理员用例图 (SYSTEM ADMIN USE CASE DIAGRAM)",
              "OAISS CHAIN 平台准入治理与系统管理系统边界 (Admin Boundary)",
              (250, 75, 940, 380),
              [dict(id='p5_act_ad', label="«Platform Admin»&#xa;系统管理员&#xa;(ADMIN)", x=90, y=TOP_Y + 3 * ROW_STEP - 18)],
              ucs,
              [('p5_act_ad', centers[r][0]) for r in range(4)],
              chain_deps('p5', centers, 4),
              legend_geo=(1100, 120, 400, 90))

    # =====================================================================
    # Page 0 (首位): 多角色核心用例全景图 (2×2 象限)
    # =====================================================================
    pg = ET.SubElement(root_el, 'diagram', id='page1_panoramic', name='多角色核心用例全景图')
    m = ET.SubElement(pg, 'mxGraphModel', dx='1900', dy='1500', grid='1', gridSize='10',
                      pageWidth='1654', pageHeight='1169', pageScale='1')
    r = ET.SubElement(m, 'root')
    ET.SubElement(r, 'mxCell', id='0')
    ET.SubElement(r, 'mxCell', id='1', parent='0')

    t = add_cell(r, 'p1_title', "OAISS CHAIN 多角色核心用例全景图 (Multi-Role Use Case Panorama · UML 2.5)",
                 TITLE_STYLE, vertex=True)
    set_geo(t, 40, 20, 900, 35)

    quads = [
        dict(prefix='p1q1', title="控排企业 (ENTERPRISE)", ox=80, oy=100, actor="控排企业\n(ENTERPRISE)",
             rows=p2_rows),
        dict(prefix='p1q2', title="数据审核员 (REVIEWER)", ox=880, oy=100, actor="数据审核员\n(REVIEWER)",
             rows=p3_rows),
        dict(prefix='p1q3', title="第三方监管机构 (THIRD_PARTY)", ox=80, oy=820, actor="第三方监管机构\n(THIRD_PARTY)",
             rows=p4_rows),
        dict(prefix='p1q4', title="系统管理员 (ADMIN)", ox=880, oy=820, actor="系统管理员\n(ADMIN)",
             rows=p5_rows),
    ]

    QUAD_UC_W, QUAD_UC_H = 120, 28
    qcol = [200, 345, 490, 635]
    qstep = 62
    qtop = 170
    all_ucs = []
    for q in quads:
        ox, oy = q['ox'], q['oy']
        tt = add_cell(r, f"{q['prefix']}_title", q['title'],
                      "text;html=1;strokeColor=none;fillColor=none;align=left;verticalAlign=middle;rounded=0;fontStyle=1;fontSize=12;fontColor=#374151;",
                      vertex=True)
        set_geo(tt, ox, oy - 35, 380, 26)

        bnd = add_cell(r, f"{q['prefix']}_bnd", "", 
                       "rounded=0;whiteSpace=wrap;html=1;fillColor=#FAFAFA;strokeColor=#D1D5DB;strokeWidth=1.0;",
                       vertex=True)
        set_geo(bnd, ox + 90, oy, 730, 660)

        act = add_cell(r, f"{q['prefix']}_act", q['actor'], ACTOR_STYLE, vertex=True)
        set_geo(act, ox - 10, oy + 290, ACT_W, ACT_H)

        centers = {}
        for ri, row in enumerate(q['rows']):
            cy = oy + ri * qstep + 56
            centers[ri] = []
            for ci, (text, style) in enumerate(row):
                uid = f"{q['prefix']}_u{ri}_{ci}"
                c = add_cell(r, uid, text, style, vertex=True)
                set_geo(c, ox + qcol[ci], cy, QUAD_UC_W, QUAD_UC_H)
                centers[ri].append(uid)
        # 关联
        for ri in range(4):
            add_cell(r, f"{q['prefix']}_a{ri}", '', ASSOC_STYLE, edge=True,
                     source=f"{q['prefix']}_act", target=centers[ri][0])
        # include
        for ri in range(4):
            for ci in range(3):
                add_cell(r, f"{q['prefix']}_d{ri}_{ci}", '«include»', DEP_STYLE, edge=True,
                         source=centers[ri][ci], target=centers[ri][ci + 1])
        # extend (仅审核员象限)
        if q['prefix'] == 'p1q2':
            add_cell(r, f"{q['prefix']}_ext", '«extend»', DEP_RV_STYLE, edge=True,
                     source=centers[3][0], target=centers[2][3])

    tree = ET.ElementTree(root_el)
    ET.indent(tree, space='  ')
    tree.write(OUT_DRAWIO, encoding='utf-8', xml_declaration=True)
    print(f"成功完整生成 5 个 Tab 页的标准 UML 2.5 draw.io 工程文件: {OUT_DRAWIO}")


if __name__ == '__main__':
    build_full_uml25_usecases_drawio()
