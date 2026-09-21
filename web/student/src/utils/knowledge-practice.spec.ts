import { describe, expect, it } from 'vitest';
import { practiceRequestIdentityFor } from './knowledge-practice';

describe('knowledge practice request identity', () => {
  it('reuses the normalized UUID only for the same start payload', () => {
    const payload = { knowledgePointId: '900000000000000101', expectedGoalVersion: 4 };
    const first = practiceRequestIdentityFor(payload, undefined, () => 'A1B2C3D4-E5F6-0000-1111-222233334444');

    expect(first.id).toBe('a1b2c3d4-e5f6-0000-1111-222233334444');
    expect(practiceRequestIdentityFor({ ...payload }, first, () => 'second').id).toBe(first.id);
    expect(practiceRequestIdentityFor({ ...payload, expectedGoalVersion: 5 }, first, () => 'second').id).toBe('second');
  });
});
