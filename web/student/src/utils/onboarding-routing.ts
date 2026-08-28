import type { OnboardingNextAction, OnboardingStatus } from '@/api/types';

const targets: Record<OnboardingNextAction, string> = {
  SET_GOAL: '/learning/onboarding',
  START_DIAGNOSTIC: '/learning/diagnostic/intro',
  CONTINUE_DIAGNOSTIC: '/learning/diagnostic/session',
  WAIT_PROCESSING: '/learning/diagnostic/result',
  ENTER_HOME: '/learning/home',
  RETRY_DIAGNOSTIC: '/learning/diagnostic/intro?mode=retry',
  CONTACT_SUPPORT: '/learning/home?state=support',
  VIEW_DIAGNOSTIC_REPORT: '/learning/diagnostic/report',
  RETRY_DIAGNOSTIC_RESULT: '/learning/diagnostic/result?mode=failed'
};

export function resolveOnboardingTarget(action: unknown): string | undefined {
  return typeof action === 'string' ? targets[action as OnboardingNextAction] : undefined;
}

/** Adds the server-owned session identity when routing into a diagnostic detail page. */
export function resolveOnboardingTargetWithSession(action: unknown, sessionId?: string): string | undefined {
  const target = resolveOnboardingTarget(action);
  if (!target || !sessionId || !target.startsWith('/learning/diagnostic/')) return target;
  const separator = target.includes('?') ? '&' : '?';
  return `${target}${separator}sessionId=${encodeURIComponent(sessionId)}`;
}

export function isConsistentOnboardingStatus(status: OnboardingStatus): boolean {
  if (status.nextAction === 'SET_GOAL')
    return status.goalStatus === 'NONE' && status.diagnosticStatus === 'NOT_STARTED';
  return status.goalStatus !== 'NONE';
}

export function isOnboardingManagedPath(path: string): boolean {
  return path.startsWith('/learning/');
}
