# Changelog

All notable optimizations to the OAISS CHAIN platform are documented here.

## [1.7.1] - 2026-10-31

### 碳币转账功能移除（需求溯源裁决 + TDD 闭环）

**背景**：「账户间转账」在原始需求 docx 中 0 命中（实现层自造），全仓唯一调用方是 Controller（拍卖/P2P/信誉分零依赖），且存在三项质量缺陷（`counterpart_id` 死列从未写入、txNo 时间戳+随机数非幂等、分布式锁只锁发起方），与撮合引擎防洗售语义矛盾。按 TDD 红先行执行深度移除。

- **后端**：删除 `POST /carbon-coin/transfer` 端点、`CarbonCoinService.transfer()`、`TX_TYPE_TRANSFER`、`CarbonCoinTransferRequest` DTO 与实体 `counterpartId` 字段；新增 404 契约测试。数据库 `counterpart_id` 列保留（nullable 无写入路径），`tx_type=4` 作为历史转账遗留值不再产生新数据。
- **前端**：移除碳币账户页转账按钮/对话框/API/类型与 15 个 i18n 键（新增 `txTypeLegacy`=历史转账，zh/en 镜像）。
- **驱动型修复**：流水显示映射与后端 txType 语义全面错位（购买显示「转入」、出售显示「转出」，正负号与颜色全反）——对齐为 1=充值/2=购买/3=出售/4=历史转账，正号集 [1,3]。
- **测试资产**：system-suite TC-EQ-014/TC-BVA-004 改 404 契约（005-007 删除）、client.mjs mock 404、COIN 脚本改单检查、e2e 回归流 404 契约与 smoke S8-04 改「无转账入口」断言、coverage 清单除名。
- **附带修复**：`carbon_coin_transaction.tx_type` 物理列漂移（tinyint→INT，对齐 V1 建表与实体；该漂移曾令一切新启动在 Hibernate schema 校验处失败）。
- **实机回归**：GUI 黑盒 T1-T6 全过（转账入口零残留、历史转账行正确显示、0 console 错误）；转账端点实测 404；flow e2e 2/2、v1.1 404 契约实测通过；vue-tsc 0 错；doc-verify/i18n-verify 零漂移。

## [1.7.0] - 2026-10-31

### 证书门禁落地 + 区块链数据面归真 + 查询口径收敛

**背景**：四项问题均非「功能缺失」，而是「声称的能力实际不生效」或「展示的内容不是真的」。本轮把它们逐项钉成可验证的事实。

#### 一、企业证书吊销真正生效（安全控制面）

改造前，管理员点「吊销」只把 `enterprise_admission.status` 置 2，而**全仓没有任何业务服务读取该字段**——吊销是孤岛写入。企业照常登录、挂单、成交、提交报告、充值碳币；审核员照常审核；与此同时 i18n 文案写着「没有准入证书就不能交易」。

- **V19 迁移**：两张证书表补 `revoked_at` / `revoke_reason`；对存量 ACTIVE 证书回填 `expiry_date = 签发日 + 1 年`（该列此前恒为 NULL），否则新门禁启用当日全部存量证书会被判为过期而集体失效。
- **`CertificateGateService`（新）**：唯一判定点。有效 = `status=1` 且（有效期为空 或 >= 今天，到期日含当天）。**无证书即拒绝**，刻意不复制信誉分闸门的「无记录默认放行」——那等于把缺数据当成合规。
- **接入 5 类写入口**：P2P 成交确认（交割前判双方）、拍卖挂单与撮合结算（跳过不合规配对而非整批中断）、碳报告提交、审核员审核（资格证）、碳币充值。
- **错误语义**：新增 `7001 ADMISSION_REQUIRED` / `7002 QUALIFICATION_REQUIRED`，带可读中英文案。
- **吊销留痕**：`DELETE /admin/{enterprise-admission|reviewer-qualification}/{id}` 支持 `?reason=`（≤200 字），同时写入吊销时间；前端二次确认改为 `prompt`，给一次填写原因的机会。
- **顺带修正**：`confirmTrade` 原先「先改 PROCESSING 再解析企业」，任一步失败就把交易停在半途；已改为先校验后改状态。`getMyCertificate` 原先无排序而前端取 `list[0]`，重新签发后会误显示「已吊销」——已改为按创建时间倒序。

#### 二、区块链数据面归真

- **删除 4 个恒空列**：区块表「矿工地址」、交易表「发送方 / 接收方 / 金额」后端从不产出，长期显示 `-`。
- **`status` 不再写死**：改为读取区块元数据 `TRANSACTIONS_FILTER`（0=VALID，非 0=INVALID），并输出 `validationCode`。
- **补真实字段**：交易发起组织 `senderMsp`（签名头）、背书组织 `endorsers`（背书列表）、被调用的链码函数与入参 `function`/`args`/`chaincodeName`；区块补 `previousHash` 与 `channel` 列。
- **区块下钻**：`GET /blockchain/block/{n}` 早已存在且返回块内交易，但前端从未调用——文档宣称的「逐层下钻」实际不存在。现补区块详情弹窗。
- **新增 `GET /blockchain/report/{reportId}`**：调链码 `QueryReportHistory` 读回 v2 快照。链上 16 个字段此前**没有任何 HTTP 端点能读到**。

#### 三、交易哈希查询口径收敛

`Blockchain.vue` 内嵌的「输入哈希直接查询」面板被移除（连同后端 `GET /blockchain/transaction/{txHash}`、两个实现、`TX_QUERY_FAILED`、9 个 i18n 键、8 处测试）。链上交易统一从「交易记录」列表与区块下钻查看，避免同一事实存在两条查询口径。顺带补上了该端点此前**缺少 `@PreAuthorize`**（任意登录用户可查任意 txHash）的问题——端点已不存在。

#### 四、管理员链上管理（对账 + 死信）

- **新增 `/admin/blockchain` 页**（管理员此前**没有任何**区块链入口，路由守卫甚至会把 ADMIN 从企业端浏览器重定向走）。
- **链上对账** `GET /admin/blockchain/reconciliation`：四类结果（一致 / 链上缺失 / 内容不一致 / 无法判定）。链上不可达时归类 `UNVERIFIABLE` 而非「缺失」——避免一次网络故障变成一页虚假差异。
- **死信管理** `GET /admin/outbox/dead-letters` + `POST /admin/outbox/{id}/replay`：此前 outbox 转 `DEAD` 后**没有任何查询入口**，消息永久滞留且无人可见。重放只接受 DEAD 状态，避免在途消息重复提交。
- **不提供改链操作**：Fabric 账本只读追加，「还原」的真实含义只有对账与补偿。给一个能改链的按钮只会制造假象。

#### 五、验证

- 后端全量测试通过；前端 441 项通过；`vue-tsc` 0 错误；`doc-verify` / `i18n-verify` 零漂移。
- 端点 150→153，i18n 2126→2180，Services 44 主包 / 47 合计。

## [1.6.0] - 2026-10-30

### 全链路闭环修复 + 实机验收 + 测试基础设施治理

**背景**：逐页面×角色的闭环检阅发现，多个「文档声称已实现」的能力在界面上没有入口，流程走到某一步即永久停滞。本轮接通 5 条断链，并在真实环境（MySQL/Redis/MinIO/Fabric 全栈）逐条实机验收。

#### 一、碳中和项目生命周期打通（三处断链）

后端状态机完整，但前端缺三个入口：

- **审核入口缺失**：`POST /carbon-neutral/{id}/review` 前端零引用 → 企业提交后永远停在 `status=1`。
  审核员端 `ProjectReview.vue` 扩为**双 Tab**（待审核 / 待核证），「待审核」每行提供通过/驳回。
- **申请核证入口缺失**：企业端无按钮把 `verificationStatus` 推到 1 → 审核员核证工单池恒为空。
  `CarbonNeutral.vue` 与 `CarbonNeutralDetail.vue` 新增「申请核证」（弹窗录入核证机构 ID）。
- **完成认证入口缺失**：`POST /{id}/certify` 前端零引用 → `certStatus` 永远到不了 2。
  「待核证」Tab 增设「已核证待认证」区块，录入证书编号完成签发。
- **修正逻辑错位**：`canCertify` 原按 `status===3` 判断，后端要求 `verificationStatus===2`，点击必报 3003。
- **修正契约缺陷**：三个 api 包装器不传请求体，与后端 DTO 的 `@NotNull` 冲突，调用必 400。

**实机验收**：项目 0→6 全链路 8/8 步通过，终态 `status=4 / verificationStatus=2 / certStatus=2`。

#### 二、附件三重断链（安全相关）

- **权限注解自相矛盾**：`/file/download` 与 `/file/presigned-url` 注解写死 `ENTERPRISE, ADMIN`，把 REVIEWER 挡在外面；而同类 `checkDownloadPermission()` 内部早已写好 `userType==2/3` 放行分支——**那段代码永远不可达**。注解改为四角色，属主/归属校验与 Fail-Closed 语义全部保留。
- **存的是不可访问地址**：上传落库的是 MinIO 无签名直连 URL（桶私有，打开必 403）。改为同时保存 `objectName`，保留 `url` 兼容历史数据。
- **审核弹窗无附件区块**：仅用布尔值生成一句文案，审核员看不到凭据却被引导点「通过」。新增附件区块：文件名、大小、SHA-256，下载走预签名链接；老数据从 URL 反推 `objectName`。

**实机验收**：预签名 URL 含 `X-Amz-Signature`，真实 GET 返回 200 + 文件内容；审核员访问 `/file/list` 仍 403。

#### 三、信誉分接入交易链（原为「只写不读」死数据）

- **前置风控闸门**：`TradeService` 与 `DoubleAuctionService` 四个挂单入口接入 `checkTradePermission`。
- **闸门必须先于自成交检查**：否则冻结账户会先收到「挂单方向冲突」这类次要提示。
- **同价信誉优先**：撮合保持价格优先，仅同价位时以信誉分降序为次级键；查不到按中性 50 处理。

**实机验收**：信誉压至 0（FROZEN）后，买卖双向挂单均返回 `4012`。

#### 四、监管交易穿透 + 市场搜索匿名化

- **监管端原本看不到任何交易**：不是权限问题（`TradeController` 早已放行 THIRD_PARTY），是产品层没接线。
  `ThirdPartyService` 新增交易笔数/总量/总额聚合；`Monitor.vue` 新增只读交易流水面板（含上链哈希与完成时间）。
- **需求 3.3.2 匿名化零实现**：`SearchService.searchTrades` 按角色分流——ADMIN/THIRD_PARTY 保留实名，
  ENTERPRISE/REVIEWER 返回 `匿名企业-<8位HEX>`（SHA-256(固定盐 + enterpriseId)，跨记录恒等、同笔交易两侧不同）。
- 新增通用搜索页 `/search`（四角色可访问，双 Tab），四角色菜单与图标栏同步配齐五字段。

**实机验收**：同一笔交易，企业看到 `匿名企业-4D088839`，监管看到 `孙六`；监管面板 81 笔、137,506 tCO₂e、191,526.01 元。

#### 五、AI 输出诚实化

- **删除伪造兜底**：ML 服务 `total<=0` 时凭空填充 `370.50/250.50/120.00/22.00`，改为 `insufficient_data=true` + 原因说明。
- **审核页「AI 预审」是硬编码假结论**：本地三元表达式拼出「AI 建议通过」却从未调用模型。改为诚实的「**规则预检（非 AI 模型）**」并附免责说明。
- **LLM 配置显式化**：`llm_api_*` 从 `getattr` 兜底提升为 `config.py` 正式字段，未配置即标注走内置规则引擎。

#### 六、文档归一

- i18n 计数在四份活文档里为 1441/1826 等多个值，统一到脚本实测 **2039**。
- 架构规格书端点数字 146/139/134 三方并存，统一到 **148**。
- 补 3 处状态机/权限矩阵事实说明（`UNDER_REVIEW` 不可达、`FAILED` 从不写入、两行权限与 `@PreAuthorize` 不符）。

#### 七、测试基础设施治理

| 缺陷 | 影响 | 修复 |
|---|---|---|
| `db-config.sh` 路径少一级 | 4 个脚本直接退出 | `$SCRIPT_DIR/../db-config.sh` |
| `aop-test.sh` 的 `WORK_DIR` / `start-backend.sh` 路径错 | 无法定位源码，误停后端 | `../..` 与 `../start-backend.sh` |
| `edge-test.sh` 的 `WORK_DIR` 少一级 | i18n 检查恒失败 4 项 | `../..` |
| `double-auction-test.sh` 的 `SERVICE_FILE` 少一级 | `@Transactional` 检查恒失败 | `../../` |
| 全部 23 个脚本假设「登录无需验证码」 | 默认配置下必然登录失败 | `.env.example` 补 `SECURITY_CAPTCHA_ENABLED` 并文档化 |
| `carbon-report-test.sh` 断言审核后 `status==3` | 方案 A 自动上链后为 5 | 接受 3 或 5 |
| `double-auction-test.sh` 未重置碳币余额 | 买入挂单因余额不足 4001 | 补余额重置 |

**API 集成测试最终结果**：22/23 脚本全绿，累计 220 项断言通过。

#### 门禁与验证

- `doc-verify.mjs` ✅ 16/16（148 端点 / 23 Controller / 25 Entity / 24 表 / 2039 i18n）
- `role-matrix-verify.mjs` ✅ / `i18n-verify.mjs` ✅
- `vue-tsc` ✅ 0 error / `vitest` ✅ **380 passed**
- 前端生产构建 ✅ 2.13s / 后端 `package` ✅ 117 MB jar
- ML `pytest` ✅ **46 passed**
- `closure-audit.mjs` ✅ 结构性完整 / `validate-prod-env.mjs` ✅

**已知遗留**：`CarbonNeutralProjectControllerTest` 两个用例与 `BusinessScenarioTest` 集成用例在**基线即失败**（已用 stash 复跑验证），与本轮改动无关，需另行修夹具。

## [1.5.3] - 2026-10-28

### 双向拍卖 CDA 自动撮合闭环（闭合缺陷 C-2 / D-03）

**问题**：撮合引擎是「批量集合竞价」，必须有外部时钟驱动，但项目里 `POST /auction/match` 是唯一出口且仅 ADMIN 可调、管理员端又没有对应页面。结果是企业挂单后**永远不会成交**，`TradingMarket.vue` 的「等待下次撮合」文案实际在承诺一个不存在的机制。文档《系统设计与架构规格书》§4.5.1 却写着「系统自动撮合匹配」，与实现不符。

- **新增自动撮合时钟**：`AuctionMatchingScheduler` —— `@Scheduled` 每 30 秒调用 `executeMatching()` 出清一轮，`initialDelay` 60 秒避开启动预热。与手动端点共享 `@DistributedLock(key='auction:matching')`，多实例/并发触发天然不会重复结算。
- **系统身份审计**：定时触发无 HTTP 请求，`@AuditLog` 切面不生效，故调度器在**真正产生成交时**手工落一条 `OperationLog`（`SYSTEM-AUCTION-MATCHER`，含笔数与累计成交量）；无成交不写，避免每 30 秒刷屏。
- **新增管理员撮合控制台** `/admin/trading/matching`（`views/admin/AuctionMatching.vue`）：运行态状态条（自动撮合开关 / 出清周期 / 买盘卖盘待撮合数 / 上次成交时间，15 秒自刷新）+【立即撮合】手动补触发 + 全站成交明细表。
- **新增端点**：`GET /auction/matching-status`（运行态）、`GET /auction/all-results`（全平台成交明细，ADMIN；企业侧 `/auction/results` 仍只返回本人成交，不暴露交易对手）。端点总数 146 → 148。
- **文案纠偏**：企业交易大厅新增「平台每 30 秒自动撮合」提示条，`matchGuideText` 改为说明自动周期；成交通知补注「系统自动撮合」。
- **测试**：后端新增 `AuctionMatchingSchedulerTest`（8 例：定时注解存在性、开关生效链路、审计聚合量、锁竞争与异常兜底）+ `DoubleAuctionControllerTest` 补 2 例新端点；前端新增 `AuctionMatching.test.ts`（5 例：挂载加载、成功/空/失败三态、连点防抖）。
- **门禁**：doc-verify 148 端点 / 38 Service / 1731 i18n 全绿；role-matrix-verify 全绿；style-guard 0 violation；vue-tsc 0 error；vitest 361 passed；后端拍卖域 67 tests 全绿。

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