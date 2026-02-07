<script setup>
import { ref, computed } from 'vue'
import { ThumbsUp, MessageCircle, Pin, Trash2, VolumeX, MoreHorizontal, User } from 'lucide-vue-next'
import CommentInput from './CommentInput.vue'
import { useConfirmStore } from '../../stores/confirm'

const confirmStore = useConfirmStore()

const props = defineProps({
  comment: {
    type: Object,
    required: true
  },
  currentUser: {
    type: Object,
    default: null
  },
  canManage: {
    type: Boolean,
    default: false
  },
  isReply: {
    type: Boolean,
    default: false
  },
  replyLoading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['reply', 'like', 'pin', 'delete', 'mute', 'submit-reply'])

const showReplyInput = ref(false)
const showActions = ref(false)

const isOwnComment = computed(() => {
  return props.currentUser?.id === props.comment.userId
})

const canDelete = computed(() => {
  return isOwnComment.value || props.canManage
})

const formatTime = (dateStr) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now - date
  
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  if (diff < 604800000) return `${Math.floor(diff / 86400000)}天前`
  
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

const handleLike = () => {
  emit('like', props.comment.id)
}

const handleReply = () => {
  showReplyInput.value = !showReplyInput.value
}

const handlePin = () => {
  emit('pin', props.comment.id)
  showActions.value = false
}

const handleDelete = async () => {
  const confirmed = await confirmStore.show({
    title: '删除评论',
    message: '确定要删除这条评论吗？',
    type: 'danger',
    confirmText: '删除',
    cancelText: '取消'
  })
  if (confirmed) {
    emit('delete', props.comment.id)
  }
  showActions.value = false
}

const handleMute = async () => {
  const confirmed = await confirmStore.show({
    title: '禁言用户',
    message: `确定要禁言用户"${props.comment.userName}"吗？`,
    type: 'warning',
    confirmText: '确定禁言',
    cancelText: '取消'
  })
  if (confirmed) {
    emit('mute', props.comment.userId)
  }
  showActions.value = false
}

const submitReply = (content) => {
  emit('submit-reply', { parentId: props.comment.id, content })
  showReplyInput.value = false
}

const cancelReply = () => {
  showReplyInput.value = false
}

const getRoleLabel = (role) => {
  const labels = {
    teacher: '教师',
    admin: '管理员',
    student: ''
  }
  return labels[role] || ''
}

const getRoleClass = (role) => {
  const classes = {
    teacher: 'bg-qinghua/10 text-qinghua',
    admin: 'bg-amber-100 text-amber-700',
    student: ''
  }
  return classes[role] || ''
}
</script>

<template>
  <div 
    class="group relative"
    :class="{ 'pl-10 border-l-2 border-slate-100': isReply }"
  >
    <!-- 置顶标识 -->
    <div v-if="comment.isPinned && !isReply" class="flex items-center gap-1.5 text-xs text-amber-600 mb-2">
      <Pin class="w-3 h-3" />
      <span class="font-medium">置顶评论</span>
    </div>
    
    <div class="flex gap-3">
      <!-- 头像 -->
      <div class="flex-shrink-0">
        <div 
          class="w-9 h-9 rounded-full bg-gradient-to-br from-qinghua/20 to-halanzi/20 flex items-center justify-center overflow-hidden"
          :class="{ 'w-7 h-7': isReply }"
        >
          <img 
            v-if="comment.userAvatar" 
            :src="comment.userAvatar" 
            :alt="comment.userName"
            class="w-full h-full object-cover"
          />
          <User v-else class="w-4 h-4 text-shuimo/40" :class="{ 'w-3 h-3': isReply }" />
        </div>
      </div>
      
      <!-- 内容区 -->
      <div class="flex-1 min-w-0">
        <!-- 用户信息 -->
        <div class="flex items-center gap-2 mb-1">
          <span class="font-medium text-sm text-shuimo">{{ comment.userName }}</span>
          <span 
            v-if="getRoleLabel(comment.userRole)"
            class="px-1.5 py-0.5 rounded text-xs font-medium"
            :class="getRoleClass(comment.userRole)"
          >
            {{ getRoleLabel(comment.userRole) }}
          </span>
          <span class="text-xs text-shuimo/40">{{ formatTime(comment.createdAt) }}</span>
        </div>
        
        <!-- 评论内容 -->
        <p class="text-sm text-shuimo/80 leading-relaxed whitespace-pre-wrap break-words">
          {{ comment.content }}
        </p>
        
        <!-- 操作栏 -->
        <div class="flex items-center gap-4 mt-2">
          <!-- 点赞 -->
          <button 
            @click="handleLike"
            class="flex items-center gap-1 text-xs transition-colors"
            :class="comment.isLiked ? 'text-qinghua' : 'text-shuimo/40 hover:text-qinghua'"
          >
            <ThumbsUp class="w-3.5 h-3.5" :class="{ 'fill-current': comment.isLiked }" />
            <span v-if="comment.likeCount > 0">{{ comment.likeCount }}</span>
          </button>
          
          <!-- 回复 -->
          <button 
            v-if="!isReply"
            @click="handleReply"
            class="flex items-center gap-1 text-xs text-shuimo/40 hover:text-qinghua transition-colors"
          >
            <MessageCircle class="w-3.5 h-3.5" />
            <span v-if="comment.replyCount > 0">{{ comment.replyCount }}</span>
            <span v-else>回复</span>
          </button>
          
          <!-- 管理操作 -->
          <div v-if="canManage || canDelete" class="relative ml-auto">
            <button 
              @click="showActions = !showActions"
              class="p-1 text-shuimo/30 hover:text-shuimo/60 transition-colors opacity-0 group-hover:opacity-100"
            >
              <MoreHorizontal class="w-4 h-4" />
            </button>
            
            <!-- 下拉菜单 -->
            <Transition name="fade">
              <div 
                v-if="showActions"
                class="absolute right-0 top-full mt-1 bg-white rounded-lg shadow-lg border border-slate-100 py-1 min-w-[100px] z-10"
              >
                <button 
                  v-if="canManage && !isReply"
                  @click="handlePin"
                  class="w-full px-3 py-1.5 text-left text-sm text-shuimo/70 hover:bg-slate-50 flex items-center gap-2"
                >
                  <Pin class="w-3.5 h-3.5" />
                  {{ comment.isPinned ? '取消置顶' : '置顶' }}
                </button>
                <button 
                  v-if="canManage && !isOwnComment"
                  @click="handleMute"
                  class="w-full px-3 py-1.5 text-left text-sm text-amber-600 hover:bg-amber-50 flex items-center gap-2"
                >
                  <VolumeX class="w-3.5 h-3.5" />
                  禁言
                </button>
                <button 
                  v-if="canDelete"
                  @click="handleDelete"
                  class="w-full px-3 py-1.5 text-left text-sm text-red-500 hover:bg-red-50 flex items-center gap-2"
                >
                  <Trash2 class="w-3.5 h-3.5" />
                  删除
                </button>
              </div>
            </Transition>
          </div>
        </div>
        
        <!-- 回复输入框 -->
        <div v-if="showReplyInput" class="mt-3">
          <CommentInput
            :placeholder="`回复 ${comment.userName}...`"
            :is-reply="true"
            :loading="replyLoading"
            @submit="submitReply"
            @cancel="cancelReply"
          />
        </div>
        
        <!-- 回复列表 -->
        <div v-if="comment.replies && comment.replies.length > 0" class="mt-3 space-y-3">
          <CommentItem
            v-for="reply in comment.replies"
            :key="reply.id"
            :comment="reply"
            :current-user="currentUser"
            :can-manage="canManage"
            :is-reply="true"
            @like="$emit('like', $event)"
            @delete="$emit('delete', $event)"
            @mute="$emit('mute', $event)"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
