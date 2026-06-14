# 部署指南

本文档介绍如何部署智慧课堂在线教育平台。

## 目录

- [环境要求](#环境要求)
- [开发环境部署](#开发环境部署)
- [生产环境部署](#生产环境部署)
- [Docker 部署](#docker-部署)
- [配置说明](#配置说明)
- [常见问题](#常见问题)

## 环境要求

### 开发环境

| 工具 | 版本要求 | 说明 |
|------|----------|------|
| Node.js | >= 22.0.0 | 前端运行环境 |
| npm | >= 10.0.0 | 包管理器 |
| Java | >= 21 | 后端运行环境 |
| Maven | >= 3.9.0 | 构建工具 |
| Docker | >= 24.0.0 | 容器化部署 |
| Docker Compose | >= 2.20.0 | 服务编排 |

### 生产环境

| 工具 | 版本要求 | 说明 |
|------|----------|------|
| Docker | >= 24.0.0 | 容器化部署 |
| Docker Compose | >= 2.20.0 | 服务编排 |
| 内存 | >= 8GB | 推荐配置 |
| CPU | >= 4 核 | 推荐配置 |

## 开发环境部署

### 1. 克隆项目

```bash
git clone https://github.com/your-username/edu-platform.git
cd edu-platform
```

### 2. 配置环境变量

```bash
# 复制环境变量模板
cp .env.example .env

# 编辑 .env 文件，填写必要的配置
# 必填项：
# - POSTGRES_PASSWORD: PostgreSQL 密码
# - REDIS_PASSWORD: Redis 密码
# - JWT_SECRET: JWT 密钥（至少 32 位）
# - INTERNAL_API_TOKEN: 内部 API 调用令牌
# - MINIO_ROOT_USER: MinIO 管理员用户名
# - MINIO_ROOT_PASSWORD: MinIO 管理员密码
# - NACOS_AUTH_TOKEN: Nacos 认证令牌（至少 32 位）
```

### 3. 启动服务

#### 使用 Docker Compose（推荐）

```powershell
# 使用启动助手（含 .env 预检查）
powershell -ExecutionPolicy Bypass -File .\ops\scripts\docker\Docker启动.ps1

# 或直接启动
docker compose up -d --force-recreate

# 查看日志
docker compose logs -f

# 停止服务
docker compose down
```

#### 本地开发

**前端开发**

```powershell
cd frontend

# 安装依赖
npm install

# 启动开发服务器（端口 3000）
npm run dev

# 代码质量检查
npm run type-check  # TypeScript 类型检查
npm run lint        # ESLint 检查
npm run test        # 单元测试

# 生产构建
npm run build
```

**后端开发**

```powershell
cd backend

# 编译项目
mvn -T 1C clean test -s settings.xml

# 打包（跳过测试）
mvn -T 1C package -DskipTests -s settings.xml
```

### 4. 访问服务

| 服务 | 地址 | 说明 |
|------|------|------|
| 前端页面 | http://localhost:5173 | 开发环境前端 |
| API 网关 | http://localhost:8090 | 统一 API 入口 |
| Nacos 控制台 | http://localhost:8848/nacos | 服务注册/配置 |
| MinIO Console | http://localhost:9001 | 对象存储管理 |
| PostgreSQL | localhost:5432 | 数据库 |
| Redis | localhost:16379 | 缓存 |

## 生产环境部署

### 1. 准备服务器

- 操作系统：Linux（推荐 Ubuntu 22.04 或 CentOS 8）
- 内存：>= 8GB
- CPU：>= 4 核
- 磁盘：>= 50GB

### 2. 安装 Docker

```bash
# Ubuntu
sudo apt update
sudo apt install docker.io docker-compose-plugin

# CentOS
sudo yum install docker-ce docker-ce-cli containerd.io docker-compose-plugin

# 启动 Docker
sudo systemctl start docker
sudo systemctl enable docker
```

### 3. 配置环境变量

```bash
# 克隆项目
git clone https://github.com/your-username/edu-platform.git
cd edu-platform

# 复制环境变量模板
cp .env.example .env

# 编辑 .env 文件
vim .env
```

**生产环境必填项：**

```bash
# 数据库密码（使用强密码）
POSTGRES_PASSWORD=your-strong-postgres-password

# Redis 密码（使用强密码）
REDIS_PASSWORD=your-strong-redis-password

# JWT 密钥（至少 32 位，使用随机生成的密钥）
JWT_SECRET=your-jwt-secret-at-least-32-characters-long

# 内部 API 调用令牌（使用随机生成的令牌）
INTERNAL_API_TOKEN=your-internal-api-token

# MinIO 配置
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=your-strong-minio-password
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=your-strong-minio-secret

# Nacos 认证令牌（至少 32 位）
NACOS_AUTH_TOKEN=your-nacos-auth-token-at-least-32-characters

# 允许的源（生产环境使用实际域名）
ALLOWED_ORIGINS=https://your-domain.com
```

### 4. 启动服务

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

### 5. 配置反向代理（Nginx）

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # 前端
    location / {
        proxy_pass http://localhost:5173;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # API
    location /api/ {
        proxy_pass http://localhost:8090;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # WebSocket
    location /ws/ {
        proxy_pass http://localhost:8090;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

### 6. 配置 SSL（可选）

```bash
# 安装 Certbot
sudo apt install certbot python3-certbot-nginx

# 获取 SSL 证书
sudo certbot --nginx -d your-domain.com

# 自动续期
sudo certbot renew --dry-run
```

## Docker 部署

### 开发环境

```bash
# 启动所有服务
docker compose up -d --force-recreate

# 查看服务状态
docker compose ps

# 查看日志
docker compose logs -f

# 停止服务
docker compose down

# 重新构建镜像
docker compose build
```

### 生产环境

```bash
# 使用生产配置启动
docker compose -f docker-compose.prod.yml up -d

# 查看服务状态
docker compose -f docker-compose.prod.yml ps

# 查看日志
docker compose -f docker-compose.prod.yml logs -f

# 停止服务
docker compose -f docker-compose.prod.yml down

# 重新构建镜像
docker compose -f docker-compose.prod.yml build
```

### 服务端口映射

| 服务 | 开发环境端口 | 生产环境端口 |
|------|--------------|--------------|
| 前端 | 5173 | 5173 |
| API 网关 | 8090 | 8090 |
| PostgreSQL | 5432 | 不暴露 |
| Redis | 16379 | 不暴露 |
| Nacos | 8848 | 不暴露 |
| MinIO | 9000, 9001 | 不暴露 |
| Hindsight | 8888 | 不暴露 |
| Ollama Proxy | 11436 | 不暴露 |

## 配置说明

### 环境变量

| 变量名 | 说明 | 必填 | 默认值 |
|--------|------|------|--------|
| POSTGRES_PASSWORD | PostgreSQL 密码 | 是 | - |
| REDIS_PASSWORD | Redis 密码 | 是 | - |
| JWT_SECRET | JWT 密钥（至少 32 位） | 是 | - |
| INTERNAL_API_TOKEN | 内部 API 调用令牌 | 是 | - |
| MINIO_ROOT_USER | MinIO 管理员用户名 | 是 | minioadmin |
| MINIO_ROOT_PASSWORD | MinIO 管理员密码 | 是 | - |
| MINIO_ACCESS_KEY | MinIO 访问密钥 | 是 | minioadmin |
| MINIO_SECRET_KEY | MinIO 秘密密钥 | 是 | - |
| NACOS_AUTH_TOKEN | Nacos 认证令牌（至少 32 位） | 是 | - |
| ALLOWED_ORIGINS | 允许的源 | 否 | http://localhost |
| GATEWAY_RATE_LIMIT_PERMITS_PER_SECOND | 网关限流 | 否 | 200 |

### 数据库配置

- 数据库名：edu_platform
- 字符集：UTF-8
- 初始化脚本：backend/schema.sql

### Redis 配置

- 内存限制：256mb
- 淘汰策略：allkeys-lru
- 持久化：AOF

## 常见问题

### 1. 端口被占用

```bash
# 查看端口占用
netstat -tulpn | grep :8090

# 停止占用端口的进程
sudo kill -9 <PID>
```

### 2. Docker 容器启动失败

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

## 监控和日志

### 查看服务状态

```bash
# 查看所有服务状态
docker compose ps

# 查看特定服务状态
docker compose ps <service-name>
```

### 查看日志

```bash
# 查看所有服务日志
docker compose logs -f

# 查看特定服务日志
docker compose logs -f <service-name>

# 查看最近 100 行日志
docker compose logs --tail 100 <service-name>
```

### 性能监控

```bash
# 查看容器资源使用
docker stats

# 查看特定容器资源使用
docker stats <container-name>
```

## 备份和恢复

### 数据库备份

```bash
# 备份 PostgreSQL
docker compose exec postgres pg_dump -U postgres edu_platform > backup.sql

# 恢复 PostgreSQL
docker compose exec -T postgres psql -U postgres edu_platform < backup.sql
```

### 文件备份

```bash
# 备份 MinIO 数据
docker compose exec minio tar czf /tmp/minio-backup.tar.gz /data

# 复制备份文件
docker compose cp minio:/tmp/minio-backup.tar.gz ./minio-backup.tar.gz
```

## 更新和升级

### 更新代码

```bash
# 拉取最新代码
git pull origin main

# 重新构建镜像
docker compose build

# 重启服务
docker compose up -d
```

### 升级数据库

```bash
# 运行数据库迁移
docker compose exec postgres psql -U postgres -d edu_platform -f /docker-entrypoint-initdb.d/schema.sql
```

## 联系方式

- 项目主页: https://github.com/your-username/edu-platform
- 问题反馈: https://github.com/your-username/edu-platform/issues
- 邮箱: your-email@example.com
