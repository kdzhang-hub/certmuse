import { flushPromises, mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import PaperImportDialog from './PaperImportDialog.vue';

const paperApi = vi.hoisted(() => ({
  createPaperImport: vi.fn(),
  validatePaperImport: vi.fn(),
  getPaperImportProgress: vi.fn(),
  getPaperImportQualifications: vi.fn(),
  listPaperImportIssues: vi.fn(),
  confirmPaperImport: vi.fn()
}));
const confirm = vi.hoisted(() => vi.fn());
const requestUtils = vi.hoisted(() => ({
  extractErrorMessage: vi.fn(async (error: any) => error?.response?.data?.msg || error?.message)
}));

const DialogStub = {
  name: 'ElDialog',
  props: ['modelValue', 'title'],
  emits: ['update:modelValue'],
  template:
    '<section v-show="modelValue" class="paper-dialog-stub" :data-title="title"><slot /><footer><slot name="footer" /></footer></section>'
};
const UploadStub = {
  name: 'ElUpload',
  props: ['onChange', 'onRemove', 'onExceed'],
  template: '<div><slot /><button class="paper-upload-remove" @click="onRemove?.()">移除文件</button></div>'
};

function mountInteractiveDialog() {
  return mount(PaperImportDialog, {
    attachTo: document.body,
    props: { modelValue: true },
    global: { plugins: [ElementPlus], stubs: { ElDialog: DialogStub, ElUpload: UploadStub } }
  });
}

function actionButton(wrapper: any, label: string) {
  return wrapper.findAll('button').find((button: any) => button.text().trim() === label);
}

async function fillReadyPaperForm(
  wrapper: any,
  file = new globalThis.File(['PK{}'], 'paper.zip', { type: 'application/zip' })
) {
  const name = wrapper.findComponent({ name: 'ElInput' });
  name.vm.$emit('update:modelValue', '架构模拟卷');
  const certification = wrapper
    .findAllComponents({ name: 'ElSelect' })
    .find((component: any) => component.props('placeholder') === '请选择考试资格')!;
  certification.vm.$emit('update:modelValue', '1');
  const upload = wrapper.findComponent({ name: 'ElUpload' });
  upload.vm.$emit('update:fileList', [{ name: file.name, raw: file }]);
  await (upload.props('onChange') as (entry: unknown) => Promise<void>)({ name: file.name, raw: file });
  await flushPromises();
  return upload;
}

vi.mock('@/api/certmuse/question/paper-import', () => paperApi);
vi.mock('@/utils/request', () => requestUtils);
vi.mock('element-plus', async importOriginal => {
  const elementPlus = await importOriginal<typeof import('element-plus')>();
  return { ...elementPlus, ElMessageBox: { confirm } };
});

describe('PaperImportDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    paperApi.getPaperImportQualifications.mockResolvedValue([{ id: '1', certificationName: '系统架构设计师' }]);
    paperApi.listPaperImportIssues.mockResolvedValue({ data: { rows: [], total: 0 } });
    confirm.mockReset();
    confirm.mockResolvedValue(undefined);
  });
  afterEach(() => document.body.replaceChildren());

  it('only exposes qualification, never a syllabus selector', async () => {
    const wrapper = mount(PaperImportDialog, {
      attachTo: document.body,
      props: { modelValue: true },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();
    const labels = wrapper.findAll('.el-form-item__label').map(item => item.text());
    expect(labels).toContain('考试资格');
    expect(labels).not.toContain('考纲版本');
    wrapper.unmount();
  });

  it('blocks confirmation when precheck has a warning or failed question', async () => {
    const wrapper = mount(PaperImportDialog, {
      attachTo: document.body,
      props: { modelValue: true },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();
    const state = wrapper.vm as typeof wrapper.vm & {
      batch: { id: string; status: 'waiting_confirm' };
      progress: { status: 'waiting_confirm'; validCount: number; warningCount: number; failedCount: number };
    };
    state.batch = { id: '70001', status: 'waiting_confirm' };
    state.progress = { status: 'waiting_confirm', validCount: 5, warningCount: 1, failedCount: 0 };
    await wrapper.vm.$nextTick();
    const button = wrapper.findAll('button').find(item => item.text().includes('确认导入'));
    expect(button?.attributes('disabled')).toBeDefined();
    wrapper.unmount();
  });

  it('accepts the backend batch shape and starts precheck with a new request id', async () => {
    paperApi.createPaperImport.mockResolvedValue({
      data: {
        id: '70001',
        documentId: null,
        importType: 'paper',
        mode: null,
        syllabusVersionId: '20001',
        examSubjectId: null,
        knowledgeSyllabusVersionId: '20001',
        derivedSubjects: [],
        templateVersion: 'question-zip/1.0',
        status: 'uploaded',
        reused: false,
        createTime: '2026-08-07T00:00:00+08:00'
      }
    });
    paperApi.validatePaperImport.mockResolvedValue({
      data: { id: '70001', status: 'validating', currentStage: 'read_zip', accepted: true }
    });
    paperApi.getPaperImportProgress.mockResolvedValue({
      data: {
        id: '70001',
        status: 'waiting_confirm',
        currentStage: 'resolve_syllabus',
        progressPercent: 100,
        totalCount: 3,
        validCount: 3,
        warningCount: 0,
        failedCount: 0,
        syllabusVersionId: '20001',
        syllabusVersionName: '2026 考纲',
        collectionId: null,
        revisionId: null,
        failureTraceId: null
      }
    });
    const wrapper = mount(PaperImportDialog, {
      attachTo: document.body,
      props: { modelValue: true },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();
    const state = wrapper.vm as any;
    state.form.collectionName = '模拟卷一';
    state.form.certificationId = '1';
    state.fileList = [{ name: 'paper.zip', raw: new File(['PK{}'], 'paper.zip', { type: 'application/zip' }) }];
    state.hashState = 'success';

    await state.startPrecheck();

    expect(paperApi.validatePaperImport).toHaveBeenCalledWith('70001', expect.stringMatching(/^[0-9a-f-]{36}$/i));
    expect(state.progress.status).toBe('waiting_confirm');
    wrapper.unmount();
  });

  it('validates the ZIP signature and computes a content hash', async () => {
    const wrapper = mount(PaperImportDialog, { props: { modelValue: true }, global: { plugins: [ElementPlus] } });
    await flushPromises();
    const state = wrapper.vm as any;
    await state.handleFileChange({ name: 'paper.jsonl', raw: new File(['PK{}'], 'paper.jsonl') });
    expect(state.fileError).toContain('只支持 .zip');
    await state.handleFileChange({ name: 'paper.zip', raw: new File(['NOPE'], 'paper.zip') });
    expect(state.fileError).toContain('不是有效的 ZIP');
    await state.handleFileChange({ name: 'paper.zip', raw: new File(['PK{}'], 'paper.zip') });
    expect(state.fileError).toBe('');
    expect(state.hashState).toBe('success');
    expect(state.fileHash).toMatch(/^[0-9a-f]{64}$/);
    state.handleFileExceed();
    expect(state.fileError).toBe('一次只能选择一个 ZIP 文件。');
    wrapper.unmount();
  });

  it('confirms a clean batch, loads issues, and emits completion once', async () => {
    paperApi.confirmPaperImport.mockResolvedValue({
      data: { id: '70001', status: 'importing', currentStage: 'persist_questions' }
    });
    paperApi.getPaperImportProgress.mockResolvedValue({
      data: {
        id: '70001',
        status: 'completed',
        validCount: 3,
        warningCount: 0,
        failedCount: 0,
        collectionId: 'c-1',
        revisionId: 'r-1'
      }
    });
    const wrapper = mount(PaperImportDialog, { props: { modelValue: true }, global: { plugins: [ElementPlus] } });
    await flushPromises();
    const state = wrapper.vm as any;
    state.form.collectionName = '模拟卷一';
    state.form.certificationId = '1';
    state.batch = { id: '70001', status: 'waiting_confirm' };
    state.progress = { status: 'waiting_confirm', validCount: 3, warningCount: 0, failedCount: 0 };
    await state.confirmImport();
    await flushPromises();
    expect(confirm).toHaveBeenCalledWith(
      expect.stringContaining('原子创建 3 道草稿题目'),
      '确认导入试卷',
      expect.any(Object)
    );
    expect(paperApi.confirmPaperImport).toHaveBeenCalledWith('70001', expect.stringMatching(/^[0-9a-f-]{36}$/i));
    expect(paperApi.listPaperImportIssues).toHaveBeenCalledWith(
      '70001',
      expect.objectContaining({ pageNum: 1, pageSize: 100 })
    );
    expect(wrapper.emitted('success')).toHaveLength(1);
    expect(wrapper.emitted('update:modelValue')).toContainEqual([false]);
    wrapper.unmount();
  });

  it('keeps qualification and issue failures visible for retry', async () => {
    const wrapper = mount(PaperImportDialog, { props: { modelValue: true }, global: { plugins: [ElementPlus] } });
    await flushPromises();
    const state = wrapper.vm as any;
    paperApi.getPaperImportQualifications.mockRejectedValueOnce(new Error('资格服务不可用'));
    await state.loadQualifications();
    expect(state.operationError).toBe('资格服务不可用');
    state.batch = { id: '70001', status: 'failed' };
    paperApi.listPaperImportIssues.mockRejectedValueOnce(new Error('问题服务不可用'));
    await state.loadIssues();
    expect(state.operationError).toBe('问题服务不可用');
    expect(state.isTerminal('cancelled')).toBe(true);
    expect(state.isTerminal('importing')).toBe(false);
    wrapper.unmount();
  });

  it('shows the backend reason instead of the Axios 400 status text', async () => {
    const wrapper = mount(PaperImportDialog, { props: { modelValue: true }, global: { plugins: [ElementPlus] } });
    await flushPromises();
    const state = wrapper.vm as any;
    state.form.collectionName = '模拟卷一';
    state.form.certificationId = '1';
    state.fileList = [{ name: 'paper.zip', raw: new File(['PK{}'], 'paper.zip', { type: 'application/zip' }) }];
    state.hashState = 'success';
    paperApi.createPaperImport.mockRejectedValueOnce({
      message: 'Request failed with status code 400',
      response: { data: { msg: '该考试资格没有可用考纲版本' } }
    });

    await state.startPrecheck();

    expect(state.operationError).toBe('该考试资格没有可用考纲版本');
    wrapper.unmount();
  });

  it('resets a removed file and clears the complete dialog state', async () => {
    const wrapper = mount(PaperImportDialog, { props: { modelValue: true }, global: { plugins: [ElementPlus] } });
    await flushPromises();
    const state = wrapper.vm as any;
    state.fileList = [{ name: 'paper.zip', raw: new File(['PK{}'], 'paper.zip') }];
    state.fileError = 'old error';
    state.fileHash = 'old hash';
    state.hashState = 'success';
    state.batch = { id: '70001', status: 'failed' };
    state.progress = { status: 'failed', validCount: 0, warningCount: 0, failedCount: 1 };
    state.form.collectionName = '临时试卷';
    state.form.certificationId = '1';

    state.resetFile();
    expect(state.fileList).toEqual([]);
    expect(state.fileHash).toBe('');
    expect(state.hashState).toBe('idle');
    expect(state.batch).toBeUndefined();
    expect(state.progress).toBeUndefined();

    state.form.collectionName = '再次编辑';
    state.form.certificationId = '1';
    state.reset();
    expect(state.form).toEqual({
      collectionName: '',
      collectionType: 'SIMULATION',
      durationMinutes: 240,
      certificationId: '',
      examYear: undefined,
      examMonth: undefined,
      paperTypeCode: '',
      paperTypeName: ''
    });
    expect(state.operationError).toBe('');
    wrapper.unmount();
  });

  it('searches issues only for an existing batch and polls until completion', async () => {
    vi.useFakeTimers();
    try {
      const wrapper = mount(PaperImportDialog, { props: { modelValue: true }, global: { plugins: [ElementPlus] } });
      await vi.runAllTimersAsync();
      const state = wrapper.vm as any;
      state.searchIssues();
      expect(paperApi.listPaperImportIssues).not.toHaveBeenCalled();

      state.batch = { id: '70001', status: 'parsing' };
      state.progress = { status: 'parsing', validCount: 0, warningCount: 0, failedCount: 0 };
      paperApi.listPaperImportIssues.mockResolvedValue({ data: { rows: [], total: 0 } });
      state.issueQuery.severity = 'error';
      state.issueQuery.issueCode = 'QUESTION_JSON_INVALID';
      state.searchIssues();
      await flushPromises();
      expect(paperApi.listPaperImportIssues).toHaveBeenCalledWith(
        '70001',
        expect.objectContaining({ severity: 'error', issueCode: 'QUESTION_JSON_INVALID' })
      );

      paperApi.getPaperImportProgress.mockResolvedValue({
        data: { id: '70001', status: 'completed', validCount: 1, warningCount: 0, failedCount: 0 }
      });
      state.schedulePolling();
      await vi.advanceTimersByTimeAsync(1500);
      await flushPromises();
      expect(paperApi.getPaperImportProgress).toHaveBeenCalledWith('70001');
      expect(state.currentStatus).toBe('completed');
      wrapper.unmount();
    } finally {
      vi.useRealTimers();
    }
  });

  it('uses visible form and upload controls to create a paper precheck batch', async () => {
    paperApi.createPaperImport.mockResolvedValue({ data: { id: 'public-paper-batch', status: 'uploaded' } });
    paperApi.validatePaperImport.mockResolvedValue({ data: { id: 'public-paper-batch', status: 'waiting_confirm' } });
    paperApi.getPaperImportProgress.mockResolvedValue({
      data: { id: 'public-paper-batch', status: 'waiting_confirm', validCount: 2, warningCount: 1, failedCount: 0 }
    });
    const wrapper = mount(PaperImportDialog, {
      props: { modelValue: true },
      global: { plugins: [ElementPlus], stubs: { ElDialog: DialogStub, ElUpload: UploadStub } }
    });
    await flushPromises();

    const textInput = wrapper.findComponent({ name: 'ElInput' });
    textInput.vm.$emit('update:modelValue', '架构模拟卷');
    const certification = wrapper
      .findAllComponents({ name: 'ElSelect' })
      .find(component => component.props('placeholder') === '请选择考试资格')!;
    certification.vm.$emit('update:modelValue', '1');
    await flushPromises();
    const upload = wrapper.findComponent({ name: 'ElUpload' });
    const validFile = new globalThis.File(['PK{}'], 'paper.zip');
    upload.vm.$emit('update:fileList', [{ name: 'paper.zip', raw: validFile }]);
    await (upload.props('onChange') as (file: unknown) => unknown)({ name: 'paper.zip', raw: validFile });
    await flushPromises();
    await wrapper.vm.$nextTick();
    const start = wrapper.findAll('button').find(button => button.text().trim() === '开始预检');
    expect(start?.attributes('disabled')).toBeUndefined();
    expect(wrapper.findComponent({ name: 'ElInput' }).props('modelValue')).toBe('架构模拟卷');
    expect(certification.props('modelValue')).toBe('1');
    expect(upload.props('onChange') as unknown).toBeTypeOf('function');
    await start!.trigger('click');
    await flushPromises();

    expect(paperApi.createPaperImport).toHaveBeenCalledWith(
      expect.objectContaining({ collectionName: '架构模拟卷', certificationId: '1' })
    );
    expect(paperApi.validatePaperImport).toHaveBeenCalledWith('public-paper-batch', expect.any(String));
    expect(wrapper.text()).toContain('存在 warning 或 error，不能确认导入。');
    wrapper.unmount();
  });

  it('shows public upload validation feedback and keeps precheck unavailable', async () => {
    const wrapper = mount(PaperImportDialog, {
      props: { modelValue: true },
      global: { plugins: [ElementPlus], stubs: { ElDialog: DialogStub, ElUpload: UploadStub } }
    });
    await flushPromises();

    const upload = wrapper.findComponent({ name: 'ElUpload' });
    const invalidFile = new globalThis.File(['PK{}'], 'paper.txt');
    upload.vm.$emit('update:fileList', [{ name: 'paper.txt', raw: invalidFile }]);
    await (upload.props('onChange') as (file: unknown) => unknown)({ name: 'paper.txt', raw: invalidFile });
    await flushPromises();
    expect(wrapper.text()).toContain('试卷导入只支持 .zip 文件。');
    expect(
      wrapper
        .findAll('button')
        .find(button => button.text().trim() === '开始预检')
        ?.attributes('disabled')
    ).toBeDefined();
    await wrapper.get('.paper-upload-remove').trigger('click');
    await flushPromises();
    expect(wrapper.text()).not.toContain('SHA-256：');
    wrapper.unmount();
  });

  it('keeps empty, oversized, and hash-failed ZIP feedback visible and blocks precheck', async () => {
    const wrapper = mountInteractiveDialog();
    await flushPromises();
    const name = wrapper.findComponent({ name: 'ElInput' });
    name.vm.$emit('update:modelValue', '导入边界试卷');
    const certification = wrapper
      .findAllComponents({ name: 'ElSelect' })
      .find((component: any) => component.props('placeholder') === '请选择考试资格')!;
    certification.vm.$emit('update:modelValue', '1');
    const upload = wrapper.findComponent({ name: 'ElUpload' });
    const choose = async (file: File) => {
      upload.vm.$emit('update:fileList', [{ name: file.name, raw: file }]);
      await (upload.props('onChange') as (entry: unknown) => Promise<void>)({ name: file.name, raw: file });
      await flushPromises();
    };

    await choose(new globalThis.File([], 'empty.zip', { type: 'application/zip' }));
    expect(wrapper.text()).toContain('文件不能为空。');
    expect(actionButton(wrapper, '开始预检')?.attributes('disabled')).toBeDefined();

    const oversized = new globalThis.File(['PK{}'], 'oversized.zip', { type: 'application/zip' });
    Object.defineProperty(oversized, 'size', { value: 64 * 1024 * 1024 + 1 });
    await choose(oversized);
    expect(wrapper.text()).toContain('ZIP 包超过 64 MiB 上限，请重新选择。');
    expect(actionButton(wrapper, '开始预检')?.attributes('disabled')).toBeDefined();

    const digest = vi.spyOn(globalThis.crypto.subtle, 'digest').mockRejectedValueOnce(new Error('digest unavailable'));
    try {
      await choose(new globalThis.File(['PK{}'], 'hash-failure.zip', { type: 'application/zip' }));
      expect(wrapper.text()).toContain('前端 SHA-256 计算失败，请重新选择文件。');
      expect(actionButton(wrapper, '开始预检')?.attributes('disabled')).toBeDefined();
    } finally {
      digest.mockRestore();
    }
    wrapper.unmount();
  });

  it('shows an actionable error when creating a batch returns no data', async () => {
    paperApi.createPaperImport.mockResolvedValueOnce({});
    const wrapper = mountInteractiveDialog();
    await flushPromises();
    await fillReadyPaperForm(wrapper);

    const start = actionButton(wrapper, '开始预检')!;
    expect(start.attributes('disabled')).toBeUndefined();
    await start.trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('创建试卷导入批次响应缺少 data。');
    expect(wrapper.text()).toContain('尚未创建批次');
    expect(actionButton(wrapper, '开始预检')?.attributes('disabled')).toBeUndefined();
    wrapper.unmount();
  });

  it('shows an actionable error when the precheck acceptance response returns no data', async () => {
    paperApi.createPaperImport.mockResolvedValueOnce({ data: { id: 'accepted-missing', status: 'uploaded' } });
    paperApi.validatePaperImport.mockResolvedValueOnce({});
    const wrapper = mountInteractiveDialog();
    await flushPromises();
    await fillReadyPaperForm(wrapper);

    await actionButton(wrapper, '开始预检')!.trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('触发预检响应缺少 data。');
    expect(wrapper.text()).toContain('文件已上传');
    expect(actionButton(wrapper, '确认导入')?.attributes('disabled')).toBeDefined();
    wrapper.unmount();
  });

  it('shows an actionable error when the progress response returns no data', async () => {
    paperApi.createPaperImport.mockResolvedValueOnce({ data: { id: 'progress-missing', status: 'uploaded' } });
    paperApi.validatePaperImport.mockResolvedValueOnce({ data: { id: 'progress-missing', status: 'validating' } });
    paperApi.getPaperImportProgress.mockResolvedValueOnce({});
    const wrapper = mountInteractiveDialog();
    await flushPromises();
    await fillReadyPaperForm(wrapper);

    await actionButton(wrapper, '开始预检')!.trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('导入进度响应缺少 data。');
    expect(wrapper.text()).toContain('文件已上传');
    expect(actionButton(wrapper, '确认导入')?.attributes('disabled')).toBeDefined();
    wrapper.unmount();
  });

  it('leaves a clean precheck ready for the operator when confirmation is cancelled', async () => {
    paperApi.createPaperImport.mockResolvedValueOnce({ data: { id: 'cancel-confirm', status: 'uploaded' } });
    paperApi.validatePaperImport.mockResolvedValueOnce({ data: { id: 'cancel-confirm', status: 'waiting_confirm' } });
    paperApi.getPaperImportProgress.mockResolvedValueOnce({
      data: { id: 'cancel-confirm', status: 'waiting_confirm', validCount: 2, warningCount: 0, failedCount: 0 }
    });
    confirm.mockRejectedValueOnce(new Error('operator cancelled'));
    const wrapper = mountInteractiveDialog();
    await flushPromises();
    await fillReadyPaperForm(wrapper);

    await actionButton(wrapper, '开始预检')!.trigger('click');
    await flushPromises();
    const confirmButton = actionButton(wrapper, '确认导入')!;
    expect(wrapper.text()).toContain('全部题目通过预检，可以确认导入。');
    expect(confirmButton.attributes('disabled')).toBeUndefined();

    await confirmButton.trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('全部题目通过预检，可以确认导入。');
    expect(actionButton(wrapper, '确认导入')?.attributes('disabled')).toBeUndefined();
    expect(wrapper.emitted('success')).toBeUndefined();
    expect(wrapper.emitted('update:modelValue')).toBeUndefined();
    expect(paperApi.confirmPaperImport).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('emits completed import success only once when a terminal progress payload is delivered again', async () => {
    vi.useFakeTimers();
    try {
      paperApi.createPaperImport.mockResolvedValueOnce({ data: { id: 'complete-once', status: 'uploaded' } });
      paperApi.validatePaperImport.mockResolvedValueOnce({ data: { id: 'complete-once', status: 'waiting_confirm' } });
      paperApi.confirmPaperImport.mockResolvedValueOnce({ data: { id: 'complete-once', status: 'importing' } });
      paperApi.getPaperImportProgress
        .mockResolvedValueOnce({
          data: { id: 'complete-once', status: 'waiting_confirm', validCount: 2, warningCount: 0, failedCount: 0 }
        })
        .mockResolvedValueOnce({
          data: { id: 'complete-once', status: 'importing', validCount: 2, warningCount: 0, failedCount: 0 }
        })
        .mockResolvedValue({
          data: {
            id: 'complete-once',
            status: 'completed',
            validCount: 2,
            warningCount: 0,
            failedCount: 0,
            collectionId: 'collection-1',
            revisionId: 'revision-1'
          }
        });
      const wrapper = mountInteractiveDialog();
      await flushPromises();
      await fillReadyPaperForm(wrapper);

      await actionButton(wrapper, '开始预检')!.trigger('click');
      await flushPromises();
      await actionButton(wrapper, '确认导入')!.trigger('click');
      await flushPromises();
      await vi.advanceTimersByTimeAsync(1500);
      await flushPromises();

      expect(wrapper.text()).toContain('导入完成');
      expect(wrapper.text()).toContain('题目与题集草稿已创建。');
      expect(wrapper.emitted('success')).toHaveLength(1);
      expect(wrapper.emitted('update:modelValue')).toEqual([[false]]);

      // Simulate a delayed duplicate progress delivery after the completed UI state is already visible.
      await (wrapper.vm as any).refreshProgress();
      await flushPromises();
      expect(wrapper.emitted('success')).toHaveLength(1);
      expect(wrapper.emitted('update:modelValue')).toEqual([[false]]);
      wrapper.unmount();
    } finally {
      vi.useRealTimers();
    }
  });

  it.each([
    ['failed', '处理失败'],
    ['cancelled', '已取消']
  ] as const)(
    'shows the terminal %s state and stops polling without reporting success',
    async (status, visibleLabel) => {
      vi.useFakeTimers();
      try {
        paperApi.createPaperImport.mockResolvedValueOnce({ data: { id: `${status}-batch`, status: 'uploaded' } });
        paperApi.validatePaperImport.mockResolvedValueOnce({ data: { id: `${status}-batch`, status: 'validating' } });
        paperApi.getPaperImportProgress
          .mockResolvedValueOnce({
            data: { id: `${status}-batch`, status: 'parsing', validCount: 0, warningCount: 0, failedCount: 0 }
          })
          .mockResolvedValueOnce({
            data: { id: `${status}-batch`, status, validCount: 0, warningCount: 0, failedCount: 1 }
          });
        const wrapper = mountInteractiveDialog();
        await flushPromises();
        await fillReadyPaperForm(wrapper);

        await actionButton(wrapper, '开始预检')!.trigger('click');
        await flushPromises();
        expect(wrapper.text()).toContain('正在解析');
        await vi.advanceTimersByTimeAsync(1500);
        await flushPromises();

        expect(wrapper.text()).toContain(visibleLabel);
        expect(actionButton(wrapper, '确认导入')?.attributes('disabled')).toBeDefined();
        expect(wrapper.emitted('success')).toBeUndefined();
        await vi.advanceTimersByTimeAsync(4500);
        expect(paperApi.getPaperImportProgress).toHaveBeenCalledTimes(2);
        wrapper.unmount();
      } finally {
        vi.useRealTimers();
      }
    }
  );

  it('continues polling through non-terminal states until the clean precheck becomes confirmable', async () => {
    vi.useFakeTimers();
    try {
      paperApi.createPaperImport.mockResolvedValueOnce({ data: { id: 'polling-batch', status: 'uploaded' } });
      paperApi.validatePaperImport.mockResolvedValueOnce({ data: { id: 'polling-batch', status: 'validating' } });
      paperApi.getPaperImportProgress
        .mockResolvedValueOnce({
          data: { id: 'polling-batch', status: 'parsing', validCount: 0, warningCount: 0, failedCount: 0 }
        })
        .mockResolvedValueOnce({
          data: { id: 'polling-batch', status: 'validating', validCount: 1, warningCount: 0, failedCount: 0 }
        })
        .mockResolvedValueOnce({
          data: { id: 'polling-batch', status: 'waiting_confirm', validCount: 1, warningCount: 0, failedCount: 0 }
        });
      const wrapper = mountInteractiveDialog();
      await flushPromises();
      await fillReadyPaperForm(wrapper);

      await actionButton(wrapper, '开始预检')!.trigger('click');
      await flushPromises();
      expect(wrapper.text()).toContain('正在解析');
      expect(actionButton(wrapper, '确认导入')?.attributes('disabled')).toBeDefined();

      await vi.advanceTimersByTimeAsync(1500);
      await flushPromises();
      expect(wrapper.text()).toContain('正在预检');
      expect(actionButton(wrapper, '确认导入')?.attributes('disabled')).toBeDefined();

      await vi.advanceTimersByTimeAsync(1500);
      await flushPromises();
      expect(wrapper.text()).toContain('预检完成');
      expect(wrapper.text()).toContain('全部题目通过预检，可以确认导入。');
      expect(actionButton(wrapper, '确认导入')?.attributes('disabled')).toBeUndefined();
      wrapper.unmount();
    } finally {
      vi.useRealTimers();
    }
  });
});
