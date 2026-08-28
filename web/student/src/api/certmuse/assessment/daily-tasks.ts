import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';

export interface DailyTaskLearningContentVo {
  taskId: string;
  phase: 'LEARNING';
  title: string;
  knowledgePoint: string;
  estimatedMinutes: number;
  sections: Array<{
    order: number;
    title: string;
    content: string;
    images: Array<{ url: string; alt: string | null; sortOrder: number }>;
  }>;
  nextAction: 'START_PRACTICE';
}

export interface StartDailyTaskPracticeVo {
  taskId: string;
  sessionId: string;
  nextAction: 'OPEN_PRACTICE_SESSION';
}

export interface DailyTaskSessionVo {
  sessionId: string;
  sessionStatus: 'IN_PROGRESS' | 'ALL_SUBMITTED' | 'COMPLETED';
  taskTitle: string;
  totalCount: number;
  submittedCount: number;
  navigation: Array<{ questionOrder: number; state: 'SUBMITTED_CORRECT' | 'SUBMITTED_INCORRECT' | 'UNANSWERED' }>;
  nextAction: 'CONTINUE_PRACTICE' | 'COMPLETE_TASK' | 'RETURN_TASKS';
}

export interface DailyTaskItemVo {
  sessionId: string;
  questionOrder: number;
  totalCount: number;
  question: {
    questionType: 'CHOICE';
    selectionMode: 'single';
    stem: string;
    options: Array<{ label: string; content: string }>;
    images: Array<{ url: string; alt: string | null; sortOrder: number }>;
  };
  submission: null | {
    selectedOptionLabels: string[];
    correct: boolean;
    correctOptionLabels: string[];
    analysis: string | null;
  };
}

export interface DailyTaskErrorVo {
  errorCode: string;
  retryable: boolean;
  traceId: string | null;
  fieldErrors: Array<{ field: string; code: 'REQUIRED' | 'INVALID_FORMAT' | 'OUT_OF_RANGE'; message: string }>;
}

export const getDailyTaskLearningContent = (taskId: string): AxiosPromise<DailyTaskLearningContentVo> =>
  request({ url: `/api/learning/tasks/${taskId}/learning-content`, method: 'get' });

export const startDailyTaskPractice = (taskId: string, requestId: string): AxiosPromise<StartDailyTaskPracticeVo> =>
  request({
    url: `/api/learning/tasks/${taskId}/start-practice`,
    method: 'post',
    headers: { 'X-Request-Id': requestId, repeatSubmit: false }
  });

export const getDailyTaskSession = (sessionId: string): AxiosPromise<DailyTaskSessionVo> =>
  request({ url: `/api/assessment/daily-tasks/${sessionId}`, method: 'get' });

export const getDailyTaskItem = (sessionId: string, questionOrder: number): AxiosPromise<DailyTaskItemVo> =>
  request({ url: `/api/assessment/daily-tasks/${sessionId}/items/${questionOrder}`, method: 'get' });

export const submitDailyTaskItem = (sessionId: string, questionOrder: number, answer: string[], requestId: string) =>
  request({
    url: `/api/assessment/daily-tasks/${sessionId}/items/${questionOrder}/submit`,
    method: 'post',
    data: { answer: { value: answer } },
    headers: { 'X-Request-Id': requestId, repeatSubmit: false }
  });

export const completeDailyTask = (sessionId: string, requestId: string) =>
  request({
    url: `/api/assessment/daily-tasks/${sessionId}/complete`,
    method: 'post',
    headers: { 'X-Request-Id': requestId, repeatSubmit: false }
  });
