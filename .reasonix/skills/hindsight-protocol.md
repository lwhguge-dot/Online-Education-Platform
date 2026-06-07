---
name: hindsight-protocol
description: Hindsight 记忆系统协议。自定义插件自动处理每轮 recall/retain，AI 负责 code-change 保存和会话快照。
---

# Hindsight Memory Protocol

> 在 OpenCode 中由 `hindsight-automemory.ts` 插件自动处理。Reasonix 中通过 MCP 工具手动调用：
> - 每轮 recall（以用户消息为查询注入历史记忆到 system prompt）
> - 每轮 retain（fire-and-forget 保存当前对话轮，category=conversation）
>
> 你仍可手动调用 MCP 工具补充操作。

## 自动注入的格式契约

插件在每条用户消息最前注入：

```
<hindsight_memories priority="preference>correction>code-change>snapshot>experience>world">
- [preference|2026-05-30] 用户偏好：xxx
- [code-change|2026-05-29] 文件: src/foo.ts | 变更: xxx
</hindsight_memories>
```

**读取优先级**：`preference > correction > code-change > snapshot > experience > world`

**禁止重复保存**：每轮对话已被自动 retain（category=conversation），**不要**再手动保存同一内容。

## 你必须手动调用的场景

| 场景 | 调用 | category |
|------|------|----------|
| 代码修改完成 | `hindsight_retain_batch` | `code-change` |
| 用户明确表达偏好 | `hindsight_retain` | `preference` |
| 用户纠正你 | `hindsight_retain` | `correction` |
| 会话结束（最终回复前） | `hindsight_session_end` | 内部 snapshot+reflect |
| auto-recall 不够 | `hindsight_recall`（指定 budget=high） | — |
| 查看记忆库状态 | `hindsight_bank_stats` | — |

**所有调用 bank_id 固定为 `deepseek-v2`**

## 你只需做两件事

### 1. 代码修改 → batch 保存

```
hindsight_retain_batch(bank_id="deepseek-v2", items=[
  {"content": "文件: [路径] | 变更: [内容]", "category": "code-change"},
])
```

### 2. 会话结束 → 快照

```
hindsight_session_end(bank_id="deepseek-v2")
```

## 你可以做（补充）

- 当 auto-recall 结果不充分时，手动 `hindsight_recall` 查更多历史
- 当 auto-retain 不完整时，手动 `hindsight_retain` 保存特定内容
- 使用 `hindsight_bank_stats` 查看记忆库统计

## 不要做

- ❌ 声称完成但没执行步骤 1 和 2

## code-change 保存格式模板

每次代码修改后，使用以下模板保存：

```
hindsight_retain_batch(bank_id="deepseek-v2", items=[
  # 每项一个完整变更记录
  {"content": "文件: src/component/foo.ts | 变更: 添加了 Xxx 方法实现 Y 功能 | 原因: 用户需求 Z", "category": "code-change"},
  {"content": "文件: src/component/foo.test.ts | 变更: 新增 Y 功能测试用例 | 原因: 对应代码变更", "category": "code-change"},
  # 用户偏好
  {"content": "用户偏好: 使用了 A 模式而非 B 模式 | 上下文: foo.ts 实现", "category": "preference"},
  # 决策记录
  {"content": "决策: 选择 X 方案代替 Y 方案 | 原因: 性能更好/更符合项目架构", "category": "preference"},
])
```

每个 code-change item 的 content 格式：
`文件: {路径} | 变更: {操作+内容简述} | 原因: {原因}（此项可选）`

## 项目规则索引

- 前端/Vue → `vue3-frontend-standards.md`（项目级规则）
- SQL/MyBatis → `postgres-best-practices.md` + `mybatis-plus-practices.md`
- 后端 API 安全 → `api-security-best-practices.md`
- Docker → `docker-expert.md`
- 部署 → `deployment-procedures.md`
- 测试 → `testing-standards.md`
- 故障排查 → `hindsight-auto-memory.md`（Hindsight 故障排查指南）
- 状态管理 → `pinia-state-management.md`
- 样式 → `tailwind-styling-standards.md`

## 行为准则

- 回复使用中文简体，UTF-8 编码
- 修改文件后运行 lint/typecheck 验证
- 使用 `hindsight_healthcheck` 工具验证记忆系统正常工作
- 不修改全局 `~/.config/opencode/` 配置
