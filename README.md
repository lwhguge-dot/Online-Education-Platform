# 基于 Spring Cloud 的在线教育平台

基于 Spring Cloud Alibaba 的前后端分离微服务项目，提供课程管理、作业发布、学习进度追踪等在线教育功能。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3 + Spring Cloud Alibaba |
| 微服务组件 | Nacos（注册/配置）、Spring Cloud Gateway |
| 数据库 | PostgreSQL + Redis |
| 对象存储 | MinIO |
| 前端 | Vue 3 + Vite + Pinia + TypeScript |
| 容器化 | Docker Compose |
| 语言 | Java 21 + TypeScript |

## 微服务模块

```
gateway          — API 网关，统一入口、JWT 鉴权、限流
user-service     — 用户服务，注册登录、角色管理、消息通知
course-service   — 课程服务，课程 CRUD、章节管理、评论审核
homework-service — 作业服务，作业发布、提交批改、讨论答疑
progress-service — 进度服务，学习追踪、章节测验、成就徽章
common           — 公共模块，通用工具、异常处理、事件消息
```

## 快速启动

### 1. 准备环境

```powershell
# 复制环境变量模板
Copy-Item .env.example .env -Force
```

编辑 `.env`，填写数据库密码、JWT 密钥等必填项。

### 2. 启动服务

```powershell
# 使用启动助手（含 .env 预检查）
powershell -ExecutionPolicy Bypass -File .\ops\scripts\docker\Docker启动.ps1

# 或直接 Docker Compose
docker compose up -d --force-recreate
```

### 3. 访问

| 服务 | 地址 |
|------|------|
| 前端页面 | `http://localhost:5173` |
| API 网关 | `http://localhost:8090` |
| Nacos 控制台 | `http://localhost:8848/nacos` |
| MinIO Console | `http://localhost:9001` |

> 开发环境前端端口为 5173（80 端口留给生产编排使用）。

## 目录结构

```
├── backend/                # 后端微服务（Maven 多模块）
├── frontend/               # 前端工程（Vite）
├── ops/
│   ├── scripts/docker/     # Docker 启停脚本
│   └── scripts/            # 其他运维脚本
├── .github/workflows/      # CI / 安全扫描 / 部署流水线
├── .opencode/rules/        # AI 编码规范
├── docker-compose.yml      # 开发编排
└── docker-compose.prod.yml # 生产编排
```

## 本地开发

### 前端

```powershell
cd frontend
npm install
npm run dev

# 质量检查
npm run type-check && npm run lint && npm run test
```

### 后端

```powershell
cd backend
mvn -T 1C clean test -s settings.xml
mvn -T 1C package -DskipTests -s settings.xml
```

## CI / CD

- `ci.yml` — 后端编译测试 + 前端构建检查
- `security.yml` — CodeQL + Trivy 安全扫描
- `docker-build.yml` — 多服务镜像构建推送
- `deploy.yml` — 远程部署

## AI 辅助开发

项目配置了 AI 编码规范（`.opencode/rules/`）以保持代码风格和架构一致性，涵盖 Spring Cloud Alibaba 最佳实践、API 安全、数据库规范等。
