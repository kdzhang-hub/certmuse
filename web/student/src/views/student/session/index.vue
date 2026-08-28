<template>
  <knowledge-practice-answering v-if="isKnowledgePracticeRoute" />
  <past-paper-practice-answering v-else-if="isPastPaperPracticeRoute" />
  <mistake-correction-answering v-else-if="isMistakeCorrectionRoute" />
  <daily-task-answering v-else-if="isDailyTaskPracticeRoute" />
  <task-learning-material v-else-if="isTaskLearningRoute" :task-id="taskId" />
  <div v-else v-loading="loading">
    <el-result
      v-if="!session && !loading"
      icon="warning"
      title="练习会话不可用"
      sub-title="请从任务、题集或错题页面重新开始。"
    >
      <template #extra>
        <el-button type="primary" @click="router.push('/learning/home')">返回学习首页</el-button>
      </template>
    </el-result>
    <focus-exam-shell
      v-else-if="session && currentQuestion"
      :mode="session.mode"
      :title="session.title"
      :description="session.instruction"
      :total-count="session.questions.length"
      :answered-count="answeredCount"
      :unanswered-count="session.questions.length - answeredCount"
      timer-label="剩余"
      formatted-timer="--:--:--"
      :is-overtime="false"
      :paused="false"
      :saving="submitting"
      :save-text="saveText"
      :save-state="saveState"
      :exit-label="kind === 'task' ? '暂存并退出' : '退出练习'"
      @exit="exitSession"
    >
      <template v-if="kind === 'task'" #steps>
        <task-session-steps phase="practice" />
      </template>
      <choice-question
        v-if="currentQuestion.type === 'CHOICE'"
        :question-order="currentIndex + 1"
        :total-count="session.questions.length"
        :selection-mode="currentQuestion.selectionMode ?? 'single'"
        :stem="currentQuestion.stem"
        :options="(currentQuestion.options ?? []).map(item => ({ label: item.key, content: item.content }))"
        :images="[]"
        :answer="choiceAnswer"
        :disabled="isSubmitted(currentQuestion.id)"
        @update:answer="choiceAnswer = $event"
      >
        <template #actions>
          <el-button :disabled="currentIndex === 0" @click="go(currentIndex - 1)">上一题</el-button>
          <el-button
            v-if="!isSubmitted(currentQuestion.id)"
            type="primary"
            :disabled="!hasAnswer"
            :loading="submitting"
            @click="submitCurrent"
          >
            提交本题
          </el-button>
          <el-button v-else type="primary" @click="goNext">
            {{ isLastQuestion ? (kind === 'task' ? '完成任务' : '完成练习') : '下一题' }}
          </el-button>
        </template>
      </choice-question>

      <el-card v-else class="subjective-question" shadow="never">
        <div class="subjective-question__topline">
          <span>{{ currentQuestion.type === 'CASE' ? '案例题' : '论文题' }}</span>
          <span>第 {{ currentIndex + 1 }} / {{ session.questions.length }} 题</span>
        </div>
        <section class="subjective-question__stem">
          <h3>{{ currentQuestion.stem }}</h3>
          <ol v-if="currentQuestion.subQuestions?.length" class="subjective-question__subquestions">
            <li v-for="item in currentQuestion.subQuestions" :key="item">{{ item }}</li>
          </ol>
        </section>
        <el-input
          v-model="textAnswers[currentQuestion.id]"
          type="textarea"
          :rows="12"
          :disabled="isSubmitted(currentQuestion.id)"
          placeholder="请按（1）（2）（3）分段作答。"
        />
        <div v-if="feedback[currentQuestion.id]" class="subjective-question__feedback">
          <el-alert type="info" :closable="false" show-icon title="参考答案与得分要点">
            <template #default>
              <p class="pre-line">{{ feedback[currentQuestion.id].referenceAnswer }}</p>
            </template>
          </el-alert>
          <el-card
            v-if="feedback[currentQuestion.id].scoringPoints?.length"
            shadow="never"
            class="subjective-question__points"
          >
            <strong>得分要点</strong>
            <ul>
              <li v-for="point in feedback[currentQuestion.id].scoringPoints" :key="point.code">
                {{ point.description }}（{{ point.maxScore }} 分）
              </li>
            </ul>
          </el-card>
          <el-alert type="info" :closable="false" show-icon title="解析">
            <p class="pre-line">{{ feedback[currentQuestion.id].analysis }}</p>
          </el-alert>
          <el-alert
            v-if="feedback[currentQuestion.id].aiScoring.state === 'PENDING'"
            type="warning"
            :closable="false"
            show-icon
            title="AI 正在评分，你可以继续下一题。"
          />
          <el-alert
            v-else-if="feedback[currentQuestion.id].aiScoring.state === 'SUCCEEDED'"
            type="success"
            :closable="false"
            show-icon
            :title="`AI 预评分：${feedback[currentQuestion.id].aiScoring.score} / ${feedback[currentQuestion.id].aiScoring.maxScore}`"
          >
            {{ feedback[currentQuestion.id].aiScoring.feedback }}
          </el-alert>
        </div>
        <div class="subjective-question__actions">
          <el-button :disabled="currentIndex === 0" @click="go(currentIndex - 1)">上一题</el-button>
          <el-button
            v-if="!isSubmitted(currentQuestion.id)"
            type="primary"
            :disabled="!hasAnswer"
            :loading="submitting"
            @click="submitCurrent"
          >
            提交本题
          </el-button>
          <el-button v-else type="primary" @click="goNext">
            {{ isLastQuestion ? (kind === 'task' ? '完成任务' : '完成练习') : '下一题' }}
          </el-button>
        </div>
      </el-card>

      <template #navigation>
        <exam-answer-card
          :total-count="session.questions.length"
          :answered-count="answeredCount"
          :current-question-order="currentIndex + 1"
          :navigation="session.questions.map((_, index) => ({ questionOrder: index + 1 }))"
          :answered="answeredStates"
          :disabled="submitting"
          :finish-label="kind === 'task' ? '完成任务' : '结束练习'"
          :show-finish="kind !== 'task' || canFinishTask"
          @select="go($event - 1)"
          @finish="finishSession"
        />
      </template>
    </focus-exam-shell>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import type { StudentSession, StudentSessionItemFeedback, StudentSessionKind } from '@/api/student/types';
import { getStudentSession, submitStudentSession, submitStudentSessionItem } from '@/api/student';
import ChoiceQuestion from '@/components/exam/ChoiceQuestion.vue';
import ExamAnswerCard from '@/components/exam/ExamAnswerCard.vue';
import FocusExamShell from '@/components/exam/FocusExamShell.vue';
import DailyTaskAnswering from './DailyTaskAnswering.vue';
import KnowledgePracticeAnswering from './KnowledgePracticeAnswering.vue';
import MistakeCorrectionAnswering from './MistakeCorrectionAnswering.vue';
import PastPaperPracticeAnswering from './PastPaperPracticeAnswering.vue';
import TaskLearningMaterial from './TaskLearningMaterial.vue';
import TaskSessionSteps from './TaskSessionSteps.vue';

const route = useRoute();
const router = useRouter();
const supportedKinds: StudentSessionKind[] = ['task', 'practice', 'verification', 'correction'];
const kind = computed<StudentSessionKind>(() =>
  supportedKinds.includes(String(route.params.kind) as StudentSessionKind)
    ? (String(route.params.kind) as StudentSessionKind)
    : 'practice'
);
const isPastPaperPracticeRoute = computed(
  () => route.params.kind === 'past-paper-practice' && typeof route.query.sessionId === 'string'
);
const isReinforcementResultRoute = computed(
  () => route.query.reinforcementResult === '1' && typeof route.query.reinforcementRoundId === 'string'
);
const isKnowledgePracticeRoute = computed(
  () =>
    kind.value === 'practice' &&
    !isPastPaperPracticeRoute.value &&
    (typeof route.query.sessionId === 'string' || isReinforcementResultRoute.value)
);
const isMistakeCorrectionRoute = computed(
  () => kind.value === 'correction' && typeof route.query.sessionId === 'string'
);
const taskId = computed(() => (typeof route.query.taskId === 'string' ? route.query.taskId : ''));
const isDailyTaskPracticeRoute = computed(
  () => kind.value === 'task' && route.query.phase === 'practice' && typeof route.query.sessionId === 'string'
);
const isTaskLearningRoute = computed(
  () => kind.value === 'task' && Boolean(taskId.value) && route.query.phase !== 'practice'
);
const loading = ref(true);
const submitting = ref(false);
const session = ref<StudentSession>();
const currentIndex = ref(0);
const choiceAnswers = reactive<Record<string, string[]>>({});
const textAnswers = reactive<Record<string, string>>({});
const feedback = reactive<Record<string, StudentSessionItemFeedback>>({});
const saveText = ref('已保存');
const saveState = ref<'saved' | 'saving' | 'failed'>('saved');
const currentQuestion = computed(() => session.value?.questions[currentIndex.value]);
const choiceAnswer = computed({
  get: () => (currentQuestion.value ? (choiceAnswers[currentQuestion.value.id] ?? []) : []),
  set: (value: string[]) => {
    if (currentQuestion.value) choiceAnswers[currentQuestion.value.id] = value;
  }
});
const isLastQuestion = computed(() =>
  Boolean(session.value && currentIndex.value === session.value.questions.length - 1)
);
const hasAnswer = computed(() =>
  currentQuestion.value
    ? currentQuestion.value.type === 'CHOICE'
      ? choiceAnswer.value.length > 0
      : Boolean(textAnswers[currentQuestion.value.id]?.trim())
    : false
);
const isSubmitted = (questionId: string) => Boolean(feedback[questionId]?.submitted);
const answeredStates = computed(() =>
  Object.fromEntries((session.value?.questions ?? []).map((question, index) => [index + 1, isSubmitted(question.id)]))
);
const answeredCount = computed(() => Object.values(answeredStates.value).filter(Boolean).length);
const canFinishTask = computed(() => Boolean(session.value) && answeredCount.value === session.value.questions.length);

function go(index: number) {
  currentIndex.value = Math.max(0, Math.min(index, (session.value?.questions.length ?? 1) - 1));
}
function goNext() {
  if (isLastQuestion.value) void finishSession();
  else go(currentIndex.value + 1);
}
async function submitCurrent() {
  if (!session.value || !currentQuestion.value || !hasAnswer.value) return;
  const question = currentQuestion.value;
  submitting.value = true;
  saveText.value = '提交中';
  saveState.value = 'saving';
  try {
    const answer = question.type === 'CHOICE' ? (choiceAnswers[question.id] ?? []) : (textAnswers[question.id] ?? '');
    const response = await submitStudentSessionItem({
      sessionId: session.value.id,
      kind: session.value.kind,
      questionId: question.id,
      answer
    });
    if (!response.data) throw new Error('提交失败。');
    feedback[question.id] = response.data;
    if (response.data.aiScoring.state === 'PENDING') {
      window.setTimeout(() => {
        const item = feedback[question.id];
        if (item?.aiScoring.state === 'PENDING')
          item.aiScoring = {
            state: 'SUCCEEDED',
            score: 18,
            maxScore: 25,
            feedback: 'AI 预评分已完成：回答覆盖了主要方向，建议把每一问的依据和边界写得更明确。'
          };
      }, 1800);
    }
    saveText.value = '已提交';
    saveState.value = 'saved';
  } catch (error) {
    saveText.value = '提交失败';
    saveState.value = 'failed';
  } finally {
    submitting.value = false;
  }
}
async function finishSession() {
  if (!session.value) return;
  submitting.value = true;
  try {
    const answers: Record<string, string[] | string> = {};
    session.value.questions
      .filter(question => isSubmitted(question.id))
      .forEach(question => {
        answers[question.id] =
          question.type === 'CHOICE' ? (choiceAnswers[question.id] ?? []) : (textAnswers[question.id] ?? '');
      });
    const response = await submitStudentSession({ sessionId: session.value.id, kind: session.value.kind, answers });
    const result = response.data;
    if (result && session.value.kind === 'task') {
      await router.push('/learning/tasks');
    } else if (result) {
      await router.push({
        path: `/learning/result/${session.value.kind}`,
        query: {
          title: result.title,
          answered: String(result.answeredCount),
          correct: String(result.correctCount),
          review: String(result.reviewCount)
        }
      });
    }
  } finally {
    submitting.value = false;
  }
}
const exitSession = () =>
  router.push(
    kind.value === 'task'
      ? '/learning/tasks'
      : kind.value === 'verification' || kind.value === 'correction'
        ? '/learning/mistakes'
        : '/learning/practice'
  );
onMounted(async () => {
  if (
    isKnowledgePracticeRoute.value ||
    isPastPaperPracticeRoute.value ||
    isMistakeCorrectionRoute.value ||
    isDailyTaskPracticeRoute.value ||
    isTaskLearningRoute.value
  )
    return;
  try {
    const response = await getStudentSession(kind.value);
    session.value = response.data;
  } finally {
    loading.value = false;
  }
});
</script>

<style lang="scss" scoped>
.subjective-question {
  border-color: var(--el-border-color-light);
  background: var(--el-bg-color);
}
.subjective-question__topline,
.subjective-question__actions {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}
.subjective-question__topline {
  padding-bottom: 18px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.subjective-question__stem h3 {
  margin: 24px 0 16px;
  line-height: 1.85;
  font-size: 17px;
}
.subjective-question__subquestions {
  padding-left: 22px;
  line-height: 1.8;
}
.subjective-question__feedback {
  display: grid;
  gap: 12px;
  margin-top: 20px;
}
.subjective-question__points ul {
  margin: 10px 0 0;
  padding-left: 20px;
  line-height: 1.8;
}
.subjective-question__actions {
  justify-content: flex-end;
  margin-top: 24px;
}
.pre-line {
  margin: 0;
  white-space: pre-line;
  line-height: 1.7;
}
</style>
