#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
同步更新 02-system-usecases.drawio 的 Tab 5 (page1_panoramic 多角色核心用例全景图)
采用标准 UML 2.5 语法、标准比例图元与完整关系连线
"""
import xml.etree.ElementTree as ET

drawio_p = 'docs/assets/architecture/02-system-usecases.drawio'
tree = ET.parse(drawio_p)
root = tree.getroot()

# 找到 page1_panoramic
diag_pano = None
for d in root.findall('diagram'):
    if d.get('id') == 'page1_panoramic':
        diag_pano = d
        break

new_tab_xml = """<diagram id="page1_panoramic" name="多角色核心用例全景图">
    <mxGraphModel dx="1600" dy="1000" grid="1" gridSize="10" pageWidth="1654" pageHeight="1169" pageScale="1" background="#FFFFFF">
      <root>
        <mxCell id="0" />
        <mxCell id="1" parent="0" />

        <!-- EA12 Frame -->
        <mxCell id="p_frame" value="usecase [Diagram] OAISS-CHAIN [系统多角色核心用例全景图]&#xa;Enterprise Architect 12 / UML 2.5 Specification · 300 DPI" style="shape=folder;fontStyle=1;tabWidth=520;tabHeight=40;tabPosition=left;html=1;boundedLbl=1;fillColor=#FAFAFA;strokeColor=#94A3B8;fontColor=#1E293B;fontSize=13;" vertex="1" parent="1">
          <mxGeometry x="40" y="30" width="1560" height="1080" as="geometry" />
        </mxCell>

        <!-- System Boundary -->
        <mxCell id="p_bnd" value="OAISS-CHAIN 双碳链动系统 —— 核心业务系统边界" style="shape=rect;html=1;verticalAlign=top;fontStyle=1;whiteSpace=wrap;fillColor=#FFFFFF;strokeColor=#64748B;fontColor=#0F172A;fontSize=14;" vertex="1" parent="1">
          <mxGeometry x="240" y="70" width="1160" height="1000" as="geometry" />
        </mxCell>

        <!-- 4 Subsystems inside boundary -->
        <mxCell id="sub_accounting" value="碳资产核算与存证业务域" style="shape=rect;html=1;verticalAlign=top;fontStyle=1;whiteSpace=wrap;fillColor=#F8FAFC;strokeColor=#CBD5E1;fontColor=#1E40AF;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="270" y="120" width="530" height="420" as="geometry" />
        </mxCell>
        <mxCell id="sub_regulation" value="宏观态势与智能监管大盘域" style="shape=rect;html=1;verticalAlign=top;fontStyle=1;whiteSpace=wrap;fillColor=#F8FAFC;strokeColor=#CBD5E1;fontColor=#1E40AF;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="840" y="120" width="530" height="420" as="geometry" />
        </mxCell>
        <mxCell id="sub_trading" value="碳配额交易与清算交割域" style="shape=rect;html=1;verticalAlign=top;fontStyle=1;whiteSpace=wrap;fillColor=#F8FAFC;strokeColor=#CBD5E1;fontColor=#1E40AF;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="270" y="580" width="530" height="460" as="geometry" />
        </mxCell>
        <mxCell id="sub_admin" value="平台准入与安全审计治理域" style="shape=rect;html=1;verticalAlign=top;fontStyle=1;whiteSpace=wrap;fillColor=#F8FAFC;strokeColor=#CBD5E1;fontColor=#1E40AF;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="840" y="580" width="530" height="460" as="geometry" />
        </mxCell>

        <!-- 4 Actors -->
        <mxCell id="act_ent" value="控排企业&#xa;(ENTERPRISE)" style="shape=umlActor;verticalLabelPosition=bottom;verticalAlign=top;html=1;outlineConnect=0;fontStyle=1;fontColor=#0F172A;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="110" y="240" width="50" height="90" as="geometry" />
        </mxCell>
        <mxCell id="act_rev" value="数据审核员&#xa;(REVIEWER)" style="shape=umlActor;verticalLabelPosition=bottom;verticalAlign=top;html=1;outlineConnect=0;fontStyle=1;fontColor=#0F172A;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="110" y="420" width="50" height="90" as="geometry" />
        </mxCell>
        <mxCell id="act_reg" value="第三方监管机构&#xa;(THIRD_PARTY)" style="shape=umlActor;verticalLabelPosition=bottom;verticalAlign=top;html=1;outlineConnect=0;fontStyle=1;fontColor=#0F172A;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="1480" y="240" width="50" height="90" as="geometry" />
        </mxCell>
        <mxCell id="act_adm" value="系统管理员&#xa;(ADMIN)" style="shape=umlActor;verticalLabelPosition=bottom;verticalAlign=top;html=1;outlineConnect=0;fontStyle=1;fontColor=#0F172A;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="1480" y="720" width="50" height="90" as="geometry" />
        </mxCell>

        <!-- Subsystem 1 Use Cases -->
        <mxCell id="uc_fill" value="碳核算模板填报" style="ellipse;whiteSpace=wrap;html=1;fillColor=#F8FAFC;strokeColor=#64748B;fontColor=#0F172A;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="310" y="160" width="190" height="50" as="geometry" />
        </mxCell>
        <mxCell id="uc_submit" value="提交核算报告送审" style="ellipse;whiteSpace=wrap;html=1;fillColor=#F8FAFC;strokeColor=#64748B;fontColor=#0F172A;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="570" y="160" width="190" height="50" as="geometry" />
        </mxCell>
        <mxCell id="uc_sign" value="报告生成数字签名" style="ellipse;whiteSpace=wrap;html=1;fillColor=#F8FAFC;strokeColor=#64748B;fontColor=#0F172A;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="570" y="280" width="190" height="50" as="geometry" />
        </mxCell>
        <mxCell id="uc_audit" value="审核碳核算报告" style="ellipse;whiteSpace=wrap;html=1;fillColor=#F8FAFC;strokeColor=#64748B;fontColor=#0F172A;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="310" y="380" width="190" height="50" as="geometry" />
        </mxCell>
        <mxCell id="uc_verify" value="企业公钥RSA验签" style="ellipse;whiteSpace=wrap;html=1;fillColor=#F8FAFC;strokeColor=#64748B;fontColor=#0F172A;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="570" y="380" width="190" height="50" as="geometry" />
        </mxCell>
        <mxCell id="uc_reject" value="结构化驳回出具" style="ellipse;whiteSpace=wrap;html=1;fillColor=#F8FAFC;strokeColor=#64748B;fontColor=#0F172A;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="310" y="470" width="190" height="50" as="geometry" />
        </mxCell>

        <!-- Subsystem 2 Use Cases -->
        <mxCell id="uc_board" value="访问双碳监管大盘" style="ellipse;whiteSpace=wrap;html=1;fillColor=#F8FAFC;strokeColor=#64748B;fontColor=#0F172A;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="1140" y="160" width="190" height="50" as="geometry" />
        </mxCell>
        <mxCell id="uc_prophet" value="AI时序碳价预测" style="ellipse;whiteSpace=wrap;html=1;fillColor=#F8FAFC;strokeColor=#64748B;fontColor=#0F172A;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="880" y="160" width="190" height="50" as="geometry" />
        </mxCell>
        <mxCell id="uc_anomaly" value="异常排放偏离归因" style="ellipse;whiteSpace=wrap;html=1;fillColor=#F8FAFC;strokeColor=#64748B;fontColor=#0F172A;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="880" y="280" width="190" height="50" as="geometry" />
        </mxCell>
        <mxCell id="uc_certify" value="存证穿透比对与公信核验" style="ellipse;whiteSpace=wrap;html=1;fillColor=#F8FAFC;strokeColor=#64748B;fontColor=#0F172A;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="1140" y="380" width="190" height="50" as="geometry" />
        </mxCell>
        <mxCell id="uc_fabric" value="Fabric链码上链存证" style="ellipse;whiteSpace=wrap;html=1;fillColor=#F8FAFC;strokeColor=#64748B;fontColor=#0F172A;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="880" y="380" width="190" height="50" as="geometry" />
        </mxCell>

        <!-- Subsystem 3 Use Cases -->
        <mxCell id="uc_auction" value="双向拍卖限价挂单" style="ellipse;whiteSpace=wrap;html=1;fillColor=#F8FAFC;strokeColor=#64748B;fontColor=#0F172A;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="310" y="640" width="190" height="50" as="geometry" />
        </mxCell>
        <mxCell id="uc_matching" value="连续撮合出清运算" style="ellipse;whiteSpace=wrap;html=1;fillColor=#F8FAFC;strokeColor=#64748B;fontColor=#0F172A;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="570" y="640" width="190" height="50" as="geometry" />
        </mxCell>
        <mxCell id="uc_dvp" value="DVP原子清算交割" style="ellipse;whiteSpace=wrap;html=1;fillColor=#F8FAFC;strokeColor=#64748B;fontColor=#0F172A;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="570" y="750" width="190" height="50" as="geometry" />
        </mxCell>
        <mxCell id="uc_account" value="碳币资金账户管理" style="ellipse;whiteSpace=wrap;html=1;fillColor=#F8FAFC;strokeColor=#64748B;fontColor=#0F172A;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="310" y="870" width="190" height="50" as="geometry" />
        </mxCell>
        <mxCell id="uc_credit" value="碳信用履约评分评价" style="ellipse;whiteSpace=wrap;html=1;fillColor=#F8FAFC;strokeColor=#64748B;fontColor=#0F172A;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="570" y="870" width="190" height="50" as="geometry" />
        </mxCell>

        <!-- Subsystem 4 Use Cases -->
        <mxCell id="uc_admission" value="企业准入资质审查" style="ellipse;whiteSpace=wrap;html=1;fillColor=#F8FAFC;strokeColor=#64748B;fontColor=#0F172A;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="1140" y="640" width="190" height="50" as="geometry" />
        </mxCell>
        <mxCell id="uc_cert" value="签发X.509管理证书" style="ellipse;whiteSpace=wrap;html=1;fillColor=#F8FAFC;strokeColor=#64748B;fontColor=#0F172A;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="880" y="640" width="190" height="50" as="geometry" />
        </mxCell>
        <mxCell id="uc_users" value="全域账号与权限治理" style="ellipse;whiteSpace=wrap;html=1;fillColor=#F8FAFC;strokeColor=#64748B;fontColor=#0F172A;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="1140" y="760" width="190" height="50" as="geometry" />
        </mxCell>
        <mxCell id="uc_log" value="敏感审计日志穿透" style="ellipse;whiteSpace=wrap;html=1;fillColor=#F8FAFC;strokeColor=#64748B;fontColor=#0F172A;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="880" y="880" width="190" height="50" as="geometry" />
        </mxCell>
        <mxCell id="uc_ops" value="容器健康态势巡检" style="ellipse;whiteSpace=wrap;html=1;fillColor=#F8FAFC;strokeColor=#64748B;fontColor=#0F172A;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="1140" y="880" width="190" height="50" as="geometry" />
        </mxCell>

        <!-- Associations (Solid Lines, No Arrows) -->
        <mxCell id="e_act_fill" style="endArrow=none;html=1;strokeColor=#334155;strokeWidth=1.2;" edge="1" parent="1" source="act_ent" target="uc_fill"><mxGeometry relative="1" as="geometry" /></mxCell>
        <mxCell id="e_act_auc" style="endArrow=none;html=1;strokeColor=#334155;strokeWidth=1.2;" edge="1" parent="1" source="act_ent" target="uc_auction"><mxGeometry relative="1" as="geometry" /></mxCell>
        <mxCell id="e_act_acc" style="endArrow=none;html=1;strokeColor=#334155;strokeWidth=1.2;" edge="1" parent="1" source="act_ent" target="uc_account"><mxGeometry relative="1" as="geometry" /></mxCell>

        <mxCell id="e_act_audit" style="endArrow=none;html=1;strokeColor=#334155;strokeWidth=1.2;" edge="1" parent="1" source="act_rev" target="uc_audit"><mxGeometry relative="1" as="geometry" /></mxCell>
        <mxCell id="e_act_rej" style="endArrow=none;html=1;strokeColor=#334155;strokeWidth=1.2;" edge="1" parent="1" source="act_rev" target="uc_reject"><mxGeometry relative="1" as="geometry" /></mxCell>

        <mxCell id="e_act_board" style="endArrow=none;html=1;strokeColor=#334155;strokeWidth=1.2;" edge="1" parent="1" source="act_reg" target="uc_board"><mxGeometry relative="1" as="geometry" /></mxCell>
        <mxCell id="e_act_certify" style="endArrow=none;html=1;strokeColor=#334155;strokeWidth=1.2;" edge="1" parent="1" source="act_reg" target="uc_certify"><mxGeometry relative="1" as="geometry" /></mxCell>

        <mxCell id="e_act_adm_adm" style="endArrow=none;html=1;strokeColor=#334155;strokeWidth=1.2;" edge="1" parent="1" source="act_adm" target="uc_admission"><mxGeometry relative="1" as="geometry" /></mxCell>
        <mxCell id="e_act_adm_usr" style="endArrow=none;html=1;strokeColor=#334155;strokeWidth=1.2;" edge="1" parent="1" source="act_adm" target="uc_users"><mxGeometry relative="1" as="geometry" /></mxCell>
        <mxCell id="e_act_adm_ops" style="endArrow=none;html=1;strokeColor=#334155;strokeWidth=1.2;" edge="1" parent="1" source="act_adm" target="uc_ops"><mxGeometry relative="1" as="geometry" /></mxCell>

        <!-- Dependencies («include» / «extend») -->
        <mxCell id="e_fill_submit" value="«include»" style="endArrow=open;endSize=8;dashed=1;html=1;strokeColor=#475569;strokeWidth=1.1;fontSize=10;fontColor=#334155;" edge="1" parent="1" source="uc_fill" target="uc_submit"><mxGeometry relative="1" as="geometry" /></mxCell>
        <mxCell id="e_sub_sign" value="«include»" style="endArrow=open;endSize=8;dashed=1;html=1;strokeColor=#475569;strokeWidth=1.1;fontSize=10;fontColor=#334155;" edge="1" parent="1" source="uc_submit" target="uc_sign"><mxGeometry relative="1" as="geometry" /></mxCell>
        <mxCell id="e_aud_ver" value="«include»" style="endArrow=open;endSize=8;dashed=1;html=1;strokeColor=#475569;strokeWidth=1.1;fontSize=10;fontColor=#334155;" edge="1" parent="1" source="uc_audit" target="uc_verify"><mxGeometry relative="1" as="geometry" /></mxCell>
        <mxCell id="e_rej_aud" value="«extend»" style="endArrow=open;endSize=8;dashed=1;html=1;strokeColor=#475569;strokeWidth=1.1;fontSize=10;fontColor=#334155;" edge="1" parent="1" source="uc_reject" target="uc_audit"><mxGeometry relative="1" as="geometry" /></mxCell>

        <mxCell id="e_board_pro" value="«include»" style="endArrow=open;endSize=8;dashed=1;html=1;strokeColor=#475569;strokeWidth=1.1;fontSize=10;fontColor=#334155;" edge="1" parent="1" source="uc_board" target="uc_prophet"><mxGeometry relative="1" as="geometry" /></mxCell>
        <mxCell id="e_board_anom" value="«include»" style="endArrow=open;endSize=8;dashed=1;html=1;strokeColor=#475569;strokeWidth=1.1;fontSize=10;fontColor=#334155;" edge="1" parent="1" source="uc_board" target="uc_anomaly"><mxGeometry relative="1" as="geometry" /></mxCell>
        <mxCell id="e_cer_fab" value="«只读核验»" style="endArrow=open;endSize=8;dashed=1;html=1;strokeColor=#475569;strokeWidth=1.1;fontSize=10;fontColor=#334155;" edge="1" parent="1" source="uc_certify" target="uc_fabric"><mxGeometry relative="1" as="geometry" /></mxCell>

        <mxCell id="e_auc_mat" value="«include»" style="endArrow=open;endSize=8;dashed=1;html=1;strokeColor=#475569;strokeWidth=1.1;fontSize=10;fontColor=#334155;" edge="1" parent="1" source="uc_auction" target="uc_matching"><mxGeometry relative="1" as="geometry" /></mxCell>
        <mxCell id="e_mat_dvp" value="«include»" style="endArrow=open;endSize=8;dashed=1;html=1;strokeColor=#475569;strokeWidth=1.1;fontSize=10;fontColor=#334155;" edge="1" parent="1" source="uc_matching" target="uc_dvp"><mxGeometry relative="1" as="geometry" /></mxCell>
        <mxCell id="e_acc_crd" value="«include»" style="endArrow=open;endSize=8;dashed=1;html=1;strokeColor=#475569;strokeWidth=1.1;fontSize=10;fontColor=#334155;" edge="1" parent="1" source="uc_account" target="uc_credit"><mxGeometry relative="1" as="geometry" /></mxCell>

        <mxCell id="e_adm_cert" value="«include»" style="endArrow=open;endSize=8;dashed=1;html=1;strokeColor=#475569;strokeWidth=1.1;fontSize=10;fontColor=#334155;" edge="1" parent="1" source="uc_admission" target="uc_cert"><mxGeometry relative="1" as="geometry" /></mxCell>
        <mxCell id="e_usr_log" value="«include»" style="endArrow=open;endSize=8;dashed=1;html=1;strokeColor=#475569;strokeWidth=1.1;fontSize=10;fontColor=#334155;" edge="1" parent="1" source="uc_users" target="uc_log"><mxGeometry relative="1" as="geometry" /></mxCell>

        <!-- Cross-domain collaboration flow -->
        <mxCell id="e_flow_verify_fabric" value="«通过流转»" style="endArrow=open;endSize=8;dashed=1;html=1;strokeColor=#0284C7;strokeWidth=1.4;fontSize=10;fontColor=#0284C7;fontStyle=1;" edge="1" parent="1" source="uc_verify" target="uc_fabric"><mxGeometry relative="1" as="geometry" /></mxCell>
        <mxCell id="e_flow_cert_sign" value="«准入授信»" style="endArrow=open;endSize=8;dashed=1;html=1;strokeColor=#16A34A;strokeWidth=1.4;fontSize=10;fontColor=#16A34A;fontStyle=1;" edge="1" parent="1" source="uc_cert" target="uc_sign"><mxGeometry relative="1" as="geometry" /></mxCell>

      </root>
    </mxGraphModel>
  </diagram>"""

# 替换 diagram 内容
idx = -1
for i, child in enumerate(root):
    if child.get('id') == 'page1_panoramic':
        idx = i
        break

if idx >= 0:
    root.remove(root[idx])
    new_diag_elem = ET.fromstring(new_tab_xml)
    root.append(new_diag_elem)
    tree.write(drawio_p, encoding='utf-8', xml_declaration=True)
    print("02-system-usecases.drawio Tab 5 已成功更新为全景原型规范图！")
else:
    print("未找到 page1_panoramic")
