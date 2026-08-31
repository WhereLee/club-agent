<template>
  <div class="qa-page">
    <!-- 左侧：会话列表（管理层本人；跨届经验传承入口） -->
    <el-card shadow="never" class="session-panel">
      <template #header>
        <div class="panel-header">
          <span>经验问答</span>
          <el-button size="small" type="primary" @click="onNewSession">新建会话</el-button>
        </div>
      </template>
      <div v-if="!sessions.length" class="hint">暂无会话，点击「新建会话」向社团知识库提问</div>
      <div
        v-for="s in sessions"
        :key="s.id"
        class="session-item"
        :class="{ active: activeSessionId === s.id }"
        @click="onSelectSession(s)"
      >
        <div class="session-title">{{ s.title }}</div>
        <div class="session-time">{{ s.updatedAt }}</div>
        <el-icon class="session-del" @click.stop="onDeleteSession(s)"><Delete /></el-icon>
      </div>
    </el-card>

    <!-- 右侧：对话区 -->
    <el-card shadow="never" class="chat-panel">
      <template #header>
        <span>{{ activeSession ? activeSession.title : '社团历史经验问答' }}</span>
      </template>
      <div v-if="!activeSession" class="empty-tip">
        <el-empty description="选择或新建一个会话，向社团知识库提问历史活动经验" />
      </div>
      <template v-else>
        <div ref="msgBox" class="msg-list">
          <template v-for="m in messages" :key="m.id">
            <!-- 工具调用：折叠展示检索过程（审计与溯源） -->
            <el-collapse v-if="m.role === 'tool'" class="tool-item">
              <el-collapse-item :title="`检索过程：${m.toolName || 'search_knowledge'}`">
                <pre class="tool-content">{{ m.content }}</pre>
              </el-collapse-item>
            </el-collapse>
            <div v-else class="msg-row" :class="m.role">
              <div class="msg-role">{{ m.role === 'user' ? '我' : 'AI' }}</div>
              <div class="msg-content">{{ m.content }}</div>
            </div>
          </template>
          <div v-if="sending" class="msg-row assistant">
            <div class="msg-role">AI</div>
            <div class="msg-content thinking">正在检索社团知识库并整理回答…</div>
          </div>
        </div>
        <div class="input-bar">
          <el-input
            v-model="input"
            type="textarea"
            :rows="2"
            maxlength="2000"
            placeholder="例如：以前办过哪些户外活动？预算一般怎么控制？"
            @keydown.enter.exact.prevent="onSend"
          />
          <el-button type="primary" :loading="sending" :disabled="!input.trim()" @click="onSend">提问</el-button>
        </div>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'
import { chatQa, createQaSession, deleteQaSession, getQaMessages, listQaSessions } from '../api/qa'

const route = useRoute()
const router = useRouter()
const clubId = route.params.clubId

const sessions = ref([])
const activeSessionId = ref(null)
const activeSession = ref(null)
const messages = ref([])
const input = ref('')
const sending = ref(false)
const msgBox = ref(null)

async function loadSessions(keepActive = true) {
  const r = await listQaSessions(clubId)
  sessions.value = r.data || []
  if (!keepActive || !sessions.value.some(s => s.id === activeSessionId.value)) {
    activeSessionId.value = null
    activeSession.value = null
    messages.value = []
  }
}

async function onNewSession() {
  try {
    const r = await createQaSession(clubId, '')
    await loadSessions(false)
    await onSelectSession(r.data)
  } catch (e) { /* 拦截器已提示 */ }
}

async function onSelectSession(s) {
  activeSessionId.value = s.id
  activeSession.value = s
  try {
    const r = await getQaMessages(clubId, s.id)
    messages.value = r.data || []
    await nextTick()
    scrollBottom()
  } catch (e) { /* 拦截器已提示 */ }
}

async function onDeleteSession(s) {
  try {
    await ElMessageBox.confirm(`删除会话「${s.title}」？历史消息不可恢复。`, '确认删除', { type: 'warning' })
  } catch { return }
  try {
    await deleteQaSession(clubId, s.id)
    ElMessage.success('已删除')
    await loadSessions(false)
  } catch (e) { /* 拦截器已提示 */ }
}

async function onSend() {
  const q = input.value.trim()
  if (!q || sending.value || !activeSessionId.value) return
  input.value = ''
  sending.value = true
  // 乐观展示本轮提问（后端返回完整会话后整体替换，事实源以服务端为准）
  messages.value.push({ id: `tmp-${Date.now()}`, role: 'user', content: q })
  await nextTick()
  scrollBottom()
  try {
    const r = await chatQa(clubId, activeSessionId.value, q)
    messages.value = r.data || []
    await loadSessions()   // 首问自动命名后刷新标题
    await nextTick()
    scrollBottom()
  } catch (e) {
    // 失败时回到服务端事实（user 消息已留痕，刷新可见）
    try {
      const r = await getQaMessages(clubId, activeSessionId.value)
      messages.value = r.data || []
    } catch { /* 忽略 */ }
  } finally {
    sending.value = false
  }
}

function scrollBottom() {
  if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight
}

onMounted(loadSessions)
</script>

<style scoped>
.qa-page { display: flex; gap: 16px; padding: 16px; align-items: stretch; }
.session-panel { width: 260px; flex-shrink: 0; }
.chat-panel { flex: 1; display: flex; flex-direction: column; }
.panel-header { display: flex; justify-content: space-between; align-items: center; }
.hint { color: #909399; font-size: 12px; padding: 8px 0; }
.session-item { position: relative; padding: 8px 26px 8px 10px; border-radius: 6px; cursor: pointer; }
.session-item:hover { background: #f5f7fa; }
.session-item.active { background: #ecf5ff; }
.session-title { font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.session-time { font-size: 11px; color: #909399; margin-top: 2px; }
.session-del { position: absolute; right: 8px; top: 10px; color: #c0c4cc; }
.session-del:hover { color: #f56c6c; }
.msg-list { height: 520px; overflow-y: auto; padding-right: 6px; }
.msg-row { display: flex; gap: 8px; margin-bottom: 12px; }
.msg-role { flex-shrink: 0; width: 28px; height: 28px; border-radius: 50%; background: #409eff; color: #fff;
  font-size: 12px; display: flex; align-items: center; justify-content: center; }
.msg-row.user .msg-role { background: #67c23a; }
.msg-content { background: #f5f7fa; border-radius: 8px; padding: 8px 12px; white-space: pre-wrap;
  font-size: 13px; line-height: 1.7; max-width: 80%; }
.msg-row.user .msg-content { background: #f0f9eb; }
.msg-content.thinking { color: #909399; }
.tool-item { margin: 0 0 12px 36px; max-width: 80%; }
.tool-content { white-space: pre-wrap; font-size: 12px; color: #606266; margin: 0; }
.input-bar { display: flex; gap: 8px; margin-top: 12px; align-items: flex-end; }
.input-bar .el-button { height: 54px; }
.empty-tip { padding: 40px 0; }
</style>
