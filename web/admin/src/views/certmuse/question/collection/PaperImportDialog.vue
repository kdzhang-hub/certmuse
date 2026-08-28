<template>
  <el-dialog
    v-model="dialogVisible"
    title="导入试卷"
    width="min(860px, calc(100vw - 32px))"
    destroy-on-close
    @closed="reset"
  >
    <el-alert
      title="导入会先全量预检。出现任意 warning 或 error 时都不能确认，确认阶段会原子创建全部题目草稿与一个题集草稿。"
      type="info"
      :closable="false"
      show-icon
    />
    <el-alert v-if="operationError" class="mt-3" :title="operationError" type="error" :closable="false" show-icon />

    <el-form class="mt-4" label-width="108px">
      <el-form-item label="试卷名称" required>
        <el-input
          v-model.trim="form.collectionName"
          :disabled="hasBatch"
          maxlength="200"
          show-word-limit
          placeholder="请输入试卷名称"
          @change="resetIdentity"
        />
      </el-form-item>
      <el-form-item label="题集类型" required>
        <el-select v-model="form.collectionType" :disabled="hasBatch" class="form-control" @change="resetIdentity">
          <el-option label="首次诊断" value="FIRST_DIAGNOSTIC" />
          <el-option label="模拟试卷" value="SIMULATION" />
          <el-option label="历年真题" value="PAST_PAPER" />
        </el-select>
      </el-form-item>
      <el-form-item label="考试时长" required>
        <el-input-number
          v-model="form.durationMinutes"
          :disabled="hasBatch"
          :min="1"
          :max="1440"
          controls-position="right"
        />
        <span class="ml-2 text-muted">分钟</span>
      </el-form-item>
      <el-form-item label="考试资格" required>
        <el-select
          v-model="form.certificationId"
          :disabled="hasBatch || qualificationsLoading"
          class="form-control"
          placeholder="请选择考试资格"
          @change="resetIdentity"
        >
          <el-option v-for="item in qualifications" :key="item.id" :label="item.certificationName" :value="item.id" />
        </el-select>
      </el-form-item>
      <template v-if="form.collectionType === 'PAST_PAPER'">
        <el-form-item label="考试年份" required>
          <el-input-number
            v-model="form.examYear"
            :disabled="hasBatch"
            :min="1990"
            :max="2100"
            controls-position="right"
            @change="resetIdentity"
          />
        </el-form-item>
        <el-form-item label="考试批次" required>
          <el-select
            v-model="form.examMonth"
            :disabled="hasBatch"
            class="form-control"
            placeholder="请选择考试批次"
            @change="resetIdentity"
          >
            <el-option label="5 月" :value="5" />
            <el-option label="11 月" :value="11" />
          </el-select>
        </el-form-item>
        <el-form-item label="卷型编码" required>
          <el-input
            v-model.trim="form.paperTypeCode"
            :disabled="hasBatch"
            maxlength="50"
            placeholder="例如：AM"
            @change="resetIdentity"
          />
        </el-form-item>
        <el-form-item label="卷型名称" required>
          <el-input
            v-model.trim="form.paperTypeName"
            :disabled="hasBatch"
            maxlength="100"
            placeholder="例如：综合知识"
            @change="resetIdentity"
          />
        </el-form-item>
      </template>
      <el-form-item label="题目 ZIP" required>
        <el-upload
          v-model:file-list="fileList"
          :auto-upload="false"
          :limit="maxArchiveFiles"
          multiple
          accept=".zip,application/zip,application/x-zip-compressed"
          :disabled="hasBatch"
          :on-change="handleFileChange"
          :on-remove="resetFile"
          :on-exceed="handleFileExceed"
        >
          <el-button type="primary" plain :disabled="hasBatch">选择 ZIP 文件</el-button>
          <template #tip>
            <div class="el-upload__tip">可选 1～3 个 ZIP，或选择一个题集文件夹；合计最大 64 MiB。</div>
          </template>
        </el-upload>
        <input ref="folderInput" class="folder-input" type="file" multiple webkitdirectory @change="handleFolderChange" />
        <el-button plain :disabled="hasBatch" @click="pickFolder">选择题集文件夹</el-button>
        <div class="field-tip">文件夹内只能直接包含 1～3 个题目 ZIP，不要选择包含多个年份的总目录。</div>
        <div v-if="fileError" class="error-tip">{{ fileError }}</div>
        <div v-else-if="hashState === 'calculating'" class="field-tip">正在计算 SHA-256…</div>
        <div v-else-if="fileHash" class="field-tip">SHA-256：{{ fileHash.slice(0, 16) }}…</div>
      </el-form-item>
    </el-form>

    <section class="batch-panel">
      <header>
        <h4>批次状态与进度</h4>
        <el-tag effect="plain" :type="batch ? undefined : 'info'">{{ batch ? statusLabel : '尚未创建批次' }}</el-tag>
      </header>
      <el-progress
        :percentage="progress?.progressPercent ?? 0"
        :status="progress?.status === 'failed' ? 'exception' : undefined"
      />
      <p>{{ statusTip }}</p>
      <div v-if="batch" class="resolved-context">
        <span>服务端识别考纲：</span>
        <b>{{ progress?.syllabusVersionName || '等待预检确定' }}</b>
      </div>
    </section>

    <section class="summary-grid" aria-label="预检统计">
      <article>
        <span>总记录数</span>
        <strong>{{ progress?.totalCount ?? '—' }}</strong>
        <small>validCount + failedCount</small>
      </article>
      <article>
        <span>有效记录</span>
        <strong>{{ progress?.validCount ?? '—' }}</strong>
        <small>无 warning 或 error 的题目</small>
      </article>
      <article>
        <span>警告问题</span>
        <strong>{{ progress?.warningCount ?? '—' }}</strong>
        <small>存在 warning 时不能确认</small>
      </article>
      <article>
        <span>错误题目</span>
        <strong>{{ progress?.failedCount ?? '—' }}</strong>
        <small>存在 error 时不能确认</small>
      </article>
    </section>

    <section class="issues-panel">
      <header class="issues-header">
        <div>
          <h4>预检问题</h4>
          <p>warning 和 error 都会阻断整份试卷导入；请修正 ZIP 后重新创建批次。</p>
        </div>
        <div class="issue-filters">
          <el-select v-model="issueQuery.severity" clearable placeholder="全部级别" @change="searchIssues">
            <el-option label="错误" value="error" />
            <el-option label="警告" value="warning" />
          </el-select>
          <el-input
            v-model.trim="issueQuery.issueCode"
            clearable
            placeholder="问题码精确匹配"
            @keyup.enter="searchIssues"
          />
          <el-button type="primary" :disabled="!batch" @click="searchIssues">查询</el-button>
        </div>
      </header>
      <template v-if="batch">
        <el-table v-if="issuesLoading || issues.length" v-loading="issuesLoading" :data="issues" border size="small">
          <el-table-column prop="lineNo" label="行号" width="72" sortable />
          <el-table-column prop="sourceKey" label="来源键" min-width="120" />
          <el-table-column prop="fieldPath" label="字段路径" min-width="150" />
          <el-table-column label="级别" width="84">
            <template #default="scope">
              <el-tag :type="scope.row.severity === 'error' ? 'danger' : 'warning'">
                {{ scope.row.severity === 'error' ? '错误' : '警告' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="issueCode" label="问题码" min-width="180" />
          <el-table-column prop="message" label="原因" min-width="260" show-overflow-tooltip />
          <el-table-column prop="createTime" label="发生时间" min-width="170" />
        </el-table>
        <el-empty
          v-else
          :image-size="62"
          :description="currentStatus === 'waiting_confirm' ? '预检通过，暂无问题' : '暂无预检问题'"
        />
      </template>
      <el-empty v-else :image-size="62" description="预检尚未开始" />
    </section>

    <template #footer>
      <el-button :disabled="submitting || confirming" @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="startDisabled" @click="startPrecheck">
        开始预检
      </el-button>
      <el-button type="success" :loading="confirming" :disabled="!canConfirm" @click="confirmImport">
        确认导入
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import type { UploadFile, UploadFiles, UploadRawFile, UploadUserFile } from 'element-plus';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue';
import type { QualificationVO } from '@/api/certmuse/catalog/subject-version/types';
import type {
  PaperImportBatchVO,
  PaperImportErrorVO,
  PaperImportIssueVO,
  PaperImportProgressVO
} from '@/api/certmuse/question/paper-import';
import {
  confirmPaperImport,
  createPaperImport,
  getPaperImportProgress,
  getPaperImportQualifications,
  listPaperImportIssues,
  validatePaperImport
} from '@/api/certmuse/question/paper-import';
import { extractErrorMessage, getHandledRequestError } from '@/utils/request';

const props = defineProps<{ modelValue: boolean }>();
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; success: [result: PaperImportProgressVO] }>();
const maxArchiveSize = 64 * 1024 * 1024;
const maxArchiveFiles = 3;
const pollingInterval = 1500;
const form = reactive({
  collectionName: '',
  collectionType: 'SIMULATION' as 'FIRST_DIAGNOSTIC' | 'SIMULATION' | 'PAST_PAPER',
  durationMinutes: 240,
  certificationId: '',
  examYear: undefined as number | undefined,
  examMonth: undefined as number | undefined,
  paperTypeCode: '',
  paperTypeName: ''
});
const qualifications = ref<QualificationVO[]>([]);
const qualificationsLoading = ref(false);
const fileList = ref<UploadUserFile[]>([]);
const folderInput = ref<HTMLInputElement>();
const fileError = ref('');
const fileHash = ref('');
const hashState = ref<'idle' | 'calculating' | 'success' | 'failed'>('idle');
const batch = ref<PaperImportBatchVO>();
const progress = ref<PaperImportProgressVO>();
const issues = ref<PaperImportIssueVO[]>([]);
const issuesLoading = ref(false);
const issueQuery = reactive<{ severity?: 'error' | 'warning'; issueCode?: string }>({});
const submitting = ref(false);
const confirming = ref(false);
const operationError = ref('');
let pollTimer: ReturnType<typeof setTimeout> | undefined;
let emitted = false;
const statusLabels: Record<string, string> = {
  uploaded: '文件已上传',
  parsing: '正在解析',
  validating: '正在预检',
  waiting_confirm: '预检完成',
  importing: '正在导入',
  completed: '导入完成',
  failed: '处理失败',
  cancelled: '已取消'
};

const dialogVisible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) });
const selectedFiles = computed<File[]>(() => fileList.value.flatMap(item => (item.raw ? [item.raw] : [])));
const hasBatch = computed(() => Boolean(batch.value));
const pastPaperMetadataComplete = computed(
  () =>
    form.collectionType !== 'PAST_PAPER' ||
    (Boolean(form.examYear) &&
      [5, 11].includes(form.examMonth ?? 0) &&
      Boolean(form.paperTypeCode.trim()) &&
      Boolean(form.paperTypeName.trim()))
);
const currentStatus = computed(() => progress.value?.status ?? batch.value?.status);
const canConfirm = computed(
  () =>
    currentStatus.value === 'waiting_confirm' &&
    (progress.value?.validCount ?? 0) > 0 &&
    (progress.value?.warningCount ?? 0) === 0 &&
    (progress.value?.failedCount ?? 0) === 0 &&
    !confirming.value
);
const startDisabled = computed(
  () =>
    submitting.value ||
    hasBatch.value ||
    !form.collectionName ||
    !form.certificationId ||
    !pastPaperMetadataComplete.value ||
    selectedFiles.value.length === 0 ||
    Boolean(fileError.value) ||
    hashState.value !== 'success'
);
const statusLabel = computed(() => statusLabels[currentStatus.value ?? 'uploaded'] ?? '处理中');
const statusTip = computed(() => {
  if (currentStatus.value === 'waiting_confirm')
    return (progress.value?.warningCount ?? 0) === 0 && (progress.value?.failedCount ?? 0) === 0
      ? '全部题目通过预检，可以确认导入。'
      : '存在 warning 或 error，不能确认导入。';
  if (currentStatus.value === 'completed') return '题目与题集草稿已创建。';
  return '预检会校验每道题目，并确定唯一的考纲版本。';
});

async function loadQualifications() {
  qualificationsLoading.value = true;
  try {
    qualifications.value = await getPaperImportQualifications();
  } catch (error) {
    operationError.value = await operationErrorMessage(error, '加载考试资格失败。');
  } finally {
    qualificationsLoading.value = false;
  }
}

async function handleFileChange(file: UploadFile, uploadFiles?: UploadFiles) {
  const files = (uploadFiles ?? [file]).map(item => item.raw).filter(Boolean) as File[];
  await setFiles(files);
}

function pickFolder() {
  folderInput.value?.click();
}

async function handleFolderChange(event: Event) {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files ?? []);
  await setFiles(files, true);
  input.value = '';
}

async function setFiles(files: File[], fromFolder = false) {
  resetRuntime();
  resetIdentity();
  fileError.value = '';
  fileHash.value = '';
  hashState.value = 'idle';
  fileList.value = files.map((file, index) => ({ name: file.name, raw: toUploadRawFile(file, index) }));
  if (!files.length) fileError.value = '文件夹中没有 ZIP 文件。';
  else if (files.length > maxArchiveFiles) fileError.value = '一个题集文件夹最多包含 3 个 ZIP 文件。';
  else if (fromFolder && files.some(file => file.webkitRelativePath.split('/').filter(Boolean).length !== 2)) {
    fileError.value = '题集文件夹只能直接包含 ZIP 文件，不能包含子目录。';
  } else if (files.some(file => file.size === 0)) fileError.value = '文件不能为空。';
  else if (files.some(file => !file.name.toLowerCase().endsWith('.zip'))) fileError.value = '试卷导入只支持 .zip 文件。';
  else if (files.reduce((total, file) => total + file.size, 0) > maxArchiveSize)
    fileError.value = files.length > 1 ? 'ZIP 包合计超过 64 MiB 上限，请重新选择。' : 'ZIP 包超过 64 MiB 上限，请重新选择。';
  else {
    for (const file of files) {
      const prefix = new Uint8Array(await file.slice(0, 4).arrayBuffer());
      if (prefix.length < 4 || prefix[0] !== 0x50 || prefix[1] !== 0x4b) {
        fileError.value = `文件不是有效的 ZIP 包：${file.name}`;
        break;
      }
    }
  }
  if (fileError.value) return;
  hashState.value = 'calculating';
  try {
    const content = new Blob(files);
    const digest = await crypto.subtle.digest('SHA-256', await content.arrayBuffer());
    fileHash.value = Array.from(new Uint8Array(digest), item => item.toString(16).padStart(2, '0')).join('');
    hashState.value = 'success';
  } catch {
    hashState.value = 'failed';
    fileError.value = '前端 SHA-256 计算失败，请重新选择文件。';
  }
}

function toUploadRawFile(file: File, index: number): UploadRawFile {
  return Object.assign(file, { uid: -Date.now() - index });
}

function handleFileExceed() {
  fileError.value = '一次最多选择 3 个 ZIP 文件。';
}
function resetFile() {
  fileList.value = [];
  fileError.value = '';
  fileHash.value = '';
  hashState.value = 'idle';
  resetRuntime();
  resetIdentity();
}
function resetIdentity() {
  operationError.value = '';
}
function resetRuntime() {
  stopPolling();
  batch.value = undefined;
  progress.value = undefined;
  issues.value = [];
  issueQuery.severity = undefined;
  issueQuery.issueCode = undefined;
}

async function startPrecheck() {
  if (startDisabled.value || !selectedFiles.value.length) return;
  submitting.value = true;
  operationError.value = '';
  try {
    const created = await createPaperImport({ ...form, files: selectedFiles.value, requestId: crypto.randomUUID() });
    if (!created.data) throw new Error('创建试卷导入批次响应缺少 data。');
    batch.value = created.data;
    const accepted = await validatePaperImport(batch.value.id, crypto.randomUUID());
    if (!accepted.data) throw new Error('触发预检响应缺少 data。');
    await refreshProgress();
    if (!isTerminal(currentStatus.value)) schedulePolling();
  } catch (error) {
    operationError.value = await operationErrorMessage(error, '创建试卷导入失败。');
  } finally {
    submitting.value = false;
  }
}

async function confirmImport() {
  if (!batch.value || !canConfirm.value) return;
  try {
    await ElMessageBox.confirm(
      `将原子创建 ${progress.value?.validCount ?? 0} 道草稿题目和题集“${form.collectionName}”。任一写入失败会全部回滚，确定继续吗？`,
      '确认导入试卷',
      { type: 'warning', confirmButtonText: '确认导入', cancelButtonText: '取消' }
    );
  } catch {
    return;
  }
  confirming.value = true;
  operationError.value = '';
  try {
    const accepted = await confirmPaperImport(batch.value.id, crypto.randomUUID());
    if (!accepted.data) throw new Error('确认导入响应缺少 data。');
    await refreshProgress();
    if (!isTerminal(currentStatus.value)) schedulePolling();
  } catch (error) {
    operationError.value = await operationErrorMessage(error, '确认导入失败。');
  } finally {
    confirming.value = false;
  }
}

async function refreshProgress() {
  if (!batch.value) return;
  const response = await getPaperImportProgress(batch.value.id);
  if (!response.data) throw new Error('导入进度响应缺少 data。');
  progress.value = response.data;
  if (isTerminal(response.data.status)) {
    stopPolling();
    await loadIssues();
    if (response.data.status === 'completed' && !emitted) {
      emitted = true;
      emit('success', response.data);
      dialogVisible.value = false;
    }
  }
}

async function loadIssues() {
  if (!batch.value) return;
  issuesLoading.value = true;
  try {
    const response = await listPaperImportIssues(batch.value.id, { pageNum: 1, pageSize: 100, ...issueQuery });
    issues.value = response.data?.rows ?? [];
  } catch (error) {
    operationError.value = await operationErrorMessage(error, '加载预检问题失败。');
  } finally {
    issuesLoading.value = false;
  }
}
function searchIssues() {
  if (batch.value) void loadIssues();
}
async function operationErrorMessage(error: unknown, fallback: string) {
  const handled = getHandledRequestError<PaperImportErrorVO>(error);
  const responseData = handled?.responseData
    ?? (error as { response?: { data?: { data?: PaperImportErrorVO } } })?.response?.data?.data;
  const fieldErrors = responseData?.fieldErrors ?? [];
  if (fieldErrors.length) {
    return fieldErrors.map(item => `${item.field}：${item.message}`).join('；');
  }
  return (await extractErrorMessage(error)) || fallback;
}
function schedulePolling() {
  stopPolling();
  pollTimer = setTimeout(async () => {
    await refreshProgress();
    if (!isTerminal(currentStatus.value)) schedulePolling();
  }, pollingInterval);
}
function stopPolling() {
  if (pollTimer) clearTimeout(pollTimer);
  pollTimer = undefined;
}
function isTerminal(status: string | undefined) {
  return ['waiting_confirm', 'completed', 'failed', 'cancelled'].includes(status ?? '');
}
function reset() {
  stopPolling();
  resetRuntime();
  resetFile();
  form.collectionName = '';
  form.collectionType = 'SIMULATION';
  form.durationMinutes = 240;
  form.certificationId = '';
  form.examYear = undefined;
  form.examMonth = undefined;
  form.paperTypeCode = '';
  form.paperTypeName = '';
  emitted = false;
  operationError.value = '';
}
watch(
  () => props.modelValue,
  visible => {
    if (visible) {
      reset();
      void loadQualifications();
    } else stopPolling();
  }
);
onBeforeUnmount(stopPolling);
</script>

<style scoped>
.form-control {
  width: 100%;
}
.field-tip,
.text-muted {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.folder-input {
  display: none;
}
.error-tip {
  margin-top: 6px;
  color: var(--el-color-danger);
  font-size: 12px;
}
.batch-panel,
.issues-panel {
  margin-top: 18px;
  padding: 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
}
.batch-panel header,
.issues-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}
.batch-panel h4,
.issues-panel h4 {
  margin: 0;
  font-size: 16px;
}
.batch-panel p,
.issues-header p {
  margin: 10px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.resolved-context {
  margin-top: 12px;
  font-size: 13px;
}
.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
}
.summary-grid article {
  display: grid;
  min-height: 100px;
  padding: 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
}
.summary-grid span,
.summary-grid small {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.summary-grid strong {
  align-self: center;
  font-size: 28px;
}
.issue-filters {
  display: flex;
  gap: 8px;
}
.issue-filters .el-select {
  width: 135px;
}
.issue-filters .el-input {
  width: 190px;
}
@media (max-width: 760px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .issues-header {
    flex-direction: column;
  }
  .issue-filters {
    width: 100%;
    flex-wrap: wrap;
  }
}
</style>
