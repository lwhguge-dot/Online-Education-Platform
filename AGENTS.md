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
- `backend-security-coder`
- `docker-expert`
- `deployment-procedures`
- `distributed-tracing`
- `observability-engineer`
- `postgres-best-practices`
- `microservices-patterns`

## 3. Skill 路由表（默认执行）
| 场景 | 优先 Skill | 说明 |
| --- | --- | --- |
| 复杂需求拆解 | `planning-with-files` + `writing-plans` | 先做任务拆分、依赖梳理、里程碑定义 |
| 方案探索 | `brainstorming` | 需求不明确时先收敛方案 |
| 按计划落地开发 | `executing-plans` + `verification-before-completion` | 按步骤实现并做完成前校验 |
| 前端 UI/UX 设计 | `ui-ux-pro-max` | 负责页面结构、视觉语言与交互细节 |
| 后端 API 安全评审 | `api-security-best-practices` + `backend-security-coder` | 覆盖鉴权、输入校验、常见漏洞 |
| 微服务设计/重构 | `microservices-patterns` | 服务边界、通信模式、容错策略 |
| PostgreSQL 优化 | `postgres-best-practices` | 索引、SQL、连接池和性能基线 |
| Docker 与发布 | `docker-expert` + `deployment-procedures` | 镜像构建、环境一致性、发布流程 |
| 可观测性与追踪 | `observability-engineer` + `distributed-tracing` | 指标、日志、链路追踪落地 |
| 开发收尾与分支治理 | `receiving-code-review` + `finishing-a-development-branch` | 代码评审、合并前检查、分支清理 |

## 4. 默认触发顺序
1. 先判断任务是否复杂；复杂任务先用 `planning-with-files`。
2. 需求模糊时先用 `brainstorming` 做方案收敛。
3. 进入实现阶段后按领域调用目标 Skill（前端/后端/数据库/发布/可观测性）。
4. 完成后固定执行 `verification-before-completion` + `receiving-code-review`。
5. 需要收尾时再用 `finishing-a-development-branch`。

## 5. Antigravity 精选集约束
- 当前项目只保留上面列出的 8 个精选 Skill。
- 不默认安装 antigravity 全量技能，避免触发噪音与维护负担。
- 如需新增，必须先说明新增场景、预期收益与替代关系，再评审是否纳入。

