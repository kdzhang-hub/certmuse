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

export type PastPaperQuestionType = 'CHOICE' | 'CASE' | 'ESSAY';

export interface PastPaperAccessVo {
  action: 'START' | 'RESUME' | 'BLOCKED';
  canStart: boolean;
  blockCode: string | null;
}
export interface PastPaperSubjectVo {
  id: string;
  name: string;
}
export interface PastPaperSetupVo {
  certifications: Array<{ id: string; name: string; subjects: PastPaperSubjectVo[] }>;
  goal: { id: string; rowVersion: number; certificationId: string; certificationName: string } | null;
}
export interface PastPaperListVo {
  collectionId: string;
  revisionId: string;
  collectionName: string;
  certificationName: string;
  subjects: PastPaperSubjectVo[];
  questionCount: number;
  totalReportScore: string;
  durationMinutes: number | null;
  questionTypes: PastPaperQuestionType[];
  examYear: number | null;
  examMonth: number | null;
  paperTypeCode: string | null;
  paperTypeName: string | null;
  practiceAccess: PastPaperAccessVo;
  examAccess: PastPaperAccessVo;
}
export interface PastPaperPreviewQuestionVo {
  questionOrder: number;
  questionType: PastPaperQuestionType;
  stem: string;
  options: Array<{ label: string; content: string }>;
  images: Array<{ url: string; alt: string | null; sortOrder: number }>;
}
export interface PastPaperPreviewVo {
  collectionId: string;
  revisionId: string;
  questions: PastPaperPreviewQuestionVo[];
}
export interface PastPaperRevealVo {
  correctOptionLabels: string[];
  analysis: string | null;
  disclosedAt: string;
  profileEvidenceBlockedUntil: string;
}
export interface PastPaperStartVo {
  sessionId: string;
  totalCount: number;
  action: 'START' | 'RESUME';
  answerPath: string;
}

/**
 * U15 practice responses deliberately match the knowledge-practice answering
 * contract so the learner uses one answering interaction for both sources.
 */
export type PastPaperPracticeSessionStatus = 'IN_PROGRESS' | 'COMPLETED';
export interface PastPaperPracticeSessionVo {
  sessionId: string;
  sessionStatus: PastPaperPracticeSessionStatus;
  totalCount: number;
  submittedCount: number;
  navigation: Array<{
    questionOrder: number;
    state: 'SUBMITTED_CORRECT' | 'SUBMITTED_INCORRECT' | 'UNANSWERED';
  }>;
  nextAction: 'CONTINUE_PRACTICE' | 'RETURN_KNOWLEDGE_PRACTICE';
  returnPath: string | null;
}
export interface PastPaperPracticeItemVo {
  sessionId: string;
  questionOrder: number;
  totalCount: number;
  question: {
    questionType: 'CHOICE' | 'CASE' | 'ESSAY';
    stem: string;
    selectionMode: 'single' | 'multiple' | null;
    options: Array<{ label: string; content: string }>;
    images: Array<{ url: string; alt: string | null; sortOrder: number }>;
  };
  submission: null | {
    selectedOptionLabels: string[];
    textAnswer: string | null;
    correct: boolean | null;
    correctOptionLabels: string[];
    analysis: string | null;
    subjectiveGrading: null | {
      state: 'PENDING' | 'PROCESSING' | 'SUCCEEDED' | 'FAILED';
      score: string | null;
      maxScore: string | null;
      scoreRate: string | null;
      feedback: string | null;
      items: Array<{ code: string; scoreRate: string }>;
      errorCode: string | null;
    };
  };
}
export interface SubmitPastPaperPracticeItemVo {
  questionOrder: number;
  submittedCount: number;
  feedback: NonNullable<PastPaperPracticeItemVo['submission']>;
}
export interface CompletePastPaperPracticeVo {
  sessionId: string;
  sessionStatus: 'COMPLETED';
  submittedCount: number;
  returnPath: '/learning/question-bank/past-papers';
}
export interface StartPastPaperPracticeVo {
  sessionId: string;
  totalCount: number;
  answerPath: string;
}
export interface PastPaperPracticeErrorVo {
  errorCode: string;
  retryable: boolean;
  traceId: string | null;
  fieldErrors: Array<{ field: string; code: 'REQUIRED' | 'INVALID_FORMAT' | 'OUT_OF_RANGE'; message: string }>;
  details: { answerPath: string } | null;
}
export interface PastPaperPageVo {
  rows: PastPaperListVo[];
  total: number;
}

export const getPastPaperSetup = (): AxiosPromise<PastPaperSetupVo> =>
  request({ url: '/api/assessment/past-papers/setup', method: 'get' });
export const listPastPapers = (params: {
  certificationId?: string;
  subjectId?: string;
  keyword?: string;
  pageNum?: number;
  pageSize?: number;
}): AxiosPromise<PastPaperPageVo> => request({ url: '/api/assessment/past-papers', method: 'get', params });
export const getPastPaperPreview = (collectionId: string): AxiosPromise<PastPaperPreviewVo> =>
  request({ url: `/api/assessment/past-papers/${collectionId}/preview`, method: 'get' });
export const revealPastPaperAnswer = (
  collectionId: string,
  questionOrder: number,
  requestId = crypto.randomUUID().toLowerCase()
): AxiosPromise<PastPaperRevealVo> =>
  request({
    url: `/api/assessment/past-papers/${collectionId}/items/${questionOrder}/answer-reveal`,
    method: 'post',
    headers: { 'X-Request-Id': requestId, repeatSubmit: false }
  });
export function startPastPaper(
  collectionId: string,
  mode: 'practice',
  data: { expectedRevisionId: string; expectedGoalVersion: number },
  requestId?: string
): AxiosPromise<StartPastPaperPracticeVo>;
export function startPastPaper(
  collectionId: string,
  mode: 'exam',
  data: { expectedRevisionId: string; expectedGoalVersion: number },
  requestId?: string
): AxiosPromise<PastPaperStartVo>;
export function startPastPaper(
  collectionId: string,
  mode: 'practice' | 'exam',
  data: { expectedRevisionId: string; expectedGoalVersion: number },
  requestId?: string
): AxiosPromise<StartPastPaperPracticeVo | PastPaperStartVo>;
export function startPastPaper(
  collectionId: string,
  mode: 'practice' | 'exam',
  data: { expectedRevisionId: string; expectedGoalVersion: number },
  requestId = crypto.randomUUID().toLowerCase()
): AxiosPromise<StartPastPaperPracticeVo | PastPaperStartVo> {
  return request({
    url: `/api/assessment/past-papers/${collectionId}/${mode}-sessions`,
    method: 'post',
    data,
    headers: { 'X-Request-Id': requestId, repeatSubmit: false }
  });
}

const practiceBase = (sessionId: string) => `/api/assessment/past-papers/practice-sessions/${sessionId}`;
export const getPastPaperPracticeSession = (sessionId: string): AxiosPromise<PastPaperPracticeSessionVo> =>
  request({ url: practiceBase(sessionId), method: 'get' });
export const getPastPaperPracticeItem = (
  sessionId: string,
  questionOrder: number
): AxiosPromise<PastPaperPracticeItemVo> => request({ url: `${practiceBase(sessionId)}/items/${questionOrder}`, method: 'get' });
export const submitPastPaperPracticeItem = (
  sessionId: string,
  questionOrder: number,
  data: { answer: { value?: string[]; text?: string; questionType?: 'CASE' | 'ESSAY'; schemaVersion?: string } },
  requestId = crypto.randomUUID().toLowerCase()
): AxiosPromise<SubmitPastPaperPracticeItemVo> =>
  request({
    url: `${practiceBase(sessionId)}/items/${questionOrder}/submit`,
    method: 'post',
    data,
    headers: { 'X-Request-Id': requestId, repeatSubmit: false }
  });
export const completePastPaperPractice = (
  sessionId: string,
  requestId = crypto.randomUUID().toLowerCase()
): AxiosPromise<CompletePastPaperPracticeVo> =>
  request({
    url: `${practiceBase(sessionId)}/complete`,
    method: 'post',
    headers: { 'X-Request-Id': requestId, repeatSubmit: false }
  });

const examBase = (sessionId: string) => `/api/assessment/past-papers/exam-sessions/${sessionId}`;
const write = (url: string, method: 'post' | 'put', data?: unknown) =>
  request({ url, method, data, headers: { 'X-Request-Id': crypto.randomUUID().toLowerCase(), repeatSubmit: false } });
export const getPastPaperExamSession = (sessionId: string): AxiosPromise<FormalExamSessionVo> =>
  request({ url: examBase(sessionId), method: 'get' });
export const getPastPaperExamItem = (sessionId: string, order: number): AxiosPromise<FormalExamItemVo> =>
  request({ url: `${examBase(sessionId)}/items/${order}`, method: 'get' });
export const savePastPaperExamDraft = (
  sessionId: string,
  order: number,
  data: FormalExamDraftRequest
): AxiosPromise<FormalExamSessionVo> => write(`${examBase(sessionId)}/items/${order}/draft`, 'put', data);
export const sendPastPaperExamTimer = (
  sessionId: string,
  data: FormalExamTimerRequest
): AxiosPromise<FormalExamTimerEventVo> => write(`${examBase(sessionId)}/timer-events`, 'post', data);
export const pausePastPaperExam = (
  sessionId: string,
  data: { currentQuestionOrder: number; expectedSessionVersion: number }
): AxiosPromise<FormalExamSessionVo> => write(`${examBase(sessionId)}/pause`, 'post', data);
export const getPastPaperExamFinishCheck = (sessionId: string): AxiosPromise<FormalExamFinishCheckVo> =>
  request({ url: `${examBase(sessionId)}/finish-check`, method: 'get' });
export const finishPastPaperExam = (
  sessionId: string,
  data: { expectedSessionVersion: number }
): AxiosPromise<FormalExamFinishVo> => write(`${examBase(sessionId)}/finish`, 'post', data);
export const getPastPaperExamStatus = (sessionId: string): AxiosPromise<FormalExamStatusVo> =>
  request({ url: `${examBase(sessionId)}/status`, method: 'get' });
export const regeneratePastPaperExamResult = (sessionId: string): AxiosPromise<FormalExamStatusVo> =>
  write(`${examBase(sessionId)}/regenerate-result`, 'post', {});
export const getPastPaperExamResult = (sessionId: string): AxiosPromise<FormalExamResultVo> =>
  request({ url: `${examBase(sessionId)}/result`, method: 'get' });
