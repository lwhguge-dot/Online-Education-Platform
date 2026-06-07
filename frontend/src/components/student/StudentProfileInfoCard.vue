<script setup>
import { ref, watch, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { User, Mail, Phone, Save, Edit, Camera, Calendar, ChevronLeft, ChevronRight } from 'lucide-vue-next'
import GlassCard from '../ui/GlassCard.vue'
import { userAPI, getImageUrl } from '../../services/api'
import { useToastStore } from '../../stores/toast'

const props = defineProps({
  userId: { type: [Number, String], default: null },
  profile: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['save'])
const toast = useToastStore()

const isEditing = ref(false)
const formData = ref({ ...props.profile })
const avatarInput = ref(null)
const avatarUploading = ref(false)

// 日期选择器状态
const showDatePicker = ref(false)
const currentYear = ref(new Date().getFullYear())
const currentMonth = ref(new Date().getMonth())
const pickerView = ref('day') // 'day', 'month', 'year'
const yearRangeStart = ref(Math.floor(new Date().getFullYear() / 10) * 10)
const datePickerRef = ref(null)
const datePickerStyle = ref({})

const updateDatePickerPosition = () => {
  const el = document.getElementById('profile-birthday-picker')
  if (!el) return
  const rect = el.getBoundingClientRect()
  const viewportHeight = window.innerHeight
  const pickerHeight = 380 // 估计高度

  let top = rect.bottom + 8
  if (rect.bottom + pickerHeight > viewportHeight) {
    top = rect.top - pickerHeight - 8
  }

  datePickerStyle.value = {
    position: 'fixed',
    top: `${top}px`,
    left: `${rect.left}px`,
    zIndex: 9999
  }
}

// 性别选择相关
const showGenderPicker = ref(false)
const genderPickerStyle = ref({})

const updateGenderPickerPosition = () => {
  const el = document.getElementById('profile-gender-picker')
  if (!el) return
  const rect = el.getBoundingClientRect()

  genderPickerStyle.value = {
    position: 'fixed',
    top: `${rect.bottom + 8}px`,
    left: `${rect.left}px`,
    width: `${rect.width}px`,
    zIndex: 9999
  }
}

const toggleGenderPicker = async () => {
  if (!isEditing.value) return
  showGenderPicker.value = !showGenderPicker.value
  if (showGenderPicker.value) {
    await nextTick()
    updateGenderPickerPosition()
  }
}

const handleClickOutside = (event) => {
  // 检查日期选择器
  if (showDatePicker.value) {
    const datePickerEl = document.querySelector('[data-date-picker]')
    const popupEl = datePickerRef.value
    if (datePickerEl && !datePickerEl.contains(event.target) && popupEl && !popupEl.contains(event.target)) {
      showDatePicker.value = false
    }
  }
  // 检查性别选择器
  if (showGenderPicker.value) {
    const genderPickerEl = document.querySelector('[data-gender-picker]')
    const popupEl = document.querySelector('.gender-popup') // 需要给性别弹窗加个类名
    if (genderPickerEl && !genderPickerEl.contains(event.target) && (!popupEl || !popupEl.contains(event.target))) {
      showGenderPicker.value = false
    }
  }
}

const handleScroll = () => {
  if (showDatePicker.value) updateDatePickerPosition()
  if (showGenderPicker.value) updateGenderPickerPosition()
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

// 监听 profile 变化，避免父级更新后界面数据滞后
watch(() => props.profile, (newProfile) => {
  if (!isEditing.value) formData.value = { ...newProfile }
}, { deep: true })

const handleSave = () => {
  emit('save', { ...formData.value })
  isEditing.value = false
}

const toggleEdit = () => {
  if (isEditing.value) {
    formData.value = { ...props.profile }
    isEditing.value = false
  } else {
    formData.value = { ...props.profile }
    isEditing.value = true
  }
}

// 触发头像上传文件选择
const triggerAvatarUpload = () => {
  if (avatarUploading.value) return
  avatarInput.value?.click()
}

const handleActivatorKeydown = (event, action) => {
  // 自定义触发器统一支持 Enter/Space 键激活
  if (event.key !== 'Enter' && event.key !== ' ') return
  event.preventDefault()
  action()
}

// 处理头像文件上传，并将返回的头像地址写入表单
const handleAvatarChange = async (event) => {
  const file = event.target.files?.[0]
  if (!file) return

  const resolvedUserId = Number(props.userId)
  if (!resolvedUserId || Number.isNaN(resolvedUserId)) {
    toast.error('缺少用户ID，无法上传头像')
    event.target.value = ''
    return
  }

  avatarUploading.value = true
  try {
    const res = await userAPI.uploadAvatar(resolvedUserId, file)
    if (res.code === 200 && res.data?.avatarUrl) {
      formData.value.avatar = res.data.avatarUrl
      toast.success('头像上传成功')
    } else {
      toast.error(res.message || '头像上传失败')
    }
  } catch (error) {
    console.error('头像上传失败:', error)
    toast.error('头像上传失败，请稍后重试')
  } finally {
    avatarUploading.value = false
    // 清空已选文件，便于重复选择同一张图触发 change
    event.target.value = ''
  }
}

// 日期选择器逻辑
const weekDays = ['一', '二', '三', '四', '五', '六', '日']
const months = ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月', '11月', '12月']
const monthsShort = ['一月', '二月', '三月', '四月', '五月', '六月', '七月', '八月', '九月', '十月', '十一月', '十二月']

// 年份列表（显示12年）
const yearList = computed(() => {
  const years = []
  for (let i = 0; i < 12; i++) {
    years.push(yearRangeStart.value + i)
  }
  return years
})

const daysInMonth = computed(() => {
  return new Date(currentYear.value, currentMonth.value + 1, 0).getDate()
})

const firstDayOfMonth = computed(() => {
  const day = new Date(currentYear.value, currentMonth.value, 1).getDay()
  return day === 0 ? 6 : day - 1
})

const calendarDays = computed(() => {
  const days = []
  const prevMonthDays = new Date(currentYear.value, currentMonth.value, 0).getDate()

  for (let i = firstDayOfMonth.value - 1; i >= 0; i--) {
    days.push({ day: prevMonthDays - i, current: false, prev: true })
  }
  for (let i = 1; i <= daysInMonth.value; i++) {
    days.push({ day: i, current: true })
  }
  const remaining = 42 - days.length
  for (let i = 1; i <= remaining; i++) {
    days.push({ day: i, current: false, next: true })
  }
  return days
})

const selectedDate = computed(() => {
  if (!formData.value.birthday) return null
  const [y, m, d] = formData.value.birthday.split('-').map(Number)
  return { year: y, month: m - 1, day: d }
})

const isSelectedDay = (dayObj) => {
  if (!selectedDate.value || !dayObj.current) return false
  return selectedDate.value.year === currentYear.value &&
         selectedDate.value.month === currentMonth.value &&
         selectedDate.value.day === dayObj.day
}

const isToday = (dayObj) => {
  if (!dayObj.current) return false
  const today = new Date()
  return today.getFullYear() === currentYear.value &&
         today.getMonth() === currentMonth.value &&
         today.getDate() === dayObj.day
}

const selectDate = (dayObj) => {
  if (!dayObj.current) return
  const month = String(currentMonth.value + 1).padStart(2, '0')
  const day = String(dayObj.day).padStart(2, '0')
  formData.value.birthday = `${currentYear.value}-${month}-${day}`
  showDatePicker.value = false
}

const prevMonth = () => {
  if (currentMonth.value === 0) {
    currentMonth.value = 11
    currentYear.value--
  } else {
    currentMonth.value--
  }
}

const nextMonth = () => {
  if (currentMonth.value === 11) {
    currentMonth.value = 0
    currentYear.value++
  } else {
    currentMonth.value++
  }
}

// 年份视图导航
const prevYearRange = () => {
  yearRangeStart.value -= 12
}

const nextYearRange = () => {
  yearRangeStart.value += 12
}

// 选择年份
const selectYear = (year) => {
  currentYear.value = year
  pickerView.value = 'month'
}

// 选择月份
const selectMonth = (monthIndex) => {
  currentMonth.value = monthIndex
  pickerView.value = 'day'
}

// 切换到年份视图
const showYearView = () => {
  yearRangeStart.value = Math.floor(currentYear.value / 12) * 12
  pickerView.value = 'year'
}

// 切换到月份视图
const showMonthView = () => {
  pickerView.value = 'month'
}

const openDatePicker = async () => {
  if (!isEditing.value) return

  if (formData.value.birthday) {
    const [y, m] = formData.value.birthday.split('-').map(Number)
    currentYear.value = y
    currentMonth.value = m - 1
    yearRangeStart.value = Math.floor(y / 12) * 12
  }
  pickerView.value = 'day'
  showDatePicker.value = true

  await nextTick()
  updateDatePickerPosition()
}

const formatDisplayDate = (dateStr) => {
  if (!dateStr) return '未设置'
  const [y, m, d] = dateStr.split('-')
  return `${y}年${parseInt(m)}月${parseInt(d)}日`
}

// 性别选择
const genderOptions = [
  { value: 'male', label: '男', icon: '👨' },
  { value: 'female', label: '女', icon: '👩' }
]

const selectGender = (value) => {
  formData.value.gender = value
  showGenderPicker.value = false
}

const getGenderLabel = (value) => {
  const opt = genderOptions.find(o => o.value === value)
  return opt ? opt.label : '未设置'
}
</script>

<template>
  <GlassCard class="p-6 col-span-2 card-hover-glow" overflow="visible" style="animation: fade-in-up var(--motion-duration-medium) var(--motion-ease-standard) forwards;">
    <div class="flex items-center justify-between mb-6">
      <h3 class="text-lg font-bold text-shuimo flex items-center gap-2 font-song">
        <User class="w-5 h-5 text-qinghua icon-hover-rotate" />
        个人信息
      </h3>
      <button
        @click="isEditing ? handleSave() : toggleEdit()"
        class="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium transition-[background-color,color,box-shadow,transform] duration-300 btn-ripple"
        :class="isEditing ? 'bg-gradient-to-r from-qinghua to-halanzi text-white shadow-lg shadow-qinghua/30 hover:shadow-qinghua/50' : 'bg-slate-100 text-shuimo hover:bg-slate-200'"
      >
        <component :is="isEditing ? Save : Edit" class="w-4 h-4" />
        {{ isEditing ? '保存修改' : '编辑资料' }}
      </button>
    </div>

    <div class="flex flex-col md:flex-row gap-8">
      <!-- 头像 -->
      <div class="flex flex-col items-center gap-3">
        <div
          class="w-24 h-24 rounded-full bg-slate-100 border-4 border-white shadow-lg overflow-hidden relative group cursor-pointer avatar-hover"
          :class="{ 'opacity-70': avatarUploading }"
          role="button"
          tabindex="0"
          aria-label="上传头像"
          @click="triggerAvatarUpload"
          @keydown="(event) => handleActivatorKeydown(event, triggerAvatarUpload)"
        >
          <img v-if="formData.avatar" :src="getImageUrl(formData.avatar)" class="w-full h-full object-cover" />
          <div v-else class="w-full h-full flex items-center justify-center text-3xl font-bold text-slate-300">
            {{ formData.username?.[0] || '学' }}
          </div>
          <div class="absolute inset-0 bg-shuimo/50 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity duration-300">
            <Camera class="w-8 h-8 text-white transform group-hover:scale-110 transition-transform" />
          </div>
        </div>
        <input
          id="profile-avatar-input"
          ref="avatarInput"
          name="avatar"
          type="file"
          accept="image/*"
          aria-label="上传头像"
          class="hidden"
          @change="handleAvatarChange"
        />
        <label for="profile-avatar-input" class="sr-only">上传头像</label>
      </div>

      <!-- 表单 -->
      <div class="flex-1 grid grid-cols-1 md:grid-cols-2 gap-6">
        <div class="space-y-1.5 stagger-item" style="--stagger-delay: 0.1s;">
          <label for="profile-username" class="text-xs font-bold text-shuimo/60">用户名/昵称</label>
          <input
            id="profile-username"
            name="username"
            v-model="formData.username"
            :disabled="!isEditing"
            autocomplete="username"
            class="w-full bg-slate-50/80 border border-slate-200/50 rounded-xl px-4 py-2.5 text-sm focus:ring-2 focus:ring-qinghua/20 focus:border-qinghua/30 disabled:bg-transparent disabled:border-transparent disabled:px-0 disabled:text-shuimo transition-[background-color,border-color,color,box-shadow,padding] duration-300"
          />
          <p v-if="isEditing" class="text-xs text-shuimo/40">用户名在系统内显示，可随意修改</p>
        </div>
        <div class="space-y-1.5 stagger-item" style="--stagger-delay: 0.15s;">
          <label for="profile-realname" class="text-xs font-bold text-shuimo/60">真实姓名（登录账号）</label>
          <input id="profile-realname" name="realName" v-model="formData.realName" disabled autocomplete="name" class="w-full bg-transparent border-transparent px-0 py-2.5 text-sm text-shuimo cursor-not-allowed" />
          <p class="text-xs text-shuimo/40">真实姓名为登录账号，不可修改</p>
        </div>

        <!-- 自定义日期选择器 -->
        <div class="space-y-1.5 relative z-20" data-date-picker>
          <!-- 无障碍：这里是自定义按钮触发器，使用文本元素并通过 aria-labelledby 关联 -->
          <span id="label-birthday" class="text-xs font-bold text-shuimo/60">出生年月日</span>
          <div
            id="profile-birthday-picker"
            aria-labelledby="label-birthday"
            aria-haspopup="dialog"
            :aria-expanded="showDatePicker ? 'true' : 'false'"
            role="button"
            tabindex="0"
            class="w-full flex items-center justify-between rounded-xl text-sm transition-[background-color,border-color,color,box-shadow,padding] duration-300"
            :class="isEditing ? 'bg-slate-50/80 border border-slate-200/50 cursor-pointer hover:border-qinghua/30 hover:bg-white px-4 py-2.5' : 'text-shuimo py-2.5'"
            @click.stop="openDatePicker"
            @keydown="(event) => handleActivatorKeydown(event, openDatePicker)"
          >
            <span :class="formData.birthday ? 'text-shuimo' : 'text-shuimo/40'">
              {{ formatDisplayDate(formData.birthday) }}
            </span>
            <Calendar v-if="isEditing" class="w-4 h-4 text-shuimo/40" />
          </div>

          <!-- 日期选择器弹窗 -->
          <Teleport to="body">
            <Transition name="dropdown">
              <div
                v-if="showDatePicker"
                ref="datePickerRef"
                :style="datePickerStyle"
                class="w-72 bg-white/95 backdrop-blur-xl rounded-2xl shadow-2xl shadow-black/10 border border-white/50 overflow-visible"
                @click.stop
                @keydown.esc.stop.prevent="showDatePicker = false"
              >
                <div class="p-4">
                  <!-- 头部 -->
                  <div class="flex items-center justify-between mb-4">
                    <button
                      class="p-2 rounded-xl hover:bg-slate-100 transition-colors"
                      @click.stop="pickerView === 'year' ? prevYearRange() : (pickerView === 'month' ? (currentYear--, yearRangeStart = Math.floor(currentYear / 12) * 12) : prevMonth())"
                    >
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
                    <button
                      class="p-2 rounded-xl hover:bg-slate-100 transition-colors"
                      @click.stop="pickerView === 'year' ? nextYearRange() : (pickerView === 'month' ? (currentYear++, yearRangeStart = Math.floor(currentYear / 12) * 12) : nextMonth())"
                    >
                      <ChevronRight class="w-4 h-4 text-shuimo" />
                    </button>
                  </div>

                  <!-- 年份视图 -->
                  <Transition name="fade" mode="out-in">
                    <div v-if="pickerView === 'year'" key="year" class="grid grid-cols-3 gap-2">
                      <button
                        v-for="year in yearList"
                        :key="year"
                        class="py-3 rounded-xl text-sm font-medium transition-[background-color,color,box-shadow,transform] duration-200"
                        :class="[
                          year === currentYear ? 'bg-gradient-to-br from-qinghua to-halanzi text-white shadow-lg shadow-qinghua/30' : 'hover:bg-qinghua/10 text-shuimo',
                          year === new Date().getFullYear() && year !== currentYear ? 'ring-2 ring-qinghua/30 text-qinghua' : ''
                        ]"
                        @click.stop="selectYear(year)"
                      >
                        {{ year }}
                      </button>
                    </div>

                    <!-- 月份视图 -->
                    <div v-else-if="pickerView === 'month'" key="month" class="grid grid-cols-3 gap-2">
                      <button
                        v-for="(month, idx) in monthsShort"
                        :key="idx"
                        class="py-3 rounded-xl text-sm font-medium transition-[background-color,color,box-shadow,transform] duration-200"
                        :class="[
                          idx === currentMonth ? 'bg-gradient-to-br from-qinghua to-halanzi text-white shadow-lg shadow-qinghua/30' : 'hover:bg-qinghua/10 text-shuimo',
                          idx === new Date().getMonth() && currentYear === new Date().getFullYear() && idx !== currentMonth ? 'ring-2 ring-qinghua/30 text-qinghua' : ''
                        ]"
                        @click.stop="selectMonth(idx)"
                      >
                        {{ month }}
                      </button>
                    </div>

                    <!-- 日期视图 -->
                    <div v-else key="day">
                      <!-- 星期 -->
                      <div class="grid grid-cols-7 gap-1 mb-2">
                        <div v-for="day in weekDays" :key="day" class="text-center text-xs font-medium text-shuimo/50 py-1">
                          {{ day }}
                        </div>
                      </div>

                      <!-- 日期 -->
                      <div class="grid grid-cols-7 gap-1">
                        <button
                          v-for="(dayObj, idx) in calendarDays"
                          :key="idx"
                          class="aspect-square flex items-center justify-center text-sm rounded-xl transition-[background-color,color,box-shadow,transform] duration-200"
                          :class="[
                            dayObj.current ? 'hover:bg-qinghua/10 cursor-pointer' : 'text-shuimo/20 cursor-default',
                            isSelectedDay(dayObj) ? 'bg-gradient-to-br from-qinghua to-halanzi text-white shadow-lg shadow-qinghua/30' : '',
                            isToday(dayObj) && !isSelectedDay(dayObj) ? 'ring-2 ring-qinghua/30 text-qinghua font-bold' : '',
                            dayObj.current && !isSelectedDay(dayObj) ? 'text-shuimo' : ''
                          ]"
                          @click.stop="selectDate(dayObj)"
                        >
                          {{ dayObj.day }}
                        </button>
                      </div>
                    </div>
                  </Transition>
                </div>

                <!-- 底部按钮 -->
                <div class="px-4 py-3 bg-slate-50/50 border-t border-slate-100 flex justify-between items-center">
                  <button
                    v-if="pickerView !== 'day'"
                    class="px-3 py-1.5 text-sm text-qinghua hover:bg-qinghua/10 rounded-lg transition-colors"
                    @click.stop="pickerView = pickerView === 'year' ? 'month' : 'day'"
                  >
                    返回
                  </button>
                  <div v-else></div>
                  <button class="px-4 py-1.5 text-sm text-shuimo/60 hover:text-shuimo transition-colors" @click.stop="showDatePicker = false">
                    取消
                  </button>
                </div>
              </div>
            </Transition>
          </Teleport>
        </div>

        <!-- 自定义性别选择器 -->
        <div class="space-y-1.5 relative" data-gender-picker>
          <!-- 无障碍：这里是自定义按钮触发器，使用文本元素并通过 aria-labelledby 关联 -->
          <span id="label-gender" class="text-xs font-bold text-shuimo/60">性别</span>
          <div
            id="profile-gender-picker"
            aria-labelledby="label-gender"
            aria-haspopup="listbox"
            :aria-expanded="showGenderPicker ? 'true' : 'false'"
            role="button"
            tabindex="0"
            class="w-full flex items-center justify-between rounded-xl text-sm transition-[background-color,border-color,color,box-shadow,padding] duration-300"
            :class="isEditing ? 'bg-slate-50/80 border border-slate-200/50 cursor-pointer hover:border-qinghua/30 hover:bg-white px-4 py-2.5' : 'text-shuimo py-2.5'"
            @click.stop="toggleGenderPicker"
            @keydown="(event) => handleActivatorKeydown(event, toggleGenderPicker)"
          >
            <span :class="formData.gender ? 'text-shuimo' : 'text-shuimo/40'">
              {{ getGenderLabel(formData.gender) }}
            </span>
            <ChevronRight v-if="isEditing" class="w-4 h-4 text-shuimo/40 transition-transform" :class="showGenderPicker ? 'rotate-90' : ''" />
          </div>

          <!-- 性别选择弹窗 -->
          <Teleport to="body">
            <Transition name="dropdown">
              <div
                v-if="showGenderPicker && isEditing"
                :style="genderPickerStyle"
                class="gender-popup bg-white/95 backdrop-blur-xl rounded-2xl shadow-2xl shadow-black/10 border border-white/50 overflow-hidden"
                @click.stop
                @keydown.esc.stop.prevent="showGenderPicker = false"
              >
                <div class="p-2">
                  <button
                    v-for="opt in genderOptions"
                    :key="opt.value"
                    class="w-full flex items-center gap-3 px-4 py-3 rounded-xl transition-[background-color,color,box-shadow] duration-200"
                    :class="formData.gender === opt.value ? 'bg-gradient-to-r from-qinghua/10 to-halanzi/10 text-qinghua' : 'hover:bg-slate-50 text-shuimo'"
                    @click.stop="selectGender(opt.value)"
                  >
                    <span class="text-xl">{{ opt.icon }}</span>
                    <span class="font-medium">{{ opt.label }}</span>
                    <div v-if="formData.gender === opt.value" class="ml-auto w-2 h-2 rounded-full bg-qinghua"></div>
                  </button>
                </div>
              </div>
            </Transition>
          </Teleport>
        </div>

        <div class="space-y-1.5">
          <label for="profile-email" class="text-xs font-bold text-shuimo/60 flex items-center gap-1">
            <Mail class="w-3 h-3" /> 邮箱（登录账号）
          </label>
          <input id="profile-email" name="email" v-model="formData.email" disabled autocomplete="email" class="w-full bg-transparent border-transparent px-0 py-2.5 text-sm text-shuimo cursor-not-allowed" />
          <p class="text-xs text-shuimo/40">邮箱为登录账号，不可修改</p>
        </div>
        <div class="space-y-1.5">
          <label for="profile-phone" class="text-xs font-bold text-shuimo/60 flex items-center gap-1">
            <Phone class="w-3 h-3" /> 手机号
          </label>
          <input
            id="profile-phone"
            name="phone"
            v-model="formData.phone"
            :disabled="!isEditing"
            autocomplete="tel"
            class="w-full bg-slate-50/80 border border-slate-200/50 rounded-xl px-4 py-2.5 text-sm focus:ring-2 focus:ring-qinghua/20 focus:border-qinghua/30 disabled:bg-transparent disabled:border-transparent disabled:px-0 disabled:text-shuimo transition-[background-color,border-color,color,box-shadow,padding] duration-300"
          />
        </div>
      </div>
    </div>
  </GlassCard>
</template>

<style scoped>
.stagger-item {
  animation: fade-in-up var(--motion-duration-medium) var(--motion-ease-standard) forwards;
  animation-delay: var(--stagger-delay, 0s);
  opacity: 0;
}

.dropdown-enter-active,
.dropdown-leave-active {
  /* 下拉层仅过渡透明度与位移缩放 */
  transition:
    opacity var(--motion-duration-base) var(--motion-ease-standard),
    transform var(--motion-duration-base) var(--motion-ease-standard);
}
.dropdown-enter-from,
.dropdown-leave-to {
  opacity: 0;
  transform: translateY(-8px) scale(0.95);
}

.fade-enter-active,
.fade-leave-active {
  transition:
    opacity var(--motion-duration-fast) var(--motion-ease-standard),
    transform var(--motion-duration-fast) var(--motion-ease-standard);
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: scale(0.98);
}
</style>
