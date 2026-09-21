import { describe, expect, it } from 'vitest';
import {
  isEntryType,
  isLearningTarget,
  resolveAdminTarget,
  resolveEntryTarget,
  resolveLearningTarget,
  sanitizeRedirect
} from './auth-routing';

describe('auth routing', () => {
  it('只接受服务端定义的入口类型', () => {
    expect(isEntryType('ADMIN')).toBe(true);
    expect(isEntryType('LEARNING')).toBe(true);
    expect(isEntryType('sys_user')).toBe(false);
    expect(isEntryType(undefined)).toBe(false);
  });

  it('只保留安全站内地址', () => {
    expect(sanitizeRedirect('/content/knowledge?tab=tree#node')).toBe('/content/knowledge?tab=tree#node');
    expect(sanitizeRedirect('%2Flearning%2Ftasks%3Fday%3Dtoday')).toBe('/learning/tasks?day=today');
    expect(sanitizeRedirect('https://evil.example/path')).toBeUndefined();
    expect(sanitizeRedirect('//evil.example/path')).toBeUndefined();
    expect(sanitizeRedirect('/\\evil')).toBeUndefined();
    expect(sanitizeRedirect('/login?redirect=/index')).toBeUndefined();
    expect(sanitizeRedirect(['/learning/tasks', '/system/user'])).toBe('/learning/tasks');
    expect(sanitizeRedirect([1])).toBeUndefined();
    expect(sanitizeRedirect('%E0%A4%A')).toBeUndefined();
    expect(sanitizeRedirect('/tasks%00')).toBeUndefined();
    expect(sanitizeRedirect('/redirect/next')).toBeUndefined();
  });

  it('按身份隔离管理端和学习端目标', () => {
    expect(resolveAdminTarget('/system/user')).toBe('/system/user');
    expect(resolveAdminTarget('/learning/tasks')).toBe('/admin/index');
    expect(isLearningTarget('/learning/tasks/1')).toBe(true);
    expect(isLearningTarget('/user/profile')).toBe(false);
    expect(isLearningTarget('/system/user')).toBe(false);
  });

  it('统一登录后管理员进入后台，学员固定进入学员首页', () => {
    expect(resolveEntryTarget('ADMIN', '/system/user')).toBe('/system/user');
    expect(resolveEntryTarget('ADMIN', '/learning/tasks')).toBe('/admin/index');
    expect(resolveEntryTarget('LEARNING', '/system/user')).toBe('/learning/home');
    expect(resolveEntryTarget('LEARNING', '/learning/tasks')).toBe('/learning/home');
  });

  it('首次流程优先于学员登录前地址', () => {
    expect(resolveLearningTarget('SET_GOAL', '/learning/tasks')).toBe('/learning/onboarding');
    expect(resolveLearningTarget('START_DIAGNOSTIC', '/learning/tasks')).toBe('/learning/diagnostic/intro');
    expect(resolveLearningTarget('CONTINUE_DIAGNOSTIC', '/learning/tasks')).toBe('/learning/diagnostic/session');
    expect(resolveLearningTarget('WAIT_PROCESSING', '/learning/tasks')).toBe('/learning/home?state=processing');
    expect(resolveLearningTarget('RETRY_DIAGNOSTIC', '/learning/tasks')).toBe('/learning/diagnostic/intro?mode=retry');
    expect(resolveLearningTarget('CONTACT_SUPPORT', '/learning/tasks')).toBe('/learning/home?state=support');
  });

  it('仅在首次流程完成后恢复合法学习地址', () => {
    expect(resolveLearningTarget('ENTER_HOME', '/learning/tasks/9')).toBe('/learning/tasks/9');
    expect(resolveLearningTarget('ENTER_HOME', '/system/user')).toBe('/learning/home');
    expect(resolveLearningTarget('ENTER_HOME', 'https://evil.example')).toBe('/learning/home');
    expect(() => resolveLearningTarget('UNKNOWN' as never, '/learning/tasks')).toThrow('未知的学员流程状态');
  });
});
