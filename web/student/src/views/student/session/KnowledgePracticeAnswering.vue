<template>
  <div v-loading="loading" class="p-2 app-container knowledge-practice-answering">
    <el-card v-if="reinforcementResult" shadow="never" class="reinforcement-result">
      <el-result
        icon="success"
        title="本轮强化已完成"
        :sub-title="`答对 ${reinforcementResult.correctCount}/${reinforcementResult.actualCount} 题，正确率 ${reinforcementResult.correctRate}%`"
      />
      <div class="reinforcement-result__items">
        <el-tag
          v-for="item in reinforcementResult.items"
          :key="item.questionOrder"
          :type="item.correct ? 'success' : 'danger'"
        >
          第 {{ item.questionOrder }} 题 {{ item.correct ? '正确' : '错误' }}
        </el-tag>
      </div>
      <div class="reinforcement-result__actions">
        <el-button
          type="primary"
          :disabled="!reinforcementResult.hasMoreCandidates"
          :loading="submitting"
          @click="continueReinforcement"
        >
          继续强化
        </el-button>
        <el-button @click="returnToSetup">返回原练习</el-button>
      </div>
      <el-alert
        v-if="!reinforcementResult.hasMoreCandidates"
        title="题库中暂无更多未做题"
        type="info"
        :closable="false"
        show-icon
      />
    </el-card>

    <el-result v-else-if="loadError" icon="warning" title="练习会话不可用" :sub-title="loadError">
      <template #extra>
        <el-button type="primary" @click="returnToSetup">返回知识点练习</el-button>
      </template>
    </el-result>

    <el-result
      v-else-if="session?.sessionStatus === 'COMPLETED'"
      icon="success"
      title="练习已结束"
      sub-title="已提交的作答已经保存，未提交的选择不会计入本次练习。"
    >
      <template #extra>
        <el-button type="primary" @click="returnToSetup">返回知识点练习</el-button>
      </template>
    </el-result>

    <focus-exam-shell
      v-else-if="session && currentQuestion"
      mode="PRACTICE"
      :title="reinforcementRoundId ? '同知识点强化 · 题库练习' : knowledgePointName"
      description="逐题提交后即可查看结果与解析；未提交的选择不会保存。"
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
      exit-label="退出练习"
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
        <template #stem-actions>
          <ai-question-coach-panel
            :session-id="sessionId"
            :question-order="currentQuestion.questionOrder"
            :current-selection="choiceAnswer"
            :submitted="Boolean(currentQuestion.submission)"
          />
        </template>
        <template #actions>
          <el-button
            :disabled="currentQuestion.questionOrder === 1 || submitting || switchingQuestion"
            @click="goTo(currentQuestion.questionOrder - 1)"
          >
            上一题
          </el-button>
          <el-button
            v-if="!currentQuestion.submission"
            type="primary"
            :disabled="!choiceAnswer.length || switchingQuestion"
            :loading="submitting"
            @click="submitCurrent"
          >
            提交本题
          </el-button>
          <el-button v-else type="primary" :disabled="switchingQuestion" @click="goNext">
            {{ currentQuestion.questionOrder === session.totalCount ? '结束练习' : '下一题' }}
          </el-button>
        </template>
      </choice-question>

      <section v-if="currentQuestion.submission" class="knowledge-practice-answering__feedback" aria-live="polite">
        <el-alert
          :type="currentQuestion.submission.correct ? 'success' : 'error'"
          :title="
            currentQuestion.submission.correct
              ? '回答正确'
              : `回答错误，正确答案：${currentQuestion.submission.correctOptionLabels.join('、')}`
          "
          :closable="false"
          show-icon
        />
        <el-card
          v-if="currentQuestion.submission.analysis"
          shadow="never"
          class="knowledge-practice-answering__analysis"
        >
          <template #header><strong>解析</strong></template>
          <p>{{ currentQuestion.submission.analysis }}</p>
        </el-card>
        <reinforcement-suggestion-card
          v-if="!reinforcementRoundId"
          :session-id="sessionId"
          :question-order="currentQuestion.questionOrder"
          :submitted="Boolean(currentQuestion.submission)"
        />
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
          finish-label="结束练习"
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
  completeKnowledgePractice,
  getKnowledgePracticeAnswerSession,
  getKnowledgePracticeItem,
  submitKnowledgePracticeItem,
  continueReinforcementRound,
  getReinforcementResult,
  type KnowledgePracticeAnswerSessionVo,
  type KnowledgePracticeErrorVo,
  type KnowledgePracticeItemVo,
  type ReinforcementResultVo
} from '@/api/certmuse/assessment/knowledge-practices';
import AiQuestionCoachPanel from '@/components/exam/AiQuestionCoachPanel.vue';
import ChoiceQuestion from '@/components/exam/ChoiceQuestion.vue';
import ExamAnswerCard from '@/components/exam/ExamAnswerCard.vue';
import FocusExamShell from '@/components/exam/FocusExamShell.vue';
import ReinforcementSuggestionCard from '@/components/exam/ReinforcementSuggestionCard.vue';
import { contractErrorFrom } from '@/utils/learning-goal';

const setupPath = '/learning/question-bank/knowledge-practice';
const route = useRoute();
const router = useRouter();
const loading = ref(true);
const switchingQuestion = ref(false);
const submitting = ref(false);
const loadError = ref('');
const session = ref<KnowledgePracticeAnswerSessionVo>();
const currentQuestion = ref<KnowledgePracticeItemVo>();
const reinforcementResult = ref<ReinforcementResultVo>();
const choiceAnswers = reactive<Record<number, string[]>>({});
const saveText = ref('');
const saveState = ref<'saved' | 'saving' | 'failed'>('saved');
let itemRequestSequence = 0;

const sessionId = computed(() => (typeof route.query.sessionId === 'string' ? route.query.sessionId : ''));
const reinforcementRoundId = computed(() =>
  typeof route.query.reinforcementRoundId === 'string' ? route.query.reinforcementRoundId : ''
);
const knowledgePointName = computed(() => {
  const value = route.query.knowledgePointName;
  return typeof value === 'string' && value.trim() ? value.trim() : '知识点专项练习';
});
const choiceAnswer = computed(() =>
  currentQuestion.value ? (choiceAnswers[currentQuestion.value.questionOrder] ?? []) : []
);
const answeredStates = computed(() =>
  Object.fromEntries((session.value?.navigation ?? []).map(item => [item.questionOrder, item.state !== 'UNANSWERED']))
);
const answerResults = computed(() =>
  Object.fromEntries(
    (session.value?.navigation ?? [])
      .filter(item => item.state !== 'UNANSWERED')
      .map(item => [item.questionOrder, item.state === 'SUBMITTED_CORRECT' ? 'correct' : 'incorrect'] as const)
  )
);

watch(
  () => [sessionId.value, reinforcementRoundId.value, route.query.reinforcementResult] as const,
  () => void initialize(),
  { immediate: true }
);

async function initialize() {
  loading.value = true;
  loadError.value = '';
  currentQuestion.value = undefined;
  saveText.value = '';
  reinforcementResult.value = undefined;
  if (route.query.reinforcementResult === '1' && reinforcementRoundId.value) {
    try {
      reinforcementResult.value = (await getReinforcementResult(reinforcementRoundId.value)).data;
    } catch (error) {
      loadError.value = messageForError(error, '无法加载强化结果。');
    } finally {
      loading.value = false;
    }
    return;
  }
  if (!sessionId.value) {
    session.value = undefined;
    loadError.value = '缺少练习会话编号，请从知识点练习页重新开始。';
    loading.value = false;
    return;
  }
  try {
    const response = await getKnowledgePracticeAnswerSession(sessionId.value);
    if (!response.data) throw new Error('未取得练习会话数据。');
    session.value = response.data;
    if (response.data.sessionStatus === 'COMPLETED') {
      if (reinforcementRoundId.value) {
        reinforcementResult.value = (await getReinforcementResult(reinforcementRoundId.value)).data;
      }
      return;
    }
    const firstUnanswered = response.data.navigation.find(item => item.state === 'UNANSWERED')?.questionOrder;
    const requestedOrder = Number(route.query.questionOrder);
    await loadQuestion(
      Number.isInteger(requestedOrder) && requestedOrder > 0
        ? requestedOrder
        : (firstUnanswered ?? response.data.navigation[0]?.questionOrder ?? 1)
    );
  } catch (error) {
    loadError.value = messageForError(error, '无法加载本次练习，请从知识点练习页重新开始。');
  } finally {
    loading.value = false;
  }
}

async function loadQuestion(questionOrder: number) {
  if (!sessionId.value || !session.value || switchingQuestion.value) return;
  const requestSequence = ++itemRequestSequence;
  switchingQuestion.value = true;
  try {
    const response = await getKnowledgePracticeItem(sessionId.value, questionOrder);
    if (!response.data) throw new Error('未取得题目数据。');
    if (requestSequence !== itemRequestSequence) return;
    currentQuestion.value = response.data;
    if (response.data.submission) {
      choiceAnswers[questionOrder] = [...response.data.submission.selectedOptionLabels];
    } else if (!choiceAnswers[questionOrder]) {
      choiceAnswers[questionOrder] = [];
    }
  } catch (error) {
    await handlePracticeError(error);
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
  if (currentQuestion.value.questionOrder === session.value.totalCount) {
    void requestFinish();
  } else {
    goTo(currentQuestion.value.questionOrder + 1);
  }
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
    const response = await submitKnowledgePracticeItem(
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
        navigation: session.value.navigation.map(item =>
          item.questionOrder === question.questionOrder
            ? { ...item, state: response.data!.feedback.correct ? 'SUBMITTED_CORRECT' : 'SUBMITTED_INCORRECT' }
            : item
        )
      };
    }
    saveText.value = '已提交';
    saveState.value = 'saved';
  } catch (error) {
    saveText.value = '提交失败';
    saveState.value = 'failed';
    await handlePracticeError(error);
  } finally {
    submitting.value = false;
  }
}

async function requestExit() {
  const confirmed = await ElMessageBox.confirm(
    '已提交的作答会保留，当前未提交的选择将丢弃。确认退出练习吗？',
    '退出练习',
    { confirmButtonText: '退出练习', cancelButtonText: '继续答题', type: 'warning' }
  )
    .then(() => true)
    .catch(() => false);
  if (confirmed) await completeSession();
}

async function requestFinish() {
  const unansweredCount = (session.value?.totalCount ?? 0) - (session.value?.submittedCount ?? 0);
  const confirmed = await ElMessageBox.confirm(
    unansweredCount
      ? `还有 ${unansweredCount} 道题未提交，结束后将不保存这些题的选择。确认结束练习吗？`
      : '本次题目均已提交，确认结束练习吗？',
    '结束练习',
    { confirmButtonText: '结束练习', cancelButtonText: '继续答题', type: 'warning' }
  )
    .then(() => true)
    .catch(() => false);
  if (confirmed) await completeSession();
}

async function completeSession() {
  if (!sessionId.value || submitting.value) return;
  submitting.value = true;
  saveText.value = '正在结束练习';
  saveState.value = 'saving';
  try {
    const response = await completeKnowledgePractice(sessionId.value, crypto.randomUUID().toLowerCase());
    if (reinforcementRoundId.value) {
      await router.replace({
        path: '/learning/session/practice',
        query: {
          sessionId: sessionId.value,
          reinforcementRoundId: reinforcementRoundId.value,
          reinforcementResult: '1'
        }
      });
    } else {
      await router.replace(response.data?.returnPath ?? setupPath);
    }
  } catch (error) {
    saveText.value = '结束失败';
    saveState.value = 'failed';
    await handlePracticeError(error);
  } finally {
    submitting.value = false;
  }
}

async function continueReinforcement() {
  if (!reinforcementResult.value?.hasMoreCandidates || !reinforcementRoundId.value) return;
  submitting.value = true;
  try {
    const response = await continueReinforcementRound(reinforcementRoundId.value, crypto.randomUUID().toLowerCase());
    if (response.data?.answerPath) await router.replace(response.data.answerPath);
  } catch (error) {
    ElMessage.error(messageForError(error, '暂时无法继续强化。'));
  } finally {
    submitting.value = false;
  }
}

async function handlePracticeError(error: unknown) {
  const contractError = contractErrorFrom(error) as KnowledgePracticeErrorVo | undefined;
  switch (contractError?.errorCode) {
    case 'PRACTICE_ITEM_ALREADY_SUBMITTED':
      ElMessage.warning('该题已在其他设备提交，已同步最新结果。');
      if (currentQuestion.value) await loadQuestion(currentQuestion.value.questionOrder);
      return;
    case 'PRACTICE_SESSION_NOT_ACTIVE':
      ElMessage.warning('本次练习已结束。');
      await initialize();
      return;
    case 'PRACTICE_SESSION_NOT_FOUND':
      loadError.value = '练习会话不存在或无权访问。';
      return;
    case 'PRACTICE_ANSWER_INVALID':
      ElMessage.warning('当前选择不符合本题规则，请修改后再次提交。');
      return;
    case 'PRACTICE_IDEMPOTENCY_CONFLICT':
      ElMessage.error('本次提交请求无效，请重新提交。');
      return;
    default:
      ElMessage.error(messageForError(error, '操作失败，请稍后重试。'));
  }
}

function messageForError(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback;
}

function returnToSetup() {
  void router.replace(reinforcementResult.value?.returnPath ?? session.value?.returnPath ?? setupPath);
}
</script>

<style scoped lang="scss">
.knowledge-practice-answering {
  width: 100%;
  max-width: none;
  margin: 0;
}
.knowledge-practice-answering__feedback {
  display: grid;
  gap: 12px;
  margin-top: 16px;
}
.knowledge-practice-answering__analysis {
  border-color: var(--el-border-color-light);
  background: var(--el-bg-color);
}
.knowledge-practice-answering__analysis p {
  margin: 0;
  color: var(--el-text-color-regular);
  line-height: 1.8;
  white-space: pre-line;
}
.reinforcement-result {
  max-width: 760px;
  margin: 32px auto;
}
.reinforcement-result__items,
.reinforcement-result__actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin: 16px 0;
}
</style>
