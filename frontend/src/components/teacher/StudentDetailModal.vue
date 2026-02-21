<script setup>
import { ref, computed, watch } from 'vue'
import { X, TrendingUp, BookOpen, Clock, Award, AlertTriangle, Calendar } from 'lucide-vue-next'
import { progressAPI } from '../../services/api'
import GlassCard from '../ui/GlassCard.vue'
import BaseModal from '../ui/BaseModal.vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  student: { type: Object, default: null },
  courseId: { type: [Number, String], default: null }
})

const emit = defineEmits(['close'])

// 状态
const loading = ref(false)
const analyticsData = ref(null)

// 学情状态配置
const statusConfig = {
  excellent: { label: '优秀', color: 'qingsong', bgColor: 'bg-qingsong/10', textColor: 'text-qingsong' },
  good: { label: '良好', color: 'tianlv', bgColor: 'bg-tianlv/10', textColor: 'text-tianlv' },
  'at-risk': { label: '需关注', color: 'zhizi', bgColor: 'bg-zhizi/10', textColor: 'text-zhizi' },
  inactive: { label: '不活跃', color: 'yanzhi', bgColor: 'bg-yanzhi/10', textColor: 'text-yanzhi' }
}

// 加载学生详细学情
const loadAnalytics = async () => {
  if (!props.courseId || !props.student?.studentId) return
  
  loading.value = true
  try {
    const res = await progressAPI.getStudentCourseAnalytics(props.courseId, props.student.studentId)
    if (res.code === 200 && res.data) {
      analyticsData.value = res.data
    }
  } catch (e) {
    console.error('加载学情数据失败', e)
  } finally {
    loading.value = false
  }
}

// 学习轨迹图表数据
const trajectoryChartData = computed(() => {
  if (!analyticsData.value?.learningTrajectory) return { labels: [], values: [] }
  const trajectory = analyticsData.value.learningTrajectory.slice(-14) // 最近14天
  return {
    labels: trajectory.map(t => t.date?.substring(5) || ''),
    values: trajectory.map(t => t.studyMinutes || 0)
  }
})

// 测验分数趋势数据
const quizTrendData = computed(() => {
  if (!analyticsData.value?.quizScoreTrend) return { labels: [], values: [] }
  const trend = analyticsData.value.quizScoreTrend
  return {
    labels: trend.map(t => t.title?.substring(0, 6) || ''),
    values: trend.map(t => t.score || 0)
  }
})

// 读屏摘要：为图表补充可理解的文本说明
const trajectorySummary = computed(() => {
  const values = trajectoryChartData.value.values
  if (!values.length) return '最近14天暂无学习轨迹数据。'

  const total = values.reduce((sum, item) => sum + item, 0)
  const max = Math.max(...values)
  const avg = Math.round(total / values.length)
  return `最近 ${values.length} 天累计学习 ${total} 分钟，单日最高 ${max} 分钟，日均 ${avg} 分钟。`
})

const quizSummary = computed(() => {
  const values = quizTrendData.value.values
  if (!values.length) return '暂无测验分数趋势数据。'

  const total = values.reduce((sum, item) => sum + item, 0)
  const max = Math.max(...values)
  const min = Math.min(...values)
  const avg = (total / values.length).toFixed(1)
  const latest = values[values.length - 1]
  return `共 ${values.length} 次测验，平均分 ${avg}，最高分 ${max}，最低分 ${min}，最近一次 ${latest} 分。`
})

const chapterSummary = computed(() => {
  const chapters = analyticsData.value?.chapterProgress || []
  if (!chapters.length) return '暂无章节学习详情数据。'

  const avgVideoRate = Math.round(
    chapters.reduce((sum, chapter) => sum + (chapter.videoRate || 0), 0) / chapters.length
  )
  return `共 ${chapters.length} 个章节，平均视频完成率 ${avgVideoRate}%。`
})

// 计算图表最大值
const getMaxValue = (values) => Math.max(...values, 1)

// 计算柱状图高度
const getBarHeight = (value, maxVal) => Math.max((value / maxVal) * 100, 2)

// 计算折线图点位置
const getLinePoints = (values, maxVal) => {
  if (!values.length) return ''
  const width = 100 / (values.length - 1 || 1)
  return values.map((v, i) => {
    const x = i * width
    const y = 100 - (v / maxVal) * 100
    return `${x},${y}`
  }).join(' ')
}

// 监听弹窗打开
watch(() => props.visible, (val) => {
  if (val && props.student && props.courseId) {
    loadAnalytics()
  }
})

 // 关闭弹窗
 const close = () => emit('close')
</script>

<template>
  <BaseModal :model-value="visible" max-width-class="max-w-4xl" :show-close="false" @update:modelValue="close">
    <template #header>
      <div class="flex items-center justify-between gap-4">
        <div class="flex items-center gap-4 min-w-0">
          <div class="w-14 h-14 rounded-full bg-gradient-to-br from-qinghua to-danqing flex items-center justify-center text-white text-xl font-bold shadow-lg shrink-0">
            {{ student?.name?.charAt(0) || '?' }}
          </div>
          <div class="min-w-0">
            <h2 class="text-xl font-bold text-shuimo truncate">{{ student?.name || '学生详情' }}</h2>
            <div class="flex items-center gap-3 mt-1 flex-wrap">
              <span
                :class="[
                  'text-xs px-2.5 py-1 rounded-full font-medium',
                  statusConfig[student?.learningStatus]?.bgColor,
                  statusConfig[student?.learningStatus]?.textColor
                ]"
              >
                {{ statusConfig[student?.learningStatus]?.label || '未知' }}
              </span>
              <span class="text-sm text-shuimo/60">课程进度: {{ student?.courseProgress || 0 }}%</span>
            </div>
          </div>
        </div>
        <button @click="close" aria-label="关闭学生详情弹窗" class="p-2 hover:bg-slate-100 rounded-full transition-colors">
          <X class="w-5 h-5 text-shuimo/60" />
        </button>
      </div>
    </template>

    <div v-if="loading" class="flex items-center justify-center py-20" role="status" aria-live="polite">
      <div class="animate-spin w-8 h-8 border-2 border-tianlv border-t-transparent rounded-full"></div>
      <span class="sr-only">正在加载学生学情数据</span>
    </div>

    <div v-else class="space-y-6">
      <div class="grid grid-cols-4 gap-4">
        <GlassCard class="p-4 text-center">
          <BookOpen class="w-6 h-6 mx-auto text-tianlv mb-2" />
          <div class="text-2xl font-bold text-shuimo font-mono">{{ student?.courseProgress || 0 }}%</div>
          <div class="text-xs text-shuimo/60 mt-1">学习进度</div>
        </GlassCard>
        <GlassCard class="p-4 text-center">
          <Award class="w-6 h-6 mx-auto text-qingsong mb-2" />
          <div class="text-2xl font-bold text-shuimo font-mono">{{ analyticsData?.quizAverage || '--' }}</div>
          <div class="text-xs text-shuimo/60 mt-1">测验均分</div>
        </GlassCard>
        <GlassCard class="p-4 text-center">
          <TrendingUp class="w-6 h-6 mx-auto text-qinghua mb-2" />
          <div class="text-2xl font-bold text-shuimo font-mono">{{ analyticsData?.homeworkCompletion || '--' }}</div>
          <div class="text-xs text-shuimo/60 mt-1">作业完成</div>
        </GlassCard>
        <GlassCard class="p-4 text-center">
          <Clock class="w-6 h-6 mx-auto text-zhizi mb-2" />
          <div class="text-2xl font-bold text-shuimo font-mono">{{ analyticsData?.totalStudyMinutes || 0 }}</div>
          <div class="text-xs text-shuimo/60 mt-1">学习时长(分)</div>
        </GlassCard>
      </div>

      <GlassCard v-if="student?.alerts?.length" class="p-4 bg-yanzhi/5 border-yanzhi/20">
        <div class="flex items-center gap-2 mb-3">
          <AlertTriangle class="w-5 h-5 text-yanzhi" />
          <span class="font-medium text-yanzhi">预警提示</span>
        </div>
        <div class="flex flex-wrap gap-2">
          <span
            v-for="(alert, idx) in student.alerts"
            :key="idx"
            class="text-sm bg-yanzhi/10 text-yanzhi px-3 py-1 rounded-full"
          >
            {{ alert }}
          </span>
        </div>
      </GlassCard>

      <GlassCard class="p-4">
        <h3 id="trajectory-chart-title" class="font-bold text-shuimo mb-4 flex items-center gap-2">
          <Calendar class="w-5 h-5 text-tianlv" />
          学习轨迹（近14天）
        </h3>
        <p class="sr-only">{{ trajectorySummary }}</p>
        <div v-if="trajectoryChartData.values.length" class="h-40" role="group" aria-labelledby="trajectory-chart-title">
          <div class="flex items-end justify-between gap-1 h-32 px-2">
            <div v-for="(value, index) in trajectoryChartData.values" :key="index" class="flex-1 flex flex-col items-center gap-1">
              <div
                class="w-full max-w-6 bg-tianlv/80 rounded-t transition-all duration-500 hover:bg-tianlv"
                :style="{ height: getBarHeight(value, getMaxValue(trajectoryChartData.values)) + '%' }"
                :title="`${trajectoryChartData.labels[index]}: ${value}分钟`"
                aria-hidden="true"
              ></div>
              <span class="text-[10px] text-shuimo/50 truncate w-full text-center" aria-hidden="true">{{ trajectoryChartData.labels[index] }}</span>
            </div>
          </div>
        </div>
        <div v-else class="text-center py-8 text-shuimo/40" role="status" aria-live="polite">暂无学习轨迹数据</div>
      </GlassCard>

      <GlassCard class="p-4">
        <h3 id="quiz-trend-title" class="font-bold text-shuimo mb-4 flex items-center gap-2">
          <TrendingUp class="w-5 h-5 text-qingsong" />
          测验分数趋势
        </h3>
        <p class="sr-only">{{ quizSummary }}</p>
        <div v-if="quizTrendData.values.length" class="h-40" role="group" aria-labelledby="quiz-trend-title">
          <div class="relative h-32 px-4">
            <div class="absolute inset-0 flex flex-col justify-between pointer-events-none" aria-hidden="true">
              <div class="border-b border-dashed border-slate-200 text-[10px] text-shuimo/40 text-right pr-1" aria-hidden="true">100</div>
              <div class="border-b border-dashed border-slate-200 text-[10px] text-shuimo/40 text-right pr-1" aria-hidden="true">75</div>
              <div class="border-b border-dashed border-slate-200 text-[10px] text-shuimo/40 text-right pr-1" aria-hidden="true">50</div>
              <div class="border-b border-dashed border-slate-200 text-[10px] text-shuimo/40 text-right pr-1" aria-hidden="true">25</div>
              <div class="text-[10px] text-shuimo/40 text-right pr-1" aria-hidden="true">0</div>
            </div>

            <svg class="absolute inset-0 w-full h-full overflow-visible" preserveAspectRatio="none" aria-hidden="true">
              <polyline
                :points="getLinePoints(quizTrendData.values, 100)"
                fill="none"
                stroke="#4ade80"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
                class="drop-shadow-sm"
              />
              <circle
                v-for="(value, index) in quizTrendData.values"
                :key="index"
                :cx="(index / (quizTrendData.values.length - 1 || 1)) * 100 + '%'"
                :cy="(100 - value) + '%'"
                r="4"
                fill="#4ade80"
                stroke="white"
                stroke-width="2"
                class="cursor-pointer hover:r-6"
              >
                  <title>{{ quizTrendData.labels[index] }}: {{ value }}分</title>
              </circle>
            </svg>
          </div>
          <div class="flex justify-between px-4 mt-2" aria-hidden="true">
            <span
              v-for="(label, index) in quizTrendData.labels"
              :key="index"
              class="text-[10px] text-shuimo/50 truncate"
              style="max-width: 60px;"
            >
              {{ label }}
            </span>
          </div>
        </div>
        <div v-else class="text-center py-8 text-shuimo/40" role="status" aria-live="polite">暂无测验数据</div>
      </GlassCard>

      <GlassCard v-if="analyticsData?.chapterProgress?.length" class="p-4">
        <h3 id="chapter-progress-title" class="font-bold text-shuimo mb-4 flex items-center gap-2">
          <BookOpen class="w-5 h-5 text-qinghua" />
          章节学习详情
        </h3>
        <p class="sr-only">{{ chapterSummary }}</p>
        <div class="space-y-3">
          <div
            v-for="chapter in analyticsData.chapterProgress"
            :key="chapter.chapterId"
            class="flex items-center gap-4 p-3 bg-slate-50/50 rounded-lg"
          >
            <div class="flex-1">
              <div class="font-medium text-sm text-shuimo">{{ chapter.title }}</div>
              <div class="flex items-center gap-3 mt-1">
                <div class="flex items-center gap-1">
                  <div class="w-20 h-1.5 bg-slate-200 rounded-full overflow-hidden" role="img" :aria-label="`${chapter.title} 视频完成率 ${chapter.videoRate}%`">
                    <div class="h-full bg-tianlv rounded-full" :style="{ width: chapter.videoRate + '%' }"></div>
                  </div>
                  <span class="text-xs text-shuimo/60">视频 {{ chapter.videoRate }}%</span>
                </div>
              </div>
            </div>
            <div class="text-right">
              <div class="text-lg font-bold font-mono" :class="chapter.quizScore >= 60 ? 'text-qingsong' : 'text-yanzhi'">
                {{ chapter.quizScore ?? '--' }}
              </div>
              <div class="text-xs text-shuimo/50">测验分</div>
            </div>
          </div>
        </div>
      </GlassCard>
    </div>
  </BaseModal>
</template>

<style scoped>
.modal-enter-active,
.modal-leave-active {
  /* P1：遮罩过渡限定透明度 */
  transition: opacity var(--motion-duration-medium) var(--motion-ease-standard);
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-from .animate-scale-in,
.modal-leave-to .animate-scale-in {
  transform: scale(0.95);
}

.animate-scale-in {
  animation: scaleIn var(--motion-duration-medium) var(--motion-ease-standard);
}

@keyframes scaleIn {
  from {
    opacity: 0;
    transform: scale(0.95);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}
</style>
