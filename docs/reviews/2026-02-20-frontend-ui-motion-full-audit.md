# 2026-02-20 前端 UI 与动效全量审查报告（60fps 优先）

## 1. 审查概览
- 审查日期：`2026-02-20`
- 审查范围：`frontend/src/**`、`frontend/src/assets/**`、`frontend/tailwind.config.js`
- 审查方法：
  - 静态全量扫描（样式、动效、交互、主题 token）
  - 基线验证（`lint`/`type-check`/`test`）
  - Skill 基线对照（`ui-ux-pro-max`：glassmorphism、reduced-motion、hover vs tap、contrast）
- 审查目标：在不改代码前提下，输出可直接排期的 `P0/P1/P2` 风险清单与优化路径

## 2. 基线验证（执行证据）
- 命令：`npm run lint`（目录 `frontend`）
  - 结果：通过（Exit Code `0`）
- 命令：`npm run type-check`（目录 `frontend`）
  - 结果：通过（Exit Code `0`）
- 命令：`npm run test`（目录 `frontend`）
  - 结果：通过（Exit Code `0`），`3` 个测试文件、`11` 个测试全部通过

## 3. 全量索引统计（热区地图）

### 3.1 指标总量
| 指标 | 数量 | 说明 |
|---|---:|---|
| `transition-all` | 182 | 过渡属性范围过宽，增加不必要重绘风险 |
| `animation: ... infinite` | 34 | 持续动画较多，需按场景区分是否必要 |
| `linear infinite` | 6 | 线性无限循环，存在“机械感+高频”风险 |
| `style="...animation..."` | 56 | 动效逻辑分散到模板 inline style，治理成本高 |
| `@keyframes` | 114 | 动效定义规模较大，重复与漂移风险高 |

### 3.2 主要热区文件（Top）
- `transition-all` 热区：
  - `frontend/src/views/teacher/TeacherDashboard.vue`（16）
  - `frontend/src/components/student/StudentProfileInfoCard.vue`（10）
  - `frontend/src/views/DoHomework.vue`（10）
  - `frontend/src/views/student/StudentMyCourses.vue`（10）
- `infinite` 热区：
  - `frontend/src/assets/animations/components.css`（11）
  - `frontend/src/assets/animations/utilities.css`（4）
  - `frontend/src/assets/animations/teacher-center.css`（3）

## 4. 三端主题一致性矩阵（学生/教师/管理）
| 端 | 主题主色 | 玻璃层级 | 动效节奏 | 结论 |
|---|---|---|---|---|
| 学生端 | `qinghua/halanzi`（证据：`frontend/src/layouts/StudentLayout.vue:105`） | `bg-white/80 + backdrop-blur-xl`（`frontend/src/layouts/StudentLayout.vue:98`） | 页面内局部又定义 `0.5s` scoped 动效（`frontend/src/views/student/StudentDashboard.vue:340`） | 存在动效节奏漂移 |
| 教师端 | `tianlv/qingsong`（`frontend/src/views/TeacherCenter.vue:364`） | `bg-white/80 + backdrop-blur-xl`（`frontend/src/views/TeacherCenter.vue:360`） | 无限循环动画较多（`frontend/src/assets/animations/teacher-center.css:17`） | 视觉统一，性能需收敛 |
| 管理端 | `zijinghui/qianniuzi`（`frontend/src/views/AdminCenter.vue:231`） | `bg-white/80 + backdrop-blur-xl`（`frontend/src/views/AdminCenter.vue:226`） | 采用 `clip-path` 过渡（`frontend/src/views/AdminCenter.vue:385`） | 动效路径偏重，需按设备降级 |

## 5. 风险清单（按优先级）

### UIA-P0-01
- 级别：`P0`
- 类别：动效 Token 体系
- 文件：`frontend/src/assets/main.css:25`、`frontend/src/assets/main.css:27`、`frontend/src/assets/main.css:28`
- 现象：`--motion-duration-medium` 与 `--motion-duration-slow` 同为 `0.2s`，且被大量 `infinite` 动画复用。
- 影响：循环动画频率过高，容易引发视觉噪声与持续重绘压力，不利于稳定 60fps。
- 优化建议：新增专用循环时长 token（如 `--motion-duration-loop` ≥ `1.2s`），仅给加载态保留快速循环。
- 验证场景：`Home`/`Login`/`TeacherCenter`/`StudentDashboard` 同时存在循环元素时，观察是否仍有高频闪烁。

### UIA-P0-02
- 级别：`P0`
- 类别：高成本无限动画
- 文件：`frontend/src/assets/animations/teacher-center.css:91`、`frontend/src/assets/animations/teacher-center.css:96`、`frontend/src/assets/animations/components.css:218`、`frontend/src/assets/animations/components.css:222`
- 现象：`box-shadow`、`filter: blur()`、`filter: brightness()` 参与循环动效。
- 影响：栅格化开销高，在低端设备或密集场景下易掉帧。
- 优化建议：优先用 `transform/opacity` 替代；装饰性效果改为 hover/一次性触发，避免常驻无限循环。
- 验证场景：教师中心今日高亮、徽章发光、骨架屏并存时滚动与点击保持流畅。

### UIA-P0-03
- 级别：`P0`
- 类别：布局动画主线程压力
- 文件：`frontend/src/layouts/StudentLayout.vue:98`、`frontend/src/layouts/StudentLayout.vue:144`
- 现象：侧边栏与主内容区域使用 `transition-all`，并标记 `will-change-[width,transform]`、`will-change-[margin]`。
- 影响：布局相关属性参与动画，容易触发布局重算与抖动。
- 优化建议：收敛为 `transform` 驱动（抽屉式位移），避免 `width/margin` 参与动画路径。
- 验证场景：`375x812` 与 `1440x900` 下频繁展开/收起侧栏，无明显卡顿。

### UIA-P0-04
- 级别：`P0`
- 类别：主题 token 失配
- 文件：`frontend/src/views/student/StudentDashboard.vue:229`、`frontend/src/views/student/StudentDashboard.vue:312`、`frontend/src/components/charts/KnowledgeMasteryChart.vue:94`、`frontend/src/composables/useStudentCourses.ts:87`、`frontend/tailwind.config.js:20`
- 现象：使用 `songshi/qiuxiang/mudan/ouhe` 等未在 Tailwind 颜色表声明的 token。
- 影响：对应类名无法稳定生效，导致主题颜色回退或风格漂移。
- 优化建议：统一到已声明语义色，或补齐缺失 token 并给出设计语义说明。
- 验证场景：`StudentDashboard`、`StudentMyCourses`、`KnowledgeMasteryChart` 在亮/暗色下颜色一致且可预期。

### UIA-P1-01
- 级别：`P1`
- 类别：`transition-all` 过量
- 文件：`frontend/src/views/teacher/TeacherDashboard.vue`、`frontend/src/views/student/StudentMyCourses.vue`、`frontend/src/views/DoHomework.vue`
- 现象：全仓 `transition-all` 共 `182` 处，热区文件集中。
- 影响：无关属性也参与过渡，增加性能不确定性和调试复杂度。
- 优化建议：按组件替换为显式属性过渡（如 `transition-colors/transform/opacity`）。
- 验证场景：高频 hover 列表与卡片组批量操作时动画响应稳定。

### UIA-P1-02
- 级别：`P1`
- 类别：Hover-only 交互
- 文件：`frontend/src/views/student/StudentDashboard.vue:285`、`frontend/src/views/student/StudentDashboard.vue:286`、`frontend/src/views/admin/AdminUsers.vue:520`
- 现象：徽章说明依赖 `mouseenter/mouseleave`，操作按钮依赖 `group-hover` 才可见。
- 影响：触屏与键盘路径可达性不足，导致信息与操作不可发现。
- 优化建议：补齐 `focus-visible`、`click/tap` 等价交互，并为触屏场景提供常显或显式入口。
- 验证场景：仅键盘（Tab/Enter）与触屏点击可完成同等操作。

### UIA-P1-03
- 级别：`P1`
- 类别：对比度风险
- 文件：`frontend/src/views/Home.vue:478`、`frontend/src/views/Login.vue:303`、`frontend/src/components/comments/CommentItem.vue:169`
- 现象：关键说明文本大量使用 `text-*/40`、`text-*/30`。
- 影响：在玻璃背景与浅色底叠加时可读性下降。
- 优化建议：正文/关键状态提升到 `text-*/60` 以上或改用语义 `text-muted`（满足 WCAG 4.5:1）。
- 验证场景：亮色模式下常见笔记本屏幕阅读清晰，不依赖放大。

### UIA-P1-04
- 级别：`P1`
- 类别：动效样式可维护性
- 文件：`frontend/src/assets/animations/components.css:37`、`frontend/src/assets/animations/components.css:52`、`frontend/src/assets/animations/components.css:67`
- 现象：存在临时推理式注释与重复定义段落（同一功能多次重定义）。
- 影响：后续维护者难以判断真实生效路径，容易引入回归。
- 优化建议：清理临时注释，保留单一实现并补中文注释说明“为何这样做”。
- 验证场景：样式文件内同名规则单一定义，可直接追溯。

### UIA-P1-05
- 级别：`P1`
- 类别：动效语义失真
- 文件：`frontend/src/views/student/StudentDashboard.vue:374`、`frontend/src/views/student/StudentDashboard.vue:378`、`frontend/src/views/student/StudentDashboard.vue:379`
- 现象：`urgentPulse` 的关键帧 alpha 为 `0`，视觉反馈接近不可见。
- 影响：业务触发了“紧急提醒”，但用户感知弱，反馈语义不一致。
- 优化建议：调整颜色与透明度范围，确保“紧急”状态可辨识但不过度干扰。
- 验证场景：`pendingHomework > 2` 时视觉可识别且不刺眼。

### UIA-P1-06
- 级别：`P1`
- 类别：循环动画节奏不分层
- 文件：`frontend/src/assets/animations/utilities.css:10`、`frontend/src/assets/animations/teacher-center.css:17`、`frontend/src/views/Home.vue:578`、`frontend/src/views/Login.vue:710`
- 现象：装饰性循环动效与加载动效共享同一中速 token，并出现 `linear infinite`。
- 影响：动效语言不统一，视觉高级感下降。
- 优化建议：按“加载/提醒/装饰”拆分节奏，并将 `linear` 仅保留给必要转圈加载。
- 验证场景：同屏多动画时观感平滑，无明显机械抖动。

### UIA-P2-01
- 级别：`P2`
- 类别：样式治理
- 文件：`frontend/src/views/student/StudentDashboard.vue:79`、`frontend/src/views/student/StudentDashboard.vue:184`、`frontend/src/views/student/StudentRecords.vue:226`
- 现象：`style="animation..."` 共 `56` 处，延迟和 fill-mode 主要通过 inline style 分散配置。
- 影响：难统一管理，后续批量调优成本高。
- 优化建议：提炼延迟工具类或 CSS 变量方案，减少模板内联动效字符串。
- 验证场景：统一修改动效节奏时可在样式层一次收敛。

### UIA-P2-02
- 级别：`P2`
- 类别：玻璃效果性能分级
- 文件：`frontend/src/assets/main.css:159`、`frontend/src/views/TeacherCenter.vue:445`、`frontend/src/views/AdminCenter.vue:296`
- 现象：多处固定高强度 `backdrop-blur`，缺少设备能力分级策略。
- 影响：低性能设备下滚动/切换可能出现稳定性波动。
- 优化建议：移动端与低性能设备下降级 blur 半径与阴影层级，桌面端保留高级质感。
- 验证场景：移动端滚动和弹窗开合流畅优先，视觉可接受降级。

## 6. 已满足项（本轮核验）
- 全局 reduced motion 守卫存在：`frontend/src/assets/main.css:272`。
- 守卫采用 `animation-duration/iteration-count/transition-duration` 的全局压制策略：`frontend/src/assets/main.css:274` 到 `frontend/src/assets/main.css:277`。
- 三端主色方向基本稳定（学生蓝、教师绿、管理紫），具备统一设计语言基础。

## 7. 验证场景与测试用例
1. 页面覆盖：`Home`、`Login`、`StudentDashboard`、`TeacherCenter`、`AdminCenter`、`TeachingCalendar`、`StudentMyCourses`、`CourseDetail`。
2. 设备与视口：`375x812`、`768x1024`、`1024x768`、`1440x900`。
3. 主题与可访问性：亮/暗色，`prefers-reduced-motion` 开关，键盘 `Tab/Enter/Esc` 与触屏点击。
4. 性能观察点：侧栏开合、卡片 hover 密集区、图表刷新、骨架屏循环动画。
5. 验收标准：主观无明显掉帧与抖动；冲突时优先 60fps，视觉效果可降级。

## 8. 公共 API / 接口 / 类型变更评估
- 本轮为审查交付，不涉及代码改动。
- 无公共 API、接口、类型变更。

## 9. 后续整改顺序（建议）
1. 先处理 `P0`：动效 token、高成本无限动画、布局动画路径、缺失主题 token。
2. 再处理 `P1`：`transition-all` 收敛、hover-only 交互、低对比度文本、样式文件清理。
3. 最后处理 `P2`：inline 动画治理、玻璃效果设备分级。
4. 每批次完成后固定复验：`npm run lint`、`npm run type-check`、`npm run test`。
