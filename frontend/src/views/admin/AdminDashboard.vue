<script setup>
/**
 * 管理员仪表盘组件
 * 展示系统概况、数据趋势图表、待办事项和最近活动。
 * 集成骨架屏加载、数据可视化图表和 stagger 入场动画。
 */
import { ref, onMounted } from 'vue'
import {
  TrendingUp, UserPlus, LogIn, Users, BookOpen,
  Bell, Eye, Ban, ChevronRight, RefreshCw
} from 'lucide-vue-next'
import GlassCard from '../../components/ui/GlassCard.vue'
import AnimatedNumber from '../../components/ui/AnimatedNumber.vue'
import SkeletonDashboard from '../../components/ui/SkeletonDashboard.vue'
import EmptyState from '../../components/ui/EmptyState.vue'
import UserTrendChart from '../../components/charts/UserTrendChart.vue'
import CourseDistributionChart from '../../components/charts/CourseDistributionChart.vue'
import OnlineUsersModal from '../../components/admin/OnlineUsersModal.vue'
import { statsAPI, courseAPI } from '../../services/api'

defineProps({
  stats: {
    type: Object,
    default: () => ({ total: 0, students: 0, teachers: 0, admins: 0 })
  },
  courseStats: {
    type: Object,
    default: () => ({ total: 0, pending: 0, published: 0, offline: 0 })
  },
  todayStats: {
    type: Object,
    default: () => ({ newUsers: 0, newStudents: 0, newTeachers: 0, activeUsers: 0, onlineUsers: 0 })
  },
  recentUsers: {
    type: Array,
    default: () => []
  },
  disabledUsersCount: {
    type: Number,
    default: 0
  },
  lastUpdateTime: {
    type: String,
    default: ''
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['navigate', 'refresh'])

// 图表数据状态
const userTrendData = ref({
  labels: [],
  newUsers: [],
  activeUsers: [],
  onlineUsers: []
})
const courseDistributionData = ref({
  subjects: [],
  courseCounts: [],
  studentCounts: []
})
const chartsLoading = ref(true)
const trendDays = ref(7)

// 在线用户弹窗状态
const showOnlineUsersModal = ref(false)

// 加载图表数据
const loadChartData = async () => {
  chartsLoading.value = true
  try {
    const [trendRes, distributionRes] = await Promise.all([
      statsAPI.getUserTrends(trendDays.value),
      courseAPI.getStatsBySubject()
    ])

    if (trendRes?.data) {
      userTrendData.value = trendRes.data
    }
    if (distributionRes?.data) {
      courseDistributionData.value = distributionRes.data
    }
  } catch (e) {
    console.error('加载图表数据失败:', e)
  } finally {
    chartsLoading.value = false
  }
}

// 处理时间范围变化
const handleTrendRangeChange = async (days) => {
  trendDays.value = days
  chartsLoading.value = true
  try {
    const res = await statsAPI.getUserTrends(days)
    if (res?.data) {
      userTrendData.value = res.data
    }
  } catch (e) {
    console.error('加载趋势数据失败:', e)
  } finally {
    chartsLoading.value = false
  }
}

// 点击学科跳转
const handleSubjectClick = (subject) => {
  emit('navigate', { menu: 'courses', filter: subject })
}

// 刷新数据
const handleRefresh = () => {
  loadChartData()
  emit('refresh')
}

onMounted(() => {
  loadChartData()
})

// 格式化相对时间
const formatTimeAgo = (dateStr) => {
  if (!dateStr) return '未知时间'
  const date = new Date(dateStr)
  const now = new Date()
  const diff = Math.floor((now - date) / 1000)
  if (diff < 60) return '刚刚'
  if (diff < 3600) return Math.floor(diff / 60) + '分钟前'
  if (diff < 86400) return Math.floor(diff / 3600) + '小时前'
  return Math.floor(diff / 86400) + '天前'
}

// 推断用户活动类型
const getUserActivityType = (user) => {
  if (!user.createdAt) return '登录了系统'
  const createdAt = new Date(user.createdAt).getTime()
  const lastLoginAt = user.lastLoginAt ? new Date(user.lastLoginAt).getTime() : null

  if (!lastLoginAt || Math.abs(lastLoginAt - createdAt) < 5 * 60 * 1000) {
    return '新注册'
  }
  return '登录了系统'
}

const getActivityColor = (type) => {
  return type === '新注册' ? 'text-blue-500' : 'text-emerald-500'
}

const getRoleBadgeClass = (role) => {
  switch (role) {
    case 'admin': return 'bg-purple-100 text-purple-600 dark:bg-purple-900/30 dark:text-purple-400'
    case 'teacher': return 'bg-emerald-100 text-emerald-600 dark:bg-emerald-900/30 dark:text-emerald-400'
    case 'student': return 'bg-blue-100 text-blue-600 dark:bg-blue-900/30 dark:text-blue-400'
    default: return 'bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-400'
  }
}

const getRoleName = (role) => {
  const map = { admin: '管理员', teacher: '教师', student: '学生' }
  return map[role] || role
}
</script>

<template>
  <!-- 骨架屏加载状态 -->
  <SkeletonDashboard v-if="loading" :stats-count="4" :show-charts="true" />

  <!-- 实际内容 -->
  <div v-else class="space-y-6">
    <!-- 第一行：今日概况与待办 -->
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 stagger-list">

      <!-- 卡片：今日统计 -->
      <GlassCard class="p-6 dark:bg-slate-800/60 dark:border-slate-700">
        <div class="flex items-center gap-2 mb-5">
          <div class="w-8 h-8 rounded-lg bg-gradient-to-br from-amber-400 to-orange-500 flex items-center justify-center shadow-md shadow-orange-500/20">
            <TrendingUp class="w-4 h-4 text-white" />
          </div>
          <h3 class="text-lg font-bold text-shuimo dark:text-slate-200 font-song">今日概况</h3>
          <div class="flex items-center gap-2 ml-auto">
            <button
              @click="handleRefresh"
              class="p-1.5 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-700 text-shuimo/50 dark:text-slate-400 transition-colors"
              title="刷新数据"
            >
              <RefreshCw class="w-4 h-4" />
            </button>
            <span class="flex items-center gap-1 text-xs px-2 py-1 rounded-full bg-emerald-100 text-emerald-600 dark:bg-emerald-900/30 dark:text-emerald-400">
              <span class="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
              实时
            </span>
            <span class="text-xs text-shuimo/50 dark:text-slate-500">{{ lastUpdateTime }}</span>
          </div>
        </div>

        <div class="grid grid-cols-2 md:grid-cols-4 gap-4 stagger-fast">
          <div class="stagger-item text-center p-4 rounded-xl bg-blue-50 dark:bg-blue-900/20 hover:bg-blue-100 dark:hover:bg-blue-900/30 hover:shadow-lg hover:shadow-blue-100 dark:hover:shadow-blue-900/20 hover:-translate-y-0.5 transition-all duration-300 cursor-default">
            <UserPlus class="w-6 h-6 text-blue-500 mx-auto mb-2" />
            <div class="text-2xl font-bold text-blue-600 dark:text-blue-400 font-mono">
              <AnimatedNumber :value="todayStats.newUsers" :duration="800" :show-trend="true" />
            </div>
            <div class="text-xs text-blue-600/70 dark:text-blue-400/70 font-medium">新增用户</div>
          </div>
          <div class="stagger-item text-center p-4 rounded-xl bg-emerald-50 dark:bg-emerald-900/20 hover:bg-emerald-100 dark:hover:bg-emerald-900/30 hover:shadow-lg hover:shadow-emerald-100 dark:hover:shadow-emerald-900/20 hover:-translate-y-0.5 transition-all duration-300 cursor-default">
            <LogIn class="w-6 h-6 text-emerald-500 mx-auto mb-2" />
            <div class="text-2xl font-bold text-emerald-600 dark:text-emerald-400 font-mono">
              <AnimatedNumber :value="todayStats.activeUsers" :duration="800" :show-trend="true" />
            </div>
            <div class="text-xs text-emerald-600/70 dark:text-emerald-400/70 font-medium">今日活跃</div>
          </div>
          <button
            type="button"
            class="stagger-item text-center p-4 rounded-xl bg-green-50 dark:bg-green-900/20 hover:bg-green-100 dark:hover:bg-green-900/30 hover:shadow-lg hover:shadow-green-100 dark:hover:shadow-green-900/20 hover:-translate-y-0.5 transition-all duration-300 cursor-pointer w-full"
            @click="showOnlineUsersModal = true"
            title="点击查看在线用户详情"
            aria-label="查看在线用户详情"
          >
            <Users class="w-6 h-6 text-green-500 mx-auto mb-2" />
            <div class="text-2xl font-bold text-green-600 dark:text-green-400 font-mono">
              <AnimatedNumber :value="todayStats.onlineUsers || 0" :duration="800" :show-trend="true" />
            </div>
            <div class="text-xs text-green-600/70 dark:text-green-400/70 font-medium">当前在线</div>
          </button>
          <div class="stagger-item text-center p-4 rounded-xl bg-amber-50 dark:bg-amber-900/20 hover:bg-amber-100 dark:hover:bg-amber-900/30 hover:shadow-lg hover:shadow-amber-100 dark:hover:shadow-amber-900/20 hover:-translate-y-0.5 transition-all duration-300 cursor-default">
            <BookOpen class="w-6 h-6 text-amber-500 mx-auto mb-2" />
            <div class="text-2xl font-bold text-amber-600 dark:text-amber-400 font-mono">
              <AnimatedNumber :value="courseStats.published" :duration="800" />
            </div>
            <div class="text-xs text-amber-600/70 dark:text-amber-400/70 font-medium">已发布课程</div>
          </div>
        </div>
      </GlassCard>

      <!-- 卡片：待办事项 -->
      <GlassCard class="p-6 dark:bg-slate-800/60 dark:border-slate-700">
        <div class="flex items-center gap-2 mb-5">
          <div class="w-8 h-8 rounded-lg bg-gradient-to-br from-rose-400 to-rose-500 flex items-center justify-center shadow-md shadow-rose-500/20">
            <Bell class="w-4 h-4 text-white" />
          </div>
          <h3 class="text-lg font-bold text-shuimo dark:text-slate-200 font-song">待办事项</h3>
        </div>

        <div class="space-y-3">
          <!-- 待审核课程 -->
          <button
            v-if="courseStats.pending > 0"
            type="button"
            @click="emit('navigate', { menu: 'courses', filter: '0' })"
            class="stagger-item flex items-center gap-4 p-4 rounded-xl bg-amber-50 dark:bg-amber-900/20 border border-amber-100 dark:border-amber-800/30 cursor-pointer hover:bg-amber-100 dark:hover:bg-amber-900/30 transition-all group text-left w-full"
            aria-label="查看待审核课程详情"
          >
            <div class="w-12 h-12 rounded-xl bg-amber-100 dark:bg-amber-800/30 flex items-center justify-center group-hover:scale-110 transition-transform">
              <Eye class="w-6 h-6 text-amber-600 dark:text-amber-400" />
            </div>
            <div class="flex-1">
              <p class="font-bold text-amber-700 dark:text-amber-300 text-sm md:text-base">{{ courseStats.pending }} 门课程待审核</p>
              <p class="text-xs md:text-sm text-amber-600/70 dark:text-amber-400/60 mt-0.5">点击立即处理</p>
            </div>
            <ChevronRight class="w-5 h-5 text-amber-500 dark:text-amber-400 group-hover:translate-x-1 transition-transform" />
          </button>

          <!-- 已禁用用户 -->
          <button
            v-if="disabledUsersCount > 0"
            type="button"
            @click="emit('navigate', { menu: 'users', filter: 'disabled' })"
            class="stagger-item flex items-center gap-4 p-4 rounded-xl bg-slate-50 dark:bg-slate-700/30 border border-slate-100 dark:border-slate-600/30 cursor-pointer hover:bg-slate-100 dark:hover:bg-slate-700/50 transition-all group text-left w-full"
            aria-label="查看已禁用用户详情"
          >
            <div class="w-12 h-12 rounded-xl bg-slate-100 dark:bg-slate-600/30 flex items-center justify-center group-hover:scale-110 transition-transform">
              <Ban class="w-6 h-6 text-slate-500 dark:text-slate-400" />
            </div>
            <div class="flex-1">
              <p class="font-bold text-slate-700 dark:text-slate-300 text-sm md:text-base">{{ disabledUsersCount }} 个用户已禁用</p>
              <p class="text-xs md:text-sm text-slate-500 dark:text-slate-400 mt-0.5">点击查看详情</p>
            </div>
            <ChevronRight class="w-5 h-5 text-slate-400 group-hover:translate-x-1 transition-transform" />
          </button>

          <!-- 空状态 -->
          <EmptyState
            v-if="courseStats.pending === 0 && disabledUsersCount === 0"
            type="success"
            title="暂无待办事项"
            description="系统运行正常，请继续保持"
            size="sm"
          />
        </div>
      </GlassCard>
    </div>

    <!-- 第二行：数据图表 -->
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 stagger-list">
      <!-- 用户增长趋势图 -->
      <GlassCard class="p-6 dark:bg-slate-800/60 dark:border-slate-700 stagger-item">
        <UserTrendChart
          :data="userTrendData"
          :loading="chartsLoading"
          @change-range="handleTrendRangeChange"
        />
      </GlassCard>

      <!-- 课程分布饼图 -->
      <GlassCard class="p-6 dark:bg-slate-800/60 dark:border-slate-700 stagger-item">
        <CourseDistributionChart
          :data="courseDistributionData"
          :loading="chartsLoading"
          @click-subject="handleSubjectClick"
        />
      </GlassCard>
    </div>

    <!-- 第三行：最近活动 -->
    <GlassCard class="p-6 dark:bg-slate-800/60 dark:border-slate-700">
      <div class="flex items-center justify-between mb-6">
        <h3 class="text-lg font-bold text-shuimo dark:text-slate-200 font-song">最近活动</h3>
        <button @click="emit('navigate', { menu: 'users' })" class="text-sm text-qinghua hover:text-danqing dark:text-blue-400 dark:hover:text-blue-300 transition-colors flex items-center gap-1 font-medium">
          全部用户 <ChevronRight class="w-4 h-4" />
        </button>
      </div>

      <!-- 空状态 -->
      <EmptyState
        v-if="recentUsers.length === 0"
        type="empty"
        icon="users"
        title="暂无活动记录"
        description="用户活动将在此显示"
        size="sm"
      />

      <!-- 活动列表 -->
      <div v-else class="space-y-4 stagger-list">
        <div v-for="(user, index) in recentUsers.slice(0, 5)" :key="user.id || index"
          class="stagger-item flex items-center gap-4 p-3 rounded-xl hover:bg-slate-50 dark:hover:bg-slate-700/30 transition-colors border border-transparent hover:border-slate-100 dark:hover:border-slate-600/30"
        >
          <div class="w-10 h-10 rounded-full flex items-center justify-center text-white text-sm font-bold shadow-sm"
            :class="user.role === 'admin' ? 'bg-gradient-to-br from-zijinghui to-qianniuzi' : 'bg-gradient-to-br from-tianlv to-qingsong'"
          >
            {{ user.username?.charAt(0).toUpperCase() }}
          </div>

          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2 mb-1">
              <span class="font-bold text-shuimo dark:text-slate-200 truncate">{{ user.name || user.username }}</span>
              <span class="px-2 py-0.5 rounded text-[10px] font-bold" :class="getRoleBadgeClass(user.role)">
                {{ getRoleName(user.role) }}
              </span>
            </div>
            <p class="text-xs text-shuimo/50 dark:text-slate-400 flex items-center gap-2">
              <span :class="getActivityColor(getUserActivityType(user))">{{ getUserActivityType(user) }}</span>
              <span>·</span>
              <span>{{ user.email || '无邮箱' }}</span>
            </p>
          </div>

          <div class="text-right text-xs text-shuimo/40 dark:text-slate-500 whitespace-nowrap font-medium">
            {{ formatTimeAgo(user.lastLoginAt || user.createdAt) }}
          </div>
        </div>
      </div>
    </GlassCard>

    <!-- 在线用户监控弹窗 -->
    <OnlineUsersModal v-model="showOnlineUsersModal" />
  </div>
</template>
