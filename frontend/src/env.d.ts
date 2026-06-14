declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

// 环境变量类型定义
interface ImportMetaEnv {
  readonly VITE_API_BASE?: string
  readonly VITE_STATIC_BASE?: string
  readonly VITE_USER_STATIC_BASE?: string
  readonly VITE_SENTRY_DSN?: string
  readonly VITE_SENTRY_ENVIRONMENT?: string
  readonly VITE_SENTRY_ENABLE_IN_DEV?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
