<template>
  <div class="public-page">
    <el-card shadow="never" class="empty-card">
      <el-empty :image-size="120">
        <template #description><div class="empty-description"><h1>{{ title }}</h1><p>{{ description }}</p></div></template>
        <el-tag type="info" effect="plain">页面已建立 · 内容待接入</el-tag>
      </el-empty>
    </el-card>
  </div>
</template>

<script setup lang="ts">
defineProps<{ title: string; description: string }>();
</script>

<style scoped lang="scss">
.public-page { width: min(1180px, calc(100% - 40px)); margin: 0 auto; padding: 40px 0; }.empty-card { min-height: calc(100vh - 152px); border-color: #e7ecf3; }.empty-card :deep(.el-card__body) { display: grid; min-height: calc(100vh - 234px); place-items: center; }.empty-description { text-align: center; }.empty-description h1 { margin: 0 0 10px; color: var(--el-text-color-primary); font-size: 24px; }.empty-description p { max-width: 520px; margin: 0; color: var(--el-text-color-secondary); line-height: 1.7; }
</style>
