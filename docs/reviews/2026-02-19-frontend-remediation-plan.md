# 前端整改迭代计划（基于 2026-02-19 全量审查）

## 1. 目标与策略
- 目标：按风险优先级分三批（P0/P1/P2）完成稳定性与可维护性整改。
- 策略：
  1. 先修会话/鉴权/请求链路（防止线上行为不一致）。
  2. 再补质量门禁与类型约束（降低回归风险）。
  3. 最后做结构化拆分与体验完善（降低长期维护成本）。

## 1.1 执行进展（截至 2026-02-20）
- 已完成：
  - `P0-01`：`App.vue + websocket.js` 已完成登录态联动、主动断连不重连、握手改为 `AUTH` 消息认证（URL 不含 `token`）；新增 `websocket` 单测固化回归。
  - `P0-02`：`main.ts` 已确保 `authStore.init()` 在 `app.use(router)` 之前执行，首屏鉴权竞态已收敛。
  - `P0-03`：`course/enrollment/user` 导出链路已统一走 `requestBlob`，关键路径不再直调 `fetch`，请求行为收敛到统一请求层。
  - `P0-04`：`AdminSystem` 清理缓存已改为命名空间清理（精确 key + 前缀），不再影响同域其他系统存储。
  - `P1-04`：`AdminUsers` 批量状态更新由串行改为并发限流，增加失败汇总与防重入控制。
  - `P1-02`（阶段一）：开启 `noImplicitAny` 并完成首批显式类型补齐（composables 与 UI 头部组件）。
  - `P1-02`（阶段二）：开启 `noImplicitReturns`、`noImplicitThis`、`useUnknownInCatchVariables`，并通过全量验证。
  - `P1-02`（阶段三）：开启 `exactOptionalPropertyTypes`、`noUncheckedIndexedAccess`，修复 `useStudent* / utils` 相关类型边界。
  - `P1-02`（阶段四 / 最终收口）：开启 `strict`、`noUnusedLocals`、`noUnusedParameters`，并清理路由守卫未使用参数。
  - `P2-01`（阶段一）：`StudyView` 完成“目录侧栏 + 章节信息卡片”组件化拆分，页面职责收敛到编排层。
  - `P2-01`（阶段二）：`TeacherCourses` 完成“课程目录区块”组件化拆分，页面收敛为状态与弹窗编排层。
  - `P2-01`（阶段三）：`StudentProfile` 完成“资料主卡片 + 设置侧栏”组件化拆分，页面收敛为编排与事件透传层。
  - `P2-01`（阶段四）：`animations.css` 完成分层拆分（入口聚合 + 4 个子文件），超长样式文件完成职责拆分。
  - `P2-03`（部分）：清理未使用的 `useWebSocket`，统一保留 `services/websocket.js` 作为当前实时链路实现。
  - `P2-03`（阶段二）：`main.css` 表单覆盖规则收敛，`!important` 从 `25` 收敛到 `4`（仅保留 reduced-motion 必需项）。
  - `P2-03`（阶段三）：`main.css` 全局表单/按钮样式改为 `:where(...)` 低优先级基线，直接全局选择器从 `42` 收敛到 `0`。
  - `P2-03`（阶段三可视化复核）：完成亮色/暗色全页截图与控件计算样式抽样，确认 `:where(...)` 改造无视觉回退。
  - `P2-02`（部分）：补充关键 icon-only 按钮 `aria-label`（`ConfirmDialog`、`TeacherCourses`、`StudentDetailModal`、`AdminUsers`）。
  - `P2-02`（阶段二）：补齐键盘可访问路径（`Esc` 关闭、`Tab` 焦点环、`Enter/Space` 激活）并统一弹窗焦点恢复行为。
  - `P2-02`（阶段三）：补齐读屏语义（图表摘要与语义分组）并强化 `BaseSelect` 键盘路径（含 `Home/End/Tab` 与活动项追踪）。
  - `P2-02`（阶段四 / 首批终检）：完成 3 个页面的 `div@click` 语义化替换与键盘可达补齐（`TeacherCenter`、`AdminDashboard`、`TeachingCalendar`）。
  - `P2-02`（阶段五）：对 6 个自定义弹窗容器统一补齐 `role="dialog"`、`aria-modal`、`@keydown.esc`，收敛遮罩弹窗键盘关闭路径。
  - `P2-02`（阶段六 / 手工回归收口）：完成真实页面键盘与读屏回归，修复 `StudentQuestions` 与 `TeachingCalendar` 的“弹窗打开后焦点未入弹窗导致 Esc 首次无效”问题。
  - `P2-03`（阶段三验收）：完成亮色/暗色视觉回归抽样并归档截图证据（`docs/reviews/evidence/frontend-maincss-light.png`、`docs/reviews/evidence/frontend-maincss-dark.png`）。
  - 质量收敛：`lint` 告警由 `112` 持续收敛至 `0`（`0` errors，`0` warnings）。
  - 验证链路：`npm run lint`、`npm run test`、`npm run type-check`、`npm run build` 全部通过（最近一次复验时间：`2026-02-20`，`11` tests passed，`build in 10.82s`）。
- 当前状态：
  - `P0/P1/P2` 已按计划闭环，进入持续回归与增量治理阶段。

## 2. 依赖关系总览
1. `P0-01`（WebSocket 生命周期）与 `P0-02`（路由鉴权初始化顺序）优先执行。
2. `P0-03`（统一请求层）完成后再推进 `P1-02`（类型收紧），避免重复改接口类型。
3. `P1-01`（lint/test 门禁）应在 `P1` 早期落地，为后续改造提供回归保护。
4. `P2` 的页面拆分需建立在 `P0/P1` 稳定后执行。

---

## 3. P0（立即整改，阻断高风险）

### P0-01 会话实时链路修复（WebSocket 与登录态联动）
- 关联问题：`C-01`、`I-05`
- 变更要点：
  - 在 `App.vue` 增加对登录态变化的监听，登录后自动连接，登出后清理连接与回调。
  - 避免 token 出现在 URL query；改为连接后消息认证或短时 ticket。
- 影响面：
  - `frontend/src/App.vue`
  - `frontend/src/services/websocket.js`
  - 可能涉及后端握手策略（若当前强依赖 query token）
- 回归验证：
  - 场景：登录后不刷新页面，能实时收到通知。
  - 场景：强制下线事件触发后立即跳转登录。
  - 场景：浏览器网络面板不出现 `?token=`。

### P0-02 鉴权初始化顺序修复（首屏路由竞态）
- 关联问题：`I-01`
- 变更要点：
  - 调整 `main.ts` 时序：先恢复认证状态再启用路由守卫，或在守卫中等待 `authStore.loading`。
- 影响面：
  - `frontend/src/main.ts`
  - `frontend/src/router/index.ts`
  - `frontend/src/stores/auth.ts`
- 回归验证：
  - 带有效会话直接访问 `/teacher`、`/admin`、`/student` 无错误跳转。

### P0-03 请求行为统一（移除关键路径 fetch 直调）
- 关联问题：`I-02`、`I-03`、`I-04`
- 变更要点：
  - 为下载场景提供 `requestBlob`，统一鉴权、错误处理、埋点。
  - 统一处理重复提交：非 200 统一抛错，避免调用侧误判成功。
  - 仅在有 JSON body 时设置 `Content-Type`。
- 影响面：
  - `frontend/src/services/request.ts`
  - `frontend/src/services/modules/course.ts`
  - `frontend/src/services/modules/enrollment.ts`
  - `frontend/src/services/modules/user.ts`
- 回归验证：
  - 同步验证导出、禁用用户、删除用户等接口在 401/500/网络错误下表现一致。

### P0-04 管理端“清除缓存”改为命名空间清理
- 关联问题：`I-06`
- 变更要点：
  - 仅删除前端应用 key（例如 `sc_` 前缀），禁止全量 `clear()`。
- 影响面：
  - `frontend/src/views/admin/AdminSystem.vue`
  - `frontend/src/services/request.ts`（如需统一 key 管理）
- 回归验证：
  - 执行清理后本应用会话被清空，但同域其他 key 保留。

---

## 4. P1（高优先级，降低回归与维护成本）

### P1-01 建立质量门禁（lint + test）
- 关联问题：`I-12`
- 变更要点：
  - 增加 ESLint 与最小单元测试框架（建议 Vitest）。
  - 在 `package.json` 增加 `lint`、`test` 脚本。
  - CI 将 `type-check + lint + test + build` 设为必过。
- 影响面：
  - `frontend/package.json`
  - 新增 ESLint/Vitest 配置文件
- 回归验证：
  - 本地与 CI 均可稳定执行完整检查链路。

### P1-02 类型收紧与契约对齐
- 关联问题：`I-08`、`I-09`
- 变更要点：
  - 先开启 `strictNullChecks`，修复高频报错后逐步提升到 `noImplicitAny`。
  - 修复 API 签名与调用不一致问题（如 `getPublished` 参数、`updateStatus` 操作人参数）。
- 影响面：
  - `frontend/tsconfig.json`
  - `frontend/src/types/api.ts`
  - `frontend/src/services/modules/**`
  - 相关调用页面（如 `Home.vue`、`AdminUsers.vue`）
- 回归验证：
  - 新增类型错误为 0；关键业务路径行为不变。

### P1-03 Store 一致性治理
- 关联问题：`I-10`、`M-01`
- 变更要点：
  - 组件层禁止直接解析 `sessionStorage user`，统一通过 `authStore`。
  - 删除 `setSkipFirstCheck` 或实现其真实职责。
- 影响面：
  - `frontend/src/views/teacher/TeacherCourses.vue`
  - `frontend/src/views/admin/AdminUsers.vue`
  - `frontend/src/services/request.ts`
  - `frontend/src/views/Login.vue`
- 回归验证：
  - 会话一致性场景（登录/刷新/登出）无行为回退。

### P1-04 AdminUsers 网络行为收敛
- 关联问题：`I-11`
- 变更要点：
  - 去掉深度 watch 拉取，保留定时轮询或事件驱动之一。
  - 批量操作改为后端批量接口或并发控制策略，避免串行慢请求。
- 影响面：
  - `frontend/src/views/admin/AdminUsers.vue`
  - `frontend/src/services/modules/user.ts`（若新增批量接口）
- 回归验证：
  - 在线状态接口 QPS 降低且功能正确。

### P1-05 Confirm 队列化
- 关联问题：`I-07`
- 变更要点：
  - 为 confirm store 增加队列，保证并发 show 可按顺序 resolve。
- 影响面：
  - `frontend/src/stores/confirm.ts`
  - `frontend/src/components/ui/ConfirmDialog.vue`
- 回归验证：
  - 连续触发多个确认弹窗，均可得到正确返回。

---

## 5. P2（结构优化与体验增强）

### P2-01 大文件拆分与职责分层
- 关联问题：`I-13`
- 变更要点：
  - 目标文件优先：`StudyView.vue`、`TeacherCourses.vue`、`StudentProfile.vue`、`animations.css`。
  - 拆分策略：容器页面 + 业务子组件 + composable + 样式分层。
- 影响面：
  - 多个 `views/components/composables/assets`
- 回归验证：
  - 行数与复杂度显著下降，功能与视觉保持一致。

### P2-02 可访问性补全
- 关联问题：`M-02`
- 变更要点：
  - 为 icon-only 按钮补充 `aria-label`。
  - 对关键弹窗与交互补充键盘操作校验。
  - 为复杂图表提供读屏摘要、语义分组与装饰元素隐藏策略。
  - 为自定义选择器补齐 `listbox/option` 与活动项 ARIA 链路。
- 影响面：
  - `frontend/src/components/ui/ConfirmDialog.vue`
  - `frontend/src/components/ui/BaseSelect.vue`
  - `frontend/src/views/TeacherCenter.vue`
  - `frontend/src/views/admin/AdminDashboard.vue`
  - `frontend/src/views/teacher/TeachingCalendar.vue`
  - `frontend/src/views/CourseDetail.vue`
  - `frontend/src/views/student/StudentQuestions.vue`
  - `frontend/src/components/teacher/AlertPanel.vue`
  - `frontend/src/views/teacher/TeacherDiscussion.vue`
  - `frontend/src/views/teacher/TeacherHomeworks.vue`
  - `frontend/src/views/teacher/TeacherCourses.vue`
  - `frontend/src/components/teacher/StudentDetailModal.vue`
- 回归验证：
  - 键盘可操作，读屏可读按钮语义与图表摘要。

### P2-03 清理遗留与重复实现
- 关联问题：`M-03`、`M-04`
- 变更要点：
  - 清理未使用的 `useWebSocket`，或统一改用 composable。
  - 收敛 `!important` 与全局样式覆盖，改为组件化样式策略。
  - 将高优先级全局表单选择器下沉为 `:where(...)` 低优先级基线，降低样式冲突概率。
- 影响面：
  - `frontend/src/composables/useWebSocket.ts`
  - `frontend/src/services/websocket.js`
  - `frontend/src/assets/main.css`
- 回归验证：
  - 功能无回退，样式冲突与覆盖成本下降。

---

## 6. 每批次统一验收清单

## P0 验收
- 命令：
  - `npm run type-check`
  - `npm run build`
- 场景：
  - 登录后实时通知可达
  - 强制下线可达
  - 首屏受保护路由无错误跳转
  - 导出与状态更新接口在异常场景行为一致

## P1 验收
- 命令：
  - `npm run lint`
  - `npm run test`
  - `npm run type-check`
  - `npm run build`
- 场景：
  - 类型契约一致
  - confirm 并发调用无悬挂
  - AdminUsers 在线状态请求量下降

## P2 验收
- 命令：
  - `npm run lint`
  - `npm run test`
  - `npm run build`
- 场景：
  - 大页面拆分后功能等价
  - 可访问性检查通过（键盘与读屏关键路径）
  - 样式冲突无新增


