import { mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import { reactive } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';
import Sidebar from './index.vue';

const sidebar = reactive({ opened: true });

vi.mock('@/store/modules/app', () => ({
  useAppStore: () => ({ sidebar })
}));

vi.mock('@/store/modules/permission', () => ({
  usePermissionStore: () => ({
    getSidebarRoutes: () => []
  })
}));

vi.mock('@/store/modules/settings', () => ({
  useSettingsStore: () => ({
    sidebarLogo: false,
    sideTheme: 'theme-dark',
    theme: '#409eff'
  })
}));

const router = createRouter({
  history: createMemoryHistory(),
  routes: [{ path: '/learning/home', component: { template: '<div />' } }]
});

function mountSidebar() {
  return mount(Sidebar, {
    global: {
      plugins: [router],
      stubs: {
        ElMenu: { template: '<nav><slot /></nav>' },
        ElScrollbar: { template: '<div><slot /></div>' },
        Logo: true,
        SidebarItem: true,
        SvgIcon: { template: '<svg />' }
      }
    }
  });
}

describe('student sidebar public home link', () => {
  it('keeps an accessible link to the learner home page in both sidebar states', async () => {
    sidebar.opened = true;
    const wrapper = mountSidebar();
    const homeLink = wrapper.get('.public-home-link');

    expect(homeLink.attributes('href')).toBe('/learning/home');
    expect(homeLink.attributes('aria-label')).toBe('回到学习首页');
    expect(homeLink.attributes('title')).toBe('回到首页');
    expect(homeLink.text()).toContain('回到首页');

    sidebar.opened = false;
    await wrapper.vm.$nextTick();

    expect(wrapper.get('.public-home-link').text()).not.toContain('回到首页');
  });
});
