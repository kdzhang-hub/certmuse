import ElementPlus from 'element-plus';
import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import CollectionPage from './index.vue';

const collectionApi = vi.hoisted(() => ({
  createCollectionRevision: vi.fn(),
  deleteCollectionDraft: vi.fn(),
  getCollectionRevision: vi.fn(),
  listCollectionManagements: vi.fn(),
  renameCollection: vi.fn(),
  submitCollectionReview: vi.fn(),
  takeCollectionOffline: vi.fn()
}));
const listQualifications = vi.hoisted(() => vi.fn());
const router = vi.hoisted(() => ({ push: vi.fn() }));
const messages = vi.hoisted(() => ({ confirm: vi.fn(), error: vi.fn(), success: vi.fn(), warning: vi.fn() }));

vi.mock('@/api/certmuse/question/collection', () => collectionApi);
vi.mock('@/api/certmuse/catalog/subject-version', () => ({ listQualifications }));
vi.mock('vue-router', () => ({ useRouter: () => router }));
vi.mock('./PaperImportDialog.vue', () => ({
  default: {
    name: 'PaperImportDialog',
    emits: ['success', 'update:modelValue'],
    template: `
      <div class="paper-import-stub">
        <button class="paper-import-close" @click="$emit('update:modelValue', false)">关闭导入</button>
        <button class="paper-import-success" @click="$emit('success', { collectionId: 'collection-imported', revisionId: 'revision-imported', validCount: 3 })">导入成功</button>
      </div>`
  }
}));
vi.mock('element-plus', async importOriginal => {
  const elementPlus = await importOriginal<typeof import('element-plus')>();
  return {
    ...elementPlus,
    ElMessage: { error: messages.error, success: messages.success, warning: messages.warning },
    ElMessageBox: { confirm: messages.confirm }
  };
});

const manageRow = {
  collectionId: 'collection-1',
  collectionCode: 'COL-001',
  collectionName: '架构专项练习',
  collectionType: 'PRACTICE',
  certificationId: 'cert-1',
  certificationName: '系统架构设计师',
  syllabusVersionId: 'syllabus-1',
  syllabusVersionName: '2026版',
  currentPublishedRevisionId: 'revision-2',
  currentPublishedRevisionNo: 2,
  updatedTime: '2026-08-08T10:00:00+08:00',
  revisions: [
    {
      revisionId: 'revision-1', revisionNo: 1, status: 'rejected', currentPublished: false, rowVersion: '3',
      questionCount: 8, totalReportScore: 80, hasReviewOpinion: true, updatedTime: '2026-08-07T10:00:00+08:00'
    },
    {
      revisionId: 'revision-2', revisionNo: 2, status: 'published', currentPublished: true, rowVersion: '4',
      questionCount: 10, totalReportScore: 100, hasReviewOpinion: false, updatedTime: '2026-08-08T10:00:00+08:00'
    }
  ]
};

const revisionDetail = {
  revisionId: 'revision-1', collectionId: 'collection-1', collectionCode: 'COL-001', revisionNo: 1,
  collectionName: '架构专项练习', collectionType: 'PRACTICE', certificationId: 'cert-1',
  certificationName: '系统架构设计师', syllabusVersionId: 'syllabus-1', syllabusVersionName: '2026版',
  durationMinutes: 60, status: 'rejected', questionCount: 2, totalReportScore: 20, rowVersion: '3',
  reviewOpinion: '题目 Q-001（修订内容不足）', updatedTime: '2026-08-07T10:00:00+08:00',
  items: [
    { itemOrder: 1, questionRevisionId: 'qr-1', questionId: 'q-1', questionCode: 'Q-001', stem: '题干', questionType: 'CHOICE', difficulty: 'easy', questionStatus: 'rejected', examSubjectId: 's-1', examSubjectName: '综合知识', knowledgePointId: 'kp-1', knowledgePointLabel: '架构', reportScore: 10 },
    { itemOrder: 2, questionRevisionId: 'qr-2', questionId: 'q-2', questionCode: 'Q-002', stem: '题干二', questionType: 'ESSAY', difficulty: 'hard', questionStatus: 'draft', examSubjectId: 's-1', examSubjectName: '综合知识', knowledgePointId: null, knowledgePointLabel: null, reportScore: 10 }
  ]
};

function mountPage() {
  return mount(CollectionPage, {
    attachTo: document.body,
    global: {
      plugins: [ElementPlus],
      directives: { hasPermi: () => undefined },
      stubs: { Pagination: true }
    }
  });
}

describe('collection management page', () => {
  beforeEach(() => {
    Object.values(collectionApi).forEach(mock => mock.mockReset());
    listQualifications.mockReset();
    router.push.mockReset();
    Object.values(messages).forEach(mock => mock.mockReset());
    listQualifications.mockResolvedValue({ data: { rows: [{ id: 'cert-1', certificationName: '系统架构设计师' }] } });
    collectionApi.listCollectionManagements.mockResolvedValue({ data: { rows: [manageRow], total: 1 } });
    collectionApi.renameCollection.mockResolvedValue({ data: { collectionId: 'collection-1', collectionName: '新名称' } });
    collectionApi.getCollectionRevision.mockResolvedValue({ data: revisionDetail });
    collectionApi.createCollectionRevision.mockResolvedValue({ data: { revisionNo: 3 } });
    collectionApi.deleteCollectionDraft.mockResolvedValue({});
    collectionApi.submitCollectionReview.mockResolvedValue({});
    collectionApi.takeCollectionOffline.mockResolvedValue({});
    messages.confirm.mockResolvedValue(undefined);
  });

  it('loads management rows, maps revisions and applies display filters', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;

    expect(listQualifications).toHaveBeenCalledWith({ pageNum: 1, pageSize: 100, status: '0' });
    expect(collectionApi.listCollectionManagements).toHaveBeenCalledWith({
      keyword: undefined, collectionType: undefined, certificationId: undefined, statuses: [], pageNum: 1, pageSize: 100
    });
    expect(vm.collections[0]).toMatchObject({ id: 'collection-1', name: '架构专项练习', currentRevisionNo: 2 });
    expect(vm.tableRows[0].children.map((item: any) => item.status)).toEqual(['rejected', 'published']);
    expect(vm.typeLabel('SIMULATION')).toBe('模拟试卷');
    expect(vm.questionTypeLabel('UNKNOWN')).toBe('UNKNOWN');

    vm.filters.keyword = '  架构  ';
    vm.filters.statuses = ['published'];
    await wrapper.vm.$nextTick();
    expect(vm.filteredCollections).toHaveLength(1);
    expect(vm.tableRows[0].children.map((item: any) => item.status)).toEqual(['published']);
    vm.resetFilters();
    await flushPromises();
    expect(vm.filters).toEqual({ keyword: '', type: '', certification: '', statuses: [] });
  });

  it('renames only the collection parent and reloads the list once', async () => {
    collectionApi.listCollectionManagements.mockResolvedValue({
      data: {
        rows: [{
          ...manageRow,
          revisions: [
            ...manageRow.revisions,
            {
              revisionId: 'revision-3', revisionNo: 3, status: 'draft', currentPublished: false, rowVersion: '0',
              questionCount: 10, totalReportScore: 100, hasReviewOpinion: false, updatedTime: '2026-08-09T10:00:00+08:00'
            }
          ]
        }],
        total: 1
      }
    });
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;
    const collection = vm.tableRows[0];

    vm.openRename(collection);
    await wrapper.vm.$nextTick();
    expect(vm.renameForm).toMatchObject({ collectionId: 'collection-1', collectionName: '架构专项练习' });

    vm.renameForm.collectionName = '  新父题集名称  ';
    await vm.submitRename();
    await flushPromises();

    expect(collectionApi.renameCollection).toHaveBeenCalledWith('collection-1', { collectionName: '新父题集名称' });
    expect(collectionApi.listCollectionManagements).toHaveBeenCalledTimes(2);
    expect(messages.success).toHaveBeenCalledWith('题集名称已更新');
    expect(collection.children.map((revision: any) => revision.revisionNo)).toHaveLength(3);
    expect(collection.children.map((revision: any) => revision.revisionNo)).toEqual(expect.arrayContaining([1, 2, 3]));
  });

  it.each(['pending_review', 'published', 'rejected'] as const)(
    'opens a read-only rename dialog when the latest available state is %s',
    async status => {
      collectionApi.listCollectionManagements.mockResolvedValue({
        data: {
          rows: [{
            ...manageRow,
            currentPublishedRevisionId: status === 'published' ? 'revision-2' : null,
            currentPublishedRevisionNo: status === 'published' ? 2 : null,
            revisions: [{
              ...manageRow.revisions[1],
              status,
              currentPublished: status === 'published'
            }]
          }],
          total: 1
        }
      });
      const wrapper = mountPage();
      await flushPromises();
      const vm = wrapper.vm as any;

      expect(wrapper.find('button[aria-label="编辑题集名称"]').exists()).toBe(true);
      vm.openRename(vm.tableRows[0]);
      await wrapper.vm.$nextTick();

      expect(vm.renameVisible).toBe(true);
      expect(vm.renameEditable).toBe(false);

      await vm.submitRename();
      expect(messages.warning).toHaveBeenCalledWith('只有草稿状态的题集可以修改名称');
      expect(collectionApi.renameCollection).not.toHaveBeenCalled();
      wrapper.unmount();
    }
  );

  it('refreshes once after cache activation without browser navigation', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;

    vm.markRefreshOnActivation();
    await vm.refreshAfterActivation();
    await vm.refreshAfterActivation();

    expect(collectionApi.listCollectionManagements).toHaveBeenCalledTimes(2);
    expect(router.push).not.toHaveBeenCalled();
  });

  it('protects published revisions from deletion and deletes editable revisions after confirmation', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;
    const collection = vm.tableRows[0];
    const published = collection.children.find((item: any) => item.status === 'published');
    vm.selectedRows = [published];
    vm.confirmDeleteSelected();
    expect(messages.warning).toHaveBeenCalledWith('选中的题集包含已发布或审核中版本，不能整体删除；请仅选择草稿或已驳回版本。');
    expect(messages.confirm).not.toHaveBeenCalled();

    const rejected = collection.children.find((item: any) => item.status === 'rejected');
    vm.selectedRows = [rejected];
    vm.confirmDeleteSelected();
    await flushPromises();
    expect(messages.confirm).toHaveBeenCalledWith('确认永久删除选中的 1 个草稿或已驳回版本吗？', '批量删除题集', expect.objectContaining({ type: 'warning' }));
    expect(collectionApi.deleteCollectionDraft).toHaveBeenCalledWith('revision-1');
    expect(messages.success).toHaveBeenCalledWith('已删除 1 个草稿或已驳回版本');
  });

  it('submits, takes offline and creates a new revision through guarded actions', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;
    const collection = vm.tableRows[0];
    const published = collection.children.find((item: any) => item.status === 'published');

    vm.submitReview(published);
    await flushPromises();
    expect(collectionApi.submitCollectionReview).toHaveBeenCalledWith('revision-2');
    expect(messages.success).toHaveBeenCalledWith('V2 已提交审核');

    vm.takeOffline(published);
    await flushPromises();
    expect(collectionApi.takeCollectionOffline).toHaveBeenCalledWith('revision-2');
    expect(messages.success).toHaveBeenCalledWith('题集 V2 已下架并退回草稿');

    const draftCollection = { ...collection, revisions: [{ ...published, id: 'revision-3', revisionNo: 3, status: 'draft', currentPublished: false }] };
    vm.createRevision(draftCollection, published);
    await flushPromises();
    expect(collectionApi.createCollectionRevision).toHaveBeenCalledWith('collection-1', 'revision-2');
    expect(messages.success).toHaveBeenCalledWith('草稿 V3 已创建');
    expect(vm.createRevisionTip(draftCollection)).toContain('尚无已发布版本');
  });

  it('loads revision details and formats rejection and knowledge summaries', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;
    const row = vm.tableRows[0].children[0];
    await vm.showRevisionDetail(row);
    await flushPromises();

    expect(collectionApi.getCollectionRevision).toHaveBeenCalledWith('revision-1');
    expect(vm.revisionDetailVisible).toBe(true);
    expect(vm.collectionRejectionMessage).toBe('第 1 题（Q-001）被驳回');
    expect(vm.revisionKnowledgeCount).toBe(1);
    expect(vm.revisionDifficultyCount('easy')).toBe(1);
    expect(vm.revisionQuestionTypeCount('CHOICE')).toBe(1);
    expect(vm.questionTypeLabel('CASE')).toBe('案例题');

    vm.handleSubmitReviewError({ response: { data: { data: { blockingIssues: [{ itemOrder: 1, message: '缺少知识点' }, { itemOrder: null, message: '题集为空' }] } } } });
    expect(messages.error).toHaveBeenCalledWith('第1题：缺少知识点；题集为空');
    vm.handleActionError('cancel', '不会显示');
    expect(messages.error).toHaveBeenCalledTimes(1);
  });

  it('handles imported papers, edit navigation and failed revision detail loads', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;
    const collection = vm.tableRows[0];
    const rejected = collection.children.find((item: any) => item.status === 'rejected');

    vm.openImportedPaper({ collectionId: '', revisionId: '', validCount: 2 });
    await flushPromises();
    expect(router.push).not.toHaveBeenCalled();

    vm.openImportedPaper({ collectionId: 'collection-2', revisionId: 'revision-8', validCount: 4 });
    await flushPromises();
    expect(messages.success).toHaveBeenCalledWith('试卷导入完成，已创建 4 道草稿题目和题集草稿');
    expect(router.push).toHaveBeenCalledWith({
      path: '/content/collections/new', query: { id: 'collection-2', revisionId: 'revision-8' }
    });

    vm.editRevision(rejected);
    expect(router.push).toHaveBeenCalledWith({
      path: '/content/collections/new', query: { id: 'collection-1', revisionId: 'revision-1' }
    });

    collectionApi.getCollectionRevision.mockRejectedValueOnce(new Error('revision unavailable'));
    await vm.showRevisionDetail(rejected);
    expect(messages.error).toHaveBeenCalledWith('revision unavailable');
    expect(vm.revisionDetailLoading).toBe(false);
  });

  it('blocks collection-level deletion and revision creation while work is active', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;
    const collection = vm.tableRows[0];
    const published = collection.children.find((item: any) => item.status === 'published');

    vm.deleteCollection(collection);
    expect(messages.warning).toHaveBeenCalledWith('选中的题集包含已发布或审核中版本，不能整体删除；请仅选择草稿或已驳回版本。');

    messages.confirm.mockReset();
    const activeCollection = {
      ...collection,
      revisions: collection.revisions.map((revision: any) =>
        revision.status === 'rejected' ? { ...revision, status: 'pending_review' } : revision
      )
    };
    vm.createRevision(activeCollection, published);
    expect(messages.confirm).not.toHaveBeenCalled();
    expect(vm.createRevisionTip(activeCollection)).toContain('审核中');
  });

  it('tracks selection, opens collection detail and reports deletion failures', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;
    const collection = vm.tableRows[0];

    vm.handleSelectionChange([collection]);
    expect(vm.selectedRows).toEqual([collection]);

    vm.showCollectionDetail(collection);
    expect(vm.activeCollection).toMatchObject({ id: 'collection-1', rowType: 'collection' });
    expect(vm.collectionDetailVisible).toBe(true);

    const deletableCollection = {
      ...collection,
      revisions: collection.revisions.map((revision: any) => ({
        ...revision,
        status: 'draft',
        currentPublished: false
      }))
    };
    collectionApi.deleteCollectionDraft.mockRejectedValueOnce(new Error('删除冲突'));
    vm.deleteCollection(deletableCollection);
    await flushPromises();
    expect(collectionApi.deleteCollectionDraft).toHaveBeenCalledWith('revision-1');
    expect(messages.error).toHaveBeenCalledWith('删除冲突');

    const rejected = collection.children.find((item: any) => item.status === 'rejected');
    collectionApi.deleteCollectionDraft.mockRejectedValueOnce(new Error('版本删除失败'));
    vm.deleteDraft(rejected);
    await flushPromises();
    expect(messages.error).toHaveBeenCalledWith('版本删除失败');
  });

  it('runs rendered search, details, revisions, deletion, and import workflows', async () => {
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.get('[aria-label="切换题集筛选"]').trigger('click');
    expect(wrapper.find('.search-panel').classes()).toContain('is-collapsed');

    await wrapper.get('input[placeholder="题集名称或编码"]').setValue('  架构  ');
    const select = (placeholder: string) =>
      wrapper.findAllComponents({ name: 'ElSelect' }).find(component => component.props('placeholder') === placeholder)!;
    select('全部类型').vm.$emit('update:modelValue', 'PRACTICE');
    select('全部资格').vm.$emit('update:modelValue', 'cert-1');
    select('全部状态').vm.$emit('update:modelValue', ['rejected']);
    await wrapper.findAll('button').find(button => button.text().trim() === '搜索')!.trigger('click');
    await flushPromises();
    expect(collectionApi.listCollectionManagements).toHaveBeenLastCalledWith({
      keyword: '架构', collectionType: 'PRACTICE', certificationId: 'cert-1', statuses: ['rejected'], pageNum: 1, pageSize: 100
    });

    const table = wrapper.findComponent({ name: 'ElTable' });
    const rows = table.props('data') as Array<{ rowType: string; id?: string }>;
    const collectionRow = rows.find(row => row.rowType === 'collection')!;
    const revisionRow = rows.find(row => row.rowType === 'revision')!;
    table.vm.$emit('selection-change', [revisionRow]);
    await flushPromises();
    expect(wrapper.findAll('button').find(button => button.text().trim() === '删除')?.attributes('disabled')).toBeUndefined();

    const collectionLink = wrapper.findAll('a').find(link => link.text().trim() === '架构专项练习');
    expect(collectionLink).toBeDefined();
    await collectionLink!.trigger('click');
    await flushPromises();
    expect(document.body.textContent).toContain('当前生效版本');

    const tooltip = (content: string, occurrence = 0) =>
      wrapper.findAllComponents({ name: 'ElTooltip' }).filter(component => component.props('content') === content)[occurrence]!;
    await tooltip('查看修订').find('button').trigger('click');
    await flushPromises();
    expect(collectionApi.getCollectionRevision).toHaveBeenCalledWith('revision-1');
    expect(document.body.textContent).toContain('第 1 题（Q-001）被驳回');

    await tooltip('编辑题集').find('button').trigger('click');
    expect(router.push).toHaveBeenCalledWith({
      path: '/content/collections/new', query: { id: 'collection-1', revisionId: 'revision-1' }
    });
    await tooltip('提交审核').find('button').trigger('click');
    await flushPromises();
    expect(collectionApi.submitCollectionReview).toHaveBeenCalledWith('revision-1');
    await tooltip('删除题集').find('button').trigger('click');
    await flushPromises();
    expect(collectionApi.deleteCollectionDraft).toHaveBeenCalledWith('revision-1');

    await wrapper.findAll('button').find(button => button.text().trim() === '新增题集')!.trigger('click');
    expect(router.push).toHaveBeenCalledWith('/content/collections/new');
    await wrapper.findAll('button').find(button => button.text().trim() === '导入试卷')!.trigger('click');
    await wrapper.get('.paper-import-close').trigger('click');
    await wrapper.get('.paper-import-success').trigger('click');
    await flushPromises();
    expect(messages.success).toHaveBeenCalledWith('试卷导入完成，已创建 3 道草稿题目和题集草稿');
    expect(router.push).toHaveBeenCalledWith({
      path: '/content/collections/new', query: { id: 'collection-imported', revisionId: 'revision-imported' }
    });
    wrapper.unmount();
  });

  it('shows empty and failed list states through the rendered search workflow', async () => {
    collectionApi.listCollectionManagements.mockRejectedValueOnce(new Error('网络暂不可用'));
    listQualifications.mockRejectedValueOnce(new Error('资格服务暂不可用'));
    const wrapper = mountPage();
    await flushPromises();
    expect(messages.error).toHaveBeenCalledWith('资格服务暂不可用');
    expect(messages.error).toHaveBeenCalledWith('网络暂不可用');

    collectionApi.listCollectionManagements.mockResolvedValueOnce({ data: { rows: [], total: 0 } });
    await wrapper.findAll('button').find(button => button.text().trim() === '搜索')!.trigger('click');
    await flushPromises();
    expect((wrapper.findComponent({ name: 'ElTable' }).props('data') as unknown[])).toHaveLength(0);
    wrapper.unmount();
  });

  it('keeps an empty row selection inert and reports user cancellation for rendered revision actions', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const table = wrapper.findComponent({ name: 'ElTable' });
    table.vm.$emit('selection-change', []);
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text().trim() === '删除')!.trigger('click');
    expect(messages.confirm).not.toHaveBeenCalled();

    const tooltip = (content: string) =>
      wrapper.findAllComponents({ name: 'ElTooltip' }).find(component => component.props('content') === content)!;
    messages.confirm.mockRejectedValueOnce('cancel');
    await tooltip('提交审核').find('button').trigger('click');
    await flushPromises();
    expect(collectionApi.submitCollectionReview).not.toHaveBeenCalled();

    messages.confirm.mockRejectedValueOnce('close');
    await tooltip('删除题集').find('button').trigger('click');
    await flushPromises();
    expect(collectionApi.deleteCollectionDraft).not.toHaveBeenCalled();
    wrapper.unmount();
  });
});
