import request from './request'

// 个人信息接口
export const getMe = () => request.get('/user/me')
export const updateProfile = (data) => request.put('/user/profile', data)
export const updatePassword = (data) => request.put('/user/password', data)
export const updateAvatar = (file) => {
  const form = new FormData()
  form.append('file', file)
  return request.post('/user/avatar', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
