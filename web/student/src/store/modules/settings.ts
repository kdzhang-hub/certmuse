import { useStorage } from '@vueuse/core';
import { defineStore } from 'pinia';
import { ref, watch } from 'vue';
import { NavTypeEnum } from '@/enums/NavTypeEnum';
import defaultSettings from '@/settings';
import { useDynamicTitle } from '@/utils/dynamicTitle';

export const useSettingsStore = defineStore('setting', () => {
  const storageSetting = useStorage<LayoutSetting>('layout-setting', {
    tagsView: defaultSettings.tagsView,
    tagsViewPersist: defaultSettings.tagsViewPersist,
    tagsIcon: defaultSettings.tagsIcon,
    fixedHeader: defaultSettings.fixedHeader,
    sidebarLogo: defaultSettings.sidebarLogo,
    dynamicTitle: defaultSettings.dynamicTitle,
    sideTheme: defaultSettings.sideTheme,
    theme: defaultSettings.theme,
    dark: defaultSettings.dark,
    navType: defaultSettings.navType,
    radiusBase: defaultSettings.radiusBase,
    fullHeightTable: defaultSettings.fullHeightTable
  });
  const title = ref<string>(defaultSettings.title);
  const theme = ref<string>(storageSetting.value.theme);
  const sideTheme = ref<string>(storageSetting.value.sideTheme);
  const showSettings = ref<boolean>(defaultSettings.showSettings);
  const tagsView = ref<boolean>(storageSetting.value.tagsView);
  const tagsViewPersist = ref<boolean>(storageSetting.value.tagsViewPersist);
  const tagsIcon = ref<boolean>(storageSetting.value.tagsIcon);
  const fixedHeader = ref<boolean>(storageSetting.value.fixedHeader);
  const sidebarLogo = ref<boolean>(storageSetting.value.sidebarLogo);
  const dynamicTitle = ref<boolean>(storageSetting.value.dynamicTitle);
  const animationEnable = ref<boolean>(defaultSettings.animationEnable);
  const dark = ref<boolean>(storageSetting.value.dark ?? defaultSettings.dark);
  const navType = ref<NavTypeEnum>(storageSetting.value.navType || NavTypeEnum.LEFT);
  const radiusBase = ref<number>(storageSetting.value.radiusBase ?? defaultSettings.radiusBase);
  const fullHeightTable = ref<boolean>(storageSetting.value.fullHeightTable ?? defaultSettings.fullHeightTable);

  const setTitle = (value: string) => {
    title.value = value;
    useDynamicTitle();
  };

  // 公共首页与学习端共用 layout-setting.dark；在应用初始化时同步 DOM，
  // 因此从首页进入学习端不会重新落回浅色模式。
  watch(
    dark,
    value => {
      document.documentElement.classList.toggle('dark', value);
      storageSetting.value.dark = value;
    },
    { immediate: true }
  );
  return {
    title,
    theme,
    sideTheme,
    showSettings,
    tagsView,
    tagsViewPersist,
    tagsIcon,
    fixedHeader,
    sidebarLogo,
    dynamicTitle,
    animationEnable,
    dark,
    navType,
    radiusBase,
    fullHeightTable,
    setTitle
  };
});
