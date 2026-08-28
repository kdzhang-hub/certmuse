<template>
  <el-card v-if="submitted && suggestion?.status !== 'DISMISSED'" shadow="never" class="reinforcement-card">
    <template #header>
      <div class="reinforcement-card__header">
        <strong>同知识点强化</strong>
        <el-tag size="small" effect="plain">题库练习</el-tag>
      </div>
    </template>
    <el-skeleton v-if="!suggestion || suggestion.status === 'PREPARING'" :rows="2" animated />
    <el-alert
      v-else-if="suggestion.status === 'UNAVAILABLE'"
      title="题库中暂无更多可用的同知识点题目"
      type="info"
      :closable="false"
      show-icon
    />
    <template v-else>
      <p class="reinforcement-card__knowledge">{{ knowledgeNames }}</p>
      <p>{{ suggestion.recommendationReason }}</p>
      <p v-if="suggestion.status === 'READY'" class="reinforcement-card__meta">
        最多 {{ suggestion.estimatedCount }} 题，题目来自正式题库。
      </p>
      <div class="reinforcement-card__actions">
        <el-button v-if="suggestion.status === 'READY'" type="primary" :loading="working" @click="start">
          开始强化
        </el-button>
        <el-button v-if="suggestion.status === 'READY'" :disabled="working" @click="dismiss">暂不练习</el-button>
        <el-button v-if="suggestion.status === 'STARTED' && suggestion.answerPath" type="primary" @click="enter">
          进入强化练习
        </el-button>
        <el-button v-if="suggestion.status === 'COMPLETED'" type="primary" @click="viewResult">查看强化结果</el-button>
      </div>
    </template>
  </el-card>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import {
  createReinforcementRound,
  dismissReinforcement,
  getReinforcementSuggestion,
  type ReinforcementSuggestionVo
} from '@/api/certmuse/assessment/knowledge-practices';

const props = defineProps<{ sessionId: string; questionOrder: number; submitted: boolean }>();
const router = useRouter();
const suggestion = ref<ReinforcementSuggestionVo>();
const working = ref(false);
let pollTimer: ReturnType<typeof setTimeout> | undefined;
let retryCount = 0;
const knowledgeNames = computed(() => suggestion.value?.knowledgePoints.map(item => item.name).join('、') ?? '');

watch(
  () => [props.sessionId, props.questionOrder, props.submitted] as const,
  () => {
    if (pollTimer) clearTimeout(pollTimer);
    suggestion.value = undefined;
    retryCount = 0;
    void load();
  },
  { immediate: true }
);
onBeforeUnmount(() => pollTimer && clearTimeout(pollTimer));

async function load() {
  if (pollTimer) clearTimeout(pollTimer);
  if (!props.submitted) return;
  const requestKey = `${props.sessionId}:${props.questionOrder}`;
  try {
    const response = await getReinforcementSuggestion(props.sessionId, props.questionOrder);
    if (requestKey !== `${props.sessionId}:${props.questionOrder}`) return;
    suggestion.value = response.data;
    retryCount = 0;
    if (response.data?.status === 'PREPARING') pollTimer = setTimeout(() => void load(), 1500);
  } catch {
    if (requestKey !== `${props.sessionId}:${props.questionOrder}`) return;
    if (retryCount < 4) {
      retryCount += 1;
      pollTimer = setTimeout(() => void load(), 1500);
    }
  }
}
async function start() {
  working.value = true;
  try {
    const response = await createReinforcementRound(
      props.sessionId,
      props.questionOrder,
      crypto.randomUUID().toLowerCase()
    );
    if (suggestion.value && response.data)
      suggestion.value = {
        ...suggestion.value,
        status: 'STARTED',
        reinforcementSessionId: response.data.reinforcementSessionId,
        answerPath: response.data.answerPath,
        estimatedCount: response.data.actualCount
      };
    await load();
    ElMessage.success('强化练习已准备好，可在方便时进入。');
  } catch {
    ElMessage.error('强化练习暂时无法创建，请稍后重试。');
  } finally {
    working.value = false;
  }
}
async function dismiss() {
  working.value = true;
  try {
    await dismissReinforcement(props.sessionId, props.questionOrder);
    if (suggestion.value) suggestion.value = { ...suggestion.value, status: 'DISMISSED' };
  } finally {
    working.value = false;
  }
}
function enter() {
  if (suggestion.value?.answerPath) void router.push(suggestion.value.answerPath);
}
function viewResult() {
  if (suggestion.value)
    void router.push({
      path: '/learning/session/practice',
      query: {
        sessionId: props.sessionId,
        reinforcementRoundId: suggestion.value.roundId,
        reinforcementResult: '1'
      }
    });
}
</script>

<style scoped lang="scss">
.reinforcement-card {
  margin-top: 12px;
  border-color: var(--el-color-primary-light-7);
}
.reinforcement-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.reinforcement-card__knowledge {
  color: var(--el-color-primary);
  font-weight: 600;
}
.reinforcement-card__meta {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.reinforcement-card__actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
