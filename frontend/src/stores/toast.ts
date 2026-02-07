import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Ref } from 'vue'

export type ToastType = 'success' | 'error' | 'warning' | 'info'

export interface Toast {
    id: number
    message: string
    type: ToastType
}

export const useToastStore = defineStore('toast', () => {
    const toasts: Ref<Toast[]> = ref([])
    let idCounter = 0

    const add = (message: string, type: ToastType = 'info', duration = 3000): void => {
        const id = idCounter++
        const toast: Toast = { id, message, type }
        toasts.value.push(toast)

        if (duration > 0) {
            setTimeout(() => {
                remove(id)
            }, duration)
        }
    }

    const remove = (id: number): void => {
        const idx = toasts.value.findIndex(t => t.id === id)
        if (idx !== -1) {
            toasts.value.splice(idx, 1)
        }
    }

    const success = (msg: string, duration = 3000): void => add(msg, 'success', duration)
    const error = (msg: string, duration = 4000): void => add(msg, 'error', duration)
    const warning = (msg: string, duration = 3000): void => add(msg, 'warning', duration)
    const info = (msg: string, duration = 3000): void => add(msg, 'info', duration)

    return {
        toasts,
        add,
        remove,
        success,
        error,
        warning,
        info
    }
})
