---
name: error-debug
description: Use when user says "报错了", "出错了", "error", "exception", "失败". Debug errors, exceptions, and failures in frontend/backend/Docker.
---

# 错误排查

> 触发条件：用户说"报错了"、"出错了"、"error"、"exception"、"失败"等。

## 排查流程

### 1. 定位错误来源

| 错误类型 | 检查位置 |
|----------|----------|
| 前端报错 | 浏览器控制台 / `frontend/` 源码 |
| 后端报错 | `docker compose logs <service>` |
| 编译错误 | `mvn compile` / `npm run build` |
| 运行时错误 | 服务日志 + 堆栈跟踪 |

### 2. 收集信息

```bash
# 后端日志
docker compose logs --tail=100 <service-name>

# 前端构建日志
npm run build 2>&1

# 后端编译
mvn clean compile -s settings.xml

# Docker 状态
docker compose ps
docker stats --no-stream
```

### 3. 常见错误速查

| 错误 | 原因 | 修复 |
|------|------|------|
| `Connection refused` | 服务未启动 | `docker compose up -d` |
| `ECONNREFUSED` | 端口未监听 | 检查服务端口配置 |
| `JWT validation failed` | Token 过期/无效 | 重新登录获取新 Token |
| `Duplicate key value` | 数据重复 | 检查唯一约束 |
| `OutOfMemoryError` | 内存不足 | 增大 JVM 堆 / 容器内存 |
| `CORS error` | 跨域配置 | 检查 Gateway CORS 配置 |
| `403 Forbidden` | 权限不足 | 检查用户角色和权限 |
| `404 Not Found` | 路由错误 | 检查 API 路径和 Gateway 路由 |

### 4. 前端错误排查

```bash
# 类型检查
npm run type-check

# Lint 检查
npm run lint

# 测试
npm run test
```

### 5. 后端错误排查

```bash
# 编译检查
mvn clean compile -s settings.xml

# 单元测试
mvn test -s settings.xml

# 检查依赖
mvn dependency:tree -s settings.xml
```

## 堆栈跟踪分析

```
com.eduplatform.xxx.XxxException: 错误信息
    at com.eduplatform.xxx.XxxService.method(XxxService.java:123)  ← 定位到这里
    at com.eduplatform.xxx.XxxController.handle(XxxController.java:45)
```

**关键：** 找到 `at com.eduplatform.xxx` 开头的行，这是项目代码。

## 行为准则

- 先收集完整错误信息，再猜测原因
- 不要盲目修改代码，先理解错误
- 使用 `hindsight_retain` 记录重要 bug 和修复方案
