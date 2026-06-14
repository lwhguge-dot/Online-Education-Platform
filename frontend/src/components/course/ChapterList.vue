<script setup lang="ts">
import { Clock, BookOpen } from 'lucide-vue-next'

interface Chapter {
  id: number
  title: string
  description: string
  videoDuration: number
}

defineProps<{
  chapters: Chapter[]
  interactive: boolean
}>()

const emit = defineEmits<{
  select: []
}>()

const formatDuration = (seconds: number) => {
  if (!seconds) return '0分钟'
  const totalSeconds = Math.floor(seconds)
  const mins = Math.floor(totalSeconds / 60)
  const secs = totalSeconds % 60
  if (mins === 0) return `${secs}秒`
  if (secs === 0) return `${mins}分钟`
  return `${mins}分${secs}秒`
}
</script>

<template>
  <div class="glass-card rounded-2xl p-8">
    <h2 class="text-xl font-bold text-shuimo mb-6 flex items-center gap-2">
      <div class="w-1 h-6 bg-qinghua rounded-full" />
      课程章节
      <span v-if="chapters.length > 0" class="ml-2 text-sm font-normal text-shuimo/50">共 {{ chapters.length }} 章</span>
    </h2>

    <div v-if="chapters.length === 0" class="text-center py-12">
      <div class="w-16 h-16 bg-slate-100 rounded-full flex items-center justify-center mx-auto mb-4">
        <BookOpen class="w-8 h-8 text-slate-400" />
      </div>
      <p class="text-shuimo/50">暂无章节内容</p>
    </div>

    <div v-else class="space-y-4">
      <TransitionGroup name="chapter-list" appear>
        <div v-for="(chapter, index) in chapters" :key="chapter.id"
             :class="[
               'chapter-item flex items-center justify-between p-5 border border-slate-100/50 bg-white/50 rounded-xl transition-[background-color,border-color,box-shadow,opacity] duration-300 group relative',
               interactive
                 ? 'hover:bg-white/80 hover:shadow-md hover:border-qinghua/30 cursor-pointer'
                 : 'opacity-60 cursor-not-allowed'
             ]"
             :style="{ '--delay': index * 0.08 + 's' }"
             @click="interactive && emit('select')">
          <div class="absolute left-0 top-0 bottom-0 w-1 bg-qinghua rounded-l-xl opacity-0 group-hover:opacity-100 transition-opacity duration-300" />

          <div class="flex items-center gap-5">
            <span class="course-chapter-number chapter-number w-10 h-10 flex items-center justify-center bg-qinghua/10 text-qinghua rounded-xl text-lg font-bold font-song relative overflow-hidden group-hover:bg-qinghua group-hover:text-white transition-[background-color,color,transform] duration-300">
              {{ index + 1 }}
              <div class="absolute inset-0 bg-gradient-to-br from-white/40 to-transparent" />
            </span>
            <div>
              <h3 class="font-medium text-shuimo text-lg mb-1 group-hover:text-qinghua transition-colors">{{ chapter.title }}</h3>
              <p class="text-sm text-shuimo/60">{{ chapter.description }}</p>
            </div>
          </div>

          <div class="text-sm font-medium text-shuimo/50 flex items-center gap-2 bg-slate-100 px-3 py-1 rounded-lg group-hover:bg-qinghua/10 group-hover:text-qinghua transition-[background-color,color] duration-300">
            <Clock class="w-4 h-4" />
            {{ formatDuration(chapter.videoDuration) }}
          </div>
        </div>
      </TransitionGroup>
    </div>
  </div>
</template>
