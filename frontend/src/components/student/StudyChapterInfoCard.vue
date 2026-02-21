<script setup lang="ts">
import { CheckCircle, Clock } from 'lucide-vue-next'
import AnimatedNumber from '../ui/AnimatedNumber.vue'

interface StudyChapterDetail {
  id: number | string
  title?: string
  description?: string
  completed?: boolean
  progress?: number
  videoDuration?: number
}

withDefaults(
  defineProps<{
    chapter: StudyChapterDetail | null
    videoDuration?: number
  }>(),
  {
    chapter: null,
    videoDuration: 0,
  },
)

// 统一进度展示范围，防止异常值破坏进度条渲染
const clampPercent = (value: number): number => {
  if (!Number.isFinite(value)) {
    return 0
  }
  return Math.min(100, Math.max(0, value))
}

// 统一章节时长显示格式，保持与播放器提示一致
const formatDuration = (seconds: number): string => {
  if (!seconds) {
    return '0分00秒'
  }
  const totalSeconds = Math.floor(seconds)
  const mins = Math.floor(totalSeconds / 60)
  const secs = totalSeconds % 60
  return `${mins}分${secs.toString().padStart(2, '0')}秒`
}
</script>

<template>
  <Transition name="chapter-info" mode="out-in">
    <div :key="chapter?.id" class="glass-card rounded-2xl p-6">
      <div class="flex items-start justify-between gap-4">
        <div>
          <h2 class="text-xl font-bold text-shuimo mb-2 font-song">{{ chapter?.title }}</h2>
          <p class="text-shuimo/60 text-sm leading-relaxed">{{ chapter?.description || '暂无章节描述' }}</p>
        </div>
        <Transition name="badge-pop">
          <div v-if="chapter?.completed" class="flex-shrink-0 px-3 py-1 bg-qingsong/10 text-qingsong rounded-lg text-sm font-medium flex items-center gap-1.5 completed-badge">
            <CheckCircle class="w-4 h-4" />
            已学完
          </div>
        </Transition>
      </div>

      <div class="mt-6 flex items-center gap-6 text-sm text-shuimo/50 pt-4 border-t border-slate-100/50">
        <span class="flex items-center gap-2">
          <Clock class="w-4 h-4" />
          时长：{{ formatDuration(videoDuration || chapter?.videoDuration || 0) }}
        </span>
        <span class="flex items-center gap-2">
          <div class="w-20 h-2 bg-slate-100 rounded-full overflow-hidden">
            <div class="h-full bg-gradient-to-r from-qinghua to-halanzi transition-all duration-500" :style="{ width: clampPercent(chapter?.progress || 0) + '%' }"></div>
          </div>
          <AnimatedNumber :value="clampPercent(chapter?.progress || 0)" :duration="300" />%
        </span>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.chapter-info-enter-active {
  animation: chapter-info-in var(--motion-duration-medium) var(--motion-ease-standard);
}

.chapter-info-leave-active {
  animation: chapter-info-out var(--motion-duration-medium) var(--motion-ease-standard);
}

@keyframes chapter-info-in {
  from {
    opacity: 0;
    transform: translateY(15px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes chapter-info-out {
  from {
    opacity: 1;
    transform: translateY(0);
  }
  to {
    opacity: 0;
    transform: translateY(-10px);
  }
}

.badge-pop-enter-active {
  animation: badge-pop-in var(--motion-duration-medium) var(--motion-ease-standard);
}

.badge-pop-leave-active {
  animation: badge-pop-out 0.2s ease-in;
}

@keyframes badge-pop-in {
  0% {
    opacity: 0;
    transform: scale(0.5);
  }
  70% {
    transform: scale(1.1);
  }
  100% {
    opacity: 1;
    transform: scale(1);
  }
}

@keyframes badge-pop-out {
  from {
    opacity: 1;
    transform: scale(1);
  }
  to {
    opacity: 0;
    transform: scale(0.8);
  }
}

.completed-badge {
  animation: badge-glow var(--motion-duration-medium) var(--motion-ease-standard) infinite;
}

@keyframes badge-glow {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(var(--color-qingsong), 0);
  }
  50% {
    box-shadow: 0 0 10px 2px rgba(var(--color-qingsong), 0.2);
  }
}
</style>
