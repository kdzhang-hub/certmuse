export type TextbookAccessState = 'ready' | 'setup_required' | 'load_failed';

export function resolveTextbookAccessState(nextAction: unknown, loadFailed: boolean): TextbookAccessState {
  if (nextAction === 'SET_GOAL') return 'setup_required';
  return loadFailed ? 'load_failed' : 'ready';
}
