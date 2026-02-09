<script setup>
import { ref, onMounted } from 'vue'
import { MessageCircle, Send, CheckCircle, Clock } from 'lucide-vue-next'
import { homeworkAPI } from '../../services/api'
import { useToastStore } from '../../stores/toast'
import GlassCard from '../ui/GlassCard.vue'
import BaseButton from '../ui/BaseButton.vue'
import { formatDateTimeCN } from '../../utils/datetime'

const formatDisplayDateTime = (dateStr) => {
  return formatDateTimeCN(dateStr, '未知时间')
}

const props = defineProps({
  homeworkId: {
    type: Number,
    required: true
  },
  studentId: {
    type: Number,
    required: true
  }
})

const toast = useToastStore()
const loading = ref(false)
const questions = ref([])
const newQuestion = ref('')
const selectedQuestionId = ref(null)

const loadQuestions = async () => {
  loading.value = true
  try {
    const res = await homeworkAPI.getHomeworkQuestions(props.homeworkId)
    if (res.code === 200) {
      // 只显示当前学生的提问
      questions.value = res.data.filter(q => q.studentId === props.studentId)
    }
  } catch (e) {
    console.error('加载问答失败', e)
  } finally {
    loading.value = false
  }
}

const submitQuestion = async () => {
  if (!newQuestion.value.trim()) {
    toast.warning('请输入问题内容')
    return
  }
  
  try {
    await homeworkAPI.askQuestion(
      props.homeworkId,
      selectedQuestionId.value,
      newQuestion.value,
      props.studentId
    )
    toast.success('提问成功，等待教师回复')
    newQuestion.value = ''
    selectedQuestionId.value = null
    await loadQuestions()
  } catch (e) {
    toast.error('提问失败: ' + e.message)
  }
}

onMounted(() => {
  loadQuestions()
})
</script>

<template>
  <div class="space-y-4">
    <GlassCard class="p-4">
      <h3 class="text-lg font-bold text-shuimo mb-4 flex items-center gap-2">
        <MessageCircle class="w-5 h-5 text-qinghua" />
        作业问答
      </h3>
      
      <!-- 提问框 -->
      <div class="bg-slate-50 rounded-xl p-4 mb-4">
        <textarea
          v-model="newQuestion"
          placeholder="在这里提出您的问题..."
          class="w-full p-3 border border-slate-200 rounded-lg resize-none focus:ring-2 focus:ring-qinghua/20 focus:border-qinghua outline-none"
          rows="3"
        ></textarea>
        <div class="flex justify-end mt-2">
          <BaseButton @click="submitQuestion" icon="Send" size="sm" variant="primary">
            提交问题
          </BaseButton>
        </div>
      </div>
      
      <!-- 问答列表 -->
      <div class="space-y-3">
        <div v-if="questions.length === 0" class="text-center py-8 text-shuimo/40">
          <MessageCircle class="w-12 h-12 mx-auto mb-2 opacity-30" />
          <p>还没有提问，有疑问就问吧！</p>
        </div>
        
        <div
          v-for="q in questions"
          :key="q.id"
          class="bg-white border border-slate-200 rounded-xl p-4"
        >
          <!-- 学生提问 -->
          <div class="flex items-start gap-3 mb-3">
            <div class="w-8 h-8 rounded-full bg-qinghua/10 flex items-center justify-center shrink-0">
              <MessageCircle class="w-4 h-4 text-qinghua" />
            </div>
            <div class="flex-1">
              <div class="flex items-center gap-2 mb-1">
                <span class="font-bold text-sm text-shuimo">我的提问</span>
                <span class="text-xs text-shuimo/40">
                  {{ formatDisplayDateTime(q.createdAt) }}
                </span>
              </div>
              <p class="text-sm text-shuimo/80">{{ q.questionContent }}</p>
            </div>
          </div>
          
          <!-- 教师回复 -->
          <div v-if="q.status === 'answered'" class="ml-11 bg-tianlv/5 rounded-lg p-3 border-l-4 border-tianlv">
            <div class="flex items-center gap-2 mb-1">
              <CheckCircle class="w-4 h-4 text-tianlv" />
              <span class="font-bold text-sm text-tianlv">教师回复</span>
              <span class="text-xs text-shuimo/40">
                {{ formatDisplayDateTime(q.repliedAt) }}
              </span>
            </div>
            <p class="text-sm text-shuimo/80">{{ q.teacherReply }}</p>
          </div>
          
          <!-- 等待回复 -->
          <div v-else class="ml-11 bg-amber-50 rounded-lg p-3 border-l-4 border-amber-300">
            <div class="flex items-center gap-2 text-amber-600">
              <Clock class="w-4 h-4" />
              <span class="text-sm font-medium">等待教师回复...</span>
            </div>
          </div>
        </div>
      </div>
    </GlassCard>
  </div>
</template>
