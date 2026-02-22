# Jaeger 安装与基础校验

本文档提供一个最小可用的 Jaeger 落地流程，用于配合 `distributed-tracing` skill 的基础环境准备。

## 1. Docker Compose 快速启动

```yaml
version: "3.8"
services:
  jaeger:
    image: jaegertracing/all-in-one:1.57
    container_name: jaeger
    ports:
      - "16686:16686" # Web UI
      - "14268:14268" # HTTP collector
      - "14250:14250" # gRPC collector
      - "6831:6831/udp" # Jaeger agent
    environment:
      - COLLECTOR_OTLP_ENABLED=true
```

启动：

```bash
docker compose up -d
```

## 2. Kubernetes 最小部署思路

1. 创建可观测命名空间，例如 `observability`。  
2. 部署 Jaeger Operator 或直接部署 all-in-one（测试环境）。  
3. 暴露 `16686` 端口用于 UI 访问。  
4. 为应用配置 OTLP/Jaeger exporter 的目标地址。

## 3. 连通性检查

1. 打开 `http://localhost:16686`，确认 UI 可访问。  
2. 应用发送一条测试请求后，进入 `Search` 页面按 `service` 过滤。  
3. 能看到 trace/span 即表示链路打通。

## 4. 常见问题

1. UI 无数据：先检查应用采样率和 exporter 地址。  
2. 端口冲突：更换本地端口映射。  
3. 容器正常但无 trace：检查应用是否真正注入了 OpenTelemetry SDK。
