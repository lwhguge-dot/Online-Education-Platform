const isDev = import.meta.env.DEV

function toError(err: unknown): Error {
  if (err instanceof Error) return err
  return new Error(String(err ?? 'unknown error'))
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
      console.error(message, err ?? '')
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
      console.warn(message, data ?? '')
    }
  },

  info(message: string, data?: unknown): void {
    if (isDev) {
      console.log(message, data ?? '')
    }
  },
}

export default logger
