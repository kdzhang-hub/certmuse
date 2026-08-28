<template>
  <div class="p-2 app-container knowledge-practice-page">
    <section class="page-intro">
      <div>
        <span class="page-intro__eyebrow">QUESTION BANK</span>
        <h2>知识点练习</h2>
        <p>选择科目和知识点，查看当前掌握情况后开始专项练习。</p>
      </div>
      <el-button class="page-intro__back" size="large" @click="router.push('/learning/question-bank')">返回题库</el-button>
    </section>

    <section v-if="loading" class="page-state" aria-live="polite">
      <el-skeleton :rows="8" animated />
    </section>

    <section v-else-if="loadError" class="page-state">
      <el-result icon="warning" title="知识点练习暂不可用" :sub-title="loadError">
        <template #extra><el-button type="primary" @click="() => loadSetup()">重新加载</el-button></template>
      </el-result>
    </section>

    <section v-else class="practice-layout">
      <el-card class="syllabus-panel" shadow="never">
        <template #header>
          <div class="panel-heading">
            <div>
              <span class="panel-heading__eyebrow">SYLLABUS DIRECTORY</span>
              <h3>考纲目录</h3>
            </div>
          </div>
        </template>

        <el-form-item label="科目" class="subject-picker subject-picker--directory">
          <el-select v-model="selectedSubjectId" aria-label="选择练习科目" :disabled="loading || !subjects.length" @change="changeSubject">
            <el-option v-for="subject in subjects" :key="subject.id" :label="subject.subjectName" :value="subject.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="搜索" class="knowledge-search">
          <el-input v-model="knowledgeSearch" clearable :prefix-icon="Search" placeholder="按章节名称搜索" aria-label="按章节名称搜索" />
        </el-form-item>

        <div class="directory-list" role="tree" aria-label="知识点考纲目录">
          <div
            v-for="row in visibleRows"
            :key="row.node.id"
            class="directory-row"
            :class="{ 'is-selected': row.node.id === selectedNodeId, 'is-parent': row.node.children.length }"
            :style="{ '--depth': row.depth }"
            role="treeitem"
            :aria-selected="row.node.id === selectedNodeId"
            :aria-expanded="row.depth === 0 && row.node.children.length ? isDirectoryExpanded(row.node.id) : undefined"
          >
            <span class="directory-row__title">
              <button
                class="directory-row__toggle"
                :class="{ 'is-open': isDirectoryExpanded(row.node.id), 'is-placeholder': row.depth > 0 || !row.node.children.length }"
                type="button"
                :aria-label="isDirectoryExpanded(row.node.id) ? `收起${directoryNodeName(row.node)}` : `展开${directoryNodeName(row.node)}`"
                :disabled="row.depth > 0 || !row.node.children.length"
                @click="toggleNode(row.node)"
              >
                <el-icon><CaretRight /></el-icon>
              </button>
              <button class="directory-row__name" type="button" @click="selectNode(row.node)">{{ directoryNodeName(row.node) }}</button>
            </span>
          </div>
        </div>
      </el-card>

      <el-card class="detail-panel" shadow="never">
        <template #header>
          <div class="panel-heading">
            <div>
              <span class="panel-heading__eyebrow">KNOWLEDGE DETAIL</span>
              <h3>知识点详情</h3>
            </div>
          </div>
        </template>

        <template v-if="selectedNode">
          <section class="detail-hero">
            <span v-if="nodeOrdinal(selectedNode)" class="detail-hero__number">{{ nodeOrdinal(selectedNode) }}</span>
            <div class="detail-hero__heading">
              <h2>{{ selectedNode.syllabusTitle }}</h2>
            <div v-if="canPractice(selectedNode) || resumePath" class="detail-hero__actions">
              <el-button v-if="resumePath" @click="returnToExistingPractice">返回原练习</el-button>
              <el-button
                v-if="canPractice(selectedNode)"
                type="primary"
                  :loading="startingNodeId === selectedNode.id"
                  :disabled="selectedNode.questionCount === 0 || Boolean(startingNodeId)"
                  @click="startPractice(selectedNode)"
                >开始练习</el-button>
              </div>
            </div>
            <p>{{ detailDescription(selectedNode) }}</p>
          </section>

          <section class="detail-metrics">
            <div class="metric-card">
              <span>掌握程度</span>
              <strong :class="{ 'is-unassessed': selectedNode.mastery.currentDirectAbility === null }">{{ masteryText(selectedNode) }}</strong>
              <div class="mastery-stars mastery-stars--large">
                <el-icon v-for="star in 5" :key="star" :class="{ 'is-active': star <= starCount(selectedNode) }"><StarFilled /></el-icon>
              </div>
            </div>
            <div class="metric-card">
              <span>重要度</span>
              <strong>{{ importanceText(selectedNode.importance) }}</strong>
              <small>权重 {{ selectedNode.importance }}</small>
            </div>
            <div class="metric-card">
              <span>{{ questionCountLabel(selectedNode) }}</span>
              <strong>{{ selectedNode.questionCount }} 道</strong>
              <small>{{ canPractice(selectedNode) ? '开始后将练习当前范围的全部题目' : '覆盖本章全部下属范围' }}</small>
            </div>
          </section>

          <section v-if="selectedNode.children.length" class="child-summary">
            <h4>下一层知识点</h4>
            <div class="child-summary__items">
              <div v-for="child in selectedNode.children" :key="child.id" class="child-summary__item">
                <button class="child-summary__name" type="button" @click="selectNode(child)">{{ detailChildName(child) }}</button>
                <strong>{{ child.questionCount }} 道</strong>
                <el-button
                  size="small"
                  type="primary"
                  :loading="startingNodeId === child.id"
                  :disabled="child.questionCount === 0 || Boolean(startingNodeId)"
                  @click.stop="startPractice(child)"
                >开始练习</el-button>
              </div>
            </div>
          </section>

          <el-alert v-if="practiceError" class="practice-error" :title="practiceError" type="error" :closable="false" show-icon />
        </template>
        <el-empty v-else description="请选择左侧知识点" :image-size="96" />
      </el-card>
    </section>
  </div>
</template>

<script setup name="StudentKnowledgePractice" lang="ts">
import { CaretRight, Search, StarFilled } from '@element-plus/icons-vue';
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  getKnowledgePracticeSetup,
  startKnowledgePractice,
  type KnowledgePracticeErrorVo,
  type KnowledgePracticeNodeVo,
  type KnowledgePracticeSetupVo
} from '@/api/certmuse/assessment/knowledge-practices';
import { contractErrorFrom, type RequestIdentity } from '@/utils/learning-goal';
import { practiceRequestIdentityFor } from '@/utils/knowledge-practice';

type PracticeNode = KnowledgePracticeNodeVo;

const router = useRouter();
const route = useRoute();
const setup = ref<KnowledgePracticeSetupVo>();
const loading = ref(true);
const loadError = ref('');
const practiceError = ref('');
const resumePath = ref('');
const startingNodeId = ref('');
const selectedSubjectId = ref('');
const selectedNodeId = ref('');
const expandedNodeIds = ref<string[]>([]);
const knowledgeSearch = ref('');
const requestIdentity = ref<RequestIdentity>();
const practiceTargetNode = ref<PracticeNode>();

const subjects = computed(() => setup.value?.subjects ?? []);
const selectedSubject = computed(() => subjects.value.find(item => item.id === selectedSubjectId.value) ?? subjects.value[0]);
const filteredSubjectNodes = computed(() => filterDirectoryByName(selectedSubject.value?.nodes ?? [], knowledgeSearch.value));
const searchExpandedNodeIds = computed(() => knowledgeSearch.value.trim() ? filteredSubjectNodes.value.map(node => node.id) : []);
const visibleRows = computed(() => {
  const rows: Array<{ node: PracticeNode; depth: number }> = [];
  filteredSubjectNodes.value.forEach(node => {
    rows.push({ node, depth: 0 });
    if (isDirectoryExpanded(node.id)) node.children.forEach(child => rows.push({ node: child, depth: 1 }));
  });
  return rows;
});
const selectedNode = computed(() => {
  const subject = selectedSubject.value;
  return subject ? findNode(subject.nodes, selectedNodeId.value) : undefined;
});

onMounted(() => void loadSetup());

async function loadSetup(notice = '') {
  loading.value = true;
  loadError.value = '';
  practiceError.value = notice;
  resumePath.value = '';
  try {
    const response = await getKnowledgePracticeSetup();
    if (!response.data) throw new Error('未取得知识点练习设置数据。');
    setup.value = response.data;
    const requestedNodeId = typeof route.query.knowledgePointId === 'string' ? route.query.knowledgePointId : '';
    const requestedSubject = response.data.subjects.find(subject => findNode(subject.nodes, requestedNodeId));
    const subject = requestedSubject ?? response.data.subjects.find(item => item.id === selectedSubjectId.value) ?? response.data.subjects[0];
    selectedSubjectId.value = subject?.id ?? '';
    const node = subject && findNode(subject.nodes, requestedNodeId || selectedNodeId.value) ? findNode(subject.nodes, requestedNodeId || selectedNodeId.value) : subject?.nodes[0];
    selectedNodeId.value = node?.id ?? '';
    expandedNodeIds.value = requestedNodeId && node
      ? ancestorIds(subject?.nodes ?? [], node.id).filter(id => findNode(subject?.nodes ?? [], id)?.treeDepth === 1)
      : [];
    requestIdentity.value = undefined;
  } catch (error) {
    const contractError = contractErrorFrom(error) as KnowledgePracticeErrorVo | undefined;
    if (contractError?.errorCode === 'LEARNING_GOAL_NOT_ACTIVE') {
      await router.replace('/learning/onboarding');
      return;
    }
    setup.value = undefined;
    loadError.value = messageForError(error, '无法加载知识点练习设置，请稍后重试。');
  } finally {
    loading.value = false;
  }
}

function findNode(nodes: PracticeNode[], id: string): PracticeNode | undefined {
  for (const node of nodes) {
    if (node.id === id) return node;
    const found = findNode(node.children, id);
    if (found) return found;
  }
}

function ancestorIds(nodes: PracticeNode[], targetId: string, parents: string[] = []): string[] {
  for (const node of nodes) {
    if (node.id === targetId) return parents;
    const result = ancestorIds(node.children, targetId, [...parents, node.id]);
    if (result.length || node.children.some(child => child.id === targetId)) return result;
  }
  return [];
}

function filterDirectoryByName(nodes: PracticeNode[], query: string): PracticeNode[] {
  const keyword = query.trim().toLocaleLowerCase();
  return nodes.flatMap(chapter => {
    const sections = chapter.children.filter(child => child.treeDepth === 2);
    if (!keyword || chapter.syllabusTitle.toLocaleLowerCase().includes(keyword)) return [{ ...chapter, children: sections }];
    const matchedSections = sections.filter(section => section.syllabusTitle.toLocaleLowerCase().includes(keyword));
    return matchedSections.length ? [{ ...chapter, children: matchedSections }] : [];
  });
}

function isDirectoryExpanded(id: string) {
  return expandedNodeIds.value.includes(id) || searchExpandedNodeIds.value.includes(id);
}
function toggleNode(node: PracticeNode) {
  if (!node.children.length || knowledgeSearch.value.trim()) return;
  expandedNodeIds.value = expandedNodeIds.value.includes(node.id)
    ? expandedNodeIds.value.filter(item => item !== node.id)
    : [...expandedNodeIds.value, node.id];
}
function clearPracticeState() {
  practiceError.value = '';
  resumePath.value = '';
  requestIdentity.value = undefined;
  practiceTargetNode.value = undefined;
}
function selectNode(node: PracticeNode) {
  selectedNodeId.value = node.id;
  const subject = selectedSubject.value;
  if (subject && !knowledgeSearch.value.trim()) {
    const chapterIds = ancestorIds(subject.nodes, node.id)
      .filter(id => findNode(subject.nodes, id)?.treeDepth === 1);
    expandedNodeIds.value = [...new Set([...expandedNodeIds.value, ...chapterIds])];
  }
  clearPracticeState();
}
function changeSubject() {
  const firstNode = filteredSubjectNodes.value[0];
  selectedNodeId.value = firstNode?.id ?? '';
  expandedNodeIds.value = [];
  clearPracticeState();
}
function canPractice(node: PracticeNode) { return node.treeDepth === 2 || node.treeDepth === 3; }
function lastNumber(number: string): number | undefined {
  const match = number.match(/(?:^|\.)(\d+)$/);
  return match ? Number(match[1]) : undefined;
}
function chineseNumber(value: number): string {
  const digits = ['零', '一', '二', '三', '四', '五', '六', '七', '八', '九'];
  if (value < 10) return digits[value];
  if (value < 20) return `十${value % 10 ? digits[value % 10] : ''}`;
  if (value < 100) return `${digits[Math.floor(value / 10)]}十${value % 10 ? digits[value % 10] : ''}`;
  return String(value);
}
function nodeOrdinal(node: PracticeNode): string {
  if (node.treeDepth !== 1 && node.treeDepth !== 2) return '';
  if (/^第.+[章节]$/.test(node.syllabusNumber)) return node.syllabusNumber;
  const number = lastNumber(node.syllabusNumber);
  if (number === undefined) return node.syllabusNumber;
  return `第${chineseNumber(number)}${node.treeDepth === 1 ? '章' : '节'}`;
}
function directoryNodeName(node: PracticeNode) { return `${nodeOrdinal(node)} ${node.syllabusTitle}`.trim(); }
function detailChildName(node: PracticeNode) { return node.treeDepth === 3 ? node.syllabusTitle : directoryNodeName(node); }
function practiceNodeName(node: PracticeNode) { return node.treeDepth === 3 ? node.syllabusTitle : directoryNodeName(node); }
function detailDescription(node: PracticeNode) {
  if (node.treeDepth === 1) return '展开章节并选择具体小节后开始练习。';
  if (node.treeDepth === 2) return '可练习本节全部题目，也可选择下方知识点进行专项练习。';
  return '开始当前知识点的专项练习。';
}
function questionCountLabel(node: PracticeNode) {
  return node.treeDepth === 1 ? '本章题量' : node.treeDepth === 2 ? '本节题量' : '本知识点题量';
}
function starCount(node: PracticeNode) {
  if (node.mastery.currentDirectAbility === null) return 0;
  return Math.max(1, Math.min(5, Math.ceil(node.mastery.currentDirectAbility / 20)));
}
function masteryText(node: PracticeNode) {
  return ({
    unassessed: '未评估', provisional: '暂定', urgent: '紧急', weak: '薄弱', learning: '学习中', proficient: '熟练',
    mastery_candidate: '掌握候选', mastered: '已掌握', needs_retest: '需复测', pending_verification: '待验证', needs_review: '需复核', expired: '已失效'
  })[node.mastery.profileStatus];
}
function importanceText(importance: PracticeNode['importance']) { return ({ 1: '补充', 2: '常规', 3: '核心' })[importance]; }

async function startPractice(node: PracticeNode) {
  const goal = setup.value?.goal;
  if (!goal || !canPractice(node) || node.questionCount === 0 || startingNodeId.value) return;
  const payload = { knowledgePointId: node.id, expectedGoalVersion: goal.version };
  requestIdentity.value = practiceRequestIdentityFor(payload, requestIdentity.value);
  practiceTargetNode.value = node;
  startingNodeId.value = node.id;
  practiceError.value = '';
  resumePath.value = '';
  try {
    const response = await startKnowledgePractice(payload, requestIdentity.value.id);
    if (!response.data) throw new Error('练习会话创建失败，请稍后重试。');
    await goToPracticeAnswering(response.data.answerPath, node);
  } catch (error) {
    const contractError = contractErrorFrom(error) as KnowledgePracticeErrorVo | undefined;
    const code = contractError?.errorCode;
    if (code === 'LEARNING_GOAL_NOT_ACTIVE') {
      await router.push('/learning/onboarding');
    } else if (code === 'PRACTICE_GOAL_VERSION_CONFLICT') {
      await loadSetup('学习目标已更新，已刷新知识点练习设置。请确认知识点后重新开始。');
    } else if (code === 'PRACTICE_SESSION_IN_PROGRESS' && contractError?.details?.answerPath) {
      practiceError.value = '你有一场尚未结束的练习。';
      resumePath.value = contractError.details.answerPath;
    } else if (code === 'PRACTICE_IDEMPOTENCY_CONFLICT') {
      requestIdentity.value = undefined;
      practiceError.value = '本次请求已失效，请再次点击“开始练习”创建新请求。';
    } else {
      practiceError.value = practiceErrorMessage(code, error);
    }
    // U08 requires a new UUID after a failed start request. The server returns
    // the active session path explicitly when recovery is possible.
    requestIdentity.value = undefined;
  } finally {
    startingNodeId.value = '';
  }
}

function returnToExistingPractice() {
  const node = practiceTargetNode.value ?? selectedNode.value;
  if (resumePath.value && node) void goToPracticeAnswering(resumePath.value, node);
}

async function goToPracticeAnswering(answerPath: string, node: PracticeNode) {
  const target = router.resolve(answerPath);
  await router.push({
    path: target.path,
    query: {
      ...target.query,
      knowledgePointName: practiceNodeName(node)
    }
  });
}
function messageForError(error: unknown, fallback: string) {
  const contractError = contractErrorFrom(error) as KnowledgePracticeErrorVo | undefined;
  return practiceErrorMessage(contractError?.errorCode, error, fallback);
}
function practiceErrorMessage(code: string | undefined, error: unknown, fallback = '当前无法创建练习，请稍后重试。') {
  const messages: Record<string, string> = {
    LEARNING_NOT_ALLOWED: '当前学习目标暂不允许练习，请先完成服务端提示的处理。',
    PRACTICE_DIRECTORY_INVALID: '知识点目录正在维护中，暂时不能开始练习。',
    PRACTICE_NODE_UNAVAILABLE: '该知识点当前没有可练题目，请刷新后重试。',
    PRACTICE_QUESTION_LIMIT_EXCEEDED: '该知识点题量超过首版练习上限，暂时不能创建练习。',
    PRACTICE_RULE_VERSION_UNAVAILABLE: '练习规则暂不可用，请稍后重试。',
    PRACTICE_REQUEST_INVALID: '练习请求无效，请刷新页面后重新开始。',
    KNOWLEDGE_PRACTICE_SYSTEM_FAILURE: '练习服务暂时不可用，请稍后重试。'
  };
  if (code && messages[code]) return messages[code];
  return error instanceof Error && error.message ? error.message : fallback;
}
</script>

<style scoped lang="scss">
.knowledge-practice-page { width: 100%; max-width: none; margin: 0; }
.page-intro { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; margin: 12px 4px 20px; }
.page-intro__eyebrow, .panel-heading__eyebrow { display: block; color: var(--app-accent-strong); font-size: 11px; font-weight: 700; letter-spacing: .12em; }
.page-intro h2 { margin: 5px 0 6px; color: var(--el-text-color-primary); font-size: 26px; }
.page-intro p { margin: 0; color: var(--el-text-color-secondary); }
.page-intro__back { min-width: 112px; margin-top: 12px; font-size: 15px; }
.subject-picker { margin: 0; min-width: 250px; }
.subject-picker--directory { margin: 0 0 12px; }
.subject-picker--directory :deep(.el-form-item__content) { min-width: 0; }
.subject-picker--directory :deep(.el-select) { width: min(100%, 360px); }
.knowledge-search { margin: 0 0 16px; }
.knowledge-search :deep(.el-form-item__content) { min-width: 0; }
.page-state { min-height: 360px; padding: 28px; border: 1px solid var(--el-border-color-light); border-radius: 16px; background: var(--el-bg-color); }
.practice-layout { display: grid; grid-template-columns: minmax(500px, 1.08fr) minmax(430px, .92fr); align-items: start; gap: 20px; }
.syllabus-panel, .detail-panel { border-color: var(--el-border-color-light); border-radius: 16px; }
.panel-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.panel-heading h3 { margin: 4px 0 0; color: var(--el-text-color-primary); font-size: 18px; }
.directory-list { overflow: hidden; border: 1px solid var(--el-border-color-lighter); border-radius: 12px; }
.directory-row { display: flex; align-items: center; width: 100%; min-height: 56px; padding: 10px 16px 10px calc(16px + var(--depth) * 22px); border-bottom: 1px solid var(--el-border-color-lighter); background: var(--el-bg-color); color: var(--el-text-color-primary); text-align: left; transition: background .18s ease, color .18s ease; }
.directory-row:last-child { border-bottom: 0; }
.directory-row:hover { background: var(--el-fill-color-light); }
.directory-row.is-selected { background: var(--el-color-primary-light-9); }
.directory-row.is-selected { box-shadow: inset 3px 0 0 var(--el-color-primary); }
.directory-row__title { display: flex; align-items: center; width: 100%; min-width: 0; gap: 9px; }
.directory-row__toggle { display: inline-flex; align-items: center; justify-content: center; width: 32px; height: 32px; padding: 0; border: 0; border-radius: 6px; background: transparent; color: var(--el-color-primary); cursor: pointer; transition: background .18s ease, transform .18s ease; }
.directory-row__toggle:hover:not(:disabled), .directory-row__toggle:focus-visible { background: var(--el-color-primary-light-9); outline: 2px solid var(--el-color-primary-light-5); outline-offset: 1px; }
.directory-row__toggle.is-open { transform: rotate(90deg); }
.directory-row__toggle.is-placeholder { visibility: hidden; pointer-events: none; }
.directory-row__name { overflow: hidden; flex: 1; padding: 8px 4px; border: 0; border-radius: 6px; background: transparent; color: inherit; cursor: pointer; font-size: 15px; font-weight: 650; text-align: left; text-overflow: ellipsis; white-space: nowrap; }
.directory-row__name:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 1px; }
.mastery-stars { display: inline-flex; gap: 2px; color: var(--el-border-color); }
.mastery-stars .is-active { color: #f59e0b; }
.detail-panel { min-height: 520px; }
.detail-hero { padding: 10px 4px 24px; border-bottom: 1px solid var(--el-border-color-lighter); }
.detail-hero__number { color: var(--app-accent-strong); font-size: 14px; font-weight: 700; }
.detail-hero__heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin: 8px 0; }
.detail-hero__heading h2 { margin: 0; font-size: 25px; line-height: 1.4; }
.detail-hero__actions { display: flex; flex: none; gap: 8px; }
.detail-hero p { margin: 0; color: var(--el-text-color-secondary); line-height: 1.7; }
.detail-metrics { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin: 24px 0; }
.metric-card { min-height: 126px; padding: 17px; border: 1px solid var(--el-border-color-lighter); border-radius: 12px; background: var(--el-fill-color-lighter); }
.metric-card > span, .metric-card small { display: block; color: var(--el-text-color-secondary); font-size: 13px; }
.metric-card strong { display: block; margin: 9px 0 8px; color: var(--el-text-color-primary); font-size: 18px; }
.metric-card strong.is-unassessed { color: var(--el-text-color-secondary); }
.mastery-stars--large { font-size: 17px; }
.child-summary { padding: 4px 0 22px; }
.child-summary h4 { margin: 0 0 10px; font-size: 15px; }
.child-summary__items { display: grid; gap: 8px; }
.child-summary__item { display: grid; grid-template-columns: minmax(0, 1fr) auto auto; align-items: center; gap: 12px; min-height: 48px; padding: 6px 8px 6px 12px; border: 1px solid var(--el-border-color-light); border-radius: 8px; background: var(--el-bg-color); }
.child-summary__name { overflow: hidden; min-height: 36px; padding: 6px 0; border: 0; background: transparent; color: var(--el-text-color-regular); cursor: pointer; font-size: 14px; text-align: left; text-overflow: ellipsis; white-space: nowrap; }
.child-summary__name:hover { color: var(--el-color-primary); }
.child-summary__name:focus-visible { border-radius: 4px; outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.child-summary__item strong { color: var(--el-text-color-secondary); font-size: 13px; font-weight: 600; white-space: nowrap; }
.practice-error { max-width: 480px; margin-top: 14px; }
@media (max-width: 1100px) { .practice-layout { grid-template-columns: 1fr; } .detail-panel { min-height: 0; } }
@media (max-width: 720px) { .page-intro { align-items: stretch; flex-direction: column; } .subject-picker { min-width: 0; } .detail-hero__heading { align-items: stretch; flex-direction: column; } .detail-hero__actions { flex-wrap: wrap; } .detail-metrics { grid-template-columns: 1fr; } .child-summary__item { grid-template-columns: minmax(0, 1fr) auto; } .child-summary__item :deep(.el-button) { grid-column: 1 / -1; width: 100%; min-height: 44px; } }
</style>
