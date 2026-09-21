import { beforeEach, describe, expect, it, vi } from 'vitest';
import request from '@/utils/request';
import { getOnboardingStatus } from './onboarding';

vi.mock('@/utils/request', () => ({ default: vi.fn() }));

describe('learner onboarding API', () => {
  beforeEach(() => vi.mocked(request).mockReset());

  it('queries the onboarding status for the authenticated session', () => {
    vi.mocked(request).mockResolvedValue({ data: { nextAction: 'SET_GOAL' } } as never);

    getOnboardingStatus();

    expect(request).toHaveBeenCalledWith({
      url: '/api/learning/onboarding/status',
      method: 'get'
    });
  });
});
