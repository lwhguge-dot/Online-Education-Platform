<template>
  <div class="glass-card rounded-2xl p-6 hover:shadow-xl transition-all duration-300 hover:-translate-y-1">
    <div class="flex items-center justify-between mb-3">
      <span class="text-sm font-medium text-shuimo/60">{{ label }}</span>
      <component :is="icon" :class="['w-5 h-5', iconColor]" />
    </div>
    <div :class="['text-3xl font-bold bg-gradient-to-r bg-clip-text text-transparent', gradient]">
      {{ value }}
    </div>
    <p :class="['text-xs mt-2', subtextColor]">{{ subtext }}</p>
  </div>
</template>

<script setup>
defineProps({
  label: { type: String, required: true },
  value: { type: [String, Number], required: true },
  subtext: { type: String, default: '' },
  icon: { type: Object, required: true },
  iconColor: { type: String, default: 'text-tianlv' },
  gradient: { type: String, default: 'from-tianlv to-qingsong' },
  subtextColor: { type: String, default: 'text-shuimo/50' }
})
</script>
