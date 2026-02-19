import { request, resolveUserId } from '../request'

export const homeworkAPI = {
    create: (homework: any) => request('/homeworks', { method: 'POST', body: JSON.stringify(homework) }),
    getDetail: (id: number) => request(`/homeworks/${id}`),
    getByChapter: (chapterId: number) => request(`/homeworks/chapter/${chapterId}`),
    getStudentHomeworks: (chapterId: number, studentId: number | null = null) => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/homeworks/student?studentId=${resolvedStudentId}&chapterId=${chapterId}`)
    },
    unlock: (studentId: number, chapterId: number) => request(`/homeworks/unlock?studentId=${studentId}&chapterId=${chapterId}`, { method: 'POST' }),
    submit: (data: any) => request('/homeworks/submit', { method: 'POST', body: JSON.stringify(data) }),
    getSubmission: (homeworkId: number, studentId: number | null = null) => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/homeworks/${homeworkId}/submission?studentId=${resolvedStudentId}`)
    },
    getReport: (homeworkId: number, studentId: number | null = null) => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/homeworks/${homeworkId}/report?studentId=${resolvedStudentId}`)
    },
    getSubmissions: (homeworkId: number) => request(`/homeworks/${homeworkId}/submissions`),
    gradeSubjective: (submissionId: number, questionId: number, score: number, feedback: string) =>
        request(`/homeworks/grade-subjective?submissionId=${submissionId}&questionId=${questionId}&score=${score}&feedback=${feedback || ''}`, { method: 'POST' }),
    // 教师待办事项和活动（真实数据）
    getTeacherTodos: (teacherId: number | null = null) => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/homeworks/teacher/${resolvedTeacherId}/todos`)
    },
    getTeacherActivities: (teacherId: number | null = null) => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/homeworks/teacher/${resolvedTeacherId}/activities`)
    },

    // 批改工作台相关API
    getPendingSubmissions: (homeworkId: number) => request(`/homeworks/${homeworkId}/submissions/pending`),
    getSubmissionDetail: (submissionId: number) => request(`/homeworks/submissions/${submissionId}/detail`),
    gradeSubmission: (submissionId: number, grades: any) =>
        request(`/homeworks/submissions/${submissionId}/grade`, {
            method: 'POST',
            body: JSON.stringify(grades)
        }),

    // 复制作业
    duplicate: (id: number, chapterId: number, title: string) =>
        request(`/homeworks/${id}/duplicate`, {
            method: 'POST',
            body: JSON.stringify({ chapterId, title }),
        }),

    // 批量导入题目
    importQuestions: (homeworkId: number, questions: any[]) =>
        request(`/homeworks/${homeworkId}/import-questions`, {
            method: 'POST',
            body: JSON.stringify({ questions }),
        }),

    // ==================== 作业问答相关API ====================
    // 学生提问
    askQuestion: (homeworkId: number, questionId: number | null, content: string, studentId: number | null = null) => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/homeworks/${homeworkId}/questions?studentId=${resolvedStudentId}${questionId ? `&questionId=${questionId}` : ''}&content=${encodeURIComponent(content)}`, {
            method: 'POST',
        })
    },

    // 教师回复问题
    replyQuestion: (discussionId: number, reply: string, teacherId: number | null = null) => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/homeworks/questions/${discussionId}/reply?teacherId=${resolvedTeacherId}&reply=${encodeURIComponent(reply)}`, {
            method: 'POST',
        })
    },

    // 获取作业的所有问答
    getHomeworkQuestions: (homeworkId: number) =>
        request(`/homeworks/${homeworkId}/questions`),

    // 获取学生的所有提问
    getStudentQuestions: (studentId: number | null = null) => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/homeworks/student/${resolvedStudentId}/questions`)
    },

    // 获取教师待回复的问题数量
    getTeacherPendingQuestionsCount: (teacherId: number | null = null) => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/homeworks/teacher/${resolvedTeacherId}/pending-questions-count`)
    },
}
