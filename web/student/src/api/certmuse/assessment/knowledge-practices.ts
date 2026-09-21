import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';

export interface KnowledgePracticeMasteryVo {
  currentDirectAbility: number | null;
  profileStatus:
    | 'unassessed'
    | 'provisional'
    | 'urgent'
    | 'weak'
    | 'learning'
    | 'proficient'
    | 'mastery_candidate'
    | 'mastered'
    | 'needs_retest'
    | 'pending_verification'
    | 'needs_review'
    | 'expired';
  confidenceLevel: 'unassessed' | 'low' | 'medium' | 'high' | 'expired';
}

export interface KnowledgePracticeNodeVo {
  id: string;
  parentId: string | null;
  examSubjectId: string;
  syllabusNumber: string;
  syllabusTitle: string;
  treeDepth: number;
  sortOrder: number;
  importance: 1 | 2 | 3;
  questionCount: number;
  mastery: KnowledgePracticeMasteryVo;
  children: KnowledgePracticeNodeVo[];
}

export interface KnowledgePracticeSetupVo {
  goal: {
    id: string;
    certificationName: string;
    syllabusVersionName: string;
    version: number;
  };
  subjects: Array<{
    id: string;
    subjectName: string;
    nodes: KnowledgePracticeNodeVo[];
  }>;
}

export interface StartKnowledgePracticeRequest {
  knowledgePointId: string;
  expectedGoalVersion: number;
}

export interface StartKnowledgePracticeVo {
  sessionId: string;
  totalCount: number;
  answerPath: string;
}

export interface KnowledgePracticeErrorVo {
  errorCode: string;
  retryable: boolean;
  traceId: string | null;
  fieldErrors: Array<{ field: string; code: 'REQUIRED' | 'INVALID_FORMAT' | 'OUT_OF_RANGE'; message: string }>;
  details: { answerPath: string } | null;
}

export type KnowledgePracticeSessionStatus = 'IN_PROGRESS' | 'COMPLETED';

export interface KnowledgePracticeNavigationItemVo {
  questionOrder: number;
  state: 'SUBMITTED_CORRECT' | 'SUBMITTED_INCORRECT' | 'UNANSWERED';
}

export interface KnowledgePracticeAnswerSessionVo {
  sessionId: string;
  sessionStatus: KnowledgePracticeSessionStatus;
  totalCount: number;
  submittedCount: number;
  navigation: KnowledgePracticeNavigationItemVo[];
  nextAction: 'CONTINUE_PRACTICE' | 'RETURN_KNOWLEDGE_PRACTICE';
  returnPath: string | null;
}

export interface KnowledgePracticeChoiceOptionVo {
  label: string;
  content: string;
}

export interface KnowledgePracticeItemVo {
  sessionId: string;
  questionOrder: number;
  totalCount: number;
  question: {
    questionType: 'CHOICE';
    stem: string;
    selectionMode: 'single' | 'multiple';
    options: KnowledgePracticeChoiceOptionVo[];
    images: Array<{ url: string; alt: string | null; sortOrder: number }>;
  };
  submission: null | {
    selectedOptionLabels: string[];
    correct: boolean;
    correctOptionLabels: string[];
    analysis: string | null;
  };
}

export interface SubmitKnowledgePracticeItemRequest {
  answer: { value: string[] };
}

export interface SubmitKnowledgePracticeItemVo {
  questionOrder: number;
  submittedCount: number;
  feedback: NonNullable<KnowledgePracticeItemVo['submission']>;
}

export interface CompleteKnowledgePracticeVo {
  sessionId: string;
  sessionStatus: 'COMPLETED';
  submittedCount: number;
  returnPath: '/learning/question-bank/knowledge-practice';
}

export const getKnowledgePracticeSetup = (): AxiosPromise<KnowledgePracticeSetupVo> => {
  return request({ url: '/api/assessment/knowledge-practices/setup', method: 'get' });
};

export const startKnowledgePractice = (
  data: StartKnowledgePracticeRequest,
  requestId: string
): AxiosPromise<StartKnowledgePracticeVo> => {
  return request({
    url: '/api/assessment/knowledge-practices',
    method: 'post',
    data,
    headers: { 'X-Request-Id': requestId, repeatSubmit: false }
  });
};

export const getKnowledgePracticeAnswerSession = (
  sessionId: string
): AxiosPromise<KnowledgePracticeAnswerSessionVo> => {
  return request({ url: `/api/assessment/knowledge-practices/${sessionId}`, method: 'get' });
};

export const getKnowledgePracticeItem = (
  sessionId: string,
  questionOrder: number
): AxiosPromise<KnowledgePracticeItemVo> => {
  return request({ url: `/api/assessment/knowledge-practices/${sessionId}/items/${questionOrder}`, method: 'get' });
};

export const submitKnowledgePracticeItem = (
  sessionId: string,
  questionOrder: number,
  data: SubmitKnowledgePracticeItemRequest,
  requestId: string
): AxiosPromise<SubmitKnowledgePracticeItemVo> => {
  return request({
    url: `/api/assessment/knowledge-practices/${sessionId}/items/${questionOrder}/submit`,
    method: 'post',
    data,
    headers: { 'X-Request-Id': requestId, repeatSubmit: false }
  });
};

export const completeKnowledgePractice = (
  sessionId: string,
  requestId: string
): AxiosPromise<CompleteKnowledgePracticeVo> => {
  return request({
    url: `/api/assessment/knowledge-practices/${sessionId}/complete`,
    method: 'post',
    headers: { 'X-Request-Id': requestId, repeatSubmit: false }
  });
};
