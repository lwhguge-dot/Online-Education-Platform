<script setup lang="ts">
import { reactive } from 'vue'
import { X, CheckCircle, AlertCircle } from 'lucide-vue-next'
import GlassCard from '../ui/GlassCard.vue'
import { logger } from '../../utils/logger'

interface Question {
  id: number
  content: string
  questionType: string
  options: string | string[]
  score: number
}

interface AnswerResult {
  questionId: number
  isCorrect: number
  score: number | null
  correctAnswer?: string
  studentAnswer?: string
  aiFeedback?: string
  teacherFeedback?: string
}

const props = defineProps<{
  question: Question
  index: number
  isViewMode: boolean
  modelValue: string
  answerResult?: AnswerResult | undefined
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const animatingOptions = reactive<Record<string, boolean>>({})

const triggerOptionAnimation = (optionKey: string) => {
  animatingOptions[optionKey] = true
  setTimeout(() => { animatingOptions[optionKey] = false }, 300)
}

const isOptionAnimating = (optionKey: string) => animatingOptions[optionKey]

const parseQuestionOptions = (options: string | string[]): string[] => {
  if (Array.isArray(options)) return options
  if (typeof options === 'string') {
    try {
      const parsed = JSON.parse(options)
      return Array.isArray(parsed) ? parsed : []
    } catch (error) {
      logger.warn('题目选项解析失败:', error)
      return []
    }
  }
  return []
}

const selectSingle = (option: string) => {
  if (props.isViewMode) return
  triggerOptionAnimation(`${props.question.id}-${option}`)
  emit('update:modelValue', option)
}

const toggleMultiple = (option: string) => {
  if (props.isViewMode) return
  triggerOptionAnimation(`${props.question.id}-${option}`)
  const current = props.modelValue || ''
  if (current.includes(option)) {
    emit('update:modelValue', current.replace(option, '').split('').sort().join(''))
  } else {
    emit('update:modelValue', (current + option).split('').sort().join(''))
  }
}
</script>

<template>
  <GlassCard
    :hoverable="!isViewMode"
    class="transition-[transform,box-shadow,border-color,background-color] duration-500 question-card"
    :class="{ 'ring-2 ring-qinghua/20': !isViewMode }"
    :style="{ '--delay': index * 0.08 + 's' }"
  >
    <div class="flex items-start gap-4">
      <span class="flex-shrink-0 w-8 h-8 flex items-center justify-center bg-qinghua/10 text-qinghua rounded-lg font-bold font-song text-lg mt-0.5 question-number">
        {{ index + 1 }}
      </span>

      <div class="flex-1 min-w-0">
        <div class="mb-4">
          <div class="flex items-center gap-3 mb-2">
            <span class="text-xs px-2 py-0.5 rounded bg-slate-100 text-shuimo/60 font-medium">
              {{ question.questionType === 'single' ? '单选题' : question.questionType === 'multiple' ? '多选题' : question.questionType === 'fill' ? '填空题' : '主观题' }}
            </span>
            <span class="text-xs text-shuimo/40">{{ question.score }} 分</span>
          </div>
          <h3 class="text-lg font-medium text-shuimo leading-relaxed">{{ question.content }}</h3>
        </div>

        <!-- 单选题 -->
        <div v-if="question.questionType === 'single'" class="space-y-3">
          <div v-for="(option, idx) in parseQuestionOptions(question.options)" :key="idx"
               @click="selectSingle(String.fromCharCode(65 + idx))"
               :class="['group relative p-4 rounded-xl border transition-[transform,box-shadow,border-color,background-color] duration-300 option-item',
                         !isViewMode && 'cursor-pointer hover:border-qinghua/50 hover:bg-white/80',
                         modelValue === String.fromCharCode(65 + idx)
                           ? 'border-qinghua bg-qinghua/5 shadow-sm ring-1 ring-qinghua/20'
                           : 'border-slate-200/60 bg-white/40',
                         isOptionAnimating(String.fromCharCode(65 + idx)) && 'animate-option-select']">
            <div class="flex items-center gap-3">
              <div :class="['w-5 h-5 rounded-full border flex items-center justify-center transition-[transform,border-color,background-color] duration-200',
                            modelValue === String.fromCharCode(65 + idx)
                              ? 'border-qinghua/50 bg-qinghua text-white scale-110'
                              : 'border-slate-300 group-hover:border-qinghua/50']">
                <div v-if="modelValue === String.fromCharCode(65 + idx)" class="w-2 h-2 bg-white rounded-full animate-scale-in" />
              </div>
              <span class="font-medium text-shuimo/50 w-4">{{ String.fromCharCode(65 + idx) }}.</span>
              <span :class="['flex-1 text-shuimo transition-colors', modelValue === String.fromCharCode(65 + idx) ? 'font-medium' : '']">
                {{ option }}
              </span>
            </div>
          </div>
        </div>

        <!-- 多选题 -->
        <div v-else-if="question.questionType === 'multiple'" class="space-y-3">
          <div v-for="(option, idx) in parseQuestionOptions(question.options)" :key="idx"
               @click="toggleMultiple(String.fromCharCode(65 + idx))"
               :class="['group relative p-4 rounded-xl border transition-[transform,box-shadow,border-color,background-color] duration-300 option-item',
                         !isViewMode && 'cursor-pointer hover:border-qinghua/50 hover:bg-white/80',
                         (modelValue || '').includes(String.fromCharCode(65 + idx))
                           ? 'border-qinghua/50 bg-qinghua/5 shadow-sm ring-1 ring-qinghua/20'
                           : 'border-slate-200/60 bg-white/40',
                         isOptionAnimating(String.fromCharCode(65 + idx)) && 'animate-option-select']">
            <div class="flex items-center gap-3">
              <div :class="['w-5 h-5 rounded border flex items-center justify-center transition-[transform,border-color,background-color] duration-200',
                            (modelValue || '').includes(String.fromCharCode(65 + idx))
                              ? 'border-qinghua bg-qinghua text-white scale-110'
                              : 'border-slate-300 group-hover:border-qinghua/50']">
                <CheckCircle v-if="(modelValue || '').includes(String.fromCharCode(65 + idx))" class="w-3.5 h-3.5 animate-scale-in" />
              </div>
              <span class="font-medium text-shuimo/50 w-4">{{ String.fromCharCode(65 + idx) }}.</span>
              <span :class="['flex-1 text-shuimo transition-colors', (modelValue || '').includes(String.fromCharCode(65 + idx)) ? 'font-medium' : '']">
                {{ option }}
              </span>
            </div>
          </div>
        </div>

        <!-- 填空题 -->
        <div v-else-if="question.questionType === 'fill'" class="relative">
          <input :value="modelValue" @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
                 type="text" :disabled="isViewMode"
                 class="w-full px-4 py-3 rounded-xl border border-slate-200/60 bg-white/50 focus:bg-white focus:border-qinghua focus:ring-4 focus:ring-qinghua/10 outline-none transition-[background-color,border-color,box-shadow,color] duration-300 disabled:bg-slate-50 disabled:text-shuimo/60 placeholder:text-shuimo/30"
                 placeholder="在此输入您的答案..." />
        </div>

        <!-- 主观题 -->
        <div v-else-if="question.questionType === 'subjective'">
          <textarea :value="modelValue" @input="emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)"
                    rows="5" :disabled="isViewMode"
                    class="w-full px-4 py-3 rounded-xl border border-slate-200/60 bg-white/50 focus:bg-white focus:border-qinghua focus:ring-4 focus:ring-qinghua/10 outline-none resize-none transition-[background-color,border-color,box-shadow,color] duration-300 disabled:bg-slate-50 disabled:text-shuimo/60 placeholder:text-shuimo/30"
                    placeholder="在此输入您的详细解答..." />
        </div>

        <!-- 批改结果 -->
        <Transition name="result-expand">
          <div v-if="isViewMode && answerResult" class="mt-6 pt-4 border-t border-slate-100/50 space-y-3">
            <div class="flex items-center justify-between">
              <span :class="['flex items-center gap-2 font-medium px-3 py-1 rounded-lg text-sm result-badge',
                answerResult.isCorrect === 1 ? 'bg-qingsong/10 text-qingsong' :
                answerResult.isCorrect === 0 ? 'bg-yanzhi/10 text-yanzhi' : 'bg-zhizi/10 text-zhizi']">
                <component :is="answerResult.isCorrect === 1 ? CheckCircle : answerResult.isCorrect === 0 ? X : AlertCircle"
                           :class="['w-4 h-4', answerResult.isCorrect === 1 ? 'animate-check-mark' : '']" />
                {{ answerResult.isCorrect === 1 ? '回答正确' : answerResult.isCorrect === 0 ? '回答错误' : (answerResult.score !== null ? '已批改' : '等待批改') }}
              </span>
              <span class="font-bold text-lg font-mono text-qinghua">
                {{ answerResult.score !== null ? answerResult.score : '--' }} <span class="text-xs text-shuimo/40 font-normal">/ {{ question.score }}</span>
              </span>
            </div>

            <div v-if="answerResult.isCorrect === 0" class="p-3 bg-slate-50/80 rounded-xl border border-slate-100 animate-slide-down">
              <p class="text-xs text-shuimo/50 mb-1">正确答案</p>
              <p class="text-sm font-medium text-shuimo">{{ answerResult.correctAnswer }}</p>
            </div>

            <div v-if="answerResult.aiFeedback || answerResult.teacherFeedback" class="space-y-2">
              <div v-if="answerResult.aiFeedback" class="flex gap-3 p-3 bg-qinghua/5 rounded-xl border border-qinghua/10 animate-slide-down" style="animation-delay: 0.1s">
                <div class="w-6 h-6 rounded-full bg-qinghua text-white flex items-center justify-center text-xs flex-shrink-0">AI</div>
                <p class="text-sm text-shuimo/80 leading-relaxed">{{ answerResult.aiFeedback }}</p>
              </div>
              <div v-if="answerResult.teacherFeedback" class="flex gap-3 p-3 bg-zhizi/5 rounded-xl border border-zhizi/10 animate-slide-down" style="animation-delay: 0.2s">
                <div class="w-6 h-6 rounded-full bg-zhizi text-white flex items-center justify-center text-xs flex-shrink-0">师</div>
                <p class="text-sm text-shuimo/80 leading-relaxed">{{ answerResult.teacherFeedback }}</p>
              </div>
            </div>
          </div>
        </Transition>
      </div>
    </div>
  </GlassCard>
</template>

<style scoped>
.question-list-enter-active {
  animation: question-enter var(--motion-duration-medium) var(--motion-ease-standard) both;
  animation-delay: var(--delay, 0s);
}

.question-list-leave-active {
  animation: question-leave var(--motion-duration-medium) var(--motion-ease-standard) both;
}

@keyframes question-enter {
  from { opacity: 0; transform: translateY(30px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes question-leave {
  from { opacity: 1; transform: translateY(0); }
  to { opacity: 0; transform: translateY(-20px); }
}

.question-number {
  animation: number-pop var(--motion-duration-medium) var(--motion-ease-standard) both;
  animation-delay: calc(var(--delay, 0s) + 0.2s);
}

@keyframes number-pop {
  0% { transform: scale(0); opacity: 0; }
  70% { transform: scale(1.2); }
  100% { transform: scale(1); opacity: 1; }
}

.animate-option-select {
  animation: option-bounce var(--motion-duration-medium) var(--motion-ease-standard);
}

@keyframes option-bounce {
  0% { transform: scale(1); }
  50% { transform: scale(0.97); }
  100% { transform: scale(1); }
}

.animate-scale-in {
  animation: scale-in var(--motion-duration-medium) var(--motion-ease-standard);
}

@keyframes scale-in {
  from { transform: scale(0); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}

.result-expand-enter-active {
  animation: result-expand var(--motion-duration-medium) var(--motion-ease-standard);
}

.result-expand-leave-active {
  animation: result-collapse var(--motion-duration-medium) var(--motion-ease-standard);
}

@keyframes result-expand {
  from { opacity: 0; transform: translateY(-8px) scaleY(0.96); transform-origin: top; }
  to { opacity: 1; transform: translateY(0) scaleY(1); transform-origin: top; }
}

@keyframes result-collapse {
  from { opacity: 1; transform: translateY(0) scaleY(1); transform-origin: top; }
  to { opacity: 0; transform: translateY(-8px) scaleY(0.96); transform-origin: top; }
}

.animate-check-mark {
  animation: check-mark var(--motion-duration-medium) var(--motion-ease-standard);
}

@keyframes check-mark {
  0% { transform: scale(0) rotate(-45deg); opacity: 0; }
  50% { transform: scale(1.3) rotate(0deg); }
  100% { transform: scale(1) rotate(0deg); opacity: 1; }
}

.animate-slide-down {
  animation: slide-down var(--motion-duration-medium) var(--motion-ease-standard) both;
}

@keyframes slide-down {
  from { opacity: 0; transform: translateY(-10px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
