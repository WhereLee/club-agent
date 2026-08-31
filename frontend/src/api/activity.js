import request from './request'

// ===== 活动（块 A） =====
export const getActivities = (clubId, page = 1, size = 10, status) =>
  request.get(`/clubs/${clubId}/activities`, { params: { page, size, status } })
export const getActivityDetail = (clubId, id) => request.get(`/clubs/${clubId}/activities/${id}`)
export const cancelActivity = (clubId, id, reason) =>
  request.post(`/clubs/${clubId}/activities/${id}/cancel`, { reason })

// ===== 问卷（块 B） =====
export const publishSurvey = (clubId, id, data) => request.post(`/clubs/${clubId}/activities/${id}/survey`, data)
export const getSurvey = (clubId, id) => request.get(`/clubs/${clubId}/activities/${id}/survey`)
export const submitSurvey = (clubId, id, data) => request.post(`/clubs/${clubId}/activities/${id}/survey/submit`, data)
export const getSurveyResults = (clubId, id) => request.get(`/clubs/${clubId}/activities/${id}/survey/results`)
export const closeSurvey = (clubId, id) => request.post(`/clubs/${clubId}/activities/${id}/survey/close`)

// ===== 讨论群（块 C） =====
export const getChatMessages = (clubId, id, page = 1, size = 20) =>
  request.get(`/clubs/${clubId}/activities/${id}/chat/messages`, { params: { page, size } })

// ===== 正式文件（块 D）=====
export const saveActivityFile = (clubId, id, data) => request.post(`/clubs/${clubId}/activities/${id}/file/save`, data)
export const publishActivityFile = (clubId, id, data) => request.post(`/clubs/${clubId}/activities/${id}/file/publish`, data)
export const getActivityFile = (clubId, id) => request.get(`/clubs/${clubId}/activities/${id}/file`)

// ===== 正式文件撰写 AI（E1，活动前 Agent）=====
export const aiChatActivity = (clubId, id, message) =>
  request.post(`/clubs/${clubId}/activities/${id}/ai/chat`, { message }, { timeout: 300000 })
export const aiSessionActivity = (clubId, id) => request.get(`/clubs/${clubId}/activities/${id}/ai/session`)

// ===== 活动中状态机推进（阶段 0） =====
export const endDiscussion = (clubId, id) => request.post(`/clubs/${clubId}/activities/${id}/discussion/end`)
export const startSignup = (clubId, id, deadline) => request.post(`/clubs/${clubId}/activities/${id}/signup/start`, { deadline })
export const startExecution = (clubId, id, deadline) => request.post(`/clubs/${clubId}/activities/${id}/execution/start`, deadline ? { deadline } : {})
export const completeExecution = (clubId, id) => request.post(`/clubs/${clubId}/activities/${id}/execution/complete`)
export const closeRecords = (clubId, id) => request.post(`/clubs/${clubId}/activities/${id}/records/close`)

// ===== 报名（块 F） =====
export const signupActivity = (clubId, id, data) => request.post(`/clubs/${clubId}/activities/${id}/signup`, data)
export const getSignups = (clubId, id) => request.get(`/clubs/${clubId}/activities/${id}/signups`)

// ===== 签到（块 G） =====
export const checkinActivity = (clubId, id) => request.post(`/clubs/${clubId}/activities/${id}/attendance`)
export const getAttendances = (clubId, id) => request.get(`/clubs/${clubId}/activities/${id}/attendances`)

// ===== 执行留痕（块 G） =====
export const getRecordMine = (clubId, id) => request.get(`/clubs/${clubId}/activities/${id}/records/mine`)
export const submitRecord = (clubId, id, data) => request.post(`/clubs/${clubId}/activities/${id}/records`, data)
export const getRecords = (clubId, id) => request.get(`/clubs/${clubId}/activities/${id}/records`)

// ===== 留痕打分（块 H，Java AI 预评 + 管理员确认） =====
export const previewRecordScore = (clubId, id, userId) =>
  request.post(`/clubs/${clubId}/activities/${id}/record-scores/preview`, null, { params: { userId }, timeout: 120000 })
export const scoreRecord = (clubId, id, userId, score) =>
  request.post(`/clubs/${clubId}/activities/${id}/record-scores`, { userId, score })
export const getRecordScores = (clubId, id) => request.get(`/clubs/${clubId}/activities/${id}/record-scores`)

// ===== 讨论建议（块 H，Java AI 提炼 + 采纳） =====
export const extractSuggestions = (clubId, id) => request.post(`/clubs/${clubId}/activities/${id}/suggestions/extract`, null, { timeout: 120000 })
export const adoptSuggestion = (clubId, id, suggestionId) =>
  request.post(`/clubs/${clubId}/activities/${id}/suggestions/${suggestionId}/adopt`)
export const getSuggestions = (clubId, id) => request.get(`/clubs/${clubId}/activities/${id}/suggestions`)

// ===== 奖励统计（块 H） =====
export const getRewards = (clubId, id) => request.get(`/clubs/${clubId}/activities/${id}/rewards`)

// ---- 活动后阶段：总结 + 归档 ----
export const getActivitySummary = (clubId, id) => request.get(`/clubs/${clubId}/activities/${id}/summary`)
export const regenerateSummary = (clubId, id) => request.post(`/clubs/${clubId}/activities/${id}/summary/regenerate`, null, { timeout: 180000 })
export const resumeSummary = (clubId, id, answers) => request.post(`/clubs/${clubId}/activities/${id}/summary/resume`, answers, { timeout: 180000 })
export const archiveActivity = (clubId, id) => request.post(`/clubs/${clubId}/activities/${id}/archive`)

// ===== 活动资料库（双项目集成：文件入 rag 知识库，概念 Agent 起草时检索复用） =====
export const getFileLib = (clubId) => request.get(`/clubs/${clubId}/file-lib`)
export const uploadFileLib = (clubId, formData, activityId) =>
  request.post(`/clubs/${clubId}/file-lib/upload`, formData, {
    // 不手动设 Content-Type：axios 对 FormData 会自动生成带 boundary 的头（手动设置会丢 boundary，同 updateAvatar 教训）
    params: activityId ? { activityId } : {},
    timeout: 120000,
  })
export const deleteFileLib = (clubId, libId) => request.delete(`/clubs/${clubId}/file-lib/${libId}`)
