<template>
  <div class="p-2 app-container certmuse-empty-business-page">
    <el-card shadow="hover" class="empty-page-card">
      <el-empty :image-size="120">
        <template #description>
          <div class="empty-description">
            <h2>{{ title }}</h2>
            <p>{{ description }}</p>
          </div>
        </template>
        <el-tag type="info" effect="plain">页面已建立 · 功能待接入</el-tag>
        <slot name="action" />
      </el-empty>
    </el-card>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string;
  description: string;
}>();
</script>

<style scoped lang="scss">
.empty-page-card {
  min-height: calc(100vh - 154px);

  :deep(.el-card__body) {
    display: grid;
    min-height: calc(100vh - 194px);
    place-items: center;
  }
}

.empty-description {
  text-align: center;

  h2 {
    margin: 0 0 10px;
    color: var(--el-text-color-primary);
    font-size: 22px;
  }

  p {
    max-width: 520px;
    margin: 0;
    color: var(--el-text-color-secondary);
    line-height: 1.7;
  }
}
</style>
