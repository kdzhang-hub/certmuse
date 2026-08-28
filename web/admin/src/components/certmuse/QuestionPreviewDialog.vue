<template>
  <el-dialog :model-value="modelValue" width="min(720px, calc(100vw - 32px))" @update:model-value="emit('update:modelValue', $event)">
    <template #header>
      <div class="preview-dialog-header">
        <span class="preview-dialog-title">题目预览</span>
        <div v-if="reviewable" class="review-actions">
          <el-button type="success" :loading="processing" @click="emit('approve')">通过</el-button>
          <el-button type="danger" plain :disabled="processing" @click="emit('reject')">驳回</el-button>
        </div>
      </div>
    </template>
    <div v-loading="loading" class="preview-shell">
      <template v-if="question">
        <el-alert
          v-if="question.reviewOpinion"
          class="review-opinion"
          title="驳回原因"
          :description="question.reviewOpinion"
          type="error"
          :closable="false"
          show-icon
        />
        <section class="preview-content">
          <h4>题干</h4>
          <QuestionRichText :content="previewStem" />
          <template v-if="previewImages.length">
            <h4>题目图片</h4>
            <div class="question-images">
              <figure v-for="image in previewImages" :key="`${image.sortOrder}-${image.url}`">
                <el-image :src="image.url" :alt="image.alt" fit="contain" :preview-src-list="previewImageUrls" preview-teleported />
                <figcaption>{{ image.alt || `图片 ${image.sortOrder}` }}</figcaption>
              </figure>
            </div>
          </template>
          <template v-if="question.options.length">
            <h4>选项</h4>
            <ol class="question-options">
              <li v-for="option in question.options" :key="option.label"><strong>{{ option.label }}.</strong><QuestionRichText :content="option.content" /></li>
            </ol>
          </template>
          <h4>答案与解析</h4>
          <QuestionRichText :content="answerText" />
          <QuestionRichText class="analysis" :content="question.analysis || '尚未填写解析'" />
        </section>
      </template>
      <el-empty v-else-if="!loading" description="暂无题目详情" />
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { QuestionPreviewVO } from '@/api/certmuse/question/types';
import { formatQuestionPreviewStem, toQuestionImageUrl } from '@/api/certmuse/question/image-url';
import QuestionRichText from './QuestionRichText.vue';

const props = withDefaults(defineProps<{ modelValue: boolean; question?: QuestionPreviewVO; loading?: boolean; reviewable?: boolean; processing?: boolean }>(), { loading: false, reviewable: false, processing: false });
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; approve: []; reject: [] }>();
const previewStem = computed(() => formatQuestionPreviewStem(props.question?.stem ?? ''));
const previewImages = computed(() => (props.question?.images ?? []).toSorted((a, b) => a.sortOrder - b.sortOrder).map(image => ({ ...image, url: toQuestionImageUrl(image.url) })));
const previewImageUrls = computed(() => previewImages.value.map(image => image.url));
const answerText = computed(() => {
  const value = props.question?.answer.value;
  return (Array.isArray(value) ? value.join('、') : value) || '尚未填写答案';
});
</script>

<style scoped lang="scss">
.preview-shell { min-height: 160px; }
.preview-dialog-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding-right: 36px; }
.preview-dialog-title { color: var(--el-text-color-primary); font-size: var(--el-font-size-large); font-weight: 600; }
.review-actions { display: flex; flex: none; gap: 8px; }
.review-opinion { margin-bottom: 16px; }
.review-opinion :deep(.el-alert__description) { line-height: 1.6; white-space: pre-wrap; overflow-wrap: anywhere; }
.preview-content h4 { margin: 16px 0 8px; }
.question-options { display: grid; gap: 8px; margin: 0; padding: 0; list-style: none; }
.question-options li { display: flex; gap: 6px; padding: 8px 10px; line-height: 1.6; border: 1px solid var(--el-border-color-lighter); border-radius: 6px; background: var(--el-fill-color-lighter); }
.question-options li :deep(.question-rich-text) { flex: 1; min-width: 0; }
.question-options strong { margin-right: 6px; }
.question-images { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 260px), 1fr)); gap: 12px; }
.question-images figure { min-width: 0; margin: 0; padding: 8px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; background: var(--el-fill-color-lighter); }
.question-images :deep(.el-image) { display: block; width: 100%; max-height: 320px; cursor: zoom-in; }
.question-images figcaption { overflow: hidden; margin-top: 7px; color: var(--el-text-color-secondary); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.scoring-points { display: grid; gap: 8px; padding-left: 20px; }
.scoring-points li { line-height: 1.6; }
.scoring-points span { display: block; color: var(--el-text-color-secondary); }
.analysis { color: var(--el-text-color-secondary); }
</style>
