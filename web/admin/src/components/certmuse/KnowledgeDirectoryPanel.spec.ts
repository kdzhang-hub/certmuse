import { mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import KnowledgeDirectoryPanel from './KnowledgeDirectoryPanel.vue';

const treeFilter = vi.fn();
const treeSetCurrentKey = vi.fn();

const global = {
  stubs: {
    'el-tag': { template: '<span class="tag"><slot /></span>' },
    'el-input': {
      props: ['modelValue', 'placeholder'],
      emits: ['update:modelValue'],
      template:
        '<input :value="modelValue" :placeholder="placeholder" @input="$emit(\'update:modelValue\', $event.target.value)" />'
    },
    'el-tree': {
      props: ['data'],
      methods: { filter: treeFilter, setCurrentKey: treeSetCurrentKey },
      template: '<div class="tree-stub" />'
    },
    'el-button': {
      emits: ['click'],
      template: '<button @click="$emit(\'click\')"><slot /></button>'
    }
  }
};

const nodes = [
  { id: 'subject', label: '综合知识', children: [{ id: 'leaf-1', label: '软件架构' }] },
  { id: 'leaf-2', label: '系统维护' }
];

describe('KnowledgeDirectoryPanel', () => {
  beforeEach(() => {
    treeFilter.mockReset();
    treeSetCurrentKey.mockReset();
  });

  it('renders the recursive count and filters tree data case-insensitively', () => {
    const wrapper = mount(KnowledgeDirectoryPanel, { props: { nodes }, global });
    const state = wrapper.vm as typeof wrapper.vm & {
      knowledgeCount: number;
      filterNode: (value: string, data: { label: string }) => boolean;
    };

    expect(wrapper.get('h3').text()).toBe('知识点目录');
    expect(wrapper.text()).toContain('KNOWLEDGE DIRECTORY');
    expect(wrapper.text()).toContain('3');
    expect(state.knowledgeCount).toBe(3);
    expect(state.filterNode('  架构 ', { label: '软件架构' })).toBe(true);
    expect(state.filterNode('', { label: '任意节点' })).toBe(true);
    expect(state.filterNode('不存在', { label: '软件架构' })).toBe(false);
  });

  it('forwards search, clears the current node and resets when nodes change', async () => {
    const wrapper = mount(KnowledgeDirectoryPanel, { props: { nodes }, global });
    const input = wrapper.get('input');

    await input.setValue('架构');
    expect(treeFilter).toHaveBeenCalledWith('架构');

    await wrapper.get('.clear-directory').trigger('click');
    expect((input.element as HTMLInputElement).value).toBe('');
    expect(treeSetCurrentKey).toHaveBeenCalled();
    expect(wrapper.emitted('clear')).toHaveLength(1);

    await input.setValue('维护');
    await wrapper.setProps({ nodes: [{ id: 'new', label: '新目录' }] });
    await nextTick();
    expect((input.element as HTMLInputElement).value).toBe('');
    expect(treeSetCurrentKey).toHaveBeenCalledTimes(2);
  });

  it('only emits collapse changes when the panel is collapsible', async () => {
    const fixed = mount(KnowledgeDirectoryPanel, { props: { nodes }, global });
    await fixed.get('.directory-header').trigger('click');
    expect(fixed.emitted('update:collapsed')).toBeUndefined();

    const collapsible = mount(KnowledgeDirectoryPanel, {
      props: { nodes, collapsible: true, collapsed: false },
      global
    });
    await collapsible.get('.directory-header').trigger('click');
    expect(collapsible.emitted('update:collapsed')).toEqual([[true]]);
  });

  it('emits the node chosen from the visible directory tree', async () => {
    const wrapper = mount(KnowledgeDirectoryPanel, {
      props: { nodes },
      global: {
        stubs: {
          ...global.stubs,
          'el-tree': {
            props: ['data'],
            emits: ['node-click'],
            methods: { filter: treeFilter, setCurrentKey: treeSetCurrentKey },
            template: '<button class="tree-node" @click="$emit(\'node-click\', data[0].children[0])">节点</button>'
          }
        }
      }
    });

    await wrapper.get('.tree-node').trigger('click');
    expect(wrapper.emitted('select')).toEqual([[nodes[0].children![0]]]);
  });

  it('renders a compact collapsed directory and asks its parent to expand it', async () => {
    const wrapper = mount(KnowledgeDirectoryPanel, {
      props: { nodes, collapsible: true, collapsed: true, showCounts: false, eyebrow: '' },
      global
    });

    expect(wrapper.classes()).toContain('is-collapsed');
    expect(wrapper.find('input').exists()).toBe(false);
    expect(wrapper.find('.tag').exists()).toBe(false);
    expect(wrapper.find('.eyebrow').exists()).toBe(false);
    expect(wrapper.get('.directory-header').attributes('aria-expanded')).toBe('false');

    await wrapper.get('.directory-header').trigger('click');
    expect(wrapper.emitted('update:collapsed')).toEqual([[false]]);
  });
});
