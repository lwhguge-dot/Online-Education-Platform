import { request, resolveUserId } from '../request'
import type { Result } from '../../types/api'

export const commentAPI = {
    publishAnswer: (questionId: number, answerContent: string, studentId: number | null = null): Promise<Result<void>> => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/comments/publish-answer?studentId=${resolvedStudentId}&questionId=${questionId}`, {
            method: 'POST',
            body: JSON.stringify({ answerContent }),
        })
    },
    getStudentQuestions: (studentId: number | null = null): Promise<Result<unknown[]>> => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/comments/student/${resolvedStudentId}/questions`)
    },
    toggleTop: (commentId: number): Promise<Result<void>> => request(`/comments/${commentId}/toggle-top`, { method: 'PUT' }),
    delete: (commentId: number): Promise<Result<void>> => request(`/comments/${commentId}`, { method: 'DELETE' }),
}

export const chapterCommentAPI = {
    getChapterComments: (chapterId: number, params: Record<string, string> = {}): Promise<Result<unknown[]>> => {
        const query = new URLSearchParams(params).toString()
        return request(`/comments/chapter/${chapterId}?${query}`)
    },
    getStudentQuestions: (studentId: number | null = null): Promise<Result<unknown[]>> => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/comments/chapter/student/${resolvedStudentId}/questions`)
    },
    createComment: (data: { courseId: number; chapterId: number; content: string; parentId?: number | null }): Promise<Result<{ id: number }>> =>
        request(`/comments`, {
            method: 'POST',
            body: JSON.stringify(data),
        }),
    toggleLike: (commentId: number, _userId: number): Promise<Result<void>> =>
        request(`/comments/${commentId}/like`, {
            method: 'POST',
        }),
    togglePin: (commentId: number): Promise<Result<void>> =>
        request(`/comments/${commentId}/pin`, {
            method: 'POST',
        }),
    deleteComment: (commentId: number, _userId: number, _isAdmin = false): Promise<Result<void>> =>
        request(`/comments/${commentId}`, {
            method: 'DELETE',
        }),
    getReplies: (commentId: number, _userId: number): Promise<Result<unknown[]>> =>
        request(`/comments/${commentId}/replies`),
    muteUser: (data: { userId: number; courseId: number; duration?: number }): Promise<Result<void>> =>
        request('/comments/mute', {
            method: 'POST',
            body: JSON.stringify(data),
        }),
    unmuteUser: (data: { userId: number; courseId: number }): Promise<Result<void>> =>
        request('/comments/unmute', {
            method: 'POST',
            body: JSON.stringify(data),
        }),
    getMuteStatus: (userId: number, courseId: number): Promise<Result<{ muted: boolean }>> =>
        request(`/comments/mute-status?userId=${userId}&courseId=${courseId}`),
    getMuteRecords: (courseId: number): Promise<Result<unknown[]>> =>
        request(`/comments/mute-records?courseId=${courseId}`),
    getBlockedWords: (scope = 'global', courseId: number | null = null): Promise<Result<Array<{ id: number; word: string }>>> => {
        const params = new URLSearchParams({ scope })
        if (courseId) params.append('courseId', courseId.toString())
        return request(`/comments/blocked-words?${params}`)
    },
    addBlockedWord: (data: { word: string; scope?: string; courseId?: number }): Promise<Result<void>> =>
        request('/comments/blocked-words', {
            method: 'POST',
            body: JSON.stringify(data),
        }),
    deleteBlockedWord: (id: number): Promise<Result<void>> =>
        request(`/comments/blocked-words/${id}`, {
            method: 'DELETE',
        }),
    checkBlockedWords: (content: string, courseId: number): Promise<Result<{ blocked: boolean; words: string[] }>> =>
        request('/comments/blocked-words/check', {
            method: 'POST',
            body: JSON.stringify({ content, courseId }),
        }),
}

export const discussionAPI = {
    getTeacherDiscussions: (teacherId: number | null = null): Promise<Result<unknown[]>> => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/discussions/teacher/${resolvedTeacherId}`)
    },
    getStats: (teacherId: number | null = null): Promise<Result<{ total: number; answered: number; unanswered: number }>> => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/discussions/teacher/${resolvedTeacherId}/stats`)
    },
    getByCourse: (courseId: number): Promise<Result<unknown[]>> => request(`/discussions/course/${courseId}`),
    updateStatus: (id: number, status: number, answeredBy: string): Promise<Result<void>> =>
        request(`/discussions/${id}/status?status=${status}${answeredBy ? `&answeredBy=${answeredBy}` : ''}`, { method: 'PUT' }),
    toggleTop: (id: number): Promise<Result<void>> => request(`/discussions/${id}/toggle-top`, { method: 'PUT' }),
    reply: (parentId: number, data: { content: string; userId: number }): Promise<Result<void>> => request(`/discussions/${parentId}/reply`, {
        method: 'POST',
        body: JSON.stringify(data)
    }),
    getReplies: (parentId: number): Promise<Result<unknown[]>> => request(`/discussions/${parentId}/replies`),
}

export const announcementAPI = {
    getList: (params: Record<string, string> = {}): Promise<Result<unknown[]>> => {
        const query = new URLSearchParams(params).toString()
        return request(`/announcements?${query}`)
    },
    getActive: (audience: string): Promise<Result<unknown[]>> => {
        const query = audience ? `?audience=${audience}` : ''
        return request(`/announcements/active${query}`)
    },
    getById: (id: number): Promise<Result<unknown>> => request(`/announcements/${id}`),
    create: (announcement: { title: string; content: string; audience: 'all' | 'student' | 'teacher'; isPinned?: boolean }): Promise<Result<{ id: number }>> =>
        request('/announcements', {
            method: 'POST',
            body: JSON.stringify(announcement),
        }),
    update: (id: number, announcement: { title: string; content: string; audience: 'all' | 'student' | 'teacher'; isPinned?: boolean }): Promise<Result<void>> =>
        request(`/announcements/${id}`, {
            method: 'PUT',
            body: JSON.stringify(announcement),
        }),
    delete: (id: number): Promise<Result<void>> =>
        request(`/announcements/${id}`, {
            method: 'DELETE',
        }),
    publish: (id: number): Promise<Result<void>> =>
        request(`/announcements/${id}/publish`, {
            method: 'POST',
        }),
    createByTeacher: (teacherId: number | null = null, announcement: { title: string; content: string; audience: 'all' | 'student' | 'teacher'; isPinned?: boolean }): Promise<Result<{ id: number }>> => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/announcements/teachers/${resolvedTeacherId}`, {
            method: 'POST',
            body: JSON.stringify(announcement),
        })
    },
    updateByTeacher: (teacherId: number | null = null, announcementId: number, announcement: { title: string; content: string; audience: 'all' | 'student' | 'teacher'; isPinned?: boolean }): Promise<Result<void>> => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/announcements/teachers/${resolvedTeacherId}/${announcementId}`, {
            method: 'PUT',
            body: JSON.stringify(announcement),
        })
    },
    deleteByTeacher: (teacherId: number | null = null, announcementId: number): Promise<Result<void>> => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/announcements/teachers/${resolvedTeacherId}/${announcementId}`, {
            method: 'DELETE',
        })
    },
    getByTeacher: (teacherId: number | null = null, params: Record<string, string> = {}): Promise<Result<unknown[]>> => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        const query = new URLSearchParams(params).toString()
        return request(`/announcements/teachers/${resolvedTeacherId}?${query}`)
    },
    getStats: (id: number): Promise<Result<{ total: number; read: number; unread: number }>> => request(`/announcements/${id}/stats`),
    recordRead: (id: number, userId: number | null = null): Promise<Result<void>> => {
        const query = userId ? `?userId=${userId}` : ''
        return request(`/announcements/${id}/read${query}`, {
            method: 'POST',
        })
    },
    togglePin: (teacherId: number | null = null, announcementId: number): Promise<Result<void>> => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/announcements/teachers/${resolvedTeacherId}/${announcementId}/toggle-pin`, {
            method: 'POST',
        })
    },
}

export const notificationAPI = {
    send: (userId: number, title: string, content: string, type = 'NOTIFICATION'): Promise<Result<void>> =>
        request('/notifications/send', {
            method: 'POST',
            body: JSON.stringify({ userId, title, content, type }),
        }),
    sendBatch: (userIds: number[], title: string, content: string): Promise<Result<void>> =>
        request('/notifications/send-batch', {
            method: 'POST',
            body: JSON.stringify({ userIds, title, content }),
        }),
    isOnline: (userId: number): Promise<Result<{ online: boolean }>> => request(`/notifications/online/${userId}`),
}
