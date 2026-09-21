import { beforeEach, describe, expect, it, vi } from 'vitest';

const request = vi.hoisted(() => vi.fn());
vi.mock('@/utils/request', () => ({ default: request }));

import {
  confirmPaperImport,
  createPaperImport,
  getPaperImportProgress,
  getPaperImportQualifications,
  listPaperImportIssues,
  validatePaperImport
} from './index';

describe('paper import API', () => {
  beforeEach(() => request.mockReset());

  it('uses dedicated routes, multipart data, and an idempotency key', () => {
    const file = new File(['PK{}'], 'paper.zip', { type: 'application/zip' });
    createPaperImport({
      file,
      collectionName: '模拟卷一',
      collectionType: 'SIMULATION',
      durationMinutes: 240,
      certificationId: '1',
      requestId: 'request-1'
    });
    const config = request.mock.calls[0]?.[0];
    expect(config.url).toBe('/api/admin/paper-imports');
    expect(config.data.get('certificationId')).toBe('1');
    expect(config.data.get('syllabusVersionId')).toBeNull();
    expect(config.headers['X-Request-Id']).toBe('request-1');
  });

  it('includes required metadata when creating a past-paper import', () => {
    const file = new File(['PK'], 'past-paper.zip', { type: 'application/zip' });
    createPaperImport({
      file,
      collectionName: '2025年系统架构设计师综合知识',
      collectionType: 'PAST_PAPER',
      durationMinutes: 150,
      certificationId: '1',
      examYear: 2025,
      examMonth: 11,
      paperTypeCode: 'AM',
      paperTypeName: '综合知识',
      requestId: 'past-paper-request'
    });

    const data = request.mock.calls.at(-1)?.[0].data as FormData;
    expect(data.get('examYear')).toBe('2025');
    expect(data.get('examMonth')).toBe('11');
    expect(data.get('paperTypeCode')).toBe('AM');
    expect(data.get('paperTypeName')).toBe('综合知识');
  });

  it('reuses enabled qualification options from the catalog endpoint', async () => {
    request.mockResolvedValue({ data: { rows: [{ id: '1', certificationName: '系统架构设计师' }], total: 1 } });

    await expect(getPaperImportQualifications()).resolves.toEqual([{ id: '1', certificationName: '系统架构设计师' }]);
    expect(request).toHaveBeenCalledWith({
      url: '/api/admin/catalog/certifications',
      method: 'get',
      params: { status: '0', pageNum: 1, pageSize: 100 }
    });
  });

  it('returns no qualifications when the catalog response has no rows', async () => {
    request.mockResolvedValue({ data: {} });

    await expect(getPaperImportQualifications()).resolves.toEqual([]);
  });

  it('uses an empty list when the catalog leaves the data envelope absent', async () => {
    request.mockResolvedValue({});

    await expect(getPaperImportQualifications()).resolves.toEqual([]);
  });

  it('keeps every paper-import API on the real HTTP boundary when preview mode is disabled', async () => {
    const file = new File(['PK{}'], 'paper.zip', { type: 'application/zip' });
    request.mockResolvedValue({ data: { rows: [] } });

    await getPaperImportQualifications();
    createPaperImport({
      file,
      collectionName: '真实边界试卷',
      collectionType: 'SIMULATION',
      durationMinutes: 180,
      certificationId: 'cert-1',
      requestId: 'real-create'
    });
    validatePaperImport('batch-real', 'real-validate');
    getPaperImportProgress('batch-real');
    listPaperImportIssues('batch-real', { pageNum: 1, pageSize: 10 });
    confirmPaperImport('batch-real', 'real-confirm');

    expect(request.mock.calls.map(([config]) => config.url)).toEqual([
      '/api/admin/catalog/certifications',
      '/api/admin/paper-imports',
      '/api/admin/paper-imports/batch-real/validate',
      '/api/admin/paper-imports/batch-real/progress',
      '/api/admin/paper-imports/batch-real/issues',
      '/api/admin/paper-imports/batch-real/confirm'
    ]);
  });

  it('uses lifecycle routes and sends a unique key for every write', () => {
    validatePaperImport('70001', 'request-2');
    getPaperImportProgress('70001');
    listPaperImportIssues('70001', { pageNum: 1, pageSize: 100, severity: 'error' });
    confirmPaperImport('70001', 'request-3');
    expect(request.mock.calls.map(call => call[0].url)).toEqual([
      '/api/admin/paper-imports/70001/validate',
      '/api/admin/paper-imports/70001/progress',
      '/api/admin/paper-imports/70001/issues',
      '/api/admin/paper-imports/70001/confirm'
    ]);
    expect(request.mock.calls[0][0].headers['X-Request-Id']).toBe('request-2');
    expect(request.mock.calls[3][0].headers['X-Request-Id']).toBe('request-3');
  });
});
