<template>
  <div class="p-2 subject-version-page">
    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': filterCollapsed }">
        <template #header>
          <div
            class="panel-heading search-panel-toggle"
            role="button"
            tabindex="0"
            aria-label="切换资格筛选"
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
        <el-form class="query-form" :model="draftFilters" :inline="true">
          <el-form-item label="关键词">
            <el-input
              v-model="draftFilters.keyword"
              clearable
              placeholder="请输入资格名称"
              :prefix-icon="Search"
              @keyup.enter="applyFilters"
            />
          </el-form-item>
          <el-form-item label="资格等级">
            <el-select v-model="draftFilters.qualificationLevel" clearable placeholder="全部等级">
              <el-option
                v-for="option in levelOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="draftFilters.status" clearable placeholder="全部状态">
              <el-option label="启用" value="0" />
              <el-option label="停用" value="1" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" :loading="loading" @click="applyFilters">搜索</el-button>
            <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>

    <el-card shadow="hover" class="table-panel qualification-results-panel">
      <template #header>
        <div class="toolbar-shell">
          <div class="table-heading">
            <span class="panel-kicker">Qualification Dataset</span>
            <h3>资格列表</h3>
          </div>
          <div class="toolbar-actions">
            <el-button type="primary" plain :icon="Plus" @click="openQualificationDialog()">新增资格</el-button>
            <el-button
              type="success"
              plain
              :disabled="single"
              :icon="Edit"
              @click="editSelectedRow"
            >
              修改
            </el-button>
            <el-button type="danger" plain :disabled="multiple" :icon="Delete" @click="confirmDeleteSelected">
              删除
            </el-button>
          </div>
        </div>
      </template>

      <el-table
        v-loading="loading"
        border
        class="data-table"
        :data="qualifications"
        row-key="id"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="50" align="center" />
        <el-table-column label="资格名称" min-width="220" :show-overflow-tooltip="true">
          <template #default="scope">
            <el-link
              type="primary"
              underline="never"
              @click="openQualificationDialog(scope.row as QualificationVO)"
            >
              {{ scope.row.certificationName }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column label="资格等级" min-width="100" align="center">
          <template #default="scope">
            <el-tag effect="plain">
              {{ levelLabel(scope.row.qualificationLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="90" align="center">
          <template #default="scope">
            <el-tag
              :type="scope.row.status === '0' ? 'success' : 'info'"
            >
              {{ statusLabel(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="110" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="编辑资格" placement="top">
              <el-button link type="primary" icon="Edit" @click="openQualificationDialog(scope.row as QualificationVO)" />
            </el-tooltip>
            <el-tooltip content="删除资格" placement="top">
              <el-button link type="primary" icon="Delete" @click="confirmDeleteQualification(scope.row as QualificationVO)" />
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
      <Pagination
        v-model:page="qualificationPagination.pageNum"
        v-model:limit="qualificationPagination.pageSize"
        :total="displayedQualificationTotal"
        @pagination="loadQualifications"
      />
    </el-card>

    <el-dialog
      v-model="qualificationDialog.visible"
      :title="qualificationDialog.id ? '编辑资格' : '新增资格'"
      width="560px"
      destroy-on-close
    >
      <el-form ref="qualificationFormRef" :model="qualificationForm" :rules="qualificationRules" label-position="top">
        <div class="dialog-grid">
          <el-form-item label="资格名称" prop="certificationName">
            <el-input v-model="qualificationForm.certificationName" maxlength="200" placeholder="如 系统架构设计师" />
          </el-form-item>
          <el-form-item label="资格等级" prop="qualificationLevel">
            <el-select v-model="qualificationForm.qualificationLevel">
              <el-option
                v-for="option in levelOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="qualificationDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveQualification">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="CertmuseSubjectVersion" lang="ts">
import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';
import type {
  QualificationForm,
  QualificationLevel,
  QualificationPageQuery,
  QualificationStatus,
  QualificationVO
} from '@/api/certmuse/catalog/subject-version';
import {
  createQualification,
  deleteQualification,
  listQualifications,
  updateQualification
} from '@/api/certmuse/catalog/subject-version';
import Pagination from '@/components/Pagination/index.vue';

const levelOptions = [
  { value: 'HIGH' as const, label: '高级' },
  { value: 'MIDDLE' as const, label: '中级' },
  { value: 'LOW' as const, label: '初级' }
];
const emptyFilters = (): QualificationPageQuery => ({
  keyword: '',
  qualificationLevel: '',
  status: ''
});
const emptyQualification = (): QualificationForm => ({
  certificationCode: '',
  certificationName: '',
  qualificationLevel: 'HIGH',
  status: '0',
  sortOrder: 0
});
const qualifications = ref<QualificationVO[]>([]);
const qualificationPagination = reactive({ pageNum: 1, pageSize: 10 });
const qualificationTotal = ref(0);
const filterCollapsed = ref(false);
const loading = ref(false);
const saving = ref(false);
const selectedRows = ref<QualificationVO[]>([]);
const draftFilters = reactive<QualificationPageQuery>(emptyFilters());
const appliedFilters = reactive<QualificationPageQuery>(emptyFilters());
const qualificationDialog = reactive({ visible: false, id: '' });
const qualificationForm = reactive<QualificationForm>(emptyQualification());
const qualificationFormRef = ref<FormInstance>();
const single = computed(() => selectedRows.value.length !== 1);
const multiple = computed(() => selectedRows.value.length === 0);
const displayedQualificationTotal = computed(() => Math.max(qualificationTotal.value, qualifications.value.length));
const qualificationRules: FormRules<QualificationForm> = {
  certificationName: [{ required: true, whitespace: true, message: '请输入资格名称', trigger: 'blur' }],
  qualificationLevel: [{ required: true, message: '请选择资格等级', trigger: 'change' }]
};

async function loadQualifications() {
  loading.value = true;
  try {
    const result = await listQualifications({
      ...appliedFilters,
      pageNum: qualificationPagination.pageNum,
      pageSize: qualificationPagination.pageSize
    });
    qualifications.value = result.data.rows;
    qualificationTotal.value = result.data.total;
    selectedRows.value = [];
  } catch (error) {
    ElMessage.error(errorMessage(error, '资格列表加载失败'));
  } finally {
    loading.value = false;
  }
}

async function applyFilters() {
  Object.assign(appliedFilters, draftFilters);
  qualificationPagination.pageNum = 1;
  await loadQualifications();
}

async function resetFilters() {
  Object.assign(draftFilters, emptyFilters());
  Object.assign(appliedFilters, emptyFilters());
  qualificationPagination.pageNum = 1;
  qualificationPagination.pageSize = 10;
  await loadQualifications();
}

function handleSelectionChange(rows: QualificationVO[]) {
  selectedRows.value = rows;
}

function editSelectedRow() {
  const row = selectedRows.value[0];
  if (!row) return;
  openQualificationDialog(row);
}

function openQualificationDialog(item?: QualificationVO) {
  qualificationDialog.id = item?.id ?? '';
  Object.assign(
    qualificationForm,
    item
      ? {
          certificationCode: item.certificationCode,
          certificationName: item.certificationName,
          qualificationLevel: item.qualificationLevel,
          status: item.status,
          sortOrder: item.sortOrder
        }
      : {
          ...emptyQualification(),
          certificationCode: nextPlaceholderCode(),
          sortOrder: nextSortOrder()
        }
  );
  qualificationDialog.visible = true;
}

async function saveQualification() {
  if (!(await qualificationFormRef.value?.validate().catch(() => false))) return;
  saving.value = true;
  try {
    if (qualificationDialog.id) await updateQualification(qualificationDialog.id, { ...qualificationForm });
    else await createQualification({ ...qualificationForm });
    ElMessage.success(qualificationDialog.id ? '资格已更新' : '资格已新增');
    qualificationDialog.visible = false;
    await loadQualifications();
  } catch (error) {
    ElMessage.error(errorMessage(error, '资格保存失败'));
  } finally {
    saving.value = false;
  }
}
async function confirmDeleteQualification(item: QualificationVO) {
  try {
    await ElMessageBox.confirm(`确定删除资格“${item.certificationName}”吗？`, '删除资格', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    });
    await deleteQualification(item.id);
    ElMessage.success('资格已删除');
    await loadQualifications();
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(errorMessage(error, '资格删除失败'));
  }
}
async function confirmDeleteSelected() {
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selectedRows.value.length} 个资格吗？`, '批量删除', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    });
    await Promise.all(selectedRows.value.map(row => deleteQualification(row.id)));
    ElMessage.success('选中内容已删除');
    selectedRows.value = [];
    await loadQualifications();
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(errorMessage(error, '资格删除失败'));
  }
}

function levelLabel(value: QualificationLevel) {
  return levelOptions.find(option => option.value === value)?.label ?? value;
}

function statusLabel(value: QualificationStatus) {
  return value === '0' ? '启用' : '停用';
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback;
}

function nextPlaceholderCode() {
  const largestSuffix = qualifications.value.reduce((largest, item) => {
    const matched = /^NULL_(\d+)$/i.exec(item.certificationCode);
    return matched ? Math.max(largest, Number(matched[1])) : largest;
  }, 0);
  return `NULL_${largestSuffix + 1}`;
}

function nextSortOrder() {
  return qualifications.value.reduce((largest, item) => Math.max(largest, item.sortOrder), -1) + 1;
}
onMounted(() => void loadQualifications());
</script>

<style scoped lang="scss">
@use '@/assets/styles/components/page-shell' as pageShell;

@include pageShell.table-crud-page;

.dialog-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}
.dialog-grid :deep(.el-input-number),
.dialog-grid :deep(.el-select),
:deep(.el-date-editor) {
  width: 100%;
}
@media (max-width: 620px) {
  .dialog-grid {
    grid-template-columns: 1fr;
  }
}
</style>
