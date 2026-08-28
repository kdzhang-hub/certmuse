<template>
  <div class="p-2 app-container diagnostic-intro" v-loading="loading">
    <el-row justify="center">
      <el-col :xs="24" :md="18" :lg="14">
        <el-card shadow="hover">
          <template #header>
            <el-tag type="success" effect="plain">首次诊断</el-tag>
            <h2>开始前，先了解本次诊断</h2>
          </template>
          <el-result v-if="error" icon="error" title="诊断暂时不可开始" :sub-title="error">
            <template #extra><el-button type="primary" @click="load">重新加载</el-button></template>
          </el-result>
          <template v-else-if="preflight">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="学习目标">
                {{ preflight.goal.certificationName }} · {{ preflight.goal.syllabusVersionName }}
              </el-descriptions-item>
              <el-descriptions-item label="本次题量">{{ preflight.diagnosticRevision?.questionCount ?? 0 }} 题</el-descriptions-item>
              <el-descriptions-item label="预计时间">
                约 {{ preflight.diagnosticRevision?.estimatedMinutes ?? 50 }} 分钟
              </el-descriptions-item>
              <el-descriptions-item v-if="preflight.diagnosticRevision?.subjectBreakdown.length" label="科目覆盖">
                <el-tag
                  v-for="item in preflight.diagnosticRevision.subjectBreakdown"
                  :key="item.examSubjectId"
                  class="mr-2"
                >
                  {{ item.subjectName }} {{ item.questionCount }} 题
                </el-tag>
              </el-descriptions-item>
            </el-descriptions>
            <el-alert
              class="mt-4"
              type="info"
              :closable="false"
              show-icon
              title="诊断用于了解当前基础，不代表正式考试成绩。答题中不展示答案、解析、提示、对错或分数。"
            />
            <ul class="rules">
              <li>可以随时暂停或离开；已成功保存的进度会在下次恢复。</li>
              <li>交卷后答案不可修改，系统会基于真实作答生成诊断报告和初始画像。</li>
            </ul>
            <el-alert
              v-if="preflight.existingSession"
              class="mt-3"
              type="warning"
              :closable="false"
              :title="`你有一份未完成的首次诊断，当前已完成 ${preflight.existingSession.answeredCount}/${preflight.existingSession.totalCount} 题。`"
            />
            <div class="actions">
              <el-button @click="router.replace('/learning/home')">返回学习首页</el-button>
              <el-button type="primary" :loading="starting" @click="start">
                {{ preflight.existingSession ? '继续诊断' : '开始诊断' }}
              </el-button>
            </div>
          </template>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  getDiagnosticPreflight,
  startDiagnostic,
  type DiagnosticPreflightVo
} from '@/api/certmuse/learning/diagnostics';
import { resolveOnboardingTarget } from '@/utils/onboarding-routing';
import { getHandledRequestError } from '@/utils/request';

const router = useRouter();
const loading = ref(true);
const starting = ref(false);
const error = ref('');
const preflight = ref<DiagnosticPreflightVo>();

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const response = await getDiagnosticPreflight();
    if (!response.data) throw new Error('诊断预检数据缺失。');
    preflight.value = response.data;
    if (response.data.nextAction !== 'START_DIAGNOSTIC' && response.data.nextAction !== 'CONTINUE_DIAGNOSTIC') {
      await router.replace(resolveOnboardingTarget(response.data.nextAction) ?? '/learning/onboarding-error');
    }
  } catch (caught) {
    const detail = getHandledRequestError<{ errorCode?: string; nextAction?: string; traceId?: string }>(
      caught
    )?.responseData;
    const target = resolveOnboardingTarget(detail?.nextAction);
    if (target) {
      if (detail?.errorCode === 'FIRST_DIAGNOSTIC_NOT_READY' && detail.nextAction === 'SET_GOAL')
        ElMessage.warning('请先设置学习目标，再开始首次诊断。');
      await router.replace(target);
      return;
    }
    const message = caught instanceof Error ? caught.message : '加载失败，请稍后重试。';
    error.value = detail?.traceId ? `${message}（错误编号：${detail.traceId}）` : message;
  } finally {
    loading.value = false;
  }
}

async function start() {
  const existing = preflight.value?.existingSession;
  starting.value = true;
  try {
    if (existing) {
      await router.push({ path: '/learning/diagnostic/session', query: { sessionId: existing.id } });
      return;
    }
    const revision = preflight.value?.diagnosticRevision;
    if (!revision || !preflight.value) throw new Error('诊断预检数据缺失，请重新加载。');
    const response = await startDiagnostic(
      { diagnosticRevisionId: revision.id, goalVersion: preflight.value.goal.version },
      crypto.randomUUID()
    );
    if (!response.data) throw new Error('未能创建诊断会话。');
    await router.push({ path: '/learning/diagnostic/session', query: { sessionId: response.data.sessionId } });
  } catch (caught) {
    const detail = getHandledRequestError<{ nextAction?: string }>(caught)?.responseData;
    const target = resolveOnboardingTarget(detail?.nextAction);
    if (target) {
      await router.replace(target);
      return;
    }
    error.value = caught instanceof Error ? caught.message : '开始失败，请稍后重试。';
  } finally {
    starting.value = false;
  }
}

void load();
</script>

<style lang="scss" scoped>
h2 {
  margin: 12px 0 0;
}
.rules {
  line-height: 2;
  padding-left: 20px;
}
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
}
</style>
