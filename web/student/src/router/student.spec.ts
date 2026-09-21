import { describe, expect, it, vi } from 'vitest';
import { seedStudentRoutes, studentRoutes } from './student';

const permissionSpies = vi.hoisted(() => ({
  setRoutes: vi.fn(),
  setSidebarRouters: vi.fn(),
  setDefaultRoutes: vi.fn(),
  setTopbarRoutes: vi.fn()
}));

vi.mock('@/layout/index.vue', () => ({ default: {} }));
vi.mock('@/store/modules/permission', () => ({
  usePermissionStore: () => permissionSpies
}));
describe('student route contract', () => {
  it('将根路径重定向到学习首页，保证错误页能够返回学生端', () => {
    const rootRoute = studentRoutes.find(route => route.path === '/');

    expect(rootRoute?.redirect).toBe('/learning/home');
  });

  it('使用本地学习路由初始化正式学生菜单', () => {
    seedStudentRoutes();

    expect(permissionSpies.setRoutes).toHaveBeenCalledWith(studentRoutes);
    expect(permissionSpies.setSidebarRouters).toHaveBeenCalledWith(studentRoutes);
    expect(permissionSpies.setDefaultRoutes).toHaveBeenCalledWith(studentRoutes);
    expect(permissionSpies.setTopbarRoutes).toHaveBeenCalledWith(studentRoutes);
  });

  it('registers independent past-paper and simulation exam and result pages', () => {
    const paths = studentRoutes.flatMap(route => [
      route.path,
      ...(route.children ?? []).map(child => `${route.path.replace(/\/$/, '')}/${child.path}`)
    ]);

    expect(paths).toEqual(
      expect.arrayContaining([
        '/learning/question-bank/past-papers/exam',
        '/learning/question-bank/past-papers/result',
        '/learning/question-bank/mock-exams/exam',
        '/learning/question-bank/mock-exams/result'
      ])
    );
  });
});
