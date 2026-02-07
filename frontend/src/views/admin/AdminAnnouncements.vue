<script setup>
import { ref, computed, onMounted } from 'vue'
import {
  Search, X, Plus, Edit2, Trash2, Send,
  ChevronLeft, ChevronRight, RefreshCw, Bell, Clock,
  Users, Eye, EyeOff, BarChart3
} from 'lucide-vue-next'
import GlassCard from '../../components/ui/GlassCard.vue'
import BaseModal from '../../components/ui/BaseModal.vue'
import AnnouncementStatsModal from '../../components/admin/AnnouncementStatsModal.vue'
import { announcementAPI } from '../../services/api'
import { useToastStore } from '../../stores/toast'
import { useConfirmStore } from '../../stores/confirm'

const toast = useToastStore()
const confirmStore = useConfirmStore()

// 状态
const announcements = ref([])
const loading = ref(false)
const totalCount = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

// 筛选条件
const searchQuery = ref('')
const statusFilter = ref('')

// 弹窗状态
const showModal = ref(false)
const showStatsModal = ref(false)
const selectedAnnouncement = ref(null)
const editingAnnouncement = ref(null)
const formData = ref({
  title: '',
  content: '',
  targetAudience: 'ALL',
  status: 'DRAFT'
})
const saving = ref(false)

// 状态映射
const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'SCHEDULED', label: '定时发布' },
  { value: 'PUBLISHED', label: '已发布' },
  { value: 'EXPIRED', label: '已过期' },
]

const statusStyleMap = {
  DRAFT: { label: '草稿', class: 'bg-slate-100 text-slate-600', icon: EyeOff },
  SCHEDULED: { label: '定时发布', class: 'bg-blue-100 text-blue-600', icon: Clock },
  PUBLISHED: { label: '已发布', class: 'bg-emerald-100 text-emerald-600', icon: Eye },
  EXPIRED: { label: '已过期', class: 'bg-amber-100 text-amber-600', icon: Clock },
}

const audienceOptions = [
  { value: 'ALL', label: '全部用户' },
  { value: 'STUDENT', label: '仅学生' },
  { value: 'TEACHER', label: '仅教师' },
]

// 获取公告列表
const fetchAnnouncements = async () => {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      size: pageSize.value,
    }
    if (statusFilter.value) params.status = statusFilter.value

    const res = await announcementAPI.getList(params)
    // 后端返回 data.records，兼容 data.content
    announcements.value = res.data?.records || res.data?.content || res.data || []
    totalCount.value = res.data?.total || res.data?.totalElements || announcements.value.length
  } catch (e) {
    toast.error('获取公告列表失败')
    announcements.value = []
  } finally {
    loading.value = false
  }
}

// 计算属性
const filteredAnnouncements = computed(() => {
  if (!searchQuery.value.trim()) return announcements.value
  const query = searchQuery.value.toLowerCase()
  return announcements.value.filter(a =>
    (a.title && a.title.toLowerCase().includes(query)) ||
    (a.content && a.content.toLowerCase().includes(query))
  )
})

const totalPages = computed(() => Math.ceil(totalCount.value / pageSize.value))
const canGoPrev = computed(() => currentPage.value > 1)
const canGoNext = computed(() => currentPage.value < totalPages.value)

// 分页控制
const goToPage = (page) => {
  if (page >= 1 && page <= totalPages.value) {
    currentPage.value = page
    fetchAnnouncements()
  }
}

// 打开新建弹窗
const openCreateModal = () => {
  editingAnnouncement.value = null
  formData.value = {
    title: '',
    content: '',
    targetAudience: 'ALL',
    status: 'DRAFT'
  }
  showModal.value = true
}

// 打开编辑弹窗
const openEditModal = (announcement) => {
  editingAnnouncement.value = announcement
  formData.value = {
    title: announcement.title || '',
    content: announcement.content || '',
    targetAudience: announcement.targetAudience || 'ALL',
    status: announcement.status || 'DRAFT'
  }
  showModal.value = true
}

// 保存公告
const saveAnnouncement = async () => {
  if (!formData.value.title.trim()) {
    toast.error('请输入公告标题')
    return
  }
  if (!formData.value.content.trim()) {
    toast.error('请输入公告内容')
    return
  }

  saving.value = true
  try {
    if (editingAnnouncement.value) {
      await announcementAPI.update(editingAnnouncement.value.id, formData.value)
      toast.success('公告已更新')
    } else {
      await announcementAPI.create(formData.value)
      toast.success('公告已创建')
    }
    showModal.value = false
    fetchAnnouncements()
  } catch (e) {
    toast.error('保存失败')
  } finally {
    saving.value = false
  }
}

// 确认发布
const confirmPublish = async (announcement) => {
  const confirmed = await confirmStore.show({
    title: '发布公告',
    message: `确定要发布公告"${announcement?.title || ''}"吗？`,
    type: 'info',
    confirmText: '确认发布',
    cancelText: '取消'
  })
  if (!confirmed) return
  try {
    await announcementAPI.publish(announcement.id)
    toast.success('公告已发布')
    fetchAnnouncements()
  } catch (e) {
    toast.error('发布失败')
  }
}

// 确认删除
const confirmDelete = async (announcement) => {
  const confirmed = await confirmStore.show({
    title: '删除公告',
    message: `确定要删除公告"${announcement?.title || ''}"吗？此操作不可恢复！`,
    type: 'danger',
    confirmText: '确认删除',
    cancelText: '取消'
  })
  if (!confirmed) return
  try {
    await announcementAPI.delete(announcement.id)
    toast.success('公告已删除')
    fetchAnnouncements()
  } catch (e) {
    toast.error('删除失败')
  }
}

// 格式化时间
const formatDate = (dateStr) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
}

// 打开阅读统计弹窗
const openStatsModal = (announcement) => {
  selectedAnnouncement.value = announcement
  showStatsModal.value = true
}

// 生命周期
onMounted(() => {
  fetchAnnouncements()
})
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <!-- Toolbar - 单行水平布局 -->
    <GlassCard class="p-4">
      <div class="flex items-center justify-between gap-4">
        <!-- 左侧：标题和标签页 -->
        <div class="flex items-center gap-4 shrink-0">
          <h3 class="text-lg font-bold text-shuimo flex items-center gap-2 font-song shrink-0">
            <Bell class="w-5 h-5 text-zijinghui" />
            系统公告
          </h3>
          <div class="flex items-center gap-1">
            <button
              v-for="status in [{id: '', label: '全部'}, {id: 'DRAFT', label: '草稿'}, {id: 'PUBLISHED', label: '已发布'}]"
              :key="status.id"
              @click="statusFilter = status.id; fetchAnnouncements()"
              class="px-3 py-1.5 rounded-lg text-sm font-medium transition-all whitespace-nowrap"
              :class="statusFilter === status.id ? 'bg-zijinghui text-white shadow-md shadow-zijinghui/20' : 'text-shuimo/60 hover:text-shuimo hover:bg-slate-100'"
            >
              {{ status.label }}
            </button>
          </div>
        </div>

        <!-- 右侧：搜索、刷新和创建按钮 -->
        <div class="flex items-center gap-3 shrink-0">
          <!-- 搜索框 -->
          <div class="relative group">
            <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-shuimo/40 transition-colors group-focus-within:text-zijinghui" />
            <input 
              id="announcement-search-input"
              v-model="searchQuery"
              type="text" 
              placeholder="搜索公告..."
              aria-label="搜索公告标题或内容"
              class="w-40 pl-9 pr-3 py-2 rounded-xl bg-slate-50 border-none focus:ring-2 focus:ring-zijinghui/20 transition-all text-sm"
            />
            <button v-if="searchQuery" @click="searchQuery = ''" class="absolute right-3 top-1/2 -translate-y-1/2 text-shuimo/40 hover:text-shuimo">
              <X class="w-3 h-3" />
            </button>
          </div>
          
          <!-- 刷新按钮 -->
          <button 
            @click="fetchAnnouncements"
            class="p-2 rounded-xl border border-slate-200 text-shuimo/70 hover:bg-slate-50 transition-colors"
            title="刷新"
          >
            <RefreshCw class="w-4 h-4" :class="{'animate-spin': loading}" />
          </button>
          
          <!-- 新建按钮 -->
          <button 
            @click="openCreateModal"
            class="px-4 py-2 rounded-xl bg-zijinghui text-white text-sm font-medium hover:bg-zijinghui/90 flex items-center gap-2 transition-colors"
          >
            <Plus class="w-4 h-4" />
            新建公告
          </button>
        </div>
      </div>
    </GlassCard>

    <!-- 公告列表 -->
    <GlassCard class="overflow-hidden">
      <div class="min-h-[400px]">
        <template v-if="loading">
          <div class="py-20 text-center text-shuimo/50">
            <div class="w-8 h-8 border-2 border-zijinghui/20 border-t-zijinghui rounded-full animate-spin mx-auto mb-3"></div>
            加载中...
          </div>
        </template>
        <template v-else-if="filteredAnnouncements.length > 0">
          <div class="divide-y divide-slate-100">
            <div 
              v-for="announcement in filteredAnnouncements" 
              :key="announcement.id"
              class="p-6 hover:bg-slate-50/50 transition-colors"
            >
              <div class="flex items-start justify-between gap-4">
                <div class="flex-1 min-w-0">
                  <div class="flex items-center gap-3 mb-2">
                    <h3 class="font-bold text-shuimo truncate">{{ announcement.title }}</h3>
                    <span 
                      class="px-2 py-0.5 rounded-full text-xs font-medium flex items-center gap-1 shrink-0"
                      :class="statusStyleMap[announcement.status]?.class"
                    >
                      <component :is="statusStyleMap[announcement.status]?.icon" class="w-3 h-3" />
                      {{ statusStyleMap[announcement.status]?.label }}
                    </span>
                  </div>
                  <p class="text-sm text-shuimo/60 line-clamp-2 mb-3">{{ announcement.content }}</p>
                  <div class="flex items-center gap-4 text-xs text-shuimo/40">
                    <span class="flex items-center gap-1">
                      <Users class="w-3.5 h-3.5" />
                      {{ audienceOptions.find(a => a.value === announcement.targetAudience)?.label || announcement.targetAudience }}
                    </span>
                    <span class="flex items-center gap-1">
                      <Clock class="w-3.5 h-3.5" />
                      {{ formatDate(announcement.createdAt) }}
                    </span>
                    <!-- 阅读统计 -->
                    <span
                      v-if="announcement.status === 'PUBLISHED'"
                      class="flex items-center gap-1 text-qinghua cursor-pointer hover:text-qinghua/80 transition-colors"
                      @click="openStatsModal(announcement)"
                      title="查看阅读统计"
                    >
                      <Eye class="w-3.5 h-3.5" />
                      {{ announcement.readCount || 0 }} 次阅读
                    </span>
                  </div>
                </div>
                <div class="flex items-center gap-2 shrink-0">
                  <!-- 阅读统计按钮 -->
                  <button
                    v-if="announcement.status === 'PUBLISHED'"
                    @click="openStatsModal(announcement)"
                    class="p-2 rounded-lg hover:bg-qinghua/10 text-qinghua/60 hover:text-qinghua transition-colors"
                    title="阅读统计"
                  >
                    <BarChart3 class="w-4 h-4" />
                  </button>
                  <button
                    v-if="announcement.status === 'DRAFT'"
                    @click="confirmPublish(announcement)"
                    class="p-2 rounded-lg hover:bg-emerald-50 text-emerald-500 hover:text-emerald-600 transition-colors"
                    title="发布"
                  >
                    <Send class="w-4 h-4" />
                  </button>
                  <button
                    @click="openEditModal(announcement)"
                    class="p-2 rounded-lg hover:bg-slate-100 text-shuimo/40 hover:text-shuimo transition-colors"
                    title="编辑"
                  >
                    <Edit2 class="w-4 h-4" />
                  </button>
                  <button
                    @click="confirmDelete(announcement)"
                    class="p-2 rounded-lg hover:bg-red-50 text-shuimo/40 hover:text-red-500 transition-colors"
                    title="删除"
                  >
                    <Trash2 class="w-4 h-4" />
                  </button>
                </div>
              </div>
            </div>
          </div>
        </template>
        <template v-else>
          <div class="py-16 text-center">
            <div class="flex flex-col items-center">
              <div class="w-16 h-16 rounded-full bg-slate-50 flex items-center justify-center mb-4">
                <Bell class="w-8 h-8 text-slate-300" />
              </div>
              <p class="text-shuimo/60 font-medium mb-2">暂无公告</p>
              <button @click="openCreateModal" class="text-zijinghui text-sm hover:underline">创建第一条公告</button>
            </div>
          </div>
        </template>
      </div>
      
      <!-- 分页区域 -->
      <div class="p-4 border-t border-slate-100 flex justify-between items-center text-sm text-shuimo/50 bg-slate-50/50">
        <span>共 {{ totalCount }} 条公告</span>
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

    <!-- 新建/编辑弹窗 -->
    <BaseModal v-model="showModal" :title="editingAnnouncement ? '编辑公告' : '新建公告'" max-width-class="max-w-lg">
      <div class="overflow-y-auto max-h-[60vh] space-y-4">
              <!-- 标题 -->
              <div>
                <label class="text-sm font-medium text-shuimo mb-1.5 block">标题 *</label>
                <input 
                  id="announcement-title"
                  v-model="formData.title"
                  type="text"
                  placeholder="请输入公告标题"
                  aria-label="公告标题"
                  class="w-full px-4 py-2.5 rounded-xl border border-slate-200 bg-white focus:ring-2 focus:ring-zijinghui/20 outline-none text-sm"
                />
              </div>
              
              <!-- 内容 -->
              <div>
                <label class="text-sm font-medium text-shuimo mb-1.5 block">内容 *</label>
                <textarea 
                  id="announcement-content"
                  v-model="formData.content"
                  rows="5"
                  placeholder="请输入公告内容"
                  aria-label="公告内容"
                  class="w-full px-4 py-2.5 rounded-xl border border-slate-200 bg-white focus:ring-2 focus:ring-zijinghui/20 outline-none text-sm resize-none"
                ></textarea>
              </div>
              
              <!-- 受众 -->
              <div>
                <label class="text-sm font-medium text-shuimo mb-1.5 block">受众</label>
                <select 
                  id="announcement-audience"
                  v-model="formData.targetAudience"
                  aria-label="公告受众选择"
                  class="w-full px-4 py-2.5 rounded-xl border border-slate-200 bg-white focus:ring-2 focus:ring-zijinghui/20 outline-none text-sm"
                >
                  <option v-for="audience in audienceOptions" :key="audience.value" :value="audience.value">
                    {{ audience.label }}
                  </option>
                </select>
              </div>
      </div>

      <template #footer>
        <button 
          @click="showModal = false"
          class="px-4 py-2 rounded-xl bg-slate-100 text-shuimo text-sm font-medium hover:bg-slate-200 transition-colors"
        >
          取消
        </button>
        <button 
          @click="saveAnnouncement"
          :disabled="saving"
          class="px-4 py-2 rounded-xl bg-zijinghui text-white text-sm font-medium hover:bg-zijinghui/90 transition-colors disabled:opacity-50 flex items-center gap-2"
        >
          <span v-if="saving" class="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></span>
          {{ saving ? '保存中...' : '保存' }}
        </button>
      </template>
    </BaseModal>

    <!-- 阅读统计弹窗 -->
    <AnnouncementStatsModal
      v-model="showStatsModal"
      :announcement-id="selectedAnnouncement?.id"
      :announcement-title="selectedAnnouncement?.title || ''"
    />
  </div>
</template>

<style scoped>
.line-clamp-2 {
  display: -webkit-box;
  line-clamp: 2;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
