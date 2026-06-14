---
name: backend-check
description: Use when user says "后端检查", "编译检查", "后端测试", "mvn test". Run compile and test for backend code.
---

# 后端代码检查

> 触发条件：用户说"后端检查"、"编译检查"、"后端测试"、"mvn test"、"check backend"等。

## 检查流程（按顺序执行）

### 1. 编译检查
```bash
mvn -T 1C clean compile -s settings.xml -q
```
- `-T 1C` 多线程编译
- `-s settings.xml` 阿里云镜像
- `-q` 静默模式

### 2. 单元测试
```bash
mvn -T 1C test -s settings.xml
```
- 运行所有测试

### 3. 特定模块测试
```bash
# 测试 gateway 模块
mvn -T 1C clean test -pl gateway -s settings.xml 2>&1

# 测试 common 模块
mvn test -pl common -Dtest=InternalTokenFilterTest -s settings.xml 2>&1
```

### 4. 打包验证
```bash
mvn -T 1C package -DskipTests -s settings.xml
```
- 确认打包成功

## 常见问题

| 问题 | 修复 |
|------|------|
| 编译失败 | 检查 Java 版本和依赖 |
| 测试失败 | 检查测试用例和 Mock |
| 依赖冲突 | `mvn dependency:tree -s settings.xml` |
| 端口冲突 | 检查服务端口配置 |

## 行为准则

- 所有 Maven 命令必须带 `-s settings.xml`（阿里云镜像）
- 编译失败不能提交
- 使用 `hindsight_retain` 记录重要修复
