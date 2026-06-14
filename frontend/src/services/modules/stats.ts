import { request, resolveUserId } from '../request'
import type { Result, TeacherDashboardStats, AdminDashboardStats, StudentDashboardStats, ChapterProgress } from '../../types/api'

export const statsAPI = {
    getTeacherDashboard: (teacherId: number | null = null, courses: Array<{ id: number; title: string }> = []): Promise<Result<TeacherDashboardStats>> => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        if (courses.length > 0) {
            return request<TeacherDashboardStats>(`/stats/teacher/dashboard?teacherId=${resolvedTeacherId}`, {
                method: 'POST',
                body: JSON.stringify(courses),
            })
        }
        return request<TeacherDashboardStats>(`/stats/teacher/dashboard?teacherId=${resolvedTeacherId}`)
    },
    getTeacherTodos: (teacherId: number | null = null): Promise<Result<Array<{ id: number; title: string; count: number; type: string }>>> => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/homeworks/teacher/${resolvedTeacherId}/todos`)
    },
    getTeacherActivities: (teacherId: number | null = null): Promise<Result<Array<{ type: string; title: string; time: string }>>> => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/homeworks/teacher/${resolvedTeacherId}/activities`)
    },
    getAdminDashboard: (): Promise<Result<AdminDashboardStats>> => request<AdminDashboardStats>('/stats/admin/dashboard'),
    getStudentDashboard: (studentId: number | null = null): Promise<Result<StudentDashboardStats>> => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request<StudentDashboardStats>(`/stats/student/${resolvedStudentId}/dashboard`)
    },
    getTeacherTodayEnrollments: (teacherId: number | null = null): Promise<Result<{ count: number }>> => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/enrollments/teacher/${resolvedTeacherId}/today`)
    },
    getUserTrends: (days = 7): Promise<Result<Array<{ date: string; count: number }>>> =>
        request(`/stats/admin/user-trends?days=${days}`),
}

export const progressAPI = {
    reportVideo: (data: { chapterId: number; studentId: number; progress: number; duration: number }): Promise<Result<void>> => request('/progress/video/report', {
        method: 'POST',
        body: JSON.stringify({ ...data, clientTimestamp: Date.now() })
    }),
    submitQuiz: (data: { chapterId: number; studentId: number; answers: number[] }): Promise<Result<{ score: number }>> => request('/progress/quiz/submit', { method: 'POST', body: JSON.stringify(data) }),
    getChapterProgress: (chapterId: number, studentId: number | null = null): Promise<Result<ChapterProgress>> => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request<ChapterProgress>(`/progress/chapter/${chapterId}?studentId=${resolvedStudentId}`)
    },
    getCourseProgress: (courseId: number, studentId: number | null = null): Promise<Result<ChapterProgress[]>> => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request<ChapterProgress[]>(`/progress/course/${courseId}?studentId=${resolvedStudentId}`)
    },
    checkUnlock: (chapterId: number, studentId: number | null = null): Promise<Result<{ unlocked: boolean }>> => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/progress/check-unlock?studentId=${resolvedStudentId}&chapterId=${chapterId}`)
    },
    updateProgress: (data: { chapterId: number; studentId: number; progress?: number; duration?: number; courseId?: number; videoRate?: number; isCompleted?: number; currentPosition?: number }): Promise<Result<void>> => request('/progress/video/report', {
        method: 'POST',
        body: JSON.stringify({ ...data, clientTimestamp: Date.now() })
    }),
    getLearningTrack: (studentId: number | null = null): Promise<Result<Array<{ chapterId: number; chapterTitle: string; completed: boolean; studyTime: number }>>> => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/progress/student/${resolvedStudentId}/learning-track`)
    },
    getKnowledgeMastery: (studentId: number | null = null): Promise<Result<Array<{ chapterId: number; title: string; mastery: number }>>> => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/progress/student/${resolvedStudentId}/mastery`)
    },
    getLearningTrajectory: (courseId: number, studentId: number): Promise<Result<unknown>> => request(`/progress/course/${courseId}/student/${studentId}/trajectory`),
    getQuizScoreTrend: (courseId: number, studentId: number): Promise<Result<unknown>> => request(`/progress/course/${courseId}/student/${studentId}/quiz-trend`),
    getStudentCourseAnalytics: (courseId: number, studentId: number): Promise<Result<unknown>> => request(`/progress/course/${courseId}/student/${studentId}/analytics`),
    getCourseAnalytics: (courseId: number): Promise<Result<unknown>> => request(`/progress/course/${courseId}/analytics`),
}

export const badgeAPI = {
    getStudentBadges: (studentId: number | null = null): Promise<Result<Array<{ id: number; name: string; code: string; description: string; unlocked: boolean; unlockedAt: string | null; progress: number; currentValue: number; targetValue: number; nearUnlock: boolean }>>> => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/progress/badges/student/${resolvedStudentId}`)
    },
    checkAndAwardBadges: (studentId: number | null = null): Promise<Result<void>> => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/progress/badges/student/${resolvedStudentId}/check`, {
            method: 'POST',
        })
    },
}

export const auditLogAPI = {
    getList: (params: Record<string, string> = {}): Promise<Result<unknown[]>> => {
        const query = new URLSearchParams(params).toString()
        return request(`/audit-logs?${query}`)
    },
    getById: (id: number): Promise<Result<unknown>> => request(`/audit-logs/${id}`),
}
