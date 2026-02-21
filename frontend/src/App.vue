<script setup>
import { RouterView } from 'vue-router'
import { onUnmounted, watch } from 'vue'
import OfflineNotice from './components/OfflineNotice.vue'
import BaseToast from './components/ui/BaseToast.vue'
import ConfirmDialog from './components/ui/ConfirmDialog.vue'
import { useConfirmStore } from './stores/confirm'
import { useAuthStore } from './stores/auth'
import { useToastStore } from './stores/toast'
import {
  connectWebSocket,
  disconnectWebSocket,
  onForceLogout,
  onNotification,
} from './services/websocket'

const confirmStore = useConfirmStore()
const authStore = useAuthStore()
const toastStore = useToastStore()

let offForceLogout = null
let offNotification = null

// 清理 WebSocket 监听器，避免重复注册导致事件重复触发
const cleanupWebSocketListeners = () => {
  if (offForceLogout) {
    offForceLogout()
    offForceLogout = null
  }
  if (offNotification) {
    offNotification()
    offNotification = null
  }
}

// 建立全局 WebSocket 连接（与登录态联动）
const setupWebSocket = () => {
  if (!authStore.token) {
    return
  }

  // 先清理旧监听器，避免重复绑定
  cleanupWebSocketListeners()
  connectWebSocket()

  offForceLogout = onForceLogout((reason) => {
    toastStore.error(reason || '账号已在其他设备登录')
    authStore.logout()
    window.location.href = '/login'
  })

  offNotification = onNotification((payload) => {
    if (payload?.title) {
      toastStore.info(payload.title)
    }
  })
}

// 统一断开并清理，确保登出后不会残留连接或监听器
const teardownWebSocket = () => {
  cleanupWebSocketListeners()
  disconnectWebSocket()
}

// 监听 token：登录后自动连接，登出后自动断开
watch(
  () => authStore.token,
  (token) => {
    if (token) {
      setupWebSocket()
      return
    }
    teardownWebSocket()
  },
  { immediate: true },
)

onUnmounted(() => {
  teardownWebSocket()
})
</script>

<template>
  <BaseToast />
  <OfflineNotice />
  <ConfirmDialog 
    v-model="confirmStore.visible"
    :title="confirmStore.title"
    :message="confirmStore.message"
    :type="confirmStore.type"
    :confirm-text="confirmStore.confirmText"
    :cancel-text="confirmStore.cancelText"
    :loading="confirmStore.loading"
    @confirm="confirmStore.confirm"
    @cancel="confirmStore.cancel"
  />
  <main id="app-main" role="main" class="min-h-screen">
    <!-- 无障碍：为页面提供稳定的一级标题锚点，避免无 H1 报警 -->
    <h1 class="visually-hidden-heading">智慧课堂系统</h1>
    <RouterView v-slot="{ Component }">
      <Transition name="page-fade" mode="out-in">
        <component :is="Component" />
      </Transition>
    </RouterView>
  </main>
</template>

<style>
/* 全局样式已在main.css中定义 */

/* 无障碍：视觉隐藏但对读屏可见的标题样式 */
.visually-hidden-heading {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
</style>
