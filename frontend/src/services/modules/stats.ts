import { request, resolveUserId } from '../request'

export const statsAPI = {
    // 教师仪表盘统计（带课程数据）
    getTeacherDashboard: (teacherId: number | null = null, courses: any[] = []) => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        if (courses.length > 0) {
            return request(`/stats/teacher/dashboard?teacherId=${resolvedTeacherId}`, {
                method: 'POST',
                body: JSON.stringify(courses),
            })
        }
        return request(`/stats/teacher/dashboard?teacherId=${resolvedTeacherId}`)
    },

    // 教师待办事项
    getTeacherTodos: (teacherId: number | null = null) => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/homeworks/teacher/${resolvedTeacherId}/todos`)
    },

    // 教师最近活动
    getTeacherActivities: (teacherId: number | null = null) => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/homeworks/teacher/${resolvedTeacherId}/activities`)
    },

    // 管理员仪表盘
    getAdminDashboard: () => request('/stats/admin/dashboard'),

    // 学生学习统计
    getStudentDashboard: (studentId: number | null = null) => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/stats/student/${resolvedStudentId}/dashboard`)
    },

    // 获取教师今日新增学生数
    getTeacherTodayEnrollments: (teacherId: number | null = null) => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/enrollments/teacher/${resolvedTeacherId}/today`)
    },

    // 获取用户增长趋势数据（管理员仪表盘图表）
    getUserTrends: (days = 7) =>
        request(`/stats/admin/user-trends?days=${days}`),
}

export const progressAPI = {
    // 上报视频进度（自动携带时间戳用于防作弊校验）
    reportVideo: (data: any) => request('/progress/video/report', {
        method: 'POST',
        body: JSON.stringify({ ...data, clientTimestamp: Date.now() })
    }),
    submitQuiz: (data: any) => request('/progress/quiz/submit', { method: 'POST', body: JSON.stringify(data) }),
    getChapterProgress: (chapterId: number, studentId: number | null = null) => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/progress/chapter/${chapterId}?studentId=${resolvedStudentId}`)
    },
    getCourseProgress: (courseId: number, studentId: number | null = null) => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/progress/course/${courseId}?studentId=${resolvedStudentId}`)
    },
    checkUnlock: (chapterId: number, studentId: number | null = null) => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/progress/check-unlock?studentId=${resolvedStudentId}&chapterId=${chapterId}`)
    },
    // 更新进度（自动携带时间戳用于防作弊校验）
    updateProgress: (data: any) => request('/progress/video/report', {
        method: 'POST',
        body: JSON.stringify({ ...data, clientTimestamp: Date.now() })
    }),
    // 学习轨迹和知识点掌握度（真实数据）
    getLearningTrack: (studentId: number | null = null) => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/progress/student/${resolvedStudentId}/learning-track`)
    },
    getKnowledgeMastery: (studentId: number | null = null) => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/progress/student/${resolvedStudentId}/mastery`)
    },
    // 教师端：获取学生在特定课程的学习轨迹
    getLearningTrajectory: (courseId: number, studentId: number) => request(`/progress/course/${courseId}/student/${studentId}/trajectory`),
    // 教师端：获取学生在特定课程的测验分数趋势
    getQuizScoreTrend: (courseId: number, studentId: number) => request(`/progress/course/${courseId}/student/${studentId}/quiz-trend`),
    // 教师端：获取学生在特定课程的详细学情分析
    getStudentCourseAnalytics: (courseId: number, studentId: number) => request(`/progress/course/${courseId}/student/${studentId}/analytics`),
    // 教师端：获取课程分析数据
    getCourseAnalytics: (courseId: number) => request(`/progress/course/${courseId}/analytics`),
}

export const badgeAPI = {
    // 获取学生徽章（包含已解锁和未解锁）
    getStudentBadges: (studentId: number | null = null) => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/progress/badges/student/${resolvedStudentId}`)
    },

    // 检查并授予符合条件的徽章
    checkAndAwardBadges: (studentId: number | null = null) => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/progress/badges/student/${resolvedStudentId}/check`, {
            method: 'POST',
        })
    },
}

export const auditLogAPI = {
    // 分页查询审计日志
    getList: (params: any = {}) => {
        const query = new URLSearchParams(params).toString()
        return request(`/audit-logs?${query}`)
    },
    // 根据ID查询审计日志详情
    getById: (id: number) => request(`/audit-logs/${id}`),
}
