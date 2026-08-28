<template>
  <el-dialog
    v-model="dialogVisible"
    :title="activeConfig.label"
    width="min(1200px, calc(100vw - 32px))"
    top="4vh"
    append-to-body
    destroy-on-close
    class="certmuse-import-dialog"
    @closed="handleClosed"
  >
    <div class="certmuse-catalog-import-page">
      <el-card v-if="!showKnowledgeDiff" shadow="hover" class="workspace-panel">
        <template #header>
          <div class="workspace-header">
            <div>
              <h3>{{ activeConfig.label }}</h3>
            </div>
            <div class="workspace-tags">
              <el-tag v-if="importMockEnabled" type="warning" effect="dark">Mock 模式</el-tag>
              <el-tag type="info" effect="plain">预检契约 V1.0</el-tag>
            </div>
          </div>
        </template>

        <el-alert
          v-if="activeType === 'question'"
          class="context-alert"
          title="题目 ZIP 可同时包含三个科目；服务端会逐题推导科目并预检题型、答案、评分点和知识点关联。"
          type="info"
          :closable="false"
          show-icon
        />

        <el-alert
          v-else-if="activeType === 'textbook'"
          class="context-alert"
          title="先选择已有考纲知识树，再导入与该知识树绑定的教材内容块 JSONL；预检会逐项校验知识点归属。"
          type="info"
          :closable="false"
          show-icon
        />

        <el-alert
          v-else
          class="context-alert"
          title="考纲导入预检仅执行上传与校验；预检结果不会写入正式知识点表。"
          type="info"
          :closable="false"
          show-icon
        />

        <el-alert
          v-if="operationError"
          class="operation-error"
          :title="operationError"
          type="error"
          :closable="false"
          show-icon
        >
          <template #default>
            <div v-if="fieldErrors.length" class="field-errors">
              <span v-for="item in fieldErrors" :key="`${item.field}:${item.code}`">
                {{ item.field }}：{{ item.message }}（{{ item.code }}）
              </span>
            </div>
          </template>
        </el-alert>

        <el-form label-width="112px" class="import-form">
          <el-form-item label="导入类型">
            <el-input :model-value="`${activeConfig.label}（${activeType}）`" disabled class="form-control" />
          </el-form-item>

          <el-form-item v-if="activeType === 'question'" label="资格名称" required>
            <el-select
              v-model="form.questionCertification"
              :disabled="questionCertificationOptions.length === 0 || hasActiveBatch"
              placeholder="请选择资格名称"
              class="form-control"
              @change="handleQuestionCertificationChange"
            >
              <el-option v-for="option in questionCertificationOptions" :key="option" :label="option" :value="option" />
            </el-select>
            <span class="field-tip">请选择题目所属资格名称；ZIP 内的 subject 只用于服务端校验，不会自动生成资格。</span>
          </el-form-item>

          <el-form-item v-if="activeType !== 'question'" label="资格名称" required>
            <el-select
              v-model="form.syllabusCertification"
              :disabled="syllabusCertificationOptions.length === 0 || hasActiveBatch"
              placeholder="请选择资格名称"
              class="form-control"
              @change="handleSyllabusCertificationChange"
            >
              <el-option v-for="option in syllabusCertificationOptions" :key="option" :label="option" :value="option" />
            </el-select>
          </el-form-item>

          <template v-if="activeType === 'textbook'">
            <el-form-item label="导入方式" required>
              <el-radio-group v-model="form.textbookMode" :disabled="hasActiveBatch" @change="handleTextbookModeChange">
                <el-radio value="create">新建教材</el-radio>
                <el-radio value="replace_draft">替换草稿教材</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item v-if="form.textbookMode === 'replace_draft'" label="目标草稿" required>
              <el-select
                v-model="form.textbookDocumentId"
                :disabled="hasActiveBatch"
                class="form-control"
                placeholder="请选择待替换的草稿教材"
                @change="handleTextbookDraftChange"
              >
                <el-option v-for="draft in replaceableDrafts" :key="draft.id" :label="draft.title" :value="draft.id" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="form.textbookMode === 'create'" label="教材名称" required>
              <el-input
                v-model.trim="form.textbookTitle"
                :disabled="hasActiveBatch"
                class="form-control"
                maxlength="500"
                show-word-limit
                placeholder="请输入教材名称"
                @change="resetTextbookRequest"
              />
            </el-form-item>
            <el-form-item label="知识树科目范围">
              <el-tag
                v-for="mapping in subjectMappings"
                :key="mapping.subjectNo"
                class="mapping-tag"
                type="info"
                effect="plain"
              >
                科目{{ mapping.subjectNo }} → {{ subjectLabel(mapping.examSubjectId) }}
              </el-tag>
              <span class="field-tip">由所选知识树自动确定；JSONL 中的 subject_no 与知识点编号必须属于此范围。</span>
            </el-form-item>
          </template>

          <el-form-item label="模板版本" required>
            <el-input :model-value="activeConfig.templateVersion" disabled class="form-control" />
          </el-form-item>

          <el-form-item :label="activeConfig.fileLabel" required>
            <div class="upload-block">
              <el-upload
                v-model:file-list="fileList"
                :auto-upload="false"
                :limit="1"
                :accept="activeConfig.accept"
                :disabled="uploadDisabled"
                :on-change="handleFileChange"
                :on-remove="handleFileRemove"
                :on-exceed="handleFileExceed"
              >
                <el-button type="primary" plain icon="Upload" :disabled="uploadDisabled || hasActiveBatch">
                  选择文件
                </el-button>
                <template #tip>
                  <div class="el-upload__tip">{{ activeConfig.fileTip }}</div>
                </template>
              </el-upload>

              <div v-if="selectedFile" class="file-metadata">
                <div>
                  <span>文件名</span>
                  <strong>{{ selectedFile.name }}</strong>
                </div>
                <div>
                  <span>大小</span>
                  <strong>{{ formatFileSize(selectedFile.size) }}</strong>
                </div>
                <div>
                  <span>浏览器类型</span>
                  <strong>{{ selectedFile.type || '未声明' }}</strong>
                </div>
                <div>
                  <span>前端 SHA-256</span>
                  <strong :class="{ 'hash-value': hashState === 'success' }">{{ hashDisplay }}</strong>
                </div>
                <div class="server-confirmation">
                  <span>服务端确认</span>
                  <strong>{{ serverFileConfirmed ? '已重新校验并创建批次' : '等待上传' }}</strong>
                </div>
              </div>

              <el-alert
                v-if="fileError"
                class="file-error"
                :title="fileError"
                type="error"
                :closable="false"
                show-icon
              />
            </div>
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              icon="VideoPlay"
              :loading="submitting"
              :disabled="startDisabled"
              @click="startPrecheck"
            >
              开始预检
            </el-button>
            <el-button
              type="success"
              icon="CircleCheck"
              :loading="confirming"
              :disabled="!canConfirmImport"
              @click="confirmImport"
            >
              确认导入
            </el-button>
            <el-button v-if="requestRetryable && !hasTerminalBatch" :disabled="submitting" @click="startPrecheck">
              安全重试
            </el-button>
            <span class="action-tip">{{ actionTip }}</span>
          </el-form-item>
        </el-form>
      </el-card>

      <template v-if="showKnowledgeDiff && batch">
        <KnowledgeDiffWorkspace
          ref="knowledgeDiffRef"
          :batch-id="batch.id"
          :syllabus-version-id="form.syllabusVersionId"
          :subject-options="examSubjectOptions.map(item => ({ id: item.id, label: item.label }))"
          :disabled="confirming || currentStatus !== 'waiting_confirm' || knowledgeDiffLocked"
          @summary-change="handleKnowledgeDiffSummary"
          @baseline-changed="knowledgeDiffLocked = true"
          @state-invalid="handleKnowledgeDiffStateInvalid"
        />
        <el-card shadow="hover" class="diff-confirm-panel">
          <el-button
            type="success"
            icon="CircleCheck"
            :loading="confirming"
            :disabled="!canConfirmImport"
            @click="confirmImport"
          >
            正式确认导入
          </el-button>
          <span class="action-tip">{{ actionTip }}</span>
        </el-card>
      </template>

      <el-card shadow="hover" class="progress-panel">
        <template #header>
          <div class="progress-header">
            <div>
              <h3>批次状态与进度</h3>
            </div>
            <el-tag :type="statusTagType" effect="plain">{{ statusLabel }}</el-tag>
          </div>
        </template>

        <div v-if="batch" class="batch-metadata">
          <div>
            <span>批次 ID</span>
            <strong>{{ batch.id }}</strong>
          </div>
          <div>
            <span>创建时间</span>
            <strong>{{ formatDateTime(batch.createTime) }}</strong>
          </div>
          <div>
            <span>当前阶段</span>
            <strong>{{ stageLabel }}</strong>
          </div>
          <div>
            <span>幂等复用</span>
            <strong>{{ batch.reused ? '是，返回原批次' : '否，新建批次' }}</strong>
          </div>
          <div v-if="batch.importType === 'question'" class="derived-subjects">
            <span>服务端识别科目</span>
            <strong>{{ derivedSubjectLabels }}</strong>
          </div>
          <div v-if="batch.importType === 'document_chunk'">
            <span>教材 ID</span>
            <strong>{{ batch.documentId || '等待服务端分配' }}</strong>
          </div>
          <div>
            <span>开始时间</span>
            <strong>{{ formatDateTime(progress?.startedTime) }}</strong>
          </div>
          <div>
            <span>结束时间</span>
            <strong>{{ formatDateTime(progress?.finishedTime) }}</strong>
          </div>
        </div>

        <el-progress
          :percentage="progressPercent"
          :stroke-width="12"
          :status="progressBarStatus"
          :indeterminate="progressIndeterminate"
          :duration="2"
        />
        <p class="progress-tip">{{ progressDescription }}</p>
        <div
          v-if="currentStatus === 'completed' && batch.importType === 'document_chunk'"
          class="completion-actions"
        ></div>
        <el-alert
          v-if="progress?.failureTraceId"
          class="trace-alert"
          :title="`系统处理失败，请联系管理员并提供 Trace ID：${progress.failureTraceId}`"
          type="error"
          :closable="false"
          show-icon
        />
      </el-card>

      <el-row :gutter="16" class="summary-row">
        <el-col v-for="item in summaryItems" :key="item.key" :xs="12" :sm="6">
          <el-card shadow="hover" class="summary-card">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
            <small>{{ item.description }}</small>
          </el-card>
        </el-col>
      </el-row>

      <el-card v-if="textbookPreview" shadow="hover" class="preview-panel">
        <template #header>
          <div class="issues-header">
            <div>
              <h3>教材导入预览</h3>
              <p>{{ textbookPreview.document.title }} · {{ textbookPreview.document.syllabusVersionName }}</p>
            </div>
          </div>
        </template>
        <el-row :gutter="12" class="summary-row preview-summary">
          <el-col v-for="item in previewSummaryItems" :key="item.label" :xs="12" :sm="8" :md="4">
            <div class="preview-count">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </el-col>
        </el-row>
        <el-table
          :data="textbookPreview.chunks.rows"
          v-loading="previewLoading"
          border
          height="440"
          class="data-table preview-table"
        >
          <el-table-column prop="chunkOrder" label="顺序" width="72" />
          <el-table-column prop="heading" label="标题" min-width="130" />
          <el-table-column label="原文章节" min-width="180">
            <template #default="scope">{{ scope.row.headingPath.join(' / ') }}</template>
          </el-table-column>
          <el-table-column prop="contentPreview" label="正文摘要" min-width="240" show-overflow-tooltip />
          <el-table-column label="页码" width="100">
            <template #default="scope">{{ pageRange(scope.row.pageStart, scope.row.pageEnd) }}</template>
          </el-table-column>
          <el-table-column label="知识点" min-width="180">
            <template #default="scope">
              {{ scope.row.knowledgePoints.map(point => `${point.code} ${point.title}`).join('、') || '未关联' }}
            </template>
          </el-table-column>
          <el-table-column prop="issueCount" label="问题" width="72" />
        </el-table>
        <el-pagination
          v-if="previewTotal > 0"
          v-model:current-page="previewQuery.pageNum"
          v-model:page-size="previewQuery.pageSize"
          class="issue-pagination"
          layout="total, sizes, prev, pager, next"
          :page-sizes="[20, 50, 100]"
          :total="previewTotal"
          @current-change="loadTextbookPreview"
          @size-change="handlePreviewPageSizeChange"
        />
      </el-card>

      <el-card shadow="hover" class="issues-panel">
        <template #header>
          <div class="issues-header">
            <div>
              <h3>预检问题</h3>
              <p v-if="activeType === 'question'">warning 按问题条数统计；含 warning 的题目不会导入。</p>
              <p v-else>warning 按问题条数统计；只有 warning 的记录仍属于有效记录。</p>
            </div>
            <div class="issue-filters">
              <el-select
                v-model="issueQuery.severity"
                :disabled="!batch"
                clearable
                placeholder="全部级别"
                @change="searchIssues"
              >
                <el-option label="错误" value="error" />
                <el-option label="警告" value="warning" />
              </el-select>
              <el-input
                v-model.trim="issueQuery.issueCode"
                :disabled="!batch"
                clearable
                placeholder="问题码精确匹配"
                @keyup.enter="searchIssues"
                @clear="searchIssues"
              />
              <el-button :disabled="!batch" :loading="issuesLoading" @click="searchIssues">查询</el-button>
            </div>
          </div>
        </template>

        <el-table :data="issues" v-loading="issuesLoading" class="data-table" border>
          <el-table-column label="行号" prop="lineNo" width="88" align="center" sortable="custom" />
          <el-table-column label="来源键" prop="sourceKey" min-width="170" show-overflow-tooltip />
          <el-table-column label="字段路径" prop="fieldPath" min-width="160" show-overflow-tooltip />
          <el-table-column label="级别" prop="severity" width="96" align="center">
            <template #default="scope">
              <el-tag :type="scope.row.severity === 'error' ? 'danger' : 'warning'" size="small">
                {{ scope.row.severity === 'error' ? '错误' : '警告' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="问题码" prop="issueCode" min-width="220" show-overflow-tooltip />
          <el-table-column label="原因" prop="message" min-width="260" show-overflow-tooltip />
          <el-table-column label="发生时间" prop="createTime" min-width="180">
            <template #default="scope">{{ formatDateTime(scope.row.createTime) }}</template>
          </el-table-column>
        </el-table>

        <el-empty
          v-if="!issuesLoading && issues.length === 0"
          :description="batch ? '当前筛选条件下暂无问题' : '预检尚未开始'"
          :image-size="72"
        />
        <el-pagination
          v-if="issueTotal > 0"
          v-model:current-page="issueQuery.pageNum"
          v-model:page-size="issueQuery.pageSize"
          class="issue-pagination"
          layout="total, sizes, prev, pager, next, jumper"
          :page-sizes="[20, 50, 100]"
          :total="issueTotal"
          @current-change="loadIssues"
          @size-change="handleIssuePageSizeChange"
        />
      </el-card>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import type { UploadFile, UploadUserFile } from 'element-plus';
import { ElMessageBox } from 'element-plus';
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue';
import type {
  ImportBatchVO,
  ImportCurrentStage,
  ImportErrorVO,
  ImportFieldErrorVO,
  ImportIssueQuery,
  ImportIssueVO,
  ImportProgressVO,
  KnowledgeDiffPageVO,
  ImportStatus,
  ImportType,
  ExamSubjectOptionVO,
  SubjectMappingForm,
  SyllabusVersionOptionVO,
  ReplaceableTextbookDraftVO,
  TextbookImportPreviewVO,
  TextbookImportPreviewSummaryVO
} from '@/api/certmuse/catalog/import/types';
import {
  createKnowledgePointImport,
  confirmImport as confirmImportRequest,
  createQuestionImport,
  createTextbookImport,
  getImportContextOptions,
  getQuestionImportContextOptions,
  getTextbookImportContextOptions,
  getTextbookImportPreview,
  getImportProgress,
  importMockEnabled,
  listImportIssues,
  validateImport
} from '@/api/certmuse/catalog/import';
import { onImportProgress } from '@/utils/import-progress-events';
import { pushConnected } from '@/utils/push-state';
import { getHandledRequestError } from '@/utils/request';
import { listQualifications } from '@/api/certmuse/catalog/subject-version';
import KnowledgeDiffWorkspace from './KnowledgeDiffWorkspace.vue';

defineOptions({ name: 'CertmuseImportDialog' });

const props = defineProps<{
  modelValue: boolean;
  importType: ImportType;
  initialSyllabusVersionId?: string;
  initialTextbookId?: string;
}>();
const emit = defineEmits<{
  'update:modelValue': [value: boolean];
  closed: [];
  success: [batch: ImportBatchVO];
}>();

type HashState = 'idle' | 'calculating' | 'success' | 'failed';

interface ImportTypeOption {
  value: ImportType;
  label: string;
  shortLabel: string;
  description: string;
  contractReady: boolean;
  templateVersion: string;
  fileLabel: string;
  accept: string;
  fileTip: string;
}

const knowledgePointTemplateVersion = 'knowledge_point/1.0' as const;
const questionTemplateVersion = 'question-zip/1.0' as const;
const textbookTemplateVersion = 'document_chunk/1.0' as const;
const maxKnowledgePointFileSize = 10 * 1024 * 1024;
const maxQuestionImportArchiveSize = 64 * 1024 * 1024;
const maxTextbookFileSize = 10 * 1024 * 1024;
const pollingInterval = 10_000;
const pushFallbackDelay = 5000;

const importTypeOptions: ImportTypeOption[] = [
  {
    value: 'knowledge_point',
    label: '考纲导入',
    shortLabel: '纲',
    description: '上传考纲 JSONL 并执行预检。',
    contractReady: true,
    templateVersion: knowledgePointTemplateVersion,
    fileLabel: 'JSONL 文件',
    accept: '.jsonl,application/x-ndjson,application/jsonl,application/json,text/plain,application/octet-stream',
    fileTip: '仅支持 UTF-8 无 BOM 的 JSONL，文件最大 10 MiB、单行最大 1 MiB；最终格式与哈希由服务端确认。'
  },
  {
    value: 'question',
    label: '题目导入',
    shortLabel: '题',
    description: '上传包含题目 JSONL 与图片目录的 ZIP 包。',
    contractReady: true,
    templateVersion: questionTemplateVersion,
    fileLabel: '题目 ZIP 包',
    accept: '.zip,application/zip,application/x-zip-compressed',
    fileTip:
      'ZIP 根目录必须含 questions.jsonl，可含 images/；可一次包含 1～3 科题目，最大 64 MiB。服务端会逐题校验科目、知识点和图片。'
  },
  {
    value: 'textbook',
    label: '教材导入',
    shortLabel: '书',
    description: '选择已有考纲知识树，导入并绑定教材内容块 JSONL。',
    contractReady: true,
    templateVersion: textbookTemplateVersion,
    fileLabel: '教材 JSONL 文件',
    accept: '.jsonl,application/x-ndjson,application/jsonl,application/json,text/plain,application/octet-stream',
    fileTip: '仅支持 UTF-8 无 BOM 的分块 JSONL，文件最大 10 MiB；服务端将校验章节、正文和知识点映射。'
  }
];

const statusLabels: Record<ImportStatus, string> = {
  uploaded: '文件已上传',
  parsing: '正在解析',
  validating: '正在校验',
  waiting_confirm: '预检完成',
  importing: '正在导入',
  completed: '导入完成',
  partial_failed: '部分导入失败',
  failed: '系统处理失败',
  cancelled: '已取消'
};

const stageLabels: Record<ImportCurrentStage, string> = {
  read_jsonl: '读取 JSONL',
  validate_schema: '校验字段结构',
  validate_mapping: '校验科目映射',
  validate_tree: '校验考纲',
  finalize_counts: '汇总统计',
  persist_knowledge_tree: '写入考纲',
  persist_questions: '写入草稿题目',
  persist_document_chunks: '写入教材内容块'
};

const activeType = computed(() => props.importType);
const dialogVisible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
});
const form = reactive({
  certificationId: '',
  syllabusVersionId: '',
  syllabusCertification: '',
  questionCertification: '',
  knowledgeSyllabusVersionId: '',
  textbookMode: 'create' as 'create' | 'replace_draft',
  textbookDocumentId: '',
  textbookTitle: ''
});
const subjectMappings = ref<SubjectMappingForm[]>([]);
// 新契约未定义目录端点。真实接口发布前保持为空，禁止使用模拟数据库 ID。
const syllabusOptions = ref<SyllabusVersionOptionVO[]>([]);
const syllabusCreationOptions = ref<Array<{ id: string; label: string }>>([]);
const examSubjectOptions = ref<ExamSubjectOptionVO[]>([]);
const questionKnowledgeSyllabusOptions = ref<SyllabusVersionOptionVO[]>([]);
const replaceableDrafts = ref<ReplaceableTextbookDraftVO[]>([]);
const fileList = ref<UploadUserFile[]>([]);
const fileError = ref('');
const fileHash = ref('');
const hashState = ref<HashState>('idle');
const serverFileConfirmed = ref(false);
const requestId = ref('');
const confirmRequestId = ref('');
const submitting = ref(false);
const confirming = ref(false);
const knowledgeDiffLocked = ref(false);
const knowledgeDiffSummary = ref<KnowledgeDiffPageVO | null>(null);
const knowledgeDiffRef = ref<InstanceType<typeof KnowledgeDiffWorkspace>>();
const requestRetryable = ref(false);
const operationError = ref('');
const fieldErrors = ref<ImportFieldErrorVO[]>([]);
const batch = ref<ImportBatchVO | null>(null);
const progress = ref<ImportProgressVO | null>(null);
const issues = ref<ImportIssueVO[]>([]);
const issueTotal = ref(0);
const issuesLoading = ref(false);
const textbookPreview = ref<TextbookImportPreviewVO | null>(null);
const previewLoading = ref(false);
const previewQuery = reactive({ pageNum: 1, pageSize: 20 });
const issueQuery = reactive<ImportIssueQuery>({
  pageNum: 1,
  pageSize: 20,
  orderByColumn: 'lineNo',
  isAsc: 'asc'
});
let pollingTimer: ReturnType<typeof setTimeout> | undefined;
let pushFallbackTimer: ReturnType<typeof setTimeout> | undefined;
let pushRefreshTimer: ReturnType<typeof setTimeout> | undefined;
let stopImportProgress: (() => void) | undefined;
let successEmitted = false;

const activeConfig = computed(
  () => importTypeOptions.find(item => item.value === activeType.value) ?? importTypeOptions[0]!
);
const availableSyllabusOptions = computed(() =>
  activeType.value === 'knowledge_point'
    ? syllabusOptions.value
    : syllabusOptions.value.filter(item => item.knowledgeTreeAvailable)
);
const syllabusCertificationOptions = computed(() =>
  activeType.value === 'knowledge_point' && !props.initialSyllabusVersionId
    ? syllabusCreationOptions.value.map(option => option.label)
    : [...new Set(availableSyllabusOptions.value.map(option => certificationLabel(option.label)))].filter(Boolean)
);
const visibleSyllabusOptions = computed(() =>
  availableSyllabusOptions.value.filter(option => certificationLabel(option.label) === form.syllabusCertification)
);
const questionCertificationOptions = computed(() =>
  [...new Set((questionKnowledgeSyllabusOptions.value ?? []).map(option => certificationLabel(option.label)))].filter(
    Boolean
  )
);
const visibleQuestionKnowledgeSyllabusOptions = computed(() =>
  (questionKnowledgeSyllabusOptions.value ?? []).filter(
    option => certificationLabel(option.label) === form.questionCertification
  )
);
const selectedFile = computed(() => fileList.value[0]?.raw ?? null);
const hasActiveBatch = computed(() => Boolean(batch.value));
const currentStatus = computed<ImportStatus | null>(() => progress.value?.status ?? batch.value?.status ?? null);
const showKnowledgeDiff = computed(
  () =>
    activeType.value === 'knowledge_point' &&
    currentStatus.value === 'waiting_confirm' &&
    (progress.value?.failedCount ?? 0) === 0 &&
    progress.value?.knowledgeDiffRequired === true
);
const hasTerminalBatch = computed(() =>
  ['waiting_confirm', 'completed', 'partial_failed', 'failed', 'cancelled'].includes(currentStatus.value ?? '')
);
const canConfirmImport = computed(
  () =>
    currentStatus.value === 'waiting_confirm' &&
    (progress.value?.validCount ?? 0) > 0 &&
    (activeType.value === 'question' || (progress.value?.failedCount ?? 0) === 0) &&
    (activeType.value !== 'knowledge_point' ||
      !progress.value?.knowledgeDiffRequired ||
      knowledgeDiffSummary.value?.pendingCount === 0) &&
    !knowledgeDiffLocked.value &&
    !confirming.value
);
const mappingReady = computed(() => validateSubjectMappings(subjectMappings.value) === '');
const questionContextReady = computed(() => Boolean(form.questionCertification && form.knowledgeSyllabusVersionId));
const syllabusContextReady = computed(() => Boolean(
  form.syllabusCertification && (activeType.value !== 'knowledge_point' || form.certificationId) &&
  (activeType.value === 'knowledge_point' || form.syllabusVersionId)
));
const uploadDisabled = computed(() => submitting.value || confirming.value || currentStatus.value === 'importing');
const startDisabled = computed(
  () =>
    submitting.value ||
    hasActiveBatch.value ||
    (activeType.value === 'knowledge_point' && (!syllabusContextReady.value || !mappingReady.value)) ||
    (activeType.value === 'question' && !questionContextReady.value) ||
    (activeType.value === 'textbook' &&
      (!syllabusContextReady.value ||
        !mappingReady.value ||
        (form.textbookMode === 'create' ? !form.textbookTitle : !form.textbookDocumentId))) ||
    !selectedFile.value ||
    Boolean(fileError.value) ||
    hashState.value !== 'success'
);
const actionTip = computed(() => {
  if (activeType.value === 'textbook') {
    if (!form.syllabusCertification) return '请先选择资格名称。';
    if (!form.syllabusVersionId) return '所选资格暂无可用考纲。';
    if (!mappingReady.value) return '正在加载所选知识树的科目范围，请稍候。';
    if (form.textbookMode === 'create' && !form.textbookTitle) return '请填写教材名称。';
    if (form.textbookMode === 'replace_draft' && !form.textbookDocumentId) return '请选择待替换的草稿教材。';
    if (!selectedFile.value) return '请选择教材 JSONL 文件。';
    if (hashState.value === 'calculating') return '正在计算前端 SHA-256。';
    if (currentStatus.value === 'waiting_confirm')
      return (progress.value?.failedCount ?? 0) === 0
        ? '预检完成，请确认导入教材。'
        : '存在错误记录，修正文件后重新导入。';
    if (currentStatus.value === 'completed') return '教材内容块已导入为草稿。';
    if (batch.value) return hasTerminalBatch.value ? statusLabel.value : '批次已创建，正在跟踪服务端状态。';
    return '将按所选知识树校验 JSONL 知识点映射，确认后写入绑定的教材内容块。';
  }
  if (activeType.value === 'question') {
    if (!form.questionCertification) return '请先选择资格名称。';
    if (!form.knowledgeSyllabusVersionId) return '所选资格暂无可用考纲。';
    if (!selectedFile.value) return '请选择题目 ZIP 包。';
    if (hashState.value === 'calculating') return '正在计算前端 SHA-256。';
    if (currentStatus.value === 'waiting_confirm') {
      const validCount = progress.value?.validCount ?? 0;
      const failedCount = progress.value?.failedCount ?? 0;
      return failedCount === 0
        ? '预检完成，请确认导入草稿题目。'
        : `预检完成，${validCount} 道有效题目可确认导入；${failedCount} 道错误或警告题目不会导入。`;
    }
    if (currentStatus.value === 'completed') return '题目已作为草稿导入，可前往题库审核。';
    if (batch.value) return hasTerminalBatch.value ? statusLabel.value : '批次已创建，正在跟踪服务端状态。';
    return '点击后依次创建唯一批次、触发预检并轮询服务端进度。';
  }
  if (!form.syllabusCertification) return '请先选择资格名称。';
  if (!form.certificationId) return '请先选择资格名称。';
  if (!mappingReady.value) return '请完成 subject_no 1～3 到考试科目的显式映射。';
  if (currentStatus.value === 'waiting_confirm') {
    if (!progress.value?.knowledgeDiffRequired) return '预检完成，可直接确认导入新考纲。';
    return knowledgeDiffSummary.value?.pendingCount
      ? `还有 ${knowledgeDiffSummary.value.pendingCount} 条知识点差异待人工确认。`
      : '所有知识点差异已确认，可以正式导入。';
  }
  if (currentStatus.value === 'completed') return '考纲已正式导入。';
  if (!selectedFile.value) return '请选择考纲 JSONL 文件。';
  if (hashState.value === 'calculating') return '正在计算前端 SHA-256。';
  if (batch.value) return hasTerminalBatch.value ? statusLabel.value : '批次已创建，正在跟踪服务端状态。';
  return '点击后依次创建唯一批次、触发预检并轮询服务端进度。';
});
const hashDisplay = computed(() => {
  if (hashState.value === 'calculating') return '计算中…';
  if (hashState.value === 'failed') return '计算失败';
  if (hashState.value === 'success') return fileHash.value;
  return '尚未计算';
});
const progressPercent = computed(() => {
  const value = progress.value?.progressPercent ?? 0;
  return Math.min(100, Math.max(0, Number(value.toFixed(2))));
});
const statusLabel = computed(() => (currentStatus.value ? statusLabels[currentStatus.value] : '尚未创建批次'));
const derivedSubjectLabels = computed(() => {
  const subjects = batch.value?.derivedSubjects;
  if (!subjects?.length) return '等待服务端识别';
  return subjects.map(subject => `${subject.label}（科目${subject.subjectNo}）`).join('、');
});
const stageLabel = computed(() => {
  const stage = progress.value?.currentStage;
  return stage ? stageLabels[stage] : batch.value ? '等待预检' : '—';
});
const statusTagType = computed(() => {
  if (currentStatus.value === 'waiting_confirm') return 'success';
  if (currentStatus.value === 'completed') return 'success';
  if (currentStatus.value === 'failed') return 'danger';
  if (currentStatus.value === 'parsing' || currentStatus.value === 'validating' || currentStatus.value === 'importing')
    return 'warning';
  return 'info';
});
const progressBarStatus = computed(() => {
  if (currentStatus.value === 'waiting_confirm') return 'success';
  if (currentStatus.value === 'completed') return 'success';
  if (currentStatus.value === 'failed') return 'exception';
  return undefined;
});
const progressIndeterminate = computed(
  () => ['parsing', 'validating', 'importing'].includes(currentStatus.value ?? '') && progressPercent.value === 0
);
const progressDescription = computed(() => {
  if (!batch.value) return '创建批次后展示服务端返回的状态、当前阶段和处理进度。';
  if (currentStatus.value === 'waiting_confirm')
    return activeType.value === 'textbook'
      ? '预检已完成。仅当没有错误记录时，才可确认写入草稿教材。'
      : activeType.value === 'question'
        ? '预检已完成。可确认导入有效题目；错误或警告题目不会导入。'
        : '预检已完成。确认导入后，服务端将正式写入考纲。';
  if (currentStatus.value === 'completed')
    return activeType.value === 'textbook'
      ? '教材内容块已导入为草稿。'
      : activeType.value === 'question'
        ? '题目已导入为草稿，可进入题库审核。'
        : '知识点已正式导入，可前往考纲管理页面查看。';
  if (['failed', 'partial_failed', 'cancelled'].includes(currentStatus.value ?? ''))
    return '批次已停止，进度保留最后一次服务端快照。';
  return `服务端阶段：${stageLabel.value}。进度只表示任务执行程度，不代表数据正确率。`;
});
const summaryItems = computed(() => [
  {
    key: 'total',
    label: '总记录数',
    value: progress.value?.totalCount ?? '—',
    description: 'validCount + failedCount'
  },
  {
    key: 'valid',
    label: '有效记录',
    value: progress.value?.validCount ?? '—',
    description: activeType.value === 'question' ? '仅无问题记录' : '含仅有 warning 的记录'
  },
  { key: 'warning', label: '警告问题', value: progress.value?.warningCount ?? '—', description: '按问题条数统计' },
  {
    key: 'failed',
    label: activeType.value === 'question' ? '未导入记录' : '失败记录',
    value: progress.value?.failedCount ?? '—',
    description: activeType.value === 'question' ? '含错误或 warning 的题目' : '按失败行数统计'
  }
]);
const previewTotal = computed(() => textbookPreview.value?.chunks.total ?? 0);
const previewSummaryItems = computed(() => {
  const summary: TextbookImportPreviewSummaryVO | undefined = textbookPreview.value?.summary;
  if (!summary) return [];
  return [
    { label: '有效内容块', value: summary.validChunks },
    { label: '已映射', value: summary.mappedChunks },
    { label: '未映射', value: summary.unmappedChunks },
    { label: '知识点关联', value: summary.knowledgeRelationCount },
    { label: '图片', value: summary.imageCount },
    { label: '警告', value: summary.warningCount }
  ];
});

function initializeKnowledgePointContext() {
  if (!form.syllabusVersionId && !form.certificationId) return;
  // 科目映射由所选考纲的目录接口自动提供，前端不再要求用户手工维护。
  subjectMappings.value = examSubjectOptions.value.map(option => ({
    subjectNo: option.subjectNo,
    examSubjectId: option.id
  }));
}

async function loadImportContext(syllabusVersionId?: string, certificationId?: string) {
  try {
    const options = await getImportContextOptions(syllabusVersionId, certificationId);
    if (activeType.value !== 'knowledge_point') return;
    syllabusOptions.value = options.syllabusVersions;
    syllabusCreationOptions.value = options.certifications ?? [];
    if (!syllabusVersionId && !certificationId) {
      const qualificationPage = await listQualifications({ status: '0', pageNum: 1, pageSize: 100 });
      const syllabusQualificationIds = new Set(
        syllabusOptions.value.map(option => option.certificationId).filter((id): id is string => Boolean(id))
      );
      syllabusCreationOptions.value = qualificationPage.data.rows
        .filter(qualification =>
          (qualification.versions?.length ?? 0) === 0 && !syllabusQualificationIds.has(qualification.id)
        )
        .map(qualification => ({ id: qualification.id, label: qualification.certificationName }));
    }
    examSubjectOptions.value = options.examSubjects;

    if (form.syllabusVersionId) {
      const selected = syllabusOptions.value.find(item => item.id === form.syllabusVersionId);
      form.syllabusCertification = certificationLabel(selected?.label ?? '');
      form.certificationId = selected?.certificationId ?? '';
    }
    initializeKnowledgePointContext();
  } catch (error) {
    handleOperationError(error);
  }
}

async function loadQuestionImportContext() {
  try {
    const options = await getQuestionImportContextOptions();
    if (activeType.value !== 'question') return;
    questionKnowledgeSyllabusOptions.value = options.knowledgeSyllabusVersions ?? [];
    if (!questionCertificationOptions.value.includes(form.questionCertification)) {
      form.questionCertification = questionCertificationOptions.value[0] ?? '';
    }
    form.knowledgeSyllabusVersionId = visibleQuestionKnowledgeSyllabusOptions.value[0]?.id ?? '';
  } catch (error) {
    handleOperationError(error);
  }
}

async function loadTextbookImportContext(syllabusVersionId?: string) {
  try {
    const options = await getTextbookImportContextOptions(syllabusVersionId);
    if (activeType.value !== 'textbook') return;
    syllabusOptions.value = options.syllabusVersions;
    examSubjectOptions.value = options.examSubjects;
    replaceableDrafts.value = options.replaceableDrafts ?? [];
    subjectMappings.value = options.defaultSubjectMappings ?? [];
    if (form.syllabusVersionId) {
      form.syllabusCertification = certificationLabel(
        syllabusOptions.value.find(item => item.id === form.syllabusVersionId)?.label ?? ''
      );
    }
  } catch (error) {
    handleOperationError(error);
  }
}

function handleQuestionCertificationChange() {
  form.knowledgeSyllabusVersionId = visibleQuestionKnowledgeSyllabusOptions.value[0]?.id ?? '';
  resetRequestIdentity();
}

async function handleSyllabusCertificationChange() {
  if (activeType.value === 'knowledge_point' && !props.initialSyllabusVersionId) {
    const selected = syllabusCreationOptions.value.find(option => option.label === form.syllabusCertification);
    form.certificationId = selected?.id ?? '';
    form.syllabusVersionId = '';
    examSubjectOptions.value = [];
    subjectMappings.value = [];
    resetRequestIdentity();
    if (form.certificationId) await loadImportContext(undefined, form.certificationId);
    return;
  }
  form.syllabusVersionId = visibleSyllabusOptions.value[0]?.id ?? '';
  form.certificationId = visibleSyllabusOptions.value[0]?.certificationId ?? '';
  examSubjectOptions.value = [];
  subjectMappings.value = [];
  replaceableDrafts.value = [];
  form.textbookDocumentId = '';
  resetRequestIdentity();
  if (!form.syllabusVersionId) return;
  if (activeType.value === 'textbook') await loadTextbookImportContext(form.syllabusVersionId);
  else await loadImportContext(form.syllabusVersionId);
}

function certificationLabel(syllabusLabel: string) {
  return syllabusLabel.split('·')[0]?.trim() || syllabusLabel;
}

function handleTextbookModeChange() {
  form.textbookDocumentId = '';
  resetTextbookRequest();
}

function handleTextbookDraftChange(value: string) {
  const draft = replaceableDrafts.value.find(item => item.id === value);
  if (draft) {
    form.textbookTitle = draft.title;
  }
  resetTextbookRequest();
}

function resetTextbookRequest() {
  stopPolling();
  resetRuntimeState();
  resetRequestIdentity();
}

function subjectLabel(examSubjectId: string) {
  return examSubjectOptions.value.find(item => item.id === examSubjectId)?.label ?? examSubjectId;
}

async function handleFileChange(file: UploadFile) {
  stopPolling();
  resetRuntimeState();
  resetRequestIdentity();
  serverFileConfirmed.value = false;
  fileHash.value = '';
  hashState.value = 'idle';
  fileError.value = await validateFile(file);
  if (fileError.value || !file.raw) return;

  hashState.value = 'calculating';
  try {
    const digest = await crypto.subtle.digest('SHA-256', await file.raw.arrayBuffer());
    fileHash.value = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('');
    hashState.value = 'success';
  } catch {
    hashState.value = 'failed';
    fileError.value = '前端 SHA-256 计算失败，请重新选择文件。';
  }
}

async function validateFile(file: UploadFile) {
  if (!file.raw || file.raw.size === 0) return '文件不能为空。';
  if (activeType.value === 'question') {
    if (!file.name.toLowerCase().endsWith('.zip')) return '题目导入只支持 .zip 文件。';
    if (file.raw.size > maxQuestionImportArchiveSize) return 'ZIP 包超过 64 MiB 上限，请重新选择。';
    const prefix = new Uint8Array(await file.raw.slice(0, 4).arrayBuffer());
    if (prefix.length < 4 || prefix[0] !== 0x50 || prefix[1] !== 0x4b) return '文件不是有效的 ZIP 包。';
    return '';
  }

  if (!file.name.toLowerCase().endsWith('.jsonl'))
    return `${activeType.value === 'textbook' ? '教材' : '考纲'}导入只支持 .jsonl 文件。`;
  if (file.raw.size > (activeType.value === 'textbook' ? maxTextbookFileSize : maxKnowledgePointFileSize))
    return '文件超过 10 MiB 上限，请重新选择。';

  const prefix = new Uint8Array(await file.raw.slice(0, 3).arrayBuffer());
  if (prefix.length === 3 && prefix[0] === 0xef && prefix[1] === 0xbb && prefix[2] === 0xbf) {
    return '文件包含 UTF-8 BOM，请移除 BOM 后重新选择。';
  }
  return '';
}

function validateSubjectMappings(mappings: SubjectMappingForm[]) {
  if (mappings.length < 1 || mappings.length > 3) return '科目映射数量必须为 1～3。';
  const subjectNos = new Set<number>();
  const subjectIds = new Set<string>();
  for (const mapping of mappings) {
    if (![1, 2, 3].includes(mapping.subjectNo)) return 'subjectNo 只能为 1、2、3。';
    if (!/^[1-9][0-9]{0,18}$/.test(mapping.examSubjectId)) return 'examSubjectId 必须是十进制正整数文本。';
    if (subjectNos.has(mapping.subjectNo) || subjectIds.has(mapping.examSubjectId))
      return '科目编号和目标科目均不得重复。';
    subjectNos.add(mapping.subjectNo);
    subjectIds.add(mapping.examSubjectId);
  }
  return '';
}

async function startPrecheck() {
  if (submitting.value) return;
  if (!batch.value) {
    try {
      await ElMessageBox.confirm(
        `将上传“${selectedFile.value?.name ?? activeConfig.value.label}”并开始预检。预检不会直接写入正式数据，确定继续吗？`,
        '确认上传并开始预检',
        { type: 'warning', confirmButtonText: '确认上传', cancelButtonText: '取消' }
      );
    } catch {
      return;
    }
  }
  clearOperationError();
  submitting.value = true;

  try {
    if (!batch.value) {
      const file = selectedFile.value;
      if (!file) throw new Error(`请选择${activeConfig.value.label}。`);
      if (!requestId.value) requestId.value = crypto.randomUUID();

      const createResponse =
        activeType.value === 'textbook'
          ? await createTextbookImport({
              file,
              importType: 'document_chunk',
              templateVersion: textbookTemplateVersion,
              mode: form.textbookMode,
              syllabusVersionId: form.syllabusVersionId,
              subjectMappings: subjectMappings.value.toSorted((left, right) => left.subjectNo - right.subjectNo),
              title: form.textbookMode === 'create' ? form.textbookTitle : undefined,
              documentId: form.textbookMode === 'replace_draft' ? form.textbookDocumentId : undefined,
              requestId: requestId.value
            })
          : activeType.value === 'question'
            ? await createQuestionImport({
                file,
                importType: 'question',
                knowledgeSyllabusVersionId: form.knowledgeSyllabusVersionId,
                templateVersion: questionTemplateVersion,
                requestId: requestId.value
              })
            : await createKnowledgePointImport({
                file,
                importType: 'knowledge_point',
                certificationId: form.certificationId,
                syllabusVersionId: form.syllabusVersionId,
                subjectMappings: subjectMappings.value.toSorted((left, right) => left.subjectNo - right.subjectNo),
                templateVersion: knowledgePointTemplateVersion,
                requestId: requestId.value
              });
      batch.value = requireResponseData(createResponse.data, '创建批次响应缺少 data。');
      if (activeType.value === 'knowledge_point' && batch.value.syllabusVersionId) {
        form.syllabusVersionId = batch.value.syllabusVersionId;
      }
      serverFileConfirmed.value = true;
    }

    const validateResponse = await validateImport(batch.value.id);
    const accepted = requireResponseData(validateResponse.data, '触发预检响应缺少 data。');
    requestRetryable.value = false;
    await refreshProgress();
    if (!['waiting_confirm', 'failed', 'partial_failed', 'cancelled'].includes(accepted.status)) schedulePolling();
  } catch (error) {
    handleOperationError(error);
  } finally {
    submitting.value = false;
  }
}

async function confirmImport() {
  if (!batch.value || !canConfirmImport.value) return;
  try {
    await ElMessageBox.confirm(
      activeType.value === 'textbook'
        ? form.textbookMode === 'replace_draft'
          ? `本次将整体替换草稿教材中的 ${progress.value?.validCount ?? 0} 个有效内容块。替换失败会保留原内容，确定继续吗？`
          : `本次将创建草稿教材并写入 ${progress.value?.validCount ?? 0} 个有效内容块，确定继续吗？`
        : activeType.value === 'question'
          ? `本次将创建 ${progress.value?.validCount ?? 0} 道草稿题目。确认后不会覆盖既有题目，确定继续吗？`
          : knowledgeDiffSummary.value
            ? `本次将新增 ${knowledgeDiffSummary.value.addCount} 个、修改 ${knowledgeDiffSummary.value.updateCount} 个、移动 ${knowledgeDiffSummary.value.moveCount} 个、删除 ${knowledgeDiffSummary.value.deleteCount} 个知识点，预计影响 ${knowledgeDiffSummary.value.affectedQuestionCount} 道题，其中 ${knowledgeDiffSummary.value.offlineQuestionCount} 道已发布题目可能转为草稿。是否继续？`
            : `本次将导入 ${progress.value?.validCount ?? 0} 条有效知识点记录，确定继续吗？`,
      activeType.value === 'textbook'
        ? '确认导入教材'
        : activeType.value === 'question'
          ? '确认导入草稿题目'
          : '确认导入考纲',
      { type: 'warning', confirmButtonText: '确认导入', cancelButtonText: '取消' }
    );
  } catch {
    return;
  }

  clearOperationError();
  confirming.value = true;
  try {
    if (!confirmRequestId.value) confirmRequestId.value = crypto.randomUUID();
    const response = await confirmImportRequest(batch.value.id, confirmRequestId.value);
    const accepted = requireResponseData(response.data, '确认导入响应缺少 data。');
    progress.value = {
      ...(progress.value ?? {
        id: batch.value.id,
        progressPercent: 100,
        totalCount: 0,
        validCount: 0,
        warningCount: 0,
        failedCount: 0,
        startedTime: null,
        finishedTime: null,
        failureTraceId: null,
        knowledgeDiffRequired: false
      }),
      status: accepted.status,
      currentStage: accepted.currentStage
    };
    if (accepted.status !== 'completed') schedulePolling();
    confirmRequestId.value = '';
  } catch (error) {
    handleOperationError(error);
  } finally {
    confirming.value = false;
  }
}

function handleKnowledgeDiffSummary(value: KnowledgeDiffPageVO) {
  knowledgeDiffSummary.value = value;
}

async function handleKnowledgeDiffStateInvalid() {
  knowledgeDiffLocked.value = true;
  await refreshProgress();
}

async function refreshProgress() {
  if (!batch.value) return;
  try {
    const response = await getImportProgress(batch.value.id);
    progress.value = requireResponseData(response.data, '进度响应缺少 data。');
    if (['waiting_confirm', 'completed', 'partial_failed', 'failed', 'cancelled'].includes(progress.value.status)) {
      stopPolling();
      await Promise.all([loadIssues(), loadTextbookPreview()]);
    }
  } catch (error) {
    stopPolling();
    handleOperationError(error);
  }
}

async function loadTextbookPreview() {
  if (!batch.value || batch.value.importType !== 'document_chunk') return;
  previewLoading.value = true;
  try {
    const response = await getTextbookImportPreview(batch.value.id, previewQuery);
    textbookPreview.value = requireResponseData(response.data, '教材预览响应缺少 data。');
  } catch (error) {
    handleOperationError(error);
  } finally {
    previewLoading.value = false;
  }
}

function handlePreviewPageSizeChange() {
  previewQuery.pageNum = 1;
  void loadTextbookPreview();
}

function pageRange(start: number | null, end: number | null) {
  if (!start && !end) return '—';
  return start === end || !end ? String(start) : `${start}-${end}`;
}

function schedulePolling() {
  stopPolling();
  if (hasTerminalBatch.value || !batch.value || pushConnected.value) return;
  pollingTimer = setTimeout(async () => {
    await refreshProgress();
    if (!hasTerminalBatch.value) schedulePolling();
  }, pollingInterval);
}

function stopPolling() {
  if (pollingTimer) clearTimeout(pollingTimer);
  pollingTimer = undefined;
}

function subscribeImportProgress(batchId?: string) {
  stopImportProgress?.();
  stopImportProgress = undefined;
  if (!batchId) return;
  stopImportProgress = onImportProgress(batchId, () => {
    if (pushRefreshTimer) clearTimeout(pushRefreshTimer);
    pushRefreshTimer = setTimeout(() => void refreshProgress(), 300);
  });
}

async function loadIssues() {
  if (!batch.value) return;
  issuesLoading.value = true;
  try {
    const response = await listImportIssues(batch.value.id, issueQuery);
    const page = requireResponseData(response.data, '问题分页响应缺少 data。');
    issues.value = page.rows;
    issueTotal.value = page.total;
  } catch (error) {
    handleOperationError(error);
  } finally {
    issuesLoading.value = false;
  }
}

function searchIssues() {
  issueQuery.pageNum = 1;
  void loadIssues();
}

function handleIssuePageSizeChange() {
  issueQuery.pageNum = 1;
  void loadIssues();
}

function handleOperationError(error: unknown) {
  const handledError = getHandledRequestError<ImportErrorVO>(error);
  const httpErrorData = (error as { response?: { data?: { data?: ImportErrorVO } } } | undefined)?.response?.data?.data;
  const detail = handledError?.responseData ?? httpErrorData;

  requestRetryable.value = Boolean(detail?.retryable);
  fieldErrors.value = detail?.fieldErrors ?? [];
  if (detail?.traceId) {
    operationError.value = `系统处理失败，请联系管理员并提供 Trace ID：${detail.traceId}`;
  } else if (detail?.errorCode) {
    operationError.value = `${error instanceof Error ? error.message : '导入请求失败'}（${detail.errorCode}）`;
  } else {
    operationError.value = error instanceof Error ? error.message : '导入请求失败，请稍后重试。';
  }
}

function clearOperationError() {
  operationError.value = '';
  fieldErrors.value = [];
  requestRetryable.value = false;
}

function handleFileExceed() {
  fileError.value = `一次只能选择一个${activeConfig.value.fileLabel}。`;
}

function handleFileRemove() {
  stopPolling();
  resetRuntimeState();
  resetFileState();
}

function resetFileState() {
  fileList.value = [];
  fileError.value = '';
  fileHash.value = '';
  hashState.value = 'idle';
  serverFileConfirmed.value = false;
  resetRequestIdentity();
}

function resetRequestIdentity() {
  requestId.value = '';
  confirmRequestId.value = '';
  clearOperationError();
}

function resetRuntimeState() {
  batch.value = null;
  progress.value = null;
  issues.value = [];
  issueTotal.value = 0;
  textbookPreview.value = null;
  knowledgeDiffSummary.value = null;
  knowledgeDiffLocked.value = false;
  previewQuery.pageNum = 1;
  issueQuery.pageNum = 1;
  clearOperationError();
}

function resetDialogState() {
  stopPolling();
  resetRuntimeState();
  resetFileState();
  syllabusOptions.value = [];
  syllabusCreationOptions.value = [];
  examSubjectOptions.value = [];
  questionKnowledgeSyllabusOptions.value = [];
  subjectMappings.value = [];
  replaceableDrafts.value = [];
  form.syllabusVersionId = props.initialSyllabusVersionId ?? '';
  form.certificationId = '';
  form.syllabusCertification = '';
  form.questionCertification = '';
  form.knowledgeSyllabusVersionId = props.initialSyllabusVersionId ?? '';
  form.textbookMode = 'create';
  form.textbookDocumentId = '';
  form.textbookTitle = '';
  successEmitted = false;
}

async function initializeDialog() {
  resetDialogState();
  if (activeType.value === 'question') {
    await loadQuestionImportContext();
    const selected = questionKnowledgeSyllabusOptions.value.find(item => item.id === props.initialSyllabusVersionId);
    if (selected) {
      form.questionCertification = certificationLabel(selected.label);
      form.knowledgeSyllabusVersionId = selected.id;
    }
    return;
  }
  if (activeType.value === 'textbook') {
    await loadTextbookImportContext(props.initialSyllabusVersionId);
    if (!syllabusOptions.value.some(item => item.id === form.syllabusVersionId)) form.syllabusVersionId = '';
    const draft = replaceableDrafts.value.find(item => item.id === props.initialTextbookId);
    if (draft) {
      form.textbookMode = 'replace_draft';
      form.textbookDocumentId = draft.id;
    }
    return;
  }
  await loadImportContext(props.initialSyllabusVersionId);
  if (!syllabusOptions.value.some(item => item.id === form.syllabusVersionId)) form.syllabusVersionId = '';
}

function handleClosed() {
  emit('closed');
  resetDialogState();
}

watch(
  () => props.modelValue,
  value => {
    if (value) void initializeDialog();
    else stopPolling();
  },
  { immediate: true }
);

watch(currentStatus, status => {
  if (status === 'completed' && batch.value && !successEmitted) {
    successEmitted = true;
    emit('success', batch.value);
    dialogVisible.value = false;
  }
});

watch(
  () => batch.value?.id,
  batchId => subscribeImportProgress(batchId)
);

watch(pushConnected, connected => {
  if (pushFallbackTimer) clearTimeout(pushFallbackTimer);
  pushFallbackTimer = undefined;
  if (connected) {
    stopPolling();
    if (batch.value && !hasTerminalBatch.value) void refreshProgress();
    return;
  }
  if (batch.value && !hasTerminalBatch.value) {
    pushFallbackTimer = setTimeout(schedulePolling, pushFallbackDelay);
  }
});

function requireResponseData<T>(data: T | undefined, message: string): T {
  if (data === undefined) throw new Error(message);
  return data;
}

function formatFileSize(size: number) {
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KiB`;
  return `${(size / 1024 / 1024).toFixed(2)} MiB`;
}

function formatDateTime(value?: string | null) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false });
}

onBeforeUnmount(() => {
  stopPolling();
  stopImportProgress?.();
  if (pushFallbackTimer) clearTimeout(pushFallbackTimer);
  if (pushRefreshTimer) clearTimeout(pushRefreshTimer);
});
</script>

<style scoped lang="scss">
:global(.certmuse-import-dialog .el-dialog__body) {
  max-height: calc(92vh - 120px);
  padding: 0;
  overflow-y: auto;
}

.certmuse-catalog-import-page {
  display: grid;
  gap: 10px;
  min-height: 0;
  color: var(--el-text-color-primary);
  background: var(--el-bg-color-page);
  font-size: 13px;
}

.workspace-header,
.issues-header,
.progress-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;

  h3,
  p {
    margin: 0;
  }

  h3 {
    font-size: 16px;
  }

  p {
    margin-top: 6px;
    color: var(--el-text-color-secondary);
    font-size: 13px;
  }
}

.workspace-header h3 {
  font-size: 20px;
}

.panel-kicker {
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.workspace-tags {
  display: flex;
  gap: 8px;
  align-items: center;
}

.import-type-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 10px;
}

.import-type-card {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 12px;
  align-items: center;
  min-height: 104px;
  padding: 16px;
  color: inherit;
  text-align: left;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color);
  border-radius: 10px;
  cursor: pointer;
  transition: 0.2s ease;

  &:hover,
  &.active {
    border-color: var(--el-color-primary);
    box-shadow: 0 5px 16px rgb(64 158 255 / 12%);
    transform: translateY(-1px);
  }

  &.active {
    background: var(--el-color-primary-light-9);
  }
}

.type-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  color: var(--el-color-primary);
  font-size: 18px;
  font-weight: 700;
  background: var(--el-color-primary-light-8);
  border-radius: 10px;
}

.type-question .type-icon {
  color: var(--el-color-warning);
  background: var(--el-color-warning-light-8);
}

.type-textbook .type-icon {
  color: var(--el-color-success);
  background: var(--el-color-success-light-8);
}

.type-content {
  display: grid;
  gap: 7px;

  strong {
    font-size: 16px;
  }

  small {
    color: var(--el-text-color-secondary);
    line-height: 1.5;
  }
}

.context-alert,
.operation-error {
  margin-bottom: 10px;
}

.alert-content,
.field-errors {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
}

.field-errors {
  flex-direction: column;
  margin-top: 6px;
}

.import-form {
  max-width: 980px;
}

.form-control {
  width: 400px;
}

.field-tip,
.action-tip {
  margin-left: 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.mapping-tag {
  margin-right: 8px;
}

.preview-summary {
  margin-bottom: 10px;
}

.preview-table {
  height: 440px;
}

.preview-count {
  display: grid;
  gap: 5px;
  padding: 10px;
  background: var(--el-fill-color-light);
  border-radius: 6px;

  span {
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }
  strong {
    font-size: 20px;
  }
}

.upload-block {
  width: min(100%, 720px);
}

.file-metadata,
.batch-metadata {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 18px;
  margin-top: 12px;
  padding: 14px;
  background: var(--el-fill-color-light);
  border-radius: 8px;

  div {
    display: grid;
    gap: 3px;
    min-width: 0;
  }

  span {
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }

  strong {
    overflow: hidden;
    font-size: 13px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.server-confirmation {
  grid-column: 1 / -1;
}

.hash-value {
  font-family: monospace;
}

.file-error,
.trace-alert {
  margin-top: 10px;
}

.batch-metadata {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin: 0 0 16px;
}

.progress-tip {
  margin: 10px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.completion-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.diff-confirm-panel :deep(.el-card__body) {
  display: flex;
  align-items: center;
  gap: 8px;
}

.summary-card {
  display: grid;
  gap: 6px;

  span,
  small {
    color: var(--el-text-color-secondary);
  }

  strong {
    font-size: 28px;
    line-height: 1;
  }
}

.issues-header {
  align-items: center;
}

.issue-filters {
  display: grid;
  grid-template-columns: 130px 210px auto;
  gap: 8px;
}

.issue-pagination {
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 1080px) {
  .import-type-grid {
    grid-template-columns: 1fr;
  }

  .batch-metadata {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .workspace-header,
  .issues-header,
  .progress-header {
    align-items: stretch;
    flex-direction: column;
  }

  .form-control,
  .issue-filters {
    width: 100%;
  }

  .issue-filters,
  .file-metadata,
  .batch-metadata {
    grid-template-columns: 1fr;
  }

  .field-tip,
  .action-tip {
    display: block;
    margin: 6px 0 0;
  }
}
</style>
