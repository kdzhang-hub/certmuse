<template>
  <div v-loading="loading" class="p-2 app-container mistake-correction-answering">
    <el-result v-if="loadError" icon="warning" title="错题订正会话不可用" :sub-title="loadError">
      <template #extra><el-button type="primary" @click="returnToMistakes">返回错题复习</el-button></template>
    </el-result>

    <el-result
      v-else-if="session?.sessionStatus === 'COMPLETED'"
      icon="success"
      title="错题订正已结束"
      sub-title="已提交的答案已经保存；答对的题目已移入“已订正”。"
    >
      <template #extra><el-button type="primary" @click="returnToMistakes">返回错题复习</el-button></template>
    </el-result>

    <focus-exam-shell
      v-else-if="session && currentQuestion"
      mode="PRACTICE"
      title="错题订正"
      description="请独立重做原题。答对后，这道题会移入“已订正”；答错仍保留在“待订正”。"
      :total-count="session.totalCount"
      :answered-count="session.submittedCount"
      :unanswered-count="session.totalCount - session.submittedCount"
      timer-label=""
      formatted-timer=""
      :is-overtime="false"
      :paused="false"
      :saving="submitting || switchingQuestion"
      :save-text="saveText"
      :save-state="saveState"
      exit-label="退出订正"
      @exit="requestExit"
    >
      <choice-question
        :question-order="currentQuestion.questionOrder"
        :total-count="session.totalCount"
        :selection-mode="currentQuestion.question.selectionMode"
        :stem="currentQuestion.question.stem"
        :options="currentQuestion.question.options"
        :images="currentQuestion.question.images"
        :answer="choiceAnswer"
        :disabled="Boolean(currentQuestion.submission) || submitting || switchingQuestion"
        @update:answer="updateChoiceAnswer"
      >
        <template #actions>
          <el-button :disabled="currentQuestion.questionOrder === 1 || submitting || switchingQuestion" @click="goTo(currentQuestion.questionOrder - 1)">
            上一题
          </el-button>
          <el-button v-if="!currentQuestion.submission" type="primary" :disabled="!choiceAnswer.length || switchingQuestion" :loading="submitting" @click="submitCurrent">
            提交本题
          </el-button>
          <el-button v-else type="primary" :disabled="switchingQuestion" @click="goNext">
            {{ currentQuestion.questionOrder === session.totalCount ? '结束订正' : '下一题' }}
          </el-button>
        </template>
      </choice-question>

      <section v-if="currentQuestion.submission" class="mistake-correction-answering__feedback" aria-live="polite">
        <el-alert
          :type="currentQuestion.submission.correct ? 'success' : 'error'"
          :title="currentQuestion.submission.correct ? '回答正确，已移入“已订正”。' : `回答错误，正确答案：${currentQuestion.submission.correctOptionLabels.join('、')}`"
          :closable="false"
          show-icon
        />
        <el-card v-if="currentQuestion.submission.analysis" shadow="never" class="mistake-correction-answering__analysis">
          <template #header><strong>解析</strong></template>
          <p>{{ currentQuestion.submission.analysis }}</p>
        </el-card>
      </section>

      <template #navigation>
        <exam-answer-card
          :total-count="session.totalCount"
          :answered-count="session.submittedCount"
          :current-question-order="currentQuestion.questionOrder"
          :navigation="session.navigation"
          :answered="answeredStates"
          :answer-results="answerResults"
          show-answer-results
          :disabled="submitting || switchingQuestion"
          finish-label="结束订正"
          @select="goTo"
          @finish="requestFinish"
        />
      </template>
    </focus-exam-shell>

    <el-skeleton v-else-if="loading" :rows="10" animated />
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  completeMistakeCorrectionSession,
  getMistakeCorrectionItem,
  getMistakeCorrectionSession,
  submitMistakeCorrectionItem,
  type MistakeCorrectionItemVo,
  type MistakeCorrectionSessionVo
} from '@/api/certmuse/learning/mistakes';
import ChoiceQuestion from '@/components/exam/ChoiceQuestion.vue';
import ExamAnswerCard from '@/components/exam/ExamAnswerCard.vue';
import FocusExamShell from '@/components/exam/FocusExamShell.vue';
import { contractErrorFrom } from '@/utils/learning-goal';

const mistakePath = '/learning/mistakes';
const route = useRoute();
const router = useRouter();
const loading = ref(true);
const switchingQuestion = ref(false);
const submitting = ref(false);
const loadError = ref('');
const session = ref<MistakeCorrectionSessionVo>();
const currentQuestion = ref<MistakeCorrectionItemVo>();
const choiceAnswers = reactive<Record<number, string[]>>({});
const saveText = ref('');
const saveState = ref<'saved' | 'saving' | 'failed'>('saved');
let itemRequestSequence = 0;

const sessionId = computed(() => typeof route.query.sessionId === 'string' ? route.query.sessionId : '');
const choiceAnswer = computed(() => currentQuestion.value ? (choiceAnswers[currentQuestion.value.questionOrder] ?? []) : []);
const answeredStates = computed(() => Object.fromEntries((session.value?.navigation ?? []).map(item => [item.questionOrder, item.state !== 'UNANSWERED'])));
const answerResults = computed(() => Object.fromEntries(
  (session.value?.navigation ?? [])
    .filter(item => item.state !== 'UNANSWERED')
    .map(item => [item.questionOrder, item.state === 'SUBMITTED_CORRECT' ? 'correct' : 'incorrect'] as const)
));

watch(sessionId, () => void initialize(), { immediate: true });

async function initialize() {
  loading.value = true;
  loadError.value = '';
  currentQuestion.value = undefined;
  saveText.value = '';
  if (!sessionId.value) {
    session.value = undefined;
    loadError.value = '缺少错题订正会话编号，请从错题列表重新开始。';
    loading.value = false;
    return;
  }
  try {
    const response = await getMistakeCorrectionSession(sessionId.value);
    if (!response.data) throw new Error('未取得错题订正会话数据。');
    session.value = response.data;
    if (response.data.sessionStatus === 'COMPLETED') return;
    const firstUnanswered = response.data.navigation.find(item => item.state === 'UNANSWERED')?.questionOrder;
    await loadQuestion(firstUnanswered ?? response.data.navigation[0]?.questionOrder ?? 1);
  } catch (error) {
    loadError.value = messageForError(error, '无法加载本次错题订正，请从错题列表重新开始。');
  } finally {
    loading.value = false;
  }
}

async function loadQuestion(questionOrder: number) {
  if (!sessionId.value || !session.value || switchingQuestion.value) return;
  const requestSequence = ++itemRequestSequence;
  switchingQuestion.value = true;
  try {
    const response = await getMistakeCorrectionItem(sessionId.value, questionOrder);
    if (!response.data) throw new Error('未取得原题快照。');
    if (requestSequence !== itemRequestSequence) return;
    currentQuestion.value = response.data;
    choiceAnswers[questionOrder] = response.data.submission ? [...response.data.submission.selectedOptionLabels] : choiceAnswers[questionOrder] ?? [];
  } catch (error) {
    await handleCorrectionError(error);
  } finally {
    if (requestSequence === itemRequestSequence) switchingQuestion.value = false;
  }
}

function goTo(questionOrder: number) {
  if (!session.value || questionOrder < 1 || questionOrder > session.value.totalCount) return;
  void loadQuestion(questionOrder);
}

function goNext() {
  if (!currentQuestion.value || !session.value) return;
  if (currentQuestion.value.questionOrder === session.value.totalCount) void requestFinish();
  else goTo(currentQuestion.value.questionOrder + 1);
}

function updateChoiceAnswer(value: string[]) {
  if (!currentQuestion.value || currentQuestion.value.submission) return;
  choiceAnswers[currentQuestion.value.questionOrder] = value;
  saveText.value = '';
}

async function submitCurrent() {
  const question = currentQuestion.value;
  if (!question || question.submission || !choiceAnswer.value.length || submitting.value) return;
  submitting.value = true;
  saveText.value = '提交中';
  saveState.value = 'saving';
  try {
    const response = await submitMistakeCorrectionItem(
      sessionId.value,
      question.questionOrder,
      { answer: { value: [...choiceAnswer.value] } },
      crypto.randomUUID().toLowerCase()
    );
    if (!response.data) throw new Error('提交失败，请重试。');
    if (currentQuestion.value?.questionOrder === question.questionOrder) {
      currentQuestion.value = { ...question, submission: response.data.feedback };
    }
    if (session.value) {
      session.value = {
        ...session.value,
        submittedCount: response.data.submittedCount,
        navigation: session.value.navigation.map(item => item.questionOrder === question.questionOrder
          ? { ...item, state: response.data!.feedback.correct ? 'SUBMITTED_CORRECT' : 'SUBMITTED_INCORRECT' }
          : item)
      };
    }
    saveText.value = '已提交';
    saveState.value = 'saved';
  } catch (error) {
    saveText.value = '提交失败';
    saveState.value = 'failed';
    await handleCorrectionError(error);
  } finally {
    submitting.value = false;
  }
}

async function requestExit() {
  const confirmed = await ElMessageBox.confirm(
    '已提交的答案会保留；当前未提交的选择不会保存。确认退出订正吗？',
    '退出订正',
    { confirmButtonText: '退出订正', cancelButtonText: '继续答题', type: 'warning' }
  ).then(() => true).catch(() => false);
  if (confirmed) await completeSession();
}

async function requestFinish() {
  const unansweredCount = (session.value?.totalCount ?? 0) - (session.value?.submittedCount ?? 0);
  const confirmed = await ElMessageBox.confirm(
    unansweredCount ? `还有 ${unansweredCount} 道题未提交，结束后不会保存这些选择。确认结束订正吗？` : '本次题目均已提交，确认结束订正吗？',
    '结束订正',
    { confirmButtonText: '结束订正', cancelButtonText: '继续答题', type: 'warning' }
  ).then(() => true).catch(() => false);
  if (confirmed) await completeSession();
}

async function completeSession() {
  if (!sessionId.value || submitting.value) return;
  submitting.value = true;
  saveText.value = '正在结束订正';
  saveState.value = 'saving';
  try {
    const response = await completeMistakeCorrectionSession(sessionId.value, crypto.randomUUID().toLowerCase());
    await router.replace(response.data?.returnPath ?? mistakePath);
  } catch (error) {
    saveText.value = '结束失败';
    saveState.value = 'failed';
    await handleCorrectionError(error);
  } finally {
    submitting.value = false;
  }
}

async function handleCorrectionError(error: unknown) {
  const contractError = contractErrorFrom(error) as ReturnType<typeof contractErrorFrom> & { details?: { sessionId?: string } };
  switch (contractError?.errorCode) {
    case 'MISTAKE_CORRECTION_ITEM_ALREADY_SUBMITTED':
      ElMessage.warning('该题已在其他设备提交，已同步最新结果。');
      if (currentQuestion.value) await loadQuestion(currentQuestion.value.questionOrder);
      return;
    case 'MISTAKE_CORRECTION_IN_PROGRESS':
      if (typeof contractError.details?.sessionId === 'string') {
        await router.replace({ path: '/learning/session/correction', query: { sessionId: contractError.details.sessionId } });
      }
      return;
    case 'MISTAKE_CORRECTION_SESSION_NOT_ACTIVE':
    case 'MISTAKE_CORRECTION_SESSION_NOT_FOUND':
      ElMessage.warning('本次错题订正已结束或不可用。');
      await router.replace(mistakePath);
      return;
    case 'QUESTION_NOT_FOUND':
    case 'MISTAKE_NOT_PENDING_CORRECTION':
      ElMessage.warning('所选错题状态已变化，已返回列表刷新。');
      await router.replace(mistakePath);
      return;
    case 'LEARNING_GOAL_NOT_ACTIVE':
      await router.replace('/learning/goal-required');
      return;
    default:
      ElMessage.error(messageForError(error, '操作失败，请稍后重试。'));
  }
}

function messageForError(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback;
}

function returnToMistakes() {
  void router.replace(session.value?.returnPath ?? mistakePath);
}
</script>

<style scoped lang="scss">
.mistake-correction-answering { width: 100%; max-width: none; margin: 0; }
.mistake-correction-answering__feedback { display: grid; gap: 12px; margin-top: 16px; }
.mistake-correction-answering__analysis { border-color: var(--el-border-color-light); background: var(--el-bg-color); }
.mistake-correction-answering__analysis p { margin: 0; color: var(--el-text-color-regular); line-height: 1.8; white-space: pre-line; }
</style>
