import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Ref } from 'vue'

export type ConfirmType = 'warning' | 'danger' | 'info' | 'question'

export interface ConfirmOptions {
  title?: string
  message?: string
  type?: ConfirmType
  confirmText?: string
  cancelText?: string
}

interface ConfirmQueueItem {
  options: ConfirmOptions
  resolve: (value: boolean) => void
}

export const useConfirmStore = defineStore('confirm', () => {
  const visible: Ref<boolean> = ref(false)
  const title: Ref<string> = ref('确认操作')
  const message: Ref<string> = ref('确定要执行此操作吗？')
  const type: Ref<ConfirmType> = ref('warning')
  const confirmText: Ref<string> = ref('确定')
  const cancelText: Ref<string> = ref('取消')
  const loading: Ref<boolean> = ref(false)

  // 使用队列保证多个 confirm 并发触发时按顺序展示
  const queue: ConfirmQueueItem[] = []
  let activeResolver: ((value: boolean) => void) | null = null

  const applyOptions = (options: ConfirmOptions = {}): void => {
    title.value = options.title || '确认操作'
    message.value = options.message || '确定要执行此操作吗？'
    type.value = options.type || 'warning'
    confirmText.value = options.confirmText || '确定'
    cancelText.value = options.cancelText || '取消'
    loading.value = false
  }

  const openNext = (): void => {
    if (visible.value || queue.length === 0) {
      return
    }

    const next = queue.shift()
    if (!next) {
      return
    }

    applyOptions(next.options)
    activeResolver = next.resolve
    visible.value = true
  }

  const finishCurrent = (confirmed: boolean): void => {
    visible.value = false
    loading.value = false

    if (activeResolver) {
      activeResolver(confirmed)
      activeResolver = null
    }

    // 让弹窗先完成一次关闭渲染，再展示队列中的下一项
    setTimeout(() => {
      openNext()
    }, 0)
  }

  const show = (options: ConfirmOptions = {}): Promise<boolean> => {
    return new Promise((resolve) => {
      queue.push({ options, resolve })
      openNext()
    })
  }

  const confirm = (): void => {
    finishCurrent(true)
  }

  const cancel = (): void => {
    finishCurrent(false)
  }

  const setLoading = (val: boolean): void => {
    loading.value = val
  }

  return { visible, title, message, type, confirmText, cancelText, loading, show, confirm, cancel, setLoading }
})
