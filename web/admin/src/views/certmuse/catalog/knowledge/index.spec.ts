import { flushPromises, mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { KeepAlive, defineComponent, h, nextTick, ref } from 'vue';
import KnowledgePage from './index.vue';

const { confirm, success, error, warning } = vi.hoisted(() => ({
  confirm: vi.fn(),
  success: vi.fn(),
  error: vi.fn(),
  warning: vi.fn()
}));
const { listSyllabusVersions, getKnowledgeTree, deleteSyllabusVersion, updateSyllabusPublishedDate } = vi.hoisted(
  () => ({
    listSyllabusVersions: vi.fn(),
    getKnowledgeTree: vi.fn(),
    deleteSyllabusVersion: vi.fn(),
    updateSyllabusPublishedDate: vi.fn()
  })
);
const { listQualifications } = vi.hoisted(() => ({ listQualifications: vi.fn() }));

vi.mock('element-plus', async importOriginal => {
  const elementPlus = await importOriginal<typeof import('element-plus')>();
  return { ...elementPlus, ElMessageBox: { confirm }, ElMessage: { error, success, warning } };
});
vi.mock('@/api/certmuse/catalog/knowledge', () => ({
  listSyllabusVersions,
  getKnowledgeTree,
  deleteSyllabusVersion,
  updateSyllabusPublishedDate
}));
vi.mock('@/api/certmuse/catalog/subject-version', () => ({ listQualifications }));
vi.mock('@/views/certmuse/catalog/import/ImportDialog.vue', () => ({
  default: {
    emits: ['closed'],
    props: ['modelValue', 'initialSyllabusVersionId'],
    template:
      '<div class="import-dialog-stub" :data-visible="modelValue" :data-syllabus-version-id="initialSyllabusVersionId" @click="$emit(\'closed\')" />'
  }
}));

const syllabus = {
  id: '20001',
  displayName: '系统架构设计师 · 第二版',
  certificationId: '10001',
  certificationName: '系统架构设计师',
  versionName: '第二版',
  publishedDate: '2026-01-01',
  updateTime: '2026-08-13T09:55:12+08:00',
  latestKnowledgeImportTime: '2026-07-31T09:00:00+08:00',
  knowledgePointCount: 5,
  status: 'available'
};

const qualification = {
  id: '10001',
  certificationName: '系统架构设计师',
  versions: [{ id: '20001', updateTime: '2026-08-04T09:00:00+08:00' }]
};

const nodeBase = {
  examSubjectId: '101',
  examSubjectCode: 'COMPREHENSIVE',
  importance: null,
  diagnosticEnabled: false,
  recommendationEnabled: false,
  status: '0',
  createdTime: '2026-07-31T09:00:00+08:00',
  updatedTime: '2026-07-31T09:00:00+08:00'
};

const tree = {
  syllabus: { ...syllabus, createdTime: '2026-07-31T09:00:00+08:00', latestKnowledgeImport: null },
  summary: { syllabusVersionRecordCount: 1, subjectCount: 1, knowledgePointCount: 5, directoryCount: 1, leafCount: 4 },
  subjects: [
    { id: '101', subjectCode: 'COMPREHENSIVE', subjectName: '综合知识', sortOrder: 1, knowledgePointCount: 5 }
  ],
  nodes: [
    {
      ...nodeBase,
      id: '1',
      parentId: null,
      syllabusNumber: '1.1',
      syllabusTitle: '系统基础',
      treeDepth: 1,
      sortOrder: 1,
      hasChildren: true
    },
    {
      ...nodeBase,
      id: '2',
      parentId: '1',
      syllabusNumber: '1.1.1',
      syllabusTitle: '软件架构设计',
      treeDepth: 2,
      sortOrder: 1,
      hasChildren: false
    },
    {
      ...nodeBase,
      id: '3',
      parentId: null,
      syllabusNumber: '1.2',
      syllabusTitle: '系统分析',
      treeDepth: 1,
      sortOrder: 2,
      hasChildren: false
    },
    {
      ...nodeBase,
      id: '4',
      parentId: null,
      syllabusNumber: '1.3',
      syllabusTitle: '系统实现',
      treeDepth: 1,
      sortOrder: 3,
      hasChildren: false
    },
    {
      ...nodeBase,
      id: '5',
      parentId: null,
      syllabusNumber: '1.4',
      syllabusTitle: '系统维护',
      treeDepth: 1,
      sortOrder: 4,
      hasChildren: false
    }
  ]
};

function mountPage() {
  return mount(KnowledgePage, { global: { plugins: [ElementPlus], stubs: { Pagination: true } } });
}

function mountKeptAlivePage() {
  const KeepAliveHost = defineComponent({
    setup(_, { expose }) {
      const active = ref(true);
      expose({ setActive: (value: boolean) => (active.value = value) });
      return () => h(KeepAlive, null, { default: () => (active.value ? h(KnowledgePage) : null) });
    }
  });
  return mount(KeepAliveHost, { global: { plugins: [ElementPlus], stubs: { Pagination: true } } });
}

async function reactivatePage(wrapper: ReturnType<typeof mountKeptAlivePage>) {
  const host = wrapper.vm as unknown as { setActive: (value: boolean) => void };
  host.setActive(false);
  await nextTick();
  host.setActive(true);
  await flushPromises();
}

async function selectSyllabus(wrapper: ReturnType<typeof mountPage>) {
  await flushPromises();
  await wrapper.find('.query-form .el-button--primary').trigger('click');
  await flushPromises();
  await wrapper.find('.syllabus-result').trigger('click');
  await flushPromises();
}

describe('knowledge syllabus management page', () => {
  beforeEach(() => {
    confirm.mockReset();
    success.mockReset();
    error.mockReset();
    warning.mockReset();
    listSyllabusVersions.mockReset();
    getKnowledgeTree.mockReset();
    deleteSyllabusVersion.mockReset();
    updateSyllabusPublishedDate.mockReset();
    listQualifications.mockReset();
    listSyllabusVersions.mockResolvedValue({ data: { rows: [syllabus], total: 1 } });
    getKnowledgeTree.mockResolvedValue({ data: tree });
    deleteSyllabusVersion.mockResolvedValue({});
    updateSyllabusPublishedDate.mockResolvedValue({});
    listQualifications.mockResolvedValue({ data: { rows: [qualification], total: 1 } });
  });

  afterEach(() => vi.unstubAllGlobals());

  it('keeps syllabus search and loads management panels after selection', async () => {
    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.text()).toContain('筛选条件');
    expect(wrapper.text()).not.toContain('系统架构设计师 · 第二版');
    expect(wrapper.find('.management-layout').exists()).toBe(false);

    await wrapper.find('.query-form .el-button--primary').trigger('click');
    await flushPromises();
    expect(wrapper.find('.syllabus-panel').text()).toContain('系统架构设计师');
    expect(wrapper.find('.syllabus-panel').text()).not.toContain('系统架构设计师 · 第二版');
    expect(wrapper.find('.syllabus-panel .data-table').attributes('style') ?? '').not.toContain('height');

    await wrapper.find('.syllabus-result').trigger('click');
    await flushPromises();

    expect(getKnowledgeTree).toHaveBeenCalledWith('20001');
    expect(wrapper.find('.directory-tree-scroll').exists()).toBe(true);
    expect(wrapper.find('.leaf-table-panel').exists()).toBe(true);
  });

  it('loads enabled qualification options independently of the paged syllabus list', async () => {
    listQualifications.mockResolvedValue({
      data: {
        rows: [
          { id: '10001', certificationName: '系统架构设计师' },
          { id: '10002', certificationName: '系统分析师' }
        ],
        total: 2
      }
    });

    const wrapper = mountPage();
    await flushPromises();

    expect(listQualifications).toHaveBeenCalledWith({ status: '0', pageNum: 1, pageSize: 100 });
    const certificationOptions = wrapper
      .findAll('.query-form .el-select__wrapper')
      .map(option => option.attributes('aria-label') ?? option.text());
    expect(certificationOptions).toHaveLength(2);
  });

  it('loads every qualification page before offering all available certification filters', async () => {
    listQualifications.mockResolvedValueOnce({
      data: {
        rows: Array.from({ length: 100 }, (_, index) => ({
          id: String(index + 1),
          certificationName: `资格 ${index + 1}`
        })),
        total: 101
      }
    });
    listQualifications.mockResolvedValueOnce({
      data: { rows: [{ id: '101', certificationName: '资格 101' }], total: 101 }
    });

    const wrapper = mountPage();
    await flushPromises();

    expect(listQualifications).toHaveBeenNthCalledWith(2, { status: '0', pageNum: 2, pageSize: 100 });
    await wrapper.findAll('.query-form .el-select__wrapper')[0]!.trigger('click');
    await flushPromises();
    expect(document.body.textContent).toContain('资格 101');
  });

  it('shows the syllabus list update time and opens import with the row qualification selected', async () => {
    const wrapper = mountPage();
    await flushPromises();
    await wrapper.find('.query-form .el-button--primary').trigger('click');
    await flushPromises();

    expect(wrapper.find('.syllabus-panel').text()).not.toContain('考纲名称');
    expect(wrapper.text()).toContain('更新时间');
    expect(wrapper.text()).toContain('2026/8/13 09:55:12');

    const addButton = wrapper.find('.syllabus-add');
    expect(addButton.exists()).toBe(true);
    expect(addButton.text()).toBe('');
    await addButton.trigger('click');
    await flushPromises();

    const importDialog = wrapper.find('.import-dialog-stub');
    expect(importDialog.attributes('data-visible')).toBe('true');
    expect(importDialog.attributes('data-syllabus-version-id')).toBe('20001');
  });

  it('shows the refreshed syllabus result when the import dialog closes', async () => {
    const wrapper = mountPage();
    await flushPromises();
    await wrapper.find('.query-form .el-button--primary').trigger('click');
    await flushPromises();

    await wrapper.find('.syllabus-panel .toolbar-actions .el-button--primary').trigger('click');
    listSyllabusVersions.mockResolvedValueOnce({ data: { rows: [], total: 0 } });
    await wrapper.find('.import-dialog-stub').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('没有符合条件的考纲');
    expect(wrapper.find('.syllabus-result').exists()).toBe(false);
  });

  it('opens a new import without preselecting an existing syllabus', async () => {
    const wrapper = mountPage();
    await flushPromises();
    await wrapper.find('.query-form .el-button--primary').trigger('click');
    await flushPromises();

    await wrapper.find('.syllabus-panel .toolbar-actions .el-button--primary').trigger('click');
    await flushPromises();

    const importDialog = wrapper.get('.import-dialog-stub');
    expect(importDialog.attributes('data-visible')).toBe('true');
    expect(importDialog.attributes('data-syllabus-version-id')).toBeUndefined();
  });

  it('opens the compact edit action and saves the published date', async () => {
    const wrapper = mountPage();
    await flushPromises();
    await (wrapper.vm as unknown as { handleSearch: () => Promise<void> }).handleSearch();
    await flushPromises();

    const editButton = wrapper.find('.syllabus-edit');
    expect(editButton.exists()).toBe(true);
    expect(editButton.text()).toBe('');
    await editButton.trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('编辑发布日期');
    const vm = wrapper.vm as unknown as {
      publishedDateForm: { publishedDate: string };
      submitPublishedDate: () => Promise<void>;
    };
    vm.publishedDateForm.publishedDate = '2026-08-13';
    await vm.submitPublishedDate();

    expect(updateSyllabusPublishedDate).toHaveBeenCalledWith('20001', { publishedDate: '2026-08-13' });
    expect(success).toHaveBeenCalledWith('发布日期已更新');
  });

  it('keeps the published-date dialog open and explains why an empty date cannot be saved', async () => {
    listSyllabusVersions.mockResolvedValue({ data: { rows: [{ ...syllabus, publishedDate: null }], total: 1 } });
    const wrapper = mountPage();
    await flushPromises();
    await wrapper.find('.query-form .el-button--primary').trigger('click');
    await flushPromises();

    await wrapper.find('.syllabus-edit').trigger('click');
    await flushPromises();
    expect(wrapper.get('input[placeholder="请选择发布日期"]').element).toHaveProperty('value', '');

    await wrapper.find('.el-dialog .el-button--primary').trigger('click');
    await flushPromises();

    expect(warning).toHaveBeenCalledWith('请选择发布日期');
    expect(wrapper.text()).toContain('编辑发布日期');
    expect(updateSyllabusPublishedDate).not.toHaveBeenCalled();
  });

  it('stops the directory at non-leaf nodes and lists all descendant leaves', async () => {
    const wrapper = mountPage();
    await selectSyllabus(wrapper);

    const directoryLabels = wrapper.findAll('.node-label').map(item => item.text());
    expect(directoryLabels).toEqual(['综合知识', '1.1 系统基础']);
    expect(directoryLabels).not.toContain('1.1.1 软件架构设计');
    expect(wrapper.findAll('.knowledge-table .el-table__body-wrapper tbody tr')).toHaveLength(4);

    await wrapper.findAll('.directory-tree .el-tree-node__content')[1].trigger('click');
    await flushPromises();

    expect(wrapper.text()).not.toContain('综合知识 / 1.1 系统基础');
    expect(wrapper.text()).toContain('知识点列表');
    expect(wrapper.findAll('.knowledge-table .el-table__body-wrapper tbody tr')).toHaveLength(1);
    expect(wrapper.text()).toContain('软件架构设计');
  });

  it('uses the same collapsible knowledge directory layout as textbook management', async () => {
    const wrapper = mountPage();
    await selectSyllabus(wrapper);

    await wrapper.find('.tree-panel-header').trigger('click');
    await nextTick();

    expect(wrapper.find('.tree-panel-col').classes()).toContain('is-collapsed');
    expect(wrapper.find('.tree-panel-shell').classes()).toContain('is-collapsed');
    expect(wrapper.find('.tree-content-col').classes()).toContain('is-tree-collapsed');
    expect(wrapper.find('.directory-tree-scroll').exists()).toBe(false);
  });

  it('groups descendant leaves by their full hierarchical syllabus number', async () => {
    getKnowledgeTree.mockResolvedValue({
      data: {
        ...tree,
        nodes: [
          {
            ...nodeBase,
            id: '10',
            parentId: null,
            syllabusNumber: '2.2',
            syllabusTitle: '架构设计',
            treeDepth: 1,
            sortOrder: 1,
            hasChildren: true
          },
          {
            ...nodeBase,
            id: '11',
            parentId: '10',
            syllabusNumber: '2.2.3.1',
            syllabusTitle: '架构方法',
            treeDepth: 2,
            sortOrder: 1,
            hasChildren: true
          },
          {
            ...nodeBase,
            id: '12',
            parentId: '10',
            syllabusNumber: '2.2.4.1',
            syllabusTitle: '架构案例',
            treeDepth: 2,
            sortOrder: 2,
            hasChildren: true
          },
          {
            ...nodeBase,
            id: '13',
            parentId: '11',
            syllabusNumber: '2.2.3.1.1',
            syllabusTitle: '方法一',
            treeDepth: 3,
            sortOrder: 1,
            hasChildren: false
          },
          {
            ...nodeBase,
            id: '14',
            parentId: '12',
            syllabusNumber: '2.2.4.1.1',
            syllabusTitle: '案例一',
            treeDepth: 3,
            sortOrder: 1,
            hasChildren: false
          },
          {
            ...nodeBase,
            id: '15',
            parentId: '11',
            syllabusNumber: '2.2.3.1.2',
            syllabusTitle: '方法二',
            treeDepth: 3,
            sortOrder: 2,
            hasChildren: false
          },
          {
            ...nodeBase,
            id: '16',
            parentId: '12',
            syllabusNumber: '2.2.4.1.2',
            syllabusTitle: '案例二',
            treeDepth: 3,
            sortOrder: 2,
            hasChildren: false
          }
        ]
      }
    });
    const wrapper = mountPage();
    await selectSyllabus(wrapper);

    const numbers = wrapper
      .findAll('.knowledge-table .el-table__body-wrapper tbody tr')
      .map(row => row.find('td').text());
    expect(numbers).toEqual(['2.2.3.1.1', '2.2.3.1.2', '2.2.4.1.1', '2.2.4.1.2']);
  });

  it('filters leaf knowledge points by title', async () => {
    const wrapper = mountPage();
    await selectSyllabus(wrapper);

    await wrapper.find('input[placeholder="请输入知识点名称"]').setValue('系统维护');
    await flushPromises();

    expect(wrapper.findAll('.knowledge-table .el-table__body-wrapper tbody tr')).toHaveLength(1);
    expect(wrapper.text()).toContain('系统维护');
  });

  it('searches by syllabus number, resets leaf filters, and returns to the selected directory', async () => {
    const wrapper = mountPage();
    await selectSyllabus(wrapper);

    await wrapper.find('input[placeholder="请输入知识点编号"]').setValue('1.1.1');
    await wrapper.find('.leaf-search-wrap .el-button--primary').trigger('click');
    await flushPromises();
    expect(wrapper.findAll('.knowledge-table .el-table__body-wrapper tbody tr')).toHaveLength(1);
    expect(wrapper.text()).toContain('软件架构设计');

    await wrapper.find('.leaf-search-wrap .el-button:not(.el-button--primary)').trigger('click');
    await flushPromises();
    expect(wrapper.findAll('.knowledge-table .el-table__body-wrapper tbody tr')).toHaveLength(4);
  });

  it('searches all subjects when filtering by knowledge point name', async () => {
    getKnowledgeTree.mockResolvedValue({
      data: {
        ...tree,
        subjects: [
          ...tree.subjects,
          { id: '102', subjectCode: 'CASE', subjectName: '案例分析', sortOrder: 2, knowledgePointCount: 1 }
        ],
        nodes: [
          ...tree.nodes,
          {
            ...nodeBase,
            id: '6',
            examSubjectId: '102',
            examSubjectCode: 'CASE',
            parentId: null,
            syllabusNumber: '2.1',
            syllabusTitle: '软件案例分析',
            treeDepth: 1,
            sortOrder: 1,
            hasChildren: false
          }
        ]
      }
    });
    const wrapper = mountPage();
    await selectSyllabus(wrapper);

    await wrapper.find('input[placeholder="请输入知识点名称"]').setValue('软件');
    await wrapper.find('.leaf-search-wrap .el-button--primary').trigger('click');
    await flushPromises();
    expect(wrapper.findAll('.knowledge-table .el-table__body-wrapper tbody tr')).toHaveLength(2);
  });

  it('soft-deletes the syllabus while retaining the qualification', async () => {
    confirm.mockResolvedValue(undefined);
    const wrapper = mountPage();
    await flushPromises();
    await wrapper.find('.query-form .el-button--primary').trigger('click');
    await flushPromises();

    expect(wrapper.find('.el-table__header-wrapper .el-checkbox').exists()).toBe(true);
    expect(wrapper.find('pagination-stub').exists()).toBe(true);
    const deleteButton = wrapper.find('.syllabus-delete');
    expect(deleteButton.text()).toBe('');
    await deleteButton.trigger('click');
    await flushPromises();

    expect(confirm).toHaveBeenCalledOnce();
    expect(deleteSyllabusVersion).toHaveBeenCalledWith('20001');
    expect(success).toHaveBeenCalledWith('考纲已删除');
  });

  it('enables bulk deletion after selecting syllabus rows', async () => {
    confirm.mockResolvedValue(undefined);
    const wrapper = mountPage();
    await flushPromises();
    await wrapper.find('.query-form .el-button--primary').trigger('click');
    await flushPromises();

    wrapper.find('.syllabus-panel').findComponent({ name: 'ElTable' }).vm.$emit('selection-change', [syllabus]);
    await flushPromises();

    await wrapper.find('.syllabus-panel .toolbar-actions .el-button--danger').trigger('click');
    await flushPromises();
    expect(deleteSyllabusVersion).toHaveBeenCalledWith('20001');
  });

  it('keeps bulk deletion unavailable until the user selects a syllabus', async () => {
    const wrapper = mountPage();
    await flushPromises();
    await wrapper.find('.query-form .el-button--primary').trigger('click');
    await flushPromises();

    const bulkDelete = wrapper.get('.syllabus-panel .toolbar-actions .el-button--danger');
    expect(bulkDelete.attributes('disabled')).toBeDefined();
    expect(bulkDelete.classes()).toContain('is-disabled');
  });

  it('returns to the selection prompt after deleting the syllabus currently being viewed', async () => {
    confirm.mockResolvedValue(undefined);
    const wrapper = mountPage();
    await selectSyllabus(wrapper);
    expect(wrapper.find('.management-layout').exists()).toBe(true);

    await wrapper.find('.syllabus-delete').trigger('click');
    await flushPromises();

    expect(wrapper.find('.management-layout').exists()).toBe(false);
    expect(wrapper.find('.selection-empty').text()).toContain('请先从上方选择一个考纲');
  });

  it('keeps the shared pagination visible when the current page has rows but the response total is zero', async () => {
    listSyllabusVersions.mockResolvedValueOnce({ data: { rows: [syllabus], total: 0 } });
    const wrapper = mountPage();
    await flushPromises();
    await wrapper.find('.query-form .el-button--primary').trigger('click');
    await flushPromises();

    expect(wrapper.find('pagination-stub').attributes('total')).toBe('1');
  });

  it('handles empty, failed list and failed tree responses', async () => {
    listSyllabusVersions.mockRejectedValueOnce(new Error('offline'));
    const failedList = mountPage();
    await flushPromises();
    await failedList.find('.query-form .el-button--primary').trigger('click');
    await flushPromises();
    expect(error).toHaveBeenCalledWith('考纲查询失败，请稍后重试');

    listSyllabusVersions.mockResolvedValueOnce({ data: { rows: [syllabus], total: 1 } });
    getKnowledgeTree.mockRejectedValueOnce(new Error('broken tree'));
    const failedTree = mountPage();
    await selectSyllabus(failedTree);
    expect(error).toHaveBeenCalledWith('知识点列表加载失败，请稍后重试');
    expect(failedTree.find('.management-layout').exists()).toBe(true);
  });

  it('supports reset, directory filtering and no-match leaf searches', async () => {
    const wrapper = mountPage();
    await selectSyllabus(wrapper);
    await wrapper.find('input[placeholder="搜索知识点"]').setValue('系统基础');
    await wrapper.find('input[placeholder="请输入知识点编号"]').setValue('missing');
    await wrapper.find('.leaf-search-wrap .el-button--primary').trigger('click');
    await flushPromises();
    expect(wrapper.findAll('.knowledge-table .el-table__body-wrapper tbody tr')).toHaveLength(0);

    await wrapper.find('.leaf-search-wrap .el-button:not(.el-button--primary)').trigger('click');
    await wrapper.find('input[placeholder="如：2026、系统架构"]').setValue('架构');
    await wrapper.find('.query-form .el-button:not(.el-button--primary)').trigger('click');
    await flushPromises();
    expect(wrapper.find('.syllabus-panel').exists()).toBe(false);
  });

  it('does not delete when confirmation is cancelled and reports delete failures', async () => {
    confirm.mockRejectedValueOnce(new Error('cancel'));
    const cancelled = mountPage();
    await flushPromises();
    await cancelled.find('.query-form .el-button--primary').trigger('click');
    await flushPromises();
    await cancelled.find('.syllabus-delete').trigger('click');
    await flushPromises();
    expect(deleteSyllabusVersion).not.toHaveBeenCalled();

    confirm.mockResolvedValueOnce(undefined);
    deleteSyllabusVersion.mockRejectedValueOnce(new Error('server error'));
    await cancelled.find('.syllabus-delete').trigger('click');
    await flushPromises();
    expect(error).toHaveBeenCalledWith('删除考纲失败，请稍后重试');
  });

  it('keeps the selected directory after activation when the syllabus still exists', async () => {
    const host = mountKeptAlivePage();
    await flushPromises();
    const page = host.getComponent(KnowledgePage) as ReturnType<typeof mountPage>;
    await selectSyllabus(page);
    await page.findAll('.directory-tree .el-tree-node__content')[1].trigger('click');
    await flushPromises();
    expect(page.findAll('.knowledge-table .el-table__body-wrapper tbody tr')).toHaveLength(1);

    await reactivatePage(host);
    const reactivatedPage = host.getComponent(KnowledgePage) as ReturnType<typeof mountPage>;

    expect(reactivatedPage.find('.management-layout').exists()).toBe(true);
    expect(reactivatedPage.findAll('.knowledge-table .el-table__body-wrapper tbody tr')).toHaveLength(1);
    expect(reactivatedPage.find('.table-footer').text()).toContain('系统架构设计师 · 第二版');
  });

  it('returns to the selection prompt after activation when the previous syllabus no longer exists', async () => {
    const host = mountKeptAlivePage();
    await flushPromises();
    const page = host.getComponent(KnowledgePage) as ReturnType<typeof mountPage>;
    await selectSyllabus(page);
    expect(page.find('.management-layout').exists()).toBe(true);

    listSyllabusVersions.mockResolvedValueOnce({ data: { rows: [], total: 0 } });
    await reactivatePage(host);
    const reactivatedPage = host.getComponent(KnowledgePage) as ReturnType<typeof mountPage>;

    expect(reactivatedPage.find('.management-layout').exists()).toBe(false);
    expect(reactivatedPage.find('.selection-empty').text()).toContain('请先从上方选择一个考纲');
  });
});
