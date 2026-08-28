import type { EntryType, OnboardingNextAction } from '@/api/types';

const AUTH_LOOP_PATHS = ['/login', '/register', '/logout', '/401', '/404', '/redirect'];

export const isEntryType = (value: unknown): value is EntryType => value === 'ADMIN' || value === 'LEARNING';

export const LEARNING_HOME_TARGET = '/learning/home';

const firstQueryValue = (value: unknown): string => {
  if (Array.isArray(value)) {
    return typeof value[0] === 'string' ? value[0] : '';
  }
  return typeof value === 'string' ? value : '';
};

const decodeOnce = (value: string): string => {
  try {
    return decodeURIComponent(value);
  } catch {
    return '';
  }
};

/**
 * 将登录前地址收敛为安全站内地址。
 * 返回值只可能是以单个 / 开头的 path/query/hash，不包含源站信息。
 */
export const sanitizeRedirect = (raw: unknown): string | undefined => {
  const source = firstQueryValue(raw).trim();
  if (!source) return undefined;

  const candidate = source.includes('%') ? decodeOnce(source) : source;
  if (!candidate || !candidate.startsWith('/') || candidate.startsWith('//') || candidate.includes('\\')) {
    return undefined;
  }
  if ([...candidate].some(char => char.charCodeAt(0) <= 31 || char.charCodeAt(0) === 127)) return undefined;

  let url: URL;
  try {
    url = new URL(candidate, 'https://certmuse.local');
  } catch {
    return undefined;
  }
  if (url.origin !== 'https://certmuse.local') return undefined;

  const path = `${url.pathname}${url.search}${url.hash}`;
  if (AUTH_LOOP_PATHS.some(blocked => url.pathname === blocked || url.pathname.startsWith(`${blocked}/`))) {
    return undefined;
  }
  return path;
};

export const isLearningTarget = (path: string): boolean => {
  const pathname = new URL(path, 'https://certmuse.local').pathname;
  return pathname === '/learning' || pathname.startsWith('/learning/');
};

export const resolveAdminTarget = (redirect: unknown): string => {
  const target = sanitizeRedirect(redirect);
  return target && !isLearningTarget(target) && target !== '/' && target !== '/home' ? target : '/admin/index';
};

/** Unified login only chooses the application entry; learner workflows continue inside the learner app. */
export const resolveEntryTarget = (entryType: EntryType, redirect: unknown): string =>
  entryType === 'ADMIN' ? resolveAdminTarget(redirect) : LEARNING_HOME_TARGET;

const LEARNING_ACTION_TARGET: Record<OnboardingNextAction, string> = {
  SET_GOAL: '/learning/onboarding',
  START_DIAGNOSTIC: '/learning/diagnostic/intro',
  CONTINUE_DIAGNOSTIC: '/learning/diagnostic/session',
  WAIT_PROCESSING: '/learning/home?state=processing',
  ENTER_HOME: '/learning/home',
  RETRY_DIAGNOSTIC: '/learning/diagnostic/intro?mode=retry',
  CONTACT_SUPPORT: '/learning/home?state=support'
};

export const resolveLearningTarget = (nextAction: OnboardingNextAction, redirect: unknown): string => {
  if (!(nextAction in LEARNING_ACTION_TARGET)) {
    throw new Error('未知的学员流程状态');
  }
  if (nextAction === 'ENTER_HOME') {
    const target = sanitizeRedirect(redirect);
    return target && isLearningTarget(target) ? target : LEARNING_ACTION_TARGET.ENTER_HOME;
  }
  return LEARNING_ACTION_TARGET[nextAction];
};
