import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import AiQuestionCoachPanel from './AiQuestionCoachPanel.vue';

const api = vi.hoisted(() => ({
  createAiConversation: vi.fn(),
  getAiConversation: vi.fn(),
  listAiMessages: vi.fn(),
  cancelAiMessage: vi.fn(),
  streamAiMessage: vi.fn()
}));

vi.mock('@/api/certmuse/assessment/ai-conversations', () => api);

const conversation = {
  conversationId: '901',
  practiceSessionId: '123',
  questionOrder: 2,
  status: 'ACTIVE',
  answerDisclosureMode: 'GUIDANCE_ONLY',
  generatingAssistantMessageId: null,
  createdAt: '2026-08-18T00:00:00Z',
  updatedAt: '2026-08-18T00:00:00Z'
};

describe('AiQuestionCoachPanel', () => {
  beforeEach(() => {
    api.createAiConversation.mockResolvedValue({ data: { created: false, conversation } });
    api.getAiConversation.mockResolvedValue({ data: conversation });
    api.listAiMessages.mockResolvedValue({
      data: {
        items: [
          { id: '1', sequence: 1, role: 'USER', content: '考什么？', status: 'COMPLETED', errorCode: null },
          { id: '2', sequence: 2, role: 'ASSISTANT', content: '**访问控制**', status: 'COMPLETED', errorCode: null }
        ],
        hasMore: false,
        nextBeforeSequence: null
      }
    });
  });

  it('loads persistent history only after opening and clears only the visible messages', async () => {
    const wrapper = mount(AiQuestionCoachPanel, {
      props: { sessionId: '123', questionOrder: 2, currentSelection: ['B'], submitted: false },
      global: {
        stubs: {
          ElButton: { template: '<button @click="$emit(\'click\')"><slot /></button>' },
          ElSkeleton: true,
          ElAlert: true,
          ElEmpty: true,
          ElInput: { props: ['modelValue'], template: '<textarea :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' }
        }
      }
    });

    expect(api.createAiConversation).not.toHaveBeenCalled();
    await wrapper.get('button').trigger('click');
    await flushPromises();
    expect(api.createAiConversation).toHaveBeenCalledWith('123', 2, expect.any(String));
    expect(wrapper.text()).toContain('访问控制');

    await wrapper.findAll('button').find(button => button.text().includes('清屏'))!.trigger('click');
    expect(wrapper.text()).not.toContain('访问控制');
    expect(api.cancelAiMessage).not.toHaveBeenCalled();
  });

  it('provides drag targets along every edge of the opened panel', async () => {
    const wrapper = mount(AiQuestionCoachPanel, {
      props: { sessionId: '123', questionOrder: 2, currentSelection: ['B'], submitted: false },
      global: {
        stubs: {
          ElButton: { template: '<button @click="$emit(\'click\')"><slot /></button>' },
          ElSkeleton: true,
          ElAlert: true,
          ElEmpty: true,
          ElInput: { props: ['modelValue'], template: '<textarea :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' }
        }
      }
    });

    await wrapper.get('button').trigger('click');
    await flushPromises();

    expect(wrapper.findAll('.ai-question-coach__drag-edge')).toHaveLength(4);
    expect(wrapper.find('.ai-question-coach__drag-edge--top').exists()).toBe(true);
    expect(wrapper.find('.ai-question-coach__drag-edge--right').exists()).toBe(true);
    expect(wrapper.find('.ai-question-coach__drag-edge--bottom').exists()).toBe(true);
    expect(wrapper.find('.ai-question-coach__drag-edge--left').exists()).toBe(true);
  });

  it('shows the assistant message as stopped after cancelling a stream', async () => {
    api.cancelAiMessage.mockResolvedValue({ data: { assistantMessageId: '3', status: 'CANCELLED' } });
    api.streamAiMessage.mockImplementation((_conversationId, _body, _requestId, onEvent, signal) => {
      onEvent({
        event: 'message.start',
        data: { userMessageId: 'user-3', assistantMessageId: '3', sequence: 3, startedAt: '2026-08-18T00:00:00Z' }
      });
      return new Promise((_resolve, reject) => {
        signal.addEventListener('abort', () => reject(new DOMException('', 'AbortError')));
      });
    });
    const wrapper = mount(AiQuestionCoachPanel, {
      props: { sessionId: '123', questionOrder: 2, currentSelection: ['B'], submitted: false },
      global: {
        stubs: {
          ElButton: { template: '<button @click="$emit(\'click\')"><slot /></button>' },
          ElSkeleton: true,
          ElAlert: true,
          ElEmpty: true,
          ElInput: { props: ['modelValue'], template: '<textarea :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' }
        }
      }
    });

    await wrapper.get('button').trigger('click');
    await flushPromises();
    await wrapper.get('textarea').setValue('请继续讲解');
    await wrapper.get('textarea').trigger('keydown', { key: 'Enter' });
    await flushPromises();
    await wrapper.findAll('button').find(button => button.text().includes('停止生成'))!.trigger('click');
    await flushPromises();

    expect(api.cancelAiMessage).toHaveBeenCalledWith('901', '3', expect.any(String));
    expect(wrapper.text()).toContain('已停止生成');
    expect(wrapper.text()).not.toContain('正在生成');
  });
});
