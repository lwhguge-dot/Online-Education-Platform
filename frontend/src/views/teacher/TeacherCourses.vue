<script setup>
import { ref, computed } from 'vue'
import {
  Plus, Video, Trash2,
  Upload, CheckCircle, Settings, X, ListChecks
} from 'lucide-vue-next'
import { useToastStore } from '../../stores/toast'
import { useConfirmStore } from '../../stores/confirm'
import { useAuthStore } from '../../stores/auth'
import TeacherCourseCatalog from '../../components/teacher/TeacherCourseCatalog.vue'
import BaseButton from '../../components/ui/BaseButton.vue'
import BaseInput from '../../components/ui/BaseInput.vue'
import BaseSelect from '../../components/ui/BaseSelect.vue'
import { courseAPI, chapterAPI, fileAPI } from '../../services/api'

const confirmStore = useConfirmStore()
const authStore = useAuthStore()

const props = defineProps({
  courses: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['refresh'])

// 状态
const searchQuery = ref('')
const activeTab = ref('all')
const showCourseModal = ref(false)
const showChapterModal = ref(false)
const editingCourse = ref(null)
const toast = useToastStore()

const courseStatusMap = {
  PUBLISHED: { label: '已发布', class: 'bg-qingsong/10 text-qingsong border-qingsong/20' },
  DRAFT: { label: '草稿', class: 'bg-slate-100 text-slate-600 border-slate-200' },
  OFFLINE: { label: '已下线', class: 'bg-yanzhi/10 text-yanzhi border-yanzhi/20' },
  REVIEWING: { label: '审核中', class: 'bg-zhizi/10 text-zhizi border-zhizi/20' },
  REJECTED: { label: '已驳回', class: 'bg-yanzhi/10 text-yanzhi border-yanzhi/20' }
}

const statusTooltipMap = {
  DRAFT: '草稿仅课程教师可见，编辑并保存后会自动提交管理员审核。',
  REVIEWING: '课程正在等待管理员审核，通过后才会重新对学生开放。'
}

const getStatusTooltip = (status) => statusTooltipMap[status] || ''

const subjectOptions = ['语文', '数学', '英语', '物理', '化学', '生物', '政治', '历史', '地理']

// 新课程表单
const newCourse = ref({
  title: '',
  subject: '语文',
  description: '',
  coverImage: '',
  unlockVideoRate: 90,
  unlockQuizScore: 60
})
const uploadingCover = ref(false)
const coverInput = ref(null)

// 章节管理状态
const courseChapters = ref([])
const editingChapter = ref(null)
const newChapter = ref({ title: '', description: '', videoUrl: '', duration: 0, orderNum: 1 })
const uploadingVideo = ref(false)
const dragging = ref(false)

// 测验管理状态
const chapterQuizzes = ref([])
const loadingQuizzes = ref(false)

const secondsToMinutes = (seconds) => Math.max(0, Math.round((Number(seconds) || 0) / 60))
const minutesToSeconds = (minutes) => Math.max(0, Math.round((Number(minutes) || 0) * 60))
const chapterMinutes = (chapter) => secondsToMinutes(chapter?.videoDuration ?? chapter?.duration ?? 0)

// 计算属性
const filteredCourses = computed(() => {
  let result = props.courses
  
  if (activeTab.value !== 'all') {
    result = result.filter(c => c.status === activeTab.value)
  }
  
  if (searchQuery.value.trim()) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(c => 
      c.title.toLowerCase().includes(query) || 
      c.subject.toLowerCase().includes(query)
    )
  }
  
  return result
})

// 课程相关方法
const openCourseModal = (course = null) => {
  editingCourse.value = course
  if (course) {
    newCourse.value = { ...course }
  } else {
    newCourse.value = { title: '', subject: '语文', description: '', unlockVideoRate: 90, unlockQuizScore: 60, coverImage: '' }
  }
  showCourseModal.value = true
}

const handleCoverUpload = async (event) => {
  const file = event.target.files[0]
  if (!file) return

  uploadingCover.value = true
  try {
    const res = await fileAPI.uploadImage(file)
    if (res.code === 200) {
      newCourse.value.coverImage = res.data.url
    }
  } catch (e) {
    toast.error('上传失败: ' + e.message)
  } finally {
    uploadingCover.value = false
  }
}

// 删除封面图片
const deleteCoverImage = async () => {
  if (!newCourse.value.coverImage) return
  const confirmed = await confirmStore.show({
    title: '删除封面',
    message: '确定要删除此封面图片吗？',
    type: 'warning',
    confirmText: '删除',
    cancelText: '取消'
  })
  if (!confirmed) return
  try {
    await fileAPI.deleteFile(newCourse.value.coverImage)
    newCourse.value.coverImage = ''
    toast.success('封面已删除')
  } catch (error) {
    console.error('删除课程封面失败:', error)
    // 即使删除失败也清空本地引用
    newCourse.value.coverImage = ''
    toast.warning('文件可能已被删除')
  }
}

const saveCourse = async () => {
  if (!newCourse.value.title) return toast.warning('请输入课程名称')
  
  const currentUser = authStore.user
  if (!currentUser) {
    toast.error('请先登录')
    return
  }
  
  try {
    const data = {
      title: newCourse.value.title,
      description: newCourse.value.description,
      subject: newCourse.value.subject,
      coverImage: newCourse.value.coverImage,
      unlockVideoRate: newCourse.value.unlockVideoRate,
      unlockQuizScore: newCourse.value.unlockQuizScore
    }
    
    if (editingCourse.value) {
      await courseAPI.update(editingCourse.value.id, data)
    } else {
      await courseAPI.create({
        ...data,
        status: 'DRAFT' // 默认草稿
      })
    }
    showCourseModal.value = false
    emit('refresh')
    // 课程编辑保存后由后端统一进入待审核状态，提示语需明确流程变化
    toast.success(editingCourse.value ? '已保存并提交审核' : '课程创建成功（草稿）')
  } catch (e) {
    toast.error('保存失败: ' + e.message)
  }
}

// 章节相关方法
const openChapterManager = async (course) => {
  editingCourse.value = course
  showChapterModal.value = true
  await loadChapters(course.id)
}

const loadChapters = async (courseId) => {
  try {
    const res = await chapterAPI.getByCourse(courseId)
    courseChapters.value = res.data || []
  } catch (error) {
    console.error('加载章节列表失败:', error)
    courseChapters.value = []
  }
}

const prepareChapterForm = (chapter = null) => {
  editingChapter.value = chapter
  if (chapter) {
    newChapter.value = {
      ...chapter,
      title: chapter.title || '',
      description: chapter.description || '',
      videoUrl: chapter.videoUrl || '',
      duration: secondsToMinutes(chapter.videoDuration ?? chapter.duration ?? 0),
      orderNum: chapter.sortOrder ?? chapter.orderNum ?? 1
    }
    // 加载章节测验
    loadQuizzes(chapter.id)
  } else {
    newChapter.value = { title: '', description: '', videoUrl: '', duration: 0, orderNum: courseChapters.value.length + 1 }
    chapterQuizzes.value = []
  }
}

const handleVideoUpload = async (event) => {
  const file = event.target.files[0]
  if (!file) return
  uploadingVideo.value = true
  try {
    // 获取视频时长
    const duration = await getVideoDuration(file)
    
    const res = await fileAPI.uploadVideo(file)
    if (res.code === 200) {
      newChapter.value.videoUrl = res.data.url
      newChapter.value.duration = secondsToMinutes(duration)
      toast.success('视频上传成功')
    }
  } catch (error) {
    console.error('上传章节视频失败:', error)
    toast.error('上传失败')
  } finally {
    uploadingVideo.value = false
  }
}

const getVideoDuration = (file) => {
  return new Promise((resolve) => {
    const video = document.createElement('video')
    video.preload = 'metadata'
    video.onloadedmetadata = () => {
      window.URL.revokeObjectURL(video.src)
      resolve(Math.floor(video.duration))
    }
    video.onerror = () => resolve(0)
    video.src = URL.createObjectURL(file)
  })
}

// 删除章节视频
const deleteChapterVideo = async () => {
  if (!newChapter.value.videoUrl) return
  const confirmed = await confirmStore.show({
    title: '删除视频',
    message: '确定要删除此章节视频吗？',
    type: 'warning',
    confirmText: '删除',
    cancelText: '取消'
  })
  if (!confirmed) return
  try {
    await fileAPI.deleteFile(newChapter.value.videoUrl)
    newChapter.value.videoUrl = ''
    newChapter.value.duration = 0
    toast.success('视频已删除')
  } catch (error) {
    console.error('删除章节视频失败:', error)
    // 即使删除失败也清空本地引用
    newChapter.value.videoUrl = ''
    newChapter.value.duration = 0
    toast.warning('文件可能已被删除')
  }
}

const saveChapter = async () => {
  if (!newChapter.value.title) return toast.warning('请输入章节标题')
  
  try {
    const data = {
      ...newChapter.value,
      videoDuration: minutesToSeconds(newChapter.value.duration || 0),
      sortOrder: newChapter.value.orderNum,
      courseId: editingCourse.value.id
    }
    
    if (editingChapter.value) {
      await chapterAPI.update(editingChapter.value.id, data)
    } else {
      await chapterAPI.create(data)
    }
    await loadChapters(editingCourse.value.id)
    prepareChapterForm(null) // 重置表单
    toast.success('章节保存成功')
  } catch (e) {
    toast.error('保存章节失败: ' + e.message)
  }
}

const deleteChapter = async (id) => {
  const confirmed = await confirmStore.show({
    title: '删除章节',
    message: '确定要删除此章节吗？章节内的所有内容将被删除。',
    type: 'danger',
    confirmText: '删除',
    cancelText: '取消'
  })
  if (!confirmed) return
  try {
    await chapterAPI.delete(id)
    await loadChapters(editingCourse.value.id)
    toast.success('章节已删除')
  } catch (error) {
    console.error('删除章节失败:', error)
    toast.error('删除失败')
  }
}

// 测验管理方法
const loadQuizzes = async (chapterId) => {
  if (!chapterId) {
    chapterQuizzes.value = []
    return
  }
  loadingQuizzes.value = true
  try {
    const res = await chapterAPI.getQuizzes(chapterId)
    chapterQuizzes.value = res.data || []
  } catch (error) {
    console.error('加载章节测验失败:', error)
    chapterQuizzes.value = []
  } finally {
    loadingQuizzes.value = false
  }
}

const deleteQuiz = async (quizId) => {
  const confirmed = await confirmStore.show({
    title: '删除测验题目',
    message: '确定要删除此测验题目吗？此操作不可撤销。',
    type: 'danger',
    confirmText: '删除',
    cancelText: '取消'
  })
  if (!confirmed) return
  try {
    await chapterAPI.deleteQuiz(quizId)
    // 重新加载测验列表
    if (editingChapter.value) {
      await loadQuizzes(editingChapter.value.id)
    }
    toast.success('测验题目已删除')
  } catch (e) {
    toast.error('删除失败：' + (e.message || '未知错误'))
  }
}
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <!-- 抽离课程目录区块，主页面仅保留状态与行为编排 -->
    <TeacherCourseCatalog
      :courses="filteredCourses"
      :active-tab="activeTab"
      :search-query="searchQuery"
      :status-map="courseStatusMap"
      :get-status-tooltip="getStatusTooltip"
      @update:active-tab="activeTab = $event"
      @update:search-query="searchQuery = $event"
      @create="openCourseModal()"
      @edit="openCourseModal"
      @manage-chapters="openChapterManager"
    />

    <!-- 课程弹窗 -->
    <Teleport to="body">
      <div
        v-if="showCourseModal"
        class="fixed inset-0 z-[100] flex items-center justify-center p-6"
        role="dialog"
        aria-modal="true"
        aria-labelledby="teacher-course-modal-title"
        @click.self="showCourseModal = false"
        @keydown.esc.stop.prevent="showCourseModal = false"
      >
        <!-- 背景遮罩 -->
        <div class="absolute inset-0 bg-shuimo/20 backdrop-blur-[2px]" @click="showCourseModal = false"></div>
        <!-- 弹窗内容 -->
        <div class="relative bg-white rounded-2xl w-full max-w-md max-h-[85vh] shadow-2xl border border-slate-200 animate-scale-in flex flex-col">
          <!-- 固定头部 -->
          <div class="flex justify-between items-center p-5 border-b border-slate-100 shrink-0">
            <h3 id="teacher-course-modal-title" class="text-lg font-bold text-shuimo font-song">{{ editingCourse ? '编辑课程' : '创建新课程' }}</h3>
            <button @click="showCourseModal = false" aria-label="关闭课程编辑弹窗"><X class="w-5 h-5 text-shuimo/40 hover:text-shuimo transition-colors" /></button>
          </div>
          
          <!-- 可滚动内容区 -->
          <div class="flex-1 overflow-y-auto p-5 space-y-4">
             <!-- 封面上传 -->
             <div>
               <span class="block text-sm font-bold text-shuimo mb-2">课程封面</span>
               <div class="flex gap-3 items-start">
                 <div class="w-24 h-16 rounded-lg border-2 border-dashed border-slate-200 bg-slate-50 overflow-hidden flex items-center justify-center">
                   <img v-if="newCourse.coverImage" :src="newCourse.coverImage" class="w-full h-full object-cover">
                   <Upload v-else class="w-5 h-5 text-slate-300" />
                 </div>
                  <div>
                     <label for="course-cover-input" class="sr-only">上传课程封面</label>
                     <input id="course-cover-input" name="courseCover" type="file" ref="coverInput" class="hidden" accept="image/*" @change="handleCoverUpload">
                     <div class="flex gap-2">
                      <BaseButton size="sm" variant="secondary" @click="coverInput?.click()" :loading="uploadingCover">
                        {{ uploadingCover ? '上传中' : (newCourse.coverImage ? '更换' : '上传图片') }}
                      </BaseButton>
                      <BaseButton v-if="newCourse.coverImage" size="sm" variant="ghost" @click="deleteCoverImage" class="text-yanzhi hover:bg-yanzhi/10">
                        删除
                      </BaseButton>
                    </div>
                    <p class="text-xs text-shuimo/40 mt-1">支持 JPG, PNG</p>
                 </div>
               </div>
             </div>

             <BaseInput id="teacher-course-title" label="课程名称" v-model="newCourse.title" required placeholder="输入课程名" autofocus />
             
             <div>
               <span class="block text-sm font-bold text-shuimo mb-1">学科分类</span>
               <BaseSelect 
                 v-model="newCourse.subject" 
                 :options="subjectOptions"
                 placeholder="选择学科"
               />
             </div>

              <div>
                <label for="course-description-input" class="block text-sm font-bold text-shuimo mb-1">简介</label>
                <textarea id="course-description-input" name="courseDescription" v-model="newCourse.description" rows="2" class="w-full px-3 py-2 rounded-xl border border-slate-200 focus:ring-2 focus:ring-tianlv/20 outline-none text-sm resize-none"></textarea>
              </div>
             
             <!-- 解锁设置 -->
             <div class="bg-slate-50 p-3 rounded-xl">
               <h4 class="font-bold text-sm text-shuimo mb-2 flex items-center gap-2">
                 <Settings class="w-4 h-4" /> 解锁条件
               </h4>
               <div class="grid grid-cols-2 gap-3">
                  <BaseInput label="视频观看率 (%)" type="number" v-model="newCourse.unlockVideoRate" />
                  <BaseInput label="测验分 (%)" type="number" v-model="newCourse.unlockQuizScore" />
               </div>
             </div>
          </div>

          <!-- 固定底部按钮 -->
          <div class="p-4 border-t border-slate-100 flex gap-3 shrink-0 bg-white rounded-b-2xl">
            <BaseButton block variant="ghost" @click="showCourseModal = false">取消</BaseButton>
            <BaseButton block variant="primary" @click="saveCourse">
              {{ editingCourse ? '保存并提交审核' : '保存草稿' }}
            </BaseButton>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 章节弹窗 -->
    <Teleport to="body">
      <div
        v-if="showChapterModal"
        class="fixed inset-0 z-[100] flex items-center justify-center p-6"
        role="dialog"
        aria-modal="true"
        aria-labelledby="teacher-chapter-modal-title"
        @click.self="showChapterModal = false"
        @keydown.esc.stop.prevent="showChapterModal = false"
      >
        <!-- 背景遮罩 -->
        <div class="absolute inset-0 bg-shuimo/20 backdrop-blur-[2px]" @click="showChapterModal = false"></div>
        <!-- 弹窗内容 -->
        <div class="relative bg-white rounded-2xl w-full max-w-4xl max-h-[85vh] shadow-2xl border border-slate-200 flex overflow-hidden animate-scale-in">
          <!-- 左侧：章节列表 -->
          <div class="w-72 border-r border-slate-100 bg-slate-50/50 flex flex-col">
            <div class="p-4 border-b border-slate-100">
             <h3 class="font-bold text-shuimo text-base mb-1">章节管理</h3>
             <p class="text-xs text-shuimo/50 truncate">{{ editingCourse?.title }}</p>
          </div>
          <div class="flex-1 overflow-y-auto p-3 space-y-2">
             <button 
               @click="prepareChapterForm()" 
               class="w-full py-3 border-2 border-dashed border-slate-200 rounded-xl text-shuimo/50 hover:border-tianlv hover:text-tianlv hover:bg-tianlv/5 transition-all text-sm font-bold flex items-center justify-center gap-2"
             >
               <Plus class="w-4 h-4" /> 添加新章节
             </button>

             <div v-for="(chapter, idx) in courseChapters" :key="chapter.id" 
               role="button"
               tabindex="0"
               :aria-label="`编辑章节 ${chapter.title}`"
               @click="prepareChapterForm(chapter)"
               @keydown.enter.prevent="prepareChapterForm(chapter)"
               @keydown.space.prevent="prepareChapterForm(chapter)"
               :class="['p-4 rounded-xl text-left transition-all border group cursor-pointer hover:shadow-md',
                 editingChapter?.id === chapter.id ? 'bg-white border-tianlv shadow-md' : 'bg-white border-transparent hover:border-slate-200']"
             >
                <div class="flex justify-between items-start mb-1">
                   <span class="font-bold text-shuimo/80 text-sm">第 {{ idx+1 }} 章</span>
                   <CheckCircle v-if="chapter.videoUrl" class="w-3 h-3 text-tianlv" />
                </div>
                <h4 class="font-bold text-shuimo text-sm mb-1">{{ chapter.title }}</h4>
                <div class="flex justify-between items-center mt-2 opacity-0 group-hover:opacity-100 transition-opacity">
                   <span class="text-[10px] text-shuimo/40">{{ chapterMinutes(chapter) }} min</span>
                   <button @click.stop="deleteChapter(chapter.id)" :aria-label="`删除章节 ${chapter.title}`" class="p-1 hover:bg-yanzhi/10 rounded text-yanzhi">
                      <Trash2 class="w-3 h-3" />
                    </button>
                </div>
             </div>
          </div>
        </div>

        <!-- 右侧：详情表单 -->
        <div class="flex-1 p-6 overflow-y-auto bg-white flex flex-col">
           <div class="flex justify-between items-center mb-6">
             <h3 id="teacher-chapter-modal-title" class="text-lg font-bold text-shuimo font-song">{{ editingChapter ? '编辑章节' : '新建章节' }}</h3>
             <button @click="showChapterModal = false" aria-label="关闭章节管理弹窗"><X class="w-5 h-5 text-shuimo/40 hover:text-shuimo transition-colors" /></button>
           </div>
           
           <div class="space-y-5 max-w-xl mx-auto w-full">
              <BaseInput id="teacher-chapter-title" label="章节标题" v-model="newChapter.title" required autofocus />
              
              <div>
                 <span class="block text-sm font-bold text-shuimo mb-2">章节视频</span>
                 <div class="border-2 border-dashed border-slate-200 rounded-xl p-6 text-center transition-colors"
                   :class="{'border-tianlv bg-tianlv/5': dragging}"
                 >
                     <label for="chapter-video-input" class="sr-only">上传章节视频</label>
                     <input id="chapter-video-input" name="chapterVideo" type="file" ref="videoInput" accept="video/*" class="hidden" @change="handleVideoUpload">
                    
                    <div v-if="uploadingVideo" class="py-3">
                       <div class="animate-spin w-6 h-6 border-4 border-tianlv border-t-transparent rounded-full mx-auto mb-2"></div>
                       <p class="text-tianlv font-bold text-sm">正在上传视频...</p>
                    </div>
                    
                    <div v-else-if="newChapter.videoUrl">
                       <Video class="w-10 h-10 text-tianlv mx-auto mb-2" />
                       <p class="text-tianlv font-bold text-sm mb-1">视频已上传</p>
                       <p class="text-xs text-shuimo/40 truncate max-w-xs mx-auto">{{ newChapter.videoUrl }}</p>
                       <div class="mt-2 flex justify-center gap-3">
                         <button @click="videoInput?.click()" class="text-xs text-shuimo/60 hover:underline">重新上传</button>
                         <button @click="deleteChapterVideo" class="text-xs text-yanzhi hover:underline">删除视频</button>
                       </div>
                    </div>

                    <div v-else>
                       <Upload class="w-10 h-10 text-slate-300 mx-auto mb-2" />
                       <p class="text-shuimo/60 font-medium text-sm mb-3">拖拽视频文件到此处，或点击上传</p>
                       <BaseButton size="sm" variant="secondary" @click="videoInput?.click()">选择视频</BaseButton>
                    </div>
                 </div>
              </div>

              <div class="grid grid-cols-2 gap-4">
                 <BaseInput label="时长 (分钟)" type="number" v-model="newChapter.duration" />
                 <BaseInput label="排序" type="number" v-model="newChapter.orderNum" />
              </div>
              
              <div>
                 <label for="chapter-description-input" class="block text-sm font-bold text-shuimo mb-2">本章简介</label>
                 <textarea id="chapter-description-input" name="chapterDescription" v-model="newChapter.description" rows="3" class="w-full px-3 py-2 rounded-xl border border-slate-200 focus:ring-2 focus:ring-tianlv/20 outline-none text-sm resize-none"></textarea>
              </div>

              <!-- 测验管理区域 -->
              <div v-if="editingChapter" class="border-t border-slate-100 pt-5">
                <div class="flex items-center justify-between mb-3">
                  <span class="text-sm font-bold text-shuimo flex items-center gap-2">
                    <ListChecks class="w-4 h-4 text-qinghua" />
                    章节测验题目
                  </span>
                  <span class="text-xs text-shuimo/50">共 {{ chapterQuizzes.length }} 题</span>
                </div>

                <div v-if="loadingQuizzes" class="py-4 text-center text-shuimo/50 text-sm">
                  加载中...
                </div>

                <div v-else-if="chapterQuizzes.length === 0" class="py-4 text-center text-shuimo/40 text-sm bg-slate-50 rounded-xl">
                  暂无测验题目
                </div>

                <div v-else class="space-y-2 max-h-48 overflow-y-auto">
                  <div
                    v-for="(quiz, idx) in chapterQuizzes"
                    :key="quiz.id"
                    class="flex items-start gap-3 p-3 bg-slate-50 rounded-xl group hover:bg-slate-100 transition-colors"
                  >
                    <span class="w-6 h-6 rounded-full bg-qinghua/10 text-qinghua text-xs font-bold flex items-center justify-center shrink-0">
                      {{ idx + 1 }}
                    </span>
                    <div class="flex-1 min-w-0">
                      <p class="text-sm text-shuimo line-clamp-2">{{ quiz.question || quiz.content }}</p>
                      <p class="text-xs text-shuimo/50 mt-1">
                        {{ quiz.type === 'single' ? '单选题' : quiz.type === 'multiple' ? '多选题' : quiz.type }}
                        · {{ quiz.score || 10 }}分
                      </p>
                    </div>
                    <button
                      @click="deleteQuiz(quiz.id)"
                      :aria-label="`删除第 ${idx + 1} 道测验题`"
                      class="p-1.5 rounded-lg text-shuimo/30 hover:text-yanzhi hover:bg-yanzhi/10 transition-colors opacity-0 group-hover:opacity-100"
                      title="删除此题"
                    >
                      <Trash2 class="w-4 h-4" />
                    </button>
                  </div>
                </div>
              </div>

              <div class="pt-4 flex gap-3">
                 <BaseButton block variant="ghost" @click="prepareChapterForm(null)" v-if="editingChapter">取消编辑</BaseButton>
                 <BaseButton block variant="primary" @click="saveChapter">
                   {{ editingChapter ? '保存修改' : '创建章节' }}
                 </BaseButton>
              </div>
           </div>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
