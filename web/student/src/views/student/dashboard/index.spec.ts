import { flushPromises } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { buttonByText, mountStudentView } from '../view-test-utils';
import DashboardView from './index.vue';

const api = vi.hoisted(() => ({
  listLearningTaskPool: vi.fn(),
  launchLearningTask: vi.fn(),
  getOverallScore: vi.fn(),
  listMistakeReviews: vi.fn(),
  getGoalSwitchOptions: vi.fn()
}));
const router = vi.hoisted(() => ({ push: vi.fn() }));

vi.mock('@/api/certmuse/learning/tasks', () => ({
  listLearningTaskPool: api.listLearningTaskPool,
  launchLearningTask: api.launchLearningTask
}));
vi.mock('@/api/certmuse/learning/overall-score', () => ({ getOverallScore: api.getOverallScore }));
vi.mock('@/api/certmuse/learning/mistakes', () => ({ listMistakeReviews: api.listMistakeReviews }));
vi.mock('@/api/certmuse/learning/goal-switch', () => ({ getGoalSwitchOptions: api.getGoalSwitchOptions }));
vi.mock('vue-router', () => ({ useRouter: () => router }));

describe('student dashboard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.listLearningTaskPool.mockResolvedValue({
      data: { total: 2, rows: [{ id: 'task-1', title: '架构风格练习', action: 'CONTINUE' }] }
    });
    api.getOverallScore.mockResolvedValue({ data: { overallScore: 76.5 } });
    api.listMistakeReviews.mockResolvedValue({ data: { total: 3, rows: [] } });
    api.getGoalSwitchOptions.mockResolvedValue({
      data: {
        currentGoal: {
          id: 'goal-1', certificationId: 'cert-1', certificationName: '系统架构设计师', syllabusVersionId: 'syllabus-1',
          syllabusVersionName: '2024 年考试大纲', targetExamYear: 2026, targetExamMonth: 11, targetExamDate: null,
          examBatchType: 'OFFICIAL', status: 'ACTIVE', version: 1
        }
      }
    });
  });

  it('shows the task, profile and pending-mistake summaries', async () => {
    const wrapper = mountStudentView(DashboardView);
    await flushPromises();

    expect(wrapper.text()).toContain('2条任务待完成');
    expect(wrapper.text()).toContain('76.5');
    expect(wrapper.text()).toContain('3道错题待订正');
    expect(wrapper.text()).toContain('继续任务');
    expect(wrapper.text()).toContain('系统架构设计师');
    expect(wrapper.text()).toContain('2026 年 11 月（官方）');

    await buttonByText(wrapper, '查看学习画像').trigger('click');
    expect(router.push).toHaveBeenCalledWith('/learning/progress');

    await buttonByText(wrapper, '切换学习目标').trigger('click');
    expect(router.push).toHaveBeenCalledWith('/learning/account');
  });
});
