<script setup>
/**
 * 错题报告面板组件
 * 展示作业中的高错误率题目和讲解建议
 */
import { ref, onMounted, watch } from 'vue'
import { AlertTriangle, ChevronDown, ChevronUp, Lightbulb, BookOpen, Users, Target } from 'lucide-vue-next'
import { homeworkAPI } from '../../services/api'
import AnimatedNumber from '../ui/AnimatedNumber.vue'

const props = defineProps({
  /** 作业ID */
  homeworkId: {
    type: [Number, String],
    required: true
  },
  /** 学生ID（可选，查看特定学生时使用） */
  studentId: {
    type: [Number, String],
    default: null
  }
})

// 状态管理
const loading = ref(false)
const reportData = ref({
  totalQuestions: 0,
  avgScore: 0,
  highErrorQuestions: [],
  commonMistakes: [],
  suggestions: []
})
const expandedQuestions = ref({})

/**
 * 加载错题报告数据
 */
const loadReportData = async () => {
  if (!props.homeworkId) return

  loading.value = true
  try {
    const res = await homeworkAPI.getReport(props.homeworkId, props.studentId)
    if (res.code === 200 && res.data) {
      reportData.value = {
        totalQuestions: res.data.totalQuestions || 0,
        avgScore: res.data.avgScore || 0,
        highErrorQuestions: res.data.highErrorQuestions || [],
        commonMistakes: res.data.commonMistakes || [],
        suggestions: res.data.suggestions || []
      }
    }
  } catch (e) {
    console.error('加载错题报告失败:', e)
    reportData.value = {
      totalQuestions: 0,
      avgScore: 0,
      highErrorQuestions: [],
      commonMistakes: [],
      suggestions: []
    }
  } finally {
    loading.value = false
  }
}

/**
 * 切换题目展开状态
 */
const toggleQuestion = (questionId) => {
  expandedQuestions.value[questionId] = !expandedQuestions.value[questionId]
}

/**
 * 获取错误率对应的颜色
 */
const getErrorRateColor = (rate) => {
  if (rate >= 70) return 'text-yanzhi'
  if (rate >= 50) return 'text-zhizi'
  if (rate >= 30) return 'text-qiuxiang'
  return 'text-shuimo/60'
}

/**
 * 获取错误率对应的背景色
 */
const getErrorRateBgColor = (rate) => {
  if (rate >= 70) return 'bg-yanzhi/10'
  if (rate >= 50) return 'bg-zhizi/10'
  if (rate >= 30) return 'bg-qiuxiang/10'
  return 'bg-slate-100'
}

/**
 * 获取严重程度标签
 */
const getSeverityLabel = (rate) => {
  if (rate >= 70) return '高度关注'
  if (rate >= 50) return '需要注意'
  if (rate >= 30) return '稍有困难'
  return '基本掌握'
}

// 安全解析题目选项，避免模板中重复 JSON.parse
const parseQuestionOptions = (options) => {
  if (Array.isArray(options)) {
    return options
  }
  if (typeof options === 'string') {
    try {
      const parsed = JSON.parse(options)
      return Array.isArray(parsed) ? parsed : []
    } catch (error) {
      console.warn('错题选项解析失败:', error)
      return []
    }
  }
  return []
}

// 监听homeworkId变化
watch(() => props.homeworkId, (newId) => {
  if (newId) {
    loadReportData()
  }
}, { immediate: true })

onMounted(() => {
  if (props.homeworkId) {
    loadReportData()
  }
})
</script>

<template>
  <div class="space-y-5">
    <!-- 加载状态 -->
    <div v-if="loading" class="py-12 text-center">
      <div class="w-8 h-8 border-2 border-yanzhi/20 border-t-yanzhi rounded-full animate-spin mx-auto mb-3"></div>
      <p class="text-sm text-shuimo/50">分析中...</p>
    </div>

    <template v-else>
      <!-- 概览统计 -->
      <div class="grid grid-cols-3 gap-4">
        <div class="text-center p-4 rounded-xl bg-slate-50">
          <BookOpen class="w-5 h-5 mx-auto text-qinghua mb-1" />
          <div class="text-xl font-bold text-shuimo font-mono">
            <AnimatedNumber :value="reportData.totalQuestions" />
          </div>
          <div class="text-xs text-shuimo/50">题目总数</div>
        </div>
        <div class="text-center p-4 rounded-xl bg-tianlv/10">
          <Target class="w-5 h-5 mx-auto text-tianlv mb-1" />
          <div class="text-xl font-bold text-tianlv font-mono">
            <AnimatedNumber :value="Math.round(reportData.avgScore)" />
          </div>
          <div class="text-xs text-shuimo/50">平均得分</div>
        </div>
        <div class="text-center p-4 rounded-xl bg-yanzhi/10">
          <AlertTriangle class="w-5 h-5 mx-auto text-yanzhi mb-1" />
          <div class="text-xl font-bold text-yanzhi font-mono">
            <AnimatedNumber :value="reportData.highErrorQuestions.length" />
          </div>
          <div class="text-xs text-shuimo/50">高错题目</div>
        </div>
      </div>

      <!-- 高错误率题目列表 -->
      <div class="space-y-3">
        <h4 class="font-bold text-shuimo flex items-center gap-2">
          <AlertTriangle class="w-4 h-4 text-yanzhi" />
          高错误率题目
        </h4>

        <!-- 空状态 -->
        <div v-if="reportData.highErrorQuestions.length === 0" class="text-center py-8">
          <div class="w-14 h-14 mx-auto mb-3 rounded-full bg-qingsong/10 flex items-center justify-center">
            <Target class="w-7 h-7 text-qingsong" />
          </div>
          <p class="text-sm text-qingsong font-medium">表现优秀!</p>
          <p class="text-xs text-shuimo/40 mt-1">没有高错误率的题目</p>
        </div>

        <!-- 题目列表 -->
        <div v-else class="space-y-3 max-h-80 overflow-y-auto">
          <div
            v-for="question in reportData.highErrorQuestions"
            :key="question.questionId"
            class="rounded-xl border transition-all"
            :class="getErrorRateBgColor(question.errorRate)"
          >
            <!-- 题目头部 -->
            <div
              class="p-4 flex items-center justify-between cursor-pointer"
              @click="toggleQuestion(question.questionId)"
            >
              <div class="flex items-center gap-3">
                <span
                  class="px-2 py-1 rounded-lg text-xs font-bold"
                  :class="getErrorRateBgColor(question.errorRate) + ' ' + getErrorRateColor(question.errorRate)"
                >
                  错误率 {{ question.errorRate }}%
                </span>
                <span class="text-sm font-medium text-shuimo truncate max-w-[200px]">
                  {{ question.content || `第${question.sortOrder}题` }}
                </span>
              </div>
              <div class="flex items-center gap-2">
                <span class="text-xs text-shuimo/40">
                  {{ getSeverityLabel(question.errorRate) }}
                </span>
                <component
                  :is="expandedQuestions[question.questionId] ? ChevronUp : ChevronDown"
                  class="w-4 h-4 text-shuimo/40"
                />
              </div>
            </div>

            <!-- 展开内容 -->
            <Transition name="expand">
              <div v-if="expandedQuestions[question.questionId]" class="px-4 pb-4 space-y-3 border-t border-slate-200/50">
                <!-- 题目内容 -->
                <div class="pt-3">
                  <p class="text-sm text-shuimo whitespace-pre-wrap">{{ question.content }}</p>
                </div>

                <!-- 选项（如果是选择题） -->
                <div v-if="question.options" class="space-y-1">
                  <div
                    v-for="(option, index) in parseQuestionOptions(question.options)"
                    :key="index"
                    class="flex items-center gap-2 p-2 rounded-lg text-sm"
                    :class="String.fromCharCode(65 + index) === question.correctAnswer ? 'bg-qingsong/10 text-qingsong font-medium' : 'bg-white/50 text-shuimo/70'"
                  >
                    <span class="font-bold">{{ String.fromCharCode(65 + index) }}.</span>
                    <span>{{ option }}</span>
                    <span v-if="String.fromCharCode(65 + index) === question.correctAnswer" class="ml-auto text-xs">✓ 正确答案</span>
                  </div>
                </div>

                <!-- 常见错误答案 -->
                <div v-if="question.commonWrongAnswers && question.commonWrongAnswers.length > 0" class="p-3 rounded-lg bg-yanzhi/5">
                  <p class="text-xs font-medium text-yanzhi mb-2 flex items-center gap-1">
                    <Users class="w-3 h-3" />
                    常见错误答案
                  </p>
                  <div class="flex flex-wrap gap-2">
                    <span
                      v-for="(wrongAnswer, index) in question.commonWrongAnswers"
                      :key="index"
                      class="px-2 py-1 rounded bg-yanzhi/10 text-yanzhi text-xs"
                    >
                      {{ wrongAnswer.answer }} ({{ wrongAnswer.count }}人)
                    </span>
                  </div>
                </div>

                <!-- 答案解析 -->
                <div v-if="question.answerAnalysis" class="p-3 rounded-lg bg-tianlv/5">
                  <p class="text-xs font-medium text-tianlv mb-2 flex items-center gap-1">
                    <Lightbulb class="w-3 h-3" />
                    答案解析
                  </p>
                  <p class="text-sm text-shuimo/70 whitespace-pre-wrap">{{ question.answerAnalysis }}</p>
                </div>

                <!-- 教学建议 -->
                <div v-if="question.teachingSuggestion" class="p-3 rounded-lg bg-qinghua/5">
                  <p class="text-xs font-medium text-qinghua mb-2 flex items-center gap-1">
                    <BookOpen class="w-3 h-3" />
                    教学建议
                  </p>
                  <p class="text-sm text-shuimo/70">{{ question.teachingSuggestion }}</p>
                </div>
              </div>
            </Transition>
          </div>
        </div>
      </div>

      <!-- 整体教学建议 -->
      <div v-if="reportData.suggestions && reportData.suggestions.length > 0" class="space-y-3">
        <h4 class="font-bold text-shuimo flex items-center gap-2">
          <Lightbulb class="w-4 h-4 text-zhizi" />
          整体教学建议
        </h4>
        <div class="space-y-2">
          <div
            v-for="(suggestion, index) in reportData.suggestions"
            :key="index"
            class="p-3 rounded-xl bg-zhizi/5 border border-zhizi/10"
          >
            <p class="text-sm text-shuimo/80">{{ suggestion }}</p>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.expand-enter-active,
.expand-leave-active {
  /* P1：展开过渡仅保留透明度与高度 */
  transition:
    opacity var(--motion-duration-medium) var(--motion-ease-standard),
    max-height var(--motion-duration-medium) var(--motion-ease-standard);
  overflow: hidden;
}

.expand-enter-from,
.expand-leave-to {
  opacity: 0;
  max-height: 0;
}

.expand-enter-to,
.expand-leave-from {
  opacity: 1;
  max-height: 500px;
}
</style>
