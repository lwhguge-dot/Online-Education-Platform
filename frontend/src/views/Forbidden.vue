<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import BaseButton from '../components/ui/BaseButton.vue'
import GlassCard from '../components/ui/GlassCard.vue'
import { Home, ArrowLeft, ShieldOff } from 'lucide-vue-next'
import { computed } from 'vue'

const router = useRouter()
const authStore = useAuthStore()

const roleHome = computed(() => {
  const role = authStore.user?.role
  if (role === 'admin') return '/admin'
  if (role === 'teacher') return '/teacher'
  return '/student'
})

const goHome = () => {
  router.push(roleHome.value)
}

const goBack = () => {
  router.back()
}
</script>

<template>
  <div class="min-h-screen flex items-center justify-center p-4 animate-fade-in">
    <div class="max-w-lg w-full text-center relative">
      <div class="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-96 h-96 bg-yanzhi/10 rounded-full blur-3xl animate-pulse-slow"></div>

      <GlassCard class="relative z-10 py-16 px-8 rounded-3xl border-white/60 shadow-xl shadow-yanzhi/5 overflow-hidden">
        <div class="mb-8 relative">
          <h1 class="text-9xl font-bold text-transparent bg-clip-text bg-gradient-to-r from-yanzhi to-chenpi opacity-20 select-none">
            403
          </h1>
          <div class="absolute inset-0 flex items-center justify-center">
            <ShieldOff class="w-16 h-16 text-yanzhi/70 animate-bounce" aria-hidden="true" />
          </div>
        </div>

        <h2 class="text-2xl font-bold text-shuimo mb-4 font-song">无权访问</h2>
        <p class="text-shuimo/60 mb-10 max-w-xs mx-auto leading-relaxed">
          您没有权限访问此页面，请联系管理员或返回对应功能中心。
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
            class="flex items-center justify-center gap-2 px-6 py-3 shadow-lg shadow-yanzhi/20"
          >
            <Home class="w-4 h-4" />
            回到我的主页
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
  animation: pulse var(--motion-duration-loop-slow) var(--motion-ease-standard) 3 both;
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

@media (prefers-reduced-motion: reduce) {
  .animate-pulse-slow {
    animation: none !important;
  }
}
</style>
