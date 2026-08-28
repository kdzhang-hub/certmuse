<template>
  <div class="p-2 app-container student-result">
    <el-card shadow="hover">
      <el-result
        icon="success"
        :title="`${resultTitle}已完成`"
        sub-title="答题记录已保存，下一步学习会根据这次记录安排。"
      >
        <template #extra>
          <el-row :gutter="16" class="result-stats">
            <el-col :xs="24" :sm="8"><el-statistic title="已完成" :value="answeredCount" suffix="题" /></el-col>
            <el-col :xs="24" :sm="8"><el-statistic title="本次答对" :value="correctCount" suffix="题" /></el-col>
            <el-col :xs="24" :sm="8"><el-statistic title="建议回顾" :value="reviewCount" suffix="题" /></el-col>
          </el-row>
          <el-alert class="next-alert" type="info" :closable="false" show-icon :title="nextAdvice" />
          <div class="result-actions">
            <el-button type="primary" @click="goNext">{{ primaryLabel }}</el-button>
            <el-button @click="router.push('/learning/home')">返回学习首页</el-button>
          </div>
        </template>
      </el-result>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import type { StudentSessionKind } from '@/api/student/types';

const route = useRoute();
const router = useRouter();
const kind = computed(() => String(route.params.kind) as StudentSessionKind);
const resultTitle = computed(() => String(route.query.title ?? '本次练习'));
const numberQuery = (name: string) => Number(route.query[name] ?? 0);
const answeredCount = computed(() => numberQuery('answered'));
const correctCount = computed(() => numberQuery('correct'));
const reviewCount = computed(() => numberQuery('review'));
const primaryLabel = computed(() => (kind.value === 'verification' ? '查看学习进度' : '查看错题复习'));
const nextAdvice = computed(() =>
  kind.value === 'verification' ? '验证结果会帮助更新学习进度。' : '先查看需要回顾的题目，再按计划进入下一项学习。'
);
const goNext = () => router.push(kind.value === 'verification' ? '/learning/progress' : '/learning/mistakes');
</script>

<style lang="scss" scoped>
.result-stats {
  width: 100%;
  margin: 8px auto 0;
  text-align: left;
}

.next-alert {
  width: 100%;
  margin: 28px auto 0;
  text-align: left;
}

.result-actions {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 24px;
}
</style>
