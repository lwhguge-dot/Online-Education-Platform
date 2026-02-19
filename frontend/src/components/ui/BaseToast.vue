<script setup>
import { useToastStore } from '../../stores/toast'
import { CheckCircle, AlertCircle, AlertTriangle, Info, X } from 'lucide-vue-next'
import { storeToRefs } from 'pinia'

const store = useToastStore()
const { toasts } = storeToRefs(store)

const icons = {
  success: CheckCircle,
  error: AlertCircle,
  warning: AlertTriangle,
  info: Info
}

const styles = {
  success: 'bg-gradient-to-r from-tianlv/90 to-qingsong/90 border-tianlv/50 text-white shadow-tianlv/20',
  error: 'bg-gradient-to-r from-yanzhi/90 to-yanzhihong/90 border-yanzhi/50 text-white shadow-yanzhi/20',
  warning: 'bg-gradient-to-r from-zhizi/90 to-orange-400/90 border-zhizi/50 text-white shadow-zhizi/20',
  info: 'bg-gradient-to-r from-qinghua/90 to-halanzi/90 border-qinghua/50 text-white shadow-qinghua/20'
}
</script>

<template>
  <div class="fixed top-24 right-4 z-[9999] flex flex-col gap-3 pointer-events-none" role="region" aria-label="系统消息通知">
    <TransitionGroup name="toast">
      <div
        v-for="toast in toasts"
        :key="toast.id"
        class="pointer-events-auto flex items-center gap-3 px-5 py-3.5 rounded-2xl shadow-lg backdrop-blur-md border min-w-[300px] max-w-sm animate-toast-in cursor-pointer hover:scale-102 transition-transform"
        :class="styles[toast.type]"
        @click="store.remove(toast.id)"
      >
        <component :is="icons[toast.type]" class="w-5 h-5 flex-shrink-0" />
        <span class="flex-1 text-sm font-medium tracking-wide">{{ toast.message }}</span>
        <button class="p-1 hover:bg-white/20 rounded-lg transition-colors" aria-label="关闭提示消息">
          <X class="w-4 h-4 opacity-80" />
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toast-enter-active,
.toast-leave-active {
  /* P1：Toast 动画限定到透明度和位移缩放 */
  transition:
    opacity var(--motion-duration-slow) var(--motion-ease-standard),
    transform var(--motion-duration-slow) var(--motion-ease-standard);
}

.toast-enter-from {
  opacity: 0;
  transform: translateX(50px) scale(0.9);
}

.toast-leave-to {
  opacity: 0;
  transform: translateX(50px) scale(0.9);
}

.hover\:scale-102:hover {
  transform: scale(1.02);
}
</style>
