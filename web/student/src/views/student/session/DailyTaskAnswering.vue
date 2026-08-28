<template>
  <div v-loading="loading" class="p-2 app-container daily-task-answering">
    <el-result v-if="loadError" icon="warning" title="今日任务不可用" :sub-title="loadError">
      <template #extra><el-button type="primary" @click="router.replace('/learning/tasks')">返回今日任务</el-button></template>
    </el-result>
    <el-result v-else-if="session?.sessionStatus === 'COMPLETED'" icon="success" title="任务已完成" sub-title="本次作答已保存。">
      <template #extra><el-button type="primary" @click="router.replace('/learning/tasks')">返回今日任务</el-button></template>
    </el-result>
    <el-result v-else-if="session?.sessionStatus === 'ALL_SUBMITTED'" icon="success" title="题目已全部提交" sub-title="确认后完成今日任务。">
      <template #extra><el-button type="primary" :loading="submitting" @click="completeTask">完成任务</el-button></template>
    </el-result>
    <focus-exam-shell
      v-else-if="session && currentQuestion"
      mode="PRACTICE"
      :title="`${session.taskTitle} · 题目训练`"
      description="逐题提交后即可查看结果与解析，全部提交后才能完成任务。"
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
      exit-label="暂存并退出"
      @exit="router.push('/learning/tasks')"
    >
      <template #steps><task-session-steps phase="practice" /></template>
      <choice-question
        :question-order="currentQuestion.questionOrder"
        :total-count="session.totalCount"
        selection-mode="single"
        :stem="currentQuestion.question.stem"
        :options="currentQuestion.question.options"
        :images="currentQuestion.question.images"
        :answer="choiceAnswer"
        :disabled="Boolean(currentQuestion.submission) || submitting || switchingQuestion"
        @update:answer="updateChoiceAnswer"
      >
        <template #actions>
          <el-button :disabled="currentQuestion.questionOrder === 1 || switchingQuestion" @click="goTo(currentQuestion.questionOrder - 1)">上一题</el-button>
          <el-button v-if="!currentQuestion.submission" type="primary" :disabled="!choiceAnswer.length || switchingQuestion" :loading="submitting" @click="submitCurrent">提交本题</el-button>
          <el-button v-else type="primary" :disabled="switchingQuestion" @click="goNext">{{ currentQuestion.questionOrder === session.totalCount ? '完成任务' : '下一题' }}</el-button>
        </template>
      </choice-question>
      <section v-if="currentQuestion.submission" class="daily-task-answering__feedback">
        <el-alert :type="currentQuestion.submission.correct ? 'success' : 'error'" :title="currentQuestion.submission.correct ? '回答正确' : `回答错误，正确答案：${currentQuestion.submission.correctOptionLabels.join('、')}`" :closable="false" show-icon />
        <el-card v-if="currentQuestion.submission.analysis" shadow="never"><template #header><strong>解析</strong></template><p>{{ currentQuestion.submission.analysis }}</p></el-card>
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
          finish-label="完成任务"
          :show-finish="false"
          @select="goTo"
          @finish="completeTask"
        />
      </template>
    </focus-exam-shell>
    <el-skeleton v-else-if="loading" :rows="10" animated />
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { computed, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { completeDailyTask, getDailyTaskItem, getDailyTaskSession, submitDailyTaskItem, type DailyTaskErrorVo, type DailyTaskItemVo, type DailyTaskSessionVo } from '@/api/certmuse/assessment/daily-tasks';
import ChoiceQuestion from '@/components/exam/ChoiceQuestion.vue';
import ExamAnswerCard from '@/components/exam/ExamAnswerCard.vue';
import FocusExamShell from '@/components/exam/FocusExamShell.vue';
import { contractErrorFrom } from '@/utils/learning-goal';
import TaskSessionSteps from './TaskSessionSteps.vue';

const route = useRoute();
const router = useRouter();
const loading = ref(true);
const submitting = ref(false);
const switchingQuestion = ref(false);
const loadError = ref('');
const saveText = ref('');
const saveState = ref<'saved' | 'saving' | 'failed'>('saved');
const session = ref<DailyTaskSessionVo>();
const currentQuestion = ref<DailyTaskItemVo>();
const choiceAnswers = reactive<Record<number, string[]>>({});
let itemRequestSequence = 0;
const sessionId = computed(() => typeof route.query.sessionId === 'string' ? route.query.sessionId : '');
const choiceAnswer = computed(() => currentQuestion.value ? choiceAnswers[currentQuestion.value.questionOrder] ?? [] : []);
const answeredStates = computed(() => Object.fromEntries((session.value?.navigation ?? []).map(item => [item.questionOrder, item.state !== 'UNANSWERED'])));
const answerResults = computed(() => Object.fromEntries(
  (session.value?.navigation ?? [])
    .filter(item => item.state !== 'UNANSWERED')
    .map(item => [item.questionOrder, item.state === 'SUBMITTED_CORRECT' ? 'correct' : 'incorrect'] as const)
));

watch(sessionId, () => void initialize(), { immediate: true });

async function initialize() {
  loading.value = true; loadError.value = ''; currentQuestion.value = undefined;
  if (!sessionId.value) { loadError.value = '缺少题目训练会话，请从今日任务重新开始。'; loading.value = false; return; }
  try {
    const response = await getDailyTaskSession(sessionId.value);
    if (!response.data) throw new Error('未取得任务会话数据。');
    session.value = response.data;
    if (response.data.sessionStatus !== 'COMPLETED') {
      const first = response.data.navigation.find(item => item.state === 'UNANSWERED')?.questionOrder ?? response.data.navigation[0]?.questionOrder;
      if (first) await loadQuestion(first);
    }
  } catch (error) { loadError.value = messageForError(error, '无法加载今日任务，请返回任务列表后重试。'); }
  finally { loading.value = false; }
}

async function loadQuestion(questionOrder: number) {
  if (!sessionId.value || !session.value || switchingQuestion.value || session.value.sessionStatus !== 'IN_PROGRESS') return;
  const sequence = ++itemRequestSequence; switchingQuestion.value = true;
  try {
    const response = await getDailyTaskItem(sessionId.value, questionOrder);
    if (!response.data) throw new Error('未取得题目数据。');
    if (sequence !== itemRequestSequence) return;
    currentQuestion.value = response.data;
    choiceAnswers[questionOrder] = response.data.submission ? [...response.data.submission.selectedOptionLabels] : choiceAnswers[questionOrder] ?? [];
  } catch (error) { await handleError(error); }
  finally { if (sequence === itemRequestSequence) switchingQuestion.value = false; }
}

function goTo(questionOrder: number) { if (session.value && questionOrder >= 1 && questionOrder <= session.value.totalCount) void loadQuestion(questionOrder); }
function goNext() { if (!currentQuestion.value || !session.value) return; if (session.value.sessionStatus === 'ALL_SUBMITTED') void completeTask(); else goTo(currentQuestion.value.questionOrder + 1); }
function updateChoiceAnswer(value: string[]) { if (currentQuestion.value && !currentQuestion.value.submission) { choiceAnswers[currentQuestion.value.questionOrder] = value; saveText.value = ''; } }

async function submitCurrent() {
  const question = currentQuestion.value;
  if (!question || question.submission || !choiceAnswer.value.length || submitting.value) return;
  submitting.value = true; saveText.value = '提交中'; saveState.value = 'saving';
  try {
    const response = await submitDailyTaskItem(sessionId.value, question.questionOrder, [...choiceAnswer.value], crypto.randomUUID().toLowerCase());
    if (!response.data) throw new Error('提交失败，请重试。');
    if (currentQuestion.value?.questionOrder === question.questionOrder) currentQuestion.value = { ...question, submission: response.data.submission };
    if (session.value) {
      const allSubmitted = response.data.submittedCount === session.value.totalCount;
      session.value = { ...session.value, submittedCount: response.data.submittedCount, sessionStatus: allSubmitted ? 'ALL_SUBMITTED' : 'IN_PROGRESS', navigation: session.value.navigation.map(item => item.questionOrder === question.questionOrder ? { ...item, state: response.data!.submission.correct ? 'SUBMITTED_CORRECT' : 'SUBMITTED_INCORRECT' } : item) };
    }
    saveText.value = '已提交'; saveState.value = 'saved';
  } catch (error) { saveText.value = '提交失败'; saveState.value = 'failed'; await handleError(error); }
  finally { submitting.value = false; }
}

async function completeTask() {
  if (!sessionId.value || !session.value || session.value.sessionStatus !== 'ALL_SUBMITTED' || submitting.value) return;
  submitting.value = true; saveText.value = '正在完成任务'; saveState.value = 'saving';
  try { await completeDailyTask(sessionId.value, crypto.randomUUID().toLowerCase()); await router.replace('/learning/tasks'); }
  catch (error) { saveText.value = '完成失败'; saveState.value = 'failed'; await handleError(error); }
  finally { submitting.value = false; }
}

async function handleError(error: unknown) {
  const contractError = contractErrorFrom(error) as DailyTaskErrorVo | undefined;
  if (contractError?.errorCode === 'TASK_ITEM_ALREADY_SUBMITTED') { ElMessage.warning('该题已在其他设备提交，正在同步结果。'); if (currentQuestion.value) await loadQuestion(currentQuestion.value.questionOrder); return; }
  if (contractError?.errorCode === 'TASK_SESSION_NOT_FOUND') { loadError.value = '任务会话不存在或无权访问。'; return; }
  if (contractError?.errorCode === 'TASK_NOT_ALL_SUBMITTED') { ElMessage.warning('请先提交全部题目。'); await initialize(); return; }
  ElMessage.error(messageForError(error, '操作失败，请稍后重试。'));
}
function messageForError(error: unknown, fallback: string) { return error instanceof Error && error.message ? error.message : fallback; }
</script>

<style scoped lang="scss">
.daily-task-answering { width: 100%; max-width: none; margin: 0; }
.daily-task-answering :deep(.focus-exam-shell) { min-height: auto; padding: 0; }
.daily-task-answering :deep(.focus-exam-shell__header) { overflow: hidden; border: 0; border-radius: 14px; box-shadow: 0 10px 28px rgb(31 55 85 / 8%); }
.daily-task-answering :deep(.focus-exam-shell__header-content) { padding: 8px 10px; }
.daily-task-answering :deep(.focus-exam-shell__eyebrow) { font-weight: 800; letter-spacing: .04em; }
.daily-task-answering :deep(.focus-exam-shell h2) { color: var(--el-text-color-primary); font-size: 20px; }
.daily-task-answering :deep(.focus-exam-shell__body) { grid-template-columns: minmax(0, 1fr) 280px; gap: 20px; margin-top: 20px; }
.daily-task-answering :deep(.choice-question) { border: 0; border-radius: 14px; box-shadow: 0 10px 28px rgb(31 55 85 / 8%); }
.daily-task-answering :deep(.choice-question__number) { box-shadow: 0 4px 10px rgb(64 158 255 / 25%); }
.daily-task-answering :deep(.exam-answer-card) { border: 0; border-radius: 14px; box-shadow: 0 10px 28px rgb(31 55 85 / 8%); }
.daily-task-answering__feedback { display: grid; gap: 12px; margin-top: 16px; }
.daily-task-answering__feedback :deep(.el-alert--success) { border: 1px solid var(--el-color-success-light-5); background: var(--el-color-success-light-9); }
.daily-task-answering__feedback :deep(.el-alert--error) { border: 1px solid var(--el-color-danger-light-5); background: var(--el-color-danger-light-9); }
.daily-task-answering__feedback :deep(.el-card) { border-color: var(--el-border-color-light); }
.daily-task-answering__feedback p { margin: 0; line-height: 1.8; white-space: pre-line; }
@media (max-width: 992px) { .daily-task-answering :deep(.focus-exam-shell__body) { grid-template-columns: 1fr; } }
</style>
