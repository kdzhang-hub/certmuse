import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import QuestionPreviewDialog from './QuestionPreviewDialog.vue';
import type { QuestionPreviewVO } from '@/api/certmuse/question/types';

const global = {
  directives: { loading: () => undefined },
  stubs: {
    'el-dialog': {
      props: ['modelValue'],
      template: '<div class="dialog"><header><slot name="header" /></header><slot /></div>'
    },
    'el-button': {
      props: ['disabled', 'loading'],
      emits: ['click'],
      template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
    },
    'el-alert': {
      props: ['title', 'description'],
      template: '<div class="alert">{{ title }} {{ description }}</div>'
    },
    'el-image': {
      props: ['src', 'alt'],
      template: '<img :src="src" :alt="alt" />'
    },
    'el-empty': { props: ['description'], template: '<div class="empty">{{ description }}</div>' }
  }
};

const question: QuestionPreviewVO = {
  questionId: 'q-1',
  revisionId: 'r-1',
  stem: '题干 [图片 2] {{question-image: 2}}',
  images: [
    { imageId: 'img-2', sortOrder: 2, url: 'https://example.com/two.png', alt: '第二张' },
    { imageId: 'img-1', sortOrder: 1, url: 'http://minio:9000/cert/question.png?x=1', alt: '' }
  ],
  options: [
    { label: 'A', content: '选项一' },
    { label: 'B', content: '选项二' }
  ],
  answer: { answerType: 'option_keys', value: ['A', 'B'] },
  analysis: null,
  reviewOpinion: '请补充依据'
};

describe('QuestionPreviewDialog', () => {
  it('renders the full preview, normalizes image URLs and emits review actions', async () => {
    const wrapper = mount(QuestionPreviewDialog, {
      props: { modelValue: true, question, reviewable: true, processing: false },
      global
    });

    expect(wrapper.text()).toContain('题目预览');
    expect(wrapper.text()).toContain('题干');
    expect(wrapper.text()).toContain('题干');
    expect(wrapper.text()).toContain('A.选项一');
    expect(wrapper.text()).toContain('A、B');
    expect(wrapper.text()).toContain('请补充依据');
    expect(wrapper.text()).toContain('尚未填写解析');
    expect(wrapper.text()).not.toContain('评分点说明');
    expect(wrapper.text()).toContain('图片 1');
    expect(wrapper.get('img').attributes('src')).toBe('/oss-proxy/minio:9000/cert/question.png?x=1');
    expect(wrapper.findAll('img')).toHaveLength(2);

    await wrapper.findAll('button')[0].trigger('click');
    await wrapper.findAll('button')[1].trigger('click');
    expect(wrapper.emitted('approve')).toHaveLength(1);
    expect(wrapper.emitted('reject')).toHaveLength(1);
  });

  it('shows the empty state and handles scalar answers without images', () => {
    const empty = mount(QuestionPreviewDialog, { props: { modelValue: true }, global });
    expect(empty.text()).toContain('暂无题目详情');

    const scalarQuestion: QuestionPreviewVO = {
      ...question,
      images: [],
      options: [],
      reviewOpinion: null,
      answer: { answerType: 'reference_text', value: '参考答案' },
      analysis: '已有解析'
    };
    const wrapper = mount(QuestionPreviewDialog, {
      props: { modelValue: true, question: scalarQuestion },
      global
    });
    expect(wrapper.text()).toContain('参考答案');
    expect(wrapper.text()).toContain('已有解析');
    expect(wrapper.find('.alert').exists()).toBe(false);
    expect(wrapper.findAll('img')).toHaveLength(0);
  });
});
