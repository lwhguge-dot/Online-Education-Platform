import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Ref, ComputedRef } from 'vue'
import { getAuth, saveAuth, clearAuth } from '../services/api'
import type { User } from '../types/api'
import SentryService from '../utils/sentry'

export const useAuthStore = defineStore('auth', () => {
  const user: Ref<User | null> = ref(null)
  const token: Ref<string | null> = ref(null)
  const loading: Ref<boolean> = ref(true)

  const isAuthenticated: ComputedRef<boolean> = computed(() => !!token.value && !!user.value)

  function init(): void {
    const auth = getAuth()
    if (auth.token && auth.user) {
      token.value = auth.token
      user.value = auth.user
      // 设置 Sentry 用户上下文
      SentryService.setUser({
        id: auth.user.id,
        username: auth.user.username,
        email: auth.user.email,
      })
    }
    loading.value = false
  }

  function login(newToken: string, newUser: User): void {
    saveAuth(newToken, newUser)
    token.value = newToken
    user.value = newUser
    // 设置 Sentry 用户上下文
    SentryService.setUser({
      id: newUser.id,
      username: newUser.username,
      email: newUser.email,
    })
    // 记录登录事件
    SentryService.addBreadcrumb({
      message: 'User logged in',
      category: 'auth',
      level: 'info',
    })
  }

  function logout(): void {
    clearAuth()
    token.value = null
    user.value = null
    // 清除 Sentry 用户上下文
    SentryService.setUser(null)
    // 记录登出事件
    SentryService.addBreadcrumb({
      message: 'User logged out',
      category: 'auth',
      level: 'info',
    })
  }

  // 更新用户信息（不改变token）
  function updateUser(updatedFields: Partial<User>): void {
    if (user.value) {
      user.value = { ...user.value, ...updatedFields }
      // 同步到sessionStorage
      saveAuth(token.value!, user.value)
      // 更新 Sentry 用户上下文
      SentryService.setUser({
        id: user.value.id,
        username: user.value.username,
        email: user.value.email,
      })
    }
  }

  return {
    user,
    token,
    loading,
    isAuthenticated,
    init,
    login,
    logout,
    updateUser
  }
})
