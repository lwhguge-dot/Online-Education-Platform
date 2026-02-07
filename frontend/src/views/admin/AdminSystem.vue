<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import {
  Shield, Database, Activity, HardDrive, Trash2,
  Users, BookOpen, BarChart3, Settings, Play,
  Check, X
} from 'lucide-vue-next'
import GlassCard from '../../components/ui/GlassCard.vue'
import BaseButton from '../../components/ui/BaseButton.vue'
import { useAuthStore } from '../../stores/auth'
import { useConfirmStore } from '../../stores/confirm'
import { useRouter } from 'vue-router'
import { healthAPI } from '../../services/api'

const router = useRouter()
const authStore = useAuthStore()
const confirmStore = useConfirmStore()

// 系统运行时长
const uptime = ref('...')
const bootTime = ref(null)
let timer = null

onMounted(() => {
  checkHealth()
  
  // 启动本地校准计时器
  timer = setInterval(() => {
    if (!bootTime.value) return
    const diff = Math.floor((new Date() - new Date(bootTime.value)) / 1000)
    if (diff < 60) uptime.value = diff + '秒'
    else if (diff < 3600) uptime.value = Math.floor(diff / 60) + '分钟'
    else uptime.value = Math.floor(diff / 3600) + '小时 ' + Math.floor((diff % 3600) / 60) + '分钟'
  }, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

// 服务健康状态
const services = ref([
  { id: 'frontend', name: '前端服务', status: 'up', message: '运行正常 (Node/Vite)', icon: Activity },
  { id: 'gateway', name: 'API 网关 (User)', status: 'checking', message: '检查中...', icon: Shield },
  { id: 'db', name: '后端 MySQL', status: 'checking', message: '检查中...', icon: Database },
])

const checkHealth = async () => {
  try {
    const res = await healthAPI.check()
    if (res.code === 200 && res.data) {
       bootTime.value = res.data.boot_time
       services.value[1].status = 'up'
       services.value[1].message = `在线 (Port: 8090)`
       services.value[2].status = res.data.database === 'UP' ? 'up' : 'down'
       services.value[2].message = res.data.database === 'UP' ? '连接成功 (edu_platform)' : '连接中断'
    }
  } catch (e) {
    services.value[1].status = 'down'
    services.value[1].message = '后端服务无法连接'
    services.value[2].status = 'unknown'
    services.value[2].message = '无法检测'
  }
}

const getStatusColor = (status) => {
  switch(status) {
    case 'up': return 'text-emerald-500 bg-emerald-50'
    case 'down': return 'text-red-500 bg-red-50'
    default: return 'text-amber-500 bg-amber-50'
  }
}

// 缓存与存储
const clearCache = async () => {
  const confirmed = await confirmStore.show({
    title: '清除缓存',
    message: '确定要清除本地缓存吗？清除后需要重新登录。',
    type: 'warning',
    confirmText: '确定清除',
    cancelText: '取消'
  })
  if (confirmed) {
    try {
      // 调用后端API更新会话状态
      const { authAPI } = await import('../../services/api')
      await authAPI.logout()
    } catch (e) {
      console.error('登出API调用失败:', e)
    }
    localStorage.clear()
    sessionStorage.clear()
    authStore.logout()
    router.push('/login')
  }
}

// 权限矩阵
const roles = [
  { id: 'admin', label: '管理员' },
  { id: 'teacher', label: '教师' },
  { id: 'student', label: '学生' }
]

const permissions = [
  { module: '用户管理', icon: Users, admin: true, teacher: false, student: false },
  { module: '课程管理', icon: BookOpen, admin: true, teacher: 'partial', student: false },
  { module: '课程学习', icon: Play, admin: true, teacher: true, student: true },
  { module: '统计分析', icon: BarChart3, admin: true, teacher: 'partial', student: false },
  { module: '系统设置', icon: Settings, admin: true, teacher: false, student: false }
]

const getPermissionIcon = (val) => {
  if (val === true) return { icon: Check, class: 'text-emerald-500' }
  if (val === 'partial') return { icon: Activity, class: 'text-amber-500' }
  return { icon: X, class: 'text-slate-300' }
}
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <!-- 第一行：系统状态与维护 -->
    <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
      <GlassCard class="p-6">
        <h3 class="flex items-center gap-2 text-lg font-bold text-shuimo mb-6 font-song">
          <Activity class="w-5 h-5 text-zijinghui" /> 系统状态
        </h3>
        <div class="space-y-4">
          <div v-for="svc in services" :key="svc.id" class="flex items-center justify-between p-3 rounded-xl border border-slate-100 bg-slate-50/50">
            <div class="flex items-center gap-3">
              <div class="w-10 h-10 rounded-lg flex items-center justify-center" :class="getStatusColor(svc.status)">
                <component :is="svc.icon" class="w-5 h-5" />
              </div>
              <div>
                <p class="font-bold text-shuimo text-sm">{{ svc.name }}</p>
                <p class="text-xs text-shuimo/50">{{ svc.message }}</p>
              </div>
            </div>
            <div class="flex items-center gap-2">
              <div class="relative flex items-center justify-center w-3 h-3">
                <span v-if="svc.status === 'up'" class="animate-ripple ripple-1 bg-emerald-400"></span>
                <span v-if="svc.status === 'up'" class="animate-ripple ripple-2 bg-emerald-400"></span>
                <span v-if="svc.status === 'up'" class="animate-ripple ripple-3 bg-emerald-400"></span>
                <span class="relative w-2 h-2 rounded-full" 
                  :class="svc.status === 'up' ? 'bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.5)]' : (svc.status === 'down' ? 'bg-red-500' : 'bg-amber-500')">
                </span>
              </div>
              <span class="text-xs font-bold uppercase transition-colors" 
                :class="svc.status === 'up' ? 'text-emerald-600' : 'text-shuimo/50'">
                {{ svc.status }}
              </span>
            </div>
          </div>
        </div>
      </GlassCard>

      <GlassCard class="p-6">
        <h3 class="flex items-center gap-2 text-lg font-bold text-shuimo mb-6 font-song">
          <Settings class="w-5 h-5 text-zijinghui" /> 系统维护
        </h3>
        <div class="grid grid-cols-1 gap-4">
           <div class="p-4 rounded-xl bg-slate-50 border border-slate-100">
              <div class="flex items-center gap-3 mb-2">
                 <HardDrive class="w-5 h-5 text-slate-500" />
                 <span class="font-bold text-shuimo">系统运行时间</span>
              </div>
              <p class="text-2xl font-mono text-slate-700 pl-8">{{ uptime }}</p>
           </div>
           
           <button @click="clearCache" class="flex items-center justify-between p-4 rounded-xl bg-slate-50 border border-slate-100 hover:bg-red-50 hover:border-red-100 group transition-all text-left">
              <div class="flex items-center gap-3">
                 <Trash2 class="w-5 h-5 text-slate-500 group-hover:text-red-500 transition-colors" />
                 <div>
                    <p class="font-bold text-shuimo group-hover:text-red-600 transition-colors">清除缓存</p>
                    <p class="text-xs text-shuimo/50 group-hover:text-red-400">清理本地存储和临时文件</p>
                 </div>
              </div>
              <div class="w-8 h-8 rounded-full bg-white flex items-center justify-center group-hover:bg-red-200 transition-colors">
                 <X class="w-4 h-4 text-slate-300 group-hover:text-red-500" />
              </div>
           </button>
        </div>
      </GlassCard>
    </div>

    <!-- 第二行：权限矩阵 -->
    <GlassCard class="p-6">
       <h3 class="flex items-center gap-2 text-lg font-bold text-shuimo mb-6 font-song">
          <Shield class="w-5 h-5 text-zijinghui" /> 权限预览
       </h3>
       
       <div class="overflow-x-auto">
          <table class="w-full text-center">
             <thead>
                <tr class="text-sm font-bold text-shuimo/60 border-b border-slate-100">
                   <th class="p-4 text-left font-normal">功能模块</th>
                   <th v-for="role in roles" :key="role.id" class="p-4 w-32">
                      {{ role.label }}
                   </th>
                </tr>
             </thead>
             <tbody class="divide-y divide-slate-50">
                <tr v-for="perm in permissions" :key="perm.module" class="hover:bg-slate-50/50 transition-colors">
                   <td class="p-4 text-left flex items-center gap-2 font-bold text-shuimo">
                      <div class="w-8 h-8 rounded-lg bg-slate-100 flex items-center justify-center text-slate-500">
                         <component :is="perm.icon" class="w-4 h-4" />
                      </div>
                      {{ perm.module }}
                   </td>
                   <td v-for="role in roles" :key="role.id" class="p-4">
                      <div class="flex justify-center">
                         <component 
                           :is="getPermissionIcon(perm[role.id]).icon" 
                           class="w-5 h-5" 
                           :class="getPermissionIcon(perm[role.id]).class" 
                         />
                      </div>
                   </td>
                </tr>
             </tbody>
          </table>
       </div>
    </GlassCard>
  </div>
</template>
