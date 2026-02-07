<script setup>
import { Trophy, Users } from 'lucide-vue-next'

const props = defineProps({
  rankings: {
    type: Array,
    default: () => []
  },
  maxItems: {
    type: Number,
    default: 5
  }
})

const emit = defineEmits(['click'])

const getRankStyle = (index) => {
  switch (index) {
    case 0: return 'bg-zhizi/20 text-zhizi'
    case 1: return 'bg-slate-200 text-slate-600'
    case 2: return 'bg-amber-100 text-amber-700'
    default: return 'bg-slate-100 text-slate-500'
  }
}
</script>

<template>
  <div class="course-ranking-list">
    <div v-if="rankings.length === 0" class="flex flex-col items-center justify-center py-8 text-center">
      <div class="w-16 h-16 rounded-full bg-slate-50 flex items-center justify-center mb-3">
        <Trophy class="w-8 h-8 text-slate-300" />
      </div>
      <p class="text-sm text-shuimo/60">暂无课程数据</p>
    </div>
    
    <div v-else class="space-y-4">
      <div v-for="(course, index) in rankings.slice(0, maxItems)" :key="course.courseId || index"
           @click="emit('click', course)"
           class="group cursor-pointer">
        <div class="flex items-center justify-between mb-2">
          <div class="flex items-center gap-2">
            <span :class="['w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold', getRankStyle(index)]">
              {{ index + 1 }}
            </span>
            <span class="text-sm text-shuimo font-medium group-hover:text-qinghua transition-colors">
              {{ course.title }}
            </span>
          </div>
          <span class="text-sm font-mono text-shuimo/60">
            {{ Math.round(course.completionRate || 0) }}%
          </span>
        </div>
        
        <div class="h-2 bg-slate-100 rounded-full overflow-hidden">
          <div class="h-full bg-gradient-to-r from-tianlv to-qingsong rounded-full transition-all duration-1000 ease-out"
               :style="{ width: (course.completionRate || 0) + '%' }">
          </div>
        </div>
        
        <div v-if="course.studentCount" class="flex items-center gap-1 mt-1 text-xs text-shuimo/40">
          <Users class="w-3 h-3" />
          {{ course.studentCount }} 名学生
        </div>
      </div>
    </div>
  </div>
</template>
