<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  BookOpen, Clock, FileText, Flame,
  Target, Play, Award, Lock,
  MessageSquare, BarChart3, Star, Medal, Bell, CheckCircle, AlertCircle,
  Sun, Sunrise, Moon, Rocket, Sparkles
} from 'lucide-vue-next'
import GlassCard from '../../components/ui/GlassCard.vue'
import DailyGoalProgress from '../../components/student/DailyGoalProgress.vue'
import UrgentHomeworkBanner from '../../components/student/UrgentHomeworkBanner.vue'
import SkeletonDashboard from '../../components/ui/SkeletonDashboard.vue'
import { useAuthStore } from '../../stores/auth'
import { useStudentCourses } from '../../composables/useStudentCourses'
import { useStudentHomeworks } from '../../composables/useStudentHomeworks'
import { useStudentStats } from '../../composables/useStudentStats'

const router = useRouter()
const authStore = useAuthStore()
const emit = defineEmits(['navigate', 'start-study'])

// Composables
const { enrolledCourses, recentCourses, loadEnrolledCourses, loading: coursesLoading } = useStudentCourses()
const { todayTasks, activities, loadHomeworks, loading: homeworkLoading } = useStudentHomeworks()
const { dashboardStats: stats, badges, loadStudentStats, loadBadges, loading: statsLoading } = useStudentStats()

const loading = computed(() => coursesLoading.value || homeworkLoading.value || statsLoading.value)
const urgentHomeworks = computed(() => todayTasks.value.filter(t => t.urgent))
const displayName = computed(() => authStore.user?.username || '同学')

// Load Data
onMounted(async () => {
   const userId = authStore.user?.id
   if (userId) {
      await Promise.all([
         loadEnrolledCourses(userId),
         loadStudentStats(userId),
         loadBadges(userId)
      ])
      if (enrolledCourses.value.length > 0) {
         await loadHomeworks(userId, enrolledCourses.value)
      }
   }
})

// UI Helpers
const hoveredBadgeId = ref(null)
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour >= 5 && hour < 12) {
    return {
      text: '早上好',
      subtext: '新的一天，让我们开始学习吧！',
      icon: Sunrise,
      gradient: 'from-amber-400 to-orange-500'
    }
  } else if (hour >= 12 && hour < 18) {
    return {
      text: '下午好',
      subtext: '保持专注，继续加油！',
      icon: Sun,
      gradient: 'from-qinghua to-halanzi'
    }
  } else {
    return {
      text: '晚上好',
      subtext: '今天辛苦了，记得早点休息哦~',
      icon: Moon,
      gradient: 'from-zijinghui to-qianniuzi'
    }
  }
})
</script>

<template>
  <SkeletonDashboard v-if="loading" :stats-count="4" :show-charts="true" />
  <div v-else class="space-y-6 animate-fade-in">
    <!-- 个性化问候区域 -->
    <div class="flex items-center justify-between animate-slide-up" style="animation-delay: 0s; animation-fill-mode: both;">
      <div class="flex items-center gap-4">
        <div
          class="w-12 h-12 rounded-2xl flex items-center justify-center shadow-lg"
          :class="`bg-gradient-to-br ${greeting.gradient}`"
        >
          <component :is="greeting.icon" class="w-6 h-6 text-white" />
        </div>
        <div>
          <h1 class="text-2xl font-bold text-shuimo font-song">
            {{ greeting.text }}，<span class="bg-gradient-to-r from-qinghua to-halanzi bg-clip-text text-transparent">{{ displayName }}</span>
          </h1>
          <p class="text-sm text-shuimo/60 mt-0.5">{{ greeting.subtext }}</p>
        </div>
      </div>
      <!-- 连续学习徽章（如果连续学习天数 >= 3） -->
      <div
        v-if="stats.streakDays >= 3"
        class="hidden sm:flex items-center gap-2 px-4 py-2 rounded-full bg-gradient-to-r from-yanzhi/10 to-tanxiang/10 border border-yanzhi/20"
      >
        <Flame class="w-5 h-5 text-yanzhi animate-pulse" />
        <span class="text-sm font-medium text-yanzhi">连续学习 {{ stats.streakDays }} 天</span>
        <Sparkles class="w-4 h-4 text-tanxiang" />
      </div>
    </div>

    <!-- 紧急作业提醒横幅 -->
    <UrgentHomeworkBanner 
      :homeworks="urgentHomeworks"
      @navigate="router.push('/student/homeworks')"
    />

    <!-- Stats Grid -->
    <div class="grid grid-cols-1 tablet:grid-cols-2 fold:grid-cols-3 lg:grid-cols-4 gap-6">
      <!-- 今日目标进度卡片 -->
      <GlassCard 
        class="p-6 card-hover-lift animate-slide-up"
        style="animation-delay: 0s; animation-fill-mode: both;"
      >
        <DailyGoalProgress 
          :todayMinutes="stats.todayStudyMinutes"
          :goalMinutes="stats.dailyGoalMinutes || 60"
        />
      </GlassCard>

      <GlassCard 
        class="p-6 card-hover-lift cursor-pointer group animate-slide-up"
        style="animation-delay: 0.1s; animation-fill-mode: both;"
        @click="router.push('/student/courses')"
      >
        <div class="flex items-center justify-between mb-3">
          <span class="text-sm font-medium text-muted">学习课程</span>
          <BookOpen class="w-5 h-5 text-qinghua icon-hover-scale group-hover:text-qinghua/80 transition-colors" aria-hidden="true" />
        </div>
        <div class="text-3xl font-bold bg-gradient-to-r from-qinghua to-halanzi bg-clip-text text-transparent number-pop">
          {{ stats.enrolledCourses }}
        </div>
        <p class="text-xs text-muted mt-2">已完成 {{ stats.completedChapters }} 章节</p>
      </GlassCard>

      <GlassCard 
        class="p-6 card-hover-lift cursor-pointer group animate-slide-up"
        :class="{ 'urgent-pulse': stats.pendingHomework > 2 }"
        style="animation-delay: 0.2s; animation-fill-mode: both;"
        @click="router.push('/student/homeworks')"
      >
        <div class="flex items-center justify-between mb-3">
          <span class="text-sm font-medium text-muted">待完成作业</span>
          <FileText class="w-5 h-5 text-zhizi icon-hover-scale" :class="{ 'animate-bounce': stats.pendingHomework > 0 }" aria-hidden="true" />
        </div>
        <div class="text-3xl font-bold bg-gradient-to-r from-zhizi to-tanxiang bg-clip-text text-transparent number-pop">
          {{ stats.pendingHomework }}
        </div>
        <p class="text-xs text-yanzhi mt-2 flex items-center gap-1" v-if="stats.pendingHomework > 0">
          <span class="w-1.5 h-1.5 rounded-full bg-yanzhi animate-pulse"></span>
          请尽快完成
        </p>
      </GlassCard>

      <GlassCard 
        class="p-6 card-hover-lift animate-slide-up"
        style="animation-delay: 0.3s; animation-fill-mode: both;"
      >
        <div class="flex items-center justify-between mb-3">
          <span class="text-sm font-medium text-muted">累计学习</span>
          <Clock class="w-5 h-5 text-zijinghui icon-hover-scale" aria-hidden="true" />
        </div>
        <div class="text-3xl font-bold bg-gradient-to-r from-zijinghui to-qianniuzi bg-clip-text text-transparent number-pop">
          {{ stats.totalStudyHours }}<span class="text-base font-normal text-muted ml-1">小时</span>
        </div>
        <p class="text-xs text-muted mt-2">
           <span v-if="stats.thisWeekMinutes > stats.lastWeekMinutes" class="text-tianlv flex items-center gap-0.5">
             <Rocket class="w-3 h-3" />
             本周提升 {{ stats.weeklyChange }}%
           </span>
           <span v-else class="text-muted">保持节奏，继续加油</span>
        </p>
      </GlassCard>
    </div>
    
    <!-- Main Content Split -->
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <!-- Left Column: Recent Tasks & Courses -->
      <div class="lg:col-span-2 space-y-6">
        <!-- Today Assignments -->
        <GlassCard class="p-6 animate-slide-up" style="animation-delay: 0.4s; animation-fill-mode: both;">
          <div class="flex items-center justify-between mb-4">
            <h2 class="text-lg font-bold text-shuimo flex items-center gap-2">
              <Target class="w-5 h-5 text-qinghua" />
              今日任务
            </h2>
            <button @click="router.push('/student/homeworks')" class="text-sm text-qinghua hover:text-halanzi hover:underline transition-colors">
              全部作业
            </button>
          </div>
          
          <div v-if="todayTasks.length > 0" class="space-y-3">
             <div 
               v-for="task in todayTasks" 
               :key="task.id"
               class="flex items-center justify-between p-3 rounded-xl border border-slate-100/50 hover:bg-slate-50 transition-colors group cursor-pointer"
               @click="router.push(`/homework/${task.id}`)"
             >
               <div class="flex items-center gap-3">
                 <div class="w-10 h-10 rounded-full bg-qinghua/10 flex items-center justify-center group-hover:bg-qinghua/20 transition-colors">
                   <FileText class="w-5 h-5 text-qinghua" />
                 </div>
                 <div>
                   <p class="text-sm font-medium text-shuimo line-clamp-1 group-hover:text-qinghua transition-colors">{{ task.title }}</p>
                   <p class="text-xs" :class="task.urgent ? 'text-yanzhi' : 'text-muted'">{{ task.deadline }}</p>
                 </div>
               </div>
               <div class="flex items-center" v-if="task.urgent">
                 <span class="px-2 py-0.5 rounded text-xs bg-yanzhi/10 text-yanzhi">紧急</span>
               </div>
             </div>
          </div>
          <div v-else class="flex flex-col items-center justify-center py-8 text-muted">
            <CheckCircle class="w-12 h-12 text-slate-200 mb-2" />
            <p>今日任务已全部完成，真棒！</p>
          </div>
        </GlassCard>

        <!-- Recent Courses -->
        <GlassCard class="p-6 animate-slide-up" style="animation-delay: 0.5s; animation-fill-mode: both;">
          <div class="flex items-center justify-between mb-4">
            <h2 class="text-lg font-bold text-shuimo flex items-center gap-2">
              <Play class="w-5 h-5 text-tianlv" />
              最近学习
            </h2>
            <button @click="router.push('/student/courses')" class="text-sm text-tianlv hover:text-songshi hover:underline transition-colors">
              全部课程
            </button>
          </div>
          
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4" v-if="recentCourses.length > 0">
             <div 
               v-for="course in recentCourses" 
               :key="course.id"
               class="p-4 rounded-xl border border-slate-100/50 hover:shadow-md hover:border-tianlv/30 transition-all cursor-pointer group bg-white/50"
               @click="router.push(`/study/${course.id}`)"
             >
               <div class="flex justify-between items-start mb-2">
                  <span class="text-xs font-bold px-2 py-1 rounded bg-slate-100 text-muted group-hover:bg-tianlv/10 group-hover:text-tianlv transition-colors">
                    {{ course.name }}
                  </span>
                  <Play class="w-4 h-4 text-slate-300 group-hover:text-tianlv transition-colors" />
               </div>
               <h3 class="font-bold text-shuimo mb-1 line-clamp-1 h-6">{{ course.lastChapter }}</h3>
               <div class="w-full bg-slate-100 rounded-full h-1.5 mt-2 overflow-hidden">
                 <div 
                   class="bg-gradient-to-r from-tianlv to-qingsong h-full rounded-full transition-all duration-500" 
                   :style="{ width: `${course.progress}%` }"
                 ></div>
               </div>
               <div class="flex justify-between mt-1">
                 <span class="text-xs text-muted">进度 {{ course.progress }}%</span>
                 <span class="text-xs text-muted">{{ course.lastStudy }}</span>
               </div>
             </div>
          </div>
          <div v-else class="text-center py-8 text-muted">
            <p>还没有开始学习课程，去课程中心看看吧</p>
          </div>
        </GlassCard>
      </div>

      <!-- Right Column: Badges & Activity -->
      <div class="space-y-6">
        <!-- Badges -->
        <GlassCard class="p-6 animate-slide-up" style="animation-delay: 0.6s; animation-fill-mode: both;">
           <div class="flex items-center justify-between mb-4">
            <h2 class="text-lg font-bold text-shuimo flex items-center gap-2">
              <Award class="w-5 h-5 text-yanzhi" />
              我的徽章
            </h2>
            <button @click="router.push('/student/profile')" class="text-sm text-muted hover:text-shuimo transition-colors">
              查看全部
            </button>
          </div>
          
          <div class="flex flex-wrap gap-3">
             <div 
               v-for="badge in badges" 
               :key="badge.id"
               class="relative group"
               @mouseenter="hoveredBadgeId = badge.id"
               @mouseleave="hoveredBadgeId = null"
             >
               <div 
                 class="w-10 h-10 rounded-full flex items-center justify-center transition-all duration-300 transform group-hover:scale-110"
                 :class="badge.unlocked ? 'bg-gradient-to-br from-slate-50 to-white shadow-sm border border-slate-100' : 'bg-slate-100 grayscale opacity-60'"
               >
                 <component :is="badge.icon" class="w-5 h-5" :class="badge.color" />
               </div>
               
               <!-- Tooltip -->
               <div 
                 v-if="hoveredBadgeId === badge.id"
                 class="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 w-32 p-2 bg-shuimo text-white text-xs rounded-lg shadow-xl z-10 pointer-events-none animate-fade-in text-center"
               >
                 <p class="font-bold mb-1">{{ badge.name }}</p>
                 <p class="opacity-80 scale-90">{{ badge.description }}</p>
                 <div class="absolute bottom-0 left-1/2 -translate-x-1/2 translate-y-1 border-4 border-transparent border-t-shuimo"></div>
               </div>
             </div>
          </div>
        </GlassCard>

        <!-- Activity Feed -->
        <GlassCard class="p-6 animate-slide-up" style="animation-delay: 0.7s; animation-fill-mode: both;">
          <div class="flex items-center justify-between mb-4">
            <h2 class="text-lg font-bold text-shuimo flex items-center gap-2">
              <MessageSquare class="w-5 h-5 text-qiuxiang" />
              最新动态
            </h2>
          </div>
          
          <div class="space-y-4">
             <div v-for="(activity, i) in activities" :key="i" class="flex gap-3">
               <div class="relative pt-1">
                 <div class="w-2 h-2 rounded-full bg-qiuxiang ring-4 ring-qiuxiang/10"></div>
                 <div class="absolute top-3 left-1 w-0.5 h-full bg-slate-100 -z-10" v-if="i < activities.length - 1"></div>
               </div>
               <div class="pb-1">
                 <p class="text-sm text-shuimo font-medium">{{ activity.title }}</p>
                 <p class="text-xs text-muted mt-0.5">{{ activity.time }} <span v-if="activity.score" class="text-yanzhi font-bold ml-1">+{{ activity.score }}分</span></p>
               </div>
             </div>
             <div v-if="activities.length === 0" class="text-center py-4 text-muted text-sm">
               暂无最新动态
             </div>
          </div>
        </GlassCard>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Scoped styles reuse default Tailwind classes mostly */
.animate-fade-in {
  animation: fadeIn 0.5s ease-out;
}

.animate-slide-up {
  opacity: 0;
  animation: slideUp 0.5s ease-out forwards;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes slideUp {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

.card-hover-lift {
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}
.card-hover-lift:hover {
  transform: translateY(-5px);
  box-shadow: 0 10px 30px -10px rgba(0, 0, 0, 0.1);
}

.icon-hover-scale {
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.group:hover .icon-hover-scale {
  transform: scale(1.2) rotate(5deg);
}

.urgent-pulse {
  animation: urgentPulse 2s infinite;
}
@keyframes urgentPulse {
  0% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0); }
  70% { box-shadow: 0 0 0 10px rgba(239, 68, 68, 0); }
  100% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0); }
}
</style>
