const isDev = import.meta.env.DEV

function toError(err: unknown): Error {
  if (err instanceof Error) return err
  return new Error(String(err ?? 'unknown error'))
}

/**
 * 净化日志字符串，防止日志注入（换行/控制字符伪造日志条目）。
 */
function sanitizeForLog(value: unknown): string {
  if (value == null) return ''
  const str = typeof value === 'string' ? value : String(value)
  // 去除换行/回车/制表符等控制字符，截断长度
  const cleaned = str.replace(/[\r\n\t\x00-\x1F\x7F]/g, '_')
  return cleaned.length > 500 ? cleaned.slice(0, 500) + '...' : cleaned
}

let sentryReady = false
let SentryService: typeof import('./sentry').default | null = null

async function ensureSentry() {
  if (sentryReady) return SentryService
  try {
    const mod = await import('./sentry')
    SentryService = mod.default
    sentryReady = true
  } catch {
    sentryReady = true
  }
  return SentryService
}

export const logger = {
  error(message: string, err?: unknown): void {
    if (isDev) {
      console.error(sanitizeForLog(message), err ?? '')
    }
    ensureSentry().then((s) => {
      s?.captureException(toError(err), {
        level: 'error',
        extra: { message },
      })
    })
  },

  warn(message: string, data?: unknown): void {
    if (isDev) {
      console.warn(sanitizeForLog(message), data ?? '')
    }
  },

  info(message: string, data?: unknown): void {
    if (isDev) {
      console.log(sanitizeForLog(message), data ?? '')
    }
  },
}

export default logger
