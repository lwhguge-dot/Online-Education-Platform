# UI/动效问题清单

| ID | 模块 | 问题描述 | 影响等级 | 优化优先级 | 预估工作量 | 状态 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| VIS-001 | 全局样式 | main.css 中存在大量硬编码 Hex 色值，未统一使用 Design Token | High | P0 | 1h | **已修复** |
| VIS-002 | 组件库 | 圆角规范不一致（rounded-xl vs rounded-2xl） | Low | P2 | 2h | 待处理 |
| RES-001 | 学生中心 | 侧边栏在移动端（<768px）无法折叠，遮挡内容 | High | P0 | 2h | **已修复** |
| RES-002 | 仪表盘 | 卡片布局在平板尺寸（md）下过于拥挤 | Medium | P1 | 1h | 待处理 |
| ACC-001 | 基础组件 | BaseModal 缺少 role="dialog" 和 aria-modal 属性 | High | P0 | 0.5h | **已修复** |
| ACC-002 | 基础组件 | BaseButton Loading 状态缺少 aria-busy 标识 | Medium | P1 | 0.5h | **已修复** |
| ACC-003 | 登录页 | 图标按钮（如密码切换）缺少 aria-label | Medium | P1 | 0.5h | 待处理 |
| ANI-001 | 全局动画 | 关键动画缺少 will-change 属性，可能引发重排 | Medium | P1 | 0.5h | **已修复** |
| ANI-002 | 全局动画 | 未适配 prefers-reduced-motion | Low | P2 | 0.5h | **已修复** |
| PERF-001 | 构建配置 | 缺少 Gzip/Brotli 压缩配置 | High | P0 | 0.5h | **已修复** |
| PERF-002 | 构建配置 | 依赖包未进行分包处理（Manual Chunks） | High | P0 | 0.5h | **已修复** |
| INT-001 | 学生仪表盘 | 数据加载时显示为空白，缺少骨架屏反馈 | Medium | P1 | 1h | **已修复** |

## 修复详情说明

### 1. 视觉一致性 (VIS-001)
- **问题**: `src/assets/main.css` 中直接使用了 `#f8fafb`, `#0f172a` 等颜色。
- **修复**: 重构了 CSS 变量定义，引用 `theme('colors.slate.50')` 等 Tailwind 配置值，确保暗黑模式和主题切换的一致性。

### 2. 响应式适配 (RES-001)
- **问题**: `StudentCenter.vue` 侧边栏在移动端采用固定宽度，导致布局错乱。
- **修复**: 引入了抽屉式交互（Drawer），在 `<768px` 屏幕下默认隐藏，点击汉堡菜单后以覆盖层形式滑入，并添加了背景遮罩。

### 3. 可访问性 (ACC-001, ACC-002)
- **BaseModal**: 添加了 `role="dialog"`, `aria-modal="true"`, `aria-labelledby`，支持屏幕阅读器识别。
- **BaseButton**: 添加了 `aria-busy` 属性，在加载状态下通知辅助技术。

### 4. 动效优化 (ANI-001, ANI-002)
- **GPU加速**: 为 `.animate-slide-up`, `.animate-fade-in` 等类添加了 `will-change: transform, opacity`。
- **减弱动态**: 添加了 `@media (prefers-reduced-motion)` 查询，为敏感用户自动关闭动画。

### 5. 性能优化 (PERF-001, PERF-002)
- **Vite配置**: 添加了 `vite-plugin-compression` 进行 Gzip 压缩，配置了 `rollupOptions.output.manualChunks` 将 `lucide-vue-next` 等大库单独打包。

### 6. 交互反馈 (INT-001)
- **骨架屏**: 为 `StudentDashboard` 引入了 `SkeletonDashboard` 组件，在数据加载期间展示占位 UI，提升用户体验。
