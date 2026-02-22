import { ref } from 'vue'
import { homeworkAPI } from '../services/api'
import { formatDateCN } from '../utils/datetime'

interface EnrolledChapter {
    id: number
    completed?: boolean
}

interface EnrolledCourse {
    title: string
    chapters?: EnrolledChapter[]
}

interface HomeworkListItem {
    id: number
    title: string
    course: string
    type: string
    daysLeft: number | null
    submitTime: string | null
    totalScore: number | null
    status: 'graded' | 'submitted' | 'pending'
    unlocked: boolean
}

interface TodayTaskItem {
    id: number
    title: string
    deadline: string
    urgent: boolean
}

interface HomeworkActivity {
    type: 'grade'
    title: string
    time: string
    score: number | null
    action: 'homework'
}

export function useStudentHomeworks() {
    const pendingHomeworks = ref<HomeworkListItem[]>([])
    const completedHomeworks = ref<HomeworkListItem[]>([])
    const urgentHomeworks = ref<HomeworkListItem[]>([])
    const todayTasks = ref<TodayTaskItem[]>([])
    const activities = ref<HomeworkActivity[]>([])
    const loading = ref(false)

    const loadHomeworks = async (
        studentId: number | null | undefined,
        enrolledCourses: EnrolledCourse[] | null | undefined
    ): Promise<void> => {
        if (!studentId || !enrolledCourses) return
        loading.value = true

        try {
            const pending: HomeworkListItem[] = []
            const completed: HomeworkListItem[] = []

            for (const course of enrolledCourses) {
                if (!course.chapters) continue
                for (const chapter of course.chapters) {
                    // 只有已完成的章节才显示作业
                    if (!chapter.completed) continue

                    try {
                        // 真实业务中应由聚合 API 直接返回学生作业列表
                        // Warning: N+1 problem here, ideally refactor backend later
                        const res = await homeworkAPI.getStudentHomeworks(chapter.id, studentId)
                        if (res.data) {
                            res.data.forEach((hw: any) => {
                                const item: HomeworkListItem = {
                                    id: Number(hw.homework?.id || 0),
                                    title: hw.homework?.title || '未命名作业',
                                    course: course.title,
                                    type: hw.homework?.homeworkType || 'objective',
                                    daysLeft: hw.homework?.deadline ? Math.ceil((new Date(hw.homework.deadline).getTime() - Date.now()) / (86400000)) : null,
                                    submitTime: hw.submission?.submittedAt || null,
                                    totalScore: typeof hw.submission?.totalScore === 'number' ? hw.submission.totalScore : null,
                                    status: hw.submission?.submitStatus === 'graded' ? 'graded' :
                                        (hw.submission ? 'submitted' : 'pending'),
                                    unlocked: true // If we are here, chapter is completed/unlocked
                                }

                                if (hw.submitted && hw.submission) {
                                    completed.push(item)
                                } else {
                                    pending.push(item)
                                }
                            })
                        }
                    } catch (error) {
                        console.error(`加载作业失败(chapterId=${chapter.id}):`, error)
                    }
                }
            }

            pendingHomeworks.value = pending
            completedHomeworks.value = completed

            todayTasks.value = pending.slice(0, 3).map((hw): TodayTaskItem => ({
                id: hw.id, title: `${hw.course} - ${hw.title}`,
                deadline: hw.daysLeft !== null ? (hw.daysLeft <= 0 ? '已截止' : `${hw.daysLeft}天后`) : '无截止',
                urgent: hw.daysLeft !== null && hw.daysLeft <= 1
            }))

            // 由已批改作业生成活动流
            const gradedActivities = completed
                .filter(hw => hw.status === 'graded')
                .slice(0, 5)
                .map((hw): HomeworkActivity => ({
                    type: 'grade',
                    title: `${hw.title} 已批改`,
                    time: hw.submitTime ? formatDateCN(hw.submitTime, '刚刚') : '刚刚',
                    score: hw.totalScore,
                    action: 'homework'
                }))
            activities.value = gradedActivities

        } catch (e) {
            console.error('加载作业列表失败:', e)
        } finally {
            loading.value = false
        }
    }

    return {
        pendingHomeworks,
        completedHomeworks,
        urgentHomeworks, // Logic for urgentHomeworks was in stats in original, but here redundant?
        todayTasks,
        activities,
        loading,
        loadHomeworks
    }
}
