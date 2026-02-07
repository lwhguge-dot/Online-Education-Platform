# .env 轮换与预检 Runbook

本文档用于指导本项目在本地/测试环境进行 `.env` 密码轮换与安全校验。

## 适用场景

- 首次克隆项目后初始化环境变量
- 周期性密码轮换
- 怀疑密钥泄漏后的应急替换

## 预检命令（不启动容器）

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\scripts\docker\Docker启动.ps1 -CheckOnly
```

预检通过标准：

- `.env` 存在
- 必填变量完整
- 密码长度满足最小要求
- 不包含示例弱口令（如 `change-*`）
- `docker compose config` 校验通过

## 密码轮换标准流程

### 步骤 1：修改 `.env`

更新以下高敏配置（按需）：

- `POSTGRES_PASSWORD`
- `REDIS_PASSWORD`
- `JWT_SECRET`
- `MINIO_ROOT_PASSWORD`
- `MINIO_SECRET_KEY`
- `GRAFANA_ADMIN_PASSWORD`

### 步骤 2：执行预检

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\scripts\docker\Docker启动.ps1 -CheckOnly
```

### 步骤 3：处理 PostgreSQL 特殊同步（如改了 POSTGRES_PASSWORD）

如果 `postgres-data` 卷已存在，必须先同步数据库内部密码：

```powershell
docker exec demo-postgres psql -U postgres -d postgres -c "ALTER USER postgres WITH PASSWORD '新密码';"
```

### 步骤 4：强制重建容器使配置注入

推荐（生产覆盖模式）：

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\scripts\docker\Docker启动.ps1 -ForceRecreate
```

或使用 compose 明确执行：

```powershell
docker compose -f docker-compose.yml -f docker-compose.prod.yml --env-file .env up -d --force-recreate
```

### 步骤 5：验收

```powershell
docker compose ps
docker compose logs --tail 100 gateway
docker compose logs --tail 100 user-service
```

观察点：

- 服务状态均为 `Up`（健康检查通过）
- 无数据库认证失败/Redis 认证失败日志

## 回滚方案

若轮换后服务异常：

1. 立即回滚 `.env` 到上一个可用版本
2. 如改过 `POSTGRES_PASSWORD`，同时回滚数据库内部密码
3. 重新执行 `-ForceRecreate`

## 注意事项

- `.env` 不入库，避免泄露。
- 生产覆盖模式默认不暴露中间件端口到宿主机。
- 改密后不重建容器，旧进程不会自动拿到新值。

