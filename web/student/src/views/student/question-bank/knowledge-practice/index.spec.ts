import { flushPromises } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';
import KnowledgePracticePage from './index.vue';
import { mountStudentView } from '@/views/student/view-test-utils';

const api = vi.hoisted(() => ({
  getSetup: vi.fn(),
  start: vi.fn()
}));
const router = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn(), resolve: vi.fn((path: string) => ({ path, query: {} })) }));
const route = vi.hoisted(() => ({ query: {} as Record<string, string> }));

vi.mock('@/api/certmuse/assessment/knowledge-practices', async importOriginal => {
  const original = await importOriginal<typeof import('@/api/certmuse/assessment/knowledge-practices')>();
  return { ...original, getKnowledgePracticeSetup: api.getSetup, startKnowledgePractice: api.start };
});
vi.mock('vue-router', async importOriginal => {
  const original = await importOriginal<typeof import('vue-router')>();
  return { ...original, useRouter: () => router, useRoute: () => route };
});

const mastery = { currentDirectAbility: null, profileStatus: 'unassessed' as const, confidenceLevel: 'unassessed' as const };
const setup = {
  goal: { id: 'goal-1', certificationName: '系统架构设计师', syllabusVersionName: '2026 年考试大纲', version: 4 },
  subjects: [{
    id: 'subject-1',
    subjectName: '综合知识',
    nodes: [
      {
        id: 'chapter-1', parentId: null, examSubjectId: 'subject-1', syllabusNumber: '1.1', syllabusTitle: '计算机系统基本知识',
        treeDepth: 1, sortOrder: 1, importance: 3 as const, questionCount: 4, mastery,
        children: [{
          id: 'section-1', parentId: 'chapter-1', examSubjectId: 'subject-1', syllabusNumber: '1.1.1', syllabusTitle: '嵌入式系统及软件',
          treeDepth: 2, sortOrder: 1, importance: 2 as const, questionCount: 4, mastery,
          children: [
            { id: 'point-1', parentId: 'section-1', examSubjectId: 'subject-1', syllabusNumber: '1.1.1.1', syllabusTitle: '嵌入式系统的组成及特点', treeDepth: 3, sortOrder: 1, importance: 2 as const, questionCount: 2, mastery, children: [] },
            { id: 'point-2', parentId: 'section-1', examSubjectId: 'subject-1', syllabusNumber: '1.1.1.2', syllabusTitle: '嵌入式系统分类', treeDepth: 3, sortOrder: 2, importance: 2 as const, questionCount: 0, mastery, children: [] }
          ]
        }]
      },
      {
        id: 'chapter-2', parentId: null, examSubjectId: 'subject-1', syllabusNumber: '1.2', syllabusTitle: '计算机网络',
        treeDepth: 1, sortOrder: 2, importance: 3 as const, questionCount: 1, mastery, children: []
      }
    ]
  }]
};

async function mountPage(query: Record<string, string> = {}) {
  route.query = query;
  const wrapper = mountStudentView(KnowledgePracticePage, {
    global: { stubs: { ElIcon: true, CaretRight: true, Search: true, StarFilled: true } }
  });
  await flushPromises();
  return wrapper;
}

describe('student knowledge practice directory', () => {
  beforeEach(() => {
    api.getSetup.mockReset().mockResolvedValue({ data: setup });
    api.start.mockReset().mockResolvedValue({ data: { sessionId: 'session-1', totalCount: 2, answerPath: '/learning/session/practice?sessionId=session-1' } });
    router.push.mockReset();
    router.replace.mockReset();
    route.query = {};
    vi.stubGlobal('crypto', { randomUUID: () => '0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd' });
  });

  it('initially shows only Chinese chapter labels and reveals sections on demand', async () => {
    const wrapper = await mountPage();
    const directory = wrapper.find('.directory-list');

    expect(directory.findAll('.directory-row')).toHaveLength(2);
    expect(directory.text()).toContain('第一章 计算机系统基本知识');
    expect(directory.text()).toContain('第二章 计算机网络');
    expect(directory.text()).not.toContain('第一节');
    expect(wrapper.find('.detail-hero__actions').exists()).toBe(false);

    await directory.find('button[aria-label="展开第一章 计算机系统基本知识"]').trigger('click');

    expect(directory.findAll('.directory-row')).toHaveLength(3);
    expect(directory.text()).toContain('第一节 嵌入式系统及软件');
    expect(directory.text()).not.toContain('嵌入式系统的组成及特点');
  });

  it('temporarily expands matching sections during search and restores the collapsed directory', async () => {
    const wrapper = await mountPage();
    const directory = wrapper.find('.directory-list');

    (wrapper.vm as unknown as { knowledgeSearch: string }).knowledgeSearch = '嵌入式';
    await nextTick();
    expect(directory.text()).toContain('第一节 嵌入式系统及软件');
    expect(directory.text()).not.toContain('嵌入式系统的组成及特点');

    await directory.find('button[aria-label="收起第一章 计算机系统基本知识"]').trigger('click');
    await directory.findAll('.directory-row__name')[1].trigger('click');

    (wrapper.vm as unknown as { knowledgeSearch: string }).knowledgeSearch = '';
    await nextTick();
    expect(directory.findAll('.directory-row')).toHaveLength(2);
    expect(directory.text()).not.toContain('第一节');
  });

  it('deep-links to a section by expanding its chapter without exposing knowledge points', async () => {
    const wrapper = await mountPage({ knowledgePointId: 'section-1' });
    const directory = wrapper.find('.directory-list');

    expect(directory.text()).toContain('第一节 嵌入式系统及软件');
    expect(directory.text()).not.toContain('1.1.1.1');
    expect(directory.text()).not.toContain('嵌入式系统的组成及特点');
  });

  it('starts sections and small knowledge points with their own node ids and hides point numbers', async () => {
    const wrapper = await mountPage({ knowledgePointId: 'section-1' });

    expect(wrapper.find('.detail-hero').text()).toContain('第一节');
    expect(wrapper.find('.child-summary').text()).toContain('嵌入式系统的组成及特点');
    expect(wrapper.find('.child-summary').text()).not.toContain('1.1.1.1');

    await wrapper.find('.detail-hero__actions button:last-child').trigger('click');
    await flushPromises();
    expect(api.start).toHaveBeenLastCalledWith(
      { knowledgePointId: 'section-1', expectedGoalVersion: 4 },
      '0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd'
    );

    api.start.mockClear();
    const wrapperForPoint = await mountPage({ knowledgePointId: 'section-1' });
    const firstPointStart = wrapperForPoint.findAll('.child-summary__item')[0].findAll('button').at(-1)!;
    await firstPointStart.trigger('click');
    await flushPromises();
    expect(api.start).toHaveBeenLastCalledWith(
      { knowledgePointId: 'point-1', expectedGoalVersion: 4 },
      '0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd'
    );
    expect(wrapperForPoint.findAll('.child-summary__item')[1].findAll('button').at(-1)!.attributes('disabled')).toBeDefined();
  });

  it('isolates inline practice actions and shows loading only on the requested node', async () => {
    let resolveStart!: (value: unknown) => void;
    api.start.mockReturnValue(new Promise(resolve => { resolveStart = resolve; }));
    const wrapper = await mountPage({ knowledgePointId: 'section-1' });
    const pointRows = wrapper.findAll('.child-summary__item');
    const firstStart = pointRows[0].findAll('button').at(-1)!;
    const secondStart = pointRows[1].findAll('button').at(-1)!;

    await firstStart.trigger('click');
    await nextTick();

    expect(wrapper.find('.detail-hero h2').text()).toBe('嵌入式系统及软件');
    expect(firstStart.attributes('data-loading')).toBe('true');
    expect(secondStart.attributes('data-loading')).toBe('false');
    expect(secondStart.attributes('disabled')).toBeDefined();

    resolveStart({ data: { sessionId: 'session-1', totalCount: 2, answerPath: '/learning/session/practice?sessionId=session-1' } });
    await flushPromises();
  });

  it('keeps the selected node and displays a start failure without navigating', async () => {
    api.start.mockRejectedValue(new Error('创建练习失败'));
    const wrapper = await mountPage({ knowledgePointId: 'section-1' });

    await wrapper.findAll('.child-summary__item')[0].findAll('button').at(-1)!.trigger('click');
    await flushPromises();

    expect(wrapper.find('.detail-hero h2').text()).toBe('嵌入式系统及软件');
    expect(wrapper.find('.practice-error').text()).toContain('创建练习失败');
    expect(router.push).not.toHaveBeenCalled();
  });
});
