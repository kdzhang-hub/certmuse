import { mount } from '@vue/test-utils';
import { afterEach, describe, expect, it } from 'vitest';
import { defineComponent, nextTick } from 'vue';
import PdfReaderDialog from './PdfReaderDialog.vue';

const DialogStub = defineComponent({ props: { modelValue: Boolean }, template: '<div v-if="modelValue"><slot name="header" /><slot /></div>' });
const ButtonStub = defineComponent({ emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' });
const global = {
  stubs: {
    'el-dialog': DialogStub,
    'el-button': ButtonStub,
    'el-alert': defineComponent({ template: '<div><slot /><slot name="default" /></div>' }),
    'el-skeleton': defineComponent({ template: '<div class="skeleton" />' })
  }
};

function mountReader(initialPage = 1) {
  return mount(PdfReaderDialog, {
    attachTo: document.body,
    props: { modelValue: true, initialPage, reader: { readUrl: '/api/reader/textbook-pdfs/ticket', expiresAt: '' } },
    global
  });
}

describe('PdfReaderDialog', () => {
  afterEach(() => { document.body.innerHTML = ''; });

  it('opens the browser preview immediately at the requested page', () => {
    const wrapper = mountReader(18);
    expect(wrapper.find('iframe').attributes('src')).toBe('/api/reader/textbook-pdfs/ticket#page=18');
    expect(wrapper.find('canvas').exists()).toBe(false);
    expect(wrapper.find('.reader-toolbar').element.nextElementSibling).toBe(wrapper.find('.pdf-scroll-host').element);
    wrapper.unmount();
  });

  it('updates the browser destination when the content locator changes', async () => {
    const wrapper = mountReader(1);
    await wrapper.setProps({ initialPage: 56 });
    await nextTick();
    expect(wrapper.find('iframe').attributes('src')).toBe('/api/reader/textbook-pdfs/ticket#page=56');
    wrapper.unmount();
  });

  it('keeps only the reader-link refresh action outside the browser PDF controls', async () => {
    const wrapper = mountReader();
    expect(wrapper.findAll('button').map(button => button.text())).toEqual(['刷新阅读链接']);
    await wrapper.find('button').trigger('click');
    expect(wrapper.emitted('refresh')).toHaveLength(1);
    wrapper.unmount();
  });
});
