<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ChevronDown, Check } from 'lucide-vue-next'

const props = defineProps({
  modelValue: {
    type: [String, Number, null],
    default: null
  },
  options: {
    type: Array,
    default: () => []
    // 格式: [{ value: 'xxx', label: '显示文本' }] 或 ['选项1', '选项2']
  },
  placeholder: {
    type: String,
    default: '请选择'
  },
  disabled: {
    type: Boolean,
    default: false
  },
  size: {
    type: String,
    default: 'md', // sm, md, lg
    validator: (v) => ['sm', 'md', 'lg'].includes(v)
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const isOpen = ref(false)
const selectRef = ref(null)
const dropdownRef = ref(null)
const dropdownStyle = ref({})

// 标准化选项格式
const normalizedOptions = computed(() => {
  return props.options.map(opt => {
    if (typeof opt === 'object' && opt !== null) {
      return { value: opt.value, label: opt.label || opt.value }
    }
    return { value: opt, label: opt }
  })
})

// 当前选中的标签
const selectedLabel = computed(() => {
  const found = normalizedOptions.value.find(opt => opt.value === props.modelValue)
  return found ? found.label : props.placeholder
})

// 是否有选中值
const hasValue = computed(() => {
  return props.modelValue !== null && props.modelValue !== undefined && props.modelValue !== ''
})

// 尺寸样式
const sizeClasses = computed(() => {
  const sizes = {
    sm: 'px-2.5 py-1.5 text-xs min-h-[32px]',
    md: 'px-3 py-2 text-sm min-h-[40px]',
    lg: 'px-4 py-2.5 text-base min-h-[48px]'
  }
  return sizes[props.size]
})

// 计算下拉列表位置
const updateDropdownPosition = () => {
  if (!selectRef.value) return
  const rect = selectRef.value.getBoundingClientRect()
  const viewportHeight = window.innerHeight
  const dropdownHeight = 200 // 估计的下拉菜单高度
  
  // 检查下方是否有足够空间
  const spaceBelow = viewportHeight - rect.bottom
  const spaceAbove = rect.top
  
  let top, maxHeight
  if (spaceBelow >= dropdownHeight || spaceBelow >= spaceAbove) {
    // 在下方显示
    top = rect.bottom + 6
    maxHeight = Math.min(240, spaceBelow - 10)
  } else {
    // 在上方显示
    top = rect.top - dropdownHeight - 6
    maxHeight = Math.min(240, spaceAbove - 10)
  }
  
  dropdownStyle.value = {
    position: 'fixed',
    top: `${Math.max(10, top)}px`,
    left: `${rect.left}px`,
    width: `${rect.width}px`,
    maxHeight: `${maxHeight}px`,
    zIndex: 9999
  }
}

const toggleDropdown = async () => {
  if (props.disabled) return
  isOpen.value = !isOpen.value
  if (isOpen.value) {
    await nextTick()
    updateDropdownPosition()
  }
}

const selectOption = (option) => {
  emit('update:modelValue', option.value)
  emit('change', option.value)
  isOpen.value = false
}

const handleClickOutside = (e) => {
  if (selectRef.value && !selectRef.value.contains(e.target)) {
    if (dropdownRef.value && dropdownRef.value.contains(e.target)) {
      return
    }
    isOpen.value = false
  }
}

const handleScroll = () => {
  if (isOpen.value) {
    updateDropdownPosition()
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
  window.addEventListener('scroll', handleScroll, true)
  window.addEventListener('resize', handleScroll)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
  window.removeEventListener('scroll', handleScroll, true)
  window.removeEventListener('resize', handleScroll)
})
</script>

<template>
  <div ref="selectRef" class="relative inline-block w-full">
    <!-- 触发器 -->
    <button
      type="button"
      @click="toggleDropdown"
      :disabled="disabled"
      :class="[
        'w-full flex items-center justify-between gap-2 rounded-xl border transition-all duration-200 text-left',
        sizeClasses,
        isOpen 
          ? 'border-danqing ring-2 ring-danqing/20 bg-white shadow-lg' 
          : 'border-slate-200 bg-white/60 hover:border-danqing/50 hover:bg-danqing/5',
        disabled ? 'opacity-50 cursor-not-allowed bg-slate-100' : 'cursor-pointer',
      ]"
    >
      <span :class="hasValue ? 'text-shuimo' : 'text-shuimo/50'">
        {{ selectedLabel }}
      </span>
      <ChevronDown 
        :class="[
          'w-4 h-4 text-danqing transition-transform duration-200 flex-shrink-0',
          isOpen ? 'rotate-180' : ''
        ]" 
      />
    </button>

    <!-- 下拉列表 - 使用Teleport渲染到body -->
    <Teleport to="body">
      <Transition
        enter-active-class="transition duration-150 ease-out"
        enter-from-class="opacity-0 -translate-y-2 scale-95"
        enter-to-class="opacity-100 translate-y-0 scale-100"
        leave-active-class="transition duration-100 ease-in"
        leave-from-class="opacity-100 translate-y-0 scale-100"
        leave-to-class="opacity-0 -translate-y-2 scale-95"
      >
        <div
          v-if="isOpen"
          ref="dropdownRef"
          :style="dropdownStyle"
          class="py-1.5 bg-white rounded-xl border border-slate-200 shadow-xl shadow-slate-200/50 max-h-60 overflow-y-auto"
        >
          <div
            v-for="option in normalizedOptions"
            :key="option.value"
            @click="selectOption(option)"
            :class="[
              'flex items-center justify-between px-3 py-2 mx-1.5 rounded-lg cursor-pointer transition-all duration-150',
              option.value === modelValue
                ? 'bg-danqing/10 text-danqing font-medium'
                : 'text-shuimo hover:bg-slate-50'
            ]"
          >
            <span>{{ option.label }}</span>
            <Check 
              v-if="option.value === modelValue" 
              class="w-4 h-4 text-danqing flex-shrink-0" 
            />
          </div>

          <!-- 空状态 -->
          <div v-if="normalizedOptions.length === 0" class="px-3 py-4 text-center text-shuimo/50 text-sm">
            暂无选项
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>
