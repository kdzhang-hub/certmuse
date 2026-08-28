<template>
  <div class="p-2 app-container">
    <el-result icon="error" title="首次学习流程状态异常" sub-title="系统无法确认下一步操作，请刷新后重试。">
      <template #extra><el-button type="primary" @click="router.go(0)">刷新页面</el-button></template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router';

const router = useRouter();
</script>
