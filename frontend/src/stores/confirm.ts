import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Ref } from 'vue'

export type ConfirmType = 'warning' | 'info' | 'error' | 'success'

export interface ConfirmOptions {
  title?: string
  message?: string
  type?: ConfirmType
  confirmText?: string
  cancelText?: string
}

export const useConfirmStore = defineStore('confirm', () => {
  const visible: Ref<boolean> = ref(false)
  const title: Ref<string> = ref('确认操作')
  const message: Ref<string> = ref('确定要执行此操作吗？')
  const type: Ref<ConfirmType> = ref('warning')
  const confirmText: Ref<string> = ref('确定')
  const cancelText: Ref<string> = ref('取消')
  const loading: Ref<boolean> = ref(false)

  let resolvePromise: ((value: boolean) => void) | null = null

  const show = (options: ConfirmOptions = {}): Promise<boolean> => {
    title.value = options.title || '确认操作'
    message.value = options.message || '确定要执行此操作吗？'
    type.value = options.type || 'warning'
    confirmText.value = options.confirmText || '确定'
    cancelText.value = options.cancelText || '取消'
    loading.value = false
    visible.value = true

    return new Promise((resolve) => {
      resolvePromise = resolve
    })
  }

  const confirm = (): void => {
    visible.value = false
    if (resolvePromise) resolvePromise(true)
  }

  const cancel = (): void => {
    visible.value = false
    if (resolvePromise) resolvePromise(false)
  }

  const setLoading = (val: boolean): void => {
    loading.value = val
  }

  return { visible, title, message, type, confirmText, cancelText, loading, show, confirm, cancel, setLoading }
})
