<script setup>
/**
 * 空状态组件
 * 用于展示无数据、搜索无结果、加载失败等场景的占位状态。
 * 支持多种预设场景和自定义图标、文案、CTA按钮。
 *
 * @prop {String|Object} icon - 图标名称或组件
 * @prop {String} title - 标题文本
 * @prop {String} description - 描述文本
 * @prop {String} size - 尺寸: sm/md/lg
 * @prop {String} type - 预设场景类型: empty/search/error/offline/success
 * @prop {Boolean} animated - 是否启用动画
 * @prop {String} actionText - CTA按钮文本
 * @prop {Object} actionIcon - CTA按钮图标组件
 * @prop {String} actionVariant - CTA按钮样式变体: primary/secondary/ghost
 */
import {
  FileText, Users, MessageSquare, Bell, BookOpen,
  ClipboardCheck, Calendar, BarChart3, CheckCircle, AlertCircle,
  Search, WifiOff, RefreshCw, FileQuestion, Inbox, Package, Plus
} from 'lucide-vue-next'

const props = defineProps({
  icon: {
    type: [String, Object],
    default: null
  },
  title: {
    type: String,
    default: ''
  },
  description: {
    type: String,
    default: ''
  },
  size: {
    type: String,
    default: 'md',
    validator: (v) => ['sm', 'md', 'lg'].includes(v)
  },
  type: {
    type: String,
    default: 'empty',
    validator: (v) => ['empty', 'search', 'error', 'offline', 'success'].includes(v)
  },
  animated: {
    type: Boolean,
    default: true
  },
  actionText: {
    type: String,
    default: ''
  },
  actionIcon: {
    type: [Object, Function],
    default: null
  },
  actionVariant: {
    type: String,
    default: 'primary',
    validator: (v) => ['primary', 'secondary', 'ghost'].includes(v)
  }
})

const emit = defineEmits(['action'])

// 图标映射
const iconMap = {
  file: FileText,
  users: Users,
  message: MessageSquare,
  bell: Bell,
  book: BookOpen,
  clipboard: ClipboardCheck,
  calendar: Calendar,
  chart: BarChart3,
  check: CheckCircle,
  alert: AlertCircle,
  search: Search,
  wifi: WifiOff,
  refresh: RefreshCw,
  question: FileQuestion,
  inbox: Inbox,
  package: Package,
  plus: Plus
}

// 预设场景配置
const typePresets = {
  empty: {
    icon: 'inbox',
    title: '暂无数据',
    description: '当前列表为空',
    iconClass: 'text-shuimo/30',
    bgClass: 'bg-slate-50'
  },
  search: {
    icon: 'search',
    title: '未找到结果',
    description: '尝试调整搜索条件或关键词',
    iconClass: 'text-qinghua/40',
    bgClass: 'bg-qinghua/5'
  },
  error: {
    icon: 'alert',
    title: '加载失败',
    description: '请稍后重试或联系管理员',
    iconClass: 'text-yanzhi/50',
    bgClass: 'bg-yanzhi/5'
  },
  offline: {
    icon: 'wifi',
    title: '网络连接失败',
    description: '请检查网络连接后重试',
    iconClass: 'text-zhizi/60',
    bgClass: 'bg-zhizi/5'
  },
  success: {
    icon: 'check',
    title: '操作成功',
    description: '',
    iconClass: 'text-tianlv/60',
    bgClass: 'bg-tianlv/5'
  }
}

// 获取当前使用的配置
const currentPreset = typePresets[props.type] || typePresets.empty

// 确定最终使用的图标
const finalIcon = props.icon
  ? (typeof props.icon === 'string' ? iconMap[props.icon] : props.icon)
  : iconMap[currentPreset.icon]

// 确定最终使用的标题和描述
const finalTitle = props.title || currentPreset.title
const finalDescription = props.description || currentPreset.description

// 尺寸配置
const sizeClasses = {
  sm: { icon: 'w-8 h-8', container: 'w-14 h-14', text: 'text-sm', descText: 'text-xs', padding: 'py-6', btn: 'px-3 py-1.5 text-xs' },
  md: { icon: 'w-12 h-12', container: 'w-20 h-20', text: 'text-base', descText: 'text-sm', padding: 'py-10', btn: 'px-4 py-2 text-sm' },
  lg: { icon: 'w-16 h-16', container: 'w-28 h-28', text: 'text-lg', descText: 'text-base', padding: 'py-16', btn: 'px-5 py-2.5 text-base' }
}

// CTA按钮样式
const buttonVariants = {
  primary: 'bg-gradient-to-r from-tianlv to-qingsong text-white shadow-lg shadow-tianlv/20 hover:shadow-xl hover:-translate-y-0.5',
  secondary: 'bg-white text-shuimo border border-slate-200 hover:border-tianlv/30 hover:bg-slate-50',
  ghost: 'text-tianlv hover:bg-tianlv/10'
}

// 动画类
const animationClass = props.animated ? 'animate-slide-up' : ''
const floatClass = props.animated ? 'empty-state-float' : ''

// 处理CTA点击
const handleAction = () => {
  emit('action')
}
</script>

<template>
  <div
    :class="[
      'flex flex-col items-center justify-center text-center',
      sizeClasses[size].padding,
      animationClass
    ]"
    style="animation-fill-mode: both;"
  >
    <!-- 图标容器 - 增强的渐变背景 -->
    <div
      :class="[
        'rounded-full flex items-center justify-center mb-5 transition-all relative',
        sizeClasses[size].container,
        floatClass
      ]"
    >
      <!-- 背景渐变装饰 -->
      <div :class="['absolute inset-0 rounded-full', currentPreset.bgClass, 'opacity-60']"></div>
      <div class="absolute inset-1 rounded-full bg-gradient-to-br from-white/80 to-transparent"></div>
      <component
        :is="finalIcon || FileText"
        :class="[currentPreset.iconClass, sizeClasses[size].icon, 'relative z-10']"
      />
    </div>

    <!-- 标题 -->
    <h3 :class="['text-shuimo/80 font-semibold', sizeClasses[size].text]">
      {{ finalTitle }}
    </h3>

    <!-- 描述 -->
    <p
      v-if="finalDescription"
      :class="['text-shuimo/50 mt-2 max-w-xs mx-auto leading-relaxed', sizeClasses[size].descText]"
    >
      {{ finalDescription }}
    </p>

    <!-- CTA按钮 -->
    <button
      v-if="actionText"
      @click="handleAction"
      :class="[
        'mt-5 rounded-xl font-medium flex items-center gap-2 transition-all duration-300',
        sizeClasses[size].btn,
        buttonVariants[actionVariant]
      ]"
    >
      <component v-if="actionIcon" :is="actionIcon" class="w-4 h-4" />
      {{ actionText }}
    </button>

    <!-- 操作按钮插槽 -->
    <div v-if="$slots.action" class="mt-5">
      <slot name="action" />
    </div>

    <!-- 自定义内容插槽 -->
    <div v-if="$slots.default" class="mt-4">
      <slot />
    </div>
  </div>
</template>

<style scoped>
/* 悬浮动画 */
.empty-state-float {
  /* P1 第二批：空状态动画统一 200ms 档 */
  animation: hover-float var(--motion-duration-medium) var(--motion-ease-standard) infinite;
}

@keyframes hover-float {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-8px);
  }
}

/* 错误状态的图标抖动动画 */
.empty-state-error .empty-state-float {
  animation:
    shake-gentle var(--motion-duration-medium) var(--motion-ease-standard),
    hover-float var(--motion-duration-medium) var(--motion-ease-standard) infinite var(--motion-duration-medium);
}

@keyframes shake-gentle {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-3px); }
  75% { transform: translateX(3px); }
}

/* 成功状态的图标缩放动画 */
.empty-state-success .empty-state-float {
  animation:
    scale-bounce var(--motion-duration-medium) var(--motion-ease-standard),
    hover-float var(--motion-duration-medium) var(--motion-ease-standard) infinite var(--motion-duration-medium);
}

@keyframes scale-bounce {
  0% { transform: scale(0.8); opacity: 0; }
  50% { transform: scale(1.1); }
  100% { transform: scale(1); opacity: 1; }
}
</style>
