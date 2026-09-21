import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import ExamAnswerCard from './ExamAnswerCard.vue';

describe('ExamAnswerCard', () => {
  it('shows distinct correct and incorrect states while preserving the current state', () => {
    const wrapper = mount(ExamAnswerCard, {
      props: {
        totalCount: 4,
        answeredCount: 2,
        currentQuestionOrder: 1,
        navigation: [1, 2, 3, 4].map(questionOrder => ({ questionOrder })),
        answered: { 2: true, 3: true },
        answerResults: { 2: 'correct', 3: 'incorrect' },
        showAnswerResults: true,
        disabled: false,
        finishLabel: '结束练习'
      },
      global: {
        stubs: {
          ElCard: { template: '<section><slot name="header" /><slot /></section>' },
          ElDivider: true,
          ElButton: { template: '<button><slot /></button>' }
        }
      }
    });

    const items = wrapper.findAll('.exam-answer-card__item');
    expect(items[0].classes()).toContain('is-current');
    expect(items[1].classes()).toContain('is-correct');
    expect(items[2].classes()).toContain('is-incorrect');
    expect(items[3].classes()).not.toContain('is-answered');
    expect(wrapper.text()).toContain('正确');
    expect(wrapper.text()).toContain('错误');
  });
});
