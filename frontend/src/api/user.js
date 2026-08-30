import request from './request'

// 个人信息接口
export const getMe = () => request.get('/user/me')
export const updateProfile = (data) => request.put('/user/profile', data)
export const updatePassword = (data) => request.put('/user/password', data)
export const updateAvatar = (file) => {
  const form = new FormData()
  form.append('file', file)
  // 不手动设置 Content-Type：浏览器自动生成带 boundary 的 multipart 头（手动设置会丢失 boundary 导致后端解析失败）
  return request.post('/user/avatar', form)
}
