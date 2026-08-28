<template>
  <div class="p-2 app-container" v-loading="loading">
    <el-card shadow="hover" class="account-card">
      <template #header>
        <div class="card-header">
          <div>
            <h2>个人中心</h2>
            <p>查看账户信息与当前备考目标。</p>
          </div>
        </div>
      </template>
      <el-descriptions :column="descriptionColumns" border>
        <el-descriptions-item label="姓名">{{ userStore.nickname || '学员' }}</el-descriptions-item>
        <el-descriptions-item label="账号">{{ userStore.userId || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="hover" class="goal-card">
      <template #header>
        <div class="card-header">
          <div>
            <h2>当前学习目标</h2>
            <p>切换资格会暂停当前目标；历史学习记录仍保留在原目标中。</p>
          </div>
          <el-button v-if="switchOptions" type="primary" plain @click="openSwitchDialog">切换学习目标</el-button>
        </div>
      </template>

      <el-result v-if="loadError" icon="error" title="学习目标暂时无法加载" :sub-title="loadError">
        <template #extra><el-button type="primary" @click="loadGoal">重新加载</el-button></template>
      </el-result>

      <template v-else-if="switchOptions">
        <el-descriptions :column="descriptionColumns" border>
          <el-descriptions-item label="目标资格">{{ switchOptions.currentGoal.certificationName }}</el-descriptions-item>
          <el-descriptions-item label="目标考试批次">
            {{ batchLabel(switchOptions.currentGoal) }}
          </el-descriptions-item>
          <el-descriptions-item label="适用考纲">{{ switchOptions.currentGoal.syllabusVersionName }}</el-descriptions-item>
          <el-descriptions-item label="目标状态"><el-tag type="success">进行中</el-tag></el-descriptions-item>
        </el-descriptions>
      </template>

      <el-empty v-else description="尚未设置学习目标">
        <el-button type="primary" @click="router.push('/learning/onboarding')">设置第一个学习目标</el-button>
      </el-empty>
    </el-card>

    <el-dialog v-model="switchDialogVisible" title="切换学习目标" width="560px" :close-on-click-modal="false">
      <el-alert
        title="切换后，当前目标会暂停；新的资格将使用独立的学习记录。"
        type="info"
        :closable="false"
        show-icon
      />

      <el-alert
        v-if="switchOptions?.interruptedSessions.length"
        class="impact-alert"
        type="warning"
        :closable="false"
        show-icon
      >
        <template #title>以下未完成会话会被放弃，不能继续作答</template>
        <ul>
          <li v-for="item in switchOptions.interruptedSessions" :key="`${item.sessionType}-${item.title}`">
            {{ item.title }}（{{ sessionStatusText(item.status) }}）
          </li>
        </ul>
      </el-alert>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="108px" class="switch-form">
        <el-form-item label="新的目标资格" prop="certificationId">
          <el-select v-model="form.certificationId" class="form-control" :disabled="saving">
            <el-option
              v-for="item in switchOptions?.certifications ?? []"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="目标考试批次" prop="batchKey">
          <el-select v-model="form.batchKey" class="form-control" :disabled="saving">
            <el-option
              v-for="item in availableBatches"
              :key="batchKey(item.targetExamYear, item.targetExamMonth)"
              :label="batchLabel(item)"
              :value="batchKey(item.targetExamYear, item.targetExamMonth)"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="switchOptions?.interruptedSessions.length" prop="confirmAbandonInProgress">
          <el-checkbox v-model="form.confirmAbandonInProgress" :disabled="saving">
            我已知悉未完成会话会被放弃，且无法恢复。
          </el-checkbox>
        </el-form-item>
      </el-form>

      <el-alert v-if="formError" class="form-error" type="error" :title="formError" :closable="false" show-icon />

      <template #footer>
        <el-button :disabled="saving" @click="switchDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitSwitch">确认切换</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { useMediaQuery } from '@vueuse/core';
import { useRouter } from 'vue-router';
import type { FormInstance, FormRules } from 'element-plus';
import { getOnboardingStatus } from '@/api/certmuse/learning/onboarding';
import {
  getGoalSwitchOptions,
  switchLearningGoal,
  type GoalSwitchErrorVo,
  type GoalSwitchOptionsVo,
  type SwitchLearningGoalRequest
} from '@/api/certmuse/learning/goal-switch';
import { useUserStore } from '@/store/modules/user';
import modal from '@/plugins/modal';

const router = useRouter();
const userStore = useUserStore();
const isCompactScreen = useMediaQuery('(max-width: 640px)');
const descriptionColumns = computed(() => (isCompactScreen.value ? 1 : 2));
const loading = ref(false);
const saving = ref(false);
const loadError = ref('');
const formError = ref('');
const switchOptions = ref<GoalSwitchOptionsVo>();
const switchDialogVisible = ref(false);
const formRef = ref<FormInstance>();
const form = reactive({
  certificationId: '',
  batchKey: '',
  confirmAbandonInProgress: false
});
const requestIdentity = ref<{ fingerprint: string; id: string }>();
const selectedCertification = computed(() =>
  switchOptions.value?.certifications.find(item => item.id === form.certificationId)
);
const availableBatches = computed(() => selectedCertification.value?.examBatches ?? []);
const selectedBatch = computed(() =>
  availableBatches.value.find(item => batchKey(item.targetExamYear, item.targetExamMonth) === form.batchKey)
);
const rules: FormRules = {
  certificationId: [{ required: true, message: '请选择新的目标资格', trigger: 'change' }],
  batchKey: [{ required: true, message: '请选择目标考试批次', trigger: 'change' }],
  confirmAbandonInProgress: [
    {
      validator: (_rule, value, callback) => {
        if (switchOptions.value?.interruptedSessions.length && value !== true) callback(new Error('请确认放弃未完成会话'));
        else callback();
      },
      trigger: 'change'
    }
  ]
};

watch(availableBatches, batches => {
  if (!batches.some(item => batchKey(item.targetExamYear, item.targetExamMonth) === form.batchKey)) {
    const first = batches[0];
    form.batchKey = first ? batchKey(first.targetExamYear, first.targetExamMonth) : '';
  }
});

watch(
  () => [form.certificationId, form.batchKey, form.confirmAbandonInProgress] as const,
  () => {
    requestIdentity.value = undefined;
    formError.value = '';
  }
);

const sessionStatusText = (status: 'CREATED' | 'IN_PROGRESS') => (status === 'CREATED' ? '未开始' : '进行中');
const batchKey = (year: number, month: number) => `${year}-${month}`;
const batchLabel = (batch: {
  targetExamYear: number;
  targetExamMonth: number;
  examBatchType: string;
  targetExamDate?: string | null;
}) =>
  `${batch.targetExamYear} 年 ${batch.targetExamMonth} 月${batch.examBatchType === 'OFFICIAL' ? '（官方）' : '（预计）'}`;

const isGoalSwitchError = (value: unknown): value is GoalSwitchErrorVo =>
  typeof value === 'object' && value !== null && 'errorCode' in value;

const errorDetail = (error: unknown): GoalSwitchErrorVo | undefined => {
  const candidate = error as { responseData?: unknown; response?: { data?: { data?: unknown } } };
  const detail = candidate?.responseData ?? candidate?.response?.data?.data;
  return isGoalSwitchError(detail) ? detail : undefined;
};

const syncFormDefaults = (options: GoalSwitchOptionsVo) => {
  form.certificationId = options.certifications[0]?.id ?? '';
  const firstBatch = options.certifications[0]?.examBatches[0];
  form.batchKey = firstBatch ? batchKey(firstBatch.targetExamYear, firstBatch.targetExamMonth) : '';
  form.confirmAbandonInProgress = false;
};

const loadGoal = async () => {
  loading.value = true;
  loadError.value = '';
  try {
    const onboarding = (await getOnboardingStatus()).data;
    if (onboarding?.nextAction === 'SET_GOAL') {
      switchOptions.value = undefined;
      return;
    }
    const response = await getGoalSwitchOptions();
    if (!response.data) throw new Error('当前学习目标数据缺失，请重新加载。');
    switchOptions.value = response.data;
  } catch (error) {
    const detail = errorDetail(error);
    if (detail?.errorCode === 'LEARNING_GOAL_NOT_ACTIVE') {
      switchOptions.value = undefined;
      return;
    }
    loadError.value = error instanceof Error ? error.message : '加载失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
};

const openSwitchDialog = () => {
  if (!switchOptions.value) return;
  syncFormDefaults(switchOptions.value);
  formError.value = '';
  switchDialogVisible.value = true;
};

const switchPayload = (): SwitchLearningGoalRequest => ({
  certificationId: form.certificationId,
  targetExamYear: selectedBatch.value?.targetExamYear ?? 0,
  targetExamMonth: selectedBatch.value?.targetExamMonth ?? 5,
  expectedCurrentGoalVersion: switchOptions.value?.currentGoal.version ?? -1,
  confirmAbandonInProgress: form.confirmAbandonInProgress
});

const requestIdForPayload = (payload: SwitchLearningGoalRequest) => {
  const fingerprint = JSON.stringify(payload);
  if (requestIdentity.value?.fingerprint === fingerprint) return requestIdentity.value.id;
  const next = { fingerprint, id: crypto.randomUUID() };
  requestIdentity.value = next;
  return next.id;
};

const submitSwitch = async () => {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid || !switchOptions.value) return;
  const payload = switchPayload();
  saving.value = true;
  formError.value = '';
  try {
    const response = await switchLearningGoal(payload, requestIdForPayload(payload));
    if (response.data?.nextAction !== 'ENTER_HOME') throw new Error('学习目标已切换，但返回状态异常，请重新加载。');
    modal.msgSuccess('学习目标已切换');
    await router.replace('/learning/home');
  } catch (error) {
    const detail = errorDetail(error);
    if (detail?.errorCode === 'GOAL_SWITCH_CONFIRM_REQUIRED') {
      formError.value = `还有 ${detail.details?.abandonedSessionCount ?? 0} 个未完成会话，请确认后再切换。`;
    } else if (detail?.errorCode === 'GOAL_VERSION_CONFLICT') {
      switchDialogVisible.value = false;
      await loadGoal();
      formError.value = '当前目标已发生变化，请重新确认切换内容。';
    } else if (detail?.errorCode === 'TARGET_EXAM_BATCH_INVALID' || detail?.errorCode === 'CERTIFICATION_UNAVAILABLE') {
      await loadGoal();
      formError.value = '可切换目标已更新，请重新选择。';
    } else if (detail?.errorCode === 'LEARNING_GOAL_NOT_ACTIVE') {
      switchDialogVisible.value = false;
      switchOptions.value = undefined;
    } else if (detail?.errorCode === 'GOAL_SWITCH_SAME_CERTIFICATION') {
      formError.value = '请选择不同于当前目标的资格。';
    } else {
      formError.value = error instanceof Error ? error.message : '切换失败，请稍后重试。';
    }
  } finally {
    saving.value = false;
  }
};

void loadGoal();
</script>

<style lang="scss" scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.card-header h2,
.card-header p {
  margin: 0;
}

.card-header p {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.goal-card {
  margin-top: 16px;
}

.switch-form {
  margin-top: 20px;
}

.form-control {
  width: 100%;
}

.impact-alert,
.form-error {
  margin-top: 16px;
}

.impact-alert ul {
  margin: 8px 0 0;
  padding-left: 20px;
}

@media (max-width: 640px) {
  .card-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .card-header :deep(.el-button) {
    width: 100%;
  }

  .switch-form {
    margin-top: 16px;
  }

  .switch-form :deep(.el-form-item) {
    display: block;
  }

  .switch-form :deep(.el-form-item__label) {
    display: block;
    width: auto !important;
    margin-bottom: 6px;
    line-height: 1.5;
    text-align: left;
  }

  .switch-form :deep(.el-form-item__content) {
    margin-left: 0 !important;
  }
}

</style>
