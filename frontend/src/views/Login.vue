<script setup>
import { ref, watch, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { authAPI } from '../services/api'
import BaseInput from '../components/ui/BaseInput.vue'
import BaseButton from '../components/ui/BaseButton.vue'
import GlassCard from '../components/ui/GlassCard.vue'
import { 
  GraduationCap, 
  Lock, 
  Eye, 
  EyeOff, 
  ArrowRight,
  User,
  Users,
  AlertCircle,
  Mail,
  KeyRound,
  X,
  CheckCircle
} from 'lucide-vue-next'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const isLogin = ref(true)
const showPassword = ref(false)
const showConfirmPassword = ref(false)
const showNewPassword = ref(false)
const selectedRole = ref('student')
const isLoading = ref(false)
const error = ref('')
const successMessage = ref('')

// 忘记密码弹窗
const showResetModal = ref(false)
const resetStep = ref(1) // 1: 输入邮箱和姓名, 2: 设置新密码
const resetLoading = ref(false)
const resetError = ref('')
const resetSuccess = ref(false)

const formData = ref({
  email: '',
  username: '',
  realName: '',
  password: '',
  confirmPassword: ''
})

const resetData = ref({
  email: '',
  realName: '',
  // 一次性重置令牌，仅在第一步校验通过后下发
  resetToken: '',
  newPassword: '',
  confirmNewPassword: ''
})

const registerRoles = [
  { id: 'student', label: '我是学生', icon: User, color: 'from-qinghua to-halanzi', description: '学习课程、完成作业' },
  { id: 'teacher', label: '我是教师', icon: Users, color: 'from-tianlv to-qingsong', description: '发布课程、管理学生' },
]

// 设置页面标题，检查是否从注册链接跳转
onMounted(() => {
  if (route.query.register === 'true') {
    isLogin.value = false
  }
})

// 如果已登录，跳转到对应页面
watch(() => authStore.isAuthenticated, (val) => {
  if (val && authStore.user) {
    const path = authStore.user.role === 'admin' ? '/admin' : authStore.user.role === 'teacher' ? '/teacher' : '/student'
    router.replace(path)
  }
}, { immediate: true })

const handleSubmit = async () => {
  isLoading.value = true
  error.value = ''
  successMessage.value = ''

  try {
    if (isLogin.value) {
      // 登录：使用邮箱+密码
      const result = await authAPI.login(formData.value.email, formData.value.password)
      authStore.login(result.data.token, result.data.user)
      
      const role = result.data.user.role
      if (role === 'admin') {
        router.replace('/admin')
      } else if (role === 'teacher') {
        router.replace('/teacher')
      } else {
        router.replace('/student')
      }
    } else {
      // 注册：邮箱+用户名+真实姓名+密码
      if (formData.value.password !== formData.value.confirmPassword) {
        error.value = '两次输入的密码不一致'
        isLoading.value = false
        return
      }

      if (!formData.value.email || !formData.value.email.includes('@')) {
        error.value = '请输入有效的邮箱地址'
        isLoading.value = false
        return
      }

      if (!formData.value.username || formData.value.username.length < 2) {
        error.value = '用户名至少需要2个字符'
        isLoading.value = false
        return
      }

      if (!formData.value.realName || formData.value.realName.length < 2) {
        error.value = '请输入真实姓名'
        isLoading.value = false
        return
      }

      const result = await authAPI.register(
        formData.value.email,
        formData.value.username,
        formData.value.realName,
        formData.value.password,
        selectedRole.value
      )
      authStore.login(result.data.token, result.data.user)

      if (selectedRole.value === 'teacher') {
        router.replace('/teacher')
      } else {
        router.replace('/student')
      }
    }
  } catch (err) {
    error.value = err.message || '操作失败，请稍后重试'
  } finally {
    isLoading.value = false
  }
}

// 打开忘记密码弹窗
const openResetModal = () => {
  showResetModal.value = true
  resetStep.value = 1
  resetError.value = ''
  resetSuccess.value = false
  resetData.value = {
    email: '',
    realName: '',
    resetToken: '',
    newPassword: '',
    confirmNewPassword: ''
  }
}

// 关闭忘记密码弹窗
const closeResetModal = () => {
  showResetModal.value = false
  resetStep.value = 1
  resetError.value = ''
  resetSuccess.value = false
}

// 处理密码重置
const handleResetPassword = async () => {
  resetLoading.value = true
  resetError.value = ''

  try {
    if (resetStep.value === 1) {
      // 验证邮箱和真实姓名
      if (!resetData.value.email || !resetData.value.email.includes('@')) {
        resetError.value = '请输入有效的邮箱地址'
        resetLoading.value = false
        return
      }
      if (!resetData.value.realName || resetData.value.realName.length < 2) {
        resetError.value = '请输入真实姓名'
        resetLoading.value = false
        return
      }

      // 第一步先向后端申请一次性重置令牌，避免直接暴露旧接口
      const issueResult = await authAPI.requestPasswordResetToken(
        resetData.value.email,
        resetData.value.realName
      )
      const token = issueResult?.data?.resetToken
      if (!token) {
        // 统一提示，避免暴露账号是否存在
        resetError.value = '请求已受理，请核对信息后重试'
        resetLoading.value = false
        return
      }
      resetData.value.resetToken = token

      // 令牌获取成功后进入下一步
      resetStep.value = 2
    } else {
      // 设置新密码
      if (!resetData.value.newPassword || resetData.value.newPassword.length < 6) {
        resetError.value = '密码至少需要6个字符'
        resetLoading.value = false
        return
      }
      if (resetData.value.newPassword !== resetData.value.confirmNewPassword) {
        resetError.value = '两次输入的密码不一致'
        resetLoading.value = false
        return
      }

      // 第二步使用一次性令牌完成密码重置
      await authAPI.confirmPasswordReset(
        resetData.value.resetToken,
        resetData.value.newPassword
      )
      
      resetSuccess.value = true
      setTimeout(() => {
        closeResetModal()
        // 自动填充邮箱
        formData.value.email = resetData.value.email
      }, 2000)
    }
  } catch (err) {
    const message = err?.message || ''
    if (message.includes('频繁')) {
      resetError.value = '操作过于频繁，请稍后再试'
    } else if (message.includes('令牌') || message.includes('无效') || message.includes('失效')) {
      resetError.value = '重置凭证已失效，请返回上一步重新申请'
    } else {
      resetError.value = message || '操作失败，请稍后重试'
    }
  } finally {
    resetLoading.value = false
  }
}

const clearError = () => {
  error.value = ''
}
</script>

<template>
  <div class="min-h-screen flex items-center justify-center p-6 relative overflow-hidden animate-fade-in">
    <!-- Background Decorations -->
    <div class="absolute inset-0 overflow-hidden">
      <div class="absolute -top-40 -left-40 w-80 h-80 bg-danqing/20 rounded-full blur-3xl animate-float" />
      <div class="absolute -bottom-40 -right-40 w-96 h-96 bg-qingbai/30 rounded-full blur-3xl animate-float" style="animation-delay: 1.5s" />
      <div class="absolute top-1/4 right-1/4 w-64 h-64 bg-zijinghui/10 rounded-full blur-3xl animate-float" style="animation-delay: 0.5s" />
    </div>

    <div class="w-full max-w-md relative z-10">
      <!-- Logo with hover animation -->
      <router-link to="/" class="flex items-center justify-center gap-3 mb-8 group logo-hover">
        <div class="w-12 h-12 rounded-2xl bg-gradient-to-br from-danqing to-qinghua flex items-center justify-center transition-transform duration-300 logo-icon">
          <GraduationCap class="w-7 h-7 text-white" />
        </div>
        <span class="text-2xl font-bold text-shuimo group-hover:text-danqing transition-colors">智慧课堂</span>
      </router-link>

      <!-- Main Card -->
      <GlassCard class="animate-scale-in" padding="p-6">
        <!-- Toggle -->
        <div class="flex bg-white/50 rounded-xl p-1 mb-5">
          <button
            type="button"
            @click="isLogin = true; showPassword = false; showConfirmPassword = false"
            :class="[
              'flex-1 py-2.5 rounded-lg font-medium transition-all duration-300 text-sm',
              isLogin ? 'bg-white shadow-md text-shuimo' : 'text-shuimo/80 hover:text-shuimo'
            ]"
            :aria-pressed="isLogin"
            aria-label="切换到登录表单"
          >
            登录
          </button>
          <button
            type="button"
            @click="isLogin = false; showPassword = false; showConfirmPassword = false"
            :class="[
              'flex-1 py-2.5 rounded-lg font-medium transition-all duration-300 text-sm',
              !isLogin ? 'bg-white shadow-md text-shuimo' : 'text-shuimo/80 hover:text-shuimo'
            ]"
            :aria-pressed="!isLogin"
            aria-label="切换到注册表单"
          >
            注册
          </button>
        </div>

        <!-- Role Selection - 仅注册时显示（紧凑版） -->
        <div v-if="!isLogin" class="mb-5 animate-slide-down">
          <div class="flex items-center gap-2 mb-2">
            <label class="text-sm font-medium text-shuimo/70">选择身份</label>
            <span class="text-xs text-shuimo/40">（管理员需后台创建）</span>
          </div>
          <div class="flex gap-2">
            <button
              v-for="role in registerRoles"
              :key="role.id"
              type="button"
              @click="selectedRole = role.id"
              :class="[
                'flex-1 px-4 py-2.5 rounded-xl border transition-all duration-300 flex items-center justify-center gap-2',
                selectedRole === role.id
                  ? `border-transparent bg-gradient-to-r ${role.color} text-white shadow-md`
                  : 'border-slate-200 bg-white/50 text-shuimo/70 hover:border-qinghua/30 hover:bg-white/80'
              ]"
            >
              <component :is="role.icon" class="w-4 h-4" />
              <span class="text-sm font-medium">{{ role.label }}</span>
            </button>
          </div>
        </div>

        <!-- Error Message with shake animation -->
        <div v-if="error" class="mb-4 p-3 rounded-xl bg-danger/10 border border-danger/30 flex items-center gap-2 text-danger text-sm animate-shake">
          <AlertCircle class="w-4 h-4 flex-shrink-0" />
          <span>{{ error }}</span>
        </div>

        <!-- Form -->
        <form @submit.prevent="handleSubmit" class="space-y-4">
          <!-- 登录表单 -->
          <template v-if="isLogin">
            <BaseInput
              v-model="formData.email"
              type="email"
              autocomplete="username"
              label="邮箱"
              placeholder="请输入邮箱登录"
              :icon="Mail"
              required
              @input="clearError"
            />
          </template>

          <!-- 注册表单 -->
          <template v-else>
            <!-- 邮箱和用户名并排 -->
            <div class="grid grid-cols-2 gap-3">
              <BaseInput
                v-model="formData.email"
                type="email"
                label="邮箱"
                placeholder="用于登录"
                :icon="Mail"
                required
                @input="clearError"
              />
              <BaseInput
                v-model="formData.username"
                label="用户名"
                placeholder="可随时修改"
                :icon="User"
                required
                @input="clearError"
              />
            </div>

            <BaseInput
              v-model="formData.realName"
              label="真实姓名"
              placeholder="注册后不可修改"
              :icon="User"
              required
              @input="clearError"
            />
          </template>

          <!-- 密码区域 -->
          <div :class="!isLogin ? 'grid grid-cols-2 gap-3' : ''">
            <BaseInput
              v-model="formData.password"
              :type="showPassword ? 'text' : 'password'"
              :autocomplete="isLogin ? 'current-password' : 'new-password'"
              label="密码"
              placeholder="请输入密码"
              :icon="Lock"
              required
              @input="clearError"
            >
              <template #suffix>
                <button
                  type="button"
                  @click="showPassword = !showPassword"
                  class="text-shuimo/40 hover:text-shuimo transition-colors eye-toggle"
                  tabindex="-1"
                  :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                >
                  <Eye v-if="showPassword" class="w-5 h-5" />
                  <EyeOff v-else class="w-5 h-5" />
                </button>
              </template>
            </BaseInput>

            <BaseInput
              v-if="!isLogin"
              v-model="formData.confirmPassword"
              :type="showConfirmPassword ? 'text' : 'password'"
              autocomplete="new-password"
              label="确认密码"
              placeholder="再次输入"
              :icon="Lock"
              required
              @input="clearError"
            >
              <template #suffix>
                <button
                  type="button"
                  @click="showConfirmPassword = !showConfirmPassword"
                  class="text-shuimo/40 hover:text-shuimo transition-colors eye-toggle"
                  tabindex="-1"
                  :aria-label="showConfirmPassword ? '隐藏确认密码' : '显示确认密码'"
                >
                  <Eye v-if="showConfirmPassword" class="w-5 h-5" />
                  <EyeOff v-else class="w-5 h-5" />
                </button>
              </template>
            </BaseInput>
          </div>

          <div v-if="isLogin" class="flex justify-end -mt-1">
            <BaseButton
              type="button"
              variant="text"
              size="sm"
              @click="openResetModal"
            >
              忘记密码？
            </BaseButton>
          </div>

          <BaseButton
            type="submit"
            :loading="isLoading"
            block
            :class="[
              isLogin ? 'shadow-qinghua/30' : (selectedRole === 'teacher' ? 'bg-gradient-to-r from-tianlv to-qingsong shadow-tianlv/30' : 'bg-gradient-to-r from-qinghua to-halanzi shadow-qinghua/30')
            ]"
          >
            {{ isLogin ? '登录' : '注册' }}
            <ArrowRight class="w-5 h-5 ml-2" v-if="!isLoading" />
          </BaseButton>
        </form>
      </GlassCard>
    </div>

    <!-- 忘记密码弹窗 -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showResetModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-shuimo/20 backdrop-blur-[2px]">
          <GlassCard class="w-full max-w-md animate-scale-in" padding="p-6">
            <!-- 标题 -->
            <div class="flex items-center justify-between mb-6">
              <h3 class="text-lg font-bold text-shuimo flex items-center gap-2">
                <KeyRound class="w-5 h-5 text-qinghua" />
                {{ resetSuccess ? '密码重置成功' : '重置密码' }}
              </h3>
              <button
                @click="closeResetModal"
                class="p-1 rounded-lg hover:bg-slate-100 text-shuimo/50 hover:text-shuimo transition-colors"
                aria-label="关闭重置密码弹窗"
              >
                <X class="w-5 h-5" />
              </button>
            </div>

            <!-- 成功状态 -->
            <div v-if="resetSuccess" class="text-center py-8">
              <div class="w-16 h-16 mx-auto rounded-full bg-tianlv/10 flex items-center justify-center mb-4 success-checkmark">
                <CheckCircle class="w-8 h-8 text-tianlv" />
              </div>
              <p class="text-shuimo font-medium">密码已重置成功！</p>
              <p class="text-sm text-shuimo/60 mt-2">正在返回登录页面...</p>
            </div>

            <!-- 表单 -->
            <form v-else @submit.prevent="handleResetPassword" class="space-y-5">
              <!-- 步骤指示 -->
              <div class="flex items-center justify-center gap-2 mb-4">
                <div :class="['w-8 h-8 rounded-full flex items-center justify-center text-sm font-bold', resetStep >= 1 ? 'bg-qinghua text-white' : 'bg-slate-200 text-shuimo/50']">1</div>
                <div class="w-8 h-0.5 bg-slate-200"></div>
                <div :class="['w-8 h-8 rounded-full flex items-center justify-center text-sm font-bold', resetStep >= 2 ? 'bg-qinghua text-white' : 'bg-slate-200 text-shuimo/50']">2</div>
              </div>

              <!-- 错误提示 -->
              <div v-if="resetError" class="p-3 rounded-xl bg-danger/10 border border-danger/30 flex items-center gap-2 text-danger text-sm">
                <AlertCircle class="w-4 h-4 flex-shrink-0" />
                <span>{{ resetError }}</span>
              </div>

              <!-- 步骤1：输入邮箱和真实姓名 -->
              <template v-if="resetStep === 1">
                <p class="text-sm text-shuimo/70 mb-4">请输入您注册时使用的邮箱和真实姓名进行身份验证</p>
                
                <BaseInput
                  v-model="resetData.email"
                  type="email"
                  autocomplete="email"
                  label="邮箱"
                  placeholder="请输入注册邮箱"
                  :icon="Mail"
                  required
                />

                <BaseInput
                  v-model="resetData.realName"
                  label="真实姓名"
                  placeholder="请输入注册时填写的真实姓名"
                  :icon="User"
                  required
                />
              </template>

              <!-- 步骤2：设置新密码 -->
              <template v-else>
                <p class="text-sm text-shuimo/70 mb-4">请设置您的新密码（若提示令牌失效，请返回上一步重新申请）</p>
                
                <BaseInput
                  v-model="resetData.newPassword"
                  :type="showNewPassword ? 'text' : 'password'"
                  autocomplete="new-password"
                  label="新密码"
                  placeholder="请输入新密码（至少6位）"
                  :icon="Lock"
                  required
                >
                  <template #suffix>
                    <button
                      type="button"
                      @click="showNewPassword = !showNewPassword"
                      class="text-shuimo/40 hover:text-shuimo transition-colors"
                      tabindex="-1"
                      :aria-label="showNewPassword ? '隐藏新密码' : '显示新密码'"
                    >
                      <Eye v-if="showNewPassword" class="w-5 h-5" />
                      <EyeOff v-else class="w-5 h-5" />
                    </button>
                  </template>
                </BaseInput>

                <BaseInput
                  v-model="resetData.confirmNewPassword"
                  type="password"
                  autocomplete="new-password"
                  label="确认新密码"
                  placeholder="请再次输入新密码"
                  :icon="Lock"
                  required
                />
              </template>

              <div class="flex gap-3">
                <BaseButton
                  v-if="resetStep === 2"
                  type="button"
                  variant="outline"
                  @click="resetStep = 1"
                  class="flex-1"
                >
                  上一步
                </BaseButton>
                <BaseButton
                  type="submit"
                  :loading="resetLoading"
                  :class="resetStep === 1 ? 'w-full' : 'flex-1'"
                >
                  {{ resetStep === 1 ? '下一步' : '重置密码' }}
                </BaseButton>
              </div>
            </form>
          </GlassCard>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* Error shake animation */
@keyframes shake {
  0%, 100% { transform: translateX(0); }
  10%, 30%, 50%, 70%, 90% { transform: translateX(-4px); }
  20%, 40%, 60%, 80% { transform: translateX(4px); }
}

.animate-shake {
  /* P1 第二批：登录页反馈时长压缩，保证 100ms 内有响应 */
  animation: shake var(--motion-duration-medium) var(--motion-ease-standard);
}

/* Enhanced input focus glow effect */
:deep(.base-input-wrapper:focus-within) {
  box-shadow: 0 0 0 3px rgba(var(--color-qinghua-rgb, 66, 133, 244), 0.15);
  border-color: var(--color-qinghua, #4285f4);
}

/* Button hover lift effect */
:deep(.base-button):hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

/* Role button selection pulse */
@keyframes selection-pulse {
  0% { box-shadow: 0 0 0 0 rgba(var(--color-qinghua-rgb, 66, 133, 244), 0.4); }
  70% { box-shadow: 0 0 0 8px rgba(var(--color-qinghua-rgb, 66, 133, 244), 0); }
  100% { box-shadow: 0 0 0 0 rgba(var(--color-qinghua-rgb, 66, 133, 244), 0); }
}

/* Toggle button slide indicator */
.toggle-indicator {
  transition: transform var(--motion-duration-medium) var(--motion-ease-standard);
}

/* Form field stagger animation */
@keyframes field-appear {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.form-field-animate {
  animation: field-appear var(--motion-duration-medium) var(--motion-ease-standard) forwards;
}

/* Success checkmark animation */
@keyframes checkmark-pop {
  0% { transform: scale(0); opacity: 0; }
  50% { transform: scale(1.2); }
  100% { transform: scale(1); opacity: 1; }
}

.success-checkmark {
  animation: checkmark-pop var(--motion-duration-medium) var(--motion-ease-standard);
}

/* Password visibility toggle animation */
@keyframes eye-blink {
  0%, 100% { transform: scaleY(1); }
  50% { transform: scaleY(0.1); }
}

.eye-toggle:active {
  animation: eye-blink 0.2s ease;
}

/* 无障碍：仅屏幕阅读器可见文本 */
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

/* Logo hover effect */
.logo-hover:hover .logo-icon {
  animation: logo-bounce var(--motion-duration-medium) var(--motion-ease-standard);
}

@keyframes logo-bounce {
  0%, 100% { transform: scale(1) rotate(0deg); }
  25% { transform: scale(1.1) rotate(-5deg); }
  50% { transform: scale(1.15) rotate(5deg); }
  75% { transform: scale(1.1) rotate(-3deg); }
}

/* Background floating animation enhancement */
@keyframes float-enhanced {
  0%, 100% { 
    transform: translateY(0) scale(1); 
    opacity: 0.2;
  }
  50% { 
    transform: translateY(-20px) scale(1.05); 
    opacity: 0.3;
  }
}

.animate-float {
  animation: float-enhanced var(--motion-duration-medium) var(--motion-ease-standard) infinite;
}
</style>
