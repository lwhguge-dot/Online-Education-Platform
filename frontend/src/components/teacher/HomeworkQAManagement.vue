<script setup>
import { ref, computed, onMounted } from 'vue'
import { MessageCircle, Send, CheckCircle, Clock, User } from 'lucide-vue-next'
import { homeworkAPI } from '../../services/api'
import { useToastStore } from '../../stores/toast'
import GlassCard from '../ui/GlassCard.vue'
import BaseButton from '../ui/BaseButton.vue'

const props = defineProps({
  homeworkId: {
    type: Number,
    required: true
  },
  teacherId: {
    type: Number,
    required: true
  }
})

const toast = useToastStore()
const loading = ref(false)
const questions = ref([])
const replyContent = ref({})
const activeFilter = ref('all') // all, pending, answered

const loadQuestions = async () => {
  loading.value = true
  try {
    const res = await homeworkAPI.getHomeworkQuestions(props.homeworkId)
    if (res.code === 200) {
      questions.value = res.data
    }
  } catch (e) {
    console.error('加载问答失败', e)
  } finally {
    loading.value = false
  }
}

const submitReply = async (discussionId) => {
  const reply = replyContent.value[discussionId]
  if (!reply || !reply.trim()) {
    toast.warning('请输入回复内容')
    return
  }
  
  try {
    await homeworkAPI.replyQuestion(discussionId, reply, props.teacherId)
    toast.success('回复成功')
    replyContent.value[discussionId] = ''
    await loadQuestions()
  } catch (e) {
    toast.error('回复失败: ' + e.message)
  }
}

const filteredQuestions = computed(() => {
  if (activeFilter.value === 'all') return questions.value
  return questions.value.filter(q => q.status === activeFilter.value)
})

const pendingCount = computed(() => {
  return questions.value.filter(q => q.status === 'pending').length
})

onMounted(() => {
  loadQuestions()
})
</script>

<template>
  <div class="space-y-4">
    <GlassCard class="p-4">
      <div class="flex items-center justify-between mb-4">
        <h3 class="text-lg font-bold text-shuimo flex items-center gap-2">
          <MessageCircle class="w-5 h-5 text-qinghua" />
          学生提问
          <span v-if="pendingCount > 0" class="text-sm px-2 py-0.5 rounded-full bg-yanzhi text-white">
            {{ pendingCount }}
          </span>
        </h3>
        
        <!-- 筛选按钮 -->
        <div class="flex gap-2">
          <button
            v-for="filter in [{id: 'all', label: '全部'}, {id: 'pending', label: '待回复'}, {id: 'answered', label: '已回复'}]"
            :key="filter.id"
            @click="activeFilter = filter.id"
            :class="['px-3 py-1.5 rounded-lg text-sm font-medium transition-all',
              activeFilter === filter.id ? 'bg-qinghua text-white' : 'bg-slate-100 text-shuimo/60 hover:bg-slate-200']"
          >
            {{ filter.label }}
          </button>
        </div>
      </div>
      
      <!-- 问答列表 -->
      <div class="space-y-3">
        <div v-if="filteredQuestions.length === 0" class="text-center py-8 text-shuimo/40">
          <MessageCircle class="w-12 h-12 mx-auto mb-2 opacity-30" />
          <p>{{ activeFilter === 'pending' ? '暂无待回复的问题' : '暂无提问' }}</p>
        </div>
        
        <div
          v-for="q in filteredQuestions"
          :key="q.id"
          class="bg-white border rounded-xl p-4"
          :class="q.status === 'pending' ? 'border-amber-200 bg-amber-50/30' : 'border-slate-200'"
        >
          <!-- 学生提问 -->
          <div class="flex items-start gap-3 mb-3">
            <div class="w-8 h-8 rounded-full bg-qinghua/10 flex items-center justify-center shrink-0">
              <User class="w-4 h-4 text-qinghua" />
            </div>
            <div class="flex-1">
              <div class="flex items-center gap-2 mb-1">
                <span class="font-bold text-sm text-shuimo">{{ q.studentName }}</span>
                <span class="text-xs px-2 py-0.5 rounded-full bg-slate-100 text-slate-500">
                  学生ID: {{ q.studentId }}
                </span>
                <span class="text-xs text-shuimo/40">
                  {{ new Date(q.createdAt).toLocaleString() }}
                </span>
                <span
                  v-if="q.status === 'pending'"
                  class="text-xs px-2 py-0.5 rounded-full bg-amber-100 text-amber-600 font-medium"
                >
                  待回复
                </span>
              </div>
              <p class="text-sm text-shuimo/80">{{ q.questionContent }}</p>
              <p v-if="q.questionTitle" class="text-xs text-shuimo/50 mt-1">
                关于题目：{{ q.questionTitle }}
              </p>
            </div>
          </div>
          
          <!-- 已回复 -->
          <div v-if="q.status === 'answered'" class="ml-11 bg-tianlv/5 rounded-lg p-3 border-l-4 border-tianlv">
            <div class="flex items-center gap-2 mb-1">
              <CheckCircle class="w-4 h-4 text-tianlv" />
              <span class="font-bold text-sm text-tianlv">您的回复</span>
              <span class="text-xs text-shuimo/40">
                {{ new Date(q.repliedAt).toLocaleString() }}
              </span>
            </div>
            <p class="text-sm text-shuimo/80">{{ q.teacherReply }}</p>
          </div>
          
          <!-- 回复框 -->
          <div v-else class="ml-11 bg-white rounded-lg p-3 border border-slate-200">
            <textarea
              v-model="replyContent[q.id]"
              placeholder="输入您的回复..."
              class="w-full p-2 border border-slate-200 rounded-lg resize-none focus:ring-2 focus:ring-qinghua/20 focus:border-qinghua outline-none text-sm"
              rows="2"
            ></textarea>
            <div class="flex justify-end mt-2">
              <BaseButton @click="submitReply(q.id)" icon="Send" size="sm" variant="primary">
                回复
              </BaseButton>
            </div>
          </div>
        </div>
      </div>
    </GlassCard>
  </div>
</template>
