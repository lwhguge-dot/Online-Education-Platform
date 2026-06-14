import { defineStore } from 'pinia'
import { ref, readonly } from 'vue'
import type { Ref } from 'vue'
import { enrollmentAPI, courseAPI, chapterAPI, progressAPI } from '../services/api'
import { formatDateCN } from '../utils/datetime'
import { getSubjectColor } from '../utils/subject'
import { logger } from '../utils/logger'

interface CourseChapter {
  id: number
  title: string
  completed: boolean
}

export interface EnrolledCourse {
  id: number
  title: string
  teacher: string
  progress: number
  totalChapters: number
  completedChapters: number
  lastStudy: string
  chapters: CourseChapter[]
  subject: string
  color: string
  coverImage: string
  rating: number
  students: number
  lastChapterId: number | null
  lastChapterTitle: string | null
  lastPosition: number
  hasNewChapters: boolean
}

interface RecentCourse {
  id: number
  name: string
  progress: number
  lastChapter: string
  lastStudy: string
  lastChapterId: number | null
  lastPosition: number
}

interface AvailableCourse {
  id: number
  title: string
  teacher: string
  subject: string
  color: string
  coverImage: string
  rating: number
  students: number
}

interface TimelineEntry {
  title: string
  time: string
  action: string
  courseId: number
  chapterId: number
  chapterTitle: string
}

const formatTime = (dateStr: string | null | undefined): string => {
  return formatDateCN(dateStr, '暂无')
}

export const useStudentCourseStore = defineStore('student-courses', () => {
  const enrolledCourses: Ref<EnrolledCourse[]> = ref([])
  const recentCourses: Ref<RecentCourse[]> = ref([])
  const availableCourses: Ref<AvailableCourse[]> = ref([])
  const timeline: Ref<TimelineEntry[]> = ref([])
  const loading: Ref<boolean> = ref(false)
  const actionLoading: Ref<boolean> = ref(false)

  async function loadEnrolledCourses(studentId: number | null | undefined): Promise<void> {
    if (!studentId) return
    loading.value = true
    try {
      const res = await enrollmentAPI.getStudentEnrollments(studentId)
      const enrollmentList: Array<{ courseId: number; progress?: number; lastStudyAt?: string }> = Array.isArray(res.data) ? res.data : []
      if (enrollmentList.length > 0) {
        const coursesData = await Promise.all(
          enrollmentList.map(async (enrollment) => {
            try {
              const courseRes = await courseAPI.getById(enrollment.courseId)
              if (!courseRes.data) return null
              const courseData = courseRes.data

              let hasNewChapters = false
              try {
                const newChapterRes = await enrollmentAPI.checkNewChapters(enrollment.courseId, studentId)
                if (newChapterRes.data) {
                  hasNewChapters = Boolean((newChapterRes.data as { hasNewChapters: boolean }).hasNewChapters)
                }
              } catch (error) {
                logger.error(`检查新章节失败(courseId=${enrollment.courseId}):`, error)
              }

              return {
                id: enrollment.courseId,
                title: courseData.title || '未知课程',
                teacher: courseData.teacherName || '未知教师',
                progress: Number(enrollment.progress || 0),
                totalChapters: 0,
                completedChapters: 0,
                lastStudy: enrollment.lastStudyAt ? formatTime(enrollment.lastStudyAt) : '暂无记录',
                chapters: [],
                subject: String(courseData.subject || ''),
                color: getSubjectColor(courseData.subject),
                coverImage: String(courseData.coverImage || courseData.cover || ''),
                rating: Number(courseData.rating || 4.5),
                students: Number(courseData.studentCount || 0),
                lastChapterId: null,
                lastChapterTitle: null,
                lastPosition: 0,
                hasNewChapters,
              }
            } catch (err) {
              logger.error(`加载课程详情失败(courseId=${enrollment.courseId}):`, err)
              return null
            }
          })
        )

        enrolledCourses.value = coursesData.filter((course): course is EnrolledCourse => course !== null)

        await Promise.all(
          enrolledCourses.value.map(async (course) => {
            try {
              const [chaptersRes, progressRes] = await Promise.all([
                chapterAPI.getByCourse(course.id),
                progressAPI.getCourseProgress(course.id, studentId)
              ])

              const chapterList: Array<{ id: number | string; title?: string }> = Array.isArray(chaptersRes.data) ? chaptersRes.data : []
              const progressList: Array<{ chapterId: number; isCompleted?: number; lastPosition?: number; lastUpdateTime?: string }> = Array.isArray(progressRes.data) ? progressRes.data : []
              const progressMap: Record<number, { chapterId: number; isCompleted?: number; lastPosition?: number; lastUpdateTime?: string }> = {}
              let lastStudiedChapter: { chapterId: number; lastPosition?: number } | null = null
              let lastStudyTime: string | null = null

              for (const p of progressList) {
                const chapterId = Number(p.chapterId)
                if (Number.isNaN(chapterId)) continue
                progressMap[chapterId] = { ...p, chapterId }
                if (p.lastUpdateTime && (!lastStudyTime || new Date(p.lastUpdateTime) > new Date(lastStudyTime))) {
                  lastStudyTime = p.lastUpdateTime
                  lastStudiedChapter = { ...p, chapterId }
                }
              }

              course.chapters = chapterList.map((ch) => {
                const chapterId = Number(ch.id)
                return {
                  id: Number.isNaN(chapterId) ? 0 : chapterId,
                  title: ch.title || '未命名章节',
                  completed: progressMap[chapterId]?.isCompleted === 1
                }
              })
              course.totalChapters = course.chapters.length
              course.completedChapters = course.chapters.filter((c) => c.completed).length

              if (lastStudiedChapter) {
                course.lastChapterId = lastStudiedChapter.chapterId
                course.lastPosition = Number(lastStudiedChapter.lastPosition || 0)
                const chapterInfo = course.chapters.find((ch) => ch.id === lastStudiedChapter!.chapterId)
                course.lastChapterTitle = chapterInfo?.title || '未知章节'
              }
            } catch (err) {
              logger.error(`加载课程章节进度失败(courseId=${course.id}):`, err)
            }
          })
        )

        recentCourses.value = enrolledCourses.value.slice(0, 3).map((c): RecentCourse => {
          const lastChapter = c.chapters.length > 0 ? c.chapters[c.chapters.length - 1] : undefined
          return {
            id: c.id,
            name: c.title,
            progress: c.progress,
            lastChapter: c.lastChapterTitle || lastChapter?.title || '暂无章节',
            lastStudy: c.lastStudy,
            lastChapterId: c.lastChapterId,
            lastPosition: c.lastPosition
          }
        })

        const timelineEntries: TimelineEntry[] = []
        for (const course of enrolledCourses.value) {
          course.chapters.filter((ch) => ch.completed).forEach((ch) => {
            timelineEntries.push({
              title: course.title,
              time: course.lastStudy || '本周',
              action: `完成章节: ${ch.title}`,
              courseId: course.id,
              chapterId: ch.id,
              chapterTitle: ch.title
            })
          })
        }
        timeline.value = timelineEntries.slice(0, 5)
      } else {
        enrolledCourses.value = []
        recentCourses.value = []
        timeline.value = []
      }
    } catch (e) {
      logger.error('List enrollments failed', e)
    } finally {
      loading.value = false
    }
  }

  async function loadAvailableCourses(): Promise<void> {
    try {
      const res = await courseAPI.getAll()
      if (res.data) {
        const enrolledIds = new Set<number>(enrolledCourses.value.map((c) => c.id))
        const courseList = Array.isArray(res.data) ? res.data : []
        availableCourses.value = courseList
          .filter((c) => !enrolledIds.has(Number(c.id)))
          .map((c): AvailableCourse => ({
            id: Number(c.id),
            title: c.title || '未知课程',
            teacher: c.teacherName || '未知教师',
            subject: c.subject || '',
            color: getSubjectColor(c.subject),
            coverImage: c.coverImage || '',
            rating: 4.5,
            students: 0
          }))
      }
    } catch (e) {
      logger.error('Load available courses failed', e)
    }
  }

  async function enrollCourse(courseId: number, studentId: number): Promise<void> {
    actionLoading.value = true
    try {
      await enrollmentAPI.enroll(courseId, studentId)
      await loadEnrolledCourses(studentId)
      await loadAvailableCourses()
    } finally {
      actionLoading.value = false
    }
  }

  async function dropCourse(courseId: number, studentId: number): Promise<void> {
    actionLoading.value = true
    try {
      await enrollmentAPI.drop(courseId, studentId)
      await loadEnrolledCourses(studentId)
      await loadAvailableCourses()
    } finally {
      actionLoading.value = false
    }
  }

  function reset(): void {
    enrolledCourses.value = []
    recentCourses.value = []
    availableCourses.value = []
    timeline.value = []
    loading.value = false
    actionLoading.value = false
  }

  return {
    enrolledCourses: readonly(enrolledCourses),
    recentCourses: readonly(recentCourses),
    availableCourses: readonly(availableCourses),
    timeline: readonly(timeline),
    loading: readonly(loading),
    actionLoading: readonly(actionLoading),
    loadEnrolledCourses,
    loadAvailableCourses,
    enrollCourse,
    dropCourse,
    reset
  }
})
