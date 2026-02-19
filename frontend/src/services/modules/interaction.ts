import { request, resolveUserId } from '../request'

export const commentAPI = {
    // 学生发布答案（解锁主观题问答）
    publishAnswer: (questionId: number, answerContent: string, studentId: number | null = null) => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/comments/publish-answer?studentId=${resolvedStudentId}&questionId=${questionId}`, {
            method: 'POST',
            body: JSON.stringify({ answerContent }),
        })
    },

    // 获取学生问答列表（学生中心）
    getStudentQuestions: (studentId: number | null = null) => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/comments/student/${resolvedStudentId}/questions`)
    },

    toggleTop: (commentId: number) => request(`/comments/${commentId}/toggle-top`, { method: 'PUT' }),
    delete: (commentId: number) => request(`/comments/${commentId}`, { method: 'DELETE' }),
}

export const chapterCommentAPI = {
    // 获取章节评论列表
    getChapterComments: (chapterId: number, params: any = {}) => {
        const query = new URLSearchParams(params).toString()
        return request(`/comments/chapter/${chapterId}?${query}`)
    },
    // 获取学生在章节评论区的提问列表（学生中心）
    getStudentQuestions: (studentId: number | null = null) => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/comments/chapter/student/${resolvedStudentId}/questions`)
    },
    // 发表评论
    createComment: (data: any) =>
        request('/comments', {
            method: 'POST',
            body: JSON.stringify(data),
        }),
    // 点赞/取消点赞
    toggleLike: (commentId: number, _userId: any) =>
        request(`/comments/${commentId}/like`, {
            method: 'POST',
        }),
    // 置顶/取消置顶
    togglePin: (commentId: number) =>
        request(`/comments/${commentId}/pin`, {
            method: 'POST',
        }),
    // 删除评论
    deleteComment: (commentId: number, _userId: any, _isAdmin = false) =>
        request(`/comments/${commentId}`, {
            method: 'DELETE',
        }),
    // 获取评论回复
    getReplies: (commentId: number, _userId: any) =>
        request(`/comments/${commentId}/replies`),
    // 禁言用户
    muteUser: (data: any) =>
        request('/comments/mute', {
            method: 'POST',
            body: JSON.stringify(data),
        }),
    // 解除禁言
    unmuteUser: (data: any) =>
        request('/comments/unmute', {
            method: 'POST',
            body: JSON.stringify(data),
        }),
    // 检查禁言状态
    getMuteStatus: (userId: number, courseId: number) =>
        request(`/comments/mute-status?userId=${userId}&courseId=${courseId}`),
    // 获取禁言记录
    getMuteRecords: (courseId: number) =>
        request(`/comments/mute-records?courseId=${courseId}`),
    // 获取屏蔽词列表
    getBlockedWords: (scope = 'global', courseId: number | null = null) => {
        const params = new URLSearchParams({ scope })
        if (courseId) params.append('courseId', courseId.toString())
        return request(`/comments/blocked-words?${params}`)
    },
    // 添加屏蔽词
    addBlockedWord: (data: any) =>
        request('/comments/blocked-words', {
            method: 'POST',
            body: JSON.stringify(data),
        }),
    // 删除屏蔽词
    deleteBlockedWord: (id: number) =>
        request(`/comments/blocked-words/${id}`, {
            method: 'DELETE',
        }),
    // 检查内容是否包含屏蔽词
    checkBlockedWords: (content: string, courseId: number) =>
        request('/comments/blocked-words/check', {
            method: 'POST',
            body: JSON.stringify({ content, courseId }),
        }),
}

export const discussionAPI = {
    // 获取教师的所有讨论（按课程/章节分组）
    getTeacherDiscussions: (teacherId: number | null = null) => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/discussions/teacher/${resolvedTeacherId}`)
    },
    // 获取讨论统计
    getStats: (teacherId: number | null = null) => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/discussions/teacher/${resolvedTeacherId}/stats`)
    },
    // 按课程获取讨论
    getByCourse: (courseId: number) => request(`/discussions/course/${courseId}`),
    // 更新回答状态
    updateStatus: (id: number, status: number, answeredBy: string) =>
        request(`/discussions/${id}/status?status=${status}${answeredBy ? `&answeredBy=${answeredBy}` : ''}`, { method: 'PUT' }),
    // 切换置顶状态
    toggleTop: (id: number) => request(`/discussions/${id}/toggle-top`, { method: 'PUT' }),
    // 回复讨论
    reply: (parentId: number, data: any) => request(`/discussions/${parentId}/reply`, {
        method: 'POST',
        body: JSON.stringify(data)
    }),
    // 获取回复列表
    getReplies: (parentId: number) => request(`/discussions/${parentId}/replies`),
}

export const announcementAPI = {
    // 分页查询公告列表
    getList: (params: any = {}) => {
        const query = new URLSearchParams(params).toString()
        return request(`/announcements?${query}`)
    },
    // 查询已发布的公告（按受众过滤）
    getActive: (audience: string) => {
        const query = audience ? `?audience=${audience}` : ''
        return request(`/announcements/active${query}`)
    },
    // 根据ID查询公告详情
    getById: (id: number) => request(`/announcements/${id}`),
    // 创建公告
    create: (announcement: any) =>
        request('/announcements', {
            method: 'POST',
            body: JSON.stringify(announcement),
        }),
    // 更新公告
    update: (id: number, announcement: any) =>
        request(`/announcements/${id}`, {
            method: 'PUT',
            body: JSON.stringify(announcement),
        }),
    // 删除公告
    delete: (id: number) =>
        request(`/announcements/${id}`, {
            method: 'DELETE',
        }),
    // 发布公告
    publish: (id: number) =>
        request(`/announcements/${id}/publish`, {
            method: 'POST',
        }),

    // ========== 教师公告相关API ==========
    // 教师发布公告
    createByTeacher: (teacherId: number | null = null, announcement: any) => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/announcements/teachers/${resolvedTeacherId}`, {
            method: 'POST',
            body: JSON.stringify(announcement),
        })
    },
    // 教师更新公告
    updateByTeacher: (teacherId: number | null = null, announcementId: number, announcement: any) => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/announcements/teachers/${resolvedTeacherId}/${announcementId}`, {
            method: 'PUT',
            body: JSON.stringify(announcement),
        })
    },
    // 教师删除公告
    deleteByTeacher: (teacherId: number | null = null, announcementId: number) => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/announcements/teachers/${resolvedTeacherId}/${announcementId}`, {
            method: 'DELETE',
        })
    },
    // 查询教师发布的公告列表
    getByTeacher: (teacherId: number | null = null, params: any = {}) => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        const query = new URLSearchParams(params).toString()
        return request(`/announcements/teachers/${resolvedTeacherId}?${query}`)
    },
    // 获取公告阅读统计
    getStats: (id: number) => request(`/announcements/${id}/stats`),
    // 记录用户阅读公告（默认以网关注入身份为准）
    recordRead: (id: number, userId: number | null = null) => {
        const query = userId ? `?userId=${userId}` : ''
        return request(`/announcements/${id}/read${query}`, {
            method: 'POST',
        })
    },
    // 置顶/取消置顶公告
    togglePin: (teacherId: number | null = null, announcementId: number) => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/announcements/teachers/${resolvedTeacherId}/${announcementId}/toggle-pin`, {
            method: 'POST',
        })
    },
}

export const notificationAPI = {
    // 发送通知给单个用户
    send: (userId: number, title: string, content: string, type = 'NOTIFICATION') =>
        request('/notifications/send', {
            method: 'POST',
            body: JSON.stringify({ userId, title, content, type }),
        }),
    // 批量发送通知
    sendBatch: (userIds: number[], title: string, content: string) =>
        request('/notifications/send-batch', {
            method: 'POST',
            body: JSON.stringify({ userIds, title, content }),
        }),
    // 检查用户是否在线
    isOnline: (userId: number) => request(`/notifications/online/${userId}`),
}
