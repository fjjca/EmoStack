import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    // 同时监听 IPv4/IPv6，避免内置浏览器解析 localhost 为 ::1 时连不上导致白屏
    host: true,
    proxy: {
      '/api': {
        target: 'http://localhost:1236',
        changeOrigin: true
      }
    }
  }
})
