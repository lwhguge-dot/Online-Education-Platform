---
name: vue3-frontend-standards
description: Vue 3 / Composition API / Pinia / Vite / TypeScript strict — frontend coding standards for the edu platform.
---

# Vue 3 前端开发规范

> 适用项目：`frontend/` — 基于 Vue 3 + Vite + TypeScript + Pinia + Vue Router + Tailwind CSS 的在线教育平台前端。

---

## 1. 项目概况

| 维度 | 技术选型 |
|------|----------|
| 框架 | Vue 3.5+（Composition API） |
| 构建工具 | Vite 7 |
| 语言 | TypeScript 5.9（strict 模式） |
| 状态管理 | Pinia 3（setup store 风格） |
| 路由 | Vue Router 4（History 模式） |
| CSS | Tailwind CSS 4 + 自定义 design tokens |
| 图标 | lucide-vue-next |
| HTTP | 原生 fetch（非 axios） |
| 监控 | Sentry（`@sentry/vue`） |
| PWA | vite-plugin-pwa（autoUpdate） |
| 产物优化 | gzip 压缩 + rollup-visualizer |

**路径别名**：`@/` 映射到 `src/`。

---

## 2. 组件设计规范

### 2.1 `<script setup>` 必须

所有 `.vue` 组件统一使用 `<script setup>` + TypeScript，不混用 Options API 和 Class API。

### 2.2 Props / Emits 声明方式

使用 Runtime Declaration（`defineProps` 对象字面量），结合 JSDoc 注释补充说明：

```ts
const props = defineProps({
  variant: {
    type: String,
    default: 'primary',
    validator: (value: string) => ['primary', 'secondary', 'danger'].includes(value)
  },
  loading: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  block: { type: Boolean, default: false }
})
```

Emits 使用数组字面量或函数签名：

```ts
const emit = defineEmits(['update:modelValue', 'close'])
// 或
const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'close'): void
}>()
```

### 2.3 组件命名

- **文件名**：PascalCase，如 `BaseButton.vue`、`DailyGoalProgress.vue`、`StudentDashboard.vue`
- **模板中使用**：PascalCase，如 `<BaseButton />`、`<DailyGoalProgress />`
- **目录分类**：
  - 通用 UI 组件 → `src/components/ui/`
  - 业务领域组件 → `src/components/student/` / `src/components/teacher/` / `src/components/admin/` / `src/components/charts/`
  - 页面级视图 → `src/views/`（按角色划分子目录）

### 2.4 SFC 结构顺序

严格遵循：`<script setup>` → `<template>` → `<style scoped>`

```vue
<script setup>
// 1. imports (vue, vue-router, stores, services, components, icons)
// 2. props / emits
// 3. composables (useRouter, useStore, etc.)
// 4. reactive state (ref, reactive)
// 5. computed
// 6. methods
// 7. lifecycle hooks (onMounted, watch, etc.)
</script>

<template>
  <!-- 模板内容 -->
</template>

<style scoped>
/* 仅组件私有样式 */
</style>
```

### 2.5 Tailwind CSS 使用约定

- 优先 Tailwind 原子类，避免内联 style
- 颜色优先用自定义 token：`text-shuimo`、`bg-qinghua`、`text-halanzi`、`border-slate-200`、`text-text-main`
- 响应式采用 Mobile-first：先写移动端样式，再用 `md:` / `lg:` 前缀覆写
- 触摸目标最小 44px：`min-h-[44px] min-w-[44px]`
- 过渡动画统一时长：`duration-300`

### 2.6 无障碍（a11y）

- 交互元素必须有 `focus-visible:ring` 焦点样式
- 弹窗使用 `role="dialog"` + `aria-modal="true"`
- 加载状态用 `aria-busy` 属性
- 图标按钮必须有 `aria-label`

---

## 3. 状态管理规范

### 3.1 Setup Store 风格

所有 Pinia Store 统一使用 Setup Store（Composition API 风格），不使用 Options Store：

```ts
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Ref, ComputedRef } from 'vue'

export const useAuthStore = defineStore('auth', () => {
  // state — 显式标注 Ref 类型
  const user: Ref<User | null> = ref(null)
  const token: Ref<string | null> = ref(null)
  const loading: Ref<boolean> = ref(true)

  // getters — 显式标注 ComputedRef 类型
  const isAuthenticated: ComputedRef<boolean> = computed(() => !!token.value && !!user.value)

  // actions — 普通函数
  function login(newToken: string, newUser: User): void { /* ... */ }
  function logout(): void { /* ... */ }

  return { user, token, loading, isAuthenticated, login, logout }
})
```

### 3.2 Store 命名约定

- store 变量名：`useXxxStore`，如 `useAuthStore`、`useToastStore`
- `defineStore` 第一个参数（store id）：小驼峰，如 `'auth'`、`'toast'`
- 文件名与 store id 一致：`auth.ts`、`toast.ts`、`confirm.ts`

### 3.3 现有 Store 清单

| Store | 文件 | 职责 |
|-------|------|------|
| `useAuthStore` | `stores/auth.ts` | 用户认证状态、登录/登出、token 管理、Sentry 用户上下文同步 |
| `useToastStore` | `stores/toast.ts` | 全局 Toast 消息队列 |
| `useConfirmStore` | `stores/confirm.ts` | 全局确认弹窗 |

---

## 4. 路由规范

### 4.1 懒加载

所有页面组件必须使用动态 import 实现路由级代码分割：

```ts
const routes: RouteRecordRaw[] = [
  {
    path: '/student',
    component: () => import('../layouts/StudentLayout.vue'),
    meta: { requiresAuth: true, allowedRoles: ['student', 'admin'], title: '学生中心' },
    children: [
      {
        path: 'dashboard',
        name: 'StudentDashboard',
        component: () => import('../views/student/StudentDashboard.vue'),
        meta: { title: '学习概览' }
      }
    ]
  }
]
```

### 4.2 Route Meta

通过 `declare module 'vue-router'` 扩展 `RouteMeta` 类型：

```ts
declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    requiresAuth?: boolean
    allowedRoles?: UserRole[]
  }
}
```

- `title`：页面标题，通过 `afterEach` 守卫自动设置 `document.title`
- `requiresAuth`：是否需要登录（`true` 时触发 beforeEach 鉴权）
- `allowedRoles`：允许访问的角色数组，不匹配则重定向到首页

### 4.3 路由命名

- `name` 使用 PascalCase：`Home`、`Login`、`StudentDashboard`、`CourseDetail`
- 嵌套子路由使用父级前缀：`StudentDashboard`、`StudentCourses`、`StudentHomeworks`

### 4.4 路由守卫

```ts
router.beforeEach((to, _from, next) => {
  const authStore = useAuthStore()
  if (to.meta.requiresAuth) {
    if (!authStore.isAuthenticated) { next('/login'); return }
    if (to.meta.allowedRoles && authStore.user && !to.meta.allowedRoles.includes(authStore.user.role)) {
      next('/'); return
    }
  }
  next()
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - 智慧课堂` : '智慧课堂'
})
```

---

## 5. API 封装规范

### 5.1 核心请求层

项目使用原生 `fetch` 封装（非 axios），核心位于 `services/request.ts`。

关键能力：
- **自动注入 Bearer Token**（从 `sessionStorage` 读取）
- **友好错误提示**：后端异常/网络错误自动映射为中文提示
- **重复提交拦截**：同一非 GET 请求在 pending 期间拦截重复提交
- **内存缓存**：GET 请求 30 秒内存缓存，非 GET 请求自动清缓存
- **401/403 强退**：Token 过期或被禁用 → 自动跳转登录页
- **Sentry 异常上报**：API 错误自动捕获并上报
- **心跳机制**：30 秒间隔发送 `/auth/heartbeat`

提供的请求方法：

| 方法 | 用途 |
|------|------|
| `request<T>(url, options)` | 标准 JSON 请求，返回 `Result<T>` |
| `requestRaw(url, options)` | 返回原始 `Response`，用于读取响应头 |
| `requestBlob(url, options)` | 文件下载，返回 `{ blob, filename, response }` |
| `cachedRequest<T>(url, options)` | 带缓存的 GET 请求 |

### 5.2 模块化 API 组织

所有业务 API 按模块拆分到 `src/services/modules/`，每个文件导出 API 对象：

```ts
// src/services/modules/auth.ts
import { request } from '../request'
import type { Result, LoginResponse } from '../../types/api'

export const authAPI = {
  login: (email: string, password: string): Promise<Result<LoginResponse>> =>
    request<LoginResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),
  logout: (): Promise<Result<void>> =>
    request<void>('/auth/logout', { method: 'POST' }),
}
```

现有 API 模块：`auth`、`user`、`course`、`enrollment`、`homework`、`interaction`、`stats`、`file`、`health`、`calendar`。

### 5.3 统一入口

`src/services/api.ts` 作为统一入口，聚合导出所有模块。

### 5.4 类型定义

所有请求/响应类型定义在 `src/types/api.ts`：
- **通用类型**：`Result<T>`（统一响应结构）、`PageParams`、`PageResult<T>`
- **实体类型**：`User`、`Course`、`Chapter`、`Homework`、`Comment`、`Announcement` 等
- **DTO 类型**：`CourseDTO`、`ChapterDTO`、`UserProfileDTO` 等
- **联合类型**：`UserRole = 'admin' | 'teacher' | 'student'`、`CourseStatus = 'draft' | 'published' | 'archived'`

---

## 6. Vite 构建规范

### 6.1 路径别名

已配置 `@/` → `src/`，在 tsconfig 和 Vite 中同步生效。

### 6.2 环境变量

使用 `VITE_` 前缀，通过 `import.meta.env` 访问：

| 变量 | 用途 | 默认值 |
|------|------|--------|
| `VITE_API_BASE` | API 请求前缀 | `/api` |
| `VITE_STATIC_BASE` | 静态资源路径 | `''` |
| `VITE_USER_STATIC_BASE` | 用户头像路径 | `''` |
| `VITE_GATEWAY_TARGET` | 开发代理目标 | `http://demo-gateway:8090` |

### 6.3 开发服务器代理

```ts
server: {
  host: '0.0.0.0',
  port: 3000,
  proxy: {
    '/api': { target: gatewayTarget, changeOrigin: true },
    '/ws':  { target: gatewayTarget, changeOrigin: true, ws: true },
    '/oss': { target: gatewayTarget, changeOrigin: true }
  }
}
```

### 6.4 构建配置

**代码分割**：手动指定 vendor chunk，将 Vue 生态与图标库分离。
**产物优化**：Gzip 压缩（`vite-plugin-compression`，阈值 10KB），Bundle 分析（`rollup-plugin-visualizer`）。

### 6.5 PWA 配置

```ts
VitePWA({
  registerType: 'autoUpdate',
  workbox: {
    globPatterns: ['**/*.{js,css,html,ico,png,svg,woff,woff2}'],
    runtimeCaching: [ ... ]
  },
  manifest: { name: 'Edu Platform', short_name: 'Edu', theme_color: '#3b82f6' }
})
```

### 6.6 NPM Scripts

| 命令 | 用途 |
|------|------|
| `npm run dev` | 启动开发服务器 |
| `npm run build` | 类型检查 + 生产构建 |
| `npm run preview` | 预览生产构建 |
| `npm run type-check` | 仅类型检查（不构建） |
| `npm run lint` | ESLint 检查 |
| `npm run lint:fix` | ESLint 自动修复 |
| `npm run test` | 运行 Vitest 测试 |
| `npm run test:watch` | 监听模式测试 |
| `npm run lint:motion` | 动效守卫检查 |

---

## 7. 开发约定速查

- **新增 API 模块**：在 `src/services/modules/` 新建文件 → 在 `src/services/api.ts` 导出
- **新增 Store**：`src/stores/xxx.ts`，使用 `defineStore('xxx', () => { ... })`，导出 `useXxxStore`
- **新增页面**：`src/views/` 下创建，路由用 `() => import()` 懒加载，补充 `meta.title` 和权限信息
- **新增通用组件**：放入 `src/components/ui/`，文件名 PascalCase
- **调用 API**：`import { xxxAPI } from '@/services/api'` → `const res = await xxxAPI.method()`
- **使用 Store**：`const authStore = useAuthStore()`，直接在组件 setup 中调用
- **非 GET 请求后需刷新列表**：调用 `clearCache()`（从 `@/services/request` 导入）清除缓存

---

## 8. Vue 3 防幻觉禁止清单

### 8.1 NEVER 直接修改 props

Props 是只读的单向数据流，**NEVER** 在子组件内直接赋值修改 props。

### 8.2 NEVER `v-if` 和 `v-for` 在同一元素上

Vue 3 中 `v-if` 优先级高于 `v-for`，二者同元素会导致 `v-if` 无法访问 `v-for` 作用域变量。

### 8.3 NEVER 使用未声明的 emit

在 `<script setup>` 中，**NEVER** 直接使用 `$emit` 而不通过 `defineEmits` 声明。

### 8.4 NEVER 异步组件无 `<Suspense>` 包裹

使用 `async setup()` 或顶层 `await` 的异步组件，**NEVER** 在没有 `<Suspense>` 包裹的情况下直接渲染。
