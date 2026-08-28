<template>
  <div class="p-2 app-container question-bank-page">
    <section class="question-bank-hero">
      <div>
        <span class="hero-eyebrow">QUESTION BANK</span>
        <h2>练习，从清晰的目标开始</h2>
        <p>按知识点巩固基础、用真题检验能力，或通过模拟试卷进入实战节奏。</p>
      </div>
      <router-link v-if="isAuthenticated" to="/learning/history" class="history-link">
        <el-button plain class="history-button">
          题目历史
          <el-icon><Clock /></el-icon>
        </el-button>
      </router-link>
    </section>

    <el-card shadow="never" class="entry-panel">
      <div class="page-heading">
        <div>
          <h3>选择练习方式</h3>
          <span>请选择适合当前学习阶段的题库能力</span>
        </div>
        <span class="entry-count">{{ entries.length }} 种练习</span>
      </div>
      <el-row :gutter="20">
        <el-col v-for="item in entries" :key="item.path" :xs="24" :sm="8">
          <a :href="`${basePath}/${item.path}`" class="entry-link" @click.prevent="openEntry(item)">
            <el-card shadow="never" class="entry-card">
              <span class="entry-card__number">0{{ entries.indexOf(item) + 1 }}</span>
              <h3>{{ item.title }}</h3>
              <p>{{ item.description }}</p>
              <span class="entry-card__action">
                {{ isAuthenticated ? item.authenticatedAction : item.public ? '浏览真题' : '登录后使用' }}
                <el-icon><ArrowRight /></el-icon>
              </span>
            </el-card>
          </a>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup name="StudentQuestionBank" lang="ts">
import { useRoute, useRouter } from 'vue-router';
import { getToken } from '@/utils/auth';

const props = withDefaults(defineProps<{ basePath?: string }>(), { basePath: '/learning/question-bank' });
const basePath = props.basePath;
const router = useRouter();
const route = useRoute();
const isAuthenticated = Boolean(getToken());
const entries = [
  {
    title: '知识点练习',
    description: '按知识点选择范围，自主进行专项练习。',
    path: 'knowledge-practice',
    public: false,
    authenticatedAction: '自主练习'
  },
  {
    title: '历年真题',
    description: '无需登录即可浏览已发布真题题面。',
    path: 'past-papers',
    public: true,
    authenticatedAction: '浏览真题'
  },
  {
    title: '模拟试卷',
    description: '按考试规则进行完整模拟。',
    path: 'mock-exams',
    public: false,
    authenticatedAction: '模拟考试'
  }
];

function openEntry(item: (typeof entries)[number]) {
  const target = `${basePath}/${item.path}`;
  if (item.public || isAuthenticated) {
    void router.push(target);
    return;
  }
  void router.push({ path: '/login', query: { redirect: target, reason: 'unlock', from: route.fullPath } });
}
</script>

<style scoped lang="scss">
.question-bank-page {
  width: 100%;
  max-width: none;
  margin: 0;
  padding-top: 20px;
}
.question-bank-hero {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 0;
  padding: 22px 28px;
  overflow: hidden;
  color: #fff;
  border-radius: 18px;
  background:
    radial-gradient(circle at 86% 12%, rgb(255 255 255 / 18%) 0 2px, transparent 2.5px) 0 0 / 23px 23px,
    linear-gradient(118deg, #1e4fc7 0%, #397de8 57%, #64a6f5 100%);
  box-shadow: 0 18px 36px rgb(41 106 209 / 22%);
}
.question-bank-hero::after { position: absolute; right: -82px; bottom: -128px; width: 35%; aspect-ratio: 1; border: 54px solid rgb(255 255 255 / 10%); border-radius: 50%; content: ''; }
.question-bank-hero > * { position: relative; z-index: 1; }
.hero-eyebrow {
  display: block;
  margin-bottom: 9px;
  color: rgba(255, 255, 255, 0.72);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 1.5px;
}
.question-bank-hero h2 {
  margin: 0;
  font-size: 29px;
  letter-spacing: -0.03em;
}
.question-bank-hero p {
  max-width: 620px;
  margin: 10px 0 0;
  color: rgba(255, 255, 255, 0.84);
  font-size: 14px;
  line-height: 1.7;
}
.history-link {
  flex: none;
  text-decoration: none;
}
.history-button {
  color: #285cb7;
  border-color: rgba(255, 255, 255, 0.7);
  background: rgba(255, 255, 255, 0.95);
  font-weight: 600;
}
.history-button .el-icon {
  margin-left: 5px;
}
.entry-panel {
  border: 0;
  border-radius: 18px;
  box-shadow: 0 8px 26px rgba(31, 45, 71, 0.07);
}
.page-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 3px 2px 20px;
}
.page-heading h3 {
  margin: 0 0 7px;
  font-size: 18px;
}
.page-heading span,
.entry-card p {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.entry-count {
  padding: 5px 10px;
  color: var(--el-color-primary) !important;
  border-radius: 999px;
  background: var(--el-color-primary-light-9);
}
.entry-link {
  display: block;
  color: inherit;
  text-decoration: none;
}
.entry-card {
  position: relative;
  min-height: 186px;
  overflow: hidden;
  border-radius: 14px;
  transition:
    border-color 0.2s,
    box-shadow 0.2s,
    transform 0.2s;
}
.entry-card:hover {
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 10px 22px rgba(48, 106, 216, 0.13);
  transform: translateY(-4px);
}
.entry-card__number {
  display: inline-block;
  margin-bottom: 15px;
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 1px;
}
.entry-card h3 {
  margin: 0 0 10px;
  font-size: 18px;
}
.entry-card p {
  min-height: 43px;
  margin: 0;
  line-height: 1.65;
}
.entry-card__action {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 17px;
  color: var(--el-color-primary);
  font-size: 13px;
  font-weight: 600;
}
.entry-card__action .el-icon {
  transition: transform 0.2s;
}
.entry-card:hover .entry-card__action .el-icon {
  transform: translateX(4px);
}
@media (max-width: 640px) {
  .question-bank-hero {
    align-items: flex-start;
    flex-direction: column;
    min-height: 0;
    padding: 24px;
  }
  .question-bank-hero h2 {
    font-size: 24px;
  }
  .page-heading {
    align-items: flex-start;
  }
  .entry-count {
    display: none;
  }
}
</style>
