import type {
  CompleteKnowledgePracticeVo,
  KnowledgePracticeAnswerSessionVo,
  KnowledgePracticeItemVo,
  KnowledgePracticeSetupVo,
  StartKnowledgePracticeRequest,
  StartKnowledgePracticeVo,
  SubmitKnowledgePracticeItemRequest,
  SubmitKnowledgePracticeItemVo
} from '@/api/certmuse/assessment/knowledge-practices';
import type {
  DiagnosticPreflightVo,
  DiagnosticQuestionVo,
  DiagnosticReportVo,
  DiagnosticResultStatusVo,
  DiagnosticSessionVo,
  DiagnosticTimerEventRequest,
  DiagnosticTimerEventVo,
  FinishCheckVo
} from '@/api/certmuse/learning/diagnostics';
import type {
  CreateLearningGoalRequest,
  CreateLearningGoalResultVo,
  LearningGoalOptionsVo
} from '@/api/certmuse/learning/goals';
import type {
  GoalSwitchCurrentGoalVo,
  GoalSwitchOptionsVo,
  SwitchLearningGoalRequest,
  SwitchLearningGoalResultVo
} from '@/api/certmuse/learning/goal-switch';
import type { PageResult } from '@/api/types';
import type { OnboardingStatus } from '@/api/types';
import type { RuoYiAjaxResult } from '@/utils/api-types';
import type {
  StudentAccount,
  StudentGoal,
  StudentGoalForm,
  StudentHistoryQuery,
  StudentHistoryVO,
  StudentMistakeDetailVO,
  StudentMistakeQuery,
  StudentMistakeVO,
  StudentOverview,
  StudentPageQuery,
  StudentPracticeSetForm,
  StudentPracticeSetVO,
  StudentQuestion,
  StudentResourceQuery,
  StudentResourceVO,
  StudentSession,
  StudentSessionItemFeedback,
  StudentSessionItemSubmit,
  StudentSessionKind,
  StudentSessionResult,
  StudentSessionSubmit,
  StudentSettings,
  StudentTaskMaterial,
  StudentTaskLaunchVO,
  StudentTaskPoolListVO,
  StudentTaskPoolQuery,
  StudentTaskPoolVO,
  StudentTaskSupplementVO,
  StudentTaskQuery,
  StudentTaskVO,
  StudentTopicVO
} from './types';

const mockLatency = 120;
let onboardingNextAction: OnboardingStatus['nextAction'] = 'SET_GOAL';

const diagnosticQuestions = Array.from({ length: 50 }, (_, index) => ({
  questionOrder: index + 1,
  questionType: 'CHOICE' as const,
  stem: `首次诊断第 ${index + 1} 题：请根据题目描述选择最符合软件工程与系统架构基本原则的选项。`,
  difficulty: 'medium',
  options: ['A', 'B', 'C', 'D'].map((label, index) => ({
    label,
    content: `选项 ${label}：本题用于了解当前知识基础。`,
    sortOrder: index + 1
  })),
  images: [],
  selectionMode: index % 7 === 0 ? 'multiple' : 'single'
}));
let diagnosticSession:
  | {
      id: string;
      version: number;
      currentOrder: number;
      answers: Record<number, string[]>;
      status: 'IN_PROGRESS' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
      submittedAt?: string;
      effectiveElapsedSeconds: number;
      lease?: { id: string; questionOrder: number; lastHeartbeatAt: number };
    }
  | undefined;

const knowledgePracticeSetup: KnowledgePracticeSetupVo = {
  goal: {
    id: '900000000000000301',
    certificationName: '系统架构设计师',
    syllabusVersionName: '2026 年考试大纲',
    version: 4
  },
  subjects: [
    {
      id: '900000000000000011',
      subjectName: '综合知识',
      nodes: [
        {
          id: '900000000000000101',
          parentId: null,
          examSubjectId: '900000000000000011',
          syllabusNumber: '第九章',
          syllabusTitle: '软件可靠性基础知识',
          treeDepth: 1,
          sortOrder: 1,
          importance: 2,
          questionCount: 51,
          mastery: { currentDirectAbility: null, profileStatus: 'unassessed', confidenceLevel: 'unassessed' },
          children: [
            {
              id: '900000000000000102',
              parentId: '900000000000000101',
              examSubjectId: '900000000000000011',
              syllabusNumber: '第一节',
              syllabusTitle: '软件可靠性概述',
              treeDepth: 2,
              sortOrder: 1,
              importance: 2,
              questionCount: 51,
              mastery: { currentDirectAbility: null, profileStatus: 'unassessed', confidenceLevel: 'unassessed' },
              children: [
                {
                  id: '900000000000000103',
                  parentId: '900000000000000102',
                  examSubjectId: '900000000000000011',
                  syllabusNumber: '知识点 1',
                  syllabusTitle: '可靠性基本概念',
                  treeDepth: 3,
                  sortOrder: 1,
                  importance: 2,
                  questionCount: 27,
                  mastery: { currentDirectAbility: null, profileStatus: 'unassessed', confidenceLevel: 'unassessed' },
                  children: []
                },
                {
                  id: '900000000000000104',
                  parentId: '900000000000000102',
                  examSubjectId: '900000000000000011',
                  syllabusNumber: '知识点 2',
                  syllabusTitle: '可靠性模型',
                  treeDepth: 3,
                  sortOrder: 2,
                  importance: 2,
                  questionCount: 24,
                  mastery: { currentDirectAbility: 76, profileStatus: 'learning', confidenceLevel: 'medium' },
                  children: []
                }
              ]
            }
          ]
        }
      ]
    },
    {
      id: '900000000000000012',
      subjectName: '案例分析',
      nodes: [
        {
          id: '900000000000000201',
          parentId: null,
          examSubjectId: '900000000000000012',
          syllabusNumber: '架构设计',
          syllabusTitle: '架构分析与设计',
          treeDepth: 1,
          sortOrder: 1,
          importance: 3,
          questionCount: 32,
          mastery: { currentDirectAbility: 68, profileStatus: 'learning', confidenceLevel: 'medium' },
          children: []
        }
      ]
    }
  ]
};
let knowledgePracticeSession: StartKnowledgePracticeVo | undefined;
let knowledgePracticeSessionSequence = 801;
const knowledgePracticeAnswerSessions = new Map<string, MockKnowledgePracticeAnswerSession>();

interface MockKnowledgePracticeAnswerSession {
  id: string;
  status: 'CREATED' | 'IN_PROGRESS' | 'COMPLETED';
  items: Array<{
    question: KnowledgePracticeItemVo['question'];
    correctOptionLabels: string[];
    analysis: string | null;
    selectedOptionLabels: string[] | null;
  }>;
}

const diagnosticAction = (): OnboardingStatus['nextAction'] => {
  if (!diagnosticSession) return 'START_DIAGNOSTIC';
  return (
    {
      IN_PROGRESS: 'CONTINUE_DIAGNOSTIC',
      PROCESSING: 'WAIT_PROCESSING',
      COMPLETED: 'VIEW_DIAGNOSTIC_REPORT',
      FAILED: 'RETRY_DIAGNOSTIC_RESULT'
    } as const
  )[diagnosticSession.status];
};
const diagnosticAnsweredCount = () =>
  Object.values(diagnosticSession?.answers ?? {}).filter(answer => answer.length > 0).length;

const success = async <T>(data: T): Promise<RuoYiAjaxResult<T>> => {
  await new Promise(resolve => setTimeout(resolve, mockLatency));
  return { code: 200, msg: 'success', data: structuredClone(data) };
};

const goal: StudentGoal = {
  certification: '系统架构设计师',
  version: '2025 版考试大纲',
  examDate: '2026 年 10 月 31 日',
  daysRemaining: 81,
  dailyMinutes: 30
};
let activeGoalId = 'mock-goal-1';
let activeGoalCertificationId = '900000000000000001';
let activeGoalVersion = 0;
const goalCertifications = [
  { id: '900000000000000001', code: 'SYSTEM_ARCHITECT', name: '系统架构设计师', syllabusVersionId: '900000000000000021' },
  { id: '900000000000000002', code: 'SYSTEM_ANALYST', name: '系统分析师', syllabusVersionId: '900000000000000031' }
];

let tasks: StudentTaskVO[] = [
  {
    id: 'task-architecture-style',
    title: '架构风格专项学习',
    type: '今日任务',
    knowledgePoint: '架构风格',
    questionCount: 8,
    duration: '20 分钟',
    status: '进行中',
    progress: 3,
    updatedAt: '今天 09:30',
    recommendation: '你在管道—过滤器的适用边界上还需要多做几道不同场景的新题。'
  },
  {
    id: 'task-database-design',
    title: '数据库设计回顾',
    type: '间隔复习',
    knowledgePoint: '数据库设计',
    questionCount: 6,
    duration: '15 分钟',
    status: '待开始',
    progress: 0,
    updatedAt: '昨天 20:10',
    recommendation: '根据上一次订正后的间隔复习计划安排。'
  },
  {
    id: 'task-requirements',
    title: '需求工程基础练习',
    type: '今日任务',
    knowledgePoint: '需求工程',
    questionCount: 10,
    duration: '25 分钟',
    status: '待开始',
    progress: 0,
    updatedAt: '2026-08-08 19:40',
    recommendation: '用一组短练习确认需求跟踪矩阵的使用范围。'
  },
  {
    id: 'task-quality',
    title: '系统质量属性巩固',
    type: '专项巩固',
    knowledgePoint: '系统质量属性',
    questionCount: 7,
    duration: '18 分钟',
    status: '已完成',
    progress: 7,
    updatedAt: '2026-08-07 21:20',
    recommendation: '已完成，建议在下一轮复习时检查遗忘情况。'
  }
];

let taskPool: StudentTaskPoolVO[] = [
  {
    id: 'task-architecture-style',
    title: '架构风格专项学习',
    phaseSummary: ['知识点学习', '知识点练习'],
    knowledgePoint: '架构风格',
    questionCount: 8,
    completedQuestionCount: 3,
    estimatedMinutes: 20,
    updatedAt: '刚刚',
    recommendation: '根据近期作答情况，先梳理适用边界，再用新题确认判断。',
    action: 'CONTINUE'
  },
  {
    id: 'task-database-design',
    title: '数据库设计回顾',
    phaseSummary: ['知识点学习', '知识点练习'],
    knowledgePoint: '数据库设计',
    questionCount: 8,
    completedQuestionCount: 0,
    estimatedMinutes: 15,
    updatedAt: '刚刚',
    recommendation: '先回顾设计步骤与约束，再完成对应的基础练习。',
    action: 'START'
  }
];

let activeCorrectionQuestionIds: string[] = [];

let mistakes: StudentMistakeVO[] = [
  {
    id: 'mistake-pipe-filter',
    questionId: 'question-1',
    title: '管道—过滤器架构风格的适用场景',
    knowledgePoint: '架构风格',
    status: '待订正',
    wrongCount: 2,
    skipCount: 0,
    sources: ['自主练习', '每日任务'],
    lastWrongAt: '2026-08-18 09:12',
    updatedAt: '今天 09:12',
    summary: '需要区分数据流处理与需要复杂共享状态的场景。'
  },
  {
    id: 'mistake-normalisation',
    questionId: 'question-3',
    title: '数据库规范化与反规范化取舍',
    knowledgePoint: '数据库设计',
    status: '待订正',
    wrongCount: 1,
    skipCount: 1,
    sources: ['知识点练习'],
    lastWrongAt: '2026-08-17 20:10',
    updatedAt: '昨天 20:10',
    summary: '在性能目标与数据一致性约束之间遗漏了前提条件。'
  },
  {
    id: 'mistake-traceability',
    questionId: 'question-2',
    title: '需求跟踪矩阵的使用范围',
    knowledgePoint: '需求工程',
    status: '待订正',
    wrongCount: 3,
    skipCount: 0,
    sources: ['首次诊断', '自主练习'],
    lastWrongAt: '2026-08-01 18:22',
    updatedAt: '2026-08-01 18:22',
    summary: '变更影响分析依赖需求、设计和测试工件之间的可追溯关系。'
  }
];

let practiceSets: StudentPracticeSetVO[] = [
  {
    id: 'set-architecture',
    name: '架构风格易错点',
    note: '用不同场景题巩固架构风格的适用边界。',
    questionCount: 18,
    practiceCount: 2,
    updatedAt: '今天 09:30',
    status: '可练习'
  },
  {
    id: 'set-case-analysis',
    name: '案例分析表达练习',
    note: '收集需要写出取舍理由的案例题。',
    questionCount: 12,
    practiceCount: 1,
    updatedAt: '2026-08-06 20:00',
    status: '可练习'
  }
];

let history: StudentHistoryVO[] = [
  {
    id: 'history-1',
    activity: '架构风格专项学习',
    activityType: '今日任务',
    duration: '12 分钟',
    result: '完成 3 / 8 题',
    completedAt: '今天 09:30',
    knowledgePoint: '架构风格'
  },
  {
    id: 'history-2',
    activity: '数据库设计错题订正',
    activityType: '错题复习',
    duration: '18 分钟',
    result: '订正 6 题',
    completedAt: '昨天 20:10',
    knowledgePoint: '数据库设计'
  },
  {
    id: 'history-3',
    activity: '首次学习诊断',
    activityType: '学习诊断',
    duration: '36 分钟',
    result: '完成 18 / 50 题',
    completedAt: '2026-08-02 19:40',
    knowledgePoint: '综合诊断'
  },
  {
    id: 'history-4',
    activity: '需求工程基础回顾',
    activityType: '间隔复习',
    duration: '10 分钟',
    result: '正确率 80%',
    completedAt: '2026-08-01 18:22',
    knowledgePoint: '需求工程'
  }
];

const resources: StudentResourceVO[] = [
  {
    id: 'resource-outline',
    title: '系统架构设计师 2025 版考试大纲',
    category: '考试大纲',
    knowledgePoint: '全部知识点',
    progress: 72,
    updatedAt: '2026-08-02',
    description: '用于确认当前复习范围和考试结构。'
  },
  {
    id: 'resource-style',
    title: '架构风格与设计模式速查',
    category: '重点资料',
    knowledgePoint: '架构风格',
    progress: 38,
    updatedAt: '2026-08-01',
    description: '用关键特征和反例对照常见架构风格。'
  },
  {
    id: 'resource-database',
    title: '数据库系统复习讲义',
    category: '教材章节',
    knowledgePoint: '数据库设计',
    progress: 54,
    updatedAt: '2026-07-28',
    description: '覆盖建模、规范化和事务处理的复习要点。'
  },
  {
    id: 'resource-requirements',
    title: '需求工程案例分析题集',
    category: '案例资料',
    knowledgePoint: '需求工程',
    progress: 18,
    updatedAt: '2026-07-25',
    description: '通过案例识别需求获取、跟踪和变更控制的边界。'
  }
];

const topics: StudentTopicVO[] = [
  {
    id: 'topic-architecture-style',
    name: '架构风格',
    category: '软件架构',
    mastery: 48,
    status: '需要加强',
    recommendation: '先完成今天的专项任务，再做一组不同场景的新题确认。',
    recentChange: '最近一次练习后发现概念边界仍不稳定。'
  },
  {
    id: 'topic-database-design',
    name: '数据库设计',
    category: '数据库系统',
    mastery: 62,
    status: '正在学习',
    recommendation: '完成订正后，按间隔复习计划回顾。',
    recentChange: '规范化与性能取舍的解释更完整了。'
  },
  {
    id: 'topic-requirements',
    name: '需求工程',
    category: '系统分析与设计',
    mastery: 74,
    status: '比较稳定',
    recommendation: '保留每周一次的短练习，防止遗忘。',
    recentChange: '最近三次短练习表现较稳定。'
  },
  {
    id: 'topic-quality',
    name: '系统质量属性',
    category: '软件架构',
    mastery: null,
    status: '继续了解',
    recommendation: '还没有足够的新记录，先从基础题开始。',
    recentChange: '当前记录不足，暂不作掌握程度判断。'
  }
];

let settings: StudentSettings = {
  dailyMinutes: 30,
  reminderEnabled: true,
  reminderTime: '20:00',
  reviewFrequency: '每天'
};

const questions: StudentQuestion[] = [
  {
    id: 'question-1',
    stem: '在需要将一系列独立数据处理步骤按顺序组合、并允许灵活替换处理步骤的场景中，优先考虑哪种架构风格？',
    type: 'CHOICE',
    knowledgePoint: '架构风格',
    selectionMode: 'single',
    options: [
      { key: 'A', content: '管道—过滤器风格' },
      { key: 'B', content: '黑板风格' },
      { key: 'C', content: '分层风格' },
      { key: 'D', content: '解释器风格' }
    ],
    mockAnswer: ['A'],
    referenceAnswer: '管道—过滤器风格适合将多个独立数据处理步骤按顺序组合，并允许替换步骤。',
    analysis: '关键在于“独立处理步骤”和“顺序组合”，这正是管道—过滤器风格的典型适用条件。'
  },
  {
    id: 'question-2',
    stem: '以下哪些做法有助于在需求变更后分析受影响的设计、代码与测试项？',
    type: 'CHOICE',
    knowledgePoint: '需求工程',
    selectionMode: 'multiple',
    options: [
      { key: 'A', content: '维护需求跟踪矩阵' },
      { key: 'B', content: '只记录最终上线日期' },
      { key: 'C', content: '关联需求、设计和测试工件' },
      { key: 'D', content: '忽略已关闭需求的历史关系' }
    ],
    mockAnswer: ['A', 'C'],
    referenceAnswer: 'A、C。需求跟踪矩阵及需求、设计、测试工件之间的关联可用于分析变更影响。',
    analysis: '变更影响分析依赖可追溯关系；上线日期与已关闭需求的历史忽略都不能提供影响链。'
  },
  {
    id: 'question-3',
    stem: '反规范化一定会降低系统的数据一致性。',
    type: 'CHOICE',
    knowledgePoint: '数据库设计',
    selectionMode: 'single',
    options: [
      { key: 'A', content: '正确' },
      { key: 'B', content: '错误' }
    ],
    mockAnswer: ['B'],
    referenceAnswer: '错误。反规范化会增加一致性维护成本，但通过约束、事务和同步策略仍可保持数据一致性。',
    analysis: '反规范化不是必然降低一致性，而是需要用更明确的机制管理冗余数据。'
  },
  {
    id: 'question-case-1',
    stem: '公共题干：某在线学习平台在考试报名高峰期出现访问缓慢、订单重复提交和课程资料下载失败等问题。技术团队计划从架构、数据一致性和容量保障三个方面改进系统。',
    type: 'CASE',
    knowledgePoint: '系统架构设计',
    subQuestions: [
      '第一问：针对报名高峰期的访问缓慢，给出两项架构改进措施，并说明理由。',
      '第二问：针对订单重复提交，说明应采用的幂等控制方式。',
      '第三问：说明容量保障中至少需要监控的两项指标。'
    ],
    referenceAnswer:
      '（1）采用负载均衡和缓存/CDN，分散请求并降低热点读取压力。\n（2）以业务请求号或订单号建立唯一约束，并以幂等键保证重复请求只产生一个有效订单。\n（3）监控请求量与响应时间、错误率、数据库连接/CPU/队列积压等容量指标。',
    analysis: '第一问考查高并发架构分流；第二问考查业务幂等边界；第三问要求同时关注体验、错误和资源容量。',
    scoringPoints: [
      { code: 'Q1-P1', description: '提出负载均衡、缓存或 CDN 等合理架构措施并说明作用', maxScore: 8 },
      { code: 'Q2-P1', description: '说明业务请求号/唯一约束/幂等键中的有效控制机制', maxScore: 8 },
      { code: 'Q3-P1', description: '列出至少两类有效容量或服务质量监控指标', maxScore: 9 }
    ]
  }
];

function createKnowledgePracticeItems(totalCount: number): MockKnowledgePracticeAnswerSession['items'] {
  const choiceQuestions = questions.filter(
    (
      question
    ): question is StudentQuestion & {
      type: 'CHOICE';
      selectionMode: 'single' | 'multiple';
      options: NonNullable<StudentQuestion['options']>;
      mockAnswer: string[];
    } =>
      question.type === 'CHOICE' &&
      Boolean(question.selectionMode && question.options?.length && question.mockAnswer?.length)
  );
  return Array.from({ length: totalCount }, (_, index) => {
    const source = choiceQuestions[index % choiceQuestions.length];
    return {
      question: {
        questionType: 'CHOICE',
        stem: totalCount > choiceQuestions.length ? `${source.stem}（专项练习第 ${index + 1} 题）` : source.stem,
        selectionMode: source.selectionMode,
        options: source.options.map(option => ({ label: option.key, content: option.content })),
        images: []
      },
      correctOptionLabels: [...source.mockAnswer],
      analysis: source.analysis ?? null,
      selectedOptionLabels: null
    };
  });
}

function sameLabels(left: string[], right: string[]) {
  return left.length === right.length && left.every((label, index) => label === right[index]);
}

const sessionLabels: Record<StudentSessionKind, { title: string; instruction: string; minutes: number }> = {
  task: {
    title: '今日任务练习',
    instruction: '先独立思考；需要时再查看提示。离开前会保存当前选择。',
    minutes: 20
  },
  practice: {
    title: '自主练习',
    instruction: '题目顺序已固定。提交前可以检查和修改已作答内容。',
    minutes: 20
  },
  diagnostic: {
    title: '首次学习诊断',
    instruction: '诊断可暂停；提交后会根据已有记录整理下一步学习建议。',
    minutes: 30
  },
  verification: {
    title: '新题验证',
    instruction: '这里使用不同的新题确认订正效果，默认不展示完整提示。',
    minutes: 15
  },
  correction: {
    title: '错题订正',
    instruction: '请独立重做原题。答对后会从“待订正”中移出，并保留在“已订正”历史中。',
    minutes: 15
  },
  exam: {
    title: '模拟考试',
    instruction: '请在规定时间内独立完成，交卷后不可修改。',
    minutes: 90
  }
};

const textIncludes = (value: string, keyword?: string) =>
  !keyword || value.toLowerCase().includes(keyword.toLowerCase());

const paginate = <T>(items: T[], query?: StudentPageQuery): PageResult<T> => {
  const pageNum = Math.max(1, query?.pageNum ?? 1);
  const pageSize = Math.max(1, query?.pageSize ?? 10);
  const start = (pageNum - 1) * pageSize;
  return { total: items.length, rows: items.slice(start, start + pageSize) };
};

export const getOverview = () => {
  const currentTask = tasks.find(item => item.status === '进行中') ?? tasks.find(item => item.status === '待开始');
  const completedTasks = tasks.filter(item => item.status === '已完成').length;
  return success<StudentOverview>({
    learnerName: '林同学',
    goal: { ...goal, dailyMinutes: settings.dailyMinutes },
    stats: {
      todayTasks: tasks.filter(item => item.status !== '已完成').length,
      weeklyMinutes: 96,
      weeklyTarget: settings.dailyMinutes * 5,
      completedTasks,
      reviewCount: mistakes.filter(item => item.status === '待订正').length
    },
    currentTask,
    topics: topics.slice(0, 4)
  });
};

export const updateGoal = async (nextGoal: StudentGoalForm) => {
  Object.assign(goal, nextGoal);
  settings.dailyMinutes = nextGoal.dailyMinutes;
  return success(goal);
};

export const getOnboardingStatus = () =>
  success<OnboardingStatus>({
    goalStatus: onboardingNextAction === 'SET_GOAL' ? 'NONE' : 'ACTIVE',
    diagnosticStatus:
      onboardingNextAction === 'SET_GOAL' ? 'NOT_STARTED' : (diagnosticSession?.status ?? 'NOT_STARTED'),
    currentCertificationId: onboardingNextAction === 'SET_GOAL' ? undefined : '900000000000000001',
    activeSessionId: diagnosticSession?.id,
    nextAction: onboardingNextAction === 'SET_GOAL' ? onboardingNextAction : diagnosticAction()
  });

export const getLearningGoalOptions = () => {
  const now = new Date();
  const examYears = goalExamYears(now);
  const firstAvailable = examYears.find(item => item.selectable);
  return success<LearningGoalOptionsVo>({
    serverTime: now.toISOString(),
    timezone: 'Asia/Shanghai',
    certifications: goalCertifications.map(({ id, code, name }) => ({ id, code, name })),
    examYears,
    dailyMinutes: { min: 15, max: 300, defaultValue: 30 },
    defaults: firstAvailable
      ? { certificationId: '900000000000000001', examYear: firstAvailable.year, examMonth: firstAvailable.months[0] }
      : null
  });
};

export const createLearningGoal = async (input: CreateLearningGoalRequest) => {
  const selected = goalCertifications.find(item => item.id === input.certificationId) ?? goalCertifications[0];
  await updateGoal({
    certification: selected.name,
    version: '第二版',
    examDate: `${input.targetExamYear} 年 ${input.targetExamMonth} 月`,
    dailyMinutes: input.dailyMinutes
  });
  activeGoalCertificationId = selected.id;
  activeGoalVersion = 0;
  onboardingNextAction = 'START_DIAGNOSTIC';
  return success<CreateLearningGoalResultVo>({
    goal: {
      id: 'mock-goal-1',
      certificationId: selected.id,
      certificationName: selected.name,
      syllabusVersionId: selected.syllabusVersionId,
      syllabusVersionName: '第二版',
      targetExamYear: input.targetExamYear,
      targetExamMonth: input.targetExamMonth,
      dailyMinutes: input.dailyMinutes,
      status: 'ACTIVE',
      version: 0
    },
    nextAction: 'START_DIAGNOSTIC'
  });
};

const goalExamYears = (now: Date) => {
  const currentYear = now.getFullYear();
  const currentMonth = now.getMonth() + 1;
  return Array.from({ length: 5 }, (_, index) => {
    const year = currentYear + index;
    const months = (year === currentYear ? ([5, 11] as const).filter(month => month > currentMonth) : [5, 11]) as Array<
      5 | 11
    >;
    return { year, months, selectable: months.length > 0 };
  });
};

const currentGoalSwitchVo = (): GoalSwitchCurrentGoalVo => {
  const certification = goalCertifications.find(item => item.id === activeGoalCertificationId) ?? goalCertifications[0];
  const match = goal.examDate.match(/(\d{4})\D+(\d{1,2})/);
  return {
    id: activeGoalId,
    certificationId: certification.id,
    certificationName: certification.name,
    syllabusVersionId: certification.syllabusVersionId,
    syllabusVersionName: goal.version,
    targetExamYear: Number(match?.[1] ?? new Date().getFullYear() + 1),
    targetExamMonth: Number(match?.[2] ?? 5) as 5 | 11,
    targetExamDate: null,
    examBatchType: 'ESTIMATED',
    status: 'ACTIVE',
    version: activeGoalVersion
  };
};

export const getGoalSwitchOptions = () => {
  const now = new Date();
  const interruptedSessions: GoalSwitchOptionsVo['interruptedSessions'] = diagnosticSession?.status === 'IN_PROGRESS'
    ? [{ sessionType: 'initial_diagnosis', title: '首次诊断', status: 'IN_PROGRESS' }]
    : [];
  return success<GoalSwitchOptionsVo>({
    serverTime: now.toISOString(),
    timezone: 'Asia/Shanghai',
    currentGoal: currentGoalSwitchVo(),
    interruptedSessions,
    certifications: goalCertifications
      .filter(item => item.id !== activeGoalCertificationId)
      .map(({ id, code, name }) => ({
        id,
        code,
        name,
        examBatches: goalExamYears(now).flatMap(item =>
          item.months.map(month => ({
            targetExamYear: item.year,
            targetExamMonth: month,
            examBatchType: 'ESTIMATED' as const,
            targetExamDate: null
          }))
        )
      }))
  });
};

export const switchLearningGoal = async (input: SwitchLearningGoalRequest) => {
  const previousGoal = currentGoalSwitchVo();
  const selected = goalCertifications.find(item => item.id === input.certificationId) ?? goalCertifications[0];
  const abandonedSessionCount = diagnosticSession?.status === 'IN_PROGRESS' ? 1 : 0;
  if (abandonedSessionCount && !input.confirmAbandonInProgress) return success<SwitchLearningGoalResultVo>(undefined as never);

  diagnosticSession = undefined;
  activeGoalId = `mock-goal-${Date.now()}`;
  activeGoalCertificationId = selected.id;
  activeGoalVersion = 0;
  Object.assign(goal, {
    certification: selected.name,
    version: '第二版',
    examDate: `${input.targetExamYear} 年 ${input.targetExamMonth} 月`
  });
  onboardingNextAction = 'START_DIAGNOSTIC';
  return success<SwitchLearningGoalResultVo>({
    previousGoal: { id: previousGoal.id, status: 'PAUSED', version: previousGoal.version + 1 },
    currentGoal: currentGoalSwitchVo(),
    abandonedSessionCount,
    nextAction: 'ENTER_HOME'
  });
};

export const getKnowledgePracticeSetup = () => success<KnowledgePracticeSetupVo>(knowledgePracticeSetup);

export const startKnowledgePractice = async (input: StartKnowledgePracticeRequest, _requestId: string) => {
  const findNode = (
    nodes: KnowledgePracticeSetupVo['subjects'][number]['nodes']
  ): KnowledgePracticeSetupVo['subjects'][number]['nodes'][number] | undefined => {
    for (const node of nodes) {
      if (node.id === input.knowledgePointId) return node;
      const nested = findNode(node.children);
      if (nested) return nested;
    }
  };
  const node = knowledgePracticeSetup.subjects.flatMap(subject => subject.nodes).length
    ? findNode(knowledgePracticeSetup.subjects.flatMap(subject => subject.nodes))
    : undefined;
  if (!node || ![2, 3].includes(node.treeDepth) || node.questionCount === 0) throw new Error('当前知识点暂不可练习。');
  if (!knowledgePracticeSession) {
    const sessionId = `900000000000000${knowledgePracticeSessionSequence++}`;
    knowledgePracticeSession = {
      sessionId,
      totalCount: node.questionCount,
      answerPath: `/learning/session/practice?sessionId=${sessionId}`
    };
    knowledgePracticeAnswerSessions.set(sessionId, {
      id: sessionId,
      status: 'CREATED',
      items: createKnowledgePracticeItems(node.questionCount)
    });
  }
  return success(knowledgePracticeSession);
};

const assertKnowledgePracticeAnswerSession = (sessionId: string) => {
  const session = knowledgePracticeAnswerSessions.get(sessionId);
  if (!session) throw new Error('练习会话不存在或已不可用。');
  return session;
};

const practiceSubmittedCount = (session: MockKnowledgePracticeAnswerSession) =>
  session.items.filter(item => item.selectedOptionLabels !== null).length;

const toKnowledgePracticeSessionVo = (
  session: MockKnowledgePracticeAnswerSession
): KnowledgePracticeAnswerSessionVo => {
  if (session.status === 'CREATED') session.status = 'IN_PROGRESS';
  const completed = session.status === 'COMPLETED';
  return {
    sessionId: session.id,
    sessionStatus: completed ? 'COMPLETED' : 'IN_PROGRESS',
    totalCount: session.items.length,
    submittedCount: practiceSubmittedCount(session),
    navigation: completed
      ? []
      : session.items.map((item, index) => ({
          questionOrder: index + 1,
          state: item.selectedOptionLabels
            ? sameLabels(item.selectedOptionLabels, item.correctOptionLabels)
              ? ('SUBMITTED_CORRECT' as const)
              : ('SUBMITTED_INCORRECT' as const)
            : ('UNANSWERED' as const)
        })),
    nextAction: completed ? 'RETURN_KNOWLEDGE_PRACTICE' : 'CONTINUE_PRACTICE',
    returnPath: completed ? '/learning/question-bank/knowledge-practice' : null
  };
};

export const getKnowledgePracticeAnswerSession = (sessionId: string) =>
  success<KnowledgePracticeAnswerSessionVo>(
    toKnowledgePracticeSessionVo(assertKnowledgePracticeAnswerSession(sessionId))
  );

export const getKnowledgePracticeItem = (sessionId: string, questionOrder: number) => {
  const session = assertKnowledgePracticeAnswerSession(sessionId);
  if (session.status === 'COMPLETED') throw new Error('练习已结束。');
  const item = session.items[questionOrder - 1];
  if (!item) throw new Error('题序不存在。');
  return success<KnowledgePracticeItemVo>({
    sessionId,
    questionOrder,
    totalCount: session.items.length,
    question: item.question,
    submission:
      item.selectedOptionLabels === null
        ? null
        : {
            selectedOptionLabels: [...item.selectedOptionLabels],
            correct: sameLabels(item.selectedOptionLabels, item.correctOptionLabels),
            correctOptionLabels: [...item.correctOptionLabels],
            analysis: item.analysis
          }
  });
};

export const submitKnowledgePracticeItem = async (
  sessionId: string,
  questionOrder: number,
  input: SubmitKnowledgePracticeItemRequest,
  _requestId: string
) => {
  const session = assertKnowledgePracticeAnswerSession(sessionId);
  if (session.status === 'CREATED') session.status = 'IN_PROGRESS';
  if (session.status !== 'IN_PROGRESS') throw new Error('练习已结束。');
  const item = session.items[questionOrder - 1];
  if (!item) throw new Error('题序不存在。');
  if (item.selectedOptionLabels !== null) throw new Error('该题已经提交。');
  const selected = input.answer?.value;
  const labels = item.question.options.map(option => option.label);
  if (
    !Array.isArray(selected) ||
    !selected.length ||
    selected.some(label => typeof label !== 'string' || !label.trim()) ||
    new Set(selected).size !== selected.length
  ) {
    throw new Error('答案格式不正确。');
  }
  if (
    selected.some(label => !labels.includes(label)) ||
    (item.question.selectionMode === 'single' && selected.length !== 1)
  ) {
    throw new Error('答案不符合本题规则。');
  }
  item.selectedOptionLabels = labels.filter(label => selected.includes(label));
  const feedback = {
    selectedOptionLabels: [...item.selectedOptionLabels],
    correct: sameLabels(item.selectedOptionLabels, item.correctOptionLabels),
    correctOptionLabels: [...item.correctOptionLabels],
    analysis: item.analysis
  };
  const data: SubmitKnowledgePracticeItemVo = {
    questionOrder,
    submittedCount: practiceSubmittedCount(session),
    feedback
  };
  return success(data);
};

export const completeKnowledgePractice = async (sessionId: string, _requestId: string) => {
  const session = assertKnowledgePracticeAnswerSession(sessionId);
  if (session.status !== 'COMPLETED') {
    if (session.status === 'CREATED') session.status = 'IN_PROGRESS';
    session.status = 'COMPLETED';
    if (knowledgePracticeSession?.sessionId === sessionId) knowledgePracticeSession = undefined;
  }
  const data: CompleteKnowledgePracticeVo = {
    sessionId,
    sessionStatus: 'COMPLETED',
    submittedCount: practiceSubmittedCount(session),
    returnPath: '/learning/question-bank/knowledge-practice'
  };
  return success(data);
};

export const getDiagnosticPreflight = () => {
  const session = diagnosticSession;
  return success<DiagnosticPreflightVo>({
    goal: {
      id: 'mock-goal-1',
      certificationId: '900000000000000001',
      certificationName: goal.certification,
      syllabusVersionId: '900000000000000021',
      syllabusVersionName: goal.version,
      version: 0
    },
    diagnosticStatus: session?.status ?? 'NOT_STARTED',
    nextAction: diagnosticAction() as DiagnosticPreflightVo['nextAction'],
    diagnosticRevision: session
      ? null
      : {
          id: 'mock-diagnostic-revision-1',
          questionCount: 50,
          estimatedMinutes: 50,
          subjectBreakdown: [
            { examSubjectId: '1', subjectName: '综合知识', questionCount: 25 },
            { examSubjectId: '2', subjectName: '案例分析', questionCount: 15 },
            { examSubjectId: '3', subjectName: '论文写作', questionCount: 10 }
          ]
        },
    existingSession:
      session && session.status === 'IN_PROGRESS'
        ? {
            id: session.id,
            answeredCount: diagnosticAnsweredCount(),
            totalCount: 50,
            lastQuestionOrder: session.currentOrder,
            sessionVersion: session.version
          }
        : null,
    blockers: []
  });
};

export const startDiagnostic = async (_input: { diagnosticRevisionId: string; goalVersion: number }) => {
  const resumed = Boolean(diagnosticSession);
  if (!diagnosticSession)
    diagnosticSession = {
      id: 'mock-diagnostic-session-1',
      version: 0,
      currentOrder: 1,
      answers: {},
      status: 'IN_PROGRESS',
      effectiveElapsedSeconds: 0
    };
  onboardingNextAction = 'START_DIAGNOSTIC';
  return success({
    sessionId: diagnosticSession.id,
    status: 'IN_PROGRESS' as const,
    resumed,
    answeredCount: diagnosticAnsweredCount(),
    totalCount: 50 as const,
    resumeQuestionOrder: diagnosticSession.currentOrder,
    sessionVersion: diagnosticSession.version
  });
};

const assertDiagnosticSession = (id: string) => {
  if (!diagnosticSession || diagnosticSession.id !== id) throw new Error('诊断会话不存在，请重新进入首次流程。');
  return diagnosticSession;
};
export const getDiagnosticSession = (id: string) => {
  const session = assertDiagnosticSession(id);
  const answered = diagnosticAnsweredCount();
  return success<DiagnosticSessionVo>({
    sessionId: session.id,
    status: session.status,
    nextAction: diagnosticAction(),
    sessionVersion: session.version,
    currentQuestionOrder: session.currentOrder,
    answeredCount: answered,
    unansweredCount: 50 - answered,
    estimatedDurationSeconds: 60 * 60,
    effectiveElapsedSeconds: session.effectiveElapsedSeconds,
    serverTime: new Date().toISOString(),
    navigation: diagnosticQuestions.map(question => ({
      questionOrder: question.questionOrder,
      state:
        question.questionOrder === session.currentOrder
          ? 'CURRENT'
          : session.answers[question.questionOrder]?.length
            ? 'ANSWERED'
            : 'UNANSWERED'
    }))
  });
};
export const getDiagnosticQuestion = (id: string, order: number) => {
  const session = assertDiagnosticSession(id);
  const question = diagnosticQuestions[order - 1];
  if (!question) throw new Error('题号无效。');
  return success<DiagnosticQuestionVo>({
    ...question,
    answer: {
      schema_version: '1.0',
      answer_type: 'CHOICE',
      selection_mode: question.selectionMode,
      value: session.answers[order] ?? null
    },
    answerSaveState: 'SAVED',
    sessionVersion: session.version
  });
};

const diagnosticTiming = (
  session: NonNullable<typeof diagnosticSession>,
  questionOrder: number,
  eventAccepted: boolean,
  leaseId: string | null,
  timerState: DiagnosticTimerEventVo['timerState']
) =>
  success<DiagnosticTimerEventVo>({
    eventAccepted,
    leaseId,
    questionOrder,
    timerState,
    estimatedDurationSeconds: 60 * 60,
    effectiveElapsedSeconds: session.effectiveElapsedSeconds,
    serverTime: new Date().toISOString()
  });

export const sendDiagnosticTimerEvent = (id: string, input: DiagnosticTimerEventRequest) => {
  const session = assertDiagnosticSession(id);
  const now = Date.now();
  const lease = session.lease;
  const expires = lease && now - lease.lastHeartbeatAt > 20_000;
  const accrue = () => {
    if (!session.lease) return;
    session.effectiveElapsedSeconds += Math.max(0, Math.floor((now - session.lease.lastHeartbeatAt) / 1000));
  };
  if (input.eventType === 'ENTER') {
    if (lease && !expires) accrue();
    session.lease = { id: crypto.randomUUID(), questionOrder: input.questionOrder, lastHeartbeatAt: now };
    return diagnosticTiming(session, input.questionOrder, true, session.lease.id, 'RUNNING');
  }
  if (!lease || expires || lease.id !== input.leaseId || lease.questionOrder !== input.questionOrder) {
    if (expires) session.lease = undefined;
    return diagnosticTiming(session, input.questionOrder, false, null, expires ? 'STOPPED' : 'REPLACED');
  }
  accrue();
  if (input.eventType === 'HEARTBEAT') {
    session.lease.lastHeartbeatAt = now;
    return diagnosticTiming(session, input.questionOrder, true, session.lease.id, 'RUNNING');
  }
  session.lease = undefined;
  return diagnosticTiming(session, input.questionOrder, true, null, 'STOPPED');
};
export const saveDiagnosticDraft = (
  id: string,
  order: number,
  input: { answer: { value: string[] | null }; expectedSessionVersion: number; currentQuestionOrder: number }
) => {
  const session = assertDiagnosticSession(id);
  if (session.status !== 'IN_PROGRESS') throw new Error('本次诊断已提交，不能再修改答案。');
  if (input.expectedSessionVersion !== session.version) throw new Error('其他设备已有新进度，已同步最新内容。');
  session.answers[order] = [...(input.answer.value ?? [])];
  session.currentOrder = input.currentQuestionOrder;
  session.version += 1;
  return success({
    savedAt: new Date().toISOString(),
    answered: (input.answer.value?.length ?? 0) > 0,
    answeredCount: diagnosticAnsweredCount(),
    sessionVersion: session.version
  });
};
export const pauseDiagnostic = (
  id: string,
  input: { currentQuestionOrder: number; expectedSessionVersion: number }
) => {
  const session = assertDiagnosticSession(id);
  if (input.expectedSessionVersion !== session.version) throw new Error('其他设备已有新进度，已同步最新内容。');
  session.currentOrder = input.currentQuestionOrder;
  session.version += 1;
  session.lease = undefined;
  return getDiagnosticSession(id);
};
export const getDiagnosticFinishCheck = (id: string) => {
  assertDiagnosticSession(id);
  const answered = diagnosticAnsweredCount();
  return success<FinishCheckVo>({
    answeredCount: answered,
    unansweredCount: 50 - answered,
    pendingSaveCount: 0,
    sessionVersion: diagnosticSession!.version,
    firstUnansweredQuestionOrder:
      diagnosticQuestions.find(question => !diagnosticSession!.answers[question.questionOrder]?.length)
        ?.questionOrder ?? null
  });
};
export const finishDiagnostic = (id: string, input: { expectedSessionVersion: number }) => {
  const session = assertDiagnosticSession(id);
  if (input.expectedSessionVersion !== session.version) throw new Error('其他设备已有新进度，已同步最新内容。');
  session.status = 'PROCESSING';
  session.lease = undefined;
  session.submittedAt = new Date().toISOString();
  return success({
    sessionId: session.id,
    diagnosticStatus: 'PROCESSING' as const,
    nextAction: 'WAIT_PROCESSING' as const,
    submittedAt: session.submittedAt
  });
};
export const getDiagnosticResultStatus = (id: string) => {
  const session = assertDiagnosticSession(id);
  const stages: DiagnosticResultStatusVo['stages'] = [
    'SUBMITTED',
    'SCORING',
    'REPORT_GENERATING',
    'PROFILE_GENERATING',
    'COMPLETED'
  ].map((code, index) => ({
    code: code as DiagnosticResultStatusVo['stages'][number]['code'],
    state:
      session.status === 'PROCESSING' ? (index === 1 ? 'RUNNING' : index === 0 ? 'COMPLETED' : 'PENDING') : 'COMPLETED'
  }));
  return success<DiagnosticResultStatusVo>({
    sessionId: id,
    diagnosticStatus: session.status === 'IN_PROGRESS' ? 'PROCESSING' : session.status,
    nextAction: diagnosticAction() as DiagnosticResultStatusVo['nextAction'],
    stages,
    failure: null
  });
};
export const regenerateDiagnosticResult = (id: string) => {
  const session = assertDiagnosticSession(id);
  session.status = 'PROCESSING';
  return success({ diagnosticStatus: 'PROCESSING' as const, nextAction: 'WAIT_PROCESSING' as const });
};
export const getDiagnosticReport = (id: string) => {
  const session = assertDiagnosticSession(id);
  if (session.status !== 'COMPLETED') throw new Error('诊断报告尚在生成，请稍后查看。');
  const answered = diagnosticAnsweredCount();
  return success<DiagnosticReportVo>({
    diagnosticStatus: 'COMPLETED',
    profileStatus: 'AVAILABLE',
    nextAction: 'VIEW_DIAGNOSTIC_REPORT',
    report: {
      subjectScores: {
        schema_version: 'diagnostic_report/1.0',
        totalQuestions: 50,
        correctCount: 0,
        unansweredCount: 50 - answered,
        subjects: [
          { examSubjectId: '1', questionCount: 25, correctCount: 0 },
          { examSubjectId: '2', questionCount: 15, correctCount: 0 },
          { examSubjectId: '3', questionCount: 10, correctCount: 0 }
        ]
      },
      profileSummary: {
        schema_version: 'diagnostic_report/1.0',
        knowledgePoints: [],
        priorityDirections: [],
        confidence: 'low',
        dataStatus: 'INITIAL_PROFILE'
      }
    }
  });
};

export const listTasks = (query?: StudentTaskQuery) => {
  const filtered = tasks.filter(
    item =>
      (!query?.status || item.status === query.status) &&
      (!query?.knowledgePoint || item.knowledgePoint === query.knowledgePoint) &&
      textIncludes(`${item.title} ${item.knowledgePoint}`, query?.keyword)
  );
  return success(paginate(filtered, query));
};

export const listTaskPool = (query?: StudentTaskPoolQuery) => {
  const filtered = taskPool.filter(item => textIncludes(`${item.title} ${item.knowledgePoint}`, query?.keyword));
  const page = paginate(filtered, query);
  return success<StudentTaskPoolListVO>({
    ...page,
    canShowSupplement: taskPool.length === 0
  });
};

export const supplementTaskPool = () => {
  const beforeAvailableCount = taskPool.length;
  const requestedCount = Math.max(0, 4 - beforeAvailableCount);
  const additions: StudentTaskPoolVO[] = [
    {
      id: `pool-component-${Date.now()}`,
      title: '构件设计专项学习',
      phaseSummary: ['知识点学习', '知识点练习'],
      knowledgePoint: '构件设计',
      questionCount: 8,
      completedQuestionCount: 0,
      estimatedMinutes: 18,
      updatedAt: '刚刚',
      recommendation: '从构件职责和接口边界开始，再完成一组针对性练习。',
      action: 'START'
    },
    {
      id: `pool-quality-${Date.now()}`,
      title: '系统质量属性巩固',
      phaseSummary: ['知识点学习', '知识点练习'],
      knowledgePoint: '系统质量属性',
      questionCount: 8,
      completedQuestionCount: 0,
      estimatedMinutes: 20,
      updatedAt: '刚刚',
      recommendation: '结合质量属性场景，练习区分目标、约束与实现手段。',
      action: 'START'
    },
    {
      id: `pool-requirements-${Date.now()}`,
      title: '需求工程基础练习',
      phaseSummary: ['知识点学习', '知识点练习'],
      knowledgePoint: '需求工程',
      questionCount: 8,
      completedQuestionCount: 0,
      estimatedMinutes: 25,
      updatedAt: '刚刚',
      recommendation: '通过短练习确认需求跟踪和变更控制的使用范围。',
      action: 'START'
    },
    {
      id: `pool-patterns-${Date.now()}`,
      title: '架构设计模式练习',
      phaseSummary: ['知识点学习', '知识点练习'],
      knowledgePoint: '架构设计模式',
      questionCount: 8,
      completedQuestionCount: 0,
      estimatedMinutes: 20,
      updatedAt: '刚刚',
      recommendation: '先辨别模式解决的问题，再用不同场景的新题验证。',
      action: 'START'
    }
  ];
  taskPool = [...taskPool, ...additions.slice(0, requestedCount)];
  return success<StudentTaskSupplementVO>({
    beforeAvailableCount,
    requestedCount,
    createdCount: requestedCount,
    refreshRequired: true
  });
};

export const launchTaskPool = (taskId: string) => {
  const task = taskPool.find(item => item.id === taskId);
  if (!task) throw new Error('任务不存在。');
  const result: StudentTaskLaunchVO = task.action === 'CONTINUE'
    ? { taskId, nextAction: 'OPEN_PRACTICE_SESSION', sessionId: `${taskId}-session` }
    : { taskId, nextAction: 'OPEN_LEARNING_CONTENT', sessionId: null };
  return success(result);
};

export const getTask = (id: string) => success(tasks.find(item => item.id === id));

export const listMistakes = (query?: StudentMistakeQuery) => {
  const filtered = mistakes.filter(
    item =>
      (!query?.status || item.status === query.status) &&
      (!query?.knowledgePoint || item.knowledgePoint === query.knowledgePoint) &&
      (!query?.source || item.sources.includes(query.source)) &&
      (!query?.minWrongCount || item.wrongCount >= query.minWrongCount) &&
      textIncludes(`${item.title} ${item.knowledgePoint}`, query?.keyword)
  );
  return success(paginate(filtered, query));
};

export const getMistake = (id: string) => {
  const mistake = mistakes.find(item => item.id === id);
  const question = mistake ? questions.find(item => item.id === mistake.questionId) : undefined;
  if (!mistake || !question) return success<StudentMistakeDetailVO | undefined>(undefined);
  return success<StudentMistakeDetailVO>({
    ...mistake,
    question,
    originalAnswer: mistake.questionId === 'question-1' ? ['B'] : mistake.questionId === 'question-2' ? ['A', 'D'] : ['A'],
    history: [
      {
        occurredAt: mistake.lastWrongAt,
        source: mistake.sources[0],
        answerSummary: mistake.questionId === 'question-2' ? '选择了 A、D' : '选择了 A',
        skipped: false
      },
      ...(mistake.skipCount
        ? [{ occurredAt: '2026-08-15 19:40', source: mistake.sources[0], answerSummary: '本题未作答', skipped: true }]
        : [])
    ]
  });
};

export const startMistakeCorrection = (ids: string[]) => {
  const selected = [...new Set(ids)]
    .map(id => mistakes.find(item => item.id === id))
    .filter((item): item is StudentMistakeVO => Boolean(item && item.status === '待订正'));
  if (!selected.length) throw new Error('请选择至少一道待订正题目。');
  activeCorrectionQuestionIds = selected.map(item => item.questionId);
  return success({ sessionId: `correction-${Date.now()}`, questionCount: activeCorrectionQuestionIds.length });
};

export const listPracticeSets = () => success(practiceSets);

export const savePracticeSet = async (form: StudentPracticeSetForm) => {
  const now = '刚刚';
  if (form.id) {
    const target = practiceSets.find(item => item.id === form.id);
    if (target) {
      target.name = form.name;
      target.note = form.note ?? '';
      target.questionCount = form.questionCount;
      target.updatedAt = now;
      target.status = form.questionCount > 0 ? '可练习' : '题目不足';
      return success(target);
    }
  }
  const created: StudentPracticeSetVO = {
    id: `set-${Date.now()}`,
    name: form.name,
    note: form.note ?? '',
    questionCount: form.questionCount,
    practiceCount: 0,
    updatedAt: now,
    status: form.questionCount > 0 ? '可练习' : '题目不足'
  };
  practiceSets = [created, ...practiceSets];
  return success(created);
};

export const deletePracticeSet = async (id: string) => {
  practiceSets = practiceSets.filter(item => item.id !== id);
  return success(true);
};

export const listHistory = (query?: StudentHistoryQuery) => {
  const filtered = history.filter(
    item =>
      (!query?.activityType || item.activityType === query.activityType) &&
      textIncludes(`${item.activity} ${item.knowledgePoint}`, query?.keyword)
  );
  return success(paginate(filtered, query));
};

export const listResources = (query?: StudentResourceQuery) => {
  const filtered = resources.filter(
    item =>
      (!query?.category || item.category === query.category) &&
      (!query?.knowledgePoint || item.knowledgePoint === query.knowledgePoint) &&
      textIncludes(`${item.title} ${item.description}`, query?.keyword)
  );
  return success(paginate(filtered, query));
};

export const listTopics = () => success(topics);

export const getSession = (kind: StudentSessionKind) => {
  const label = sessionLabels[kind];
  const correctionQuestions = questions.filter(question => activeCorrectionQuestionIds.includes(question.id));
  return success<StudentSession>({
    id: `${kind}-session-demo`,
    kind,
    mode: kind === 'exam' || kind === 'diagnostic' ? 'EXAM' : 'PRACTICE',
    title: label.title,
    instruction: label.instruction,
    estimatedMinutes: label.minutes,
    questions: kind === 'practice'
      ? questions.filter(question => question.type === 'CHOICE')
      : kind === 'correction'
        ? correctionQuestions
        : questions
  });
};

export const getTaskMaterial = (taskId: string) => {
  const task = tasks.find(item => item.id === taskId) ?? taskPool.find(item => item.id === taskId);
  if (!task) throw new Error('任务不存在。');
  return success<StudentTaskMaterial>({
    taskId: task.id,
    title: task.title,
    knowledgePoint: task.knowledgePoint,
    estimatedMinutes: 'estimatedMinutes' in task
      ? Math.max(5, Math.floor(task.estimatedMinutes * 0.45))
      : Math.max(5, Math.floor(Number.parseInt(task.duration, 10) * 0.45)),
    summary: `先用几分钟梳理“${task.knowledgePoint}”的关键判断，再进入本任务的题目练习。`,
    sections: [
      {
        title: '先抓住判断对象',
        content: `学习 ${task.knowledgePoint} 时，先明确题目描述的是目标、约束还是具体实现，再判断它与场景是否匹配。`
      },
      {
        title: '避免只记结论',
        content: '看到术语后先说清适用前提和限制条件；当题干给出反例或边界条件时，不能只凭关键词选择。'
      },
      {
        title: '带着问题去练习',
        content: '下面的题目会检验你是否能在不同场景中作出判断。建议先独立作答，再查看提交后的反馈。'
      }
    ]
  });
};

export const submitSessionItem = async (input: StudentSessionItemSubmit) => {
  const question = questions.find(item => item.id === input.questionId);
  if (!question) throw new Error('题目不存在。');
  const subjective = question.type === 'CASE' || question.type === 'ESSAY';
  const correct =
    subjective || !Array.isArray(input.answer)
      ? null
      : input.answer.toSorted().join(',') === (question.mockAnswer ?? []).toSorted().join(',');
  const feedback: StudentSessionItemFeedback = {
    questionId: question.id,
    submitted: true,
    correct,
    referenceAnswer: question.referenceAnswer,
    analysis: question.analysis,
    scoringPoints: question.scoringPoints,
    aiScoring: subjective ? { state: 'PENDING' } : { state: 'NOT_REQUIRED' }
  };
  if (input.kind === 'correction' && correct) {
    mistakes = mistakes.map(item =>
      item.questionId === question.id && item.status === '待订正'
        ? { ...item, status: '已订正', updatedAt: '刚刚' }
        : item
    );
  }
  return success(feedback);
};

export const submitSession = async (input: StudentSessionSubmit) => {
  const answeredCount = Object.values(input.answers).filter(answer =>
    Array.isArray(answer) ? answer.length > 0 : answer.trim().length > 0
  ).length;
  const result: StudentSessionResult = {
    sessionId: input.sessionId,
    kind: input.kind,
    title: sessionLabels[input.kind].title,
    answeredCount,
    correctCount: Math.min(answeredCount, 2),
    reviewCount: Math.max(0, answeredCount - 2),
    message: '答题记录已保存，结果会用于安排下一步学习。',
    nextAction: input.kind === 'verification' ? '查看学习进度' : '查看错题并安排下一步练习'
  };
  history = [
    {
      id: `history-${Date.now()}`,
      activity: result.title,
      activityType: input.kind === 'diagnostic' ? '学习诊断' : '自主练习',
      duration: `${sessionLabels[input.kind].minutes} 分钟`,
      result: `完成 ${result.answeredCount} 题`,
      completedAt: '刚刚',
      knowledgePoint: '综合练习'
    },
    ...history
  ];
  return success(result);
};

export const getSettings = () => success(settings);

export const updateSettings = async (nextSettings: StudentSettings) => {
  settings = { ...nextSettings };
  return success(settings);
};

export const getAccount = () =>
  success<StudentAccount>({
    name: '林同学',
    account: 'student-demo',
    certification: goal.certification,
    examDate: goal.examDate,
    joinedAt: '2026-07-20'
  });
