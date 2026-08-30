import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/clubs' },
    { path: '/login', component: () => import('../views/Login.vue'), meta: { public: true } },
    { path: '/register', component: () => import('../views/Register.vue'), meta: { public: true } },
    { path: '/clubs', component: () => import('../views/ClubList.vue') },
    { path: '/clubs/:clubId', component: () => import('../views/ClubDetail.vue') },
    { path: '/clubs/:clubId/concept', component: () => import('../views/ConceptBirth.vue') },
    { path: '/clubs/:clubId/concept/:id', component: () => import('../views/ConceptBirth.vue') },
    // 活动前（块 A-D）：列表 / 详情（含问卷·文件） / 讨论群
    { path: '/clubs/:clubId/activities', component: () => import('../views/ActivityList.vue') },
    { path: '/clubs/:clubId/activities/:id', component: () => import('../views/ActivityDetail.vue') },
    { path: '/clubs/:clubId/activities/:id/chat', component: () => import('../views/ChatView.vue') },
    { path: '/my-clubs', component: () => import('../views/MyClubs.vue') },
    { path: '/messages', component: () => import('../views/Messages.vue') },
    { path: '/profile', component: () => import('../views/Profile.vue') },
    // 老师管理台（需 isTeacher，守卫拦截）
    { path: '/teacher', redirect: '/teacher/clubs', meta: { teacher: true } },
    { path: '/teacher/clubs', component: () => import('../views/TeacherClubs.vue'), meta: { teacher: true } },
    { path: '/teacher/clubs/:clubId', component: () => import('../views/TeacherClubManage.vue'), meta: { teacher: true } },
    { path: '/teacher/todos', component: () => import('../views/TeacherTodos.vue'), meta: { teacher: true } },
    { path: '/teacher/logs', component: () => import('../views/TeacherLogs.vue'), meta: { teacher: true } }
  ]
})

// 路由守卫：未登录只能访问公开页；已登录访问登录页重定向到社团列表
router.beforeEach((to) => {
  const token = localStorage.getItem('club_token')
  if (!to.meta.public && !token) {
    return '/login'
  }
  if (to.meta.public && token && to.path === '/login') {
    return '/clubs'
  }
  // 老师路由：非老师（或未登录态信息缺失）拦截回社团列表
  if (to.meta.teacher) {
    const user = JSON.parse(localStorage.getItem('club_user') || 'null')
    if (!user?.isTeacher) {
      return '/clubs'
    }
  }
  return true
})

export default router
