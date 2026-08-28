<template>
  <div v-loading="loading" class="p-2 app-container task-learning-material">
    <el-result v-if="!material && !loading" icon="warning" title="学习资料不可用" sub-title="请返回今日任务后重新开始。">
      <template #extra><el-button type="primary" @click="router.replace('/learning/tasks')">返回今日任务</el-button></template>
    </el-result>

    <template v-else-if="material">
      <task-session-steps phase="study" />
      <el-card shadow="never" class="task-learning-material__hero">
        <div class="task-learning-material__hero-copy">
          <span class="task-learning-material__eyebrow">TODAY'S FOCUS</span>
          <h2>{{ material.title }}</h2>
          <p>先理解关键概念，再通过题目检验掌握情况。</p>
        </div>
        <div class="task-learning-material__hero-meta">
          <span><b>学习主题</b>{{ material.knowledgePoint }}</span>
          <span><b>建议阅读</b>约 {{ material.estimatedMinutes }} 分钟</span>
        </div>
      </el-card>

      <el-card v-for="(section, index) in material.sections" :key="section.order" shadow="never" class="task-learning-material__section">
        <template #header><div class="task-learning-material__section-title"><i>{{ String(index + 1).padStart(2, '0') }}</i><strong>{{ section.title }}</strong></div></template>
        <p class="task-learning-material__content">{{ section.content }}</p>
        <el-image
          v-for="image in section.images"
          :key="`${image.sortOrder}-${image.url}`"
          class="task-learning-material__image"
          :src="image.url"
          :alt="image.alt ?? ''"
          fit="contain"
        />
      </el-card>

      <el-card shadow="never" class="task-learning-material__action">
        <div class="task-learning-material__action-copy">
          <span>下一步</span>
          <h3>准备好后，开始题目训练</h3>
          <p>提交后会显示正确答案与解析；全部题目提交后即可完成今日任务。</p>
        </div>
        <el-button type="primary" size="large" :loading="starting" @click="startPractice">开始题目训练 →</el-button>
      </el-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { getDailyTaskLearningContent, startDailyTaskPractice, type DailyTaskLearningContentVo } from '@/api/certmuse/assessment/daily-tasks';
import TaskSessionSteps from './TaskSessionSteps.vue';

const props = defineProps<{ taskId: string }>();
const router = useRouter();
const loading = ref(true);
const starting = ref(false);
const material = ref<DailyTaskLearningContentVo>();

async function startPractice() {
  if (starting.value) return;
  starting.value = true;
  try {
    const response = await startDailyTaskPractice(props.taskId, crypto.randomUUID().toLowerCase());
    if (!response.data?.sessionId) throw new Error('未取得题目训练会话。');
    await router.replace({ path: '/learning/session/task', query: { taskId: props.taskId, phase: 'practice', sessionId: response.data.sessionId } });
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '无法开始题目训练，请稍后重试。');
  } finally {
    starting.value = false;
  }
}

onMounted(async () => {
  try {
    const response = await getDailyTaskLearningContent(props.taskId);
    material.value = response.data;
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped lang="scss">
.task-learning-material { width: 100%; max-width: 100%; margin: 0 auto; padding-top: 14px; }
.task-learning-material__hero, .task-learning-material__section, .task-learning-material__action { margin-bottom: 18px; border-color: transparent; box-shadow: 0 10px 30px rgb(31 55 85 / 7%); }
.task-learning-material__hero :deep(.el-card__body) { display: flex; align-items: end; justify-content: space-between; gap: 34px; padding: 34px 38px; border-radius: 12px; background: linear-gradient(135deg, var(--student-material-surface-start), var(--student-material-surface-end)); }
.task-learning-material__eyebrow, .task-learning-material__action-copy > span { color: var(--el-color-primary); font-size: 11px; font-weight: 800; letter-spacing: .13em; }
.task-learning-material h2 { margin: 9px 0 10px; color: var(--el-text-color-primary); font-size: 30px; letter-spacing: -.02em; }
.task-learning-material p { margin: 0; color: var(--el-text-color-secondary); line-height: 1.9; white-space: pre-line; }
.task-learning-material__hero-meta { display: grid; flex: 1 1 25%; min-width: 0; gap: 16px; padding: 5px 0 5px 28px; border-left: 1px solid var(--student-material-divider); color: var(--el-text-color-regular); font-size: 14px; }
.task-learning-material__hero-meta span { display: grid; gap: 4px; }
.task-learning-material__hero-meta b { color: var(--el-text-color-secondary); font-size: 12px; font-weight: 500; }
.task-learning-material__section :deep(.el-card__header) { padding: 18px 26px; border-bottom-color: var(--el-border-color-lighter); }
.task-learning-material__section :deep(.el-card__body) { padding: 24px 26px 28px; }
.task-learning-material__section-title { display: flex; align-items: center; gap: 12px; color: var(--el-text-color-primary); font-size: 17px; }
.task-learning-material__section-title i { color: var(--el-color-primary); font-family: ui-monospace, monospace; font-size: 12px; font-style: normal; }
.task-learning-material__content { font-size: 16px; color: var(--el-text-color-regular); }
.task-learning-material__image { display: block; max-width: 100%; margin-top: 20px; border-radius: 8px; }
.task-learning-material__action { overflow: hidden; background: var(--student-material-action-bg); }
.task-learning-material__action :deep(.el-card__body) { display: flex; align-items: center; justify-content: space-between; gap: 30px; padding: 26px 30px; }
.task-learning-material__action-copy > span { color: #9fc7f5; }
.task-learning-material__action h3 { margin: 5px 0; color: #fff; font-size: 20px; }
.task-learning-material__action p { color: var(--student-material-action-muted); }
.task-learning-material__action :deep(.el-button) { flex: 1 1 20%; min-width: 0; border: 0; font-weight: 700; }
@media (max-width: 640px) { .task-learning-material { padding-top: 4px; } .task-learning-material__hero :deep(.el-card__body), .task-learning-material__action :deep(.el-card__body) { align-items: stretch; flex-direction: column; padding: 26px 22px; } .task-learning-material__hero-meta { padding: 16px 0 0; border-top: 1px solid var(--student-material-divider); border-left: 0; } .task-learning-material h2 { font-size: 24px; } .task-learning-material__action :deep(.el-button) { width: 100%; } }
</style>
