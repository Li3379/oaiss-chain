#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OAISS CHAIN - 生成 5 张全新模块专属 300 DPI 高清工程架构关系大图:
1. 08-frontend-vue3-architecture.png: 前端工程化多层架构与安全管控设计图
2. 09-fabric-consortium-network-topology.png: Hyperledger Fabric 联盟链网络拓扑与链码调用架构图
3. 10-ai-ml-microservice-architecture.png: AI/ML 预测算法微服务中台与大模型推理架构图
4. 11-container-cluster-deployment-topology.png: 容器化集群部署与网络隔离拓扑架构图
5. 12-fullstack-testing-pyramid.png: 全栈分层测试金字塔与质量度量架构图
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

def create_canvas(title, subtitle, w=16, h=9):
    fig, ax = plt.subplots(figsize=(w, h), dpi=300)
    fig.patch.set_facecolor('#0B1120')
    ax.set_facecolor('#0B1120')
    ax.set_xlim(0, w)
    ax.set_ylim(0, h)
    ax.axis('off')
    
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
# 图 8: 前端工程化多层架构与安全管控设计图
# ==========================================
def render_fig_08():
    fig, ax = create_canvas("OAISS CHAIN · 前端工程化多层架构与安全管控设计图",
                            "Vue 3.5 SPA Multi-Tier Architecture & Role-Based Route Guards")

    draw_node(ax, 0.8, 5.2, 3.2, 2.2, "1. 客户端运行时与构建",
              ["• Vite 5 构建与模块热重载",
               "• TypeScript 5 严格类型约束",
               "• Vendor 分包 (Vue/ECharts/Element)",
               "• 统一 i18n 双语切换 (1430词条)"],
              border_color='#38BDF8', bg_color='#0C4A6E')

    draw_node(ax, 4.6, 5.2, 3.4, 2.2, "2. 交互表现层 (Presentation)",
              ["• Element Plus 2.13 响应式组件",
               "• ECharts 6 金融级看板渲染",
               "• 动态深色/浅色主题引擎",
               "• 四角色独立工作台布局容器"],
              border_color='#60A5FA', bg_color='#1E293B')

    draw_node(ax, 8.6, 5.2, 3.2, 2.2, "3. 状态与路由中心",
              ["• Pinia 3.0 全局模块化状态",
               "• appStore / userStore / carbonStore",
               "• Vue Router 4.0 动态路由挂载",
               "• 前置路由守卫 (router.beforeEach)"],
              border_color='#10B981', bg_color='#064E3B')

    draw_node(ax, 12.4, 5.2, 2.8, 2.2, "4. 四角色鉴权控制网格",
              ["• ENTERPRISE: 填报/挂单",
               "• REVIEWER: 审核/驳回",
               "• THIRD_PARTY: 监管/上链",
               "• ADMIN: 准入/密钥/审计"],
              border_color='#EC4899', bg_color='#500724', title_color='#F472B6')

    # 通信管道
    draw_node(ax, 2.5, 1.6, 11.0, 2.4, "5. Axios 统一通信与拦截器管道 (request.ts)",
              ["• 请求拦截器: 自动提取 localStorage 访问令牌，注入 Authorization: Bearer <Token> 头，记录链路跟踪 requestId",
               "• 分页规整管道: 前端 1-based pageNum 自动转换后端的 0-based page 查询参数",
               "• 响应拦截器: 拦截非 200 响应信封，统一解包 ApiResponse<T>，401 自动触发防抖登出并重定向登录页",
               "• 网络容错网闸: 捕获 5xx 网关异常与断网状态，弹出标准化友好的 ELI5 错误提示"],
              border_color='#F59E0B', bg_color='#451A03', title_color='#FCD34D')

    # 连线
    draw_arrow(ax, 4.0, 6.3, 4.6, 6.3, '#38BDF8', '驱动渲染')
    draw_arrow(ax, 8.0, 6.3, 8.6, 6.3, '#60A5FA', '绑定状态')
    draw_arrow(ax, 11.8, 6.3, 12.4, 6.3, '#10B981', '角色守卫')
    draw_arrow(ax, 6.3, 5.2, 6.3, 4.0, '#F59E0B', 'API 调度')
    draw_arrow(ax, 10.2, 5.2, 10.2, 4.0, '#F59E0B', '响应回写')

    fig.savefig(os.path.join(OUTPUT_DIR, '08-frontend-vue3-architecture.png'), bbox_inches='tight')
    plt.close(fig)
    print("生成完成: 08-frontend-vue3-architecture.png")

# ==========================================
# 图 9: Hyperledger Fabric 联盟链网络拓扑与链码架构图
# ==========================================
def render_fig_09():
    fig, ax = create_canvas("OAISS CHAIN · Hyperledger Fabric 联盟链网络拓扑与链码调用架构图",
                            "Consortium Blockchain Infrastructure, Channel Artifacts & Gateway SDK")

    # 1. 业务调用端
    draw_node(ax, 0.8, 4.8, 3.2, 2.6, "Spring Boot 后端应用",
              ["• FabricGatewayService 连接池",
               "• Fabric Gateway Java SDK 1.11.0",
               "• X.509 客户端证书与私钥签名",
               "• gRPC ManagedChannel 通道",
               "• 支持 evaluate(读) / submit(写)"],
              border_color='#38BDF8', bg_color='#1E293B')

    # 2. 排序集群
    draw_node(ax, 4.8, 6.0, 3.6, 1.8, "Raft 共识排序集群 (Orderer)",
              ["• orderer.example.com (端口 7050)",
               "• 状态机复制与 Raft Leader 选举",
               "• 批量打包出块与区块哈希计算"],
              border_color='#F59E0B', bg_color='#451A03', title_color='#FCD34D')

    # 3. 组织节点群
    draw_node(ax, 4.8, 1.6, 3.6, 3.8, "联盟链组织 Peer 节点群",
              ["• 组织1: Org1MSP (企业节点 peer0:7051)",
               "• 组织2: Org2MSP (审核节点 peer0:9051)",
               "• 组织3: Org3MSP (监管节点 peer0:11051)",
               "• LevelDB 状态账本 (World State)",
               "• 背书策略: AND('Org1MSP', 'Org2MSP')",
               "• 账本只读查询与两阶段背书"],
              border_color='#10B981', bg_color='#064E3B', title_color='#6EE7B7')

    # 4. 通道与智能合约
    draw_node(ax, 9.2, 3.6, 3.2, 3.2, "共享通道与智能合约 (Channel)",
              ["• 通道名称: carbon-channel",
               "• 链码 1: carbon-report-cc",
               "  (碳核算指纹登记与验签固化)",
               "• 链码 2: carbon-trade-cc",
               "  (配额撮合成交与不可逆过户)",
               "• 链码 3: carbon-neutral-cc",
               "  (中和项目减排量签发与注销)"],
              border_color='#A855F7', bg_color='#3B0764', title_color='#C084FC')

    # 5. 存证交付物
    draw_node(ax, 13.0, 3.6, 2.2, 3.2, "链上存证凭证",
              ["• 区块高度 (Block#)",
               "• 唯一交易 TxHash",
               "• 提案背书签名集合",
               "• 防伪二维码生成",
               "• 穿透监管核验"],
              border_color='#EC4899', bg_color='#500724', title_color='#F472B6')

    # 连线
    draw_arrow(ax, 4.0, 6.2, 4.8, 6.5, '#F59E0B', '提交交易广播')
    draw_arrow(ax, 4.0, 5.2, 4.8, 4.2, '#10B981', '发送提案背书')
    draw_arrow(ax, 8.4, 6.5, 9.2, 5.8, '#F59E0B', '区块分发提交')
    draw_arrow(ax, 8.4, 4.2, 9.2, 4.8, '#10B981', '驱动链码执行')
    draw_arrow(ax, 12.4, 5.2, 13.0, 5.2, '#EC4899', '回写链上凭证')

    fig.savefig(os.path.join(OUTPUT_DIR, '09-fabric-consortium-network-topology.png'), bbox_inches='tight')
    plt.close(fig)
    print("生成完成: 09-fabric-consortium-network-topology.png")

# ==========================================
# 图 10: AI/ML 预测算法微服务中台架构图
# ==========================================
def render_fig_10():
    fig, ax = create_canvas("OAISS CHAIN · AI/ML 预测算法微服务中台与大模型推理架构图",
                            "FastAPI Microservice, Multi-Model Pipeline & Ollama Qwen 2.5 LLM")

    draw_node(ax, 0.8, 3.6, 3.2, 3.0, "Spring Boot 业务核心",
              ["• CarbonAiPredictService",
               "• WebClient 异步非阻塞调用",
               "• Resilience4j 熔断器保护",
               "  - 失败率阈值: 50%",
               "  - 慢调用熔断与超时重试",
               "• 自动降级与冷启动保护",
               "• 存证哈希解算与上链驱动"],
              border_color='#38BDF8', bg_color='#1E293B')

    draw_node(ax, 4.8, 3.6, 3.4, 3.0, "FastAPI 异步算法中台 (8001)",
              ["• Python 3.12 + Uvicorn 异步服务",
               "• Docker 容器网络安全隔离",
               "• 路由 1: /predict/market (碳价)",
               "• 路由 2: /inference/enterprise (体检)",
               "• 路由 3: /predict/emission (排放)",
               "• 路由 4: /llm/generate-dossier (报告)"],
              border_color='#F59E0B', bg_color='#451A03', title_color='#FCD34D')

    draw_node(ax, 8.8, 5.2, 3.4, 2.4, "核心算法计算矩阵",
              ["• Meta Prophet 1.1.5: 时序分解 (95%置信)",
               "• XGBoost 2.0.3: 多维敏感度归因 (XAI)",
               "• Scikit-learn: Isolation Forest 离群诊断",
               "• 6 维合规偏离雷达图指标解算"],
              border_color='#10B981', bg_color='#064E3B')

    draw_node(ax, 8.8, 1.4, 3.4, 2.4, "通义千问本地大模型 (Ollama)",
              ["• 模型型号: qwen2.5:7b-instruct",
               "• 独立推理守护进程 (端口 11434)",
               "• 智能生成《碳盘查归因诊断深度报告》",
               "• 结构化输出三阶段降碳技改路径"],
              border_color='#A855F7', bg_color='#3B0764', title_color='#C084FC')

    draw_node(ax, 12.8, 3.6, 2.4, 2.8, "可信结果交付",
              ["• 金融级 ECharts 看板",
               "• 支撑位与阻力位标签",
               "• 操盘建仓策略建议",
               "• SHA-256 存证哈希",
               "• Fabric 链上溯源存证"],
              border_color='#EC4899', bg_color='#500724', title_color='#F472B6')

    # 连线
    draw_arrow(ax, 4.0, 5.1, 4.8, 5.1, '#38BDF8', 'HTTP/REST 载荷')
    draw_arrow(ax, 8.2, 5.8, 8.8, 6.2, '#10B981', '时序拟合归因')
    draw_arrow(ax, 8.2, 4.4, 8.8, 2.8, '#A855F7', 'Prompt 诊断请求')
    draw_arrow(ax, 12.2, 6.2, 12.8, 5.4, '#EC4899', '回传预测数据')
    draw_arrow(ax, 12.2, 2.8, 12.8, 4.4, '#EC4899', '回传诊断报表')

    fig.savefig(os.path.join(OUTPUT_DIR, '10-ai-ml-microservice-architecture.png'), bbox_inches='tight')
    plt.close(fig)
    print("生成完成: 10-ai-ml-microservice-architecture.png")

# ==========================================
# 图 11: 容器化集群部署与网络拓扑架构图
# ==========================================
def render_fig_11():
    fig, ax = create_canvas("OAISS CHAIN · 容器化集群部署与网络隔离拓扑架构图",
                            "Docker Compose & Kubernetes Network Isolation Topology")

    # 外部公网
    draw_node(ax, 0.8, 3.6, 2.2, 2.8, "外部接入端",
              ["• 浏览器终端", "• 企业客户端", "• 外部监控系统", "• HTTPS (443)", "• HTTP (80)"],
              border_color='#64748B', bg_color='#1E293B')

    # 边缘代理
    draw_node(ax, 3.6, 3.6, 2.6, 2.8, "边界 Nginx 网关",
              ["• 端口: 80 / 443", "• SSL 证书解密卸载", "• 静态资源托管", "• 动静分离反向代理", "• 挂载 host 端口"],
              border_color='#38BDF8', bg_color='#0C4A6E')

    # 核心应用网络 (oaiss-private)
    draw_node(ax, 6.8, 4.8, 4.0, 2.6, "业务服务区 (oaiss-private 隔离网)",
              ["• backend (Spring Boot 3.2.12, 端口 8080)",
               "• ml-service (FastAPI 3.12, 端口 8001)",
               "• HPA 弹性水平伸缩 (2~5 副本)",
               "• 非 root 容器特权受限运行"],
              border_color='#10B981', bg_color='#064E3B')

    # 数据与存储网络 (oaiss-network)
    draw_node(ax, 6.8, 1.2, 4.0, 2.6, "数据持久区 (oaiss-network 专网)",
              ["• mysql (MySQL 8.0, 端口 3306, 卷挂载)",
               "• redis (Redis 7.0, 端口 6379, 密码鉴权)",
               "• minio (MinIO S3, 端口 9000/9001)",
               "• 严禁对外暴露裸端口，仅限集群内互联"],
              border_color='#F59E0B', bg_color='#451A03', title_color='#FCD34D')

    # 联盟链独立沙箱
    draw_node(ax, 11.4, 3.0, 3.8, 3.6, "Fabric 联盟链集群网络",
              ["• orderer.example.com (端口 7050)",
               "• peer0.org1.example.com (端口 7051)",
               "• peer0.org2.example.com (端口 9051)",
               "• ca.org1.example.com (端口 7054)",
               "• docker.sock 隔离链码执行环境",
               "• 独立证书卷材料与加密材料存储"],
              border_color='#A855F7', bg_color='#3B0764', title_color='#C084FC')

    # 连线
    draw_arrow(ax, 3.0, 5.0, 3.6, 5.0, '#38BDF8', '公网流量')
    draw_arrow(ax, 6.2, 5.8, 6.8, 5.8, '#10B981', '转发内网')
    draw_arrow(ax, 8.8, 4.8, 8.8, 3.8, '#F59E0B', 'DB/Redis/S3')
    draw_arrow(ax, 10.8, 5.8, 11.4, 5.2, '#A855F7', 'Fabric gRPC')

    fig.savefig(os.path.join(OUTPUT_DIR, '11-container-cluster-deployment-topology.png'), bbox_inches='tight')
    plt.close(fig)
    print("生成完成: 11-container-cluster-deployment-topology.png")

# ==========================================
# 图 12: 全栈分层测试金字塔与质量度量架构图
# ==========================================
def render_fig_12():
    fig, ax = create_canvas("OAISS CHAIN · 全栈分层测试金字塔与质量度量架构图",
                            "Fullstack Testing Pyramid, JaCoCo 95% Baseline & Playwright E2E")

    # 绘制金字塔梯形多边形
    # 顶层: E2E (10-15%)
    poly_top = patches.Polygon([[8.0, 7.5], [6.6, 5.6], [9.4, 5.6]], closed=True,
                               edgecolor='#EC4899', facecolor='#500724', linewidth=1.5)
    ax.add_patch(poly_top)
    ax.text(8.0, 6.4, "端到端巡检测试\n(E2E 10-15%)\nPlaywright 29视图",
            fontsize=9.5, color='#F8FAFC', weight='bold', ha='center', va='center')

    # 中层: 集成测试 (20-30%)
    poly_mid = patches.Polygon([[6.6, 5.6], [5.0, 3.5], [11.0, 3.5], [9.4, 5.6]], closed=True,
                               edgecolor='#38BDF8', facecolor='#0C4A6E', linewidth=1.5)
    ax.add_patch(poly_mid)
    ax.text(8.0, 4.4, "切片与容器集成测试 (Integration 20-30%)\nTestcontainers 真实 MySQL 8 + Redis 7 容器\n@DataJpaTest / @WebMvcTest / 22 个专用回归脚本",
            fontsize=9.5, color='#F8FAFC', weight='bold', ha='center', va='center')

    # 底层: 单元测试 (60-70%)
    poly_bot = patches.Polygon([[5.0, 3.5], [3.2, 1.2], [12.8, 1.2], [11.0, 3.5]], closed=True,
                               edgecolor='#10B981', facecolor='#064E3B', linewidth=1.5)
    ax.add_patch(poly_bot)
    ax.text(8.0, 2.2, "核心单元测试 (Unit Tests 60-70%)\nJUnit 5 + Mockito (后端 35 个业务服务) / Vitest (前端状态与逻辑)\nJaCoCo 行覆盖率 >= 95% / 分支覆盖率 >= 85%",
            fontsize=9.5, color='#F8FAFC', weight='bold', ha='center', va='center')

    # 左侧度量标注
    draw_node(ax, 0.6, 3.2, 2.2, 2.8, "质量门槛约束",
              ["• 单元测试 100% 通过",
               "• 0 Error / 0 Failure",
               "• 控制台报错数为 0",
               "• 架构断言零漂移"],
              border_color='#F59E0B', bg_color='#1E293B')

    # 右侧度量标注
    draw_node(ax, 13.2, 3.2, 2.2, 2.8, "执行耗时规范",
              ["• 单元测试: < 30 秒",
               "• 切片测试: < 2 分钟",
               "• E2E 走查: < 5 分钟",
               "• 门禁拦截: 毫秒级"],
              border_color='#A855F7', bg_color='#1E293B')

    fig.savefig(os.path.join(OUTPUT_DIR, '12-fullstack-testing-pyramid.png'), bbox_inches='tight')
    plt.close(fig)
    print("生成完成: 12-fullstack-testing-pyramid.png")

def main():
    render_fig_08()
    render_fig_09()
    render_fig_10()
    render_fig_11()
    render_fig_12()
    print("5 张全新模块专属企业架构大图全部生成完毕！")

if __name__ == '__main__':
    main()
