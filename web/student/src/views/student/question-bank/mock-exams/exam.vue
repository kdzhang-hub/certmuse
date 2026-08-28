<template>
  <div v-loading="loading" class="formal-exam-page">
    <el-card v-if="session" class="exam-header" shadow="never">
      <div class="exam-header__content">
        <div>
          <span>模拟考试 · 共 {{ session.totalCount }} 题</span>
          <h2>{{ session.title }}</h2>
          <p>已答 {{ session.answeredCount }} 题 · 未答 {{ session.unansweredCount }} 题 · {{ saveText }}</p>
        </div>
        <div>
          <el-button text :disabled="saving" @click="exit">暂存并退出</el-button>
          <el-button :disabled="saving || paused" @click="pause">暂停考试</el-button>
        </div>
      </div>
    </el-card>
    <div v-if="session && item" class="exam-body">
      <main>
        <choice-question
          v-if="item.questionType === 'CHOICE'"
          :question-order="order"
          :total-count="session.totalCount"
          selection-mode="single"
          :stem="item.stem"
          :options="item.options"
          :images="item.images"
          :answer="choiceValue"
          :disabled="disabled"
          @update:answer="choiceValue = $event"
        >
          <template #actions>
            <el-button :disabled="order === 1 || disabled" @click="go(order - 1)">上一题</el-button>
            <el-button v-if="saveFailed" type="warning" :loading="saving" @click="retrySave">重新保存</el-button>
            <el-button
              type="primary"
              :disabled="disabled"
              @click="order === session.totalCount ? openFinish() : go(order + 1)"
            >
              {{ order === session.totalCount ? '交卷检查' : '下一题' }}
            </el-button>
          </template>
        </choice-question>
        <el-card v-else shadow="never" class="subjective-card">
          <div class="question-meta">
            <span>{{ item.questionType === 'CASE' ? '案例题' : '论文题' }}</span>
            <span>第 {{ order }} / {{ session.totalCount }} 题</span>
          </div>
          <h3>{{ formatQuestionStem(item.stem) }}</h3>
          <question-images :images="item.images" />
          <el-input v-model="textValue" type="textarea" :rows="16" :disabled="disabled" placeholder="请输入你的答案" />
          <div class="actions">
            <el-button :disabled="order === 1 || disabled" @click="go(order - 1)">上一题</el-button>
            <el-button v-if="saveFailed" type="warning" :loading="saving" @click="retrySave">重新保存</el-button>
            <el-button
              type="primary"
              :disabled="disabled"
              @click="order === session.totalCount ? openFinish() : go(order + 1)"
            >
              {{ order === session.totalCount ? '交卷检查' : '下一题' }}
            </el-button>
          </div>
        </el-card>
      </main>
      <aside>
        <section class="exam-timer-panel" :class="{ 'is-overtime': isOvertime }" aria-label="考试计时">
          <span class="exam-timer-panel__label">{{ timerLabel }}时间</span>
          <strong>{{ formattedTimer }}</strong>
          <small>{{ isOvertime ? '请尽快交卷' : '考试进行中' }}</small>
        </section>
        <exam-answer-card
          :total-count="session.totalCount"
          :answered-count="session.answeredCount"
          :current-question-order="order"
          :navigation="session.navigation"
          :answered="answeredStates"
          :disabled="disabled"
          finish-label="交卷检查"
          @select="go"
          @finish="openFinish"
        />
      </aside>
    </div>
    <el-dialog
      v-model="paused"
      width="420px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
    >
      <div class="pause-content">
        <h3>考试已暂停</h3>
        <p>答题与有效计时已经停止。</p>
        <el-button type="primary" size="large" :loading="saving" @click="resume">继续答题</el-button>
        <el-button text @click="leaveToList">返回模拟试卷</el-button>
      </div>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router';
import type {
  FormalExamItemVo,
  FormalExamSessionVo,
  FormalExamTimerRequest
} from '@/api/certmuse/assessment/formal-exam-types';
import {
  finishSimulation,
  getSimulationFinishCheck,
  getSimulationItem,
  getSimulationSession,
  pauseSimulation,
  saveSimulationDraft,
  sendSimulationTimer
} from '@/api/certmuse/assessment/simulations';
import ChoiceQuestion from '@/components/exam/ChoiceQuestion.vue';
import ExamAnswerCard from '@/components/exam/ExamAnswerCard.vue';
import QuestionImages from '@/components/exam/QuestionImages.vue';
import { useExamCountdown } from '@/hooks/exam/useExamCountdown';
import { formatQuestionStem } from '@/utils/question-image';
const route = useRoute(),
  router = useRouter(),
  sessionId = String(route.query.sessionId ?? '');
const loading = ref(true),
  saving = ref(false),
  saveFailed = ref(false),
  hydrating = ref(false),
  submitting = ref(false),
  session = ref<FormalExamSessionVo>(),
  item = ref<FormalExamItemVo>(),
  order = ref(1),
  choiceValue = ref<string[]>([]),
  textValue = ref(''),
  paused = ref(false),
  leaseId = ref<string | null>(null),
  visible = ref(!document.hidden),
  replaced = ref(false),
  leaving = ref(false);
let saveTimer: ReturnType<typeof setTimeout> | undefined, heartbeat: ReturnType<typeof setInterval> | undefined;
const disabled = computed(() => saving.value || paused.value || replaced.value || submitting.value),
  answeredStates = computed(() =>
    Object.fromEntries((session.value?.navigation ?? []).map(v => [v.questionOrder, v.state === 'ANSWERED']))
  ),
  saveText = computed(() => (saving.value ? '保存中' : saveFailed.value ? '保存失败' : '已保存')),
  shouldTick = computed(() => Boolean(leaseId.value) && visible.value && !paused.value && !replaced.value);
const {
  start: startCountdown,
  sync: syncCountdown,
  isOvertime,
  label: timerLabel,
  formatted: formattedTimer
} = useExamCountdown(
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
watch(
  [choiceValue, textValue],
  () => {
    if (!hydrating.value) {
      clearTimeout(saveTimer);
      saveTimer = setTimeout(() => void flushSave(), 5000);
    }
  },
  { deep: true }
);
function accept(value: FormalExamSessionVo) {
  session.value = value;
  syncCountdown(value);
}
async function load(target = order.value) {
  if (!sessionId) return router.replace('/learning/question-bank/mock-exams');
  loading.value = true;
  try {
    const [s, q] = await Promise.all([getSimulationSession(sessionId), getSimulationItem(sessionId, target)]);
    if (!s.data || !q.data) throw new Error('考试数据缺失');
    accept(s.data);
    item.value = q.data;
    order.value = target;
    hydrating.value = true;
    choiceValue.value = [...q.data.choiceValue];
    textValue.value = q.data.textValue ?? '';
    hydrating.value = false;
  } finally {
    loading.value = false;
  }
}
async function timerEvent(eventType: FormalExamTimerRequest['eventType']) {
  if (!session.value) return;
  const r = await sendSimulationTimer(sessionId, {
    eventType,
    questionOrder: order.value,
    leaseId: leaseId.value,
    expectedSessionVersion: session.value.sessionVersion
  });
  if (!r.data) return;
  leaseId.value = r.data.leaseId;
  replaced.value = r.data.timerState === 'REPLACED';
  syncCountdown(r.data);
  if (r.data.effectiveElapsedSeconds >= r.data.estimatedDurationSeconds) {
    leaving.value = true;
    await router.replace(`/learning/question-bank/mock-exams/result?sessionId=${sessionId}`);
    return;
  }
  if (replaced.value) {
    paused.value = true;
    ElMessage.warning('考试已在其他窗口或设备继续');
  }
}
async function flushSave(currentQuestionOrder = order.value) {
  clearTimeout(saveTimer);
  if (!session.value || !item.value || saving.value) return !saveFailed.value;
  saving.value = true;
  try {
    const r = await saveSimulationDraft(sessionId, order.value, {
      answer: {
        choiceValue: item.value.questionType === 'CHOICE' ? [...choiceValue.value] : null,
        textValue: item.value.questionType === 'CHOICE' ? null : textValue.value
      },
      expectedSessionVersion: session.value.sessionVersion,
      currentQuestionOrder
    });
    if (r.data) accept(r.data);
    saveFailed.value = false;
    return true;
  } catch {
    saveFailed.value = true;
    return false;
  } finally {
    saving.value = false;
  }
}
async function retrySave() {
  await flushSave();
}
async function go(target: number) {
  if (disabled.value || !session.value || target < 1 || target > session.value.totalCount) return;
  if (!(await flushSave(target))) return;
  await timerEvent('LEAVE');
  await load(target);
  await timerEvent('ENTER');
}
async function pause() {
  if (!(await flushSave()) || !session.value) return;
  await timerEvent('HIDDEN');
  const r = await pauseSimulation(sessionId, {
    currentQuestionOrder: order.value,
    expectedSessionVersion: session.value.sessionVersion
  });
  if (r.data) accept(r.data);
  paused.value = true;
}
async function resume() {
  paused.value = false;
  replaced.value = false;
  await load(order.value);
  await timerEvent('ENTER');
}
async function exit() {
  await pause();
  leaving.value = true;
  await router.push('/learning/question-bank/mock-exams');
}
async function openFinish() {
  if (!(await flushSave()) || !session.value) return;
  const r = await getSimulationFinishCheck(sessionId);
  if (!r.data) return;
  await ElMessageBox.confirm(`还有 ${r.data.unansweredCount} 题未作答，确认交卷吗？`, '交卷确认', {
    type: 'warning',
    confirmButtonText: '确认交卷'
  });
  submitting.value = true;
  try {
    const finished = await finishSimulation(sessionId, { expectedSessionVersion: r.data.sessionVersion });
    leaving.value = true;
    await router.replace(
      finished.data?.resultPath ?? `/learning/question-bank/mock-exams/result?sessionId=${sessionId}`
    );
  } finally {
    submitting.value = false;
  }
}
const leaveToList = async () => {
  leaving.value = true;
  await router.push('/learning/question-bank/mock-exams');
};
async function visibility() {
  visible.value = !document.hidden;
  if (document.hidden && leaseId.value) {
    await flushSave();
    await timerEvent('HIDDEN');
    paused.value = true;
  }
}
onBeforeRouteLeave(async () => {
  if (!leaving.value && session.value?.status === 'IN_PROGRESS') {
    await flushSave();
    await timerEvent('LEAVE');
  }
  return true;
});
onMounted(async () => {
  await load();
  await timerEvent('ENTER');
  startCountdown();
  heartbeat = setInterval(() => {
    if (shouldTick.value) void timerEvent('HEARTBEAT');
  }, 10000);
  document.addEventListener('visibilitychange', visibility);
});
onBeforeUnmount(() => {
  clearTimeout(saveTimer);
  if (heartbeat) clearInterval(heartbeat);
  document.removeEventListener('visibilitychange', visibility);
});
</script>
<style scoped lang="scss">
.formal-exam-page {
  min-height: 100vh;
  padding: 8px;
}
.exam-header {
  border-color: var(--el-border-color-light);
}
.exam-header__content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}
.exam-header span {
  color: var(--el-color-primary);
  font-size: 13px;
  font-weight: 600;
}
.exam-header h2 {
  margin: 8px 0;
  font-size: 18px;
}
.exam-header p {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.exam-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 16px;
  margin-top: 16px;
  align-items: start;
}
.exam-body aside {
  position: sticky;
  top: 16px;
}
.exam-timer-panel {
  display: grid;
  gap: 4px;
  margin-bottom: 12px;
  padding: 18px 20px;
  border: 1px solid var(--el-color-primary-light-5);
  border-radius: 10px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary-dark-2);
}
.exam-timer-panel__label,
.exam-timer-panel small {
  font-size: 13px;
  font-weight: 600;
}
.exam-timer-panel strong {
  font-size: 32px;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.04em;
  line-height: 1.1;
}
.exam-timer-panel small {
  color: var(--el-text-color-secondary);
  font-weight: 400;
}
.exam-timer-panel.is-overtime {
  border-color: var(--el-color-danger-light-5);
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger-dark-2);
}
:global(html.dark .exam-timer-panel) {
  border-color: rgba(96, 165, 250, 0.58);
  background: rgba(30, 64, 175, 0.2);
  color: #dbeafe;
}
:global(html.dark .exam-timer-panel small) {
  color: #bfd4ed;
}
:global(html.dark .exam-timer-panel.is-overtime) {
  border-color: rgba(248, 113, 113, 0.62);
  background: rgba(127, 29, 29, 0.28);
  color: #fecaca;
}
:global(html.dark .exam-timer-panel.is-overtime small) {
  color: #fecaca;
}
.subjective-card h3 {
  margin: 22px 0;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.8;
}
.question-meta,
.actions {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}
.question-meta {
  padding-bottom: 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  color: var(--el-text-color-secondary);
}
.actions {
  justify-content: flex-end;
  margin-top: 20px;
}
.pause-content {
  text-align: center;
}
.pause-content p {
  color: var(--el-text-color-secondary);
}
@media (max-width: 992px) {
  .exam-body {
    grid-template-columns: 1fr;
  }
  .exam-body aside {
    position: static;
  }
}
@media (max-width: 576px) {
  .exam-header__content {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
