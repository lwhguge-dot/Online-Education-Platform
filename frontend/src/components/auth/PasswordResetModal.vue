<script setup lang="ts">
import { ref } from 'vue'
import { authAPI } from '../../services/api'
import BaseInput from '../ui/BaseInput.vue'
import BaseButton from '../ui/BaseButton.vue'
import GlassCard from '../ui/GlassCard.vue'
import { KeyRound, X, CheckCircle, AlertCircle, Lock, Eye, EyeOff, Mail, User } from 'lucide-vue-next'

interface Props {
  modelValue: boolean
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
  (e: 'reset-success', email: string): void
}

defineProps<Props>()
const emit = defineEmits<Emits>()

const showNewPassword = ref(false)
const resetStep = ref(1)
const resetLoading = ref(false)
const resetError = ref('')
const resetSuccess = ref(false)

const resetData = ref({
  email: '',
  realName: '',
  resetToken: '',
  newPassword: '',
  confirmNewPassword: ''
})

const close = () => {
  emit('update:modelValue', false)
  resetStep.value = 1
  resetError.value = ''
  resetSuccess.value = false
}

const handleResetPassword = async () => {
  resetLoading.value = true
  resetError.value = ''

  try {
    if (resetStep.value === 1) {
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

      const issueResult = await authAPI.requestPasswordResetToken(
        resetData.value.email,
        resetData.value.realName
      )
      const token = issueResult?.data?.resetToken
      if (!token) {
        resetError.value = '请求已受理，请核对信息后重试'
        resetLoading.value = false
        return
      }
      resetData.value.resetToken = token
      resetStep.value = 2
    } else {
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

      await authAPI.confirmPasswordReset(
        resetData.value.resetToken,
        resetData.value.newPassword
      )
      
      resetSuccess.value = true
      setTimeout(() => {
        emit('reset-success', resetData.value.email)
        close()
      }, 2000)
    }
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : ''
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
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="modelValue" role="dialog" aria-modal="true" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-shuimo/20 backdrop-blur-[2px]">
        <GlassCard class="w-full max-w-md animate-scale-in" padding="p-6">
          <!-- 标题 -->
          <div class="flex items-center justify-between mb-6">
            <h3 class="text-lg font-bold text-shuimo flex items-center gap-2">
              <KeyRound class="w-5 h-5 text-qinghua" />
              {{ resetSuccess ? '密码重置成功' : '重置密码' }}
            </h3>
            <button
              @click="close"
              data-testid="reset-password-close"
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

.success-checkmark {
  animation: checkpop 0.3s ease-out;
}

@keyframes checkpop {
  0% { transform: scale(0); }
  50% { transform: scale(1.2); }
  100% { transform: scale(1); }
}
</style>
