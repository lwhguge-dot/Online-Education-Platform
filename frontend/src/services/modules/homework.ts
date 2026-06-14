import { request, resolveUserId } from '../request'
import type { Result, Homework, HomeworkSubmission } from '../../types/api'

export const homeworkAPI = {
    create: (homework: { chapterId: number; title: string; description: string; deadline?: string; homeworkType?: string }): Promise<Result<Homework>> => request<Homework>('/homeworks', { method: 'POST', body: JSON.stringify(homework) }),
    getDetail: (id: number): Promise<Result<Homework>> => request<Homework>(`/homeworks/${id}`),
    getByChapter: (chapterId: number): Promise<Result<Homework[]>> => request<Homework[]>(`/homeworks/chapter/${chapterId}`),
    getStudentHomeworks: (chapterId: number, studentId: number | null = null): Promise<Result<Array<{ homework: Homework; submission: HomeworkSubmission | null; submitted: boolean }>>> => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/homeworks/student?studentId=${resolvedStudentId}&chapterId=${chapterId}`)
    },
    unlock: (studentId: number, chapterId: number): Promise<Result<void>> => request(`/homeworks/unlock?studentId=${studentId}&chapterId=${chapterId}`, { method: 'POST' }),
    submit: (data: { homeworkId: number; studentId: number; content: string; answers?: number[] }): Promise<Result<HomeworkSubmission>> => request<HomeworkSubmission>('/homeworks/submit', { method: 'POST', body: JSON.stringify(data) }),
    getSubmission: (homeworkId: number, studentId: number | null = null): Promise<Result<HomeworkSubmission | null>> => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/homeworks/${homeworkId}/submission?studentId=${resolvedStudentId}`)
    },
    getReport: (homeworkId: number, studentId: number | null = null): Promise<Result<{ homework: Homework; submission: HomeworkSubmission | null; questions: unknown[] }>> => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/homeworks/${homeworkId}/report?studentId=${resolvedStudentId}`)
    },
    getSubmissions: (homeworkId: number): Promise<Result<HomeworkSubmission[]>> => request<HomeworkSubmission[]>(`/homeworks/${homeworkId}/submissions`),
    gradeSubjective: (submissionId: number, questionId: number, score: number, feedback: string): Promise<Result<void>> =>
        request(`/homeworks/grade-subjective?submissionId=${submissionId}&questionId=${questionId}&score=${score}&feedback=${feedback || ''}`, { method: 'POST' }),
    getTeacherTodos: (teacherId: number | null = null): Promise<Result<Array<{ id: number; title: string; count: number; type: string }>>> => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/homeworks/teacher/${resolvedTeacherId}/todos`)
    },
    getTeacherActivities: (teacherId: number | null = null): Promise<Result<Array<{ type: string; title: string; time: string }>>> => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/homeworks/teacher/${resolvedTeacherId}/activities`)
    },
    getPendingSubmissions: (homeworkId: number): Promise<Result<HomeworkSubmission[]>> => request<HomeworkSubmission[]>(`/homeworks/${homeworkId}/submissions/pending`),
    getSubmissionDetail: (submissionId: number): Promise<Result<HomeworkSubmission>> => request<HomeworkSubmission>(`/homeworks/submissions/${submissionId}/detail`),
    gradeSubmission: (submissionId: number, grades: { score: number; feedback: string }): Promise<Result<void>> =>
        request(`/homeworks/submissions/${submissionId}/grade`, {
            method: 'POST',
            body: JSON.stringify(grades)
        }),
    duplicate: (id: number, chapterId: number, title: string): Promise<Result<Homework>> =>
        request<Homework>(`/homeworks/${id}/duplicate`, {
            method: 'POST',
            body: JSON.stringify({ chapterId, title }),
        }),
    importQuestions: (homeworkId: number, questions: Array<{ question: string; options: string[]; correctAnswer: number; explanation?: string }>): Promise<Result<void>> =>
        request(`/homeworks/${homeworkId}/import-questions`, {
            method: 'POST',
            body: JSON.stringify({ questions }),
        }),
    askQuestion: (homeworkId: number, questionId: number | null, content: string, studentId: number | null = null): Promise<Result<{ id: number }>> => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/homeworks/${homeworkId}/questions?studentId=${resolvedStudentId}${questionId ? `&questionId=${questionId}` : ''}&content=${encodeURIComponent(content)}`, {
            method: 'POST',
        })
    },
    replyQuestion: (discussionId: number, reply: string, teacherId: number | null = null): Promise<Result<void>> => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/homeworks/questions/${discussionId}/reply?teacherId=${resolvedTeacherId}&reply=${encodeURIComponent(reply)}`, {
            method: 'POST',
        })
    },
    getHomeworkQuestions: (homeworkId: number): Promise<Result<Array<{ id: number; questionContent: string; teacherReply: string | null; createdAt: string; repliedAt: string | null }>>> =>
        request(`/homeworks/${homeworkId}/questions`),
    getStudentQuestions: (studentId: number | null = null): Promise<Result<Array<{ id: number; questionContent: string; teacherReply: string | null; createdAt: string; repliedAt: string | null; homeworkTitle: string }>>> => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/homeworks/student/${resolvedStudentId}/questions`)
    },
    getTeacherPendingQuestionsCount: (teacherId: number | null = null): Promise<Result<{ count: number }>> => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/homeworks/teacher/${resolvedTeacherId}/pending-questions-count`)
    },
}
