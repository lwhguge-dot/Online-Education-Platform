---
name: frontend-check
description: Use when user says "前端检查", "类型检查", "lint", "前端测试". Run type-check, lint, and test for frontend code.
---

# 前端代码检查

> 触发条件：用户说"前端检查"、"类型检查"、"lint"、"前端测试"、"check frontend"等。

## 检查流程（按顺序执行）

### 1. 类型检查
```bash
npm run type-check 2>&1 | Select-String "error TS" | Measure-Object | Select-Object -ExpandProperty Count
```
- 返回 0 = 无类型错误
- 返回数字 = 有 N 个类型错误

### 2. Lint 检查
```bash
npm run lint 2>&1 | Select-Object -Last 5
```
- 检查代码风格问题

### 3. 单元测试
```bash
npm run test 2>&1
```
- 运行 vitest 测试

### 4. 构建验证
```bash
npm run build 2>&1 | Select-Object -Last 10
```
- 确认生产构建通过

## 常见问题

| 问题 | 修复 |
|------|------|
| `error TS` 类型错误 | 修复 TypeScript 类型 |
| eslint 错误 | 运行 `npm run lint -- --fix` 自动修复 |
| 测试失败 | 检查测试用例 |
| 构建失败 | 检查依赖和配置 |

## 行为准则

- 检查前先确认 `frontend/` 目录存在
- 类型错误必须修复才能提交
- 使用 `hindsight_retain` 记录重要修复
