import { request, clearCache, API_BASE } from '../request'

export const courseAPI = {
    getAll: (params: any = {}) => {
        const query = new URLSearchParams(params).toString()
        return request(`/courses?${query}`)
    },

    getPublished: (subject: string) => {
        const query = subject ? `?subject=${subject}` : ''
        return request(`/courses/published${query}`)
    },

    getStats: () => request('/courses/stats'),

    getStatsBySubject: () => request('/courses/stats/by-subject'),

    getById: (id: number) => request(`/courses/${id}`),

    create: (course: any) =>
        request('/courses', {
            method: 'POST',
            body: JSON.stringify(course),
        }),

    updateStatus: async (id: number, status: number) => {
        const data = await request(`/courses/${id}/status`, {
            method: 'PUT',
            body: JSON.stringify({ status }),
        })
        clearCache()
        return data
    },

    delete: (id: number) =>
        request(`/courses/${id}`, {
            method: 'DELETE',
        }),

    update: (id: number, course: any) =>
        request(`/courses/${id}`, {
            method: 'PUT',
            body: JSON.stringify(course),
        }),

    getTeacherCourses: (teacherId: number) => request(`/courses/teacher/${teacherId}`),

    getReviewingCourses: () => request('/courses/reviewing'),

    submitReview: (id: number) =>
        request(`/courses/${id}/submit-review`, {
            method: 'POST',
        }),

    withdrawReview: (id: number) =>
        request(`/courses/${id}/withdraw-review`, {
            method: 'POST',
        }),

    audit: (id: number, action: string, remark: string) =>
        request(`/courses/${id}/audit`, {
            method: 'POST',
            body: JSON.stringify({ action, remark }),
        }),

    exportCSV: async () => {
        const token = sessionStorage.getItem('token')
        const response = await fetch(`${API_BASE}/courses/export?format=csv`, {
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
        const filename = disposition?.match(/filename="?(.+)"?/)?.[1] || `courses_${Date.now()}.csv`
        a.download = filename
        document.body.appendChild(a)
        a.click()
        window.URL.revokeObjectURL(url)
        a.remove()
    },

    batchUpdateStatus: (courseIds: number[], status: number) =>
        request('/courses/batch-status', {
            method: 'POST',
            body: JSON.stringify({ courseIds, status }),
        }),

    duplicate: (id: number, title: string, teacherId: number | null = null) => {
        const payload: Record<string, any> = { title }
        if (teacherId !== null && teacherId !== undefined) {
            payload.teacherId = teacherId
        }
        return request(`/courses/${id}/duplicate`, {
            method: 'POST',
            body: JSON.stringify(payload),
        })
    },

    offline: async (id: number) => {
        const data = await request(`/courses/${id}/offline`, {
            method: 'POST',
        })
        clearCache()
        return data
    },
}

export const chapterAPI = {
    getByCourse: (courseId: number) => request(`/chapters/course/${courseId}`),
    getDetail: (id: number) => request(`/chapters/${id}`),
    create: (chapter: any) => request('/chapters', { method: 'POST', body: JSON.stringify(chapter) }),
    update: (id: number, chapter: any) => request(`/chapters/${id}`, { method: 'PUT', body: JSON.stringify(chapter) }),
    delete: (id: number) => request(`/chapters/${id}`, { method: 'DELETE' }),
    getQuizzes: (chapterId: number) => request(`/chapters/${chapterId}/quizzes`),
    addQuiz: (chapterId: number, quiz: any) => request(`/chapters/${chapterId}/quizzes`, { method: 'POST', body: JSON.stringify(quiz) }),
    addQuizzesBatch: (chapterId: number, quizzes: any[]) => request(`/chapters/${chapterId}/quizzes/batch`, { method: 'POST', body: JSON.stringify(quizzes) }),
    deleteQuiz: (quizId: number) => request(`/chapters/quizzes/${quizId}`, { method: 'DELETE' }),
}
