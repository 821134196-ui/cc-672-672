import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';

export default defineConfig({
  plugins: [svelte()],
  server: {
    port: 5173,
    proxy: {
      // 前端开发服务器把 /api 代理到 Quarkus 后端 8080
      '/api': 'http://localhost:8080'
    }
  }
});
