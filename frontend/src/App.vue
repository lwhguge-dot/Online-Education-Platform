<script setup>
import { RouterView } from 'vue-router'
import OfflineNotice from './components/OfflineNotice.vue'
import BaseToast from './components/ui/BaseToast.vue'
import ConfirmDialog from './components/ui/ConfirmDialog.vue'
import { useConfirmStore } from './stores/confirm'

const confirmStore = useConfirmStore()
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
