# AI Skills 维护与门禁策略（项目本地）

## 1. 目标与范围

- 本项目 skill 唯一事实来源：`./.codex/skills`
- 不修改全局目录：`$HOME/.codex/skills`
- 技能治理目标：同时提升“调用成功率”与“触发准确率”
- 所有维护动作必须基于仓库真实文件与脚本结果，不做臆测

## 2. 当前技能基线（2026-02-22）

### 2.1 通用流程（14）

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

### 2.2 规划类（1）

- `planning-with-files`

### 2.3 前端设计类（1）

- `ui-ux-pro-max`

### 2.4 后端精选（8）

- `api-security-best-practices`
- `auth-implementation-patterns`
- `docker-expert`
- `deployment-procedures`
- `distributed-tracing`
- `observability-engineer`
- `postgres-best-practices`
- `microservices-patterns`

### 2.5 GitHub / 可观测增强（5）

- `address-github-comments`
- `playwright-skill`
- `gh-fix-ci`
- `sentry`
- `security-threat-model`

总计：`29` 个本地 skills。

## 3. CI 强门禁

skill 质量由 `.github/workflows/skills-quality.yml` 强制执行，包含以下检查：

1. `skill-health-check`：基线一致性检查（目录、文件、清单对齐）
2. `skill-lint`：文档规则检查（UTF-8、frontmatter、禁用词、路径引用）
3. `skill-smoke`：脚本能力冒烟（可执行命令链路）
4. `skill-regression`：规则级全量回放（触发命中与顺序约束）

任一 job 失败均阻断 PR 合并。

## 4. 本地维护流程

1. 同步技能目录状态：
   - `Get-ChildItem .\.codex\skills -Directory | Select-Object -ExpandProperty Name`
2. 执行一致性检查：
   - `pwsh -File .\ops\scripts\skills\skill-health-check.ps1 -Strict`
3. 执行文档规则检查：
   - `pwsh -File .\ops\scripts\skills\skill-lint.ps1`
4. 执行冒烟验证：
   - `pwsh -File .\ops\scripts\skills\skill-smoke.ps1`
5. 执行规则回放：
   - `pwsh -File .\ops\scripts\skills\skill-regression.ps1`

## 5. 规范要求

- `SKILL.md` frontmatter 必须包含 `name` 与 `description`
- `description` 必须以 `Use when` 开头
- 每个 skill 必须包含中文执行层：`触发条件`、`前置条件`、`执行步骤`、`完成证据`、`失败回退`
- 禁用平台耦合词：`Claude`、`TodoWrite`、`.claude`
- 所有引用路径必须可在仓库中解析并存在

## 6. 回归契约文件

- `ops/skills/route-contract.json`：路由与顺序契约
- `ops/skills/regression-cases.json`：全量回放样例

两者必须与 `AGENTS.md` 第 3~7 节保持一致。

## 7. 回滚策略

1. 发现异常时先保留失败日志与 JSON 报告。
2. 回退到最近稳定提交并重新执行四类检查。
3. 回滚后不得跳过门禁，必须再次通过 CI。
