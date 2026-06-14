import { request, requestBlob, clearCache } from '../request'
import type { Result, Course, CourseDTO, Chapter, ChapterDTO, ChapterQuiz } from '../../types/api'

export const courseAPI = {
    getAll: (params: Record<string, string> = {}): Promise<Result<Course[]>> => {
        const query = new URLSearchParams(params).toString()
        return request<Course[]>(`/courses?${query}`)
    },

    getPublished: (subject?: string): Promise<Result<Course[]>> => {
        const query = subject ? `?subject=${subject}` : ''
        return request<Course[]>(`/courses/published${query}`)
    },

    getStats: (): Promise<Result<{ totalCourses: number; publishedCourses: number }>> => request('/courses/stats'),

    getStatsBySubject: (): Promise<Result<Array<{ subject: string; count: number }>>> => request('/courses/stats/by-subject'),

    getById: (id: number): Promise<Result<Course>> => request<Course>(`/courses/${id}`),

    create: (course: CourseDTO): Promise<Result<Course>> =>
        request<Course>('/courses', {
            method: 'POST',
            body: JSON.stringify(course),
        }),

    updateStatus: async (id: number, status: number): Promise<Result<void>> => {
        const data = await request<void>(`/courses/${id}/status`, {
            method: 'PUT',
            body: JSON.stringify({ status }),
        })
        clearCache()
        return data
    },

    delete: (id: number): Promise<Result<void>> =>
        request(`/courses/${id}`, {
            method: 'DELETE',
        }),

    update: (id: number, course: Partial<CourseDTO>): Promise<Result<void>> =>
        request(`/courses/${id}`, {
            method: 'PUT',
            body: JSON.stringify(course),
        }),

    getTeacherCourses: (teacherId: number): Promise<Result<Course[]>> => request(`/courses/teacher/${teacherId}`),

    getReviewingCourses: (): Promise<Result<Course[]>> => request('/courses/reviewing'),

    submitReview: (id: number): Promise<Result<void>> =>
        request(`/courses/${id}/submit-review`, {
            method: 'POST',
        }),

    withdrawReview: (id: number): Promise<Result<void>> =>
        request(`/courses/${id}/withdraw-review`, {
            method: 'POST',
        }),

    audit: (id: number, action: string, remark: string): Promise<Result<void>> =>
        request(`/courses/${id}/audit`, {
            method: 'POST',
            body: JSON.stringify({ action, remark }),
        }),

    exportCSV: async () => {
        // 统一走请求层，复用鉴权、错误处理和埋点逻辑
        const { blob, filename } = await requestBlob('/courses/export?format=csv')
        const url = window.URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = filename || `courses_${Date.now()}.csv`
        document.body.appendChild(a)
        a.click()
        window.URL.revokeObjectURL(url)
        a.remove()
    },

    batchUpdateStatus: (courseIds: number[], status: number): Promise<Result<void>> =>
        request('/courses/batch-status', {
            method: 'POST',
            body: JSON.stringify({ courseIds, status }),
        }),

    duplicate: (id: number, title: string, teacherId: number | null = null): Promise<Result<Course>> => {
        const payload: Record<string, string | number> = { title }
        if (teacherId !== null && teacherId !== undefined) {
            payload.teacherId = teacherId
        }
        return request<Course>(`/courses/${id}/duplicate`, {
            method: 'POST',
            body: JSON.stringify(payload),
        })
    },

    offline: async (id: number): Promise<Result<void>> => {
        const data = await request<void>(`/courses/${id}/offline`, {
            method: 'POST',
        })
        clearCache()
        return data
    },
}

export const chapterAPI = {
    getByCourse: (courseId: number): Promise<Result<Chapter[]>> => request<Chapter[]>(`/chapters/course/${courseId}`),
    getDetail: (id: number): Promise<Result<Chapter>> => request<Chapter>(`/chapters/${id}`),
    create: (chapter: ChapterDTO): Promise<Result<Chapter>> => request<Chapter>('/chapters', { method: 'POST', body: JSON.stringify(chapter) }),
    update: (id: number, chapter: Partial<ChapterDTO>): Promise<Result<void>> => request(`/chapters/${id}`, { method: 'PUT', body: JSON.stringify(chapter) }),
    delete: (id: number): Promise<Result<void>> => request(`/chapters/${id}`, { method: 'DELETE' }),
    getQuizzes: (chapterId: number): Promise<Result<ChapterQuiz[]>> => request<ChapterQuiz[]>(`/chapters/${chapterId}/quizzes`),
    addQuiz: (chapterId: number, quiz: Omit<ChapterQuiz, 'id'>): Promise<Result<ChapterQuiz>> => request<ChapterQuiz>(`/chapters/${chapterId}/quizzes`, { method: 'POST', body: JSON.stringify(quiz) }),
    addQuizzesBatch: (chapterId: number, quizzes: Array<Omit<ChapterQuiz, 'id'>>): Promise<Result<ChapterQuiz[]>> => request<ChapterQuiz[]>(`/chapters/${chapterId}/quizzes/batch`, { method: 'POST', body: JSON.stringify(quizzes) }),
    deleteQuiz: (quizId: number): Promise<Result<void>> => request(`/chapters/quizzes/${quizId}`, { method: 'DELETE' }),
}
