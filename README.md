# 智慧课堂在线教育平台（Smart Classroom）

基于 `Spring Cloud Alibaba + Vue 3 + TypeScript + PostgreSQL` 的前后端分离微服务项目。

## 项目快照（2026-02-22）

- Git 跟踪文件：`496`（`backend=305`、`frontend=125`、`docs=40`、`ops=13`）
- 后端模块：`gateway`、`user-service`、`course-service`、`homework-service`、`progress-service`、`common`
- 前端工程：`frontend`（Vue 3 + Vite + Pinia + Vue Router）
- 基础设施：Nacos、Sentinel、PostgreSQL、Redis、MinIO、Prometheus、Grafana、Jaeger
- 编排文件：`docker-compose.yml` + `docker-compose.prod.yml`
- 数据库初始化：`backend/schema.sql`（`CREATE TABLE IF NOT EXISTS` 共 29 张表）

完整审查结果见：`docs/project-current-state.md`

## 目录结构

- `backend/`：Java 21 + Spring Boot 3 微服务代码（Maven 多模块）
- `frontend/`：Vue 3 前端工程（含 Vitest 与 ESLint）
- `ops/scripts/docker/`：Docker 启动与重建脚本（Windows）
- `ops/scripts/skills/`：项目本地技能健康检查脚本
- `ops/scripts/upload/`：Git 推送辅助脚本
- `ops/monitoring/`：Prometheus / Grafana 配置与说明
- `docs/`：文档目录（维护策略、会话模板、审查报告与证据）
- `.github/workflows/`：CI / 安全扫描 / 镜像构建 / 部署流程

## 运行环境

- Windows + Docker Desktop
- Docker Compose v2
- Node.js 20+（本地单独跑前端）
- Java 21 + Maven（本地单独跑后端）

## 环境变量

先复制模板：

```powershell
Copy-Item .env.example .env -Force
```

关键变量（基于当前 `.env.example`）：

- `POSTGRES_PASSWORD`
- `REDIS_PASSWORD`
- `JWT_SECRET`
- `INTERNAL_API_TOKEN`
- `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD`
- `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY`
- `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD`
- `TRACING_SAMPLING_PROBABILITY`
- `GATEWAY_RATE_LIMIT_PERMITS_PER_SECOND` / `GATEWAY_RATE_LIMIT_CACHE_EXPIRE_MINUTES` / `GATEWAY_RATE_LIMIT_CACHE_MAX_SIZE`

## Docker 启动

### 方式 A：启动助手脚本（推荐）

`ops/scripts/docker/Docker启动.ps1` 会先校验 `.env` 必填项与弱口令，再执行 compose 启动流程。

预检查：

```powershell
powershell -ExecutionPolicy Bypass -File .\ops\scripts\docker\Docker启动.ps1 -CheckOnly
```

启动：

```powershell
powershell -ExecutionPolicy Bypass -File .\ops\scripts\docker\Docker启动.ps1
```

说明：该脚本默认 `AutoProd=true`，当检测到 `docker-compose.prod.yml` 时会自动叠加生产覆盖（端口将收敛）。

### 方式 B：显式开发编排

```powershell
docker compose up -d --force-recreate
```

此方式不叠加 `docker-compose.prod.yml`，更适合本地联调与观测所有公开端口。

## 常用访问地址

- 前端：`http://localhost`
- 网关：`http://localhost:8090`
- Nacos：`http://localhost:8848/nacos`（仅开发编排）
- Sentinel：`http://localhost:8858`（仅开发编排）
- MinIO Console：`http://localhost:9001`（仅开发编排）
- Prometheus：`http://localhost:9090`（仅开发编排）
- Grafana：`http://localhost:3000`（仅开发编排）
- Jaeger：`http://localhost:16686`（仅开发编排）

## 本地开发（可选）

### 前端开发

```powershell
cd frontend
npm install
npm run dev
```

### 前端质量检查

```powershell
cd frontend
npm run type-check
npm run lint
npm run test
```

### 后端构建与测试

```powershell
cd backend
mvn -T 1C clean test -s settings.xml
mvn -T 1C package -DskipTests -s settings.xml
```

## CI / 安全 / 部署流水线

- `ci.yml`：后端编译+测试、前端构建+检查、结果汇总
- `security.yml`：CodeQL + Trivy + 依赖漏洞检查
- `docker-build.yml`：多服务镜像构建并推送 GHCR
- `deploy.yml`：Staging/Production 远程部署

## 文档索引

- `docs/project-current-state.md`：当前仓库状态快照（建议优先阅读）
- `docs/ai-skills-maintenance.md`：本地 skills 维护策略
- `docs/ai-session-start-template.md`：新会话开场模板
- `ops/monitoring/README.md`：监控配置与排查手册
- `docs/reviews/`：历史审查报告与证据
