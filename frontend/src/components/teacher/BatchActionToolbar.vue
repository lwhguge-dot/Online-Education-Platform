<script setup>
import { ref, computed } from 'vue'
import { CheckSquare, Square, Trash2, Upload, Download, Copy, X, AlertTriangle } from 'lucide-vue-next'
import { useToastStore } from '../../stores/toast'
import BaseButton from '../ui/BaseButton.vue'

const props = defineProps({
  selectedCount: { type: Number, default: 0 },
  totalCount: { type: Number, default: 0 },
  type: { type: String, default: 'course' } // 'course' | 'homework' | 'student'
})

const emit = defineEmits(['selectAll', 'clearSelection', 'batchPublish', 'batchOffline', 'batchDelete', 'export', 'duplicate'])

const toast = useToastStore()
const showConfirmModal = ref(false)
const confirmAction = ref(null)
const confirmMessage = ref('')

// 是否显示工具栏
const isVisible = computed(() => props.selectedCount > 0)

// 全选状态
const isAllSelected = computed(() => props.selectedCount === props.totalCount && props.totalCount > 0)
const isPartialSelected = computed(() => props.selectedCount > 0 && props.selectedCount < props.totalCount)

// 切换全选
const toggleSelectAll = () => {
  if (isAllSelected.value) {
    emit('clearSelection')
  } else {
    emit('selectAll')
  }
}

// 确认操作
const confirmBatchAction = (action, message) => {
  confirmAction.value = action
  confirmMessage.value = message
  showConfirmModal.value = true
}

// 执行操作
const executeAction = () => {
  if (confirmAction.value) {
    emit(confirmAction.value)
    toast.success('操作已执行')
  }
  showConfirmModal.value = false
  confirmAction.value = null
}

// 取消操作
const cancelAction = () => {
  showConfirmModal.value = false
  confirmAction.value = null
}
</script>

<template>
  <Transition
    enter-active-class="transition-all duration-300 ease-out"
    enter-from-class="opacity-0 translate-y-4"
    enter-to-class="opacity-100 translate-y-0"
    leave-active-class="transition-all duration-200 ease-in"
    leave-from-class="opacity-100 translate-y-0"
    leave-to-class="opacity-0 translate-y-4"
  >
    <div v-if="isVisible" class="fixed bottom-6 left-1/2 -translate-x-1/2 z-40">
      <div class="bg-white/95 backdrop-blur-xl rounded-2xl shadow-2xl border border-slate-200 px-6 py-4 flex items-center gap-6">
        <!-- 选择状态 -->
        <div class="flex items-center gap-3">
          <button @click="toggleSelectAll" class="p-1 hover:bg-slate-100 rounded transition-colors">
            <CheckSquare v-if="isAllSelected" class="w-5 h-5 text-tianlv" />
            <div v-else-if="isPartialSelected" class="w-5 h-5 border-2 border-tianlv rounded bg-tianlv/20"></div>
            <Square v-else class="w-5 h-5 text-shuimo/40" />
          </button>
          <span class="text-sm font-medium text-shuimo">
            已选择 <span class="text-tianlv font-bold">{{ selectedCount }}</span> 项
          </span>
        </div>
        
        <div class="h-6 w-px bg-slate-200"></div>
        
        <!-- 操作按钮 -->
        <div class="flex items-center gap-2">
          <!-- 课程操作 -->
          <template v-if="type === 'course'">
            <BaseButton 
              size="sm" 
              variant="secondary"
              @click="confirmBatchAction('batchPublish', `确定要发布选中的 ${selectedCount} 个课程吗？`)"
            >
              批量发布
            </BaseButton>
            <BaseButton 
              size="sm" 
              variant="ghost"
              @click="confirmBatchAction('batchOffline', `确定要下线选中的 ${selectedCount} 个课程吗？`)"
            >
              批量下线
            </BaseButton>
            <BaseButton 
              size="sm" 
              variant="ghost"
              @click="emit('duplicate')"
            >
              <Copy class="w-4 h-4 mr-1" />
              复制
            </BaseButton>
          </template>
          
          <!-- 作业操作 -->
          <template v-if="type === 'homework'">
            <BaseButton 
              size="sm" 
              variant="secondary"
              @click="emit('duplicate')"
            >
              <Copy class="w-4 h-4 mr-1" />
              复制作业
            </BaseButton>
          </template>
          
          <!-- 学生操作 -->
          <template v-if="type === 'student'">
            <BaseButton 
              size="sm" 
              variant="secondary"
              @click="emit('export')"
            >
              <Download class="w-4 h-4 mr-1" />
              导出数据
            </BaseButton>
          </template>
          
          <!-- 通用删除 -->
          <BaseButton 
            v-if="type !== 'student'"
            size="sm" 
            variant="ghost"
            class="text-yanzhi hover:bg-yanzhi/10"
            @click="confirmBatchAction('batchDelete', `确定要删除选中的 ${selectedCount} 项吗？此操作不可撤销。`)"
          >
            <Trash2 class="w-4 h-4 mr-1" />
            删除
          </BaseButton>
        </div>
        
        <div class="h-6 w-px bg-slate-200"></div>
        
        <!-- 取消选择 -->
        <button 
          @click="emit('clearSelection')"
          class="p-2 hover:bg-slate-100 rounded-lg transition-colors text-shuimo/60 hover:text-shuimo"
        >
          <X class="w-5 h-5" />
        </button>
      </div>
    </div>
  </Transition>
  
  <!-- 确认弹窗 -->
  <Teleport to="body">
    <div v-if="showConfirmModal" class="fixed inset-0 z-50 flex items-center justify-center">
      <div class="absolute inset-0 bg-shuimo/20 backdrop-blur-[2px]" @click="cancelAction"></div>
      <div class="relative bg-white rounded-2xl shadow-xl w-full max-w-sm mx-4 p-6">
        <div class="flex items-center gap-3 mb-4">
          <div class="p-2 bg-zhizi/10 rounded-full">
            <AlertTriangle class="w-6 h-6 text-zhizi" />
          </div>
          <h3 class="font-bold text-shuimo text-lg">确认操作</h3>
        </div>
        <p class="text-shuimo/70 mb-6">{{ confirmMessage }}</p>
        <div class="flex gap-3">
          <BaseButton block variant="ghost" @click="cancelAction">取消</BaseButton>
          <BaseButton block variant="primary" @click="executeAction">确认</BaseButton>
        </div>
      </div>
    </div>
  </Teleport>
</template>
