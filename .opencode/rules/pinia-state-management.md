# Pinia 状态管理规范

> 适用项目：`frontend/` — Pinia 3.0+ / Vue 3.5+ / TypeScript 5.9 strict
> 现有 Store：[auth.ts](file:///c:/Users/XuShuang/Desktop/demo/frontend/src/stores/auth.ts) · [toast.ts](file:///c:/Users/XuShuang/Desktop/demo/frontend/src/stores/toast.ts) · [confirm.ts](file:///c:/Users/XuShuang/Desktop/demo/frontend/src/stores/confirm.ts)

---

## 1. Store 设计模式

### 1.1 Option Store vs Setup Store

```ts
// ❌ Option Store — 已过时，禁止使用
export const useCounterStore = defineStore('counter', {
  state: () => ({ count: 0 }),
  getters: {
    doubleCount: (state) => state.count * 2
  },
  actions: {
    increment() { this.count++ }
  }
})

// ✅ Setup Store — 组合式 API，与 Vue 3 Composition API 完全一致
export const useCounterStore = defineStore('counter', () => {
  const count = ref(0)
  const doubleCount = computed(() => count.value * 2)
  function increment() { count.value++ }
  return { count, doubleCount, increment }
})
```

**理由**：Setup Store 与 `<script setup>` 语法一致，可复用 composables，TypeScript 类型推导无需额外泛型标注。本项目 3 个 Store 全部使用 Setup Store。

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

```ts
// ❌ 错误：Store ID 与文件名不一致
// 文件 student-course.ts
export const useStudentCourseStore = defineStore('studentCourse', () => { /* ... */ })
// ID 应为 'student-course'

// ✅ 正确：ID 与文件名一致
// 文件 student-course.ts
export const useStudentCourseStore = defineStore('student-course', () => { /* ... */ })
```

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

```ts
// ❌ 错误：所有 Store 堆在一个文件
// src/stores/index.ts
export const useAuthStore = defineStore('auth', () => { /* ... */ })
export const useToastStore = defineStore('toast', () => { /* ... */ })
export const useConfirmStore = defineStore('confirm', () => { /* ... */ })
export const useCourseStore = defineStore('course', () => { /* ... */ })

// ✅ 正确：一个功能模块一个文件
// src/stores/auth.ts
export const useAuthStore = defineStore('auth', () => { /* ... */ })
```

### 1.4 Store ID 约定

```ts
// ✅ 正确：ID 就是文件名（不含扩展名）
// src/stores/student-course.ts
export const useStudentCourseStore = defineStore('student-course', () => { /* ... */ })

// ❌ 错误：自定义随机 ID
export const useStudentCourseStore = defineStore('studentCourseStore-v2-final', () => { /* ... */ })
```

**理由**：Pinia devtools 中 Store ID 用于调试面板展示，与文件名一致可快速定位源码。

---

## 2. Action / Getter 规范

### 2.1 职责边界

| 概念 | 应包含 | 不应包含 |
|------|--------|----------|
| **Action** | 异步操作、API 调用、状态变更的组合逻辑、错误处理 | 纯计算逻辑 |
| **Getter** | 派生状态、计算属性、过滤/排序逻辑 | API 调用、副作用、状态变更 |

### 2.2 Action 规范

```ts
// ✅ 正确：Action 改变 state + 返回 Promise + 错误处理
export const useCourseStore = defineStore('course', () => {
  const courses = ref<Course[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchCourses(): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const res = await courseAPI.list()
      courses.value = res.data
    } catch (e) {
      error.value = e instanceof Error ? e.message : '加载课程失败'
    } finally {
      loading.value = false
    }
  }

  return { courses, loading, error, fetchCourses }
})

// ❌ 错误：Action 只返回数据不改变 state
async function fetchCourses(): Promise<Course[]> {
  const res = await courseAPI.list()
  return res.data
  // 调用方需要手动管理 state，职责分散
}

// ❌ 错误：无错误处理直接抛异常
async function fetchCourses(): Promise<void> {
  const res = await courseAPI.list()
  courses.value = res.data
  // API 失败 → 未捕获异常 → 组件崩溃
}
```

**现有代码参考**：[auth.ts](file:///c:/Users/XuShuang/Desktop/demo/frontend/src/stores/auth.ts#L30-L46) — `login()` 同步更新 token、user state 并同步 Sentry 上下文。

### 2.3 Getter 规范

```ts
// ✅ 正确：纯计算无副作用
export const useCourseStore = defineStore('course', () => {
  const courses = ref<Course[]>([])
  const activeFilter = ref<'all' | 'published' | 'draft'>('all')

  const filteredCourses = computed(() => {
    if (activeFilter.value === 'all') return courses.value
    return courses.value.filter(c => c.status === activeFilter.value.toUpperCase())
  })

  const publishedCount = computed(() =>
    courses.value.filter(c => c.status === 'PUBLISHED').length
  )

  return { courses, activeFilter, filteredCourses, publishedCount }
})

// ❌ 错误：Getter 中调用 API
const courseStats = computed(async () => {
  const res = await statsAPI.getCourseStats()
  return res.data
  // ❌ computed 不接受异步回调，且不应有副作用
})

// ❌ 错误：Getter 中修改 state
const sortedCourses = computed(() => {
  courses.value.sort((a, b) => a.title.localeCompare(b.title))
  // ❌ .sort() 就地修改原数组，改变了 state
  return courses.value
})
```

**现有代码参考**：[auth.ts](file:///c:/Users/XuShuang/Desktop/demo/frontend/src/stores/auth.ts#L13) — `isAuthenticated` 是纯计算 Getter，基于 `token` 和 `user` 派生。

### 2.4 模块级状态 vs Store 内状态

```ts
// ✅ 正确：不共享的辅助变量放模块作用域（非 reactive）
// src/stores/toast.ts
export const useToastStore = defineStore('toast', () => {
  const toasts = ref<Toast[]>([])
  let idCounter = 0  // ✅ 模块级，不暴露给外部，无需响应式

  function add(message: string, type: ToastType = 'info', duration = 3000) {
    const id = idCounter++
    toasts.value.push({ id, message, type })
    if (duration > 0) setTimeout(() => remove(id), duration)
  }

  return { toasts, add }
})

// ❌ 错误：idCounter 放入 ref（无必要响应式开销）
const idCounter = ref(0)
```

---

## 3. 持久化策略

### 3.1 当前项目现状

项目**未使用** `pinia-plugin-persistedstate`，认证持久化通过 `src/services/request.ts` 的手动 sessionStorage 工具函数实现：

```ts
// src/services/request.ts 提供
saveAuth(token, user)   // → sessionStorage.setItem('token', token)
                         //   sessionStorage.setItem('user', JSON.stringify(user))
getAuth()                // → { token, user }
clearAuth()              // → sessionStorage.removeItem('token')
                         //   sessionStorage.removeItem('user')
```

### 3.2 推荐：迁移到 pinia-plugin-persistedstate

```bash
npm install pinia-plugin-persistedstate
```

```ts
// src/main.ts — 插件注册
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'

const pinia = createPinia()
pinia.use(piniaPluginPersistedstate)
app.use(pinia)
```

```ts
// ✅ 正确：声明式持久化，paths 白名单精确控制
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

// ❌ 错误：token 持久化到 localStorage（XSS 可读取）
// persist: { storage: localStorage, paths: ['token', 'user'] }

// ❌ 错误：手动在 action 中写 localStorage
function login(token: string, user: User) {
  localStorage.setItem('token', token)  // ❌ 手动 setItem，破坏声明式
  localStorage.setItem('user', JSON.stringify(user))
  token.value = token
  user.value = user
}

// ❌ 错误：过度持久化 — 整个 store 全部持久化
// persist: true
```

### 3.3 持久化原则

| 原则 | 说明 |
|------|------|
| **paths 白名单** | 显式列出需持久化的字段，避免意外持久化临时状态（loading、error 等） |
| **敏感信息用 sessionStorage** | token、密码等敏感数据用 sessionStorage（关闭浏览器即清除） |
| **非敏感偏好用 localStorage** | 主题、语言等用户偏好可用 localStorage |
| **不持久化非 JSON 类型** | Map、Set、Function、Symbol 等不可序列化类型不放入 persist paths |

### 3.4 与现有代码的兼容

如果暂不引入 `pinia-plugin-persistedstate`，保持现有的 `saveAuth` / `getAuth` / `clearAuth` 模式：

```ts
// ✅ 当前正确做法（auth.ts）
import { getAuth, saveAuth, clearAuth } from '../services/api'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null)
  const user = ref<User | null>(null)

  function init() {
    const auth = getAuth()
    if (auth.token && auth.user) {
      token.value = auth.token
      user.value = auth.user
    }
  }

  function login(newToken: string, newUser: User) {
    saveAuth(newToken, newUser)       // ✅ 通过统一工具函数写 sessionStorage
    token.value = newToken
    user.value = newUser
  }

  function logout() {
    clearAuth()                       // ✅ 通过统一工具函数清除
    token.value = null
    user.value = null
  }

  return { token, user, init, login, logout }
})
```

---

## 4. Pinia 反模式

### 4.1 Store 间循环依赖

```ts
// ❌ 禁止：Store A 引用 Store B，Store B 引用 Store A
// src/stores/user.ts
import { useAuthStore } from './auth'

export const useUserStore = defineStore('user', () => {
  const authStore = useAuthStore()       // A 引用 B

  async function fetchProfile() {
    if (!authStore.token) return          // 运行时可能未初始化
    /* ... */
  }
  return { fetchProfile }
})

// src/stores/auth.ts
import { useUserStore } from './user'

export const useAuthStore = defineStore('auth', () => {
  const userStore = useUserStore()       // B 引用 A → 循环依赖！

  function logout() {
    clearAuth()
    token.value = null
    userStore.resetProfile()             // 可能 undefined
  }
  return { logout }
})

// ✅ 正确：提取共享逻辑到 composable 或 service，Store 只依赖单向
// src/stores/auth.ts — 只依赖 service，不依赖其他 store
export const useAuthStore = defineStore('auth', () => {
  function logout() { /* ... */ }
  return { logout }
})

// src/stores/user.ts — 依赖 authStore 是安全的单向依赖
export const useUserStore = defineStore('user', () => {
  const authStore = useAuthStore()       // ✅ 单向，无回环
  async function fetchProfile() { /* ... */ }
  return { fetchProfile }
})
```

**原则**：允许 Store 单向依赖另一个 Store，但绝不形成回环。若出现双向通信需求，提取到 composable 或通过事件总线解耦。

### 4.2 直接修改 state 绕过 action

```ts
// ❌ 禁止：组件内直接修改 store state
const authStore = useAuthStore()
authStore.token = 'new-token'        // ❌ 绕过 login() action
authStore.user = newUserData         // ❌ 绕过 updateUser() action

// ✅ 正确：始终通过 action 修改 state
const authStore = useAuthStore()
authStore.login(token, user)         // ✅ 通过 action，确保 Sentry 同步
authStore.updateUser({ email })      // ✅ 通过 action
```

**理由**：Action 不仅修改 state，还承载副作用（Sentry 同步、持久化、日志）。直接修改 state 会遗漏这些副作用，导致状态不一致。

### 4.3 过度持久化

```ts
// ❌ 禁止：整个 Store 持久化
// persist: true — 会把 loading、error、临时 UI 状态全部写入 storage

// ❌ 禁止：持久化大体积数据
const notifications = ref<Notification[]>([])  // 可能有数千条
// persist.paths: ['notifications'] — 大量数据频繁序列化

// ✅ 正确：白名单只持久化必要字段
// persist: { paths: ['token', 'user'] }

// ✅ 正确：大列表用缓存层（TanStack Query / 手动内存缓存），不持久化
const notifications = ref<Notification[]>([])  // 不放入 persist.paths
```

### 4.4 在组件外使用 store（SSR 不安全）

```ts
// ❌ 禁止：在 .ts 文件顶层（非 setup 上下文）直接调用 useXxxStore()
// src/services/api.ts
import { useAuthStore } from '../stores/auth'
const authStore = useAuthStore()           // ❌ 在 pinia 安装前执行，报错
// SSR 下多个请求共享同一 authStore 实例 → 数据泄露

// ✅ 正确：在组件 setup / action / composable 内调用
// src/components/ProfileCard.vue
<script setup>
const authStore = useAuthStore()           // ✅ 组件 setup 上下文中
</script>

// ✅ 正确：需要 token 时从 service 层获取
// src/services/request.ts
function getToken(): string | null {
  return sessionStorage.getItem('token')   // ✅ 直接读 storage，不依赖 pinia
}
```

### 4.5 其他反模式速查

| 反模式 | 说明 |
|--------|------|
| `storeToRefs` 误用 | 用 `storeToRefs(store)` 解构 state/getter 保持响应式；action 直接解构 `const { login } = useAuthStore()` |
| 跨 Store 重复状态 | 同一份数据不在多个 Store 中重复维护，应从单一来源派生 |
| Action 无返回值 | 异步 Action 应返回 Promise，方便调用方 await 和链式处理 |
| 忘记 `$reset` | Option Store 有 `$reset()`，Setup Store 需手动实现 reset 方法 |

---

## 5. 中文执行层

### 5.1 触发条件

以下任一场景命中时，必须读取本 Rule 并执行对应规范：

- 新建 Pinia Store 文件（`src/stores/xxx.ts`）
- 修改现有 Store 的 state/getter/action
- 添加/调整 Store 持久化策略
- 引入 `pinia-plugin-persistedstate` 或替换持久化方案
- 审查代码发现 Store 间循环依赖

### 5.2 前置条件

| # | 条件 | 验证方式 |
|---|------|----------|
| 1 | Pinia 版本 ≥ 3.0 | `npm list pinia` |
| 2 | TypeScript strict 模式开启 | 检查 `tsconfig.json` `"strict": true` |
| 3 | 目标 Store 文件位于 `src/stores/` | 目录存在 |
| 4 | 若涉及持久化，确认插件是否已安装 | `npm list pinia-plugin-persistedstate` |
| 5 | 确认 vue3-frontend-standards.md 第 3 节已读取（状态管理基础规范） | 文件存在 |

### 5.3 执行步骤

**新建 Store**：

1. 在 `src/stores/` 创建 `xxx.ts`，文件名即 Store ID
2. 使用 `defineStore('xxx', () => { })` Setup Store 语法
3. State 用 `ref()` 定义，标注 `Ref<T>` 类型
4. Getter 用 `computed()` 定义，标注 `ComputedRef<T>` 类型
5. Action 用 `function` 声明，异步 Action 返回 `Promise<void>` 或 `Promise<T>`
6. `return { state1, state2, getter1, action1, action2 }`（显式导出，不遗漏）
7. 如需持久化，添加 `persist` 配置（`paths` 白名单）
8. 模板中使用：`const xxxStore = useXxxStore()`

**修改现有 Store**：

1. 检查是否有其他 Store 依赖当前 Store（避免循环依赖）
2. 新增 state 考虑是否需要加入 persist.paths
3. 新增 action 确保有错误处理（try-catch + 错误状态）
4. 修改后运行 `npm run type-check` + `npm run lint`

**引入持久化插件**：

1. `npm install pinia-plugin-persistedstate`
2. `main.ts` 中 `pinia.use(piniaPluginPersistedstate)`
3. 逐个 Store 添加 `persist` 配置
4. 移除旧的手动 storage 代码（`saveAuth`/`getAuth`/`clearAuth` 调用处）
5. 测试：登录 → 刷新页面 → 状态恢复 → 登出 → 状态清除

### 5.4 完成证据

- [ ] 新建/修改的 Store 通过 `npm run type-check` 无类型错误
- [ ] 新建/修改的 Store 通过 `npm run lint` 无 ESLint 错误
- [ ] Store ID 与文件名一致
- [ ] 导出函数名为 `useXxxStore` 格式
- [ ] 如有持久化：`persist` 配置含 `paths` 白名单，敏感数据用 `sessionStorage`
- [ ] 无直接修改 state 绕过 action 的代码
- [ ] 无 Store 间循环依赖
- [ ] 现有功能回归正常（登录/登出/弹窗/Toast 等涉及 Store 的功能）
- [ ] 刷新页面后持久化状态正确恢复

### 5.5 失败回退

| 失败场景 | 回退方案 |
|----------|----------|
| 类型错误 | 检查 `Ref<T>` / `ComputedRef<T>` 标注，确保 `return` 中不遗漏字段 |
| 循环依赖 | 提取共享逻辑到 composable 或 service；若必须双向，用事件总线解耦 |
| 持久化不生效 | 确认插件在 `createPinia()` 后 `.use()` 注册；确认 `paths` 字段名与 `return` 中一致 |
| 持久化恢复数据异常 | 使用 `beforeRestore` / `afterRestore` 钩子做数据校验和迁移；或临时迁移到新版 schema |
| 构建失败 | 回滚本次变更，回到已知良好 commit，重新分步实施 |