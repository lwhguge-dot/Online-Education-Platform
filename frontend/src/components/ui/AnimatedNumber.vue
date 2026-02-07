<script setup>
/**
 * 动态数字组件
 * 支持数字递增动画、变化时高亮效果（上升绿色/下降红色）及脉冲动画。
 *
 * @prop {Number} value - 目标数值
 * @prop {Number} duration - 动画持续时间（毫秒）
 * @prop {String} suffix - 后缀文本
 * @prop {String} prefix - 前缀文本
 * @prop {Boolean} highlight - 是否启用变化高亮效果
 * @prop {Boolean} showTrend - 是否显示趋势指示器（上升/下降箭头）
 */
import { ref, watch, onMounted, computed } from 'vue'

const props = defineProps({
  value: {
    type: Number,
    default: 0
  },
  duration: {
    type: Number,
    default: 800
  },
  suffix: {
    type: String,
    default: ''
  },
  prefix: {
    type: String,
    default: ''
  },
  highlight: {
    type: Boolean,
    default: true
  },
  showTrend: {
    type: Boolean,
    default: false
  }
})

const displayValue = ref(0)
const previousValue = ref(0)
const isHighlighted = ref(false)
const trend = ref(0) // 1: 上升, -1: 下降, 0: 不变
let animationFrame = null
let highlightTimeout = null

// 高亮颜色类
const highlightClass = computed(() => {
  if (!isHighlighted.value) return ''
  return trend.value > 0 ? 'highlight-up' : trend.value < 0 ? 'highlight-down' : ''
})

// 趋势图标
const trendIcon = computed(() => {
  if (trend.value > 0) return '↑'
  if (trend.value < 0) return '↓'
  return ''
})

const animateValue = (start, end, duration) => {
  const startTime = performance.now()

  const update = (currentTime) => {
    const elapsed = currentTime - startTime
    const progress = Math.min(elapsed / duration, 1)

    // 使用 easeOutQuart 缓动函数
    const easeProgress = 1 - Math.pow(1 - progress, 4)
    displayValue.value = Math.round(start + (end - start) * easeProgress)

    if (progress < 1) {
      animationFrame = requestAnimationFrame(update)
    }
  }

  if (animationFrame) {
    cancelAnimationFrame(animationFrame)
  }
  animationFrame = requestAnimationFrame(update)
}

// 触发高亮效果
const triggerHighlight = (newVal, oldVal) => {
  if (!props.highlight || newVal === oldVal) return

  // 确定趋势
  trend.value = newVal > oldVal ? 1 : newVal < oldVal ? -1 : 0
  isHighlighted.value = true

  // 清除之前的定时器
  if (highlightTimeout) {
    clearTimeout(highlightTimeout)
  }

  // 高亮持续1.5秒后恢复
  highlightTimeout = setTimeout(() => {
    isHighlighted.value = false
  }, 1500)
}

watch(() => props.value, (newVal, oldVal) => {
  previousValue.value = oldVal || 0
  triggerHighlight(newVal, oldVal)
  animateValue(oldVal || 0, newVal, props.duration)
}, { immediate: false })

onMounted(() => {
  animateValue(0, props.value, props.duration)
})
</script>

<template>
  <span
    class="animated-number tabular-nums inline-flex items-center gap-1"
    :class="[highlightClass, { 'is-animating': isHighlighted }]"
  >
    <span class="number-value">{{ prefix }}{{ displayValue }}{{ suffix }}</span>
    <!-- 趋势指示器 -->
    <span
      v-if="showTrend && trend !== 0"
      class="trend-indicator text-xs"
      :class="trend > 0 ? 'text-tianlv' : 'text-yanzhi'"
    >
      {{ trendIcon }}
    </span>
  </span>
</template>

<style scoped>
.animated-number {
  transition: all 0.3s ease;
}

/* 上升高亮 - 绿色 */
.highlight-up {
  color: #20a162; /* tianlv */
}

.highlight-up .number-value {
  animation: pulse-highlight-up 0.6s ease-out;
}

/* 下降高亮 - 红色 */
.highlight-down {
  color: #c45a65; /* yanzhi */
}

.highlight-down .number-value {
  animation: pulse-highlight-down 0.6s ease-out;
}

/* 脉冲高亮动画 - 上升 */
@keyframes pulse-highlight-up {
  0% {
    transform: scale(1);
    text-shadow: 0 0 0 transparent;
  }
  30% {
    transform: scale(1.15);
    text-shadow: 0 0 10px rgba(32, 161, 98, 0.5);
  }
  100% {
    transform: scale(1);
    text-shadow: 0 0 0 transparent;
  }
}

/* 脉冲高亮动画 - 下降 */
@keyframes pulse-highlight-down {
  0% {
    transform: scale(1);
    text-shadow: 0 0 0 transparent;
  }
  30% {
    transform: scale(1.15);
    text-shadow: 0 0 10px rgba(196, 90, 101, 0.5);
  }
  100% {
    transform: scale(1);
    text-shadow: 0 0 0 transparent;
  }
}

/* 趋势指示器动画 */
.trend-indicator {
  animation: trend-bounce 0.5s ease-out;
}

@keyframes trend-bounce {
  0% {
    opacity: 0;
    transform: translateY(5px);
  }
  50% {
    transform: translateY(-3px);
  }
  100% {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 数值变化时的整体脉冲效果 */
.is-animating .number-value {
  display: inline-block;
}
</style>
