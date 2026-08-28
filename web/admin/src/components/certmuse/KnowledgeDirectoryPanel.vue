<template>
  <aside class="knowledge-directory-panel" :class="{ 'is-collapsed': collapsed }">
    <header
      class="directory-header"
      :class="{ 'is-collapsible': collapsible }"
      :aria-expanded="!collapsed"
      @click="toggleCollapsed"
    >
      <div v-show="!collapsed">
        <span v-if="eyebrow" class="eyebrow">{{ eyebrow }}</span>
        <h3>{{ title }}</h3>
      </div>
      <el-tag v-if="showCounts && !collapsed" type="success" effect="plain">{{ knowledgeCount }}</el-tag>
    </header>
    <template v-if="!collapsed">
      <el-input
        v-model="keyword"
        class="directory-search"
        clearable
        :placeholder="searchPlaceholder"
        :prefix-icon="Search"
      />
      <el-tree
        ref="treeRef"
        class="directory-tree"
        node-key="id"
        :data="nodes"
        :props="treeProps"
        :filter-node-method="filterNode"
        default-expand-all
        highlight-current
        @node-click="node => emit('select', node)"
      >
        <template #default="{ data }">
          <span class="directory-node">
            <span class="directory-node-label">{{ data.label }}</span>
            <small v-if="showCounts && data.children?.length">{{ data.children.length }}</small>
          </span>
        </template>
      </el-tree>
      <el-button text class="clear-directory" @click="clear">{{ clearLabel }}</el-button>
    </template>
  </aside>
</template>

<script setup lang="ts">
import { Search } from '@element-plus/icons-vue';
import { computed, ref, watch } from 'vue';

export interface KnowledgeDirectoryNode {
  id: string;
  label: string;
  children?: KnowledgeDirectoryNode[];
}

const props = withDefaults(
  defineProps<{
    nodes: KnowledgeDirectoryNode[];
    title?: string;
    eyebrow?: string;
    searchPlaceholder?: string;
    clearLabel?: string;
    showCounts?: boolean;
    collapsible?: boolean;
    collapsed?: boolean;
  }>(),
  {
    title: '知识点目录',
    eyebrow: 'KNOWLEDGE DIRECTORY',
    searchPlaceholder: '搜索知识点',
    clearLabel: '清除目录筛选',
    showCounts: true,
    collapsible: false,
    collapsed: false
  }
);
const emit = defineEmits<{ select: [node: KnowledgeDirectoryNode]; clear: []; 'update:collapsed': [value: boolean] }>();
const keyword = ref('');
const treeRef = ref<{ filter: (value: string) => void; setCurrentKey: (key?: string) => void }>();
const treeProps = { children: 'children', label: 'label' };
const knowledgeCount = computed(() => {
  const countNodes = (nodes: KnowledgeDirectoryNode[]): number =>
    nodes.reduce((count, node) => count + 1 + countNodes(node.children ?? []), 0);
  return countNodes(props.nodes);
});

watch(keyword, value => treeRef.value?.filter(value));
watch(
  () => props.nodes,
  () => {
    keyword.value = '';
    treeRef.value?.setCurrentKey();
  }
);

function filterNode(value: string, data: KnowledgeDirectoryNode) {
  return !value.trim() || data.label.toLowerCase().includes(value.trim().toLowerCase());
}
function clear() {
  keyword.value = '';
  treeRef.value?.setCurrentKey();
  emit('clear');
}
function toggleCollapsed() {
  if (props.collapsible) emit('update:collapsed', !props.collapsed);
}
</script>

<style scoped lang="scss">
.knowledge-directory-panel {
  min-height: 560px;
  padding: 17px 14px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 14px;
  background: var(--el-bg-color);
  box-shadow: 0 8px 24px rgb(15 23 42 / 5%);
  transition:
    padding 0.24s ease,
    min-height 0.24s ease;
}
.directory-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 14px;
}
.directory-header.is-collapsible {
  cursor: pointer;
  user-select: none;
}
.directory-header.is-collapsible:hover { color: var(--app-accent-strong); }
.directory-header.is-collapsible::after {
  content: '';
  width: 9px;
  min-width: 9px;
  height: 9px;
  margin-left: auto;
  flex-shrink: 0;
  border-right: 2px solid currentColor;
  border-bottom: 2px solid currentColor;
  color: var(--el-text-color-secondary);
  transform-origin: center;
  transform: rotate(135deg);
  transition:
    transform 0.24s ease,
    color 0.24s ease;
}
.knowledge-directory-panel.is-collapsed {
  min-height: 560px;
  padding: 17px 12px;
}
.knowledge-directory-panel.is-collapsed .directory-header {
  justify-content: center;
  height: 100%;
  margin: 0;
}
.knowledge-directory-panel.is-collapsed .directory-header::after {
  margin-left: 0;
  transform: rotate(-45deg);
}
.eyebrow {
  display: block;
  margin-bottom: 4px;
  color: var(--app-accent-strong);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.12em;
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
.directory-node {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
  gap: 8px;
}
.directory-node-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.directory-node small {
  flex: 0 0 auto;
  color: var(--el-text-color-secondary);
}
.clear-directory {
  width: 100%;
  margin-top: 8px;
}
@media (max-width: 900px) {
  .knowledge-directory-panel {
    min-height: 0;
  }
  .knowledge-directory-panel.is-collapsed {
    min-height: 64px;
  }
  .directory-tree {
    min-height: 240px;
    max-height: 320px;
  }
}
</style>
