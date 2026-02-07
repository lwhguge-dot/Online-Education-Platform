<script setup>
import { ref, computed, onMounted } from 'vue'
import { CheckCircle, Send, MessageSquare, Clock, AlertTriangle, Pin, Filter, ChevronDown, ChevronUp } from 'lucide-vue-next'
import GlassCard from '../../components/ui/GlassCard.vue'
import BaseButton from '../../components/ui/BaseButton.vue'
import BaseSelect from '../../components/ui/BaseSelect.vue'
import EmptyState from '../../components/ui/EmptyState.vue'
import { discussionAPI } from '../../services/api'
import { useToastStore } from '../../stores/toast'

const props = defineProps({
  user: {
    type: Object,
    required: true
  }
})

const toast = useToastStore()
const loading = ref(false)
const activeTab = ref('all')
const groups = ref([])
const stats = ref({
  totalQuestions: 0,
  pendingCount: 0,
  answeredCount: 0,
  followUpCount: 0,
  overdueCount: 0
})

// 筛选
const filterCourse = ref(null)
const filterStatus = ref('')
const expandedGroups = ref(new Set())

// 回复弹窗
const showReplyModal = ref(false)
const selectedQuestion = ref(null)
const replyContent = ref('')
const submitting = ref(false)

// 获取所有课程列表（从分组数据中提取）
const courses = computed(() => {
  const courseMap = new Map()
  groups.value.forEach(g => {
    if (g.courseId && !courseMap.has(g.courseId)) {
      courseMap.set(g.courseId, { id: g.courseId, title: g.courseTitle })
    }
  })
  return Array.from(courseMap.values())
})

// 课程选项（用于 BaseSelect）
const courseOptions = computed(() => [
  { value: null, label: '全部课程' },
  ...courses.value.map(c => ({ value: c.id, label: c.title }))
])

// 状态选项（用于 BaseSelect）
const statusOptions = computed(() => [
  { value: '', label: '全部状态' },
  { value: 'pending', label: '待回复' },
  { value: 'answered', label: '已回答' },
  { value: 'follow_up', label: '待跟进' },
  { value: 'overdue', label: '超时未回复' }
])

// 讨论状态选项（用于 BaseSelect）
const discussionStatusOptions = computed(() => [
  { value: 'pending', label: '待回复' },
  { value: 'answered', label: '已回答' },
  { value: 'follow_up', label: '待跟进' }
])

// 筛选后的分组
const filteredGroups = computed(() => {
  let result = groups.value
  
  if (filterCourse.value) {
    result = result.filter(g => g.courseId === filterCourse.value)
  }
  
  if (filterStatus.value) {
    result = result.map(g => ({
      ...g,
      discussions: g.discussions.filter(d => {
        if (filterStatus.value === 'overdue') return d.isOverdue
        return d.answerStatus === filterStatus.value
      })
    })).filter(g => g.discussions.length > 0)
  }
  
  return result
})

// 统计卡片数据
const statCards = computed(() => [
  { id: 'all', label: '全部问题', count: stats.value.totalQuestions, color: 'shuimo' },
  { id: 'pending', label: '待回复', count: stats.value.pendingCount, color: 'zhizi' },
  { id: 'answered', label: '已回答', count: stats.value.answeredCount, color: 'qingsong' },
  { id: 'follow_up', label: '待跟进', count: stats.value.followUpCount, color: 'qinghua' },
  { id: 'overdue', label: '超时未回复', count: stats.value.overdueCount, color: 'yanzhi' },
])

const loadDiscussions = async () => {
  loading.value = true
  try {
    const userId = props.user?.id
    if (!userId) return
    
    const res = await discussionAPI.getTeacherDiscussions(userId)
    if (res.code === 200 && res.data) {
      groups.value = res.data.groups || []
      stats.value = res.data.stats || stats.value
      
      // 默认展开有待处理问题的分组
      groups.value.forEach(g => {
        if (g.pendingCount > 0 || g.overdueCount > 0) {
          expandedGroups.value.add(`${g.courseId}_${g.chapterId || 0}`)
        }
      })
    }
  } catch (e) {
    console.error('加载讨论失败:', e)
  } finally {
    loading.value = false
  }
}

const toggleGroup = (group) => {
  const key = `${group.courseId}_${group.chapterId || 0}`
  if (expandedGroups.value.has(key)) {
    expandedGroups.value.delete(key)
  } else {
    expandedGroups.value.add(key)
  }
}

const isGroupExpanded = (group) => {
  return expandedGroups.value.has(`${group.courseId}_${group.chapterId || 0}`)
}

const openReply = (q) => {
  selectedQuestion.value = q
  showReplyModal.value = true
  replyContent.value = ''
}

const submitReply = async () => {
  if (!selectedQuestion.value || !replyContent.value.trim()) return
  
  submitting.value = true
  try {
    const res = await discussionAPI.reply(selectedQuestion.value.id, {
      userId: props.user.id,
      content: replyContent.value,
      courseId: selectedQuestion.value.courseId,
      chapterId: selectedQuestion.value.chapterId
    })
    
    if (res.code === 200) {
      toast.success('回复成功')
      selectedQuestion.value.answerStatus = 'answered'
      selectedQuestion.value.replyCount = (selectedQuestion.value.replyCount || 0) + 1
      showReplyModal.value = false
      replyContent.value = ''
    } else {
      toast.error(res.message || '回复失败')
    }
  } catch (e) {
    console.error('回复失败:', e)
    toast.error('回复失败')
  } finally {
    submitting.value = false
  }
}

const updateStatus = async (discussion, status) => {
  try {
    const res = await discussionAPI.updateStatus(discussion.id, status, props.user.id)
    if (res.code === 200) {
      discussion.answerStatus = status
      toast.success('状态已更新')
      // 重新加载统计
      loadDiscussions()
    } else {
      toast.error(res.message || '更新状态失败')
    }
  } catch (e) {
    console.error('更新状态失败:', e)
  }
}

const toggleTop = async (discussion) => {
  try {
    const res = await discussionAPI.toggleTop(discussion.id)
    if (res.code === 200) {
      discussion.isTop = discussion.isTop === 1 ? 0 : 1
      toast.success(discussion.isTop ? '已置顶' : '已取消置顶')
    } else {
      toast.error(res.message || '置顶操作失败')
    }
  } catch (e) {
    console.error('置顶操作失败:', e)
  }
}

const getStatusClass = (status) => {
  const classes = {
    'pending': 'bg-zhizi/10 text-zhizi border-zhizi/20',
    'answered': 'bg-qingsong/10 text-qingsong border-qingsong/20',
    'follow_up': 'bg-qinghua/10 text-qinghua border-qinghua/20',
  }
  return classes[status] || 'bg-slate-100 text-slate-500'
}

const getStatusText = (status) => {
  const texts = {
    'pending': '待回复',
    'answered': '已回答',
    'follow_up': '待跟进',
  }
  return texts[status] || status
}

const formatTime = (dateStr) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now - date
  const hours = Math.floor(diff / (1000 * 60 * 60))
  
  if (hours < 1) return '刚刚'
  if (hours < 24) return `${hours}小时前`
  if (hours < 48) return '昨天'
  return date.toLocaleDateString('zh-CN')
}

const handleStatClick = (statId) => {
  if (statId === 'all') {
    filterStatus.value = ''
  } else if (statId === 'overdue') {
    filterStatus.value = 'overdue'
  } else {
    filterStatus.value = statId
  }
}

onMounted(() => {
  loadDiscussions()
})
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <!-- 统计卡片 -->
    <div class="grid grid-cols-2 md:grid-cols-5 gap-4">
      <div 
        v-for="stat in statCards" 
        :key="stat.id"
        @click="handleStatClick(stat.id)"
        :class="[
          'p-4 rounded-xl cursor-pointer transition-all border-2',
          filterStatus === stat.id || (stat.id === 'all' && !filterStatus) 
            ? `bg-${stat.color}/10 border-${stat.color}/30` 
            : 'bg-white border-transparent hover:border-slate-200'
        ]"
      >
        <div class="text-2xl font-bold font-mono" :class="`text-${stat.color}`">{{ stat.count }}</div>
        <div class="text-sm text-shuimo/60">{{ stat.label }}</div>
      </div>
    </div>
    
    <!-- 筛选栏 -->
    <div class="flex items-center gap-4 flex-wrap">
      <div class="flex items-center gap-2">
        <Filter class="w-4 h-4 text-shuimo/50" />
        <div class="min-w-[160px]">
          <BaseSelect 
            v-model="filterCourse"
            :options="courseOptions"
            size="sm"
          />
        </div>
      </div>
      
      <div class="min-w-[140px]">
        <BaseSelect 
          v-model="filterStatus"
          :options="statusOptions"
          size="sm"
        />
      </div>
      
      <button 
        @click="loadDiscussions"
        class="px-3 py-2 rounded-lg bg-tianlv/10 text-tianlv hover:bg-tianlv/20 transition-colors text-sm"
      >
        刷新
      </button>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="space-y-4">
      <div v-for="i in 3" :key="i" class="bg-white rounded-xl p-6 animate-pulse">
        <div class="h-5 bg-slate-200 rounded w-1/3 mb-3"></div>
        <div class="h-4 bg-slate-200 rounded w-full mb-2"></div>
        <div class="h-4 bg-slate-200 rounded w-2/3"></div>
      </div>
    </div>

    <!-- 空状态 -->
    <EmptyState 
      v-else-if="filteredGroups.length === 0" 
      icon="message" 
      title="暂无讨论问题" 
      description="学生提问后将在这里显示"
    />
    
    <!-- 分组列表 -->
    <div v-else class="space-y-4">
      <GlassCard v-for="group in filteredGroups" :key="`${group.courseId}_${group.chapterId}`" class="overflow-hidden">
        <!-- 分组标题 -->
        <div 
          @click="toggleGroup(group)"
          class="flex items-center justify-between cursor-pointer p-4 -m-4 mb-0 hover:bg-slate-50 transition-colors"
        >
          <div class="flex items-center gap-3">
            <component :is="isGroupExpanded(group) ? ChevronUp : ChevronDown" class="w-5 h-5 text-shuimo/50" />
            <div>
              <h3 class="font-bold text-shuimo">{{ group.courseTitle }}</h3>
              <p v-if="group.chapterTitle" class="text-sm text-shuimo/50">{{ group.chapterTitle }}</p>
            </div>
          </div>
          <div class="flex items-center gap-3">
            <span v-if="group.overdueCount > 0" class="px-2 py-1 text-xs rounded-full bg-yanzhi/10 text-yanzhi flex items-center gap-1">
              <AlertTriangle class="w-3 h-3" /> {{ group.overdueCount }} 超时
            </span>
            <span v-if="group.pendingCount > 0" class="px-2 py-1 text-xs rounded-full bg-zhizi/10 text-zhizi">
              {{ group.pendingCount }} 待回复
            </span>
            <span class="text-sm text-shuimo/50">共 {{ group.totalCount }} 条</span>
          </div>
        </div>
        
        <!-- 讨论列表 -->
        <div v-if="isGroupExpanded(group)" class="mt-4 space-y-3 border-t border-slate-100 pt-4">
          <div 
            v-for="discussion in group.discussions" 
            :key="discussion.id"
            :class="[
              'p-4 rounded-xl border transition-all',
              discussion.isOverdue ? 'border-yanzhi/30 bg-yanzhi/5' : 'border-slate-100 bg-slate-50/50',
              discussion.isTop === 1 ? 'ring-2 ring-zhizi/30' : ''
            ]"
          >
            <div class="flex items-start justify-between mb-3">
              <div class="flex items-center gap-3">
                <div class="w-10 h-10 rounded-full bg-gradient-to-br from-qinghua to-danqing flex items-center justify-center text-white font-medium">
                  {{ (discussion.userName || '匿名').charAt(0) }}
                </div>
                <div>
                  <div class="flex items-center gap-2">
                    <p class="font-medium text-shuimo">{{ discussion.userName || '匿名学生' }}</p>
                    <span v-if="discussion.isTop === 1" class="text-xs text-zhizi flex items-center gap-1">
                      <Pin class="w-3 h-3" /> 置顶
                    </span>
                  </div>
                  <p class="text-xs text-shuimo/50">{{ formatTime(discussion.createdAt) }}</p>
                </div>
              </div>
              
              <div class="flex items-center gap-2">
                <!-- 超时标记 -->
                <span v-if="discussion.isOverdue" class="px-2 py-1 text-xs rounded-full bg-yanzhi/10 text-yanzhi flex items-center gap-1">
                  <Clock class="w-3 h-3" /> 超48小时
                </span>
                <!-- 状态标签 -->
                <span :class="['px-2 py-1 text-xs rounded-full border', getStatusClass(discussion.answerStatus)]">
                  {{ getStatusText(discussion.answerStatus) }}
                </span>
              </div>
            </div>
            
            <p class="text-sm text-shuimo/70 mb-4 bg-white p-3 rounded-lg">{{ discussion.content }}</p>
            
            <div class="flex items-center justify-between">
              <span class="text-sm text-shuimo/50">{{ discussion.replyCount || 0 }} 条回复</span>
              
              <div class="flex items-center gap-2">
                <!-- 状态切换 -->
                <div class="min-w-[100px]">
                  <BaseSelect 
                    :modelValue="discussion.answerStatus"
                    @update:modelValue="updateStatus(discussion, $event)"
                    :options="discussionStatusOptions"
                    size="sm"
                  />
                </div>
                
                <!-- 置顶按钮 -->
                <button 
                  @click="toggleTop(discussion)"
                  :class="['p-1.5 rounded-lg transition-colors', discussion.isTop === 1 ? 'bg-zhizi/10 text-zhizi' : 'hover:bg-slate-100 text-shuimo/50']"
                  :title="discussion.isTop === 1 ? '取消置顶' : '置顶'"
                >
                  <Pin class="w-4 h-4" />
                </button>
                
                <!-- 回复按钮 -->
                <BaseButton @click="openReply(discussion)" size="sm" variant="secondary">
                  <Send class="w-3 h-3 mr-1" /> 回复
                </BaseButton>
              </div>
            </div>
          </div>
        </div>
      </GlassCard>
    </div>

    <!-- 回复弹窗 -->
    <div v-if="showReplyModal" class="fixed inset-0 bg-shuimo/20 backdrop-blur-[2px] flex items-center justify-center z-50 p-4" @click.self="showReplyModal = false">
      <div class="bg-white rounded-2xl p-6 w-full max-w-md shadow-xl border border-slate-100 animate-scale-in">
        <h3 class="text-lg font-bold text-shuimo mb-4">回复: {{ selectedQuestion?.userName || '学生' }}</h3>
        <div class="bg-slate-50 rounded-xl p-4 mb-4">
          <p class="text-sm text-shuimo/70">{{ selectedQuestion?.content }}</p>
        </div>
        <textarea 
          v-model="replyContent" 
          rows="4" 
          class="w-full px-4 py-3 rounded-xl border border-slate-200 focus:ring-2 focus:ring-zijinghui/20 outline-none mb-4" 
          placeholder="输入回复内容..."
        ></textarea>
        <div class="flex gap-3">
          <BaseButton block variant="ghost" @click="showReplyModal = false" :disabled="submitting">取消</BaseButton>
          <BaseButton block variant="primary" @click="submitReply" :disabled="submitting || !replyContent.trim()">
            {{ submitting ? '发送中...' : '发送回复' }}
          </BaseButton>
        </div>
      </div>
    </div>
  </div>
</template>
