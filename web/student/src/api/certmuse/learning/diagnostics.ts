import type { OnboardingNextAction } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import * as mock from '@/api/student/mock';
import request from '@/utils/request';

const inMockMode = () => import.meta.env.VITE_APP_STUDENT_MOCK === 'true';

export type DiagnosticStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
export type DiagnosticNextAction = Extract<
  OnboardingNextAction,
  'START_DIAGNOSTIC' | 'CONTINUE_DIAGNOSTIC' | 'WAIT_PROCESSING' | 'VIEW_DIAGNOSTIC_REPORT' | 'RETRY_DIAGNOSTIC_RESULT'
>;

/** The answer snapshot is the only answer shape accepted by the diagnostic API. */
export interface DiagnosticChoiceAnswer {
  schema_version: '1.0';
  answer_type: 'CHOICE';
  selection_mode: string;
  value: string[] | null;
}

export interface DiagnosticPreflightVo {
  goal: {
    id: string;
    certificationId: string;
    certificationName: string;
    syllabusVersionId: string;
    syllabusVersionName: string;
    version: number;
  };
  diagnosticStatus: DiagnosticStatus;
  nextAction: DiagnosticNextAction;
  diagnosticRevision: {
    id: string;
    questionCount: number;
    estimatedMinutes: number | null;
    subjectBreakdown: Array<{ examSubjectId: string; subjectName: string; questionCount: number }>;
  } | null;
  existingSession: {
    id: string;
    answeredCount: number;
    totalCount: number;
    lastQuestionOrder: number;
    sessionVersion: number;
  } | null;
  blockers: Array<{ code: string; message: string }>;
}

export interface DiagnosticSessionVo {
  sessionId: string;
  status: DiagnosticStatus;
  nextAction: OnboardingNextAction;
  sessionVersion: number;
  currentQuestionOrder: number;
  answeredCount: number;
  unansweredCount: number;
  /** Server-confirmed display timing facts. Client time is never submitted. */
  estimatedDurationSeconds: number;
  effectiveElapsedSeconds: number;
  serverTime: string;
  navigation: Array<{ questionOrder: number; state: 'CURRENT' | 'ANSWERED' | 'UNANSWERED' }>;
}

export type DiagnosticTimerEventType = 'ENTER' | 'HEARTBEAT' | 'HIDDEN' | 'LEAVE';

export interface DiagnosticTimerEventRequest {
  eventType: DiagnosticTimerEventType;
  questionOrder: number;
  leaseId: string | null;
}

export interface DiagnosticTimerEventVo {
  eventAccepted: boolean;
  leaseId: string | null;
  questionOrder: number;
  timerState: 'RUNNING' | 'STOPPED' | 'REPLACED';
  estimatedDurationSeconds: number;
  effectiveElapsedSeconds: number;
  serverTime: string;
}

export interface DiagnosticQuestionVo {
  questionOrder: number;
  questionType: 'CHOICE';
  stem: string;
  difficulty: string | null;
  options: Array<{ label: string; content: string; sortOrder: number }>;
  images: Array<{ url: string; alt: string | null; sortOrder: number }>;
  answer: DiagnosticChoiceAnswer;
  answerSaveState: 'SAVED';
  sessionVersion: number;
}

export interface FinishCheckVo {
  answeredCount: number;
  unansweredCount: number;
  pendingSaveCount: number;
  sessionVersion: number;
  firstUnansweredQuestionOrder: number | null;
}

export interface DiagnosticResultStatusVo {
  sessionId: string;
  diagnosticStatus: Extract<DiagnosticStatus, 'PROCESSING' | 'COMPLETED' | 'FAILED'>;
  nextAction: Extract<OnboardingNextAction, 'WAIT_PROCESSING' | 'VIEW_DIAGNOSTIC_REPORT' | 'RETRY_DIAGNOSTIC_RESULT'>;
  stages: Array<{
    code: 'SUBMITTED' | 'SCORING' | 'REPORT_GENERATING' | 'PROFILE_GENERATING' | 'COMPLETED';
    state: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  }>;
  failure: { message: string; traceId: string | null; retryAvailable: boolean } | null;
}

export interface DiagnosticReportPayload {
  subjectScores: {
    schema_version?: string;
    totalQuestions?: number;
    correctCount?: number;
    unansweredCount?: number;
    subjects?: Array<{ examSubjectId: string; questionCount: number; correctCount: number }>;
  };
  profileSummary: {
    schema_version?: string;
    knowledgePoints?: Array<{
      knowledgePointId: string;
      ability: number;
      status: string;
      confidence: 'low' | 'medium' | string;
      correctCount: number;
      questionCount: number;
    }>;
    priorityDirections?: Array<{
      knowledgePointId: string;
      ability: number;
      status: string;
      confidence: 'low' | 'medium' | string;
      correctCount: number;
      questionCount: number;
    }>;
    confidence?: 'low' | 'medium' | string;
    dataStatus?: string;
  };
}

export interface DiagnosticReportVo {
  diagnosticStatus: 'COMPLETED' | 'FAILED';
  profileStatus: 'AVAILABLE' | 'FAILED';
  nextAction: 'VIEW_DIAGNOSTIC_REPORT' | 'RETRY_DIAGNOSTIC_RESULT';
  report: DiagnosticReportPayload;
}

export interface DiagnosticErrorVo {
  errorCode: string;
  retryable: boolean;
  traceId: string | null;
  nextAction: OnboardingNextAction | null;
  fieldErrors: Array<{ field: string; code: string; message: string }>;
}

const call = <T>(mockCall: () => AxiosPromise<T>, config: any): AxiosPromise<T> =>
  inMockMode() ? mockCall() : request(config);

export const getDiagnosticPreflight = () =>
  call(mock.getDiagnosticPreflight, { url: '/api/assessment/diagnostics/preflight', method: 'get' });
export const startDiagnostic = (data: { diagnosticRevisionId: string; goalVersion: number }, requestId: string) =>
  call(() => mock.startDiagnostic(data), {
    url: '/api/assessment/diagnostics',
    method: 'post',
    data,
    headers: { 'X-Request-Id': requestId, repeatSubmit: false }
  });
export const getDiagnosticSession = (sessionId: string) =>
  call(() => mock.getDiagnosticSession(sessionId), { url: `/api/assessment/diagnostics/${sessionId}`, method: 'get' });
export const getDiagnosticQuestion = (sessionId: string, order: number) =>
  call(() => mock.getDiagnosticQuestion(sessionId, order), {
    url: `/api/assessment/diagnostics/${sessionId}/items/${order}`,
    method: 'get'
  });
export const sendDiagnosticTimerEvent = (
  sessionId: string,
  data: DiagnosticTimerEventRequest,
  requestId: string
) =>
  call(() => mock.sendDiagnosticTimerEvent(sessionId, data), {
    url: `/api/assessment/diagnostics/${sessionId}/timer-events`,
    method: 'post',
    data,
    headers: { 'X-Request-Id': requestId, repeatSubmit: false }
  });
export const saveDiagnosticDraft = (
  sessionId: string,
  order: number,
  data: { answer: DiagnosticChoiceAnswer; expectedSessionVersion: number; currentQuestionOrder: number },
  requestId: string
) =>
  call(() => mock.saveDiagnosticDraft(sessionId, order, data as any), {
    url: `/api/assessment/diagnostics/${sessionId}/items/${order}/draft`,
    method: 'put',
    data,
    headers: { 'X-Request-Id': requestId, repeatSubmit: false }
  });
export const pauseDiagnostic = (
  sessionId: string,
  data: { currentQuestionOrder: number; expectedSessionVersion: number },
  requestId: string
) =>
  call(() => mock.pauseDiagnostic(sessionId, data), {
    url: `/api/assessment/diagnostics/${sessionId}/pause`,
    method: 'post',
    data,
    headers: { 'X-Request-Id': requestId, repeatSubmit: false }
  });
export const getFinishCheck = (sessionId: string) =>
  call(() => mock.getDiagnosticFinishCheck(sessionId), {
    url: `/api/assessment/diagnostics/${sessionId}/finish-check`,
    method: 'get'
  });
export const finishDiagnostic = (sessionId: string, data: { expectedSessionVersion: number }, requestId: string) =>
  call(() => mock.finishDiagnostic(sessionId, data), {
    url: `/api/assessment/diagnostics/${sessionId}/finish`,
    method: 'post',
    data,
    headers: { 'X-Request-Id': requestId, repeatSubmit: false }
  });
export const getDiagnosticResultStatus = (sessionId: string) =>
  call(() => mock.getDiagnosticResultStatus(sessionId), {
    url: `/api/assessment/diagnostics/${sessionId}/status`,
    method: 'get'
  });
export const regenerateDiagnosticResult = (sessionId: string, requestId: string) =>
  call(() => mock.regenerateDiagnosticResult(sessionId), {
    url: `/api/assessment/diagnostics/${sessionId}/regenerate-result`,
    method: 'post',
    data: {},
    headers: { 'X-Request-Id': requestId, repeatSubmit: false }
  });
export const getDiagnosticReport = (sessionId: string) =>
  call(() => mock.getDiagnosticReport(sessionId), {
    url: `/api/assessment/diagnostics/${sessionId}/report`,
    method: 'get'
  });
