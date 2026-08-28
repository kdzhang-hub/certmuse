<template>
  <div v-loading="loading" class="p-2 app-container past-paper-practice-answering">
    <el-result v-if="loadError" icon="warning" title="真题练习会话不可用" :sub-title="loadError">
      <template #extra><el-button type="primary" @click="returnToPapers">返回历年真题</el-button></template>
    </el-result>

    <el-result v-else-if="session?.sessionStatus === 'COMPLETED'" icon="success" title="练习已结束" sub-title="已提交的作答已经保存，未提交的选择不会计入本次练习。">
      <template #extra><el-button type="primary" @click="returnToPapers">返回历年真题</el-button></template>
    </el-result>

    <focus-exam-shell
      v-else-if="session && currentQuestion"
      mode="PRACTICE"
      :title="paperName"
      description="逐题提交后即可查看结果与解析；题目按真题原卷顺序呈现，未提交的选择不会保存。"
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
        v-if="currentQuestion.question.questionType === 'CHOICE'"
        :question-order="currentQuestion.questionOrder"
        :total-count="session.totalCount"
        :selection-mode="currentQuestion.question.selectionMode ?? 'single'"
        :stem="currentQuestion.question.stem"
        :options="currentQuestion.question.options"
        :images="currentQuestion.question.images"
        :answer="choiceAnswer"
        :disabled="Boolean(currentQuestion.submission) || submitting || switchingQuestion"
        @update:answer="updateChoiceAnswer"
      >
        <template #actions>
          <el-button :disabled="currentQuestion.questionOrder === 1 || submitting || switchingQuestion" @click="goTo(currentQuestion.questionOrder - 1)">上一题</el-button>
          <el-button v-if="!currentQuestion.submission" type="primary" :disabled="!choiceAnswer.length || switchingQuestion" :loading="submitting" @click="submitCurrent">提交本题</el-button>
          <el-button v-else type="primary" :disabled="switchingQuestion" @click="goNext">{{ currentQuestion.questionOrder === session.totalCount ? '结束练习' : '下一题' }}</el-button>
        </template>
      </choice-question>

      <el-card v-else shadow="never" class="past-paper-practice-answering__subjective">
        <div class="past-paper-practice-answering__subjective-meta">
          <span>{{ currentQuestion.question.questionType === 'CASE' ? '案例题' : '论文题' }}</span>
          <span>第 {{ currentQuestion.questionOrder }} / {{ session.totalCount }} 题</span>
        </div>
        <h3><question-text :content="currentQuestion.question.stem" /></h3>
        <question-images :images="currentQuestion.question.images" />
        <el-alert
          v-if="['PENDING', 'PROCESSING'].includes(currentQuestion.submission?.subjectiveGrading?.state ?? '')"
          class="past-paper-practice-answering__grading-pending"
          type="info"
          :closable="false"
          show-icon
          title="AI 评分中，请稍后回来查看。"
        />
        <el-input v-model="textAnswer" type="textarea" :rows="14" :disabled="Boolean(currentQuestion.submission) || submitting || switchingQuestion" placeholder="请输入你的作答。" />
        <section
          v-if="currentQuestion.submission?.subjectiveGrading && !['PENDING', 'PROCESSING'].includes(currentQuestion.submission.subjectiveGrading.state)"
          class="past-paper-practice-answering__feedback"
          aria-live="polite"
        >
          <el-alert v-if="currentQuestion.submission.subjectiveGrading.state === 'FAILED'" type="warning" :closable="false" show-icon title="AI 评分失败，本题不计入错题和学习画像。" />
          <el-card v-else shadow="never" class="past-paper-practice-answering__analysis">
            <template #header><strong>AI 评分与解析</strong></template>
            <p>得分：{{ currentQuestion.submission.subjectiveGrading.score }} / {{ currentQuestion.submission.subjectiveGrading.maxScore }}</p>
            <p>{{ currentQuestion.submission.subjectiveGrading.feedback }}</p>
          </el-card>
        </section>
        <div class="past-paper-practice-answering__subjective-actions">
          <el-button :disabled="currentQuestion.questionOrder === 1 || submitting || switchingQuestion" @click="goTo(currentQuestion.questionOrder - 1)">上一题</el-button>
          <el-button v-if="!currentQuestion.submission" type="primary" :disabled="!textAnswer.trim() || switchingQuestion" :loading="submitting" @click="submitCurrent">提交本题</el-button>
          <el-button v-else type="primary" :disabled="switchingQuestion" @click="goNext">{{ currentQuestion.questionOrder === session.totalCount ? '结束练习' : '下一题' }}</el-button>
        </div>
      </el-card>

      <section v-if="currentQuestion.submission && currentQuestion.question.questionType === 'CHOICE'" class="past-paper-practice-answering__feedback" aria-live="polite">
        <el-alert :type="currentQuestion.submission.correct ? 'success' : 'error'" :title="currentQuestion.submission.correct ? '回答正确' : `回答错误，正确答案：${currentQuestion.submission.correctOptionLabels.join('、')}`" :closable="false" show-icon />
        <el-card v-if="currentQuestion.submission.analysis" shadow="never" class="past-paper-practice-answering__analysis">
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
import ChoiceQuestion from '@/components/exam/ChoiceQuestion.vue';
import ExamAnswerCard from '@/components/exam/ExamAnswerCard.vue';
import QuestionImages from '@/components/exam/QuestionImages.vue';
import QuestionText from '@/components/exam/QuestionText.vue';
import FocusExamShell from '@/components/exam/FocusExamShell.vue';
import {
  completePastPaperPractice,
  getPastPaperPracticeItem,
  getPastPaperPracticeSession,
  submitPastPaperPracticeItem,
  type PastPaperPracticeErrorVo,
  type PastPaperPracticeItemVo,
  type PastPaperPracticeSessionVo
} from '@/api/certmuse/assessment/past-papers';
import { contractErrorFrom } from '@/utils/learning-goal';

const papersPath = '/learning/question-bank/past-papers';
const route = useRoute();
const router = useRouter();
const loading = ref(true);
const switchingQuestion = ref(false);
const submitting = ref(false);
const loadError = ref('');
const session = ref<PastPaperPracticeSessionVo>();
const currentQuestion = ref<PastPaperPracticeItemVo>();
const choiceAnswers = reactive<Record<number, string[]>>({});
const textAnswers = reactive<Record<number, string>>({});
const saveText = ref('');
const saveState = ref<'saved' | 'saving' | 'failed'>('saved');
let itemRequestSequence = 0;

const sessionId = computed(() => typeof route.query.sessionId === 'string' ? route.query.sessionId : '');
const paperName = computed(() => typeof route.query.paperName === 'string' && route.query.paperName.trim() ? route.query.paperName.trim() : '历年真题练习');
const choiceAnswer = computed(() => currentQuestion.value ? choiceAnswers[currentQuestion.value.questionOrder] ?? [] : []);
const textAnswer = computed({ get: () => currentQuestion.value ? textAnswers[currentQuestion.value.questionOrder] ?? '' : '', set: value => { if (currentQuestion.value) textAnswers[currentQuestion.value.questionOrder] = value; } });
const answeredStates = computed(() => Object.fromEntries((session.value?.navigation ?? []).map(item => [item.questionOrder, item.state !== 'UNANSWERED'])));
const answerResults = computed(() => Object.fromEntries((session.value?.navigation ?? []).filter(item => item.state !== 'UNANSWERED').map(item => [item.questionOrder, item.state === 'SUBMITTED_CORRECT' ? 'correct' : 'incorrect'] as const)));

watch(sessionId, () => void initialize(), { immediate: true });

async function initialize() {
  loading.value = true; loadError.value = ''; currentQuestion.value = undefined; saveText.value = '';
  if (!sessionId.value) { session.value = undefined; loadError.value = '缺少真题练习会话编号，请从历年真题页重新开始。'; loading.value = false; return; }
  try {
    const response = await getPastPaperPracticeSession(sessionId.value);
    if (!response.data) throw new Error('未取得真题练习会话数据。');
    session.value = response.data;
    if (response.data.sessionStatus === 'COMPLETED') return;
    await loadQuestion(response.data.navigation.find(item => item.state === 'UNANSWERED')?.questionOrder ?? response.data.navigation[0]?.questionOrder ?? 1);
  } catch (error) { loadError.value = messageForError(error, '无法加载本次真题练习，请从历年真题页重新开始。'); }
  finally { loading.value = false; }
}

async function loadQuestion(questionOrder: number) {
  if (!sessionId.value || !session.value || switchingQuestion.value) return;
  const requestSequence = ++itemRequestSequence; switchingQuestion.value = true;
  try {
    const response = await getPastPaperPracticeItem(sessionId.value, questionOrder);
    if (!response.data) throw new Error('未取得题目数据。');
    if (requestSequence !== itemRequestSequence) return;
    currentQuestion.value = response.data;
    choiceAnswers[questionOrder] = response.data.submission ? [...response.data.submission.selectedOptionLabels] : choiceAnswers[questionOrder] ?? [];
    textAnswers[questionOrder] = response.data.submission?.textAnswer ?? textAnswers[questionOrder] ?? '';
    if (response.data.submission?.subjectiveGrading && ['PENDING', 'PROCESSING'].includes(response.data.submission.subjectiveGrading.state)) window.setTimeout(() => void loadQuestion(questionOrder), 2500);
  } catch (error) { await handlePracticeError(error); }
  finally { if (requestSequence === itemRequestSequence) switchingQuestion.value = false; }
}
function goTo(questionOrder: number) { if (session.value && questionOrder >= 1 && questionOrder <= session.value.totalCount) void loadQuestion(questionOrder); }
function goNext() { if (!currentQuestion.value || !session.value) return; currentQuestion.value.questionOrder === session.value.totalCount ? void requestFinish() : goTo(currentQuestion.value.questionOrder + 1); }
function updateChoiceAnswer(value: string[]) { if (currentQuestion.value && !currentQuestion.value.submission) { choiceAnswers[currentQuestion.value.questionOrder] = value; saveText.value = ''; } }

async function submitCurrent() {
  const question = currentQuestion.value;
  if (!question || question.submission || submitting.value) return;
  const isChoice = question.question.questionType === 'CHOICE';
  if (isChoice ? !choiceAnswer.value.length : !textAnswer.value.trim()) return;
  submitting.value = true; saveText.value = '提交中'; saveState.value = 'saving';
  try {
    const response = await submitPastPaperPracticeItem(sessionId.value, question.questionOrder, isChoice
      ? { answer: { value: [...choiceAnswer.value] } }
      : { answer: { text: textAnswer.value.trim(), questionType: question.question.questionType as 'CASE' | 'ESSAY', schemaVersion: 'subjective_answer/1.0' } });
    if (!response.data) throw new Error('提交失败，请重试。');
    if (currentQuestion.value?.questionOrder === question.questionOrder) currentQuestion.value = { ...question, submission: response.data.feedback };
    if (session.value) session.value = { ...session.value, submittedCount: response.data.submittedCount, navigation: session.value.navigation.map(item => item.questionOrder === question.questionOrder ? { ...item, state: response.data!.feedback.correct === false ? 'SUBMITTED_INCORRECT' : 'SUBMITTED_CORRECT' } : item) };
    saveText.value = '已提交'; saveState.value = 'saved';
  } catch (error) { saveText.value = '提交失败'; saveState.value = 'failed'; await handlePracticeError(error); }
  finally { submitting.value = false; }
}

async function requestExit() {
  const confirmed = await ElMessageBox.confirm('已提交的作答会保留，当前未提交的选择将丢弃。确认退出练习吗？', '退出练习', { confirmButtonText: '退出练习', cancelButtonText: '继续答题', type: 'warning' }).then(() => true).catch(() => false);
  if (confirmed) await completeSession();
}
async function requestFinish() {
  const unansweredCount = (session.value?.totalCount ?? 0) - (session.value?.submittedCount ?? 0);
  const confirmed = await ElMessageBox.confirm(unansweredCount ? `还有 ${unansweredCount} 道题未提交，结束后将不保存这些题的选择。确认结束练习吗？` : '本次题目均已提交，确认结束练习吗？', '结束练习', { confirmButtonText: '结束练习', cancelButtonText: '继续答题', type: 'warning' }).then(() => true).catch(() => false);
  if (confirmed) await completeSession();
}
async function completeSession() {
  if (!sessionId.value || submitting.value) return;
  submitting.value = true; saveText.value = '正在结束练习'; saveState.value = 'saving';
  try { const response = await completePastPaperPractice(sessionId.value); await router.replace(response.data?.returnPath ?? papersPath); }
  catch (error) { saveText.value = '结束失败'; saveState.value = 'failed'; await handlePracticeError(error); }
  finally { submitting.value = false; }
}
async function handlePracticeError(error: unknown) {
  const code = (contractErrorFrom(error) as PastPaperPracticeErrorVo | undefined)?.errorCode;
  if (code === 'PAST_PAPER_PRACTICE_ITEM_ALREADY_SUBMITTED') { ElMessage.warning('该题已在其他设备提交，已同步最新结果。'); if (currentQuestion.value) await loadQuestion(currentQuestion.value.questionOrder); return; }
  if (code === 'PAST_PAPER_PRACTICE_SESSION_NOT_ACTIVE') { ElMessage.warning('本次练习已结束。'); await initialize(); return; }
  if (code === 'PAST_PAPER_PRACTICE_SESSION_NOT_FOUND') { loadError.value = '真题练习会话不存在或无权访问。'; return; }
  if (code === 'PAST_PAPER_PRACTICE_REQUEST_INVALID') { ElMessage.warning('当前选择不符合本题规则，请修改后再次提交。'); return; }
  if (code === 'PAST_PAPER_PRACTICE_IDEMPOTENCY_CONFLICT') { ElMessage.error('本次提交请求无效，请重新提交。'); return; }
  ElMessage.error(messageForError(error, '操作失败，请稍后重试。'));
}
function messageForError(error: unknown, fallback: string) { return error instanceof Error && error.message ? error.message : fallback; }
function returnToPapers() { void router.replace(session.value?.returnPath ?? papersPath); }
</script>

<style scoped lang="scss">
.past-paper-practice-answering { width: 100%; max-width: none; margin: 0; }
.past-paper-practice-answering__feedback { display: grid; gap: 12px; margin-top: 16px; }
.past-paper-practice-answering__grading-pending { margin: 16px 0; }
.past-paper-practice-answering__analysis { border-color: var(--el-border-color-light); background: var(--el-bg-color); }
.past-paper-practice-answering__analysis p { margin: 0; color: var(--el-text-color-regular); line-height: 1.8; white-space: pre-line; }
.past-paper-practice-answering__subjective { border-color: var(--el-border-color-light); }
.past-paper-practice-answering__subjective-meta, .past-paper-practice-answering__subjective-actions { display: flex; justify-content: space-between; gap: 12px; }
.past-paper-practice-answering__subjective h3 { margin: 20px 0; line-height: 1.8; }
.past-paper-practice-answering__subjective img { display: block; max-width: 100%; margin: 12px 0; }
.past-paper-practice-answering__subjective-actions { justify-content: flex-end; margin-top: 20px; }
</style>
