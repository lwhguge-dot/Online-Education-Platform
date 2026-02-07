# 智慧课堂监控系统

## 📊 监控架构

本项目使用 **Prometheus + Grafana** 构建完整的可观测性体系。

```
Spring Boot 应用
    ↓ (暴露 /actuator/prometheus)
Prometheus (收集指标)
    ↓
Grafana (可视化展示)
```

---

## 🎯 监控内容

### 1. 系统指标
- CPU 使用率
- 内存使用情况
- JVM 堆内存、GC 情况
- 线程池状态

### 2. 应用指标
- HTTP 请求数量、延迟
- 数据库连接池状态
- 服务健康状态
- 错误率统计

### 3. 业务指标
- API 调用统计
- 服务响应时间
- 请求成功率

---

## 🚀 快速开始

### 1. 启动监控服务

```bash
# 启动所有服务（包括 Prometheus 和 Grafana）
docker-compose up -d

# 查看服务状态
docker-compose ps
```

### 2. 访问监控界面

#### Prometheus
- **URL**: http://localhost:9090
- **功能**: 查看原始指标数据、执行 PromQL 查询

#### Grafana
- **URL**: http://localhost:3000
- **默认账号**: admin
- **默认密码**: admin
- **功能**: 可视化仪表盘、告警配置

---

## 📈 Grafana 仪表盘

### 预配置仪表盘：智慧课堂 - 微服务监控

包含以下面板：

1. **HTTP 请求速率** - 实时请求 QPS
2. **平均响应时间** - API 响应延迟
3. **JVM 内存使用** - 堆内存、非堆内存
4. **系统 CPU 使用率** - 各服务 CPU 占用
5. **服务健康状态** - 各微服务在线状态

### 访问仪表盘

1. 登录 Grafana: http://localhost:3000
2. 左侧菜单 → Dashboards → Browse
3. 选择 "智慧课堂 - 微服务监控"

---

## 🔍 监控端点

所有微服务都暴露了以下 Actuator 端点：

| 端点 | 说明 | URL 示例 |
|------|------|---------|
| `/actuator/health` | 健康检查 | http://localhost:8081/actuator/health |
| `/actuator/metrics` | 指标列表 | http://localhost:8081/actuator/metrics |
| `/actuator/prometheus` | Prometheus 格式指标 | http://localhost:8081/actuator/prometheus |
| `/actuator/info` | 应用信息 | http://localhost:8081/actuator/info |

### 各服务端口

- Gateway: 8090
- User Service: 8081
- Course Service: 8082
- Homework Service: 8083
- Progress Service: 8084

---

## 📝 Prometheus 查询示例

### 1. 查看 HTTP 请求速率
```promql
rate(http_server_requests_seconds_count[1m])
```

### 2. 查看平均响应时间
```promql
rate(http_server_requests_seconds_sum[1m]) / rate(http_server_requests_seconds_count[1m])
```

### 3. 查看 JVM 内存使用
```promql
jvm_memory_used_bytes{application="user-service"}
```

### 4. 查看 CPU 使用率
```promql
system_cpu_usage * 100
```

### 5. 查看服务在线状态
```promql
up{job="user-service"}
```

---

## 🛠️ 配置文件说明

### Prometheus 配置
- **位置**: `monitoring/prometheus/prometheus.yml`
- **抓取间隔**: 15秒
- **监控目标**: 所有微服务的 `/actuator/prometheus` 端点

### Grafana 配置
- **数据源**: `monitoring/grafana/provisioning/datasources/prometheus.yml`
- **仪表盘**: `monitoring/grafana/provisioning/dashboards/`

---

## 🔧 自定义监控指标

### 在代码中添加自定义指标

```java
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final Counter loginCounter;

    public UserService(MeterRegistry registry) {
        this.loginCounter = Counter.builder("user.login.count")
            .description("用户登录次数")
            .tag("service", "user-service")
            .register(registry);
    }

    public void login() {
        // 业务逻辑
        loginCounter.increment();
    }
}
```

### 在 Prometheus 中查询

```promql
user_login_count_total
```

---

## 📊 监控最佳实践

### 1. 设置合理的告警规则
- CPU 使用率 > 80%
- 内存使用率 > 85%
- 错误率 > 5%
- 响应时间 > 1s

### 2. 定期检查仪表盘
- 每天查看关键指标
- 关注异常趋势
- 及时发现性能瓶颈

### 3. 优化查询性能
- 使用合适的时间范围
- 避免过于复杂的 PromQL
- 合理设置抓取间隔

---

## 🐛 故障排查

### 问题 1: Prometheus 无法抓取指标

**检查步骤**:
```bash
# 1. 检查服务是否启动
docker-compose ps

# 2. 检查 Actuator 端点是否可访问
curl http://localhost:8081/actuator/prometheus

# 3. 检查 Prometheus 配置
docker-compose logs prometheus
```

### 问题 2: Grafana 无法连接 Prometheus

**检查步骤**:
```bash
# 1. 检查 Prometheus 是否运行
curl http://localhost:9090/-/healthy

# 2. 检查 Grafana 数据源配置
# 登录 Grafana → Configuration → Data Sources → Prometheus
```

### 问题 3: 仪表盘没有数据

**可能原因**:
- 服务刚启动，还没有收集到数据（等待 15-30 秒）
- Prometheus 抓取失败（检查 Prometheus Targets 页面）
- 时间范围选择不正确（调整 Grafana 时间范围）

---

## 📚 参考资源

- [Prometheus 官方文档](https://prometheus.io/docs/)
- [Grafana 官方文档](https://grafana.com/docs/)
- [Spring Boot Actuator 文档](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer 文档](https://micrometer.io/docs)

---

## 🎓 下一步

1. **添加告警规则** - 配置 Alertmanager
2. **集成日志系统** - 添加 Loki 或 ELK
3. **添加分布式追踪** - 集成 Jaeger
4. **优化仪表盘** - 根据业务需求定制

---

**监控系统已就绪！** 🎉

现在你可以实时监控所有微服务的健康状况和性能指标。
