<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import {
  GraduationCap, LayoutDashboard, BookOpen,
  FileText, History, MessageSquare, Settings,
  LogOut, Menu, X, Home
} from 'lucide-vue-next'
import { startStatusCheck, stopStatusCheck, authAPI, userAPI } from '../services/api'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

// 布局状态
const isMobile = ref(false)
const sidebarOpen = ref(window.innerWidth >= 768)

const handleResize = () => {
  isMobile.value = window.innerWidth < 768
  if (isMobile.value) {
    sidebarOpen.value = false
  } else {
    sidebarOpen.value = true
  }
}

// 菜单配置
const menuItems = [
  { id: 'dashboard', label: '学习概览', icon: LayoutDashboard, path: '/student/dashboard' },
  { id: 'courses', label: '我的课程', icon: BookOpen, path: '/student/courses' },
  { id: 'homework', label: '作业中心', icon: FileText, path: '/student/homeworks' },
  { id: 'records', label: '学习记录', icon: History, path: '/student/records' },
  { id: 'questions', label: '问答互动', icon: MessageSquare, path: '/student/questions' },
  { id: 'settings', label: '个人设置', icon: Settings, path: '/student/profile' },
]

const handleMenuClick = (path) => {
  router.push(path)
  if (isMobile.value) {
    sidebarOpen.value = false
  }
}

const handleLogout = async () => {
   try {
     await authAPI.logout()
   } catch (e) {}
   stopStatusCheck()
   authStore.logout()
   router.push('/login')
}

// 获取用户头像等信息
// 优先使用 store 中的信息，如果 store 中不全，尝试获取一次
const loadUserProfile = async () => {
  try {
     const userId = authStore.user?.id
     if (userId) {
       const res = await userAPI.getById(userId)
       if (res.data) {
          authStore.updateUser(res.data)
       }
     }
  } catch(e) {
    console.error('Failed to load user profile in layout', e)
  }
}

onMounted(() => {
   startStatusCheck()
   window.addEventListener('resize', handleResize)
   handleResize() // Initial check
   loadUserProfile()
})

onUnmounted(() => {
   stopStatusCheck()
   window.removeEventListener('resize', handleResize)
})
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
          @click="handleMenuClick(item.path)"
          class="w-full flex items-center gap-3 px-4 py-3 rounded-xl transition-all duration-300 group relative overflow-hidden focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-qinghua"
          :class="route.path.startsWith(item.path) 
            ? 'bg-gradient-to-r from-qinghua to-halanzi text-white shadow-lg shadow-qinghua/30' 
            : 'text-muted hover:bg-slate-100 hover:text-shuimo'"
          :aria-label="item.label"
        >
          <component :is="item.icon" class="w-5 h-5 transition-transform" :class="{'scale-110': route.path.startsWith(item.path)}" aria-hidden="true" />
          <span v-if="sidebarOpen" class="font-medium whitespace-nowrap">{{ item.label }}</span>
          <div v-if="route.path.startsWith(item.path)" class="absolute inset-0 bg-white/10 opacity-0 group-hover:opacity-100 transition-opacity"></div>
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
                  :src="authStore.user?.avatar || `https://ui-avatars.com/api/?name=${authStore.user?.username}&background=random`" 
                  alt="用户头像"
                />
             </div>
          </div>
        </div>
      </header>

      <!-- View Content -->
      <div class="p-6 md:p-8 flex-1 overflow-x-hidden">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
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
  transition:
    opacity 0.25s var(--motion-ease-standard),
    transform 0.25s var(--motion-ease-standard);
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(15px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-15px);
}
</style>
