<template>
  <div class="detail-page">
    <section class="detail-hero">
      <div class="content-shell">
        <router-link class="back-link" to="/qualifications">← 返回资格目录</router-link>
        <template v-if="profile">
          <p class="eyebrow">{{ qualificationLevelLabels[profile.level] }}</p>
          <h1>{{ profile.name }}</h1>
          <p>{{ profile.suitableFor }}</p>
          <div class="hero-tags">
            <span>{{ directionLabels[profile.direction] }}</span>
            <span v-for="preference in profile.preferences" :key="preference">
              {{ workPreferenceLabels[preference] }}
            </span>
          </div>
        </template>
        <template v-else>
          <p class="eyebrow">资格详情</p>
          <h1>未找到该资格</h1>
          <p>该链接对应的资格不在当前目录中。你可以返回目录重新选择。</p>
        </template>
      </div>
    </section>

    <main v-if="profile" class="content-shell detail-content">
      <section class="detail-overview" aria-labelledby="overview-title">
        <p class="eyebrow">方向说明</p>
        <h2 id="overview-title">适合关注的能力范围</h2>
        <p>{{ profile.suitableFor }}</p>
        <p>
          该资格在本平台的方向归类为“{{
            directionLabels[profile.direction]
          }}”。它用于帮助你理解不同资格与工作兴趣之间的关系，不替代官方考试大纲。
        </p>
      </section>

      <section class="detail-grid" aria-label="资格信息">
        <article>
          <h2>关注内容</h2>
          <ul>
            <li v-for="specialty in profile.specialties" :key="specialty">
              {{ specialtyLabels[specialty] ?? specialty }}
            </li>
          </ul>
        </article>
        <article>
          <h2>工作侧重</h2>
          <ul>
            <li v-for="preference in profile.preferences" :key="preference">{{ workPreferenceLabels[preference] }}</li>
          </ul>
        </article>
        <article class="preparation-card">
          <h2>准备提示</h2>
          <p>{{ profile.preparationNote }}</p>
        </article>
      </section>

      <section class="next-step" aria-labelledby="next-step-title">
        <div>
          <p class="eyebrow">下一步</p>
          <h2 id="next-step-title">确认内容与当期安排</h2>
          <p>决定继续准备前，请结合官方考试大纲、当期考试安排和所在地报名通知确认实际报考要求。</p>
        </div>
        <a href="https://www.ruankao.org.cn/guide/main.html" target="_blank" rel="noopener noreferrer">
          查看官方资格设置 ↗
        </a>
      </section>
    </main>
  </div>
</template>

<script setup name="QualificationDetailPage" lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { listPublicQualifications } from '@/api/public/exam-guidance';
import {
  directionLabels,
  qualificationByCode,
  qualificationLevelLabels,
  mergeQualificationCatalogue,
  specialtyLabels,
  workPreferenceLabels
} from './qualification-fit-config';

const route = useRoute();
const qualificationProfiles = ref<ReturnType<typeof qualificationByCode>[]>([]);
const profile = computed(() => {
  const code = typeof route.params.code === 'string' ? route.params.code : undefined;
  return qualificationProfiles.value.find(item => item?.code === code) ?? qualificationByCode(code);
});

onMounted(async () => {
  try {
    const response = await listPublicQualifications();
    qualificationProfiles.value = mergeQualificationCatalogue(response.data);
  } catch {
    qualificationProfiles.value = [];
  }
});
</script>

<style scoped lang="scss">
.detail-page {
  min-height: 100%;
  color: #23354c;
  background: #fff;
}
.content-shell {
  width: min(1020px, calc(100% - 40px));
  margin: 0 auto;
}
.detail-hero {
  padding: 34px 0 70px;
  background: linear-gradient(135deg, #f5faff, #edf5fc);
}
.back-link {
  display: inline-block;
  margin-bottom: 48px;
  color: #52708f;
  font-size: 14px;
  text-decoration: none;
}
.back-link:hover {
  color: #2878d8;
}
.eyebrow {
  margin: 0 0 12px;
  color: #3677bb;
  font-size: 13px;
  font-weight: 750;
  letter-spacing: 0.08em;
}
.detail-hero h1 {
  margin: 0 0 18px;
  color: #172a43;
  font-size: clamp(36px, 5vw, 56px);
  letter-spacing: -0.04em;
  line-height: 1.15;
}
.detail-hero p:not(.eyebrow) {
  max-width: 720px;
  margin: 0;
  color: #506880;
  font-size: 18px;
  line-height: 1.85;
}
.hero-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 24px;
}
.hero-tags span {
  padding: 6px 10px;
  color: #3679be;
  border-radius: 99px;
  background: #dfeefe;
  font-size: 13px;
  font-weight: 700;
}
.detail-content {
  padding: 76px 0 96px;
}
.detail-overview {
  max-width: 780px;
}
.detail-overview h2,
.detail-grid h2,
.next-step h2 {
  margin: 0 0 16px;
  color: #1b2c43;
  font-size: clamp(25px, 3vw, 32px);
  line-height: 1.3;
}
.detail-overview p {
  margin: 0 0 16px;
  color: #5b6f85;
  line-height: 1.9;
}
.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  margin-top: 54px;
}
.detail-grid article {
  padding: 25px;
  border: 1px solid #dce8f2;
  border-radius: 12px;
  background: #fff;
}
.detail-grid ul {
  display: grid;
  gap: 10px;
  padding: 0;
  margin: 0;
  list-style: none;
}
.detail-grid li {
  color: #5c7187;
  line-height: 1.7;
}
.detail-grid li::before {
  margin-right: 9px;
  color: #4788c7;
  content: '•';
}
.detail-grid .preparation-card {
  grid-column: 1 / -1;
  background: #f3f8fe;
  border-color: #c9def2;
}
.preparation-card p {
  margin: 0;
  color: #56738f;
  line-height: 1.8;
}
.next-step {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 28px;
  padding: 34px;
  margin-top: 54px;
  border-radius: 12px;
  color: #eff6ff;
  background: #1d334d;
}
.next-step .eyebrow {
  color: #a3caef;
}
.next-step h2 {
  color: #fff;
}
.next-step p:not(.eyebrow) {
  max-width: 650px;
  margin: 0;
  color: #c6d8ea;
  line-height: 1.8;
}
.next-step a {
  flex: none;
  padding: 12px 16px;
  color: #fff;
  border: 1px solid rgb(216 232 255 / 32%);
  border-radius: 8px;
  text-decoration: none;
}
.next-step a:hover {
  background: rgb(255 255 255 / 10%);
}
@media (max-width: 640px) {
  .content-shell {
    width: min(100% - 28px, 1020px);
  }
  .detail-hero {
    padding-bottom: 58px;
  }
  .back-link {
    margin-bottom: 36px;
  }
  .detail-hero p:not(.eyebrow) {
    font-size: 16px;
  }
  .detail-content {
    padding: 58px 0;
  }
  .detail-grid {
    grid-template-columns: 1fr;
    margin-top: 42px;
  }
  .detail-grid .preparation-card {
    grid-column: auto;
  }
  .next-step {
    align-items: flex-start;
    flex-direction: column;
    margin-top: 42px;
    padding: 27px;
  }
}
</style>
