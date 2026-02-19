import { request, resolveUserId, API_BASE } from '../request'

export const calendarAPI = {
    // 按月查询教学日历事件
    getByMonth: (teacherId: number | null = null, year: number, month: number) => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/calendar/teacher/${resolvedTeacherId}/month?year=${year}&month=${month}`)
    },
    // 按周查询教学日历事件
    getByWeek: (teacherId: number | null = null, startDate: string) => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/calendar/teacher/${resolvedTeacherId}/week?startDate=${startDate}`)
    },
    // 按日查询教学日历事件
    getByDay: (teacherId: number | null = null, date: string) => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/calendar/teacher/${resolvedTeacherId}/day?date=${date}`)
    },
    // 创建事件
    createEvent: (data: any) => request('/calendar/events', { method: 'POST', body: JSON.stringify(data) }),
    // 更新事件
    updateEvent: (id: number, data: any) => request(`/calendar/events/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    // 删除事件（后端要求 teacherId 参与鉴权）
    deleteEvent: (id: number, teacherId: number | null = null) => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/calendar/events/${id}?teacherId=${resolvedTeacherId}`, { method: 'DELETE' })
    },
    // 导出iCal
    exportICal: (teacherId: number | null = null, year: number, month: number) => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return `${API_BASE}/calendar/teacher/${resolvedTeacherId}/export?year=${year}&month=${month}`
    },
}
