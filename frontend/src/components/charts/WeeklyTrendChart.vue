<script setup>
import { computed } from 'vue'

const props = defineProps({
  data: {
    type: Object,
    default: () => ({
      labels: [],
      studentActivity: [],
      homeworkSubmissions: [],
      quizCompletions: []
    })
  }
})

// 计算最大值用于缩放
const maxValue = computed(() => {
  const allValues = [
    ...(props.data.studentActivity || []),
    ...(props.data.homeworkSubmissions || []),
    ...(props.data.quizCompletions || [])
  ]
  return Math.max(...allValues, 1)
})

// 计算柱状图高度百分比
const getBarHeight = (value) => {
  return Math.max((value / maxValue.value) * 100, 2)
}
</script>

<template>
  <div class="weekly-trend-chart">
    <!-- 图例 -->
    <div class="flex items-center justify-center gap-6 mb-4">
      <div class="flex items-center gap-2">
        <div class="w-3 h-3 rounded-full bg-tianlv"></div>
        <span class="text-xs text-shuimo/60">学生活动</span>
      </div>
      <div class="flex items-center gap-2">
        <div class="w-3 h-3 rounded-full bg-qinghua"></div>
        <span class="text-xs text-shuimo/60">作业提交</span>
      </div>
      <div class="flex items-center gap-2">
        <div class="w-3 h-3 rounded-full bg-zhizi"></div>
        <span class="text-xs text-shuimo/60">测验完成</span>
      </div>
    </div>
    
    <!-- 图表区域 -->
    <div class="flex items-end justify-between gap-2 h-32 px-2">
      <div v-for="(label, index) in data.labels" :key="index" 
           class="flex-1 flex flex-col items-center gap-1">
        <!-- 柱状图组 -->
        <div class="flex items-end gap-0.5 h-24 w-full justify-center">
          <div class="w-2 bg-tianlv/80 rounded-t transition-all duration-500"
               :style="{ height: getBarHeight(data.studentActivity?.[index] || 0) + '%' }"
               :title="`学生活动: ${data.studentActivity?.[index] || 0}`">
          </div>
          <div class="w-2 bg-qinghua/80 rounded-t transition-all duration-500"
               :style="{ height: getBarHeight(data.homeworkSubmissions?.[index] || 0) + '%' }"
               :title="`作业提交: ${data.homeworkSubmissions?.[index] || 0}`">
          </div>
          <div class="w-2 bg-zhizi/80 rounded-t transition-all duration-500"
               :style="{ height: getBarHeight(data.quizCompletions?.[index] || 0) + '%' }"
               :title="`测验完成: ${data.quizCompletions?.[index] || 0}`">
          </div>
        </div>
        <!-- 标签 -->
        <span class="text-xs text-shuimo/50">{{ label }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.weekly-trend-chart {
  min-height: 180px;
}
</style>
