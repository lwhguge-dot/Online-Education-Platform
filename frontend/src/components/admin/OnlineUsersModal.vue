<script setup>
/**
 * 在线用户实时监控弹窗组件
 * 展示当前在线用户列表，支持WebSocket实时更新
 */
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { X, Users, Wifi, WifiOff, Clock, Monitor, RefreshCw, Search } from 'lucide-vue-next'
import { userAPI } from '../../services/api'
import { useWebSocket } from '../../composables/useWebSocket'
import AnimatedNumber from '../ui/AnimatedNumber.vue'
import { formatTimeCN } from '../../utils/datetime'

const props = defineProps({
  /** 是否显示弹窗 */
  modelValue: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue'])

// 状态管理
const loading = ref(false)
const searchQuery = ref('')
const onlineUsers = ref([])
const lastUpdateTime = ref('')

// WebSocket连接状态（如果websocket.js支持）
let wsUnsubscribe = null

/**
 * 加载在线用户列表
 * 调用后端API获取当前在线用户
 */
const loadOnlineUsers = async () => {
  loading.value = true
  try {
    const res = await userAPI.getOnlineStatus()
    if (res.code === 200 && res.data) {
      onlineUsers.value = res.data.users || res.data || []
      lastUpdateTime.value = formatTimeCN(new Date())
    }
  } catch (e) {
    console.error('加载在线用户失败:', e)
    // 如果API不存在，使用模拟数据展示组件功能
    onlineUsers.value = []
  } finally {
    loading.value = false
  }
}

/**
 * 关闭弹窗
 */
const closeModal = () => {
  emit('update:modelValue', false)
}

/**
 * 手动刷新数据
 */
const handleRefresh = () => {
  loadOnlineUsers()
}

/**
 * 格式化在线时长
 * @param {string} loginTime - 登录时间
 * @returns {string} 格式化后的在线时长
 */
const formatOnlineDuration = (loginTime) => {
  if (!loginTime) return '未知'
  const login = new Date(loginTime)
  const now = new Date()
  const diff = Math.floor((now - login) / 1000)

  if (diff < 60) return `${diff}秒`
  if (diff < 3600) return `${Math.floor(diff / 60)}分钟`
  if (diff < 86400) {
    const hours = Math.floor(diff / 3600)
    const minutes = Math.floor((diff % 3600) / 60)
    return `${hours}小时${minutes}分钟`
  }
  return `${Math.floor(diff / 86400)}天`
}

/**
 * 获取角色显示名称
 * @param {string} role - 角色标识
 * @returns {string} 角色中文名
 */
const getRoleName = (role) => {
  const map = { admin: '管理员', teacher: '教师', student: '学生' }
  return map[role] || role
}

/**
 * 获取角色对应的样式类
 * @param {string} role - 角色标识
 * @returns {string} 样式类名
 */
const getRoleBadgeClass = (role) => {
  switch (role) {
    case 'admin': return 'bg-zijinghui/10 text-zijinghui'
    case 'teacher': return 'bg-qingsong/10 text-qingsong'
    case 'student': return 'bg-qinghua/10 text-qinghua'
    default: return 'bg-slate-100 text-slate-600'
  }
}

/**
 * 过滤后的用户列表
 */
const filteredUsers = computed(() => {
  if (!searchQuery.value.trim()) return onlineUsers.value
  const query = searchQuery.value.toLowerCase()
  return onlineUsers.value.filter(user =>
    (user.username && user.username.toLowerCase().includes(query)) ||
    (user.name && user.name.toLowerCase().includes(query)) ||
    (user.email && user.email.toLowerCase().includes(query))
  )
})

/**
 * 按角色分组的用户统计
 */
const userStats = computed(() => {
  const stats = { admin: 0, teacher: 0, student: 0 }
  onlineUsers.value.forEach(user => {
    if (stats[user.role] !== undefined) {
      stats[user.role]++
    }
  })
  return stats
})

// 监听弹窗显示状态
watch(() => props.modelValue, (newVal) => {
  if (newVal) {
    loadOnlineUsers()
  }
})

// 定时刷新（每30秒）
let refreshTimer = null

onMounted(() => {
  if (props.modelValue) {
    loadOnlineUsers()
  }
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }
  if (wsUnsubscribe) {
    wsUnsubscribe()
  }
})

// 弹窗打开时启动定时刷新
watch(() => props.modelValue, (newVal) => {
  if (newVal) {
    refreshTimer = setInterval(loadOnlineUsers, 30000)
  } else {
    if (refreshTimer) {
      clearInterval(refreshTimer)
      refreshTimer = null
    }
  }
})
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div
        v-if="modelValue"
        class="fixed inset-0 z-[100] flex items-center justify-center p-4"
        @click.self="closeModal"
      >
        <!-- 背景遮罩 -->
        <div
          class="absolute inset-0 bg-black/30 backdrop-blur-sm"
          @click="closeModal"
        ></div>

        <!-- 弹窗内容 -->
        <div class="relative w-full max-w-2xl bg-white rounded-2xl shadow-2xl overflow-hidden animate-scale-in">
          <!-- 头部 -->
          <div class="flex items-center justify-between p-5 border-b border-slate-100 bg-gradient-to-r from-qingsong/5 to-tianlv/5">
            <div class="flex items-center gap-3">
              <div class="w-10 h-10 rounded-xl bg-gradient-to-br from-qingsong to-tianlv flex items-center justify-center shadow-lg shadow-qingsong/20">
                <Users class="w-5 h-5 text-white" />
              </div>
              <div>
                <h3 class="text-lg font-bold text-shuimo font-song">在线用户监控</h3>
                <div class="flex items-center gap-2 text-xs text-shuimo/50">
                  <span class="flex items-center gap-1">
                    <span class="w-2 h-2 rounded-full bg-qingsong animate-pulse"></span>
                    实时更新
                  </span>
                  <span>·</span>
                  <span>{{ lastUpdateTime }}</span>
                </div>
              </div>
            </div>
            <div class="flex items-center gap-2">
              <button
                @click="handleRefresh"
                :disabled="loading"
                class="p-2 rounded-xl hover:bg-slate-100 text-shuimo/50 hover:text-shuimo transition-colors disabled:opacity-50"
                title="刷新"
              >
                <RefreshCw class="w-5 h-5" :class="{ 'animate-spin': loading }" />
              </button>
              <button
                @click="closeModal"
                class="p-2 rounded-xl hover:bg-slate-100 text-shuimo/50 hover:text-shuimo transition-colors"
              >
                <X class="w-5 h-5" />
              </button>
            </div>
          </div>

          <!-- 内容区域 -->
          <div class="p-5 space-y-5 max-h-[65vh] overflow-y-auto">
            <!-- 统计概览 -->
            <div class="grid grid-cols-4 gap-3">
              <!-- 总在线 -->
              <div class="text-center p-3 rounded-xl bg-slate-50 hover:bg-slate-100 transition-colors">
                <Wifi class="w-5 h-5 mx-auto text-qingsong mb-1" />
                <div class="text-xl font-bold text-shuimo font-mono">
                  <AnimatedNumber :value="onlineUsers.length" />
                </div>
                <div class="text-xs text-shuimo/50">总在线</div>
              </div>

              <!-- 管理员 -->
              <div class="text-center p-3 rounded-xl bg-zijinghui/5 hover:bg-zijinghui/10 transition-colors">
                <Monitor class="w-5 h-5 mx-auto text-zijinghui mb-1" />
                <div class="text-xl font-bold text-zijinghui font-mono">
                  <AnimatedNumber :value="userStats.admin" />
                </div>
                <div class="text-xs text-shuimo/50">管理员</div>
              </div>

              <!-- 教师 -->
              <div class="text-center p-3 rounded-xl bg-qingsong/5 hover:bg-qingsong/10 transition-colors">
                <Users class="w-5 h-5 mx-auto text-qingsong mb-1" />
                <div class="text-xl font-bold text-qingsong font-mono">
                  <AnimatedNumber :value="userStats.teacher" />
                </div>
                <div class="text-xs text-shuimo/50">教师</div>
              </div>

              <!-- 学生 -->
              <div class="text-center p-3 rounded-xl bg-qinghua/5 hover:bg-qinghua/10 transition-colors">
                <Users class="w-5 h-5 mx-auto text-qinghua mb-1" />
                <div class="text-xl font-bold text-qinghua font-mono">
                  <AnimatedNumber :value="userStats.student" />
                </div>
                <div class="text-xs text-shuimo/50">学生</div>
              </div>
            </div>

            <!-- 搜索框 -->
            <div class="relative">
              <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-shuimo/40" />
              <input
                v-model="searchQuery"
                type="text"
                placeholder="搜索用户名、姓名或邮箱..."
                class="w-full pl-10 pr-4 py-2.5 rounded-xl bg-slate-50 border-none focus:ring-2 focus:ring-qingsong/20 transition-all text-sm"
              />
              <button
                v-if="searchQuery"
                @click="searchQuery = ''"
                class="absolute right-3 top-1/2 -translate-y-1/2 text-shuimo/40 hover:text-shuimo"
              >
                <X class="w-4 h-4" />
              </button>
            </div>

            <!-- 加载状态 -->
            <div v-if="loading && onlineUsers.length === 0" class="py-12 text-center">
              <div class="w-8 h-8 border-2 border-qingsong/20 border-t-qingsong rounded-full animate-spin mx-auto mb-3"></div>
              <p class="text-sm text-shuimo/50">加载中...</p>
            </div>

            <!-- 用户列表 -->
            <div v-else-if="filteredUsers.length > 0" class="space-y-2">
              <div
                v-for="user in filteredUsers"
                :key="user.id || user.userId"
                class="flex items-center justify-between p-4 rounded-xl bg-slate-50 hover:bg-slate-100 transition-all group"
              >
                <div class="flex items-center gap-3">
                  <!-- 头像 -->
                  <div class="relative">
                    <div
                      class="w-10 h-10 rounded-full flex items-center justify-center text-white text-sm font-bold"
                      :class="user.role === 'admin' ? 'bg-gradient-to-br from-zijinghui to-qianniuzi' : user.role === 'teacher' ? 'bg-gradient-to-br from-qingsong to-tianlv' : 'bg-gradient-to-br from-qinghua to-halanzi'"
                    >
                      {{ (user.username || user.name || 'U').charAt(0).toUpperCase() }}
                    </div>
                    <!-- 在线指示器 -->
                    <span class="absolute -bottom-0.5 -right-0.5 w-3 h-3 bg-qingsong rounded-full border-2 border-white"></span>
                  </div>

                  <!-- 用户信息 -->
                  <div>
                    <div class="flex items-center gap-2">
                      <span class="font-medium text-shuimo">{{ user.name || user.username }}</span>
                      <span
                        class="px-2 py-0.5 rounded text-[10px] font-bold"
                        :class="getRoleBadgeClass(user.role)"
                      >
                        {{ getRoleName(user.role) }}
                      </span>
                    </div>
                    <p class="text-xs text-shuimo/50">{{ user.email || user.username + '@edu.cn' }}</p>
                  </div>
                </div>

                <!-- 在线时长 -->
                <div class="text-right">
                  <div class="flex items-center gap-1 text-xs text-shuimo/60">
                    <Clock class="w-3 h-3" />
                    <span>在线 {{ formatOnlineDuration(user.lastLoginAt || user.loginTime) }}</span>
                  </div>
                  <p class="text-xs text-shuimo/40 mt-0.5">{{ user.ipAddress || '未知IP' }}</p>
                </div>
              </div>
            </div>

            <!-- 空状态 -->
            <div v-else class="text-center py-12">
              <div class="w-16 h-16 mx-auto mb-4 rounded-full bg-slate-100 flex items-center justify-center">
                <WifiOff class="w-8 h-8 text-slate-300" />
              </div>
              <p class="text-shuimo/60 font-medium mb-1">
                {{ searchQuery ? '未找到匹配的用户' : '当前没有在线用户' }}
              </p>
              <p class="text-xs text-shuimo/40">
                {{ searchQuery ? '请尝试其他关键词' : '等待用户上线...' }}
              </p>
            </div>
          </div>

          <!-- 底部 -->
          <div class="p-4 border-t border-slate-100 bg-slate-50/50 flex items-center justify-between">
            <span class="text-xs text-shuimo/50">
              每30秒自动刷新
            </span>
            <button
              @click="closeModal"
              class="px-4 py-2 rounded-xl bg-slate-100 text-shuimo text-sm font-medium hover:bg-slate-200 transition-colors"
            >
              关闭
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
/* 弹窗入场动画 */
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.3s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.animate-scale-in {
  animation: scaleIn 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

@keyframes scaleIn {
  from {
    opacity: 0;
    transform: scale(0.95);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}
</style>
