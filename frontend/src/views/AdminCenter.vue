<script setup>
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import {
  userAPI, courseAPI, statsAPI,
  startStatusCheck, stopStatusCheck
} from '../services/api'
import {
  LayoutDashboard, Users, BookOpen, Settings,
  LogOut, Menu, X, Home, GraduationCap,
  FileText, Megaphone, Sun, Moon, Monitor, Search, Keyboard
} from 'lucide-vue-next'

// Composables
import { useTheme } from '../composables/useTheme'
import { useKeyboard, keyboardShortcuts } from '../composables/useKeyboard'

// 子组件
import AdminDashboard from './admin/AdminDashboard.vue'
import AdminUsers from './admin/AdminUsers.vue'
import AdminCourses from './admin/AdminCourses.vue'
import AdminSystem from './admin/AdminSystem.vue'
import AdminLogs from './admin/AdminLogs.vue'
import AdminAnnouncements from './admin/AdminAnnouncements.vue'

const router = useRouter()
const authStore = useAuthStore()

// 主题切换
const { isDark, theme, setTheme, toggleTheme } = useTheme()

// 布局状态
const sidebarOpen = ref(true)
const showSearch = ref(false)
const showKeyboardHelp = ref(false)
const searchQuery = ref('')

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

// 快捷键处理
useKeyboard({
  onSearch: () => {
    showSearch.value = true
    showKeyboardHelp.value = false
  },
  onEscape: () => {
    if (showSearch.value) {
      showSearch.value = false
      searchQuery.value = ''
    } else if (showKeyboardHelp.value) {
      showKeyboardHelp.value = false
    }
  },
  onRefresh: () => {
    refreshData()
  },
  onMenuSwitch: (index) => {
    if (index < menuItems.length) {
      activeMenu.value = menuItems[index].id
    }
  },
  onThemeToggle: () => {
    toggleTheme()
  }
})

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
      ? new Date(adminStats.timestamp).toLocaleTimeString('zh-CN')
      : new Date().toLocaleTimeString('zh-CN')
  } catch (e) {
    console.error('Data refresh failed', e)
  } finally {
    loading.value = false
  }
}

// 生命周期
let refreshTimer = null
onMounted(() => {
  startStatusCheck()
  refreshData()
  refreshTimer = setInterval(refreshData, 30000) // 每 30 秒自动刷新
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
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

// 搜索功能
const handleSearch = () => {
  if (!searchQuery.value.trim()) return
  // 根据搜索内容跳转到对应页面
  const query = searchQuery.value.toLowerCase()
  if (query.includes('用户') || query.includes('user')) {
    activeMenu.value = 'users'
  } else if (query.includes('课程') || query.includes('course')) {
    activeMenu.value = 'courses'
  } else if (query.includes('日志') || query.includes('log')) {
    activeMenu.value = 'logs'
  } else if (query.includes('公告') || query.includes('announcement')) {
    activeMenu.value = 'announcements'
  } else if (query.includes('设置') || query.includes('setting')) {
    activeMenu.value = 'settings'
  }
  showSearch.value = false
  searchQuery.value = ''
}

const disabledUsersCount = computed(() => allUsers.value.filter(u => u.status === 0).length)

const componentMap = {
  dashboard: AdminDashboard,
  users: AdminUsers,
  courses: AdminCourses,
  logs: AdminLogs,
  announcements: AdminAnnouncements,
  settings: AdminSystem
}

const currentComponent = computed(() => componentMap[activeMenu.value])

const currentProps = computed(() => {
  if (activeMenu.value === 'dashboard') {
    return {
      stats: stats.value,
      todayStats: todayStats.value,
      courseStats: courseStats.value,
      recentUsers: recentUsers.value,
      disabledUsersCount: disabledUsersCount.value,
      lastUpdateTime: lastUpdateTime.value,
      loading: loading.value
    }
  }

  if (activeMenu.value === 'users') {
    return {
      users: allUsers.value,
      loading: loading.value,
      initialFilter: userInitialFilter.value
    }
  }

  if (activeMenu.value === 'courses') {
    return {
      courses: courses.value,
      loading: loading.value,
      initialFilter: courseInitialFilter.value
    }
  }

  return {}
})

const currentListeners = computed(() => {
  if (activeMenu.value === 'dashboard') {
    return { navigate: handleNavigate, refresh: refreshData }
  }
  if (activeMenu.value === 'users' || activeMenu.value === 'courses') {
    return { refresh: refreshData }
  }
  return {}
})

// 主题图标
const themeIcon = computed(() => {
  if (theme.value === 'system') return Monitor
  return isDark.value ? Moon : Sun
})

// 切换到下一个主题模式
const cycleTheme = () => {
  const themes = ['light', 'dark', 'system']
  const currentIndex = themes.indexOf(theme.value)
  const nextIndex = (currentIndex + 1) % themes.length
  setTheme(themes[nextIndex])
}

const themeLabel = computed(() => {
  const labels = { light: '浅色', dark: '深色', system: '跟随系统' }
  return labels[theme.value] || '浅色'
})
</script>

<template>
  <div class="min-h-screen flex transition-colors duration-300" :class="{ 'dark': isDark }">
    <!-- 全局搜索弹窗 -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="showSearch" class="fixed inset-0 z-[100] flex items-start justify-center pt-[20vh]">
          <div class="fixed inset-0 bg-black/30 dark:bg-black/50 backdrop-blur-sm" @click="showSearch = false"></div>
          <div class="relative w-full max-w-lg mx-4 bg-white dark:bg-slate-800 rounded-2xl shadow-2xl overflow-hidden">
            <div class="flex items-center gap-3 p-4 border-b border-slate-100 dark:border-slate-700">
              <Search class="w-5 h-5 text-shuimo/50 dark:text-slate-400" />
              <input
                id="admin-search-input"
                v-model="searchQuery"
                type="text"
                placeholder="搜索菜单、功能..."
                aria-label="全局搜索菜单"
                class="flex-1 bg-transparent outline-none text-shuimo dark:text-slate-200 placeholder:text-shuimo/40 dark:placeholder:text-slate-500"
                @keydown.enter="handleSearch"
                @keydown.escape="showSearch = false"
                autofocus
              />
              <kbd class="px-2 py-1 text-xs bg-slate-100 dark:bg-slate-700 text-shuimo/60 dark:text-slate-400 rounded">Esc</kbd>
            </div>
            <div class="p-4 text-sm text-shuimo/60 dark:text-slate-400">
              输入关键词搜索：用户、课程、日志、公告、设置
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- 快捷键帮助弹窗 -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="showKeyboardHelp" class="fixed inset-0 z-[100] flex items-center justify-center">
          <div class="fixed inset-0 bg-black/30 dark:bg-black/50 backdrop-blur-sm" @click="showKeyboardHelp = false"></div>
          <div class="relative w-full max-w-sm mx-4 bg-white dark:bg-slate-800 rounded-2xl shadow-2xl p-6">
            <div class="flex items-center justify-between mb-4">
              <h3 class="text-lg font-bold text-shuimo dark:text-slate-200">键盘快捷键</h3>
              <button @click="showKeyboardHelp = false" class="p-1 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-700">
                <X class="w-5 h-5 text-shuimo/50 dark:text-slate-400" />
              </button>
            </div>
            <div class="space-y-3">
              <div v-for="shortcut in keyboardShortcuts" :key="shortcut.key" class="flex items-center justify-between">
                <span class="text-sm text-shuimo/70 dark:text-slate-300">{{ shortcut.description }}</span>
                <kbd class="px-2 py-1 text-xs bg-slate-100 dark:bg-slate-700 text-shuimo/60 dark:text-slate-400 rounded font-mono">{{ shortcut.key }}</kbd>
              </div>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

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

      .sidebar-spring {
        transition: all 0.5s cubic-bezier(0.34, 1.56, 0.64, 1);
      }
    </component>

    <!-- Sidebar -->
    <aside
      class="fixed top-0 left-0 h-full bg-white/80 dark:bg-slate-900/90 backdrop-blur-xl border-r border-slate-200/60 dark:border-slate-700/60 z-50 sidebar-spring flex flex-col"
      :class="sidebarOpen ? 'w-64' : 'w-20'"
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
          v-for="(item, index) in menuItems"
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

          <!-- 快捷键提示 -->
          <Transition name="sidebar-text">
            <kbd v-if="sidebarOpen" class="ml-auto px-1.5 py-0.5 text-[10px] rounded opacity-50" :class="activeMenu === item.id ? 'bg-white/20' : 'bg-slate-200 dark:bg-slate-700'">{{ index + 1 }}</kbd>
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
        <!-- 主题切换 -->
        <button
          @click="cycleTheme"
          class="w-full flex items-center gap-3 px-4 py-3 rounded-xl text-shuimo/60 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
          :title="`当前: ${themeLabel}`"
        >
          <component :is="themeIcon" class="w-5 h-5" />
          <Transition name="sidebar-text">
            <span v-if="sidebarOpen" class="font-medium">{{ themeLabel }}</span>
          </Transition>
        </button>

        <!-- 快捷键帮助 -->
        <button
          @click="showKeyboardHelp = true"
          class="w-full flex items-center gap-3 px-4 py-3 rounded-xl text-shuimo/60 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
        >
          <Keyboard class="w-5 h-5" />
          <Transition name="sidebar-text">
            <span v-if="sidebarOpen" class="font-medium">快捷键</span>
          </Transition>
        </button>

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
          <!-- 搜索按钮 -->
          <button
            @click="showSearch = true"
            class="flex items-center gap-2 px-3 py-2 rounded-xl bg-slate-100 dark:bg-slate-700 hover:bg-slate-200 dark:hover:bg-slate-600 text-shuimo/60 dark:text-slate-400 transition-colors"
          >
            <Search class="w-4 h-4" />
            <span class="text-sm hidden md:inline">搜索</span>
            <kbd class="px-1.5 py-0.5 text-[10px] bg-white dark:bg-slate-600 rounded hidden md:inline">⌘K</kbd>
          </button>

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
          <KeepAlive>
            <component
              :is="currentComponent"
              :key="activeMenu"
              v-bind="currentProps"
              v-on="currentListeners"
            />
          </KeepAlive>
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

/* 页面切换动画 */
.page-fade-enter-active,
.page-fade-leave-active {
  transition: all 0.25s ease-out;
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
  transition: all 0.2s ease-out 0.1s;
}

.sidebar-text-leave-active {
  transition: all 0.15s ease-in;
}

.sidebar-text-enter-from,
.sidebar-text-leave-to {
  opacity: 0;
  transform: translateX(-8px);
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
