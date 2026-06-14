<script setup lang="ts">
import { ChevronLeft, ChevronRight, Plus, Download } from 'lucide-vue-next'

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

interface EventType {
  value: string
  label: string
  color: string
  dotColor: string
}

const props = defineProps<{
  viewMode: string
  currentYear: number
  currentMonth: number
  currentDate: Date
  calendarDays: Array<{ date: Date; isCurrentMonth: boolean }>
  weekDays: Date[]
  timeSlots: string[]
  events: CalendarEvent[]
  eventTypes: EventType[]
  loading: boolean
  calendarSlideDirection: string
  calendarKey: number
}>()

const emit = defineEmits<{
  (e: 'update:viewMode', value: string): void
  (e: 'navigate', direction: number): void
  (e: 'goToToday'): void
  (e: 'export'): void
  (e: 'openCreate', date?: Date): void
  (e: 'openEdit', event: CalendarEvent): void
}>()

const formatDate = (date: Date): string => {
  const y = date.getFullYear()
  const m = (date.getMonth() + 1).toString().padStart(2, '0')
  const d = date.getDate().toString().padStart(2, '0')
  return `${y}-${m}-${d}`
}

const isToday = (date: Date): boolean => {
  const today = new Date()
  return formatDate(date) === formatDate(today)
}

const getEventsForDate = (date: Date): CalendarEvent[] => {
  const dateStr = formatDate(date)
  return props.events.filter(e => e.startTime.split('T')[0] === dateStr)
}

const getEventsForTimeSlot = (date: Date, hour: number): CalendarEvent[] => {
  const dateStr = formatDate(date)
  return props.events.filter(e => {
    const eventDate = e.startTime.split('T')[0]
    const eventHour = parseInt(e.startTime.split('T')[1]?.split(':')[0] || '0')
    return eventDate === dateStr && eventHour === hour
  })
}

const getEventColor = (type: string): string => {
  return props.eventTypes.find(t => t.value === type)?.color || 'bg-shuimo'
}
</script>

<template>
  <div class="space-y-6">
    <!-- 头部工具栏 -->
    <div class="flex flex-wrap items-center justify-between gap-4">
      <div class="flex items-center gap-4">
        <div class="flex bg-white/60 rounded-xl p-1 shadow-sm">
          <button
            v-for="mode in [{ value: 'month', label: '月' }, { value: 'week', label: '周' }, { value: 'day', label: '日' }]"
            :key="mode.value"
            @click="emit('update:viewMode', mode.value)"
            :class="[
              'px-4 py-2 rounded-lg text-sm font-medium transition-colors transition-shadow',
              viewMode === mode.value
                ? 'bg-tianlv text-white shadow'
                : 'text-shuimo/70 hover:bg-white/50'
            ]"
          >
            {{ mode.label }}
          </button>
        </div>

        <div class="flex items-center gap-2">
          <button @click="emit('navigate', -1)" class="p-2 hover:bg-white/50 rounded-lg transition-colors">
            <ChevronLeft class="w-5 h-5 text-shuimo" />
          </button>
          <button @click="emit('goToToday')" class="px-3 py-1.5 text-sm font-medium text-tianlv hover:bg-tianlv/10 rounded-lg transition-colors">
            今天
          </button>
          <button @click="emit('navigate', 1)" class="p-2 hover:bg-white/50 rounded-lg transition-colors">
            <ChevronRight class="w-5 h-5 text-shuimo" />
          </button>
        </div>

        <h3 class="text-lg font-bold text-shuimo">
          {{ currentYear }}年{{ currentMonth }}月
          <span v-if="viewMode === 'day'" class="ml-1">{{ currentDate.getDate() }}日</span>
        </h3>
      </div>

      <div class="flex items-center gap-3">
        <button @click="emit('export')" class="flex items-center gap-2 px-4 py-2 text-shuimo/70 hover:bg-white/50 rounded-xl transition-colors">
          <Download class="w-4 h-4" />
          <span class="text-sm">导出</span>
        </button>
        <button @click="emit('openCreate')" class="flex items-center gap-2 px-4 py-2 bg-gradient-to-r from-tianlv to-qingsong text-white rounded-xl shadow-lg shadow-tianlv/20 hover:shadow-xl transition-shadow">
          <Plus class="w-4 h-4" />
          <span class="text-sm font-medium">新建事件</span>
        </button>
      </div>
    </div>

    <!-- 月视图 -->
    <div v-if="viewMode === 'month'" class="bg-white/60 backdrop-blur-sm rounded-2xl shadow-lg overflow-hidden">
      <div class="grid grid-cols-7 bg-slate-50/80">
        <div v-for="day in ['日', '一', '二', '三', '四', '五', '六']" :key="day" class="py-3 text-center text-sm font-medium text-shuimo/70">
          周{{ day }}
        </div>
      </div>

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
          @click="emit('openCreate', day.date)"
        >
          <div :class="[
            'w-8 h-8 flex items-center justify-center rounded-full text-sm mb-1 transition-colors transition-shadow',
            isToday(day.date)
              ? 'bg-gradient-to-br from-tianlv to-qingsong text-white font-bold shadow-lg shadow-tianlv/30 animate-today-pulse'
              : day.isCurrentMonth ? 'text-shuimo group-hover:bg-slate-100' : 'text-shuimo/30'
          ]">
            {{ day.date.getDate() }}
          </div>

          <div class="space-y-1">
            <div
              v-for="event in getEventsForDate(day.date).slice(0, 2)"
              :key="event.id"
              :class="['px-2 py-0.5 rounded text-xs text-white truncate cursor-pointer hover:opacity-90 transition-opacity', getEventColor(event.eventType)]"
              @click.stop="emit('openEdit', event)"
            >
              {{ event.title }}
            </div>
            <div v-if="getEventsForDate(day.date).length > 2" class="text-xs text-shuimo/50 pl-2">
              +{{ getEventsForDate(day.date).length - 2 }} 更多
            </div>
          </div>

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
        <div class="border-r border-slate-100">
          <div class="h-12 border-b border-slate-100"></div>
          <div v-for="time in timeSlots" :key="time" class="h-16 px-2 py-1 text-xs text-shuimo/50 border-b border-slate-100">
            {{ time }}
          </div>
        </div>

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
              @click="emit('openEdit', event)"
              @keydown.enter.prevent="emit('openEdit', event)"
              @keydown.space.prevent="emit('openEdit', event)"
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
            @click="emit('openCreate')"
            @keydown.enter.prevent="emit('openCreate')"
            @keydown.space.prevent="emit('openCreate')"
          >
            <div
              v-for="event in getEventsForTimeSlot(currentDate, parseInt(time))"
              :key="event.id"
              :class="['px-3 py-2 rounded-lg text-white mb-1 cursor-pointer', getEventColor(event.eventType)]"
              role="button"
              tabindex="0"
              :aria-label="`编辑事件：${event.title}`"
              @click.stop="emit('openEdit', event)"
              @keydown.enter.stop.prevent="emit('openEdit', event)"
              @keydown.space.stop.prevent="emit('openEdit', event)"
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
  </div>
</template>

<style scoped>
.animate-today-pulse {
  animation: today-pulse var(--motion-duration-medium) var(--motion-ease-standard) infinite;
  animation-iteration-count: var(--motion-loop-iterations-attention, 4);
  animation-fill-mode: both;
}

@keyframes today-pulse {
  0%, 100% {
    box-shadow: 0 4px 15px rgba(136, 173, 166, 0.3);
  }
  50% {
    box-shadow: 0 4px 20px rgba(136, 173, 166, 0.5), 0 0 0 4px rgba(136, 173, 166, 0.1);
  }
}

.animate-calendar-slide-left {
  animation: calendar-slide-left var(--motion-duration-medium) var(--motion-ease-standard) forwards;
}

@keyframes calendar-slide-left {
  from { opacity: 0; transform: translateX(40px); }
  to { opacity: 1; transform: translateX(0); }
}

.animate-calendar-slide-right {
  animation: calendar-slide-right var(--motion-duration-medium) var(--motion-ease-standard) forwards;
}

@keyframes calendar-slide-right {
  from { opacity: 0; transform: translateX(-40px); }
  to { opacity: 1; transform: translateX(0); }
}
</style>
