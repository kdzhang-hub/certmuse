import { describe, expect, it } from 'vitest';
import type { QuestionListVO, QuestionStatus } from '@/api/certmuse/question/types';
import {
  isQuestionDeletable,
  isQuestionEditable,
  isQuestionReviewSubmittable,
  isQuestionSelectable
} from './question-list';

function question(status: QuestionStatus, updatedTime: string): QuestionListVO {
  return { status, updatedTime } as QuestionListVO;
}

describe('question list rules', () => {
  it('allows selecting rows that have a batch action', () => {
    expect(isQuestionSelectable(question('draft', '2026-08-06T12:00:00Z'))).toBe(true);
    expect(isQuestionSelectable(question('rejected', '2026-08-06T12:00:00Z'))).toBe(true);
    expect(isQuestionSelectable(question('published', '2026-08-06T12:00:00Z'))).toBe(true);
    expect(isQuestionSelectable(question('pending_review', '2026-08-06T12:00:00Z'))).toBe(false);
  });

  it('allows deleting drafts and rejected questions only', () => {
    expect(isQuestionDeletable(question('draft', '2026-08-06T12:00:00Z'))).toBe(true);
    expect(isQuestionDeletable(question('rejected', '2026-08-06T12:00:00Z'))).toBe(true);
    expect(isQuestionDeletable(question('pending_review', '2026-08-06T12:00:00Z'))).toBe(false);
    expect(isQuestionDeletable(question('published', '2026-08-06T12:00:00Z'))).toBe(false);
  });

  it('allows editing drafts and rejected questions only', () => {
    expect(isQuestionEditable(question('draft', '2026-08-06T12:00:00Z'))).toBe(true);
    expect(isQuestionEditable(question('rejected', '2026-08-06T12:00:00Z'))).toBe(true);
    expect(isQuestionEditable(question('pending_review', '2026-08-06T12:00:00Z'))).toBe(false);
    expect(isQuestionEditable(question('published', '2026-08-06T12:00:00Z'))).toBe(false);
  });

  it('allows submitting drafts and rejected questions for review only', () => {
    expect(isQuestionReviewSubmittable(question('draft', '2026-08-06T12:00:00Z'))).toBe(true);
    expect(isQuestionReviewSubmittable(question('rejected', '2026-08-06T12:00:00Z'))).toBe(true);
    expect(isQuestionReviewSubmittable(question('pending_review', '2026-08-06T12:00:00Z'))).toBe(false);
    expect(isQuestionReviewSubmittable(question('published', '2026-08-06T12:00:00Z'))).toBe(false);
  });
});
