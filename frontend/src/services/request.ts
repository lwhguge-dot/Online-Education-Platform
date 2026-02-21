/**
 * Core Request Service
 * Provides unified HTTP request encapsulation, authentication management, and error handling.
 */

import type { Result, User } from '../types/api'
import { useToastStore } from '../stores/toast'
import SentryService from '../utils/sentry'

// Environment configuration
export const API_BASE = import.meta.env.VITE_API_BASE || '/api'
export const STATIC_BASE = import.meta.env.VITE_STATIC_BASE || ''
export const USER_STATIC_BASE = import.meta.env.VITE_USER_STATIC_BASE || ''

// ==================== Type Definitions ====================

interface CacheEntry<T = any> {
    data: T
    timestamp: number
}

export interface RequestOptions extends RequestInit {
    method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
}

type FriendlyErrorMessages = Record<string, string>

interface RequestError extends Error {
    handledByToast?: boolean
    skipSentry?: boolean
}

export interface BlobResponse {
    blob: Blob
    filename: string | null
    response: Response
}

// ========== Simple Memory Cache ==========
const cache = new Map<string, CacheEntry>()
const CACHE_DURATION = 30000 // 30 seconds

// Clear cache
export const clearCache = (): void => cache.clear()

// Handle image URL
export const getImageUrl = (path: string | undefined | null): string => {
    if (!path) return ''
    if (path.startsWith('http')) return path
    // OSS resources go through gateway
    if (path.startsWith('/oss/')) {
        return path
    }
    // User avatars
    if (path.includes('/avatars/')) {
        return USER_STATIC_BASE + path
    }
    return STATIC_BASE + path
}

// Prevent duplicate force logout
let isForceLoggingOut = false

// Force logout
const forceLogout = (message = '您的账号已被禁用，请重新登录'): void => {
    if (isForceLoggingOut) {
        return
    }
    isForceLoggingOut = true

    stopHeartbeat()

    sessionStorage.removeItem('token')
    sessionStorage.removeItem('user')
    const toast = useToastStore()
    toast.error(message)
    setTimeout(() => {
        window.location.href = '/login'
    }, 1000)
}

// Friendly error messages
const friendlyErrorMessages: FriendlyErrorMessages = {
    'database': '数据处理异常，请稍后重试',
    'SQL': '数据处理异常，请稍后重试',
    'Table': '系统配置异常，请联系管理员',
    'Column': '系统配置异常，请联系管理员',
    'doesn\'t exist': '系统配置异常，请联系管理员',
    'Network Error': '网络连接失败，请检查网络后重试',
    'Failed to fetch': '无法连接服务器，请稍后重试',
    'ECONNREFUSED': '无法连接服务器，请稍后重试',
    'Connection refused': '服务器连接失败，请稍后重试',
    'Internal Server Error': '服务器繁忙，请稍后重试',
    '500': '服务器繁忙，请稍后重试',
    '502': '服务器维护中，请稍后重试',
    '503': '服务暂时不可用，请稍后重试',
    '504': '服务器响应超时，请稍后重试',
    'timeout': '请求超时，请稍后重试',
}

const getFriendlyMessage = (errorMsg: string | undefined): string => {
    if (!errorMsg) return '操作失败，请稍后重试'

    for (const [key, friendly] of Object.entries(friendlyErrorMessages)) {
        if (errorMsg.toLowerCase().includes(key.toLowerCase())) {
            return friendly
        }
    }

    if (/exception|error|sql|null|undefined|cannot|failed/i.test(errorMsg)) {
        return '操作失败，请稍后重试'
    }

    return errorMsg
}

// Pending requests to prevent conflicts
const pendingRequests = new Set<string>()

const getErrorMessage = (error: unknown): string => {
    if (error instanceof Error) {
        return error.message
    }
    return String(error ?? '未知错误')
}

// 统一把不同形态的 headers 转成普通对象，方便后续判断和合并
const normalizeHeaders = (headers?: HeadersInit): Record<string, string> => {
    if (!headers) {
        return {}
    }

    if (headers instanceof Headers) {
        const normalized: Record<string, string> = {}
        headers.forEach((value, key) => {
            normalized[key] = value
        })
        return normalized
    }

    if (Array.isArray(headers)) {
        return headers.reduce((acc, [key, value]) => {
            acc[key] = value
            return acc
        }, {} as Record<string, string>)
    }

    return { ...headers }
}

const hasHeaderIgnoreCase = (headers: Record<string, string>, headerName: string): boolean => {
    const target = headerName.toLowerCase()
    return Object.keys(headers).some((key) => key.toLowerCase() === target)
}

const shouldTrackPendingRequest = (method?: string): boolean => !!method && method !== 'GET'

// 仅在存在请求体时才自动补 JSON Content-Type，避免 GET 等请求触发不必要预检
const shouldAutoSetJsonContentType = (body: BodyInit | null | undefined): boolean => {
    if (body === undefined || body === null) {
        return false
    }
    return !(body instanceof FormData) && !(body instanceof URLSearchParams)
}

const createHandledError = (message: string, skipSentry = false): RequestError => {
    const error: RequestError = new Error(message)
    error.handledByToast = true
    error.skipSentry = skipSentry
    return error
}

const parseResultFromResponse = async <T = any>(response: Response): Promise<Result<T>> => {
    const rawText = await response.text()
    if (!rawText) {
        const normalizedCode = response.status === 204 ? 200 : response.status
        return { code: normalizedCode, message: response.statusText || '', data: null as any }
    }

    try {
        return JSON.parse(rawText) as Result<T>
    } catch {
        return { code: response.status, message: '响应解析失败', data: null as any }
    }
}

const extractResponseMessage = async (response: Response): Promise<string> => {
    const rawText = await response.clone().text()
    if (!rawText) {
        return response.statusText || `请求失败（${response.status}）`
    }

    try {
        const parsed = JSON.parse(rawText) as Partial<Result<unknown>>
        return parsed.message || response.statusText || rawText
    } catch {
        return rawText
    }
}

const extractFilenameFromDisposition = (disposition: string | null): string | null => {
    if (!disposition) {
        return null
    }

    const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i)
    if (utf8Match?.[1]) {
        try {
            return decodeURIComponent(utf8Match[1])
        } catch {
            return utf8Match[1]
        }
    }

    const basicMatch = disposition.match(/filename="?([^";]+)"?/i)
    return basicMatch?.[1] || null
}

const shouldForceLogoutOnForbidden = (message?: string): boolean => {
    if (!message) {
        return false
    }

    const lowerMessage = message.toLowerCase()
    return message.includes('账号已被禁用')
        || message.includes('账户已被禁用')
        || (lowerMessage.includes('token') && (
            message.includes('失效')
            || message.includes('过期')
            || message.includes('无效')
            || message.includes('重新登录')
        ))
}

// Auth Helpers
export const saveAuth = (token: string, user: User): void => {
    sessionStorage.setItem('token', token)
    sessionStorage.setItem('user', JSON.stringify(user))
}

export const getAuth = (): { token: string | null; user: User | null } => {
    const token = sessionStorage.getItem('token')
    const userStr = sessionStorage.getItem('user')
    let user: User | null = null

    if (userStr) {
        try {
            user = JSON.parse(userStr)
        } catch {
            sessionStorage.removeItem('user')
        }
    }

    return {
        token,
        user,
    }
}

export const clearAuth = (): void => {
    sessionStorage.removeItem('token')
    sessionStorage.removeItem('user')
}

const getSessionUserId = (): number | null => {
    const userStr = sessionStorage.getItem('user')
    if (!userStr) {
        return null
    }

    try {
        const user = JSON.parse(userStr)
        const rawId = user?.id
        if (rawId === null || rawId === undefined || rawId === '') {
            return null
        }
        const parsedId = Number(rawId)
        return Number.isNaN(parsedId) ? null : parsedId
    } catch {
        return null
    }
}

export const resolveUserId = (inputId: unknown, roleLabel = '用户'): number => {
    if (inputId !== null && inputId !== undefined && inputId !== '') {
        const parsedId = Number(inputId)
        if (!Number.isNaN(parsedId)) {
            return parsedId
        }
    }

    const sessionUserId = getSessionUserId()
    if (sessionUserId !== null) {
        return sessionUserId
    }

    throw new Error(`缺少${roleLabel}ID，请重新登录后重试`)
}

const getRequestKey = (url: string, options: RequestOptions): string => {
    const method = options.method || 'GET'
    if (method === 'GET') {
        return `${method}:${url}`
    }

    if (options.body instanceof FormData) {
        return `${method}:${url}:form-data`
    }

    if (typeof options.body === 'string') {
        return `${method}:${url}:${options.body}`
    }

    if (options.body instanceof URLSearchParams) {
        return `${method}:${url}:${options.body.toString()}`
    }

    return `${method}:${url}`
}

export const request = async <T = any>(url: string, options: RequestOptions = {}): Promise<Result<T>> => {
    const requestKey = getRequestKey(url, options)
    const shouldTrackPending = shouldTrackPendingRequest(options.method)
    let pendingAdded = false

    if (shouldTrackPending) {
        if (pendingRequests.has(requestKey)) {
            console.warn('重复提交已拦截:', requestKey)
            const duplicateMessage = '请求正在处理中，请勿重复提交'
            useToastStore().warning(duplicateMessage)
            throw createHandledError(duplicateMessage, true)
        }
        pendingRequests.add(requestKey)
        pendingAdded = true
    }

    const token = sessionStorage.getItem('token')
    const headers = normalizeHeaders(options.headers)
    if (token) {
        headers.Authorization = `Bearer ${token}`
    }

    const config: RequestInit = {
        ...options,
        headers,
    }

    if (shouldAutoSetJsonContentType(options.body) && !hasHeaderIgnoreCase(headers, 'Content-Type')) {
        headers['Content-Type'] = 'application/json'
    }

    try {
        const response = await fetch(`${API_BASE}${url}`, config)
        const data = await parseResultFromResponse<T>(response)

        const isUnauthorized = response.status === 401 || data.code === 401
        const isForbiddenNeedLogout = (response.status === 403 || data.code === 403) && shouldForceLogoutOnForbidden(data.message)
        if (isUnauthorized || isForbiddenNeedLogout) {
            forceLogout(data.message || '登录已过期')
            throw new Error(data.message || '权限校验失败')
        }

        if (!response.ok || data.code !== 200) {
            const friendlyMsg = getFriendlyMessage(data.message)
            useToastStore().error(friendlyMsg)
            throw createHandledError(friendlyMsg)
        }

        if (shouldTrackPending) {
            clearCache()
        }

        return data
    } catch (err: unknown) {
        const errorMessage = getErrorMessage(err)
        const requestError = err as RequestError
        const sentryError = err instanceof Error ? err : new Error(errorMessage)

        if (!errorMessage.includes('权限校验') && !errorMessage.includes('禁用')) {
            const friendlyMsg = getFriendlyMessage(errorMessage)
            if (!requestError.handledByToast) {
                useToastStore().error(friendlyMsg)
            }

            if (!requestError.skipSentry) {
                SentryService.captureException(sentryError, {
                    level: 'error',
                    tags: {
                        type: 'api_error',
                        url: url,
                        method: options.method || 'GET',
                    },
                    extra: {
                        requestUrl: `${API_BASE}${url}`,
                        friendlyMessage: friendlyMsg,
                        originalMessage: errorMessage,
                    },
                })
            }
        }
        throw err
    } finally {
        if (pendingAdded) {
            pendingRequests.delete(requestKey)
        }
    }
}

// 原始响应请求：用于下载文件等需要读取响应头/二进制内容的场景
export const requestRaw = async (url: string, options: RequestOptions = {}): Promise<Response> => {
    const requestKey = getRequestKey(url, options)
    const shouldTrackPending = shouldTrackPendingRequest(options.method)
    let pendingAdded = false

    if (shouldTrackPending) {
        if (pendingRequests.has(requestKey)) {
            console.warn('重复提交已拦截:', requestKey)
            const duplicateMessage = '请求正在处理中，请勿重复提交'
            useToastStore().warning(duplicateMessage)
            throw createHandledError(duplicateMessage, true)
        }
        pendingRequests.add(requestKey)
        pendingAdded = true
    }

    const token = sessionStorage.getItem('token')
    const headers = normalizeHeaders(options.headers)
    if (token) {
        headers.Authorization = `Bearer ${token}`
    }

    const config: RequestInit = {
        ...options,
        headers,
    }

    if (shouldAutoSetJsonContentType(options.body) && !hasHeaderIgnoreCase(headers, 'Content-Type')) {
        headers['Content-Type'] = 'application/json'
    }

    try {
        const response = await fetch(`${API_BASE}${url}`, config)

        if (response.status === 401) {
            forceLogout('登录已过期')
            throw new Error('权限校验失败')
        }

        if (response.status === 403) {
            const forbiddenMessage = await extractResponseMessage(response)
            if (shouldForceLogoutOnForbidden(forbiddenMessage)) {
                forceLogout(forbiddenMessage || '登录已过期')
                throw new Error(forbiddenMessage || '权限校验失败')
            }
        }

        if (!response.ok) {
            const message = await extractResponseMessage(response)
            const friendlyMsg = getFriendlyMessage(message)
            useToastStore().error(friendlyMsg)
            throw createHandledError(friendlyMsg)
        }

        if (shouldTrackPending) {
            clearCache()
        }

        return response
    } catch (err: unknown) {
        const errorMessage = getErrorMessage(err)
        const requestError = err as RequestError
        const sentryError = err instanceof Error ? err : new Error(errorMessage)

        if (!errorMessage.includes('权限校验') && !errorMessage.includes('禁用')) {
            const friendlyMsg = getFriendlyMessage(errorMessage)
            if (!requestError.handledByToast) {
                useToastStore().error(friendlyMsg)
            }

            if (!requestError.skipSentry) {
                SentryService.captureException(sentryError, {
                    level: 'error',
                    tags: {
                        type: 'api_error',
                        url: url,
                        method: options.method || 'GET',
                    },
                    extra: {
                        requestUrl: `${API_BASE}${url}`,
                        friendlyMessage: friendlyMsg,
                        originalMessage: errorMessage,
                    },
                })
            }
        }
        throw err
    } finally {
        if (pendingAdded) {
            pendingRequests.delete(requestKey)
        }
    }
}

// 下载类请求统一入口：复用鉴权、错误处理与埋点逻辑
export const requestBlob = async (url: string, options: RequestOptions = {}): Promise<BlobResponse> => {
    const response = await requestRaw(url, options)
    const blob = await response.blob()
    const filename = extractFilenameFromDisposition(response.headers.get('Content-Disposition'))
    return {
        blob,
        filename,
        response,
    }
}

// Cached request
export const cachedRequest = async <T = any>(url: string, options: RequestOptions = {}): Promise<Result<T>> => {
    const cacheKey = url
    const now = Date.now()

    if (!options.method || options.method === 'GET') {
        const cached = cache.get(cacheKey)
        if (cached && now - cached.timestamp < CACHE_DURATION) {
            return cached.data
        }
    }

    const data = await request<T>(url, options)

    if (!options.method || options.method === 'GET') {
        cache.set(cacheKey, { data, timestamp: now })
    }

    return data
}

// Heartbeat Logic
let heartbeatInterval: ReturnType<typeof setInterval> | null = null
let heartbeatFailCount = 0
const MAX_HEARTBEAT_FAIL = 2

const sendHeartbeat = async (): Promise<void> => {
    try {
        const currentAuth = getAuth()
        if (!currentAuth.token) {
            stopHeartbeat()
            return
        }

        const response = await fetch(`${API_BASE}/auth/heartbeat`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${currentAuth.token}`
            }
        })

        const rawText = await response.text()
        const result: Result = rawText ? JSON.parse(rawText) : { code: response.status, message: response.statusText || '', data: null }

        if (result.code === 200) {
            heartbeatFailCount = 0
        } else {
            heartbeatFailCount++
            console.warn(`心跳失败 (${heartbeatFailCount}/${MAX_HEARTBEAT_FAIL}):`, result.message)

            if (heartbeatFailCount >= MAX_HEARTBEAT_FAIL) {
                if (result.message && (result.message.includes('过期') || result.message.includes('其他设备'))) {
                    forceLogout(result.message)
                } else {
                    forceLogout('会话已过期，请重新登录')
                }
            }
        }
    } catch (error: any) {
        heartbeatFailCount++
        console.warn(`心跳网络错误 (${heartbeatFailCount}/${MAX_HEARTBEAT_FAIL}):`, error.message)

        if (heartbeatFailCount >= MAX_HEARTBEAT_FAIL) {
            console.error('心跳连续失败，停止心跳检测')
            stopHeartbeat()
        }
    }
}

export const startHeartbeat = (): void => {
    const auth = getAuth()
    if (!auth.token) return

    if (heartbeatInterval) {
        clearInterval(heartbeatInterval)
    }

    heartbeatFailCount = 0
    isForceLoggingOut = false

    heartbeatInterval = setInterval(() => {
        const latestAuth = getAuth()
        if (latestAuth.token) {
            sendHeartbeat()
        } else {
            clearInterval(heartbeatInterval!)
            heartbeatInterval = null
        }
    }, 30000)
}

export const stopHeartbeat = (): void => {
    if (heartbeatInterval) {
        clearInterval(heartbeatInterval)
        heartbeatInterval = null
    }
}

export const startStatusCheck = startHeartbeat
export const stopStatusCheck = stopHeartbeat
