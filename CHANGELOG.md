# Changelog

All notable optimizations to the OAISS CHAIN platform are documented here.

## [1.5.2] - 2026-09-10

### 核心链路细究深磨：碳核算与 Fabric 存证全流程体验与密码学语义重塑
- **企业填报（Step 1）**：`CarbonUpload.vue` 新增国家标准推荐排放源快捷选择（无烟煤/烟煤/天然气/汽油/柴油/网电等），一键带入标准系数。
- **送审确权（Step 2）**：`CarbonUpload.vue` 提交送审升级为数字签名安全确认卡片，直观展现 RSA-2048 算法、密钥编号 `#79` 与防篡改法律效力说明。
- **第三方核查（Step 3）**：`AuditList.vue` 驳回操作提供 4 个一键合规驳回原因标签（凭证不符/因子偏低/缺少发票/周期冲突），集成信用扣减警示。
- **行政认证（Step 4）**：`VerifyList.vue` 认证弹窗升级为超级账本凭证卡预览，明确展示通道 `mychannel`、链码 `carbon-chaincode` 与共识不可篡改警示。
- **区块链查验（Step 5）**：`Blockchain.vue` 交易查验结果顶部集成 `📌 区块链存证小百科`，以通俗大白话解读哈希抗篡改数学原理。
- **依赖安全治理**：升级 `vitest` / `@vitest/mocker` 4.1.5 → 4.1.11，清零官方 npm audit 中存在的 1 处路径穿越漏洞（`GHSA-82fw-gwwq-j7x9`）。
- **门禁守候**：`i18n-verify.mjs` 扩充校验至 1213 键零漂移，vitest 336 用例全绿，vue-tsc 0 错误。

## [1.5.1] - 2026-09-10

### 验收标准体系建立 + 按标准全量摸底（38 条：36 PASS / 2 PARTIAL / 0 FAIL）
- **新增验收标准**：`docs/质量保证/体验验收标准与报告-2026-09-10.md`
  —— A 构建静态 / B 语义视觉 / C 表单 / D 国际化 / E 联动闭环 / F 响应式可达 / G 数据鲁棒 / H 文档 八维度 38 条可判定标准 + 判定规则 + 域差异白名单
- **新增自动化门禁**：`scripts/i18n-verify.mjs` —— i18n **键引用反向校验**（键集合对齐检查无法发现"两侧同缺"），
  977 处代码引用 ↔ 1179 键双向核对；首跑即抓出 6 个缺失键与 1 处结构性错位

### 缺陷修复
- **UX-122（P1）**：通知功能无 UI 入口——后端落库/推送/铃铛组件/WS 组合式函数齐全，但铃铛无任何挂载点 → 挂载到布局头部，徽标实测 34 条未读
- **UX-125（P2）**：未读徽标恒空——WS 卡在 CONNECTING 时轮询兜底永不启动 → 改为「启动即取计数 + 先起轮询，WS 成功再停轮询」
- **UX-124（P1）**：`notification.*` 六键 zh/en 同缺（组件一挂载即显示键名）→ 补齐并固化为门禁
- **UX-123（P2）**：原生 WS 与后端 SockJS/STOMP 协议+路径双不匹配 → 默认关闭实时通道（`VITE_NOTIFICATION_WS` 开关）+ 文档化两套对齐方案，消除每次加载的 WS 失败噪音（控制台 error 0）
- **UX-126（P2）**：en 界面交易域直出后端中文状态/类型 → 新增 trade 域 `status-meta` + `tradeTypeLabel()`；报告空态文案校正

### 验证
- vitest **336 passed**（68 文件；新增 StatusTag 5 例 + 通知通道 3 例行为断言，替换 9 条"仅断言字面量"空测）
- vue-tsc **0 error**；build ✓；`i18n-verify` 1179=1179 全通过；`doc-verify` 全绿
- 真实浏览器全量：27 路由（说明/控制台/异常文本/术语ⓘ）、en 模式 27 路由、375px 26 路由 + 768/1440 12 项零溢出、暗色 4 页 0 低对比、越权 3 例被拦、分页边界与空搜索正常

### 登记待办
- **UX-123** 实时推送对齐（方案 A：前端 @stomp/stompjs + sockjs-client；方案 B：后端补原生 WS handler）
- **UX-127** 碳币流水备注为后端中文 prose（建议后端存类型 + 前端渲染）

## [1.5.0] - 2026-09-10

### ELI5 新手体验改造全量落地（逐页面/表单黑盒深挖 + 多端一致性）
- **P0 排放数据结构化表单**：`CarbonUpload` 双模式（范围一/二/三动态行 + 自动小计/总计 +
  负值拦截；高级 JSON 自动生成并保留）——替代纯 JSON 粘贴，后端契约零改动
- **P0 状态三重编码**：新增 `utils/status-meta.ts`（单一色板/图标/i18n）+ `components/StatusTag.vue`，
  报告域 6 页统一 📝⏳🔍✅❌⛓️，项目域 7 态含 ⏸️，交易/证书/用户域补图标
- **P1 术语即时翻译**：新增 `components/TermTip.vue` + 22 条术语表（zh/en 同键），
  公式页/碳核算/区块链/个人中心等挂载 ⓘ
- **P1 页面白话说明**：新增 `components/PageHint.vue`，15 页接入「这一页帮你做什么」
- **P1 空态引导**：新增 `components/EmptyHint.vue`（证书管理/核证队列/碳币流水空态 CTA）
- **P2 首访 3 步引导卡片 + 成功 Toast 下一步指引**（创建/提交/挂单/审核/批量认证）
- **P3 公式页 25 参数白话化**：分组填法提示 + FC/NCV/CC/OF ⓘ

### 缺陷修复
- **UX-101（P1）**：管理端「碳核算管理」状态色板错位（审核通过显红/审核中显绿/已上链显灰）→ 域化统一
- **UX-102（P2）**：碳中和项目「已终止」跨页两色（danger/info）→ 统一中性 info
- **UX-103（P2）**：英文界面下报告状态直出中文 → StatusTag 走前端 i18n（实测 ⛓️On Chain 等）
- **UX-120（P2）**：历史报告标题 GBK 乱码 → 备份表 + 定向清洗 30 行（剩余 0）
- **UX-121（P3）**：区块链浏览器加载期显示「暂无数据/共 0 条」→ 加载期文案改「加载中...」

### 验证（多角色真实浏览器闭环）
- 企业结构化建档 5400 tCO2e → 提交 → 审核通过 → 认证上链（tx `5d041bc8…` 块 #16）→
  第三方监管面板/区块链浏览器/管理端总账全端一致可见
- vitest **337 passed**（+2 结构化表单回归）；vue-tsc **0 error**；build ✓；
  i18n **zh=en=1168** 零漂移；`doc-verify.mjs` 全绿（143 端点）

## [1.4.0] - 2026-09-08

### 工程治理五轮（09-07~09-08，脚本审计→UI 审计→遗留闭环→类型债→依赖面）
- **脚本/CI/Fabric/ML 十一轮闭环**（c93cf34~b706c1a）：25 脚本 24P/0F；BUG-05 Fabric
  区块/交易列表永久空修复（事件流断流→逐块短流探测）；全角色 26 路由穷尽探查
- **Phase26 UI 审计全量修复**（967514a/1e353b1）：表格页溢出清零（实测 1440/375
  零文档级滚动）；审核前验签分流（待审列表 signatureValid 三态列 + 篡改「通过」
  二次确认闸门 + 决策单 toast）；主色统一 #14a79a；i18n zh=en=1059 零漂移
- **遗留闭环**（d626b03/0625453/6c37ec9/2f0475b）：CORS 测试根因=test-classpath
  同名遮蔽；DEBT-03 关闭；compose backend healthcheck
- **DEBT-04 清零**（57f9577/40e7123/1c3b6da）：vue-tsc 397→0；4 处字段实源化真修复
  （VerifyList 恒 normal / ReviewHistory / dashboard assetNo / CarbonNeutral 数值提交）
- **依赖与升级**（c013356/4051c7f/f1b755d/e9b0d57）：npm audit fix 10 包 42 advisory
  全清（0 vulnerabilities）；Spring Boot 3.2.5→3.2.12（3.2 线 EOL 末版安全收口）；
  测试基建两预置缺陷修复（integration 套件转绿）；e2e 报告路径 basename 白名单

### 排期中（详见 docs/接口参考 与 .planning/debug 记录）
- @DataIsolation 数据隔离缺口（Carbon/Trade/CreditScore/CarbonCoin 4 控制器）
- FabricBlockchainServiceTest 20 例漂移调查；Spring Boot 3.5 LTS 迁移
- Mimosa i18n 文案键结构性误报（工具侧治理，hooks 已禁用待重配）

## [1.3.0] - 2026-09-06

### v2.5 / v2.6 验收与全功能落地（Phase 19-30）
- **FUNC-11 / FUNC-13**：P2P/拍卖交易通知 + 撮合结果引导（51ecc73 / 31dc4af）
- **通知/证书/性能监控入库**：前端通知中心 + 后端证书 + WebVitals 端到端（d093263 / 3e770d8）
- **57 处审计占位符语义化**：ENDPOINT_ → action 级语义命名（7f31663）

### 缺陷修复（2026-09-05 E2E 会话）
- **BUG-01**：转账表单 validate 失败加兜底提示（64fb5a4）
- **BUG-02**：en-US carbonCoin 块补 4 个缺失 i18n keys（0cfcca0）
- **BUG-03**：碳排放活动数据/因子负数校验 + 防回归单测（f2a99f4 / d2c595e）
- **BUG-04**：补定义 blockchainExecutor 线程池（P0 回归，4b08c7b）

### 文档驱动体系（2026-09-02）
- **DEV-GUIDE 真相源 + docs 归档 + 覆盖矩阵**：文档驱动开发体系建立（d75774a）
- **doc-verify.mjs 入库**：文档-代码零漂移断言脚本（cea46f4）
- **文件治理**：API_DOCUMENT 归档、gitignore 豁免修正、三车道分析报告（6bcf19f / bb888e2 / 261a3ed）

### 测试与工程
- **E2E 全功能用例集 + 执行报告**：真实浏览器 computer-use 执行（b597433）
- **前端 API 测试入库**：修正 8 个过时断言（bb56f2d）
- **@WebMvcTest 基础设施修复**：ratelimit 条件化（fac88d6）
- **CI/ML/部署配置入库** + chaos/load 测试资产（fb434c0 / 5391b85）

### 工作区治理（2026-09-06，6823cb1）
- 垃圾清理：nul/bak/孤儿 .m2(178MB)/target/旧日志回收 ≈298MB
- gitignore 补 .mimosa/nul/毕业设计模版；截图 504 文件归档；文档索引全面更新

## [1.2.0] - 2026-06-24

### Infrastructure & Deployment
- **K8s Deployment Manifests** (13 files): Deployment, Service, HPA, PDB, Ingress for backend/frontend/ml-service
- **Monitoring Stack**: Prometheus + Grafana (16 panels) + Zipkin distributed tracing
- **Docker Compose Variants**: infra-only, prod, release, monitoring overlays
- **Service Worker**: Offline capability with Cache-First/Network-First strategies
- **Web Vitals Monitoring**: LCP/FID/CLS/FCP/TTFB collection with backend endpoint

### Security
- **8 Security Headers**: CSP, HSTS, X-XSS-Protection, X-Frame-Options, X-Content-Type-Options, Referrer-Policy, Permissions-Policy
- **Per-Role Rate Limiting**: Redis Lua-based (ADMIN=200, REVIEWER=80, ENTERPRISE=60, THIRD_PARTY=40, ANONYMOUS=10/min)
- **API Versioning Strategy**: ADR-001, X-API-Version response header
- **CORS Hardening**: 8 exposed headers for frontend access

### Performance
- **Backend**: N+1 query fixes, 12 composite indexes, 9 @Cacheable, 11 @CacheEvict, 12 @Transactional(readOnly)
- **Frontend**: KeepAlive (9 pages), route prefetch (3 critical), lazy loading, chunk splitting (5 vendor chunks)
- **Build**: 1.57s average build time

### Testing
- **Frontend**: 190/190 unit tests, 100% page coverage
- **Backend**: 138 test files, security/contract/integration tests
- **Load Testing**: k6 smoke + stress + resilience scripts
- **Chaos Engineering**: 10 resilience scenarios (health, ratelimit, auth, security, idempotency)
- **E2E**: Playwright smoke/flow/v1.1 tests

### Documentation
- **132+ Markdown Files**: Architecture, DB tuning guide, production runbook, API reference, ADR
- **Swagger/OpenAPI**: 24 controllers, 178+ @Operation, 120 @ApiResponse
- **i18n**: 1081 zh-CN keys, 1070 en-US keys, 0 hardcoded strings

### Monitoring & Observability
- **Grafana Dashboard**: 16 panels (business + system + endpoint metrics)
- **Distributed Tracing**: Micrometer Brave + Zipkin
- **Custom Metrics**: Prometheus counters/gauges for carbon reports, trades, users
- **Response Headers**: X-Trace-Id, X-Response-Time, X-API-Version, X-RateLimit-*

## [1.1.0] - 2026-05-22

### Features
- Carbon neutral project lifecycle management
- Digital signature (RSA) for report attestation
- Double auction trading with matching engine
- P2P trading with credit score integration
- AI market prediction and enterprise inference
- Blockchain integration (Hyperledger Fabric)
- Enterprise admission and reviewer qualification

### Quality
- 190 frontend tests, 137 backend test files
- 0 hardcoded Chinese strings
- 70 aria-labels for accessibility
- WCAG 2.1 AA compliance

## [1.0.0] - 2025-12-28

### Initial Release
- Carbon accounting module (smart calculation + data review)
- Carbon trading module (double auction + P2P)
- Search and query module
- Admin management module
- User login module
- AI intelligent module (market prediction + enterprise inference)