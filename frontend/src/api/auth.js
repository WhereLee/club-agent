import request from './request'

// 认证接口
export const getCaptcha = () => request.get('/auth/captcha')
export const register = (data) => request.post('/auth/register', data)
export const login = (data) => request.post('/auth/login', data)
export const logout = () => request.post('/auth/logout')
