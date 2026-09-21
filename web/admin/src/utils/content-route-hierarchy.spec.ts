import type { RouteRecordRaw } from 'vue-router';
import { describe, expect, it, vi } from 'vitest';

vi.hoisted(() => {
  // Vitest does not load the application's Vite auto-import plugin.
  // The router import initializes i18n, which reads this composable.
  (globalThis as typeof globalThis & { useStorage: () => { value: string } }).useStorage = () => ({ value: 'zh-cn' });
});

import { constantRoutes } from '@/router';

describe('考纲管理回退路由', () => {
  it('keeps 内容中心 as the breadcrumb parent', () => {
    const contentRoute = constantRoutes.find(route => route.path === '/content') as RouteRecordRaw | undefined;
    const knowledgeRoute = contentRoute?.children?.find(route => route.path === 'knowledge');

    expect(contentRoute?.meta?.title).toBe('内容中心');
    expect(knowledgeRoute?.meta?.title).toBe('考纲管理');
    expect(knowledgeRoute?.meta?.activeMenu).toBe('/content/knowledge');
  });
});
