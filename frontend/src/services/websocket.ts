type UnsubscribeFn = () => void

interface WsAuthMessage {
  type: 'AUTH'
  token: string
}

interface WsPingMessage {
  type: 'PING'
}

interface WsForceLogoutMessage {
  type: 'FORCE_LOGOUT'
  reason?: string
}

interface WsNotificationMessage {
  type: 'NOTIFICATION'
  [key: string]: unknown
}

interface WsAuthOkMessage {
  type: 'AUTH_OK'
}

interface WsAuthFailedMessage {
  type: 'AUTH_FAILED'
}

interface WsPongMessage {
  type: 'PONG'
}

type WsIncomingMessage =
  | WsForceLogoutMessage
  | WsNotificationMessage
  | WsAuthOkMessage
  | WsAuthFailedMessage
  | WsPongMessage

type ForceLogoutCallback = (reason?: string) => void
type NotificationCallback = (data: WsNotificationMessage) => void
type ConnectedCallback = () => void
type DisconnectedCallback = () => void

interface ListenerMap {
  onForceLogout: ForceLogoutCallback[]
  onNotification: NotificationCallback[]
  onConnected: ConnectedCallback[]
  onDisconnected: DisconnectedCallback[]
}

const apiBase: string = import.meta.env.VITE_API_BASE || ''
let wsOrigin = ''

if (typeof apiBase === 'string' && apiBase.startsWith('http')) {
  try {
    const u = new URL(apiBase)
    const wsProtocol = u.protocol === 'https:' ? 'wss:' : 'ws:'
    wsOrigin = `${wsProtocol}//${u.host}`
  } catch {
    wsOrigin = ''
  }
}

if (!wsOrigin) {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = window.location.host
  wsOrigin = `${protocol}//${host}`
}

const WS_BASE = `${wsOrigin}/ws/notification`

let ws: WebSocket | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let heartbeatTimer: ReturnType<typeof setInterval> | null = null
let lastErrorLoggedAt = 0
let manualDisconnect = false

const listeners: ListenerMap = {
  onForceLogout: [],
  onNotification: [],
  onConnected: [],
  onDisconnected: [],
}

const startHeartbeat = (): void => {
  heartbeatTimer = setInterval(() => {
    if (ws && ws.readyState === WebSocket.OPEN) {
      const ping: WsPingMessage = { type: 'PING' }
      ws.send(JSON.stringify(ping))
    }
  }, 30000)
}

const stopHeartbeat = (): void => {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
}

const handleMessage = (data: WsIncomingMessage): void => {
  switch (data.type) {
    case 'FORCE_LOGOUT':
      listeners.onForceLogout.forEach(fn => fn(data.reason))
      break
    case 'NOTIFICATION':
      listeners.onNotification.forEach(fn => fn(data))
      break
    case 'AUTH_OK':
      console.log('WebSocket 认证成功')
      break
    case 'AUTH_FAILED':
      console.warn('WebSocket 认证失败')
      disconnectWebSocket()
      break
    case 'PONG':
      break
    default:
      console.log('未知消息类型:', (data as WsIncomingMessage).type)
  }
}

export const connectWebSocket = (): void => {
  if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
    return
  }

  const token = sessionStorage.getItem('token')
  if (!token) {
    console.warn('WebSocket 连接失败：缺少登录 token')
    return
  }

  try {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }

    manualDisconnect = false
    ws = new WebSocket(WS_BASE)

    ws.onopen = () => {
      console.log('WebSocket 连接成功')

      const authMsg: WsAuthMessage = {
        type: 'AUTH',
        token,
      }
      ws!.send(JSON.stringify(authMsg))

      startHeartbeat()
      listeners.onConnected.forEach(fn => fn())
    }

    ws.onmessage = (event: MessageEvent) => {
      try {
        const data = JSON.parse(event.data as string) as WsIncomingMessage
        handleMessage(data)
      } catch (e) {
        console.error('解析 WebSocket 消息失败:', e)
      }
    }

    ws.onclose = () => {
      console.log('WebSocket 连接关闭')
      stopHeartbeat()
      listeners.onDisconnected.forEach(fn => fn())

      if (!manualDisconnect) {
        reconnectTimer = setTimeout(() => {
          if (sessionStorage.getItem('token')) {
            connectWebSocket()
          }
        }, 5000)
      }
    }

    ws.onerror = (_error: Event) => {
      const now = Date.now()
      if (now - lastErrorLoggedAt > 30000) {
        console.warn('WebSocket 连接异常:', _error)
        lastErrorLoggedAt = now
      }
    }
  } catch (e) {
    console.error('WebSocket 连接失败:', e)
  }
}

export const disconnectWebSocket = (): void => {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }

  stopHeartbeat()

  if (ws) {
    manualDisconnect = true
    ws.close()
    ws = null
  }
}

export const onForceLogout = (callback: ForceLogoutCallback): UnsubscribeFn => {
  listeners.onForceLogout.push(callback)
  return () => {
    const index = listeners.onForceLogout.indexOf(callback)
    if (index > -1) {
      listeners.onForceLogout.splice(index, 1)
    }
  }
}

export const onNotification = (callback: NotificationCallback): UnsubscribeFn => {
  listeners.onNotification.push(callback)
  return () => {
    const index = listeners.onNotification.indexOf(callback)
    if (index > -1) {
      listeners.onNotification.splice(index, 1)
    }
  }
}

export const onConnected = (callback: ConnectedCallback): UnsubscribeFn => {
  listeners.onConnected.push(callback)
  return () => {
    const index = listeners.onConnected.indexOf(callback)
    if (index > -1) {
      listeners.onConnected.splice(index, 1)
    }
  }
}

export const onDisconnected = (callback: DisconnectedCallback): UnsubscribeFn => {
  listeners.onDisconnected.push(callback)
  return () => {
    const index = listeners.onDisconnected.indexOf(callback)
    if (index > -1) {
      listeners.onDisconnected.splice(index, 1)
    }
  }
}

export default {
  connectWebSocket,
  disconnectWebSocket,
  onForceLogout,
  onNotification,
  onConnected,
  onDisconnected,
}