<script setup>
import { watch, useSlots, onBeforeUnmount, ref, nextTick } from 'vue'
import { X } from 'lucide-vue-next'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '' },
  maxWidthClass: { type: String, default: 'max-w-lg' },
  closeOnMask: { type: Boolean, default: true },
  showClose: { type: Boolean, default: true },
  closeOnEsc: { type: Boolean, default: true }
})

const emit = defineEmits(['update:modelValue', 'close'])

const slots = useSlots()
const dialogPanelRef = ref(null)
const previousActiveElement = ref(null)

const close = () => {
  emit('update:modelValue', false)
  emit('close')
}

const onMaskClick = () => {
  if (!props.closeOnMask) return
  close()
}

const updateBodyLock = (delta) => {
  const key = 'modalCount'
  const current = Number(document.body.dataset[key] || '0')
  const next = Math.max(0, current + delta)
  document.body.dataset[key] = String(next)
  document.body.style.overflow = next > 0 ? 'hidden' : ''
}

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

const focusFirstElement = () => {
  const focusables = getFocusableElements()
  if (focusables.length > 0) {
    focusables[0].focus()
    return
  }
  dialogPanelRef.value?.focus()
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

  if (event.key === 'Escape' && props.closeOnEsc) {
    // 键盘用户可通过 Esc 快速关闭弹窗
    event.preventDefault()
    close()
    return
  }

  if (event.key === 'Tab') {
    // 约束焦点在弹窗内部循环，避免焦点穿透到背景页面
    trapFocus(event)
  }
}

watch(
  () => props.modelValue,
  (val, oldVal) => {
    if (val && !oldVal) updateBodyLock(1)
    if (!val && oldVal) updateBodyLock(-1)
  },
  { immediate: true }
)

watch(
  () => props.modelValue,
  async (val, oldVal) => {
    if (val && !oldVal) {
      // 记录打开前焦点，关闭后恢复，提升键盘导航连续性
      previousActiveElement.value = document.activeElement
      await nextTick()
      focusFirstElement()
      return
    }

    if (!val && oldVal) {
      previousActiveElement.value?.focus?.()
      previousActiveElement.value = null
    }
  }
)

onBeforeUnmount(() => {
  if (props.modelValue) updateBodyLock(-1)
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
        :aria-labelledby="title ? 'modal-title' : undefined"
        @keydown="handleDialogKeydown"
      >
        <div class="absolute inset-0 bg-shuimo/20 backdrop-blur-[2px]" @click="onMaskClick" aria-hidden="true"></div>

        <div
          ref="dialogPanelRef"
          tabindex="-1"
          :class="['relative bg-white/95 backdrop-blur-xl rounded-2xl shadow-2xl w-full overflow-hidden animate-scale-in border border-white/20', maxWidthClass]"
        >
          <div class="flex flex-col max-h-[85vh] min-h-0">
            <div v-if="slots.header || title" class="p-6 border-b border-slate-100 flex items-center justify-between gap-4 shrink-0">
              <div class="min-w-0 flex-1">
                <slot name="header">
                  <h3 id="modal-title" class="font-bold text-lg text-shuimo truncate">{{ title }}</h3>
                </slot>
              </div>
              <button
                v-if="showClose"
                @click="close"
                class="p-2 rounded-lg hover:bg-slate-100 text-shuimo/40 hover:text-shuimo transition-colors"
                aria-label="关闭弹窗"
              >
                <X class="w-5 h-5" />
              </button>
            </div>

            <div class="p-6 flex-1 overflow-y-auto min-h-0">
              <slot></slot>
            </div>

            <div v-if="slots.footer" class="p-6 border-t border-slate-100 flex justify-end gap-3 shrink-0">
              <slot name="footer"></slot>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.modal-enter-active,
.modal-leave-active {
  /* P1：蒙层过渡仅保留透明度 */
  transition: opacity var(--motion-duration-medium) var(--motion-ease-standard);
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-from .animate-scale-in,
.modal-leave-to .animate-scale-in {
  transform: scale(0.95);
}

/* 弹窗内容动画使用全局 scale-in */
.animate-scale-in {
  animation: scale-in var(--motion-duration-medium) var(--motion-ease-standard) forwards;
}
</style>
