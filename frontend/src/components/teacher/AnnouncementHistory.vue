<template>
  <div class="announcement-history">
    <!-- 筛选栏 -->
    <div class="flex items-center justify-between mb-6">
      <div class="flex items-center gap-4">
        <div class="min-w-[180px]">
          <BaseSelect 
            v-model="filters.courseId" 
            :options="courseOptions"
            @change="loadAnnouncements"
          />
        </div>
        
        <div class="min-w-[140px]">
          <BaseSelect 
            v-model="filters.status" 
            :options="statusOptions"
            @change="loadAnnouncements"
          />
        </div>
      </div>
      
      <button 
        @click="showEditor = true"
        class="px-4 py-2 rounded-xl bg-tianlv text-white hover:bg-tianlv/90 transition-colors flex items-center gap-2"
      >
        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
        </svg>
        发布公告
      </button>
    </div>
    
    <!-- 加载状态 -->
    <div v-if="loading" class="space-y-4">
      <div v-for="i in 3" :key="i" class="bg-white rounded-xl p-6 animate-pulse">
        <div class="h-5 bg-slate-200 rounded w-1/3 mb-3"></div>
        <div class="h-4 bg-slate-200 rounded w-full mb-2"></div>
        <div class="h-4 bg-slate-200 rounded w-2/3"></div>
      </div>
    </div>
    
    <!-- 空状态 -->
    <div v-else-if="announcements.length === 0" class="text-center py-12">
      <EmptyState 
        icon="bell" 
        title="暂无公告" 
        description="发布第一条公告，让学生了解最新动态"
      >
        <template #action>
          <button 
            @click="showEditor = true"
            class="px-4 py-2 rounded-xl bg-tianlv/10 text-tianlv hover:bg-tianlv/20 transition-colors"
          >
            发布第一条公告
          </button>
        </template>
      </EmptyState>
    </div>
    
    <!-- 公告列表 -->
    <div v-else class="space-y-4">
      <div 
        v-for="item in announcements" 
        :key="item.id"
        class="bg-white rounded-xl p-6 border border-shuimo/10 hover:shadow-md transition-shadow"
      >
        <div class="flex items-start justify-between">
          <div class="flex-1">
            <div class="flex items-center gap-2 mb-2">
              <!-- 置顶标记 -->
              <span v-if="item.isPinned" class="px-2 py-0.5 text-xs rounded-full bg-yanzhi/10 text-yanzhi">
                置顶
              </span>
              <!-- 状态标签 -->
              <span :class="getStatusClass(item.status)" class="px-2 py-0.5 text-xs rounded-full">
                {{ getStatusText(item.status) }}
              </span>
              <!-- 课程标签 -->
              <span v-if="item.courseId" class="px-2 py-0.5 text-xs rounded-full bg-tianlv/10 text-tianlv">
                {{ getCourseName(item.courseId) }}
              </span>
              <span v-else class="px-2 py-0.5 text-xs rounded-full bg-shuimo/10 text-shuimo">
                全局公告
              </span>
            </div>
            
            <h3 class="font-bold text-lg text-shuimo mb-2">{{ item.title }}</h3>
            <p class="text-shuimo/70 line-clamp-2 mb-3">{{ item.content }}</p>
            
            <div class="flex items-center gap-4 text-sm text-shuimo/60">
              <span>发布时间: {{ formatDateTime(item.publishTime) }}</span>
              <span v-if="item.expireTime">过期时间: {{ formatDateTime(item.expireTime) }}</span>
              <span class="flex items-center gap-1">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                </svg>
                {{ item.readCount || 0 }} 次阅读
              </span>
            </div>
          </div>
          
          <!-- 操作按钮 -->
          <div class="flex items-center gap-2 ml-4">
            <button 
              @click="togglePin(item)"
              :title="item.isPinned ? '取消置顶' : '置顶'"
              class="p-2 rounded-lg hover:bg-slate-100 transition-colors"
            >
              <svg class="w-5 h-5" :class="item.isPinned ? 'text-yanzhi' : 'text-shuimo/40'" fill="currentColor" viewBox="0 0 24 24">
                <path d="M16 4v8l2 2v2h-6v6l-1 1-1-1v-6H4v-2l2-2V4c0-1.1.9-2 2-2h6c1.1 0 2 .9 2 2z"/>
              </svg>
            </button>
            
            <button 
              @click="editAnnouncement(item)"
              title="编辑"
              class="p-2 rounded-lg hover:bg-slate-100 transition-colors text-shuimo/60 hover:text-tianlv"
            >
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
              </svg>
            </button>
            
            <button 
              @click="confirmDelete(item)"
              title="删除"
              class="p-2 rounded-lg hover:bg-slate-100 transition-colors text-shuimo/60 hover:text-yanzhi"
            >
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
              </svg>
            </button>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 分页 -->
    <div v-if="pagination.total > pagination.size" class="flex items-center justify-center gap-2 mt-6">
      <button 
        @click="changePage(pagination.current - 1)"
        :disabled="pagination.current <= 1"
        class="px-3 py-1.5 rounded-lg border border-shuimo/20 disabled:opacity-50 disabled:cursor-not-allowed hover:bg-slate-50"
      >
        上一页
      </button>
      <span class="text-sm text-shuimo/60">
        第 {{ pagination.current }} / {{ pagination.pages }} 页
      </span>
      <button 
        @click="changePage(pagination.current + 1)"
        :disabled="pagination.current >= pagination.pages"
        class="px-3 py-1.5 rounded-lg border border-shuimo/20 disabled:opacity-50 disabled:cursor-not-allowed hover:bg-slate-50"
      >
        下一页
      </button>
    </div>
    
    <!-- 公告编辑器 -->
    <AnnouncementEditor 
      :visible="showEditor"
      :announcement="editingAnnouncement"
      @close="closeEditor"
      @success="onEditorSuccess"
    />
    
    <!-- 删除确认弹窗 -->
    <div v-if="showDeleteConfirm" class="fixed inset-0 bg-shuimo/20 backdrop-blur-[2px] flex items-center justify-center z-50">
      <div class="bg-white rounded-xl p-6 max-w-sm w-full mx-4">
        <h3 class="font-bold text-lg mb-2">确认删除</h3>
        <p class="text-shuimo/70 mb-6">确定要删除公告「{{ deletingAnnouncement?.title }}」吗？此操作不可撤销。</p>
        <div class="flex justify-end gap-3">
          <button 
            @click="showDeleteConfirm = false"
            class="px-4 py-2 rounded-lg border border-shuimo/20 hover:bg-slate-50"
          >
            取消
          </button>
          <button 
            @click="doDelete"
            :disabled="deleting"
            class="px-4 py-2 rounded-lg bg-yanzhi text-white hover:bg-yanzhi/90 disabled:opacity-50"
          >
            {{ deleting ? '删除中...' : '确认删除' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { announcementAPI, courseAPI } from '../../services/api'
import { useAuthStore } from '../../stores/auth'
import { useToastStore } from '../../stores/toast'
import AnnouncementEditor from './AnnouncementEditor.vue'
import BaseSelect from '../ui/BaseSelect.vue'
import EmptyState from '../ui/EmptyState.vue'

const authStore = useAuthStore()
const toast = useToastStore()

const loading = ref(false)
const announcements = ref([])
const courses = ref([])
const showEditor = ref(false)
const editingAnnouncement = ref(null)
const showDeleteConfirm = ref(false)
const deletingAnnouncement = ref(null)
const deleting = ref(false)

const filters = reactive({
  courseId: null,
  status: '',
})

const pagination = reactive({
  current: 1,
  size: 10,
  total: 0,
  pages: 0,
})

// 课程选项
const courseOptions = computed(() => [
  { value: null, label: '全部课程' },
  ...courses.value.map(c => ({ value: c.id, label: c.title }))
])

// 状态选项
const statusOptions = computed(() => [
  { value: '', label: '全部状态' },
  { value: 'PUBLISHED', label: '已发布' },
  { value: 'SCHEDULED', label: '定时发布' },
  { value: 'EXPIRED', label: '已过期' }
])

// 加载公告列表
const loadAnnouncements = async () => {
  loading.value = true
  try {
    const teacherId = authStore.user?.id
    const params = {
      page: pagination.current,
      size: pagination.size,
    }
    if (filters.courseId) params.courseId = filters.courseId
    if (filters.status) params.status = filters.status
    
    const res = await announcementAPI.getByTeacher(teacherId, params)
    if (res.code === 200) {
      announcements.value = res.data.records || []
      pagination.total = res.data.total || 0
      pagination.pages = res.data.pages || 0
    }
  } catch (error) {
    console.error('加载公告失败:', error)
  } finally {
    loading.value = false
  }
}

// 加载课程列表
const loadCourses = async () => {
  try {
    const teacherId = authStore.user?.id
    const res = await courseAPI.getTeacherCourses(teacherId)
    if (res.code === 200) {
      courses.value = res.data || []
    }
  } catch (error) {
    console.error('加载课程失败:', error)
  }
}

// 获取课程名称
const getCourseName = (courseId) => {
  const course = courses.value.find(c => c.id === courseId)
  return course?.title || '未知课程'
}

// 状态样式
const getStatusClass = (status) => {
  const classes = {
    'PUBLISHED': 'bg-qingsong/10 text-qingsong',
    'SCHEDULED': 'bg-amber-100 text-amber-600',
    'EXPIRED': 'bg-slate-100 text-slate-500',
    'DRAFT': 'bg-slate-100 text-slate-500',
  }
  return classes[status] || 'bg-slate-100 text-slate-500'
}

// 状态文本
const getStatusText = (status) => {
  const texts = {
    'PUBLISHED': '已发布',
    'SCHEDULED': '定时发布',
    'EXPIRED': '已过期',
    'DRAFT': '草稿',
  }
  return texts[status] || status
}

// 格式化日期时间
const formatDateTime = (dateStr) => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

// 切换置顶
const togglePin = async (item) => {
  try {
    const teacherId = authStore.user?.id
    const res = await announcementAPI.togglePin(teacherId, item.id)
    if (res.code === 200) {
      item.isPinned = res.data.isPinned
      toast.success(item.isPinned ? '已置顶' : '已取消置顶')
    } else {
      toast.error(res.message || '操作失败')
    }
  } catch (error) {
    console.error('操作失败:', error)
  }
}

// 编辑公告
const editAnnouncement = (item) => {
  editingAnnouncement.value = item
  showEditor.value = true
}

// 关闭编辑器
const closeEditor = () => {
  showEditor.value = false
  editingAnnouncement.value = null
}

// 编辑器成功回调
const onEditorSuccess = () => {
  loadAnnouncements()
}

// 确认删除
const confirmDelete = (item) => {
  deletingAnnouncement.value = item
  showDeleteConfirm.value = true
}

// 执行删除
const doDelete = async () => {
  if (!deletingAnnouncement.value || deleting.value) return
  
  deleting.value = true
  try {
    const teacherId = authStore.user?.id
    const res = await announcementAPI.deleteByTeacher(teacherId, deletingAnnouncement.value.id)
    if (res.code === 200) {
      toast.success('公告已删除')
      showDeleteConfirm.value = false
      deletingAnnouncement.value = null
      loadAnnouncements()
    } else {
      toast.error(res.message || '删除失败')
    }
  } catch (error) {
    console.error('删除失败:', error)
  } finally {
    deleting.value = false
  }
}

// 切换页码
const changePage = (page) => {
  if (page < 1 || page > pagination.pages) return
  pagination.current = page
  loadAnnouncements()
}

onMounted(() => {
  loadCourses()
  loadAnnouncements()
})

// 暴露方法供父组件调用
defineExpose({
  refresh: loadAnnouncements,
  openEditor: () => { showEditor.value = true },
})
</script>
