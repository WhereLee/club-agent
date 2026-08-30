import request from './request'

// 站内消息：概念作废/通过通知（所有登录用户）
export const getMessages = (params) => request.get('/messages', { params })
export const getUnreadCount = () => request.get('/messages/unread-count')
export const markMessageRead = (id) => request.post(`/messages/${id}/read`)
