import { describe, expect, it } from 'vitest';
import {
  deletePracticeSet,
  getKnowledgePracticeSetup,
  getMistake,
  getSession,
  getSettings,
  listHistory,
  listTasks,
  savePracticeSet,
  startMistakeCorrection,
  startKnowledgePractice,
  submitSessionItem,
  submitSession,
  updateSettings
} from './mock';

describe('student Mock API boundary', () => {
  it('filters and paginates student tasks without exposing page implementation details', async () => {
    const response = await listTasks({ keyword: '架构', pageNum: 1, pageSize: 1 });

    expect(response.code).toBe(200);
    expect(response.data?.total).toBeGreaterThan(0);
    expect(response.data?.rows).toHaveLength(1);
    expect(response.data?.rows[0].knowledgePoint).toContain('架构');
  });

  it('creates and removes a private practice set through the Mock boundary', async () => {
    const created = await savePracticeSet({ name: '测试题集', note: '单测临时数据', questionCount: 3 });
    const id = created.data?.id;

    expect(created.data?.status).toBe('可练习');
    expect(id).toBeTruthy();

    const removed = await deletePracticeSet(String(id));
    expect(removed.data).toBe(true);
  });

  it('moves a correctly redone mistake to corrected history', async () => {
    await startMistakeCorrection(['mistake-pipe-filter']);
    const session = await getSession('correction');
    const question = session.data?.questions[0];
    await submitSessionItem({
      sessionId: String(session.data?.id),
      kind: 'correction',
      questionId: String(question?.id),
      answer: ['A']
    });
    const reloaded = await getMistake('mistake-pipe-filter');

    expect(reloaded.data?.status).toBe('已订正');
  });

  it('records submitted sessions in learning history', async () => {
    const result = await submitSession({
      sessionId: 'practice-session-demo',
      kind: 'practice',
      answers: { 'question-1': ['A'], 'question-2': ['A', 'C'], 'question-3': [] }
    });
    const history = await listHistory({ pageNum: 1, pageSize: 10 });

    expect(result.data?.answeredCount).toBe(2);
    expect(history.data?.rows.some(item => item.activity === '自主练习')).toBe(true);
  });

  it('updates settings and can restore them for later consumers', async () => {
    const previous = await getSettings();
    const next = { ...previous.data!, dailyMinutes: 45, reminderEnabled: false };

    await updateSettings(next);
    const updated = await getSettings();

    expect(updated.data).toMatchObject({ dailyMinutes: 45, reminderEnabled: false });
    await updateSettings(previous.data!);
  });

  it('returns the U08 directory and creates one stable practice session for a leaf', async () => {
    const setup = await getKnowledgePracticeSetup();
    const leaf = setup.data?.subjects[0].nodes[0].children[0].children[0];

    expect(leaf?.questionCount).toBeGreaterThan(0);
    const first = await startKnowledgePractice(
      { knowledgePointId: String(leaf?.id), expectedGoalVersion: setup.data!.goal.version },
      '0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd'
    );
    const replay = await startKnowledgePractice(
      { knowledgePointId: String(leaf?.id), expectedGoalVersion: setup.data!.goal.version },
      '0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd'
    );

    expect(first.data).toMatchObject({ totalCount: leaf?.questionCount, answerPath: expect.stringContaining('sessionId=') });
    expect(replay.data).toEqual(first.data);
    expect((await getSession('practice')).data?.questions.every(question => question.type === 'CHOICE')).toBe(true);
  });

});
