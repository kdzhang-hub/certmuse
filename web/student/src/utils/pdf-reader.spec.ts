import { describe, expect, it } from 'vitest';
import { isBlankPdfSample, resolvePdfReaderUrl, toNativePdfPreviewUrl } from '@/views/student/resources/pdf-reader';

describe('PDF reader URL and canvas compatibility helpers', () => {
  it('routes backend reader paths through the configured API gateway and preserves opaque paths', () => {
    expect(resolvePdfReaderUrl('/api/reader/textbook-pdfs/ticket', '/prod-api/')).toBe('/prod-api/api/reader/textbook-pdfs/ticket');
    expect(resolvePdfReaderUrl('https://example.test/reader', '/prod-api')).toBe('https://example.test/reader');
  });

  it('sets the native PDF destination page without changing the ticket path', () => {
    expect(toNativePdfPreviewUrl('/prod-api/api/reader/textbook-pdfs/ticket?x=1', 32)).toBe('/prod-api/api/reader/textbook-pdfs/ticket?x=1#page=32');
  });

  it('treats sparse decoder artifacts as blank but retains a visibly rendered page', () => {
    const sample = new Uint8ClampedArray(100 * 4).fill(255);
    sample.set([12, 12, 12, 255], 0);
    expect(isBlankPdfSample(sample)).toBe(true);
    for (let index = 0; index < 4; index += 1) sample.set([12, 12, 12, 255], index * 4);
    expect(isBlankPdfSample(sample)).toBe(false);
  });
});
