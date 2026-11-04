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
  server: { port: 5173, strictPort: true },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    // Node 25+ ships a global localStorage stub that shadows jsdom's; turn it off in test workers.
    execArgv: ['--no-experimental-webstorage'],
    css: false,
  },
});
