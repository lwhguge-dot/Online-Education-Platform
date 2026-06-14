---
name: code-review
description: Use when user says "代码审查", "code review", "审查代码", "检查代码质量". Perform project-specific full-stack code review with severity-ranked findings and fixes.
---

# 全栈代码审查

> 触发条件：用户说"代码审查"、"code review"、"审查代码"、"检查代码质量"、"review"等。

## 项目上下文

- **前端**: Vue 3.5 + Vite 7 + TypeScript 5.9 (strict) + Pinia 3 + Tailwind CSS 4
- **后端**: Spring Boot 3.2 + Java 21 + Spring Cloud Alibaba (Nacos, Gateway)
- **模块**: gateway(8090), user-service(8081), course-service(8082), homework-service(8083), progress-service(8084), common
- **编码规范**: `.opencode/rules/` 下的 8 个规范文件

## 审查流程

### Phase 1: 扫描项目结构

```bash
# 前端结构
frontend/src/ — views/, composables/, stores/, services/, components/, types/, utils/, router/

# 后端结构
backend/ — gateway/, user-service/, course-service/, homework-service/, progress-service/, common/
```

读取关键配置文件：
- `frontend/tsconfig.json` — TypeScript 配置
- `frontend/vite.config.ts` — 构建配置
- `AGENTS.md` — 项目约定和 Gotchas
- `.opencode/rules/` — 编码规范（按需读取相关规则）

### Phase 2: 按维度审查

#### 维度 1: 安全 (Critical)

**前端安全检查：**
- `v-html` / `innerHTML` 使用 → XSS 风险
- Token 存储方式（应仅 sessionStorage）
- 心跳检测和强制登出逻辑
- API 请求中是否暴露敏感信息
- Sentry 是否过滤敏感数据

**后端安全检查：**
- JWT 校验完整性（JwtAuthFilter）
- 签名校验（timing-safe 比较）
- SQL 注入风险（MyBatis Plus 注解模式，#{} vs ${}）
- 权限控制（@PreAuthorize / @RequestHeader）
- 内部 API 鉴权（X-Internal-Token）
- 限流配置（RateLimitFilter）

#### 维度 2: 类型安全 (High)

**前端 TypeScript 检查：**
```bash
npm run type-check 2>&1 | Select-String "error TS" | Measure-Object | Select-Object -ExpandProperty Count
```
- `any` 类型使用
- 不安全的类型断言（`as any`, `as unknown`）
- 缺失的泛型参数
- `Result<T>` 默认泛型是否为 `unknown`

**后端 Java 检查：**
```bash
mvn -T 1C clean compile -s settings.xml -q
```
- Lombok 使用是否合规（Entity: @Getter+@Setter, DTO: @Data）
- JSR303 Bean Validation 是否完整
- 异常处理是否统一

#### 维度 3: 架构 (Warning)

**前端架构：**
- Store 是否使用 setup store 风格
- Composable 是否遵循单一职责
- 组件是否过大（>300 行）
- API 模块拆分是否合理

**后端架构：**
- Controller → Service → Mapper 层次是否清晰
- FeignClient fallback 是否实现
- 跨服务事务一致性
- 异常处理：全局 @RestControllerAdvice

#### 维度 4: 规范 (Suggestion)

**前端规范：**
- ESLint 检查：`npm run lint`
- 未使用的导入/变量
- 组件命名是否一致
- CSS 是否使用 Tailwind 而非自定义样式

**后端规范：**
- 日志是否使用 @Slf4j（非 System.out.println）
- 接口入参是否使用 @Valid
- API 响应是否符合统一格式 `{ code, message, data, traceId }`
- 测试覆盖率

### Phase 3: 输出格式

按文件分组，每个问题标注：

```
🔴 Critical | 文件路径:行号 | 问题描述 | 修复建议
🟡 Warning  | 文件路径:行号 | 问题描述 | 修复建议
🔵 Suggestion | 文件路径:行号 | 问题描述 | 修复建议
```

### Phase 4: 修复（可选）

如果用户要求修复，按优先级顺序：
1. 🔴 Critical — 立即修复
2. 🟡 Warning — 修复
3. 🔵 Suggestion — 建议修复

每个修复后验证：
```bash
# 前端验证
npm run type-check  # 类型检查
npm run lint        # 代码风格

# 后端验证
mvn -T 1C clean compile -s settings.xml -q  # 编译
mvn -T 1C test -s settings.xml               # 测试
```

## 审查范围选项

| 用户说 | 审查范围 |
|--------|----------|
| "审查前端" / "前端审查" | 仅 frontend/src/ |
| "审查后端" / "后端审查" | 仅 backend/ |
| "全栈审查" / "代码审查" | 前端 + 后端 |
| "安全审查" | 仅维度 1 (安全) |
| "审查 gateway" | 仅 backend/gateway/ |
| "审查 user-service" | 仅 backend/user-service/ |

## 行为准则

- 默认中文沟通
- 所有决策基于真实文件，不做臆测
- 审查前先读取 `.opencode/rules/` 中的相关规范
- 每个发现必须引用具体文件和行号
- 修复前确认用户要求（除非明确说"全部修复"）
- 修复后必须运行验证命令
- 使用 `hindsight_retain` 记录重要修复
