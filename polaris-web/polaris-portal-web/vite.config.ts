import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// SSO 网关地址：本地为 9080，生产由 nginx 同域转发
const target = process.env.VITE_SSO_TARGET || 'http://127.0.0.1:9080'

export default defineConfig({
  // 门户前台与 CMS 管理端共用 polaris-cms 域名，前台挂在 /portal/ 子路径（对齐 forum-admin 的 /admin/）
  base: '/portal/',
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      '@shared': fileURLToPath(new URL('../_shared', import.meta.url))
    }
  },
  server: {
    port: 5178,
    proxy: {
      '/api': { target, changeOrigin: true },
      '/actuator': { target, changeOrigin: true }
    }
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 2000
  }
})
