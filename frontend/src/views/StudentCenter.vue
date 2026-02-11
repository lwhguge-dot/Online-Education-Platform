<script setup>
import { ref, onMounted, onUnmounted, computed, watch, defineAsyncComponent } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useConfirmStore } from '../stores/confirm'
import {
  startStatusCheck, stopStatusCheck,
  authAPI, courseAPI, enrollmentAPI, chapterAPI,
  progressAPI, homeworkAPI, commentAPI, chapterCommentAPI, userAPI, statsAPI, badgeAPI
} from '../services/api'
import {
  GraduationCap, LayoutDashboard, BookOpen,
  FileText, History, MessageSquare, Settings,
  LogOut, Menu, X, Home, Star, Flame, Award, Medal
} from 'lucide-vue-next'
import { useToastStore } from '../stores/toast'
import { formatDateCN, formatDateTimeCN } from '../utils/datetime'

// 子组件（按需异步加载，减少主包体积）
const StudentDashboard = defineAsyncComponent(() => import('./student/StudentDashboard.vue'))
const StudentMyCourses = defineAsyncComponent(() => import('./student/StudentMyCourses.vue'))
const StudentHomeworks = defineAsyncComponent(() => import('./student/StudentHomeworks.vue'))
const StudentRecords = defineAsyncComponent(() => import('./student/StudentRecords.vue'))
const StudentQuestions = defineAsyncComponent(() => import('./student/StudentQuestions.vue'))
const StudentProfile = defineAsyncComponent(() => import('./student/StudentProfile.vue'))

// 预热学生中心异步页面，减少首次切换时的加载延迟
const preloadStudentViews = () => {
  void import('./student/StudentDashboard.vue')
  void import('./student/StudentMyCourses.vue')
  void import('./student/StudentHomeworks.vue')
  void import('./student/StudentRecords.vue')
  void import('./student/StudentQuestions.vue')
  void import('./student/StudentProfile.vue')
}

const router = useRouter()
const authStore = useAuthStore()
const toast = useToastStore()
const confirmStore = useConfirmStore()

// 布局状态
const isMobile = ref(false)
const sidebarOpen = ref(window.innerWidth >= 768)
// 监听窗口大小变化
const handleResize = () => {
  isMobile.value = window.innerWidth < 768
  if (isMobile.value) {
    sidebarOpen.value = false
  } else {
    sidebarOpen.value = true
  }
}

const handleMenuClick = (menuId) => {
  activeMenu.value = menuId
  if (isMobile.value) {
    sidebarOpen.value = false
  }
}

// 优先从URL hash读取，其次从sessionStorage，最后默认dashboard
const getInitialMenu = () => {
  const hash = window.location.hash.replace('#', '')
  const validMenus = ['dashboard', 'courses', 'homework', 'records', 'questions', 'settings']
  if (hash && validMenus.includes(hash)) {
    return hash
  }
  const stored = sessionStorage.getItem('studentActiveMenu')
  if (stored && validMenus.includes(stored)) {
    return stored
  }
  return 'dashboard'
}
const activeMenu = ref(getInitialMenu())
const loading = ref(false)
const prevMenuIndex = ref(0)
const slideDirection = ref('up') // 'up' 或 'down'

// 计算当前菜单项的索引（用于动效方向判断）
const getMenuIndex = (menuId) => {
  return menuItems.findIndex(item => item.id === menuId)
}

// 监听activeMenu变化，同步到URL hash和sessionStorage，并计算动效方向
watch(activeMenu, (newMenu, oldMenu) => {
  // 更新URL hash（不触发页面刷新）
  window.history.replaceState(window.history.state, '', `#${newMenu}`)
  // 同步到sessionStorage
  sessionStorage.setItem('studentActiveMenu', newMenu)

  // 计算切换方向
  const newIndex = getMenuIndex(newMenu)
  const oldIndex = oldMenu ? getMenuIndex(oldMenu) : 0
  slideDirection.value = newIndex > oldIndex ? 'up' : 'down'
  prevMenuIndex.value = newIndex
})

// 监听URL hash变化，同步到activeMenu
const handleHashChange = () => {
  const hash = window.location.hash.replace('#', '')
  const validMenus = ['dashboard', 'courses', 'homework', 'records', 'questions', 'settings']
  if (hash && validMenus.includes(hash) && hash !== activeMenu.value) {
    activeMenu.value = hash
  }
}

// 数据状态 - 从后端 API 获取的真实数据
const dashboardStats = ref({
  enrolledCourses: 0, completedChapters: 0, totalStudyHours: 0,
  pendingHomework: 0, todayStudyMinutes: 0, streakDays: 0,
  dailyGoalMinutes: 60, goalAchievedToday: false,
  thisWeekMinutes: 0, lastWeekMinutes: 0, weeklyChange: 0
})
const urgentHomeworks = ref([])
const todayTasks = ref([])
const recentCourses = ref([])

// 徽章数据 - 从后端获取
const badgesData = ref([])

const enrolledCourses = ref([])
const availableCourses = ref([])
const pendingHomeworks = ref([])
const completedHomeworks = ref([])
const timeline = ref([])
const quizScores = ref([])
const weeklyStudyHours = ref([
  { day: '周一', hours: 0 }, { day: '周二', hours: 0 }, { day: '周三', hours: 0 },
  { day: '周四', hours: 0 }, { day: '周五', hours: 0 }, { day: '周六', hours: 0 }, { day: '周日', hours: 0 }
])
const myQuestions = ref([])
const activities = ref([]) // 活动流数据
const studentProfile = ref({
  username: authStore.user?.username || '学生',
  realName: '',
  birthday: '',
  gender: '',
  email: '',
  phone: '',
  avatar: '',
  dailyGoal: 60
})
const notificationSettings = ref({
  homeworkReminder: true, courseUpdate: true, teacherReply: true,
  systemNotice: false, emailNotify: true, pushNotify: true
})
const studyGoal = ref({ dailyMinutes: 60, weeklyHours: 10 })

const settingsReady = ref(false)
let settingsSaveTimer = null

const scheduleSaveUserSettings = () => {
  if (!settingsReady.value) return
  const userId = authStore.user?.id
  if (!userId) return

  if (settingsSaveTimer) {
    clearTimeout(settingsSaveTimer)
  }

  settingsSaveTimer = setTimeout(async () => {
    try {
      await userAPI.updateSettings(userId, {
        notificationSettings: {
          homeworkReminder: notificationSettings.value.homeworkReminder,
          courseUpdate: notificationSettings.value.courseUpdate,
          teacherReply: notificationSettings.value.teacherReply,
          systemNotice: notificationSettings.value.systemNotice,
          emailNotify: notificationSettings.value.emailNotify,
          pushNotify: notificationSettings.value.pushNotify,
        },
        studyGoal: {
          dailyMinutes: studyGoal.value.dailyMinutes,
          weeklyHours: studyGoal.value.weeklyHours
        }
      })

      localStorage.setItem('notification_settings', JSON.stringify(notificationSettings.value))
      localStorage.setItem('study_goal', JSON.stringify(studyGoal.value))
      dashboardStats.value.dailyGoalMinutes = studyGoal.value.dailyMinutes || 60
    } catch (e) {
      console.error('自动保存设置失败:', e)
      toast.error('设置保存失败，请稍后重试')
    }
  }, 600)
}

watch(notificationSettings, scheduleSaveUserSettings, { deep: true })
watch(studyGoal, scheduleSaveUserSettings, { deep: true })

// 菜单配置
const menuItems = [
  { id: 'dashboard', label: '学习概览', icon: LayoutDashboard },
  { id: 'courses', label: '我的课程', icon: BookOpen },
  { id: 'homework', label: '作业中心', icon: FileText },
  { id: 'records', label: '学习记录', icon: History },
  { id: 'questions', label: '问答互动', icon: MessageSquare },
  { id: 'settings', label: '个人设置', icon: Settings },
]

// 辅助方法
const getSubjectColor = (subject) => {
  const map = {
    '语文': 'from-yanzhi to-zhizi',
    '数学': 'from-qinghua to-halanzi',
    '英语': 'from-zijinghui to-qianniuzi',
    '物理': 'from-tianlv to-qingsong',
    '化学': 'from-zhizi to-tianlv',
    '生物': 'from-qingsong to-songshi',
    '历史': 'from-yanzhi to-mudan',
    '地理': 'from-qiuxiang to-ouhe',
    '政治': 'from-red-500 to-orange-500',
  }
  return map[subject] || 'from-qinghua to-halanzi'
}

const formatTime = (dateStr) => {
   return formatDateCN(dateStr, '暂无')
}

// 加载学生统计数据（从后端API）
const loadStudentStats = async () => {
  try {
    const studentId = authStore.user?.id
    if (!studentId) return
    
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
        // 新增字段
        dailyGoalMinutes: stats.dailyGoalMinutes || 60,
        goalAchievedToday: stats.goalAchievedToday || false,
        thisWeekMinutes: stats.thisWeekMinutes || 0,
        lastWeekMinutes: stats.lastWeekMinutes || 0,
        weeklyChange: stats.weeklyChange || 0,
        lastWeekStudyHours: stats.lastWeekStudyHours || []
      }
      
      // 更新本周学习时长
      if (stats.weeklyStudyHours && stats.weeklyStudyHours.length > 0) {
        weeklyStudyHours.value = stats.weeklyStudyHours.map(item => ({
          day: item.day || '未知',
          hours: typeof item.hours === 'number' ? item.hours : 0
        }))
      }
      
      // 更新测验成绩
      if (stats.quizScores && stats.quizScores.length > 0) {
        quizScores.value = stats.quizScores.map(q => ({
          courseId: q.courseId,
          chapterId: q.chapterId,
          title: q.title || q.chapterTitle || '测验',
          score: q.score || 0,
          time: q.time || '最近'
        }))
      }
      
      // 更新紧急作业列表
      if (stats.urgentHomeworks && stats.urgentHomeworks.length > 0) {
        urgentHomeworks.value = stats.urgentHomeworks.map(hw => ({
          id: hw.id,
          title: hw.title,
          courseName: hw.courseName,
          deadline: hw.deadline,
          daysLeft: hw.daysLeft
        }))
      } else {
        urgentHomeworks.value = []
      }
    }
  } catch (e) {
    console.error('加载学生统计数据失败:', e)
  }
}

// 加载徽章数据（从后端API）
const loadBadges = async () => {
  try {
    const studentId = authStore.user?.id
    if (!studentId) return
    
    // 先检查并授予新徽章
    try {
      await badgeAPI.checkAndAwardBadges(studentId)
    } catch (e) {
      // 忽略检查徽章的错误
    }
    
    // 获取徽章列表
    const res = await badgeAPI.getStudentBadges(studentId)
    if (res.data && res.data.length > 0) {
      // 徽章图标映射
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
      
      badgesData.value = res.data.map(badge => ({
        id: badge.id,
        name: badge.name,
        icon: iconMap[badge.code] || Award,
        color: colorMap[badge.code] || 'text-slate-400',
        unlocked: badge.unlocked || false,
        description: badge.description,
        unlockedAt: badge.unlockedAt,
        // 新增进度相关字段
        progress: badge.progress || 0,
        currentValue: badge.currentValue || 0,
        targetValue: badge.targetValue || 0,
        nearUnlock: badge.nearUnlock || false
      }))
    } else {
      // 如果后端没有返回徽章，使用默认徽章列表
      badgesData.value = [
        { id: 1, name: '学习新星', icon: Star, color: 'text-zhizi', unlocked: false, description: '报名1门课程' },
        { id: 2, name: '连续7天', icon: Flame, color: 'text-yanzhi', unlocked: false, description: '连续学习7天' },
        { id: 3, name: '勤奋学子', icon: Award, color: 'text-tianlv', unlocked: false, description: '完成5个章节' },
        { id: 4, name: '作业达人', icon: Medal, color: 'text-qinghua', unlocked: false, description: '3次作业90分以上' },
        { id: 5, name: '学霸之路', icon: GraduationCap, color: 'text-zijinghui', unlocked: false, description: '完成20章节+10小时学习' },
      ]
    }
  } catch (e) {
    console.error('加载徽章数据失败:', e)
    // 使用默认徽章
    badgesData.value = [
      { id: 1, name: '学习新星', icon: Star, color: 'text-zhizi', unlocked: false, description: '报名1门课程' },
      { id: 2, name: '连续7天', icon: Flame, color: 'text-yanzhi', unlocked: false, description: '连续学习7天' },
      { id: 3, name: '勤奋学子', icon: Award, color: 'text-tianlv', unlocked: false, description: '完成5个章节' },
      { id: 4, name: '作业达人', icon: Medal, color: 'text-qinghua', unlocked: false, description: '3次作业90分以上' },
      { id: 5, name: '学霸之路', icon: GraduationCap, color: 'text-zijinghui', unlocked: false, description: '完成20章节+10小时学习' },
    ]
  }
}

// 数据加载逻辑
const loadEnrolledCourses = async () => {
  try {
    const studentId = authStore.user?.id
    if (!studentId) return
    
    const res = await enrollmentAPI.getStudentEnrollments(studentId)
    if (res.data && res.data.length > 0) {
      const coursesData = await Promise.all(
        res.data.map(async (enrollment) => {
          try {
            const courseRes = await courseAPI.getById(enrollment.courseId)
            if (!courseRes.data) return null

            // 检查新章节
            let hasNewChapters = false
            let newChaptersCount = 0
            try {
              const newChapterRes = await enrollmentAPI.checkNewChapters(enrollment.courseId, studentId)
              if (newChapterRes.data) {
                hasNewChapters = newChapterRes.data.hasNewChapters || false
                newChaptersCount = newChapterRes.data.newChaptersCount || 0
              }
            } catch (err) {
              // 新章节检查失败不影响主流程
              console.warn(`检查新章节失败(courseId=${enrollment.courseId}):`, err)
            }

            return {
              id: enrollment.courseId,
              title: courseRes.data.title || '未知课程',
              teacher: courseRes.data.teacherName || '未知教师',
              progress: enrollment.progress || 0,
              totalChapters: 0,
              completedChapters: 0,
              lastStudy: enrollment.lastStudyAt ? formatTime(enrollment.lastStudyAt) : '暂无记录',
              chapters: [],
              subject: courseRes.data.subject,
              color: getSubjectColor(courseRes.data.subject),
              // 添加封面图片和评分信息
              coverImage: courseRes.data.coverImage || courseRes.data.cover,
              rating: courseRes.data.rating || 4.5,
              students: courseRes.data.studentCount || 0,
              // 上次学习位置信息
              lastChapterId: null,
              lastChapterTitle: null,
              lastPosition: 0,
              // 新章节信息
              hasNewChapters,
              newChaptersCount
            }
          } catch (err) {
            console.error(`加载课程详情失败(courseId=${enrollment.courseId}):`, err)
            return null
          }
        })
      )

      enrolledCourses.value = coursesData.filter(Boolean)

      // 加载统计所需的章节详情（并行化，减少等待时间）
      await Promise.all(
        enrolledCourses.value.map(async (course) => {
          try {
            const [chaptersRes, progressRes] = await Promise.all([
              chapterAPI.getByCourse(course.id),
              progressAPI.getCourseProgress(course.id, studentId)
            ])

            if (chaptersRes.data) {
              const progressMap = {}
              let lastStudiedChapter = null
              let lastStudyTime = null

              if (progressRes.data) {
                progressRes.data.forEach(p => {
                  progressMap[p.chapterId] = p
                  // 找到最近学习的章节
                  if (p.lastUpdateTime && (!lastStudyTime || new Date(p.lastUpdateTime) > new Date(lastStudyTime))) {
                    lastStudyTime = p.lastUpdateTime
                    lastStudiedChapter = p
                  }
                })
              }

              course.chapters = chaptersRes.data.map(ch => ({
                id: ch.id,
                title: ch.title,
                completed: progressMap[ch.id]?.isCompleted === 1
              }))
              course.totalChapters = course.chapters.length
              course.completedChapters = course.chapters.filter(c => c.completed).length

              // 设置上次学习位置
              if (lastStudiedChapter) {
                course.lastChapterId = lastStudiedChapter.chapterId
                course.lastPosition = lastStudiedChapter.lastPosition || 0
                const chapterInfo = course.chapters.find(ch => ch.id === lastStudiedChapter.chapterId)
                course.lastChapterTitle = chapterInfo?.title || '未知章节'
              }
            }
          } catch (err) {
            console.error(`加载课程章节进度失败(courseId=${course.id}):`, err)
          }
        })
      )
      
      recentCourses.value = enrolledCourses.value.slice(0, 3).map(c => ({
         id: c.id, name: c.title, progress: c.progress,
         lastChapter: c.lastChapterTitle || (c.chapters.length > 0 ? c.chapters[c.chapters.length - 1].title : '暂无章节'),
         lastStudy: c.lastStudy,
         lastChapterId: c.lastChapterId,
         lastPosition: c.lastPosition
      }))
      
      // 由已完成章节生成时间线
      const timelineEntries = []
      for (const course of enrolledCourses.value) {
        if (course.chapters) {
          course.chapters.filter(ch => ch.completed).forEach(ch => {
            timelineEntries.push({
              title: course.title,
              time: course.lastStudy || '本周',
              action: `完成章节: ${ch.title}`,
              // 新增：用于跳转的信息
              courseId: course.id,
              chapterId: ch.id,
              chapterTitle: ch.title
            })
          })
        }
      }
      timeline.value = timelineEntries.slice(0, 5)
      
    } else {
      enrolledCourses.value = []
      recentCourses.value = []
      timeline.value = []
    }
  } catch (e) {
    console.error('List enrollments failed', e)
  }
}

const loadAvailableCourses = async () => {
  try {
    const res = await courseAPI.getPublished()
    if (res.data) {
      const enrolledIds = enrolledCourses.value.map(c => c.id)
      availableCourses.value = res.data
        .filter(c => !enrolledIds.includes(c.id))
        .map(c => ({
          id: c.id, 
          title: c.title, 
          teacher: c.teacherName || '未知教师',
          students: c.studentCount || 0, 
          progress: 0,
          subject: c.subject,
          color: getSubjectColor(c.subject),
          // 添加封面图片和评分信息
          coverImage: c.coverImage || c.cover,
          rating: c.rating || 4.5
        }))
    }
  } catch (e) {
    console.error('加载可报名课程失败:', e)
  }
}

const loadHomeworks = async () => {
  try {
    const studentId = authStore.user?.id
    if (!studentId) return
    
    // 真实业务中应由聚合 API 直接返回学生作业列表
    const pending = []
    const completed = []
    
    for (const course of enrolledCourses.value) {
      if (!course.chapters) continue
      for (const chapter of course.chapters) {
         // 只有已完成的章节才显示作业
         if (!chapter.completed) continue
         
         try {
           const res = await homeworkAPI.getStudentHomeworks(chapter.id, studentId)
           if (res.data) {
             res.data.forEach(hw => {
                const item = {
                  id: hw.homework?.id,
                  title: hw.homework?.title,
                  course: course.title,
                  type: hw.homework?.homeworkType || 'objective',
                  daysLeft: hw.homework?.deadline ? Math.ceil((new Date(hw.homework.deadline) - new Date()) / (86400000)) : null,
                  submitTime: hw.submission?.submittedAt,
                  totalScore: hw.submission?.totalScore,
                  // 使用后端返回的submitStatus字段判断状态
                  status: hw.submission?.submitStatus === 'graded' ? 'graded' : 
                          (hw.submission ? 'submitted' : 'pending')
                }
                
                if (hw.submitted && hw.submission) completed.push(item)
                else if (hw.unlocked) pending.push(item)
             })
           }
         } catch(e) {
           console.error(`加载作业失败(courseId=${course.id}, chapterId=${chapter.id}):`, e)
         }
      }
    }
    
    pendingHomeworks.value = pending
    completedHomeworks.value = completed
    
    // 更新dashboardStats中的待完成作业数
    dashboardStats.value.pendingHomework = pending.length
    
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
  }
}

const loadQuestions = async () => {
  try {
    const studentId = authStore.user?.id
    if (!studentId) return
    const [commentRes, homeworkRes, chapterCommentRes] = await Promise.allSettled([
      commentAPI.getStudentQuestions(studentId),
      homeworkAPI.getStudentQuestions(studentId),
      chapterCommentAPI.getStudentQuestions(studentId)
    ])

    const normalizeTime = (timeValue) => {
      if (!timeValue) return ''
      const s = String(timeValue)
      // 后端 LocalDateTime 序列化通常为 2026-01-02T23:36:14，统一成更友好的展示
      return s.includes('T') ? s.replace('T', ' ') : s
    }

    const parseTime = (timeValue) => {
      if (!timeValue) return 0
      const raw = String(timeValue)
      // Date.parse 对 "YYYY-MM-DD HH:mm:ss" 的兼容性不稳定，尽量转成 ISO 再解析
      const normalized = /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}/.test(raw) ? raw.replace(' ', 'T') : raw
      const t = Date.parse(normalized)
      return Number.isNaN(t) ? 0 : t
    }

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

    myQuestions.value = [...homeworkQuestions, ...commentQuestions, ...chapterCommentQuestions]
      .sort((a, b) => parseTime(b.time) - parseTime(a.time))
  } catch (e) {
    console.error('加载我的提问失败:', e)
  }
}

// 加载用户设置
const loadUserSettings = async () => {
  settingsReady.value = false
  try {
    const userId = authStore.user?.id
    if (!userId) return
    
    const res = await userAPI.getSettings(userId)
    if (res.data) {
      const settings = res.data
      // 更新通知设置（优先使用嵌套结构；兼容历史扁平字段）
      const ns = settings.notificationSettings || settings
      if (ns.homeworkReminder !== undefined) notificationSettings.value.homeworkReminder = ns.homeworkReminder
      if (ns.courseUpdate !== undefined) notificationSettings.value.courseUpdate = ns.courseUpdate
      if (ns.teacherReply !== undefined) notificationSettings.value.teacherReply = ns.teacherReply
      if (ns.systemNotice !== undefined) notificationSettings.value.systemNotice = ns.systemNotice
      if (ns.emailNotify !== undefined) notificationSettings.value.emailNotify = ns.emailNotify
      if (ns.pushNotify !== undefined) notificationSettings.value.pushNotify = ns.pushNotify

      // 更新学习目标（优先使用嵌套结构；兼容历史扁平字段）
      const sg = settings.studyGoal || {}
      const dailyMinutes = sg.dailyMinutes ?? settings.dailyGoalMinutes
      const weeklyHours = sg.weeklyHours ?? settings.weeklyGoalChapters
      if (dailyMinutes !== undefined && dailyMinutes !== null) studyGoal.value.dailyMinutes = dailyMinutes
      if (weeklyHours !== undefined && weeklyHours !== null) studyGoal.value.weeklyHours = weeklyHours
      if (studyGoal.value.dailyMinutes) {
        dashboardStats.value.dailyGoalMinutes = studyGoal.value.dailyMinutes
      }
    }
  } catch (e) {
    console.error('加载用户设置失败:', e)
    // 从localStorage加载备用
    try {
      const savedNotifications = localStorage.getItem('notification_settings')
      const savedGoal = localStorage.getItem('study_goal')
      if (savedNotifications) Object.assign(notificationSettings.value, JSON.parse(savedNotifications))
      if (savedGoal) {
        Object.assign(studyGoal.value, JSON.parse(savedGoal))
        if (studyGoal.value.dailyMinutes) {
          dashboardStats.value.dailyGoalMinutes = studyGoal.value.dailyMinutes
        }
      }
    } catch(e) {
      console.warn('本地设置缓存解析失败:', e)
    }
  } finally {
    settingsReady.value = true
  }
}

const refreshAll = async () => {
    loading.value = true
    try {
      // 第一阶段：并行加载无依赖数据
      await Promise.all([
        loadStudentStats(),
        loadBadges(),
        loadEnrolledCourses(),
        loadQuestions(),
        loadUserSettings()
      ])
      // 学习目标以个人设置为准（避免与统计接口的默认值竞争覆盖）
      dashboardStats.value.dailyGoalMinutes = studyGoal.value.dailyMinutes || dashboardStats.value.dailyGoalMinutes || 60

      // 第二阶段：依赖已报名课程的数据
      await Promise.all([
        loadAvailableCourses(),
        loadHomeworks()
      ])
    } finally {
      loading.value = false
    }
}

// 操作方法
const handleEnroll = async (course) => {
  try {
     await enrollmentAPI.enroll(course.id, authStore.user?.id)
     await refreshAll()
     toast.success('报名成功')
  } catch (e) { toast.error(e.message) }
}

const handleDrop = async (courseId) => {
   const confirmed = await confirmStore.show({
     title: '退出课程',
     message: '确定要退出该课程吗？退出后需要重新报名才能继续学习。',
     type: 'warning',
     confirmText: '确定退课',
     cancelText: '取消'
   })
   if (!confirmed) return
   try {
     await enrollmentAPI.drop(courseId, authStore.user?.id)
     await refreshAll()
   } catch(e) {
     console.error('退课失败:', e)
     toast.error('退课失败，请稍后重试')
   }
}

const handleSubmitQuestion = async (q) => {
   try {
     const studentId = authStore.user?.id
     if (!studentId) {
       toast.error('用户未登录')
       return
     }

     if (!q.courseId || !q.chapterId) {
       toast.warning('请选择关联课程与章节后再提问')
       return
     }

     const enrolled = enrolledCourses.value.some(c => String(c.id) === String(q.courseId))
     if (!enrolled) {
       toast.warning('请先报名该课程后再提问')
       return
     }

     const mergedContent = q.title ? `【${q.title}】\n${q.content}` : q.content
     const res = await chapterCommentAPI.createComment({
       courseId: Number(q.courseId),
       chapterId: Number(q.chapterId),
       content: mergedContent,
       parentId: null
     })

     if (res.code === 200 && res.data) {
       myQuestions.value.unshift({
         id: `chapter-${res.data.id ?? Date.now()}`,
        title: q.title || '章节提问',
        content: mergedContent,
        courseName: q.courseName,
        chapterName: q.chapterName,
        time: formatDateTimeCN(new Date()),
        commentCount: 0
      })
       toast.success('提问成功，等待老师回复')
     }
   } catch (e) {
     console.error('提交问题失败:', e)
     toast.error('提问失败，请稍后重试')
   }
}

const handleSaveProfile = async (newProfile) => {
   try {
     const userId = authStore.user?.id
     if (!userId) {
       toast.error('用户未登录')
       return
     }
     
     // 调用后端API保存到数据库（username可修改，name不可修改）
     const res = await userAPI.updateProfile(userId, {
       username: newProfile.username,  // 用户名/昵称可修改
       phone: newProfile.phone,
       avatar: newProfile.avatar,
       birthday: newProfile.birthday,
       gender: newProfile.gender
     })
     
     if (res.data) {
       // 使用后端返回的数据更新本地状态
       const updatedProfile = {
         ...newProfile,
         username: res.data.username,
         realName: res.data.name  // 真实姓名从数据库获取
       }
       studentProfile.value = updatedProfile
       
       // 同步更新authStore中的用户信息（用于右上角显示）
       authStore.updateUser({
         username: res.data.username,  // 更新用户名
         name: res.data.name,
         avatar: res.data.avatar,
         birthday: res.data.birthday,
         gender: res.data.gender
       })
       
       // 保存用户设置到数据库
       try {
         await userAPI.updateSettings(userId, {
          notificationSettings: {
            homeworkReminder: notificationSettings.value.homeworkReminder,
            courseUpdate: notificationSettings.value.courseUpdate,
            teacherReply: notificationSettings.value.teacherReply,
            systemNotice: notificationSettings.value.systemNotice,
            emailNotify: notificationSettings.value.emailNotify,
            pushNotify: notificationSettings.value.pushNotify,
          },
          studyGoal: {
            dailyMinutes: studyGoal.value.dailyMinutes,
            weeklyHours: studyGoal.value.weeklyHours
          }
         })
         dashboardStats.value.dailyGoalMinutes = studyGoal.value.dailyMinutes || 60
       } catch (settingsErr) {
         console.error('保存设置失败:', settingsErr)
       }
       
       // 同时保存到localStorage作为缓存
       localStorage.setItem('student_profile', JSON.stringify(updatedProfile))
       localStorage.setItem('notification_settings', JSON.stringify(notificationSettings.value))
       localStorage.setItem('study_goal', JSON.stringify(studyGoal.value))
       
       toast.success('设置保存成功')
     }
   } catch (e) {
     console.error('保存个人信息失败:', e)
     toast.error('保存失败: ' + e.message)
   }
}

const handleNavigate = (menu) => {
   activeMenu.value = menu
}

// 继续学习 - 跳转到上次学习位置
const handleStartStudy = (courseIdOrObject) => {
  // 支持传入courseId或course对象
  if (typeof courseIdOrObject === 'object') {
    const course = courseIdOrObject
    let url = `/study/${course.id}`
    // 如果有上次学习位置，添加参数
    if (course.lastChapterId) {
      url += `?chapter=${course.lastChapterId}`
      if (course.lastPosition && course.lastPosition > 0) {
        url += `&t=${course.lastPosition}`
      }
    }
    // 添加来源标记，用于返回时判断
    url += (url.includes('?') ? '&' : '?') + 'from=student'
    router.push(url)
  } else {
    // 简单的courseId，查找对应课程
    const course = recentCourses.value.find(c => c.id === courseIdOrObject) || 
                   enrolledCourses.value.find(c => c.id === courseIdOrObject)
    if (course && course.lastChapterId) {
      let url = `/study/${courseIdOrObject}?chapter=${course.lastChapterId}`
      if (course.lastPosition && course.lastPosition > 0) {
        url += `&t=${course.lastPosition}`
      }
      url += '&from=student'
      router.push(url)
    } else {
      router.push(`/study/${courseIdOrObject}?from=student`)
    }
  }
}

const handleStartHomework = (hw) => {
   router.push(`/homework/${hw.id}`)
}

const handleViewHomework = (hw) => {
   router.push(`/homework/${hw.id}?view=true`)
}

// 生命周期
onMounted(async () => {
   startStatusCheck()
   
   // 监听hash变化
   window.addEventListener('hashchange', handleHashChange)
   // 监听窗口大小变化
   window.addEventListener('resize', handleResize)
   // 初始化时也检查一次hash
   handleHashChange()
   
   // 清除可能存在的错误缓存数据
   localStorage.removeItem('student_profile')
   
   // 从数据库加载用户信息（数据库数据优先）
   try {
     const userId = authStore.user?.id
     if (userId) {
       const res = await userAPI.getById(userId)
       if (res.data) {
         const userData = res.data
         studentProfile.value = {
           username: userData.username || authStore.user?.username || '学生',
           realName: userData.name || '',
           birthday: userData.birthday || '',
           gender: userData.gender || '',
           email: userData.email || (userData.username + '@edu.cn'),
           phone: userData.phone || '',
           avatar: userData.avatar || '',
           dailyGoal: 60
         }
         // 同步到authStore（确保右上角显示正确）
         authStore.updateUser({
           username: userData.username,
           name: userData.name,
           avatar: userData.avatar,
           birthday: userData.birthday,
           gender: userData.gender
         })
       }
     }
   } catch(e) {
     console.error('加载用户信息失败:', e)
   }
   
   refreshAll()

   // 首屏数据请求发起后异步预加载其他视图，提升菜单切换顺滑度
   setTimeout(() => preloadStudentViews(), 0)
})

onUnmounted(() => {
   stopStatusCheck()
   // 移除hash变化监听
   window.removeEventListener('hashchange', handleHashChange)
   window.removeEventListener('resize', handleResize)
   // sessionStorage已通过watch实时保存，无需在此处理
   if (settingsSaveTimer) {
     clearTimeout(settingsSaveTimer)
     settingsSaveTimer = null
   }
})

const handleLogout = async () => {
   try {
     // 调用后端API更新会话状态
     await authAPI.logout()
   } catch (e) {
     // 即使API调用失败也继续登出
   }
   stopStatusCheck()
   authStore.logout()
   router.push('/login')
}
</script>

<template>
  <div class="min-h-screen flex transition-colors duration-300">
    <!-- Mobile Backdrop -->
    <div 
      v-if="sidebarOpen" 
      class="fixed inset-0 bg-shuimo/20 backdrop-blur-sm z-40 md:hidden"
      @click="sidebarOpen = false"
      aria-hidden="true"
    ></div>

    <!-- Sidebar -->
    <aside 
      class="fixed top-0 left-0 h-full bg-white/80 backdrop-blur-xl border-r border-slate-200/60 z-50 transition-all duration-300 flex flex-col will-change-[width,transform]"
      :class="[
        sidebarOpen ? 'translate-x-0 w-64' : '-translate-x-full md:translate-x-0 md:w-20',
        'w-64'
      ]"
    >
      <div class="h-20 flex items-center px-6 border-b border-slate-100/50">
        <div class="w-8 h-8 rounded-xl bg-gradient-to-br from-qinghua to-halanzi flex items-center justify-center shrink-0 shadow-lg shadow-qinghua/20">
          <GraduationCap class="w-5 h-5 text-white" aria-hidden="true" />
        </div>
        <span v-if="sidebarOpen" class="ml-3 font-bold text-lg text-shuimo font-song tracking-wide truncate">
           学生中心
        </span>
      </div>

      <nav class="flex-1 p-4 space-y-2 overflow-y-auto custom-scrollbar">
        <button
          v-for="item in menuItems"
          :key="item.id"
          @click="handleMenuClick(item.id)"
          class="w-full flex items-center gap-3 px-4 py-3 rounded-xl transition-all duration-300 group relative overflow-hidden focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-qinghua"
          :class="activeMenu === item.id 
            ? 'bg-gradient-to-r from-qinghua to-halanzi text-white shadow-lg shadow-qinghua/30' 
            : 'text-muted hover:bg-slate-100 hover:text-shuimo'"
          :aria-label="item.label"
        >
          <component :is="item.icon" class="w-5 h-5 transition-transform" :class="{'scale-110': activeMenu === item.id}" aria-hidden="true" />
          <span v-if="sidebarOpen" class="font-medium whitespace-nowrap">{{ item.label }}</span>
          <div v-if="activeMenu === item.id" class="absolute inset-0 bg-white/10 opacity-0 group-hover:opacity-100 transition-opacity"></div>
        </button>
      </nav>

      <div class="p-4 border-t border-slate-100/50 space-y-2">
        <button @click="router.push('/')" class="w-full flex items-center gap-3 px-4 py-3 rounded-xl text-qinghua hover:bg-qinghua/10 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-qinghua" aria-label="回到首页">
          <Home class="w-5 h-5" aria-hidden="true" />
          <span v-if="sidebarOpen" class="font-medium">回到首页</span>
        </button>
        <button @click="handleLogout" class="w-full flex items-center gap-3 px-4 py-3 rounded-xl text-yanzhi hover:bg-yanzhi/10 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-yanzhi" aria-label="退出登录">
          <LogOut class="w-5 h-5" aria-hidden="true" />
          <span v-if="sidebarOpen" class="font-medium">退出登录</span>
        </button>
      </div>
    </aside>

    <!-- Main Content -->
    <main 
      class="flex-1 transition-all duration-300 min-h-screen flex flex-col will-change-[margin]"
      :class="sidebarOpen ? 'md:ml-64' : 'md:ml-20'"
      aria-live="polite"
    >
      <!-- Header -->
      <header class="sticky top-0 z-40 bg-white/70 backdrop-blur-md border-b border-slate-200/50 px-6 h-20 flex items-center justify-between">
        <div class="flex items-center gap-4">
          <button 
            @click="sidebarOpen = !sidebarOpen"
            class="p-2 rounded-xl hover:bg-slate-100 text-muted hover:text-shuimo transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-qinghua"
            :aria-label="sidebarOpen ? '收起侧边栏' : '展开侧边栏'"
          >
            <component :is="sidebarOpen ? X : Menu" class="w-5 h-5" aria-hidden="true" />
          </button>
          
        </div>

        <div class="flex items-center gap-6">
          <div class="flex items-center gap-3 pl-6 border-l border-slate-200">
             <div class="text-right hidden md:block">
                <p class="text-sm font-bold text-shuimo">{{ authStore.user?.username || 'Student' }}</p>
                <p class="text-xs text-muted">学生</p>
             </div>
             <div class="w-10 h-10 rounded-full bg-slate-200 border-2 border-white shadow-sm overflow-hidden">
                <img 
                  :src="studentProfile.avatar || `https://ui-avatars.com/api/?name=${authStore.user?.username}&background=random`" 
                  alt="用户头像"
                />
             </div>
          </div>
        </div>
      </header>

      <!-- View Content -->
      <div class="p-6 md:p-8 flex-1 overflow-x-hidden">
          <Transition
            :name="slideDirection === 'up' ? 'slide-up' : 'slide-down'"
            mode="out-in"
          >
            <component
              :key="activeMenu"
              :is="{
                dashboard: StudentDashboard,
                courses: StudentMyCourses,
                homework: StudentHomeworks,
                records: StudentRecords,
                questions: StudentQuestions,
                settings: StudentProfile
              }[activeMenu]"
            
            v-bind="{
              loading: loading,
              ...(activeMenu === 'dashboard' ? {
                stats: dashboardStats,
                urgentHomeworks: urgentHomeworks,
                todayTasks: todayTasks,
                recentCourses: recentCourses,
                badges: badgesData,
                activities: activities,
                username: authStore.user?.username || studentProfile.username
              } : {}),
              ...(activeMenu === 'courses' ? {
                enrolledCourses: enrolledCourses,
                availableCourses: availableCourses,
                loading: loading
              } : {}),
              ...(activeMenu === 'homework' ? {
                pendingHomeworks: pendingHomeworks,
                completedHomeworks: completedHomeworks,
                loading: loading
              } : {}),
              ...(activeMenu === 'records' ? {
                timeline: timeline,
                quizScores: quizScores,
                weeklyStudyHours: weeklyStudyHours,
                lastWeekStudyHours: dashboardStats.lastWeekStudyHours || [],
                thisWeekMinutes: dashboardStats.thisWeekMinutes,
                lastWeekMinutes: dashboardStats.lastWeekMinutes,
                weeklyChange: dashboardStats.weeklyChange
              } : {}),
              ...(activeMenu === 'questions' ? {
                questions: myQuestions,
                enrolledCourses: enrolledCourses
              } : {}),
              ...(activeMenu === 'settings' ? {
                userId: authStore.user?.id,
                profile: studentProfile,
                notificationSettings: notificationSettings,
                studyGoal: studyGoal
              } : {})
            }"
            
            @navigate="handleNavigate"
            @start-study="handleStartStudy"
            @enroll="handleEnroll"
            @drop="handleDrop"
            @view-detail="(c) => $router.push(`/course/${c.id}?from=student`)"
            @start="handleStartHomework"
            @view="handleViewHomework"
            @submit="handleSubmitQuestion"
            @save="handleSaveProfile"
          />
          </Transition>
      </div>
    </main>
  </div>
</template>

<style scoped>
.custom-scrollbar::-webkit-scrollbar {
  width: 4px;
}
.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 4px;
}
.custom-scrollbar:hover::-webkit-scrollbar-thumb {
  background: #94a3b8;
}

.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(15px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-15px);
}

/* 页面切换过渡动画 - 向上滑入 */
.slide-up-enter-active,
.slide-up-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.slide-up-enter-from {
  opacity: 0;
  transform: translateY(20px);
}

.slide-up-leave-to {
  opacity: 0;
  transform: translateY(-20px);
}

/* 页面切换过渡动画 - 向下滑入 */
.slide-down-enter-active,
.slide-down-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.slide-down-enter-from {
  opacity: 0;
  transform: translateY(-20px);
}

.slide-down-leave-to {
  opacity: 0;
  transform: translateY(20px);
}
</style>
