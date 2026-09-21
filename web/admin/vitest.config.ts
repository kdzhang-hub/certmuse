import vue from '@vitejs/plugin-vue';
import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vitest/config';
import createAutoImport from './vite/plugins/auto-import';

export default defineConfig({
  // Match the production Vite transform for composables while leaving
  // component resolution to each test's explicit stubs or Element Plus plugin.
  plugins: [vue(), createAutoImport()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  test: {
    environment: 'jsdom',
    // 自研行为测试只从 CertMuse 目录收集；RuoYi 源码仍参与 typecheck 和 production build。
    include: [
      'src/api/certmuse/**/*.spec.ts',
      'src/views/certmuse/**/*.spec.ts',
      'src/views/public/**/*.spec.ts',
      'src/components/certmuse/**/*.spec.ts',
      'src/router/legacy-certmuse-redirects.spec.ts',
      // Only these two utilities are CertMuse behaviour; the neighbouring
      // RuoYi utility test suite remains outside this product test scope.
      'src/utils/auth-routing.spec.ts',
      'src/utils/import-progress-events.spec.ts'
    ],
    // Mock 自测不属于产品行为测试，避免用假数据用例虚高正式测试数量。
    exclude: [
      'src/**/mock.spec.ts',
      // This helper belongs solely to routes explicitly marked pending integration.
      'src/components/certmuse/EmptyBusinessPage.spec.ts'
    ],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'json-summary'],
      // 这里只统计 CertMuse 自研前端代码。RuoYi 原生代码仍会参与构建和测试执行，
      // 但不作为本项目自研覆盖率的分母。
      include: [
        'src/api/certmuse/**/*.{ts,tsx,vue}',
        'src/views/certmuse/**/*.{ts,tsx,vue}',
        'src/components/certmuse/**/*.{ts,tsx,vue}',
        // These two utilities are CertMuse product behaviour even though they
        // sit next to upstream utilities: authentication entry routing and
        // import-progress push dispatch are both imported by the running app.
        'src/utils/auth-routing.ts',
        'src/utils/import-progress-events.ts'
      ],
      // 以下路由目前仍是产品占位页，尚未进入实际实现范围，不计入覆盖率分母。
      exclude: [
        'src/**/*.d.ts',
        'src/**/*.spec.ts',
        'src/**/*.test.ts',
        'src/**/mock.ts',
        // 未接入重复本地文件：生产代码统一从同目录 index.ts 导入，保留这些
        // 用户本地文件但不把无人使用的副本计入产品覆盖率分母。
        'src/api/certmuse/catalog/import/real.ts',
        'src/api/certmuse/question/paper-import/real.ts',
        'src/components/certmuse/EmptyBusinessPage.vue',
        // These remaining routes only render EmptyBusinessPage, whose UI
        // explicitly says the function is still pending integration.
        'src/views/certmuse/insight/**',
        'src/views/certmuse/learning/rule/**'
      ],
      thresholds: {
        statements: 90,
        branches: 90,
        functions: 90,
        lines: 90
      }
    }
  }
});
