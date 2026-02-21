<script setup>
import { ref, onMounted, onUnmounted, onActivated, onDeactivated, computed, watch, defineAsyncComponent } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import {
  userAPI, courseAPI, statsAPI,
  startStatusCheck, stopStatusCheck
} from '../services/api'
import { formatTimeCN } from '../utils/datetime'
import {
  LayoutDashboard, Users, BookOpen, Settings,
  LogOut, Menu, X, Home, GraduationCap,
  FileText, Megaphone
} from 'lucide-vue-next'

// 子组件（按需异步加载，降低主包体积）
const AdminDashboard = defineAsyncComponent(() => import('./admin/AdminDashboard.vue'))
const AdminUsers = defineAsyncComponent(() => import('./admin/AdminUsers.vue'))
const AdminCourses = defineAsyncComponent(() => import('./admin/AdminCourses.vue'))
const AdminSystem = defineAsyncComponent(() => import('./admin/AdminSystem.vue'))
const AdminLogs = defineAsyncComponent(() => import('./admin/AdminLogs.vue'))
const AdminAnnouncements = defineAsyncComponent(() => import('./admin/AdminAnnouncements.vue'))

const router = useRouter()
const authStore = useAuthStore()

// 布局状态
const sidebarOpen = ref(true)

// 优先从URL hash读取，其次从sessionStorage，最后默认dashboard
const getInitialMenu = () => {
  const hash = window.location.hash.replace('#', '')
  const validMenus = ['dashboard', 'users', 'courses', 'logs', 'announcements', 'settings']
  if (hash && validMenus.includes(hash)) {
    return hash
  }
  const stored = sessionStorage.getItem('adminActiveMenu')
  if (stored && validMenus.includes(stored)) {
    return stored
  }
  return 'dashboard'
}
const activeMenu = ref(getInitialMenu())
const prevMenuIndex = ref(0)
const slideDirection = ref('up') // 'up' 或 'down'

const userInitialFilter = ref('all')
const courseInitialFilter = ref('all')

// 计算当前菜单项的索引（用于动效方向判断）
const getMenuIndex = (menuId) => {
  return menuItems.findIndex(item => item.id === menuId)
}

// 监听activeMenu变化，同步到URL hash和sessionStorage，并计算动效方向
watch(activeMenu, (newMenu, oldMenu) => {
  window.history.replaceState(window.history.state, '', `#${newMenu}`)
  sessionStorage.setItem('adminActiveMenu', newMenu)

  // 计算切换方向
  const newIndex = getMenuIndex(newMenu)
  const oldIndex = oldMenu ? getMenuIndex(oldMenu) : 0
  slideDirection.value = newIndex > oldIndex ? 'up' : 'down'
  prevMenuIndex.value = newIndex
})

// 数据状态
const loading = ref(false)
const stats = ref({}) // 系统统计
const courseStats = ref({}) // 课程统计
const todayStats = ref({}) // 今日活跃
const recentUsers = ref([]) // 最近活跃用户
const allUsers = ref([]) // 全量用户
const courses = ref([]) // 全量课程
const lastUpdateTime = ref('')

// 菜单配置
const menuItems = [
  { id: 'dashboard', label: '数据概览', icon: LayoutDashboard },
  { id: 'users', label: '用户管理', icon: Users },
  { id: 'courses', label: '课程管理', icon: BookOpen, badge: 'pending' },
  { id: 'logs', label: '操作日志', icon: FileText },
  { id: 'announcements', label: '系统公告', icon: Megaphone },
  { id: 'settings', label: '系统设置', icon: Settings },
]

// 数据请求
const refreshData = async () => {
  loading.value = true
  try {
    const [userRes, courseRes, adminStatsRes] = await Promise.all([
      userAPI.getStats(),
      courseAPI.getAll(),
      statsAPI.getAdminDashboard()
    ])

    // 用户统计数据
    const uData = userRes.data
    recentUsers.value = uData.recentUsers || []
    allUsers.value = uData.allUsers || []

    // 课程数据
    courses.value = courseRes.data || []

    // 管理员仪表盘统计（后端真实数据）
    const adminStats = adminStatsRes.data
    stats.value = {
      total: adminStats.totalUsers || 0,
      students: adminStats.totalStudents || 0,
      teachers: adminStats.totalTeachers || 0,
      admins: adminStats.totalAdmins || 0
    }

    courseStats.value = {
      total: adminStats.totalCourses || courses.value.length,
      pending: adminStats.pendingCourses || 0,
      published: adminStats.publishedCourses || 0,
      offline: courses.value.filter(c => c.status === 'OFFLINE').length
    }

    // 今日统计（后端真实数据）
    todayStats.value = {
      newUsers: adminStats.newUsersToday || 0,
      activeUsers: adminStats.activeUsersToday || 0,
      onlineUsers: adminStats.onlineUsers || 0,
      newStudents: 0,
      newTeachers: 0
    }

    lastUpdateTime.value = adminStats.timestamp
      ? formatTimeCN(adminStats.timestamp)
      : formatTimeCN(new Date())
  } catch (e) {
    console.error('Data refresh failed', e)
  } finally {
    loading.value = false
  }
}

// 生命周期
let refreshTimer = null

// 启动后台数据轮询（兼容 KeepAlive 激活/失活）
const startRefreshTimer = () => {
  if (refreshTimer) return
  refreshTimer = setInterval(refreshData, 30000) // 每 30 秒自动刷新
}

// 停止后台数据轮询，避免页面缓存时继续请求
const stopRefreshTimer = () => {
  if (!refreshTimer) return
  clearInterval(refreshTimer)
  refreshTimer = null
}

onMounted(() => {
  startStatusCheck()
  refreshData()
  startRefreshTimer()
})

onUnmounted(() => {
  stopRefreshTimer()
  stopStatusCheck()
})

onActivated(() => {
  startStatusCheck()
  startRefreshTimer()
})
onDeactivated(() => {
  stopRefreshTimer()
  stopStatusCheck()
})

// 事件处理
const handleLogout = async () => {
  try {
    const { authAPI } = await import('../services/api')
    await authAPI.logout()
  } catch (e) {
    console.error('登出 API 调用失败:', e)
  }
  stopStatusCheck()
  authStore.logout()
  router.push('/login')
}

const handleNavigate = ({ menu, filter }) => {
  activeMenu.value = menu
  if (menu === 'courses') {
    courseInitialFilter.value = filter ?? 'all'
  }
  if (menu === 'users') {
    userInitialFilter.value = filter ?? 'all'
  }
}

const disabledUsersCount = computed(() => allUsers.value.filter(u => u.status === 0).length)

</script>

<template>
  <!-- 已按需求移除“跟随系统/快捷键”功能，容器不再依赖主题切换状态 -->
  <div class="min-h-screen flex transition-colors duration-300">
    <!-- 全局自定义滚动条与动效注入 -->
    <component is="style">
      ::-webkit-scrollbar {
        width: 6px;
        height: 6px;
      }
      ::-webkit-scrollbar-track {
        background: transparent;
      }
      ::-webkit-scrollbar-thumb {
        background: linear-gradient(180deg, rgba(82, 45, 128, 0.2), rgba(18, 53, 85, 0.2));
        border-radius: 10px;
      }
      ::-webkit-scrollbar-thumb:hover {
        background: linear-gradient(180deg, rgba(82, 45, 128, 0.4), rgba(18, 53, 85, 0.4));
      }
    </component>

    <!-- Sidebar -->
    <aside
      class="fixed top-0 left-0 h-full w-64 bg-white/80 dark:bg-slate-900/90 backdrop-blur-xl border-r border-slate-200/60 dark:border-slate-700/60 z-50 sidebar-spring flex flex-col"
      :class="sidebarOpen ? 'sidebar-expanded' : 'sidebar-collapsed'"
    >
      <!-- Logo -->
      <div class="h-20 flex items-center px-6 border-b border-slate-100/50 dark:border-slate-700/50">
        <div class="w-8 h-8 rounded-xl bg-gradient-to-br from-zijinghui to-qianniuzi flex items-center justify-center shrink-0 shadow-lg shadow-zijinghui/20">
          <GraduationCap class="w-5 h-5 text-white" />
        </div>
        <Transition name="sidebar-text">
          <span
            v-if="sidebarOpen"
            class="ml-3 font-bold text-lg text-shuimo dark:text-slate-200 font-song tracking-wide truncate"
          >
            管理后台
          </span>
        </Transition>
      </div>

      <!-- Navigation -->
      <nav class="flex-1 p-4 space-y-2 overflow-y-auto custom-scrollbar">
        <button
          v-for="item in menuItems"
          :key="item.id"
          @click="activeMenu = item.id"
          class="w-full flex items-center gap-3 px-4 py-3 rounded-xl transition-all duration-300 group relative overflow-hidden"
          :class="activeMenu === item.id
            ? 'bg-gradient-to-r from-zijinghui to-qianniuzi text-white shadow-lg shadow-zijinghui/30'
            : 'text-shuimo/60 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800 hover:text-shuimo dark:hover:text-slate-200'"
        >
          <component :is="item.icon" class="w-5 h-5 transition-transform" :class="{'scale-110': activeMenu === item.id}" />
          <Transition name="sidebar-text">
            <span v-if="sidebarOpen" class="font-medium whitespace-nowrap">{{ item.label }}</span>
          </Transition>

          <!-- 待审核课程徽标 -->
          <span
            v-if="item.badge === 'pending' && courseStats.pending > 0 && sidebarOpen"
            class="ml-auto px-2 py-0.5 text-xs font-bold rounded-full"
            :class="activeMenu === item.id ? 'bg-white/20 text-white' : 'bg-amber-100 dark:bg-amber-900/30 text-amber-600 dark:text-amber-400'"
          >
            {{ courseStats.pending }}
          </span>

          <div v-if="activeMenu === item.id" class="absolute inset-0 bg-white/10 opacity-0 group-hover:opacity-100 transition-opacity"></div>
        </button>
      </nav>

      <!-- Bottom Actions -->
      <div class="p-4 border-t border-slate-100/50 dark:border-slate-700/50 space-y-2">
        <button @click="router.push('/')" class="w-full flex items-center gap-3 px-4 py-3 rounded-xl text-qinghua hover:bg-qinghua/10 dark:hover:bg-qinghua/20 transition-colors">
          <Home class="w-5 h-5" />
          <Transition name="sidebar-text">
            <span v-if="sidebarOpen" class="font-medium">回到首页</span>
          </Transition>
        </button>
        <button @click="handleLogout" class="w-full flex items-center gap-3 px-4 py-3 rounded-xl text-yanzhi hover:bg-yanzhi/10 dark:hover:bg-yanzhi/20 transition-colors">
          <LogOut class="w-5 h-5" />
          <Transition name="sidebar-text">
            <span v-if="sidebarOpen" class="font-medium">退出登录</span>
          </Transition>
        </button>
      </div>
    </aside>

    <!-- 主内容区 -->
    <main
      class="flex-1 transition-all duration-300 min-h-screen flex flex-col bg-slate-50 dark:bg-slate-900"
      :class="sidebarOpen ? 'ml-64' : 'ml-20'"
    >
      <!-- 头部区域 -->
      <header class="sticky top-0 z-40 bg-white/70 dark:bg-slate-800/70 backdrop-blur-md border-b border-slate-200/50 dark:border-slate-700/50 px-6 h-20 flex items-center justify-between">
        <div class="flex items-center gap-4">
          <button
            @click="sidebarOpen = !sidebarOpen"
            class="p-2 rounded-xl hover:bg-slate-100 dark:hover:bg-slate-700 text-shuimo/60 dark:text-slate-400 hover:text-shuimo dark:hover:text-slate-200 transition-colors"
          >
            <component :is="sidebarOpen ? X : Menu" class="w-5 h-5" />
          </button>

          <!-- 面包屑标题 -->
          <h2 class="text-lg font-bold text-shuimo dark:text-slate-200 font-song">
            {{ menuItems.find(i => i.id === activeMenu)?.label }}
          </h2>
        </div>

        <div class="flex items-center gap-4">
          <div class="flex items-center gap-3 pl-4 border-l border-slate-200 dark:border-slate-600">
             <div class="text-right hidden md:block">
                <p class="text-sm font-bold text-shuimo dark:text-slate-200">{{ authStore.user?.username || '管理员' }}</p>
                <p class="text-xs text-shuimo/50 dark:text-slate-500">管理员</p>
             </div>
             <div class="w-10 h-10 rounded-full bg-slate-200 dark:bg-slate-600 border-2 border-white dark:border-slate-500 shadow-sm overflow-hidden">
                <img :src="authStore.user?.avatar || `https://ui-avatars.com/api/?name=${authStore.user?.username || 'Admin'}&background=random`" :alt="authStore.user?.username || 'Admin'" />
             </div>
          </div>
        </div>
      </header>

      <!-- 视图内容 -->
      <div class="p-6 md:p-8 flex-1 overflow-x-hidden">
        <Transition
          :name="slideDirection === 'up' ? 'slide-up' : 'slide-down'"
          mode="out-in"
        >
          <!-- 使用显式分支渲染，避免动态组件在切换时出现空白 -->
          <div :key="activeMenu">
            <AdminDashboard
              v-if="activeMenu === 'dashboard'"
              :stats="stats"
              :today-stats="todayStats"
              :course-stats="courseStats"
              :recent-users="recentUsers"
              :disabled-users-count="disabledUsersCount"
              :last-update-time="lastUpdateTime"
              :loading="loading"
              @navigate="handleNavigate"
              @refresh="refreshData"
            />
            <AdminUsers
              v-else-if="activeMenu === 'users'"
              :users="allUsers"
              :loading="loading"
              :initial-filter="userInitialFilter"
              @refresh="refreshData"
            />
            <AdminCourses
              v-else-if="activeMenu === 'courses'"
              :courses="courses"
              :loading="loading"
              :initial-filter="courseInitialFilter"
              @refresh="refreshData"
            />
            <AdminLogs v-else-if="activeMenu === 'logs'" />
            <AdminAnnouncements v-else-if="activeMenu === 'announcements'" />
            <AdminSystem v-else-if="activeMenu === 'settings'" />
          </div>
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

/* P1 收尾：侧边栏改为 clip-path 过渡，移除 width 布局属性动画风险 */
.sidebar-spring {
  transition: clip-path var(--motion-duration-medium) var(--motion-ease-standard);
  will-change: clip-path;
}

.sidebar-expanded {
  clip-path: inset(0 0 0 0);
}

.sidebar-collapsed {
  clip-path: inset(0 calc(100% - 5rem) 0 0);
}

/* 页面切换动画 */
.page-fade-enter-active,
.page-fade-leave-active {
  /* P1：仅过渡透明度与位移，减少无关属性参与动画 */
  transition:
    opacity 0.25s var(--motion-ease-standard),
    transform 0.25s var(--motion-ease-standard);
}

.page-fade-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.page-fade-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

/* 侧边栏文字淡出动画 */
.sidebar-text-enter-active {
  transition:
    opacity var(--motion-duration-base) var(--motion-ease-standard) 0.1s,
    transform var(--motion-duration-base) var(--motion-ease-standard) 0.1s;
}

.sidebar-text-leave-active {
  transition:
    opacity var(--motion-duration-fast) var(--motion-ease-standard),
    transform var(--motion-duration-fast) var(--motion-ease-standard);
}

.sidebar-text-enter-from,
.sidebar-text-leave-to {
  opacity: 0;
  transform: translateX(-8px);
}

/* 页面切换过渡动画 - 向上滑入 */
.slide-up-enter-active,
.slide-up-leave-active {
  transition:
    opacity var(--motion-duration-medium) var(--motion-ease-standard),
    transform var(--motion-duration-medium) var(--motion-ease-standard);
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
  transition:
    opacity var(--motion-duration-medium) var(--motion-ease-standard),
    transform var(--motion-duration-medium) var(--motion-ease-standard);
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
