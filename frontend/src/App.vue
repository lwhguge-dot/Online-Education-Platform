<script setup>
import { RouterView } from 'vue-router'
import { onMounted, onUnmounted } from 'vue'
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

// 建立全局 WebSocket 连接（当前登录用户）
const setupWebSocket = () => {
  const userId = authStore.user?.id
  if (!userId) {
    return
  }

  connectWebSocket(userId)

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

onMounted(() => {
  setupWebSocket()
})

onUnmounted(() => {
  if (offForceLogout) {
    offForceLogout()
    offForceLogout = null
  }
  if (offNotification) {
    offNotification()
    offNotification = null
  }
  disconnectWebSocket()
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
  <RouterView v-slot="{ Component }">
    <Transition name="page-fade" mode="out-in">
      <component :is="Component" />
    </Transition>
  </RouterView>
</template>

<style>
/* 全局样式已在main.css中定义 */
</style>
