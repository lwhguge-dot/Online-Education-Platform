# AI 会话开场模板（项目本地 Skills）

将下面模板复制到新会话开头，可提高技能触发稳定性与执行一致性。

```text
你必须只使用当前项目本地 .codex/skills 中已安装的 skill，不得改动全局 ~/.codex/skills。

请按以下流程执行：
1) 每轮先 Use using-superpowers 做技能判定守卫，并先输出本轮按顺序选中的 skill 列表。
2) 如果任务复杂，先 Use planning-with-files；若已有明确规格，补 Use writing-plans。
3) 如果方案不清晰，先 Use brainstorming 做方案收敛。
4) 按计划实现时，Use executing-plans；多子任务并行时可 Use subagent-driven-development。
5) 按领域挂载 skill：
   - 前端 UI/UX：Use ui-ux-pro-max
   - API 安全：Use api-security-best-practices + backend-security-coder
   - SQL / PostgreSQL：Use postgres-best-practices
   - 可观测性与链路：Use observability-engineer + distributed-tracing
   - Docker / 发布：Use docker-expert + deployment-procedures
6) 若是 bug 修复，必须先 Use systematic-debugging，再 Use test-driven-development。
7) 任一“完成/通过”结论前，必须执行 verification-before-completion 并展示命令与结果证据。
8) 结束前 Use receiving-code-review 做审查；需要分支收尾时再 Use finishing-a-development-branch。

输出要求：
- 所有文件修改必须基于仓库真实内容，不允许臆测。
- 代码注释使用简体中文。
- 保持 UTF-8 编码。
- 不触碰与当前任务无关的现有改动。
```

## 常用一键触发短语
- `Use planning-with-files to break this task into phases.`
- `Use writing-plans to produce an implementation plan from this spec.`
- `Use executing-plans to implement this approved plan with checkpoints.`
- `Use ui-ux-pro-max to redesign this page with implementation-ready changes.`
- `Use api-security-best-practices to review backend auth endpoints.`
- `Use postgres-best-practices to optimize this SQL path.`
- `Use observability-engineer to propose metrics/logs/traces for this service.`
