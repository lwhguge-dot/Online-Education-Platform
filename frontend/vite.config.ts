import { defineConfig, loadEnv } from 'vite'
import tailwindcss from '@tailwindcss/vite'
import vue from '@vitejs/plugin-vue'
import viteCompression from 'vite-plugin-compression'
import { visualizer } from 'rollup-plugin-visualizer'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const gatewayTarget = env.VITE_GATEWAY_TARGET || 'http://demo-gateway:8090'

  return {
    plugins: [
      tailwindcss(),
      vue(),
      VitePWA({
        registerType: 'autoUpdate',
        workbox: {
          globPatterns: ['**/*.{js,css,html,ico,png,svg,woff,woff2}'],
          runtimeCaching: [
            {
              urlPattern: /^https:\/\/api\..*/i,
              handler: 'NetworkFirst',
              options: { cacheName: 'api-cache', expiration: { maxEntries: 100, maxAgeSeconds: 86400 } }
            }
          ]
        },
        manifest: {
          name: 'Edu Platform',
          short_name: 'Edu',
          description: '在线教育平台',
          theme_color: '#3b82f6',
          background_color: '#ffffff',
          display: 'standalone',
          icons: [
            { src: '/pwa-192x192.svg', sizes: '192x192', type: 'image/svg+xml' },
            { src: '/pwa-512x512.svg', sizes: '512x512', type: 'image/svg+xml' }
          ]
        }
      }),
      viteCompression({
        verbose: true,
        disable: false,
        threshold: 10240,
        algorithm: 'gzip',
        ext: '.gz',
      }),
      visualizer({
        open: false,
        gzipSize: true,
        brotliSize: true,
        filename: 'stats.html'
      }),
    ],
    server: {
      host: '0.0.0.0', // 监听所有网卡，允许Docker外部访问
      port: 3000,
      proxy: {
        '/api': {
          target: gatewayTarget,
          changeOrigin: true,
        },
        // WebSocket 通知通道代理（修复浏览器直连 localhost:80 时握手超时）
        '/ws': {
          target: gatewayTarget,
          changeOrigin: true,
          ws: true,
        },
        '/oss': {
          target: gatewayTarget,
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
  }
})
