import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/clubs' },
    { path: '/login', component: () => import('../views/Login.vue'), meta: { public: true } },
    { path: '/register', component: () => import('../views/Register.vue'), meta: { public: true } },
    { path: '/clubs', component: () => import('../views/ClubList.vue') },
    { path: '/clubs/:clubId', component: () => import('../views/ClubDetail.vue') },
    { path: '/my-clubs', component: () => import('../views/MyClubs.vue') },
    { path: '/profile', component: () => import('../views/Profile.vue') }
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
  return true
})

export default router
