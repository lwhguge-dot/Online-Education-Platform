---
name: pinia-state-management
description: Pinia 3 / Setup Store / 持久化策略 / 反模式 — frontend state management standards.
---

# Pinia 状态管理规范

> 适用项目：`frontend/` — Pinia 3.0+ / Vue 3.5+ / TypeScript 5.9 strict
> 现有 Store：`auth.ts` · `toast.ts` · `confirm.ts`

---

## 1. Store 设计模式

### 1.1 Option Store vs Setup Store

```ts
// ❌ Option Store — 已过时，禁止使用
export const useCounterStore = defineStore('counter', {
  state: () => ({ count: 0 }),
  getters: { doubleCount: (state) => state.count * 2 },
  actions: { increment() { this.count++ } }
})

// ✅ Setup Store — 组合式 API，与 Vue 3 Composition API 完全一致
export const useCounterStore = defineStore('counter', () => {
  const count = ref(0)
  const doubleCount = computed(() => count.value * 2)
  function increment() { count.value++ }
  return { count, doubleCount, increment }
})
```

**理由**：Setup Store 与 `<script setup>` 语法一致，可复用 composables，TypeScript 类型推导无需额外泛型标注。

### 1.2 命名约定

```ts
// ✅ 正确：useXxxStore 导出函数 + xxx.ts 文件名
export const useAuthStore = defineStore('auth', () => { /* ... */ })

// ❌ 错误：不规范的导出名
export const authStore = defineStore('auth', () => { /* ... */ })
export const useAuth = defineStore('auth', () => { /* ... */ })
```

| 维度 | 约定 | 示例 |
|------|------|------|
| 导出函数名 | `useXxxStore` | `useAuthStore`、`useToastStore` |
| `defineStore` 第一参数 (id) | 文件名（不含 `.ts`） | `'auth'`、`'toast'`、`'confirm'` |
| 文件名 | `xxx.ts` | `auth.ts`、`toast.ts`、`confirm.ts` |

### 1.3 目录组织

```
src/stores/
├── auth.ts            ✅ 认证状态
├── toast.ts           ✅ Toast 消息队列
├── confirm.ts         ✅ 确认弹窗
├── student-course.ts  ✅ 按功能模块分文件
├── teacher-homework.ts
└── ui-sidebar.ts
```

### 1.4 Store ID 约定

Store ID 必须与文件名一致（不含扩展名），便于 Pinia devtools 调试定位源码。

---

## 2. Action / Getter 规范

### 2.1 职责边界

| 概念 | 应包含 | 不应包含 |
|------|--------|----------|
| **Action** | 异步操作、API 调用、状态变更的组合逻辑、错误处理 | 纯计算逻辑 |
| **Getter** | 派生状态、计算属性、过滤/排序逻辑 | API 调用、副作用、状态变更 |

### 2.2 Action 规范

Action 必须改变 state + 返回 Promise + 有错误处理，不能只返回数据不改变 state。

### 2.3 Getter 规范

Getter 必须是纯计算无副作用，不能调用 API 或修改 state。

### 2.4 模块级状态 vs Store 内状态

不共享的辅助变量放模块作用域（非 reactive），无需响应式开销。

---

## 3. 持久化策略

### 3.1 当前项目现状

项目**未使用** `pinia-plugin-persistedstate`，认证持久化通过 `src/services/request.ts` 的手动 sessionStorage 工具函数实现。

### 3.2 推荐：迁移到 pinia-plugin-persistedstate

```ts
// 声明式持久化，paths 白名单精确控制
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null)
  const user = ref<User | null>(null)
  const loading = ref(true)
  /* ... */
  return { token, user, loading }
}, {
  persist: {
    key: 'auth',
    storage: sessionStorage,          // 敏感信息用 sessionStorage
    paths: ['token', 'user'],         // 不持久化 loading
  }
})
```

### 3.3 持久化原则

| 原则 | 说明 |
|------|------|
| **paths 白名单** | 显式列出需持久化的字段，避免意外持久化临时状态 |
| **敏感信息用 sessionStorage** | token、密码等敏感数据用 sessionStorage |
| **非敏感偏好用 localStorage** | 主题、语言等用户偏好可用 localStorage |
| **不持久化非 JSON 类型** | Map、Set、Function、Symbol 等不可序列化类型 |

### 3.4 与现有代码的兼容

如果暂不引入持久化插件，保持现有的 `saveAuth` / `getAuth` / `clearAuth` 模式，通过统一工具函数读写 sessionStorage。

---

## 4. Pinia 反模式

### 4.1 Store 间循环依赖

允许 Store 单向依赖另一个 Store，但绝不形成回环。若出现双向通信需求，提取到 composable 或通过事件总线解耦。

### 4.2 直接修改 state 绕过 action

始终通过 action 修改 state，确保副作用（Sentry 同步、持久化、日志）被正确触发。

### 4.3 过度持久化

白名单只持久化必要字段，大列表用缓存层不持久化。

### 4.4 在组件外使用 store（SSR 不安全）

在 `.ts` 文件顶层（非 setup 上下文）直接调用 `useXxxStore()` 会导致 pinia 安装前执行报错。应在组件 setup / action / composable 内调用。

### 4.5 其他反模式速查

| 反模式 | 说明 |
|--------|------|
| `storeToRefs` 误用 | 用 `storeToRefs(store)` 解构 state/getter 保持响应式；action 直接解构 |
| 跨 Store 重复状态 | 同一份数据不在多个 Store 中重复维护，应从单一来源派生 |
| Action 无返回值 | 异步 Action 应返回 Promise，方便调用方 await 和链式处理 |
| 忘记 `$reset` | Setup Store 需手动实现 reset 方法 |
