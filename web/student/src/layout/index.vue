<template>
  <div :class="classObj" class="app-wrapper" :style="{ '--current-color': theme }">
    <div v-if="!isFocusMode && device === 'mobile' && sidebar.opened" class="drawer-bg" @click="handleClickOutside" />
    <div
      :class="{
        hasTagsView: false,
        sidebarHide: true,
        'focus-main-container': isFocusMode
      }"
      class="main-container"
    >
      <div v-if="!isFocusMode" :class="{ 'fixed-header': fixedHeader }" class="layout-header">
        <navbar @set-layout="setLayout" />
      </div>
      <app-main
        :class="{
          'with-fixed-header': fixedHeader && !isFocusMode,
          'with-tags-view': false,
          'focus-app-main': isFocusMode
        }"
      />
      <settings v-if="!isFocusMode" ref="settingRef" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { useAppStore } from '@/store/modules/app';
import { useSettingsStore } from '@/store/modules/settings';
import { initMessageBox, initPush } from '@/utils/push';
import { AppMain, Navbar, Settings } from './components';

const settingsStore = useSettingsStore();
const theme = computed(() => settingsStore.theme);
const sidebar = computed(() => useAppStore().sidebar);
const device = computed(() => useAppStore().device);
const fixedHeader = computed(() => settingsStore.fixedHeader);
const route = useRoute();
const isFocusMode = computed(() => route.meta.focusMode === true);

const classObj = computed(() => ({
  hideSidebar: !sidebar.value.opened,
  openSidebar: sidebar.value.opened,
  withoutAnimation: sidebar.value.withoutAnimation,
  mobile: device.value === 'mobile',
  'focus-mode': isFocusMode.value
}));

const { width } = useWindowSize();
const WIDTH = 992;
watch(
  width,
  w => {
    if (w - 1 < WIDTH) {
      useAppStore().toggleDevice('mobile');
      useAppStore().closeSideBar({ withoutAnimation: true });
    } else {
      useAppStore().toggleDevice('desktop');
    }
  },
  { immediate: true }
);

const settingRef = ref<InstanceType<typeof Settings>>();
onMounted(async () => {
  try {
    await initMessageBox();
  } finally {
    initPush();
  }
});
const handleClickOutside = () => useAppStore().closeSideBar({ withoutAnimation: false });
const setLayout = () => settingRef.value?.openSetting();
</script>

<style lang="scss" scoped>
@use '@/assets/styles/mixin.scss';
@use '@/assets/styles/tokens/sass-vars' as *;
.app-wrapper { @include mixin.clearfix; position: relative; height: 100%; width: 100%; background: var(--app-shell-bg); }
.app-wrapper.mobile.openSidebar { position: fixed; top: 0; }
.drawer-bg { background: #000; opacity: .4; width: 100%; top: 0; height: 100%; position: absolute; z-index: 999; }
.layout-header { position: relative; z-index: 9; background: #fff; }
.fixed-header { position: fixed; top: 0; right: 0; left: 0; width: 100%; transition: width .28s; }
.mobile .fixed-header { width: 100%; top: 0; }
:global(#app .focus-mode .main-container) { width: 100%; min-height: 100vh; margin-left: 0 !important; }
</style>
