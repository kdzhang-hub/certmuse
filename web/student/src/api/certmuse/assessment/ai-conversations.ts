import type { AxiosPromise, RuoYiAjaxResult } from '@/utils/api-types';
import { getLanguage } from '@/lang';
import { getToken } from '@/utils/auth';
import request from '@/utils/request';

export type AiConversationStatus = 'ACTIVE' | 'DISABLED';
export type AiMessageRole = 'USER' | 'ASSISTANT';
export type AiMessageStatus = 'COMPLETED' | 'GENERATING' | 'FAILED' | 'CANCELLED';
export type AnswerDisclosureMode = 'GUIDANCE_ONLY' | 'FULL_EXPLANATION';

export interface AiChatErrorVo {
  errorCode: string;
  retryable: boolean;
  traceId: string | null;
  fieldErrors: Array<{ field: string; code: 'REQUIRED' | 'INVALID_FORMAT' | 'OUT_OF_RANGE'; message: string }>;
  details: {
    conversationId?: string;
    assistantMessageId?: string;
    retryAfterSeconds?: number;
    limitType?: 'CONCURRENT_GENERATION' | 'USER_MINUTE' | 'USER_DAY';
  } | null;
}

export interface AiChatMessageVo {
  id: string;
  sequence: number;
  role: AiMessageRole;
  content: string;
  status: AiMessageStatus;
  answerDisclosureMode: AnswerDisclosureMode | null;
  errorCode: string | null;
  createdAt: string;
  completedAt: string | null;
}

export interface AiConversationVo {
  conversationId: string;
  practiceSessionId: string;
  questionOrder: number;
  status: AiConversationStatus;
  answerDisclosureMode: AnswerDisclosureMode;
  generatingAssistantMessageId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateAiConversationVo {
  created: boolean;
  conversation: AiConversationVo;
}

export interface AiMessagePageVo {
  items: AiChatMessageVo[];
  hasMore: boolean;
  nextBeforeSequence: number | null;
}

export interface SendAiMessageRequest {
  message: string;
  clientMessageId: string;
  currentSelection: string[];
}

export interface CancelAiMessageVo {
  assistantMessageId: string;
  status: 'CANCELLED';
}

export type AiStreamEvent =
  | {
      event: 'message.start';
      data: { conversationId: string; userMessageId: string; assistantMessageId: string; answerDisclosureMode: AnswerDisclosureMode };
    }
  | { event: 'message.delta'; data: { assistantMessageId: string; delta: string } }
  | {
      event: 'message.completed';
      data: { assistantMessageId: string; status: 'COMPLETED'; finishReason: 'STOP' | 'LENGTH' | 'CONTENT_FILTER'; contentLength: number; completedAt: string };
    }
  | {
      event: 'message.failed';
      data: { assistantMessageId: string; status: 'FAILED'; errorCode: string; retryable: boolean; traceId: string; partialContentDiscarded: boolean };
    }
  | { event: 'message.cancelled'; data: { assistantMessageId: string; status: 'CANCELLED'; completedAt: string } };

export class AiChatHttpError extends Error {
  readonly responseData: AiChatErrorVo | undefined;

  constructor(
    message: string,
    readonly status: number,
    responseData?: AiChatErrorVo
  ) {
    super(message);
    this.name = 'AiChatHttpError';
    this.responseData = responseData;
  }
}

export const createAiConversation = (
  sessionId: string,
  questionOrder: number,
  requestId: string
): Promise<RuoYiAjaxResult<CreateAiConversationVo>> =>
  fetchAiJson(`/api/assessment/knowledge-practices/${sessionId}/items/${questionOrder}/ai-conversations`, {
    method: 'POST',
    requestId
  });

export const getAiConversation = (conversationId: string): AxiosPromise<AiConversationVo> =>
  request({ url: `/api/assessment/ai-conversations/${conversationId}`, method: 'get' });

export const listAiMessages = (
  conversationId: string,
  params?: { beforeSequence?: number; limit?: number }
): AxiosPromise<AiMessagePageVo> =>
  request({ url: `/api/assessment/ai-conversations/${conversationId}/messages`, method: 'get', params });

export const cancelAiMessage = (
  conversationId: string,
  assistantMessageId: string,
  requestId: string
): AxiosPromise<CancelAiMessageVo> =>
  request({
    url: `/api/assessment/ai-conversations/${conversationId}/messages/${assistantMessageId}/cancel`,
    method: 'post',
    headers: { 'X-Request-Id': requestId, repeatSubmit: false }
  });

export async function streamAiMessage(
  conversationId: string,
  payload: SendAiMessageRequest,
  requestId: string,
  onEvent: (event: AiStreamEvent) => void,
  signal?: AbortSignal
) {
  const response = await fetch(apiUrl(`/api/assessment/ai-conversations/${conversationId}/messages/stream`), {
    method: 'POST',
    signal,
    headers: {
      ...aiHeaders(requestId, 'text/event-stream, application/json'),
      'Content-Type': 'application/json;charset=UTF-8'
    },
    body: JSON.stringify(payload)
  });

  const contentType = response.headers.get('content-type')?.toLowerCase() ?? '';
  if (!response.ok || !contentType.includes('text/event-stream')) {
    const body = await readJson(response);
    const error = body?.data as AiChatErrorVo | undefined;
    throw new AiChatHttpError(error?.errorCode || body?.msg || `AI 对话请求失败（${response.status}）`, response.status, error);
  }
  if (!response.body) throw new AiChatHttpError('AI 对话流未返回内容。', response.status);

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let pending = '';
  try {
    while (true) {
      const { done, value } = await reader.read();
      pending += decoder.decode(value, { stream: !done });
      const chunks = pending.split(/\r?\n\r?\n/);
      pending = chunks.pop() ?? '';
      chunks.forEach(chunk => dispatchSseChunk(chunk, onEvent));
      if (done) break;
    }
    if (pending.trim()) dispatchSseChunk(pending, onEvent);
  } finally {
    reader.releaseLock();
  }
}

async function fetchAiJson<T>(path: string, options: { method: 'POST'; requestId: string }): Promise<RuoYiAjaxResult<T>> {
  const response = await fetch(apiUrl(path), {
    method: options.method,
    headers: aiHeaders(options.requestId, 'application/json')
  });
  const body = await readJson(response);
  if (!response.ok) {
    const error = body?.data as AiChatErrorVo | undefined;
    throw new AiChatHttpError(error?.errorCode || body?.msg || `AI 对话请求失败（${response.status}）`, response.status, error);
  }
  return (body ?? {}) as RuoYiAjaxResult<T>;
}

function aiHeaders(requestId: string, accept: string) {
  return {
    Accept: accept,
    Authorization: `Bearer ${getToken() ?? ''}`,
    clientid: import.meta.env.VITE_APP_CLIENT_ID ?? '',
    'Content-Language': getLanguage(),
    'X-Request-Id': requestId
  };
}

function apiUrl(path: string) {
  return `${(import.meta.env.VITE_APP_BASE_API ?? '').replace(/\/$/, '')}${path}`;
}

function dispatchSseChunk(chunk: string, onEvent: (event: AiStreamEvent) => void) {
  const lines = chunk.split(/\r?\n/);
  if (lines.every(line => !line || line.startsWith(':'))) return;
  const event = lines.find(line => line.startsWith('event:'))?.slice('event:'.length).trim();
  const data = lines
    .filter(line => line.startsWith('data:'))
    .map(line => line.slice('data:'.length).trimStart())
    .join('\n');
  if (!event || !data) return;
  if (!['message.start', 'message.delta', 'message.completed', 'message.failed', 'message.cancelled'].includes(event)) return;
  onEvent({ event, data: JSON.parse(data) } as AiStreamEvent);
}

async function readJson(response: Response): Promise<{ code?: number; msg?: string; data?: unknown } | undefined> {
  const text = await response.text();
  if (!text) return undefined;
  try {
    return JSON.parse(text);
  } catch {
    return { msg: text };
  }
}
