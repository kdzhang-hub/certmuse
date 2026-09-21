import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import EmptyBusinessPage from './EmptyBusinessPage.vue';

const global = {
  stubs: {
    'el-card': { template: '<section><slot /></section>' },
    'el-empty': { template: '<div><slot name="description" /><slot /></div>' },
    'el-tag': { template: '<span><slot /></span>' }
  }
};

describe('EmptyBusinessPage', () => {
  it('renders valid title, description and optional action content', () => {
    const wrapper = mount(EmptyBusinessPage, {
      props: { title: '题目管理', description: '管理题目' },
      slots: { action: '<button>立即配置</button>' },
      global
    });

    expect(wrapper.get('h2').text()).toBe('题目管理');
    expect(wrapper.get('p').text()).toBe('管理题目');
    expect(wrapper.get('button').text()).toBe('立即配置');
    expect(wrapper.text()).toContain('页面已建立 · 功能待接入');
  });

  it('handles empty edge-case content without rendering an action', () => {
    const wrapper = mount(EmptyBusinessPage, { props: { title: '', description: '' }, global });

    expect(wrapper.get('h2').text()).toBe('');
    expect(wrapper.get('p').text()).toBe('');
    expect(wrapper.find('button').exists()).toBe(false);
  });
});
