import { describe, expect, it, vi } from 'vitest';
import { buttonByText, mountStudentView } from '../view-test-utils';
import ErrorView from './error.vue';

const router = vi.hoisted(() => ({ go: vi.fn() }));
vi.mock('vue-router', () => ({ useRouter: () => router }));

describe('onboarding error recovery', () => {
  it('explains the safe failure and reloads only on user request', async () => {
    const wrapper = mountStudentView(ErrorView);
    expect(wrapper.text()).toContain('首次学习流程状态异常');
    expect(wrapper.text()).toContain('系统无法确认下一步操作');
    expect(router.go).not.toHaveBeenCalled();
    await buttonByText(wrapper, '刷新页面').trigger('click');
    expect(router.go).toHaveBeenCalledWith(0);
  });
});
