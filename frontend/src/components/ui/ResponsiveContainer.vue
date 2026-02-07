<script setup>
defineProps({
  // 列数配置
  cols: {
    type: Object,
    default: () => ({
      sm: 1,
      md: 2,
      lg: 3,
      xl: 4
    })
  },
  gap: {
    type: String,
    default: '6' // Tailwind gap value
  },
  // 是否在移动端堆叠
  stackOnMobile: {
    type: Boolean,
    default: true
  }
})
</script>

<template>
  <div :class="[
    'grid',
    `gap-${gap}`,
    stackOnMobile ? 'grid-cols-1' : '',
    `sm:grid-cols-${cols.sm}`,
    `md:grid-cols-${cols.md}`,
    `lg:grid-cols-${cols.lg}`,
    `xl:grid-cols-${cols.xl}`
  ]">
    <slot></slot>
  </div>
</template>
