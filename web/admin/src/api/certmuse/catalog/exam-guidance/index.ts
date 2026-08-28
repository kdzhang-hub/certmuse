import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  ExamPeriodCreateForm,
  ExamPeriodDetailVO,
  ExamPeriodQuery,
  ExamPeriodUpdateForm,
  ExamPeriodVO,
  ExamRegionQuery,
  ExamRegionRegistrationForm,
  ExamRegionUpdateForm,
  ExamRegionVO,
  ExamScheduleForm,
  PeriodMutationResult
} from './types';

const baseUrl = '/api/admin/catalog';
const writeHeaders = () => ({ 'X-Request-Id': crypto.randomUUID(), repeatSubmit: false });

export const listExamPeriods = (params: ExamPeriodQuery): AxiosPromise<PageResult<ExamPeriodVO>> =>
  request({ url: `${baseUrl}/exam-periods`, method: 'get', params });

export const getExamPeriod = (periodId: string): AxiosPromise<ExamPeriodDetailVO> =>
  request({ url: `${baseUrl}/exam-periods/${periodId}`, method: 'get' });

export const createExamPeriod = (data: ExamPeriodCreateForm): AxiosPromise<ExamPeriodDetailVO> =>
  request({ url: `${baseUrl}/exam-periods`, method: 'post', data, headers: writeHeaders() });

export const updateExamPeriod = (periodId: string, data: ExamPeriodUpdateForm): AxiosPromise<ExamPeriodDetailVO> =>
  request({ url: `${baseUrl}/exam-periods/${periodId}`, method: 'put', data, headers: writeHeaders() });

export const deleteExamPeriod = (periodId: string, expectedRowVersion: number) =>
  request({
    url: `${baseUrl}/exam-periods/${periodId}`,
    method: 'delete',
    data: { expectedRowVersion },
    headers: writeHeaders()
  });

export const createExamPeriodRevision = (
  periodId: string,
  expectedRowVersion: number
): AxiosPromise<ExamPeriodDetailVO> =>
  request({
    url: `${baseUrl}/exam-periods/${periodId}/revisions`,
    method: 'post',
    data: { expectedRowVersion },
    headers: writeHeaders()
  });

export const publishExamPeriod = (periodId: string, expectedRowVersion: number): AxiosPromise<PeriodMutationResult> =>
  request({
    url: `${baseUrl}/exam-periods/${periodId}/publish`,
    method: 'post',
    data: { expectedRowVersion },
    headers: writeHeaders()
  });

export const createExamSchedule = (periodId: string, data: ExamScheduleForm): AxiosPromise<PeriodMutationResult> =>
  request({ url: `${baseUrl}/exam-periods/${periodId}/schedules`, method: 'post', data, headers: writeHeaders() });

export const updateExamSchedule = (scheduleId: string, data: ExamScheduleForm): AxiosPromise<PeriodMutationResult> =>
  request({ url: `${baseUrl}/exam-schedules/${scheduleId}`, method: 'put', data, headers: writeHeaders() });

export const deleteExamSchedule = (
  scheduleId: string,
  expectedPeriodRowVersion: number
): AxiosPromise<PeriodMutationResult> =>
  request({
    url: `${baseUrl}/exam-schedules/${scheduleId}`,
    method: 'delete',
    data: { expectedPeriodRowVersion },
    headers: writeHeaders()
  });

export const listExamRegions = (params: ExamRegionQuery): AxiosPromise<PageResult<ExamRegionVO>> =>
  request({ url: `${baseUrl}/exam-regions`, method: 'get', params });

export const getExamRegion = (regionId: string): AxiosPromise<ExamRegionVO> =>
  request({ url: `${baseUrl}/exam-regions/${regionId}`, method: 'get' });

export const updateExamRegion = (regionId: string, data: ExamRegionUpdateForm): AxiosPromise<ExamRegionVO> =>
  request({ url: `${baseUrl}/exam-regions/${regionId}`, method: 'put', data, headers: writeHeaders() });

export const createExamRegionRegistration = (
  periodId: string,
  data: ExamRegionRegistrationForm
): AxiosPromise<PeriodMutationResult> =>
  request({ url: `${baseUrl}/exam-periods/${periodId}/registrations`, method: 'post', data, headers: writeHeaders() });

export const updateExamRegionRegistration = (
  registrationId: string,
  data: ExamRegionRegistrationForm
): AxiosPromise<PeriodMutationResult> =>
  request({
    url: `${baseUrl}/exam-region-registrations/${registrationId}`,
    method: 'put',
    data,
    headers: writeHeaders()
  });

export const deleteExamRegionRegistration = (
  registrationId: string,
  expectedPeriodRowVersion: number
): AxiosPromise<PeriodMutationResult> =>
  request({
    url: `${baseUrl}/exam-region-registrations/${registrationId}`,
    method: 'delete',
    data: { expectedPeriodRowVersion },
    headers: writeHeaders()
  });

export type * from './types';
