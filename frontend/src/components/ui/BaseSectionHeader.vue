<script setup lang="ts">
interface BaseSectionHeaderProps {
  title: string
  icon?: object | (() => unknown) | null
  iconColor?: string
}

withDefaults(defineProps<BaseSectionHeaderProps>(), {
  icon: null,
  iconColor: 'text-zhizi'
})

defineSlots<{
  actions?: () => unknown
}>()
</script>

<template>
  <div class="flex items-center justify-between mb-6">
    <h3 class="text-lg font-bold text-shuimo flex items-center gap-2 font-song">
      <component 
        v-if="icon"
        :is="icon" 
        class="w-5 h-5 icon-hover-rotate transition-colors"
        :class="iconColor"
      />
      {{ title }}
    </h3>
    <div class="flex items-center gap-4">
      <slot name="actions"></slot>
    </div>
  </div>
</template>

<style scoped>
.icon-hover-rotate {
  /* P1 第二批：标题图标过渡压缩 */
  transition: transform var(--motion-duration-medium) var(--motion-ease-standard);
}
.icon-hover-rotate:hover {
  transform: rotate(15deg);
}
</style>
