import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// 后端地址可用环境变量覆盖：VITE_API_TARGET=http://localhost:18080 npm run dev
const apiTarget = process.env.VITE_API_TARGET ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          echarts: ['echarts', 'echarts-for-react'],
          antd: ['antd', '@ant-design/icons'],
          vendor: ['react', 'react-dom', 'react-router-dom', 'dayjs'],
        },
      },
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/v1': {
        target: apiTarget,
        changeOrigin: true,
      },
    },
  },
});
