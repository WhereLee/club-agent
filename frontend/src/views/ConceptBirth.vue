<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createConcept, getConcepts, getConceptDetail, saveConceptDraft, submitConcept, abandonConcept, chatAi, getAiSession, applyAiDraft, saveExperience, saveSkill } from '../api/concept'

const route = useRoute()
const router = useRouter()
const clubId = route.params.clubId
const editId = route.params.id // 有 id = 继续编辑既有概念

const sessionId = ref(null)
const status = ref(null)
const form = ref({ reason: '', plannedTime: '', plannedLocation: '', content: '' })
const saving = ref(false)
const submitting = ref(false)
const loading = ref(false)

// AI 起草助手会话
const messages = ref([])
const aiInput = ref('')
const aiSending = ref(false)
const aiLoading = ref(false)
const adoptedDraftId = ref(null)   // 已采纳的草案 tool 消息 id（防止重复采纳）
const savedExpIds = ref(new Set())   // 已沉淀的经验/思考角度 tool 消息 id（防重复确认）
const savedSkillIds = ref(new Set()) // 已落盘的 SKILL tool 消息 id（防重复确认）
const conceptOwnerId = ref(null)     // 概念发起人（thinking_pattern 归属）

// 状态常量（与后端一致）
const STATUS = { DRAFTING: 1, SUBMITTED: 2, REVOTING: 3, TEACHER_REVIEW: 4, APPROVED: 5, VOIDED: 6 }

onMounted(async () => {
  loading.value = true
  try {
    if (editId) {
      // 继续编辑：加载既有概念
      const res = await getConceptDetail(clubId, editId)
      sessionId.value = res.data.id
      status.value = res.data.status
      conceptOwnerId.value = res.data.userId
      fillForm(res.data)
    } else {
      // 幂等进入：已有起草中概念则加载，否则新建
      const list = await getConcepts(clubId, { page: 1, size: 5, status: STATUS.DRAFTING })
      const drafting = list.data?.records?.find((c) => c.status === STATUS.DRAFTING)
      if (drafting) {
        sessionId.value = drafting.id
        status.value = drafting.status
        conceptOwnerId.value = drafting.userId
        fillForm(drafting)
      } else {
        const res = await createConcept(clubId, {})
        sessionId.value = res.data.id
        status.value = res.data.status
        conceptOwnerId.value = res.data.userId
      }
    }
    loadAiSession()
  } catch (e) { /* 拦截器已提示 */ } finally {
    loading.value = false
  }
})

// ---------- AI 起草助手（D1：纯对话，无工具） ----------
async function loadAiSession() {
  if (!sessionId.value) return
  aiLoading.value = true
  try {
    const res = await getAiSession(clubId, sessionId.value)
    messages.value = res.data || []
  } catch (e) { /* 拦截器已提示；会话为空时静默 */ } finally {
    aiLoading.value = false
  }
}

async function onAiSend() {
  const text = aiInput.value.trim()
  if (!text || aiSending.value) return
  if (!sessionId.value) { ElMessage.warning('概念尚未就绪，请稍后重试'); return }
  aiSending.value = true
  try {
    const res = await chatAi(clubId, sessionId.value, { message: text })
    messages.value = res.data || []
    aiInput.value = ''
    scrollToBottom()
  } catch (e) {
    // 拦截器已提示（如 1035 AI 暂不可用）；输入保留，用户可重发或手动填表
  } finally {
    aiSending.value = false
  }
}

// ---------- 草案卡片：人确认后采纳落表（AI 无写权限，写必须由人触发） ----------
// tool 消息：tool_args=入参（审计），content=工具输出（generate_draft 的草案 JSON）
function parseDraft(m) {
  try { return JSON.parse(m.content) } catch (e) { return null }
}

async function onAdopt(m) {
  const draft = parseDraft(m)
  if (!draft || adoptedDraftId.value) return
  adoptedDraftId.value = m.id
  try {
    const res = await applyAiDraft(clubId, sessionId.value, {
      reason: draft.reason,
      plannedTime: draft.planned_time,
      plannedLocation: draft.planned_location,
      content: draft.content,
      note: draft.decision_note
    })
    fillForm(res.data)
    ElMessage.success('已采纳 AI 草案，表单已更新')
  } catch (e) {
    adoptedDraftId.value = null
    // 拦截器已提示
  }
}

function scrollToBottom() {
  setTimeout(() => {
    const box = document.getElementById('ai-chat-box')
    if (box) box.scrollTop = box.scrollHeight
  }, 50)
}

// ---------- 经验/思考角度确认卡片（D3：AI 只草拟，人确认后落库） ----------
function parseExperience(m) {
  try { return JSON.parse(m.content) } catch (e) { return null }
}

function parseThinkingPattern(m) {
  try { return JSON.parse(m.content) } catch (e) { return null }
}

async function onSaveExperience(m) {
  const exp = parseExperience(m)
  if (!exp || savedExpIds.value.has(m.id)) return
  savedExpIds.value.add(m.id)
  try {
    await saveExperience(clubId, {
      category: exp.category,
      title: exp.title,
      content: exp.content,
      sourceConceptId: sessionId.value
    })
    ElMessage.success('经验已沉淀，后续对话可复用')
  } catch (e) {
    savedExpIds.value.delete(m.id)
  }
}

async function onSaveThinkingPattern(m) {
  const tp = parseThinkingPattern(m)
  if (!tp || savedExpIds.value.has(m.id)) return
  savedExpIds.value.add(m.id)
  try {
    await saveExperience(clubId, {
      category: 'thinking_pattern',
      title: '发起人思考角度',
      content: tp.content,
      ownerId: conceptOwnerId.value,
      sourceConceptId: sessionId.value
    })
    ElMessage.success('思考角度已保存，后续对话将自动注入')
  } catch (e) {
    savedExpIds.value.delete(m.id)
  }
}

// ---------- SKILL 确认卡片（D4：AI 只草拟，人确认后落盘） ----------
function parseSkill(m) {
  try { return JSON.parse(m.content) } catch (e) { return null }
}

async function onSaveSkill(m) {
  const sk = parseSkill(m)
  if (!sk || savedSkillIds.value.has(m.id)) return
  savedSkillIds.value.add(m.id)
  try {
    const res = await saveSkill(clubId, {
      name: sk.name,
      description: sk.description,
      whenToUse: sk.when_to_use,
      body: sk.body,
      sourceConceptId: sessionId.value
    })
    ElMessage.success(`SKILL 已落盘：${res.data}`)
  } catch (e) {
    savedSkillIds.value.delete(m.id)
  }
}

function fillForm(c) {
  form.value = {
    reason: c.reason || '',
    plannedTime: c.plannedTime || '',
    plannedLocation: c.plannedLocation || '',
    content: c.content || ''
  }
}

async function onSave() {
  if (!sessionId.value) { ElMessage.warning('概念尚未就绪，请稍后重试'); return }
  saving.value = true
  try {
    const res = await saveConceptDraft(clubId, sessionId.value, { ...form.value })
    fillForm(res.data)
    ElMessage.success('草稿已保存')
  } catch (e) { /* 拦截器已提示 */ } finally {
    saving.value = false
  }
}

async function onSubmit() {
  if (!form.value.reason.trim() || !form.value.plannedTime.trim()
    || !form.value.plannedLocation.trim() || !form.value.content.trim()) {
    ElMessage.warning('发起理由、预计时间、预计地点、活动简述为必填项')
    return
  }
  if (!sessionId.value) { ElMessage.warning('概念尚未就绪，请稍后重试'); return }
  submitting.value = true
  try {
    // 提交 = 保存终稿 + 置为已提交（后端校验已落库内容）
    await saveConceptDraft(clubId, sessionId.value, { ...form.value })
    await submitConcept(clubId, sessionId.value)
    ElMessage.success('已提交，等待其他管理层审阅（时限 36 小时）')
    router.push(`/clubs/${clubId}`)
  } catch (e) { /* 拦截器已提示 */ } finally {
    submitting.value = false
  }
}

async function onAbandon() {
  try {
    await ElMessageBox.confirm('确定放弃该概念吗？放弃后不可恢复，名额将释放。', '放弃概念', { type: 'warning' })
  } catch (e) { return }
  try {
    await abandonConcept(clubId, sessionId.value)
    ElMessage.success('已放弃概念')
    router.push(`/clubs/${clubId}`)
  } catch (e) { /* 拦截器已提示 */ }
}
</script>

<template>
  <div v-loading="loading">
    <el-page-header @back="router.push(`/clubs/${clubId}`)" content="发起概念" class="page-header" />

    <div class="layout">
      <!-- 左：表单（主体，事实源） -->
      <el-card shadow="never" class="form-card">
        <el-form label-position="top">
          <el-form-item label="发起理由" required>
            <el-input v-model="form.reason" type="textarea" :rows="3" maxlength="500" show-word-limit
              placeholder="为什么发起这个活动？例如：文昌下月 5 号有发射计划，组织一次跨市骑行观摩航天发射是难得的机会。" />
          </el-form-item>
          <el-form-item label="预计时间" required>
            <el-input v-model="form.plannedTime" maxlength="100" placeholder="例如：下周五出发，共 2 天" />
          </el-form-item>
          <el-form-item label="预计地点" required>
            <el-input v-model="form.plannedLocation" maxlength="200" placeholder="例如：文昌市（单程骑行约 80 公里）" />
          </el-form-item>
          <el-form-item label="活动简述" required>
            <el-input v-model="form.content" type="textarea" :rows="4" maxlength="1000" show-word-limit
              placeholder="简述活动安排，例如：周六凌晨出发，上午观看发射，下午返程，沿途设补给点…" />
          </el-form-item>
        </el-form>
        <div class="actions">
          <el-button @click="onSave" :loading="saving">保存草稿</el-button>
          <el-button type="primary" @click="onSubmit" :loading="submitting">提交</el-button>
          <el-button type="danger" plain @click="onAbandon" v-if="status === STATUS.DRAFTING">放弃</el-button>
        </div>
      </el-card>

      <!-- 右：AI 起草助手（对话式构思，产物经人确认落表单） -->
      <el-card shadow="never" class="ai-card">
        <template #header>
          <div class="ai-header">
            <span>AI 起草助手</span>
            <el-tag size="small" type="info">对话构思</el-tag>
          </div>
        </template>
        <div id="ai-chat-box" v-loading="aiLoading" class="ai-chat-box">
          <el-empty v-if="!messages.length && !aiLoading" description="和 AI 聊聊你的想法，它会帮你分析可行性、补充你没考虑到的维度" :image-size="60" />
          <div v-for="m in messages" :key="m.id" class="msg-row" :class="m.role === 'user' ? 'right' : 'left'">
            <div class="bubble" :class="m.role === 'user' ? 'user' : 'assistant'">
              <div v-if="m.role === 'tool'" class="tool-tag">工具</div>
              <div v-if="m.role === 'tool' && m.toolName === 'generate_draft'" class="draft-card">
                <div class="draft-title">AI 草案（待确认）</div>
                <div v-if="parseDraft(m)" class="draft-body">
                  <div class="draft-field"><span class="label">发起理由</span>{{ parseDraft(m).reason }}</div>
                  <div class="draft-field"><span class="label">预计时间</span>{{ parseDraft(m).planned_time }}</div>
                  <div class="draft-field"><span class="label">预计地点</span>{{ parseDraft(m).planned_location }}</div>
                  <div class="draft-field"><span class="label">活动简述</span>{{ parseDraft(m).content }}</div>
                  <div class="draft-note">决策说明：{{ parseDraft(m).decision_note }}</div>
                  <el-button v-if="adoptedDraftId !== m.id" type="primary" size="small" @click="onAdopt(m)">采纳（写入表单）</el-button>
                  <el-tag v-else type="success" size="small">已采纳</el-tag>
                </div>
              </div>
              <div v-else-if="m.role === 'tool' && m.toolName === 'extract_experience'" class="draft-card exp-card">
                <div class="draft-title">AI 建议沉淀经验（待确认）</div>
                <div v-if="parseExperience(m)" class="draft-body">
                  <div class="draft-field"><span class="label">标题</span>{{ parseExperience(m).title }}</div>
                  <div class="draft-field"><span class="label">类别</span>{{ parseExperience(m).category }}</div>
                  <div class="draft-field"><span class="label">内容</span>{{ parseExperience(m).content }}</div>
                  <el-button v-if="!savedExpIds.has(m.id)" type="warning" size="small" @click="onSaveExperience(m)">确认沉淀</el-button>
                  <el-tag v-else type="success" size="small">已沉淀</el-tag>
                </div>
              </div>
              <div v-else-if="m.role === 'tool' && m.toolName === 'extract_thinking_pattern'" class="draft-card tp-card">
                <div class="draft-title">AI 建议保存思考角度（待确认）</div>
                <div v-if="parseThinkingPattern(m)" class="draft-body">
                  <div class="draft-field" style="white-space: pre-line">{{ parseThinkingPattern(m).content }}</div>
                  <el-button v-if="!savedExpIds.has(m.id)" type="warning" size="small" @click="onSaveThinkingPattern(m)">确认保存</el-button>
                  <el-tag v-else type="success" size="small">已保存</el-tag>
                </div>
              </div>
              <div v-else-if="m.role === 'tool' && m.toolName === 'generate_skill'" class="draft-card sk-card">
                <div class="draft-title">AI 建议沉淀 SKILL（待确认）</div>
                <div v-if="parseSkill(m)" class="draft-body">
                  <div class="draft-field"><span class="label">名称</span>{{ parseSkill(m).name }}</div>
                  <div class="draft-field"><span class="label">说明</span>{{ parseSkill(m).description }}</div>
                  <pre class="skill-preview">{{ parseSkill(m).body }}</pre>
                  <el-button v-if="!savedSkillIds.has(m.id)" type="warning" size="small" @click="onSaveSkill(m)">确认落盘</el-button>
                  <el-tag v-else type="success" size="small">已落盘</el-tag>
                </div>
              </div>
              <div v-else class="msg-content">{{ m.content }}</div>
              <div class="msg-time">{{ (m.createdAt || '').slice(11, 16) }}</div>
            </div>
          </div>
          <div v-if="aiSending" class="msg-row left">
            <div class="bubble assistant typing">AI 思考中…</div>
          </div>
        </div>
        <div class="ai-input-row">
          <el-input v-model="aiInput" placeholder="说说你的想法（Enter 发送）" maxlength="2000"
            :disabled="aiSending || !sessionId" @keyup.enter="onAiSend" />
          <el-button type="primary" :loading="aiSending" :disabled="!aiInput.trim() || !sessionId" @click="onAiSend">发送</el-button>
        </div>
        <div class="ai-tip">AI 仅作构思参考，最终内容以表单为准；AI 不可用时可直接手动填写。</div>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.page-header { margin-bottom: 16px; }
.layout { display: flex; gap: 16px; align-items: flex-start; }
.form-card { flex: 1; min-width: 0; }
.ai-card { width: 400px; flex-shrink: 0; }
.ai-header { display: flex; align-items: center; justify-content: space-between; }
.ai-chat-box { height: 420px; overflow-y: auto; padding: 4px 2px; }
.msg-row { display: flex; margin-bottom: 12px; }
.msg-row.right { justify-content: flex-end; }
.bubble { max-width: 85%; padding: 8px 12px; border-radius: 8px; font-size: 13px; line-height: 1.6; white-space: pre-wrap; word-break: break-word; }
.bubble.user { background: #ecf5ff; color: #303133; }
.bubble.assistant { background: #f4f4f5; color: #303133; }
.bubble.typing { color: #909399; }
.tool-tag { font-size: 12px; color: #909399; margin-bottom: 4px; }
.draft-card { background: #fdf6ec; border: 1px solid #f5dab1; border-radius: 8px; padding: 10px; }
.draft-title { font-weight: 600; color: #b88230; margin-bottom: 8px; }
.draft-field { margin-bottom: 6px; line-height: 1.5; }
.draft-field .label { display: inline-block; width: 60px; color: #909399; font-size: 12px; }
.draft-note { color: #909399; font-size: 12px; margin: 6px 0; }
.exp-card { background: #f0f9eb; border-color: #c2e7b0; }
.tp-card { background: #f4f4ff; border-color: #c8c8f0; }
.sk-card { background: #fdf6ec; border-color: #f5dab1; }
.skill-preview { max-height: 160px; overflow-y: auto; background: #fff; border: 1px solid #e4e7ed; border-radius: 4px; padding: 8px; font-size: 12px; line-height: 1.5; white-space: pre-wrap; word-break: break-word; margin: 4px 0 8px; }
.msg-time { font-size: 11px; color: #c0c4cc; margin-top: 4px; }
.ai-input-row { display: flex; gap: 8px; margin-top: 8px; }
.ai-input-row .el-input { flex: 1; }
.ai-tip { margin-top: 8px; font-size: 12px; color: #909399; }
.actions { margin-top: 8px; }
@media (max-width: 1100px) {
  .layout { flex-direction: column; }
  .ai-card { width: 100%; }
}
</style>
