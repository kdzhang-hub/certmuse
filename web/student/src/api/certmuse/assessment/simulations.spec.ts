import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  finishSimulation,
  getSimulationDetail,
  getSimulationFinishCheck,
  getSimulationItem,
  getSimulationPreview,
  getSimulationResult,
  getSimulationSession,
  getSimulationSetup,
  getSimulationStatus,
  listSimulations,
  pauseSimulation,
  regenerateSimulationResult,
  saveSimulationDraft,
  sendSimulationTimer,
  startSimulationSession
} from './simulations';

const request = vi.hoisted(() => vi.fn(config => Promise.resolve(config)));

vi.mock('@/utils/request', () => ({ default: request }));

describe('simulation HTTP contract', () => {
  beforeEach(() => request.mockClear());

  it('uses the U13 display routes and passes only server-supported filters', () => {
    getSimulationSetup();
    listSimulations({ certificationId: '9', syllabusVersionId: '21', keyword: '模拟卷', pageNum: 2, pageSize: 20 });
    getSimulationDetail('101');
    getSimulationPreview('101');

    expect(request.mock.calls).toEqual([
      [{ url: '/api/assessment/simulations/setup', method: 'get' }],
      [
        {
          url: '/api/assessment/simulations',
          method: 'get',
          params: { certificationId: '9', syllabusVersionId: '21', keyword: '模拟卷', pageNum: 2, pageSize: 20 }
        }
      ],
      [{ url: '/api/assessment/simulations/101', method: 'get' }],
      [{ url: '/api/assessment/simulations/101/preview', method: 'get' }]
    ]);
  });

  it('sends the immutable revision and goal version with a caller request ID', () => {
    startSimulationSession('101', { expectedRevisionId: '102', expectedGoalVersion: 3 }, 'request-id');

    expect(request.mock.calls).toEqual([
      [
        {
          url: '/api/assessment/simulations/101/sessions',
          method: 'post',
          data: { expectedRevisionId: '102', expectedGoalVersion: 3 },
          headers: { 'X-Request-Id': 'request-id', repeatSubmit: false }
        }
      ]
    ]);
  });

  it('uses the independent formal-exam lifecycle and sends a request ID on every write', () => {
    vi.spyOn(crypto, 'randomUUID').mockReturnValue('00000000-0000-4000-8000-000000000001');
    const draft = {
      answer: { choiceValue: ['A'], textValue: null },
      expectedSessionVersion: 2,
      currentQuestionOrder: 3
    };
    const timer = { eventType: 'HEARTBEAT' as const, questionOrder: 3, leaseId: 'lease', expectedSessionVersion: 2 };

    getSimulationSession('31');
    getSimulationItem('31', 3);
    saveSimulationDraft('31', 3, draft);
    sendSimulationTimer('31', timer);
    pauseSimulation('31', { currentQuestionOrder: 3, expectedSessionVersion: 2 });
    getSimulationFinishCheck('31');
    finishSimulation('31', { expectedSessionVersion: 3 });
    getSimulationStatus('31');
    regenerateSimulationResult('31');
    getSimulationResult('31');

    const base = '/api/assessment/simulations/sessions/31';
    expect(request.mock.calls.map(call => call[0].url)).toEqual([
      base,
      `${base}/items/3`,
      `${base}/items/3/draft`,
      `${base}/timer-events`,
      `${base}/pause`,
      `${base}/finish-check`,
      `${base}/finish`,
      `${base}/status`,
      `${base}/regenerate-result`,
      `${base}/result`
    ]);
    for (const call of request.mock.calls.filter(call => ['post', 'put'].includes(call[0].method))) {
      expect(call[0].headers).toEqual({
        'X-Request-Id': '00000000-0000-4000-8000-000000000001',
        repeatSubmit: false
      });
    }
    expect(request.mock.calls[3][0].data).toEqual(timer);
  });
});
