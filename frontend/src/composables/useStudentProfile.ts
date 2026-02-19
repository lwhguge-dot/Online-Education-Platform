import { ref } from 'vue'
import { userAPI } from '../services/api'
import { useAuthStore } from '../stores/auth'
import { useToastStore } from '../stores/toast'

export function useStudentProfile() {
    const studentProfile = ref({
        username: '',
        realName: '',
        birthday: '',
        gender: '',
        email: '',
        phone: '',
        avatar: '',
    })

    const notificationSettings = ref({
        homeworkReminder: true,
        courseUpdate: true,
        teacherReply: true,
        systemNotice: true,
        emailNotify: false,
        pushNotify: true
    })

    const studyGoal = ref({
        dailyMinutes: 60,
        weeklyHours: 3
    })

    const loading = ref(false)
    const settingsReady = ref(false)

    const authStore = useAuthStore()
    const toast = useToastStore()

    const loadUserProfile = async (userId) => {
        if (!userId) return
        loading.value = true
        settingsReady.value = false

        try {
            // 1. Load basic profile
            const resProfile = await userAPI.getById(userId)
            if (resProfile.data) {
                const userData = resProfile.data
                studentProfile.value = {
                    username: userData.username || authStore.user?.username || '学生',
                    realName: userData.name || '',
                    birthday: userData.birthday || '',
                    gender: userData.gender || '',
                    email: userData.email || (userData.username + '@edu.cn'),
                    phone: userData.phone || '',
                    avatar: userData.avatar || ''
                }

                // Sync to auth store
                authStore.updateUser({
                    username: userData.username,
                    name: userData.name,
                    avatar: userData.avatar,
                    birthday: userData.birthday,
                    gender: userData.gender
                })
            }

            // 2. Load settings
            try {
                const resSettings = await userAPI.getSettings(userId)
                if (resSettings.data) {
                    const settings = resSettings.data
                    const ns = settings.notificationSettings || settings

                    // Merge notification settings
                    if (ns.homeworkReminder !== undefined) notificationSettings.value.homeworkReminder = ns.homeworkReminder
                    if (ns.courseUpdate !== undefined) notificationSettings.value.courseUpdate = ns.courseUpdate
                    if (ns.teacherReply !== undefined) notificationSettings.value.teacherReply = ns.teacherReply
                    if (ns.systemNotice !== undefined) notificationSettings.value.systemNotice = ns.systemNotice
                    if (ns.emailNotify !== undefined) notificationSettings.value.emailNotify = ns.emailNotify
                    if (ns.pushNotify !== undefined) notificationSettings.value.pushNotify = ns.pushNotify

                    // Merge study goal
                    const sg = settings.studyGoal || {}
                    const dailyMinutes = sg.dailyMinutes ?? settings.dailyGoalMinutes
                    const weeklyHours = sg.weeklyHours ?? settings.weeklyGoalChapters

                    if (dailyMinutes !== undefined && dailyMinutes !== null) studyGoal.value.dailyMinutes = dailyMinutes
                    if (weeklyHours !== undefined && weeklyHours !== null) studyGoal.value.weeklyHours = weeklyHours
                }
            } catch (e) {
                console.error('加载用户设置失败:', e)
                // Fallback to localStorage
                try {
                    const savedNotifications = localStorage.getItem('notification_settings')
                    const savedGoal = localStorage.getItem('study_goal')
                    if (savedNotifications) Object.assign(notificationSettings.value, JSON.parse(savedNotifications))
                    if (savedGoal) Object.assign(studyGoal.value, JSON.parse(savedGoal))
                } catch (parseErr) {
                    console.warn('本地设置缓存解析失败:', parseErr)
                }
            }
        } catch (e) {
            console.error('加载用户信息失败:', e)
            toast.error('加载用户信息失败')
        } finally {
            loading.value = false
            settingsReady.value = true
        }
    }

    const updateProfile = async (userId, newProfile) => {
        if (!userId) return false

        try {
            const res = await userAPI.updateProfile(userId, {
                username: newProfile.username,
                phone: newProfile.phone,
                avatar: newProfile.avatar,
                birthday: newProfile.birthday,
                gender: newProfile.gender
            })

            if (res.data) {
                // Update local state with response
                const updatedProfile = {
                    ...newProfile,
                    username: res.data.username,
                    realName: res.data.name
                }
                studentProfile.value = updatedProfile

                // Update auth store
                authStore.updateUser({
                    username: res.data.username,
                    name: res.data.name,
                    avatar: res.data.avatar,
                    birthday: res.data.birthday,
                    gender: res.data.gender
                })

                // Update settings
                try {
                    await userAPI.updateSettings(userId, {
                        notificationSettings: { ...notificationSettings.value },
                        studyGoal: { ...studyGoal.value }
                    })
                } catch (settingsErr) {
                    console.error('保存设置失败:', settingsErr)
                }

                // Update localStorage
                localStorage.setItem('student_profile', JSON.stringify(updatedProfile))
                localStorage.setItem('notification_settings', JSON.stringify(notificationSettings.value))
                localStorage.setItem('study_goal', JSON.stringify(studyGoal.value))

                toast.success('设置保存成功')
                return true
            }
        } catch (e) {
            console.error('保存个人信息失败:', e)
            toast.error('保存失败: ' + e.message)
            return false
        }
        return false
    }

    return {
        studentProfile,
        notificationSettings,
        studyGoal,
        loading,
        settingsReady,
        loadUserProfile,
        updateProfile
    }
}
