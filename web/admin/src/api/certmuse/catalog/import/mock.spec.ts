import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  confirmMockKnowledgePointImport,
  createMockKnowledgePointImport,
  createMockQuestionImport,
  getMockImportContextOptions,
  getMockQuestionImportContextOptions,
  getMockImportProgress,
  listMockImportIssues,
  validateMockImport
} from './mock';
import type { KnowledgePointImportCreateForm } from './types';

const requiredRecord = {
  schema_version: '1.0',
  source_key: 'architecture-root',
  subject_no: 1,
  syllabus_number: '1',
  syllabus_title: '计算机系统基础',
  parent_syllabus_number: null,
  tree_depth: 1,
  sort_order: 1,
  description: '根知识点',
  importance: 'high',
  diagnostic_enabled: true,
  recommendation_enabled: true,
  status: 'enabled'
};

function jsonlFile(...records: unknown[]) {
  const text = records.map(record => JSON.stringify(record)).join('\n');
  const bytes = new TextEncoder().encode(text);
  return {
    name: 'knowledge-points.jsonl',
    text: async () => text,
    arrayBuffer: async () => bytes.buffer.slice(0)
  } as File;
}

function createForm(requestId: string, file = jsonlFile(requiredRecord)): KnowledgePointImportCreateForm {
  return {
    file,
    importType: 'knowledge_point',
    syllabusVersionId: '20001',
    subjectMappings: [{ subjectNo: 1, examSubjectId: '101' }],
    templateVersion: 'knowledge_point/1.0',
    requestId
  };
}

describe('knowledge-point import mock', () => {
  let now = 0;

  beforeEach(() => {
    now = 0;
  });

  afterEach(() => vi.restoreAllMocks());

  function useMockClock() {
    vi.spyOn(Date, 'now').mockImplementation(() => now);
  }

  it('creates a batch and returns the same batch for an idempotent request', async () => {
    useMockClock();
    const form = createForm('request-idempotent');

    const created = await createMockKnowledgePointImport(form);
    const reused = await createMockKnowledgePointImport(form);

    expect(created.data.status).toBe('uploaded');
    expect(created.data.reused).toBe(false);
    expect(reused.data).toMatchObject({ id: created.data.id, reused: true });
  });

  it('rejects reuse of a request id with a different upload payload', async () => {
    useMockClock();
    await createMockKnowledgePointImport(createForm('request-conflict'));

    await expect(
      createMockKnowledgePointImport(createForm('request-conflict', jsonlFile({ ...requiredRecord, source_key: 'other' })))
    ).rejects.toMatchObject({ responseCode: 409, responseData: { errorCode: 'IMPORT_REQUEST_ID_CONFLICT' } });
  });

  it('reports invalid JSONL records as precheck issues', async () => {
    useMockClock();
    const created = await createMockKnowledgePointImport(createForm('request-invalid', jsonlFile('{not-json')));
    await validateMockImport(created.data.id);

    now = 6000;
    const progress = await getMockImportProgress(created.data.id);
    const issues = await listMockImportIssues(created.data.id, {
      pageNum: 1,
      pageSize: 10,
      orderByColumn: 'lineNo',
      isAsc: 'asc'
    });

    expect(progress.data).toMatchObject({ status: 'waiting_confirm', totalCount: 1, validCount: 0, failedCount: 1 });
    expect(issues.data.rows).toEqual(expect.arrayContaining([expect.objectContaining({ issueCode: 'KP_JSON_INVALID' })]));
  });

  it('only confirms after precheck and completes the import flow', async () => {
    useMockClock();
    const created = await createMockKnowledgePointImport(createForm('request-confirm'));

    await expect(confirmMockKnowledgePointImport(created.data.id, 'confirm-too-early')).rejects.toMatchObject({
      responseCode: 409,
      responseData: { errorCode: 'IMPORT_BATCH_STATE_INVALID' }
    });

    await validateMockImport(created.data.id);
    now = 6000;
    const ready = await getMockImportProgress(created.data.id);
    expect(ready.data.status).toBe('waiting_confirm');
    const accepted = await confirmMockKnowledgePointImport(created.data.id, 'confirm-ready');
    now = 7800;
    const completed = await getMockImportProgress(created.data.id);

    expect(accepted.data).toMatchObject({ status: 'importing', currentStage: 'persist_knowledge_tree', accepted: true });
    expect(completed.data).toMatchObject({ status: 'completed', progressPercent: 100, validCount: 1 });
  });

  it('returns the supported syllabus and all three subject mappings', async () => {
    const options = await getMockImportContextOptions();
    expect(options.syllabusVersions).toHaveLength(1);
    expect(options.examSubjects.map(item => item.subjectNo)).toEqual([1, 2, 3]);
  });

  it('rejects invalid import context fields and missing batches', async () => {
    await expect(createMockKnowledgePointImport({
      ...createForm('request-bad-context'),
      syllabusVersionId: 'missing',
      templateVersion: 'knowledge_point/0.9' as unknown as KnowledgePointImportCreateForm['templateVersion'],
      subjectMappings: []
    })).rejects.toMatchObject({
      responseCode: 400,
      responseData: { errorCode: 'IMPORT_CONTEXT_INVALID', fieldErrors: expect.arrayContaining([
        expect.objectContaining({ field: 'syllabusVersionId' }),
        expect.objectContaining({ field: 'templateVersion' }),
        expect.objectContaining({ field: 'subjectMappings' })
      ]) }
    });
    await expect(getMockImportProgress('missing')).rejects.toMatchObject({ responseCode: 404 });
    await expect(validateMockImport('missing')).rejects.toMatchObject({ responseCode: 404 });
  });

  it('reports schema, field, mapping, duplicate and parent relationship errors', async () => {
    const child = {
      ...requiredRecord,
      schema_version: '2.0',
      source_key: 'architecture-root',
      subject_no: 2,
      syllabus_number: '1',
      parent_syllabus_number: 'missing',
      unexpected: true
    };
    const missing = { source_key: 'architecture-root', subject_no: 4, syllabus_number: '1' };
    const duplicateNumber = { ...requiredRecord, source_key: 'duplicate-number' };
    const created = await createMockKnowledgePointImport(
      createForm('request-many-issues', jsonlFile(requiredRecord, child, missing, duplicateNumber, null, []))
    );
    const issues = await listMockImportIssues(created.data.id, {
      pageNum: 1,
      pageSize: 100,
      severity: 'error',
      orderByColumn: 'issueCode',
      isAsc: 'desc'
    });
    const codes = issues.data.rows.map(item => item.issueCode);
    expect(codes).toEqual(expect.arrayContaining([
      'KP_SCHEMA_VERSION_UNSUPPORTED',
      'KP_UNKNOWN_FIELD',
      'KP_SUBJECT_MAPPING_UNKNOWN',
      'KP_DUPLICATE_SOURCE_KEY',
      'KP_DUPLICATE_SYLLABUS_NUMBER',
      'KP_PARENT_NOT_FOUND',
      'KP_REQUIRED_FIELD_MISSING',
      'KP_SUBJECT_NO_INVALID',
      'KP_JSON_INVALID'
    ]));
  });

  it('exposes each precheck stage and treats repeated validation idempotently', async () => {
    useMockClock();
    const created = await createMockKnowledgePointImport(createForm('request-stages'));
    expect((await getMockImportProgress(created.data.id)).data.status).toBe('uploaded');
    expect((await validateMockImport(created.data.id)).data.accepted).toBe(true);

    for (const [time, status, stage] of [
      [600, 'parsing', 'read_jsonl'],
      [1800, 'validating', 'validate_schema'],
      [3000, 'validating', 'validate_mapping'],
      [4200, 'validating', 'validate_tree'],
      [5400, 'validating', 'finalize_counts'],
      [6000, 'waiting_confirm', 'finalize_counts']
    ] as const) {
      now = time;
      expect((await getMockImportProgress(created.data.id)).data).toMatchObject({ status, currentStage: stage });
    }
    expect((await validateMockImport(created.data.id)).data.accepted).toBe(false);
  });
});

describe('question ZIP import mock', () => {
  it('exposes available knowledge-tree versions without exposing exam-subject choices', async () => {
    const context = await getMockQuestionImportContextOptions();

    expect(context.knowledgeSyllabusVersions).toHaveLength(1);
    expect(context.knowledgeSyllabusVersions[0]).toMatchObject({ id: '20001', knowledgeTreeAvailable: true });
  });

  it('creates a question batch with warning-only precheck data for a ZIP package', async () => {
    const file = new File(['mock zip content'], 'subject-1.zip', { type: 'application/zip' });
    const response = await createMockQuestionImport({
      file,
      importType: 'question',
      knowledgeSyllabusVersionId: '20001',
      templateVersion: 'question-zip/1.0',
      requestId: 'question-zip-request'
    });

    expect(response.data).toMatchObject({
      importType: 'question',
      derivedSubjects: [
        { subjectNo: 1, examSubjectId: '101' },
        { subjectNo: 2, examSubjectId: '102' },
        { subjectNo: 3, examSubjectId: '103' }
      ],
      knowledgeSyllabusVersionId: '20001',
      status: 'uploaded'
    });
  });
});
