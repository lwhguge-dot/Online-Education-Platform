<script setup>
/**
 * 公告阅读统计弹窗组件
 * 展示公告的阅读率、已读用户列表和阅读时间分布
 */
import { ref, computed, onMounted, watch } from 'vue'
import { X, Users, Eye, Clock, CheckCircle, User, TrendingUp } from 'lucide-vue-next'
import { announcementAPI } from '../../services/api'
import { formatDateCN } from '../../utils/datetime'
import GlassCard from '../ui/GlassCard.vue'
import AnimatedNumber from '../ui/AnimatedNumber.vue'

const props = defineProps({
  /** 是否显示弹窗 */
  modelValue: {
    type: Boolean,
    default: false
  },
  /** 公告ID */
  announcementId: {
    type: [Number, String],
    default: null
  },
  /** 公告标题 */
  announcementTitle: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:modelValue'])

// 状态管理
const loading = ref(false)
const stats = ref({
  totalTargetUsers: 0,
  readCount: 0,
  readRate: 0,
  readUsers: []
})

/**
 * 加载公告阅读统计数据
 * 调用后端API获取阅读统计信息
 */
const loadStats = async () => {
  if (!props.announcementId) return

  loading.value = true
  try {
    const res = await announcementAPI.getStats(props.announcementId)
    if (res.code === 200 && res.data) {
      stats.value = {
        totalTargetUsers: res.data.totalTargetUsers || 0,
        readCount: res.data.readCount || 0,
        readRate: res.data.readRate || 0,
        readUsers: res.data.readUsers || []
      }
    }
  } catch (e) {
    console.error('加载公告统计失败:', e)
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
 * 格式化时间为相对时间描述
 * @param {string} dateStr - 日期字符串
 * @returns {string} 格式化后的相对时间
 */
const formatTimeAgo = (dateStr) => {
  if (!dateStr) return '未知'
  const date = new Date(dateStr)
  const now = new Date()
  const diff = Math.floor((now - date) / 1000)

  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)}分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)}小时前`
  if (diff < 604800) return `${Math.floor(diff / 86400)}天前`
  return formatDateCN(dateStr, '未知')
}

/**
 * 计算阅读率对应的颜色样式
 */
const readRateColor = computed(() => {
  const rate = stats.value.readRate
  if (rate >= 80) return 'text-qingsong'
  if (rate >= 50) return 'text-zhizi'
  return 'text-yanzhi'
})

/**
 * 计算阅读率对应的背景色
 */
const readRateBgColor = computed(() => {
  const rate = stats.value.readRate
  if (rate >= 80) return 'bg-qingsong/10'
  if (rate >= 50) return 'bg-zhizi/10'
  return 'bg-yanzhi/10'
})

// 监听弹窗显示状态，显示时加载数据
watch(() => props.modelValue, (newVal) => {
  if (newVal && props.announcementId) {
    loadStats()
  }
})

// 监听公告ID变化，重新加载数据
watch(() => props.announcementId, (newId) => {
  if (props.modelValue && newId) {
    loadStats()
  }
})

onMounted(() => {
  if (props.modelValue && props.announcementId) {
    loadStats()
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
        <div class="relative w-full max-w-lg bg-white rounded-2xl shadow-2xl overflow-hidden animate-scale-in">
          <!-- 头部 -->
          <div class="flex items-center justify-between p-5 border-b border-slate-100 bg-gradient-to-r from-zijinghui/5 to-qianniuzi/5">
            <div class="flex items-center gap-3">
              <div class="w-10 h-10 rounded-xl bg-gradient-to-br from-zijinghui to-qianniuzi flex items-center justify-center shadow-lg shadow-zijinghui/20">
                <TrendingUp class="w-5 h-5 text-white" />
              </div>
              <div>
                <h3 class="text-lg font-bold text-shuimo font-song">阅读统计</h3>
                <p class="text-xs text-shuimo/50 truncate max-w-[200px]">{{ announcementTitle }}</p>
              </div>
            </div>
            <button
              @click="closeModal"
              class="p-2 rounded-xl hover:bg-slate-100 text-shuimo/50 hover:text-shuimo transition-colors"
            >
              <X class="w-5 h-5" />
            </button>
          </div>

          <!-- 内容区域 -->
          <div class="p-5 space-y-5 max-h-[60vh] overflow-y-auto">
            <!-- 加载状态 -->
            <div v-if="loading" class="py-12 text-center">
              <div class="w-8 h-8 border-2 border-zijinghui/20 border-t-zijinghui rounded-full animate-spin mx-auto mb-3"></div>
              <p class="text-sm text-shuimo/50">加载中...</p>
            </div>

            <!-- 统计数据 -->
            <template v-else>
              <!-- 概览卡片 -->
              <div class="grid grid-cols-3 gap-4">
                <!-- 目标用户数 -->
                <div class="text-center p-4 rounded-xl bg-slate-50 hover:bg-slate-100 transition-colors">
                  <Users class="w-6 h-6 mx-auto text-qinghua mb-2" />
                  <div class="text-2xl font-bold text-shuimo font-mono">
                    <AnimatedNumber :value="stats.totalTargetUsers" />
                  </div>
                  <div class="text-xs text-shuimo/50 mt-1">目标用户</div>
                </div>

                <!-- 已读人数 -->
                <div class="text-center p-4 rounded-xl bg-qingsong/5 hover:bg-qingsong/10 transition-colors">
                  <Eye class="w-6 h-6 mx-auto text-qingsong mb-2" />
                  <div class="text-2xl font-bold text-qingsong font-mono">
                    <AnimatedNumber :value="stats.readCount" />
                  </div>
                  <div class="text-xs text-shuimo/50 mt-1">已读人数</div>
                </div>

                <!-- 阅读率 -->
                <div class="text-center p-4 rounded-xl transition-colors" :class="readRateBgColor">
                  <CheckCircle class="w-6 h-6 mx-auto mb-2" :class="readRateColor" />
                  <div class="text-2xl font-bold font-mono" :class="readRateColor">
                    <AnimatedNumber :value="stats.readRate" suffix="%" />
                  </div>
                  <div class="text-xs text-shuimo/50 mt-1">阅读率</div>
                </div>
              </div>

              <!-- 进度条 -->
              <div class="space-y-2">
                <div class="flex items-center justify-between text-sm">
                  <span class="text-shuimo/60">阅读进度</span>
                  <span class="font-mono" :class="readRateColor">{{ stats.readCount }}/{{ stats.totalTargetUsers }}</span>
                </div>
                <div class="h-3 bg-slate-100 rounded-full overflow-hidden">
                  <div
                    class="h-full rounded-full transition-all duration-1000 ease-out"
                    :class="stats.readRate >= 80 ? 'bg-gradient-to-r from-qingsong to-tianlv' : stats.readRate >= 50 ? 'bg-gradient-to-r from-zhizi to-qiuxiang' : 'bg-gradient-to-r from-yanzhi to-mudan'"
                    :style="{ width: `${stats.readRate}%` }"
                  ></div>
                </div>
              </div>

              <!-- 已读用户列表 -->
              <div class="space-y-3">
                <h4 class="text-sm font-bold text-shuimo flex items-center gap-2">
                  <User class="w-4 h-4 text-zijinghui" />
                  已读用户 ({{ stats.readUsers.length }})
                </h4>

                <div v-if="stats.readUsers.length === 0" class="text-center py-8">
                  <div class="w-12 h-12 mx-auto mb-3 rounded-full bg-slate-100 flex items-center justify-center">
                    <Eye class="w-6 h-6 text-slate-300" />
                  </div>
                  <p class="text-sm text-shuimo/50">暂无用户阅读</p>
                </div>

                <div v-else class="space-y-2 max-h-48 overflow-y-auto">
                  <div
                    v-for="user in stats.readUsers"
                    :key="user.userId"
                    class="flex items-center justify-between p-3 rounded-xl bg-slate-50 hover:bg-slate-100 transition-colors"
                  >
                    <div class="flex items-center gap-3">
                      <div class="w-8 h-8 rounded-full bg-gradient-to-br from-qinghua to-halanzi flex items-center justify-center text-white text-xs font-bold">
                        {{ (user.username || user.name || 'U').charAt(0).toUpperCase() }}
                      </div>
                      <div>
                        <p class="text-sm font-medium text-shuimo">{{ user.username || user.name || '未知用户' }}</p>
                        <p class="text-xs text-shuimo/40">{{ user.role === 'student' ? '学生' : user.role === 'teacher' ? '教师' : '管理员' }}</p>
                      </div>
                    </div>
                    <div class="flex items-center gap-1 text-xs text-shuimo/40">
                      <Clock class="w-3 h-3" />
                      <span>{{ formatTimeAgo(user.readAt) }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </template>
          </div>

          <!-- 底部 -->
          <div class="p-4 border-t border-slate-100 bg-slate-50/50">
            <button
              @click="closeModal"
              class="w-full py-2.5 rounded-xl bg-slate-100 text-shuimo text-sm font-medium hover:bg-slate-200 transition-colors"
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
