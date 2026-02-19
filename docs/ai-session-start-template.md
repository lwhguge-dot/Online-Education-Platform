# AI 会话开场模板（项目本地 Skills）

将下面模板复制到新会话开头，可提高技能触发稳定性与执行一致性。

```text
你必须只使用当前项目本地 .codex/skills 中已安装的 skill，不得改动全局 ~/.codex/skills。

请按以下流程执行：
1) 如果任务复杂，先 Use planning-with-files 生成可执行计划。
2) 如果方案不清晰，先 Use brainstorming 做方案收敛。
3) 进入实现后，按任务类型调用匹配 skill（示例：前端用 ui-ux-pro-max；后端安全用 api-security-best-practices + backend-security-coder）。
4) 实现后必须执行 verification-before-completion，并给出验证结果。
5) 最后用 receiving-code-review 做一次审查，明确风险与后续建议。

输出要求：
- 所有文件修改必须基于仓库真实内容，不允许臆测。
- 代码注释使用简体中文。
- 保持 UTF-8 编码。
- 不触碰与当前任务无关的现有改动。
```

## 常用一键触发短语
- `Use planning-with-files to break this task into phases.`
- `Use ui-ux-pro-max to redesign this page with implementation-ready changes.`
- `Use api-security-best-practices to review backend auth endpoints.`
- `Use postgres-best-practices to optimize this SQL path.`
- `Use observability-engineer to propose metrics/logs/traces for this service.`

