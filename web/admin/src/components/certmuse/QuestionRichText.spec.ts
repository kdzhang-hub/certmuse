import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import QuestionRichText from './QuestionRichText.vue';

describe('QuestionRichText', () => {
  it('renders safe tables and LaTeX without exposing HTML source', () => {
    const wrapper = mount(QuestionRichText, {
      props: { content: '<table><tbody><tr><th>数量</th><td>$x^2$</td></tr></tbody></table><script>alert(1)</script>' }
    });

    expect(wrapper.find('table').exists()).toBe(true);
    expect(wrapper.get('th').text()).toBe('数量');
    expect(wrapper.find('.katex').exists()).toBe(true);
    expect(wrapper.find('script').exists()).toBe(false);
  });

  it('removes unsafe attributes while retaining the text content', () => {
    const wrapper = mount(QuestionRichText, {
      props: { content: '<p onclick="alert(1)">题干 <img src="javascript:alert(1)" alt="示例"></p>' }
    });

    expect(wrapper.text()).toContain('题干');
    expect(wrapper.find('p').attributes('onclick')).toBeUndefined();
    expect(wrapper.find('img').attributes('src')).toBeUndefined();
  });
});
