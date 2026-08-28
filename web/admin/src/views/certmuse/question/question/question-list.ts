import type { QuestionListVO } from '@/api/certmuse/question/types';

export function isQuestionDeletable(item: QuestionListVO) {
  return item.status === 'draft' || item.status === 'rejected';
}

export function isQuestionSelectable(item: QuestionListVO) {
  return isQuestionDeletable(item) || item.status === 'published';
}

export function isQuestionEditable(item: QuestionListVO) {
  return item.status === 'draft' || item.status === 'rejected';
}

export function isQuestionReviewSubmittable(item: QuestionListVO) {
  return item.status === 'draft' || item.status === 'rejected';
}
