import { describe, expect, it } from 'vitest';
import { isEntryType, isLearningEntry } from './auth-entry';

describe('student auth entry', () => {
  it('只接受服务端定义的入口类型', () => {
    expect(isEntryType('ADMIN')).toBe(true);
    expect(isEntryType('LEARNING')).toBe(true);
    expect(isEntryType('app_user')).toBe(false);
    expect(isEntryType(undefined)).toBe(false);
  });

  it('只允许学习入口进入学生端', () => {
    expect(isLearningEntry('LEARNING')).toBe(true);
    expect(isLearningEntry('ADMIN')).toBe(false);
  });
});
