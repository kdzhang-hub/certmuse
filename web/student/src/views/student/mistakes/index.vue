<template>
  <div class="p-2 app-container">
    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel">
        <template #header>
          <div class="panel-heading"><h3>筛选条件</h3></div>
        </template>
        <el-form :model="queryParams" :inline="true" class="query-form">
          <el-form-item label="题目或知识点">
            <el-input v-model="queryParams.keyword" placeholder="输入关键词" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="复习状态">
            <el-select v-model="queryParams.status" placeholder="全部状态" clearable>
              <el-option v-for="option in statusOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="知识点">
            <el-input v-model="queryParams.knowledgePointId" placeholder="输入知识点 ID" clearable />
          </el-form-item>
          <el-form-item label="作答来源">
            <el-select v-model="queryParams.source" placeholder="全部来源" clearable>
              <el-option v-for="option in sourceOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="累计错误">
            <el-select v-model="queryParams.minWrongCount" placeholder="不限" clearable>
              <el-option label="至少 2 次" :value="2" />
              <el-option label="至少 3 次" :value="3" />
            </el-select>
          </el-form-item>
          <el-form-item label="最近错误日期">
            <el-date-picker
              v-model="lastWrongDate"
              type="date"
              placeholder="选择日期"
              value-format="YYYY-MM-DD"
              clearable
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>

    <el-card shadow="hover" class="table-panel">
      <template #header>
        <div class="toolbar-shell">
          <div class="table-heading">
            <h3>错题复习</h3>
            <span>答对原题后移入“已订正”；记录会保留，之后仍可筛选查看。</span>
          </div>
          <div class="toolbar-actions">
            <el-button type="primary" :disabled="!selectedIds.length" @click="() => startCorrection()">
              重做已选（{{ selectedIds.length }}）
            </el-button>
            <right-toolbar :search="false" @query-table="getList" />
          </div>
        </div>
      </template>
      <el-table v-loading="loading" border class="data-table" :data="mistakeList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="48" :selectable="row => row.status === 'PENDING_CORRECTION' && row.canCorrect" />
        <el-table-column label="题目" min-width="280">
          <template #default="scope">
            <strong>{{ scope.row.stemPreview }}</strong>
          </template>
        </el-table-column>
        <el-table-column label="知识点" width="160" align="center">
          <template #default="scope">{{ knowledgePointText(scope.row as MistakeReviewSummary) }}</template>
        </el-table-column>
        <el-table-column label="累计错误" width="100" align="center">
          <template #default="scope">{{ scope.row.wrongCount }} 次</template>
        </el-table-column>
        <el-table-column label="跳过" width="76" align="center">
          <template #default="scope">{{ scope.row.skipCount }} 次</template>
        </el-table-column>
        <el-table-column label="来源" min-width="125">
          <template #default="scope">{{ sourceText(scope.row.sources) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag :type="statusTagType(scope.row.status)">{{ statusText(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近错误" width="120" align="center">
          <template #default="scope">{{ formatDate(scope.row.lastWrongAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center">
          <template #default="scope">
            <button type="button" class="table-action" @click="openDetail(scope.row as MistakeReviewSummary)">查看</button>
            <button v-if="scope.row.status === 'PENDING_CORRECTION' && scope.row.canCorrect" type="button" class="table-action" @click="startCorrection([scope.row.questionId])">重做</button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="total > 0"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        :total="total"
        @pagination="getList"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  createMistakeCorrectionSession,
  listMistakeReviews,
  type MistakeReviewQuery,
  type MistakeReviewStatus,
  type MistakeReviewSummary,
  type MistakeSource
} from '@/api/certmuse/learning/mistakes';
import { contractErrorFrom } from '@/utils/learning-goal';

const router = useRouter();
const loading = ref(false);
const total = ref(0);
const mistakeList = ref<MistakeReviewSummary[]>([]);
const statusOptions: Array<{ label: string; value: MistakeReviewStatus }> = [
  { label: '待订正', value: 'PENDING_CORRECTION' },
  { label: '已订正', value: 'CORRECTED' }
];
const sourceOptions: Array<{ label: string; value: MistakeSource }> = [
  { label: '首次诊断', value: 'INITIAL_DIAGNOSIS' },
  { label: '每日任务', value: 'DAILY_TASK' },
  { label: '自主练习', value: 'SELF_PRACTICE' },
  { label: '历年真题', value: 'PAST_PAPER' },
  { label: '模拟考试', value: 'SIMULATION' }
];
const sourceLabels: Record<MistakeSource, string> = Object.fromEntries(sourceOptions.map(item => [item.value, item.label])) as Record<MistakeSource, string>;
const selectedIds = ref<string[]>([]);
const lastWrongDate = ref('');
const queryParams = reactive<MistakeReviewQuery>({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  status: 'PENDING_CORRECTION',
  knowledgePointId: undefined,
  source: undefined,
  minWrongCount: undefined
});

const statusText = (status: MistakeReviewStatus) => status === 'CORRECTED' ? '已订正' : '待订正';
const statusTagType = (status: MistakeReviewStatus) => status === 'CORRECTED' ? 'success' : 'warning';
const sourceText = (sources: MistakeSource[]) => sources.map(source => sourceLabels[source]).join('、');
const knowledgePointText = (row: MistakeReviewSummary) => row.knowledgePoints.map(item => item.name).join('、');
const formatDate = (value: string) => value.slice(0, 10);
const getList = async () => {
  loading.value = true;
  try {
    if (lastWrongDate.value) {
      const rows: MistakeReviewSummary[] = [];
      let pageNum = 1;
      let responseTotal = 0;
      do {
        const response = await listMistakeReviews({ ...queryParams, pageNum, pageSize: 100 });
        const page = response.data;
        rows.push(...(page?.rows ?? []));
        responseTotal = page?.total ?? 0;
        pageNum += 1;
      } while (rows.length < responseTotal);

      const filteredRows = rows.filter(item => formatDate(item.lastWrongAt) === lastWrongDate.value);
      total.value = filteredRows.length;
      const start = (queryParams.pageNum - 1) * queryParams.pageSize;
      mistakeList.value = filteredRows.slice(start, start + queryParams.pageSize);
      return;
    }
    const response = await listMistakeReviews(queryParams);
    mistakeList.value = response.data?.rows ?? [];
    total.value = response.data?.total ?? 0;
  } finally {
    loading.value = false;
  }
};
const handleQuery = () => {
  queryParams.pageNum = 1;
  void getList();
};
const resetQuery = () => {
  Object.assign(queryParams, {
    pageNum: 1,
    pageSize: 10,
    keyword: '',
    status: 'PENDING_CORRECTION',
    knowledgePointId: undefined,
    source: undefined,
    minWrongCount: undefined
  });
  lastWrongDate.value = '';
  void getList();
};
const handleSelectionChange = (rows: MistakeReviewSummary[]) => {
  selectedIds.value = rows.map(row => row.questionId);
};
const startCorrection = async (ids = selectedIds.value) => {
  try {
    const response = await createMistakeCorrectionSession({ questionIds: ids });
    if (!response.data) throw new Error('未创建重做会话。');
    await router.push({ path: '/learning/session/correction', query: { sessionId: response.data.sessionId } });
  } catch (error) {
    const contractError = contractErrorFrom(error) as ReturnType<typeof contractErrorFrom> & { details?: { sessionId?: string } };
    if (contractError?.errorCode === 'MISTAKE_CORRECTION_IN_PROGRESS' && contractError.details?.sessionId) {
      ElMessage.info('已有进行中的错题订正，已为你继续该会话。');
      await router.push({ path: '/learning/session/correction', query: { sessionId: contractError.details.sessionId } });
      return;
    }
    ElMessage.error(error instanceof Error ? error.message : '无法开始错题订正。');
  }
};
const openDetail = (item: MistakeReviewSummary) => {
  return router.push(`/learning/mistakes/${item.questionId}`);
};

onMounted(getList);
</script>

<style lang="scss" scoped>
.query-form {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  align-items: end;
  gap: 16px 24px;
}

.query-form :deep(.el-form-item) {
  display: flex;
  width: 100%;
  margin: 0;
}

.query-form :deep(.el-form-item__content) {
  flex: 1;
  min-width: 0;
}

.query-form :deep(.el-input),
.query-form :deep(.el-select),
.query-form :deep(.el-date-editor) {
  width: 100%;
}

.query-form :deep(.el-form-item:last-child) {
  grid-column: 3 / -1;
  justify-self: end;
  margin-left: 0;
  width: auto;
}

.query-form :deep(.el-form-item:last-child .el-form-item__content) {
  display: flex;
  flex: none;
  gap: 8px;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.table-action {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--el-color-primary);
  font: inherit;
  cursor: pointer;
}

.table-action + .table-action { margin-left: 12px; }

.table-action:hover { color: var(--el-color-primary-light-3); }

.table-action:focus-visible {
  outline: 2px solid var(--el-color-primary-light-5);
  outline-offset: 3px;
}

@media (max-width: 1500px) {
  .query-form {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .query-form :deep(.el-form-item:last-child) {
    grid-column: 3;
  }
}

@media (max-width: 960px) {
  .query-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .query-form :deep(.el-form-item:last-child) {
    grid-column: 2;
  }
}

@media (max-width: 640px) {
  .query-form {
    display: flex;
    gap: 12px;
  }

  .query-form :deep(.el-form-item:last-child) {
    width: 100%;
  }

  .query-form :deep(.el-form-item:last-child .el-form-item__content) {
    width: 100%;
  }

  .query-form :deep(.el-form-item:last-child .el-button) {
    flex: 1;
  }
}
</style>
