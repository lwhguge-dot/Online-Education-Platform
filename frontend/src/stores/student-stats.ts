import { defineStore } from 'pinia'
import { ref, readonly } from 'vue'
import type { Ref } from 'vue'
import { statsAPI, badgeAPI } from '../services/api'
import { logger } from '../utils/logger'

interface WeeklyStudyHour {
  day: string
  hours: number
}

interface DashboardStats {
  enrolledCourses: number
  completedChapters: number
  totalStudyHours: number
  pendingHomework: number
  todayStudyMinutes: number
  streakDays: number
  dailyGoalMinutes: number
  goalAchievedToday: boolean
  thisWeekMinutes: number
  lastWeekMinutes: number
  weeklyChange: number
  weeklyStudyHours: WeeklyStudyHour[]
  lastWeekStudyHours: WeeklyStudyHour[]
}

interface QuizScoreItem {
  courseId?: number
  chapterId?: number
  title: string
  score: number
  time: string
}

interface BadgeItem {
  id: number
  name: string
  iconCode: string
  color: string
  unlocked: boolean
  description: string
  unlockedAt?: string
  progress: number
  currentValue: number
  targetValue: number
  nearUnlock: boolean
}

export const useStudentStatsStore = defineStore('student-stats', () => {
  const dashboardStats: Ref<DashboardStats> = ref({
    enrolledCourses: 0, completedChapters: 0, totalStudyHours: 0,
    pendingHomework: 0, todayStudyMinutes: 0, streakDays: 0,
    dailyGoalMinutes: 60, goalAchievedToday: false,
    thisWeekMinutes: 0, lastWeekMinutes: 0, weeklyChange: 0,
    weeklyStudyHours: [],
    lastWeekStudyHours: []
  })

  const quizzes: Ref<QuizScoreItem[]> = ref([])
  const badges: Ref<BadgeItem[]> = ref([])
  const loading: Ref<boolean> = ref(false)

  async function loadStudentStats(studentId: number | null | undefined): Promise<void> {
    if (!studentId) return
    loading.value = true
    try {
      const res = await statsAPI.getStudentDashboard(studentId)
      if (res.data) {
        const stats = res.data
        dashboardStats.value = {
          enrolledCourses: stats.enrolledCourses || 0,
          completedChapters: stats.completedChapters || 0,
          totalStudyHours: Math.round((stats.totalStudyMinutes || 0) / 60 * 10) / 10,
          pendingHomework: stats.pendingHomework || 0,
          todayStudyMinutes: stats.todayStudyMinutes || 0,
          streakDays: stats.streakDays || 0,
          dailyGoalMinutes: stats.dailyGoalMinutes || 60,
          goalAchievedToday: stats.goalAchievedToday || false,
          thisWeekMinutes: stats.thisWeekMinutes || 0,
          lastWeekMinutes: stats.lastWeekMinutes || 0,
          weeklyChange: stats.weeklyChange || 0,
          weeklyStudyHours: (stats.weeklyStudyHours || []).map((item: { day?: string; hours?: number }): WeeklyStudyHour => ({
            day: item.day || '未知',
            hours: typeof item.hours === 'number' ? item.hours : 0
          })),
          lastWeekStudyHours: (stats.lastWeekStudyHours || []).map((item: { day?: string; hours?: number }): WeeklyStudyHour => ({
            day: item.day || '未知',
            hours: typeof item.hours === 'number' ? item.hours : 0
          }))
        }

        if (stats.quizScores && stats.quizScores.length > 0) {
          quizzes.value = stats.quizScores.map((q: Record<string, unknown>): QuizScoreItem => ({
            courseId: typeof q.courseId === 'number' ? q.courseId : undefined,
            chapterId: typeof q.chapterId === 'number' ? q.chapterId : undefined,
            title: ((q.title as string) || (q.chapterTitle as string) || '测验'),
            score: typeof q.score === 'number' ? q.score : 0,
            time: (q.time as string) || '最近'
          }))
        } else {
          quizzes.value = []
        }
      }
    } catch (e) {
      logger.error('加载学生统计数据失败:', e)
    } finally {
      loading.value = false
    }
  }

  async function loadBadges(studentId: number | null | undefined): Promise<void> {
    if (!studentId) return
    try {
      try {
        await badgeAPI.checkAndAwardBadges(studentId)
      } catch (error) {
        logger.warn('检查并授予徽章失败:', error)
      }

      const res = await badgeAPI.getStudentBadges(studentId)
      if (res.data && res.data.length > 0) {
        const colorMap: Record<string, string> = {
          'FIRST_COURSE': 'text-zhizi',
          'STREAK_7': 'text-yanzhi',
          'CHAPTER_5': 'text-tianlv',
          'HOMEWORK_MASTER': 'text-qinghua',
          'SCHOLAR': 'text-zijinghui'
        }

        badges.value = res.data.map((badge: Record<string, unknown>): BadgeItem => ({
          id: Number(badge.id),
          name: (badge.name as string) || '未命名徽章',
          iconCode: (badge.code as string) || 'AWARD',
          color: colorMap[badge.code as string] || 'text-slate-400',
          unlocked: Boolean(badge.unlocked),
          description: (badge.description as string) || '',
          unlockedAt: badge.unlockedAt as string | undefined,
          progress: Number(badge.progress || 0),
          currentValue: Number(badge.currentValue || 0),
          targetValue: Number(badge.targetValue || 0),
          nearUnlock: Boolean(badge.nearUnlock)
        }))
      } else {
        badges.value = [
          { id: 1, name: '学习新星', iconCode: 'Star', color: 'text-zhizi', unlocked: false, description: '报名1门课程', progress: 0, currentValue: 0, targetValue: 1, nearUnlock: false },
          { id: 2, name: '连续7天', iconCode: 'Flame', color: 'text-yanzhi', unlocked: false, description: '连续学习7天', progress: 0, currentValue: 0, targetValue: 7, nearUnlock: false },
          { id: 3, name: '勤奋学子', iconCode: 'Award', color: 'text-tianlv', unlocked: false, description: '完成5个章节', progress: 0, currentValue: 0, targetValue: 5, nearUnlock: false },
          { id: 4, name: '作业达人', iconCode: 'Medal', color: 'text-qinghua', unlocked: false, description: '3次作业90分以上', progress: 0, currentValue: 0, targetValue: 3, nearUnlock: false },
          { id: 5, name: '学霸之路', iconCode: 'GraduationCap', color: 'text-zijinghui', unlocked: false, description: '完成20章节+10小时学习', progress: 0, currentValue: 0, targetValue: 20, nearUnlock: false },
        ]
      }
    } catch (e) {
      logger.error('加载徽章数据失败:', e)
    }
  }

  function reset(): void {
    dashboardStats.value = {
      enrolledCourses: 0, completedChapters: 0, totalStudyHours: 0,
      pendingHomework: 0, todayStudyMinutes: 0, streakDays: 0,
      dailyGoalMinutes: 60, goalAchievedToday: false,
      thisWeekMinutes: 0, lastWeekMinutes: 0, weeklyChange: 0,
      weeklyStudyHours: [],
      lastWeekStudyHours: []
    }
    quizzes.value = []
    badges.value = []
    loading.value = false
  }

  return {
    dashboardStats: readonly(dashboardStats),
    quizzes: readonly(quizzes),
    badges: readonly(badges),
    loading: readonly(loading),
    loadStudentStats,
    loadBadges,
    reset
  }
})
