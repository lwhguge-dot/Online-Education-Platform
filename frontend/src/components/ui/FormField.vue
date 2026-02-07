<script setup>
import { computed } from 'vue'
import { AlertCircle, Check } from 'lucide-vue-next'

const props = defineProps({
  label: {
    type: String,
    default: ''
  },
  required: {
    type: Boolean,
    default: false
  },
  error: {
    type: String,
    default: ''
  },
  success: {
    type: Boolean,
    default: false
  },
  hint: {
    type: String,
    default: ''
  }
})

const hasError = computed(() => !!props.error)
const showSuccess = computed(() => props.success && !hasError.value)
</script>

<template>
  <div class="form-field space-y-1.5">
    <!-- 标签 -->
    <label v-if="label" class="flex items-center gap-1 text-sm font-medium text-shuimo/70">
      {{ label }}
      <span v-if="required" class="text-yanzhi">*</span>
    </label>
    
    <!-- 输入框插槽 -->
    <div class="relative">
      <slot></slot>
      
      <!-- 状态图标 -->
      <div v-if="hasError || showSuccess" class="absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none">
        <AlertCircle v-if="hasError" class="w-5 h-5 text-yanzhi animate-shake" />
        <Check v-else-if="showSuccess" class="w-5 h-5 text-tianlv animate-pop" />
      </div>
    </div>
    
    <!-- 错误提示 -->
    <Transition name="slide-fade">
      <p v-if="hasError" class="flex items-center gap-1 text-xs text-yanzhi">
        <AlertCircle class="w-3 h-3" />
        {{ error }}
      </p>
    </Transition>
    
    <!-- 提示信息 -->
    <p v-if="hint && !hasError" class="text-xs text-shuimo/50">{{ hint }}</p>
  </div>
</template>

<style scoped>
@keyframes shake {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-4px); }
  75% { transform: translateX(4px); }
}

@keyframes pop {
  0% { transform: scale(0); }
  50% { transform: scale(1.2); }
  100% { transform: scale(1); }
}

.animate-shake {
  animation: shake 0.3s ease-in-out;
}

.animate-pop {
  animation: pop 0.3s cubic-bezier(0.68, -0.55, 0.265, 1.55);
}

.slide-fade-enter-active {
  transition: all 0.2s ease-out;
}

.slide-fade-leave-active {
  transition: all 0.15s ease-in;
}

.slide-fade-enter-from,
.slide-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

/* 输入框错误状态样式 */
.form-field:has(.text-yanzhi) :deep(input),
.form-field:has(.text-yanzhi) :deep(textarea),
.form-field:has(.text-yanzhi) :deep(select) {
  border-color: rgb(var(--color-yanzhi, 220 38 38) / 0.5);
  background-color: rgb(var(--color-yanzhi, 220 38 38) / 0.02);
}

/* 输入框成功状态样式 */
.form-field:has(.text-tianlv.animate-pop) :deep(input),
.form-field:has(.text-tianlv.animate-pop) :deep(textarea),
.form-field:has(.text-tianlv.animate-pop) :deep(select) {
  border-color: rgb(var(--color-tianlv, 34 197 94) / 0.5);
}
</style>
