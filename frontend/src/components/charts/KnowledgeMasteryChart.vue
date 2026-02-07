<script setup>
/**
 * 知识点掌握度图表组件
 * 以雷达图/进度条形式展示学生对各章节/知识点的掌握程度
 */
import { ref, computed, onMounted, watch } from 'vue'
import { Brain, TrendingUp, Target, Award, ChevronRight } from 'lucide-vue-next'
import { progressAPI } from '../../services/api'
import GlassCard from '../ui/GlassCard.vue'
import AnimatedNumber from '../ui/AnimatedNumber.vue'

const props = defineProps({
  /** 学生ID */
  studentId: {
    type: [Number, String],
    required: true
  },
  /** 是否显示标题 */
  showHeader: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['navigate'])

// 状态管理
const loading = ref(false)
const masteryData = ref({
  overallMastery: 0,
  totalKnowledgePoints: 0,
  masteredCount: 0,
  learningCount: 0,
  notStartedCount: 0,
  chapters: []
})

/**
 * 加载知识点掌握度数据
 * 调用后端API获取学生的知识点掌握情况
 */
const loadMasteryData = async () => {
  if (!props.studentId) return

  loading.value = true
  try {
    const res = await progressAPI.getKnowledgeMastery(props.studentId)
    if (res.code === 200 && res.data) {
      masteryData.value = {
        overallMastery: res.data.overallMastery || 0,
        totalKnowledgePoints: res.data.totalKnowledgePoints || 0,
        masteredCount: res.data.masteredCount || 0,
        learningCount: res.data.learningCount || 0,
        notStartedCount: res.data.notStartedCount || 0,
        chapters: res.data.chapters || []
      }
    }
  } catch (e) {
    console.error('加载知识点掌握度失败:', e)
    // 如果API返回错误，使用空数据
    masteryData.value = {
      overallMastery: 0,
      totalKnowledgePoints: 0,
      masteredCount: 0,
      learningCount: 0,
      notStartedCount: 0,
      chapters: []
    }
  } finally {
    loading.value = false
  }
}

/**
 * 获取掌握度对应的颜色样式
 * @param {number} mastery - 掌握度百分比
 * @returns {string} 颜色类名
 */
const getMasteryColor = (mastery) => {
  if (mastery >= 80) return 'text-qingsong'
  if (mastery >= 60) return 'text-tianlv'
  if (mastery >= 40) return 'text-zhizi'
  return 'text-yanzhi'
}

/**
 * 获取掌握度对应的进度条颜色
 * @param {number} mastery - 掌握度百分比
 * @returns {string} 渐变色类名
 */
const getMasteryBarColor = (mastery) => {
  if (mastery >= 80) return 'from-qingsong to-tianlv'
  if (mastery >= 60) return 'from-tianlv to-qingsong'
  if (mastery >= 40) return 'from-zhizi to-qiuxiang'
  return 'from-yanzhi to-mudan'
}

/**
 * 获取掌握度对应的背景色
 * @param {number} mastery - 掌握度百分比
 * @returns {string} 背景色类名
 */
const getMasteryBgColor = (mastery) => {
  if (mastery >= 80) return 'bg-qingsong/10'
  if (mastery >= 60) return 'bg-tianlv/10'
  if (mastery >= 40) return 'bg-zhizi/10'
  return 'bg-yanzhi/10'
}

/**
 * 获取掌握度等级描述
 * @param {number} mastery - 掌握度百分比
 * @returns {string} 等级描述
 */
const getMasteryLevel = (mastery) => {
  if (mastery >= 80) return '熟练掌握'
  if (mastery >= 60) return '基本掌握'
  if (mastery >= 40) return '正在学习'
  if (mastery > 0) return '需要加强'
  return '未开始'
}

/**
 * 计算整体掌握度进度条宽度
 */
const overallProgressWidth = computed(() => {
  return `${Math.min(masteryData.value.overallMastery, 100)}%`
})

/**
 * 点击章节跳转到学习页面
 */
const handleChapterClick = (chapter) => {
  emit('navigate', { courseId: chapter.courseId, chapterId: chapter.chapterId })
}

// 监听studentId变化，重新加载数据
watch(() => props.studentId, (newId) => {
  if (newId) {
    loadMasteryData()
  }
}, { immediate: true })

onMounted(() => {
  if (props.studentId) {
    loadMasteryData()
  }
})
</script>

<template>
  <GlassCard class="p-6 card-hover-glow">
    <!-- 标题区域 -->
    <div v-if="showHeader" class="flex items-center justify-between mb-6">
      <h3 class="text-lg font-bold text-shuimo flex items-center gap-2 font-song">
        <Brain class="w-5 h-5 text-zijinghui" />
        知识点掌握度
      </h3>
      <span class="text-xs text-shuimo/50">基于学习进度和测验成绩</span>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="py-12 text-center">
      <div class="w-8 h-8 border-2 border-zijinghui/20 border-t-zijinghui rounded-full animate-spin mx-auto mb-3"></div>
      <p class="text-sm text-shuimo/50">分析中...</p>
    </div>

    <!-- 数据展示 -->
    <template v-else>
      <!-- 整体概览 -->
      <div class="grid grid-cols-4 gap-4 mb-6">
        <!-- 总体掌握度 -->
        <div class="col-span-2 p-4 rounded-xl" :class="getMasteryBgColor(masteryData.overallMastery)">
          <div class="flex items-center gap-3">
            <div class="w-12 h-12 rounded-xl flex items-center justify-center" :class="getMasteryBgColor(masteryData.overallMastery)">
              <Target class="w-6 h-6" :class="getMasteryColor(masteryData.overallMastery)" />
            </div>
            <div>
              <div class="text-3xl font-bold font-mono" :class="getMasteryColor(masteryData.overallMastery)">
                <AnimatedNumber :value="masteryData.overallMastery" suffix="%" />
              </div>
              <div class="text-xs text-shuimo/60">整体掌握度</div>
            </div>
          </div>
          <!-- 进度条 -->
          <div class="mt-3 h-2 bg-white/50 rounded-full overflow-hidden">
            <div
              class="h-full rounded-full bg-gradient-to-r transition-all duration-1000 ease-out"
              :class="getMasteryBarColor(masteryData.overallMastery)"
              :style="{ width: overallProgressWidth }"
            ></div>
          </div>
        </div>

        <!-- 已掌握 -->
        <div class="text-center p-4 rounded-xl bg-qingsong/10 hover:bg-qingsong/15 transition-colors">
          <Award class="w-5 h-5 mx-auto text-qingsong mb-1" />
          <div class="text-xl font-bold text-qingsong font-mono">
            <AnimatedNumber :value="masteryData.masteredCount" />
          </div>
          <div class="text-xs text-shuimo/50">已掌握</div>
        </div>

        <!-- 学习中 -->
        <div class="text-center p-4 rounded-xl bg-zhizi/10 hover:bg-zhizi/15 transition-colors">
          <TrendingUp class="w-5 h-5 mx-auto text-zhizi mb-1" />
          <div class="text-xl font-bold text-zhizi font-mono">
            <AnimatedNumber :value="masteryData.learningCount" />
          </div>
          <div class="text-xs text-shuimo/50">学习中</div>
        </div>
      </div>

      <!-- 章节详情 -->
      <div class="space-y-3">
        <h4 class="text-sm font-bold text-shuimo flex items-center gap-2">
          各章节掌握情况
        </h4>

        <!-- 空状态 -->
        <div v-if="masteryData.chapters.length === 0" class="text-center py-8">
          <div class="w-14 h-14 mx-auto mb-3 rounded-full bg-zijinghui/10 flex items-center justify-center">
            <Brain class="w-7 h-7 text-zijinghui/50" />
          </div>
          <p class="text-sm text-shuimo/50">暂无学习数据</p>
          <p class="text-xs text-shuimo/30 mt-1">开始学习后会显示掌握情况</p>
        </div>

        <!-- 章节列表 -->
        <div v-else class="space-y-2 max-h-64 overflow-y-auto custom-scrollbar">
          <div
            v-for="chapter in masteryData.chapters"
            :key="chapter.chapterId"
            class="group p-3 rounded-xl bg-slate-50 hover:bg-slate-100 transition-all cursor-pointer"
            @click="handleChapterClick(chapter)"
          >
            <div class="flex items-center justify-between mb-2">
              <div class="flex items-center gap-2">
                <span class="text-sm font-medium text-shuimo group-hover:text-qinghua transition-colors truncate max-w-[180px]">
                  {{ chapter.chapterTitle || chapter.title }}
                </span>
                <span
                  class="px-2 py-0.5 rounded text-[10px] font-bold"
                  :class="getMasteryBgColor(chapter.mastery) + ' ' + getMasteryColor(chapter.mastery)"
                >
                  {{ getMasteryLevel(chapter.mastery) }}
                </span>
              </div>
              <div class="flex items-center gap-2">
                <span class="text-sm font-mono font-bold" :class="getMasteryColor(chapter.mastery)">
                  {{ chapter.mastery }}%
                </span>
                <ChevronRight class="w-4 h-4 text-shuimo/30 group-hover:text-qinghua group-hover:translate-x-0.5 transition-all" />
              </div>
            </div>
            <!-- 章节进度条 -->
            <div class="h-1.5 bg-slate-200 rounded-full overflow-hidden">
              <div
                class="h-full rounded-full bg-gradient-to-r transition-all duration-500"
                :class="getMasteryBarColor(chapter.mastery)"
                :style="{ width: `${chapter.mastery}%` }"
              ></div>
            </div>
            <!-- 详细信息 -->
            <div class="flex items-center gap-4 mt-2 text-xs text-shuimo/40">
              <span v-if="chapter.quizScore !== undefined">测验: {{ chapter.quizScore }}分</span>
              <span v-if="chapter.videoProgress !== undefined">视频: {{ chapter.videoProgress }}%</span>
              <span v-if="chapter.lastStudyTime">{{ chapter.lastStudyTime }}</span>
            </div>
          </div>
        </div>
      </div>
    </template>
  </GlassCard>
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
