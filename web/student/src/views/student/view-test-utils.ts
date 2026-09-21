import { mount, type MountingOptions } from '@vue/test-utils';
import { vi } from 'vitest';
import { defineComponent, h } from 'vue';

export const validateStudentForm = vi.fn<() => Promise<boolean>>(() => Promise.resolve(true));

const SlotStub = defineComponent({
  name: 'SlotStub',
  inheritAttrs: false,
  setup(_, { attrs, slots }) {
    return () => h('div', attrs, [slots.header?.(), slots.default?.(), slots.extra?.()]);
  }
});

const TextStub = defineComponent({
  name: 'TextStub',
  inheritAttrs: false,
  props: ['title', 'subTitle', 'description', 'label', 'value', 'suffix'],
  setup(props, { attrs, slots }) {
    return () =>
      h('div', attrs, [
        props.title,
        props.subTitle,
        props.description,
        props.label,
        props.value,
        props.suffix,
        slots.default?.(),
        slots.extra?.()
      ]);
  }
});

const ButtonStub = defineComponent({
  name: 'ElButton',
  inheritAttrs: false,
  emits: ['click'],
  setup(_, { attrs, emit, slots }) {
    return () =>
      h(
        'button',
        {
          disabled: Boolean(attrs.disabled),
          type: 'button',
          'data-loading': String(Boolean(attrs.loading)),
          onClick: (event: MouseEvent) => emit('click', event)
        },
        slots.default?.()
      );
  }
});

const FormStub = defineComponent({
  name: 'ElForm',
  setup(_, { expose, slots }) {
    expose({ validate: validateStudentForm });
    return () => h('form', slots.default?.());
  }
});

const ModelStub = defineComponent({
  name: 'ModelStub',
  inheritAttrs: false,
  props: ['modelValue'],
  emits: ['update:modelValue', 'change', 'blur'],
  setup(props, { attrs, emit, slots }) {
    return () =>
      h('div', { ...attrs, 'data-model-value': JSON.stringify(props.modelValue) }, [
        slots.default?.(),
        h(
          'button',
          { type: 'button', class: 'model-change', onClick: () => emit('change', props.modelValue) },
          'change'
        )
      ]);
  }
});

export const studentViewStubs = {
  ElAlert: TextStub,
  ElButton: ButtonStub,
  ElCard: SlotStub,
  ElCheckbox: SlotStub,
  ElCheckboxGroup: ModelStub,
  ElCol: SlotStub,
  ElDescriptions: SlotStub,
  ElDescriptionsItem: TextStub,
  ElDivider: SlotStub,
  ElEmpty: TextStub,
  ElForm: FormStub,
  ElFormItem: SlotStub,
  ElImage: TextStub,
  ElInputNumber: ModelStub,
  ElOption: TextStub,
  ElRadio: SlotStub,
  ElRadioGroup: ModelStub,
  ElResult: TextStub,
  ElRow: SlotStub,
  ElSelect: ModelStub,
  ElSkeleton: TextStub,
  ElStatistic: TextStub,
  ElStep: TextStub,
  ElSteps: SlotStub,
  ElTag: SlotStub
};

export function mountStudentView(component: object, options: MountingOptions<any> = {}) {
  return mount(
    component as any,
    {
      ...options,
      global: {
        ...options.global,
        directives: { loading: () => undefined, ...options.global?.directives },
        stubs: { ...studentViewStubs, ...options.global?.stubs }
      }
    } as any
  );
}

export function buttonByText(wrapper: ReturnType<typeof mountStudentView>, text: string) {
  const button = wrapper.findAll('button').find(candidate => candidate.text().includes(text));
  if (!button) throw new Error(`Button not found: ${text}`);
  return button;
}
