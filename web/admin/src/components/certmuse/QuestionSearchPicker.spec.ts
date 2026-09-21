import { flushPromises, mount } from '@vue/test-utils';
import { defineComponent, h, nextTick } from 'vue';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import QuestionSearchPicker from './QuestionSearchPicker.vue';
import type { QuestionListVO } from '@/api/certmuse/question/types';

const listQuestions = vi.hoisted(() => vi.fn());
vi.mock('@/api/certmuse/question', () => ({ listQuestions }));

const tableClearSelection = vi.fn();
const global = {
  directives: { hasPermi: () => undefined, loading: () => undefined },
  stubs: {
    KnowledgeDirectoryPanel: {
      emits: ['select', 'clear', 'update:collapsed'],
      template: '<div class="directory-stub"><button class="select-subject" @click="$emit(\'select\', { id: \'subject-1\', label: \'综合知识\' })">科目</button><button class="select-leaf" @click="$emit(\'select\', { id: \'knowledge-1\', label: \'软件架构\' })">知识点</button><button class="clear" @click="$emit(\'clear\')">清除</button><button class="collapse" @click="$emit(\'update:collapsed\', true)">折叠</button></div>'
    },
    InlineQuestionEditDialog: {
      emits: ['update:modelValue', 'saved'],
      template: '<div class="edit-stub"><button class="save-edit" @click="$emit(\'saved\')">保存</button></div>'
    },
    'el-form': { template: '<form><slot /></form>' },
    'el-form-item': { template: '<div><slot /></div>' },
    'el-input': { props: ['modelValue'], emits: ['update:modelValue'], template: '<input class="keyword-input" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' },
    'el-select': { props: ['modelValue'], emits: ['update:modelValue'], template: '<select :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><slot /></select>' },
    'el-option': { template: '<option><slot /></option>' },
    'el-button': { props: ['disabled'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
    'el-table': { methods: { clearSelection: tableClearSelection }, template: '<div class="table-stub"><slot /></div>' },
    'el-table-column': defineComponent({
      setup(_, { slots }) {
        return () => h('div', slots.default?.({ row: rows[0] }));
      }
    }),
    'el-tag': { template: '<span><slot /></span>' },
    'el-tooltip': { template: '<span><slot /></span>' },
    'el-pagination': {
      emits: ['update:currentPage', 'update:pageSize'],
      template: '<div class="pagination-stub"><button class="next-page" @click="$emit(\'update:currentPage\', 2)">下一页</button><button class="page-size" @click="$emit(\'update:pageSize\', 20)">每页20</button></div>'
    }
  }
};

const rows: QuestionListVO[] = [
  {
    questionId: 'q-1', revisionId: 'r-1', revisionNo: 1, questionCode: 'Q-001', syllabusVersionId: 's-1',
    syllabusVersionName: '2026', stemSummary: '题干一', examSubjectId: 'subject-1', examSubjectName: '综合知识',
    questionType: 'CHOICE', difficulty: 'easy', status: 'draft', updatedTime: '2026-08-01T00:00:00Z'
  },
  {
    questionId: 'q-2', revisionId: 'r-2', revisionNo: 1, questionCode: 'Q-002', syllabusVersionId: 's-1',
    syllabusVersionName: '2026', stemSummary: '题干二', examSubjectId: 'subject-1', examSubjectName: '综合知识',
    questionType: 'ESSAY', difficulty: null, status: 'published', updatedTime: '2026-08-01T00:00:00Z'
  }
];

function mountPicker() {
  return mount(QuestionSearchPicker, {
    props: {
      syllabusVersionId: 's-1', syllabusLabel: '系统架构设计师 · 2026',
      knowledgeDirectory: [{ id: 'subject-1', label: '综合知识', children: [{ id: 'knowledge-1', label: '软件架构' }] }],
      selectedRevisionIds: ['r-2']
    },
    global
  });
}

describe('QuestionSearchPicker', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    listQuestions.mockResolvedValue({ data: { rows, total: rows.length } });
  });

  it('loads questions, maps labels and prevents already selected revisions', async () => {
    const wrapper = mountPicker();
    await flushPromises();
    const state = wrapper.vm as any;

    expect(listQuestions).toHaveBeenCalledWith(expect.objectContaining({
      syllabusVersionId: 's-1', includeDescendants: true, pageNum: 1, pageSize: 10,
      keyword: undefined, examSubjectId: undefined, knowledgePointId: undefined
    }));
    expect(state.isSelectable(rows[0])).toBe(true);
    expect(state.isSelectable(rows[1])).toBe(false);
    expect(state.typeLabel('CHOICE')).toBe('选择题');
    expect(state.typeLabel('CASE')).toBe('案例题');
    expect(state.difficultyLabel(null)).toBe('未设置');
    expect(state.statusLabel('rejected')).toBe('已驳回');
    expect(state.statusTag('published')).toBe('success');
    expect(state.statusTag('rejected')).toBe('danger');
    expect(state.statusTag('pending_review')).toBe('warning');
    expect(state.statusTag('draft')).toBe('info');
  });

  it('changes scope, emits selected questions and resets filters', async () => {
    const wrapper = mountPicker();
    await flushPromises();
    const state = wrapper.vm as any;

    await wrapper.find('.select-subject').trigger('click');
    await flushPromises();
    expect(listQuestions).toHaveBeenLastCalledWith(expect.objectContaining({ examSubjectId: 'subject-1', knowledgePointId: undefined }));
    await wrapper.find('.select-leaf').trigger('click');
    await flushPromises();
    expect(listQuestions).toHaveBeenLastCalledWith(expect.objectContaining({ examSubjectId: undefined, knowledgePointId: 'knowledge-1' }));

    state.handleSelectionChange([rows[0]]);
    state.addCheckedQuestions();
    expect(wrapper.emitted('select')).toContainEqual([rows[0], 'knowledge-1']);
    expect(tableClearSelection).toHaveBeenCalled();

    state.filters.keyword = '  架构 ';
    state.filters.questionType = 'CHOICE';
    state.filters.difficulty = 'easy';
    state.resetFilters();
    await nextTick();
    expect(state.filters).toEqual({ keyword: '', questionType: '', difficulty: '' });
    await wrapper.find('.clear').trigger('click');
    expect(state.selectedKnowledgeLabel).toBe('');
  });

  it('clears rows when no syllabus is selected and refreshes after an edit', async () => {
    const wrapper = mountPicker();
    await flushPromises();
    const state = wrapper.vm as any;
    await wrapper.setProps({ syllabusVersionId: '' });
    await flushPromises();
    expect(state.rows).toEqual([]);
    expect(state.total).toBe(0);

    await wrapper.setProps({ syllabusVersionId: 's-1' });
    await flushPromises();
    state.handleQuestionSaved();
    await flushPromises();
    expect(listQuestions).toHaveBeenCalled();
  });

  it('opens the inline editor only for editable question states', async () => {
    const wrapper = mountPicker();
    await flushPromises();
    const state = wrapper.vm as any;

    expect(state.isEditable('draft')).toBe(true);
    expect(state.isEditable('rejected')).toBe(true);
    expect(state.isEditable('pending_review')).toBe(false);
    expect(state.isEditable('published')).toBe(false);
    state.openEdit(rows[0]);
    expect(state.editingQuestion).toEqual(rows[0]);
    expect(state.editVisible).toBe(true);
    state.handleQuestionSaved();
    await flushPromises();
    expect(listQuestions).toHaveBeenCalled();
    wrapper.unmount();
  });

  it('reloads through rendered filters, directory collapse, pagination and editor-save events', async () => {
    const wrapper = mountPicker();
    await flushPromises();

    await wrapper.find('.keyword-input').setValue('架构');
    const selects = wrapper.findAll('select');
    await selects[0].setValue('CHOICE');
    await selects[1].setValue('hard');
    await wrapper.find('.collapse').trigger('click');
    await wrapper.find('.next-page').trigger('click');
    await wrapper.find('.page-size').trigger('click');
    await wrapper.find('.save-edit').trigger('click');
    await flushPromises();

    expect(listQuestions).toHaveBeenCalledWith(expect.objectContaining({
      keyword: '架构', questionType: 'CHOICE', difficulty: 'hard', pageSize: 20
    }));
    expect(wrapper.find('.selection-layout').classes()).toContain('is-directory-collapsed');
    wrapper.unmount();
  });
});
