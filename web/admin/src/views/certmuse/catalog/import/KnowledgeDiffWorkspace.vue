<template>
  <el-card shadow="hover" class="diff-workspace">
    <template #header>
      <div class="diff-header">
        <div><h3>知识树差异确认</h3><p>所有变化都需要管理员明确确认，高分建议不会自动通过。</p></div>
        <el-tag :type="summary.pendingCount ? 'warning' : 'success'">{{ summary.pendingCount ? `未确认 ${summary.pendingCount}` : '已全部确认' }}</el-tag>
      </div>
    </template>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon class="diff-error" />
    <div class="summary-grid">
      <div v-for="item in summaryItems" :key="item.label" class="summary-item">
        <span>{{ item.label }}</span><strong>{{ item.value }}</strong>
      </div>
    </div>

    <el-form :inline="true" class="diff-filters" @submit.prevent>
      <el-form-item label="科目"><el-select v-model="query.examSubjectId" clearable placeholder="全部科目" @change="search"><el-option v-for="item in subjects" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
      <el-form-item label="差异"><el-select v-model="query.action" clearable placeholder="全部差异" @change="search"><el-option v-for="item in actionOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
      <el-form-item label="状态"><el-select v-model="query.resolutionStatus" clearable placeholder="全部状态" @change="search"><el-option label="未确认" value="pending" /><el-option label="已确认" value="manual_confirmed" /></el-select></el-form-item>
      <el-form-item><el-button type="primary" :loading="loading" @click="search">查询</el-button><el-button @click="resetFilters">重置</el-button></el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="rows" row-key="id" border>
      <el-table-column label="科目" prop="subjectName" width="110" />
      <el-table-column label="新知识点" min-width="180"><template #default="{ row }"><PointLabel :point="row.newPoint" /></template></el-table-column>
      <el-table-column label="旧知识点" min-width="180"><template #default="{ row }"><PointLabel :point="row.oldPoint" /></template></el-table-column>
      <el-table-column label="差异" width="105"><template #default="{ row }"><el-tag :type="actionTag(row.action)">{{ actionLabel(row.action) }}</el-tag></template></el-table-column>
      <el-table-column label="操作" fixed="right" min-width="230"><template #default="{ row }"><el-radio-group :model-value="effectiveDecision(row as KnowledgeDiffRowVO)" :disabled="lockedByParent(row as KnowledgeDiffRowVO) || editingDisabled || resolvingIds.has(row.id)" @change="decision => resolveRow(row, decision as KnowledgeDiffDecision)"><el-radio value="approve">{{ approveLabel(row.action) }}</el-radio><el-radio value="reject">{{ rejectLabel(row.action) }}</el-radio></el-radio-group><el-tag v-if="lockedByParent(row as KnowledgeDiffRowVO)" size="small" type="info">跟随父节点</el-tag></template></el-table-column>
    </el-table>
    <el-pagination v-if="total" v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" class="pagination" layout="total, sizes, prev, pager, next" :page-sizes="[20, 50, 100]" :total="total" @current-change="load" @size-change="handleSizeChange" />

  </el-card>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { getKnowledgeDiff, resolveKnowledgeDiff } from '@/api/certmuse/catalog/import';
import type { KnowledgeDiffDecision, KnowledgeDiffPageVO, KnowledgeDiffQuery, KnowledgeDiffRowVO, KnowledgePointSummaryVO } from '@/api/certmuse/catalog/import/types';
import { getHandledRequestError } from '@/utils/request';

const props = defineProps<{ batchId: string; syllabusVersionId: string; subjectOptions?: { id: string; label: string }[]; disabled?: boolean }>();
const emit = defineEmits<{ summaryChange: [summary: KnowledgeDiffPageVO]; baselineChanged: []; stateInvalid: [] }>();
const PointLabel = defineComponent({ props: { point: Object as () => KnowledgePointSummaryVO | null }, setup(p) { return () => p.point ? h('span', `${p.point.syllabusNumber ?? '—'} ${p.point.syllabusTitle ?? '—'}`) : h('span', '—'); } });

const emptySummary = (): KnowledgeDiffPageVO => ({ rows: [], total: 0, unchangedCount: 0, updateCount: 0, moveCount: 0, addCount: 0, deleteCount: 0, pendingCount: 0, affectedQuestionCount: 0, offlineQuestionCount: 0 });
const query = reactive<KnowledgeDiffQuery>({ pageNum: 1, pageSize: 20, excludeUnchanged: true });
const rows = ref<KnowledgeDiffRowVO[]>([]); const total = ref(0); const summary = ref(emptySummary());
const loading = ref(false); const resolvingIds = reactive(new Set<string>()); const errorMessage = ref('');
const requestIdentity = ref<{ fingerprint: string; id: string }>();
const editingDisabled = computed(() => Boolean(props.disabled));
const subjects = computed(() => [...new Map([...(props.subjectOptions ?? []).map(item => [item.id, { id: item.id, name: item.label }] as const), ...rows.value.map(row => [row.examSubjectId, { id: row.examSubjectId, name: row.subjectName }] as const)]).values()]);
const actionOptions = [{ value: 'update', label: '修改' }, { value: 'move', label: '移动/层级调整' }, { value: 'add', label: '新增' }, { value: 'delete', label: '删除' }];
const summaryItems = computed(() => { const confirmationRequired = summary.value.updateCount + summary.value.moveCount + summary.value.addCount + summary.value.deleteCount; return [{ label: '新增', value: summary.value.addCount }, { label: '修改', value: summary.value.updateCount }, { label: '移动', value: summary.value.moveCount }, { label: '删除', value: summary.value.deleteCount }, { label: '已确认', value: confirmationRequired - summary.value.pendingCount }, { label: '未确认', value: summary.value.pendingCount }, { label: '预计影响题目', value: summary.value.affectedQuestionCount }, { label: '预计转草稿', value: summary.value.offlineQuestionCount }]; });

async function load() { loading.value = true; errorMessage.value = ''; try { const response = await getKnowledgeDiff(props.batchId, cleanQuery()); const page = requireData(response.data); rows.value = page.rows; total.value = page.total; summary.value = page; emit('summaryChange', page); } catch (error) { handleError(error); } finally { loading.value = false; } }
function cleanQuery() { return { ...query }; }
function search() { query.pageNum = 1; void load(); }
function resetFilters() { Object.assign(query, { examSubjectId: undefined, action: undefined, resolutionStatus: undefined, excludeUnchanged: true, pageNum: 1 }); void load(); }
function handleSizeChange() { query.pageNum = 1; void load(); }
function descendantsOf(root: KnowledgeDiffRowVO) { const result = [root]; const pending = new Set([root.id]); for (let changed = true; changed;) { changed = false; for (const row of rows.value) if (row.parentDiffId && pending.has(row.parentDiffId) && !pending.has(row.id)) { pending.add(row.id); result.push(row); changed = true; } } return result; }
async function resolveRow(value: unknown, decision: KnowledgeDiffDecision) { const row = value as KnowledgeDiffRowVO; const followsChildren = row.action === 'move' || (row.action === 'add' && decision === 'reject'); const affected = followsChildren ? descendantsOf(row) : [row]; const previous = new Map(affected.map(item => [item.id, item.resolutionDecision])); affected.forEach(item => { item.resolutionDecision = decision; }); resolvingIds.add(row.id); errorMessage.value = ''; const payload = { decision, oldKnowledgePointId: null }; try { await resolveKnowledgeDiff(props.batchId, row.id, payload, requestIdFor({ diffId: row.id, ...payload })); requestIdentity.value = undefined; ElMessage.success('差异确认成功'); setTimeout(() => { void load(); }, 600); } catch (error) { affected.forEach(item => { item.resolutionDecision = previous.get(item.id) ?? null; }); handleError(error); } finally { resolvingIds.delete(row.id); } }
function parentRow(row: KnowledgeDiffRowVO) { return row.parentDiffId ? rows.value.find(item => item.id === row.parentDiffId) : undefined; }
function lockedByParent(row: KnowledgeDiffRowVO): boolean { const parent = parentRow(row); return Boolean(parent && (lockedByParent(parent) || (parent.action === 'move' && parent.resolutionDecision) || (parent.action === 'add' && parent.resolutionDecision === 'reject'))); }
function effectiveDecision(row: KnowledgeDiffRowVO) { const parent = parentRow(row); return lockedByParent(row) && parent ? effectiveDecision(parent) : row.resolutionDecision; }
function approveLabel(action: string) { return action === 'add' ? '确认新增' : action === 'delete' ? '确认删除' : action === 'move' ? '确认移动' : '确认修改'; }
function rejectLabel(action: string) { return action === 'add' ? '拒绝新增' : action === 'delete' ? '继续保留' : action === 'move' ? '拒绝移动' : '拒绝修改'; }
function requestIdFor(payload: unknown) { const fingerprint = JSON.stringify(payload); if (requestIdentity.value?.fingerprint === fingerprint) return requestIdentity.value.id; const id = crypto.randomUUID(); requestIdentity.value = { fingerprint, id }; return id; }
function handleError(error: unknown) { const handled = getHandledRequestError<{ errorCode?: string }>(error); const detail = handled?.responseData ?? (error as { response?: { data?: { data?: { errorCode?: string } } } })?.response?.data?.data; const code = detail?.errorCode; const message = error instanceof Error ? error.message : '差异请求失败，请稍后重试。'; if (code === 'KNOWLEDGE_TREE_BASELINE_CHANGED') { errorMessage.value = '正式知识树已变化，当前批次不能继续，请重新上传并预检。'; emit('baselineChanged'); } else if (code === 'IMPORT_BATCH_STATE_INVALID') { errorMessage.value = '批次状态已变化，已禁止继续编辑，请刷新进度。'; emit('stateInvalid'); } else if (code === 'KNOWLEDGE_DIFF_MAPPING_CONFLICT') { errorMessage.value = `${message} 请核对旧知识点是否已被其他差异继承。`; void load(); } else if (code === 'KNOWLEDGE_DIFF_UNRESOLVED') { errorMessage.value = message; void load(); } else errorMessage.value = code ? `${message}（${code}）` : message; }
function requireData<T>(data: T | undefined) { if (data === undefined) throw new Error('差异查询响应缺少 data。'); return data; }
function actionLabel(action: string) { return actionOptions.find(item => item.value === action)?.label ?? action; }
function actionTag(action: string) { return action === 'delete' ? 'danger' : action === 'add' ? 'success' : action === 'unchanged' ? 'info' : 'warning'; }
watch(() => props.batchId, () => { void load(); });
onMounted(load);
defineExpose({ reload: load });
</script>

<style scoped lang="scss">
.diff-workspace { margin-top: 10px; }
.diff-header { display:flex; justify-content:space-between; gap:12px; align-items:flex-start; h3,p{margin:0} p{margin-top:6px;color:var(--el-text-color-secondary)} }
.diff-error,.summary-grid,.diff-filters { margin-bottom:14px; }
.summary-grid { display:grid; grid-template-columns:repeat(8,minmax(90px,1fr)); gap:8px; }
.summary-item { display:grid; gap:4px; padding:10px; background:var(--el-fill-color-light); border-radius:6px; span{color:var(--el-text-color-secondary);font-size:12px} strong{font-size:20px} }
.diff-filters :deep(.el-select) { width:145px; }
.pagination { justify-content:flex-end; margin:14px 0; }
@media(max-width:1000px){.summary-grid{grid-template-columns:repeat(4,1fr)}}
</style>
