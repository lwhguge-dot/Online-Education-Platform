<script setup>
import StudentProfileInfoCard from '../../components/student/StudentProfileInfoCard.vue'
import StudentProfileSettingsPanel from '../../components/student/StudentProfileSettingsPanel.vue'

const props = defineProps({
  userId: { type: [Number, String], default: null },
  profile: { type: Object, default: () => ({}) },
  notificationSettings: { type: Object, default: () => ({}) },
  studyGoal: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['save'])

const handleProfileSave = (payload) => {
  // 视图层仅做事件透传，保持原有保存入口不变
  emit('save', payload)
}
</script>

<template>
  <div class="space-y-6 animate-fade-in">
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <StudentProfileInfoCard
        :user-id="props.userId"
        :profile="props.profile"
        @save="handleProfileSave"
      />

      <StudentProfileSettingsPanel
        :notification-settings="props.notificationSettings"
        :study-goal="props.studyGoal"
      />
    </div>
  </div>
</template>
