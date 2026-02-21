<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { Search, Users, AlertTriangle, Clock, ChevronLeft, ChevronRight, Eye, BookOpen, Download } from 'lucide-vue-next'
import { useAuthStore } from '../../stores/auth'
import { enrollmentAPI } from '../../services/api'
import { useToastStore } from '../../stores/toast'
import GlassCard from '../../components/ui/GlassCard.vue'
import BaseButton from '../../components/ui/BaseButton.vue'
import BaseSelect from '../../components/ui/BaseSelect.vue'
import SkeletonTable from '../../components/ui/SkeletonTable.vue'
import StudentDetailModal from '../../components/teacher/StudentDetailModal.vue'
import EmptyState from '../../components/ui/EmptyState.vue'
import AnimatedNumber from '../../components/ui/AnimatedNumber.vue'

defineProps({
  courses: {
    type: Array,
    default: () => []
  }
})

const authStore = useAuthStore()
const toast = useToastStore()

// 状态
const loading = ref(false)
const exporting = ref(false)
const selectedCourseId = ref(null)
const statusFilter = ref('all')
const searchQuery = ref('')
const currentPage = ref(1)
const pageSize = ref(20)

// 学生详情弹窗状态
const showDetailModal = ref(false)
const selectedStudent = ref(null)

// 数据
const coursesOverview = ref([])
const courseStudents = ref([])
const summary = ref({ total: 0, excellent: 0, good: 0, atRisk: 0, inactive: 0 })
const pagination = ref({ page: 1, size: 20, total: 0 })

// 学情状态配置
const statusConfig = {
  excellent: { label: '优秀', color: 'qingsong', bgColor: 'bg-qingsong/10', textColor: 'text-qingsong' },
  good: { label: '良好', color: 'tianlv', bgColor: 'bg-tianlv/10', textColor: 'text-tianlv' },
  'at-risk': { label: '需关注', color: 'zhizi', bgColor: 'bg-zhizi/10', textColor: 'text-zhizi' },
  inactive: { label: '不活跃', color: 'yanzhi', bgColor: 'bg-yanzhi/10', textColor: 'text-yanzhi' }
}

// 课程选项
const courseOptions = computed(() => {
  return [
    { value: null, label: '全部课程概览' },
    ...coursesOverview.value.map(course => ({
      value: course.courseId,
      label: `${course.courseTitle} (${course.totalStudents}人)`
    }))
  ]
})

// 状态筛选选项
const statusOptions = [
  { value: 'all', label: '全部状态' },
  { value: 'excellent', label: '优秀' },
  { value: 'good', label: '良好' },
  { value: 'at-risk', label: '需关注' },
  { value: 'inactive', label: '不活跃' }
]

// 计算属性
const filteredStudents = computed(() => {
  if (!searchQuery.value.trim()) return courseStudents.value
  const query = searchQuery.value.toLowerCase()
  return courseStudents.value.filter(s => 
    s.name.toLowerCase().includes(query)
  )
})

// 加载教师学生概览
const loadOverview = async () => {
  const userId = authStore.user?.id
  if (!userId) return
  
  loading.value = true
  try {
    const res = await enrollmentAPI.getTeacherStudentsOverview(userId)
    if (res.code === 200 && res.data) {
      coursesOverview.value = res.data.courses || []
      summary.value = {
        total: res.data.totalStudents || 0,
        excellent: 0,
        good: 0,
        atRisk: res.data.totalAtRisk || 0,
        inactive: res.data.totalInactive || 0
      }
    }
  } catch (e) {
    console.error('加载概览失败', e)
  } finally {
    loading.value = false
  }
}

// 加载课程学生列表
const loadCourseStudents = async () => {
  if (!selectedCourseId.value) return
  
  loading.value = true
  try {
    const res = await enrollmentAPI.getCourseStudentsWithStatus(
      selectedCourseId.value, 
      currentPage.value, 
      pageSize.value, 
      statusFilter.value
    )
    if (res.code === 200 && res.data) {
      courseStudents.value = res.data.students || []
      summary.value = res.data.summary || { total: 0, excellent: 0, good: 0, atRisk: 0, inactive: 0 }
      pagination.value = res.data.pagination || { page: 1, size: 20, total: 0 }
    }
  } catch (e) {
    console.error('加载学生列表失败', e)
  } finally {
    loading.value = false
  }
}

// 选择课程
const selectCourse = (courseId) => {
  selectedCourseId.value = courseId
  currentPage.value = 1
  if (courseId) {
    loadCourseStudents()
  }
}

// 返回概览
const backToOverview = () => {
  selectedCourseId.value = null
  courseStudents.value = []
}

// 分页
const goToPage = (page) => {
  currentPage.value = page
  loadCourseStudents()
}

// 监听筛选变化
watch(statusFilter, () => {
  if (selectedCourseId.value) {
    currentPage.value = 1
    loadCourseStudents()
  }
})

// 打开学生详情弹窗
const openStudentDetail = (student) => {
  selectedStudent.value = student
  showDetailModal.value = true
}

/**
 * 导出学生数据为CSV
 * 根据当前选择的课程导出学生列表
 */
const exportStudents = async () => {
  const userId = authStore.user?.id
  if (!userId) {
    toast.error('请先登录')
    return
  }

  exporting.value = true
  try {
    if (selectedCourseId.value) {
      // 导出特定课程的学生
      await enrollmentAPI.exportStudentsCSV(userId, selectedCourseId.value)
      toast.success('学生数据导出成功')
    } else {
      // 导出所有学生（需要后端支持）
      toast.info('请先选择课程后再导出')
    }
  } catch (e) {
    toast.error('导出失败: ' + (e.message || '未知错误'))
  } finally {
    exporting.value = false
  }
}

// 关闭学生详情弹窗
const closeStudentDetail = () => {
  showDetailModal.value = false
  selectedStudent.value = null
}

onMounted(() => {
  loadOverview()
})
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <!-- 课程选择器 -->
    <GlassCard class="p-4">
      <div class="flex items-center gap-4 flex-wrap">
        <label class="font-medium text-shuimo">选择课程:</label>
        <div class="flex-1 max-w-xs">
          <BaseSelect 
            v-model="selectedCourseId" 
            :options="courseOptions"
            @change="selectCourse(selectedCourseId)"
          />
        </div>
        
        <!-- 学情状态筛选 -->
        <div v-if="selectedCourseId" class="w-36">
          <BaseSelect 
            v-model="statusFilter" 
            :options="statusOptions"
          />
        </div>
        
        <!-- 搜索 -->
        <div v-if="selectedCourseId" class="relative flex-1 max-w-xs">
          <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-shuimo/40" />
          <input
            v-model="searchQuery"
            type="text"
            placeholder="搜索学生..."
            class="w-full pl-10 pr-4 py-2 rounded-xl border border-slate-200 bg-white focus:ring-2 focus:ring-tianlv/20 outline-none text-sm"
          />
        </div>

        <!-- 导出按钮 -->
        <BaseButton
          v-if="selectedCourseId && courseStudents.length > 0"
          @click="exportStudents"
          :disabled="exporting"
          variant="secondary"
          size="sm"
        >
          <Download class="w-4 h-4" />
          {{ exporting ? '导出中...' : '导出CSV' }}
        </BaseButton>
      </div>
    </GlassCard>

    <!-- 加载状态 -->
    <SkeletonTable v-if="loading" :rows="5" :cols="6" />

    <!-- 全部课程概览模式 -->
    <div v-else-if="!selectedCourseId" class="space-y-6">
      <!-- 总体统计 -->
      <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
        <GlassCard class="p-4 text-center relative overflow-hidden group">
          <div class="relative z-10">
            <div class="p-2 rounded-lg bg-qinghua/10 text-qinghua w-fit mx-auto mb-2">
              <Users class="w-5 h-5" />
            </div>
            <div class="text-3xl font-bold text-shuimo font-mono">
              <AnimatedNumber :value="summary.total" />
            </div>
            <div class="text-sm text-shuimo/60 mt-1">学生总数</div>
          </div>
          <Users class="absolute -bottom-4 -right-4 w-20 h-20 text-qinghua/8 group-hover:text-qinghua/15 transition-all duration-500" />
        </GlassCard>
        <GlassCard class="p-4 text-center bg-zhizi/5 border-zhizi/20 relative overflow-hidden group">
          <div class="relative z-10">
            <div class="p-2 rounded-lg bg-zhizi/10 text-zhizi w-fit mx-auto mb-2">
              <AlertTriangle class="w-5 h-5" />
            </div>
            <div class="text-3xl font-bold text-zhizi font-mono">
              <AnimatedNumber :value="summary.atRisk" />
            </div>
            <div class="text-sm text-shuimo/60 mt-1">需关注</div>
          </div>
          <AlertTriangle class="absolute -bottom-4 -right-4 w-20 h-20 text-zhizi/8 group-hover:text-zhizi/15 transition-all duration-500" />
        </GlassCard>
        <GlassCard class="p-4 text-center bg-yanzhi/5 border-yanzhi/20 relative overflow-hidden group">
          <div class="relative z-10">
            <div class="p-2 rounded-lg bg-yanzhi/10 text-yanzhi w-fit mx-auto mb-2">
              <Clock class="w-5 h-5" />
            </div>
            <div class="text-3xl font-bold text-yanzhi font-mono">
              <AnimatedNumber :value="summary.inactive" />
            </div>
            <div class="text-sm text-shuimo/60 mt-1">不活跃</div>
          </div>
          <Clock class="absolute -bottom-4 -right-4 w-20 h-20 text-yanzhi/8 group-hover:text-yanzhi/15 transition-all duration-500" />
        </GlassCard>
        <GlassCard class="p-4 text-center relative overflow-hidden group">
          <div class="relative z-10">
            <div class="p-2 rounded-lg bg-tianlv/10 text-tianlv w-fit mx-auto mb-2">
              <BookOpen class="w-5 h-5" />
            </div>
            <div class="text-3xl font-bold text-tianlv font-mono">
              <AnimatedNumber :value="coursesOverview.length" />
            </div>
            <div class="text-sm text-shuimo/60 mt-1">课程数</div>
          </div>
          <BookOpen class="absolute -bottom-4 -right-4 w-20 h-20 text-tianlv/8 group-hover:text-tianlv/15 transition-all duration-500" />
        </GlassCard>
      </div>
      
      <!-- 课程卡片列表 -->
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        <GlassCard 
          v-for="course in coursesOverview" 
          :key="course.courseId" 
          @click="selectCourse(course.courseId)"
          class="cursor-pointer hover:shadow-lg hover:border-tianlv/30 transition-all group"
          hoverable
        >
          <div class="p-4">
            <h3 class="font-bold text-lg text-shuimo group-hover:text-tianlv transition-colors mb-3">
              {{ course.courseTitle }}
            </h3>
            <div class="grid grid-cols-3 gap-2 text-sm">
              <div class="text-center">
                <div class="font-mono font-bold text-shuimo">{{ course.totalStudents }}</div>
                <div class="text-xs text-shuimo/50">学生</div>
              </div>
              <div class="text-center">
                <div class="font-mono font-bold text-zhizi">{{ course.atRiskCount }}</div>
                <div class="text-xs text-shuimo/50">需关注</div>
              </div>
              <div class="text-center">
                <div class="font-mono font-bold text-yanzhi">{{ course.inactiveCount }}</div>
                <div class="text-xs text-shuimo/50">不活跃</div>
              </div>
            </div>
          </div>
        </GlassCard>
      </div>
      
      <!-- 空状态 -->
      <EmptyState 
        v-if="coursesOverview.length === 0" 
        icon="book" 
        title="暂无课程数据" 
        description="发布课程后，学生数据将在这里显示"
      />
    </div>

    <!-- 单课程学生列表模式 -->
    <div v-else class="space-y-6">
      <!-- 返回按钮 -->
      <button @click="backToOverview" class="flex items-center gap-2 text-shuimo/60 hover:text-shuimo transition-colors">
        <ChevronLeft class="w-4 h-4" />
        返回课程概览
      </button>
      
      <!-- 学情统计摘要 -->
      <div class="grid grid-cols-5 gap-4">
        <GlassCard class="p-4 text-center">
          <div class="text-2xl font-bold text-shuimo font-mono">
            <AnimatedNumber :value="summary.total" />
          </div>
          <div class="text-xs text-shuimo/60 mt-1">学生总数</div>
        </GlassCard>
        <GlassCard class="p-4 text-center bg-qingsong/5 border-qingsong/20">
          <div class="text-2xl font-bold text-qingsong font-mono">
            <AnimatedNumber :value="summary.excellent" />
          </div>
          <div class="text-xs text-shuimo/60 mt-1">优秀</div>
        </GlassCard>
        <GlassCard class="p-4 text-center bg-tianlv/5 border-tianlv/20">
          <div class="text-2xl font-bold text-tianlv font-mono">
            <AnimatedNumber :value="summary.good" />
          </div>
          <div class="text-xs text-shuimo/60 mt-1">良好</div>
        </GlassCard>
        <GlassCard class="p-4 text-center bg-zhizi/5 border-zhizi/20">
          <div class="text-2xl font-bold text-zhizi font-mono">
            <AnimatedNumber :value="summary.atRisk" />
          </div>
          <div class="text-xs text-shuimo/60 mt-1">需关注</div>
        </GlassCard>
        <GlassCard class="p-4 text-center bg-yanzhi/5 border-yanzhi/20">
          <div class="text-2xl font-bold text-yanzhi font-mono">
            <AnimatedNumber :value="summary.inactive" />
          </div>
          <div class="text-xs text-shuimo/60 mt-1">不活跃</div>
        </GlassCard>
      </div>
      
      <!-- 学生表格 -->
      <GlassCard class="overflow-hidden">
        <div class="overflow-x-auto">
          <table class="w-full">
            <thead>
              <tr class="bg-slate-50/50 text-left text-sm text-shuimo/60 border-b border-slate-100">
                <th class="p-4 font-medium pl-6">学生</th>
                <th class="p-4 font-medium">学习进度</th>
                <th class="p-4 font-medium">状态</th>
                <th class="p-4 font-medium">最近活跃</th>
                <th class="p-4 font-medium">预警</th>
                <th class="p-4 font-medium">操作</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-50">
              <tr v-for="student in filteredStudents" :key="student.id" 
                  :class="['hover:bg-slate-50/50 transition-colors', 
                    student.learningStatus === 'at-risk' ? 'bg-zhizi/5' : '',
                    student.learningStatus === 'inactive' ? 'bg-yanzhi/5' : '']">
                <td class="p-4 pl-6">
                  <div class="flex items-center gap-3">
                    <div class="w-10 h-10 rounded-full bg-gradient-to-br from-qinghua to-danqing flex items-center justify-center text-white font-medium shadow-sm">
                      {{ student.name.charAt(0) }}
                    </div>
                    <span class="font-medium text-shuimo">{{ student.name }}</span>
                  </div>
                </td>
                <td class="p-4">
                  <div class="flex items-center gap-3">
                    <div class="w-24 h-1.5 bg-slate-100 rounded-full overflow-hidden">
                      <div class="h-full bg-gradient-to-r from-tianlv to-qingsong rounded-full" 
                           :style="{ width: student.courseProgress + '%' }"></div>
                    </div>
                    <span class="text-sm text-shuimo font-mono">{{ student.courseProgress }}%</span>
                  </div>
                </td>
                <td class="p-4">
                  <span :class="['text-xs px-2.5 py-1 rounded-full font-medium', 
                    statusConfig[student.learningStatus]?.bgColor, 
                    statusConfig[student.learningStatus]?.textColor]">
                    {{ statusConfig[student.learningStatus]?.label || '未知' }}
                  </span>
                </td>
                <td class="p-4 text-sm text-shuimo/60 font-mono">
                  {{ student.lastActiveAt }}
                </td>
                <td class="p-4">
                  <div v-if="student.alerts && student.alerts.length > 0" class="flex flex-wrap gap-1">
                    <span v-for="(alert, idx) in student.alerts" :key="idx" 
                          class="text-xs bg-yanzhi/10 text-yanzhi px-2 py-0.5 rounded-full flex items-center gap-1">
                      <AlertTriangle class="w-3 h-3" />
                      {{ alert }}
                    </span>
                  </div>
                  <span v-else class="text-xs text-shuimo/40">-</span>
                </td>
                <td class="p-4">
                  <button 
                    @click="openStudentDetail(student)"
                    class="flex items-center gap-1 text-sm text-tianlv hover:text-qingsong transition-colors"
                  >
                    <Eye class="w-4 h-4" />
                    详情
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        
        <!-- 分页 -->
        <div v-if="pagination.total > pageSize" class="p-4 border-t border-slate-100 flex items-center justify-between">
          <div class="text-sm text-shuimo/60">
            共 {{ pagination.total }} 条记录
          </div>
          <div class="flex items-center gap-2">
            <BaseButton 
              @click="goToPage(currentPage - 1)" 
              :disabled="currentPage <= 1"
              variant="secondary"
              size="sm"
            >
              <ChevronLeft class="w-4 h-4" />
            </BaseButton>
            <span class="text-sm text-shuimo px-3">{{ currentPage }} / {{ Math.ceil(pagination.total / pageSize) }}</span>
            <BaseButton 
              @click="goToPage(currentPage + 1)" 
              :disabled="currentPage >= Math.ceil(pagination.total / pageSize)"
              variant="secondary"
              size="sm"
            >
              <ChevronRight class="w-4 h-4" />
            </BaseButton>
          </div>
        </div>
      </GlassCard>
      
      <!-- 空状态 -->
      <EmptyState 
        v-if="filteredStudents.length === 0 && !loading" 
        icon="users" 
        title="暂无学生数据" 
        description="该课程暂无符合条件的学生"
      />
    </div>
    
    <!-- 学生详情弹窗 -->
    <StudentDetailModal 
      :visible="showDetailModal"
      :student="selectedStudent"
      :courseId="selectedCourseId"
      @close="closeStudentDetail"
    />
  </div>
</template>
