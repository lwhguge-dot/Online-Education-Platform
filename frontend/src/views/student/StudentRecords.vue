<script setup lang="ts">
/**
 * 学生学习记录页面
 * 展示学习时长图表、周对比、学习足迹、测验成绩和知识点掌握度
 */
import { computed, ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { History, TrendingUp, Clock, CheckCircle, BarChart3, ArrowUp, ArrowDown, Minus, Play, RefreshCw, BookOpen } from 'lucide-vue-next'
import GlassCard from '../../components/ui/GlassCard.vue'
import BaseSectionHeader from '../../components/ui/BaseSectionHeader.vue'
import KnowledgeMasteryChart from '../../components/charts/KnowledgeMasteryChart.vue'
import SkeletonCard from '../../components/ui/SkeletonCard.vue'
import { chapterAPI, progressAPI } from '../../services/api'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

/**
 * 每日学习时长数据结构
 */
interface WeeklyStudyDay {
  day: string
  hours: number
}

/**
 * 学习足迹项数据结构
 */
interface TimelineItem {
  title: string
  time: string
  action: string
  courseId?: number | string
  chapterId?: number | string
  chapterTitle?: string
  duration?: number
  progress?: number
  type?: string
}

/**
 * 测验成绩项数据结构
 */
interface QuizScoreItem {
  title: string
  time: string
  score: number
  chapterId?: number | string
  courseId?: number | string
}

/**
 * 组件属性类型定义
 */
interface StudentRecordsProps {
  timeline?: TimelineItem[]
  quizScores?: QuizScoreItem[]
  weeklyStudyHours?: WeeklyStudyDay[]
  lastWeekStudyHours?: WeeklyStudyDay[]
  thisWeekMinutes?: number
  lastWeekMinutes?: number
  weeklyChange?: number
}

const props = withDefaults(defineProps<StudentRecordsProps>(), {
  timeline: () => [],
  quizScores: () => [],
  weeklyStudyHours: () => [
    { day: '周一', hours: 0 },
    { day: '周二', hours: 0 },
    { day: '周三', hours: 0 },
    { day: '周四', hours: 0 },
    { day: '周五', hours: 0 },
    { day: '周六', hours: 0 },
    { day: '周日', hours: 0 }
  ],
  lastWeekStudyHours: () => [],
  thisWeekMinutes: 0,
  lastWeekMinutes: 0,
  weeklyChange: 0
})

// 学习轨迹真实数据状态
const learningTrackLoading = ref(false)
const learningTrack = ref<TimelineItem[]>([])

/**
 * 加载学习轨迹真实数据
 * 调用后端 progressAPI.getLearningTrack 获取学生的学习轨迹
 */
const loadLearningTrack = async () => {
  const studentId = authStore.user?.id
  if (!studentId) return

  learningTrackLoading.value = true
  try {
    const res = await progressAPI.getLearningTrack(studentId)
    if (res.code !== 200 || !res.data) return

    const rawData: any = res.data
    const items: any[] = Array.isArray(rawData)
      ? rawData
      : (Array.isArray(rawData?.recentLearning) ? rawData.recentLearning : [])

    const mapped = await Promise.allSettled(items.map(async (item) => {
      const chapterId = item?.chapterId
      let chapterTitle = item?.chapterTitle
      let courseId = item?.courseId
      let courseTitle = item?.courseName || item?.courseTitle

      if (chapterId && (!courseId || !chapterTitle)) {
        try {
          const chapterRes = await chapterAPI.getDetail(chapterId)
          if (chapterRes?.code === 200 && chapterRes?.data) {
            courseId = courseId ?? chapterRes.data.courseId
            chapterTitle = chapterTitle ?? chapterRes.data.title
          }
        } catch {
        }
      }

      return {
        title: courseTitle || '学习记录',
        time: item?.studyTime || item?.lastStudyTime || item?.time || '最近',
        action: item?.action || (chapterTitle ? `学习章节: ${chapterTitle}` : '学习进度更新'),
        courseId,
        chapterId,
        chapterTitle,
        duration: item?.duration,
        progress: item?.progress,
        type: item?.type || 'learning_track'
      }
    }))

    learningTrack.value = mapped
      .filter(r => r.status === 'fulfilled')
      .map((r: any) => r.value)
  } catch (e) {
    console.error('加载学习轨迹失败:', e)
    // 如果API失败，使用父组件传入的timeline数据
    learningTrack.value = []
  } finally {
    learningTrackLoading.value = false
  }
}

// 计算最终显示的时间线数据（优先使用API数据，否则使用props）
const displayTimeline = computed(() => {
  return learningTrack.value.length > 0 ? learningTrack.value : props.timeline
})

// 计算图表缩放所需的最大时长
const maxHours = computed(() => {
  const max = Math.max(...props.weeklyStudyHours.map(d => d.hours), 1)
  return max
})

// 本周总学习小时数
const thisWeekHours = computed(() => {
  return Math.round(props.thisWeekMinutes / 60 * 10) / 10
})

// 上周总学习小时数
const lastWeekHours = computed(() => {
  return Math.round(props.lastWeekMinutes / 60 * 10) / 10
})

// 周对比状态
const weeklyCompareStatus = computed(() => {
  if (props.weeklyChange > 0) return 'up'
  if (props.weeklyChange < 0) return 'down'
  return 'same'
})

// 周对比提示文案
const weeklyCompareMessage = computed(() => {
  if (props.weeklyChange > 20) return '进步神速！继续保持！'
  if (props.weeklyChange > 0) return '有进步，继续加油！'
  if (props.weeklyChange === 0) return '保持稳定，再接再厉！'
  if (props.weeklyChange > -20) return '稍有下降，调整状态！'
  return '学习时间减少了，加把劲！'
})

/**
 * 点击学习足迹跳转到对应章节
 */
const handleTimelineClick = (item: TimelineItem) => {
  if (item.courseId && item.chapterId) {
    router.push(`/study/${item.courseId}?chapter=${item.chapterId}&from=student`)
  } else if (item.courseId) {
    router.push(`/study/${item.courseId}?from=student`)
  }
}

/**
 * 点击测验跳转到对应章节
 */
const handleQuizClick = async (quiz: QuizScoreItem) => {
  const chapterId = quiz?.chapterId
  if (!chapterId) return

  if (quiz?.courseId) {
    router.push(`/study/${quiz.courseId}?chapter=${chapterId}&from=student`)
    return
  }

  try {
    const res = await chapterAPI.getDetail(chapterId)
    if (res?.code === 200 && res?.data?.courseId) {
      router.push(`/study/${res.data.courseId}?chapter=${chapterId}&from=student`)
    }
  } catch (e) {
    console.error('获取章节信息失败:', e)
  }
}

/**
 * 从知识点图表跳转到学习页面
 */
const handleMasteryNavigate = ({ courseId, chapterId }: { courseId?: number | string, chapterId?: number | string }) => {
  if (courseId && chapterId) {
    router.push(`/study/${courseId}?chapter=${chapterId}&from=student`)
  } else if (courseId) {
    router.push(`/study/${courseId}?from=student`)
  }
}

/**
 * 刷新学习轨迹数据
 */
const refreshTrack = () => {
  loadLearningTrack()
}

// 组件挂载时加载学习轨迹
onMounted(() => {
  loadLearningTrack()
})
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <!-- 本周学习图表 -->
      <GlassCard class="p-6 col-span-2 card-hover-glow animate-slide-up" style="animation-fill-mode: both;">
        <BaseSectionHeader title="本周学习时长" :icon="BarChart3" icon-color="text-qinghua">
           <template #actions>
             <span class="text-xs text-shuimo/50">单位: 小时</span>
           </template>
        </BaseSectionHeader>
        
        <div class="h-64 flex items-end justify-between gap-2 px-2">
           <div 
             v-for="(day, index) in weeklyStudyHours" 
             :key="day.day" 
             class="flex flex-col items-center gap-2 flex-1 group"
             style="height: 100%;"
           >
             <div class="flex-1 w-full flex items-end justify-center">
               <!-- 只有有学习时长时才显示柱子 -->
               <div v-if="day.hours > 0"
                    class="w-full max-w-[40px] mx-auto rounded-t-xl relative overflow-visible cursor-pointer chart-bar" 
                    :style="{ height: Math.max((day.hours / maxHours * 100), 10) + '%', animationDelay: `${index * 0.1}s` }">
                  <div class="absolute inset-0 bg-gradient-to-t from-qinghua to-halanzi rounded-t-xl opacity-90 group-hover:opacity-100 group-hover:shadow-lg group-hover:shadow-qinghua/30 transition-all"></div>
                  <!-- Tooltip - 鼠标悬停显示 -->
                  <div class="absolute -top-10 left-1/2 -translate-x-1/2 bg-shuimo text-white text-xs px-3 py-1.5 rounded-lg opacity-0 group-hover:opacity-100 transition-all duration-200 pointer-events-none whitespace-nowrap z-20 shadow-lg transform group-hover:scale-100 scale-95">
                     <span class="font-bold">{{ day.hours }}</span> 小时
                     <div class="absolute bottom-0 left-1/2 -translate-x-1/2 translate-y-full w-0 h-0 border-l-4 border-r-4 border-t-4 border-transparent border-t-shuimo"></div>
                  </div>
               </div>
               <!-- 没有学习时长时不显示柱子，只保留占位 -->
               <div v-else class="w-full max-w-[40px] mx-auto h-0"></div>
             </div>
             <span class="text-xs font-medium shrink-0 transition-colors" 
                   :class="day.hours > 0 ? 'text-shuimo' : 'text-shuimo/40'">{{ day.day }}</span>
           </div>
        </div>
      </GlassCard>

      <!-- 学习汇总与周对比 -->
      <GlassCard class="p-6 flex flex-col justify-center gap-4 card-hover-glow animate-slide-up" style="animation-delay: 0.1s; animation-fill-mode: both;">
        <!-- 本周学习时长 -->
        <div class="text-center">
           <div class="w-14 h-14 mx-auto rounded-full bg-tianlv/10 flex items-center justify-center mb-2 text-tianlv icon-hover-scale">
              <Clock class="w-7 h-7" />
           </div>
           <p class="text-sm text-shuimo/60">本周累计学习</p>
           <p class="text-3xl font-bold font-mono text-tianlv mt-1 number-pop">
              {{ thisWeekHours }}<span class="text-sm text-shuimo/40 ml-1">小时</span>
           </p>
        </div>
        
        <!-- 周对比 -->
        <div class="bg-slate-50 rounded-xl p-4 transition-all hover:bg-slate-100">
          <div class="flex items-center justify-between mb-2">
            <span class="text-xs text-shuimo/60">vs 上周</span>
            <div class="flex items-center gap-1">
              <ArrowUp v-if="weeklyCompareStatus === 'up'" class="w-4 h-4 text-tianlv animate-bounce" />
              <ArrowDown v-else-if="weeklyCompareStatus === 'down'" class="w-4 h-4 text-yanzhi" />
              <Minus v-else class="w-4 h-4 text-slate-400" />
              <span 
                class="text-sm font-bold number-pop"
                :class="{
                  'text-tianlv': weeklyCompareStatus === 'up',
                  'text-yanzhi': weeklyCompareStatus === 'down',
                  'text-slate-500': weeklyCompareStatus === 'same'
                }"
              >
                {{ weeklyChange > 0 ? '+' : '' }}{{ weeklyChange }}%
              </span>
            </div>
          </div>
          <div class="flex items-center justify-between text-xs">
            <span class="text-shuimo/50">上周: {{ lastWeekHours }}小时</span>
          </div>
          <p 
            class="text-xs mt-2 text-center py-1.5 rounded-lg transition-all"
            :class="{
              'bg-tianlv/10 text-tianlv': weeklyCompareStatus === 'up',
              'bg-yanzhi/10 text-yanzhi': weeklyCompareStatus === 'down',
              'bg-slate-100 text-slate-500': weeklyCompareStatus === 'same'
            }"
          >
            {{ weeklyCompareMessage }}
          </p>
        </div>
        
        <div class="w-full h-px bg-slate-100"></div>
        
        <!-- 平均测验成绩 -->
        <div class="text-center">
           <div class="w-14 h-14 mx-auto rounded-full bg-zhizi/10 flex items-center justify-center mb-2 text-zhizi icon-hover-scale">
              <CheckCircle class="w-7 h-7" />
           </div>
           <p class="text-sm text-shuimo/60">平均测验成绩</p>
           <p class="text-3xl font-bold font-mono text-zhizi mt-1 number-pop">
              {{ quizScores.length > 0 ? Math.round(quizScores.reduce((a, b) => a + b.score, 0) / quizScores.length) : 0 }}<span class="text-sm text-shuimo/40 ml-1">分</span>
           </p>
        </div>
      </GlassCard>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
       <!-- 学习足迹 -->
       <GlassCard class="p-6 card-hover-glow animate-slide-up" style="animation-delay: 0.2s; animation-fill-mode: both;">
          <div class="flex items-center justify-between mb-6">
            <h3 class="text-lg font-bold text-shuimo flex items-center gap-2 font-song">
               <History class="w-5 h-5 text-zijinghui icon-hover-rotate" />
               学习足迹
            </h3>
            <button
              @click="refreshTrack"
              :disabled="learningTrackLoading"
              class="p-1.5 rounded-lg hover:bg-slate-100 text-shuimo/40 hover:text-shuimo transition-colors disabled:opacity-50"
              title="刷新"
            >
              <RefreshCw class="w-4 h-4" :class="{ 'animate-spin': learningTrackLoading }" />
            </button>
          </div>

          <!-- 加载状态 -->
          <div v-if="learningTrackLoading" class="space-y-4">
            <div v-for="i in 3" :key="i" class="animate-pulse flex gap-4">
              <div class="w-3 h-3 rounded-full bg-slate-200"></div>
              <div class="flex-1 space-y-2">
                <div class="h-4 bg-slate-200 rounded w-3/4"></div>
                <div class="h-3 bg-slate-100 rounded w-1/2"></div>
              </div>
            </div>
          </div>

          <!-- 学习足迹内容 -->
          <div v-else class="relative pl-4 space-y-6 before:absolute before:left-0 before:top-2 before:bottom-2 before:w-0.5 before:bg-slate-100 timeline-animated">
             <div
               v-for="(item, index) in displayTimeline"
               :key="index"
               class="relative pl-6 group cursor-pointer stagger-item"
               :style="{ animationDelay: `${index * 0.1}s` }"
               @click="handleTimelineClick(item)"
             >
                <div class="absolute left-[-5px] top-1 w-2.5 h-2.5 rounded-full ring-4 ring-white transition-all group-hover:scale-125 timeline-dot"
                     :class="index === 0 ? 'bg-zijinghui' : 'bg-slate-200 group-hover:bg-zijinghui'"></div>
                <div class="p-3 rounded-xl transition-all group-hover:bg-slate-50 group-hover:shadow-sm">
                   <div class="flex items-center justify-between">
                     <p class="font-medium text-shuimo group-hover:text-qinghua transition-colors">{{ item.title }}</p>
                     <div class="opacity-0 group-hover:opacity-100 transition-all flex items-center gap-1 text-xs text-qinghua transform translate-x-2 group-hover:translate-x-0">
                       <Play class="w-3 h-3" />
                       <span>继续学习</span>
                     </div>
                   </div>
                   <p class="text-xs text-shuimo/50 mt-1">{{ item.time }}</p>
                   <p class="text-sm text-shuimo/70 mt-2 bg-slate-50 group-hover:bg-white p-2 rounded-lg inline-block transition-colors">
                      {{ item.action }}
                   </p>
                   <!-- 学习时长（如果有） -->
                   <span v-if="item.duration" class="ml-2 text-xs text-qinghua/60">
                     {{ item.duration }}分钟
                   </span>
                </div>
             </div>
             <div v-if="displayTimeline.length === 0" class="text-center py-10">
                <div class="w-14 h-14 mx-auto mb-3 rounded-full bg-zijinghui/10 flex items-center justify-center empty-state-float">
                  <History class="w-7 h-7 text-zijinghui/50" />
                </div>
                <p class="text-shuimo/50 text-sm">暂无记录，开始学习吧！</p>
                <p class="text-xs text-shuimo/30 mt-1">完成章节学习后这里会显示足迹</p>
                <button
                  @click="$emit('navigate', 'courses')"
                  class="mt-4 px-4 py-2 rounded-xl bg-qinghua text-white text-sm font-medium hover:bg-qinghua/90 transition-colors"
                >
                  去选课
                </button>
             </div>
          </div>
       </GlassCard>

       <!-- 测验成绩 -->
       <GlassCard class="p-6 card-hover-glow animate-slide-up" style="animation-delay: 0.3s; animation-fill-mode: both;">
          <h3 class="text-lg font-bold text-shuimo mb-6 flex items-center gap-2 font-song">
             <TrendingUp class="w-5 h-5 text-yanzhi icon-hover-rotate" />
             测验成绩
          </h3>
          <div class="space-y-3">
             <div 
               v-for="(quiz, index) in quizScores" 
               :key="index" 
               class="flex items-center justify-between p-3 rounded-xl bg-slate-50 transition-all hover:shadow-sm stagger-item"
               :class="quiz?.chapterId ? 'hover:bg-slate-100 cursor-pointer' : 'opacity-60 cursor-not-allowed'"
               :style="{ animationDelay: `${index * 0.1}s` }"
               @click="quiz?.chapterId ? handleQuizClick(quiz) : null"
             >
                <div class="flex items-center gap-3">
                   <div class="w-8 h-8 rounded-lg bg-white flex items-center justify-center text-sm font-bold text-shuimo border border-slate-100 transition-transform hover:scale-110">
                      {{ index + 1 }}
                   </div>
                   <div>
                      <p class="font-medium text-shuimo text-sm">{{ quiz.title }}</p>
                      <p class="text-xs text-shuimo/50">{{ quiz.time }}</p>
                   </div>
                </div>
                <div 
                  class="text-lg font-bold font-mono number-pop" 
                  :class="quiz.score >= 90 ? 'text-emerald-500' : (quiz.score >= 60 ? 'text-amber-500' : 'text-red-500')"
                >
                   {{ quiz.score }}
                </div>
             </div>
             <div v-if="quizScores.length === 0" class="text-center py-10">
                <div class="w-14 h-14 mx-auto mb-3 rounded-full bg-yanzhi/10 flex items-center justify-center empty-state-float">
                  <TrendingUp class="w-7 h-7 text-yanzhi/50" />
                </div>
                <p class="text-shuimo/50 text-sm">暂无测验记录</p>
                <p class="text-xs text-shuimo/30 mt-1">完成章节测验后这里会显示成绩</p>
             </div>
          </div>
       </GlassCard>
    </div>

    <!-- 知识点掌握度 -->
    <div class="animate-slide-up" style="animation-delay: 0.4s; animation-fill-mode: both;">
      <KnowledgeMasteryChart
        v-if="authStore.user?.id"
        :student-id="authStore.user.id"
        @navigate="handleMasteryNavigate"
      />
    </div>
  </div>
</template>
