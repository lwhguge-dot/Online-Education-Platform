<script setup>
defineProps({
  size: {
    type: String,
    default: 'md', // sm, md, lg
    validator: (v) => ['sm', 'md', 'lg'].includes(v)
  },
  text: {
    type: String,
    default: ''
  },
  overlay: {
    type: Boolean,
    default: false
  }
})

const sizeClasses = {
  sm: 'w-4 h-4 border-2',
  md: 'w-8 h-8 border-3',
  lg: 'w-12 h-12 border-4'
}
</script>

<template>
  <div :class="['flex flex-col items-center justify-center gap-3', overlay && 'fixed inset-0 bg-white/80 backdrop-blur-sm z-50']">
    <div :class="['rounded-full border-tianlv/20 border-t-tianlv animate-spin', sizeClasses[size]]"></div>
    <span v-if="text" class="text-sm text-shuimo/60 animate-pulse">{{ text }}</span>
  </div>
</template>

<style scoped>
@keyframes spin {
  to { transform: rotate(360deg); }
}
.animate-spin {
  animation: spin 0.8s linear infinite;
}
</style>
