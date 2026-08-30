import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发代理：/auth /user /clubs /todos /logs /messages /uploads 转发到后端 8093
// 注意：API 前缀必须全部列出，漏配的路径会被 Vite SPA fallback 劫持返回 index.html（axios 解析失败报"操作失败"）
// /ws：STOMP WebSocket 代理（ws:true），聊天页走 ws://127.0.0.1:5174/ws
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5174,
    proxy: {
      '/auth': { target: 'http://127.0.0.1:8093', changeOrigin: true },
      '/user': { target: 'http://127.0.0.1:8093', changeOrigin: true },
      '/clubs': { target: 'http://127.0.0.1:8093', changeOrigin: true },
      '/todos': { target: 'http://127.0.0.1:8093', changeOrigin: true },
      '/logs': { target: 'http://127.0.0.1:8093', changeOrigin: true },
      '/messages': { target: 'http://127.0.0.1:8093', changeOrigin: true },
      '/uploads': { target: 'http://127.0.0.1:8093', changeOrigin: true },
      '/ws': { target: 'ws://127.0.0.1:8093', ws: true, changeOrigin: true }
    }
  }
})
