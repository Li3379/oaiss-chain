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
| 1 | **一个任务 = 一个分支 = 一个 worktree** | 两个会话互相覆盖未提交文件 | `.githooks/pre-commit` + `scripts/agent/preflight.mjs` |
| 2 | **改动前取得路径租约** | 版本号/接口撞车，合并时才发现 | `scripts/agent/lease.mjs` |
| 3 | **禁止直推 main，只走 PR** | CI 在合并后才发现红，修复成本翻倍 | `.githooks/pre-push` + 分支保护 |
| 4 | **动手前必须基线同步** | 两侧各自实现同一需求，合并时冲突密集 | `preflight` + `pre-push` 护栏 4 |

### 1.1 这些规则现在是被自动执行的，不是自觉

2026-11-04 复盘发现一个反复出现的失效模式：**工具全都有，但没人调用**。

当时的真实状态是：本地 `main` 上积压了 26 个提交（2026-11-02~11-03），
它们从未经过一次 CI；与此同时远端 `main` 已经通过 PR #2/#3 推进。
两边各自独立实现了同一批需求（配额语义改造、`V27` 迁移、三个测试类），
一个 `git merge-tree` 试合并直接暴露 **8 个冲突文件，其中 3 个是 add/add**。

三道旧护栏为什么全都漏过去了：

| 旧护栏 | 检查时机 | 为什么漏掉本次事故 |
|---|---|---|
| `commit-msg` | 新建提交时 | 只校验信息格式，与内容冲突无关 |
| `pre-push` 护栏 1-3 | 推送时 | 本地 main **从未推送**，等于永不触发 |
| `lease.mjs` | 人工调用 | 全仓没有任何脚本/CI 调用它 |

所以补了三处**自动**执行：

- **`.githooks/pre-commit`**：拦截「在 main 上直接提交」。这是 2026-11-05 补上的
  最后一块拼图。两次积压（26 个、13 个）的根因是**时机错配**——
  `pre-push` 只在推送时检查，而提交可以无限积压、永不推送。更隐蔽的后果是：
  本地 main 一旦领先远端，这些提交就再也不会被 CI 看见（分支保护只在 PR 上跑），
  于是「本地跑过门禁」被当成「验过了」，而 8 项必需检查一次都没执行。
- **`scripts/agent/preflight.mjs`**：开工前跑，只读、不改状态。
  它以 `BEHIND_ORIGIN` / `DIVERGED` / `OVERLAPPING_FILES` 三个码直接阻断
  「在漂移基线上开工 + 已产生冲突面」这一组合。
- **`pre-push` 护栏 4**：推送前复查基线漂移。默认只警告（避免逼人 `--no-verify`），
  CI 与发布流程用 `REQUIRE_UP_TO_DATE=1` 硬拦。

三条都有可执行的反向测试：`node scripts/agent/concurrency-sim.mjs`（S1-S5）。

---

## 2. 标准作业流程（Agent 必须逐步执行）

### 2.1 认领任务：先跑预检，再建 worktree

**不要**在共享工作区直接开工。先回答一个问题：**这个目录现在还有谁在用？**

```bash
node scripts/agent/preflight.mjs      # 只读，不改任何状态
```

三种典型输出与处置：

| 输出 | 含义 | 处置 |
|---|---|---|
| `ON_MAIN` | 你在主分支上 | 停手，按下一节建 worktree |
| `BEHIND_ORIGIN` | 基线已过期 | `git fetch origin`，rebase/merge 到最新再动手 |
| `OVERLAPPING_FILES` | 你和远端已各自改了同一批文件 | **停手**，先合并并跑通测试，再开新任务 |

预检通过后再建独立目录：

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

- SemVer：`MAJOR.MINOR.PATCH`，**唯一事实源是 `CHANGELOG.md`**
  （`pom.xml` / `oaiss-chain-backend/pom.xml` / 前端 `package.json` 必须与之对齐，
  由 `scripts/gates/version-verify.mjs` 强制）
- Tag 格式：`v<MAJOR>.<MINOR>.<PATCH>`，**附注 tag**（`git tag -a`），不是轻量 tag
- 打标签前必须：`main` 全绿 + `CHANGELOG.md` 顶部有对应段落

### 5.1 发布流水（照抄即可）

```bash
# 0. 前提：改动已经通过 PR 合入 main，且 CI 全绿
git switch main && git pull --ff-only

# 1. 先写 CHANGELOG —— 顺序不能反。
#    先打 tag 再补文档，等于发布了一个「没人知道改了什么」的版本。
#    编辑 CHANGELOG.md，在顶部新增：
#      ## [1.7.2] - YYYY-MM-DD
#      （正文说明改了什么，release.mjs 会拒绝少于 30 字的空壳段落）

# 2. 对齐三处版本号（pom / 后端 pom / package.json）
#    改完跑门禁确认：
node scripts/gates/version-verify.mjs

# 3. 提交版本变更
git add CHANGELOG.md pom.xml oaiss-chain-backend/pom.xml oaiss-chain-frontend/package.json
ALLOW_COMMIT_MAIN=1 git commit -m "chore(release): 1.7.2"

# 4. 干跑：检查 7 项前置条件，不做任何动作
node scripts/release.mjs 1.7.2

# 5. 打标签并推送
node scripts/release.mjs 1.7.2 --tag --push

# 6. 创建 GitHub Release（从 tag 抓 notes）
gh release create v1.7.2 --title "v1.7.2" --notes-from-tag
```

`release.mjs` 会检查的 7 项：
版本递增 / CHANGELOG 段落非占位 / 版本门禁通过 / 在 main 上 / 工作区干净 /
不落后 origin/main / 当前提交 CI 全绿 / tag 未占用。

**为什么需要脚本而不是几条命令**：发布最容易漏的不是打 tag，而是**前置条件**——
在红色 CI 上打 tag、在没写 CHANGELOG 的版本上打 tag、在落后远端时打 tag。
这些都能在 5 秒内查出来，但人工执行时通常一条都不查。

### 5.2 关于 tag 与 worktree 的并发

Tag 是**全局命名空间**，没有路径可以租。两个会话同时发布同一版本，
后者会在 `--push` 阶段被远端拒绝（non-fast-forward），不会静默覆盖。
但更早的失败信号是 `release.mjs` 的「tag 未占用」检查——**发布前先干跑一次**。

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
# 0. 预检（只读）——先确认这个目录还有谁在用、基线是否过期
node scripts/agent/preflight.mjs

# 开工
git fetch origin && git worktree add ../t-<id> -b feat/<slug> origin/main && cd ../t-<id>
node scripts/agent/lease.mjs acquire --task "feat/<slug>" --paths "<dirs>"

# 自检
node scripts/gates/doc-verify.mjs && npm run type-check --prefix oaiss-chain-frontend

# 交付（护栏 4 会提示基线漂移；发布前用 REQUIRE_UP_TO_DATE=1 硬拦）
git push -u origin feat/<slug> && gh pr create --fill && gh pr checks --watch

# 收尾
node scripts/agent/lease.mjs release --task "feat/<slug>"
gh pr merge --squash --delete-branch && git worktree remove ../t-<id>
```

---

## 8. 机制自检

怀疑护栏是否真的生效时，不要在文档里找答案，直接跑反向验证：

```bash
node scripts/agent/concurrency-sim.mjs          # 5 个真实场景，全部在临时沙盒里跑
node scripts/agent/concurrency-sim.mjs --only S3 # 单场景
```

| 场景 | 验证的事 | 期望 |
|---|---|---|
| S1 | 两个 Agent 改不相交文件 | 合并零冲突（证明路径隔离充分） |
| S2 | 两个 Agent 改同一 i18n 文件 | **确实冲突**（证明独占租约不是多余门槛） |
| S3 | 两侧各自 +1 到同一迁移版本号 | `pre-push` 以 Flyway 水位线拦截 |
| S4 | 在落后远端的基线上开工 | `preflight` 以 `BEHIND_ORIGIN` 阻断 |
| S5 | 小数迁移版本（`V29_1`） | 不误判「命名不合规」；乱序关闭时仍按水位线拦、开启时降级提示 |

S2 的期望结果是「冲突」，这一点容易误读：我们的策略是**禁止同改**（高危路径独占），
不是**解决同改**。S2 证明冲突真实存在，从而证明那条禁令不是形式主义。

S5 是一次真实事故的回归锁：`version_of()` 只认 `^V([0-9]+)__` 时，`V29_1` 解析为空
被判成「命名不合规」，导致 main 上 13 个提交全部推不出去——护栏从保护变成了封锁。
验证时做过 A/B：把正则还原成旧版后 S5 立刻 FAIL，恢复后 PASS，确认它不是空转的锁。
