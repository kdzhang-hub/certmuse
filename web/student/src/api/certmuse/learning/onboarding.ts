import type { OnboardingStatus } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import * as mock from '@/api/student/mock';
import request from '@/utils/request';

const inMockMode = () => import.meta.env.VITE_APP_STUDENT_MOCK === 'true';

/** 查询当前学员首次流程状态；身份和用户范围仅从服务端会话取得。 */
export const getOnboardingStatus = (): AxiosPromise<OnboardingStatus> => {
  if (inMockMode()) return mock.getOnboardingStatus();
  return request({ url: '/api/learning/onboarding/status', method: 'get' });
};
