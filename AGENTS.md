# 项目级 AI Skill 使用约定

## 1. 作用范围
- 仅使用当前项目本地目录：`.codex/skills`
- 不修改全局目录：`$HOME/.codex/skills`
- `.codex/` 默认保持在 `.gitignore` 中，不上传远端仓库

## 2. 已安装 Skill 清单

### 2.1 通用工程流程（superpowers，14 个）
- `brainstorming`
- `dispatching-parallel-agents`
- `executing-plans`
- `finishing-a-development-branch`
- `receiving-code-review`
- `requesting-code-review`
- `subagent-driven-development`
- `systematic-debugging`
- `test-driven-development`
- `using-git-worktrees`
- `using-superpowers`
- `verification-before-completion`
- `writing-plans`
- `writing-skills`

### 2.2 规划类
- `planning-with-files`

### 2.3 前端设计类
- `ui-ux-pro-max`

### 2.4 Antigravity 后端精选（固定 8 个）
- `api-security-best-practices`
- `auth-implementation-patterns`
- `docker-expert`
- `deployment-procedures`
- `distributed-tracing`
- `observability-engineer`
- `postgres-best-practices`
- `microservices-patterns`

### 2.5 GitHub 工作流与可观测增强（新增 5 个）
- `address-github-comments`
- `playwright-skill`
- `gh-fix-ci`
- `sentry`
- `security-threat-model`

### 2.6 系统边界技能（仅边界补充）
- `skill-creator`
- `skill-installer`
- 仅在“创建 skill / 安装 skill”场景启用，不作为项目常规交付链路。

## 3. Skill 路由表（默认执行）
| 场景 | 优先 Skill | 说明 |
| --- | --- | --- |
| 复杂需求拆解 | `planning-with-files` + `writing-plans` | 先做任务拆分、依赖梳理、里程碑定义 |
| 方案探索 | `brainstorming` | 需求不明确时先收敛方案 |
| 按计划落地开发 | `executing-plans` + `verification-before-completion` | 按步骤实现并做完成前校验 |
| 前端 UI/UX 设计 | `ui-ux-pro-max` | 负责页面结构、视觉语言与交互细节 |
| 后端 API 安全评审 | `api-security-best-practices` + `auth-implementation-patterns` | 覆盖鉴权、输入校验、常见漏洞 |
| 微服务设计/重构 | `microservices-patterns` | 服务边界、通信模式、容错策略 |
| PostgreSQL 优化 | `postgres-best-practices` | 索引、SQL、连接池和性能基线 |
| Docker 与发布 | `docker-expert` + `deployment-procedures` | 镜像构建、环境一致性、发布流程 |
| 可观测性与追踪 | `observability-engineer` + `distributed-tracing` | 指标、日志、链路追踪落地 |
| PR 评论处理 | `address-github-comments` | 基于 GitHub PR 评论做分组修复与闭环 |
| CI 失败排查 | `gh-fix-ci` | 定位 GitHub Actions 失败检查并生成修复路径 |
| 浏览器自动化回归 | `playwright-skill` | 基于 Playwright 执行关键流程回归与截图 |
| Sentry 线上问题排查 | `sentry` | 查询生产错误、事件与基础健康状态 |
| 安全威胁建模 | `security-threat-model` | 输出边界、资产、威胁路径与缓解建议 |
| Bug 修复 | `systematic-debugging` + `test-driven-development` | 先定位根因，再以测试驱动方式修复 |
| 实现编码 | `test-driven-development` | 实现前先补测试，避免回归 |
| 全局守卫 | `using-superpowers` | 每轮先检查是否应触发 skill，再执行具体任务 |
| 开发收尾与分支治理 | `receiving-code-review` + `finishing-a-development-branch` | 代码评审、合并前检查、分支清理 |

## 4. 默认触发顺序
1. 每轮先走 `using-superpowers`，判断是否命中 skill 触发条件。
2. 先判断任务是否复杂；复杂任务先用 `planning-with-files`。
3. 需求模糊时先用 `brainstorming` 做方案收敛。
4. 需求明确且进入实现前，先用 `writing-plans` 形成可执行步骤。
5. 按计划落地时，优先 `executing-plans`，多子任务并行时可用 `subagent-driven-development`。
6. 进入领域开发后，按任务类型叠加对应 skill（前端/后端安全/数据库/发布/可观测性等）。
7. Bug 修复必须先 `systematic-debugging`，再 `test-driven-development`。
8. 任一“完成/通过”声明前，固定执行 `verification-before-completion` + `receiving-code-review`。
9. 需要分支收尾时再用 `finishing-a-development-branch`。

## 5. Antigravity 精选集约束
- 当前项目后端方向只保留上面列出的 8 个精选 Skill（见 2.4）。
- 非后端方向可按评审引入少量 Antigravity Skill（如 `address-github-comments`、`playwright-skill`）。
- 不默认安装 antigravity 全量技能，避免触发噪音与维护负担。
- 如需新增，必须先说明新增场景、预期收益与替代关系，再评审是否纳入。

## 6. 对话执行接口约定（行为层）
- 输入：任务意图、复杂度、任务领域（前端/后端/数据库/发布/调试等）。
- 输出：按顺序选择的 skill 列表（允许多 skill 串联）。
- 约束：若命中 skill 触发条件，优先执行 skill 约定，再进入具体实现。

## 7. 行为验收场景
- 用户说“做一个新功能”：先触发 `brainstorming`，再 `writing-plans`。
- 用户说“按这个计划实现”：触发 `executing-plans` 或 `subagent-driven-development`。
- 用户说“这个 bug 修一下”：先 `systematic-debugging`，再 `test-driven-development`。
- 用户说“优化 SQL”：触发 `postgres-best-practices`。
- 用户说“加监控链路”：触发 `observability-engineer` + `distributed-tracing`。
- 用户说“准备发布”：触发 `deployment-procedures`（必要时叠加 `docker-expert`）。
- 用户说“修复当前 PR 的 GitHub Actions 失败检查”：触发 `gh-fix-ci`。
- 用户说“处理这个 PR 的 review comments”：触发 `address-github-comments`。
- 用户说“用 playwright 跑一遍登录流程并截图”：触发 `playwright-skill`。
- 用户说“列出生产环境最近 24 小时 unresolved Sentry issues”：触发 `sentry`。
- 用户说“对 backend 做 threat model”：触发 `security-threat-model`。
- 用户说“帮我创建一个新 skill”：仅此时触发 `writing-skills` 或 `skill-creator`。
- 任一“完成/通过”声明前：必须执行 `verification-before-completion` 并给出验证证据。

## 8. Assumptions & Defaults
- “项目里所有 skill”默认指 `./.codex/skills` 的本地 skills。
- 系统技能 `skill-creator`、`skill-installer` 仅作补充边界认知，不替代项目本地路由。
- 默认中文沟通，文本与代码文件统一按 UTF-8 处理。
- 所有技能应用必须基于项目真实文件与上下文，不做臆测。
