<script setup>
import { computed, ref } from 'vue'
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

const props = defineProps({
  loading: {
    type: Boolean,
    default: false
  },
  stats: {
    type: Object,
    required: true,
    default: () => ({
      enrolledCourses: 0,
      completedChapters: 0,
      totalStudyHours: 0,
      todayStudyMinutes: 0,
      pendingHomework: 0,
      streakDays: 0,
      dailyGoalMinutes: 60,
      goalAchievedToday: false
    })
  },
  urgentHomeworks: {
    type: Array,
    default: () => []
  },
  todayTasks: {
    type: Array,
    default: () => []
  },
  recentCourses: {
    type: Array,
    default: () => []
  },
  badges: {
    type: Array,
    default: () => []
  },
  activities: {
    type: Array,
    default: () => []
  },
  // 新增：用户名（用于个性化问候）
  username: {
    type: String,
    default: '同学'
  }
})

const emit = defineEmits(['navigate', 'start-study'])

// 当前鼠标悬浮的徽章ID：用于确保同一时刻只展示一个提示框
const hoveredBadgeId = ref(null)

/**
 * 根据当前时间段返回问候语和图标
 * - 5:00 - 11:59 早上好
 * - 12:00 - 17:59 下午好
 * - 18:00 - 4:59 晚上好
 */
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

// 显示用户名（优先使用传入的，否则使用默认值）
const displayName = computed(() => props.username || '同学')
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
      @navigate="emit('navigate', $event)"
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
        @click="emit('navigate', 'courses')"
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
        @click="emit('navigate', 'homework')"
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
        <p class="text-xs text-tianlv mt-2 flex items-center gap-1" v-else>
          <CheckCircle class="w-3 h-3" aria-hidden="true" />
          太棒了！
        </p>
      </GlassCard>

      <GlassCard 
        class="p-6 card-hover-lift animate-slide-up group"
        style="animation-delay: 0.3s; animation-fill-mode: both;"
      >
        <div class="flex items-center justify-between mb-3">
          <span class="text-sm font-medium text-muted">连续学习</span>
          <Flame class="w-5 h-5 text-yanzhi icon-hover-scale" :class="{ 'animate-pulse': stats.streakDays >= 7 }" aria-hidden="true" />
        </div>
        <div class="text-3xl font-bold bg-gradient-to-r from-yanzhi to-tanxiang bg-clip-text text-transparent number-pop">
          {{ stats.streakDays }}天
        </div>
        <p class="text-xs text-qingsong mt-2">继续保持！</p>
      </GlassCard>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <!-- Today Tasks -->
      <GlassCard class="p-6 h-full flex flex-col card-hover-glow animate-slide-up" style="animation-delay: 0.4s; animation-fill-mode: both;">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-lg font-bold text-shuimo flex items-center gap-2 font-song">
            <Target class="w-5 h-5 text-qinghua icon-hover-rotate" aria-hidden="true" />
            今日任务
          </h3>
          <span class="text-xs bg-qinghua/20 text-qinghua px-2 py-1 rounded-full font-bold" :class="{ 'animate-pulse': todayTasks.length > 0 }">
            {{ todayTasks.length }} 项
          </span>
        </div>
        <div class="space-y-3 flex-1 overflow-y-auto max-h-[300px] custom-scrollbar">
          <div 
            v-for="(task, index) in todayTasks" 
            :key="task.id" 
            class="p-3 rounded-xl transition-all cursor-pointer border border-transparent hover:border-slate-200 stagger-item btn-ripple"
            :class="task.urgent ? 'bg-yanzhi/5 hover:bg-yanzhi/10' : 'bg-slate-50 hover:bg-slate-100'"
            :style="{ animationDelay: `${index * 0.1}s` }"
            @click="emit('navigate', 'homework')"
          >
            <div class="flex items-center justify-between">
              <span class="text-sm text-shuimo font-medium line-clamp-1" :title="task.title">{{ task.title }}</span>
              <span v-if="task.urgent" class="text-xs text-yanzhi font-bold whitespace-nowrap ml-2 animate-pulse">紧急</span>
            </div>
            <p class="text-xs text-muted mt-1">{{ task.deadline }}</p>
          </div>
          <div v-if="todayTasks.length === 0" class="flex flex-col items-center justify-center h-32 text-muted">
            <div class="w-16 h-16 rounded-full bg-tianlv/10 flex items-center justify-center mb-3 empty-state-float">
              <CheckCircle class="w-8 h-8 text-tianlv" aria-hidden="true" />
            </div>
            <p class="text-sm font-medium text-tianlv">太棒了！今日无紧急任务</p>
            <p class="text-xs text-muted mt-1">保持这个状态，继续加油~</p>
          </div>
        </div>
      </GlassCard>

      <!-- Continue Learning -->
      <GlassCard class="p-6 h-full flex flex-col card-hover-glow animate-slide-up" style="animation-delay: 0.5s; animation-fill-mode: both;">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-lg font-bold text-shuimo flex items-center gap-2 font-song">
            <Play class="w-5 h-5 text-tianlv icon-hover-scale" />
            继续学习
          </h3>
        </div>
        <div class="space-y-3 flex-1 overflow-y-auto max-h-[300px] custom-scrollbar">
          <div 
            v-for="(course, index) in recentCourses" 
            :key="course.id" 
            class="p-3 rounded-xl bg-slate-50 hover:bg-slate-100 transition-all cursor-pointer group border border-slate-100/50 hover:border-qinghua/20 hover:shadow-md stagger-item"
            :style="{ animationDelay: `${index * 0.1}s` }"
            @click="emit('start-study', course.id)"
          >
            <div class="flex items-center justify-between mb-2">
              <span class="text-sm font-medium text-shuimo group-hover:text-qinghua transition-colors line-clamp-1">
                {{ course.name }}
              </span>
              <button 
                @click.stop="emit('start-study', course.id)"
                class="w-7 h-7 rounded-full bg-qinghua/10 flex items-center justify-center text-qinghua opacity-0 group-hover:opacity-100 transition-all hover:bg-qinghua hover:text-white play-btn-pulse"
              >
                <Play class="w-3 h-3 ml-0.5" />
              </button>
            </div>
            <div class="h-1.5 bg-slate-200 rounded-full overflow-hidden mb-1">
              <div 
                class="h-full bg-gradient-to-r from-qinghua to-halanzi rounded-full progress-bar-animated transition-all duration-500" 
                :style="{ width: course.progress + '%' }"
              ></div>
            </div>
            <p class="text-xs text-shuimo/50">{{ course.lastChapter }}</p>
          </div>
          <div v-if="recentCourses.length === 0" class="flex flex-col items-center justify-center h-32 text-shuimo/40">
            <div class="w-16 h-16 rounded-full bg-qinghua/10 flex items-center justify-center mb-3 empty-state-float">
              <Rocket class="w-8 h-8 text-qinghua" />
            </div>
            <p class="text-sm font-medium text-shuimo/60">还没有课程？</p>
            <button
              @click="emit('navigate', 'courses')"
              class="mt-2 px-4 py-1.5 rounded-full bg-gradient-to-r from-qinghua to-halanzi text-white text-xs font-medium hover:shadow-lg hover:shadow-qinghua/30 transition-all"
            >
              去选修第一门课程
            </button>
          </div>
        </div>
      </GlassCard>

      <!-- Learning Badges -->
      <GlassCard class="p-6 h-full flex flex-col card-hover-glow animate-slide-up" style="animation-delay: 0.6s; animation-fill-mode: both;">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-lg font-bold text-shuimo flex items-center gap-2 font-song">
            <Award class="w-5 h-5 text-zhizi icon-hover-rotate" />
            学习徽章
          </h3>
          <span class="text-xs text-shuimo/50">
            {{ badges.filter(b => b.unlocked).length }}/{{ badges.length }} 已解锁
          </span>
        </div>
        <div v-if="badges.length > 0" class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
          <div
            v-for="(badge, index) in badges"
            :key="badge.id"
            class="p-3 rounded-xl text-center transition-all border relative cursor-default stagger-item"
            @mouseenter="hoveredBadgeId = badge.id"
            @mouseleave="hoveredBadgeId = null"
            :class="[
              badge.unlocked
                ? hoveredBadgeId === badge.id
                  ? 'bg-gradient-to-br from-white to-slate-50 border-slate-100 shadow-xl scale-105 badge-unlocked-glow'
                  : 'bg-gradient-to-br from-white to-slate-50 border-slate-100 shadow-md badge-unlocked-glow'
                : badge.nearUnlock
                  ? hoveredBadgeId === badge.id
                    ? 'bg-zhizi/5 border-zhizi/50 badge-near-unlock'
                    : 'bg-zhizi/5 border-zhizi/30 badge-near-unlock'
                  : hoveredBadgeId === badge.id
                    ? 'bg-slate-50 border-transparent badge-locked opacity-80'
                    : 'bg-slate-50 border-transparent badge-locked'
            ]"
            :style="{ animationDelay: `${index * 0.08}s` }"
          >
            <!-- Near Unlock Badge -->
            <div
              v-if="badge.nearUnlock && !badge.unlocked"
              class="absolute -top-1.5 -right-1.5 w-5 h-5 bg-gradient-to-br from-zhizi to-tanxiang rounded-full flex items-center justify-center animate-bounce shadow-lg"
            >
              <Sparkles class="w-3 h-3 text-white" />
            </div>

            <!-- Unlocked Star -->
            <div
              v-if="badge.unlocked"
              class="absolute -top-1.5 -right-1.5 w-5 h-5 bg-gradient-to-br from-tianlv to-qingsong rounded-full flex items-center justify-center shadow-lg"
            >
              <Star class="w-3 h-3 text-white fill-white" />
            </div>

            <!-- Badge Icon -->
            <div
              class="w-12 h-12 mx-auto mb-2 rounded-full flex items-center justify-center transition-all"
              :class="[
                badge.unlocked
                  ? 'bg-gradient-to-br from-zhizi/20 to-tanxiang/20 badge-icon-glow'
                  : badge.nearUnlock
                    ? 'bg-zhizi/10'
                    : 'bg-slate-100'
              ]"
            >
              <component
                :is="badge.icon"
                class="w-6 h-6 transition-all"
                :class="[
                  badge.unlocked ? badge.color : badge.nearUnlock ? 'text-zhizi/70' : 'text-slate-400',
                  badge.unlocked ? 'drop-shadow-lg' : '',
                  hoveredBadgeId === badge.id ? 'scale-110' : ''
                ]"
              />
            </div>
            <p class="text-xs font-medium text-shuimo truncate" :title="badge.name">{{ badge.name }}</p>

            <!-- Progress bar for near unlock badges -->
            <div v-if="badge.nearUnlock && !badge.unlocked && badge.progress" class="mt-1.5">
              <div class="h-1.5 bg-slate-200 rounded-full overflow-hidden">
                <div
                  class="h-full bg-gradient-to-r from-zhizi to-tanxiang rounded-full transition-all progress-bar-animated"
                  :style="{ width: badge.progress + '%' }"
                ></div>
              </div>
              <p class="text-[10px] text-zhizi mt-0.5 font-medium">还差 {{ 100 - badge.progress }}%</p>
            </div>

            <!-- Lock icon for locked badges -->
            <div v-else-if="!badge.unlocked" class="mt-1.5 flex items-center justify-center gap-1">
              <Lock class="w-3 h-3 text-slate-400" />
              <span class="text-[10px] text-slate-400">未解锁</span>
            </div>

            <!-- Checkmark for unlocked badges -->
            <div v-else class="mt-1.5 flex items-center justify-center gap-1">
              <CheckCircle class="w-3 h-3 text-tianlv" />
              <span class="text-[10px] text-tianlv font-medium">已获得</span>
            </div>

            <!-- Tooltip on hover -->
            <div
              v-if="hoveredBadgeId === badge.id"
              class="absolute -top-12 left-1/2 -translate-x-1/2 bg-shuimo text-white text-xs px-3 py-1.5 rounded-lg pointer-events-none whitespace-nowrap z-10 shadow-lg"
            >
              {{ badge.description || badge.name }}
              <span v-if="badge.nearUnlock && !badge.unlocked" class="block text-zhizi font-medium">即将解锁！冲刺吧~</span>
              <span v-if="badge.unlocked && badge.unlockedAt" class="block text-tianlv/80 text-[10px]">解锁于 {{ badge.unlockedAt }}</span>
              <div class="absolute bottom-0 left-1/2 -translate-x-1/2 translate-y-1/2 w-2 h-2 bg-shuimo rotate-45"></div>
            </div>
          </div>
        </div>
        <!-- 徽章空状态 -->
        <div v-else class="flex flex-col items-center justify-center py-8 text-shuimo/40">
          <div class="w-16 h-16 rounded-full bg-zhizi/10 flex items-center justify-center mb-3 empty-state-float">
            <Award class="w-8 h-8 text-zhizi" />
          </div>
          <p class="text-sm font-medium text-shuimo/60">开始学习解锁徽章</p>
          <p class="text-xs text-shuimo/40 mt-1">完成学习任务即可获得专属荣誉</p>
        </div>
      </GlassCard>
    </div>

    <!-- Activity Feed (学习动态) - Replaces redundant Quick Actions -->
    <GlassCard class="p-6 card-hover-glow animate-slide-up" style="animation-delay: 0.7s; animation-fill-mode: both;">
      <div class="flex items-center justify-between mb-4">
        <h3 class="text-lg font-bold text-shuimo flex items-center gap-2 font-song">
          <Bell class="w-5 h-5 text-zijinghui icon-hover-scale" />
          学习动态
        </h3>
        <span v-if="activities.length > 0" class="text-xs text-shuimo/50">最近7天</span>
      </div>
      
      <div v-if="activities.length > 0" class="space-y-3 max-h-[200px] overflow-y-auto custom-scrollbar">
        <div 
          v-for="(activity, index) in activities" 
          :key="index"
          class="flex items-start gap-3 p-3 rounded-xl bg-slate-50 hover:bg-slate-100 transition-all cursor-pointer group hover:shadow-sm stagger-item"
          :style="{ animationDelay: `${index * 0.1}s` }"
          @click="activity.action && emit('navigate', activity.action)"
        >
          <!-- Icon based on type -->
          <div 
            class="w-8 h-8 rounded-lg flex items-center justify-center shrink-0 transition-transform group-hover:scale-110"
            :class="{
              'bg-tianlv/10 text-tianlv': activity.type === 'grade',
              'bg-zijinghui/10 text-zijinghui': activity.type === 'reply',
              'bg-qinghua/10 text-qinghua': activity.type === 'course',
              'bg-zhizi/10 text-zhizi': activity.type === 'homework'
            }"
          >
            <CheckCircle v-if="activity.type === 'grade'" class="w-4 h-4" />
            <MessageSquare v-else-if="activity.type === 'reply'" class="w-4 h-4" />
            <BookOpen v-else-if="activity.type === 'course'" class="w-4 h-4" />
            <FileText v-else class="w-4 h-4" />
          </div>
          
          <div class="flex-1 min-w-0">
            <p class="text-sm font-medium text-shuimo truncate group-hover:text-qinghua transition-colors">
              {{ activity.title }}
            </p>
            <p class="text-xs text-shuimo/50 mt-0.5">{{ activity.time }}</p>
          </div>
          
          <!-- Score badge for grade type -->
          <div 
            v-if="activity.type === 'grade' && activity.score !== undefined" 
            class="text-sm font-bold font-mono number-pop"
            :class="activity.score >= 90 ? 'text-tianlv' : (activity.score >= 60 ? 'text-zhizi' : 'text-yanzhi')"
          >
            {{ activity.score }}分
          </div>
        </div>
      </div>
      
      <!-- Empty State -->
      <div v-else class="text-center py-8 text-shuimo/40">
        <Bell class="w-10 h-10 mx-auto mb-2 opacity-30 empty-state-float" />
        <p class="text-sm">暂无新动态</p>
        <p class="text-xs mt-1">去学习课程获取最新通知吧</p>
      </div>
    </GlassCard>
  </div>
</template>

<style scoped>
.custom-scrollbar::-webkit-scrollbar {
  width: 4px;
}
.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 4px;
}
.custom-scrollbar:hover::-webkit-scrollbar-thumb {
  background: #94a3b8;
}
</style>
