<template>
  <div class="p-2 app-container past-papers-page">
    <section class="page-intro">
      <div>
        <span>QUESTION BANK</span>
        <h2>历年真题</h2>
        <p>选择资格后可直接浏览已发布真题题面；答案解析、练习和考试需要登录。</p>
      </div>
      <el-button class="page-intro__back" size="large" @click="router.push('/learning/question-bank')">
        返回题库
      </el-button>
    </section>
    <el-card shadow="never" class="filters">
      <el-form :inline="true" @submit.prevent="search">
        <el-form-item label="考试资格">
          <el-select v-model="filters.certificationId" clearable placeholder="请选择资格" @change="changeCertification">
            <el-option v-for="item in certificationOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="科目">
          <el-select v-model="filters.subjectId" clearable placeholder="全部科目">
            <el-option v-for="item in subjects" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="试卷名称">
          <el-input v-model="filters.keyword" clearable placeholder="搜索历年真题" @keyup.enter="search" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    <el-alert
      v-if="!filters.certificationId"
      class="qualification-required"
      type="info"
      :closable="false"
      show-icon
      title="请先选择要浏览的考试资格"
    />
    <section v-else v-loading="loading" class="paper-list">
      <el-card v-for="paper in papers" :key="paper.collectionId" shadow="never" class="paper-card">
        <div>
          <h3>{{ paper.collectionName }}</h3>
          <p>{{ paper.certificationName }} · {{ paper.subjects.map(item => item.name).join('、') || '科目待维护' }}</p>
          <div class="metrics">
            <span>{{ paper.questionCount }} 题</span>
            <span>{{ paper.totalReportScore }} 分</span>
            <span>{{ paper.durationMinutes ?? '--' }} 分钟</span>
            <span>{{ paper.examYear ?? '--' }} 年 {{ paper.examMonth ?? '' }}月</span>
          </div>
          <el-tag v-for="type in paper.questionTypes" :key="type" size="small">{{ typeText(type) }}</el-tag>
        </div>
        <div class="actions">
          <el-button @click="openPreview(paper)">查看题集</el-button>
          <template v-if="isAuthenticated">
            <el-button :disabled="!paper.practiceAccess.canStart" @click="start(paper, 'practice')">
              {{ paper.practiceAccess.action === 'RESUME' ? '继续练习' : '练习模式' }}
            </el-button>
            <el-button type="primary" :disabled="!paper.examAccess.canStart" @click="start(paper, 'exam')">
              {{ paper.examAccess.action === 'RESUME' ? '继续考试' : '考试模式' }}
            </el-button>
          </template>
          <el-button v-else type="primary" @click="goToLogin">登录后练习或考试</el-button>
        </div>
      </el-card>
      <el-empty v-if="!loading && !papers.length" description="没有符合条件的历年真题" />
    </section>
    <el-pagination
      v-if="total > pageSize"
      class="pagination"
      background
      layout="prev, pager, next"
      :current-page="pageNum"
      :page-size="pageSize"
      :total="total"
      @current-change="
        page => {
          pageNum = page;
          load();
        }
      "
    />
    <el-dialog
      v-model="previewVisible"
      title="查看题集"
      width="min(1000px, calc(100vw - 32px))"
      destroy-on-close
      @closed="clearPreview"
    >
      <el-alert
        v-if="isAuthenticated"
        type="warning"
        :closable="false"
        show-icon
        title="查看题目答案后，短期内做此题不会记入学习画像。"
      />
      <el-alert
        v-else
        type="info"
        :closable="false"
        show-icon
        title="游客可阅读题面；登录后可查看答案解析、开始练习或参加考试。"
      />
      <div v-loading="previewLoading" class="preview">
        <article v-for="question in preview?.questions ?? []" :key="question.questionOrder" class="question">
          <h4>
            第 {{ question.questionOrder }} 题
            <el-tag size="small">{{ typeText(question.questionType) }}</el-tag>
          </h4>
          <p><question-text :content="question.stem" /></p>
          <ol v-if="question.options.length" type="A">
            <li v-for="option in question.options" :key="option.label"><question-text :content="option.content" /></li>
          </ol>
          <img v-for="image in question.images" :key="image.url" :src="image.url" :alt="image.alt ?? ''" />
          <div v-if="reveals[question.questionOrder]" class="reveal">
            <strong>正确答案：{{ reveals[question.questionOrder].correctOptionLabels.join('、') }}</strong>
            <p v-if="reveals[question.questionOrder].analysis">{{ reveals[question.questionOrder].analysis }}</p>
            <small>
              本题在
              {{ formatTime(reveals[question.questionOrder].profileEvidenceBlockedUntil) }} 前作答不计入学习画像。
            </small>
          </div>
          <el-button
            v-else-if="isAuthenticated"
            type="primary"
            plain
            :loading="revealing === question.questionOrder"
            @click="reveal(question.questionOrder)"
          >
            查看答案和解析
          </el-button>
          <el-button v-else type="primary" plain @click="goToLogin">登录后查看答案和解析</el-button>
        </article>
      </div>
    </el-dialog>
  </div>
</template>

<script setup name="StudentPastPapers" lang="ts">
import { ElMessage } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  getPastPaperPreview,
  getPastPaperSetup,
  listPastPapers,
  revealPastPaperAnswer,
  startPastPaper,
  type PastPaperListVo,
  type PastPaperPreviewVo,
  type PastPaperQuestionType,
  type PastPaperRevealVo,
  type PastPaperSetupVo
} from '@/api/certmuse/assessment/past-papers';
import QuestionText from '@/components/exam/QuestionText.vue';
import { getToken } from '@/utils/auth';
import { getPublicBrowseCertificationId, setPublicBrowseCertificationId } from '@/utils/public-browse-certification';
const router = useRouter();
const route = useRoute();
const isAuthenticated = Boolean(getToken());
const setup = ref<PastPaperSetupVo>();
const papers = ref<PastPaperListVo[]>([]);
const loading = ref(false);
const pageNum = ref(1);
const pageSize = 20;
const total = ref(0);
const filters = reactive({ certificationId: '', subjectId: '', keyword: '' });
const previewVisible = ref(false);
const previewLoading = ref(false);
const selected = ref<PastPaperListVo>();
const preview = ref<PastPaperPreviewVo>();
const revealing = ref<number>();
const reveals = reactive<Record<number, PastPaperRevealVo>>({});
const certifications = computed(() => setup.value?.certifications ?? []);
const certificationOptions = computed(() => {
  const goal = setup.value?.goal;
  if (!goal || certifications.value.some(item => item.id === goal.certificationId)) return certifications.value;
  return [{ id: goal.certificationId, name: goal.certificationName, subjects: [] }, ...certifications.value];
});
const subjects = computed(
  () => setup.value?.certifications.find(item => item.id === filters.certificationId)?.subjects ?? []
);
const typeText = (type: PastPaperQuestionType) => ({ CHOICE: '选择题', CASE: '案例题', ESSAY: '论文题' })[type];
const formatTime = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false });
async function load() {
  if (!filters.certificationId) {
    papers.value = [];
    total.value = 0;
    return;
  }
  loading.value = true;
  try {
    const response = await listPastPapers({
      ...filters,
      keyword: filters.keyword.trim() || undefined,
      pageNum: pageNum.value,
      pageSize
    });
    papers.value = response.data?.rows ?? [];
    total.value = response.data?.total ?? 0;
  } finally {
    loading.value = false;
  }
}
async function initialize() {
  const response = await getPastPaperSetup();
  setup.value = response.data;
  const stored = getPublicBrowseCertificationId();
  if (response.data?.certifications.some(item => item.id === stored)) {
    filters.certificationId = stored;
    await load();
  }
}
function changeCertification() {
  setPublicBrowseCertificationId(filters.certificationId);
  filters.subjectId = '';
  search();
}
function search() {
  pageNum.value = 1;
  void load();
}
function reset() {
  filters.certificationId = '';
  filters.subjectId = '';
  filters.keyword = '';
  setPublicBrowseCertificationId('');
  search();
}
async function openPreview(paper: PastPaperListVo) {
  selected.value = paper;
  previewVisible.value = true;
  previewLoading.value = true;
  try {
    preview.value = (await getPastPaperPreview(paper.collectionId)).data;
  } finally {
    previewLoading.value = false;
  }
}
function clearPreview() {
  preview.value = undefined;
  selected.value = undefined;
  revealing.value = undefined;
  Object.keys(reveals).forEach(key => delete reveals[Number(key)]);
}
async function reveal(questionOrder: number) {
  if (!selected.value || reveals[questionOrder]) return;
  revealing.value = questionOrder;
  try {
    const response = await revealPastPaperAnswer(selected.value.collectionId, questionOrder);
    if (response.data) reveals[questionOrder] = response.data;
  } finally {
    revealing.value = undefined;
  }
}
async function start(paper: PastPaperListVo, mode: 'practice' | 'exam') {
  const goal = setup.value?.goal;
  if (!goal) return ElMessage.warning('请先设置当前学习目标。');
  const response = await startPastPaper(paper.collectionId, mode, {
    expectedRevisionId: paper.revisionId,
    expectedGoalVersion: goal.rowVersion
  });
  if (!response.data?.answerPath) return;
  if (mode === 'practice') {
    const target = router.resolve(response.data.answerPath);
    await router.push({ path: target.path, query: { ...target.query, paperName: paper.collectionName } });
    return;
  }
  await router.push(response.data.answerPath);
}
function goToLogin() {
  void router.push({ path: '/login', query: { redirect: route.fullPath, reason: 'unlock' } });
}
onMounted(() => void initialize());
</script>

<style scoped lang="scss">
.past-papers-page {
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
.page-intro__back {
  min-width: 112px;
  margin-top: 12px;
  font-size: 15px;
}
.page-intro span {
  color: var(--app-accent-strong);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
}
.page-intro h2 {
  margin: 5px 0;
  font-size: 26px;
}
.page-intro p,
.paper-card p {
  color: var(--el-text-color-secondary);
}
.filters {
  border-radius: 14px;
}
.qualification-required {
  margin-top: 22px;
}
.paper-list {
  display: grid;
  gap: 14px;
  margin-top: 22px;
}
.paper-card :deep(.el-card__body) {
  display: flex;
  justify-content: space-between;
  gap: 18px;
}
.paper-card h3 {
  margin: 0;
}
.metrics {
  display: flex;
  gap: 16px;
  margin: 14px 0;
  color: var(--el-text-color-regular);
  font-size: 14px;
}
.actions {
  display: flex;
  align-items: center;
  flex: none;
  gap: 8px;
}
.pagination {
  justify-content: center;
  margin: 22px;
}
.preview {
  margin-top: 18px;
}
.question {
  padding: 18px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.question h4 {
  margin: 0;
}
.question p,
.question li {
  line-height: 1.8;
  white-space: pre-wrap;
}
.question img {
  display: block;
  max-width: 100%;
  margin: 12px 0;
}
.reveal {
  margin: 16px 0 0;
  padding: 14px;
  border-radius: 8px;
  background: var(--el-color-success-light-9);
}
.reveal p {
  margin: 8px 0;
}
.reveal small {
  color: var(--el-text-color-secondary);
}
@media (max-width: 760px) {
  .page-intro {
    align-items: stretch;
    flex-direction: column;
  }
  .page-intro__back {
    margin-top: 0;
  }
  .paper-card :deep(.el-card__body) {
    flex-direction: column;
  }
  .actions {
    flex-wrap: wrap;
  }
  .metrics {
    flex-wrap: wrap;
  }
}
</style>
