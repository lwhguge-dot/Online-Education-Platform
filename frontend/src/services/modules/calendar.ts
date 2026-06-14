import { request, resolveUserId, API_BASE } from '../request'
import type { Result } from '../../types/api'

export const calendarAPI = {
    getByMonth: (teacherId: number | null = null, year: number, month: number): Promise<Result<unknown[]>> => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/calendar/teacher/${resolvedTeacherId}/month?year=${year}&month=${month}`)
    },
    getByWeek: (teacherId: number | null = null, startDate: string): Promise<Result<unknown[]>> => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/calendar/teacher/${resolvedTeacherId}/week?startDate=${startDate}`)
    },
    getByDay: (teacherId: number | null = null, date: string): Promise<Result<unknown[]>> => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/calendar/teacher/${resolvedTeacherId}/day?date=${date}`)
    },
    createEvent: (data: { title: string; description?: string; startTime: string; endTime: string; type?: string }): Promise<Result<{ id: number }>> => request('/calendar/events', { method: 'POST', body: JSON.stringify(data) }),
    updateEvent: (id: number, data: { title: string; description?: string; startTime: string; endTime: string; type?: string }): Promise<Result<void>> => request(`/calendar/events/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    deleteEvent: (id: number, teacherId: number | null = null): Promise<Result<void>> => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/calendar/events/${id}?teacherId=${resolvedTeacherId}`, { method: 'DELETE' })
    },
    exportICal: (teacherId: number | null = null, year: number, month: number): string => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return `${API_BASE}/calendar/teacher/${resolvedTeacherId}/export?year=${year}&month=${month}`
    },
}
