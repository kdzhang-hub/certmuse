import type { QualificationVO } from '@/api/certmuse/catalog/subject-version/types';
import type { AxiosPromise } from '@/utils/api-types';
import { listQualifications } from '@/api/certmuse/catalog/subject-version';
import request from '@/utils/request';
import type {
  PaperImportAcceptedVO,
  PaperImportBatchVO,
  PaperImportCreateForm,
  PaperImportIssuePage,
  PaperImportIssueQuery,
  PaperImportProgressVO
} from './types';

// Mock preview is an explicit Vite alias to a separate adapter. Keep the
// running API entry point independent from mock-only branches.
export const paperImportMockEnabled = false;
const baseUrl = '/api/admin/paper-imports';

/** 资格数据沿用最新资格管理目录；Mock 模式不依赖未实现的后端。 */
export async function getPaperImportQualifications(): Promise<QualificationVO[]> {
  const response = await listQualifications({ status: '0', pageNum: 1, pageSize: 100 });
  return response.data?.rows ?? [];
}

export function createPaperImport(form: PaperImportCreateForm): AxiosPromise<PaperImportBatchVO> {
  const data = new FormData();
  data.append('file', form.file);
  data.append('collectionName', form.collectionName);
  data.append('collectionType', form.collectionType);
  data.append('durationMinutes', String(form.durationMinutes));
  data.append('certificationId', form.certificationId);
  if (form.collectionType === 'PAST_PAPER') {
    data.append('examYear', String(form.examYear));
    data.append('examMonth', String(form.examMonth));
    data.append('paperTypeCode', form.paperTypeCode ?? '');
    data.append('paperTypeName', form.paperTypeName ?? '');
  }
  return request({
    url: baseUrl,
    method: 'post',
    data,
    headers: { 'X-Request-Id': form.requestId, repeatSubmit: false }
  });
}

export function validatePaperImport(batchId: string, requestId: string): AxiosPromise<PaperImportAcceptedVO> {
  return request({
    url: `${baseUrl}/${batchId}/validate`,
    method: 'post',
    headers: { 'X-Request-Id': requestId, repeatSubmit: false }
  });
}

export function getPaperImportProgress(batchId: string): AxiosPromise<PaperImportProgressVO> {
  return request({ url: `${baseUrl}/${batchId}/progress`, method: 'get' });
}

export function listPaperImportIssues(
  batchId: string,
  params: PaperImportIssueQuery
): AxiosPromise<PaperImportIssuePage> {
  return request({ url: `${baseUrl}/${batchId}/issues`, method: 'get', params });
}

export function confirmPaperImport(batchId: string, requestId: string): AxiosPromise<PaperImportAcceptedVO> {
  return request({
    url: `${baseUrl}/${batchId}/confirm`,
    method: 'post',
    headers: { 'X-Request-Id': requestId, repeatSubmit: false }
  });
}

export type * from './types';
