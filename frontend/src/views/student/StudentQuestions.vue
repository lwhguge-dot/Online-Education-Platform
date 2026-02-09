<script setup>
import { ref, computed, watch } from 'vue'
import { MessageSquare, Plus, MessageCircle, X, Image, Upload, Loader2, Trash2, CheckCircle, Clock, ChevronDown, ChevronUp, BookOpen, FileText } from 'lucide-vue-next'

import GlassCard from '../../components/ui/GlassCard.vue'
import BaseButton from '../../components/ui/BaseButton.vue'
import { fileAPI, chapterAPI } from '../../services/api'
import { useToastStore } from '../../stores/toast'

const props = defineProps({
  questions: {
    type: Array,
    default: () => []
  },
  // 新增：已选课程列表，用于关联问题
  enrolledCourses: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['submit'])
const toast = useToastStore()

const showAskModal = ref(false)
const newQuestion = ref({ courseId: '', chapterId: '', courseName: '', chapterName: '', title: '', content: '', imageUrl: '' })
const imageFile = ref(null)
const imagePreview = ref('')
const uploading = ref(false)
const fileInputRef = ref(null)

// 展开的问题ID列表
const expandedQuestions = ref(new Set())

// 选中课程的章节列表
const courseChapters = ref([])
const loadingChapters = ref(false)

// 监听课程选择变化，加载对应章节
watch(() => newQuestion.value.courseId, async (courseId) => {
  newQuestion.value.chapterId = ''
  newQuestion.value.chapterName = ''
  courseChapters.value = []
  
  if (courseId) {
    loadingChapters.value = true
    try {
      const res = await chapterAPI.getByCourse(courseId)
      if (res.data) {
        courseChapters.value = res.data
      }
    } catch (e) {
      console.error('加载章节失败:', e)
    }
    loadingChapters.value = false
    
    // 设置课程名称
    const course = props.enrolledCourses.find(c => c.id == courseId)
    if (course) {
      newQuestion.value.courseName = course.title
    }
  }
})

// 监听章节选择变化
watch(() => newQuestion.value.chapterId, (chapterId) => {
  if (chapterId) {
    const chapter = courseChapters.value.find(c => c.id == chapterId)
    if (chapter) {
      newQuestion.value.chapterName = chapter.title
    }
  }
})

// 获取问题状态
const getQuestionStatus = (question) => {
  if (question.commentCount > 0 || question.hasReply) {
    return 'replied' // 已回复
  }
  // 检查是否超过24小时
  if (question.time) {
    const questionTime = new Date(question.time)
    const now = new Date()
    const hoursDiff = (now - questionTime) / (1000 * 60 * 60)
    if (hoursDiff > 24) {
      return 'waiting' // 等待中（超过24小时）
    }
  }
  return 'pending' // 待回复
}

// 状态样式映射
const statusStyles = {
  replied: {
    bg: 'bg-tianlv/10',
    text: 'text-tianlv',
    label: '已回复',
    icon: CheckCircle
  },
  waiting: {
    bg: 'bg-amber-100',
    text: 'text-amber-600',
    label: '等待回复中',
    icon: Clock
  },
  pending: {
    bg: 'bg-slate-100',
    text: 'text-slate-500',
    label: '待回复',
    icon: Clock
  }
}

// 切换展开状态
const toggleExpand = (questionId) => {
  if (expandedQuestions.value.has(questionId)) {
    expandedQuestions.value.delete(questionId)
  } else {
    expandedQuestions.value.add(questionId)
  }
}

// 检查是否展开
const isExpanded = (questionId) => {
  return expandedQuestions.value.has(questionId)
}

const openAskModal = () => {
  if (!props.enrolledCourses || props.enrolledCourses.length === 0) {
    toast.warning('请先报名课程后再提问')
    return
  }
  newQuestion.value = { courseId: '', chapterId: '', courseName: '', chapterName: '', title: '', content: '', imageUrl: '' }
  imageFile.value = null
  imagePreview.value = ''
  courseChapters.value = []
  showAskModal.value = true
}

// 处理图片文件选择
const handleImageSelect = (event) => {
  const file = event.target.files?.[0]
  if (!file) return
  
  // 校验文件类型
  if (!file.type.startsWith('image/')) {
    toast.error('请选择图片文件')
    return
  }
  
  // 校验文件大小（最大 5MB）
  if (file.size > 5 * 1024 * 1024) {
    toast.error('图片大小不能超过5MB')
    return
  }
  
  imageFile.value = file
  // 生成预览图
  const reader = new FileReader()
  reader.onload = (e) => {
    imagePreview.value = e.target.result
  }
  reader.readAsDataURL(file)
}

// 移除已选图片
const removeImage = () => {
  imageFile.value = null
  imagePreview.value = ''
  newQuestion.value.imageUrl = ''
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
  }
}

// 上传图片并提交问题
const submitQuestion = async () => {
  if (!props.enrolledCourses || props.enrolledCourses.length === 0) {
    toast.warning('请先报名课程后再提问')
    return
  }
  if (!newQuestion.value.courseId) {
    toast.warning('请选择关联课程')
    return
  }
  if (!newQuestion.value.chapterId) {
    toast.warning('请选择关联章节')
    return
  }
  if (!newQuestion.value.title || !newQuestion.value.content) {
    toast.warning('请填写问题标题和详情')
    return
  }
  
  // 如果选择图片则先上传
  if (imageFile.value) {
    uploading.value = true
    try {
      const res = await fileAPI.uploadImage(imageFile.value)
      if (res.code === 200 && res.data) {
        newQuestion.value.imageUrl = res.data
      } else {
        toast.error('图片上传失败')
        uploading.value = false
        return
      }
    } catch (e) {
      toast.error('图片上传失败: ' + e.message)
      uploading.value = false
      return
    }
    uploading.value = false
  }
  
  emit('submit', { ...newQuestion.value })
  showAskModal.value = false
}
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <!-- 工具栏 -->
    <GlassCard class="p-4 flex flex-col md:flex-row justify-between items-center gap-4" style="animation: fade-in-up 0.4s ease-out forwards;">
       <h3 class="text-lg font-bold text-shuimo flex items-center gap-2 font-song">
          <MessageCircle class="w-5 h-5 text-zijinghui icon-hover-rotate" />
          我的提问
       </h3>
       
       <BaseButton @click="openAskModal" icon="Plus" variant="primary" class="btn-ripple">我要提问</BaseButton>
    </GlassCard>

    <!-- 问题列表 - 网格布局 -->
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
       <GlassCard 
         v-for="(q, index) in questions" 
         :key="q.id"
         class="p-4 transition-all hover:bg-slate-50 cursor-pointer card-hover-lift stagger-item h-fit"
         :style="{ animationDelay: `${index * 0.08}s`, opacity: 0, animation: `fade-in-up 0.4s ease-out ${index * 0.08}s forwards` }"
         @click="toggleExpand(q.id)"
       >
         <div class="flex items-start gap-4">
            <div class="w-10 h-10 rounded-full bg-zijinghui/10 flex items-center justify-center text-zijinghui shrink-0 transition-transform hover:scale-110">
               <MessageSquare class="w-5 h-5" />
            </div>
            <div class="flex-1 min-w-0">
               <div class="flex items-center justify-between mb-1 gap-2">
                  <h4 class="font-bold text-shuimo truncate">{{ q.title || q.content || '无标题' }}</h4>
                  <div class="flex items-center gap-2 shrink-0">
                    <!-- 状态标签 -->
                    <span 
                      class="text-xs px-2 py-0.5 rounded-full flex items-center gap-1 transition-all"
                      :class="[statusStyles[getQuestionStatus(q)].bg, statusStyles[getQuestionStatus(q)].text]"
                    >
                      <component :is="statusStyles[getQuestionStatus(q)].icon" class="w-3 h-3" />
                      {{ statusStyles[getQuestionStatus(q)].label }}
                    </span>
                    <span class="text-xs text-shuimo/50">{{ q.time }}</span>
                  </div>
               </div>
               
               <!-- 问题内容预览 -->
               <p class="text-sm text-shuimo/70" :class="isExpanded(q.id) ? '' : 'line-clamp-2'">{{ q.content }}</p>
               
               <!-- 展开/收起按钮 -->
               <div class="mt-2 flex items-center justify-between">
                  <div class="flex items-center gap-4 text-xs text-shuimo/50">
                    <span v-if="q.commentCount > 0" class="text-zijinghui font-medium">{{ q.commentCount }} 条回复</span>
                    <span v-else>暂无回复</span>
                  </div>
                  <button 
                    class="text-xs text-qinghua flex items-center gap-1 hover:text-qinghua/80 transition-all btn-ripple"
                    @click.stop="toggleExpand(q.id)"
                  >
                    {{ isExpanded(q.id) ? '收起' : '展开' }}
                    <ChevronUp v-if="isExpanded(q.id)" class="w-3 h-3 transition-transform" />
                    <ChevronDown v-else class="w-3 h-3 transition-transform" />
                  </button>
               </div>
               
               <!-- 展开后显示回复内容 -->
               <Transition name="expand">
                 <div v-if="isExpanded(q.id) && q.commentCount > 0" class="mt-4 pt-4 border-t border-slate-100 space-y-3">
                   <div class="text-sm font-medium text-shuimo mb-2">教师回复：</div>
                   <!-- 回复预览 - 由后端返回数据 -->
                   <div v-if="q.replies && q.replies.length > 0" class="space-y-2">
                     <div 
                       v-for="(reply, idx) in q.replies" 
                       :key="idx"
                       class="bg-tianlv/5 rounded-lg p-3 stagger-item"
                       :style="{ animationDelay: `${idx * 0.1}s` }"
                     >
                       <div class="flex items-center gap-2 mb-1">
                         <span class="text-xs font-medium text-tianlv">{{ reply.teacherName || '教师' }}</span>
                         <span class="text-xs text-shuimo/40">{{ reply.time }}</span>
                       </div>
                       <p class="text-sm text-shuimo/80">{{ reply.content }}</p>
                     </div>
                   </div>
                   <div v-else class="text-sm text-shuimo/50 italic">
                     回复内容加载中...
                   </div>
                 </div>
               </Transition>
               
               <!-- 等待回复提示 -->
               <Transition name="expand">
                 <div v-if="isExpanded(q.id) && getQuestionStatus(q) === 'waiting'" class="mt-4 pt-4 border-t border-slate-100">
                   <div class="bg-amber-50 rounded-lg p-3 text-sm text-amber-700 flex items-center gap-2">
                     <Clock class="w-4 h-4 animate-pulse" />
                     您的问题已提交，教师正在处理中，请耐心等待...
                   </div>
                 </div>
               </Transition>
            </div>
         </div>
       </GlassCard>

       <div v-if="questions.length === 0" class="col-span-full text-center py-20" style="animation: fade-in-up 0.5s ease-out forwards;">
          <div class="w-16 h-16 mx-auto mb-4 rounded-full bg-zijinghui/10 flex items-center justify-center empty-state-float">
            <MessageSquare class="w-8 h-8 text-zijinghui/50" />
          </div>
          <p class="text-shuimo/60 font-medium">还没有提问过</p>
          <p class="text-sm text-shuimo/40 mt-1">有问题随时问老师哦，点击上方"我要提问"按钮</p>
          <button 
            @click="openAskModal"
            class="mt-4 px-4 py-2 rounded-xl bg-zijinghui/10 text-zijinghui text-sm font-medium hover:bg-zijinghui hover:text-white transition-all btn-ripple"
          >
            立即提问
          </button>
       </div>
    </div>

    <!-- 提问弹窗 -->
    <Teleport to="body">
      <div v-if="showAskModal" class="fixed inset-0 bg-white/80 backdrop-blur-md z-[9999] flex items-center justify-center p-4">
        <GlassCard class="w-full max-w-lg p-6 animate-slide-up md:p-8 max-h-[90vh] overflow-y-auto shadow-2xl border border-slate-200">
        <div class="flex items-center justify-between mb-6">
          <h3 class="text-xl font-bold text-shuimo font-song">向老师提问</h3>
          <button @click="showAskModal = false" class="p-2 rounded-full hover:bg-slate-100 text-shuimo/50 hover:text-shuimo transition-colors">
            <X class="w-5 h-5" />
          </button>
        </div>
        
        <div class="space-y-4">
          <!-- 关联课程选择 -->
          <div>
            <label for="question-course-select" class="block text-sm font-bold text-shuimo mb-1">
              <BookOpen class="w-4 h-4 inline-block mr-1" />
              关联课程 <span class="font-normal text-shuimo/50">(可选)</span>
            </label>
            <select 
              id="question-course-select"
              name="questionCourse"
              v-model="newQuestion.courseId"
              class="w-full px-4 py-2 rounded-xl bg-slate-50 border-none focus:ring-2 focus:ring-zijinghui/20 outline-none"
            >
              <option value="">不关联课程</option>
              <option v-for="course in enrolledCourses" :key="course.id" :value="course.id">
                {{ course.title }}
              </option>
            </select>
          </div>
          
          <!-- 关联章节选择 -->
          <div v-if="newQuestion.courseId">
            <label for="question-chapter-select" class="block text-sm font-bold text-shuimo mb-1">
              <FileText class="w-4 h-4 inline-block mr-1" />
              关联章节 <span class="font-normal text-shuimo/50">(可选)</span>
            </label>
            <select 
              id="question-chapter-select"
              name="questionChapter"
              v-model="newQuestion.chapterId"
              class="w-full px-4 py-2 rounded-xl bg-slate-50 border-none focus:ring-2 focus:ring-zijinghui/20 outline-none"
              :disabled="loadingChapters"
            >
              <option value="">{{ loadingChapters ? '加载中...' : '不关联章节' }}</option>
              <option v-for="chapter in courseChapters" :key="chapter.id" :value="chapter.id">
                {{ chapter.title }}
              </option>
            </select>
          </div>
          
          <div>
            <label for="question-title-input" class="block text-sm font-bold text-shuimo mb-1">问题标题</label>
            <input 
              id="question-title-input"
              name="questionTitle"
              v-model="newQuestion.title" 
              type="text" 
              placeholder="简要描述你的问题"
              class="w-full px-4 py-2 rounded-xl bg-slate-50 border-none focus:ring-2 focus:ring-zijinghui/20 outline-none"
            />
          </div>
          <div>
            <label for="question-content-input" class="block text-sm font-bold text-shuimo mb-1">问题详情</label>
            <textarea 
              id="question-content-input"
              name="questionContent"
              v-model="newQuestion.content" 
              rows="4" 
              placeholder="详细描述你遇到的困难..."
              class="w-full px-4 py-2 rounded-xl bg-slate-50 border-none focus:ring-2 focus:ring-zijinghui/20 outline-none resize-none"
            ></textarea>
          </div>
          
          <!-- 图片上传区域 -->
          <div>
            <label for="question-image-input" class="block text-sm font-bold text-shuimo mb-2">
              <Image class="w-4 h-4 inline-block mr-1" />
              添加图片 <span class="font-normal text-shuimo/50">(可选，支持拍题上传)</span>
            </label>
            
            <!-- 隐藏文件输入框 -->
            <input 
              id="question-image-input"
              name="questionImage"
              ref="fileInputRef"
              type="file" 
              accept="image/*" 
              class="hidden" 
              @change="handleImageSelect"
            />
            
            <!-- 上传按钮 / 预览 -->
            <div v-if="!imagePreview" 
                 @click="fileInputRef?.click()"
                 class="w-full h-32 rounded-xl border-2 border-dashed border-slate-200 bg-slate-50/50 flex flex-col items-center justify-center gap-2 cursor-pointer hover:border-zijinghui/30 hover:bg-zijinghui/5 transition-colors group"
            >
              <Upload class="w-8 h-8 text-slate-300 group-hover:text-zijinghui/50 transition-colors" />
              <span class="text-sm text-slate-400 group-hover:text-zijinghui/70">点击上传图片</span>
              <span class="text-xs text-slate-300">支持 JPG、PNG，最大 5MB</span>
            </div>
            
            <!-- 图片预览 -->
            <div v-else class="relative">
              <img :src="imagePreview" class="w-full max-h-48 object-contain rounded-xl border border-slate-100" />
              <button 
                @click="removeImage" 
                class="absolute top-2 right-2 w-8 h-8 rounded-full bg-black/50 text-white flex items-center justify-center hover:bg-yanzhi transition-colors"
              >
                <Trash2 class="w-4 h-4" />
              </button>
            </div>
          </div>
          
          <div class="pt-4 flex justify-end gap-3">
            <button @click="showAskModal = false" class="px-4 py-2 rounded-xl text-shuimo/60 hover:bg-slate-100 transition-colors" :disabled="uploading">取消</button>
            <BaseButton @click="submitQuestion" variant="primary" :disabled="uploading">
              <Loader2 v-if="uploading" class="w-4 h-4 mr-2 animate-spin" />
              {{ uploading ? '上传中...' : '提交问题' }}
            </BaseButton>
          </div>
        </div>
      </GlassCard>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
/* 展开/收起动画 */
.expand-enter-active,
.expand-leave-active {
  transition: all 0.3s ease-out;
  overflow: hidden;
}

.expand-enter-from,
.expand-leave-to {
  opacity: 0;
  max-height: 0;
  transform: translateY(-10px);
}

.expand-enter-to,
.expand-leave-from {
  opacity: 1;
  max-height: 500px;
  transform: translateY(0);
}
</style>
