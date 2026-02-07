<script setup>
import { ref, computed, onMounted, watch, onUnmounted } from 'vue'
import {
  Search, X, UserX, UserCheck, Shield, Trash2,
  MoreVertical, CheckSquare, Square, Download,
  Wifi, WifiOff, Monitor, Clock, LogOut as LogOutIcon, Users
} from 'lucide-vue-next'
import GlassCard from '../../components/ui/GlassCard.vue'
import BaseModal from '../../components/ui/BaseModal.vue'
import { userAPI, authAPI } from '../../services/api'
import { useToastStore } from '../../stores/toast'
import { useConfirmStore } from '../../stores/confirm'

const confirmStore = useConfirmStore()

const props = defineProps({
  users: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  },
  initialFilter: {
    type: String,
    default: 'all'
  }
})

const emit = defineEmits(['refresh'])

// 本地状态
const searchQuery = ref('')
const roleFilter = ref('all')
const statusFilter = ref('all')
const selectedUsers = ref([])
const toast = useToastStore()

watch(
  () => props.initialFilter,
  (val) => {
    if (val === 'disabled') statusFilter.value = 'disabled'
    else if (val === 'enabled') statusFilter.value = 'enabled'
    else statusFilter.value = 'all'

    selectedUsers.value = []
  },
  { immediate: true }
)

// 在线状态
const onlineUserIds = ref(new Set())
const loadingOnlineStatus = ref(false)

// 会话弹窗状态
const showSessionModal = ref(false)
const selectedUserForSession = ref(null)
const userSessions = ref([])
const loadingSessions = ref(false)

const closeSessionModal = () => {
  showSessionModal.value = false
  selectedUserForSession.value = null
  userSessions.value = []
}

// 导出状态
const exporting = ref(false)

// 映射配置
const roleMap = {
  admin: { label: '管理员', class: 'bg-purple-100 text-purple-600', icon: Shield },
  teacher: { label: '教师', class: 'bg-emerald-100 text-emerald-600', icon: UserCheck },
  student: { label: '学生', class: 'bg-blue-100 text-blue-600', icon: null }
}

const statusMap = {
  1: { label: '正常', class: 'bg-emerald-50 text-emerald-600 border-emerald-200' },
  0: { label: '禁用', class: 'bg-slate-100 text-slate-500 border-slate-200' }
}

// 获取在线状态
const fetchOnlineStatus = async () => {
  loadingOnlineStatus.value = true
  try {
    const res = await userAPI.getOnlineStatus()
    if (res.data && Array.isArray(res.data)) {
      onlineUserIds.value = new Set(res.data)
    }
  } catch (e) {
    console.error('Failed to fetch online status:', e)
  } finally {
    loadingOnlineStatus.value = false
  }
}

// 判断是否在线
const isUserOnline = (userId) => onlineUserIds.value.has(userId)

// 获取用户会话
const fetchUserSessions = async (user) => {
  selectedUserForSession.value = user
  showSessionModal.value = true
  loadingSessions.value = true
  userSessions.value = []
  try {
    const res = await userAPI.getSessions(user.id)
    userSessions.value = res.data || []
  } catch (e) {
    toast.error('获取会话信息失败')
  } finally {
    loadingSessions.value = false
  }
}

// 强制下线用户
const forceLogoutUser = async (user) => {
  const confirmed = await confirmStore.show({
    title: '强制下线',
    message: `确定要强制下线用户 "${user.username}" 吗？该用户的所有会话将被终止。`,
    type: 'warning',
    confirmText: '确定下线',
    cancelText: '取消'
  })
  if (!confirmed) return
  try {
    await authAPI.forceLogout(user.id)
    toast.success(`用户 ${user.username} 已被强制下线`)
    // 刷新在线状态
    await fetchOnlineStatus()
    // 如果弹窗打开则同步关闭
    if (showSessionModal.value && selectedUserForSession.value?.id === user.id) {
      showSessionModal.value = false
    }
  } catch (e) {
    toast.error('强制下线失败')
  }
}

// 导出用户 CSV
const exportUsers = async () => {
  exporting.value = true
  try {
    await userAPI.exportCSV()
    toast.success('用户数据导出成功')
  } catch (e) {
    toast.error('导出失败')
  } finally {
    exporting.value = false
  }
}

// 过滤后的用户列表
const filteredUsers = computed(() => {
  let result = props.users
  
  if (roleFilter.value !== 'all') {
    result = result.filter(u => u.role === roleFilter.value)
  }
  
  if (statusFilter.value !== 'all') {
    const statusVal = statusFilter.value === 'enabled' ? 1 : 0
    result = result.filter(u => u.status === statusVal)
  }
  
  if (searchQuery.value.trim()) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(u => 
      (u.username && u.username.toLowerCase().includes(query)) ||
      (u.name && u.name.toLowerCase().includes(query)) ||
      (u.email && u.email.toLowerCase().includes(query))
    )
  }
  
  return result
})

// 选择逻辑
const isAllSelected = computed(() => {
  return filteredUsers.value.length > 0 && selectedUsers.value.length === filteredUsers.value.length
})

const toggleSelectAll = () => {
  if (isAllSelected.value) {
    selectedUsers.value = []
  } else {
    selectedUsers.value = filteredUsers.value.map(u => u.id)
  }
}

const toggleSelectUser = (id) => {
  const index = selectedUsers.value.indexOf(id)
  if (index > -1) {
    selectedUsers.value.splice(index, 1)
  } else {
    selectedUsers.value.push(id)
  }
}

// 操作方法
const getCurrentUser = () => {
  try {
    const userStr = sessionStorage.getItem('user')
    return userStr ? JSON.parse(userStr) : null
  } catch { return null }
}

const toggleUserStatus = async (user) => {
  const newStatus = user.status === 1 ? 0 : 1
  const actionText = newStatus === 1 ? '启用' : '禁用'
  const confirmed = await confirmStore.show({
    title: `${actionText}用户`,
    message: `确定要${actionText}用户 "${user.username}" 吗？`,
    type: newStatus === 1 ? 'info' : 'warning',
    confirmText: `确定${actionText}`,
    cancelText: '取消'
  })
  if (!confirmed) return
  const currentUser = getCurrentUser()
  try {
    await userAPI.updateStatus(user.id, newStatus, currentUser?.id, currentUser?.username)
    emit('refresh')
    toast.success(`用户${newStatus === 1 ? '已启用' : '已禁用'}`)
  } catch (e) {
    toast.error('操作失败')
  }
}

const deleteConfirmUser = ref(null)
const showDeleteModal = ref(false)

const confirmDeleteUser = (user) => {
  deleteConfirmUser.value = user
  showDeleteModal.value = true
}

const deleteUser = async () => {
  if (!deleteConfirmUser.value) return
  const user = deleteConfirmUser.value
  const currentUser = getCurrentUser()
  try {
    await userAPI.deleteUser(user.id, currentUser?.id, currentUser?.username)
    emit('refresh')
    toast.success('用户已删除')
  } catch (e) {
    toast.error('删除失败')
  } finally {
    showDeleteModal.value = false
    deleteConfirmUser.value = null
  }
}

const cancelDelete = () => {
  showDeleteModal.value = false
  deleteConfirmUser.value = null
}

const batchAction = async (action) => {
  if (selectedUsers.value.length === 0) return
  
  const status = action === 'enable' ? 1 : 0
  const confirmMsg = action === 'enable' ? '启用' : '禁用'
  
  const confirmed = await confirmStore.show({
    title: `批量${confirmMsg}用户`,
    message: `确定要批量${confirmMsg} ${selectedUsers.value.length} 个用户吗？`,
    type: action === 'enable' ? 'info' : 'warning',
    confirmText: `确定${confirmMsg}`,
    cancelText: '取消'
  })
  if (!confirmed) return
  
  for (const id of selectedUsers.value) {
    try {
      await userAPI.updateStatus(id, status)
    } catch (e) {}
  }
  selectedUsers.value = []
  emit('refresh')
}

// 生命周期
onMounted(() => {
  fetchOnlineStatus()
})

// 用户列表变化时刷新在线状态
watch(() => props.users, () => {
  fetchOnlineStatus()
}, { deep: true })

// 每 30 秒自动刷新在线状态
let onlineStatusTimer = null
onMounted(() => {
  onlineStatusTimer = setInterval(fetchOnlineStatus, 30000)
})
onUnmounted(() => {
  if (onlineStatusTimer) clearInterval(onlineStatusTimer)
})
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <!-- 顶部工具栏 - 单行布局 -->
    <GlassCard class="p-4">
      <div class="flex items-center justify-between gap-4">
        <!-- 左侧：标题和标签页 -->
        <div class="flex items-center gap-4 shrink-0">
          <h3 class="text-lg font-bold text-shuimo flex items-center gap-2 font-song shrink-0">
            <Users class="w-5 h-5 text-zijinghui" />
            用户管理
          </h3>
          <div class="flex items-center gap-1">
            <button
              v-for="role in [{id: 'all', label: '全部'}, {id: 'admin', label: '管理员'}, {id: 'teacher', label: '教师'}, {id: 'student', label: '学生'}]"
              :key="role.id"
              @click="roleFilter = role.id"
              class="px-3 py-1.5 rounded-lg text-sm font-medium transition-all whitespace-nowrap"
              :class="roleFilter === role.id ? 'bg-zijinghui text-white shadow-md shadow-zijinghui/20' : 'text-shuimo/60 hover:text-shuimo hover:bg-slate-100'"
            >
              {{ role.label }}
            </button>
          </div>
        </div>

        <!-- 右侧：搜索、筛选和操作按钮 -->
        <div class="flex items-center gap-3 shrink-0">
          <!-- 状态筛选 -->
          <select 
            id="user-status-filter"
            v-model="statusFilter"
            aria-label="账号状态筛选"
            class="px-3 py-2 rounded-xl border border-slate-200 bg-white/50 text-sm focus:ring-2 focus:ring-zijinghui/20 outline-none"
          >
            <option value="all">全部状态</option>
            <option value="enabled">正常</option>
            <option value="disabled">禁用</option>
          </select>
          
          <!-- 搜索框 -->
          <div class="relative group">
            <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-shuimo/40 transition-colors group-focus-within:text-zijinghui" />
            <input 
              id="user-search-input"
              v-model="searchQuery"
              type="text" 
              placeholder="搜索用户..."
              aria-label="搜索用户名、姓名或邮箱"
              class="w-40 pl-9 pr-3 py-2 rounded-xl bg-slate-50 border-none focus:ring-2 focus:ring-zijinghui/20 transition-all text-sm"
            />
            <button v-if="searchQuery" @click="searchQuery = ''" class="absolute right-3 top-1/2 -translate-y-1/2 text-shuimo/40 hover:text-shuimo">
              <X class="w-3 h-3" />
            </button>
          </div>
          
          <!-- 批量操作按钮 -->
          <template v-if="selectedUsers.length > 0">
            <div class="w-px h-6 bg-slate-200"></div>
            <button @click="batchAction('enable')" class="px-3 py-2 rounded-xl bg-emerald-50 text-emerald-600 text-sm font-medium hover:bg-emerald-100 transition-colors whitespace-nowrap">
              启用选中
            </button>
            <button @click="batchAction('disable')" class="px-3 py-2 rounded-xl bg-slate-100 text-slate-600 text-sm font-medium hover:bg-slate-200 transition-colors whitespace-nowrap">
              禁用选中
            </button>
          </template>
          
          <!-- 导出按钮 -->
          <button 
            v-if="selectedUsers.length === 0"
            @click="exportUsers"
            :disabled="exporting"
            class="px-4 py-2 rounded-xl bg-qinghua/10 text-qinghua text-sm font-medium hover:bg-qinghua/20 transition-colors flex items-center gap-2 disabled:opacity-50"
          >
            <Download class="w-4 h-4" />
            {{ exporting ? '导出中...' : '导出' }}
          </button>
        </div>
      </div>
    </GlassCard>

    <!-- Table -->
    <GlassCard class="overflow-hidden">
      <div class="overflow-x-auto min-h-[400px]">
        <table class="w-full text-left border-collapse">
          <thead>
            <tr class="text-sm font-bold text-shuimo/60 border-b border-slate-100 bg-slate-50/50">
              <th class="p-4 w-12 text-center">
                <button @click="toggleSelectAll" class="text-shuimo/40 hover:text-zijinghui" title="全选">
                   <component :is="isAllSelected ? CheckSquare : Square" class="w-5 h-5" />
                </button>
              </th>
              <th class="p-4 pl-0">用户</th>
              <th class="p-4">角色</th>
              <th class="p-4">在线状态</th>
              <th class="p-4">账号状态</th>
              <th class="p-4">最后登录</th>
              <th class="p-4 text-right pr-6">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-50">
            <template v-if="filteredUsers.length > 0">
              <tr v-for="user in filteredUsers" :key="user.id" 
                class="group hover:bg-slate-50/80 transition-colors"
                :class="{'bg-zijinghui/5': selectedUsers.includes(user.id)}"
              >
                <td class="p-4 text-center">
                   <button @click="toggleSelectUser(user.id)" 
                     :class="['transition-colors', selectedUsers.includes(user.id) ? 'text-zijinghui' : 'text-slate-300 hover:text-slate-400']"
                   >
                     <component :is="selectedUsers.includes(user.id) ? CheckSquare : Square" class="w-5 h-5" />
                   </button>
                </td>
                <td class="p-4 pl-0">
                  <div class="flex items-center gap-3">
                    <div class="relative">
                      <div class="w-10 h-10 rounded-full flex items-center justify-center text-white text-sm font-bold shadow-sm ring-2 ring-white"
                        :class="user.role === 'admin' ? 'bg-gradient-to-br from-zijinghui to-qianniuzi' : 'bg-gradient-to-br from-tianlv to-qingsong'"
                      >
                        {{ user.username?.charAt(0).toUpperCase() }}
                      </div>
                      <!-- 在线状态指示点 -->
                      <span 
                        class="absolute -bottom-0.5 -right-0.5 w-3.5 h-3.5 rounded-full border-2 border-white"
                        :class="isUserOnline(user.id) ? 'bg-emerald-500' : 'bg-slate-300'"
                        :title="isUserOnline(user.id) ? '在线' : '离线'"
                      ></span>
                    </div>
                    <div>
                      <p class="font-bold text-shuimo text-sm">{{ user.username }}</p>
                    </div>
                  </div>
                </td>
                <td class="p-4">
                  <div class="flex items-center gap-2">
                     <span class="px-2.5 py-1 rounded-lg text-xs font-bold flex items-center gap-1.5"
                       :class="roleMap[user.role]?.class || 'bg-slate-100 text-slate-500'"
                     >
                       <component :is="roleMap[user.role]?.icon" class="w-3.5 h-3.5" v-if="roleMap[user.role]?.icon" />
                       {{ roleMap[user.role]?.label || user.role }}
                     </span>
                  </div>
                </td>
                <td class="p-4">
                  <button 
                    @click="fetchUserSessions(user)"
                    class="flex items-center gap-2 px-2.5 py-1 rounded-lg text-xs font-medium transition-colors hover:bg-slate-100"
                    :class="isUserOnline(user.id) ? 'text-emerald-600' : 'text-slate-400'"
                  >
                    <component :is="isUserOnline(user.id) ? Wifi : WifiOff" class="w-3.5 h-3.5" />
                    {{ isUserOnline(user.id) ? '在线' : '离线' }}
                  </button>
                </td>
                <td class="p-4">
                  <span class="px-2.5 py-1 rounded-full text-xs font-bold border"
                    :class="statusMap[user.status]?.class"
                  >
                    {{ statusMap[user.status]?.label }}
                  </span>
                </td>
                <td class="p-4 text-sm text-shuimo/60 font-mono">
                  {{ user.lastLoginAt ? new Date(user.lastLoginAt).toLocaleString() : '从未登录' }}
                </td>
                <td class="p-4 text-right pr-6">
                   <div class="flex items-center justify-end gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                      <!-- 强制下线按钮 -->
                      <button 
                        v-if="isUserOnline(user.id)"
                        @click="forceLogoutUser(user)"
                        class="p-2 rounded-lg hover:bg-amber-50 text-amber-500 hover:text-amber-600 transition-colors"
                        title="强制下线"
                      >
                         <LogOutIcon class="w-4 h-4" />
                      </button>
                      <button 
                        @click="toggleUserStatus(user)"
                        class="p-2 rounded-lg hover:bg-slate-100 transition-colors"
                        :title="user.status === 1 ? '禁用用户' : '启用用户'"
                        :class="user.status === 1 ? 'text-slate-400 hover:text-yanzhi' : 'text-emerald-500 hover:bg-emerald-50'"
                      >
                         <component :is="user.status === 1 ? UserX : UserCheck" class="w-4 h-4" />
                      </button>
                      <button 
                        @click="confirmDeleteUser(user)"
                        class="p-2 rounded-lg hover:bg-red-50 text-slate-400 hover:text-red-500 transition-colors"
                        title="删除用户"
                      >
                         <Trash2 class="w-4 h-4" />
                      </button>
                   </div>
                </td>
              </tr>
            </template>
            <tr v-else>
               <td colspan="7" class="py-16 text-center">
                  <div class="flex flex-col items-center">
                    <div class="w-16 h-16 rounded-full bg-slate-50 flex items-center justify-center mb-4">
                      <Users class="w-8 h-8 text-slate-300" />
                    </div>
                    <p class="text-shuimo/60 font-medium mb-2">暂无符合条件的用户</p>
                    <button @click="searchQuery = ''; roleFilter = 'all'; statusFilter = 'all'" class="text-zijinghui text-sm hover:underline">清除筛选</button>
                  </div>
               </td>
            </tr>
          </tbody>
        </table>
      </div>
      
      <!-- 分页区域 -->
      <div class="p-4 border-t border-slate-100 flex justify-between items-center text-sm text-shuimo/50 bg-slate-50/50">
         <span>显示 {{ filteredUsers.length > 0 ? 1 : 0 }} - {{ filteredUsers.length }} 共 {{ filteredUsers.length }} 条</span>
         <div class="flex gap-2">
            <button class="px-3 py-1 rounded-lg border border-slate-200 bg-white disabled:opacity-50" disabled>上一页</button>
            <button class="px-3 py-1 rounded-lg border border-slate-200 bg-white disabled:opacity-50" disabled>下一页</button>
         </div>
      </div>
    </GlassCard>

    <!-- 会话详情弹窗 -->
    <BaseModal v-model="showSessionModal" max-width-class="max-w-lg" @close="closeSessionModal">
      <template #header>
        <div class="flex items-center gap-3">
          <div
            class="w-10 h-10 rounded-full flex items-center justify-center text-white text-sm font-bold"
            :class="selectedUserForSession?.role === 'admin' ? 'bg-gradient-to-br from-zijinghui to-qianniuzi' : 'bg-gradient-to-br from-tianlv to-qingsong'"
          >
            {{ selectedUserForSession?.username?.charAt(0).toUpperCase() }}
          </div>
          <div>
            <h3 class="font-bold text-shuimo">{{ selectedUserForSession?.username }}</h3>
            <p class="text-xs text-shuimo/50">会话详情</p>
          </div>
        </div>
      </template>

      <div class="overflow-y-auto max-h-[50vh]">
        <div v-if="loadingSessions" class="py-12 text-center text-shuimo/50">
          <div class="w-8 h-8 border-2 border-zijinghui/20 border-t-zijinghui rounded-full animate-spin mx-auto mb-3"></div>
          加载中...
        </div>
        <div v-else-if="userSessions.length === 0" class="py-12 text-center text-shuimo/50">
          <Monitor class="w-12 h-12 mx-auto mb-3 opacity-30" />
          <p>暂无会话记录</p>
        </div>
        <div v-else class="space-y-3">
          <div
            v-for="(session, index) in userSessions"
            :key="index"
            class="p-4 rounded-xl border border-slate-100 hover:border-slate-200 transition-colors"
            :class="session.active ? 'bg-emerald-50/50' : 'bg-slate-50/50'"
          >
            <div class="flex items-start justify-between gap-4">
              <div class="flex items-center gap-3">
                <div class="p-2 rounded-lg" :class="session.active ? 'bg-emerald-100 text-emerald-600' : 'bg-slate-100 text-slate-400'">
                  <Monitor class="w-5 h-5" />
                </div>
                <div>
                  <p class="font-medium text-sm text-shuimo">{{ session.deviceInfo || '未知设备' }}</p>
                  <p class="text-xs text-shuimo/50 flex items-center gap-1 mt-0.5">
                    <Clock class="w-3 h-3" />
                    {{ session.loginTime ? new Date(session.loginTime).toLocaleString() : '未知时间' }}
                  </p>
                </div>
              </div>
              <span
                class="px-2 py-0.5 rounded-full text-xs font-medium"
                :class="session.active ? 'bg-emerald-100 text-emerald-600' : 'bg-slate-100 text-slate-500'"
              >
                {{ session.active ? '活跃' : '已过期' }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <button
          v-if="isUserOnline(selectedUserForSession?.id)"
          @click="forceLogoutUser(selectedUserForSession)"
          class="px-4 py-2 rounded-xl bg-amber-500 text-white text-sm font-medium hover:bg-amber-600 transition-colors flex items-center gap-2"
        >
          <LogOutIcon class="w-4 h-4" />
          强制下线
        </button>
        <button
          @click="closeSessionModal"
          class="px-4 py-2 rounded-xl bg-slate-100 text-shuimo text-sm font-medium hover:bg-slate-200 transition-colors"
        >
          关闭
        </button>
      </template>
    </BaseModal>
  </div>

  <!-- 删除确认模态框 -->
  <BaseModal v-model="showDeleteModal" title="确认删除用户" max-width-class="max-w-md" @close="cancelDelete">
    <div class="text-center">
      <div class="w-16 h-16 mx-auto mb-4 rounded-full bg-red-100 flex items-center justify-center">
        <Trash2 class="w-8 h-8 text-red-500" />
      </div>
      <p class="text-shuimo/60">
        确定要删除用户 <span class="font-bold text-shuimo">"{{ deleteConfirmUser?.username }}"</span> 吗？<br>
        <span class="text-red-500 text-sm">此操作不可恢复！</span>
      </p>
    </div>

    <template #footer>
      <button
        @click="cancelDelete"
        class="px-6 py-2.5 rounded-xl border border-slate-200 text-shuimo/70 font-medium hover:bg-slate-50 transition-colors"
      >
        取消
      </button>
      <button
        @click="deleteUser"
        class="px-6 py-2.5 rounded-xl bg-red-500 text-white font-medium hover:bg-red-600 transition-colors"
      >
        确认删除
      </button>
    </template>
  </BaseModal>
</template>
