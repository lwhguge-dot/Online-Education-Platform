# 智慧课堂在线教育平台（Smart Classroom）

基于 `Spring Cloud Alibaba + Vue 3 + TypeScript` 的前后端分离微服务项目。

## 当前版本重点（2026-02）

- Docker 启动链路已支持 `.env` 强校验（缺失/弱密码会拒绝启动）。
- 生产覆盖模式默认收口端口（宿主机只暴露 `80` 与 `8090`）。
- 启动脚本支持：`-ForceRecreate`（强制重建）与 `-CheckOnly`（仅预检）。
- `docker-compose.yml` 关键密码变量改为必填（fail-fast）。

## 目录结构

- `backend/`：Spring Cloud 微服务（gateway + 4 个业务服务）
- `frontend/`：Vue 3 前端
- `monitoring/`：Prometheus/Grafana 配置
- `tools/scripts/docker/`：Docker 一键启动脚本（Windows）
- `docker-compose.yml`：基础编排
- `docker-compose.prod.yml`：生产覆盖（端口收口）

## 启动前要求

- Windows + Docker Desktop（已启动）
- 已安装 Docker Compose v2（`docker compose version`）

## 首次启动（必须先准备 .env）

### 1) 生成本地 `.env`

```powershell
Copy-Item .env.example .env -Force
```

然后编辑 `.env`，将所有示例值替换为强密码。

> 注意：`.env` 已加入 `.gitignore`，不会提交到仓库。

### 2) 一键预检（推荐先执行）

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\scripts\docker\Docker启动.ps1 -CheckOnly
```

预检会校验：

- `.env` 是否存在
- 必填变量是否完整
- 密码长度与弱口令规则
- compose 配置是否可解析

### 3) 启动系统

- 双击：`tools/scripts/docker/Docker启动.bat`
- 或命令行：

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\scripts\docker\Docker启动.ps1
```

默认会自动启用生产覆盖模式（若存在 `docker-compose.prod.yml`）。

## 运行模式说明

### 生产覆盖模式（默认推荐）

- 入口：前端 `http://localhost`
- 网关：`http://localhost:8090`
- 其余中间件/运维端口不暴露到宿主机

### 开发模式（需要暴露全部端口时）

```powershell
docker compose up -d --force-recreate
```

或使用启动脚本显式关闭自动生产覆盖：

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\scripts\docker\Docker启动.ps1 -AutoProd:$false -ForceRecreate
```

## 改密与生效流程（必读）

当你修改 `.env` 中任意密码/密钥后：

1. 保存 `.env`
2. 执行一次强制重建：

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\scripts\docker\Docker启动.ps1 -ForceRecreate
```

或：

```powershell
docker compose -f docker-compose.yml -f docker-compose.prod.yml --env-file .env up -d --force-recreate
```

### 特殊项：`POSTGRES_PASSWORD`

若已存在数据库卷（非首次启动），改 `POSTGRES_PASSWORD` 前需先同步数据库内部账号密码：

```powershell
docker exec demo-postgres psql -U postgres -d postgres -c "ALTER USER postgres WITH PASSWORD '新密码';"
```

然后再执行 `--force-recreate`。

> 若不做 `ALTER USER`，应用会因数据库认证失败而启动异常。

## 常用命令

```powershell
# 查看服务状态
docker compose ps

# 查看某服务日志
docker compose logs -f gateway

# 关闭全部服务
docker compose down
```

## 相关文档

- 后端优化验收：`docs/backend-optimization-acceptance.md`
- 环境变量与改密 Runbook：`docs/env-rotation-runbook.md`
- 安全加固说明：`docs/security-hardening-guide.md`
