import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { PublicExamRegionsVO, PublicQualificationContextVO, PublicQualificationVO } from './types';

const baseUrl = '/api/public/exam-guidance';

export const listPublicQualifications = (): AxiosPromise<PublicQualificationVO[]> =>
  request({ url: `${baseUrl}/qualifications`, method: 'get' });

export const getPublicQualificationContext = (
  targetYear: number,
  targetHalf: 'H1' | 'H2'
): AxiosPromise<PublicQualificationContextVO> =>
  request({ url: `${baseUrl}/qualification-context`, method: 'get', params: { targetYear, targetHalf } });

export const listPublicExamRegions = (): AxiosPromise<PublicExamRegionsVO> =>
  request({ url: `${baseUrl}/regions`, method: 'get' });
