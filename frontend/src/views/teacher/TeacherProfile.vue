<script setup>
import { ref, watch, computed } from 'vue'
import { User, Mail, Phone, Bell, Save, Edit, Camera, BookOpen } from 'lucide-vue-next'
import GlassCard from '../../components/ui/GlassCard.vue'
import { teacherProfileAPI, getImageUrl } from '../../services/api'
import { useToastStore } from '../../stores/toast'

const props = defineProps({
  user: { type: Object, default: null },
  profile: { type: Object, default: () => ({}) },
  settings: { type: Object, default: () => ({}) }
})

const toast = useToastStore()
const isEditing = ref(false)
const avatarInput = ref(null)
const avatarUploading = ref(false)

const availableSubjects = ['语文', '数学', '英语', '物理', '化学', '生物', '历史', '地理', '政治']

// 教学科目可视化统计：用于展示已选数量和覆盖率
const selectedSubjectCount = computed(() => {
  return Array.isArray(fullProfile.value.teachingSubjects) ? fullProfile.value.teachingSubjects.length : 0
})

const subjectCoveragePercent = computed(() => {
  if (!availableSubjects.length) return 0
  return Math.round((selectedSubjectCount.value / availableSubjects.length) * 100)
})

const fullProfile = ref({
  username: '',
  realName: '',
  email: '',
  phone: '',
  avatar: '',
  title: '',
  introduction: '',
  teachingSubjects: [],
  notificationSettings: {
    newStudent: true,
    homeworkSubmit: true,
    studentQuestion: true,
    systemNotice: true,
    courseReview: true,
    deadlineReminder: true,
    atRiskStudent: true
  }
})

watch(() => props.profile, (newProfile) => {
  fullProfile.value = {
    ...fullProfile.value,
    ...(newProfile || {}),
    teachingSubjects: Array.isArray(newProfile?.teachingSubjects) ? [...newProfile.teachingSubjects] : (fullProfile.value.teachingSubjects || []),
    notificationSettings: {
      ...fullProfile.value.notificationSettings,
      ...(newProfile?.notificationSettings || {})
    }
  }
}, { immediate: true, deep: true })

const handleSave = async () => {
  if (!props.user?.id) {
    toast.error('未登录')
    return
  }
  try {
    const res = await teacherProfileAPI.updateProfile(props.user.id, fullProfile.value)
    if (res.code === 200) {
      toast.success('保存成功')
      isEditing.value = false
    } else {
      toast.error(res.message || '保存失败')
    }
  } catch {
    toast.error('保存失败')
  }
}

const toggleEdit = () => {
  isEditing.value = !isEditing.value
  if (!isEditing.value) {
    fullProfile.value = {
      ...fullProfile.value,
      ...(props.profile || {}),
      teachingSubjects: Array.isArray(props.profile?.teachingSubjects) ? [...props.profile.teachingSubjects] : (fullProfile.value.teachingSubjects || []),
      notificationSettings: {
        ...fullProfile.value.notificationSettings,
        ...(props.profile?.notificationSettings || {})
      }
    }
  }
}

const triggerAvatarUpload = () => {
  if (avatarUploading.value) return
  avatarInput.value?.click()
}

const handleAvatarChange = async (event) => {
  const file = event.target.files?.[0]
  if (!file) return
  if (!props.user?.id) {
    toast.error('未登录')
    event.target.value = ''
    return
  }

  avatarUploading.value = true
  try {
    const res = await teacherProfileAPI.uploadAvatar(props.user.id, file)
    if (res.code === 200 && res.data?.avatarUrl) {
      fullProfile.value.avatar = res.data.avatarUrl
      toast.success('头像上传成功')
    } else {
      toast.error(res.message || '头像上传失败')
    }
  } catch {
    toast.error('头像上传失败')
  } finally {
    avatarUploading.value = false
    event.target.value = ''
  }
}

const toggleNotification = (key) => {
  fullProfile.value.notificationSettings[key] = !fullProfile.value.notificationSettings[key]
}

const toggleSubject = (subject) => {
  if (!Array.isArray(fullProfile.value.teachingSubjects)) {
    fullProfile.value.teachingSubjects = []
  }
  const idx = fullProfile.value.teachingSubjects.indexOf(subject)
  if (idx === -1) fullProfile.value.teachingSubjects.push(subject)
  else fullProfile.value.teachingSubjects.splice(idx, 1)
}
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <GlassCard class="p-6 col-span-2 card-hover-glow" overflow="visible" style="animation: fade-in-up 0.5s ease-out forwards;">
        <div class="flex items-center justify-between mb-6">
          <h3 class="text-lg font-bold text-shuimo flex items-center gap-2 font-song">
            <User class="w-5 h-5 text-qinghua icon-hover-rotate" />
            个人信息
          </h3>
          <button
            @click="isEditing ? handleSave() : toggleEdit()"
            class="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium transition-all duration-300 btn-ripple"
            :class="isEditing ? 'bg-gradient-to-r from-qinghua to-halanzi text-white shadow-lg shadow-qinghua/30 hover:shadow-qinghua/50' : 'bg-slate-100 text-shuimo hover:bg-slate-200'"
          >
            <component :is="isEditing ? Save : Edit" class="w-4 h-4" />
            {{ isEditing ? '保存修改' : '编辑资料' }}
          </button>
        </div>

        <div class="flex flex-col md:flex-row gap-8">
          <div class="flex flex-col items-center gap-3">
            <div
              class="w-24 h-24 rounded-full bg-slate-100 border-4 border-white shadow-lg overflow-hidden relative group cursor-pointer avatar-hover"
              :class="{ 'opacity-70': avatarUploading }"
              @click="triggerAvatarUpload"
            >
              <img v-if="fullProfile.avatar" :src="getImageUrl(fullProfile.avatar)" class="w-full h-full object-cover" />
              <div v-else class="w-full h-full flex items-center justify-center text-3xl font-bold text-slate-300">
                {{ fullProfile.username?.[0] || '师' }}
              </div>
              <div class="absolute inset-0 bg-shuimo/50 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-all duration-300">
                <Camera class="w-8 h-8 text-white transform group-hover:scale-110 transition-transform" />
              </div>
            </div>
            <input
              id="teacher-profile-avatar-input"
              ref="avatarInput"
              name="teacherAvatar"
              type="file"
              accept="image/*"
              aria-label="上传头像"
              class="hidden"
              @change="handleAvatarChange"
            />
            <label for="teacher-profile-avatar-input" class="sr-only">上传头像</label>
          </div>

          <div class="flex-1 grid grid-cols-1 md:grid-cols-2 gap-6">
            <div class="space-y-1.5 stagger-item" style="animation: fade-in-up 0.4s ease-out 0.1s forwards; opacity: 0;">
              <label for="teacher-profile-username" class="text-xs font-bold text-shuimo/60">用户名/昵称</label>
              <input id="teacher-profile-username" name="username" v-model="fullProfile.username" :disabled="!isEditing" autocomplete="username"
                     class="w-full bg-slate-50/80 border border-slate-200/50 rounded-xl px-4 py-2.5 text-sm focus:ring-2 focus:ring-qinghua/20 focus:border-qinghua/30 disabled:bg-transparent disabled:border-transparent disabled:px-0 disabled:text-shuimo transition-all duration-300" />
              <p v-if="isEditing" class="text-xs text-shuimo/40">用户名在系统内显示，可随意修改</p>
            </div>
            <div class="space-y-1.5 stagger-item" style="animation: fade-in-up 0.4s ease-out 0.15s forwards; opacity: 0;">
              <label for="teacher-profile-realname" class="text-xs font-bold text-shuimo/60">真实姓名（登录账号）</label>
              <input id="teacher-profile-realname" name="realName" v-model="fullProfile.realName" disabled autocomplete="name"
                     class="w-full bg-transparent border-transparent px-0 py-2.5 text-sm text-shuimo cursor-not-allowed" />
              <p class="text-xs text-shuimo/40">真实姓名为登录账号，不可修改</p>
            </div>
            <div class="space-y-1.5 stagger-item" style="animation: fade-in-up 0.4s ease-out 0.2s forwards; opacity: 0;">
              <label for="teacher-profile-email" class="text-xs font-bold text-shuimo/60 flex items-center gap-1"><Mail class="w-3.5 h-3.5"/>邮箱（登录账号）</label>
              <input id="teacher-profile-email" name="email" v-model="fullProfile.email" disabled autocomplete="email"
                     class="w-full bg-transparent border-transparent px-0 py-2.5 text-sm text-shuimo cursor-not-allowed" />
              <p class="text-xs text-shuimo/40">邮箱为登录账号，不可修改</p>
            </div>
            <div class="space-y-1.5 stagger-item" style="animation: fade-in-up 0.4s ease-out 0.25s forwards; opacity: 0;">
              <label for="teacher-profile-phone" class="text-xs font-bold text-shuimo/60 flex items-center gap-1"><Phone class="w-3.5 h-3.5"/>手机号</label>
              <input id="teacher-profile-phone" name="phone" v-model="fullProfile.phone" :disabled="!isEditing" autocomplete="tel"
                     class="w-full bg-slate-50/80 border border-slate-200/50 rounded-xl px-4 py-2.5 text-sm focus:ring-2 focus:ring-qinghua/20 focus:border-qinghua/30 disabled:bg-transparent disabled:border-transparent disabled:px-0 disabled:text-shuimo transition-all duration-300" />
            </div>
          </div>
        </div>
      </GlassCard>

      <div class="space-y-6">
        <GlassCard class="p-6 card-hover-glow" style="animation: fade-in-up 0.5s ease-out 0.2s forwards; opacity: 0;">
          <h3 class="text-lg font-bold text-shuimo mb-6 flex items-center gap-2 font-song">
            <Bell class="w-5 h-5 text-zhizi icon-hover-rotate" />
            消息通知
          </h3>
          <div class="space-y-4">
            <div v-for="(label, key) in { newStudent: '新学生', homeworkSubmit: '作业提交', studentQuestion: '学生提问', systemNotice: '系统公告', courseReview: '课程审核', deadlineReminder: '截止提醒' }" :key="key" class="flex items-center justify-between group">
              <span class="text-sm text-shuimo group-hover:text-shuimo/80 transition-colors">{{ label }}</span>
              <button class="w-11 h-6 rounded-full transition-all duration-300 relative shadow-inner switch-enhanced"
                      :class="fullProfile.notificationSettings[key] ? 'bg-gradient-to-r from-qinghua to-halanzi' : 'bg-slate-200'"
                      @click="toggleNotification(key)">
                <div class="absolute top-1 left-1 w-4 h-4 rounded-full bg-white shadow-md transition-all duration-300"
                     :class="fullProfile.notificationSettings[key] ? 'translate-x-5' : 'translate-x-0'"></div>
              </button>
            </div>
          </div>
        </GlassCard>

        <GlassCard class="p-6 card-hover-glow" style="animation: fade-in-up 0.5s ease-out 0.3s forwards; opacity: 0;">
          <div class="flex items-center justify-between mb-4">
            <h3 class="text-lg font-bold text-shuimo flex items-center gap-2 font-song">
              <BookOpen class="w-5 h-5 text-tianlv icon-hover-rotate" />
              教学科目
            </h3>
            <span class="text-sm font-bold text-tianlv number-pop">{{ selectedSubjectCount }} / {{ availableSubjects.length }}</span>
          </div>

          <div class="mb-5">
            <div class="flex justify-between mb-2">
              <span class="text-sm text-shuimo">科目覆盖率</span>
              <span class="text-sm font-bold text-tianlv number-pop">{{ subjectCoveragePercent }}%</span>
            </div>
            <div class="h-2 rounded-full bg-slate-200/80 overflow-hidden">
              <div
                class="h-full rounded-full bg-gradient-to-r from-tianlv to-qingsong transition-all duration-500"
                :style="{ width: `${subjectCoveragePercent}%` }"
              ></div>
            </div>
          </div>

          <div class="grid grid-cols-3 gap-3">
            <button
              v-for="subject in availableSubjects"
              :key="subject"
              @click="toggleSubject(subject)"
              :disabled="!isEditing"
              :class="[
                'px-3 py-2 rounded-xl text-sm border transition-all duration-300',
                fullProfile.teachingSubjects?.includes(subject)
                  ? 'bg-tianlv/15 border-tianlv/40 text-tianlv font-semibold shadow-sm scale-[1.02]'
                  : 'bg-white/60 border-slate-200 text-shuimo/80',
                isEditing
                  ? 'hover:-translate-y-0.5 hover:shadow-sm cursor-pointer'
                  : 'opacity-75 cursor-not-allowed'
              ]"
            >
              {{ subject }}
            </button>
          </div>
          <p class="mt-3 text-xs text-shuimo/40">{{ isEditing ? '点击科目进行选择或取消' : '点击“编辑资料”后可调整教学科目' }}</p>
        </GlassCard>
      </div>
    </div>
  </div>
</template>

<style scoped>
.slider-tianlv::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: linear-gradient(135deg, #2dd4bf, #14b8a6);
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(45, 212, 191, 0.4);
  transition: all 0.2s;
}

.slider-tianlv::-webkit-slider-thumb:hover {
  transform: scale(1.1);
  box-shadow: 0 4px 12px rgba(45, 212, 191, 0.5);
}

.slider-tianlv::-moz-range-thumb {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: linear-gradient(135deg, #2dd4bf, #14b8a6);
  cursor: pointer;
  border: none;
  box-shadow: 0 2px 8px rgba(45, 212, 191, 0.4);
}
</style>
