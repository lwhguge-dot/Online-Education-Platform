---
name: testing-standards
description: Vitest / JUnit 5 / MockMvc / component test / integration test / API test — full stack testing standards.
---

# 全栈测试规范

> 覆盖前端 Vitest（Vue 3 + TypeScript）、后端 JUnit 5 + Spring Boot Test、MockMvc API 测试的完整测试规范。

---

## 1. 前端 Vitest 测试规范

### 1.1 组件测试 — `@vue/test-utils` mount

**核心原则：** 组件测试必须完整注入所有依赖（provide / inject / store / router），不允许隐式依赖导致测试结果不可信。

#### 1.1.1 provide / inject 正确注入

❌ **DO NOT** — 缺少 `provide` 导致子组件 `inject` 拿到 `undefined`，测试静默失败
✅ **DO** — 通过 `global.provide` 精确注入所有依赖

#### 1.1.2 组件异步操作测试

❌ **DO NOT** — 不等待异步完成就断言
✅ **DO** — 使用 `flushPromises` 或 `nextTick` 等待 DOM 更新

#### 1.1.3 事件 emit 测试

❌ **DO NOT** — 不验证 emit 的 payload 内容
✅ **DO** — 精确断言 emit 的数量和 payload

### 1.2 Composable 测试

❌ **DO NOT** — 在组件上下文外直接调用 composable
✅ **DO** — 使用 `@vue/test-utils` 的 `createApp` 或 `withSetup` 包装

### 1.3 Store 测试 — Pinia

❌ **DO NOT** — 直接使用真实 store 实例污染测试之间状态
✅ **DO** — 使用 `createTestingPinia` 为每个测试创建隔离的 store

### 1.4 快照测试

❌ **DO NOT** — 无脑快照一切，一旦 UI 微调就全量更新
✅ **DO** — 对有意义的局部做语义化快照，结合明确断言

---

## 2. 后端 JUnit 5 测试规范

### 2.1 单元测试 — Mock 外部依赖

❌ **DO NOT** — 单元测试依赖真实数据库、Redis、外部 API
✅ **DO** — Mock 所有外部依赖，只测试业务逻辑

### 2.2 集成测试 — `@SpringBootTest` 正确用法

❌ **DO NOT** — 集成测试直接使用生产配置
✅ **DO** — 使用 `@TestConfiguration` + 测试 profile 隔离环境

### 2.3 `@MockBean` vs `@SpyBean` 使用场景

| 注解 | 使用场景 |
|------|----------|
| `@MockBean` | 完全替换 Spring 容器中的 Bean，所有方法默认返回 null |
| `@SpyBean` | 部分 Mock，保留真实 Bean 逻辑，仅覆写特定方法 |

### 2.4 测试数据隔离 — `@Transactional`

❌ **DO NOT** — 手动在 `@AfterEach` 中清理数据，容易遗漏
✅ **DO** — 使用 `@Transactional` 自动回滚

---

## 3. API 测试规范 — MockMvc

### 3.1 MockMvc 完整请求链测试

❌ **DO NOT** — 只测 Controller 层，跳过 Filter / Interceptor / 序列化
✅ **DO** — 使用 MockMvc 覆盖完整请求链路

### 3.2 测试数据管理 — `@Sql` 注解

❌ **DO NOT** — 硬编码依赖现有数据，换环境测试就挂
✅ **DO** — 使用 `@Sql` 注解在测试前初始化精确数据集

### 3.3 测试环境隔离 — Profile

| Profile | 用途 | 数据库 |
|---------|------|--------|
| `test` | 单元测试 + MockMvc 集成测试 | H2 内存 / Testcontainers |
| `dev` | 本地开发联调 | 本地 PostgreSQL |
| `prod` | 生产环境 | 生产 PostgreSQL |

---

## 4. 测试反模式禁止清单

- NEVER 无断言的测试
- NEVER 测试间依赖执行顺序
- NEVER 硬编码测试数据导致脆弱测试
- NEVER 测试间共享可变状态
- NEVER 忽略异常只打 Log 的测试

---

## 5. 中文执行层

### 触发条件
- 编写前端 `.test.ts` / `.spec.ts` 测试文件
- 编写后端 `*Test.java` / `*IT.java` 测试文件
- 审查测试代码质量、排查脆性测试（flaky test）

### 前置条件
- 前端：`vitest` + `@vue/test-utils` + `@pinia/testing` 已安装
- 后端：`spring-boot-starter-test` + `h2` 或 Testcontainers 依赖已配置

### 执行步骤
1. **识别测试类型：** 单元测试 vs 集成测试 vs API 测试
2. **前端组件测试：** mount → global.provide → await flushPromises → 断言
3. **前端 Store 测试：** createTestingPinia → 隔离 store 实例
4. **前端 Composable 测试：** withSetup 包装 → 调用 → 断言
5. **后端单元测试：** @Mock → @InjectMocks → when/thenReturn → verify
6. **后端集成测试：** @SpringBootTest + @ActiveProfiles("test") + @Transactional → @Sql
7. **API 测试：** MockMvc → perform → andExpect(jsonPath)
8. **检查反模式：** 确认无 4 项 NEVER
9. **运行测试：** `npm run test` 或 `mvn test`，确认全部通过

### 完成证据
- 所有测试用例包含明确的断言
- 每个测试独立可运行，不依赖执行顺序
- `@Transactional` 或 `@Sql` 确保测试间数据隔离
- MockMvc 测试覆盖完整请求链
- 测试运行结果：0 failures, 0 errors
