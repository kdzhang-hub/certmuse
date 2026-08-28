import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  FormalExamDraftRequest,
  FormalExamFinishCheckVo,
  FormalExamFinishVo,
  FormalExamItemVo,
  FormalExamResultVo,
  FormalExamSessionVo,
  FormalExamStatusVo,
  FormalExamTimerEventVo,
  FormalExamTimerRequest
} from './formal-exam-types';

export interface SimulationSyllabusVersionVo {
  id: string;
  name: string;
}

export interface SimulationCertificationVo {
  id: string;
  name: string;
  syllabusVersions: SimulationSyllabusVersionVo[];
}

export interface SimulationSetupVo {
  serverTime: string;
  timezone: 'Asia/Shanghai';
  currentGoal: {
    id: string;
    certificationId: string;
    certificationName: string;
    syllabusVersionId: string;
    syllabusVersionName: string;
    version: number;
  } | null;
  certifications: SimulationCertificationVo[];
}

export interface SimulationSubjectVo {
  id: string;
  name: string;
}

export interface SimulationAccessVo {
  action: 'START' | 'RESUME' | 'BLOCKED';
  canStart: boolean;
  blockCode: string | null;
  activeSessionId: string | null;
  activeAnswerPath: string | null;
}

export interface SimulationListItemVo {
  collectionId: string;
  revisionId: string;
  collectionCode: string;
  collectionName: string;
  certificationId: string;
  certificationName: string;
  syllabusVersionId: string;
  syllabusVersionName: string;
  subject: SimulationSubjectVo | null;
  questionTypes: Array<'CHOICE' | 'CASE' | 'ESSAY'>;
  questionCount: number;
  totalReportScore: number;
  durationMinutes: number;
  publishedTime: string | null;
  access: SimulationAccessVo;
}

export interface SimulationDetailVo extends Omit<SimulationListItemVo, 'collectionCode'> {
  examMode: 'standard';
  rules: string[];
}

export interface SimulationPreviewQuestionVo {
  questionOrder: number;
  questionType: 'CHOICE' | 'CASE' | 'ESSAY';
  stem: string;
}

export interface SimulationPreviewVo {
  collectionId: string;
  revisionId: string;
  collectionName: string;
  questions: SimulationPreviewQuestionVo[];
}

export interface SimulationPageQuery {
  certificationId?: string;
  syllabusVersionId?: string;
  keyword?: string;
  pageNum?: number;
  pageSize?: number;
}

export interface SimulationPageVo {
  rows: SimulationListItemVo[];
  total: number;
}

export interface StartSimulationSessionRequest {
  expectedRevisionId: string;
  expectedGoalVersion: number;
}

export interface StartSimulationSessionVo {
  serverTime: string;
  sessionId: string;
  collectionId: string;
  revisionId: string;
  resumed: boolean;
  formalAttemptNo: number;
  isRetest: boolean;
  startedTime: string;
  durationSeconds: number;
  deadlineTime: string;
  answerPath: string;
}

export interface SimulationErrorVo {
  errorCode: string;
  retryable: boolean;
  traceId: string | null;
  fieldErrors: Array<{ field: string; code: 'REQUIRED' | 'INVALID_FORMAT' | 'OUT_OF_RANGE'; message: string }>;
  details: {
    currentRevisionId: string | null;
    sessionId: string | null;
    collectionId: string | null;
    answerPath: string | null;
  } | null;
}

export const getSimulationSetup = (): AxiosPromise<SimulationSetupVo> =>
  request({ url: '/api/assessment/simulations/setup', method: 'get' });

export const listSimulations = (query: SimulationPageQuery = {}): AxiosPromise<SimulationPageVo> =>
  request({ url: '/api/assessment/simulations', method: 'get', params: query });

export const getSimulationDetail = (collectionId: string): AxiosPromise<SimulationDetailVo> =>
  request({ url: `/api/assessment/simulations/${collectionId}`, method: 'get' });

export const getSimulationPreview = (collectionId: string): AxiosPromise<SimulationPreviewVo> =>
  request({ url: `/api/assessment/simulations/${collectionId}/preview`, method: 'get' });

export const startSimulationSession = (
  collectionId: string,
  data: StartSimulationSessionRequest,
  requestId: string = crypto.randomUUID().toLowerCase()
): AxiosPromise<StartSimulationSessionVo> =>
  request({
    url: `/api/assessment/simulations/${collectionId}/sessions`,
    method: 'post',
    data,
    headers: { 'X-Request-Id': requestId, repeatSubmit: false }
  });

const sessionBase = (sessionId: string) => `/api/assessment/simulations/sessions/${sessionId}`;
const write = (url: string, method: 'post' | 'put', data?: unknown) =>
  request({ url, method, data, headers: { 'X-Request-Id': crypto.randomUUID().toLowerCase(), repeatSubmit: false } });
export const getSimulationSession = (sessionId: string): AxiosPromise<FormalExamSessionVo> =>
  request({ url: sessionBase(sessionId), method: 'get' });
export const getSimulationItem = (sessionId: string, order: number): AxiosPromise<FormalExamItemVo> =>
  request({ url: `${sessionBase(sessionId)}/items/${order}`, method: 'get' });
export const saveSimulationDraft = (
  sessionId: string,
  order: number,
  data: FormalExamDraftRequest
): AxiosPromise<FormalExamSessionVo> => write(`${sessionBase(sessionId)}/items/${order}/draft`, 'put', data);
export const sendSimulationTimer = (
  sessionId: string,
  data: FormalExamTimerRequest
): AxiosPromise<FormalExamTimerEventVo> => write(`${sessionBase(sessionId)}/timer-events`, 'post', data);
export const pauseSimulation = (
  sessionId: string,
  data: { currentQuestionOrder: number; expectedSessionVersion: number }
): AxiosPromise<FormalExamSessionVo> => write(`${sessionBase(sessionId)}/pause`, 'post', data);
export const getSimulationFinishCheck = (sessionId: string): AxiosPromise<FormalExamFinishCheckVo> =>
  request({ url: `${sessionBase(sessionId)}/finish-check`, method: 'get' });
export const finishSimulation = (
  sessionId: string,
  data: { expectedSessionVersion: number }
): AxiosPromise<FormalExamFinishVo> => write(`${sessionBase(sessionId)}/finish`, 'post', data);
export const getSimulationStatus = (sessionId: string): AxiosPromise<FormalExamStatusVo> =>
  request({ url: `${sessionBase(sessionId)}/status`, method: 'get' });
export const regenerateSimulationResult = (sessionId: string): AxiosPromise<FormalExamStatusVo> =>
  write(`${sessionBase(sessionId)}/regenerate-result`, 'post', {});
export const getSimulationResult = (sessionId: string): AxiosPromise<FormalExamResultVo> =>
  request({ url: `${sessionBase(sessionId)}/result`, method: 'get' });
