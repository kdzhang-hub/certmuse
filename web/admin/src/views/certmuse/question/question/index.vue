<template>
  <div class="p-2 app-container question-page">
    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': !showSearch }">
        <template #header>
          <div class="panel-heading search-panel-toggle" @click.stop="showSearch = !showSearch">
            <div>
              <span class="panel-kicker">Question Filters</span>
              <h3>筛选条件</h3>
            </div>
          </div>
        </template>
        <div class="search-content">
          <el-form :model="filters" :inline="true" class="query-form">
            <el-form-item label="关键词">
              <el-input
                v-model="filters.keyword"
                clearable
                placeholder="请输入题目编号或题干"
                @keyup.enter="handleQuery"
              />
            </el-form-item>
            <el-form-item label="考试资格">
              <el-select
                v-model="filters.certificationId"
                clearable
                placeholder="全部资格"
                @change="handleCertificationChange"
              >
                <el-option
                  v-for="option in certificationOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
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
            <el-form-item label="状态">
              <el-select v-model="filters.status" clearable placeholder="全部状态">
                <el-option label="草稿" value="draft" />
                <el-option label="审核中" value="pending_review" />
                <el-option label="已驳回" value="rejected" />
                <el-option label="已发布" value="published" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
              <el-button icon="Refresh" @click="resetFilters">重置</el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-card>
    </div>

    <el-card
      shadow="hover"
      class="table-panel question-table-card"
      :class="{ 'has-syllabus-directory': scope.syllabus }"
    >
      <template #header>
        <div class="toolbar-shell">
          <div class="table-heading">
            <h3>题目列表</h3>
          </div>
          <div class="toolbar-actions">
            <el-button
              v-hasPermi="['certmuse:question:submit-review']"
              type="success"
              plain
              :icon="Promotion"
              :disabled="!selectedReviewSubmittableQuestions.length"
              :loading="submittingReview"
              @click="submitSelectedForReview"
            >
              提交审核{{ selectedReviewSubmittableQuestions.length ? `（${selectedReviewSubmittableQuestions.length}）` : '' }}
            </el-button>
            <el-button
              v-hasPermi="['certmuse:question:submit-review']"
              type="success"
              :icon="Promotion"
              :loading="submittingReview"
              @click="submitAllDraftsForReview"
            >
              提交全部草稿
            </el-button>
            <el-button
              v-hasPermi="['certmuse:question:offline']"
              type="warning"
              plain
              :icon="Bottom"
              :disabled="!selectedPublishedQuestions.length"
              :loading="offliningQuestions"
              @click="takeSelectedOffline"
            >
              批量下架{{ selectedPublishedQuestions.length ? `（${selectedPublishedQuestions.length}）` : '' }}
            </el-button>
            <el-button
              v-hasPermi="['certmuse:question:remove']"
              type="danger"
              plain
              :icon="Delete"
              :disabled="!selectedDeletableQuestions.length"
              :loading="deletingQuestions"
              @click="removeSelectedQuestions"
            >
              批量删除{{ selectedDeletableQuestions.length ? `（${selectedDeletableQuestions.length}）` : '' }}
            </el-button>
            <el-button
              v-hasPermi="['certmuse:catalog:import']"
              type="primary"
              plain
              :icon="Plus"
              @click="openQuestionImport"
            >
              新增
            </el-button>
            <right-toolbar v-model:show-search="showSearch" :search="false" @query-table="loadQuestions" />
          </div>
        </div>
      </template>

      <div class="question-list-layout" :class="{ 'has-directory': scope.syllabus, 'is-directory-collapsed': directoryCollapsed }">
        <KnowledgeDirectoryPanel
          v-if="scope.syllabus"
          class="syllabus-directory"
          :nodes="activeKnowledgeDirectory"
          title="考纲目录"
          eyebrow="SYLLABUS DIRECTORY"
          search-placeholder="搜索章节或知识点"
          clear-label="清除章节筛选"
          :show-counts="false"
          collapsible
          v-model:collapsed="directoryCollapsed"
          @select="selectKnowledgePoint"
          @clear="clearKnowledgePoint"
        />

        <div class="question-list-main">
          <div v-if="scope.syllabus" class="active-scope">
            <span>当前范围</span>
            <el-tag closable type="primary" effect="plain" @close="resetScope">{{ currentSearchScope }}</el-tag>
          </div>

          <div
            class="question-table-shell"
            :class="{ 'is-fixed': scope.syllabus, 'is-ten-page': scope.syllabus && pagination.pageSize === 10 }"
          >
            <el-table
              v-loading="loading"
              :data="rows"
              :height="scope.syllabus ? 570 : undefined"
              :scrollbar-always-on="!!scope.syllabus"
              data-full-height-table="false"
              border
              stripe
              class="data-table question-table"
              empty-text="没有符合条件的题目"
              @selection-change="questionSelection = $event"
            >
              <el-table-column
                type="selection"
                width="48"
                fixed="left"
                :selectable="isQuestionSelectable"
              />
              <el-table-column
                prop="questionCode"
                label="题目编号"
                min-width="170"
                fixed="left"
                show-overflow-tooltip
              />
              <el-table-column prop="stemSummary" label="题干摘要" min-width="260">
                <template #default="scope">
                  <div class="stem-summary">{{ scope.row.stemSummary }}</div>
                </template>
              </el-table-column>
              <el-table-column prop="examSubjectName" label="考试科目" width="120" show-overflow-tooltip />
              <el-table-column label="题型" width="96" align="center">
                <template #default="scope">
                  <el-tag :type="typeTag(scope.row.questionType)" effect="light">
                    {{ typeLabel(scope.row.questionType) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="难度" width="82" align="center">
                <template #default="scope">{{ difficultyLabel(scope.row.difficulty) }}</template>
              </el-table-column>
              <el-table-column label="状态" width="100" align="center">
                <template #default="scope">
                  <el-tag :type="statusTag(scope.row.status)" effect="light">
                    {{ statusLabel(scope.row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="更新时间" width="180" align="center">
                <template #default="scope">{{ formatDateTime(scope.row.updatedTime) }}</template>
              </el-table-column>
              <el-table-column
                label="操作"
                width="172"
                fixed="right"
                align="center"
                class-name="small-padding fixed-width"
              >
                <template #default="scope">
                  <el-tooltip content="查看" placement="top">
                    <el-button
                      v-hasPermi="['certmuse:question:preview']"
                      link
                      type="primary"
                      icon="View"
                      @click="openPreview(scope.row as QuestionListItem)"
                    />
                  </el-tooltip>
                  <el-tooltip v-if="isQuestionEditable(scope.row as QuestionListItem)" content="编辑" placement="top">
                    <el-button
                      v-hasPermi="['certmuse:question:edit']"
                      link
                      type="primary"
                      icon="Edit"
                      @click="openEdit(scope.row as QuestionListItem)"
                    />
                  </el-tooltip>
                  <el-tooltip v-if="isQuestionReviewSubmittable(scope.row as QuestionListItem)" content="提交审核" placement="top">
                    <el-button
                      v-hasPermi="['certmuse:question:offline']"
                      link
                      type="primary"
                      icon="Promotion"
                      @click="submitReview(scope.row as QuestionListItem)"
                    />
                  </el-tooltip>
                  <el-tooltip v-if="scope.row.status === 'published'" content="下架" placement="top">
                    <el-button
                      v-hasPermi="['certmuse:question:offline']"
                      link
                      type="warning"
                      icon="Bottom"
                      @click="takeOffline(scope.row as QuestionListItem)"
                    />
                  </el-tooltip>
                  <el-tooltip v-if="isQuestionDeletable(scope.row as QuestionListItem)" content="删除" placement="top">
                    <el-button
                      v-hasPermi="['certmuse:question:remove']"
                      link
                      type="primary"
                      icon="Delete"
                      @click="removeQuestion(scope.row as QuestionListItem)"
                    />
                  </el-tooltip>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div v-if="total > 0" class="question-pagination">
            <el-pagination
              v-model:current-page="pagination.pageNum"
              v-model:page-size="pagination.pageSize"
              background
              layout="total, sizes, prev, pager, next, jumper"
              :page-sizes="[10, 20, 30, 50]"
              :pager-count="7"
              :total="total"
              @current-change="loadQuestions"
              @size-change="handlePageSizeChange"
            />
          </div>
        </div>
      </div>
    </el-card>

    <el-dialog
      v-model="formVisible"
      title="编辑题目草稿"
      width="min(960px, calc(100vw - 32px))"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-alert
        v-if="editingStatus !== 'draft' && editingStatus !== 'rejected'"
        class="revision-alert"
        :title="
          editingStatus === 'published'
            ? '当前为已发布题目：正式保存时应创建新的草稿修订版。'
            : '当前题目不是草稿状态，正式接口需先校验是否允许编辑。'
        "
        type="warning"
        :closable="false"
        show-icon
      />
      <el-alert
        v-if="editingStatus === 'rejected' && editingReviewOpinion"
        class="revision-alert rejection-alert"
        title="审核驳回原因"
        type="error"
        :description="editingReviewOpinion"
        :closable="false"
        show-icon
      />
      <el-form ref="formRef" :model="form" :rules="formRules" label-position="top">
        <section class="editor-section">
          <header class="editor-section-header">
            <div>
              <h3>基础信息</h3>
              <p>题目编号与考试资格不可在普通编辑中修改。</p>
            </div>
          </header>
          <div class="form-grid form-grid--four">
            <el-form-item label="考试科目">
              <el-input :model-value="examSubjectName" readonly />
            </el-form-item>
            <el-form-item label="题型" prop="questionType">
              <el-select v-model="form.questionType" @change="handleQuestionTypeChange">
                <el-option label="选择题" value="CHOICE" />
                <el-option label="案例题" value="CASE" />
                <el-option label="论文题" value="ESSAY" />
              </el-select>
            </el-form-item>
            <el-form-item label="难度" prop="difficulty">
              <el-select v-model="form.difficulty" clearable placeholder="未标注">
                <el-option label="简单" value="easy" />
                <el-option label="中等" value="medium" />
                <el-option label="困难" value="hard" />
              </el-select>
            </el-form-item>
            <el-form-item label="预计作答时间（秒）">
              <el-input-number v-model="form.estimatedSeconds" :min="1" :max="14400" controls-position="right" />
            </el-form-item>
          </div>
          <div class="knowledge-binding-workspace">
            <section class="selected-knowledge-panel">
              <header class="knowledge-panel-heading">
                <div>
                  <h4>已选知识点</h4>
                  <p>必须且只能设置一个主知识点。</p>
                </div>
              </header>
              <el-radio-group v-model="primaryKnowledgeId" class="question-knowledge-editor">
                <div
                  v-for="(binding, index) in form.knowledgeBindings"
                  :key="`${binding.knowledgePointId}-${index}`"
                  class="question-knowledge-row"
                >
                  <span class="knowledge-binding-name">
                    {{ binding.knowledgePointLabel?.trim() || `知识点 #${binding.knowledgePointId}` }}
                  </span>
                  <el-radio :value="binding.knowledgePointId">主知识点</el-radio>
                  <el-button
                    text
                    type="danger"
                    :icon="Delete"
                    :disabled="form.knowledgeBindings.length === 1"
                    aria-label="删除知识点"
                    @click="removeKnowledgeBinding(index)"
                  />
                </div>
              </el-radio-group>
            </section>
            <section class="editor-knowledge-tree-panel">
              <header class="knowledge-panel-heading knowledge-panel-heading--tree">
                <div>
                  <h4>{{ editorSyllabusName }} · 知识点目录</h4>
                  <p>点击最末级知识点右侧的加号，加入左侧已选列表。</p>
                </div>
                <el-input v-model="editorKnowledgeKeyword" clearable placeholder="检索知识点" :prefix-icon="Search" />
              </header>
              <el-tree
                ref="editorKnowledgeTreeRef"
                node-key="id"
                :data="editorKnowledgeTree"
                :props="treeProps"
                :filter-node-method="filterEditorKnowledgeNode"
                :default-expanded-keys="editorExpandedKeys"
                :expand-on-click-node="false"
              >
                <template #default="{ data }">
                  <span class="editor-knowledge-tree-node">
                    <span>{{ data.label }}</span>
                    <el-button
                      v-if="!data.children?.length"
                      text
                      type="primary"
                      :icon="Plus"
                      :disabled="isKnowledgePointBound(data.id, -1)"
                      :aria-label="`添加知识点 ${data.label}`"
                      @click.stop="addKnowledgeFromTree(data)"
                    />
                  </span>
                </template>
              </el-tree>
              <el-empty
                v-if="editorKnowledgeTree.length === 0"
                description="当前考纲暂无此科目知识点"
                :image-size="56"
              />
            </section>
          </div>
        </section>

        <section class="editor-section">
          <header class="editor-section-header">
            <div>
              <h3>{{ typeLabel(form.questionType) }}内容</h3>
              <p>{{ typeEditorHint }}</p>
            </div>
          </header>
          <el-form-item :label="stemEditorLabel" prop="stem">
            <el-input v-model="form.stem" type="textarea" :rows="5" maxlength="5000" show-word-limit />
          </el-form-item>

          <template v-if="form.questionType === 'CHOICE'">
            <div class="editor-section-header editor-section-header--sub">
              <div>
                <h4>选项与标准答案</h4>
                <p>当前一期仅支持单选；标准答案仍按单元素数组保存。</p>
              </div>
              <el-button :icon="Plus" :disabled="form.options.length >= 10" @click="addOption">添加选项</el-button>
            </div>
            <el-radio-group v-model="form.correctOptionKey" class="option-editor-list">
              <div v-for="(option, index) in form.options" :key="option.label" class="option-editor-row">
                <el-radio :value="option.label">{{ option.label }}</el-radio>
                <el-input v-model="option.content" :placeholder="`请输入选项 ${option.label}`" maxlength="1000" />
                <el-button
                  text
                  type="danger"
                  :icon="Delete"
                  :disabled="form.options.length <= 2"
                  aria-label="删除选项"
                  @click.prevent="removeOption(index)"
                />
              </div>
            </el-radio-group>
          </template>

          <template v-else>
            <el-form-item :label="form.questionType === 'CASE' ? '参考答案' : '参考提纲 / 范文要点'">
              <el-input
                v-model="form.referenceAnswer"
                type="textarea"
                :rows="4"
                maxlength="5000"
                show-word-limit
                placeholder="草稿允许暂缺，提交审核前必须补齐"
              />
            </el-form-item>
          </template>
        </section>

        <section class="editor-section">
          <header class="editor-section-header">
            <div>
              <h3>解析与订正</h3>
              <p>解析面向学习者，常见错误用于后续订正和诊断。</p>
            </div>
          </header>
          <el-form-item label="答案解析">
            <el-input v-model="form.analysis" type="textarea" :rows="3" maxlength="5000" show-word-limit />
          </el-form-item>
          <el-form-item label="常见错误">
            <el-input v-model="form.commonMistakes" type="textarea" :rows="2" maxlength="2000" show-word-limit />
          </el-form-item>
        </section>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="saveQuestion">保存草稿</el-button>
      </template>
    </el-dialog>

    <ImportDialog
      v-model="importVisible"
      import-type="question"
      :initial-syllabus-version-id="scope.syllabus || undefined"
      @closed="loadQuestions"
    />

    <QuestionPreviewDialog v-model="previewVisible" :question="previewItem" />
  </div>
</template>

<script setup name="CertmuseQuestion" lang="ts">
import { Bottom, Delete, Plus, Promotion, Refresh, Search } from '@element-plus/icons-vue';
import to from 'await-to-js';
import { type ElTree, type FormInstance, type FormRules } from 'element-plus';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import type { KnowledgeTreeNodeVO, SyllabusListVO } from '@/api/certmuse/catalog/knowledge/types';
import type { QualificationVO } from '@/api/certmuse/catalog/subject-version';
import type {
  KnowledgeBindingVO as QuestionKnowledgeBinding,
  QuestionDetailVO,
  QuestionListVO as QuestionListItem,
  QuestionOptionVO as QuestionOption,
  QuestionPreviewVO,
  QuestionStatus,
  QuestionType
} from '@/api/certmuse/question/types';
import type { KnowledgeDirectoryNode } from '@/components/certmuse/KnowledgeDirectoryPanel.vue';
import { getKnowledgeTree, listSyllabusVersions } from '@/api/certmuse/catalog/knowledge';
import { listQualifications } from '@/api/certmuse/catalog/subject-version';
import {
  deleteQuestion,
  getQuestionDetail,
  getQuestionPreview,
  listQuestions,
  saveQuestionDraft,
  submitQuestionReview,
  takeQuestionOffline
} from '@/api/certmuse/question';
import KnowledgeDirectoryPanel from '@/components/certmuse/KnowledgeDirectoryPanel.vue';
import QuestionPreviewDialog from '@/components/certmuse/QuestionPreviewDialog.vue';
import ImportDialog from '@/views/certmuse/catalog/import/ImportDialog.vue';
import modal from '@/plugins/modal';
import { runSequentialBatch } from '../review/batch';
import {
  isQuestionDeletable,
  isQuestionEditable,
  isQuestionReviewSubmittable,
  isQuestionSelectable
} from './question-list';

const treeProps = { children: 'children', label: 'label' };
const syllabuses = ref<SyllabusListVO[]>([]);
const rows = ref<QuestionListItem[]>([]);
const total = ref(0);
const loading = ref(false);
const submittingReview = ref(false);
const offliningQuestions = ref(false);
const deletingQuestions = ref(false);
const questionSelection = ref<QuestionListItem[]>([]);
const selectedReviewSubmittableQuestions = computed(() => questionSelection.value.filter(isQuestionReviewSubmittable));
const selectedPublishedQuestions = computed(() => questionSelection.value.filter(item => item.status === 'published'));
const selectedDeletableQuestions = computed(() => questionSelection.value.filter(isQuestionDeletable));
const showSearch = ref(true);
const directoryCollapsed = ref(false);
const importVisible = ref(false);
const scope = reactive({ syllabus: '', subjectId: '' });
const filters = reactive<{
  keyword: string;
  certificationId: string;
  questionType: QuestionType | '';
  difficulty: NonNullable<QuestionListItem['difficulty']> | '';
  status: QuestionStatus | '';
}>({ keyword: '', certificationId: '', questionType: '', difficulty: '', status: '' });
const certificationOptions = ref<Array<{ value: string; label: string; syllabusVersionId?: string }>>([]);
const pagination = reactive({ pageNum: 1, pageSize: 10 });
const selectedKnowledgeId = ref('');
const selectedKnowledgeLabel = ref('');
const editorKnowledgeTreeRef = ref<InstanceType<typeof ElTree>>();
const editorKnowledgeKeyword = ref('');
const formRef = ref<FormInstance>();
const formVisible = ref(false);
const previewVisible = ref(false);
const previewItem = ref<QuestionPreviewVO>();
const editingId = ref('');
const editingStatus = ref<QuestionStatus>('draft');
const editingReviewOpinion = ref('');
const baseRevisionId = ref('');
const rowVersion = ref('');
const examSubjectName = ref('');
const knowledgeDirectories = ref<Record<string, KnowledgeDirectoryNode[]>>({});
interface QuestionEditForm {
  syllabusId: string;
  examSubjectId: string;
  questionType: QuestionType;
  difficulty: QuestionListItem['difficulty'];
  knowledgeBindings: QuestionKnowledgeBinding[];
  estimatedSeconds?: number;
  stem: string;
  options: QuestionOption[];
  correctOptionKey: string;
  referenceAnswer: string;
  analysis: string;
  commonMistakes: string;
}
const form = reactive<QuestionEditForm>({
  syllabusId: '',
  examSubjectId: '',
  questionType: 'CHOICE',
  difficulty: null,
  knowledgeBindings: [],
  estimatedSeconds: undefined,
  stem: '',
  options: [],
  correctOptionKey: '',
  referenceAnswer: '',
  analysis: '',
  commonMistakes: ''
});
const formRules: FormRules = {
  questionType: [{ required: true, message: '请选择题型', trigger: 'change' }],
  stem: [{ required: true, message: '请输入题干', trigger: 'blur' }]
};

const activeKnowledgeDirectory = computed(() => knowledgeDirectories.value[scope.syllabus] ?? []);
const editorSyllabusName = computed(
  () => syllabuses.value.find(item => item.id === form.syllabusId)?.displayName ?? '当前考纲'
);
const editorKnowledgeTree = computed(() => {
  const directory = knowledgeDirectories.value[form.syllabusId] ?? [];
  const subject = directory.find(item => item.id === form.examSubjectId);
  return subject ? [subject] : [];
});
const currentSyllabusKnowledgeOptions = computed(() => {
  const collectLeaves = (nodes: KnowledgeDirectoryNode[]): KnowledgeDirectoryNode[] =>
    nodes.flatMap(node => (node.children?.length ? collectLeaves(node.children) : [node]));
  return collectLeaves(editorKnowledgeTree.value);
});
const editorExpandedKeys = computed(() => {
  const expanded = new Set<string>();
  const visit = (nodes: KnowledgeDirectoryNode[], ancestors: string[]): boolean =>
    nodes.some(node => {
      const path = [...ancestors, node.id];
      if (form.knowledgeBindings.some(binding => binding.knowledgePointId === node.id)) {
        path.forEach(id => expanded.add(id));
        return true;
      }
      return node.children?.length ? visit(node.children, path) : false;
    });
  visit(editorKnowledgeTree.value, []);
  if (expanded.size === 0 && editorKnowledgeTree.value[0]) expanded.add(editorKnowledgeTree.value[0].id);
  return [...expanded];
});
const primaryKnowledgeId = computed({
  get: () => form.knowledgeBindings.find(binding => binding.relationRole === 'primary')?.knowledgePointId ?? '',
  set: knowledgePointId => {
    form.knowledgeBindings.forEach(binding => {
      binding.relationRole = binding.knowledgePointId === knowledgePointId ? 'primary' : 'secondary';
    });
  }
});
const currentSearchScope = computed(() => {
  if (!scope.syllabus) return '全考纲';
  const syllabus = syllabuses.value.find(item => item.id === scope.syllabus);
  const parts = [syllabus?.displayName ?? '已选考纲'];
  if (selectedKnowledgeLabel.value) {
    parts.push(selectedKnowledgeLabel.value);
  }
  return parts.join(' · ');
});
const stemEditorLabel = computed(() =>
  form.questionType === 'CASE' ? '案例材料与问题' : form.questionType === 'ESSAY' ? '论文题目与写作要求' : '题干'
);
const typeEditorHint = computed(() => {
  if (form.questionType === 'CHOICE') return '维护题干、选项和唯一正确答案。';
  if (form.questionType === 'CASE') return '维护案例材料、问题和参考答案。';
  return '维护论文题目、写作要求和参考提纲。';
});
watch(editorKnowledgeKeyword, value => editorKnowledgeTreeRef.value?.filter(value));

function filterEditorKnowledgeNode(value: string, data: KnowledgeDirectoryNode) {
  return !value.trim() || data.label.toLowerCase().includes(value.trim().toLowerCase());
}
function selectKnowledgePoint(data: KnowledgeDirectoryNode) {
  const isExamSubject = activeKnowledgeDirectory.value.some(subject => subject.id === data.id);
  if (isExamSubject) {
    scope.subjectId = data.id;
    selectedKnowledgeId.value = '';
    selectedKnowledgeLabel.value = data.label;
    void loadQuestions();
    return;
  }
  // 知识点自身已限定所属科目；不把目录节点 ID 作为 examSubjectId 传给后端。
  scope.subjectId = '';
  selectedKnowledgeId.value = data.id;
  selectedKnowledgeLabel.value = data.label;
  void loadQuestions();
}
function clearKnowledgePoint() {
  scope.subjectId = '';
  selectedKnowledgeId.value = '';
  selectedKnowledgeLabel.value = '';
  void loadQuestions();
}
function handleQuery() {
  pagination.pageNum = 1;
  void loadQuestions();
}
function handleCertificationChange(certificationId: string | undefined) {
  scope.subjectId = '';
  selectedKnowledgeId.value = '';
  selectedKnowledgeLabel.value = '';
  pagination.pageNum = 1;
  const syllabusVersionId = certificationOptions.value.find(option => option.value === certificationId)?.syllabusVersionId;
  scope.syllabus = syllabusVersionId ?? '';
  if (scope.syllabus) void loadKnowledgeTree(scope.syllabus);
  void loadQuestions();
}
function openQuestionImport() {
  importVisible.value = true;
}
function selectSyllabus(syllabusId: string) {
  scope.subjectId = '';
  selectedKnowledgeId.value = '';
  selectedKnowledgeLabel.value = '';
  pagination.pageNum = 1;
  if (!syllabusId) {
    void loadQuestions();
    return;
  }
  scope.syllabus = syllabusId;
  void loadKnowledgeTree(syllabusId);
  void loadQuestions();
}
function resetScope() {
  Object.assign(scope, { syllabus: '', subjectId: '' });
  selectedKnowledgeId.value = '';
  selectedKnowledgeLabel.value = '';
  pagination.pageNum = 1;
  void loadQuestions();
}
function resetFilters() {
  Object.assign(filters, { keyword: '', certificationId: '', questionType: '', difficulty: '', status: '' });
  Object.assign(scope, { syllabus: '', subjectId: '' });
  selectedKnowledgeId.value = '';
  selectedKnowledgeLabel.value = '';
  pagination.pageNum = 1;
  void loadQuestions();
}
async function loadCertificationOptions() {
  const pageSize = 100;
  const firstPage = await listQualifications({ status: '0', pageNum: 1, pageSize });
  const rows = [...firstPage.data.rows];
  for (let pageNum = 2; pageNum <= Math.ceil(firstPage.data.total / pageSize); pageNum += 1) {
    const page = await listQualifications({ status: '0', pageNum, pageSize });
    rows.push(...page.data.rows);
  }
  certificationOptions.value = rows.map(item => ({
    value: item.id,
    label: item.certificationName,
    syllabusVersionId: latestSyllabusVersion(item)?.id
  }));
}
function latestSyllabusVersion(qualification: QualificationVO) {
  return qualification.versions.toSorted((left, right) =>
    `${right.publishedDate ?? ''}\u0000${right.updateTime}`.localeCompare(`${left.publishedDate ?? ''}\u0000${left.updateTime}`)
  )[0];
}
function typeLabel(value: QuestionType) {
  return value === 'CHOICE' ? '选择题' : value === 'CASE' ? '案例题' : '论文题';
}
function typeTag(value: QuestionType): 'success' | 'warning' | 'info' {
  return value === 'CHOICE' ? 'success' : value === 'CASE' ? 'warning' : 'info';
}
function difficultyLabel(value: QuestionListItem['difficulty']) {
  return value === 'easy' ? '简单' : value === 'medium' ? '中等' : value === 'hard' ? '困难' : '未标注';
}
function statusLabel(value: QuestionStatus) {
  return {
    draft: '草稿',
    pending_review: '审核中',
    rejected: '已驳回',
    published: '已发布'
  }[value];
}
function statusTag(value: QuestionStatus): 'info' | 'warning' | 'success' | 'danger' {
  const tags: Record<QuestionStatus, 'info' | 'warning' | 'success' | 'danger'> = {
    draft: 'info',
    pending_review: 'warning',
    rejected: 'danger',
    published: 'success'
  };
  return tags[value];
}
function questionStatusRank(status: QuestionStatus) {
  return { rejected: 0, pending_review: 1, published: 2, draft: 3 }[status];
}
function compareQuestions(left: QuestionListItem, right: QuestionListItem) {
  return questionStatusRank(left.status) - questionStatusRank(right.status);
}
function formatDateTime(value: string) {
  return new Date(value).toLocaleString('zh-CN', { hour12: false });
}
function createDefaultOptions(): QuestionOption[] {
  return ['A', 'B', 'C', 'D'].map(label => ({ label, content: '' }));
}
function isKnowledgePointBound(knowledgePointId: string, currentIndex: number) {
  return form.knowledgeBindings.some(
    (binding, index) => index !== currentIndex && binding.knowledgePointId === knowledgePointId
  );
}
function removeKnowledgeBinding(index: number) {
  if (form.knowledgeBindings.length <= 1) return;
  const removedWasPrimary = form.knowledgeBindings[index].relationRole === 'primary';
  form.knowledgeBindings.splice(index, 1);
  if (removedWasPrimary) form.knowledgeBindings[0].relationRole = 'primary';
}
function setPrimaryKnowledge(index: number) {
  form.knowledgeBindings.forEach((binding, bindingIndex) => {
    binding.relationRole = bindingIndex === index ? 'primary' : 'secondary';
  });
}
function addKnowledgeFromTree(data: KnowledgeDirectoryNode) {
  if (data.children?.length || isKnowledgePointBound(data.id, -1)) return;
  const primaryExists = form.knowledgeBindings.some(binding => binding.relationRole === 'primary');
  form.knowledgeBindings.push({
    knowledgePointId: data.id,
    knowledgePointLabel: data.label,
    relationRole: primaryExists ? 'secondary' : 'primary',
    sortOrder: form.knowledgeBindings.length
  });
}
function ensureExactlyOnePrimaryKnowledge() {
  if (form.knowledgeBindings.length === 0) {
    const firstKnowledgePoint = currentSyllabusKnowledgeOptions.value[0];
    if (firstKnowledgePoint) {
      form.knowledgeBindings.push({
        knowledgePointId: firstKnowledgePoint.id,
        knowledgePointLabel: firstKnowledgePoint.label,
        relationRole: 'primary',
        sortOrder: 0
      });
    }
    return;
  }
  const primaryIndex = form.knowledgeBindings.findIndex(binding => binding.relationRole === 'primary');
  setPrimaryKnowledge(primaryIndex >= 0 ? primaryIndex : 0);
}
function handleQuestionTypeChange(value: QuestionType) {
  if (value === 'CHOICE') {
    form.options = createDefaultOptions();
    form.correctOptionKey = '';
    form.referenceAnswer = '';
    return;
  }
  form.options = [];
  form.correctOptionKey = '';
  form.referenceAnswer = '';
}
function addOption() {
  if (form.options.length >= 10) return;
  form.options.push({ label: String.fromCharCode(65 + form.options.length), content: '' });
}
function removeOption(index: number) {
  if (form.options.length <= 2) return;
  const selectedIndex = form.options.findIndex(option => option.label === form.correctOptionKey);
  form.options.splice(index, 1);
  form.options.forEach((option, optionIndex) => {
    option.label = String.fromCharCode(65 + optionIndex);
  });
  if (selectedIndex === index) {
    form.correctOptionKey = '';
  } else if (selectedIndex >= 0) {
    const nextIndex = selectedIndex > index ? selectedIndex - 1 : selectedIndex;
    form.correctOptionKey = form.options[nextIndex]?.label ?? '';
  }
}
async function openEdit(item: QuestionListItem) {
  const detail = (await getQuestionDetail(item.questionId, item.revisionId)).data;
  await loadKnowledgeTree(detail.syllabusVersionId);
  const answer = detail.answer?.value;
  Object.assign(form, {
    syllabusId: detail.syllabusVersionId,
    examSubjectId: detail.examSubjectId,
    questionType: detail.questionType,
    difficulty: detail.difficulty,
    knowledgeBindings: detail.knowledgeBindings.map(binding => ({ ...binding })),
    estimatedSeconds: detail.estimatedSeconds ?? undefined,
    stem: detail.stem,
    options: detail.options.map(option => ({
      label: option.label,
      content: option.content,
      sortOrder: option.sortOrder
    })),
    correctOptionKey: Array.isArray(answer) ? (answer[0] ?? '') : '',
    referenceAnswer: typeof answer === 'string' ? answer : '',
    analysis: detail.analysis ?? '',
    commonMistakes: detail.commonMistakes ?? ''
  });
  ensureExactlyOnePrimaryKnowledge();
  editingId.value = detail.questionId;
  baseRevisionId.value = detail.revisionId;
  rowVersion.value = detail.rowVersion;
  editingStatus.value = detail.status;
  editingReviewOpinion.value = detail.reviewOpinion?.trim() ?? '';
  examSubjectName.value = detail.examSubjectName;
  formVisible.value = true;
  nextTick(() => formRef.value?.clearValidate());
}
async function openPreview(item: QuestionListItem) {
  previewItem.value = (await getQuestionPreview(item.questionId, item.revisionId)).data;
  previewVisible.value = true;
}
async function saveQuestion() {
  if (!(await formRef.value?.validate().catch(() => false))) return;
  const result = (
    await saveQuestionDraft(editingId.value, {
      baseRevisionId: baseRevisionId.value,
      rowVersion: rowVersion.value,
      examSubjectId: form.examSubjectId,
      questionType: form.questionType,
      difficulty: form.difficulty,
      estimatedSeconds: form.estimatedSeconds,
      stem: form.stem,
      options:
        form.questionType === 'CHOICE'
          ? form.options.map((option, index) => ({ ...option, sortOrder: index + 1 }))
          : [],
      answer:
        form.questionType === 'CHOICE'
          ? {
              schemaVersion: '1.0',
              answerType: 'option_keys',
              selectionMode: 'single',
              value: form.correctOptionKey ? [form.correctOptionKey] : []
            }
          : { schemaVersion: '1.0', answerType: 'reference_text', value: form.referenceAnswer },
      analysis: form.analysis || undefined,
      commonMistakes: form.commonMistakes || undefined,
      knowledgeBindings: form.knowledgeBindings.map((binding, index) => ({
        knowledgePointId: binding.knowledgePointId,
        relationRole: binding.relationRole,
        sortOrder: index
      }))
    })
  ).data;
  ElMessage.success(result.createdNewRevision ? '已创建并保存新的草稿修订版' : '题目草稿已保存');
  formVisible.value = false;
  await loadQuestions();
}
async function removeQuestion(item: QuestionListItem) {
  const [cancelled] = await to(
    modal.confirm(`确认删除“${item.questionCode}”吗？仅允许删除从未发布且未被业务数据引用的草稿或已驳回题目。`) as Promise<unknown>
  );
  if (cancelled) return;
  await deleteQuestion(item.questionId);
  modal.msgSuccess('题目已删除');
  await loadQuestions();
}
async function removeSelectedQuestions() {
  const targets = [...selectedDeletableQuestions.value];
  if (!targets.length) return;
  await ElMessageBox.confirm(
    `确认删除选中的 ${targets.length} 道题目吗？已发布、待审核或已被业务数据引用的题目不会被删除。`,
    '批量删除题目',
    { type: 'warning', confirmButtonText: `删除 ${targets.length} 道` }
  );
  deletingQuestions.value = true;
  try {
    const result = await runSequentialBatch(targets, item => deleteQuestion(item.questionId));
    questionSelection.value = result.failed.map(entry => entry.item);
    await loadQuestions();
    if (!result.failed.length) ElMessage.success(`已删除全部 ${result.succeeded.length} 道题目`);
    else ElMessage.warning(`删除完成：成功 ${result.succeeded.length} 道，失败 ${result.failed.length} 道`);
  } finally {
    deletingQuestions.value = false;
  }
}
async function submitReview(item: QuestionListItem) {
  const [cancelled] = await to(
    modal.confirm(`提交“${item.questionCode}”前将自动执行审核门禁。通过后题目将进入审核中，期间不能编辑。`) as Promise<unknown>
  );
  if (cancelled) return;
  await submitQuestionReview(item.revisionId);
  modal.msgSuccess('题目已提交审核');
  await loadQuestions();
}
async function takeOffline(item: QuestionListItem) {
  const [cancelled] = await to(
    modal.confirm(`确认下架“${item.questionCode}”吗？下架后将退回草稿。`) as Promise<unknown>
  );
  if (cancelled) return;
  await takeQuestionOffline(item.revisionId);
  modal.msgSuccess('题目已下架并退回草稿');
  await loadQuestions();
}
async function takeSelectedOffline() {
  const targets = [...selectedPublishedQuestions.value];
  if (!targets.length) return;
  await ElMessageBox.confirm(
    `确认下架选中的 ${targets.length} 道已发布题目吗？下架后将退回草稿。`,
    '批量下架题目',
    { type: 'warning', confirmButtonText: `下架 ${targets.length} 道` }
  );
  offliningQuestions.value = true;
  try {
    const result = await runSequentialBatch(targets, item => takeQuestionOffline(item.revisionId));
    questionSelection.value = result.failed.map(entry => entry.item);
    await loadQuestions();
    if (!result.failed.length) ElMessage.success(`已下架全部 ${result.succeeded.length} 道题目`);
    else ElMessage.warning(`下架完成：成功 ${result.succeeded.length} 道，失败 ${result.failed.length} 道`);
  } finally {
    offliningQuestions.value = false;
  }
}
async function submitSelectedForReview() {
  const targets = [...selectedReviewSubmittableQuestions.value];
  if (!targets.length) return;
  await ElMessageBox.confirm(
    `确认将选中的 ${targets.length} 道草稿或已驳回题目提交审核吗？提交后将进入“内容审核 / 题目审核”，审核完成前不可编辑。`,
    '提交审核',
    { type: 'warning', confirmButtonText: '提交审核' }
  );
  await submitReviewTargets(targets);
}
async function submitAllDraftsForReview() {
  submittingReview.value = true;
  try {
    const targets = await loadAllDrafts();
    if (!targets.length) {
      ElMessage.info('当前范围内没有可提交的草稿题目');
      return;
    }
    const scopeLabel = hasQuestionScope() ? '当前筛选范围' : '整个题库';
    await ElMessageBox.confirm(
      `将提交${scopeLabel}内全部 ${targets.length} 道草稿题。该操作不受当前页限制，确认继续吗？`,
      '提交全部草稿',
      { type: 'warning', confirmButtonText: `提交 ${targets.length} 道` }
    );
    await submitReviewTargets(targets, false);
  } finally {
    submittingReview.value = false;
  }
}
async function submitReviewTargets(targets: QuestionListItem[], manageLoading = true) {
  if (manageLoading) submittingReview.value = true;
  try {
    const result = await runSequentialBatch(targets, item => submitQuestionReview(item.revisionId));
    questionSelection.value = [];
    await loadQuestions();
    if (!result.failed.length) ElMessage.success(`已提交全部 ${result.succeeded.length} 道题目`);
    else ElMessage.warning(`提交完成：成功 ${result.succeeded.length} 道，失败 ${result.failed.length} 道；失败题目仍保留原状态`);
  } finally {
    if (manageLoading) submittingReview.value = false;
  }
}
function questionQueryWithoutStatus() {
  return {
    keyword: filters.keyword.trim() || undefined,
    certificationId: filters.certificationId || undefined,
    syllabusVersionId: scope.syllabus || undefined,
    examSubjectId: scope.subjectId || undefined,
    knowledgePointId: selectedKnowledgeId.value || undefined,
    includeDescendants: true,
    questionType: filters.questionType || undefined,
    difficulty: filters.difficulty || undefined
  };
}
function hasQuestionScope() {
  const query = questionQueryWithoutStatus();
  return Object.values(query).some(value => value !== undefined && value !== '' && value !== true);
}
async function loadAllDrafts() {
  const pageSize = 100;
  const first = (await listQuestions({ ...questionQueryWithoutStatus(), status: 'draft', pageNum: 1, pageSize })).data;
  const targets = [...first.rows];
  for (let pageNum = 2; pageNum <= Math.ceil(first.total / pageSize); pageNum += 1) {
    const page = (await listQuestions({ ...questionQueryWithoutStatus(), status: 'draft', pageNum, pageSize })).data;
    targets.push(...page.rows);
  }
  return [...new Map(targets.map(item => [item.revisionId, item])).values()];
}

function toDirectory(nodes: KnowledgeTreeNodeVO[], subjectId: string): KnowledgeDirectoryNode[] {
  const children = new Map<string, KnowledgeDirectoryNode[]>();
  for (const node of nodes.filter(item => item.examSubjectId === subjectId)) {
    const target = children.get(node.parentId ?? '') ?? [];
    target.push({
      id: node.id,
      label: `${node.syllabusNumber} ${node.syllabusTitle}`.trim(),
      children: children.get(node.id)
    });
    children.set(node.parentId ?? '', target);
  }
  const build = (node: KnowledgeDirectoryNode): KnowledgeDirectoryNode => ({
    ...node,
    children: (children.get(node.id) ?? []).map(build)
  });
  return (children.get('') ?? []).map(build);
}
async function loadKnowledgeTree(syllabusVersionId: string) {
  if (!syllabusVersionId) return;
  if (knowledgeDirectories.value[syllabusVersionId]) return;
  const tree = (await getKnowledgeTree(syllabusVersionId)).data;
  knowledgeDirectories.value[syllabusVersionId] = tree.subjects.map(subject => ({
    id: subject.id,
    label: subject.subjectName,
    children: toDirectory(tree.nodes, subject.id)
  }));
}
async function loadQuestions() {
  loading.value = true;
  try {
    const result = (
      await listQuestions({
        ...questionQueryWithoutStatus(),
        status: filters.status || undefined,
        pageNum: pagination.pageNum,
        pageSize: pagination.pageSize
      })
    ).data;
    rows.value = result.rows;
    total.value = result.total;
  } finally {
    loading.value = false;
  }
}
function handlePageSizeChange() {
  if (pagination.pageNum === 1) {
    loadQuestions();
  } else {
    pagination.pageNum = 1;
  }
}
onMounted(async () => {
  const [syllabusResult] = await Promise.all([listSyllabusVersions({ pageNum: 1, pageSize: 100 }), loadQuestions()]);
  syllabuses.value = syllabusResult.data.rows;
  try {
    await loadCertificationOptions();
  } catch {
    ElMessage.error('考试资格选项加载失败，请稍后重试');
  }
});
</script>

<style scoped lang="scss">
.question-page {
  padding: 10px;
  color: var(--el-text-color-primary);
  background: var(--el-bg-color-page);
  font-size: 13px;
}
.scope-panel,
.directory-panel,
.list-filter-panel {
  width: 100%;
  min-width: 0;
  max-width: 100%;
  box-sizing: border-box;
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  background: var(--el-bg-color);
  box-shadow: 0 4px 14px rgb(15 23 42 / 4%);
}
.scope-panel {
  padding: 12px 16px;
}
.question-search-panel {
  margin-bottom: 10px;
  border-radius: 10px;
}
.question-search-panel :deep(.el-card__header) {
  padding: 12px 16px;
}
.question-search-panel :deep(.el-card__body) {
  padding: 12px 16px;
}
.question-search-heading h3 {
  margin: 0;
  font-size: 16px;
}
.scope-panel.collapsed {
  padding-top: 10px;
  padding-bottom: 10px;
}
.section-heading,
.panel-header,
.list-heading,
.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.section-heading-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  min-width: 0;
}
.section-heading h2 {
  margin: 0;
  font-size: 20px;
}
.panel-header h3,
.list-heading h3 {
  margin: 2px 0 0;
  font-size: 16px;
}
.eyebrow {
  color: var(--el-color-success);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
}
.data-source,
.selection-path,
.list-heading p,
.table-footer {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.filter-form :deep(.el-form-item) {
  margin-bottom: 0;
}
.filter-form :deep(.el-form-item__label) {
  padding-bottom: 3px;
  font-size: 12px;
  line-height: 17px;
}
.filter-actions,
.list-actions {
  display: flex;
  gap: 7px;
  align-items: center;
}
.management-layout {
  display: grid;
  grid-template-columns: clamp(230px, 20vw, 268px) minmax(0, 1fr);
  gap: 10px;
  min-height: 590px;
}
.directory-panel {
  padding: 12px 10px;
}
.panel-header.compact {
  padding: 0 2px 9px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.panel-header.compact h3 {
  font-size: 15px;
}
.directory-empty {
  padding: 42px 8px 24px;
}
.directory-search {
  margin: 10px 0;
}
.directory-tree {
  background: transparent;
  font-size: 12px;
}
.directory-tree :deep(.el-tree-node__content) {
  height: 32px;
  border-radius: 6px;
}
.directory-node {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
  padding-right: 4px;
  overflow: hidden;
}
.directory-node > span:first-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.directory-node-count {
  display: inline-grid;
  min-width: 20px;
  height: 18px;
  place-items: center;
  color: var(--el-color-success);
  font-size: 10px;
  border-radius: 9px;
  background: var(--el-color-success-light-9);
}
.clear-directory {
  margin-top: 8px;
  font-size: 12px;
}
.question-list-area {
  display: grid;
  grid-template-rows: 1fr;
  gap: 10px;
  min-width: 0;
}
.list-filter-panel {
  padding: 12px 14px;
}
.selection-path {
  overflow: hidden;
  max-width: 55%;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.filter-form {
  display: grid;
  grid-template-columns: 1.35fr repeat(3, minmax(120px, 1fr)) auto;
  gap: 10px;
  align-items: end;
  margin-top: 10px;
}
.list-heading {
  margin-bottom: 10px;
}
.list-heading p {
  margin: 3px 0 0;
}
.question-table {
  width: 100%;
  font-size: 12px;
}
.question-table :deep(.el-table__cell) {
  padding: 7px 0;
}
.question-table :deep(.el-button) {
  padding: 2px 4px;
  font-size: 12px;
}
.table-footer {
  margin-top: 10px;
  padding-top: 9px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0 14px;
}
.form-grid--four {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}
.form-grid :deep(.el-input-number) {
  width: 100%;
}
.revision-alert {
  margin-bottom: 14px;
}
.editor-section {
  padding: 14px 16px 4px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
}
.editor-section + .editor-section {
  margin-top: 12px;
}
.editor-section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.editor-section-header h3,
.editor-section-header h4 {
  margin: 0;
}
.editor-section-header h3 {
  font-size: 16px;
}
.editor-section-header h4 {
  font-size: 14px;
}
.editor-section-header p {
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}
.editor-section-header--sub {
  align-items: center;
  margin-top: 4px;
}
.option-editor-list {
  display: grid;
  gap: 8px;
  width: 100%;
  margin-bottom: 14px;
}
.option-editor-row {
  display: grid;
  grid-template-columns: 62px minmax(0, 1fr) 32px;
  gap: 8px;
  align-items: center;
}
.option-editor-row :deep(.el-radio) {
  margin-right: 0;
}
.question-knowledge-editor {
  display: grid;
  gap: 8px;
  width: 100%;
}
.knowledge-binding-workspace {
  display: grid;
  grid-template-columns: minmax(240px, 0.85fr) minmax(340px, 1.4fr);
  min-height: 260px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}
.selected-knowledge-panel,
.editor-knowledge-tree-panel {
  display: grid;
  align-content: start;
  gap: 12px;
  padding: 14px;
}
.selected-knowledge-panel {
  background: var(--el-fill-color-lighter);
  border-right: 1px solid var(--el-border-color-lighter);
}
.knowledge-panel-heading {
  display: flex;
  gap: 12px;
  align-items: end;
  justify-content: space-between;
}
.knowledge-panel-heading h4 {
  margin: 0;
  font-size: 14px;
}
.knowledge-panel-heading p {
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}
.knowledge-panel-heading--tree :deep(.el-input) {
  width: 180px;
}
.question-knowledge-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 94px 32px;
  gap: 8px;
  align-items: center;
}
.knowledge-binding-name {
  min-width: 0;
  font-size: 14px;
  line-height: 20px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.field-hint {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}
.editor-knowledge-tree-panel :deep(.el-tree) {
  max-height: 210px;
  padding-right: 4px;
  overflow: auto;
  background: transparent;
}
.editor-knowledge-tree-node {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
}
.editor-knowledge-tree-node > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.scoring-point-list {
  display: grid;
  gap: 8px;
  margin-bottom: 14px;
}
.scoring-point-row {
  display: grid;
  grid-template-columns: 88px minmax(180px, 1fr) 110px minmax(180px, 0.8fr) 32px;
  gap: 8px;
  align-items: center;
}
.score-value {
  width: 100%;
}
.preview-scoring-points {
  display: grid;
  gap: 8px;
  padding-left: 20px;
}
.preview-scoring-points li {
  line-height: 1.6;
}
.preview-scoring-points span {
  display: block;
  color: var(--el-text-color-secondary);
}
.preview-content {
  margin-top: 18px;
}
.preview-content h4 {
  margin: 16px 0 8px;
}
.preview-content p {
  line-height: 1.7;
  white-space: pre-wrap;
}
.question-options {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.question-options li {
  padding: 8px 10px;
  line-height: 1.6;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
}
.question-options strong {
  margin-right: 6px;
}
.question-images {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 260px), 1fr));
  gap: 12px;
}
.question-images figure {
  min-width: 0;
  margin: 0;
  padding: 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
}
.question-images :deep(.el-image) {
  display: block;
  width: 100%;
  max-height: 320px;
  cursor: zoom-in;
}
.question-images figcaption {
  overflow: hidden;
  margin-top: 7px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.analysis {
  color: var(--el-text-color-secondary);
}
@media (max-width: 1200px) {
  .management-layout {
    grid-template-columns: 238px minmax(0, 1fr);
  }
  .filter-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 960px) {
  .question-page {
    padding: 8px;
  }
  .management-layout {
    grid-template-columns: 1fr;
  }
  .directory-panel {
    max-height: 300px;
    overflow: auto;
  }
  .filter-form,
  .form-grid {
    grid-template-columns: 1fr;
  }
  .scoring-point-row {
    grid-template-columns: 1fr;
    padding: 10px;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 6px;
  }
  .question-knowledge-row {
    grid-template-columns: 1fr;
    padding: 10px;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 6px;
  }
  .knowledge-binding-workspace {
    grid-template-columns: 1fr;
  }
  .selected-knowledge-panel {
    border-right: 0;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }
  .knowledge-panel-heading--tree {
    align-items: flex-start;
    flex-direction: column;
  }
  .knowledge-panel-heading--tree :deep(.el-input) {
    width: 100%;
  }
  .question-knowledge-row > .el-button {
    justify-self: end;
  }
  .scoring-point-row > .el-button {
    justify-self: end;
  }
  .table-footer,
  .list-heading {
    align-items: flex-start;
    flex-direction: column;
  }
}
.search-section {
  margin-top: 12px;
}
.search-section h3 {
  margin: 0;
  font-size: 14px;
}
.question-filter-form {
  display: grid;
  grid-template-columns: 1.35fr 1.05fr repeat(3, minmax(110px, 1fr)) auto;
  gap: 10px;
  align-items: end;
  margin-top: 10px;
}
.syllabus-filter-button {
  width: 100%;
}
.question-filter-form :deep(.el-form-item) {
  margin-bottom: 0;
}
.question-filter-form :deep(.el-form-item__label) {
  padding-bottom: 3px;
  font-size: 12px;
  line-height: 17px;
}
@media (max-width: 1200px) {
  .question-filter-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 960px) {
  .question-filter-form {
    grid-template-columns: 1fr;
  }
}
.question-table-card {
  flex: 0 0 auto;
}
.question-table-card.has-syllabus-directory :deep(.el-card__body) {
  min-height: 712px;
  box-sizing: border-box;
  flex: 0 0 auto;
}
.question-list-layout {
  position: relative;
  min-width: 0;
}
.question-list-layout.has-directory {
  display: grid;
  grid-template-columns: clamp(220px, 18vw, 270px) minmax(0, 1fr);
  gap: 12px;
  align-items: stretch;
  height: 680px;
  min-height: 680px;
  flex: 0 0 680px;
  transition: grid-template-columns 0.24s ease;
}
.question-list-layout.has-directory.is-directory-collapsed {
  grid-template-columns: 56px minmax(0, 1fr);
}
.question-list-main {
  min-width: 0;
}
.question-list-layout.has-directory .question-list-main {
  display: flex;
  height: 680px;
  min-height: 0;
  max-height: 680px;
  flex-direction: column;
  flex: 0 0 680px;
}
.syllabus-directory {
  position: static;
  display: flex;
  width: 100%;
  height: 680px;
  max-width: none;
  min-height: 0;
  box-sizing: border-box;
  flex-direction: column;
  padding: 14px 12px;
  border-radius: 8px;
  box-shadow: none;
}
.syllabus-directory.is-collapsed {
  padding: 0;
}
.syllabus-directory :deep(.directory-tree) {
  min-height: 0;
  max-height: none;
  flex: 1 1 auto;
}
.syllabus-directory :deep(.el-tree-node__content) {
  height: auto;
  min-height: 32px;
  padding-top: 5px;
  padding-bottom: 5px;
  align-items: flex-start;
}
.syllabus-directory :deep(.directory-node) {
  align-items: flex-start;
}
.syllabus-directory :deep(.directory-node-label) {
  overflow: visible;
  text-overflow: clip;
  white-space: normal;
  line-height: 20px;
  overflow-wrap: anywhere;
}
.question-table-shell {
  min-width: 0;
}
.question-table-shell.is-fixed {
  height: 570px;
  min-height: 570px;
  flex: 0 0 570px;
}
.question-table-shell.is-ten-page :deep(.el-table__row) {
  height: 52px;
}
.question-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding-top: 12px;
  margin-top: 10px;
  border-top: 1px solid var(--el-border-color-lighter);
  flex: 0 0 auto;
}
.stem-summary {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rejection-alert :deep(.el-alert__description) {
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.active-scope {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
@media (max-width: 900px) {
  .question-list-layout.has-directory {
    grid-template-columns: 1fr;
    height: auto;
  }
  .syllabus-directory {
    position: static;
    width: 100%;
    height: 420px;
    max-width: none;
    margin-bottom: 12px;
  }
  .syllabus-directory :deep(.directory-tree) {
    min-height: 0;
    max-height: none;
  }
  .question-list-layout.has-directory .question-list-main {
    height: 680px;
  }
}
@media (max-width: 768px) {
  .toolbar-shell {
    align-items: flex-start;
  }
  .toolbar-actions {
    width: 100%;
    margin-left: 0;
  }
}
</style>
