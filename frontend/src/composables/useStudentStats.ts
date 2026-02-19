import { ref } from 'vue'
import { statsAPI, badgeAPI } from '../services/api'
import { Star, Flame, Award, Medal, GraduationCap } from 'lucide-vue-next'

export function useStudentStats() {
    const dashboardStats = ref({
        enrolledCourses: 0, completedChapters: 0, totalStudyHours: 0,
        pendingHomework: 0, todayStudyMinutes: 0, streakDays: 0,
        dailyGoalMinutes: 60, goalAchievedToday: false,
        thisWeekMinutes: 0, lastWeekMinutes: 0, weeklyChange: 0,
        weeklyStudyHours: [],
        lastWeekStudyHours: []
    })

    const quizzes = ref([]) // Quiz scores
    const badges = ref([])
    const loading = ref(false)

    const loadStudentStats = async (studentId) => {
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
                    weeklyStudyHours: (stats.weeklyStudyHours || []).map(item => ({
                        day: item.day || '未知',
                        hours: typeof item.hours === 'number' ? item.hours : 0
                    })),
                    lastWeekStudyHours: stats.lastWeekStudyHours || []
                }

                if (stats.quizScores && stats.quizScores.length > 0) {
                    quizzes.value = stats.quizScores.map(q => ({
                        courseId: q.courseId,
                        chapterId: q.chapterId,
                        title: q.title || q.chapterTitle || '测验',
                        score: q.score || 0,
                        time: q.time || '最近'
                    }))
                }
            }
        } catch (e) {
            console.error('加载学生统计数据失败:', e)
        } finally {
            loading.value = false
        }
    }

    const loadBadges = async (studentId) => {
        if (!studentId) return
        try {
            try {
                await badgeAPI.checkAndAwardBadges(studentId)
            } catch (e) { } // ignore

            const res = await badgeAPI.getStudentBadges(studentId)
            if (res.data && res.data.length > 0) {
                const iconMap = {
                    'FIRST_COURSE': Star,
                    'STREAK_7': Flame,
                    'CHAPTER_5': Award,
                    'HOMEWORK_MASTER': Medal,
                    'SCHOLAR': GraduationCap
                }
                const colorMap = {
                    'FIRST_COURSE': 'text-zhizi',
                    'STREAK_7': 'text-yanzhi',
                    'CHAPTER_5': 'text-tianlv',
                    'HOMEWORK_MASTER': 'text-qinghua',
                    'SCHOLAR': 'text-zijinghui'
                }

                badges.value = res.data.map(badge => ({
                    id: badge.id,
                    name: badge.name,
                    icon: iconMap[badge.code] || Award,
                    color: colorMap[badge.code] || 'text-slate-400',
                    unlocked: badge.unlocked || false,
                    description: badge.description,
                    unlockedAt: badge.unlockedAt,
                    progress: badge.progress || 0,
                    currentValue: badge.currentValue || 0,
                    targetValue: badge.targetValue || 0,
                    nearUnlock: badge.nearUnlock || false
                }))
            } else {
                // Defaults
                badges.value = [
                    { id: 1, name: '学习新星', icon: Star, color: 'text-zhizi', unlocked: false, description: '报名1门课程' },
                    { id: 2, name: '连续7天', icon: Flame, color: 'text-yanzhi', unlocked: false, description: '连续学习7天' },
                    { id: 3, name: '勤奋学子', icon: Award, color: 'text-tianlv', unlocked: false, description: '完成5个章节' },
                    { id: 4, name: '作业达人', icon: Medal, color: 'text-qinghua', unlocked: false, description: '3次作业90分以上' },
                    { id: 5, name: '学霸之路', icon: GraduationCap, color: 'text-zijinghui', unlocked: false, description: '完成20章节+10小时学习' },
                ]
            }
        } catch (e) {
            console.error('加载徽章数据失败:', e)
        }
    }

    return {
        dashboardStats,
        quizzes,
        badges,
        loading, // shared loading state might be tricky if used for both
        loadStudentStats,
        loadBadges
    }
}
