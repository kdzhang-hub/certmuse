<template>
  <div v-if="renderedImages.length" class="question-images">
    <el-image
      v-for="image in renderedImages"
      :key="`${image.sortOrder}-${image.url}`"
      :src="image.url"
      :alt="image.alt ?? '题目图片'"
      fit="contain"
      :preview-src-list="previewImageUrls"
      preview-teleported
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { toQuestionImageUrl } from '@/utils/question-image';

const props = defineProps<{
  images: Array<{ url: string; alt: string | null; sortOrder: number }>;
}>();

const renderedImages = computed(() =>
  props.images
    .toSorted((left, right) => left.sortOrder - right.sortOrder)
    .map(image => ({ ...image, url: toQuestionImageUrl(image.url) }))
);
const previewImageUrls = computed(() => renderedImages.value.map(image => image.url));
</script>

<style lang="scss" scoped>
.question-images {
  display: grid;
  gap: 12px;
  margin: 0 0 20px;
}
.question-images :deep(.el-image) {
  display: block;
  width: 100%;
  max-width: 960px;
}
.question-images :deep(.el-image__inner) {
  display: block;
  width: 100%;
  height: auto;
  max-height: 640px;
  object-fit: contain;
}
</style>
