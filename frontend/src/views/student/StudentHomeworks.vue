<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { FileText, Clock, CheckCircle, AlertCircle, ChevronRight, Filter, RotateCcw, AlertTriangle } from 'lucide-vue-next'
import GlassCard from '../../components/ui/GlassCard.vue'
import EmptyState from '../../components/ui/EmptyState.vue'
import BaseSelect from '../../components/ui/BaseSelect.vue'
import { formatDateTimeCN } from '../../utils/datetime'
import { useAuthStore } from '../../stores/auth'
import { useStudentCourses } from '../../composables/useStudentCourses'
import { useStudentHomeworks } from '../../composables/useStudentHomeworks'

const router = useRouter()
const authStore = useAuthStore()

// Composables
const { enrolledCourses, loadEnrolledCourses } = useStudentCourses()
const { pendingHomeworks, completedHomeworks, loadHomeworks } = useStudentHomeworks()

const activeTab = ref('pending')
const courseFilter = ref('all')

const formatDisplayDateTime = (dateStr) => {
  return formatDateTimeCN(dateStr, '未知时间')
}

// 从全部作业中提取课程列表
const availableCourses = computed(() => {
  const allHomeworks = [...pendingHomeworks.value, ...completedHomeworks.value]
  const courses = [...new Set(allHomeworks.map(hw => hw.course).filter(Boolean))]
  return courses.sort()
})

// 格式化课程选项供 BaseSelect 使用
const courseOptions = computed(() => {
  return [
    { value: 'all', label: '全部课程' },
    ...availableCourses.value.map(course => ({ value: course, label: course }))
  ]
})

// 作业紧急程度判断
const getHomeworkUrgency = (daysLeft) => {
  if (daysLeft === null || daysLeft === undefined) return 'none'
  if (daysLeft < 0) return 'overdue'
  if (daysLeft <= 1) return 'critical'
  if (daysLeft <= 3) return 'warning'
  return 'normal'
}

// 紧急程度样式映射
const urgencyStyles = {
  overdue: {
    card: 'border-2 border-red-300 bg-red-50/50',
    icon: 'bg-red-100 text-red-600',
    badge: 'bg-red-500 text-white',
    text: 'text-red-600 font-bold'
  },
  critical: {
    card: 'border-2 border-red-200 bg-red-50/30',
    icon: 'bg-red-100 text-red-500',
    badge: 'bg-red-500 text-white animate-pulse',
    text: 'text-red-500 font-bold'
  },
  warning: {
    card: 'border-2 border-amber-200 bg-amber-50/30',
    icon: 'bg-amber-100 text-amber-600',
    badge: 'bg-amber-500 text-white',
    text: 'text-amber-600 font-semibold'
  },
  normal: {
    card: '',
    icon: 'bg-zhizi/10 text-zhizi',
    badge: 'bg-slate-100 text-slate-500',
    text: 'text-slate-400'
  },
  none: {
    card: '',
    icon: 'bg-zhizi/10 text-zhizi',
    badge: 'bg-slate-100 text-slate-500',
    text: 'text-slate-400'
  }
}

// 按紧急程度排序的待完成作业
const sortedPendingHomeworks = computed(() => {
  const urgencyOrder = { overdue: 0, critical: 1, warning: 2, normal: 3, none: 4 }
  return [...pendingHomeworks.value].sort((a, b) => {
    const urgencyA = getHomeworkUrgency(a.daysLeft)
    const urgencyB = getHomeworkUrgency(b.daysLeft)
    return urgencyOrder[urgencyA] - urgencyOrder[urgencyB]
  })
})

// 过滤后的列表
const filteredPending = computed(() => {
  const sorted = sortedPendingHomeworks.value
  if (courseFilter.value === 'all') return sorted
  return sorted.filter(hw => hw.course === courseFilter.value)
})

const filteredCompleted = computed(() => {
  if (courseFilter.value === 'all') return completedHomeworks.value
  return completedHomeworks.value.filter(hw => hw.course === courseFilter.value)
})

// 判断作业是否存在错题（分数小于 100）
const hasMistakes = (hw) => {
  return hw.status === 'graded' && hw.totalScore !== null && hw.totalScore < 100
}

const getScoreColor = (score) => {
  if (score >= 90) return 'text-emerald-500'
  if (score >= 80) return 'text-tianlv'
  if (score >= 60) return 'text-amber-500'
  return 'text-red-500'
}

const getScoreBgColor = (score) => {
  if (score >= 90) return 'bg-emerald-50'
  if (score >= 80) return 'bg-tianlv/10'
  if (score >= 60) return 'bg-amber-50'
  return 'bg-red-50'
}

const formatDeadline = (days) => {
  if (days === null || days === undefined) return '无截止日期'
  if (days < 0) return '已截止'
  if (days === 0) return '今天截止'
  if (days === 1) return '明天截止'
  return `${days}天后截止`
}

const getCountdown = (daysLeft) => {
  if (daysLeft === null || daysLeft === undefined) return null
  if (daysLeft < 0) return null
  if (daysLeft === 0) return null
  if (daysLeft > 0 && daysLeft < 1) return { text: `剩余 ${Math.ceil(daysLeft * 24)} 小时`, urgent: true }
  return null
}

const handleStartHomework = (hw) => {
   router.push(`/homework/${hw.id}`)
}

const handleViewHomework = (hw) => {
   router.push(`/homework/${hw.id}?view=true`)
}

onMounted(async () => {
  const userId = authStore.user?.id
  if (userId) {
     // Check if we need to load courses first (if not already loaded by other views or kept in keep-alive, but let's be safe)
     // useStudentCourses uses local state, so we must load.
     await loadEnrolledCourses(userId)
     if (enrolledCourses.value.length > 0) {
        await loadHomeworks(userId, enrolledCourses.value)
     }
  }
})
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <!-- 顶部工具栏 -->
    <GlassCard class="p-4 animate-slide-up" style="animation-fill-mode: both;">
      <div class="flex items-center justify-between gap-4 flex-wrap">
        <!-- 左侧：标题和标签页 -->
        <div class="flex items-center gap-4">
          <h3 class="text-lg font-bold text-shuimo flex items-center gap-2 font-song shrink-0">
            <FileText class="w-5 h-5 text-zhizi icon-hover-rotate" />
            作业中心
          </h3>
          <div class="flex gap-2">
            <button 
              v-for="tab in [{id: 'pending', label: '待完成'}, {id: 'completed', label: '已提交'}]"
              :key="tab.id"
              @click="activeTab = tab.id"
              class="px-4 py-2 rounded-xl text-sm font-medium transition-all btn-ripple"
              :class="activeTab === tab.id ? 'bg-zhizi text-white shadow-lg shadow-zhizi/30' : 'bg-slate-50 text-shuimo/70 hover:bg-slate-100'"
            >
              {{ tab.label }}
              <span v-if="tab.id === 'pending' && filteredPending.length > 0" class="ml-1 px-1.5 py-0.5 rounded-full bg-white/20 text-xs animate-pulse">
                {{ filteredPending.length }}
              </span>
            </button>
          </div>
        </div>
        
        <!-- 右侧：课程筛选 -->
        <div class="flex items-center gap-2">
          <Filter class="w-4 h-4 text-shuimo/40" />
          <div class="w-40">
            <BaseSelect 
              v-model="courseFilter"
              :options="courseOptions"
              size="sm"
              align="right"
            />
          </div>
        </div>
      </div>
    </GlassCard>

    <!-- 内容区 -->
    <div class="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-4">
       <!-- 待完成作业 -->
       <template v-if="activeTab === 'pending'">
          <GlassCard 
            v-for="(hw, index) in filteredPending" 
            :key="hw.id"
            class="p-4 flex flex-col justify-between gap-4 transition-all cursor-pointer group card-hover-lift stagger-item h-full animate-slide-up"
            :class="urgencyStyles[getHomeworkUrgency(hw.daysLeft)].card"
            :style="{ animationDelay: `${index * 0.08}s`, animationFillMode: 'both' }"
            @click="handleStartHomework(hw)"
          >
             <div class="flex items-start gap-4">
                <div 
                  class="w-12 h-12 rounded-xl flex items-center justify-center shrink-0 group-hover:scale-110 transition-transform"
                  :class="urgencyStyles[getHomeworkUrgency(hw.daysLeft)].icon"
                >
                   <AlertTriangle v-if="getHomeworkUrgency(hw.daysLeft) === 'critical' || getHomeworkUrgency(hw.daysLeft) === 'overdue'" class="w-6 h-6 animate-pulse" />
                   <FileText v-else class="w-6 h-6" />
                </div>
                <div class="flex-1 min-w-0">
                   <div class="flex items-center gap-2 flex-wrap">
                     <h3 class="font-bold text-shuimo group-hover:text-zhizi transition-colors truncate">{{ hw.title }}</h3>
                     <!-- 紧急标签 -->
                     <span 
                       v-if="getHomeworkUrgency(hw.daysLeft) === 'critical' || getHomeworkUrgency(hw.daysLeft) === 'overdue'"
                       class="text-xs px-2 py-0.5 rounded-full animate-pulse shrink-0"
                       :class="urgencyStyles[getHomeworkUrgency(hw.daysLeft)].badge"
                     >
                       {{ getHomeworkUrgency(hw.daysLeft) === 'overdue' ? '已截止' : '紧急' }}
                     </span>
                     <span 
                       v-else-if="getHomeworkUrgency(hw.daysLeft) === 'warning'"
                       class="text-xs px-2 py-0.5 rounded-full shrink-0"
                       :class="urgencyStyles[getHomeworkUrgency(hw.daysLeft)].badge"
                     >
                       即将截止
                     </span>
                   </div>
                   <p class="text-xs text-shuimo/50 mt-1 truncate">{{ hw.course }}</p>
                   <div class="flex items-center gap-3 mt-2 flex-wrap">
                      <span class="text-xs px-2 py-0.5 rounded bg-slate-100 text-slate-500">
                         {{ hw.type === 'objective' ? '客观题' : '主观题' }}
                      </span>
                      <span 
                        class="text-xs flex items-center gap-1"
                        :class="urgencyStyles[getHomeworkUrgency(hw.daysLeft)].text"
                      >
                         <Clock class="w-3 h-3" />
                         {{ formatDeadline(hw.daysLeft) }}
                      </span>
                      <!-- 倒计时显示 -->
                      <span 
                        v-if="getCountdown(hw.daysLeft)"
                        class="text-xs px-2 py-0.5 rounded-full bg-red-100 text-red-600 font-mono animate-pulse"
                      >
                        {{ getCountdown(hw.daysLeft).text }}
                      </span>
                   </div>
                </div>
             </div>
             <div class="flex justify-end">
                <button 
                  class="px-4 py-2 rounded-lg text-white text-sm font-bold shadow-md transition-all whitespace-nowrap btn-ripple hover:scale-105 active:scale-95"
                  :class="getHomeworkUrgency(hw.daysLeft) === 'critical' || getHomeworkUrgency(hw.daysLeft) === 'overdue' 
                    ? 'bg-red-500 shadow-red-500/20 hover:bg-red-600' 
                    : getHomeworkUrgency(hw.daysLeft) === 'warning'
                      ? 'bg-amber-500 shadow-amber-500/20 hover:bg-amber-600'
                      : 'bg-zhizi shadow-zhizi/20 hover:bg-zhizi/90'"
                >
                   {{ getHomeworkUrgency(hw.daysLeft) === 'overdue' ? '补交作业' : '开始作业' }}
                </button>
             </div>
          </GlassCard>

          <EmptyState
            v-if="pendingHomeworks.length === 0" 
            title="所有作业都完成了，太棒了！" 
            icon="check"
            type="success"
            size="sm"
            class="col-span-full"
          />
       </template>

       <!-- 已提交作业 -->
       <template v-else>
          <GlassCard 
            v-for="(hw, index) in filteredCompleted" 
            :key="hw.id"
            class="p-4 flex flex-col justify-between gap-4 hover:bg-slate-50 transition-all cursor-pointer stagger-item card-hover-lift h-full animate-slide-up"
            :style="{ animationDelay: `${index * 0.08}s`, animationFillMode: 'both' }"
            @click="handleViewHomework(hw)"
          >
             <div class="flex items-start gap-4">
                <!-- 根据分数调整图标颜色 -->
                <div 
                  class="w-12 h-12 rounded-xl flex items-center justify-center shrink-0 transition-transform hover:scale-110"
                  :class="hw.status === 'graded' ? getScoreBgColor(hw.totalScore) : 'bg-amber-50'"
                >
                   <CheckCircle v-if="hw.status === 'graded' && hw.totalScore >= 60" class="w-6 h-6" :class="getScoreColor(hw.totalScore)" />
                   <AlertCircle v-else-if="hw.status === 'graded'" class="w-6 h-6 text-red-500" />
                   <Clock v-else class="w-6 h-6 text-amber-500 animate-pulse" />
                </div>
                <div class="flex-1 min-w-0">
                   <h3 class="font-bold text-shuimo truncate">{{ hw.title }}</h3>
                   <p class="text-xs text-shuimo/50 mt-1 truncate">{{ hw.course }}</p>
                   <div class="flex items-center gap-3 mt-2">
                       <span class="text-xs text-slate-400">提交于 {{ formatDisplayDateTime(hw.submitTime) }}</span>
                   </div>
                </div>
             </div>
             <div class="flex items-center justify-between gap-4 pt-3 border-t border-slate-100">
                <div>
                   <div v-if="hw.status === 'graded'" class="flex items-center gap-2">
                      <span class="text-2xl font-bold font-mono number-pop" :class="getScoreColor(hw.totalScore)">
                         {{ hw.totalScore }}<span class="text-sm text-slate-400 font-normal">分</span>
                      </span>
                      <span class="text-xs text-qingsong bg-qingsong/10 px-2 py-0.5 rounded">已批改</span>
                   </div>
                   <div v-else>
                      <span class="text-sm font-bold text-amber-500 bg-amber-50 px-2 py-1 rounded animate-pulse">
                         等待批改
                      </span>
                   </div>
                </div>
                <!-- 查看错题按钮 -->
                <button 
                  v-if="hasMistakes(hw)"
                  @click.stop="handleViewHomework(hw)"
                  class="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-yanzhi/10 text-yanzhi text-xs font-bold hover:bg-yanzhi hover:text-white transition-all whitespace-nowrap btn-ripple hover:scale-105"
                >
                  <RotateCcw class="w-3.5 h-3.5" />
                  查看错题
                </button>
                <ChevronRight v-else class="w-5 h-5 text-slate-300 transition-transform group-hover:translate-x-1" />
             </div>
          </GlassCard>

          <EmptyState
            v-if="filteredCompleted.length === 0"
            title="还没有提交过作业"
            icon="alert"
            type="empty"
            size="sm"
            class="col-span-full"
          />
       </template>
    </div>
  </div>
</template>
