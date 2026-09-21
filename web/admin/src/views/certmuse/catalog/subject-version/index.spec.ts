import { flushPromises, mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { computed, onMounted, reactive, ref } from 'vue';
import SubjectVersionPage from './index.vue';

const api = vi.hoisted(() => ({
  createQualification: vi.fn(),
  deleteQualification: vi.fn(),
  listQualifications: vi.fn(),
  updateQualification: vi.fn()
}));
const messages = vi.hoisted(() => ({ confirm: vi.fn(), error: vi.fn(), success: vi.fn() }));

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

vi.mock('@/api/certmuse/catalog/subject-version', () => api);
vi.mock('element-plus', async importOriginal => {
  const elementPlus = await importOriginal<typeof import('element-plus')>();
  return { ...elementPlus, ElMessage: { error: messages.error, success: messages.success }, ElMessageBox: { confirm: messages.confirm } };
});

const rows = [
  {
    id: '1',
    certificationCode: 'SYSTEM_ARCHITECT',
    certificationName: '系统架构设计师',
    qualificationLevel: 'HIGH',
    status: '0',
    sortOrder: 1,
    versionCount: 1,
    referenceCount: 1,
    createTime: '2026-08-04T09:00:00+08:00',
    updateTime: '2026-08-04T09:00:00+08:00',
    versions: [
      {
        id: '11',
        certificationId: '1',
        versionName: '第二版',
        publishedDate: '2024-05-01',
        referenceCount: 1,
        createTime: '2026-08-04T09:00:00+08:00',
        updateTime: '2026-08-04T09:00:00+08:00'
      },
      {
        id: '12',
        certificationId: '1',
        versionName: '第三版',
        publishedDate: '2025-01-01',
        referenceCount: 1,
        createTime: '2026-08-05T09:00:00+08:00',
        updateTime: '2026-08-05T09:00:00+08:00'
      }
    ]
  },
  {
    id: '2',
    certificationCode: 'SOFTWARE_DESIGNER',
    certificationName: '软件设计师',
    qualificationLevel: 'MIDDLE',
    status: '0',
    sortOrder: 2,
    versionCount: 1,
    referenceCount: 0,
    createTime: '2026-08-04T09:00:00+08:00',
    updateTime: '2026-08-04T09:00:00+08:00',
    versions: [
      {
        id: '21',
        certificationId: '2',
        versionName: '第六版',
        publishedDate: '2025-01-01',
        referenceCount: 0,
        createTime: '2026-08-04T09:00:00+08:00',
        updateTime: '2026-08-04T09:00:00+08:00'
      }
    ]
  }
];

describe('qualification page', () => {
  beforeEach(() => {
    vi.stubGlobal('ref', ref);
    vi.stubGlobal('reactive', reactive);
    vi.stubGlobal('computed', computed);
    vi.stubGlobal('onMounted', onMounted);
    Object.values(api).forEach(mock => mock.mockReset());
    Object.values(messages).forEach(mock => mock.mockReset());
    api.listQualifications.mockResolvedValue({ data: { rows, total: rows.length } });
    messages.confirm.mockResolvedValue(undefined);
  });

  it('renders qualification rows without exposing versions', async () => {
    const wrapper = mount(SubjectVersionPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();
    expect(wrapper.text()).toContain('资格列表');
    expect(wrapper.text()).toContain('系统架构设计师');
    expect(wrapper.text()).not.toContain('SYSTEM_ARCHITECT');
    expect(wrapper.text()).not.toContain('第二版');
    expect(wrapper.text()).not.toContain('第三版');
    expect(wrapper.text()).not.toContain('新建版本');
    expect(wrapper.findAll('.el-table__row')).toHaveLength(2);
    expect(wrapper.find('.el-table-column--selection').exists()).toBe(true);
    expect(wrapper.find('.pagination-container').exists()).toBe(true);
    expect(api.listQualifications).toHaveBeenCalledWith(expect.objectContaining({ pageNum: 1, pageSize: 10 }));
  });

  it('opens the qualification editor for the selected row', async () => {
    const wrapper = mount(SubjectVersionPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();
    const vm = wrapper.vm as unknown as {
      qualifications: typeof rows;
      handleSelectionChange: (selectedRows: typeof rows) => void;
      editSelectedRow: () => void;
      qualificationDialog: { visible: boolean; id: string };
    };

    const checkboxes = wrapper.findAll('.el-table__body-wrapper .el-checkbox');
    expect(checkboxes[0].classes()).not.toContain('is-disabled');
    expect(checkboxes[1].classes()).not.toContain('is-disabled');

    vm.handleSelectionChange([vm.qualifications[0]]);
    vm.editSelectedRow();
    expect(vm.qualificationDialog).toMatchObject({ visible: true, id: '1' });
  });

  it('selects multiple qualifications independently', async () => {
    const wrapper = mount(SubjectVersionPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();
    const vm = wrapper.vm as unknown as {
      selectedRows: typeof rows;
    };
    const checkboxes = wrapper.findAll('.el-table__body-wrapper .el-checkbox');
    await checkboxes[0].find('input').setValue(true);
    await checkboxes[1].find('input').setValue(true);

    expect(vm.selectedRows).toHaveLength(2);
    expect(vm.selectedRows.map(row => row.id)).toEqual(['1', '2']);
  });

  it('applies and resets filter values', async () => {
    const wrapper = mount(SubjectVersionPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();
    const vm = wrapper.vm as unknown as {
      draftFilters: { keyword: string };
      applyFilters: () => Promise<void>;
      resetFilters: () => Promise<void>;
    };
    vm.draftFilters.keyword = '架构设计师';
    await vm.applyFilters();
    expect(api.listQualifications).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: '架构设计师' }));
    await vm.resetFilters();
    expect(api.listQualifications).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: '' }));
  });

  it('shows an empty result state', async () => {
    api.listQualifications.mockResolvedValue({ data: { rows: [], total: 0 } });
    const wrapper = mount(SubjectVersionPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();
    expect(wrapper.find('.el-table__empty-block').exists()).toBe(true);
  });

  it('keeps pagination visible when the service omits a nonzero total', async () => {
    api.listQualifications.mockResolvedValue({ data: { rows, total: 0 } });
    const wrapper = mount(SubjectVersionPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    expect((wrapper.vm as unknown as { displayedQualificationTotal: number }).displayedQualificationTotal).toBe(rows.length);
  });

  it('collapses the filter card and enables batch actions after selecting rows', async () => {
    const wrapper = mount(SubjectVersionPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();

    const filterToggle = wrapper.find('.search-panel-toggle');
    await filterToggle.trigger('click');
    expect(wrapper.find('.search-panel-toggle').attributes('aria-expanded')).toBe('false');

    const vm = wrapper.vm as unknown as {
      qualifications: typeof rows;
      handleSelectionChange: (items: typeof rows) => void;
      single: boolean;
      multiple: boolean;
      openQualificationDialog: () => void;
      qualificationForm: { certificationCode: string; status: string; sortOrder: number };
    };
    vm.handleSelectionChange([vm.qualifications[0]]);
    expect(vm.single).toBe(false);
    expect(vm.multiple).toBe(false);
    vm.handleSelectionChange([]);
    expect(vm.single).toBe(true);
    expect(vm.multiple).toBe(true);

    vm.openQualificationDialog();
    expect(vm.qualificationForm.certificationCode).toBe('NULL_1');
    expect(vm.qualificationForm.status).toBe('0');
    expect(vm.qualificationForm.sortOrder).toBe(3);
  });

  it('creates, updates and deletes qualifications through guarded actions', async () => {
    api.createQualification.mockResolvedValue({});
    api.updateQualification.mockResolvedValue({});
    api.deleteQualification.mockResolvedValue({});
    const wrapper = mount(SubjectVersionPage, { global: { plugins: [ElementPlus] } });
    await flushPromises();
    const vm = wrapper.vm as any;
    vm.qualificationFormRef = { validate: vi.fn().mockResolvedValue(true) };

    vm.openQualificationDialog();
    await vm.saveQualification();
    expect(api.createQualification).toHaveBeenCalledWith(expect.objectContaining({ certificationCode: 'NULL_1' }));
    expect(messages.success).toHaveBeenCalledWith('资格已新增');

    vm.openQualificationDialog(rows[0]);
    await vm.saveQualification();
    expect(api.updateQualification).toHaveBeenCalledWith('1', expect.objectContaining({ certificationName: '系统架构设计师' }));
    expect(messages.success).toHaveBeenCalledWith('资格已更新');

    await vm.confirmDeleteQualification(rows[0]);
    expect(api.deleteQualification).toHaveBeenCalledWith('1');
    expect(messages.success).toHaveBeenCalledWith('资格已删除');

    vm.selectedRows = [rows[0], rows[1]];
    await vm.confirmDeleteSelected();
    expect(api.deleteQualification).toHaveBeenCalledWith('2');
    expect(messages.success).toHaveBeenCalledWith('选中内容已删除');
    expect(vm.levelLabel('MIDDLE')).toBe('中级');
    expect(vm.statusLabel('1')).toBe('停用');
    expect(vm.errorMessage(new Error('坏请求'), 'fallback')).toBe('坏请求');
    expect(vm.errorMessage('bad', 'fallback')).toBe('fallback');
  });

  it('submits rendered filter and pagination controls in the backend query contract', async () => {
    const wrapper = mount(SubjectVersionPage, {
      global: { plugins: [ElementPlus], stubs: { Pagination: PaginationStub, ElDialog: DialogStub } }
    });
    await flushPromises();

    const keyword = wrapper.find('input[placeholder="请输入资格名称"]');
    const filters = wrapper.find('.query-form').findAllComponents({ name: 'ElSelect' });
    await keyword.setValue('架构设计师');
    filters[0]!.vm.$emit('update:modelValue', 'HIGH');
    filters[1]!.vm.$emit('update:modelValue', '1');
    await keyword.trigger('keyup.enter');
    await flushPromises();

    expect(api.listQualifications).toHaveBeenLastCalledWith({
      keyword: '架构设计师', qualificationLevel: 'HIGH', status: '1', pageNum: 1, pageSize: 10
    });

    await wrapper.find('.pagination-page').trigger('click');
    await wrapper.find('.pagination-limit').trigger('click');
    await wrapper.find('.pagination-load').trigger('click');
    await flushPromises();
    expect(api.listQualifications).toHaveBeenLastCalledWith({
      keyword: '架构设计师', qualificationLevel: 'HIGH', status: '1', pageNum: 3, pageSize: 25
    });
  });

  it('creates and closes a qualification through rendered dialog controls', async () => {
    api.createQualification.mockResolvedValue({});
    const wrapper = mount(SubjectVersionPage, {
      global: { plugins: [ElementPlus], stubs: { Pagination: PaginationStub, ElDialog: DialogStub } }
    });
    await flushPromises();

    const addButton = wrapper
      .findAll('.qualification-results-panel .toolbar-actions button')
      .find(button => button.text().includes('新增资格'));
    await addButton!.trigger('click');
    await flushPromises();
    let dialog = wrapper.find('.dialog-stub');
    expect(dialog.attributes('data-title')).toBe('新增资格');
    await dialog.find('input[placeholder="如 系统架构设计师"]').setValue('网络规划设计师');
    dialog.findComponent({ name: 'ElSelect' }).vm.$emit('update:modelValue', 'HIGH');
    await flushPromises();
    await dialog.findAll('button').find(button => button.text().includes('保存'))!.trigger('click');
    await flushPromises();
    expect(api.createQualification).toHaveBeenCalledWith(
      expect.objectContaining({ certificationName: '网络规划设计师', qualificationLevel: 'HIGH' })
    );
    expect(messages.success).toHaveBeenCalledWith('资格已新增');

    await addButton!.trigger('click');
    await flushPromises();
    dialog = wrapper.find('.dialog-stub');
    await dialog.find('.dialog-model-close').trigger('click');
    expect(dialog.isVisible()).toBe(false);
  });

  it('opens and deletes a qualification through its rendered row actions', async () => {
    api.deleteQualification.mockResolvedValue({});
    const wrapper = mount(SubjectVersionPage, {
      global: { plugins: [ElementPlus], stubs: { Pagination: PaginationStub, ElDialog: DialogStub } }
    });
    await flushPromises();

    const firstRow = wrapper.find('.qualification-results-panel .el-table__body-wrapper tbody tr');
    await firstRow.find('.el-link').trigger('click');
    await flushPromises();
    expect(wrapper.find('.dialog-stub').attributes('data-title')).toBe('编辑资格');
    await wrapper.find('.dialog-model-close').trigger('click');

    const actionButtons = firstRow.findAll('button');
    await actionButtons[0]!.trigger('click');
    await flushPromises();
    expect(wrapper.find('.dialog-stub').attributes('data-title')).toBe('编辑资格');
    await wrapper.find('.dialog-model-close').trigger('click');
    await actionButtons[1]!.trigger('click');
    await flushPromises();
    expect(messages.confirm).toHaveBeenCalledWith(expect.stringContaining('系统架构设计师'), '删除资格', expect.any(Object));
    expect(api.deleteQualification).toHaveBeenCalledWith('1');
  });

  it('supports keyboard filter collapse and keeps an invalid qualification form open for correction', async () => {
    const wrapper = mount(SubjectVersionPage, {
      global: { plugins: [ElementPlus], stubs: { Pagination: PaginationStub, ElDialog: DialogStub } }
    });
    await flushPromises();

    wrapper.find('.search-panel-toggle').element.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, key: 'Enter' }));
    await wrapper.vm.$nextTick();
    expect(wrapper.find('.search-panel-toggle').attributes('aria-expanded')).toBe('false');

    const addButton = wrapper
      .findAll('.qualification-results-panel .toolbar-actions button')
      .find(button => button.text().includes('新增资格'));
    await addButton!.trigger('click');
    await flushPromises();
    const vm = wrapper.vm as any;
    vm.qualificationFormRef = { validate: vi.fn().mockRejectedValue(new Error('请输入资格名称')) };
    const dialog = wrapper.find('.dialog-stub');
    await dialog.findAll('button').find(button => button.text().trim() === '保存')!.trigger('click');
    await flushPromises();
    expect(api.createQualification).not.toHaveBeenCalled();
    expect(vm.qualificationDialog.visible).toBe(true);

    await dialog.findAll('button').find(button => button.text().trim() === '取消')!.trigger('click');
    expect(vm.qualificationDialog.visible).toBe(false);
  });
});
