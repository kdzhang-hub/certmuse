import { beforeEach, describe, expect, it, vi } from 'vitest';

const request = vi.hoisted(() => vi.fn());
vi.mock('@/utils/request', () => ({ default: request }));

import { approveQuestionRevision, deleteQuestion, getQuestionDetail, getQuestionPreview, listQuestions, rejectQuestionRevision, saveQuestionDraft, submitQuestionReview, takeQuestionOffline } from './index';
import { approveCollectionRevision, getCollectionRevision, listCollections, rejectCollectionRevision } from './collection';

describe('content review API', () => {
  beforeEach(() => request.mockReset());

  it('queries only pending collection revisions', async () => {
    await listCollections({ status: 'pending_review', pageNum: 1, pageSize: 10 });
    expect(request).toHaveBeenCalledWith({ url: '/api/admin/collections', method: 'get', params: { status: 'pending_review', pageNum: 1, pageSize: 10 } });
  });

  it('passes question review filters and paging through unchanged', async () => {
    const params = { keyword: '架构', syllabusVersionId: 's1', examSubjectId: 'e1', questionType: 'CHOICE' as const, difficulty: 'medium', status: 'pending_review' as const, pageNum: 2, pageSize: 20 };
    await listQuestions(params);
    expect(request).toHaveBeenCalledWith({ url: '/api/admin/questions', method: 'get', params });
  });

  it('requests previews and collection details with revision identifiers', async () => {
    await getQuestionDetail('q1');
    await getQuestionPreview('q1', 'r1');
    await getCollectionRevision('cr1');
    expect(request).toHaveBeenNthCalledWith(1, { url: '/api/admin/questions/q1', method: 'get', params: undefined });
    expect(request).toHaveBeenNthCalledWith(2, { url: '/api/admin/questions/q1/preview', method: 'get', params: { revisionId: 'r1' } });
    expect(request).toHaveBeenNthCalledWith(3, { url: '/api/admin/collection-revisions/cr1', method: 'get' });
  });

  it('adds a revision filter only when a revision is supplied', () => {
    getQuestionDetail('q1', 'r1');
    expect(request).toHaveBeenCalledWith({
      url: '/api/admin/questions/q1',
      method: 'get',
      params: { revisionId: 'r1' }
    });
  });

  it('saves and deletes a question with request-scoped idempotency keys', () => {
    saveQuestionDraft('q1', { baseRevisionId: 'r1' } as never);
    deleteQuestion('q1');

    expect(request).toHaveBeenCalledWith(expect.objectContaining({
      url: '/api/admin/questions/q1', method: 'put', data: { baseRevisionId: 'r1' },
      headers: { 'X-Request-Id': expect.any(String) }
    }));
    expect(request).toHaveBeenCalledWith(expect.objectContaining({
      url: '/api/admin/questions/q1', method: 'delete', headers: { 'X-Request-Id': expect.any(String) }
    }));
  });

  it.each([
    [submitQuestionReview, '/api/admin/question-revisions/r1/submit-review'],
    [approveQuestionRevision, '/api/admin/question-revisions/r1/approve'],
    [approveCollectionRevision, '/api/admin/collection-revisions/r1/approve']
  ])('sends review mutation with an idempotency key', async (operation, url) => {
    await operation('r1');
    expect(request).toHaveBeenCalledWith(expect.objectContaining({ url, method: 'post', headers: { 'X-Request-Id': expect.any(String) } }));
  });

  it('sends an offline request with an idempotency key', async () => {
    await takeQuestionOffline('r1');
    expect(request).toHaveBeenCalledWith(expect.objectContaining({
      url: '/api/admin/question-revisions/r1/offline',
      method: 'post',
      headers: { 'X-Request-Id': expect.any(String) }
    }));
  });

  it.each([
    [rejectQuestionRevision, '/api/admin/question-revisions/r1/reject'],
    [rejectCollectionRevision, '/api/admin/collection-revisions/r1/reject']
  ])('sends reject opinion with an idempotency key', async (operation, url) => {
    await operation('r1', '内容依据不足');
    expect(request).toHaveBeenCalledWith(expect.objectContaining({ url, method: 'post', data: { reviewOpinion: '内容依据不足' }, headers: { 'X-Request-Id': expect.any(String) } }));
  });

  it('generates a new idempotency key for each review request', async () => {
    await approveQuestionRevision('r1');
    await approveQuestionRevision('r2');
    const firstId = request.mock.calls[0][0].headers['X-Request-Id'];
    const secondId = request.mock.calls[1][0].headers['X-Request-Id'];
    expect(firstId).not.toBe(secondId);
  });
});
