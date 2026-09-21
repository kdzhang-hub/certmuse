import { flushPromises, mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ReviewPage from './index.vue';

const messages = vi.hoisted(() => ({ confirm: vi.fn(), error: vi.fn(), info: vi.fn(), success: vi.fn(), warning: vi.fn() }));
const questionApi = vi.hoisted(() => ({
  approveQuestionRevision: vi.fn(),
  getQuestionPreview: vi.fn(),
  listQuestions: vi.fn(),
  rejectQuestionRevision: vi.fn()
}));
const collectionApi = vi.hoisted(() => ({
  approveCollectionRevision: vi.fn(),
  getCollectionRevision: vi.fn(),
  listCollections: vi.fn(),
  rejectCollectionRevision: vi.fn()
}));
const catalogApi = vi.hoisted(() => ({ listSyllabusVersions: vi.fn() }));

vi.mock('element-plus', async importOriginal => {
  const elementPlus = await importOriginal<typeof import('element-plus')>();
  return {
    ...elementPlus,
    ElMessageBox: { confirm: messages.confirm },
    ElMessage: { error: messages.error, info: messages.info, success: messages.success, warning: messages.warning }
  };
});
vi.mock('@/api/certmuse/question', () => questionApi);
vi.mock('@/api/certmuse/question/collection', () => collectionApi);
vi.mock('@/api/certmuse/catalog/knowledge', () => catalogApi);
vi.mock('@/components/certmuse/QuestionPreviewDialog.vue', () => ({
  default: {
    props: ['modelValue', 'question', 'loading', 'reviewable', 'processing'],
    emits: ['update:modelValue', 'approve', 'reject'],
    template: '<div class="preview-stub"><button class="preview-close" @click="$emit(\'update:modelValue\', false)">关闭</button><button class="preview-approve" @click="$emit(\'approve\')">通过</button><button class="preview-reject" @click="$emit(\'reject\')">驳回</button></div>'
  }
}));

const question = {
  questionId: 'q1', revisionId: 'r1', revisionNo: 1, questionCode: 'Q001',
  syllabusVersionId: 's1', syllabusVersionName: '2026版', stemSummary: '题干',
  examSubjectId: 'e1', examSubjectName: '综合知识', questionType: 'CHOICE',
  difficulty: 'medium', status: 'pending_review', updatedTime: '2026-08-06T10:00:00+08:00'
};

function mountPage() {
  return mount(ReviewPage, {
    attachTo: document.body,
    global: {
      plugins: [ElementPlus],
      directives: { hasPermi: () => undefined },
      stubs: { Pagination: true, RightToolbar: true }
    }
  });
}

describe('content review page', () => {
  beforeEach(() => {
    Object.values(messages).forEach(mock => mock.mockReset());
    Object.values(questionApi).forEach(mock => mock.mockReset());
    Object.values(collectionApi).forEach(mock => mock.mockReset());
    catalogApi.listSyllabusVersions.mockReset();
    catalogApi.listSyllabusVersions.mockResolvedValue({ data: { rows: [], total: 0 } });
    questionApi.listQuestions.mockResolvedValue({ data: { rows: [question], total: 1 } });
    collectionApi.listCollections.mockResolvedValue({ data: { rows: [], total: 0 } });
    questionApi.approveQuestionRevision.mockResolvedValue({});
    questionApi.rejectQuestionRevision.mockResolvedValue({});
    collectionApi.approveCollectionRevision.mockResolvedValue({});
    collectionApi.rejectCollectionRevision.mockResolvedValue({});
    messages.confirm.mockResolvedValue(undefined);
  });

  it('loads only pending questions and resets filters before reloading', async () => {
    const wrapper = mountPage();
    await flushPromises();
    expect(questionApi.listQuestions).toHaveBeenCalledWith(expect.objectContaining({ status: 'pending_review', pageNum: 1, pageSize: 10 }));

    const vm = wrapper.vm as unknown as {
      questionFilters: { keyword: string; syllabusVersionId: string; questionType: string; difficulty: string };
      resetQuestionFilters: () => void;
    };
    Object.assign(vm.questionFilters, { keyword: '旧值', syllabusVersionId: 's1', questionType: 'CHOICE', difficulty: 'hard' });
    vm.resetQuestionFilters();
    await flushPromises();

    expect(vm.questionFilters).toEqual({ keyword: '', syllabusVersionId: '', questionType: '', difficulty: '' });
    expect(questionApi.listQuestions).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'pending_review', keyword: undefined }));
  });

  it('validates trimmed reject opinions before calling the backend', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as unknown as {
      questionSelection: typeof question[]; reviewOpinion: string; rejectError: string;
      openReject: (kind: 'question') => void; submitReject: () => Promise<void>;
    };
    vm.questionSelection = [question];
    vm.openReject('question');
    vm.reviewOpinion = '   ';
    await vm.submitReject();
    expect(vm.rejectError).toBe('请输入驳回原因');

    vm.reviewOpinion = 'x'.repeat(501);
    await vm.submitReject();
    expect(vm.rejectError).toBe('审核意见不能超过 500 个字符');
    expect(questionApi.rejectQuestionRevision).not.toHaveBeenCalled();
  });

  it('keeps failed reject targets and the dialog open for a safe retry', async () => {
    const failedQuestion = { ...question, questionId: 'q2', revisionId: 'r2', questionCode: 'Q002' };
    questionApi.listQuestions.mockResolvedValue({ data: { rows: [failedQuestion], total: 1 } });
    questionApi.rejectQuestionRevision.mockResolvedValueOnce({}).mockRejectedValueOnce(new Error('conflict'));
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as unknown as {
      questionSelection: typeof question[]; reviewOpinion: string; rejectVisible: boolean;
      rejectTargets: typeof question[]; openReject: (kind: 'question') => void; submitReject: () => Promise<void>;
    };
    vm.questionSelection = [question, failedQuestion];
    vm.openReject('question');
    vm.reviewOpinion = '  依据不足  ';
    await vm.submitReject();
    await flushPromises();

    expect(questionApi.rejectQuestionRevision.mock.calls).toEqual([['r1', '依据不足'], ['r2', '依据不足']]);
    expect(vm.rejectVisible).toBe(true);
    expect(vm.rejectTargets.map(item => item.revisionId)).toEqual(['r2']);
    expect(messages.warning).toHaveBeenCalledWith('处理完成：成功 1 条，失败 1 条；失败项已保留选择');
  });

  it('prevents a second approve batch while one is processing', async () => {
    let resolveApprove: (() => void) | undefined;
    questionApi.approveQuestionRevision.mockImplementation(() => new Promise<void>(resolve => { resolveApprove = resolve; }));
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as unknown as {
      questionSelection: typeof question[]; confirmApprove: (kind: 'question') => Promise<void>;
    };
    vm.questionSelection = [question];
    const first = vm.confirmApprove('question');
    await flushPromises();
    await vm.confirmApprove('question');
    expect(questionApi.approveQuestionRevision).toHaveBeenCalledOnce();
    resolveApprove?.();
    await first;
  });

  it('opens the next refreshed pending question when the preview queue reaches its end', async () => {
    const nextQuestion = { ...question, questionId: 'q2', revisionId: 'r2', questionCode: 'Q002' };
    questionApi.getQuestionPreview.mockResolvedValue({ data: { questionId: 'q2', revisionId: 'r2' } });
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as unknown as {
      previewQueue: typeof question[]; previewQueueIndex: number; questions: typeof question[];
      finishReviewSuccess: (action: 'next-preview-question') => void;
    };
    vm.previewQueue = [question];
    vm.previewQueueIndex = 0;
    vm.questions = [nextQuestion];
    vm.finishReviewSuccess('next-preview-question');
    await flushPromises();

    expect(questionApi.getQuestionPreview).toHaveBeenCalledWith('q2', 'r2');
  });

  it('loads collection reviews when switching tabs and preserves filter contracts', async () => {
    collectionApi.listCollections.mockResolvedValue({ data: { rows: [{ revisionId: 'cr1', collectionCode: 'C001', collectionName: '专项练习', collectionType: 'PRACTICE', certificationName: '架构', questionCount: 2, totalReportScore: 20, updatedTime: '2026-08-08T10:00:00+08:00', status: 'pending_review' }], total: 1 } });
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;
    vm.activeTab = 'collection';
    await flushPromises();
    expect(collectionApi.listCollections).toHaveBeenCalledWith(expect.objectContaining({ status: 'pending_review', pageNum: 1, pageSize: 10 }));
    expect(vm.collectionTypeLabel('SIMULATION')).toBe('模拟试卷');
    vm.collectionFilters.keyword = '  旧值 ';
    vm.collectionFilters.collectionType = 'PRACTICE';
    vm.resetCollectionFilters();
    await flushPromises();
    expect(vm.collectionFilters).toEqual({ keyword: '', collectionType: '', syllabusVersionId: '' });
  });

  it('validates mixed decisions and submits only after every rejection has an opinion', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;
    vm.questionSelection = [question];
    vm.openMixedReview();
    expect(vm.mixedReviewVisible).toBe(true);
    vm.mixedReviewRows[0].decision = 'reject';
    await vm.submitMixedReview();
    expect(messages.warning).toHaveBeenCalledWith('请填写题目 Q001 的驳回原因');
    vm.mixedReviewRows[0].opinion = '依据不足';
    questionApi.rejectQuestionRevision.mockResolvedValue({});
    await vm.submitMixedReview();
    await flushPromises();
    expect(questionApi.rejectQuestionRevision).toHaveBeenCalledWith('r1', '依据不足');
    expect(messages.success).toHaveBeenCalledWith('处理完成，共成功 1 条');
  });

  it('supports reviewing all pending questions across pages and reports collection blockers', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;
    questionApi.listQuestions.mockImplementation(async (params: any) => params.pageNum === 1
      ? { data: { rows: [question], total: 101 } }
      : { data: { rows: [{ ...question, revisionId: 'r2', questionId: 'q2', questionCode: 'Q002' }], total: 101 } });
    await vm.openAllMixedReview();
    expect(questionApi.listQuestions).toHaveBeenCalledWith(expect.objectContaining({ pageNum: 2, pageSize: 100, status: 'pending_review' }));
    expect(vm.mixedReviewRows).toHaveLength(2);

    collectionApi.listCollections.mockResolvedValue({ data: { rows: [], total: 0 } });
    await vm.finishBatch('collection', ['cr1'], 0, 1, [{ reason: { response: { data: { data: { blockingIssues: [{ itemOrder: 2, message: '题目未发布' }] } } } } }]);
    expect(messages.error).toHaveBeenCalledWith('第2题：题目未发布');
  });

  it('loads a collection detail and previews one of its pending questions', async () => {
    const item = {
      itemOrder: 1, questionId: 'q1', questionRevisionId: 'r1', questionCode: 'Q001', questionStatus: 'pending_review'
    };
    collectionApi.getCollectionRevision.mockResolvedValue({ data: {
      revisionId: 'cr1', collectionId: 'c1', collectionCode: 'C001', collectionName: '专项练习',
      collectionType: 'PRACTICE', status: 'pending_review', items: [item]
    } });
    questionApi.getQuestionPreview.mockResolvedValue({ data: { questionId: 'q1', revisionId: 'r1' } });
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;

    await vm.showCollection({ revisionId: 'cr1', collectionCode: 'C001' });
    expect(vm.collectionDetailVisible).toBe(true);
    expect(vm.collectionDetail.revisionId).toBe('cr1');
    expect(vm.collectionDetailLoading).toBe(false);

    vm.previewCollectionQuestion(item);
    await flushPromises();
    expect(questionApi.getQuestionPreview).toHaveBeenCalledWith('q1', 'r1');
    vm.finishReviewSuccess('close-collection-detail');
    expect(vm.collectionDetailVisible).toBe(false);
    wrapper.unmount();
  });

  it('approves a selected collection revision through the collection endpoint', async () => {
    collectionApi.approveCollectionRevision.mockResolvedValue({});
    collectionApi.listCollections.mockResolvedValue({ data: { rows: [], total: 0 } });
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;
    vm.collectionSelection = [{ revisionId: 'cr1', collectionCode: 'C001' }];

    await vm.confirmApprove('collection');
    await flushPromises();

    expect(collectionApi.approveCollectionRevision).toHaveBeenCalledWith('cr1');
    expect(collectionApi.listCollections).toHaveBeenCalledWith(expect.objectContaining({ status: 'pending_review' }));
    expect(messages.success).toHaveBeenCalledWith('处理完成，共成功 1 条');
    wrapper.unmount();
  });

  it('blocks an overlong mixed rejection and reports an empty all-review scope', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;
    vm.questionSelection = [question];
    vm.openMixedReview();
    vm.mixedReviewRows[0].decision = 'reject';
    vm.mixedReviewRows[0].opinion = 'x'.repeat(501);
    await vm.submitMixedReview();
    expect(messages.warning).toHaveBeenCalledWith('题目 Q001 的驳回原因不能超过 500 个字符');
    expect(questionApi.rejectQuestionRevision).not.toHaveBeenCalled();

    questionApi.listQuestions.mockResolvedValue({ data: { rows: [], total: 0 } });
    await vm.openAllMixedReview();
    expect(messages.info).toHaveBeenCalledWith('当前范围内没有审核中题目');
    wrapper.unmount();
  });

  it('rejects a question from the rendered preview only after an opinion is entered', async () => {
    questionApi.getQuestionPreview.mockResolvedValue({ data: { questionId: 'q1', revisionId: 'r1' } });
    const wrapper = mountPage();
    await flushPromises();

    const questionTable = wrapper
      .findAllComponents({ name: 'ElTable' })
      .find(table => (table.props('data') as Array<{ revisionId?: string }> | undefined)?.[0]?.revisionId === 'r1');
    const viewButton = questionTable!.findAll('button').find(button => button.text() === '查看');
    await viewButton!.trigger('click');
    await flushPromises();
    expect(questionApi.getQuestionPreview).toHaveBeenCalledWith('q1', 'r1');

    await wrapper.find('.preview-reject').trigger('click');
    await flushPromises();
    const confirmReject = wrapper.findAll('button').find(button => button.text() === '确认驳回');
    await confirmReject!.trigger('click');
    await flushPromises();
    const opinionItem = wrapper
      .findAllComponents({ name: 'ElFormItem' })
      .find(item => item.props('label') === '审核意见');
    expect(opinionItem?.props('error')).toBe('请输入驳回原因');
    expect(questionApi.rejectQuestionRevision).not.toHaveBeenCalled();

    await wrapper.find('textarea[placeholder="请输入驳回原因"]').setValue('  题干依据不足  ');
    await confirmReject!.trigger('click');
    await flushPromises();
    expect(questionApi.rejectQuestionRevision).toHaveBeenCalledWith('r1', '题干依据不足');
    wrapper.unmount();
  });

  it('submits a rendered mixed review decision', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const questionTable = wrapper.findAllComponents({ name: 'ElTable' })[0];
    questionTable.vm.$emit('selection-change', [question]);
    await flushPromises();
    const openMixed = wrapper.findAll('button').find(button => button.text().includes('审核已选'));
    await openMixed!.trigger('click');
    await flushPromises();

    const mixedTable = wrapper
      .findAllComponents({ name: 'ElTable' })
      .find(table => (table.props('data') as Array<{ decision?: string }> | undefined)?.[0]?.decision === 'approve');
    mixedTable!.findComponent({ name: 'ElSelect' }).vm.$emit('update:modelValue', 'reject');
    await flushPromises();
    mixedTable!.findComponent({ name: 'ElInput' }).vm.$emit('update:modelValue', '逐项复核未通过');
    await flushPromises();
    expect(wrapper.text()).toContain('通过 0 道，驳回 1 道');

    await wrapper.findAll('button').find(button => button.text() === '确认提交')!.trigger('click');
    await flushPromises();
    expect(questionApi.rejectQuestionRevision).toHaveBeenCalledWith('r1', '逐项复核未通过');
    wrapper.unmount();
  });

  it('filters, previews, approves, and reviews a collection through visible controls', async () => {
    const collection = {
      revisionId: 'cr1', collectionId: 'c1', collectionCode: 'C001', collectionName: '专项练习',
      collectionType: 'PRACTICE', certificationName: '系统架构设计师', questionCount: 2,
      totalReportScore: 20, updatedTime: '2026-08-08T10:00:00+08:00', status: 'pending_review'
    };
    catalogApi.listSyllabusVersions.mockResolvedValue({
      data: { rows: [{ id: 's1', certificationId: 'c1', certificationName: '系统架构设计师' }], total: 1 }
    });
    collectionApi.listCollections.mockResolvedValue({ data: { rows: [collection], total: 1 } });
    collectionApi.getCollectionRevision.mockResolvedValue({
      data: {
        revisionId: 'cr1', collectionId: 'c1', collectionCode: 'C001', collectionName: '专项练习',
        certificationName: '系统架构设计师', revisionNo: 1, durationMinutes: 60, questionCount: 1,
        totalReportScore: 10, status: 'pending_review',
        items: [{
          itemOrder: 1, questionId: 'q1', questionRevisionId: 'r1', questionCode: 'Q001', stem: '题干',
          examSubjectName: '综合知识', reportScore: 10, questionStatus: 'pending_review'
        }]
      }
    });
    questionApi.getQuestionPreview.mockResolvedValue({ data: { questionId: 'q1', revisionId: 'r1' } });

    const wrapper = mountPage();
    await flushPromises();
    const tabs = wrapper.findComponent({ name: 'ElTabs' });
    tabs.vm.$emit('update:modelValue', 'collection');
    await flushPromises();
    expect(collectionApi.listCollections).toHaveBeenCalledWith(expect.objectContaining({ status: 'pending_review' }));

    const collectionType = wrapper
      .findAllComponents({ name: 'ElSelect' })
      .find(component => component.props('placeholder') === '全部类型');
    const qualification = wrapper
      .findAllComponents({ name: 'ElSelect' })
      .filter(component => component.props('placeholder') === '全部资格')
      .at(-1);
    expect(collectionType).toBeDefined();
    expect(qualification).toBeDefined();
    collectionType!.vm.$emit('update:modelValue', 'PRACTICE');
    collectionType!.vm.$emit('change', 'PRACTICE');
    qualification!.vm.$emit('update:modelValue', 's1');
    qualification!.vm.$emit('change', 's1');
    const searchButtons = wrapper.findAll('button').filter(button => button.text().trim() === '搜索');
    await searchButtons.at(-1)!.trigger('click');
    await flushPromises();
    expect(collectionApi.listCollections).toHaveBeenLastCalledWith(expect.objectContaining({
      collectionType: 'PRACTICE', syllabusVersionId: 's1'
    }));

    const collectionTable = wrapper
      .findAllComponents({ name: 'ElTable' })
      .find(table => (table.props('data') as Array<{ revisionId?: string }> | undefined)?.[0]?.revisionId === 'cr1');
    expect(collectionTable).toBeDefined();
    const view = collectionTable!.findAll('button').find(button => button.text().trim() === '查看');
    await view!.trigger('click');
    await flushPromises();
    expect(document.body.textContent).toContain('题集审核详情');
    expect(document.body.textContent).toContain('专项练习');
    const detailTable = wrapper
      .findAllComponents({ name: 'ElTable' })
      .find(table => (table.props('data') as Array<{ questionRevisionId?: string }> | undefined)?.[0]?.questionRevisionId === 'r1');
    const preview = detailTable!.findAll('button').find(button => button.text().trim() === '查看');
    await preview!.trigger('click');
    await flushPromises();
    expect(questionApi.getQuestionPreview).toHaveBeenCalledWith('q1', 'r1');
    await wrapper.get('.preview-approve').trigger('click');
    await flushPromises();
    expect(questionApi.approveQuestionRevision).toHaveBeenCalledWith('r1');

    const collectionTableAfterRefresh = wrapper
      .findAllComponents({ name: 'ElTable' })
      .find(table => (table.props('data') as Array<{ revisionId?: string }> | undefined)?.[0]?.revisionId === 'cr1');
    collectionTableAfterRefresh!.vm.$emit('selection-change', [collection]);
    await flushPromises();
    const batchApprove = wrapper.findAll('button').find(button => button.text().trim() === '批量通过');
    await batchApprove!.trigger('click');
    await flushPromises();
    expect(collectionApi.approveCollectionRevision).toHaveBeenCalledWith('cr1');
    wrapper.unmount();
  });

  it('closes a reviewed collection detail only after its approve or reject operation completes', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;
    vm.collectionDetail = { revisionId: 'cr1', status: 'pending_review', collectionName: '架构专项练习' };
    vm.collectionDetailVisible = true;

    await vm.approveCollectionDetail();
    await flushPromises();
    expect(collectionApi.approveCollectionRevision).toHaveBeenCalledWith('cr1');
    expect(vm.collectionDetailVisible).toBe(false);

    vm.collectionDetailVisible = true;
    vm.rejectCollectionDetail();
    expect(vm.rejectVisible).toBe(true);
    expect(vm.rejectKind).toBe('collection');
    vm.reviewOpinion = '题集内仍有待修订题目';
    await vm.submitReject();
    await flushPromises();
    expect(collectionApi.rejectCollectionRevision).toHaveBeenCalledWith('cr1', '题集内仍有待修订题目');
    expect(vm.rejectVisible).toBe(false);
    expect(vm.collectionDetailVisible).toBe(false);

    vm.collectionDetail = undefined;
    vm.approveCollectionDetail();
    vm.rejectCollectionDetail();
    expect(collectionApi.approveCollectionRevision).toHaveBeenCalledTimes(1);
    expect(collectionApi.rejectCollectionRevision).toHaveBeenCalledTimes(1);
    wrapper.unmount();
  });
});
