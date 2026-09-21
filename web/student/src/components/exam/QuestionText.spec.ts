import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import QuestionText from './QuestionText.vue';

describe('QuestionText', () => {
  it('renders inline and block LaTeX while preserving surrounding text', () => {
    const wrapper = mount(QuestionText, {
      props: { content: '计算 $x^2$：\\n$$\\frac{a}{b}$$' }
    });

    expect(wrapper.text()).toContain('计算');
    expect(wrapper.findAll('.katex')).toHaveLength(2);
    expect(wrapper.find('.katex-display').exists()).toBe(true);
  });

  it('escapes non-math HTML and keeps malformed formulas as text', () => {
    const wrapper = mount(QuestionText, {
      props: { content: '题干 <img src=x onerror=alert(1)> $x^2' }
    });

    expect(wrapper.find('img').exists()).toBe(false);
    expect(wrapper.text()).toContain('<img src=x onerror=alert(1)> $x^2');
  });
});
