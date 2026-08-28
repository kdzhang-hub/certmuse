import type { PageResult } from '@/api/types';
import * as mock from '@/api/student/mock';
import type { StudentMistakeDetailVO, StudentMistakeVO, StudentQuestion } from '@/api/student/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';

export type MistakeReviewStatus = 'PENDING_CORRECTION' | 'CORRECTED';
export type MistakeSource = 'INITIAL_DIAGNOSIS' | 'DAILY_TASK' | 'SELF_PRACTICE' | 'PAST_PAPER' | 'SIMULATION';

export interface MistakeReviewQuery {
  pageNum?: number;
  pageSize?: number;
  keyword?: string;
  status?: MistakeReviewStatus;
  knowledgePointId?: string;
  source?: MistakeSource;
  minWrongCount?: 2 | 3;
}

export interface MistakeReviewSummary {
  questionId: string;
  stemPreview: string;
  knowledgePoints: Array<{ id: string; name: string }>;
  status: MistakeReviewStatus;
  wrongCount: number;
  skipCount: number;
  sources: MistakeSource[];
  lastWrongAt: string;
  canCorrect: boolean;
  blockReason: 'UNSUPPORTED_QUESTION_TYPE' | 'QUESTION_UNAVAILABLE' | null;
}

export interface MistakeReviewDetail {
  questionId: string;
  stemPreview: string;
  status: MistakeReviewStatus;
  wrongCount: number;
  skipCount: number;
  sources: MistakeSource[];
  lastWrongAt: string;
  question: {
    questionType: 'CHOICE';
    stem: string;
    selectionMode: 'single' | 'multiple';
    options: Array<{ label: string; content: string }>;
    images: Array<{ url: string; alt: string | null; sortOrder: number }>;
  } | null;
  originalAnswer: string[];
  correctAnswer: string[];
  analysis: string | null;
  history: Array<{ attemptId: string; occurredAt: string; source: MistakeSource; answer: string[]; skipped: boolean }>;
  canCorrect: boolean;
  blockReason: 'UNSUPPORTED_QUESTION_TYPE' | 'QUESTION_UNAVAILABLE' | null;
}

export interface CreateMistakeCorrectionSessionRequest {
  questionIds: string[];
}

export interface CreateMistakeCorrectionSessionVo {
  sessionId: string;
  totalCount: number;
  answerPath: string;
}

export interface MistakeCorrectionSessionVo {
  sessionId: string;
  sessionStatus: 'IN_PROGRESS' | 'COMPLETED';
  totalCount: number;
  submittedCount: number;
  navigation: Array<{ questionOrder: number; state: 'SUBMITTED_CORRECT' | 'SUBMITTED_INCORRECT' | 'UNANSWERED' }>;
  nextAction: 'CONTINUE_CORRECTION' | 'RETURN_MISTAKES';
  returnPath: string | null;
}

export interface MistakeCorrectionItemVo {
  sessionId: string;
  questionOrder: number;
  totalCount: number;
  question: NonNullable<MistakeReviewDetail['question']>;
  submission: MistakeCorrectionSubmissionVo | null;
}

export interface MistakeCorrectionSubmissionVo {
  selectedOptionLabels: string[];
  correct: boolean;
  correctOptionLabels: string[];
  analysis: string | null;
}

export interface SubmitMistakeCorrectionItemRequest {
  answer: { value: string[] };
}

export interface SubmitMistakeCorrectionItemVo {
  questionOrder: number;
  submittedCount: number;
  feedback: MistakeCorrectionSubmissionVo & { mistakeStatus: MistakeReviewStatus };
}

export interface CompleteMistakeCorrectionSessionVo {
  sessionId: string;
  sessionStatus: 'COMPLETED';
  submittedCount: number;
  returnPath: '/learning/mistakes';
}

const inMockMode = () => import.meta.env.VITE_APP_STUDENT_MOCK === 'true';
const sourceByLabel: Record<string, MistakeSource> = {
  '首次诊断': 'INITIAL_DIAGNOSIS',
  '每日任务': 'DAILY_TASK',
  '自主练习': 'SELF_PRACTICE',
  '知识点练习': 'SELF_PRACTICE',
  '历年真题': 'PAST_PAPER'
};
const sourceLabel: Record<MistakeSource, string> = {
  INITIAL_DIAGNOSIS: '首次诊断',
  DAILY_TASK: '每日任务',
  SELF_PRACTICE: '自主练习',
  PAST_PAPER: '历年真题',
  SIMULATION: '模拟考试'
};
const statusByMock = { '待订正': 'PENDING_CORRECTION', '已订正': 'CORRECTED' } as const;
const statusToMock = { PENDING_CORRECTION: '待订正', CORRECTED: '已订正' } as const;
type MockCorrectionSubmission = MistakeCorrectionSubmissionVo;
interface MockCorrectionSession {
  questions: StudentQuestion[];
  submissions: Map<number, MockCorrectionSubmission>;
  completed: boolean;
}

const mockCorrectionSessions = new Map<string, MockCorrectionSession>();

const toSummary = (item: StudentMistakeVO): MistakeReviewSummary => ({
  questionId: item.questionId,
  stemPreview: item.title,
  knowledgePoints: [{ id: item.knowledgePoint, name: item.knowledgePoint }],
  status: statusByMock[item.status as keyof typeof statusByMock],
  wrongCount: item.wrongCount,
  skipCount: item.skipCount,
  sources: item.sources.map((source: string) => sourceByLabel[source] ?? 'SELF_PRACTICE'),
  lastWrongAt: item.lastWrongAt,
  canCorrect: true,
  blockReason: null
});

const mockList = async (query?: MistakeReviewQuery) => {
  const response = await mock.listMistakes({
    pageNum: query?.pageNum,
    pageSize: query?.pageSize,
    keyword: query?.keyword,
    status: query?.status ? statusToMock[query.status] : undefined,
    source: query?.source ? sourceLabel[query.source] : undefined,
    minWrongCount: query?.minWrongCount
  });
  return { ...response, data: response.data && { total: response.data.total, rows: response.data.rows.map(toSummary) } };
};

/** 查询当前学习目标下按题目聚合的错题。 */
export const listMistakeReviews = (query?: MistakeReviewQuery): AxiosPromise<PageResult<MistakeReviewSummary>> =>
  inMockMode()
    ? mockList(query)
    : request({ url: '/api/learning/mistakes', method: 'get', params: query });

/** 查询题目级错题详情及历史作答。 */
export const getMistakeReview = async (questionId: string): AxiosPromise<MistakeReviewDetail | undefined> => {
  if (!inMockMode()) return request({ url: `/api/learning/mistakes/${questionId}`, method: 'get' });
  const page = await mock.listMistakes({ pageNum: 1, pageSize: 100 });
  const item = page.data?.rows.find(row => row.questionId === questionId);
  if (!item) return { code: 200, msg: '操作成功', data: undefined };
  const detail = await mock.getMistake(item.id);
  const source = detail.data as StudentMistakeDetailVO | undefined;
  if (!source) return { ...detail, data: undefined };
  return {
    ...detail,
    data: {
      ...toSummary(source),
      question: source.question.type === 'CHOICE'
        ? {
            questionType: 'CHOICE' as const,
            stem: source.question.stem,
            selectionMode: source.question.selectionMode ?? 'single',
            options: (source.question.options ?? []).map(option => ({ label: option.key, content: option.content })),
            images: []
          }
        : null,
      originalAnswer: source.originalAnswer,
      correctAnswer: source.question.mockAnswer ?? [],
      analysis: source.question.analysis ?? null,
      history: source.history.map((history, index) => ({
        attemptId: `${source.questionId}-${index + 1}`,
        occurredAt: history.occurredAt,
        source: sourceByLabel[history.source] ?? 'SELF_PRACTICE',
        answer: history.skipped ? [] : source.originalAnswer,
        skipped: history.skipped
      }))
    } satisfies MistakeReviewDetail
  };
};

/** 冻结用户选择的原题，创建一份错题订正会话。 */
export const createMistakeCorrectionSession = async (
  data: CreateMistakeCorrectionSessionRequest,
  requestId: string = crypto.randomUUID()
): AxiosPromise<CreateMistakeCorrectionSessionVo> => {
  if (!inMockMode()) {
    return request({
      url: '/api/learning/mistakes/correction-sessions',
      method: 'post',
      data,
      headers: { 'X-Request-Id': requestId, repeatSubmit: false }
    });
  }
  const list = await mock.listMistakes({ pageNum: 1, pageSize: 100 });
  const ids = data.questionIds.flatMap(questionId => list.data?.rows.filter(item => item.questionId === questionId).map(item => item.id) ?? []);
  const response = await mock.startMistakeCorrection(ids);
  const session = (await mock.getSession('correction')).data;
  if (!response.data || !session) return { ...response, data: undefined };
  mockCorrectionSessions.set(response.data.sessionId, {
    questions: session.questions,
    submissions: new Map(),
    completed: false
  });
  return {
    ...response,
    data: {
      sessionId: response.data.sessionId,
      totalCount: response.data.questionCount,
      answerPath: `/learning/session/correction?sessionId=${response.data.sessionId}`
    } satisfies CreateMistakeCorrectionSessionVo
  };
};

/** 读取订正会话答题卡。 */
export const getMistakeCorrectionSession = async (sessionId: string): AxiosPromise<MistakeCorrectionSessionVo | undefined> => {
  if (!inMockMode()) return request({ url: `/api/learning/mistakes/correction-sessions/${sessionId}`, method: 'get' });
  const session = mockCorrectionSessions.get(sessionId);
  if (!session) return { code: 200, msg: '操作成功', data: undefined };
  const navigation = session.questions.map((_, index) => {
    const submission = session.submissions.get(index + 1);
    return {
      questionOrder: index + 1,
      state: submission ? (submission.correct ? 'SUBMITTED_CORRECT' : 'SUBMITTED_INCORRECT') : 'UNANSWERED'
    } as const;
  });
  return {
    code: 200,
    msg: '操作成功',
    data: {
      sessionId,
      sessionStatus: session.completed ? 'COMPLETED' : 'IN_PROGRESS',
      totalCount: session.questions.length,
      submittedCount: session.submissions.size,
      navigation,
      nextAction: session.completed ? 'RETURN_MISTAKES' : 'CONTINUE_CORRECTION',
      returnPath: session.completed ? '/learning/mistakes' : null
    } satisfies MistakeCorrectionSessionVo
  };
};

/** 读取订正会话的冻结题目。 */
export const getMistakeCorrectionItem = async (
  sessionId: string,
  questionOrder: number
): AxiosPromise<MistakeCorrectionItemVo | undefined> => {
  if (!inMockMode()) return request({ url: `/api/learning/mistakes/correction-sessions/${sessionId}/items/${questionOrder}`, method: 'get' });
  const session = mockCorrectionSessions.get(sessionId);
  const question = session?.questions[questionOrder - 1];
  if (!question || question.type !== 'CHOICE') return { code: 200, msg: '操作成功', data: undefined };
  return {
    code: 200,
    msg: '操作成功',
    data: {
      sessionId,
      questionOrder,
      totalCount: session.questions.length,
      question: {
        questionType: 'CHOICE' as const,
        stem: question.stem,
        selectionMode: question.selectionMode ?? 'single',
        options: (question.options ?? []).map(option => ({ label: option.key, content: option.content })),
        images: []
      },
      submission: session.submissions.get(questionOrder) ?? null
    } satisfies MistakeCorrectionItemVo
  };
};

/** 提交一题订正答案；答对后服务端将错误记录改为已订正。 */
export const submitMistakeCorrectionItem = async (
  sessionId: string,
  questionOrder: number,
  data: SubmitMistakeCorrectionItemRequest,
  requestId: string = crypto.randomUUID()
): AxiosPromise<SubmitMistakeCorrectionItemVo> => {
  if (!inMockMode()) {
    return request({
      url: `/api/learning/mistakes/correction-sessions/${sessionId}/items/${questionOrder}/submit`,
      method: 'post',
      data,
      headers: { 'X-Request-Id': requestId, repeatSubmit: false }
    });
  }
  const session = mockCorrectionSessions.get(sessionId);
  const question = session?.questions[questionOrder - 1];
  if (!question) return { code: 200, msg: '操作成功', data: undefined };
  if (session.submissions.has(questionOrder)) throw new Error('该题已经提交。');
  const response = await mock.submitSessionItem({ sessionId, kind: 'correction', questionId: question.id, answer: data.answer.value });
  const feedback = response.data;
  if (!feedback) return { ...response, data: undefined };
  const correctionFeedback: MockCorrectionSubmission = {
    selectedOptionLabels: data.answer.value,
    correct: feedback.correct === true,
    correctOptionLabels: question.mockAnswer ?? [],
    analysis: feedback.analysis ?? null
  };
  session.submissions.set(questionOrder, correctionFeedback);
  return {
    ...response,
    data: {
      questionOrder,
      submittedCount: session.submissions.size,
      feedback: { ...correctionFeedback, mistakeStatus: feedback.correct ? 'CORRECTED' : 'PENDING_CORRECTION' }
    } satisfies SubmitMistakeCorrectionItemVo
  };
};

/** 结束订正会话；未提交题不改变错题状态。 */
export const completeMistakeCorrectionSession = (
  sessionId: string,
  requestId: string = crypto.randomUUID()
): AxiosPromise<CompleteMistakeCorrectionSessionVo> => {
  if (inMockMode()) {
    const session = mockCorrectionSessions.get(sessionId);
    if (session) session.completed = true;
    return Promise.resolve({
        code: 200,
        msg: '操作成功',
        data: { sessionId, sessionStatus: 'COMPLETED', submittedCount: session?.submissions.size ?? 0, returnPath: '/learning/mistakes' }
      });
  }
  return request({
    url: `/api/learning/mistakes/correction-sessions/${sessionId}/complete`,
    method: 'post',
    headers: { 'X-Request-Id': requestId, repeatSubmit: false }
  });
};
