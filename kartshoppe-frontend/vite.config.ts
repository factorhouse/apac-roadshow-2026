import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  base: '/',
  build: {
    outDir: 'dist',
    emptyOutDir: true
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: (path) => path
      },
      '/ecommerce': {
        target: 'ws://localhost:8081',
        ws: true,
        changeOrigin: true
      },
      '/chat': {
        target: 'ws://localhost:8081',
        ws: true,
        changeOrigin: true
      }
    },
  },
})