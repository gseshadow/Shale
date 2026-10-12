import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: { input: ['index.html', 'foundation.html'] },
  },
  server: {
    port: 5173,
    strictPort: true,
  },
});
