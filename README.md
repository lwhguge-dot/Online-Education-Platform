# 智慧课堂在线教育平台 (Smart Classroom)

> 基于 **Spring Cloud Alibaba + Vue 3 + TypeScript** 的现代化微服务在线教育平台。
>
> 本科毕业设计项目，借助 Claude Code 辅助开发。

## 项目简介

智慧课堂是一个功能完善的在线教育平台，采用前后端分离的微服务架构。后端基于 Spring Cloud Alibaba 生态（Nacos 注册中心、Sentinel 限流熔断、OpenFeign 服务调用），前端采用 Vue 3 + Vite + TailwindCSS + Element Plus 现代化技术栈。

系统包含 **课程管理、作业批改、学习进度追踪、用户权限管理、学情分析、消息通知** 等核心业务功能，同时集成了 **Prometheus + Grafana 监控、Jaeger 分布式追踪、Sentry 前端错误监控** 等可观测性基础设施。

### 项目规模

| 指标 | 数值 |
| --- | --- |
| 后端 Java 源文件 | 201 个 |
| 前端 Vue / TS 文件 | 96 个 |
| 微服务数量 | 5 个业务服务 + 1 个网关 |
| API 端点 | 224 个 |
| 数据库表 | 29 张 |
| 单元测试用例 | 48 个 |
| Docker 容器 | 13 个 |
| Feign 服务间调用 | 9 个客户端 |

---

## 技术栈

### 后端 (Backend)

| 技术 | 说明 | 版本 |
| --- | --- | --- |
| **Java** | 编程语言 | JDK 21 |
| **Spring Boot** | 核心框架 | 3.2.0 |
| **Spring Cloud** | 微服务架构 | 2023.0.0 |
| **Spring Cloud Alibaba** | 微服务生态 (Nacos, Sentinel) | 2023.0.3.4 |
| **MyBatis Plus** | ORM 框架 | 3.5.5 |
| **PostgreSQL** | 关系型数据库 | 16 |
| **Redis** | 缓存 / 会话管理 / 分布式锁 | 7.0 |
| **MinIO** | 对象存储 (头像、课件) | RELEASE |
| **Sentinel** | 流量控制 / 熔断降级 | 1.8.8 (v6x Jakarta) |
| **JWT (jjwt)** | 身份认证 | 0.12.3 |
| **Micrometer** | 指标采集 (Prometheus) | 1.12.0 |
| **OpenTelemetry** | 分布式追踪 (Jaeger) | 1.32.0 |

### 前端 (Frontend)

| 技术 | 说明 | 版本 |
| --- | --- | --- |
| **Vue 3** | 核心框架 (Composition API) | 3.5.24 |
| **TypeScript** | 类型安全 | 5.9.3 |
| **Vite** | 构建工具 | 7.2.4 |
| **Element Plus** | UI 组件库 | 2.13.1 |
| **TailwindCSS** | 原子化 CSS | 3.4.0 |
| **Pinia** | 状态管理 | 3.0.4 |
| **Vue Router** | 路由管理 | 4.6.4 |
| **Sentry** | 前端错误监控 | 10.38.0 |
| **Lucide Vue** | 图标库 | 0.562.0 |

### 运维与基础设施 (DevOps)

| 技术 | 说明 |
| --- | --- |
| **Docker & Docker Compose** | 容器化一键部署，13 个服务编排 |
| **GitHub Actions** | CI/CD 持续集成 (编译/测试/安全扫描) |
| **Nginx** | 前端反向代理与静态资源服务 |
| **Prometheus + Grafana** | 后端指标监控与可视化仪表板 |
| **Jaeger** | 分布式链路追踪 |
| **Sentry** | 前端运行时错误监控 |
| **CodeQL + Trivy** | 代码安全静态分析与容器漏洞扫描 |
| **Dependabot** | 依赖自动更新检测 |

---

## 系统架构

```
                              ┌──────────┐
                              │  Nginx   │ :80
                              │ (前端SPA) │
                              └────┬─────┘
                                   │
                              ┌────▼─────┐
                     ┌────────┤ Gateway  ├────────┐
                     │        │  :8090   │        │
                     │        └────┬─────┘        │
              ┌──────▼──┐   ┌─────▼────┐   ┌─────▼──────┐
              │  User    │   │  Course  │   │  Homework  │
              │ Service  │   │ Service  │   │  Service   │
              │  :8081   │   │  :8082   │   │   :8083    │
              └──────────┘   └──────────┘   └────────────┘
                     │              │              │
              ┌──────▼──────────────▼──────────────▼──┐
              │          Progress Service :8084        │
              └───────────────────────────────────────┘
                     │              │              │
        ┌────────────▼──┐   ┌──────▼───┐   ┌─────▼────┐
        │  PostgreSQL   │   │  Redis   │   │  MinIO   │
        │    :5432      │   │  :6379   │   │ :9000    │
        └───────────────┘   └──────────┘   └──────────┘
```

### 微服务说明

| 服务 | 端口 | 职责 |
| --- | --- | --- |
| **Gateway** | 8090 | API 网关，统一路由、JWT 鉴权、限流 |
| **user-service** | 8081 | 用户注册/登录、角色管理、会话管理、头像上传、审计日志 |
| **course-service** | 8082 | 课程 CRUD、章节管理、选课/退课、资源管理、公告 |
| **homework-service** | 8083 | 作业发布、学生提交、自动批改、题目讨论 |
| **progress-service** | 8084 | 视频进度追踪、章节测验、完课判定、徽章系统、学情分析 |
| **common** | — | 公共模块：Result 统一响应、GlobalExceptionHandler |

### 服务间调用关系 (OpenFeign)

项目共有 **9 个 Feign 客户端**，所有客户端均配置了 Sentinel 降级兜底方案：

- course-service → user-service, homework-service, progress-service, audit-log
- homework-service → course-service, progress-service, user-service
- progress-service → homework-service
- user-service → course-service

---

## 快速开始

### 前置要求

- **Docker & Docker Compose** (推荐，可一键启动全部服务)
- JDK 21+（仅本地开发时需要）
- Node.js 20+（仅本地开发时需要）

### 方式一：Docker 一键部署 (推荐)

```bash
# 1. 克隆项目
git clone https://github.com/lwhguge-dot/Online-Education-Platform.git
cd Online-Education-Platform

# 2. 一键启动全部 13 个服务
docker compose up -d

# 3. 等待所有服务就绪 (约 2-3 分钟)
docker compose ps
```

也可以使用项目内置的一键脚本（Windows）：

```bash
# 双击运行
tools/scripts/docker/Docker启动.bat

# 或命令行执行
cmd /c tools/scripts/docker/Docker启动.bat
```

启动后各服务访问地址：

| 服务 | 地址 | 说明 |
| --- | --- | --- |
| **前端** | http://localhost | 平台主页面 |
| **API 网关** | http://localhost:8090 | 后端统一入口 |
| **Nacos 控制台** | http://localhost:8848/nacos | 注册中心 (nacos/nacos) |
| **Sentinel 控制台** | http://localhost:8858 | 限流熔断管理 (sentinel/sentinel) |
| **MinIO 控制台** | http://localhost:9001 | 对象存储管理 (admin/admin123) |
| **Grafana 监控** | http://localhost:3000 | 监控仪表板 (admin/admin) |
| **Jaeger 追踪** | http://localhost:16686 | 分布式追踪查询 |
| **Prometheus** | http://localhost:9090 | 原始指标查询 |
| **PostgreSQL** | localhost:5432 | 数据库 (postgres/123456) |
| **Redis** | localhost:16379 | 缓存 (密码 123456) |

### 方式二：本地开发运行

#### 1. 启动基础设施

首先确保 Nacos、PostgreSQL、Redis 已启动（可用 Docker 单独启动）。

**首次需导入数据库：**
```bash
# 连接 PostgreSQL 后执行
psql -h localhost -U postgres -d edu_platform -f backend/schema.sql
```

#### 2. 启动后端

```bash
cd backend
mvn clean package -DskipTests
# 按顺序启动各服务
java -jar user-service/target/user-service-1.0.0.jar
java -jar course-service/target/course-service-1.0.0.jar
java -jar homework-service/target/homework-service-1.0.0.jar
java -jar progress-service/target/progress-service-1.0.0.jar
java -jar gateway/target/gateway-1.0.0.jar
```

#### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
# 访问 http://localhost:3000
```

---

## CI/CD 流水线

项目采用 **GitHub Actions** 实现自动化 CI/CD，每次推送到 main 分支会自动执行以下检查：

| 流水线 | 触发条件 | 功能 | 状态 |
| --- | --- | --- | --- |
| **CI 持续集成** | Push/PR 到 main/develop | 后端 Maven 编译+测试、前端 TypeScript 检查+构建 | 已启用 |
| **安全扫描** | Push 到 main + 每周定时 | CodeQL 静态分析 (Java/JS)、Trivy 容器扫描、依赖审计 | 已启用 |
| **Docker 镜像构建** | 推送 tag (v*) 或手动触发 | 构建所有微服务 Docker 镜像并推送到 GHCR | 已配置 |
| **自动部署** | 镜像构建完成后 | SSH 部署到 Staging/Production | 已配置 |
| **Dependabot** | 每周一自动检查 | Maven/npm/Docker/Actions 依赖更新建议 | 已启用 |

---

## 目录结构

```
Online-Education-Platform/
├── .github/                    # GitHub 配置
│   ├── workflows/              # CI/CD 流水线
│   │   ├── ci.yml              #   持续集成 (编译+测试)
│   │   ├── docker-build.yml    #   Docker 镜像构建
│   │   ├── security.yml        #   安全扫描 (CodeQL+Trivy)
│   │   └── deploy.yml          #   自动部署
│   └── dependabot.yml          # 依赖自动更新配置
│
├── backend/                    # 后端微服务 (Java 21 + Spring Boot 3.2)
│   ├── gateway/                #   API 网关 (:8090)
│   ├── user-service/           #   用户服务 (:8081) — 55 个 Java 文件
│   ├── course-service/         #   课程服务 (:8082) — 54 个 Java 文件
│   ├── homework-service/       #   作业服务 (:8083) — 58 个 Java 文件
│   ├── progress-service/       #   进度服务 (:8084) — 28 个 Java 文件
│   ├── common/                 #   公共模块 (Result, GlobalExceptionHandler)
│   ├── pom.xml                 #   父工程 Maven 配置
│   ├── schema.sql              #   数据库初始化脚本 (29 张表)
│   └── settings.xml            #   Maven 镜像配置
│
├── frontend/                   # 前端 (Vue 3 + TypeScript + Vite)
│   ├── src/
│   │   ├── views/              #   页面组件 (路由级)
│   │   ├── components/         #   可复用组件
│   │   ├── composables/        #   组合式 API 函数
│   │   ├── services/           #   API 请求封装 (Axios)
│   │   ├── stores/             #   Pinia 状态仓库
│   │   ├── router/             #   Vue Router 配置
│   │   ├── types/              #   TypeScript 类型定义
│   │   └── utils/              #   工具函数
│   ├── vite.config.ts          #   Vite 构建配置
│   ├── tailwind.config.js      #   TailwindCSS 配置
│   └── tsconfig.json           #   TypeScript 配置
│
├── monitoring/                 # 监控配置
│   ├── prometheus/             #   Prometheus 采集规则
│   └── grafana/                #   Grafana 仪表板与数据源
│
├── tools/scripts/              # 运维脚本 (Windows)
│   ├── docker/                 #   Docker 启动相关脚本
│   │   ├── Docker启动.bat      #     一键启动入口
│   │   └── Docker启动.ps1      #     启动核心逻辑
│   ├── upload/                 #   Git 上传相关脚本
│   │   ├── Git推送.bat         #     一键推送入口
│   │   └── Git推送.ps1         #     推送核心逻辑
│   ├── verify_progress.ps1     #   进度上报验证脚本
│   └── verify_track.ps1        #   学习轨迹验证脚本
│
├── docker-compose.yml          # Docker 编排 (13 个容器)
├── .gitignore                  # Git 忽略规则
├── CLAUDE.md                   # AI 辅助开发规则
└── README.md                   # 项目说明 (本文件)
```

---

## 数据库设计

数据库共 **29 张表**，使用 PostgreSQL 16，主要模块：

| 模块 | 表 | 说明 |
| --- | --- | --- |
| **用户** | users, user_session, student_profiles, teacher_profiles | 用户信息、会话管理、师生档案 |
| **课程** | courses, chapters, chapter_resources, enrollments, chapter_comments | 课程体系、章节内容、选课关系 |
| **作业** | homeworks, homework_questions, homework_submissions, homework_answers, homework_unlocks, homework_question_discussions | 作业发布、提交批改、讨论互动 |
| **进度** | chapter_progress, chapter_quizzes, chapter_quiz_results, badges, student_badges | 学习进度、章节测验、徽章成就 |
| **系统** | announcements, notifications, course_favorites, learning_status, audit_logs | 公告通知、收藏、学情分析、操作审计 |

---

## 注意事项

1. **字符编码**：项目全局使用 **UTF-8** 编码，数据库连接已强制指定。
2. **虚拟线程**：Spring Cloud Gateway 底层依赖 WebFlux，通过 `SPRING_THREADS_VIRTUAL_ENABLED=false` 禁用虚拟线程。
3. **首次部署**：Docker 首次启动需等待 Nacos 完全就绪后，各微服务才能正常注册。
4. **数据库初始化**：Docker 方式会自动执行 `schema.sql`；本地开发需手动导入。

---

## 贡献与规范

- **代码风格**：后端遵循 Google Java Style，前端遵循 Vue 3 Composition API 规范。
- **提交规范**：使用 Conventional Commits（`feat:`, `fix:`, `docs:`, `refactor:` 等）。
- **AI 辅助**：项目使用 Claude Code 辅助开发，协作规范见 `CLAUDE.md`。
