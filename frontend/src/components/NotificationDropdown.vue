<template>
  <div class="relative">
    <!-- 通知按钮 -->
    <button 
      @click="toggleDropdown" 
      class="relative p-2 rounded-xl hover:bg-white/50 transition-colors"
    >
      <Bell class="w-5 h-5 text-shuimo/60" />
      <span 
        v-if="unreadCount > 0" 
        class="absolute -top-1 -right-1 w-5 h-5 bg-yanzhi text-white text-xs rounded-full flex items-center justify-center"
      >
        {{ unreadCount > 99 ? '99+' : unreadCount }}
      </span>
    </button>

    <!-- 下拉菜单 -->
    <Transition name="dropdown">
      <div 
        v-if="showDropdown" 
        class="absolute right-0 top-12 w-80 bg-white rounded-2xl shadow-xl border border-slate-100 overflow-hidden z-50"
      >
        <div class="p-4 border-b border-slate-100 flex items-center justify-between">
          <h3 class="font-semibold text-shuimo">通知中心</h3>
          <button 
            v-if="notifications.length > 0"
            @click="markAllRead" 
            class="text-xs text-qinghua hover:underline"
          >
            全部已读
          </button>
        </div>

        <div class="max-h-80 overflow-y-auto">
          <div 
            v-for="notification in notifications" 
            :key="notification.id"
            :class="['p-4 border-b border-slate-50 hover:bg-slate-50 cursor-pointer transition-colors', 
                     notification.read ? 'opacity-60' : '']"
            @click="handleNotification(notification)"
          >
            <div class="flex items-start gap-3">
              <div :class="['w-2 h-2 rounded-full mt-2', notification.read ? 'bg-slate-300' : 'bg-yanzhi']"></div>
              <div class="flex-1">
                <p class="text-sm text-shuimo">{{ notification.title }}</p>
                <p class="text-xs text-shuimo/60 mt-1">{{ notification.time }}</p>
              </div>
            </div>
          </div>

          <div v-if="notifications.length === 0" class="p-8 text-center">
            <Bell class="w-12 h-12 text-slate-300 mx-auto mb-3" />
            <p class="text-sm text-shuimo/60">暂无通知</p>
          </div>
        </div>

        <div class="p-3 border-t border-slate-100 text-center">
          <button class="text-sm text-qinghua hover:underline">查看全部</button>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { Bell } from 'lucide-vue-next'
import { homeworkAPI } from '../services/api'
import { useAuthStore } from '../stores/auth'

const authStore = useAuthStore()
const showDropdown = ref(false)
const notifications = ref([])
let pollInterval = null

const unreadCount = computed(() => notifications.value.filter(n => !n.read).length)

const toggleDropdown = () => {
  showDropdown.value = !showDropdown.value
}

const markAllRead = () => {
  notifications.value.forEach(n => n.read = true)
}

const handleNotification = (notification) => {
  notification.read = true
}

const fetchNotifications = async () => {
  try {
    const userId = authStore.user?.id
    if (!userId) return
    
    // 从homework-service获取真实活动数据作为通知
    const res = await homeworkAPI.getTeacherActivities(userId)
    if (res.code === 200 && res.data) {
      notifications.value = res.data.map((act, index) => ({
        id: act.id || index,
        title: act.content,
        time: act.time,
        read: false,
        type: act.type
      }))
    }
  } catch (e) {
    console.error('获取通知失败:', e)
  }
}

onMounted(() => {
  fetchNotifications()
  // 每30秒轮询一次
  pollInterval = setInterval(fetchNotifications, 30000)
})

onUnmounted(() => {
  if (pollInterval) clearInterval(pollInterval)
})
</script>

<style scoped>
.dropdown-enter-active,
.dropdown-leave-active {
  transition: all 0.2s ease;
}

.dropdown-enter-from,
.dropdown-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
