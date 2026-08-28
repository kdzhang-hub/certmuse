<template>
  <div class="collection-editor-page">
    <header class="editor-header">
      <div>
        <el-button link :icon="ArrowLeft" @click="goBack">返回题集列表</el-button>
        <h2>{{ editingId ? '编辑题集草稿' : '新建题集' }}</h2>
      </div>
      <div class="save-state">
        <span v-if="generatedCode" class="system-code">系统编码：{{ generatedCode }}</span>
        <span :class="{ dirty }">{{ dirty ? '有未保存修改' : '草稿已保存' }}</span>
        <el-button type="primary" :icon="DocumentChecked" @click="saveDraft">保存草稿</el-button>
      </div>
    </header>

    <section class="base-panel">
      <header>
        <h3>题集基本信息</h3>
      </header>
      <el-form class="base-form" label-position="top">
        <el-form-item label="题集名称">
          <el-input v-model="form.name" :disabled="isEditingDraft" placeholder="请输入题集名称" />
        </el-form-item>
        <el-form-item label="题集类型">
          <el-select v-model="form.type" :disabled="isEditingDraft">
            <el-option label="首次诊断" value="FIRST_DIAGNOSTIC" />
            <el-option label="模拟试卷" value="SIMULATION" />
            <el-option label="历年真题" value="PAST_PAPER" />
          </el-select>
        </el-form-item>
        <el-form-item label="考试资格">
          <el-select
            v-model="form.certification"
            :disabled="isEditingDraft"
            placeholder="请选择考试资格"
            @change="handleCertificationChange"
          >
            <el-option v-for="item in certificationOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="考试时长（分钟）">
          <el-input-number v-model="form.durationMinutes" :min="1" controls-position="right" />
        </el-form-item>
        <template v-if="form.type === 'PAST_PAPER'">
          <el-form-item label="考试年份">
            <el-input-number v-model="form.examYear" :min="2000" :max="2100" controls-position="right" />
          </el-form-item>
          <el-form-item label="考试批次">
            <el-select v-model="form.examMonth" placeholder="请选择">
              <el-option label="5 月" :value="5" />
              <el-option label="11 月" :value="11" />
            </el-select>
          </el-form-item>
          <el-form-item label="卷型编码">
            <el-input v-model.trim="form.paperTypeCode" maxlength="50" placeholder="例如：AM" />
          </el-form-item>
          <el-form-item label="卷型名称">
            <el-input v-model.trim="form.paperTypeName" maxlength="100" placeholder="例如：综合知识" />
          </el-form-item>
        </template>
      </el-form>
    </section>

    <QuestionSearchPicker
      :syllabus-version-id="form.syllabus"
      :syllabus-label="syllabusLabel"
      :knowledge-directory="activeKnowledgeDirectory"
      :selected-revision-ids="selectedRevisionIds"
      @select="addQuestion"
      @preview="preview"
    />
    <button
      v-if="basketCollapsed"
      type="button"
      class="basket-trigger"
      :style="floatingBasketStyle"
      @pointerdown="startFloatingBasketDrag"
      @click="expandBasket"
    >
      <Collection />
      当前题集
      <b>{{ selectedItems.length }}</b>
    </button>
    <aside v-else class="basket-panel" :style="floatingBasketStyle" aria-label="当前题集编排">
      <header>
        <div class="basket-drag-handle" @pointerdown="startFloatingBasketDrag">
          <h3>当前题集</h3>
        </div>
        <div class="basket-header-actions">
          <el-tag type="success" effect="plain">{{ selectedItems.length }} 题</el-tag>
          <el-button text type="primary" @click="basketCollapsed = true">收起</el-button>
        </div>
      </header>
      <div v-if="selectedItems.length" class="basket-summary">
        <div>
          <span>题集总分</span>
          <strong>{{ totalScore }}</strong>
        </div>
        <div>
          <span>覆盖知识点</span>
          <strong>{{ coveredKnowledgeCount }}</strong>
        </div>
        <div>
          <span>难度分布</span>
          <div class="distribution difficulty-distribution">
            <b>简 {{ difficultyCounts.easy }}</b>
            <b>中 {{ difficultyCounts.medium }}</b>
            <b>难 {{ difficultyCounts.hard }}</b>
          </div>
        </div>
        <div>
          <span>题型分布</span>
          <div class="distribution type-distribution">
            <b>选 {{ typeCounts.CHOICE }}</b>
            <b>案 {{ typeCounts.CASE }}</b>
            <b>论 {{ typeCounts.ESSAY }}</b>
          </div>
        </div>
      </div>
      <el-alert
        v-if="form.type === 'FIRST_DIAGNOSTIC'"
        :title="`首次诊断还需 ${Math.max(0, 50 - selectedItems.length)} 题`"
        type="warning"
        :closable="false"
        show-icon
      />
      <div v-if="selectedItems.length" class="selected-list">
        <article
          v-for="(item, index) in selectedItems"
          :key="item.question.revisionId"
          draggable="true"
          @dragstart="dragIndex = index"
          @dragover.prevent
          @drop="dropAt(index)"
        >
          <span class="seq">{{ index + 1 }}</span>
          <div class="selected-main">
            <strong>{{ item.question.stemSummary }}</strong>
            <small>
              {{ item.question.examSubjectName }} · {{ typeLabel(item.question.questionType) }} ·
              {{ questionStatusLabel(item.question.status) }}
            </small>
          </div>
          <el-input-number
            v-model="item.reportScore"
            class="score-input"
            :min="0.5"
            :step="0.5"
            size="small"
            controls-position="right"
          />
          <div class="item-actions">
            <el-button link type="danger" :icon="Delete" @click="remove(index)" />
          </div>
        </article>
      </div>
      <el-empty v-else :image-size="64" description="从题目列表加入题目" />
      <footer v-if="selectedItems.length">
        <el-button @click="selectedItems = []">清空</el-button>
        <el-button :disabled="selectedItems.length < 2" @click="sortItems">一键排序</el-button>
      </footer>
    </aside>

    <el-dialog v-model="previewVisible" title="题目预览" width="680px">
      <template v-if="previewItem">
        <el-alert
          v-if="previewItem.reviewOpinion"
          class="preview-review-opinion"
          title="驳回原因"
          :description="previewItem.reviewOpinion"
          type="error"
          :closable="false"
          show-icon
        />
        <el-descriptions :column="2" border>
          <el-descriptions-item label="题目编号">{{ previewItem.questionCode }}</el-descriptions-item>
          <el-descriptions-item label="题型">{{ typeLabel(previewItem.questionType) }}</el-descriptions-item>
          <el-descriptions-item label="科目">{{ previewItem.examSubjectName }}</el-descriptions-item>
          <el-descriptions-item label="难度">{{ difficultyLabel(previewItem.difficulty) }}</el-descriptions-item>
        </el-descriptions>
        <section class="preview-content">
          <h4>题干</h4>
          <QuestionRichText :content="previewStem" />
          <template v-if="previewImages.length">
            <h4>题目图片</h4>
            <div class="question-images">
              <figure v-for="image in previewImages" :key="`${image.sortOrder}-${image.url}`">
                <el-image
                  :src="image.url"
                  :alt="image.alt"
                  fit="contain"
                  :preview-src-list="previewImageUrls"
                  preview-teleported
                />
                <figcaption>{{ image.alt || `图片 ${image.sortOrder}` }}</figcaption>
              </figure>
            </div>
          </template>
          <template v-if="previewItem.questionType === 'CHOICE'">
            <h4>选项</h4>
            <ol class="question-options">
              <li v-for="option in previewItem.options" :key="option.label">
                <strong>{{ option.label }}.</strong>
                <QuestionRichText :content="option.content" />
              </li>
            </ol>
          </template>
          <h4>答案与解析</h4>
          <QuestionRichText :content="formatAnswer(previewItem.answer)" />
          <QuestionRichText class="analysis" :content="previewItem.analysis || '尚未填写解析'" />
        </section>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="CertmuseCollectionNew" lang="ts">
import { ArrowLeft, Collection, Delete, DocumentChecked } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, nextTick, onActivated, onBeforeUnmount, onDeactivated, onMounted, reactive, ref, watch } from 'vue';
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router';
import type { KnowledgeTreeNodeVO, SyllabusListVO } from '@/api/certmuse/catalog/knowledge/types';
import type { QuestionListVO, QuestionPreviewVO, QuestionStatus, QuestionType } from '@/api/certmuse/question/types';
import type { KnowledgeDirectoryNode } from '@/components/certmuse/KnowledgeDirectoryPanel.vue';
import { getKnowledgeTree, listSyllabusVersions } from '@/api/certmuse/catalog/knowledge';
import { getQuestionDetail, getQuestionPreview } from '@/api/certmuse/question';
import { formatQuestionPreviewStem, toQuestionImageUrl } from '@/api/certmuse/question/image-url';
import { createCollection, getCollectionRevision, saveCollectionRevision } from '@/api/certmuse/question/collection';
import QuestionSearchPicker from '@/components/certmuse/QuestionSearchPicker.vue';
import QuestionRichText from '@/components/certmuse/QuestionRichText.vue';
import { toCollectionItems } from './model';

interface SelectedItem {
  question: QuestionListVO;
  reportScore: number;
  knowledgePointId?: string;
}
interface PreviewItem extends QuestionPreviewVO {
  questionCode: string;
  questionType: QuestionType;
  examSubjectName: string;
  difficulty: QuestionListVO['difficulty'];
  status: QuestionStatus;
}
const route = useRoute();
const router = useRouter();
const editingId = computed(() => route.query.id as string | undefined);
const editingRevisionId = computed(() => route.query.revisionId as string | undefined);
const sourceCollectionId = computed(() => (route.query.id || route.query.copyOf) as string | undefined);
const emptyForm = () => ({
  name: '',
  type: 'SIMULATION',
  certification: '',
  syllabus: '',
  durationMinutes: 60,
  examYear: undefined as number | undefined,
  examMonth: undefined as number | undefined,
  paperTypeCode: '',
  paperTypeName: '',
  pauseAllowed: true
});
const form = reactive(emptyForm());
const isEditingDraft = computed(() => Boolean(editingRevisionId.value));
const selectedItems = ref<SelectedItem[]>([]);
const dirty = ref(false);
const generatedCode = ref('');
const rowVersion = ref('0');
const previewVisible = ref(false);
const previewItem = ref<PreviewItem>();
const basketCollapsed = ref(false);
const currentCertification = ref(form.certification);
const syllabuses = ref<SyllabusListVO[]>([]);
const knowledgeDirectories = ref<Record<string, KnowledgeDirectoryNode[]>>({});
const dragIndex = ref<number>();
const floatingBasketPosition = ref<{ left: number; top: number }>();
let revertingCertification = false;
let editorLoadSequence = 0;
let floatingBasketDrag:
  | {
      element: HTMLElement;
      originLeft: number;
      originTop: number;
      offsetX: number;
      offsetY: number;
      width: number;
      height: number;
      startX: number;
      startY: number;
    }
  | undefined;
let floatingBasketWasDragged = false;
let floatingBasketPendingPosition: { left: number; top: number } | undefined;
let floatingBasketAnimationFrame: number | undefined;
const certificationOptions = computed(() => {
  const options = new Map<string, string>();
  for (const item of syllabuses.value) options.set(item.certificationId, item.certificationName);
  return [...options].map(([id, name]) => ({ id, name }));
});
const availableSyllabuses = computed(() =>
  syllabuses.value.filter(item => !form.certification || item.certificationId === form.certification)
);
const syllabusLabel = computed(
  () => syllabuses.value.find(x => x.id === form.syllabus)?.certificationName || '未选择考试资格'
);
const previewImages = computed(() =>
  (previewItem.value?.images ?? [])
    .toSorted((left, right) => left.sortOrder - right.sortOrder)
    .map(image => ({ ...image, url: toQuestionImageUrl(image.url) }))
);
const previewImageUrls = computed(() => previewImages.value.map(image => image.url));
const previewStem = computed(() => formatQuestionPreviewStem(previewItem.value?.stem ?? ''));
const activeKnowledgeDirectory = computed(() => knowledgeDirectories.value[form.syllabus] || []);
const selectedRevisionIds = computed(() => selectedItems.value.map(item => item.question.revisionId));
const floatingBasketStyle = computed(() =>
  floatingBasketPosition.value
    ? {
        left: `${floatingBasketPosition.value.left}px`,
        top: `${floatingBasketPosition.value.top}px`,
        right: 'auto',
        bottom: 'auto'
      }
    : undefined
);
const totalScore = computed(() => selectedItems.value.reduce((sum, item) => sum + item.reportScore, 0));
const subjectCount = computed(() => new Set(selectedItems.value.map(item => item.question.examSubjectId)).size);
const coveredKnowledgeCount = computed(
  () => new Set(selectedItems.value.map(item => item.knowledgePointId).filter(Boolean)).size
);
const difficultyCounts = computed(() =>
  selectedItems.value.reduce<Record<'easy' | 'medium' | 'hard', number>>(
    (result, item) => {
      if (item.question.difficulty) result[item.question.difficulty]++;
      return result;
    },
    { easy: 0, medium: 0, hard: 0 }
  )
);
const typeCounts = computed(() =>
  selectedItems.value.reduce<Record<QuestionType, number>>(
    (result, item) => {
      result[item.question.questionType]++;
      return result;
    },
    { CHOICE: 0, CASE: 0, ESSAY: 0 }
  )
);
watch(
  [form, selectedItems],
  () => {
    dirty.value = true;
  },
  { deep: true }
);
onMounted(() => void initializeEditor());
onBeforeUnmount(stopFloatingBasketDrag);
onActivated(() => void initializeEditor());
onDeactivated(() => {
  editorLoadSequence++;
  stopFloatingBasketDrag();
  resetEditor();
});
watch(
  () => route.fullPath,
  () => {
    if (route.path === '/content/collections/new') void initializeEditor();
  }
);

async function initializeEditor() {
  const loadSequence = ++editorLoadSequence;
  resetEditor();
  try {
    const syllabusResult = (await listSyllabusVersions({ pageNum: 1, pageSize: 100 })).data;
    if (loadSequence !== editorLoadSequence) return;
    syllabuses.value = syllabusResult.rows;
    if (editingRevisionId.value) {
      const revision = (await getCollectionRevision(editingRevisionId.value)).data;
      if (loadSequence !== editorLoadSequence) return;
      Object.assign(form, {
        name: revision.collectionName,
        type: revision.collectionType,
        certification: revision.certificationId,
        syllabus: revision.syllabusVersionId,
        durationMinutes: revision.durationMinutes,
        pauseAllowed: true
      });
      generatedCode.value = revision.collectionCode;
      rowVersion.value = revision.rowVersion;
      selectedItems.value = revision.items.map(item => ({
        question: {
          questionId: item.questionId,
          revisionId: item.questionRevisionId,
          revisionNo: 1,
          questionCode: item.questionCode,
          syllabusVersionId: revision.syllabusVersionId,
          syllabusVersionName: revision.syllabusVersionName,
          stemSummary: item.stem,
          examSubjectId: item.examSubjectId,
          examSubjectName: item.examSubjectName,
          questionType: item.questionType as QuestionType,
          difficulty: item.difficulty as QuestionListVO['difficulty'],
          status: item.questionStatus as QuestionStatus,
          updatedTime: revision.updatedTime
        },
        reportScore: Number(item.reportScore),
        knowledgePointId: item.knowledgePointId || undefined
      }));
    } else {
      const draft = sourceCollectionId.value ? demoDrafts[sourceCollectionId.value] : undefined;
      if (draft) {
        Object.assign(form, draft.form);
        generatedCode.value = draft.code;
      }
    }
    if (!syllabuses.value.some(item => item.certificationId === form.certification)) {
      form.certification = certificationOptions.value[0]?.id ?? '';
    }
    if (!availableSyllabuses.value.some(item => item.id === form.syllabus)) {
      form.syllabus = availableSyllabuses.value[0]?.id ?? '';
    }
    currentCertification.value = form.certification;
    await loadKnowledgeTree(form.syllabus);
    if (loadSequence !== editorLoadSequence) return;
    await nextTick();
    dirty.value = false;
  } catch (error) {
    if (loadSequence === editorLoadSequence) {
      ElMessage.error(error instanceof Error && error.message ? error.message : '题集编辑页加载失败');
    }
  }
}
function resetEditor() {
  Object.assign(form, emptyForm());
  selectedItems.value = [];
  generatedCode.value = '';
  rowVersion.value = '0';
  previewVisible.value = false;
  previewItem.value = undefined;
  basketCollapsed.value = false;
  currentCertification.value = '';
  dragIndex.value = undefined;
  revertingCertification = false;
  dirty.value = false;
}
watch(
  () => form.syllabus,
  syllabusVersionId => {
    void loadKnowledgeTree(syllabusVersionId);
  }
);
function typeLabel(type: QuestionType) {
  return { CHOICE: '选择题', CASE: '案例题', ESSAY: '论文题' }[type];
}
function difficultyLabel(value: QuestionListVO['difficulty']) {
  return value ? { easy: '简单', medium: '中等', hard: '困难' }[value] : '未设置';
}
function questionStatusLabel(status: QuestionStatus) {
  return { draft: '草稿', pending_review: '审核中', rejected: '已驳回', published: '已发布' }[status];
}
function formatAnswer(answer: QuestionPreviewVO['answer']) {
  const value = answer?.value;
  if (Array.isArray(value)) return value.length ? value.join('、') : '尚未填写答案';
  return value || '尚未填写答案';
}
function isSelected(revisionId: string) {
  return selectedItems.value.some(item => item.question.revisionId === revisionId);
}
function addQuestion(question: QuestionListVO, knowledgePointId?: string) {
  if (isSelected(question.revisionId)) return;
  const item: SelectedItem = {
    question,
    reportScore: question.questionType === 'CHOICE' ? 1 : 10,
    knowledgePointId
  };
  selectedItems.value.push(item);
  void fillKnowledgePoint(item);
}
async function fillKnowledgePoint(item: SelectedItem) {
  try {
    const detail = (await getQuestionDetail(item.question.questionId, item.question.revisionId)).data;
    const primaryBinding = detail.knowledgeBindings.find(binding => binding.relationRole === 'primary');
    item.knowledgePointId = primaryBinding?.knowledgePointId ?? detail.knowledgeBindings[0]?.knowledgePointId;
  } catch {
    // 保留目录筛选时带入的知识点；题目明细加载失败不影响继续组卷。
  }
}
function remove(index: number) {
  selectedItems.value.splice(index, 1);
}
function sortItems() {
  const typeOrder: Record<QuestionType, number> = { CHOICE: 1, CASE: 2, ESSAY: 3 };
  selectedItems.value.sort(
    (left, right) =>
      typeOrder[left.question.questionType] - typeOrder[right.question.questionType] ||
      left.question.revisionId.localeCompare(right.question.revisionId, undefined, { numeric: true })
  );
  ElMessage.success('已按选择题、案例题、论文题及题目 ID 排序');
}
function dropAt(index: number) {
  if (dragIndex.value === undefined || dragIndex.value === index) return;
  const [item] = selectedItems.value.splice(dragIndex.value, 1);
  selectedItems.value.splice(index, 0, item);
  dragIndex.value = undefined;
}
async function expandBasket() {
  if (floatingBasketWasDragged) {
    floatingBasketWasDragged = false;
    return;
  }
  basketCollapsed.value = false;
  await nextTick();
  clampFloatingBasketToViewport();
}
function startFloatingBasketDrag(event: PointerEvent) {
  if (event.button !== 0) return;
  const element = (event.currentTarget as HTMLElement).closest('.basket-panel, .basket-trigger') as HTMLElement | null;
  if (!element) return;
  const rect = element.getBoundingClientRect();
  floatingBasketDrag = {
    element,
    originLeft: rect.left,
    originTop: rect.top,
    offsetX: event.clientX - rect.left,
    offsetY: event.clientY - rect.top,
    width: rect.width,
    height: rect.height,
    startX: event.clientX,
    startY: event.clientY
  };
  floatingBasketPendingPosition = { left: rect.left, top: rect.top };
  floatingBasketWasDragged = false;
  element.classList.add('is-dragging');
  document.addEventListener('pointermove', moveFloatingBasket);
  document.addEventListener('pointerup', stopFloatingBasketDrag, { once: true });
}
function moveFloatingBasket(event: PointerEvent) {
  if (!floatingBasketDrag) return;
  if (
    Math.abs(event.clientX - floatingBasketDrag.startX) > 3 ||
    Math.abs(event.clientY - floatingBasketDrag.startY) > 3
  ) {
    floatingBasketWasDragged = true;
  }
  const gap = 12;
  floatingBasketPendingPosition = {
    left: Math.max(
      gap,
      Math.min(event.clientX - floatingBasketDrag.offsetX, window.innerWidth - floatingBasketDrag.width - gap)
    ),
    top: Math.max(
      gap,
      Math.min(event.clientY - floatingBasketDrag.offsetY, window.innerHeight - floatingBasketDrag.height - gap)
    )
  };
  if (floatingBasketAnimationFrame !== undefined) return;
  floatingBasketAnimationFrame = requestAnimationFrame(() => {
    floatingBasketAnimationFrame = undefined;
    if (!floatingBasketDrag || !floatingBasketPendingPosition) return;
    const translateX = floatingBasketPendingPosition.left - floatingBasketDrag.originLeft;
    const translateY = floatingBasketPendingPosition.top - floatingBasketDrag.originTop;
    floatingBasketDrag.element.style.transform = `translate3d(${translateX}px, ${translateY}px, 0)`;
  });
}
function stopFloatingBasketDrag() {
  if (floatingBasketAnimationFrame !== undefined) {
    cancelAnimationFrame(floatingBasketAnimationFrame);
    floatingBasketAnimationFrame = undefined;
  }
  if (floatingBasketDrag) {
    floatingBasketDrag.element.style.transform = '';
    floatingBasketDrag.element.classList.remove('is-dragging');
  }
  if (floatingBasketPendingPosition) floatingBasketPosition.value = floatingBasketPendingPosition;
  floatingBasketDrag = undefined;
  floatingBasketPendingPosition = undefined;
  document.removeEventListener('pointermove', moveFloatingBasket);
}
function clampFloatingBasketToViewport() {
  if (!floatingBasketPosition.value) return;
  const element = document.querySelector<HTMLElement>('.basket-panel');
  if (!element) return;
  const rect = element.getBoundingClientRect();
  const gap = 12;
  floatingBasketPosition.value = {
    left: Math.max(gap, Math.min(floatingBasketPosition.value.left, window.innerWidth - rect.width - gap)),
    top: Math.max(gap, Math.min(floatingBasketPosition.value.top, window.innerHeight - rect.height - gap))
  };
}
function toDirectory(nodes: KnowledgeTreeNodeVO[], subjectId: string): KnowledgeDirectoryNode[] {
  const children = new Map<string, KnowledgeDirectoryNode[]>();
  for (const node of nodes.filter(item => item.examSubjectId === subjectId)) {
    const siblings = children.get(node.parentId ?? '') ?? [];
    siblings.push({ id: node.id, label: `${node.syllabusNumber} ${node.syllabusTitle}`.trim() });
    children.set(node.parentId ?? '', siblings);
  }
  const build = (node: KnowledgeDirectoryNode): KnowledgeDirectoryNode => ({
    ...node,
    children: (children.get(node.id) ?? []).map(build)
  });
  return (children.get('') ?? []).map(build);
}
async function loadKnowledgeTree(syllabusVersionId: string) {
  if (!syllabusVersionId || knowledgeDirectories.value[syllabusVersionId]) return;
  const tree = (await getKnowledgeTree(syllabusVersionId)).data;
  knowledgeDirectories.value[syllabusVersionId] = tree.subjects.map(subject => ({
    id: subject.id,
    label: subject.subjectName,
    children: toDirectory(tree.nodes, subject.id)
  }));
}
async function handleCertificationChange(value: string) {
  if (revertingCertification) {
    revertingCertification = false;
    return;
  }
  if (value === currentCertification.value) return;
  if (selectedItems.value.length) {
    try {
      await ElMessageBox.confirm('切换考试资格会清空当前题集中的题目，是否继续？', '切换考试资格', {
        confirmButtonText: '清空并切换',
        cancelButtonText: '取消',
        type: 'warning'
      });
    } catch {
      revertingCertification = true;
      form.certification = currentCertification.value;
      return;
    }
  }
  selectedItems.value = [];
  currentCertification.value = value;
  form.syllabus = availableSyllabuses.value[0]?.id ?? '';
}
async function preview(row: QuestionListVO) {
  const detail = (await getQuestionPreview(row.questionId, row.revisionId)).data;
  previewItem.value = {
    ...detail,
    questionCode: row.questionCode,
    questionType: row.questionType,
    examSubjectName: row.examSubjectName,
    difficulty: row.difficulty,
    status: row.status
  };
  previewVisible.value = true;
}
async function saveDraft() {
  if (!form.name.trim()) {
    ElMessage.warning('请先填写题集名称');
    return;
  }
  if (
    form.type === 'PAST_PAPER' &&
    (!form.examYear || ![5, 11].includes(form.examMonth ?? 0) || !form.paperTypeCode || !form.paperTypeName)
  ) {
    ElMessage.warning('历年真题请填写年份、5 月或 11 月批次、卷型编码和卷型名称');
    return;
  }
  const items = toCollectionItems(
    selectedItems.value.map(item => ({ revisionId: item.question.revisionId, reportScore: item.reportScore }))
  );
  try {
    const payload = {
      collectionName: form.name.trim(),
      collectionType: form.type as 'FIRST_DIAGNOSTIC' | 'PRACTICE' | 'SIMULATION' | 'PAST_PAPER',
      certificationId: form.certification,
      syllabusVersionId: form.syllabus,
      durationMinutes: form.durationMinutes,
      ...(form.type === 'PAST_PAPER'
        ? {
            examYear: form.examYear,
            examMonth: form.examMonth,
            paperTypeCode: form.paperTypeCode,
            paperTypeName: form.paperTypeName
          }
        : {}),
      items,
      ...(editingRevisionId.value ? { rowVersion: rowVersion.value } : {})
    };
    const result = editingRevisionId.value
      ? (await saveCollectionRevision(editingRevisionId.value, payload)).data
      : (await createCollection(payload)).data;
    generatedCode.value = result.collectionCode;
    rowVersion.value = result.rowVersion;
    dirty.value = false;
    ElMessage.success(`题集草稿已保存，共 ${items.length} 题`);
    await router.push('/content/collections');
  } catch (error) {
    ElMessage.error(error instanceof Error && error.message ? error.message : '题集草稿保存失败');
  }
}
async function goBack() {
  if (dirty.value) {
    try {
      await ElMessageBox.confirm('当前有未保存修改，确认返回题集列表吗？', '离开页面');
    } catch {
      return;
    }
    dirty.value = false;
  }
  router.push('/content/collections');
}
onBeforeRouteLeave(async () => {
  if (!dirty.value) return true;
  try {
    await ElMessageBox.confirm('当前有未保存修改，确认离开吗？', '离开页面');
    return true;
  } catch {
    return false;
  }
});

const demoDrafts: Record<
  string,
  {
    code: string;
    form: {
      name: string;
      type: string;
      certification: string;
      syllabus: string;
      durationMinutes: number;
      pauseAllowed: boolean;
    };
    questionIds: string[];
  }
> = {
  'c-1': {
    code: 'COL-20260804-00001',
    form: {
      name: '系统架构设计师首次诊断',
      type: 'FIRST_DIAGNOSTIC',
      certification: 'architect',
      syllabus: 'architect-v2',
      durationMinutes: 150,
      pauseAllowed: true
    },
    questionIds: ['q-1001']
  },
  'c-2': {
    code: 'COL-20260803-00002',
    form: {
      name: '架构基础专项训练',
      type: 'PRACTICE',
      certification: 'architect',
      syllabus: 'architect-v2',
      durationMinutes: 45,
      pauseAllowed: true
    },
    questionIds: ['q-1001']
  },
  'c-3': {
    code: 'COL-20260801-00003',
    form: {
      name: '2026年模拟试卷（一）',
      type: 'SIMULATION',
      certification: 'architect',
      syllabus: 'architect-v2',
      durationMinutes: 240,
      pauseAllowed: true
    },
    questionIds: ['q-1001']
  }
};
</script>

<style scoped lang="scss">
.collection-editor-page {
  min-height: calc(100vh - 84px);
  padding: 10px;
  color: var(--el-text-color-primary);
  background: var(--el-bg-color-page);
  font-size: 13px;
}
.editor-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 10px;
}
.editor-header h2 {
  margin: 4px 0 0;
  font-size: 20px;
}
.editor-header p {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.save-state {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-top: 8px;
}
.save-state span {
  color: #25935c;
  font-size: 12px;
}
.save-state span.dirty {
  color: var(--el-color-warning);
}
.save-state .system-code {
  color: var(--el-text-color-secondary);
  font-family: Consolas, monospace;
}
.base-panel,
.search-panel,
.question-area,
.directory-panel,
.basket-panel {
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  background: var(--el-bg-color);
  box-shadow: 0 4px 14px rgb(15 23 42 / 4%);
}
.base-panel {
  padding: 12px 16px;
  margin-bottom: 10px;
}
.base-panel header h3,
.section-heading h3,
.basket-panel h3,
.table-heading h3 {
  margin: 0;
  font-size: 16px;
}
.base-form {
  display: grid;
  grid-template-columns: 1.5fr 1fr 1fr 0.8fr;
  gap: 12px;
  margin-top: 10px;
}
.base-form :deep(.el-form-item),
.filter-form :deep(.el-form-item) {
  margin-bottom: 0;
}
.base-form :deep(.el-input-number) {
  width: 100%;
}
.search-panel {
  padding: 12px 16px;
  margin-bottom: 10px;
}
.section-heading {
  display: flex;
  justify-content: space-between;
}
.section-heading p {
  margin: 5px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.filter-form {
  display: grid;
  grid-template-columns: 1.7fr 1fr 1fr 1fr auto;
  gap: 12px;
  margin-top: 10px;
  align-items: end;
}
.filter-actions {
  min-height: 32px;
}
.selection-layout {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
}
.directory-panel {
  position: sticky;
  top: 12px;
  min-height: 560px;
  padding: 12px 16px;
}
.directory-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 10px;
}
.directory-header h3 {
  margin: 0;
}
.directory-search {
  margin-bottom: 12px;
}
.directory-tree {
  min-height: 440px;
  max-height: calc(100vh - 300px);
  overflow-y: auto;
  background: transparent;
}
.clear-directory {
  width: 100%;
  margin-top: 8px;
}
.question-area {
  min-width: 0;
  overflow: hidden;
  padding: 12px 16px;
}
.question-area :deep(.el-table) {
  font-size: 12px;
}
.question-area :deep(.el-table__header-wrapper) {
  font-size: 11px;
}
.question-area :deep(.el-table .cell) {
  padding: 0 6px;
}
.table-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.table-heading p {
  margin: 5px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-top: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.basket-panel {
  position: fixed;
  right: 24px;
  bottom: 20px;
  z-index: 2000;
  display: flex;
  width: 500px;
  max-height: 66.667vh;
  flex-direction: column;
  overflow: hidden;
  padding: 12px 16px;
  border-color: rgba(44, 166, 100, 0.42);
  box-shadow: 0 18px 48px rgba(18, 64, 39, 0.22);
}
.basket-panel > header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}
.basket-panel.is-dragging,
.basket-trigger.is-dragging {
  will-change: transform;
  user-select: none;
}
.basket-drag-handle {
  min-width: 0;
  cursor: grab;
  touch-action: none;
  user-select: none;
}
.basket-drag-handle:active,
.basket-trigger:active {
  cursor: grabbing;
}
.basket-header-actions {
  display: flex;
  align-items: center;
  gap: 5px;
}
.basket-trigger {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 2000;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 11px 16px;
  border: 1px solid #2ca664;
  border-radius: 22px;
  color: #fff;
  background: #25935c;
  box-shadow: 0 12px 28px rgba(18, 92, 54, 0.3);
  cursor: pointer;
  touch-action: none;
  user-select: none;
}
.basket-trigger svg {
  width: 16px;
}
.basket-trigger b {
  display: grid;
  min-width: 21px;
  height: 21px;
  place-items: center;
  border-radius: 11px;
  color: #177747;
  background: #fff;
  font-size: 11px;
}
.basket-summary {
  display: grid;
  grid-template-columns: 0.8fr 0.9fr 1.25fr 1.25fr;
  gap: 8px;
  margin: 10px 0;
}
.basket-summary > div {
  display: flex;
  min-width: 0;
  min-height: 72px;
  flex-direction: column;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: 9px;
  background: var(--el-fill-color-light);
}
.basket-summary span {
  display: block;
  color: var(--el-text-color-secondary);
  font-size: 11px;
}
.basket-summary strong {
  font-size: 22px;
  line-height: 1;
}
.distribution {
  display: flex;
  flex-wrap: wrap;
  gap: 3px;
  margin-top: 8px;
}
.distribution b {
  padding: 3px 5px;
  border-radius: 4px;
  font-size: 11px;
  line-height: 1.25;
  white-space: nowrap;
}
.difficulty-distribution b:nth-child(1) {
  color: #167545;
  background: #dcf5e7;
}
.difficulty-distribution b:nth-child(2) {
  color: #a4610a;
  background: #fff0d5;
}
.difficulty-distribution b:nth-child(3) {
  color: #b63642;
  background: #ffe2e5;
}
.type-distribution b:nth-child(1) {
  color: #2167b1;
  background: #e2efff;
}
.type-distribution b:nth-child(2) {
  color: #7c4bb5;
  background: #efe5ff;
}
.type-distribution b:nth-child(3) {
  color: #087b83;
  background: #dcf6f7;
}
.selected-list {
  min-height: 0;
  max-height: none;
  flex: 1 1 auto;
  overflow-y: auto;
  margin-top: 12px;
}
.selected-list article {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) 72px auto;
  gap: 7px;
  align-items: center;
  padding: 7px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  cursor: grab;
}
.seq {
  display: grid;
  width: 24px;
  height: 24px;
  place-items: center;
  border-radius: 7px;
  color: #197b49;
  background: rgba(44, 166, 100, 0.12);
  font-size: 11px;
}
.selected-main {
  min-width: 0;
}
.selected-main strong {
  display: block;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.selected-main small {
  overflow: hidden;
  display: block;
  margin-top: 2px;
  color: var(--el-text-color-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.score-input {
  width: 72px;
}
.item-actions {
  display: flex;
  gap: 1px;
}
.basket-panel footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 14px;
}
.preview-scoring-points {
  display: grid;
  gap: 8px;
  padding-left: 20px;
}
.preview-scoring-points li {
  line-height: 1.6;
}
.preview-scoring-points span {
  display: block;
  color: var(--el-text-color-secondary);
}
.preview-content {
  margin-top: 16px;
}
.preview-review-opinion {
  margin-bottom: 16px;
}
.preview-review-opinion :deep(.el-alert__description) {
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.preview-content h4 {
  margin: 16px 0 8px;
}
.preview-content p {
  line-height: 1.7;
  white-space: pre-wrap;
}
.question-options {
  display: grid;
  gap: 8px;
  padding: 0;
  margin: 0;
  list-style: none;
}
.question-options li {
  display: flex;
  gap: 6px;
  padding: 8px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
  line-height: 1.6;
}
.question-options li :deep(.question-rich-text) {
  flex: 1;
  min-width: 0;
}
.question-options strong {
  margin-right: 6px;
}
.question-images {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 260px), 1fr));
  gap: 12px;
}
.question-images figure {
  min-width: 0;
  padding: 8px;
  margin: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
}
.question-images :deep(.el-image) {
  display: block;
  width: 100%;
  max-height: 320px;
  cursor: zoom-in;
}
.question-images figcaption {
  overflow: hidden;
  margin-top: 7px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.analysis {
  color: var(--el-text-color-secondary);
}
@media (max-width: 1280px) {
  .base-form {
    grid-template-columns: repeat(3, 1fr);
  }
}
@media (max-width: 900px) {
  .filter-form {
    grid-template-columns: repeat(2, 1fr);
  }
  .selection-layout {
    grid-template-columns: 1fr;
  }
  .directory-panel {
    position: static;
    min-height: 0;
  }
  .directory-tree {
    min-height: 240px;
    max-height: 320px;
  }
  .basket-panel {
    right: 12px;
    bottom: 12px;
    width: min(500px, calc(100vw - 24px));
  }
  .selected-list {
    max-height: 420px;
  }
}
@media (max-width: 640px) {
  .collection-editor-page {
    padding: 14px;
  }
  .editor-header {
    gap: 12px;
    flex-direction: column;
  }
  .base-form,
  .filter-form {
    grid-template-columns: 1fr;
  }
}
</style>
