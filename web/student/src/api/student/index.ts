import type { RuoYiAjaxResult } from '@/utils/api-types';
import type {
  StudentHistoryQuery,
  StudentGoalForm,
  StudentMistakeQuery,
  StudentPracticeSetForm,
  StudentResourceQuery,
  StudentSessionKind,
  StudentSessionItemSubmit,
  StudentSessionSubmit,
  StudentSettings,
  StudentTaskMaterial,
  StudentTaskQuery
} from './types';
import * as mock from './mock';

export {
  launchLearningTask as launchStudentTask,
  listLearningTaskPool as listStudentTaskPool,
  supplementLearningTasks as supplementStudentTasks
} from '@/api/certmuse/learning/tasks';

/**
 * 学生端数据边界。
 *
 * 尚未单独定义正式契约的页面继续从本模块获取 Mock 数据；已有正式契约的
 * 学习流程在各自的 `api/certmuse/learning` 模块中按环境切换实现。
 */
const inMockMode = () => import.meta.env.VITE_APP_STUDENT_MOCK === 'true';

const useMock = <T>(factory: () => Promise<RuoYiAjaxResult<T>>) => {
  if (!inMockMode()) {
    return Promise.reject(new Error('学生端后端接口尚未接入，请启用 VITE_APP_STUDENT_MOCK。'));
  }
  return factory();
};

export const getStudentOverview = () => useMock(mock.getOverview);
export const updateStudentGoal = (goal: StudentGoalForm) => useMock(() => mock.updateGoal(goal));
export const listStudentTasks = (query?: StudentTaskQuery) => useMock(() => mock.listTasks(query));
export const getStudentTask = (id: string) => useMock(() => mock.getTask(id));
export const getStudentTaskMaterial = (id: string): Promise<RuoYiAjaxResult<StudentTaskMaterial>> => useMock(() => mock.getTaskMaterial(id));
export const listStudentMistakes = (query?: StudentMistakeQuery) => useMock(() => mock.listMistakes(query));
export const getStudentMistake = (id: string) => useMock(() => mock.getMistake(id));
export const startStudentMistakeCorrection = (ids: string[]) => useMock(() => mock.startMistakeCorrection(ids));
export const listStudentPracticeSets = () => useMock(mock.listPracticeSets);
export const saveStudentPracticeSet = (form: StudentPracticeSetForm) => useMock(() => mock.savePracticeSet(form));
export const deleteStudentPracticeSet = (id: string) => useMock(() => mock.deletePracticeSet(id));
export const listStudentHistory = (query?: StudentHistoryQuery) => useMock(() => mock.listHistory(query));
/**
 * 学习资料页尚未有对应的服务端资料目录，但其既有内容本身就是本地演示资料。
 * PDF 原文使用独立的 `/api/learning/textbooks` 契约，不能因为生产环境关闭其它
 * Mock 页面而阻断这份纯前端资料列表。
 */
export const listStudentResources = (query?: StudentResourceQuery) => mock.listResources(query);
export const listStudentTopics = () => useMock(mock.listTopics);
export const getStudentSession = (kind: StudentSessionKind) => useMock(() => mock.getSession(kind));
export const submitStudentSessionItem = (input: StudentSessionItemSubmit) => useMock(() => mock.submitSessionItem(input));
export const submitStudentSession = (input: StudentSessionSubmit) => useMock(() => mock.submitSession(input));
export const getStudentSettings = () => useMock(mock.getSettings);
export const updateStudentSettings = (settings: StudentSettings) => useMock(() => mock.updateSettings(settings));
export const getStudentAccount = () => useMock(mock.getAccount);
