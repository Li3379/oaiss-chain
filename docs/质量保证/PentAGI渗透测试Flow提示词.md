<div align="center">

**OAISS CHAIN — ENTERPRISE DUAL-CARBON BLOCKCHAIN PLATFORM**  
### 双碳链动系统 · 安全渗透测试作战任务书
#### PentAGI 灰盒渗透测试任务流、漏洞假设与安全加固验证

<br>

# PentAGI 渗透测试 Flow 任务书
### （OAISS CHAIN · PENTEST FLOW SPECIFICATION & WORKBOOK）

<br>

| 项目 | 内容详情 |
| :--- | :--- |
| **作品名称** | OAISS CHAIN 双碳链动系统 |
| **文档版本** | v2.2 (规范发布版) |
| **文档定位** | 面向 PentAGI 智能体执行全系统 6 大安全域渗透测试的任务蓝图 |
| **执笔团队** | OAISS CHAIN 安全攻防与红蓝对抗组 |
| **审核人员** | 首席架构师 & 安全总监 |
| **所属单位** | 软件学院 & 双碳区块链工程中心 |
| **完成日期** | 二〇二六年十月 |

</div>

---

### 文档修订控制记录

**表 0.1 渗透测试任务书修订历史记录表**

| 编号 | 变更状态 | 修订日期 | 执笔人 | 审核人 | 批准人 | 变更摘要与说明 |
| :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| 1 | 文档创建 | 2026-10-19 | 安全组 | 架构师 | 委员会 | 编排 6 大安全域灰盒渗透测试 Flow 与漏洞假设 |

---

> 用途：整段粘贴到 PentAGI 新建 Flow 的 Task 输入框。  
> 授权前提：本项目为本人所有、仅在本机 Docker 开发环境运行。测试结束即销毁。  
> 版本：2026-10-19；基于对 backend / frontend / ML / compose 的源码审查与实机探针预置。  

---

## 一、任务目标（Objective）

对 OAISS CHAIN 双碳链动系统执行**灰盒渗透测试**：目标不是堆砌扫描器输出，而是验证一套已从源码推断出的具体漏洞假设，并补充业务逻辑漏洞。

最终交付一份可复现、可按 CVSS 3.1 定级的漏洞报告，每条漏洞必须包含：可复制的请求、真实响应、影响说明、修复建议（指出具体文件与行号级别的根因）。

判定成功 = 至少完成下列 6 类验证并给出结论：
1. 认证与会话（验证码可绕过性、Token 失效、暴力破解防护有效性）
2. 越权访问（水平越权 / 垂直越权 / 租户数据隔离）
3. 业务逻辑（碳报告、交易撮合、碳币转账、数字签名的金额与状态机篡改）
4. 文件与对象存储（上传绕过、路径遍历、越权下载）
5. 内部服务暴露（ML 微服务、Actuator、数据库/Redis/MinIO）
6. 加固缺陷（日志泄露、错误信息泄露、CORS/CSP/WS 认证）

---

## 二、目标环境（Target）

所有服务运行在**宿主机**，PentAGI 在 Docker 内，因此**必须使用 `host.docker.internal` 而不是 `localhost`**。

| 组件 | 从 PentAGI 容器访问的地址 | 说明 |
|---|---|---|
| 后端 API | `http://host.docker.internal:8080/api/v1` | Spring Boot，context-path `/api/v1` |
| ML 服务 | `http://host.docker.internal:8001` | FastAPI，无 `/api/v1` 前缀 |
| 前端 | `http://host.docker.internal:5173` | **实测从容器不可达**，需要 API 层测试时直接打 8080 |
| MySQL | `host.docker.internal:3306` | **不在授权范围，禁止连接** |
| Redis | `host.docker.internal:6379` | **不在授权范围，禁止连接** |
| MinIO | `host.docker.internal:9004` / 控制台 9003 | 仅允许通过应用 API 间接测试，禁止直连 S3 |
| Fabric Peer/CA | `host.docker.internal:7051` / `7054` | **不在授权范围** |

**范围外，任何情况下不得触碰**：MySQL 数据文件、Redis 键空间、Fabric 账本、MinIO 裸桶、宿主机文件系统、`docker` 命令、其他容器。

**允许的探测上限**：单目标并发 ≤ 5，请求速率 ≤ 10 req/s；禁止任何 DoS/压测/大文件轰炸；上传测试文件 ≤ 1MB。

---

## 三、测试账号（Credentials）

| 角色 | 用户名 | 密码 | 权限边界 |
|---|---|---|---|
| ADMIN | `admin` | `admin123` | 全平台 |
| ENTERPRISE | `enterprise001` | `admin123` | 仅本企业（enterpriseId=1） |
| ENTERPRISE | `enterprise002` | `admin123` | 仅本企业（enterpriseId=2），**用于构造水平越权对照** |
| REVIEWER | `reviewer001` | `admin123` | 审核 |
| THIRD_PARTY | `thirdparty001` | `admin123` | 监管只读 |

账号说明：
- 登录接口**不强制验证码**（不传 `captcha`/`captchaKey` 即可通过），便于脚本化取 Token。
- JWT 在响应 `data.accessToken`，请求头 `Authorization: Bearer <token>`。
- 需要新账号时，可用 `POST /auth/register` 自助注册，但**只允许注册 userType=1（企业）或 3（第三方）**；不要注册 ADMIN/REVIEWER。注册的临时账号在本轮结束后由管理员禁用，不得留下可用后门账号。

---

## 四、已知攻击面热区（源码审查结论，优先验证，不要重复泛扫）

以下每条都是**已从代码中看到、尚未完整验证利用链**的点。请逐条给出「可利用 / 不可利用 / 有条件可利用」的实证结论，不要只复述代码。

### A. 认证与账号
1. **验证码形同虚设**：`AuthService.login()` 仅在 `captchaKey` 与 `captcha` **同时非空**时才校验（`AuthService.java:52-55`）。验证「完全不传验证码」能否无限登录；并确认图形验证码是否在日志里明文输出（`CaptchaService.generateCaptcha()` 有 `log.info(..., code={})`）。
2. **自助注册提权**：`AuthService.ALLOWED_REGISTRATION_TYPES` 允许 userType=3（THIRD_PARTY，监管角色）自助注册（`AuthService.java:310-313`）。验证注册后能否直接调用 `/third-party/*` 监管接口读取全平台数据。
3. **暴力破解防护可绕过**：限流按 `LimitType.IP_USER`，而 IP 取自可伪造的 `X-Forwarded-For`（`CommonUtils.getClientIp()` 直接信任首段）。验证伪造 XFF 后登录失败次数限制是否失效；同时确认 `/auth/captcha` 或 `/captcha/generate` 无速率限制导致的资源放大。
4. **登出失效**：`tokenBlacklist` 依赖 `CacheManager`，而 `CacheConfig.localCacheManager()` 只声明了 `captcha/users/permissions` 三个缓存名（`CacheConfig.java:35-37`）。验证 Redis 不可用时 `getCache("tokenBlacklist")` 返回 null → 登出后 Token 仍可用。
5. **Refresh Token 重放**：`refreshToken()` 签发新 Token 但不吊销旧 Refresh Token。验证同一 Refresh Token 能否被重复使用直至过期。
6. **账号枚举**：`GET /user/check-username`、`GET /user/check-email` 的鉴权与响应差异。

### B. 越权与租户隔离
7. **对象级越权（已验证部分）**：`GET /user/{userId}` 标注 `@PreAuthorize("isAuthenticated()")`，**无归属校验**。已实测 enterprise001 可读取 enterprise002 与 admin 的用户资料。请确认可读到哪些字段（手机号/邮箱/上次登录 IP），并评估是否可批量枚举。
8. **跨租户报告列表泄露（已验证）**：`GET /carbon/reports?enterpriseId=2` 对 ENTERPRISE 角色开放，实测返回其他企业的报告编号、标题、排放量、状态。请确认字段范围与可否翻页拉全量。
9. **跨租户全局搜索**：`GET /search/reports`、`GET /search/trades` 仅要求登录，未按企业作用域过滤。验证企业账号能否检索到全平台报告与交易对手信息。
10. **详情越权的纵深**：`GET /carbon/reports/{id}` 已有归属校验（`CarbonService.assertEnterpriseOwnsReport`），请验证是否存在绕过路径（如通过 `/search`、`/reviewer`、`/third-party` 的间接读取，或签名/附件接口带出正文）。
11. **写操作越权**：对 `/carbon/reports/{id}/submit`、`/signature/sign`、`/auction/buy|sell`、`/trade/*`、`/carbon-coin/*` 逐个验证是否能以 A 企业身份操作 B 企业资源（重点看是否只依赖请求体里的 id）。

### C. 业务逻辑
12. **报送数据篡改**：碳报告 `totalEmission` 是否允许负数、超大值、负数分项；提交后状态机是否可回退；草稿能否绕过审核直接进入 CERTIFIED。
13. **交易与拍卖**：自买自卖、负价格/零价格、数量为负、超额卖出（余额校验与扣减是否原子）、并发重复下单导致双花。检查是否存在乐观锁/分布式锁（`@DistributedLock`）。
14. **碳币**：转账给不存在的用户、负数转账、并发转账把余额转成负数、转账给自己。
15. **数字签名**：签名是否绑定报告内容与报告 ID；能否把 A 报告的签名复用到 B 报告；`/signature/verify` 是否可被用来做签名预言机；私钥接口是否会把私钥回显。
16. **审核链路**：REVIEWER 能否审核自己企业的报告；驳回后能否重复扣分（信誉分刷负）；`carbon/certify` 批量接口能否对未审核报告强制上链。

### D. 文件与对象存储
17. **上传白名单**：`MinioService.validateFile()` 只做**危险扩展名黑名单**（`.jsp/.exe` 等）与大小校验，不校验 MIME 与文件头。验证 `.svg`（存储型 XSS）、`.html`、双扩展名（`a.jpg.jsp`、`a.jsp.jpg`）、大小写绕过、无扩展名。
18. **越权下载**：`GET /file/download?objectName=` 仅 `hasAnyRole('ENTERPRISE','ADMIN')`，**无所有权校验**。用 enterprise001 的 Token 下载 enterprise002 报告附件（objectName 可从报告详情/列表中获得）。
19. **删除降级**：`FileController.checkDeletePermission()` 在 `fileOwner == null` 时**直接放行**（`FileController.java:459-462`，注释写明向后兼容）。验证能否删除无 owner 元数据的他人文件。
20. **对象名遍历**：`objectName` 是否可传 `../`、绝对路径、其他 bucket 前缀；`presigned-url` 与 `presigned-upload-url` 是否可签发任意对象的 URL。

### E. 内部服务与信息暴露
21. **ML 服务裸奔（已验证）**：`ML_SERVICE_SECRET` 为空时 `require_ml_service_secret` 直接放行，实测 `POST http://host.docker.internal:8001/copilot/policy-qa` 无需任何凭据即返回内容，且 `/docs` 开放。评估：未授权调用 → 算力滥用、模型输入投毒、通过 `/copilot/*` 构造提示注入获取系统提示词或越权数据。
22. **Actuator**：`/actuator/health` 公开、`/actuator/prometheus` 需 ADMIN、其余需认证。验证 health 的 `show-details: when-authorized` 是否泄露依赖拓扑；用 ADMIN Token 拉 `/actuator/metrics`、`/actuator/env` 看是否带出配置键名。
23. **WebSocket 认证缺陷**：`WebSocketAuthConfig` 在 Token 无效时**只打 warning 仍放行 CONNECT**（`WebSocketAuthConfig.java:55-70`）。验证无 Token / 伪造 Token 能否连上 `/ws` 并订阅 `/topic/*` 或 `/user/queue/*`，从而窃听他人通知。
24. **错误信息与日志**：触发 500 看是否回显堆栈、SQL 片段、内部路径；登录失败响应是否区分「用户不存在」与「密码错误」。
25. **安全响应头与 CORS**：核对 `SecurityConfig` 中 CSP 的 `style-src 'unsafe-inline'`、CORS 白名单是否可被 `Origin: null` 或子域绕过；前端是否存在 `v-html`（目前扫描为 0，需确认动态渲染路径）。

### F. 配置与部署
26. **开发默认口令**：`.env` 与种子数据使用 `admin123`、`enterprise001/password123`；compose 中大量 `${X:?}` 已强制注入，但本地 profile 回落到弱默认值。评估生产 profile 的 `SecurityStartupValidator` 是否真的能拦住弱密钥。
27. **端口暴露面**：MySQL/Redis/MinIO/Fabric Peer 在宿主机仅绑定 127.0.0.1，但**从 Docker 网络经 `host.docker.internal` 可达**。仅记录为加固建议，**不要连接、不要读取**。

---

## 五、执行阶段（Phases）

按顺序推进，每阶段结束输出小结再继续，避免一次性长跑丢证据。

**Phase 1 — Recon（只读）**
- 枚举 22 个 controller 的路由（可从 `/v3/api-docs` 尝试，prod 已关；dev 若开放则导出完整端点表）。
- 用四个角色各取一个 Token，建立「端点 × 角色」可访问性矩阵（200/401/403 三态）。
- 产出：端点-角色矩阵（这是后续越权判定的基线）。

**Phase 2 — 认证与会话**
- 验证 A1–A6。每个结论都要有「不传验证码即可登录」「旧 Token 登出后仍 200」这类**可复现的一对请求**。

**Phase 3 — 越权**
- 用 enterprise001 去打 enterprise002 的资源：先读、后写。写操作只在**自己创建的新对象**上做越权尝试，不要修改任何既有业务数据。
- 每条记录：请求、响应状态、关键响应字段、与预期（403）的偏差。

**Phase 4 — 业务逻辑**
- 用新建的草稿报告做篡改实验（负数、超大值、状态跳跃）。
- 交易/拍卖/碳币：用最小金额（≤1 吨、≤1 碳币）验证负数与双花，结束后把产生的订单标记为已取消（若能取消）。

**Phase 5 — 文件与内部服务**
- 上传测试文件到 `pentest/` 前缀，验证扩展名绕过；测试越权下载时只读取 1 个文件的头部字节即停止，不落地保存他人数据。
- ML 服务未授权访问与提示注入。

**Phase 6 — 报告**
- 汇总去重、按 CVSS 3.1 打分、按「可直接利用 > 有条件 > 加固建议」排序。

---

## 六、硬性约束（Rules of Engagement）

必须遵守，违反即视为测试失败：

1. **不破坏**：不删除、不改状态、不改配置、不改密码、不禁用账号（临时注册的测试账号除外，且用完禁用）。
2. **不拖库**：不使用 SQL 注入工具批量取数；验证到「可读出 1 行」即停。
3. **不打崩**：不做并发压测、不做大文件上传、不做目录爆破超过 200 次/路径。
4. **不外传**：任何取到的数据不得写入外部服务、不得留存在 PentAGI 长期记忆之外的位置。
5. **不越界**：不碰范围外的 MySQL/Redis/MinIO/Fabric/宿主机。
6. **不装后门**：不创建持久化账号、不在代码库写文件、不改动 `/carbon` 业务数据。
7. **停止条件**：一旦拿到「同一条利用链的完整证据」（请求 + 响应 + 影响），立刻停止该链，转向下一条，不做横向扩大。
8. **可还原**：所有写操作使用新建对象；结束时列出「本轮创建的对象清单」，供人工清理。

---

## 七、交付格式（Deliverable）

输出 Markdown 报告，结构如下（每条漏洞一节）：

```
### VULN-01 <标题>  [严重度: Critical/High/Medium/Low]  [CVSS: x.x]  [状态: 已复现/有条件/未复现]

- 受影响端点: METHOD /api/v1/...
- 前置条件: 需要 ENTERPRISE Token（enterprise001）
- 复现步骤:
  1. ...
  2. ...
- 请求:
  ```
  curl -X ... 
  ```
- 响应: <关键片段，脱敏>
- 根因: 文件:行号 + 一句话（例：FileController.java:459 无 owner 时直接放行）
- 影响: 可读取跨租户 X 数据 / 可越权删除 Y / 可绕过 Z
- 修复建议: 具体改法（含代码级建议）
- 证据文件: <截图或响应落盘路径>
```

报告末尾附三张表：
1. **端点 × 角色访问矩阵**（22 个 controller 的关键端点）
2. **漏洞总表**（编号 / 名称 / 严重度 / CVSS / 状态 / 修复优先级）
3. **加固项**（不构成漏洞但违反最小权限/纵深防御的配置项）

另外单独给出一段「**未验证但高风险**」清单：源码上可疑、但受限于环境或授权未能实证的点，供人工复核。

---

## 八、PentAGI 使用提示

- 在 Flow 设置里把 **ASK_USER** 打开：遇到「需要写操作」「需要注册新账号」「发现自己可能越界」时先问你，而不是自行决定。
- 把本文件作为第一个 message 提交；若 Flow 支持上传附件，附上 `DEV-GUIDE.md` 与 `docs/接口参考/` 目录，能显著减少它乱猜端点的轮次。
- 若 Flow 卡在「找不到前端 5173」，直接告诉它：前端不在容器可达范围，全部测试走 `host.docker.internal:8080/api/v1`。
- 若它开始跑 nmap/nikto 之类泛扫，打断并重申第四节的具体假设——本任务价值在业务逻辑与越权，不在端口扫描。
