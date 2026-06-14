<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { X, Trash2 } from 'lucide-vue-next'
import BaseSelect from '../ui/BaseSelect.vue'

interface EventType {
  value: string
  label: string
  color: string
  dotColor: string
}

interface Course {
  id: number
  title: string
}

interface Chapter {
  id: number
  title: string
}

interface EventForm {
  title: string
  eventType: string
  startTime: string
  endTime: string
  courseId: number | null
  chapterId: number | null
  description: string
  reminderMinutes: number
}

const props = defineProps<{
  visible: boolean
  editingEvent: { id: number; [key: string]: unknown } | null
  eventForm: EventForm
  eventTypes: EventType[]
  courses: Course[]
  chapters: Chapter[]
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'update:eventForm', value: EventForm): void
  (e: 'save'): void
  (e: 'delete'): void
  (e: 'courseChange', courseId: number | null): void
}>()

const modalRef = ref<HTMLElement | null>(null)
const previousActiveElement = ref<HTMLElement | null>(null)

const getFocusableElements = (): HTMLElement[] => {
  if (!modalRef.value) return []
  const selectors = [
    'a[href]',
    'button:not([disabled])',
    'input:not([disabled])',
    'select:not([disabled])',
    'textarea:not([disabled])',
    '[tabindex]:not([tabindex="-1"])'
  ]
  return Array.from(modalRef.value.querySelectorAll(selectors.join(','))) as HTMLElement[]
}

const focusFirstElement = () => {
  const focusables = getFocusableElements()
  if (focusables.length > 0) {
    focusables[0].focus()
    return
  }
  modalRef.value?.focus()
}

const trapFocus = (event: KeyboardEvent) => {
  const focusables = getFocusableElements()
  if (focusables.length === 0) {
    event.preventDefault()
    modalRef.value?.focus()
    return
  }

  const first = focusables[0]!
  const last = focusables[focusables.length - 1]!
  const active = document.activeElement

  if (event.shiftKey && active === first) {
    event.preventDefault()
    last.focus()
    return
  }

  if (!event.shiftKey && active === last) {
    event.preventDefault()
    first.focus()
  }
}

const handleKeydown = (event: KeyboardEvent) => {
  if (!props.visible) return

  if (event.key === 'Escape') {
    event.preventDefault()
    emit('update:visible', false)
    return
  }

  if (event.key === 'Tab') {
    trapFocus(event)
  }
}

const updateField = <K extends keyof EventForm>(key: K, value: EventForm[K]) => {
  emit('update:eventForm', { ...props.eventForm, [key]: value })
}

const handleCourseChange = (courseId: number | null) => {
  updateField('courseId', courseId)
  emit('courseChange', courseId)
}

watch(() => props.visible, async (visible, oldVisible) => {
  if (visible && !oldVisible) {
    previousActiveElement.value = document.activeElement as HTMLElement
    await nextTick()
    focusFirstElement()
    return
  }

  if (!visible && oldVisible) {
    previousActiveElement.value?.focus?.()
    previousActiveElement.value = null
  }
})
</script>

<template>
  <Teleport to="body">
    <div
      v-if="visible"
      ref="modalRef"
      class="fixed inset-0 bg-shuimo/20 backdrop-blur-[2px] flex items-center justify-center z-50"
      role="dialog"
      aria-modal="true"
      aria-label="编辑事件弹窗"
      tabindex="-1"
      @click.self="emit('update:visible', false)"
      @keydown="handleKeydown"
    >
      <div class="bg-white rounded-2xl shadow-2xl w-full max-w-lg mx-4 overflow-hidden">
        <div class="px-6 py-4 border-b border-slate-100 flex items-center justify-between">
          <h3 class="text-lg font-bold text-shuimo">{{ editingEvent ? '编辑事件' : '新建事件' }}</h3>
          <button @click="emit('update:visible', false)" class="p-1 hover:bg-slate-100 rounded-lg transition-colors">
            <X class="w-5 h-5 text-shuimo/50" />
          </button>
        </div>

        <div class="p-6 space-y-4 max-h-[60vh] overflow-y-auto">
          <div>
            <label class="block text-sm font-medium text-shuimo/70 mb-1">事件标题</label>
            <input :value="eventForm.title" @input="updateField('title', ($event.target as HTMLInputElement).value)" type="text" class="w-full px-4 py-2 border border-slate-200 rounded-xl focus:ring-2 focus:ring-tianlv/20 focus:border-tianlv outline-none" placeholder="输入事件标题" />
          </div>

          <div>
            <label class="block text-sm font-medium text-shuimo/70 mb-1">事件类型</label>
            <BaseSelect
              :modelValue="eventForm.eventType"
              @update:modelValue="updateField('eventType', $event)"
              :options="eventTypes.map(t => ({ value: t.value, label: t.label }))"
              size="md"
            />
          </div>

          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-sm font-medium text-shuimo/70 mb-1">开始时间</label>
              <input :value="eventForm.startTime" @input="updateField('startTime', ($event.target as HTMLInputElement).value)" type="datetime-local" class="w-full px-4 py-2 border border-slate-200 rounded-xl focus:ring-2 focus:ring-tianlv/20 focus:border-tianlv outline-none" />
            </div>
            <div>
              <label class="block text-sm font-medium text-shuimo/70 mb-1">结束时间</label>
              <input :value="eventForm.endTime" @input="updateField('endTime', ($event.target as HTMLInputElement).value)" type="datetime-local" class="w-full px-4 py-2 border border-slate-200 rounded-xl focus:ring-2 focus:ring-tianlv/20 focus:border-tianlv outline-none" />
            </div>
          </div>

          <div v-if="eventForm.eventType === 'CLASS' || eventForm.eventType === 'HOMEWORK_DEADLINE'">
            <label class="block text-sm font-medium text-shuimo/70 mb-1">关联课程</label>
            <BaseSelect
              :modelValue="eventForm.courseId"
              @update:modelValue="handleCourseChange($event)"
              :options="[{ value: null, label: '不关联课程' }, ...courses.map(c => ({ value: c.id, label: c.title }))]"
              size="md"
            />
          </div>

          <div v-if="eventForm.courseId && chapters.length > 0">
            <label class="block text-sm font-medium text-shuimo/70 mb-1">关联章节</label>
            <BaseSelect
              :modelValue="eventForm.chapterId"
              @update:modelValue="updateField('chapterId', $event)"
              :options="[{ value: null, label: '不关联章节' }, ...chapters.map(ch => ({ value: ch.id, label: ch.title }))]"
              size="md"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-shuimo/70 mb-1">提前提醒</label>
            <BaseSelect
              :modelValue="eventForm.reminderMinutes"
              @update:modelValue="updateField('reminderMinutes', $event)"
              :options="[
                { value: 0, label: '不提醒' },
                { value: 15, label: '15分钟前' },
                { value: 30, label: '30分钟前' },
                { value: 60, label: '1小时前' },
                { value: 1440, label: '1天前' }
              ]"
              size="md"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-shuimo/70 mb-1">备注</label>
            <textarea :value="eventForm.description" @input="updateField('description', ($event.target as HTMLTextAreaElement).value)" rows="3" class="w-full px-4 py-2 border border-slate-200 rounded-xl focus:ring-2 focus:ring-tianlv/20 focus:border-tianlv outline-none resize-none" placeholder="添加备注..."></textarea>
          </div>
        </div>

        <div class="px-6 py-4 border-t border-slate-100 flex items-center justify-between">
          <button v-if="editingEvent" @click="emit('delete')" class="flex items-center gap-2 px-4 py-2 text-yanzhi hover:bg-yanzhi/10 rounded-xl transition-colors">
            <Trash2 class="w-4 h-4" />
            <span>删除</span>
          </button>
          <div v-else></div>

          <div class="flex items-center gap-3">
            <button @click="emit('update:visible', false)" class="px-4 py-2 text-shuimo/70 hover:bg-slate-100 rounded-xl transition-colors">
              取消
            </button>
            <button @click="emit('save')" class="px-6 py-2 bg-gradient-to-r from-tianlv to-qingsong text-white rounded-xl shadow-lg shadow-tianlv/20 hover:shadow-xl transition-shadow">
              保存
            </button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>
