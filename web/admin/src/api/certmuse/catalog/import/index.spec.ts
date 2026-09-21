import { beforeEach, describe, expect, it, vi } from 'vitest';

const request = vi.hoisted(() => vi.fn());

vi.mock('@/utils/request', () => ({ default: request }));

import {
  createQuestionImport,
  createTextbookImport,
  confirmKnowledgePointImport,
  confirmImport,
  createKnowledgePointImport,
  getCompletedImportBatch,
  getImportBatchFilterOptions,
  getImportContextOptions,
  getQuestionImportContextOptions,
  getTextbookImportContextOptions,
  getTextbookImportPreview,
  getImportProgress,
  getKnowledgeDiff,
  listImportIssues,
  listCompletedImportBatches,
  resolveKnowledgeDiff,
  resolveKnowledgeDiffBatch,
  validateImport
} from './index';

describe('knowledge-point import API', () => {
  beforeEach(() => {
    request.mockReset();
    request.mockResolvedValue({ code: 200, msg: 'ok', data: { syllabusVersions: [], examSubjects: [] } });
  });

  it('requests import context for the selected syllabus version', async () => {
    await getImportContextOptions('20001');

    expect(request).toHaveBeenCalledWith({
      url: '/api/admin/imports/options',
      method: 'get',
      params: { type: 'knowledge_point', syllabusVersionId: '20001' }
    });
  });

  it('requests question import context by question type', async () => {
    await getQuestionImportContextOptions();

    expect(request).toHaveBeenCalledWith({
      url: '/api/admin/imports/options',
      method: 'get',
      params: { type: 'question' }
    });
  });

  it('requests textbook context and submits its multipart contract', async () => {
    await getTextbookImportContextOptions('20001');
    expect(request).toHaveBeenCalledWith({
      url: '/api/admin/imports/options', method: 'get', params: { type: 'textbook', syllabusVersionId: '20001' }
    });

    const file = new File(['{}'], 'textbook.jsonl', { type: 'application/x-ndjson' });
    createTextbookImport({
      file, importType: 'document_chunk', templateVersion: 'document_chunk/1.0', mode: 'replace_draft',
      syllabusVersionId: '20001', documentId: '30001', edition: '第4版',
      subjectMappings: [{ subjectNo: 1, examSubjectId: '101' }], requestId: 'textbook-request-id'
    });
    const config = request.mock.calls[1][0];
    expect(config.headers).toEqual({ 'X-Request-Id': 'textbook-request-id', repeatSubmit: false });
    expect(config.data.get('importType')).toBe('document_chunk');
    expect(config.data.get('templateVersion')).toBe('document_chunk/1.0');
    expect(config.data.get('mode')).toBe('replace_draft');
    expect(config.data.get('documentId')).toBe('30001');

    getTextbookImportPreview('batch-1', { pageNum: 2, pageSize: 50 });
    expect(request.mock.calls[2][0]).toEqual({
      url: '/api/admin/imports/batch-1/preview', method: 'get', params: { pageNum: 2, pageSize: 50 }
    });
  });

  it('preserves optional textbook metadata only when it was supplied', () => {
    const file = new File(['{}'], 'textbook.jsonl', { type: 'application/x-ndjson' });
    createTextbookImport({
      file, importType: 'document_chunk', templateVersion: 'document_chunk/1.0', mode: 'replace_draft',
      syllabusVersionId: '20001', subjectMappings: [], title: '系统架构设计教程', edition: '第 4 版',
      documentId: 'doc-1', requestId: 'textbook-all-options'
    });
    const allOptions = request.mock.calls[0][0].data as FormData;
    expect(allOptions.get('title')).toBe('系统架构设计教程');
    expect(allOptions.get('edition')).toBe('第 4 版');
    expect(allOptions.get('documentId')).toBe('doc-1');

    request.mockReset();
    createTextbookImport({
      file, importType: 'document_chunk', templateVersion: 'document_chunk/1.0', mode: 'replace_draft',
      syllabusVersionId: '20001', subjectMappings: [], requestId: 'textbook-no-options'
    });
    const noOptions = request.mock.calls[0][0].data as FormData;
    expect(noOptions.get('title')).toBeNull();
    expect(noOptions.get('edition')).toBeNull();
    expect(noOptions.get('documentId')).toBeNull();
  });

  it('submits the JSONL file and its idempotency header when creating a batch', () => {
    const file = new File(['{}'], 'knowledge-points.jsonl', { type: 'application/jsonl' });
    createKnowledgePointImport({
      file,
      importType: 'knowledge_point',
      syllabusVersionId: '20001',
      subjectMappings: [{ subjectNo: 1, examSubjectId: '101' }],
      templateVersion: 'knowledge_point/1.0',
      requestId: 'create-request-id'
    });

    const config = request.mock.calls[0][0];
    expect(config).toMatchObject({
      url: '/api/admin/imports',
      method: 'post',
      headers: { 'X-Request-Id': 'create-request-id', repeatSubmit: false }
    });
    expect(config.data).toBeInstanceOf(FormData);
    expect(config.data.get('file')).toBe(file);
    expect(config.data.get('subjectMappings')).toBe('[{"subjectNo":1,"examSubjectId":"101"}]');
  });

  it('sends only the optional knowledge-point context that the operator selected', () => {
    const file = new File(['{}'], 'knowledge-points.jsonl', { type: 'application/jsonl' });
    createKnowledgePointImport({
      file, importType: 'knowledge_point', certificationId: 'cert-1', subjectMappings: [],
      templateVersion: 'knowledge_point/1.0', requestId: 'certification-only'
    });
    const certificationOnly = request.mock.calls[0][0].data as FormData;
    expect(certificationOnly.get('certificationId')).toBe('cert-1');
    expect(certificationOnly.get('syllabusVersionId')).toBeNull();

    request.mockReset();
    createKnowledgePointImport({
      file, importType: 'knowledge_point', syllabusVersionId: 'syllabus-1', subjectMappings: [],
      templateVersion: 'knowledge_point/1.0', requestId: 'syllabus-only'
    });
    const syllabusOnly = request.mock.calls[0][0].data as FormData;
    expect(syllabusOnly.get('certificationId')).toBeNull();
    expect(syllabusOnly.get('syllabusVersionId')).toBe('syllabus-1');
  });

  it('submits the question batch context required by the Q2 contract', () => {
    const file = new File(['PK{}'], 'questions.zip', { type: 'application/zip' });
    createQuestionImport({
      file,
      importType: 'question',
      knowledgeSyllabusVersionId: '20001',
      templateVersion: 'question-zip/1.0',
      requestId: 'question-create-request-id'
    });

    const config = request.mock.calls[0][0];
    expect(config).toMatchObject({
      url: '/api/admin/imports',
      method: 'post',
      headers: { 'X-Request-Id': 'question-create-request-id', repeatSubmit: false }
    });
    expect(config.data.get('importType')).toBe('question');
    expect(config.data.get('examSubjectId')).toBeNull();
    expect(config.data.get('knowledgeSyllabusVersionId')).toBe('20001');
    expect(config.data.get('templateVersion')).toBe('question-zip/1.0');
  });

  it('keeps every import API on the real HTTP boundary when preview mode is disabled', () => {
    const file = new File(['{}'], 'knowledge-points.jsonl', { type: 'application/jsonl' });

    getImportContextOptions();
    getQuestionImportContextOptions();
    getTextbookImportContextOptions();
    createKnowledgePointImport({
      file, importType: 'knowledge_point', syllabusVersionId: '20001', subjectMappings: [],
      templateVersion: 'knowledge_point/1.0', requestId: 'real-knowledge'
    });
    createQuestionImport({
      file, importType: 'question', knowledgeSyllabusVersionId: '20001',
      templateVersion: 'question-zip/1.0', requestId: 'real-question'
    });
    createTextbookImport({
      file, importType: 'document_chunk', templateVersion: 'document_chunk/1.0', mode: 'create',
      syllabusVersionId: '20001', subjectMappings: [], requestId: 'real-textbook'
    });
    validateImport('batch-real');
    confirmKnowledgePointImport('batch-real', 'real-confirm');
    getImportProgress('batch-real');
    getTextbookImportPreview('batch-real', { pageNum: 1, pageSize: 10 });
    listImportIssues('batch-real', { pageNum: 1, pageSize: 10 });

    expect(request.mock.calls.map(([config]) => config.url)).toEqual([
      '/api/admin/imports/options',
      '/api/admin/imports/options',
      '/api/admin/imports/options',
      '/api/admin/imports',
      '/api/admin/imports',
      '/api/admin/imports',
      '/api/admin/imports/batch-real/validate',
      '/api/admin/imports/batch-real/confirm',
      '/api/admin/imports/batch-real/progress',
      '/api/admin/imports/batch-real/preview',
      '/api/admin/imports/batch-real/issues'
    ]);
  });

  it('uses the documented validate, progress, issue and confirm endpoints', () => {
    validateImport('batch-1');
    getImportProgress('batch-1');
    listImportIssues('batch-1', { pageNum: 2, pageSize: 20, severity: 'error' });
    confirmKnowledgePointImport('batch-1', 'confirm-request-id');
    confirmImport('batch-2', 'confirm-request-id-2');

    expect(request.mock.calls.map(([config]) => config)).toEqual([
      { url: '/api/admin/imports/batch-1/validate', method: 'post' },
      { url: '/api/admin/imports/batch-1/progress', method: 'get' },
      {
        url: '/api/admin/imports/batch-1/issues',
        method: 'get',
        params: { pageNum: 2, pageSize: 20, severity: 'error' }
      },
      {
        url: '/api/admin/imports/batch-1/confirm',
        method: 'post',
        headers: { 'X-Request-Id': 'confirm-request-id', repeatSubmit: false }
      },
      {
        url: '/api/admin/imports/batch-2/confirm',
        method: 'post',
        headers: { 'X-Request-Id': 'confirm-request-id-2', repeatSubmit: false }
      }
    ]);
  });

  it('queries completed import batches and their filter/detail routes', () => {
    getImportBatchFilterOptions();
    listCompletedImportBatches({ pageNum: 2, pageSize: 20, importType: 'textbook' });
    getCompletedImportBatch('batch-9');

    expect(request.mock.calls.map(([config]) => config)).toEqual([
      { url: '/api/admin/imports/options', method: 'get' },
      { url: '/api/admin/imports', method: 'get', params: { pageNum: 2, pageSize: 20, importType: 'textbook' } },
      { url: '/api/admin/imports/batch-9', method: 'get' }
    ]);
  });

  it('queries and resolves knowledge differences with string ids and idempotency headers', () => {
    getKnowledgeDiff('batch-1', {
      examSubjectId: '1930000000000000001',
      action: 'move',
      resolutionStatus: 'pending',
      excludeUnchanged: true,
      pageNum: 2,
      pageSize: 50
    });
    resolveKnowledgeDiff(
      'batch-1',
      '1930000000000000101',
      { decision: 'approve', oldKnowledgePointId: null },
      'single-request-id'
    );
    resolveKnowledgeDiffBatch(
      'batch-1',
      [{ diffId: '1930000000000000102', decision: 'reject', oldKnowledgePointId: null }],
      'batch-request-id'
    );

    expect(request.mock.calls.map(([config]) => config)).toEqual([
      {
        url: '/api/admin/imports/batch-1/knowledge-diff',
        method: 'get',
        params: {
          examSubjectId: '1930000000000000001', action: 'move', resolutionStatus: 'pending',
          excludeUnchanged: true, pageNum: 2, pageSize: 50
        }
      },
      {
        url: '/api/admin/imports/batch-1/knowledge-diff/1930000000000000101',
        method: 'put',
        data: { decision: 'approve', oldKnowledgePointId: null },
        headers: { 'X-Request-Id': 'single-request-id', repeatSubmit: false }
      },
      {
        url: '/api/admin/imports/batch-1/knowledge-diff/batch',
        method: 'put',
        data: { resolutions: [{ diffId: '1930000000000000102', decision: 'reject', oldKnowledgePointId: null }] },
        headers: { 'X-Request-Id': 'batch-request-id', repeatSubmit: false }
      }
    ]);
  });
});
