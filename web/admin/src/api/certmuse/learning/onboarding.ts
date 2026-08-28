import type { OnboardingStatus } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';

/** 查询当前学员首次流程状态。用户身份只能从当前会话取得。 */
export function getOnboardingStatus(): AxiosPromise<OnboardingStatus> {
  return request({
    url: '/api/learning/onboarding/status',
    method: 'get'
  });
}
