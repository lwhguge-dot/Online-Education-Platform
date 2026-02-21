<script setup>
defineProps({
  rows: {
    type: Number,
    default: 5
  },
  columns: {
    type: Number,
    default: 4
  }
})
</script>

<template>
  <div class="skeleton-table bg-white/60 backdrop-blur-sm rounded-2xl border border-white/50 overflow-hidden">
    <!-- 表头 -->
    <div class="flex items-center gap-4 p-4 border-b border-slate-100 bg-slate-50/50">
      <div v-for="i in columns" :key="'h-' + i" 
           :class="['h-4 bg-slate-200 rounded animate-pulse', i === 1 ? 'w-32' : 'w-20']">
      </div>
    </div>
    
    <!-- 表格行 -->
    <div v-for="row in rows" :key="'r-' + row" 
         class="flex items-center gap-4 p-4 border-b border-slate-50 last:border-0">
      <div v-for="col in columns" :key="'c-' + col" 
           :class="['h-4 bg-slate-200 rounded animate-pulse', col === 1 ? 'w-32' : 'w-20']"
           :style="{ animationDelay: (row * 0.1 + col * 0.05) + 's' }">
      </div>
    </div>
  </div>
</template>

<style scoped>
@keyframes shimmer {
  0% {
    opacity: 0.5;
  }
  50% {
    opacity: 1;
  }
  100% {
    opacity: 0.5;
  }
}

.skeleton-table .animate-pulse {
  /* P1 第二批：表格骨架动画压缩 */
  animation: shimmer var(--motion-duration-medium) var(--motion-ease-standard) infinite;
}
</style>
