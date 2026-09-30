import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Frontend chạy ở cổng 3000 — bank-service đã cho phép cổng này gọi API (CORS)
export default defineConfig({
  plugins: [react()],
  server: { port: 3000 },
})
