<template>
  <div class="p-2 app-container learning-home">
    <section class="learning-home__hero">
      <div>
        <span class="hero-eyebrow">LEARNING CENTER</span>
        <h2>今天，完成下一步学习</h2>
        <p>任务、学习画像与错题复习都围绕当前学习目标独立统计。</p>
      </div>
      <el-button plain class="hero-history-button" @click="router.push('/learning/history')">
        学习记录
        <el-icon><Clock /></el-icon>
      </el-button>
    </section>

    <section class="learning-goal-bar" aria-label="当前学习目标">
      <div class="learning-goal-bar__content">
        <span class="learning-goal-bar__label">当前学习目标</span>
        <span v-if="currentGoal" class="learning-goal-bar__value">{{ currentGoal.certificationName }}</span>
        <span v-else-if="goalLoading" class="learning-goal-bar__value">正在加载目标信息…</span>
        <span v-else class="learning-goal-bar__value learning-goal-bar__value--muted">尚未设置学习目标</span>
        <span v-if="currentGoal" class="learning-goal-bar__separator" aria-hidden="true"></span>
        <span v-if="currentGoal" class="learning-goal-bar__batch">{{ batchLabel(currentGoal) }}</span>
      </div>
      <el-button type="primary" plain class="learning-goal-bar__action" @click="router.push('/learning/account')">
        切换学习目标
        <el-icon><ArrowRight /></el-icon>
      </el-button>
    </section>

    <section v-loading="loading" class="overview-grid" aria-label="学习概览">
      <el-card shadow="never" class="overview-card task-card">
        <div class="card-topline">
          <span class="card-eyebrow">今日任务</span>
          <el-icon><DocumentChecked /></el-icon>
        </div>
        <template v-if="tasks.total > 0">
          <div class="metric-line">
            <strong>{{ tasks.total }}</strong>
            <span>条任务待完成</span>
          </div>
          <div class="task-list" aria-label="待完成任务列表">
            <div class="task-list__header"><span>任务</span><span>操作</span></div>
            <div v-for="task in visibleTasks" :key="task.id" class="task-list__row">
              <span class="task-list__title" :title="task.title">{{ task.title }}</span>
              <el-button
                link
                type="primary"
                :loading="launchingTaskId === task.id"
                :disabled="Boolean(launchingTaskId)"
                @click="launchTask(task)"
              >
                {{ task.action === 'CONTINUE' ? '继续任务' : '开始任务' }}
              </el-button>
            </div>
          </div>
          <el-button v-if="tasks.total > visibleTasks.length" type="primary" plain class="card-action" @click="router.push('/learning/tasks')">
            查看全部任务
            <el-icon><ArrowRight /></el-icon>
          </el-button>
        </template>
        <template v-else>
          <div class="metric-line">
            <strong>0</strong>
            <span>条任务待完成</span>
          </div>
          <p class="card-description">今日任务已完成，可进入题库继续巩固。</p>
          <el-button type="primary" plain class="card-action" @click="router.push('/learning/question-bank')">
            去题库练习
            <el-icon><ArrowRight /></el-icon>
          </el-button>
        </template>
      </el-card>

      <el-card shadow="never" class="overview-card profile-card">
        <div class="card-topline">
          <span class="card-eyebrow">学习画像</span>
          <el-icon><TrendCharts /></el-icon>
        </div>
        <div class="metric-line metric-line--score">
          <strong>{{ overallScore === null ? '--' : overallScore.toFixed(1) }}</strong>
          <span>/ 100</span>
        </div>
        <p class="card-description">
          {{
            overallScore === null
              ? '尚未形成总体评分，完成正式作答后会逐步更新。'
              : '总体掌握度基于当前目标下有效的作答证据计算。'
          }}
        </p>
        <el-button type="primary" plain class="card-action" @click="router.push('/learning/progress')">
          查看学习画像
          <el-icon><ArrowRight /></el-icon>
        </el-button>
      </el-card>

      <el-card shadow="never" class="overview-card mistakes-card">
        <div class="card-topline">
          <span class="card-eyebrow">错题复习</span>
          <el-icon><WarningFilled /></el-icon>
        </div>
        <div class="metric-line">
          <strong>{{ pendingMistakeCount }}</strong>
          <span>道错题待订正</span>
        </div>
        <p class="card-description">
          {{
            pendingMistakeCount
              ? '重新完成原题后，还需要用同知识点新题验证掌握情况。'
              : '当前没有待订正错题，继续保持练习节奏。'
          }}
        </p>
        <el-button type="primary" plain class="card-action" @click="router.push('/learning/mistakes')">
          去错题复习
          <el-icon><ArrowRight /></el-icon>
        </el-button>
      </el-card>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ArrowRight, Clock, DocumentChecked, TrendCharts, WarningFilled } from '@element-plus/icons-vue';
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import type { StudentTaskPoolVO } from '@/api/student/types';
import { listMistakeReviews } from '@/api/certmuse/learning/mistakes';
import { getGoalSwitchOptions, type GoalSwitchCurrentGoalVo } from '@/api/certmuse/learning/goal-switch';
import { getOverallScore } from '@/api/certmuse/learning/overall-score';
import { listLearningTaskPool, launchLearningTask } from '@/api/certmuse/learning/tasks';

const router = useRouter();
const loading = ref(true);
const goalLoading = ref(true);
const launchingTaskId = ref<string>();
const overallScore = ref<number | null>(null);
const pendingMistakeCount = ref(0);
const currentGoal = ref<GoalSwitchCurrentGoalVo>();
const tasks = reactive<{ total: number; rows: StudentTaskPoolVO[] }>({ total: 0, rows: [] });

const visibleTasks = computed(() => tasks.rows.slice(0, 4));

const batchLabel = (goal: GoalSwitchCurrentGoalVo) =>
  `${goal.targetExamYear} 年 ${goal.targetExamMonth} 月${goal.examBatchType === 'OFFICIAL' ? '（官方）' : '（预计）'}`;

async function loadCurrentGoal() {
  goalLoading.value = true;
  try {
    currentGoal.value = (await getGoalSwitchOptions()).data?.currentGoal;
  } finally {
    goalLoading.value = false;
  }
}

async function loadOverview() {
  loading.value = true;
  const [taskResult, scoreResult, mistakeResult] = await Promise.allSettled([
    listLearningTaskPool({ pageNum: 1, pageSize: 4 }),
    getOverallScore(),
    listMistakeReviews({ pageNum: 1, pageSize: 1, status: 'PENDING_CORRECTION' })
  ]);

  if (taskResult.status === 'fulfilled') {
    tasks.total = taskResult.value.data?.total ?? 0;
    tasks.rows = taskResult.value.data?.rows ?? [];
  }
  if (scoreResult.status === 'fulfilled') overallScore.value = scoreResult.value.data?.overallScore ?? null;
  if (mistakeResult.status === 'fulfilled') pendingMistakeCount.value = mistakeResult.value.data?.total ?? 0;
  loading.value = false;
}

async function launchTask(task: StudentTaskPoolVO) {
  launchingTaskId.value = task.id;
  try {
    const response = await launchLearningTask(task.id);
    const result = response.data;
    if (!result) return;
    await router.push({
      path: '/learning/session/task',
      query: {
        taskId: result.taskId,
        ...(result.nextAction === 'OPEN_PRACTICE_SESSION'
          ? { phase: 'practice', sessionId: result.sessionId ?? undefined }
          : {})
      }
    });
  } finally {
    launchingTaskId.value = undefined;
  }
}

onMounted(() => {
  void loadOverview();
  void loadCurrentGoal();
});
</script>

<style lang="scss" scoped>
.learning-home {
  width: 100%;
  max-width: none;
  margin: 0 auto;
  padding-top: 20px;
}
.learning-goal-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  min-height: 78px;
  margin-bottom: 0;
  padding: 14px clamp(20px, 2.2vw, 36px);
  border: 1px solid var(--student-info-border);
  border-radius: 14px;
  background: linear-gradient(105deg, var(--student-info-surface-start) 0%, var(--student-info-surface-end) 100%);
  box-shadow: 0 8px 22px rgb(53 108 194 / 7%);
}
.learning-goal-bar__content {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.learning-goal-bar__label {
  flex: none;
  color: var(--el-text-color-secondary);
  font-size: clamp(14px, 1.05vw, 16px);
}
.learning-goal-bar__value {
  overflow: hidden;
  color: var(--student-info-title);
  font-size: clamp(19px, 1.55vw, 24px);
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.learning-goal-bar__value--muted {
  color: var(--el-text-color-secondary);
  font-weight: 500;
}
.learning-goal-bar__separator {
  width: 1px;
  height: 26px;
  background: var(--student-info-divider);
}
.learning-goal-bar__batch {
  flex: none;
  color: var(--student-info-muted);
  font-size: clamp(15px, 1.15vw, 18px);
}
.learning-goal-bar__action {
  flex: none;
  min-height: 42px;
  padding: 0 18px;
  font-size: 16px;
  font-weight: 600;
}
.learning-goal-bar__action .el-icon {
  margin-left: 5px;
}
.learning-home__hero {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 0;
  padding: 22px 28px;
  overflow: hidden;
  color: #fff;
  border-radius: 18px;
  background:
    radial-gradient(circle at 86% 12%, rgb(255 255 255 / 18%) 0 2px, transparent 2.5px) 0 0 / 23px 23px,
    linear-gradient(118deg, #1e4fc7 0%, #397de8 57%, #64a6f5 100%);
  box-shadow: 0 18px 36px rgb(41 106 209 / 22%);
}
.learning-home__hero::after { position: absolute; right: -82px; bottom: -128px; width: 35%; aspect-ratio: 1; border: 54px solid rgb(255 255 255 / 10%); border-radius: 50%; content: ''; }
.learning-home__hero > * { position: relative; z-index: 1; }
.hero-eyebrow,
.card-eyebrow {
  display: block;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
}
.hero-eyebrow {
  margin-bottom: 9px;
  color: rgb(255 255 255 / 72%);
}
.learning-home__hero h2 {
  margin: 0;
  font-size: 29px;
  letter-spacing: -0.02em;
}
.learning-home__hero p {
  margin: 10px 0 0;
  color: rgb(255 255 255 / 84%);
  font-size: 14px;
  line-height: 1.7;
}
.hero-history-button {
  flex: none;
  color: #285cb7;
  border-color: rgb(255 255 255 / 70%);
  background: rgb(255 255 255 / 95%);
  font-weight: 600;
}
.hero-history-button .el-icon {
  margin-left: 5px;
}
.overview-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(300px, 1fr);
  grid-template-rows: auto auto;
  align-items: start;
  gap: clamp(14px, 1.5vw, 18px);
}
.overview-card {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 18px;
  box-shadow: 0 10px 28px rgb(31 45 71 / 6%);
  transition:
    transform 0.2s,
    box-shadow 0.2s;
}
.overview-card:hover {
  box-shadow: 0 15px 30px rgb(31 45 71 / 10%);
  transform: translateY(-3px);
}
.overview-card :deep(.el-card__body) {
  display: flex;
  flex: 1;
  flex-direction: column;
  padding: 23px 24px;
}
.overview-card::after {
  position: absolute;
  right: -40px;
  bottom: -46px;
  width: 138px;
  height: 138px;
  border: 25px solid var(--card-wash);
  border-radius: 50%;
  content: '';
}
.task-card {
  --card-accent: #2869d8;
  --card-wash: rgb(40 105 216 / 10%);
  grid-row: span 2;
  height: 520px;
}
.profile-card {
  --card-accent: #3d9c78;
  --card-wash: rgb(61 156 120 / 10%);
  height: 251px;
}
.mistakes-card {
  --card-accent: #dc7a5a;
  --card-wash: rgb(220 122 90 / 10%);
  height: 251px;
}
.card-topline {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--card-accent);
}
.card-topline .card-eyebrow {
  font-size: clamp(19px, 1.45vw, 24px);
  font-weight: 800;
  letter-spacing: 0;
  line-height: 1.25;
}
.card-topline .el-icon {
  width: 22px;
  height: 22px;
  padding: 8px;
  border-radius: 12px;
  background: var(--card-wash);
  font-size: 22px;
}
.metric-line {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: 26px;
}
.metric-line strong {
  color: var(--card-accent);
  font-size: 47px;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.06em;
  line-height: 1;
}
.metric-line span {
  color: var(--el-text-color-secondary);
  font-size: 14px;
}
.metric-line--score {
  gap: 6px;
}
.metric-line--score strong {
  font-size: 45px;
}
.card-description {
  position: relative;
  z-index: 1;
  min-height: 46px;
  margin: 18px 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.75;
}
.task-list {
  position: relative;
  z-index: 1;
  display: grid;
  margin-top: 22px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
}
.task-list__header,
.task-list__row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 16px;
  padding: 12px 16px;
}
.task-list__header {
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-lighter);
  font-size: 13px;
  font-weight: 700;
}
.task-list__header span:last-child { text-align: right; }
.task-list__row { min-height: 54px; border-top: 1px solid var(--el-border-color-lighter); }
.task-list__title { overflow: hidden; color: var(--el-text-color-primary); font-size: 15px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.task-list__row :deep(.el-button) { min-width: 68px; font-size: 15px; font-weight: 600; }
.card-action {
  position: relative;
  z-index: 1;
  align-self: flex-start;
  margin-top: auto;
  border-color: color-mix(in srgb, var(--card-accent) 35%, transparent);
  color: var(--card-accent);
  background: color-mix(in srgb, var(--card-accent) 7%, transparent);
  font-weight: 600;
}
.card-action .el-icon,
.task-card__primary-action .el-icon {
  margin-left: 5px;
}
.task-card__primary-action {
  position: relative;
  z-index: 1;
  align-self: stretch;
  min-height: 54px;
  margin-top: auto;
  border: 0;
  background: var(--card-accent);
  font-size: 18px;
  font-weight: 600;
}
@media (max-width: 1100px) {
  .overview-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    grid-template-rows: auto;
    min-height: 0;
  }
  .task-card {
    grid-column: 1 / -1;
    grid-row: auto;
    height: auto;
  }
  .profile-card,
  .mistakes-card { height: auto; }
}
@media (max-width: 760px) {
  .overview-grid {
    grid-template-columns: 1fr;
  }
  .card-description {
    min-height: auto;
  }
}
@media (max-width: 640px) {
  .learning-home {
    padding-top: 10px;
  }
  .learning-home__hero {
    align-items: flex-start;
    flex-direction: column;
    min-height: 0;
    padding: 24px;
  }
  .learning-home__hero h2 {
    font-size: 24px;
  }
  .learning-goal-bar {
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
  }
  .learning-goal-bar__content {
    flex-wrap: wrap;
    gap: 7px 10px;
  }
  .learning-goal-bar__value {
    max-width: 100%;
  }
  .learning-goal-bar__separator {
    display: none;
  }
  .learning-goal-bar__batch {
    width: 100%;
  }
  .learning-goal-bar__action {
    width: 100%;
  }
  .overview-grid {
    gap: 14px;
  }
  .overview-card :deep(.el-card__body) {
    padding: 21px;
  }
  .task-list { margin-top: 18px; }
  .task-list__header,
  .task-list__row { padding: 11px 12px; gap: 10px; }
  .task-list__title { font-size: 14px; }
}
</style>
