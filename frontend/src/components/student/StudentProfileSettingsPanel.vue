<script setup>
import { Bell, Target } from 'lucide-vue-next'
import GlassCard from '../ui/GlassCard.vue'

const props = defineProps({
  notificationSettings: { type: Object, default: () => ({}) },
  studyGoal: { type: Object, default: () => ({}) }
})

const getNotificationLabel = (key) => {
  if (key === 'homeworkReminder') return '作业提醒'
  if (key === 'courseUpdate') return '课程更新'
  if (key === 'teacherReply') return '老师回复'
  if (key === 'systemNotice') return '系统公告'
  if (key === 'emailNotify') return '邮件通知'
  return '推送通知'
}

const toggleNotification = (key, value) => {
  // 保留原有“直接修改配置对象”的行为，避免改变父级状态联动路径
  props.notificationSettings[key] = !value
}
</script>

<template>
  <div class="space-y-6">
    <!-- 消息通知 -->
    <GlassCard class="p-6 card-hover-glow" style="animation: fade-in-up var(--motion-duration-medium) var(--motion-ease-standard) 0.2s forwards; opacity: 0;">
      <h3 class="text-lg font-bold text-shuimo mb-6 flex items-center gap-2 font-song">
        <Bell class="w-5 h-5 text-zhizi icon-hover-rotate" />
        消息通知
      </h3>
      <div class="space-y-4">
        <div v-for="(val, key) in notificationSettings" :key="key" class="flex items-center justify-between group">
          <span class="text-sm text-shuimo group-hover:text-shuimo/80 transition-colors">
            {{ getNotificationLabel(key) }}
          </span>
          <button
            class="w-11 h-6 rounded-full transition-all duration-300 relative shadow-inner switch-enhanced"
            :class="val ? 'bg-gradient-to-r from-qinghua to-halanzi' : 'bg-slate-200'"
            @click="toggleNotification(key, val)"
          >
            <div class="absolute top-1 left-1 w-4 h-4 rounded-full bg-white shadow-md transition-all duration-300" :class="val ? 'translate-x-5' : 'translate-x-0'"></div>
          </button>
        </div>
      </div>
    </GlassCard>

    <!-- 学习目标 -->
    <GlassCard class="p-6 card-hover-glow" style="animation: fade-in-up var(--motion-duration-medium) var(--motion-ease-standard) 0.2s forwards; opacity: 0;">
      <h3 class="text-lg font-bold text-shuimo mb-6 flex items-center gap-2 font-song">
        <Target class="w-5 h-5 text-tianlv icon-hover-rotate" />
        学习目标
      </h3>
      <div class="space-y-5">
        <div>
          <div class="flex justify-between mb-2">
            <label for="daily-minutes-range" class="text-sm text-shuimo">每日学习时长</label>
            <span class="text-sm font-bold text-tianlv number-pop">{{ studyGoal.dailyMinutes }} 分钟</span>
          </div>
          <div class="relative">
            <input
              id="daily-minutes-range"
              name="dailyMinutes"
              type="range"
              v-model="studyGoal.dailyMinutes"
              min="15"
              max="180"
              step="15"
              class="w-full h-2 bg-slate-200 rounded-full appearance-none cursor-pointer slider-tianlv slider-enhanced"
            />
          </div>
        </div>
        <div>
          <div class="flex justify-between mb-2">
            <label for="weekly-hours-range" class="text-sm text-shuimo">每周完成章节</label>
            <span class="text-sm font-bold text-tianlv number-pop">{{ studyGoal.weeklyHours }} 章</span>
          </div>
          <div class="relative">
            <input
              id="weekly-hours-range"
              name="weeklyHours"
              type="range"
              v-model="studyGoal.weeklyHours"
              min="1"
              max="20"
              class="w-full h-2 bg-slate-200 rounded-full appearance-none cursor-pointer slider-tianlv slider-enhanced"
            />
          </div>
        </div>
      </div>
    </GlassCard>
  </div>
</template>

<style scoped>
.slider-tianlv::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: linear-gradient(135deg, #2dd4bf, #14b8a6);
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(45, 212, 191, 0.4);
  transition:
    transform var(--motion-duration-base) var(--motion-ease-standard),
    box-shadow var(--motion-duration-base) var(--motion-ease-standard);
}
.slider-tianlv::-webkit-slider-thumb:hover {
  transform: scale(1.1);
  box-shadow: 0 4px 12px rgba(45, 212, 191, 0.5);
}
.slider-tianlv::-moz-range-thumb {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: linear-gradient(135deg, #2dd4bf, #14b8a6);
  cursor: pointer;
  border: none;
  box-shadow: 0 2px 8px rgba(45, 212, 191, 0.4);
}
</style>
