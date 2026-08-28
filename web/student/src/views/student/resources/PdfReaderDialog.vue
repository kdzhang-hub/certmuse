<template>
  <el-dialog
    :model-value="modelValue"
    fullscreen
    destroy-on-close
    append-to-body
    class="pdf-reader-dialog"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template #header>
      <div class="reader-title">
        <span>{{ documentTitle || '教材原文' }}</span>
        <small>缩放、页码与搜索请使用浏览器 PDF 工具栏</small>
      </div>
    </template>
    <div class="reader-layout" @contextmenu.prevent>
      <div class="reader-toolbar" aria-label="PDF 阅读工具栏">
        <span class="reader-mode">浏览器兼容预览</span>
        <el-button :disabled="loading" @click="emit('refresh')">刷新阅读链接</el-button>
      </div>
      <main class="pdf-scroll-host">
        <el-skeleton v-if="loading" :rows="12" animated class="reader-loading" />
        <el-alert v-else-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon>
          <template #default><el-button type="primary" link @click="emit('refresh')">获取新的阅读链接</el-button></template>
        </el-alert>
        <div v-else-if="nativePreviewUrl" class="native-preview-container">
          <iframe
            class="native-pdf-preview"
            :src="nativePreviewUrl"
            :title="`${documentTitle || '教材原文'}（浏览器兼容预览）`"
            @error="errorMessage = '教材原文暂时无法加载，请刷新阅读链接后重试。'"
          />
        </div>
      </main>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';
import { resolvePdfReaderUrl, toNativePdfPreviewUrl } from './pdf-reader';

interface ReaderDocument {
  readUrl: string;
  expiresAt: string;
}

const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    documentTitle?: string;
    reader?: ReaderDocument;
    initialPage?: number;
  }>(),
  { documentTitle: '', initialPage: 1 }
);
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; refresh: [] }>();

const loading = ref(false);
const errorMessage = ref('');
const nativePreviewUrl = ref('');
let refreshTimer: number | undefined;

watch(
  () => [props.modelValue, props.reader?.readUrl, props.initialPage] as const,
  () => updatePreview(),
  { immediate: true }
);

function updatePreview() {
  resetPreview();
  if (!props.modelValue || !props.reader?.readUrl) return;
  const readUrl = resolvePdfReaderUrl(props.reader.readUrl, import.meta.env.VITE_APP_BASE_API);
  nativePreviewUrl.value = toNativePdfPreviewUrl(readUrl, normalizePage(props.initialPage));
  scheduleRefresh();
}
function normalizePage(page: number | undefined) {
  return Math.max(1, Math.round(page || 1));
}
function scheduleRefresh() {
  const expiresAt = props.reader?.expiresAt ? new Date(props.reader.expiresAt).getTime() : 0;
  const delay = expiresAt - Date.now() - 30_000;
  if (delay > 0) refreshTimer = window.setTimeout(() => emit('refresh'), delay);
}
function resetPreview() {
  if (refreshTimer) window.clearTimeout(refreshTimer);
  refreshTimer = undefined;
  loading.value = false;
  errorMessage.value = '';
  nativePreviewUrl.value = '';
}
onBeforeUnmount(resetPreview);
</script>

<style scoped lang="scss">
:global(.pdf-reader-dialog) { display: flex; flex-direction: column; overflow: hidden; }
:global(.pdf-reader-dialog .el-dialog__header) { flex: 0 0 auto; }
:global(.pdf-reader-dialog .el-dialog__body) { display: flex; min-height: 0; flex: 1; overflow: hidden; }
.reader-title { display: flex; flex-direction: column; gap: 4px; font-weight: 600; }
.reader-title small { color: var(--el-text-color-secondary); font-size: 12px; font-weight: 400; }
.reader-layout { display: flex; min-height: 0; height: 100%; flex: 1; flex-direction: column; }
.reader-toolbar { position: relative; z-index: 1; display: flex; flex: 0 0 auto; align-items: center; justify-content: space-between; gap: 12px; border-bottom: 1px solid var(--el-border-color); padding: 0 0 12px; background: var(--el-bg-color); }
.reader-mode { color: var(--el-text-color-secondary); font-size: 13px; }
.pdf-scroll-host { display: flex; min-height: 0; flex: 1; overflow: hidden; border: 1px solid var(--el-border-color); border-radius: 8px; background: var(--el-fill-color-darker); }
.native-preview-container { display: flex; min-height: 0; width: 100%; height: 100%; flex: 1; }
.native-pdf-preview { display: block; width: 100%; min-height: 0; flex: 1; border: 0; background: var(--el-bg-color); }
.reader-loading { width: 100%; padding: 20px; background: var(--el-bg-color); }
</style>
