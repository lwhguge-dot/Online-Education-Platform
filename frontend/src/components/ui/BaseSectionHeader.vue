<script setup>
defineProps({
  title: {
    type: String,
    required: true
  },
  icon: {
    type: Object, // Icon component
    default: null
  },
  iconColor: {
    type: String,
    default: 'text-zhizi'
  }
})
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
  transition: transform 0.3s ease;
}
.icon-hover-rotate:hover {
  transform: rotate(15deg);
}
</style>
