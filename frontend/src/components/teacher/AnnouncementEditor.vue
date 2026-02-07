<template>
  <div class="announcement-editor">
    <!-- 编辑器弹窗 -->
    <BaseModal :model-value="visible" max-width-class="max-w-2xl" :show-close="false" @update:modelValue="handleClose">
      <template #header>
        <!-- 头部 -->
        <div class="flex items-center justify-between gap-4">
          <h2 class="text-xl font-bold text-shuimo">{{ isEdit ? '编辑公告' : '发布公告' }}</h2>
          <button @click="handleClose" class="text-shuimo/60 hover:text-shuimo">
            <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
      </template>
      
      <!-- 表单内容 -->
      <form @submit.prevent="handleSubmit" class="space-y-6">
            <!-- 公告标题 -->
            <div>
              <label class="block text-sm font-medium text-shuimo mb-2">公告标题 *</label>
              <input 
                v-model="form.title" 
                type="text" 
                required
                placeholder="请输入公告标题"
                class="w-full px-4 py-3 rounded-xl border border-shuimo/20 focus:border-tianlv focus:ring-2 focus:ring-tianlv/20 outline-none transition-all"
              />
            </div>
            
            <!-- 公告内容 -->
            <div>
              <label class="block text-sm font-medium text-shuimo mb-2">公告内容 *</label>
              <textarea 
                v-model="form.content" 
                required
                rows="6"
                placeholder="请输入公告内容..."
                class="w-full px-4 py-3 rounded-xl border border-shuimo/20 focus:border-tianlv focus:ring-2 focus:ring-tianlv/20 outline-none transition-all resize-none"
              ></textarea>
            </div>
            
            <!-- 发布范围 -->
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="block text-sm font-medium text-shuimo mb-2">发布范围</label>
                <BaseSelect 
                  v-model="form.targetType" 
                  :options="[
                    { value: 'global', label: '全局公告（所有学生）' },
                    { value: 'course', label: '课程公告（指定课程）' }
                  ]"
                />
              </div>
              
              <div v-if="form.targetType === 'course'">
                <label class="block text-sm font-medium text-shuimo mb-2">选择课程</label>
                <BaseSelect 
                  v-model="form.courseId" 
                  :options="courseOptions"
                  placeholder="请选择课程"
                />
              </div>
            </div>
            
            <!-- 发布时间设置 -->
            <div>
              <label class="block text-sm font-medium text-shuimo mb-2">发布时间</label>
              <div class="flex items-center gap-4">
                <label class="flex items-center gap-2 cursor-pointer">
                  <input 
                    type="radio" 
                    v-model="form.publishType" 
                    value="now"
                    class="w-4 h-4 text-tianlv"
                  />
                  <span>立即发布</span>
                </label>
                <label class="flex items-center gap-2 cursor-pointer">
                  <input 
                    type="radio" 
                    v-model="form.publishType" 
                    value="scheduled"
                    class="w-4 h-4 text-tianlv"
                  />
                  <span>定时发布</span>
                </label>
              </div>
              
              <div v-if="form.publishType === 'scheduled'" class="mt-3">
                <input 
                  type="datetime-local" 
                  v-model="form.publishTime"
                  :min="minDateTime"
                  class="w-full px-4 py-3 rounded-xl border border-shuimo/20 focus:border-tianlv outline-none"
                />
              </div>
            </div>
            
            <!-- 过期时间 -->
            <div>
              <label class="block text-sm font-medium text-shuimo mb-2">过期时间（可选）</label>
              <input 
                type="datetime-local" 
                v-model="form.expireTime"
                :min="minDateTime"
                class="w-full px-4 py-3 rounded-xl border border-shuimo/20 focus:border-tianlv outline-none"
              />
              <p class="text-xs text-shuimo/60 mt-1">留空表示永不过期</p>
            </div>
            
            <!-- 置顶选项 -->
            <div class="flex items-center gap-2">
              <input 
                type="checkbox" 
                id="isPinned"
                v-model="form.isPinned"
                class="w-4 h-4 text-tianlv rounded"
              />
              <label for="isPinned" class="text-sm text-shuimo cursor-pointer">置顶此公告</label>
            </div>
      </form>
      
      <template #footer>
        <!-- 底部按钮 -->
        <div class="flex items-center justify-end gap-3">
          <button 
            @click="handleClose"
            class="px-6 py-2.5 rounded-xl border border-shuimo/20 text-shuimo hover:bg-slate-100 transition-colors"
          >
            取消
          </button>
          <button 
            @click="handleSubmit"
            :disabled="submitting || !isFormValid"
            class="px-6 py-2.5 rounded-xl bg-tianlv text-white hover:bg-tianlv/90 transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
          >
            <svg v-if="submitting" class="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            {{ isEdit ? '保存修改' : '发布公告' }}
          </button>
        </div>
      </template>
    </BaseModal>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { announcementAPI, courseAPI } from '../../services/api'
import { useAuthStore } from '../../stores/auth'
import { useToastStore } from '../../stores/toast'
import BaseSelect from '../ui/BaseSelect.vue'
import BaseModal from '../ui/BaseModal.vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  announcement: { type: Object, default: null }, // 编辑时传入
})

const emit = defineEmits(['close', 'success'])

const authStore = useAuthStore()
const toast = useToastStore()

const courses = ref([])
const submitting = ref(false)

const form = ref({
  title: '',
  content: '',
  targetType: 'global',
  courseId: null,
  publishType: 'now',
  publishTime: '',
  expireTime: '',
  isPinned: false,
})

const isEdit = computed(() => !!props.announcement)

const minDateTime = computed(() => {
  const now = new Date()
  now.setMinutes(now.getMinutes() - now.getTimezoneOffset())
  return now.toISOString().slice(0, 16)
})

const isFormValid = computed(() => {
  if (!form.value.title.trim() || !form.value.content.trim()) return false
  if (form.value.targetType === 'course' && !form.value.courseId) return false
  if (form.value.publishType === 'scheduled' && !form.value.publishTime) return false
  return true
})

// 课程选项
const courseOptions = computed(() => {
  return courses.value.map(course => ({
    value: course.id,
    label: course.title
  }))
})

// 加载教师课程列表
const loadCourses = async () => {
  try {
    const teacherId = authStore.user?.id
    if (!teacherId) return
    const res = await courseAPI.getTeacherCourses(teacherId)
    if (res.code === 200) {
      courses.value = res.data || []
    }
  } catch (error) {
    console.error('加载课程失败:', error)
  }
}

// 重置表单
const resetForm = () => {
  form.value = {
    title: '',
    content: '',
    targetType: 'global',
    courseId: null,
    publishType: 'now',
    publishTime: '',
    expireTime: '',
    isPinned: false,
  }
}

// 填充编辑数据
const fillEditData = () => {
  if (props.announcement) {
    form.value.title = props.announcement.title || ''
    form.value.content = props.announcement.content || ''
    form.value.targetType = props.announcement.courseId ? 'course' : 'global'
    form.value.courseId = props.announcement.courseId || null
    form.value.isPinned = props.announcement.isPinned || false
    
    if (props.announcement.publishTime) {
      const publishDate = new Date(props.announcement.publishTime)
      if (publishDate > new Date()) {
        form.value.publishType = 'scheduled'
        form.value.publishTime = props.announcement.publishTime.slice(0, 16)
      }
    }
    
    if (props.announcement.expireTime) {
      form.value.expireTime = props.announcement.expireTime.slice(0, 16)
    }
  }
}

// 关闭弹窗
const handleClose = () => {
  resetForm()
  emit('close')
}

// 提交表单
const handleSubmit = async () => {
  if (!isFormValid.value || submitting.value) return
  
  submitting.value = true
  try {
    const teacherId = authStore.user?.id
    
    const data = {
      title: form.value.title.trim(),
      content: form.value.content.trim(),
      targetAudience: 'STUDENT',
      courseId: form.value.targetType === 'course' ? form.value.courseId : null,
      isPinned: form.value.isPinned,
      publishTime: form.value.publishType === 'scheduled' ? form.value.publishTime : null,
      expireTime: form.value.expireTime || null,
    }
    
    let res
    if (isEdit.value) {
      res = await announcementAPI.updateByTeacher(teacherId, props.announcement.id, data)
    } else {
      res = await announcementAPI.createByTeacher(teacherId, data)
    }
    
    if (res.code === 200) {
      toast.success(isEdit.value ? '公告已更新' : '公告发布成功')
      emit('success', res.data)
      handleClose()
    } else {
      toast.error(res.message || '操作失败')
    }
  } catch (error) {
    console.error('操作失败:', error)
  } finally {
    submitting.value = false
  }
}

// 监听弹窗显示
watch(() => props.visible, (val) => {
  if (val) {
    loadCourses()
    if (props.announcement) {
      fillEditData()
    } else {
      resetForm()
    }
  }
})

onMounted(() => {
  if (props.visible) {
    loadCourses()
  }
})
</script>
