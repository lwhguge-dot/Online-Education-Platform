<template>
  <div class="glass-card rounded-2xl p-6">
    <h3 class="text-lg font-bold text-shuimo mb-4 font-song">{{ title }}</h3>
    <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
      <slot />
    </div>
  </div>
</template>

<script setup>
/**
 * 快捷操作容器组件
 * 用于教师中心的快捷操作按钮区域
 * 子元素建议使用 QuickActionButton 组件或带有 group 类的按钮
 *
 * @prop {String} title - 区块标题
 */
defineProps({
  title: { type: String, default: '快捷操作' }
})
</script>

<style scoped>
/* 快捷操作按钮的悬停效果由子组件或父组件的 group 类控制 */
/* 此组件仅作为容器，动效定义在 animations.css 和子组件中 */
</style>
