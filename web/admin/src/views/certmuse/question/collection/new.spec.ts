import ElementPlus from 'element-plus';
import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import CollectionEditor from './new.vue';

const route = vi.hoisted(() => ({ path: '/content/collections/new', fullPath: '/content/collections/new', query: {} as Record<string, string> }));
const router = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }));
const beforeRouteLeave = vi.hoisted(() => vi.fn());
const catalogApi = vi.hoisted(() => ({ getKnowledgeTree: vi.fn(), listSyllabusVersions: vi.fn() }));
const questionApi = vi.hoisted(() => ({ getQuestionDetail: vi.fn(), getQuestionPreview: vi.fn() }));
const collectionApi = vi.hoisted(() => ({ createCollection: vi.fn(), getCollectionRevision: vi.fn(), saveCollectionRevision: vi.fn() }));
const messages = vi.hoisted(() => ({ confirm: vi.fn(), error: vi.fn(), success: vi.fn(), warning: vi.fn() }));

vi.mock('vue-router', () => ({
  onBeforeRouteLeave: beforeRouteLeave,
  useRoute: () => route,
  useRouter: () => router
}));
vi.mock('@/api/certmuse/catalog/knowledge', () => catalogApi);
vi.mock('@/api/certmuse/question', () => questionApi);
vi.mock('@/api/certmuse/question/collection', () => collectionApi);
vi.mock('@/components/certmuse/QuestionSearchPicker.vue', () => ({
  default: {
    props: ['syllabusVersionId', 'syllabusLabel', 'knowledgeDirectory', 'selectedRevisionIds'],
    emits: ['select', 'preview'],
    template: `
      <section class="question-picker-stub">
        <button class="picker-add-choice" @click="$emit('select', { questionId: 'q-1', revisionId: 'r-1', revisionNo: 1, questionCode: 'Q-001', syllabusVersionId: 's1', syllabusVersionName: '2026', stemSummary: '选择题干', examSubjectId: 'subject-1', examSubjectName: '综合知识', questionType: 'CHOICE', difficulty: 'easy', status: 'draft', updatedTime: '2026-08-09' }, 'kp-1')">加入选择题</button>
        <button class="picker-add-essay" @click="$emit('select', { questionId: 'q-2', revisionId: 'r-2', revisionNo: 1, questionCode: 'Q-002', syllabusVersionId: 's1', syllabusVersionName: '2026', stemSummary: '论文题干', examSubjectId: 'subject-1', examSubjectName: '综合知识', questionType: 'ESSAY', difficulty: 'hard', status: 'draft', updatedTime: '2026-08-09' }, 'kp-1')">加入论文题</button>
        <button class="picker-preview" @click="$emit('preview', { questionId: 'q-1', revisionId: 'r-1', revisionNo: 1, questionCode: 'Q-001', syllabusVersionId: 's1', syllabusVersionName: '2026', stemSummary: '选择题干', examSubjectId: 'subject-1', examSubjectName: '综合知识', questionType: 'CHOICE', difficulty: 'easy', status: 'draft', updatedTime: '2026-08-09' })">预览题目</button>
        <button class="picker-preview-essay" @click="$emit('preview', { questionId: 'q-2', revisionId: 'r-2', revisionNo: 1, questionCode: 'Q-002', syllabusVersionId: 's1', syllabusVersionName: '2026', stemSummary: '论文题干', examSubjectId: 'subject-1', examSubjectName: '综合知识', questionType: 'ESSAY', difficulty: 'hard', status: 'rejected', updatedTime: '2026-08-09' })">预览论文题</button>
      </section>`
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

const syllabusRows = [
  { id: 's1', certificationId: 'c1', certificationName: '系统架构设计师', displayName: '系统架构设计师 · 2026' },
  { id: 's2', certificationId: 'c2', certificationName: '软件设计师', displayName: '软件设计师 · 2026' }
];
const choice = {
  questionId: 'q-1', revisionId: 'r-1', revisionNo: 1, questionCode: 'Q-001', syllabusVersionId: 's1', syllabusVersionName: '2026',
  stemSummary: '选择题干', examSubjectId: 'subject-1', examSubjectName: '综合知识', questionType: 'CHOICE', difficulty: 'easy', status: 'draft', updatedTime: '2026-08-09'
};
const essay = { ...choice, questionId: 'q-2', revisionId: 'r-2', questionCode: 'Q-002', stemSummary: '论文题干', questionType: 'ESSAY', difficulty: 'hard' };

function mountPage() {
  return mount(CollectionEditor, {
    attachTo: document.body,
    global: { plugins: [ElementPlus] }
  });
}

describe('collection editor page', () => {
  beforeEach(() => {
    route.query = {};
    Object.values(router).forEach(mock => mock.mockReset());
    Object.values(catalogApi).forEach(mock => mock.mockReset());
    Object.values(questionApi).forEach(mock => mock.mockReset());
    Object.values(collectionApi).forEach(mock => mock.mockReset());
    Object.values(messages).forEach(mock => mock.mockReset());
    beforeRouteLeave.mockReset();
    catalogApi.listSyllabusVersions.mockResolvedValue({ data: { rows: syllabusRows, total: syllabusRows.length } });
    catalogApi.getKnowledgeTree.mockResolvedValue({ data: { subjects: [{ id: 'subject-1', subjectName: '综合知识' }], nodes: [{ id: 'kp-1', parentId: null, examSubjectId: 'subject-1', syllabusNumber: '1.1', syllabusTitle: '架构' }] } });
    questionApi.getQuestionDetail.mockResolvedValue({ data: { knowledgeBindings: [{ knowledgePointId: 'kp-1', relationRole: 'primary' }] } });
    questionApi.getQuestionPreview.mockResolvedValue({ data: {
      stem: '预览题干 [图片 1] {{question-image: 1}}', answer: { value: ['A', 'B'] }, analysis: '解析', options: [], images: [
        { sortOrder: 2, url: 'b.png', alt: '' }, { sortOrder: 1, url: 'http://minio:9000/certmuse/a.png?signature=abc', alt: '图一' }
      ]
    } });
    collectionApi.createCollection.mockResolvedValue({ data: { collectionId: 'c-1', revisionId: 'r-1', collectionCode: 'COL-1', rowVersion: '1' } });
    collectionApi.saveCollectionRevision.mockResolvedValue({ data: { collectionCode: 'COL-1', rowVersion: '2' } });
    messages.confirm.mockResolvedValue(undefined);
  });

  it('asks before a visible qualification change clears the rendered basket', async () => {
    const wrapper = mountPage();
    await flushPromises();
    await wrapper.get('.picker-add-choice').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('选择题干');

    const certification = wrapper
      .findAllComponents({ name: 'ElSelect' })
      .find(component => component.props('placeholder') === '请选择考试资格');
    expect(certification).toBeDefined();
    messages.confirm.mockRejectedValueOnce('cancel');
    certification!.vm.$emit('update:modelValue', 'c2');
    certification!.vm.$emit('change', 'c2');
    await flushPromises();
    expect(messages.confirm).toHaveBeenCalledWith(
      '切换考试资格会清空当前题集中的题目，是否继续？',
      '切换考试资格',
      expect.objectContaining({ confirmButtonText: '清空并切换' })
    );
    expect(wrapper.text()).toContain('选择题干');

    // Element Plus reports the component's model rollback before the next user selection.
    certification!.vm.$emit('change', 'c1');
    messages.confirm.mockResolvedValueOnce(undefined);
    certification!.vm.$emit('update:modelValue', 'c2');
    certification!.vm.$emit('change', 'c2');
    await flushPromises();
    expect(wrapper.text()).toContain('从题目列表加入题目');
    wrapper.unmount();
  });

  it('initializes available certifications and maintains the question basket', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;
    expect(vm.certificationOptions).toEqual([{ id: 'c1', name: '系统架构设计师' }, { id: 'c2', name: '软件设计师' }]);
    expect(vm.availableSyllabuses).toHaveLength(1);
    expect(vm.form.syllabus).toBe('s1');

    vm.addQuestion(choice);
    await flushPromises();
    vm.addQuestion(choice);
    expect(vm.selectedItems).toHaveLength(1);
    expect(vm.selectedItems[0].reportScore).toBe(1);
    expect(vm.selectedItems[0].knowledgePointId).toBe('kp-1');
    vm.addQuestion(essay);
    await flushPromises();
    expect(vm.totalScore).toBe(11);
    expect(vm.subjectCount).toBe(1);
    expect(vm.coveredKnowledgeCount).toBe(1);
    expect(vm.difficultyCounts).toEqual({ easy: 1, medium: 0, hard: 1 });
    expect(vm.typeCounts).toEqual({ CHOICE: 1, CASE: 0, ESSAY: 1 });
    vm.dragIndex = 1;
    vm.dropAt(0);
    expect(vm.selectedItems.map((item: any) => item.question.revisionId)).toEqual(['r-2', 'r-1']);
    vm.sortItems();
    expect(vm.selectedItems.map((item: any) => item.question.questionType)).toEqual(['CHOICE', 'ESSAY']);
    expect(messages.success).toHaveBeenCalledWith('已按选择题、案例题、论文题及题目 ID 排序');
  });

  it('prevents an empty save and creates a trimmed collection draft', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;
    await vm.saveDraft();
    expect(messages.warning).toHaveBeenCalledWith('请先填写题集名称');
    expect(collectionApi.createCollection).not.toHaveBeenCalled();

    vm.form.name = '  架构专项练习  ';
    vm.form.type = 'PRACTICE';
    vm.form.certification = 'c1';
    vm.form.syllabus = 's1';
    vm.form.durationMinutes = 60;
    vm.selectedItems = [{ question: choice, reportScore: 2, knowledgePointId: 'kp-1' }];
    await vm.saveDraft();
    expect(collectionApi.createCollection).toHaveBeenCalledWith({
      collectionName: '架构专项练习', collectionType: 'PRACTICE', certificationId: 'c1', syllabusVersionId: 's1', durationMinutes: 60,
      items: [{ itemOrder: 1, questionRevisionId: 'r-1', reportScore: 2 }]
    });
    expect(router.push).toHaveBeenCalledWith('/content/collections');
    expect(router.replace).not.toHaveBeenCalled();
    expect(vm.dirty).toBe(false);
  });

  it('clears the basket only after confirming a certification switch', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;
    vm.selectedItems = [{ question: choice, reportScore: 1 }];
    vm.currentCertification = 'c1';
    messages.confirm.mockRejectedValueOnce('cancel');
    vm.form.certification = 'c2';
    await vm.handleCertificationChange('c2');
    expect(vm.selectedItems).toHaveLength(1);
    expect(vm.form.certification).toBe('c1');

    messages.confirm.mockResolvedValueOnce(undefined);
    vm.form.certification = 'c2';
    await vm.handleCertificationChange('c2');
    await vm.handleCertificationChange('c2');
    expect(vm.selectedItems).toEqual([]);
    expect(vm.currentCertification).toBe('c2');
    expect(vm.form.syllabus).toBe('s2');
  });

  it('loads a preview and formats answer and sorted image data', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;
    await vm.preview(choice);
    expect(questionApi.getQuestionPreview).toHaveBeenCalledWith('q-1', 'r-1');
    expect(vm.previewVisible).toBe(true);
    expect(vm.previewItem.questionCode).toBe('Q-001');
    expect(vm.previewImages.map((image: any) => image.sortOrder)).toEqual([1, 2]);
    expect(vm.previewImageUrls).toEqual(['/oss-proxy/minio:9000/certmuse/a.png?signature=abc', 'b.png']);
    expect(vm.previewStem).toBe('预览题干');
    expect(vm.formatAnswer({ value: ['A', 'B'] })).toBe('A、B');
    expect(vm.formatAnswer({ value: '' })).toBe('尚未填写答案');
    expect(vm.formatAnswer(undefined)).toBe('尚未填写答案');
    expect(vm.typeLabel('CASE')).toBe('案例题');
    expect(vm.difficultyLabel(null)).toBe('未设置');
  });

  it('loads an existing revision and saves it without creating a new route', async () => {
    route.query = { id: 'collection-1', revisionId: 'revision-9' };
    collectionApi.getCollectionRevision.mockResolvedValue({ data: {
      collectionCode: 'COL-9', collectionName: '旧题集', collectionType: 'SIMULATION',
      certificationId: 'c1', syllabusVersionId: 's1', syllabusVersionName: '2026', durationMinutes: 90,
      rowVersion: '7', updatedTime: '2026-08-09', items: [{
        questionId: 'q-1', questionRevisionId: 'r-1', questionCode: 'Q-001', stem: '旧题干',
        examSubjectId: 'subject-1', examSubjectName: '综合知识', questionType: 'CHOICE', difficulty: 'easy',
        questionStatus: 'rejected', reportScore: 2, knowledgePointId: 'kp-1'
      }]
    } });
    collectionApi.saveCollectionRevision.mockResolvedValue({ data: { collectionCode: 'COL-9', rowVersion: '8' } });

    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;

    expect(collectionApi.getCollectionRevision).toHaveBeenCalledWith('revision-9');
    expect(vm.isEditingDraft).toBe(true);
    expect(vm.form.name).toBe('旧题集');
    expect(vm.generatedCode).toBe('COL-9');
    expect(vm.selectedItems[0].question.status).toBe('rejected');

    vm.form.name = '  修改后的题集  ';
    await vm.saveDraft();

    expect(collectionApi.saveCollectionRevision).toHaveBeenCalledWith('revision-9', expect.objectContaining({
      collectionName: '修改后的题集', rowVersion: '7', items: [{ itemOrder: 1, questionRevisionId: 'r-1', reportScore: 2 }]
    }));
    expect(router.replace).not.toHaveBeenCalled();
    expect(router.push).toHaveBeenCalledWith('/content/collections');
    expect(vm.rowVersion).toBe('8');
  });

  it('uses a secondary binding as fallback and tolerates detail lookup failures', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;

    questionApi.getQuestionDetail.mockResolvedValueOnce({ data: {
      knowledgeBindings: [{ knowledgePointId: 'kp-secondary', relationRole: 'secondary' }]
    } });
    vm.addQuestion({ ...choice, revisionId: 'r-secondary' });
    await flushPromises();
    expect(vm.selectedItems.at(-1).knowledgePointId).toBe('kp-secondary');

    questionApi.getQuestionDetail.mockRejectedValueOnce(new Error('detail unavailable'));
    vm.addQuestion({ ...essay, revisionId: 'r-failed' }, 'kp-from-directory');
    await flushPromises();
    expect(vm.selectedItems.at(-1).knowledgePointId).toBe('kp-from-directory');
    vm.dropAt(0);
    expect(vm.selectedItems).toHaveLength(2);
  });

  it('honours the dirty-page confirmation before returning to the collection list', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;
    vm.dirty = true;
    messages.confirm.mockRejectedValueOnce('cancel');

    await vm.goBack();

    expect(router.push).not.toHaveBeenCalled();
    expect(vm.dirty).toBe(true);

    messages.confirm.mockResolvedValueOnce(undefined);
    await vm.goBack();
    expect(router.push).toHaveBeenCalledWith('/content/collections');
    expect(vm.dirty).toBe(false);
  });

  it('removes a selected question and expands the floating basket safely', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const vm = wrapper.vm as any;
    vm.selectedItems = [
      { question: choice, reportScore: 1, knowledgePointId: 'kp-1' },
      { question: essay, reportScore: 10, knowledgePointId: 'kp-1' }
    ];
    vm.remove(0);
    expect(vm.selectedItems.map((item: any) => item.question.revisionId)).toEqual(['r-2']);
    vm.basketCollapsed = true;
    await vm.expandBasket();
    expect(vm.basketCollapsed).toBe(false);
    vm.clampFloatingBasketToViewport();
    wrapper.unmount();
  });

  it('shows the first-diagnostic quota and renders a non-choice preview from picker actions', async () => {
    questionApi.getQuestionPreview.mockResolvedValueOnce({ data: {
      stem: '论文预览题干', answer: { value: '' }, analysis: '', options: [], images: [],
      reviewOpinion: '请补充取舍理由'
    } });
    const wrapper = mountPage();
    await flushPromises();

    const typeSelect = wrapper.findAllComponents({ name: 'ElSelect' })[0];
    typeSelect.vm.$emit('update:modelValue', 'FIRST_DIAGNOSTIC');
    await wrapper.get('.picker-add-choice').trigger('click');
    await wrapper.get('.picker-add-essay').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('首次诊断还需 48 题');

    const rows = wrapper.findAll('.selected-list article');
    await rows[1].trigger('dragstart');
    await rows[0].trigger('drop');
    await wrapper.vm.$nextTick();
    expect(wrapper.findAll('.selected-list article')[0].text()).toContain('论文题干');

    await wrapper.get('.picker-preview-essay').trigger('click');
    await flushPromises();
    expect(document.body.textContent).toContain('论文预览题干');
    expect(document.body.textContent).toContain('驳回原因');
    expect(document.body.textContent).toContain('请补充取舍理由');
    expect(document.body.textContent).toContain('尚未填写解析');
    wrapper.unmount();
  });

  it('persists a user-dragged basket position and requires a second click to expand it', async () => {
    const wrapper = mountPage();
    await flushPromises();
    await wrapper.get('.picker-add-choice').trigger('click');
    await flushPromises();

    const panel = wrapper.get('.basket-panel').element as HTMLElement;
    vi.spyOn(panel, 'getBoundingClientRect').mockReturnValue({
      x: 200, y: 100, left: 200, top: 100, right: 520, bottom: 300, width: 320, height: 200, toJSON: () => ({})
    });
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => {
      callback(0);
      return 1;
    });
    try {
      wrapper.get('.basket-drag-handle').element.dispatchEvent(
        new MouseEvent('pointerdown', { bubbles: true, button: 2, clientX: 220, clientY: 120 })
      );
      expect(panel.classList.contains('is-dragging')).toBe(false);
      wrapper.get('.basket-drag-handle').element.dispatchEvent(
        new MouseEvent('pointerdown', { bubbles: true, button: 0, clientX: 220, clientY: 120 })
      );
      document.dispatchEvent(new MouseEvent('pointermove', { clientX: 220, clientY: 320 }));
      await wrapper.vm.$nextTick();
      expect(panel.classList.contains('is-dragging')).toBe(true);
      expect(panel.style.transform).toContain('translate3d');

      document.dispatchEvent(new MouseEvent('pointerup', { clientX: 420, clientY: 320 }));
      await wrapper.vm.$nextTick();
      expect(panel.classList.contains('is-dragging')).toBe(false);
      expect(panel.getAttribute('style')).toContain('left:');

      const collapse = wrapper.findAll('button').find(button => button.text().trim() === '收起');
      await collapse!.trigger('click');
      await wrapper.vm.$nextTick();
      expect(wrapper.find('.basket-trigger').exists()).toBe(true);

      await wrapper.get('.basket-trigger').trigger('click');
      await wrapper.vm.$nextTick();
      expect(wrapper.find('.basket-trigger').exists()).toBe(true);
      await wrapper.get('.basket-trigger').trigger('click');
      await wrapper.vm.$nextTick();
      expect(wrapper.find('.basket-panel').exists()).toBe(true);
    } finally {
      vi.unstubAllGlobals();
      wrapper.unmount();
    }
  });

  it('blocks browser navigation only while there are unsaved collection edits', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const leavePage = beforeRouteLeave.mock.calls.at(-1)?.[0] as () => Promise<boolean>;
    const vm = wrapper.vm as any;

    expect(await leavePage()).toBe(true);

    vm.dirty = true;
    messages.confirm.mockRejectedValueOnce('cancel');
    expect(await leavePage()).toBe(false);
    expect(messages.confirm).toHaveBeenCalledWith('当前有未保存修改，确认离开吗？', '离开页面');

    messages.confirm.mockResolvedValueOnce(undefined);
    expect(await leavePage()).toBe(true);
    wrapper.unmount();
  });

  it('keeps the entered collection visible when the save request is rejected', async () => {
    collectionApi.createCollection.mockRejectedValueOnce(new Error('草稿版本已过期'));
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.get('input[placeholder="请输入题集名称"]').setValue('待重试的架构练习');
    await wrapper.get('.picker-add-choice').trigger('click');
    await flushPromises();
    const save = wrapper.findAll('button').find(button => button.text().trim() === '保存草稿');
    await save!.trigger('click');
    await flushPromises();

    expect((wrapper.get('input[placeholder="请输入题集名称"]').element as HTMLInputElement).value).toBe('待重试的架构练习');
    expect(wrapper.text()).toContain('选择题干');
    expect(messages.error).toHaveBeenCalledWith('草稿版本已过期');
    expect(router.replace).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('lets an editor adjust, remove, and clear the rendered basket through its controls', async () => {
    const wrapper = mountPage();
    await flushPromises();
    await wrapper.get('.picker-add-choice').trigger('click');
    await wrapper.get('.picker-add-essay').trigger('click');
    await flushPromises();

    const scoreInput = wrapper
      .findAllComponents({ name: 'ElInputNumber' })
      .find(component => component.props('min') === 0.5)!;
    scoreInput.vm.$emit('update:modelValue', 3.5);
    await wrapper.vm.$nextTick();
    expect(wrapper.text()).toContain('13.5');

    await wrapper.findAll('.selected-list article')[0].find('.item-actions button').trigger('click');
    expect(wrapper.findAll('.selected-list article')).toHaveLength(1);
    const clear = wrapper.findAll('button').find(button => button.text().trim() === '清空');
    await clear!.trigger('click');
    expect(wrapper.text()).toContain('从题目列表加入题目');
    wrapper.unmount();
  });

  it('changes an empty collection qualification without prompting and reloads its visible syllabus scope', async () => {
    const wrapper = mountPage();
    await flushPromises();
    const qualification = wrapper
      .findAllComponents({ name: 'ElSelect' })
      .find(component => component.props('placeholder') === '请选择考试资格')!;

    qualification.vm.$emit('update:modelValue', 'c2');
    qualification.vm.$emit('change', 'c2');
    await flushPromises();

    expect(messages.confirm).not.toHaveBeenCalled();
    expect((wrapper.vm as any).form.syllabus).toBe('s2');
    expect(catalogApi.getKnowledgeTree).toHaveBeenCalledWith('s2');
    wrapper.unmount();
  });

  it('renders a choice preview with answer and option fallbacks supplied by the API', async () => {
    questionApi.getQuestionPreview.mockResolvedValueOnce({ data: {
      stem: '无图选择题', answer: { value: [] }, analysis: '',
      options: [{ label: 'A', content: '架构权衡' }]
    } });
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.get('.picker-preview').trigger('click');
    await flushPromises();
    expect(document.body.textContent).toContain('无图选择题');
    expect(document.body.textContent).toContain('架构权衡');
    expect(document.body.textContent).toContain('尚未填写答案');
    wrapper.unmount();
  });

  it('keeps the page recoverable when the initial syllabus request has no usable error detail', async () => {
    catalogApi.listSyllabusVersions.mockRejectedValueOnce({});
    const wrapper = mountPage();
    await flushPromises();

    expect(messages.error).toHaveBeenCalledWith('题集编辑页加载失败');
    expect(wrapper.text()).toContain('新建题集');
    wrapper.unmount();
  });
});
