<template>
  <div class="p-2 app-container" v-loading="loading">
    <el-card shadow="hover">
      <el-result
        :icon="failed ? 'error' : 'info'"
        :title="failed ? '诊断结果暂未生成' : '正在生成诊断结果'"
        :sub-title="
          failed ? '答题记录已保存，不需要重新作答。' : '答卷已提交。你可以留在当前页面等待，也可以稍后回来查看。'
        "
      >
        <template #extra>
          <el-steps direction="vertical" :active="active" finish-status="success">
            <el-step
              v-for="stage in status?.stages"
              :key="stage.code"
              :title="title(stage.code)"
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
          <el-alert
            v-if="failed"
            class="mt-4"
            type="error"
            :closable="false"
            :title="status?.failure?.message ?? '结果生成失败。'"
          />
          <div class="actions">
            <el-button @click="load">刷新状态</el-button>
            <el-button
              v-if="failed && status?.failure?.retryAvailable"
              type="primary"
              :loading="retrying"
              @click="retry"
            >
              重新生成结果
            </el-button>
            <el-button @click="router.push('/learning/home')">稍后查看</el-button>
          </div>
        </template>
      </el-result>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  getDiagnosticResultStatus,
  regenerateDiagnosticResult,
  type DiagnosticResultStatusVo
} from '@/api/certmuse/learning/diagnostics';
import { getOnboardingStatus } from '@/api/certmuse/learning/onboarding';
import { resolveOnboardingTarget } from '@/utils/onboarding-routing';
import { getHandledRequestError } from '@/utils/request';

const route = useRoute();
const router = useRouter();
const sessionId = ref(String(route.query.sessionId ?? ''));
const loading = ref(true);
const retrying = ref(false);
const status = ref<DiagnosticResultStatusVo>();
const failed = computed(() => status.value?.diagnosticStatus === 'FAILED');
const active = computed(() => status.value?.stages.filter(item => item.state === 'COMPLETED').length ?? 0);
const title = (code: string) =>
  ({
    SUBMITTED: '答卷已提交',
    SCORING: '正在评分',
    REPORT_GENERATING: '正在生成诊断报告',
    PROFILE_GENERATING: '正在生成初始画像',
    COMPLETED: '结果已生成'
  })[code] ?? code;

async function routeByAction(action: unknown) {
  await router.replace(resolveOnboardingTarget(action) ?? '/learning/onboarding-error');
}
async function ensureSessionId() {
  if (sessionId.value) return true;
  const onboarding = (await getOnboardingStatus()).data;
  if (onboarding?.activeSessionId) {
    sessionId.value = onboarding.activeSessionId;
    await router.replace({ path: '/learning/diagnostic/result', query: { sessionId: sessionId.value } });
    return true;
  }
  await routeByAction(onboarding?.nextAction);
  return false;
}
async function load() {
  loading.value = true;
  try {
    if (!(await ensureSessionId())) return;
    const response = await getDiagnosticResultStatus(sessionId.value);
    status.value = response.data;
    if (response.data?.diagnosticStatus === 'COMPLETED')
      await router.replace({ path: '/learning/diagnostic/report', query: { sessionId: sessionId.value } });
  } catch (error) {
    const detail = getHandledRequestError<{ nextAction?: string }>(error)?.responseData;
    await routeByAction(detail?.nextAction ?? 'WAIT_PROCESSING');
  } finally {
    loading.value = false;
  }
}
async function retry() {
  retrying.value = true;
  try {
    const response = await regenerateDiagnosticResult(sessionId.value, crypto.randomUUID());
    if (response.data?.nextAction && response.data.nextAction !== 'WAIT_PROCESSING')
      await routeByAction(response.data.nextAction);
    else await load();
  } finally {
    retrying.value = false;
  }
}
void load();
</script>

<style scoped>
.actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 24px;
}
</style>
