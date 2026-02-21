import { request, requestBlob, resolveUserId } from '../request'

export const enrollmentAPI = {
    enroll: (courseId: number, studentId: number | null = null) => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request('/enrollments', {
            method: 'POST',
            body: JSON.stringify({ courseId, studentId: resolvedStudentId }),
        })
    },

    drop: (courseId: number, studentId: number | null = null) => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/enrollments?courseId=${courseId}&studentId=${resolvedStudentId}`, {
            method: 'DELETE',
        })
    },

    checkEnrollment: (courseId: number, studentId: number | null = null) => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/enrollments/check?studentId=${resolvedStudentId}&courseId=${courseId}`)
    },

    getStudentEnrollments: (studentId: number | null = null) => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/enrollments/student/${resolvedStudentId}`)
    },

    getStudentEnrollmentsWithNewChapters: (studentId: number | null = null) => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/enrollments/student/${resolvedStudentId}/with-new-chapters`)
    },

    checkNewChapters: (courseId: number, studentId: number | null = null) => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/enrollments/check-new-chapters?studentId=${resolvedStudentId}&courseId=${courseId}`)
    },

    getCourseEnrollments: (courseId: number) =>
        request(`/enrollments/course/${courseId}`),

    updateProgress: (courseId: number, progress: number, studentId: number | null = null) => {
        const resolvedStudentId = resolveUserId(studentId, '学生')
        return request(`/enrollments/progress?studentId=${resolvedStudentId}&courseId=${courseId}&progress=${progress}`, {
            method: 'PUT',
        })
    },

    // 获取教师所有课程的学生列表（聚合数据，支持分页）
    getTeacherStudents: (teacherId: number | null = null, page = 1, size = 20) => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/enrollments/teacher/${resolvedTeacherId}/students?page=${page}&size=${size}`)
    },

    // 获取教师所有课程的学生概览（按课程分组）
    getTeacherStudentsOverview: (teacherId: number | null = null) => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        return request(`/enrollments/teacher/${resolvedTeacherId}/students/overview`)
    },

    // 获取课程学生列表（含学情状态）
    getCourseStudentsWithStatus: (courseId: number, page = 1, size = 20, status = 'all') =>
        request(`/enrollments/course/${courseId}/students?page=${page}&size=${size}&status=${status}`),

    // 导出教师学生数据CSV
    exportStudentsCSV: async (teacherId: number | null = null, courseId: number | null = null) => {
        const resolvedTeacherId = resolveUserId(teacherId, '教师')
        const url = courseId
            ? `/enrollments/teacher/${resolvedTeacherId}/students/export?courseId=${courseId}`
            : `/enrollments/teacher/${resolvedTeacherId}/students/export`
        // 统一走请求层，确保导出接口行为与其他接口一致
        const { blob, filename } = await requestBlob(url)
        const blobUrl = window.URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = blobUrl
        a.download = filename || `students_${Date.now()}.csv`
        document.body.appendChild(a)
        a.click()
        window.URL.revokeObjectURL(blobUrl)
        a.remove()
    },
}
