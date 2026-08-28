<template>
  <focus-exam-shell
    v-if="session && question"
    v-loading="loading"
    title="首次诊断"
    description="独立完成作答，系统将据此生成初始学习画像"
    :total-count="totalCount"
    :answered-count="answeredCount"
    :unanswered-count="unansweredCount"
    :timer-label="timerLabel"
    :formatted-timer="formattedTimer"
    :is-overtime="isOvertime"
    :paused="isPaused"
    :saving="saving"
    :save-text="saveText"
    :save-state="saveState"
    @pause="pause"
    @resume="resume"
    @exit="requestExit"
  >
    <el-alert
      v-if="replaced"
      class="mb-4"
      type="warning"
      :closable="false"
      title="诊断已在其他窗口或设备继续"
      description="当前页面已停止计时和编辑。请刷新以同步最新进度，或返回学习首页。"
    >
      <template #default>
        <el-button size="small" @click="refreshAfterReplacement">刷新进度</el-button>
        <el-button size="small" @click="leaveToHome">返回学习首页</el-button>
      </template>
    </el-alert>
    <choice-question
      :question-order="order"
      :total-count="totalCount"
      :selection-mode="question.answer.selection_mode"
      :stem="question.stem"
      :options="question.options"
      :images="question.images"
      :answer="answerValue"
      :disabled="isInteractionDisabled"
      @update:answer="answerValue = $event"
    >
      <template #actions>
        <el-button :disabled="order === 1 || isInteractionDisabled" @click="go(order - 1)">上一题</el-button>
        <el-button v-if="saveState === 'failed'" type="warning" :loading="saving" :disabled="replaced" @click="retrySave">
          重新保存
        </el-button>
        <el-button type="primary" :disabled="isInteractionDisabled" @click="order === totalCount ? openFinish() : go(order + 1)">
          {{ order === totalCount ? '交卷检查' : '下一题' }}
        </el-button>
      </template>
    </choice-question>
    <template #navigation>
      <exam-answer-card
        :total-count="totalCount"
        :answered-count="answeredCount"
        :current-question-order="order"
        :navigation="session.navigation"
        :answered="savedAnswerStates"
        :disabled="isInteractionDisabled"
        finish-label="交卷检查"
        @select="go"
        @finish="openFinish"
      />
    </template>
  </focus-exam-shell>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router';
import {
  finishDiagnostic,
  getDiagnosticQuestion,
  getDiagnosticSession,
  getFinishCheck,
  pauseDiagnostic,
  saveDiagnosticDraft,
  sendDiagnosticTimerEvent,
  type DiagnosticChoiceAnswer,
  type DiagnosticQuestionVo,
  type DiagnosticSessionVo,
  type DiagnosticTimerEventType,
  type DiagnosticTimerEventVo
} from '@/api/certmuse/learning/diagnostics';
import { getOnboardingStatus } from '@/api/certmuse/learning/onboarding';
import ChoiceQuestion from '@/components/exam/ChoiceQuestion.vue';
import ExamAnswerCard from '@/components/exam/ExamAnswerCard.vue';
import FocusExamShell from '@/components/exam/FocusExamShell.vue';
import { useExamCountdown } from '@/hooks/exam/useExamCountdown';
import { resolveOnboardingTarget } from '@/utils/onboarding-routing';
import { getHandledRequestError } from '@/utils/request';

const AUTO_SAVE_DELAY = 5_000;
const HEARTBEAT_DELAY = 10_000;
const route = useRoute();
const router = useRouter();
const sessionId = ref(String(route.query.sessionId ?? ''));
const loading = ref(true);
const saving = ref(false);
const hydratingAnswer = ref(false);
const order = ref(1);
const session = ref<DiagnosticSessionVo>();
const question = ref<DiagnosticQuestionVo>();
const answerValue = ref<string[]>([]);
const savedAnswerStates = ref<Record<number, boolean>>({});
const saveText = ref('已保存');
const saveState = ref<'saved' | 'saving' | 'failed'>('saved');
const isPaused = ref(false);
const isVisible = ref(!document.hidden);
const leaseId = ref<string | null>(null);
const replaced = ref(false);
const submitting = ref(false);

type DiagnosticDraft = {
  requestId: string;
  questionOrder: number;
  answer: DiagnosticChoiceAnswer;
  currentQuestionOrder: number;
};

let pendingDraft: DiagnosticDraft | undefined;
let failedDraft: DiagnosticDraft | undefined;
let autoSaveTimer: ReturnType<typeof setTimeout> | undefined;
let saveQueue: Promise<boolean> = Promise.resolve(true);
let timerEventQueue: Promise<boolean> = Promise.resolve(true);
let heartbeatTimer: ReturnType<typeof setInterval> | undefined;
let leavingForHome = false;

const isInteractionDisabled = computed(() => saving.value || isPaused.value || replaced.value || submitting.value);
const shouldTick = computed(() => Boolean(leaseId.value) && isVisible.value && !isPaused.value && !replaced.value);
const totalCount = computed(() => session.value?.navigation.length ?? 0);
const answeredCount = computed(() => Object.values(savedAnswerStates.value).filter(Boolean).length);
const unansweredCount = computed(() => totalCount.value - answeredCount.value);
const { start: startCountdown, sync: syncCountdown, isOvertime, label: timerLabel, formatted: formattedTimer } = useExamCountdown(
  () =>
    session.value
      ? {
          estimatedDurationSeconds: session.value.estimatedDurationSeconds,
          effectiveElapsedSeconds: session.value.effectiveElapsedSeconds,
          serverTime: session.value.serverTime
        }
      : undefined,
  shouldTick
);

function answerIsSelected(answer: DiagnosticChoiceAnswer) {
  return Array.isArray(answer.value) && answer.value.length > 0;
}

function normalizeAnswer(): DiagnosticChoiceAnswer {
  if (!question.value) throw new Error('题目不可用。');
  return { ...question.value.answer, value: answerValue.value.length ? [...answerValue.value] : null };
}

function draftForCurrent(currentQuestionOrder = order.value): DiagnosticDraft {
  return {
    requestId: crypto.randomUUID(),
    questionOrder: order.value,
    answer: normalizeAnswer(),
    currentQuestionOrder
  };
}

function replaceNavigation(next: DiagnosticSessionVo) {
  session.value = next;
  savedAnswerStates.value = Object.fromEntries(next.navigation.map(item => [item.questionOrder, item.state === 'ANSWERED']));
  syncCountdown({
    estimatedDurationSeconds: next.estimatedDurationSeconds,
    effectiveElapsedSeconds: next.effectiveElapsedSeconds,
    serverTime: next.serverTime
  });
}

function clearAutoSave() {
  if (autoSaveTimer) clearTimeout(autoSaveTimer);
  autoSaveTimer = undefined;
}

function scheduleAutoSave() {
  clearAutoSave();
  autoSaveTimer = setTimeout(() => void flushSave(), AUTO_SAVE_DELAY);
}

async function routeByAction(action: unknown) {
  await router.replace(resolveOnboardingTarget(action) ?? '/learning/onboarding-error');
}

async function handleDiagnosticError(error: unknown) {
  const detail = getHandledRequestError<{ errorCode?: string; nextAction?: string }>(error)?.responseData;
  if (detail?.errorCode === 'DIAGNOSTIC_SESSION_VERSION_CONFLICT') {
    ElMessage.warning('其他设备已有新进度，已同步最新内容');
    await load();
    return true;
  }
  if (detail?.errorCode === 'DIAGNOSTIC_SESSION_NOT_FOUND') {
    const onboarding = (await getOnboardingStatus()).data;
    await routeByAction(onboarding?.nextAction);
    return true;
  }
  if (detail?.nextAction) {
    await routeByAction(detail.nextAction);
    return true;
  }
  return false;
}

async function ensureSessionId() {
  if (sessionId.value) return true;
  const status = (await getOnboardingStatus()).data;
  if (status?.activeSessionId) {
    sessionId.value = status.activeSessionId;
    await router.replace({ path: '/learning/diagnostic/session', query: { sessionId: sessionId.value } });
    return true;
  }
  await routeByAction(status?.nextAction);
  return false;
}

function stopHeartbeat() {
  if (heartbeatTimer) clearInterval(heartbeatTimer);
  heartbeatTimer = undefined;
}

function startHeartbeat() {
  stopHeartbeat();
  heartbeatTimer = setInterval(() => void sendTimerEvent('HEARTBEAT'), HEARTBEAT_DELAY);
}

function acceptTiming(response: DiagnosticTimerEventVo) {
  if (session.value) {
    session.value.estimatedDurationSeconds = response.estimatedDurationSeconds;
    session.value.effectiveElapsedSeconds = response.effectiveElapsedSeconds;
    session.value.serverTime = response.serverTime;
    syncCountdown(response);
  }
  if (response.timerState === 'REPLACED') {
    leaseId.value = null;
    replaced.value = true;
    stopHeartbeat();
    ElMessage.warning('诊断已在其他窗口或设备继续');
    return false;
  }
  leaseId.value = response.timerState === 'RUNNING' ? response.leaseId : null;
  if (response.timerState === 'RUNNING') startHeartbeat();
  else stopHeartbeat();
  return response.eventAccepted;
}

async function sendTimerEvent(eventType: DiagnosticTimerEventType, bestEffort = false) {
  if (!sessionId.value || !question.value || replaced.value) return false;
  if (eventType !== 'ENTER' && !leaseId.value) return true;
  const request = {
    eventType,
    questionOrder: order.value,
    leaseId: eventType === 'ENTER' ? null : leaseId.value
  };
  timerEventQueue = timerEventQueue.then(async () => {
    try {
      const response = await sendDiagnosticTimerEvent(sessionId.value, request, crypto.randomUUID());
      return response.data ? acceptTiming(response.data) : false;
    } catch (error) {
      if (!bestEffort && !(await handleDiagnosticError(error)))
        ElMessage.warning('计时连接暂不可用，将在恢复页面后重新校准。');
      return false;
    }
  });
  return timerEventQueue;
}

async function enterCurrentQuestion() {
  if (!isVisible.value || isPaused.value || replaced.value || !question.value) return;
  await nextTick();
  if (isVisible.value && !isPaused.value) await sendTimerEvent('ENTER');
}

async function leaveCurrentQuestion(bestEffort = false) {
  clearAutoSave();
  stopHeartbeat();
  if (!leaseId.value) return true;
  return sendTimerEvent('LEAVE', bestEffort);
}

async function load(nextOrder?: number, enter = true) {
  loading.value = true;
  try {
    if (!(await ensureSessionId())) return;
    const summary = (await getDiagnosticSession(sessionId.value)).data;
    if (!summary) throw new Error('诊断会话不可用。');
    if (summary.status !== 'IN_PROGRESS') {
      await routeByAction(summary.nextAction);
      return;
    }
    replaceNavigation(summary);
    order.value = nextOrder ?? summary.currentQuestionOrder;
    const item = (await getDiagnosticQuestion(sessionId.value, order.value)).data;
    if (!item) throw new Error('题目不可用。');
    question.value = item;
    hydratingAnswer.value = true;
    answerValue.value = [...(item.answer.value ?? [])];
    await nextTick();
    hydratingAnswer.value = false;
    saveText.value = '已保存';
    saveState.value = 'saved';
    pendingDraft = undefined;
    failedDraft = undefined;
    startCountdown();
    if (enter) await enterCurrentQuestion();
  } catch (error) {
    if (!(await handleDiagnosticError(error))) {
      ElMessage.error(error instanceof Error ? error.message : '加载失败');
      await router.replace('/learning/diagnostic/intro');
    }
  } finally {
    loading.value = false;
  }
}

function queueDraftSave(draft: DiagnosticDraft) {
  saveQueue = saveQueue.then(async () => {
    if (!session.value || replaced.value) return false;
    saving.value = true;
    saveText.value = '保存中';
    saveState.value = 'saving';
    try {
      const result = await saveDiagnosticDraft(
        sessionId.value,
        draft.questionOrder,
        {
          answer: draft.answer,
          expectedSessionVersion: session.value.sessionVersion,
          currentQuestionOrder: draft.currentQuestionOrder
        },
        draft.requestId
      );
      if (!result.data) throw new Error('保存失败。');
      session.value.sessionVersion = result.data.sessionVersion;
      savedAnswerStates.value[draft.questionOrder] = answerIsSelected(draft.answer);
      if (pendingDraft?.requestId === draft.requestId) pendingDraft = undefined;
      if (failedDraft?.requestId === draft.requestId) failedDraft = undefined;
      saveText.value = '已保存';
      saveState.value = 'saved';
      return true;
    } catch (error) {
      failedDraft = draft;
      saveText.value = '保存失败，请重试';
      saveState.value = 'failed';
      if (!(await handleDiagnosticError(error))) ElMessage.error(error instanceof Error ? error.message : '保存失败');
      return false;
    } finally {
      saving.value = false;
    }
  });
  return saveQueue;
}

async function flushSave() {
  clearAutoSave();
  const draft = failedDraft ?? pendingDraft;
  if (!draft) return true;
  const saved = await queueDraftSave(draft);
  if (!saved) return false;
  // A newer input may have appeared while the previous request was in flight.
  if (pendingDraft && pendingDraft.requestId !== draft.requestId) return flushSave();
  return true;
}

watch(
  answerValue,
  () => {
    if (loading.value || hydratingAnswer.value || replaced.value) return;
    failedDraft = undefined;
    pendingDraft = draftForCurrent();
    saveText.value = '待保存';
    saveState.value = 'saved';
    scheduleAutoSave();
  },
  { deep: true }
);

async function retrySave() {
  if (failedDraft) pendingDraft = failedDraft;
  await flushSave();
}

async function go(nextOrder: number) {
  if (isInteractionDisabled.value || nextOrder === order.value) return;
  // Persist the destination as the server-side current position together with the current answer.
  // Otherwise the subsequent session refresh still reports the question being left as CURRENT.
  if (pendingDraft) pendingDraft.currentQuestionOrder = nextOrder;
  if (failedDraft) failedDraft.currentQuestionOrder = nextOrder;
  if (!pendingDraft && !failedDraft) pendingDraft = draftForCurrent(nextOrder);
  if (!(await flushSave())) return;
  await leaveCurrentQuestion();
  if (replaced.value) return;
  await load(nextOrder);
}

async function pause() {
  if (!session.value || isInteractionDisabled.value) return;
  if (!(await flushSave())) return;
  stopHeartbeat();
  await leaveCurrentQuestion(true);
  try {
    const response = await pauseDiagnostic(
      sessionId.value,
      { currentQuestionOrder: order.value, expectedSessionVersion: session.value.sessionVersion },
      crypto.randomUUID()
    );
    if (!response.data) throw new Error('暂停失败。');
    replaceNavigation(response.data);
    isPaused.value = true;
  } catch (error) {
    if (!(await handleDiagnosticError(error))) ElMessage.error('暂停失败，请稍后重试。');
  }
}

async function resume() {
  isPaused.value = false;
  await enterCurrentQuestion();
}

async function requestExit() {
  if (!session.value || submitting.value) return;
  const confirmed = await ElMessageBox.confirm('当前已保存进度将保留，下次可继续完成首次诊断。确认退出吗？', '退出首次诊断', {
    confirmButtonText: '确认退出',
    cancelButtonText: '继续答题',
    type: 'warning'
  }).catch(() => false);
  if (!confirmed) return;
  if (!(await flushSave())) return;
  await leaveCurrentQuestion(true);
  try {
    const response = await pauseDiagnostic(
      sessionId.value,
      { currentQuestionOrder: order.value, expectedSessionVersion: session.value.sessionVersion },
      crypto.randomUUID()
    );
    if (response.data) replaceNavigation(response.data);
    leavingForHome = true;
    await router.replace('/learning/home');
  } catch (error) {
    if (!(await handleDiagnosticError(error))) ElMessage.error('退出失败，请稍后重试。');
  }
}

async function leaveToHome() {
  leavingForHome = true;
  await router.replace('/learning/home');
}

async function refreshAfterReplacement() {
  replaced.value = false;
  leaseId.value = null;
  await load();
}

async function openFinish() {
  if (!session.value || isInteractionDisabled.value) return;
  if (!(await flushSave())) return;
  try {
    const check = (await getFinishCheck(sessionId.value)).data;
    if (!check || check.pendingSaveCount) {
      ElMessage.warning('正在保存最后的作答，请稍候。');
      return;
    }
    const text = check.unansweredCount
      ? `还有 ${check.unansweredCount} 道题未作答。交卷后不能修改答案，未答题将按未作答记录。`
      : `已完成 ${check.answeredCount}/${totalCount.value} 题。交卷后不能修改答案，确认提交吗？`;
    const confirmed = await ElMessageBox.confirm(text, '确认交卷', {
      confirmButtonText: check.unansweredCount ? '仍然交卷' : '确认交卷',
      cancelButtonText: check.unansweredCount ? '返回检查' : '继续检查',
      type: 'warning'
    }).catch(() => false);
    if (!confirmed) {
      if (check.firstUnansweredQuestionOrder) await go(check.firstUnansweredQuestionOrder);
      return;
    }
    submitting.value = true;
    await leaveCurrentQuestion(true);
    const result = await finishDiagnostic(sessionId.value, { expectedSessionVersion: check.sessionVersion }, crypto.randomUUID());
    if (!result.data) throw new Error('交卷状态未确认。');
    leavingForHome = true;
    await router.replace({ path: '/learning/diagnostic/result', query: { sessionId: sessionId.value } });
  } catch (error) {
    submitting.value = false;
    if (!(await handleDiagnosticError(error))) ElMessage.error('正在确认交卷结果，请勿重复操作。');
  }
}

function onVisibilityChange() {
  isVisible.value = !document.hidden;
  if (document.hidden) void sendTimerEvent('HIDDEN', true);
  else void enterCurrentQuestion();
}

function onBeforeUnload(event: BeforeUnloadEvent) {
  if (pendingDraft || failedDraft || saving.value) {
    event.preventDefault();
    event.returnValue = '';
  }
  void leaveCurrentQuestion(true);
}

onMounted(() => {
  document.addEventListener('visibilitychange', onVisibilityChange);
  window.addEventListener('beforeunload', onBeforeUnload);
});
onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', onVisibilityChange);
  window.removeEventListener('beforeunload', onBeforeUnload);
  stopHeartbeat();
  void leaveCurrentQuestion(true);
});
onBeforeRouteLeave(async () => {
  if (leavingForHome || !session.value || replaced.value) return true;
  const confirmed = await ElMessageBox.confirm('离开前将保存已确认的进度；下次可继续完成首次诊断。确认离开吗？', '离开首次诊断', {
    confirmButtonText: '确认离开',
    cancelButtonText: '继续答题',
    type: 'warning'
  }).catch(() => false);
  if (!confirmed || !(await flushSave())) return false;
  await leaveCurrentQuestion(true);
  try {
    const response = await pauseDiagnostic(
      sessionId.value,
      { currentQuestionOrder: order.value, expectedSessionVersion: session.value.sessionVersion },
      crypto.randomUUID()
    );
    if (response.data) replaceNavigation(response.data);
    return true;
  } catch (error) {
    if (!(await handleDiagnosticError(error))) ElMessage.error('离开前保存进度失败，请稍后重试。');
    return false;
  }
});

void load();
</script>
