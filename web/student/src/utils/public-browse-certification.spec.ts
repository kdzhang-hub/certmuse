import { afterEach, describe, expect, it } from 'vitest';
import { getPublicBrowseCertificationId, setPublicBrowseCertificationId } from './public-browse-certification';

describe('public browsing certification', () => {
  afterEach(() => window.sessionStorage.clear());

  it('keeps the selected qualification in the browser session only', () => {
    setPublicBrowseCertificationId('42');

    expect(getPublicBrowseCertificationId()).toBe('42');

    setPublicBrowseCertificationId('');
    expect(getPublicBrowseCertificationId()).toBe('');
  });
});
