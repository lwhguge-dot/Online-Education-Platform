<script setup>
/**
 * 测验分数趋势图表组件
 * 展示学生在特定课程中的测验分数趋势折线图
 */
import { ref, computed, onMounted, watch } from 'vue'
import { TrendingUp, TrendingDown, Minus, Calendar, Award } from 'lucide-vue-next'
import { progressAPI } from '../../services/api'
import AnimatedNumber from '../ui/AnimatedNumber.vue'

const props = defineProps({
  /** 课程ID */
  courseId: {
    type: [Number, String],
    required: true
  },
  /** 学生ID（可选，教师查看特定学生时使用） */
  studentId: {
    type: [Number, String],
    default: null
  },
  /** 图表高度 */
  height: {
    type: Number,
    default: 200
  },
  /** 是否显示标题 */
  showHeader: {
    type: Boolean,
    default: true
  }
})

// 状态管理
const loading = ref(false)
const trendData = ref({
  scores: [],
  average: 0,
  highest: 0,
  lowest: 0,
  trend: 'stable',
  trendPercentage: 0
})

/**
 * 加载测验分数趋势数据
 */
const loadTrendData = async () => {
  if (!props.courseId) return

  loading.value = true
  try {
    const res = await progressAPI.getQuizScoreTrend(props.courseId, props.studentId)
    if (res.code === 200 && res.data) {
      trendData.value = {
        scores: res.data.scores || [],
        average: res.data.average || 0,
        highest: res.data.highest || 0,
        lowest: res.data.lowest || 0,
        trend: res.data.trend || 'stable',
        trendPercentage: res.data.trendPercentage || 0
      }
    }
  } catch (e) {
    console.error('加载测验趋势失败:', e)
    trendData.value = {
      scores: [],
      average: 0,
      highest: 0,
      lowest: 0,
      trend: 'stable',
      trendPercentage: 0
    }
  } finally {
    loading.value = false
  }
}

/**
 * 计算图表路径（SVG折线图）
 */
const chartPath = computed(() => {
  const scores = trendData.value.scores
  if (scores.length < 2) return ''

  const width = 100 // SVG viewBox宽度
  const height = 100 // SVG viewBox高度
  const padding = 10
  const drawWidth = width - padding * 2
  const drawHeight = height - padding * 2

  const maxScore = Math.max(...scores.map(s => s.score), 100)
  const minScore = Math.min(...scores.map(s => s.score), 0)
  const range = maxScore - minScore || 1

  const points = scores.map((item, index) => {
    const x = padding + (index / (scores.length - 1)) * drawWidth
    const y = padding + drawHeight - ((item.score - minScore) / range) * drawHeight
    return `${x},${y}`
  })

  return `M ${points.join(' L ')}`
})

/**
 * 计算图表区域填充路径
 */
const chartAreaPath = computed(() => {
  const scores = trendData.value.scores
  if (scores.length < 2) return ''

  const width = 100
  const height = 100
  const padding = 10
  const drawWidth = width - padding * 2
  const drawHeight = height - padding * 2

  const maxScore = Math.max(...scores.map(s => s.score), 100)
  const minScore = Math.min(...scores.map(s => s.score), 0)
  const range = maxScore - minScore || 1

  const points = scores.map((item, index) => {
    const x = padding + (index / (scores.length - 1)) * drawWidth
    const y = padding + drawHeight - ((item.score - minScore) / range) * drawHeight
    return `${x},${y}`
  })

  return `M ${padding},${height - padding} L ${points.join(' L ')} L ${width - padding},${height - padding} Z`
})

/**
 * 趋势指示器颜色
 */
const trendColor = computed(() => {
  switch (trendData.value.trend) {
    case 'up': return 'text-qingsong'
    case 'down': return 'text-yanzhi'
    default: return 'text-slate-400'
  }
})

/**
 * 趋势指示器图标
 */
const trendIcon = computed(() => {
  switch (trendData.value.trend) {
    case 'up': return TrendingUp
    case 'down': return TrendingDown
    default: return Minus
  }
})

/**
 * 获取分数对应的颜色
 */
const getScoreColor = (score) => {
  if (score >= 90) return 'text-qingsong'
  if (score >= 70) return 'text-tianlv'
  if (score >= 60) return 'text-zhizi'
  return 'text-yanzhi'
}

// 监听courseId变化
watch(() => props.courseId, (newId) => {
  if (newId) {
    loadTrendData()
  }
}, { immediate: true })

onMounted(() => {
  if (props.courseId) {
    loadTrendData()
  }
})
</script>

<template>
  <div class="space-y-4">
    <!-- 标题 -->
    <div v-if="showHeader" class="flex items-center justify-between">
      <h4 class="font-bold text-shuimo flex items-center gap-2">
        <TrendingUp class="w-4 h-4 text-tianlv" />
        测验分数趋势
      </h4>
      <div class="flex items-center gap-1 text-sm" :class="trendColor">
        <component :is="trendIcon" class="w-4 h-4" />
        <span v-if="trendData.trendPercentage !== 0">
          {{ trendData.trendPercentage > 0 ? '+' : '' }}{{ trendData.trendPercentage }}%
        </span>
      </div>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="flex items-center justify-center" :style="{ height: `${height}px` }">
      <div class="w-6 h-6 border-2 border-tianlv/20 border-t-tianlv rounded-full animate-spin"></div>
    </div>

    <!-- 图表区域 -->
    <template v-else>
      <!-- 空数据状态 -->
      <div v-if="trendData.scores.length === 0" class="text-center py-8">
        <Award class="w-10 h-10 mx-auto text-slate-200 mb-2" />
        <p class="text-sm text-shuimo/50">暂无测验数据</p>
      </div>

      <!-- 有数据时显示图表 -->
      <template v-else>
        <!-- SVG图表 -->
        <div class="relative" :style="{ height: `${height}px` }">
          <svg viewBox="0 0 100 100" class="w-full h-full" preserveAspectRatio="none">
            <!-- 背景网格线 -->
            <line x1="10" y1="25" x2="90" y2="25" stroke="#e2e8f0" stroke-width="0.5" stroke-dasharray="2,2" />
            <line x1="10" y1="50" x2="90" y2="50" stroke="#e2e8f0" stroke-width="0.5" stroke-dasharray="2,2" />
            <line x1="10" y1="75" x2="90" y2="75" stroke="#e2e8f0" stroke-width="0.5" stroke-dasharray="2,2" />

            <!-- 渐变填充区域 -->
            <defs>
              <linearGradient id="areaGradient" x1="0%" y1="0%" x2="0%" y2="100%">
                <stop offset="0%" style="stop-color: #10b981; stop-opacity: 0.3" />
                <stop offset="100%" style="stop-color: #10b981; stop-opacity: 0.05" />
              </linearGradient>
            </defs>
            <path :d="chartAreaPath" fill="url(#areaGradient)" />

            <!-- 折线 -->
            <path
              :d="chartPath"
              fill="none"
              stroke="#10b981"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
              class="chart-line"
            />

            <!-- 数据点 -->
            <circle
              v-for="(item, index) in trendData.scores"
              :key="index"
              :cx="10 + (index / (trendData.scores.length - 1)) * 80"
              :cy="10 + 80 - ((item.score - Math.min(...trendData.scores.map(s => s.score), 0)) / (Math.max(...trendData.scores.map(s => s.score), 100) - Math.min(...trendData.scores.map(s => s.score), 0) || 1)) * 80"
              r="3"
              fill="#10b981"
              class="chart-dot"
            />
          </svg>
        </div>

        <!-- 统计数据 -->
        <div class="grid grid-cols-3 gap-3 text-center">
          <div class="p-3 rounded-xl bg-slate-50">
            <div class="text-lg font-bold font-mono" :class="getScoreColor(trendData.average)">
              <AnimatedNumber :value="Math.round(trendData.average)" />
            </div>
            <div class="text-xs text-shuimo/50">平均分</div>
          </div>
          <div class="p-3 rounded-xl bg-qingsong/5">
            <div class="text-lg font-bold font-mono text-qingsong">
              <AnimatedNumber :value="trendData.highest" />
            </div>
            <div class="text-xs text-shuimo/50">最高分</div>
          </div>
          <div class="p-3 rounded-xl bg-yanzhi/5">
            <div class="text-lg font-bold font-mono text-yanzhi">
              <AnimatedNumber :value="trendData.lowest" />
            </div>
            <div class="text-xs text-shuimo/50">最低分</div>
          </div>
        </div>

        <!-- 测验记录列表 -->
        <div v-if="trendData.scores.length > 0" class="space-y-2 max-h-40 overflow-y-auto">
          <div
            v-for="(item, index) in trendData.scores.slice().reverse()"
            :key="index"
            class="flex items-center justify-between p-2 rounded-lg bg-slate-50 text-sm"
          >
            <div class="flex items-center gap-2">
              <Calendar class="w-3.5 h-3.5 text-shuimo/40" />
              <span class="text-shuimo/70">{{ item.chapterTitle || item.title || `测验${trendData.scores.length - index}` }}</span>
            </div>
            <span class="font-mono font-bold" :class="getScoreColor(item.score)">{{ item.score }}分</span>
          </div>
        </div>
      </template>
    </template>
  </div>
</template>

<style scoped>
.chart-line {
  stroke-dasharray: 500;
  stroke-dashoffset: 500;
  /* P1 第二批：图表入场动画压缩 */
  animation: drawLine var(--motion-duration-medium) var(--motion-ease-standard) forwards;
}

.chart-dot {
  opacity: 0;
  animation: fadeIn var(--motion-duration-medium) var(--motion-ease-standard) forwards;
}

.chart-dot:nth-child(1) { animation-delay: 0.3s; }
.chart-dot:nth-child(2) { animation-delay: 0.4s; }
.chart-dot:nth-child(3) { animation-delay: 0.5s; }
.chart-dot:nth-child(4) { animation-delay: 0.6s; }
.chart-dot:nth-child(5) { animation-delay: 0.7s; }

@keyframes drawLine {
  to {
    stroke-dashoffset: 0;
  }
}

@keyframes fadeIn {
  to {
    opacity: 1;
  }
}
</style>
