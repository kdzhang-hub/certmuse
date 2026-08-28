<template>
  <div class="focus-exam-shell">
    <slot name="steps" />
    <el-card class="focus-exam-shell__header" shadow="never">
      <div class="focus-exam-shell__header-content">
        <div>
          <div class="focus-exam-shell__eyebrow">{{ title }} · 共 {{ totalCount }} 题</div>
          <h2>{{ description }}</h2>
          <p>
            已答 {{ answeredCount }} 题 · 未答 {{ unansweredCount }} 题 ·
            <span v-if="mode === 'EXAM'" class="focus-exam-shell__timer" :class="{ 'focus-exam-shell__timer--overtime': isOvertime }">
              {{ timerLabel }} {{ formattedTimer }}
            </span>
            <span v-if="saveText" class="focus-exam-shell__save" :class="`focus-exam-shell__save--${saveState}`">
              {{ saveText }}
            </span>
          </p>
        </div>
        <div class="focus-exam-shell__header-actions">
          <el-button text :disabled="saving" @click="$emit('exit')">{{ exitLabel }}</el-button>
          <el-button v-if="mode === 'EXAM'" :disabled="saving || paused" @click="$emit('pause')">暂停考试</el-button>
        </div>
      </div>
    </el-card>

    <div class="focus-exam-shell__body">
      <main class="focus-exam-shell__question"><slot /></main>
      <aside class="focus-exam-shell__navigation" aria-label="答题卡"><slot name="navigation" /></aside>
    </div>

    <el-dialog
      :model-value="paused"
      class="focus-exam-shell__pause-dialog"
      width="420px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
      :append-to-body="true"
    >
      <div v-if="mode === 'EXAM'" class="focus-exam-shell__pause-content">
        <el-icon class="focus-exam-shell__pause-icon"><VideoPause /></el-icon>
        <h3>考试已暂停</h3>
        <p>答题与计时已停止。</p>
        <el-button type="primary" size="large" :disabled="saving" @click="$emit('resume')">继续答题</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { VideoPause } from '@element-plus/icons-vue';

withDefaults(
  defineProps<{
    title: string;
    description: string;
    mode?: 'EXAM' | 'PRACTICE';
    totalCount: number;
    answeredCount: number;
    unansweredCount: number;
    timerLabel: string;
    formattedTimer: string;
    isOvertime: boolean;
    paused: boolean;
    saving: boolean;
    saveText?: string;
    saveState?: 'saved' | 'saving' | 'failed';
    exitLabel?: string;
  }>(),
  { mode: 'EXAM', saveText: '', saveState: 'saved', exitLabel: '退出答题' }
);

defineEmits<{ pause: []; resume: []; exit: [] }>();
</script>

<style lang="scss" scoped>
.focus-exam-shell {
  min-height: 100vh;
  padding: 8px;
}
.focus-exam-shell__header,
.focus-exam-shell__navigation :deep(.el-card) {
  border-color: var(--el-border-color-light);
  background: var(--el-bg-color);
}
.focus-exam-shell__header-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}
.focus-exam-shell__header-actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}
.focus-exam-shell__eyebrow {
  color: var(--el-color-primary);
  font-size: 13px;
  font-weight: 600;
}
.focus-exam-shell h2 {
  margin: 8px 0;
  font-size: 18px;
}
.focus-exam-shell p {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.focus-exam-shell__timer {
  color: var(--el-color-primary);
  font-variant-numeric: tabular-nums;
  font-weight: 700;
}
.focus-exam-shell__timer--overtime {
  color: var(--el-color-danger);
}
.focus-exam-shell__save {
  margin-left: 8px;
}
.focus-exam-shell__save--saved {
  color: var(--el-color-success);
}
.focus-exam-shell__save--saving {
  color: var(--el-color-primary);
}
.focus-exam-shell__save--failed {
  color: var(--el-color-danger);
}
.focus-exam-shell__body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 16px;
  margin-top: 16px;
  align-items: start;
}
.focus-exam-shell__navigation {
  position: sticky;
  top: 16px;
}
.focus-exam-shell__pause-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px 8px 20px;
  text-align: center;
}
.focus-exam-shell__pause-icon {
  margin-bottom: 12px;
  color: var(--el-color-warning);
  font-size: 42px;
}
.focus-exam-shell__pause-content h3 {
  margin: 0;
  font-size: 20px;
}
.focus-exam-shell__pause-content p {
  margin: 12px 0 24px;
  color: var(--el-text-color-secondary);
}
@media (max-width: 992px) {
  .focus-exam-shell__body {
    grid-template-columns: 1fr;
  }
  .focus-exam-shell__navigation {
    position: static;
  }
}
@media (max-width: 576px) {
  .focus-exam-shell__header-content {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
