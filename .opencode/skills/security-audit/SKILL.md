---
name: security-audit
description: Use when user says "安全审计", "security audit", "安全检查", "security check", "检查安全漏洞". Systematic security review of Spring Boot + Vue projects.
---

# 安全审计

> 触发条件：用户说"安全审计"、"security audit"、"安全检查"、"security check"、"检查安全漏洞"等。

## 审计范围

### 1. 认证与授权
- `@PreAuthorize` 注解完整性
- SpEL 表达式类型匹配（Long vs String）
- 角色权限边界验证
- 资源所有权校验（教师只能操作自己的课程/作业）

### 2. 令牌安全
- JWT 过期时间合理性
- Token 比较是否 timing-safe（`MessageDigest.isEqual`）
- 内部服务令牌（X-Internal-Token）保护
- Refresh Token 机制

### 3. 输入验证
- SQL 注入防护（MyBatis-Plus 参数化查询）
- CSV 注入防护（`=`, `+`, `-`, `@` 前缀）
- 文件上传类型校验（Magic Bytes）
- 敏感词过滤（ContentModerationService）

### 4. 会话安全
- WebSocket 认证（握手时强制 token）
- Session 固定防护
- 并发会话控制

### 5. 数据保护
- PII 端点授权（getById, batch）
- 密码重置流程安全性
- 审计日志完整性

## 审计流程

### Step 1: 扫描 @PreAuthorize
```bash
# 查找所有 Controller 中的 @PreAuthorize
grep -rn "@PreAuthorize" backend/*/src/main/java/**/*Controller.java
```

检查项：
- [ ] Long 参数是否加 `.toString()`
- [ ] 是否有端点缺少授权注解
- [ ] 角色检查是否足够（admin/teacher/student）

### Step 2: 检查 Token 比较
```bash
# 查找 String.equals 用于 token 比较
grep -rn "\.equals(internalToken)" backend/
grep -rn "\.equals(requestInternalToken)" backend/
```

检查项：
- [ ] 是否使用 `MessageDigest.isEqual()`
- [ ] 是否有 timing attack 风险

### Step 3: 检查 CSV 导出
```bash
# 查找 CSV 导出方法
grep -rn "escapeCsv\|text/csv" backend/
```

检查项：
- [ ] 是否过滤 `=`, `+`, `-`, `@` 前缀
- [ ] 是否正确转义逗号和引号

### Step 4: 检查文件上传
```bash
# 查找文件上传校验
grep -rn "MAGIC_BYTES\|validateMagicBytes" backend/
```

检查项：
- [ ] Magic Bytes 是否覆盖所有允许类型
- [ ] 文件大小限制是否合理

### Step 5: 检查异常处理
```bash
# 查找 RuntimeException 使用
grep -rn "throw new RuntimeException" backend/
```

检查项：
- [ ] 是否使用 BusinessException
- [ ] log.error 是否包含异常堆栈

## 常见漏洞模式

### @PreAuthorize 类型不匹配
```java
// ❌ 错误：Long == String 永远 false
@PreAuthorize("#studentId == authentication.principal")

// ✅ 正确：转换为 String 比较
@PreAuthorize("#studentId.toString() == authentication.principal")
```

### Timing Attack
```java
// ❌ 错误：String.equals 短路
return requestToken.equals(expectedToken);

// ✅ 正确：常量时间比较
return MessageDigest.isEqual(
    requestToken.getBytes(StandardCharsets.UTF_8),
    expectedToken.getBytes(StandardCharsets.UTF_8));
```

### CSV 注入
```java
// ❌ 错误：直接输出
writer.println(value);

// ✅ 正确：过滤危险前缀
if (value.matches("^[=+\\-@\\t\\r].*")) {
    value = "'" + value;
}
```

### 缺少资源归属校验
```java
// ❌ 错误：只检查角色
if (!hasTeacherRole()) { return 403; }

// ✅ 正确：检查资源归属
Course course = courseMapper.selectById(courseId);
if (!course.getTeacherId().equals(currentUserId)) {
    return 403;
}
```

## 输出格式

审计完成后，按以下格式输出：

```
## 安全审计报告

### 发现问题
| # | 文件 | 行号 | 严重程度 | 问题描述 |
|---|------|------|----------|----------|

### 修复建议
| # | 修复方案 | 优先级 |
|---|----------|--------|

### 统计
- Critical: X 个
- High: X 个
- Medium: X 个
- Low: X 个
```

## 注意事项

- 审计是只读操作，不修改代码
- 发现问题后由用户决定是否修复
- 优先修复 Critical 和 High 级别问题
- 某些"问题"可能是设计意图（如 SecurityConfig.permitAll）
