<script setup lang="ts">
import { BookOpen, Plus, Search, Edit, Video } from 'lucide-vue-next'
import GlassCard from '../ui/GlassCard.vue'
import BaseButton from '../ui/BaseButton.vue'
import EmptyState from '../ui/EmptyState.vue'
import BaseTooltip from '../ui/BaseTooltip.vue'
import { getImageUrl } from '../../services/api'

interface CourseStatusTag {
  label: string
  class: string
}

interface TeacherCourseItem {
  id: number | string
  title: string
  description?: string
  subject?: string
  status: string
  cover?: string
  students?: number
  chapters?: number
}

defineProps<{
  courses: TeacherCourseItem[]
  activeTab: string
  searchQuery: string
  statusMap: Record<string, CourseStatusTag>
  getStatusTooltip: (status: string) => string
}>()

const emit = defineEmits<{
  (e: 'update:activeTab', value: string): void
  (e: 'update:searchQuery', value: string): void
  (e: 'create'): void
  (e: 'edit', course: TeacherCourseItem): void
  (e: 'manage-chapters', course: TeacherCourseItem): void
}>()

const tabs = [
  { id: 'all', label: '全部' },
  { id: 'PUBLISHED', label: '已发布' },
  { id: 'DRAFT', label: '草稿' },
  { id: 'OFFLINE', label: '已下线' },
]

const onCreate = (): void => {
  emit('create')
}

const onEdit = (course: TeacherCourseItem): void => {
  emit('edit', course)
}

const onManageChapters = (course: TeacherCourseItem): void => {
  emit('manage-chapters', course)
}
</script>

<template>
  <div class="space-y-6">
    <GlassCard class="p-4 animate-slide-up" style="animation-fill-mode: both;">
      <div class="flex items-center justify-between gap-4">
        <div class="flex items-center gap-4 shrink-0">
          <h3 class="text-lg font-bold text-shuimo flex items-center gap-2 font-song shrink-0">
            <BookOpen class="w-5 h-5 text-tianlv" />
            课程管理
          </h3>
          <div class="flex items-center gap-1">
            <button
              v-for="tab in tabs"
              :key="tab.id"
              @click="emit('update:activeTab', tab.id)"
              class="px-3 py-1.5 rounded-lg text-sm font-medium transition-all whitespace-nowrap"
              :class="activeTab === tab.id ? 'bg-tianlv text-white shadow-md shadow-tianlv/20' : 'text-shuimo/60 hover:text-shuimo hover:bg-slate-100'"
            >
              {{ tab.label }}
            </button>
          </div>
        </div>

        <div class="flex items-center gap-3 shrink-0">
          <div class="relative group">
            <label for="teacher-course-search" class="sr-only">搜索课程</label>
            <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-shuimo/40 transition-colors group-focus-within:text-tianlv" />
            <input
              id="teacher-course-search"
              name="teacherCourseSearch"
              :value="searchQuery"
              type="text"
              placeholder="搜索课程..."
              class="w-40 pl-9 pr-3 py-2 rounded-xl bg-slate-50 border-none focus:ring-2 focus:ring-tianlv/20 transition-all text-sm"
              @input="emit('update:searchQuery', ($event.target as HTMLInputElement).value)"
            />
          </div>
          <BaseButton @click="onCreate" icon="Plus" variant="primary">
            创建课程
          </BaseButton>
        </div>
      </div>
    </GlassCard>

    <div v-if="courses.length === 0" class="flex items-center justify-center min-h-[400px]">
      <EmptyState
        icon="book"
        title="暂无相关课程"
        description="开始创建您的第一门课程，与学生分享知识"
        actionText="创建课程"
        :actionIcon="Plus"
        size="lg"
        @action="onCreate"
      />
    </div>

    <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
      <GlassCard
        v-for="course in courses"
        :key="course.id"
        class="group p-0 overflow-hidden flex flex-col h-full hover:-translate-y-1 transition-transform duration-300"
      >
        <div class="h-40 relative bg-slate-100 overflow-hidden">
          <img v-if="course.cover" :src="getImageUrl(course.cover)" class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500" />
          <div v-else class="w-full h-full flex items-center justify-center bg-gradient-to-br from-tianlv/10 to-qingsong/10">
            <BookOpen class="w-12 h-12 text-tianlv/30" />
          </div>

          <div class="absolute top-3 right-3">
            <BaseTooltip :text="getStatusTooltip(course.status)" placement="top">
              <div
                class="px-2 py-1 rounded-full text-xs font-bold border backdrop-blur-md"
                :class="statusMap[course.status]?.class || 'bg-slate-100 text-slate-500'"
              >
                {{ statusMap[course.status]?.label || course.status }}
              </div>
            </BaseTooltip>
          </div>
        </div>

        <div class="p-5 flex-1 flex flex-col">
          <h3 class="font-bold text-shuimo text-lg mb-1 line-clamp-1">{{ course.title }}</h3>
          <p class="text-sm text-shuimo/60 line-clamp-2 mb-4 flex-1">{{ course.description || '暂无简介' }}</p>

          <div class="flex items-center justify-between mt-auto pt-4 border-t border-slate-100">
            <div class="flex gap-2 text-xs text-shuimo/50">
              <span>{{ course.students || 0 }} 学生</span>
              <span>·</span>
              <span>{{ course.chapters || 0 }} 章节</span>
            </div>

            <div class="flex gap-1">
              <button
                @click="onManageChapters(course)"
                :aria-label="`管理课程 ${course.title} 的章节`"
                class="p-2 hover:bg-slate-100 rounded-lg text-shuimo/60 hover:text-tianlv transition-colors"
                title="章节管理"
              >
                <Video class="w-4 h-4" />
              </button>
              <button
                @click="onEdit(course)"
                :aria-label="`编辑课程 ${course.title}`"
                class="p-2 hover:bg-slate-100 rounded-lg text-shuimo/60 hover:text-qinghua transition-colors"
                title="编辑课程"
              >
                <Edit class="w-4 h-4" />
              </button>
              <div class="h-4 w-px bg-slate-200 mx-1 self-center"></div>
              <BaseTooltip :text="getStatusTooltip(course.status)" placement="top">
                <span v-if="course.status === 'REVIEWING'" class="text-xs font-bold text-yanzhi px-2">等待审核</span>
                <span v-else class="text-xs font-bold text-tianlv px-2">保存即送审</span>
              </BaseTooltip>
            </div>
          </div>
        </div>
      </GlassCard>
    </div>
  </div>
</template>
