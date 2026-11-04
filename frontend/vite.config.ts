/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

export default defineConfig({
  plugins: [react()],
  // Read VITE_* variables from the repository-root .env shared with the backend.
  // Only VITE_-prefixed keys are ever exposed to browser code.
  envDir: '..',
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  // Same-origin API in development, mirroring the Vercel rewrite in production.
  server: {
    port: 5173,
    strictPort: true,
    proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: false } },
    // Tests read the backend ErrorCode enum to check every code is translated; nothing else is exposed.
    fs: {
      allow: ['.', ...(process.env.VITEST ? ['../backend/src/main/java/com/bookeasy/common/exception'] : [])],
    },
  },
  build: {
    rollupOptions: {
      output: {
        // Stable vendor chunks cache well across deploys.
        manualChunks: {
          react: ['react', 'react-dom', 'react-router'],
          data: ['@tanstack/react-query', 'axios'],
          i18n: ['i18next', 'react-i18next', 'i18next-browser-languagedetector'],
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    // Node 25+ ships a global localStorage stub that shadows jsdom's; turn it off in test workers.
    execArgv: ['--no-experimental-webstorage'],
    css: false,
  },
});
