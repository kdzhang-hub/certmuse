<template>
  <div class="p-2 app-container" v-loading="loading">
    <el-result v-if="!mistake && !loading" icon="warning" title="错题不存在" sub-title="该错题记录可能已被移除。">
      <template #extra>
        <el-button type="primary" @click="router.push('/learning/mistakes')">返回错题列表</el-button>
      </template>
    </el-result>
    <template v-else-if="mistake">
      <el-card shadow="hover">
        <template #header>
          <div class="card-header">
            <div>
              <el-tag :type="mistake.status === 'CORRECTED' ? 'success' : 'warning'">{{ statusText(mistake.status) }}</el-tag>
              <h2>{{ mistake.stemPreview }}</h2>
              <p>累计错误 {{ mistake.wrongCount }} 次 · 跳过 {{ mistake.skipCount }} 次</p>
            </div>
            <el-button plain @click="router.push('/learning/mistakes')">返回列表</el-button>
          </div>
        </template>

        <section class="detail-section">
          <h3>原题</h3>
          <template v-if="mistake.question">
            <p class="stem">{{ mistake.question.stem }}</p>
            <ol v-if="mistake.question.options.length" class="options">
              <li v-for="option in mistake.question.options" :key="option.label">
                <strong>{{ option.label }}.</strong> {{ optionContent(option.label, option.content) }}
              </li>
            </ol>
          </template>
          <el-alert v-else type="warning" :closable="false" show-icon title="原题快照暂不可用，仍可查看这道题的历史记录。" />
        </section>

        <section class="detail-section">
          <h3>作答记录</h3>
          <el-descriptions :column="1" border class="answer-summary">
            <el-descriptions-item label="作答来源">{{ sourceText(mistake.sources) }}</el-descriptions-item>
            <el-descriptions-item label="最近错误">{{ mistake.lastWrongAt }}</el-descriptions-item>
            <el-descriptions-item v-if="answerRevealed" label="当时答案">{{ mistake.originalAnswer.join('、') || '未作答' }}</el-descriptions-item>
            <el-descriptions-item v-if="answerRevealed" label="正确答案">{{ mistake.correctAnswer.join('、') || '以题目解析为准' }}</el-descriptions-item>
          </el-descriptions>

          <div class="answer-reveal-action">
            <template v-if="!answerRevealed">
              <span>建议先独立回想原题，再查看本次作答、正确答案与解析。</span>
              <el-button type="primary" @click="answerRevealed = true">查看答案和解析</el-button>
            </template>
            <el-tag v-else type="success">已显示答案和解析</el-tag>
          </div>
        </section>

        <section v-if="answerRevealed && mistake.analysis" class="detail-section">
          <h3>解析</h3>
          <p>{{ mistake.analysis }}</p>
        </section>

        <section class="detail-section">
          <h3>错误历史</h3>
          <el-table :data="mistake.history" border>
            <el-table-column label="时间" prop="occurredAt" min-width="170" />
            <el-table-column label="来源" min-width="120">
              <template #default="scope">{{ sourceLabel(scope.row.source) }}</template>
            </el-table-column>
            <el-table-column label="当时作答" min-width="150">
              <template #default="scope">{{ scope.row.answer.join('、') || '未作答' }}</template>
            </el-table-column>
            <el-table-column label="结果" min-width="100">
              <template #default="scope">{{ scope.row.skipped ? '跳过' : '答错' }}</template>
            </el-table-column>
          </el-table>
        </section>

        <el-alert
          :type="mistake.status === 'PENDING_CORRECTION' ? 'info' : 'success'"
          :closable="false"
          show-icon
          :title="mistake.status === 'PENDING_CORRECTION' ? '独立重做原题，答对后会移入“已订正”。' : '该题已订正，历史记录仍可在列表中筛选查看。'"
        />
        <div class="actions">
          <el-button v-if="mistake.status === 'PENDING_CORRECTION' && mistake.canCorrect" type="primary" :loading="starting" @click="startCorrection">
            开始重做原题
          </el-button>
          <el-button @click="router.push('/learning/mistakes')">返回列表</el-button>
        </div>
      </el-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  createMistakeCorrectionSession,
  getMistakeReview,
  type MistakeReviewDetail,
  type MistakeReviewStatus,
  type MistakeSource
} from '@/api/certmuse/learning/mistakes';
import { contractErrorFrom } from '@/utils/learning-goal';

const route = useRoute();
const router = useRouter();
const loading = ref(true);
const starting = ref(false);
const answerRevealed = ref(false);
const mistake = ref<MistakeReviewDetail>();
const sourceLabels: Record<MistakeSource, string> = {
  INITIAL_DIAGNOSIS: '首次诊断',
  DAILY_TASK: '每日任务',
  SELF_PRACTICE: '自主练习',
  PAST_PAPER: '历年真题',
  SIMULATION: '模拟考试'
};
const statusText = (status: MistakeReviewStatus) => status === 'CORRECTED' ? '已订正' : '待订正';
const sourceLabel = (source: MistakeSource) => sourceLabels[source] ?? '其他练习';
const sourceText = (sources: MistakeSource[]) => sources.map(sourceLabel).join('、');
const optionContent = (label: string, content: string) => content.replace(new RegExp(`^\\s*${label}[.、．:：]?\\s*`, 'i'), '');

const startCorrection = async () => {
  if (!mistake.value) return;
  starting.value = true;
  try {
    const response = await createMistakeCorrectionSession({ questionIds: [mistake.value.questionId] });
    if (!response.data) throw new Error('未创建重做会话。');
    await router.push({ path: '/learning/session/correction', query: { sessionId: response.data.sessionId } });
  } catch (error) {
    const contractError = contractErrorFrom(error) as ReturnType<typeof contractErrorFrom> & { details?: { sessionId?: string } };
    if (contractError?.errorCode === 'MISTAKE_CORRECTION_IN_PROGRESS' && contractError.details?.sessionId) {
      ElMessage.info('已有进行中的错题订正，已为你继续该会话。');
      await router.push({ path: '/learning/session/correction', query: { sessionId: contractError.details.sessionId } });
      return;
    }
    ElMessage.error(error instanceof Error ? error.message : '无法开始错题订正。');
  } finally {
    starting.value = false;
  }
};

onMounted(async () => {
  try {
    const response = await getMistakeReview(String(route.params.mistakeId));
    mistake.value = response.data;
    answerRevealed.value = false;
  } finally {
    loading.value = false;
  }
});
</script>

<style lang="scss" scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;

  h2,
  p {
    margin: 10px 0 0;
  }

  p {
    color: var(--el-text-color-secondary);
  }
}

.detail-section {
  margin-top: 28px;
  padding-top: 24px;
  border-top: 1px solid var(--el-border-color-lighter);

  &:first-of-type {
    margin-top: 0;
    padding-top: 0;
    border-top: 0;
  }

  h3 {
    margin: 0 0 12px;
    font-size: 16px;
  }

  p {
    color: var(--el-text-color-regular);
    line-height: 1.8;
  }
}

.stem {
  font-size: 16px;
}

.options {
  display: grid;
  gap: 10px;
  padding-left: 24px;
  line-height: 1.7;
}

.answer-reveal-action {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 16px;
  padding: 14px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 20px;
}
</style>
