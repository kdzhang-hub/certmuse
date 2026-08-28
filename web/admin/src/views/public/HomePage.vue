<template>
  <div ref="homeRoot" class="home-page">
    <section class="hero-section" aria-labelledby="home-title">
      <div class="content-shell hero-grid">
        <div class="hero-copy">
          <p class="eyebrow">计算机技术与软件专业技术资格（水平）考试</p>
          <h1 id="home-title">认识软考</h1>
          <p>软考是国家统一的计算机专业技术资格考试，评价相关人员的专业能力。</p>
          <router-link class="primary-action" to="/exam-introduction">
            了解软考
            <span aria-hidden="true">→</span>
          </router-link>
        </div>
        <aside class="hero-summary" aria-label="首页内容概览">
          <p>内容概览</p>
          <ol>
            <li>
              <span>01</span>
              <strong>考试与资格</strong>
              <small>了解软考的性质与资格体系</small>
            </li>
            <li>
              <span>02</span>
              <strong>方向与选择</strong>
              <small>从职业方向开始缩小范围</small>
            </li>
            <li>
              <span>03</span>
              <strong>备考与安排</strong>
              <small>确认考试信息后进入学习</small>
            </li>
          </ol>
        </aside>
      </div>
    </section>

    <section class="content-shell qualification-directory reveal" aria-label="资格目录">
      <p class="qualification-directory-title">资格目录</p>
      <details
        v-for="group in qualificationGroups"
        :key="group.level"
        class="qualification-level"
      >
        <summary>
          <span>
            <strong>{{ qualificationLevelLabels[group.level] }}</strong>
            <small>{{ group.items.length }} 项资格</small>
          </span>
          <span class="summary-mark" aria-hidden="true">+</span>
        </summary>
        <div class="qualification-list">
          <router-link
            v-for="item in group.items"
            :key="item.code"
            :to="`/qualifications/${item.code}`"
            class="qualification-item"
          >
            <span>
              <strong>{{ item.name }}</strong>
              <small>{{ directionLabels[item.direction] }}</small>
              <small class="next-exam-date">{{ formatNextOfficialExamDate(item.nearestExamStartDate) }}</small>
            </span>
            <span aria-hidden="true">→</span>
          </router-link>
        </div>
      </details>
    </section>

    <section class="content-shell preparation-section reveal" aria-labelledby="preparation-title">
      <div class="section-heading">
        <p class="eyebrow">备考信息</p>
        <h2 id="preparation-title">从了解进入准备</h2>
        <p>首页不代替详细说明。确认方向后，可以按以下顺序继续浏览；每一步都可以返回、调整或暂缓决定。</p>
      </div>
      <ol class="preparation-list">
        <li>
          <span>1</span>
          <div>
            <h3>查看考试介绍</h3>
            <p>了解资格对应的考试内容、形式与准备重点。</p>
          </div>
          <router-link to="/exam-introduction">考试介绍 →</router-link>
        </li>
        <li>
          <span>2</span>
          <div>
            <h3>浏览资格信息</h3>
            <p>从候选资格的方向、层级和适合人群继续确认。</p>
          </div>
          <router-link to="/qualifications">资格 →</router-link>
        </li>
        <li>
          <span>3</span>
          <div>
            <h3>查看题库与资料</h3>
            <p>先感受学习内容与知识结构，再决定投入方式。</p>
          </div>
          <a href="/learning/question-bank">题库与资料 →</a>
        </li>
        <li>
          <span>4</span>
          <div>
            <h3>核对官方安排</h3>
            <p>报名之前，确认当期考试计划与所在地考试机构通知。</p>
          </div>
          <a href="https://www.ruankao.org.cn/exam/plan.html" target="_blank" rel="noopener noreferrer">官方安排 ↗</a>
        </li>
      </ol>
    </section>

    <section class="fit-section reveal" aria-labelledby="fit-title">
      <div class="content-shell fit-shell">
        <div>
          <p class="eyebrow">选择方向</p>
          <h2 id="fit-title">资格适合度测试</h2>
          <p>
            如果还不能确定关注哪个资格，可以先根据学习阶段、已有基础和工作偏好获得一项方向参考。测试不判断报考资格，不保存答案或结果。
          </p>
        </div>
        <router-link class="fit-action" to="/qualification-fit-test">
          <span>开始测试</span>
          <small>约 5–7 题 · 单选 · 无需登录</small>
          <b aria-hidden="true">→</b>
        </router-link>
      </div>
    </section>

    <section class="official-section reveal" aria-labelledby="official-title">
      <div class="content-shell official-shell">
        <div>
          <p class="eyebrow">官方信息</p>
          <h2 id="official-title">报考信息以当期通知为准</h2>
          <p>
            全国考试安排、当地报名时间、审核要求与报名入口可能分别发布。平台提供理解和浏览入口；提交报名前，请以中国计算机技术职业资格网及所在地考试机构通知为准。
          </p>
        </div>
        <div class="official-links">
          <a href="https://www.ruankao.org.cn/introduction/main.html" target="_blank" rel="noopener noreferrer">
            考试简介
            <span>↗</span>
          </a>
          <a href="https://www.ruankao.org.cn/guide/main.html" target="_blank" rel="noopener noreferrer">
            报考指南与资格设置
            <span>↗</span>
          </a>
          <a href="https://www.ruankao.org.cn/exam/contact.html" target="_blank" rel="noopener noreferrer">
            各地考试机构
            <span>↗</span>
          </a>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup name="PublicHomePage" lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { listPublicQualifications } from '@/api/public/exam-guidance';
import {
  directionLabels,
  formatNextOfficialExamDate,
  qualificationLevelLabels,
  mergeQualificationCatalogue,
  qualifications,
  type QualificationLevel
} from './qualification-fit-config';

const homeRoot = ref<HTMLElement>();
let observer: IntersectionObserver | undefined;
const qualificationProfiles = ref(qualifications);
const qualificationGroups = computed(() =>
  (['LOW', 'MIDDLE', 'HIGH'] as QualificationLevel[]).map(level => ({
    level,
    items: qualificationProfiles.value.filter(item => item.level === level)
  }))
);

onMounted(() => {
  const sections = homeRoot.value?.querySelectorAll<HTMLElement>('.reveal');
  if (!sections || !('IntersectionObserver' in window)) {
    sections?.forEach(section => section.classList.add('is-visible'));
  } else {
    observer = new IntersectionObserver(
      entries => {
        entries.forEach(entry => {
          if (!entry.isIntersecting) return;
          (entry.target as HTMLElement).classList.add('is-visible');
          observer?.unobserve(entry.target);
        });
      },
      { rootMargin: '0px 0px -8%', threshold: 0.1 }
    );
    sections.forEach(section => observer?.observe(section));
  }

  listPublicQualifications()
    .then(catalogue => {
      qualificationProfiles.value = mergeQualificationCatalogue(catalogue.data);
    })
    .catch(() => {
      // 保留内置目录，公共接口暂不可用时首页仍可浏览资格。
    });
});

onBeforeUnmount(() => observer?.disconnect());
</script>

<style scoped lang="scss">
.home-page {
  overflow: hidden;
  color: #23364d;
  background: #fff;
}
.content-shell {
  width: min(1120px, calc(100% - 40px));
  margin: 0 auto;
}
.hero-section {
  padding: clamp(56px, 8vw, 88px) 0 32px;
  background:
    radial-gradient(circle at 84% 20%, rgb(185 215 246 / 65%), transparent 25%),
    linear-gradient(135deg, #f8fbff, #eef6fc);
}
.hero-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(300px, 360px);
  gap: clamp(40px, 7vw, 78px);
  align-items: center;
}
.eyebrow {
  margin: 0 0 11px;
  color: #3477bc;
  font-size: 13px;
  font-weight: 750;
  letter-spacing: 0.08em;
}
.hero-copy h1 {
  margin: 0 0 18px;
  color: #162940;
  font-size: clamp(46px, 6vw, 68px);
  letter-spacing: -0.055em;
  line-height: 1.08;
}
.hero-copy > p:not(.eyebrow) {
  max-width: 630px;
  margin: 0;
  color: #536b86;
  font-size: 17px;
  line-height: 1.85;
}
.primary-action {
  display: inline-flex;
  gap: 9px;
  align-items: center;
  min-height: 46px;
  padding: 0 19px;
  margin-top: 29px;
  color: #fff;
  border-radius: 8px;
  background: #2878d8;
  box-shadow: 0 10px 24px rgb(40 120 216 / 22%);
  font-weight: 700;
  text-decoration: none;
}
.hero-summary {
  padding: 27px 28px;
  border: 1px solid #d7e6f5;
  border-radius: 15px;
  background: rgb(255 255 255 / 86%);
  box-shadow: 0 20px 48px rgb(34 78 126 / 10%);
}
.hero-summary > p {
  margin: 0 0 18px;
  color: #4c7299;
  font-size: 13px;
  font-weight: 750;
}
.hero-summary ol {
  display: grid;
  gap: 15px;
  padding: 0;
  margin: 0;
  list-style: none;
}
.hero-summary li {
  display: grid;
  grid-template-columns: 30px 1fr;
  column-gap: 10px;
}
.hero-summary span {
  color: #77a9db;
  font-size: 12px;
  font-weight: 800;
}
.hero-summary strong {
  color: #264363;
  font-size: 15px;
}
.hero-summary small {
  grid-column: 2;
  margin-top: 3px;
  color: #73859a;
  font-size: 13px;
  line-height: 1.55;
}
.preparation-section {
  padding: 72px 0;
}
.section-heading {
  max-width: 750px;
  margin-bottom: 31px;
}
.section-heading h2,
.fit-section h2,
.official-section h2 {
  margin: 0 0 13px;
  color: #182a42;
  font-size: clamp(26px, 3.2vw, 32px);
  letter-spacing: -0.035em;
  line-height: 1.25;
}
.section-heading > p:not(.eyebrow),
.fit-section p:not(.eyebrow),
.official-section p:not(.eyebrow) {
  margin: 0;
  color: #60758c;
  line-height: 1.8;
}
.qualification-directory {
  padding: 58px 0 24px;
}
.qualification-directory + .preparation-section {
  padding-top: 32px;
}
.qualification-directory-title {
  margin: 0 0 26px;
  color: #182a42;
  font-size: clamp(28px, 3.4vw, 40px);
  font-weight: 750;
  letter-spacing: -0.035em;
  line-height: 1.25;
}
.qualification-level {
  margin-top: 12px;
  overflow: hidden;
  border: 1px solid #d9e6f1;
  border-radius: 10px;
  background: #fff;
}
.qualification-level summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 19px 21px;
  color: #213b59;
  cursor: pointer;
  list-style: none;
}
.qualification-level summary::-webkit-details-marker {
  display: none;
}
.qualification-level summary strong,
.qualification-level summary small {
  display: block;
}
.qualification-level summary strong {
  font-size: 17px;
}
.qualification-level summary small {
  margin-top: 4px;
  color: #71859a;
  font-size: 13px;
}
.summary-mark {
  color: #4c88c5;
  font-size: 23px;
  font-weight: 400;
  transition: transform 0.18s ease;
}
.qualification-level[open] .summary-mark {
  transform: rotate(45deg);
}
.qualification-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  border-top: 1px solid #e3edf5;
}
.qualification-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 76px;
  padding: 14px 20px;
  color: #3276ba;
  border-right: 1px solid #e3edf5;
  border-bottom: 1px solid #e3edf5;
  text-decoration: none;
}
.qualification-item:nth-child(2n) {
  border-right: 0;
}
.qualification-item:nth-last-child(-n + 2) {
  border-bottom: 0;
}
.qualification-item:hover {
  background: #f3f8fe;
}
.qualification-item strong,
.qualification-item small {
  display: block;
}
.qualification-item strong {
  color: #294767;
  font-size: 15px;
}
.qualification-item small {
  margin-top: 5px;
  color: #718398;
  font-size: 12px;
  line-height: 1.5;
}
.qualification-item .next-exam-date {
  color: #4c88c5;
}
.fit-section {
  padding: 65px 0;
  color: #eff6fd;
  background: #1f3855;
}
.fit-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(270px, 340px);
  gap: 55px;
  align-items: center;
}
.fit-section .eyebrow {
  color: #a3c7ee;
}
.fit-section h2 {
  color: #fff;
}
.fit-section p:not(.eyebrow) {
  color: #c6d7e9;
}
.fit-action {
  display: grid;
  grid-template-columns: 1fr auto;
  row-gap: 5px;
  align-items: center;
  padding: 21px 22px;
  color: #eff6fd;
  border: 1px solid rgb(202 225 249 / 34%);
  border-radius: 10px;
  background: rgb(255 255 255 / 6%);
  text-decoration: none;
}
.fit-action span {
  font-size: 18px;
  font-weight: 750;
}
.fit-action small {
  color: #b4c9de;
  font-size: 12px;
}
.fit-action b {
  grid-row: 1 / span 2;
  grid-column: 2;
  font-size: 23px;
  font-weight: 400;
}
.preparation-list {
  padding: 0;
  margin: 0;
  list-style: none;
  border-top: 1px solid #dce7f1;
}
.preparation-list li {
  display: grid;
  grid-template-columns: 33px minmax(0, 1fr) auto;
  gap: 17px;
  align-items: center;
  padding: 19px 0;
  border-bottom: 1px solid #dce7f1;
}
.preparation-list > li > span {
  color: #72a7dc;
  font-size: 13px;
  font-weight: 800;
}
.preparation-list h3 {
  margin: 0 0 4px;
  color: #2a4663;
  font-size: 16px;
}
.preparation-list p {
  margin: 0;
  color: #718398;
  font-size: 14px;
  line-height: 1.6;
}
.preparation-list a {
  color: #2878d8;
  font-size: 14px;
  font-weight: 700;
  text-decoration: none;
  white-space: nowrap;
}
.official-section {
  padding: 61px 0;
  background: #eef4fa;
}
.official-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(300px, 390px);
  gap: 65px;
  align-items: center;
}
.official-links {
  display: grid;
  gap: 9px;
}
.official-links a {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 13px 15px;
  color: #315578;
  border: 1px solid #d5e3f0;
  border-radius: 8px;
  background: rgb(255 255 255 / 68%);
  font-size: 14px;
  font-weight: 700;
  text-decoration: none;
}
.official-links span {
  color: #4b8bd0;
}
.reveal {
  opacity: 0;
  transform: translateY(26px);
  will-change: opacity, transform;
  transition:
    opacity 0.62s ease-out,
    transform 0.62s cubic-bezier(0.22, 1, 0.36, 1);
}
.reveal.is-visible {
  opacity: 1;
  transform: translateY(0);
  will-change: auto;
}
html.dark .home-page {
  color: #d8e5f3;
  background: #09111f;
}
html.dark .hero-section {
  background:
    radial-gradient(circle at 84% 20%, rgb(43 102 165 / 34%), transparent 27%),
    linear-gradient(135deg, #101f33, #0b1727);
}
html.dark .eyebrow,
html.dark .preparation-list > li > span {
  color: #7db9f4;
}
html.dark .hero-copy h1,
html.dark .section-heading h2,
html.dark .fit-section h2,
html.dark .official-section h2,
html.dark .preparation-list h3 {
  color: #f0f6ff;
}
html.dark .hero-copy > p:not(.eyebrow),
html.dark .section-heading > p:not(.eyebrow),
html.dark .fit-section p:not(.eyebrow),
html.dark .official-section p:not(.eyebrow),
html.dark .preparation-list p {
  color: #a8bad0;
}
html.dark .primary-action {
  background: #2b6fd0;
  box-shadow: 0 10px 24px rgb(20 79 149 / 28%);
}
html.dark .hero-summary {
  border-color: #2d4661;
  background: #111f33;
  box-shadow: 0 18px 40px rgb(0 0 0 / 18%);
}
html.dark .hero-summary > p {
  color: #9fc5eb;
}
html.dark .hero-summary strong {
  color: #e7f1fc;
}
html.dark .hero-summary small {
  color: #a1b4c9;
}
html.dark .qualification-level {
  border-color: #2d4965;
  background: #111f33;
}
html.dark .qualification-directory-title {
  color: #f0f6ff;
}
html.dark .qualification-level summary strong,
html.dark .qualification-item strong {
  color: #f0f6ff;
}
html.dark .qualification-level summary small,
html.dark .qualification-item small {
  color: #a8bad0;
}
html.dark .qualification-item .next-exam-date {
  color: #7db9f4;
}
html.dark .qualification-list,
html.dark .qualification-item {
  border-color: #2c455f;
}
html.dark .qualification-item:hover {
  background: #162b43;
}
html.dark .fit-section {
  background: #142d49;
}
html.dark .fit-action {
  border-color: rgb(150 201 250 / 36%);
  background: rgb(255 255 255 / 8%);
}
html.dark .preparation-list,
html.dark .preparation-list li {
  border-color: #2c455f;
}
html.dark .official-section {
  background: #0e1d30;
}
html.dark .official-links a {
  color: #c5ddf4;
  border-color: #2d4965;
  background: rgb(17 33 52 / 80%);
}
html.dark .official-links span,
html.dark .preparation-list a {
  color: #79b9f6;
}
@media (prefers-reduced-motion: reduce) {
  .reveal {
    opacity: 1;
    transform: none;
    transition: none;
  }
  .level-card {
    transition: none;
  }
}
@media (max-width: 860px) {
  .hero-grid,
  .fit-shell,
  .official-shell {
    grid-template-columns: 1fr;
    gap: 32px;
  }
}
@media (max-width: 640px) {
  .content-shell {
    width: min(100% - 28px, 1120px);
  }
  .hero-section {
    padding: 49px 0 28px;
  }
  .hero-copy h1 {
    font-size: 47px;
  }
  .preparation-section {
    padding: 54px 0;
  }
  .qualification-directory {
    padding: 46px 0 28px;
  }
  .qualification-directory-title {
    margin-bottom: 22px;
  }
  .qualification-directory + .preparation-section {
    padding-top: 34px;
  }
  .qualification-list {
    grid-template-columns: 1fr;
  }
  .qualification-item,
  .qualification-item:nth-child(2n),
  .qualification-item:nth-last-child(-n + 2) {
    border-right: 0;
    border-bottom: 1px solid #e3edf5;
  }
  .qualification-item:last-child {
    border-bottom: 0;
  }
  .fit-section {
    padding: 53px 0;
  }
  .preparation-list li {
    grid-template-columns: 25px 1fr;
    gap: 12px;
  }
  .preparation-list a {
    grid-column: 2;
  }
  .official-section {
    padding: 52px 0;
  }
}
</style>
