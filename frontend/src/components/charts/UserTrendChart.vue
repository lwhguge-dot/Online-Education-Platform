<script setup>
/**
 * 用户增长趋势图组件
 * 使用纯CSS实现的折线图，展示新增用户、活跃用户和在线用户的趋势。
 *
 * @prop {Object} data - 趋势数据，包含 labels、newUsers、activeUsers、onlineUsers 数组
 * @prop {Boolean} loading - 加载状态
 */
import { computed, ref } from 'vue'

const props = defineProps({
  data: {
    type: Object,
    default: () => ({
      labels: [],
      newUsers: [],
      activeUsers: [],
      onlineUsers: []
    })
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['change-range'])

// 时间范围选项
const timeRanges = [
  { label: '7天', value: 7 },
  { label: '14天', value: 14 },
  { label: '30天', value: 30 }
]

const selectedRange = ref(7)

// 切换时间范围
const changeRange = (days) => {
  selectedRange.value = days
  emit('change-range', days)
}

// 计算所有数据的最大值用于缩放
const maxValue = computed(() => {
  const allValues = [
    ...(props.data.newUsers || []),
    ...(props.data.activeUsers || []),
    ...(props.data.onlineUsers || [])
  ]
  return Math.max(...allValues, 1)
})

// 计算点的Y轴位置（百分比，从底部算起）
const getPointY = (value) => {
  return Math.max((value / maxValue.value) * 100, 2)
}

// 生成 SVG 折线路径
const generatePath = (data) => {
  if (!data || data.length === 0) return ''

  const points = data.map((value, index) => {
    const x = (index / Math.max(data.length - 1, 1)) * 100
    const y = 100 - getPointY(value)
    return `${x},${y}`
  })

  return `M ${points.join(' L ')}`
}

// 鼠标悬停显示的数据点索引
const hoveredIndex = ref(-1)

// 获取悬停时的详细数据
const getTooltipData = (index) => {
  if (index < 0 || !props.data.labels) return null
  return {
    label: props.data.labels[index],
    newUsers: props.data.newUsers?.[index] || 0,
    activeUsers: props.data.activeUsers?.[index] || 0,
    onlineUsers: props.data.onlineUsers?.[index] || 0
  }
}

const tooltipData = computed(() => getTooltipData(hoveredIndex.value))
</script>

<template>
  <div class="user-trend-chart">
    <!-- 头部：标题和时间范围选择 -->
    <div class="flex items-center justify-between mb-4">
      <h3 class="text-sm font-medium text-shuimo">用户增长趋势</h3>
      <div class="flex gap-1">
        <button
          v-for="range in timeRanges"
          :key="range.value"
          @click="changeRange(range.value)"
          class="px-2 py-1 text-xs rounded transition-all"
          :class="selectedRange === range.value
            ? 'bg-qinghua text-white'
            : 'bg-gray-100 text-shuimo/60 hover:bg-gray-200'"
        >
          {{ range.label }}
        </button>
      </div>
    </div>

    <!-- 图例 -->
    <div class="flex items-center justify-center gap-6 mb-3">
      <div class="flex items-center gap-2">
        <div class="w-3 h-0.5 bg-tianlv rounded"></div>
        <span class="text-xs text-shuimo/60">新增用户</span>
      </div>
      <div class="flex items-center gap-2">
        <div class="w-3 h-0.5 bg-qinghua rounded"></div>
        <span class="text-xs text-shuimo/60">活跃用户</span>
      </div>
      <div class="flex items-center gap-2">
        <div class="w-3 h-0.5 bg-zhizi rounded"></div>
        <span class="text-xs text-shuimo/60">在线用户</span>
      </div>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="h-40 flex items-center justify-center">
      <div class="w-8 h-8 border-2 border-qinghua border-t-transparent rounded-full animate-spin"></div>
    </div>

    <!-- 图表区域 -->
    <div v-else class="relative h-40">
      <!-- Y轴刻度 -->
      <div class="absolute left-0 top-0 bottom-0 w-8 flex flex-col justify-between text-xs text-shuimo/40">
        <span>{{ maxValue }}</span>
        <span>{{ Math.round(maxValue / 2) }}</span>
        <span>0</span>
      </div>

      <!-- SVG 折线图 -->
      <div class="ml-10 h-full relative">
        <svg class="w-full h-full" viewBox="0 0 100 100" preserveAspectRatio="none">
          <!-- 网格线 -->
          <line x1="0" y1="50" x2="100" y2="50" stroke="#e5e7eb" stroke-width="0.5" stroke-dasharray="2,2" />
          <line x1="0" y1="0" x2="100" y2="0" stroke="#e5e7eb" stroke-width="0.5" />
          <line x1="0" y1="100" x2="100" y2="100" stroke="#e5e7eb" stroke-width="0.5" />

          <!-- 新增用户折线 -->
          <path
            :d="generatePath(data.newUsers)"
            fill="none"
            stroke="#20a162"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            class="transition-all duration-500"
          />

          <!-- 活跃用户折线 -->
          <path
            :d="generatePath(data.activeUsers)"
            fill="none"
            stroke="#2e59a7"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            class="transition-all duration-500"
          />

          <!-- 在线用户折线 -->
          <path
            :d="generatePath(data.onlineUsers)"
            fill="none"
            stroke="#eacd76"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            class="transition-all duration-500"
          />

          <!-- 交互区域 -->
          <g v-for="(label, index) in data.labels" :key="index">
            <rect
              :x="(index / Math.max(data.labels.length - 1, 1)) * 100 - 3"
              y="0"
              width="6"
              height="100"
              fill="transparent"
              @mouseenter="hoveredIndex = index"
              @mouseleave="hoveredIndex = -1"
              class="cursor-pointer"
            />
            <!-- 悬停时的垂直指示线 -->
            <line
              v-if="hoveredIndex === index"
              :x1="(index / Math.max(data.labels.length - 1, 1)) * 100"
              y1="0"
              :x2="(index / Math.max(data.labels.length - 1, 1)) * 100"
              y2="100"
              stroke="#94a3b8"
              stroke-width="1"
              stroke-dasharray="2,2"
            />
          </g>
        </svg>

        <!-- Tooltip -->
        <div
          v-if="hoveredIndex >= 0 && tooltipData"
          class="absolute z-10 bg-white/95 backdrop-blur border border-gray-200 rounded-lg shadow-lg p-2 text-xs pointer-events-none"
          :style="{
            left: `${(hoveredIndex / Math.max(data.labels.length - 1, 1)) * 100}%`,
            top: '10px',
            transform: 'translateX(-50%)'
          }"
        >
          <div class="font-medium text-shuimo mb-1">{{ tooltipData.label }}</div>
          <div class="space-y-0.5">
            <div class="flex items-center gap-2">
              <span class="w-2 h-2 bg-tianlv rounded-full"></span>
              <span class="text-shuimo/60">新增: {{ tooltipData.newUsers }}</span>
            </div>
            <div class="flex items-center gap-2">
              <span class="w-2 h-2 bg-qinghua rounded-full"></span>
              <span class="text-shuimo/60">活跃: {{ tooltipData.activeUsers }}</span>
            </div>
            <div class="flex items-center gap-2">
              <span class="w-2 h-2 bg-zhizi rounded-full"></span>
              <span class="text-shuimo/60">在线: {{ tooltipData.onlineUsers }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- X轴标签 -->
      <div class="ml-10 flex justify-between text-xs text-shuimo/40 mt-1">
        <span v-for="(label, index) in data.labels" :key="index"
              class="flex-1 text-center"
              :class="{ 'invisible': index % Math.ceil(data.labels.length / 7) !== 0 && index !== data.labels.length - 1 }">
          {{ label }}
        </span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.user-trend-chart {
  min-height: 200px;
}

/* 折线动画 */
path {
  stroke-dasharray: 1000;
  stroke-dashoffset: 1000;
  /* P1 第二批：趋势图绘制动画压缩 */
  animation: draw var(--motion-duration-medium) var(--motion-ease-standard) forwards;
}

@keyframes draw {
  to {
    stroke-dashoffset: 0;
  }
}
</style>
