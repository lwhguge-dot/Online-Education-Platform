import { ref } from 'vue'
import { commentAPI, homeworkAPI, chapterCommentAPI, chapterAPI } from '../services/api'
import { useToastStore } from '../stores/toast'
import { formatDateTimeCN } from '../utils/datetime'

export function useStudentQuestions() {
    const questions = ref([])
    const loading = ref(false)
    const toast = useToastStore()

    const normalizeTime = (timeValue) => {
        if (!timeValue) return ''
        const s = String(timeValue)
        return s.includes('T') ? s.replace('T', ' ') : s
    }

    const parseTime = (timeValue) => {
        if (!timeValue) return 0
        const raw = String(timeValue)
        const normalized = /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}/.test(raw) ? raw.replace(' ', 'T') : raw
        const t = Date.parse(normalized)
        return Number.isNaN(t) ? 0 : t
    }

    const loadQuestions = async (studentId) => {
        if (!studentId) return
        loading.value = true
        try {
            const [commentRes, homeworkRes, chapterCommentRes] = await Promise.allSettled([
                commentAPI.getStudentQuestions(studentId),
                homeworkAPI.getStudentQuestions(studentId),
                chapterCommentAPI.getStudentQuestions(studentId)
            ])

            const commentQuestions =
                commentRes.status === 'fulfilled' && commentRes.value?.code === 200 && Array.isArray(commentRes.value?.data)
                    ? commentRes.value.data.map(q => ({
                        id: q.id ?? q.questionId,
                        title: q.title,
                        content: q.content || q.answerContent,
                        time: normalizeTime(q.time || q.answeredAt || q.createdAt),
                        commentCount: Number(q.commentCount || 0),
                        hasReply: Boolean(q.hasReply),
                        replies: q.replies
                    }))
                    : []

            const homeworkQuestions =
                homeworkRes.status === 'fulfilled' && homeworkRes.value?.code === 200 && Array.isArray(homeworkRes.value?.data)
                    ? homeworkRes.value.data.map(q => {
                        const hasTeacherReply = q.teacherReply != null && String(q.teacherReply).trim() !== ''
                        return {
                            id: `homework-${q.id}`,
                            title: q.homeworkTitle ? `作业：${q.homeworkTitle}` : '作业提问',
                            content: q.questionContent,
                            time: normalizeTime(q.repliedAt || q.createdAt),
                            commentCount: hasTeacherReply ? 1 : 0,
                            hasReply: hasTeacherReply,
                            replies: hasTeacherReply
                                ? [
                                    {
                                        teacherName: '教师',
                                        time: normalizeTime(q.repliedAt || q.createdAt),
                                        content: q.teacherReply
                                    }
                                ]
                                : []
                        }
                    })
                    : []

            const chapterCommentQuestions =
                chapterCommentRes.status === 'fulfilled' && chapterCommentRes.value?.code === 200 && Array.isArray(chapterCommentRes.value?.data)
                    ? chapterCommentRes.value.data.map(q => ({
                        id: `chapter-${q.id ?? Date.now()}`,
                        title: q.title || '章节提问',
                        content: q.content,
                        courseName: q.courseName,
                        chapterName: q.chapterName,
                        time: normalizeTime(q.time || q.createdAt),
                        commentCount: Number(q.commentCount || 0),
                        hasReply: Boolean(q.hasReply),
                        replies: q.replies
                    }))
                    : []

            questions.value = [...homeworkQuestions, ...commentQuestions, ...chapterCommentQuestions]
                .sort((a, b) => parseTime(b.time) - parseTime(a.time))

        } catch (e) {
            console.error('加载我的提问失败:', e)
        } finally {
            loading.value = false
        }
    }

    const submitQuestion = async (studentId, data) => {
        if (!studentId) return false

        try {
            // Construct the content with image if present
            let mergedContent = data.content
            if (data.imageUrl) {
                mergedContent += `\n![image](${data.imageUrl})`
            }

            const res = await chapterCommentAPI.createComment({
                courseId: Number(data.courseId),
                chapterId: Number(data.chapterId),
                content: mergedContent,
                parentId: null
            })

            if (res.code === 200 && res.data) {
                toast.success('提问成功，等待老师回复')

                // Optimistic update
                questions.value.unshift({
                    id: `chapter-${res.data.id ?? Date.now()}`,
                    title: data.title || '章节提问',
                    content: mergedContent,
                    courseName: data.courseName,
                    chapterName: data.chapterName,
                    time: formatDateTimeCN(new Date()),
                    commentCount: 0,
                    hasReply: false,
                    replies: []
                })
                return true
            }
        } catch (e) {
            console.error('提交问题失败:', e)
            toast.error('提问失败，请稍后重试')
            return false
        }
    }

    return {
        questions,
        loading,
        loadQuestions,
        submitQuestion
    }
}
