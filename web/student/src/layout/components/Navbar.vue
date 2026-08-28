<template>
  <header class="public-header">
    <a class="brand" href="/home" aria-label="CertMuse 软考学习平台首页">
      <span class="brand-mark">C</span>
      <span>CertMuse</span>
      <small>软考学习平台</small>
    </a>
    <nav class="public-nav" aria-label="公开导航">
      <a v-for="item in navigation" :key="item.path" :href="item.path" class="nav-link">{{ item.title }}</a>
    </nav>
    <a v-if="!isAuthenticated" class="login-link" href="/login">登录 / 注册</a>
    <el-dropdown v-else class="user-menu" trigger="click" @command="handleUserCommand">
      <button class="user-menu__trigger" type="button" aria-label="打开账户菜单">
        <img :src="userStore.avatar" class="user-menu__avatar" alt="" />
        <span class="user-menu__meta">
          <span class="user-menu__name">{{ displayName }}</span>
          <span class="user-menu__role">Workspace</span>
        </span>
        <el-icon class="user-menu__arrow"><CaretBottom /></el-icon>
      </button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="profile">个人中心</el-dropdown-item>
          <el-dropdown-item command="settings">布局设置</el-dropdown-item>
          <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </header>
</template>

<script setup name="StudentPublicNavbar" lang="ts">
import { ElMessageBox } from 'element-plus';
import { CaretBottom } from '@element-plus/icons-vue';
import router from '@/router';
import { useUserStore } from '@/store/modules/user';

const emit = defineEmits<{ setLayout: [] }>();
const userStore = useUserStore();
const isAuthenticated = computed(() => Boolean(userStore.token));
const displayName = computed(() => userStore.nickname || '学员');

const navigation = [
  { title: '首页', path: '/home' },
  { title: '学习首页', path: '/learning/home' },
  { title: '题库', path: '/learning/question-bank' },
  { title: '资料库', path: '/learning/resources' },
  { title: '今日任务', path: '/learning/tasks' },
  { title: '错题复习', path: '/learning/mistakes' },
  { title: '学习画像', path: '/learning/progress' },
  { title: '学习记录', path: '/learning/history' },
];

async function handleUserCommand(command: 'profile' | 'settings' | 'logout') {
  if (command === 'profile') {
    router.push('/learning/account');
    return;
  }
  if (command === 'settings') {
    emit('setLayout');
    return;
  }
  await ElMessageBox.confirm('确定注销并退出系统吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  });
  await userStore.logout();
  router.replace({ path: '/login', query: { redirect: encodeURIComponent(router.currentRoute.value.fullPath || '/learning/home') } });
}

</script>

<style scoped lang="scss">
.public-header {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  min-height: 72px;
  padding: 0 max(28px, calc((100vw - 1280px) / 2));
  gap: 34px;
  border-bottom: 1px solid var(--student-header-border);
  background: var(--app-navbar-bg);
  box-shadow: var(--app-navbar-shadow);
}
.brand {
  display: flex;
  align-items: center;
  gap: 9px;
  flex-shrink: 0;
  color: var(--student-header-text);
  font-size: 20px;
  font-weight: 800;
  text-decoration: none;
}
.brand-mark {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  color: #fff;
  font-size: 16px;
  border-radius: 9px;
  background: #409eff;
}
.brand small {
  padding-left: 9px;
  color: var(--student-header-subtle);
  font-size: 12px;
  font-weight: 500;
  border-left: 1px solid var(--student-header-border);
}
.public-nav {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: clamp(14px, 2vw, 32px);
  flex: 1;
}
.nav-link,
.login-link {
  color: var(--student-header-muted);
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
  white-space: nowrap;
}
.nav-link:hover,
.nav-link.router-link-active {
  color: #409eff;
}
.login-link {
  padding: 9px 14px;
  color: #fff;
  border-radius: 6px;
  background: #409eff;
}
.login-link:hover {
  background: #337ecc;
}
.user-menu { flex: none; }
.user-menu__trigger {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
  padding: 4px 9px 4px 4px;
  color: inherit;
  border: 1px solid var(--student-header-control-border);
  border-radius: 13px;
  background: var(--student-header-control-bg);
  cursor: pointer;
  transition: border-color .2s ease, background .2s ease;
}
.user-menu__trigger:hover { border-color: var(--student-header-control-hover-border); background: var(--student-header-control-hover-bg); }
.user-menu__avatar { width: 30px; height: 30px; border-radius: 11px; object-fit: cover; }
.user-menu__meta { display: flex; flex-direction: column; align-items: flex-start; gap: 1px; min-width: 0; }
.user-menu__name { max-width: 92px; overflow: hidden; color: var(--student-header-text); font-size: 12px; font-weight: 700; line-height: 1.2; text-overflow: ellipsis; white-space: nowrap; }
.user-menu__role { color: var(--student-header-subtle); font-size: 11px; line-height: 1.2; }
.user-menu__arrow { flex: none; color: var(--student-header-subtle); font-size: 13px; }
@media (max-width: 900px) {
  .public-header {
    gap: 16px;
    padding: 0 18px;
  }
  .public-nav {
    gap: 14px;
    overflow-x: auto;
    justify-content: flex-start;
  }
  .brand small {
    display: none;
  }
  .user-menu__meta,
  .user-menu__arrow { display: none; }
  .user-menu__trigger { padding: 4px; }
}
</style>
