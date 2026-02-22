---
name: auth-implementation-patterns
description: "Use when implementing or hardening authentication and authorization flows, including JWT, OAuth2, sessions, and RBAC."
risk: unknown
source: community
---

# Authentication & Authorization Implementation Patterns

Build secure, scalable authentication and authorization systems using industry-standard patterns and modern best practices.

## Use this skill when

- Implementing user authentication systems
- Securing REST or GraphQL APIs
- Adding OAuth2/social login or SSO
- Designing session management or RBAC
- Debugging authentication or authorization issues

## Do not use this skill when

- You only need UI copy or login page styling
- The task is infrastructure-only without identity concerns
- You cannot change auth policies or credential storage

## Instructions

- Define users, tenants, flows, and threat model constraints.
- Choose auth strategy (session, JWT, OIDC) and token lifecycle.
- Design authorization model and policy enforcement points.
- Plan secrets storage, rotation, logging, and audit requirements.
- If detailed examples are required, open `resources/implementation-playbook.md`.

## Safety

- Never log secrets, tokens, or credentials.
- Enforce least privilege and secure storage for keys.

## Resources

- `resources/implementation-playbook.md` for detailed patterns and examples.

## 中文执行层

### 触发条件
- Use when implementing or hardening authentication and authorization flows, including JWT, OAuth2, sessions, and RBAC.

### 前置条件
- 已确认当前任务与本 skill 的适用范围匹配。
- 已读取本文件的关键步骤，并确认命令路径基于仓库真实文件。
- 若依赖外部工具或凭据，先执行最小可用性检查（如 --help 或版本检查）。

### 执行步骤
1. 先按本 skill 的流程章节确认边界和产出物。
2. 先执行最小可验证步骤，再逐步扩展到完整实现。
3. 过程中的关键命令、输入和结果要记录到可复盘证据中。
4. 若与 AGENTS.md 路由冲突，以项目级约定和任务目标为准。

### 完成证据
- 提供关键命令与输出摘要，必要时附日志或报告文件路径。
- 列出受影响文件和核心改动点，确保与需求一一对应。
- 明确说明验证是否通过，以及尚未覆盖的风险。

### 失败回退
- 失败时先保留现场与报错信息，再定位根因并重试。
- 如需降级方案，必须说明影响范围、回退路径和补偿措施。

