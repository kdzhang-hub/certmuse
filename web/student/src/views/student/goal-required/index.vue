<template>
  <div class="p-2 app-container goal-required-page">
    <el-card shadow="hover" class="goal-required-card">
      <el-result icon="info" title="请先设置学习目标" sub-title="设置目标资格和考试批次后，即可使用此功能。">
        <template #extra>
          <el-button type="primary" @click="router.push('/learning/onboarding')">设置学习目标</el-button>
        </template>
      </el-result>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router';

const router = useRouter();
</script>

<style lang="scss" scoped>
.goal-required-card {
  max-width: 680px;
  margin: 72px auto;
}
</style>
