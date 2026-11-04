# OAISS CHAIN — 开发主控指南 (DEV-GUIDE)

> **单一真相源** | 最后同步: 2026-10-21 | 断言校验: `node scripts/doc-verify.mjs` + `node scripts/role-matrix-verify.mjs`
>
> 本文件是开发时唯一必读的权威文档。所有活文档经它引用；所有事实经断言脚本自动校验，与代码零漂移。
> **开发新功能前请先读本文件；改代码后运行断言，漂移由 AI 自修。**

---

## 1. 项目是什么

**双碳链动系统** — 基于区块链的可信碳核算与交易平台。企业上报碳排放 → 审核员审核 → 碳交易（P2P + 双向拍卖）→ 碳币经济 → 信用评分 → 数字签名 → 碳中和项目 → AI 预测 → Fabric 存证。

**四角色**: `ENTERPRISE`(企业) / `REVIEWER`(审核员) / `THIRD_PARTY`(第三方监管) / `ADMIN`(管理员)

## 2. 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Java 17, Spring Boot 3.2.12, Spring Data JPA, Spring Security, MySQL 8, Redis 7, S3 对象存储 (LocalStack), Flyway, jjwt 0.12.5 |
| 前端 | Vue 3.5, TypeScript, Vite, Element Plus 2.13, Pinia 3, Vue Router 5, ECharts 6, vue-i18n 11 |
| ML | Python FastAPI (Prophet, scikit-learn, XGBoost) — 端口 8001 |
| 区块链 | Go Chaincode, Hyperledger Fabric Gateway SDK（可选，`fabric` profile 默认关） |
| 测试 | JUnit 5 + Testcontainers + JaCoCo(95%), Vitest, Playwright |

## 3. 仓库结构

```
├── oaiss-chain-backend/    Spring Boot 后端 (23 controllers / 42 services / 28 entities / 27 repos，纯净业务模块)
├── oaiss-chain-frontend/   Vue 3 SPA (22 api modules, views/ 按角色分，纯净前端模块)
├── oaiss-chain-ml-service/ FastAPI ML (3 routers: emission/enterprise/market)
├── oaiss-chain-chaincode/  Go Fabric 链码
├── deploy/                 统一基础设施与部署编排 (compose/k8s/monitoring/nginx/fabric)
├── docs/                   集中式活文档库（见 §7 文档体系）
├── scripts/                工程化脚本体系 (api/browser-qa/lifecycle/ops/gates/db/common + 顶层兼容转发桩)
└── tests/                  全局压测 (load) 与混沌工程 (chaos)
```

## 4. 关键入口点

| 入口 | 路径 |
|------|------|
| 后端主类 | `oaiss-chain-backend/.../OaissChainApplication.java` |
| 安全 | `config/SecurityConfig.java` + `security/JwtAuthenticationFilter.java` |
| 前端路由 | `oaiss-chain-frontend/src/router/index.ts`（角色守卫） |
| HTTP 客户端 | `oaiss-chain-frontend/src/api/request.ts`（JWT 注入 + 分页转换） |
| ML 入口 | `oaiss-chain-ml-service/app/main.py` |
| 链码 | `oaiss-chain-chaincode/chaincode.go` |

## 5. 请求生命周期（主排障轴）

```
Vue 组件 → api/*.ts (Axios) → request.ts 拦截器(JWT注入, pageNum→page转换)
  → /api/v1/*
    → JwtAuthenticationFilter → SecurityConfig (@PreAuthorize)
    → @RestController → @Service (@AuditLog/@RateLimit/@DataIsolation/@DistributedLock/@RequirePermission)
    → Repository (JPA) → MySQL
  → ApiResponse<T> { code, message, data, meta }
```

## 6. 核心约定

- **响应信封**: `ApiResponse<T>`；分页 `PageResponse<T>` `{ list, total, pageNum, pageSize, pages }`
- **分页转换**: 前端 `pageNum`(1-based) → `request.ts` 转 `page`(0-based) → 后端 Spring Page → 前端转回
- **软删除**: 实体继承 `BaseEntity`（`deleted`），查询用 `...AndDeletedFalse`
- **AOP 注解**: `@AuditLog` `@RateLimit` `@RequirePermission` `@DataIsolation` `@DistributedLock`
- **Fabric**: 默认关，仅 `fabric` profile 激活 `FabricBlockchainService`
- **Git**: Conventional commits (`feat|fix|docs|test|refactor|chore(scope):`)；由 `.githooks/commit-msg` 强制
- **分支**: 禁止直推 `main`，一个任务 = 一个分支 = 一个 worktree；并发作业见 `docs/开发手册/多Agent协作-Git契约.md`
- **版本**: `CHANGELOG.md` 为版本号唯一事实源，`pom.xml` / `package.json` 跟随，由 `scripts/gates/version-verify.mjs` 强制

## 7. 文档体系（活文档索引）

> 开发时只读**活文档**（`docs/` 分类目录）。历史取证与截图归档在 `docs/证据归档/` 与
> `docs/认知手册/raw/`，**不参与决策**（仓库不另设 archive 归档目录）。

> ⚠️ **路径修正（2026-10-20）**：本节此前引用的 14 条活文档路径在仓库中全部不存在，
> 属于「自称 SSOT 但索引已失效」。下表已按 `docs/` 真实文件名逐条校正，
> 并新增自动化断言（`scripts/gates/doc-verify.mjs`）防止再次漂移。

| 分类 | 活文档 | 用途 |
|------|--------|------|
| 概览 | `docs/项目概览/系统设计与架构规格书.md` | 产品全貌 + 架构决策与权衡 |
| 概览 | `docs/OAISS_CHAIN_技术架构与使用说明书.md` | 技术架构与使用说明 |
| 接口 | `docs/接口参考/系统API接口参考规范.md` | 端点与模块完整清单 |
| 指南 | `docs/开发指南/全栈开发与技术规格指南.md` | 前后端开发规范（页面/API/路由/i18n） |
| 指南 | `docs/开发手册/多Agent协作-Git契约.md` | 多 Agent/多会话并发作业的强制 Git 契约 |
| 指南 | `docs/开发指南/drawio与EA12架构图绘制实操手册.md` | 架构图绘制 |
| 运维 | `docs/运维部署/系统部署与运维手册.md` | staging/prod 部署与生产配置 |
| 产品 | `docs/产品规格/AI智能预测模块深度重塑与LLM融合升级说明.md` | AI/ML 模块设计 |
| 质保 | `docs/质量保证/质量保证与验收规范.md` | 质量门禁与验收标准 |
| 质保 | `docs/质量保证/PentAGI渗透测试Flow提示词.md` | 安全测试 |
| 认知 | `docs/认知手册/OAISS_CHAIN_认知与操作教学手册.md` | 接手/教学 |
| 索引 | `docs/文档索引.md` | 全量文档入口 |
| 证据 | `docs/认知手册/证据索引.md` / `docs/证据归档/` | 实测证据归档 |
| 原始需求 | `docs/raw/` | 原始需求规格书（勿改） |

## 8. 开发流程（AI 主导 + 人工审批）

```
1. 读本文件 §1-7 了解现状
2. 定位需求 → 打开对应活文档（数据库/接口/指南）
3. 开发（遵循 §6 约定）
4. 改代码后运行: node scripts/doc-verify.mjs && node scripts/role-matrix-verify.mjs
   ← 结构断言 + 角色权限语义断言；漂移即 AI 自修文档
5. 同步活文档（新增 API → 后端接口.md；改表 → 数据库设计.md）
6. 人工审批关键决策
```

## 9. 环境变量

`DB_PASSWORD` `REDIS_PASSWORD` `JWT_SECRET` `MINIO_ACCESS_KEY` `MINIO_SECRET_KEY` `RSA_KEK` `ML_SERVICE_SECRET`
（前端: `VITE_API_BASE_URL` 相对 `/api/v1`，`VITE_APP_TITLE`）

**可选开关**（均有默认值，见 `.env.example`）：
- `SECURITY_CAPTCHA_ENABLED`（默认 `true`）：登录是否强制图形验证码。
  **运行 `scripts/api/*.sh` 集成测试时必须设为 `false`**——这些脚本不传验证码，
  在 `true` 下会全部以 `2006 验证码错误` 失败。生产环境保持 `true`。
- `AUCTION_AUTO_MATCHING_ENABLED`（默认 `true`）：双向拍卖 30s 自动出清。
- `CORS_ALLOWED_ORIGINS`：前端来源白名单。

## 10. 常用命令

```bash
# 文档结构断言（开发后必跑）
node scripts/doc-verify.mjs

# 角色/权限语义断言（开发后必跑）——防止「文档写的权限」与「@PreAuthorize 实际授权」漂移
node scripts/role-matrix-verify.mjs

# i18n 键引用断言（开发后必跑）——代码引用的键在 zh/en 均存在且两侧键集合一致
node scripts/i18n-verify.mjs

# GitHub Actions 工作流断言（改 .github/workflows/ 或移动 compose/脚本后必跑）
# 三类静默失效：YAML 结构损坏 / 触发分支写错 / 引用路径已不存在（改完不跑，CI 恒绿）
node scripts/gates/workflow-verify.mjs

# 测试
cd oaiss-chain-backend && mvn test          # 单元
cd oaiss-chain-backend && mvn verify        # 集成(含95% JaCoCo gate)
cd oaiss-chain-frontend && npm run verify   # 类型 + 样式 + 单测（一次跑完）
cd oaiss-chain-frontend && npm run test:e2e # Playwright

# API 集成测试（需先以 SECURITY_CAPTCHA_ENABLED=false 启动后端）
bash scripts/api/login-test.sh
bash scripts/api/double-auction-test.sh

# 运行
scripts\start-all.bat --with-fabric   # Windows 全栈
docker compose -f deploy/compose/docker-compose.infra.yml up -d  # 基础设施

# Fabric 单步（--with-fabric 内部调用的就是这两个）
bash scripts/bootstrap-fabric.sh          # 建通道 + 链码部署（幂等）
bash scripts/ops/deploy-chaincode.sh 1.2 4  # 手工升级链码：版本标签 + sequence

# 接手验证（第一件事）
bash ./scripts/stability-baseline.sh
```

> **链码版本政策（2026-11-02 起）**：`FABRIC_CHAINCODE_VERSION`（默认 `1.2`）只是
> 版本**标签**，包内容始终打包自 `oaiss-chain-chaincode/` 挂载源码；**sequence 由
> `bootstrap-fabric.sh` 依网络已提交值自动 +1**，不要再手工传 sequence（旧脚本默认
> `1.0/seq1`，对已升到 v1.2/seq3 的网络会被 Fabric 以 *sequence must be strictly
> increasing* 拒绝，直接杀死 `start-all --with-fabric`）。链码源码变更 → 上调
> `.env` 中 `FABRIC_CHAINCODE_VERSION` → 重跑 bootstrap 即自动升级。
> 冒烟测试：`bash scripts/ops/test-bootstrap-fabric.sh`（6 例，含版本判定/升级/幂等）。

> ⚠️ **`scripts/api/aop-test.sh` 会执行 `git checkout --` 回退**
> `CarbonController` / `DoubleAuctionController`。运行前必须备份工作区改动，
> 或在干净分支上运行。

## 11. 核心系统结构事实（权威断言锚点）

| 事实 | 值 | 说明 |
|------|-----|------|
| Controllers | 25 | `controller/*.java` 数（2026-10-31 新增 RegulatoryFindingController、TradeAuditController） |
| REST 端点 | 169 | `@Mapping` 注解总数（2026-11-03 Phase 7 恢复碳中和 7 个生命周期端点：+7；碳币闭环 +1 `POST /carbon-coin/redeem-quota`；+1 `GET /regulatory/shortfalls`） |
| Services | 50 | 47 主包 + 3 ml/（2026-11-02 组件 1：QuotaAllocationService、IndustryBenchmarkService） |
| Entities | 29 | 28 @Entity + BaseEntity（+IndustryBenchmark） |
| Tables | 28 | 现存 28 张（曾创建 29 张，authenticator 在 V8 DROP 退场；+industry_benchmark） |
| Repositories | 28 | `repository/*.java` 数（+IndustryBenchmarkRepository） |
| Flyway 迁移 | V1-V30 | V3、V22 跳过（共 29 个版本；V23=单价口径注释，V24=信誉下市标记，V25=配额发放结构，V26=发电基准值种子，V27=报告周期唯一键，V28=碳币发放结构，V29=缺口抵扣字段，V29_1=证书编号唯一索引前的历史重号去重，V30=证书编号唯一索引与信誉事件复合索引） |
| i18n 词条 | 2272 | zh-CN 与 en-US 严格镜像（`scripts/gates/i18n-verify.mjs` 实测；2026-11-03 Phase 7 生命周期与核证弹窗文案：+12） |
| 前端 API 模块 | 23 | `src/api/*.ts`（不含 request.ts 与 index.ts；2026-11-01 新增 tradeAudit / regulatoryFinding） |
| 枚举 | 10 | `enums/*.java`（含新增 AllocationStateEnum；`doc-verify.mjs` 的 expectEnums 已同步） |
| 角色 | 4 | ENTERPRISE/REVIEWER/THIRD_PARTY/ADMIN |
| JaCoCo 门槛 | 95% | PACKAGE 级 |
| 上链触发者 | REVIEWER（系统自动） | 方案 A：审核通过即自动上链，见 §12 |

## 12. 角色职责定稿（方案 A · 2026-10-21）

以 `docs/raw` 原始需求为最高依据，消除历史多重表述：

| 角色 | 核心职责 | **不做什么** |
|------|---------|-------------|
| ENTERPRISE | 填报核算、RSA 签名、发起交易与挂单 | 不审核、不上链 |
| REVIEWER | 审核碳报告与项目；**审核通过即触发系统自动上链** | 不管理用户、不签发证书 |
| THIRD_PARTY | 宏观监管大屏、**穿透式只读核查**、定期核查、下市处罚 | **不参与上链审批**、无任何业务写权限 |
| ADMIN | 用户管理、准入/资格证**签发与吊销**、系统配置、网络运维、**链上对账与死信重放** | 不代行上链（仅保留兜底补救）；**不修改链上数据**（账本不可改） |

**依据**：
- L1 原始需求：「当数据审核员点击提交碳排放报告后，**系统后台自动将该报告进行上链**」
- L1 原始需求：第三方监管「作为特殊的参与者角色，**进行数据的定期核查**」
- L2 设计规格书 §3.1：第三方监管＝「监管碳报告和交易数据，**查看**统计信息，**定期核查**」

**端点归属**：
- `POST /carbon/review`（REVIEWER）：审核通过 → 自动上链（主路径）
- `POST /carbon/certify`（REVIEWER|ADMIN）：**幂等补救入口**，处理自动上链失败的重试与审核后驳回

### 12.1 证书门禁（2026-10-31）

准入证书与审核资格证**不再是展示字段**，而是真正生效的业务约束：

- 唯一判定点：`CertificateGateService`（有效 = `status=1` 且有效期为空或 >= 今天；**无证书即拒绝**）。
- 已接入：P2P 成交确认（双方）、拍卖挂单与撮合结算、碳报告提交、审核员审核、碳币充值。
- 错误码：`7001 ADMISSION_REQUIRED` / `7002 QUALIFICATION_REQUIRED`。
- 吊销留痕：`revoked_at` + `revoke_reason`（V19 迁移，可选 `?reason=` ≤200 字）。
- **不阻断登录与只读浏览**：吊销后果应当让人看见（横幅 + 写操作被拒），而不是把人锁在门外看不到原因。

**前端表现**（2026-11-02 补齐）：

| 层 | 实现 |
|---|---|
| 状态源 | `composables/useCertificateStatus.ts`（模块级单例，判定口径与后端一致：`status=1` 且有效期为空或 ≥ 今天，含当天） |
| 全局横幅 | `components/AdmissionStatusBanner.vue`，挂在 `layout/index.vue` 的 `el-main` 内、`router-view` 之前，仅 ENTERPRISE 角色、且无有效证书时显示；区分「从未签发 / 已吊销 / 已过期」三种文案，已吊销时展示证书编号、吊销时间、吊销原因 |
| 写操作禁用 | 交易大厅「发起挂单」、P2P「发起转让」按钮 `:disabled="!admitted"` + tooltip 说明原因 |
| 提交拦截 | 填报页三条提交路径（单条提交 / 批量送审 / 表单提交）前置 `ensureAdmitted()`；**创建草稿不拦**（草稿不产生对外证据） |
| 错误码透传 | `api/request.ts` 对 403 中的 `7001/7002` 透传后端文案，其余 403 仍用通用「无权限」，避免用户只看到笼统提示 |

**实机验证**（2026-11-02，真实后端 + 真实 Fabric）：吊销后横幅显示「准入证书已被吊销 / 证书编号 EA-20261102-511625 / 吊销时间 2026-11-02 11:23:12 / 吊销原因 连续三期报告数据不合规」，交易大厅与 P2P 创建按钮 `disabled=true`，填报页横幅可见；重新签发后横幅消失、按钮恢复可用。

### 12.2 链上「还原」的边界

Fabric 账本只读追加，系统**不提供任何改写链上数据的操作**。所谓「还原」只有两种：

| 层级 | 含义 | 入口 |
|------|------|------|
| L1 补偿 | 死信重放，让本该上链却没上的记录重新投递 | `POST /admin/outbox/{id}/replay` |
| L2 对账 | 读回链上快照与本地记录比对，输出差异分类 | `GET /admin/blockchain/reconciliation` |
| L3 更正 | 追加一条 CORRECTION 记录（需链码升级） | **未实现**，需 chaincode v3 + sequence bump |

> ⚠️ 角色权限语义由 `node scripts/role-matrix-verify.mjs` 自动断言，改代码后必跑。

> ⚠️ 以上数字由 `scripts/doc-verify.mjs` 自动断言。**改代码后数字变化 → 先跑断言 → 同步本表与对应活文档 → 再提交。**
