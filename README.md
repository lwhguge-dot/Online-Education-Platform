# 智慧课堂在线教育平台（Smart Classroom）

基于 `Spring Cloud Alibaba + Vue 3 + TypeScript` 的前后端分离微服务项目。

## 当前版本重点（2026-02）

- Docker 启动链路已支持 `.env` 强校验（缺失/弱密码会拒绝启动）。
- 生产覆盖模式默认收口端口（宿主机只暴露 `80` 与 `8090`）。
- 启动脚本支持：`-CheckOnly`、`-ForceRecreate`、`-Prod`、`-AutoProd:$false`、`-EnvFile`、`-TimeoutSeconds`、`-IntervalSeconds`。
- `tools/scripts/docker/Docker启动.bat` 会透传全部参数到 `Docker启动.ps1`，并在失败时自动暂停窗口。
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

# 或直接使用 bat（参数会透传给 ps1）
tools\scripts\docker\Docker启动.bat -CheckOnly
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

## 启动脚本参数速查

- `-CheckOnly`：只做环境预检，不启动容器。
- `-ForceRecreate`：对 `docker compose up` 追加 `--force-recreate`。
- `-Prod`：显式启用 `docker-compose.prod.yml` 覆盖。
- `-AutoProd:$false`：关闭“自动启用生产覆盖”逻辑。
- `-EnvFile <path>`：指定环境变量文件（默认 `.env`）。
- `-TimeoutSeconds <N>`：健康检查总超时秒数（默认 `180`）。
- `-IntervalSeconds <N>`：健康检查轮询间隔秒数（默认 `5`）。

## 运行模式说明

### 生产覆盖模式（默认推荐）

- 入口：前端 `http://localhost`
- 网关：`http://localhost:8090`
- 其余中间件/运维端口不暴露到宿主机
- 启动脚本末尾会打印完整地址清单，其中中间件地址在该模式下默认不可从宿主机直接访问

### 开发模式（需要暴露全部端口时）

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\scripts\docker\Docker启动.ps1 -AutoProd:$false -ForceRecreate
```

或使用 compose 直接拉起：

```powershell
docker compose -f docker-compose.yml --env-file .env up -d --force-recreate
```

开发模式下，除前端与网关外，常用中间件端口也会映射到宿主机（如 `8848`、`8858`、`9001`、`9090`、`3000`、`16686` 等）。

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
