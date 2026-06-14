/**
 * API 服务层入口
 * 统一导出所有 API 模块
 */

export * from './request'

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
