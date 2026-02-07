/**
 * 主题切换 Composable
 * 管理应用的亮色/暗黑模式切换，支持本地存储持久化和跟随系统设置。
 */
import { ref, onMounted, onUnmounted } from 'vue'

// 主题模式: 'light' | 'dark' | 'system'
const theme = ref('system')
const isDark = ref(false)

// 本地存储键名
const STORAGE_KEY = 'edu-platform-theme'
const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
let mediaListenerRegistered = false

// 系统主题变化处理器（用于注册与清理）
const handleSystemThemeChange = (event: MediaQueryListEvent) => {
  if (theme.value === 'system') {
    applyTheme(event.matches)
  }
}

/**
 * 应用主题到 DOM
 * @param {Boolean} dark - 是否为暗黑模式
 */
const applyTheme = (dark) => {
  isDark.value = dark
  if (dark) {
    document.documentElement.classList.add('dark')
  } else {
    document.documentElement.classList.remove('dark')
  }
}

/**
 * 获取系统主题偏好
 * @returns {Boolean} 是否为暗黑模式
 */
const getSystemTheme = () => {
  return mediaQuery.matches
}

/**
 * 根据当前主题设置更新实际显示的主题
 */
const updateTheme = () => {
  if (theme.value === 'system') {
    applyTheme(getSystemTheme())
  } else {
    applyTheme(theme.value === 'dark')
  }
}

/**
 * 初始化主题
 * 从本地存储读取主题设置，如果没有则使用系统设置
 */
const initTheme = () => {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored && ['light', 'dark', 'system'].includes(stored)) {
    theme.value = stored
  } else {
    theme.value = 'system'
  }
  updateTheme()

  // 监听系统主题变化
  if (!mediaListenerRegistered) {
    mediaQuery.addEventListener('change', handleSystemThemeChange)
    mediaListenerRegistered = true
  }
}

/**
 * 设置主题
 * @param {String} newTheme - 新主题: 'light' | 'dark' | 'system'
 */
const setTheme = (newTheme) => {
  if (!['light', 'dark', 'system'].includes(newTheme)) {
    console.warn(`无效的主题值: ${newTheme}`)
    return
  }
  theme.value = newTheme
  localStorage.setItem(STORAGE_KEY, newTheme)
  updateTheme()
}

/**
 * 切换主题（在亮色和暗黑之间循环）
 */
const toggleTheme = () => {
  if (isDark.value) {
    setTheme('light')
  } else {
    setTheme('dark')
  }
}

/**
 * 主题切换 Composable Hook
 */
export function useTheme() {
  onMounted(() => {
    initTheme()
  })

  // 清理系统主题监听，避免重复注册导致内存泄漏
  onUnmounted(() => {
    if (!mediaListenerRegistered) return
    mediaQuery.removeEventListener('change', handleSystemThemeChange)
    mediaListenerRegistered = false
  })

  return {
    theme,        // 当前主题设置: 'light' | 'dark' | 'system'
    isDark,       // 实际是否为暗黑模式
    setTheme,     // 设置主题
    toggleTheme   // 切换主题
  }
}

export default useTheme
