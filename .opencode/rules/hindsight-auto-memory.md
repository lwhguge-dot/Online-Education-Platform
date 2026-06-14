---
description: "Hindsight 记忆操作规范 — MiMoCode 中插件不自动加载，AI 必须每轮手动执行完整流程。"
alwaysApply: true
---

# 记忆系统操作

> **MiMoCode 重要：** `hindsight-automemory.ts` 插件在 MiMoCode 中不会自动加载。所有操作必须由 AI 手动执行。

## 每轮必须执行

1. **代码修改后** → `hindsight_retain_batch`
2. **用户偏好/纠正** → `hindsight_retain`
3. **会话结束前** → `hindsight_session_end`

## 记忆利用守则

### 你必须遵守的规则

1. **优先级读取**：preference > correction > code-change > snapshot > experience > world
2. **必须手动 retain 的场景**：
   - 代码修改完成 → `hindsight_retain_batch`，每项 `category: "code-change"`
   - 用户明确表达偏好 → `hindsight_retain`，`category: "preference"`
   - 用户纠正你 → `hindsight_retain`，`category: "correction"`
   - 会话结束 → `hindsight_session_end`（生成 snapshot + 触发 reflect）
3. **bank_id 固定为 `deepseek-v2`**：所有手动调用必须显式传 `bank_id="deepseek-v2"`

## 你只需记住一件事

**代码修改 + 会话结束 → 两个调用缺一不可：**

### 代码修改保存（每个任务结束时）

```
hindsight_retain_batch(bank_id="deepseek-v2", items=[
  {"content": "文件: xxx | 变更: yyy", "category": "code-change"},
])
```

### 会话结束快照（最终回复前）

```
hindsight_session_end(bank_id="deepseek-v2")
```

### 用户偏好/纠正（发生时立即保存）

```
hindsight_retain(bank_id="deepseek-v2", content="原话", category="preference")
hindsight_retain(bank_id="deepseek-v2", content="原话", category="correction")
```

---

## 故障排查指南

### API 服务不可用
- **检查 API 服务**：直接执行 `curl http://localhost:8888/health`
- **网络问题**：确认 Hindsight API 服务在 `localhost:8888` 正常启动且未被防火墙拦截
- **启动服务**：运行 `uv run ops/scripts/hindsight/hindsight-mcp-server.py`

### API 服务返回错误
- **端口冲突**：确认 8888 端口未被其他进程占用：`netstat -ano | findstr :8888`
- **环境问题**：确保 Python ≥ 3.10 且已安装 `mcp` 包：`pip install mcp`
- **启动服务**：运行 `uv run ops/scripts/hindsight/hindsight-mcp-server.py`

### 记忆查询无结果
- **检查统计**：使用 `hindsight_bank_stats(bank_id="deepseek-v2")` 查看节点数和链接数
- **等待 consolidation**：新保存的记忆需要 3-7 分钟完成合并（consolidation）才能被检索到
- **优化查询**：recall 查询应包含足够关键词，使用 `budget: "high"` + `max_tokens: 4096` 获得最完整结果
- **检查保存状态**：`hindsight_retain_batch` 返回 "queued for saving" 是正常的，实际写入在后台异步进行

### API 服务断开
- Hindsight API 服务中断后，记忆操作自动降级为静默失败，不影响正常编码流程
- 恢复服务后，新的记忆操作会自动重新开始工作
- 使用 `hindsight_healthcheck` 工具验证服务状态
