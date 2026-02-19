<script setup>
import { useRouter } from 'vue-router'
import BaseButton from '../components/ui/BaseButton.vue'
import GlassCard from '../components/ui/GlassCard.vue'
import { Home, ArrowLeft } from 'lucide-vue-next'

const router = useRouter()

const goHome = () => {
  router.push('/')
}

const goBack = () => {
  // 404页面返回首页（上一级）
  router.push('/')
}
</script>

<template>
  <div class="min-h-screen flex items-center justify-center p-4 animate-fade-in">
    <div class="max-w-lg w-full text-center relative">
      <!-- Background decoration -->
      <div class="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-96 h-96 bg-qinghua/10 rounded-full blur-3xl animate-pulse-slow"></div>
      
      <GlassCard class="relative z-10 py-16 px-8 rounded-3xl border-white/60 shadow-xl shadow-qinghua/5 overflow-hidden">
        <!-- 404 Illustration placeholder -->
        <div class="mb-8 relative">
          <h1 class="text-9xl font-bold text-transparent bg-clip-text bg-gradient-to-r from-qinghua to-halanzi opacity-20 select-none">
            404
          </h1>
          <div class="absolute inset-0 flex items-center justify-center">
             <div class="text-6xl animate-bounce">🙈</div>
          </div>
        </div>
        
        <h2 class="text-2xl font-bold text-shuimo mb-4 font-song">页面走丢了</h2>
        <p class="text-shuimo/60 mb-10 max-w-xs mx-auto leading-relaxed">
          您访问的页面不存在，或者已经被移动到了其他位置。
        </p>
        
        <div class="flex flex-col sm:flex-row gap-4 justify-center">
          <BaseButton 
            @click="goBack" 
            variant="secondary"
            class="flex items-center justify-center gap-2 px-6 py-3"
          >
            <ArrowLeft class="w-4 h-4" />
            返回上一页
          </BaseButton>
          
          <BaseButton 
            @click="goHome" 
            variant="primary"
            class="flex items-center justify-center gap-2 px-6 py-3 shadow-lg shadow-qinghua/20"
          >
            <Home class="w-4 h-4" />
            回到首页
          </BaseButton>
        </div>
      </GlassCard>
      
      <div class="mt-8 text-sm text-shuimo/30">
        © 2024 智慧课堂 Smart Education Platform
      </div>
    </div>
  </div>
</template>

<style scoped>
.animate-pulse-slow {
  /* P1：统一错误页脉冲动效曲线 */
  animation: pulse var(--motion-duration-slow) var(--motion-ease-standard) infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
    transform: translate(-50%, -50%) scale(1);
  }
  50% {
    opacity: .8;
    transform: translate(-50%, -50%) scale(1.1);
  }
}
</style>
