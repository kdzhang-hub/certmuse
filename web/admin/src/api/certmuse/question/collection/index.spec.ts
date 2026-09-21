import { beforeEach, describe, expect, it, vi } from 'vitest';

const request = vi.hoisted(() => vi.fn());
vi.mock('@/utils/request', () => ({ default: request }));

import {
  approveCollectionRevision,
  createCollection,
  createCollectionRevision,
  deleteCollectionDraft,
  getCollectionDetail,
  getCollectionRevision,
  listCollectionManagements,
  listCollections,
  rejectCollectionRevision,
  renameCollection,
  saveCollectionRevision,
  submitCollectionReview,
  takeCollectionOffline
} from './index';

describe('collection management API', () => {
  beforeEach(() => {
    request.mockReset();
    request.mockResolvedValue({ data: {} });
  });

  it('covers collection list, detail and draft writes', () => {
    const query = { keyword: '模拟', status: 'draft' as const, pageNum: 1, pageSize: 20 };
    const form = {
      collectionName: '模拟卷',
      collectionType: 'SIMULATION' as const,
      certificationId: 'cert-1',
      syllabusVersionId: 'syllabus-1',
      durationMinutes: 120,
      items: []
    };

    listCollections(query);
    listCollectionManagements(query);
    getCollectionDetail('c-1');
    getCollectionRevision('r-1');
    createCollection(form);
    saveCollectionRevision('r-1', form);
    renameCollection('c-1', { collectionName: '新名称' });
    createCollectionRevision('c-1');
    createCollectionRevision('c-1', 'r-0');
    deleteCollectionDraft('r-1');

    expect(request.mock.calls.slice(0, 4).map(([config]) => config)).toEqual([
      { url: '/api/admin/collections', method: 'get', params: query },
      { url: '/api/admin/collections/manage', method: 'get', params: query },
      { url: '/api/admin/collections/c-1', method: 'get' },
      { url: '/api/admin/collection-revisions/r-1', method: 'get' }
    ]);
    expect(request).toHaveBeenCalledWith(expect.objectContaining({
      url: '/api/admin/collections/c-1/name', method: 'put', data: { collectionName: '新名称' },
      headers: { 'X-Request-Id': expect.any(String) }
    }));
    expect(request).toHaveBeenCalledWith(expect.objectContaining({
      url: '/api/admin/collections/c-1/revisions', method: 'post', data: { sourceRevisionId: 'r-0' },
      headers: { 'X-Request-Id': expect.any(String) }
    }));
    expect(request).toHaveBeenCalledWith(expect.objectContaining({
      url: '/api/admin/collections/c-1/revisions', method: 'post', data: {},
      headers: { 'X-Request-Id': expect.any(String) }
    }));
  });

  it('uses idempotent review endpoints and preserves reject opinions', () => {
    submitCollectionReview('r-1');
    approveCollectionRevision('r-1');
    rejectCollectionRevision('r-1', '依据不足');
    takeCollectionOffline('r-1');

    expect(request).toHaveBeenCalledWith(expect.objectContaining({
      url: '/api/admin/collection-revisions/r-1/submit-review', method: 'post',
      headers: { 'X-Request-Id': expect.any(String) }
    }));
    expect(request).toHaveBeenCalledWith(expect.objectContaining({
      url: '/api/admin/collection-revisions/r-1/reject', method: 'post', data: { reviewOpinion: '依据不足' },
      headers: { 'X-Request-Id': expect.any(String) }
    }));
    expect(request.mock.calls).toHaveLength(4);
  });
});
