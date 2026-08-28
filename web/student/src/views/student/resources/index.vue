<template>
  <div class="p-2 app-container resources-page">
    <el-card shadow="never" class="textbook-source-panel">
      <template #header>
        <div class="toolbar-shell">
          <div class="table-heading">
            <h3>教材原文</h3>
          </div>
          <div class="toolbar-actions">
            <el-select
              v-model="certificationId"
              clearable
              filterable
              placeholder="请选择考试资格"
              class="certification-select"
              @change="changeCertification"
            >
              <el-option v-for="item in certifications" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
            <el-input
              v-model="keyword"
              clearable
              placeholder="搜索教材名称"
              class="textbook-search"
              @keyup.enter="loadTextbookPdfs"
            />
            <el-button type="primary" :disabled="!certificationId" @click="loadTextbookPdfs">查询</el-button>
          </div>
        </div>
      </template>

      <el-alert v-if="!certificationId" type="info" :closable="false" show-icon title="请先选择要浏览的考试资格" />
      <template v-else>
        <el-table v-loading="loading" border class="data-table" :data="textbookPdfs">
          <el-table-column label="教材" min-width="260">
            <template #default="scope">
              <strong>{{ scope.row.title }}</strong>
              <span class="table-subtext">{{ scope.row.edition || scope.row.syllabusVersionName || '教材原文' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="文件" min-width="220" prop="fileName" show-overflow-tooltip />
          <el-table-column label="大小" width="110" align="center">
            <template #default="scope">{{ formatFileSize(scope.row.fileSize) }}</template>
          </el-table-column>
          <el-table-column label="上传时间" min-width="170" align="center" prop="uploadedTime" />
          <el-table-column label="操作" width="110" align="center">
            <template #default="scope">
              <el-button link type="primary" @click="openTextbookPdf(scope.row as PublicTextbookPdfListItemVO)">
                在线阅读
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!loading && textbookPdfs.length === 0" :image-size="88" description="暂无已发布教材" />
      </template>
    </el-card>

    <PdfReaderDialog
      v-model="pdfDialogVisible"
      :document-title="pdfReader?.title"
      :reader="pdfReader"
      @refresh="refreshPdfReader"
    />
  </div>
</template>

<script setup name="StudentResources" lang="ts">
import { onMounted, ref } from 'vue';
import {
  getPublicTextbookPdfReader,
  listPublicTextbookCertifications,
  listPublicTextbookPdfs,
  type PublicTextbookCertificationVO,
  type PublicTextbookPdfListItemVO,
  type PublicTextbookPdfReaderVO
} from '@/api/certmuse/public/textbooks';
import { getPublicBrowseCertificationId, setPublicBrowseCertificationId } from '@/utils/public-browse-certification';
import PdfReaderDialog from './PdfReaderDialog.vue';

const certifications = ref<PublicTextbookCertificationVO[]>([]);
const certificationId = ref(getPublicBrowseCertificationId());
const keyword = ref('');
const textbookPdfs = ref<PublicTextbookPdfListItemVO[]>([]);
const loading = ref(false);
const pdfDialogVisible = ref(false);
const pdfReader = ref<PublicTextbookPdfReaderVO>();
const readingTextbookId = ref<string>();

async function initialize() {
  certifications.value = await listPublicTextbookCertifications();
  if (certificationId.value && !certifications.value.some(item => item.id === certificationId.value)) {
    certificationId.value = '';
    setPublicBrowseCertificationId('');
  }
  if (certificationId.value) await loadTextbookPdfs();
}

function changeCertification() {
  setPublicBrowseCertificationId(certificationId.value);
  textbookPdfs.value = [];
  void loadTextbookPdfs();
}

async function loadTextbookPdfs() {
  if (!certificationId.value) return;
  loading.value = true;
  try {
    textbookPdfs.value = await listPublicTextbookPdfs(certificationId.value, keyword.value.trim() || undefined);
  } finally {
    loading.value = false;
  }
}

async function openTextbookPdf(textbook: PublicTextbookPdfListItemVO) {
  readingTextbookId.value = textbook.id;
  if (await refreshPdfReader()) pdfDialogVisible.value = true;
}

async function refreshPdfReader(): Promise<boolean> {
  if (!readingTextbookId.value) return false;
  pdfReader.value = await getPublicTextbookPdfReader(readingTextbookId.value);
  return true;
}

function formatFileSize(size: number) {
  if (size < 1024 * 1024) return `${Math.max(1, Math.round(size / 1024))} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

onMounted(() => void initialize());
</script>

<style scoped lang="scss">
.resources-page {
  width: 100%;
  max-width: none;
  margin: 0;
}
.textbook-source-panel {
  border-radius: 14px;
}
.toolbar-shell,
.toolbar-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}
.toolbar-shell {
  justify-content: space-between;
}
.table-heading h3 {
  margin: 0 0 6px;
}
.certification-select {
  flex: 1 1 30%;
  max-width: 100%;
}
.textbook-search {
  flex: 1 1 35%;
  max-width: 100%;
}
.table-subtext {
  display: block;
  margin-top: 5px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
@media (max-width: 760px) {
  .toolbar-shell,
  .toolbar-actions {
    align-items: stretch;
    flex-direction: column;
  }
  .certification-select,
  .textbook-search {
    width: 100%;
  }
}
</style>
