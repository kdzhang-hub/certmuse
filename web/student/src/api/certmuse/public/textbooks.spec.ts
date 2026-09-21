import { describe, expect, it, vi } from 'vitest';
import { getPublicTextbookPdfReader, listPublicTextbookCertifications, listPublicTextbookPdfs } from './textbooks';

const { request } = vi.hoisted(() => ({ request: vi.fn().mockResolvedValue({ data: [] }) }));

vi.mock('@/utils/request', () => ({ default: request }));

describe('public textbook API adapter', () => {
  it('uses only the anonymous published-textbook endpoints', async () => {
    await listPublicTextbookCertifications();
    await listPublicTextbookPdfs('42', '架构');
    await getPublicTextbookPdfReader('99');

    expect(request).toHaveBeenNthCalledWith(1, { url: '/api/public/textbooks/certifications', method: 'get' });
    expect(request).toHaveBeenNthCalledWith(2, {
      url: '/api/public/textbooks',
      method: 'get',
      params: { certificationId: '42', keyword: '架构' }
    });
    expect(request).toHaveBeenNthCalledWith(3, { url: '/api/public/textbooks/99/reader', method: 'get' });
  });
});
