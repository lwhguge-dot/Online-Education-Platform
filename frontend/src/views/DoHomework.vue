<script setup>
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useConfirmStore } from '../stores/confirm'
import { homeworkAPI } from '../services/api'
import { X, CheckCircle, ArrowLeft, Clock, FileText, AlertCircle, Sparkles } from 'lucide-vue-next'
import { useToastStore } from '../stores/toast'
import BaseButton from '../components/ui/BaseButton.vue'
import GlassCard from '../components/ui/GlassCard.vue'
import SkeletonLoader from '../components/SkeletonLoader.vue'
import HomeworkQA from '../components/student/HomeworkQA.vue'
import AnimatedNumber from '../components/ui/AnimatedNumber.vue'
import { formatDateTimeCN } from '../utils/datetime'

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

// 是否全部完成
const isAllCompleted = computed(() => completionRate.value === 100)

// 选项选中动画状态
const animatingOptions = reactive({})
const triggerOptionAnimation = (questionId, optionKey) => {
  const key = `${questionId}-${optionKey}`
  animatingOptions[key] = true
  setTimeout(() => {
    animatingOptions[key] = false
  }, 300)
}

// 检查选项是否正在动画
const isOptionAnimating = (questionId, optionKey) => {
  return animatingOptions[`${questionId}-${optionKey}`]
}

// 安全解析题目选项，避免模板内反复 JSON.parse 和异常中断
const parseQuestionOptions = (options) => {
  if (Array.isArray(options)) {
    return options
  }
  if (typeof options === 'string') {
    try {
      const parsed = JSON.parse(options)
      return Array.isArray(parsed) ? parsed : []
    } catch (error) {
      console.warn('题目选项解析失败:', error)
      return []
    }
  }
  return []
}

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
    const res = await homeworkAPI.getDetail(homeworkId)
    if (res.data) {
      homework.value = res.data.homework
      questions.value = res.data.questions || []
    }
  } catch (e) {
    console.error('加载作业失败:', e)
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
    console.error('加载提交记录失败:', e)
  }
}

const getAnswerResult = (questionId) => {
  return answerResults.value.find(a => a.questionId === questionId)
}

/**
 * 切换多选选项状态
 * @param {number} questionId 题目ID
 * @param {string} option 选项标识 (A, B, C...)
 * 
 * 逻辑：
 * 1. 触发选中动画
 * 2. 如果已选则移除，未选则追加
 * 3. 始终保持选项按字母顺序排序，方便后续比对
 */
const toggleMultipleChoice = (questionId, option) => {
  if (isViewMode.value) return
  triggerOptionAnimation(questionId, option)
  const current = studentAnswers[questionId] || ''
  if (current.includes(option)) {
    studentAnswers[questionId] = current.replace(option, '').split('').sort().join('')
  } else {
    studentAnswers[questionId] = (current + option).split('').sort().join('')
  }
}

// 单选点击处理（带动画）
const selectSingleChoice = (questionId, option) => {
  if (isViewMode.value) return
  triggerOptionAnimation(questionId, option)
  studentAnswers[questionId] = option
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
    console.error('提交失败:', e)
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
                 'px-6 py-2 text-white rounded-xl shadow-lg transition-all duration-300',
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
          <div class="h-full bg-gradient-to-r from-qinghua to-halanzi transition-all duration-500 ease-out" 
               :style="{ width: completionRate + '%' }"></div>
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
          <GlassCard v-for="(question, index) in questions" :key="question.id" 
                     :hoverable="!isViewMode"
                     class="transition-all duration-500 question-card"
                     :class="{'ring-2 ring-qinghua/20': !isViewMode}"
                     :style="{ '--delay': index * 0.08 + 's' }">
            
            <div class="flex items-start gap-4">
              <!-- 题号 -->

              <span class="flex-shrink-0 w-8 h-8 flex items-center justify-center bg-qinghua/10 text-qinghua rounded-lg font-bold font-song text-lg mt-0.5 question-number">
                {{ index + 1 }}
              </span>
              
              <div class="flex-1 min-w-0">
                <!-- 题目内容 -->

                <div class="mb-4">
                  <div class="flex items-center gap-3 mb-2">
                    <span class="text-xs px-2 py-0.5 rounded bg-slate-100 text-shuimo/60 font-medium">
                      {{ question.questionType === 'single' ? '单选题' : question.questionType === 'multiple' ? '多选题' : question.questionType === 'fill' ? '填空题' : '主观题' }}
                    </span>
                    <span class="text-xs text-shuimo/40">
                      {{ question.score }} 分
                    </span>
                  </div>
                  <h3 class="text-lg font-medium text-shuimo leading-relaxed">{{ question.content }}</h3>
                </div>

                <!-- 单选题选项 -->

                <div v-if="question.questionType === 'single'" class="space-y-3">
                  <div v-for="(option, idx) in parseQuestionOptions(question.options)" :key="idx" 
                       @click="selectSingleChoice(question.id, String.fromCharCode(65 + idx))"
                       :class="['group relative p-4 rounded-xl border transition-all duration-300 option-item',
                                !isViewMode && 'cursor-pointer hover:border-qinghua/50 hover:bg-white/80',
                                studentAnswers[question.id] === String.fromCharCode(65 + idx) 
                                  ? 'border-qinghua bg-qinghua/5 shadow-sm ring-1 ring-qinghua/20' 
                                  : 'border-slate-200/60 bg-white/40',
                                isOptionAnimating(question.id, String.fromCharCode(65 + idx)) && 'animate-option-select']">
                    <div class="flex items-center gap-3">
                      <div :class="['w-5 h-5 rounded-full border flex items-center justify-center transition-all duration-200',
                                    studentAnswers[question.id] === String.fromCharCode(65 + idx)
                                      ? 'border-qinghua/50 bg-qinghua text-white scale-110'
                                      : 'border-slate-300 group-hover:border-qinghua/50']">
                         <div v-if="studentAnswers[question.id] === String.fromCharCode(65 + idx)" class="w-2 h-2 bg-white rounded-full animate-scale-in"></div>
                      </div>
                      <span class="font-medium text-shuimo/50 w-4">{{ String.fromCharCode(65 + idx) }}.</span>
                      <span :class="['flex-1 text-shuimo transition-colors', studentAnswers[question.id] === String.fromCharCode(65 + idx) ? 'font-medium' : '']">
                        {{ option }}
                      </span>
                    </div>
                  </div>
                </div>

                <!-- 多选题选项 -->

                <div v-else-if="question.questionType === 'multiple'" class="space-y-3">
                  <div v-for="(option, idx) in parseQuestionOptions(question.options)" :key="idx"
                       @click="toggleMultipleChoice(question.id, String.fromCharCode(65 + idx))"
                       :class="['group relative p-4 rounded-xl border transition-all duration-300 option-item',
                                !isViewMode && 'cursor-pointer hover:border-qinghua/50 hover:bg-white/80',
                                (studentAnswers[question.id] || '').includes(String.fromCharCode(65 + idx))
                                  ? 'border-qinghua/50 bg-qinghua/5 shadow-sm ring-1 ring-qinghua/20' 
                                  : 'border-slate-200/60 bg-white/40',
                                isOptionAnimating(question.id, String.fromCharCode(65 + idx)) && 'animate-option-select']">
                    <div class="flex items-center gap-3">
                      <div :class="['w-5 h-5 rounded border flex items-center justify-center transition-all duration-200',
                                    (studentAnswers[question.id] || '').includes(String.fromCharCode(65 + idx))
                                      ? 'border-qinghua bg-qinghua text-white scale-110'
                                      : 'border-slate-300 group-hover:border-qinghua/50']">
                         <CheckCircle v-if="(studentAnswers[question.id] || '').includes(String.fromCharCode(65 + idx))" class="w-3.5 h-3.5 animate-scale-in" />
                      </div>
                      <span class="font-medium text-shuimo/50 w-4">{{ String.fromCharCode(65 + idx) }}.</span>
                      <span :class="['flex-1 text-shuimo transition-colors', (studentAnswers[question.id] || '').includes(String.fromCharCode(65 + idx)) ? 'font-medium' : '']">
                        {{ option }}
                      </span>
                    </div>
                  </div>
                </div>

                <!-- 填空题 -->

                <div v-else-if="question.questionType === 'fill'" class="relative">
                  <input v-model="studentAnswers[question.id]" type="text" :disabled="isViewMode"
                         class="w-full px-4 py-3 rounded-xl border border-slate-200/60 bg-white/50 focus:bg-white focus:border-qinghua focus:ring-4 focus:ring-qinghua/10 outline-none transition-all disabled:bg-slate-50 disabled:text-shuimo/60 placeholder:text-shuimo/30"
                         placeholder="在此输入您的答案..." />
                </div>

                <!-- 主观题 -->

                <div v-else-if="question.questionType === 'subjective'">
                  <textarea v-model="studentAnswers[question.id]" rows="5" :disabled="isViewMode"
                            class="w-full px-4 py-3 rounded-xl border border-slate-200/60 bg-white/50 focus:bg-white focus:border-qinghua focus:ring-4 focus:ring-qinghua/10 outline-none resize-none transition-all disabled:bg-slate-50 disabled:text-shuimo/60 placeholder:text-shuimo/30"
                            placeholder="在此输入您的详细解答..."></textarea>
                </div>

                <!-- 批改结果 -->

                <Transition name="result-expand">
                <div v-if="isViewMode && getAnswerResult(question.id)" class="mt-6 pt-4 border-t border-slate-100/50 space-y-3">
                   <div class="flex items-center justify-between">
                     <span :class="['flex items-center gap-2 font-medium px-3 py-1 rounded-lg text-sm result-badge', 
                       getAnswerResult(question.id).isCorrect === 1 ? 'bg-qingsong/10 text-qingsong' : 
                       getAnswerResult(question.id).isCorrect === 0 ? 'bg-yanzhi/10 text-yanzhi' : 'bg-zhizi/10 text-zhizi']">
                      <component :is="getAnswerResult(question.id).isCorrect === 1 ? CheckCircle : getAnswerResult(question.id).isCorrect === 0 ? X : AlertCircle" 
                                 :class="['w-4 h-4', getAnswerResult(question.id).isCorrect === 1 ? 'animate-check-mark' : '']" />
                      {{ getAnswerResult(question.id).isCorrect === 1 ? '回答正确' : getAnswerResult(question.id).isCorrect === 0 ? '回答错误' : (getAnswerResult(question.id).score !== null ? '已批改' : '等待批改') }}
                    </span>
                    
                     <span class="font-bold text-lg font-mono text-qinghua">
                       {{ getAnswerResult(question.id).score !== null ? getAnswerResult(question.id).score : '--' }} <span class="text-xs text-shuimo/40 font-normal">/ {{ question.score }}</span>
                     </span>
                   </div>
                   
                   <div v-if="getAnswerResult(question.id).isCorrect === 0" class="p-3 bg-slate-50/80 rounded-xl border border-slate-100 animate-slide-down">
                     <p class="text-xs text-shuimo/50 mb-1">正确答案</p>
                     <p class="text-sm font-medium text-shuimo">{{ getAnswerResult(question.id).correctAnswer }}</p>
                   </div>
                   
                   <div v-if="getAnswerResult(question.id).aiFeedback || getAnswerResult(question.id).teacherFeedback" class="space-y-2">
                     <div v-if="getAnswerResult(question.id).aiFeedback" class="flex gap-3 p-3 bg-qinghua/5 rounded-xl border border-qinghua/10 animate-slide-down" style="animation-delay: 0.1s">
                       <div class="w-6 h-6 rounded-full bg-qinghua text-white flex items-center justify-center text-xs flex-shrink-0">AI</div>
                       <p class="text-sm text-shuimo/80 leading-relaxed">{{ getAnswerResult(question.id).aiFeedback }}</p>
                     </div>
                     <div v-if="getAnswerResult(question.id).teacherFeedback" class="flex gap-3 p-3 bg-zhizi/5 rounded-xl border border-zhizi/10 animate-slide-down" style="animation-delay: 0.2s">
                       <div class="w-6 h-6 rounded-full bg-zhizi text-white flex items-center justify-center text-xs flex-shrink-0">师</div>
                       <p class="text-sm text-shuimo/80 leading-relaxed">{{ getAnswerResult(question.id).teacherFeedback }}</p>
                     </div>
                   </div>
                </div>
                </Transition>
              </div>
            </div>
          </GlassCard>
          </TransitionGroup>
        </div>

        <div v-if="isViewMode" class="flex justify-center pt-8 pb-12">
          <BaseButton 
             @click="goBack"
             variant="custom"
             class="px-8 py-3 bg-white border border-slate-200 text-shuimo rounded-xl hover:bg-slate-50 hover:shadow-md transition-all font-medium"
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
/* 题目卡片入场动画 */
.question-list-enter-active {
  animation: question-enter 0.5s ease-out both;
  animation-delay: var(--delay, 0s);
}

.question-list-leave-active {
  animation: question-leave 0.3s ease-in both;
}

@keyframes question-enter {
  from {
    opacity: 0;
    transform: translateY(30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes question-leave {
  from {
    opacity: 1;
    transform: translateY(0);
  }
  to {
    opacity: 0;
    transform: translateY(-20px);
  }
}

/* 题号动画 */
.question-number {
  animation: number-pop 0.4s ease-out both;
  animation-delay: calc(var(--delay, 0s) + 0.2s);
}

@keyframes number-pop {
  0% {
    transform: scale(0);
    opacity: 0;
  }
  70% {
    transform: scale(1.2);
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}

/* 选项选中弹性动画 */
.animate-option-select {
  animation: option-bounce 0.3s ease-out;
}

@keyframes option-bounce {
  0% {
    transform: scale(1);
  }
  50% {
    transform: scale(0.97);
  }
  100% {
    transform: scale(1);
  }
}

/* 选中圆点/勾选动画 */
.animate-scale-in {
  animation: scale-in 0.2s ease-out;
}

@keyframes scale-in {
  from {
    transform: scale(0);
    opacity: 0;
  }
  to {
    transform: scale(1);
    opacity: 1;
  }
}

/* 批改结果展开动画 */
.result-expand-enter-active {
  animation: result-expand 0.4s ease-out;
}

.result-expand-leave-active {
  animation: result-collapse 0.3s ease-in;
}

@keyframes result-expand {
  from {
    opacity: 0;
    max-height: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    max-height: 500px;
    transform: translateY(0);
  }
}

@keyframes result-collapse {
  from {
    opacity: 1;
    max-height: 500px;
  }
  to {
    opacity: 0;
    max-height: 0;
  }
}

/* 正确答案打勾动画 */
.animate-check-mark {
  animation: check-mark 0.4s ease-out;
}

@keyframes check-mark {
  0% {
    transform: scale(0) rotate(-45deg);
    opacity: 0;
  }
  50% {
    transform: scale(1.3) rotate(0deg);
  }
  100% {
    transform: scale(1) rotate(0deg);
    opacity: 1;
  }
}

/* 下滑动画 */
.animate-slide-down {
  animation: slide-down 0.4s ease-out both;
}

@keyframes slide-down {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 微弱脉冲动画（完成状态按钮） */
.animate-pulse-subtle {
  animation: pulse-subtle 2s ease-in-out infinite;
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
