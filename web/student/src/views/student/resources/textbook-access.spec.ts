import { describe, expect, it } from 'vitest';
import { resolveTextbookAccessState } from './textbook-access';

describe('resolveTextbookAccessState', () => {
  it('requires first-goal setup before the learner can have textbook eligibility', () => {
    expect(resolveTextbookAccessState('SET_GOAL', false)).toBe('setup_required');
  });

  it('keeps ordinary empty results distinct from failed loading', () => {
    expect(resolveTextbookAccessState('ENTER_HOME', false)).toBe('ready');
    expect(resolveTextbookAccessState('ENTER_HOME', true)).toBe('load_failed');
  });
});
