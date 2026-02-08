<template>
  <div class="min-h-screen animate-fade-in">
    <div class="max-w-6xl mx-auto px-4 py-8">
      <!-- 返回按钮 -->
      <button @click="goBack" class="mb-6 flex items-center gap-2 text-shuimo/70 hover:text-qinghua transition-colors group">
        <div class="p-2 rounded-full bg-white/50 group-hover:bg-qinghua/10 transition-all duration-300 group-hover:shadow-sm">
          <ArrowLeft class="w-5 h-5 transition-transform duration-300 group-hover:-translate-x-1" />
        </div>
        <span class="font-medium">返回</span>
      </button>
      
      <div v-if="loading" class="py-10">
        <SkeletonLoader type="detail-header" class="w-full" />
      </div>
      
      <div v-else-if="course" class="space-y-6 animate-slide-up">
        <div class="glass-card rounded-2xl overflow-hidden p-1">
          <div class="md:flex">
            <div class="md:w-1/3 relative group overflow-hidden rounded-xl">
              <img v-if="course.coverImage" :src="getImageUrl(course.coverImage)" 
                   :alt="course.title"
                   class="w-full h-full object-cover transition-transform duration-700 group-hover:scale-105" />
              <div v-else class="w-full h-64 bg-gradient-to-br from-qinghua/20 to-halanzi/20 flex items-center justify-center">
                <svg class="w-16 h-16 text-qinghua/40" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"></path>
                </svg>
              </div>
            </div>
            <div class="p-8 md:w-2/3 flex flex-col justify-center">
              <div class="flex items-center gap-3 mb-4">
                <span :class="statusClass">{{ statusText }}</span>
              </div>
              <h1 class="text-3xl font-bold text-shuimo mb-4 font-song">{{ course.title }}</h1>
              <p class="text-shuimo/70 mb-6 leading-relaxed">{{ course.description }}</p>
              <div class="flex items-center gap-8 text-sm text-shuimo/60 mb-8 p-4 bg-slate-50/50 rounded-xl">
                <span class="flex items-center gap-2 stat-item" style="--delay: 0s">
                  <User class="w-4 h-4 text-qinghua" />
                  讲师：{{ course.teacherName }}
                </span>
                <span class="flex items-center gap-2 stat-item" style="--delay: 0.1s">
                  <Users class="w-4 h-4 text-tianlv" />
                  学生：<AnimatedNumber :value="course.studentCount || 0" :duration="800" />人
                </span>
                <span class="flex items-center gap-2 stat-item" style="--delay: 0.2s">
                  <Star class="w-4 h-4 text-zhizi" />
                  评分：{{ course.rating || 0 }}分
                </span>
              </div>
              
              <div class="flex gap-4 flex-wrap">
                <BaseButton 
                  v-if="canEnroll" 
                  @click="handleEnroll"
                  :disabled="enrolling"
                  variant="custom"
                  class="px-8 py-3 bg-gradient-to-r from-qinghua to-halanzi text-white rounded-xl shadow-lg shadow-qinghua/30 hover:-translate-y-0.5 hover:shadow-xl transition-all duration-300 flex items-center gap-2"
                >
                  <Play class="w-5 h-5" />
                  {{ enrolling ? '正在报名...' : '立即报名' }}
                </BaseButton>
                
                <BaseButton 
                  v-if="isEnrolled" 
                  @click="goToStudy"
                  variant="custom"
                  class="px-8 py-3 bg-gradient-to-r from-tianlv to-qingsong text-white rounded-xl shadow-lg shadow-tianlv/30 hover:-translate-y-0.5 hover:shadow-xl transition-all duration-300 flex items-center gap-2"
                >
                  <Play class="w-5 h-5" />
                  继续学习
                </BaseButton>
                
                <BaseButton 
                  v-if="isEnrolled" 
                  @click="handleDrop"
                  variant="custom"
                  class="px-8 py-3 bg-yanzhi/10 text-yanzhi rounded-xl hover:bg-yanzhi/20 hover:-translate-y-0.5 transition-all duration-300 flex items-center gap-2"
                >
                  <LogOut class="w-5 h-5" />
                  退出课程
                </BaseButton>
                
                <BaseButton 
                  v-if="isAdmin"
                  @click="goToStudy"
                  variant="custom"
                  class="px-8 py-3 bg-gradient-to-r from-zijinghui to-qianniuzi text-white rounded-xl shadow-lg shadow-zijinghui/30 hover:-translate-y-0.5 hover:shadow-xl transition-all duration-300 flex items-center gap-2"
                >
                  <Eye class="w-5 h-5" />
                  查看课程
                </BaseButton>
              </div>
            </div>
          </div>
        </div>
        
        <div class="glass-card rounded-2xl p-8">
          <h2 class="text-xl font-bold text-shuimo mb-6 flex items-center gap-2">
            <div class="w-1 h-6 bg-qinghua rounded-full"></div>
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
                   'chapter-item flex items-center justify-between p-5 border border-slate-100/50 bg-white/50 rounded-xl transition-all duration-300 group',
                   (isEnrolled || isAdmin)
                     ? 'hover:bg-white/80 hover:shadow-md hover:border-qinghua/30 cursor-pointer'
                     : 'opacity-60 cursor-not-allowed'
                 ]"
                 :style="{ '--delay': index * 0.08 + 's' }"
                 @click="handleChapterClick">
              <!-- 左侧指示条 -->
              <div class="absolute left-0 top-0 bottom-0 w-1 bg-qinghua rounded-l-xl opacity-0 group-hover:opacity-100 transition-opacity duration-300"></div>
              
              <div class="flex items-center gap-5">
                <span class="chapter-number w-10 h-10 flex items-center justify-center bg-qinghua/10 text-qinghua rounded-xl text-lg font-bold font-song relative overflow-hidden group-hover:bg-qinghua group-hover:text-white transition-all duration-300">
                  {{ index + 1 }}
                  <div class="absolute inset-0 bg-gradient-to-br from-white/40 to-transparent"></div>
                </span>
                <div>
                  <h3 class="font-medium text-shuimo text-lg mb-1 group-hover:text-qinghua transition-colors">{{ chapter.title }}</h3>
                  <p class="text-sm text-shuimo/60">{{ chapter.description }}</p>
                </div>
              </div>
              <div class="text-sm font-medium text-shuimo/50 flex items-center gap-2 bg-slate-100 px-3 py-1 rounded-lg group-hover:bg-qinghua/10 group-hover:text-qinghua transition-all duration-300">
                <Clock class="w-4 h-4" />
                {{ formatDuration(chapter.videoDuration) }}
              </div>
            </div>
            </TransitionGroup>
          </div>
        </div>
      </div>
      
      <div v-else class="text-center py-32">
        <h2 class="text-2xl font-bold text-shuimo mb-4">课程不存在</h2>
        <p class="text-shuimo/60 mb-8">您访问的课程可能已被删除或下架</p>
        <button @click="$router.push('/')" class="px-6 py-2 bg-qinghua text-white rounded-lg hover:bg-qinghua/90 transition-colors">
          返回首页
        </button>
      </div>
    </div>
    
    <!-- 自定义弹出框 -->
    <Transition name="scale">
      <div v-if="showToast" class="fixed inset-0 flex items-center justify-center z-50">
        <div class="absolute inset-0 bg-shuimo/20 backdrop-blur-sm" @click="showToast = false"></div>
        <div class="relative glass-card rounded-2xl p-8 max-w-sm w-full mx-4 transform animate-scale-in">
          <div class="text-center">
            <div :class="['w-16 h-16 mx-auto mb-4 rounded-2xl flex items-center justify-center transform rotate-3 hover:rotate-6 transition-transform', 
                          toastType === 'success' ? 'bg-qingsong/10 text-qingsong' : 'bg-yanzhi/10 text-yanzhi']">
              <svg v-if="toastType === 'success'" class="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
              </svg>
              <svg v-else class="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
              </svg>
            </div>
            <h3 class="text-xl font-bold text-shuimo mb-2">{{ toastTitle }}</h3>
            <p class="text-shuimo/60 mb-8">{{ toastMessage }}</p>
            <button @click="showToast = false" 
                    :class="['w-full py-3 rounded-xl font-medium transition-all shadow-lg hover:-translate-y-0.5',
                             toastType === 'success' ? 'bg-gradient-to-r from-tianlv to-qingsong text-white shadow-tianlv/30' : 'bg-gradient-to-r from-yanzhihong to-yanzhi text-white shadow-yanzhi/30']">
              确定
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useConfirmStore } from '../stores/confirm'
import { courseAPI, chapterAPI, enrollmentAPI, getImageUrl } from '../services/api'
import BaseButton from '../components/ui/BaseButton.vue'
import SkeletonLoader from '../components/SkeletonLoader.vue'
import AnimatedNumber from '../components/ui/AnimatedNumber.vue'
import { ArrowLeft, Users, Star, User, Clock, BookOpen, Play, LogOut, Eye } from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const confirmStore = useConfirmStore()

const loading = ref(true)
const course = ref(null)
const chapters = ref([])
const isEnrolled = ref(false)
const enrolling = ref(false)
const showToast = ref(false)
const toastType = ref('success')
const toastTitle = ref('')
const toastMessage = ref('')

const showMessage = (type, title, message) => {
  toastType.value = type
  toastTitle.value = title
  toastMessage.value = message
  showToast.value = true
}

const formatDuration = (seconds) => {
  if (!seconds) return '0分钟'
  const totalSeconds = Math.floor(seconds)
  const mins = Math.floor(totalSeconds / 60)
  const secs = totalSeconds % 60
  if (mins === 0) return `${secs}秒`
  if (secs === 0) return `${mins}分钟`
  return `${mins}分${secs}秒`
}

const statusClass = computed(() => {
  const statusMap = {
    'PUBLISHED': 'px-3 py-1 bg-qingsong/10 text-qingsong rounded-lg text-sm font-medium',
    'DRAFT': 'px-3 py-1 bg-shuimo/10 text-shuimo rounded-lg text-sm font-medium',
    'REVIEWING': 'px-3 py-1 bg-zhizi/10 text-zhizi rounded-lg text-sm font-medium',
    'REJECTED': 'px-3 py-1 bg-yanzhi/10 text-yanzhi rounded-lg text-sm font-medium',
    'OFFLINE': 'px-3 py-1 bg-shuimo/10 text-shuimo rounded-lg text-sm font-medium',
  }
  return statusMap[course.value?.status] || statusMap['DRAFT']
})

const statusText = computed(() => {
  const textMap = {
    'PUBLISHED': '已发布',
    'DRAFT': '草稿',
    'REVIEWING': '审核中',
    'REJECTED': '已驳回',
    'OFFLINE': '已下架',
  }
  return textMap[course.value?.status] || '未知'
})

const canEnroll = computed(() => {
  const role = authStore.user?.role?.toLowerCase()
  return role === 'student' && 
         course.value?.status === 'PUBLISHED' && 
         !isEnrolled.value
})

const isAdmin = computed(() => {
  const role = authStore.user?.role?.toLowerCase()
  return role === 'admin'
})

const loadCourse = async () => {
  try {
    const courseId = route.params.id
    const result = await courseAPI.getById(courseId)
    if (result.code === 200 && result.data) {
      course.value = result.data
    }
    
    try {
      const chaptersResult = await chapterAPI.getByCourse(courseId)
      if (chaptersResult.code === 200 && chaptersResult.data) {
        chapters.value = chaptersResult.data || []
      }
    } catch (e) {
      console.log('章节加载失败:', e)
    }
    
    if (authStore.user?.id) {
      try {
        const enrollResult = await enrollmentAPI.check(courseId, authStore.user.id)
        if (enrollResult.code === 200 && enrollResult.data) {
          isEnrolled.value = enrollResult.data?.enrolled || false
        }
      } catch (e) {
        console.log('报名状态检查失败:', e)
      }
    }
  } catch (e) {
    console.error('加载课程失败:', e)
  } finally {
    loading.value = false
  }
}

const handleEnroll = async () => {
  if (!authStore.isAuthenticated) {
    router.push('/login')
    return
  }
  
  enrolling.value = true
  try {
    const result = await enrollmentAPI.enroll(course.value.id, authStore.user.id)
    if (result.code === 200) {
      isEnrolled.value = true
      course.value.studentCount = (course.value.studentCount || 0) + 1
      showMessage('success', '报名成功', '恭喜您成功报名该课程，开始学习之旅吧！')
    } else {
      showMessage('error', '报名失败', result.message || '请稍后重试')
    }
  } catch (e) {
    showMessage('error', '报名失败', e.message)
  } finally {
    enrolling.value = false
  }
}

const handleDrop = async () => {
  const confirmed = await confirmStore.show({
    title: '退出课程',
    message: '确定要退出该课程吗？退出后需要重新报名才能继续学习。',
    type: 'warning',
    confirmText: '确定退出',
    cancelText: '取消'
  })
  if (!confirmed) return
  
  try {
    const result = await enrollmentAPI.drop(course.value.id, authStore.user.id)
    if (result.code === 200) {
      isEnrolled.value = false
      course.value.studentCount = Math.max(0, (course.value.studentCount || 1) - 1)
      showMessage('success', '退出成功', '您已成功退出该课程')
    } else {
      showMessage('error', '退出失败', result.message || '请稍后重试')
    }
  } catch (e) {
    showMessage('error', '退出失败', e.message)
  }
}

const handleChapterClick = () => {
  if (isEnrolled.value || isAdmin.value) {
    goToStudy()
    return
  }
  showMessage('error', '无法进入', '请先报名该课程后再学习章节')
}

const goToStudy = () => {
  router.push(`/study/${course.value.id}`)
}

const goBack = () => {
  // 检查是否有来源参数或referrer
  const from = route.query.from
  
  // 如果是从学生中心来的，返回学生中心的课程页面
  if (from === 'student') {
    // 使用 window.location 确保 hash 正确处理
    window.location.href = '/student#courses'
    return
  }
  
  // 如果是从教师中心来的，返回教师中心
  if (from === 'teacher') {
    window.location.href = '/teacher#courses'
    return
  }
  
  // 检查浏览器历史记录长度
  if (window.history.length > 1) {
    router.back()
  } else {
    // 如果没有历史记录，根据用户角色返回对应页面
    const role = authStore.user?.role?.toLowerCase()
    if (role === 'student') {
      window.location.href = '/student#courses'
    } else if (role === 'teacher') {
      window.location.href = '/teacher#courses'
    } else {
      router.push('/')
    }
  }
}

onMounted(() => {
  loadCourse()
})
</script>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}
.fade-enter-active .relative,
.fade-leave-active .relative {
  transition: transform 0.3s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
.fade-enter-from .relative {
  transform: scale(0.9);
}
.fade-leave-to .relative {
  transform: scale(0.9);
}

/* 统计项入场动画 */
.stat-item {
  animation: stat-fade-in 0.5s ease-out both;
  animation-delay: var(--delay, 0s);
}

@keyframes stat-fade-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 章节列表入场动画 */
.chapter-list-enter-active {
  animation: chapter-enter 0.5s ease-out both;
  animation-delay: var(--delay, 0s);
}

.chapter-list-leave-active {
  animation: chapter-leave 0.3s ease-in both;
}

@keyframes chapter-enter {
  from {
    opacity: 0;
    transform: translateX(-20px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

@keyframes chapter-leave {
  from {
    opacity: 1;
    transform: translateX(0);
  }
  to {
    opacity: 0;
    transform: translateX(20px);
  }
}

/* 章节项相对定位 */
.chapter-item {
  position: relative;
  overflow: hidden;
}

/* 章节序号动画 */
.chapter-number {
  animation: number-pop 0.4s ease-out both;
  animation-delay: calc(var(--delay, 0s) + 0.15s);
}

@keyframes number-pop {
  0% {
    transform: scale(0);
    opacity: 0;
  }
  70% {
    transform: scale(1.15);
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}
</style>
