import { beforeEach, describe, expect, it, vi } from 'vitest';

const request = vi.hoisted(() => vi.fn());
vi.mock('@/utils/request', () => ({ default: request }));

import { deleteSyllabusVersion, getKnowledgeTree, listSyllabusVersions, updateSyllabusPublishedDate } from './index';

describe('knowledge tree API', () => {
  beforeEach(() => request.mockReset());

  it('uses the frozen syllabus list and tree query endpoints', () => {
    listSyllabusVersions({
      keyword: '架构',
      certificationId: '10001',
      versionName: '第二版',
      status: 'available',
      pageNum: 1,
      pageSize: 10
    });
    getKnowledgeTree('20001');

    expect(request.mock.calls.map(([config]) => config)).toEqual([
      {
        url: '/api/admin/catalog/syllabus-versions',
        method: 'get',
        params: {
          keyword: '架构',
          certificationId: '10001',
          versionName: '第二版',
          status: 'available',
          pageNum: 1,
          pageSize: 10
        }
      },
      { url: '/api/admin/catalog/syllabus-versions/20001/knowledge-tree', method: 'get' }
    ]);
  });

  it('deletes the selected syllabus version through its route', () => {
    deleteSyllabusVersion('20001');
    expect(request).toHaveBeenCalledWith({ url: '/api/admin/catalog/syllabus-versions/20001', method: 'delete' });
  });

  it('updates the selected syllabus published date through its dedicated route', () => {
    updateSyllabusPublishedDate('20001', { publishedDate: '2026-08-13' });

    expect(request).toHaveBeenCalledWith(expect.objectContaining({
      url: '/api/admin/catalog/syllabus-versions/20001/published-date',
      method: 'put',
      data: { publishedDate: '2026-08-13' }
    }));
    expect(request.mock.calls[0]![0].headers['X-Request-Id']).toMatch(/^[0-9a-f-]{36}$/i);
  });
});
