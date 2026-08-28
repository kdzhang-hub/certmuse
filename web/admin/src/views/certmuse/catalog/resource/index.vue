<template>
  <div class="p-2 resource-page">
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
        <div class="resource-query-scroll">
          <el-form :model="query" :inline="true" class="query-form resource-query-form">
            <el-form-item label="教材名称">
              <el-input v-model="query.title" clearable placeholder="请输入教材名称" @keyup.enter="applyFilters" />
            </el-form-item>
            <el-form-item label="考试资格">
              <el-select v-model="query.certificationId" clearable placeholder="全部资格">
                <el-option v-for="item in certificationOptions" :key="item.id" :label="item.label" :value="item.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="query.status" clearable placeholder="全部状态">
                <el-option v-for="item in options.statuses" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="创建人">
              <el-select v-model="query.createBy" clearable placeholder="全部创建人">
                <el-option v-for="item in options.creators" :key="item.id" :label="item.label" :value="item.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="知识点关联">
              <el-select v-model="chunkQuery.hasKnowledgePoint" placeholder="全部">
                <el-option label="全部" value="" />
                <el-option label="已绑定知识点" :value="true" />
                <el-option label="未绑定知识点" :value="false" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="applyFilters">搜索</el-button>
              <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-card>
    </div>

    <el-card v-if="hasSearched" v-loading="textbookLoading" shadow="hover" class="table-panel textbook-panel">
      <template #header>
        <div class="toolbar-shell">
          <div class="table-heading">
            <span class="panel-kicker">Textbook Dataset</span>
            <h3>教材列表</h3>
          </div>
          <div class="toolbar-actions">
            <el-button
              v-hasPermi="['certmuse:catalog:import']"
              type="primary"
              plain
              :icon="Plus"
              @click="importVisible = true"
            >
              新增
            </el-button>
            <el-button
              v-hasPermi="['certmuse:catalog:resource:remove']"
              type="danger"
              plain
              :icon="Delete"
              :disabled="!canDeleteSelectedTextbooks"
              @click="removeSelectedTextbooks"
            >
              删除
            </el-button>
          </div>
        </div>
      </template>
      <el-table
        :data="textbooks"
        border
        class="data-table"
        highlight-current-row
        @selection-change="selectedTextbookRows = $event"
      >
        <el-table-column type="selection" width="48" align="center" />
        <el-table-column label="教材名称" prop="title" min-width="220" show-overflow-tooltip />
        <el-table-column label="资格名称" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ textbookCertificationName(row as TextbookListItemVO) }}</template>
        </el-table-column>
        <el-table-column label="内容块" prop="chunkCount" width="90" align="center" />
        <el-table-column label="知识点关联" min-width="160" align="center">
          <template #default="{ row }">
            <span class="mapped">{{ row.mappedChunkCount }}</span>
            已关联 /
            <span class="unmapped">{{ row.unmappedChunkCount }}</span>
            未关联
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建人" prop="createByName" min-width="110" show-overflow-tooltip />
        <el-table-column label="操作" width="154" fixed="right" align="center">
          <template #default="{ row }">
            <el-tooltip content="编辑教材" placement="top">
              <el-button
                v-hasPermi="['certmuse:catalog:resource:edit']"
                link
                type="primary"
                :icon="Edit"
                aria-label="编辑教材"
                class="textbook-edit"
                @click.stop="openTextbookEdit(row as TextbookListItemVO)"
              />
            </el-tooltip>
            <el-tooltip v-if="row.status === 'draft'" content="发布教材" placement="top">
              <el-button
                v-hasPermi="['certmuse:catalog:resource:edit']"
                link
                type="success"
                :icon="Promotion"
                aria-label="发布教材"
                class="textbook-publish"
                :loading="textbookStatusChanging === row.id"
                :disabled="Boolean(textbookStatusChanging)"
                @click.stop="publishTextbook(row as TextbookListItemVO)"
              />
            </el-tooltip>
            <el-tooltip v-else-if="row.status === 'published'" content="下架教材" placement="top">
              <el-button
                v-hasPermi="['certmuse:catalog:resource:edit']"
                link
                type="warning"
                :icon="Bottom"
                aria-label="下架教材"
                class="textbook-offline"
                :loading="textbookStatusChanging === row.id"
                :disabled="Boolean(textbookStatusChanging)"
                @click.stop="takeTextbookOffline(row as TextbookListItemVO)"
              />
            </el-tooltip>
            <el-tooltip content="查看教材内容" placement="top">
              <el-button link type="primary" :icon="Search" aria-label="查看教材内容" @click="selectTextbook(row.id)" />
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="textbookTotal > 0"
        v-model:page="query.pageNum"
        v-model:limit="query.pageSize"
        :total="textbookTotal"
        @pagination="loadTextbooks"
      />
    </el-card>

    <el-card v-if="selectedTextbook" v-loading="pdfLoading" shadow="hover" class="original-pdf-card">
      <template #header>
        <div class="toolbar-shell">
          <div class="table-heading">
            <span class="panel-kicker">Original PDF</span>
            <h3>原始 PDF</h3>
          </div>
          <div class="toolbar-actions">
            <el-upload
              v-if="selectedTextbook.status === 'draft'"
              accept="application/pdf,.pdf"
              :auto-upload="false"
              :show-file-list="false"
              :disabled="pdfUploading"
              @change="handlePdfUpload"
            >
              <el-button v-hasPermi="['certmuse:catalog:resource:edit']" type="primary" :loading="pdfUploading">
                {{ pdfInfo?.available ? '替换原始 PDF' : '上传原始 PDF' }}
              </el-button>
            </el-upload>
            <el-button type="primary" plain :disabled="!pdfInfo?.available" @click="openPdfReader()">
              在线阅读
            </el-button>
            <el-button
              v-if="selectedTextbook.status === 'draft'"
              v-hasPermi="['certmuse:catalog:resource:edit']"
              type="danger"
              plain
              :disabled="!pdfInfo?.available"
              :loading="pdfDeleting"
              @click="removeOriginalPdf"
            >
              删除原始 PDF
            </el-button>
          </div>
        </div>
      </template>
      <el-descriptions class="original-pdf-meta" :column="3" border>
        <el-descriptions-item label="附件状态">
          <el-tag :type="pdfInfo?.available ? 'success' : 'info'">
            {{ pdfInfo?.available ? '已上传' : '未上传' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="文件信息">
          {{ pdfInfo?.available ? `${pdfInfo.fileName}（${formatFileSize(pdfInfo.fileSize)}）` : '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="上传时间">{{ pdfInfo?.uploadedTime || '—' }}</el-descriptions-item>
      </el-descriptions>
      <p v-if="selectedTextbook.status !== 'draft'" class="pdf-card-hint">
        已发布教材需先使用现有“下架”操作，才可上传或替换原始 PDF。
      </p>
    </el-card>

    <el-row v-if="selectedTextbook" :gutter="20" class="content-grid">
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
                <h3>考纲目录</h3>
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
                :data="navigation"
                :props="treeProps"
                :filter-node-method="filterNode"
                default-expand-all
                highlight-current
                @node-click="handleDirectoryClick"
              >
                <template #default="{ data }">
                  <span class="directory-node">
                    <span class="node-label">
                      <Collection v-if="data.nodeType === 'subject'" />
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
        <el-card shadow="hover" class="table-panel chunk-card">
          <template #header>
            <div class="toolbar-shell">
              <div class="table-heading">
                <span class="panel-kicker">Document Chunks</span>
                <h3>内容块</h3>
              </div>
              <div class="toolbar-actions">
                <div class="chunk-filter">
                  <el-input
                    v-model="chunkQuery.keyword"
                    clearable
                    placeholder="搜索标题或正文"
                    :prefix-icon="Search"
                    @keyup.enter="loadChunks"
                  />
                </div>
              </div>
            </div>
          </template>
          <div class="chunk-table-wrap">
            <el-table
              v-loading="chunkLoading"
              :data="chunkRows"
              border
              height="100%"
              data-full-height-table="false"
              class="data-table"
            >
              <el-table-column label="顺序" prop="chunkOrder" width="70" align="center" />
              <el-table-column label="标题 / 目录" min-width="210">
                <template #default="{ row }">
                  <b>{{ row.heading || '未命名内容块' }}</b>
                  <small>{{ row.headingPath.join(' / ') || '教材根目录' }}</small>
                </template>
              </el-table-column>
              <el-table-column label="正文摘要" min-width="300">
                <template #default="{ row }">
                  <span class="chunk-content-preview">{{ row.contentPreview }}</span>
                </template>
              </el-table-column>
              <el-table-column label="页码" width="90" align="center">
                <template #default="{ row }">{{ pageRange(row as TextbookChunkListItemVO) }}</template>
              </el-table-column>
              <el-table-column label="关联知识点" min-width="180">
                <template #default="{ row }">
                  <span v-if="row.knowledgePoints.length">
                    {{ row.knowledgePoints.map(item => item.code).join('、') }}
                  </span>
                  <span v-else class="unmapped">未关联</span>
                </template>
              </el-table-column>
              <el-table-column label="来源定位" min-width="145">
                <template #default="{ row }">
                  <span>行 {{ row.sourceLocator.lineStart }}–{{ row.sourceLocator.lineEnd }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="160" fixed="right" align="center">
                <template #default="{ row }">
                  <el-tooltip content="定位原文" placement="top">
                    <el-button
                      link
                      type="primary"
                      :icon="Document"
                      :disabled="!pdfInfo?.available || !(row as TextbookChunkListItemVO).pageStart"
                      @click="openPdfReader((row as TextbookChunkListItemVO).pageStart ?? 1)"
                    />
                  </el-tooltip>
                  <el-tooltip content="查看内容块" placement="top">
                    <el-button
                      link
                      type="primary"
                      :icon="View"
                      @click="openChunkView(row as TextbookChunkListItemVO)"
                    />
                  </el-tooltip>
                  <el-tooltip content="修改内容块" placement="top">
                    <el-button
                      link
                      type="primary"
                      :icon="Edit"
                      @click="openChunkEdit(row as TextbookChunkListItemVO)"
                    />
                  </el-tooltip>
                  <el-tooltip content="删除内容块" placement="top">
                    <el-button
                      link
                      type="danger"
                      :icon="Delete"
                      @click="removeChunks([row as TextbookChunkListItemVO])"
                    />
                  </el-tooltip>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <pagination
            v-show="chunkTotal > 0"
            v-model:page="chunkQuery.pageNum"
            v-model:limit="chunkQuery.pageSize"
            :total="chunkTotal"
            @pagination="loadChunks()"
          />
        </el-card>
      </el-col>
    </el-row>

    <ImportDialog
      v-model="importVisible"
      import-type="textbook"
      :initial-syllabus-version-id="selectedTextbook?.syllabusVersionId"
      :initial-textbook-id="selectedTextbook?.id"
      @closed="loadTextbooks"
    />

    <el-drawer v-model="detailVisible" title="教材详情" size="520px">
      <template v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="教材名称">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item label="大纲版本">{{ detail.syllabusVersionName }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(detail.status)">{{ statusLabel(detail.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="内容统计">
            {{ detail.statistics.chunkCount }} 块，已关联 {{ detail.statistics.mappedChunkCount }} 块，未关联
            {{ detail.statistics.unmappedChunkCount }} 块
          </el-descriptions-item>
          <el-descriptions-item label="最近导入">
            {{ detail.latestImportBatch?.sourceFileName || '—' }}
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>

    <el-dialog v-model="textbookEditVisible" title="编辑教材" width="520px" append-to-body>
      <el-form ref="textbookEditFormRef" :model="textbookEditForm" :rules="textbookEditRules" label-width="88px">
        <el-form-item label="教材名称" prop="title">
          <el-input v-model="textbookEditForm.title" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="绑定资格" prop="certificationId">
          <el-select
            v-model="textbookEditForm.certificationId"
            filterable
            placeholder="请选择资格"
            :disabled="textbookHasKnowledgeMappings"
          >
            <el-option
              v-for="item in textbookCertificationOptions"
              :key="item.id"
              :label="item.label"
              :value="item.id"
            />
          </el-select>
          <div v-if="textbookHasKnowledgeMappings" class="form-help-text">教材已绑定知识点，绑定资格不可修改。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="textbookEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="textbookSaving" @click="submitTextbookEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="chunkViewVisible" title="查看内容块" width="760px" append-to-body>
      <el-descriptions v-if="viewingChunk" :column="1" border>
        <el-descriptions-item label="顺序">{{ viewingChunk.chunkOrder }}</el-descriptions-item>
        <el-descriptions-item label="标题">{{ viewingChunk.heading || '未命名内容块' }}</el-descriptions-item>
        <el-descriptions-item label="目录">
          {{ viewingChunk.headingPath.join(' / ') || '教材根目录' }}
        </el-descriptions-item>
        <el-descriptions-item label="关联知识点">
          <template v-if="viewingChunk.knowledgePoints.length">
            {{ viewingChunk.knowledgePoints.map(item => `${item.code} ${item.title}`).join('、') }}
          </template>
          <span v-else class="unmapped">未关联</span>
        </el-descriptions-item>
        <el-descriptions-item label="来源定位">
          {{ sourceLocatorText(viewingChunk) }}
        </el-descriptions-item>
        <el-descriptions-item label="正文">
          <div class="chunk-detail-content">{{ viewingChunk.content }}</div>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog v-model="editVisible" title="修改内容块" width="680px" append-to-body>
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="78px">
        <el-form-item label="当前目录">
          <el-input :model-value="editForm.headingPath.join(' / ') || '教材根目录'" disabled />
        </el-form-item>
        <el-form-item label="标题"><el-input v-model="editForm.heading" maxlength="500" /></el-form-item>
        <el-form-item label="正文" prop="content">
          <el-input v-model="editForm.content" type="textarea" :rows="8" maxlength="10000" show-word-limit />
        </el-form-item>
        <el-form-item label="关联知识点">
          <el-tree-select
            class="edit-knowledge-select"
            v-model="editForm.knowledgeNodeKeys"
            :data="navigation"
            :props="knowledgeSelectProps"
            node-key="key"
            multiple
            show-checkbox
            check-strictly
            filterable
            clearable
            collapse-tags
            collapse-tags-tooltip
            placeholder="选择知识点（可不选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <PdfReaderDialog
      v-model="pdfDialogVisible"
      :document-title="pdfReader?.title || selectedTextbook?.title"
      :reader="pdfReader"
      :initial-page="pdfInitialPage"
      @refresh="refreshPdfReader"
    />
  </div>
</template>

<script setup name="CertmuseResource" lang="ts">
import {
  Bottom,
  Collection,
  Delete,
  Document,
  Edit,
  FolderOpened,
  Plus,
  Promotion,
  Refresh,
  Search,
  View
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox, type ElForm, type ElTree, type UploadFile } from 'element-plus';
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import type { KnowledgeTreeNodeVO, KnowledgeTreeVO } from '@/api/certmuse/catalog/knowledge/types';
import type {
  TextbookChunkDetailVO,
  TextbookChunkListItemVO,
  TextbookDetailVO,
  TextbookListItemVO,
  TextbookOptionsVO,
  TextbookStatus,
  TextbookUpdateForm
} from '@/api/certmuse/catalog/resource/types';
import type { TextbookOriginalPdfInfoVO, TextbookPdfReaderVO } from '@/api/certmuse/catalog/textbook-pdf/types';
import { getKnowledgeTree } from '@/api/certmuse/catalog/knowledge';
import {
  deleteTextbook,
  deleteTextbookChunks,
  getTextbook,
  getTextbookChunk,
  getTextbookOptions,
  listTextbookChunks,
  listTextbooks,
  publishTextbook as publishTextbookRequest,
  takeTextbookOffline as takeTextbookOfflineRequest,
  updateTextbook,
  updateTextbookChunk
} from '@/api/certmuse/catalog/resource';
import {
  deleteTextbookOriginalPdf,
  getTextbookOriginalPdf,
  getTextbookPdfReader,
  uploadTextbookOriginalPdf
} from '@/api/certmuse/catalog/textbook-pdf';
import ImportDialog from '@/views/certmuse/catalog/import/ImportDialog.vue';
import PdfReaderDialog from './PdfReaderDialog.vue';

interface KnowledgeNavigationNode {
  key: string;
  nodeType: 'subject' | 'knowledge';
  label: string;
  knowledgePointId: string | null;
  examSubjectId: string;
  path: string;
  disabled?: boolean;
  children: KnowledgeNavigationNode[];
}

const treeProps = { children: 'children', label: 'label' };
const knowledgeSelectProps = { children: 'children', label: 'label', disabled: 'disabled' };
const directoryTreeRef = ref<InstanceType<typeof ElTree>>();
const editFormRef = ref<InstanceType<typeof ElForm>>();
const textbookEditFormRef = ref<InstanceType<typeof ElForm>>();
const navigation = ref<KnowledgeNavigationNode[]>([]);
const textbooks = ref<TextbookListItemVO[]>([]);
const selectedTextbookRows = ref<TextbookListItemVO[]>([]);
const selectedChunkRows = ref<TextbookChunkListItemVO[]>([]);
const selectedTextbook = ref<TextbookListItemVO>();
const detail = ref<TextbookDetailVO>();
const selectedNode = ref<KnowledgeNavigationNode>();
const chunkRows = ref<TextbookChunkListItemVO[]>([]);
const options = ref<TextbookOptionsVO>({ certifications: [], statuses: [], creators: [] });
const certificationOptions = ref<Array<{ id: string; label: string }>>([]);
const knowledgeTreeCache = new Map<string, KnowledgeTreeVO>();
const textbookTotal = ref(0);
const chunkTotal = ref(0);
const textbookLoading = ref(false);
const importVisible = ref(false);
const chunkLoading = ref(false);
const hasSearched = ref(false);
const searchCollapsed = ref(false);
const directoryCollapsed = ref(false);
const directoryKeyword = ref('');
const detailVisible = ref(false);
const chunkViewVisible = ref(false);
const viewingChunk = ref<TextbookChunkDetailVO>();
const editVisible = ref(false);
const saving = ref(false);
const textbookEditVisible = ref(false);
const textbookSaving = ref(false);
const textbookStatusChanging = ref<string>();
const pdfInfo = ref<TextbookOriginalPdfInfoVO>();
const pdfReader = ref<TextbookPdfReaderVO>();
const pdfLoading = ref(false);
const pdfUploading = ref(false);
const pdfDeleting = ref(false);
const pdfDialogVisible = ref(false);
const pdfInitialPage = ref(1);
const editingTextbook = ref<TextbookListItemVO>();
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  title: '',
  certificationId: '',
  status: undefined as TextbookStatus | undefined,
  createBy: ''
});
const chunkQuery = reactive({ pageNum: 1, pageSize: 10, keyword: '', hasKnowledgePoint: '' as '' | boolean });
const editForm = reactive({
  id: '',
  heading: '' as string | null,
  headingPath: [] as string[],
  content: '',
  updateTime: '',
  knowledgeNodeKeys: [] as string[]
});
const editRules = { content: [{ required: true, message: '请输入内容块正文', trigger: 'blur' }] };
const textbookEditForm = reactive<TextbookUpdateForm>({ title: '', certificationId: '' });
const textbookEditRules = {
  title: [{ required: true, message: '请输入教材名称', trigger: 'blur' }],
  certificationId: [{ required: true, message: '请选择绑定资格', trigger: 'change' }]
};
const textbookCertificationOptions = computed(() => {
  const textbook = editingTextbook.value;
  if (!textbook || certificationOptions.value.some(item => item.id === textbook.certificationId)) {
    return certificationOptions.value;
  }
  return [{ id: textbook.certificationId, label: textbookCertificationName(textbook) }, ...certificationOptions.value];
});
const textbookHasKnowledgeMappings = computed(() => (editingTextbook.value?.knowledgePointCount ?? 0) > 0);
const canDeleteSelectedTextbooks = computed(
  () => selectedTextbookRows.value.length > 0 && selectedTextbookRows.value.every(item => item.deletable)
);

watch(directoryKeyword, value => directoryTreeRef.value?.filter(value));
function filterNode(value: string, data: KnowledgeNavigationNode) {
  return !value || `${data.label} ${data.path}`.toLowerCase().includes(value.toLowerCase());
}

function textbookCertificationName(textbook: TextbookListItemVO) {
  return textbook.certificationName || textbook.syllabusVersionName?.split('·')[0]?.trim() || '—';
}

async function init() {
  await loadOptions();
}
async function loadOptions() {
  try {
    options.value = await getTextbookOptions();
    certificationOptions.value = options.value.certifications;
  } catch (error) {
    textbooks.value = [];
    textbookTotal.value = 0;
    showError(error);
  }
}
async function loadTextbooks() {
  textbookLoading.value = true;
  try {
    const result = await listTextbooks(query);
    textbooks.value = result.rows;
    textbookTotal.value = result.total;
  } catch (error) {
    showError(error);
  } finally {
    textbookLoading.value = false;
  }
}
async function openTextbookEdit(row: TextbookListItemVO) {
  editingTextbook.value = row;
  Object.assign(textbookEditForm, { title: row.title, certificationId: row.certificationId });
  textbookEditVisible.value = true;
  await loadOptions();
}
async function submitTextbookEdit() {
  const valid = await textbookEditFormRef.value?.validate().catch(() => false);
  const textbook = editingTextbook.value;
  if (!valid || !textbook) return;
  textbookSaving.value = true;
  try {
    const certificationChanged = textbook.certificationId !== textbookEditForm.certificationId;
    await updateTextbook(textbook.id, { ...textbookEditForm });
    textbookEditVisible.value = false;
    editingTextbook.value = undefined;
    ElMessage.success('教材已更新');
    if (selectedTextbook.value?.id === textbook.id) {
      if (certificationChanged) clearContentSelection();
      else await loadDetail(textbook.id);
    }
    await loadTextbooks();
  } catch (error) {
    showError(error);
  } finally {
    textbookSaving.value = false;
  }
}
async function publishTextbook(textbook: TextbookListItemVO) {
  try {
    await ElMessageBox.confirm(`发布后“${textbook.title}”将立即对游客和学生端公开阅读，确定继续吗？`, '确认发布教材', {
      type: 'warning',
      confirmButtonText: '确认发布',
      cancelButtonText: '取消'
    });
  } catch {
    return;
  }
  await changeTextbookStatus(textbook, publishTextbookRequest, '教材已发布，游客和学生端现在可阅读');
}
async function takeTextbookOffline(textbook: TextbookListItemVO) {
  try {
    await ElMessageBox.confirm(
      `下架后“${textbook.title}”将转为草稿，游客和学生端将无法查看，确定继续吗？`,
      '确认下架教材',
      { type: 'warning', confirmButtonText: '确认下架', cancelButtonText: '取消' }
    );
  } catch {
    return;
  }
  await changeTextbookStatus(textbook, takeTextbookOfflineRequest, '教材已下架，游客和学生端现在不可见');
}
async function changeTextbookStatus(
  textbook: TextbookListItemVO,
  request: (textbookId: string) => Promise<void>,
  successMessage: string
) {
  textbookStatusChanging.value = textbook.id;
  try {
    await request(textbook.id);
    ElMessage.success(successMessage);
    if (selectedTextbook.value?.id === textbook.id) clearContentSelection();
    await loadTextbooks();
  } catch (error) {
    showError(error);
  } finally {
    textbookStatusChanging.value = undefined;
  }
}
async function selectTextbook(id: string) {
  selectedTextbook.value = textbooks.value.find(item => item.id === id);
  if (!selectedTextbook.value) return;
  selectedChunkRows.value = [];
  chunkQuery.pageNum = 1;
  await Promise.all([loadDetail(id), loadPdfInfo(id)]);
  await loadKnowledgeNavigation(selectedTextbook.value.syllabusVersionId);
  selectedNode.value = navigation.value[0];
  await nextTick();
  if (selectedNode.value) directoryTreeRef.value?.setCurrentKey(selectedNode.value.key);
  await loadChunks();
}
async function loadPdfInfo(textbookId: string) {
  pdfLoading.value = true;
  try {
    pdfInfo.value = await getTextbookOriginalPdf(textbookId);
  } catch (error) {
    pdfInfo.value = undefined;
    showError(error);
  } finally {
    pdfLoading.value = false;
  }
}
async function handlePdfUpload(uploadFile: UploadFile) {
  const textbook = selectedTextbook.value;
  const file = uploadFile.raw;
  if (!textbook || !file) return;
  if (textbook.status !== 'draft') {
    ElMessage.warning('请先下架教材，再上传或替换原始 PDF');
    return;
  }
  if (!file.name.toLowerCase().endsWith('.pdf')) {
    ElMessage.warning('仅支持 PDF 文件');
    return;
  }
  pdfUploading.value = true;
  try {
    pdfInfo.value = await uploadTextbookOriginalPdf(textbook.id, file);
    ElMessage.success('原始 PDF 已保存');
  } catch (error) {
    showError(error);
  } finally {
    pdfUploading.value = false;
  }
}
async function removeOriginalPdf() {
  const textbook = selectedTextbook.value;
  if (!textbook || !pdfInfo.value?.available) return;
  if (textbook.status !== 'draft') {
    ElMessage.warning('请先下架教材，再删除原始 PDF');
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确定删除“${textbook.title}”的原始 PDF 吗？教材 JSON、内容块和知识点绑定不会受影响。`,
      '删除原始 PDF',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    );
  } catch {
    return;
  }
  pdfDeleting.value = true;
  try {
    await deleteTextbookOriginalPdf(textbook.id);
    pdfInfo.value = { available: false, fileName: null, fileSize: null, fileHash: null, uploadedTime: null };
    pdfReader.value = undefined;
    pdfDialogVisible.value = false;
    ElMessage.success('原始 PDF 已删除');
  } catch (error) {
    showError(error);
  } finally {
    pdfDeleting.value = false;
  }
}
async function openPdfReader(page = 1) {
  if (!selectedTextbook.value || !pdfInfo.value?.available) return;
  pdfInitialPage.value = page;
  try {
    await refreshPdfReader();
    pdfDialogVisible.value = true;
  } catch (error) {
    showError(error);
  }
}
async function refreshPdfReader() {
  if (!selectedTextbook.value) return;
  pdfReader.value = await getTextbookPdfReader(selectedTextbook.value.id);
}
async function loadDetail(id: string) {
  try {
    detail.value = await getTextbook(id);
  } catch (error) {
    showError(error);
  }
}
async function loadKnowledgeNavigation(syllabusVersionId: string) {
  try {
    let tree = knowledgeTreeCache.get(syllabusVersionId);
    if (!tree) {
      const response = await getKnowledgeTree(syllabusVersionId);
      tree = response.data;
      knowledgeTreeCache.set(syllabusVersionId, tree);
    }
    navigation.value = buildKnowledgeNavigation(tree);
  } catch (error) {
    navigation.value = [];
    showError(error);
  }
}
async function loadChunks() {
  const id = selectedTextbook.value?.id;
  if (!id || !selectedNode.value) {
    chunkRows.value = [];
    chunkTotal.value = 0;
    return;
  }
  chunkLoading.value = true;
  try {
    const result = await listTextbookChunks(id, {
      ...chunkQuery,
      hasKnowledgePoint: chunkQuery.hasKnowledgePoint === '' ? undefined : chunkQuery.hasKnowledgePoint,
      knowledgePointId: selectedNode.value?.knowledgePointId ?? undefined,
      examSubjectId: selectedNode.value
        ? selectedNode.value.knowledgePointId
          ? undefined
          : selectedNode.value.examSubjectId
        : undefined,
      includeDescendants: selectedNode.value ? true : undefined
    });
    chunkRows.value = result.rows;
    chunkTotal.value = result.total;
  } catch (error) {
    showError(error);
  } finally {
    chunkLoading.value = false;
  }
}
async function handleDirectoryClick(node: KnowledgeNavigationNode) {
  selectedNode.value = node;
  chunkQuery.pageNum = 1;
  await loadChunks();
}
async function applyFilters() {
  query.pageNum = 1;
  chunkQuery.pageNum = 1;
  clearContentSelection();
  hasSearched.value = true;
  await loadTextbooks();
}
function buildKnowledgeNavigation(tree: KnowledgeTreeVO): KnowledgeNavigationNode[] {
  const childrenByParent = new Map<string, KnowledgeTreeNodeVO[]>();
  tree.nodes.forEach(node => {
    const key = node.parentId ?? `subject:${node.examSubjectId}`;
    const children = childrenByParent.get(key) ?? [];
    children.push(node);
    childrenByParent.set(key, children);
  });
  const compare = (left: KnowledgeTreeNodeVO, right: KnowledgeTreeNodeVO) =>
    left.syllabusNumber.localeCompare(right.syllabusNumber, 'zh-CN', { numeric: true }) ||
    left.sortOrder - right.sortOrder;
  childrenByParent.forEach(children => children.sort(compare));
  const hasChildren = (nodeId: string) => (childrenByParent.get(nodeId)?.length ?? 0) > 0;
  const buildNode = (node: KnowledgeTreeNodeVO, parentPath: string): KnowledgeNavigationNode => {
    const label = `${node.syllabusNumber} ${node.syllabusTitle}`;
    const path = `${parentPath} / ${label}`;
    return {
      key: `knowledge:${node.id}`,
      nodeType: 'knowledge',
      label,
      knowledgePointId: node.id,
      examSubjectId: node.examSubjectId,
      path,
      disabled: false,
      children: (childrenByParent.get(node.id) ?? [])
        .filter(child => hasChildren(child.id))
        .map(child => buildNode(child, path))
    };
  };
  return tree.subjects.map(subject => ({
    key: `subject:${subject.id}`,
    nodeType: 'subject',
    label: subject.subjectName,
    knowledgePointId: null,
    examSubjectId: subject.id,
    path: subject.subjectName,
    disabled: true,
    children: (childrenByParent.get(`subject:${subject.id}`) ?? [])
      .filter(node => hasChildren(node.id))
      .map(node => buildNode(node, subject.subjectName))
  }));
}
function resetQuery() {
  Object.assign(query, {
    pageNum: 1,
    pageSize: 10,
    title: '',
    certificationId: '',
    status: undefined,
    createBy: ''
  });
  Object.assign(chunkQuery, { pageNum: 1, pageSize: 10, keyword: '', hasKnowledgePoint: '' });
  textbooks.value = [];
  textbookTotal.value = 0;
  hasSearched.value = false;
  clearContentSelection();
}
function clearContentSelection() {
  selectedTextbook.value = undefined;
  selectedNode.value = undefined;
  selectedChunkRows.value = [];
  detail.value = undefined;
  navigation.value = [];
  directoryKeyword.value = '';
  chunkRows.value = [];
  chunkTotal.value = 0;
  detailVisible.value = false;
  chunkViewVisible.value = false;
  viewingChunk.value = undefined;
  pdfInfo.value = undefined;
  pdfReader.value = undefined;
  pdfDialogVisible.value = false;
  pdfInitialPage.value = 1;
}
async function openChunkView(row: TextbookChunkListItemVO) {
  if (!selectedTextbook.value) return;
  try {
    viewingChunk.value = await getTextbookChunk(selectedTextbook.value.id, row.id);
    chunkViewVisible.value = true;
  } catch (error) {
    showError(error);
  }
}
async function openChunkEdit(row?: TextbookChunkListItemVO) {
  if (!row || !selectedTextbook.value) return;
  try {
    const chunk: TextbookChunkDetailVO = await getTextbookChunk(selectedTextbook.value.id, row.id);
    if (!chunk.editable) {
      ElMessage.warning(chunk.operationDisabledReason || '当前内容块不可修改');
      return;
    }
    Object.assign(editForm, {
      id: chunk.id,
      heading: chunk.heading,
      headingPath: chunk.headingPath,
      content: chunk.content,
      updateTime: chunk.updateTime,
      knowledgeNodeKeys: chunk.knowledgePoints.map(point => `knowledge:${point.id}`)
    });
    editVisible.value = true;
  } catch (error) {
    showError(error);
  }
}
async function submitEdit() {
  const valid = await editFormRef.value?.validate().catch(() => false);
  if (!valid || !selectedTextbook.value) return;
  saving.value = true;
  try {
    await updateTextbookChunk(selectedTextbook.value.id, editForm.id, {
      heading: editForm.heading,
      content: editForm.content,
      updateTime: editForm.updateTime,
      knowledgePointIds: editForm.knowledgeNodeKeys
        .filter(key => key.startsWith('knowledge:'))
        .map(key => key.slice('knowledge:'.length))
    });
    editVisible.value = false;
    ElMessage.success('内容块已修改');
    await Promise.all([loadChunks(), loadDetail(selectedTextbook.value.id), loadTextbooks()]);
  } catch (error) {
    showError(error);
  } finally {
    saving.value = false;
  }
}
async function removeChunks(rows: TextbookChunkListItemVO[]) {
  if (!rows.length || !selectedTextbook.value) return;
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${rows.length} 个内容块吗？删除后不可恢复。`, '删除内容块', {
      type: 'warning'
    });
    await deleteTextbookChunks(
      selectedTextbook.value.id,
      rows.map(row => row.id)
    );
    ElMessage.success('内容块已删除');
    selectedChunkRows.value = [];
    await Promise.all([loadChunks(), loadDetail(selectedTextbook.value.id), loadTextbooks()]);
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') showError(error);
  }
}
async function removeSelectedTextbooks() {
  const rows = selectedTextbookRows.value;
  if (!rows.length || !rows.every(item => item.deletable)) return;
  try {
    const names = rows.map(item => `“${item.title}”`).join('、');
    await ElMessageBox.confirm(`确定删除教材 ${names} 吗？教材下的内容块将不再显示。`, '删除教材', {
      type: 'warning',
      confirmButtonText: '确认删除'
    });
    await Promise.all(rows.map(item => deleteTextbook(item.id)));
    ElMessage.success(`已删除 ${rows.length} 本教材`);
    const removedIds = new Set(rows.map(item => item.id));
    const removedCurrent = selectedTextbook.value ? removedIds.has(selectedTextbook.value.id) : false;
    selectedTextbookRows.value = [];
    await loadTextbooks();
    if (removedCurrent) {
      selectedTextbook.value = undefined;
      selectedNode.value = undefined;
      detail.value = undefined;
      navigation.value = [];
      chunkRows.value = [];
      chunkTotal.value = 0;
    }
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') showError(error);
  }
}
function statusLabel(status: TextbookStatus) {
  return options.value.statuses.find(item => item.value === status)?.label ?? status;
}
function statusType(status: TextbookStatus) {
  return (
    {
      draft: 'info',
      pending_review: 'warning',
      approved: 'success',
      rejected: 'danger',
      published: 'success',
      offline: 'info'
    } as const
  )[status];
}
function pageRange(row: TextbookChunkListItemVO) {
  return row.pageStart
    ? row.pageStart === row.pageEnd
      ? `第 ${row.pageStart} 页`
      : `${row.pageStart}–${row.pageEnd} 页`
    : '—';
}
function sourceLocatorText(chunk: TextbookChunkDetailVO) {
  const locator = chunk.sourceLocator;
  const lineRange =
    locator.lineStart == null
      ? ''
      : locator.lineStart === locator.lineEnd
        ? `行 ${locator.lineStart}`
        : `行 ${locator.lineStart}–${locator.lineEnd ?? ''}`;
  return [locator.sourceKey, lineRange].filter(Boolean).join(' · ') || '—';
}
function formatFileSize(size: number | null) {
  if (size == null) return '—';
  if (size < 1024 * 1024) return `${Math.ceil(size / 1024)} KiB`;
  return `${(size / 1024 / 1024).toFixed(1)} MiB`;
}
function showError(error: unknown) {
  ElMessage.error(error instanceof Error ? error.message : '操作失败，请稍后重试');
}
onMounted(init);
</script>

<style scoped lang="scss">
@use '@/assets/styles/components/page-shell' as pageShell;

@include pageShell.tree-table-crud-page;

.resource-page {
  width: 100%;
  min-width: 0;
}

.query-form :deep(.el-input),
.query-form :deep(.el-select) {
  width: 180px;
}

.resource-query-scroll {
  overflow: visible;
}

.resource-query-form {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px 14px;
}

.resource-query-form :deep(.el-form-item) {
  min-width: 0;
  width: auto;
  margin: 0;
}

.resource-query-form :deep(.el-form-item__content) {
  min-width: 0;
  flex: 1 1 0;
}

.resource-query-form :deep(.el-input),
.resource-query-form :deep(.el-select) {
  width: 100%;
}

.resource-query-form :deep(.el-form-item:last-child) {
  grid-column: 4;
  margin-left: 0;
  justify-self: end;
}

.textbook-panel :deep(.pagination-container),
.chunk-card :deep(.pagination-container) {
  padding: 12px 0 0;
}

.original-pdf-card {
  margin-top: 20px;
}

.original-pdf-meta {
  --el-descriptions-table-border: 1px solid var(--app-surface-border);
}

.original-pdf-meta :deep(.el-descriptions__body),
.original-pdf-meta :deep(.el-descriptions__content.el-descriptions__cell) {
  background: var(--app-surface-bg);
  color: var(--app-text-title);
}

.original-pdf-meta :deep(.el-descriptions__label.el-descriptions__cell) {
  background: var(--app-elevated-soft-bg) !important;
  color: var(--app-text-title);
}

.pdf-card-hint {
  margin: 12px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.content-grid {
  --resource-content-height: 720px;

  align-items: stretch;
  height: var(--resource-content-height);
}

.tree-panel-col,
.tree-content-col {
  height: 100%;
}

.tree-panel-shell,
.chunk-card {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.tree-panel-shell :deep(.el-card__body) {
  height: auto;
  max-height: none;
  flex: 1 1 auto;
}

.chunk-card {
  min-height: 0;
  flex: 1 1 auto;
}

.chunk-card :deep(.el-card__body) {
  display: flex;
  min-height: 0;
  flex: 1 1 auto;
  flex-direction: column;
  overflow: hidden;
}

.chunk-table-wrap {
  min-height: 0;
  flex: 1 1 auto;
}

.chunk-table-wrap :deep(.el-table) {
  width: 100%;
  min-height: 0;
}

.tree-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.tree-panel-header::after {
  content: '';
  border-right: 2px solid currentColor;
  border-bottom: 2px solid currentColor;
  color: var(--app-text-muted);
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
  color: var(--app-accent-strong);
}

.chunk-filter :deep(.el-input) {
  width: 210px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.data-table small {
  display: block;
  margin-top: 4px;
  color: var(--app-text-muted);
  font-size: 11px;
}

.chunk-content-preview {
  display: -webkit-box;
  overflow: hidden;
  line-height: 1.6;
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.chunk-detail-content {
  max-height: 420px;
  overflow: auto;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.mapped {
  color: var(--el-color-success);
  font-weight: 600;
}

.unmapped {
  color: var(--el-color-warning);
  font-weight: 600;
}

.edit-knowledge-select {
  width: 100%;
}

@media (max-width: 900px) {
  .content-grid {
    height: auto;
  }

  .tree-panel-col,
  .tree-content-col {
    height: 620px;
  }
}

@media (max-width: 900px) {
  .query-form :deep(.el-form-item),
  .query-form :deep(.el-input),
  .query-form :deep(.el-select) {
    width: 100%;
  }

  .toolbar-actions {
    align-items: stretch;
    width: 100%;
    flex-direction: column;
  }

  .chunk-filter :deep(.el-input) {
    width: 100%;
  }

  .toolbar {
    flex-wrap: wrap;
  }

  .resource-query-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .resource-query-form :deep(.el-form-item:last-child) {
    grid-column: auto;
    justify-self: start;
  }

  .chunk-card {
    height: auto;
    min-height: 520px;
  }
}
</style>
