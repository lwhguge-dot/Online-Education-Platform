<template>
  <Transition name="slide">
    <div v-if="!isOnline" class="offline-notice">
      <div class="offline-content">
        <WifiOff class="w-5 h-5" />
        <span>网络连接已断开，部分功能可能不可用</span>
      </div>
    </div>
  </Transition>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { WifiOff } from 'lucide-vue-next'

const isOnline = ref(navigator.onLine)

const updateOnlineStatus = () => {
  isOnline.value = navigator.onLine
}

onMounted(() => {
  window.addEventListener('online', updateOnlineStatus)
  window.addEventListener('offline', updateOnlineStatus)
})

onUnmounted(() => {
  window.removeEventListener('online', updateOnlineStatus)
  window.removeEventListener('offline', updateOnlineStatus)
})
</script>

<style scoped>
.offline-notice {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 9999;
  background: linear-gradient(135deg, #ef4444, #dc2626);
  color: white;
  padding: 12px 20px;
  text-align: center;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.offline-content {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 500;
}

.slide-enter-active,
.slide-leave-active {
  /* P1 第二批：离线提示过渡压缩 */
  transition:
    transform var(--motion-duration-medium) var(--motion-ease-standard),
    opacity var(--motion-duration-medium) var(--motion-ease-standard);
}

.slide-enter-from,
.slide-leave-to {
  transform: translateY(-100%);
  opacity: 0;
}
</style>
