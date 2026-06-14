<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { User, Mail, Phone, Save, Edit, Camera, ChevronRight } from 'lucide-vue-next'
import GlassCard from '../ui/GlassCard.vue'
import DatePicker from './DatePicker.vue'
import { userAPI, getImageUrl } from '../../services/api'
import { useToastStore } from '../../stores/toast'
import { logger } from '../../utils/logger'

const props = defineProps({
  userId: { type: [Number, String], default: null },
  profile: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['save'])
const toast = useToastStore()

const isEditing = ref(false)
const formData = ref({ ...props.profile })
const avatarInput = ref<HTMLInputElement | null>(null)
const avatarUploading = ref(false)

const showGenderPicker = ref(false)
const genderPickerStyle = ref<Record<string, string>>({})

const updateGenderPickerPosition = () => {
  const el = document.getElementById('profile-gender-picker')
  if (!el) return
  const rect = el.getBoundingClientRect()
  genderPickerStyle.value = { position: 'fixed', top: `${rect.bottom + 8}px`, left: `${rect.left}px`, width: `${rect.width}px`, zIndex: '9999' }
}

const toggleGenderPicker = async () => {
  if (!isEditing.value) return
  showGenderPicker.value = !showGenderPicker.value
  if (showGenderPicker.value) { await nextTick(); updateGenderPickerPosition() }
}

const handleClickOutside = (event: MouseEvent) => {
  if (showGenderPicker.value) {
    const genderEl = document.querySelector('[data-gender-picker]')
    const popupEl = document.querySelector('.gender-popup')
    if (genderEl && !genderEl.contains(event.target as Node) && (!popupEl || !popupEl.contains(event.target as Node))) {
      showGenderPicker.value = false
    }
  }
}

const handleScroll = () => { if (showGenderPicker.value) updateGenderPickerPosition() }

onMounted(() => { document.addEventListener('click', handleClickOutside); window.addEventListener('scroll', handleScroll, true); window.addEventListener('resize', handleScroll) })
onUnmounted(() => { document.removeEventListener('click', handleClickOutside); window.removeEventListener('scroll', handleScroll, true); window.removeEventListener('resize', handleScroll) })

watch(() => props.profile, (newProfile) => { if (!isEditing.value) formData.value = { ...newProfile } }, { deep: true })

const handleSave = () => { emit('save', { ...formData.value }); isEditing.value = false }
const toggleEdit = () => { formData.value = isEditing.value ? { ...props.profile } : { ...props.profile }; isEditing.value = !isEditing.value }

const triggerAvatarUpload = () => { if (!avatarUploading.value) avatarInput.value?.click() }

const handleActivatorKeydown = (event: KeyboardEvent, action: () => void) => {
  if (event.key !== 'Enter' && event.key !== ' ') return
  event.preventDefault(); action()
}

const handleAvatarChange = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  const resolvedUserId = Number(props.userId)
  if (!resolvedUserId || Number.isNaN(resolvedUserId)) { toast.error('缺少用户ID，无法上传头像'); input.value = ''; return }
  avatarUploading.value = true
  try {
    const res = await userAPI.uploadAvatar(resolvedUserId, file)
    if (res.code === 200 && res.data?.avatarUrl) { formData.value.avatar = res.data.avatarUrl; toast.success('头像上传成功') }
    else { toast.error(res.message || '头像上传失败') }
  } catch (error) { logger.error('头像上传失败:', error); toast.error('头像上传失败，请稍后重试') }
  finally { avatarUploading.value = false; input.value = '' }
}

const genderOptions = [
  { value: 'male', label: '男', icon: '👨' },
  { value: 'female', label: '女', icon: '👩' }
]
const selectGender = (value: string) => { formData.value.gender = value; showGenderPicker.value = false }
const getGenderLabel = (value: string) => genderOptions.find(o => o.value === value)?.label || '未设置'
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

        <!-- 日期选择器 -->
        <div class="space-y-1.5">
          <DatePicker v-model="formData.birthday" :disabled="!isEditing" />
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