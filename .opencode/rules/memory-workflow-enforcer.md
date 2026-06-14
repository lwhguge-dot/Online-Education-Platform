---
description: "强制执行 Hindsight 记忆系统完整流程。每轮必须检查并执行，无例外。"
alwaysApply: true
---

# 记忆流程强制执行器

## 检查清单（每轮回复前必须执行）

### 1. 代码修改检查
如果本轮修改了任何文件：
```
hindsight_retain_batch(bank_id="deepseek-v2", items=[
  {"content": "文件: [路径] | 变更: [内容]", "category": "code-change"},
])
```

### 2. 用户偏好/纠正检查
如果用户表达了偏好或纠正了你：
```
hindsight_retain(bank_id="deepseek-v2", content="原话", category="preference|correction")
```

### 3. 会话结束检查
如果这是会话的最后一轮回复（或用户说"结束"、"完成"、"就这样"）：
```
hindsight_session_end(bank_id="deepseek-v2")
```

## 你必须做的事

1. **每轮回复前**：检查是否有代码修改 → 有则 retain_batch
2. **用户说话时**：检查是否有偏好/纠正 → 有则 retain
3. **会话结束时**：调用 session_end

## 你绝对不能做的事

- ❌ 声称完成但没调用 retain
- ❌ 忘记检查代码修改
- ❌ 会话结束不调用 session_end

## 违反后果

用户已多次纠正此问题。每次违反都是一次信任损失。
