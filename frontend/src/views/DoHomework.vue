<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useConfirmStore } from '../stores/confirm'
import { homeworkAPI } from '../services/api'
import { CheckCircle, ArrowLeft, Clock, FileText, Sparkles } from 'lucide-vue-next'
import { useToastStore } from '../stores/toast'
import BaseButton from '../components/ui/BaseButton.vue'
import GlassCard from '../components/ui/GlassCard.vue'
import SkeletonLoader from '../components/SkeletonLoader.vue'
import HomeworkQA from '../components/student/HomeworkQA.vue'
import AnimatedNumber from '../components/ui/AnimatedNumber.vue'
import { formatDateTimeCN } from '../utils/datetime'
import { logger } from '../utils/logger'
import QuestionCard from '../components/homework/QuestionCard.vue'

const router = useRouter()
const route = useRoute()

// 常量定义: 学生端首页路由
const STUDENT_HOME_ROUTE = '/student'
const authStore = useAuthStore()
const toast = useToastStore()
const confirmStore = useConfirmStore()

const homework = ref(null)
const questions = ref([])
const studentAnswers = reactive({})
const submissionData = ref(null)
const answerResults = ref([])
const loading = ref(true)
const submitting = ref(false)
const isViewMode = ref(false)
const showQA = ref(false)

const displayScore = computed(() => {
  if (!submissionData.value) return 0
  return submissionData.value.totalScore ?? submissionData.value.objectiveScore ?? 0
})

const completionRate = computed(() => {
  if (questions.value.length === 0) return 0
  const answeredCount = Object.keys(studentAnswers).filter(k => studentAnswers[k] && studentAnswers[k].length > 0).length
  return Math.round((answeredCount / questions.value.length) * 100)
})

// 中文注释：进度条使用 scaleX，避免 width 过渡触发布局重排
const getCompletionScaleStyle = () => ({
  transform: `scaleX(${Math.max(0, Math.min(100, completionRate.value)) / 100})`
})

// 是否全部完成
const isAllCompleted = computed(() => completionRate.value === 100)

// 判断是否可以提问：必须是查看模式（已提交）且作业已被批改
const canAskQuestion = computed(() => {
  if (!isViewMode.value || !submissionData.value) return false
  // 检查是否已批改：submitStatus为graded或者gradedAt有值
  return submissionData.value.submitStatus === 'graded' || submissionData.value.gradedAt != null
})

onMounted(async () => {
  isViewMode.value = route.query.view === 'true'
  await loadHomework()
  if (isViewMode.value) {
    await loadSubmission()
  }
})

const loadHomework = async () => {
  try {
    const homeworkId = route.params.id
    if (!homeworkId || !/^\d+$/.test(homeworkId as string)) {
      router.replace('/404')
      return
    }
    const res = await homeworkAPI.getDetail(homeworkId)
    if (res.data) {
      homework.value = res.data.homework
      questions.value = res.data.questions || []
    }
  } catch (e) {
    logger.error('加载作业失败:', e)
    toast.error('加载作业失败: ' + e.message)
    // 使用显式跳转替代 router.back()，提升可靠性
    router.push(STUDENT_HOME_ROUTE)
  } finally {
    loading.value = false
  }
}

const loadSubmission = async () => {
  try {
    const homeworkId = route.params.id
    if (!homeworkId || !/^\d+$/.test(homeworkId as string)) {
      router.replace('/404')
      return
    }
    const studentId = authStore.user?.id
    const res = await homeworkAPI.getSubmission(homeworkId, studentId)
    if (res.data) {
      submissionData.value = res.data.submission
      answerResults.value = res.data.answers || []
      
      // 填充学生答案到studentAnswers
      answerResults.value.forEach(answer => {
        studentAnswers[answer.questionId] = answer.studentAnswer
      })
    }
  } catch (e) {
    logger.error('加载提交记录失败:', e)
  }
}

const getAnswerResult = (questionId: number) => {
  return answerResults.value.find(a => a.questionId === questionId)
}

const submitHomework = async () => {
  const answers = questions.value.map(q => ({
    questionId: q.id,
    answer: studentAnswers[q.id] || ''
  }))
  
  if (answers.some(a => !a.answer)) {
    const confirmed = await confirmStore.show({
      title: '题目未完成',
      message: '还有题目未作答，确定要提交吗？',
      type: 'warning',
      confirmText: '确定提交',
      cancelText: '继续作答'
    })
    if (!confirmed) {
      return
    }
  }
  
  submitting.value = true
  try {
    const dto = {
      homeworkId: homework.value.id,
      studentId: authStore.user?.id,
      answers: answers
    }
    
    const res = await homeworkAPI.submit(dto)
    if (res.code === 200) {
      submissionData.value = res.data
      // answerResults 已是数组，不需要 JSON.parse
      answerResults.value = res.data.answerResults || []
      toast.success('提交成功！得分：' + (res.data?.objectiveScore || 0) + '分')
      router.push(STUDENT_HOME_ROUTE)
    } else {
      toast.error('提交失败: ' + res.message)
    }
  } catch (e) {
    logger.error('提交失败:', e)
    toast.error('提交失败: ' + e.message)
  } finally {
    submitting.value = false
  }
}

const goBack = () => {
  router.push(STUDENT_HOME_ROUTE)
}
</script>

<template>
  <div class="min-h-screen flex flex-col animate-fade-in">
    <!-- 顶部导航 (Sticky) -->
    <header class="sticky top-0 z-30 bg-white/80 backdrop-blur-xl border-b border-white/50 shadow-sm">
      <div class="max-w-4xl mx-auto px-4 py-3">
        <div class="flex items-center justify-between mb-4">
          <div class="flex items-center gap-4">
            <button @click="goBack" class="p-2 hover:bg-slate-100 rounded-xl transition-colors text-shuimo/70 hover:text-qinghua group">
              <ArrowLeft class="w-5 h-5 group-hover:-translate-x-0.5 transition-transform" />
            </button>
            <div v-if="loading" class="h-6 w-32 bg-slate-200 rounded animate-pulse"></div>
            <div v-else-if="homework">
              <h1 class="text-lg font-bold text-shuimo font-song">{{ homework.title }}</h1>
            </div>
          </div>
          
          <div v-if="!loading && !isViewMode" class="flex items-center gap-3">
             <div class="hidden sm:flex flex-col items-end">
               <span class="text-xs text-shuimo/50">已完成</span>
               <span class="text-sm font-bold font-mono" :class="isAllCompleted ? 'text-tianlv' : 'text-qinghua'">
                 <AnimatedNumber :value="completionRate" :duration="300" />%
               </span>
             </div>
             <BaseButton 
               @click="submitHomework" 
               :disabled="submitting" 
                variant="custom"
                :class="[
                  'px-6 py-2 text-white rounded-xl shadow-lg transition-[transform,box-shadow,background-color] duration-300',
                  isAllCompleted 
                    ? 'bg-gradient-to-r from-tianlv to-qingsong shadow-tianlv/30 animate-pulse-subtle' 
                    : 'bg-gradient-to-r from-zhizi to-tanxiang shadow-zhizi/20'
                ]"
             >
               <Sparkles v-if="isAllCompleted && !submitting" class="w-4 h-4 mr-1" />
               {{ submitting ? '提交中...' : (isAllCompleted ? '全部完成，提交作业' : '提交作业') }}
             </BaseButton>
          </div>
          <div v-else-if="isViewMode && submissionData" class="flex items-center gap-3">
             <BaseButton 
               v-if="canAskQuestion"
               @click="showQA = !showQA" 
               variant="secondary"
               class="px-4 py-2"
             >
               {{ showQA ? '返回查看' : '提问' }}
             </BaseButton>
             <div v-else-if="!canAskQuestion && submissionData.submitStatus !== 'graded' && !submissionData.gradedAt" 
                  class="px-3 py-1.5 bg-amber-50 text-amber-600 rounded-lg text-sm flex items-center gap-1">
               <Clock class="w-4 h-4" />
               待批改后可提问
             </div>
             <div class="px-4 py-1.5 bg-qinghua/10 text-qinghua rounded-lg font-bold">
              {{ displayScore }} 分
            </div>
          </div>
        </div>
        
        <!-- 进度条 -->
        <div v-if="!loading && !isViewMode" class="h-1 bg-slate-100 rounded-full overflow-hidden w-full">
          <div
            class="h-full bg-gradient-to-r from-qinghua to-halanzi origin-left transition-transform duration-500 ease-out will-change-transform"
            :style="getCompletionScaleStyle()"
          ></div>
        </div>
      </div>
    </header>

    <!-- 内容区域 -->
    <main class="flex-1 max-w-4xl mx-auto w-full px-4 py-8">
      <!-- 加载状态 -->

      <div v-if="loading" class="space-y-6">
        <div class="space-y-2">
          <SkeletonLoader type="default" class="h-8 w-1/3" />
          <SkeletonLoader type="default" class="h-4 w-1/4" />
        </div>
        <SkeletonLoader v-for="i in 3" :key="i" type="default" class="h-40" />
      </div>

      <!-- 作业内容 -->

      <div v-else class="space-y-8 animate-slide-up">
        <!-- 问答区域 -->

        <div v-if="showQA && homework">
          <HomeworkQA
            :homework-id="homework.id"
            :student-id="authStore.user?.id"
          />
        </div>
        
        <!-- 作业详情 -->

        <div v-else>
        <!-- 作业信息卡片 -->

        <GlassCard class="flex items-center justify-between !py-4 !px-6 bg-gradient-to-r from-qinghua/5 to-transparent border-qinghua/20">
          <div class="flex items-center gap-6 text-sm text-shuimo/70">
            <span class="flex items-center gap-2">
              <FileText class="w-4 h-4 text-qinghua" />
              共 {{ questions.length }} 题
            </span>
            <span class="flex items-center gap-2">
              <CheckCircle class="w-4 h-4 text-tianlv" />
              总分 {{ homework.totalScore }} 分
            </span>
          </div>
          <div v-if="isViewMode && submissionData" class="text-sm text-shuimo/50">
             提交时间：{{ formatDateTimeCN(submissionData.submittedAt, '-') }}
          </div>
        </GlassCard>

        <!-- 题目列表 -->

        <div class="space-y-6">
          <TransitionGroup name="question-list" appear>
            <QuestionCard
              v-for="(question, index) in questions"
              :key="question.id"
              :question="question"
              :index="index"
              :is-view-mode="isViewMode"
              v-model="studentAnswers[question.id]"
              :answer-result="getAnswerResult(question.id)"
            />
          </TransitionGroup>
        </div>

        <div v-if="isViewMode" class="flex justify-center pt-8 pb-12">
          <BaseButton 
             @click="goBack"
             variant="custom"
             class="px-8 py-3 bg-white border border-slate-200 text-shuimo rounded-xl hover:bg-slate-50 hover:shadow-md transition-[background-color,box-shadow,color] duration-300 font-medium"
          >
            返回作业列表
          </BaseButton>
        </div>
        </div>
      </div>
    </main>
  </div>
</template>


<style scoped>
.animate-pulse-subtle {
  animation: pulse-subtle var(--motion-duration-medium) var(--motion-ease-standard) infinite;
  animation-iteration-count: var(--motion-loop-iterations-attention, 4);
  animation-fill-mode: both;
}

@keyframes pulse-subtle {
  0%, 100% {
    box-shadow: 0 10px 25px -5px rgba(var(--color-tianlv), 0.3);
  }
  50% {
    box-shadow: 0 10px 35px -5px rgba(var(--color-tianlv), 0.5);
  }
}
</style>