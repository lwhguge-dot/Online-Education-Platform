---
name: git-workflow
description: Use when user says "提交", "commit", "push", "git", "分支". Git operations, commit conventions, branch management.
---

# Git 工作流

> 触发条件：用户说"提交"、"commit"、"push"、"git"、"分支"等。

## Commit 规范

### 格式
```
<type>(<scope>): <subject>

<body>

<footer>
```

### Type 类型
| Type | 说明 | 示例 |
|------|------|------|
| feat | 新功能 | feat(user): 添加密码重置功能 |
| fix | 修复 Bug | fix(gateway): 修复 JWT 校验失败 |
| docs | 文档 | docs: 更新 README |
| style | 格式（不影响逻辑） | style(frontend): 格式化代码 |
| refactor | 重构 | refactor(course): 重构课程查询逻辑 |
| test | 测试 | test(homework): 添加作业单元测试 |
| chore | 构建/工具 | chore: 更新依赖 |
| perf | 性能优化 | perf(progress): 优化进度查询 |

### 示例
```bash
git add frontend/src/views/Login.vue
git commit -m "feat(auth): 添加记住密码功能"
```

## 分支管理

### 分支命名
| 分支 | 用途 |
|------|------|
| main | 生产环境 |
| develop | 开发环境 |
| feature/xxx | 功能分支 |
| bugfix/xxx | 修复分支 |
| release/xxx | 发布分支 |

### 工作流
```bash
# 创建功能分支
git checkout -b feature/password-reset

# 开发完成后合并
git checkout develop
git merge feature/password-reset
git branch -d feature/password-reset
```

## 常用操作

### 查看状态
```bash
git status
git diff
git log --oneline -10
```

### 撤销操作
```bash
# 撤销工作区修改
git checkout -- <file>

# 撤销暂存
git reset HEAD <file>

# 撤销 commit（保留修改）
git reset --soft HEAD~1
```

### 暂存工作
```bash
git stash
git stash pop
git stash list
```

## 行为准则

- commit 前先 `git status` 确认改动
- 不要提交 `.env`、`node_modules`、`target/`
- commit 信息用中文，简洁明了
- 使用 `hindsight_retain` 记录重要决策
