import { beforeEach, describe, expect, it, vi } from 'vitest';

const request = vi.hoisted(() => vi.fn());
vi.mock('@/utils/request', () => ({ default: request }));

import {
  deleteTextbookOriginalPdf,
  getTextbookOriginalPdf,
  getTextbookPdfReader,
  uploadTextbookOriginalPdf
} from './index';

describe('textbook original PDF API', () => {
  beforeEach(() => request.mockReset());

  it('reads original-PDF metadata and the protected reader payload', async () => {
    request.mockResolvedValueOnce({ data: { fileName: '教材.pdf' } }).mockResolvedValueOnce({ data: { url: '/reader' } });

    await expect(getTextbookOriginalPdf('textbook-1')).resolves.toEqual({ fileName: '教材.pdf' });
    await expect(getTextbookPdfReader('textbook-1')).resolves.toEqual({ url: '/reader' });

    expect(request).toHaveBeenNthCalledWith(1, {
      url: '/api/admin/catalog/textbooks/textbook-1/original-pdf',
      method: 'get'
    });
    expect(request).toHaveBeenNthCalledWith(2, {
      url: '/api/admin/catalog/textbooks/textbook-1/original-pdf/reader',
      method: 'get'
    });
  });

  it('uploads only the selected file and returns no stale value after deletion', async () => {
    request.mockResolvedValueOnce({ data: { fileName: '教材.pdf' } }).mockResolvedValueOnce({ data: { ignored: true } });
    const file = new File(['pdf'], '教材.pdf', { type: 'application/pdf' });

    await expect(uploadTextbookOriginalPdf('textbook-1', file)).resolves.toEqual({ fileName: '教材.pdf' });
    await expect(deleteTextbookOriginalPdf('textbook-1')).resolves.toBeUndefined();

    const upload = request.mock.calls[0][0];
    expect(upload).toMatchObject({
      url: '/api/admin/catalog/textbooks/textbook-1/original-pdf',
      method: 'post'
    });
    expect(upload.data).toBeInstanceOf(FormData);
    expect(upload.data.get('file')).toBe(file);
    expect(request).toHaveBeenNthCalledWith(2, {
      url: '/api/admin/catalog/textbooks/textbook-1/original-pdf',
      method: 'delete'
    });
  });
});
