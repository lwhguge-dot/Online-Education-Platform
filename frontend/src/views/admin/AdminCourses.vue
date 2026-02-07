<script setup>
import { ref, computed, onMounted, onUnmounted, watch, onActivated, onDeactivated } from 'vue'
import { useRouter } from 'vue-router'
import {
  Search, X, CheckCircle, XCircle, Ban, Eye,
  BookOpen, Square, CheckSquare, Download, AlertTriangle
} from 'lucide-vue-next'
import GlassCard from '../../components/ui/GlassCard.vue'
import BaseButton from '../../components/ui/BaseButton.vue'
import BaseCourseCard from '../../components/ui/BaseCourseCard.vue'
import BaseSelect from '../../components/ui/BaseSelect.vue'
import { courseAPI } from '../../services/api'
import { useToastStore } from '../../stores/toast'
import { useConfirmStore } from '../../stores/confirm'

const confirmStore = useConfirmStore()

const props = defineProps({
  courses: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  },
  initialFilter: {
    type: String, // '0' for pending
    default: 'all'
  }
})

const emit = defineEmits(['refresh'])

// 状态
const searchQuery = ref('')
const statusFilter = ref(props.initialFilter || 'all')
const subjectFilter = ref('all')
const selectedCourses = ref([])
const localCourses = ref([])
const localPatchedAt = ref({})
const toast = useToastStore()
const exporting = ref(false)
const router = useRouter()

const idKey = (id) => (id === null || id === undefined) ? '' : String(id)

const patchLocalCourse = (id, patch) => {
  const key = idKey(id)
  localPatchedAt.value = { ...localPatchedAt.value, [key]: Date.now() }
  localCourses.value = localCourses.value.map((c) =>
    idKey(c.id) === key ? { ...c, ...patch } : c
  )
}

watch(
  () => props.initialFilter,
  (val) => {
    statusFilter.value = val || 'all'
    selectedCourses.value = []
  },
  { immediate: true }
)

watch(
  () => props.courses,
  (val) => {
    const incoming = Array.isArray(val) ? val.map(c => ({ ...c })) : []
    const localById = new Map(localCourses.value.map(c => [idKey(c.id), c]))
    const now = Date.now()
    localCourses.value = incoming.map((c) => {
      const key = idKey(c.id)
      const local = localById.get(key)
      const patchedAt = localPatchedAt.value?.[key]
      if (local && patchedAt && (now - patchedAt) < 5000) {
        return { ...c, ...local }
      }
      return c
    })
  },
  { immediate: true }
)

const goToCourseDetail = (courseId) => {
  router.push(`/course/${courseId}`)
}

// 常量配置
const subjects = ['语文', '数学', '英语', '物理', '化学', '生物', '政治', '历史', '地理']

const statusMap = {
  // 数字状态 (兼容旧格式)
  0: { label: '待审核', class: 'bg-amber-100 text-amber-600', icon: Eye },
  1: { label: '已发布', class: 'bg-emerald-100 text-emerald-600', icon: CheckCircle },
  2: { label: '已下架', class: 'bg-slate-100 text-slate-500', icon: Ban },
  // 字符串状态 (后端实际返回格式)
  'PENDING': { label: '待审核', class: 'bg-amber-100 text-amber-600', icon: Eye },
  'REVIEWING': { label: '审核中', class: 'bg-amber-100 text-amber-600', icon: Eye },
  'PUBLISHED': { label: '已发布', class: 'bg-emerald-100 text-emerald-600', icon: CheckCircle },
  'OFFLINE': { label: '已下架', class: 'bg-slate-100 text-slate-500', icon: Ban },
  'REJECTED': { label: '已驳回', class: 'bg-red-100 text-red-500', icon: XCircle },
}

// 状态判断辅助函数 (使用宽松比较以支持字符串和数字)
const isPending = (status) => status == 0 || status === 'PENDING' || status === 'REVIEWING'
const isPublished = (status) => status == 1 || status === 'PUBLISHED'
const isOffline = (status) => status == 2 || status === 'OFFLINE' || status === 'REJECTED'

// 数据处理
const filteredCourses = computed(() => {
  let result = localCourses.value
  
  if (statusFilter.value !== 'all') {
    const val = statusFilter.value
    // 支持数字和字符串状态过滤
    if (val === '0') {
      result = result.filter(c => isPending(c.status))
    } else if (val === '1') {
      result = result.filter(c => isPublished(c.status))
    } else if (val === '2') {
      result = result.filter(c => isOffline(c.status))
    }
  }
  
  if (subjectFilter.value !== 'all') {
    result = result.filter(c => c.subject === subjectFilter.value)
  }
  
  if (searchQuery.value.trim()) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(c => 
      c.title.toLowerCase().includes(query) ||
      (c.teacherName && c.teacherName.toLowerCase().includes(query))
    )
  }
  return result
})

// 选择逻辑
const isAllSelected = computed(() => filteredCourses.value.length > 0 && selectedCourses.value.length === filteredCourses.value.length)

const toggleSelectAll = () => {
  if (isAllSelected.value) selectedCourses.value = []
  else selectedCourses.value = filteredCourses.value.map(c => c.id)
}

const toggleSelectCourse = (id) => {
  const index = selectedCourses.value.indexOf(id)
  if (index > -1) selectedCourses.value.splice(index, 1)
  else selectedCourses.value.push(id)
}

// 操作方法
const getCurrentUser = () => {
  try {
    const userStr = sessionStorage.getItem('user')
    return userStr ? JSON.parse(userStr) : null
  } catch {
    return null
  }
}

const updateStatus = async (course, status) => {
  const actionText = (() => {
    if (isPublished(course?.status)) return '下架'
    if (isOffline(course?.status)) return '重新上架'
    return status == 1 ? '上架' : '下架'
  })()

  const confirmed = await confirmStore.show({
    title: `${actionText}课程`,
    message: `确定要${actionText}课程 "${course?.title || ''}" 吗？`,
    type: actionText === '驳回' || actionText === '下架' ? 'warning' : 'info',
    confirmText: `确定${actionText}`,
    cancelText: '取消'
  })
  if (!confirmed) return

  const currentUser = getCurrentUser()
  try {
    await courseAPI.updateStatus(course.id, status, currentUser?.id, currentUser?.username)
    patchLocalCourse(course.id, { status: status == 2 ? 'OFFLINE' : 'PUBLISHED' })
    statusFilter.value = status == 2 ? '2' : '1'
    emit('refresh')
    toast.success('操作成功')
  } catch (e) {
    toast.error('操作失败')
  }
}

const auditCourse = async (course, action) => {
  const actionText = action === 'APPROVE' ? '通过' : '驳回'
  const confirmed = await confirmStore.show({
    title: `${actionText}课程`,
    message: `确定要${actionText}课程 "${course?.title || ''}" 吗？`,
    type: action === 'REJECT' ? 'warning' : 'info',
    confirmText: `确定${actionText}`,
    cancelText: '取消'
  })
  if (!confirmed) return

  const currentUser = getCurrentUser()
  try {
    await courseAPI.audit(course.id, action, '', currentUser?.id)
    patchLocalCourse(course.id, { status: action === 'APPROVE' ? 'PUBLISHED' : 'REJECTED' })
    statusFilter.value = action === 'APPROVE' ? '1' : '2'
    emit('refresh')
    toast.success('操作成功')
  } catch (e) {
    toast.error('操作失败')
  }
}

// 管理员强制下线违规课程
const offlineCourse = async (course) => {
  const confirmed = await confirmStore.show({
    title: '强制下线',
    message: `确定要强制下线课程 "${course?.title || ''}" 吗？此操作通常用于处理违规课程。`,
    type: 'danger',
    confirmText: '确定下线',
    cancelText: '取消'
  })
  if (!confirmed) return

  const currentUser = getCurrentUser()
  try {
    await courseAPI.offline(course.id, currentUser?.id, currentUser?.username)
    patchLocalCourse(course.id, { status: 'OFFLINE' })
    statusFilter.value = '2'
    emit('refresh')
    toast.success('课程已强制下线')
  } catch (e) {
    toast.error('操作失败：' + (e.message || '未知错误'))
  }
}

const batchAction = async (action) => {
  if (selectedCourses.value.length === 0) return
  
  const actionText = action === 'publish' ? '发布' : '下架'
  const confirmed = await confirmStore.show({
    title: `批量${actionText}课程`,
    message: `确定要批量${actionText} ${selectedCourses.value.length} 个课程吗？`,
    type: action === 'publish' ? 'info' : 'warning',
    confirmText: `确定${actionText}`,
    cancelText: '取消'
  })
  if (!confirmed) return
  
  const currentUser = getCurrentUser()
  const status = action === 'publish' ? 1 : 2
  for (const id of selectedCourses.value) {
    try {
      await courseAPI.updateStatus(id, status, currentUser?.id, currentUser?.username)
      patchLocalCourse(id, { status: status == 2 ? 'OFFLINE' : 'PUBLISHED' })
    } catch (e) {
      console.error('批量更新课程状态失败:', e)
    }
  }
  selectedCourses.value = []
  emit('refresh')
}

// 导出课程 CSV
const exportCourses = async () => {
  exporting.value = true
  try {
    await courseAPI.exportCSV()
    toast.success('课程数据导出成功')
  } catch (e) {
    toast.error('导出失败')
  } finally {
    exporting.value = false
  }
}

// 每 30 秒触发一次刷新
let refreshInterval = null

// 启动轮询（兼容 KeepAlive 激活/失活）
const startRefreshTimer = () => {
  if (refreshInterval) return
  refreshInterval = setInterval(() => {
    emit('refresh')
  }, 30000)
}

// 停止轮询，避免页面缓存时继续请求
const stopRefreshTimer = () => {
  if (!refreshInterval) return
  clearInterval(refreshInterval)
  refreshInterval = null
}

onMounted(startRefreshTimer)
onUnmounted(stopRefreshTimer)
onActivated(startRefreshTimer)
onDeactivated(stopRefreshTimer)
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <!-- Toolbar - 单行水平布局 -->
    <GlassCard class="p-4">
      <div class="flex items-center justify-between gap-4">
        <!-- 左侧：标题和标签页 -->
        <div class="flex items-center gap-4 shrink-0">
          <h3 class="text-lg font-bold text-shuimo flex items-center gap-2 font-song shrink-0">
            <BookOpen class="w-5 h-5 text-tianlv" />
            课程管理
          </h3>
          <div class="flex items-center gap-1">
            <button
              v-for="tab in [{id: 'all', label: '全部'}, {id: '0', label: '待审核'}, {id: '1', label: '已发布'}, {id: '2', label: '已下架'}]"
              :key="tab.id"
              @click="statusFilter = tab.id"
              class="px-3 py-1.5 rounded-lg text-sm font-medium transition-all whitespace-nowrap"
              :class="statusFilter === tab.id ? 'bg-tianlv text-white shadow-md shadow-tianlv/20' : 'text-shuimo/60 hover:text-shuimo hover:bg-slate-100'"
            >
              {{ tab.label }}
            </button>
          </div>
        </div>

        <!-- 右侧：搜索、筛选和操作按钮 -->
        <div class="flex items-center gap-3 shrink-0">
          <!-- 学科筛选 -->
          <BaseSelect 
            v-model="subjectFilter" 
            :options="[{ value: 'all', label: '全部学科' }, ...subjects.map(s => ({ value: s, label: s }))]"
            size="sm"
            class="w-28"
          />
          
          <!-- 搜索框 -->
          <div class="relative group">
            <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-shuimo/40 transition-colors group-focus-within:text-tianlv" />
            <input 
              id="course-search-input"
              v-model="searchQuery"
              type="text" 
              placeholder="搜索课程..."
              aria-label="搜索课程名称或教师"
              class="w-40 pl-9 pr-3 py-2 rounded-xl bg-slate-50 border-none focus:ring-2 focus:ring-tianlv/20 transition-all text-sm"
            />
          </div>
          
          <!-- 批量操作按钮 -->
          <template v-if="selectedCourses.length > 0">
            <div class="w-px h-6 bg-slate-200"></div>
            <button @click="batchAction('publish')" class="px-3 py-2 rounded-xl bg-emerald-50 text-emerald-600 text-sm font-medium hover:bg-emerald-100 transition-colors whitespace-nowrap">
              批量上架
            </button>
            <button @click="batchAction('offline')" class="px-3 py-2 rounded-xl bg-slate-100 text-slate-600 text-sm font-medium hover:bg-slate-200 transition-colors whitespace-nowrap">
              批量下架
            </button>
          </template>
          
          <!-- 导出按钮 -->
          <button 
            v-if="selectedCourses.length === 0"
            @click="exportCourses"
            :disabled="exporting"
            class="px-4 py-2 rounded-xl bg-qinghua/10 text-qinghua text-sm font-medium hover:bg-qinghua/20 transition-colors flex items-center gap-2 disabled:opacity-50"
          >
            <Download class="w-4 h-4" />
            {{ exporting ? '导出中...' : '导出' }}
          </button>
        </div>
      </div>
    </GlassCard>

    <!-- 网格布局 -->
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
       <!-- 全选区域 -->
       <div class="col-span-full flex items-center gap-2 px-2 text-sm text-shuimo/60" v-if="filteredCourses.length > 0">
          <button @click="toggleSelectAll" class="flex items-center gap-2 hover:text-shuimo">
             <component :is="isAllSelected ? CheckSquare : Square" class="w-4 h-4" />
             <span v-if="selectedCourses.length > 0">已选 {{ selectedCourses.length }} 项</span>
             <span v-else>全选本页</span>
          </button>
       </div>

       <!-- 课程卡片（含管理操作） -->
       <div 
         v-for="course in filteredCourses" 
         :key="course.id" 
         class="relative group"
         :class="{'ring-2 ring-tianlv/50 rounded-2xl': selectedCourses.includes(course.id)}"
       >
         <BaseCourseCard 
           :course="{
             ...course,
             teacher: course.teacherName || '未知教师',
             students: course.studentCount || 0,
             rating: course.rating || '4.5'
           }"
           @click="goToCourseDetail(course.id)"
         >
           <!-- 覆盖层：选择框与操作按钮 -->
           <template #overlay>
             <div class="absolute inset-0 z-10 flex flex-col justify-between p-3 opacity-0 group-hover:opacity-100 transition-all duration-300">
               <!-- 顶部：选择框 -->
               <div class="flex justify-start">
                 <button @click.stop="toggleSelectCourse(course.id)" class="p-2 rounded-lg bg-white/90 hover:bg-white text-shuimo shadow-lg backdrop-blur-sm transform -translate-y-2 group-hover:translate-y-0 transition-transform duration-300">
                   <component :is="selectedCourses.includes(course.id) ? CheckSquare : Square" class="w-5 h-5" :class="{'text-tianlv': selectedCourses.includes(course.id)}" />
                 </button>
               </div>
               <!-- 底部：操作按钮 -->
               <div class="flex justify-center gap-2 transform translate-y-2 group-hover:translate-y-0 transition-transform duration-300">
                 <template v-if="isPending(course.status)">
                   <button @click.stop="auditCourse(course, 'REJECT')" class="px-3 py-2 rounded-lg bg-red-500/90 hover:bg-red-500 text-white text-sm font-medium shadow-lg backdrop-blur-sm transition-colors">
                     驳回
                   </button>
                   <button @click.stop="auditCourse(course, 'APPROVE')" class="px-4 py-2 rounded-lg bg-emerald-500/90 hover:bg-emerald-500 text-white text-sm font-bold shadow-lg backdrop-blur-sm transition-colors">
                     通过
                   </button>
                 </template>
                 <template v-if="isPublished(course.status)">
                   <button @click.stop="offlineCourse(course)" class="px-3 py-2 rounded-lg bg-red-500/90 hover:bg-red-500 text-white text-sm font-medium shadow-lg backdrop-blur-sm transition-colors" title="强制下线违规课程">
                     强制下线
                   </button>
                   <button @click.stop="updateStatus(course, 2)" class="px-3 py-2 rounded-lg bg-slate-600/90 hover:bg-slate-600 text-white text-sm font-medium shadow-lg backdrop-blur-sm transition-colors">
                     下架
                   </button>
                 </template>
                 <template v-if="isOffline(course.status)">
                   <button @click.stop="updateStatus(course, 1)" class="px-3 py-2 rounded-lg bg-emerald-500/90 hover:bg-emerald-500 text-white text-sm font-medium shadow-lg backdrop-blur-sm transition-colors">
                     重新上架
                   </button>
                 </template>
               </div>
             </div>
           </template>
           
           <!-- 状态徽标 -->
           <template #badge>
             <div class="absolute top-3 right-3 px-2.5 py-1 rounded-lg backdrop-blur-md border text-xs font-bold shadow-sm"
               :class="statusMap[course.status]?.class"
             >
               {{ statusMap[course.status]?.label }}
             </div>
           </template>
           
           <!-- 底部补充信息 -->
           <template #footer>
             <h3 class="font-bold text-lg text-white mb-1 line-clamp-1 group-hover:text-qingbai transition-colors">
               {{ course.title }}
             </h3>
             <p class="text-xs text-white/70 mb-1">{{ course.teacherName || '未知教师' }}</p>
             <p class="text-xs text-white/50 line-clamp-1">{{ course.description || '暂无简介' }}</p>
           </template>
         </BaseCourseCard>
       </div>
    </div>

    <!-- 空状态 -->
    <div v-if="filteredCourses.length === 0" class="flex flex-col items-center justify-center py-20 text-center">
       <div class="w-20 h-20 rounded-full bg-slate-50 flex items-center justify-center mb-4">
         <BookOpen class="w-10 h-10 text-slate-300" />
       </div>
       <p class="text-shuimo/60 font-medium">暂无相关课程</p>
    </div>
  </div>
</template>
