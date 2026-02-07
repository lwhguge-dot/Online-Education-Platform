# 后端优化落地与验收说明

## 覆盖范围

- P0：镜像瘦身、JVM 参数生效、Redis Stream 超时优化、敏感配置外置。
- P1：采样率生产默认值、缓存能力增强、资源限制与日志滚动、生产端口收敛覆盖文件。
- P2：网关限流器内存安全改造、限流参数化、Sentinel 状态码语义修复。

## 本次改动文件

- `backend/Dockerfile`
- `docker-compose.yml`
- `docker-compose.prod.yml`
- `.env.example`
- `backend/gateway/src/main/java/com/eduplatform/gateway/filter/RateLimitFilter.java`
- `backend/gateway/src/main/java/com/eduplatform/gateway/config/RateLimiterConfig.java`
- `backend/gateway/src/main/java/com/eduplatform/gateway/config/SentinelConfig.java`
- `backend/gateway/src/main/resources/application.yml`
- `backend/user-service/src/main/resources/application.yml`
- `backend/course-service/src/main/resources/application.yml`
- `backend/homework-service/src/main/resources/application.yml`
- `backend/progress-service/src/main/resources/application.yml`
- `backend/user-service/src/main/java/com/eduplatform/user/config/RedisStreamConfig.java`
- `backend/homework-service/src/main/java/com/eduplatform/homework/config/RedisStreamConfig.java`
- `backend/gateway/pom.xml`
- `backend/user-service/pom.xml`
- `backend/course-service/pom.xml`
- `backend/homework-service/pom.xml`
- `backend/progress-service/pom.xml`

## 验收命令

```powershell
mvn -f backend/pom.xml -DskipTests package
mvn -f backend/pom.xml test -DskipITs
docker compose up -d --build gateway user-service course-service homework-service progress-service
docker compose ps
docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}" | Select-String -Pattern "demo-gateway|demo-user-service|demo-course-service|demo-homework-service|demo-progress-service"
docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}"
```

## 关键验收结果（示例）

- 后端 5 个服务镜像体积约 `402MB~443MB`，较之前 `986MB` 显著下降。
- 5 个服务 `JAVA_OPTS` 生效，进程启动参数包含 `-Xms128m -Xmx256m`。
- `mem_limit=768MiB`、`cpus=1.0`、日志滚动（`10m*3`）已生效。
- Redis 3 秒超时报错（`Redis command timed out`）在本次启动后日志未复现。

## 生产建议

- 使用生产覆盖文件启动：

```powershell
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

- 准备 `.env`（基于 `.env.example`）并替换所有默认弱口令。
- 生产环境限制仅暴露 `gateway`（必要时再开放 Grafana）。
