import { describe, expect, it, vi } from 'vitest';

const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock('@/utils/request', () => ({ default: request }));

import { createQualification, createSyllabusVersion, deleteQualification, deleteSyllabusVersion, listQualifications, updateQualification, updateSyllabusVersion } from './index';

describe('M01 qualification and version API', () => {
  it('uses the frozen paged list endpoint and M01 write paths with UUID request IDs', () => {
    listQualifications({ keyword: '架构', pageNum: 2, pageSize: 20 });
    createQualification({ certificationCode: 'ARCH', certificationName: '架构', qualificationLevel: 'HIGH', status: '0', sortOrder: 1 });
    updateQualification('1', { certificationCode: 'ARCH', certificationName: '架构', qualificationLevel: 'HIGH', status: '0', sortOrder: 1 });
    deleteQualification('1');
    createSyllabusVersion('1', { versionName: '第二版', publishedDate: null });
    updateSyllabusVersion('1', '2', { versionName: '第三版', publishedDate: '2026-08-04' });
    deleteSyllabusVersion('1', '2');
    expect(request).toHaveBeenNthCalledWith(1, expect.objectContaining({ url: '/api/admin/catalog/certifications', method: 'get', params: { keyword: '架构', pageNum: 2, pageSize: 20 } }));
    for (const call of request.mock.calls.slice(1)) expect(call[0].headers['X-Request-Id']).toMatch(/^[0-9a-f-]{36}$/i);
    expect(request.mock.calls.slice(1).map(call => call[0].url)).toEqual([
      '/api/admin/catalog/certifications', '/api/admin/catalog/certifications/1', '/api/admin/catalog/certifications/1',
      '/api/admin/catalog/certifications/1/syllabus-versions', '/api/admin/catalog/certifications/1/syllabus-versions/2', '/api/admin/catalog/certifications/1/syllabus-versions/2'
    ]);
    expect(request.mock.calls[4][0].data).not.toHaveProperty('certificationId');
  });
});
