<template>
  <div class="p-2 app-container mock-exams-page">
    <section class="page-intro">
      <div>
        <span class="page-intro__eyebrow">QUESTION BANK</span>
        <h2>模拟试卷</h2>
        <p>浏览平台当前发布的固定模拟卷，查看正式考试说明。</p>
      </div>
      <el-button class="page-intro__back" size="large" @click="router.push('/learning/question-bank')">返回题库</el-button>
    </section>

    <section v-if="setupLoading" class="page-state" aria-live="polite"><el-skeleton :rows="8" animated /></section>
    <section v-else-if="loadError" class="page-state">
      <el-result icon="warning" title="模拟试卷暂不可用" :sub-title="loadError">
        <template #extra><el-button type="primary" @click="reloadAll">重新加载</el-button></template>
      </el-result>
    </section>

    <template v-else>
      <el-card class="filter-card" shadow="never">
        <el-form :inline="true" label-position="top" class="filter-form" @submit.prevent="search">
          <el-form-item label="考试资格">
            <el-select v-model="filters.certificationId" clearable placeholder="全部资格">
              <el-option v-for="item in certificationOptions" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="试卷名称">
            <el-input
              v-model="filters.keyword"
              clearable
              placeholder="搜索模拟试卷"
              :prefix-icon="Search"
              @keyup.enter="search"
            />
          </el-form-item>
          <el-form-item class="filter-form__actions">
            <el-button type="primary" native-type="submit">查询</el-button>
            <el-button @click="resetFilters">重置</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <section class="list-heading">
        <div>
          <span class="list-heading__eyebrow">PUBLISHED PAPERS</span>
          <h3>已发布模拟试卷</h3>
        </div>
        <span>共 {{ total }} 份</span>
      </section>

      <section
        v-loading="listLoading"
        class="paper-list"
        :class="{ 'paper-list--empty': !listLoading && !papers.length }"
        aria-label="模拟试卷列表"
      >
        <el-card v-for="paper in papers" :key="paper.collectionId" class="paper-card" shadow="never">
          <div class="paper-card__main">
            <div class="paper-card__title-row">
              <div>
                <h4>{{ paper.collectionName }}</h4>
                <p>
                  {{ paper.certificationName }}
                  <template v-if="paper.subject">· {{ paper.subject.name }}</template>
                </p>
              </div>
              <el-tag type="success" effect="light">已发布</el-tag>
            </div>
            <div class="paper-card__metrics">
              <div>
                <span>题目数量</span>
                <strong>{{ paper.questionCount }} 道</strong>
              </div>
              <div>
                <span>试卷总分</span>
                <strong>{{ formatScore(paper.totalReportScore) }} 分</strong>
              </div>
              <div>
                <span>考试时长</span>
                <strong>{{ paper.durationMinutes }} 分钟</strong>
              </div>
            </div>
            <div class="paper-card__meta">
              <el-tag v-for="type in paper.questionTypes" :key="type" size="small" effect="plain">
                {{ questionTypeText(type) }}
              </el-tag>
              <span v-if="paper.access.blockCode" class="paper-card__notice">
                <el-icon><Timer /></el-icon>
                {{ accessMessage(paper.access.blockCode) }}
              </span>
            </div>
          </div>
          <div class="paper-card__actions">
            <el-button @click="openPreview(paper.collectionId)">查看试卷</el-button>
            <el-button type="primary" :disabled="!paper.access.canStart" @click="startExam(paper)">
              {{ paper.access.action === 'RESUME' ? '继续考试' : paper.access.canStart ? '开始考试' : '暂不可用' }}
              <el-icon v-if="paper.access.canStart" class="el-icon--right"><ArrowRight /></el-icon>
            </el-button>
          </div>
        </el-card>
        <el-empty
          v-if="!listLoading && !papers.length"
          class="empty-state"
          :image-size="96"
          description="没有符合条件的已发布模拟试卷"
        >
          <el-button @click="resetFilters">清除筛选条件</el-button>
        </el-empty>
      </section>

      <el-pagination
        v-if="total > pageSize"
        class="paper-pagination"
        background
        layout="prev, pager, next"
        :current-page="pageNum"
        :page-size="pageSize"
        :total="total"
        @current-change="changePage"
      />
    </template>

    <el-dialog v-model="previewVisible" title="模拟试卷" width="min(1000px, calc(100vw - 32px))" destroy-on-close>
      <div v-if="detailLoading" class="dialog-loading"><el-skeleton :rows="6" animated /></div>
      <template v-else-if="selectedPaper">
        <div class="preview-title">
          <span class="preview-title__eyebrow">SIMULATION PAPER</span>
          <h3>{{ selectedPaper.collectionName }}</h3>
          <p>
            {{ selectedPaper.certificationName }}
            <template v-if="selectedPaper.subject">· {{ selectedPaper.subject.name }}</template>
          </p>
        </div>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="题目数量">{{ selectedPaper.questionCount }} 道</el-descriptions-item>
          <el-descriptions-item label="试卷总分">
            {{ formatScore(selectedPaper.totalReportScore) }} 分
          </el-descriptions-item>
          <el-descriptions-item label="考试时长">{{ selectedPaper.durationMinutes }} 分钟</el-descriptions-item>
          <el-descriptions-item label="作答形式">正式考试模式</el-descriptions-item>
        </el-descriptions>
        <div class="preview-types">
          <el-tag v-for="type in selectedPaper.questionTypes" :key="type" effect="plain">
            {{ questionTypeText(type) }}
          </el-tag>
        </div>
        <el-alert
          v-if="selectedPaper.access.blockCode"
          class="preview-rule"
          type="warning"
          :closable="false"
          show-icon
          :title="accessMessage(selectedPaper.access.blockCode)"
        />
        <ol class="rules">
          <li v-for="rule in selectedPaper.rules" :key="rule">{{ rule }}</li>
        </ol>
        <section class="question-preview">
          <h4>试卷题干</h4>
          <el-empty v-if="!previewQuestions.length" description="本试卷暂无可预览题干" :image-size="72" />
          <article v-for="question in previewQuestions" :key="question.questionOrder" class="question-preview__item">
            <header>
              <strong>第 {{ question.questionOrder }} 题</strong>
              <el-tag size="small" effect="plain">{{ questionTypeText(question.questionType) }}</el-tag>
            </header>
            <p>{{ question.stem }}</p>
          </article>
        </section>
      </template>
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
        <el-button
          v-if="selectedPaper"
          type="primary"
          :disabled="!selectedPaper.access.canStart"
          @click="startExam(selectedPaper)"
        >
          {{
            selectedPaper.access.action === 'RESUME'
              ? '继续考试'
              : selectedPaper.access.canStart
                ? '开始考试'
                : '暂不可用'
          }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="StudentMockExams" lang="ts">
import { ArrowRight, Search, Timer } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  getSimulationDetail,
  getSimulationPreview,
  getSimulationSetup,
  listSimulations,
  startSimulationSession,
  type SimulationAccessVo,
  type SimulationDetailVo,
  type SimulationListItemVo,
  type SimulationPreviewQuestionVo,
  type SimulationSetupVo
} from '@/api/certmuse/assessment/simulations';

type StartableSimulation = Pick<SimulationListItemVo, 'collectionId' | 'revisionId' | 'access'>;

const router = useRouter();
const setup = ref<SimulationSetupVo>();
const papers = ref<SimulationListItemVo[]>([]);
const selectedPaper = ref<SimulationDetailVo>();
const previewQuestions = ref<SimulationPreviewQuestionVo[]>([]);
const setupLoading = ref(true);
const listLoading = ref(false);
const detailLoading = ref(false);
const loadError = ref('');
const previewVisible = ref(false);
const pageNum = ref(1);
const pageSize = 20;
const total = ref(0);
const filters = reactive({ certificationId: '', keyword: '' });

const certifications = computed(() => setup.value?.certifications ?? []);
const certificationOptions = computed(() => {
  const goal = setup.value?.currentGoal;
  if (!goal || certifications.value.some(item => item.id === goal.certificationId)) return certifications.value;
  return [{ id: goal.certificationId, name: goal.certificationName }, ...certifications.value];
});
onMounted(() => void reloadAll());

async function reloadAll() {
  setupLoading.value = true;
  loadError.value = '';
  try {
    const response = await getSimulationSetup();
    if (!response.data) throw new Error('未取得模拟试卷筛选数据。');
    setup.value = response.data;
    filters.certificationId = response.data.currentGoal?.certificationId ?? '';
    await loadPapers();
  } catch (error) {
    setup.value = undefined;
    papers.value = [];
    total.value = 0;
    loadError.value = messageForError(error, '无法加载模拟试卷，请稍后重试。');
  } finally {
    setupLoading.value = false;
  }
}

async function loadPapers() {
  listLoading.value = true;
  try {
    const response = await listSimulations({
      certificationId: filters.certificationId || undefined,
      keyword: filters.keyword.trim() || undefined,
      pageNum: pageNum.value,
      pageSize
    });
    papers.value = response.data?.rows ?? [];
    total.value = response.data?.total ?? 0;
  } catch (error) {
    papers.value = [];
    total.value = 0;
    ElMessage.error(messageForError(error, '无法加载模拟试卷列表，请稍后重试。'));
  } finally {
    listLoading.value = false;
  }
}

function search() {
  pageNum.value = 1;
  void loadPapers();
}

function resetFilters() {
  filters.certificationId = setup.value?.currentGoal?.certificationId ?? '';
  filters.keyword = '';
  search();
}

function changePage(nextPage: number) {
  pageNum.value = nextPage;
  void loadPapers();
}

async function openPreview(collectionId: string) {
  detailLoading.value = true;
  selectedPaper.value = undefined;
  previewQuestions.value = [];
  previewVisible.value = true;
  try {
    const [detailResponse, previewResponse] = await Promise.all([
      getSimulationDetail(collectionId),
      getSimulationPreview(collectionId)
    ]);
    if (!detailResponse.data || !previewResponse.data) throw new Error('未取得模拟试卷内容。');
    selectedPaper.value = detailResponse.data;
    previewQuestions.value = previewResponse.data.questions;
  } catch (error) {
    previewVisible.value = false;
    ElMessage.error(messageForError(error, '无法加载模拟试卷说明，请稍后重试。'));
  } finally {
    detailLoading.value = false;
  }
}

async function startExam(paper: StartableSimulation) {
  if (!paper.access.canStart) {
    ElMessage.warning(accessMessage(paper.access.blockCode));
    return;
  }
  const goal = setup.value?.currentGoal;
  if (!goal) {
    ElMessage.warning('请先设置当前学习目标。');
    return;
  }
  try {
    const response = await startSimulationSession(paper.collectionId, {
      expectedRevisionId: paper.revisionId,
      expectedGoalVersion: goal.version
    });
    if (response.data?.answerPath) {
      previewVisible.value = false;
      await router.push(response.data.answerPath);
    }
  } catch (error) {
    ElMessage.error(messageForError(error, '无法开始模拟考试，请稍后重试。'));
  }
}

function questionTypeText(type: SimulationListItemVo['questionTypes'][number]) {
  return { CHOICE: '选择题', CASE: '案例题', ESSAY: '论文题' }[type];
}

function formatScore(score: number) {
  return Number.isInteger(score) ? String(score) : score.toFixed(2).replace(/0+$/, '').replace(/\.$/, '');
}

function accessMessage(code: SimulationAccessVo['blockCode']) {
  const messages: Record<string, string> = {
    LEARNING_GOAL_NOT_ACTIVE: '请先设置当前学习目标。',
    SIMULATION_GOAL_SCOPE_MISMATCH: '当前学习目标与本试卷的考试资格或考纲版本不一致。',
    SIMULATION_PAPER_UNAVAILABLE: '该模拟试卷的数据正在维护中。',
    SIMULATION_SUBJECT_SCOPE_INVALID: '该试卷的科目范围正在维护中。',
    SIMULATION_SUBJECTIVE_NOT_SUPPORTED: '该试卷包含当前不支持自动判分的题型。',
    SIMULATION_SESSION_IN_PROGRESS: '当前目标已有进行中的模拟考试。'
  };
  return code ? (messages[code] ?? '该试卷当前不可开考。') : '该试卷当前不可开考。';
}

function messageForError(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback;
}
</script>

<style scoped lang="scss">
.mock-exams-page {
  width: 100%;
  max-width: none;
  margin: 0;
}
.page-intro {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin: 12px 4px 20px;
}
.page-intro__back { min-width: 112px; margin-top: 12px; font-size: 15px; }
.page-intro__eyebrow,
.list-heading__eyebrow,
.preview-title__eyebrow {
  display: block;
  color: var(--app-accent-strong);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
}
.page-intro h2 {
  margin: 5px 0;
  font-size: 26px;
}
.page-intro p {
  color: var(--el-text-color-secondary);
}
.page-state {
  min-height: 360px;
  padding: 28px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 16px;
  background: var(--el-bg-color);
}
.filter-card {
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 16px;
  background: linear-gradient(105deg, var(--el-fill-color-lighter) 0%, var(--el-bg-color) 52%);
  box-shadow: 0 10px 26px rgb(32 73 118 / 7%);
}
.filter-card :deep(.el-card__body) {
  padding: 18px 24px;
}
.filter-form {
  display: flex;
  align-items: flex-end;
  gap: 16px;
}
.filter-form :deep(.el-form-item) {
  margin: 0;
}
.filter-form :deep(.el-form-item__label) {
  height: auto;
  margin-bottom: 7px;
  color: var(--el-text-color-regular);
  font-size: 14px;
  font-weight: 600;
  line-height: 20px;
}
.filter-form :deep(.el-select),
.filter-form :deep(.el-input) {
  width: 260px;
}
.filter-form :deep(.el-input__wrapper),
.filter-form :deep(.el-select__wrapper) {
  min-height: 40px;
  border-radius: 10px;
  background: var(--el-bg-color);
  box-shadow: 0 0 0 1px var(--el-border-color-lighter) inset;
}
.filter-form :deep(.el-input__wrapper:hover),
.filter-form :deep(.el-select__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--el-color-primary-light-5) inset;
}
.filter-form__actions {
  display: flex;
  align-items: center;
  align-self: flex-end;
  gap: 10px;
  margin-left: 4px !important;
}
.filter-form__actions :deep(.el-button) {
  min-width: 72px;
  height: 40px;
  margin-left: 0;
  border-radius: 10px;
  font-weight: 600;
}
.list-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 16px;
  margin: 22px 4px 14px;
}
.list-heading h3 {
  margin: 4px 0 0;
  color: var(--el-text-color-primary);
  font-size: 19px;
}
.list-heading > span {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.paper-list {
  display: grid;
  gap: 14px;
}
.paper-list--empty {
  display: block;
}
.paper-card {
  border-radius: 14px;
}
.paper-card :deep(.el-card__body) {
  display: flex;
  justify-content: space-between;
  gap: 18px;
}
.paper-card__main {
  min-width: 0;
  flex: 1;
}
.paper-card__title-row {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 12px;
}
.paper-card h4 {
  margin: 0;
  font-size: 18px;
  line-height: 1.45;
}
.paper-card__title-row p {
  margin: 5px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.paper-card__metrics {
  display: flex;
  gap: 16px;
  margin: 14px 0;
  color: var(--el-text-color-regular);
  font-size: 14px;
}
.paper-card__metrics div {
  padding: 0;
  border: 0;
}
.paper-card__metrics strong {
  color: inherit;
  font-size: inherit;
  font-weight: 400;
}
.paper-card__metrics span {
  display: none;
}
.paper-card__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.paper-card__notice {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-left: 6px;
}
.paper-card__notice .el-icon {
  color: var(--el-color-warning);
}
.paper-card__actions {
  display: flex;
  align-items: center;
  flex: none;
  gap: 8px;
}
.empty-state {
  min-height: 220px;
  padding: 18px 0;
  border: 1px dashed var(--el-border-color);
  border-radius: 14px;
  background: var(--el-bg-color);
}
.paper-pagination {
  justify-content: center;
  margin-top: 22px;
}
.preview-title {
  margin-bottom: 20px;
}
.preview-title h3 {
  margin: 7px 0 5px;
  color: var(--el-text-color-primary);
  font-size: 20px;
}
.preview-title p {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.preview-types {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 18px;
}
.preview-rule {
  margin-top: 18px;
}
.rules {
  display: grid;
  gap: 9px;
  margin: 18px 0 0;
  padding-left: 22px;
  color: var(--el-text-color-regular);
  line-height: 1.6;
}
.question-preview {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.question-preview h4 {
  margin: 0 0 14px;
  color: var(--el-text-color-primary);
  font-size: 17px;
}
.question-preview__item {
  padding: 16px 0;
  border-top: 1px solid var(--el-border-color-lighter);
}
.question-preview__item:first-of-type {
  border-top: 0;
}
.question-preview__item header {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--el-text-color-primary);
}
.question-preview__item p {
  margin: 10px 0 0;
  color: var(--el-text-color-regular);
  line-height: 1.8;
  white-space: pre-wrap;
}
.dialog-loading {
  padding: 12px 0;
}
:deep(.el-dialog__body) {
  max-height: 72vh;
  overflow-y: auto;
}
@media (max-width: 760px) {
  .page-intro {
    align-items: stretch;
    flex-direction: column;
  }
  .filter-form {
    display: grid;
    grid-template-columns: 1fr;
    gap: 14px;
  }
  .filter-form :deep(.el-select),
  .filter-form :deep(.el-input) {
    width: 100%;
  }
  .filter-form__actions {
    justify-content: flex-start;
    margin-left: 0 !important;
  }
  .paper-card :deep(.el-card__body) {
    align-items: stretch;
    flex-direction: column;
    gap: 20px;
  }
  .paper-card__metrics {
    grid-template-columns: 1fr;
  }
  .paper-card__metrics div {
    padding: 0 0 10px;
    border-right: 0;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }
  .paper-card__metrics div:last-child {
    padding-bottom: 0;
    border-bottom: 0;
  }
  .paper-card__actions {
    width: 100%;
  }
  .paper-card__actions .el-button {
    flex: 1;
  }
}
</style>
