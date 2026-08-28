import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';

export type HistoryCorrectionStatus = 'NONE' | 'PENDING_CORRECTION' | 'CORRECTED';
export type HistoryAttemptResult = 'CORRECT' | 'INCORRECT' | 'SKIPPED' | 'PENDING_SCORING';
export type HistoryQuestionType = 'CHOICE' | 'CASE' | 'ESSAY';
export type HistoryDifficulty = 'EASY' | 'MEDIUM' | 'HARD';

export interface HistoryPage<T> {
  rows: T[];
  total: number;
}

export interface HistoryGoal {
  goalId: string;
  certificationName: string;
  syllabusVersionName: string;
  targetExamDate: string | null;
  status: 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'CANCELLED';
  isCurrent: boolean;
  lastRecordAt: string;
}

export interface HistoryLookup {
  id: string;
  name: string;
}

export interface HistoryTaskQuery {
  goalId?: string;
  keyword?: string;
  completedFrom?: string;
  completedTo?: string;
  subjectId?: string;
  knowledgePointId?: string;
  pageNum?: number;
  pageSize?: number;
}

export interface HistoryTaskRow {
  taskId: string;
  title: string;
  knowledgePoint: HistoryLookup;
  subject: HistoryLookup;
  questionCount: number;
  correctCount: number;
  incorrectCount: number;
  skippedCount: number;
  completedAt: string;
}

export interface HistoryTaskDetail {
  taskId: string;
  title: string;
  completedAt: string;
  knowledgePoint: HistoryLookup;
  summary: Pick<HistoryTaskRow, 'questionCount' | 'correctCount' | 'incorrectCount' | 'skippedCount'>;
  questions: Array<{
    questionId: string;
    questionOrder: number;
    questionType: 'CHOICE';
    difficulty: HistoryDifficulty;
    stem: string;
    selectionMode: 'single' | 'multiple';
    options: HistoryOption[];
    images: HistoryImage[];
    knowledgePoints: HistoryLookup[];
    submission: {
      selectedOptionLabels: string[];
      unanswered: boolean;
      submittedAt: string | null;
    };
    result: {
      correct: boolean | null;
      score: string | null;
      maxScore: string | null;
      scoreRate: number | null;
      correctOptionLabels: string[];
      analysis: string | null;
    };
  }>;
}

export interface HistoryQuestionQuery {
  goalId?: string;
  keyword?: string;
  questionType?: HistoryQuestionType;
  difficulty?: HistoryDifficulty;
  knowledgePointId?: string;
  correctionStatus?: HistoryCorrectionStatus;
  lastAnsweredFrom?: string;
  lastAnsweredTo?: string;
  pageNum?: number;
  pageSize?: number;
}

export interface HistoryQuestionRow {
  questionId: string;
  stemPreview: string;
  questionType: string;
  difficulty: string;
  knowledgePoints: HistoryLookup[];
  attemptCount: number;
  lastAnsweredAt: string;
  lastAttempt: {
    sessionSource: string;
    attemptResult: HistoryAttemptResult;
    scoreRate: number | null;
  };
  correctionStatus: HistoryCorrectionStatus;
}

export interface HistoryQuestionDetail {
  questionId: string;
  stemPreview: string;
  attemptCount: number;
  history: Array<{
    attemptId: string;
    sessionId: string;
    sessionSource: string;
    answeredAt: string;
    stemPreview: string;
    questionType: string;
    difficulty: string;
    knowledgePoints: HistoryLookup[];
    attemptResult: HistoryAttemptResult;
    answerSummary: string;
    score: string | null;
    maxScore: string | null;
    scoreRate: number | null;
    skipped: boolean;
  }>;
  correction: {
    status: HistoryCorrectionStatus;
    wrongCount: number;
    skipCount: number;
    events: Array<{
      occurredAt: string;
      eventType: 'WRONG_ANSWER' | 'SKIPPED' | 'CORRECTED' | 'REOPENED';
      sessionSource: string;
    }>;
  };
}

export type HistoryPracticeType = 'KNOWLEDGE_PRACTICE' | 'PAST_PAPER_PRACTICE';

export interface HistoryPracticeQuery {
  goalId?: string;
  practiceType?: HistoryPracticeType;
  completedFrom?: string;
  completedTo?: string;
  pageNum?: number;
  pageSize?: number;
}

export interface HistoryPracticeSummary {
  questionCount: number;
  answeredCount: number;
  correctCount: number;
  incorrectCount: number;
  unansweredCount: number;
}

export interface HistoryPracticeRow extends HistoryPracticeSummary {
  sessionId: string;
  practiceType: HistoryPracticeType;
  title: string;
  subject: HistoryLookup;
  knowledgePoints: HistoryLookup[];
  completedAt: string;
}

export interface HistoryOption {
  label: string;
  content: string;
}

export interface HistoryImage {
  sortOrder: number;
  url: string;
  altText: string | null;
}

export interface HistoryPracticeDetail {
  sessionId: string;
  practiceType: HistoryPracticeType;
  title: string;
  subject: HistoryLookup;
  startedAt: string | null;
  completedAt: string;
  summary: HistoryPracticeSummary;
  questions: Array<{
    questionId: string;
    questionOrder: number;
    questionType: HistoryQuestionType;
    difficulty: HistoryDifficulty;
    stem: string;
    selectionMode: 'single' | 'multiple' | null;
    options: HistoryOption[];
    images: HistoryImage[];
    knowledgePoints: HistoryLookup[];
    submission: null | {
      selectedOptionLabels: string[];
      textAnswer: string | null;
      unanswered: boolean;
      submittedAt: string | null;
    };
    result: {
      correct: boolean | null;
      score: string | null;
      maxScore: string | null;
      scoreRate: number | null;
      correctOptionLabels: string[];
      referenceAnswer: string | null;
      analysis: string | null;
      gradingStatus: 'graded' | 'pending' | 'processing' | 'failed' | null;
      gradingSource: string | null;
      gradingRevisionNo: number | null;
      aiFeedback: string | null;
      gradingItems: Array<{
        code: string;
        description: string;
        weight: number;
        scoreRate: number;
      }>;
    };
  }>;
}

export type HistoryExamType = 'INITIAL_DIAGNOSIS' | 'PAST_PAPER' | 'SIMULATION';
export type HistoryReportStatus = 'AVAILABLE' | 'PROCESSING' | 'FAILED' | 'UNAVAILABLE';

export interface HistoryExamQuery {
  goalId?: string;
  examType?: HistoryExamType;
  completedFrom?: string;
  completedTo?: string;
  pageNum?: number;
  pageSize?: number;
}

export interface HistoryExamResult {
  reportStatus: HistoryReportStatus;
  score: string | null;
  maxScore: string | null;
  scoreRate: number | null;
}

export interface HistoryExamRow {
  sessionId: string;
  examType: HistoryExamType;
  title: string;
  subject: HistoryLookup | null;
  completedAt: string;
  result: HistoryExamResult;
  durationSeconds: number | null;
  durationLimitSeconds: number | null;
  durationStatus: 'AVAILABLE' | 'UNAVAILABLE';
}

export interface HistoryExamDetail extends HistoryExamRow {
  questions: Array<{
    questionId: string;
    questionOrder: number;
    questionType: HistoryQuestionType;
    difficulty: HistoryDifficulty;
    stem: string;
    options: HistoryOption[];
    images: HistoryImage[];
    submission: {
      selectedOptionLabels: string[];
      textAnswer: string | null;
      unanswered: boolean;
      submittedAt: string | null;
    };
    result: {
      correct: boolean | null;
      score: string | null;
      maxScore: string | null;
      scoreRate: number | null;
      correctOptionLabels: string[];
      referenceAnswer: string | null;
      analysis: string | null;
      gradingStatus: 'graded' | 'pending' | 'processing' | 'failed' | null;
      gradingSource: string | null;
      gradingRevisionNo: number | null;
      aiFeedback: string | null;
      gradingItems: Array<{
        code: string;
        description: string;
        weight: number;
        scoreRate: number;
      }>;
    };
  }>;
}

export const getHistoryGoals = (): AxiosPromise<{ rows: HistoryGoal[] }> =>
  request({ url: '/api/learning/history/goals', method: 'get' });

export const listHistoryTasks = (params: HistoryTaskQuery): AxiosPromise<HistoryPage<HistoryTaskRow>> =>
  request({ url: '/api/learning/history/tasks', method: 'get', params });

export const getHistoryTask = (taskId: string, goalId?: string): AxiosPromise<HistoryTaskDetail> =>
  request({ url: `/api/learning/history/tasks/${taskId}`, method: 'get', params: { goalId } });

export const listHistoryQuestions = (params: HistoryQuestionQuery): AxiosPromise<HistoryPage<HistoryQuestionRow>> =>
  request({ url: '/api/learning/history/questions', method: 'get', params });

export const getHistoryQuestion = (questionId: string, goalId?: string): AxiosPromise<HistoryQuestionDetail> =>
  request({ url: `/api/learning/history/questions/${questionId}`, method: 'get', params: { goalId } });

export const listHistoryPractices = (params: HistoryPracticeQuery): AxiosPromise<HistoryPage<HistoryPracticeRow>> =>
  request({ url: '/api/learning/history/practices', method: 'get', params });

export const getHistoryPractice = (sessionId: string, goalId?: string): AxiosPromise<HistoryPracticeDetail> =>
  request({ url: `/api/learning/history/practices/${sessionId}`, method: 'get', params: { goalId } });

export const listHistoryExams = (params: HistoryExamQuery): AxiosPromise<HistoryPage<HistoryExamRow>> =>
  request({ url: '/api/learning/history/exams', method: 'get', params });

export const getHistoryExam = (sessionId: string, goalId?: string): AxiosPromise<HistoryExamDetail> =>
  request({ url: `/api/learning/history/exams/${sessionId}`, method: 'get', params: { goalId } });
