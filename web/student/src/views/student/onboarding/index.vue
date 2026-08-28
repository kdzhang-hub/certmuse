<template>
  <div class="p-2 app-container">
    <el-row justify="center">
      <el-col :xs="24" :md="18" :lg="14">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <div>
                <h2>设置学习目标</h2>
                <p>保存后进入学习首页；你可按需要发起首次诊断，了解当前学习基础。</p>
              </div>
            </div>
          </template>

          <el-skeleton v-if="loading" :rows="4" animated />

          <el-result v-else-if="loadError" icon="error" title="目标设置暂时无法加载" :sub-title="loadError">
            <template #extra><el-button type="primary" @click="loadPage">重新加载</el-button></template>
          </el-result>

          <el-empty v-else-if="!options?.defaults" description="当前暂无可设置的学习目标，请稍后再试。" />

          <el-form v-else ref="formRef" :model="form" :rules="rules" label-width="104px" @submit.prevent>
            <el-form-item label="目标资格" prop="certificationId" :error="fieldErrors.certificationId">
              <el-select v-model="form.certificationId" class="form-control" :disabled="saving">
                <el-option v-for="item in options.certifications" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>

            <el-form-item
              label="考试批次"
              prop="targetExamYear"
              :error="fieldErrors.targetExamYear || fieldErrors.targetExamMonth"
            >
              <div class="exam-batch-selects">
                <el-select v-model="form.targetExamYear" aria-label="考试年份" :disabled="saving">
                  <el-option
                    v-for="item in options.examYears"
                    :key="item.year"
                    :label="`${item.year} 年`"
                    :value="item.year"
                    :disabled="!item.selectable"
                  />
                </el-select>
                <el-select v-model="form.targetExamMonth" aria-label="考试月份" :disabled="saving">
                  <el-option v-for="month in availableMonths" :key="month" :label="`${month} 月`" :value="month" />
                </el-select>
              </div>
            </el-form-item>

            <el-alert v-if="formError" class="mb-4" type="error" :title="formError" :closable="false" show-icon />

            <el-form-item>
              <el-button type="primary" :loading="saving" @click="submit">保存学习目标，进入学习首页</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import type { OnboardingNextAction } from '@/api/types';
import {
  getLearningGoalOptions,
  createLearningGoal,
  type ContractErrorVo,
  type CreateLearningGoalRequest,
  type LearningGoalOptionsVo
} from '@/api/certmuse/learning/goals';
import { getOnboardingStatus } from '@/api/certmuse/learning/onboarding';
import { useUserStore } from '@/store/modules/user';
import { contractErrorFrom, requestIdentityFor, type RequestIdentity } from '@/utils/learning-goal';
import { resolveOnboardingTarget } from '@/utils/onboarding-routing';

const router = useRouter();
const formRef = ref();
const loading = ref(true);
const saving = ref(false);
const loadError = ref('');
const formError = ref('');
const options = ref<LearningGoalOptionsVo>();
const requestIdentity = ref<RequestIdentity>();
const fieldErrors = reactive<Record<string, string>>({});
const form = reactive({ certificationId: '', targetExamYear: 0, targetExamMonth: 5 as 5 | 11, dailyMinutes: 30 });
const availableMonths = computed(
  () => options.value?.examYears.find(item => item.year === form.targetExamYear)?.months ?? []
);
const rules = {
  certificationId: [{ required: true, message: '请选择目标资格', trigger: 'change' }],
  targetExamYear: [{ required: true, message: '请选择考试年份', trigger: 'change' }],
  targetExamMonth: [{ required: true, message: '请选择考试月份', trigger: 'change' }]
};

watch(
  () => form.targetExamYear,
  () => {
    if (!availableMonths.value.includes(form.targetExamMonth)) form.targetExamMonth = availableMonths.value[0] ?? 5;
  }
);

watch(
  () => [form.certificationId, form.targetExamYear, form.targetExamMonth] as const,
  () => {
    requestIdentity.value = undefined;
    Object.keys(fieldErrors).forEach(key => delete fieldErrors[key]);
  }
);

function syncDefaults(nextOptions: LearningGoalOptionsVo) {
  const defaults = nextOptions.defaults;
  if (!defaults) return;
  if (!nextOptions.certifications.some(item => item.id === form.certificationId))
    form.certificationId = defaults.certificationId ?? '';
  if (!nextOptions.examYears.some(item => item.year === form.targetExamYear && item.selectable))
    form.targetExamYear = defaults.examYear;
  if (!nextOptions.examYears.find(item => item.year === form.targetExamYear)?.months.includes(form.targetExamMonth))
    form.targetExamMonth = defaults.examMonth;
  // 每日时长暂不作为用户设置项；为兼容当前创建目标接口，始终提交服务端给出的默认值。
  form.dailyMinutes = nextOptions.dailyMinutes.defaultValue;
}

async function routeByAction(action: OnboardingNextAction) {
  const target = resolveOnboardingTarget(action);
  if (!target) throw new Error('首次流程状态未知，请刷新后重试。');
  await router.replace(target);
}

async function routeAfterGoalExists(action: OnboardingNextAction) {
  if (action === 'START_DIAGNOSTIC' || action === 'CONTINUE_DIAGNOSTIC') {
    await router.replace('/learning/home');
    return;
  }
  await routeByAction(action);
}

async function loadPage() {
  loading.value = true;
  loadError.value = '';
  formError.value = '';
  try {
    const status = await getOnboardingStatus();
    if (status.data?.nextAction !== 'SET_GOAL') {
      if (!status.data?.nextAction) throw new Error('首次流程状态缺失，请刷新后重试。');
      await routeAfterGoalExists(status.data.nextAction);
      return;
    }
    const response = await getLearningGoalOptions();
    if (!response.data) throw new Error('目标设置选项缺失，请重新加载。');
    options.value = response.data;
    syncDefaults(response.data);
  } catch (error) {
    const detail = contractErrorFrom(error);
    if (detail?.errorCode === 'GOAL_ALREADY_EXISTS') {
      await refreshOnboarding();
      return;
    }
    loadError.value = error instanceof Error ? error.message : '加载失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
}

async function refreshOnboarding() {
  const status = (await getOnboardingStatus()).data;
  if (!status?.nextAction) throw new Error('首次流程状态缺失，请刷新后重试。');
  await routeAfterGoalExists(status.nextAction);
}

async function refreshOptionsAfterExpiry(errorCode: string) {
  const response = await getLearningGoalOptions();
  if (!response.data) throw new Error('目标设置选项缺失，请重新加载。');
  options.value = response.data;
  syncDefaults(response.data);
  if (errorCode === 'CERTIFICATION_UNAVAILABLE') {
    form.certificationId = '';
    form.targetExamYear = 0;
    form.targetExamMonth = 5;
  }
  formError.value =
    errorCode === 'TARGET_EXAM_BATCH_INVALID' ? '考试批次已失效，已为你更新可选批次。' : '目标资格已更新，请重新选择。';
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid || !options.value) return;
  const payload: CreateLearningGoalRequest = { ...form };
  requestIdentity.value = requestIdentityFor(payload, requestIdentity.value);
  saving.value = true;
  formError.value = '';
  try {
    const response = await createLearningGoal(payload, requestIdentity.value.id);
    if (response.data?.nextAction !== 'START_DIAGNOSTIC' && response.data?.nextAction !== 'ENTER_HOME')
      throw new Error('学习目标已保存，但首次流程状态异常，请刷新后重试。');
    await router.replace('/learning/home');
  } catch (error) {
    const detail = contractErrorFrom(error);
    const code = detail?.errorCode;
    applyFieldErrors(detail);
    if (code === 'TARGET_EXAM_BATCH_INVALID' || code === 'CERTIFICATION_UNAVAILABLE') {
      await refreshOptionsAfterExpiry(code);
    } else if (code === 'GOAL_ALREADY_EXISTS') {
      await refreshOnboarding();
    } else if (code === 'DAILY_MINUTES_OUT_OF_RANGE') {
      form.dailyMinutes = options.value.dailyMinutes.defaultValue;
      formError.value = '目标设置选项已更新，请重新提交。';
    } else if (code === 'GOAL_IDEMPOTENCY_CONFLICT') {
      requestIdentity.value = undefined;
      formError.value = '提交请求发生冲突，已停止重试并刷新首次流程状态。';
      await refreshOnboarding();
    } else if (code === 'FIRST_DIAGNOSTIC_NOT_READY') {
      formError.value = '首次诊断暂不可开始，请稍后使用相同内容重试。';
    } else if (code === 'LEARNING_GOAL_FORBIDDEN') {
      formError.value = '当前账号没有设置学习目标的权限。';
    } else if (code === 'UNAUTHORIZED') {
      useUserStore().clearSession();
      await router.replace('/login?redirect=' + encodeURIComponent('/learning/onboarding'));
    } else if (code === 'LEARNING_GOAL_CREATE_FAILED') {
      formError.value = `学习目标保存失败，请稍后重试${detail?.traceId ? `（错误编号：${detail.traceId}）` : ''}。`;
    } else {
      formError.value = error instanceof Error ? error.message : '保存失败，请稍后重试。';
    }
  } finally {
    saving.value = false;
  }
}

function applyFieldErrors(detail?: ContractErrorVo) {
  Object.keys(fieldErrors).forEach(key => delete fieldErrors[key]);
  detail?.fieldErrors.forEach(item => {
    if (['certificationId', 'targetExamYear', 'targetExamMonth'].includes(item.field))
      fieldErrors[item.field] = item.message;
  });
}

void loadPage();
</script>

<style lang="scss" scoped>
.card-header h2,
.card-header p {
  margin: 0;
}

.card-header p {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.form-control,
.exam-batch-selects {
  width: 100%;
}

.exam-batch-selects {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

@media (max-width: 480px) {
  .exam-batch-selects {
    grid-template-columns: 1fr;
  }
}
</style>
