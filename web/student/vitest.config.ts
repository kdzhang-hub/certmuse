import vue from '@vitejs/plugin-vue';
import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vitest/config';
import createAutoImport from './vite/plugins/auto-import';
import createComponents from './vite/plugins/components';

export default defineConfig({
  // Match production SFC transformation: diagnostic views rely on Vue and
  // Element Plus auto imports and must not pass only because unresolved tags
  // were silently rendered as custom elements in unit tests.
  plugins: [vue(), createAutoImport(), createComponents()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  test: {
    environment: 'jsdom',
    include: [
      'src/api/certmuse/**/*.spec.ts',
      'src/components/exam/**/*.spec.ts',
      'src/views/student/dashboard/**/*.spec.ts',
      'src/views/student/onboarding/**/*.spec.ts',
      'src/views/student/diagnostic/**/*.spec.ts',
      'src/views/student/resources/**/*.spec.ts',
      'src/views/student/question-bank/**/*.spec.ts',
      'src/hooks/exam/**/*.spec.ts',
      'src/layout/components/**/*.spec.ts',
      'src/permission.spec.ts',
      'src/router/student.spec.ts',
      'src/store/modules/user.spec.ts',
      'src/utils/auth-entry.spec.ts',
      'src/utils/learning-goal.spec.ts',
      'src/utils/question-image.spec.ts',
      'src/utils/knowledge-practice.spec.ts',
      'src/utils/learning-login-redirect.spec.ts',
      'src/utils/onboarding-routing.spec.ts',
      'src/utils/pdf-reader.spec.ts',
      'src/utils/public-browse-certification.spec.ts'
    ],
    exclude: ['src/**/mock.spec.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'json-summary'],
      // Auditable denominator: the implemented, server-backed first-learning
      // flow.  Upstream RuoYi code, Mock-preview adapters, and explicitly
      // unimplemented holding/preview pages are not production coverage.
      include: [
        'src/api/certmuse/learning/**/*.ts',
        'src/permission.ts',
        'src/router/student.ts',
        'src/router/student-flow.ts',
        'src/router/student-flow-routes.ts',
        'src/store/modules/user.ts',
        'src/components/exam/**/*.vue',
        'src/hooks/exam/**/*.ts',
        'src/utils/auth-entry.ts',
        'src/utils/learning-goal.ts',
        'src/utils/learning-login-redirect.ts',
        'src/utils/onboarding-routing.ts',
        'src/views/student/onboarding/**/*.vue',
        'src/views/student/diagnostic/**/*.vue'
      ],
      exclude: [
        'src/**/*.d.ts',
        'src/**/*.spec.ts',
        'src/**/*.test.ts',
        'src/**/mock.ts',
        // Mock adapters and preview-only routes do not describe production
        // behaviour; nor do explicitly unimplemented holding pages.
        'src/api/student/**',
        'src/router/student-preview.ts',
        'src/views/student/onboarding/next-step.vue',
        'src/views/student/question-bank/**',
        'src/views/student/session/**'
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
