<script setup>
/**
 * 每日学习目标进度组件
 *
 * 功能：以环形进度条展示今日学习分钟数与目标的对比，
 * 达标时展示庆祝状态，未达标时显示剩余分钟数。
 *
 * Props:
 * - todayMinutes: 今日已学习分钟数
 * - goalMinutes: 每日目标分钟数（默认60）
 */
import { computed } from 'vue'
import { Target, CheckCircle, Flame } from 'lucide-vue-next'

const props = defineProps({
  todayMinutes: {
    type: Number,
    default: 0
  },
  goalMinutes: {
    type: Number,
    default: 60
  }
})

/** 计算完成百分比，上限100% */
const percentage = computed(() => {
  if (props.goalMinutes <= 0) return 0
  return Math.min(Math.round((props.todayMinutes / props.goalMinutes) * 100), 100)
})

/** 是否已达成今日目标 */
const isAchieved = computed(() => props.todayMinutes >= props.goalMinutes)

/** 剩余分钟数 */
const remainingMinutes = computed(() => {
  return Math.max(props.goalMinutes - props.todayMinutes, 0)
})

/** 环形进度条的 SVG stroke-dashoffset 计算 */
const circumference = 2 * Math.PI * 36
const strokeDashoffset = computed(() => {
  return circumference - (percentage.value / 100) * circumference
})

/** 进度条颜色样式 */
const progressColor = computed(() => {
  if (isAchieved.value) return 'stroke-emerald-500'
  if (percentage.value >= 60) return 'stroke-blue-500'
  if (percentage.value >= 30) return 'stroke-amber-500'
  return 'stroke-slate-400'
})
</script>

<template>
  <div class="flex flex-col items-center">
    <!-- 环形进度条 -->
    <div class="relative w-20 h-20 mb-3">
      <svg class="w-20 h-20 -rotate-90" viewBox="0 0 80 80">
        <!-- 背景圆环 -->
        <circle
          cx="40" cy="40" r="36"
          fill="none"
          stroke-width="5"
          class="stroke-slate-200 dark:stroke-slate-700"
        />
        <!-- 进度圆环 -->
        <circle
          cx="40" cy="40" r="36"
          fill="none"
          stroke-width="5"
          stroke-linecap="round"
          :class="progressColor"
          :stroke-dasharray="circumference"
          :stroke-dashoffset="strokeDashoffset"
          class="transition-all duration-700 ease-out"
        />
      </svg>
      <!-- 中心图标/数字 -->
      <div class="absolute inset-0 flex items-center justify-center">
        <CheckCircle
          v-if="isAchieved"
          class="w-7 h-7 text-emerald-500"
        />
        <span v-else class="text-lg font-bold text-shuimo">
          {{ percentage }}%
        </span>
      </div>
    </div>

    <!-- 标题和状态文字 -->
    <div class="flex items-center gap-1.5 mb-1">
      <Target class="w-4 h-4 text-qinghua" />
      <span class="text-sm font-medium text-shuimo/60">今日目标</span>
    </div>
    <div v-if="isAchieved" class="flex items-center gap-1 text-emerald-600">
      <Flame class="w-4 h-4" />
      <span class="text-xs font-bold">已达成！</span>
    </div>
    <div v-else class="text-center">
      <p class="text-xs text-shuimo/50">
        还差 <span class="font-bold text-qinghua">{{ remainingMinutes }}</span> 分钟
      </p>
    </div>
  </div>
</template>
