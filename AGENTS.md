# AGENTS.md

## 项目全景

| 维度 | 内容 |
|------|------|
| 前端 | Vue 3.5 + Vite 7.2 + TypeScript 5.9(strict) + Pinia 3.0 + Tailwind CSS 4.0 |
| 后端 | Spring Boot 3.2 + Java 21 + Spring Cloud Alibaba 2023.0.3.4 |
| 微服务 | Nacos(注册/配置), Spring Cloud Gateway, 5 业务模块 |
| 数据库 | PostgreSQL 16 + Redis 7 (DB 分区: 0=gateway,1=user,2=course,3=homework,4=progress) |
| 对象存储 | MinIO |
| 容器化 | Docker Compose (8 服务) |
| 监控 | Sentry (前端, 仅 production 或显式开启) |
| AI 记忆 | Hindsight + Ollama(qwen2.5:3b) |

## 目录所有权

| 目录 | 归属 | 职责 |
|------|------|------|
| `frontend/` | Vue SPA | Vite 构建, `npm run dev` → port 3000 | Docker → port 5173 |
| `backend/` | Maven 多模块 | gateway(8090), user-service(8081), course-service(8082), homework-service(8083), progress-service(8084), common |
| `ops/scripts/docker/` | 运维 | Docker 启停脚本 (PowerShell) |
| `.opencode/rules/` | AI 规范 | 编码约定 (按需注入) |

## 开发者命令

### 前端 (frontend/)

```powershell
npm run dev              # dev server @ localhost:3000, 代理 /api /ws /oss → gateway
npm run type-check       # vue-tsc --noEmit (类型检查)
npm run lint             # eslint src/**/*.{js,ts,vue}
npm run test             # vitest run (Node 环境, 仅 *.test.ts)
npm run build            # vue-tsc && vite build (生产构建)
```

### 后端 (backend/)

```powershell
mvn -T 1C clean test -s settings.xml           # 编译+测试
mvn -T 1C package -DskipTests -s settings.xml  # 打包 (跳过测试)
```

Maven 使用阿里云镜像 (`settings.xml`), 测试依赖已声明但**无实际测试目录**.

### Docker

```powershell
# 完整启动 (含 .env 检查)
powershell -ExecutionPolicy Bypass -File .\ops\scripts\docker\Docker启动.ps1
# 或直接
docker compose up -d --force-recreate
docker compose down        # 停止
docker compose logs -f     # 查看日志
docker compose build       # 构建镜像
```

### CI 命令顺序

前端 CI: `lint:motion` → `vue-tsc --noEmit` → `npm run build` (Node 22, 4GB heap)
后端 CI: `mvn compile` → `mvn test` (内联 PG/Redis 服务) → `mvn package -DskipTests`

## 架构要点

- **虚拟线程**: 业务模块启用 (`spring.threads.virtual.enabled: true`), Gateway 禁用 (WebFlux 不兼容)
- **JWT** 在 gateway 校验, user-service 生成, 密钥来自 `${JWT_SECRET}`
- **服务间鉴权**: 内部 API 调用使用 `${INTERNAL_API_TOKEN}` 头
- **Gateway 限流**: Redis+Caffeine 混合, 默认 200 permits/sec
- **统一响应**: `{ "code": 200, "message": "...", "data": ..., "traceId": "..." }`
- **编辑器约定**: `.editorconfig` — indent 2 (多数文件), 4 (Java/XML/YAML/SQL), LF 换行, UTF-8
- **构建产物**: 前端 `frontend/dist/`, 后端各模块 `target/*.jar`
- **Docker 构建**: 后端使用 `ARG MODULE_NAME` 多阶段构建, 非 root `spring` 用户运行

## 测试账号 (API 测试用)

| 角色 | email | 密码 |
|------|-------|------|
| Student | student@edu.cn | Student123! |
| Teacher | teacher@edu.cn | Teacher123! |
| Admin | admin@edu.cn | Admin123! |

Gateway URL: `http://localhost:8090`, 认证方式: `Authorization: Bearer <jwt_token>`

## 知识文件索引 (AI 规范, 按需注入)

| 领域 | 路径 |
|------|------|
| 前端规范 | `.opencode/rules/vue3-frontend-standards.md` |
| CSS 样式 | `.opencode/rules/tailwind-styling-standards.md` |
| 状态管理 | `.opencode/rules/pinia-state-management.md` |
| Java 规范 | `.opencode/rules/java-engineering-standards.md` |
| 测试规范 | `.opencode/rules/testing-standards.md` |
| TDD 契约 | `.opencode/rules/test-driven-development.md` |

## 记忆系统 (强制)

### Hindsight 记忆系统配置

- **服务地址**: `http://localhost:8888`
- **记忆库 ID**: `deepseek-v2`
- **LLM 提供者**: Ollama (qwen2.5:3b)
- **代理服务**: `ops/scripts/hindsight/json-ollama-proxy.py`

### 操作方式（MiMoCode）

> **MiMoCode 注意：** `hindsight-automemory.ts` 插件不会自动加载，所有操作必须由 AI 手动执行。

- **每轮 recall**：AI 手动调用 `hindsight_recall` 检索历史记忆
- **每轮 retain**：AI 手动调用 `hindsight_retain_batch` / `hindsight_retain` 保存记录

### 手动操作场景

| 场景 | 调用 | category |
|------|------|----------|
| 代码修改完成 | `hindsight_retain_batch` | `code-change` |
| 用户明确表达偏好 | `hindsight_retain` | `preference` |
| 用户纠正你 | `hindsight_retain` | `correction` |
| 会话结束（最终回复前） | `hindsight_session_end` | 内部 snapshot+reflect |
| auto-recall 不够 | `hindsight_recall`（指定 budget=high） | — |
| 查看记忆库状态 | `hindsight_bank_stats` | — |
| 清理低质量记忆 | `hindsight_cleanup` | — |
| 压缩相似记忆 | `hindsight_compress` | — |

### 记忆读取优先级

`preference > correction > code-change > snapshot > experience > world`

### 禁止操作

- ❌ 声称完成但没执行步骤 1 和 2
- ❌ 重复保存已被自动 retain 的对话内容

## 常见陷阱 (Gotchas)

### 环境变量
- **必须创建 `.env`**: 复制 `.env.example` 后填写强密码，Docker 启动脚本会校验
- **密码强度要求**: `JWT_SECRET`/`INTERNAL_API_TOKEN` ≥32位，数据库密码 ≥12位
- **不要提交 `.env`**: 已在 `.gitignore` 中，但 `.env.example` 可提交

### 端口冲突
| 服务 | 开发端口 | Docker 端口 | 说明 |
|------|----------|-------------|------|
| 前端 | 3000 | 5173 | dev server vs nginx |
| Gateway | 8090 | 8090 | 统一 API 入口 |
| PostgreSQL | 5432 | 127.0.0.1:5432 | 仅本机访问 |
| Redis | 16379 | 127.0.0.1:16379 | 非标准端口避免冲突 |

### 前端开发
- **Token 存储**: 使用 `sessionStorage`（非 `localStorage`），关闭标签页即清除
- **心跳检测**: 每 30 秒发送一次，连续 2 次失败强制登出
- **API 代理**: `vite.config.ts` 配置 `/api`、`/ws`、`/oss` 代理到 gateway
- **TypeScript 严格模式**: `tsconfig.json` 启用 strict，类型错误会导致构建失败

### 后端开发
- **虚拟线程**: 业务模块启用，Gateway 禁用（WebFlux 不兼容）
- **JWT 校验**: 仅在 gateway 层校验，user-service 负责生成
- **内部 API 调用**: 必须携带 `X-Internal-Token` 头
- **数据库**: 所有服务共享 `edu_platform` 数据库，通过 schema 分区

### Docker 启动顺序
```
Nacos (健康检查通过)
  → PostgreSQL + Redis (健康检查通过)
    → Gateway (健康检查通过)
      → 业务服务 (user/course/homework/progress)
        → Frontend (依赖 Gateway)
```

### 测试注意事项
- **单元测试**: `vitest` 运行在 Node 环境，仅 `src/**/*.test.ts`
- **E2E 测试**: 使用 Playwright（`frontend/tests/`），需手动运行，不在 npm scripts 中
- **后端测试**: Maven 声明了测试依赖但无实际测试目录，CI 中运行 `mvn test`
- **数据库测试**: CI 使用内联 PostgreSQL/Redis 服务容器

### Sentry 配置
- **开发环境默认关闭**: 需设置 `VITE_SENTRY_ENABLE_IN_DEV=true` 才启用
- **生产环境自动启用**: 但需设置 `VITE_SENTRY_DSN`
- **错误过滤**: 网络错误和 `ResizeObserver` 错误被忽略

### 常见错误
1. **"账号已被禁用"**: 检查用户状态字段（`status=1` 为启用）
2. **"请求正在处理中"**: 防重复提交机制，等待请求完成或刷新页面
3. **"登录已过期"**: Token 失效，需重新登录
4. **Docker 启动失败**: 检查 `.env` 文件是否完整，密码是否符合强度要求

## 默认约定

- 默认中文沟通
- 所有文件 UTF-8
- 所有决策基于真实文件, 不做臆测