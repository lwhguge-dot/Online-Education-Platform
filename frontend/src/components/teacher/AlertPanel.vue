<script setup>
import { ref, computed, onMounted } from 'vue'
import { AlertTriangle, Users, Clock, TrendingDown, ChevronRight, Bell, X, Send, MessageCircle } from 'lucide-vue-next'
import { useAuthStore } from '../../stores/auth'
import { enrollmentAPI, notificationAPI } from '../../services/api'
import GlassCard from '../ui/GlassCard.vue'
import BaseButton from '../ui/BaseButton.vue'

const props = defineProps({
  maxItems: { type: Number, default: 5 },
  showTitle: { type: Boolean, default: true }
})

const emit = defineEmits(['viewStudent', 'viewAll', 'messageSent'])

const authStore = useAuthStore()

// 状态
const loading = ref(false)
const alertStudents = ref([])
const totalAlerts = ref(0)
const sendingMessage = ref(false)
const showMessageModal = ref(false)
const selectedStudent = ref(null)
const messageContent = ref('')

// 预警类型配置
const alertTypeConfig = {
  inactive: { 
    icon: Clock, 
    label: '长期未活跃', 
    color: 'yanzhi',
    bgColor: 'bg-yanzhi/10',
    textColor: 'text-yanzhi'
  },
  'low-progress': { 
    icon: TrendingDown, 
    label: '进度落后', 
    color: 'zhizi',
    bgColor: 'bg-zhizi/10',
    textColor: 'text-zhizi'
  },
  'low-score': { 
    icon: AlertTriangle, 
    label: '成绩预警', 
    color: 'yanzhi',
    bgColor: 'bg-yanzhi/10',
    textColor: 'text-yanzhi'
  }
}

// 预设消息模板
const messageTemplates = [
  { label: '学习提醒', content: '同学你好，老师注意到你最近的学习进度有些落后，希望你能抽时间继续学习课程内容。如有任何问题，欢迎随时联系老师。' },
  { label: '鼓励消息', content: '同学你好，学习是一个循序渐进的过程，不要气馁。老师相信你一定能够跟上进度，加油！' },
  { label: '答疑邀请', content: '同学你好，老师发现你在学习中可能遇到了一些困难。如果有任何不理解的地方，欢迎在讨论区提问或者私信老师。' }
]

// 加载预警学生
const loadAlertStudents = async () => {
  const userId = authStore.user?.id
  if (!userId) return
  
  loading.value = true
  try {
    const res = await enrollmentAPI.getTeacherStudentsOverview(userId)
    if (res.code === 200 && res.data) {
      // 从各课程中提取预警学生
      const alerts = []
      const courses = res.data.courses || []
      
      for (const course of courses) {
        // 获取该课程的预警学生
        const courseRes = await enrollmentAPI.getCourseStudentsWithStatus(
          course.courseId, 1, 100, 'at-risk'
        )
        if (courseRes.code === 200 && courseRes.data?.students) {
          courseRes.data.students.forEach(student => {
            alerts.push({
              ...student,
              courseName: course.courseTitle,
              courseId: course.courseId
            })
          })
        }
        
        // 获取不活跃学生
        const inactiveRes = await enrollmentAPI.getCourseStudentsWithStatus(
          course.courseId, 1, 100, 'inactive'
        )
        if (inactiveRes.code === 200 && inactiveRes.data?.students) {
          inactiveRes.data.students.forEach(student => {
            // 避免重复
            if (!alerts.find(a => a.studentId === student.studentId && a.courseId === course.courseId)) {
              alerts.push({
                ...student,
                courseName: course.courseTitle,
                courseId: course.courseId
              })
            }
          })
        }
      }
      
      // 按预警级别排序（inactive > at-risk）
      alerts.sort((a, b) => {
        const priority = { inactive: 0, 'at-risk': 1 }
        return (priority[a.learningStatus] || 2) - (priority[b.learningStatus] || 2)
      })
      
      totalAlerts.value = alerts.length
      alertStudents.value = alerts.slice(0, props.maxItems)
    }
  } catch (e) {
    console.error('加载预警学生失败', e)
  } finally {
    loading.value = false
  }
}

// 获取预警类型
const getAlertType = (student) => {
  if (student.learningStatus === 'inactive') return 'inactive'
  if (student.courseProgress < 30) return 'low-progress'
  return 'low-score'
}

// 查看学生详情
const viewStudent = (student) => {
  emit('viewStudent', student)
}

// 查看全部
const viewAll = () => {
  emit('viewAll')
}

// 打开发送消息弹窗
const openMessageModal = (student, event) => {
  event?.stopPropagation()
  selectedStudent.value = student
  messageContent.value = ''
  showMessageModal.value = true
}

// 关闭消息弹窗
const closeMessageModal = () => {
  showMessageModal.value = false
  selectedStudent.value = null
  messageContent.value = ''
}

// 使用模板
const useTemplate = (template) => {
  messageContent.value = template.content
}

// 发送提醒消息
const sendReminder = async () => {
  if (!selectedStudent.value || !messageContent.value.trim()) return
  
  sendingMessage.value = true
  try {
    const teacherName = authStore.user?.name || '老师'
    const title = `来自${teacherName}的学习提醒`
    
    const res = await notificationAPI.send(
      selectedStudent.value.studentId,
      title,
      messageContent.value.trim(),
      'REMINDER'
    )
    
    if (res.code === 200) {
      emit('messageSent', {
        student: selectedStudent.value,
        message: messageContent.value
      })
      closeMessageModal()
    }
  } catch (e) {
    console.error('发送提醒失败', e)
  } finally {
    sendingMessage.value = false
  }
}

// 快速发送提醒（使用默认模板）
const quickSendReminder = async (student, event) => {
  event?.stopPropagation()
  
  const teacherName = authStore.user?.name || '老师'
  const alertType = getAlertType(student)
  let content = ''
  
  if (alertType === 'inactive') {
    content = `同学你好，老师注意到你已经有一段时间没有学习《${student.courseName}》了。希望你能抽时间继续学习，如有任何问题欢迎随时联系老师。`
  } else if (alertType === 'low-progress') {
    content = `同学你好，你在《${student.courseName}》的学习进度目前是${student.courseProgress}%，建议加快学习进度。如果遇到困难，欢迎在讨论区提问。`
  } else {
    content = `同学你好，老师注意到你在《${student.courseName}》的学习中可能遇到了一些困难。如果有任何不理解的地方，欢迎随时联系老师。`
  }
  
  try {
    const res = await notificationAPI.send(
      student.studentId,
      `来自${teacherName}的学习提醒`,
      content,
      'REMINDER'
    )
    
    if (res.code === 200) {
      emit('messageSent', { student, message: content })
    }
  } catch (e) {
    console.error('发送提醒失败', e)
  }
}

onMounted(() => {
  loadAlertStudents()
})

// 暴露刷新方法
defineExpose({ refresh: loadAlertStudents })
</script>

<template>
  <GlassCard class="overflow-hidden">
    <!-- 标题 -->
    <div v-if="showTitle" class="flex items-center justify-between p-4 border-b border-slate-100">
      <div class="flex items-center gap-2">
        <div class="p-2 bg-yanzhi/10 rounded-lg">
          <Bell class="w-5 h-5 text-yanzhi" />
        </div>
        <div>
          <h3 class="font-bold text-shuimo">学生预警</h3>
          <p class="text-xs text-shuimo/50">需要关注的学生</p>
        </div>
      </div>
      <div v-if="totalAlerts > 0" class="px-3 py-1 bg-yanzhi/10 rounded-full">
        <span class="text-sm font-bold text-yanzhi">{{ totalAlerts }}</span>
      </div>
    </div>
    
    <!-- 加载状态 -->
    <div v-if="loading" class="p-6 space-y-3">
      <div v-for="i in 3" :key="i" class="animate-pulse flex items-center gap-3">
        <div class="w-10 h-10 bg-slate-200 rounded-full"></div>
        <div class="flex-1 space-y-2">
          <div class="h-4 bg-slate-200 rounded w-1/3"></div>
          <div class="h-3 bg-slate-200 rounded w-1/2"></div>
        </div>
      </div>
    </div>
    
    <!-- 预警列表 -->
    <div v-else-if="alertStudents.length > 0" class="divide-y divide-slate-50">
      <div 
        v-for="student in alertStudents" 
        :key="`${student.studentId}-${student.courseId}`"
        @click="viewStudent(student)"
        class="p-4 hover:bg-slate-50/50 cursor-pointer transition-colors group"
      >
        <div class="flex items-center gap-3">
          <!-- 头像 -->
          <div class="relative">
            <div class="w-10 h-10 rounded-full bg-gradient-to-br from-yanzhi/80 to-zhizi flex items-center justify-center text-white font-medium shadow-sm">
              {{ student.name?.charAt(0) || '?' }}
            </div>
            <div class="absolute -bottom-1 -right-1 p-0.5 bg-white rounded-full">
              <component 
                :is="alertTypeConfig[getAlertType(student)]?.icon || AlertTriangle" 
                class="w-3.5 h-3.5"
                :class="alertTypeConfig[getAlertType(student)]?.textColor || 'text-yanzhi'"
              />
            </div>
          </div>
          
          <!-- 信息 -->
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2">
              <span class="font-medium text-shuimo truncate">{{ student.name }}</span>
              <span :class="['text-xs px-2 py-0.5 rounded-full', 
                alertTypeConfig[getAlertType(student)]?.bgColor,
                alertTypeConfig[getAlertType(student)]?.textColor]">
                {{ alertTypeConfig[getAlertType(student)]?.label }}
              </span>
            </div>
            <div class="text-xs text-shuimo/50 mt-0.5 truncate">
              {{ student.courseName }} · 进度 {{ student.courseProgress }}%
            </div>
          </div>
          
          <!-- 操作按钮 -->
          <div class="flex items-center gap-1">
            <!-- 快速发送提醒 -->
            <button 
              @click="quickSendReminder(student, $event)"
              class="p-1.5 rounded-lg hover:bg-tianlv/10 text-shuimo/40 hover:text-tianlv transition-colors"
              title="快速发送提醒"
            >
              <Send class="w-4 h-4" />
            </button>
            <!-- 自定义消息 -->
            <button 
              @click="openMessageModal(student, $event)"
              class="p-1.5 rounded-lg hover:bg-qingsong/10 text-shuimo/40 hover:text-qingsong transition-colors"
              title="发送自定义消息"
            >
              <MessageCircle class="w-4 h-4" />
            </button>
            <!-- 箭头 -->
            <ChevronRight class="w-4 h-4 text-shuimo/30 group-hover:text-shuimo/60 transition-colors" />
          </div>
        </div>
        
        <!-- 预警详情 -->
        <div v-if="student.alerts?.length" class="mt-2 pl-13 flex flex-wrap gap-1">
          <span 
            v-for="(alert, idx) in student.alerts.slice(0, 2)" 
            :key="idx"
            class="text-xs bg-yanzhi/5 text-yanzhi/80 px-2 py-0.5 rounded"
          >
            {{ alert }}
          </span>
          <span v-if="student.alerts.length > 2" class="text-xs text-shuimo/40">
            +{{ student.alerts.length - 2 }}
          </span>
        </div>
      </div>
    </div>
    
    <!-- 空状态 -->
    <div v-else class="p-8 text-center">
      <div class="w-12 h-12 mx-auto mb-3 bg-qingsong/10 rounded-full flex items-center justify-center">
        <Users class="w-6 h-6 text-qingsong" />
      </div>
      <p class="text-sm text-shuimo/60">暂无预警学生</p>
      <p class="text-xs text-shuimo/40 mt-1">所有学生学习状态良好</p>
    </div>
    
    <!-- 查看全部 -->
    <div v-if="totalAlerts > maxItems" class="p-3 border-t border-slate-100 bg-slate-50/30">
      <button 
        @click="viewAll"
        class="w-full text-center text-sm text-tianlv hover:text-qingsong transition-colors py-1"
      >
        查看全部 {{ totalAlerts }} 位预警学生
      </button>
    </div>
  </GlassCard>
  
  <!-- 发送消息弹窗 -->
  <Teleport to="body">
    <div v-if="showMessageModal" class="fixed inset-0 z-50 flex items-center justify-center">
      <!-- 遮罩 -->
      <div class="absolute inset-0 bg-shuimo/20 backdrop-blur-[2px]" @click="closeMessageModal"></div>
      
      <!-- 弹窗内容 -->
      <div class="relative bg-white rounded-2xl shadow-xl w-full max-w-md mx-4 overflow-hidden">
        <!-- 头部 -->
        <div class="flex items-center justify-between p-4 border-b border-slate-100">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-full bg-gradient-to-br from-tianlv to-qingsong flex items-center justify-center text-white">
              <MessageCircle class="w-5 h-5" />
            </div>
            <div>
              <h3 class="font-bold text-shuimo">发送提醒消息</h3>
              <p class="text-xs text-shuimo/50">给 {{ selectedStudent?.name }} 发送学习提醒</p>
            </div>
          </div>
          <button @click="closeMessageModal" class="p-2 hover:bg-slate-100 rounded-lg transition-colors">
            <X class="w-5 h-5 text-shuimo/50" />
          </button>
        </div>
        
        <!-- 消息模板 -->
        <div class="p-4 border-b border-slate-100">
          <p class="text-xs text-shuimo/60 mb-2">快速选择模板：</p>
          <div class="flex flex-wrap gap-2">
            <button 
              v-for="template in messageTemplates" 
              :key="template.label"
              @click="useTemplate(template)"
              class="px-3 py-1.5 text-xs bg-slate-100 hover:bg-tianlv/10 hover:text-tianlv rounded-full transition-colors"
            >
              {{ template.label }}
            </button>
          </div>
        </div>
        
        <!-- 消息输入 -->
        <div class="p-4">
          <textarea 
            v-model="messageContent"
            placeholder="输入提醒消息内容..."
            class="w-full h-32 p-3 border border-slate-200 rounded-xl resize-none focus:outline-none focus:ring-2 focus:ring-tianlv/30 focus:border-tianlv text-sm"
          ></textarea>
        </div>
        
        <!-- 底部按钮 -->
        <div class="flex items-center justify-end gap-3 p-4 bg-slate-50">
          <button 
            @click="closeMessageModal"
            class="px-4 py-2 text-sm text-shuimo/70 hover:text-shuimo transition-colors"
          >
            取消
          </button>
          <button 
            @click="sendReminder"
            :disabled="!messageContent.trim() || sendingMessage"
            class="px-4 py-2 text-sm bg-tianlv text-white rounded-lg hover:bg-qingsong transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
          >
            <Send class="w-4 h-4" />
            {{ sendingMessage ? '发送中...' : '发送提醒' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.pl-13 {
  padding-left: 3.25rem;
}
</style>
