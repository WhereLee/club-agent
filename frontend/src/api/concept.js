import request from './request'

// 概念：发起 / 列表 / 详情 / 草稿 / 提交 / 撤回 / 放弃 / 投票 / 老师批复
export const createConcept = (clubId, data) => request.post(`/clubs/${clubId}/concepts`, data)
export const getConcepts = (clubId, params) => request.get(`/clubs/${clubId}/concepts`, { params })
export const getConceptDetail = (clubId, id) => request.get(`/clubs/${clubId}/concepts/${id}`)
export const saveConceptDraft = (clubId, id, data) => request.put(`/clubs/${clubId}/concepts/${id}/draft`, data)
export const submitConcept = (clubId, id) => request.post(`/clubs/${clubId}/concepts/${id}/submit`)
export const withdrawConcept = (clubId, id) => request.post(`/clubs/${clubId}/concepts/${id}/withdraw`)
export const abandonConcept = (clubId, id) => request.post(`/clubs/${clubId}/concepts/${id}/abandon`)
export const voteConcept = (clubId, id, data) => request.post(`/clubs/${clubId}/concepts/${id}/vote`, data)
export const reviewConcept = (clubId, id, data) => request.post(`/clubs/${clubId}/concepts/${id}/review`, data)

// 概念 AI 起草助手：对话 / 会话重放 / 草案采纳（人确认前置）
// chatAi 单独放宽超时：MiMo 一轮 ReAct 40-60s，全局默认 15s 会提前 abort
export const chatAi = (clubId, id, data) => request.post(`/clubs/${clubId}/concepts/${id}/ai/chat`, data, { timeout: 120000 })
export const getAiSession = (clubId, id) => request.get(`/clubs/${clubId}/concepts/${id}/ai/session`)
export const applyAiDraft = (clubId, id, data) => request.put(`/clubs/${clubId}/concepts/${id}/ai-draft`, data)

// D3 经验沉淀（人确认后写，AI 无写权限）
export const saveExperience = (clubId, data) => request.post(`/clubs/${clubId}/ai/experience`, data)

// D4 SKILL.md 落盘（人确认后写）
export const saveSkill = (clubId, data) => request.post(`/clubs/${clubId}/ai/skill`, data)
