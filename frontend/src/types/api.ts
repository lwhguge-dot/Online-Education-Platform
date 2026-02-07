/**
 * API 类型定义
 * 定义后端 API 的请求和响应类型
 */

// ==================== 通用类型 ====================

/**
 * 统一响应结构
 */
export interface Result<T = any> {
  code: number
  message: string
  data: T
}

/**
 * 分页参数
 */
export interface PageParams {
  page?: number
  limit?: number
}

/**
 * 分页响应
 */
export interface PageResult<T> {
  list: T[]
  pagination: {
    total: number
    page: number
    limit: number
    pages: number
  }
}

// ==================== 用户相关 ====================

/**
 * 用户角色
 */
export type UserRole = 'admin' | 'teacher' | 'student'

/**
 * 用户实体
 */
export interface User {
  id: number
  username: string
  name: string
  email: string
  role: UserRole
  avatar?: string
  phone?: string
  birthday?: string
  gender?: string
  status: number
  createdAt: string
  lastLoginAt?: string
}

/**
 * 用户视图对象
 */
export interface UserVO {
  id: number
  username: string
  name: string
  email: string
  role: UserRole
  avatar?: string
  phone?: string
  birthday?: string
  gender?: string
  status: number
  createdAt: string
  lastLoginAt?: string
}

/**
 * 用户简要信息
 */
export interface UserBriefVO {
  id: number
  username: string
  name: string
  email: string
}

/**
 * 用户资料更新 DTO
 */
export interface UserProfileDTO {
  name?: string
  username?: string
  phone?: string
  avatar?: string
  birthday?: string
  gender?: string
}

/**
 * 用户设置 DTO
 */
export interface UserSettingsDTO {
  notificationEnabled?: boolean
  emailNotification?: boolean
  dailyGoal?: number
  [key: string]: any
}

// ==================== 认证相关 ====================

/**
 * 登录请求
 */
export interface LoginRequest {
  email: string
  password: string
}

/**
 * 登录响应
 */
export interface LoginResponse {
  token: string
  user: User
}

/**
 * 注册请求
 */
export interface RegisterRequest {
  email: string
  username: string
  realName: string
  password: string
  role: UserRole
}

/**
 * 密码重置请求
 */
export interface ResetPasswordRequest {
  email: string
  realName: string
  newPassword: string
}

// ==================== 课程相关 ====================

/**
 * 课程状态
 */
export type CourseStatus = 'draft' | 'published' | 'archived'

/**
 * 课程实体
 */
export interface Course {
  id: number
  title: string
  description: string
  coverImage?: string
  teacherId: number
  teacherName?: string
  subject: string
  status: CourseStatus
  createdAt: string
  updatedAt: string
}

/**
 * 课程 DTO
 */
export interface CourseDTO {
  title: string
  description: string
  coverImage?: string
  teacherId: number
  subject: string
}

// ==================== 章节相关 ====================

/**
 * 章节实体
 */
export interface Chapter {
  id: number
  courseId: number
  title: string
  description?: string
  videoUrl?: string
  duration?: number
  orderNum: number
  createdAt: string
}

/**
 * 章节 DTO
 */
export interface ChapterDTO {
  courseId: number
  title: string
  description?: string
  videoUrl?: string
  duration?: number
  orderNum: number
}

/**
 * 测验题目
 */
export interface ChapterQuiz {
  id: number
  chapterId: number
  question: string
  options: string[]
  correctAnswer: number
  explanation?: string
}

// ==================== 作业相关 ====================

/**
 * 作业实体
 */
export interface Homework {
  id: number
  chapterId: number
  title: string
  description: string
  deadline?: string
  createdAt: string
}

/**
 * 作业提交
 */
export interface HomeworkSubmission {
  id: number
  homeworkId: number
  studentId: number
  content: string
  score?: number
  feedback?: string
  submittedAt: string
  gradedAt?: string
}

// ==================== 进度相关 ====================

/**
 * 视频进度上报
 */
export interface VideoProgressReport {
  chapterId: number
  studentId: number
  progress: number
  duration: number
  clientTimestamp?: number
}

/**
 * 测验提交
 */
export interface QuizSubmission {
  chapterId: number
  studentId: number
  answers: number[]
}

/**
 * 章节进度
 */
export interface ChapterProgress {
  chapterId: number
  studentId: number
  videoProgress: number
  quizScore?: number
  completed: boolean
}

// ==================== 报名相关 ====================

/**
 * 课程报名
 */
export interface Enrollment {
  id: number
  studentId: number
  courseId: number
  progress: number
  enrolledAt: string
}

// ==================== 评论相关 ====================

/**
 * 评论实体
 */
export interface Comment {
  id: number
  chapterId: number
  userId: number
  userName?: string
  userAvatar?: string
  content: string
  isPinned: boolean
  likeCount: number
  createdAt: string
}

/**
 * 评论 DTO
 */
export interface CommentDTO {
  chapterId: number
  userId: number
  content: string
  parentId?: number
}

// ==================== 公告相关 ====================

/**
 * 公告实体
 */
export interface Announcement {
  id: number
  title: string
  content: string
  audience: 'all' | 'student' | 'teacher'
  isPinned: boolean
  publishedAt?: string
  createdAt: string
}

/**
 * 公告 DTO
 */
export interface AnnouncementDTO {
  title: string
  content: string
  audience: 'all' | 'student' | 'teacher'
  isPinned?: boolean
}

// ==================== 统计相关 ====================

/**
 * 管理员仪表盘统计
 */
export interface AdminDashboardStats {
  totalUsers: number
  totalCourses: number
  totalEnrollments: number
  activeUsers: number
  userTrends?: Array<{ date: string; count: number }>
  courseDistribution?: Array<{ subject: string; count: number }>
}

/**
 * 教师仪表盘统计
 */
export interface TeacherDashboardStats {
  totalStudents: number
  totalCourses: number
  pendingHomeworks: number
  todayEnrollments: number
}

/**
 * 学生仪表盘统计
 */
export interface StudentDashboardStats {
  enrolledCourses: number
  completedChapters: number
  totalStudyTime: number
  averageScore: number
}
