# 智慧课堂在线教育平台（Smart Classroom）

基于 `Spring Cloud Alibaba + Vue 3 + TypeScript + PostgreSQL` 的前后端分离微服务项目。

## 项目现状（按当前代码）

- 后端模块：`gateway`、`user-service`、`course-service`、`homework-service`、`progress-service`、`common`
- 前端模块：`frontend`（Vue 3 + Vite + Pinia + Vue Router）
- 基础设施：Nacos、Sentinel、PostgreSQL、Redis、MinIO、Prometheus、Grafana、Jaeger
- 编排文件：`docker-compose.yml` + `docker-compose.prod.yml`
- 数据库初始化脚本：`backend/schema.sql`

## 目录结构

- `backend/`：Java 21 + Spring Boot 3 微服务
- `frontend/`：前端工程
- `monitoring/`：监控配置
- `tools/scripts/docker/`：Windows 一键启动脚本
- `docs/`：项目说明文档

## 运行环境

- Windows + Docker Desktop
- Docker Compose v2
- Node.js 20+（本地单独跑前端时）
- Java 21 + Maven（本地单独跑后端时）

## 环境变量

先复制：

```powershell
Copy-Item .env.example .env -Force
```

然后按需修改 `.env`，至少要配置以下关键项（来自当前 `.env.example`）：

- `POSTGRES_PASSWORD`
- `REDIS_PASSWORD`
- `JWT_SECRET`
- `INTERNAL_API_TOKEN`
- `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD`
- `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY`
- `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD`

## Docker 启动（推荐）

### 1) 预检查

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\scripts\docker\Docker启动.ps1 -CheckOnly
```

或：

```powershell
tools\scripts\docker\Docker启动.bat -CheckOnly
```

### 2) 启动

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\scripts\docker\Docker启动.ps1
```

或直接双击：`tools/scripts/docker/Docker启动.bat`

## 常用访问地址（开发编排）

- 前端：`http://localhost`
- 网关：`http://localhost:8090`
- Nacos：`http://localhost:8848/nacos`
- Sentinel：`http://localhost:8858`
- MinIO Console：`http://localhost:9001`
- Prometheus：`http://localhost:9090`
- Grafana：`http://localhost:3000`
- Jaeger：`http://localhost:16686`

> 生产覆盖编排会收口大部分端口，仅保留必要入口。

## 数据库说明

- 当前建表脚本：`backend/schema.sql`
- 当前脚本包含 **29 张表**（按 `CREATE TABLE IF NOT EXISTS` 统计）
- 字符集：UTF-8
- 主要业务域：用户、课程、作业、学习进度、公告、评论/问答、教学日历、监控审计

### 重要变更（本次同步）

- 已按当前实体补齐 `homeworks.teacher_id` 字段与索引：
  - 列：`teacher_id BIGINT DEFAULT NULL`
  - 索引：`idx_homeworks_teacher`
  - 兼容迁移：`ALTER TABLE ... ADD COLUMN IF NOT EXISTS teacher_id`

## 本地开发（可选）

### 前端

```powershell
cd frontend
npm install
npm run dev
```

### 前端构建

```powershell
cd frontend
npm run build
```

### 后端（Maven）

```powershell
cd backend
mvn -T 1C clean package -DskipTests
```

## 其他文档

- `docs/backend-optimization-acceptance.md`
- `docs/env-rotation-runbook.md`
- `docs/security-hardening-guide.md`

