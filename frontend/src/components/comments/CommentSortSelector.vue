<script setup>
import { Clock, Flame } from 'lucide-vue-next'

const props = defineProps({
  currentSort: {
    type: String,
    default: 'time'
  }
})

const emit = defineEmits(['change'])

const sortOptions = [
  { value: 'time', label: '最新', icon: Clock },
  { value: 'hot', label: '最热', icon: Flame }
]

const changeSort = (value) => {
  if (value !== props.currentSort) {
    emit('change', value)
  }
}
</script>

<template>
  <div class="flex items-center gap-1 bg-slate-100/80 rounded-lg p-1">
    <button
      v-for="option in sortOptions"
      :key="option.value"
      @click="changeSort(option.value)"
      class="flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm font-medium transition-all"
      :class="currentSort === option.value 
        ? 'bg-white text-qinghua shadow-sm' 
        : 'text-shuimo/60 hover:text-shuimo'"
    >
      <component :is="option.icon" class="w-3.5 h-3.5" />
      {{ option.label }}
    </button>
  </div>
</template>
