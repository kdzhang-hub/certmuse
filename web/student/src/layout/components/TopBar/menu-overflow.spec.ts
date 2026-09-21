import { describe, expect, it } from 'vitest';
import { resolveTopbarVisibleMenuCount } from './menu-overflow';

describe('resolveTopbarVisibleMenuCount', () => {
  it('shows every route when the container can fit them all', () => {
    expect(resolveTopbarVisibleMenuCount(4, 466)).toBe(4);
  });

  it('reserves room for the more-menu control when routes overflow', () => {
    expect(resolveTopbarVisibleMenuCount(7, 700)).toBe(5);
  });

  it('keeps one direct route available in an extremely narrow container', () => {
    expect(resolveTopbarVisibleMenuCount(7, 0)).toBe(1);
  });

  it('recalculates from the current route count', () => {
    expect(resolveTopbarVisibleMenuCount(2, 200)).toBe(1);
    expect(resolveTopbarVisibleMenuCount(1, 200)).toBe(1);
    expect(resolveTopbarVisibleMenuCount(0, 200)).toBe(0);
  });
});
