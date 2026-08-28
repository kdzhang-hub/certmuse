<template>
  <div class="p-2 knowledge-page">
    <div class="search-wrap">
      <el-card shadow="hover" class="search-panel" :class="{ 'is-collapsed': searchCollapsed }">
        <template #header>
          <div class="panel-heading search-panel-toggle" @click.stop="searchCollapsed = !searchCollapsed">
            <div>
              <span class="panel-kicker">Search Filters</span>
              <h3>筛选条件</h3>
            </div>
          </div>
        </template>
        <el-form class="query-form" :inline="true">
          <el-form-item label="考纲名称 / 年份">
            <el-input
              v-model="filters.keyword"
              clearable
              placeholder="如：2026、系统架构"
              :prefix-icon="Search"
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="考试资格">
            <el-select v-model="filters.certificationId" clearable placeholder="全部资格">
              <el-option
                v-for="option in certificationOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="filters.status" clearable placeholder="全部状态">
              <el-option label="已有考纲" value="available" />
              <el-option label="暂无考纲" value="empty" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" :loading="syllabusLoading" @click="handleSearch">搜索</el-button>
            <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>

    <el-card v-if="hasSearched" v-loading="syllabusLoading" shadow="hover" class="table-panel syllabus-panel">
      <template #header>
        <div class="toolbar-shell">
          <div class="table-heading">
            <span class="panel-kicker">Syllabus Dataset</span>
            <h3>考纲列表</h3>
          </div>
          <div class="toolbar-actions">
            <el-button
              v-hasPermi="['certmuse:catalog:import']"
              type="primary"
              plain
              :icon="Plus"
              @click="openImportDialog"
            >
              新增考纲
            </el-button>
            <el-button
              v-hasPermi="['certmuse:catalog:knowledge:remove']"
              type="danger"
              plain
              :icon="Delete"
              :disabled="selectedSyllabuses.length === 0"
              @click="handleDeleteSelectedSyllabuses"
            >
              删除考纲
            </el-button>
            <right-toolbar :search="false" @query-table="loadSyllabuses" />
          </div>
        </div>
      </template>
      <el-table
        :data="syllabuses"
        border
        class="data-table"
        row-key="id"
        empty-text="没有符合条件的考纲"
        @selection-change="handleSyllabusSelectionChange"
      >
        <el-table-column type="selection" width="50" align="center" />
        <el-table-column label="考试资格" prop="certificationName" min-width="240" show-overflow-tooltip />
        <el-table-column label="发布日期" prop="publishedDate" width="120" align="center" />
        <el-table-column label="更新时间" width="170" align="center">
          <template #default="{ row }">{{ formatDateTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="105" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'available' ? 'success' : 'info'" effect="light">
              {{ row.status === 'available' ? '已有考纲' : '暂无考纲' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="更新考纲" placement="top">
              <el-button
                v-hasPermi="['certmuse:catalog:import']"
                link
                type="primary"
                :icon="Plus"
                aria-label="更新考纲"
                class="syllabus-add"
                @click.stop="openImportForSyllabus(scope.row as SyllabusListVO)"
              />
            </el-tooltip>
            <el-tooltip content="查看知识点" placement="top">
              <el-button
                link
                type="primary"
                :icon="Search"
                aria-label="查看知识点"
                class="syllabus-result"
                @click.stop="selectSyllabus(scope.row.id)"
              />
            </el-tooltip>
            <el-tooltip content="编辑发布日期" placement="top">
              <el-button
                v-hasPermi="['certmuse:catalog:knowledge:edit']"
                link
                type="primary"
                :icon="Edit"
                aria-label="编辑发布日期"
                class="syllabus-edit"
                @click.stop="openPublishedDateDialog(scope.row as SyllabusListVO)"
              />
            </el-tooltip>
            <el-tooltip content="删除考纲" placement="top">
              <el-button
                v-hasPermi="['certmuse:catalog:knowledge:remove']"
                link
                type="danger"
                :icon="Delete"
                aria-label="删除考纲"
                class="syllabus-delete"
                @click.stop="handleDeleteSyllabus(scope.row as SyllabusListVO)"
              />
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
      <Pagination
        v-model:page="syllabusPagination.pageNum"
        v-model:limit="syllabusPagination.pageSize"
        :total="displayedSyllabusTotal"
        @pagination="loadSyllabuses"
      />
    </el-card>

    <el-row v-if="selectedSyllabus" v-loading="treeLoading" :gutter="20" class="content-grid management-layout">
      <el-col
        :lg="directoryCollapsed ? 1 : 5"
        :xs="24"
        class="tree-panel-col"
        :class="{ 'is-collapsed': directoryCollapsed }"
      >
        <el-card shadow="hover" class="tree-panel-shell side-panel" :class="{ 'is-collapsed': directoryCollapsed }">
          <template #header>
            <div
              class="panel-heading tree-panel-header"
              :class="{ 'is-collapsed': directoryCollapsed }"
              @click.stop="directoryCollapsed = !directoryCollapsed"
            >
              <div v-show="!directoryCollapsed">
                <span class="panel-kicker">Knowledge Tree</span>
                <h3>知识目录</h3>
              </div>
            </div>
          </template>
          <template v-if="!directoryCollapsed">
            <el-input v-model="directoryKeyword" clearable placeholder="搜索知识点" :prefix-icon="Search" />
            <div class="directory-tree-scroll">
              <el-tree
                ref="directoryTreeRef"
                class="directory-tree"
                node-key="key"
                :data="navigationTree"
                :props="treeProps"
                :filter-node-method="filterDirectoryNode"
                default-expand-all
                highlight-current
                @node-click="handleDirectoryClick"
              >
                <template #default="{ data }">
                  <span class="directory-node">
                    <span class="node-label">
                      <Collection v-if="data.nodeId === null" />
                      <FolderOpened v-else />
                      {{ data.label }}
                    </span>
                  </span>
                </template>
              </el-tree>
            </div>
          </template>
        </el-card>
      </el-col>

      <el-col
        :lg="directoryCollapsed ? 23 : 19"
        :xs="24"
        class="tree-content-col content-main"
        :class="{ 'is-tree-collapsed': directoryCollapsed }"
      >
        <div class="search-wrap leaf-search-wrap">
          <el-card shadow="hover" class="search-panel">
            <template #header>
              <div class="panel-heading">
                <div>
                  <span class="panel-kicker">Knowledge Filters</span>
                  <h3>知识点筛选</h3>
                </div>
              </div>
            </template>
            <el-form class="query-form" :inline="true">
              <el-form-item label="知识点编号">
                <el-input v-model="leafFilters.number" clearable placeholder="请输入知识点编号" />
              </el-form-item>
              <el-form-item label="知识点名称">
                <el-input
                  v-model="leafFilters.title"
                  clearable
                  placeholder="请输入知识点名称"
                  @keyup.enter="applyLeafFilters"
                />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :icon="Search" @click="applyLeafFilters">搜索</el-button>
                <el-button :icon="Refresh" @click="resetLeafFilters">重置</el-button>
              </el-form-item>
            </el-form>
          </el-card>
        </div>

        <el-card shadow="hover" class="table-panel leaf-table-panel">
          <template #header>
            <div class="toolbar-shell">
              <div class="table-heading">
                <span class="panel-kicker">Knowledge Dataset</span>
                <h3>知识点列表</h3>
              </div>
              <div class="toolbar-actions list-heading-actions">
                <el-tag type="success" effect="plain">共 {{ filteredLeaves.length }} 条</el-tag>
              </div>
            </div>
          </template>

          <div class="leaf-table-wrap">
            <el-table
              :data="pagedLeaves"
              border
              height="100%"
              class="data-table knowledge-table"
              empty-text="所选目录下暂无知识点"
            >
              <el-table-column label="知识点编号" prop="syllabusNumber" min-width="140" />
              <el-table-column label="知识点名称" prop="syllabusTitle" min-width="260" show-overflow-tooltip />
              <el-table-column label="考试科目" min-width="150" show-overflow-tooltip>
                <template #default="scope">{{ subjectNameById.get(scope.row.examSubjectId) ?? '—' }}</template>
              </el-table-column>
              <el-table-column label="层级" prop="treeDepth" width="84" align="center" />
              <el-table-column label="父级目录" min-width="140" show-overflow-tooltip>
                <template #default="scope">{{ parentNumberById.get(scope.row.parentId ?? '') ?? '—' }}</template>
              </el-table-column>
              <el-table-column label="更新时间" min-width="180">
                <template #default="scope">{{ formatDateTime(scope.row.updatedTime) }}</template>
              </el-table-column>
            </el-table>
          </div>

          <div class="table-footer">
            <span>考纲：{{ selectedSyllabus.displayName }}</span>
            <el-pagination
              v-model:current-page="pagination.pageNum"
              v-model:page-size="pagination.pageSize"
              :page-sizes="[10, 20, 50, 100]"
              :total="filteredLeaves.length"
              layout="total, sizes, prev, pager, next, jumper"
            />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-else class="selection-empty" description="请先从上方选择一个考纲" />

    <ImportDialog
      v-model="importVisible"
      import-type="knowledge_point"
      :initial-syllabus-version-id="initialImportSyllabusVersionId || undefined"
      @closed="loadSyllabuses"
    />
    <el-dialog v-model="publishedDateDialogVisible" title="编辑发布日期" width="min(420px, calc(100vw - 32px))" destroy-on-close>
      <el-form label-position="top" @submit.prevent="submitPublishedDate">
        <el-form-item label="考纲"><span>{{ editingSyllabus?.displayName }}</span></el-form-item>
        <el-form-item label="发布日期" required>
          <el-date-picker v-model="publishedDateForm.publishedDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择发布日期" class="w-full" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="publishedDateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="publishedDateSaving" @click="submitPublishedDate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { Collection, Delete, Edit, FolderOpened, Plus, Refresh, Search } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox, type ElTree } from 'element-plus';
import { computed, nextTick, onActivated, onDeactivated, onMounted, reactive, ref, watch } from 'vue';
import type { KnowledgeTreeNodeVO, KnowledgeTreeVO, SyllabusListVO } from '@/api/certmuse/catalog/knowledge/types';
import { deleteSyllabusVersion, getKnowledgeTree, listSyllabusVersions, updateSyllabusPublishedDate } from '@/api/certmuse/catalog/knowledge';
import { listQualifications } from '@/api/certmuse/catalog/subject-version';
import Pagination from '@/components/Pagination/index.vue';
import ImportDialog from '@/views/certmuse/catalog/import/ImportDialog.vue';

defineOptions({ name: 'CertmuseKnowledge' });

interface NavigationNode {
  id: string;
  key: string;
  label: string;
  nodeId: string | null;
  subjectId: string;
  path: string;
  children: NavigationNode[];
}

const filters = reactive({ keyword: '', certificationId: '', status: '' as '' | SyllabusListVO['status'] });
const leafFilters = reactive({ number: '', title: '' });
const pagination = reactive({ pageNum: 1, pageSize: 20 });
const syllabusPagination = reactive({ pageNum: 1, pageSize: 10 });
const syllabuses = ref<SyllabusListVO[]>([]);
const syllabusTotal = ref(0);
const selectedSyllabusId = ref('');
const selectedSyllabuses = ref<SyllabusListVO[]>([]);
const importVisible = ref(false);
const initialImportSyllabusVersionId = ref('');
const publishedDateDialogVisible = ref(false);
const publishedDateSaving = ref(false);
const editingSyllabus = ref<SyllabusListVO>();
const publishedDateForm = reactive({ publishedDate: '' });
const knowledgeTree = ref<KnowledgeTreeVO | null>(null);
const selectedNavigationKey = ref('');
const syllabusLoading = ref(false);
const treeLoading = ref(false);
const searchCollapsed = ref(false);
const directoryCollapsed = ref(false);
const hasSearched = ref(false);
const directoryTreeRef = ref<InstanceType<typeof ElTree>>();
const directoryKeyword = ref('');
const certificationOptions = ref<Array<{ value: string; label: string }>>([]);
const treeProps = { children: 'children', label: 'label' };
let refreshOnActivation = false;

const selectedSyllabus = computed(() => syllabuses.value.find(item => item.id === selectedSyllabusId.value));
const displayedSyllabusTotal = computed(() => Math.max(syllabusTotal.value, syllabuses.value.length));
const nodeById = computed(() => new Map((knowledgeTree.value?.nodes ?? []).map(node => [node.id, node])));
const childrenByParent = computed(() => {
  const result = new Map<string, KnowledgeTreeNodeVO[]>();
  for (const node of knowledgeTree.value?.nodes ?? []) {
    const key = node.parentId ?? `subject:${node.examSubjectId}`;
    const values = result.get(key) ?? [];
    values.push(node);
    result.set(key, values);
  }
  result.forEach((values, key) => result.set(key, values.toSorted(compareNodes)));
  return result;
});
const subjectNameById = computed(
  () => new Map((knowledgeTree.value?.subjects ?? []).map(subject => [subject.id, subject.subjectName]))
);
const parentNumberById = computed(
  () => new Map((knowledgeTree.value?.nodes ?? []).map(node => [node.id, node.syllabusNumber]))
);
const allLeaves = computed(() => (knowledgeTree.value?.nodes ?? []).filter(node => !hasChildren(node.id)));
const navigationTree = computed<NavigationNode[]>(() =>
  (knowledgeTree.value?.subjects ?? []).map(subject => {
    const roots = childrenByParent.value.get(`subject:${subject.id}`) ?? [];
    const path = subject.subjectName;
    return {
      id: `subject:${subject.id}`,
      key: `subject:${subject.id}`,
      label: subject.subjectName,
      nodeId: null,
      subjectId: subject.id,
      path,
      children: roots.filter(node => hasChildren(node.id)).map(node => buildNavigationNode(node, path))
    };
  })
);
const navigationByKey = computed(() => {
  const result = new Map<string, NavigationNode>();
  const visit = (nodes: NavigationNode[]) => {
    nodes.forEach(node => {
      result.set(node.key, node);
      visit(node.children);
    });
  };
  visit(navigationTree.value);
  return result;
});
const selectedNavigation = computed(() => navigationByKey.value.get(selectedNavigationKey.value));
const selectedLeaves = computed(() => {
  const navigation = selectedNavigation.value;
  if (!navigation) return [];
  return collectLeaves(navigation.nodeId, navigation.subjectId);
});
const hasLeafFilters = computed(() => Object.values(leafFilters).some(value => value.trim()));
const filteredLeaves = computed(() => {
  const number = leafFilters.number.trim().toLowerCase();
  const title = leafFilters.title.trim().toLowerCase();
  const source = hasLeafFilters.value ? allLeaves.value : selectedLeaves.value;
  return source.filter(node => {
    const matchesNumber = !number || node.syllabusNumber.toLowerCase().includes(number);
    const matchesTitle = !title || node.syllabusTitle.toLowerCase().includes(title);
    return matchesNumber && matchesTitle;
  });
});
const pagedLeaves = computed(() => {
  const start = (pagination.pageNum - 1) * pagination.pageSize;
  return filteredLeaves.value.slice(start, start + pagination.pageSize);
});
const knowledgePointTotal = computed(() => knowledgeTree.value?.summary.knowledgePointCount ?? 0);

watch([() => leafFilters.number, () => leafFilters.title], () => {
  pagination.pageNum = 1;
});

watch(directoryKeyword, value => directoryTreeRef.value?.filter(value));

function compareNodes(left: KnowledgeTreeNodeVO, right: KnowledgeTreeNodeVO) {
  return (
    left.syllabusNumber.localeCompare(right.syllabusNumber, 'zh-CN', { numeric: true }) ||
    left.sortOrder - right.sortOrder
  );
}

function filterDirectoryNode(value: string, data: NavigationNode) {
  return !value.trim() || `${data.label} ${data.path}`.toLowerCase().includes(value.trim().toLowerCase());
}

function hasChildren(nodeId: string) {
  return (childrenByParent.value.get(nodeId)?.length ?? 0) > 0;
}

function collectLeaves(nodeId: string | null, subjectId: string) {
  if (nodeId === null) return allLeaves.value.filter(node => node.examSubjectId === subjectId).toSorted(compareNodes);
  const leaves: KnowledgeTreeNodeVO[] = [];
  const visit = (id: string) => {
    const children = childrenByParent.value.get(id) ?? [];
    children.forEach(child => {
      if (hasChildren(child.id)) visit(child.id);
      else leaves.push(child);
    });
  };
  visit(nodeId);
  return leaves.toSorted(compareNodes);
}

function buildNavigationNode(node: KnowledgeTreeNodeVO, parentPath: string): NavigationNode {
  const path = `${parentPath} / ${node.syllabusNumber} ${node.syllabusTitle}`;
  const directoryChildren = (childrenByParent.value.get(node.id) ?? []).filter(child => hasChildren(child.id));
  return {
    id: `node:${node.id}`,
    key: `node:${node.id}`,
    label: `${node.syllabusNumber} ${node.syllabusTitle}`,
    nodeId: node.id,
    subjectId: node.examSubjectId,
    path,
    children: directoryChildren.map(child => buildNavigationNode(child, path))
  };
}

async function loadSyllabuses() {
  syllabusLoading.value = true;
  try {
    const response = await listSyllabusVersions({
      keyword: filters.keyword.trim() || undefined,
      certificationId: filters.certificationId || undefined,
      status: filters.status || undefined,
      pageNum: syllabusPagination.pageNum,
      pageSize: syllabusPagination.pageSize
    });
    syllabuses.value = response.data.rows;
    syllabusTotal.value = response.data.total;
    selectedSyllabuses.value = [];
  } catch {
    ElMessage.error('考纲查询失败，请稍后重试');
  } finally {
    syllabusLoading.value = false;
  }
}

async function loadCertificationOptions() {
  const pageSize = 100;
  const firstPage = await listQualifications({ status: '0', pageNum: 1, pageSize });
  const rows = [...firstPage.data.rows];
  const pageCount = Math.ceil(firstPage.data.total / pageSize);
  for (let pageNum = 2; pageNum <= pageCount; pageNum += 1) {
    const page = await listQualifications({ status: '0', pageNum, pageSize });
    rows.push(...page.data.rows);
  }
  certificationOptions.value = rows.map(item => ({ value: item.id, label: item.certificationName }));
}

function openImportDialog() {
  initialImportSyllabusVersionId.value = '';
  importVisible.value = true;
}

function openImportForSyllabus(syllabus: SyllabusListVO) {
  initialImportSyllabusVersionId.value = syllabus.id;
  importVisible.value = true;
}

function openPublishedDateDialog(syllabus: SyllabusListVO) {
  editingSyllabus.value = syllabus;
  publishedDateForm.publishedDate = syllabus.publishedDate ?? '';
  publishedDateDialogVisible.value = true;
}

async function submitPublishedDate() {
  if (!editingSyllabus.value) return;
  if (!publishedDateForm.publishedDate) {
    ElMessage.warning('请选择发布日期');
    return;
  }
  publishedDateSaving.value = true;
  try {
    await updateSyllabusPublishedDate(editingSyllabus.value.id, { publishedDate: publishedDateForm.publishedDate });
    ElMessage.success('发布日期已更新');
    publishedDateDialogVisible.value = false;
    await loadSyllabuses();
  } catch {
    ElMessage.error('更新发布日期失败，请稍后重试');
  } finally {
    publishedDateSaving.value = false;
  }
}

async function selectSyllabus(id: string) {
  const syllabus = syllabuses.value.find(item => item.id === id);
  if (!syllabus) return;
  selectedSyllabusId.value = id;
  directoryKeyword.value = '';
  treeLoading.value = true;
  try {
    const response = await getKnowledgeTree(id);
    knowledgeTree.value = response.data;
    resetLeafFilters();
    await nextTick();
    const firstSubject = navigationTree.value[0];
    selectedNavigationKey.value = firstSubject?.key ?? '';
    if (firstSubject) directoryTreeRef.value?.setCurrentKey(firstSubject.key);
  } catch {
    knowledgeTree.value = null;
    ElMessage.error('知识点列表加载失败，请稍后重试');
  } finally {
    treeLoading.value = false;
  }
}

function handleSyllabusSelectionChange(rows: SyllabusListVO[]) {
  selectedSyllabuses.value = rows;
}

async function refreshSelectedSyllabus() {
  if (selectedSyllabusId.value) await selectSyllabus(selectedSyllabusId.value);
}

async function refreshPageAfterActivation() {
  const previousSyllabusId = selectedSyllabusId.value;
  const previousNavigationKey = selectedNavigationKey.value;
  await loadSyllabuses();
  if (!previousSyllabusId || !syllabuses.value.some(item => item.id === previousSyllabusId)) return;
  await selectSyllabus(previousSyllabusId);
  if (navigationByKey.value.has(previousNavigationKey)) {
    selectedNavigationKey.value = previousNavigationKey;
  }
}

function handleDirectoryClick(data: NavigationNode) {
  selectedNavigationKey.value = data.key;
  pagination.pageNum = 1;
}
function applyLeafFilters() {
  pagination.pageNum = 1;
}

function resetLeafFilters() {
  Object.assign(leafFilters, { number: '', title: '' });
  pagination.pageNum = 1;
}

async function handleSearch() {
  clearSyllabusSelection();
  syllabusPagination.pageNum = 1;
  hasSearched.value = true;
  await loadSyllabuses();
}

function handleReset() {
  Object.assign(filters, { keyword: '', certificationId: '', status: '' });
  syllabusPagination.pageNum = 1;
  syllabusPagination.pageSize = 10;
  hasSearched.value = false;
  syllabusTotal.value = 0;
  syllabuses.value = [];
  clearSyllabusSelection();
}

function clearSyllabusSelection() {
  selectedSyllabuses.value = [];
  selectedSyllabusId.value = '';
  knowledgeTree.value = null;
  selectedNavigationKey.value = '';
  directoryKeyword.value = '';
  directoryTreeRef.value?.setCurrentKey();
  resetLeafFilters();
}

async function handleDeleteSelectedSyllabuses() {
  await handleDeleteSyllabuses(selectedSyllabuses.value);
}

async function handleDeleteSyllabus(syllabus: SyllabusListVO) {
  await handleDeleteSyllabuses([syllabus]);
}

async function handleDeleteSyllabuses(targets: SyllabusListVO[]) {
  if (!targets.length) return;
  const targetName = targets.length === 1 ? `“${targets[0]!.displayName}”` : `选中的 ${targets.length} 个考纲`;
  try {
    await ElMessageBox.confirm(`确定删除${targetName}吗？考纲及其知识点、教材、导入记录等关联内容将一并删除，资格本身保留。`, '删除考纲', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    });
  } catch {
    return;
  }
  try {
    await Promise.all(targets.map(syllabus => deleteSyllabusVersion(syllabus.id)));
    ElMessage.success(targets.length === 1 ? '考纲已删除' : `已删除 ${targets.length} 个考纲`);
    if (targets.some(syllabus => syllabus.id === selectedSyllabusId.value)) clearSyllabusSelection();
    else selectedSyllabuses.value = [];
    await loadSyllabuses();
  } catch {
    ElMessage.error('删除考纲失败，请稍后重试');
  }
}

function formatDateTime(value?: string | null) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false });
}

onMounted(() => {
  void loadSyllabuses();
  void loadCertificationOptions().catch(() => ElMessage.error('考试资格选项加载失败，请稍后重试'));
});
onDeactivated(() => {
  refreshOnActivation = true;
});
onActivated(() => {
  if (!refreshOnActivation) return;
  refreshOnActivation = false;
  void refreshPageAfterActivation();
});
</script>

<style scoped lang="scss">
@use '@/assets/styles/components/page-shell' as pageShell;

@include pageShell.tree-table-crud-page;

.knowledge-page,
.tree-content-col {
  width: 100%;
  min-width: 0;
}

.table-footer {
  color: var(--app-text-muted);
  font-size: 13px;
}

.query-form :deep(.el-input),
.query-form :deep(.el-select) {
  width: 180px;
}

.content-grid {
  --knowledge-content-height: 720px;

  align-items: stretch;
  height: var(--knowledge-content-height);
}

.tree-panel-col,
.tree-content-col {
  height: 100%;
}

.tree-panel-shell,
.leaf-table-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.tree-panel-shell :deep(.el-card__body) {
  height: auto;
  max-height: none;
  flex: 1 1 auto;
}

.tree-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.tree-panel-header.is-collapsed {
  justify-content: center;
}

.tree-panel-header::after {
  content: '';
  border-right: 2px solid currentColor;
  border-bottom: 2px solid currentColor;
  color: var(--app-text-muted);
}

.tree-content-col {
  display: flex;
  flex-direction: column;
}

.leaf-table-panel {
  min-height: 0;
  flex: 1 1 auto;
}

.leaf-table-panel :deep(.el-card__body) {
  display: flex;
  min-height: 0;
  flex: 1 1 auto;
  flex-direction: column;
  overflow: hidden;
}

.directory-tree-scroll {
  flex: 1 1 auto;
  min-height: 180px;
  margin-top: 12px;
  overflow: auto;
}

.directory-tree {
  display: inline-block;
  width: max-content;
  min-width: 100%;
  background: transparent;
}

.directory-tree :deep(.el-tree-node) {
  width: max-content;
  min-width: 100%;
}

.directory-tree :deep(.el-tree-node__children) {
  min-width: max-content;
}

.directory-tree :deep(.el-tree-node__content) {
  height: 32px;
  border-radius: 6px;
}

.directory-node,
.node-label {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 6px;
}

.directory-node {
  width: 100%;
}

.node-label {
  flex: 0 0 auto;
  color: var(--app-text-regular);
  font-size: 13px;
  white-space: nowrap;
}

.node-label .el-icon {
  flex: 0 0 auto;
  width: 16px;
  height: 16px;
  color: var(--app-accent-strong);
  font-size: 16px;
}

.leaf-search-wrap {
  flex: 0 0 auto;
  margin-bottom: 10px;
}

.syllabus-panel :deep(.pagination-container) {
  padding: 12px 0 0;
}

.knowledge-table {
  width: 100%;
}

.leaf-table-wrap {
  min-height: 0;
  flex: 1 1 auto;
}

.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-top: 12px;
}

.selection-empty {
  min-height: 320px;
}

@media (max-width: 900px) {
  .content-grid {
    height: auto;
  }

  .tree-panel-col,
  .tree-content-col {
    height: 620px;
  }

  .query-form :deep(.el-form-item),
  .query-form :deep(.el-input),
  .query-form :deep(.el-select) {
    width: 100%;
  }

  .leaf-table-panel {
    height: auto;
    min-height: 520px;
  }

  .table-footer {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
