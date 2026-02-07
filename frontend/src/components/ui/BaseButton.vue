<script setup>
import { computed } from 'vue'
import { Loader2 } from 'lucide-vue-next'

/**
 * 通用按钮组件 (BaseButton)
 * 
 * @description
 * 系统的核心交互组件，支持多种预设样式(variant)和尺寸(size)。
 * 
 * 核心特性：
 * 1. 自动处理 loading 状态，禁用点击并显示加载动画。
 * 2. 统一的 Hover 和 Active 状态反馈。
 * 3. 支持 block 属性撑满父容器。
 */
const props = defineProps({
  /**
   * 按钮样式变体
   * @values primary(主色), secondary(次要), danger(危险), ghost(幽灵), text(文字), custom(自定义), outline(描边)
   */
  variant: {
    type: String,
    default: 'primary',
    validator: (value) => ['primary', 'secondary', 'danger', 'ghost', 'text', 'custom', 'outline'].includes(value)
  },
  /**
   * 按钮尺寸
   * @values sm(小), md(默认), lg(大)
   */
  size: {
    type: String,
    default: 'md',
    validator: (value) => ['sm', 'md', 'lg'].includes(value)
  },
  /**
   * 是否处于加载状态
   */
  loading: {
    type: Boolean,
    default: false
  },
  /**
   * 是否禁用
   */
  disabled: {
    type: Boolean,
    default: false
  },
  /**
   * 是否为块级元素 (width: 100%)
   */
  block: {
    type: Boolean,
    default: false
  },
  /**
   * 原生按钮类型
   */
  type: {
    type: String,
    default: 'button'
  }
})

const sizeClasses = {
  sm: 'px-3 py-1.5 text-xs',
  md: 'px-5 py-2.5 text-sm',
  lg: 'px-8 py-3.5 text-base',
}

const variantClasses = {
  primary: 'bg-gradient-to-r from-qinghua to-halanzi text-white shadow-lg shadow-qinghua/20 hover:-translate-y-0.5 hover:shadow-qinghua/40 hover:shadow-xl active:scale-95 active:translate-y-0 active:shadow-qinghua/10',
  secondary: 'bg-white border border-slate-200 text-text-main hover:bg-slate-50 hover:text-primary hover:border-primary/30 active:scale-95 active:bg-slate-100',
  danger: 'bg-danger/10 text-danger hover:bg-danger/20 border border-danger/20 active:scale-95',
  ghost: 'bg-white/50 text-text-main border border-white/60 hover:bg-white/80 backdrop-blur-sm active:scale-95',
  text: 'bg-transparent text-text-main hover:text-primary p-0 shadow-none hover:translate-y-0 hover:bg-transparent active:scale-95',
  custom: 'shadow-lg hover:-translate-y-0.5 active:translate-y-0 active:scale-95 transition-all duration-300',
  outline: 'bg-transparent border-2 border-slate-300 text-text-main hover:border-qinghua hover:text-qinghua hover:bg-qinghua/5 active:scale-95',
}

const computedClasses = computed(() => {
  return [
    'inline-flex items-center justify-center font-medium rounded-xl transition-all duration-300 relative overflow-hidden',
    sizeClasses[props.size],
    variantClasses[props.variant],
    props.block ? 'w-full' : '',
    (props.disabled || props.loading) ? 'opacity-60 cursor-not-allowed pointer-events-none' : 'cursor-pointer'
  ]
})
</script>

<template>
  <button 
    :type="type" 
    :class="computedClasses"
    :disabled="disabled || loading"
    v-bind="$attrs"
  >
    <!-- Ripple effect placeholder (can be implemented with directive) -->
    
    <span v-if="loading" class="mr-2 animate-spin">
      <Loader2 class="w-4 h-4" />
    </span>
    <span :class="['flex items-center justify-center gap-2', { 'opacity-0': loading && !block }]">
      <slot />
    </span>
    
    <!-- Absolute centered loader for block buttons -->
    <div v-if="loading && block" class="absolute inset-0 flex items-center justify-center">
      <Loader2 class="w-5 h-5 animate-spin" />
    </div>
  </button>
</template>
