import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

interface WebSocketServiceModule {
  connectWebSocket: () => void
  disconnectWebSocket: () => void
}

interface MockWindowLike {
  location: {
    protocol: string
    host: string
  }
  sessionStorage: Storage
  localStorage: Storage
}

const createStorage = (): Storage => {
  const map = new Map<string, string>()

  return {
    get length() {
      return map.size
    },
    clear() {
      map.clear()
    },
    getItem(key: string) {
      return map.has(key) ? (map.get(key) ?? null) : null
    },
    key(index: number) {
      return Array.from(map.keys())[index] ?? null
    },
    removeItem(key: string) {
      map.delete(key)
    },
    setItem(key: string, value: string) {
      map.set(key, String(value))
    },
  }
}

class MockWebSocket {
  static CONNECTING = 0
  static OPEN = 1
  static CLOSING = 2
  static CLOSED = 3

  static instances: MockWebSocket[] = []

  readonly url: string
  readyState = MockWebSocket.CONNECTING
  onopen: ((event: Event) => void) | null = null
  onclose: ((event: CloseEvent) => void) | null = null
  onerror: ((event: Event) => void) | null = null
  onmessage: ((event: MessageEvent<string>) => void) | null = null
  sentMessages: string[] = []
  closeCallCount = 0

  constructor(url: string) {
    this.url = url
    MockWebSocket.instances.push(this)
  }

  send(data: string): void {
    this.sentMessages.push(data)
  }

  close(): void {
    this.closeCallCount += 1
    this.readyState = MockWebSocket.CLOSED
  }

  // 测试辅助：模拟连接建立
  triggerOpen(): void {
    this.readyState = MockWebSocket.OPEN
    this.onopen?.({ type: 'open' } as Event)
  }

  // 测试辅助：模拟连接断开
  triggerClose(): void {
    this.readyState = MockWebSocket.CLOSED
    this.onclose?.({ type: 'close' } as CloseEvent)
  }
}

const loadService = async (): Promise<WebSocketServiceModule> => {
  // 历史模块当前仍为 JS 文件，这里在测试侧做显式类型断言以通过严格类型检查
  // @ts-expect-error websocket.js 暂无独立 d.ts 声明
  return (await import('./websocket.js')) as unknown as WebSocketServiceModule
}

describe('websocket service', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.resetModules()
    MockWebSocket.instances = []

    const sessionStorage = createStorage()
    const localStorage = createStorage()
    const windowLike: MockWindowLike = {
      location: {
        protocol: 'http:',
        host: 'localhost:5173',
      },
      sessionStorage,
      localStorage,
    }

    vi.stubGlobal('window', windowLike)
    vi.stubGlobal('sessionStorage', sessionStorage)
    vi.stubGlobal('localStorage', localStorage)
    vi.stubGlobal('WebSocket', MockWebSocket)
  })

  afterEach(() => {
    vi.clearAllTimers()
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('握手 URL 不应携带 token，连接后应发送 AUTH 消息', async () => {
    sessionStorage.setItem('token', 'token-123')
    const service = await loadService()

    service.connectWebSocket()

    expect(MockWebSocket.instances).toHaveLength(1)
    const socket = MockWebSocket.instances[0]
    expect(socket).toBeDefined()
    if (!socket) {
      throw new Error('WebSocket 实例未创建')
    }

    expect(socket.url).toBe('ws://localhost:5173/ws/notification')
    expect(socket.url.includes('token=')).toBe(false)

    socket.triggerOpen()
    expect(socket.sentMessages).toHaveLength(1)
    const authPayload = JSON.parse(socket.sentMessages[0] ?? '{}') as { type?: string; token?: string }
    expect(authPayload).toEqual({
      type: 'AUTH',
      token: 'token-123',
    })
  })

  it('主动断开后不应触发自动重连', async () => {
    sessionStorage.setItem('token', 'token-123')
    const service = await loadService()

    service.connectWebSocket()
    const socket = MockWebSocket.instances[0]
    expect(socket).toBeDefined()
    if (!socket) {
      throw new Error('WebSocket 实例未创建')
    }

    service.disconnectWebSocket()
    socket.triggerClose()
    vi.advanceTimersByTime(5000)

    expect(socket.closeCallCount).toBe(1)
    expect(MockWebSocket.instances).toHaveLength(1)
  })

  it('异常断开时在会话仍有效的情况下应自动重连', async () => {
    sessionStorage.setItem('token', 'token-123')
    const service = await loadService()

    service.connectWebSocket()
    const socket = MockWebSocket.instances[0]
    expect(socket).toBeDefined()
    if (!socket) {
      throw new Error('WebSocket 实例未创建')
    }

    socket.triggerClose()
    vi.advanceTimersByTime(5000)

    expect(MockWebSocket.instances).toHaveLength(2)
  })

  it('异常断开后若会话失效则不应重连', async () => {
    sessionStorage.setItem('token', 'token-123')
    const service = await loadService()

    service.connectWebSocket()
    const socket = MockWebSocket.instances[0]
    expect(socket).toBeDefined()
    if (!socket) {
      throw new Error('WebSocket 实例未创建')
    }

    socket.triggerClose()
    sessionStorage.removeItem('token')
    vi.advanceTimersByTime(5000)

    expect(MockWebSocket.instances).toHaveLength(1)
  })
})
