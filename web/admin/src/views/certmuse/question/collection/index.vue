<template>
  <div class="p-2 collection-page">
    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': filterCollapsed }">
        <template #header>
          <div
            class="panel-heading search-panel-toggle"
            role="button"
            tabindex="0"
            aria-label="切换题集筛选"
            :aria-expanded="!filterCollapsed"
            @click="filterCollapsed = !filterCollapsed"
            @keydown.enter.prevent="filterCollapsed = !filterCollapsed"
            @keydown.space.prevent="filterCollapsed = !filterCollapsed"
          >
            <div>
              <span class="panel-kicker">Search Filters</span>
              <h3>筛选条件</h3>
            </div>
          </div>
        </template>
        <el-form class="query-form" :model="filters" :inline="true">
          <el-form-item label="关键词">
            <el-input v-model="filters.keyword" clearable placeholder="题集名称或编码" :prefix-icon="Search" />
          </el-form-item>
          <el-form-item label="题集类型">
            <el-select v-model="filters.type" clearable placeholder="全部类型">
              <el-option label="首次诊断" value="FIRST_DIAGNOSTIC" />
              <el-option label="专项题集" value="PRACTICE" />
              <el-option label="模拟试卷" value="SIMULATION" />
              <el-option label="历年真题" value="PAST_PAPER" />
            </el-select>
          </el-form-item>
          <el-form-item label="考试资格">
            <el-select v-model="filters.certification" clearable placeholder="全部资格">
              <el-option
                v-for="certification in certificationOptions"
                :key="certification.id"
                :label="certification.certificationName"
                :value="certification.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="修订状态">
            <el-select v-model="filters.statuses" multiple collapse-tags collapse-tags-tooltip placeholder="全部状态">
              <el-option label="已发布" value="published" />
              <el-option label="审核中" value="pending_review" />
              <el-option label="被驳回" value="rejected" />
              <el-option label="草稿" value="draft" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" :loading="loading" @click="loadCollections">搜索</el-button>
            <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>

    <el-card shadow="hover" class="table-panel collection-results-panel">
      <template #header>
        <div class="toolbar-shell">
          <div class="table-heading">
            <span class="panel-kicker">Collection Dataset</span>
            <h3>题集列表</h3>
          </div>
          <div class="toolbar-actions">
            <el-button type="primary" plain :icon="Plus" @click="router.push('/content/collections/new')">
              新增题集
            </el-button>
            <el-button
              v-hasPermi="['certmuse:catalog:import']"
              type="primary"
              plain
              :icon="UploadFilled"
              @click="importVisible = true"
            >
              导入试卷
            </el-button>
            <el-button type="danger" plain :icon="Delete" :disabled="multiple" @click="confirmDeleteSelected">
              删除
            </el-button>
          </div>
        </div>
      </template>

      <el-table
        v-loading="loading"
        border
        class="data-table"
        :data="tableRows"
        row-key="rowKey"
        :tree-props="{ children: 'children', checkStrictly: true }"
        :indent="16"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="50" align="center" />
        <el-table-column label="题集 / 修订版本" min-width="260" :show-overflow-tooltip="true">
          <template #default="scope">
            <template v-if="scope.row.rowType === 'collection'">
              <el-link type="primary" underline="never" @click="showCollectionDetail(scope.row as CollectionTableRow)">
                {{ scope.row.name }}
              </el-link>
            </template>
            <div v-else class="revision-name">
              <strong>V{{ scope.row.revisionNo }}</strong>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="题集类型" min-width="110" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.rowType === 'collection'" effect="plain">{{ typeLabel(scope.row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="考试资格" min-width="180">
          <template #default="scope">
            <template v-if="scope.row.rowType === 'collection'">
              <div>{{ scope.row.certification }}</div>
            </template>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="170" align="center">
          <template #default="scope">
            <template v-if="scope.row.rowType === 'revision'">
              <el-tag :type="collectionStatusMeta[revisionDisplayStatus(scope.row as RevisionTableRow)].tagType">
                {{ collectionStatusMeta[revisionDisplayStatus(scope.row as RevisionTableRow)].label }}
              </el-tag>
            </template>
            <template v-else>
              <div class="collection-status-summary">
                <span class="summary-state">
                  发布版本：{{ scope.row.currentRevisionNo ? `V${scope.row.currentRevisionNo}` : '无' }}
                </span>
                <span v-if="activeRevision(scope.row as CollectionTableRow)" class="summary-state active-revision">
                  V{{ activeRevision(scope.row as CollectionTableRow)!.revisionNo }}
                  {{ collectionStatusMeta[revisionDisplayStatus(activeRevision(scope.row as CollectionTableRow)!)].label }}
                </span>
                <span v-else class="secondary-text">暂无最新操作</span>
              </div>
            </template>
          </template>
        </el-table-column>
        <el-table-column label="题目 / 总分" min-width="120" align="center">
          <template #default="scope">
            <span v-if="scope.row.rowType === 'revision'">
              {{ scope.row.questionCount }} 题 / {{ scope.row.totalScore }} 分
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="165" align="center">
          <template #default="scope">{{ scope.row.updatedTime }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="230" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <template v-if="scope.row.rowType === 'collection'">
              <el-tooltip content="查看题集" placement="top">
                <el-button
                  link
                  type="primary"
                  :icon="View"
                  @click="showCollectionDetail(scope.row as CollectionTableRow)"
                />
              </el-tooltip>
              <el-tooltip content="编辑题集名称" placement="top">
                <el-button
                  v-hasPermi="['certmuse:question:collection:edit']"
                  link
                  type="primary"
                  :icon="Edit"
                  aria-label="编辑题集名称"
                  @click="openRename(scope.row as CollectionTableRow)"
                />
              </el-tooltip>
              <el-tooltip :content="createRevisionTip(scope.row as CollectionTableRow)" placement="top">
                <span>
                  <el-button
                    link
                    type="primary"
                    :icon="Plus"
                    :disabled="
                      !currentPublishedRevision(scope.row as CollectionTableRow) ||
                      hasActiveRevision(scope.row as CollectionTableRow)
                    "
                    @click="
                      createRevision(
                        scope.row as CollectionTableRow,
                        currentPublishedRevision(scope.row as CollectionTableRow)!
                      )
                    "
                  />
                </span>
              </el-tooltip>
              <el-tooltip :content="collectionDeleteTip(scope.row as CollectionTableRow)" placement="top">
                <span>
                  <el-button
                    link
                    type="danger"
                    :icon="Delete"
                    :disabled="!isCollectionDeletable(scope.row as CollectionTableRow)"
                    @click="deleteCollection(scope.row as CollectionTableRow)"
                  />
                </span>
              </el-tooltip>
            </template>
            <template v-else>
              <el-tooltip content="查看修订" placement="top">
                <el-button
                  link
                  type="primary"
                  :icon="View"
                  @click="showRevisionDetail(scope.row as RevisionTableRow)"
                />
              </el-tooltip>
              <el-tooltip v-if="isRevisionEditable(scope.row as RevisionTableRow)" content="编辑题集" placement="top">
                <el-button link type="primary" :icon="Edit" @click="editRevision(scope.row as RevisionTableRow)" />
              </el-tooltip>
              <el-tooltip v-if="isRevisionEditable(scope.row as RevisionTableRow)" content="提交审核" placement="top">
                <el-button link type="primary" :icon="Promotion" @click="submitReview(scope.row as RevisionTableRow)" />
              </el-tooltip>
              <el-tooltip v-if="isRevisionEditable(scope.row as RevisionTableRow)" content="删除题集" placement="top">
                <el-button link type="danger" :icon="Delete" @click="deleteDraft(scope.row as RevisionTableRow)" />
              </el-tooltip>
              <el-tooltip
                v-if="scope.row.status === 'published'"
                :content="revisionCreateTip(scope.row as RevisionTableRow)"
                placement="top"
              >
                <span>
                  <el-button
                    link
                    type="primary"
                    :icon="Plus"
                    :disabled="hasActiveRevision(scope.row.collection)"
                    @click="createRevision(scope.row.collection, scope.row as RevisionTableRow)"
                  />
                </span>
              </el-tooltip>
              <el-tooltip
                v-if="(scope.row as RevisionTableRow).currentPublished"
                content="下架当前发布版本"
                placement="top"
              >
                <el-button
                  link
                  type="warning"
                  icon="Bottom"
                  @click="takeOffline(scope.row as RevisionTableRow)"
                />
              </el-tooltip>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="renameVisible" title="编辑题集名称" width="480px" destroy-on-close @closed="resetRenameForm">
      <el-alert
        v-if="!renameEditable"
        title="只有草稿状态的题集可以修改名称"
        type="warning"
        :closable="false"
        show-icon
        class="mb-4"
      />
      <el-form ref="renameFormRef" :model="renameForm" :rules="renameRules" label-position="top">
        <el-form-item label="题集名称" prop="collectionName">
          <el-input
            v-model="renameForm.collectionName"
            maxlength="200"
            show-word-limit
            clearable
            :disabled="!renameEditable"
            placeholder="请输入题集名称"
            @keyup.enter="submitRename"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="renameSubmitting" @click="renameVisible = false">取消</el-button>
        <el-button type="primary" :loading="renameSubmitting" :disabled="!renameEditable" @click="submitRename">
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="collectionDetailVisible" :title="collectionDetailTitle" width="680px">
      <el-descriptions v-if="activeCollection" :column="2" border>
        <el-descriptions-item label="题集名称" :span="2">{{ activeCollection.name }}</el-descriptions-item>
        <el-descriptions-item label="题集编码">{{ activeCollection.code }}</el-descriptions-item>
        <el-descriptions-item label="题集类型">{{ typeLabel(activeCollection.type) }}</el-descriptions-item>
        <el-descriptions-item label="考试资格">{{ activeCollection.certification }}</el-descriptions-item>
        <el-descriptions-item label="当前生效版本">
          {{ activeCollection.currentRevisionNo ? `V${activeCollection.currentRevisionNo}` : '尚未发布' }}
        </el-descriptions-item>
        <el-descriptions-item label="修订数量">{{ activeCollection.revisions.length }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog v-model="revisionDetailVisible" :title="revisionDetailTitle" width="940px" destroy-on-close>
      <div v-loading="revisionDetailLoading" class="revision-detail-dialog">
        <template v-if="revisionDetail">
          <el-alert
            v-if="revisionDetail.reviewOpinion"
            :title="collectionRejectionMessage"
            type="error"
            :closable="false"
            show-icon
          />
          <div class="revision-detail-meta">
            <el-tag :type="collectionStatusMeta[revisionDisplayStatus(activeRevisionDetail!)].tagType">
              {{ collectionStatusMeta[revisionDisplayStatus(activeRevisionDetail!)].label }}
            </el-tag>
            <span>更新时间：{{ revisionDetail.updatedTime }}</span>
          </div>
          <div class="revision-stat-grid">
            <article>
              <span>题集总分</span>
              <strong>{{ revisionDetail.totalReportScore }}</strong>
            </article>
            <article>
              <span>覆盖知识点</span>
              <strong>{{ revisionKnowledgeCount }}</strong>
            </article>
            <article>
              <span>难度分布</span>
              <div class="revision-distribution difficulty-distribution">
                <b>简 {{ revisionDifficultyCount('easy') }}</b>
                <b>中 {{ revisionDifficultyCount('medium') }}</b>
                <b>难 {{ revisionDifficultyCount('hard') }}</b>
              </div>
            </article>
            <article>
              <span>题型分布</span>
              <div class="revision-distribution type-distribution">
                <b>选 {{ revisionQuestionTypeCount('CHOICE') }}</b>
                <b>案 {{ revisionQuestionTypeCount('CASE') }}</b>
                <b>论 {{ revisionQuestionTypeCount('ESSAY') }}</b>
              </div>
            </article>
          </div>
          <section class="revision-item-list">
            <article v-for="item in revisionDetail.items" :key="item.questionRevisionId">
              <span class="revision-item-order">{{ item.itemOrder }}</span>
              <div class="revision-item-main">
                <strong>{{ item.stem }}</strong>
                <small>{{ item.knowledgePointLabel || item.examSubjectName }} · {{ questionTypeLabel(item.questionType) }}</small>
              </div>
              <span class="revision-item-score">{{ item.reportScore }} 分</span>
            </article>
          </section>
          <el-empty v-if="!revisionDetail.items.length" :image-size="72" description="该修订暂无题目" />
        </template>
      </div>
      </el-dialog>

    <PaperImportDialog v-model="importVisible" @success="openImportedPaper" />
  </div>
</template>

<script setup name="CertmuseCollection" lang="ts">
import { Delete, Edit, Plus, Promotion, Refresh, Search, UploadFilled, View } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';
import { computed, nextTick, onActivated, onDeactivated, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { listQualifications } from '@/api/certmuse/catalog/subject-version';
import type { QualificationVO } from '@/api/certmuse/catalog/subject-version';
import {
  createCollectionRevision,
  deleteCollectionDraft,
  getCollectionRevision,
  listCollectionManagements,
  renameCollection,
  submitCollectionReview,
  takeCollectionOffline
} from '@/api/certmuse/question/collection';
import type { CollectionRevisionDetailVO } from '@/api/certmuse/question/collection';
import type { CollectionErrorData } from '@/api/certmuse/question/collection/types';
import type { PaperImportProgressVO } from '@/api/certmuse/question/paper-import';
import PaperImportDialog from './PaperImportDialog.vue';
import type { CollectionDisplayStatus, CollectionStatus } from './model';
import {
  collectionStatusMeta,
  compareRevisionStatus,
  hasInProgressRevision,
  nextRevisionNo,
  revisionDisplayStatus
} from './model';

type CollectionType = 'FIRST_DIAGNOSTIC' | 'PRACTICE' | 'SIMULATION' | 'PAST_PAPER';
interface CollectionRevision {
  id: string;
  revisionNo: number;
  status: CollectionStatus;
  currentPublished: boolean;
  sourceRevisionNo?: number;
  rowVersion: number;
  questionCount: number;
  totalScore: number;
  hasReviewOpinion?: boolean;
  updatedTime: string;
}
interface CollectionItem {
  id: string;
  name: string;
  code: string;
  type: CollectionType;
  certification: string;
  certificationId: string;
  syllabus: string;
  syllabusId: string;
  updatedTime: string;
  currentRevisionNo?: number;
  revisions: CollectionRevision[];
}
type RevisionTableRow = CollectionRevision & { rowType: 'revision'; rowKey: string; collection: CollectionItem };
type CollectionTableRow = CollectionItem & { rowType: 'collection'; rowKey: string; children: RevisionTableRow[] };

const router = useRouter();
const filterCollapsed = ref(false);
const loading = ref(false);
const certificationOptions = ref<QualificationVO[]>([]);
const filters = reactive<{
  keyword: string;
  type: CollectionType | '';
  certification: string;
  statuses: CollectionDisplayStatus[];
}>({
  keyword: '',
  type: '',
  certification: '',
  statuses: []
});
const collectionDetailVisible = ref(false);
const revisionDetailVisible = ref(false);
const importVisible = ref(false);
const renameVisible = ref(false);
const renameSubmitting = ref(false);
const renameEditable = ref(false);
const renameFormRef = ref<FormInstance>();
const renameForm = reactive({ collectionId: '', collectionName: '' });
const renameRules: FormRules<typeof renameForm> = {
  collectionName: [
    {
      validator: (_rule, value: string, callback) => {
        const name = value?.trim();
        if (!name) callback(new Error('请输入题集名称'));
        else if (name.length > 200) callback(new Error('题集名称不能超过 200 个字符'));
        else callback();
      },
      trigger: ['blur', 'change']
    }
  ]
};
const revisionDetailLoading = ref(false);
const activeCollection = ref<CollectionItem>();
const activeRevisionDetail = ref<CollectionRevision>();
const revisionDetail = ref<CollectionRevisionDetailVO>();
const selectedRows = ref<Array<CollectionTableRow | RevisionTableRow>>([]);
const collections = ref<CollectionItem[]>([]);

const filteredCollections = computed(() =>
  collections.value.filter(collection => {
    const keyword = filters.keyword.trim().toLowerCase();
    return (
      (!keyword || `${collection.name}${collection.code}`.toLowerCase().includes(keyword)) &&
      (!filters.type || collection.type === filters.type) &&
      (!filters.certification || collection.certificationId === filters.certification) &&
      collection.revisions.some(
        revision =>
          !filters.statuses.length || filters.statuses.includes(revisionDisplayStatus(revision))
      )
    );
  })
);
const tableRows = computed<CollectionTableRow[]>(() =>
  filteredCollections.value.map(collection => ({
    ...collection,
    rowType: 'collection',
    rowKey: `collection-${collection.id}`,
    children: collection.revisions
      .filter(
        revision =>
          !filters.statuses.length || filters.statuses.includes(revisionDisplayStatus(revision))
      )
      .toSorted(compareRevisionStatus)
      .map(revision => ({
        ...revision,
        rowType: 'revision',
        rowKey: `revision-${revision.id}`,
        collection
      }))
  }))
);
const collectionDetailTitle = computed(() => activeCollection.value?.name ?? '题集详情');
const revisionDetailTitle = computed(() => `修订版本 V${activeRevisionDetail.value?.revisionNo ?? ''}`);
const collectionRejectionMessage = computed(() => {
  const opinion = revisionDetail.value?.reviewOpinion ?? '';
  const matched = opinion.match(/题目\s*([^（\s]+)（修订/);
  if (!matched) return `驳回原因：${opinion}`;
  const questionCode = matched[1];
  const item = revisionDetail.value?.items.find(candidate => candidate.questionCode === questionCode);
  return item ? `第 ${item.itemOrder} 题（${questionCode}）被驳回` : `题目（${questionCode}）被驳回`;
});
const revisionKnowledgeCount = computed(
  () => new Set(revisionDetail.value?.items.map(item => item.knowledgePointId).filter(Boolean) ?? []).size
);
const multiple = computed(() => selectedRows.value.length === 0);

function typeLabel(type: CollectionType) {
  return { FIRST_DIAGNOSTIC: '首次诊断', PRACTICE: '专项题集', SIMULATION: '模拟试卷', PAST_PAPER: '历年真题' }[type];
}
function questionTypeLabel(type: string) {
  return { CHOICE: '选择题', CASE: '案例题', ESSAY: '论文题' }[type] ?? type;
}
function revisionDifficultyCount(difficulty: string) {
  return revisionDetail.value?.items.filter(item => item.difficulty === difficulty).length ?? 0;
}
function revisionQuestionTypeCount(questionType: string) {
  return revisionDetail.value?.items.filter(item => item.questionType === questionType).length ?? 0;
}
function handleSelectionChange(rows: Array<CollectionTableRow | RevisionTableRow>) {
  selectedRows.value = rows;
}
function resetFilters() {
  Object.assign(filters, {
    keyword: '',
    type: '',
    certification: '',
    statuses: []
  });
  void loadCollections();
}
function openImportedPaper(result: PaperImportProgressVO) {
  void loadCollections();
  if (!result.collectionId || !result.revisionId) return;
  ElMessage.success(`试卷导入完成，已创建 ${result.validCount} 道草稿题目和题集草稿`);
  void router.push({ path: '/content/collections/new', query: { id: result.collectionId, revisionId: result.revisionId } });
}

async function loadCollections() {
  loading.value = true;
  try {
    const page = (
      await listCollectionManagements({
        keyword: filters.keyword.trim() || undefined,
        collectionType: filters.type || undefined,
        certificationId: filters.certification || undefined,
        statuses: filters.statuses,
        pageNum: 1,
        pageSize: 100
      })
    ).data;
    collections.value = page.rows.map(row => {
      return {
        id: row.collectionId,
        name: row.collectionName,
        code: row.collectionCode,
        type: row.collectionType,
        certification: row.certificationName,
        certificationId: row.certificationId,
        syllabus: row.syllabusVersionName,
        syllabusId: row.syllabusVersionId,
        updatedTime: formatDateTime(row.updatedTime),
        currentRevisionNo: row.currentPublishedRevisionNo ?? undefined,
        revisions: row.revisions.map(revision => ({
          id: revision.revisionId,
          revisionNo: revision.revisionNo,
          status: revision.status,
          currentPublished: revision.currentPublished,
          rowVersion: Number(revision.rowVersion),
          questionCount: revision.questionCount,
          totalScore: Number(revision.totalReportScore),
          hasReviewOpinion: revision.hasReviewOpinion,
          updatedTime: formatDateTime(revision.updatedTime)
        }))
      };
    });
  } catch (error) {
    collections.value = [];
    ElMessage.error(errorMessage(error, '题集列表加载失败'));
  } finally {
    loading.value = false;
  }
}

async function loadCertificationOptions() {
  try {
    certificationOptions.value = (await listQualifications({ pageNum: 1, pageSize: 100, status: '0' })).data.rows;
  } catch (error) {
    certificationOptions.value = [];
    ElMessage.error(errorMessage(error, '考试资格加载失败'));
  }
}
function activeRevision(collection: CollectionItem) {
  return collection.revisions
    .filter(revision => isRevisionEditable(revision) || revision.status === 'pending_review')
    .toSorted((left, right) => right.revisionNo - left.revisionNo)[0];
}
function isRevisionEditable(revision: Pick<CollectionRevision, 'status'>) {
  return revision.status === 'draft' || revision.status === 'rejected';
}
function hasDraftRevision(collection: CollectionItem) {
  return collection.revisions.some(revision => revision.status === 'draft');
}
function isCollectionDeletable(collection: CollectionItem) {
  return collection.revisions.length > 0 && collection.revisions.every(isRevisionEditable);
}
function collectionDeleteTip(collection: CollectionItem) {
  return isCollectionDeletable(collection)
    ? '删除题集及其全部版本'
    : '题集包含已发布或审核中版本，不能删除';
}
function hasActiveRevision(collection: CollectionItem) {
  return hasInProgressRevision(collection.revisions);
}
function currentPublishedRevision(collection: CollectionItem) {
  return collection.revisions.find(revision => revision.currentPublished);
}
function showCollectionDetail(collection: CollectionItem) {
  activeCollection.value = collection;
  collectionDetailVisible.value = true;
}
function openRename(collection: CollectionItem) {
  renameEditable.value = hasDraftRevision(collection);
  renameForm.collectionId = collection.id;
  renameForm.collectionName = collection.name;
  renameVisible.value = true;
  void nextTick(() => renameFormRef.value?.clearValidate());
}
function resetRenameForm() {
  renameForm.collectionId = '';
  renameForm.collectionName = '';
  renameEditable.value = false;
  renameFormRef.value?.clearValidate();
}
async function submitRename() {
  if (!renameEditable.value) {
    ElMessage.warning('只有草稿状态的题集可以修改名称');
    return;
  }
  const valid = await renameFormRef.value?.validate().then(() => true).catch(() => false);
  if (!valid || renameSubmitting.value) return;
  renameSubmitting.value = true;
  try {
    const collectionName = renameForm.collectionName.trim();
    await renameCollection(renameForm.collectionId, { collectionName });
    renameVisible.value = false;
    ElMessage.success('题集名称已更新');
    await loadCollections();
  } catch (error) {
    ElMessage.error(errorMessage(error, '题集名称更新失败'));
  } finally {
    renameSubmitting.value = false;
  }
}
function deleteCollection(collection: CollectionTableRow) {
  selectedRows.value = [collection];
  confirmDeleteSelected();
}
async function showRevisionDetail(row: RevisionTableRow) {
  activeRevisionDetail.value = row;
  revisionDetail.value = undefined;
  revisionDetailVisible.value = true;
  revisionDetailLoading.value = true;
  try {
    revisionDetail.value = (await getCollectionRevision(row.id)).data;
  } catch (error) {
    ElMessage.error(errorMessage(error, '修订详情加载失败'));
  } finally {
    revisionDetailLoading.value = false;
  }
}
function editRevision(row: RevisionTableRow) {
  router.push({ path: '/content/collections/new', query: { id: row.collection.id, revisionId: row.id } });
}
function submitReview(row: RevisionTableRow) {
  ElMessageBox.confirm(`提交时将自动执行发布检查。通过后 V${row.revisionNo} 进入审核中，期间不能编辑。`, '提交审核', {
    type: 'warning'
  })
    .then(async () => {
      await submitCollectionReview(row.id);
      ElMessage.success(`V${row.revisionNo} 已提交审核`);
      await loadCollections();
    })
    .catch(error => handleSubmitReviewError(error));
}
function deleteDraft(row: RevisionTableRow) {
  ElMessageBox.confirm(`确认永久删除 V${row.revisionNo} 吗？`, '删除题集版本', { type: 'warning' })
    .then(async () => {
      await deleteCollectionDraft(row.id);
      ElMessage.success(`题集 V${row.revisionNo} 已删除`);
      await loadCollections();
    })
    .catch(error => handleActionError(error, '删除题集版本失败'));
}
function takeOffline(row: RevisionTableRow) {
  ElMessageBox.confirm(
    `确认下架当前发布版本 V${row.revisionNo} 吗？下架后学员端将不再使用该题集。`,
    '下架题集',
    { type: 'warning' }
  )
    .then(async () => {
      await takeCollectionOffline(row.id);
      ElMessage.success(`题集 V${row.revisionNo} 已下架并退回草稿`);
      await loadCollections();
    })
    .catch(error => handleActionError(error, '下架题集失败'));
}
function confirmDeleteSelected() {
  const rows = selectedRows.value;
  const targetRevisions = new Map<string, RevisionTableRow>();
  const blockedRows: Array<CollectionTableRow | RevisionTableRow> = [];
  for (const row of rows) {
    if (row.rowType === 'revision') {
      if (isRevisionEditable(row)) targetRevisions.set(row.id, row);
      else blockedRows.push(row);
      continue;
    }
    const allRevisions = row.revisions.map(
      revision => ({
        ...revision,
        rowType: 'revision' as const,
        rowKey: `revision-${revision.id}`,
        collection: row
      }) satisfies RevisionTableRow
    );
    if (!allRevisions.length || allRevisions.some(revision => !isRevisionEditable(revision))) {
      blockedRows.push(row);
      continue;
    }
    allRevisions.forEach(revision => targetRevisions.set(revision.id, revision));
  }
  if (blockedRows.length) {
    ElMessage.warning('选中的题集包含已发布或审核中版本，不能整体删除；请仅选择草稿或已驳回版本。');
    return;
  }
  const deletableRows = [...targetRevisions.values()];
  if (!deletableRows.length) return;
  ElMessageBox.confirm(`确认永久删除选中的 ${deletableRows.length} 个草稿或已驳回版本吗？`, '批量删除题集', {
    type: 'warning',
    confirmButtonText: '确认删除',
    cancelButtonText: '取消'
  })
    .then(async () => {
      for (const row of deletableRows) await deleteCollectionDraft(row.id);
      ElMessage.success(`已删除 ${deletableRows.length} 个草稿或已驳回版本`);
      selectedRows.value = [];
      await loadCollections();
    })
    .catch(error => handleActionError(error, '批量删除题集失败'));
}
function createRevision(collection: CollectionItem, source: CollectionRevision) {
  if (hasActiveRevision(collection)) return;
  const newNo = nextRevisionNo(collection.revisions);
  const sourceLabel = source.currentPublished
    ? `当前发布版本 V${source.revisionNo}`
    : `历史发布版本 V${source.revisionNo}`;
  ElMessageBox.confirm(
    `将复制${sourceLabel}的完整内容生成草稿 V${newNo}。新版本发布前，学员端仍使用${collection.currentRevisionNo ? ` V${collection.currentRevisionNo}` : '当前版本'}。`,
    '创建新修订',
    { confirmButtonText: `创建草稿 V${newNo}`, type: source.currentPublished ? 'info' : 'warning' }
  )
    .then(async () => {
      const result = (await createCollectionRevision(collection.id, source.id)).data;
      ElMessage.success(`草稿 V${result.revisionNo} 已创建`);
      await loadCollections();
    })
    .catch(error => handleActionError(error, '创建新修订失败'));
}
function createRevisionTip(collection: CollectionItem) {
  if (hasActiveRevision(collection)) {
    const revision = activeRevision(collection)!;
    return `已有${collectionStatusMeta[revision.status].label} V${revision.revisionNo}，暂时不能创建新修订`;
  }
  return currentPublishedRevision(collection) ? '基于当前发布版本创建新修订' : '题集尚无已发布版本';
}
function revisionCreateTip(row: RevisionTableRow) {
  if (hasActiveRevision(row.collection)) {
    const revision = activeRevision(row.collection)!;
    return `已有${collectionStatusMeta[revision.status].label} V${revision.revisionNo}，暂时不能创建新修订`;
  }
  return row.currentPublished ? '基于当前发布版本创建新修订' : `基于历史版本 V${row.revisionNo} 创建新修订`;
}
function formatDateTime(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false });
}
function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback;
}
function handleActionError(error: unknown, fallback: string) {
  if (error !== 'cancel' && error !== 'close') ElMessage.error(errorMessage(error, fallback));
}
function handleSubmitReviewError(error: unknown) {
  if (error === 'cancel' || error === 'close') return;
  const response = error as { response?: { data?: { data?: CollectionErrorData } } };
  const issues = response.response?.data?.data?.blockingIssues;
  if (issues?.length) {
    ElMessage.error(issues.map(issue => `${issue.itemOrder ? `第${issue.itemOrder}题：` : ''}${issue.message}`).join('；'));
    return;
  }
  ElMessage.error(errorMessage(error, '提交审核失败'));
}

let refreshOnActivation = false;
function markRefreshOnActivation() {
  refreshOnActivation = true;
}
async function refreshAfterActivation() {
  if (!refreshOnActivation) return;
  refreshOnActivation = false;
  await loadCollections();
}

onMounted(async () => {
  await loadCertificationOptions();
  if (filters.certification && !certificationOptions.value.some(item => item.id === filters.certification)) filters.certification = '';
  await loadCollections();
});
onDeactivated(markRefreshOnActivation);
onActivated(() => void refreshAfterActivation());
</script>

<style scoped lang="scss">
@use '@/assets/styles/components/page-shell' as pageShell;

@include pageShell.table-crud-page;

.secondary-text,
.revision-name span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.collection-results-panel :deep(.el-table__body .el-table__row),
.collection-results-panel :deep(.el-table__body .el-table__cell) {
  height: 56px;
}
.collection-results-panel :deep(.el-table__body .el-table__cell) {
  padding-top: 0;
  padding-bottom: 0;
  vertical-align: middle;
}
.revision-name {
  display: inline-flex;
  height: 56px;
  align-items: center;
  gap: 10px;
  // The tree table has already applied the child-level indent.
  // Keep only a small gap after that indent for the V1/V2 label.
  padding-left: 8px;
}
.revision-name strong {
  font-size: 14px;
}
.summary-state {
  color: var(--el-text-color-regular);
  font-size: 13px;
}
.collection-status-summary {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}
.active-revision {
  color: var(--el-color-warning-dark-2);
}
.revision-detail-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.revision-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}
.revision-stat-grid article {
  min-height: 112px;
  padding: 16px;
  border-radius: 10px;
  background: var(--el-fill-color-light);
}
.revision-stat-grid span {
  display: block;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.revision-stat-grid strong {
  display: block;
  margin-top: 8px;
  font-size: 28px;
  line-height: 1;
}
.revision-distribution {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 28px;
}
.revision-distribution b {
  padding: 3px 6px;
  border-radius: 5px;
  font-size: 13px;
}
.difficulty-distribution b:nth-child(1) {
  color: #167545;
  background: #dcf5e7;
}
.difficulty-distribution b:nth-child(2) {
  color: #a4610a;
  background: #fff0d5;
}
.difficulty-distribution b:nth-child(3) {
  color: #b63642;
  background: #ffe2e5;
}
.type-distribution b:nth-child(1) {
  color: #2167b1;
  background: #e2efff;
}
.type-distribution b:nth-child(2) {
  color: #7c4bb5;
  background: #efe5ff;
}
.type-distribution b:nth-child(3) {
  color: #087b83;
  background: #dcf6f7;
}
.revision-item-list {
  border-top: 1px solid var(--el-border-color-lighter);
}
.revision-item-list article {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  min-height: 76px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.revision-item-order {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  border-radius: 8px;
  color: var(--el-color-success-dark-2);
  background: var(--el-color-success-light-9);
  font-size: 13px;
}
.revision-item-main {
  min-width: 0;
}
.revision-item-main strong,
.revision-item-main small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.revision-item-main small {
  margin-top: 5px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.revision-item-score {
  min-width: 52px;
  color: var(--el-text-color-regular);
  text-align: right;
}
@media (max-width: 760px) {
  .revision-stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
