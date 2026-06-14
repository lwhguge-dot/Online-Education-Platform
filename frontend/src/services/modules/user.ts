import { request, requestBlob, resolveUserId } from '../request'
import type { Result, User, UserProfileDTO, UserSettingsDTO } from '../../types/api'

export const userAPI = {
    getList: (params: Record<string, string> = {}): Promise<Result<User[]>> => {
        const query = new URLSearchParams(params).toString()
        return request<User[]>(`/users/list?${query}`)
    },

    getStats: (): Promise<Result<{ totalUsers: number; activeUsers: number }>> => request('/users/stats'),

    updateStatus: (id: number, status: number, operatorId?: number | null, _operatorName?: string): Promise<Result<void>> => {
        const headers: Record<string, string> = {}
        if (operatorId !== null && operatorId !== undefined) {
            headers['X-User-Id'] = operatorId.toString()
        }
        return request(`/users/${id}/status`, {
            method: 'PUT',
            headers,
            body: JSON.stringify({ status }),
        })
    },

    deleteUser: (id: number, operatorId?: number | null, _operatorName?: string): Promise<Result<void>> => {
        const headers: Record<string, string> = {}
        if (operatorId !== null && operatorId !== undefined) {
            headers['X-User-Id'] = operatorId.toString()
        }
        return request(`/users/${id}`, {
            method: 'DELETE',
            headers,
        })
    },

    updateProfile: (id: number, profileData: UserProfileDTO): Promise<Result<void>> =>
        request(`/users/${id}/profile`, {
            method: 'PUT',
            body: JSON.stringify(profileData),
        }),

    getById: (id: number): Promise<Result<User>> => request<User>(`/users/${id}`),

    getSettings: (id: number): Promise<Result<UserSettingsDTO>> => request(`/users/${id}/settings`),

    updateSettings: (id: number, settings: UserSettingsDTO): Promise<Result<void>> =>
        request(`/users/${id}/settings`, {
            method: 'PUT',
            body: JSON.stringify(settings),
        }),

    uploadAvatar: (id: number, file: File): Promise<Result<{ avatarUrl: string }>> => {
        const formData = new FormData()
        formData.append('file', file)
        return request<{ avatarUrl: string }>(`/users/${id}/avatar`, {
            method: 'POST',
            body: formData,
        })
    },

    getOnlineStatus: (): Promise<Result<Record<string, number>>> => request('/users/online-status'),

    getSessions: (id: number): Promise<Result<Array<{ id: number; ipAddress: string; loginTime: string; lastActiveAt: string }>>> => request(`/users/${id}/sessions`),

    exportCSV: async () => {
        // 统一走请求层，复用鉴权、错误处理与日志采集
        const { blob, filename } = await requestBlob('/users/export?format=csv')
        const url = window.URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = filename || `users_${Date.now()}.csv`
        document.body.appendChild(a)
        a.click()
        window.URL.revokeObjectURL(url)
        a.remove()
    },
}

export const teacherProfileAPI = {
    getProfile: (userId: number | null = null) => {
        const resolvedTeacherId = resolveUserId(userId, '教师')
        return request(`/teachers/${resolvedTeacherId}/profile`)
    },
    updateProfile: (userId: number | null = null, data: Record<string, unknown>) => {
        const resolvedTeacherId = resolveUserId(userId, '教师')
        return request(`/teachers/${resolvedTeacherId}/profile`, {
            method: 'PUT',
            body: JSON.stringify(data)
        })
    },
    uploadAvatar: async (userId: number | null = null, file: File) => {
        const resolvedTeacherId = resolveUserId(userId, '教师')
        const formData = new FormData()
        formData.append('file', file)
        return request(`/teachers/${resolvedTeacherId}/avatar`, {
            method: 'POST',
            body: formData,
        })
    },
    updateNotificationSettings: (userId: number | null = null, settings: Record<string, boolean>) => {
        const resolvedTeacherId = resolveUserId(userId, '教师')
        return request(`/teachers/${resolvedTeacherId}/notification-settings`, {
            method: 'PUT',
            body: JSON.stringify(settings)
        })
    },
    updateGradingCriteria: (userId: number | null = null, criteria: Record<string, unknown>) => {
        const resolvedTeacherId = resolveUserId(userId, '教师')
        return request(`/teachers/${resolvedTeacherId}/grading-criteria`, {
            method: 'PUT',
            body: JSON.stringify(criteria)
        })
    },
    updateDashboardLayout: (userId: number | null = null, layout: Record<string, unknown>) => {
        const resolvedTeacherId = resolveUserId(userId, '教师')
        return request(`/teachers/${resolvedTeacherId}/dashboard-layout`, {
            method: 'PUT',
            body: JSON.stringify(layout)
        })
    },
}
