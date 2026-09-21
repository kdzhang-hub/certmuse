import { flushPromises, mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { defineComponent, h } from 'vue';
import QuestionPage from './index.vue';

const catalogApi = vi.hoisted(() => ({ getKnowledgeTree: vi.fn(), listSyllabusVersions: vi.fn() }));
const qualificationApi = vi.hoisted(() => ({ listQualifications: vi.fn() }));
const questionApi = vi.hoisted(() => ({
  deleteQuestion: vi.fn(),
  getQuestionDetail: vi.fn(),
  getQuestionPreview: vi.fn(),
  listQuestions: vi.fn(),
  saveQuestionDraft: vi.fn(),
  submitQuestionReview: vi.fn(),
  takeQuestionOffline: vi.fn()
}));
const modal = vi.hoisted(() => ({ confirm: vi.fn(), msgSuccess: vi.fn() }));
const messages = vi.hoisted(() => ({
  confirm: vi.fn(),
  error: vi.fn(),
  info: vi.fn(),
  success: vi.fn(),
  warning: vi.fn()
}));

vi.mock('@/api/certmuse/catalog/knowledge', () => catalogApi);
vi.mock('@/api/certmuse/catalog/subject-version', () => qualificationApi);
vi.mock('@/api/certmuse/question', () => questionApi);
vi.mock('@/plugins/modal', () => ({ default: modal }));
vi.mock('@/components/certmuse/KnowledgeDirectoryPanel.vue', () => ({
  default: {
    name: 'KnowledgeDirectoryPanel',
    emits: ['clear', 'select', 'update:collapsed'],
    template: '<div class="directory-panel-stub"><button class="directory-subject" @click="$emit(\'select\', { id: \'subject-1\', label: \'综合知识\' })">选择科目</button><button class="directory-leaf" @click="$emit(\'select\', { id: \'knowledge-1\', label: \'1.1 架构\' })">选择知识点</button><button class="directory-clear" @click="$emit(\'clear\')">清除知识点</button><button class="directory-collapse" @click="$emit(\'update:collapsed\', true)">收起目录</button></div>'
  }
}));
vi.mock('@/components/certmuse/QuestionPreviewDialog.vue', () => ({
  default: {
    name: 'QuestionPreviewDialog',
    emits: ['update:modelValue'],
    template: '<button class="preview-close" @click="$emit(\'update:modelValue\', false)">关闭预览</button>'
  }
}));
vi.mock('@/views/certmuse/catalog/import/ImportDialog.vue', () => ({
  default: {
    name: 'ImportDialog',
    emits: ['success', 'update:modelValue'],
    template: '<div class="import-dialog-stub"><button class="import-success" @click="$emit(\'success\')">导入成功</button><button class="import-close" @click="$emit(\'update:modelValue\', false)">关闭导入</button></div>'
  }
}));

const PaginationStub = defineComponent({
  name: 'ElPagination',
  emits: ['current-change', 'size-change', 'update:currentPage', 'update:pageSize'],
  setup(_, { emit }) {
    return () =>
      h('div', { class: 'question-pagination-stub' }, [
        h('button', { class: 'question-page-two', onClick: () => emit('update:currentPage', 2) }, '第2页'),
        h('button', { class: 'question-page-load', onClick: () => emit('current-change', 2) }, '加载第2页'),
        h('button', { class: 'question-size', onClick: () => emit('update:pageSize', 20) }, '每页20'),
        h('button', { class: 'question-size-load', onClick: () => emit('size-change', 20) }, '应用每页20')
      ]);
  }
});
vi.mock('element-plus', async importOriginal => {
  const elementPlus = await importOriginal<typeof import('element-plus')>();
  return {
    ...elementPlus,
    ElMessage: { error: messages.error, info: messages.info, success: messages.success, warning: messages.warning },
    ElMessageBox: { confirm: messages.confirm }
  };
});

const question = (status: 'draft' | 'rejected' | 'published' | 'pending_review', id = 'q-1') => ({
  questionId: id,
  revisionId: `revision-${id}`,
  revisionNo: 1,
  questionCode: id.toUpperCase(),
  syllabusVersionId: 'syllabus-1',
  syllabusVersionName: '2026版',
  stemSummary: '题干',
  examSubjectId: 'subject-1',
  examSubjectName: '综合知识',
  questionType: 'CHOICE',
  difficulty: 'easy',
  status,
  updatedTime: '2026-08-09T10:00:00+08:00'
});

const tree = {
  subjects: [{ id: 'subject-1', subjectName: '综合知识' }],
  nodes: [
    {
      id: 'directory-1',
      parentId: null,
      examSubjectId: 'subject-1',
      syllabusNumber: '1',
      syllabusTitle: '基础',
      hasChildren: true
    },
    {
      id: 'knowledge-1',
      parentId: 'directory-1',
      examSubjectId: 'subject-1',
      syllabusNumber: '1.1',
      syllabusTitle: '架构',
      hasChildren: false
    },
    {
      id: 'knowledge-2',
      parentId: null,
      examSubjectId: 'subject-1',
      syllabusNumber: '2',
      syllabusTitle: '设计',
      hasChildren: false
    }
  ]
};

function mountPage(grantedPermissions = ['*']) {
  const granted = new Set(grantedPermissions);
  return mount(QuestionPage, {
    attachTo: document.body,
    global: {
      plugins: [ElementPlus],
      directives: {
        hasPermi: {
          mounted(element, binding) {
            const required = binding.value as string[];
            if (!required.some(permission => granted.has('*') || granted.has(permission))) element.remove();
          }
        }
      },
      stubs: {
        Pagination: true,
        ElPagination: PaginationStub,
        RightToolbar: {
          name: 'RightToolbar',
          emits: ['query-table', 'update:showSearch'],
          template: '<button class="right-toolbar-stub" @click="$emit(\'query-table\')">刷新题目</button>'
        }
      }
    }
  });
}

describe('question management page', () => {
  beforeEach(() => {
    Object.values(catalogApi).forEach(mock => mock.mockReset());
    Object.values(qualificationApi).forEach(mock => mock.mockReset());
    Object.values(questionApi).forEach(mock => mock.mockReset());
    Object.values(modal).forEach(mock => mock.mockReset());
    Object.values(messages).forEach(mock => mock.mockReset());
    catalogApi.listSyllabusVersions.mockResolvedValue({
      data: {
        rows: [{ id: 'syllabus-1', displayName: '系统架构设计师 · 2026', certificationName: '系统架构设计师' }],
        total: 1
      }
    });
    catalogApi.getKnowledgeTree.mockResolvedValue({ data: tree });
    qualificationApi.listQualifications.mockResolvedValue({
      data: {
        rows: [
          {
            id: 'qualification-1',
            certificationName: '系统架构设计师',
            versions: [
              { id: 'syllabus-old', publishedDate: '2025-01-01', updateTime: '2025-01-01T00:00:00+08:00' },
              { id: 'syllabus-1', publishedDate: '2026-01-01', updateTime: '2026-01-01T00:00:00+08:00' }
            ]
          }
        ],
        total: 1
      }
    });
    questionApi.listQuestions.mockResolvedValue({
      data: { rows: [question('draft'), question('published', 'q-2')], total: 2 }
    });
    questionApi.getQuestionDetail.mockResolvedValue({
      data: {
        questionId: 'q-1',
        revisionId: 'revision-q-1',
        rowVersion: 'v1',
        status: 'draft',
        reviewOpinion: '  ',
        syllabusVersionId: 'syllabus-1',
        examSubjectId: 'subject-1',
        examSubjectName: '综合知识',
        questionType: 'CHOICE',
        difficulty: 'easy',
        knowledgeBindings: [
          { knowledgePointId: 'knowledge-1', knowledgePointLabel: '架构', relationRole: 'primary', sortOrder: 0 }
        ],
        estimatedSeconds: 60,
        stem: '完整题干',
        options: [
          { label: 'A', content: '选项 A', sortOrder: 1 },
          { label: 'B', content: '选项 B', sortOrder: 2 }
        ],
        answer: { value: ['A'] },
        analysis: '解析',
        commonMistakes: '易错点'
      }
    });
    questionApi.getQuestionPreview.mockResolvedValue({
      data: { questionId: 'q-1', revisionId: 'revision-q-1', stem: '预览题干' }
    });
    questionApi.saveQuestionDraft.mockResolvedValue({ data: { createdNewRevision: false } });
    questionApi.deleteQuestion.mockResolvedValue({});
    questionApi.submitQuestionReview.mockResolvedValue({});
    questionApi.takeQuestionOffline.mockResolvedValue({});
    modal.confirm.mockResolvedValue(undefined);
    messages.confirm.mockResolvedValue(undefined);
  });

  it('loads questions, scopes knowledge queries and builds the directory', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;

    expect(questionApi.listQuestions).toHaveBeenCalledWith(
      expect.objectContaining({ includeDescendants: true, pageNum: 1, pageSize: 10 })
    );
    expect(qualificationApi.listQualifications).toHaveBeenCalledWith({ status: '0', pageNum: 1, pageSize: 100 });
    expect(vm.rows).toHaveLength(2);
    expect(vm.compareQuestions(question('rejected'), question('draft'))).toBeLessThan(0);
    expect(vm.difficultyLabel(null)).toBe('未标注');
    expect(vm.statusTag('published')).toBe('success');

    await vm.selectSyllabus('syllabus-1');
    await flushPromises();
    expect(catalogApi.getKnowledgeTree).toHaveBeenCalledWith('syllabus-1');
    expect(vm.knowledgeDirectories['syllabus-1'][0].children[0].children[0].label).toBe('1.1 架构');
    vm.selectKnowledgePoint({ id: 'subject-1', label: '综合知识' });
    await flushPromises();
    expect(vm.scope.subjectId).toBe('subject-1');
    vm.selectKnowledgePoint({ id: 'knowledge-1', label: '架构' });
    await flushPromises();
    expect(vm.scope.subjectId).toBe('');
    expect(vm.selectedKnowledgeId).toBe('knowledge-1');
    vm.clearKnowledgePoint();
    expect(vm.selectedKnowledgeId).toBe('');
  });

  it('does not expose question offline operations without their dedicated permission', async () => {
    const wrapper = mountPage(['certmuse:question:list']);
    await flushPromises();

    expect(wrapper.findAll('button').some(button => button.text().trim().startsWith('批量下架'))).toBe(false);
    wrapper.unmount();
  });

  it('maintains knowledge bindings and choice options', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;
    vm.form.knowledgeBindings = [
      { knowledgePointId: 'knowledge-1', knowledgePointLabel: '', relationRole: 'secondary', sortOrder: 0 }
    ];
    vm.form.syllabusId = 'syllabus-1';
    vm.form.examSubjectId = 'subject-1';
    await vm.loadKnowledgeTree('syllabus-1');
    vm.ensureExactlyOnePrimaryKnowledge();
    expect(vm.form.knowledgeBindings[0].relationRole).toBe('primary');
    expect(vm.editorExpandedKeys).toContain('knowledge-1');
    expect(vm.currentSyllabusKnowledgeOptions[0].label).toBe('1.1 架构');

    vm.addKnowledgeFromTree({ id: 'knowledge-2', label: '设计' });
    expect(vm.form.knowledgeBindings).toHaveLength(2);
    expect(vm.isKnowledgePointBound('knowledge-2', -1)).toBe(true);
    vm.setPrimaryKnowledge(1);
    expect(vm.primaryKnowledgeId).toBe('knowledge-2');
    vm.removeKnowledgeBinding(0);
    expect(vm.form.knowledgeBindings[0].relationRole).toBe('primary');

    vm.handleQuestionTypeChange('CHOICE');
    expect(vm.form.options.map((item: any) => item.label)).toEqual(['A', 'B', 'C', 'D']);
    vm.form.correctOptionKey = 'C';
    vm.removeOption(1);
    expect(vm.form.options.map((item: any) => item.label)).toEqual(['A', 'B', 'C']);
    expect(vm.form.correctOptionKey).toBe('B');
    vm.handleQuestionTypeChange('ESSAY');
    expect(vm.form.options).toEqual([]);
  });

  it('opens a detail and preview, then saves a draft with normalized answer data', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;
    await vm.openEdit(question('draft'));
    await flushPromises();
    expect(vm.formVisible).toBe(true);
    expect(vm.form.stem).toBe('完整题干');
    expect(vm.primaryKnowledgeId).toBe('knowledge-1');
    await vm.openPreview(question('draft'));
    expect(vm.previewVisible).toBe(true);
    expect(vm.previewItem.stem).toBe('预览题干');

    vm.formRef = { validate: vi.fn().mockResolvedValue(true), clearValidate: vi.fn() };
    await vm.saveQuestion();
    expect(questionApi.saveQuestionDraft).toHaveBeenCalledWith(
      'q-1',
      expect.objectContaining({
        baseRevisionId: 'revision-q-1',
        rowVersion: 'v1',
        options: [
          expect.objectContaining({ label: 'A', sortOrder: 1 }),
          expect.objectContaining({ label: 'B', sortOrder: 2 })
        ],
        answer: { schemaVersion: '1.0', answerType: 'option_keys', selectionMode: 'single', value: ['A'] }
      })
    );
    expect(messages.success).toHaveBeenCalledWith('题目草稿已保存');
  });

  it('keeps failed batch actions selected and reports partial success', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;
    const draft = question('draft');
    const published = question('published', 'q-2');
    vm.questionSelection = [draft, published];
    questionApi.deleteQuestion.mockResolvedValueOnce({}).mockRejectedValueOnce(new Error('引用冲突'));
    await vm.removeSelectedQuestions();
    await flushPromises();
    expect(questionApi.deleteQuestion).toHaveBeenCalledWith('q-1');
    expect(vm.questionSelection).toEqual([]);

    vm.questionSelection = [published];
    questionApi.takeQuestionOffline.mockResolvedValue({});
    await vm.takeSelectedOffline();
    await flushPromises();
    expect(questionApi.takeQuestionOffline).toHaveBeenCalledWith('revision-q-2');
    expect(messages.success).toHaveBeenCalledWith('已下架全部 1 道题目');
  });

  it('submits all drafts across pages and handles empty scopes', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;
    questionApi.listQuestions.mockImplementation(async (params: any) => {
      if (params.status !== 'draft') return { data: { rows: [], total: 0 } };
      return params.pageNum === 1
        ? { data: { rows: [question('draft', 'q-1')], total: 101 } }
        : { data: { rows: [question('draft', 'q-2')], total: 101 } };
    });
    questionApi.submitQuestionReview.mockResolvedValue({});
    await vm.submitAllDraftsForReview();
    expect(questionApi.listQuestions).toHaveBeenCalledWith(
      expect.objectContaining({ status: 'draft', pageNum: 2, pageSize: 100 })
    );
    expect(questionApi.submitQuestionReview).toHaveBeenCalledTimes(2);

    questionApi.listQuestions.mockResolvedValue({ data: { rows: [], total: 0 } });
    await vm.submitAllDraftsForReview();
    expect(messages.info).toHaveBeenCalledWith('当前范围内没有可提交的草稿题目');
  });

  it('resets empty scopes and protects knowledge, option and scoring boundaries', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;

    vm.scope.syllabus = 'syllabus-1';
    vm.scope.subjectId = 'subject-1';
    vm.selectedKnowledgeId = 'knowledge-1';
    vm.scope.syllabus = '';
    vm.selectSyllabus('');
    await flushPromises();
    expect(vm.scope.syllabus).toBe('');
    expect(vm.scope.subjectId).toBe('');
    expect(vm.selectedKnowledgeId).toBe('');

    await vm.loadKnowledgeTree('syllabus-1');
    vm.form.syllabusId = 'syllabus-1';
    vm.form.examSubjectId = 'subject-1';
    vm.form.knowledgeBindings = [];
    vm.ensureExactlyOnePrimaryKnowledge();
    expect(vm.form.knowledgeBindings[0].knowledgePointId).toBe('knowledge-1');
    vm.removeKnowledgeBinding(0);
    expect(vm.form.knowledgeBindings).toHaveLength(1);

    vm.handleQuestionTypeChange('CHOICE');
    for (let index = 0; index < 10; index++) vm.addOption();
    expect(vm.form.options).toHaveLength(10);
    vm.addOption();
    expect(vm.form.options).toHaveLength(10);
    vm.form.correctOptionKey = 'J';
    vm.removeOption(9);
    expect(vm.form.correctOptionKey).toBe('');

    vm.openQuestionImport();
    expect(vm.importVisible).toBe(true);
    expect(vm.filterEditorKnowledgeNode('架构', { id: 'knowledge-1', label: '1.1 架构' })).toBe(true);
    expect(vm.filterEditorKnowledgeNode('不存在', { id: 'knowledge-1', label: '1.1 架构' })).toBe(false);
  });

  it('serializes subjective answers and refuses save when form validation fails', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;
    questionApi.getQuestionDetail.mockResolvedValueOnce({
      data: {
        questionId: 'q-case',
        revisionId: 'revision-q-case',
        rowVersion: 'v2',
        status: 'rejected',
        reviewOpinion: '请补充评分点',
        syllabusVersionId: 'syllabus-1',
        examSubjectId: 'subject-1',
        examSubjectName: '综合知识',
        questionType: 'CASE',
        difficulty: 'hard',
        knowledgeBindings: [
          { knowledgePointId: 'knowledge-1', knowledgePointLabel: '架构', relationRole: 'primary', sortOrder: 0 }
        ],
        estimatedSeconds: null,
        stem: '案例题干',
        options: [],
        answer: { value: '参考答案' },
        analysis: null,
        commonMistakes: null
      }
    });
    await vm.openEdit(question('rejected', 'q-case'));
    expect(vm.form.referenceAnswer).toBe('参考答案');
    expect(vm.editingReviewOpinion).toBe('请补充评分点');

    vm.formRef = { validate: vi.fn().mockResolvedValue(false), clearValidate: vi.fn() };
    await vm.saveQuestion();
    expect(questionApi.saveQuestionDraft).not.toHaveBeenCalled();

    vm.formRef = { validate: vi.fn().mockResolvedValue(true), clearValidate: vi.fn() };
    await vm.saveQuestion();
    expect(questionApi.saveQuestionDraft).toHaveBeenCalledWith(
      'q-case',
      expect.objectContaining({
        questionType: 'CASE',
        options: [],
        answer: { schemaVersion: '1.0', answerType: 'reference_text', value: '参考答案' }
      })
    );
    expect(questionApi.saveQuestionDraft.mock.calls.at(-1)?.[1]).not.toHaveProperty('scoringPoints');
  });

  it('resets pagination and preserves failed batch submissions for retry', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;

    vm.pagination.pageNum = 2;
    vm.handlePageSizeChange();
    expect(vm.pagination.pageNum).toBe(1);

    questionApi.submitQuestionReview.mockResolvedValueOnce({}).mockRejectedValueOnce(new Error('revision conflict'));
    await vm.submitReviewTargets([question('draft'), question('rejected', 'q-2')]);
    await flushPromises();

    expect(questionApi.submitQuestionReview).toHaveBeenNthCalledWith(1, 'revision-q-1');
    expect(questionApi.submitQuestionReview).toHaveBeenNthCalledWith(2, 'revision-q-2');
    expect(messages.warning).toHaveBeenCalledWith('提交完成：成功 1 道，失败 1 道；失败题目仍保留原状态');

    questionApi.listQuestions.mockClear();
    vm.handlePageSizeChange();
    await flushPromises();
    expect(questionApi.listQuestions).toHaveBeenCalled();
    wrapper.unmount();
  });

  it('runs individual delete, review and take-offline actions and resets filters', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;

    await vm.removeQuestion(question('draft', 'q-single'));
    expect(questionApi.deleteQuestion).toHaveBeenCalledWith('q-single');
    expect(modal.msgSuccess).toHaveBeenCalledWith('题目已删除');

    modal.confirm.mockRejectedValueOnce(new Error('cancelled'));
    await vm.submitReview(question('draft', 'q-cancelled'));
    expect(questionApi.submitQuestionReview).not.toHaveBeenCalledWith('revision-q-cancelled');

    await vm.submitReview(question('draft', 'q-submit'));
    expect(questionApi.submitQuestionReview).toHaveBeenCalledWith('revision-q-submit');
    expect(modal.msgSuccess).toHaveBeenCalledWith('题目已提交审核');

    await vm.takeOffline(question('published', 'q-offline'));
    expect(questionApi.takeQuestionOffline).toHaveBeenCalledWith('revision-q-offline');
    expect(modal.msgSuccess).toHaveBeenCalledWith('题目已下架并退回草稿');

    vm.filters.keyword = '  架构  ';
    vm.filters.certificationId = 'qualification-1';
    vm.filters.questionType = 'CASE';
    vm.filters.difficulty = 'hard';
    vm.filters.status = 'rejected';
    vm.handleCertificationChange('qualification-1');
    await flushPromises();
    expect(vm.scope.syllabus).toBe('syllabus-1');
    vm.pagination.pageNum = 3;
    vm.handleQuery();
    await flushPromises();
    expect(vm.pagination.pageNum).toBe(1);
    expect(questionApi.listQuestions).toHaveBeenLastCalledWith(
      expect.objectContaining({
        keyword: '架构',
        certificationId: 'qualification-1',
        syllabusVersionId: 'syllabus-1',
        questionType: 'CASE',
        difficulty: 'hard',
        status: 'rejected',
        pageNum: 1
      })
    );

    vm.resetFilters();
    await flushPromises();
    expect(vm.filters).toEqual({ keyword: '', certificationId: '', questionType: '', difficulty: '', status: '' });
    expect(vm.scope).toEqual({ syllabus: '', subjectId: '' });
  });

  it('drives filters, directory scope, pagination, and import dialog from rendered controls', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const selectByPlaceholder = (placeholder: string) =>
      wrapper.findAllComponents({ name: 'ElSelect' }).find(component => component.props('placeholder') === placeholder)!;

    const certification = selectByPlaceholder('全部资格');
    certification.vm.$emit('update:modelValue', 'qualification-1');
    certification.vm.$emit('change', 'qualification-1');
    selectByPlaceholder('全部题型').vm.$emit('update:modelValue', 'CASE');
    selectByPlaceholder('全部难度').vm.$emit('update:modelValue', 'hard');
    selectByPlaceholder('全部状态').vm.$emit('update:modelValue', 'rejected');
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text().trim() === '搜索')!.trigger('click');
    await flushPromises();
    expect(questionApi.listQuestions).toHaveBeenLastCalledWith(expect.objectContaining({
      certificationId: 'qualification-1', syllabusVersionId: 'syllabus-1', questionType: 'CASE', difficulty: 'hard', status: 'rejected'
    }));

    await wrapper.get('.directory-subject').trigger('click');
    await flushPromises();
    expect(questionApi.listQuestions).toHaveBeenLastCalledWith(expect.objectContaining({ examSubjectId: 'subject-1' }));
    await wrapper.get('.directory-leaf').trigger('click');
    await flushPromises();
    expect(questionApi.listQuestions).toHaveBeenLastCalledWith(expect.objectContaining({ examSubjectId: undefined, knowledgePointId: 'knowledge-1' }));
    await wrapper.get('.directory-clear').trigger('click');
    await wrapper.get('.directory-collapse').trigger('click');
    await flushPromises();
    expect(wrapper.find('.question-list-layout').classes()).toContain('is-directory-collapsed');

    await wrapper.get('.question-page-two').trigger('click');
    await wrapper.get('.question-page-load').trigger('click');
    await flushPromises();
    expect(questionApi.listQuestions).toHaveBeenLastCalledWith(expect.objectContaining({ pageNum: 2 }));
    await wrapper.get('.question-size').trigger('click');
    await wrapper.get('.question-size-load').trigger('click');
    await flushPromises();

    await wrapper.findAll('button').find(button => button.text().trim() === '新增')!.trigger('click');
    await wrapper.get('.import-close').trigger('click');
    await wrapper.get('.import-success').trigger('click');
    await flushPromises();
    expect(questionApi.listQuestions).toHaveBeenCalled();
    wrapper.unmount();
  });

  it('runs rendered row actions through the public preview and editor models', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const tooltip = (content: string, occurrence = 0) =>
      wrapper.findAllComponents({ name: 'ElTooltip' }).filter(component => component.props('content') === content)[occurrence]!;

    await tooltip('查看').find('button').trigger('click');
    await flushPromises();
    expect(questionApi.getQuestionPreview).toHaveBeenCalledWith('q-1', 'revision-q-1');
    await wrapper.get('.preview-close').trigger('click');
    await tooltip('编辑').find('button').trigger('click');
    await flushPromises();
    expect(questionApi.getQuestionDetail).toHaveBeenCalledWith('q-1', 'revision-q-1');
    const editor = wrapper.findAllComponents({ name: 'ElDialog' }).find(component => component.props('title') === '编辑题目草稿');
    await editor!.findAll('button').find(button => button.text().trim() === '取消')!.trigger('click');
    await tooltip('提交审核').find('button').trigger('click');
    await flushPromises();
    expect(questionApi.submitQuestionReview).toHaveBeenCalledWith('revision-q-1');
    await tooltip('删除').find('button').trigger('click');
    await flushPromises();
    expect(questionApi.deleteQuestion).toHaveBeenCalledWith('q-1');
    await tooltip('下架').find('button').trigger('click');
    await flushPromises();
    expect(questionApi.takeQuestionOffline).toHaveBeenCalledWith('revision-q-2');
    wrapper.unmount();
  });

  it('processes selected rows with the rendered batch review, offline, and delete controls', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const table = wrapper.findComponent({ name: 'ElTable' });
    const draft = question('draft');
    const published = question('published', 'q-2');
    table.vm.$emit('selection-change', [draft, published]);
    await flushPromises();

    const button = (label: string) => wrapper.findAll('button').find(item => item.text().trim().startsWith(label))!;
    await button('提交审核').trigger('click');
    await flushPromises();
    expect(questionApi.submitQuestionReview).toHaveBeenCalledWith('revision-q-1');

    table.vm.$emit('selection-change', [published]);
    await flushPromises();
    await button('批量下架').trigger('click');
    await flushPromises();
    expect(questionApi.takeQuestionOffline).toHaveBeenCalledWith('revision-q-2');

    table.vm.$emit('selection-change', [draft]);
    await flushPromises();
    await button('批量删除').trigger('click');
    await flushPromises();
    expect(questionApi.deleteQuestion).toHaveBeenCalledWith('q-1');
    expect(messages.success).toHaveBeenCalledWith('已删除全部 1 道题目');
    wrapper.unmount();
  });

  it('renders empty results and reports a failed qualification option load without losing the question list', async () => {
    qualificationApi.listQualifications.mockRejectedValueOnce(new Error('network unavailable'));
    questionApi.listQuestions.mockResolvedValueOnce({ data: { rows: [], total: 0 } });
    const wrapper = mountPage();
    await flushPromises();
    expect(messages.error).toHaveBeenCalledWith('考试资格选项加载失败，请稍后重试');
    expect((wrapper.findComponent({ name: 'ElTable' }).props('data') as unknown[])).toHaveLength(0);
    expect(wrapper.text()).toContain('没有符合条件的题目');
    wrapper.unmount();
  });

  it('saves a rendered rejected case-question edit without scoring inputs', async () => {
    questionApi.getQuestionDetail.mockResolvedValueOnce({
      data: {
        questionId: 'q-case', revisionId: 'revision-q-case', rowVersion: 'v2', status: 'rejected',
        reviewOpinion: '请补充评分点', syllabusVersionId: 'syllabus-1', examSubjectId: 'subject-1',
        examSubjectName: '综合知识', questionType: 'CASE', difficulty: 'hard',
        knowledgeBindings: [{ knowledgePointId: 'knowledge-1', knowledgePointLabel: '架构', relationRole: 'primary', sortOrder: 0 }],
        estimatedSeconds: 120, stem: '案例题干', options: [], answer: { value: '旧答案' },
        analysis: '旧解析', commonMistakes: null
      }
    });
    const wrapper = mountPage();
    await flushPromises();
    const edit = wrapper.findAllComponents({ name: 'ElTooltip' }).find(component => component.props('content') === '编辑')!;
    await edit.find('button').trigger('click');
    await flushPromises();
    expect(document.body.textContent).toContain('审核驳回原因');
    expect(document.body.textContent).toContain('请补充评分点');
    const reference = wrapper.find('textarea[placeholder="草稿允许暂缺，提交审核前必须补齐"]');
    await reference.setValue('补充后的参考答案');
    expect(wrapper.find('input[placeholder="评分标准"]').exists()).toBe(false);
    const dialog = wrapper.findAllComponents({ name: 'ElDialog' }).find(component => component.props('title') === '编辑题目草稿')!;
    const form = dialog.findComponent({ name: 'ElForm' });
    (form.vm as unknown as { validate: () => Promise<boolean> }).validate = vi.fn().mockResolvedValue(true);
    await dialog.findAll('button').find(item => item.text().trim() === '保存草稿')!.trigger('click');
    await flushPromises();
    expect(questionApi.saveQuestionDraft).toHaveBeenCalledWith('q-case', expect.objectContaining({
      questionType: 'CASE',
      answer: { schemaVersion: '1.0', answerType: 'reference_text', value: '补充后的参考答案' }
    }));
    expect(questionApi.saveQuestionDraft.mock.calls.at(-1)?.[1]).not.toHaveProperty('scoringPoints');
    wrapper.unmount();
  });

  it('keeps failed rendered batch actions selected and describes partial completion', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const table = wrapper.findComponent({ name: 'ElTable' });
    const draft = question('draft');
    const rejected = question('rejected', 'q-2');
    questionApi.submitQuestionReview.mockResolvedValueOnce({}).mockRejectedValueOnce(new Error('revision conflict'));
    table.vm.$emit('selection-change', [draft, rejected]);
    await flushPromises();
    const submit = wrapper.findAll('button').find(item => item.text().trim().startsWith('提交审核'))!;
    await submit.trigger('click');
    await flushPromises();
    expect(questionApi.submitQuestionReview).toHaveBeenCalledWith('revision-q-1');
    expect(questionApi.submitQuestionReview).toHaveBeenCalledWith('revision-q-2');
    expect(messages.warning).toHaveBeenCalledWith('提交完成：成功 1 道，失败 1 道；失败题目仍保留原状态');

    const published = question('published', 'q-published');
    questionApi.takeQuestionOffline.mockRejectedValueOnce(new Error('offline conflict'));
    table.vm.$emit('selection-change', [published]);
    await flushPromises();
    await wrapper.findAll('button').find(item => item.text().trim().startsWith('批量下架'))!.trigger('click');
    await flushPromises();
    expect(messages.warning).toHaveBeenCalledWith('下架完成：成功 0 道，失败 1 道');

    questionApi.deleteQuestion.mockRejectedValueOnce(new Error('reference conflict'));
    table.vm.$emit('selection-change', [draft]);
    await flushPromises();
    await wrapper.findAll('button').find(item => item.text().trim().startsWith('批量删除'))!.trigger('click');
    await flushPromises();
    expect(messages.warning).toHaveBeenCalledWith('删除完成：成功 0 道，失败 1 道');
    wrapper.unmount();
  });

  it('submits all drafts within the selected rendered scope and acknowledges a new revision save', async () => {
    questionApi.listQuestions.mockImplementation(async (params: Record<string, unknown>) => {
      if (params.status === 'draft') {
        return { data: { rows: [question('draft', 'q-scope')], total: 1 } };
      }
      return { data: { rows: [question('draft')], total: 1 } };
    });
    questionApi.saveQuestionDraft.mockResolvedValueOnce({ data: { createdNewRevision: true } });
    const wrapper = mountPage();
    await flushPromises();
    const certification = wrapper
      .findAllComponents({ name: 'ElSelect' })
      .find(component => component.props('placeholder') === '全部资格')!;
    certification.vm.$emit('update:modelValue', 'qualification-1');
    certification.vm.$emit('change', 'qualification-1');
    await flushPromises();
    await wrapper.findAll('button').find(item => item.text().trim() === '提交全部草稿')!.trigger('click');
    await flushPromises();
    expect(messages.confirm).toHaveBeenCalledWith(
      expect.stringContaining('当前筛选范围内全部 1 道草稿题'),
      '提交全部草稿',
      expect.objectContaining({ confirmButtonText: '提交 1 道' })
    );
    expect(questionApi.submitQuestionReview).toHaveBeenCalledWith('revision-q-scope');
    expect(messages.success).toHaveBeenCalledWith('已提交全部 1 道题目');

    const edit = wrapper.findAllComponents({ name: 'ElTooltip' }).find(component => component.props('content') === '编辑')!;
    await edit.find('button').trigger('click');
    await flushPromises();
    const dialog = wrapper.findAllComponents({ name: 'ElDialog' }).find(component => component.props('title') === '编辑题目草稿')!;
    const form = dialog.findComponent({ name: 'ElForm' });
    (form.vm as unknown as { validate: () => Promise<boolean> }).validate = vi.fn().mockResolvedValue(true);
    await dialog.findAll('button').find(item => item.text().trim() === '保存草稿')!.trigger('click');
    await flushPromises();
    expect(messages.success).toHaveBeenCalledWith('已创建并保存新的草稿修订版');
    wrapper.unmount();
  });

  it('switches the visible editor between choice and essay structures and warns before published revisions are changed', async () => {
    const wrapper = mountPage();
    await flushPromises();

    const edit = wrapper.findAllComponents({ name: 'ElTooltip' }).find(component => component.props('content') === '编辑')!;
    await edit.find('button').trigger('click');
    await flushPromises();
    let dialog = wrapper.findAllComponents({ name: 'ElDialog' }).find(component => component.props('title') === '编辑题目草稿')!;
    const typeSelect = dialog.findAllComponents({ name: 'ElSelect' })[0];
    typeSelect.vm.$emit('update:modelValue', 'ESSAY');
    typeSelect.vm.$emit('change', 'ESSAY');
    await flushPromises();
    expect(dialog.text()).toContain('论文题目与写作要求');
    expect(dialog.text()).toContain('参考提纲 / 范文要点');
    expect(dialog.findAll('.scoring-point-row')).toHaveLength(0);
    expect(dialog.text()).not.toContain('添加评分点');

    typeSelect.vm.$emit('update:modelValue', 'CHOICE');
    typeSelect.vm.$emit('change', 'CHOICE');
    await flushPromises();
    expect(dialog.text()).toContain('选项与标准答案');
    expect(dialog.findAll('.option-editor-row')).toHaveLength(4);
    await dialog.findAll('button').find(button => button.text().trim() === '添加选项')!.trigger('click');
    expect(dialog.findAll('.option-editor-row')).toHaveLength(5);

    await dialog.findAll('button').find(button => button.text().trim() === '取消')!.trigger('click');
    questionApi.getQuestionDetail.mockResolvedValueOnce({
      data: {
        questionId: 'q-published', revisionId: 'revision-published', rowVersion: 'v9', status: 'published',
        syllabusVersionId: 'syllabus-1', examSubjectId: 'subject-1', examSubjectName: '综合知识',
        questionType: 'CHOICE', difficulty: 'easy', knowledgeBindings: [{ knowledgePointId: 'knowledge-1', relationRole: 'primary', sortOrder: 0 }],
        estimatedSeconds: 60, stem: '已发布题干', options: [{ label: 'A', content: '选项 A', sortOrder: 1 }, { label: 'B', content: '选项 B', sortOrder: 2 }],
        answer: { value: ['A'] }, analysis: '', commonMistakes: ''
      }
    });
    const vm = wrapper.vm as any;
    await vm.openEdit(question('published', 'q-published'));
    await flushPromises();
    dialog = wrapper.findAllComponents({ name: 'ElDialog' }).find(component => component.props('title') === '编辑题目草稿')!;
    expect(dialog.text()).toContain('当前为已发布题目：正式保存时应创建新的草稿修订版。');
    wrapper.unmount();
  });
});
