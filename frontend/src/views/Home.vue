<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { courseAPI, authAPI } from '../services/api'
import BaseButton from '../components/ui/BaseButton.vue'
import BaseCourseCard from '../components/ui/BaseCourseCard.vue'
import { 
  Sparkles,
  GraduationCap,
  BookText,
  Calculator,
  Languages,
  Atom,
  FlaskConical,
  Leaf,
  Scale,
  Clock,
  Globe,
  ChevronRight,
  ChevronLeft,
  Play,
  Users,
  Star,
  BookOpen,
  Loader2
} from 'lucide-vue-next'

const router = useRouter()
const authStore = useAuthStore()

const selectedSubject = ref('全部')
const allCourses = ref([])
const displayedCourses = ref([])
const loading = ref(false)
const loadingMore = ref(false)
const pageSize = 8
const hasMore = ref(true)

// 轮播图相关
const currentSlide = ref(0)
const carouselInterval = ref(null)
const isHovering = ref(false)

// Header 滚动阴影效果
const isScrolled = ref(false)

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
    return {
      ...course,
      btnStyle: getSubjectBtnStyle(course.subject)
    }
  }).filter(Boolean)
})

// 课程数据按学科筛选与推荐

const getSubjectBtnStyle = (subject) => {
  const styles = {
    // 无障碍：语文按钮改为更高对比度配色
    '语文': 'bg-yanzhihong hover:bg-yanzhi text-white',
    '数学': 'bg-qinghua hover:bg-halanzi text-white',
    '英语': 'bg-danqing hover:bg-qingbai text-white',
    '物理': 'bg-zijinghui hover:bg-qianniuzi text-white',
    '化学': 'bg-tianlv hover:bg-qingsong text-white',
    '生物': 'bg-danya hover:bg-tianlv text-text-main',
    '政治': 'bg-yanzhihong hover:bg-yanzhi text-white',
    '历史': 'bg-tanxiang hover:bg-zhizi text-white',
    '地理': 'bg-qinghua hover:bg-danqing text-white'
  }
  return styles[subject] || 'bg-white text-shuimo hover:bg-gray-100'
}

// 课程按钮样式映射


const startCarousel = () => {
  if (carouselInterval.value) clearInterval(carouselInterval.value)
  carouselInterval.value = setInterval(() => {
    if (!isHovering.value && topCoursesBySubject.value.length > 0) {
      currentSlide.value = (currentSlide.value + 1) % topCoursesBySubject.value.length
    }
  }, 4000)
}

const stopCarousel = () => {
  if (carouselInterval.value) {
    clearInterval(carouselInterval.value)
    carouselInterval.value = null
  }
}

const goToSlide = (index) => {
  currentSlide.value = index
  startCarousel()
}

const prevSlide = () => {
  currentSlide.value = currentSlide.value === 0 
    ? topCoursesBySubject.value.length - 1 
    : currentSlide.value - 1
  startCarousel()
}

const nextSlide = () => {
  currentSlide.value = (currentSlide.value + 1) % topCoursesBySubject.value.length
  startCarousel()
}

const subjectList = [
  { name: '全部', icon: BookOpen, color: 'from-danqing to-qinghua' },
  { name: '语文', icon: BookText, color: 'from-yanzhi to-qianhong' },
  { name: '数学', icon: Calculator, color: 'from-qinghua to-halanzi' },
  { name: '英语', icon: Languages, color: 'from-danqing to-qingbai' },
  { name: '物理', icon: Atom, color: 'from-zijinghui to-qianniuzi' },
  { name: '化学', icon: FlaskConical, color: 'from-tianlv to-qingsong' },
  { name: '生物', icon: Leaf, color: 'from-danya to-tianlv' },
  { name: '政治', icon: Scale, color: 'from-yanzhihong to-yanzhi' },
  { name: '历史', icon: Clock, color: 'from-tanxiang to-zhizi' },
  { name: '地理', icon: Globe, color: 'from-qinghua to-danqing' },
]

onMounted(async () => {
  await loadCourses()
  window.addEventListener('scroll', handleScroll)
  nextTick(() => startCarousel())
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  stopCarousel()
})

// 处理滚动事件 - 更新 header 阴影状态
const updateScrollState = () => {
  isScrolled.value = window.scrollY > 10
}

const loadCourses = async () => {
  loading.value = true
  try {
    const res = await courseAPI.getPublished()
    allCourses.value = (res.data || []).map(course => ({
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
    console.error('获取课程失败:', error)
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
  // 更新 header 阴影状态
  updateScrollState()
  
  const scrollTop = window.scrollY
  const windowHeight = window.innerHeight
  const docHeight = document.documentElement.scrollHeight
  if (scrollTop + windowHeight >= docHeight - 200 && hasMore.value && !loadingMore.value) {
    loadMore()
  }
}

const getSubjectColor = (subject) => {
  const colors = {
    '语文': 'from-yanzhi to-qianhong',
    '数学': 'from-qinghua to-halanzi',
    '英语': 'from-danqing to-qingbai',
    '物理': 'from-zijinghui to-qianniuzi',
    '化学': 'from-tianlv to-qingsong',
    '生物': 'from-danya to-tianlv',
    '政治': 'from-yanzhihong to-yanzhi',
    '历史': 'from-tanxiang to-zhizi',
    '地理': 'from-qinghua to-danqing'
  }
  return colors[subject] || 'from-danqing to-qinghua'
}

const handleLogout = async () => {
  try {
    // 调用后端API更新会话状态
    await authAPI.logout()
  } catch (e) {
    // 即使API调用失败也继续登出
    console.error('登出API调用失败:', e)
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
        'sticky top-0 z-50 backdrop-blur-xl border-b transition-all duration-300',
        isScrolled 
          ? 'bg-white/90 border-slate-200/50 shadow-lg shadow-slate-200/30' 
          : 'bg-white/70 border-white/30'
      ]"
    >
      <div class="max-w-7xl mx-auto px-6 py-4">
        <div class="flex items-center justify-between">
          <router-link to="/" class="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-white/60 backdrop-blur-sm border border-white/50 group hover:shadow-md transition-all hover:scale-105">
            <Sparkles class="w-4 h-4 text-zhizi group-hover:animate-spin-slow" aria-hidden="true" />
            <span class="text-sm font-medium text-shuimo">中学在线教育平台</span>
          </router-link>
          
          <div class="flex items-center gap-3">
            <template v-if="authStore.isAuthenticated">
              <BaseButton 
                variant="text" 
                @click="goToCenter"
              >
                {{ authStore.user?.username }}
              </BaseButton>
              <BaseButton 
                variant="danger" 
                size="sm"
                @click="handleLogout"
              >
                退出
              </BaseButton>
            </template>
            <template v-else>
              <BaseButton 
                variant="text" 
                @click="$router.push('/login')"
              >
                登录
              </BaseButton>
              <BaseButton 
                variant="primary" 
                @click="$router.push('/login?register=true')"
              >
                注册
              </BaseButton>
            </template>
          </div>
        </div>
      </div>
    </header>
    
    <!-- ... (keep rest of template) -->

    <!-- Hero Carousel Section -->
    <section class="relative py-6 overflow-hidden" aria-labelledby="home-featured-heading">
      <!-- 无障碍：补充首页主标题，避免页面缺失 H1 -->
      <h1 id="home-featured-heading" class="sr-only">智慧课堂精选课程</h1>
      <div class="max-w-6xl mx-auto px-6">
        <div 
          class="relative rounded-2xl overflow-hidden group shadow-2xl"
          @mouseenter="isHovering = true"
          @mouseleave="isHovering = false"
        >
          <div class="relative h-[360px] md:h-[420px]">
            <transition-group name="carousel-fade">
              <div
                v-for="(course, index) in topCoursesBySubject"
                :key="course.id"
                v-show="currentSlide === index"
                class="absolute inset-0 cursor-pointer overflow-hidden"
                @click="$router.push(`/course/${course.id}`)"
              >
                <!-- 独立背景层 (负责缩放动画) -->
                <div 
                  class="absolute -inset-4 transition-transform duration-700 ease-out transform-gpu will-change-transform"
                  :class="[isHovering ? 'scale-110' : 'scale-100']"
                >
                  <!-- 颜色背景 -->
                  <div :class="['absolute inset-0 bg-gradient-to-br', course.color]"></div>
                  <!-- 纹理 -->
                  <div class="absolute inset-0 opacity-20 bg-[url('https://www.transparenttextures.com/patterns/cubes.png')]"></div>
                  <!-- 遮罩 -->
                  <div class="absolute inset-0 bg-gradient-to-t from-black/80 via-black/20 to-transparent"></div>
                </div>
                
                <!-- 内容区域 (不参与缩放，保持清晰) -->
                <div class="absolute inset-x-0 bottom-0 top-0 flex items-end p-8 md:p-12 pb-16 pointer-events-none">
                  <div class="relative z-10 w-full md:w-2/3 lg:w-1/2 space-y-4 animate-slide-up pointer-events-auto">
                    <div class="flex items-center gap-3">
                      <span class="px-3 py-1 rounded-full bg-white/20 backdrop-blur-md border border-white/20 text-white text-sm font-medium">
                        精选推荐
                      </span>
                      <div class="flex items-center gap-1 text-white/95" aria-label="课程评分">
                        <Star class="w-4 h-4 fill-current" aria-hidden="true" />
                        <span class="text-white font-bold">{{ course.rating }}</span>
                      </div>
                    </div>
                    
                    <h2 class="text-3xl md:text-5xl font-bold text-white leading-tight tracking-tight shadow-black/10 drop-shadow-lg">
                      {{ course.title }}
                    </h2>
                    
                    <p class="text-lg text-white/80 line-clamp-2 leading-relaxed">
                      由 {{ course.teacher }} 老师倾力打造，已有 {{ course.students }} 名同学加入学习。
                    </p>
                    
                    <div class="pt-4 flex items-center gap-4">
                      <BaseButton 
                        variant="custom"
                        size="lg"
                        :class="['!rounded-full !px-8 border-0', course.btnStyle]"
                      >
                        <Play class="w-5 h-5 mr-2 fill-current" aria-hidden="true" />
                        立即开始
                      </BaseButton>
                    </div>
                  </div>
                </div>
              </div>
            </transition-group>
          </div>

          <!-- 导航按钮 -->
          <div class="absolute right-8 bottom-8 flex items-center gap-4 z-20">
            <button 
              @click.stop="prevSlide"
              class="w-12 h-12 rounded-full bg-white/10 backdrop-blur-md border border-white/20 flex items-center justify-center text-white hover:bg-white/20 hover:scale-105 transition-all text-white/50 hover:text-white"
              aria-label="上一张课程"
            >
              <ChevronLeft class="w-6 h-6" aria-hidden="true" />
            </button>
            <button 
              @click.stop="nextSlide"
              class="w-12 h-12 rounded-full bg-white/10 backdrop-blur-md border border-white/20 flex items-center justify-center text-white hover:bg-white/20 hover:scale-105 transition-all text-white/50 hover:text-white"
              aria-label="下一张课程"
            >
              <ChevronRight class="w-6 h-6" aria-hidden="true" />
            </button>
          </div>
          
          <!-- 指示器 -->
          <div class="absolute top-8 right-8 flex gap-2 z-20">
            <button
              v-for="(course, index) in topCoursesBySubject"
              :key="'dot-' + course.id"
              @mouseenter="goToSlide(index)"
              :class="[
                'w-2.5 h-1 rounded-full transition-all duration-300',
                currentSlide === index ? 'bg-white w-8' : 'bg-white/30 hover:bg-white/50'
              ]"
              :aria-label="`切换到第 ${index + 1} 张课程`"
            />
          </div>
        </div>
      </div>
    </section>

    <!-- Course Section -->
    <section id="courses" class="py-6 px-6">
      <div class="max-w-7xl mx-auto" aria-live="polite">

        <!-- Subject Filter -->
        <div class="flex justify-center gap-3 mb-12 flex-wrap">
          <button
            v-for="subject in subjectList"
            :key="subject.name"
            @click="selectedSubject = subject.name"
            :class="[
              'flex items-center gap-2 px-5 py-2.5 rounded-full font-medium transition-all duration-300 border',
              selectedSubject === subject.name
                ? `bg-gradient-to-r ${subject.color} border-transparent text-white shadow-lg shadow-danqing/20 scale-105`
                : 'bg-white border-slate-100 text-shuimo hover:border-danqing/30 hover:text-danqing hover:shadow-md'
            ]"
            :aria-label="`筛选学科：${subject.name}`"
          >
            <component :is="subject.icon" class="w-4 h-4" aria-hidden="true" />
            {{ subject.name }}
          </button>
        </div>

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
            class="animate-slide-up"
            :style="{ animationDelay: `${index * 0.05}s`, animationFillMode: 'both' }"
            @click="$router.push(`/course/${course.id}`)"
          />
        </div>
        
        <!-- Empty State -->
        <div v-if="!loading && displayedCourses.length === 0" class="flex flex-col items-center justify-center py-20 text-center animate-fade-in">
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
.carousel-fade-enter-active,
.carousel-fade-leave-active {
  /* P1 第二批：首页核心视觉动效统一 200ms 档 */
  transition: opacity var(--motion-duration-medium) var(--motion-ease-standard);
}
.carousel-fade-enter-from,
.carousel-fade-leave-to {
  opacity: 0;
}
.carousel-fade-enter-to,
.carousel-fade-leave-from {
  opacity: 1;
}

.backface-hidden {
  backface-visibility: hidden;
  -webkit-font-smoothing: subpixel-antialiased;
}

/* 列表过渡动画：P1 限定属性，避免 all 带来的非必要重绘 */
.list-move {
  transition: transform var(--motion-duration-slow) var(--motion-ease-standard);
}

.list-enter-active,
.list-leave-active {
  transition:
    opacity var(--motion-duration-slow) var(--motion-ease-standard),
    transform var(--motion-duration-slow) var(--motion-ease-standard);
}

.list-enter-from,
.list-leave-to {
  opacity: 0;
  transform: translateY(30px);
}

.list-leave-active {
  position: absolute;
  width: 100%;
}

/* Header logo spin animation */
@keyframes spin-slow {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.group-hover\:animate-spin-slow:hover {
  animation: spin-slow var(--motion-duration-medium) linear infinite;
}

/* Footer bounce animation */
@keyframes bounce-subtle {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-3px); }
}

.group-hover\:animate-bounce-subtle {
  animation: bounce-subtle var(--motion-duration-medium) var(--motion-ease-standard);
}

/* 无障碍：仅屏幕阅读器可见标题 */
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

/* Footer tagline hover effects */
.footer-tagline span {
  transition: transform var(--motion-duration-medium) var(--motion-ease-standard);
}

.footer-tagline span:hover {
  transform: translateY(-1px);
}

/* Footer section fade-in on scroll */
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
</style>
