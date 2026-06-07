---
name: tailwind-styling-standards
description: Tailwind CSS 4 / Design Tokens / 中国传统色 / responsive / dark mode — frontend styling standards.
---

# Tailwind CSS 样式规范

> 适用项目：`frontend/` — 基于 Vue 3 + Tailwind CSS 4 + 自定义 Design Token 的在线教育平台前端。

---

## 1. 设计 Token 体系

> 所有 Token 定义位于 `main.css` 的 `@theme` 块中。Tailwind CSS 4 使用 CSS-based 配置，无 `tailwind.config.*` 文件。

### 1.1 色彩系统

项目使用中国传统色命名 + 语义色双轨体系，**所有颜色必须使用 Token 引用，禁止任意值**：

#### 中国传统色（品牌色）

| Token | HEX | 用途 |
|-------|-----|------|
| `danqing` | `#88ada6` | 主点缀色 / 焦点环 / 输入框 focus |
| `qianhong` | `#f0a1a8` | 暖调点缀 |
| `yuebai` | `#d6ecf0` | 冷调背景 |
| `shuimo` | `#50616d` | 正文色 / 中性文字 |
| `qingbai` | `#c0ebd7` | 清新绿调 |
| `yanzhi` | `#c45a65` | 胭脂红 |
| `zhizi` | `#eacd76` | 栀子黄 |
| `qingsong` | `#5dbe8a` | 青松绿 |
| `tianlv` | `#20a162` | 天绿 |
| `zijinghui` | `#815c94` | 紫荆灰 |
| `qianniuzi` | `#681752` | 牵牛紫 |
| `halanzi` | `#1781b5` | 哈兰紫蓝 |
| `yanzhihong` | `#c04851` | 胭脂红（深） |
| `danya` | `#789262` | 淡雅绿 |
| `qinghua` | `#2e59a7` | 青花蓝 |
| `tanxiang` | `#b78d71` | 檀香色 |

#### 语义色

| Token | 映射 | 用途 |
|-------|------|------|
| `primary` | `#2e59a7` (qinghua) | 主色 / 品牌色 |
| `secondary` | `#20a162` (tianlv) | 次色 |
| `success` | `#5dbe8a` (qingsong) | 成功状态 |
| `warning` | `#eacd76` (zhizi) | 警告状态 |
| `danger` | `#c45a65` (yanzhi) | 危险 / 错误 |
| `info` | `#1781b5` (halanzi) | 信息提示 |
| `text-main` | `#50616d` (shuimo) | 正文色 |
| `text-muted` | `#64748b` | 辅助文字 |

#### 背景色（CSS 变量，不在 `@theme` 中）

| 变量 | 亮色 | 暗黑 |
|------|------|------|
| `--color-bg-primary` | `--color-slate-50` | `--color-slate-900` |
| `--color-bg-secondary` | `--color-white` | `--color-slate-800` |
| `--color-bg-tertiary` | `--color-slate-100` | `--color-slate-700` |
| `--color-bg-glass` | `rgba(255,255,255,0.65)` | `rgba(30,41,59,0.8)` |
| `--color-bg-glass-hover` | `rgba(255,255,255,0.85)` | `rgba(51,65,85,0.9)` |

#### ✅ / ❌ 色彩规范

| ✅ 正确 | ❌ 错误 | 原因 |
|---------|---------|------|
| `text-primary` | `text-[#2e59a7]` | 使用 Token，保证暗色模式自动切换 |
| `bg-danqing/10` | `bg-[#88ada6]/10` | 使用色名 + 透明度修饰，符合设计系统 |
| `border-danger/20` | `border-[#c45a6533]` | 语义色统一管理状态反馈 |
| `text-text-main` | `text-[#50616d]` | 正文色走 text-main Token |
| `from-qinghua to-halanzi` | `from-[#2e59a7] to-[#1781b5]` | 渐变也使用 Token |
| `hover:bg-slate-50` | — | Tailwind 内置色板可用于非品牌场景 |
| `text-red-500` | — | 紧急状态可用 Tailwind 内置色，但 danger 语义色优先 |

### 1.2 字体系统

| Token | 值 | 用途 |
|-------|-----|------|
| `font-family-song` | `"Noto Serif SC", serif` | 衬线 / 标题 / 装饰 |
| `font-family-hei` | `"Noto Sans SC", sans-serif` | 无衬线 / 正文 / UI 默认 |

> 全局默认字体为 `'Noto Sans SC', system-ui, -apple-system, sans-serif`，定义在 `:root` 中。

### 1.3 间距与尺寸

> Tailwind 4 默认 spacing scale：`0` `0.5` `1` `1.5` `2` `2.5` `3` `3.5` `4` `5` `6` `7` `8` `9` `10` `11` `12` `14` `16` `20` `24` `28` `32` `36` `40` `44` `48` `52` `56` `60` `64` `72` `80` `96`

#### ✅ / ❌ 间距规范

| ✅ 正确 | ❌ 错误 | 原因 |
|---------|---------|------|
| `p-4` `m-8` `gap-6` | `p-[13px]` `m-[37px]` | 预定义间距，保证视觉节奏 |
| `px-5 py-2.5` | `px-[20px] py-[10px]` | 预定义 scale 足够覆盖 |
| `min-h-[44px]` 触控目标 | `min-h-[37px]` | 44px 是 iOS/Android 触控最小推荐值 |
| `gap-3` | `gap-[12px]` | 间距走 scale |
| `p-6` (卡片内边距) | `p-5` 或 `p-7` | 卡片内边距约定为 p-6（24px） |

### 1.4 断点体系

| Token | 值 | Tailwind 前缀 | 用途 |
|-------|-----|-------------|------|
| `sm` | `640px` | `sm:` | 手机横屏 / 小平板 |
| `tablet` | `744px` | `tablet:` | 平板竖屏（项目自定义） |
| `fold` | `880px` | `fold:` | 折叠屏 / 小平板横屏（项目自定义） |
| `md` | `768px` | `md:` | 平板标准 |
| `lg` | `1024px` | `lg:` | 小桌面 |
| `xl` | `1280px` | `xl:` | 标准桌面 |
| `2xl` | `1536px` | `2xl:` | 大桌面 |

#### ✅ / ❌ 响应式规范

| ✅ 正确 | ❌ 错误 | 原因 |
|---------|---------|------|
| `grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3` | `@media (min-width: 768px) { ... }` 写在 `<style>` 中 | Tailwind 响应式前缀 |
| `md:flex hidden` | `v-if="isDesktop"` 控制布局 | 响应式用 CSS，不依赖 JS |
| `tablet:px-8 px-4` | `px-4 tablet:px-8` | 移动优先：无前缀 = 最小屏幕 |
| `max-w-7xl mx-auto px-4 sm:px-6 lg:px-8` | 固定 `width: 1200px` | 响应式容器 + 渐进内边距 |

### 1.5 圆角

| Token | 值 | 用途 |
|-------|-----|------|
| `radius-xl` | `1rem` (16px) | 卡片 / 弹窗 / 输入框 |
| `radius-2xl` | `1.5rem` (24px) | 弹窗内容 / 玻璃卡片 |
| `radius-3xl` | `2rem` (32px) | 大容器 / Hero 区域 |

### 1.6 动效 Token

| 变量 | 值 | 用途 |
|------|-----|------|
| `--motion-duration-fast` | `0.12s` | 微交互（hover 颜色变化） |
| `--motion-duration-base` | `0.16s` | 按钮/控件基础过渡 |
| `--motion-duration-medium` | `0.2s` | 弹窗/路由/入场动画 |
| `--motion-duration-slow` | `0.2s` | 与 medium 统一（已压缩） |
| `--motion-duration-complex` | `0.4s` | 复杂序列动画 |
| `--motion-duration-loop` | `1.2s` | 循环动画（shimmer） |
| `--motion-duration-loop-slow` | `2.4s` | 慢循环（float） |
| `--motion-ease-standard` | `cubic-bezier(0.4, 0, 0.2, 1)` | 统一缓动曲线 |

> 所有过渡/动画必须使用 `duration-300`（映射 300ms）或上述 CSS 变量，禁止自定义贝塞尔。

### 1.7 暗色模式

项目使用 `.dark` class 策略（在 `<html>` 上切换），所有暗色适配通过 CSS 变量自动完成。

---

## 2. Tailwind 使用规范

### 2.1 @apply 禁用原则

> **核心原则：Tailwind utility class 直接写在模板中，禁止用 `@apply` 抽取。**

### 2.2 任意值禁用原则

> **禁止使用 `[]` 任意值语法绕过设计系统。** 如确需特殊值，先确认 Token 体系是否覆盖，未覆盖则在 `@theme` 中注册。

**唯一例外**：触控目标 `min-h-[44px]` / `min-w-[44px]`，这是无障碍硬性要求，且 Tailwind 默认 scale 不含 44。

### 2.3 class 顺序约定

> 按功能分组，保持一致的书写顺序，增强可读性。超过 **5 个 utility class 必须换行**。

**顺序规则**：Layout → Sizing → Spacing → Typography → Visual → Misc

### 2.4 响应式前缀

> 移动优先（Mobile First）：无前缀 = 最小屏幕，逐层覆盖。

### 2.5 暗色模式

仅装饰色使用 `dark:` 前缀，语义色通过 CSS 变量自动适配。

---

## 3. 组件样式一致性

### 3.1 变体模式（Props-Driven）

> 使用 `props` + class 映射表驱动样式变体，禁止内联 style 或模板内三元嵌套。

### 3.2 条件样式

> 使用 `:class` 数组/对象语法，保持模板简洁。

### 3.3 组件复用 vs 复制

抽出通用组件复用，不重复粘贴相同的 utility classes。

### 3.4 Scoped Style 最小化

> `<style scoped>` 仅用于 Vue Transition 动画钩子和 Tailwind 无法覆盖的极少数场景。

---

## 4. 常见错误模式

- NEVER `!important` in Scoped Style
- NEVER 混用 BEM / 语义化 Class
- NEVER 超 5 个 Utility Class 不换行
- NEVER 用 CSS 替代 Tailwind 响应式
- NEVER 在 Vue 中用 JS 计算媒体查询

---

## 5. 中文执行层

### 5.1 触发条件

- 任何涉及 `frontend/` 目录下 `.vue` / `.css` 文件的样式修改
- 新增 UI 组件、页面布局
- 代码审查中发现样式违规

### 5.2 前置条件

- [ ] 确认已读取 `main.css` 中的 `@theme` 块，了解当前 Design Token
- [ ] 确认需要使用的颜色/间距/字号/断点是否在 Token 体系中

### 5.3 执行步骤

1. **Token 检查** — 目标样式值是否在 §1 设计 Token 覆盖范围内？
2. **class 编写** — 按 §2.3 顺序分组，超 5 个换行
3. **响应式检查** — 是否使用了 sm:/md:/lg: 前缀？移动优先？
4. **暗色模式检查** — 背景/文字/边框是否能在暗色模式下可读？
5. **动效检查** — 过渡是否使用 `duration-300` 或项目 duration Token？
6. **无违规检查** — 全文搜索 `!important` `@apply` `[#` `@media` `BEM__`，确认无违规

### 5.4 完成证据

- 新增/修改的 Vue 组件模板中无 `style` 内联字符串
- 无 `text-[#...]` / `bg-[...]` / `p-[...]` 等任意值（触控 44px 除外）
- 所有颜色使用 Token（`primary`/`danger`/`danqing`/`text-main` 等）
- 响应式使用 Tailwind 断点前缀，无手写 `@media`
- 暗色模式通过语义变量覆盖（非装饰色无需 `dark:` 前缀）
- Scoped style 块仅含 Transition 钩子或 `prefers-reduced-motion` 覆盖
- 超 5 个 utility class 已换行
