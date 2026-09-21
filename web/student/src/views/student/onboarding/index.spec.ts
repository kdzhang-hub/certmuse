import { flushPromises } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { buttonByText, mountStudentView, validateStudentForm } from '../view-test-utils';
import OnboardingView from './index.vue';

const api = vi.hoisted(() => ({
  createLearningGoal: vi.fn(),
  getLearningGoalOptions: vi.fn(),
  getOnboardingStatus: vi.fn()
}));
const router = vi.hoisted(() => ({ replace: vi.fn() }));
const clearSession = vi.hoisted(() => vi.fn());

vi.mock('@/api/certmuse/learning/goals', () => ({
  createLearningGoal: api.createLearningGoal,
  getLearningGoalOptions: api.getLearningGoalOptions
}));
vi.mock('@/api/certmuse/learning/onboarding', () => ({
  getOnboardingStatus: api.getOnboardingStatus
}));
vi.mock('@/store/modules/user', () => ({ useUserStore: () => ({ clearSession }) }));
vi.mock('vue-router', () => ({ useRouter: () => router }));

const options = {
  serverTime: '2026-08-12T18:00:00+08:00',
  timezone: 'Asia/Shanghai' as const,
  certifications: [{ id: 'cert-1', code: 'SA', name: '系统架构设计师' }],
  examYears: [
    { year: 2027, months: [5, 11] as Array<5 | 11>, selectable: true },
    { year: 2028, months: [11] as Array<5 | 11>, selectable: false }
  ],
  dailyMinutes: { min: 15 as const, max: 300 as const, defaultValue: 30 as const },
  defaults: { certificationId: 'cert-1', examYear: 2027, examMonth: 5 as const }
};

const ready = () => {
  api.getOnboardingStatus.mockResolvedValue({
    data: { goalStatus: 'NONE', diagnosticStatus: 'NOT_STARTED', nextAction: 'SET_GOAL' }
  });
  api.getLearningGoalOptions.mockResolvedValue({ data: options });
};

const contractFailure = (errorCode: string, extra: Record<string, unknown> = {}) => ({
  responseData: { errorCode, retryable: false, traceId: null, fieldErrors: [], ...extra }
});

describe('learning goal onboarding', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    validateStudentForm.mockResolvedValue(true);
    ready();
    api.createLearningGoal.mockResolvedValue({ data: { nextAction: 'START_DIAGNOSTIC' } });
  });

  it('loads server choices and creates the default four-field goal', async () => {
    const wrapper = mountStudentView(OnboardingView);
    await flushPromises();

    expect(wrapper.text()).toContain('系统架构设计师');
    expect(wrapper.text()).toContain('保存学习目标，进入学习首页');
    await buttonByText(wrapper, '保存学习目标').trigger('click');
    await flushPromises();

    expect(api.createLearningGoal).toHaveBeenCalledWith(
      { certificationId: 'cert-1', targetExamYear: 2027, targetExamMonth: 5, dailyMinutes: 30 },
      expect.any(String)
    );
    expect(router.replace).toHaveBeenCalledWith('/learning/home');
  });

  it('does not submit when validation fails', async () => {
    validateStudentForm.mockResolvedValue(false);
    const wrapper = mountStudentView(OnboardingView);
    await flushPromises();
    await buttonByText(wrapper, '保存学习目标').trigger('click');
    await flushPromises();
    expect(api.createLearningGoal).not.toHaveBeenCalled();
  });

  it('submits the configured default duration with the visible goal fields', async () => {
    const wrapper = mountStudentView(OnboardingView);
    await flushPromises();

    const fields = wrapper.findAllComponents({ name: 'ModelStub' });
    expect(fields).toHaveLength(3);
    fields[0].vm.$emit('update:modelValue', 'cert-1');
    fields[1].vm.$emit('update:modelValue', 2027);
    fields[2].vm.$emit('update:modelValue', 11);
    await flushPromises();

    await buttonByText(wrapper, '保存学习目标').trigger('click');
    await flushPromises();

    expect(api.createLearningGoal).toHaveBeenCalledWith(
      { certificationId: 'cert-1', targetExamYear: 2027, targetExamMonth: 11, dailyMinutes: 30 },
      expect.any(String)
    );
  });

  it('follows a server-owned next action without loading goal options', async () => {
    api.getOnboardingStatus.mockResolvedValue({
      data: { goalStatus: 'ACTIVE', diagnosticStatus: 'NOT_STARTED', nextAction: 'START_DIAGNOSTIC' }
    });

    mountStudentView(OnboardingView);
    await flushPromises();

    expect(router.replace).toHaveBeenCalledWith('/learning/home');
    expect(api.getLearningGoalOptions).not.toHaveBeenCalled();
  });

  it('shows an empty state when the server has no selectable goal defaults', async () => {
    api.getLearningGoalOptions.mockResolvedValue({ data: { ...options, defaults: null } });
    const wrapper = mountStudentView(OnboardingView);
    await flushPromises();
    expect(wrapper.text()).toContain('当前暂无可设置的学习目标');
  });

  it('rejects an unknown server next action instead of guessing a route', async () => {
    api.getOnboardingStatus.mockResolvedValue({ data: { nextAction: 'UNKNOWN_ACTION' } });
    const wrapper = mountStudentView(OnboardingView);
    await flushPromises();
    expect(wrapper.text()).toContain('首次流程状态未知，请刷新后重试');
    expect(router.replace).not.toHaveBeenCalled();
  });

  it('refreshes onboarding when goal options report an existing goal', async () => {
    api.getOnboardingStatus
      .mockResolvedValueOnce({ data: { nextAction: 'SET_GOAL' } })
      .mockResolvedValueOnce({ data: { nextAction: 'START_DIAGNOSTIC' } });
    api.getLearningGoalOptions.mockRejectedValue(contractFailure('GOAL_ALREADY_EXISTS'));
    mountStudentView(OnboardingView);
    await flushPromises();
    expect(router.replace).toHaveBeenCalledWith('/learning/home');
  });

  it('shows a generic load message for a non-error rejection', async () => {
    api.getOnboardingStatus.mockRejectedValue('offline');
    const wrapper = mountStudentView(OnboardingView);
    await flushPromises();
    expect(wrapper.text()).toContain('加载失败，请稍后重试');
  });

  it.each([
    [{ data: undefined }, '首次流程状态缺失，请刷新后重试。'],
    [{ data: { nextAction: 'SET_GOAL' } }, '目标设置选项缺失，请重新加载。']
  ])('shows a recoverable loading error for incomplete server data', async (statusResponse, message) => {
    api.getOnboardingStatus.mockResolvedValue(statusResponse);
    if ((statusResponse as any).data?.nextAction === 'SET_GOAL')
      api.getLearningGoalOptions.mockResolvedValue({ data: undefined });

    const wrapper = mountStudentView(OnboardingView);
    await flushPromises();

    expect(wrapper.text()).toContain(message);
    expect(wrapper.text()).toContain('重新加载');
  });

  it('refreshes onboarding when the goal already exists', async () => {
    api.createLearningGoal.mockRejectedValue(contractFailure('GOAL_ALREADY_EXISTS'));
    api.getOnboardingStatus
      .mockResolvedValueOnce({ data: { nextAction: 'SET_GOAL' } })
      .mockResolvedValueOnce({ data: { nextAction: 'START_DIAGNOSTIC' } });
    const wrapper = mountStudentView(OnboardingView);
    await flushPromises();
    await buttonByText(wrapper, '保存学习目标').trigger('click');
    await flushPromises();
    expect(router.replace).toHaveBeenCalledWith('/learning/home');
  });

  it.each([
    ['FIRST_DIAGNOSTIC_NOT_READY', '首次诊断暂不可开始'],
    ['LEARNING_GOAL_FORBIDDEN', '当前账号没有设置学习目标的权限'],
    ['DAILY_MINUTES_OUT_OF_RANGE', '目标设置选项已更新，请重新提交。'],
    ['LEARNING_GOAL_CREATE_FAILED', '学习目标保存失败，请稍后重试（错误编号：trace-7）']
  ])('shows the contract-specific %s failure', async (errorCode, message) => {
    api.createLearningGoal.mockRejectedValue(
      contractFailure(errorCode, errorCode === 'LEARNING_GOAL_CREATE_FAILED' ? { traceId: 'trace-7' } : {})
    );
    const wrapper = mountStudentView(OnboardingView);
    await flushPromises();
    await buttonByText(wrapper, '保存学习目标').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain(message);
  });

  it('clears the local session and returns to login when authentication expires', async () => {
    api.createLearningGoal.mockRejectedValue(contractFailure('UNAUTHORIZED'));
    const wrapper = mountStudentView(OnboardingView);
    await flushPromises();
    await buttonByText(wrapper, '保存学习目标').trigger('click');
    await flushPromises();
    expect(clearSession).toHaveBeenCalled();
    expect(router.replace).toHaveBeenCalledWith('/login?redirect=%2Flearning%2Fonboarding');
  });

  it('refreshes stale choices after an unavailable certification', async () => {
    api.createLearningGoal.mockRejectedValue(contractFailure('CERTIFICATION_UNAVAILABLE'));
    const wrapper = mountStudentView(OnboardingView);
    await flushPromises();
    await buttonByText(wrapper, '保存学习目标').trigger('click');
    await flushPromises();
    expect(api.getLearningGoalOptions).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('目标资格已更新，请重新选择');
  });

  it('refreshes stale choices after an invalid examination batch', async () => {
    api.createLearningGoal.mockRejectedValue(contractFailure('TARGET_EXAM_BATCH_INVALID'));
    const wrapper = mountStudentView(OnboardingView);
    await flushPromises();
    await buttonByText(wrapper, '保存学习目标').trigger('click');
    await flushPromises();
    expect(api.getLearningGoalOptions).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('考试批次已失效，已为你更新可选批次');
  });

  it('accepts the future ENTER_HOME response as well as the current START_DIAGNOSTIC response', async () => {
    api.createLearningGoal.mockResolvedValue({ data: { nextAction: 'ENTER_HOME' } });
    const wrapper = mountStudentView(OnboardingView);
    await flushPromises();
    await buttonByText(wrapper, '保存学习目标').trigger('click');
    await flushPromises();
    expect(router.replace).toHaveBeenCalledWith('/learning/home');
  });

  it('stops retrying and refreshes onboarding after an idempotency conflict', async () => {
    api.createLearningGoal.mockRejectedValue(contractFailure('GOAL_IDEMPOTENCY_CONFLICT'));
    api.getOnboardingStatus
      .mockResolvedValueOnce({ data: { nextAction: 'SET_GOAL' } })
      .mockResolvedValueOnce({ data: { nextAction: 'START_DIAGNOSTIC' } });
    const wrapper = mountStudentView(OnboardingView);
    await flushPromises();
    await buttonByText(wrapper, '保存学习目标').trigger('click');
    await flushPromises();
    expect(router.replace).toHaveBeenCalledWith('/learning/home');
  });

  it('ignores server field errors for controls that are not shown', async () => {
    api.createLearningGoal.mockRejectedValue(
      contractFailure('LEARNING_GOAL_FORBIDDEN', {
        fieldErrors: [
          { field: 'dailyMinutes', message: '学习时长无效' },
          { field: 'ownerId', message: '不应展示' }
        ]
      })
    );
    const wrapper = mountStudentView(OnboardingView);
    await flushPromises();
    await buttonByText(wrapper, '保存学习目标').trigger('click');
    await flushPromises();
    expect(wrapper.find('[error="学习时长无效"]').exists()).toBe(false);
    expect(wrapper.find('[error="不应展示"]').exists()).toBe(false);
  });

  it('shows a failure without inventing an error number when no trace is returned', async () => {
    api.createLearningGoal.mockRejectedValue(contractFailure('LEARNING_GOAL_CREATE_FAILED'));
    const wrapper = mountStudentView(OnboardingView);
    await flushPromises();
    await buttonByText(wrapper, '保存学习目标').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('学习目标保存失败，请稍后重试。');
    expect(wrapper.text()).not.toContain('错误编号');
  });

  it('shows generic error and non-error submission failures', async () => {
    api.createLearningGoal.mockRejectedValueOnce(new Error('服务繁忙'));
    let wrapper = mountStudentView(OnboardingView);
    await flushPromises();
    await buttonByText(wrapper, '保存学习目标').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('服务繁忙');

    api.createLearningGoal.mockRejectedValueOnce('offline');
    wrapper = mountStudentView(OnboardingView);
    await flushPromises();
    await buttonByText(wrapper, '保存学习目标').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('保存失败，请稍后重试');
  });
});
