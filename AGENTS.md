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
| `.reasonix/skills/` | AI 规范 | 编码约定 (按需注入 `/skill-name`) |

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

## 知识文件索引 (AI 技能, 按需调用 `/skill-name`)

| 领域 | 技能名 | 说明 |
|------|--------|------|
| 部署检查 | `deploy-checklist` | 发布前版本确认、数据库迁移、构建、Docker 镜像、环境变量检查、回滚方案 |
| Docker 诊断 | `docker-health-diagnose` | Docker 服务健康诊断、故障分析、常见问题快速修复 |
| 记忆协议 | `hindsight-protocol` | Hindsight 记忆系统协议，code-change 保存和会话快照 |
| 前端规范 | `vue3-frontend-standards` | Vue 3 / Composition API / Pinia / Vite / TypeScript strict |
| CSS 样式 | `tailwind-styling-standards` | Tailwind CSS 4 / Design Tokens / 中国传统色 / 暗色模式 |
| 状态管理 | `pinia-state-management` | Pinia 3 / Setup Store / 持久化策略 / 反模式 |
| Java 规范 | `java-engineering-standards` | Java 21 / Spring Boot 3 / Lombok / MapStruct / JSR303 |
| 测试规范 | `testing-standards` | Vitest / JUnit 5 / MockMvc / 全栈测试 |
| TDD 契约 | `test-driven-development` | Red-Green-Refactor 测试驱动开发 |
| 记忆操作 | `hindsight-auto-memory` | Hindsight 记忆系统操作规范与故障排查 |

## 记忆系统 (强制)

### Hindsight 记忆系统配置

- **服务地址**: `http://127.0.0.1:8888`（Docker 绑定 127.0.0.1，Windows localhost 可能解析到 IPv6）
- **记忆库 ID**: `deepseek-v2`
- **LLM 提供者**: Ollama (qwen2.5:3b)
- **代理服务**: `ops/scripts/hindsight/json-ollama-proxy.py`

### 自动操作

Reasonix 无 OpenCode 插件机制，Hindsight 操作需手动调用 MCP 工具。

已注册的 MCP 工具（需先启动 Hindsight API 服务）：
- `hindsight_retain` / `hindsight_retain_batch` — 保存记忆
- `hindsight_recall` — 检索记忆
- `hindsight_session_end` — 会话结束快照
- `hindsight_healthcheck` — 连通性检查

### MCP 服务器配置

Hindsight MCP 服务器已注册到全局 `~/.reasonix/config.json`，指向 Python 脚本。

```json
{
  "mcpServers": {
    "hindsight": {
      "command": "python",
      "args": ["C:\\Users\\XuShuang\\Desktop\\demo\\ops\\scripts\\hindsight\\hindsight-mcp-server.py"],
      "transport": "stdio"
    }
  }
}
```

前置条件：Python ≥ 3.10，已安装 `mcp` 包（`pip install mcp`）。

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

## 默认约定

- 默认中文沟通
- 所有文件 UTF-8
- 所有决策基于真实文件, 不做臆测
