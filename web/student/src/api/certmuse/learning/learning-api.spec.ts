import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  finishDiagnostic,
  getDiagnosticPreflight,
  getDiagnosticQuestion,
  getDiagnosticReport,
  getDiagnosticResultStatus,
  getDiagnosticSession,
  getFinishCheck,
  pauseDiagnostic,
  regenerateDiagnosticResult,
  saveDiagnosticDraft,
  sendDiagnosticTimerEvent,
  startDiagnostic
} from './diagnostics';
import { createLearningGoal, getLearningGoalOptions } from './goals';
import { getGoalSwitchOptions, switchLearningGoal } from './goal-switch';
import { getOnboardingStatus } from './onboarding';

const request = vi.hoisted(() => vi.fn(config => Promise.resolve(config)));

vi.mock('@/utils/request', () => ({ default: request }));

describe('learner HTTP contract', () => {
  beforeEach(() => request.mockClear());

  it('queries the server-owned onboarding state and goal options', () => {
    getOnboardingStatus();
    getLearningGoalOptions();

    expect(request.mock.calls).toEqual([
      [{ url: '/api/learning/onboarding/status', method: 'get' }],
      [{ url: '/api/learning/goals/options', method: 'get' }]
    ]);
  });

  it('creates exactly the four-field learning goal with an idempotency key', () => {
    const data = {
      certificationId: 'cert-1',
      targetExamYear: 2027,
      targetExamMonth: 5 as const,
      dailyMinutes: 45
    };

    createLearningGoal(data, 'goal-request-1');

    expect(request).toHaveBeenCalledWith({
      url: '/api/learning/goals',
      method: 'post',
      data,
      headers: { 'X-Request-Id': 'goal-request-1', repeatSubmit: false }
    });
  });

  it('uses the dedicated goal-switch endpoints and preserves the idempotency key', () => {
    const data = {
      certificationId: 'cert-2',
      targetExamYear: 2027,
      targetExamMonth: 5 as const,
      expectedCurrentGoalVersion: 3,
      confirmAbandonInProgress: true
    };

    getGoalSwitchOptions();
    switchLearningGoal(data, 'switch-request-1');

    expect(request.mock.calls).toEqual([
      [{ url: '/api/learning/goals/current/switch-options', method: 'get' }],
      [
        {
          url: '/api/learning/goals/switch',
          method: 'post',
          data,
          headers: { 'X-Request-Id': 'switch-request-1', repeatSubmit: false }
        }
      ]
    ]);
  });

  it('loads diagnostic discovery and session resources by server identity', () => {
    getDiagnosticPreflight();
    getDiagnosticSession('session-1');
    getDiagnosticQuestion('session-1', 7);
    getFinishCheck('session-1');
    getDiagnosticResultStatus('session-1');
    getDiagnosticReport('session-1');

    expect(request.mock.calls).toEqual([
      [{ url: '/api/assessment/diagnostics/preflight', method: 'get' }],
      [{ url: '/api/assessment/diagnostics/session-1', method: 'get' }],
      [{ url: '/api/assessment/diagnostics/session-1/items/7', method: 'get' }],
      [{ url: '/api/assessment/diagnostics/session-1/finish-check', method: 'get' }],
      [{ url: '/api/assessment/diagnostics/session-1/status', method: 'get' }],
      [{ url: '/api/assessment/diagnostics/session-1/report', method: 'get' }]
    ]);
  });

  it('uses idempotency headers for every diagnostic mutation', () => {
    const answer = {
      schema_version: '1.0' as const,
      answer_type: 'CHOICE' as const,
      selection_mode: 'SINGLE',
      value: ['A']
    };

    startDiagnostic({ diagnosticRevisionId: 'revision-1', goalVersion: 3 }, 'start-1');
    sendDiagnosticTimerEvent('session-1', { eventType: 'HEARTBEAT', questionOrder: 7, leaseId: 'lease-1' }, 'timer-1');
    saveDiagnosticDraft('session-1', 7, { answer, expectedSessionVersion: 4, currentQuestionOrder: 7 }, 'draft-1');
    pauseDiagnostic('session-1', { currentQuestionOrder: 7, expectedSessionVersion: 5 }, 'pause-1');
    finishDiagnostic('session-1', { expectedSessionVersion: 6 }, 'finish-1');
    regenerateDiagnosticResult('session-1', 'retry-1');

    expect(request.mock.calls).toEqual([
      [
        {
          url: '/api/assessment/diagnostics',
          method: 'post',
          data: { diagnosticRevisionId: 'revision-1', goalVersion: 3 },
          headers: { 'X-Request-Id': 'start-1', repeatSubmit: false }
        }
      ],
      [
        {
          url: '/api/assessment/diagnostics/session-1/timer-events',
          method: 'post',
          data: { eventType: 'HEARTBEAT', questionOrder: 7, leaseId: 'lease-1' },
          headers: { 'X-Request-Id': 'timer-1', repeatSubmit: false }
        }
      ],
      [
        {
          url: '/api/assessment/diagnostics/session-1/items/7/draft',
          method: 'put',
          data: { answer, expectedSessionVersion: 4, currentQuestionOrder: 7 },
          headers: { 'X-Request-Id': 'draft-1', repeatSubmit: false }
        }
      ],
      [
        {
          url: '/api/assessment/diagnostics/session-1/pause',
          method: 'post',
          data: { currentQuestionOrder: 7, expectedSessionVersion: 5 },
          headers: { 'X-Request-Id': 'pause-1', repeatSubmit: false }
        }
      ],
      [
        {
          url: '/api/assessment/diagnostics/session-1/finish',
          method: 'post',
          data: { expectedSessionVersion: 6 },
          headers: { 'X-Request-Id': 'finish-1', repeatSubmit: false }
        }
      ],
      [
        {
          url: '/api/assessment/diagnostics/session-1/regenerate-result',
          method: 'post',
          data: {},
          headers: { 'X-Request-Id': 'retry-1', repeatSubmit: false }
        }
      ]
    ]);
  });
});
