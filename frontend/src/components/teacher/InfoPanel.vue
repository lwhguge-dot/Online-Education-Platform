<template>
  <div class="glass-card rounded-2xl p-6">
    <div class="flex items-center justify-between mb-4">
      <h3 class="text-lg font-semibold text-shuimo flex items-center gap-2">
        <component :is="icon" :class="['w-5 h-5', iconColor]" />
        {{ title }}
      </h3>
      <span v-if="badge" class="text-xs bg-slate-200 text-shuimo px-2 py-1 rounded-full">
        {{ badge }}
      </span>
    </div>
    <slot>
      <!-- 默认空状态 -->
      <div v-if="emptyState" class="flex flex-col items-center justify-center py-8 text-center">
        <component :is="icon" class="w-12 h-12 text-slate-300 mb-3" />
        <p class="text-sm text-shuimo/60">{{ emptyTitle }}</p>
        <p class="text-xs text-shuimo/40 mt-1">{{ emptySubtitle }}</p>
      </div>
    </slot>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  icon: { type: Object, required: true },
  iconColor: { type: String, default: 'text-qinghua' },
  badge: { type: String, default: '' },
  emptyState: { type: Boolean, default: false },
  emptyTitle: { type: String, default: '暂无数据' },
  emptySubtitle: { type: String, default: '' }
})
</script>
