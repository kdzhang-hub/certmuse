import { ElNotification } from 'element-plus';
import type { MessageVO } from '@/api/system/message/types';
import { closeMessageConnection, getMessageBox, issueMessageTicket } from '@/api/system/message';
import { useNoticeStore } from '@/store/modules/notice';
import { useUserStore } from '@/store/modules/user';
import { getToken } from '@/utils/auth';
import { isMessageRead } from '@/utils/message-read';
import { parsePushMessage, resolveNoticeGroup, resolveNoticeTitle, shouldAppendNotice } from '@/utils/push-message';
import { buildSseTicketUrl } from '@/utils/push-url';

let closePushConnection: (() => void) | undefined;
let eventSource: EventSource | undefined;
let connectionId: string | undefined;
let reconnectTimer: ReturnType<typeof setTimeout> | undefined;
let sseGeneration = 0;
const KICKED_MESSAGE = 'kicked';
let pushKicked = false;
let resumePushTimer: ReturnType<typeof setTimeout> | undefined;

const formatNoticeTime = (timestamp?: number | string) => {
  const time = timestamp ? new Date(timestamp) : new Date();
  return time.toLocaleString();
};

const appendNotice = (raw: string) => {
  const payload = parsePushMessage(raw);
  if (!shouldAppendNotice(payload)) {
    return;
  }
  const userId = useUserStore().userId;
  const title = resolveNoticeTitle(payload);
  useNoticeStore().addNotice({
    messageId: payload.messageId,
    title,
    category: resolveNoticeGroup(payload),
    type: payload.type,
    source: payload.source,
    message: payload.message ?? '',
    content: payload.data?.noticeContent,
    data: payload.data,
    path: payload.path,
    read: isMessageRead(userId, payload.messageId),
    timestamp: payload.timestamp ?? Date.now(),
    time: formatNoticeTime(payload.timestamp)
  });
  ElNotification({
    title,
    message: payload.message ?? '',
    type: 'success',
    duration: 3000
  });
};

const handlePushMessage = (raw: string) => {
  if (raw === KICKED_MESSAGE) {
    pushKicked = true;
    closePush();
    return;
  }
  appendNotice(raw);
};

const toNoticeItem = (item: MessageVO) => {
  const userId = useUserStore().userId;
  const timestamp = item.createTime ? new Date(item.createTime).getTime() : Date.now();
  return {
    messageId: item.messageId,
    title: item.title,
    category: resolveNoticeGroup(item),
    type: item.type,
    source: item.source,
    message: item.message ?? '',
    content: item.content,
    data: item.data ?? null,
    path: item.path,
    read: isMessageRead(userId, item.messageId),
    timestamp,
    time: formatNoticeTime(timestamp)
  };
};

const buildWsUrl = (path: string) => {
  const protocol = window.location.protocol === 'https:' ? 'wss://' : 'ws://';
  return `${protocol}${window.location.host}${import.meta.env.VITE_APP_BASE_API}${path}?Authorization=Bearer ${getToken()}&clientid=${import.meta.env.VITE_APP_CLIENT_ID}`;
};

const initSsePush = async (path: string, generation: number) => {
  if (!getToken() || generation !== sseGeneration || eventSource) return;
  try {
    const { data } = await issueMessageTicket();
    if (!getToken() || generation !== sseGeneration) {
      return;
    }
    connectionId = data.connectionId;
    const source = new EventSource(buildSseTicketUrl(import.meta.env.VITE_APP_BASE_API, path, data.ticket));
    eventSource = source;
    source.onmessage = event => handlePushMessage(event.data);
    source.onerror = () => {
      if (eventSource !== source) return;
      source.close();
      eventSource = undefined;
      connectionId = undefined;
      if (getToken() && generation === sseGeneration) {
        reconnectTimer = setTimeout(() => void initSsePush(path, generation), 5000);
      }
    };
  } catch {
    console.warn('SSE ticket or connection failed');
    if (getToken() && generation === sseGeneration) {
      reconnectTimer = setTimeout(() => void initSsePush(path, generation), 5000);
    }
  }
};

const initWsPush = (url: string) => {
  const { close } = useWebSocket(url, {
    autoReconnect: {
      retries: 3,
      delay: 1000,
      onFailed() {
        console.warn('websocket重连失败');
      }
    },
    heartbeat: {
      message: 'ping',
      interval: 10000,
      pongTimeout: 2000
    },
    onMessage: (_, e) => {
      if (String(e.data) === 'pong') {
        return;
      }
      handlePushMessage(String(e.data));
    }
  });
  closePushConnection = close;
};

export const initPush = () => {
  closePush();
  if (import.meta.env.VITE_APP_MESSAGE_ENABLED === 'false') {
    return;
  }
  pushKicked = false;
  const path = import.meta.env.VITE_APP_MESSAGE_PATH || '/resource/message';
  const transport = import.meta.env.VITE_APP_MESSAGE_TRANSPORT || 'sse';
  if (transport.toLowerCase() === 'websocket') {
    initWsPush(buildWsUrl(path));
    return;
  }
  void initSsePush(path, sseGeneration);
};

export const initMessageBox = async () => {
  if (import.meta.env.VITE_APP_MESSAGE_ENABLED === 'false') {
    useNoticeStore().clearNotice();
    return;
  }
  const { data } = await getMessageBox();
  const notices = [...(data?.systemList ?? []), ...(data?.noticeList ?? []), ...(data?.workflowList ?? [])].map(
    toNoticeItem
  );
  useNoticeStore().setNotices(notices);
};

export const closePush = () => {
  sseGeneration += 1;
  if (reconnectTimer) clearTimeout(reconnectTimer);
  reconnectTimer = undefined;
  eventSource?.close();
  eventSource = undefined;
  if (connectionId && !pushKicked) void closeMessageConnection(connectionId).catch(() => undefined);
  connectionId = undefined;
  closePushConnection?.();
  closePushConnection = undefined;
};

const resumePushIfNeeded = () => {
  if (!pushKicked || !getToken() || document.visibilityState !== 'visible') {
    return;
  }
  if (resumePushTimer) {
    clearTimeout(resumePushTimer);
  }
  resumePushTimer = setTimeout(async () => {
    resumePushTimer = undefined;
    if (!pushKicked || !getToken() || document.visibilityState !== 'visible') {
      return;
    }
    try {
      await initMessageBox();
    } finally {
      initPush();
    }
  }, 300);
};

window.addEventListener('focus', resumePushIfNeeded);
document.addEventListener('visibilitychange', resumePushIfNeeded);
window.addEventListener('online', resumePushIfNeeded);
