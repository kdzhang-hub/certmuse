import { beforeEach, describe, expect, it, vi } from 'vitest';

const request = vi.hoisted(() => vi.fn());
vi.mock('@/utils/request', () => ({ default: request }));

import {
  deleteTextbook,
  deleteTextbookChunks,
  getTextbook,
  getTextbookChunk,
  getTextbookOptions,
  listTextbookChunks,
  listTextbooks,
  publishTextbook,
  takeTextbookOffline,
  updateTextbook,
  updateTextbookChunk
} from './index';

describe('textbook resource API', () => {
  beforeEach(() => request.mockReset());

  it('sends the M03 list, detail and chunk requests and unwraps data', async () => {
    request.mockResolvedValue({ data: { marker: 'ok' } });

    await expect(
      listTextbooks({ pageNum: 1, pageSize: 20, title: '架构', orderByColumn: 'chunkCount', isAsc: 'desc' })
    ).resolves.toEqual({ marker: 'ok' });
    await getTextbookOptions();
    await getTextbook('2001');
    await updateTextbook('2001', { title: '更新后的教材', certificationId: '1001' });
    await publishTextbook('2001');
    await takeTextbookOffline('2001');
    await listTextbookChunks('2001', {
      pageNum: 2,
      pageSize: 10,
      knowledgePointId: '4001',
      includeDescendants: true,
      hasKnowledgePoint: true,
      keyword: '正文'
    });
    await getTextbookChunk('2001', '3001');
    await updateTextbookChunk('2001', '3001', {
      heading: null,
      content: '完整正文',
      updateTime: '2026-08-04T12:00:00+08:00',
      knowledgePointIds: ['4001']
    });

    expect(request.mock.calls.map(([config]) => config)).toEqual([
      {
        url: '/api/admin/catalog/textbooks',
        method: 'get',
        params: { pageNum: 1, pageSize: 20, title: '架构', orderByColumn: 'chunkCount', isAsc: 'desc' }
      },
      { url: '/api/admin/catalog/textbooks/options', method: 'get' },
      { url: '/api/admin/catalog/textbooks/2001', method: 'get' },
      {
        url: '/api/admin/catalog/textbooks/2001',
        method: 'put',
        data: { title: '更新后的教材', certificationId: '1001' }
      },
      { url: '/api/admin/catalog/textbooks/2001/publish', method: 'post' },
      { url: '/api/admin/catalog/textbooks/2001/offline', method: 'post' },
      {
        url: '/api/admin/catalog/textbooks/2001/chunks',
        method: 'get',
        params: { pageNum: 2, pageSize: 10, knowledgePointId: '4001', includeDescendants: true, hasKnowledgePoint: true, keyword: '正文' }
      },
      { url: '/api/admin/catalog/textbooks/2001/chunks/3001', method: 'get' },
      {
        url: '/api/admin/catalog/textbooks/2001/chunks/3001',
        method: 'put',
        data: { heading: null, content: '完整正文', updateTime: '2026-08-04T12:00:00+08:00', knowledgePointIds: ['4001'] }
      }
    ]);
  });

  it('propagates backend rejection unchanged', async () => {
    const failure = new Error('409 conflict');
    request.mockRejectedValueOnce(failure);
    await expect(getTextbookChunk('1', '2')).rejects.toBe(failure);
  });

  it('uses single textbook delete and comma-separated chunk deletion', async () => {
    request.mockResolvedValue({ data: undefined });
    await deleteTextbook('1');
    await deleteTextbookChunks('9', ['10', '11']);
    expect(request.mock.calls.map(([config]) => config)).toEqual([
      { url: '/api/admin/catalog/textbooks/1', method: 'delete' },
      { url: '/api/admin/catalog/textbooks/9/chunks', method: 'delete', params: { ids: '10,11' } }
    ]);
  });
});
