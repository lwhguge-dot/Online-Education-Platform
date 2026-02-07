# Sentinel 限流熔断配置指南

## 📋 概述

本项目已集成 Alibaba Sentinel 作为流量控制和熔断降级组件,提供以下能力:

- **流量控制**: 防止系统被突发流量压垮
- **熔断降级**: 当依赖服务异常时快速失败,保护系统稳定性
- **系统保护**: 根据系统负载自动限流
- **热点参数限流**: 针对热点数据进行精细化限流

---

## 🚀 快速开始

### 1. 启动 Sentinel Dashboard

```bash
# 使用 Docker 启动脚本会自动启动 Sentinel Dashboard
cd tools/scripts
./Docker启动.bat
```

访问 Sentinel 控制台: http://localhost:8858
- 默认用户名: `sentinel`
- 默认密码: `sentinel`

### 2. 查看服务列表

启动所有微服务后,在 Sentinel 控制台左侧菜单可以看到:
- `gateway` - API 网关
- `user-service` - 用户服务
- `course-service` - 课程服务
- `homework-service` - 作业服务
- `progress-service` - 进度服务

---

## 📊 规则配置

### 流控规则 (Flow Rules)

**场景**: 限制接口 QPS,防止系统过载

#### 示例 1: 限制登录接口 QPS

在 Sentinel 控制台配置:

```json
{
  "resource": "POST:/api/auth/login",
  "limitApp": "default",
  "grade": 1,
  "count": 10,
  "strategy": 0,
  "controlBehavior": 0
}
```

**参数说明**:
- `resource`: 资源名称 (接口路径)
- `limitApp`: 来源应用 (default 表示所有来源)
- `grade`: 阈值类型 (0=线程数, 1=QPS)
- `count`: 阈值 (每秒最多 10 次请求)
- `strategy`: 流控模式 (0=直接, 1=关联, 2=链路)
- `controlBehavior`: 流控效果 (0=快速失败, 1=Warm Up, 2=排队等待)

#### 示例 2: 热点参数限流 (针对特定用户 ID)

```java
@GetMapping("/api/users/{userId}")
@SentinelResource(value = "getUserById", blockHandler = "handleBlock")
public Result<UserVO> getUserById(@PathVariable Long userId) {
    // 业务逻辑
}
```

在控制台配置热点规则:
- 参数索引: 0 (第一个参数 userId)
- 单机阈值: 5 (每秒最多 5 次)
- 统计窗口: 1 秒

---

### 熔断降级规则 (Degrade Rules)

**场景**: 当服务异常率过高或响应时间过长时,自动熔断

#### 示例 1: 慢调用比例熔断

```json
{
  "resource": "GET:/api/courses",
  "grade": 0,
  "count": 1000,
  "timeWindow": 10,
  "minRequestAmount": 5,
  "slowRatioThreshold": 0.5
}
```

**参数说明**:
- `grade`: 熔断策略 (0=慢调用比例, 1=异常比例, 2=异常数)
- `count`: RT 阈值 (响应时间超过 1000ms 视为慢调用)
- `timeWindow`: 熔断时长 (10 秒后尝试恢复)
- `minRequestAmount`: 最小请求数 (至少 5 次请求才触发熔断)
- `slowRatioThreshold`: 慢调用比例阈值 (50% 的请求是慢调用时熔断)

#### 示例 2: 异常比例熔断

```json
{
  "resource": "POST:/api/submissions",
  "grade": 1,
  "count": 0.5,
  "timeWindow": 10,
  "minRequestAmount": 5
}
```

**说明**: 当异常比例超过 50% 时触发熔断,持续 10 秒

---

### 系统保护规则 (System Rules)

**场景**: 根据系统整体负载自动限流

```json
{
  "highestSystemLoad": 10.0,
  "avgRt": 1000,
  "maxThread": 100,
  "qps": 1000,
  "highestCpuUsage": 0.8
}
```

**参数说明**:
- `highestSystemLoad`: 最大系统负载 (Load1 超过 10 时限流)
- `avgRt`: 平均响应时间阈值 (超过 1000ms 限流)
- `maxThread`: 最大并发线程数
- `qps`: 系统级 QPS 阈值
- `highestCpuUsage`: CPU 使用率阈值 (超过 80% 限流)

---

## 🔧 规则持久化 (Nacos)

### 为什么需要持久化?

Sentinel 控制台配置的规则默认存储在内存中,服务重启后会丢失。通过 Nacos 持久化,规则可以永久保存。

### 配置步骤

#### 1. 在 Nacos 中创建配置

访问 Nacos 控制台: http://localhost:8848/nacos

创建以下配置:

**流控规则** (Data ID: `user-service-flow-rules`, Group: `SENTINEL_GROUP`):

```json
[
  {
    "resource": "POST:/api/auth/login",
    "limitApp": "default",
    "grade": 1,
    "count": 10,
    "strategy": 0,
    "controlBehavior": 0
  },
  {
    "resource": "GET:/api/users",
    "limitApp": "default",
    "grade": 1,
    "count": 50,
    "strategy": 0,
    "controlBehavior": 0
  }
]
```

**降级规则** (Data ID: `user-service-degrade-rules`, Group: `SENTINEL_GROUP`):

```json
[
  {
    "resource": "GET:/api/users/{userId}",
    "grade": 0,
    "count": 1000,
    "timeWindow": 10,
    "minRequestAmount": 5,
    "slowRatioThreshold": 0.5
  }
]
```

#### 2. 服务自动加载规则

服务启动时会自动从 Nacos 加载规则,无需手动操作。

---

## 📝 代码示例

### 1. 使用 @SentinelResource 注解

```java
@Service
public class UserService {

    /**
     * 使用 Sentinel 保护方法
     *
     * @param userId 用户 ID
     * @return 用户信息
     */
    @SentinelResource(
        value = "getUserById",           // 资源名称
        blockHandler = "handleBlock",    // 限流/降级处理方法
        fallback = "handleFallback"      // 异常降级处理方法
    )
    public UserVO getUserById(Long userId) {
        // 业务逻辑
        return userMapper.selectById(userId);
    }

    /**
     * 限流/降级处理方法
     *
     * 注意: 方法签名必须与原方法一致,并额外添加 BlockException 参数
     */
    public UserVO handleBlock(Long userId, BlockException ex) {
        log.warn("用户查询被限流: userId={}", userId);
        return UserVO.builder()
            .id(userId)
            .username("系统繁忙")
            .build();
    }

    /**
     * 异常降级处理方法
     *
     * 注意: 方法签名必须与原方法一致,并额外添加 Throwable 参数
     */
    public UserVO handleFallback(Long userId, Throwable throwable) {
        log.error("用户查询异常: userId={}", userId, throwable);
        return UserVO.builder()
            .id(userId)
            .username("服务异常")
            .build();
    }
}
```

### 2. OpenFeign 集成 Sentinel

```java
@FeignClient(
    name = "course-service",
    fallback = CourseServiceClientFallback.class  // 降级处理类
)
public interface CourseServiceClient {

    @GetMapping("/api/courses/{courseId}")
    Result<CourseVO> getCourseById(@PathVariable Long courseId);
}

/**
 * Feign 降级处理类
 */
@Component
public class CourseServiceClientFallback implements CourseServiceClient {

    @Override
    public Result<CourseVO> getCourseById(Long courseId) {
        log.warn("课程服务调用失败,触发降级: courseId={}", courseId);
        return Result.fail("课程服务暂时不可用");
    }
}
```

---

## 🎯 推荐配置

### Gateway 网关层

| 接口路径 | QPS 限制 | 说明 |
|---------|---------|------|
| `/api/auth/login` | 10 | 登录接口,防止暴力破解 |
| `/api/auth/register` | 5 | 注册接口,防止恶意注册 |
| `/api/files/**` | 20 | 文件上传,防止资源耗尽 |
| 其他接口 | 100 | 默认限流 |

### 微服务层

| 服务 | 资源 | 熔断策略 | 阈值 |
|-----|------|---------|------|
| user-service | 数据库查询 | 慢调用比例 | RT > 500ms, 比例 > 50% |
| course-service | 文件处理 | 异常比例 | 异常率 > 30% |
| homework-service | 批改作业 | 慢调用比例 | RT > 2000ms, 比例 > 60% |
| progress-service | 统计计算 | 异常数 | 异常数 > 10 |

---

## 🔍 监控与告警

### 1. 查看实时监控

在 Sentinel 控制台可以查看:
- 实时 QPS
- 响应时间
- 异常数量
- 限流/熔断次数

### 2. 集成 Prometheus

Sentinel 指标已自动暴露到 Prometheus:

```yaml
# Prometheus 配置
scrape_configs:
  - job_name: 'sentinel'
    static_configs:
      - targets: ['gateway:8090', 'user-service:8081']
    metrics_path: '/actuator/prometheus'
```

### 3. Grafana 可视化

导入 Sentinel Dashboard 模板,可视化展示:
- 流量趋势
- 限流统计
- 熔断统计
- 系统负载

---

## ⚠️ 注意事项

1. **规则生效时间**: Nacos 配置更新后,服务会在 3 秒内自动加载新规则
2. **资源名称**: 默认使用 HTTP 方法 + 路径作为资源名 (如 `GET:/api/users`)
3. **降级时长**: 熔断后会进入半开状态,逐步恢复流量
4. **性能影响**: Sentinel 性能损耗极低 (< 1ms),可放心使用
5. **生产环境**: 建议根据实际压测结果调整阈值

---

## 📚 参考资料

- [Sentinel 官方文档](https://sentinelguard.io/zh-cn/docs/introduction.html)
- [Spring Cloud Alibaba Sentinel](https://github.com/alibaba/spring-cloud-alibaba/wiki/Sentinel)
- [Sentinel 控制台](https://github.com/alibaba/Sentinel/wiki/%E6%8E%A7%E5%88%B6%E5%8F%B0)

---

## 🆘 常见问题

### Q1: 服务启动后在 Sentinel 控制台看不到?

**A**: Sentinel 采用懒加载机制,需要至少调用一次接口后才会在控制台显示。

### Q2: 规则配置后不生效?

**A**: 检查以下几点:
1. Nacos 配置的 Data ID 和 Group 是否正确
2. 服务是否成功连接到 Nacos
3. 查看服务日志是否有加载规则的日志

### Q3: 如何测试限流效果?

**A**: 使用压测工具 (如 JMeter, wrk) 快速发送请求:

```bash
# 使用 curl 循环测试
for i in {1..20}; do curl http://localhost:8090/api/auth/login; done
```

### Q4: 降级后如何恢复?

**A**: Sentinel 会自动进入半开状态,逐步放行请求。如果请求成功,则恢复正常;如果继续失败,则再次熔断。

---

**最后更新**: 2026-02-07
**维护者**: Claude
