<template>
  <section class="question-search-picker">
    <section class="search-panel">
      <header class="section-heading">
        <div>
          <h3>题目检索</h3>
          <p>当前范围：{{ syllabusLabel }}</p>
        </div>
      </header>
      <el-form class="filter-form" label-position="top">
        <el-form-item label="题目编号 / 题干">
          <el-input v-model="filters.keyword" clearable placeholder="请输入关键词" :prefix-icon="Search" />
        </el-form-item>
        <el-form-item label="题型">
          <el-select v-model="filters.questionType" clearable placeholder="全部题型">
            <el-option label="选择题" value="CHOICE" />
            <el-option label="案例题" value="CASE" />
            <el-option label="论文题" value="ESSAY" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="filters.difficulty" clearable placeholder="全部难度">
            <el-option label="简单" value="easy" />
            <el-option label="中等" value="medium" />
            <el-option label="困难" value="hard" />
          </el-select>
        </el-form-item>
        <div class="filter-actions"><el-button :icon="Refresh" @click="resetFilters">重置</el-button></div>
      </el-form>
    </section>

    <section class="selection-layout" :class="{ 'is-directory-collapsed': directoryCollapsed }">
      <KnowledgeDirectoryPanel
        class="directory-panel"
        :nodes="knowledgeDirectory"
        title="考纲目录"
        eyebrow=""
        collapsible
        v-model:collapsed="directoryCollapsed"
        @select="selectKnowledgePoint"
        @clear="clearKnowledgePoint"
      />
      <main class="question-area">
        <div class="table-heading">
          <div><h3>可选题目</h3><p>显示当前考纲下的全部题目，可按状态识别其审核进度。</p></div>
          <el-button type="primary" :disabled="!checkedRows.length" @click="addCheckedQuestions">
            加入题集
          </el-button>
        </div>
        <el-table
          ref="questionTable"
          v-loading="loading"
          :data="rows"
          row-key="revisionId"
          border
          stripe
          @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection" width="48" :selectable="isSelectable" reserve-selection />
          <el-table-column prop="questionCode" label="题目编号" width="180" show-overflow-tooltip />
          <el-table-column prop="stemSummary" label="题干摘要" min-width="300" show-overflow-tooltip />
          <el-table-column prop="examSubjectName" label="考试科目" width="100" show-overflow-tooltip />
          <el-table-column label="题型" width="72"><template #default="scope">{{ typeLabel(scope.row.questionType) }}</template></el-table-column>
          <el-table-column label="难度" width="80"><template #default="scope">{{ difficultyLabel(scope.row.difficulty) }}</template></el-table-column>
          <el-table-column label="状态" width="88">
            <template #default="scope"><el-tag :type="statusTag(scope.row.status)" effect="light">{{ statusLabel(scope.row.status) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="118" fixed="right" class-name="question-actions">
            <template #default="scope">
              <el-tooltip content="查看" placement="top"><el-button link type="primary" :icon="View" @click="emit('preview', scope.row as QuestionListVO)" /></el-tooltip>
              <el-tooltip v-if="isEditable(scope.row.status)" content="编辑" placement="top"><el-button v-hasPermi="['certmuse:question:edit']" link type="primary" :icon="Edit" @click="openEdit(scope.row as QuestionListVO)" /></el-tooltip>
              <el-tooltip :content="selectedRevisionIds.includes(scope.row.revisionId) ? '已加入题集' : '加入题集'" placement="top"><el-button link type="success" :icon="Plus" :disabled="selectedRevisionIds.includes(scope.row.revisionId)" @click="emit('select', scope.row as QuestionListVO, selectedKnowledgeId)" /></el-tooltip>
            </template>
          </el-table-column>
        </el-table>
        <div class="table-footer">
          <span>当前范围：{{ selectedKnowledgeLabel || syllabusLabel }}</span>
          <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next" />
        </div>
      </main>
    </section>
    <InlineQuestionEditDialog v-model="editVisible" :question="editingQuestion" @saved="handleQuestionSaved" />
  </section>
</template>

<script setup lang="ts">
import { Edit, Plus, Refresh, Search, View } from '@element-plus/icons-vue';
import { computed, reactive, ref, watch } from 'vue';
import type { TableInstance } from 'element-plus';
import KnowledgeDirectoryPanel from './KnowledgeDirectoryPanel.vue';
import InlineQuestionEditDialog from './InlineQuestionEditDialog.vue';
import type { KnowledgeDirectoryNode } from './KnowledgeDirectoryPanel.vue';
import { listQuestions } from '@/api/certmuse/question';
import type { QuestionListVO, QuestionStatus, QuestionType } from '@/api/certmuse/question/types';

const props = defineProps<{ syllabusVersionId: string; syllabusLabel: string; knowledgeDirectory: KnowledgeDirectoryNode[]; selectedRevisionIds: string[] }>();
const emit = defineEmits<{ select: [question: QuestionListVO, knowledgePointId?: string]; preview: [question: QuestionListVO] }>();
const filters = reactive({ keyword: '', questionType: '', difficulty: '' });
const pagination = reactive({ pageNum: 1, pageSize: 10 });
const rows = ref<QuestionListVO[]>([]);
const questionTable = ref<TableInstance>();
const checkedRows = ref<QuestionListVO[]>([]);
const total = ref(0);
const loading = ref(false);
const directoryCollapsed = ref(false);
const editVisible = ref(false); const editingQuestion = ref<QuestionListVO>();
const selectedSubjectId = ref('');
const selectedKnowledgeId = ref('');
const selectedKnowledgeLabel = ref('');
let requestSequence = 0;

const selectedRevisionIdSet = computed(() => new Set(props.selectedRevisionIds));

watch(() => props.syllabusVersionId, () => { clearKnowledgePoint(); pagination.pageNum = 1; void loadQuestions(); }, { immediate: true });
watch(filters, () => { if (pagination.pageNum !== 1) pagination.pageNum = 1; else void loadQuestions(); }, { deep: true });
watch([() => pagination.pageNum, () => pagination.pageSize, selectedSubjectId, selectedKnowledgeId], () => void loadQuestions());

function typeLabel(type: QuestionType) { return { CHOICE: '选择题', CASE: '案例题', ESSAY: '论文题' }[type]; }
function difficultyLabel(value: QuestionListVO['difficulty']) { return value ? { easy: '简单', medium: '中等', hard: '困难' }[value] : '未设置'; }
function statusLabel(status: QuestionStatus) {
  return { draft: '草稿', pending_review: '审核中', rejected: '已驳回', published: '已发布' }[status];
}
function statusTag(status: QuestionStatus): 'success' | 'warning' | 'info' | 'danger' | 'primary' {
  return status === 'published' ? 'success' : status === 'rejected' ? 'danger' : status === 'pending_review' ? 'warning' : status === 'draft' ? 'info' : 'primary';
}
function resetFilters() { Object.assign(filters, { keyword: '', questionType: '', difficulty: '' }); }
function isEditable(status: QuestionStatus) { return status === 'draft' || status === 'rejected'; }
function openEdit(question: QuestionListVO) { editingQuestion.value = question; editVisible.value = true; }
function handleQuestionSaved() { void loadQuestions(); }
function isSelectable(row: QuestionListVO) { return !selectedRevisionIdSet.value.has(row.revisionId); }
function handleSelectionChange(selection: QuestionListVO[]) { checkedRows.value = selection; }
function addCheckedQuestions() {
  for (const question of checkedRows.value) emit('select', question, selectedKnowledgeId.value || undefined);
  questionTable.value?.clearSelection();
}
function selectKnowledgePoint(data: { id: string; label: string; children?: unknown[] }) {
  // 与题目管理页保持一致：只有知识树的一级科目节点才是 examSubjectId。
  // 目录知识点也可能有 children，不能据此误判为科目。
  const isExamSubject = props.knowledgeDirectory.some(subject => subject.id === data.id);
  selectedSubjectId.value = isExamSubject ? data.id : '';
  selectedKnowledgeId.value = isExamSubject ? '' : data.id;
  selectedKnowledgeLabel.value = data.label;
  pagination.pageNum = 1;
}
function clearKnowledgePoint() { selectedSubjectId.value = ''; selectedKnowledgeId.value = ''; selectedKnowledgeLabel.value = ''; }
async function loadQuestions() {
  const sequence = ++requestSequence;
  if (!props.syllabusVersionId) { rows.value = []; total.value = 0; return; }
  loading.value = true;
  try {
    const result = (await listQuestions({
      keyword: filters.keyword.trim() || undefined,
      syllabusVersionId: props.syllabusVersionId,
      examSubjectId: selectedSubjectId.value || undefined,
      knowledgePointId: selectedKnowledgeId.value || undefined,
      includeDescendants: true,
      questionType: (filters.questionType || undefined) as QuestionType | undefined,
      difficulty: filters.difficulty || undefined,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    })).data;
    if (sequence !== requestSequence) return;
    rows.value = result.rows;
    total.value = result.total;
    questionTable.value?.clearSelection();
  } finally { if (sequence === requestSequence) loading.value = false; }
}
</script>

<style scoped lang="scss">
.search-panel { margin-bottom: 16px; padding: 20px; border: 1px solid var(--el-border-color-light); border-radius: 8px; background: var(--el-bg-color); }
.section-heading h3, .table-heading h3 { margin: 0; }
.section-heading p, .table-heading p { margin: 6px 0 0; color: var(--el-text-color-secondary); }
.filter-form { display: grid; grid-template-columns: minmax(220px, 2fr) repeat(2, minmax(130px, 1fr)) auto; gap: 16px; align-items: end; margin-top: 18px; }
.filter-form :deep(.el-form-item) { margin-bottom: 0; }
.filter-actions { padding-bottom: 1px; }
.selection-layout { display: grid; grid-template-columns: 260px minmax(0, 1fr); gap: 16px; transition: grid-template-columns 0.24s ease; }
.selection-layout.is-directory-collapsed { grid-template-columns: 56px minmax(0, 1fr); }
.directory-panel.is-collapsed { padding: 0; }
.question-area { min-width: 0; padding: 20px; border: 1px solid var(--el-border-color-light); border-radius: 8px; background: var(--el-bg-color); }
.table-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.question-area :deep(.el-table .cell) { white-space: nowrap; }
.question-area :deep(.question-actions .cell) { white-space: nowrap; }
.table-footer { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-top: 16px; color: var(--el-text-color-secondary); }
@media (max-width: 1000px) { .filter-form { grid-template-columns: 1fr 1fr; } .selection-layout { grid-template-columns: 1fr; } }
</style>
