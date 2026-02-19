import { request, resolveUserId, clearCache, API_BASE } from '../request'
import type { Result, User } from '../../types/api'

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
        const response = await fetch(`${API_BASE}/users/${id}`, {
            method: 'DELETE',
            headers,
        })
        const data: Result<any> = await response.json()
        if (data.code !== 200) throw new Error(data.message)
        clearCache()
        return data
    },

    updateProfile: (id: number, profileData: any): Promise<Result<any>> =>
        request(`/users/${id}/profile`, {
            method: 'PUT',
            body: JSON.stringify(profileData),
        }),

    getById: (id: number): Promise<Result<User>> => request<User>(`/users/${id}`),

    getSettings: (id: number): Promise<Result<any>> => request(`/users/${id}/settings`),

    updateSettings: (id: number, settings: any): Promise<Result<any>> =>
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

    getOnlineStatus: () => request('/users/online-status'),

    getSessions: (id: number): Promise<Result<any>> => request(`/users/${id}/sessions`),

    exportCSV: async () => {
        const token = sessionStorage.getItem('token')
        const response = await fetch(`${API_BASE}/users/export?format=csv`, {
            headers: {
                Authorization: `Bearer ${token}`,
            },
        })
        if (!response.ok) throw new Error('导出失败')
        const blob = await response.blob()
        const url = window.URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        const disposition = response.headers.get('Content-Disposition')
        const filename = disposition?.match(/filename="?(.+)"?/)?.[1] || `users_${Date.now()}.csv`
        a.download = filename
        document.body.appendChild(a)
        a.click()
        window.URL.revokeObjectURL(url)
        a.remove()
    },
}

export const teacherProfileAPI = {
    getProfile: (userId = null) => {
        const resolvedTeacherId = resolveUserId(userId, '教师')
        return request(`/teachers/${resolvedTeacherId}/profile`)
    },
    updateProfile: (userId = null, data: any) => {
        const resolvedTeacherId = resolveUserId(userId, '教师')
        return request(`/teachers/${resolvedTeacherId}/profile`, {
            method: 'PUT',
            body: JSON.stringify(data)
        })
    },
    uploadAvatar: async (userId = null, file: File) => {
        const resolvedTeacherId = resolveUserId(userId, '教师')
        const formData = new FormData()
        formData.append('file', file)
        return request(`/teachers/${resolvedTeacherId}/avatar`, {
            method: 'POST',
            body: formData,
        })
    },
    updateNotificationSettings: (userId = null, settings: any) => {
        const resolvedTeacherId = resolveUserId(userId, '教师')
        return request(`/teachers/${resolvedTeacherId}/notification-settings`, {
            method: 'PUT',
            body: JSON.stringify(settings)
        })
    },
    updateGradingCriteria: (userId = null, criteria: any) => {
        const resolvedTeacherId = resolveUserId(userId, '教师')
        return request(`/teachers/${resolvedTeacherId}/grading-criteria`, {
            method: 'PUT',
            body: JSON.stringify(criteria)
        })
    },
    updateDashboardLayout: (userId = null, layout: any) => {
        const resolvedTeacherId = resolveUserId(userId, '教师')
        return request(`/teachers/${resolvedTeacherId}/dashboard-layout`, {
            method: 'PUT',
            body: JSON.stringify(layout)
        })
    },
}
