<script setup lang="ts">
import { computed } from 'vue'
import { CheckCircle, Lock } from 'lucide-vue-next'

interface StudyChapterItem {
  id: number | string
  title: string
  unlocked: boolean
  completed: boolean
  progress: number
}

const props = withDefaults(
  defineProps<{
    chapters: StudyChapterItem[]
    currentChapterId?: number | string | null
  }>(),
  {
    currentChapterId: null,
  },
)

const emit = defineEmits<{
  (e: 'select', chapter: StudyChapterItem): void
}>()

// 统一统计已完成章节数，避免模板重复计算
const completedCount = computed<number>(() => props.chapters.filter((chapter) => chapter.completed).length)

const normalizeProgress = (value: number): number => {
  if (!Number.isFinite(value)) {
    return 0
  }
  return Math.min(100, Math.max(0, value))
}

const handleSelect = (chapter: StudyChapterItem): void => {
  emit('select', chapter)
}
</script>

<template>
  <div class="lg:col-span-1 flex flex-col h-full animate-slide-up" style="animation-delay: 0.1s;">
    <div class="glass-card rounded-2xl overflow-hidden flex flex-col h-[calc(100vh-140px)] sticky top-24">
      <div class="p-5 border-b border-white/50 bg-white/30 backdrop-blur-md">
        <h3 class="font-bold text-shuimo flex items-center gap-2">
          <div class="w-1 h-4 bg-qinghua rounded-full"></div>
          课程目录
        </h3>
        <p class="text-xs text-shuimo/50 mt-1 pl-3">
          共 {{ chapters.length }} 章节 · 已完成 {{ completedCount }} 章
        </p>
      </div>

      <div class="flex-1 overflow-y-auto p-3 space-y-2 custom-scrollbar">
        <TransitionGroup name="chapter-list">
          <div
            v-for="(chapter, idx) in chapters"
            :key="chapter.id"
            @click="handleSelect(chapter)"
            class="chapter-item group p-4 rounded-xl transition-all duration-300 border border-transparent relative overflow-hidden"
            :class="{
              'bg-qinghua/5 border-qinghua/20 shadow-sm active-chapter cursor-pointer': currentChapterId === chapter.id,
              'hover:bg-white/60 hover:shadow-sm cursor-pointer': currentChapterId !== chapter.id && chapter.unlocked,
              'opacity-60 cursor-not-allowed bg-slate-50/50': !chapter.unlocked,
            }"
          >
            <Transition name="indicator-slide">
              <div v-if="currentChapterId === chapter.id" class="absolute left-0 top-0 bottom-0 w-1 bg-qinghua"></div>
            </Transition>

            <div class="flex items-start gap-3 relative z-10">
              <div
                class="chapter-icon w-6 h-6 flex-shrink-0 mt-0.5 rounded-full flex items-center justify-center text-xs font-bold transition-all duration-300"
                :class="chapter.completed ? 'bg-qingsong/10 text-qingsong' : (currentChapterId === chapter.id ? 'bg-qinghua text-white scale-110' : 'bg-slate-200 text-slate-500')"
              >
                <CheckCircle v-if="chapter.completed" class="w-4 h-4 check-icon" />
                <Lock v-else-if="!chapter.unlocked" class="w-3 h-3" />
                <span v-else>{{ idx + 1 }}</span>
              </div>

              <div class="flex-1 min-w-0">
                <h4 class="text-sm font-medium truncate transition-colors" :class="currentChapterId === chapter.id ? 'text-qinghua' : 'text-shuimo'">
                  {{ chapter.title }}
                </h4>

                <div v-if="chapter.unlocked" class="flex items-center gap-2 mt-2">
                  <div class="flex-1 h-1 bg-slate-100 rounded-full overflow-hidden">
                    <div
                      class="h-full bg-gradient-to-r from-qinghua to-halanzi rounded-full transition-all duration-500"
                      :style="{ width: normalizeProgress(chapter.progress) + '%' }"
                    ></div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </TransitionGroup>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chapter-list-move {
  transition: transform var(--motion-duration-medium) var(--motion-ease-standard);
}

.indicator-slide-enter-active {
  animation: indicator-in var(--motion-duration-medium) var(--motion-ease-standard);
}

.indicator-slide-leave-active {
  animation: indicator-out 0.2s ease-in;
}

@keyframes indicator-in {
  from {
    transform: scaleY(0);
    opacity: 0;
  }
  to {
    transform: scaleY(1);
    opacity: 1;
  }
}

@keyframes indicator-out {
  from {
    transform: scaleY(1);
    opacity: 1;
  }
  to {
    transform: scaleY(0);
    opacity: 0;
  }
}

.active-chapter {
  animation: chapter-active var(--motion-duration-medium) var(--motion-ease-standard);
}

@keyframes chapter-active {
  0% {
    transform: scale(1);
  }
  50% {
    transform: scale(0.98);
  }
  100% {
    transform: scale(1);
  }
}

.check-icon {
  animation: check-pop var(--motion-duration-medium) var(--motion-ease-standard);
}

@keyframes check-pop {
  0% {
    transform: scale(0) rotate(-45deg);
    opacity: 0;
  }
  60% {
    transform: scale(1.2) rotate(0deg);
  }
  100% {
    transform: scale(1) rotate(0deg);
    opacity: 1;
  }
}

.custom-scrollbar::-webkit-scrollbar {
  width: 4px;
}

.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}

.custom-scrollbar::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.1);
  border-radius: 2px;
}

.custom-scrollbar::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 0, 0, 0.2);
}
</style>
