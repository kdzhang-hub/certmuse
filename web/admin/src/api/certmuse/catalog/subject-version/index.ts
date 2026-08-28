import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { QualificationForm, QualificationPageQuery, QualificationVO, SyllabusVersionForm, SyllabusVersionVO } from './types';

const baseUrl = '/api/admin/catalog/certifications';
const requestId = () => crypto.randomUUID();

export const listQualifications = (params: QualificationPageQuery): AxiosPromise<PageResult<QualificationVO>> => request({ url: baseUrl, method: 'get', params });
export const createQualification = (data: QualificationForm): AxiosPromise<QualificationVO> => request({ url: baseUrl, method: 'post', data, headers: { 'X-Request-Id': requestId() } });
export const updateQualification = (id: string, data: QualificationForm): AxiosPromise<QualificationVO> => request({ url: `${baseUrl}/${id}`, method: 'put', data, headers: { 'X-Request-Id': requestId() } });
export const deleteQualification = (id: string): AxiosPromise<void> => request({ url: `${baseUrl}/${id}`, method: 'delete', headers: { 'X-Request-Id': requestId() } });
export const createSyllabusVersion = (certificationId: string, data: SyllabusVersionForm): AxiosPromise<SyllabusVersionVO> => request({ url: `${baseUrl}/${certificationId}/syllabus-versions`, method: 'post', data, headers: { 'X-Request-Id': requestId() } });
export const updateSyllabusVersion = (certificationId: string, versionId: string, data: SyllabusVersionForm): AxiosPromise<SyllabusVersionVO> => request({ url: `${baseUrl}/${certificationId}/syllabus-versions/${versionId}`, method: 'put', data, headers: { 'X-Request-Id': requestId() } });
export const deleteSyllabusVersion = (certificationId: string, versionId: string): AxiosPromise<void> => request({ url: `${baseUrl}/${certificationId}/syllabus-versions/${versionId}`, method: 'delete', headers: { 'X-Request-Id': requestId() } });

export type * from './types';
