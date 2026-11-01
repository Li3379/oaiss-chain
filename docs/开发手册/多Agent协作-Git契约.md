# 多 Agent / 多会话 Git 协作契约

> 本文件是**强制约定**，不是建议。适用于人类开发者与任何 AI Agent 会话。
> 单一事实源：本文与 `DEV-GUIDE.md` 冲突时以 DEV-GUIDE 为准，并回来修本文。

---

## 0. 为什么需要这份契约

这个仓库已经发生过一次真实的并发事故：

```
19:47  会话 B 提交并推送  V23__update_unit_price_comment.sql
19:50  会话 A 新建        V22__add_credit_delisted.sql   （未提交）
```

两个会话共享同一个工作目录，彼此「看不见」对方的意图。结果是 V22 排在 V23
之后才要入库，Flyway 判定 out-of-order，**已部署环境直接拒绝启动**。
最终只能把 V22 重编为 V24，并把 V22 作为永久空号写进 DEV-GUIDE。

`git status` 对此无能为力：它只说「有改动」，说不出谁在改、改到哪一步、
还要多久。所以需要下面这套机制。

**一句话原则：动手前先声明，动手时独占路径，交付时只走 PR。**

---

## 1. 三条硬规则

| # | 规则 | 违反后果 | 由谁强制 |
|---|---|---|---|
| 1 | **一个任务 = 一个分支 = 一个 worktree** | 两个会话互相覆盖未提交文件 | `pre-push` + 流程 |
| 2 | **改动前取得路径租约** | 版本号/接口撞车，合并时才发现 | `scripts/agent/lease.mjs` |
| 3 | **禁止直推 main，只走 PR** | CI 在合并后才发现红，修复成本翻倍 | `.githooks/pre-push` + 分支保护 |

---

## 2. 标准作业流程（Agent 必须逐步执行）

### 2.1 认领任务：先建 worktree

**不要**在共享工作区直接开工。每个任务一个独立目录：

```bash
# 从最新 main 起分支
git fetch origin
git worktree add ../oaiss-task-<编号> -b feat/<scope>-<slug> origin/main
cd ../oaiss-task-<编号>
```

为什么必须 worktree 而不是 `git switch`：
同一目录下切分支会让**另一个正在该目录作业的会话突然"变"到你的分支上**，
它的未提交改动会跟着漂移。这是本次事故的另一种形态。

### 2.2 声明要改哪些路径（租约）

```bash
node scripts/agent/lease.mjs acquire \
  --task "feat/<scope>-<slug>" \
  --paths "oaiss-chain-backend/src/main/java/com/oaiss/chain/service/,oaiss-chain-backend/src/main/resources/db/migration/" \
  --intent "改造 CreditScoreService 并新增下市字段"
```

- `--paths` 写**目录前缀**或具体文件，逗号分隔
- 硬冲突（前缀重叠）→ 命令退出码 1，**必须停下来**重新切分任务边界
- 软冲突（同目录不同文件）→ 只警告，可继续
- 默认 120 分钟到期；长任务用 `renew --task "<名字>"` 续租
- 完成后必须 `release --task "<名字>"`

**冲突时不要做的事**：不要「先改再说」。路径重叠说明任务边界没切好，
这本身就是需要上报的信号，而不是需要绕过的障碍。

### 2.3 开工前同步一次水位线

```bash
git fetch origin
ls oaiss-chain-backend/src/main/resources/db/migration | sort -V | tail -3
```

迁移版本号**必须在这里现取**，而不是凭记忆或凭本文件里的数字。
`pre-push` 会用 `origin/main` 上的最大版本号做二次拦截。

### 2.4 提交

```bash
git add -A
git commit -m "feat(schema): 新增信誉下市标记 V25"
```

`commit-msg` 钩子会校验 Conventional Commits 格式（见 §4）。

### 2.5 推送前自检

```bash
node scripts/gates/doc-verify.mjs
node scripts/gates/i18n-verify.mjs
npm run type-check --prefix oaiss-chain-frontend
```

`pre-push` 会自动再跑：分支保护 / Flyway 水位线 / 提交信息复查。

### 2.6 开 PR，等 CI

```bash
git push -u origin feat/<scope>-<slug>
gh pr create --fill
gh pr checks --watch
```

PR 上会自动跑：门禁 + 类型检查 + 静态分析棘轮 + 后端/前端/ML 单测。
**全绿才允许合并。**

### 2.7 收尾

```bash
node scripts/agent/lease.mjs release --task "feat/<scope>-<slug>"
gh pr merge --squash --delete-branch
git worktree remove ../oaiss-task-<编号>
```

---

## 3. 路径冲突矩阵（已知高危区）

以下路径历史修改频率最高，**跨任务同时改动的概率最大**，认领时必须显式覆盖：

| 路径 | 冲突原因 | 建议独占 |
|---|---|---|
| `oaiss-chain-backend/src/main/resources/db/migration/` | 版本号必须全局唯一递增 | ✅ 独占 |
| `oaiss-chain-frontend/src/i18n/locales/zh-CN.ts`<br>`.../en-US.ts` | 单一巨型对象，任何两处文案改动都会撞行 | ✅ 独占 |
| `DEV-GUIDE.md` | §11 事实表的数字是全仓汇总 | ✅ 独占 |
| `oaiss-chain-frontend/src/config/menu.ts` | 菜单/路由一处改动影响全部角色 | ⚠️ 串行 |
| `oaiss-chain-backend/src/main/java/com/oaiss/chain/service/CarbonService.java` | 核心业务，多任务高频触碰 | ⚠️ 串行 |
| `oaiss-chain-frontend/src/layout/index.vue` | 壳层，改一处全站受影响 | ⚠️ 串行 |
| `oaiss-chain-frontend/src/style.css` | 全局样式 | ⚠️ 串行 |
| `.github/workflows/` | 影响所有人的 CI | ✅ 独占 |

**i18n 是最大的隐性冲突源**：zh-CN 与 en-US 是两个巨型对象字面量，
两个人各在末尾加词条，Git 仍可能冲突（取决于插入位置）。
认领 i18n 时务必独占整个 `locales/` 目录。

---

## 4. 提交信息规范

```
<type>(<scope>)!: <subject>

type:   feat | fix | docs | style | refactor | perf | test | build | ci | chore | revert
scope:  模块名，小写（auth / trade / carbon / schema / ui / ci / gates ...）
!      仅用于破坏性变更
subject: 中文简述，说明「改了什么」，不写「改了代码」
```

合规示例：
```
feat(trade): 平台限价服务接入 4 个写入口
fix(auth): 修正验证码二次消费导致登录误报 401
docs(dev-guide): 同步 V24 迁移版本与空号声明
refactor(p2p)!: 交易对手改为关键词远程搜索
```

不合规（钩子会拒绝）：
```
update
修复bug
WIP
fix stuff
```

---

## 5. 版本与发布

- SemVer：`MAJOR.MINOR.PATCH`，当前以 `CHANGELOG.md` 为准
- Tag 格式：`v<MAJOR>.<MINOR>.<PATCH>`
- 打标签前必须：`main` 全绿 + `CHANGELOG.md` 顶部有对应段落
- 流程见 `docs/开发手册/` 的发布章节

---

## 6. 会话交接

一个 Agent 会话结束、另一个接手时，交接内容**必须落盘**，不能只留在对话里：

```bash
node scripts/agent/lease.mjs status   # 有哪些任务在进行
git status                            # 有哪些未提交改动
git worktree list                     # 有哪些并行工作区
```

若发现**不属于自己任务**的未提交改动：
- 不要 `git checkout --` / `git stash` / `git reset`
- 用 `stat` 看 mtime，判断是否还有活跃会话在写
- 无法确认归属时，向用户报告并等待裁决

---

## 7. 快速命令卡

```bash
# 开工
git fetch origin && git worktree add ../t-<id> -b feat/<slug> origin/main && cd ../t-<id>
node scripts/agent/lease.mjs acquire --task "feat/<slug>" --paths "<dirs>"

# 自检
node scripts/gates/doc-verify.mjs && npm run type-check --prefix oaiss-chain-frontend

# 交付
git push -u origin feat/<slug> && gh pr create --fill && gh pr checks --watch

# 收尾
node scripts/agent/lease.mjs release --task "feat/<slug>"
gh pr merge --squash --delete-branch && git worktree remove ../t-<id>
```
