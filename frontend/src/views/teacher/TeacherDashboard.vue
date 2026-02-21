<script setup>
import { computed, ref } from 'vue'
import {
  BookOpen, Users, ClipboardCheck, Activity,
  AlertCircle, Plus, FileText,
  Clock, TrendingUp, Megaphone
} from 'lucide-vue-next'
import GlassCard from '../../components/ui/GlassCard.vue'
import AlertPanel from '../../components/teacher/AlertPanel.vue'
import AnnouncementEditor from '../../components/teacher/AnnouncementEditor.vue'
import AnimatedNumber from '../../components/ui/AnimatedNumber.vue'
import EmptyState from '../../components/ui/EmptyState.vue'

const props = defineProps({
  stats: {
    type: Object,
    required: true,
    default: () => ({
      myCourses: 0,
      publishedCourses: 0,
      totalStudents: 0,
      pendingHomework: 0,
      weeklyInteractions: 0,
      newStudentsToday: 0,
      urgentItems: [],
      courseRankings: [],
      weeklyTrend: null
    })
  },
  todos: {
    type: Array,
    default: () => []
  },
  activities: {
    type: Array,
    default: () => []
  },
  courseProgress: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['navigate'])

const alertPanelRef = ref(null)
const showAnnouncementEditor = ref(false)

const navigateTo = (menu, tab = null) => {
  emit('navigate', { menu, tab })
}

// 处理预警学生点击
const handleViewAlertStudent = (student) => {
  // 导航到学生管理页面并选中该课程
  emit('navigate', { 
    menu: 'students', 
    courseId: student.courseId,
    studentId: student.studentId 
  })
}

// 查看全部预警学生
const handleViewAllAlerts = () => {
  emit('navigate', { menu: 'students', filter: 'at-risk' })
}

// 打开公告编辑器
const openAnnouncementEditor = () => {
  showAnnouncementEditor.value = true
}

// 公告发布成功
const onAnnouncementSuccess = () => {
  // 可以在这里刷新数据或显示提示
}

// 合并紧急事项和待办事项
const allUrgentItems = computed(() => {
  const items = []
  
  // 添加后端返回的紧急事项
  if (props.stats.urgentItems && props.stats.urgentItems.length > 0) {
    items.push(...props.stats.urgentItems.map(item => ({
      id: item.id,
      type: item.type,
      title: item.title,
      time: item.deadline || '',
      urgent: true,
      courseName: item.courseName
    })))
  }
  
  // 添加待办事项
  if (props.todos && props.todos.length > 0) {
    items.push(...props.todos.map(todo => ({
      ...todo,
      urgent: todo.urgent || false
    })))
  }
  
  return items
})

// 课程完成率排名（使用后端数据或本地数据）
const courseRankings = computed(() => {
  if (props.stats.courseRankings && props.stats.courseRankings.length > 0) {
    return props.stats.courseRankings.slice(0, 5)
  }
  return props.courseProgress || []
})

// 本周互动环比：对异常值做兜底，避免出现 NaN%
const weeklyTrendValue = computed(() => {
  const raw = Number(props.stats?.weeklyTrend)
  if (!Number.isFinite(raw)) {
    return 0
  }
  return raw
})

// 环比方向：1 上升，-1 下降，0 持平
const weeklyTrendDirection = computed(() => {
  if (weeklyTrendValue.value > 0) return 1
  if (weeklyTrendValue.value < 0) return -1
  return 0
})
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <!-- 统计卡片网格 -->
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
      <!-- 我的课程 -->
      <!-- 可访问性：统计卡片支持键盘激活 -->
      <GlassCard
        hoverable
        class="stat-card relative overflow-hidden group cursor-pointer"
        role="button"
        tabindex="0"
        aria-label="进入课程管理"
        @click="navigateTo('courses')"
        @keydown.enter.prevent="navigateTo('courses')"
        @keydown.space.prevent="navigateTo('courses')"
      >
        <div class="flex items-center justify-between mb-4 relative z-10">
          <span class="text-sm font-medium text-shuimo/60">我的课程</span>
          <div class="p-2.5 rounded-xl bg-gradient-to-br from-tianlv/15 to-tianlv/5 text-tianlv shadow-sm group-hover:shadow-md group-hover:scale-110 transition-all duration-300">
            <BookOpen class="w-5 h-5" />
          </div>
        </div>
        <div class="text-4xl font-bold font-mono relative z-10 bg-gradient-to-r from-tianlv to-qingsong bg-clip-text text-transparent group-hover:scale-105 transition-transform duration-300 origin-left">
          <AnimatedNumber :value="stats.myCourses" />
        </div>
        <p class="text-xs text-shuimo/50 mt-3 relative z-10 flex items-center gap-1">
          <span class="inline-block w-1.5 h-1.5 rounded-full bg-tianlv/60"></span>
          已发布 {{ stats.publishedCourses || 0 }} 门
        </p>
        <!-- 优化后的装饰图标 - 缩小尺寸、降低透明度 -->
        <BookOpen class="stat-card-icon absolute -bottom-2 -right-2 w-12 h-12 text-tianlv/[0.06]" />
      </GlassCard>

      <!-- 学生总数 -->
      <!-- 可访问性：统计卡片支持键盘激活 -->
      <GlassCard
        hoverable
        class="stat-card relative overflow-hidden group cursor-pointer"
        role="button"
        tabindex="0"
        aria-label="进入学生管理"
        @click="navigateTo('students')"
        @keydown.enter.prevent="navigateTo('students')"
        @keydown.space.prevent="navigateTo('students')"
      >
        <div class="flex items-center justify-between mb-4 relative z-10">
          <span class="text-sm font-medium text-shuimo/60">学生总数</span>
          <div class="p-2.5 rounded-xl bg-gradient-to-br from-qinghua/15 to-qinghua/5 text-qinghua shadow-sm group-hover:shadow-md group-hover:scale-110 transition-all duration-300">
            <Users class="w-5 h-5" />
          </div>
        </div>
        <div class="text-4xl font-bold font-mono relative z-10 bg-gradient-to-r from-qinghua to-halanzi bg-clip-text text-transparent group-hover:scale-105 transition-transform duration-300 origin-left">
          <AnimatedNumber :value="stats.totalStudents" :show-trend="stats.newStudentsToday > 0" />
        </div>
        <p class="text-xs text-shuimo/50 mt-3 relative z-10 flex items-center gap-1">
          今日新增 <span class="text-tianlv font-medium">+{{ stats.newStudentsToday || 0 }}</span>
        </p>
        <Users class="stat-card-icon absolute -bottom-2 -right-2 w-12 h-12 text-qinghua/[0.06]" />
      </GlassCard>

      <!-- 待批改作业 -->
      <!-- 可访问性：统计卡片支持键盘激活 -->
      <GlassCard
        hoverable
        class="stat-card relative overflow-hidden group cursor-pointer"
        role="button"
        tabindex="0"
        aria-label="进入待批改作业"
        @click="navigateTo('homework', { tab: 'pending' })"
        @keydown.enter.prevent="navigateTo('homework', { tab: 'pending' })"
        @keydown.space.prevent="navigateTo('homework', { tab: 'pending' })"
      >
        <div class="flex items-center justify-between mb-4 relative z-10">
          <span class="text-sm font-medium text-shuimo/60">待批改作业</span>
          <div class="p-2.5 rounded-xl bg-gradient-to-br from-zhizi/15 to-zhizi/5 text-zhizi shadow-sm group-hover:shadow-md group-hover:scale-110 transition-all duration-300">
            <ClipboardCheck class="w-5 h-5" />
          </div>
        </div>
        <div class="text-4xl font-bold font-mono relative z-10 bg-gradient-to-r from-zhizi to-chenpi bg-clip-text text-transparent group-hover:scale-105 transition-transform duration-300 origin-left">
          <AnimatedNumber :value="stats.pendingHomework" :highlight="stats.pendingHomework > 0" />
        </div>
        <p class="text-xs text-yanzhi mt-3 relative z-10 font-medium flex items-center gap-1">
          <span class="inline-block w-1.5 h-1.5 rounded-full bg-yanzhi animate-pulse"></span>
          需要尽快处理
        </p>
        <ClipboardCheck class="stat-card-icon absolute -bottom-2 -right-2 w-12 h-12 text-zhizi/[0.06]" />
      </GlassCard>

      <!-- 本周互动 -->
      <!-- 可访问性：统计卡片支持键盘激活 -->
      <GlassCard
        hoverable
        class="stat-card relative overflow-hidden group cursor-pointer"
        role="button"
        tabindex="0"
        aria-label="进入课堂讨论"
        @click="navigateTo('discussion')"
        @keydown.enter.prevent="navigateTo('discussion')"
        @keydown.space.prevent="navigateTo('discussion')"
      >
        <div class="flex items-center justify-between mb-4 relative z-10">
          <span class="text-sm font-medium text-shuimo/60">本周互动</span>
          <div class="p-2.5 rounded-xl bg-gradient-to-br from-zijinghui/15 to-zijinghui/5 text-zijinghui shadow-sm group-hover:shadow-md group-hover:scale-110 transition-all duration-300">
            <Activity class="w-5 h-5" />
          </div>
        </div>
        <div class="text-4xl font-bold font-mono relative z-10 bg-gradient-to-r from-zijinghui to-qinghua bg-clip-text text-transparent group-hover:scale-105 transition-transform duration-300 origin-left">
          <AnimatedNumber :value="stats.weeklyInteractions" />
        </div>
        <p
          class="text-xs mt-3 relative z-10 font-medium flex items-center gap-1"
          :class="[
            weeklyTrendDirection >= 0 ? 'text-tianlv' : 'text-yanzhi'
          ]"
        >
          <TrendingUp class="w-3 h-3" :class="weeklyTrendDirection < 0 ? 'rotate-180' : ''" />
          <AnimatedNumber :value="weeklyTrendValue" suffix="%" :show-trend="true" />
          较上周
        </p>
        <Activity class="stat-card-icon absolute -bottom-2 -right-2 w-12 h-12 text-zijinghui/[0.06]" />
      </GlassCard>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-4 gap-6">
      <!-- 待办事项 -->
      <GlassCard class="flex flex-col h-full">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-lg font-bold text-shuimo flex items-center gap-2 font-song">
            <AlertCircle class="w-5 h-5 text-yanzhi" />
            待办事项
          </h3>
          <span v-if="allUrgentItems.length" class="text-xs bg-yanzhi/10 text-yanzhi px-2 py-0.5 rounded-full font-medium">{{ allUrgentItems.length }}</span>
        </div>
        
        <div v-if="allUrgentItems.length === 0" class="flex-1 flex items-center justify-center min-h-[200px]">
          <EmptyState icon="check" title="暂无待办事项" description="喝杯茶休息一下吧" size="sm" />
        </div>
        
        <div v-else class="space-y-3 max-h-[300px] overflow-y-auto">
          <div v-for="todo in allUrgentItems" :key="todo.id" 
               class="flex items-start gap-3 p-3 rounded-xl hover:bg-slate-50 transition-colors cursor-pointer group border border-transparent hover:border-slate-100">
            <div :class="['w-2 h-2 rounded-full mt-1.5', todo.urgent ? 'bg-yanzhi shadow-[0_0_8px_rgba(255,0,0,0.3)]' : 'bg-zhizi']"></div>
            <div class="flex-1 min-w-0">
              <p class="text-sm text-shuimo group-hover:text-qinghua transition-colors font-medium">{{ todo.title }}</p>
              <p v-if="todo.courseName" class="text-xs text-shuimo/40 mt-0.5">{{ todo.courseName }}</p>
              <p v-if="todo.time" class="text-xs text-shuimo/40 mt-0.5">
                <Clock class="w-3 h-3 inline mr-1" />{{ todo.time }}
              </p>
            </div>
            <span v-if="todo.count" class="text-xs bg-yanzhi/10 text-yanzhi px-2 py-0.5 rounded-full font-bold">{{ todo.count }}</span>
          </div>
        </div>
      </GlassCard>

      <!-- 学生预警面板 -->
      <AlertPanel 
        ref="alertPanelRef"
        :maxItems="5"
        @viewStudent="handleViewAlertStudent"
        @viewAll="handleViewAllAlerts"
      />

      <!-- 课程完成率排名 -->
      <GlassCard class="flex flex-col h-full">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-lg font-bold text-shuimo flex items-center gap-2 font-song">
            <TrendingUp class="w-5 h-5 text-tianlv" />
            课程完成率
          </h3>
        </div>
        
        <div v-if="courseRankings.length === 0" class="flex-1 flex items-center justify-center min-h-[200px]">
          <EmptyState icon="chart" title="暂无活跃课程数据" size="sm" />
        </div>

        <div v-else class="space-y-5 max-h-[300px] overflow-y-auto">
          <div v-for="(course, index) in courseRankings" :key="course.courseId || course.name">
            <div class="flex justify-between text-sm mb-1.5">
              <span class="text-shuimo font-medium flex items-center gap-2">
                <span :class="['w-5 h-5 rounded-full flex items-center justify-center text-xs font-bold',
                  index === 0 ? 'bg-zhizi/20 text-zhizi' :
                  index === 1 ? 'bg-slate-200 text-slate-600' :
                  index === 2 ? 'bg-amber-100 text-amber-700' : 'bg-slate-100 text-slate-500']">
                  {{ index + 1 }}
                </span>
                {{ course.title || course.name }}
              </span>
              <span class="text-shuimo/60 font-mono">{{ Math.round(course.completionRate || course.progress || 0) }}%</span>
            </div>
            <div class="h-2 bg-slate-100 rounded-full overflow-hidden">
              <div class="h-full bg-gradient-to-r from-tianlv to-qingsong rounded-full transition-all duration-1000 ease-out" 
                   :style="{ width: (course.completionRate || course.progress || 0) + '%' }"></div>
            </div>
            <div v-if="course.studentCount" class="text-xs text-shuimo/40 mt-1">
              {{ course.studentCount }} 名学生
            </div>
          </div>
        </div>
      </GlassCard>

      <!-- 最近活动 -->
      <GlassCard class="flex flex-col h-full">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-lg font-bold text-shuimo flex items-center gap-2 font-song">
            <Activity class="w-5 h-5 text-qinghua" />
            最近活动
          </h3>
          <span class="text-[10px] bg-qinghua/10 text-qinghua px-2 py-0.5 rounded-full font-bold uppercase tracking-wider">Live</span>
        </div>
        
        <div v-if="activities.length === 0" class="flex-1 flex items-center justify-center min-h-[200px]">
          <EmptyState icon="chart" title="暂无活动记录" size="sm" />
        </div>
        
        <div v-else class="space-y-4 relative">
          <!-- 时间线背景 -->
          <div class="absolute left-4 top-2 bottom-2 w-0.5 bg-slate-100"></div>
          
          <div v-for="activity in activities" :key="activity.id" 
               class="flex items-start gap-4 relative pl-2 group">
             <!-- 时间线节点 -->
             <div :class="['relative z-10 w-4 h-4 rounded-full border-2 border-white shadow-sm mt-1 flex-shrink-0',
               activity.type === 'submit' ? 'bg-tianlv' :
               activity.type === 'enroll' ? 'bg-qinghua' : 'bg-zhizi']"></div>
             
             <div class="flex-1 bg-slate-50/50 rounded-xl p-3 border border-transparent group-hover:border-slate-200 group-hover:bg-white transition-all">
               <p class="text-sm text-shuimo leading-relaxed">{{ activity.content }}</p>
               <span class="text-xs text-shuimo/40 mt-1 block font-mono">{{ activity.time }}</span>
             </div>
          </div>
        </div>
      </GlassCard>
    </div>

    <!-- 快捷操作 -->
    <GlassCard>
      <h3 class="text-lg font-bold text-shuimo mb-4 font-song">快捷操作</h3>
      <div class="grid grid-cols-2 md:grid-cols-5 gap-4">
        <button @click="navigateTo('courses', { action: 'create' })"
                class="quick-action-btn group flex items-center justify-center gap-3 p-4 rounded-xl bg-tianlv/5 border border-tianlv/10 hover:bg-tianlv/10 hover:border-tianlv/20 hover:shadow-lg hover:shadow-tianlv/10 hover:-translate-y-1 transition-all duration-300 relative overflow-hidden">
          <div class="p-2 rounded-full bg-white text-tianlv shadow-sm group-hover:scale-110 group-hover:shadow-md transition-all duration-300">
             <Plus class="w-5 h-5 icon-bounce" />
          </div>
          <span class="font-bold text-tianlv">创建课程</span>
          <div class="ripple-overlay"></div>
        </button>

        <button @click="navigateTo('homework', { action: 'create' })"
                class="quick-action-btn group flex items-center justify-center gap-3 p-4 rounded-xl bg-qinghua/5 border border-qinghua/10 hover:bg-qinghua/10 hover:border-qinghua/20 hover:shadow-lg hover:shadow-qinghua/10 hover:-translate-y-1 transition-all duration-300 relative overflow-hidden">
          <div class="p-2 rounded-full bg-white text-qinghua shadow-sm group-hover:scale-110 group-hover:shadow-md transition-all duration-300">
             <FileText class="w-5 h-5 icon-bounce" />
          </div>
          <span class="font-bold text-qinghua">布置作业</span>
          <div class="ripple-overlay"></div>
        </button>

        <button @click="openAnnouncementEditor"
                class="quick-action-btn group flex items-center justify-center gap-3 p-4 rounded-xl bg-yanzhi/5 border border-yanzhi/10 hover:bg-yanzhi/10 hover:border-yanzhi/20 hover:shadow-lg hover:shadow-yanzhi/10 hover:-translate-y-1 transition-all duration-300 relative overflow-hidden">
          <div class="p-2 rounded-full bg-white text-yanzhi shadow-sm group-hover:scale-110 group-hover:shadow-md transition-all duration-300">
             <Megaphone class="w-5 h-5 icon-bounce" />
          </div>
          <span class="font-bold text-yanzhi">发布公告</span>
          <div class="ripple-overlay"></div>
        </button>

        <button @click="navigateTo('students')"
                class="quick-action-btn group flex items-center justify-center gap-3 p-4 rounded-xl bg-zhizi/5 border border-zhizi/10 hover:bg-zhizi/10 hover:border-zhizi/20 hover:shadow-lg hover:shadow-zhizi/10 hover:-translate-y-1 transition-all duration-300 relative overflow-hidden">
          <div class="p-2 rounded-full bg-white text-zhizi shadow-sm group-hover:scale-110 group-hover:shadow-md transition-all duration-300">
             <Users class="w-5 h-5 icon-bounce" />
          </div>
          <span class="font-bold text-zhizi">查看学生</span>
          <div class="ripple-overlay"></div>
        </button>

        <button @click="navigateTo('homework', { tab: 'pending' })"
                class="quick-action-btn group flex items-center justify-center gap-3 p-4 rounded-xl bg-zijinghui/5 border border-zijinghui/10 hover:bg-zijinghui/10 hover:border-zijinghui/20 hover:shadow-lg hover:shadow-zijinghui/10 hover:-translate-y-1 transition-all duration-300 relative overflow-hidden">
          <div class="p-2 rounded-full bg-white text-zijinghui shadow-sm group-hover:scale-110 group-hover:shadow-md transition-all duration-300">
             <ClipboardCheck class="w-5 h-5 icon-bounce" />
          </div>
          <span class="font-bold text-zijinghui">批改作业</span>
          <div class="ripple-overlay"></div>
        </button>
      </div>
    </GlassCard>
    
    <!-- 公告编辑器 -->
    <AnnouncementEditor 
      :visible="showAnnouncementEditor"
      @close="showAnnouncementEditor = false"
      @success="onAnnouncementSuccess"
    />
  </div>
</template>

<style scoped>
/* 统计卡片悬停效果 */
.stat-card {
  /* P1：卡片仅过渡位移与阴影 */
  transition:
    transform var(--motion-duration-medium) var(--motion-ease-standard),
    box-shadow var(--motion-duration-medium) var(--motion-ease-standard);
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 20px 40px -12px rgba(0, 0, 0, 0.1);
}

/* 统计卡片装饰图标动画 */
.stat-card-icon {
  transition:
    transform var(--motion-duration-slow) var(--motion-ease-standard),
    opacity var(--motion-duration-slow) var(--motion-ease-standard);
}

.stat-card:hover .stat-card-icon {
  transform: scale(1.2) rotate(5deg);
  opacity: 0.12;
}

/* 快捷操作按钮图标弹跳 */
.icon-bounce {
  /* P1 第二批：教师看板交互动效压缩到 200ms 档 */
  transition: transform var(--motion-duration-medium) var(--motion-ease-standard);
}

.group:hover .icon-bounce {
  animation: icon-bounce var(--motion-duration-medium) var(--motion-ease-standard);
}

@keyframes icon-bounce {
  0%, 100% {
    transform: translateY(0);
  }
  40% {
    transform: translateY(-6px);
  }
  60% {
    transform: translateY(-3px);
  }
}

/* 点击涟漪效果 */
.quick-action-btn {
  position: relative;
  overflow: hidden;
}

.quick-action-btn::after {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  width: 100%;
  height: 100%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.4) 0%, transparent 70%);
  border-radius: 50%;
  transform: translate(-50%, -50%) scale(0);
  opacity: 0;
  pointer-events: none;
}

.quick-action-btn:active::after {
  /* 动效优化：点击涟漪改为 transform 缩放，避免 width/height 动画引发布局计算 */
  transform: translate(-50%, -50%) scale(2);
  opacity: 1;
  transition:
    transform var(--motion-duration-medium) var(--motion-ease-standard),
    opacity var(--motion-duration-medium) var(--motion-ease-standard);
}
</style>
