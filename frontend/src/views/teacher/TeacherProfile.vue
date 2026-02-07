<script setup>
import { ref, onMounted, computed, watch, nextTick } from 'vue'
import { Settings, Camera, Edit, Save, Bell, BookOpen, BarChart3, Layout, Upload } from 'lucide-vue-next'
import GlassCard from '../../components/ui/GlassCard.vue'
import BaseButton from '../../components/ui/BaseButton.vue'
import BaseInput from '../../components/ui/BaseInput.vue'
import { teacherProfileAPI, getImageUrl } from '../../services/api'
import { useToastStore } from '../../stores/toast'

const props = defineProps({
  user: { type: Object, default: null },
  profile: { type: Object, default: () => ({}) },
  settings: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['update:profile', 'update:settings', 'save-profile'])
const toast = useToastStore()
const activeTab = ref('profile')
const isEditing = ref(false)
const loading = ref(false)
const saving = ref(false)
const avatarInput = ref(null)
const uploadingAvatar = ref(false)

// Tab 指示器状态
const tabsRef = ref(null)
const indicatorStyle = ref({ left: '0px', width: '0px' })

const fullProfile = ref({
  username: '', realName: '', email: '', phone: '', avatar: '',
  title: '', department: '', introduction: '', teachingSubjects: [],
  defaultGradingCriteria: { excellentThreshold: 90, goodThreshold: 80, passThreshold: 60 },
  dashboardLayout: { refreshInterval: 30 },
  notificationSettings: { newStudent: true, homeworkSubmit: true, studentQuestion: true, systemNotice: true, courseReview: true, deadlineReminder: true, atRiskStudent: true }
})

const availableSubjects = ['语文', '数学', '英语', '物理', '化学', '生物', '历史', '地理', '政治']

// 安全访问 teachingSubjects
const safeTeachingSubjects = computed(() => {
  return Array.isArray(fullProfile.value?.teachingSubjects) ? fullProfile.value.teachingSubjects : []
})
const tabs = [
  { id: 'profile', label: '基本信息', icon: Settings },
  { id: 'subjects', label: '教学科目', icon: BookOpen },
  { id: 'grading', label: '评分标准', icon: BarChart3 },
  { id: 'notification', label: '通知设置', icon: Bell },
  { id: 'dashboard', label: '仪表盘设置', icon: Layout }
]

// 更新指示器位置
const updateIndicator = async () => {
  await nextTick()
  if (!tabsRef.value) return

  const activeButton = tabsRef.value.querySelector(`[data-tab="${activeTab.value}"]`)
  if (activeButton) {
    const containerRect = tabsRef.value.getBoundingClientRect()
    const buttonRect = activeButton.getBoundingClientRect()
    indicatorStyle.value = {
      left: `${buttonRect.left - containerRect.left}px`,
      width: `${buttonRect.width}px`
    }
  }
}

// 监听 Tab 变化
watch(activeTab, updateIndicator)

// 初始化和窗口大小变化时更新指示器
onMounted(() => {
  loadProfile()
  updateIndicator()
  window.addEventListener('resize', updateIndicator)
})

const loadProfile = async () => {
  if (!props.user?.id) return
  loading.value = true
  try {
    const res = await teacherProfileAPI.getProfile(props.user.id)
    if (res.code === 200 && res.data) {
      fullProfile.value = { ...fullProfile.value, ...res.data }
    }
  } catch (e) { console.error(e) }
  finally { loading.value = false }
}

const handleSaveProfile = async () => {
  if (!props.user?.id) {
    toast.error('未登录')
    return
  }
  saving.value = true
  try {
    const res = await teacherProfileAPI.updateProfile(props.user.id, fullProfile.value)
    if (res.code === 200) { toast.success('保存成功'); isEditing.value = false }
    else { toast.error(res.message || '保存失败') }
  } catch (e) { toast.error('保存失败') }
  finally { saving.value = false }
}

const triggerAvatarUpload = () => avatarInput.value?.click()
const handleAvatarChange = async (e) => {
  if (!props.user?.id) {
    toast.error('未登录')
    return
  }
  const file = e.target.files?.[0]
  if (!file) return
  uploadingAvatar.value = true
  try {
    const res = await teacherProfileAPI.uploadAvatar(props.user.id, file)
    if (res.code === 200) { fullProfile.value.avatar = res.data.avatarUrl; toast.success('上传成功') }
    else { toast.error(res.message || '上传失败') }
  } catch (e) { toast.error('上传失败') }
  finally { uploadingAvatar.value = false }
}

const toggleSubject = (s) => {
  // 确保 teachingSubjects 是数组
  if (!Array.isArray(fullProfile.value.teachingSubjects)) {
    fullProfile.value.teachingSubjects = []
  }
  const idx = fullProfile.value.teachingSubjects.indexOf(s)
  if (idx === -1) fullProfile.value.teachingSubjects.push(s)
  else fullProfile.value.teachingSubjects.splice(idx, 1)
}

const toggleNotification = (key) => {
  fullProfile.value.notificationSettings[key] = !fullProfile.value.notificationSettings[key]
}
</script>

<template>
  <div class="space-y-6">
    <!-- Tab 导航 - 带滑动指示器 -->
    <div ref="tabsRef" class="relative flex gap-2 flex-wrap pb-1">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        :data-tab="tab.id"
        @click="activeTab = tab.id"
        :class="[
          'px-4 py-2.5 rounded-xl text-sm font-medium flex items-center gap-2 transition-all duration-300 relative z-10',
          activeTab === tab.id
            ? 'text-white'
            : 'bg-white/50 text-shuimo/70 hover:bg-white/80 hover:text-shuimo'
        ]"
      >
        <component :is="tab.icon" class="w-4 h-4" />{{ tab.label }}
      </button>

      <!-- 滑动指示器背景 -->
      <div
        class="absolute top-0 h-[42px] rounded-xl bg-gradient-to-r from-qinghua to-halanzi shadow-lg shadow-qinghua/20 transition-all duration-300 ease-out"
        :style="indicatorStyle"
      ></div>
    </div>
    <GlassCard v-if="activeTab === 'profile'" class="p-8">
      <div class="flex justify-between mb-6">
        <h3 class="text-xl font-bold">个人资料</h3>
        <BaseButton v-if="!isEditing" @click="isEditing = true" variant="secondary" size="sm"><Edit class="w-4 h-4 mr-2"/>编辑</BaseButton>
        <BaseButton v-else @click="handleSaveProfile" :disabled="saving"><Save class="w-4 h-4 mr-2"/>保存</BaseButton>
      </div>
      <div class="flex flex-col md:flex-row gap-8">
        <div class="flex flex-col items-center">
          <div class="relative">
            <div v-if="fullProfile.avatar" class="w-32 h-32 rounded-full overflow-hidden"><img :src="getImageUrl(fullProfile.avatar)" class="w-full h-full object-cover"/></div>
            <div v-else class="w-32 h-32 rounded-full bg-gradient-to-br from-tianlv to-qingsong flex items-center justify-center text-white text-4xl font-bold">{{ user?.username?.charAt(0) || 'T' }}</div>
            <button v-if="isEditing" @click="triggerAvatarUpload" class="absolute bottom-1 right-1 p-2 bg-white rounded-full shadow"><Camera class="w-5 h-5"/></button>
            <input ref="avatarInput" type="file" accept="image/*" class="hidden" @change="handleAvatarChange"/>
          </div>
        </div>
        <div class="flex-1 grid grid-cols-1 md:grid-cols-2 gap-4">
          <BaseInput v-model="fullProfile.realName" label="真实姓名" :disabled="!isEditing"/>
          <BaseInput v-model="fullProfile.title" label="职称" :disabled="!isEditing"/>
          <BaseInput v-model="fullProfile.email" label="邮箱" disabled/>
          <BaseInput v-model="fullProfile.phone" label="手机" :disabled="!isEditing"/>
        </div>
      </div>
    </GlassCard>
    <GlassCard v-if="activeTab === 'subjects'" class="p-8">
      <h3 class="text-xl font-bold mb-6">教学科目</h3>
      <div class="grid grid-cols-3 md:grid-cols-5 gap-4">
        <button v-for="s in availableSubjects" :key="s" @click="toggleSubject(s)"
          :class="['p-4 rounded-xl border-2', safeTeachingSubjects.includes(s) ? 'border-tianlv bg-tianlv/10' : 'border-slate-200']">{{ s }}</button>
      </div>
      <div class="mt-6 flex justify-end"><BaseButton @click="handleSaveProfile" :disabled="saving">保存</BaseButton></div>
    </GlassCard>
    <GlassCard v-if="activeTab === 'grading'" class="p-8">
      <h3 class="text-xl font-bold mb-6">评分标准</h3>
      <div class="grid grid-cols-3 gap-4">
        <div><label class="block text-sm mb-2">优秀线</label><input v-model.number="fullProfile.defaultGradingCriteria.excellentThreshold" type="number" class="w-full px-4 py-2 rounded-xl border"/></div>
        <div><label class="block text-sm mb-2">良好线</label><input v-model.number="fullProfile.defaultGradingCriteria.goodThreshold" type="number" class="w-full px-4 py-2 rounded-xl border"/></div>
        <div><label class="block text-sm mb-2">及格线</label><input v-model.number="fullProfile.defaultGradingCriteria.passThreshold" type="number" class="w-full px-4 py-2 rounded-xl border"/></div>
      </div>
      <div class="mt-6 flex justify-end"><BaseButton @click="handleSaveProfile">保存</BaseButton></div>
    </GlassCard>
    <GlassCard v-if="activeTab === 'notification'" class="p-8">
      <h3 class="text-xl font-bold mb-6">通知设置</h3>
      <div class="grid grid-cols-2 gap-4">
        <div v-for="(label, key) in { newStudent: '新学生', homeworkSubmit: '作业提交', studentQuestion: '学生提问', systemNotice: '系统公告', courseReview: '课程审核', deadlineReminder: '截止提醒', atRiskStudent: '预警学生' }" :key="key" class="flex justify-between p-4 rounded-xl bg-white/40">
          <span>{{ label }}</span>
          <button @click="toggleNotification(key)" :class="['w-12 h-7 rounded-full relative', fullProfile.notificationSettings[key] ? 'bg-qinghua' : 'bg-slate-200']">
            <div :class="['w-5 h-5 bg-white rounded-full absolute top-1', fullProfile.notificationSettings[key] ? 'left-6' : 'left-1']"></div>
          </button>
        </div>
      </div>
    </GlassCard>
    <GlassCard v-if="activeTab === 'dashboard'" class="p-8">
      <h3 class="text-xl font-bold mb-6">仪表盘设置</h3>
      <div><label class="block text-sm mb-2">刷新间隔(秒)</label><input v-model.number="fullProfile.dashboardLayout.refreshInterval" type="number" class="w-48 px-4 py-2 rounded-xl border"/></div>
      <div class="mt-6 flex justify-end"><BaseButton @click="handleSaveProfile">保存</BaseButton></div>
    </GlassCard>
  </div>
</template>



