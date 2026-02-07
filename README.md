# 智慧课堂在线教育平台 (Smart Classroom)

> 基于 Spring Cloud Alibaba + Vue 3 的现代化微服务在线教育平台。

## 📖 项目简介

智慧课堂是一个功能完善的在线教育平台，采用前后端分离架构。后端基于 Spring Cloud Alibaba 微服务生态，前端采用 Vue 3 + Vite + TailwindCSS 现代化技术栈。系统包含课程管理、作业系统、学习进度追踪、用户权限管理等核心功能，并集成了 Nacos 注册中心、Gateway 网关、Redis 缓存和 MinIO 对象存储等基础设施。

## 🛠️ 技术栈

### 后端 (Backend)

| 技术 | 说明 | 版本 |
| --- | --- | --- |
| **Java** | 编程语言 | JDK 21 |
| **Spring Boot** | 核心框架 | 3.2.0 |
| **Spring Cloud** | 微服务架构 | 2023.0.0 |
| **Spring Cloud Alibaba** | 微服务生态 (Nacos, Sentinel) | 2023.0.3.4 |
| **MyBatis Plus** | ORM 框架 | 3.5.5 |
| **PostgreSQL** | 关系型数据库 | 16 |
| **Redis** | 缓存 / 分布式锁 | 7.0 |
| **MinIO** | 对象存储 | RELEASE |
| **Sentinel** | 流量控制 / 熔断降级 | 1.8.6 |
| **JWT** | 身份认证 | 0.12.3 |

### 前端 (Frontend)

| 技术 | 说明 | 版本 |
| --- | --- | --- |
| **Vue 3** | 核心框架 (Composition API) | 3.5.24 |
| **Vite** | 构建工具 | 7.2.4 |
| **Element Plus** | UI 组件库 | 2.13.1 |
| **TailwindCSS** | 原子化 CSS | 3.4.0 |
| **Pinia** | 状态管理 | 3.0.4 |
| **Vue Router** | 路由管理 | 4.6.4 |
| **Lucide Vue** | 图标库 | 0.562.0 |

### 运维与设施 (DevOps)

- **Docker & Docker Compose**: 容器化部署
- **GitHub Actions**: CI/CD 持续集成与部署
- **Nginx**: 前端反向代理
- **Maven**: 后端构建工具
- **Node.js**: 前端运行环境 (v20+)
- **Prometheus + Grafana**: 监控与可视化
- **Jaeger**: 分布式追踪
- **Sentry**: 前端错误监控
- **Dependabot**: 依赖自动更新
- **CodeQL + Trivy**: 代码安全扫描

## 🏗️ 系统架构

项目采用微服务分层架构，主要模块如下：

- **backend/**: 后端代码仓库
  - `gateway`: API 网关 (端口 8090)，负责路由转发和鉴权。
  - `user-service`: 用户服务，处理注册、登录、角色管理。
  - `course-service`: 课程服务，管理课程信息、章节、资源。
  - `homework-service`: 作业服务，处理作业发布、提交、批改。
  - `progress-service`: 进度服务 (端口 8084)，追踪用户学习和视频进度。
  - `common`: 公共模块，包含全局异常、结果封装、工具类。
- **frontend/**: 前端代码仓库
  - 基于 Vue 3 的单页应用 (SPA)。

## 🚀 快速开始

### 前置要求

- **JDK 21+**
- **Node.js 20+**
- **Docker & Docker Compose** (推荐)
- **MySQL 8.0**
- **Redis**

### 方式一：Docker 一键部署 (推荐)

项目根目录下提供了 `docker-compose.yml`，可一键启动所有服务（包括数据库、中间件、后端服务和前端）。

```bash
# 在项目根目录执行
docker-compose up -d
```

启动后，各服务访问地址如下：

| 服务 | 不公开/容器端口 | 宿主机/映射端口 | 说明 |
| --- | --- | --- | --- |
| **Nginx (Frontend)** | 80 | 80 | 前端访问入口 |
| **Gateway** | 8090 | 8090 | 后端 API 网关 |
| **Nacos** | 8848 | 8848 | 注册配置中心 |
| **Sentinel** | 8858 | 8858 | 流控熔断控制台 |
| **PostgreSQL** | 5432 | 5432 | 数据库 (postgres/123456) |
| **Redis** | 6379 | 16379 | 缓存 (密码 123456) |
| **MinIO** | 9000/9001 | 9000/9001 | 对象存储 |
| **Prometheus** | 9090 | 9090 | 监控指标采集 |
| **Grafana** | 3000 | 3000 | 监控可视化 |
| **Jaeger** | 16686 | 16686 | 分布式追踪 |

### 方式二：本地开发运行

#### 1. 启动基础设施

首先确保 Nacos, PostgreSQL, Redis 已启动。可以使用 Docker 单独启动这些中间件。
**注意**: 首次启动需导入 `backend/schema.sql` 到 PostgreSQL 数据库 `edu_platform` 中。

#### 2. 后端启动

```bash
cd backend
mvn clean package -DskipTests
# 分别启动各个微服务 (User, Course, Homework, Progress, Gateway)
java -jar user-service/target/user-service-1.0.0.jar
# ... 其他服务同理
```

#### 3. 前端启动

```bash
cd frontend
npm install
npm run dev
```

前端默认运行在：

- **Docker 方式启动**: `http://localhost:80` (通过 `docker-compose` 强制指定)
- **本地源码启动**: `http://localhost:3000` (正如 `vite.config.js` 中配置)

## 🔄 CI/CD 流水线

项目采用 **GitHub Actions** 实现自动化 CI/CD，包含以下流水线:

| 流水线 | 触发条件 | 功能 |
| --- | --- | --- |
| **CI 持续集成** | Push/PR 到 main/develop | 后端 Maven 构建+测试、前端构建+类型检查 |
| **Docker 镜像构建** | 推送 tag (v*) 或手动 | 构建所有微服务镜像并推送到 GHCR |
| **安全扫描** | Push 到 main/develop + 每周定时 | CodeQL 静态分析、Trivy 容器扫描、依赖审计 |
| **自动部署** | 镜像构建完成后或手动 | SSH 部署到 Staging/Production |
| **Dependabot** | 每周一自动检查 | Maven/npm/Docker/Actions 依赖更新 |

### 发布流程

```bash
# 1. 开发分支提交代码 → 自动触发 CI
git push origin develop

# 2. 合并到 main 分支
git checkout main && git merge develop

# 3. 打标签触发镜像构建
git tag v1.0.0 && git push origin v1.0.0

# 4. 镜像构建完成后自动部署到 Staging
# 5. 手动触发部署到 Production (需审批)
```

## 📁 目录结构

```
/
├── .github/                # GitHub 配置
│   ├── workflows/          # CI/CD 流水线
│   │   ├── ci.yml          # 持续集成
│   │   ├── docker-build.yml # Docker 镜像构建
│   │   ├── security.yml    # 安全扫描
│   │   └── deploy.yml      # 自动部署
│   └── dependabot.yml      # 依赖更新配置
├── backend/                # 后端微服务源码
│   ├── common/             # 公共模块
│   ├── gateway/            # 网关服务
│   ├── user-service/       # 用户服务
│   ├── course-service/     # 课程服务
│   ├── homework-service/   # 作业服务
│   ├── progress-service/   # 进度服务
│   ├── pom.xml             # 父工程 Maven 配置
│   └── schema.sql          # 数据库初始化脚本
├── frontend/               # 前端源码
│   ├── src/                # Vue 源代码
│   ├── public/             # 静态资源
│   ├── vite.config.ts      # Vite 配置
│   └── tailwind.config.js  # Tailwind 配置
├── monitoring/             # 监控配置
│   ├── prometheus/         # Prometheus 配置
│   └── grafana/            # Grafana 配置
├── docker-compose.yml      # Docker 编排文件
└── README.md               # 项目说明文档
```

## ⚠️ 注意事项

1. **字符编码**: 项目统一使用 **UTF-8** 编码，数据库配置已强制指定。
2. **虚拟线程**: 由于 Spring Cloud Gateway 底层依赖 WebFlux，目前通过环境变量 `SPRING_THREADS_VIRTUAL_ENABLED=false` 禁用了虚拟线程以避免兼容性问题。
3. **Session**: Docker 环境中 Redis Session 配置了过期时间以修复设置失败的问题。

## 🤝 贡献与规范

- **代码风格**: 后端遵循 Google Java Style，前端遵循 Vue 3 风格指南。
- **提交规范**: 请使用 Conventional Commits (feat, fix, docs, refactor 等)。
- **分支管理**: 开发请在 `dev` 分支进行，稳定后合并至 `main`。
