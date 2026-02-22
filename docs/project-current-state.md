# 项目当前状态快照（2026-02-22）

## 1. 审查范围与方法

- 审查时间：`2026-02-22`
- 审查范围：当前仓库所有 Git 跟踪文件（`git ls-files`）
- 审查方式：目录扫描 + 核心配置/源码抽样核对 + 文档与配置交叉校验
- 审查目标：更新项目文档到“可由仓库事实直接验证”的状态

## 2. 全仓文件统计

### 2.1 总体规模

| 指标 | 数值 |
|------|------|
| Git 跟踪文件总数 | 496 |
| backend 文件数 | 305 |
| frontend 文件数 | 125 |
| docs 文件数 | 40 |
| ops 文件数 | 13 |
| workflow 文件数 | 4 |

### 2.2 主要文件类型

| 类型 | 数量 |
|------|------|
| `.java` | 288 |
| `.vue` | 72 |
| `.ts` | 31 |
| `.png` | 18 |
| `.yml` | 15 |
| `.json` | 11 |
| `.md` | 9 |

## 3. 后端现状（`backend/`）

### 3.1 模块与代码规模

| 模块 | 主代码(Java) | 测试(Java) | Controller | Service | Mapper | Config |
|------|-------------:|-----------:|-----------:|--------:|-------:|-------:|
| common | 8 | 1 | 0 | 1 | 0 | 0 |
| gateway | 6 | 2 | 0 | 0 | 0 | 3 |
| user-service | 71 | 6 | 7 | 9 | 8 | 6 |
| course-service | 69 | 9 | 5 | 10 | 8 | 8 |
| homework-service | 74 | 8 | 5 | 11 | 9 | 4 |
| progress-service | 31 | 3 | 3 | 6 | 5 | 4 |

后端测试文件总数：`29`。

### 3.2 服务端口（来自各模块 `application.yml`）

| 服务 | 端口 |
|------|------|
| gateway | 8090 |
| user-service | 8081 |
| course-service | 8082 |
| homework-service | 8083 |
| progress-service | 8084 |

### 3.3 API 领域分布（按控制器映射）

- user-service：认证、用户、教师画像、公告、通知、管理统计、审计日志
- course-service：课程、章节、选课、文件上传、章节评论与禁言/屏蔽词
- homework-service：作业、讨论、评论、教学日历、教师统计
- progress-service：学习进度、徽章、学生统计
- gateway：统一路由、JWT 校验、限流、CORS、WebSocket 路由

### 3.4 数据库脚本

- 文件：`backend/schema.sql`
- 表数量：`29`（按 `CREATE TABLE IF NOT EXISTS` 统计）
- 业务域覆盖：用户、课程、进度、作业、评论、公告、教学日历、审计日志等

## 4. 前端现状（`frontend/`）

### 4.1 技术栈与构建

- 框架：Vue 3 + TypeScript + Vite + Pinia + Vue Router
- 质量工具：ESLint + vue-tsc + Vitest
- 运行脚本（`package.json`）：`dev` / `build` / `type-check` / `lint` / `test`

### 4.2 `src` 分层规模

| 目录/文件 | 数量 |
|-----------|------|
| `components` | 42 |
| `views` | 28 |
| `services` | 14 |
| `assets` | 6 |
| `utils` | 5 |
| `composables` | 4 |
| `stores` | 3 |

前端测试文件总数：`3`（`websocket.test.ts`、`concurrency.test.ts`、`datetime.test.ts`）。

### 4.3 路由与权限

- 路由定义文件：`frontend/src/router/index.ts`
- `path` 条目数：`16`
- 角色门禁：`admin` / `teacher` / `student`
- 鉴权逻辑：全局前置守卫 + 基于 `allowedRoles` 的角色校验

### 4.4 前端环境变量（已存在）

- `frontend/.env.development`
- `frontend/.env.production`
- `frontend/.env.local`（包含 Sentry DSN 与环境）

## 5. 编排与运维（`docker-compose*.yml` + `ops/`）

### 5.1 基础编排服务

- nacos、sentinel、postgres、redis
- gateway、user-service、course-service、homework-service、progress-service
- frontend、minio、prometheus、grafana、jaeger

### 5.2 端口策略

- `docker-compose.yml`：开发场景，公开多个基础设施端口
- `docker-compose.prod.yml`：通过 `!reset []` 收敛大部分基础设施端口
- `ops/scripts/docker/Docker启动.ps1` 默认 `AutoProd=true`，会自动叠加 `docker-compose.prod.yml`

### 5.3 监控配置

- Prometheus 抓取配置：`ops/monitoring/prometheus/prometheus.yml`
- Grafana 数据源与仪表盘：`ops/monitoring/grafana/provisioning/**`
- 监控文档：`ops/monitoring/README.md`

## 6. CI / 安全 / 部署（`.github/workflows`）

| 工作流 | 关键功能 | 触发 |
|--------|----------|------|
| `ci.yml` | 后端构建测试 + 前端构建检查 | push/PR（文档变更默认忽略） |
| `security.yml` | CodeQL、Trivy、依赖漏洞检查 | push/PR/定时 |
| `docker-build.yml` | 构建并推送 GHCR 镜像 | tag `v*` / 手动 |
| `deploy.yml` | Staging/Production SSH 部署 | docker-build 完成 / 手动 |

## 7. 文档现状与本轮更新

### 7.1 本轮已更新文档

- `README.md`
- `docs/ai-skills-maintenance.md`
- `docs/ai-session-start-template.md`
- `ops/monitoring/README.md`
- `docs/project-current-state.md`（本文件）

### 7.2 本轮识别并已修正的偏差

- `README.md` 中“docs 当前为空”与仓库不一致
- AI 文档曾引用 `backend-security-coder`，与本地已安装 skills 不一致
- 监控文档对宿主机端口与容器内抓取地址说明混杂

### 7.3 当前一致性说明

- `ops/scripts/skills/skill-health-check.ps1` 已与当前技能基线对齐，实测可通过。

## 8. 可复现实证命令（摘录）

```powershell
# 全仓跟踪文件数
git ls-files | Measure-Object

# 统计 schema 表数量
Select-String -Path backend/schema.sql -Pattern '^CREATE TABLE IF NOT EXISTS ' -AllMatches

# 查看本地已安装 skills
Get-ChildItem .codex/skills -Directory | Select-Object -ExpandProperty Name

# 运行技能健康检查
powershell -ExecutionPolicy Bypass -File .\ops\scripts\skills\skill-health-check.ps1
```
