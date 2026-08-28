<template>
  <div class="task-session-steps" aria-label="今日任务进度">
    <span class="task-session-steps__item" :class="{ 'is-active': phase === 'study', 'is-complete': phase === 'practice' }">
      <i>1</i><span><small>第一步</small>教材学习</span>
    </span>
    <span class="task-session-steps__line" :class="{ 'is-complete': phase === 'practice' }" />
    <span class="task-session-steps__item" :class="{ 'is-active': phase === 'practice' }">
      <i>2</i><span><small>第二步</small>题目训练</span>
    </span>
  </div>
</template>

<script setup lang="ts">
defineProps<{ phase: 'study' | 'practice' }>();
</script>

<style scoped lang="scss">
.task-session-steps { display: flex; align-items: center; width: 100%; margin: 12px auto 26px; color: var(--el-text-color-secondary); }
.task-session-steps__item { display: flex; align-items: center; flex: 1 1 0; gap: 10px; min-width: 0; font-size: 15px; font-weight: 700; }
.task-session-steps__item i { display: grid; width: 32px; height: 32px; place-items: center; border: 1px solid var(--el-border-color); border-radius: 50%; background: var(--el-fill-color-lighter); color: var(--el-text-color-secondary); font-style: normal; }
.task-session-steps__item span { display: grid; gap: 2px; }
.task-session-steps__item small { font-size: 11px; font-weight: 500; }
.task-session-steps__line { height: 1px; flex: 1; margin: 0 18px; background: var(--el-border-color); }
.task-session-steps__item.is-active { color: var(--el-color-primary); }
.task-session-steps__item.is-active i { border-color: var(--el-color-primary); background: var(--el-color-primary); color: #fff; box-shadow: 0 5px 14px rgb(64 158 255 / 28%); }
.task-session-steps__item.is-complete i, .task-session-steps__line.is-complete { border-color: var(--el-color-success); background: var(--el-color-success); color: #fff; }
@media (max-width: 576px) { .task-session-steps { margin: 8px 0 20px; } .task-session-steps__item { min-width: 0; flex: 1; font-size: 13px; } .task-session-steps__item i { width: 28px; height: 28px; } .task-session-steps__line { margin: 0 8px; } }
</style>
