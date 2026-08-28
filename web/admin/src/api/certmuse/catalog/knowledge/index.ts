import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { KnowledgeTreeVO, SyllabusListPage, SyllabusPublishedDateForm, SyllabusQuery } from './types';

const baseUrl = '/api/admin/catalog/syllabus-versions';
const requestId = () => crypto.randomUUID();

export function listSyllabusVersions(query: SyllabusQuery): AxiosPromise<SyllabusListPage> {
  return request({ url: baseUrl, method: 'get', params: query });
}

export function getKnowledgeTree(syllabusVersionId: string): AxiosPromise<KnowledgeTreeVO> {
  return request({ url: `${baseUrl}/${syllabusVersionId}/knowledge-tree`, method: 'get' });
}

export function updateSyllabusPublishedDate(
  syllabusVersionId: string,
  data: SyllabusPublishedDateForm
): AxiosPromise<void> {
  return request({
    url: `${baseUrl}/${syllabusVersionId}/published-date`,
    method: 'put',
    data,
    headers: { 'X-Request-Id': requestId() }
  });
}

export function deleteSyllabusVersion(syllabusVersionId: string): AxiosPromise<void> {
  return request({ url: `${baseUrl}/${syllabusVersionId}`, method: 'delete' });
}
