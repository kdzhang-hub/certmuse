<template>
  <el-button class="ai-question-coach__stem-button" plain type="primary" :icon="ChatDotRound" @click="openPanel">
    AI讲解
  </el-button>

  <button
    v-if="opened && collapsed"
    type="button"
    class="ai-question-coach__trigger"
    :style="floatingStyle"
    @pointerdown="startDrag"
    @click="expandPanel"
  >
    <ChatDotRound />
    AI讲解
  </button>

  <aside
    v-if="opened && !collapsed"
    ref="panelRef"
    class="ai-question-coach"
    :style="floatingStyle"
    aria-label="AI 讲解"
  >
    <div
      class="ai-question-coach__drag-edge ai-question-coach__drag-edge--top"
      aria-label="按住拖动 AI 讲解窗口"
      @pointerdown="startDrag"
    />
    <div
      class="ai-question-coach__drag-edge ai-question-coach__drag-edge--right"
      aria-label="按住拖动 AI 讲解窗口"
      @pointerdown="startDrag"
    />
    <div
      class="ai-question-coach__drag-edge ai-question-coach__drag-edge--bottom"
      aria-label="按住拖动 AI 讲解窗口"
      @pointerdown="startDrag"
    />
    <div
      class="ai-question-coach__drag-edge ai-question-coach__drag-edge--left"
      aria-label="按住拖动 AI 讲解窗口"
      @pointerdown="startDrag"
    />
    <header class="ai-question-coach__header">
      <div class="ai-question-coach__title">
        <ChatDotRound />
        <div>
          <strong>AI讲解</strong>
          <small>{{ disclosureHint }}</small>
        </div>
      </div>
      <div class="ai-question-coach__header-actions">
        <el-button text title="收起 AI 讲解" @click="collapsed = true">收起</el-button>
      </div>
    </header>

    <main ref="messageListRef" class="ai-question-coach__messages" aria-live="polite">
      <el-alert
        v-if="errorText"
        class="ai-question-coach__error"
        type="error"
        :title="errorText"
        :closable="true"
        @close="errorText = ''"
      />
      <el-button v-if="hasMore && !locallyCleared" class="ai-question-coach__load-more" text @click="loadOlderMessages">
        加载更早对话
      </el-button>
      <el-skeleton v-if="loadingHistory" :rows="4" animated />
      <template v-else-if="messages.length">
        <article
          v-for="message in messages"
          :key="message.id"
          class="ai-question-coach__message"
          :class="`is-${message.role.toLowerCase()}`"
        >
          <strong>{{ message.role === 'USER' ? '我' : 'AI 助教' }}</strong>
          <div
            v-if="message.role === 'ASSISTANT' && message.content"
            class="ai-question-coach__markdown"
            v-html="safeMarkdown(message.content)"
          />
          <p v-else-if="message.content">{{ message.content }}</p>
          <section
            v-if="message.role === 'ASSISTANT' && message.citations.length"
            class="ai-question-coach__sources"
            aria-label="教材来源"
          >
            <span class="ai-question-coach__sources-title">教材来源</span>
            <button
              v-for="citation in uniqueCitations(message.citations)"
              :key="citationKey(citation)"
              type="button"
              class="ai-question-coach__source"
              :title="`查看《${citation.textbookTitle}》原文`"
              @click="openCitation(citation)"
            >
              <Document />
              <span>
                <strong>
                  {{ citation.textbookTitle }}
                  <template v-if="citation.edition">· {{ citation.edition }}</template>
                </strong>
                <small>{{ citationLocation(citation) }}</small>
              </span>
            </button>
          </section>
          <small v-if="message.status === 'GENERATING'">正在生成…</small>
          <small v-else-if="message.status === 'FAILED'">
            生成失败{{ message.errorCode ? `：${message.errorCode}` : '' }}
          </small>
          <small v-else-if="message.status === 'CANCELLED'">已停止生成</small>
        </article>
      </template>
      <el-empty v-else :image-size="56" description="可以问我这道题的思路和知识点" />
    </main>

    <footer class="ai-question-coach__composer">
      <p>AI 生成内容可能有误，请以题目正式解析为准。</p>
      <el-input
        v-model="draft"
        type="textarea"
        :rows="3"
        resize="none"
        maxlength="2000"
        show-word-limit
        :disabled="loadingHistory || generating"
        placeholder="输入你想了解的问题…"
        @keydown.enter.exact.prevent="send"
      />
      <div class="ai-question-coach__composer-actions">
        <el-button text :icon="Delete" title="仅清空当前窗口显示" @click="clearVisibleMessages">清屏</el-button>
        <div>
          <el-button v-if="generating" @click="stopGeneration">停止生成</el-button>
          <el-button v-else type="primary" :disabled="!draft.trim() || loadingHistory" @click="send">发送</el-button>
        </div>
      </div>
    </footer>
  </aside>

  <PdfReaderDialog
    v-model="pdfDialogVisible"
    :document-title="readingCitation?.textbookTitle"
    :reader="pdfReader"
    :initial-page="readingCitation?.pageStart ?? 1"
    @refresh="refreshPdfReader"
  />
</template>

<script setup lang="ts">
import { ChatDotRound, Delete, Document } from '@element-plus/icons-vue';
import { marked } from 'marked';
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import {
  cancelAiMessage,
  createAiConversation,
  getAiConversation,
  listAiMessages,
  streamAiMessage,
  type AiChatErrorVo,
  type AiChatMessageVo,
  type AiCitation,
  type AiConversationVo,
  type AiStreamEvent
} from '@/api/certmuse/assessment/ai-conversations';
import { getLearningTextbookPdfReader, type LearningTextbookPdfReaderVO } from '@/api/certmuse/learning/textbooks';
import { contractErrorFrom } from '@/utils/learning-goal';
import { sanitizeHtml } from '@/utils/sanitize';
import PdfReaderDialog from '@/views/student/resources/PdfReaderDialog.vue';

const props = defineProps<{
  sessionId: string;
  questionOrder: number;
  currentSelection: string[];
  submitted: boolean;
}>();

type UiMessage = Pick<AiChatMessageVo, 'id' | 'role' | 'content' | 'status' | 'errorCode'> & {
  citations: AiCitation[];
};

const opened = ref(false);
const collapsed = ref(false);
const loadingHistory = ref(false);
const generating = ref(false);
const locallyCleared = ref(false);
const draft = ref('');
const errorText = ref('');
const conversation = ref<AiConversationVo>();
const messages = ref<UiMessage[]>([]);
const hasMore = ref(false);
const nextBeforeSequence = ref<number>();
const panelRef = ref<HTMLElement>();
const messageListRef = ref<HTMLElement>();
const pdfDialogVisible = ref(false);
const pdfReader = ref<LearningTextbookPdfReaderVO>();
const readingCitation = ref<AiCitation>();
const floatingPosition = ref<{ left: number; top: number }>();
let abortController: AbortController | undefined;
let generatingAssistantMessageId: string | undefined;
let drag:
  | {
      element: HTMLElement;
      originLeft: number;
      originTop: number;
      offsetX: number;
      offsetY: number;
      width: number;
      height: number;
      startX: number;
      startY: number;
    }
  | undefined;
let pendingPosition: { left: number; top: number } | undefined;
let dragFrame: number | undefined;
let wasDragged = false;

const floatingStyle = computed(() =>
  floatingPosition.value
    ? {
        left: `${floatingPosition.value.left}px`,
        top: `${floatingPosition.value.top}px`,
        right: 'auto',
        bottom: 'auto'
      }
    : undefined
);
const disclosureHint = computed(() =>
  conversation.value?.answerDisclosureMode === 'FULL_EXPLANATION'
    ? '可以询问答案和错因'
    : '提供思路提示，不直接公布答案'
);

watch(
  () => `${props.sessionId}:${props.questionOrder}`,
  async () => {
    await cancelActiveGeneration();
    resetQuestionState();
    if (opened.value && !collapsed.value) await loadConversation();
  }
);

watch(
  () => props.submitted,
  async submitted => {
    if (!submitted || !conversation.value) return;
    const summary = await getAiConversation(conversation.value.conversationId);
    conversation.value = summary.data ?? conversation.value;
  }
);

onBeforeUnmount(() => {
  stopDrag();
  void cancelActiveGeneration();
});

async function openPanel() {
  opened.value = true;
  collapsed.value = false;
  await loadConversation();
}

async function expandPanel() {
  if (wasDragged) {
    wasDragged = false;
    return;
  }
  collapsed.value = false;
  await nextTick();
  clampToViewport();
  if (!conversation.value) await loadConversation();
}

async function loadConversation() {
  if (loadingHistory.value || !props.sessionId || !props.questionOrder) return;
  loadingHistory.value = true;
  errorText.value = '';
  try {
    const created = await createAiConversation(props.sessionId, props.questionOrder, crypto.randomUUID().toLowerCase());
    conversation.value = created.data?.conversation;
    if (!conversation.value) throw new Error('未取得 AI 对话。');
    const [summary, page] = await Promise.all([
      getAiConversation(conversation.value.conversationId),
      listAiMessages(conversation.value.conversationId)
    ]);
    conversation.value = summary.data ?? conversation.value;
    applyMessagePage(page.data, false);
  } catch (error) {
    errorText.value = displayError(error, 'AI 讲解暂时不可用。');
  } finally {
    loadingHistory.value = false;
  }
}

async function loadOlderMessages() {
  if (!conversation.value || !hasMore.value || !nextBeforeSequence.value || locallyCleared.value) return;
  loadingHistory.value = true;
  try {
    const page = await listAiMessages(conversation.value.conversationId, {
      beforeSequence: nextBeforeSequence.value,
      limit: 50
    });
    applyMessagePage(page.data, true);
  } catch (error) {
    errorText.value = displayError(error, '无法加载更早的对话。');
  } finally {
    loadingHistory.value = false;
  }
}

function applyMessagePage(
  page: { items: AiChatMessageVo[]; hasMore: boolean; nextBeforeSequence: number | null } | undefined,
  prepend: boolean
) {
  if (!page) return;
  hasMore.value = page.hasMore;
  nextBeforeSequence.value = page.nextBeforeSequence ?? undefined;
  if (locallyCleared.value) return;
  const incoming = page.items.map(toUiMessage);
  messages.value = prepend ? [...incoming, ...messages.value] : incoming;
  void scrollMessagesToBottom(!prepend);
}

async function send() {
  const text = draft.value.trim();
  if (!text || generating.value) return;
  if (!conversation.value) await loadConversation();
  if (!conversation.value) return;
  errorText.value = '';
  generating.value = true;
  const clientMessageId = crypto.randomUUID().toLowerCase();
  const temporaryId = `pending-${clientMessageId}`;
  messages.value.push({
    id: temporaryId,
    role: 'USER',
    content: text,
    status: 'COMPLETED',
    errorCode: null,
    citations: []
  });
  draft.value = '';
  void scrollMessagesToBottom();
  abortController = new AbortController();
  try {
    await streamAiMessage(
      conversation.value.conversationId,
      { message: text, clientMessageId, currentSelection: [...props.currentSelection] },
      crypto.randomUUID().toLowerCase(),
      event => handleStreamEvent(event, temporaryId),
      abortController.signal
    );
  } catch (error) {
    if ((error as DOMException)?.name !== 'AbortError') {
      messages.value = messages.value.filter(message => message.id !== temporaryId);
      errorText.value = displayError(error, 'AI 生成失败，请稍后重试。');
    }
  } finally {
    abortController = undefined;
    generating.value = false;
    generatingAssistantMessageId = undefined;
  }
}

function handleStreamEvent(event: AiStreamEvent, temporaryUserId: string) {
  if (event.event === 'message.start') {
    messages.value = messages.value.map(message =>
      message.id === temporaryUserId ? { ...message, id: event.data.userMessageId } : message
    );
    generatingAssistantMessageId = event.data.assistantMessageId;
    conversation.value = conversation.value
      ? { ...conversation.value, generatingAssistantMessageId }
      : conversation.value;
    messages.value.push({
      id: event.data.assistantMessageId,
      role: 'ASSISTANT',
      content: '',
      status: 'GENERATING',
      errorCode: null,
      citations: []
    });
  } else {
    const message = messages.value.find(item => item.id === event.data.assistantMessageId);
    if (!message) return;
    if (event.event === 'message.delta') message.content += event.data.delta;
    if (event.event === 'message.completed') {
      message.status = 'COMPLETED';
      message.citations = event.data.citations ?? [];
    }
    if (event.event === 'message.failed') {
      message.status = 'FAILED';
      message.content = '';
      message.errorCode = event.data.errorCode;
    }
    if (event.event === 'message.cancelled') {
      message.status = 'CANCELLED';
      message.content = '';
    }
  }
  void scrollMessagesToBottom();
}

async function stopGeneration() {
  await cancelActiveGeneration(true);
}

async function cancelActiveGeneration(showFailure = false) {
  const conversationId = conversation.value?.conversationId;
  const assistantMessageId =
    generatingAssistantMessageId ?? conversation.value?.generatingAssistantMessageId ?? undefined;
  abortController?.abort();
  if (!conversationId || !assistantMessageId) return;
  try {
    await cancelAiMessage(conversationId, assistantMessageId, crypto.randomUUID().toLowerCase());
    const message = messages.value.find(item => item.id === assistantMessageId);
    if (message?.status === 'GENERATING') message.status = 'CANCELLED';
    if (conversation.value?.generatingAssistantMessageId === assistantMessageId) {
      conversation.value = { ...conversation.value, generatingAssistantMessageId: null };
    }
  } catch (error) {
    if (showFailure) errorText.value = displayError(error, '无法停止生成，将以服务端状态为准。');
  }
}

function clearVisibleMessages() {
  locallyCleared.value = true;
  messages.value = [];
  errorText.value = '';
}

function resetQuestionState() {
  conversation.value = undefined;
  messages.value = [];
  hasMore.value = false;
  nextBeforeSequence.value = undefined;
  locallyCleared.value = false;
  draft.value = '';
  errorText.value = '';
  generating.value = false;
  generatingAssistantMessageId = undefined;
}

function safeMarkdown(content: string) {
  return sanitizeHtml(marked.parse(content, { async: false, breaks: true, gfm: true }) as string);
}

function toUiMessage(message: AiChatMessageVo): UiMessage {
  return {
    id: message.id,
    role: message.role,
    content: message.content,
    status: message.status,
    errorCode: message.errorCode,
    citations: message.citations ?? []
  };
}

function citationKey(citation: AiCitation) {
  return [
    citation.textbookId,
    citation.edition,
    citation.headingPath.join('/'),
    citation.pageStart,
    citation.pageEnd
  ].join(':');
}

function uniqueCitations(citations: AiCitation[]) {
  return [...new Map(citations.map(citation => [citationKey(citation), citation])).values()];
}

function citationLocation(citation: AiCitation) {
  const heading = citation.headingPath.filter(Boolean).join(' / ');
  const pages = citation.pageStart
    ? citation.pageEnd && citation.pageEnd !== citation.pageStart
      ? `第 ${citation.pageStart}–${citation.pageEnd} 页`
      : `第 ${citation.pageStart} 页`
    : '';
  return [heading, pages].filter(Boolean).join(' · ') || '查看教材原文';
}

async function openCitation(citation: AiCitation) {
  readingCitation.value = citation;
  if (await refreshPdfReader()) pdfDialogVisible.value = true;
}

async function refreshPdfReader(): Promise<boolean> {
  if (!readingCitation.value) return false;
  try {
    pdfReader.value = await getLearningTextbookPdfReader(readingCitation.value.textbookId);
    return true;
  } catch (error) {
    errorText.value = displayError(error, '教材原文暂时无法打开。');
    return false;
  }
}

function displayError(error: unknown, fallback: string) {
  const contract = contractErrorFrom(error) as AiChatErrorVo | undefined;
  if (contract?.errorCode === 'AI_CHAT_RATE_LIMITED' && contract.details?.retryAfterSeconds) {
    return `请求过于频繁，请在 ${contract.details.retryAfterSeconds} 秒后重试。`;
  }
  if (contract?.errorCode === 'AI_CHAT_MESSAGE_LIMIT_REACHED') return '当前题的 AI 对话已达消息上限。';
  if (contract?.errorCode === 'AI_CHAT_DISABLED') return 'AI 讲解暂未启用。';
  return error instanceof Error && error.message ? error.message : fallback;
}

async function scrollMessagesToBottom(force = true) {
  await nextTick();
  if (force && messageListRef.value) messageListRef.value.scrollTop = messageListRef.value.scrollHeight;
}

function startDrag(event: PointerEvent) {
  if (event.button !== 0) return;
  const element = (event.currentTarget as HTMLElement).closest(
    '.ai-question-coach, .ai-question-coach__trigger'
  ) as HTMLElement | null;
  if (!element) return;
  const rect = element.getBoundingClientRect();
  drag = {
    element,
    originLeft: rect.left,
    originTop: rect.top,
    offsetX: event.clientX - rect.left,
    offsetY: event.clientY - rect.top,
    width: rect.width,
    height: rect.height,
    startX: event.clientX,
    startY: event.clientY
  };
  pendingPosition = { left: rect.left, top: rect.top };
  wasDragged = false;
  element.classList.add('is-dragging');
  document.addEventListener('pointermove', moveDrag);
  document.addEventListener('pointerup', stopDrag, { once: true });
}

function moveDrag(event: PointerEvent) {
  if (!drag) return;
  if (Math.abs(event.clientX - drag.startX) > 3 || Math.abs(event.clientY - drag.startY) > 3) wasDragged = true;
  const gap = 12;
  pendingPosition = {
    left: Math.max(gap, Math.min(event.clientX - drag.offsetX, window.innerWidth - drag.width - gap)),
    top: Math.max(gap, Math.min(event.clientY - drag.offsetY, window.innerHeight - drag.height - gap))
  };
  if (dragFrame !== undefined) return;
  dragFrame = requestAnimationFrame(() => {
    dragFrame = undefined;
    if (!drag || !pendingPosition) return;
    drag.element.style.transform = `translate3d(${pendingPosition.left - drag.originLeft}px, ${pendingPosition.top - drag.originTop}px, 0)`;
  });
}

function stopDrag() {
  if (dragFrame !== undefined) cancelAnimationFrame(dragFrame);
  dragFrame = undefined;
  if (drag) {
    drag.element.style.transform = '';
    drag.element.classList.remove('is-dragging');
  }
  if (pendingPosition) floatingPosition.value = pendingPosition;
  drag = undefined;
  pendingPosition = undefined;
  document.removeEventListener('pointermove', moveDrag);
}

function clampToViewport() {
  if (!floatingPosition.value || !panelRef.value) return;
  const rect = panelRef.value.getBoundingClientRect();
  const gap = 12;
  floatingPosition.value = {
    left: Math.max(gap, Math.min(floatingPosition.value.left, window.innerWidth - rect.width - gap)),
    top: Math.max(gap, Math.min(floatingPosition.value.top, window.innerHeight - rect.height - gap))
  };
}
</script>

<style scoped lang="scss">
.ai-question-coach__stem-button {
  margin: 16px 0 0 12px;
  vertical-align: middle;
}
.ai-question-coach,
.ai-question-coach__trigger {
  position: fixed;
  z-index: 2200;
  left: 24px;
  top: 12.5vh;
}
.ai-question-coach {
  display: flex;
  width: 25vw;
  height: 75vh;
  min-width: 320px;
  min-height: 420px;
  flex-direction: column;
  overflow: visible;
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
  background: var(--el-bg-color);
  box-shadow: 0 14px 34px rgb(15 23 42 / 18%);
}
.ai-question-coach__drag-edge {
  position: absolute;
  z-index: 2;
  touch-action: none;
  user-select: none;
}
.ai-question-coach__drag-edge--top,
.ai-question-coach__drag-edge--bottom {
  right: 0;
  left: 0;
  height: 16px;
  cursor: grab;
}
.ai-question-coach__drag-edge--top {
  top: -8px;
}
.ai-question-coach__drag-edge--bottom {
  bottom: -8px;
}
.ai-question-coach__drag-edge--right,
.ai-question-coach__drag-edge--left {
  top: 0;
  bottom: 0;
  width: 16px;
  cursor: grab;
}
.ai-question-coach__drag-edge--right {
  right: -8px;
}
.ai-question-coach__drag-edge--left {
  left: -8px;
}
.ai-question-coach__drag-edge:active,
.ai-question-coach__trigger:active {
  cursor: grabbing;
}
.ai-question-coach__header {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 14px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  box-shadow: 0 5px 12px rgb(15 23 42 / 8%);
}
.ai-question-coach__title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 9px;
}
.ai-question-coach__title svg {
  color: var(--el-color-primary);
}
.ai-question-coach__title strong,
.ai-question-coach__title small {
  display: block;
}
.ai-question-coach__title small {
  margin-top: 2px;
  color: var(--el-text-color-secondary);
  font-size: 11px;
}
.ai-question-coach__messages {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: 10px;
  overflow-y: auto;
  padding: 14px;
  background: var(--el-fill-color-lighter);
}
.ai-question-coach__load-more {
  align-self: center;
}
.ai-question-coach__message {
  max-width: 92%;
  padding: 10px 12px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.65;
}
.ai-question-coach__message.is-user {
  align-self: flex-end;
  color: var(--el-color-white);
  background: var(--el-color-primary);
}
.ai-question-coach__message.is-assistant {
  align-self: flex-start;
  background: var(--el-bg-color);
  box-shadow: 0 1px 2px rgb(15 23 42 / 8%);
}
.ai-question-coach__message strong {
  display: block;
  margin-bottom: 4px;
  font-size: 12px;
}
.ai-question-coach__message p {
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.ai-question-coach__message small {
  display: block;
  margin-top: 6px;
  opacity: 0.75;
}
.ai-question-coach__markdown :deep(p) {
  margin: 0 0 8px;
}
.ai-question-coach__markdown :deep(p:last-child) {
  margin-bottom: 0;
}
.ai-question-coach__markdown :deep(pre) {
  overflow-x: auto;
  padding: 8px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
}
.ai-question-coach__sources {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 10px;
  padding-top: 9px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.ai-question-coach__sources-title {
  color: var(--el-text-color-secondary);
  font-size: 11px;
  font-weight: 600;
}
.ai-question-coach__source {
  display: flex;
  width: 100%;
  align-items: flex-start;
  gap: 7px;
  padding: 7px 8px;
  border: 0;
  border-radius: 7px;
  color: var(--el-text-color-primary);
  background: var(--el-fill-color-light);
  text-align: left;
  cursor: pointer;
}
.ai-question-coach__source:hover {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.ai-question-coach__source:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}
.ai-question-coach__source > svg {
  width: 14px;
  height: 14px;
  flex: 0 0 auto;
  margin-top: 2px;
  color: var(--el-color-primary);
}
.ai-question-coach__source > span {
  min-width: 0;
}
.ai-question-coach__source strong,
.ai-question-coach__source small {
  display: block;
  margin: 0;
}
.ai-question-coach__source strong {
  overflow: hidden;
  font-size: 11px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ai-question-coach__source small {
  margin-top: 2px;
  color: var(--el-text-color-regular);
  font-size: 10px;
  line-height: 1.45;
}
.ai-question-coach__composer {
  position: relative;
  z-index: 1;
  padding: 10px 12px 12px;
  border-top: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
  box-shadow: 0 -5px 12px rgb(15 23 42 / 8%);
}
.ai-question-coach__composer > p {
  margin: 0 0 8px;
  color: var(--el-text-color-secondary);
  font-size: 11px;
  line-height: 1.5;
}
.ai-question-coach__composer-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}
.ai-question-coach__trigger {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 10px 15px;
  border: 1px solid var(--el-color-primary);
  border-radius: 22px;
  color: var(--el-color-white);
  background: var(--el-color-primary);
  box-shadow: 0 10px 24px rgb(64 158 255 / 30%);
  cursor: pointer;
  touch-action: none;
  user-select: none;
}
.is-dragging {
  transition: none;
}
@media (max-width: 768px) {
  .ai-question-coach {
    left: 12px;
    top: 12px;
    width: calc(100vw - 24px);
    height: 75vh;
    min-width: 0;
  }
  .ai-question-coach__trigger {
    left: 12px;
  }
}
</style>
