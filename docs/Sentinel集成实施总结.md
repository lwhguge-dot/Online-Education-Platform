# Sentinel 集成实施总结

## ✅ 实施完成情况

### 已完成的工作

#### 1. 依赖管理
- ✅ 在父 POM 中添加 Sentinel 版本管理 (1.8.6)
- ✅ 在父 POM 中添加 Sentinel 核心库和 Nacos 数据源依赖
- ✅ Gateway 添加 Sentinel Gateway 适配器依赖
- ✅ 所有微服务添加 Sentinel 依赖

#### 2. Docker 部署
- ✅ 在 docker-compose.yml 中添加 Sentinel Dashboard 容器
- ✅ 配置 Sentinel Dashboard 端口映射 (8858)
- ✅ 更新 Docker 启动脚本,包含 Sentinel 启动

#### 3. 配置文件
- ✅ Gateway 配置 Sentinel 连接和规则数据源
- ✅ User-service 配置 Sentinel 连接和规则数据源
- ✅ Course-service 配置 Sentinel 连接和规则数据源
- ✅ Homework-service 配置 Sentinel 连接和规则数据源
- ✅ Progress-service 配置 Sentinel 连接和规则数据源

#### 4. 代码实现
- ✅ Gateway 创建 SentinelConfig 配置类
- ✅ Gateway 实现自定义 BlockRequestHandler
- ✅ User-service 创建 SentinelConfig 配置类
- ✅ Course-service 创建 SentinelConfig 配置类
- ✅ Homework-service 创建 SentinelConfig 配置类
- ✅ Progress-service 创建 SentinelConfig 配置类
- ✅ 所有服务实现统一的异常响应格式

#### 5. 文档
- ✅ 创建 Sentinel 配置指南 (docs/Sentinel配置指南.md)
- ✅ 更新 README.md,添加 Sentinel 说明
- ✅ 更新技术栈列表

---

## 📋 配置清单

### 1. 依赖版本

| 组件 | 版本 |
|-----|------|
| Sentinel Core | 1.8.6 |
| Sentinel Dashboard | 1.8.6 |
| Spring Cloud Alibaba | 2023.0.3.4 |

### 2. 端口分配

| 服务 | 端口 | 说明 |
|-----|------|------|
| Sentinel Dashboard | 8858 | 控制台 Web UI |
| Gateway | 8719 | 与 Sentinel 通信端口 |
| User-service | 8719 | 与 Sentinel 通信端口 |
| Course-service | 8719 | 与 Sentinel 通信端口 |
| Homework-service | 8719 | 与 Sentinel 通信端口 |
| Progress-service | 8719 | 与 Sentinel 通信端口 |

### 3. 配置项

所有微服务均配置了以下 Sentinel 参数:

```yaml
spring:
  cloud:
    sentinel:
      transport:
        dashboard: sentinel:8858  # Sentinel 控制台地址
        port: 8719                # 与控制台通信的端口
      datasource:
        flow:                     # 流控规则
          nacos:
            server-addr: "${NACOS_ADDR:nacos:8848}"
            dataId: ${spring.application.name}-flow-rules
            groupId: SENTINEL_GROUP
            rule-type: flow
        degrade:                  # 降级规则
          nacos:
            server-addr: "${NACOS_ADDR:nacos:8848}"
            dataId: ${spring.application.name}-degrade-rules
            groupId: SENTINEL_GROUP
            rule-type: degrade
      eager: true                 # 启动时立即初始化
      web-context-unify: false    # 保留完整路径 (微服务)
      filter:
        enabled: true             # 启用过滤器 (Gateway)
```

---

## 🚀 启动验证

### 1. 启动服务

```bash
cd tools/scripts
./Docker启动.bat
```

### 2. 访问 Sentinel 控制台

- URL: http://localhost:8858
- 用户名: sentinel
- 密码: sentinel

### 3. 验证服务注册

启动后,在 Sentinel 控制台左侧菜单应该能看到:
- gateway
- user-service
- course-service
- homework-service
- progress-service

**注意**: Sentinel 采用懒加载,需要至少调用一次接口后才会显示。

### 4. 测试限流效果

```bash
# 快速发送多次请求测试限流
for i in {1..20}; do curl http://localhost:8090/api/auth/login; done
```

预期结果: 部分请求返回 429 状态码和限流提示。

---

## 📊 功能特性

### 1. 流量控制
- ✅ QPS 限流
- ✅ 线程数限流
- ✅ 热点参数限流
- ✅ 关联限流
- ✅ 链路限流

### 2. 熔断降级
- ✅ 慢调用比例熔断
- ✅ 异常比例熔断
- ✅ 异常数熔断
- ✅ 自定义降级响应

### 3. 系统保护
- ✅ CPU 使用率保护
- ✅ 系统负载保护
- ✅ 平均 RT 保护
- ✅ 并发线程数保护
- ✅ 入口 QPS 保护

### 4. 规则持久化
- ✅ Nacos 作为数据源
- ✅ 规则动态更新
- ✅ 服务重启规则不丢失

---

## 🎯 推荐配置

### Gateway 网关层

建议在 Nacos 中创建以下配置:

**Data ID**: `gateway-flow-rules`
**Group**: `SENTINEL_GROUP`

```json
[
  {
    "resource": "user-service",
    "limitApp": "default",
    "grade": 1,
    "count": 100,
    "strategy": 0,
    "controlBehavior": 0
  },
  {
    "resource": "course-service",
    "limitApp": "default",
    "grade": 1,
    "count": 100,
    "strategy": 0,
    "controlBehavior": 0
  }
]
```

### 微服务层

**Data ID**: `user-service-flow-rules`
**Group**: `SENTINEL_GROUP`

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
    "resource": "POST:/api/auth/register",
    "limitApp": "default",
    "grade": 1,
    "count": 5,
    "strategy": 0,
    "controlBehavior": 0
  }
]
```

**Data ID**: `user-service-degrade-rules`
**Group**: `SENTINEL_GROUP`

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

---

## 📝 使用示例

### 1. 在代码中使用 @SentinelResource

```java
@Service
public class UserService {

    @SentinelResource(
        value = "getUserById",
        blockHandler = "handleBlock",
        fallback = "handleFallback"
    )
    public UserVO getUserById(Long userId) {
        return userMapper.selectById(userId);
    }

    public UserVO handleBlock(Long userId, BlockException ex) {
        log.warn("用户查询被限流: userId={}", userId);
        return UserVO.builder()
            .id(userId)
            .username("系统繁忙")
            .build();
    }

    public UserVO handleFallback(Long userId, Throwable throwable) {
        log.error("用户查询异常: userId={}", userId, throwable);
        return UserVO.builder()
            .id(userId)
            .username("服务异常")
            .build();
    }
}
```

### 2. OpenFeign 集成

```java
@FeignClient(
    name = "course-service",
    fallback = CourseServiceClientFallback.class
)
public interface CourseServiceClient {
    @GetMapping("/api/courses/{courseId}")
    Result<CourseVO> getCourseById(@PathVariable Long courseId);
}

@Component
public class CourseServiceClientFallback implements CourseServiceClient {
    @Override
    public Result<CourseVO> getCourseById(Long courseId) {
        return Result.fail("课程服务暂时不可用");
    }
}
```

---

## ⚠️ 注意事项

1. **懒加载机制**: Sentinel 采用懒加载,服务启动后需要至少调用一次接口才会在控制台显示
2. **规则生效时间**: Nacos 配置更新后,服务会在 3 秒内自动加载新规则
3. **资源名称**: 默认使用 HTTP 方法 + 路径作为资源名 (如 `GET:/api/users`)
4. **性能影响**: Sentinel 性能损耗极低 (< 1ms),可放心使用
5. **生产环境**: 建议根据实际压测结果调整阈值

---

## 📚 相关文档

- [Sentinel 配置指南](../docs/Sentinel配置指南.md) - 详细的配置说明和使用示例
- [Sentinel 官方文档](https://sentinelguard.io/zh-cn/docs/introduction.html)
- [Spring Cloud Alibaba Sentinel](https://github.com/alibaba/spring-cloud-alibaba/wiki/Sentinel)

---

## 🔄 后续优化建议

1. **监控集成**: 将 Sentinel 指标集成到 Grafana 仪表板
2. **告警配置**: 配置限流/熔断告警,及时发现问题
3. **压测验证**: 进行压力测试,验证限流阈值是否合理
4. **规则优化**: 根据实际业务场景调整限流和熔断规则
5. **热点参数**: 针对热点数据 (如热门课程) 配置热点参数限流

---

**实施日期**: 2026-02-07
**实施人员**: Claude
**状态**: ✅ 已完成
