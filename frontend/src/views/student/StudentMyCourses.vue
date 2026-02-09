<script setup>
/**
 * 学生课程页面
 * 展示已选课程和选课中心，支持搜索、筛选和学习入口
 */
import { ref, computed } from 'vue'
import { BookOpen, Search, Play, Star, Users, Clock, GraduationCap } from 'lucide-vue-next'
import GlassCard from '../../components/ui/GlassCard.vue'
import BaseSelect from '../../components/ui/BaseSelect.vue'
import SkeletonCard from '../../components/ui/SkeletonCard.vue'
import EmptyState from '../../components/ui/EmptyState.vue'
import { getImageUrl } from '../../services/api'

const props = defineProps({
  enrolledCourses: {
    type: Array,
    default: () => []
  },
  availableCourses: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['view-detail', 'enroll', 'drop', 'start-study'])

const activeTab = ref('enrolled')
const searchQuery = ref('')
const subjectFilter = ref('all')
const imageErrors = ref({})

const handleImageError = (courseId) => {
  imageErrors.value[courseId] = true
}

const subjects = ['语文', '数学', '英语', '物理', '化学', '生物', '政治', '历史', '地理']

const filteredList = computed(() => {
  const targetList = activeTab.value === 'enrolled' ? props.enrolledCourses : props.availableCourses
  return targetList.filter(c => {
    const matchesSearch = c.title?.toLowerCase().includes(searchQuery.value.toLowerCase()) || 
                          c.teacher?.toLowerCase().includes(searchQuery.value.toLowerCase())
    const matchesSubject = subjectFilter.value === 'all' || c.subject === subjectFilter.value
    return matchesSearch && matchesSubject
  })
})

const handleCourseClick = (course) => {
  emit('view-detail', course)
}

const handleStartStudy = (course) => {
  emit('start-study', course.id)
}
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <!-- Toolbar -->
    <GlassCard class="p-4" style="animation: fade-in-up 0.4s ease-out forwards;">
      <div class="flex items-center justify-between gap-4 flex-wrap">
        <!-- 左侧：标题和标签页 -->
        <div class="flex items-center gap-4">
          <h3 class="text-lg font-bold text-shuimo flex items-center gap-2 font-song shrink-0">
            <BookOpen class="w-5 h-5 text-qinghua icon-hover-rotate" />
            我的课程
          </h3>
          <div class="flex items-center gap-2">
            <button 
              v-for="tab in [{id: 'enrolled', label: '已选课程'}, {id: 'available', label: '选课中心'}]"
              :key="tab.id"
              @click="activeTab = tab.id"
              class="px-4 py-2 rounded-xl text-sm font-medium transition-all whitespace-nowrap btn-ripple"
              :class="activeTab === tab.id ? 'bg-qinghua text-white shadow-lg shadow-qinghua/30' : 'bg-slate-50 text-shuimo/70 hover:bg-slate-100'"
            >
              {{ tab.label }}
            </button>
          </div>
        </div>

        <!-- 右侧：搜索和筛选 -->
        <div class="flex items-center gap-3">
          <div class="relative group">
             <!-- 无障碍：为课程搜索输入框提供可关联标签 -->
             <label for="course-search-input" class="sr-only">搜索课程或教师</label>
             <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-shuimo/40 transition-colors group-focus-within:text-qinghua" />
             <input 
               id="course-search-input"
               name="courseSearch"
               v-model="searchQuery"
               type="text" 
               placeholder="搜索课程、教师..."
               class="w-48 pl-9 pr-4 py-2 rounded-xl bg-slate-50 border-none focus:ring-2 focus:ring-qinghua/20 transition-all text-sm"
             />
          </div>
          <div class="w-24">
            <BaseSelect 
              v-model="subjectFilter" 
              :options="[{ value: 'all', label: '全科' }, ...subjects.map(s => ({ value: s, label: s }))]"
              size="sm"
            />
          </div>
        </div>
      </div>
    </GlassCard>

    <!-- Content Grid -->
    <!-- 骨架屏加载状态 -->
    <div v-if="loading" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
      <SkeletonCard v-for="i in 4" :key="i" />
    </div>

    <!-- 课程列表 -->
    <div v-else-if="filteredList.length > 0" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
      
      <!-- ========== 已选课程卡片 ========== -->
      <template v-if="activeTab === 'enrolled'">
        <div 
          v-for="(course, index) in filteredList" 
          :key="course.id"
          class="group relative rounded-2xl overflow-hidden hover:shadow-xl transition-all duration-300 cursor-pointer h-64 bg-white card-hover-lift stagger-item"
          :style="{ animationDelay: `${index * 0.08}s`, opacity: 0, animation: `fade-in-up 0.5s ease-out ${index * 0.08}s forwards` }"
          @click="handleCourseClick(course)"
        >
          <!-- 封面层 -->
          <div class="absolute inset-0">
            <img 
              v-if="course.coverImage && !imageErrors[course.id]" 
              :src="getImageUrl(course.coverImage)" 
              loading="lazy" 
              class="w-full h-full object-cover transition-transform duration-[8s] ease-out group-hover:scale-110"
              @error="handleImageError(course.id)"
            />
            <div 
              v-else 
              :class="['w-full h-full bg-gradient-to-br', course.color || 'from-qinghua to-halanzi']"
            >
              <div class="w-full h-full flex items-center justify-center">
                <BookOpen class="w-16 h-16 text-white/30" />
              </div>
            </div>
          </div>
          
          <!-- 渐变遮罩 -->
          <div class="absolute inset-0 bg-gradient-to-t from-black/80 via-black/30 to-transparent opacity-70 group-hover:opacity-80 transition-opacity"></div>
          
          <!-- 悬浮播放按钮 -->
          <div class="absolute inset-0 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-all duration-300 scale-50 group-hover:scale-100">
            <div class="w-16 h-16 rounded-full bg-white/20 backdrop-blur-sm border border-white/50 flex items-center justify-center shadow-lg group-active:scale-95 transition-transform play-btn-pulse">
              <div class="w-12 h-12 rounded-full bg-white text-qinghua flex items-center justify-center shadow-sm">
                <Play class="w-5 h-5 ml-1 fill-current" />
              </div>
            </div>
          </div>
          
          <!-- 新章节标签（左上角） -->
          <div v-if="course.hasNewChapters" class="absolute top-3 left-3 px-2 py-1 rounded-lg bg-gradient-to-r from-yanzhi to-zhizi text-xs font-bold text-white shadow-lg animate-bounce">
            新 {{ course.newChaptersCount > 1 ? `+${course.newChaptersCount}` : '' }}
          </div>

          <!-- 最后学习时间（右上角） -->
          <div v-if="course.lastStudy && course.lastStudy !== '暂无记录'" class="absolute top-3 right-3 px-2.5 py-1 rounded-lg backdrop-blur-md bg-black/30 border border-white/20 text-xs text-white/80 flex items-center gap-1">
            <Clock class="w-3 h-3" />
            {{ course.lastStudy }}
          </div>
          
          <!-- 底部信息面板 -->
          <div class="absolute bottom-0 left-0 right-0 p-4">
            <!-- 课程标题 -->
            <h3 class="font-bold text-lg text-white mb-2 line-clamp-1 group-hover:text-qingbai transition-colors">
              {{ course.title }}
            </h3>
            
            <!-- 教师名称 -->
            <div class="flex items-center gap-1 text-sm text-white/80 mb-3">
              <span class="w-1.5 h-1.5 rounded-full bg-zhizi/80"></span>
              {{ course.teacher }}
            </div>

            <!-- 学习进度条和继续学习按钮 -->
            <div class="pt-3 border-t border-white/10">
              <div class="flex items-center justify-between mb-2">
                <span class="text-xs text-white/70">学习进度</span>
                <span class="text-xs font-medium text-white">{{ course.completedChapters || 0 }}/{{ course.totalChapters || 0 }} 章</span>
              </div>
              <div class="flex items-center gap-3">
                <div class="flex-1 h-2 bg-white/20 rounded-full overflow-hidden backdrop-blur-sm">
                  <div 
                    class="h-full bg-gradient-to-r from-tianlv to-qingsong rounded-full shadow-[0_0_10px_rgba(255,255,255,0.3)] transition-all duration-500 progress-bar-animated" 
                    :style="{ width: (course.progress || 0) + '%' }"
                  ></div>
                </div>
                <button 
                  @click.stop="handleStartStudy(course)" 
                  class="px-4 py-1.5 text-xs font-bold text-qinghua bg-white rounded-lg hover:shadow-lg transition-all hover:scale-105 active:scale-95 shadow-sm whitespace-nowrap btn-ripple"
                >
                  继续学习
                </button>
              </div>
            </div>
          </div>
        </div>
      </template>

      <!-- ========== 选课中心卡片 ========== -->
      <template v-else>
        <div 
          v-for="(course, index) in filteredList" 
          :key="course.id"
          class="group relative rounded-2xl overflow-hidden hover:shadow-xl transition-all duration-300 cursor-pointer h-64 bg-white card-hover-lift stagger-item"
          :style="{ animationDelay: `${index * 0.08}s`, opacity: 0, animation: `fade-in-up 0.5s ease-out ${index * 0.08}s forwards` }"
          @click="handleCourseClick(course)"
        >
          <!-- 封面层 -->
          <div class="absolute inset-0">
            <img 
              v-if="course.coverImage && !imageErrors[course.id]" 
              :src="getImageUrl(course.coverImage)" 
              loading="lazy" 
              class="w-full h-full object-cover transition-transform duration-[8s] ease-out group-hover:scale-110"
              @error="handleImageError(course.id)"
            />
            <div 
              v-else 
              :class="['w-full h-full bg-gradient-to-br', course.color || 'from-qinghua to-halanzi']"
            >
              <div class="w-full h-full flex items-center justify-center">
                <BookOpen class="w-16 h-16 text-white/30" />
              </div>
            </div>
          </div>
          
          <!-- 渐变遮罩 -->
          <div class="absolute inset-0 bg-gradient-to-t from-black/80 via-black/30 to-transparent opacity-70 group-hover:opacity-80 transition-opacity"></div>
          
          <!-- 悬浮播放按钮 -->
          <div class="absolute inset-0 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-all duration-300 scale-50 group-hover:scale-100">
            <div class="w-16 h-16 rounded-full bg-white/20 backdrop-blur-sm border border-white/50 flex items-center justify-center shadow-lg group-active:scale-95 transition-transform play-btn-pulse">
              <div class="w-12 h-12 rounded-full bg-white text-qinghua flex items-center justify-center shadow-sm">
                <Play class="w-5 h-5 ml-1 fill-current" />
              </div>
            </div>
          </div>
          
          <!-- 评分（右上角） -->
          <div class="absolute top-3 right-3 px-2.5 py-1 rounded-lg backdrop-blur-md bg-black/20 border border-white/10 text-xs font-medium flex items-center gap-1 text-white shadow-sm">
            <Star class="w-3.5 h-3.5 text-yellow-400 fill-current" />
            <span>{{ course.rating || 4.5 }}</span>
          </div>
          
          <!-- 底部信息面板 -->
          <div class="absolute bottom-0 left-0 right-0 p-4">
            <!-- 课程标题 -->
            <h3 class="font-bold text-lg text-white mb-2 line-clamp-1 group-hover:text-qingbai transition-colors">
              {{ course.title }}
            </h3>
            
            <!-- 教师和学生数 -->
            <div class="flex items-center justify-between text-sm text-white/80 mb-3">
              <span class="flex items-center gap-1">
                <span class="w-1.5 h-1.5 rounded-full bg-zhizi/80"></span>
                {{ course.teacher }}
              </span>
              <div class="flex items-center gap-1.5 px-2 py-0.5 rounded-full bg-white/10 backdrop-blur-sm border border-white/10">
                <Users class="w-3.5 h-3.5" />
                <span>{{ course.students || 0 }}</span>
              </div>
            </div>

            <!-- 报名按钮 -->
            <div class="pt-3 border-t border-white/10 flex items-center justify-between">
              <span class="text-xs text-white/70">{{ course.students || 0 }} 人在学</span>
              <button 
                @click.stop="emit('enroll', course)" 
                class="px-4 py-1.5 text-xs font-bold text-white bg-white/20 hover:bg-white hover:text-qinghua rounded-lg backdrop-blur-md transition-all border border-white/20 btn-ripple hover:scale-105 active:scale-95"
              >
                立即报名
              </button>
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- 空状态（增强引导） -->
    <div v-else class="flex flex-col items-center justify-center py-20 text-center" style="animation: fade-in-up 0.5s ease-out forwards;">
       <div class="w-20 h-20 rounded-full bg-gradient-to-br from-qinghua/10 to-halanzi/10 flex items-center justify-center mb-4 empty-state-float">
          <GraduationCap class="w-10 h-10 text-qinghua/50" />
       </div>
       <p class="text-shuimo/60 font-medium text-lg">{{ activeTab === 'enrolled' ? '还没有选修课程' : '暂无可选课程' }}</p>
       <p class="text-sm text-shuimo/40 mt-2 max-w-xs" v-if="activeTab === 'enrolled'">
         选择感兴趣的课程开始学习之旅吧，在选课中心可以发现更多精彩课程
       </p>
       <p class="text-sm text-shuimo/40 mt-2 max-w-xs" v-else>
         暂时没有可选课程，请稍后再来查看
       </p>
       <button
         v-if="activeTab === 'enrolled'"
         @click="activeTab = 'available'"
         class="mt-6 px-6 py-2.5 rounded-xl bg-qinghua text-white text-sm font-medium hover:bg-qinghua/90 shadow-lg shadow-qinghua/20 transition-all btn-ripple"
       >
         去选课
       </button>
    </div>
  </div>
</template>
