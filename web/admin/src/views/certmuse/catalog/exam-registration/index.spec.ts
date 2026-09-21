import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';

const replace = vi.hoisted(() => vi.fn());
vi.mock('vue-router', () => ({ useRouter: () => ({ replace }) }));

import ExamRegistrationRedirect from './index.vue';

describe('exam registration compatibility route', () => {
  it('redirects the old entry to the implemented exam schedule workspace', async () => {
    mount(ExamRegistrationRedirect);
    await flushPromises();
    expect(replace).toHaveBeenCalledWith('/content/exam-schedules');
  });
});
