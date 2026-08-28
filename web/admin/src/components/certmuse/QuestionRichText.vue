<template>
  <div class="question-rich-text" v-html="renderedContent" />
</template>

<script setup lang="ts">
import { computed } from 'vue';
import 'katex/dist/katex.min.css';
import { renderQuestionRichText } from '@/utils/question-rich-text';

const props = defineProps<{ content: string }>();
const renderedContent = computed(() => renderQuestionRichText(props.content));
</script>

<style scoped lang="scss">
.question-rich-text {
  overflow-x: auto;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.question-rich-text :deep(p) {
  margin: 0 0 0.75em;
}
.question-rich-text :deep(table) {
  width: 100%;
  min-width: max-content;
  border-collapse: collapse;
}
.question-rich-text :deep(th),
.question-rich-text :deep(td) {
  padding: 8px 10px;
  border: 1px solid var(--el-border-color);
  vertical-align: top;
}
.question-rich-text :deep(th) {
  background: var(--el-fill-color-light);
  font-weight: 600;
}
.question-rich-text :deep(img),
.question-rich-text :deep(video) {
  display: block;
  max-width: 100%;
  height: auto;
}
.question-rich-text :deep(.katex-display) {
  max-width: 100%;
  margin: 0.6em 0;
  overflow-x: auto;
  overflow-y: hidden;
}
.question-rich-text :deep(.katex) {
  white-space: normal;
}
</style>
