import { beforeEach, describe, expect, it, vi } from 'vitest';
import { launchLearningTask, listLearningTaskPool, supplementLearningTasks } from './tasks';

const request = vi.hoisted(() => vi.fn(config => Promise.resolve(config)));

vi.mock('@/utils/request', () => ({ default: request }));

describe('learning task pool HTTP contract', () => {
  beforeEach(() => request.mockClear());

  it('queries the task pool with valid pagination and keyword parameters', () => {
    listLearningTaskPool({ keyword: '  架构风格  ', pageNum: 2, pageSize: 20 });
    listLearningTaskPool({ keyword: '', pageNum: 1, pageSize: 10 });

    expect(request.mock.calls).toEqual([
      [{ url: '/api/learning/tasks', method: 'get', params: { keyword: '架构风格', pageNum: 2, pageSize: 20 } }],
      [{ url: '/api/learning/tasks', method: 'get', params: { pageNum: 1, pageSize: 10 } }]
    ]);
  });

  it('sends an idempotency key for task launch and supplementation', () => {
    launchLearningTask('1950000000000000001', 'launch-request-1');
    supplementLearningTasks('supplement-request-1');

    expect(request.mock.calls).toEqual([
      [{
        url: '/api/learning/tasks/1950000000000000001/launch',
        method: 'post',
        headers: { 'X-Request-Id': 'launch-request-1', repeatSubmit: false }
      }],
      [{
        url: '/api/learning/tasks/supplement',
        method: 'post',
        headers: { 'X-Request-Id': 'supplement-request-1', repeatSubmit: false }
      }]
    ]);
  });
});
