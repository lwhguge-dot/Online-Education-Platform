<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import {
  Search, X, Calendar, User, FileText,
  ChevronLeft, ChevronRight, RefreshCw, Clock
} from 'lucide-vue-next'
import GlassCard from '../../components/ui/GlassCard.vue'
import { auditLogAPI } from '../../services/api'
import { useToastStore } from '../../stores/toast'

const toast = useToastStore()

// 状态
const logs = ref([])
const loading = ref(false)
const totalCount = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)

// 筛选条件
const searchQuery = ref('')
const typeFilter = ref('')
const operatorFilter = ref('')
const startDate = ref('')
const endDate = ref('')

// 操作类型映射
const operationTypes = [
  { value: '', label: '全部类型' },
  { value: 'USER_ENABLE', label: '启用用户' },
  { value: 'USER_DISABLE', label: '禁用用户' },
  { value: 'USER_DELETE', label: '删除用户' },
  { value: 'COURSE_APPROVE', label: '审核通过课程' },
  { value: 'COURSE_REJECT', label: '审核拒绝课程' },
  { value: 'COURSE_OFFLINE', label: '下架课程' },
  { value: 'COURSE_ONLINE', label: '上架课程' },
]

const typeStyleMap = {
  USER_ENABLE: { label: '启用用户', class: 'bg-emerald-100 text-emerald-600' },
  USER_DISABLE: { label: '禁用用户', class: 'bg-amber-100 text-amber-600' },
  USER_DELETE: { label: '删除用户', class: 'bg-red-100 text-red-600' },
  COURSE_APPROVE: { label: '审核通过', class: 'bg-blue-100 text-blue-600' },
  COURSE_REJECT: { label: '审核拒绝', class: 'bg-orange-100 text-orange-600' },
  COURSE_OFFLINE: { label: '下架课程', class: 'bg-slate-100 text-slate-600' },
  COURSE_ONLINE: { label: '上架课程', class: 'bg-green-100 text-green-600' },
}

// 获取日志数据
const fetchLogs = async () => {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      size: pageSize.value,
    }
    if (typeFilter.value) params.type = typeFilter.value
    if (operatorFilter.value) params.operator = operatorFilter.value
    if (startDate.value) params.startDate = startDate.value
    if (endDate.value) params.endDate = endDate.value

    const res = await auditLogAPI.getList(params)
    // 后端返回 data.records，兼容 data.content
    logs.value = res.data?.records || res.data?.content || res.data || []
    totalCount.value = res.data?.total || res.data?.totalElements || logs.value.length
  } catch (e) {
    toast.error('获取审计日志失败')
    logs.value = []
  } finally {
    loading.value = false
  }
}

// 计算属性
const filteredLogs = computed(() => {
  if (!searchQuery.value.trim()) return logs.value
  const query = searchQuery.value.toLowerCase()
  return logs.value.filter(log =>
    (log.operatorName && log.operatorName.toLowerCase().includes(query)) ||
    (log.targetName && log.targetName.toLowerCase().includes(query)) ||
    (log.details && log.details.toLowerCase().includes(query))
  )
})

const totalPages = computed(() => Math.ceil(totalCount.value / pageSize.value))

const canGoPrev = computed(() => currentPage.value > 1)
const canGoNext = computed(() => currentPage.value < totalPages.value)

// 分页控制
const goToPage = (page) => {
  if (page >= 1 && page <= totalPages.value) {
    currentPage.value = page
    fetchLogs()
  }
}

// 重置筛选条件
const resetFilters = () => {
  typeFilter.value = ''
  operatorFilter.value = ''
  startDate.value = ''
  endDate.value = ''
  searchQuery.value = ''
  currentPage.value = 1
  fetchLogs()
}

// 应用筛选条件
const applyFilters = () => {
  currentPage.value = 1
  fetchLogs()
}

// 格式化时间
const formatDate = (dateStr) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
}

// 生命周期
onMounted(() => {
  fetchLogs()
})
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <!-- Toolbar - 单行水平布局 -->
    <GlassCard class="p-4">
      <div class="flex items-center justify-between gap-4">
        <!-- 左侧：标题和快捷筛选 -->
        <div class="flex items-center gap-4 shrink-0">
          <h3 class="text-lg font-bold text-shuimo flex items-center gap-2 font-song shrink-0">
            <FileText class="w-5 h-5 text-zijinghui" />
            操作日志
          </h3>
          <div class="flex items-center gap-1">
            <button
              v-for="type in [{id: '', label: '全部'}, {id: 'USER_ENABLE', label: '启用'}, {id: 'USER_DISABLE', label: '禁用'}, {id: 'COURSE_APPROVE', label: '审核'}]"
              :key="type.id"
              @click="typeFilter = type.id; applyFilters()"
              class="px-3 py-1.5 rounded-lg text-sm font-medium transition-all whitespace-nowrap"
              :class="typeFilter === type.id ? 'bg-zijinghui text-white shadow-md shadow-zijinghui/20' : 'text-shuimo/60 hover:text-shuimo hover:bg-slate-100'"
            >
              {{ type.label }}
            </button>
          </div>
        </div>

        <!-- 右侧：搜索、日期筛选和刷新 -->
        <div class="flex items-center gap-3 shrink-0">
          <!-- 日期范围 -->
          <input 
            v-model="startDate"
            type="date"
            class="px-3 py-2 rounded-xl border border-slate-200 bg-white/50 text-sm focus:ring-2 focus:ring-zijinghui/20 outline-none"
            @change="applyFilters"
          />
          <span class="text-shuimo/40">至</span>
          <input 
            v-model="endDate"
            type="date"
            class="px-3 py-2 rounded-xl border border-slate-200 bg-white/50 text-sm focus:ring-2 focus:ring-zijinghui/20 outline-none"
            @change="applyFilters"
          />
          
          <!-- 搜索框 -->
          <div class="relative group">
            <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-shuimo/40 transition-colors group-focus-within:text-zijinghui" />
            <input 
              v-model="searchQuery"
              type="text" 
              placeholder="搜索..."
              class="w-32 pl-9 pr-3 py-2 rounded-xl bg-slate-50 border-none focus:ring-2 focus:ring-zijinghui/20 transition-all text-sm"
            />
            <button v-if="searchQuery" @click="searchQuery = ''" class="absolute right-3 top-1/2 -translate-y-1/2 text-shuimo/40 hover:text-shuimo">
              <X class="w-3 h-3" />
            </button>
          </div>
          
          <!-- 刷新按钮 -->
          <button 
            @click="fetchLogs"
            class="p-2 rounded-xl border border-slate-200 text-shuimo/70 hover:bg-slate-50 transition-colors"
            title="刷新"
          >
            <RefreshCw class="w-4 h-4" :class="{'animate-spin': loading}" />
          </button>
        </div>
      </div>
    </GlassCard>

    <!-- 日志表格 -->
    <GlassCard class="overflow-hidden">
      <div class="overflow-x-auto min-h-[400px]">
        <table class="w-full text-left border-collapse">
          <thead>
            <tr class="text-sm font-bold text-shuimo/60 border-b border-slate-100 bg-slate-50/50">
              <th class="p-4">操作时间</th>
              <th class="p-4">操作类型</th>
              <th class="p-4">操作人</th>
              <th class="p-4">操作目标</th>
              <th class="p-4">详情</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-50">
            <template v-if="loading">
              <tr>
                <td colspan="5" class="py-20 text-center text-shuimo/50">
                  <div class="w-8 h-8 border-2 border-zijinghui/20 border-t-zijinghui rounded-full animate-spin mx-auto mb-3"></div>
                  加载中...
                </td>
              </tr>
            </template>
            <template v-else-if="filteredLogs.length > 0">
              <tr v-for="log in filteredLogs" :key="log.id" class="hover:bg-slate-50/80 transition-colors">
                <td class="p-4">
                  <div class="flex items-center gap-2 text-sm text-shuimo/70">
                    <Clock class="w-4 h-4 text-shuimo/40" />
                    {{ formatDate(log.createdAt) }}
                  </div>
                </td>
                <td class="p-4">
                  <span 
                    class="px-2.5 py-1 rounded-lg text-xs font-bold"
                    :class="typeStyleMap[log.actionType]?.class || 'bg-slate-100 text-slate-600'"
                  >
                    {{ typeStyleMap[log.actionType]?.label || log.actionType }}
                  </span>
                </td>
                <td class="p-4">
                  <div class="flex items-center gap-2">
                    <User class="w-4 h-4 text-shuimo/40" />
                    <span class="text-sm font-medium text-shuimo">{{ log.operatorName || '-' }}</span>
                  </div>
                </td>
                <td class="p-4">
                  <div class="flex items-center gap-2">
                    <FileText class="w-4 h-4 text-shuimo/40" />
                    <span class="text-sm text-shuimo/70">{{ log.targetName || log.targetId || '-' }}</span>
                  </div>
                </td>
                <td class="p-4">
                  <p class="text-sm text-shuimo/60 max-w-xs truncate" :title="log.details">
                    {{ log.details || '-' }}
                  </p>
                </td>
              </tr>
            </template>
            <tr v-else>
              <td colspan="5" class="py-16 text-center">
                <div class="flex flex-col items-center">
                  <div class="w-16 h-16 rounded-full bg-slate-50 flex items-center justify-center mb-4">
                    <FileText class="w-8 h-8 text-slate-300" />
                  </div>
                  <p class="text-shuimo/60 font-medium mb-2">暂无审计日志</p>
                  <button @click="resetFilters" class="text-zijinghui text-sm hover:underline">清除筛选</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      
      <!-- 分页区域 -->
      <div class="p-4 border-t border-slate-100 flex justify-between items-center text-sm text-shuimo/50 bg-slate-50/50">
        <span>
          显示 {{ filteredLogs.length > 0 ? (currentPage - 1) * pageSize + 1 : 0 }} - 
          {{ Math.min(currentPage * pageSize, totalCount) }} 共 {{ totalCount }} 条
        </span>
        <div class="flex items-center gap-2">
          <button 
            @click="goToPage(currentPage - 1)"
            :disabled="!canGoPrev"
            class="p-2 rounded-lg border border-slate-200 bg-white disabled:opacity-50 disabled:cursor-not-allowed hover:bg-slate-50"
          >
            <ChevronLeft class="w-4 h-4" />
          </button>
          <span class="px-3 py-1 text-shuimo font-medium">
            {{ currentPage }} / {{ totalPages || 1 }}
          </span>
          <button 
            @click="goToPage(currentPage + 1)"
            :disabled="!canGoNext"
            class="p-2 rounded-lg border border-slate-200 bg-white disabled:opacity-50 disabled:cursor-not-allowed hover:bg-slate-50"
          >
            <ChevronRight class="w-4 h-4" />
          </button>
        </div>
      </div>
    </GlassCard>
  </div>
</template>

<style scoped>
.animate-slide-down {
  animation: slideDown 0.2s ease;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
