export type StudentTaskStatus = '待开始' | '进行中' | '已完成';
export type StudentMistakeStatus = '待订正' | '已订正';
export type StudentSessionKind = 'task' | 'practice' | 'diagnostic' | 'verification' | 'correction' | 'exam';
export type StudentAnsweringMode = 'EXAM' | 'PRACTICE';

export interface StudentPageQuery {
  pageNum?: number;
  pageSize?: number;
  keyword?: string;
}

export interface StudentTaskQuery extends StudentPageQuery {
  status?: StudentTaskStatus;
  knowledgePoint?: string;
}

export interface StudentTaskVO {
  id: string;
  title: string;
  type: string;
  knowledgePoint: string;
  questionCount: number;
  duration: string;
  status: StudentTaskStatus;
  progress: number;
  updatedAt: string;
  recommendation: string;
}

/** U10 任务池列表项；不暴露任务状态或任务日期。 */
export interface StudentTaskPoolVO {
  id: string;
  title: string;
  phaseSummary: string[];
  knowledgePoint: string;
  questionCount: number;
  completedQuestionCount: number;
  estimatedMinutes: number;
  updatedAt: string;
  recommendation: string;
  action: 'START' | 'CONTINUE';
}

export interface StudentTaskPoolQuery extends StudentPageQuery {
}

export interface StudentTaskPoolListVO {
  rows: StudentTaskPoolVO[];
  total: number;
  canShowSupplement: boolean;
}

export interface StudentTaskSupplementVO {
  beforeAvailableCount: number;
  requestedCount: number;
  createdCount: number;
  refreshRequired: boolean;
}

export interface StudentTaskLaunchVO {
  taskId: string;
  nextAction: 'OPEN_LEARNING_CONTENT' | 'OPEN_PRACTICE_SESSION';
  sessionId: string | null;
}

/** 今日任务开始后展示的学习资料；当前仅供学生端 Mock 页面使用。 */
export interface StudentTaskMaterial {
  taskId: string;
  title: string;
  knowledgePoint: string;
  estimatedMinutes: number;
  summary: string;
  sections: Array<{ title: string; content: string }>;
}

export interface StudentMistakeQuery extends StudentPageQuery {
  status?: StudentMistakeStatus;
  knowledgePoint?: string;
  source?: string;
  minWrongCount?: number;
}

export interface StudentMistakeVO {
  id: string;
  questionId: string;
  title: string;
  knowledgePoint: string;
  status: StudentMistakeStatus;
  wrongCount: number;
  skipCount: number;
  sources: string[];
  lastWrongAt: string;
  updatedAt: string;
  summary: string;
}

export interface StudentMistakeHistoryItem {
  occurredAt: string;
  source: string;
  answerSummary: string;
  skipped: boolean;
}

export interface StudentMistakeDetailVO extends StudentMistakeVO {
  question: StudentQuestion;
  originalAnswer: string[];
  history: StudentMistakeHistoryItem[];
}

export interface StudentPracticeSetVO {
  id: string;
  name: string;
  note: string;
  questionCount: number;
  practiceCount: number;
  updatedAt: string;
  status: '可练习' | '题目不足';
}

export interface StudentPracticeSetForm {
  id?: string;
  name: string;
  note?: string;
  questionCount: number;
}

export interface StudentHistoryQuery extends StudentPageQuery {
  activityType?: string;
  dateRange?: string[];
}

export interface StudentHistoryVO {
  id: string;
  activity: string;
  activityType: string;
  duration: string;
  result: string;
  completedAt: string;
  knowledgePoint: string;
}

export interface StudentResourceQuery extends StudentPageQuery {
  category?: string;
  knowledgePoint?: string;
}

export interface StudentResourceVO {
  id: string;
  title: string;
  category: string;
  knowledgePoint: string;
  progress: number;
  updatedAt: string;
  description: string;
}

export interface StudentTopicVO {
  id: string;
  name: string;
  category: string;
  mastery: number | null;
  status: '需要加强' | '正在学习' | '比较稳定' | '继续了解';
  recommendation: string;
  recentChange: string;
}

export interface StudentGoal {
  certification: string;
  version: string;
  examDate: string;
  daysRemaining: number;
  dailyMinutes: number;
}

export interface StudentGoalForm {
  certification: string;
  version: string;
  examDate: string;
  dailyMinutes: number;
}

export interface StudentOverview {
  learnerName: string;
  goal: StudentGoal;
  stats: {
    todayTasks: number;
    weeklyMinutes: number;
    weeklyTarget: number;
    completedTasks: number;
    reviewCount: number;
  };
  currentTask?: StudentTaskVO;
  topics: StudentTopicVO[];
}

export interface StudentQuestion {
  id: string;
  stem: string;
  type: 'CHOICE' | 'CASE' | 'ESSAY';
  knowledgePoint: string;
  selectionMode?: 'single' | 'multiple';
  options?: Array<{ key: string; content: string }>;
  /** CASE 题在入库清洗后将公共题干和所有小问合并为一题。 */
  subQuestions?: string[];
  referenceAnswer?: string;
  analysis?: string;
  scoringPoints?: Array<{ code: string; description: string; maxScore: number }>;
  mockAnswer?: string[];
}

export interface StudentSession {
  id: string;
  kind: StudentSessionKind;
  mode: StudentAnsweringMode;
  title: string;
  instruction: string;
  estimatedMinutes: number;
  questions: StudentQuestion[];
}

export interface StudentSessionSubmit {
  sessionId: string;
  kind: StudentSessionKind;
  answers: Record<string, string[] | string>;
}

export interface StudentSessionItemSubmit {
  sessionId: string;
  kind: StudentSessionKind;
  questionId: string;
  answer: string[] | string;
}

export interface StudentSessionItemFeedback {
  questionId: string;
  submitted: true;
  correct: boolean | null;
  referenceAnswer?: string;
  analysis?: string;
  scoringPoints?: Array<{ code: string; description: string; maxScore: number }>;
  aiScoring: { state: 'NOT_REQUIRED' | 'PENDING' | 'SUCCEEDED'; score?: number; maxScore?: number; feedback?: string };
}

export interface StudentSessionResult {
  sessionId: string;
  kind: StudentSessionKind;
  title: string;
  answeredCount: number;
  correctCount: number;
  reviewCount: number;
  message: string;
  nextAction: string;
}

export interface StudentSettings {
  dailyMinutes: number;
  reminderEnabled: boolean;
  reminderTime: string;
  reviewFrequency: '每天' | '每两天' | '每周';
}

export interface StudentAccount {
  name: string;
  account: string;
  certification: string;
  examDate: string;
  joinedAt: string;
}
