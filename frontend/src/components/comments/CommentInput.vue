<script setup>
import { ref, computed } from 'vue'
import { Send, AlertCircle } from 'lucide-vue-next'

const props = defineProps({
  placeholder: {
    type: String,
    default: '发表你的评论...'
  },
  isMuted: {
    type: Boolean,
    default: false
  },
  muteReason: {
    type: String,
    default: ''
  },
  isReply: {
    type: Boolean,
    default: false
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['submit', 'cancel'])

const content = ref('')
const isFocused = ref(false)

const canSubmit = computed(() => {
  return content.value.trim().length > 0 && !props.isMuted && !props.loading
})

const handleSubmit = () => {
  if (!canSubmit.value) return
  emit('submit', content.value.trim())
  content.value = ''
}

const handleCancel = () => {
  content.value = ''
  emit('cancel')
}

const handleKeydown = (e) => {
  // Ctrl+Enter 或 Cmd+Enter 提交
  if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
    handleSubmit()
  }
}
</script>

<template>
  <div class="relative">
    <!-- 禁言提示 -->
    <div v-if="isMuted" class="flex items-center gap-2 p-3 bg-red-50 border border-red-100 rounded-xl text-sm text-red-600 mb-2">
      <AlertCircle class="w-4 h-4 flex-shrink-0" />
      <span>您已被禁言，无法发表评论{{ muteReason ? `（原因：${muteReason}）` : '' }}</span>
    </div>
    
    <div 
      class="flex items-start gap-3 p-3 rounded-xl border transition-all"
      :class="isFocused ? 'border-qinghua/30 bg-white shadow-sm' : 'border-slate-200 bg-slate-50/50'"
    >
      <textarea
        v-model="content"
        :placeholder="isMuted ? '您已被禁言' : placeholder"
        :disabled="isMuted || loading"
        @focus="isFocused = true"
        @blur="isFocused = false"
        @keydown="handleKeydown"
        class="flex-1 bg-transparent border-none outline-none resize-none text-sm text-shuimo placeholder:text-shuimo/40 min-h-[60px] max-h-[120px] disabled:opacity-50 disabled:cursor-not-allowed"
        rows="2"
      />
      
      <div class="flex flex-col gap-2">
        <button
          @click="handleSubmit"
          :disabled="!canSubmit"
          class="p-2 rounded-lg transition-all disabled:opacity-40 disabled:cursor-not-allowed"
          :class="canSubmit ? 'bg-qinghua text-white hover:bg-qinghua/90' : 'bg-slate-200 text-slate-400'"
        >
          <Send class="w-4 h-4" :class="{ 'animate-pulse': loading }" />
        </button>
        
        <button
          v-if="isReply"
          @click="handleCancel"
          class="p-2 rounded-lg bg-slate-100 text-shuimo/60 hover:bg-slate-200 transition-all text-xs"
        >
          取消
        </button>
      </div>
    </div>
    
    <p v-if="!isMuted" class="text-xs text-shuimo/40 mt-1.5 pl-1">
      按 Ctrl+Enter 发送
    </p>
  </div>
</template>
