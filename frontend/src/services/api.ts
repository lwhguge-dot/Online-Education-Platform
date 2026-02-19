/**
 * API 服务层入口
 * 统一导出所有 API 模块，保持向下兼容
 */

// 导出核心请求方法和工具
export * from './request'

// 导入所有模块 API
import { authAPI } from './modules/auth'
import { userAPI, teacherProfileAPI } from './modules/user'
import { courseAPI, chapterAPI } from './modules/course'
import { enrollmentAPI } from './modules/enrollment'
import { homeworkAPI } from './modules/homework'
import { commentAPI, chapterCommentAPI, discussionAPI, announcementAPI, notificationAPI } from './modules/interaction'
import { statsAPI, progressAPI, badgeAPI, auditLogAPI } from './modules/stats'
import { fileAPI } from './modules/file'
import { healthAPI } from './modules/health'
import { calendarAPI } from './modules/calendar'
import { startHeartbeat, stopHeartbeat, startStatusCheck, stopStatusCheck, saveAuth, getAuth, clearAuth } from './request'

// 重新导出 API 对象，供单独引入使用
export {
  authAPI,
  userAPI,
  teacherProfileAPI,
  courseAPI,
  chapterAPI,
  enrollmentAPI,
  homeworkAPI,
  commentAPI,
  chapterCommentAPI,
  discussionAPI,
  announcementAPI,
  notificationAPI,
  statsAPI,
  progressAPI,
  badgeAPI,
  auditLogAPI,
  fileAPI,
  healthAPI,
  calendarAPI,
}

// 默认导出大对象，兼容旧代码 import api from '@/services/api'
export default {
  authAPI,
  userAPI,
  teacherProfileAPI,
  courseAPI,
  chapterAPI,
  enrollmentAPI,
  homeworkAPI,
  commentAPI,
  chapterCommentAPI,
  discussionAPI,
  announcementAPI,
  notificationAPI,
  statsAPI,
  progressAPI,
  badgeAPI,
  auditLogAPI,
  fileAPI,
  healthAPI,
  calendarAPI,
  saveAuth,
  getAuth,
  clearAuth,
  startStatusCheck,
  stopStatusCheck,
  startHeartbeat,
  stopHeartbeat,
}
