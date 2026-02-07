<template>
  <div class="skeleton-loader relative overflow-hidden bg-white/50 backdrop-blur-sm border border-white/20 shadow-sm">
    <!-- Shimmer Overlay -->
    <div class="absolute inset-0 bg-gradient-to-r from-transparent via-white/60 to-transparent skew-x-12 translate-x-[-150%] animate-shimmer z-20 pointer-events-none"></div>

    <!-- 课程卡片骨架 -->
    <div v-if="type === 'course-card'" class="course-card-skeleton">
      <div class="h-40 bg-gray-200/80 rounded-t-xl"></div>
      <div class="p-4 space-y-3">
        <div class="h-5 bg-gray-200/80 rounded w-3/4"></div>
        <div class="h-4 bg-gray-200/80 rounded w-1/2"></div>
        <div class="flex gap-2">
          <div class="h-6 w-16 bg-gray-200/80 rounded-full"></div>
          <div class="h-6 w-20 bg-gray-200/80 rounded-full"></div>
        </div>
      </div>
    </div>
    
    <!-- 列表项骨架 -->
    <div v-else-if="type === 'list-item'" class="list-item-skeleton flex items-center gap-4 p-4">
      <div class="w-12 h-12 bg-gray-200/80 rounded-full flex-shrink-0"></div>
      <div class="flex-1 space-y-2">
        <div class="h-4 bg-gray-200/80 rounded w-3/4"></div>
        <div class="h-3 bg-gray-200/80 rounded w-1/2"></div>
      </div>
    </div>
    
    <!-- 统计卡片骨架 -->
    <div v-else-if="type === 'stat-card'" class="stat-card-skeleton p-6 space-y-3">
      <div class="flex justify-between items-center">
        <div class="h-4 bg-gray-200/80 rounded w-20"></div>
        <div class="w-10 h-10 bg-gray-200/80 rounded-xl"></div>
      </div>
      <div class="h-8 bg-gray-200/80 rounded w-16"></div>
      <div class="h-3 bg-gray-200/80 rounded w-24"></div>
    </div>
    
    <!-- 表格行骨架 -->
    <div v-else-if="type === 'table-row'" class="table-row-skeleton flex items-center gap-4 p-4 border-b border-slate-100">
      <div class="w-8 h-8 bg-gray-200/80 rounded"></div>
      <div class="flex-1 h-4 bg-gray-200/80 rounded"></div>
      <div class="w-20 h-4 bg-gray-200/80 rounded"></div>
      <div class="w-16 h-6 bg-gray-200/80 rounded-full"></div>
    </div>
    
    <!-- 详情页 Header 骨架 (New) -->
    <div v-else-if="type === 'detail-header'" class="detail-header-skeleton p-1 md:flex gap-6">
      <div class="md:w-1/3 h-64 bg-gray-200/80 rounded-xl"></div>
      <div class="md:w-2/3 py-4 space-y-4">
        <div class="flex gap-2">
           <div class="h-6 w-16 bg-gray-200/80 rounded-lg"></div>
           <div class="h-6 w-16 bg-gray-200/80 rounded-lg"></div>
        </div>
        <div class="h-10 bg-gray-200/80 rounded w-3/4"></div>
        <div class="h-20 bg-gray-200/80 rounded w-full"></div>
        <div class="h-12 bg-gray-200/80 rounded w-40"></div>
      </div>
    </div>

    <!-- 默认骨架 -->
    <div v-else class="default-skeleton space-y-3 p-4">
      <div class="h-4 bg-gray-200/80 rounded w-full"></div>
      <div class="h-4 bg-gray-200/80 rounded w-5/6"></div>
      <div class="h-4 bg-gray-200/80 rounded w-4/6"></div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  type: {
    type: String,
    default: 'default',
    validator: (value) => ['course-card', 'list-item', 'stat-card', 'table-row', 'detail-header', 'default'].includes(value)
  }
})
</script>

<style scoped>
.skeleton-loader {
  @apply bg-white rounded-xl;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}

.animate-pulse {
  animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}
</style>
