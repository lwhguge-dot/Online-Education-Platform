<script setup>
import { ref, watch, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import BaseInput from '../components/ui/BaseInput.vue'
import BaseButton from '../components/ui/BaseButton.vue'
import GlassCard from '../components/ui/GlassCard.vue'
import PasswordResetModal from '../components/ui/PasswordResetModal.vue'
import { 
  GraduationCap, 
  Lock, 
  Eye, 
  EyeOff, 
  ArrowRight,
  User,
  Users,
  AlertCircle,
  Mail
} from 'lucide-vue-next'
import { authAPI } from '../services/api'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const isLogin = ref(true)
const showPassword = ref(false)
const showConfirmPassword = ref(false)
const selectedRole = ref('student')
const isLoading = ref(false)
const error = ref('')
const successMessage = ref('')
const showResetModal = ref(false)

const openResetModal = () => {
  showResetModal.value = true
}

const formData = ref({
  email: '',
  username: '',
  realName: '',
  password: '',
  confirmPassword: ''
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
            data-testid="tab-login"
            :class="[
              'flex-1 py-2.5 rounded-lg font-medium transition-[background-color,color,box-shadow] duration-300 text-sm',
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
            data-testid="tab-register"
            :class="[
              'flex-1 py-2.5 rounded-lg font-medium transition-[background-color,color,box-shadow] duration-300 text-sm',
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
              :data-testid="`role-${role.id}`"
              :class="[
                'flex-1 px-4 py-2.5 rounded-xl border transition-[transform,background-color,color,border-color,box-shadow] duration-300 flex items-center justify-center gap-2',
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
              data-testid="forgot-password"
              @click="openResetModal"
            >
              忘记密码？
            </BaseButton>
          </div>

          <BaseButton
            type="submit"
            :loading="isLoading"
            block
            :data-testid="isLogin ? 'login-submit' : 'register-submit'"
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
    <PasswordResetModal 
      v-model="showResetModal"
      @reset-success="(email) => formData.email = email"
    />
  </div>
</template>

<style scoped>
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
  /* 中文注释：装饰浮动改为有限次播放，降低长驻页面持续重绘 */
  animation: float-enhanced var(--motion-duration-loop-slow) var(--motion-ease-standard) 3 both;
}

@media (prefers-reduced-motion: reduce) {
  .animate-float {
    animation: none !important;
  }

  .logo-hover:hover .logo-icon {
    animation: none !important;
  }
}
</style>
