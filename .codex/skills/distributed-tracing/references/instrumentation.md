# Instrumentation 模式参考

本文档用于补充 `distributed-tracing` skill 中的埋点实践，重点覆盖“统一命名、上下文传播、错误标注”三件事。

## 1. Span 命名建议

1. 使用稳定、可聚合的操作名，如 `http.request`、`db.query`。  
2. 业务动作可放到属性里，不建议把动态参数写进 span 名称。  
3. 服务名建议统一使用 `service.name` 资源属性。

## 2. 关键属性（Attributes）

建议至少补齐：

1. `service.name`  
2. `http.method`、`http.route`、`http.status_code`  
3. `db.system`、`db.operation`  
4. `error.type`、`error.message`（发生异常时）

## 3. 上下文传播

1. HTTP 使用 `traceparent` / `tracestate`。  
2. gRPC 使用 metadata 透传。  
3. 异步消息（Kafka/RabbitMQ）需要在消息头写入并读取 trace context。

## 4. 错误处理

1. 捕获异常时调用 `span.recordException(...)`。  
2. 明确设置 span 状态为 error。  
3. 错误信息写入事件或属性，避免只在日志中出现、链路中丢失。

## 5. 采样策略

1. 生产默认建议从 `1%` 到 `10%` 起步。  
2. 高价值接口可提升采样率或使用规则采样。  
3. 出现故障窗口期可临时提高采样率，故障恢复后回调。

## 6. 验收清单

1. 一个完整请求能串起网关 -> 服务 -> 数据库。  
2. Trace 中可定位慢 span（P95/P99 场景）。  
3. 错误请求在 UI 中可直接看到异常信息与失败节点。  
4. 采样与导出配置不会造成明显性能回退。
