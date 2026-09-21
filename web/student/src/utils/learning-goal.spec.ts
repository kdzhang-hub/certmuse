import { describe, expect, it } from 'vitest';
import type { CreateLearningGoalRequest, LearningGoalOptionsVo } from '@/api/certmuse/learning/goals';
import { clampDailyMinutes, requestIdentityFor } from './learning-goal';

const payload: CreateLearningGoalRequest = {
  certificationId: '900000000000000001',
  targetExamYear: 2027,
  targetExamMonth: 5,
  dailyMinutes: 30
};

describe('learning goal contract helpers', () => {
  it('reuses a request id only for the same normalized payload', () => {
    const first = requestIdentityFor(payload, undefined, () => 'first');
    expect(requestIdentityFor({ ...payload }, first, () => 'second').id).toBe('first');
    expect(requestIdentityFor({ ...payload, dailyMinutes: 31 }, first, () => 'second').id).toBe('second');
  });

  it('clamps daily minutes using server options', () => {
    const options = { dailyMinutes: { min: 15, max: 300, defaultValue: 30 } } as LearningGoalOptionsVo;
    expect(clampDailyMinutes(10, options)).toBe(15);
    expect(clampDailyMinutes(301, options)).toBe(300);
    expect(clampDailyMinutes(undefined, options)).toBe(30);
  });

  it('keeps the request body limited to the four U03 fields', () => {
    expect(Object.keys(payload)).toEqual(['certificationId', 'targetExamYear', 'targetExamMonth', 'dailyMinutes']);
  });
});
