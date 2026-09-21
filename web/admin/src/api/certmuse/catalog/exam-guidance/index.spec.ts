import { beforeEach, describe, expect, it, vi } from 'vitest';

const request = vi.hoisted(() => vi.fn());
vi.mock('@/utils/request', () => ({ default: request }));

import {
  createExamPeriod, createExamPeriodRevision, createExamRegionRegistration, createExamSchedule,
  deleteExamPeriod, deleteExamRegionRegistration, deleteExamSchedule, getExamPeriod, getExamRegion,
  listExamPeriods, listExamRegions, publishExamPeriod, updateExamPeriod, updateExamRegion,
  updateExamRegionRegistration, updateExamSchedule
} from './index';

describe('exam guidance API', () => {
  beforeEach(() => request.mockReset());

  it('uses read-only requests for lists and details', () => {
    listExamPeriods({ pageNum: 1, pageSize: 20 });
    getExamPeriod('period-1');
    listExamRegions({ pageNum: 1, pageSize: 20, status: 'enabled' });
    getExamRegion('region-1');

    expect(request.mock.calls.map(([config]) => config)).toEqual([
      { url: '/api/admin/catalog/exam-periods', method: 'get', params: { pageNum: 1, pageSize: 20 } },
      { url: '/api/admin/catalog/exam-periods/period-1', method: 'get' },
      { url: '/api/admin/catalog/exam-regions', method: 'get', params: { pageNum: 1, pageSize: 20, status: 'enabled' } },
      { url: '/api/admin/catalog/exam-regions/region-1', method: 'get' }
    ]);
  });

  it('sends every mutation with an idempotency key and its expected row version', () => {
    const period = { periodCode: '2026-H1' } as any;
    const update = { expectedRowVersion: 3 } as any;
    const schedule = { expectedPeriodRowVersion: 3 } as any;
    const registration = { expectedPeriodRowVersion: 3 } as any;
    const region = { expectedRowVersion: 3 } as any;

    createExamPeriod(period); updateExamPeriod('period-1', update); deleteExamPeriod('period-1', 3);
    createExamPeriodRevision('period-1', 3); publishExamPeriod('period-1', 3);
    createExamSchedule('period-1', schedule); updateExamSchedule('schedule-1', schedule); deleteExamSchedule('schedule-1', 3);
    updateExamRegion('region-1', region); createExamRegionRegistration('period-1', registration);
    updateExamRegionRegistration('registration-1', registration); deleteExamRegionRegistration('registration-1', 3);

    for (const [config] of request.mock.calls) {
      expect(config.headers).toEqual(expect.objectContaining({
        'X-Request-Id': expect.any(String), repeatSubmit: false
      }));
    }
    expect(request.mock.calls.map(([config]) => [config.url, config.method, config.data])).toEqual([
      ['/api/admin/catalog/exam-periods', 'post', period],
      ['/api/admin/catalog/exam-periods/period-1', 'put', update],
      ['/api/admin/catalog/exam-periods/period-1', 'delete', { expectedRowVersion: 3 }],
      ['/api/admin/catalog/exam-periods/period-1/revisions', 'post', { expectedRowVersion: 3 }],
      ['/api/admin/catalog/exam-periods/period-1/publish', 'post', { expectedRowVersion: 3 }],
      ['/api/admin/catalog/exam-periods/period-1/schedules', 'post', schedule],
      ['/api/admin/catalog/exam-schedules/schedule-1', 'put', schedule],
      ['/api/admin/catalog/exam-schedules/schedule-1', 'delete', { expectedPeriodRowVersion: 3 }],
      ['/api/admin/catalog/exam-regions/region-1', 'put', region],
      ['/api/admin/catalog/exam-periods/period-1/registrations', 'post', registration],
      ['/api/admin/catalog/exam-region-registrations/registration-1', 'put', registration],
      ['/api/admin/catalog/exam-region-registrations/registration-1', 'delete', { expectedPeriodRowVersion: 3 }]
    ]);
  });
});
