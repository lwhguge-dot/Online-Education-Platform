/**
 * API 服务层
 * 提供统一的 HTTP 请求封装和 API 调用方法
 */

import type { Result, User, LoginResponse, RegisterRequest, ResetPasswordRequest } from '../types/api'
import { useToastStore } from '../stores/toast'
import SentryService from '../utils/sentry'

// 使用环境变量配置 API 地址，支持开发/生产环境切换
const API_BASE = import.meta.env.VITE_API_BASE || '/api'
const STATIC_BASE = import.meta.env.VITE_STATIC_BASE || ''
const USER_STATIC_BASE = import.meta.env.VITE_USER_STATIC_BASE || ''

// ==================== 类型定义 ====================

interface CacheEntry<T = any> {
  data: T
  timestamp: number
}

interface RequestOptions extends RequestInit {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
}

interface AuthData {
  token: string | null
  user: User | null
}

// 友好错误消息映射类型
type FriendlyErrorMessages = Record<string, string>

interface RequestError extends Error {
  handledByToast?: boolean
}

// ========== 简单内存缓存机制 ==========
const cache = new Map<string, CacheEntry>()
const CACHE_DURATION = 30000 // 30秒缓存

// 带缓存的请求函数
const cachedRequest = async <T = any>(url: string, options: RequestOptions = {}): Promise<Result<T>> => {
  const cacheKey = url
  const now = Date.now()

  // 只缓存GET请求
  if (!options.method || options.method === 'GET') {
    const cached = cache.get(cacheKey)
    if (cached && now - cached.timestamp < CACHE_DURATION) {
      return cached.data
    }
  }

  // 发起请求
  const data = await request<T>(url, options)

  // 缓存结果
  if (!options.method || options.method === 'GET') {
    cache.set(cacheKey, { data, timestamp: now })
  }

  return data
}

// 清除缓存
export const clearCache = (): void => cache.clear()

// 处理图片URL，将相对路径转换为完整URL
export const getImageUrl = (path: string | undefined | null): string => {
  if (!path) return ''
  if (path.startsWith('http')) return path
  // 头像文件在 user-service
  if (path.includes('/avatars/')) {
    return USER_STATIC_BASE + path
  }
  return STATIC_BASE + path
}

// 防止重复触发强制登出
let isForceLoggingOut = false

// 强制退出登录并跳转到登录页
const forceLogout = (message = '您的账号已被禁用，请重新登录'): void => {
  // 防止重复触发
  if (isForceLoggingOut) {
    return
  }
  isForceLoggingOut = true

  // 先停止心跳
  stopHeartbeat()

  sessionStorage.removeItem('token')
  sessionStorage.removeItem('user')
  const toast = useToastStore()
  toast.error(message)
  setTimeout(() => {
    window.location.href = '/login'
  }, 1000)
}

// 将技术性错误转换为用户友好的提示
// 注意：顺序很重要，更具体的匹配应该放在前面
const friendlyErrorMessages: FriendlyErrorMessages = {
  // 数据库相关错误（优先级高，放在前面）
  'database': '数据处理异常，请稍后重试',
  'SQL': '数据处理异常，请稍后重试',
  'Table': '系统配置异常，请联系管理员',
  'Column': '系统配置异常，请联系管理员',
  'doesn\'t exist': '系统配置异常，请联系管理员',
  // 网络相关错误
  'Network Error': '网络连接失败，请检查网络后重试',
  'Failed to fetch': '无法连接服务器，请稍后重试',
  'ECONNREFUSED': '无法连接服务器，请稍后重试',
  'Connection refused': '服务器连接失败，请稍后重试',
  // HTTP 错误
  'Internal Server Error': '服务器繁忙，请稍后重试',
  '500': '服务器繁忙，请稍后重试',
  '502': '服务器维护中，请稍后重试',
  '503': '服务暂时不可用，请稍后重试',
  '504': '服务器响应超时，请稍后重试',
  // 超时错误（放在最后，因为其他错误可能也包含timeout）
  'timeout': '请求超时，请稍后重试',
}

const getFriendlyMessage = (errorMsg: string | undefined): string => {
  if (!errorMsg) return '操作失败，请稍后重试'

  // 检查是否匹配已知的技术性错误
  for (const [key, friendly] of Object.entries(friendlyErrorMessages)) {
    if (errorMsg.toLowerCase().includes(key.toLowerCase())) {
      return friendly
    }
  }

  // 如果错误消息包含技术性内容（如SQL、Exception等），返回通用提示
  if (/exception|error|sql|null|undefined|cannot|failed/i.test(errorMsg)) {
    return '操作失败，请稍后重试'
  }

  return errorMsg
}

// 正在进行的请求，用于防止冲突
const pendingRequests = new Set<string>()

// 统一提取错误消息，避免 unknown 类型导致的重复判断
const getErrorMessage = (error: unknown): string => {
  if (error instanceof Error) {
    return error.message
  }
  return String(error ?? '未知错误')
}

/**
 * 仅在“账号状态异常/令牌失效”时触发强制登出。
 * 严格模式下大量接口会返回权限不足(403)，此类业务拒绝不应直接清会话。
 */
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

/**
 * 从会话中读取当前登录用户ID。
 */
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

/**
 * 优先使用显式传入ID；未传时自动回退到当前登录用户ID。
 */
const resolveUserId = (inputId: unknown, roleLabel = '用户'): number => {
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

// 生成请求去重键：非GET请求加入body信息，避免不同提交被误判为重复
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

const request = async <T = any>(url: string, options: RequestOptions = {}): Promise<Result<T>> => {
  const requestKey = getRequestKey(url, options)

  // 对于POST/PUT，防止短时间内的重复请求
  if (options.method && options.method !== 'GET') {
    if (pendingRequests.has(requestKey)) {
      console.warn('重复提交已拦截:', requestKey)
      return { code: 429, message: '请求正在处理中...', data: null as any }
    }
    pendingRequests.add(requestKey)
  }

  const token = sessionStorage.getItem('token')

  const config: RequestInit = {
    headers: {
      ...(token && { Authorization: `Bearer ${token}` }),
      ...options.headers,
    } as HeadersInit,
    ...options,
  }

  // 只有非 FormData 且未显示指定 Content-Type 时，才默认设置为 application/json
  if (!(options.body instanceof FormData) && !(config.headers as any)['Content-Type']) {
    (config.headers as any)['Content-Type'] = 'application/json'
  }

  try {
    const response = await fetch(`${API_BASE}${url}`, config)
    let data: Result<T>
    const rawText = await response.text()
    if (!rawText) {
      const normalizedCode = response.status === 204 ? 200 : response.status
      data = { code: normalizedCode, message: response.statusText || '', data: null as any }
    } else {
      try {
        data = JSON.parse(rawText) as Result<T>
      } catch {
        data = { code: response.status, message: '响应解析失败', data: null as any }
      }
    }

    // 仅对明确的认证失败触发强制登出；普通权限不足(403)保留在当前页并提示
    const isUnauthorized = response.status === 401 || data.code === 401
    const isForbiddenNeedLogout = (response.status === 403 || data.code === 403) && shouldForceLogoutOnForbidden(data.message)
    if (isUnauthorized || isForbiddenNeedLogout) {
      forceLogout(data.message || '登录已过期')
      throw new Error(data.message || '权限校验失败')
    }

    if (!response.ok || data.code !== 200) {
      const friendlyMsg = getFriendlyMessage(data.message)
      useToastStore().error(friendlyMsg)
      const businessError: RequestError = new Error(friendlyMsg)
      businessError.handledByToast = true
      throw businessError
    }

    if (options.method && options.method !== 'GET') {
      clearCache()
    }

    return data
  } catch (err: unknown) {
    const errorMessage = getErrorMessage(err)
    const requestError = err as RequestError
    const sentryError = err instanceof Error ? err : new Error(errorMessage)
    // 捕获 API 错误到 Sentry
    if (!errorMessage.includes('权限校验') && !errorMessage.includes('禁用')) {
      const friendlyMsg = getFriendlyMessage(errorMessage)
      if (!requestError.handledByToast) {
        useToastStore().error(friendlyMsg)
      }

      // 发送错误到 Sentry
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
    throw err
  } finally {
    if (options.method && options.method !== 'GET') {
      pendingRequests.delete(requestKey)
    }
  }
}

export const authAPI = {
  // 登录：使用邮箱+密码
  login: (email: string, password: string): Promise<Result<LoginResponse>> =>
    request<LoginResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),
  // 注册：邮箱+用户名+真实姓名+密码+角色
  register: (email: string, username: string, realName: string, password: string, role: string): Promise<Result<void>> =>
    request<void>('/auth/register', {
      method: 'POST',
      body: JSON.stringify({ email, username, realName, password, role }),
    }),
  // 密码重置：邮箱+真实姓名+新密码
  resetPassword: (email: string, realName: string, newPassword: string): Promise<Result<void>> =>
    request<void>('/auth/reset-password', {
      method: 'POST',
      body: JSON.stringify({ email, realName, newPassword }),
    }),
  logout: (): Promise<Result<void>> =>
    request<void>('/auth/logout', {
      method: 'POST',
    }),
  heartbeat: (): Promise<Result<void>> =>
    request<void>('/auth/heartbeat', {
      method: 'POST',
    }),
  checkStatus: (userId: number): Promise<Result<any>> =>
    request(`/auth/check-status/${userId}`),
  validateToken: (userId: number): Promise<Result<any>> =>
    request(`/auth/validate-token/${userId}`),
  forceLogout: (userId: number): Promise<Result<void>> =>
    request<void>(`/auth/force-logout/${userId}`, {
      method: 'POST',
    }),
}

// 心跳定时器（每30秒发送一次心跳）
let heartbeatInterval: ReturnType<typeof setInterval> | null = null

// 发送心跳
let heartbeatFailCount = 0
const MAX_HEARTBEAT_FAIL = 2 // 连续失败2次才强制登出

const sendHeartbeat = async (): Promise<void> => {
  try {
    const currentAuth = getAuth()
    if (!currentAuth.token) {
      stopHeartbeat()
      return
    }

    // 直接调用fetch，避免request函数的toast提示
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
      heartbeatFailCount = 0 // 重置失败计数
    } else {
      heartbeatFailCount++
      console.warn(`心跳失败 (${heartbeatFailCount}/${MAX_HEARTBEAT_FAIL}):`, result.message)

      if (heartbeatFailCount >= MAX_HEARTBEAT_FAIL) {
        // 检查是否是会话过期或其他设备登录
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

    // 网络错误时不立即登出，等待重试
    if (heartbeatFailCount >= MAX_HEARTBEAT_FAIL) {
      // 网络错误不强制登出，只是停止心跳
      console.error('心跳连续失败，停止心跳检测')
      stopHeartbeat()
    }
  }
}

export const startHeartbeat = (): void => {
  const auth = getAuth()
  if (!auth.token) return

  // 清除之前的定时器
  if (heartbeatInterval) {
    clearInterval(heartbeatInterval)
  }

  // 重置失败计数和强制登出标志
  heartbeatFailCount = 0
  isForceLoggingOut = false

  // 每30秒发送一次心跳
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

// 兼容旧接口
export const startStatusCheck = startHeartbeat
export const stopStatusCheck = stopHeartbeat
export const setSkipFirstCheck = (): void => { }

export const userAPI = {
  getList: (params: Record<string, any> = {}): Promise<Result<any>> => {
    const query = new URLSearchParams(params).toString()
    return request(`/users/list?${query}`)
  },

  getStats: (): Promise<Result<any>> => request('/users/stats'),

  updateStatus: async (id: number, status: number, operatorId: number, operatorName: string): Promise<Result<any>> => {
    const token = sessionStorage.getItem('token')
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      ...(token && { Authorization: `Bearer ${token}` }),
    }
    if (operatorId) headers['X-User-Id'] = operatorId.toString()
    if (operatorName) headers['X-User-Name'] = operatorName
    const response = await fetch(`${API_BASE}/users/${id}/status`, {
      method: 'PUT',
      headers,
      body: JSON.stringify({ status }),
    })
    const data: Result<any> = await response.json()
    if (data.code !== 200) throw new Error(data.message)
    clearCache()
    return data
  },

  deleteUser: async (id: number, operatorId: number, operatorName: string): Promise<Result<any>> => {
    const token = sessionStorage.getItem('token')
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      ...(token && { Authorization: `Bearer ${token}` }),
    }
    if (operatorId) headers['X-User-Id'] = operatorId.toString()
    if (operatorName) headers['X-User-Name'] = operatorName
    const response = await fetch(`${API_BASE}/users/${id}`, {
      method: 'DELETE',
      headers,
    })
    const data: Result<any> = await response.json()
    if (data.code !== 200) throw new Error(data.message)
    clearCache()
    return data
  },

  // 更新用户个人信息
  updateProfile: (id: number, profileData: any): Promise<Result<any>> =>
    request(`/users/${id}/profile`, {
      method: 'PUT',
      body: JSON.stringify(profileData),
    }),

  // 获取用户信息
  getById: (id: number): Promise<Result<User>> => request<User>(`/users/${id}`),

  // 获取用户设置（通知设置、学习目标）
  getSettings: (id: number): Promise<Result<any>> => request(`/users/${id}/settings`),

  // 更新用户设置
  updateSettings: (id: number, settings: any): Promise<Result<any>> =>
    request(`/users/${id}/settings`, {
      method: 'PUT',
      body: JSON.stringify(settings),
    }),

  // 获取用户在线状态
  getOnlineStatus: () => request('/users/online-status'),

  // 获取用户会话列表（管理员查看登录设备）
  getSessions: (id: number): Promise<Result<any>> => request(`/users/${id}/sessions`),

  // 导出用户数据CSV
  exportCSV: async () => {
    const token = sessionStorage.getItem('token');
    const response = await fetch(`${API_BASE}/users/export?format=csv`, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
    if (!response.ok) throw new Error('导出失败');
    const blob = await response.blob();
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    const disposition = response.headers.get('Content-Disposition');
    const filename = disposition?.match(/filename="?(.+)"?/)?.[1] || `users_${Date.now()}.csv`;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    window.URL.revokeObjectURL(url);
    a.remove();
  },
};

// 教师资料API
export const teacherProfileAPI = {
  // 获取教师资料
  getProfile: (userId = null) => {
    const resolvedTeacherId = resolveUserId(userId, '教师')
    return request(`/teachers/${resolvedTeacherId}/profile`)
  },
  // 更新教师资料
  updateProfile: (userId = null, data) => {
    const resolvedTeacherId = resolveUserId(userId, '教师')
    return request(`/teachers/${resolvedTeacherId}/profile`, {
      method: 'PUT',
      body: JSON.stringify(data)
    })
  },
  // 上传头像
  uploadAvatar: async (userId = null, file) => {
    const resolvedTeacherId = resolveUserId(userId, '教师')
    const formData = new FormData();
    formData.append('file', file);
    return request(`/teachers/${resolvedTeacherId}/avatar`, {
      method: 'POST',
      body: formData,
    });
  },
  // 更新通知设置
  updateNotificationSettings: (userId = null, settings) => {
    const resolvedTeacherId = resolveUserId(userId, '教师')
    return request(`/teachers/${resolvedTeacherId}/notification-settings`, {
      method: 'PUT',
      body: JSON.stringify(settings)
    })
  },
  // 更新评分标准
  updateGradingCriteria: (userId = null, criteria) => {
    const resolvedTeacherId = resolveUserId(userId, '教师')
    return request(`/teachers/${resolvedTeacherId}/grading-criteria`, {
      method: 'PUT',
      body: JSON.stringify(criteria)
    })
  },
  // 更新仪表盘布局
  updateDashboardLayout: (userId = null, layout) => {
    const resolvedTeacherId = resolveUserId(userId, '教师')
    return request(`/teachers/${resolvedTeacherId}/dashboard-layout`, {
      method: 'PUT',
      body: JSON.stringify(layout)
    })
  },
};

// 教学日历API
export const calendarAPI = {
  // 按月查询教学日历事件
  getByMonth: (teacherId = null, year, month) => {
    const resolvedTeacherId = resolveUserId(teacherId, '教师')
    return request(`/calendar/teacher/${resolvedTeacherId}/month?year=${year}&month=${month}`)
  },
  // 按周查询教学日历事件
  getByWeek: (teacherId = null, startDate) => {
    const resolvedTeacherId = resolveUserId(teacherId, '教师')
    return request(`/calendar/teacher/${resolvedTeacherId}/week?startDate=${startDate}`)
  },
  // 按日查询教学日历事件
  getByDay: (teacherId = null, date) => {
    const resolvedTeacherId = resolveUserId(teacherId, '教师')
    return request(`/calendar/teacher/${resolvedTeacherId}/day?date=${date}`)
  },
  // 创建事件
  createEvent: (data) => request('/calendar/events', { method: 'POST', body: JSON.stringify(data) }),
  // 更新事件
  updateEvent: (id, data) => request(`/calendar/events/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  // 删除事件（后端要求 teacherId 参与鉴权）
  deleteEvent: (id, teacherId = null) => {
    const resolvedTeacherId = resolveUserId(teacherId, '教师')
    return request(`/calendar/events/${id}?teacherId=${resolvedTeacherId}`, { method: 'DELETE' })
  },
  // 导出iCal
  exportICal: (teacherId = null, year, month) => {
    const resolvedTeacherId = resolveUserId(teacherId, '教师')
    return `${API_BASE}/calendar/teacher/${resolvedTeacherId}/export?year=${year}&month=${month}`
  },
};

export const courseAPI = {
  getAll: (params = {}) => {
    const query = new URLSearchParams(params).toString();
    return request(`/courses?${query}`);
  },

  getPublished: (subject) => {
    const query = subject ? `?subject=${subject}` : '';
    return request(`/courses/published${query}`);
  },

  getStats: () => request('/courses/stats'),

  // 获取按学科分组的课程分布统计（管理员仪表盘饼图）
  getStatsBySubject: () => request('/courses/stats/by-subject'),

  getById: (id) => request(`/courses/${id}`),

  create: (course) =>
    request('/courses', {
      method: 'POST',
      body: JSON.stringify(course),
    }),

  // 课程状态变更（严格模式：仅管理员可调用）
  updateStatus: async (id, status) => {
    const data = await request(`/courses/${id}/status`, {
      method: 'PUT',
      body: JSON.stringify({ status }),
    })
    clearCache()
    return data
  },

  delete: (id) =>
    request(`/courses/${id}`, {
      method: 'DELETE',
    }),

  update: (id, course) =>
    request(`/courses/${id}`, {
      method: 'PUT',
      body: JSON.stringify(course),
    }),

  getTeacherCourses: (teacherId) => request(`/courses/teacher/${teacherId}`),

  getReviewingCourses: () => request('/courses/reviewing'),

  submitReview: (id) =>
    request(`/courses/${id}/submit-review`, {
      method: 'POST',
    }),

  withdrawReview: (id) =>
    request(`/courses/${id}/withdraw-review`, {
      method: 'POST',
    }),

  // 课程审核（严格模式：后端优先使用网关注入身份，不再信任请求体 auditBy）
  audit: (id, action, remark) =>
    request(`/courses/${id}/audit`, {
      method: 'POST',
      body: JSON.stringify({ action, remark }),
    }),

  // 导出课程数据CSV
  exportCSV: async () => {
    const token = sessionStorage.getItem('token');
    const response = await fetch(`${API_BASE}/courses/export?format=csv`, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
    if (!response.ok) throw new Error('导出失败');
    const blob = await response.blob();
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    const disposition = response.headers.get('Content-Disposition');
    const filename = disposition?.match(/filename="?(.+)"?/)?.[1] || `courses_${Date.now()}.csv`;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    window.URL.revokeObjectURL(url);
    a.remove();
  },

  // 批量更新课程状态
  batchUpdateStatus: (courseIds, status) =>
    request('/courses/batch-status', {
      method: 'POST',
      body: JSON.stringify({ courseIds, status }),
    }),

  // 复制课程
  // 复制课程（严格模式：教师侧 teacherId 由后端按当前身份确定，管理员可显式指定）
  duplicate: (id, title, teacherId = null) => {
    const payload: Record<string, any> = { title }
    if (teacherId !== null && teacherId !== undefined) {
      payload.teacherId = teacherId
    }
    return request(`/courses/${id}/duplicate`, {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  },

  // 管理员强制下线违规课程
  // 强制下线课程（严格模式：仅管理员可调用）
  offline: async (id) => {
    const data = await request(`/courses/${id}/offline`, {
      method: 'POST',
    })
    clearCache()
    return data
  },
};

// 报名API
export const enrollmentAPI = {
  enroll: (courseId, studentId = null) => {
    const resolvedStudentId = resolveUserId(studentId, '学生')
    return request(`/enrollments/enroll?studentId=${resolvedStudentId}&courseId=${courseId}`, {
      method: 'POST',
    })
  },
  drop: (courseId, studentId = null) => {
    const resolvedStudentId = resolveUserId(studentId, '学生')
    return request(`/enrollments/drop?studentId=${resolvedStudentId}&courseId=${courseId}`, {
      method: 'POST',
    })
  },
  check: (courseId, studentId = null) => {
    const resolvedStudentId = resolveUserId(studentId, '学生')
    return request(`/enrollments/check?studentId=${resolvedStudentId}&courseId=${courseId}`)
  },
  getStudentEnrollments: (studentId = null) => {
    const resolvedStudentId = resolveUserId(studentId, '学生')
    return request(`/enrollments/student/${resolvedStudentId}`)
  },
  getStudentEnrollmentsWithNewChapters: (studentId = null) => {
    const resolvedStudentId = resolveUserId(studentId, '学生')
    return request(`/enrollments/student/${resolvedStudentId}/with-new-chapters`)
  },
  checkNewChapters: (courseId, studentId = null) => {
    const resolvedStudentId = resolveUserId(studentId, '学生')
    return request(`/enrollments/check-new-chapters?studentId=${resolvedStudentId}&courseId=${courseId}`)
  },
  getCourseEnrollments: (courseId) =>
    request(`/enrollments/course/${courseId}`),
  updateProgress: (courseId, progress, studentId = null) => {
    const resolvedStudentId = resolveUserId(studentId, '学生')
    return request(`/enrollments/progress?studentId=${resolvedStudentId}&courseId=${courseId}&progress=${progress}`, {
      method: 'PUT',
    })
  },
  // 获取教师所有课程的学生列表（聚合数据，支持分页）
  getTeacherStudents: (teacherId = null, page = 1, size = 20) => {
    const resolvedTeacherId = resolveUserId(teacherId, '教师')
    return request(`/enrollments/teacher/${resolvedTeacherId}/students?page=${page}&size=${size}`)
  },
  // 获取教师所有课程的学生概览（按课程分组）
  getTeacherStudentsOverview: (teacherId = null) => {
    const resolvedTeacherId = resolveUserId(teacherId, '教师')
    return request(`/enrollments/teacher/${resolvedTeacherId}/students/overview`)
  },
  // 获取课程学生列表（含学情状态）
  getCourseStudentsWithStatus: (courseId, page = 1, size = 20, status = 'all') =>
    request(`/enrollments/course/${courseId}/students?page=${page}&size=${size}&status=${status}`),

  // 导出教师学生数据CSV
  exportStudentsCSV: async (teacherId = null, courseId = null) => {
    const resolvedTeacherId = resolveUserId(teacherId, '教师')
    const token = sessionStorage.getItem('token');
    const url = courseId
      ? `${API_BASE}/enrollments/teacher/${resolvedTeacherId}/students/export?courseId=${courseId}`
      : `${API_BASE}/enrollments/teacher/${resolvedTeacherId}/students/export`;
    const response = await fetch(url, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
    if (!response.ok) throw new Error('导出失败');
    const blob = await response.blob();
    const blobUrl = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = blobUrl;
    const disposition = response.headers.get('Content-Disposition');
    const filename = disposition?.match(/filename="?(.+)"?/)?.[1] || `students_${Date.now()}.csv`;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    window.URL.revokeObjectURL(blobUrl);
    a.remove();
  },
};

// 文件上传API
export const fileAPI = {
  uploadVideo: (file) => {
    const formData = new FormData();
    formData.append('file', file);
    return request('/files/upload/video', {
      method: 'POST',
      body: formData,
    });
  },
  uploadImage: (file) => {
    const formData = new FormData();
    formData.append('file', file);
    return request('/files/upload/image', {
      method: 'POST',
      body: formData,
    });
  },
  uploadDocument: (file) => {
    const formData = new FormData();
    formData.append('file', file);
    return request('/files/upload/document', {
      method: 'POST',
      body: formData,
    });
  },
  // 删除已上传的文件
  deleteFile: (path) =>
    request(`/files/delete?path=${encodeURIComponent(path)}`, {
      method: 'DELETE',
    }),
};

// 健康检查API
export const healthAPI = {
  check: () => request('/auth/health'),
};

// 章节API
export const chapterAPI = {
  getByCourse: (courseId) => request(`/chapters/course/${courseId}`),
  getDetail: (id) => request(`/chapters/${id}`),
  create: (chapter) => request('/chapters', { method: 'POST', body: JSON.stringify(chapter) }),
  update: (id, chapter) => request(`/chapters/${id}`, { method: 'PUT', body: JSON.stringify(chapter) }),
  delete: (id) => request(`/chapters/${id}`, { method: 'DELETE' }),
  getQuizzes: (chapterId) => request(`/chapters/${chapterId}/quizzes`),
  addQuiz: (chapterId, quiz) => request(`/chapters/${chapterId}/quizzes`, { method: 'POST', body: JSON.stringify(quiz) }),
  addQuizzesBatch: (chapterId, quizzes) => request(`/chapters/${chapterId}/quizzes/batch`, { method: 'POST', body: JSON.stringify(quizzes) }),
  // 删除单个测验题目
  deleteQuiz: (quizId) => request(`/chapters/quizzes/${quizId}`, { method: 'DELETE' }),
};

// 进度API
export const progressAPI = {
  // 上报视频进度（自动携带时间戳用于防作弊校验）
  reportVideo: (data) => request('/progress/video/report', {
    method: 'POST',
    body: JSON.stringify({ ...data, clientTimestamp: Date.now() })
  }),
  submitQuiz: (data) => request('/progress/quiz/submit', { method: 'POST', body: JSON.stringify(data) }),
  getChapterProgress: (chapterId, studentId = null) => {
    const resolvedStudentId = resolveUserId(studentId, '学生')
    return request(`/progress/chapter/${chapterId}?studentId=${resolvedStudentId}`)
  },
  getCourseProgress: (courseId, studentId = null) => {
    const resolvedStudentId = resolveUserId(studentId, '学生')
    return request(`/progress/course/${courseId}?studentId=${resolvedStudentId}`)
  },
  checkUnlock: (chapterId, studentId = null) => {
    const resolvedStudentId = resolveUserId(studentId, '学生')
    return request(`/progress/check-unlock?studentId=${resolvedStudentId}&chapterId=${chapterId}`)
  },
  // 更新进度（自动携带时间戳用于防作弊校验）
  updateProgress: (data) => request('/progress/video/report', {
    method: 'POST',
    body: JSON.stringify({ ...data, clientTimestamp: Date.now() })
  }),
  // 学习轨迹和知识点掌握度（真实数据）
  getLearningTrack: (studentId = null) => {
    const resolvedStudentId = resolveUserId(studentId, '学生')
    return request(`/progress/student/${resolvedStudentId}/learning-track`)
  },
  getKnowledgeMastery: (studentId = null) => {
    const resolvedStudentId = resolveUserId(studentId, '学生')
    return request(`/progress/student/${resolvedStudentId}/mastery`)
  },
  // 教师端：获取学生在特定课程的学习轨迹
  getLearningTrajectory: (courseId, studentId) => request(`/progress/course/${courseId}/student/${studentId}/trajectory`),
  // 教师端：获取学生在特定课程的测验分数趋势
  getQuizScoreTrend: (courseId, studentId) => request(`/progress/course/${courseId}/student/${studentId}/quiz-trend`),
  // 教师端：获取学生在特定课程的详细学情分析
  getStudentCourseAnalytics: (courseId, studentId) => request(`/progress/course/${courseId}/student/${studentId}/analytics`),
  // 教师端：获取课程分析数据
  getCourseAnalytics: (courseId) => request(`/progress/course/${courseId}/analytics`),
};

// 作业API
export const homeworkAPI = {
  create: (homework) => request('/homeworks', { method: 'POST', body: JSON.stringify(homework) }),
  getDetail: (id) => request(`/homeworks/${id}`),
  getByChapter: (chapterId) => request(`/homeworks/chapter/${chapterId}`),
  getStudentHomeworks: (chapterId, studentId = null) => {
    const resolvedStudentId = resolveUserId(studentId, '学生')
    return request(`/homeworks/student?studentId=${resolvedStudentId}&chapterId=${chapterId}`)
  },
  unlock: (studentId, chapterId) => request(`/homeworks/unlock?studentId=${studentId}&chapterId=${chapterId}`, { method: 'POST' }),
  submit: (data) => request('/homeworks/submit', { method: 'POST', body: JSON.stringify(data) }),
  getSubmission: (homeworkId, studentId = null) => {
    const resolvedStudentId = resolveUserId(studentId, '学生')
    return request(`/homeworks/${homeworkId}/submission?studentId=${resolvedStudentId}`)
  },
  getReport: (homeworkId, studentId = null) => {
    const resolvedStudentId = resolveUserId(studentId, '学生')
    return request(`/homeworks/${homeworkId}/report?studentId=${resolvedStudentId}`)
  },
  getSubmissions: (homeworkId) => request(`/homeworks/${homeworkId}/submissions`),
  gradeSubjective: (submissionId, questionId, score, feedback) =>
    request(`/homeworks/grade-subjective?submissionId=${submissionId}&questionId=${questionId}&score=${score}&feedback=${feedback || ''}`, { method: 'POST' }),
  // 教师待办事项和活动（真实数据）
  getTeacherTodos: (teacherId = null) => {
    const resolvedTeacherId = resolveUserId(teacherId, '教师')
    return request(`/homeworks/teacher/${resolvedTeacherId}/todos`)
  },
  getTeacherActivities: (teacherId = null) => {
    const resolvedTeacherId = resolveUserId(teacherId, '教师')
    return request(`/homeworks/teacher/${resolvedTeacherId}/activities`)
  },

  // 批改工作台相关API
  getPendingSubmissions: (homeworkId) => request(`/homeworks/${homeworkId}/submissions/pending`),
  getSubmissionDetail: (submissionId) => request(`/homeworks/submissions/${submissionId}/detail`),
  gradeSubmission: (submissionId, grades) =>
    request(`/homeworks/submissions/${submissionId}/grade`, {
      method: 'POST',
      body: JSON.stringify(grades)
    }),

  // 复制作业
  duplicate: (id, chapterId, title) =>
    request(`/homeworks/${id}/duplicate`, {
      method: 'POST',
      body: JSON.stringify({ chapterId, title }),
    }),

  // 批量导入题目
  importQuestions: (homeworkId, questions) =>
    request(`/homeworks/${homeworkId}/import-questions`, {
      method: 'POST',
      body: JSON.stringify({ questions }),
    }),

  // ==================== 作业问答相关API ====================
  // 学生提问
  askQuestion: (homeworkId, questionId, content, studentId = null) => {
    const resolvedStudentId = resolveUserId(studentId, '学生')
    return request(`/homeworks/${homeworkId}/questions?studentId=${resolvedStudentId}${questionId ? `&questionId=${questionId}` : ''}&content=${encodeURIComponent(content)}`, {
      method: 'POST',
    })
  },

  // 教师回复问题
  replyQuestion: (discussionId, reply, teacherId = null) => {
    const resolvedTeacherId = resolveUserId(teacherId, '教师')
    return request(`/homeworks/questions/${discussionId}/reply?teacherId=${resolvedTeacherId}&reply=${encodeURIComponent(reply)}`, {
      method: 'POST',
    })
  },

  // 获取作业的所有问答
  getHomeworkQuestions: (homeworkId) =>
    request(`/homeworks/${homeworkId}/questions`),

  // 获取学生的所有提问
  getStudentQuestions: (studentId = null) => {
    const resolvedStudentId = resolveUserId(studentId, '学生')
    return request(`/homeworks/student/${resolvedStudentId}/questions`)
  },

  // 获取教师待回复的问题数量
  getTeacherPendingQuestionsCount: (teacherId = null) => {
    const resolvedTeacherId = resolveUserId(teacherId, '教师')
    return request(`/homeworks/teacher/${resolvedTeacherId}/pending-questions-count`)
  },
};

// 评论API
export const commentAPI = {
  // 学生发布答案（解锁主观题问答）
  publishAnswer: (questionId, answerContent, studentId = null) => {
    const resolvedStudentId = resolveUserId(studentId, '学生')
    return request(`/comments/publish-answer?studentId=${resolvedStudentId}&questionId=${questionId}`, {
      method: 'POST',
      body: JSON.stringify({ answerContent }),
    })
  },

  // 获取学生问答列表（学生中心）
  getStudentQuestions: (studentId = null) => {
    const resolvedStudentId = resolveUserId(studentId, '学生')
    return request(`/comments/student/${resolvedStudentId}/questions`)
  },

  toggleTop: (commentId) => request(`/comments/${commentId}/toggle-top`, { method: 'PUT' }),
  delete: (commentId) => request(`/comments/${commentId}`, { method: 'DELETE' }),
};

// 讨论API（教师中心增强版）
export const discussionAPI = {
  // 获取教师的所有讨论（按课程/章节分组）
  getTeacherDiscussions: (teacherId = null) => {
    const resolvedTeacherId = resolveUserId(teacherId, '教师')
    return request(`/discussions/teacher/${resolvedTeacherId}`)
  },
  // 获取讨论统计
  getStats: (teacherId = null) => {
    const resolvedTeacherId = resolveUserId(teacherId, '教师')
    return request(`/discussions/teacher/${resolvedTeacherId}/stats`)
  },
  // 按课程获取讨论
  getByCourse: (courseId) => request(`/discussions/course/${courseId}`),
  // 更新回答状态
  updateStatus: (id, status, answeredBy) =>
    request(`/discussions/${id}/status?status=${status}${answeredBy ? `&answeredBy=${answeredBy}` : ''}`, { method: 'PUT' }),
  // 切换置顶状态
  toggleTop: (id) => request(`/discussions/${id}/toggle-top`, { method: 'PUT' }),
  // 回复讨论
  reply: (parentId, data) => request(`/discussions/${parentId}/reply`, {
    method: 'POST',
    body: JSON.stringify(data)
  }),
  // 获取回复列表
  getReplies: (parentId) => request(`/discussions/${parentId}/replies`),
};

// 统计API
export const statsAPI = {
  // 教师仪表盘统计（带课程数据）
  getTeacherDashboard: (teacherId = null, courses = []) => {
    const resolvedTeacherId = resolveUserId(teacherId, '教师')
    if (courses.length > 0) {
      return request(`/stats/teacher/dashboard?teacherId=${resolvedTeacherId}`, {
        method: 'POST',
        body: JSON.stringify(courses),
      });
    }
    return request(`/stats/teacher/dashboard?teacherId=${resolvedTeacherId}`);
  },

  // 教师待办事项
  getTeacherTodos: (teacherId = null) => {
    const resolvedTeacherId = resolveUserId(teacherId, '教师')
    return request(`/homeworks/teacher/${resolvedTeacherId}/todos`)
  },

  // 教师最近活动
  getTeacherActivities: (teacherId = null) => {
    const resolvedTeacherId = resolveUserId(teacherId, '教师')
    return request(`/homeworks/teacher/${resolvedTeacherId}/activities`)
  },

  // 管理员仪表盘
  getAdminDashboard: () => request('/stats/admin/dashboard'),

  // 学生学习统计
  getStudentDashboard: (studentId = null) => {
    const resolvedStudentId = resolveUserId(studentId, '学生')
    return request(`/stats/student/${resolvedStudentId}/dashboard`)
  },

  // 获取教师今日新增学生数
  getTeacherTodayEnrollments: (teacherId = null) => {
    const resolvedTeacherId = resolveUserId(teacherId, '教师')
    return request(`/enrollments/teacher/${resolvedTeacherId}/today`)
  },

  // 获取用户增长趋势数据（管理员仪表盘图表）
  getUserTrends: (days = 7) =>
    request(`/stats/admin/user-trends?days=${days}`),
};

// 徽章API
export const badgeAPI = {
  // 获取学生徽章（包含已解锁和未解锁）
  getStudentBadges: (studentId = null) => {
    const resolvedStudentId = resolveUserId(studentId, '学生')
    return request(`/progress/badges/student/${resolvedStudentId}`)
  },

  // 检查并授予符合条件的徽章
  checkAndAwardBadges: (studentId = null) => {
    const resolvedStudentId = resolveUserId(studentId, '学生')
    return request(`/progress/badges/student/${resolvedStudentId}/check`, {
      method: 'POST',
    })
  },
};

// 审计日志API
export const auditLogAPI = {
  // 分页查询审计日志
  getList: (params = {}) => {
    const query = new URLSearchParams(params).toString();
    return request(`/audit-logs?${query}`);
  },
  // 根据ID查询审计日志详情
  getById: (id) => request(`/audit-logs/${id}`),
};

// 公告管理API
export const announcementAPI = {
  // 分页查询公告列表
  getList: (params = {}) => {
    const query = new URLSearchParams(params).toString();
    return request(`/announcements?${query}`);
  },
  // 查询已发布的公告（按受众过滤）
  getActive: (audience) => {
    const query = audience ? `?audience=${audience}` : '';
    return request(`/announcements/active${query}`);
  },
  // 根据ID查询公告详情
  getById: (id) => request(`/announcements/${id}`),
  // 创建公告
  create: (announcement) =>
    request('/announcements', {
      method: 'POST',
      body: JSON.stringify(announcement),
    }),
  // 更新公告
  update: (id, announcement) =>
    request(`/announcements/${id}`, {
      method: 'PUT',
      body: JSON.stringify(announcement),
    }),
  // 删除公告
  delete: (id) =>
    request(`/announcements/${id}`, {
      method: 'DELETE',
    }),
  // 发布公告
  publish: (id) =>
    request(`/announcements/${id}/publish`, {
      method: 'POST',
    }),

  // ========== 教师公告相关API ==========
  // 教师发布公告
  createByTeacher: (teacherId = null, announcement) => {
    const resolvedTeacherId = resolveUserId(teacherId, '教师')
    return request(`/announcements/teachers/${resolvedTeacherId}`, {
      method: 'POST',
      body: JSON.stringify(announcement),
    })
  },
  // 教师更新公告
  updateByTeacher: (teacherId = null, announcementId, announcement) => {
    const resolvedTeacherId = resolveUserId(teacherId, '教师')
    return request(`/announcements/teachers/${resolvedTeacherId}/${announcementId}`, {
      method: 'PUT',
      body: JSON.stringify(announcement),
    })
  },
  // 教师删除公告
  deleteByTeacher: (teacherId = null, announcementId) => {
    const resolvedTeacherId = resolveUserId(teacherId, '教师')
    return request(`/announcements/teachers/${resolvedTeacherId}/${announcementId}`, {
      method: 'DELETE',
    })
  },
  // 查询教师发布的公告列表
  getByTeacher: (teacherId = null, params = {}) => {
    const resolvedTeacherId = resolveUserId(teacherId, '教师')
    const query = new URLSearchParams(params).toString();
    return request(`/announcements/teachers/${resolvedTeacherId}?${query}`);
  },
  // 获取公告阅读统计
  getStats: (id) => request(`/announcements/${id}/stats`),
  // 记录用户阅读公告（默认以网关注入身份为准）
  recordRead: (id, userId = null) => {
    const query = userId ? `?userId=${userId}` : '';
    return request(`/announcements/${id}/read${query}`, {
      method: 'POST',
    });
  },
  // 置顶/取消置顶公告
  togglePin: (teacherId = null, announcementId) => {
    const resolvedTeacherId = resolveUserId(teacherId, '教师')
    return request(`/announcements/teachers/${resolvedTeacherId}/${announcementId}/toggle-pin`, {
      method: 'POST',
    })
  },
};

// 通知API
export const notificationAPI = {
  // 发送通知给单个用户
  send: (userId, title, content, type = 'NOTIFICATION') =>
    request('/notifications/send', {
      method: 'POST',
      body: JSON.stringify({ userId, title, content, type }),
    }),
  // 批量发送通知
  sendBatch: (userIds, title, content) =>
    request('/notifications/send-batch', {
      method: 'POST',
      body: JSON.stringify({ userIds, title, content }),
    }),
  // 检查用户是否在线
  isOnline: (userId) => request(`/notifications/online/${userId}`),
};

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
      // 用户信息损坏时主动清理，避免后续页面反复报错
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

// 章节评论API（学习页面评论区）
export const chapterCommentAPI = {
  // 获取章节评论列表
  getChapterComments: (chapterId, params = {}) => {
    const query = new URLSearchParams(params).toString();
    return request(`/comments/chapter/${chapterId}?${query}`);
  },
  // 获取学生在章节评论区的提问列表（学生中心）
  getStudentQuestions: (studentId = null) => {
    const resolvedStudentId = resolveUserId(studentId, '学生')
    return request(`/comments/chapter/student/${resolvedStudentId}/questions`)
  },
  // 发表评论
  createComment: (data) =>
    request('/comments', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  // 点赞/取消点赞
  toggleLike: (commentId, _userId) =>
    request(`/comments/${commentId}/like`, {
      method: 'POST',
    }),
  // 置顶/取消置顶
  togglePin: (commentId) =>
    request(`/comments/${commentId}/pin`, {
      method: 'POST',
    }),
  // 删除评论
  deleteComment: (commentId, _userId, _isAdmin = false) =>
    request(`/comments/${commentId}`, {
      method: 'DELETE',
    }),
  // 获取评论回复
  getReplies: (commentId, _userId) =>
    request(`/comments/${commentId}/replies`),
  // 禁言用户
  muteUser: (data) =>
    request('/comments/mute', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  // 解除禁言
  unmuteUser: (data) =>
    request('/comments/unmute', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  // 检查禁言状态
  getMuteStatus: (userId, courseId) =>
    request(`/comments/mute-status?userId=${userId}&courseId=${courseId}`),
  // 获取禁言记录
  getMuteRecords: (courseId) =>
    request(`/comments/mute-records?courseId=${courseId}`),
  // 获取屏蔽词列表
  getBlockedWords: (scope = 'global', courseId = null) => {
    const params = new URLSearchParams({ scope });
    if (courseId) params.append('courseId', courseId);
    return request(`/comments/blocked-words?${params}`);
  },
  // 添加屏蔽词
  addBlockedWord: (data) =>
    request('/comments/blocked-words', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  // 删除屏蔽词
  deleteBlockedWord: (id) =>
    request(`/comments/blocked-words/${id}`, {
      method: 'DELETE',
    }),
  // 检查内容是否包含屏蔽词
  checkBlockedWords: (content, courseId) =>
    request('/comments/blocked-words/check', {
      method: 'POST',
      body: JSON.stringify({ content, courseId }),
    }),
};

export default { authAPI, userAPI, teacherProfileAPI, calendarAPI, courseAPI, enrollmentAPI, fileAPI, chapterAPI, progressAPI, homeworkAPI, commentAPI, chapterCommentAPI, discussionAPI, healthAPI, statsAPI, badgeAPI, auditLogAPI, announcementAPI, notificationAPI, saveAuth, getAuth, clearAuth, startStatusCheck, stopStatusCheck, startHeartbeat, stopHeartbeat };
