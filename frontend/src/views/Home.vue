<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { courseAPI, authAPI } from '../services/api'
import BaseButton from '../components/ui/BaseButton.vue'
import BaseCourseCard from '../components/ui/BaseCourseCard.vue'
import HomeCarousel from '../components/home/HomeCarousel.vue'
import SubjectFilter from '../components/home/SubjectFilter.vue'
import { getSubjectColor, getSubjectBtnStyle } from '../utils/subject'
import { logger } from '../utils/logger'
import { Sparkles, GraduationCap, BookOpen, Loader2, ChevronRight } from 'lucide-vue-next'

const router = useRouter()
const authStore = useAuthStore()

interface HomeCourse {
  id: number
  title: string
  teacher: string
  subject: string
  rating: number
  students: number
  coverImage: string | undefined
  color: string
  btnStyle?: string
}

const selectedSubject = ref('全部')
const allCourses = ref<HomeCourse[]>([])
const displayedCourses = ref<HomeCourse[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const pageSize = 8
const hasMore = ref(true)

const isScrolled = ref(false)
const isScrollTicking = ref(false)
const scrollRafId = ref<ReturnType<typeof requestAnimationFrame> | null>(null)

const topCoursesBySubject = computed(() => {
  const subjects = ['语文', '数学', '英语', '物理', '化学', '生物', '政治', '历史', '地理']
  return subjects.map(subject => {
    const subjectCourses = allCourses.value.filter(c => c.subject === subject)
    if (subjectCourses.length === 0) return null
    subjectCourses.sort((a, b) => {
      if (b.rating !== a.rating) return b.rating - a.rating
      return b.students - a.students
    })
    const course = subjectCourses[0]
    if (!course) return null
    return {
      ...course,
      btnStyle: getSubjectBtnStyle(course.subject)
    }
  }).filter((c): c is HomeCourse & { btnStyle: string } => c !== null)
})

onMounted(async () => {
  await loadCourses()
  window.addEventListener('scroll', handleScroll, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  if (scrollRafId.value) {
    cancelAnimationFrame(scrollRafId.value)
    scrollRafId.value = null
  }
})

// 处理滚动事件 - 更新 header 阴影状态
const updateScrollState = () => {
  isScrolled.value = window.scrollY > 10
}

const loadCourses = async () => {
  loading.value = true
  try {
    const res = await courseAPI.getPublished()
    allCourses.value = ((res.data || []) as Array<{ id: number; title: string; teacherName?: string; subject: string; rating?: number; studentCount?: number; coverImage?: string; cover?: string }>).map(course => ({
      id: course.id,
      title: course.title,
      teacher: course.teacherName || '未知教师',
      subject: course.subject,
      rating: course.rating || 4.5,
      students: course.studentCount || 0,
      coverImage: course.coverImage || course.cover,
      color: getSubjectColor(course.subject)
    }))
    filterAndDisplayCourses()
  } catch (error) {
    logger.error('获取课程失败:', error)
    allCourses.value = []
  }
  loading.value = false
}

const filteredCourses = computed(() => {
  let courses = selectedSubject.value === '全部' 
    ? [...allCourses.value] 
    : allCourses.value.filter(c => c.subject === selectedSubject.value)
  courses.sort((a, b) => {
    if (b.rating !== a.rating) return b.rating - a.rating
    return b.students - a.students
  })
  return courses
})

const filterAndDisplayCourses = () => {
  const filtered = filteredCourses.value
  displayedCourses.value = filtered.slice(0, pageSize)
  hasMore.value = filtered.length > pageSize
}

watch(selectedSubject, () => {
  loading.value = true
  displayedCourses.value = [] // 触发列表离场动画
  
  // 添加延迟以展示骨架屏过渡
  setTimeout(() => {
    filterAndDisplayCourses()
    // 确保DOM更新后再结束loading，或直接结束由Vue处理
    loading.value = false
  }, 200)
})

const loadMore = () => {
  if (loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  const filtered = filteredCourses.value
  const currentLen = displayedCourses.value.length
  const nextBatch = filtered.slice(currentLen, currentLen + pageSize)
  displayedCourses.value = [...displayedCourses.value, ...nextBatch]
  hasMore.value = displayedCourses.value.length < filtered.length
  loadingMore.value = false
}

const handleScroll = () => {
  if (isScrollTicking.value) return
  isScrollTicking.value = true
  scrollRafId.value = requestAnimationFrame(() => {
    // 中文注释：将滚动相关读取与状态更新合并到同一帧中，减少连续掉帧
    updateScrollState()

    const scrollTop = window.scrollY
    const windowHeight = window.innerHeight
    const docHeight = document.documentElement.scrollHeight
    if (scrollTop + windowHeight >= docHeight - 200 && hasMore.value && !loadingMore.value) {
      loadMore()
    }

    isScrollTicking.value = false
    scrollRafId.value = null
  })
}

const handleLogout = async () => {
  try {
    // 调用后端API更新会话状态
    await authAPI.logout()
  } catch (e) {
    // 即使API调用失败也继续登出
    logger.error('登出API调用失败:', e)
  }
  authStore.logout()
  router.push('/')
}

const goToCenter = () => {
  if (authStore.user?.role === 'admin') router.push('/admin')
  else if (authStore.user?.role === 'teacher') router.push('/teacher')
  else router.push('/student')
}
</script>
<template>
  <div class="min-h-screen animate-fade-in">
    <!-- Header/Navbar with scroll shadow effect -->
    <header 
      :class="[
        'sticky top-0 z-50 home-header border-b transition-[background-color,border-color,box-shadow] duration-300',
        isScrolled 
          ? 'bg-white/90 border-slate-200/50 shadow-lg shadow-slate-200/30' 
          : 'bg-white/70 border-white/30'
      ]"
    >
      <div class="max-w-7xl mx-auto px-6 py-4">
        <div class="flex items-center justify-between">
          <router-link to="/" class="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-white/60 backdrop-blur-sm border border-white/50 group hover:shadow-md transition-[transform,box-shadow] duration-300 hover:scale-105">
            <Sparkles class="w-4 h-4 text-zhizi group-hover:animate-spin-slow" aria-hidden="true" />
            <span class="text-sm font-medium text-shuimo">中学在线教育平台</span>
          </router-link>
          
          <div class="flex items-center gap-3">
            <template v-if="authStore.isAuthenticated">
              <BaseButton 
                variant="text" 
                data-testid="nav-user"
                @click="goToCenter"
              >
                {{ authStore.user?.username }}
              </BaseButton>
              <BaseButton 
                variant="danger" 
                size="sm"
                data-testid="nav-logout"
                @click="handleLogout"
              >
                退出
              </BaseButton>
            </template>
            <template v-else>
              <BaseButton 
                variant="text" 
                data-testid="nav-login"
                @click="$router.push('/login')"
              >
                登录
              </BaseButton>
              <BaseButton 
                variant="primary" 
                data-testid="nav-register"
                @click="$router.push('/login?register=true')"
              >
                注册
              </BaseButton>
            </template>
          </div>
        </div>
      </div>
    </header>

    <HomeCarousel :courses="topCoursesBySubject" />

    <!-- Course Section -->
    <section id="courses" class="py-6 px-6">
      <div class="max-w-7xl mx-auto" aria-live="polite">

        <SubjectFilter :selected="selectedSubject" @select="(name) => selectedSubject = name" />

        <!-- Loading Skeleton (Shimmer) -->
        <div v-if="loading" class="grid md:grid-cols-2 lg:grid-cols-4 gap-6 min-h-[200px]">
          <div v-for="i in 8" :key="i" class="bg-white rounded-2xl overflow-hidden h-[340px] relative border border-slate-100 shadow-sm animate-pulse">
            <!-- Shimmer effect overlay -->
            <div class="absolute inset-0 bg-gradient-to-r from-transparent via-white/40 to-transparent skew-x-12 translate-x-[-150%] animate-shimmer z-10"></div>
            
            <!-- Cover image placeholder -->
            <div class="h-48 bg-slate-200"></div>
            
            <!-- Content placeholder -->
            <div class="p-5 space-y-4">
              <div class="flex justify-between items-start">
                <div class="h-4 bg-slate-200 rounded-full w-16"></div>
                <div class="h-4 bg-slate-200 rounded-full w-12"></div>
              </div>
              <div class="h-6 bg-slate-200 rounded-md w-3/4"></div>
              <div class="pt-4 flex items-center gap-3">
                <div class="w-8 h-8 rounded-full bg-slate-200"></div>
                <div class="h-4 bg-slate-200 rounded-full w-24"></div>
              </div>
            </div>
          </div>
        </div>

        <!-- Course Grid -->
        <div v-else class="grid md:grid-cols-2 lg:grid-cols-4 gap-6 min-h-[200px]">
          <BaseCourseCard 
            v-for="(course, index) in displayedCourses" 
            :key="course.id"
            :course="course"
            :data-testid="`course-card-${course.id}`"
            class="animate-slide-up"
            :style="{ animationDelay: `${index * 0.05}s`, animationFillMode: 'both' }"
            @click="$router.push(`/course/${course.id}`)"
          />
        </div>
        
        <!-- Empty State -->
        <div v-if="!loading && displayedCourses.length === 0" data-testid="courses-empty" class="flex flex-col items-center justify-center py-20 text-center animate-fade-in">
          <div class="w-24 h-24 rounded-full bg-slate-50 flex items-center justify-center mb-6">
            <BookOpen class="w-10 h-10 text-shuimo/20" aria-hidden="true" />
          </div>
          <h3 class="text-lg font-medium text-shuimo/60 mb-2">暂无该学科课程</h3>
          <p class="text-sm text-shuimo/40 mb-6">老师正在快马加鞭备课中...</p>
          <BaseButton 
            variant="secondary" 
            size="sm"
            @click="selectedSubject = '全部'"
          >
            查看全部课程
          </BaseButton>
        </div>

        <!-- Load More Indicator -->
        <div v-if="loadingMore" class="flex justify-center py-12">
          <div class="flex items-center gap-2 text-danqing bg-white/50 backdrop-blur px-4 py-2 rounded-full border border-danqing/10 shadow-sm">
            <Loader2 class="w-5 h-5 animate-spin" aria-hidden="true" />
            <span class="text-sm font-medium">加载更多课程...</span>
          </div>
        </div>
        <div v-else-if="hasMore && displayedCourses.length > 0" class="text-center py-12">
          <button 
            @click="loadMore"
            class="text-sm text-shuimo/40 hover:text-danqing transition-colors flex items-center gap-1 mx-auto group"
          >
            下滑或点击加载更多
            <ChevronRight class="w-4 h-4 group-hover:translate-y-0.5 transition-transform rotate-90" aria-hidden="true" />
          </button>
        </div>
      </div>
    </section>

    
    <!-- Footer with micro-animations -->
    <footer class="py-8 px-6 border-t border-white/20 bg-white/30 footer-section">
      <div class="max-w-7xl mx-auto text-center">
        <div class="flex items-center justify-center gap-2 mb-2 group cursor-default">
          <GraduationCap class="w-5 h-5 text-danqing group-hover:animate-bounce-subtle transition-transform" aria-hidden="true" />
          <span class="font-bold text-shuimo group-hover:text-danqing transition-colors">智慧课堂</span>
          <span class="text-sm text-shuimo/50">中学在线教育平台</span>
        </div>
        <p class="text-sm text-shuimo/40 footer-tagline">
          <span class="inline-block hover:text-danqing/60 transition-colors cursor-default">学完解锁作业</span>
          <span class="mx-1">·</span>
          <span class="inline-block hover:text-qinghua/60 transition-colors cursor-default">AI智能批改</span>
          <span class="mx-1">·</span>
          <span class="inline-block hover:text-tianlv/60 transition-colors cursor-default">分层协作学习</span>
        </p>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.home-header {
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
}

:global(html.dark) .home-header,
:global(.dark) .home-header {
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
}

@media (max-width: 1024px) {
  :global(html.dark) .home-header,
  :global(.dark) .home-header {
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.footer-tagline span {
  transition: transform var(--motion-duration-medium) var(--motion-ease-standard);
}

.footer-tagline span:hover {
  transform: translateY(-1px);
}

.footer-section {
  animation: footer-fade-in var(--motion-duration-medium) var(--motion-ease-standard);
}

@keyframes footer-fade-in {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes spin-slow {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.group-hover\:animate-spin-slow:hover {
  animation: spin-slow var(--motion-duration-medium) linear 1;
}

@keyframes bounce-subtle {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-3px); }
}

.group-hover\:animate-bounce-subtle {
  animation: bounce-subtle var(--motion-duration-medium) var(--motion-ease-standard);
}
</style>