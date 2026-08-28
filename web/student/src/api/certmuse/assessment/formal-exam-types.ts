export type FormalQuestionType = 'CHOICE' | 'CASE' | 'ESSAY';
export interface FormalExamSessionVo {
  sessionId: string;
  examKind: 'PAST_PAPER_EXAM' | 'SIMULATION';
  title: string;
  status: string;
  sessionVersion: number;
  currentQuestionOrder: number;
  totalCount: number;
  answeredCount: number;
  unansweredCount: number;
  estimatedDurationSeconds: number;
  effectiveElapsedSeconds: number;
  serverTime: string;
  lease: { active: boolean; questionOrder: number | null; leaseId: string | null; lastHeartbeatTime: string | null };
  navigation: Array<{ questionOrder: number; state: 'CURRENT' | 'ANSWERED' | 'UNANSWERED' }>;
  returnPath: string;
}
export interface FormalExamItemVo {
  questionOrder: number;
  totalCount: number;
  questionType: FormalQuestionType;
  stem: string;
  selectionMode: 'single' | null;
  options: Array<{ label: string; content: string }>;
  images: Array<{ sortOrder: number; url: string; alt: string | null }>;
  choiceValue: string[];
  textValue: string | null;
  sessionVersion: number;
}
export interface FormalExamTimerEventVo {
  eventAccepted: boolean;
  leaseId: string | null;
  questionOrder: number;
  timerState: 'RUNNING' | 'STOPPED' | 'REPLACED';
  estimatedDurationSeconds: number;
  effectiveElapsedSeconds: number;
  serverTime: string;
}
export interface FormalExamFinishCheckVo {
  sessionVersion: number;
  answeredCount: number;
  unansweredCount: number;
  totalCount: number;
  canFinish: boolean;
}
export interface FormalExamFinishVo {
  sessionId: string;
  status: 'PROCESSING';
  action: 'WAIT_PROCESSING';
  resultPath: string;
}
export interface FormalExamStatusVo {
  sessionId: string;
  status: 'PROCESSING' | 'COMPLETED' | 'FAILED';
  action: 'WAIT_PROCESSING' | 'VIEW_RESULT' | 'RETRY_RESULT';
  stages: Array<{
    code: 'SUBMITTED' | 'SCORING' | 'REPORT_GENERATING' | 'PROFILE_UPDATING' | 'COMPLETED';
    state: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  }>;
  failure: { message: string; traceId: string | null; retryAvailable: boolean } | null;
}
export interface FormalExamResultVo {
  sessionId: string;
  examKind: 'PAST_PAPER_EXAM' | 'SIMULATION';
  title: string;
  status: 'COMPLETED';
  score: string;
  maxScore: string;
  usedSeconds: number;
  correctCount: number;
  wrongCount: number;
  unansweredCount: number;
  questions: Array<{
    questionOrder: number;
    questionType: FormalQuestionType;
    stem: string;
    options: Array<{ label: string; content: string }>;
    images: Array<{ sortOrder: number; url: string; alt: string | null }>;
    selectedOptionLabels: string[];
    textAnswer: string | null;
    unanswered: boolean;
    correct: boolean | null;
    score: string;
    maxScore: string;
    correctOptionLabels: string[];
    referenceAnswer: string | null;
    analysis: string | null;
    profileApplied: boolean;
    gradingSource: string;
    gradingRevisionNo: number;
  }>;
}
export interface FormalExamDraftRequest {
  answer: { choiceValue: string[] | null; textValue: string | null };
  expectedSessionVersion: number;
  currentQuestionOrder: number;
}
export interface FormalExamTimerRequest {
  eventType: 'ENTER' | 'HEARTBEAT' | 'HIDDEN' | 'LEAVE';
  questionOrder: number;
  leaseId: string | null;
  expectedSessionVersion: number;
}
