import { flushPromises, mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ResourcePage from './index.vue';

vi.hoisted(() => {
  (globalThis as typeof globalThis & { useStorage: () => { value: string } }).useStorage = () => ({ value: 'zh_CN' });
});

vi.mock('@/views/certmuse/catalog/import/ImportDialog.vue', () => ({
  default: {
    props: ['modelValue'],
    emits: ['closed', 'update:modelValue'],
    template: '<div class="import-dialog-stub" :data-visible="modelValue" @click="$emit(\'closed\')"><button class="import-close" @click.stop="$emit(\'update:modelValue\', false)">close</button></div>'
  }
}));
vi.mock('./PdfReaderDialog.vue', () => ({
  default: {
    props: ['modelValue', 'documentTitle', 'reader', 'initialPage'],
    emits: ['update:modelValue', 'refresh'],
    template: '<div class="pdf-reader-dialog-stub" :data-visible="modelValue" />'
  }
}));

const resourceApi = vi.hoisted(() => ({
  deleteTextbook: vi.fn(),
  deleteTextbookChunks: vi.fn(),
  getTextbook: vi.fn(),
  getTextbookChunk: vi.fn(),
  getTextbookOptions: vi.fn(),
  listTextbookChunks: vi.fn(),
  listTextbooks: vi.fn(),
  publishTextbook: vi.fn(),
  takeTextbookOffline: vi.fn(),
  updateTextbook: vi.fn(),
  updateTextbookChunk: vi.fn()
}));
const pdfApi = vi.hoisted(() => ({
  deleteTextbookOriginalPdf: vi.fn(),
  getTextbookOriginalPdf: vi.fn(),
  getTextbookPdfReader: vi.fn(),
  uploadTextbookOriginalPdf: vi.fn()
}));
const knowledgeApi = vi.hoisted(() => ({ getKnowledgeTree: vi.fn() }));
const messages = vi.hoisted(() => ({ confirm: vi.fn(), error: vi.fn(), success: vi.fn(), warning: vi.fn() }));

const PaginationStub = {
  name: 'Pagination',
  props: ['page', 'limit'],
  emits: ['update:page', 'update:limit', 'pagination'],
  template: `
    <div class="pagination-stub" :data-page="page" :data-limit="limit">
      <button class="pagination-page" @click="$emit('update:page', 3)">page</button>
      <button class="pagination-limit" @click="$emit('update:limit', 25)">limit</button>
      <button class="pagination-load" @click="$emit('pagination')">load</button>
    </div>
  `
};

const DialogStub = {
  name: 'ElDialog',
  props: ['modelValue', 'title'],
  emits: ['update:modelValue'],
  template: `
    <section v-show="modelValue" class="dialog-stub" :data-title="title">
      <slot />
      <footer><slot name="footer" /></footer>
      <button class="dialog-model-close" @click="$emit('update:modelValue', false)">model close</button>
    </section>
  `
};

vi.mock('@/api/certmuse/catalog/resource', () => resourceApi);
vi.mock('@/api/certmuse/catalog/textbook-pdf', () => pdfApi);
vi.mock('@/api/certmuse/catalog/knowledge', () => knowledgeApi);
vi.mock('element-plus', async importOriginal => {
  const elementPlus = await importOriginal<typeof import('element-plus')>();
  return {
    ...elementPlus,
    ElMessage: { error: messages.error, success: messages.success, warning: messages.warning },
    ElMessageBox: { confirm: messages.confirm }
  };
});

const textbook = {
  id: '2001',
  certificationId: '10001',
  title: '系统架构设计师教程',
  syllabusVersionId: '1001',
  syllabusVersionName: '系统架构设计师 · 2026版',
  certificationName: '系统架构设计师',
  edition: '第4版',
  status: 'draft',
  chunkCount: 20,
  mappedChunkCount: 18,
  unmappedChunkCount: 2,
  knowledgePointCount: 12,
  latestImportBatchId: null,
  latestImportStatus: null,
  createBy: '1',
  createByName: '管理员',
  createTime: '2026-08-05T09:00:00+08:00',
  updateTime: '2026-08-05T09:00:00+08:00',
  deletable: true,
  deleteDisabledReason: null
};

describe('textbook resource page', () => {
  beforeEach(() => {
    Object.values(resourceApi).forEach(mock => mock.mockReset());
    Object.values(pdfApi).forEach(mock => mock.mockReset());
    knowledgeApi.getKnowledgeTree.mockReset();
    Object.values(messages).forEach(mock => mock.mockReset());
    resourceApi.getTextbookOptions.mockResolvedValue({
      certifications: [{ id: '10001', label: '系统架构设计师' }],
      statuses: [{ value: 'draft', label: '草稿' }],
      creators: []
    });
    resourceApi.listTextbooks.mockResolvedValue({ rows: [textbook], total: 1 });
    resourceApi.getTextbook.mockResolvedValue(textbook);
    pdfApi.getTextbookOriginalPdf.mockResolvedValue({ available: false });
    messages.confirm.mockResolvedValue(undefined);
  });

  it('shows only filters initially and reveals textbooks after an explicit search', async () => {
    const wrapper = mount(ResourcePage, { global: { plugins: [ElementPlus], stubs: { Pagination: true } } });
    await flushPromises();

    expect(resourceApi.getTextbookOptions).toHaveBeenCalledOnce();
    expect(resourceApi.listTextbooks).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('筛选条件');
    expect(wrapper.text()).not.toContain('教材列表');
    expect(wrapper.find('.content-grid').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('内容块');

    const vm = wrapper.vm as unknown as {
      applyFilters: () => Promise<void>;
      selectedTextbookRows: Array<typeof textbook>;
    };
    await vm.applyFilters();
    await flushPromises();

    expect(resourceApi.listTextbooks).toHaveBeenCalledOnce();
    expect(wrapper.text()).toContain('教材列表');
    expect(wrapper.text()).toContain('系统架构设计师教程');
    expect(wrapper.text()).toContain('资格名称');
    expect(wrapper.text()).toContain('系统架构设计师');
    expect(wrapper.text()).not.toContain('系统架构设计师 · 2026版');
    expect(wrapper.find('.content-grid').exists()).toBe(false);
  });

  it('uses textbook options as the single source for qualification labels', async () => {
    const wrapper = mount(ResourcePage, { global: { plugins: [ElementPlus], stubs: { Pagination: true } } });
    await flushPromises();

    expect((wrapper.vm as unknown as { certificationOptions: Array<{ id: string; label: string }> }).certificationOptions)
      .toEqual([{ id: '10001', label: '系统架构设计师' }]);
  });

  it('returns to the initial blank result state when filters are reset', async () => {
    const wrapper = mount(ResourcePage, { global: { plugins: [ElementPlus], stubs: { Pagination: true } } });
    const vm = wrapper.vm as unknown as { applyFilters: () => Promise<void>; resetQuery: () => void };
    await vm.applyFilters();
    await flushPromises();
    expect(wrapper.text()).toContain('教材列表');

    vm.resetQuery();
    await flushPromises();
    expect(wrapper.text()).not.toContain('教材列表');
    expect(resourceApi.listTextbooks).toHaveBeenCalledOnce();
  });

  it('places create, edit and delete actions on the textbook list', async () => {
    const wrapper = mount(ResourcePage, { global: { plugins: [ElementPlus], stubs: { Pagination: true } } });
    const vm = wrapper.vm as unknown as {
      applyFilters: () => Promise<void>;
      selectedTextbookRows: (typeof textbook)[];
    };
    await vm.applyFilters();
    await flushPromises();

    const textbookPanel = wrapper.find('.textbook-panel');
    expect(textbookPanel.text()).toContain('新增');
    expect(textbookPanel.find('.textbook-edit').exists()).toBe(true);
    expect(textbookPanel.find('.textbook-edit').attributes('aria-label')).toBe('编辑教材');
    expect(textbookPanel.text()).toContain('删除');
    expect(textbookPanel.findAll('thead .el-checkbox')).toHaveLength(1);

    vm.selectedTextbookRows = [textbook];
    await flushPromises();
    const actionButtons = textbookPanel.findAll('.toolbar-actions .el-button');
    expect(actionButtons[1]?.attributes('disabled')).toBeUndefined();
  });

  it('edits textbook title and certification through the icon action', async () => {
    resourceApi.updateTextbook.mockResolvedValue(undefined);
    const wrapper = mount(ResourcePage, { global: { plugins: [ElementPlus], stubs: { Pagination: true } } });
    const vm = wrapper.vm as any;
    await vm.applyFilters();
    await flushPromises();

    resourceApi.getTextbookOptions.mockResolvedValueOnce({
      certifications: [
        { id: '10001', label: '系统架构设计师' },
        { id: '10002', label: '最新创建资格' }
      ],
      statuses: [{ value: 'draft', label: '草稿' }],
      creators: []
    });
    await vm.openTextbookEdit(textbook);
    expect(vm.textbookEditVisible).toBe(true);
    expect(vm.textbookEditForm).toEqual({ title: '系统架构设计师教程', certificationId: '10001' });
    expect(resourceApi.getTextbookOptions).toHaveBeenCalledTimes(2);
    expect(vm.certificationOptions).toContainEqual({ id: '10002', label: '最新创建资格' });
    vm.textbookEditFormRef = { validate: vi.fn().mockResolvedValue(true) };
    vm.textbookEditForm.title = '新版教材';
    vm.textbookEditForm.certificationId = '10002';
    await vm.submitTextbookEdit();

    expect(resourceApi.updateTextbook).toHaveBeenCalledWith('2001', {
      title: '新版教材',
      certificationId: '10002'
    });
    expect(messages.success).toHaveBeenCalledWith('教材已更新');
    expect(resourceApi.listTextbooks).toHaveBeenCalledTimes(2);
  });

  it('publishes draft textbooks and takes published textbooks offline from icon actions', async () => {
    resourceApi.publishTextbook.mockResolvedValue(undefined);
    resourceApi.takeTextbookOffline.mockResolvedValue(undefined);
    const wrapper = mount(ResourcePage, { global: { plugins: [ElementPlus], stubs: { Pagination: true } } });
    const vm = wrapper.vm as any;
    await vm.applyFilters();
    await flushPromises();

    expect(wrapper.find('.textbook-publish').attributes('aria-label')).toBe('发布教材');
    await vm.publishTextbook(textbook);
    expect(messages.confirm).toHaveBeenCalledWith(
      expect.stringContaining('游客和学生端公开阅读'),
      '确认发布教材',
      expect.objectContaining({ confirmButtonText: '确认发布' })
    );
    expect(resourceApi.publishTextbook).toHaveBeenCalledWith('2001');
    expect(messages.success).toHaveBeenCalledWith('教材已发布，游客和学生端现在可阅读');

    resourceApi.listTextbooks.mockResolvedValue({ rows: [{ ...textbook, status: 'published' }], total: 1 });
    await vm.loadTextbooks();
    await flushPromises();
    expect(wrapper.find('.textbook-offline').attributes('aria-label')).toBe('下架教材');
    await vm.takeTextbookOffline({ ...textbook, status: 'published' });
    expect(messages.confirm).toHaveBeenCalledWith(
      expect.stringContaining('学生端将无法查看'),
      '确认下架教材',
      expect.objectContaining({ confirmButtonText: '确认下架' })
    );
    expect(resourceApi.takeTextbookOffline).toHaveBeenCalledWith('2001');
    expect(messages.success).toHaveBeenCalledWith('教材已下架，游客和学生端现在不可见');
  });

  it('derives whether the certification field is locked from knowledge point mappings', async () => {
    const wrapper = mount(ResourcePage, { global: { plugins: [ElementPlus], stubs: { Pagination: true } } });
    const vm = wrapper.vm as any;
    await vm.openTextbookEdit({ ...textbook, knowledgePointCount: 1 });
    await flushPromises();

    expect(vm.textbookHasKnowledgeMappings).toBe(true);

    await vm.openTextbookEdit({ ...textbook, knowledgePointCount: 0 });
    await flushPromises();
    expect(vm.textbookHasKnowledgeMappings).toBe(false);
  });

  it('shows imported chunks for the first exam subject immediately after viewing a textbook', async () => {
    resourceApi.getTextbook.mockResolvedValue(textbook);
    resourceApi.listTextbookChunks.mockResolvedValue({
      rows: [
        {
          id: '3001',
          documentId: '2001',
          chunkOrder: 1,
          heading: null,
          headingPath: [],
          contentPreview: '已导入的内容块',
          pageStart: null,
          pageEnd: null,
          sourceLocator: { sourceType: null, sourceKey: null, lineStart: null, lineEnd: null },
          knowledgePoints: [],
          mapped: false
        }
      ],
      total: 1
    });
    knowledgeApi.getKnowledgeTree.mockResolvedValue({
      data: {
        subjects: [{ id: '101', subjectName: '综合知识' }],
        nodes: [
          {
            id: '10001',
            examSubjectId: '101',
            parentId: null,
            syllabusNumber: '1.1',
            syllabusTitle: '系统基础',
            sortOrder: 1
          }
        ]
      }
    });
    const wrapper = mount(ResourcePage, { global: { plugins: [ElementPlus], stubs: { Pagination: true } } });
    const vm = wrapper.vm as unknown as {
      applyFilters: () => Promise<void>;
      selectTextbook: (id: string) => Promise<void>;
      selectedNode: { key: string; examSubjectId: string } | undefined;
      navigation: Array<{ key: string; children: unknown[] }>;
      chunkRows: Array<{ id: string }>;
    };

    await vm.applyFilters();
    await vm.selectTextbook(textbook.id);
    await flushPromises();

    expect(vm.selectedNode).toEqual(expect.objectContaining({ key: 'subject:101', examSubjectId: '101' }));
    expect(vm.navigation).toEqual([expect.objectContaining({ key: 'subject:101', children: [] })]);
    expect(resourceApi.listTextbookChunks).toHaveBeenCalledWith(
      textbook.id,
      expect.objectContaining({ knowledgePointId: undefined, examSubjectId: '101', includeDescendants: true })
    );
    expect(vm.chunkRows).toEqual([expect.objectContaining({ id: '3001' })]);
    expect(wrapper.text()).toContain('综合知识');
  });

  it('covers chunk editing and deletion actions', async () => {
    resourceApi.getTextbook.mockResolvedValue(textbook);
    resourceApi.listTextbookChunks.mockResolvedValue({ rows: [{
      id: '3001', heading: null, headingPath: [], contentPreview: '正文', pageStart: 3, pageEnd: 5,
      sourceLocator: { sourceType: null, sourceKey: null, lineStart: null, lineEnd: null }, knowledgePoints: [], mapped: false
    }], total: 1 });
    knowledgeApi.getKnowledgeTree.mockResolvedValue({ data: { subjects: [{ id: '101', subjectName: '综合知识' }], nodes: [] } });
    resourceApi.getTextbookChunk.mockResolvedValue({
      id: '3001', heading: '标题', headingPath: ['章节'], content: '正文', updateTime: 'v1',
      knowledgePoints: [{ id: 'kp-1' }], sourceLocator: { sourceKey: 'doc.pdf', lineStart: 2, lineEnd: 4 }, editable: true
    });
    resourceApi.updateTextbookChunk.mockResolvedValue({});
    resourceApi.deleteTextbookChunks.mockResolvedValue({});
    resourceApi.deleteTextbook.mockResolvedValue({});
    const wrapper = mount(ResourcePage, { global: { plugins: [ElementPlus], stubs: { Pagination: true } } });
    await flushPromises();
    const vm = wrapper.vm as any;
    await vm.applyFilters();
    await vm.selectTextbook(textbook.id);
    await flushPromises();
    const chunk = vm.chunkRows[0];
    await vm.openChunkView(chunk);
    expect(vm.chunkViewVisible).toBe(true);
    expect(vm.sourceLocatorText(await resourceApi.getTextbookChunk.mock.results[0].value)).toContain('doc.pdf');
    await vm.openChunkEdit(chunk);
    expect(vm.editVisible).toBe(true);
    vm.editFormRef = { validate: vi.fn().mockResolvedValue(true) };
    vm.editForm.knowledgeNodeKeys = ['knowledge:kp-1', 'subject:ignored'];
    await vm.submitEdit();
    expect(resourceApi.updateTextbookChunk).toHaveBeenCalledWith(textbook.id, '3001', expect.objectContaining({ knowledgePointIds: ['kp-1'] }));

    await vm.removeChunks([chunk]);
    expect(resourceApi.deleteTextbookChunks).toHaveBeenCalledWith(textbook.id, ['3001']);
    expect(messages.success).toHaveBeenCalledWith('内容块已删除');

    vm.selectedTextbookRows = [textbook];
    await vm.removeSelectedTextbooks();
    expect(resourceApi.deleteTextbook).toHaveBeenCalledWith(textbook.id);
    expect(messages.success).toHaveBeenCalledWith('已删除 1 本教材');
  });

  it('shows a warning for a non-editable chunk and handles display helpers', async () => {
    resourceApi.getTextbook.mockResolvedValue(textbook);
    resourceApi.listTextbookChunks.mockResolvedValue({ rows: [{
      id: '3001', heading: null, headingPath: [], contentPreview: '正文', pageStart: null, pageEnd: null,
      sourceLocator: { sourceType: null, sourceKey: null, lineStart: null, lineEnd: null }, knowledgePoints: [], mapped: false
    }], total: 1 });
    knowledgeApi.getKnowledgeTree.mockResolvedValue({ data: { subjects: [{ id: '101', subjectName: '综合知识' }], nodes: [] } });
    resourceApi.getTextbookChunk.mockResolvedValue({ id: '3001', editable: false, operationDisabledReason: '已发布内容不可改' });
    const wrapper = mount(ResourcePage, { global: { plugins: [ElementPlus], stubs: { Pagination: true } } });
    await flushPromises();
    const vm = wrapper.vm as any;
    await vm.applyFilters();
    await vm.selectTextbook(textbook.id);
    await vm.openChunkEdit(vm.chunkRows[0]);
    expect(messages.warning).toHaveBeenCalledWith('已发布内容不可改');
    expect(vm.statusType('rejected')).toBe('danger');
    expect(vm.pageRange({ pageStart: 4, pageEnd: 4 })).toBe('第 4 页');
    expect(vm.pageRange({ pageStart: 4, pageEnd: 7 })).toBe('4–7 页');
    expect(vm.pageRange({ pageStart: null, pageEnd: null })).toBe('—');
    expect(vm.filterNode('架构', { label: '系统架构', path: '目录 / 系统架构' })).toBe(true);
    expect(vm.textbookCertificationName({ certificationName: '', syllabusVersionName: '架构设计 · 2026' })).toBe('架构设计');
  });

  it('reports initialization and directory loading errors, then switches directory scope', async () => {
    resourceApi.getTextbookOptions.mockRejectedValueOnce(new Error('选项加载失败'));
    resourceApi.listTextbooks.mockRejectedValueOnce(new Error('教材查询失败'));
    const wrapper = mount(ResourcePage, { global: { plugins: [ElementPlus], stubs: { Pagination: true } } });
    await flushPromises();

    expect(messages.error).toHaveBeenCalledWith('选项加载失败');

    const vm = wrapper.vm as any;
    await vm.applyFilters();
    await flushPromises();
    expect(messages.error).toHaveBeenCalledWith('教材查询失败');
    expect(vm.textbookLoading).toBe(false);

    resourceApi.listTextbooks.mockResolvedValueOnce({ rows: [textbook], total: 1 });
    await vm.applyFilters();
    resourceApi.getTextbook.mockRejectedValueOnce(new Error('教材详情失败'));
    knowledgeApi.getKnowledgeTree.mockRejectedValueOnce(new Error('知识目录失败'));
    await vm.selectTextbook(textbook.id);
    expect(messages.error).toHaveBeenCalledWith('教材详情失败');
    expect(messages.error).toHaveBeenCalledWith('知识目录失败');

    resourceApi.getTextbook.mockResolvedValue(textbook);
    knowledgeApi.getKnowledgeTree.mockResolvedValue({
      data: {
        subjects: [{ id: '101', subjectName: '综合知识' }],
        nodes: [
          { id: 'root', examSubjectId: '101', parentId: null, syllabusNumber: '1', syllabusTitle: '基础', sortOrder: 1 },
          { id: 'branch', examSubjectId: '101', parentId: 'root', syllabusNumber: '1.1', syllabusTitle: '架构', sortOrder: 1 },
          { id: 'leaf', examSubjectId: '101', parentId: 'branch', syllabusNumber: '1.1.1', syllabusTitle: '组件', sortOrder: 1 }
        ]
      }
    });
    resourceApi.listTextbookChunks.mockResolvedValue({ rows: [], total: 0 });
    await vm.selectTextbook(textbook.id);
    expect(vm.navigation[0].children[0].children[0]).toEqual(expect.objectContaining({ key: 'knowledge:branch' }));

    resourceApi.listTextbookChunks.mockRejectedValueOnce(new Error('内容块加载失败'));
    vm.chunkQuery.pageNum = 3;
    await vm.handleDirectoryClick(vm.navigation[0].children[0].children[0]);
    expect(messages.error).toHaveBeenCalledWith('内容块加载失败');
    expect(vm.chunkLoading).toBe(false);

    resourceApi.listTextbookChunks.mockResolvedValueOnce({ rows: [], total: 0 });
    await vm.handleDirectoryClick(vm.navigation[0].children[0].children[0]);
    expect(resourceApi.listTextbookChunks).toHaveBeenLastCalledWith(textbook.id, expect.objectContaining({
      pageNum: 1,
      knowledgePointId: 'branch',
      examSubjectId: undefined,
      includeDescendants: true
    }));
  });

  it('submits all rendered textbook filters and pagination values', async () => {
    resourceApi.getTextbookOptions.mockResolvedValue({
      certifications: [{ id: '10001', label: '系统架构设计师' }],
      statuses: [{ value: 'draft', label: '草稿' }],
      creators: [{ id: '7', label: '内容管理员' }]
    });
    const wrapper = mount(ResourcePage, {
      global: { plugins: [ElementPlus], directives: { hasPermi: () => undefined }, stubs: { Pagination: PaginationStub, ElDialog: DialogStub } }
    });
    await flushPromises();
    const selects = wrapper.find('.resource-query-form').findAllComponents({ name: 'ElSelect' });
    selects[0]!.vm.$emit('update:modelValue', '10001');
    selects[1]!.vm.$emit('update:modelValue', 'draft');
    selects[2]!.vm.$emit('update:modelValue', '7');
    selects[3]!.vm.$emit('update:modelValue', false);
    const title = wrapper.find('input[placeholder="请输入教材名称"]');
    await title.setValue('架构教程');
    await title.trigger('keyup.enter');
    await flushPromises();
    expect(resourceApi.listTextbooks).toHaveBeenLastCalledWith(expect.objectContaining({
      title: '架构教程', certificationId: '10001', status: 'draft', createBy: '7', pageNum: 1, pageSize: 10
    }));
    await wrapper.find('.pagination-page').trigger('click');
    await wrapper.find('.pagination-limit').trigger('click');
    await wrapper.find('.pagination-load').trigger('click');
    await flushPromises();
    expect(resourceApi.listTextbooks).toHaveBeenLastCalledWith(expect.objectContaining({ pageNum: 3, pageSize: 25 }));
    const add = wrapper.findAll('.textbook-panel .toolbar-actions button').find(button => button.text().includes('新增'));
    await add!.trigger('click');
    expect(wrapper.find('.import-dialog-stub').attributes('data-visible')).toBe('true');
    await wrapper.find('.import-close').trigger('click');
    expect(wrapper.find('.import-dialog-stub').attributes('data-visible')).toBe('false');
  });

  it('views, edits, and deletes a chunk using rendered actions', async () => {
    const chunk = {
      id: '3001', documentId: '2001', chunkOrder: 1, heading: '架构原则', headingPath: ['第一章'],
      contentPreview: '正文摘要', pageStart: 3, pageEnd: 4,
      sourceLocator: { sourceType: 'markdown', sourceKey: 'book.md', lineStart: 10, lineEnd: 20 },
      knowledgePoints: [{ id: 'kp-1', code: '1.1', title: '架构基础' }], mapped: true
    };
    const chunkDetail = { ...chunk, content: '完整正文', updateTime: '2026-08-12T10:00:00+08:00', editable: true, operationDisabledReason: null };
    resourceApi.getTextbook.mockResolvedValue(textbook);
    resourceApi.getTextbookChunk.mockResolvedValue(chunkDetail);
    resourceApi.listTextbookChunks.mockResolvedValue({ rows: [chunk], total: 1 });
    resourceApi.updateTextbookChunk.mockResolvedValue({});
    resourceApi.deleteTextbookChunks.mockResolvedValue({});
    knowledgeApi.getKnowledgeTree.mockResolvedValue({
      data: { subjects: [{ id: '101', subjectName: '综合知识' }], nodes: [{ id: 'kp-1', examSubjectId: '101', parentId: null, syllabusNumber: '1.1', syllabusTitle: '架构基础', sortOrder: 1 }] }
    });
    const wrapper = mount(ResourcePage, {
      global: { plugins: [ElementPlus], directives: { hasPermi: () => undefined }, stubs: { Pagination: PaginationStub, ElDialog: DialogStub } }
    });
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text().trim() === '搜索')!.trigger('click');
    await flushPromises();
    await wrapper.find('.textbook-panel [aria-label="查看教材内容"]').trigger('click');
    await flushPromises();
    const rowActions = wrapper.find('.chunk-card .el-table__body-wrapper tbody tr').findAll('button');
    await rowActions[1]!.trigger('click');
    await flushPromises();
    const viewDialog = wrapper.findAll('.dialog-stub').find(dialog => dialog.attributes('data-title') === '查看内容块')!;
    expect(viewDialog.text()).toContain('1.1 架构基础');
    await viewDialog.find('.dialog-model-close').trigger('click');
    await rowActions[2]!.trigger('click');
    await flushPromises();
    const editDialog = wrapper.findAll('.dialog-stub').find(dialog => dialog.attributes('data-title') === '修改内容块')!;
    const editInputs = editDialog.findAll('input');
    await editInputs[1]!.setValue('更新后的标题');
    await editDialog.find('textarea').setValue('更新后的正文');
    editDialog.findComponent({ name: 'ElTreeSelect' }).vm.$emit('update:modelValue', ['knowledge:kp-1']);
    await flushPromises();
    await editDialog.findAll('button').find(button => button.text().includes('保存'))!.trigger('click');
    await flushPromises();
    expect(resourceApi.updateTextbookChunk).toHaveBeenCalledWith(textbook.id, chunk.id, expect.objectContaining({
      heading: '更新后的标题', content: '更新后的正文', knowledgePointIds: ['kp-1']
    }));
    await rowActions[3]!.trigger('click');
    await flushPromises();
    expect(resourceApi.deleteTextbookChunks).toHaveBeenCalledWith(textbook.id, [chunk.id]);
    wrapper.unmount();
  });
  it('collapses and resets the rendered textbook search without retaining a stale result list', async () => {
    const wrapper = mount(ResourcePage, {
      global: { plugins: [ElementPlus], directives: { hasPermi: () => undefined }, stubs: { Pagination: PaginationStub } }
    });
    await flushPromises();

    const searchPanel = wrapper.find('.search-panel');
    expect(searchPanel.classes()).not.toContain('is-collapsed');
    await wrapper.find('.search-panel-toggle').trigger('click');
    expect(searchPanel.classes()).toContain('is-collapsed');

    const title = wrapper.find('input[placeholder="请输入教材名称"]');
    await title.setValue('待清空的教材');
    await wrapper.findAll('button').find(button => button.text().trim() === '搜索')!.trigger('click');
    await flushPromises();
    expect(wrapper.find('.textbook-panel').exists()).toBe(true);
    expect(resourceApi.listTextbooks).toHaveBeenLastCalledWith(expect.objectContaining({ title: '待清空的教材' }));

    await wrapper.findAll('button').find(button => button.text().trim() === '重置')!.trigger('click');
    await flushPromises();
    expect(wrapper.find('.textbook-panel').exists()).toBe(false);
    expect((wrapper.find('input[placeholder="请输入教材名称"]').element as HTMLInputElement).value).toBe('');
  });

  it('uses the rendered table selection to delete only deletable textbooks', async () => {
    resourceApi.deleteTextbook.mockResolvedValue({});
    const wrapper = mount(ResourcePage, {
      global: { plugins: [ElementPlus], directives: { hasPermi: () => undefined }, stubs: { Pagination: PaginationStub } }
    });
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text().trim() === '搜索')!.trigger('click');
    await flushPromises();

    const rowCheckbox = wrapper.find('.textbook-panel .el-table__body-wrapper .el-checkbox input[type="checkbox"]');
    await rowCheckbox.setValue(true);
    await flushPromises();
    const deleteButton = wrapper.findAll('.textbook-panel .toolbar-actions button').find(button => button.text().includes('删除'))!;
    expect(deleteButton.attributes('disabled')).toBeUndefined();
    await deleteButton.trigger('click');
    await flushPromises();

    expect(messages.confirm).toHaveBeenCalledWith(expect.stringContaining('系统架构设计师教程'), '删除教材', expect.any(Object));
    expect(resourceApi.deleteTextbook).toHaveBeenCalledWith(textbook.id);
    expect(messages.success).toHaveBeenCalledWith('已删除 1 本教材');
  });

  it('keeps the rendered bulk-delete action disabled for a non-deletable textbook', async () => {
    resourceApi.listTextbooks.mockResolvedValue({
      rows: [{ ...textbook, deletable: false, deleteDisabledReason: '教材正在被使用' }], total: 1
    });
    const wrapper = mount(ResourcePage, {
      global: { plugins: [ElementPlus], directives: { hasPermi: () => undefined }, stubs: { Pagination: PaginationStub } }
    });
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text().trim() === '搜索')!.trigger('click');
    await flushPromises();

    const rowCheckbox = wrapper.find('.textbook-panel .el-table__body-wrapper .el-checkbox input[type="checkbox"]');
    await rowCheckbox.setValue(true);
    await flushPromises();
    const deleteButton = wrapper.findAll('.textbook-panel .toolbar-actions button').find(button => button.text().includes('删除'))!;
    expect(deleteButton.attributes('disabled')).toBeDefined();
    expect(resourceApi.deleteTextbook).not.toHaveBeenCalled();
  });

  it('refreshes the textbook list when the visible import dialog reports that it has closed', async () => {
    const wrapper = mount(ResourcePage, {
      global: { plugins: [ElementPlus], directives: { hasPermi: () => undefined }, stubs: { Pagination: PaginationStub } }
    });
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text().trim() === '搜索')!.trigger('click');
    await flushPromises();
    const callsBeforeOpen = resourceApi.listTextbooks.mock.calls.length;
    await wrapper.findAll('.textbook-panel .toolbar-actions button').find(button => button.text().includes('新增'))!.trigger('click');
    expect(wrapper.find('.import-dialog-stub').attributes('data-visible')).toBe('true');
    await wrapper.find('.import-dialog-stub').trigger('click');
    await flushPromises();

    expect(resourceApi.listTextbooks.mock.calls.length).toBe(callsBeforeOpen + 1);
  });

  it('navigates through a rendered textbook, directory, chunk filters, and chunk pagination', async () => {
    const nestedTree = {
      data: {
        subjects: [{ id: '101', subjectName: '综合知识' }],
        nodes: [
          { id: 'root', examSubjectId: '101', parentId: null, syllabusNumber: '1', syllabusTitle: '基础', sortOrder: 1 },
          { id: 'chapter', examSubjectId: '101', parentId: 'root', syllabusNumber: '1.1', syllabusTitle: '架构', sortOrder: 1 },
          { id: 'leaf', examSubjectId: '101', parentId: 'chapter', syllabusNumber: '1.1.1', syllabusTitle: '设计原则', sortOrder: 1 }
        ]
      }
    };
    resourceApi.getTextbook.mockResolvedValue({ ...textbook, statistics: { chunkCount: 1, mappedChunkCount: 1, unmappedChunkCount: 0 } });
    knowledgeApi.getKnowledgeTree.mockResolvedValue(nestedTree);
    resourceApi.listTextbookChunks.mockResolvedValue({
      rows: [{
        id: '3001', documentId: textbook.id, chunkOrder: 1, heading: '架构原则', headingPath: ['第一章'], contentPreview: '正文摘要',
        pageStart: 3, pageEnd: 4, sourceLocator: { sourceType: 'markdown', sourceKey: 'book.md', lineStart: 10, lineEnd: 20 },
        knowledgePoints: [{ id: 'leaf', code: '1.1.1', title: '设计原则' }], mapped: true
      }],
      total: 1
    });
    const wrapper = mount(ResourcePage, {
      global: { plugins: [ElementPlus], directives: { hasPermi: () => undefined }, stubs: { Pagination: PaginationStub, ElDialog: DialogStub } }
    });
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text().trim() === '搜索')!.trigger('click');
    await flushPromises();
    await wrapper.find('.textbook-panel [aria-label="查看教材内容"]').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('综合知识');
    expect(wrapper.text()).toContain('1.1 架构');
    const directoryHeader = wrapper.find('.tree-panel-header');
    await directoryHeader.trigger('click');
    expect(wrapper.find('.tree-panel-shell').classes()).toContain('is-collapsed');
    await directoryHeader.trigger('click');
    expect(wrapper.find('.tree-panel-shell').classes()).not.toContain('is-collapsed');

    const filter = wrapper.find('input[placeholder="搜索标题或正文"]');
    await filter.setValue('架构原则');
    await filter.trigger('keyup.enter');
    await flushPromises();
    expect(resourceApi.listTextbookChunks).toHaveBeenLastCalledWith(textbook.id, expect.objectContaining({ keyword: '架构原则', examSubjectId: '101' }));

    const chunkPagination = wrapper.findAll('.pagination-stub')[1]!;
    await chunkPagination.find('.pagination-page').trigger('click');
    await chunkPagination.find('.pagination-limit').trigger('click');
    await chunkPagination.find('.pagination-load').trigger('click');
    await flushPromises();
    expect(resourceApi.listTextbookChunks).toHaveBeenLastCalledWith(textbook.id, expect.objectContaining({ pageNum: 3, pageSize: 25 }));
  });

  it('uses the cached directory when a user opens two rendered textbooks from the same syllabus', async () => {
    const secondTextbook = { ...textbook, id: '2002', title: '系统架构设计师习题册' };
    resourceApi.listTextbooks.mockResolvedValue({ rows: [textbook, secondTextbook], total: 2 });
    resourceApi.getTextbook.mockResolvedValue({ ...textbook, statistics: { chunkCount: 0, mappedChunkCount: 0, unmappedChunkCount: 0 } });
    knowledgeApi.getKnowledgeTree.mockResolvedValue({ data: { subjects: [{ id: '101', subjectName: '综合知识' }], nodes: [] } });
    resourceApi.listTextbookChunks.mockResolvedValue({ rows: [], total: 0 });
    const wrapper = mount(ResourcePage, {
      global: { plugins: [ElementPlus], directives: { hasPermi: () => undefined }, stubs: { Pagination: PaginationStub } }
    });
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text().trim() === '搜索')!.trigger('click');
    await flushPromises();
    const textbookRows = wrapper.findAll('.textbook-panel .el-table__body-wrapper tbody tr');
    await textbookRows[0]!.find('[aria-label="查看教材内容"]').trigger('click');
    await flushPromises();
    await textbookRows[1]!.find('[aria-label="查看教材内容"]').trigger('click');
    await flushPromises();

    expect(knowledgeApi.getKnowledgeTree).toHaveBeenCalledTimes(1);
    expect(resourceApi.getTextbook).toHaveBeenLastCalledWith(secondTextbook.id);
  });

  it('reports visible errors for a failed search and a cancelled chunk removal without treating cancellation as an error', async () => {
    resourceApi.listTextbooks.mockRejectedValueOnce('network interrupted');
    const chunk = {
      id: '3001', documentId: textbook.id, chunkOrder: 1, heading: '架构原则', headingPath: [], contentPreview: '正文摘要',
      pageStart: null, pageEnd: null, sourceLocator: { sourceType: null, sourceKey: null, lineStart: null, lineEnd: null }, knowledgePoints: [], mapped: false
    };
    resourceApi.getTextbook.mockResolvedValue({ ...textbook, statistics: { chunkCount: 1, mappedChunkCount: 0, unmappedChunkCount: 1 } });
    knowledgeApi.getKnowledgeTree.mockResolvedValue({ data: { subjects: [{ id: '101', subjectName: '综合知识' }], nodes: [] } });
    resourceApi.listTextbookChunks.mockResolvedValue({ rows: [chunk], total: 1 });
    const wrapper = mount(ResourcePage, {
      global: { plugins: [ElementPlus], directives: { hasPermi: () => undefined }, stubs: { Pagination: PaginationStub, ElDialog: DialogStub } }
    });
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text().trim() === '搜索')!.trigger('click');
    await flushPromises();
    expect(messages.error).toHaveBeenCalledWith('操作失败，请稍后重试');

    resourceApi.listTextbooks.mockResolvedValueOnce({ rows: [textbook], total: 1 });
    await wrapper.findAll('button').find(button => button.text().trim() === '搜索')!.trigger('click');
    await flushPromises();
    await wrapper.find('.textbook-panel [aria-label="查看教材内容"]').trigger('click');
    await flushPromises();
    messages.confirm.mockRejectedValueOnce('cancel');
    const chunkActions = wrapper.find('.chunk-card .el-table__body-wrapper tbody tr').findAll('button');
    await chunkActions[3]!.trigger('click');
    await flushPromises();

    expect(resourceApi.deleteTextbookChunks).not.toHaveBeenCalled();
    expect(messages.error).toHaveBeenCalledTimes(1);
  });

  it('presents the fallback non-editable explanation through the visible edit action', async () => {
    const chunk = {
      id: '3001', documentId: textbook.id, chunkOrder: 1, heading: null, headingPath: [], contentPreview: '正文摘要',
      pageStart: null, pageEnd: null, sourceLocator: { sourceType: null, sourceKey: null, lineStart: null, lineEnd: null }, knowledgePoints: [], mapped: false
    };
    resourceApi.getTextbook.mockResolvedValue({ ...textbook, statistics: { chunkCount: 1, mappedChunkCount: 0, unmappedChunkCount: 1 } });
    resourceApi.getTextbookChunk.mockResolvedValue({ ...chunk, content: '完整正文', editable: false, operationDisabledReason: null });
    knowledgeApi.getKnowledgeTree.mockResolvedValue({ data: { subjects: [{ id: '101', subjectName: '综合知识' }], nodes: [] } });
    resourceApi.listTextbookChunks.mockResolvedValue({ rows: [chunk], total: 1 });
    const wrapper = mount(ResourcePage, {
      global: { plugins: [ElementPlus], directives: { hasPermi: () => undefined }, stubs: { Pagination: PaginationStub, ElDialog: DialogStub } }
    });
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text().trim() === '搜索')!.trigger('click');
    await flushPromises();
    await wrapper.find('.textbook-panel [aria-label="查看教材内容"]').trigger('click');
    await flushPromises();
    const chunkActions = wrapper.find('.chunk-card .el-table__body-wrapper tbody tr').findAll('button');
    await chunkActions[2]!.trigger('click');
    await flushPromises();

    expect(messages.warning).toHaveBeenCalledWith('当前内容块不可修改');
  });

  it('renders unnamed and unmapped content in the visible chunk view', async () => {
    const chunk = {
      id: '3002', documentId: textbook.id, chunkOrder: 2, heading: null, headingPath: [], contentPreview: '无目录正文',
      pageStart: null, pageEnd: null, sourceLocator: { sourceType: null, sourceKey: null, lineStart: null, lineEnd: null }, knowledgePoints: [], mapped: false
    };
    resourceApi.getTextbook.mockResolvedValue(textbook);
    resourceApi.getTextbookChunk.mockResolvedValue({ ...chunk, content: '完整正文', editable: true });
    resourceApi.listTextbookChunks.mockResolvedValue({ rows: [chunk], total: 1 });
    knowledgeApi.getKnowledgeTree.mockResolvedValue({ data: { subjects: [{ id: '101', subjectName: '综合知识' }], nodes: [] } });
    const wrapper = mount(ResourcePage, {
      global: { plugins: [ElementPlus], directives: { hasPermi: () => undefined }, stubs: { Pagination: PaginationStub, ElDialog: DialogStub } }
    });
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text().trim() === '搜索')!.trigger('click');
    await flushPromises();
    await wrapper.find('.textbook-panel [aria-label="查看教材内容"]').trigger('click');
    await flushPromises();
    await wrapper.find('.chunk-card .el-table__body-wrapper tbody tr').findAll('button')[1]!.trigger('click');
    await flushPromises();

    const view = wrapper.findAll('.dialog-stub').find(dialog => dialog.attributes('data-title') === '查看内容块')!;
    expect(view.text()).toContain('未命名内容块');
    expect(view.text()).toContain('教材根目录');
    expect(view.text()).toContain('未关联');
    expect(view.text()).toContain('—');
    wrapper.unmount();
  });

  it('shows an ordinary deletion failure from a visible chunk action', async () => {
    const chunk = {
      id: '3003', documentId: textbook.id, chunkOrder: 3, heading: '可删除块', headingPath: ['第一章'], contentPreview: '正文',
      pageStart: 1, pageEnd: 1, sourceLocator: { sourceType: 'markdown', sourceKey: 'book.md', lineStart: 1, lineEnd: 1 }, knowledgePoints: [], mapped: false
    };
    resourceApi.getTextbook.mockResolvedValue(textbook);
    resourceApi.listTextbookChunks.mockResolvedValue({ rows: [chunk], total: 1 });
    resourceApi.deleteTextbookChunks.mockRejectedValueOnce(new Error('删除服务不可用'));
    knowledgeApi.getKnowledgeTree.mockResolvedValue({ data: { subjects: [{ id: '101', subjectName: '综合知识' }], nodes: [] } });
    const wrapper = mount(ResourcePage, {
      global: { plugins: [ElementPlus], directives: { hasPermi: () => undefined }, stubs: { Pagination: PaginationStub, ElDialog: DialogStub } }
    });
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text().trim() === '搜索')!.trigger('click');
    await flushPromises();
    await wrapper.find('.textbook-panel [aria-label="查看教材内容"]').trigger('click');
    await flushPromises();
    await wrapper.find('.chunk-card .el-table__body-wrapper tbody tr').findAll('button')[3]!.trigger('click');
    await flushPromises();

    expect(messages.error).toHaveBeenCalledWith('删除服务不可用');
    wrapper.unmount();
  });

  it('keeps no-selection helper actions harmless and formats every source-locator line range', async () => {
    const wrapper = mount(ResourcePage, { global: { plugins: [ElementPlus], directives: { hasPermi: () => undefined } } });
    await flushPromises();
    const vm = wrapper.vm as any;

    await vm.loadChunks();
    await vm.openChunkView({ id: 'missing' });
    await vm.openChunkEdit();
    vm.editFormRef = { validate: vi.fn().mockResolvedValue(true) };
    await vm.submitEdit();
    await vm.removeChunks([]);
    vm.selectedTextbookRows = [];
    await vm.removeSelectedTextbooks();
    expect(resourceApi.listTextbookChunks).not.toHaveBeenCalled();
    expect(resourceApi.getTextbookChunk).not.toHaveBeenCalled();

    const detail = {
      sourceLocator: { sourceKey: 'book.md', lineStart: 10, lineEnd: 10 }
    };
    expect(vm.sourceLocatorText(detail)).toBe('book.md · 行 10');
    expect(vm.sourceLocatorText({ sourceLocator: { sourceKey: 'book.md', lineStart: 10, lineEnd: null } })).toBe('book.md · 行 10–');
    expect(vm.sourceLocatorText({ sourceLocator: { sourceKey: null, lineStart: null, lineEnd: null } })).toBe('—');
    wrapper.unmount();
  });

  it('keeps the visible textbook editor open when form validation rejects the save', async () => {
    const wrapper = mount(ResourcePage, {
      global: { plugins: [ElementPlus], directives: { hasPermi: () => undefined }, stubs: { Pagination: PaginationStub, ElDialog: DialogStub } }
    });
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text().trim() === '搜索')!.trigger('click');
    await flushPromises();
    await wrapper.find('.textbook-edit').trigger('click');
    await flushPromises();

    const editor = wrapper.findAll('.dialog-stub').find(dialog => dialog.attributes('data-title') === '编辑教材')!;
    expect(editor.isVisible()).toBe(true);
    const vm = wrapper.vm as any;
    vm.textbookEditFormRef = { validate: vi.fn().mockRejectedValue(new Error('请输入教材名称')) };
    await editor.findAll('button').find(button => button.text().includes('保存'))!.trigger('click');
    await flushPromises();

    expect(editor.isVisible()).toBe(true);
    expect(wrapper.find('.textbook-panel').text()).toContain('系统架构设计师教程');
    expect(resourceApi.updateTextbook).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('clears selected content after changing the selected textbook certification through the editor', async () => {
    resourceApi.getTextbook.mockResolvedValue({ ...textbook, statistics: { chunkCount: 1, mappedChunkCount: 1, unmappedChunkCount: 0 } });
    resourceApi.updateTextbook.mockResolvedValue({});
    resourceApi.listTextbookChunks.mockResolvedValue({ rows: [], total: 0 });
    knowledgeApi.getKnowledgeTree.mockResolvedValue({ data: { subjects: [{ id: '101', subjectName: '综合知识' }], nodes: [] } });
    const wrapper = mount(ResourcePage, {
      global: { plugins: [ElementPlus], directives: { hasPermi: () => undefined }, stubs: { Pagination: PaginationStub, ElDialog: DialogStub } }
    });
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text().trim() === '搜索')!.trigger('click');
    await flushPromises();
    await wrapper.find('.textbook-panel [aria-label="查看教材内容"]').trigger('click');
    await flushPromises();
    expect(wrapper.find('.content-grid').exists()).toBe(true);

    await wrapper.find('.textbook-edit').trigger('click');
    await flushPromises();
    const editor = wrapper.findAll('.dialog-stub').find(dialog => dialog.attributes('data-title') === '编辑教材')!;
    const vm = wrapper.vm as any;
    vm.textbookEditFormRef = { validate: vi.fn().mockResolvedValue(true) };
    vm.textbookEditForm.certificationId = '10002';
    await editor.findAll('button').find(button => button.text().includes('保存'))!.trigger('click');
    await flushPromises();

    expect(resourceApi.updateTextbook).toHaveBeenCalledWith(textbook.id, expect.objectContaining({ certificationId: '10002' }));
    expect(wrapper.find('.content-grid').exists()).toBe(false);
    expect(wrapper.find('.chunk-card').exists()).toBe(false);
    wrapper.unmount();
  });

  it('keeps selected content available after an editor save that does not change certification', async () => {
    resourceApi.getTextbook
      .mockResolvedValueOnce({ ...textbook, title: '系统架构设计师教程', statistics: { chunkCount: 1, mappedChunkCount: 1, unmappedChunkCount: 0 } })
      .mockResolvedValueOnce({ ...textbook, title: '系统架构设计师教程（修订）', statistics: { chunkCount: 1, mappedChunkCount: 1, unmappedChunkCount: 0 } });
    resourceApi.updateTextbook.mockResolvedValue({});
    resourceApi.listTextbookChunks.mockResolvedValue({ rows: [], total: 0 });
    knowledgeApi.getKnowledgeTree.mockResolvedValue({ data: { subjects: [{ id: '101', subjectName: '综合知识' }], nodes: [] } });
    const wrapper = mount(ResourcePage, {
      global: { plugins: [ElementPlus], directives: { hasPermi: () => undefined }, stubs: { Pagination: PaginationStub, ElDialog: DialogStub } }
    });
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text().trim() === '搜索')!.trigger('click');
    await flushPromises();
    await wrapper.find('.textbook-panel [aria-label="查看教材内容"]').trigger('click');
    await flushPromises();

    await wrapper.find('.textbook-edit').trigger('click');
    await flushPromises();
    const editor = wrapper.findAll('.dialog-stub').find(dialog => dialog.attributes('data-title') === '编辑教材')!;
    const vm = wrapper.vm as any;
    vm.textbookEditFormRef = { validate: vi.fn().mockResolvedValue(true) };
    vm.textbookEditForm.title = '系统架构设计师教程（修订）';
    await editor.findAll('button').find(button => button.text().includes('保存'))!.trigger('click');
    await flushPromises();

    expect(wrapper.find('.content-grid').exists()).toBe(true);
    expect(wrapper.find('.chunk-card').text()).toContain('内容块');
    expect(resourceApi.getTextbook).toHaveBeenLastCalledWith(textbook.id);
    wrapper.unmount();
  });

  it('passes the visible knowledge-point binding filter to the selected directory query', async () => {
    const mappedChunk = {
      id: '3008', documentId: textbook.id, chunkOrder: 1, heading: '已关联内容', headingPath: [], contentPreview: '正文',
      pageStart: null, pageEnd: null, sourceLocator: { sourceType: null, sourceKey: null, lineStart: null, lineEnd: null },
      knowledgePoints: [{ id: 'kp-1', code: '1.1', title: '基础' }], mapped: true
    };
    resourceApi.getTextbook.mockResolvedValue(textbook);
    resourceApi.listTextbookChunks.mockResolvedValue({ rows: [mappedChunk], total: 1 });
    knowledgeApi.getKnowledgeTree.mockResolvedValue({ data: { subjects: [{ id: '101', subjectName: '综合知识' }], nodes: [] } });
    const wrapper = mount(ResourcePage, {
      global: { plugins: [ElementPlus], directives: { hasPermi: () => undefined }, stubs: { Pagination: PaginationStub, ElDialog: DialogStub } }
    });
    await flushPromises();
    const filters = wrapper.find('.resource-query-form').findAllComponents({ name: 'ElSelect' });
    filters[3]!.vm.$emit('update:modelValue', true);
    await wrapper.findAll('button').find(button => button.text().trim() === '搜索')!.trigger('click');
    await flushPromises();
    await wrapper.find('.textbook-panel [aria-label="查看教材内容"]').trigger('click');
    await flushPromises();

    expect(wrapper.find('.chunk-card').text()).toContain('已关联内容');
    expect(resourceApi.listTextbookChunks).toHaveBeenLastCalledWith(
      textbook.id,
      expect.objectContaining({ hasKnowledgePoint: true, examSubjectId: '101', includeDescendants: true })
    );
    wrapper.unmount();
  });

  it('leaves the visible chunk area empty when an opened textbook has no selectable directory node', async () => {
    resourceApi.getTextbook.mockResolvedValue(textbook);
    knowledgeApi.getKnowledgeTree.mockResolvedValue({ data: { subjects: [], nodes: [] } });
    const wrapper = mount(ResourcePage, {
      global: { plugins: [ElementPlus], directives: { hasPermi: () => undefined }, stubs: { Pagination: PaginationStub } }
    });
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text().trim() === '搜索')!.trigger('click');
    await flushPromises();
    await wrapper.find('.textbook-panel [aria-label="查看教材内容"]').trigger('click');
    await flushPromises();

    expect(wrapper.find('.content-grid').exists()).toBe(true);
    expect(wrapper.find('.chunk-card .el-table__body-wrapper tbody').text()).not.toContain('内容块');
    expect(resourceApi.listTextbookChunks).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('keeps the list visible after a cancelled or failed rendered textbook deletion', async () => {
    resourceApi.deleteTextbook.mockRejectedValueOnce(new Error('教材删除服务不可用'));
    const wrapper = mount(ResourcePage, {
      global: { plugins: [ElementPlus], directives: { hasPermi: () => undefined }, stubs: { Pagination: PaginationStub } }
    });
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text().trim() === '搜索')!.trigger('click');
    await flushPromises();
    const rowCheckbox = wrapper.find('.textbook-panel .el-table__body-wrapper .el-checkbox input[type="checkbox"]');
    await rowCheckbox.setValue(true);
    await flushPromises();
    const deleteButton = wrapper.findAll('.textbook-panel .toolbar-actions button').find(button => button.text().includes('删除'))!;

    messages.confirm.mockRejectedValueOnce('close');
    await deleteButton.trigger('click');
    await flushPromises();
    expect(wrapper.find('.textbook-panel').text()).toContain('系统架构设计师教程');
    expect(resourceApi.deleteTextbook).not.toHaveBeenCalled();

    messages.confirm.mockResolvedValueOnce(undefined);
    await deleteButton.trigger('click');
    await flushPromises();
    expect(wrapper.find('.textbook-panel').text()).toContain('系统架构设计师教程');
    expect(messages.error).toHaveBeenCalledWith('教材删除服务不可用');
    wrapper.unmount();
  });

  it('removes visible selected content when a rendered publish action succeeds', async () => {
    resourceApi.getTextbook.mockResolvedValue(textbook);
    resourceApi.publishTextbook.mockResolvedValue({});
    resourceApi.listTextbookChunks.mockResolvedValue({ rows: [], total: 0 });
    knowledgeApi.getKnowledgeTree.mockResolvedValue({ data: { subjects: [{ id: '101', subjectName: '综合知识' }], nodes: [] } });
    const wrapper = mount(ResourcePage, {
      global: { plugins: [ElementPlus], directives: { hasPermi: () => undefined }, stubs: { Pagination: PaginationStub } }
    });
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text().trim() === '搜索')!.trigger('click');
    await flushPromises();
    await wrapper.find('.textbook-panel [aria-label="查看教材内容"]').trigger('click');
    await flushPromises();
    expect(wrapper.find('.content-grid').exists()).toBe(true);

    await wrapper.find('.textbook-publish').trigger('click');
    await flushPromises();

    expect(resourceApi.publishTextbook).toHaveBeenCalledWith(textbook.id);
    expect(wrapper.find('.content-grid').exists()).toBe(false);
    expect(messages.success).toHaveBeenCalledWith('教材已发布，游客和学生端现在可阅读');
    wrapper.unmount();
  });

  it('removes visible selected content when a rendered offline action succeeds', async () => {
    const publishedTextbook = { ...textbook, status: 'published' as const };
    resourceApi.listTextbooks.mockResolvedValue({ rows: [publishedTextbook], total: 1 });
    resourceApi.getTextbook.mockResolvedValue(publishedTextbook);
    resourceApi.takeTextbookOffline.mockResolvedValue({});
    resourceApi.listTextbookChunks.mockResolvedValue({ rows: [], total: 0 });
    knowledgeApi.getKnowledgeTree.mockResolvedValue({ data: { subjects: [{ id: '101', subjectName: '综合知识' }], nodes: [] } });
    const wrapper = mount(ResourcePage, {
      global: { plugins: [ElementPlus], directives: { hasPermi: () => undefined }, stubs: { Pagination: PaginationStub } }
    });
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text().trim() === '搜索')!.trigger('click');
    await flushPromises();
    await wrapper.find('.textbook-panel [aria-label="查看教材内容"]').trigger('click');
    await flushPromises();
    expect(wrapper.find('.content-grid').exists()).toBe(true);

    await wrapper.find('.textbook-offline').trigger('click');
    await flushPromises();

    expect(resourceApi.takeTextbookOffline).toHaveBeenCalledWith(textbook.id);
    expect(wrapper.find('.content-grid').exists()).toBe(false);
    expect(messages.success).toHaveBeenCalledWith('教材已下架，游客和学生端现在不可见');
    wrapper.unmount();
  });

  it('orders a rendered directory and filters it from the visible directory search field', async () => {
    resourceApi.getTextbook.mockResolvedValue(textbook);
    resourceApi.listTextbookChunks.mockResolvedValue({ rows: [], total: 0 });
    knowledgeApi.getKnowledgeTree.mockResolvedValue({
      data: {
        subjects: [{ id: '101', subjectName: '综合知识' }],
        nodes: [
          { id: 'second', examSubjectId: '101', parentId: null, syllabusNumber: '2', syllabusTitle: '第二章', sortOrder: 2 },
          { id: 'second-child', examSubjectId: '101', parentId: 'second', syllabusNumber: '2.1', syllabusTitle: '第二章内容', sortOrder: 1 },
          { id: 'first', examSubjectId: '101', parentId: null, syllabusNumber: '1', syllabusTitle: '第一章', sortOrder: 1 },
          { id: 'first-child', examSubjectId: '101', parentId: 'first', syllabusNumber: '1.1', syllabusTitle: '第一章内容', sortOrder: 1 }
        ]
      }
    });
    const wrapper = mount(ResourcePage, {
      global: { plugins: [ElementPlus], directives: { hasPermi: () => undefined }, stubs: { Pagination: PaginationStub } }
    });
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text().trim() === '搜索')!.trigger('click');
    await flushPromises();
    await wrapper.find('.textbook-panel [aria-label="查看教材内容"]').trigger('click');
    await flushPromises();

    const directory = wrapper.find('.directory-tree');
    expect(directory.text().indexOf('1 第一章')).toBeLessThan(directory.text().indexOf('2 第二章'));
    const directorySearch = wrapper.find('input[placeholder="搜索知识点"]');
    await directorySearch.setValue('第二章');
    await flushPromises();
    expect(wrapper.findAll('.directory-tree .is-hidden').length).toBeGreaterThan(0);
    wrapper.unmount();
  });

  it('keeps the visible chunk editor open when its form validation rejects the save', async () => {
    const chunk = {
      id: '3010', documentId: textbook.id, chunkOrder: 1, heading: '待修改内容', headingPath: [], contentPreview: '正文',
      pageStart: null, pageEnd: null, sourceLocator: { sourceType: null, sourceKey: null, lineStart: null, lineEnd: null }, knowledgePoints: [], mapped: false
    };
    resourceApi.getTextbook.mockResolvedValue(textbook);
    resourceApi.getTextbookChunk.mockResolvedValue({ ...chunk, content: '完整正文', updateTime: 'v1', editable: true });
    resourceApi.listTextbookChunks.mockResolvedValue({ rows: [chunk], total: 1 });
    knowledgeApi.getKnowledgeTree.mockResolvedValue({ data: { subjects: [{ id: '101', subjectName: '综合知识' }], nodes: [] } });
    const wrapper = mount(ResourcePage, {
      global: { plugins: [ElementPlus], directives: { hasPermi: () => undefined }, stubs: { Pagination: PaginationStub, ElDialog: DialogStub } }
    });
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text().trim() === '搜索')!.trigger('click');
    await flushPromises();
    await wrapper.find('.textbook-panel [aria-label="查看教材内容"]').trigger('click');
    await flushPromises();
    await wrapper.find('.chunk-card .el-table__body-wrapper tbody tr').findAll('button')[2]!.trigger('click');
    await flushPromises();

    const editor = wrapper.findAll('.dialog-stub').find(dialog => dialog.attributes('data-title') === '修改内容块')!;
    const vm = wrapper.vm as any;
    vm.editFormRef = { validate: vi.fn().mockRejectedValue(new Error('请输入内容块正文')) };
    await editor.findAll('button').find(button => button.text().includes('保存'))!.trigger('click');
    await flushPromises();

    expect(editor.isVisible()).toBe(true);
    expect(wrapper.find('.chunk-card').text()).toContain('待修改内容');
    expect(resourceApi.updateTextbookChunk).not.toHaveBeenCalled();
    wrapper.unmount();
  });
});
