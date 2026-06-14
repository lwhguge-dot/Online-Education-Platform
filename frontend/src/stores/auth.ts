import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Ref, ComputedRef } from 'vue'
import { getAuth, saveAuth, clearAuth } from '../services/api'
import type { User } from '../types/api'
import type { SessionUser } from '../services/request'
import SentryService from '../utils/sentry'
import { useStudentCourseStore } from './student-courses'
import { useStudentHomeworkStore } from './student-homeworks'
import { useStudentStatsStore } from './student-stats'

export const useAuthStore = defineStore('auth', () => {
  const user: Ref<SessionUser | null> = ref(null)
  const token: Ref<string | null> = ref(null)
  const loading: Ref<boolean> = ref(true)

  const isAuthenticated: ComputedRef<boolean> = computed(() => !!token.value && !!user.value)

  function init(): void {
    const auth = getAuth()
    if (auth.token && auth.user) {
      token.value = auth.token
      user.value = auth.user
      SentryService.setUser({
        id: auth.user.id,
        username: auth.user.username,
      })
    }
    loading.value = false
  }

  function login(newToken: string, newUser: User): void {
    saveAuth(newToken, newUser)
    const sessionUser: SessionUser = {
      id: newUser.id,
      username: newUser.username,
      name: newUser.name,
      role: newUser.role,
      avatar: newUser.avatar,
    }
    token.value = newToken
    user.value = sessionUser
    SentryService.setUser({
      id: newUser.id,
      username: newUser.username,
    })
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

    // 清除关联 Store 缓存
    try {
      useStudentCourseStore().reset()
      useStudentHomeworkStore().reset()
      useStudentStatsStore().reset()
    } catch {
      // Store 可能未初始化，忽略
    }

    SentryService.setUser(null)
    SentryService.addBreadcrumb({
      message: 'User logged out',
      category: 'auth',
      level: 'info',
    })
  }

  function updateUser(updatedFields: Partial<SessionUser>): void {
    if (user.value) {
      user.value = { ...user.value, ...updatedFields }
      saveAuth(token.value!, user.value as User)
      SentryService.setUser({
        id: user.value.id,
        username: user.value.username,
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
