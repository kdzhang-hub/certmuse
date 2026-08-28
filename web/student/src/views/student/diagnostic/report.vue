<template>
  <div class="p-2 app-container" v-loading="loading">
    <template v-if="report && session">
      <el-card shadow="hover">
        <template #header>
          <el-tag type="success">首次诊断报告</el-tag>
          <h2>本次真实作答的初步结果</h2>
        </template>
        <el-alert
          v-if="report.profileStatus === 'FAILED'"
          type="warning"
          :closable="false"
          title="初始画像未生成；你可以重试结果，但暂不能进入学习首页。"
        />
        <el-alert
          v-else
          type="info"
          :closable="false"
          title="初始画像来自本次诊断，后续会根据练习、订正和验证结果持续更新。"
        />
        <el-row :gutter="16" class="mt-4">
          <el-col :xs="24" :sm="12" :md="6"><el-statistic title="总题数" :value="totalCount" /></el-col>
          <el-col :xs="24" :sm="12" :md="6"><el-statistic title="已答" :value="session.answeredCount" /></el-col>
          <el-col :xs="24" :sm="12" :md="6"><el-statistic title="未答" :value="session.unansweredCount" /></el-col>
          <el-col :xs="24" :sm="12" :md="6"><el-statistic title="正确题数" :value="correctCount" /></el-col>
          <el-col :xs="24" :sm="12" :md="6"><el-statistic title="错误题数" :value="wrongCount" /></el-col>
          <el-col :xs="24" :sm="12" :md="6"><el-statistic title="正确率" :value="accuracy" suffix="%" :precision="1" /></el-col>
        </el-row>
      </el-card>

      <el-card v-if="subjects.length" class="mt-4" shadow="hover">
        <template #header><strong>分科表现</strong></template>
        <el-table :data="subjects" size="small">
          <el-table-column label="科目" min-width="160"><template #default="{ row }">科目 {{ row.examSubjectId }}</template></el-table-column>
          <el-table-column prop="questionCount" label="题数" width="100" />
          <el-table-column prop="correctCount" label="正确" width="100" />
          <el-table-column label="正确率" width="120"><template #default="{ row }">{{ percent(row.correctCount, row.questionCount) }}%</template></el-table-column>
        </el-table>
      </el-card>

      <el-card v-if="report.profileStatus === 'AVAILABLE'" class="mt-4" shadow="hover">
        <template #header>
          <div class="card-title"><strong>初始学习画像</strong><el-tag effect="plain" :type="confidenceType">{{ confidenceText }}</el-tag></div>
        </template>
        <p class="description">以下结论只覆盖本次诊断实际涉及的知识点；证据不足的结论会保留为待验证。</p>
        <el-empty v-if="!knowledgePoints.length" description="当前没有可展示的知识点画像" />
        <el-table v-else :data="knowledgePoints" size="small">
          <el-table-column label="知识点" min-width="180"><template #default="{ row }">知识点 {{ row.knowledgePointId }}</template></el-table-column>
          <el-table-column label="初步表现" min-width="130"><template #default="{ row }"><el-tag :type="statusType(row.status)" effect="plain">{{ statusText(row.status) }}</el-tag></template></el-table-column>
          <el-table-column label="本次正确/题数" width="140"><template #default="{ row }">{{ row.correctCount }}/{{ row.questionCount }}</template></el-table-column>
          <el-table-column label="数据充足程度" width="140"><template #default="{ row }">{{ confidenceTextFor(row.confidence) }}</template></el-table-column>
        </el-table>
      </el-card>

      <el-card v-if="priorityDirections.length && report.profileStatus === 'AVAILABLE'" class="mt-4" shadow="hover">
        <template #header><strong>优先学习方向</strong></template>
        <ol class="priorities">
          <li v-for="item in priorityDirections" :key="item.knowledgePointId">
            知识点 {{ item.knowledgePointId }}：{{ statusText(item.status) }}（本次 {{ item.correctCount }}/{{ item.questionCount }} 题正确）
          </li>
        </ol>
      </el-card>

      <div class="actions">
        <el-button v-if="report.nextAction === 'RETRY_DIAGNOSTIC_RESULT'" type="primary" @click="router.push({ path: '/learning/diagnostic/result', query: { sessionId } })">重新生成结果</el-button>
        <el-button v-else type="primary" @click="router.push('/learning/home')">进入学习首页</el-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getDiagnosticReport, getDiagnosticSession, type DiagnosticReportVo, type DiagnosticSessionVo } from '@/api/certmuse/learning/diagnostics';
import { getOnboardingStatus } from '@/api/certmuse/learning/onboarding';
import { resolveOnboardingTarget } from '@/utils/onboarding-routing';
import { getHandledRequestError } from '@/utils/request';

const route = useRoute();
const router = useRouter();
const sessionId = ref(String(route.query.sessionId ?? ''));
const loading = ref(true);
const report = ref<DiagnosticReportVo>();
const session = ref<DiagnosticSessionVo>();
const totalCount = computed(() => report.value?.report.subjectScores.totalQuestions ?? 0);
const correctCount = computed(() => report.value?.report.subjectScores.correctCount ?? 0);
const wrongCount = computed(() => Math.max(0, totalCount.value - correctCount.value - (session.value?.unansweredCount ?? 0)));
const accuracy = computed(() => (totalCount.value ? (correctCount.value / totalCount.value) * 100 : 0));
const subjects = computed(() => report.value?.report.subjectScores.subjects ?? []);
const knowledgePoints = computed(() => report.value?.report.profileSummary.knowledgePoints ?? []);
const priorityDirections = computed(() => report.value?.report.profileSummary.priorityDirections ?? []);
const confidenceText = computed(() => confidenceTextFor(report.value?.report.profileSummary.confidence));
const confidenceType = computed(() => (report.value?.report.profileSummary.confidence === 'medium' ? 'success' : 'warning'));

const percent = (correct: number, total: number) => (total ? ((correct / total) * 100).toFixed(1) : '0.0');
const confidenceTextFor = (confidence?: string) => (confidence === 'medium' ? '证据较充分' : '数据不足，待验证');
const statusText = (status: string) => ({ urgent: '建议优先加强', weak: '建议加强', provisional: '表现暂不稳定' })[status] ?? '待进一步验证';
const statusType = (status: string): 'danger' | 'warning' | 'info' => {
  const types: Record<string, 'danger' | 'warning' | 'info'> = { urgent: 'danger', weak: 'warning', provisional: 'info' };
  return types[status] ?? 'info';
};

async function routeByAction(action: unknown) {
  await router.replace(resolveOnboardingTarget(action) ?? '/learning/onboarding-error');
}
async function ensureSessionId() {
  if (sessionId.value) return true;
  const status = (await getOnboardingStatus()).data;
  if (status?.activeSessionId) {
    sessionId.value = status.activeSessionId;
    await router.replace({ path: '/learning/diagnostic/report', query: { sessionId: sessionId.value } });
    return true;
  }
  await routeByAction(status?.nextAction);
  return false;
}
async function load() {
  loading.value = true;
  try {
    if (!(await ensureSessionId())) return;
    const [reportResponse, sessionResponse] = await Promise.all([getDiagnosticReport(sessionId.value), getDiagnosticSession(sessionId.value)]);
    if (!reportResponse.data || !sessionResponse.data) throw new Error('诊断报告数据缺失。');
    report.value = reportResponse.data;
    session.value = sessionResponse.data;
  } catch (error) {
    const detail = getHandledRequestError<{ nextAction?: string }>(error)?.responseData;
    await routeByAction(detail?.nextAction ?? 'WAIT_PROCESSING');
  } finally {
    loading.value = false;
  }
}
void load();
</script>

<style scoped>
h2 { margin: 10px 0 0; }
.card-title { display: flex; align-items: center; justify-content: space-between; }
.description { color: var(--el-text-color-secondary); }
.priorities { margin: 0; padding-left: 22px; line-height: 2; }
.actions { display: flex; justify-content: flex-end; margin-top: 24px; }
</style>
