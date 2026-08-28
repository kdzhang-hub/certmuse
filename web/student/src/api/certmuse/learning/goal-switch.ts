import type { AxiosPromise } from '@/utils/api-types';
import * as mock from '@/api/student/mock';
import request from '@/utils/request';

const inMockMode = () => import.meta.env.VITE_APP_STUDENT_MOCK === 'true';

export interface GoalSwitchCurrentGoalVo {
  id: string;
  certificationId: string;
  certificationName: string;
  syllabusVersionId: string;
  syllabusVersionName: string;
  targetExamYear: number;
  targetExamMonth: 5 | 11;
  targetExamDate: string | null;
  examBatchType: 'OFFICIAL' | 'ESTIMATED' | 'LEGACY_UNKNOWN';
  status: 'ACTIVE';
  version: number;
}

export interface GoalSwitchOptionsVo {
  serverTime: string;
  timezone: 'Asia/Shanghai';
  currentGoal: GoalSwitchCurrentGoalVo;
  interruptedSessions: Array<{
    sessionType: 'initial_diagnosis' | 'self_practice' | 'daily_task' | 'past_paper' | 'mock_exam';
    title: string;
    status: 'CREATED' | 'IN_PROGRESS';
  }>;
  certifications: Array<{
    id: string;
    code: string;
    name: string;
    examBatches: Array<{
      targetExamYear: number;
      targetExamMonth: 5 | 11;
      examBatchType: 'OFFICIAL' | 'ESTIMATED';
      targetExamDate: string | null;
    }>;
  }>;
}

export interface SwitchLearningGoalRequest {
  certificationId: string;
  targetExamYear: number;
  targetExamMonth: 5 | 11;
  expectedCurrentGoalVersion: number;
  confirmAbandonInProgress: boolean;
}

export interface SwitchLearningGoalResultVo {
  previousGoal: { id: string; status: 'PAUSED'; version: number };
  currentGoal: GoalSwitchCurrentGoalVo;
  abandonedSessionCount: number;
  nextAction: 'ENTER_HOME';
}

export interface GoalSwitchErrorVo {
  errorCode: string;
  retryable: boolean;
  traceId: string | null;
  fieldErrors: Array<{ field: string; code: string; message: string }>;
  details: { abandonedSessionCount?: number } | null;
}

export const getGoalSwitchOptions = (): AxiosPromise<GoalSwitchOptionsVo> => {
  if (inMockMode()) return mock.getGoalSwitchOptions();
  return request({ url: '/api/learning/goals/current/switch-options', method: 'get' });
};

export const switchLearningGoal = (
  data: SwitchLearningGoalRequest,
  requestId: string
): AxiosPromise<SwitchLearningGoalResultVo> => {
  if (inMockMode()) return mock.switchLearningGoal(data);
  return request({
    url: '/api/learning/goals/switch',
    method: 'post',
    data,
    headers: { 'X-Request-Id': requestId, repeatSubmit: false }
  });
};
