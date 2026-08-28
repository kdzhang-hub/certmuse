import { to as tos } from 'await-to-js';
import { ElMessage } from 'element-plus/es';
import * as NProgressModule from 'nprogress';
import 'nprogress/nprogress.css';
import { usePermissionStore } from '@/store/modules/permission';
import { useSettingsStore } from '@/store/modules/settings';
import { useUserStore } from '@/store/modules/user';
import { getToken } from '@/utils/auth';
import { LEARNING_HOME_TARGET, resolveAdminTarget } from '@/utils/auth-routing';
import { isHandledRequestError, isRelogin } from '@/utils/request';
import { isHttp, isPathMatch } from '@/utils/validate';
import router from './router';

const NProgress = ('default' in NProgressModule ? NProgressModule.default : NProgressModule) as typeof NProgressModule;

NProgress.configure({ showSpinner: false });
const whiteList = [
  '/login',
  '/register',
  '/social-callback',
  '/register*',
  '/register/*',
  '/',
  '/home',
  '/qualifications',
  '/qualifications/*',
  '/exam-introduction',
  '/qualification-fit-test'
];

const isWhiteList = (path: string) => {
  return whiteList.some(pattern => isPathMatch(pattern, path));
};

const installAdminRoutes = async () => {
  const accessRoutes = await usePermissionStore().generateRoutes();
  accessRoutes.forEach(route => {
    if (!isHttp(route.path)) router.addRoute(route);
  });
};

const routeExistingSession = async (redirect: unknown) => {
  const userStore = useUserStore();
  if (!userStore.entryType) await userStore.getInfo();

  if (userStore.entryType === 'LEARNING') {
    window.location.replace(LEARNING_HOME_TARGET);
    return false;
  }
  if (userStore.entryType !== 'ADMIN') throw new Error('当前账号入口无法访问系统，请联系管理员');

  await installAdminRoutes();
  return resolveAdminTarget(redirect);
};

router.beforeEach(async (to, from) => {
  NProgress.start();
  if (getToken()) {
    to.meta.title && useSettingsStore().setTitle(to.meta.title as string);
    /* has token*/
    if (to.path === '/login') {
      const [err, target] = await tos(routeExistingSession(to.query.redirect));
      NProgress.done();
      if (!err && target) return target;
      if (!err) return false;
      useUserStore().clearSession();
      ElMessage.error(err instanceof Error ? err.message : '当前会话无法继续，请重新登录');
      return true;
    } else if (isWhiteList(to.path)) {
      return true;
    } else {
      if (useUserStore().roles.length === 0) {
        isRelogin.show = true;
        // 判断当前用户是否已拉取完user_info信息
        const [err] = await tos(useUserStore().getInfo());
        if (err) {
          useUserStore().clearSession();
          if (!isHandledRequestError(err)) {
            ElMessage.error(err instanceof Error ? err.message : String(err));
          }
          return { path: '/' };
        } else {
          isRelogin.show = false;
          if (useUserStore().entryType !== 'ADMIN') {
            const [routingError] = await tos(routeExistingSession(to.fullPath));
            if (routingError) {
              useUserStore().clearSession();
              ElMessage.error(routingError instanceof Error ? routingError.message : '当前会话无法继续，请重新登录');
              return { path: '/login' };
            }
            return false;
          }
          await installAdminRoutes();
          // hack方法 确保addRoutes已完成
          return {
            path: to.path,
            replace: true,
            params: to.params,
            query: to.query,
            hash: to.hash,
            name: to.name as string
          };
        }
      } else {
        return true;
      }
    }
  } else {
    // 没有token
    if (isWhiteList(to.path)) {
      // 在免登录白名单，直接进入
      return true;
    } else {
      const redirect = encodeURIComponent(to.fullPath || '/');
      NProgress.done();
      return `/login?redirect=${redirect}`; // 否则全部重定向到登录页
    }
  }
});

router.afterEach(() => {
  NProgress.done();
});
