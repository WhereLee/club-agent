import axios from 'axios'
import { ElMessage } from 'element-plus'

// 统一请求封装：
// - 请求拦截：注入 Authorization: Bearer token
// - 响应拦截：R.code 统一处理（200 透传、401 清会话跳登录、其余弹错误）
const service = axios.create({ timeout: 15000 })

service.interceptors.request.use((config) => {
  const token = localStorage.getItem('club_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

service.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code === 200) return res
    if (res.code === 401) {
      localStorage.removeItem('club_token')
      localStorage.removeItem('club_user')
      if (!location.pathname.startsWith('/login')) {
        ElMessage.warning('登录已过期，请重新登录')
        location.href = '/login'
      }
      return Promise.reject(new Error(res.message))
    }
    ElMessage.error(res.message || '操作失败')
    return Promise.reject(new Error(res.message))
  },
  (error) => {
    ElMessage.error('网络异常，请稍后重试')
    return Promise.reject(error)
  }
)

export default service
