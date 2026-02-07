import * as Sentry from '@sentry/vue'

/**
 * Sentry 错误监控工具类
 */
export class SentryService {
  /**
   * 捕获异常
   */
  static captureException(error: Error, context?: {
    level?: 'fatal' | 'error' | 'warning' | 'info' | 'debug'
    tags?: Record<string, string>
    extra?: Record<string, any>
  }) {
    Sentry.captureException(error, {
      level: context?.level || 'error',
      tags: context?.tags,
      extra: context?.extra,
    })
  }

  /**
   * 捕获消息
   */
  static captureMessage(message: string, level: 'fatal' | 'error' | 'warning' | 'info' | 'debug' = 'info') {
    Sentry.captureMessage(message, level)
  }

  /**
   * 设置用户上下文
   */
  static setUser(user: {
    id: string | number
    username?: string
    email?: string
  } | null) {
    if (user) {
      Sentry.setUser({
        id: String(user.id),
        username: user.username,
        email: user.email,
      })
    } else {
      Sentry.setUser(null)
    }
  }

  /**
   * 添加面包屑（用户操作记录）
   */
  static addBreadcrumb(breadcrumb: {
    message: string
    category?: string
    level?: 'fatal' | 'error' | 'warning' | 'info' | 'debug'
    data?: Record<string, any>
  }) {
    Sentry.addBreadcrumb({
      message: breadcrumb.message,
      category: breadcrumb.category || 'user-action',
      level: breadcrumb.level || 'info',
      data: breadcrumb.data,
    })
  }

  /**
   * 设置标签
   */
  static setTag(key: string, value: string) {
    Sentry.setTag(key, value)
  }

  /**
   * 设置上下文
   */
  static setContext(name: string, context: Record<string, any>) {
    Sentry.setContext(name, context)
  }
}

export default SentryService
