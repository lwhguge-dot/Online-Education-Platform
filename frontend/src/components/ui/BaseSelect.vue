<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
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
  },
  align: {
    type: String,
    default: 'left', // left, right
    validator: (v) => ['left', 'right'].includes(v)
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const isOpen = ref(false)
const selectRef = ref(null)
const triggerRef = ref(null)
const dropdownRef = ref(null)
const optionRefs = ref([])
const dropdownStyle = ref({})
const activeIndex = ref(-1)
const listboxId = `base-select-listbox-${Math.random().toString(36).slice(2, 9)}`

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

const getSelectedIndex = () => {
  return normalizedOptions.value.findIndex(opt => opt.value === props.modelValue)
}

const getOptionId = (idx) => `${listboxId}-option-${idx}`

const activeOptionId = computed(() => {
  if (activeIndex.value < 0) return undefined
  return getOptionId(activeIndex.value)
})

const syncActiveIndex = () => {
  const selectedIndex = getSelectedIndex()
  if (selectedIndex >= 0) {
    activeIndex.value = selectedIndex
    return
  }
  activeIndex.value = normalizedOptions.value.length > 0 ? 0 : -1
}

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
    width: `${rect.width}px`,
    maxHeight: `${maxHeight}px`,
    zIndex: 9999
  }

  if (props.align === 'right') {
    dropdownStyle.value.right = `${document.documentElement.clientWidth - rect.right}px`
    dropdownStyle.value.left = 'auto'
  } else {
    dropdownStyle.value.left = `${rect.left}px`
  }
}

const ensureActiveOptionVisible = () => {
  if (activeIndex.value < 0) return
  const el = optionRefs.value[activeIndex.value]
  el?.scrollIntoView?.({ block: 'nearest' })
}

const openDropdown = async () => {
  if (props.disabled || isOpen.value) return
  syncActiveIndex()
  isOpen.value = true
  await nextTick()
  updateDropdownPosition()
  ensureActiveOptionVisible()
}

const closeDropdown = () => {
  isOpen.value = false
}

const toggleDropdown = async () => {
  if (props.disabled) return
  if (isOpen.value) {
    closeDropdown()
    return
  }
  await openDropdown()
}

const selectOption = (option) => {
  emit('update:modelValue', option.value)
  emit('change', option.value)
  activeIndex.value = normalizedOptions.value.findIndex(opt => opt.value === option.value)
  closeDropdown()
  triggerRef.value?.focus()
}

const moveActiveIndex = (step) => {
  const total = normalizedOptions.value.length
  if (total === 0) return

  if (activeIndex.value < 0) {
    activeIndex.value = 0
  } else {
    activeIndex.value = (activeIndex.value + step + total) % total
  }
  ensureActiveOptionVisible()
}

const selectActiveOption = () => {
  if (activeIndex.value < 0) return
  const option = normalizedOptions.value[activeIndex.value]
  if (!option) return
  selectOption(option)
}

const setOptionRef = (el, idx) => {
  optionRefs.value[idx] = el
}

const handleTriggerKeydown = async (event) => {
  if (props.disabled) return

  // 键盘用户支持上下切换、回车选中、Esc关闭
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    if (!isOpen.value) {
      await openDropdown()
      return
    }
    moveActiveIndex(1)
    return
  }

  if (event.key === 'ArrowUp') {
    event.preventDefault()
    if (!isOpen.value) {
      await openDropdown()
      return
    }
    moveActiveIndex(-1)
    return
  }

  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    if (!isOpen.value) {
      await openDropdown()
      return
    }
    selectActiveOption()
    return
  }

  if (event.key === 'Home' && isOpen.value) {
    event.preventDefault()
    if (normalizedOptions.value.length > 0) {
      activeIndex.value = 0
      ensureActiveOptionVisible()
    }
    return
  }

  if (event.key === 'End' && isOpen.value) {
    event.preventDefault()
    if (normalizedOptions.value.length > 0) {
      activeIndex.value = normalizedOptions.value.length - 1
      ensureActiveOptionVisible()
    }
    return
  }

  if (event.key === 'Escape' && isOpen.value) {
    event.preventDefault()
    closeDropdown()
    return
  }

  if (event.key === 'Tab' && isOpen.value) {
    closeDropdown()
  }
}

const handleClickOutside = (e) => {
  if (selectRef.value && !selectRef.value.contains(e.target)) {
    if (dropdownRef.value && dropdownRef.value.contains(e.target)) {
      return
    }
    closeDropdown()
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

watch(() => props.modelValue, () => {
  syncActiveIndex()
})

watch(() => normalizedOptions.value.length, () => {
  optionRefs.value = []
  syncActiveIndex()
})
</script>

<template>
  <div ref="selectRef" class="relative inline-block w-full">
    <!-- 触发器 -->
    <button
      ref="triggerRef"
      type="button"
      @click="toggleDropdown"
      @keydown="handleTriggerKeydown"
      :disabled="disabled"
      :aria-label="hasValue ? `当前选择：${selectedLabel}` : placeholder"
      :aria-haspopup="'listbox'"
      :aria-expanded="isOpen ? 'true' : 'false'"
      :aria-controls="listboxId"
      :aria-activedescendant="activeOptionId"
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
          :id="listboxId"
          :style="dropdownStyle"
          role="listbox"
          class="py-1.5 bg-white rounded-xl border border-slate-200 shadow-xl shadow-slate-200/50 max-h-60 overflow-y-auto"
        >
          <div
            v-for="(option, index) in normalizedOptions"
            :key="option.value"
            :id="getOptionId(index)"
            :ref="(el) => setOptionRef(el, index)"
            role="option"
            :aria-selected="option.value === modelValue ? 'true' : 'false'"
            @mouseenter="activeIndex = index"
            @click="selectOption(option)"
            :class="[
              'flex items-center justify-between px-3 py-2 cursor-pointer transition-all duration-150',
              option.value === modelValue || (isOpen && index === activeIndex)
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
