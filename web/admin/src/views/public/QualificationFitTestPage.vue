<template>
  <div class="fit-page">
    <section class="fit-hero">
      <div class="content-shell">
        <router-link class="back-link" to="/home">← 回到首页</router-link>
        <p class="eyebrow">资格适合度测试</p>
        <h1>了解适合继续探索的方向</h1>
        <p>
          测试根据你的学习阶段、已有基础与工作偏好，在所选时间段可考的资格中给出一项参考建议。它不是报考审核，也不替代你对资格内容的确认。
        </p>
      </div>
    </section>

    <main class="content-shell fit-content">
      <section v-if="!completed" class="question-panel" aria-live="polite">
        <div class="progress-row">
          <span>第 {{ currentIndex + 1 }} 题，共 {{ questions.length }} 题</span>
          <span>{{ Math.round(((currentIndex + 1) / questions.length) * 100) }}%</span>
        </div>
        <div class="progress-track" aria-hidden="true">
          <span :style="{ width: `${((currentIndex + 1) / questions.length) * 100}%` }" />
        </div>
        <p class="question-kicker">{{ currentQuestion.kicker }}</p>
        <h2>{{ currentQuestion.title }}</h2>
        <p class="question-help">{{ currentQuestion.description }}</p>
        <fieldset>
          <legend class="sr-only">{{ currentQuestion.title }}</legend>
          <label
            v-for="option in currentQuestion.options"
            :key="option.value"
            class="option-card"
            :class="{ selected: answers[currentQuestion.id] === option.value }"
          >
            <input
              v-model="answers[currentQuestion.id]"
              type="radio"
              :name="currentQuestion.id"
              :value="option.value"
            />
            <span>{{ option.label }}</span>
          </label>
        </fieldset>
        <p v-if="showRequired" class="required-message">请选择一项后继续。</p>
        <div class="question-actions">
          <button v-if="currentIndex > 0" type="button" class="text-button" @click="previous">上一步</button>
          <span v-else />
          <button type="button" class="primary-button" @click="next">
            {{ currentIndex === questions.length - 1 ? '查看建议' : '下一步' }}
          </button>
        </div>
      </section>

      <section v-else class="result-panel" aria-labelledby="result-title">
        <p class="eyebrow">测试结果</p>
        <p class="result-label">推荐方向</p>
        <h1 id="result-title">{{ directionLabels[result!.direction] }}</h1>
        <div class="result-card">
          <p>优先建议了解</p>
          <h2>{{ result!.name }}</h2>
          <p>{{ result!.suitableFor }}</p>
        </div>
        <div class="result-explanation">
          <div>
            <h3>推荐说明</h3>
            <p>{{ recommendationSummary }}</p>
          </div>
          <div>
            <h3>准备提示</h3>
            <p>{{ result!.preparationNote }}</p>
          </div>
        </div>
        <p v-if="selectedPeriod?.availability === 'planning'" class="period-note">
          你选择的是 {{ selectedPeriod.label }}。该时间段的资格范围会在官方安排发布后更新，实际报考请以官方通知为准。
        </p>
        <div class="result-actions">
          <router-link class="primary-button" :to="`/qualifications/${result!.code}`">查看推荐资格详情</router-link>
          <button type="button" class="text-button" @click="restart">重新测试</button>
          <router-link class="text-button" to="/home">返回首页</router-link>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup name="QualificationFitTestPage" lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getPublicQualificationContext, listPublicQualifications } from '@/api/public/exam-guidance';
import {
  directionLabels,
  examPeriods,
  mergeQualificationCatalogue,
  recommendQualification,
  type Direction,
  type WorkPreference,
  type QualificationProfile
} from './qualification-fit-config';

interface Choice {
  value: string;
  label: string;
}

interface Question {
  id: string;
  kicker: string;
  title: string;
  description: string;
  options: Choice[];
}

const answers = ref<Record<string, string>>({});
const currentIndex = ref(0);
const completed = ref(false);
const showRequired = ref(false);
const result = ref<QualificationProfile>();
const qualificationProfiles = ref<QualificationProfile[]>([]);

const baseQuestions: Question[] = [
  {
    id: 'period',
    kicker: '备考时间',
    title: '你计划参加哪个时间段的考试？',
    description: '选择大致时间即可；不确定时，按最近一个可考批次进行参考。',
    options: [
      { value: 'unsure', label: '暂不确定，按最近一次可考批次参考' },
      ...examPeriods.map(item => ({ value: item.id, label: item.label }))
    ]
  },
  {
    id: 'stage',
    kicker: '当前阶段',
    title: '你目前处于什么阶段？',
    description: '它只用于判断准备起点，不代表你的能力上限。',
    options: [
      { value: 'year-1', label: '在校：大一' },
      { value: 'year-2', label: '在校：大二' },
      { value: 'year-3', label: '在校：大三' },
      { value: 'year-4', label: '在校：大四或应届' },
      { value: 'career-change', label: '非在校，准备转向相关领域或刚起步' },
      { value: 'technical', label: '已有相关技术工作经验' },
      { value: 'management', label: '已有项目、管理或信息化协作经验' }
    ]
  },
  {
    id: 'foundation',
    kicker: '已有基础',
    title: '你目前的相关基础更接近哪一种？',
    description: '如实选择即可；它不是技术测验。',
    options: [
      { value: 'none', label: '几乎没有相关基础，想从系统学习开始' },
      { value: 'coursework', label: '学过一些编程或计算机相关课程' },
      { value: 'technical', label: '已有开发、网络、安全或系统等技术基础' },
      { value: 'management', label: '已有项目协作、业务分析或管理实践' }
    ]
  },
  {
    id: 'direction',
    kicker: '职业方向',
    title: '你更想发展的工作方向是什么？',
    description: '不需要先认识资格名称，只选此刻最想进一步了解的方向。',
    options: [
      { value: 'software', label: '软件开发与设计' },
      { value: 'infrastructure', label: '网络、系统运维或信息安全' },
      { value: 'systems', label: '信息系统建设、数据或运行管理' },
      { value: 'management', label: '项目、治理或信息技术服务管理' },
      { value: 'digital', label: '数字内容、电商、辅助设计、硬件或信息处理' }
    ]
  },
  {
    id: 'preference',
    kicker: '工作偏好',
    title: '你更愿意投入哪类工作？',
    description: '这用于区分同一方向中的技术实现、运行保障、系统分析与协调管理。',
    options: [
      { value: 'build', label: '设计并实现技术方案或应用' },
      { value: 'operate', label: '保障系统、网络或服务稳定运行' },
      { value: 'analyse', label: '分析需求、数据、问题或整体方案' },
      { value: 'coordinate', label: '协调项目、流程、质量与服务' }
    ]
  }
];

const followUpQuestions: Record<Direction, Question> = {
  software: {
    id: 'specialty',
    kicker: '方向补充',
    title: '在软件方向中，你更想先接触什么？',
    description: '选择最接近的一项即可。',
    options: [
      { value: 'development', label: '程序设计与应用开发' },
      { value: 'web', label: '网页与前端制作' },
      { value: 'testing', label: '软件测试与质量分析' },
      { value: 'embedded', label: '嵌入式与软硬件结合' },
      { value: 'architecture', label: '软件架构与复杂系统设计' }
    ]
  },
  infrastructure: {
    id: 'specialty',
    kicker: '方向补充',
    title: '在基础设施方向中，你更想先接触什么？',
    description: '选择最接近的一项即可。',
    options: [
      { value: 'network', label: '网络建设与运行' },
      { value: 'security', label: '信息安全' },
      { value: 'support', label: '技术支持与故障处理' },
      { value: 'planning', label: '网络规划与整体设计' }
    ]
  },
  systems: {
    id: 'specialty',
    kicker: '方向补充',
    title: '在信息系统方向中，你更想先接触什么？',
    description: '选择最接近的一项即可。',
    options: [
      { value: 'database', label: '数据与数据库系统' },
      { value: 'operation', label: '信息系统运行与管理' },
      { value: 'analysis', label: '系统分析与业务需求' },
      { value: 'system', label: '信息系统建设与集成' }
    ]
  },
  management: {
    id: 'specialty',
    kicker: '方向补充',
    title: '在管理方向中，你更想先接触什么？',
    description: '选择最接近的一项即可。',
    options: [
      { value: 'project', label: '信息化项目管理' },
      { value: 'process', label: '软件过程与质量改进' },
      { value: 'supervision', label: '信息系统监理' },
      { value: 'planning', label: '系统规划、治理与服务管理' }
    ]
  },
  digital: {
    id: 'specialty',
    kicker: '方向补充',
    title: '在数字应用方向中，你更想先接触什么？',
    description: '选择最接近的一项即可。',
    options: [
      { value: 'multimedia', label: '多媒体内容与应用制作' },
      { value: 'ecommerce', label: '电子商务技术与应用' },
      { value: 'cad', label: '计算机辅助设计' },
      { value: 'hardware', label: '计算机硬件与设备' },
      { value: 'information-processing', label: '信息处理与应用支持' }
    ]
  }
};

const questions = computed(() => {
  const direction = answers.value.direction as Direction | undefined;
  return direction ? [...baseQuestions, followUpQuestions[direction]] : baseQuestions;
});
const currentQuestion = computed(() => questions.value[currentIndex.value]);
const selectedPeriod = computed(() => {
  const selected = answers.value.period;
  return examPeriods.find(item => item.id === selected) ?? examPeriods[0];
});

const recommendationSummary = computed(() => {
  if (!result.value) return '';
  const stage = answers.value.stage.startsWith('year-') ? '你目前仍处于学习准备阶段' : '结合你目前的学习与工作阶段';
  return `${stage}，并且更关注${directionLabels[result.value.direction]}。在${selectedPeriod.value.label}的可考资格中，${result.value.name}与这一方向最接近，可作为优先了解的起点。`;
});

function next() {
  if (!answers.value[currentQuestion.value.id]) {
    showRequired.value = true;
    return;
  }
  showRequired.value = false;
  if (currentIndex.value < questions.value.length - 1) {
    currentIndex.value += 1;
    return;
  }
  void calculateResult();
}

function previous() {
  showRequired.value = false;
  currentIndex.value -= 1;
}

async function calculateResult() {
  const period = selectedPeriod.value;
  let candidates = qualificationProfiles.value.length
    ? eligibleProfiles(period.id, qualificationProfiles.value)
    : undefined;
  const matchedPeriod = period.id.match(/^(\d{4})-h([12])$/i);
  if (matchedPeriod) {
    try {
      const context = await getPublicQualificationContext(
        Number(matchedPeriod[1]),
        `H${matchedPeriod[2]}` as 'H1' | 'H2'
      );
      if (context.data.availability === 'OFFICIAL') {
        const officialCodes = new Set(context.data.qualifications.map(item => item.certificationCode));
        const officialCandidates = (
          qualificationProfiles.value.length ? qualificationProfiles.value : undefined
        )?.filter(item => officialCodes.has(item.code));
        if (officialCandidates?.length) candidates = officialCandidates;
      }
    } catch {
      // The configured planning range remains available if the public schedule is temporarily unavailable.
    }
  }
  result.value = recommendQualification(
    {
      periodId: selectedPeriod.value.id,
      stage: answers.value.stage,
      foundation: answers.value.foundation,
      direction: answers.value.direction as Direction,
      preference: answers.value.preference as WorkPreference,
      specialty: answers.value.specialty
    },
    candidates
  );
  completed.value = true;
}

function eligibleProfiles(periodId: string, profiles: QualificationProfile[]) {
  const configuredPeriod = examPeriods.find(item => item.id === periodId) ?? examPeriods[0];
  return profiles.filter(item => configuredPeriod.eligibleCodes.includes(item.code));
}

onMounted(async () => {
  try {
    const response = await listPublicQualifications();
    qualificationProfiles.value = mergeQualificationCatalogue(response.data);
  } catch {
    qualificationProfiles.value = [];
  }
});

function restart() {
  answers.value = {};
  currentIndex.value = 0;
  result.value = undefined;
  completed.value = false;
  showRequired.value = false;
}
</script>

<style scoped lang="scss">
.fit-page {
  min-height: 100%;
  color: #203047;
  background: #f8fafc;
}
.content-shell {
  width: min(1020px, calc(100% - 40px));
  margin: 0 auto;
}
.fit-hero {
  padding: 34px 0 58px;
  background: linear-gradient(135deg, #eef6ff, #f8fbff);
}
.back-link {
  display: inline-block;
  margin-bottom: 42px;
  color: #58718d;
  font-size: 14px;
  text-decoration: none;
}
.eyebrow {
  margin: 0 0 10px;
  color: #3274ba;
  font-size: 13px;
  font-weight: 750;
  letter-spacing: 0.08em;
}
.fit-hero h1 {
  margin: 0 0 16px;
  color: #172a43;
  font-size: clamp(34px, 5vw, 54px);
  letter-spacing: -0.04em;
}
.fit-hero p:not(.eyebrow) {
  max-width: 680px;
  margin: 0;
  color: #58708b;
  line-height: 1.8;
}
.fit-content {
  padding: 54px 0 80px;
}
.question-panel,
.result-panel {
  padding: clamp(26px, 5vw, 48px);
  border: 1px solid #dce7f2;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 18px 48px rgb(33 70 109 / 7%);
}
.progress-row {
  display: flex;
  justify-content: space-between;
  color: #68809a;
  font-size: 13px;
  font-weight: 700;
}
.progress-track {
  height: 5px;
  margin: 13px 0 40px;
  overflow: hidden;
  border-radius: 99px;
  background: #e8eff6;
}
.progress-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #4288d8;
  transition: width 0.2s ease;
}
.question-kicker,
.result-label {
  margin: 0 0 10px;
  color: #3c79b9;
  font-size: 14px;
  font-weight: 700;
}
.question-panel h2,
.result-panel > h1 {
  margin: 0;
  color: #1a2d46;
  font-size: clamp(27px, 4vw, 38px);
  line-height: 1.32;
}
.question-help {
  margin: 13px 0 28px;
  color: #64778d;
  line-height: 1.75;
}
fieldset {
  display: grid;
  gap: 11px;
  padding: 0;
  margin: 0;
  border: 0;
}
.option-card {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 15px 16px;
  color: #405872;
  border: 1px solid #dbe6f1;
  border-radius: 10px;
  cursor: pointer;
  transition: 0.18s ease;
}
.option-card:hover,
.option-card.selected {
  color: #1e61a8;
  border-color: #7cb0e5;
  background: #f1f7ff;
}
.option-card input {
  width: 16px;
  height: 16px;
  margin: 0;
  accent-color: #2878d8;
}
.required-message {
  margin: 16px 0 0;
  color: #c34141;
  font-size: 14px;
}
.question-actions,
.result-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  align-items: center;
  justify-content: space-between;
  margin-top: 36px;
}
.primary-button {
  display: inline-flex;
  min-height: 45px;
  align-items: center;
  justify-content: center;
  padding: 0 19px;
  color: #fff;
  border: 0;
  border-radius: 8px;
  background: #2878d8;
  box-shadow: 0 10px 22px rgb(40 120 216 / 20%);
  font: inherit;
  font-weight: 700;
  text-decoration: none;
  cursor: pointer;
}
.text-button {
  padding: 0;
  color: #286db6;
  border: 0;
  background: transparent;
  font: inherit;
  font-weight: 700;
  text-decoration: none;
  cursor: pointer;
}
.result-card {
  padding: 27px 29px;
  margin-top: 28px;
  border: 1px solid #bdd8f1;
  border-radius: 12px;
  background: #f3f8fe;
}
.result-card p {
  margin: 0;
  color: #547899;
  line-height: 1.7;
}
.result-card h2 {
  margin: 8px 0;
  color: #1b4776;
  font-size: 28px;
}
.result-explanation {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
  margin-top: 26px;
}
.result-explanation > div {
  padding: 20px;
  border-top: 1px solid #dce7f2;
}
.result-explanation h3 {
  margin: 0 0 9px;
  color: #294564;
  font-size: 16px;
}
.result-explanation p,
.period-note {
  margin: 0;
  color: #657a91;
  line-height: 1.75;
}
.period-note {
  padding: 15px 17px;
  margin-top: 22px;
  border-radius: 8px;
  background: #f4f7fa;
  font-size: 14px;
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
html.dark .fit-page { color: #d8e5f3; background: #09111f; }
html.dark .fit-hero { background: linear-gradient(135deg, #102037, #0b1727); }
html.dark .back-link, html.dark .eyebrow, html.dark .question-number, html.dark .progress-label { color: #7db9f4; }
html.dark .fit-hero h1, html.dark .question-panel h2, html.dark .result-panel > h1, html.dark .result-card h2, html.dark .result-explanation h3 { color: #f0f6ff; }
html.dark .fit-hero p, html.dark .question-help, html.dark .result-card > p, html.dark .result-explanation p, html.dark .period-note { color: #a8bad0; }
html.dark .question-panel, html.dark .result-panel, html.dark .result-card { border-color: #2d4965; background: #111f33; box-shadow: 0 18px 48px rgb(0 0 0 / 23%); }
html.dark .progress-row { color: #a8bad0; }
html.dark .question-kicker, html.dark .result-label { color: #7db9f4; }
html.dark .progress-track { background: #263c55; }
html.dark .progress-track span { background: #4a94e6; }
html.dark .option-card { color: #c8d9eb; border-color: #314e6c; background: #0e1b2d; }
html.dark .option-card:hover, html.dark .option-card.selected { color: #dcecff; border-color: #67a6df; background: #183a5c; }
html.dark .primary-button { background: #2b6fd0; box-shadow: 0 10px 22px rgb(20 79 149 / 28%); }
html.dark .text-button { color: #a9cef4; }
html.dark .result-explanation > div { border-color: #2d4965; }
html.dark .period-note { background: #14243a; }
@media (max-width: 640px) {
  .content-shell {
    width: min(100% - 28px, 1020px);
  }
  .fit-hero {
    padding-bottom: 46px;
  }
  .back-link {
    margin-bottom: 30px;
  }
  .fit-content {
    padding: 30px 0 58px;
  }
  .result-explanation {
    grid-template-columns: 1fr;
    gap: 0;
  }
  .question-actions,
  .result-actions {
    align-items: flex-start;
    flex-direction: column-reverse;
  }
  .question-actions > :first-child {
    align-self: flex-start;
  }
}
</style>
