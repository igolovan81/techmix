import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 4203,
    proxy: {
      '/accounts': 'http://localhost:8099',
      '/transfers': 'http://localhost:8099',
    },
  },
});
