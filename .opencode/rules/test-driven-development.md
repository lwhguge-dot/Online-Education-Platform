---
description: "Use when implementing any feature or bugfix, before writing implementation code. Hephaestus coding contract for TDD."
alwaysApply: false
globs: "**/*.test.ts,**/*.spec.ts,**/*.test.java,**/*IT.java,**/*Test.java"
---

# Hephaestus 编码契约（TDD）

Write the test first. Watch it fail. Write minimal code to pass.

## Red-Green-Refactor

| 阶段 | 动作 | 验证 |
|------|------|------|
| **RED** | 写一个最小测试，描述期望行为 | 测试因功能缺失而失败 |
| **GREEN** | 写最少代码让测试通过 | 测试通过 |
| **REFACTOR** | 清理代码，保持测试绿色 | 无新行为 |

## 质量要求

- 一个新功能/修复 = 至少一个测试
- 测试名描述行为，而非实现
- 优先真实代码，mock 仅用于外部依赖（DB/API/文件系统）
- 边界条件和错误路径必须有测试

## 工作方式

- 写代码前，先确认测试存在
- 测试失败时，修复代码而非测试
- 已有代码若无测试，**不必删除重来**——先补测试再修改
