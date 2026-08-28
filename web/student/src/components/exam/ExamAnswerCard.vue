<template>
  <el-card class="exam-answer-card" shadow="never">
    <template #header>
      <div class="exam-answer-card__title">
        <strong>答题卡</strong>
        <span>{{ answeredCount }}/{{ totalCount }}</span>
      </div>
    </template>
    <div class="exam-answer-card__legend">
      <span>
        <i class="exam-answer-card__dot exam-answer-card__dot--current" />
        当前
      </span>
      <span v-if="showAnswerResults">
        <i class="exam-answer-card__dot exam-answer-card__dot--answered" />
        正确
      </span>
      <span v-else>
        <i class="exam-answer-card__dot exam-answer-card__dot--answered" />
        已答
      </span>
      <span v-if="showAnswerResults">
        <i class="exam-answer-card__dot exam-answer-card__dot--incorrect" />
        错误
      </span>
      <span>
        <i class="exam-answer-card__dot" />
        未答
      </span>
    </div>
    <div class="exam-answer-card__grid">
      <button
        v-for="item in navigation"
        :key="item.questionOrder"
        class="exam-answer-card__item"
        :class="{
          'is-current': item.questionOrder === currentQuestionOrder,
          'is-answered': answered[item.questionOrder],
          'is-correct': answerResults?.[item.questionOrder] === 'correct',
          'is-incorrect': answerResults?.[item.questionOrder] === 'incorrect'
        }"
        :disabled="disabled"
        :aria-label="`第 ${item.questionOrder} 题，${answerLabel(item.questionOrder)}`"
        @click="$emit('select', item.questionOrder)"
      >
        {{ item.questionOrder }}
      </button>
    </div>
    <el-divider v-if="showFinish" />
    <el-button v-if="showFinish" class="exam-answer-card__finish" type="danger" plain :disabled="disabled" @click="$emit('finish')">
      {{ finishLabel }}
    </el-button>
  </el-card>
</template>

<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    totalCount: number;
    answeredCount: number;
    currentQuestionOrder: number;
    navigation: Array<{ questionOrder: number }>;
    answered: Record<number, boolean>;
    answerResults?: Record<number, 'correct' | 'incorrect'>;
    showAnswerResults?: boolean;
    disabled: boolean;
    finishLabel: string;
    showFinish?: boolean;
  }>(),
  {
    answerResults: () => ({}),
    showAnswerResults: false,
    showFinish: true
  }
);

function answerLabel(questionOrder: number) {
  if (questionOrder === props.currentQuestionOrder) return '当前题';
  if (props.answerResults[questionOrder] === 'correct') return '正确';
  if (props.answerResults[questionOrder] === 'incorrect') return '错误';
  return props.answered[questionOrder] ? '已答' : '未答';
}
defineEmits<{ select: [questionOrder: number]; finish: [] }>();
</script>

<style lang="scss" scoped>
.exam-answer-card__title {
  display: flex;
  justify-content: space-between;
}
.exam-answer-card__title span,
.exam-answer-card__legend {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.exam-answer-card__legend {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.exam-answer-card__dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 4px;
  border: 1px solid var(--el-border-color);
}
.exam-answer-card__dot--current {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary);
}
.exam-answer-card__dot--answered {
  border-color: var(--el-color-success);
  background: var(--el-color-success);
}
.exam-answer-card__dot--incorrect {
  border-color: var(--el-color-danger);
  background: var(--el-color-danger);
}
.exam-answer-card__grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
  margin-top: 16px;
}
.exam-answer-card__item {
  height: 36px;
  border: 1px solid var(--el-border-color);
  background: var(--el-fill-color-blank);
  cursor: pointer;
}
.exam-answer-card__item.is-current {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary);
  color: var(--el-color-white);
}
.exam-answer-card__item.is-answered:not(.is-current) {
  border-color: var(--el-color-success-light-5);
  background: var(--el-color-success-light-9);
  color: var(--el-color-success-dark-2);
}
.exam-answer-card__item.is-incorrect:not(.is-current) {
  border-color: var(--el-color-danger-light-5);
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger-dark-2);
}
.exam-answer-card__finish {
  width: 100%;
}
@media (max-width: 992px) {
  .exam-answer-card__grid {
    grid-template-columns: repeat(10, minmax(0, 1fr));
  }
}
@media (max-width: 576px) {
  .exam-answer-card__grid {
    grid-template-columns: repeat(5, minmax(0, 1fr));
  }
}
</style>
