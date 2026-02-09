<script setup>
import { AlertCircle } from 'lucide-vue-next'

defineProps({
  title: {
    type: String,
    default: '暂无数据'
  },
  description: {
    type: String,
    default: ''
  },
  icon: {
    type: [Object, Function],
    default: null // 兼容不同构建产物下图标组件被解析为对象或函数
  }
})
</script>

<template>
  <div class="flex flex-col items-center justify-center py-20 text-center animate-slide-up" style="animation-fill-mode: both;">
    <div class="w-14 h-14 mx-auto mb-3 rounded-full bg-slate-50 flex items-center justify-center empty-state-float">
      <component 
        :is="icon || AlertCircle" 
        class="w-7 h-7 text-shuimo/30" 
      />
    </div>
    <h3 class="text-shuimo/60 font-medium">{{ title }}</h3>
    <p v-if="description" class="text-xs text-shuimo/40 mt-1">{{ description }}</p>
    <slot name="action" class="mt-4"></slot>
  </div>
</template>

<style scoped>
.empty-state-float {
  animation: hover-float 4s ease-in-out infinite;
}

@keyframes hover-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-5px); }
}
</style>
