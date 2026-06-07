---
name: docker-health-diagnose
description: Use when user says "Docker服务不正常", "Docker有问题", "服务挂了", "检查Docker". Check all services, diagnose failures (logs, resources), quick-fix common issues, and verify recovery.
---

# Docker 服务健康诊断

> 触发条件：用户说"Docker 服务不正常"、"Docker 有问题"、"服务挂了"、"检查 Docker"等。

## 执行流程

### 阶段 1：快速检查
```bash
docker compose ps
```
- 列出所有服务的状态
- 标记非 "healthy" 或非 "running" 的服务

### 阶段 2：故障服务分析
对每个异常服务：
1. 查看最近日志：
   ```bash
   docker compose logs --tail=50 <service-name>
   ```
2. 识别错误关键字：`Error`、`Exception`、`FATAL`、`refused`、`timeout`
3. 检查资源使用：
   ```bash
   docker stats --no-stream
   ```

### 阶段 3：常见问题快速修复

| 症状 | 可能原因 | 修复命令 |
|------|----------|----------|
| 服务反复重启 | 配置错误/端口冲突 | `docker compose logs <svc>` 定位 + 修复配置后 `docker compose up -d <svc>` |
| 数据库连接拒绝 | PostgreSQL 未就绪 | 等待 30s 后重试，或 `docker compose restart postgres` |
| 内存不足 | 容器内存超限 | 增大 `docker-compose.yml` 中的 `mem_limit` |
| 磁盘满 | Docker 镜像/卷堆积 | 运行 `scripts/docker-cleanup.ps1` |
| 网络不通 | Docker 网络异常 | `docker compose down && docker compose up -d` |

### 阶段 4：恢复验证
1. 确认所有服务恢复 healthy
2. 验证关键端点可访问（Gateway、Nacos、前端）
3. 调用 `hindsight_retain(category="code-change")` 记录问题与修复

## 参考文件
- `docker-compose.yml`
- `scripts/docker-cleanup.ps1`
- `docker-expert.md` Rule
