import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

/**
 * 应用层 = SPA，产物 dist/ 由 z-dms-admin 的 maven-resources-plugin 拷进
 * BOOT-INF/classes/static/（Mode 1 合体）。
 *
 * 【base 必须与 admin 的 context-path 一致】—— base 与 server.servlet.context-path
 * 不一致时，页面能打开但 /assets/* 全 404，且 `vite dev` 阶段根本复现不出来。
 * 本仓 z-dms-admin 的 context-path 固定为 `/`，所以这里固定 '/'。
 */
export default defineConfig({
  base: '/',
  resolve: {
    dedupe: ['react', 'react-dom', 'react-router-dom', 'antd', '@ant-design/icons', 'axios'],
  },
  plugins: [react()],
  server: {
    port: 5183,
    host: '0.0.0.0',
    proxy: {
      // dev 时前端跑在 5183、后端跑在 18098，用 proxy 抹掉跨域
      '/api': { target: 'http://localhost:18098', changeOrigin: true },
    },
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    emptyOutDir: true,
    sourcemap: false,
    // antd 6 全量 import 的 vendor chunk 约 1.1MB（gzip ~370KB）—— 这是
    // "React + antd 控制台"这个技术栈的体积下限，不是配置出错。
    chunkSizeWarningLimit: 1300,
    // 不做 manualChunks 手工分派（手工拆会造出循环 chunk，浏览器里直接白屏）。
    rollupOptions: {},
  },
})
