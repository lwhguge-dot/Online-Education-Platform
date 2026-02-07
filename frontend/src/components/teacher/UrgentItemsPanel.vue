<script setup>
import { AlertCircle, Clock, MessageSquare, FileText, CheckCircle } from 'lucide-vue-next'

const props = defineProps({
  items: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['click'])

const getIcon = (type) => {
  switch (type) {
    case 'homework': return FileText
    case 'question': return MessageSquare
    default: return AlertCircle
  }
}

const getIconColor = (type) => {
  switch (type) {
    case 'homework': return 'text-zhizi bg-zhizi/10'
    case 'question': return 'text-qinghua bg-qinghua/10'
    default: return 'text-yanzhi bg-yanzhi/10'
  }
}
</script>

<template>
  <div class="urgent-items-panel">
    <div v-if="items.length === 0" class="flex flex-col items-center justify-center py-8 text-center">
      <div class="w-16 h-16 rounded-full bg-slate-50 flex items-center justify-center mb-3">
        <CheckCircle class="w-8 h-8 text-slate-300" />
      </div>
      <p class="text-sm text-shuimo/60">暂无紧急事项</p>
    </div>
    
    <div v-else class="space-y-3 max-h-[300px] overflow-y-auto">
      <div v-for="item in items" :key="item.id" 
           @click="emit('click', item)"
           class="flex items-start gap-3 p-3 rounded-xl hover:bg-slate-50 transition-colors cursor-pointer group border border-transparent hover:border-slate-100">
        <div :class="['p-2 rounded-lg', getIconColor(item.type)]">
          <component :is="getIcon(item.type)" class="w-4 h-4" />
        </div>
        <div class="flex-1 min-w-0">
          <p class="text-sm text-shuimo group-hover:text-qinghua transition-colors font-medium line-clamp-2">
            {{ item.title }}
          </p>
          <div class="flex items-center gap-2 mt-1">
            <span v-if="item.courseName" class="text-xs text-shuimo/40">{{ item.courseName }}</span>
            <span v-if="item.deadline" class="text-xs text-yanzhi flex items-center gap-1">
              <Clock class="w-3 h-3" />
              {{ item.deadline }}
            </span>
          </div>
        </div>
        <div v-if="item.urgent" class="w-2 h-2 rounded-full bg-yanzhi animate-pulse"></div>
      </div>
    </div>
  </div>
</template>
