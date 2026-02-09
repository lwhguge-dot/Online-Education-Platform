<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import {
  Plus, FileText, X, Clock,
  ClipboardCheck, BarChart3
} from 'lucide-vue-next'
import { useToastStore } from '../../stores/toast'
import GlassCard from '../../components/ui/GlassCard.vue'
import BaseButton from '../../components/ui/BaseButton.vue'
import BaseInput from '../../components/ui/BaseInput.vue'
import BaseSelect from '../../components/ui/BaseSelect.vue'
import BaseModal from '../../components/ui/BaseModal.vue'
import GradingWorkbench from '../../components/teacher/GradingWorkbench.vue'
import HomeworkQAManagement from '../../components/teacher/HomeworkQAManagement.vue'
import ErrorReportPanel from '../../components/teacher/ErrorReportPanel.vue'
import EmptyState from '../../components/ui/EmptyState.vue'
import { homeworkAPI, chapterAPI } from '../../services/api'
import { useAuthStore } from '../../stores/auth'

const props = defineProps({
  courses: {
    type: Array,
    default: () => []
  }
})

const loading = ref(false)
const activeTab = ref('pending')
const showCreateModal = ref(false)
const showGradingMode = ref(false)
const showGradingWorkbench = ref(false)
const showQAManagement = ref(false)
const showErrorReport = ref(false)
const selectedHomework = ref(null)
const toast = useToastStore()
const authStore = useAuthStore()

// 筛选条件
const statusFilter = ref('all')
const typeFilter = ref('all')

const homeworks = ref([])
const submissions = ref([])

// 类型映射
const homeworkTypeMap = {
  objective: { label: '客观题', class: 'bg-qinghua/10 text-qinghua border-qinghua/20' },
  subjective: { label: '主观题', class: 'bg-zhizi/10 text-zhizi border-zhizi/20' },
  mixed: { label: '混合题', class: 'bg-zijinghui/10 text-zijinghui border-zijinghui/20' }
}

// 表单状态
const newHomework = ref({
  title: '',
  description: '',
  courseId: null,
  chapterId: null,
  testType: 'chapter',
  homeworkType: 'objective',
  totalScore: 100,
  deadline: '',
  questions: []
})

const newQuestion = ref({
  questionType: 'single',
  content: '',
  options: ['', '', '', ''],
  correctAnswer: '',
  answerAnalysis: '',
  score: 10
})

const selectedCourseChapters = ref([])

// 计算属性
const filteredHomeworks = computed(() => {
  let result = homeworks.value
  
  if (statusFilter.value !== 'all') {
    // 结果过滤留空：状态字段未统一定义
  }
  
  if (typeFilter.value !== 'all') {
    result = result.filter(h => h.homeworkType === typeFilter.value)
  }
  
  if (activeTab.value === 'pending') {
    result = result.filter(h => h.submissionCount < h.totalStudents || h.pending > 0)
  }
  
  return result
})

// === 业务逻辑 ===

const loadHomeworks = async () => {
  loading.value = true
  try {
    const allHomeworks = []
    // 当前逻辑依赖循环获取作业
    // 生产环境建议提供聚合接口以减少请求次数
    for (const course of props.courses) {
      const chaptersRes = await chapterAPI.getByCourse(course.id)
      if (chaptersRes.data) {
        for (const chapter of chaptersRes.data) {
          try {
            const hwRes = await homeworkAPI.getByChapter(chapter.id)
            if (hwRes.data) {
              hwRes.data.forEach(hw => {
                allHomeworks.push({
                  ...hw,
                  course: course.title,
                  chapterTitle: chapter.title,
                  totalSubmissions: hw.submissionCount || 0, // 兼容未返回字段
                  graded: hw.gradedCount || 0,
                  pending: (hw.submissionCount || 0) - (hw.gradedCount || 0)
                })
              })
            }
          } catch (e) {
            // 忽略单章节加载失败
          }
        }
      }
    }
    homeworks.value = allHomeworks
  } catch (e) {
    console.error('List homeworks failed', e)
  } finally {
    loading.value = false
  }
}

const onCourseChange = async () => {
  newHomework.value.chapterId = null
  if (!newHomework.value.courseId) {
    selectedCourseChapters.value = []
    return
  }
  try {
    const res = await chapterAPI.getByCourse(newHomework.value.courseId)
    selectedCourseChapters.value = res.data || []
  } catch (e) {
    selectedCourseChapters.value = []
  }
}

const openCreateModal = () => {
  newHomework.value = {
    title: '', description: '', courseId: null, chapterId: null,
    testType: 'chapter', homeworkType: 'objective', totalScore: 100,
    deadline: '', questions: []
  }
  showCreateModal.value = true
}

const addQuestion = () => {
  const q = { ...newQuestion.value, id: Date.now() }
  if (q.questionType !== 'subjective') {
    q.options = q.options.filter(o => o.trim() !== '')
  }
  newHomework.value.questions.push(q)
  // 重置题目表单
  newQuestion.value = {
    questionType: 'single', content: '', options: ['', '', '', ''],
    correctAnswer: '', answerAnalysis: '', score: 10
  }
}

const removeQuestion = (idx) => {
  newHomework.value.questions.splice(idx, 1)
}

const saveHomework = async () => {
  if (!newHomework.value.title || !newHomework.value.courseId) {
    toast.warning('请完善必填信息')
    return
  }
  
  try {
    const dto = {
      courseId: newHomework.value.courseId,
      chapterId: newHomework.value.testType === 'chapter' ? newHomework.value.chapterId : null,
      testType: newHomework.value.testType,
      title: newHomework.value.title,
      description: newHomework.value.description,
      homeworkType: newHomework.value.homeworkType,
      totalScore: newHomework.value.totalScore,
      deadline: newHomework.value.deadline || null,
      questions: newHomework.value.questions.map((q, idx) => ({
        questionType: q.questionType,
        content: q.content,
        options: q.questionType !== 'subjective' ? JSON.stringify(q.options) : null,
        correctAnswer: q.correctAnswer,
        answerAnalysis: q.answerAnalysis,
        score: q.score,
        sortOrder: idx + 1
      }))
    }
    
    await homeworkAPI.create(dto)
    showCreateModal.value = false
    await loadHomeworks()
    toast.success('作业发布成功')
  } catch (e) {
    toast.error('创建失败: ' + e.message)
  }
}

// 批改逻辑
const openGrading = async (homework) => {
  selectedHomework.value = homework
  showGradingWorkbench.value = true
}

const closeGradingWorkbench = () => {
  showGradingWorkbench.value = false
  selectedHomework.value = null
}

// 问答管理
const openQAManagement = (homework) => {
  selectedHomework.value = homework
  showQAManagement.value = true
}

const closeQAManagement = () => {
  showQAManagement.value = false
  selectedHomework.value = null
}

// 错题报告
const openErrorReport = (homework) => {
  selectedHomework.value = homework
  showErrorReport.value = true
}

const closeErrorReport = () => {
  showErrorReport.value = false
  selectedHomework.value = null
}

const onGraded = () => {
  // 刷新作业列表以更新批改进度
  loadHomeworks()
}

// 初始加载
onMounted(() => {
  if (props.courses.length > 0) {
    loadHomeworks()
  }
})

watch(
  () => props.courses,
  (val) => {
    if (val && val.length > 0) {
      loadHomeworks()
    }
  }
)

// 如需课程变化触发刷新，可启用下方监听
// watch(() => props.courses, loadHomeworks)

</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <!-- Toolbar -->
    <GlassCard v-if="!showGradingWorkbench" class="p-4 animate-slide-up" style="animation-fill-mode: both;">
      <div class="flex items-center justify-between gap-4 flex-wrap">
        <!-- 左侧：标题和标签页 -->
        <div class="flex items-center gap-4">
          <h3 class="text-lg font-bold text-shuimo flex items-center gap-2 font-song shrink-0">
            <ClipboardCheck class="w-5 h-5 text-qinghua" />
            作业管理
          </h3>
          <div class="flex items-center gap-2">
            <button
              v-for="tab in [{id: 'pending', label: '待批改'}, {id: 'all', label: '全部作业'}]"
              :key="tab.id"
              @click="activeTab = tab.id"
              class="px-4 py-2 rounded-xl text-sm font-medium transition-all whitespace-nowrap"
              :class="activeTab === tab.id ? 'bg-qinghua text-white shadow-lg shadow-qinghua/30' : 'bg-slate-50 text-shuimo/70 hover:bg-slate-100'"
            >
              {{ tab.label }}
            </button>
          </div>
        </div>

        <!-- 右侧：筛选和创建按钮 -->
        <div class="flex items-center gap-3">
          <div class="w-28">
            <BaseSelect 
              v-model="statusFilter" 
              :options="[
                { value: 'all', label: '全部状态' },
                { value: 'active', label: '进行中' },
                { value: 'ended', label: '已结束' }
              ]"
              size="sm"
            />
          </div>
          <div class="w-28">
            <BaseSelect 
              v-model="typeFilter" 
              :options="[
                { value: 'all', label: '全部类型' },
                { value: 'objective', label: '客观题' },
                { value: 'subjective', label: '主观题' },
                { value: 'mixed', label: '混合题' }
              ]"
              size="sm"
            />
          </div>
          <BaseButton @click="openCreateModal" icon="Plus" variant="primary">
            布置作业
          </BaseButton>
        </div>
      </div>
    </GlassCard>

    <!-- Homework List -->
    <div v-if="!showGradingWorkbench" class="grid grid-cols-1 gap-4">
      <div v-if="filteredHomeworks.length === 0" class="flex items-center justify-center py-16">
        <EmptyState icon="file" title="暂无相关作业" description="点击右上角布置新作业" />
      </div>
      
      <GlassCard 
        v-for="hw in filteredHomeworks" 
        :key="hw.id" 
        hoverable
        class="group"
      >
        <div class="flex items-start justify-between p-2">
          <div class="flex-1">
            <div class="flex items-center gap-3 mb-2">
              <h4 class="text-lg font-bold text-shuimo group-hover:text-qinghua transition-colors">{{ hw.title }}</h4>
              <span :class="['text-xs px-2 py-0.5 rounded-full border', homeworkTypeMap[hw.homeworkType]?.class]">
                {{ homeworkTypeMap[hw.homeworkType]?.label }}
              </span>
            </div>
            <p class="text-sm text-shuimo/60 mb-4 flex items-center gap-2">
              <span class="bg-slate-100 px-2 py-0.5 rounded text-xs">{{ hw.course }}</span>
              <span>·</span>
              <span v-if="hw.deadline" class="flex items-center gap-1">
                 <Clock class="w-3 h-3" /> 截止: {{ hw.deadline.replace('T', ' ') }}
              </span>
            </p>
            
            <div class="flex items-center gap-6 text-sm">
              <div class="flex flex-col">
                <span class="text-xs text-shuimo/40 uppercase">提交</span>
                <span class="font-mono font-medium text-shuimo">{{ hw.totalSubmissions }}</span>
              </div>
              <div class="flex flex-col">
                 <span class="text-xs text-shuimo/40 uppercase">已批改</span>
                 <span class="font-mono font-medium text-qingsong">{{ hw.graded }}</span>
              </div>
              <div class="flex flex-col">
                 <span class="text-xs text-shuimo/40 uppercase">待批改</span>
                 <span class="font-mono font-bold text-yanzhi">{{ hw.pending }}</span>
              </div>
            </div>
          </div>
          
          <div class="flex flex-col items-end gap-3">
             <div class="w-12 h-12 rounded-full border-4 border-slate-100 flex items-center justify-center text-xs font-bold text-shuimo/40" title="批改进度">
                {{ hw.totalSubmissions ? Math.round(hw.graded / hw.totalSubmissions * 100) : 0 }}%
             </div>
             <div class="flex gap-2">
               <BaseButton
                 @click="openErrorReport(hw)"
                 size="sm"
                 variant="secondary"
                 title="查看错题报告"
               >
                 <BarChart3 class="w-3.5 h-3.5" />
                 错题
               </BaseButton>
               <BaseButton
                 @click="openQAManagement(hw)"
                 size="sm"
                 variant="secondary"
                 title="查看学生提问"
               >
                 问答
               </BaseButton>
               <BaseButton
                 @click="openGrading(hw)"
                 size="sm"
                 :variant="hw.pending > 0 ? 'primary' : 'secondary'"
               >
                 {{ hw.pending > 0 ? '去批改' : '查看详情' }}
               </BaseButton>
             </div>
          </div>
        </div>
      </GlassCard>
    </div>

    <!-- Create Homework Modal -->
    <BaseModal v-model="showCreateModal" title="布置新作业" max-width-class="max-w-5xl">
      <div class="grid grid-cols-1 xl:grid-cols-2 gap-5">
        <div class="space-y-3 min-w-0">
          <h4 class="font-bold text-shuimo border-l-4 border-qinghua pl-2 text-sm">基本信息</h4>

          <BaseInput label="作业标题" v-model="newHomework.title" placeholder="输入标题" required />

          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="text-xs text-shuimo/70 mb-1 block">课程</label>
              <BaseSelect
                v-model="newHomework.courseId"
                @change="onCourseChange"
                :options="[{ value: null, label: '选择课程' }, ...courses.map(c => ({ value: c.id, label: c.title }))]"
                size="sm"
              />
            </div>
            <div>
              <label class="text-xs text-shuimo/70 mb-1 block">章节</label>
              <BaseSelect
                v-model="newHomework.chapterId"
                :disabled="!newHomework.courseId"
                :options="[{ value: null, label: '选择章节' }, ...selectedCourseChapters.map(ch => ({ value: ch.id, label: ch.title }))]"
                size="sm"
              />
            </div>
          </div>

          <div class="grid grid-cols-2 gap-3">
            <BaseInput label="总分" type="number" v-model="newHomework.totalScore" />
            <BaseInput label="截止时间" type="datetime-local" v-model="newHomework.deadline" />
          </div>

          <div>
            <label class="text-xs text-shuimo/70 mb-1 block">作业类型</label>
            <div class="flex gap-2">
              <button
                v-for="type in ['objective', 'subjective', 'mixed']"
                :key="type"
                @click="newHomework.homeworkType = type"
                :class="[
                  'flex-1 py-1.5 rounded-lg border text-xs',
                  newHomework.homeworkType === type
                    ? 'border-qinghua bg-qinghua/10 text-qinghua'
                    : 'border-slate-200 text-shuimo/60'
                ]"
              >
                {{ homeworkTypeMap[type].label }}
              </button>
            </div>
          </div>
        </div>

        <div class="space-y-3 bg-slate-50 p-3 rounded-xl min-w-0">
          <h4 class="font-bold text-shuimo border-l-4 border-tianlv pl-2 text-sm">添加题目</h4>

          <div class="bg-white p-3 rounded-xl shadow-sm space-y-2 min-w-0">
            <div class="flex gap-3">
              <div class="flex-1">
                <BaseSelect
                  v-model="newQuestion.questionType"
                  :options="[
                    { value: 'single', label: '📝 单选题' },
                    { value: 'multiple', label: '☑️ 多选题' },
                    { value: 'fill', label: '✏️ 填空题' },
                    { value: 'subjective', label: '📄 主观题' }
                  ]"
                  size="sm"
                />
              </div>
              <input
                type="number"
                v-model="newQuestion.score"
                class="w-16 p-2 border border-slate-200 rounded-lg text-center font-mono bg-white text-sm"
                placeholder="分值"
              />
            </div>

            <textarea
              v-model="newQuestion.content"
              placeholder="题目内容"
              class="w-full p-2 border rounded-lg h-14 text-sm resize-none"
            ></textarea>

            <div v-if="['single', 'multiple'].includes(newQuestion.questionType)" class="space-y-1">
              <div v-for="(opt, idx) in newQuestion.options" :key="idx" class="flex items-center gap-2">
                <span class="font-bold text-xs w-4">{{ String.fromCharCode(65 + idx) }}</span>
                <input v-model="newQuestion.options[idx]" class="flex-1 p-1.5 border rounded text-sm" />
              </div>
            </div>

            <div v-if="newQuestion.questionType !== 'subjective'">
              <input
                v-model="newQuestion.correctAnswer"
                placeholder="正确答案 (如: A)"
                class="w-full p-2 border rounded-lg text-sm"
              />
            </div>

            <BaseButton @click="addQuestion" block variant="secondary" size="sm">添加题目</BaseButton>
          </div>

          <div v-if="newHomework.questions.length" class="space-y-1.5 max-h-32 overflow-y-auto">
            <div
              v-for="(q, idx) in newHomework.questions"
              :key="q.id"
              class="bg-white p-2 rounded border flex justify-between items-center"
            >
              <span class="truncate flex-1 text-xs">{{ idx + 1 }}. {{ q.content }}</span>
              <button @click="removeQuestion(idx)" class="text-yanzhi"><X class="w-4 h-4" /></button>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <BaseButton @click="showCreateModal = false" variant="ghost">取消</BaseButton>
        <BaseButton @click="saveHomework" variant="primary">发布作业</BaseButton>
      </template>
    </BaseModal>

    <!-- Grading Workbench -->
    <GradingWorkbench
      v-if="showGradingWorkbench && selectedHomework"
      :homework-id="selectedHomework.id"
      @close="closeGradingWorkbench"
      @graded="onGraded"
    />

    <!-- QA Management Modal -->
    <Teleport to="body">
      <div v-if="showQAManagement && selectedHomework" class="fixed inset-0 z-[100] flex items-center justify-center p-6" @click.self="closeQAManagement">
        <!-- 背景遮罩 -->
        <div class="absolute inset-0 bg-shuimo/20 backdrop-blur-[2px]" @click="closeQAManagement"></div>
        <!-- 弹窗内容 -->
        <div class="relative bg-white rounded-2xl w-full max-w-2xl max-h-[85vh] overflow-hidden shadow-2xl border border-slate-200 animate-scale-in">
          <div class="flex justify-between items-center p-4 border-b">
            <h3 class="text-lg font-bold text-shuimo font-song">
              {{ selectedHomework.title }} - 学生问答
            </h3>
            <button @click="closeQAManagement" class="p-2 hover:bg-slate-100 rounded-lg">
              <X class="w-5 h-5 text-shuimo/60" />
            </button>
          </div>
          <div class="p-4 overflow-y-auto max-h-[calc(85vh-80px)]">
            <HomeworkQAManagement
              :homework-id="selectedHomework.id"
              :teacher-id="authStore.user?.id"
            />
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Error Report Modal -->
    <Teleport to="body">
      <div v-if="showErrorReport && selectedHomework" class="fixed inset-0 z-[100] flex items-center justify-center p-6" @click.self="closeErrorReport">
        <!-- 背景遮罩 -->
        <div class="absolute inset-0 bg-shuimo/20 backdrop-blur-[2px]" @click="closeErrorReport"></div>
        <!-- 弹窗内容 -->
        <div class="relative bg-white rounded-2xl w-full max-w-2xl max-h-[85vh] overflow-hidden shadow-2xl border border-slate-200 animate-scale-in">
          <div class="flex justify-between items-center p-5 border-b bg-gradient-to-r from-yanzhi/5 to-mudan/5">
            <div>
              <h3 class="text-lg font-bold text-shuimo font-song flex items-center gap-2">
                <BarChart3 class="w-5 h-5 text-yanzhi" />
                错题分析报告
              </h3>
              <p class="text-xs text-shuimo/50 mt-1">{{ selectedHomework.title }}</p>
            </div>
            <button @click="closeErrorReport" class="p-2 hover:bg-slate-100 rounded-lg">
              <X class="w-5 h-5 text-shuimo/60" />
            </button>
          </div>
          <div class="p-5 overflow-y-auto max-h-[calc(85vh-100px)]">
            <ErrorReportPanel
              :homework-id="selectedHomework.id"
            />
          </div>
        </div>
      </div>
    </Teleport>

  </div>
</template>
