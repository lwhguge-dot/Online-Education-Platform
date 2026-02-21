<script setup>
import { ref, watch, nextTick, onBeforeUnmount } from 'vue'
import { AlertTriangle, Trash2, Info, HelpCircle, X } from 'lucide-vue-next'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '确认操作' },
  message: { type: String, default: '确定要执行此操作吗？' },
  type: { type: String, default: 'warning', validator: v => ['warning', 'danger', 'info', 'question'].includes(v) },
  confirmText: { type: String, default: '确定' },
  cancelText: { type: String, default: '取消' },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'confirm', 'cancel'])
const dialogPanelRef = ref(null)
const previousActiveElement = ref(null)

const iconMap = {
  warning: AlertTriangle,
  danger: Trash2,
  info: Info,
  question: HelpCircle
}

const colorMap = {
  warning: { bg: 'bg-amber-50', icon: 'text-amber-500', btn: 'from-amber-500 to-orange-500 hover:from-amber-600 hover:to-orange-600' },
  danger: { bg: 'bg-red-50', icon: 'text-red-500', btn: 'from-red-500 to-rose-500 hover:from-red-600 hover:to-rose-600' },
  info: { bg: 'bg-blue-50', icon: 'text-blue-500', btn: 'from-blue-500 to-cyan-500 hover:from-blue-600 hover:to-cyan-600' },
  question: { bg: 'bg-purple-50', icon: 'text-purple-500', btn: 'from-purple-500 to-indigo-500 hover:from-purple-600 hover:to-indigo-600' }
}

const close = () => emit('update:modelValue', false)
const handleConfirm = () => { emit('confirm'); if (!props.loading) close() }
const handleCancel = () => { emit('cancel'); close() }

const getFocusableElements = () => {
  if (!dialogPanelRef.value) return []

  const selectors = [
    'a[href]',
    'button:not([disabled])',
    'input:not([disabled])',
    'select:not([disabled])',
    'textarea:not([disabled])',
    '[tabindex]:not([tabindex="-1"])'
  ]

  return Array.from(dialogPanelRef.value.querySelectorAll(selectors.join(',')))
    .filter((el) => !el.hasAttribute('aria-hidden'))
}

const focusPrimaryAction = () => {
  const focusables = getFocusableElements()
  if (focusables.length === 0) {
    dialogPanelRef.value?.focus()
    return
  }

  const confirmButton = focusables.find((el) => el.dataset.confirm === 'true')
  if (confirmButton) {
    confirmButton.focus()
    return
  }

  focusables[0].focus()
}

const trapFocus = (event) => {
  const focusables = getFocusableElements()
  if (focusables.length === 0) {
    event.preventDefault()
    dialogPanelRef.value?.focus()
    return
  }

  const first = focusables[0]
  const last = focusables[focusables.length - 1]
  const active = document.activeElement

  if (event.shiftKey && active === first) {
    event.preventDefault()
    last.focus()
    return
  }

  if (!event.shiftKey && active === last) {
    event.preventDefault()
    first.focus()
  }
}

const handleDialogKeydown = (event) => {
  if (!props.modelValue) return

  if (event.key === 'Escape') {
    // 提供统一的 Esc 快捷关闭能力
    event.preventDefault()
    handleCancel()
    return
  }

  if (event.key === 'Tab') {
    // 键盘焦点应停留在确认框内部
    trapFocus(event)
  }
}

watch(
  () => props.modelValue,
  async (val, oldVal) => {
    if (val && !oldVal) {
      // 记录并恢复焦点，保证键盘流连续
      previousActiveElement.value = document.activeElement
      await nextTick()
      focusPrimaryAction()
      return
    }

    if (!val && oldVal) {
      previousActiveElement.value?.focus?.()
      previousActiveElement.value = null
    }
  }
)

onBeforeUnmount(() => {
  previousActiveElement.value?.focus?.()
  previousActiveElement.value = null
})
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div
        v-if="modelValue"
        class="fixed inset-0 z-[9999] flex items-center justify-center p-4"
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirm-dialog-title"
        @keydown="handleDialogKeydown"
      >
        <!-- 背景遮罩 -->
        <div class="absolute inset-0 bg-shuimo/20 backdrop-blur-[2px]" @click="handleCancel"></div>
        <!-- 弹窗内容 -->
        <div
          ref="dialogPanelRef"
          tabindex="-1"
          class="relative bg-white/95 backdrop-blur-xl rounded-2xl shadow-2xl w-full max-w-sm overflow-hidden animate-scale-in border border-white/20"
        >
          <!-- 关闭按钮 -->
          <button
            @click="handleCancel"
            aria-label="关闭确认弹窗"
            class="absolute top-4 right-4 p-1 rounded-full hover:bg-slate-100 text-slate-400 hover:text-slate-600 transition-colors"
          >
            <X class="w-5 h-5" />
          </button>
          <!-- 图标区域 -->
          <div class="pt-8 pb-4 flex justify-center">
            <div :class="['w-16 h-16 rounded-full flex items-center justify-center', colorMap[type].bg]">
              <component :is="iconMap[type]" :class="['w-8 h-8', colorMap[type].icon]" />
            </div>
          </div>
          <!-- 内容区域 -->
          <div class="px-6 pb-6 text-center">
            <h3 id="confirm-dialog-title" class="text-lg font-bold text-shuimo mb-2">{{ title }}</h3>
            <p class="text-shuimo/70 text-sm leading-relaxed">{{ message }}</p>
          </div>
          <!-- 按钮区域 -->
          <div class="px-6 pb-6 flex gap-3">
            <button @click="handleCancel" :disabled="loading"
              class="flex-1 px-4 py-2.5 rounded-xl border border-slate-200 text-shuimo/70 hover:bg-slate-50 hover:border-slate-300 transition-all font-medium disabled:opacity-50">
              {{ cancelText }}
            </button>
            <button @click="handleConfirm" :disabled="loading"
              data-confirm="true"
              :class="['flex-1 px-4 py-2.5 rounded-xl text-white font-medium transition-all bg-gradient-to-r shadow-lg hover:shadow-xl disabled:opacity-50', colorMap[type].btn]">
              <span v-if="loading" class="flex items-center justify-center gap-2">
                <svg class="animate-spin w-4 h-4" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" fill="none"/><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"/></svg>
                处理中...
              </span>
              <span v-else>{{ confirmText }}</span>
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
/* P1：弹窗遮罩仅过渡透明度，避免 all 带来额外开销 */
.modal-enter-active,
.modal-leave-active {
  transition: opacity var(--motion-duration-medium) var(--motion-ease-standard);
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-from .animate-scale-in,
.modal-leave-to .animate-scale-in {
  transform: scale(0.9);
}

.animate-scale-in {
  animation: scaleIn var(--motion-duration-medium) var(--motion-ease-standard) forwards;
}

@keyframes scaleIn {
  from {
    opacity: 0;
    transform: scale(0.9);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}
</style>
