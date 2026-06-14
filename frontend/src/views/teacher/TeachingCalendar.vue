<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { useConfirmStore } from '../../stores/confirm'
import { calendarAPI, courseAPI, chapterAPI } from '../../services/api'
import { logger } from '../../utils/logger'
import CalendarGrid from '../../components/teacher/CalendarGrid.vue'
import EventFormModal from '../../components/teacher/EventFormModal.vue'
import type { Course, Chapter } from '../../types/api'

interface CalendarEvent {
  id: number
  title: string
  eventType: string
  startTime: string
  endTime: string
  courseId?: number | null
  chapterId?: number | null
  description?: string
  reminderMinutes?: number
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

const authStore = useAuthStore()
const confirmStore = useConfirmStore()

const viewMode = ref('month')
const currentDate = ref(new Date())
const events = ref<CalendarEvent[]>([])
const loading = ref(false)
const showEventModal = ref(false)
const editingEvent = ref<CalendarEvent | null>(null)
const courses = ref<Course[]>([])
const chapters = ref<Chapter[]>([])

const calendarSlideDirection = ref('none')
const calendarKey = ref(0)

const eventForm = ref<EventForm>({
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

const currentYear = computed(() => currentDate.value.getFullYear())
const currentMonth = computed(() => currentDate.value.getMonth() + 1)

const calendarDays = computed(() => {
  const year = currentYear.value
  const month = currentMonth.value - 1
  const firstDay = new Date(year, month, 1)
  const lastDay = new Date(year, month + 1, 0)
  const startPadding = firstDay.getDay()
  const days: Array<{ date: Date; isCurrentMonth: boolean }> = []

  const prevMonth = new Date(year, month, 0)
  for (let i = startPadding - 1; i >= 0; i--) {
    days.push({ date: new Date(year, month - 1, prevMonth.getDate() - i), isCurrentMonth: false })
  }
  for (let i = 1; i <= lastDay.getDate(); i++) {
    days.push({ date: new Date(year, month, i), isCurrentMonth: true })
  }
  const remaining = 42 - days.length
  for (let i = 1; i <= remaining; i++) {
    days.push({ date: new Date(year, month + 1, i), isCurrentMonth: false })
  }
  return days
})

const weekDays = computed(() => {
  const date = new Date(currentDate.value)
  const day = date.getDay()
  const diff = date.getDate() - day
  const days: Date[] = []
  for (let i = 0; i < 7; i++) {
    const d = new Date(date)
    d.setDate(diff + i)
    days.push(d)
  }
  return days
})

const timeSlots = computed(() => {
  const slots: string[] = []
  for (let i = 8; i <= 22; i++) {
    slots.push(`${i.toString().padStart(2, '0')}:00`)
  }
  return slots
})

const formatDate = (date: Date) => {
  const y = date.getFullYear()
  const m = (date.getMonth() + 1).toString().padStart(2, '0')
  const d = date.getDate().toString().padStart(2, '0')
  return `${y}-${m}-${d}`
}

const navigate = (direction: number) => {
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
  setTimeout(() => { calendarSlideDirection.value = 'none' }, 350)
}

const goToToday = () => {
  calendarSlideDirection.value = 'none'
  calendarKey.value++
  currentDate.value = new Date()
}

const loadEvents = async () => {
  loading.value = true
  try {
    const teacherId = authStore.user?.id
    if (!teacherId) return
    let res
    if (viewMode.value === 'month') {
      res = await calendarAPI.getByMonth(teacherId, currentYear.value, currentMonth.value)
    } else if (viewMode.value === 'week') {
      const firstDay = weekDays.value[0]
      if (firstDay) {
        res = await calendarAPI.getByWeek(teacherId, formatDate(firstDay))
      }
    } else {
      res = await calendarAPI.getByDay(teacherId, formatDate(currentDate.value))
    }
    if (res?.code === 200) {
      events.value = (res.data || []) as CalendarEvent[]
    }
  } catch (e) {
    logger.error('加载日历事件失败', e)
    events.value = []
  } finally {
    loading.value = false
  }
}

const loadCourses = async () => {
  try {
    const teacherId = authStore.user?.id
    if (!teacherId) return
    const res = await courseAPI.getTeacherCourses(teacherId)
    if (res.data) {
      courses.value = res.data as Course[]
    }
  } catch (e) {
    logger.error('加载课程失败', e)
  }
}

const loadChapters = async (courseId: number | null) => {
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
    logger.error('加载章节失败', e)
  }
}

const openCreateModal = (date?: Date) => {
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

const openEditModal = (event: CalendarEvent) => {
  editingEvent.value = event
  eventForm.value = {
    title: (event.title as string) || '',
    eventType: (event.eventType as string) || 'CLASS',
    startTime: (event.startTime as string) || '',
    endTime: (event.endTime as string) || '',
    courseId: (event.courseId as number) ?? null,
    chapterId: (event.chapterId as number) ?? null,
    description: (event.description as string) || '',
    reminderMinutes: (event.reminderMinutes as number) || 30
  }
  if (event.courseId) {
    loadChapters(event.courseId)
  }
  showEventModal.value = true
}

const saveEvent = async () => {
  try {
    const data = { ...eventForm.value, teacherId: authStore.user?.id }
    if (editingEvent.value) {
      await calendarAPI.updateEvent(editingEvent.value.id, data)
    } else {
      await calendarAPI.createEvent(data)
    }
    showEventModal.value = false
    loadEvents()
  } catch (e) {
    logger.error('保存事件失败', e)
  }
}

const deleteEvent = async () => {
  if (!editingEvent.value) return
  const confirmed = await confirmStore.show({
    title: '删除事件',
    message: '确定要删除此事件吗？删除后无法恢复。',
    type: 'danger',
    confirmText: '删除',
    cancelText: '取消'
  })
  if (!confirmed) return
  try {
    await calendarAPI.deleteEvent(editingEvent.value.id, authStore.user?.id ?? null)
    showEventModal.value = false
    loadEvents()
  } catch (e) {
    logger.error('删除事件失败', e)
  }
}

const exportCalendar = () => {
  const url = calendarAPI.exportICal(authStore.user?.id ?? null, currentYear.value, currentMonth.value)
  window.open(url, '_blank')
}

const handleCourseChange = (courseId: number | null) => {
  loadChapters(courseId)
}

watch([viewMode, currentDate], () => {
  loadEvents()
})

onMounted(() => {
  loadEvents()
  loadCourses()
})
</script>

<template>
  <div class="space-y-6">
    <CalendarGrid
      :viewMode="viewMode"
      :currentYear="currentYear"
      :currentMonth="currentMonth"
      :currentDate="currentDate"
      :calendarDays="calendarDays"
      :weekDays="weekDays"
      :timeSlots="timeSlots"
      :events="events"
      :eventTypes="eventTypes"
      :loading="loading"
      :calendarSlideDirection="calendarSlideDirection"
      :calendarKey="calendarKey"
      @update:viewMode="viewMode = $event"
      @navigate="navigate"
      @goToToday="goToToday"
      @export="exportCalendar"
      @openCreate="openCreateModal($event)"
      @openEdit="openEditModal($event)"
    />

    <EventFormModal
      :visible="showEventModal"
      @update:visible="showEventModal = $event"
      :editingEvent="editingEvent"
      :eventForm="eventForm"
      @update:eventForm="eventForm = $event"
      :eventTypes="eventTypes"
      :courses="courses"
      :chapters="chapters"
      @save="saveEvent"
      @delete="deleteEvent"
      @courseChange="handleCourseChange"
    />
  </div>
</template>
