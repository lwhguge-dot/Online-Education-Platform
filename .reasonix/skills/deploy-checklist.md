---
name: deploy-checklist
description: Use when user says "准备发布", "发布", "上线", or "deploy". Version verification, DB migration, build, Docker images, env check, deployment, and rollback plan.
---

# 发布检查清单

> 触发条件：用户说"准备发布"、"发布"、"上线"等。

## 发布前检查（按顺序执行）

### 1. 版本确认
- 检查 `pom.xml`（后端）和 `package.json`（前端）版本号
- 确认版本号遵循语义化版本规范（主版本.次版本.修订号）
- 更新 CHANGELOG（如有）

### 2. 数据库迁移
- 检查 `schema.sql` 是否有未应用的变更
- 确认数据库迁移脚本已就绪（如 Flyway/Liquibase）
- 备份当前数据库

### 3. 后端构建
- 运行 `mvn clean package -DskipTests` 验证编译通过
- 确认所有模块编译成功
- 检查 `target/` 产物完整

### 4. Docker 镜像构建
- 运行 `docker compose build` 构建所有镜像
- 确认无构建错误
- 检查镜像大小是否合理

### 5. 环境变量检查
- 对比 `.env.example` 和 `.env`，确认所有必填变量已配置
- 确认生产环境密钥已就绪（JWT Secret、数据库密码等）

### 6. 部署
- 执行 `docker compose -f docker-compose.prod.yml up -d`
- 等待所有服务健康检查通过
- 检查关键 API 端点可访问

### 7. 回滚方案
- 确认上一版本的 Docker 镜像仍在本地
- 确认数据库备份可恢复
- 记录回滚命令
