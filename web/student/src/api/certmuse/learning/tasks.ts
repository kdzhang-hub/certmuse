import type {
  StudentTaskLaunchVO,
  StudentTaskPoolListVO,
  StudentTaskPoolQuery,
  StudentTaskSupplementVO
} from '@/api/student/types';
import * as mock from '@/api/student/mock';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';

const inMockMode = () => import.meta.env.VITE_APP_STUDENT_MOCK === 'true';

const normalizeQuery = (query?: StudentTaskPoolQuery): StudentTaskPoolQuery => {
  const keyword = query?.keyword?.trim();
  return {
    pageNum: query?.pageNum,
    pageSize: query?.pageSize,
    ...(keyword ? { keyword } : {})
  };
};

const call = <T>(mockCall: () => AxiosPromise<T>, config: any): AxiosPromise<T> =>
  inMockMode() ? mockCall() : request(config);

/** 查询当前学员、当前有效学习目标的未完成任务池。 */
export const listLearningTaskPool = (query?: StudentTaskPoolQuery): AxiosPromise<StudentTaskPoolListVO> => {
  const params = normalizeQuery(query);
  return call(() => mock.listTaskPool(params), {
    url: '/api/learning/tasks',
    method: 'get',
    params
  });
};

/** 启动或继续一个冻结任务；请求号由一次用户操作生成，供网络重试复用。 */
export const launchLearningTask = (
  taskId: string,
  requestId: string = crypto.randomUUID()
): AxiosPromise<StudentTaskLaunchVO> =>
  call(() => mock.launchTaskPool(taskId), {
    url: `/api/learning/tasks/${taskId}/launch`,
    method: 'post',
    headers: { 'X-Request-Id': requestId, repeatSubmit: false }
  });

/** 按任务池规则补充可继续学习的任务。 */
export const supplementLearningTasks = (
  requestId: string = crypto.randomUUID()
): AxiosPromise<StudentTaskSupplementVO> =>
  call(mock.supplementTaskPool, {
    url: '/api/learning/tasks/supplement',
    method: 'post',
    headers: { 'X-Request-Id': requestId, repeatSubmit: false }
  });
