import { describe, expect, it } from 'vitest';
import {
  isConsistentOnboardingStatus,
  resolveOnboardingTarget,
  resolveOnboardingTargetWithSession
} from './onboarding-routing';

describe('onboarding routing', () => {
  it('maps only known actions to local routes', () => {
    expect(resolveOnboardingTarget('SET_GOAL')).toBe('/learning/onboarding');
    expect(resolveOnboardingTarget('START_DIAGNOSTIC')).toBe('/learning/diagnostic/intro');
    expect(resolveOnboardingTarget('server://redirect')).toBeUndefined();
  });

  it('keeps diagnostic routes bound to the server-owned session', () => {
    expect(resolveOnboardingTargetWithSession('CONTINUE_DIAGNOSTIC', '123')).toBe(
      '/learning/diagnostic/session?sessionId=123'
    );
    expect(resolveOnboardingTargetWithSession('WAIT_PROCESSING', '123')).toBe(
      '/learning/diagnostic/result?sessionId=123'
    );
    expect(resolveOnboardingTargetWithSession('SET_GOAL', '123')).toBe('/learning/onboarding');
  });

  it('rejects an inconsistent SET_GOAL state', () => {
    expect(
      isConsistentOnboardingStatus({
        goalStatus: 'ACTIVE',
        diagnosticStatus: 'NOT_STARTED',
        nextAction: 'SET_GOAL'
      })
    ).toBe(false);
  });
});
