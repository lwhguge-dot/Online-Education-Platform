<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import {
  Users, TrendingUp, Award, BarChart3, AlertTriangle,
  ChevronDown, RefreshCw, BookOpen, Target, LineChart
} from 'lucide-vue-next'
import { progressAPI, courseAPI } from '../../services/api'
import GlassCard from '../../components/ui/GlassCard.vue'
import BaseButton from '../../components/ui/BaseButton.vue'
import BaseSelect from '../../components/ui/BaseSelect.vue'
import SkeletonTable from '../../components/ui/SkeletonTable.vue'
import EmptyState from '../../components/ui/EmptyState.vue'
import AnimatedNumber from '../../components/ui/AnimatedNumber.vue'
import QuizTrendChart from '../../components/charts/QuizTrendChart.vue'

const props = defineProps({
  courses: { type: Array, default: () => [] }
})

// 状态
const loading = ref(false)
const selectedCourseId = ref(null)
const analyticsData = ref(null)

// 加载课程分析数据
const loadAnalytics = async () => {
  if (!selectedCourseId.value) return
  
  loading.value = true
  try {
    const res = await progressAPI.getCourseAnalytics(selectedCourseId.value)
    if (res.code === 200 && res.data) {
      analyticsData.value = res.data
    }
  } catch (e) {
    console.error('加载课程分析失败', e)
  } finally {
    loading.value = false
  }
}

// 选择课程
const selectCourse = (courseId) => {
  selectedCourseId.value = courseId
  if (courseId) {
    loadAnalytics()
  } else {
    analyticsData.value = null
  }
}

// 刷新数据
const refresh = () => {
  if (selectedCourseId.value) {
    loadAnalytics()
  }
}

// 计算章节完成率图表数据
const chapterChartData = computed(() => {
  if (!analyticsData.value?.chapterAnalytics) return []
  return analyticsData.value.chapterAnalytics.map(c => ({
    title: c.title,
    completionRate: c.completionRate,
    avgQuizScore: c.avgQuizScore,
    dropOffRate: c.dropOffRate
  }))
})

// 高错误率题目（错误率 > 40%）
const highErrorQuestions = computed(() => {
  if (!analyticsData.value?.questionDifficulty) return []
  return analyticsData.value.questionDifficulty.filter(q => q.errorRate > 40)
})

// 与平台对比
const comparisonData = computed(() => {
  if (!analyticsData.value) return null
  const overview = analyticsData.value.overview
  const platform = analyticsData.value.platformComparison
  return {
    progress: {
      course: overview.avgProgress,
      platform: platform.avgProgress,
      diff: overview.avgProgress - platform.avgProgress
    },
    quizScore: {
      course: overview.avgQuizScore,
      platform: platform.avgQuizScore,
      diff: overview.avgQuizScore - platform.avgQuizScore
    },
    completion: {
      course: overview.completionRate,
      platform: platform.completionRate,
      diff: overview.completionRate - platform.completionRate
    }
  }
})

// 课程选项
const courseOptions = computed(() => {
  return [
    { value: null, label: '请选择课程' },
    ...props.courses.map(c => ({ value: c.id, label: c.title }))
  ]
})

// 计算柱状图高度
const getBarHeight = (value, max = 100) => Math.max((value / max) * 100, 2)

onMounted(() => {
  // 如果有课程，默认选择第一个并加载数据
  if (props.courses.length > 0) {
    selectedCourseId.value = props.courses[0].id
    loadAnalytics()
  }
})

// 监听课程列表变化
watch(() => props.courses, (newCourses) => {
  if (newCourses.length > 0 && !selectedCourseId.value) {
    selectedCourseId.value = newCourses[0].id
    loadAnalytics()
  }
}, { immediate: true })
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <!-- 课程选择器 -->
    <GlassCard class="p-4">
      <div class="flex items-center justify-between flex-wrap gap-4">
        <div class="flex items-center gap-4">
          <label class="font-medium text-shuimo">选择课程:</label>
          <div class="min-w-[200px]">
            <BaseSelect 
              v-model="selectedCourseId" 
              :options="courseOptions"
              @change="selectCourse"
            />
          </div>
        </div>
        <BaseButton @click="refresh" variant="secondary" size="sm" :disabled="loading || !selectedCourseId">
          <RefreshCw class="w-4 h-4" :class="{ 'animate-spin': loading }" />
          刷新
        </BaseButton>
      </div>
    </GlassCard>

    <!-- 加载状态 -->
    <div v-if="loading" class="space-y-6">
      <div class="grid grid-cols-5 gap-4">
        <div v-for="i in 5" :key="i" class="h-24 bg-slate-100 rounded-xl animate-pulse"></div>
      </div>
      <SkeletonTable :rows="5" :cols="4" />
    </div>

    <!-- 未选择课程 -->
    <EmptyState 
      v-else-if="!selectedCourseId" 
      icon="chart" 
      title="请选择一门课程" 
      description="选择课程后查看详细的学习分析数据"
    />

    <!-- 分析数据 -->
    <div v-else-if="analyticsData" class="space-y-6">
      <!-- 概览统计 -->
      <div class="grid grid-cols-2 md:grid-cols-5 gap-4">
        <GlassCard class="p-4 text-center relative overflow-hidden group">
          <div class="relative z-10">
            <Users class="w-6 h-6 mx-auto text-qinghua mb-2" />
            <div class="text-2xl font-bold text-shuimo font-mono">
              <AnimatedNumber :value="analyticsData.overview.totalStudents" />
            </div>
            <div class="text-xs text-shuimo/60 mt-1">选课学生</div>
          </div>
          <Users class="absolute -bottom-3 -right-3 w-16 h-16 text-qinghua/8 group-hover:text-qinghua/15 transition-all duration-500" />
        </GlassCard>
        <GlassCard class="p-4 text-center relative overflow-hidden group">
          <div class="relative z-10">
            <TrendingUp class="w-6 h-6 mx-auto text-tianlv mb-2" />
            <div class="text-2xl font-bold text-shuimo font-mono">
              <AnimatedNumber :value="analyticsData.overview.activeStudents" />
            </div>
            <div class="text-xs text-shuimo/60 mt-1">活跃学生</div>
          </div>
          <TrendingUp class="absolute -bottom-3 -right-3 w-16 h-16 text-tianlv/8 group-hover:text-tianlv/15 transition-all duration-500" />
        </GlassCard>
        <GlassCard class="p-4 text-center relative overflow-hidden group">
          <div class="relative z-10">
            <Target class="w-6 h-6 mx-auto text-zhizi mb-2" />
            <div class="text-2xl font-bold text-shuimo font-mono">
              <AnimatedNumber :value="analyticsData.overview.avgProgress" suffix="%" />
            </div>
            <div class="text-xs text-shuimo/60 mt-1">平均进度</div>
          </div>
          <Target class="absolute -bottom-3 -right-3 w-16 h-16 text-zhizi/8 group-hover:text-zhizi/15 transition-all duration-500" />
        </GlassCard>
        <GlassCard class="p-4 text-center relative overflow-hidden group">
          <div class="relative z-10">
            <Award class="w-6 h-6 mx-auto text-qingsong mb-2" />
            <div class="text-2xl font-bold text-shuimo font-mono">
              <AnimatedNumber :value="analyticsData.overview.avgQuizScore" />
            </div>
            <div class="text-xs text-shuimo/60 mt-1">平均分</div>
          </div>
          <Award class="absolute -bottom-3 -right-3 w-16 h-16 text-qingsong/8 group-hover:text-qingsong/15 transition-all duration-500" />
        </GlassCard>
        <GlassCard class="p-4 text-center relative overflow-hidden group">
          <div class="relative z-10">
            <BookOpen class="w-6 h-6 mx-auto text-zijinghui mb-2" />
            <div class="text-2xl font-bold text-shuimo font-mono">
              <AnimatedNumber :value="analyticsData.overview.completionRate" suffix="%" />
            </div>
            <div class="text-xs text-shuimo/60 mt-1">完课率</div>
          </div>
          <BookOpen class="absolute -bottom-3 -right-3 w-16 h-16 text-zijinghui/8 group-hover:text-zijinghui/15 transition-all duration-500" />
        </GlassCard>
      </div>

      <!-- 章节完成率分析 -->
      <GlassCard class="p-6">
        <h3 class="font-bold text-lg text-shuimo mb-6 flex items-center gap-2">
          <BarChart3 class="w-5 h-5 text-tianlv" />
          章节完成率分析
        </h3>
        <div v-if="chapterChartData.length" class="space-y-4">
          <div v-for="(chapter, index) in chapterChartData" :key="index" class="space-y-2">
            <div class="flex items-center justify-between text-sm">
              <span class="text-shuimo font-medium truncate max-w-[200px]">{{ chapter.title }}</span>
              <div class="flex items-center gap-4 text-xs text-shuimo/60">
                <span>完成率: <span class="font-mono text-tianlv">{{ chapter.completionRate }}%</span></span>
                <span>均分: <span class="font-mono text-qingsong">{{ chapter.avgQuizScore }}</span></span>
                <span v-if="chapter.dropOffRate > 10" class="text-yanzhi">
                  流失: {{ chapter.dropOffRate }}%
                </span>
              </div>
            </div>
            <div class="flex items-center gap-2">
              <div class="flex-1 h-3 bg-slate-100 rounded-full overflow-hidden">
                <div 
                  class="h-full bg-gradient-to-r from-tianlv to-qingsong rounded-full animate-progress-bar"
                  :style="{ '--progress-width': chapter.completionRate + '%' }"
                ></div>
              </div>
              <span class="text-xs font-mono text-shuimo/60 w-12 text-right">{{ chapter.completionRate }}%</span>
            </div>
          </div>
        </div>
        <EmptyState v-else icon="chart" title="暂无章节数据" size="sm" />
      </GlassCard>

      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <!-- 高错误率题目 -->
        <GlassCard class="p-6">
          <h3 class="font-bold text-lg text-shuimo mb-4 flex items-center gap-2">
            <AlertTriangle class="w-5 h-5 text-yanzhi" />
            高错误率章节（需关注）
          </h3>
          <div v-if="highErrorQuestions.length" class="space-y-3">
            <div
              v-for="q in highErrorQuestions"
              :key="q.chapterId"
              class="p-4 bg-yanzhi/5 rounded-xl border border-yanzhi/10"
            >
              <div class="flex items-center justify-between mb-2">
                <span class="font-medium text-shuimo">{{ q.chapterTitle }}</span>
                <span class="text-sm font-bold text-yanzhi">错误率 {{ q.errorRate }}%</span>
              </div>
              <div class="flex items-center gap-4 text-xs text-shuimo/60">
                <span>题目数: {{ q.questionCount }}</span>
                <span>平均分: {{ q.avgScore }}</span>
                <span>答题人数: {{ q.attemptCount }}</span>
              </div>
            </div>
          </div>
          <div v-else class="text-center py-8">
            <div class="w-12 h-12 mx-auto mb-3 bg-qingsong/10 rounded-full flex items-center justify-center">
              <Award class="w-6 h-6 text-qingsong" />
            </div>
            <p class="text-sm text-shuimo/60">所有章节测验表现良好</p>
            <p class="text-xs text-shuimo/40 mt-1">没有错误率超过40%的章节</p>
          </div>
        </GlassCard>

        <!-- 测验分数趋势图 -->
        <GlassCard class="p-6">
          <h3 class="font-bold text-lg text-shuimo mb-4 flex items-center gap-2">
            <LineChart class="w-5 h-5 text-tianlv" />
            课程测验趋势
          </h3>
          <QuizTrendChart
            v-if="selectedCourseId"
            :course-id="selectedCourseId"
            :height="180"
            :show-header="false"
          />
          <EmptyState v-else icon="chart" title="请选择课程" size="sm" />
        </GlassCard>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <GlassCard class="p-6">
          <h3 class="font-bold text-lg text-shuimo mb-4 flex items-center gap-2">
            <TrendingUp class="w-5 h-5 text-qinghua" />
            与平台平均值对比
          </h3>
          <div v-if="comparisonData" class="space-y-6">
            <!-- 平均进度对比 -->
            <div>
              <div class="flex items-center justify-between text-sm mb-2">
                <span class="text-shuimo/60">平均进度</span>
                <span :class="comparisonData.progress.diff >= 0 ? 'text-qingsong' : 'text-yanzhi'" class="font-medium">
                  {{ comparisonData.progress.diff >= 0 ? '+' : '' }}{{ comparisonData.progress.diff.toFixed(1) }}%
                </span>
              </div>
              <div class="flex items-center gap-2">
                <div class="flex-1 space-y-1">
                  <div class="flex items-center gap-2">
                    <span class="text-xs text-shuimo/50 w-16">本课程</span>
                    <div class="flex-1 h-2 bg-slate-100 rounded-full overflow-hidden">
                      <div class="h-full bg-tianlv rounded-full" :style="{ width: comparisonData.progress.course + '%' }"></div>
                    </div>
                    <span class="text-xs font-mono w-10">{{ comparisonData.progress.course }}%</span>
                  </div>
                  <div class="flex items-center gap-2">
                    <span class="text-xs text-shuimo/50 w-16">平台均值</span>
                    <div class="flex-1 h-2 bg-slate-100 rounded-full overflow-hidden">
                      <div class="h-full bg-slate-300 rounded-full" :style="{ width: comparisonData.progress.platform + '%' }"></div>
                    </div>
                    <span class="text-xs font-mono w-10">{{ comparisonData.progress.platform }}%</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- 平均分对比 -->
            <div>
              <div class="flex items-center justify-between text-sm mb-2">
                <span class="text-shuimo/60">测验均分</span>
                <span :class="comparisonData.quizScore.diff >= 0 ? 'text-qingsong' : 'text-yanzhi'" class="font-medium">
                  {{ comparisonData.quizScore.diff >= 0 ? '+' : '' }}{{ comparisonData.quizScore.diff.toFixed(1) }}
                </span>
              </div>
              <div class="flex items-center gap-2">
                <div class="flex-1 space-y-1">
                  <div class="flex items-center gap-2">
                    <span class="text-xs text-shuimo/50 w-16">本课程</span>
                    <div class="flex-1 h-2 bg-slate-100 rounded-full overflow-hidden">
                      <div class="h-full bg-qingsong rounded-full" :style="{ width: comparisonData.quizScore.course + '%' }"></div>
                    </div>
                    <span class="text-xs font-mono w-10">{{ comparisonData.quizScore.course }}</span>
                  </div>
                  <div class="flex items-center gap-2">
                    <span class="text-xs text-shuimo/50 w-16">平台均值</span>
                    <div class="flex-1 h-2 bg-slate-100 rounded-full overflow-hidden">
                      <div class="h-full bg-slate-300 rounded-full" :style="{ width: comparisonData.quizScore.platform + '%' }"></div>
                    </div>
                    <span class="text-xs font-mono w-10">{{ comparisonData.quizScore.platform }}</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- 完课率对比 -->
            <div>
              <div class="flex items-center justify-between text-sm mb-2">
                <span class="text-shuimo/60">完课率</span>
                <span :class="comparisonData.completion.diff >= 0 ? 'text-qingsong' : 'text-yanzhi'" class="font-medium">
                  {{ comparisonData.completion.diff >= 0 ? '+' : '' }}{{ comparisonData.completion.diff.toFixed(1) }}%
                </span>
              </div>
              <div class="flex items-center gap-2">
                <div class="flex-1 space-y-1">
                  <div class="flex items-center gap-2">
                    <span class="text-xs text-shuimo/50 w-16">本课程</span>
                    <div class="flex-1 h-2 bg-slate-100 rounded-full overflow-hidden">
                      <div class="h-full bg-zijinghui rounded-full" :style="{ width: comparisonData.completion.course + '%' }"></div>
                    </div>
                    <span class="text-xs font-mono w-10">{{ comparisonData.completion.course }}%</span>
                  </div>
                  <div class="flex items-center gap-2">
                    <span class="text-xs text-shuimo/50 w-16">平台均值</span>
                    <div class="flex-1 h-2 bg-slate-100 rounded-full overflow-hidden">
                      <div class="h-full bg-slate-300 rounded-full" :style="{ width: comparisonData.completion.platform + '%' }"></div>
                    </div>
                    <span class="text-xs font-mono w-10">{{ comparisonData.completion.platform }}%</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </GlassCard>
      </div>

      <!-- 章节详细数据表格 -->
      <GlassCard class="overflow-hidden">
        <div class="p-4 border-b border-slate-100">
          <h3 class="font-bold text-lg text-shuimo">章节详细数据</h3>
        </div>
        <div class="overflow-x-auto">
          <table class="w-full">
            <thead>
              <tr class="bg-slate-50/50 text-left text-sm text-shuimo/60 border-b border-slate-100">
                <th class="p-4 font-medium">章节</th>
                <th class="p-4 font-medium text-center">完成率</th>
                <th class="p-4 font-medium text-center">视频观看率</th>
                <th class="p-4 font-medium text-center">测验均分</th>
                <th class="p-4 font-medium text-center">流失率</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-50">
              <tr v-for="chapter in analyticsData.chapterAnalytics" :key="chapter.chapterId" class="hover:bg-slate-50/50">
                <td class="p-4 font-medium text-shuimo">{{ chapter.title }}</td>
                <td class="p-4 text-center">
                  <span class="font-mono" :class="chapter.completionRate >= 70 ? 'text-qingsong' : chapter.completionRate >= 40 ? 'text-zhizi' : 'text-yanzhi'">
                    {{ chapter.completionRate }}%
                  </span>
                </td>
                <td class="p-4 text-center">
                  <span class="font-mono text-shuimo">{{ chapter.avgVideoWatchRate }}%</span>
                </td>
                <td class="p-4 text-center">
                  <span class="font-mono" :class="chapter.avgQuizScore >= 80 ? 'text-qingsong' : chapter.avgQuizScore >= 60 ? 'text-zhizi' : 'text-yanzhi'">
                    {{ chapter.avgQuizScore }}
                  </span>
                </td>
                <td class="p-4 text-center">
                  <span class="font-mono" :class="chapter.dropOffRate > 20 ? 'text-yanzhi' : chapter.dropOffRate > 10 ? 'text-zhizi' : 'text-shuimo/60'">
                    {{ chapter.dropOffRate }}%
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </GlassCard>
    </div>
  </div>
</template>
