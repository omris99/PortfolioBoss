import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // IBBot's UI takes 5173, so this one gets its own port. Main opens this exact port in the
    // browser, so fail loudly instead of silently moving to another one.
    port: 5174,
    strictPort: true,
    // The Java API (Main, port 8080) serves /api/portfolio; proxying keeps the UI same-origin.
    proxy: { '/api': 'http://127.0.0.1:8080' },
  },
})
