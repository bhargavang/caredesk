import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// In development the API runs on :8080 and Vite proxies /api to it, so the
// browser sees one origin. That keeps the staff session cookie first-party
// (SameSite=Strict), exactly as it is behind nginx or a hosting rewrite.
const api = process.env.API_PROXY_TARGET || 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: api, changeOrigin: false },
      '/swagger-ui': api,
      '/v3/api-docs': api,
    },
  },
  build: {
    sourcemap: false,
  },
})
