import { describe, expect, it } from 'vitest';
import { isArray, isExternal, isHttp, isPathMatch, validEmail } from './validate';

describe('validate utilities', () => {
  it('matches single- and multi-segment wildcard paths', () => {
    expect(isPathMatch('/system/*', '/system/user')).toBe(true);
    expect(isPathMatch('/system/*', '/system/user/profile')).toBe(false);
    expect(isPathMatch('/system/**', '/system/user/profile')).toBe(true);
  });

  it('recognises supported external links', () => {
    expect(isExternal('https://certmuse.example')).toBe(true);
    expect(isExternal('mailto:support@certmuse.example')).toBe(true);
    expect(isExternal('/certmuse/catalog')).toBe(false);
  });

  it('recognises HTTP URLs', () => {
    expect(isHttp('http://certmuse.example')).toBe(true);
    expect(isHttp('https://certmuse.example')).toBe(true);
    expect(isHttp('/certmuse/catalog')).toBe(false);
  });

  it('validates common email boundaries', () => {
    expect(validEmail('user@example.com')).toBe(true);
    expect(validEmail('user@example')).toBe(false);
    expect(validEmail('')).toBe(false);
  });

  it('distinguishes arrays from strings', () => {
    expect(isArray(['subject-1'])).toBe(true);
    expect(isArray('subject-1')).toBe(false);
  });
});
