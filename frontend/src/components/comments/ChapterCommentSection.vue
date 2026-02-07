<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { MessageSquare, Loader2 } from 'lucide-vue-next'
import CommentSortSelector from './CommentSortSelector.vue'
import CommentInput from './CommentInput.vue'
import CommentItem from './CommentItem.vue'
import { chapterCommentAPI as commentAPI } from '../../services/api'
import { useToastStore } from '../../stores/toast'

const props = defineProps({
  chapterId: {
    type: Number,
    required: true
  },
  courseId: {
    type: Number,
    required: true
  },
  currentUser: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['comment-added', 'comment-deleted'])

const toast = useToastStore()

// 状态
const comments = ref([])
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)
const replyLoading = ref(false)
const sortType = ref('time')
const page = ref(1)
const pageSize = 20
const hasMore = ref(false)
const isMuted = ref(false)
const muteReason = ref('')

// 计算属性
const canManage = computed(() => {
  const role = props.currentUser?.role
  return role === 'teacher' || role === 'admin'
})

// 加载评论列表
const loadComments = async (reset = false) => {
  if (loading.value) return
  
  if (reset) {
    page.value = 1
    comments.value = []
  }
  
  loading.value = true
  try {
    const res = await commentAPI.getChapterComments(props.chapterId, {
      userId: props.currentUser?.id,
      sort: sortType.value,
      page: page.value,
      size: pageSize
    })
    
    if (res.code === 200 && res.data) {
      if (reset) {
        comments.value = res.data.comments || []
      } else {
        comments.value.push(...(res.data.comments || []))
      }
      total.value = res.data.total || 0
      hasMore.value = comments.value.length < total.value
    }
  } catch (e) {
    console.error('加载评论失败:', e)
  } finally {
    loading.value = false
  }
}

// 检查禁言状态
const checkMuteStatus = async () => {
  if (!props.currentUser?.id) return
  
  try {
    const res = await commentAPI.getMuteStatus(props.currentUser.id, props.courseId)
    if (res.code === 200 && res.data) {
      isMuted.value = res.data.isMuted || false
      muteReason.value = res.data.reason || ''
    }
  } catch (e) {
    console.error('检查禁言状态失败:', e)
  }
}

// 发表评论
const submitComment = async (content) => {
  if (!props.currentUser?.id || submitting.value) return
  
  submitting.value = true
  try {
    const res = await commentAPI.createComment({
      chapterId: props.chapterId,
      courseId: props.courseId,
      userId: props.currentUser.id,
      content,
      parentId: null
    })
    
    if (res.code === 200 && res.data) {
      // 添加用户信息到新评论
      const newComment = {
        ...res.data,
        userName: props.currentUser.realName || props.currentUser.username,
        userAvatar: props.currentUser.avatar,
        userRole: props.currentUser.role,
        isLiked: false,
        replies: []
      }
      comments.value.unshift(newComment)
      total.value++
      emit('comment-added', newComment)
    } else if (res.code === 403) {
      // 被禁言
      isMuted.value = true
      toast.warning(res.message || '您已被禁言')
    } else if (res.code === 400) {
      // 包含屏蔽词
      toast.warning(res.message || '评论内容包含敏感词')
    }
  } catch (e) {
    console.error('发表评论失败:', e)
    toast.error('发表评论失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

// 发表回复
const submitReply = async ({ parentId, content }) => {
  if (!props.currentUser?.id || replyLoading.value) return
  
  replyLoading.value = true
  try {
    const res = await commentAPI.createComment({
      chapterId: props.chapterId,
      courseId: props.courseId,
      userId: props.currentUser.id,
      content,
      parentId
    })
    
    if (res.code === 200 && res.data) {
      // 找到父评论并添加回复
      const parentComment = comments.value.find(c => c.id === parentId)
      if (parentComment) {
        const newReply = {
          ...res.data,
          userName: props.currentUser.realName || props.currentUser.username,
          userAvatar: props.currentUser.avatar,
          userRole: props.currentUser.role,
          isLiked: false
        }
        if (!parentComment.replies) {
          parentComment.replies = []
        }
        parentComment.replies.push(newReply)
        parentComment.replyCount = (parentComment.replyCount || 0) + 1
      }
    } else if (res.code === 403) {
      isMuted.value = true
      toast.warning(res.message || '您已被禁言')
    } else if (res.code === 400) {
      toast.warning(res.message || '评论内容包含敏感词')
    }
  } catch (e) {
    console.error('发表回复失败:', e)
    toast.error('发表回复失败，请稍后重试')
  } finally {
    replyLoading.value = false
  }
}

// 点赞/取消点赞
const handleLike = async (commentId) => {
  if (!props.currentUser?.id) {
    toast.warning('请先登录')
    return
  }
  
  try {
    const res = await commentAPI.toggleLike(commentId, props.currentUser.id)
    if (res.code === 200 && res.data) {
      // 更新评论点赞状态
      const updateLikeStatus = (list) => {
        for (const comment of list) {
          if (comment.id === commentId) {
            comment.isLiked = res.data.isLiked
            comment.likeCount = comment.likeCount + (res.data.isLiked ? 1 : -1)
            return true
          }
          if (comment.replies && updateLikeStatus(comment.replies)) {
            return true
          }
        }
        return false
      }
      updateLikeStatus(comments.value)
    }
  } catch (e) {
    console.error('点赞失败:', e)
  }
}

// 置顶/取消置顶
const handlePin = async (commentId) => {
  try {
    const res = await commentAPI.togglePin(commentId)
    if (res.code === 200) {
      // 重新加载评论列表
      await loadComments(true)
    }
  } catch (e) {
    console.error('置顶操作失败:', e)
  }
}

// 删除评论
const handleDelete = async (commentId) => {
  try {
    const res = await commentAPI.deleteComment(commentId, props.currentUser.id, canManage.value)
    if (res.code === 200) {
      // 从列表中移除评论
      const removeComment = (list) => {
        const index = list.findIndex(c => c.id === commentId)
        if (index !== -1) {
          list.splice(index, 1)
          total.value--
          return true
        }
        for (const comment of list) {
          if (comment.replies && removeComment(comment.replies)) {
            comment.replyCount = Math.max(0, (comment.replyCount || 0) - 1)
            return true
          }
        }
        return false
      }
      removeComment(comments.value)
      emit('comment-deleted', commentId)
    }
  } catch (e) {
    console.error('删除评论失败:', e)
    toast.error('删除失败，请稍后重试')
  }
}

// 禁言用户
const handleMute = async (userId) => {
  try {
    const res = await commentAPI.muteUser({
      userId,
      courseId: props.courseId,
      mutedBy: props.currentUser.id,
      reason: '发布不当言论'
    })
    if (res.code === 200) {
      toast.success('禁言成功')
    } else {
      toast.error(res.message || '禁言失败')
    }
  } catch (e) {
    console.error('禁言失败:', e)
    toast.error('禁言失败，请稍后重试')
  }
}

// 切换排序
const changeSort = (newSort) => {
  sortType.value = newSort
  loadComments(true)
}

// 加载更多
const loadMore = () => {
  if (hasMore.value && !loading.value) {
    page.value++
    loadComments()
  }
}

// 监听章节变化
watch(() => props.chapterId, () => {
  loadComments(true)
  checkMuteStatus()
})

onMounted(() => {
  loadComments(true)
  checkMuteStatus()
})
</script>

<template>
  <div class="bg-white rounded-2xl shadow-sm border border-slate-100 overflow-hidden">
    <!-- 头部 -->
    <div class="px-5 py-4 border-b border-slate-100 flex items-center justify-between">
      <div class="flex items-center gap-2">
        <MessageSquare class="w-5 h-5 text-qinghua" />
        <h3 class="font-bold text-shuimo">评论区</h3>
        <span class="text-sm text-shuimo/50">{{ total }} 条评论</span>
      </div>
      
      <CommentSortSelector 
        :current-sort="sortType" 
        @change="changeSort" 
      />
    </div>
    
    <!-- 评论输入框 -->
    <div class="px-5 py-4 border-b border-slate-100 bg-slate-50/50">
      <CommentInput
        v-if="currentUser"
        :is-muted="isMuted"
        :mute-reason="muteReason"
        :loading="submitting"
        placeholder="发表你的评论，与同学们交流学习心得..."
        @submit="submitComment"
      />
      <div v-else class="text-center py-4 text-sm text-shuimo/50">
        请先登录后发表评论
      </div>
    </div>
    
    <!-- 评论列表 -->
    <div class="px-5 py-4">
      <!-- 加载中 -->
      <div v-if="loading && comments.length === 0" class="flex items-center justify-center py-8">
        <Loader2 class="w-6 h-6 text-qinghua animate-spin" />
        <span class="ml-2 text-sm text-shuimo/50">加载中...</span>
      </div>
      
      <!-- 空状态 -->
      <div v-else-if="comments.length === 0" class="text-center py-12">
        <MessageSquare class="w-12 h-12 text-slate-200 mx-auto mb-3" />
        <p class="text-sm text-shuimo/50">暂无评论，快来发表第一条评论吧</p>
      </div>
      
      <!-- 评论列表 -->
      <div v-else class="space-y-6">
        <CommentItem
          v-for="comment in comments"
          :key="comment.id"
          :comment="comment"
          :current-user="currentUser"
          :can-manage="canManage"
          :reply-loading="replyLoading"
          @like="handleLike"
          @pin="handlePin"
          @delete="handleDelete"
          @mute="handleMute"
          @submit-reply="submitReply"
        />
        
        <!-- 加载更多 -->
        <div v-if="hasMore" class="text-center pt-4">
          <button
            @click="loadMore"
            :disabled="loading"
            class="px-6 py-2 text-sm text-qinghua hover:bg-qinghua/5 rounded-lg transition-colors disabled:opacity-50"
          >
            <Loader2 v-if="loading" class="w-4 h-4 animate-spin inline mr-1" />
            {{ loading ? '加载中...' : '加载更多' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
