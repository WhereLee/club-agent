<template>
  <div class="chat-page">
    <div class="chat-header">
      <el-button link @click="router.push(`/clubs/${clubId}/activities/${activityId}`)">← 返回活动</el-button>
      <span class="title">讨论群</span>
      <el-tag size="small" :type="connected ? 'success' : 'danger'">{{ connected ? '已连接' : '连接中…' }}</el-tag>
    </div>

    <div class="chat-body" ref="bodyRef">
      <div v-for="m in messages" :key="m.id" class="msg" :class="{ mine: String(m.senderId) === myUserId }">
        <div class="meta">
          <span class="name">{{ m.senderName }}</span>
          <span class="time">{{ (m.createdAt || '').slice(11, 16) }}</span>
        </div>
        <div class="bubble">{{ m.content }}</div>
      </div>
      <el-empty v-if="!messages.length && loaded" description="还没有消息，来说两句吧" :image-size="80" />
    </div>

    <div class="chat-input">
      <el-input
        v-model="draft"
        placeholder="输入消息，Enter 发送"
        maxlength="2000"
        @keyup.enter="send"
      />
      <el-button type="primary" :disabled="!connected || !draft.trim()" @click="send">发送</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getChatMessages } from '../api/activity'
import { connectChat, disconnectChat, sendChat } from '../api/chatSocket'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const clubId = route.params.clubId
const activityId = route.params.id
const userStore = useUserStore()
const myUserId = String(userStore.userInfo?.userId ?? userStore.userInfo?.id ?? '')

const messages = ref([])
const draft = ref('')
const connected = ref(false)
const loaded = ref(false)
const bodyRef = ref(null)

function scrollBottom() {
  nextTick(() => {
    const el = bodyRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

async function loadHistory() {
  try {
    const res = await getChatMessages(clubId, activityId, 1, 50)
    messages.value = (res.data?.records || []).slice().reverse() // 倒序接口 → 正序展示
    loaded.value = true
    scrollBottom()
  } catch (e) {
    // 无权限等错误已由 request 统一提示
  }
}

function send() {
  const content = draft.value.trim()
  if (!content) return
  if (sendChat(activityId, content)) {
    draft.value = ''
  } else {
    ElMessage.warning('连接未就绪，请稍候重试')
  }
}

onMounted(async () => {
  await loadHistory()
  connectChat(activityId, {
    onMessage: (m) => {
      messages.value.push(m)
      scrollBottom()
    },
    onError: (msg) => ElMessage.error(msg || '操作被拒绝'),
    onConnected: () => {
      connected.value = true
    }
  })
})

onUnmounted(() => {
  disconnectChat()
})
</script>

<style scoped>
.chat-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  max-width: 820px;
  margin: 0 auto;
  padding: 0 12px;
}
.chat-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 4px;
  border-bottom: 1px solid #eee;
}
.chat-header .title {
  font-size: 16px;
  font-weight: 600;
  flex: 1;
}
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 14px 6px;
}
.msg {
  margin-bottom: 12px;
}
.msg .meta {
  font-size: 12px;
  color: #999;
  margin-bottom: 2px;
}
.msg.mine .meta {
  text-align: right;
}
.msg .bubble {
  display: inline-block;
  max-width: 75%;
  background: #f0f2f5;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 14px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}
.msg.mine .bubble {
  background: #409eff;
  color: #fff;
}
.chat-input {
  display: flex;
  gap: 8px;
  padding: 10px 0 14px;
}
</style>
