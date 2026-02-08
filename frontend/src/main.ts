import { createApp } from 'vue'
import { createPinia } from 'pinia'
import * as Sentry from '@sentry/vue'
import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'
import './assets/main.css'
import './assets/animations.css'

const app = createApp(App)
const pinia = createPinia()

// 初始化 Sentry（在其他插件之前）
const sentryEnabled = Boolean(import.meta.env.VITE_SENTRY_DSN) && (
  import.meta.env.MODE === 'production' || import.meta.env.VITE_SENTRY_ENABLE_IN_DEV === 'true'
)

if (sentryEnabled) {
  Sentry.init({
    app,
    dsn: import.meta.env.VITE_SENTRY_DSN,
    environment: import.meta.env.VITE_SENTRY_ENVIRONMENT || 'development',

    integrations: [
      Sentry.browserTracingIntegration({
        router,
      }),
      Sentry.replayIntegration(),
    ],
    // 性能监控采样率（开发环境 100%，生产环境建议 0.1-0.2）
    tracesSampleRate: import.meta.env.MODE === 'production' ? 0.1 : 1.0,

    // Session Replay 采样率
    replaysSessionSampleRate: 0.1,
    replaysOnErrorSampleRate: 1.0,

    // 错误过滤
    beforeSend(event, hint) {
      // 过滤掉某些不重要的错误
      if (event.exception) {
        const error = hint.originalException as Error
        // 忽略网络错误
        if (error && error.message && error.message.includes('Network Error')) {
          return null
        }
      }
      return event
    },

    // 忽略的错误
    ignoreErrors: [
      'ResizeObserver loop limit exceeded',
      'Non-Error promise rejection captured',
    ],
  })
}

app.use(pinia)
app.use(router)

// 初始化认证状态
const authStore = useAuthStore()
authStore.init()

app.mount('#app')
