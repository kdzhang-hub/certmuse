import { ElNotification } from 'element-plus';
import type { MessageVO } from '@/api/system/message/types';
import { closeMessageConnection, getMessageBox, issueMessageTicket } from '@/api/system/message';
import { useNoticeStore } from '@/store/modules/notice';
import { useUserStore } from '@/store/modules/user';
import { getToken } from '@/utils/auth';
import { emitImportProgressFromPush } from '@/utils/import-progress-events';
import { isMessageRead } from '@/utils/message-read';
import {
  parsePushMessage,
  PUSH_MESSAGE_TYPE,
  resolveNoticeGroup,
  resolveNoticeTitle,
  shouldAppendNotice
} from '@/utils/push-message';
import { pushConnected } from '@/utils/push-state';

let closePushConnection: (() => void) | undefined;
let stopPushWatchers: Array<() => void> = [];
const KICKED_MESSAGE = 'kicked';
let pushKicked = false;
let resumePushTimer: ReturnType<typeof setTimeout> | undefined;
export { pushConnected } from '@/utils/push-state';
const CHANNEL_NAME = 'certmuse-sse-v1';
const LEASE_KEY = 'certmuse:sse:leader';
const LEASE_TTL = 30_000;
const LEASE_RENEW = 10_000;
const LEADER_SIGNAL_INTERVAL = 1000;
const LEADER_SIGNAL_TIMEOUT = 5000;
const tabId = crypto.randomUUID();
const channel = typeof BroadcastChannel === 'undefined' ? undefined : new BroadcastChannel(CHANNEL_NAME);
let eventSource: EventSource | undefined;
let connectionId: string | undefined;
let leaseTimer: ReturnType<typeof setInterval> | undefined;
let reconnectTimer: ReturnType<typeof setTimeout> | undefined;
let channelListening = false;
let leaderSignalTimer: ReturnType<typeof setInterval> | undefined;
let followerWatchdogTimer: ReturnType<typeof setInterval> | undefined;
let lastLeaderSignalAt = 0;

type CoordinatorMessage =
  | { type: 'message'; data: string }
  | { type: 'state'; connected: boolean }
  | { type: 'hello' }
  | { type: 'logout' };

const broadcast = (message: CoordinatorMessage) => {
  // oxlint-disable-next-line unicorn/require-post-message-target-origin -- BroadcastChannel has no targetOrigin.
  channel?.postMessage(message);
};

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
  const payload = parsePushMessage(raw);
  emitImportProgressFromPush(payload);
  if (payload.type === PUSH_MESSAGE_TYPE.CUSTOM) {
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

const buildSseUrl = (path: string, ticket: string) =>
  `${import.meta.env.VITE_APP_BASE_API}${path}?ticket=${encodeURIComponent(ticket)}`;

const buildWsUrl = (path: string) => {
  const protocol = window.location.protocol === 'https:' ? 'wss://' : 'ws://';
  return `${protocol}${window.location.host}${import.meta.env.VITE_APP_BASE_API}${path}?Authorization=Bearer ${getToken()}&clientid=${import.meta.env.VITE_APP_CLIENT_ID}`;
};

const readLease = () => {
  try {
    return JSON.parse(localStorage.getItem(LEASE_KEY) ?? 'null') as { owner: string; expiresAt: number } | null;
  } catch {
    return null;
  }
};

const ownsLease = () => readLease()?.owner === tabId;

const writeLease = () => {
  localStorage.setItem(LEASE_KEY, JSON.stringify({ owner: tabId, expiresAt: Date.now() + LEASE_TTL }));
};

const connectLeader = async (path: string) => {
  if (!getToken() || eventSource) return;
  try {
    const { data } = await issueMessageTicket();
    if (!ownsLease()) return;
    connectionId = data.connectionId;
    eventSource = new EventSource(buildSseUrl(path, data.ticket));
    eventSource.onopen = () => {
      pushConnected.value = true;
      broadcast({ type: 'state', connected: true });
    };
    eventSource.onmessage = event => {
      handlePushMessage(event.data);
      broadcast({ type: 'message', data: event.data });
    };
    eventSource.onerror = () => {
      pushConnected.value = false;
      broadcast({ type: 'state', connected: false });
      eventSource?.close();
      eventSource = undefined;
      if (ownsLease()) {
        reconnectTimer = setTimeout(() => void connectLeader(path), 5000);
      }
    };
  } catch (error) {
    pushConnected.value = false;
    console.warn('SSE ticket or connection failed', error);
    if (ownsLease()) reconnectTimer = setTimeout(() => void connectLeader(path), 5000);
  }
};

const ensureLeader = (path: string) => {
  const lease = readLease();
  if (!lease || lease.expiresAt <= Date.now() || lease.owner === tabId) {
    writeLease();
    void connectLeader(path);
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
  if (!channelListening) {
    channelListening = true;
    channel?.addEventListener('message', event => {
      const message = event.data as CoordinatorMessage;
      if (message.type === 'message') handlePushMessage(message.data);
      if (message.type === 'state') {
        lastLeaderSignalAt = Date.now();
        pushConnected.value = message.connected;
      }
      if (message.type === 'logout') {
        pushConnected.value = false;
      }
      if (message.type === 'hello' && ownsLease()) {
        broadcast({ type: 'state', connected: pushConnected.value });
      }
    });
  }
  ensureLeader(path);
  broadcast({ type: 'hello' });
  leaderSignalTimer ??= setInterval(() => {
    if (ownsLease()) broadcast({ type: 'state', connected: pushConnected.value });
  }, LEADER_SIGNAL_INTERVAL);
  followerWatchdogTimer ??= setInterval(() => {
    if (!ownsLease() && pushConnected.value && Date.now() - lastLeaderSignalAt > LEADER_SIGNAL_TIMEOUT) {
      pushConnected.value = false;
    }
  }, LEADER_SIGNAL_INTERVAL);
  leaseTimer ??= setInterval(() => {
    if (ownsLease()) {
      writeLease();
    } else {
      ensureLeader(path);
    }
  }, LEASE_RENEW);
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
  const wasLeader = ownsLease();
  if (connectionId) void closeMessageConnection(connectionId).catch(() => undefined);
  eventSource?.close();
  eventSource = undefined;
  connectionId = undefined;
  pushConnected.value = false;
  if (reconnectTimer) clearTimeout(reconnectTimer);
  reconnectTimer = undefined;
  if (leaseTimer) clearInterval(leaseTimer);
  leaseTimer = undefined;
  if (leaderSignalTimer) clearInterval(leaderSignalTimer);
  leaderSignalTimer = undefined;
  if (followerWatchdogTimer) clearInterval(followerWatchdogTimer);
  followerWatchdogTimer = undefined;
  if (ownsLease()) localStorage.removeItem(LEASE_KEY);
  if (wasLeader) broadcast({ type: 'logout' });
  closePushConnection?.();
  closePushConnection = undefined;
  stopPushWatchers.forEach(stop => stop());
  stopPushWatchers = [];
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
