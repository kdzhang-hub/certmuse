import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  cancelAiMessage,
  createAiConversation,
  listAiMessages,
  streamAiMessage,
  type AiStreamEvent
} from './ai-conversations';

const request = vi.hoisted(() => vi.fn(config => Promise.resolve(config)));

vi.mock('@/utils/request', () => ({ default: request }));
vi.mock('@/utils/auth', () => ({ getToken: () => 'student-token' }));
vi.mock('@/lang', () => ({ getLanguage: () => 'zh_CN' }));

describe('AI question coach HTTP contract', () => {
  beforeEach(() => {
    request.mockClear();
    vi.stubGlobal('fetch', vi.fn());
  });

  it('uses the persistent conversation routes and idempotency headers', async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ code: 201, msg: '创建成功', data: { created: true, conversation: {} } }), {
        status: 201,
        headers: { 'Content-Type': 'application/json' }
      })
    );
    const created = await createAiConversation('123', 2, 'create-id');
    listAiMessages('901', { beforeSequence: 41, limit: 20 });
    cancelAiMessage('901', '1004', 'cancel-id');

    expect(created.code).toBe(201);
    expect(fetch).toHaveBeenCalledWith(
      '/api/assessment/knowledge-practices/123/items/2/ai-conversations',
      expect.objectContaining({ method: 'POST', headers: expect.objectContaining({ 'X-Request-Id': 'create-id' }) })
    );
    expect(request.mock.calls).toEqual([
      [{ url: '/api/assessment/ai-conversations/901/messages', method: 'get', params: { beforeSequence: 41, limit: 20 } }],
      [{ url: '/api/assessment/ai-conversations/901/messages/1004/cancel', method: 'post', headers: { 'X-Request-Id': 'cancel-id', repeatSubmit: false } }]
    ]);
  });

  it('sends POST SSE with authenticated headers and parses all business events', async () => {
    const sse = [
      'id: 1',
      'event: message.start',
      'data: {"conversationId":"901","userMessageId":"1003","assistantMessageId":"1004","answerDisclosureMode":"GUIDANCE_ONLY"}',
      '',
      ': heartbeat',
      '',
      'id: 2',
      'event: message.delta',
      'data: {"assistantMessageId":"1004","delta":"提示"}',
      '',
      'id: 3',
      'event: message.completed',
      'data: {"assistantMessageId":"1004","status":"COMPLETED","finishReason":"STOP","contentLength":2,"completedAt":"2026-08-18T00:00:00Z"}',
      ''
    ].join('\n');
    vi.mocked(fetch).mockResolvedValue(new Response(sse, { headers: { 'Content-Type': 'text/event-stream;charset=UTF-8' } }));
    const events: AiStreamEvent[] = [];

    await streamAiMessage('901', { message: '怎么做？', clientMessageId: 'client-id', currentSelection: ['B'] }, 'request-id', event => events.push(event));

    expect(fetch).toHaveBeenCalledWith(
      '/api/assessment/ai-conversations/901/messages/stream',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({ Authorization: 'Bearer student-token', clientid: '', 'X-Request-Id': 'request-id' }),
        body: JSON.stringify({ message: '怎么做？', clientMessageId: 'client-id', currentSelection: ['B'] })
      })
    );
    expect(events.map(event => event.event)).toEqual(['message.start', 'message.delta', 'message.completed']);
  });

  it('routes pre-stream JSON failures into a structured error', async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ code: 429, msg: 'too many', data: { errorCode: 'AI_CHAT_RATE_LIMITED', retryable: true, traceId: null, fieldErrors: [], details: { retryAfterSeconds: 42 } } }), {
        status: 429,
        headers: { 'Content-Type': 'application/json' }
      })
    );

    await expect(streamAiMessage('901', { message: 'x', clientMessageId: 'client-id', currentSelection: [] }, 'request-id', () => undefined)).rejects.toMatchObject({
      name: 'AiChatHttpError',
      status: 429,
      responseData: { errorCode: 'AI_CHAT_RATE_LIMITED' }
    });
  });
});
