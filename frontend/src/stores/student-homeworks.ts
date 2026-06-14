import { defineStore } from 'pinia'
import { ref, readonly } from 'vue'
import type { Ref } from 'vue'
import { homeworkAPI } from '../services/api'
import { formatDateCN } from '../utils/datetime'
import { logger } from '../utils/logger'
import type { EnrolledCourse } from './student-courses'

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

export const useStudentHomeworkStore = defineStore('student-homeworks', () => {
  const pendingHomeworks: Ref<HomeworkListItem[]> = ref([])
  const completedHomeworks: Ref<HomeworkListItem[]> = ref([])
  const urgentHomeworks: Ref<HomeworkListItem[]> = ref([])
  const todayTasks: Ref<TodayTaskItem[]> = ref([])
  const activities: Ref<HomeworkActivity[]> = ref([])
  const loading: Ref<boolean> = ref(false)

  async function loadHomeworks(
    studentId: number | null | undefined,
    enrolledCourses: EnrolledCourse[] | null | undefined
  ): Promise<void> {
    if (!studentId || !enrolledCourses) return
    loading.value = true

    try {
      const pending: HomeworkListItem[] = []
      const completed: HomeworkListItem[] = []

      const chapterTasks: Array<{ course: { title: string }; chapter: { id: number; completed?: boolean } }> = []
      for (const course of enrolledCourses) {
        if (!course.chapters) continue
        for (const chapter of course.chapters) {
          if (!chapter.completed) continue
          chapterTasks.push({ course, chapter })
        }
      }

      const results = await Promise.allSettled(
        chapterTasks.map(({ course, chapter }) =>
          homeworkAPI.getStudentHomeworks(chapter.id, studentId).then((res) => ({
            courseTitle: course.title,
            data: res.data,
          }))
        )
      )

      results.forEach((result, index) => {
        const task = chapterTasks[index]
        if (result.status !== 'fulfilled') {
          logger.error(`加载作业失败(chapterId=${task?.chapter.id}):`, result.reason)
          return
        }

        const { courseTitle, data } = result.value
        if (!data) return

        data.forEach((hw: Record<string, unknown>) => {
          const homework = hw.homework as Record<string, unknown> | undefined
          const submission = hw.submission as Record<string, unknown> | undefined
          const item: HomeworkListItem = {
            id: Number(homework?.id || 0),
            title: (homework?.title as string) || '未命名作业',
            course: courseTitle,
            type: (homework?.homeworkType as string) || 'objective',
            daysLeft: homework?.deadline ? Math.ceil((new Date(homework.deadline as string).getTime() - Date.now()) / (86400000)) : null,
            submitTime: (submission?.submittedAt as string) || null,
            totalScore: typeof submission?.totalScore === 'number' ? submission.totalScore as number : null,
            status: submission?.submitStatus === 'graded' ? 'graded' :
              (submission ? 'submitted' : 'pending'),
            unlocked: true
          }

          if (hw.submitted && submission) {
            completed.push(item)
          } else {
            pending.push(item)
          }
        })
      })

      pendingHomeworks.value = pending
      completedHomeworks.value = completed

      todayTasks.value = pending.slice(0, 3).map((hw): TodayTaskItem => ({
        id: hw.id, title: `${hw.course} - ${hw.title}`,
        deadline: hw.daysLeft !== null ? (hw.daysLeft <= 0 ? '已截止' : `${hw.daysLeft}天后`) : '无截止',
        urgent: hw.daysLeft !== null && hw.daysLeft <= 1
      }))

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
      logger.error('加载作业列表失败:', e)
    } finally {
      loading.value = false
    }
  }

  function reset(): void {
    pendingHomeworks.value = []
    completedHomeworks.value = []
    urgentHomeworks.value = []
    todayTasks.value = []
    activities.value = []
    loading.value = false
  }

  return {
    pendingHomeworks: readonly(pendingHomeworks),
    completedHomeworks: readonly(completedHomeworks),
    urgentHomeworks: readonly(urgentHomeworks),
    todayTasks: readonly(todayTasks),
    activities: readonly(activities),
    loading: readonly(loading),
    loadHomeworks,
    reset
  }
})
