import { to as tos } from 'await-to-js';
import { ElMessage } from 'element-plus/es';
import * as NProgressModule from 'nprogress';
import 'nprogress/nprogress.css';
import { getOnboardingStatus } from '@/api/certmuse/learning/onboarding';
import type { OnboardingStatus } from '@/api/types';
import { useSettingsStore } from '@/store/modules/settings';
import { useUserStore } from '@/store/modules/user';
import { getToken } from '@/utils/auth';
import { isLearningEntry } from '@/utils/auth-entry';
import { isConsistentOnboardingStatus, resolveOnboardingTargetWithSession } from '@/utils/onboarding-routing';
import { isHandledRequestError, isRelogin } from '@/utils/request';
import { isPathMatch } from '@/utils/validate';
import router from './router';
import { seedStudentRoutes } from './router/student';

const NProgress = ('default' in NProgressModule ? NProgressModule.default : NProgressModule) as typeof NProgressModule;

let onboardingStatusSnapshot: OnboardingStatus | undefined;
let onboardingStatusRequest: Promise<OnboardingStatus | undefined> | undefined;

const loadOnboardingStatus = () => {
  if (onboardingStatusSnapshot) {
    return Promise.resolve(onboardingStatusSnapshot);
  }
  if (!onboardingStatusRequest) {
    onboardingStatusRequest = getOnboardingStatus()
      .then(response => {
        onboardingStatusSnapshot = response.data;
        return onboardingStatusSnapshot;
      })
      .finally(() => {
        onboardingStatusRequest = undefined;
      });
  }
  return onboardingStatusRequest;
};

NProgress.configure({ showSpinner: false });
const whiteList = [
  '/login',
  '/social-callback',
  '/',
  // 游客可以浏览题库入口与资料；进入具体练习、考试或产生学习记录的路径
  // 仍不在白名单内，由下面的登录守卫处理。
  '/learning/question-bank',
  '/learning/question-bank/past-papers',
  '/learning/resources'
];

const isWhiteList = (path: string) => {
  return whiteList.some(pattern => isPathMatch(pattern, path));
};

const requiresLearningGoal = (path: string) =>
  path === '/learning/tasks' ||
  path.startsWith('/learning/tasks/') ||
  path === '/learning/progress' ||
  path === '/learning/question-bank/knowledge-practice' ||
  path === '/learning/question-bank/mock-exams' ||
  path.startsWith('/learning/question-bank/mock-exams/') ||
  path.startsWith('/learning/question-bank/past-papers/') ||
  path.startsWith('/learning/diagnostic/') ||
  path.startsWith('/learning/session/') ||
  path.startsWith('/learning/result/');

const goalRequiredRedirect = async (path: string) => {
  if (!requiresLearningGoal(path)) return undefined;
  const status = await loadOnboardingStatus();
  return status?.nextAction === 'SET_GOAL' ? '/learning/goal-required' : undefined;
};

const unifiedLoginUrl = (redirect: string) => {
  const configuredEntry = import.meta.env.VITE_AUTH_ENTRY_URL as string | undefined;
  const authEntry = configuredEntry || (import.meta.env.DEV ? 'http://localhost:3000/login' : '/login');
  const url = new URL(authEntry, window.location.origin);
  url.searchParams.set('redirect', redirect);
  return url.toString();
};

const handoffNonLearner = (redirect: string) => {
  useUserStore().clearSession();
  window.location.replace(unifiedLoginUrl(redirect));
};

const onboardingRedirect = async (path: string) => {
  // 自我诊断只保护自身会话状态；有有效学习目标的学员可以先使用其他学习能力。
  const protectedPath = path.startsWith('/learning/diagnostic/');
  if (!protectedPath || path === '/learning/onboarding-error') return undefined;
  const status = await loadOnboardingStatus();
  const target =
    status && isConsistentOnboardingStatus(status)
      ? resolveOnboardingTargetWithSession(status.nextAction, status.activeSessionId)
      : undefined;
  if (!target) return '/learning/onboarding-error';
  return target.split('?')[0] === path ? undefined : target;
};

router.beforeEach(async (to, from) => {
  NProgress.start();
  if (import.meta.env.VITE_APP_STUDENT_MOCK === 'true') {
    to.meta.title && useSettingsStore().setTitle(to.meta.title as string);
    if (to.path === '/login') {
      return { path: '/' };
    }
    return (await goalRequiredRedirect(to.path)) || true;
  }
  if (getToken()) {
    to.meta.title && useSettingsStore().setTitle(to.meta.title as string);
    /* has token*/
    if (to.path === '/login') {
      if (!useUserStore().entryType) {
        const [err] = await tos(useUserStore().getInfo());
        if (err) {
          useUserStore().clearSession();
          return true;
        }
      }
      if (!isLearningEntry(useUserStore().entryType)) {
        NProgress.done();
        handoffNonLearner('/learning/home');
        return false;
      }
      NProgress.done();
      return { path: (await onboardingRedirect('/learning/home')) || '/learning/home' };
    } else if (isWhiteList(to.path)) {
      // 题库、资料和历年真题允许游客浏览，但已登录用户进入这些白名单页面时
      // 仍要恢复当前会话的身份资料。否则右上角会保留空的 Pinia 初始状态，
      // 与其他学习页（会在首次访问时调用 getInfo）显示不一致。
      if (useUserStore().roles.length === 0) {
        isRelogin.show = true;
        const [err] = await tos(useUserStore().getInfo());
        if (err) {
          useUserStore().clearSession();
          if (!isHandledRequestError(err)) {
            ElMessage.error(err instanceof Error ? err.message : String(err));
          }
          return { path: '/' };
        }
        if (!isLearningEntry(useUserStore().entryType)) {
          NProgress.done();
          handoffNonLearner(to.fullPath || '/learning/home');
          return false;
        }
        isRelogin.show = false;
      }
      seedStudentRoutes();
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
          if (!isLearningEntry(useUserStore().entryType)) {
            NProgress.done();
            handoffNonLearner(to.fullPath || '/learning/home');
            return false;
          }
          isRelogin.show = false;
          // 学生页面与菜单均由本地静态路由提供。管理菜单接口要求后台权限，
          // 学员首次登录时请求它会中断导航并留下空白页面。
          seedStudentRoutes();
          const target = (await goalRequiredRedirect(to.path)) || (await onboardingRedirect(to.path));
          if (target) return target;
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
        const target = (await goalRequiredRedirect(to.path)) || (await onboardingRedirect(to.path));
        return target || true;
      }
    }
  } else {
    // 没有token
    if (isWhiteList(to.path)) {
      // 游客浏览公开页时同样装载完整菜单；菜单仅展示可去往的能力，
      // 受限项点击后仍由本守卫跳转登录。
      seedStudentRoutes();
      return true;
    } else {
      const redirect = encodeURIComponent(to.fullPath || '/');
      NProgress.done();
      return `/login?redirect=${redirect}&reason=unlock`; // 登录页加载后再提示，避免路由切换清掉消息
    }
  }
});

router.afterEach(() => {
  onboardingStatusSnapshot = undefined;
  NProgress.done();
});
