<script setup>
import { computed, ref, useAttrs } from 'vue'

const props = defineProps({
  modelValue: {
    type: [String, Number],
    default: ''
  },
  label: {
    type: String,
    default: ''
  },
  placeholder: {
    type: String,
    default: ''
  },
  type: {
    type: String,
    default: 'text'
  },
  error: {
    type: String,
    default: ''
  },
  icon: {
    type: [Object, Function],
    default: null
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'focus', 'blur'])

const attrs = useAttrs()
const internalId = `input-${Math.random().toString(36).slice(2, 9)}`
const inputId = computed(() => attrs.id || attrs.name || internalId)
const inputName = computed(() => attrs.name || attrs.id || undefined)

const isFocused = ref(false)

const handleFocus = (e) => {
  isFocused.value = true
  emit('focus', e)
}

const handleBlur = (e) => {
  isFocused.value = false
  emit('blur', e)
}

const containerClasses = computed(() => [
  'relative transition-all duration-300 rounded-xl border-2 flex items-center bg-white/50 backdrop-blur-sm',
  isFocused.value ? 'border-primary ring-4 ring-primary/10 shadow-lg shadow-primary/10' : 'border-transparent hover:border-text-muted/30',
  props.error ? 'border-danger ring-danger/10' : '',
  props.disabled ? 'opacity-60 bg-gray-100 cursor-not-allowed' : ''
])
</script>

<template>
  <div class="mb-4">
    <label v-if="label" :for="inputId" class="block text-sm font-medium text-text-main mb-1.5 ml-1">
      {{ label }}
    </label>
    <div :class="containerClasses">
      <div v-if="icon" class="pl-3 text-text-muted">
        <component :is="icon" class="w-5 h-5" />
      </div>
      <input
        v-bind="$attrs"
        :id="inputId"
        :name="inputName"
        :type="type"
        :value="modelValue"
        :placeholder="placeholder"
        :disabled="disabled"
        @input="$emit('update:modelValue', $event.target.value)"
        @focus="handleFocus"
        @blur="handleBlur"
        class="w-full bg-transparent border-0 px-4 py-3 text-text-main placeholder-text-muted/50 focus:ring-0 outline-none rounded-xl"
      />
      <div v-if="$slots.suffix" class="pr-3 flex items-center">
        <slot name="suffix" />
      </div>
    </div>
    <Transition name="fade">
      <p v-if="error" class="text-xs text-danger mt-1 ml-1 flex items-center">
        <svg class="w-3 h-3 mr-1" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        {{ error }}
      </p>
    </Transition>
  </div>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s, transform 0.2s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(-5px);
}
</style>
