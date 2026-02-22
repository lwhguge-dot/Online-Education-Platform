# 前端全量代码审查报告（最严格版）

## 1. 审查摘要
- 审查日期：`2026-02-19`
- 审查范围：`frontend/src/**` 与前端工程配置文件
- 优先目标：稳定性与可维护性
- 结论：
  - `Critical`: 1 项
  - `Important`: 12 项
  - `Minor`: 4 项
- 基线验证：`npm run type-check` 与 `npm run build` 均通过（详见 `findings.md`）

## 2. 覆盖与证据
- 覆盖目录：
  - `frontend/src/views/**`
  - `frontend/src/components/**`
  - `frontend/src/services/**`
  - `frontend/src/composables/**`
  - `frontend/src/stores/**`
  - `frontend/src/assets/**`
  - `frontend/src/router/**`
  - `frontend/src/types/**`
  - `frontend/src/utils/**`
- 工程配置：
  - `frontend/package.json`
  - `frontend/tsconfig.json`
  - `frontend/vite.config.ts`
  - `frontend/tailwind.config.js`
  - `frontend/postcss.config.js`
  - `frontend/src/env.d.ts`

---

## 3. Findings

### Critical

#### C-01 登录后 WebSocket 不会自动建立，实时通知与强制下线链路可能失效
- 证据：
  - `frontend/src/App.vue:46`
  - `frontend/src/App.vue:47`
  - `frontend/src/App.vue:25`
  - `frontend/src/stores/auth.ts:30`
  - `frontend/src/stores/auth.ts:33`
- 问题说明：
  - WebSocket 连接仅在 `App` 挂载时调用一次 `setupWebSocket`，登录后仅更新 `authStore`，没有任何 watch/订阅机制触发补连。
- 影响：
  - 新登录会话在不刷新页面的情况下可能收不到通知，也无法及时响应强制下线事件，属于核心会话链路风险。
- 修复建议：
  - 在 `App.vue` 监听 `authStore.user` / `authStore.token` 变化，登录后自动连接，登出后主动断开并清理监听器。
  - 避免重复注册 listener，先反注册再重注册。
- 回归验证：
  1. 冷启动进入登录页，登录后不刷新页面。
  2. 触发服务端通知事件，确认前端收到并展示。
  3. 触发强制下线，确认前端立即退回登录页。

### Important

#### I-01 路由守卫与认证初始化顺序存在竞态窗口
- 证据：
  - `frontend/src/main.ts:58`
  - `frontend/src/main.ts:59`
  - `frontend/src/main.ts:62`
  - `frontend/src/main.ts:63`
  - `frontend/src/router/index.ts:121`
  - `frontend/src/router/index.ts:125`
- 问题说明：
  - 当前先 `app.use(router)`，再 `authStore.init()`；守卫依赖 `isAuthenticated`，首轮导航存在读取未初始化状态的可能。
- 影响：
  - 已登录用户冷启动访问受保护路由时可能出现错误跳转或闪烁。
- 修复建议：
  - 在挂载前完成 `authStore.init()`，或在守卫中显式等待 `authStore.loading` 完成。
- 回归验证：
  - 带有效会话直接访问 `/teacher`、`/admin`、`/student`，不应先跳转 `/login`。

#### I-02 请求包装器对“重复提交”返回 429 结果而非抛错，调用侧语义不一致
- 证据：
  - `frontend/src/services/request.ts:225`
  - `frontend/src/services/request.ts:228`
  - `frontend/src/views/admin/AdminCourses.vue:229`
  - `frontend/src/views/admin/AdminCourses.vue:234`
- 问题说明：
  - 重复提交时 `request` 直接返回 `{ code: 429 }`，不抛异常；多数调用侧默认 `await` 成功即显示成功提示。
- 影响：
  - 可能出现“实际未执行却提示成功”的误导反馈。
- 修复建议：
  - 统一约定：非 200 一律抛错，或所有调用侧都强制检查 `res.code`。
- 回归验证：
  - 对同一非 GET 操作连续触发两次，UI 不应出现错误成功提示。

#### I-03 多处 `fetch` 直调绕过统一请求层，错误处理/鉴权/埋点不一致
- 证据：
  - `frontend/src/services/modules/course.ts:68`
  - `frontend/src/services/modules/enrollment.ts:72`
  - `frontend/src/services/modules/user.ts:19`
  - `frontend/src/services/modules/user.ts:78`
  - `frontend/src/services/request.ts:293`
- 问题说明：
  - 导出、用户状态更新、删除等接口未复用 `request` 统一行为。
- 影响：
  - toast、Sentry、401/403 会话处理不一致，维护成本高且易漏边界条件。
- 修复建议：
  - 抽出 `requestBlob`/`requestRaw` 扩展，统一走同一请求管线。
- 回归验证：
  - 模拟 401/500 与网络异常，所有接口表现一致（提示、跳转、埋点）。

#### I-04 对 GET 请求强制注入 `Content-Type: application/json`
- 证据：
  - `frontend/src/services/request.ts:243`
  - `frontend/src/services/request.ts:244`
- 问题说明：
  - 当前逻辑在无 body 的请求也会设置 JSON Content-Type。
- 影响：
  - 跨域场景可能引入不必要预检，增加时延与复杂度。
- 修复建议：
  - 仅对有 body 的 JSON 请求设置 `Content-Type`。
- 回归验证：
  - 抓包验证 GET 请求头与预检行为符合预期。

#### I-05 WebSocket 将 token 放入 URL Query，存在泄漏风险
- 证据：
  - `frontend/src/services/websocket.js:53`
  - `frontend/src/services/websocket.js:59`
- 问题说明：
  - token 同时出现在 query 与 AUTH 消息体。
- 影响：
  - URL 级 token 更易暴露在网关日志、监控链路与浏览器调试记录中。
- 修复建议：
  - 改为仅在连接后通过消息体发送，或使用短时 ticket。
- 回归验证：
  - 检查网络日志中不再出现明文 token query。

#### I-06 “清除缓存”直接清空全部 `localStorage/sessionStorage`
- 证据：
  - `frontend/src/views/admin/AdminSystem.vue:105`
  - `frontend/src/views/admin/AdminSystem.vue:106`
- 问题说明：
  - 当前实现是全量清空而非按应用命名空间清理。
- 影响：
  - 同域其他模块或第三方缓存可能被误删。
- 修复建议：
  - 采用 key 前缀策略，仅删除本应用 key。
- 回归验证：
  - 执行“清除缓存”后，非本应用 key 应保持不变。

#### I-07 Confirm Store 仅维护单个 Promise 解析器，存在并发覆盖风险
- 证据：
  - `frontend/src/stores/confirm.ts:24`
  - `frontend/src/stores/confirm.ts:35`
  - `frontend/src/stores/confirm.ts:42`
  - `frontend/src/stores/confirm.ts:47`
- 问题说明：
  - 第二次 `show()` 会覆盖第一次 `resolvePromise`。
- 影响：
  - 先弹出的确认框可能永远不返回，导致业务流程悬挂。
- 修复建议：
  - 实现对话队列（FIFO），或在并发调用时显式拒绝新请求。
- 回归验证：
  - 连续触发两个 confirm，确保两个调用都能按顺序返回。

#### I-08 类型系统约束被大幅放宽，静态防线失效
- 证据：
  - `frontend/tsconfig.json:19`
  - `frontend/tsconfig.json:20`
  - `frontend/tsconfig.json:21`
  - `frontend/src/types/api.ts:11`
  - `frontend/src/types/api.ts:110`
- 问题说明：
  - `strict/noImplicitAny/strictNullChecks` 全关闭，且核心类型中大量 `any`。
- 影响：
  - 运行时错误更难在开发阶段暴露，重构风险升高。
- 修复建议：
  - 分阶段收紧：先开 `strictNullChecks`，再逐步消除高频 `any`。
- 回归验证：
  - 开启后无新增编译错误，关键模块类型告警显著下降。

#### I-09 API 签名与调用不一致，隐藏真实接口契约问题
- 证据：
  - `frontend/src/services/modules/course.ts:9`
  - `frontend/src/views/Home.vue:151`
  - `frontend/src/services/modules/user.ts:12`
  - `frontend/src/views/admin/AdminUsers.vue:284`
- 问题说明：
  - `getPublished(subject: string)` 被无参调用；`updateStatus` 定义要求操作人参数，但批量调用时未传。
- 影响：
  - 类型层面无法准确表达接口约束，审计字段可能丢失。
- 修复建议：
  - 对签名做真实化建模（可选参数明确声明），移除未使用参数或补齐调用参数。
- 回归验证：
  - 全部调用与函数签名一致，关键操作日志字段完整。

#### I-10 登录用户来源分裂：组件直接读 `sessionStorage`，绕过 `authStore`
- 证据：
  - `frontend/src/views/teacher/TeacherCourses.vue:155`
  - `frontend/src/views/admin/AdminUsers.vue:208`
  - `frontend/src/stores/auth.ts:9`
- 问题说明：
  - 业务组件自行解析存储数据，而非统一通过 store 获取。
- 影响：
  - 数据一致性难保证，后续迁移存储策略成本高。
- 修复建议：
  - 统一通过 `authStore` 暴露当前用户；组件层禁止直接解析会话存储。
- 回归验证：
  - 删除组件内存储解析逻辑后功能不回退。

#### I-11 AdminUsers 在线状态拉取频率偏高，存在请求放大
- 证据：
  - `frontend/src/views/admin/AdminUsers.vue:90`
  - `frontend/src/views/admin/AdminUsers.vue:299`
  - `frontend/src/views/admin/AdminUsers.vue:309`
- 问题说明：
  - 既有 30s 轮询，又对 `props.users` 深度变化触发拉取。
- 影响：
  - 用户列表频繁变更时会产生额外网络开销。
- 修复建议：
  - 去掉深度 watch 或加节流/去抖，仅保留单一刷新策略。
- 回归验证：
  - 监控在线状态接口 QPS，确认明显下降。

#### I-12 缺少 lint/test 质量门禁，回归风险依赖人工
- 证据：
  - `frontend/package.json:6`
  - `frontend/package.json:10`
  - `frontend/src/__tests__`（当前文件数为 0）
- 问题说明：
  - 仅有 type-check/build，无 lint 与自动化测试脚本。
- 影响：
  - 风险集中在人工审查，回归缺陷难提前发现。
- 修复建议：
  - 增加 `lint` 与最小单元测试入口，并接入 CI 必过门禁。
- 回归验证：
  - 新增脚本可在本地和 CI 稳定执行。

#### I-13 单文件过大与样式过重，维护复杂度高
- 证据：
  - `frontend/src/views/StudyView.vue:1`（707 行）
  - `frontend/src/views/teacher/TeacherCourses.vue:1`（693 行）
  - `frontend/src/views/student/StudentProfile.vue:1`（631 行）
  - `frontend/src/assets/animations.css:1`（1090 行）
- 问题说明：
  - 页面/样式高度集中，职责耦合显著。
- 影响：
  - 修改回归面大、评审成本高、冲突概率上升。
- 修复建议：
  - 按“容器组件 + 领域子组件 + hooks + 样式分层”拆分。
- 回归验证：
  - 单文件行数与圈复杂度下降，模块边界清晰。

### Minor

#### M-01 存在遗留无效接口：`setSkipFirstCheck` 为 no-op 但仍在调用
- 证据：
  - `frontend/src/services/request.ts:416`
  - `frontend/src/views/Login.vue:88`
  - `frontend/src/views/Login.vue:132`
- 影响：
  - 增加理解噪音，误导后续维护者。
- 建议：
  - 删除遗留 API 与调用点，或补齐真实语义。

#### M-02 可访问性细节：部分图标按钮缺少可访问名称
- 证据：
  - `frontend/src/components/ui/ConfirmDialog.vue:45`
  - `frontend/src/views/teacher/TeacherCourses.vue:525`
  - `frontend/src/components/teacher/StudentDetailModal.vue:118`
- 影响：
  - 读屏器与键盘用户体验下降。
- 建议：
  - 为 icon-only 按钮补充 `aria-label`。

#### M-03 `useWebSocket` 组合式函数未被业务使用，存在并行实现
- 证据：
  - `frontend/src/composables/useWebSocket.ts:26`
  - `frontend/src/App.vue:31`（当前实际使用的是 `services/websocket.js`）
- 影响：
  - 重复能力增加维护噪音。
- 建议：
  - 二选一：统一接入 composable 或删除未使用实现。

#### M-04 样式层存在较多 `!important` 与全局覆盖
- 证据：
  - `frontend/src/assets/main.css:138`
  - `frontend/src/assets/main.css:399`
  - `frontend/src/assets/main.css:426`
- 影响：
  - 样式覆盖链路变复杂，组件复用时更易冲突。
- 建议：
  - 优先通过设计令牌与层级控制替代 `!important`。

---

## 4. 关键系统性问题（跨域）
1. 会话链路分裂：路由守卫、store、WebSocket 生命周期未形成统一时序。
2. 请求链路分裂：统一 request 与直调 fetch 并存，行为标准不一致。
3. 类型与门禁偏弱：严格模式关闭 + 缺少 lint/test，风险主要靠人工兜底。
4. 文件粒度偏大：核心页面和动画样式集中度高，长期维护成本上升。

## 5. 风险评估结论
- 当前前端可构建、可运行，但不建议直接进入“高频功能迭代”阶段。
- 建议先执行 `P0/P1` 整改（见 `docs/reviews/2026-02-19-frontend-remediation-plan.md`），再开展大规模新增需求。
