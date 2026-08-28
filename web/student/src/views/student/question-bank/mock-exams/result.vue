<template>
  <div v-loading="loading" class="p-2 app-container exam-result-page">
    <el-card v-if="status?.status !== 'COMPLETED'" shadow="never">
      <el-result
        :icon="status?.status === 'FAILED' ? 'error' : 'info'"
        :title="status?.status === 'FAILED' ? '考试结果生成失败' : '正在生成考试结果'"
        sub-title="答卷已经保存，你可以等待或稍后回来查看。"
      >
        <template #extra>
          <el-steps direction="vertical" :active="completedStages">
            <el-step
              v-for="stage in status?.stages"
              :key="stage.code"
              :title="stageTitle(stage.code)"
              :status="
                stage.state === 'FAILED'
                  ? 'error'
                  : stage.state === 'COMPLETED'
                    ? 'success'
                    : stage.state === 'RUNNING'
                      ? 'process'
                      : 'wait'
              "
            />
          </el-steps>
          <div class="result-actions">
            <el-button @click="load">刷新状态</el-button>
            <el-button v-if="status?.status === 'FAILED'" type="primary" @click="retry">重新生成</el-button>
            <el-button @click="router.push('/learning/question-bank/mock-exams')">稍后查看</el-button>
          </div>
        </template>
      </el-result>
    </el-card>
    <template v-else-if="result">
      <el-card shadow="never">
        <template #header>
          <el-tag type="success">模拟考试结果</el-tag>
          <h2>{{ result.title }}</h2>
        </template>
        <el-row :gutter="16">
          <el-col :xs="12" :sm="6"><el-statistic title="得分" :value="Number(result.score)" /></el-col>
          <el-col :xs="12" :sm="6"><el-statistic title="满分" :value="Number(result.maxScore)" /></el-col>
          <el-col :xs="12" :sm="6"><el-statistic title="正确题数" :value="result.correctCount" /></el-col>
          <el-col :xs="12" :sm="6"><el-statistic title="有效用时" :value="result.usedSeconds" suffix="秒" /></el-col>
        </el-row>
      </el-card>
      <el-card v-for="q in result.questions" :key="q.questionOrder" class="mt-4 question-result" shadow="never">
        <template #header>
          <div>
            <strong>第 {{ q.questionOrder }} 题 · {{ typeName(q.questionType) }}</strong>
            <el-tag
              :type="
                q.unanswered || q.correct === false || Number(q.score) / Math.max(Number(q.maxScore), 1) < 0.6
                  ? 'danger'
                  : 'success'
              "
            >
              {{ q.unanswered ? '未作答' : `${q.score} / ${q.maxScore} 分` }}
            </el-tag>
          </div>
        </template>
        <h3 class="question-stem"><question-text :content="q.stem" /></h3>
        <question-images :images="q.images" />
        <div v-if="q.questionType === 'CHOICE'" class="answer-options">
          <div
            v-for="option in q.options"
            :key="option.label"
            class="answer-option"
            :class="optionClasses(option.label, q.selectedOptionLabels, q.correctOptionLabels)"
          >
            <span class="answer-option__label">{{ option.label }}</span>
            <question-text class="answer-option__content" :content="option.content" />
            <span class="answer-option__badges">
              <el-tag v-if="q.selectedOptionLabels.includes(option.label)" size="small" type="primary">你的选择</el-tag>
              <el-tag v-if="q.correctOptionLabels.includes(option.label)" size="small" type="success">正确答案</el-tag>
            </span>
          </div>
        </div>
        <p v-if="q.questionType === 'CHOICE'">你的答案：{{ q.selectedOptionLabels.join('、') || '未作答' }}</p>
        <p v-else class="pre-line">你的答案：{{ q.textAnswer || '未作答' }}</p>
        <p v-if="q.questionType === 'CHOICE'">正确答案：{{ q.correctOptionLabels.join('、') }}</p>
        <p v-else class="pre-line">参考答案：{{ q.referenceAnswer || '暂无' }}</p>
        <div class="analysis-toggle">
          <el-button text type="primary" @click="toggleAnalysis(q.questionOrder)">
            {{ expandedAnalyses.includes(q.questionOrder) ? '收起解析' : '查看解析' }}
          </el-button>
        </div>
        <el-alert v-if="expandedAnalyses.includes(q.questionOrder)" type="info" :closable="false" title="解析">
          <p class="pre-line"><question-text :content="q.analysis || '暂无解析'" /></p>
        </el-alert>
      </el-card>
      <div class="result-actions">
        <el-button type="primary" @click="router.push('/learning/question-bank/mock-exams')">返回模拟试卷</el-button>
        <el-button @click="router.push('/learning/mistakes')">查看错题</el-button>
      </div>
    </template>
  </div>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import type { FormalExamResultVo, FormalExamStatusVo } from '@/api/certmuse/assessment/formal-exam-types';
import {
  getSimulationResult,
  getSimulationStatus,
  regenerateSimulationResult
} from '@/api/certmuse/assessment/simulations';
import QuestionText from '@/components/exam/QuestionText.vue';
const route = useRoute(),
  router = useRouter(),
  sessionId = String(route.query.sessionId ?? '');
const loading = ref(true),
  status = ref<FormalExamStatusVo>(),
  result = ref<FormalExamResultVo>();
const expandedAnalyses = ref<number[]>([]);
let timer: ReturnType<typeof setTimeout> | undefined;
const completedStages = computed(() => status.value?.stages.filter(v => v.state === 'COMPLETED').length ?? 0),
  stageTitle = (c: string) =>
    (
      ({
        SUBMITTED: '答卷已提交',
        SCORING: '正在评分',
        REPORT_GENERATING: '正在生成报告',
        PROFILE_UPDATING: '正在更新画像',
        COMPLETED: '结果已生成'
      }) as Record<string, string>
    )[c] ?? c;
async function load() {
  if (!sessionId) return router.replace('/learning/question-bank/mock-exams');
  loading.value = true;
  try {
    const r = await getSimulationStatus(sessionId);
    status.value = r.data;
    if (r.data?.status === 'COMPLETED') result.value = (await getSimulationResult(sessionId)).data;
    else if (r.data?.status === 'PROCESSING') {
      clearTimeout(timer);
      timer = setTimeout(() => void load(), 1500);
    }
  } finally {
    loading.value = false;
  }
}
async function retry() {
  await regenerateSimulationResult(sessionId);
  await load();
}
const typeName = (t: string) =>
  (({ CHOICE: '选择题', CASE: '案例题', ESSAY: '论文题' }) as Record<string, string>)[t] ?? t;
const toggleAnalysis = (questionOrder: number) => {
  expandedAnalyses.value = expandedAnalyses.value.includes(questionOrder)
    ? expandedAnalyses.value.filter(item => item !== questionOrder)
    : [...expandedAnalyses.value, questionOrder];
};
const optionClasses = (label: string, selected: string[], correct: string[]) => ({
  'is-selected': selected.includes(label),
  'is-correct': correct.includes(label),
  'is-wrong': selected.includes(label) && !correct.includes(label)
});
onBeforeUnmount(() => clearTimeout(timer));
void load();
</script>
<style scoped lang="scss">
.exam-result-page {
  width: 100%;
  max-width: 100%;
  margin: 0;
}
h2 {
  margin: 10px 0 0;
}
.question-result :deep(.el-card__header) > div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}
.question-result p {
  line-height: 1.8;
}
.question-stem {
  line-height: 1.8;
}
.pre-line {
  white-space: pre-line;
}
.answer-options {
  display: grid;
  gap: 8px;
  margin: 16px 0;
}
.answer-option {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
}
.answer-option.is-correct { border-color: var(--el-color-success); background: var(--el-color-success-light-9); }
.answer-option.is-wrong { border-color: var(--el-color-danger); background: var(--el-color-danger-light-9); }
.answer-option__label { font-weight: 700; }
.answer-option__badges { display: flex; gap: 6px; }
.analysis-toggle { margin: 12px 0 4px; }
.result-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 24px;
}
</style>
