import type { QualificationVO } from '@/api/certmuse/catalog/subject-version/types';
import type { AxiosPromise, RuoYiAjaxResult } from '@/utils/api-types';
import type {
  PaperImportAcceptedVO,
  PaperImportBatchVO,
  PaperImportCreateForm,
  PaperImportIssuePage,
  PaperImportIssueQuery,
  PaperImportProgressVO
} from './types';

interface State {
  batch: PaperImportBatchVO;
  progress: PaperImportProgressVO;
  issues: PaperImportIssuePage['rows'];
  signature: string;
}
const states = new Map<string, State>();
const requests = new Map<string, string>();
let sequence = 40000;
const ok = <T>(data: T): AxiosPromise<T> => Promise.resolve({ code: 200, data } as RuoYiAjaxResult<T>);

export async function getMockPaperImportQualifications(): Promise<QualificationVO[]> {
  return [
    {
      id: '1',
      certificationCode: 'ARCH',
      certificationName: '系统架构设计师',
      qualificationLevel: 'HIGH',
      status: '0',
      sortOrder: 1,
      versionCount: 1,
      referenceCount: 0,
      createTime: '2026-08-01T00:00:00+08:00',
      updateTime: '2026-08-01T00:00:00+08:00',
      versions: []
    }
  ];
}

export async function createMockPaperImport(form: PaperImportCreateForm): AxiosPromise<PaperImportBatchVO> {
  const signature = `${form.collectionName}:${form.collectionType}:${form.durationMinutes}:${form.certificationId}:${form.examYear ?? ''}:${form.examMonth ?? ''}:${form.paperTypeCode ?? ''}:${form.paperTypeName ?? ''}:${form.file.name}:${form.file.size}`;
  const existingId = requests.get(form.requestId);
  if (existingId) return ok({ ...states.get(existingId)!.batch, reused: true });
  const id = String(++sequence);
  const invalid = /(?:invalid|error|cross)/i.test(form.file.name);
  const issues = invalid
    ? [
        {
          lineNo: 1,
          sourceKey: 'q-0001',
          fieldPath: '$.knowledgePoints',
          severity: 'error' as const,
          issueCode: 'PAPER_SYLLABUS_UNRESOLVED',
          message: '题目无法确定唯一考纲版本',
          createTime: new Date().toISOString()
        }
      ]
    : [];
  const batch: PaperImportBatchVO = {
    id,
    documentId: null,
    importType: 'paper',
    mode: null,
    syllabusVersionId: '20001',
    examSubjectId: null,
    knowledgeSyllabusVersionId: '20001',
    derivedSubjects: [],
    templateVersion: 'question-zip/1.0',
    status: 'uploaded',
    reused: false,
    createTime: new Date().toISOString()
  };
  const progress: PaperImportProgressVO = {
    id,
    status: 'uploaded',
    currentStage: null,
    progressPercent: 0,
    totalCount: 3,
    validCount: invalid ? 2 : 3,
    warningCount: 0,
    failedCount: issues.length,
    syllabusVersionId: null,
    syllabusVersionName: null,
    collectionId: null,
    revisionId: null,
    failureTraceId: null
  };
  states.set(id, { batch, progress, issues, signature });
  requests.set(form.requestId, id);
  return ok(batch);
}

export function validateMockPaperImport(id: string, _requestId: string): AxiosPromise<PaperImportAcceptedVO> {
  const state = states.get(id)!;
  const failed = state.issues.length > 0;
  state.batch.status = 'waiting_confirm';
  Object.assign(state.progress, { status: 'waiting_confirm', currentStage: 'resolve_syllabus', progressPercent: 100 });
  if (!failed) {
    Object.assign(state.progress, { syllabusVersionId: '20001', syllabusVersionName: '2026 考纲' });
  }
  return ok({ id, status: 'waiting_confirm', currentStage: 'resolve_syllabus', accepted: true });
}

export function getMockPaperImportProgress(id: string): AxiosPromise<PaperImportProgressVO> {
  return ok({ ...states.get(id)!.progress });
}
export function listMockPaperImportIssues(
  id: string,
  query: PaperImportIssueQuery
): AxiosPromise<PaperImportIssuePage> {
  const code = query.issueCode?.trim().toLowerCase();
  const rows = states
    .get(id)!
    .issues.filter(
      item => (!query.severity || item.severity === query.severity) && (!code || item.issueCode.toLowerCase() === code)
    );
  return ok({ rows, total: rows.length });
}
export function confirmMockPaperImport(id: string, _requestId: string): AxiosPromise<PaperImportAcceptedVO> {
  const state = states.get(id)!;
  if (state.progress.warningCount || state.progress.failedCount)
    return Promise.reject(new Error('预检存在 warning 或 error，不能确认导入。'));
  state.batch.status = 'completed';
  Object.assign(state.progress, {
    status: 'completed',
    currentStage: 'persist_paper',
    progressPercent: 100,
    collectionId: '30001',
    revisionId: '31001'
  });
  return ok({ id, status: 'completed', currentStage: 'persist_paper', accepted: true });
}
