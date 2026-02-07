import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import type { UserRole } from '../types/api'

const DEFAULT_TITLE = '智慧课堂'

// 扩展路由元信息类型
declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    requiresAuth?: boolean
    allowedRoles?: UserRole[]
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue'),
    meta: { title: '首页' }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/course/:id',
    name: 'CourseDetail',
    component: () => import('../views/CourseDetail.vue'),
    meta: { title: '课程详情' }
  },
  {
    path: '/admin',
    name: 'AdminCenter',
    component: () => import('../views/AdminCenter.vue'),
    meta: { requiresAuth: true, allowedRoles: ['admin'], title: '管理后台' }
  },
  {
    path: '/teacher',
    name: 'TeacherCenter',
    component: () => import('../views/TeacherCenter.vue'),
    meta: { requiresAuth: true, allowedRoles: ['teacher'], title: '教师中心' }
  },
  {
    path: '/student',
    name: 'StudentCenter',
    component: () => import('../views/StudentCenter.vue'),
    meta: { requiresAuth: true, allowedRoles: ['student'], title: '学生中心' }
  },
  {
    path: '/study/:id',
    name: 'Study',
    component: () => import('../views/StudyView.vue'),
    meta: { requiresAuth: true, allowedRoles: ['student', 'admin', 'teacher'], title: '课程学习' }
  },
  {
    path: '/homework/:id',
    name: 'DoHomework',
    component: () => import('../views/DoHomework.vue'),
    meta: { requiresAuth: true, allowedRoles: ['student'], title: '作业' }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('../views/NotFound.vue'),
    meta: { title: '页面不存在' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// Auth guard
router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()

  if (to.meta.requiresAuth) {
    if (!authStore.isAuthenticated) {
      next('/login')
      return
    }

    if (to.meta.allowedRoles && authStore.user && !to.meta.allowedRoles.includes(authStore.user.role)) {
      next('/')
      return
    }
  }

  next()
})

// Dynamic title guard
router.afterEach((to) => {
  const title = to.meta.title
  document.title = title ? `${title} - ${DEFAULT_TITLE}` : DEFAULT_TITLE
})

export default router
