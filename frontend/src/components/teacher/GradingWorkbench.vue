<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { homeworkAPI } from '../../services/api'
import { useToastStore } from '../../stores/toast'
import { 
  ChevronLeft, ChevronRight, Save, Check, X, 
  FileText, User, Clock, Award, MessageSquare
} from 'lucide-vue-next'
import GlassCard from '../ui/GlassCard.vue'
import BaseButton from '../ui/BaseButton.vue'
import { formatDateTimeCN } from '../../utils/datetime'

const props = defineProps({
  homeworkId: { type: Number, required: true },
  onClose: { type: Function, default: () => {} }
})

const emit = defineEmits(['close', 'graded'])

const toast = useToastStore()

// 状态
const loading = ref(true)
const saving = ref(false)
const homeworkInfo = ref(null)
const submissions = ref([])
const currentIndex = ref(0)
const currentDetail = ref(null)
const grades = ref({})
const overallFeedback = ref('')

// 计算属性
const currentSubmission = computed(() => submissions.value[currentIndex.value] || null)
const gradedCount = computed(() => submissions.value.filter(s => s.status === 'graded').length)
const totalCount = computed(() => submissions.value.length)
const progressPercent = computed(() => totalCount.value > 0 ? Math.round(gradedCount.value / totalCount.value * 100) : 0)

const canGoPrev = computed(() => currentIndex.value > 0)
const canGoNext = computed(() => currentIndex.value < submissions.value.length - 1)

// 加载待批改列表
const loadPendingSubmissions = async () => {
  loading.value = true
  try {
    const res = await homeworkAPI.getPendingSubmissions(props.homeworkId)
    if (res.code === 200 && res.data) {
      homeworkInfo.value = res.data.homework
      submissions.value = res.data.submissions || []
      
      // 自动选择第一个未批改的
      const firstUngraded = submissions.value.findIndex(s => s.hasUngraded)
      if (firstUngraded >= 0) {
        currentIndex.value = firstUngraded
      }
      
      if (submissions.value.length > 0) {
        await loadSubmissionDetail(submissions.value[currentIndex.value].id)
      }
    }
  } catch {
    toast.error('加载失败')
  } finally {
    loading.value = false
  }
}

// 加载提交详情
const loadSubmissionDetail = async (submissionId) => {
  try {
    const res = await homeworkAPI.getSubmissionDetail(submissionId)
    if (res.code === 200 && res.data) {
      currentDetail.value = res.data
      
      // 初始化评分表单
      grades.value = {}
      overallFeedback.value = res.data.submission?.feedback || ''
      
      for (const answer of res.data.answers || []) {
        grades.value[answer.questionId] = {
          score: answer.score,
          feedback: answer.teacherFeedback || ''
        }
      }
    }
  } catch {
    toast.error('加载详情失败')
  }
}

// 选择提交
const selectSubmission = async (index) => {
  if (index === currentIndex.value) return
  currentIndex.value = index
  await loadSubmissionDetail(submissions.value[index].id)
}

// 上一个
const prevSubmission = async () => {
  if (canGoPrev.value) {
    await selectSubmission(currentIndex.value - 1)
  }
}

// 下一个
const nextSubmission = async () => {
  if (canGoNext.value) {
    await selectSubmission(currentIndex.value + 1)
  }
}

// 保存批改
const saveGrade = async (autoNext = false) => {
  if (!currentSubmission.value) return
  
  saving.value = true
  try {
    const gradeList = Object.entries(grades.value).map(([questionId, grade]) => ({
      questionId: parseInt(questionId),
      score: grade.score,
      feedback: grade.feedback
    }))
    
    await homeworkAPI.gradeSubmission(currentSubmission.value.id, {
      grades: gradeList,
      overallFeedback: overallFeedback.value
    })
    
    toast.success('批改已保存')
    
    // 更新本地状态
    submissions.value[currentIndex.value].status = 'graded'
    submissions.value[currentIndex.value].hasUngraded = false
    
    emit('graded')
    
    // 自动跳转下一个未批改
    if (autoNext) {
      const nextUngraded = submissions.value.findIndex((s, i) => i > currentIndex.value && s.hasUngraded)
      if (nextUngraded >= 0) {
        await selectSubmission(nextUngraded)
      } else if (canGoNext.value) {
        await nextSubmission()
      }
    }
  } catch {
    toast.error('保存失败')
  } finally {
    saving.value = false
  }
}

// 键盘快捷键
const handleKeydown = (e) => {
  if (e.key === 'ArrowRight' && !e.ctrlKey) {
    nextSubmission()
  } else if (e.key === 'ArrowLeft' && !e.ctrlKey) {
    prevSubmission()
  } else if (e.ctrlKey && e.key === 's') {
    e.preventDefault()
    saveGrade(false)
  } else if (e.ctrlKey && e.key === 'Enter') {
    e.preventDefault()
    saveGrade(true)
  }
}

// 格式化时间
const formatTime = (dateStr) => {
  return formatDateTimeCN(dateStr, '-')
}

// 安全解析选项，避免模板内重复 JSON.parse 且降低异常风险
const parseAnswerOptions = (options) => {
  if (Array.isArray(options)) {
    return options
  }
  if (typeof options === 'string') {
    try {
      const parsed = JSON.parse(options)
      return Array.isArray(parsed) ? parsed : []
    } catch (error) {
      console.warn('答案选项解析失败:', error)
      return []
    }
  }
  return []
}

onMounted(() => {
  loadPendingSubmissions()
  window.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <Teleport to="body">
    <div class="fixed inset-0 z-[100] bg-white flex flex-col">
      <!-- 顶部工具栏 -->
      <header class="flex-shrink-0 h-16 px-6 flex items-center justify-between border-b bg-white/80 backdrop-blur-xl z-10">
      <div class="flex items-center gap-4">
        <button @click="emit('close')" class="p-2 hover:bg-slate-100 rounded-lg transition-colors">
          <X class="w-5 h-5 text-shuimo" />
        </button>
        <div>
          <h2 class="font-bold text-lg text-shuimo">批改工作台</h2>
          <p class="text-sm text-shuimo/60">{{ homeworkInfo?.title || '加载中...' }}</p>
        </div>
      </div>
      
      <div class="flex items-center gap-6">
        <!-- 进度 -->
        <div class="flex items-center gap-3">
          <span class="text-sm text-shuimo/60">批改进度</span>
          <div class="w-32 h-2 bg-slate-100 rounded-full overflow-hidden">
            <div class="h-full bg-tianlv rounded-full transition-all" :style="{ width: progressPercent + '%' }"></div>
          </div>
          <span class="text-sm font-mono font-bold text-tianlv">{{ gradedCount }}/{{ totalCount }}</span>
        </div>
        
        <!-- 快捷键提示 -->
        <div class="text-xs text-shuimo/40 hidden lg:block">
          <span class="px-1.5 py-0.5 bg-slate-100 rounded">←→</span> 切换
          <span class="px-1.5 py-0.5 bg-slate-100 rounded ml-2">Ctrl+S</span> 保存
          <span class="px-1.5 py-0.5 bg-slate-100 rounded ml-2">Ctrl+Enter</span> 保存并下一个
        </div>
      </div>
    </header>

    <!-- 主内容区 -->
    <div class="flex-1 flex overflow-hidden" v-if="!loading">
      <!-- 左侧：学生列表 -->
      <aside class="w-64 flex-shrink-0 border-r bg-slate-50/50 overflow-y-auto">
        <div class="p-4 border-b bg-white sticky top-0">
          <div class="text-sm text-shuimo/60">提交列表</div>
        </div>
        <nav class="p-2 space-y-1">
          <button
            v-for="(sub, idx) in submissions"
            :key="sub.id"
            @click="selectSubmission(idx)"
            :class="[
              'w-full text-left p-3 rounded-xl transition-all',
              currentIndex === idx 
                ? 'bg-tianlv/10 border border-tianlv/30' 
                : 'hover:bg-white border border-transparent'
            ]"
          >
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-2">
                <div class="w-8 h-8 rounded-full bg-gradient-to-br from-qinghua to-tianlv flex items-center justify-center text-white text-sm font-bold">
                  {{ sub.studentName?.charAt(0) || '?' }}
                </div>
                <div>
                  <div class="font-medium text-sm text-shuimo">{{ sub.studentName }}</div>
                  <div class="text-xs text-shuimo/50">{{ formatTime(sub.submittedAt) }}</div>
                </div>
              </div>
              <div v-if="sub.status === 'graded'" class="w-5 h-5 rounded-full bg-qingsong/20 flex items-center justify-center">
                <Check class="w-3 h-3 text-qingsong" />
              </div>
              <div v-else-if="sub.hasUngraded" class="w-2 h-2 rounded-full bg-yanzhi animate-pulse"></div>
            </div>
          </button>
        </nav>
      </aside>

      <!-- 中间：答案展示 -->
      <main class="flex-1 overflow-y-auto p-6 bg-slate-50/30">
        <div v-if="currentDetail" class="max-w-3xl mx-auto space-y-6">
          <!-- 学生信息卡片 -->
          <GlassCard class="p-4">
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-4">
                <div class="w-12 h-12 rounded-full bg-gradient-to-br from-qinghua to-tianlv flex items-center justify-center text-white text-lg font-bold">
                  {{ currentDetail.submission?.studentName?.charAt(0) || '?' }}
                </div>
                <div>
                  <div class="font-bold text-shuimo">{{ currentDetail.submission?.studentName }}</div>
                  <div class="text-sm text-shuimo/60 flex items-center gap-4">
                    <span class="flex items-center gap-1">
                      <Clock class="w-4 h-4" />
                      提交于 {{ formatTime(currentDetail.submission?.submittedAt) }}
                    </span>
                    <span v-if="currentDetail.submission?.objectiveScore !== null" class="flex items-center gap-1">
                      <Award class="w-4 h-4" />
                      客观题 {{ currentDetail.submission?.objectiveScore }}分
                    </span>
                  </div>
                </div>
              </div>
              <div class="text-right">
                <div class="text-2xl font-bold text-tianlv">
                  {{ currentDetail.submission?.totalScore ?? '-' }}
                </div>
                <div class="text-xs text-shuimo/50">总分</div>
              </div>
            </div>
          </GlassCard>

          <!-- 答案列表 -->
          <div v-for="(answer, idx) in currentDetail.answers" :key="answer.questionId" class="space-y-3">
            <GlassCard class="overflow-hidden">
              <!-- 题目 -->
              <div class="p-4 bg-slate-50/50 border-b">
                <div class="flex items-center gap-2 mb-2">
                  <span class="px-2 py-0.5 rounded text-xs font-medium"
                    :class="{
                      'bg-qinghua/10 text-qinghua': answer.questionType === 'single',
                      'bg-tianlv/10 text-tianlv': answer.questionType === 'multiple',
                      'bg-yanzhi/10 text-yanzhi': answer.questionType === 'subjective'
                    }">
                    {{ answer.questionType === 'single' ? '单选题' : answer.questionType === 'multiple' ? '多选题' : '主观题' }}
                  </span>
                  <span class="text-sm text-shuimo/60">满分 {{ answer.maxScore }} 分</span>
                </div>
                <div class="text-shuimo font-medium">{{ idx + 1 }}. {{ answer.questionContent }}</div>
                
                <!-- 选项（客观题） -->
                <div v-if="answer.options && answer.questionType !== 'subjective'" class="mt-3 space-y-1">
                  <div v-for="(opt, optIdx) in parseAnswerOptions(answer.options)" :key="optIdx"
                    :class="[
                      'px-3 py-1.5 rounded text-sm',
                      answer.correctAnswer?.includes(String.fromCharCode(65 + optIdx)) ? 'bg-qingsong/10 text-qingsong' : 'text-shuimo/70'
                    ]">
                    {{ String.fromCharCode(65 + optIdx) }}. {{ opt }}
                  </div>
                </div>
              </div>
              
              <!-- 学生答案 -->
              <div class="p-4">
                <div class="text-sm text-shuimo/60 mb-2 flex items-center gap-2">
                  <User class="w-4 h-4" />
                  学生答案
                  <span v-if="answer.isCorrect === 1" class="text-qingsong">✓ 正确</span>
                  <span v-else-if="answer.isCorrect === 0" class="text-yanzhi">✗ 错误</span>
                </div>
                <div class="p-3 bg-slate-50 rounded-lg whitespace-pre-wrap text-shuimo">
                  {{ answer.studentAnswer || '（未作答）' }}
                </div>
                
                <!-- 正确答案（客观题） -->
                <div v-if="answer.questionType !== 'subjective' && answer.correctAnswer" class="mt-3">
                  <div class="text-sm text-shuimo/60 mb-1">正确答案</div>
                  <div class="text-qingsong font-medium">{{ answer.correctAnswer }}</div>
                </div>
                
                <!-- AI反馈 -->
                <div v-if="answer.aiFeedback" class="mt-3 p-3 bg-qinghua/5 rounded-lg">
                  <div class="text-sm text-qinghua/80">{{ answer.aiFeedback }}</div>
                </div>
              </div>
            </GlassCard>

            <!-- 评分面板（主观题） -->
            <GlassCard v-if="answer.questionType === 'subjective'" class="p-4 border-l-4 border-tianlv">
              <div class="flex items-center gap-4 mb-3">
                <label class="text-sm font-medium text-shuimo">评分</label>
                <div class="flex items-center gap-2">
                  <input 
                    type="number" 
                    v-model.number="grades[answer.questionId].score"
                    :max="answer.maxScore"
                    min="0"
                    class="w-20 px-3 py-1.5 border rounded-lg text-center font-mono"
                    placeholder="分数"
                  />
                  <span class="text-shuimo/60">/ {{ answer.maxScore }}</span>
                </div>
              </div>
              <div>
                <label class="text-sm font-medium text-shuimo mb-1 block">评语</label>
                <textarea 
                  v-model="grades[answer.questionId].feedback"
                  rows="2"
                  class="w-full px-3 py-2 border rounded-lg resize-none"
                  placeholder="输入评语（可选）"
                ></textarea>
              </div>
            </GlassCard>
          </div>

          <!-- 整体评语 -->
          <GlassCard class="p-4">
            <div class="flex items-center gap-2 mb-3">
              <MessageSquare class="w-5 h-5 text-tianlv" />
              <label class="font-medium text-shuimo">整体评语</label>
            </div>
            <textarea 
              v-model="overallFeedback"
              rows="3"
              class="w-full px-3 py-2 border rounded-lg resize-none"
              placeholder="输入整体评语（可选）"
            ></textarea>
          </GlassCard>
        </div>
      </main>

      <!-- 右侧：操作面板 -->
      <aside class="w-72 flex-shrink-0 border-l bg-white p-4 flex flex-col">
        <div class="flex-1">
          <h3 class="font-bold text-shuimo mb-4">操作</h3>
          
          <!-- 导航按钮 -->
          <div class="flex gap-2 mb-6">
            <BaseButton 
              @click="prevSubmission" 
              :disabled="!canGoPrev"
              variant="secondary"
              class="flex-1"
            >
              <ChevronLeft class="w-4 h-4 mr-1" />
              上一个
            </BaseButton>
            <BaseButton 
              @click="nextSubmission" 
              :disabled="!canGoNext"
              variant="secondary"
              class="flex-1"
            >
              下一个
              <ChevronRight class="w-4 h-4 ml-1" />
            </BaseButton>
          </div>
          
          <!-- 当前位置 -->
          <div class="text-center text-sm text-shuimo/60 mb-6">
            {{ currentIndex + 1 }} / {{ totalCount }}
          </div>
        </div>
        
        <!-- 保存按钮 -->
        <div class="space-y-3">
          <BaseButton 
            @click="saveGrade(false)" 
            :loading="saving"
            variant="secondary"
            block
          >
            <Save class="w-4 h-4 mr-2" />
            保存
          </BaseButton>
          <BaseButton 
            @click="saveGrade(true)" 
            :loading="saving"
            variant="primary"
            block
          >
            <Check class="w-4 h-4 mr-2" />
            保存并下一个
          </BaseButton>
        </div>
      </aside>
    </div>

    <!-- 加载状态 -->
    <div v-else class="flex-1 flex items-center justify-center">
      <div class="text-center">
        <div class="w-12 h-12 border-4 border-tianlv/30 border-t-tianlv rounded-full animate-spin mx-auto mb-4"></div>
        <p class="text-shuimo/60">加载中...</p>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-if="!loading && submissions.length === 0" class="flex-1 flex items-center justify-center">
      <div class="text-center">
        <FileText class="w-16 h-16 text-shuimo/20 mx-auto mb-4" />
        <p class="text-shuimo/60">暂无提交记录</p>
        <BaseButton @click="emit('close')" variant="secondary" class="mt-4">
          返回
        </BaseButton>
      </div>
    </div>
    </div>
  </Teleport>
</template>
