<template>
  <div class="p-2 app-container" v-loading="loading">
    <el-result v-if="!task && !loading" icon="warning" title="任务不存在" sub-title="该任务可能已被学习计划更新。">
      <template #extra>
        <el-button type="primary" @click="router.push('/learning/tasks')">返回任务列表</el-button>
      </template>
    </el-result>
    <template v-else-if="task">
      <el-card shadow="hover">
        <template #header>
          <div class="card-header">
            <div>
              <el-tag :type="taskTagType(task.status)">{{ task.status }}</el-tag>
              <h2>{{ task.title }}</h2>
              <p>{{ task.type }} · {{ task.knowledgePoint }}</p>
            </div>
            <el-button plain @click="router.push('/learning/tasks')">返回列表</el-button>
          </div>
        </template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="题量">{{ task.questionCount }} 题</el-descriptions-item>
          <el-descriptions-item label="预计用时">{{ task.duration }}</el-descriptions-item>
          <el-descriptions-item label="当前进度">
            {{ task.progress }} / {{ task.questionCount }} 题
          </el-descriptions-item>
          <el-descriptions-item label="最近更新">{{ task.updatedAt }}</el-descriptions-item>
        </el-descriptions>
        <el-alert class="reason-alert" type="info" :closable="false" show-icon>
          <template #title>推荐原因</template>
          {{ task.recommendation }}
        </el-alert>
        <div class="actions">
          <el-button type="primary" @click="startTask">
            {{ task.status === '进行中' ? '继续答题' : '开始答题' }}
          </el-button>
          <el-button @click="router.push('/learning/mistakes')">查看相关错题</el-button>
        </div>
      </el-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import type { StudentTaskStatus, StudentTaskVO } from '@/api/student/types';
import { getStudentTask } from '@/api/student';

const route = useRoute();
const router = useRouter();
const loading = ref(true);
const task = ref<StudentTaskVO>();

const taskTagType = (status: StudentTaskStatus) =>
  status === '已完成' ? 'success' : status === '进行中' ? 'primary' : 'info';
const startTask = () => router.push({ path: '/learning/session/task', query: { taskId: task.value?.id } });

onMounted(async () => {
  try {
    const response = await getStudentTask(String(route.params.taskId));
    task.value = response.data;
  } finally {
    loading.value = false;
  }
});
</script>

<style lang="scss" scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;

  h2,
  p {
    margin: 10px 0 0;
  }

  p {
    color: var(--el-text-color-secondary);
  }
}

.reason-alert {
  margin-top: 20px;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 20px;
}
</style>
