<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { Calendar, ChevronLeft, ChevronRight } from 'lucide-vue-next'

const props = defineProps<{
  modelValue: string
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const showPicker = ref(false)
const currentYear = ref(new Date().getFullYear())
const currentMonth = ref(new Date().getMonth())
const pickerView = ref<'day' | 'month' | 'year'>('day')
const yearRangeStart = ref(Math.floor(new Date().getFullYear() / 10) * 10)
const pickerRef = ref<HTMLElement | null>(null)
const pickerStyle = ref<Record<string, string>>({})

const weekDays = ['一', '二', '三', '四', '五', '六', '日']
const months = ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月', '11月', '12月']
const monthsShort = ['一月', '二月', '三月', '四月', '五月', '六月', '七月', '八月', '九月', '十月', '十一月', '十二月']

const yearList = computed(() => Array.from({ length: 12 }, (_, i) => yearRangeStart.value + i))
const daysInMonth = computed(() => new Date(currentYear.value, currentMonth.value + 1, 0).getDate())
const firstDayOfMonth = computed(() => { const d = new Date(currentYear.value, currentMonth.value, 1).getDay(); return d === 0 ? 6 : d - 1 })

const calendarDays = computed(() => {
  const days: Array<{ day: number; current: boolean; prev?: boolean; next?: boolean }> = []
  const prevMonthDays = new Date(currentYear.value, currentMonth.value, 0).getDate()
  for (let i = firstDayOfMonth.value - 1; i >= 0; i--) days.push({ day: prevMonthDays - i, current: false, prev: true })
  for (let i = 1; i <= daysInMonth.value; i++) days.push({ day: i, current: true })
  const remaining = 42 - days.length
  for (let i = 1; i <= remaining; i++) days.push({ day: i, current: false, next: true })
  return days
})

const selectedDate = computed(() => {
  if (!props.modelValue) return null
  const parts = props.modelValue.split('-').map(Number)
  const y = parts[0]; const m = parts[1]; const d = parts[2]
  if (y == null || m == null || d == null) return null
  return { year: y, month: m - 1, day: d }
})

const isSelectedDay = (dayObj: { day: number; current: boolean }) => {
  if (!selectedDate.value || !dayObj.current) return false
  return selectedDate.value.year === currentYear.value && selectedDate.value.month === currentMonth.value && selectedDate.value.day === dayObj.day
}

const isToday = (dayObj: { day: number; current: boolean }) => {
  if (!dayObj.current) return false
  const t = new Date()
  return t.getFullYear() === currentYear.value && t.getMonth() === currentMonth.value && t.getDate() === dayObj.day
}

const selectDate = (dayObj: { day: number; current: boolean }) => {
  if (!dayObj.current) return
  const m = String(currentMonth.value + 1).padStart(2, '0')
  const d = String(dayObj.day).padStart(2, '0')
  emit('update:modelValue', `${currentYear.value}-${m}-${d}`)
  showPicker.value = false
}

const prevMonth = () => { if (currentMonth.value === 0) { currentMonth.value = 11; currentYear.value-- } else { currentMonth.value-- } }
const nextMonth = () => { if (currentMonth.value === 11) { currentMonth.value = 0; currentYear.value++ } else { currentMonth.value++ } }
const prevYearRange = () => { yearRangeStart.value -= 12 }
const nextYearRange = () => { yearRangeStart.value += 12 }
const selectYear = (year: number) => { currentYear.value = year; pickerView.value = 'month' }
const selectMonth = (idx: number) => { currentMonth.value = idx; pickerView.value = 'day' }
const showYearView = () => { yearRangeStart.value = Math.floor(currentYear.value / 12) * 12; pickerView.value = 'year' }
const showMonthView = () => { pickerView.value = 'month' }

const updatePosition = () => {
  const el = document.getElementById('profile-birthday-picker')
  if (!el) return
  const rect = el.getBoundingClientRect()
  const vh = window.innerHeight
  let top = rect.bottom + 8
  if (rect.bottom + 380 > vh) top = rect.top - 388
  pickerStyle.value = { position: 'fixed', top: `${top}px`, left: `${rect.left}px`, zIndex: '9999' }
}

const openPicker = async () => {
  if (props.disabled) return
  if (props.modelValue) {
    const parts = props.modelValue.split('-').map(Number)
    const y = parts[0]; const m = parts[1]
    if (y != null && m != null) {
      currentYear.value = y; currentMonth.value = m - 1
      yearRangeStart.value = Math.floor(y / 12) * 12
    }
  }
  pickerView.value = 'day'
  showPicker.value = true
  await nextTick()
  updatePosition()
}

const formatDisplay = (dateStr: string) => {
  if (!dateStr) return '未设置'
  const parts = dateStr.split('-')
  const y = parts[0]; const m = parts[1]; const d = parts[2]
  return `${y}年${parseInt(m ?? '0')}月${parseInt(d ?? '0')}日`
}

const handleOutside = (e: MouseEvent) => {
  const trigger = document.getElementById('profile-birthday-picker')
  if (trigger && !trigger.contains(e.target as Node) && pickerRef.value && !pickerRef.value.contains(e.target as Node)) {
    showPicker.value = false
  }
}

const handleScroll = () => { if (showPicker.value) updatePosition() }

onMounted(() => { document.addEventListener('click', handleOutside); window.addEventListener('scroll', handleScroll, true); window.addEventListener('resize', handleScroll) })
onUnmounted(() => { document.removeEventListener('click', handleOutside); window.removeEventListener('scroll', handleScroll, true); window.removeEventListener('resize', handleScroll) })
</script>

<template>
  <div class="relative z-20" data-date-picker>
    <span id="label-birthday" class="text-xs font-bold text-shuimo/60">出生年月日</span>
    <div
      id="profile-birthday-picker"
      aria-labelledby="label-birthday"
      aria-haspopup="dialog"
      :aria-expanded="showPicker ? 'true' : 'false'"
      role="button"
      tabindex="0"
      class="w-full flex items-center justify-between rounded-xl text-sm transition-[background-color,border-color,color,box-shadow,padding] duration-300"
      :class="disabled ? 'text-shuimo py-2.5' : 'bg-slate-50/80 border border-slate-200/50 cursor-pointer hover:border-qinghua/30 hover:bg-white px-4 py-2.5'"
      @click.stop="openPicker"
    >
      <span :class="modelValue ? 'text-shuimo' : 'text-shuimo/40'">{{ formatDisplay(modelValue) }}</span>
      <Calendar v-if="!disabled" class="w-4 h-4 text-shuimo/40" />
    </div>

    <Teleport to="body">
      <Transition name="dropdown">
        <div
          v-if="showPicker"
          ref="pickerRef"
          :style="pickerStyle"
          class="w-72 bg-white/95 backdrop-blur-xl rounded-2xl shadow-2xl shadow-black/10 border border-white/50 overflow-visible"
          @click.stop
          @keydown.esc.stop.prevent="showPicker = false"
        >
          <div class="p-4">
            <div class="flex items-center justify-between mb-4">
              <button class="p-2 rounded-xl hover:bg-slate-100 transition-colors"
                @click.stop="pickerView === 'year' ? prevYearRange() : (pickerView === 'month' ? (currentYear--, yearRangeStart = Math.floor(currentYear / 12) * 12) : prevMonth())">
                <ChevronLeft class="w-4 h-4 text-shuimo" />
              </button>
              <div class="flex items-center gap-1">
                <button class="px-2 py-1 rounded-lg hover:bg-qinghua/10 transition-colors font-bold text-shuimo" @click.stop="showYearView">
                  {{ pickerView === 'year' ? `${yearRangeStart}-${yearRangeStart + 11}` : currentYear + '年' }}
                </button>
                <button v-if="pickerView === 'day'" class="px-2 py-1 rounded-lg hover:bg-qinghua/10 transition-colors font-bold text-shuimo" @click.stop="showMonthView">
                  {{ months[currentMonth] }}
                </button>
              </div>
              <button class="p-2 rounded-xl hover:bg-slate-100 transition-colors"
                @click.stop="pickerView === 'year' ? nextYearRange() : (pickerView === 'month' ? (currentYear++, yearRangeStart = Math.floor(currentYear / 12) * 12) : nextMonth())">
                <ChevronRight class="w-4 h-4 text-shuimo" />
              </button>
            </div>

            <Transition name="fade" mode="out-in">
              <div v-if="pickerView === 'year'" key="year" class="grid grid-cols-3 gap-2">
                <button v-for="year in yearList" :key="year"
                  class="py-3 rounded-xl text-sm font-medium transition-[background-color,color,box-shadow,transform] duration-200"
                  :class="[year === currentYear ? 'bg-gradient-to-br from-qinghua to-halanzi text-white shadow-lg shadow-qinghua/30' : 'hover:bg-qinghua/10 text-shuimo', year === new Date().getFullYear() && year !== currentYear ? 'ring-2 ring-qinghua/30 text-qinghua' : '']"
                  @click.stop="selectYear(year)">
                  {{ year }}
                </button>
              </div>

              <div v-else-if="pickerView === 'month'" key="month" class="grid grid-cols-3 gap-2">
                <button v-for="(month, idx) in monthsShort" :key="idx"
                  class="py-3 rounded-xl text-sm font-medium transition-[background-color,color,box-shadow,transform] duration-200"
                  :class="[idx === currentMonth ? 'bg-gradient-to-br from-qinghua to-halanzi text-white shadow-lg shadow-qinghua/30' : 'hover:bg-qinghua/10 text-shuimo', idx === new Date().getMonth() && currentYear === new Date().getFullYear() && idx !== currentMonth ? 'ring-2 ring-qinghua/30 text-qinghua' : '']"
                  @click.stop="selectMonth(idx)">
                  {{ month }}
                </button>
              </div>

              <div v-else key="day">
                <div class="grid grid-cols-7 gap-1 mb-2">
                  <div v-for="day in weekDays" :key="day" class="text-center text-xs font-medium text-shuimo/50 py-1">{{ day }}</div>
                </div>
                <div class="grid grid-cols-7 gap-1">
                  <button v-for="(dayObj, idx) in calendarDays" :key="idx"
                    class="aspect-square flex items-center justify-center text-sm rounded-xl transition-[background-color,color,box-shadow,transform] duration-200"
                    :class="[dayObj.current ? 'hover:bg-qinghua/10 cursor-pointer' : 'text-shuimo/20 cursor-default', isSelectedDay(dayObj) ? 'bg-gradient-to-br from-qinghua to-halanzi text-white shadow-lg shadow-qinghua/30' : '', isToday(dayObj) && !isSelectedDay(dayObj) ? 'ring-2 ring-qinghua/30 text-qinghua font-bold' : '', dayObj.current && !isSelectedDay(dayObj) ? 'text-shuimo' : '']"
                    @click.stop="selectDate(dayObj)">
                    {{ dayObj.day }}
                  </button>
                </div>
              </div>
            </Transition>
          </div>

          <div class="px-4 py-3 bg-slate-50/50 border-t border-slate-100 flex justify-between items-center">
            <button v-if="pickerView !== 'day'" class="px-3 py-1.5 text-sm text-qinghua hover:bg-qinghua/10 rounded-lg transition-colors"
              @click.stop="pickerView = pickerView === 'year' ? 'month' : 'day'">返回</button>
            <div v-else />
            <button class="px-4 py-1.5 text-sm text-shuimo/60 hover:text-shuimo transition-colors" @click.stop="showPicker = false">取消</button>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.dropdown-enter-active,
.dropdown-leave-active {
  transition: opacity var(--motion-duration-base) var(--motion-ease-standard), transform var(--motion-duration-base) var(--motion-ease-standard);
}
.dropdown-enter-from,
.dropdown-leave-to {
  opacity: 0; transform: translateY(-8px) scale(0.95);
}
.fade-enter-active,
.fade-leave-active {
  transition: opacity var(--motion-duration-fast) var(--motion-ease-standard), transform var(--motion-duration-fast) var(--motion-ease-standard);
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0; transform: scale(0.98);
}
</style>
