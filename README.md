# 智慧课堂在线教育平台

基于 Spring Cloud Alibaba 的前后端分离微服务项目，提供课程管理、作业发布、学习进度追踪等在线教育功能。

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 3.2.0 |
| 微服务 | Spring Cloud | 2023.0.0 |
| 微服务 | Spring Cloud Alibaba | 2023.0.3.4 |
| 数据库 | PostgreSQL | 16 |
| 缓存 | Redis | 7.0 |
| 对象存储 | MinIO | RELEASE.2024-01-18 |
| 前端框架 | Vue | 3.5 |
| 前端构建 | Vite | 7.2 |
| 前端语言 | TypeScript | 5.9 |
| 状态管理 | Pinia | 3.0 |
| CSS 框架 | Tailwind CSS | 4.0 |
| 容器化 | Docker Compose | - |
| 后端语言 | Java | 21 |
| 前端语言 | TypeScript | 5.9 |

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

## 快速开始

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

## 项目结构

```
edu-platform/
├── backend/                    # 后端微服务（Maven 多模块）
│   ├── common/                 # 公共模块
│   ├── gateway/                # API 网关（端口 8090）
│   ├── user-service/           # 用户服务（端口 8081）
│   ├── course-service/         # 课程服务（端口 8082）
│   ├── homework-service/       # 作业服务（端口 8083）
│   ├── progress-service/       # 进度服务（端口 8084）
│   ├── pom.xml                 # Maven 父 POM
│   ├── settings.xml            # Maven 配置（阿里云镜像）
│   ├── schema.sql              # 数据库初始化脚本
│   └── Dockerfile              # 后端 Docker 构建文件
├── frontend/                   # 前端工程（Vue 3 + Vite）
│   ├── src/                    # 源代码
│   ├── public/                 # 静态资源
│   ├── tests/                  # 测试文件
│   ├── package.json            # 依赖配置
│   ├── vite.config.ts          # Vite 配置
│   ├── tsconfig.json           # TypeScript 配置
│   ├── Dockerfile              # 前端 Docker 构建文件
│   └── nginx.conf              # Nginx 配置
├── ops/                        # 运维脚本
│   ├── scripts/
│   │   ├── docker/             # Docker 启停脚本
│   │   ├── hindsight/          # Hindsight 记忆系统脚本
│   │   └── ollama/             # Ollama 配置脚本
├── .github/                    # GitHub 配置
│   ├── workflows/              # CI/CD 流水线
│   └── dependabot.yml          # 依赖更新配置
├── .opencode/                  # AI 编码规范
│   ├── rules/                  # 规则文件
│   ├── plugins/                # 插件
│   └── skills/                 # 技能
├── docker-compose.yml          # 开发环境编排
├── docker-compose.prod.yml     # 生产环境编排
├── .env.example                # 环境变量模板
├── .gitignore                  # Git 忽略配置
├── .dockerignore               # Docker 忽略配置
├── .editorconfig               # 编辑器配置
├── .gitattributes              # Git 属性配置
├── AGENTS.md                   # AI 代理配置
└── README.md                   # 项目说明
```

## 微服务模块

| 模块 | 端口 | 职责 |
|------|------|------|
| gateway | 8090 | API 网关，统一入口、JWT 鉴权、限流 |
| user-service | 8081 | 用户服务，注册登录、角色管理、消息通知 |
| course-service | 8082 | 课程服务，课程 CRUD、章节管理、评论审核 |
| homework-service | 8083 | 作业服务，作业发布、提交批改、讨论答疑 |
| progress-service | 8084 | 进度服务，学习追踪、章节测验、成就徽章 |
| common | - | 公共模块，通用工具、异常处理、事件消息 |

## 环境变量配置

### 必填项

| 变量名 | 说明 | 示例 |
|--------|------|------|
| POSTGRES_PASSWORD | PostgreSQL 密码 | your-postgres-password |
| REDIS_PASSWORD | Redis 密码 | your-redis-password |
| JWT_SECRET | JWT 密钥（至少 32 位） | your-jwt-secret-at-least-32-chars |
| INTERNAL_API_TOKEN | 内部 API 调用令牌 | your-internal-api-token |
| MINIO_ROOT_USER | MinIO 管理员用户名 | minioadmin |
| MINIO_ROOT_PASSWORD | MinIO 管理员密码 | your-minio-password |
| MINIO_ACCESS_KEY | MinIO 访问密钥 | minioadmin |
| MINIO_SECRET_KEY | MinIO 秘密密钥 | your-minio-secret |
| NACOS_AUTH_TOKEN | Nacos 认证令牌（至少 32 位） | your-nacos-auth-token-at-least-32-chars |

### 可选项

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| GATEWAY_RATE_LIMIT_PERMITS_PER_SECOND | 网关限流（每秒请求数） | 200 |
| GATEWAY_RATE_LIMIT_CACHE_EXPIRE_MINUTES | 限流缓存过期时间 | 30 |
| GATEWAY_RATE_LIMIT_CACHE_MAX_SIZE | 限流缓存最大大小 | 100000 |
| BOOTSTRAP_ADMIN_ENABLED | 是否启用管理员初始化 | false |
| WEBSOCKET_ALLOWED_ORIGINS | WebSocket 允许的源 | http://localhost:3000,http://localhost:5173 |

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

## CI/CD

### GitHub Actions 流水线

| 流水线 | 触发条件 | 功能 |
|--------|----------|------|
| ci.yml | push/PR 到 main/develop | 后端编译测试 + 前端构建检查 |
| docker-build.yml | 推送 tag (v*) 或手动触发 | 多服务镜像构建推送到 GHCR |
| security.yml | push/PR 到 main + 每周一 | CodeQL + Trivy 安全扫描 |

### CI 命令顺序

**前端 CI**
```bash
npm run lint:motion      # 动效门禁检查
npx vue-tsc --noEmit    # TypeScript 类型检查
npm run build            # 生产构建
```

**后端 CI**
```bash
mvn compile              # 编译
mvn test                 # 测试
mvn package -DskipTests  # 打包
```

## 测试账号

| 角色 | 邮箱 | 密码 |
|------|------|------|
| 学生 | student@edu.cn | Student123! |
| 教师 | teacher@edu.cn | Teacher123! |
| 管理员 | admin@edu.cn | Admin123! |

**API 测试**
- Gateway URL: http://localhost:8090
- 认证方式: `Authorization: Bearer <jwt_token>`

## 架构要点

- **虚拟线程**: 业务模块启用 (`spring.threads.virtual.enabled: true`), Gateway 禁用 (WebFlux 不兼容)
- **JWT**: 在 gateway 校验, user-service 生成, 密钥来自 `${JWT_SECRET}`
- **服务间鉴权**: 内部 API 调用使用 `${INTERNAL_API_TOKEN}` 头
- **Gateway 限流**: Redis+Caffeine 混合, 默认 200 permits/sec
- **统一响应**: `{ "code": 200, "message": "...", "data": ..., "traceId": "..." }`
- **编辑器约定**: `.editorconfig` — indent 2 (多数文件), 4 (Java/XML/YAML/SQL), LF 换行, UTF-8
- **构建产物**: 前端 `frontend/dist/`, 后端各模块 `target/*.jar`
- **Docker 构建**: 后端使用 `ARG MODULE_NAME` 多阶段构建, 非 root `spring` 用户运行

## AI 辅助开发

项目配置了 AI 编码规范（`.opencode/rules/`）以保持代码风格和架构一致性，涵盖：

- Spring Cloud Alibaba 最佳实践
- API 安全规范
- 数据库规范
- 前端 Vue 3 规范
- 测试规范

## 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

## 贡献

欢迎贡献！请查看 [贡献指南](docs/contributing/CONTRIBUTING.md) 了解详情。

## 联系方式

- 项目主页: https://github.com/your-username/edu-platform
- 问题反馈: https://github.com/your-username/edu-platform/issues
- 邮箱: your-email@example.com
