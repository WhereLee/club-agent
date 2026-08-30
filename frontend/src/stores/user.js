import { defineStore } from 'pinia'
import { login as loginApi, logout as logoutApi } from '../api/auth'
import { getMe } from '../api/user'
import { getUnreadCount } from '../api/message'

// 用户会话：token + 基本信息，localStorage 持久化（刷新不丢登录态）；未读消息数（顶栏铃铛）
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('club_token') || '',
    userInfo: JSON.parse(localStorage.getItem('club_user') || 'null'),
    unreadCount: 0
  }),

  actions: {
    /** 未读消息数（登录后/路由变化/标记已读后调用） */
    async refreshUnread() {
      try {
        this.unreadCount = (await getUnreadCount()).data
      } catch (e) { /* 拦截器已提示 */ }
    },

    async login(form) {
      const res = await loginApi(form)
      this.token = res.data.token
      localStorage.setItem('club_token', this.token)
      this.userInfo = {
        userId: res.data.userId,
        username: res.data.username,
        nickname: res.data.nickname,
        avatarUrl: res.data.avatarUrl,
        isTeacher: res.data.isTeacher
      }
      localStorage.setItem('club_user', JSON.stringify(this.userInfo))
    },

    async fetchMe() {
      const res = await getMe()
      this.userInfo = res.data
      localStorage.setItem('club_user', JSON.stringify(res.data))
    },

    async logout() {
      try {
        await logoutApi()
      } catch (e) {
        // 登出接口失败不阻塞本地清理
      }
      this.token = ''
      this.userInfo = null
      localStorage.removeItem('club_token')
      localStorage.removeItem('club_user')
    }
  }
})
