import request from './request'

// 管理层经验问答（双项目集成阶段2 · J3：独立 Agent 服务）
export const createQaSession = (clubId, title) => request.post(`/clubs/${clubId}/ai/qa/sessions`, { title })
export const listQaSessions = (clubId) => request.get(`/clubs/${clubId}/ai/qa/sessions`)
export const deleteQaSession = (clubId, sessionId) => request.delete(`/clubs/${clubId}/ai/qa/sessions/${sessionId}`)
// 单轮问答：120s 后端超时，前端放宽到 150s（一轮 ReAct 可能多次检索）
export const chatQa = (clubId, sessionId, message) =>
  request.post(`/clubs/${clubId}/ai/qa/sessions/${sessionId}/chat`, { message }, { timeout: 150000 })
export const getQaMessages = (clubId, sessionId) => request.get(`/clubs/${clubId}/ai/qa/sessions/${sessionId}/messages`)
