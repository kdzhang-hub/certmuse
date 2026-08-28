<template>
  <div class="p-2 app-container">
    <el-card shadow="hover" class="table-panel">
      <template #header>
        <div class="toolbar-shell">
          <div class="table-heading">
            <h3>今日任务列表</h3>
            <span>每项任务按“知识点学习—知识点练习”顺序完成。</span>
          </div>
          <div class="toolbar-actions">
            <el-button plain class="history-button" @click="router.push('/learning/history')">题目历史 <el-icon><Clock /></el-icon></el-button>
            <right-toolbar :search="false" @query-table="getList" />
          </div>
        </div>
      </template>

      <el-table v-if="taskList.length" v-loading="loading" border class="data-table" :data="taskList">
        <el-table-column label="任务" min-width="250">
          <template #default="scope">
            <strong>{{ scope.row.title }}</strong>
            <div class="table-subtext">{{ scope.row.knowledgePoint }}</div>
          </template>
        </el-table-column>
        <el-table-column label="题量" width="90" align="center" prop="questionCount" />
        <el-table-column label="预计用时" width="110" align="center">
          <template #default="scope">{{ scope.row.estimatedMinutes }} 分钟</template>
        </el-table-column>
        <el-table-column label="进度" min-width="155">
          <template #default="scope">
            <el-progress :percentage="progressOf(asTask(scope.row))" :stroke-width="8" />
            <span class="table-subtext">{{ scope.row.completedQuestionCount }} / {{ scope.row.questionCount }} 题</span>
          </template>
        </el-table-column>
        <el-table-column label="最近学习" width="145" align="center">
          <template #default="scope">{{ formatUpdatedAt(asTask(scope.row).updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <button
              type="button"
              class="task-action"
              :aria-busy="launchingTaskId === scope.row.id"
              :disabled="Boolean(launchingTaskId)"
              @click="launchTask(asTask(scope.row))"
            >
              {{ scope.row.action === 'CONTINUE' ? '继续任务' : '开始任务' }}
            </button>
          </template>
        </el-table-column>
      </el-table>

      <div v-else-if="!loading" class="empty-task-state">
        <el-empty description="当前没有待完成的任务">
          <el-button v-if="canShowSupplement" type="primary" :loading="supplementing" @click="supplement">
            学习更多
          </el-button>
        </el-empty>
      </div>

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
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import type { StudentTaskPoolQuery, StudentTaskPoolVO } from '@/api/student/types';
import {
  launchLearningTask,
  listLearningTaskPool,
  supplementLearningTasks
} from '@/api/certmuse/learning/tasks';

const router = useRouter();
const loading = ref(false);
const supplementing = ref(false);
const launchingTaskId = ref<string>();
const total = ref(0);
const canShowSupplement = ref(false);
const taskList = ref<StudentTaskPoolVO[]>([]);
const queryParams = reactive<StudentTaskPoolQuery>({ pageNum: 1, pageSize: 10, keyword: '' });

const asTask = (row: unknown) => row as StudentTaskPoolVO;
const progressOf = (task: StudentTaskPoolVO) =>
  task.questionCount === 0 ? 0 : Math.round((task.completedQuestionCount / task.questionCount) * 100);
const formatUpdatedAt = (value: string) => {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).format(date);
};

const getList = async () => {
  loading.value = true;
  try {
    const response = await listLearningTaskPool(queryParams);
    taskList.value = response.data?.rows ?? [];
    total.value = response.data?.total ?? 0;
    canShowSupplement.value = response.data?.canShowSupplement ?? false;
  } finally {
    loading.value = false;
  }
};

const launchTask = async (task: StudentTaskPoolVO) => {
  launchingTaskId.value = task.id;
  try {
    const response = await launchLearningTask(task.id);
    const result = response.data;
    if (!result) return;
    if (result.nextAction === 'OPEN_PRACTICE_SESSION') {
      await router.push({
        path: '/learning/session/task',
        query: { taskId: result.taskId, phase: 'practice', sessionId: result.sessionId ?? undefined }
      });
      return;
    }
    await router.push({ path: '/learning/session/task', query: { taskId: result.taskId } });
  } finally {
    launchingTaskId.value = undefined;
  }
};

const supplement = async () => {
  supplementing.value = true;
  try {
    await supplementLearningTasks();
    queryParams.pageNum = 1;
    await getList();
  } finally {
    supplementing.value = false;
  }
};

onMounted(getList);
</script>

<style lang="scss" scoped>
.table-subtext {
  display: block;
  margin-top: 5px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.task-action {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--el-color-primary);
  font: inherit;
  cursor: pointer;
}

.task-action:hover { color: var(--el-color-primary-light-3); }

.task-action:focus-visible {
  outline: 2px solid var(--el-color-primary-light-5);
  outline-offset: 3px;
}

.task-action:disabled {
  color: var(--el-text-color-disabled);
  cursor: not-allowed;
}

.empty-task-state {
  min-height: 360px;
  display: grid;
  place-items: center;
}

.toolbar-shell, .toolbar-actions {
  display: flex;
  align-items: center;
}

.toolbar-shell {
  justify-content: space-between;
  gap: 16px;
}

.toolbar-actions { gap: 8px; }

.history-button .el-icon { margin-left: 4px; }

@media (max-width: 640px) {
  .toolbar-shell { align-items: flex-start; flex-direction: column; }
  .toolbar-actions { width: 100%; justify-content: space-between; }
}
</style>
