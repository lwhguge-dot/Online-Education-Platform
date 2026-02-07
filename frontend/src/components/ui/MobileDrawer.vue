<script setup>
import { watch } from 'vue'
import { X } from 'lucide-vue-next'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  position: {
    type: String,
    default: 'left', // left, right, bottom
    validator: (v) => ['left', 'right', 'bottom'].includes(v)
  },
  title: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['close'])

// 禁止背景滚动
watch(() => props.visible, (val) => {
  if (val) {
    document.body.style.overflow = 'hidden'
  } else {
    document.body.style.overflow = ''
  }
})

const positionClasses = {
  left: 'left-0 top-0 h-full w-72 max-w-[85vw]',
  right: 'right-0 top-0 h-full w-72 max-w-[85vw]',
  bottom: 'bottom-0 left-0 right-0 max-h-[85vh] rounded-t-2xl'
}

const transitionClasses = {
  left: {
    enter: 'translate-x-0',
    leave: '-translate-x-full'
  },
  right: {
    enter: 'translate-x-0',
    leave: 'translate-x-full'
  },
  bottom: {
    enter: 'translate-y-0',
    leave: 'translate-y-full'
  }
}
</script>

<template>
  <Teleport to="body">
    <Transition name="drawer">
      <div v-if="visible" class="fixed inset-0 z-[60]">
        <!-- 遮罩 -->
        <div 
          class="absolute inset-0 bg-shuimo/30 backdrop-blur-[2px]"
          @click="emit('close')"
        ></div>
        
        <!-- 抽屉内容 -->
        <div 
          :class="[
            'absolute bg-white shadow-2xl flex flex-col transition-transform duration-300',
            positionClasses[position],
            visible ? transitionClasses[position].enter : transitionClasses[position].leave
          ]"
        >
          <!-- 头部 -->
          <div v-if="title || $slots.header" class="flex items-center justify-between px-4 py-3 border-b border-slate-100">
            <slot name="header">
              <h3 class="text-lg font-bold text-shuimo">{{ title }}</h3>
            </slot>
            <button 
              @click="emit('close')"
              class="p-2 hover:bg-slate-100 rounded-lg transition-colors"
            >
              <X class="w-5 h-5 text-shuimo/50" />
            </button>
          </div>
          
          <!-- 内容 -->
          <div class="flex-1 overflow-y-auto">
            <slot></slot>
          </div>
          
          <!-- 底部 -->
          <div v-if="$slots.footer" class="px-4 py-3 border-t border-slate-100">
            <slot name="footer"></slot>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.drawer-enter-active,
.drawer-leave-active {
  transition: opacity 0.3s ease;
}

.drawer-enter-from,
.drawer-leave-to {
  opacity: 0;
}

.drawer-enter-active > div:last-child,
.drawer-leave-active > div:last-child {
  transition: transform 0.3s ease;
}
</style>
