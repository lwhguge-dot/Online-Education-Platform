import { ref } from 'vue'
import { homeworkAPI } from '../services/api'
import { formatDateCN } from '../utils/datetime'

export function useStudentHomeworks() {
    const pendingHomeworks = ref([])
    const completedHomeworks = ref([])
    const urgentHomeworks = ref([])
    const todayTasks = ref([])
    const activities = ref([])
    const loading = ref(false)

    const loadHomeworks = async (studentId, enrolledCourses) => {
        if (!studentId || !enrolledCourses) return
        loading.value = true

        try {
            const pending = []
            const completed = []

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
                            res.data.forEach(hw => {
                                const item = {
                                    id: hw.homework?.id,
                                    title: hw.homework?.title,
                                    course: course.title,
                                    type: hw.homework?.homeworkType || 'objective',
                                    daysLeft: hw.homework?.deadline ? Math.ceil((new Date(hw.homework.deadline).getTime() - Date.now()) / (86400000)) : null,
                                    submitTime: hw.submission?.submittedAt,
                                    totalScore: hw.submission?.totalScore,
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
                    } catch (e) {
                        // console.error(`加载作业失败(courseId=${course.id}, chapterId=${chapter.id}):`, e)
                    }
                }
            }

            pendingHomeworks.value = pending
            completedHomeworks.value = completed

            todayTasks.value = pending.slice(0, 3).map(hw => ({
                id: hw.id, title: `${hw.course} - ${hw.title}`,
                deadline: hw.daysLeft !== null ? (hw.daysLeft <= 0 ? '已截止' : `${hw.daysLeft}天后`) : '无截止',
                urgent: hw.daysLeft !== null && hw.daysLeft <= 1
            }))

            // 由已批改作业生成活动流
            const gradedActivities = completed
                .filter(hw => hw.status === 'graded')
                .slice(0, 5)
                .map(hw => ({
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
