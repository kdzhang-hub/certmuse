<template>
  <el-card class="choice-question" shadow="never">
    <div class="choice-question__topline">
      <span class="choice-question__number">{{ questionOrder }}</span>
      <span>{{ isMultiple ? '多选题' : '单选题' }}</span>
      <span class="choice-question__progress">第 {{ questionOrder }} / {{ totalCount }} 题</span>
    </div>
    <div class="choice-question__stem">
      <h3><question-text :content="stem" /></h3>
      <slot name="stem-actions" />
    </div>
    <question-images :images="images" />

    <el-radio-group
      v-if="!isMultiple"
      :model-value="answer[0] ?? ''"
      class="choice-question__answers"
      :disabled="disabled"
      @update:model-value="value => $emit('update:answer', value ? [String(value)] : [])"
    >
      <el-radio
        v-for="option in options"
        :key="option.label"
        :value="option.label"
        class="choice-question__answer"
        border
      >
        <strong>{{ option.label }}</strong>
        <question-text :content="option.content" />
      </el-radio>
    </el-radio-group>
    <el-checkbox-group
      v-else
      :model-value="answer"
      class="choice-question__answers"
      :disabled="disabled"
      @update:model-value="value => $emit('update:answer', value.map(String))"
    >
      <el-checkbox
        v-for="option in options"
        :key="option.label"
        :value="option.label"
        class="choice-question__answer"
        border
      >
        <strong>{{ option.label }}</strong>
        <question-text :content="option.content" />
      </el-checkbox>
    </el-checkbox-group>

    <div class="choice-question__actions"><slot name="actions" /></div>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import QuestionImages from '@/components/exam/QuestionImages.vue';
import QuestionText from '@/components/exam/QuestionText.vue';

const props = defineProps<{
  questionOrder: number;
  totalCount: number;
  selectionMode: string;
  stem: string;
  options: Array<{ label: string; content: string }>;
  images: Array<{ url: string; alt: string | null; sortOrder: number }>;
  answer: string[];
  disabled: boolean;
}>();

const isMultiple = computed(() => props.selectionMode.toLowerCase() === 'multiple');
defineEmits<{ 'update:answer': [value: string[]] }>();
</script>

<style lang="scss" scoped>
.choice-question {
  border-color: var(--el-border-color-light);
  background: var(--el-bg-color);
}
.choice-question__topline {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.choice-question__number {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border-radius: 50%;
  color: var(--el-color-white);
  background: var(--el-color-primary);
  font-weight: 700;
}
.choice-question__progress {
  margin-left: auto;
}
.choice-question__stem {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.choice-question h3 {
  flex: 1;
  margin: 22px 0;
  line-height: 1.8;
  font-size: 16px;
  font-weight: 600;
}
.choice-question__answers {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 12px;
}
.choice-question__answers :deep(.choice-question__answer) {
  display: flex;
  height: auto;
  min-height: 52px;
  margin: 0;
  padding: 12px 16px;
  white-space: normal;
  line-height: 1.65;
}
.choice-question__answers :deep(.choice-question__answer.is-checked) {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.choice-question__answers :deep(strong) {
  margin-right: 10px;
}
.choice-question__actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 30px;
}
@media (max-width: 576px) {
  .choice-question__progress {
    display: none;
  }
  .choice-question__stem {
    flex-direction: column;
    gap: 0;
  }
}
</style>
