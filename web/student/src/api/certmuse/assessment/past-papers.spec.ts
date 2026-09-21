import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  finishPastPaperExam,
  getPastPaperExamFinishCheck,
  getPastPaperExamItem,
  getPastPaperExamResult,
  getPastPaperExamSession,
  getPastPaperExamStatus,
  pausePastPaperExam,
  regeneratePastPaperExamResult,
  savePastPaperExamDraft,
  sendPastPaperExamTimer,
  completePastPaperPractice,
  getPastPaperPracticeItem,
  getPastPaperPracticeSession,
  submitPastPaperPracticeItem
} from './past-papers';

const request = vi.hoisted(() => vi.fn(config => Promise.resolve(config)));
vi.mock('@/utils/request', () => ({ default: request }));

describe('past-paper formal exam HTTP contract', () => {
  beforeEach(() => {
    request.mockClear();
    vi.spyOn(crypto, 'randomUUID').mockReturnValue('00000000-0000-4000-8000-000000000002');
  });

  it('keeps exam lifecycle under exam-sessions and carries versioned timer writes', () => {
    const draft = {
      answer: { choiceValue: null, textValue: '主观题答案' },
      expectedSessionVersion: 5,
      currentQuestionOrder: 8
    };
    const timer = { eventType: 'HIDDEN' as const, questionOrder: 8, leaseId: 'lease', expectedSessionVersion: 5 };

    getPastPaperExamSession('41');
    getPastPaperExamItem('41', 8);
    savePastPaperExamDraft('41', 8, draft);
    sendPastPaperExamTimer('41', timer);
    pausePastPaperExam('41', { currentQuestionOrder: 8, expectedSessionVersion: 5 });
    getPastPaperExamFinishCheck('41');
    finishPastPaperExam('41', { expectedSessionVersion: 6 });
    getPastPaperExamStatus('41');
    regeneratePastPaperExamResult('41');
    getPastPaperExamResult('41');

    const base = '/api/assessment/past-papers/exam-sessions/41';
    expect(request.mock.calls.map(call => call[0].url)).toEqual([
      base,
      `${base}/items/8`,
      `${base}/items/8/draft`,
      `${base}/timer-events`,
      `${base}/pause`,
      `${base}/finish-check`,
      `${base}/finish`,
      `${base}/status`,
      `${base}/regenerate-result`,
      `${base}/result`
    ]);
    expect(request.mock.calls[3][0].data).toEqual(timer);
    expect(request.mock.calls[3][0].headers).toEqual({
      'X-Request-Id': '00000000-0000-4000-8000-000000000002',
      repeatSubmit: false
    });
  });
});

describe('past-paper practice HTTP contract', () => {
  beforeEach(() => {
    request.mockClear();
    vi.spyOn(crypto, 'randomUUID').mockReturnValue('00000000-0000-4000-8000-000000000002');
  });

  it('uses the practice session endpoints with the same item-submit shape as knowledge practice', () => {
    const base = '/api/assessment/past-papers/practice-sessions/41';
    getPastPaperPracticeSession('41');
    getPastPaperPracticeItem('41', 8);
    submitPastPaperPracticeItem('41', 8, { answer: { value: ['A'] } });
    completePastPaperPractice('41');

    expect(request.mock.calls.map(call => call[0].url)).toEqual([base, `${base}/items/8`, `${base}/items/8/submit`, `${base}/complete`]);
    expect(request.mock.calls[2][0].data).toEqual({ answer: { value: ['A'] } });
    expect(request.mock.calls[2][0].headers).toEqual({
      'X-Request-Id': '00000000-0000-4000-8000-000000000002',
      repeatSubmit: false
    });
  });
});
