<template>
  <div class="guide-page">
    <section class="guide-hero" :aria-labelledby="`${page.key}-title`">
      <div class="content-shell">
        <router-link class="back-link" to="/home">← 回到首页</router-link>
        <p class="eyebrow">{{ page.eyebrow }}</p>
        <h1 :id="`${page.key}-title`">{{ page.title }}</h1>
        <p class="hero-description">{{ page.description }}</p>
        <a class="primary-action" :href="page.primaryResource.href" target="_blank" rel="noopener noreferrer">
          {{ page.primaryResource.label }}
          <span aria-hidden="true">↗</span>
        </a>
      </div>
    </section>

    <main class="content-shell guide-content">
      <section v-if="recommendedProfile" class="recommended-profile" aria-labelledby="recommended-profile-title">
        <p class="eyebrow">推荐资格</p>
        <h2 id="recommended-profile-title">{{ recommendedProfile.name }}</h2>
        <p>{{ recommendedProfile.suitableFor }}</p>
        <div>
          <span>{{ levelLabels[recommendedProfile.level] }}</span>
          <span>{{ directionLabels[recommendedProfile.direction] }}</span>
        </div>
        <p class="profile-note">{{ recommendedProfile.preparationNote }}</p>
      </section>
      <section class="overview-section" aria-labelledby="overview-title">
        <p class="eyebrow">基本说明</p>
        <h2 id="overview-title">{{ page.overviewTitle }}</h2>
        <p v-for="paragraph in page.overview" :key="paragraph">{{ paragraph }}</p>
      </section>

      <section class="card-section" :aria-labelledby="`${page.key}-focus-title`">
        <div class="section-heading">
          <p class="eyebrow">{{ page.focusEyebrow }}</p>
          <h2 :id="`${page.key}-focus-title`">{{ page.focusTitle }}</h2>
          <p>{{ page.focusDescription }}</p>
        </div>
        <div class="focus-grid">
          <article v-for="item in page.focusItems" :key="item.title" class="focus-card">
            <span class="item-number">{{ item.number }}</span>
            <h3>{{ item.title }}</h3>
            <p>{{ item.description }}</p>
          </article>
        </div>
      </section>

      <section class="steps-section" :aria-labelledby="`${page.key}-steps-title`">
        <div class="section-heading">
          <p class="eyebrow">准备步骤</p>
          <h2 :id="`${page.key}-steps-title`">{{ page.stepsTitle }}</h2>
          <p>{{ page.stepsDescription }}</p>
        </div>
        <ol class="steps-list">
          <li v-for="item in page.steps" :key="item.number">
            <span>{{ item.number }}</span>
            <div>
              <strong>{{ item.title }}</strong>
              <p>{{ item.description }}</p>
            </div>
          </li>
        </ol>
      </section>

      <section class="faq-section" :aria-labelledby="`${page.key}-faq-title`">
        <div class="section-heading">
          <p class="eyebrow">常见问题</p>
          <h2 :id="`${page.key}-faq-title`">{{ page.faqTitle }}</h2>
        </div>
        <div class="faq-list">
          <article v-for="item in page.faqs" :key="item.question">
            <h3>{{ item.question }}</h3>
            <p>{{ item.answer }}</p>
          </article>
        </div>
      </section>

      <section
        v-if="props.guide === 'exam-introduction'"
        class="registration-section"
        aria-labelledby="regional-registration-title"
      >
        <div class="section-heading">
          <p class="eyebrow">本地报名</p>
          <h2 id="regional-registration-title">按所在考区查询报名信息</h2>
          <p>
            全国考试时间发布后，各考区仍会分别公布报名、审核和缴费安排。请按本人报名考区查询，不要以搜索结果所属地区代替。
          </p>
        </div>
        <div class="registration-actions">
          <a
            :href="officialRegistrationPortal.href"
            target="_blank"
            rel="noopener noreferrer"
            class="registration-action primary"
          >
            <span>
              <strong>{{ officialRegistrationPortal.label }}</strong>
              <small>官方报名平台会显示当前开放考区及当地报名流程</small>
            </span>
            <span aria-hidden="true">↗</span>
          </a>
          <a :href="officialResources[3].href" target="_blank" rel="noopener noreferrer" class="registration-action">
            <span>
              <strong>查询考区联系方式</strong>
              <small>中国计算机技术职业资格网公布的各省级考试机构信息</small>
            </span>
            <span aria-hidden="true">↗</span>
          </a>
        </div>
        <div class="regional-link-grid" aria-label="各考区官方入口">
          <a
            v-for="item in displayedRegionalExamLinks"
            :key="item.name"
            :href="item.href"
            target="_blank"
            rel="noopener noreferrer"
          >
            <span>{{ item.name }}</span>
            <span aria-hidden="true">↗</span>
          </a>
        </div>
      </section>

      <section
        v-if="props.guide === 'qualifications'"
        class="qualification-directory"
        aria-labelledby="qualification-directory-title"
      >
        <div class="section-heading">
          <p class="eyebrow">资格目录</p>
          <h2 id="qualification-directory-title">按等级浏览全部资格</h2>
          <p>
            以下目录覆盖当前测试使用的 27
            项资格。展开一个等级后，可以查看该等级的全部资格；进入详情页可继续了解对应方向与准备重点。
          </p>
        </div>
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
    </main>

    <section class="official-section" :aria-labelledby="`${page.key}-official-title`">
      <div class="content-shell official-shell">
        <div>
          <p class="eyebrow">官方信息</p>
          <h2 :id="`${page.key}-official-title`">官方信息与报考安排</h2>
          <p>{{ page.officialDescription }}</p>
        </div>
        <div class="official-links" aria-label="中国计算机技术职业资格网官方入口">
          <a
            v-for="item in page.officialResources"
            :key="item.href"
            :href="item.href"
            target="_blank"
            rel="noopener noreferrer"
          >
            <span>
              <strong>{{ item.label }}</strong>
              <small>中国计算机技术职业资格网</small>
            </span>
            <span aria-hidden="true">↗</span>
          </a>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup name="PublicGuidePage" lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { listPublicExamRegions, listPublicQualifications } from '@/api/public/exam-guidance';
import {
  directionLabels,
  formatNextOfficialExamDate,
  qualificationLevelLabels,
  mergeQualificationCatalogue,
  qualifications,
  type QualificationLevel
} from './qualification-fit-config';

type GuideKind = 'qualifications' | 'exam-introduction';

interface ResourceLink {
  label: string;
  href: string;
}

interface RegionalExamLink {
  name: string;
  href: string;
}

interface GuidePage {
  key: GuideKind;
  eyebrow: string;
  title: string;
  description: string;
  primaryResource: ResourceLink;
  overviewTitle: string;
  overview: string[];
  focusEyebrow: string;
  focusTitle: string;
  focusDescription: string;
  focusItems: Array<{ number: string; title: string; description: string }>;
  stepsTitle: string;
  stepsDescription: string;
  steps: Array<{ number: string; title: string; description: string }>;
  faqTitle: string;
  faqs: Array<{ question: string; answer: string }>;
  officialDescription: string;
  officialResources: ResourceLink[];
}

const officialResources: ResourceLink[] = [
  { label: '官方考试简介', href: 'https://www.ruankao.org.cn/introduction/main.html' },
  { label: '报考指南与资格设置', href: 'https://www.ruankao.org.cn/guide/main.html' },
  { label: '当期考试安排', href: 'https://www.ruankao.org.cn/exam/plan.html' },
  { label: '各地考试机构联系方式', href: 'https://www.ruankao.org.cn/exam/contact.html' }
];

const officialRegistrationPortal: ResourceLink = {
  label: '按所在考区报名',
  href: 'https://bm.ruankao.org.cn/sign/welcome'
};

// 入口均来自中国计算机技术职业资格网首页“各地考试机构”或其考区联系方式页。
// 这些站点用于查看本地公告；实际报名仍应先通过上方统一报名平台选择当期考区。
const regionalExamLinks: RegionalExamLink[] = [
  { name: '北京', href: 'http://rsj.beijing.gov.cn/ywsite/bjpta/' },
  { name: '天津', href: 'https://www.ruankao.org.cn/exam/contact.html' },
  { name: '河北', href: 'http://www.hebpta.com.cn' },
  { name: '山西', href: 'https://www.ruankao.org.cn/article/content/100003230309143819970466.html' },
  { name: '内蒙古', href: 'http://www.impta.com.cn' },
  { name: '辽宁', href: 'http://www.lnicloud.com/' },
  { name: '大连', href: 'http://www.dlrkb.com' },
  { name: '吉林', href: 'http://www.jlzkb.com/' },
  { name: '黑龙江', href: 'http://www.hljrsks.org.cn' },
  { name: '上海', href: 'http://rsj.sh.gov.cn' },
  { name: '江苏', href: 'https://www.jsiic.cn/' },
  { name: '浙江', href: 'https://www.ruankao.org.cn/article/content/2502131006541603762472524.html' },
  { name: '宁波', href: 'https://www.ruankao.org.cn/exam/contact.html' },
  { name: '安徽', href: 'http://www.apta.gov.cn' },
  { name: '福建', href: 'http://gxt.fujian.gov.cn/zwgk/ztjj/fjrkzl/' },
  { name: '江西', href: 'http://www.itetc.org' },
  { name: '山东', href: 'http://hrss.shandong.gov.cn/rsks' },
  { name: '河南', href: 'http://www.chniee.org.cn/' },
  { name: '湖北', href: 'http://www.hbsme.com.cn/' },
  { name: '湖南', href: 'http://gxt.hunan.gov.cn/rkb/' },
  { name: '广东', href: 'https://www.ruankao.org.cn/exam/contact.html' },
  { name: '广西', href: 'http://www.gxpta.com.cn' },
  { name: '海南', href: 'https://zhaopin.hainan.gov.cn' },
  { name: '重庆', href: 'https://cqitrk.cqitc.cn' },
  { name: '四川', href: 'http://rst.sc.gov.cn' },
  { name: '贵州', href: 'http://www.gzsic.cn/' },
  { name: '云南', href: 'https://www.ynxr.com/' },
  { name: '西藏（联系方式）', href: 'https://www.ruankao.org.cn/exam/contact.html' },
  { name: '陕西', href: 'http://www.shaanxirk.com' },
  { name: '甘肃', href: 'http://ks.rst.gansu.gov.cn/ncms/index.shtml' },
  { name: '青海', href: 'http://www.qhpta.com' },
  { name: '宁夏', href: 'http://www.nxpta.com' },
  { name: '新疆', href: 'http://gxt.xinjiang.gov.cn/' },
  { name: '新疆生产建设兵团', href: 'http://btpta.xjbt.gov.cn/' }
];

const guidePages: Record<GuideKind, GuidePage> = {
  qualifications: {
    key: 'qualifications',
    eyebrow: '资格信息',
    title: '软考资格与专业方向',
    description: '资格名称不需要一次搞懂。先从工作内容和已有基础出发，资格会帮助你把要补的知识和想走的方向说得更清楚。',
    primaryResource: officialResources[1],
    overviewTitle: '资格层次与专业方向',
    overview: [
      '软考的资格设置按初级、中级、高级分层，也覆盖软件、网络、信息系统等不同专业领域。名称里的“初、中、高”表示专业技术层次，并不等于所有人都必须从初级一路往上考。',
      '更实用的顺序是：先看感兴趣的工作方向，再看对应资格考什么、需要哪些基础，最后决定是否报考。这样选，比只看别人推荐的“热门证书”更稳妥。',
      '官方考试简介明确，报考级别不以学历和资历作为前置条件；但报名时间、材料和具体操作会因考区及当期安排而不同，提交报名之前仍要看当地通知。'
    ],
    focusEyebrow: '选择依据',
    focusTitle: '选择资格时的关注因素',
    focusDescription: '不需要一次答得很准确。把它们当作缩小范围的提示就够了。',
    focusItems: [
      {
        number: '01',
        title: '你想做哪类工作？',
        description: '偏软件开发、网络运维、信息系统建设，还是项目和服务管理？先看工作内容，而不是先记资格名称。'
      },
      {
        number: '02',
        title: '你现在的基础在哪里？',
        description:
          '已经有工作经验，还是正处于知识起步阶段？这会影响你该从理解概念、补基础，还是直接看某个资格的大纲开始。'
      },
      {
        number: '03',
        title: '你考它是为了什么？',
        description: '有人是为了求职、岗位发展或单位要求，也有人想系统梳理知识。目的不同，准备方式和投入时间也会不同。'
      }
    ],
    stepsTitle: '资格选择流程',
    stepsDescription: '先把选择范围缩小到一两个候选方向，再决定要不要报名。',
    steps: [
      { number: '1', title: '看官方资格设置', description: '先认识有哪些专业领域和层次，不需要立刻背下所有名称。' },
      {
        number: '2',
        title: '挑一两个候选方向',
        description: '结合想做的工作、身边岗位要求或目前正在学的内容，先留两个备选即可。'
      },
      {
        number: '3',
        title: '再看对应大纲和题目',
        description: '大纲告诉你要学什么，题目能让你感受实际考查方式。发现不合适，换方向是正常的。'
      },
      {
        number: '4',
        title: '确认当期安排后再报名',
        description: '决定报考时，再核对当期考试安排、所在地报名通知和官方报名入口。'
      }
    ],
    faqTitle: '资格相关常见问题',
    faqs: [
      {
        question: '我不是计算机专业，也能了解或报考吗？',
        answer:
          '可以先了解。是否报考不取决于专业名称，而要看你准备达到哪个资格要求的知识和能力水平。报名前请以官方简介及本地报名通知为准。'
      },
      {
        question: '是不是一定要从初级开始？',
        answer:
          '不一定。层次不是给每个人规定的固定路线。更重要的是：候选资格是否和你的基础、目标及可投入的学习时间相匹配。'
      },
      {
        question: '现在完全没方向，下一步该做什么？',
        answer:
          '先不要强迫自己选。浏览官方资格设置，再看平台题库或学习资料里的内容；看过一两个方向后，通常会更容易判断自己想继续了解什么。'
      }
    ],
    officialDescription:
      '资格名称、层次和当期可报考的安排都应以官方发布为准。尤其是准备报名时，不要只依据旧攻略或其他地区的经验。',
    officialResources
  },
  'exam-introduction': {
    key: 'exam-introduction',
    eyebrow: '考试介绍',
    title: '软考考试的基本信息',
    description:
      '软考不是一套所有人都一样的题。不同资格对应不同的专业内容；你要先选定或初步关注一个方向，才知道应该看什么、练什么。',
    primaryResource: officialResources[0],
    overviewTitle: '软考的考试定位',
    overview: [
      '计算机技术与软件专业技术资格（水平）考试由国家统一组织实施。通过考试获得证书，说明你达到了相应专业岗位所要求的知识和能力水平。',
      '它不是某一门课程的期末考试。不同资格会有不同的考试大纲、内容范围和考查方式，所以“软考考什么”必须结合具体资格来看。',
      '证书能帮助别人理解你的专业能力，但它不能替代实际项目经验。把备考当作系统梳理基础、补齐短板的过程，通常比只为了分数更有收获。'
    ],
    focusEyebrow: '信息分类',
    focusTitle: '考试、报名与学习',
    focusDescription: '分开理解之后，你会知道现在最需要查的到底是哪一类信息。',
    focusItems: [
      {
        number: '01',
        title: '考试考什么？',
        description: '看你关注资格的考试大纲和官方说明。不要拿其他资格的经验，直接套到自己身上。'
      },
      {
        number: '02',
        title: '什么时候能报名？',
        description: '全国会发布考试安排，但报名、审核、缴费和准考证等时间要以所在地考试机构通知为准。'
      },
      {
        number: '03',
        title: '现在该怎么学？',
        description: '先确认方向，再按大纲建立知识框架，配合题目了解薄弱点；不要一开始就把刷题当成全部准备。'
      }
    ],
    stepsTitle: '备考准备流程',
    stepsDescription: '不用一次把所有信息查完，按顺序走可以减少反复。',
    steps: [
      {
        number: '1',
        title: '先确定要了解的资格',
        description: '还没选定也没关系，先从一个候选方向开始看，后面可以调整。'
      },
      {
        number: '2',
        title: '查看官方大纲和考试介绍',
        description: '确认内容范围、考试形式和自己需要补的基础，不用依赖零散的二手总结。'
      },
      {
        number: '3',
        title: '核对当期和本地信息',
        description: '查看当期考试安排，再到所在地考试机构确认报名、审核和缴费事项。'
      },
      {
        number: '4',
        title: '按知识框架开始学习',
        description: '先学懂重点，再用题目检查理解；准备好了再进入报名和个人学习计划。'
      }
    ],
    faqTitle: '考试相关常见问题',
    faqs: [
      {
        question: '全国考试时间确定后，是不是哪里都能立刻报名？',
        answer: '不是。全国考试安排和本地报名通知是两层信息。考区会分别发布报名、审核、缴费等具体时间，所以要同时查看。'
      },
      {
        question: '我先刷题，之后再看大纲可以吗？',
        answer:
          '可以把题目当作了解方式，但不建议完全跳过大纲。大纲能告诉你题目为什么会这样考，也能帮助你避免只记答案、不理解知识。'
      },
      {
        question: '通过考试是不是就等于有工作经验？',
        answer:
          '不是。证书说明的是相应的专业技术水平；实际工作还需要项目经验、沟通协作和持续学习。二者可以互相帮助，但不能互相替代。'
      }
    ],
    officialDescription:
      '考试内容、当期安排和各地报名事项会陆续更新。准备报考时，优先看中国计算机技术职业资格网及所在地考试机构的通知。',
    officialResources
  }
};

const props = defineProps<{ guide: GuideKind }>();
const route = useRoute();
const page = computed(() => guidePages[props.guide]);
const qualificationProfiles = ref(qualifications);
const displayedRegionalExamLinks = ref(regionalExamLinks);
const recommendedProfile = computed(() =>
  props.guide === 'qualifications'
    ? qualificationProfiles.value.find(
        item => item.code === (typeof route.query.focus === 'string' ? route.query.focus : undefined)
      )
    : undefined
);
const levelLabels = qualificationLevelLabels;
const qualificationGroups = computed(() =>
  (['LOW', 'MIDDLE', 'HIGH'] as QualificationLevel[]).map(level => ({
    level,
    items: qualificationProfiles.value.filter(item => item.level === level)
  }))
);

onMounted(async () => {
  const [catalogue, regions] = await Promise.allSettled([listPublicQualifications(), listPublicExamRegions()]);
  if (catalogue.status === 'fulfilled') {
    qualificationProfiles.value = mergeQualificationCatalogue(catalogue.value.data);
  }
  if (regions.status === 'fulfilled') {
    displayedRegionalExamLinks.value = regions.value.data.regions.map(item => ({
      name: item.regionName,
      href: item.localNoticeUrl
    }));
  }
});
</script>

<style scoped lang="scss">
.guide-page {
  min-height: 100%;
  color: #23354c;
  background: #fff;
}
.content-shell {
  width: min(1020px, calc(100% - 40px));
  margin: 0 auto;
}
.guide-hero {
  padding: 34px 0 76px;
  background: linear-gradient(135deg, #f5faff, #edf5fc);
}
.back-link {
  display: inline-block;
  margin-bottom: 52px;
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
.guide-hero h1 {
  max-width: 760px;
  margin: 0 0 20px;
  color: #172a43;
  font-size: clamp(36px, 5vw, 56px);
  letter-spacing: -0.04em;
  line-height: 1.15;
}
.hero-description {
  max-width: 730px;
  margin: 0;
  color: #506880;
  font-size: 18px;
  line-height: 1.85;
}
.primary-action {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  margin-top: 30px;
  padding: 12px 17px;
  color: #fff;
  border-radius: 8px;
  background: #2878d8;
  box-shadow: 0 10px 22px rgb(40 120 216 / 20%);
  font-weight: 700;
  text-decoration: none;
}
.guide-content {
  padding: 78px 0 96px;
}
.recommended-profile {
  padding: 28px 30px;
  margin-bottom: 56px;
  border: 1px solid #c9dff4;
  border-radius: 12px;
  background: #f2f8ff;
}
.recommended-profile h2 {
  margin: 0 0 10px;
  color: #1d446c;
  font-size: 28px;
}
.recommended-profile > p:not(.eyebrow) {
  max-width: 720px;
  margin: 0;
  color: #526f8d;
  line-height: 1.8;
}
.recommended-profile > div {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 17px;
}
.recommended-profile span {
  padding: 5px 9px;
  color: #3679be;
  border-radius: 99px;
  background: #dfeefe;
  font-size: 12px;
  font-weight: 700;
}
.recommended-profile .profile-note {
  margin-top: 14px;
  color: #637c96;
  font-size: 14px;
}
.overview-section {
  max-width: 780px;
}
.overview-section h2,
.section-heading h2,
.official-section h2 {
  margin: 0 0 18px;
  color: #1b2c43;
  font-size: clamp(27px, 3vw, 34px);
  letter-spacing: -0.02em;
  line-height: 1.3;
}
.overview-section > p:not(.eyebrow) {
  margin: 0 0 16px;
  color: #5b6f85;
  line-height: 1.9;
}
.card-section,
.steps-section,
.faq-section,
.qualification-directory {
  margin-top: 80px;
}
.section-heading {
  max-width: 720px;
  margin-bottom: 30px;
}
.section-heading > p:not(.eyebrow) {
  margin: 0;
  color: #65788f;
  line-height: 1.85;
}
.focus-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}
.focus-card {
  min-height: 210px;
  padding: 25px;
  border: 1px solid #dde8f2;
  border-radius: 12px;
  background: #fff;
}
.item-number {
  color: #77a9de;
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.09em;
}
.focus-card h3,
.faq-list h3 {
  margin: 20px 0 10px;
  color: #1f3551;
  font-size: 19px;
}
.focus-card p,
.faq-list p {
  margin: 0;
  color: #65778c;
  line-height: 1.78;
}
.steps-section {
  padding: 64px 0;
  background: #f4f8fc;
}
.steps-section > .section-heading,
.steps-list {
  width: min(1020px, calc(100% - 40px));
  margin-right: auto;
  margin-left: auto;
}
.steps-list {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  padding: 0;
  margin-top: 34px;
  list-style: none;
}
.steps-list li {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 12px;
  min-height: 138px;
  padding: 0 20px;
  border-left: 1px solid #cadced;
}
.steps-list li:first-child {
  padding-left: 0;
  border-left: 0;
}
.steps-list span {
  color: #5599df;
  font-size: 13px;
  font-weight: 800;
}
.steps-list strong {
  display: block;
  margin-bottom: 10px;
  color: #213a58;
}
.steps-list p {
  margin: 0;
  color: #68798d;
  font-size: 14px;
  line-height: 1.72;
}
.faq-list {
  display: grid;
  gap: 0;
  border-top: 1px solid #dce7f0;
}
.faq-list article {
  padding: 22px 0;
  border-bottom: 1px solid #dce7f0;
}
.faq-list h3 {
  margin: 0 0 10px;
}
.registration-section {
  padding: 34px;
  margin-top: 56px;
  border: 1px solid #cddfed;
  border-radius: 14px;
  background: #f5f9fd;
}
.registration-section .section-heading {
  max-width: 760px;
  margin-bottom: 24px;
}
.registration-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.registration-action {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  min-height: 78px;
  padding: 15px 17px;
  color: #286cae;
  border: 1px solid #c9dced;
  border-radius: 10px;
  background: #fff;
  text-decoration: none;
}
.registration-action:hover {
  border-color: #91bbdf;
  background: #f8fcff;
}
.registration-action.primary {
  color: #fff;
  border-color: #377fbd;
  background: #377fbd;
}
.registration-action.primary:hover {
  background: #286fae;
}
.registration-action strong,
.registration-action small {
  display: block;
}
.registration-action strong {
  color: #284866;
  font-size: 15px;
}
.registration-action.primary strong {
  color: #fff;
}
.registration-action small {
  margin-top: 5px;
  color: #6d8297;
  font-size: 12px;
  line-height: 1.5;
}
.registration-action.primary small {
  color: #dcecff;
}
.regional-link-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-top: 18px;
}
.regional-link-grid a {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-height: 42px;
  padding: 9px 11px;
  color: #365d81;
  border: 1px solid #d7e4ef;
  border-radius: 7px;
  background: #fff;
  font-size: 14px;
  text-decoration: none;
}
.regional-link-grid a:hover {
  color: #276fae;
  border-color: #a9cce9;
  background: #f6fbff;
}
.qualification-directory {
  padding-top: 2px;
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
.official-section {
  padding: 66px 0;
  color: #eff6ff;
  background: #1d334d;
}
.official-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(350px, 420px);
  gap: 68px;
  align-items: center;
}
.official-section .eyebrow {
  color: #a3caef;
}
.official-section h2 {
  color: #fff;
}
.official-section p:not(.eyebrow) {
  margin: 0;
  color: #c6d8ea;
  line-height: 1.85;
}
.official-links {
  display: grid;
  gap: 9px;
}
.official-links a {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  color: #f4f8ff;
  border: 1px solid rgb(216 232 255 / 21%);
  border-radius: 8px;
  background: rgb(255 255 255 / 5%);
  text-decoration: none;
}
.official-links a:hover {
  background: rgb(255 255 255 / 10%);
}
.official-links strong,
.official-links small {
  display: block;
}
.official-links strong {
  font-size: 15px;
}
.official-links small {
  margin-top: 4px;
  color: #abc1d9;
  font-size: 12px;
}
html.dark .guide-page {
  color: #d8e5f3;
  background: #09111f;
}
html.dark .guide-hero {
  background: linear-gradient(135deg, #102037, #0b1727);
}
html.dark .back-link,
html.dark .eyebrow,
html.dark .item-number,
html.dark .steps-list span {
  color: #7db9f4;
}
html.dark .guide-hero h1,
html.dark .overview-section h2,
html.dark .section-heading h2,
html.dark .focus-card h3,
html.dark .faq-list h3,
html.dark .steps-list strong,
html.dark .qualification-item h3,
html.dark .official-section h2 {
  color: #f0f6ff;
}
html.dark .hero-description,
html.dark .overview-section > p:not(.eyebrow),
html.dark .section-heading > p:not(.eyebrow),
html.dark .focus-card p,
html.dark .faq-list p,
html.dark .steps-list p,
html.dark .qualification-item p,
html.dark .registration-section p,
html.dark .official-section p:not(.eyebrow) {
  color: #a8bad0;
}
html.dark .primary-action {
  background: #2b6fd0;
}
html.dark .recommended-profile {
  border-color: #315273;
  background: #10233a;
}
html.dark .recommended-profile h2 {
  color: #f0f6ff;
}
html.dark .recommended-profile > p:not(.eyebrow),
html.dark .recommended-profile .profile-note {
  color: #b0c3d8;
}
html.dark .recommended-profile span {
  color: #9ecbfa;
  background: #193a5c;
}
html.dark .focus-card,
html.dark .registration-section {
  border-color: #2d4965;
  background: #111f33;
}
html.dark .faq-list,
html.dark .faq-list article {
  border-color: #2c455f;
}
html.dark .faq-list article {
  background: transparent;
}
html.dark .steps-section {
  background: #0d1929;
}
html.dark .steps-list,
html.dark .steps-list li,
html.dark .qualification-item {
  border-color: #2c455f;
}
html.dark .qualification-level {
  border-color: #2d4965;
  background: #111f33;
}
html.dark .qualification-level summary strong,
html.dark .qualification-item strong {
  color: #f0f6ff;
}
html.dark .qualification-level summary small,
html.dark .qualification-item small {
  color: #a8bad0;
}
html.dark .qualification-list {
  border-color: #2c455f;
}
html.dark .qualification-item:hover {
  background: #162b43;
}
html.dark .qualification-item .next-exam-date {
  color: #7db9f4;
}
html.dark .regional-link-grid a {
  color: #c5ddf4;
  border-color: #3b6085;
  background: #142b45;
}
html.dark .regional-link-grid a:hover {
  border-color: #6ba4d9;
  background: #193a5c;
}
html.dark .official-section {
  background: #142d49;
}
html.dark .official-section .eyebrow {
  color: #9fc9f3;
}
html.dark .official-links a {
  color: #d7e8fa;
  border-color: #3a5b7b;
  background: rgb(255 255 255 / 8%);
}
html.dark .official-links small {
  color: #abc7e4;
}
@media (max-width: 800px) {
  .focus-grid {
    grid-template-columns: 1fr;
  }
  .steps-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    row-gap: 28px;
  }
  .steps-list li:nth-child(3) {
    padding-left: 0;
    border-left: 0;
  }
  .official-shell {
    grid-template-columns: 1fr;
    gap: 34px;
  }
  .regional-link-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 640px) {
  .content-shell {
    width: min(100% - 28px, 1020px);
  }
  .guide-hero {
    padding-bottom: 62px;
  }
  .back-link {
    margin-bottom: 38px;
  }
  .hero-description {
    font-size: 16px;
  }
  .guide-content {
    padding: 62px 0;
  }
  .card-section,
  .steps-section,
  .faq-section,
  .qualification-directory {
    margin-top: 62px;
  }
  .registration-section {
    padding: 24px 18px;
    margin-top: 42px;
  }
  .registration-actions,
  .regional-link-grid {
    grid-template-columns: 1fr;
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
  .steps-section {
    padding: 52px 0;
  }
  .steps-section > .section-heading,
  .steps-list {
    width: min(100% - 28px, 1020px);
  }
  .steps-list {
    grid-template-columns: 1fr;
    gap: 0;
  }
  .steps-list li,
  .steps-list li:nth-child(3) {
    min-height: 0;
    padding: 18px 0 18px 18px;
    border-top: 1px solid #cadced;
    border-left: 0;
  }
  .steps-list li:first-child {
    padding-left: 18px;
  }
  .official-section {
    padding: 54px 0;
  }
}
</style>
