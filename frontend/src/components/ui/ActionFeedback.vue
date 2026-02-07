<script setup>
import { ref, watch } from 'vue'
import { Check, X, AlertCircle, Info } from 'lucide-vue-next'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  type: {
    type: String,
    default: 'success', // success, error, warning, info
    validator: (v) => ['success', 'error', 'warning', 'info'].includes(v)
  },
  message: {
    type: String,
    default: ''
  },
  duration: {
    type: Number,
    default: 2000
  }
})

const emit = defineEmits(['close'])

const show = ref(false)

const icons = {
  success: Check,
  error: X,
  warning: AlertCircle,
  info: Info
}

const colors = {
  success: {
    bg: 'bg-tianlv',
    ring: 'ring-tianlv/30',
    text: 'text-tianlv'
  },
  error: {
    bg: 'bg-yanzhi',
    ring: 'ring-yanzhi/30',
    text: 'text-yanzhi'
  },
  warning: {
    bg: 'bg-zhizi',
    ring: 'ring-zhizi/30',
    text: 'text-zhizi'
  },
  info: {
    bg: 'bg-qinghua',
    ring: 'ring-qinghua/30',
    text: 'text-qinghua'
  }
}

watch(() => props.visible, (val) => {
  if (val) {
    show.value = true
    if (props.duration > 0) {
      setTimeout(() => {
        show.value = false
        emit('close')
      }, props.duration)
    }
  } else {
    show.value = false
  }
}, { immediate: true })
</script>

<template>
  <Teleport to="body">
    <Transition name="feedback">
      <div v-if="show" class="fixed inset-0 flex items-center justify-center z-[100] pointer-events-none">
        <div :class="['flex flex-col items-center gap-3 p-6 rounded-2xl bg-white shadow-2xl ring-4 pointer-events-auto', colors[type].ring]">
          <!-- 动画图标 -->
          <div :class="['w-16 h-16 rounded-full flex items-center justify-center animate-bounce-in', colors[type].bg]">
            <component :is="icons[type]" class="w-8 h-8 text-white" />
          </div>
          
          <!-- 消息 -->
          <p v-if="message" :class="['text-base font-medium', colors[type].text]">{{ message }}</p>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
@keyframes bounce-in {
  0% { transform: scale(0); }
  50% { transform: scale(1.2); }
  100% { transform: scale(1); }
}

.animate-bounce-in {
  animation: bounce-in 0.4s cubic-bezier(0.68, -0.55, 0.265, 1.55);
}

.feedback-enter-active {
  animation: feedback-in 0.3s ease-out;
}

.feedback-leave-active {
  animation: feedback-out 0.2s ease-in;
}

@keyframes feedback-in {
  from {
    opacity: 0;
    transform: scale(0.8);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

@keyframes feedback-out {
  from {
    opacity: 1;
    transform: scale(1);
  }
  to {
    opacity: 0;
    transform: scale(0.8);
  }
}
</style>
