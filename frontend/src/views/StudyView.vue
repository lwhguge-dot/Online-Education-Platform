<script setup>
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { courseAPI, chapterAPI, progressAPI } from '../services/api'
import SkeletonLoader from '../components/SkeletonLoader.vue'
import ChapterCommentSection from '../components/comments/ChapterCommentSection.vue'
import AnimatedNumber from '../components/ui/AnimatedNumber.vue'
import StudyChapterSidebar from '../components/student/StudyChapterSidebar.vue'
import StudyChapterInfoCard from '../components/student/StudyChapterInfoCard.vue'
import { ArrowLeft, Play, Clock, Sparkles } from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const courseId = computed(() => route.params.id)
const initialChapterId = computed(() => route.query.chapter ? Number(route.query.chapter) : null)
const initialPosition = computed(() => route.query.t ? Number(route.query.t) : null)
const fromStudent = computed(() => route.query.from === 'student')
const course = ref(null)
const chapters = ref([])
const currentChapter = ref(null)
const isPlaying = ref(false)
const videoProgress = ref(0)
const loading = ref(true)
const videoRef = ref(null)
const videoDuration = ref(0)
const lastSavedProgress = ref(0)
const lastPlayPosition = ref(0) // 上次播放位置（秒）
const showResumePrompt = ref(false) // 显示跳转提示
const showCompleteCelebration = ref(false) // 显示完成庆祝动画
let resumePromptTimer = null // 跳转提示定时器

// 显示跳转提示（5秒后自动消失）
const showResumePromptWithTimer = () => {
  showResumePrompt.value = true
  // 清除之前的定时器
  if (resumePromptTimer) {
    clearTimeout(resumePromptTimer)
  }
  // 5秒后自动消失
  resumePromptTimer = setTimeout(() => {
    showResumePrompt.value = false
  }, 5000)
}

const clampPercent = (value) => {
  const n = Number(value)
  if (!Number.isFinite(n)) return 0
  return Math.min(100, Math.max(0, n))
}

const loadCourse = async () => {
  try {
    loading.value = true
    const res = await courseAPI.getById(courseId.value)
    if (res.data) {
      course.value = res.data
    }
    
    const chaptersRes = await chapterAPI.getByCourse(courseId.value)
    if (chaptersRes.data) {
      chapters.value = chaptersRes.data.map((ch, idx) => ({
        ...ch,
        unlocked: idx === 0, // 第一章默认解锁
        completed: false,
        progress: 0
      }))
      
      // 尝试加载进度（忽略错误）
      try {
        const studentId = authStore.user?.id
        if (studentId) {
          const progressRes = await progressAPI.getCourseProgress(courseId.value, studentId)
          if (progressRes.data) {
            progressRes.data.forEach(p => {
              const ch = chapters.value.find(c => Number(c.id) === Number(p.chapterId))
              if (ch) {
                const rate = Number(p.videoRate || 0)
                let percent = Math.round(rate * 100)
                if (p.isCompleted === 1) percent = 100
                ch.progress = clampPercent(percent)
                ch.completed = p.isCompleted === 1
                ch.unlocked = true
                ch.lastPosition = p.lastPosition || 0 // 保存上次播放位置
              }
            })
            // 解锁下一章
            for (let i = 0; i < chapters.value.length - 1; i++) {
              if (chapters.value[i].completed) {
                chapters.value[i + 1].unlocked = true
              }
            }
          }
        }
      } catch (e) {
        console.warn('加载进度失败，使用默认状态:', e)
      }
      
      // 恢复上次选择的章节（刷新不跳转）
      if (chapters.value.length > 0) {
        const savedChapterId = sessionStorage.getItem(`study_chapter_${courseId.value}`)
        let targetChapter = null
        let targetPosition = null
        
        // 优先使用URL参数指定的章节
        if (initialChapterId.value) {
          targetChapter = chapters.value.find(ch => Number(ch.id) === Number(initialChapterId.value) && ch.unlocked)
          if (targetChapter && initialPosition.value) {
            targetPosition = initialPosition.value
          }
        }
        // 其次恢复上次选择的章节
        if (!targetChapter && savedChapterId) {
          targetChapter = chapters.value.find(ch => Number(ch.id) === Number(savedChapterId) && ch.unlocked)
        }
        // 兜底：选择第一章
        if (!targetChapter) {
          targetChapter = chapters.value[0]
        }
        
        targetChapter.progress = clampPercent(targetChapter.progress || 0)
        if (targetChapter.completed) targetChapter.progress = 100

        currentChapter.value = targetChapter
        videoProgress.value = clampPercent(currentChapter.value.progress || 0)
        lastSavedProgress.value = clampPercent(currentChapter.value.progress || 0)
        // 保存章节ID到sessionStorage
        sessionStorage.setItem(`study_chapter_${courseId.value}`, targetChapter.id.toString())
        
        // 如果有URL指定的播放位置，使用它
        if (targetPosition && targetPosition > 0) {
          lastPlayPosition.value = targetPosition
          showResumePromptWithTimer()
        }
        // 否则检查是否有上次播放位置（未完成的章节才显示提示）
        else if (!currentChapter.value.completed && currentChapter.value.lastPosition && currentChapter.value.lastPosition > 10) {
          lastPlayPosition.value = currentChapter.value.lastPosition
          showResumePromptWithTimer()
        }
      }
    }
  } catch (e) {
    console.error('加载课程失败:', e)
  } finally {
    loading.value = false
  }
}

const selectChapter = (chapter) => {
  if (chapter.unlocked) {
    // 保存当前章节ID到sessionStorage（刷新不跳转）
    sessionStorage.setItem(`study_chapter_${courseId.value}`, chapter.id.toString())
    
    chapter.progress = clampPercent(chapter.progress || 0)
    if (chapter.completed) chapter.progress = 100

    currentChapter.value = chapter
    videoProgress.value = clampPercent(chapter.progress || 0)
    lastSavedProgress.value = clampPercent(chapter.progress || 0)
    isPlaying.value = false
    videoDuration.value = 0 // 重置时长，等待新视频加载
    
    // 检查该章节是否有上次播放位置
    if (chapter.lastPosition && chapter.lastPosition > 10) {
      lastPlayPosition.value = chapter.lastPosition
      showResumePromptWithTimer()
    } else {
      lastPlayPosition.value = 0
      showResumePrompt.value = false
      if (resumePromptTimer) {
        clearTimeout(resumePromptTimer)
        resumePromptTimer = null
      }
    }
  }
}

watch(
  () => ({
    id: currentChapter.value?.id,
    completed: currentChapter.value?.completed,
    lastPosition: currentChapter.value?.lastPosition
  }),
  (val) => {
    if (!val?.id) return
    if (initialPosition.value && initialPosition.value > 0) return
    if (!val.completed && val.lastPosition && val.lastPosition > 10) {
      lastPlayPosition.value = val.lastPosition
      showResumePromptWithTimer()
    }
  },
  { immediate: true }
)

const getVideoUrl = (chapter) => {
  if (!chapter?.videoUrl) return null
  if (chapter.videoUrl.startsWith('/')) {
    const staticBase = import.meta.env.VITE_API_BASE.replace('/api', '') || '';
    return `${staticBase}/api/course-service${chapter.videoUrl}`;
  }
  return chapter.videoUrl
}

// 视频加载完成，获取时长
const onVideoLoaded = (e) => {
  const video = e.target
  if (video.duration) {
    videoDuration.value = Math.floor(video.duration) // 向下取整保存秒数
  }
}

// 格式化时长显示
const formatDuration = (seconds) => {
  if (!seconds) return '0分00秒'
  const totalSeconds = Math.floor(seconds)
  const mins = Math.floor(totalSeconds / 60)
  const secs = totalSeconds % 60
  return `${mins}分${secs.toString().padStart(2, '0')}秒`
}

// 保存进度到后端（包含播放位置）
const saveProgress = async (progress, isCompleted = 0, currentPosition = 0) => {
  if (!currentChapter.value) return
  try {
    const safeProgress = clampPercent(progress)
    await progressAPI.updateProgress({
      courseId: Number(courseId.value),
      chapterId: currentChapter.value.id,
      studentId: authStore.user?.id,
      videoRate: safeProgress / 100,
      isCompleted: isCompleted,
      currentPosition: Math.floor(currentPosition)
    })
    lastSavedProgress.value = safeProgress
  } catch (e) {
    console.error('保存进度失败:', e)
  }
}

// 跳转到上次播放位置
const resumePlayback = () => {
  if (videoRef.value && lastPlayPosition.value > 0) {
    videoRef.value.currentTime = lastPlayPosition.value
    videoRef.value.play()
  }
  showResumePrompt.value = false
}

// 关闭提示，从头播放
const dismissResumePrompt = () => {
  showResumePrompt.value = false
}

// 视频暂停时保存播放位置（精确记忆，不降低进度）
const onVideoPause = (e) => {
  const video = e.target
  if (video.currentTime > 10 && video.duration) {
    // 只保存播放位置，使用当前进度或历史最高进度
    const currentProgress = clampPercent(Math.round((video.currentTime / video.duration) * 100))
    const progressToSave = Math.max(currentProgress, clampPercent(videoProgress.value))
    saveProgress(progressToSave, 0, video.currentTime)
  }
}

// 视频进度更新（只增不减，保持历史最高进度）
const onVideoTimeUpdate = (e) => {
  const video = e.target
  if (video.duration) {
    const currentProgress = clampPercent(Math.round((video.currentTime / video.duration) * 100))
    // 只有当前进度大于已记录进度时才更新
    if (currentProgress > videoProgress.value) {
      videoProgress.value = currentProgress
      // 每增加10%保存一次（同时保存播放位置）
      if (currentProgress - lastSavedProgress.value >= 10) {
        saveProgress(currentProgress, 0, video.currentTime)
      }
    }
    if (currentChapter.value && currentProgress > currentChapter.value.progress) {
      currentChapter.value.progress = currentProgress
    }
  }
}

const onVideoEnded = async () => {
  videoProgress.value = 100
  if (currentChapter.value) {
    currentChapter.value.progress = 100
    currentChapter.value.completed = true
    // 显示完成庆祝动画
    showCompleteCelebration.value = true
    setTimeout(() => {
      showCompleteCelebration.value = false
    }, 2000)
    // 保存完成状态
    await saveProgress(100, 1)
    // 解锁下一章（使用展开运算符触发响应式更新）
    const idx = chapters.value.findIndex(c => c.id === currentChapter.value.id)
    if (idx >= 0 && idx < chapters.value.length - 1) {
      const nextChapter = chapters.value[idx + 1]
      chapters.value[idx + 1] = { ...nextChapter, unlocked: true }
      // 强制刷新数组引用
      chapters.value = [...chapters.value]
    }
  }
}

const goBack = () => {
  // 如果是从学生中心来的，返回学生中心
  if (fromStudent.value) {
    router.replace('/student')
  } else {
    // 否则返回课程详情页
    router.replace(`/course/${courseId.value}`)
  }
}

onMounted(() => {
  // 滚动到页面顶部
  window.scrollTo({ top: 0, behavior: 'instant' })
  loadCourse()
})

onUnmounted(() => {
  // 清除定时器
  if (resumePromptTimer) {
    clearTimeout(resumePromptTimer)
    resumePromptTimer = null
  }
})
</script>

<template>
  <div class="min-h-screen flex flex-col animate-fade-in">
    <!-- 顶部导航 -->
    <header class="sticky top-0 z-20 bg-white/70 backdrop-blur-xl border-b border-white/50 shadow-sm">
      <div class="max-w-7xl mx-auto px-4 py-3 flex items-center justify-between">
        <div class="flex items-center gap-4">
          <button @click="goBack" class="p-2 text-shuimo/70 hover:text-qinghua hover:bg-qinghua/10 rounded-xl transition-all">
            <ArrowLeft class="w-5 h-5" />
          </button>
          <div>
            <h1 class="text-lg font-bold text-shuimo font-song">{{ course?.title || '课程学习' }}</h1>
            <p v-if="currentChapter" class="text-xs text-shuimo/50 hidden sm:block">正在学习：{{ currentChapter.title }}</p>
          </div>
        </div>
        
        <div class="flex items-center gap-3">
          <div class="hidden sm:flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-medium transition-all duration-300"
               :class="videoProgress >= 100 ? 'bg-tianlv/10 text-tianlv' : 'bg-qinghua/10 text-qinghua'">
            <Clock class="w-3 h-3" />
            <span>学习进度 <AnimatedNumber :value="videoProgress" :duration="300" />%</span>
            <Sparkles v-if="videoProgress >= 100" class="w-3 h-3 animate-pulse" />
          </div>
        </div>
      </div>
    </header>
    
    <div class="flex-1 max-w-7xl mx-auto w-full px-4 py-6">
      <div v-if="loading" class="grid grid-cols-1 lg:grid-cols-3 gap-6 h-full">
        <div class="lg:col-span-2 space-y-6">
           <SkeletonLoader type="detail-header" />
        </div>
        <div class="lg:col-span-1">
           <SkeletonLoader type="course-card" class="h-full" />
        </div>
      </div>
      
      <div v-else class="grid grid-cols-1 lg:grid-cols-3 gap-6 h-full">
        <!-- 视频播放区域 -->
        <div class="lg:col-span-2 space-y-6 flex flex-col">
          <div class="bg-black rounded-2xl overflow-hidden shadow-2xl shadow-slate-200 aspect-video relative group ring-1 ring-black/5">
            <!-- 视频播放器 -->
            <div v-if="getVideoUrl(currentChapter)" class="w-full h-full relative">
              <video 
                ref="videoRef"
                :src="getVideoUrl(currentChapter)" 
                class="w-full h-full object-contain" 
                controls 
                @loadedmetadata="onVideoLoaded"
                @timeupdate="onVideoTimeUpdate"
                @pause="onVideoPause"
                @ended="onVideoEnded"
              />
              <!-- 跳转到上次播放位置提示 -->
              <Transition name="slide-up">
                <div v-if="showResumePrompt" class="absolute bottom-20 left-4 right-4 sm:right-auto sm:w-auto bg-black/80 backdrop-blur-md border border-white/10 text-white px-5 py-3 rounded-xl flex items-center justify-between gap-4 shadow-xl">
                  <span class="text-sm font-medium">上次观看到 <span class="text-qinghua ml-1">{{ formatDuration(lastPlayPosition) }}</span></span>
                  <div class="flex items-center gap-2">
                    <button @click="resumePlayback" class="px-3 py-1.5 bg-qinghua hover:bg-qinghua/90 rounded-lg text-xs font-bold transition-colors">
                      跳转
                    </button>
                    <button @click="dismissResumePrompt" class="p-1 text-white/50 hover:text-white transition-colors">
                      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
                    </button>
                  </div>
                </div>
              </Transition>
            </div>
            <div v-else class="absolute inset-0 flex items-center justify-center text-white text-center bg-slate-900">
              <div>
                <div class="w-20 h-20 bg-white/10 backdrop-blur-sm rounded-full flex items-center justify-center mb-6 mx-auto ring-1 ring-white/20">
                  <Play class="w-8 h-8 ml-1 text-white/70" />
                </div>
                <p class="text-lg font-medium text-white/90">{{ currentChapter?.title || '请选择章节' }}</p>
                <p class="text-sm text-white/50 mt-2">该章节暂无视频资源</p>
              </div>
            </div>
          </div>
          
          <!-- 章节信息 -->
          <StudyChapterInfoCard :chapter="currentChapter" :video-duration="videoDuration" />
          
          <!-- 评论区 -->
          <ChapterCommentSection
            v-if="currentChapter && course"
            :chapter-id="currentChapter.id"
            :course-id="Number(courseId)"
            :current-user="authStore.user"
            class="animate-slide-up"
            style="animation-delay: 0.2s;"
          />
        </div>
        
        <!-- 章节列表 -->
        <StudyChapterSidebar
          :chapters="chapters"
          :current-chapter-id="currentChapter?.id ?? null"
          @select="selectChapter"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 上滑动画 */
.slide-up-enter-active {
  animation: slide-up-in var(--motion-duration-medium) var(--motion-ease-standard);
}

.slide-up-leave-active {
  animation: slide-up-out 0.2s ease-in;
}

@keyframes slide-up-in {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes slide-up-out {
  from {
    opacity: 1;
    transform: translateY(0);
  }
  to {
    opacity: 0;
    transform: translateY(-10px);
  }
}
</style>
