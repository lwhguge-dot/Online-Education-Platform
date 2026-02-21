<script setup>
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { useConfirmStore } from '../../stores/confirm'
import { calendarAPI, courseAPI, chapterAPI } from '../../services/api'
import {
  ChevronLeft, ChevronRight, Plus, X, Download, Trash2
} from 'lucide-vue-next'
import BaseSelect from '../../components/ui/BaseSelect.vue'

const authStore = useAuthStore()
const confirmStore = useConfirmStore()

// 视图模式
const viewMode = ref('month') // 月/周/日
const currentDate = ref(new Date())
const events = ref([])
const loading = ref(false)
const showEventModal = ref(false)
const editingEvent = ref(null)
const courses = ref([])
const chapters = ref([])
const eventModalRef = ref(null)
const previousActiveElement = ref(null)

// 月份切换动画方向
const calendarSlideDirection = ref('none') // 'left' / 'right' / 'none'
const calendarKey = ref(0) // 用于触发动画

// 事件表单
const eventForm = ref({
  title: '',
  eventType: 'CLASS',
  startTime: '',
  endTime: '',
  courseId: null,
  chapterId: null,
  description: '',
  reminderMinutes: 30
})

const eventTypes = [
  { value: 'CLASS', label: '课程', color: 'bg-tianlv', dotColor: 'bg-tianlv' },
  { value: 'HOMEWORK_DEADLINE', label: '作业截止', color: 'bg-yanzhi', dotColor: 'bg-yanzhi' },
  { value: 'EXAM', label: '考试', color: 'bg-qinghua', dotColor: 'bg-qinghua' },
  { value: 'MEETING', label: '会议', color: 'bg-chenpi', dotColor: 'bg-chenpi' },
  { value: 'OTHER', label: '其他', color: 'bg-shuimo', dotColor: 'bg-shuimo' }
]

// 计算当前显示的年月
const currentYear = computed(() => currentDate.value.getFullYear())
const currentMonth = computed(() => currentDate.value.getMonth() + 1)

// 月视图日历数据
const calendarDays = computed(() => {
  const year = currentYear.value
  const month = currentMonth.value - 1
  const firstDay = new Date(year, month, 1)
  const lastDay = new Date(year, month + 1, 0)
  const startPadding = firstDay.getDay()
  const days = []
  
  // 上月填充
  const prevMonth = new Date(year, month, 0)
  for (let i = startPadding - 1; i >= 0; i--) {
    days.push({
      date: new Date(year, month - 1, prevMonth.getDate() - i),
      isCurrentMonth: false
    })
  }
  
  // 当月
  for (let i = 1; i <= lastDay.getDate(); i++) {
    days.push({
      date: new Date(year, month, i),
      isCurrentMonth: true
    })
  }
  
  // 下月填充
  const remaining = 42 - days.length
  for (let i = 1; i <= remaining; i++) {
    days.push({
      date: new Date(year, month + 1, i),
      isCurrentMonth: false
    })
  }
  
  return days
})

// 周视图数据
const weekDays = computed(() => {
  const date = new Date(currentDate.value)
  const day = date.getDay()
  const diff = date.getDate() - day
  const days = []
  
  for (let i = 0; i < 7; i++) {
    const d = new Date(date)
    d.setDate(diff + i)
    days.push(d)
  }
  return days
})

// 时间段（周/日视图用）
const timeSlots = computed(() => {
  const slots = []
  for (let i = 8; i <= 22; i++) {
    slots.push(`${i.toString().padStart(2, '0')}:00`)
  }
  return slots
})

// 获取某天的事件
const getEventsForDate = (date) => {
  const dateStr = formatDate(date)
  return events.value.filter(e => {
    const eventDate = e.startTime.split('T')[0]
    return eventDate === dateStr
  })
}

// 获取某时间段的事件
const getEventsForTimeSlot = (date, hour) => {
  const dateStr = formatDate(date)
  return events.value.filter(e => {
    const eventDate = e.startTime.split('T')[0]
    const eventHour = parseInt(e.startTime.split('T')[1]?.split(':')[0] || '0')
    return eventDate === dateStr && eventHour === hour
  })
}

const formatDate = (date) => {
  const y = date.getFullYear()
  const m = (date.getMonth() + 1).toString().padStart(2, '0')
  const d = date.getDate().toString().padStart(2, '0')
  return `${y}-${m}-${d}`
}

const isToday = (date) => {
  const today = new Date()
  return formatDate(date) === formatDate(today)
}

// 导航
const navigate = (direction) => {
  // 设置滑动方向
  calendarSlideDirection.value = direction > 0 ? 'left' : 'right'
  calendarKey.value++

  const date = new Date(currentDate.value)
  if (viewMode.value === 'month') {
    date.setMonth(date.getMonth() + direction)
  } else if (viewMode.value === 'week') {
    date.setDate(date.getDate() + direction * 7)
  } else {
    date.setDate(date.getDate() + direction)
  }
  currentDate.value = date

  // 重置动画方向
  setTimeout(() => {
    calendarSlideDirection.value = 'none'
  }, 350)
}

const goToToday = () => {
  calendarSlideDirection.value = 'none'
  calendarKey.value++
  currentDate.value = new Date()
}

// 加载事件
const loadEvents = async () => {
  loading.value = true
  try {
    const teacherId = authStore.user?.id
    if (!teacherId) return
    
    let res
    if (viewMode.value === 'month') {
      res = await calendarAPI.getByMonth(teacherId, currentYear.value, currentMonth.value)
    } else if (viewMode.value === 'week') {
      res = await calendarAPI.getByWeek(teacherId, formatDate(weekDays.value[0]))
    } else {
      res = await calendarAPI.getByDay(teacherId, formatDate(currentDate.value))
    }
    
    if (res.code === 200) {
      events.value = res.data || []
    }
  } catch (e) {
    console.error('加载日历事件失败', e)
    events.value = []
  } finally {
    loading.value = false
  }
}

// 加载课程
const loadCourses = async () => {
  try {
    const res = await courseAPI.getTeacherCourses(authStore.user?.id)
    if (res.data) {
      courses.value = res.data
    }
  } catch (e) {
    console.error('加载课程失败', e)
  }
}

// 加载章节
const loadChapters = async (courseId) => {
  if (!courseId) {
    chapters.value = []
    return
  }
  try {
    const res = await chapterAPI.getByCourse(courseId)
    if (res.data) {
      chapters.value = res.data
    }
  } catch (e) {
    console.error('加载章节失败', e)
  }
}

// 打开新建事件弹窗
const openCreateModal = (date = null) => {
  editingEvent.value = null
  const now = new Date()
  const targetDate = date || currentDate.value
  const dateStr = formatDate(targetDate)
  const hour = now.getHours().toString().padStart(2, '0')
  
  eventForm.value = {
    title: '',
    eventType: 'CLASS',
    startTime: `${dateStr}T${hour}:00`,
    endTime: `${dateStr}T${(parseInt(hour) + 1).toString().padStart(2, '0')}:00`,
    courseId: null,
    chapterId: null,
    description: '',
    reminderMinutes: 30
  }
  showEventModal.value = true
}

// 打开编辑事件弹窗
const openEditModal = (event) => {
  editingEvent.value = event
  eventForm.value = { ...event }
  if (event.courseId) {
    loadChapters(event.courseId)
  }
  showEventModal.value = true
}

// 保存事件
const saveEvent = async () => {
  try {
    const data = {
      ...eventForm.value,
      teacherId: authStore.user?.id
    }
    
    if (editingEvent.value) {
      await calendarAPI.updateEvent(editingEvent.value.id, data)
    } else {
      await calendarAPI.createEvent(data)
    }
    
    showEventModal.value = false
    loadEvents()
  } catch (e) {
    console.error('保存事件失败', e)
  }
}

// 删除事件
const deleteEvent = async (event) => {
  const confirmed = await confirmStore.show({
    title: '删除事件',
    message: '确定要删除此事件吗？删除后无法恢复。',
    type: 'danger',
    confirmText: '删除',
    cancelText: '取消'
  })
  if (!confirmed) return
  try {
    await calendarAPI.deleteEvent(event.id, authStore.user?.id)
    loadEvents()
  } catch (e) {
    console.error('删除事件失败', e)
  }
}

// 导出iCal
const exportCalendar = () => {
  const url = calendarAPI.exportICal(authStore.user?.id, currentYear.value, currentMonth.value)
  window.open(url, '_blank')
}

const getFocusableElements = () => {
  if (!eventModalRef.value) return []
  const selectors = [
    'a[href]',
    'button:not([disabled])',
    'input:not([disabled])',
    'select:not([disabled])',
    'textarea:not([disabled])',
    '[tabindex]:not([tabindex="-1"])'
  ]
  return Array.from(eventModalRef.value.querySelectorAll(selectors.join(',')))
}

const focusFirstElement = () => {
  const focusables = getFocusableElements()
  if (focusables.length > 0) {
    focusables[0].focus()
    return
  }
  eventModalRef.value?.focus()
}

const trapFocus = (event) => {
  const focusables = getFocusableElements()
  if (focusables.length === 0) {
    event.preventDefault()
    eventModalRef.value?.focus()
    return
  }

  const first = focusables[0]
  const last = focusables[focusables.length - 1]
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

// 统一处理事件弹窗键盘行为，保证 Esc 与 Tab 都可预测
const handleEventModalKeydown = (event) => {
  if (!showEventModal.value) return

  if (event.key === 'Escape') {
    event.preventDefault()
    showEventModal.value = false
    return
  }

  if (event.key === 'Tab') {
    trapFocus(event)
  }
}

// 获取事件类型颜色
const getEventColor = (type) => {
  return eventTypes.find(t => t.value === type)?.color || 'bg-shuimo'
}

// 监听视图模式和日期变化
watch([viewMode, currentDate], () => {
  loadEvents()
})

watch(() => eventForm.value.courseId, (newVal) => {
  loadChapters(newVal)
})

watch(showEventModal, async (visible, oldVisible) => {
  if (visible && !oldVisible) {
    // 记录打开前焦点并在弹窗打开后将焦点引导至弹窗内部
    previousActiveElement.value = document.activeElement
    await nextTick()
    focusFirstElement()
    return
  }

  if (!visible && oldVisible) {
    previousActiveElement.value?.focus?.()
    previousActiveElement.value = null
  }
})

onMounted(() => {
  loadEvents()
  loadCourses()
})
</script>

<template>
  <div class="space-y-6">
    <!-- 头部工具栏 -->
    <div class="flex flex-wrap items-center justify-between gap-4">
      <div class="flex items-center gap-4">
        <!-- 视图切换 -->
        <div class="flex bg-white/60 rounded-xl p-1 shadow-sm">
          <button
            v-for="mode in [{ value: 'month', label: '月' }, { value: 'week', label: '周' }, { value: 'day', label: '日' }]"
            :key="mode.value"
            @click="viewMode = mode.value"
            :class="[
              'px-4 py-2 rounded-lg text-sm font-medium transition-all',
              viewMode === mode.value
                ? 'bg-tianlv text-white shadow'
                : 'text-shuimo/70 hover:bg-white/50'
            ]"
          >
            {{ mode.label }}
          </button>
        </div>
        
        <!-- 导航 -->
        <div class="flex items-center gap-2">
          <button @click="navigate(-1)" class="p-2 hover:bg-white/50 rounded-lg transition-colors">
            <ChevronLeft class="w-5 h-5 text-shuimo" />
          </button>
          <button @click="goToToday" class="px-3 py-1.5 text-sm font-medium text-tianlv hover:bg-tianlv/10 rounded-lg transition-colors">
            今天
          </button>
          <button @click="navigate(1)" class="p-2 hover:bg-white/50 rounded-lg transition-colors">
            <ChevronRight class="w-5 h-5 text-shuimo" />
          </button>
        </div>
        
        <!-- 当前日期显示 -->
        <h3 class="text-lg font-bold text-shuimo">
          {{ currentYear }}年{{ currentMonth }}月
          <span v-if="viewMode === 'day'" class="ml-1">{{ currentDate.getDate() }}日</span>
        </h3>
      </div>
      
      <div class="flex items-center gap-3">
        <button @click="exportCalendar" class="flex items-center gap-2 px-4 py-2 text-shuimo/70 hover:bg-white/50 rounded-xl transition-colors">
          <Download class="w-4 h-4" />
          <span class="text-sm">导出</span>
        </button>
        <button @click="openCreateModal()" class="flex items-center gap-2 px-4 py-2 bg-gradient-to-r from-tianlv to-qingsong text-white rounded-xl shadow-lg shadow-tianlv/20 hover:shadow-xl transition-all">
          <Plus class="w-4 h-4" />
          <span class="text-sm font-medium">新建事件</span>
        </button>
      </div>
    </div>

    <!-- 月视图 -->
    <div v-if="viewMode === 'month'" class="bg-white/60 backdrop-blur-sm rounded-2xl shadow-lg overflow-hidden">
      <!-- 星期头 -->
      <div class="grid grid-cols-7 bg-slate-50/80">
        <div v-for="day in ['日', '一', '二', '三', '四', '五', '六']" :key="day" class="py-3 text-center text-sm font-medium text-shuimo/70">
          周{{ day }}
        </div>
      </div>

      <!-- 日期格子 - 添加动画 -->
      <div
        :key="calendarKey"
        :class="[
          'grid grid-cols-7',
          calendarSlideDirection === 'left' ? 'animate-calendar-slide-left' : '',
          calendarSlideDirection === 'right' ? 'animate-calendar-slide-right' : ''
        ]"
      >
        <div
          v-for="(day, index) in calendarDays"
          :key="index"
          :class="[
            'min-h-[100px] p-2 border-t border-l border-slate-100 cursor-pointer hover:bg-slate-50/50 transition-colors relative group',
            !day.isCurrentMonth && 'bg-slate-50/30',
            isToday(day.date) && 'bg-tianlv/5'
          ]"
          @click="openCreateModal(day.date)"
        >
          <!-- 日期数字 -->
          <div :class="[
            'w-8 h-8 flex items-center justify-center rounded-full text-sm mb-1 transition-all',
            isToday(day.date)
              ? 'bg-gradient-to-br from-tianlv to-qingsong text-white font-bold shadow-lg shadow-tianlv/30 animate-today-pulse'
              : day.isCurrentMonth ? 'text-shuimo group-hover:bg-slate-100' : 'text-shuimo/30'
          ]">
            {{ day.date.getDate() }}
          </div>

          <!-- 事件列表 -->
          <div class="space-y-1">
            <div
              v-for="event in getEventsForDate(day.date).slice(0, 2)"
              :key="event.id"
              :class="['px-2 py-0.5 rounded text-xs text-white truncate cursor-pointer hover:opacity-90 transition-opacity', getEventColor(event.eventType)]"
              @click.stop="openEditModal(event)"
            >
              {{ event.title }}
            </div>
            <div v-if="getEventsForDate(day.date).length > 2" class="text-xs text-shuimo/50 pl-2">
              +{{ getEventsForDate(day.date).length - 2 }} 更多
            </div>
          </div>

          <!-- 事件点指示器（当有事件但未全部显示时） -->
          <div
            v-if="getEventsForDate(day.date).length > 0"
            class="absolute bottom-1 left-1/2 -translate-x-1/2 flex gap-0.5"
          >
            <span
              v-for="(event, idx) in getEventsForDate(day.date).slice(0, 3)"
              :key="idx"
              :class="['w-1.5 h-1.5 rounded-full', eventTypes.find(t => t.value === event.eventType)?.dotColor || 'bg-shuimo']"
            ></span>
          </div>
        </div>
      </div>
    </div>

    <!-- 周视图 -->
    <div v-if="viewMode === 'week'" class="bg-white/60 backdrop-blur-sm rounded-2xl shadow-lg overflow-hidden">
      <div class="grid grid-cols-8">
        <!-- 时间列 -->
        <div class="border-r border-slate-100">
          <div class="h-12 border-b border-slate-100"></div>
          <div v-for="time in timeSlots" :key="time" class="h-16 px-2 py-1 text-xs text-shuimo/50 border-b border-slate-100">
            {{ time }}
          </div>
        </div>
        
        <!-- 每天一列 -->
        <div v-for="day in weekDays" :key="day.toISOString()" class="border-r border-slate-100 last:border-r-0">
          <div :class="['h-12 flex flex-col items-center justify-center border-b border-slate-100', isToday(day) && 'bg-tianlv/10']">
            <span class="text-xs text-shuimo/50">周{{ ['日', '一', '二', '三', '四', '五', '六'][day.getDay()] }}</span>
            <span :class="['text-sm font-medium', isToday(day) ? 'text-tianlv' : 'text-shuimo']">{{ day.getDate() }}</span>
          </div>
          
          <div v-for="(time, idx) in timeSlots" :key="time" class="h-16 border-b border-slate-100 p-1 relative">
            <div
              v-for="event in getEventsForTimeSlot(day, 8 + idx)"
              :key="event.id"
              :class="['absolute inset-x-1 px-1 py-0.5 rounded text-xs text-white truncate cursor-pointer', getEventColor(event.eventType)]"
              role="button"
              tabindex="0"
              :aria-label="`编辑事件：${event.title}`"
              @click="openEditModal(event)"
              @keydown.enter.prevent="openEditModal(event)"
              @keydown.space.prevent="openEditModal(event)"
            >
              {{ event.title }}
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 日视图 -->
    <div v-if="viewMode === 'day'" class="bg-white/60 backdrop-blur-sm rounded-2xl shadow-lg overflow-hidden">
      <div class="divide-y divide-slate-100">
        <div v-for="time in timeSlots" :key="time" class="flex">
          <div class="w-20 py-4 px-3 text-sm text-shuimo/50 flex-shrink-0">
            {{ time }}
          </div>
          <div
            class="flex-1 py-2 px-3 min-h-[60px] border-l border-slate-100 hover:bg-slate-50/50 cursor-pointer"
            role="button"
            tabindex="0"
            :aria-label="`在 ${time} 创建新事件`"
            @click="openCreateModal()"
            @keydown.enter.prevent="openCreateModal()"
            @keydown.space.prevent="openCreateModal()"
          >
            <div
              v-for="event in getEventsForTimeSlot(currentDate, parseInt(time))"
              :key="event.id"
              :class="['px-3 py-2 rounded-lg text-white mb-1 cursor-pointer', getEventColor(event.eventType)]"
              role="button"
              tabindex="0"
              :aria-label="`编辑事件：${event.title}`"
              @click.stop="openEditModal(event)"
              @keydown.enter.stop.prevent="openEditModal(event)"
              @keydown.space.stop.prevent="openEditModal(event)"
            >
              <div class="font-medium">{{ event.title }}</div>
              <div class="text-xs opacity-80">{{ event.startTime?.split('T')[1]?.slice(0, 5) }} - {{ event.endTime?.split('T')[1]?.slice(0, 5) }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 事件类型图例 -->
    <div class="flex flex-wrap items-center gap-4 text-sm">
      <span class="text-shuimo/50">事件类型：</span>
      <div v-for="type in eventTypes" :key="type.value" class="flex items-center gap-1.5">
        <span :class="['w-3 h-3 rounded', type.color]"></span>
        <span class="text-shuimo/70">{{ type.label }}</span>
      </div>
    </div>

    <!-- 事件编辑弹窗 -->
    <div
      v-if="showEventModal"
      ref="eventModalRef"
      class="fixed inset-0 bg-shuimo/20 backdrop-blur-[2px] flex items-center justify-center z-50"
      role="dialog"
      aria-modal="true"
      aria-label="编辑事件弹窗"
      tabindex="-1"
      @click.self="showEventModal = false"
      @keydown="handleEventModalKeydown"
    >
      <div class="bg-white rounded-2xl shadow-2xl w-full max-w-lg mx-4 overflow-hidden">
        <div class="px-6 py-4 border-b border-slate-100 flex items-center justify-between">
          <h3 class="text-lg font-bold text-shuimo">{{ editingEvent ? '编辑事件' : '新建事件' }}</h3>
          <button @click="showEventModal = false" class="p-1 hover:bg-slate-100 rounded-lg transition-colors">
            <X class="w-5 h-5 text-shuimo/50" />
          </button>
        </div>
        
        <div class="p-6 space-y-4 max-h-[60vh] overflow-y-auto">
          <div>
            <label class="block text-sm font-medium text-shuimo/70 mb-1">事件标题</label>
            <input v-model="eventForm.title" type="text" class="w-full px-4 py-2 border border-slate-200 rounded-xl focus:ring-2 focus:ring-tianlv/20 focus:border-tianlv outline-none" placeholder="输入事件标题" />
          </div>
          
          <div>
            <label class="block text-sm font-medium text-shuimo/70 mb-1">事件类型</label>
            <BaseSelect 
              v-model="eventForm.eventType" 
              :options="eventTypes.map(t => ({ value: t.value, label: t.label }))"
              size="md"
            />
          </div>
          
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-sm font-medium text-shuimo/70 mb-1">开始时间</label>
              <input v-model="eventForm.startTime" type="datetime-local" class="w-full px-4 py-2 border border-slate-200 rounded-xl focus:ring-2 focus:ring-tianlv/20 focus:border-tianlv outline-none" />
            </div>
            <div>
              <label class="block text-sm font-medium text-shuimo/70 mb-1">结束时间</label>
              <input v-model="eventForm.endTime" type="datetime-local" class="w-full px-4 py-2 border border-slate-200 rounded-xl focus:ring-2 focus:ring-tianlv/20 focus:border-tianlv outline-none" />
            </div>
          </div>
          
          <div v-if="eventForm.eventType === 'CLASS' || eventForm.eventType === 'HOMEWORK_DEADLINE'">
            <label class="block text-sm font-medium text-shuimo/70 mb-1">关联课程</label>
            <BaseSelect 
              v-model="eventForm.courseId" 
              :options="[{ value: null, label: '不关联课程' }, ...courses.map(c => ({ value: c.id, label: c.title }))]"
              size="md"
            />
          </div>
          
          <div v-if="eventForm.courseId && chapters.length > 0">
            <label class="block text-sm font-medium text-shuimo/70 mb-1">关联章节</label>
            <BaseSelect 
              v-model="eventForm.chapterId" 
              :options="[{ value: null, label: '不关联章节' }, ...chapters.map(ch => ({ value: ch.id, label: ch.title }))]"
              size="md"
            />
          </div>
          
          <div>
            <label class="block text-sm font-medium text-shuimo/70 mb-1">提前提醒</label>
            <BaseSelect 
              v-model="eventForm.reminderMinutes" 
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
            <textarea v-model="eventForm.description" rows="3" class="w-full px-4 py-2 border border-slate-200 rounded-xl focus:ring-2 focus:ring-tianlv/20 focus:border-tianlv outline-none resize-none" placeholder="添加备注..."></textarea>
          </div>
        </div>
        
        <div class="px-6 py-4 border-t border-slate-100 flex items-center justify-between">
          <button v-if="editingEvent" @click="deleteEvent(editingEvent)" class="flex items-center gap-2 px-4 py-2 text-yanzhi hover:bg-yanzhi/10 rounded-xl transition-colors">
            <Trash2 class="w-4 h-4" />
            <span>删除</span>
          </button>
          <div v-else></div>
          
          <div class="flex items-center gap-3">
            <button @click="showEventModal = false" class="px-4 py-2 text-shuimo/70 hover:bg-slate-100 rounded-xl transition-colors">
              取消
            </button>
            <button @click="saveEvent" class="px-6 py-2 bg-gradient-to-r from-tianlv to-qingsong text-white rounded-xl shadow-lg shadow-tianlv/20 hover:shadow-xl transition-all">
              保存
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 今日日期脉冲动画 */
.animate-today-pulse {
  /* P1 第二批：日历反馈时长压缩，减少持续动画负担 */
  animation: today-pulse var(--motion-duration-medium) var(--motion-ease-standard) infinite;
}

@keyframes today-pulse {
  0%, 100% {
    box-shadow: 0 4px 15px rgba(136, 173, 166, 0.3);
  }
  50% {
    box-shadow: 0 4px 20px rgba(136, 173, 166, 0.5), 0 0 0 4px rgba(136, 173, 166, 0.1);
  }
}

/* 日历月份切换动画 - 向左（下一月） */
.animate-calendar-slide-left {
  animation: calendar-slide-left var(--motion-duration-medium) var(--motion-ease-standard) forwards;
}

@keyframes calendar-slide-left {
  from {
    opacity: 0;
    transform: translateX(40px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

/* 日历月份切换动画 - 向右（上一月） */
.animate-calendar-slide-right {
  animation: calendar-slide-right var(--motion-duration-medium) var(--motion-ease-standard) forwards;
}

@keyframes calendar-slide-right {
  from {
    opacity: 0;
    transform: translateX(-40px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}
</style>

