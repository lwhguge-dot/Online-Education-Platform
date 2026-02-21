import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    // 当前先以工具函数单测为主，使用 node 环境即可
    environment: 'node',
    include: ['src/**/*.test.ts'],
    reporters: ['default'],
    clearMocks: true,
  },
})
