import type { PageResult } from '@/api/types';
import type { AxiosPromise, RuoYiAjaxResult } from '@/utils/api-types';
import type {
  ImportBatchVO,
  ImportConfirmationAcceptedVO,
  ImportContextOptionsVO,
  ImportErrorVO,
  ImportIssueQuery,
  ImportIssueVO,
  ImportProgressVO,
  ImportValidationAcceptedVO,
  KnowledgePointImportCreateForm,
  QuestionImportContextOptionsVO,
  QuestionImportCreateForm,
  TextbookImportContextOptionsVO,
  TextbookImportCreateForm,
  TextbookImportPreviewVO
} from './types';

interface MockBatchState {
  batch: ImportBatchVO;
  requestId: string;
  signature: string;
  issues: ImportIssueVO[];
  totalCount: number;
  validCount: number;
  warningCount: number;
  failedCount: number;
  startedAt: number | null;
  confirmedAt: number | null;
}

interface ParsedLine {
  lineNo: number;
  sourceKey: string | null;
  subjectNo: number | null;
  syllabusNumber: string | null;
  parentSyllabusNumber: string | null;
  value: Record<string, unknown> | null;
  hasError: boolean;
}

const mockDelay = 180;
const mockDuration = 6000;
const batches = new Map<string, MockBatchState>();
const requests = new Map<string, string>();
let batchSequence = 10000;

const requiredFields = [
  'schema_version',
  'source_key',
  'subject_no',
  'syllabus_number',
  'syllabus_title',
  'parent_syllabus_number',
  'tree_depth',
  'sort_order',
  'description',
  'importance',
  'diagnostic_enabled',
  'recommendation_enabled',
  'status'
] as const;

export function getMockImportContextOptions(): Promise<ImportContextOptionsVO> {
  return resolveAfter({
    syllabusVersions: [
      {
        id: '20001',
        label: '系统架构设计师 · 2026 考纲',
        knowledgeTreeAvailable: true,
        knowledgePointCount: 1001
      }
    ],
    examSubjects: [
      { id: '101', subjectNo: 1, label: '系统架构设计综合知识' },
      { id: '102', subjectNo: 2, label: '系统架构设计案例分析' },
      { id: '103', subjectNo: 3, label: '系统架构设计论文' }
    ]
  });
}

export function getMockQuestionImportContextOptions(): Promise<QuestionImportContextOptionsVO> {
  return resolveAfter({
    knowledgeSyllabusVersions: [
      {
        id: '20001',
        label: '系统架构设计师 · 2026 考纲',
        knowledgeTreeAvailable: true,
        knowledgePointCount: 1001
      }
    ]
  });
}

export function getMockTextbookImportContextOptions(
  syllabusVersionId?: string
): Promise<TextbookImportContextOptionsVO> {
  return resolveAfter({
    ...getMockImportContextOptionsValue(),
    defaultSubjectMappings: [
      { subjectNo: 1, examSubjectId: '101' },
      { subjectNo: 2, examSubjectId: '102' },
      { subjectNo: 3, examSubjectId: '103' }
    ],
    replaceableDrafts: syllabusVersionId === '20001'
      ? [{ id: '30001', title: '系统架构设计师教程（草稿）', edition: '第4版', syllabusVersionId: '20001' }]
      : []
  });
}

function getMockImportContextOptionsValue(): ImportContextOptionsVO {
  return {
    syllabusVersions: [{ id: '20001', label: '系统架构设计师 · 2026 考纲', knowledgeTreeAvailable: true, knowledgePointCount: 1001 }],
    examSubjects: [
      { id: '101', subjectNo: 1, label: '系统架构设计综合知识' },
      { id: '102', subjectNo: 2, label: '系统架构设计案例分析' },
      { id: '103', subjectNo: 3, label: '系统架构设计论文' }
    ]
  };
}

export async function createMockKnowledgePointImport(
  form: KnowledgePointImportCreateForm
): AxiosPromise<ImportBatchVO> {
  const signature = await createKnowledgePointSignature(form);
  const existingId = requests.get(form.requestId);
  if (existingId) {
    const existing = batches.get(existingId)!;
    if (existing.signature !== signature) {
      throwMockError(409, '请求号已被不同载荷使用', {
        errorCode: 'IMPORT_REQUEST_ID_CONFLICT',
        retryable: false,
        traceId: null,
        fieldErrors: []
      });
    }
    return success({ ...existing.batch, reused: true });
  }

  validateCreateForm(form);
  const analysis = await analyzeJsonl(
    form.file,
    new Map(form.subjectMappings.map(item => [item.subjectNo, item.examSubjectId]))
  );
  const id = String(++batchSequence);
  const batch: ImportBatchVO = {
    id,
    importType: 'knowledge_point',
    syllabusVersionId: form.syllabusVersionId,
    templateVersion: 'knowledge_point/1.0',
    status: 'uploaded',
    reused: false,
    createTime: new Date().toISOString()
  };
  batches.set(id, {
    batch,
    requestId: form.requestId,
    signature,
    ...analysis,
    startedAt: null,
    confirmedAt: null
  });
  requests.set(form.requestId, id);
  return success(batch);
}

/**
 * Mock does not unzip browser files. It represents the server result after ZIP layout parsing,
 * so the UI can exercise the question-import lifecycle before the backend is available.
 */
export async function createMockQuestionImport(form: QuestionImportCreateForm): AxiosPromise<ImportBatchVO> {
  const signature = await createQuestionSignature(form);
  const existingId = requests.get(form.requestId);
  if (existingId) {
    const existing = batches.get(existingId)!;
    if (existing.signature !== signature) {
      throwMockError(409, '请求号已被不同载荷使用', {
        errorCode: 'IMPORT_REQUEST_ID_CONFLICT',
        retryable: false,
        traceId: null,
        fieldErrors: []
      });
    }
    return success({ ...existing.batch, reused: true });
  }

  validateQuestionCreateForm(form);
  const id = String(++batchSequence);
  const batch: ImportBatchVO = {
    id,
    importType: 'question',
    derivedSubjects: [
      { subjectNo: 1, examSubjectId: '101', label: '系统架构设计综合知识' },
      { subjectNo: 2, examSubjectId: '102', label: '系统架构设计案例分析' },
      { subjectNo: 3, examSubjectId: '103', label: '系统架构设计论文' }
    ],
    knowledgeSyllabusVersionId: form.knowledgeSyllabusVersionId,
    templateVersion: 'question-zip/1.0',
    status: 'uploaded',
    reused: false,
    createTime: new Date().toISOString()
  };
  const invalidArchive = /(?:invalid|error)/i.test(form.file.name);
  const issues = invalidArchive
    ? [issue(1, '00001', '$.images[0]', 'error', 'QUESTION_IMAGE_MISSING', 'ZIP 中缺少题目引用图片')]
    : [
        issue(2, '00002', '$.answer', 'warning', 'QUESTION_ANSWER_MISSING', '答案为空，草稿不得提交审核或发布'),
        issue(3, '00003', '$.answer', 'warning', 'QUESTION_REFERENCE_ANSWER_REVIEW', '请确认案例题参考答案完整')
      ];
  const failedCount = invalidArchive ? 1 : 0;
  const totalCount = invalidArchive ? 3 : 3;
  batches.set(id, {
    batch,
    requestId: form.requestId,
    signature,
    issues,
    totalCount,
    validCount: totalCount - failedCount,
    warningCount: issues.filter(item => item.severity === 'warning').length,
    failedCount,
    startedAt: null,
    confirmedAt: null
  });
  requests.set(form.requestId, id);
  return success(batch);
}

export async function createMockTextbookImport(form: TextbookImportCreateForm): AxiosPromise<ImportBatchVO> {
  if (!form.title && form.mode === 'create') {
    throwMockError(400, '教材名称不能为空', {
      errorCode: 'IMPORT_CONTEXT_INVALID', retryable: false, traceId: null,
      fieldErrors: [{ field: 'title', code: 'REQUIRED', message: '新建教材必须填写名称' }]
    });
  }
  if (!form.documentId && form.mode === 'replace_draft') {
    throwMockError(400, '请选择待替换草稿', standardError('IMPORT_CONTEXT_INVALID'));
  }
  const id = String(++batchSequence);
  const batch: ImportBatchVO = {
    id,
    documentId: form.documentId ?? String(++batchSequence),
    importType: 'document_chunk',
    mode: form.mode,
    syllabusVersionId: form.syllabusVersionId,
    templateVersion: form.templateVersion,
    status: 'uploaded', reused: false, createTime: new Date().toISOString()
  };
  const invalid = /(?:invalid|error)/i.test(form.file.name);
  const issues = invalid
    ? [issue(1, 'textbook-ch01-001', '$.content_hash', 'error', 'IMPORT_CONTENT_HASH_INVALID', '内容哈希与正文不一致')]
    : [issue(2, 'textbook-ch01-002', '$.knowledge_points', 'warning', 'UNMAPPED_CHUNK', '内容块暂未关联知识点')];
  batches.set(id, {
    batch, requestId: form.requestId, signature: form.requestId, issues,
    totalCount: 2, validCount: invalid ? 1 : 2, warningCount: invalid ? 0 : 1, failedCount: invalid ? 1 : 0,
    startedAt: null, confirmedAt: null
  });
  return success(batch);
}

export async function validateMockImport(batchId: string): AxiosPromise<ImportValidationAcceptedVO> {
  const state = requireBatch(batchId);
  if (state.startedAt !== null) {
    const progress = buildProgress(state);
    if (progress.status === 'failed') {
      throwMockError(409, '当前批次状态不允许操作', standardError('IMPORT_BATCH_STATE_INVALID'));
    }
    return success({
      id: batchId,
      status: progress.status,
      currentStage: progress.currentStage,
      accepted: false
    });
  }

  state.startedAt = Date.now();
  state.batch.status = 'parsing';
  await delay(mockDelay);
  return success({ id: batchId, status: 'parsing', currentStage: 'read_jsonl', accepted: true }, '预检任务已受理');
}

export async function confirmMockKnowledgePointImport(
  batchId: string,
  _requestId: string
): AxiosPromise<ImportConfirmationAcceptedVO> {
  const state = requireBatch(batchId);
  if (buildProgress(state).status !== 'waiting_confirm') {
    throwMockError(409, '当前批次尚未完成预检', standardError('IMPORT_BATCH_STATE_INVALID'));
  }
  state.confirmedAt = Date.now();
  state.batch.status = 'importing';
  await delay(mockDelay);
  return success(
    {
      id: batchId,
      status: 'importing',
      currentStage: state.batch.importType === 'question' ? 'persist_questions' : 'persist_knowledge_tree',
      accepted: true
    },
    state.batch.importType === 'question' ? '题目导入任务已受理' : '确认导入任务已受理'
  );
}

export async function getMockImportProgress(batchId: string): AxiosPromise<ImportProgressVO> {
  await delay(mockDelay);
  return success(buildProgress(requireBatch(batchId)));
}

export async function getMockTextbookImportPreview(
  batchId: string,
  query: Pick<PageQuery, 'pageNum' | 'pageSize'>
): AxiosPromise<TextbookImportPreviewVO> {
  const state = requireBatch(batchId);
  if (state.batch.importType !== 'document_chunk') throwMockError(400, '不支持教材预览', standardError('IMPORT_PREVIEW_UNSUPPORTED'));
  const rows = [
    { lineNo: 1, sourceKey: 'textbook-ch01-001', chunkOrder: 1, heading: '绪论', headingPath: ['第1章 绪论'], contentPreview: '系统架构设计师教材内容摘要。', pageStart: 1, pageEnd: 1, knowledgePoints: [{ id: '9001', subjectNo: 1 as const, code: '1.1', title: '系统架构概述' }], issueCount: 0 },
    { lineNo: 2, sourceKey: 'textbook-ch01-002', chunkOrder: 2, heading: '发展历程', headingPath: ['第1章 绪论', '1.1 发展历程'], contentPreview: '未映射的内容块摘要。', pageStart: 2, pageEnd: 2, knowledgePoints: [], issueCount: state.issues.length }
  ];
  const start = (query.pageNum - 1) * query.pageSize;
  return success({
    document: { id: state.batch.documentId!, title: state.batch.mode === 'replace_draft' ? '系统架构设计师教程（草稿）' : '新建教材', edition: '第4版', syllabusVersionName: '系统架构设计师 · 2026 考纲' },
    summary: { totalChunks: 2, validChunks: state.validCount, mappedChunks: 1, unmappedChunks: 1, knowledgeRelationCount: 1, imageCount: 0, errorCount: state.failedCount, warningCount: state.warningCount },
    directory: [], chunks: { rows: rows.slice(start, start + query.pageSize), total: rows.length }
  });
}

export async function listMockImportIssues(
  batchId: string,
  query: ImportIssueQuery
): AxiosPromise<PageResult<ImportIssueVO>> {
  const state = requireBatch(batchId);
  let rows = state.issues.filter(
    item =>
      (!query.severity || item.severity === query.severity) && (!query.issueCode || item.issueCode === query.issueCode)
  );
  const direction = query.isAsc === 'desc' || query.isAsc === 'descending' ? -1 : 1;
  const column = query.orderByColumn ?? 'lineNo';
  rows = rows.toSorted((left, right) => compareIssue(left, right, column) * direction);
  const start = (query.pageNum - 1) * query.pageSize;
  await delay(mockDelay);
  return success({ rows: rows.slice(start, start + query.pageSize), total: rows.length });
}

function validateCreateForm(form: KnowledgePointImportCreateForm) {
  const fieldErrors: ImportErrorVO['fieldErrors'] = [];
  if (form.syllabusVersionId !== '20001') {
    fieldErrors.push({ field: 'syllabusVersionId', code: 'NOT_FOUND', message: '考纲版本不存在' });
  }
  if (form.templateVersion !== 'knowledge_point/1.0') {
    fieldErrors.push({ field: 'templateVersion', code: 'UNSUPPORTED', message: '模板版本不受支持' });
  }
  if (form.subjectMappings.length < 1 || form.subjectMappings.length > 3) {
    fieldErrors.push({ field: 'subjectMappings', code: 'OUT_OF_RANGE', message: '科目映射数量必须为1至3' });
  }
  if (fieldErrors.length) {
    throwMockError(400, '导入上下文不合法', {
      errorCode: 'IMPORT_CONTEXT_INVALID',
      retryable: false,
      traceId: null,
      fieldErrors
    });
  }
}

function validateQuestionCreateForm(form: QuestionImportCreateForm) {
  const fieldErrors: ImportErrorVO['fieldErrors'] = [];
  if (form.knowledgeSyllabusVersionId !== '20001') {
    fieldErrors.push({ field: 'knowledgeSyllabusVersionId', code: 'NOT_FOUND', message: '知识点考纲版本不存在' });
  }
  if (form.templateVersion !== 'question-zip/1.0') {
    fieldErrors.push({ field: 'templateVersion', code: 'UNSUPPORTED', message: '模板版本不受支持' });
  }
  if (!form.file.name.toLowerCase().endsWith('.zip')) {
    fieldErrors.push({ field: 'file', code: 'INVALID_FORMAT', message: '题目导入只支持 ZIP 包' });
  }
  if (fieldErrors.length) {
    throwMockError(400, '导入上下文不合法', {
      errorCode: 'IMPORT_CONTEXT_INVALID',
      retryable: false,
      traceId: null,
      fieldErrors
    });
  }
}

async function analyzeJsonl(file: File, subjectMappings: Map<number, string>) {
  const text = await file.text();
  const sourceLines = text.split(/\r?\n/);
  if (sourceLines.at(-1) === '') sourceLines.pop();
  const lines: ParsedLine[] = [];
  const issues: ImportIssueVO[] = [];

  for (const [index, sourceLine] of sourceLines.entries()) {
    const lineNo = index + 1;
    const byteLength = new TextEncoder().encode(sourceLine).byteLength;
    if (byteLength > 1024 * 1024) {
      issues.push(issue(lineNo, null, `$line[${lineNo}]`, 'error', 'KP_JSON_LINE_TOO_LARGE', `第${lineNo}行超过1MiB`));
      lines.push(emptyParsedLine(lineNo));
      continue;
    }

    let value: unknown;
    try {
      value = JSON.parse(sourceLine);
    } catch {
      issues.push(issue(lineNo, null, `$line[${lineNo}]`, 'error', 'KP_JSON_INVALID', `第${lineNo}行不是合法JSON`));
      lines.push(emptyParsedLine(lineNo));
      continue;
    }

    if (!value || typeof value !== 'object' || Array.isArray(value)) {
      issues.push(
        issue(lineNo, null, `$line[${lineNo}]`, 'error', 'KP_JSON_INVALID', `第${lineNo}行根节点必须是JSON对象`)
      );
      lines.push(emptyParsedLine(lineNo));
      continue;
    }

    const record = value as Record<string, unknown>;
    const sourceKey = typeof record.source_key === 'string' ? record.source_key : null;
    const parsed: ParsedLine = {
      lineNo,
      sourceKey,
      subjectNo: typeof record.subject_no === 'number' ? record.subject_no : null,
      syllabusNumber: typeof record.syllabus_number === 'string' ? record.syllabus_number : null,
      parentSyllabusNumber: typeof record.parent_syllabus_number === 'string' ? record.parent_syllabus_number : null,
      value: record,
      hasError: false
    };
    for (const field of requiredFields) {
      if (!(field in record))
        addLineIssue(parsed, issues, `$.${field}`, 'KP_REQUIRED_FIELD_MISSING', `缺少必填字段 ${field}`);
    }
    for (const field of Object.keys(record)) {
      if (!requiredFields.includes(field as (typeof requiredFields)[number])) {
        addLineIssue(parsed, issues, `$.${field}`, 'KP_UNKNOWN_FIELD', `出现未知字段 ${field}`);
      }
    }
    if (record.schema_version !== '1.0') {
      addLineIssue(parsed, issues, '$.schema_version', 'KP_SCHEMA_VERSION_UNSUPPORTED', 'Schema版本不受支持');
    }
    if (!parsed.subjectNo || ![1, 2, 3].includes(parsed.subjectNo)) {
      addLineIssue(parsed, issues, '$.subject_no', 'KP_SUBJECT_NO_INVALID', 'subject_no只能为1、2、3');
    } else if (!subjectMappings.has(parsed.subjectNo)) {
      addLineIssue(parsed, issues, '$.subject_no', 'KP_SUBJECT_MAPPING_UNKNOWN', '科目没有显式映射');
    }
    lines.push(parsed);
  }

  addCrossLineIssues(lines, issues);
  const failedLines = new Set(issues.filter(item => item.severity === 'error').map(item => item.lineNo));
  return {
    issues,
    totalCount: lines.length,
    validCount: lines.length - failedLines.size,
    warningCount: issues.filter(item => item.severity === 'warning').length,
    failedCount: failedLines.size
  };
}

function addCrossLineIssues(lines: ParsedLine[], issues: ImportIssueVO[]) {
  const sourceKeys = new Map<string, number>();
  const numbers = new Map<string, number>();
  const knownNumbers = new Set(
    lines.flatMap(line => (line.subjectNo && line.syllabusNumber ? [`${line.subjectNo}:${line.syllabusNumber}`] : []))
  );
  for (const line of lines) {
    if (line.sourceKey) {
      if (sourceKeys.has(line.sourceKey))
        addLineIssue(line, issues, '$.source_key', 'KP_DUPLICATE_SOURCE_KEY', '来源键重复');
      else sourceKeys.set(line.sourceKey, line.lineNo);
    }
    if (line.subjectNo && line.syllabusNumber) {
      const key = `${line.subjectNo}:${line.syllabusNumber}`;
      if (numbers.has(key))
        addLineIssue(line, issues, '$.syllabus_number', 'KP_DUPLICATE_SYLLABUS_NUMBER', '同科目知识点编号重复');
      else numbers.set(key, line.lineNo);
      if (line.parentSyllabusNumber && !knownNumbers.has(`${line.subjectNo}:${line.parentSyllabusNumber}`)) {
        addLineIssue(line, issues, '$.parent_syllabus_number', 'KP_PARENT_NOT_FOUND', '父节点不存在');
      }
    }
  }
}

function buildProgress(state: MockBatchState): ImportProgressVO {
  if (state.startedAt === null) {
    return progressVo(state, 'uploaded', null, 0, null, null);
  }
  const elapsed = Date.now() - state.startedAt;
  if (state.confirmedAt !== null) {
    const importElapsed = Date.now() - state.confirmedAt;
    if (importElapsed < 1800)
      return progressVo(
        state,
        'importing',
        state.batch.importType === 'question' ? 'persist_questions' : 'persist_knowledge_tree',
        100,
        state.startedAt,
        null
      );
    return progressVo(
      state,
      'completed',
      state.batch.importType === 'question' ? 'persist_questions' : 'persist_knowledge_tree',
      100,
      state.startedAt,
      state.confirmedAt + 1800
    );
  }
  if (elapsed < 1200)
    return progressVo(state, 'parsing', 'read_jsonl', 5 + (elapsed / 1200) * 25, state.startedAt, null);
  if (elapsed < 2400)
    return progressVo(
      state,
      'validating',
      'validate_schema',
      30 + ((elapsed - 1200) / 1200) * 20,
      state.startedAt,
      null
    );
  if (elapsed < 3600)
    return progressVo(
      state,
      'validating',
      'validate_mapping',
      50 + ((elapsed - 2400) / 1200) * 15,
      state.startedAt,
      null
    );
  if (elapsed < 4800)
    return progressVo(state, 'validating', 'validate_tree', 65 + ((elapsed - 3600) / 1200) * 25, state.startedAt, null);
  if (elapsed < mockDuration)
    return progressVo(
      state,
      'validating',
      'finalize_counts',
      90 + ((elapsed - 4800) / 1200) * 9,
      state.startedAt,
      null
    );
  return progressVo(state, 'waiting_confirm', 'finalize_counts', 100, state.startedAt, state.startedAt + mockDuration);
}

function progressVo(
  state: MockBatchState,
  status: ImportProgressVO['status'],
  currentStage: ImportProgressVO['currentStage'],
  progressPercent: number,
  startedAt: number | null,
  finishedAt: number | null
): ImportProgressVO {
  state.batch.status = status;
  return {
    id: state.batch.id,
    status,
    currentStage,
    progressPercent: Number(progressPercent.toFixed(2)),
    totalCount: state.totalCount,
    validCount: state.validCount,
    warningCount: state.warningCount,
    failedCount: state.failedCount,
    startedTime: startedAt ? new Date(startedAt).toISOString() : null,
    finishedTime: finishedAt ? new Date(finishedAt).toISOString() : null,
    failureTraceId: null,
    knowledgeDiffRequired: state.batch.importType === 'knowledge_point'
  };
}

function requireBatch(batchId: string) {
  const state = batches.get(batchId);
  if (!state) throwMockError(404, '导入批次不存在', standardError('IMPORT_BATCH_NOT_FOUND'));
  return state;
}

function standardError(errorCode: string): ImportErrorVO {
  return { errorCode, retryable: false, traceId: null, fieldErrors: [] };
}

function throwMockError(code: number, message: string, responseData: ImportErrorVO): never {
  const error = new Error(message) as Error & {
    isHandled: boolean;
    responseCode: number;
    responseData: ImportErrorVO;
  };
  error.isHandled = true;
  error.responseCode = code;
  error.responseData = responseData;
  throw error;
}

function success<T>(data: T, msg = '操作成功'): AxiosPromise<T> {
  return resolveAfter<RuoYiAjaxResult<T>>({ code: 200, msg, data });
}

function resolveAfter<T>(value: T): Promise<T> {
  return delay(mockDelay).then(() => value);
}

function delay(duration: number) {
  return new Promise<void>(resolve => setTimeout(resolve, duration));
}

async function createKnowledgePointSignature(form: KnowledgePointImportCreateForm) {
  const digest = await crypto.subtle.digest('SHA-256', await form.file.arrayBuffer());
  const hash = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('');
  return JSON.stringify({
    importType: form.importType,
    syllabusVersionId: form.syllabusVersionId,
    subjectMappings: form.subjectMappings.toSorted((left, right) => left.subjectNo - right.subjectNo),
    templateVersion: form.templateVersion,
    hash
  });
}

async function createQuestionSignature(form: QuestionImportCreateForm) {
  const digest = await crypto.subtle.digest('SHA-256', await form.file.arrayBuffer());
  const hash = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('');
  return JSON.stringify({
    importType: form.importType,
    knowledgeSyllabusVersionId: form.knowledgeSyllabusVersionId,
    templateVersion: form.templateVersion,
    hash
  });
}

function issue(
  lineNo: number,
  sourceKey: string | null,
  fieldPath: string | null,
  severity: ImportIssueVO['severity'],
  issueCode: string,
  message: string
): ImportIssueVO {
  return { lineNo, sourceKey, fieldPath, severity, issueCode, message, createTime: new Date().toISOString() };
}

function addLineIssue(
  line: ParsedLine,
  issues: ImportIssueVO[],
  fieldPath: string,
  issueCode: string,
  message: string
) {
  line.hasError = true;
  issues.push(issue(line.lineNo, line.sourceKey, fieldPath, 'error', issueCode, message));
}

function emptyParsedLine(lineNo: number): ParsedLine {
  return {
    lineNo,
    sourceKey: null,
    subjectNo: null,
    syllabusNumber: null,
    parentSyllabusNumber: null,
    value: null,
    hasError: true
  };
}

function compareIssue(
  left: ImportIssueVO,
  right: ImportIssueVO,
  column: NonNullable<ImportIssueQuery['orderByColumn']>
) {
  if (column === 'lineNo') return left.lineNo - right.lineNo;
  if (column === 'severity')
    return left.severity === right.severity ? left.lineNo - right.lineNo : left.severity === 'error' ? -1 : 1;
  return String(left[column]).localeCompare(String(right[column]), 'zh-CN') || left.lineNo - right.lineNo;
}
