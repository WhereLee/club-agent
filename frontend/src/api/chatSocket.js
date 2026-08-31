import { Client } from '@stomp/stompjs'

// STOMP 单例连接（聊天页用）：连接复用，页面切换时 disconnectChat
// - connectHeaders 带 JWT（后端 CONNECT 帧鉴权）
// - 订阅 /topic/activity/{id} 收广播；订阅 /user/queue/errors 收业务拒绝提示
// - 断线自动重连（reconnectDelay）；心跳 10s
let client = null

export function connectChat(activityId, { onMessage, onError, onConnected }) {
  const token = localStorage.getItem('club_token')
  if (!token || client) return
  client = new Client({
    // 按当前页协议派生：HTTPS 下 ws:// 会被 mixed-content 拦截
    brokerURL: `${location.protocol === 'https:' ? 'wss://' : 'ws://'}${location.host}/ws`,
    connectHeaders: { Authorization: `Bearer ${token}` },
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      client.subscribe(`/topic/activity/${activityId}`, (frame) => {
        try {
          onMessage(JSON.parse(frame.body))
        } catch (e) {
          // 非 JSON 帧忽略
        }
      })
      client.subscribe('/user/queue/errors', (frame) => onError(frame.body))
      if (onConnected) onConnected()
    },
    onWebSocketError: () => {
      // 握手被拒（如订阅无权限时连接被关）由 onError 兜底提示
    },
    onStompError: (frame) => {
      if (onError) onError(frame.headers?.message || '连接错误')
    }
  })
  client.activate()
}

export function disconnectChat() {
  if (client) {
    try {
      client.deactivate()
    } catch (e) {
      // 忽略
    }
    client = null
  }
}

export function sendChat(activityId, content) {
  if (!client || !client.connected) return false
  client.publish({
    destination: `/app/chat/activity/${activityId}`,
    body: JSON.stringify({ content })
  })
  return true
}

export function isChatConnected() {
  return !!client && client.connected
}
