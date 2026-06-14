---
name: hindsight-maintenance
description: Use when user says "维护记忆", "清理记忆", "压缩记忆", "记忆维护". Run Hindsight memory maintenance (cleanup + compress).
---

# Hindsight 记忆维护

> 触发条件：用户说"维护记忆"、"清理记忆"、"压缩记忆"、"记忆维护"等。

## 执行流程

### 1. 健康检查
```bash
curl -s http://localhost:8888/health
```
- 确认 API 正常

### 2. 查看当前状态
```
hindsight_bank_stats(bank_id="deepseek-v2")
```
- 记录 nodes、links、quality score

### 3. 清理低质量记忆
```bash
curl -X POST http://localhost:8888/v1/default/banks/deepseek-v2/memories/cleanup \
  -H "Content-Type: application/json" \
  -d '{"threshold": 0.3}'
```

### 4. 压缩重复记忆
```bash
curl -X POST http://localhost:8888/v1/default/banks/deepseek-v2/memories/compress \
  -H "Content-Type: application/json"
```

### 5. 查看维护后状态
```
hindsight_bank_stats(bank_id="deepseek-v2")
```

## 一键维护脚本

```powershell
# 启动服务 + 维护
.\ops\scripts\hindsight\start-services.ps1

# 仅维护
.\ops\scripts\hindsight\hindsight-maintenance.ps1
```

## 行为准则

- 维护前先检查 API 健康状态
- 维护后对比 stats 确认效果
- 使用 `hindsight_retain` 记录维护结果
