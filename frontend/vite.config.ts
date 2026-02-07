import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [
    // 当前项目未使用 Element Plus 与自动导入插件，仅保留 Vue 插件
    vue(),
  ],
  server: {
    host: '0.0.0.0', // 监听所有网卡，允许Docker外部访问
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://gateway:8090', // Docker内部服务名
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '') // 根据网关配置决定是否去掉/api
      },
      '/oss': {
        target: 'http://gateway:8090',
        changeOrigin: true,
      }
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'vendor': ['vue', 'vue-router', 'pinia'],
          'lucide': ['lucide-vue-next']
        }
      }
    }
  }
})
