<script setup>
const props = defineProps({
  lines: {
    type: Number,
    default: 3
  },
  showAvatar: {
    type: Boolean,
    default: false
  },
  showImage: {
    type: Boolean,
    default: false
  }
})
</script>

<template>
  <div class="skeleton-card bg-white/60 backdrop-blur-sm rounded-2xl p-6 border border-white/50 animate-pulse">
    <!-- 图片占位 -->
    <div v-if="showImage" class="w-full h-32 bg-slate-200 rounded-xl mb-4"></div>
    
    <!-- 头部带头像 -->
    <div v-if="showAvatar" class="flex items-center gap-3 mb-4">
      <div class="w-10 h-10 bg-slate-200 rounded-full"></div>
      <div class="flex-1">
        <div class="h-4 bg-slate-200 rounded w-24 mb-2"></div>
        <div class="h-3 bg-slate-200 rounded w-16"></div>
      </div>
    </div>
    
    <!-- 标题 -->
    <div class="h-5 bg-slate-200 rounded w-3/4 mb-4"></div>
    
    <!-- 内容行 -->
    <div class="space-y-3">
      <div v-for="i in lines" :key="i" 
           :class="['h-3 bg-slate-200 rounded', i === lines ? 'w-1/2' : 'w-full']">
      </div>
    </div>
  </div>
</template>

<style scoped>
@keyframes shimmer {
  0% {
    background-position: -200% 0;
  }
  100% {
    background-position: 200% 0;
  }
}

.skeleton-card {
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  /* P1 第二批：骨架屏流光时长压缩 */
  animation: shimmer var(--motion-duration-medium) infinite;
}
</style>
