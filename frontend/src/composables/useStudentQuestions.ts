import { ref, readonly } from 'vue'
import { commentAPI, homeworkAPI, chapterCommentAPI } from '../services/api'
import { useToastStore } from '../stores/toast'
import { formatDateTimeCN, parseToTimestamp } from '../utils/datetime'
import { logger } from '../utils/logger'

type QuestionId = string | number

interface Reply {
    teacherName?: string
    time?: string
    content?: string
    [key: string]: unknown
}

interface QuestionItem {
    id: QuestionId
    title: string
    content: string
    time: string
    commentCount: number
    hasReply: boolean
    replies: Reply[]
    courseName?: string
    chapterName?: string
}

interface SubmitQuestionPayload {
    title?: string
    content: string
    imageUrl?: string
    courseId: number | string
    chapterId: number | string
    courseName?: string
    chapterName?: string
}

export function useStudentQuestions() {
    const questions = ref<QuestionItem[]>([])
    const loading = ref(false)
    const toast = useToastStore()

    const normalizeTime = (timeValue: unknown): string => {
        if (!timeValue) return ''
        return String(timeValue)
    }

    const parseTime = (timeValue: unknown): number => {
        return parseToTimestamp(timeValue)
    }

    const loadQuestions = async (studentId: number | null | undefined): Promise<void> => {
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
                    ? commentRes.value.data.map((q: Record<string, unknown>): QuestionItem => ({
                        id: (q.id ?? q.questionId) as QuestionId,
                        title: (q.title as string) || '课程提问',
                        content: ((q.content as string) || (q.answerContent as string) || ''),
                        time: normalizeTime(q.time || q.answeredAt || q.createdAt),
                        commentCount: Number(q.commentCount || 0),
                        hasReply: Boolean(q.hasReply),
                        replies: Array.isArray(q.replies) ? (q.replies as Reply[]) : []
                    }))
                    : []

            const homeworkQuestions =
                homeworkRes.status === 'fulfilled' && homeworkRes.value?.code === 200 && Array.isArray(homeworkRes.value?.data)
                    ? homeworkRes.value.data.map((q: Record<string, unknown>): QuestionItem => {
                        const hasTeacherReply = q.teacherReply != null && String(q.teacherReply).trim() !== ''
                        return {
                            id: `homework-${q.id}`,
                            title: q.homeworkTitle ? `作业：${q.homeworkTitle}` : '作业提问',
                            content: (q.questionContent as string) || '',
                            time: normalizeTime(q.repliedAt || q.createdAt),
                            commentCount: hasTeacherReply ? 1 : 0,
                            hasReply: hasTeacherReply,
                            replies: hasTeacherReply
                                ? [
                                    {
                                        teacherName: '教师',
                                        time: normalizeTime(q.repliedAt || q.createdAt),
                                        content: q.teacherReply as string
                                    }
                                ]
                                : []
                        }
                    })
                    : []

            const chapterCommentQuestions =
                chapterCommentRes.status === 'fulfilled' && chapterCommentRes.value?.code === 200 && Array.isArray(chapterCommentRes.value?.data)
                    ? chapterCommentRes.value.data.map((q: Record<string, unknown>): QuestionItem => ({
                        id: `chapter-${(q.id ?? Date.now()) as string | number}`,
                        title: (q.title as string) || '章节提问',
                        content: (q.content as string) || '',
                        courseName: q.courseName as string | undefined,
                        chapterName: q.chapterName as string | undefined,
                        time: normalizeTime(q.time || q.createdAt),
                        commentCount: Number(q.commentCount || 0),
                        hasReply: Boolean(q.hasReply),
                        replies: Array.isArray(q.replies) ? (q.replies as Reply[]) : []
                    }))
                    : []

            questions.value = [...homeworkQuestions, ...commentQuestions, ...chapterCommentQuestions]
                .sort((a, b) => parseTime(b.time) - parseTime(a.time))

        } catch (e) {
            logger.error('加载我的提问失败:', e)
        } finally {
            loading.value = false
        }
    }

    const submitQuestion = async (studentId: number | null | undefined, data: SubmitQuestionPayload): Promise<boolean> => {
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
                const newQuestion: QuestionItem = {
                    id: `chapter-${res.data.id ?? Date.now()}`,
                    title: data.title || '章节提问',
                    content: mergedContent,
                    ...(data.courseName !== undefined ? { courseName: data.courseName } : {}),
                    ...(data.chapterName !== undefined ? { chapterName: data.chapterName } : {}),
                    time: formatDateTimeCN(new Date()),
                    commentCount: 0,
                    hasReply: false,
                    replies: []
                }
                questions.value.unshift(newQuestion)
                return true
            }
        } catch (e) {
            logger.error('提交问题失败:', e)
            toast.error('提问失败，请稍后重试')
        }
        return false
    }

    return {
        questions: readonly(questions),
        loading: readonly(loading),
        loadQuestions,
        submitQuestion
    }
}