<script setup>
/**
 * 课程分布饼图组件
 * 使用纯CSS实现的环形图，展示各学科的课程数量和学生数量。
 *
 * @prop {Object} data - 分布数据，包含 subjects、courseCounts、studentCounts 数组
 * @prop {Boolean} loading - 加载状态
 */
import { computed, ref } from 'vue'

const props = defineProps({
  data: {
    type: Object,
    default: () => ({
      subjects: [],
      courseCounts: [],
      studentCounts: []
    })
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['click-subject'])

// 学科颜色映射
const subjectColors = {
  '语文': '#c45a65',    // 胭脂
  '数学': '#2e59a7',    // 青花蓝
  '英语': '#20a162',    // 田绿
  '物理': '#1781b5',    // 花蓝紫
  '化学': '#eacd76',    // 栀子
  '生物': '#5dbe8a',    // 青松
  '政治': '#815c94',    // 紫荆灰
  '历史': '#b78d71',    // 檀香棕
  '地理': '#88ada6'     // 黛青
}

// 获取学科颜色
const getColor = (subject) => {
  return subjectColors[subject] || '#94a3b8'
}

// 计算课程总数
const totalCourses = computed(() => {
  return (props.data.courseCounts || []).reduce((sum, count) => sum + count, 0)
})

// 计算学生总数
const totalStudents = computed(() => {
  return (props.data.studentCounts || []).reduce((sum, count) => sum + count, 0)
})

// 计算饼图扇区数据
const pieSegments = computed(() => {
  if (!props.data.subjects || props.data.subjects.length === 0) return []

  let cumulativePercentage = 0
  return props.data.subjects.map((subject, index) => {
    const count = props.data.courseCounts[index] || 0
    const percentage = totalCourses.value > 0 ? (count / totalCourses.value) * 100 : 0
    const startPercentage = cumulativePercentage
    cumulativePercentage += percentage

    return {
      subject,
      count,
      studentCount: props.data.studentCounts[index] || 0,
      percentage,
      startPercentage,
      endPercentage: cumulativePercentage,
      color: getColor(subject)
    }
  })
})

// 生成 SVG 圆弧路径
const generateArcPath = (startPercent, endPercent, radius = 40) => {
  const startAngle = (startPercent / 100) * 360 - 90
  const endAngle = (endPercent / 100) * 360 - 90

  const startRad = (startAngle * Math.PI) / 180
  const endRad = (endAngle * Math.PI) / 180

  const x1 = 50 + radius * Math.cos(startRad)
  const y1 = 50 + radius * Math.sin(startRad)
  const x2 = 50 + radius * Math.cos(endRad)
  const y2 = 50 + radius * Math.sin(endRad)

  const largeArc = endPercent - startPercent > 50 ? 1 : 0

  return `M 50 50 L ${x1} ${y1} A ${radius} ${radius} 0 ${largeArc} 1 ${x2} ${y2} Z`
}

// 悬停的扇区索引
const hoveredIndex = ref(-1)

// 点击扇区
const clickSegment = (segment) => {
  emit('click-subject', segment.subject)
}
</script>

<template>
  <div class="course-distribution-chart">
    <!-- 头部 -->
    <div class="flex items-center justify-between mb-4">
      <h3 class="text-sm font-medium text-shuimo">课程学科分布</h3>
      <span class="text-xs text-shuimo/50">共 {{ totalCourses }} 门课程</span>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="h-48 flex items-center justify-center">
      <div class="w-8 h-8 border-2 border-qinghua border-t-transparent rounded-full animate-spin"></div>
    </div>

    <!-- 空状态 -->
    <div v-else-if="pieSegments.length === 0" class="h-48 flex items-center justify-center text-shuimo/50 text-sm">
      暂无课程数据
    </div>

    <!-- 图表内容 -->
    <div v-else class="flex items-start gap-6">
      <!-- 饼图 -->
      <div class="relative w-36 h-36 flex-shrink-0">
        <svg class="w-full h-full" viewBox="0 0 100 100">
          <!-- 扇区 -->
          <g v-for="(segment, index) in pieSegments" :key="index">
            <path
              :d="generateArcPath(segment.startPercentage, segment.endPercentage)"
              :fill="segment.color"
              :opacity="hoveredIndex === -1 || hoveredIndex === index ? 1 : 0.5"
              class="transition-all duration-200 cursor-pointer"
              :class="{ 'transform scale-105 origin-center': hoveredIndex === index }"
              @mouseenter="hoveredIndex = index"
              @mouseleave="hoveredIndex = -1"
              @click="clickSegment(segment)"
            />
          </g>

          <!-- 中心空白圆（环形效果） -->
          <circle cx="50" cy="50" r="25" fill="white" />

          <!-- 中心文字 -->
          <text x="50" y="47" text-anchor="middle" class="text-sm font-medium fill-shuimo">
            {{ hoveredIndex >= 0 ? pieSegments[hoveredIndex].count : totalCourses }}
          </text>
          <text x="50" y="57" text-anchor="middle" class="text-xs fill-shuimo/50">
            {{ hoveredIndex >= 0 ? pieSegments[hoveredIndex].subject : '课程' }}
          </text>
        </svg>

        <!-- 悬停提示 -->
        <div
          v-if="hoveredIndex >= 0"
          class="absolute -right-2 top-0 bg-white/95 backdrop-blur border border-gray-200 rounded-lg shadow-lg p-2 text-xs z-10 whitespace-nowrap"
        >
          <div class="font-medium text-shuimo mb-1">{{ pieSegments[hoveredIndex].subject }}</div>
          <div class="text-shuimo/60">课程: {{ pieSegments[hoveredIndex].count }} 门</div>
          <div class="text-shuimo/60">学生: {{ pieSegments[hoveredIndex].studentCount }} 人</div>
          <div class="text-shuimo/60">占比: {{ pieSegments[hoveredIndex].percentage.toFixed(1) }}%</div>
        </div>
      </div>

      <!-- 图例列表 -->
      <div class="flex-1 space-y-2 max-h-36 overflow-y-auto">
        <div
          v-for="(segment, index) in pieSegments"
          :key="index"
          class="flex items-center justify-between p-2 rounded-lg transition-all cursor-pointer hover:bg-gray-50"
          :class="{ 'bg-gray-50': hoveredIndex === index }"
          @mouseenter="hoveredIndex = index"
          @mouseleave="hoveredIndex = -1"
          @click="clickSegment(segment)"
        >
          <div class="flex items-center gap-2">
            <div class="w-3 h-3 rounded-sm" :style="{ backgroundColor: segment.color }"></div>
            <span class="text-xs text-shuimo">{{ segment.subject }}</span>
          </div>
          <div class="flex items-center gap-3">
            <span class="text-xs text-shuimo/60">{{ segment.count }}门</span>
            <span class="text-xs text-shuimo/40 w-10 text-right">{{ segment.percentage.toFixed(0) }}%</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部汇总 -->
    <div class="mt-4 pt-3 border-t border-gray-100 flex items-center justify-around text-center">
      <div>
        <div class="text-lg font-medium text-qinghua">{{ totalCourses }}</div>
        <div class="text-xs text-shuimo/50">总课程数</div>
      </div>
      <div class="w-px h-8 bg-gray-200"></div>
      <div>
        <div class="text-lg font-medium text-tianlv">{{ totalStudents }}</div>
        <div class="text-xs text-shuimo/50">总学生数</div>
      </div>
      <div class="w-px h-8 bg-gray-200"></div>
      <div>
        <div class="text-lg font-medium text-zhizi">{{ pieSegments.length }}</div>
        <div class="text-xs text-shuimo/50">学科数量</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.course-distribution-chart {
  min-height: 200px;
}

/* SVG 文字样式 */
svg text {
  font-family: inherit;
}

/* 扇区悬停动画 */
path {
  transform-origin: center;
  transition: transform 0.2s ease, opacity 0.2s ease;
}

path:hover {
  transform: scale(1.03);
}
</style>
