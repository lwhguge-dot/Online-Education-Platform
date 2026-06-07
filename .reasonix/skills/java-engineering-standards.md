---
name: java-engineering-standards
description: Java 21 / Spring Boot 3 / Lombok / MapStruct / JSR303 / exception hierarchy — backend engineering standards.
---

# Java Engineering Standards

Establish and enforce Java production coding standards: Lombok conventions, MapStruct mapping, JSR303 validation, global exception handling, logging guidelines, and code review checklists.

## Use this skill when

- Setting up project coding conventions
- Writing DTO/VO/Entity mapping with MapStruct
- Configuring Lombok annotations (avoid anti-patterns)
- Implementing JSR303 Bean Validation
- Designing global exception hierarchy and handling
- Establishing logging standards (SLF4J + Logback)
- Performing code review for Java code quality

## Do not use this skill when

- Using Kotlin or other JVM languages
- The project already has an established standard that overrides these

## Instructions

### Lombok Convention

| Annotation | Use | Anti-Pattern |
|------------|-----|-------------|
| `@Data` | DTO / VO only | Do NOT use on JPA/MyBatis-Plus Entity |
| `@Getter` + `@Setter` | Entity classes | Preferred over @Data for entities |
| `@Builder` | Complex DTO construction | Don't combine with @Data on same class |
| `@Slf4j` | All service classes | Always use, never `System.out.println` |
| `@RequiredArgsConstructor` | Constructor injection | Preferred over @Autowired on field |

### Exception Hierarchy

```
BaseException (extends RuntimeException)
 ├── BusinessException       # Known business errors
 ├── ResourceNotFoundException # 404 cases
 ├── ValidationException     # Input validation failures
 ├── AuthException           # Auth / authorization
 └── SystemException         # Infrastructure errors
```

### Logging Levels

| Level | Use Case | Example |
|-------|----------|---------|
| ERROR | System failures requiring immediate attention | DB connection fail |
| WARN | Unexpected but recoverable | Retry exhausted |
| INFO | Business milestones | Order created: #12345 |
| DEBUG | Development diagnostics | SQL parameters |
| TRACE | Step-by-step flow | Filter chain traversal |

## Spring Boot 防幻觉禁止清单

### 1. NEVER 使用 @Autowired 字段注入 — 必须构造器注入

❌ **DO NOT** — 字段注入，不可测试、隐藏依赖
✅ **DO** — 构造器注入（Lombok `@RequiredArgsConstructor` 精简）

### 2. NEVER `catch (Exception e) {}` 静默吞异常

❌ **DO NOT** — 吞掉异常，无日志、无重抛，问题不可追踪
✅ **DO** — 记录日志并重新抛出业务异常

### 3. NEVER 在 @Data 实体上使用 @ToString 导致循环引用

❌ **DO NOT** — JPA/MyBatis-Plus 实体上用 `@Data`（自带 `@ToString`），双向关联触发 `StackOverflowError`
✅ **DO** — 仅用 `@Getter`/`@Setter`，显式排除双向关联

### 4. NEVER 在 Controller 中写业务逻辑

❌ **DO NOT** — Controller 混杂校验、计算、持久化
✅ **DO** — Controller 仅做路由，Service 承载逻辑

## 中文执行层

### 触发条件
- Use when writing Java production code, reviewing code quality, configuring annotations, designing exception hierarchy.

### 前置条件
- 确认项目使用的 Java 版本和框架版本
- 确认已引入 Lombok / MapStruct 等依赖

### 执行步骤
1. 确定目标编码维度（Lombok / MapStruct / 验证 / 异常 / 日志）
2. 按本 skill 的约定规则编码
3. 确保所有异常通过 GlobalExceptionHandler 统一处理
4. 日志按级别分级输出，避免敏感信息泄露

### 完成证据
- 代码无 IDE 编译警告
- 异常返回统一错误响应格式 `{code, message, data}`
- 日志按 RollingFile 归档配置
