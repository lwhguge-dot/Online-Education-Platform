<script setup>
import { computed } from 'vue'
import { AlertTriangle, Clock, ChevronRight, X } from 'lucide-vue-next'

const props = defineProps({
  homeworks: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['navigate', 'dismiss'])

// 筛选紧急作业（截止日期≤2天）
const urgentHomeworks = computed(() => {
  return props.homeworks.filter(hw => {
    if (!hw.deadline && hw.daysLeft === undefined) return false
    const daysLeft = hw.daysLeft !== undefined ? hw.daysLeft : 
      Math.ceil((new Date(hw.deadline) - new Date()) / 86400000)
    return daysLeft >= 0 && daysLeft <= 2
  })
})

// 最紧急的作业
const mostUrgent = computed(() => {
  if (urgentHomeworks.value.length === 0) return null
  return urgentHomeworks.value.reduce((min, hw) => {
    const minDays = min.daysLeft !== undefined ? min.daysLeft : 999
    const hwDays = hw.daysLeft !== undefined ? hw.daysLeft : 999
    return hwDays < minDays ? hw : min
  }, urgentHomeworks.value[0])
})

// 紧急程度样式
const urgencyStyle = computed(() => {
  if (!mostUrgent.value) return {}
  const daysLeft = mostUrgent.value.daysLeft
  if (daysLeft <= 0) {
    return {
      bg: 'bg-red-50 border-red-200',
      text: 'text-red-700',
      icon: 'text-red-500',
      button: 'bg-red-600 hover:bg-red-700'
    }
  }
  if (daysLeft <= 1) {
    return {
      bg: 'bg-orange-50 border-orange-200',
      text: 'text-orange-700',
      icon: 'text-orange-500',
      button: 'bg-orange-600 hover:bg-orange-700'
    }
  }
  return {
    bg: 'bg-amber-50 border-amber-200',
    text: 'text-amber-700',
    icon: 'text-amber-500',
    button: 'bg-amber-600 hover:bg-amber-700'
  }
})

// 格式化剩余时间
const formatTimeLeft = (daysLeft) => {
  if (daysLeft <= 0) return '已截止'
  if (daysLeft < 1) return '今天截止'
  return `${Math.ceil(daysLeft)}天后截止`
}
</script>

<template>
  <Transition name="slide-down">
    <div 
      v-if="urgentHomeworks.length > 0"
      class="rounded-xl border p-4 mb-6 animate-pulse-subtle"
      :class="urgencyStyle.bg"
    >
      <div class="flex items-center justify-between gap-4">
        <!-- 左侧：图标和信息 -->
        <div class="flex items-center gap-3 flex-1 min-w-0">
          <div class="shrink-0">
            <AlertTriangle class="w-6 h-6" :class="urgencyStyle.icon" />
          </div>
          
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2 flex-wrap">
              <span class="font-semibold" :class="urgencyStyle.text">
                您有 {{ urgentHomeworks.length }} 项作业即将截止！
              </span>
              <span 
                v-if="mostUrgent"
                class="text-xs px-2 py-0.5 rounded-full bg-white/50"
                :class="urgencyStyle.text"
              >
                <Clock class="w-3 h-3 inline mr-1" />
                {{ formatTimeLeft(mostUrgent.daysLeft) }}
              </span>
            </div>
            
            <p 
              v-if="mostUrgent" 
              class="text-sm mt-1 truncate opacity-80"
              :class="urgencyStyle.text"
            >
              {{ mostUrgent.title }}
              <span v-if="mostUrgent.courseName"> - {{ mostUrgent.courseName }}</span>
            </p>
          </div>
        </div>
        
        <!-- 右侧：操作按钮 -->
        <div class="flex items-center gap-2 shrink-0">
          <button 
            @click="emit('navigate', 'homework')"
            class="px-4 py-2 rounded-lg text-white text-sm font-medium flex items-center gap-1 transition-colors"
            :class="urgencyStyle.button"
          >
            立即查看
            <ChevronRight class="w-4 h-4" />
          </button>
        </div>
      </div>
      
      <!-- 多个作业时显示列表预览 -->
      <div 
        v-if="urgentHomeworks.length > 1" 
        class="mt-3 pt-3 border-t border-current/10 flex flex-wrap gap-2"
      >
        <span 
          v-for="hw in urgentHomeworks.slice(0, 3)" 
          :key="hw.id"
          class="text-xs px-2 py-1 rounded-full bg-white/50"
          :class="urgencyStyle.text"
        >
          {{ hw.title }}
        </span>
        <span 
          v-if="urgentHomeworks.length > 3"
          class="text-xs px-2 py-1 rounded-full bg-white/30"
          :class="urgencyStyle.text"
        >
          +{{ urgentHomeworks.length - 3 }} 更多
        </span>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.slide-down-enter-active,
.slide-down-leave-active {
  transition: all 0.3s ease;
}

.slide-down-enter-from {
  opacity: 0;
  transform: translateY(-20px);
}

.slide-down-leave-to {
  opacity: 0;
  transform: translateY(-20px);
}

.animate-pulse-subtle {
  animation: pulse-subtle 2s ease-in-out infinite;
}

@keyframes pulse-subtle {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(239, 68, 68, 0);
  }
  50% {
    box-shadow: 0 0 0 4px rgba(239, 68, 68, 0.1);
  }
}
</style>
