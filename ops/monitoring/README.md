# 智慧课堂监控系统

## 1. 监控架构

项目当前使用 `Prometheus + Grafana + Spring Boot Actuator`，并结合 `Jaeger(OTLP)` 做链路采样。

```text
Spring Boot 微服务
  -> /actuator/prometheus
Prometheus
  -> Grafana
```

关键配置文件：

- `ops/monitoring/prometheus/prometheus.yml`
- `ops/monitoring/grafana/provisioning/datasources/prometheus.yml`
- `ops/monitoring/grafana/provisioning/dashboards/dashboard.yml`
- `ops/monitoring/grafana/provisioning/dashboards/spring-boot-dashboard.json`

## 2. 当前抓取目标（基于 prometheus.yml）

- `prometheus:9090`
- `gateway:8090`
- `user-service:8081`
- `course-service:8082`
- `homework-service:8083`
- `progress-service:8084`

说明：以上地址是 **容器网络内部地址**，由 Prometheus 容器直接访问，不依赖宿主机端口映射。

## 3. 端口可见性说明

### 3.1 `docker-compose.yml`（基础编排）

宿主机可访问端口：

- `80`（frontend）
- `8090`（gateway）
- `8084`（progress-service）
- `8848/9848`（nacos）
- `8858`（sentinel）
- `5432`（postgres）
- `16379`（redis）
- `9000/9001`（minio）
- `9090`（prometheus）
- `3000`（grafana）
- `16686`（jaeger）

### 3.2 `docker-compose.prod.yml`（生产覆盖）

通过 `!reset []` 收敛大部分基础设施端口，仅保留必要入口。  
如果使用 `ops/scripts/docker/Docker启动.ps1` 默认参数，脚本会自动叠加该覆盖文件。

## 4. 快速启动

### 4.1 纯开发编排（便于直接访问监控端口）

```bash
docker compose up -d --force-recreate
docker compose ps
```

### 4.2 使用项目启动脚本

```powershell
powershell -ExecutionPolicy Bypass -File .\ops\scripts\docker\Docker启动.ps1
```

如果仅想做配置预检查：

```powershell
powershell -ExecutionPolicy Bypass -File .\ops\scripts\docker\Docker启动.ps1 -CheckOnly
```

## 5. 访问入口

- Prometheus：`http://localhost:9090`（开发编排下）
- Grafana：`http://localhost:3000`（开发编排下）
- Jaeger：`http://localhost:16686`（开发编排下）

Grafana 登录账号密码来自 `.env`：

- `GRAFANA_ADMIN_USER`
- `GRAFANA_ADMIN_PASSWORD`

## 6. 常用 PromQL

请求速率：

```promql
rate(http_server_requests_seconds_count[1m])
```

平均响应时间：

```promql
rate(http_server_requests_seconds_sum[1m]) / rate(http_server_requests_seconds_count[1m])
```

JVM 内存：

```promql
jvm_memory_used_bytes{application="user-service"}
```

服务可用性：

```promql
up{job="gateway"}
```

## 7. 故障排查

检查容器状态：

```bash
docker compose ps
```

检查 Prometheus 抓取目标状态：

```bash
curl http://localhost:9090/api/v1/targets
```

查看 Prometheus 日志：

```bash
docker compose logs prometheus
```

查看 Grafana 日志：

```bash
docker compose logs grafana
```

## 8. 参考

- [Prometheus 文档](https://prometheus.io/docs/)
- [Grafana 文档](https://grafana.com/docs/)
- [Spring Boot Actuator 文档](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
