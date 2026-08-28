import type { AxiosPromise } from '@/utils/api-types';
import * as mock from '@/api/student/mock';
import request from '@/utils/request';

const inMockMode = () => import.meta.env.VITE_APP_STUDENT_MOCK === 'true';

export interface LearningGoalOptionsVo {
  serverTime: string;
  timezone: 'Asia/Shanghai';
  certifications: Array<{ id: string; code: string; name: string }>;
  examYears: Array<{ year: number; months: Array<5 | 11>; selectable: boolean }>;
  dailyMinutes: { min: 15; max: 300; defaultValue: 30 };
  defaults: { certificationId: string | null; examYear: number; examMonth: 5 | 11 } | null;
}

export interface ContractFieldError {
  field: string;
  code: 'REQUIRED' | 'INVALID_FORMAT' | 'OUT_OF_RANGE' | 'DUPLICATE' | 'NOT_FOUND' | 'UNSUPPORTED';
  message: string;
}

export interface ContractErrorVo {
  errorCode: string;
  retryable: boolean;
  traceId: string | null;
  fieldErrors: ContractFieldError[];
}

export interface CreateLearningGoalRequest {
  certificationId: string;
  targetExamYear: number;
  targetExamMonth: 5 | 11;
  dailyMinutes: number;
}

export interface CreateLearningGoalResultVo {
  goal: {
    id: string;
    certificationId: string;
    certificationName: string;
    syllabusVersionId: string;
    syllabusVersionName: string;
    targetExamYear: number;
    targetExamMonth: 5 | 11;
    dailyMinutes: number;
    status: 'ACTIVE';
    version: number;
  };
  nextAction: 'START_DIAGNOSTIC';
}

export const getLearningGoalOptions = (): AxiosPromise<LearningGoalOptionsVo> => {
  if (inMockMode()) return mock.getLearningGoalOptions();
  return request({ url: '/api/learning/goals/options', method: 'get' });
};

export const createLearningGoal = (
  data: CreateLearningGoalRequest,
  requestId: string
): AxiosPromise<CreateLearningGoalResultVo> => {
  if (inMockMode()) return mock.createLearningGoal(data);
  return request({
    url: '/api/learning/goals',
    method: 'post',
    data,
    headers: { 'X-Request-Id': requestId, repeatSubmit: false }
  });
};
