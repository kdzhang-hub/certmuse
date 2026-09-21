import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import InlineQuestionEditDialog from './InlineQuestionEditDialog.vue';
import type { QuestionDetailVO, QuestionListVO } from '@/api/certmuse/question/types';

const api = vi.hoisted(() => ({ getQuestionDetail: vi.fn(), saveQuestionDraft: vi.fn() }));
const message = vi.hoisted(() => ({ warning: vi.fn(), success: vi.fn() }));

vi.mock('@/api/certmuse/question', () => api);
vi.mock('element-plus', () => ({ ElMessage: message }));

const global = {
  directives: { loading: () => undefined },
  stubs: {
    'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
    'el-alert': { props: ['title', 'description'], template: '<div>{{ title }} {{ description }}</div>' },
    'el-form': { template: '<form><slot /></form>' },
    'el-form-item': { template: '<div><slot /></div>' },
    'el-input': { template: '<input />' },
    'el-input-number': { template: '<input />' },
    'el-select': { template: '<select><slot /></select>' },
    'el-option': { template: '<option><slot /></option>' },
    'el-radio': { template: '<label><slot /></label>' },
    'el-button': { props: ['disabled', 'loading'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' }
  }
};

const listQuestion: QuestionListVO = {
  questionId: 'q-1', revisionId: 'r-1', revisionNo: 1, questionCode: 'Q-001',
  syllabusVersionId: 's-1', syllabusVersionName: '2026', stemSummary: '题干',
  examSubjectId: 'subject-1', examSubjectName: '综合知识', questionType: 'CHOICE',
  difficulty: 'easy', status: 'rejected', hasReviewOpinion: true, updatedTime: '2026-08-01T00:00:00Z'
};

const choiceDetail: QuestionDetailVO = {
  questionId: 'q-1', revisionId: 'r-1', revisionNo: 1, rowVersion: '3', questionCode: 'Q-001',
  syllabusVersionId: 's-1', syllabusVersionName: '2026', examSubjectId: 'subject-1', examSubjectName: '综合知识',
  examSubjectEditable: false, examSubjectOptions: [], questionType: 'CHOICE', difficulty: 'easy', estimatedSeconds: 60,
  stem: '原题干', images: [], options: [{ label: 'A', content: '甲' }, { label: 'B', content: '乙' }],
  answer: { schemaVersion: '1.0', answerType: 'option_keys', selectionMode: 'single', value: ['B'] },
  analysis: '原解析', commonMistakes: '原错误',
  knowledgeBindings: [{ knowledgePointId: 'k-1', knowledgePointLabel: '知识点一', relationRole: 'primary', sortOrder: 0 }],
  status: 'rejected', reviewOpinion: '依据不足', editableMode: 'update_working_revision', updatedTime: '2026-08-01T00:00:00Z'
};

const subjectiveDetail: QuestionDetailVO = {
  ...choiceDetail,
  questionType: 'ESSAY',
  answer: { schemaVersion: '1.0', answerType: 'reference_text', value: '参考答案' },
  options: [],
  reviewOpinion: null
};

describe('InlineQuestionEditDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.getQuestionDetail.mockResolvedValue({ data: choiceDetail });
    api.saveQuestionDraft.mockResolvedValue({ data: { revisionId: 'r-2' } });
  });

  it('loads a choice question, maintains options and saves the normalized payload', async () => {
    const wrapper = mount(InlineQuestionEditDialog, {
      props: { modelValue: false, question: listQuestion },
      global
    });
    await wrapper.setProps({ modelValue: true });
    await flushPromises();
    const state = wrapper.vm as any;

    expect(api.getQuestionDetail).toHaveBeenCalledWith('q-1', 'r-1');
    expect(state.form.correctOptionKey).toBe('B');
    state.addOption();
    expect(state.form.options).toHaveLength(3);
    state.removeOption(2);
    state.form.stem = '  新题干  ';
    state.form.correctOptionKey = 'B';
    await state.save();

    expect(api.saveQuestionDraft).toHaveBeenCalledWith('q-1', expect.objectContaining({
      baseRevisionId: 'r-1', rowVersion: '3', stem: '新题干',
      answer: expect.objectContaining({ answerType: 'option_keys', value: ['B'] }),
      options: [
        { label: 'A', content: '甲', sortOrder: 1 },
        { label: 'B', content: '乙', sortOrder: 2 }
      ]
    }));
    expect(message.success).toHaveBeenCalledWith('题目已保存');
    expect(wrapper.emitted('saved')).toHaveLength(1);
    expect(wrapper.emitted('update:modelValue')).toContainEqual([false]);
  });

  it('validates choice and subjective forms before sending', async () => {
    const choice = mount(InlineQuestionEditDialog, {
      props: { modelValue: false, question: listQuestion },
      global
    });
    await choice.setProps({ modelValue: true });
    await flushPromises();
    const choiceState = choice.vm as any;
    choiceState.form.stem = ' ';
    await choiceState.save();
    expect(message.warning).toHaveBeenCalledWith('题干不能为空');
    choiceState.form.stem = '有效题干';
    choiceState.form.correctOptionKey = '';
    await choiceState.save();
    expect(message.warning).toHaveBeenCalledWith('请选择正确答案');
    expect(api.saveQuestionDraft).not.toHaveBeenCalled();

    api.getQuestionDetail.mockResolvedValue({ data: subjectiveDetail });
    const subjective = mount(InlineQuestionEditDialog, {
      props: { modelValue: false, question: { ...listQuestion, questionType: 'ESSAY' } },
      global
    });
    await subjective.setProps({ modelValue: true });
    await flushPromises();
    const subjectiveState = subjective.vm as any;
    subjectiveState.form.referenceAnswer = '';
    await subjectiveState.save();
    expect(message.warning).toHaveBeenCalledWith('请填写参考答案');
    subjectiveState.form.referenceAnswer = '  新参考答案  ';
    await subjectiveState.save();
    expect(api.saveQuestionDraft).toHaveBeenCalledWith('q-1', expect.objectContaining({
      answer: expect.objectContaining({ answerType: 'reference_text', value: '新参考答案' }),
      options: []
    }));
    expect(api.saveQuestionDraft.mock.calls.at(-1)?.[1]).not.toHaveProperty('scoringPoints');
  });

  it('does not load when opened without a question', async () => {
    const wrapper = mount(InlineQuestionEditDialog, { props: { modelValue: true }, global });
    await flushPromises();
    expect(api.getQuestionDetail).not.toHaveBeenCalled();
    await (wrapper.vm as any).save();
    expect(api.saveQuestionDraft).not.toHaveBeenCalled();
  });

  it('lets an editor add and remove choice options through rendered controls before closing', async () => {
    const wrapper = mount(InlineQuestionEditDialog, {
      props: { modelValue: false, question: listQuestion },
      global
    });
    await wrapper.setProps({ modelValue: true });
    await flushPromises();

    expect(wrapper.text()).toContain('原驳回意见');
    expect(wrapper.text()).toContain('依据不足');
    expect(wrapper.findAll('.option-row')).toHaveLength(2);
    await wrapper.findAll('button').find(button => button.text() === '新增选项')!.trigger('click');
    expect(wrapper.findAll('.option-row')).toHaveLength(3);
    await wrapper.findAll('.option-row')[2].find('button').trigger('click');
    expect(wrapper.findAll('.option-row')).toHaveLength(2);

    await wrapper.findAll('button').find(button => button.text() === '取消')!.trigger('click');
    expect(wrapper.emitted('update:modelValue')).toContainEqual([false]);
  });

  it('does not render subjective scoring controls', async () => {
    api.getQuestionDetail.mockResolvedValueOnce({ data: subjectiveDetail });
    const wrapper = mount(InlineQuestionEditDialog, {
      props: { modelValue: false, question: { ...listQuestion, questionType: 'ESSAY' } },
      global
    });
    await wrapper.setProps({ modelValue: true });
    await flushPromises();

    expect(wrapper.findAll('.score-row')).toHaveLength(0);
    expect(wrapper.text()).not.toContain('评分点');
  });
});
