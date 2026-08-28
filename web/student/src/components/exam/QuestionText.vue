<template>
  <span class="question-text" v-html="renderedContent" />
</template>

<script setup lang="ts">
import { computed } from 'vue';
import 'katex/dist/katex.min.css';
import { renderQuestionText } from '@/utils/question-text';

const props = defineProps<{ content: string }>();
const renderedContent = computed(() => renderQuestionText(props.content));
</script>

<style scoped lang="scss">
.question-text {
  white-space: pre-wrap;
}
.question-text :deep(.katex-display) {
  max-width: 100%;
  margin: 0.6em 0;
  overflow-x: auto;
  overflow-y: hidden;
}
.question-text :deep(.katex) {
  white-space: normal;
}
</style>
