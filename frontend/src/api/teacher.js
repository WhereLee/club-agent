import request from './request'

// 老师管理台接口
export const getTodos = () => request.get('/todos')
export const getOperLogs = (page = 1, size = 10) => request.get('/logs/oper', { params: { page, size } })
export const getLoginLogs = (page = 1, size = 10) => request.get('/logs/login', { params: { page, size } })
