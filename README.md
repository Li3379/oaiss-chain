# 双碳链动系统 (OAISS CHAIN)

> 基于区块链与人工智能的可信碳核算、碳交易与资产履约企业级综合平台。

[![Java 17](https://img.shields.io/badge/Java-17-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.12-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5-green.svg)](https://vuejs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.x-blue.svg)](https://www.typescriptlang.org/)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.111-teal.svg)](https://fastapi.tiangolo.com/)
[![Hyperledger Fabric](https://img.shields.io/badge/Fabric-2.5+-blueviolet.svg)](https://www.hyperledger.org/projects/fabric)

---

## 一、 系统概述

**OAISS CHAIN** 是一套面向国家“碳达峰、碳中和”战略的全栈技术平台。系统深度融合了：
1. **国标碳核算**：GB/T 32150 发电企业（25项参数）与电网企业（9项参数）碳排放精准模型；
2. **多模式碳交易**：支持 P2P 双边协议交易与双向连续竞价拍卖（Double Auction）；
3. **数字签名与防伪**：基于 RSA 2048 与 AES-256-GCM 信封加密，实现申报数据防篡改与法律效力；
4. **区块链存证**：采用 Hyperledger Fabric 联盟链将审核通过的报告与成交记录持久化存证；
5. **AI 智能预测**：基于 Prophet / XGBoost / IsolationForest 提供碳排放预测与企业合规推断。

### 四角色业务协同矩阵

```text
┌─────────────────┐       ┌──────────────────────────┐       ┌────────────────────┐
│ 企业 (ENTERPRISE)│ ───>  │ 审核员 (REVIEWER)        │ ───>  │ 管理员 (ADMIN)     │
│ - 填报碳排放数据 │ 签名  │ - 审核合规报告           │ 通过  │ - 平台准入与赋权   │
│ - 发起P2P/拍卖  │ 提交  │ - 审核通过即自动上链     │ 自动  │ - 系统配置与运维   │
└─────────────────┘       └──────────────────────────┘       └────────────────────┘
         ▲                                                    │
         └──────────────── 碳币结算与交易履约 ─────────────────┘
                                  ▲
                       ┌──────────────────────────┐
                       │ 监管 (THIRD_PARTY)       │
                       │ - 行业大屏宏观监管       │
                       │ - 穿透审计与公信核验     │
                       │ - 只读核查（不参与上链审批）│
                       └──────────────────────────┘
```

---

## 二、 3 步极速上手（面向人类普通开发者，无需任何 AI 介入）

本项目经过深度轻量化调优，**日常开发完全不需要在本地安装笨重的 Hyperledger Fabric 节点网络**（后端默认集成 Mock 区块链引擎，自动模拟生成合法哈希），仅需标准三步即可在本地 5 分钟内完整运行：

### 1. 准备配置文件 (10 秒)
在项目根目录下复制环境变量模板生成 `.env`：
```bash
cp .env.example .env
```

### 2. 启动中间件基础设施 (1 分钟)
使用 Docker Compose 启动 MySQL 8、Redis 7、MinIO 对象存储与 Python ML 服务：
```bash
docker compose -f deploy/compose/docker-compose.infra.yml up -d
```
*启动后，MySQL 数据库表结构将由 Flyway 自动执行增量迁移，无感初始化 24 张核心表与种子数据。*

### 3. 本地启动前后端 (断点调试模式)

* **启动后端 (Java 17)**：
  在 IntelliJ IDEA 或 Eclipse 中打开 `oaiss-chain-backend`，运行主类：
  `com.oaiss.chain.OaissChainApplication.java`（端口 `8080`，API 前缀 `/api/v1`）。
* **启动前端 (Node.js)**：
  在终端中进入前端目录启动 Vite 开发服务器：
  ```bash
  cd oaiss-chain-frontend
  npm install
  npm run dev
  ```
  访问浏览器：`http://localhost:5173`。

---

## 三、 四大角色开箱预置账号

系统内置全套角色种子数据，直接使用以下账号即可体验全部业务流：

| 角色类型 | 登录账号 | 初始密码 | 体验功能 |
| :--- | :--- | :--- | :--- |
| **企业用户** | `enterprise001` | `admin123` | 碳核算计算器、填报申报、挂单交易、碳币账户、AI预测 |
| **审核员** | `reviewer001` | `admin123` | 审核待审企业碳报告、复核与驳回流转 |
| **第三方监管**| `thirdparty001` | `admin123` | 碳排放多维监管大屏、公信力核验、穿透式合规监管 |
| **超级管理员**| `admin` | `admin123` | 资质准入审核、系统用户赋权、系统配置运维、全站审计日志（上链由审核员自动触发） |

---

## 四、 本地轻量化开发与调优技巧

1. **区块链 Mock 引擎**：
   * 本地默认使用 `MockBlockchainService`，所有存证与交易哈希在内存毫秒级生成，开发业务逻辑无需消耗额外内存；
   * 若需联调真实 Fabric SDK 底层网络，可执行 `scripts\start-all.bat --with-fabric`（Windows）或 `./scripts/start-all.sh --with-fabric`。
2. **数据库零手工维护 (Flyway)**：
   * 禁止在客户端工具中手动修改表结构。如需新增字段，在 `oaiss-chain-backend/src/main/resources/db/migration/` 增加 `Vxx__xxx.sql`，应用重启自动生效。
3. **接口在线文档 (Swagger/OpenAPI)**：
   * 后端启动后，直接访问：`http://localhost:8080/api/v1/swagger-ui/index.html` 在线调试全部 153 个 REST 接口。
4. **提交前一键自检**：
   ```bash
   node scripts/doc-verify.mjs          # 校验代码与架构事实零漂移（数字）
   node scripts/role-matrix-verify.mjs  # 校验角色/权限语义与方案 A 定稿一致
   node scripts/i18n-verify.mjs  # 校验前端国际化语言包完整性
   ```

---

## 五、 企业级标准工程目录导航

```text
OAISS CHAIN/
├── deploy/                      # [基础设施中枢] 统领全部环境编排、网络与监控
│   ├── compose/                 # 4 套环境 Compose 配置 (infra, prod, release, fabric)
│   ├── k8s/                     # 13 个 Kubernetes 生产清单与弹性伸缩 HPA
│   ├── monitoring/              # Prometheus 告警规则 + Grafana 仪表盘 + Zipkin
│   ├── nginx/                   # 边缘网关反向代理模板
│   └── fabric/                  # Fabric 区块链网络拓扑与证书配置
├── docs/                        # [知识资产中心] 纯净活文档
│   ├── OAISS_CHAIN_技术架构与使用说明书.docx # [唯一企业级规整规范书] 全系统详细设计与操作指南
│   └── raw/                     # 5 份 CMMI 标准原始需求说明
├── scripts/                     # [工程工具体系] 7 大功能子系统 + 顶层零破坏兼容桩
│   ├── api/                     # 22 个 Spring Boot REST API 集成测试脚本
│   ├── browser-qa/              # 13 个 Playwright 自动化巡检套件
│   ├── lifecycle/               # 9 个服务启动/停止 CLI 脚本
│   ├── ops/                     # 生产容器编排封装、Fabric 初始化与远程发布脚本
│   ├── gates/                   # 核心代码-文档零漂移与上线审计门禁
│   ├── db/                      # 数据库维护与清洗脚本
│   └── common/                  # 共享断言测试框架与环境配置
├── oaiss-chain-backend/         # [核心后端] 纯净 Java 17 + Spring Boot 3.2 业务工程
├── oaiss-chain-frontend/        # [前端应用] 纯净 Vue 3.5 + TypeScript SPA 模块
├── oaiss-chain-ml-service/      # [算法服务] 纯净 Python 3.11 + FastAPI 预测微服务
├── oaiss-chain-chaincode/       # [区块链] 纯净 Hyperledger Fabric Go 链码
├── docker-compose.yml           # 根目录全栈本地一键启动入口 (唯一的根 Compose)
├── pom.xml                      # 根构建聚合文件
└── README.md                    # 本文件
```

---

## 六、 详细企业级技术规格与文档

本项目已将完整的架构设计、碳核算公式数学推导、27 张表数据字典、153 个 API 端点规范及生产高可用运维指南汇编生成为正式的企业级 Word 规格书，详见：
👉 **[`docs/OAISS_CHAIN_技术架构与使用说明书.docx`](docs/OAISS_CHAIN_技术架构与使用说明书.docx)**。
