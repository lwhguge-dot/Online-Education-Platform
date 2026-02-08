<script setup>
const props = defineProps({
  hoverable: {
    type: Boolean,
    default: true
  },
  padding: {
    type: String,
    default: 'p-6'
  },
  overflow: {
    type: String,
    default: 'hidden'
  }
})
</script>

<template>
  <div 
    class="glass-card relative rounded-2xl transition-all duration-300 group border border-white/20 shadow-lg"
    :class="[
      padding,
      props.overflow === 'hidden' ? 'overflow-hidden' : 'overflow-visible',
      props.hoverable ? 'hover:shadow-2xl hover:shadow-primary/10 hover:-translate-y-1.5 hover:border-white/40' : ''
    ]"
    :style="{
      backdropFilter: 'blur(16px) contrast(1.1) saturate(1.6)',
      WebkitBackdropFilter: 'blur(16px) contrast(1.1) saturate(1.6)',
      backgroundColor: 'rgba(255, 255, 255, 0.4)'
    }"
  >
    <!-- 边缘锐利高光边缘 - Glassmorphism 2.0 核心 -->
    <div class="absolute inset-0 border-t border-l border-white/40 rounded-2xl pointer-events-none z-10"></div>
    
    <!-- 内容层 -->
    <div class="relative z-20">
      <slot />
    </div>
    
    <!-- 背景噪声与光泽叠加 -->
    <div class="absolute inset-0 opacity-[0.03] pointer-events-none bg-[url('https://www.transparenttextures.com/patterns/stardust.png')] z-0"></div>
    <div class="absolute inset-0 bg-gradient-to-br from-white/30 to-transparent pointer-events-none z-0"></div>
  </div>
</template>
