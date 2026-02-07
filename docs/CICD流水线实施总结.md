# CI/CD 流水线实施总结

## 实施完成情况

### 已完成的工作

#### 1. CI 持续集成流水线 (`.github/workflows/ci.yml`)

**触发条件**: Push/PR 到 `main`/`develop` 分支

**后端构建与测试**:
- 使用 JDK 21 (Temurin) 编译 Maven 多模块项目
- 启动 PostgreSQL 16 和 Redis 7.0 服务容器用于集成测试
- 自动初始化数据库 (执行 `schema.sql`)
- Maven 依赖缓存加速 (基于 `pom.xml` 哈希)
- 构建产物 (JAR) 上传为 Artifact,保留 3 天
- 测试报告上传,保留 7 天

**前端构建与检查**:
- 使用 Node.js 20 安装依赖和构建
- TypeScript 类型检查 (`vue-tsc --noEmit`)
- 生产版本构建 (`npm run build`)
- npm 依赖缓存加速
- 构建产物 (dist/) 上传为 Artifact

**优化特性**:
- 同分支新提交自动取消旧运行 (`concurrency`)
- 忽略文档变更 (`.md`, `docs/`) 避免无效触发
- CI 汇总 Job 统一报告所有任务状态

---

#### 2. Docker 镜像构建与推送 (`.github/workflows/docker-build.yml`)

**触发条件**: 推送语义化版本 Tag (`v*`) 或手动触发

**特性**:
- **矩阵构建**: 5 个后端微服务并行构建,大幅缩短总时间
- **多标签策略**: 语义化版本 (`1.0.0`, `1.0`) + Git SHA + `latest`
- **GitHub Container Registry (GHCR)**: 免费镜像仓库
- **BuildKit 缓存**: 使用 GitHub Actions Cache 加速构建
- **前端镜像**: 独立构建前端 Nginx 镜像

**镜像命名规范**:
```
ghcr.io/<owner>/edu-platform/gateway:v1.0.0
ghcr.io/<owner>/edu-platform/user-service:v1.0.0
ghcr.io/<owner>/edu-platform/course-service:v1.0.0
ghcr.io/<owner>/edu-platform/homework-service:v1.0.0
ghcr.io/<owner>/edu-platform/progress-service:v1.0.0
ghcr.io/<owner>/edu-platform/frontend:v1.0.0
```

---

#### 3. 安全扫描 (`.github/workflows/security.yml`)

**触发条件**: Push 到 `main`/`develop` + 每周一定时扫描

**CodeQL 静态分析**:
- Java/Kotlin 代码分析 (需要编译)
- JavaScript/TypeScript 代码分析
- 使用 `security-extended` 查询套件 (更多安全规则)
- 结果自动上传到 GitHub Security 标签页

**Trivy 容器扫描**:
- 扫描后端和前端 Docker 镜像
- 检测 CRITICAL 和 HIGH 级别漏洞
- SARIF 格式结果集成到 GitHub Security

**依赖审计**:
- Maven 依赖树分析
- npm audit 检查生产依赖

---

#### 4. 自动部署 (`.github/workflows/deploy.yml`)

**Staging 部署**:
- Docker 镜像构建完成后自动触发
- SSH 连接到 Staging 服务器执行 `docker compose up`
- 支持手动触发

**Production 部署**:
- 仅支持手动触发,需选择版本号
- 部署前自动备份数据库
- **滚动更新**: 逐个微服务更新,保持服务可用性
- 部署失败提示回滚命令

**所需 Secrets 配置**:

| Secret 名称 | 说明 |
|---|---|
| `STAGING_HOST` | Staging 服务器地址 |
| `PRODUCTION_HOST` | Production 服务器地址 |
| `DEPLOY_USER` | SSH 登录用户名 |
| `DEPLOY_KEY` | SSH 私钥 |
| `DEPLOY_PATH` | 项目在服务器上的路径 |

---

#### 5. Dependabot 依赖更新 (`.github/dependabot.yml`)

| 生态系统 | 扫描目录 | 频率 | PR 上限 |
|---|---|---|---|
| Maven | `/backend` | 每周一 | 5 |
| npm | `/frontend` | 每周一 | 5 |
| Docker | `/backend`, `/frontend` | 每月 | - |
| GitHub Actions | `/` | 每周 | - |

**PR 标签规范**: `dependencies` + 模块标签 (`backend`/`frontend`/`docker`/`ci`)

---

#### 6. 其他配置

- **`.dockerignore`** (根目录): 优化 Docker 构建上下文
- **README.md**: 更新 CI/CD 章节、目录结构、技术栈

---

## 流水线架构图

```
                    ┌─────────────┐
                    │   开发者     │
                    │  Push/PR    │
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │  CI 持续集成  │
                    │  ci.yml     │
                    └──────┬──────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
       ┌──────▼──────┐ ┌──▼───┐ ┌──────▼──────┐
       │ 后端构建+测试 │ │ 前端  │ │  安全扫描    │
       │ Maven/JDK21 │ │ 构建  │ │ CodeQL/Trivy│
       └──────┬──────┘ └──┬───┘ └─────────────┘
              │            │
              └────┬───────┘
                   │ Tag v*
            ┌──────▼──────┐
            │ Docker 镜像  │
            │ 构建与推送    │
            │ GHCR        │
            └──────┬──────┘
                   │
            ┌──────▼──────┐
            │  自动部署     │
            │  Staging     │
            └──────┬──────┘
                   │ 手动审批
            ┌──────▼──────┐
            │  生产部署     │
            │  Production  │
            └─────────────┘
```

---

## 使用指南

### 首次配置

1. **初始化 Git 仓库并推送到 GitHub**:
```bash
cd C:\Users\XuShuang\Desktop\demo
git init
git add .
git commit -m "feat: 初始提交"
git remote add origin https://github.com/<your-username>/edu-platform.git
git push -u origin main
```

2. **配置 GitHub Secrets** (Settings > Secrets and variables > Actions):
   - 添加部署相关的 SSH 密钥和服务器信息

3. **启用 GitHub Security** (Settings > Code security and analysis):
   - 启用 CodeQL 分析
   - 启用 Dependabot alerts

### 日常开发流程

```bash
# 1. 在 develop 分支开发
git checkout develop
# ... 编码 ...
git add . && git commit -m "feat: 新功能"
git push origin develop
# → 自动触发 CI

# 2. 创建 PR 合并到 main
# → CI 通过后可合并

# 3. 发布新版本
git checkout main
git merge develop
git tag v1.0.0
git push origin main --tags
# → 自动构建镜像 → 自动部署 Staging

# 4. 确认 Staging 无误后,手动触发 Production 部署
```

---

**实施日期**: 2026-02-07
**状态**: 已完成
