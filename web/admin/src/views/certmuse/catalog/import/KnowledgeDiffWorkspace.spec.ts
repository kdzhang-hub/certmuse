import { flushPromises, mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import KnowledgeDiffWorkspace from './KnowledgeDiffWorkspace.vue';

const api = vi.hoisted(() => ({
  getKnowledgeDiff: vi.fn(),
  resolveKnowledgeDiff: vi.fn()
}));
const messages = vi.hoisted(() => ({ success: vi.fn() }));
const getHandledRequestError = vi.hoisted(() => vi.fn());

vi.mock('@/api/certmuse/catalog/import', () => ({
  getKnowledgeDiff: api.getKnowledgeDiff,
  resolveKnowledgeDiff: api.resolveKnowledgeDiff
}));
vi.mock('@/utils/request', () => ({ getHandledRequestError }));
vi.mock('element-plus', async importOriginal => {
  const elementPlus = await importOriginal<typeof import('element-plus')>();
  return {
    ...elementPlus,
    ElMessage: { success: messages.success }
  };
});

function diffRow(overrides: Record<string, unknown> = {}) {
  return {
    id: 'diff-1',
    examSubjectId: 'subject-1',
    subjectName: '综合知识',
    action: 'update',
    resolutionStatus: 'pending',
    resolutionDecision: null,
    parentDiffId: null,
    matchScore: 0.875,
    changedFields: ['title'],
    newPoint: { id: 'new-1', syllabusNumber: '1.2', syllabusTitle: '新知识点' },
    oldPoint: { id: 'old-1', syllabusNumber: '1.1', syllabusTitle: '旧知识点' },
    suggestedPoint: { id: 'old-1', syllabusNumber: '1.1', syllabusTitle: '旧知识点' },
    confirmedPoint: null,
    evidence: {
      titleScore: 0.9,
      parentScore: 0.8,
      descriptionScore: 0.7,
      childrenScore: 0.6,
      numberScore: 0.5,
      candidateGap: 0.1,
      possibleMove: true,
      ambiguous: true
    },
    ...overrides
  };
}

function page(rows = [diffRow()]) {
  return {
    rows,
    total: rows.length,
    unchangedCount: 2,
    updateCount: 1,
    moveCount: 0,
    addCount: 1,
    deleteCount: 0,
    pendingCount: 1,
    affectedQuestionCount: 3,
    offlineQuestionCount: 1
  };
}

function mountWorkspace(props: Record<string, unknown> = {}) {
  return mount(KnowledgeDiffWorkspace, {
    props: {
      batchId: 'batch-1',
      syllabusVersionId: 'syllabus-1',
      subjectOptions: [{ id: 'subject-1', label: '综合知识' }],
      ...props
    },
    global: { plugins: [ElementPlus] }
  });
}

describe('knowledge diff workspace', () => {
  beforeEach(() => {
    Object.values(api).forEach(mock => mock.mockReset());
    messages.success.mockReset();
    getHandledRequestError.mockReset();
    getHandledRequestError.mockReturnValue(undefined);
    api.getKnowledgeDiff.mockResolvedValue({ data: page() });
    api.resolveKnowledgeDiff.mockResolvedValue({ data: {} });
  });

  afterEach(() => {
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('loads only reviewable differences and emits the current summary', async () => {
    const wrapper = mountWorkspace();
    await flushPromises();

    expect(api.getKnowledgeDiff).toHaveBeenCalledWith('batch-1', {
      pageNum: 1,
      pageSize: 20,
      excludeUnchanged: true
    });
    expect(wrapper.text()).toContain('知识树差异确认');
    expect(wrapper.text()).toContain('未确认 1');
    expect(wrapper.text()).toContain('综合知识');
    expect(wrapper.text()).toContain('确认修改');
    expect(wrapper.text()).toContain('拒绝修改');
    expect(wrapper.emitted('summaryChange')).toHaveLength(1);

    const vm = wrapper.vm as any;
    vm.query.action = 'move';
    vm.query.pageNum = 4;
    vm.search();
    await flushPromises();
    expect(api.getKnowledgeDiff).toHaveBeenLastCalledWith(
      'batch-1',
      expect.objectContaining({ action: 'move', pageNum: 1, excludeUnchanged: true })
    );

    vm.resetFilters();
    await flushPromises();
    expect(api.getKnowledgeDiff).toHaveBeenLastCalledWith(
      'batch-1',
      expect.objectContaining({ action: undefined, pageNum: 1, excludeUnchanged: true })
    );
    wrapper.unmount();
  });

  it('approves one row with an idempotency key and reloads after the optimistic update', async () => {
    vi.useFakeTimers();
    const wrapper = mountWorkspace();
    await flushPromises();
    const vm = wrapper.vm as any;
    const current = vm.rows[0];

    await vm.resolveRow(current, 'approve');

    expect(api.resolveKnowledgeDiff).toHaveBeenCalledWith(
      'batch-1',
      'diff-1',
      { decision: 'approve', oldKnowledgePointId: null },
      expect.stringMatching(/^[0-9a-f-]{36}$/i)
    );
    expect(current.resolutionDecision).toBe('approve');
    expect(messages.success).toHaveBeenCalledWith('差异确认成功');
    expect(api.getKnowledgeDiff).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(600);
    await flushPromises();
    expect(api.getKnowledgeDiff).toHaveBeenCalledTimes(2);
    wrapper.unmount();
  });

  it('rolls back a failed decision and reuses its request identity for a retry', async () => {
    api.resolveKnowledgeDiff.mockRejectedValue(new Error('网络不可用'));
    const wrapper = mountWorkspace();
    await flushPromises();
    const vm = wrapper.vm as any;
    const current = vm.rows[0];

    await vm.resolveRow(current, 'reject');
    const firstRequestId = api.resolveKnowledgeDiff.mock.calls[0][3];
    expect(current.resolutionDecision).toBeNull();
    expect(vm.errorMessage).toBe('网络不可用');

    await vm.resolveRow(current, 'reject');
    expect(api.resolveKnowledgeDiff.mock.calls[1][3]).toBe(firstRequestId);
    wrapper.unmount();
  });

  it('cascades parent decisions in the UI and locks descendants to the effective decision', async () => {
    vi.useFakeTimers();
    const parent = diffRow({ id: 'parent', action: 'move' });
    const child = diffRow({ id: 'child', parentDiffId: 'parent' });
    const grandchild = diffRow({ id: 'grandchild', parentDiffId: 'child', action: 'add' });
    const sibling = diffRow({ id: 'sibling', action: 'add' });
    api.getKnowledgeDiff.mockResolvedValue({ data: page([parent, child, grandchild, sibling]) });
    const wrapper = mountWorkspace();
    await flushPromises();
    const vm = wrapper.vm as any;

    await vm.resolveRow(vm.rows[0], 'reject');

    expect(vm.rows.map((item: any) => item.resolutionDecision)).toEqual(['reject', 'reject', 'reject', null]);
    expect(vm.lockedByParent(vm.rows[1])).toBe(true);
    expect(vm.lockedByParent(vm.rows[2])).toBe(true);
    expect(vm.effectiveDecision(vm.rows[2])).toBe('reject');
    expect(api.resolveKnowledgeDiff).toHaveBeenCalledTimes(1);
    wrapper.unmount();
  });

  it('uses action-specific labels and honours the disabled editing state', async () => {
    const wrapper = mountWorkspace({ disabled: true });
    await flushPromises();
    const vm = wrapper.vm as any;

    expect(vm.editingDisabled).toBe(true);
    expect(vm.approveLabel('add')).toBe('确认新增');
    expect(vm.approveLabel('delete')).toBe('确认删除');
    expect(vm.approveLabel('move')).toBe('确认移动');
    expect(vm.rejectLabel('add')).toBe('拒绝新增');
    expect(vm.rejectLabel('delete')).toBe('继续保留');
    expect(vm.rejectLabel('move')).toBe('拒绝移动');
    wrapper.unmount();
  });

  it('surfaces baseline and state errors through the documented events', async () => {
    const wrapper = mountWorkspace();
    await flushPromises();
    const vm = wrapper.vm as any;

    getHandledRequestError.mockReturnValue({ responseData: { errorCode: 'KNOWLEDGE_TREE_BASELINE_CHANGED' } });
    vm.handleError(new Error('baseline'));
    expect(wrapper.emitted('baselineChanged')).toHaveLength(1);
    expect(vm.errorMessage).toContain('正式知识树已变化');

    getHandledRequestError.mockReturnValue({ responseData: { errorCode: 'IMPORT_BATCH_STATE_INVALID' } });
    vm.handleError(new Error('state'));
    expect(wrapper.emitted('stateInvalid')).toHaveLength(1);
    expect(vm.errorMessage).toContain('批次状态已变化');
    wrapper.unmount();
  });

  it('resets pagination and refreshes recoverable resolution errors', async () => {
    const wrapper = mountWorkspace();
    await flushPromises();
    const vm = wrapper.vm as any;
    vm.query.pageNum = 5;
    vm.handleSizeChange();
    await flushPromises();
    expect(vm.query.pageNum).toBe(1);
    expect(api.getKnowledgeDiff).toHaveBeenLastCalledWith('batch-1', expect.objectContaining({ pageNum: 1 }));

    getHandledRequestError.mockReturnValue({ responseData: { errorCode: 'KNOWLEDGE_DIFF_MAPPING_CONFLICT' } });
    api.getKnowledgeDiff.mockResolvedValue({ data: page() });
    vm.handleError(new Error('旧知识点已被使用'));
    await flushPromises();
    expect(api.getKnowledgeDiff).toHaveBeenCalled();
    expect(vm.errorMessage).toBe('');

    getHandledRequestError.mockReturnValue({ responseData: { errorCode: 'KNOWLEDGE_DIFF_UNRESOLVED' } });
    vm.handleError(new Error('仍有差异待确认'));
    await flushPromises();
    expect(api.getKnowledgeDiff).toHaveBeenCalled();
    expect(vm.errorMessage).toBe('');
    wrapper.unmount();
  });

  it('reports a malformed query response without emitting a summary', async () => {
    api.getKnowledgeDiff.mockResolvedValue({ data: undefined });
    const wrapper = mountWorkspace();
    await flushPromises();

    expect((wrapper.vm as any).errorMessage).toBe('差异查询响应缺少 data。');
    expect(wrapper.emitted('summaryChange')).toBeUndefined();
    wrapper.unmount();
  });
});
