import ElementPlus from 'element-plus';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { emitImportProgress } from '@/utils/import-progress-events';
import ImportDialog from './ImportDialog.vue';

const api = vi.hoisted(() => ({
  confirmImport: vi.fn(),
  createKnowledgePointImport: vi.fn(),
  createQuestionImport: vi.fn(),
  createTextbookImport: vi.fn(),
  getImportContextOptions: vi.fn(),
  getQuestionImportContextOptions: vi.fn(),
  getTextbookImportContextOptions: vi.fn(),
  getTextbookImportPreview: vi.fn(),
  getImportProgress: vi.fn(),
  getKnowledgeDiff: vi.fn(),
  importMockEnabled: false,
  listImportIssues: vi.fn(),
  resolveKnowledgeDiff: vi.fn(),
  resolveKnowledgeDiffBatch: vi.fn(),
  validateImport: vi.fn()
}));
const requestErrors = vi.hoisted(() => ({ getHandledRequestError: vi.fn() }));
const messages = vi.hoisted(() => ({ confirm: vi.fn() }));
const qualifications = vi.hoisted(() => ({ listQualifications: vi.fn() }));


vi.mock('@/api/certmuse/catalog/import', () => api);
vi.mock('@/api/certmuse/catalog/knowledge', () => ({ getKnowledgeTree: vi.fn() }));
vi.mock('@/api/certmuse/catalog/subject-version', () => qualifications);
vi.mock('@/plugins/auth', () => ({ default: { hasPermi: vi.fn(() => true) } }));
vi.mock('@/utils/request', () => requestErrors);
vi.mock('element-plus', async importOriginal => {
  const elementPlus = await importOriginal<typeof import('element-plus')>();
  return { ...elementPlus, ElMessageBox: { confirm: messages.confirm } };
});

describe('ImportDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    requestErrors.getHandledRequestError.mockReset();
    messages.confirm.mockReset();
    messages.confirm.mockResolvedValue(undefined);
    qualifications.listQualifications.mockResolvedValue({ data: { rows: [] } });
    api.getImportContextOptions.mockResolvedValue({
      syllabusVersions: [{ id: '20001', label: '系统架构设计师 · 2026', knowledgeTreeAvailable: true, knowledgePointCount: 10 }],
      examSubjects: [{ id: '101', subjectNo: 1, label: '综合知识' }]
    });
    api.getQuestionImportContextOptions.mockResolvedValue({
      knowledgeSyllabusVersions: [{ id: '20001', label: '系统架构设计师 · 2026', knowledgeTreeAvailable: true, knowledgePointCount: 10 }]
    });
    api.getTextbookImportContextOptions.mockResolvedValue({
      syllabusVersions: [{ id: '20001', label: '系统架构设计师 · 2026', knowledgeTreeAvailable: true, knowledgePointCount: 10 }],
      examSubjects: [{ id: '101', subjectNo: 1, label: '综合知识' }],
      defaultSubjectMappings: [{ subjectNo: 1, examSubjectId: '101' }],
      replaceableDrafts: [{ id: '30001', title: '教材草稿', edition: null, syllabusVersionId: '20001' }]
    });
    api.getKnowledgeDiff.mockResolvedValue({
      data: {
        rows: [], total: 0, unchangedCount: 2, updateCount: 1, moveCount: 0, addCount: 1, deleteCount: 0,
        pendingCount: 2, affectedQuestionCount: 3, offlineQuestionCount: 1
      }
    });
  });

  afterEach(() => document.body.replaceChildren());

  it.each([
    ['knowledge_point', 'getImportContextOptions'],
    ['question', 'getQuestionImportContextOptions'],
    ['textbook', 'getTextbookImportContextOptions']
  ] as const)('loads only the %s context', async (importType, method) => {
    const wrapper = mount(ImportDialog, {
      attachTo: document.body,
      props: { modelValue: true, importType, initialSyllabusVersionId: '20001' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();

    expect(api[method]).toHaveBeenCalled();
    expect(document.body.textContent).not.toContain('选择导入类型');
    wrapper.unmount();
  });

  it('prefills a selected replaceable textbook draft', async () => {
    const wrapper = mount(ImportDialog, {
      attachTo: document.body,
      props: {
        modelValue: true,
        importType: 'textbook',
        initialSyllabusVersionId: '20001',
        initialTextbookId: '30001'
      },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();

    expect(document.body.textContent).toContain('替换草稿教材');
    expect(document.body.textContent).toContain('教材草稿');
    wrapper.unmount();
  });

  it.each(['knowledge_point', 'textbook'] as const)('%s import only exposes qualification selection', async importType => {
    const wrapper = mount(ImportDialog, {
      attachTo: document.body,
      props: { modelValue: true, importType },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();

    expect(document.body.textContent).toContain('资格名称');
    expect(document.body.textContent).not.toContain('资格版本');
    wrapper.unmount();
  });

  it('prefills the qualification from the selected syllabus version', async () => {
    const wrapper = mount(ImportDialog, {
      attachTo: document.body,
      props: { modelValue: true, importType: 'knowledge_point', initialSyllabusVersionId: '20001' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();

    const state = wrapper.vm as typeof wrapper.vm & {
      form: { syllabusCertification: string; syllabusVersionId: string };
    };
    expect(state.form.syllabusCertification).toBe('系统架构设计师');
    expect(state.form.syllabusVersionId).toBe('20001');
    wrapper.unmount();
  });

  it('only exposes qualification name for question import', async () => {
    const wrapper = mount(ImportDialog, {
      attachTo: document.body,
      props: { modelValue: true, importType: 'question' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();

    expect(document.body.textContent).toContain('资格名称');
    expect(document.body.textContent).not.toContain('资格版本');
    wrapper.unmount();
  });

  it('keeps the default question syllabus selected when no initial syllabus version is provided', async () => {
    api.getQuestionImportContextOptions.mockResolvedValue({
      knowledgeSyllabusVersions: [
        { id: '20001', label: '系统架构设计师 · 2026', knowledgeTreeAvailable: true, knowledgePointCount: 10 },
        { id: '20002', label: '12 · 2026', knowledgeTreeAvailable: true, knowledgePointCount: 1 }
      ]
    });
    const wrapper = mount(ImportDialog, {
      props: { modelValue: true, importType: 'question' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();

    const state = wrapper.vm as typeof wrapper.vm & {
      form: { questionCertification: string; knowledgeSyllabusVersionId: string };
      questionContextReady: boolean;
    };
    expect(state.form.questionCertification).toBe('系统架构设计师');
    expect(state.form.knowledgeSyllabusVersionId).toBe('20001');
    expect(state.questionContextReady).toBe(true);
    wrapper.unmount();
  });

  it('allows question confirmation when valid and failed records coexist', async () => {
    const wrapper = mount(ImportDialog, {
      attachTo: document.body,
      props: { modelValue: true, importType: 'question' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();

    const state = wrapper.vm as typeof wrapper.vm & {
      batch: { id: string; status: 'waiting_confirm' };
      form: { questionCertification: string; knowledgeSyllabusVersionId: string };
      progress: {
        status: 'waiting_confirm';
        validCount: number;
        failedCount: number;
      };
    };
    state.batch = { id: '90001', status: 'waiting_confirm' };
    state.form.questionCertification = '系统架构设计师';
    state.form.knowledgeSyllabusVersionId = '20001';
    state.progress = { status: 'waiting_confirm', validCount: 6, failedCount: 589 };
    await nextTick();

    const confirmButton = wrapper.findAll('button').find(button => button.text().includes('确认导入'));
    expect(confirmButton?.attributes('disabled')).toBeUndefined();
    expect(document.body.textContent).toContain('可确认导入有效题目；错误或警告题目不会导入。');
    expect(document.body.textContent).toContain('含 warning 的题目不会导入。');
    wrapper.unmount();
  });

  it('enters knowledge diff confirmation after a clean precheck and blocks final import while pending', async () => {
    const wrapper = mount(ImportDialog, {
      attachTo: document.body,
      props: { modelValue: true, importType: 'knowledge_point', initialSyllabusVersionId: '20001' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();
    const state = wrapper.vm as typeof wrapper.vm & {
      batch: { id: string; status: 'waiting_confirm'; importType: 'knowledge_point' };
      progress: { status: 'waiting_confirm'; validCount: number; failedCount: number; knowledgeDiffRequired: boolean };
    };
    state.batch = { id: 'batch-diff', status: 'waiting_confirm', importType: 'knowledge_point' };
    state.progress = { status: 'waiting_confirm', validCount: 4, failedCount: 0, knowledgeDiffRequired: true };
    await flushPromises();

    expect(api.getKnowledgeDiff).toHaveBeenCalledWith('batch-diff', expect.objectContaining({ pageNum: 1, pageSize: 20, excludeUnchanged: true }));
    expect(document.body.textContent).toContain('知识树差异确认');
    expect(document.body.textContent).toContain('未确认 2');
    const confirmButton = [...document.body.querySelectorAll('button')].find(button =>
      button.textContent?.includes('正式确认导入')
    );
    expect(confirmButton).toBeInstanceOf(HTMLButtonElement);
    expect((confirmButton as HTMLButtonElement).disabled).toBe(true);
    wrapper.unmount();
  });

  it('validates JSONL and ZIP files before calculating their hashes', async () => {
    const jsonl = mount(ImportDialog, {
      props: { modelValue: true, importType: 'knowledge_point' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();
    const jsonState = jsonl.vm as any;
    expect(await jsonState.validateFile({ name: 'empty.jsonl', raw: new File([], 'empty.jsonl') })).toBe('文件不能为空。');
    expect(await jsonState.validateFile({ name: 'tree.txt', raw: new File(['{}'], 'tree.txt') })).toContain('只支持 .jsonl');
    expect(await jsonState.validateFile({ name: 'tree.jsonl', raw: new File([new Uint8Array([0xef, 0xbb, 0xbf])], 'tree.jsonl') })).toContain('UTF-8 BOM');
    expect(await jsonState.validateFile({ name: 'tree.jsonl', raw: new File(['{}'], 'tree.jsonl') })).toBe('');
    expect(jsonState.formatFileSize(512)).toBe('512 B');
    expect(jsonState.formatFileSize(2048)).toBe('2.0 KiB');
    expect(jsonState.formatFileSize(2 * 1024 * 1024)).toBe('2.00 MiB');
    jsonl.unmount();

    const question = mount(ImportDialog, {
      props: { modelValue: true, importType: 'question' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();
    const questionState = question.vm as any;
    expect(await questionState.validateFile({ name: 'questions.jsonl', raw: new File(['PK{}'], 'questions.jsonl') })).toContain('只支持 .zip');
    expect(await questionState.validateFile({ name: 'questions.zip', raw: new File(['NOPE'], 'questions.zip') })).toContain('不是有效的 ZIP');
    expect(await questionState.validateFile({ name: 'questions.zip', raw: new File(['PK{}'], 'questions.zip') })).toBe('');
    question.unmount();
  });

  it('validates subject mappings and creates then confirms a question batch', async () => {
    const wrapper = mount(ImportDialog, {
      props: { modelValue: true, importType: 'question' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();
    const state = wrapper.vm as any;
    expect(state.validateSubjectMappings([])).toContain('1～3');
    expect(state.validateSubjectMappings([{ subjectNo: 4, examSubjectId: '101' }])).toContain('subjectNo');
    expect(state.validateSubjectMappings([{ subjectNo: 1, examSubjectId: '0' }])).toContain('正整数');
    expect(state.validateSubjectMappings([{ subjectNo: 1, examSubjectId: '101' }, { subjectNo: 1, examSubjectId: '102' }])).toContain('不得重复');
    expect(state.validateSubjectMappings([{ subjectNo: 1, examSubjectId: '101' }, { subjectNo: 2, examSubjectId: '102' }])).toBe('');

    state.form.questionCertification = '系统架构设计师';
    state.form.knowledgeSyllabusVersionId = '20001';
    state.fileList = [{ name: 'questions.zip', raw: new File(['PK{}'], 'questions.zip') }];
    state.hashState = 'success';
    api.createQuestionImport.mockResolvedValue({ data: { id: 'batch-1', status: 'uploaded', importType: 'question' } });
    api.validateImport.mockResolvedValue({ data: { id: 'batch-1', status: 'waiting_confirm', currentStage: 'validate_schema' } });
    api.getImportProgress.mockResolvedValue({ data: { id: 'batch-1', status: 'waiting_confirm', validCount: 2, failedCount: 1, warningCount: 0, progressPercent: 100, knowledgeDiffRequired: false } });
    api.listImportIssues.mockResolvedValue({ data: { rows: [], total: 0 } });
    await state.startPrecheck();
    expect(messages.confirm).toHaveBeenCalledWith(
      expect.stringContaining('将上传“questions.zip”并开始预检'),
      '确认上传并开始预检',
      expect.objectContaining({ confirmButtonText: '确认上传', cancelButtonText: '取消' })
    );
    expect(api.createQuestionImport).toHaveBeenCalledWith(expect.objectContaining({ knowledgeSyllabusVersionId: '20001', requestId: expect.any(String) }));
    expect(api.validateImport).toHaveBeenCalledWith('batch-1');
    expect(state.currentStatus).toBe('waiting_confirm');
    expect(state.canConfirmImport).toBe(true);

    api.confirmImport.mockResolvedValue({ data: { status: 'completed', currentStage: 'persist_questions' } });
    await state.confirmImport();
    expect(api.confirmImport).toHaveBeenCalledWith('batch-1', expect.any(String));
    expect(wrapper.emitted('success')).toHaveLength(1);
    expect(wrapper.emitted('update:modelValue')).toContainEqual([false]);
    wrapper.unmount();
  });

  it('keeps operation errors actionable and resets file/runtime state', async () => {
    const wrapper = mount(ImportDialog, {
      props: { modelValue: true, importType: 'textbook' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();
    const state = wrapper.vm as any;
    requestErrors.getHandledRequestError.mockReturnValue({ responseData: { errorCode: 'IMPORT_BATCH_STATE_INVALID', retryable: true, traceId: 'trace-1', fieldErrors: [{ field: 'file', message: '无效' }] } });
    state.handleOperationError(new Error('批次状态无效'));
    expect(state.requestRetryable).toBe(true);
    expect(state.fieldErrors).toEqual([{ field: 'file', message: '无效' }]);
    expect(state.operationError).toContain('trace-1');
    state.handleFileExceed();
    expect(state.fileError).toContain('一次只能选择一个');
    state.fileList = [{ name: 'book.jsonl', raw: new File(['{}'], 'book.jsonl') }];
    state.fileHash = 'hash';
    state.hashState = 'success';
    state.resetFileState();
    expect(state.fileList).toEqual([]);
    expect(state.hashState).toBe('idle');
    expect(state.formatDateTime(null)).toBe('—');
    expect(state.formatDateTime('not-a-date')).toBe('not-a-date');
    expect(state.pageRange(null, null)).toBe('—');
    expect(state.pageRange(3, 3)).toBe('3');
    expect(state.pageRange(3, 5)).toBe('3-5');
    wrapper.unmount();
  });

  it('resets runtime state when the selected file changes or is removed', async () => {
    const wrapper = mount(ImportDialog, {
      props: { modelValue: true, importType: 'knowledge_point' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();
    const state = wrapper.vm as any;
    state.batch = { id: 'old-batch', status: 'parsing', importType: 'knowledge_point' };
    state.progress = { status: 'parsing', validCount: 0, failedCount: 0 };
    state.fileList = [{ name: 'old.jsonl', raw: new File(['{}'], 'old.jsonl') }];
    state.fileHash = 'old-hash';
    state.hashState = 'success';

    await state.handleFileChange({ name: 'wrong.txt', raw: new File(['{}'], 'wrong.txt') });
    expect(state.fileError).toContain('只支持 .jsonl');
    expect(state.batch).toBeNull();
    expect(state.hashState).toBe('idle');
    expect(state.serverFileConfirmed).toBe(false);

    state.fileList = [{ name: 'tree.jsonl', raw: new File(['{}'], 'tree.jsonl') }];
    await state.handleFileChange({ name: 'tree.jsonl', raw: new File(['{}'], 'tree.jsonl') });
    expect(state.fileError).toBe('');
    expect(state.hashState).toBe('success');
    expect(state.fileHash).toMatch(/^[0-9a-f]{64}$/);

    state.handleFileRemove();
    expect(state.fileList).toEqual([]);
    expect(state.fileHash).toBe('');
    expect(state.batch).toBeNull();
    wrapper.unmount();
  });

  it('updates question and textbook contexts through their change handlers', async () => {
    const question = mount(ImportDialog, {
      props: { modelValue: true, importType: 'question' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();
    const questionState = question.vm as any;
    questionState.form.questionCertification = '系统架构设计师';
    questionState.handleQuestionCertificationChange();
    expect(questionState.form.knowledgeSyllabusVersionId).toBe('20001');
    question.unmount();

    const textbook = mount(ImportDialog, {
      props: { modelValue: true, importType: 'textbook' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();
    const textbookState = textbook.vm as any;
    textbookState.handleTextbookDraftChange('30001');
    expect(textbookState.form.textbookTitle).toBe('教材草稿');
    textbookState.handleTextbookModeChange();
    expect(textbookState.form.textbookDocumentId).toBe('');
    textbookState.form.syllabusCertification = '系统架构设计师';
    await textbookState.handleSyllabusCertificationChange();
    expect(textbookState.form.syllabusVersionId).toBe('20001');
    expect(api.getTextbookImportContextOptions).toHaveBeenCalledWith('20001');
    textbookState.handleClosed();
    expect(textbook.emitted('closed')).toHaveLength(1);
    expect(textbookState.form.syllabusVersionId).toBe('');
    expect(textbookState.batch).toBeNull();
    textbook.unmount();
  });

  it('refreshes after a knowledge-diff state change and handles preview and issue pagination', async () => {
    const wrapper = mount(ImportDialog, {
      props: { modelValue: true, importType: 'textbook' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();
    const state = wrapper.vm as any;
    state.batch = { id: 'book-batch', status: 'waiting_confirm', importType: 'document_chunk' };
    api.getImportProgress.mockResolvedValue({ data: { id: 'book-batch', status: 'waiting_confirm', validCount: 1, failedCount: 0, knowledgeDiffRequired: false } });
    await state.handleKnowledgeDiffStateInvalid();
    expect(state.knowledgeDiffLocked).toBe(true);
    expect(api.getImportProgress).toHaveBeenCalledWith('book-batch');

    api.getTextbookImportPreview.mockResolvedValue({ data: { document: {}, summary: {}, chunks: { rows: [], total: 0 } } });
    state.previewQuery.pageNum = 4;
    state.handlePreviewPageSizeChange();
    await flushPromises();
    expect(state.previewQuery.pageNum).toBe(1);
    expect(api.getTextbookImportPreview).toHaveBeenCalledWith('book-batch', expect.objectContaining({ pageNum: 1 }));

    api.listImportIssues.mockResolvedValue({ data: { rows: [], total: 0 } });
    state.issueQuery.pageNum = 3;
    state.searchIssues();
    await flushPromises();
    expect(api.listImportIssues).toHaveBeenCalledWith('book-batch', expect.objectContaining({ pageNum: 1 }));
    state.issueQuery.pageNum = 2;
    state.handleIssuePageSizeChange();
    await flushPromises();
    expect(state.issueQuery.pageNum).toBe(1);
    wrapper.unmount();
  });

  it('polls a non-terminal batch and stops after the terminal progress snapshot', async () => {
    vi.useFakeTimers();
    try {
      const wrapper = mount(ImportDialog, {
        props: { modelValue: true, importType: 'question' },
        global: { plugins: [ElementPlus] }
      });
      await vi.runAllTimersAsync();
      const state = wrapper.vm as any;
      state.batch = { id: 'poll-batch', status: 'parsing', importType: 'question' };
      state.progress = { status: 'parsing', validCount: 0, failedCount: 0 };
      api.getImportProgress.mockResolvedValue({ data: { id: 'poll-batch', status: 'completed', validCount: 1, failedCount: 0 } });
      api.listImportIssues.mockResolvedValue({ data: { rows: [], total: 0 } });
      state.schedulePolling();
      await vi.advanceTimersByTimeAsync(10_000);
      await flushPromises();
      expect(api.getImportProgress).toHaveBeenCalledWith('poll-batch');
      expect(state.currentStatus).toBe('completed');
      wrapper.unmount();
    } finally {
      vi.useRealTimers();
    }
  });

  it('offers a new qualification as a knowledge-point import target and reloads its server context', async () => {
    qualifications.listQualifications.mockResolvedValue({
      data: {
        rows: [
          { id: 'cert-new', certificationName: '新资格', versions: [] },
          { id: 'cert-existing', certificationName: '已有考纲资格', versions: [] },
          { id: 'cert-versioned', certificationName: '已有版本资格', versions: [{}] }
        ]
      }
    });
    api.getImportContextOptions
      .mockResolvedValueOnce({
        syllabusVersions: [{ id: 'old-syllabus', certificationId: 'cert-existing', label: '已有考纲资格 · 2026' }],
        certifications: [],
        examSubjects: []
      })
      .mockResolvedValueOnce({
        syllabusVersions: [],
        certifications: [{ id: 'cert-new', label: '新资格' }],
        examSubjects: [{ id: '201', subjectNo: 1, label: '基础知识' }]
      });
    const wrapper = mount(ImportDialog, {
      attachTo: document.body,
      props: { modelValue: true, importType: 'knowledge_point' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();

    const state = wrapper.vm as any;
    expect(state.syllabusCreationOptions).toEqual([{ id: 'cert-new', label: '新资格' }]);
    state.form.syllabusCertification = '新资格';
    await state.handleSyllabusCertificationChange();
    await flushPromises();

    expect(api.getImportContextOptions).toHaveBeenLastCalledWith(undefined, 'cert-new');
    expect(state.form.certificationId).toBe('cert-new');
    expect(document.body.textContent).toContain('请选择考纲 JSONL 文件。');
    wrapper.unmount();
  });
  it('creates a knowledge-point batch, shows the server-selected syllabus, and confirms its clean precheck', async () => {
    api.getImportContextOptions.mockResolvedValue({
      syllabusVersions: [{ id: '20001', certificationId: 'cert-1', label: '系统架构设计师 · 2026', knowledgeTreeAvailable: true, knowledgePointCount: 10 }],
      examSubjects: [{ id: '101', subjectNo: 1, label: '综合知识' }]
    });
    api.createKnowledgePointImport.mockResolvedValue({
      data: {
        id: 'knowledge-batch', status: 'uploaded', importType: 'knowledge_point', syllabusVersionId: 'server-syllabus',
        templateVersion: 'knowledge_point/1.0', createTime: null, reused: false
      }
    });
    api.validateImport.mockResolvedValue({
      data: { id: 'knowledge-batch', status: 'waiting_confirm', currentStage: 'finalize_counts' }
    });
    api.getImportProgress.mockResolvedValue({
      data: {
        id: 'knowledge-batch', status: 'waiting_confirm', progressPercent: 100, totalCount: 2,
        validCount: 2, warningCount: 0, failedCount: 0, knowledgeDiffRequired: false,
        startedTime: null, finishedTime: null, failureTraceId: null
      }
    });
    api.listImportIssues.mockResolvedValue({ data: { rows: [], total: 0 } });
    api.confirmImport.mockResolvedValue({ data: { id: 'knowledge-batch', status: 'completed', currentStage: 'persist_knowledge_tree' } });
    const wrapper = mount(ImportDialog, {
      attachTo: document.body,
      props: { modelValue: true, importType: 'knowledge_point', initialSyllabusVersionId: '20001' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();

    const upload = wrapper.findComponent({ name: 'ElUpload' });
    const jsonl = new globalThis.File(['{"code":"1.1"}'], 'knowledge.jsonl', { type: 'application/json' });
    upload.vm.$emit('update:fileList', [{ name: 'knowledge.jsonl', raw: jsonl }]);
    await (upload.props('onChange') as (file: unknown) => Promise<void>)({ name: 'knowledge.jsonl', raw: jsonl });
    await flushPromises();

    const start = [...document.body.querySelectorAll('button')].find(button => button.textContent?.trim() === '开始预检');
    expect(start).toBeInstanceOf(HTMLButtonElement);
    expect((start as HTMLButtonElement).disabled).toBe(false);
    await (start as HTMLButtonElement).click();
    await flushPromises();

    expect(api.createKnowledgePointImport).toHaveBeenCalledWith(
      expect.objectContaining({ file: jsonl, certificationId: 'cert-1', syllabusVersionId: '20001', subjectMappings: [{ subjectNo: 1, examSubjectId: '101' }] })
    );
    expect((wrapper.vm as any).form.syllabusVersionId).toBe('server-syllabus');
    expect(document.body.textContent).toContain('预检完成，可直接确认导入新考纲。');

    const confirmButton = [...document.body.querySelectorAll('button')].find(
      button => button.textContent?.trim() === '确认导入'
    );
    expect(confirmButton).toBeInstanceOf(HTMLButtonElement);
    await (confirmButton as HTMLButtonElement).click();
    await flushPromises();
    expect(messages.confirm).toHaveBeenCalledWith(expect.stringContaining('导入 2 条有效知识点记录'), '确认导入考纲', expect.any(Object));
    expect(api.confirmImport).toHaveBeenCalledWith('knowledge-batch', expect.any(String));
    expect(wrapper.emitted('success')).toHaveLength(1);
    wrapper.unmount();
  });

  it('sorts textbook and knowledge-point subject mappings before sending the real precheck request', async () => {
    api.createTextbookImport.mockResolvedValue({
      data: { id: 'sorted-textbook', status: 'uploaded', importType: 'document_chunk', createTime: null, reused: false }
    });
    api.createKnowledgePointImport.mockResolvedValue({
      data: { id: 'sorted-knowledge', status: 'uploaded', importType: 'knowledge_point', createTime: null, reused: false }
    });
    api.validateImport.mockResolvedValue({ data: { id: 'sorted', status: 'waiting_confirm', currentStage: 'finalize_counts' } });
    api.getImportProgress.mockResolvedValue({ data: { id: 'sorted', status: 'waiting_confirm', validCount: 1, warningCount: 0, failedCount: 0, knowledgeDiffRequired: false } });
    api.listImportIssues.mockResolvedValue({ data: { rows: [], total: 0 } });

    const textbook = mount(ImportDialog, {
      props: { modelValue: true, importType: 'textbook', initialSyllabusVersionId: '20001' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();
    const textbookState = textbook.vm as any;
    textbookState.form.textbookTitle = '排序教材';
    textbookState.fileList = [{ name: 'sorted.jsonl', raw: new globalThis.File(['{}'], 'sorted.jsonl') }];
    textbookState.hashState = 'success';
    textbookState.subjectMappings = [{ subjectNo: 2, examSubjectId: '102' }, { subjectNo: 1, examSubjectId: '101' }];
    await textbookState.startPrecheck();
    expect(api.createTextbookImport).toHaveBeenCalledWith(expect.objectContaining({
      subjectMappings: [{ subjectNo: 1, examSubjectId: '101' }, { subjectNo: 2, examSubjectId: '102' }]
    }));
    textbook.unmount();

    const knowledge = mount(ImportDialog, {
      props: { modelValue: true, importType: 'knowledge_point', initialSyllabusVersionId: '20001' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();
    const knowledgeState = knowledge.vm as any;
    knowledgeState.form.certificationId = 'cert-1';
    knowledgeState.fileList = [{ name: 'sorted.jsonl', raw: new globalThis.File(['{}'], 'sorted.jsonl') }];
    knowledgeState.hashState = 'success';
    knowledgeState.subjectMappings = [{ subjectNo: 3, examSubjectId: '103' }, { subjectNo: 1, examSubjectId: '101' }];
    await knowledgeState.startPrecheck();
    expect(api.createKnowledgePointImport).toHaveBeenCalledWith(expect.objectContaining({
      subjectMappings: [{ subjectNo: 1, examSubjectId: '101' }, { subjectNo: 3, examSubjectId: '103' }]
    }));
    knowledge.unmount();
  });

  it('reports missing payloads and generic errors as actionable import failures', async () => {
    api.createQuestionImport.mockResolvedValueOnce({});
    const wrapper = mount(ImportDialog, {
      attachTo: document.body,
      props: { modelValue: true, importType: 'question' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();
    const state = wrapper.vm as any;
    state.fileList = [{ name: 'missing.zip', raw: new globalThis.File(['PK{}'], 'missing.zip') }];
    state.hashState = 'success';
    await state.startPrecheck();
    await flushPromises();
    expect(document.body.textContent).toContain('创建批次响应缺少 data。');
    wrapper.unmount();
  });
  it('renders the question archive’s server-derived subjects and an active parsing stage', async () => {
    const wrapper = mount(ImportDialog, {
      attachTo: document.body,
      props: { modelValue: true, importType: 'question' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();

    const state = wrapper.vm as any;
    state.batch = {
      id: 'subject-batch', status: 'parsing', importType: 'question',
      derivedSubjects: [{ subjectNo: 1, label: '综合知识' }, { subjectNo: 2, label: '案例分析' }]
    };
    state.progress = {
      id: 'subject-batch', status: 'parsing', currentStage: 'read_jsonl', progressPercent: 0,
      totalCount: 0, validCount: 0, warningCount: 0, failedCount: 0, knowledgeDiffRequired: false,
      startedTime: null, finishedTime: null, failureTraceId: null
    };
    await nextTick();

    expect(document.body.textContent).toContain('综合知识（科目1）、案例分析（科目2）');
    expect(document.body.textContent).toContain('服务端阶段：读取 JSONL。进度只表示任务执行程度，不代表数据正确率。');
    expect(document.body.textContent).toContain('正在解析');
    wrapper.unmount();
  });

  it('shows completed textbook and knowledge-point import outcomes to the operator', async () => {
    const textbook = mount(ImportDialog, {
      attachTo: document.body,
      props: { modelValue: true, importType: 'textbook', initialSyllabusVersionId: '20001' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();
    const textbookState = textbook.vm as any;
    textbookState.batch = { id: 'completed-textbook', status: 'completed', importType: 'document_chunk', documentId: 'draft-book' };
    textbookState.progress = { status: 'completed', validCount: 1, warningCount: 0, failedCount: 0, knowledgeDiffRequired: false };
    await nextTick();
    expect(document.body.textContent).toContain('教材内容块已导入为草稿。');
    expect(document.body.textContent).toContain('教材 IDdraft-book');
    textbook.unmount();

    const knowledge = mount(ImportDialog, {
      attachTo: document.body,
      props: { modelValue: true, importType: 'knowledge_point', initialSyllabusVersionId: '20001' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();
    const knowledgeState = knowledge.vm as any;
    knowledgeState.batch = { id: 'completed-knowledge', status: 'completed', importType: 'knowledge_point' };
    knowledgeState.progress = { status: 'completed', validCount: 1, warningCount: 0, failedCount: 0, knowledgeDiffRequired: false };
    knowledgeState.form.certificationId = 'cert-1';
    await nextTick();
    expect(document.body.textContent).toContain('考纲已正式导入。');
    expect(document.body.textContent).toContain('知识点已正式导入，可前往考纲管理页面查看。');
    knowledge.unmount();
  });
  it('stops confirmation for an incomplete knowledge-diff review and allows it after all differences are confirmed', async () => {
    const wrapper = mount(ImportDialog, {
      attachTo: document.body,
      props: { modelValue: true, importType: 'knowledge_point', initialSyllabusVersionId: '20001' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();

    const state = wrapper.vm as any;
    state.form.certificationId = 'cert-1';
    state.fileList = [{ name: 'diff.jsonl', raw: new globalThis.File(['{}'], 'diff.jsonl') }];
    state.hashState = 'success';
    state.batch = { id: 'knowledge-diff-batch', status: 'waiting_confirm', importType: 'knowledge_point' };
    state.progress = { status: 'waiting_confirm', validCount: 2, warningCount: 0, failedCount: 0, knowledgeDiffRequired: true };
    state.knowledgeDiffSummary = { pendingCount: 1, addCount: 1, updateCount: 0, moveCount: 0, deleteCount: 0, affectedQuestionCount: 0, offlineQuestionCount: 0 };
    await nextTick();

    expect(document.body.textContent).toContain('还有 2 条知识点差异待人工确认。');
    const diffConfirm = [...document.body.querySelectorAll('button')].find(button => button.textContent?.trim() === '正式确认导入');
    expect(diffConfirm).toBeInstanceOf(HTMLButtonElement);
    expect((diffConfirm as HTMLButtonElement).disabled).toBe(true);

    state.knowledgeDiffSummary = { ...state.knowledgeDiffSummary, pendingCount: 0 };
    await nextTick();
    expect(document.body.textContent).toContain('所有知识点差异已确认，可以正式导入。');
    expect((diffConfirm as HTMLButtonElement).disabled).toBe(false);
    wrapper.unmount();
  });

  it('confirms both create and replace-draft textbook import choices through the visible dialog', async () => {
    const create = mount(ImportDialog, {
      attachTo: document.body,
      props: { modelValue: true, importType: 'textbook', initialSyllabusVersionId: '20001' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();
    const createState = create.vm as any;
    createState.form.textbookTitle = '新教材';
    createState.fileList = [{ name: 'new.jsonl', raw: new globalThis.File(['{}'], 'new.jsonl') }];
    createState.hashState = 'success';
    createState.batch = { id: 'create-book', status: 'waiting_confirm', importType: 'document_chunk' };
    createState.progress = { status: 'waiting_confirm', validCount: 2, warningCount: 0, failedCount: 0, knowledgeDiffRequired: false };
    await nextTick();
    const createConfirm = [...document.body.querySelectorAll('button')].find(button => button.textContent?.trim() === '确认导入');
    expect(createConfirm).toBeInstanceOf(HTMLButtonElement);
    await (createConfirm as HTMLButtonElement).click();
    await flushPromises();
    expect(messages.confirm).toHaveBeenCalledWith(expect.stringContaining('创建草稿教材并写入 2 个有效内容块'), '确认导入教材', expect.any(Object));
    create.unmount();

    const replace = mount(ImportDialog, {
      attachTo: document.body,
      props: { modelValue: true, importType: 'textbook', initialSyllabusVersionId: '20001', initialTextbookId: '30001' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();
    const replaceState = replace.vm as any;
    replaceState.fileList = [{ name: 'replace.jsonl', raw: new globalThis.File(['{}'], 'replace.jsonl') }];
    replaceState.hashState = 'success';
    replaceState.batch = { id: 'replace-book', status: 'waiting_confirm', importType: 'document_chunk' };
    replaceState.progress = { status: 'waiting_confirm', validCount: 2, warningCount: 0, failedCount: 0, knowledgeDiffRequired: false };
    await nextTick();
    const replaceConfirm = [...document.body.querySelectorAll('button')].find(button => button.textContent?.trim() === '确认导入');
    expect(replaceConfirm).toBeInstanceOf(HTMLButtonElement);
    await (replaceConfirm as HTMLButtonElement).click();
    await flushPromises();
    expect(messages.confirm).toHaveBeenLastCalledWith(expect.stringContaining('整体替换草稿教材中的 2 个有效内容块'), '确认导入教材', expect.any(Object));
    replace.unmount();
  });

  it('renders the pending file and mapping guidance before an import begins', async () => {
    const question = mount(ImportDialog, {
      attachTo: document.body,
      props: { modelValue: true, importType: 'question' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();
    const questionState = question.vm as any;
    questionState.form.knowledgeSyllabusVersionId = '';
    await nextTick();
    expect(document.body.textContent).toContain('所选资格暂无可用考纲。');
    question.unmount();

    const knowledge = mount(ImportDialog, {
      attachTo: document.body,
      props: { modelValue: true, importType: 'knowledge_point', initialSyllabusVersionId: '20001' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();
    const knowledgeState = knowledge.vm as any;
    knowledgeState.form.certificationId = 'cert-1';
    knowledgeState.subjectMappings = [];
    await nextTick();
    expect(document.body.textContent).toContain('请完成 subject_no 1～3 到考试科目的显式映射。');
    knowledge.unmount();
  });
  it('surfaces a retryable server validation error through the visible retry action', async () => {
    api.createQuestionImport.mockRejectedValueOnce({
      response: {
        data: {
          data: {
            errorCode: 'IMPORT_RETRYABLE',
            retryable: true,
            traceId: 'trace-retry',
            fieldErrors: [{ field: 'file', code: 'INVALID_FORMAT', message: 'ZIP 内容无效' }]
          }
        }
      }
    });
    const wrapper = mount(ImportDialog, {
      attachTo: document.body,
      props: { modelValue: true, importType: 'question' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();

    const upload = wrapper.findComponent({ name: 'ElUpload' });
    const zip = new globalThis.File(['PK{}'], 'retry.zip', { type: 'application/zip' });
    upload.vm.$emit('update:fileList', [{ name: 'retry.zip', raw: zip }]);
    await (upload.props('onChange') as (file: unknown) => Promise<void>)({ name: 'retry.zip', raw: zip });
    await flushPromises();

    const start = [...document.body.querySelectorAll('button')].find(button => button.textContent?.trim() === '开始预检');
    expect(start).toBeInstanceOf(HTMLButtonElement);
    await (start as HTMLButtonElement).click();
    await flushPromises();

    expect(document.body.textContent).toContain('系统处理失败，请联系管理员并提供 Trace ID：trace-retry');
    expect(document.body.textContent).toContain('file：ZIP 内容无效（INVALID_FORMAT）');
    const retry = [...document.body.querySelectorAll('button')].find(button => button.textContent?.trim() === '安全重试');
    expect(retry).toBeInstanceOf(HTMLButtonElement);
    wrapper.unmount();
  });

  it('disables confirmation when a textbook precheck contains failed records', async () => {
    const wrapper = mount(ImportDialog, {
      attachTo: document.body,
      props: { modelValue: true, importType: 'textbook', initialSyllabusVersionId: '20001' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();

    const state = wrapper.vm as any;
    state.form.textbookTitle = '待校验教材';
    state.fileList = [{ name: 'pending.jsonl', raw: new globalThis.File(['{}'], 'pending.jsonl') }];
    state.hashState = 'success';
    state.batch = { id: 'textbook-errors', status: 'waiting_confirm', importType: 'document_chunk' };
    state.progress = { status: 'waiting_confirm', validCount: 1, warningCount: 0, failedCount: 1, knowledgeDiffRequired: false };
    await nextTick();

    expect(document.body.textContent).toContain('存在错误记录，修正文件后重新导入。');
    const confirmButton = [...document.body.querySelectorAll('button')].find(
      button => button.textContent?.trim() === '确认导入'
    );
    expect(confirmButton).toBeInstanceOf(HTMLButtonElement);
    expect((confirmButton as HTMLButtonElement).disabled).toBe(true);
    wrapper.unmount();
  });
  it('keeps the dialog visible while a confirmed import continues and polls its next progress snapshot', async () => {
    vi.useFakeTimers();
    try {
      api.confirmImport.mockResolvedValue({ data: { id: 'importing-batch', status: 'importing', currentStage: 'persist_questions' } });
      api.getImportProgress.mockResolvedValue({
        data: {
          id: 'importing-batch', status: 'importing', progressPercent: 55, totalCount: 3,
          validCount: 3, warningCount: 0, failedCount: 0, knowledgeDiffRequired: false,
          startedTime: null, finishedTime: null, failureTraceId: null
        }
      });
      const wrapper = mount(ImportDialog, {
        attachTo: document.body,
        props: { modelValue: true, importType: 'question' },
        global: { plugins: [ElementPlus] }
      });
      await vi.runAllTimersAsync();
      const state = wrapper.vm as any;
      state.batch = { id: 'importing-batch', status: 'waiting_confirm', importType: 'question' };
      state.progress = { status: 'waiting_confirm', validCount: 3, warningCount: 0, failedCount: 0, knowledgeDiffRequired: false };
      await nextTick();

      const confirmButton = [...document.body.querySelectorAll('button')].find(
        button => button.textContent?.trim() === '确认导入'
      );
      expect(confirmButton).toBeInstanceOf(HTMLButtonElement);
      await (confirmButton as HTMLButtonElement).click();
      await flushPromises();
      expect(document.body.textContent).toContain('正在导入');
      expect(wrapper.emitted('success')).toBeUndefined();

      await vi.advanceTimersByTimeAsync(10_000);
      await flushPromises();
      expect(api.getImportProgress).toHaveBeenCalledWith('importing-batch');
      wrapper.unmount();
    } finally {
      vi.useRealTimers();
    }
  });
  it('cancels the visible question confirmation without importing', async () => {
    messages.confirm.mockRejectedValueOnce(new Error('cancelled'));
    const wrapper = mount(ImportDialog, {
      attachTo: document.body,
      props: { modelValue: true, importType: 'question' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();

    const state = wrapper.vm as any;
    state.batch = { id: 'cancel-question-batch', status: 'waiting_confirm', importType: 'question' };
    state.progress = { status: 'waiting_confirm', validCount: 3, failedCount: 0, knowledgeDiffRequired: false };
    await nextTick();

    const confirmButton = [...document.body.querySelectorAll('button')].find(
      button => button.textContent?.trim() === '确认导入'
    );
    expect(confirmButton).toBeInstanceOf(HTMLButtonElement);
    await (confirmButton as HTMLButtonElement).click();
    await flushPromises();

    expect(messages.confirm).toHaveBeenCalledWith(
      expect.stringContaining('创建 3 道草稿题目'),
      '确认导入草稿题目',
      expect.any(Object)
    );
    expect(api.confirmImport).not.toHaveBeenCalled();
    expect(wrapper.emitted('success')).toBeUndefined();
    wrapper.unmount();
  });

  it('shows the server failure trace and keeps the import action disabled after a failed precheck', async () => {
    api.createQuestionImport.mockResolvedValue({
      data: { id: 'failed-question-batch', status: 'uploaded', importType: 'question', createTime: null, reused: false }
    });
    api.validateImport.mockResolvedValue({
      data: { id: 'failed-question-batch', status: 'failed', currentStage: 'validate_schema' }
    });
    api.getImportProgress.mockResolvedValue({
      data: {
        id: 'failed-question-batch', status: 'failed', progressPercent: 37, totalCount: 2,
        validCount: 0, warningCount: 0, failedCount: 2, knowledgeDiffRequired: false,
        startedTime: null, finishedTime: null, failureTraceId: 'trace-precheck-failed'
      }
    });
    api.listImportIssues.mockResolvedValue({ data: { rows: [], total: 0 } });
    const wrapper = mount(ImportDialog, {
      attachTo: document.body,
      props: { modelValue: true, importType: 'question' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();

    const upload = wrapper.findComponent({ name: 'ElUpload' });
    const zip = new globalThis.File(['PK{}'], 'failed.zip', { type: 'application/zip' });
    upload.vm.$emit('update:fileList', [{ name: 'failed.zip', raw: zip }]);
    await (upload.props('onChange') as (file: unknown) => Promise<void>)({ name: 'failed.zip', raw: zip });
    await flushPromises();

    const start = [...document.body.querySelectorAll('button')].find(button => button.textContent?.trim() === '开始预检');
    expect(start).toBeInstanceOf(HTMLButtonElement);
    await (start as HTMLButtonElement).click();
    await flushPromises();

    expect(document.body.textContent).toContain('系统处理失败，请联系管理员并提供 Trace ID：trace-precheck-failed');
    expect(document.body.textContent).toContain('批次已停止，进度保留最后一次服务端快照。');
    const confirmButton = [...document.body.querySelectorAll('button')].find(
      button => button.textContent?.trim() === '确认导入'
    );
    expect(confirmButton).toBeInstanceOf(HTMLButtonElement);
    expect((confirmButton as HTMLButtonElement).disabled).toBe(true);
    wrapper.unmount();
  });

  it('opens a textbook preview with issue pagination after a successful precheck', async () => {
    api.createTextbookImport.mockResolvedValue({
      data: { id: 'textbook-batch', status: 'uploaded', importType: 'document_chunk', createTime: null, reused: false }
    });
    api.validateImport.mockResolvedValue({
      data: { id: 'textbook-batch', status: 'waiting_confirm', currentStage: 'finalize_counts' }
    });
    api.getImportProgress.mockResolvedValue({
      data: {
        id: 'textbook-batch', status: 'waiting_confirm', progressPercent: 100, totalCount: 1,
        validCount: 1, warningCount: 1, failedCount: 0, knowledgeDiffRequired: false,
        startedTime: null, finishedTime: null, failureTraceId: null
      }
    });
    api.getTextbookImportPreview.mockResolvedValue({
      data: {
        document: { id: 'draft-1', title: '架构教材', edition: null, syllabusVersionName: '系统架构设计师 · 2026' },
        summary: { totalChunks: 1, validChunks: 1, mappedChunks: 1, unmappedChunks: 0, knowledgeRelationCount: 1, imageCount: 0, errorCount: 0, warningCount: 1 },
        directory: [],
        chunks: {
          rows: [{ lineNo: 1, sourceKey: 'chapter-1', chunkOrder: 1, heading: '第一章', headingPath: ['第一章'], contentPreview: '内容摘要', pageStart: 3, pageEnd: 5, knowledgePoints: [{ id: 'kp-1', subjectNo: 1, code: '1.1', title: '架构基础' }], issueCount: 1 }],
          total: 21
        }
      }
    });
    api.listImportIssues.mockResolvedValue({
      data: {
        rows: [{ lineNo: 1, sourceKey: 'chapter-1', fieldPath: 'content', severity: 'warning', issueCode: 'TEXTBOOK_WARN', message: '可继续导入', createTime: '2026-08-13T00:00:00Z' }],
        total: 21
      }
    });
    const wrapper = mount(ImportDialog, {
      attachTo: document.body,
      props: { modelValue: true, importType: 'textbook', initialSyllabusVersionId: '20001' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();

    const state = wrapper.vm as any;
    state.form.textbookTitle = '架构教材';
    const upload = wrapper.findComponent({ name: 'ElUpload' });
    const jsonl = new globalThis.File(['{"chunk":"one"}'], 'textbook.jsonl', { type: 'application/json' });
    upload.vm.$emit('update:fileList', [{ name: 'textbook.jsonl', raw: jsonl }]);
    await (upload.props('onChange') as (file: unknown) => Promise<void>)({ name: 'textbook.jsonl', raw: jsonl });
    await flushPromises();

    const start = [...document.body.querySelectorAll('button')].find(button => button.textContent?.trim() === '开始预检');
    expect(start).toBeInstanceOf(HTMLButtonElement);
    expect((start as HTMLButtonElement).disabled).toBe(false);
    await (start as HTMLButtonElement).click();
    await flushPromises();

    expect(api.createTextbookImport).toHaveBeenCalledWith(
      expect.objectContaining({ file: jsonl, mode: 'create', title: '架构教材', syllabusVersionId: '20001' })
    );
    expect(document.body.textContent).toContain('教材导入预览');
    expect(document.body.textContent).toContain('架构教材 · 系统架构设计师 · 2026');
    expect(document.body.textContent).toContain('第一章');
    expect(document.body.textContent).toContain('3-5');
    expect(document.body.textContent).toContain('1.1 架构基础');
    expect(document.body.textContent).toContain('TEXTBOOK_WARN');

    const paginations = wrapper.findAllComponents({ name: 'ElPagination' });
    expect(paginations).toHaveLength(2);
    const previewPagination = paginations[0]!;
    const issuePagination = paginations[1]!;
    (state.previewQuery as { pageSize: number }).pageSize = 50;
    previewPagination.vm.$emit('size-change', 50);
    (state.issueQuery as { pageNum: number }).pageNum = 2;
    issuePagination.vm.$emit('current-change', 2);
    await flushPromises();
    expect(api.getTextbookImportPreview).toHaveBeenLastCalledWith('textbook-batch', expect.objectContaining({ pageNum: 1, pageSize: 50 }));
    expect(api.listImportIssues).toHaveBeenLastCalledWith('textbook-batch', expect.objectContaining({ pageNum: 2 }));
    wrapper.unmount();
  });

  it('emits closed when the dialog surface finishes closing', async () => {
    const wrapper = mount(ImportDialog, {
      props: { modelValue: true, importType: 'question' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();

    const dialog = wrapper.findComponent({ name: 'ElDialog' });
    expect(dialog.exists()).toBe(true);
    dialog.vm.$emit('closed');
    await nextTick();

    expect(wrapper.emitted('closed')).toHaveLength(1);
    wrapper.unmount();
  });
  it('creates and confirms a question import through the upload component contract and visible actions', async () => {
    api.createQuestionImport.mockResolvedValue({
      data: { id: 'public-question-batch', status: 'uploaded', importType: 'question', createTime: null, reused: false }
    });
    api.validateImport.mockResolvedValue({
      data: { id: 'public-question-batch', status: 'waiting_confirm', currentStage: 'validate_schema' }
    });
    api.getImportProgress.mockResolvedValue({
      data: {
        id: 'public-question-batch', status: 'waiting_confirm', progressPercent: 100, totalCount: 2,
        validCount: 2, warningCount: 0, failedCount: 0, knowledgeDiffRequired: false,
        startedTime: null, finishedTime: null, failureTraceId: null
      }
    });
    api.listImportIssues.mockResolvedValue({ data: { rows: [], total: 0 } });
    api.confirmImport.mockResolvedValue({ data: { status: 'completed', currentStage: 'persist_questions' } });
    const wrapper = mount(ImportDialog, {
      attachTo: document.body,
      props: { modelValue: true, importType: 'question' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();

    const upload = wrapper.findComponent({ name: 'ElUpload' });
    expect(upload.exists()).toBe(true);
    const zip = new globalThis.File(['PK{}'], 'questions.zip', { type: 'application/zip' });
    upload.vm.$emit('update:fileList', [{ name: 'questions.zip', raw: zip }]);
    await (upload.props('onChange') as (file: unknown) => Promise<void>)({ name: 'questions.zip', raw: zip });
    await flushPromises();

    const start = [...document.body.querySelectorAll('button')].find(button => button.textContent?.trim() === '开始预检');
    expect(start).toBeInstanceOf(HTMLButtonElement);
    expect((start as HTMLButtonElement).disabled).toBe(false);
    await (start as HTMLButtonElement).click();
    await flushPromises();
    expect(api.createQuestionImport).toHaveBeenCalledWith(
      expect.objectContaining({ file: zip, knowledgeSyllabusVersionId: '20001', requestId: expect.any(String) })
    );
    expect(api.validateImport).toHaveBeenCalledWith('public-question-batch');
    expect(document.body.textContent).toContain('预检完成');

    const confirmButton = [...document.body.querySelectorAll('button')].find(
      button => button.textContent?.trim() === '确认导入'
    );
    expect(confirmButton).toBeInstanceOf(HTMLButtonElement);
    expect((confirmButton as HTMLButtonElement).disabled).toBe(false);
    await (confirmButton as HTMLButtonElement).click();
    await flushPromises();
    expect(messages.confirm).toHaveBeenCalledWith(
      expect.stringContaining('创建 2 道草稿题目'),
      '确认导入草稿题目',
      expect.any(Object)
    );
    expect(api.confirmImport).toHaveBeenCalledWith('public-question-batch', expect.any(String));
    expect(wrapper.emitted('success')).toHaveLength(1);
    wrapper.unmount();
  });

  it.each([
    ['knowledge_point', 'getImportContextOptions', '考纲上下文暂不可用'],
    ['question', 'getQuestionImportContextOptions', '题目上下文暂不可用'],
    ['textbook', 'getTextbookImportContextOptions', '教材上下文暂不可用']
  ] as const)('keeps the %s dialog actionable when its context request fails', async (importType, method, message) => {
    api[method].mockRejectedValueOnce(new Error(message));
    const wrapper = mount(ImportDialog, {
      attachTo: document.body,
      props: { modelValue: true, importType },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();

    expect(document.body.textContent).toContain(message);
    expect(document.body.textContent).toContain('开始预检');
    wrapper.unmount();
  });

  it('explains why a question import cannot start when the server has no available knowledge tree', async () => {
    api.getQuestionImportContextOptions.mockResolvedValueOnce({ knowledgeSyllabusVersions: undefined });
    const wrapper = mount(ImportDialog, {
      attachTo: document.body,
      props: { modelValue: true, importType: 'question' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();

    expect(document.body.textContent).toContain('请先选择资格名称。');
    const start = [...document.body.querySelectorAll('button')].find(button => button.textContent?.trim() === '开始预检');
    expect(start).toBeInstanceOf(HTMLButtonElement);
    expect((start as HTMLButtonElement).disabled).toBe(true);
    wrapper.unmount();
  });

  it('reports invalid textbook selection and browser hash failures through the upload control', async () => {
    const wrapper = mount(ImportDialog, {
      attachTo: document.body,
      props: { modelValue: true, importType: 'textbook', initialSyllabusVersionId: '20001' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();

    const upload = wrapper.findComponent({ name: 'ElUpload' });
    const textFile = new globalThis.File(['not-jsonl'], 'chapter.txt', { type: '' });
    upload.vm.$emit('update:fileList', [{ name: textFile.name, raw: textFile }]);
    await (upload.props('onChange') as (file: unknown) => Promise<void>)({ name: textFile.name, raw: textFile });
    await flushPromises();
    expect(document.body.textContent).toContain('教材导入只支持 .jsonl 文件。');
    expect(document.body.textContent).toContain('未声明');

    const digest = vi.spyOn(globalThis.crypto.subtle, 'digest').mockRejectedValueOnce(new Error('digest unavailable'));
    try {
      const jsonl = new globalThis.File(['{"chapter":"one"}'], 'chapter.jsonl', { type: 'application/json' });
      upload.vm.$emit('update:fileList', [{ name: jsonl.name, raw: jsonl }]);
      await (upload.props('onChange') as (file: unknown) => Promise<void>)({ name: jsonl.name, raw: jsonl });
      await flushPromises();

      expect(document.body.textContent).toContain('前端 SHA-256 计算失败，请重新选择文件。');
      expect(document.body.textContent).toContain('计算失败');
    } finally {
      digest.mockRestore();
    }

    await (upload.props('onExceed') as () => void)();
    await nextTick();
    expect(document.body.textContent).toContain('一次只能选择一个教材 JSONL 文件。');
    wrapper.unmount();
  });

  it('renders an error issue and an unmapped textbook chunk after a public textbook precheck', async () => {
    api.createTextbookImport.mockResolvedValue({
      data: { id: 'unmapped-book-batch', status: 'uploaded', importType: 'document_chunk', createTime: null, reused: false }
    });
    api.validateImport.mockResolvedValue({
      data: { id: 'unmapped-book-batch', status: 'waiting_confirm', currentStage: 'finalize_counts' }
    });
    api.getImportProgress.mockResolvedValue({
      data: {
        id: 'unmapped-book-batch', status: 'waiting_confirm', progressPercent: 100, totalCount: 1,
        validCount: 1, warningCount: 0, failedCount: 0, knowledgeDiffRequired: false,
        startedTime: null, finishedTime: null, failureTraceId: null
      }
    });
    api.getTextbookImportPreview.mockResolvedValue({
      data: {
        document: { id: 'draft-unmapped', title: '无关联教材', edition: null, syllabusVersionName: '系统架构设计师 · 2026' },
        summary: { totalChunks: 1, validChunks: 1, mappedChunks: 0, unmappedChunks: 1, knowledgeRelationCount: 0, imageCount: 0, errorCount: 1, warningCount: 0 },
        directory: [],
        chunks: {
          rows: [{ lineNo: 1, sourceKey: 'chapter-empty', chunkOrder: 1, heading: '空关联章节', headingPath: [], contentPreview: '无关联内容', pageStart: null, pageEnd: null, knowledgePoints: [], issueCount: 1 }],
          total: 1
        }
      }
    });
    api.listImportIssues.mockResolvedValue({
      data: {
        rows: [{ lineNo: 1, sourceKey: 'chapter-empty', fieldPath: 'knowledgePoints', severity: 'error', issueCode: 'KNOWLEDGE_UNMAPPED', message: '未关联知识点', createTime: null }],
        total: 1
      }
    });
    const wrapper = mount(ImportDialog, {
      attachTo: document.body,
      props: { modelValue: true, importType: 'textbook', initialSyllabusVersionId: '20001' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();

    const title = wrapper.findAllComponents({ name: 'ElInput' }).find(input => input.props('placeholder') === '请输入教材名称');
    expect(title).toBeDefined();
    title!.vm.$emit('update:modelValue', '无关联教材');
    await nextTick();
    const upload = wrapper.findComponent({ name: 'ElUpload' });
    const jsonl = new globalThis.File(['{"chapter":"one"}'], 'unmapped.jsonl', { type: 'application/json' });
    upload.vm.$emit('update:fileList', [{ name: jsonl.name, raw: jsonl }]);
    await (upload.props('onChange') as (file: unknown) => Promise<void>)({ name: jsonl.name, raw: jsonl });
    await flushPromises();

    const start = [...document.body.querySelectorAll('button')].find(button => button.textContent?.trim() === '开始预检');
    expect(start).toBeInstanceOf(HTMLButtonElement);
    await (start as HTMLButtonElement).click();
    await flushPromises();

    expect(api.createTextbookImport).toHaveBeenCalledWith(expect.objectContaining({ mode: 'create', title: '无关联教材' }));
    expect(document.body.textContent).toContain('未关联');
    expect(document.body.textContent).toContain('KNOWLEDGE_UNMAPPED');
    expect(document.body.textContent).toContain('错误');
    wrapper.unmount();
  });

  it('retries a nonterminal server validation without creating a second question batch', async () => {
    api.createQuestionImport.mockResolvedValue({
      data: {
        id: 'retry-existing-batch', status: 'uploaded', importType: 'question', createTime: 'not-a-date',
        reused: true, derivedSubjects: []
      }
    });
    api.validateImport
      .mockRejectedValueOnce({
        response: { data: { data: { errorCode: 'IMPORT_VALIDATION_RETRYABLE', retryable: true } } }
      })
      .mockResolvedValueOnce({
        data: { id: 'retry-existing-batch', status: 'waiting_confirm', currentStage: 'finalize_counts' }
      });
    api.getImportProgress.mockResolvedValue({
      data: {
        id: 'retry-existing-batch', status: 'waiting_confirm', progressPercent: 100, totalCount: 1,
        validCount: 1, warningCount: 0, failedCount: 0, knowledgeDiffRequired: false,
        startedTime: null, finishedTime: null, failureTraceId: null
      }
    });
    api.listImportIssues.mockResolvedValue({ data: { rows: [], total: 0 } });
    const wrapper = mount(ImportDialog, {
      attachTo: document.body,
      props: { modelValue: true, importType: 'question' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();

    const upload = wrapper.findComponent({ name: 'ElUpload' });
    const zip = new globalThis.File(['PK{}'], 'retry-existing.zip', { type: 'application/zip' });
    upload.vm.$emit('update:fileList', [{ name: zip.name, raw: zip }]);
    await (upload.props('onChange') as (file: unknown) => Promise<void>)({ name: zip.name, raw: zip });
    await flushPromises();

    const start = [...document.body.querySelectorAll('button')].find(button => button.textContent?.trim() === '开始预检');
    expect(start).toBeInstanceOf(HTMLButtonElement);
    await (start as HTMLButtonElement).click();
    await flushPromises();
    expect(document.body.textContent).toContain('导入请求失败（IMPORT_VALIDATION_RETRYABLE）');
    expect(document.body.textContent).toContain('是，返回原批次');
    expect(document.body.textContent).toContain('等待服务端识别');
    expect(document.body.textContent).toContain('等待预检');

    const retry = [...document.body.querySelectorAll('button')].find(button => button.textContent?.trim() === '安全重试');
    expect(retry).toBeInstanceOf(HTMLButtonElement);
    await (retry as HTMLButtonElement).click();
    await flushPromises();

    expect(api.createQuestionImport).toHaveBeenCalledTimes(1);
    expect(api.validateImport).toHaveBeenCalledTimes(2);
    expect(document.body.textContent).toContain('预检完成，请确认导入草稿题目。');
    wrapper.unmount();
  });

  it('uses the selected replaceable draft when the operator prechecks and confirms a textbook replacement', async () => {
    api.createTextbookImport.mockResolvedValue({
      data: { id: 'replace-public-batch', status: 'uploaded', importType: 'document_chunk', createTime: null, reused: false }
    });
    api.validateImport.mockResolvedValue({
      data: { id: 'replace-public-batch', status: 'waiting_confirm', currentStage: 'finalize_counts' }
    });
    api.getImportProgress.mockResolvedValue({
      data: {
        id: 'replace-public-batch', status: 'waiting_confirm', progressPercent: 100, totalCount: 2,
        validCount: 2, warningCount: 0, failedCount: 0, knowledgeDiffRequired: false,
        startedTime: null, finishedTime: null, failureTraceId: null
      }
    });
    api.getTextbookImportPreview.mockResolvedValue({
      data: {
        document: { id: '30001', title: '教材草稿', edition: null, syllabusVersionName: '系统架构设计师 · 2026' },
        summary: { totalChunks: 2, validChunks: 2, mappedChunks: 2, unmappedChunks: 0, knowledgeRelationCount: 2, imageCount: 0, errorCount: 0, warningCount: 0 },
        directory: [], chunks: { rows: [], total: 0 }
      }
    });
    api.listImportIssues.mockResolvedValue({ data: { rows: [], total: 0 } });
    api.confirmImport.mockResolvedValue({ data: { id: 'replace-public-batch', status: 'completed', currentStage: 'persist_document_chunks' } });
    const wrapper = mount(ImportDialog, {
      attachTo: document.body,
      props: {
        modelValue: true,
        importType: 'textbook',
        initialSyllabusVersionId: '20001',
        initialTextbookId: '30001'
      },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();

    const upload = wrapper.findComponent({ name: 'ElUpload' });
    const jsonl = new globalThis.File(['{"chapter":"replacement"}'], 'replacement.jsonl', { type: 'application/json' });
    upload.vm.$emit('update:fileList', [{ name: jsonl.name, raw: jsonl }]);
    await (upload.props('onChange') as (file: unknown) => Promise<void>)({ name: jsonl.name, raw: jsonl });
    await flushPromises();
    const start = [...document.body.querySelectorAll('button')].find(button => button.textContent?.trim() === '开始预检');
    expect(start).toBeInstanceOf(HTMLButtonElement);
    await (start as HTMLButtonElement).click();
    await flushPromises();

    expect(api.createTextbookImport).toHaveBeenCalledWith(
      expect.objectContaining({ mode: 'replace_draft', documentId: '30001', title: undefined })
    );
    const confirm = [...document.body.querySelectorAll('button')].find(button => button.textContent?.trim() === '确认导入');
    expect(confirm).toBeInstanceOf(HTMLButtonElement);
    await (confirm as HTMLButtonElement).click();
    await flushPromises();
    expect(api.confirmImport).toHaveBeenCalledWith('replace-public-batch', expect.any(String));
    expect(wrapper.emitted('success')).toHaveLength(1);
    wrapper.unmount();
  });

  it('refreshes an active question batch when the public import-progress event arrives', async () => {
    api.createQuestionImport.mockResolvedValue({
      data: { id: 'push-refresh-batch', status: 'uploaded', importType: 'question', createTime: null, reused: false }
    });
    api.validateImport.mockResolvedValue({
      data: { id: 'push-refresh-batch', status: 'parsing', currentStage: 'read_jsonl' }
    });
    api.getImportProgress
      .mockResolvedValueOnce({
        data: {
          id: 'push-refresh-batch', status: 'parsing', currentStage: 'read_jsonl', progressPercent: 0,
          totalCount: 1, validCount: 0, warningCount: 0, failedCount: 0, knowledgeDiffRequired: false,
          startedTime: null, finishedTime: null, failureTraceId: null
        }
      })
      .mockResolvedValueOnce({
        data: {
          id: 'push-refresh-batch', status: 'waiting_confirm', currentStage: 'finalize_counts', progressPercent: 100,
          totalCount: 1, validCount: 1, warningCount: 0, failedCount: 0, knowledgeDiffRequired: false,
          startedTime: null, finishedTime: null, failureTraceId: null
        }
      });
    api.listImportIssues.mockResolvedValue({ data: { rows: [], total: 0 } });
    const wrapper = mount(ImportDialog, {
      attachTo: document.body,
      props: { modelValue: true, importType: 'question' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();

    const upload = wrapper.findComponent({ name: 'ElUpload' });
    const zip = new globalThis.File(['PK{}'], 'push-refresh.zip', { type: 'application/zip' });
    upload.vm.$emit('update:fileList', [{ name: zip.name, raw: zip }]);
    await (upload.props('onChange') as (file: unknown) => Promise<void>)({ name: zip.name, raw: zip });
    await flushPromises();
    const start = [...document.body.querySelectorAll('button')].find(button => button.textContent?.trim() === '开始预检');
    expect(start).toBeInstanceOf(HTMLButtonElement);
    await (start as HTMLButtonElement).click();
    await flushPromises();
    expect(document.body.textContent).toContain('正在解析');

    vi.useFakeTimers();
    try {
      emitImportProgress({ eventType: 'certmuse.import.progress', batchId: 'push-refresh-batch' });
      await vi.advanceTimersByTimeAsync(300);
      await flushPromises();
    } finally {
      vi.useRealTimers();
    }

    expect(api.getImportProgress).toHaveBeenCalledTimes(2);
    expect(document.body.textContent).toContain('预检完成，请确认导入草稿题目。');
    wrapper.unmount();
  });

  it('confirms a reviewed knowledge-diff import through the visible workspace action', async () => {
    api.getImportContextOptions.mockResolvedValue({
      syllabusVersions: [{ id: '20001', certificationId: 'cert-1', label: '系统架构设计师 · 2026', knowledgeTreeAvailable: true, knowledgePointCount: 10 }],
      examSubjects: [{ id: '101', subjectNo: 1, label: '综合知识' }]
    });
    api.createKnowledgePointImport.mockResolvedValue({
      data: { id: 'reviewed-diff-batch', status: 'uploaded', importType: 'knowledge_point', createTime: null, reused: false }
    });
    api.validateImport.mockResolvedValue({
      data: { id: 'reviewed-diff-batch', status: 'waiting_confirm', currentStage: 'finalize_counts' }
    });
    api.getImportProgress.mockResolvedValue({
      data: {
        id: 'reviewed-diff-batch', status: 'waiting_confirm', progressPercent: 100, totalCount: 3,
        validCount: 3, warningCount: 0, failedCount: 0, knowledgeDiffRequired: true,
        startedTime: null, finishedTime: null, failureTraceId: null
      }
    });
    api.getKnowledgeDiff.mockResolvedValue({
      data: {
        rows: [], total: 0, unchangedCount: 0, updateCount: 1, moveCount: 1, addCount: 1, deleteCount: 1,
        pendingCount: 0, affectedQuestionCount: 4, offlineQuestionCount: 2
      }
    });
    api.listImportIssues.mockResolvedValue({ data: { rows: [], total: 0 } });
    api.confirmImport.mockResolvedValue({ data: { id: 'reviewed-diff-batch', status: 'completed', currentStage: 'persist_knowledge_tree' } });
    const wrapper = mount(ImportDialog, {
      attachTo: document.body,
      props: { modelValue: true, importType: 'knowledge_point', initialSyllabusVersionId: '20001' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();

    const upload = wrapper.findComponent({ name: 'ElUpload' });
    const jsonl = new globalThis.File(['{"code":"1.1"}'], 'reviewed-diff.jsonl', { type: 'application/json' });
    upload.vm.$emit('update:fileList', [{ name: jsonl.name, raw: jsonl }]);
    await (upload.props('onChange') as (file: unknown) => Promise<void>)({ name: jsonl.name, raw: jsonl });
    await flushPromises();
    const start = [...document.body.querySelectorAll('button')].find(button => button.textContent?.trim() === '开始预检');
    expect(start).toBeInstanceOf(HTMLButtonElement);
    await (start as HTMLButtonElement).click();
    await flushPromises();

    const confirm = [...document.body.querySelectorAll('button')].find(button => button.textContent?.trim() === '正式确认导入');
    expect(confirm).toBeInstanceOf(HTMLButtonElement);
    expect((confirm as HTMLButtonElement).disabled).toBe(false);
    await (confirm as HTMLButtonElement).click();
    await flushPromises();

    expect(messages.confirm).toHaveBeenCalledWith(expect.stringContaining('预计影响 4 道题'), '确认导入考纲', expect.any(Object));
    expect(api.confirmImport).toHaveBeenCalledWith('reviewed-diff-batch', expect.any(String));
    expect(wrapper.emitted('success')).toHaveLength(1);
    wrapper.unmount();
  });

  it('cancels precheck upload before creating a batch', async () => {
    messages.confirm.mockRejectedValueOnce(new Error('cancelled'));
    const wrapper = mount(ImportDialog, {
      attachTo: document.body,
      props: { modelValue: true, importType: 'question' },
      global: { plugins: [ElementPlus] }
    });
    await flushPromises();

    const state = wrapper.vm as any;
    state.form.questionCertification = '系统架构设计师';
    state.form.knowledgeSyllabusVersionId = '20001';
    state.fileList = [{ name: 'cancelled.zip', raw: new File(['PK{}'], 'cancelled.zip') }];
    state.hashState = 'success';

    await state.startPrecheck();

    expect(messages.confirm).toHaveBeenCalledWith(
      expect.stringContaining('将上传“cancelled.zip”并开始预检'),
      '确认上传并开始预检',
      expect.objectContaining({ confirmButtonText: '确认上传', cancelButtonText: '取消' })
    );
    expect(api.createQuestionImport).not.toHaveBeenCalled();
    expect(api.validateImport).not.toHaveBeenCalled();
    expect(state.batch).toBeNull();
    wrapper.unmount();
  });

});
