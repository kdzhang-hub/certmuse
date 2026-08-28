<template>
  <div class="p-2 review-page">
    <el-tabs v-model="activeTab" class="review-tabs">
      <el-tab-pane label="题目审核" name="question">
        <el-card shadow="hover" class="search-panel">
          <el-form :model="questionFilters" :inline="true" class="query-form">
            <el-form-item label="关键词"><el-input v-model="questionFilters.keyword" clearable placeholder="题目编号或题干" @keyup.enter="queryQuestions" /></el-form-item>
            <el-form-item label="资格名称"><el-select v-model="questionFilters.syllabusVersionId" clearable placeholder="全部资格"><el-option v-for="item in qualificationOptions" :key="item.syllabusVersionId" :label="item.label" :value="item.syllabusVersionId" /></el-select></el-form-item>
            <el-form-item label="题型"><el-select v-model="questionFilters.questionType" clearable placeholder="全部题型"><el-option label="选择题" value="CHOICE" /><el-option label="案例题" value="CASE" /><el-option label="论文题" value="ESSAY" /></el-select></el-form-item>
            <el-form-item label="难度"><el-select v-model="questionFilters.difficulty" clearable placeholder="全部难度"><el-option label="简单" value="easy" /><el-option label="中等" value="medium" /><el-option label="困难" value="hard" /></el-select></el-form-item>
            <el-form-item><el-button type="primary" icon="Search" :disabled="processing" @click="queryQuestions">搜索</el-button><el-button icon="Refresh" :disabled="processing" @click="resetQuestionFilters">重置</el-button></el-form-item>
          </el-form>
        </el-card>
        <el-card shadow="hover" class="table-panel">
          <template #header><div class="toolbar-shell"><div class="table-heading"><h3>审核列表</h3></div><div class="toolbar-actions"><el-button v-hasPermi="['certmuse:question:review']" type="primary" plain :disabled="processing || !questionSelection.length" @click="openMixedReview">审核已选（{{ questionSelection.length }}）</el-button><el-button v-hasPermi="['certmuse:question:review']" type="primary" :loading="loadingAllReview" :disabled="processing" @click="openAllMixedReview">审核全部审核中题目</el-button><right-toolbar :search="false" @query-table="loadQuestions" /></div></div></template>
          <el-table ref="questionTable" v-loading="questionLoading" :data="questions" border stripe row-key="revisionId" @selection-change="questionSelection = $event">
            <el-table-column type="selection" width="48" reserve-selection /><el-table-column prop="questionCode" label="题目编号" min-width="155" /><el-table-column prop="stemSummary" label="题干摘要" min-width="280" show-overflow-tooltip /><el-table-column label="资格名称" min-width="145" show-overflow-tooltip><template #default="scope">{{ questionCertificationName(scope.row as QuestionListVO) }}</template></el-table-column><el-table-column prop="examSubjectName" label="考试科目" width="120" /><el-table-column label="题型" width="90" align="center"><template #default="scope">{{ typeLabel(scope.row.questionType) }}</template></el-table-column><el-table-column label="难度" width="75" align="center"><template #default="scope">{{ difficultyLabel(scope.row.difficulty) }}</template></el-table-column><el-table-column label="更新时间" width="170"><template #default="scope">{{ formatDateTime(scope.row.updatedTime) }}</template></el-table-column>
            <el-table-column label="操作" width="170" fixed="right" align="center"><template #default="scope"><el-button link type="primary" icon="View" :disabled="processing" @click="previewQuestion(scope.row as QuestionListVO)">查看</el-button><el-button v-hasPermi="['certmuse:question:review']" link type="success" :disabled="processing" @click="confirmApprove('question', scope.row as QuestionListVO)">通过</el-button><el-button v-hasPermi="['certmuse:question:review']" link type="danger" :disabled="processing" @click="openReject('question', scope.row as QuestionListVO)">驳回</el-button></template></el-table-column>
          </el-table>
          <pagination v-show="questionTotal > 0" v-model:page="questionPage.pageNum" v-model:limit="questionPage.pageSize" :total="questionTotal" @pagination="loadQuestions" />
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="题集审核" name="collection">
        <el-card shadow="hover" class="search-panel">
          <el-form :model="collectionFilters" :inline="true" class="query-form">
            <el-form-item label="关键词"><el-input v-model="collectionFilters.keyword" clearable placeholder="题集名称或编码" @keyup.enter="queryCollections" /></el-form-item>
            <el-form-item label="题集类型"><el-select v-model="collectionFilters.collectionType" clearable placeholder="全部类型"><el-option label="首次诊断" value="FIRST_DIAGNOSTIC" /><el-option label="专项题集" value="PRACTICE" /><el-option label="模拟试卷" value="SIMULATION" /><el-option label="历年真题" value="PAST_PAPER" /></el-select></el-form-item>
            <el-form-item label="资格名称"><el-select v-model="collectionFilters.syllabusVersionId" clearable placeholder="全部资格"><el-option v-for="item in qualificationOptions" :key="item.syllabusVersionId" :label="item.label" :value="item.syllabusVersionId" /></el-select></el-form-item>
            <el-form-item><el-button type="primary" icon="Search" :disabled="processing" @click="queryCollections">搜索</el-button><el-button icon="Refresh" :disabled="processing" @click="resetCollectionFilters">重置</el-button></el-form-item>
          </el-form>
        </el-card>
        <el-card shadow="hover" class="table-panel">
          <template #header><div class="toolbar-shell"><div class="table-heading"><h3>待审核题集</h3></div><div class="toolbar-actions"><el-button v-hasPermi="['certmuse:question:collection:review']" type="success" plain :loading="processing" :disabled="processing || !collectionSelection.length" @click="confirmApprove('collection')">批量通过</el-button><el-button v-hasPermi="['certmuse:question:collection:review']" type="danger" plain :disabled="processing || !collectionSelection.length" @click="openReject('collection')">批量驳回</el-button><right-toolbar :search="false" @query-table="loadCollections" /></div></div></template>
          <el-table ref="collectionTable" v-loading="collectionLoading" :data="collections" border stripe row-key="revisionId" @selection-change="collectionSelection = $event">
            <el-table-column type="selection" width="48" reserve-selection /><el-table-column prop="collectionCode" label="题集编码" min-width="160" /><el-table-column prop="collectionName" label="题集名称" min-width="220" show-overflow-tooltip /><el-table-column label="题集类型" width="110"><template #default="scope">{{ collectionTypeLabel(scope.row.collectionType) }}</template></el-table-column><el-table-column prop="certificationName" label="资格名称" min-width="150" /><el-table-column prop="questionCount" label="题目数" width="80" align="center" /><el-table-column label="总分" width="80" align="center"><template #default="scope">{{ scope.row.totalReportScore }}</template></el-table-column><el-table-column label="更新时间" width="170"><template #default="scope">{{ formatDateTime(scope.row.updatedTime) }}</template></el-table-column>
            <el-table-column label="操作" width="170" fixed="right" align="center"><template #default="scope"><el-button link type="primary" icon="View" :disabled="processing" @click="showCollection(scope.row as CollectionListVO)">查看</el-button><el-button v-hasPermi="['certmuse:question:collection:review']" link type="success" :disabled="processing" @click="confirmApprove('collection', scope.row as CollectionListVO)">通过</el-button><el-button v-hasPermi="['certmuse:question:collection:review']" link type="danger" :disabled="processing" @click="openReject('collection', scope.row as CollectionListVO)">驳回</el-button></template></el-table-column>
          </el-table>
          <pagination v-show="collectionTotal > 0" v-model:page="collectionPage.pageNum" v-model:limit="collectionPage.pageSize" :total="collectionTotal" @pagination="loadCollections" />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="collectionDetailVisible" title="题集审核详情" size="min(1480px, calc(100vw - 32px))">
      <template #header="{ titleId, titleClass }">
        <div class="drawer-header">
          <span :id="titleId" :class="titleClass">题集审核详情</span>
          <div class="review-actions">
            <el-button v-hasPermi="['certmuse:question:collection:review']" type="success" :loading="processing" :disabled="!collectionDetail || collectionDetail.status !== 'pending_review'" @click="approveCollectionDetail">通过</el-button>
            <el-button v-hasPermi="['certmuse:question:collection:review']" type="danger" plain :disabled="processing || !collectionDetail || collectionDetail.status !== 'pending_review'" @click="rejectCollectionDetail">驳回</el-button>
          </div>
        </div>
      </template>
      <div v-loading="collectionDetailLoading"><template v-if="collectionDetail"><el-descriptions :column="2" border><el-descriptions-item label="题集名称" :span="2">{{ collectionDetail.collectionName }}</el-descriptions-item><el-descriptions-item label="题集编码">{{ collectionDetail.collectionCode }}</el-descriptions-item><el-descriptions-item label="修订版本">V{{ collectionDetail.revisionNo }}</el-descriptions-item><el-descriptions-item label="考试资格">{{ collectionDetail.certificationName }}</el-descriptions-item><el-descriptions-item label="考试时长">{{ collectionDetail.durationMinutes ? `${collectionDetail.durationMinutes} 分钟` : '无' }}</el-descriptions-item><el-descriptions-item label="题目数量">{{ collectionDetail.questionCount }}</el-descriptions-item><el-descriptions-item label="总分">{{ collectionDetail.totalReportScore }}</el-descriptions-item></el-descriptions><h3 class="detail-title">题目明细</h3><el-alert title="题集仅在题目全部已发布后才能通过审核。驳回任一题目将同步驳回该题集。" type="warning" :closable="false" show-icon /><el-table :data="collectionDetail.items" border><el-table-column prop="itemOrder" label="序号" width="65" /><el-table-column prop="questionCode" label="题目编号" width="150" /><el-table-column prop="stem" label="题干" min-width="260" show-overflow-tooltip /><el-table-column prop="examSubjectName" label="科目" width="115" /><el-table-column prop="reportScore" label="分值" width="70" /><el-table-column label="状态" width="90"><template #default="scope"><el-tag :type="questionStatusTag(scope.row.questionStatus)">{{ questionStatusLabel(scope.row.questionStatus) }}</el-tag></template></el-table-column><el-table-column label="操作" width="180"><template #default="scope"><el-button link type="primary" :disabled="processing" @click="previewCollectionQuestion(scope.row as CollectionRevisionDetailVO['items'][number])">查看</el-button><el-button v-hasPermi="['certmuse:question:review']" link type="success" :disabled="processing || scope.row.questionStatus !== 'pending_review'" @click="approveCollectionQuestion(scope.row as CollectionRevisionDetailVO['items'][number])">通过</el-button><el-button v-hasPermi="['certmuse:question:review']" link type="danger" :disabled="processing || scope.row.questionStatus !== 'pending_review'" @click="rejectCollectionQuestion(scope.row as CollectionRevisionDetailVO['items'][number])">驳回</el-button></template></el-table-column></el-table></template></div>
    </el-drawer>
    <QuestionPreviewDialog v-model="previewVisible" :question="previewItem" :loading="previewLoading" :reviewable="previewTarget?.status === 'pending_review'" :processing="processing" @approve="approvePreviewQuestion" @reject="rejectPreviewQuestion" />
    <el-dialog v-model="mixedReviewVisible" title="批量审核题目" width="min(920px, calc(100vw - 32px))" :close-on-click-modal="false">
      <el-alert title="默认全部通过；只需把少数不合格题目标记为驳回并填写各自原因。提交前可逐项复核，避免误批。" type="info" :closable="false" show-icon />
      <el-table :data="mixedReviewRows" border max-height="520" class="mixed-review-table">
        <el-table-column prop="questionCode" label="题目编号" width="180" />
        <el-table-column prop="stemSummary" label="题干摘要" min-width="260" show-overflow-tooltip />
        <el-table-column label="结论" width="130"><template #default="scope"><el-select v-model="scope.row.decision"><el-option label="通过" value="approve" /><el-option label="驳回" value="reject" /></el-select></template></el-table-column>
        <el-table-column label="驳回原因" min-width="230"><template #default="scope"><el-input v-model="scope.row.opinion" :disabled="scope.row.decision === 'approve'" maxlength="500" placeholder="标记驳回后必填" /></template></el-table-column>
      </el-table>
      <template #footer><span class="mixed-summary">通过 {{ mixedApproveCount }} 道，驳回 {{ mixedRejectCount }} 道</span><el-button @click="mixedReviewVisible = false">取消</el-button><el-button type="primary" :loading="processing" @click="submitMixedReview">确认提交</el-button></template>
    </el-dialog>
    <el-dialog v-model="rejectVisible" title="驳回审核" width="520px"><el-alert :title="rejectKind === 'question' ? '题目将标记为已驳回；审核意见会应用于本次选择的全部题目。' : '题集将返回草稿；审核意见会应用于本次选择的全部题集。'" type="warning" :closable="false" show-icon /><el-form class="reject-form" label-position="top"><el-form-item label="审核意见" required :error="rejectError"><el-input v-model="reviewOpinion" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="请输入驳回原因" @input="rejectError = ''" /></el-form-item></el-form><template #footer><el-button @click="rejectVisible = false">取消</el-button><el-button type="danger" :loading="processing" @click="submitReject">确认驳回</el-button></template></el-dialog>
  </div>
</template>

<script setup name="CertmuseContentReview" lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import type { SyllabusListVO } from '@/api/certmuse/catalog/knowledge/types';
import type { QuestionListVO, QuestionPreviewVO, QuestionStatus, QuestionType } from '@/api/certmuse/question/types';
import type { CollectionErrorData, CollectionListVO, CollectionRevisionDetailVO, CollectionType } from '@/api/certmuse/question/collection/types';
import { listSyllabusVersions } from '@/api/certmuse/catalog/knowledge';
import { approveQuestionRevision, getQuestionPreview, listQuestions, rejectQuestionRevision } from '@/api/certmuse/question';
import { approveCollectionRevision, getCollectionRevision, listCollections, rejectCollectionRevision } from '@/api/certmuse/question/collection';
import QuestionPreviewDialog from '@/components/certmuse/QuestionPreviewDialog.vue';
import { runSequentialBatch } from './batch';

type ReviewKind = 'question' | 'collection';
const activeTab = ref<ReviewKind>('question');
const syllabuses = ref<SyllabusListVO[]>([]);
const qualificationOptions = computed(() => {
  const options = new Map<string, { syllabusVersionId: string; label: string }>();
  for (const syllabus of syllabuses.value) {
    if (!options.has(syllabus.certificationId)) {
      options.set(syllabus.certificationId, {
        syllabusVersionId: syllabus.id,
        label: syllabus.certificationName
      });
    }
  }
  return [...options.values()];
});
const questions = ref<QuestionListVO[]>([]); const questionTotal = ref(0); const questionLoading = ref(false);
const collections = ref<CollectionListVO[]>([]); const collectionTotal = ref(0); const collectionLoading = ref(false);
const questionSelection = ref<QuestionListVO[]>([]); const collectionSelection = ref<CollectionListVO[]>([]);
interface SelectionTable { clearSelection: () => void; toggleRowSelection: (row: unknown, selected?: boolean) => void; }
const questionTable = ref<SelectionTable>(); const collectionTable = ref<SelectionTable>();
const questionPage = reactive({ pageNum: 1, pageSize: 10 }); const collectionPage = reactive({ pageNum: 1, pageSize: 10 });
const questionFilters = reactive<{ keyword: string; syllabusVersionId: string; questionType: QuestionType | ''; difficulty: string }>({ keyword: '', syllabusVersionId: '', questionType: '', difficulty: '' });
const collectionFilters = reactive<{ keyword: string; collectionType: CollectionType | ''; syllabusVersionId: string }>({ keyword: '', collectionType: '', syllabusVersionId: '' });

function questionCertificationName(question: QuestionListVO) {
  return (
    syllabuses.value.find(syllabus => syllabus.id === question.syllabusVersionId)?.certificationName ||
    question.syllabusVersionName?.split('·')[0]?.trim() ||
    '—'
  );
}
const previewVisible = ref(false); const previewLoading = ref(false); const previewItem = ref<QuestionPreviewVO>(); const previewTarget = ref<QuestionListVO>();
const previewQueue = ref<QuestionListVO[]>([]); const previewQueueIndex = ref(-1); const previewFromCollectionDetail = ref(false);
const collectionDetailVisible = ref(false); const collectionDetailLoading = ref(false); const collectionDetail = ref<CollectionRevisionDetailVO>();
const rejectVisible = ref(false); const rejectKind = ref<ReviewKind>('question'); const rejectTargets = ref<Array<QuestionListVO | CollectionListVO>>([]); const reviewOpinion = ref(''); const rejectError = ref(''); const processing = ref(false);
type ReviewSuccessAction = 'none' | 'next-preview-question' | 'close-collection-detail' | 'close-rejected-collection-detail';
const reviewSuccessAction = ref<ReviewSuccessAction>('none');
const MAX_REVIEW_OPINION_LENGTH = 500;
type MixedDecision = 'approve' | 'reject';
type MixedReviewRow = QuestionListVO & { decision: MixedDecision; opinion: string };
const mixedReviewVisible = ref(false); const mixedReviewRows = ref<MixedReviewRow[]>([]);
const loadingAllReview = ref(false);
const mixedApproveCount = computed(() => mixedReviewRows.value.filter(row => row.decision === 'approve').length);
const mixedRejectCount = computed(() => mixedReviewRows.value.length - mixedApproveCount.value);

watch(activeTab, tab => { if (tab === 'collection' && !collections.value.length) void loadCollections(); });
function queryQuestions() { questionPage.pageNum = 1; void loadQuestions(); }
function queryCollections() { collectionPage.pageNum = 1; void loadCollections(); }
function resetQuestionFilters() { Object.assign(questionFilters, { keyword: '', syllabusVersionId: '', questionType: '', difficulty: '' }); queryQuestions(); }
function resetCollectionFilters() { Object.assign(collectionFilters, { keyword: '', collectionType: '', syllabusVersionId: '' }); queryCollections(); }
async function loadQuestions(preserveIds: string[] = []) { questionLoading.value = true; try { const page = (await listQuestions({ keyword: questionFilters.keyword.trim() || undefined, syllabusVersionId: questionFilters.syllabusVersionId || undefined, questionType: questionFilters.questionType || undefined, difficulty: questionFilters.difficulty || undefined, status: 'pending_review', ...questionPage })).data; questions.value = page.rows; questionTotal.value = page.total; await nextTick(); questionTable.value?.clearSelection(); page.rows.filter(row => preserveIds.includes(row.revisionId)).forEach(row => questionTable.value?.toggleRowSelection(row, true)); } finally { questionLoading.value = false; } }
async function loadCollections(preserveIds: string[] = []) { collectionLoading.value = true; try { const page = (await listCollections({ keyword: collectionFilters.keyword.trim() || undefined, collectionType: collectionFilters.collectionType || undefined, syllabusVersionId: collectionFilters.syllabusVersionId || undefined, status: 'pending_review', ...collectionPage })).data; collections.value = page.rows; collectionTotal.value = page.total; await nextTick(); collectionTable.value?.clearSelection(); page.rows.filter(row => preserveIds.includes(row.revisionId)).forEach(row => collectionTable.value?.toggleRowSelection(row, true)); } finally { collectionLoading.value = false; } }
async function previewQuestion(row: QuestionListVO, queue: QuestionListVO[] = questions.value, fromCollectionDetail = false) { previewQueue.value = queue.filter(item => item.status === 'pending_review'); previewQueueIndex.value = previewQueue.value.findIndex(item => item.revisionId === row.revisionId); previewFromCollectionDetail.value = fromCollectionDetail; previewTarget.value = row; previewVisible.value = true; previewLoading.value = true; previewItem.value = undefined; try { previewItem.value = (await getQuestionPreview(row.questionId, row.revisionId)).data; } finally { previewLoading.value = false; } }
async function showCollection(row: CollectionListVO) { collectionDetailVisible.value = true; collectionDetailLoading.value = true; collectionDetail.value = undefined; try { collectionDetail.value = (await getCollectionRevision(row.revisionId)).data; } finally { collectionDetailLoading.value = false; } }
function collectionQuestionTarget(row: CollectionRevisionDetailVO['items'][number]) { return { questionId: row.questionId, revisionId: row.questionRevisionId, questionCode: row.questionCode, status: row.questionStatus as QuestionStatus } as QuestionListVO; }
function previewCollectionQuestion(row: CollectionRevisionDetailVO['items'][number]) { void previewQuestion(collectionQuestionTarget(row), [], true); }
function approveCollectionQuestion(row: CollectionRevisionDetailVO['items'][number]) { void confirmApprove('question', collectionQuestionTarget(row)); }
function rejectCollectionQuestion(row: CollectionRevisionDetailVO['items'][number]) { openReject('question', collectionQuestionTarget(row), 'close-rejected-collection-detail'); }
function approvePreviewQuestion() { if (previewTarget.value) void confirmApprove('question', previewTarget.value, 'next-preview-question'); }
function rejectPreviewQuestion() { if (previewTarget.value) openReject('question', previewTarget.value, previewFromCollectionDetail.value ? 'close-rejected-collection-detail' : 'next-preview-question'); }
function collectionDetailTarget() { return collectionDetail.value ? ({ revisionId: collectionDetail.value.revisionId } as CollectionListVO) : undefined; }
function approveCollectionDetail() { const target = collectionDetailTarget(); if (target) void confirmApprove('collection', target, 'close-collection-detail'); }
function rejectCollectionDetail() { const target = collectionDetailTarget(); if (target) openReject('collection', target, 'close-collection-detail'); }
function selected(kind: ReviewKind, row?: QuestionListVO | CollectionListVO) { return row ? [row] : kind === 'question' ? questionSelection.value : collectionSelection.value; }
function openMixedReview() { if (processing.value || !questionSelection.value.length) return; mixedReviewRows.value = questionSelection.value.map(row => ({ ...row, decision: 'approve', opinion: '' })); mixedReviewVisible.value = true; }
async function openAllMixedReview() { if (processing.value || loadingAllReview.value) return; loadingAllReview.value = true; try { const targets = await loadAllPendingQuestions(); if (!targets.length) { ElMessage.info('当前范围内没有审核中题目'); return; } mixedReviewRows.value = targets.map(row => ({ ...row, decision: 'approve', opinion: '' })); mixedReviewVisible.value = true; } finally { loadingAllReview.value = false; } }
async function loadAllPendingQuestions() { const pageSize = 100; const query = { keyword: questionFilters.keyword.trim() || undefined, syllabusVersionId: questionFilters.syllabusVersionId || undefined, questionType: questionFilters.questionType || undefined, difficulty: questionFilters.difficulty || undefined, status: 'pending_review' as const }; const first = (await listQuestions({ ...query, pageNum: 1, pageSize })).data; const targets = [...first.rows]; for (let pageNum = 2; pageNum <= Math.ceil(first.total / pageSize); pageNum += 1) targets.push(...(await listQuestions({ ...query, pageNum, pageSize })).data.rows); return [...new Map(targets.map(item => [item.revisionId, item])).values()]; }
async function submitMixedReview() { if (processing.value) return; const missing = mixedReviewRows.value.find(row => row.decision === 'reject' && !row.opinion.trim()); if (missing) { ElMessage.warning(`请填写题目 ${missing.questionCode} 的驳回原因`); return; } const tooLong = mixedReviewRows.value.find(row => row.decision === 'reject' && row.opinion.trim().length > MAX_REVIEW_OPINION_LENGTH); if (tooLong) { ElMessage.warning(`题目 ${tooLong.questionCode} 的驳回原因不能超过 ${MAX_REVIEW_OPINION_LENGTH} 个字符`); return; } processing.value = true; try { const result = await runSequentialBatch(mixedReviewRows.value, row => row.decision === 'approve' ? approveQuestionRevision(row.revisionId) : rejectQuestionRevision(row.revisionId, row.opinion.trim())); const failedRows = result.failed.map(entry => entry.item); mixedReviewRows.value = failedRows; mixedReviewVisible.value = failedRows.length > 0; await finishBatch('question', failedRows.map(row => row.revisionId), result.succeeded.length, failedRows.length); } finally { processing.value = false; } }
async function confirmApprove(kind: ReviewKind, row?: QuestionListVO | CollectionListVO, successAction: ReviewSuccessAction = 'none') { if (processing.value) return; const targets = selected(kind, row); if (!targets.length) return; await ElMessageBox.confirm(`确认通过选中的 ${targets.length} 条内容并立即发布吗？`, '审核通过', { type: 'warning' }); if (processing.value) return; processing.value = true; try { const result = await runSequentialBatch(targets, item => kind === 'question' ? approveQuestionRevision(item.revisionId) : approveCollectionRevision(item.revisionId)); await finishBatch(kind, result.failed.map(entry => entry.item.revisionId), result.succeeded.length, result.failed.length, result.failed); if (!result.failed.length) finishReviewSuccess(successAction); } finally { processing.value = false; } }
function openReject(kind: ReviewKind, row?: QuestionListVO | CollectionListVO, successAction: ReviewSuccessAction = 'none') { if (processing.value) return; const targets = selected(kind, row); if (!targets.length) return; reviewSuccessAction.value = successAction; rejectKind.value = kind; rejectTargets.value = targets; reviewOpinion.value = ''; rejectError.value = ''; rejectVisible.value = true; }
async function submitReject() { if (processing.value) return; const opinion = reviewOpinion.value.trim(); if (!opinion) { rejectError.value = '请输入驳回原因'; return; } if (opinion.length > MAX_REVIEW_OPINION_LENGTH) { rejectError.value = `审核意见不能超过 ${MAX_REVIEW_OPINION_LENGTH} 个字符`; return; } processing.value = true; try { const result = await runSequentialBatch(rejectTargets.value, item => rejectKind.value === 'question' ? rejectQuestionRevision(item.revisionId, opinion) : rejectCollectionRevision(item.revisionId, opinion)); const failedTargets = result.failed.map(entry => entry.item); rejectTargets.value = failedTargets; rejectVisible.value = failedTargets.length > 0; await finishBatch(rejectKind.value, failedTargets.map(item => item.revisionId), result.succeeded.length, failedTargets.length); if (!result.failed.length) finishReviewSuccess(reviewSuccessAction.value); } finally { processing.value = false; } }
function finishReviewSuccess(action: ReviewSuccessAction) { reviewSuccessAction.value = 'none'; if (action === 'close-collection-detail') { collectionDetailVisible.value = false; return; } if (action === 'close-rejected-collection-detail') { previewVisible.value = false; collectionDetailVisible.value = false; ElMessage.warning('题集已驳回'); return; } if (action !== 'next-preview-question') return; const next = previewQueue.value[previewQueueIndex.value + 1] ?? questions.value[previewQueueIndex.value] ?? questions.value[0]; if (!next) { previewVisible.value = false; return; } void previewQuestion(next, questions.value); }
function collectionFailureMessages(failures: Array<{ reason: unknown }>) { return failures.flatMap(({ reason }) => { const response = reason as { response?: { data?: { data?: CollectionErrorData } } }; const issues = response.response?.data?.data?.blockingIssues; return issues?.map(issue => `${issue.itemOrder ? `第${issue.itemOrder}题：` : ''}${issue.message}`) ?? []; }); }
async function finishBatch(kind: ReviewKind, failedIds: string[], succeeded: number, failed: number, failures: Array<{ reason: unknown }> = []) { if (kind === 'question') { await loadQuestions(failedIds); if (collectionDetail.value) { collectionDetailLoading.value = true; try { collectionDetail.value = (await getCollectionRevision(collectionDetail.value.revisionId)).data; } finally { collectionDetailLoading.value = false; } await loadCollections(); } } else await loadCollections(failedIds); if (!failed) ElMessage.success(`处理完成，共成功 ${succeeded} 条`); else { const messages = kind === 'collection' ? collectionFailureMessages(failures) : []; if (messages.length) ElMessage.error(messages.join('；')); else ElMessage.warning(`处理完成：成功 ${succeeded} 条，失败 ${failed} 条；失败项已保留选择`); } }
function typeLabel(type: QuestionType) { return { CHOICE: '选择题', CASE: '案例题', ESSAY: '论文题' }[type]; }
function difficultyLabel(value: QuestionListVO['difficulty']) { return value ? { easy: '简单', medium: '中等', hard: '困难' }[value] : '-'; }
function questionStatusLabel(status: string) { return { draft: '草稿', pending_review: '审核中', rejected: '已驳回', published: '已发布' }[status as QuestionStatus] || status; }
function questionStatusTag(status: string): 'success' | 'warning' | 'info' | 'danger' { return status === 'published' ? 'success' : status === 'pending_review' ? 'warning' : status === 'rejected' ? 'danger' : 'info'; }
function collectionTypeLabel(type: CollectionType) { return { FIRST_DIAGNOSTIC: '首次诊断', PRACTICE: '专项题集', SIMULATION: '模拟试卷', PAST_PAPER: '历年真题' }[type]; }
function formatDateTime(value: string) { return value ? value.replace('T', ' ').slice(0, 19) : '-'; }
onMounted(async () => { const [result] = await Promise.all([listSyllabusVersions({ pageNum: 1, pageSize: 100 }), loadQuestions()]); syllabuses.value = result.data.rows; });
</script>

<style scoped lang="scss">
.review-page { min-height: 100%; background: var(--el-bg-color-page); }
.header-copy h2, .table-heading h3 { margin: 2px 0 0; }
.header-copy p, .table-heading p { margin: 4px 0 0; color: var(--el-text-color-secondary); font-size: 12px; }
.panel-kicker { color: var(--el-color-success); font-size: 10px; font-weight: 700; letter-spacing: .08em; text-transform: uppercase; }
.review-tabs :deep(.el-tabs__header) { margin: 0 0 10px; padding: 0 4px; }
.review-tabs :deep(.el-tabs__nav-wrap::after) { height: 1px; background-color: var(--el-border-color-light); }
.review-tabs :deep(.el-tabs__item) { height: 40px; padding: 0 20px; font-size: 14px; font-weight: 600; }
.review-tabs :deep(.el-tabs__content) { overflow: visible; }
.search-panel { margin-bottom: 10px; }
.query-form :deep(.el-form-item) { margin-bottom: 0; }
.query-form :deep(.el-input), .query-form :deep(.el-select) { width: 190px; }
.toolbar-shell { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.toolbar-actions { display: flex; align-items: center; justify-content: flex-end; gap: 8px; }
.drawer-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; width: 100%; padding-right: 36px; }
.review-actions { display: flex; flex: none; gap: 8px; }
.detail-title { margin: 20px 0 10px; font-size: 16px; }
.detail-title + .el-alert { margin-bottom: 12px; }
.reject-form { margin-top: 16px; }
.mixed-review-table { margin-top: 16px; }
.mixed-summary { margin-right: 14px; color: var(--el-text-color-secondary); }
@media (max-width: 900px) { .toolbar-shell { align-items: flex-start; flex-direction: column; } .toolbar-actions { flex-wrap: wrap; justify-content: flex-start; } .query-form :deep(.el-input), .query-form :deep(.el-select) { width: 100%; } }
</style>
