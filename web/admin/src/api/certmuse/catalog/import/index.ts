import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  ImportBatchVO,
  CompletedImportBatchDetailVO,
  CompletedImportBatchPageVO,
  CompletedImportBatchQuery,
  ImportBatchFilterOptionsVO,
  ImportConfirmationAcceptedVO,
  ImportContextOptionsVO,
  ImportIssueQuery,
  ImportIssueVO,
  ImportProgressVO,
  ImportValidationAcceptedVO,
  KnowledgePointImportCreateForm,
  KnowledgeDiffBatchResolutionForm,
  KnowledgeDiffPageVO,
  KnowledgeDiffQuery,
  KnowledgeDiffResolutionForm,
  KnowledgeDiffResolutionResultVO,
  QuestionImportContextOptionsVO,
  QuestionImportCreateForm,
  TextbookImportContextOptionsVO,
  TextbookImportCreateForm,
  TextbookImportPreviewVO
} from './types';

// Mock preview is an explicit Vite alias to a separate adapter. Keep the
// running API entry point independent from mock-only branches.
export const importMockEnabled = false;

/** 已完成批次展示页使用的筛选项；此接口不携带 type 参数。 */
export function getImportBatchFilterOptions(): Promise<ImportBatchFilterOptionsVO> {
  return request({
    url: '/api/admin/imports/options',
    method: 'get'
  }).then(response => response.data);
}

/** 查询当前用户可见的成功导入批次。 */
export function listCompletedImportBatches(query: CompletedImportBatchQuery): Promise<CompletedImportBatchPageVO> {
  return request({
    url: '/api/admin/imports',
    method: 'get',
    params: query
  }).then(response => response.data);
}

export function getCompletedImportBatch(id: string): Promise<CompletedImportBatchDetailVO> {
  return request({
    url: '/api/admin/imports/' + id,
    method: 'get'
  }).then(response => response.data);
}

export function getImportContextOptions(syllabusVersionId?: string, certificationId?: string): Promise<ImportContextOptionsVO> {
  return request({
    url: '/api/admin/imports/options',
    method: 'get',
    params: { type: 'knowledge_point', syllabusVersionId, certificationId }
  }).then(response => response.data);
}

export function getQuestionImportContextOptions(): Promise<QuestionImportContextOptionsVO> {
  return request({
    url: '/api/admin/imports/options',
    method: 'get',
    params: { type: 'question' }
  }).then(response => response.data);
}

export function getTextbookImportContextOptions(syllabusVersionId?: string): Promise<TextbookImportContextOptionsVO> {
  return request({
    url: '/api/admin/imports/options',
    method: 'get',
    params: { type: 'textbook', syllabusVersionId }
  }).then(response => response.data);
}

export function createKnowledgePointImport(form: KnowledgePointImportCreateForm): AxiosPromise<ImportBatchVO> {
  const data = new FormData();
  data.append('file', form.file);
  data.append('importType', form.importType);
  if (form.certificationId) data.append('certificationId', form.certificationId);
  if (form.syllabusVersionId) data.append('syllabusVersionId', form.syllabusVersionId);
  data.append('subjectMappings', JSON.stringify(form.subjectMappings));
  data.append('templateVersion', form.templateVersion);

  return request({
    url: '/api/admin/imports',
    method: 'post',
    data,
    headers: {
      'X-Request-Id': form.requestId,
      repeatSubmit: false
    }
  });
}

export function createQuestionImport(form: QuestionImportCreateForm): AxiosPromise<ImportBatchVO> {
  const data = new FormData();
  data.append('file', form.file);
  data.append('importType', form.importType);
  data.append('knowledgeSyllabusVersionId', form.knowledgeSyllabusVersionId);
  data.append('templateVersion', form.templateVersion);

  return request({
    url: '/api/admin/imports',
    method: 'post',
    data,
    headers: {
      'X-Request-Id': form.requestId,
      repeatSubmit: false
    }
  });
}

export function createTextbookImport(form: TextbookImportCreateForm): AxiosPromise<ImportBatchVO> {
  const data = new FormData();
  data.append('file', form.file);
  data.append('importType', form.importType);
  data.append('templateVersion', form.templateVersion);
  data.append('mode', form.mode);
  data.append('syllabusVersionId', form.syllabusVersionId);
  data.append('subjectMappings', JSON.stringify(form.subjectMappings));
  if (form.title) data.append('title', form.title);
  if (form.edition) data.append('edition', form.edition);
  if (form.documentId) data.append('documentId', form.documentId);
  return request({
    url: '/api/admin/imports',
    method: 'post',
    data,
    headers: { 'X-Request-Id': form.requestId, repeatSubmit: false }
  });
}

export function validateImport(batchId: string): AxiosPromise<ImportValidationAcceptedVO> {
  return request({
    url: '/api/admin/imports/' + batchId + '/validate',
    method: 'post'
  });
}

export function confirmKnowledgePointImport(
  batchId: string,
  requestId: string
): AxiosPromise<ImportConfirmationAcceptedVO> {
  return request({
    url: '/api/admin/imports/' + batchId + '/confirm',
    method: 'post',
    headers: { 'X-Request-Id': requestId, repeatSubmit: false }
  });
}

export function confirmImport(batchId: string, requestId: string): AxiosPromise<ImportConfirmationAcceptedVO> {
  return confirmKnowledgePointImport(batchId, requestId);
}

export function getImportProgress(batchId: string): AxiosPromise<ImportProgressVO> {
  return request({
    url: '/api/admin/imports/' + batchId + '/progress',
    method: 'get'
  });
}

export function getTextbookImportPreview(
  batchId: string,
  query: Pick<PageQuery, 'pageNum' | 'pageSize'>
): AxiosPromise<TextbookImportPreviewVO> {
  return request({ url: '/api/admin/imports/' + batchId + '/preview', method: 'get', params: query });
}

export function listImportIssues(batchId: string, query: ImportIssueQuery): AxiosPromise<PageResult<ImportIssueVO>> {
  return request({
    url: '/api/admin/imports/' + batchId + '/issues',
    method: 'get',
    params: query
  });
}

export function getKnowledgeDiff(batchId: string, query: KnowledgeDiffQuery): AxiosPromise<KnowledgeDiffPageVO> {
  return request({
    url: `/api/admin/imports/${batchId}/knowledge-diff`,
    method: 'get',
    params: query
  });
}

export function resolveKnowledgeDiff(
  batchId: string,
  diffId: string,
  resolution: KnowledgeDiffResolutionForm,
  requestId: string
): AxiosPromise<KnowledgeDiffResolutionResultVO> {
  return request({
    url: `/api/admin/imports/${batchId}/knowledge-diff/${diffId}`,
    method: 'put',
    data: resolution,
    headers: { 'X-Request-Id': requestId, repeatSubmit: false }
  });
}

export function resolveKnowledgeDiffBatch(
  batchId: string,
  resolutions: KnowledgeDiffBatchResolutionForm[],
  requestId: string
): AxiosPromise<KnowledgeDiffResolutionResultVO> {
  return request({
    url: `/api/admin/imports/${batchId}/knowledge-diff/batch`,
    method: 'put',
    data: { resolutions },
    headers: { 'X-Request-Id': requestId, repeatSubmit: false }
  });
}
