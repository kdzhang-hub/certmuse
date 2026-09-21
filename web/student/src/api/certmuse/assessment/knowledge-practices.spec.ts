import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  completeKnowledgePractice,
  getKnowledgePracticeSetup as getRemoteKnowledgePracticeSetup,
  getKnowledgePracticeAnswerSession,
  getKnowledgePracticeItem,
  startKnowledgePractice as startRemoteKnowledgePractice,
  submitKnowledgePracticeItem
} from './knowledge-practices';
import {
  completeKnowledgePractice as completeMockKnowledgePractice,
  getKnowledgePracticeAnswerSession as getMockKnowledgePracticeAnswerSession,
  getKnowledgePracticeItem as getMockKnowledgePracticeItem,
  getKnowledgePracticeSetup,
  startKnowledgePractice,
  submitKnowledgePracticeItem as submitMockKnowledgePracticeItem
} from '@/api/student/mock';

const request = vi.hoisted(() => vi.fn(config => Promise.resolve(config)));

vi.mock('@/utils/request', () => ({ default: request }));

describe('knowledge practice answering HTTP contract', () => {
  beforeEach(() => request.mockClear());

  it('uses the U08 setup and start routes with the caller request ID', () => {
    getRemoteKnowledgePracticeSetup();
    startRemoteKnowledgePractice(
      { knowledgePointId: '900000000000000103', expectedGoalVersion: 4 },
      '0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd'
    );

    expect(request.mock.calls).toEqual([
      [{ url: '/api/assessment/knowledge-practices/setup', method: 'get' }],
      [{
        url: '/api/assessment/knowledge-practices',
        method: 'post',
        data: { knowledgePointId: '900000000000000103', expectedGoalVersion: 4 },
        headers: { 'X-Request-Id': '0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd', repeatSubmit: false }
      }]
    ]);
  });

  it('reads the session summary and individual frozen questions on demand', () => {
    getKnowledgePracticeAnswerSession('session-1');
    getKnowledgePracticeItem('session-1', 4);

    expect(request.mock.calls).toEqual([
      [{ url: '/api/assessment/knowledge-practices/session-1', method: 'get' }],
      [{ url: '/api/assessment/knowledge-practices/session-1/items/4', method: 'get' }]
    ]);
  });

  it('sends idempotency headers for submit and complete', () => {
    submitKnowledgePracticeItem('session-1', 4, { answer: { value: ['A', 'C'] } }, 'submit-1');
    completeKnowledgePractice('session-1', 'complete-1');

    expect(request.mock.calls).toEqual([
      [
        {
          url: '/api/assessment/knowledge-practices/session-1/items/4/submit',
          method: 'post',
          data: { answer: { value: ['A', 'C'] } },
          headers: { 'X-Request-Id': 'submit-1', repeatSubmit: false }
        }
      ],
      [
        {
          url: '/api/assessment/knowledge-practices/session-1/complete',
          method: 'post',
          headers: { 'X-Request-Id': 'complete-1', repeatSubmit: false }
        }
      ]
    ]);
  });

  it('supports the choice-only Mock lifecycle without persisting an unsubmitted choice', async () => {
    const setup = await getKnowledgePracticeSetup();
    const leaf = setup.data!.subjects[0].nodes[0].children[0].children[0];
    const started = await startKnowledgePractice(
      { knowledgePointId: leaf.id, expectedGoalVersion: setup.data!.goal.version },
      '915afef7-ec44-4666-aaf7-96d59efb8478'
    );
    const sessionId = started.data!.sessionId;
    const summary = await getMockKnowledgePracticeAnswerSession(sessionId);
    const first = await getMockKnowledgePracticeItem(sessionId, 1);
    const submitted = await submitMockKnowledgePracticeItem(
      sessionId,
      1,
      { answer: { value: [first.data!.question.options[0].label] } },
      '315afef7-ec44-4666-aaf7-96d59efb8478'
    );
    const reloaded = await getMockKnowledgePracticeItem(sessionId, 1);
    const completed = await completeMockKnowledgePractice(sessionId, '415afef7-ec44-4666-aaf7-96d59efb8478');

    expect(summary.data?.sessionStatus).toBe('IN_PROGRESS');
    expect(submitted.data?.submittedCount).toBe(1);
    expect(reloaded.data?.submission?.selectedOptionLabels).toEqual([first.data!.question.options[0].label]);
    expect(completed.data).toMatchObject({ sessionStatus: 'COMPLETED', submittedCount: 1 });
    expect((await getMockKnowledgePracticeAnswerSession(sessionId)).data?.navigation).toEqual([]);
  });
});
