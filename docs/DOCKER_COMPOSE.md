# Docker Compose 配置说明

本项目提供两套 Docker Compose 配置，分别用于开发环境和生产环境。

## 目录

- [开发环境](#开发环境)
- [生产环境](#生产环境)
- [服务说明](#服务说明)
- [常用命令](#常用命令)
- [故障排除](#故障排除)

## 开发环境

### 配置文件

- `docker-compose.yml`: 开发环境配置

### 服务列表

| 服务 | 端口 | 说明 |
|------|------|------|
| nacos | 8848, 9848 | 服务注册与配置中心 |
| postgres | 5432 | PostgreSQL 数据库 |
| redis | 16379 | Redis 缓存 |
| gateway | 8090 | API 网关 |
| user-service | 8081 | 用户服务 |
| course-service | 8082 | 课程服务 |
| homework-service | 8083 | 作业服务 |
| progress-service | 8084 | 进度服务 |
| frontend | 5173 | 前端应用 |
| minio | 9000, 9001 | 对象存储 |
| hindsight | 8888 | AI 记忆系统 |
| ollama-json-proxy | 11436 | Ollama 代理 |

### 启动命令

```bash
# 使用启动助手（推荐）
powershell -ExecutionPolicy Bypass -File .\ops\scripts\docker\Docker启动.ps1

# 或直接启动
docker compose up -d --force-recreate

# 查看日志
docker compose logs -f

# 停止服务
docker compose down
```

### 环境变量

开发环境使用 `.env` 文件配置：

```bash
# 复制环境变量模板
cp .env.example .env

# 编辑 .env 文件
vim .env
```

**必填项：**

```bash
POSTGRES_PASSWORD=your-postgres-password
REDIS_PASSWORD=your-redis-password
JWT_SECRET=your-jwt-secret-at-least-32-characters
INTERNAL_API_TOKEN=your-internal-api-token
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=your-minio-password
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=your-minio-secret
NACOS_AUTH_TOKEN=your-nacos-auth-token-at-least-32-characters
```

## 生产环境

### 配置文件

- `docker-compose.prod.yml`: 生产环境配置

### 服务列表

| 服务 | 端口 | 说明 |
|------|------|------|
| nacos | 不暴露 | 服务注册与配置中心 |
| postgres | 不暴露 | PostgreSQL 数据库 |
| redis | 不暴露 | Redis 缓存 |
| gateway | 8090 | API 网关 |
| user-service | 不暴露 | 用户服务 |
| course-service | 不暴露 | 课程服务 |
| homework-service | 不暴露 | 作业服务 |
| progress-service | 不暴露 | 进度服务 |
| frontend | 5173 | 前端应用 |
| minio | 不暴露 | 对象存储 |

### 启动命令

```bash
# 使用生产配置启动
docker compose -f docker-compose.prod.yml up -d

# 查看服务状态
docker compose -f docker-compose.prod.yml ps

# 查看日志
docker compose -f docker-compose.prod.yml logs -f

# 停止服务
docker compose -f docker-compose.prod.yml down
```

### 环境变量

生产环境使用 `.env.production` 文件配置：

```bash
# 复制环境变量模板
cp .env.example .env.production

# 编辑 .env.production 文件
vim .env.production
```

**生产环境必填项：**

```bash
POSTGRES_PASSWORD=your-strong-postgres-password
REDIS_PASSWORD=your-strong-redis-password
JWT_SECRET=your-jwt-secret-at-least-32-characters-long
INTERNAL_API_TOKEN=your-internal-api-token
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=your-strong-minio-password
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=your-strong-minio-secret
NACOS_AUTH_TOKEN=your-nacos-auth-token-at-least-32-characters
ALLOWED_ORIGINS=https://your-domain.com
```

## 服务说明

### nacos

- **镜像**: nacos/nacos-server:v2.4.3
- **端口**: 8848 (HTTP), 9848 (gRPC)
- **内存限制**: 512m
- **CPU 限制**: 0.5 核
- **健康检查**: `curl -sf http://localhost:8848/nacos/v1/console/health/readiness || exit 1`

### postgres

- **镜像**: postgres:16
- **端口**: 5432
- **内存限制**: 1g
- **CPU 限制**: 1.0 核
- **数据库**: edu_platform
- **初始化脚本**: backend/schema.sql
- **健康检查**: `pg_isready -U postgres`

### redis

- **镜像**: redis:7.0
- **端口**: 6379
- **内存限制**: 512m
- **CPU 限制**: 0.5 核
- **内存限制**: 256mb
- **淘汰策略**: allkeys-lru
- **健康检查**: `redis-cli --no-auth-warning -a "$REDIS_PASSWORD" ping | grep PONG`

### gateway

- **镜像**: demo-gateway:latest
- **端口**: 8090
- **内存限制**: 768m
- **CPU 限制**: 1.0 核
- **健康检查**: `curl -sf http://localhost:8090/actuator/health || exit 1`

### user-service

- **镜像**: demo-user-service:latest
- **端口**: 8081
- **内存限制**: 768m
- **CPU 限制**: 1.0 核
- **健康检查**: `curl -sf http://localhost:8081/actuator/health || exit 1`

### course-service

- **镜像**: demo-course-service:latest
- **端口**: 8082
- **内存限制**: 768m
- **CPU 限制**: 1.0 核
- **健康检查**: `curl -sf http://localhost:8082/actuator/health || exit 1`

### homework-service

- **镜像**: demo-homework-service:latest
- **端口**: 8083
- **内存限制**: 768m
- **CPU 限制**: 1.0 核
- **健康检查**: `curl -sf http://localhost:8083/actuator/health || exit 1`

### progress-service

- **镜像**: demo-progress-service:latest
- **端口**: 8084
- **内存限制**: 768m
- **CPU 限制**: 1.0 核
- **健康检查**: `curl -sf http://localhost:8084/actuator/health || exit 1`

### frontend

- **镜像**: demo-frontend:latest
- **端口**: 5173
- **内存限制**: 256m
- **CPU 限制**: 0.25 核
- **健康检查**: `curl -sf http://localhost:80 || exit 1`

### minio

- **镜像**: minio/minio:RELEASE.2024-01-18T22-51-28Z
- **端口**: 9000 (API), 9001 (Console)
- **内存限制**: 512m
- **CPU 限制**: 0.5 核
- **健康检查**: `curl -sf http://localhost:9000/minio/health/live || exit 1`

### hindsight

- **镜像**: ghcr.io/vectorize-io/hindsight:latest
- **端口**: 8888 (API), 9999 (Admin)
- **内存限制**: 2g
- **CPU 限制**: 2.0 核
- **健康检查**: `curl -sf http://localhost:8888/health || exit 1`

### ollama-json-proxy

- **镜像**: python:3.12-slim
- **端口**: 11436
- **说明**: Ollama JSON 代理

## 常用命令

### 服务管理

```bash
# 启动所有服务
docker compose up -d

# 停止所有服务
docker compose down

# 重启所有服务
docker compose restart

# 重启特定服务
docker compose restart <service-name>

# 查看服务状态
docker compose ps

# 查看服务日志
docker compose logs -f

# 查看特定服务日志
docker compose logs -f <service-name>
```

### 镜像管理

```bash
# 构建所有镜像
docker compose build

# 构建特定镜像
docker compose build <service-name>

# 拉取最新镜像
docker compose pull

# 推送镜像
docker compose push
```

### 数据管理

```bash
# 备份 PostgreSQL
docker compose exec postgres pg_dump -U postgres edu_platform > backup.sql

# 恢复 PostgreSQL
docker compose exec -T postgres psql -U postgres edu_platform < backup.sql

# 备份 MinIO
docker compose exec minio tar czf /tmp/minio-backup.tar.gz /data

# 复制备份文件
docker compose cp minio:/tmp/minio-backup.tar.gz ./minio-backup.tar.gz
```

### 清理资源

```bash
# 停止并删除容器
docker compose down

# 停止并删除容器和卷
docker compose down -v

# 停止并删除容器、卷和镜像
docker compose down -v --rmi all

# 清理未使用的资源
docker system prune -a
```

## 故障排除

### 1. 端口被占用

```bash
# 查看端口占用
netstat -tulpn | grep :8090

# 停止占用端口的进程
sudo kill -9 <PID>
```

### 2. 容器启动失败

```bash
# 查看容器日志
docker compose logs <service-name>

# 重新构建镜像
docker compose build <service-name>

# 重启服务
docker compose restart <service-name>
```

### 3. 数据库连接失败

```bash
# 检查 PostgreSQL 状态
docker compose ps postgres

# 查看 PostgreSQL 日志
docker compose logs postgres

# 测试连接
psql -h localhost -U postgres -d edu_platform
```

### 4. 内存不足

```bash
# 查看内存使用
free -h

# 增加交换空间
sudo fallocate -l 4G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
```

### 5. 磁盘空间不足

```bash
# 查看磁盘使用
df -h

# 清理 Docker 资源
docker system prune -a

# 清理未使用的镜像
docker image prune -a
```

## 相关链接

- [Docker 官网](https://www.docker.com/)
- [Docker Compose 文档](https://docs.docker.com/compose/)
- [Docker Hub](https://hub.docker.com/)
