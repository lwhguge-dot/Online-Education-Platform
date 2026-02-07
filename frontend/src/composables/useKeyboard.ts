/**
 * 键盘快捷键 Composable
 * 提供全局快捷键注册和管理功能，支持管理员中心的常用操作。
 *
 * 快捷键列表:
 * - Ctrl+K: 打开全局搜索
 * - Esc: 关闭弹窗/返回
 * - 1-6: 切换菜单项
 * - R: 刷新当前页面数据
 * - T: 切换主题
 */
import { ref, onMounted, onUnmounted } from 'vue'
import type { Ref } from 'vue'

export interface KeyboardOptions {
  onSearch?: (() => void) | null
  onEscape?: (() => void) | null
  onRefresh?: (() => void) | null
  onMenuSwitch?: ((index: number) => void) | null
  onThemeToggle?: (() => void) | null
  enabled?: boolean
}

export interface KeyboardModifiers {
  ctrl: boolean
  shift: boolean
  alt: boolean
  meta: boolean
}

export interface UseKeyboardReturn {
  isEnabled: Ref<boolean>
  modifiers: Ref<KeyboardModifiers>
  enable: () => void
  disable: () => void
}

/**
 * 快捷键 Composable Hook
 * @param {Object} options - 配置选项
 * @param {Function} options.onSearch - Ctrl+K 搜索回调
 * @param {Function} options.onEscape - Esc 关闭回调
 * @param {Function} options.onRefresh - R 刷新回调
 * @param {Function} options.onMenuSwitch - 数字键菜单切换回调
 * @param {Function} options.onThemeToggle - T 主题切换回调
 * @param {Boolean} options.enabled - 是否启用快捷键（默认 true）
 */
export function useKeyboard(options: KeyboardOptions = {}): UseKeyboardReturn {
  const {
    onSearch = null,
    onEscape = null,
    onRefresh = null,
    onMenuSwitch = null,
    onThemeToggle = null,
    enabled = true
  } = options

  // 是否启用快捷键
  const isEnabled = ref(enabled)

  // 当前按下的修饰键状态
  const modifiers: Ref<KeyboardModifiers> = ref({
    ctrl: false,
    shift: false,
    alt: false,
    meta: false
  })

  /**
   * 检查是否在输入元素中
   * 在输入框、文本域中时不触发快捷键
   */
  const isInInputElement = (): boolean => {
    const activeElement = document.activeElement
    if (!activeElement) return false

    const tagName = activeElement.tagName.toLowerCase()
    const isContentEditable = (activeElement as HTMLElement).contentEditable === 'true'

    return ['input', 'textarea', 'select'].includes(tagName) || isContentEditable
  }

  /**
   * 处理键盘按下事件
   */
  const handleKeyDown = (event: KeyboardEvent): void => {
    if (!isEnabled.value) return

    // 更新修饰键状态
    modifiers.value = {
      ctrl: event.ctrlKey || event.metaKey,
      shift: event.shiftKey,
      alt: event.altKey,
      meta: event.metaKey
    }

    const key = event.key.toLowerCase()
    const { ctrl, shift } = modifiers.value

    // Ctrl+K: 打开搜索
    if (ctrl && key === 'k') {
      event.preventDefault()
      onSearch?.()
      return
    }

    // Esc: 关闭弹窗
    if (key === 'escape') {
      event.preventDefault()
      onEscape?.()
      return
    }

    // 在输入元素中时，不处理以下快捷键
    if (isInInputElement()) return

    // R: 刷新数据
    if (key === 'r' && !ctrl && !shift) {
      event.preventDefault()
      onRefresh?.()
      return
    }

    // T: 切换主题
    if (key === 't' && !ctrl && !shift) {
      event.preventDefault()
      onThemeToggle?.()
      return
    }

    // 数字键 1-6: 切换菜单
    if (/^[1-6]$/.test(key) && !ctrl && !shift) {
      event.preventDefault()
      const menuIndex = parseInt(key) - 1
      onMenuSwitch?.(menuIndex)
      return
    }
  }

  /**
   * 处理键盘松开事件
   */
  const handleKeyUp = (event: KeyboardEvent): void => {
    modifiers.value = {
      ctrl: event.ctrlKey || event.metaKey,
      shift: event.shiftKey,
      alt: event.altKey,
      meta: event.metaKey
    }
  }

  /**
   * 启用快捷键
   */
  const enable = (): void => {
    isEnabled.value = true
  }

  /**
   * 禁用快捷键
   */
  const disable = (): void => {
    isEnabled.value = false
  }

  // 组件挂载时注册事件监听
  onMounted(() => {
    window.addEventListener('keydown', handleKeyDown)
    window.addEventListener('keyup', handleKeyUp)
  })

  // 组件卸载时移除事件监听
  onUnmounted(() => {
    window.removeEventListener('keydown', handleKeyDown)
    window.removeEventListener('keyup', handleKeyUp)
  })

  return {
    isEnabled,
    modifiers,
    enable,
    disable
  }
}

/**
 * 快捷键提示配置
 * 用于在界面上显示可用的快捷键列表
 */
export const keyboardShortcuts = [
  { key: 'Ctrl+K', description: '打开全局搜索', icon: 'Search' },
  { key: 'Esc', description: '关闭弹窗/返回', icon: 'X' },
  { key: '1-6', description: '切换菜单项', icon: 'Menu' },
  { key: 'R', description: '刷新当前页面', icon: 'RefreshCw' },
  { key: 'T', description: '切换主题', icon: 'Moon' }
]

export default useKeyboard
