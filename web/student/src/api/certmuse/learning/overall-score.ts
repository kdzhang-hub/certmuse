import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';

/** Current-goal qualification score; null means that no overall profile exists yet. */
export interface OverallScoreVo {
  certificationName: string;
  overallScore: number | null;
  calculatedTime: string | null;
}

export const getOverallScore = (): AxiosPromise<OverallScoreVo> =>
  request({ url: '/api/learning/profile/overall-score', method: 'get' });
