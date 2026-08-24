import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发代理：/auth /user /uploads 转发到后端 8093
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5174,
    proxy: {
      '/auth': { target: 'http://127.0.0.1:8093', changeOrigin: true },
      '/user': { target: 'http://127.0.0.1:8093', changeOrigin: true },
      '/uploads': { target: 'http://127.0.0.1:8093', changeOrigin: true }
    }
  }
})
