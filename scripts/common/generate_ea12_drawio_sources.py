#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成标准 EA12 / draw.io 工程源文件:
1. docs/assets/architecture/01-system-hexagonal-architecture.drawio
2. docs/assets/architecture/04-database-entity-er-diagram.drawio
"""
import os
import xml.etree.ElementTree as ET

OUTPUT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '../../docs/assets/architecture'))

def create_architecture_drawio():
    xml_content = """<mxfile host="Electron" modified="2026-10-13T06:00:00.000Z" agent="Mozilla/5.0" version="21.0.0" type="device">
  <diagram id="diagram_arch" name="系统总体分层与六边形微服务拓扑架构图">
    <mxGraphModel dx="1600" dy="1000" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="1654" pageHeight="1169" background="#FFFFFF">
      <root>
        <mxCell id="0" />
        <mxCell id="1" parent="0" />
        
        <!-- Frame -->
        <mxCell id="frame_title" value="package [Model] OAISS-CHAIN [系统总体分层与六边形微服务拓扑架构图]&#xa;Enterprise Architect 12 / UML 2.5 Specification" style="shape=folder;fontStyle=1;tabWidth=520;tabHeight=40;tabPosition=left;html=1;boundedLbl=1;fillColor=#FAFAFA;strokeColor=#94A3B8;fontColor=#1E293B;fontSize=13;" vertex="1" parent="1">
          <mxGeometry x="40" y="40" width="1560" height="960" as="geometry" />
        </mxCell>

        <!-- 1. 表现接入层 -->
        <mxCell id="pkg_presentation" value="«layer» 表现接入层" style="shape=folder;fontStyle=1;tabWidth=160;tabHeight=25;tabPosition=left;html=1;boundedLbl=1;fillColor=#F8FAFC;strokeColor=#64748B;fontColor=#334155;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="80" y="110" width="460" height="190" as="geometry" />
        </mxCell>
        <mxCell id="cmp_vue" value="«presentation»&#xa;&lt;b&gt;Vue 3.5 SPA 前端&lt;/b&gt;&#xa;---------------------&#xa;• 四角色独立工作台路由&#xa;• Pinia 3 响应式状态中心&#xa;• ECharts 6.0 金融大盘&#xa;• i18n 1430词条国际化" style="shape=module;align=center;verticalAlign=top;html=1;fillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="100" y="150" width="200" height="135" as="geometry" />
        </mxCell>
        <mxCell id="cmp_nginx" value="«edge gateway»&#xa;&lt;b&gt;Nginx 1.25 网关&lt;/b&gt;&#xa;---------------------&#xa;• 静态资产分发托管&#xa;• SSL / TLS 终结卸载&#xa;• URL 动态反向代理分流&#xa;• 跨域与网络防护过滤" style="shape=module;align=center;verticalAlign=top;html=1;fillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="320" y="150" width="200" height="135" as="geometry" />
        </mxCell>

        <!-- 2. 算法智能中台 -->
        <mxCell id="pkg_ai" value="«subsystem» 算法智能中台" style="shape=folder;fontStyle=1;tabWidth=180;tabHeight=25;tabPosition=left;html=1;boundedLbl=1;fillColor=#F8FAFC;strokeColor=#64748B;fontColor=#334155;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="1080" y="110" width="480" height="190" as="geometry" />
        </mxCell>
        <mxCell id="cmp_fastapi" value="«ai engine»&#xa;&lt;b&gt;FastAPI 算法微服务 (Python 3.11)&lt;/b&gt;&#xa;--------------------------------------------&#xa;• Prophet 时间序列模型: 碳价 30/90/180 天预测与 95% 置信通道&#xa;• XGBoost 特征重要度归因引擎: 宏观能源与履约因子 XAI 驱动解析&#xa;• Isolation Forest 无监督异常排放模式检测与合规偏离雷达图&#xa;• Qwen 2.5 7B 大模型异步推理引擎: 智能合规诊断建议自动生成" style="shape=module;align=center;verticalAlign=top;html=1;fillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="1100" y="150" width="440" height="135" as="geometry" />
        </mxCell>

        <!-- 3. 核心业务中台 -->
        <mxCell id="pkg_core" value="«hexagonal subsystem» 核心业务中台 (Spring Boot 3.2.12)" style="shape=folder;fontStyle=1;tabWidth=360;tabHeight=28;tabPosition=left;html=1;boundedLbl=1;fillColor=#FAFAFA;strokeColor=#64748B;fontColor=#334155;fontSize=12;" vertex="1" parent="1">
          <mxGeometry x="80" y="340" width="1480" height="380" as="geometry" />
        </mxCell>

        <!-- 3.1 入站适配器 -->
        <mxCell id="pkg_inbound" value="«inbound adapters» 入站端口适配器" style="shape=folder;fontStyle=1;tabWidth=220;tabHeight=22;tabPosition=left;html=1;boundedLbl=1;fillColor=#FFFFFF;strokeColor=#94A3B8;fontColor=#334155;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="110" y="380" width="360" height="320" as="geometry" />
        </mxCell>
        <mxCell id="cmp_rest" value="«inbound adapter»&#xa;&lt;b&gt;RESTful API 控制器群&lt;/b&gt;&#xa;---------------------------&#xa;• 22 个 REST 控制器 (143 个端点)&#xa;• JSON 数据契约校验 (@Valid)&#xa;• 统一响应与异常包装 (R&lt;T&gt;)" style="shape=module;align=center;verticalAlign=top;html=1;fillColor=#F8FAFC;strokeColor=#475569;fontColor=#0F172A;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="130" y="420" width="320" height="120" as="geometry" />
        </mxCell>
        <mxCell id="cmp_sec" value="«inbound adapter»&#xa;&lt;b&gt;安全鉴权与 AOP 切面&lt;/b&gt;&#xa;---------------------------&#xa;• Spring Security + JWT 双令牌&#xa;• @AuditLog 敏感操作自动留痕&#xa;• RateLimit IP/用户双轨防刷" style="shape=module;align=center;verticalAlign=top;html=1;fillColor=#F8FAFC;strokeColor=#475569;fontColor=#0F172A;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="130" y="560" width="320" height="120" as="geometry" />
        </mxCell>

        <!-- 3.2 领域核心 -->
        <mxCell id="pkg_domain" value="«domain core» 业务领域内核与应用服务" style="shape=folder;fontStyle=1;tabWidth=250;tabHeight=22;tabPosition=left;html=1;boundedLbl=1;fillColor=#FFFFFF;strokeColor=#0284C7;fontColor=#0284C7;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="500" y="380" width="630" height="320" as="geometry" />
        </mxCell>
        <mxCell id="cmp_domain1" value="«domain service»&#xa;&lt;b&gt;核心领域计算与交易引擎&lt;/b&gt;&#xa;----------------------------------------------------&#xa;• 碳核算推导引擎: 发电25参、电网SF6排放核算与反推&#xa;• 连续双向拍卖撮合引擎: 价格/时间优先、微批次出清、原子交割&#xa;• 密码学签名验签中心: RSA-2048 签名/验签、AES-GCM 信封解密&#xa;• 碳币资产总账管理: 账户隔离、资金流水记账、双花防御" style="shape=module;align=center;verticalAlign=top;html=1;fillColor=#F0F9FF;strokeColor=#0284C7;fontColor=#0F172A;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="520" y="420" width="590" height="120" as="geometry" />
        </mxCell>
        <mxCell id="cmp_domain2" value="«domain service»&#xa;&lt;b&gt;信用评价与合规监管服务&lt;/b&gt;&#xa;----------------------------------------------------&#xa;• 碳信用履约评价体系: 基础信用分维护、违规扣分、奖惩轨迹存证&#xa;• 企业准入与证书签发: 资质审查流转、X.509 数字证书生命周期管线&#xa;• 监控与大屏态势引擎: 区域碳排放宏观聚类、历史审核统计、预警推送" style="shape=module;align=center;verticalAlign=top;html=1;fillColor=#F0F9FF;strokeColor=#0284C7;fontColor=#0F172A;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="520" y="560" width="590" height="120" as="geometry" />
        </mxCell>

        <!-- 3.3 出站适配器 -->
        <mxCell id="pkg_outbound" value="«outbound adapters» 出站端口适配器" style="shape=folder;fontStyle=1;tabWidth=230;tabHeight=22;tabPosition=left;html=1;boundedLbl=1;fillColor=#FFFFFF;strokeColor=#94A3B8;fontColor=#334155;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="1160" y="380" width="370" height="320" as="geometry" />
        </mxCell>
        <mxCell id="cmp_out1" value="«outbound adapter»&#xa;&lt;b&gt;区块链与事件发布适配器&lt;/b&gt;&#xa;---------------------------&#xa;• Fabric Gateway 客户端 (gRPC)&#xa;• Outbox 事务外发箱持久发布器&#xa;• 异步存证指纹回填监听器" style="shape=module;align=center;verticalAlign=top;html=1;fillColor=#F8FAFC;strokeColor=#475569;fontColor=#0F172A;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="1180" y="420" width="330" height="120" as="geometry" />
        </mxCell>
        <mxCell id="cmp_out2" value="«outbound adapter»&#xa;&lt;b&gt;数据存储与 RPC 适配器&lt;/b&gt;&#xa;---------------------------&#xa;• Spring Data JPA 持久化仓储&#xa;• Redisson 缓存与分布式互斥锁&#xa;• WebClient 异步非阻塞 HTTP 客户端" style="shape=module;align=center;verticalAlign=top;html=1;fillColor=#F8FAFC;strokeColor=#475569;fontColor=#0F172A;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="1180" y="560" width="330" height="120" as="geometry" />
        </mxCell>

        <!-- 4. 区块链存证层 -->
        <mxCell id="pkg_fabric" value="«subsystem» 可信区块链存证层" style="shape=folder;fontStyle=1;tabWidth=210;tabHeight=25;tabPosition=left;html=1;boundedLbl=1;fillColor=#F8FAFC;strokeColor=#64748B;fontColor=#334155;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="80" y="750" width="710" height="210" as="geometry" />
        </mxCell>
        <mxCell id="cmp_fabric_net" value="«blockchain network»&#xa;&lt;b&gt;Hyperledger Fabric 2.5 联盟链网络&lt;/b&gt;&#xa;-------------------------------------------------------------&#xa;• 组织 Peer 节点群: EnterprisePeer / ReviewerPeer / AdminPeer / RegulatorPeer&#xa;• Raft 共识排序集群: Orderer 节点池 (高效共识出块、强防分叉机制)&#xa;• Go 智能合约链码: 碳核算指纹不可篡改存证、交易结算凭据存证、TxHash 穿透验证" style="shape=module;align=center;verticalAlign=top;html=1;fillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="100" y="790" width="670" height="150" as="geometry" />
        </mxCell>

        <!-- 5. 基础设施层 -->
        <mxCell id="pkg_infra" value="«layer» 基础设施与存储集群" style="shape=folder;fontStyle=1;tabWidth=200;tabHeight=25;tabPosition=left;html=1;boundedLbl=1;fillColor=#F8FAFC;strokeColor=#64748B;fontColor=#334155;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="840" y="750" width="720" height="210" as="geometry" />
        </mxCell>
        <mxCell id="cmp_infra_storage" value="«storage cluster»&#xa;&lt;b&gt;数据存储与对象池 (Persistence Cluster)&lt;/b&gt;&#xa;-------------------------------------------------------------&#xa;• MySQL 8.0: 关系型主库 (24 张核心业务实体表，Flyway V1~V13 版本化迁移)&#xa;• Redis 7.0: 内存缓存集群 (行情缓存、防刷令牌桶计数、分布式互斥锁)&#xa;• MinIO S3: 兼容对象存储 (原始化验单凭据扫描件、PDF 报告附件、审计留痕凭单)" style="shape=module;align=center;verticalAlign=top;html=1;fillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="860" y="790" width="680" height="150" as="geometry" />
        </mxCell>

      </root>
    </mxGraphModel>
  </diagram>
</mxfile>"""
    out_p = os.path.join(OUTPUT_DIR, '01-system-hexagonal-architecture.drawio')
    with open(out_p, 'w', encoding='utf-8') as f:
        f.write(xml_content)
    print(f"Generated draw.io source: {out_p}")

def create_database_er_drawio():
    xml_content = """<mxfile host="Electron" modified="2026-10-13T06:00:00.000Z" agent="Mozilla/5.0" version="21.0.0" type="device">
  <diagram id="diagram_er" name="数据库核心业务实体 ER 逻辑关系模型图">
    <mxGraphModel dx="1600" dy="1000" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="1654" pageHeight="1169" background="#FFFFFF">
      <root>
        <mxCell id="0" />
        <mxCell id="1" parent="0" />
        
        <!-- Frame -->
        <mxCell id="frame_title" value="package [DataModel] OAISS-CHAIN [数据库 24 张核心实体 ER 逻辑关系模型图]&#xa;EA12 Data Modeling Profile · IE Crow's Foot Notation · Flyway V1~V13" style="shape=folder;fontStyle=1;tabWidth=580;tabHeight=40;tabPosition=left;html=1;boundedLbl=1;fillColor=#FAFAFA;strokeColor=#94A3B8;fontColor=#1E293B;fontSize=13;" vertex="1" parent="1">
          <mxGeometry x="40" y="40" width="1560" height="1060" as="geometry" />
        </mxCell>

        <!-- 域 1: 身份权限域 -->
        <mxCell id="dom_auth" value="【身份权限与审计域】" style="shape=folder;fontStyle=1;tabWidth=160;tabHeight=25;tabPosition=left;html=1;boundedLbl=1;fillColor=#F8FAFC;strokeColor=#94A3B8;fontColor=#334155;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="80" y="100" width="460" height="420" as="geometry" />
        </mxCell>
        <mxCell id="tbl_user" value="«table»&#xa;&lt;b&gt;user (用户主表)&lt;/b&gt;&#xa;----------------------------&#xa;+ id : bigint [PK]&#xa;----------------------------&#xa;+ username : varchar(64)&#xa;+ password : varchar(128)&#xa;+ real_name : varchar(64)&#xa;+ status : int&#xa;+ email : varchar(128)&#xa;+ phone : varchar(32)" style="shape=table;html=1;whiteSpace=wrap;fillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="100" y="140" width="200" height="180" as="geometry" />
        </mxCell>
        <mxCell id="tbl_user_type" value="«table»&#xa;&lt;b&gt;user_type_list (角色关联)&lt;/b&gt;&#xa;----------------------------&#xa;+ id : bigint [PK]&#xa;----------------------------&#xa;+ user_id : bigint [FK]&#xa;+ user_type_id : int&#xa;+ assigned_at : datetime" style="shape=table;html=1;whiteSpace=wrap;fillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="320" y="140" width="200" height="130" as="geometry" />
        </mxCell>
        <mxCell id="tbl_perm" value="«table»&#xa;&lt;b&gt;entry_permission (权限字典)&lt;/b&gt;&#xa;----------------------------&#xa;+ id : bigint [PK]&#xa;----------------------------&#xa;+ permission_name : varchar&#xa;+ permission_code : varchar&#xa;+ path : varchar(128)" style="shape=table;html=1;whiteSpace=wrap;fillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="320" y="340" width="200" height="140" as="geometry" />
        </mxCell>
        <mxCell id="tbl_account_perm" value="«table»&#xa;&lt;b&gt;account_permission_list&lt;/b&gt;&#xa;----------------------------&#xa;+ id : bigint [PK]&#xa;----------------------------&#xa;+ user_id : bigint [FK]&#xa;+ permission_id : bigint [FK]&#xa;+ granted_at : datetime" style="shape=table;html=1;whiteSpace=wrap;fillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="100" y="340" width="200" height="140" as="geometry" />
        </mxCell>

        <!-- 域 2: 企业主体与资质域 -->
        <mxCell id="dom_ent" value="【企业主体与资质证书域】" style="shape=folder;fontStyle=1;tabWidth=180;tabHeight=25;tabPosition=left;html=1;boundedLbl=1;fillColor=#F8FAFC;strokeColor=#94A3B8;fontColor=#334155;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="580" y="100" width="460" height="420" as="geometry" />
        </mxCell>
        <mxCell id="tbl_enterprise" value="«table»&#xa;&lt;b&gt;enterprise (企业主体)&lt;/b&gt;&#xa;----------------------------&#xa;+ id : bigint [PK]&#xa;----------------------------&#xa;+ user_id : bigint [FK]&#xa;+ enterprise_name : varchar&#xa;+ credit_code : varchar(32)&#xa;+ industry_type : varchar&#xa;+ legal_person : varchar" style="shape=table;html=1;whiteSpace=wrap;fillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="600" y="140" width="200" height="180" as="geometry" />
        </mxCell>
        <mxCell id="tbl_cert" value="«table»&#xa;&lt;b&gt;certificate (数字证书)&lt;/b&gt;&#xa;----------------------------&#xa;+ id : bigint [PK]&#xa;----------------------------&#xa;+ enterprise_id : bigint [FK]&#xa;+ cert_sn : varchar(64)&#xa;+ public_key_fp : varchar&#xa;+ valid_until : datetime" style="shape=table;html=1;whiteSpace=wrap;fillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="820" y="140" width="200" height="140" as="geometry" />
        </mxCell>
        <mxCell id="tbl_admission" value="«table»&#xa;&lt;b&gt;enterprise_admission (准入)&lt;/b&gt;&#xa;----------------------------&#xa;+ id : bigint [PK]&#xa;----------------------------&#xa;+ enterprise_id : bigint [FK]&#xa;+ audit_status : int&#xa;+ reason : text&#xa;+ audited_at : datetime" style="shape=table;html=1;whiteSpace=wrap;fillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="820" y="340" width="200" height="140" as="geometry" />
        </mxCell>
        <mxCell id="tbl_reviewer" value="«table»&#xa;&lt;b&gt;reviewer / third_party_org&lt;/b&gt;&#xa;----------------------------&#xa;+ id : bigint [PK]&#xa;----------------------------&#xa;+ user_id : bigint [FK]&#xa;+ org_name : varchar(128)&#xa;+ qualification_lvl : varchar&#xa;+ status : int" style="shape=table;html=1;whiteSpace=wrap;fillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="600" y="340" width="200" height="140" as="geometry" />
        </mxCell>

        <!-- 域 3: 碳资产核算域 -->
        <mxCell id="dom_carbon" value="【碳资产核算与数字签名域】" style="shape=folder;fontStyle=1;tabWidth=190;tabHeight=25;tabPosition=left;html=1;boundedLbl=1;fillColor=#F8FAFC;strokeColor=#94A3B8;fontColor=#334155;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="1080" y="100" width="480" height="420" as="geometry" />
        </mxCell>
        <mxCell id="tbl_report" value="«table»&#xa;&lt;b&gt;carbon_report (核算报告主表)&lt;/b&gt;&#xa;---------------------------------&#xa;+ id : bigint [PK]&#xa;---------------------------------&#xa;+ enterprise_id : bigint [FK]&#xa;+ report_title : varchar(128)&#xa;+ period : varchar(32)&#xa;+ total_emissions : decimal&#xa;+ status : varchar(32)&#xa;+ rsa_signature : text&#xa;+ tx_hash : varchar(64)" style="shape=table;html=1;whiteSpace=wrap;fillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="1100" y="140" width="210" height="190" as="geometry" />
        </mxCell>
        <mxCell id="tbl_rsa" value="«table»&#xa;&lt;b&gt;rsa_key_pair (密钥托管)&lt;/b&gt;&#xa;---------------------------------&#xa;+ id : bigint [PK]&#xa;---------------------------------&#xa;+ enterprise_id : bigint [FK]&#xa;+ public_key : text&#xa;+ enc_private_key : text&#xa;+ key_status : int" style="shape=table;html=1;whiteSpace=wrap;fillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="1330" y="140" width="210" height="140" as="geometry" />
        </mxCell>
        <mxCell id="tbl_neutral" value="«table»&#xa;&lt;b&gt;carbon_neutral_project&lt;/b&gt;&#xa;---------------------------------&#xa;+ id : bigint [PK]&#xa;---------------------------------&#xa;+ enterprise_id : bigint [FK]&#xa;+ project_name : varchar(128)&#xa;+ reduction_amt : decimal&#xa;+ status : varchar(32)" style="shape=table;html=1;whiteSpace=wrap;fillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="1100" y="350" width="210" height="140" as="geometry" />
        </mxCell>
        <mxCell id="tbl_rating" value="«table»&#xa;&lt;b&gt;emission_rating (排放评级)&lt;/b&gt;&#xa;---------------------------------&#xa;+ id : bigint [PK]&#xa;---------------------------------&#xa;+ enterprise_id : bigint [FK]&#xa;+ carbon_intensity : decimal&#xa;+ rating_grade : varchar(16)&#xa;+ year : int" style="shape=table;html=1;whiteSpace=wrap;fillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="1330" y="350" width="210" height="140" as="geometry" />
        </mxCell>

        <!-- 域 4: 市场拍卖撮合域 -->
        <mxCell id="dom_trade" value="【配额市场拍卖与撮合结算域】" style="shape=folder;fontStyle=1;tabWidth=200;tabHeight=25;tabPosition=left;html=1;boundedLbl=1;fillColor=#F8FAFC;strokeColor=#94A3B8;fontColor=#334155;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="80" y="550" width="460" height="420" as="geometry" />
        </mxCell>
        <mxCell id="tbl_order" value="«table»&#xa;&lt;b&gt;auction_order (拍卖挂单表)&lt;/b&gt;&#xa;----------------------------&#xa;+ id : bigint [PK]&#xa;----------------------------&#xa;+ enterprise_id : bigint [FK]&#xa;+ order_type : varchar(16)&#xa;+ quota_amount : decimal&#xa;+ price : decimal(16,4)&#xa;+ status : varchar(32)&#xa;+ frozen_amount : decimal" style="shape=table;html=1;whiteSpace=wrap;fillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="100" y="590" width="200" height="170" as="geometry" />
        </mxCell>
        <mxCell id="tbl_match" value="«table»&#xa;&lt;b&gt;matching_result (撮合出清)&lt;/b&gt;&#xa;----------------------------&#xa;+ id : bigint [PK]&#xa;----------------------------&#xa;+ buy_order_id : bigint [FK]&#xa;+ sell_order_id : bigint [FK]&#xa;+ match_price : decimal&#xa;+ match_amount : decimal&#xa;+ batch_id : varchar(64)" style="shape=table;html=1;whiteSpace=wrap;fillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="320" y="590" width="200" height="160" as="geometry" />
        </mxCell>
        <mxCell id="tbl_tx" value="«table»&#xa;&lt;b&gt;transaction (原子结算表 DVP)&lt;/b&gt;&#xa;-------------------------------------------------------&#xa;+ id : bigint [PK]&#xa;-------------------------------------------------------&#xa;+ buy_ent_id : bigint [FK]   |   + sell_ent_id : bigint [FK]&#xa;+ matching_id : bigint [FK]   |   + total_price : decimal&#xa;+ tx_hash : varchar(64)     |   + settlement_status : varchar" style="shape=table;html=1;whiteSpace=wrap;fillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="100" y="790" width="420" height="140" as="geometry" />
        </mxCell>

        <!-- 域 5: 碳币经济与信誉评价域 -->
        <mxCell id="dom_coin" value="【碳币账户与信誉评价域】" style="shape=folder;fontStyle=1;tabWidth=180;tabHeight=25;tabPosition=left;html=1;boundedLbl=1;fillColor=#F8FAFC;strokeColor=#94A3B8;fontColor=#334155;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="580" y="550" width="460" height="420" as="geometry" />
        </mxCell>
        <mxCell id="tbl_coin_acc" value="«table»&#xa;&lt;b&gt;carbon_coin_account (资金)&lt;/b&gt;&#xa;----------------------------&#xa;+ id : bigint [PK]&#xa;----------------------------&#xa;+ enterprise_id : bigint [FK]&#xa;+ balance : decimal(16,4)&#xa;+ frozen_balance : decimal&#xa;+ total_income : decimal&#xa;+ total_expense : decimal" style="shape=table;html=1;whiteSpace=wrap;fillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="600" y="590" width="200" height="170" as="geometry" />
        </mxCell>
        <mxCell id="tbl_coin_tx" value="«table»&#xa;&lt;b&gt;carbon_coin_transaction&lt;/b&gt;&#xa;----------------------------&#xa;+ id : bigint [PK]&#xa;----------------------------&#xa;+ account_id : bigint [FK]&#xa;+ tx_type : varchar(32)&#xa;+ amount : decimal(16,4)&#xa;+ balance_after : decimal" style="shape=table;html=1;whiteSpace=wrap;fillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="820" y="590" width="200" height="150" as="geometry" />
        </mxCell>
        <mxCell id="tbl_credit_score" value="«table»&#xa;&lt;b&gt;credit_score (信誉总表)&lt;/b&gt;&#xa;----------------------------&#xa;+ id : bigint [PK]&#xa;----------------------------&#xa;+ enterprise_id : bigint [FK]&#xa;+ current_score : int&#xa;+ credit_level : varchar(16)&#xa;+ rating_date : datetime" style="shape=table;html=1;whiteSpace=wrap;fillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="600" y="790" width="200" height="150" as="geometry" />
        </mxCell>
        <mxCell id="tbl_credit_event" value="«table»&#xa;&lt;b&gt;credit_event (奖惩轨迹)&lt;/b&gt;&#xa;----------------------------&#xa;+ id : bigint [PK]&#xa;----------------------------&#xa;+ enterprise_id : bigint [FK]&#xa;+ event_type : varchar(32)&#xa;+ delta_points : int&#xa;+ reason : varchar(255)" style="shape=table;html=1;whiteSpace=wrap;fillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="820" y="790" width="200" height="150" as="geometry" />
        </mxCell>

        <!-- 域 6: 可靠事件外箱与审计域 -->
        <mxCell id="dom_outbox" value="【可靠事件外箱与审计运维域】" style="shape=folder;fontStyle=1;tabWidth=200;tabHeight=25;tabPosition=left;html=1;boundedLbl=1;fillColor=#F8FAFC;strokeColor=#94A3B8;fontColor=#334155;fontSize=11;" vertex="1" parent="1">
          <mxGeometry x="1080" y="550" width="480" height="420" as="geometry" />
        </mxCell>
        <mxCell id="tbl_outbox" value="«table»&#xa;&lt;b&gt;outbox_message (外发箱)&lt;/b&gt;&#xa;---------------------------------&#xa;+ id : bigint [PK]&#xa;---------------------------------&#xa;+ aggregate_type : varchar&#xa;+ aggregate_id : varchar&#xa;+ payload : text&#xa;+ status : varchar(32)&#xa;+ retry_count : int" style="shape=table;html=1;whiteSpace=wrap;fillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="1100" y="590" width="210" height="160" as="geometry" />
        </mxCell>
        <mxCell id="tbl_op_log" value="«table»&#xa;&lt;b&gt;operation_log (敏感审计)&lt;/b&gt;&#xa;---------------------------------&#xa;+ id : bigint [PK]&#xa;---------------------------------&#xa;+ user_id : bigint [FK]&#xa;+ action : varchar(64)&#xa;+ details : text&#xa;+ ip_address : varchar(45)" style="shape=table;html=1;whiteSpace=wrap;fillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="1330" y="590" width="210" height="160" as="geometry" />
        </mxCell>
        <mxCell id="tbl_notif" value="«table»&#xa;&lt;b&gt;notification (站内消息)&lt;/b&gt;&#xa;---------------------------------&#xa;+ id : bigint [PK]&#xa;---------------------------------&#xa;+ user_id : bigint [FK]&#xa;+ title : varchar(128)&#xa;+ content : text&#xa;+ is_read : boolean" style="shape=table;html=1;whiteSpace=wrap;fillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="1100" y="780" width="210" height="150" as="geometry" />
        </mxCell>
        <mxCell id="tbl_flyway" value="«table»&#xa;&lt;b&gt;flyway_schema_history&lt;/b&gt;&#xa;---------------------------------&#xa;+ installed_rank : int [PK]&#xa;---------------------------------&#xa;+ version : varchar(50)&#xa;+ description : varchar(200)&#xa;+ success : boolean" style="shape=table;html=1;whiteSpace=wrap;fillColor=#FFFFFF;strokeColor=#475569;fontColor=#0F172A;fontSize=10;" vertex="1" parent="1">
          <mxGeometry x="1330" y="780" width="210" height="150" as="geometry" />
        </mxCell>

      </root>
    </mxGraphModel>
  </diagram>
</mxfile>"""
    out_p = os.path.join(OUTPUT_DIR, '04-database-entity-er-diagram.drawio')
    with open(out_p, 'w', encoding='utf-8') as f:
        f.write(xml_content)
    print(f"Generated draw.io source: {out_p}")

if __name__ == '__main__':
    create_architecture_drawio()
    create_database_er_drawio()
