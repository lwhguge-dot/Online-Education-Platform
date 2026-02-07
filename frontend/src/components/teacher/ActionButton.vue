<template>
  <button 
    :class="['flex items-center gap-3 p-4 rounded-xl transition-colors', colorClass]"
    @click="$emit('click')"
  >
    <component :is="icon" class="w-5 h-5" />
    <span class="font-medium">{{ label }}</span>
  </button>
</template>

<script setup>
defineProps({
  label: { type: String, required: true },
  icon: { type: Object, required: true },
  color: { 
    type: String, 
    default: 'tianlv',
    validator: (v) => ['tianlv', 'qinghua', 'zhizi', 'zijinghui', 'yanzhi'].includes(v)
  }
})

defineEmits(['click'])

const colorClass = {
  tianlv: 'bg-tianlv/10 text-tianlv hover:bg-tianlv/20',
  qinghua: 'bg-qinghua/10 text-qinghua hover:bg-qinghua/20',
  zhizi: 'bg-zhizi/10 text-zhizi hover:bg-zhizi/20',
  zijinghui: 'bg-zijinghui/10 text-zijinghui hover:bg-zijinghui/20',
  yanzhi: 'bg-yanzhi/10 text-yanzhi hover:bg-yanzhi/20'
}
</script>
