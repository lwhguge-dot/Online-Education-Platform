<script setup>
import { ref, onMounted, onUnmounted, watch, defineAsyncComponent, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import {
  courseAPI, statsAPI, enrollmentAPI,
  startStatusCheck, stopStatusCheck, authAPI
} from '../services/api'

import {
  GraduationCap, LayoutDashboard, BookOpen, FileText,
  Users, MessageSquare, Settings, LogOut, Home,
  Menu, X, Bell, BarChart3, Megaphone, Calendar
} from 'lucide-vue-next'

// 视图组件（按需异步加载，降低首屏体积）
const TeacherDashboard = defineAsyncComponent(() => import('./teacher/TeacherDashboard.vue'))
const TeacherCourses = defineAsyncComponent(() => import('./teacher/TeacherCourses.vue'))
const TeacherHomeworks = defineAsyncComponent(() => import('./teacher/TeacherHomeworks.vue'))
const TeacherStudents = defineAsyncComponent(() => import('./teacher/TeacherStudents.vue'))
const TeacherDiscussion = defineAsyncComponent(() => import('./teacher/TeacherDiscussion.vue'))
const TeacherProfile = defineAsyncComponent(() => import('./teacher/TeacherProfile.vue'))
const CourseAnalytics = defineAsyncComponent(() => import('./teacher/CourseAnalytics.vue'))
const AnnouncementHistory = defineAsyncComponent(() => import('../components/teacher/AnnouncementHistory.vue'))
const TeachingCalendar = defineAsyncComponent(() => import('./teacher/TeachingCalendar.vue'))

const router = useRouter()
const authStore = useAuthStore()
const roleLabel = computed(() => {
  const role = authStore.user?.role
  if (role === 'admin') return '管理员'
  if (role === 'student') return '学生'
  return '教师'
})

// UI 状态
const sidebarOpen = ref(true)
// 优先从URL hash读取，其次从sessionStorage，最后默认dashboard
const getInitialMenu = () => {
  // 确保正确解析 hash（去掉 # 符号）
  const hash = window.location.hash ? window.location.hash.substring(1) : ''
  const validMenus = ['dashboard', 'courses', 'homework', 'students', 'analytics', 'calendar', 'announcements', 'discussion', 'settings']
  if (hash && validMenus.includes(hash)) {
    return hash
  }
  const stored = sessionStorage.getItem('teacherActiveMenu')
  if (stored && validMenus.includes(stored)) {
    return stored
  }
  return 'dashboard'
}
const activeMenu = ref(getInitialMenu())
const loading = ref(false)
const prevMenuIndex = ref(0)
const slideDirection = ref('up') // 'up' 或 'down'

// 监听 activeMenu 变化，同步到 URL hash 和 sessionStorage，并计算动效方向
watch(activeMenu, (newMenu, oldMenu) => {
  window.history.replaceState(window.history.state, '', `#${newMenu}`)
  sessionStorage.setItem('teacherActiveMenu', newMenu)

  // 计算切换方向
  const newIndex = getMenuIndex(newMenu)
  const oldIndex = oldMenu ? getMenuIndex(oldMenu) : 0
  slideDirection.value = newIndex > oldIndex ? 'up' : 'down'
  prevMenuIndex.value = newIndex
})

// 监听 URL hash 变化，同步到 activeMenu
const handleHashChange = () => {
  const hash = window.location.hash ? window.location.hash.substring(1) : ''
  const validMenus = ['dashboard', 'courses', 'homework', 'students', 'analytics', 'calendar', 'announcements', 'discussion', 'settings']
  if (hash && validMenus.includes(hash) && hash !== activeMenu.value) {
    activeMenu.value = hash
  }
}

// 菜单配置 - 分组结构
const menuGroups = [
  {
    id: 'core',
    label: '核心',
    items: [
      { id: 'dashboard', label: '教学概览', icon: LayoutDashboard }
    ]
  },
  {
    id: 'teaching',
    label: '教学管理',
    items: [
      { id: 'courses', label: '课程管理', icon: BookOpen },
      { id: 'homework', label: '作业管理', icon: FileText },
      { id: 'students', label: '学生管理', icon: Users },
      { id: 'analytics', label: '课程分析', icon: BarChart3 }
    ]
  },
  {
    id: 'schedule',
    label: '日程与沟通',
    items: [
      { id: 'calendar', label: '教学日历', icon: Calendar },
      { id: 'announcements', label: '公告管理', icon: Megaphone },
      { id: 'discussion', label: '讨论互动', icon: MessageSquare }
    ]
  },
  {
    id: 'system',
    label: '系统',
    items: [
      { id: 'settings', label: '个人设置', icon: Settings }
    ]
  }
]

// 扁平化菜单项（用于计算指示器位置）
const menuItems = menuGroups.flatMap(group => group.items)

// 计算当前菜单项的索引（用于动效方向判断）
const getMenuIndex = (menuId) => {
  return menuItems.findIndex(item => item.id === menuId)
}

// 数据状态
const dashboardStats = ref({
  myCourses: 0,
  totalStudents: 0,
  pendingHomework: 0,
  weeklyInteractions: 0,
  newStudentsToday: 0
})
const todoList = ref([])
const recentActivities = ref([])
const courses = ref([])
const students = ref([])

// 个人设置状态
const teacherProfile = ref({
  username: authStore.user?.username || '教师',
  realName: '',
  title: '',
  email: '',
  phone: '',
  introduction: ''
})
const notificationSettings = ref({
  newStudent: true,
  homeworkSubmit: true,
  studentQuestion: true,
  systemNotice: false
})

// === 数据加载 ===

const loadCourses = async () => {
  try {
    const res = await courseAPI.getAll()
    if (res.data) {
      const userStr = sessionStorage.getItem('user')
      const teacherId = userStr ? Number(JSON.parse(userStr).id) : 0
      
      const myCourses = res.data.filter(c => Number(c.teacherId) === teacherId)
      
      courses.value = myCourses.map(c => ({
        id: c.id,
        title: c.title,
        subject: c.subject,
        status: c.status,
        students: c.studentCount || 0,
        chapters: c.totalChapters || 0,
        rating: c.rating || 0,
        cover: c.coverImage || '',
        description: c.description || ''
      }))

      // 使用课程数据补全基础统计，避免接口未返回导致空白
      dashboardStats.value.myCourses = courses.value.length
      dashboardStats.value.totalStudents = courses.value.reduce((sum, c) => sum + (c.students || 0), 0)
    }
  } catch (e) {
    console.error('Failed to load courses', e)
  }
}

const loadStats = async () => {
  try {
    const userId = authStore.user?.id
    if (!userId) return

    // 准备课程数据用于仪表盘聚合查询
    const coursesData = courses.value.map(c => ({
      id: c.id,
      title: c.title,
      status: c.status,
      studentCount: c.students || 0
    }))

    const [dashRes, todosRes, activitiesRes] = await Promise.all([
      statsAPI.getTeacherDashboard(userId, coursesData),
      statsAPI.getTeacherTodos(userId),
      statsAPI.getTeacherActivities(userId)
    ])

    if (dashRes.code === 200 && dashRes.data) {
      dashboardStats.value = { 
        ...dashboardStats.value, 
        ...dashRes.data,
        // 兜底统计字段，避免后端缺失导致卡片显示异常
        myCourses: dashRes.data.myCourses || courses.value.length,
        publishedCourses: dashRes.data.publishedCourses || courses.value.filter(c => c.status === 'PUBLISHED').length,
        totalStudents: dashRes.data.totalStudents || courses.value.reduce((sum, c) => sum + (c.students || 0), 0)
      }
    }
    if (todosRes.code === 200 && todosRes.data) todoList.value = todosRes.data
    if (activitiesRes.code === 200 && activitiesRes.data) recentActivities.value = activitiesRes.data

  } catch (e) {
    console.error('Failed to load stats', e)
  }
}

const loadStudents = async () => {
  try {
    const userId = authStore.user?.id
    if (!userId) return
    
    // 使用聚合 API 替代循环请求，降低请求数量
    const res = await enrollmentAPI.getTeacherStudents(userId, 1, 100)
    if (res.code === 200 && res.data) {
      students.value = res.data.students || []
    }
  } catch (e) {
    console.error('Failed to load students', e)
  }
}

const loadAllData = async () => {
  loading.value = true
  // 先加载课程，因为统计数据依赖课程信息
  await loadCourses()
  // 然后并行加载统计数据和学生数据
  await Promise.all([
    loadStats(),
    loadStudents()
  ])
  loading.value = false
}

// === 导航与操作 ===

const handleNavigate = ({ menu, tab, action }) => {
  activeMenu.value = menu
  // 如果后续需要基于 action 传参，可通过 store 或事件总线扩展
}

const handleLogout = async () => {
  stopStatusCheck()
  try {
    // 调用后端 API 更新会话状态
    await authAPI.logout()
  } catch (e) {
    // 即使 API 调用失败也继续登出
    console.error('登出 API 调用失败:', e)
  }
  authStore.logout()
  router.push('/login')
}

let refreshInterval = null

onMounted(() => {
  startStatusCheck()
  loadAllData()
  
  // 监听 hash 变化
  window.addEventListener('hashchange', handleHashChange)
  
  refreshInterval = setInterval(() => {
    // 静默刷新，保证仪表盘数据及时
    loadCourses()
    loadStats()
  }, 30000) // 30s refresh
})

onUnmounted(() => {
  stopStatusCheck()
  window.removeEventListener('hashchange', handleHashChange)
  if (refreshInterval) clearInterval(refreshInterval)
})
</script>

<template>
  <div class="min-h-screen relative overflow-hidden font-sans">
    <!-- Background Decoration -->
    <div class="fixed inset-0 pointer-events-none z-0">
      <div class="absolute -top-[20%] -left-[10%] w-[50%] h-[50%] bg-tianlv/5 rounded-full blur-3xl animate-float"></div>
      <div class="absolute top-[40%] -right-[10%] w-[40%] h-[40%] bg-qinghua/5 rounded-full blur-3xl animate-float" style="animation-delay: 2s"></div>
    </div>

    <!-- Sidebar -->
    <aside 
      :class="['fixed top-0 left-0 h-full bg-white/80 backdrop-blur-xl border-r border-slate-200/60 z-50 transition-all duration-500 ease-out shadow-lg shadow-slate-200/50', 
              sidebarOpen ? 'w-64' : 'w-20']"
    >
      <div class="p-6 flex items-center gap-3 mb-6">
        <div class="w-10 h-10 rounded-xl bg-gradient-to-br from-tianlv to-qingsong flex items-center justify-center flex-shrink-0 shadow-lg shadow-tianlv/20">
          <GraduationCap class="w-6 h-6 text-white" />
        </div>
        <span v-if="sidebarOpen" class="text-xl font-bold text-shuimo font-song tracking-wide animate-fade-in">智慧课堂</span>
      </div>

      <nav class="px-3 space-y-1 flex-1 overflow-y-auto">
        <!-- 分组菜单 -->
        <div v-for="group in menuGroups" :key="group.id" class="mb-4">
          <!-- 分组标签 -->
          <div v-if="sidebarOpen && group.label" class="px-4 py-2 text-[10px] font-bold uppercase tracking-wider text-shuimo/40">
            {{ group.label }}
          </div>
          <div v-else class="h-2"></div>

          <!-- 菜单项 -->
          <div class="space-y-1 relative">
            <button
              v-for="item in group.items"
              :key="item.id"
              @click="activeMenu = item.id"
              :class="[
                'w-full flex items-center gap-3 px-4 py-3 rounded-xl transition-all duration-300 group relative overflow-hidden menu-item-glow',
                activeMenu === item.id
                  ? 'bg-gradient-to-r from-tianlv to-qingsong text-white shadow-lg shadow-tianlv/25'
                  : 'text-shuimo/70 hover:bg-slate-100/80 hover:text-shuimo'
              ]"
            >
              <!-- 左侧滑动指示器 -->
              <div
                v-if="activeMenu === item.id"
                class="absolute left-0 top-1/2 -translate-y-1/2 w-1 h-8 bg-white/40 rounded-r-full"
              ></div>

              <component
                :is="item.icon"
                :class="[
                  'w-5 h-5 transition-all duration-300 flex-shrink-0',
                  activeMenu === item.id ? 'scale-110' : 'group-hover:scale-110 group-hover:text-tianlv'
                ]"
              />
              <span
                v-if="sidebarOpen"
                class="font-medium whitespace-nowrap transition-opacity duration-200"
              >
                {{ item.label }}
              </span>

              <!-- 悬停光效 -->
              <div
                v-if="activeMenu !== item.id"
                class="absolute inset-0 bg-gradient-to-r from-tianlv/5 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300 -z-10"
              ></div>
            </button>
          </div>
        </div>
      </nav>

      <div class="absolute bottom-6 left-0 right-0 px-3 space-y-2">
        <button
          @click="router.push('/')"
          class="w-full flex items-center gap-3 px-4 py-3 rounded-xl text-qinghua hover:bg-qinghua/5 transition-colors"
        >
          <Home class="w-5 h-5" />
          <span v-if="sidebarOpen" class="font-medium">回到首页</span>
        </button>
        <button
          @click="handleLogout"
          class="w-full flex items-center gap-3 px-4 py-3 rounded-xl text-yanzhi hover:bg-yanzhi/5 transition-colors"
        >
          <LogOut class="w-5 h-5" />
          <span v-if="sidebarOpen" class="font-medium">退出登录</span>
        </button>
      </div>
    </aside>

    <!-- Main Content -->
    <main 
      :class="['relative z-10 min-h-screen transition-all duration-500 ease-out flex flex-col', sidebarOpen ? 'ml-64' : 'ml-20']"
    >
      <!-- Header -->
      <header class="sticky top-0 z-40 px-8 py-5 flex items-center justify-between bg-white/60 backdrop-blur-xl border-b border-white/50">
        <div class="flex items-center gap-4">
          <button @click="sidebarOpen = !sidebarOpen" class="p-2 hover:bg-white/50 rounded-xl transition-colors text-shuimo">
            <Menu v-if="!sidebarOpen" class="w-5 h-5" />
            <X v-else class="w-5 h-5" />
          </button>
          
          <h2 class="text-xl font-bold text-shuimo font-song">{{ menuItems.find(i => i.id === activeMenu)?.label }}</h2>
        </div>

        <div class="flex items-center gap-6">
          <button class="relative p-2 hover:bg-white/50 rounded-xl transition-colors group">
            <Bell class="w-5 h-5 text-shuimo group-hover:animate-bell-shake" />
            <span class="absolute top-1.5 right-1.5 w-2 h-2 bg-yanzhi rounded-full ring-2 ring-white animate-pulse"></span>
          </button>
          
          <div class="flex items-center gap-3 pl-6 border-l border-slate-200/50">
            <div class="text-right hidden md:block">
              <p class="text-sm font-bold text-shuimo">{{ authStore.user?.username || '教师' }}</p>
              <p class="text-xs text-shuimo/50">{{ roleLabel }}</p>
            </div>
            <div class="w-10 h-10 rounded-full bg-gradient-to-br from-tianlv to-qingsong flex items-center justify-center text-white font-bold shadow-lg shadow-tianlv/20 cursor-pointer hover:scale-105 transition-transform" @click="activeMenu = 'settings'">
              {{ authStore.user?.username?.charAt(0).toUpperCase() || 'T' }}
            </div>
          </div>
        </div>
      </header>

      <!-- Content Area with Page Transition -->
      <div class="p-8 flex-1">
        <Transition :name="'page-slide-' + slideDirection" mode="out-in">
          <!-- Dashboard -->
          <TeacherDashboard 
            v-if="activeMenu === 'dashboard'" 
            key="dashboard"
            :stats="dashboardStats"
            :todos="todoList"
            :activities="recentActivities"
            :loading="loading"
            @navigate="handleNavigate"
          />

          <!-- Courses -->
          <TeacherCourses 
            v-else-if="activeMenu === 'courses'" 
            key="courses"
            :courses="courses"
            :loading="loading"
            @refresh="loadAllData"
          />

          <!-- Homeworks -->
          <TeacherHomeworks 
            v-else-if="activeMenu === 'homework'" 
            key="homework"
            :courses="courses"
          />

          <!-- Students -->
          <TeacherStudents 
            v-else-if="activeMenu === 'students'" 
            key="students"
            :students="students"
            :courses="courses"
          />
          
          <!-- Course Analytics -->
          <CourseAnalytics 
            v-else-if="activeMenu === 'analytics'" 
            key="analytics"
            :courses="courses"
          />
          
          <!-- Teaching Calendar -->
          <TeachingCalendar 
            v-else-if="activeMenu === 'calendar'" 
            key="calendar"
          />
          
          <!-- Announcements -->
          <AnnouncementHistory 
            v-else-if="activeMenu === 'announcements'" 
            key="announcements"
          />
          
          <!-- Discussion -->
          <TeacherDiscussion 
            v-else-if="activeMenu === 'discussion'" 
            key="discussion"
            :user="authStore.user"
          />

          <!-- Profile -->
          <TeacherProfile 
            v-else-if="activeMenu === 'settings'" 
            key="settings"
            :user="authStore.user"
            :profile="teacherProfile"
            :settings="notificationSettings"
            @update:profile="(p) => teacherProfile = p"
            @update:settings="(s) => notificationSettings = s"
            @save-profile="() => {}"
          />
        </Transition>
      </div>
    </main>
  </div>
</template>

<style scoped>
/* 页面切换过渡动画 - 向上滑动（切换到下方菜单项） */
.page-slide-up-enter-active {
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}

.page-slide-up-leave-active {
  transition: all 0.2s ease-in;
}

.page-slide-up-enter-from {
  opacity: 0;
  transform: translateY(30px);
}

.page-slide-up-leave-to {
  opacity: 0;
  transform: translateY(-20px);
}

/* 页面切换过渡动画 - 向下滑动（切换到上方菜单项） */
.page-slide-down-enter-active {
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}

.page-slide-down-leave-active {
  transition: all 0.2s ease-in;
}

.page-slide-down-enter-from {
  opacity: 0;
  transform: translateY(-30px);
}

.page-slide-down-leave-to {
  opacity: 0;
  transform: translateY(20px);
}

/* 侧边栏滑动指示器动画 */
.slide-indicator-enter-active {
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

/* 菜单项光效动画 */
.menu-item-glow {
  position: relative;
  overflow: hidden;
}

.menu-item-glow::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.2), transparent);
  transform: translateX(-100%);
  opacity: 0;
  pointer-events: none;
}

.menu-item-glow:hover::after {
  animation: menu-shimmer 0.6s ease-out;
}

@keyframes menu-shimmer {
  0% {
    opacity: 0;
    transform: translateX(-100%);
  }
  50% {
    opacity: 0.3;
  }
  100% {
    opacity: 0;
    transform: translateX(100%);
  }
}

/* 通知铃铛摇晃动画 */
.animate-bell-shake {
  animation: bell-shake 0.6s ease-in-out;
}

@keyframes bell-shake {
  0%, 100% {
    transform: rotate(0deg);
  }
  15%, 45%, 75% {
    transform: rotate(-12deg);
  }
  30%, 60%, 90% {
    transform: rotate(12deg);
  }
}
</style>
