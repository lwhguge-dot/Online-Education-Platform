<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * 通用提示组件（BaseTooltip）
 * 目标：统一页面提示样式，并兼顾移动端点击查看体验。
 */
const props = defineProps({
  // 提示文本，留空时组件仅渲染插槽内容
  text: {
    type: String,
    default: ''
  },
  // 弹层方向
  placement: {
    type: String,
    default: 'top',
    validator: (value) => ['top', 'bottom', 'left', 'right'].includes(value)
  },
  // 最大宽度，避免长文案撑破布局
  maxWidth: {
    type: String,
    default: '240px'
  },
  // 是否禁用提示
  disabled: {
    type: Boolean,
    default: false
  }
})

const rootRef = ref(null)
const visible = ref(false)
const supportsHover = ref(true)

const canShow = computed(() => Boolean(props.text) && !props.disabled)

const tooltipPositionClasses = computed(() => {
  const map = {
    top: 'left-1/2 -translate-x-1/2 bottom-full mb-2',
    bottom: 'left-1/2 -translate-x-1/2 top-full mt-2',
    left: 'right-full mr-2 top-1/2 -translate-y-1/2',
    right: 'left-full ml-2 top-1/2 -translate-y-1/2'
  }
  return map[props.placement] || map.top
})

const tooltipStyle = computed(() => ({ maxWidth: props.maxWidth }))

const showTooltip = () => {
  if (!canShow.value) return
  visible.value = true
}

const hideTooltip = () => {
  visible.value = false
}

const toggleTooltip = () => {
  if (!canShow.value) return
  visible.value = !visible.value
}

// 点击组件外部时关闭提示
const handleDocumentClick = (event) => {
  if (!visible.value || !rootRef.value) return
  if (!rootRef.value.contains(event.target)) {
    visible.value = false
  }
}

// 键盘 Esc 关闭提示
const handleDocumentKeydown = (event) => {
  if (event.key === 'Escape') {
    visible.value = false
  }
}

// 移动端采用点击触发，桌面端保留 hover 体验
const handleTriggerClick = () => {
  if (!supportsHover.value) {
    toggleTooltip()
  }
}

onMounted(() => {
  if (typeof window !== 'undefined' && window.matchMedia) {
    supportsHover.value = window.matchMedia('(hover: hover)').matches
  }
  document.addEventListener('click', handleDocumentClick, true)
  document.addEventListener('keydown', handleDocumentKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick, true)
  document.removeEventListener('keydown', handleDocumentKeydown)
})
</script>

<template>
  <span
    ref="rootRef"
    class="relative inline-flex items-center"
    @mouseenter="supportsHover ? showTooltip() : null"
    @mouseleave="supportsHover ? hideTooltip() : null"
    @focusin="showTooltip"
    @focusout="hideTooltip"
    @click="handleTriggerClick"
  >
    <slot />

    <Transition name="tooltip-fade">
      <span
        v-if="visible && canShow"
        role="tooltip"
        class="pointer-events-none absolute z-[120] px-3 py-2 rounded-lg bg-shuimo text-white text-xs leading-5 shadow-xl border border-white/15 backdrop-blur-sm"
        :class="tooltipPositionClasses"
        :style="tooltipStyle"
      >
        {{ text }}
      </span>
    </Transition>
  </span>
</template>

<style scoped>
.tooltip-fade-enter-active,
.tooltip-fade-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.tooltip-fade-enter-from,
.tooltip-fade-leave-to {
  opacity: 0;
  transform: translateY(2px);
}
</style>
