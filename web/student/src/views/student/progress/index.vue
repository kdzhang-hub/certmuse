<template>
  <div class="p-2 app-container learning-profile">
    <section v-if="loading" class="page-state" aria-live="polite"><el-skeleton :rows="8" animated /></section>
    <section v-else-if="loadError" class="page-state">
      <el-result icon="warning" title="学习画像暂不可用" :sub-title="loadError">
        <template #extra><el-button type="primary" @click="load">重新加载</el-button></template>
      </el-result>
    </section>

    <template v-else-if="setup">
      <el-card shadow="never" class="score-card">
        <div class="score-card__body">
          <div class="score-card__intro">
            <span class="score-card__eyebrow">LEARNING PROFILE</span>
            <h2 class="score-card__title">我的学习画像</h2>
            <h3>{{ score?.certificationName ?? setup.goal.certificationName }}</h3>
            <p>基于当前目标下已积累的学习证据，帮你定位下一步该投入的知识点。</p>
          </div>
          <div class="score-card__value">
            <span class="score-card__score-label">总体掌握度</span>
            <template v-if="score?.overallScore !== null && score?.overallScore !== undefined">
              <strong>{{ score.overallScore.toFixed(1) }}</strong><span>/ 100</span>
              <small>更新于 {{ formatTime(score.calculatedTime) }}</small>
            </template>
            <template v-else>
              <strong>--</strong><span>/ 100</span>
              <small>{{ scoreError ? '总体评分暂不可用' : '尚未形成总体评分' }}</small>
            </template>
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="summary-card">
        <template #header>
          <div class="summary-heading">
            <strong>知识点掌握概览</strong>
          </div>
        </template>
        <div class="summary-grid">
          <div v-for="item in statusSummary" :key="item.label" class="summary-item" :class="`summary-item--${item.tone}`">
            <span class="summary-item__label">{{ item.label }}</span>
            <div class="summary-item__metric">
              <strong>{{ item.count }}</strong>
              <el-button class="summary-item__action" @click="filterBySummary(item.filter)">
                查看
                <el-icon><ArrowRight /></el-icon>
              </el-button>
            </div>
          </div>
        </div>
      </el-card>

      <section class="profile-layout">
        <el-card shadow="never" class="syllabus-panel">
          <template #header>
            <div class="panel-heading panel-heading--with-filter">
              <div><h3>考纲目录</h3></div>
              <el-select v-model="statusFilter" class="status-filter" placeholder="全部状态" aria-label="按掌握状态筛选" @change="applyStatusFilter">
                <el-option label="全部状态" value="" />
                <el-option v-for="option in statusFilterOptions" :key="option.value" :label="option.label" :value="option.value" />
              </el-select>
            </div>
          </template>
          <el-form-item label="科目" class="subject-picker">
            <el-select v-model="selectedSubjectId" aria-label="选择科目" :disabled="!setup.subjects.length" @change="changeSubject">
              <el-option v-for="subject in setup.subjects" :key="subject.id" :label="subject.subjectName" :value="subject.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="搜索" class="knowledge-search">
            <el-input v-model="knowledgeSearch" clearable :prefix-icon="Search" placeholder="按知识点名称搜索" aria-label="按知识点名称搜索" />
          </el-form-item>
          <div class="directory-list" role="tree" aria-label="学习画像知识点目录">
            <button v-for="row in visibleRows" :key="row.node.id" class="directory-row" :class="{ 'is-selected': row.node.id === selectedNodeId }" :style="{ '--depth': row.depth }" type="button" role="treeitem" :aria-expanded="row.node.children.length ? isExpanded(row.node.id) : undefined" @click="selectNode(row.node)">
              <span class="directory-row__title">
                <span class="directory-row__toggle" :class="{ 'is-open': isExpanded(row.node.id), 'is-placeholder': !row.node.children.length }" @click.stop="toggleNode(row.node)"><el-icon><CaretRight /></el-icon></span>
                <span>{{ row.node.syllabusNumber }} {{ row.node.syllabusTitle }}</span>
              </span>
              <el-tag size="small" :type="statusTagType(row.node.mastery.profileStatus)" effect="plain">{{ masteryText(row.node) }}</el-tag>
            </button>
          </div>
        </el-card>

        <el-card shadow="never" class="detail-panel">
          <template #header><div class="panel-heading"><span class="panel-heading__eyebrow">KNOWLEDGE DETAIL</span><h3>知识点详情</h3></div></template>
          <template v-if="selectedNode">
            <section class="detail-hero"><span>{{ selectedNode.syllabusNumber }}</span><h2>{{ selectedNode.syllabusTitle }}</h2><p>掌握程度来自当前目标下已保存的学习画像。</p></section>
            <section class="detail-metrics">
              <div class="metric-card"><span>掌握程度</span><strong>{{ masteryText(selectedNode) }}</strong><small>{{ confidenceText(selectedNode.mastery.confidenceLevel) }}</small></div>
              <div class="metric-card"><span>重要度</span><strong>{{ importanceText(selectedNode.importance) }}</strong><small>权重 {{ selectedNode.importance }}</small></div>
              <div class="metric-card"><span>{{ isPracticeNode(selectedNode) ? '本知识点题量' : '下属知识点题量' }}</span><strong>{{ selectedNode.questionCount }} 道</strong><small>{{ isPracticeNode(selectedNode) ? '可进入专项练习' : '请选择下一层知识点练习' }}</small></div>
            </section>
            <section v-if="selectedNode.children.length" class="child-summary"><h4>下一层知识点</h4><button v-for="child in selectedNode.children" :key="child.id" type="button" @click="selectNode(child)"><span>{{ child.syllabusNumber }} {{ child.syllabusTitle }}</span><el-tag size="small" :type="statusTagType(child.mastery.profileStatus)" effect="plain">{{ masteryText(child) }}</el-tag></button></section>
            <section v-if="isPracticeNode(selectedNode)" class="detail-action"><div><h4>{{ selectedNode.questionCount ? '继续专项练习' : '当前暂无可练题目' }}</h4><p>练习会在知识点练习页面中发起。</p></div><el-button type="primary" size="large" :disabled="selectedNode.questionCount === 0" @click="goPractice">去练习</el-button></section>
          </template>
          <el-empty v-else description="请选择左侧知识点" :image-size="96" />
        </el-card>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ArrowRight, CaretRight, Search } from '@element-plus/icons-vue';
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { getKnowledgePracticeSetup, type KnowledgePracticeNodeVo, type KnowledgePracticeSetupVo } from '@/api/certmuse/assessment/knowledge-practices';
import { getOverallScore, type OverallScoreVo } from '@/api/certmuse/learning/overall-score';

type PracticeNode = KnowledgePracticeNodeVo;
type ProfileStatus = PracticeNode['mastery']['profileStatus'];
type SummaryFilter = 'weak_or_urgent' | 'proficient_or_above';
type StatusFilter = ProfileStatus | SummaryFilter | '';
const router = useRouter();
const loading = ref(true);
const loadError = ref('');
const scoreError = ref(false);
const setup = ref<KnowledgePracticeSetupVo>();
const score = ref<OverallScoreVo>();
const selectedSubjectId = ref('');
const selectedNodeId = ref('');
const expandedNodeIds = ref<string[]>([]);
const statusFilter = ref<StatusFilter>('');
const knowledgeSearch = ref('');

const selectedSubject = computed(() => setup.value?.subjects.find(subject => subject.id === selectedSubjectId.value));
const selectedNode = computed(() => selectedSubject.value ? findNode(selectedSubject.value.nodes, selectedNodeId.value) : undefined);
const filteredSubjectNodes = computed(() => selectedSubject.value ? filterTreeByName(filterTreeByLeafStatus(selectedSubject.value.nodes, statusFilter.value), knowledgeSearch.value) : []);
const visibleRows = computed(() => flattenVisible(filteredSubjectNodes.value, expandedNodeIds.value));
const visibleLeaves = computed(() => setup.value ? flattenAll(setup.value.subjects.flatMap(subject => subject.nodes)).filter(node => !node.children.length) : []);
const statusSummary = computed(() => {
  const nodes = visibleLeaves.value;
  return [
    { label: '未评估', count: nodes.filter(node => node.mastery.profileStatus === 'unassessed').length, tone: 'neutral', filter: 'unassessed' as StatusFilter },
    { label: '暂定', count: nodes.filter(node => node.mastery.profileStatus === 'provisional').length, tone: 'muted', filter: 'provisional' as StatusFilter },
    { label: '薄弱 / 紧急', count: nodes.filter(node => ['urgent', 'weak'].includes(node.mastery.profileStatus)).length, tone: 'danger', filter: 'weak_or_urgent' as StatusFilter },
    { label: '学习中', count: nodes.filter(node => node.mastery.profileStatus === 'learning').length, tone: 'primary', filter: 'learning' as StatusFilter },
    { label: '熟练及以上', count: nodes.filter(node => ['proficient', 'mastery_candidate', 'mastered'].includes(node.mastery.profileStatus)).length, tone: 'success', filter: 'proficient_or_above' as StatusFilter }
  ];
});
const statusFilterOptions: Array<{ label: string; value: Exclude<StatusFilter, ''> }> = [
  { label: '未评估', value: 'unassessed' },
  { label: '暂定', value: 'provisional' },
  { label: '薄弱 / 紧急', value: 'weak_or_urgent' },
  { label: '学习中', value: 'learning' },
  { label: '熟练及以上', value: 'proficient_or_above' }
];

watch([knowledgeSearch, filteredSubjectNodes], () => {
  if (!knowledgeSearch.value.trim()) return;
  expandedNodeIds.value = [...new Set([...expandedNodeIds.value, ...branchNodeIds(filteredSubjectNodes.value)])];
});

function findNode(nodes: PracticeNode[], id: string): PracticeNode | undefined {
  for (const node of nodes) { if (node.id === id) return node; const found = findNode(node.children, id); if (found) return found; }
}
function flattenAll(nodes: PracticeNode[]): PracticeNode[] { return nodes.flatMap(node => [node, ...flattenAll(node.children)]); }
function filterTreeByLeafStatus(nodes: PracticeNode[], status: StatusFilter): PracticeNode[] {
  if (!status) return nodes;
  return nodes.flatMap((node) => {
    if (!node.children.length) return matchesStatusFilter(node.mastery.profileStatus, status) ? [node] : [];
    const children = filterTreeByLeafStatus(node.children, status);
    return children.length ? [{ ...node, children }] : [];
  });
}
function matchesStatusFilter(status: ProfileStatus, filter: Exclude<StatusFilter, ''>) {
  if (filter === 'weak_or_urgent') return status === 'weak' || status === 'urgent';
  if (filter === 'proficient_or_above') return ['proficient', 'mastery_candidate', 'mastered'].includes(status);
  return status === filter;
}
function filterTreeByName(nodes: PracticeNode[], query: string): PracticeNode[] {
  const keyword = query.trim().toLocaleLowerCase();
  if (!keyword) return nodes;
  return nodes.flatMap((node) => {
    if (node.syllabusTitle.toLocaleLowerCase().includes(keyword)) return [node];
    const children = filterTreeByName(node.children, keyword);
    return children.length ? [{ ...node, children }] : [];
  });
}
function flattenVisible(nodes: PracticeNode[], expanded: string[], depth = 0): Array<{ node: PracticeNode; depth: number }> {
  return nodes.flatMap((node) => [{ node, depth }, ...(expanded.includes(node.id) ? flattenVisible(node.children, expanded, depth + 1) : [])]);
}
function branchNodeIds(nodes: PracticeNode[]): string[] { return nodes.flatMap(node => node.children.length ? [node.id, ...branchNodeIds(node.children)] : []); }
function ancestorIds(nodes: PracticeNode[], targetId: string, parents: string[] = []): string[] {
  for (const node of nodes) {
    if (node.id === targetId) return parents;
    const result = ancestorIds(node.children, targetId, [...parents, node.id]);
    if (result.length || node.children.some(child => child.id === targetId)) return result;
  }
  return [];
}
function selectNode(node: PracticeNode) {
  selectedNodeId.value = node.id;
  if (selectedSubject.value) expandedNodeIds.value = [...new Set([...expandedNodeIds.value, ...ancestorIds(selectedSubject.value.nodes, node.id)])];
}
function toggleNode(node: PracticeNode) {
  if (!node.children.length) return;
  expandedNodeIds.value = expandedNodeIds.value.includes(node.id) ? expandedNodeIds.value.filter(id => id !== node.id) : [...expandedNodeIds.value, node.id];
}
function isExpanded(id: string) { return expandedNodeIds.value.includes(id); }
function changeSubject() {
  const nodes = filteredSubjectNodes.value;
  const first = nodes[0];
  selectedNodeId.value = first?.id ?? '';
  expandedNodeIds.value = statusFilter.value ? flattenAll(nodes).filter(node => node.children.length).map(node => node.id) : (first?.children.length ? [first.id] : []);
}
function applyStatusFilter() {
  const nodes = filteredSubjectNodes.value;
  expandedNodeIds.value = flattenAll(nodes).filter(node => node.children.length).map(node => node.id);
  if (!findNode(nodes, selectedNodeId.value)) selectedNodeId.value = flattenAll(nodes)[0]?.id ?? '';
}
function filterBySummary(filter: StatusFilter) {
  statusFilter.value = filter;
  applyStatusFilter();
}
function isPracticeNode(node: PracticeNode) { return node.children.length === 0; }
function masteryText(node: PracticeNode) { return ({ unassessed: '未评估', provisional: '暂定', urgent: '紧急', weak: '薄弱', learning: '学习中', proficient: '熟练', mastery_candidate: '掌握候选', mastered: '已掌握', needs_retest: '需复测', pending_verification: '待验证', needs_review: '待复核', expired: '已失效' })[node.mastery.profileStatus]; }
function confidenceText(value: PracticeNode['mastery']['confidenceLevel']) { return ({ unassessed: '暂无作答证据', low: '证据较少', medium: '证据逐步充分', high: '证据较充分', expired: '现有证据已过期' })[value]; }
function importanceText(value: PracticeNode['importance']) { return ({ 1: '补充', 2: '常规', 3: '核心' })[value]; }
function statusTagType(status: PracticeNode['mastery']['profileStatus']): 'danger' | 'warning' | 'success' | 'info' | 'primary' { if (['urgent', 'needs_review'].includes(status)) return 'danger'; if (['weak', 'pending_verification', 'needs_retest', 'expired'].includes(status)) return 'warning'; if (['proficient', 'mastered'].includes(status)) return 'success'; if (['unassessed', 'provisional'].includes(status)) return 'info'; return 'primary'; }
function formatTime(value: string | null) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '--'; }
function goPractice() { if (selectedNode.value) router.push({ path: '/learning/question-bank/knowledge-practice', query: { knowledgePointId: selectedNode.value.id } }); }
async function load() {
  loading.value = true; loadError.value = ''; scoreError.value = false;
  try {
    const setupResponse = await getKnowledgePracticeSetup();
    if (!setupResponse.data) throw new Error('未返回知识点练习目录。');
    setup.value = setupResponse.data;
    const first = setup.value.subjects[0]; selectedSubjectId.value = first?.id ?? ''; selectedNodeId.value = first?.nodes[0]?.id ?? ''; expandedNodeIds.value = first?.nodes[0]?.children.length ? [first.nodes[0].id] : [];
    try { const scoreResponse = await getOverallScore(); score.value = scoreResponse.data; } catch { scoreError.value = true; }
  } catch (error) { loadError.value = error instanceof Error ? error.message : '请稍后重试。'; }
  finally { loading.value = false; }
}
onMounted(() => void load());
</script>

<style lang="scss" scoped>
.learning-profile {
  width: 100%;
  max-width: none;
  margin: 0;
  padding-top: 20px;
  padding-bottom: 28px;
}

.page-state { padding: 32px 0; }

.score-card,
.summary-card,
.syllabus-panel,
.detail-panel {
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 18px;
  box-shadow: 0 12px 32px rgb(30 47 80 / 5%);
}

.score-card {
  position: relative;
  margin-bottom: 0;
  border: 0;
  background:
    radial-gradient(circle at 86% 12%, rgb(255 255 255 / 18%) 0 2px, transparent 2.5px) 0 0 / 23px 23px,
    linear-gradient(118deg, #1e4fc7 0%, #397de8 57%, #64a6f5 100%);
  color: #fff;
  box-shadow: 0 18px 36px rgb(41 106 209 / 22%);

  :deep(.el-card__body) { padding: 22px 28px; }
}

.score-card::after {
  position: absolute;
  right: -82px;
  bottom: -128px;
  width: 35%;
  aspect-ratio: 1;
  height: auto;
  border: 54px solid rgb(255 255 255 / 10%);
  border-radius: 50%;
  content: '';
}

.score-card__body {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 42px;
}

.score-card__intro { flex: 1 1 60%; min-width: 0; }
.score-card__eyebrow,
.panel-heading__eyebrow,
.summary-heading > div > span {
  display: block;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: .14em;
}

.score-card__eyebrow { color: rgb(255 255 255 / 74%); }
.score-card__title { margin: 7px 0 12px; font-size: 29px; letter-spacing: -.03em; }
.score-card h3 { margin: 0; font-size: 22px; font-weight: 600; }
.score-card p { margin: 9px 0 0; color: rgb(255 255 255 / 76%); font-size: 14px; line-height: 1.75; }

.score-card__value {
  flex: 1 1 25%;
  min-width: 0;
  max-width: 100%;
  padding: 17px 22px;
  border: 1px solid rgb(255 255 255 / 20%);
  border-radius: 15px;
  background: rgb(10 55 151 / 18%);
  box-shadow: inset 0 1px 0 rgb(255 255 255 / 14%);
  text-align: right;
}

.score-card__score-label { display: block; margin-bottom: 4px; color: rgb(255 255 255 / 70%); font-size: 12px; }
.score-card__value strong { font-size: 44px; font-variant-numeric: tabular-nums; line-height: 1; }
.score-card__value > span:not(.score-card__score-label) { margin-left: 5px; color: rgb(255 255 255 / 72%); font-size: 14px; }
.score-card__value small { display: block; margin-top: 9px; color: rgb(255 255 255 / 66%); font-size: 12px; }

.summary-card { margin-bottom: 0; }
.summary-card :deep(.el-card__header) { padding: 13px 22px; border-bottom-color: var(--el-border-color-lighter); }
.syllabus-panel :deep(.el-card__header),
.detail-panel :deep(.el-card__header) { padding: 17px 22px; border-bottom-color: var(--el-border-color-lighter); }
.summary-card :deep(.el-card__body) { padding: 16px 22px 22px; }

.summary-heading,
.panel-heading--with-filter { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.panel-heading__eyebrow { margin-bottom: 4px; color: var(--el-color-primary); }
.summary-heading strong { display: block; font-size: 17px; }

.summary-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px; }
.summary-item {
  position: relative;
  min-height: 112px;
  padding: 17px 16px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 13px;
  background: var(--el-fill-color-blank);
}

.summary-item::before { position: absolute; top: 0; left: 0; width: 4px; height: 100%; background: var(--item-accent); content: ''; }
.summary-item::after { position: absolute; right: -19px; bottom: -25px; width: 72px; height: 72px; border: 14px solid var(--item-wash); border-radius: 50%; content: ''; }
.summary-item__label { position: relative; z-index: 1; display: block; color: var(--el-text-color-secondary); font-size: 16px; font-weight: 600; }
.summary-item__metric { position: relative; z-index: 1; margin-top: 12px; }
.summary-item strong { color: var(--item-accent); font-size: 28px; font-variant-numeric: tabular-nums; line-height: 1; }
.summary-item__action {
  position: absolute;
  top: -4px;
  right: 24px;
  display: inline-flex;
  width: 92px;
  height: 36px;
  padding: 0;
  border-color: color-mix(in srgb, var(--item-accent) 34%, transparent);
  border-radius: 10px;
  background: var(--el-bg-color);
  color: var(--item-accent);
  font-size: 14px;
  font-weight: 700;
  line-height: 1;
  box-shadow: 0 3px 8px rgb(31 45 71 / 4%);
  transition: color .18s ease, border-color .18s ease, background .18s ease, box-shadow .18s ease;

  .el-icon { margin-left: 5px; font-size: 16px; transition: transform .18s ease; }

  &:hover {
    border-color: var(--item-accent);
    background: var(--item-wash);
    box-shadow: 0 5px 12px color-mix(in srgb, var(--item-accent) 14%, transparent);
  }

  &:hover .el-icon { transform: translateX(2px); }
}
.summary-item--neutral { --item-accent: #8d99aa; --item-wash: rgb(141 153 170 / 13%); }
.summary-item--muted { --item-accent: #a678c4; --item-wash: rgb(166 120 196 / 13%); }
.summary-item--danger { --item-accent: #e16c72; --item-wash: rgb(225 108 114 / 13%); }
.summary-item--primary { --item-accent: #4389ec; --item-wash: rgb(67 137 236 / 13%); }
.summary-item--success { --item-accent: #37a47b; --item-wash: rgb(55 164 123 / 13%); }

.profile-layout { display: grid; grid-template-columns: minmax(0, .8fr) minmax(0, 1.55fr); gap: 18px; }
.panel-heading h3 { margin: 0; font-size: 17px; }
.status-filter { width: 100%; max-width: 100%; }
.syllabus-panel :deep(.el-card__body) { padding: 18px; }
.detail-panel :deep(.el-card__body) { padding: 24px 26px 26px; }
.subject-picker,
.knowledge-search { margin-bottom: 14px; }
.subject-picker :deep(.el-form-item__label),
.knowledge-search :deep(.el-form-item__label) { color: var(--el-text-color-secondary); font-size: 12px; }
.knowledge-search :deep(.el-form-item__content) { min-width: 0; }
.directory-list { display: grid; gap: 4px; max-height: 590px; padding-right: 3px; overflow: auto; }
.directory-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  min-height: 42px;
  padding: 7px 9px 7px calc(10px + var(--depth) * 18px);
  border: 1px solid transparent;
  border-radius: 9px;
  background: transparent;
  color: var(--el-text-color-primary);
  text-align: left;
  cursor: pointer;
  transition: background-color .16s, border-color .16s, transform .16s;
}

.directory-row:hover { border-color: var(--el-color-primary-light-7); background: var(--el-color-primary-light-9); transform: translateX(2px); }
.directory-row.is-selected { border-color: rgb(64 135 237 / 30%); background: linear-gradient(90deg, var(--el-color-primary-light-8), var(--el-fill-color-light)); box-shadow: 0 5px 12px rgb(64 135 237 / 8%); }
.directory-row__title { display: flex; align-items: center; gap: 6px; min-width: 0; font-size: 13px; }
.directory-row__title > span:last-child { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.directory-row__toggle { display: inline-flex; width: 16px; color: var(--el-text-color-secondary); transition: transform .15s; }
.directory-row__toggle.is-open { transform: rotate(90deg); }
.directory-row__toggle.is-placeholder { visibility: hidden; }

.detail-hero { position: relative; padding: 4px 0 24px; border-bottom: 1px solid var(--el-border-color-lighter); }
.detail-hero::after { position: absolute; right: 0; bottom: -1px; width: 96px; height: 2px; background: var(--el-color-primary); content: ''; }
.detail-hero > span { display: inline-flex; padding: 4px 8px; border-radius: 5px; background: var(--el-color-primary-light-9); color: var(--el-color-primary); font-size: 12px; font-weight: 800; }
.detail-hero h2 { margin: 12px 0 8px; font-size: 26px; letter-spacing: -.025em; }
.detail-hero p { margin: 0; color: var(--el-text-color-secondary); font-size: 14px; }
.detail-metrics { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 13px; margin-top: 22px; }
.metric-card { padding: 17px; border: 1px solid var(--el-border-color-lighter); border-radius: 12px; background: linear-gradient(150deg, var(--el-fill-color-light), var(--el-bg-color)); }
.metric-card span,
.metric-card strong,
.metric-card small { display: block; }
.metric-card span { color: var(--el-text-color-secondary); font-size: 12px; }
.metric-card strong { margin: 9px 0 7px; color: var(--el-text-color-primary); font-size: 20px; }
.metric-card small { color: var(--el-text-color-secondary); font-size: 12px; }
.child-summary { margin-top: 24px; }
.child-summary h4 { margin: 0 0 11px; font-size: 15px; }
.child-summary button { display: flex; align-items: center; justify-content: space-between; width: 100%; padding: 12px; border: 1px solid var(--el-border-color-light); border-radius: 9px; background: var(--el-bg-color); color: var(--el-text-color-primary); cursor: pointer; text-align: left; transition: border-color .16s, box-shadow .16s; }
.child-summary button:hover { border-color: var(--el-color-primary-light-5); box-shadow: 0 5px 14px rgb(64 135 237 / 8%); }
.child-summary button + button { margin-top: 8px; }
.detail-action { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-top: 25px; padding: 18px; border: 1px solid var(--el-color-primary-light-8); border-radius: 13px; background: var(--el-color-primary-light-9); }
.detail-action h4,
.detail-action p { margin: 0; }
.detail-action h4 { font-size: 16px; }
.detail-action p { margin-top: 5px; color: var(--el-text-color-secondary); font-size: 13px; }

@media (max-width: 900px) {
  .profile-layout { grid-template-columns: 1fr; }
  .summary-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}

@media (max-width: 600px) {
  .learning-profile { padding-top: 10px; }
  .score-card :deep(.el-card__body) { padding: 24px; }
  .score-card__body,
  .detail-action { align-items: stretch; flex-direction: column; }
  .score-card__value { text-align: left; }
  .summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .detail-metrics { grid-template-columns: 1fr; }
}
</style>
